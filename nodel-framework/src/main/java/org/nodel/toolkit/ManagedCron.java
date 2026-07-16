package org.nodel.toolkit;

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.joda.time.DateTime;
import org.nodel.Handler.H0;
import org.nodel.Handler.H1;
import org.nodel.cron.CronExpressions;
import org.nodel.threading.CallbackQueue;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;

import com.cronutils.model.Cron;

/**
 * A managed CRON schedule (see CronExpressions for the expression rules), sharing the
 * pooled timer infrastructure — no dedicated thread per schedule.
 *
 * Each fire schedules the next occurrence. A fire that arrives late (e.g. after system
 * suspension or heavy load) still occurs once while the host remains running; occurrences
 * missed in the meantime are coalesced into it, never replayed. A schedule created after
 * a host or node restart looks forward only.
 */
public class ManagedCron implements Closeable {

    /**
     * When a fire arrives earlier than this ahead of its target (e.g. wall-clock
     * adjustment), the remainder is re-slept instead of firing.
     */
    private final static long EARLY_FIRE_TOLERANCE = 500;

    private Object _lock = new Object();

    private boolean _shutdown;

    /**
     * The shared timer thread (must minimise time spent on this thread)
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
     * The registered callback (fixed for this schedule)
     */
    private H0 _callback;

    /**
     * Sets up the thread state.
     */
    private H0 _threadStateHandler;

    /**
     * Do not start on creation
     * (carries flag only, not used within this class)
     */
    private boolean _stoppedAtFirst;

    /**
     * The expression, as provided.
     */
    private String _expression;

    /**
     * (parsed and validated form of '_expression')
     */
    private Cron _cron;

    /**
     * The effective timezone (the host timezone unless one was provided).
     */
    private ZoneId _zone;

    /**
     * Holds the current timer task
     */
    private TimerTask _timerTask;

    /**
     * Whether the schedule is running.
     */
    private boolean _started;

    /**
     * The instant (millis) the pending task is aimed at (0 when none is pending).
     */
    private long _nextFireMillis;

    /**
     * When the callback actually last fired (null if never).
     */
    private DateTime _lastFired;

    /**
     * (throws IllegalArgumentException on an invalid expression or timezone)
     */
    public ManagedCron(H0 callback, String expression, String timeZone, boolean stoppedAtFirst, H0 threadStateHandler, Timers timerThreads, ThreadPool threadPool, H1<Exception> exceptionHandler, CallbackQueue callbackQueue) {
        _threadStateHandler = threadStateHandler;
        _timerThread = timerThreads;
        _threadPool = threadPool;
        _exceptionHandler = exceptionHandler;
        _callbackQueue = callbackQueue;

        _stoppedAtFirst = stoppedAtFirst;
        _callback = callback;

        _cron = CronExpressions.parse(expression);
        _expression = expression.trim();

        _zone = CronExpressions.resolveTimeZone(timeZone);
    }

    /**
     * The wall-clock (overridable for deterministic testing).
     */
    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * ('currentTimeMillis' as a zoned timestamp)
     */
    private ZonedDateTime now() {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis()), _zone);
    }

    /**
     * Schedules the next occurrence strictly after 'from' (lock must be held).
     */
    private void scheduleNext(ZonedDateTime from) {
        if (_timerTask != null)
            _timerTask.cancel();

        _timerTask = null;
        _nextFireMillis = 0;

        ZonedDateTime next = CronExpressions.nextOccurrence(_cron, from);
        if (next == null)
            // can never fire (e.g. 'Feb 30'); remains started but dormant
            return;

        long target = next.toInstant().toEpochMilli();
        _nextFireMillis = target;

        final TimerTask timerTask = new TimerTask() {

            TimerTask _self = this;

            @Override
            public void run() {
                long target;

                synchronized (_lock) {
                    if (_shutdown || _self.isCancelled() || !_started)
                        return;

                    target = _nextFireMillis;

                    long now = currentTimeMillis();
                    if (target - now > EARLY_FIRE_TOLERANCE) {
                        // woke early (e.g. wall-clock adjusted backwards); sleep out the remainder
                        _timerThread.schedule(_self, target - now);
                        return;
                    }
                }

                // callback can block so execute on a thread-pool
                _threadPool.execute(new Runnable() {

                    @Override
                    public void run() {
                        synchronized (_lock) {
                            if (_shutdown || _self.isCancelled() || !_started)
                                return;
                        }

                        _threadStateHandler.handle();

                        _callbackQueue.handle(_callback, _exceptionHandler);

                        synchronized (_lock) {
                            if (_shutdown || _self.isCancelled() || !_started)
                                return;

                            _lastFired = DateTime.now();

                            // next occurrence from the later of 'now' and the target just fired:
                            // coalesces any occurrences missed while delayed and can never
                            // re-fire the occurrence that just completed
                            long basis = Math.max(currentTimeMillis(), target);
                            scheduleNext(ZonedDateTime.ofInstant(Instant.ofEpochMilli(basis), _zone));
                        }
                    }

                }); // (.execute)
            }

        }; // (new TimerTask)

        _timerTask = _timerThread.schedule(timerTask, Math.max(1, target - currentTimeMillis()));
    }

    /**
     * Starts the schedule if it hasn't already been started.
     */
    public void start() {
        synchronized (_lock) {
            if (_shutdown || _started)
                return;

            _started = true;

            scheduleNext(now());
        }
    }

    /**
     * Stops / pauses / suspends this schedule.
     */
    public void stop() {
        synchronized (_lock) {
            if (_timerTask != null)
                _timerTask.cancel();

            _timerTask = null;
            _nextFireMillis = 0;
            _started = false;
        }
    }

    /**
     * Reconfigures the expression (throws IllegalArgumentException when invalid;
     * the previous schedule remains in place on failure).
     */
    public void setExpression(String expression) {
        Cron cron = CronExpressions.parse(expression);

        synchronized (_lock) {
            _cron = cron;
            _expression = expression.trim();

            if (_started)
                scheduleNext(now());
        }
    }

    public String getExpression() {
        synchronized (_lock) {
            return _expression;
        }
    }

    /**
     * Reconfigures the timezone — an IANA timezone, or null / empty for the host timezone
     * (throws IllegalArgumentException when unknown; the previous schedule remains in place on failure).
     */
    public void setTimeZone(String timeZone) {
        ZoneId zone = CronExpressions.resolveTimeZone(timeZone);

        synchronized (_lock) {
            _zone = zone;

            if (_started)
                scheduleNext(now());
        }
    }

    /**
     * The effective IANA timezone.
     */
    public String getTimeZone() {
        synchronized (_lock) {
            return _zone.getId();
        }
    }

    /**
     * A human-readable description of the schedule.
     */
    public String getDescription() {
        synchronized (_lock) {
            return CronExpressions.describe(_cron);
        }
    }

    /**
     * When this schedule will next fire (null when stopped or when it can never fire).
     */
    public DateTime getNextExecution() {
        synchronized (_lock) {
            if (_nextFireMillis == 0)
                return null;

            return CronExpressions.toDateTime(ZonedDateTime.ofInstant(Instant.ofEpochMilli(_nextFireMillis), _zone));
        }
    }

    /**
     * The most recent occurrence of the expression before now, regardless of
     * whether the schedule was running at the time (null if none).
     */
    public DateTime getPreviousExecution() {
        Cron cron;
        ZonedDateTime from;
        synchronized (_lock) {
            cron = _cron;
            from = now();
        }

        // the backwards search can span a long calendar range for rarely-matching
        // expressions, so it must not hold up the fire/reconfigure paths
        return CronExpressions.toDateTime(CronExpressions.previousOccurrence(cron, from));
    }

    /**
     * When the callback actually last fired (null if never).
     */
    public DateTime getLastFired() {
        synchronized (_lock) {
            return _lastFired;
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
            return _started;
        }
    }

    public boolean isStopped() {
        synchronized (_lock) {
            return !_started;
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (_lock) {
            if (_shutdown)
                return;

            _shutdown = true;
            _started = false;
            _nextFireMillis = 0;

            if (_timerTask != null)
                _timerTask.cancel();
        }
    }

}
