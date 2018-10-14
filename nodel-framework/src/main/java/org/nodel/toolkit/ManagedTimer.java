package org.nodel.toolkit;

import java.io.Closeable;
import java.io.IOException;

import org.nodel.Handler.H0;
import org.nodel.Handler.H1;
import org.nodel.threading.CallbackQueue;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;

public class ManagedTimer implements Closeable {
    
    private Object _lock = new Object();
    
    public boolean _shutdown;
    
    /**
     * The shares timer thread (must minimise time spend on this thread)
     */
    private Timers _timerThread;
    
    /**
     * A shared thread pool to use.
     */
    private ThreadPool _threadPool;

    /**
     * When unhandled exceptions occur
     */
    private H1<Exception> _exceptionHandler;

    /**
     * Callback queue
     */
    private CallbackQueue _callbackQueue;
    
    /**
     * The registered callback (fixed for this timer)
     */
    private H0 _callback;
    
    /**
     * Holds the current timer task
     */
    private TimerTask _timerTask;
    
    /**
     * First delay to use (one-time use only, reset using 'setDelay')
     */
    private long _delay;
    
    /**
     * The current first delay
     */
    private long _currentDelay;
    
    /**
     * Interval value to use when started.
     */
    private long _interval;
    
    /**
     * The current interval
     */
    private long _currentInterval;
    
    /**
     * Do not start on creation
     * (carries flag only, not used within this class) 
     */
    private boolean _stoppedAtFirst;    

    /**
     * Sets up the thread state.
     */
    private H0 _threadStateHandler;

    public ManagedTimer(H0 callback, boolean stoppedAtFirst, H0 threadStateHandler, Timers timerThreads, ThreadPool threadPool, H1<Exception> exceptionHandler, CallbackQueue callbackQueue) {
        _threadStateHandler = threadStateHandler;
        _timerThread = timerThreads;
        _threadPool = threadPool;
        _exceptionHandler = exceptionHandler;
        _callbackQueue = callbackQueue;
        
        _stoppedAtFirst = stoppedAtFirst;
        _callback = callback;
    }
    
    /**
     * Schedules the next timer
     */
    private void scheduleTimer(long delay, final long interval) {
        synchronized(_lock) {
            if (_timerTask != null)
                _timerTask.cancel();
            
            _currentDelay = delay;
            _currentInterval = interval;
            
            final TimerTask timerTask = new TimerTask() {
                
                TimerTask _self = this;

                @Override
                public void run() {
                    synchronized (_lock) {
                        if (_shutdown || _self.isCancelled())
                            return;
                    }
                    
                    // callback can block so execute on a threadpool
                    _threadPool.execute(new Runnable() {

                        @Override
                        public void run() {
                            _threadStateHandler.handle();
                            
                            _callbackQueue.handle(_callback, _exceptionHandler);
                            
                            synchronized (_lock) {
                                if (_currentInterval <= 0 || _self.isCancelled())
                                    return;

                                // continually use 'interval' for next schedule
                                _timerThread.schedule(_self, _currentInterval);
                            }                            
                        }
                        
                    }); // (.execute)
                }

            }; // (new TimerTask)
            
            if (delay > 0) {
                 // use delay for next schedule
                _timerTask = _timerThread.schedule(timerTask, delay);
                
                // reset first delay
                _delay = 0;
                
            } else {
                if (interval > 0)
                    // use interval
                    _timerTask = _timerThread.schedule(timerTask, interval);
            }
            
            // if interval and delay are zero, the timer will not be active
        }
    }
    
    /**
     * Sets the delay and interval.
     */
    public void setDelayAndInterval(long delay, final long interval) {
        synchronized (_lock) {
            _delay = delay;
            _interval = interval;
            
            if (_currentDelay > 0 || _currentInterval > 0)
                scheduleTimer(delay, interval);
        }
    }    
    
    /**
     * Resets the interval.
     */
    public void setInterval(long value) {
        synchronized(_lock) {
            _interval = value;
            
            if (_currentDelay > 0 || _currentInterval > 0)
                scheduleTimer(0, value);
        }
    }
    
    /**
     * Sets the delay (reset the timer if necessary)
     */
    public void setDelay(long value) {
        synchronized(_lock) {
            _delay = value;
            
            if (_currentDelay > 0 || _currentInterval > 0)
                scheduleTimer(value, _currentInterval);
        }
    }    
    
    /**
     * Safely resets this timer (does not fire) to the current interval
     */
    public void reset() {
        synchronized (_lock) {
            setInterval(_interval);
        }
    }
    
    /**
     * Starts the repeating timer if it hasn't already been started. If it's the first time
     * this timer has even been started, a first delay applies.
     */
    public void start() {
        synchronized (_lock) {
            if (_currentDelay > 0 || _currentInterval > 0)
                // has started, nothing to do
                return;

            // ('delay' is always cleared on use. 'setDelay___' resets)
            scheduleTimer(_delay, _interval);
        }
    }
    
    /**
     * Stops / pauses / suspends this timer.
     */
    public void stop() {
        scheduleTimer(0, 0);
    }
    
    public long getInterval() {
        synchronized (_lock) {
            return _interval;
        }
    }

    public long getDelay() {
        synchronized (_lock) {
            return _delay;
        }
    }
    
    /**
     * Be stopped on creation 
     */
    public boolean getStoppedAtFirst() {
        return _stoppedAtFirst;
    }

    public boolean isStarted() {
        synchronized (_lock) {
            return _currentDelay > 0 || _currentInterval > 0;
        }
    }

    public boolean isStopped() {
        synchronized (_lock) {
            return _currentDelay <= 0 && _currentInterval <= 0;
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (_lock) {
            if (!_shutdown)
                _shutdown = true;

            _timerTask.cancel();
        }
    }
    
}
