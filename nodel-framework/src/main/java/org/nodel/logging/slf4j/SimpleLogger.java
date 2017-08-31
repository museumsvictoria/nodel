package org.nodel.logging.slf4j;

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.nodel.DateTimes;
import org.nodel.Formatting;
import org.nodel.io.Stream;
import org.nodel.logging.Level;
import org.nodel.logging.LogEntry;
import org.nodel.logging.Logging;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleLogger extends BaseLogger {
    
    private static final long serialVersionUID = 1L;

    /**
     * The log file extension
     */
    private final static String LOGFILE_EXT = ".log";
    
    /**
     * The maximum amount of storage allowed to be used by log files (1 GB)
     */
    private final static long MAX_STORAGE = 1024 * 1024 * 1024;
    
    /**
     * Class-level lock.
     */
    private final static Object s_lock = new Object();
    
    /**
     * (init. lazily when required through 'selfLogger()')
     */
    private static Logger s_logger;
    
    /**
     * The default logging levels filter for each class.
     */
    private final static Map<String, Level> DEFAULT_LEVELMAP = new HashMap<String, Level>();

    static {
        DEFAULT_LEVELMAP.put("org.nodel.http.NanoHTTPD", Level.INFO);
        DEFAULT_LEVELMAP.put("org.nodel.discovery", Level.INFO);
        DEFAULT_LEVELMAP.put("org.nodel.toolkit.ManagedTCP", Level.INFO);
    }
    
    /**
     * Holds the default logging groups (allows logger instance grouping into separate files)
     */
    private final static Collection<String> DEFAULT_GROUPS = new ArrayList<String>();
    
    static {
        DEFAULT_GROUPS.add("org.nodel.discovery");
        DEFAULT_GROUPS.add("org.nodel.toolkit.ManagedTCP");
    }
    
    /**
     * Used as 'seq' field in log files
     */
    private static AtomicLong s_sequenceCounter = new AtomicLong();

    /**
     * The current level map (by full class name)
     */
    private static Map<String, Level> s_levelMap = DEFAULT_LEVELMAP;
    
    /**
     * (default)
     */
    private final static Level DEFAULT_FILELOG_LEVEL = Level.INFO;
    
    /**
     * The current file logging level.
     * RESERVED for possible future use.
     */
    @SuppressWarnings("unused")
    private static Level s_filelog_level = DEFAULT_FILELOG_LEVEL;
    
    /**
     * (default)
     */
    private final static Level DEFAULT_STDERR_LEVEL = Level.WARN;
    
    /**
     * (see public setter)
     */
    private static Level s_stderr_level = DEFAULT_STDERR_LEVEL;

    /**
     * The current standard error level.
     */
    public static void setStdErrLevel(Level level) {
        s_stderr_level = level;
    }

    /**
     * (see public setter)
     */
    public static Level getStdErrLevel() {
        return s_stderr_level;
    }

    /**
     * (default)
     */
    private final static Level DEFAULT_NODEL_LEVEL = Level.WARN;

    /**
     * (see public setter)
     */
    private static Level s_nodel_level = DEFAULT_NODEL_LEVEL;

    /**
     * The current nodel (via org.nodel.logging) level
     */
    public static void setNodelLevel(Level level) {
        s_nodel_level = level;
    }
    
    /**
     * (see public setter)
     */
    public static Level getNodelLevel() {
        return s_nodel_level;
    }

    /**
     * Locking for file writing.
     */
    private Object _fileLock = new Object();

    /**
     * Holds the last file name.
     * (locked around 'fileLock')
     */
    private String _lastFileName;
    
    /**
     * Used with 'DEFAULT_GROUPS' to allow log entry grouping into a single file
     * Is null if no grouping.
     */
    private String _group;

    /**
     * The log folder.
     */
    private static File s_logFolder;

    /**
     * (locked around 'fileLock')
     */
    private PrintWriter _writer;
    
    /**
     * For scheduling maintenance tasks. Initialised lazily to avoid stack-overflow during class loading.
     * (locked around 'fileLock')
     */
    private static Timers s_timers;
    
    /**
     * (init. in 'log')
     */
    private TimerTask _maintenanceTimerTask;
    
    /**
     * The minimum storage required before file logging is allowed.
     * (default 300 MB)
     */
    private static long MIN_STORAGE_RESERVED = 300 * 1024 * 1024;
    
    /**
     * If disk-space is short.
     */
    private static boolean s_fileLoggingDeactivated = false;

    /**
     * Normal constructor.
     */
    public SimpleLogger(final String name) {
        super(name);
        
        // check for grouping
        for (String group : DEFAULT_GROUPS) {
            if (name.startsWith(group)) {
                _group = group;
                break;
            }
        }
        
        // go through the map looking for specific levels (matches by start)
        for (Entry<String, Level> levelInfo : s_levelMap.entrySet()) {
            if (name.startsWith(levelInfo.getKey()) || (_group != null && _group.startsWith(levelInfo.getKey()))) {
                super.currentLogLevel = levelInfo.getValue();
                break;
            }
        }
    }

    /**
     * (timer entry-point)
     */
    private void handleMaintainanceTimer() {
        tidyUpLogFiles();
        
        synchronized(_fileLock) {
            String filename = getCurrentFilename(DateTime.now());
            
            // check if there has been any roll-over
            if (filename.equals(_lastFileName))
                return;
            
            // and release anyway in case files are left open
            _lastFileName = null;
            
            Stream.safeClose(_writer);
        }
    }

    public void setLevel(final Level level) {
        if (level != null) {
            super.currentLogLevel = level;
        }
    }
    
    public Level getLevel() {
        return super.currentLogLevel;
    }
    
    /**
     * Main logging entry-point from logging framework.
     */
    @Override
    protected void log(Level level, String msg, Throwable th) {
        // normally there would be a global filter statement right here.
        // e.g. if (!isLevelEnabled(level))
        //          return;

        int intLevel = level.intLevel();

        // get a reference timestamp immediately that should be shared if possible
        DateTime now = DateTime.now();

        // (prepare once, use many)
        String formattedTimestamp = now.toString("YYYY-MM-dd HH:mm:ss.SSS");

        String tag = super.name;

        // (prepare once, use many)
        String capturedStackTrace = (th == null ? null : captureStackTrace(th));
        
        // will be prepared lazily
        LogEntry le = null;

        // write to nodel logging
        if (intLevel >= s_nodel_level.intLevel()) {
            le = new LogEntry(now, level, tag, msg, capturedStackTrace);
            writeToNodelLogging(le);
        }

        // only write to log file if enabled and higher level
        if (s_logFolder != null && intLevel >= super.currentLogLevel.intLevel()) {
            if (le == null)
                le = new LogEntry(now, level, tag, msg, capturedStackTrace);
            
            writeToFile(formattedTimestamp, le);
        }

        // write to stderr
        if (intLevel >= s_stderr_level.intLevel()) {
            if (le == null)
                le = new LogEntry(now, level, tag, msg, capturedStackTrace);
            
            writeToStdErr(formattedTimestamp, le);
        }
    }

    /**
     * Enables file-based logging.
     */
    public static void setFolderOut(File folder) {
        if (folder == null)
            throw new IllegalArgumentException("A log folder cannot be specified as null.");

        if (!folder.isDirectory())
            throw new RuntimeException("The argument specified is not actually a folder.");

        synchronized (s_lock) {
            // going to file, so leaving at INFO instead of WARNING is fine
            DEFAULT_LOG_LEVEL = Level.INFO;

            // initialise 's_timers' once ('hidden' label is used to exclude from diags)
            if (s_timers == null) {
                s_timers = new Timers("_SimpleLogger");

                // schedule a disk space check in 30 sec (will reschedule at entry-point)
                s_timers.schedule(ThreadPool.background(), new TimerTask() {

                    @Override
                    public void run() {
                        handleDiskSpaceCheckTimer();
                    }

                }, 30000);
            }

            s_logFolder = folder;
        }
    }
    
    /**
     * Checks for a disk-space shortage.
     * 
     * (timer entry-point)
     */
    private static void handleDiskSpaceCheckTimer() {
        long rescheduleTime = 30 * 60000; // reschedule next check in 30 minutes (default)

        try {
            File folder = s_logFolder;
            if (folder == null)
                return;
            
            capTotalStorage(folder);

            // determine how much free space is available
            long spaceAvailable = folder.getUsableSpace();

            synchronized (s_lock) {
                if (spaceAvailable > MIN_STORAGE_RESERVED) {
                    // more than enough space available
                    if (!s_fileLoggingDeactivated)
                        // nothing's changed, carry on...
                        return;
                    
                    // otherwise, space was previously short and now some space has become available
                    
                    // clear the flag straight away...
                    s_fileLoggingDeactivated = false;                    

                    // ...and notify
                    selfLogger().info("File logging has been reactivated because more than {} MB freespace is available ({} MB freespace is available).", 
                            MIN_STORAGE_RESERVED / 1024 / 1024, spaceAvailable / 1024 / 1024);

                } else {
                    // not enough space available
                    
                    // (check every 5 minutes in this mode)
                    rescheduleTime = 5 * 60000;                    
                    
                    if (s_fileLoggingDeactivated)
                        // nothing's changed, carry on...
                        return;
                    
                    // force maintenance...
                    SimpleLoggerFactory.shared().requestMaintenance();
                    
                    // ... and recheck
                    spaceAvailable = folder.getUsableSpace();
                    if (spaceAvailable > MIN_STORAGE_RESERVED)
                        // space is now available (possibly not for long, but don't set flag just keep business as usual...)
                        return;
                    
                    // otherwise, space has definitely run out, log the condition...
                    selfLogger().warn("File logging has been temporarily suspended because less than {} MB freespace is available ({} MB freespace is available); will recheck every {}.", 
                            MIN_STORAGE_RESERVED / 1024 / 1024, spaceAvailable / 1024 / 1024, DateTimes.formatShortDuration(rescheduleTime));
                    
                    // ... and deactivate file logging
                    s_fileLoggingDeactivated = true;
                }
            }
        } finally {
            // schedule a disk space check in 30 sec (will reschedule at entry-point)
            s_timers.schedule(ThreadPool.background(), new TimerTask() {

                @Override
                public void run() {
                    handleDiskSpaceCheckTimer();
                }

            }, rescheduleTime);
        }
    }

    /**
     * Cap the amount of total storage being used by removing old files.
     */
    private static void capTotalStorage(File folder) {
        try {
            // list all the log files
            File[] logFiles = folder.listFiles(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String filename) {
                    return filename.toLowerCase().endsWith(LOGFILE_EXT);
                }

            });
            
            // ... and sort by date
            Arrays.sort(logFiles, FILES_MOSTRECENT_FIRST);
            
            // start from the top
            long storageLeft = MAX_STORAGE;
            
            // record total storage used
            long storageUsed = 0;
            
            int filesDeleted = 0;
            long storageFreed = 0;
            
            // go through all the files, most recent first
            // deleting them once too much storage count exceeds limit
            for(File logfile : logFiles) {
                long size = logfile.length();
                if (size <= 0)
                    continue;
                
                storageLeft -= size;
                storageUsed += size;
                
                if (storageLeft < 0) {
                    // delete the file
                    if (!logfile.delete())
                        continue;
                    
                    storageUsed -= size;
                    
                    filesDeleted++;
                    storageFreed = size;
                }
            }
            
            if (filesDeleted > 0) {
                selfLogger().warn("Log files have been trimmed in an attempt to keep total storage (now {}) within a {} limit. ({} files were deleted or {} worth).", 
                        Formatting.formatByteLength(storageUsed), Formatting.formatByteLength(MAX_STORAGE), filesDeleted, Formatting.formatByteLength(storageFreed));                
            }
        } catch (Exception exc) {
            // must be exception free
        }
    }
    
    /**
     * A static comparator, files oldest first.
     */
    private static Comparator<File> FILES_OLDEST_FIRST =  new Comparator<File>() {

        @Override
        public int compare(File lhs, File rhs) {
            long lMod = lhs.lastModified();
            long rMod = rhs.lastModified();

            if (lMod > rMod)
                return 1;
            else if (lMod == rMod)
                return 0;
            else
                return -1;
        }

    };
    
    /**
     * A static comparator, files most recent first.
     */
    private static Comparator<File> FILES_MOSTRECENT_FIRST =  new Comparator<File>() {

        @Override
        public int compare(File lhs, File rhs) {
            long lMod = lhs.lastModified();
            long rMod = rhs.lastModified();

            if (lMod > rMod)
                return -1;
            else if (lMod == rMod)
                return 0;
            else
                return 1;
        }

    };    

    /**
     * Tidy up generated log files.
     */
    private void tidyUpLogFiles() {
        File folder = s_logFolder;
        if (folder == null)
            return;
        
        final String prefix = getFilePrefix();
        
        // list the files...
        File[] logFiles = folder.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String filename) {
                return filename.toLowerCase().endsWith(LOGFILE_EXT) && filename.startsWith(prefix);
            }

        });

        // ... and sort by date
        Arrays.sort(logFiles, FILES_OLDEST_FIRST);

        int ALLOWED = 240; // 10 days worth

        int toDelete = logFiles.length - ALLOWED;

        if (toDelete > 0) {
            for (File logfile : logFiles) {
                logfile.delete();

                toDelete--;
                if (toDelete <= 0)
                    break;
            }
        }
    }
    
    /**
     * Returns the filename prefix based on this logger's settings.
     */
    private String getFilePrefix() {
        if (_group == null)
            return "general";
        else
            return _group;
    }
    
    /**
     * Returns the file name based on the roll-over rules.
     */
    private String getCurrentFilename(DateTime timestamp) {
        return String.format("%s_%s%s", getFilePrefix(), timestamp.toString("YYYY-MM-dd_HH'h00"), LOGFILE_EXT);
    }
    
    /**
     * (assumes s_logFile is set)
     */
    private void writeToFile(String formattedTimestamp, LogEntry le) {
        String currentFileName = getCurrentFilename(le.timestamp);
        
        long seq = s_sequenceCounter.getAndIncrement();

        try {
            synchronized (_fileLock) {
                if (_maintenanceTimerTask == null) {
                    // schedule a maintenance operation in 1 min, then every 12 hours.
                    _maintenanceTimerTask = s_timers.schedule(ThreadPool.background(), new TimerTask() {

                        @Override
                        public void run() {
                            handleMaintainanceTimer();
                        }

                    }, 60000, 12 * 3600 * 1000);
                }
                
                // if diskspace is short, don't log to file
                if (s_fileLoggingDeactivated)
                    return;
                
                if (_lastFileName == null) {
                    // is very first time in this process or the file was automatically closed,
                    // so append if necessary
                    File newLogFile = new File(s_logFolder, currentFileName);

                    _writer = new PrintWriter(new FileWriter(newLogFile, true));
                    
                    // put header as first line
                    if (newLogFile.length() == 0)
                        _writer.format("# %s\t%s\t%s\t%s\t[%s]\t%s%n", "seq", "timestamp", "level", "tag", "thread", "message");

                    _lastFileName = currentFileName;
                    
                } else if (!currentFileName.equals(_lastFileName)) {
                    // rolling over...

                    // (release previous file)
                    if (_writer != null)
                        _writer.close();

                    // (prepare for new one)
                    File newLogFile = new File(s_logFolder, currentFileName);

                    // (delete the file we're rolling into)
                    if (newLogFile.exists()) {
                        if (!newLogFile.delete()) {
                            // could not delete; something has gone wrong so
                            // fail silently
                            return;
                        }
                    }

                    _writer = new PrintWriter(new FileWriter(newLogFile, true));
                    
                    if (newLogFile.length() == 0)
                        _writer.format("# %s\t%s\t%s\t%s\t[%s]\t%s%n", "seq", "timestamp", "level", "tag", "thread", "message");

                    _lastFileName = currentFileName;
                    
                } else {
                    // file name is the same, so use what's already set up
                    // (fall through)
                }
            }

            // if we're here, s_writer will not be null

            if (le.error == null)
                _writer.format("%s\t%s\t%s\t[%s]\t[%s]\t%s%n", seq, formattedTimestamp, le.level, le.tag, Thread.currentThread().getName(), le.message);
            else
                // dump full stack trace
                _writer.format("%s\t%s\t%s\t[%s]\t[%s]\t%s\n%s%n", seq, formattedTimestamp, le.level, le.tag, Thread.currentThread().getName(), le.message, le.error);

            _writer.flush();
            
        } catch (Exception exc) {
            // fail silently here
        }
    }
    
    private void writeToStdErr(String formattedTimestamp, LogEntry le) {
        if (le.error == null)
            System.err.format("%s %s [%s] [%s] %s\n", formattedTimestamp, le.level, le.tag, Thread.currentThread().getName(), le.message);
        else
            // dump full stack trace
            System.err.format("%s %s [%s] [%s] %s\n%s\n", formattedTimestamp, le.level, le.tag, Thread.currentThread().getName(), le.message, le.error);
    }
    
    /**
     * Used to access the logger for this class lazily.
     * (convenience function)
     */
    private static Logger selfLogger() {
        synchronized(s_lock) {
            if (s_logger == null)
                s_logger = LoggerFactory.getLogger(SimpleLogger.class.getName());
        }
        
        return s_logger;
    }

    /**
     * Turns a name into a safe filename.
     * 
     * TODO: reserved for future use.
     */
    @SuppressWarnings("unused")
    private static String safeFilename(String name) {
        StringBuilder sb = new StringBuilder(name);
        int len = name.length();
        for (int a = 0; a < len; a++) {
            char c = name.charAt(a);
            if (c == '.')
                sb.append(c);
            else if (Character.isLetterOrDigit(c))
                sb.append(c);
        }

        return sb.toString();
    }

    /**
     * Writes to the global logger.
     */
    private static void writeToNodelLogging(LogEntry logEntry) {
        Logging.instance().addLog(logEntry);
    }

    /**
     * Captures an exception's stack-trace.
     */
    private static String captureStackTrace(Throwable currentExc) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        currentExc.printStackTrace(pw);

        pw.flush();

        return sw.toString();
    }

    /**
     * Perform best effort maintenance normally if low-disk space is detected.
     * (Might perform blocking IO)
     */
    public void requestMaintenance() {
        handleMaintainanceTimer();
    }

}
