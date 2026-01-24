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
/// Using minimal threads and resources, it waits for its child process or parent (if specified) to finish, and then it dies.
/// </summary>
namespace ProcessSandbox
{
    class Program
    {
        public static readonly string Usage = "//   Uses process jobs to ensure child and parent processes die together\r\n" +
                                              "//       [--help or -?]             // display usage and quit\r\n" +
                                              "//       [--ppid PROCESS_ID]        // parent process ID to wait on\r\n" +
                                              "//       [--priority PRIORITY]      // process priority (one of " + String.Join(", ", Enum.GetNames(typeof(ProcessPriorityClass))) + ")\r\n" +
                                              "//       [--windowStyle STYLE]      // one of " + String.Join(", ", Enum.GetNames(typeof(ProcessPriorityClass))) + " (DOES NOT ALWAYS WORK)\r\n" +
                                              "//       EXECUTABLE PARAMS...";

        static void Main()
        {
            // outside of try to ensure safely cleaned up
            Process child = null;

            try
            {
                uint ppid = 0; // the parent process ID
                string working = ""; // the working directory

                string exec = null; // the executable
                List<string> execArgs = new List<string>(); // the executable args

                ProcessPriorityClass? priorityClass = null;
                ProcessWindowStyle? windowStyle = null;

                string commandLine = Environment.CommandLine;
                ParseArgs(commandLine, ref ppid, ref working, ref exec, ref priorityClass, ref windowStyle, execArgs);

                // verify args
                if (exec == null)
                {
                    // quit silently
                    return;
                }

                // determine this (self) and parent processes
                Process self = Process.GetCurrentProcess();

                // (for parent, need to use 'OpenProcess' API for waitable handle. Managed Process class does not always provide one.)
                IntPtr parent = ppid > 0 ? OpenProcess(ProcessSecurityAndAccessRights_SYNCHRONIZE, true, (int)ppid) : IntPtr.Zero;

                // establish a job object and add itself
                Job job = new Job();
                job.AddProcess(self.Id);

                // kick off the child process...
                var startInfo = new ProcessStartInfo
                {
                    FileName = exec,
                    Arguments = string.Join(" ", execArgs),
                    UseShellExecute = false,
                    RedirectStandardError = false,
                    RedirectStandardInput = false,
                    RedirectStandardOutput = false
                };
                // in testing, had had no effect
                if (windowStyle.HasValue)
                    startInfo.WindowStyle = windowStyle.Value;

                child = Process.Start(startInfo);
                if (priorityClass.HasValue)
                    child.PriorityClass = priorityClass.Value;

                var childPID = child.Id;

                // ... and add new process (child) to the job
                job.AddProcess(childPID);

                List<ManualResetEvent> toWaitOn = new List<ManualResetEvent>();

                // wait on the child...
                toWaitOn.Add(IntPtrToManualResetEvent(child.Handle));

                // ... and parent (if specified)
                if (parent != IntPtr.Zero)
                    toWaitOn.Add(IntPtrToManualResetEvent(parent));

                // wait on either
                int signalledElement = WaitHandle.WaitAny(toWaitOn.ToArray());

                if (signalledElement > 0)
                {
                    // the parent stopped (likely unexpectidly)
                    // so kill the child and quickly die
                    child.Kill();
                }

                // wait for child to die (is redundant but no harm)...
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
                Console.WriteLine("{\"event\": \"LaunchFailure\", \"arg\": \"" + JSONEscape(exc.Message + " (" + exc.GetType() + ")") + "\"}");
            }

        }

        /// <summary>
        /// Splits the command line string into an array of arguments while preserving quotes around arguments containing spaces.
        /// </summary>
        private static string[] SplitCommandLine(string commandLine)
        {
            var args = new List<string>();
            var currentArg = new StringBuilder();
            bool inQuotes = false;

            for (int i = 0; i < commandLine.Length; i++)
            {
                char c = commandLine[i];

                if (c == '"')
                {
                    if (inQuotes && i + 1 < commandLine.Length && commandLine[i + 1] == '"')
                    {
                        // Handle escaped quotes ""
                        currentArg.Append(c);
                        i++;
                    }
                    else
                    {
                        inQuotes = !inQuotes;
                        currentArg.Append(c);
                    }
                }
                else if (c == ' ' && !inQuotes)
                {
                    if (currentArg.Length > 0)
                    {
                        args.Add(currentArg.ToString());
                        currentArg.Clear();
                    }
                }
                else
                {
                    currentArg.Append(c);
                }
            }

            if (currentArg.Length > 0)
            {
                args.Add(currentArg.ToString());
            }

            return args.ToArray();
        }


        /// <summary>
        /// This argument parses has to allow for arbitrary arguments after its own are parsed.
        /// </summary>
        private static void ParseArgs(string commandLine, ref uint ppid, ref string working, ref string exec, ref ProcessPriorityClass? priorityClass, ref ProcessWindowStyle? windowStyle, List<string> execArgs)
        {
            var args = SplitCommandLine(commandLine);
            var argStream = StringStream(args);

            // Skip the first argument, which is the executable path
            argStream.MoveNext();

            bool parsingSelfArgs = true;

            try
            {
                while (argStream.MoveNext())
                {
                    string arg = argStream.Current;
                    string lcArg = arg.ToLower();

                    if (parsingSelfArgs && (arg.Equals("-?") || arg.Equals("/?") || lcArg.Equals("--help")))
                    {
                        // print usage and quit
                        Console.WriteLine(Usage);
                        Environment.Exit(0);
                    }
                    else if (parsingSelfArgs && lcArg.Equals("--ppid"))
                    {
                        argStream.MoveNext();
                        ppid = uint.Parse(argStream.Current);
                    }
                    else if (parsingSelfArgs && lcArg.Equals("--working"))
                    {
                        argStream.MoveNext();
                        working = argStream.Current;
                    }
                    else if (parsingSelfArgs && lcArg.Equals("--windowstyle"))
                    {
                        argStream.MoveNext();

                        ProcessWindowStyle tmp;
                        if (Enum.TryParse(argStream.Current, true, out tmp))
                            windowStyle = tmp;
                    }
                    else if (parsingSelfArgs && lcArg.Equals("--priority"))
                    {
                        argStream.MoveNext();

                        ProcessPriorityClass tmp;
                        if (Enum.TryParse(argStream.Current, true, out tmp))
                            priorityClass = tmp;
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

        public static uint ProcessSecurityAndAccessRights_SYNCHRONIZE = 0x00100000;

        [DllImport("kernel32.dll")]
        public static extern IntPtr OpenProcess(
          uint dwDesiredAccess,
          bool bInheritHandle,
          int dwProcessId
        );

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
