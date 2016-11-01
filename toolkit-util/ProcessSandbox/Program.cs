using Microsoft.Win32.SafeHandles;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

/// <summary>
/// This program is a simple launcher that uses native Windows Job objects to prevent
/// child processes from being orphaned should itself or its parent die.
/// 
/// It waits for its child process to finish, and then it does.
/// </summary>
namespace ProcessSandbox
{
    class Program
    {
        public static readonly string Usage = "//   Uses process jobs to ensure child and parent processes die together\r\n" +
                                              "//       [--help or -?]          // display usage and quit\r\n" +
                                              "//       [--ppid PROCESS_ID]     // parent process ID to wait on\r\n" +
                                              "//       [--working WORKING_DIR] // the working directory\r\n" +
                                              "//       EXECUTABLE PARAMS...";

        static void Main(string[] args)
        {
            // outside of try to ensure safely cleaned up
            Process child = null;

            try
            {
                uint ppid = 0; // the parent process ID
                string working = ""; // the working directory

                string exec = null; // the executable
                List<string> execArgs = new List<string>(); // the executable args

                ParseArgs(args, ref ppid, ref working, ref exec, execArgs);

                // verify args
                if (exec == null)
                {
                    // quit silently
                    return;
                }

                // determine this (self) and parent processes
                Process self = Process.GetCurrentProcess();
                Process parent = ppid > 0 ? Process.GetProcessById((int)ppid) : null;

                // establish a job object and add itself
                Job job = new Job();
                job.AddProcess(self.Id);

                // kick off the child process...
                child = Process.Start(new ProcessStartInfo(exec, string.Join(" ", execArgs))
                {
                    UseShellExecute = false,
                    RedirectStandardError = false,
                    RedirectStandardInput = false,
                    RedirectStandardOutput = false,
                });
                var childPID = child.Id;

                // ... and add new process (child) to the job
                job.AddProcess(childPID);

                List<ManualResetEvent> toWaitOn = new List<ManualResetEvent>();

                // wait on the child...
                toWaitOn.Add(IntPtrToManualResetEvent(child.Handle));

                // ... and parent (if specified)
                if (parent != null)
                    toWaitOn.Add(IntPtrToManualResetEvent(parent.Handle));

                // wait on either
                WaitHandle.WaitAny(toWaitOn.ToArray());

                // wait for child to die (is redundant by no harm)...
                child.WaitForExit();

                // and pick up exit code to actually exit with
                int exitCode = child.ExitCode;
                Environment.Exit(exitCode);
            }
            catch (Exception exc)
            {
                // should never get here but just in case, clean up what you can
                try
                {
                    if (child != null)
                        child.Kill();
                }
                catch { }

                // produce a response message (for possible JSON consumption) and quit
                Console.WriteLine("{\"event\": \"LaunchFailure\", \"arg\": \""+ JSONEscape(exc.Message + " (" + exc.GetType()+ ")") + "\"}");
                return;
            }

        }



        /// <summary>
        /// This argument parses has to allow for arbitrary arguments after its own are parsed.
        /// </summary>
        private static void ParseArgs(string[] args, ref uint ppid, ref string working, ref string exec, List<string> execArgs)
        {
            var argStream = StringStream(args);

            bool parsingSelfArgs = true;

            try
            {
                while (argStream.MoveNext())
                {
                    string arg = argStream.Current;

                    if (parsingSelfArgs && (arg.Equals("-?") || arg.Equals("--help")))
                    {
                        // print usage and quit
                        Console.WriteLine(Usage);
                        Environment.Exit(0);
                    }
                    else if (parsingSelfArgs && arg.Equals("--ppid"))
                    {
                        argStream.MoveNext();
                        ppid = uint.Parse(argStream.Current);

                    }
                    else if (parsingSelfArgs && arg.Equals("--working"))
                    {
                        argStream.MoveNext();
                        working = argStream.Current;
                    }
                    else if (exec == null)
                    {
                        // first arg must be executable
                        exec = arg;

                        // now all args are executable args 
                        parsingSelfArgs = false;
                    }
                    else
                    {
                        // args will be executable args
                        execArgs.Add(arg);
                    }
                }
            }
            catch (InvalidOperationException)
            {
                // consume and use whatever we've already collected
            }
        }

        #region (Win32 wrappers, etc.)

        public class Job : IDisposable
        {

            private IntPtr handle;
            private bool disposed;

            public Job()
            {
                handle = CreateJobObject(IntPtr.Zero, null);

                var info = new JOBOBJECT_BASIC_LIMIT_INFORMATION
                {
                    LimitFlags = 0x2000
                };

                var extendedInfo = new JOBOBJECT_EXTENDED_LIMIT_INFORMATION
                {
                    BasicLimitInformation = info
                };

                int length = Marshal.SizeOf(typeof(JOBOBJECT_EXTENDED_LIMIT_INFORMATION));
                IntPtr extendedInfoPtr = Marshal.AllocHGlobal(length);
                Marshal.StructureToPtr(extendedInfo, extendedInfoPtr, false);

                if (!SetInformationJobObject(handle, JobObjectInfoType.ExtendedLimitInformation, extendedInfoPtr, (uint)length))
                    throw new Exception(string.Format("Unable to set information.  Error: {0}", Marshal.GetLastWin32Error()));
            }

            public void Dispose()
            {
                Dispose(true);
                GC.SuppressFinalize(this);
            }

            private void Dispose(bool disposing)
            {
                if (disposed)
                    return;

                if (disposing) { }

                Close();
                disposed = true;
            }

            public void Close()
            {
                CloseHandle(handle);
                handle = IntPtr.Zero;
            }


            public bool AddProcess(IntPtr processHandle)
            {
                return AssignProcessToJobObject(handle, processHandle);
            }

            public bool AddProcess(int processId)
            {
                return AddProcess(Process.GetProcessById(processId).Handle);
            }

        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
        static extern IntPtr CreateJobObject(IntPtr a, string lpName);

        [DllImport("kernel32.dll")]
        static extern bool SetInformationJobObject(IntPtr hJob, JobObjectInfoType infoType, IntPtr lpJobObjectInfo, UInt32 cbJobObjectInfoLength);

        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);

        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool CloseHandle(IntPtr hObject);

        [StructLayout(LayoutKind.Sequential)]
        struct IO_COUNTERS
        {
            public UInt64 ReadOperationCount;
            public UInt64 WriteOperationCount;
            public UInt64 OtherOperationCount;
            public UInt64 ReadTransferCount;
            public UInt64 WriteTransferCount;
            public UInt64 OtherTransferCount;
        }


        [StructLayout(LayoutKind.Sequential)]
        struct JOBOBJECT_BASIC_LIMIT_INFORMATION
        {
            public Int64 PerProcessUserTimeLimit;
            public Int64 PerJobUserTimeLimit;
            public UInt32 LimitFlags;
            public UIntPtr MinimumWorkingSetSize;
            public UIntPtr MaximumWorkingSetSize;
            public UInt32 ActiveProcessLimit;
            public UIntPtr Affinity;
            public UInt32 PriorityClass;
            public UInt32 SchedulingClass;
        }

        [StructLayout(LayoutKind.Sequential)]
        public struct SECURITY_ATTRIBUTES
        {
            public UInt32 nLength;
            public IntPtr lpSecurityDescriptor;
            public Int32 bInheritHandle;
        }

        [StructLayout(LayoutKind.Sequential)]
        struct JOBOBJECT_EXTENDED_LIMIT_INFORMATION
        {
            public JOBOBJECT_BASIC_LIMIT_INFORMATION BasicLimitInformation;
            public IO_COUNTERS IoInfo;
            public UIntPtr ProcessMemoryLimit;
            public UIntPtr JobMemoryLimit;
            public UIntPtr PeakProcessMemoryUsed;
            public UIntPtr PeakJobMemoryUsed;
        }

        public enum JobObjectInfoType
        {
            AssociateCompletionPortInformation = 7,
            BasicLimitInformation = 2,
            BasicUIRestrictions = 4,
            EndOfJobTimeInformation = 6,
            ExtendedLimitInformation = 9,
            SecurityLimitInformation = 5,
            GroupInformation = 11
        }

        #endregion

        #region (convenience functions)

        private static IEnumerator<String> StringStream(String[] strings)
        {
            foreach (String s in strings)
                yield return s;
        }

        private static string JSONEscape(string value)
        {
            var result = new StringBuilder(value.Length);
            foreach (char c in value)
            {
                if (c == '\\')
                    result.Append("\\\\");
                else if (c == '"')
                    result.Append("\\\"");
                else
                    result.Append(c);
            }

            return result.ToString();
        }

        /// <summary>
        /// Need SafeWaitHandle functionality here.
        /// </summary>
        private static ManualResetEvent IntPtrToManualResetEvent(IntPtr intPtr)
        {
            ManualResetEvent mre = new ManualResetEvent(true);
            mre.SafeWaitHandle = new SafeWaitHandle(intPtr, false);
            return mre;
        }

        #endregion

    }
}

