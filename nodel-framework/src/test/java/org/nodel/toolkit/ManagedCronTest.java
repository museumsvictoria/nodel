package org.nodel.toolkit;

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nodel.GitHubIssue;
import org.nodel.Handler.H0;
import org.nodel.threading.CallbackQueue;
import org.nodel.threading.CapturingTimers;
import org.nodel.threading.ThreadPool;

/**
 * Deterministic scheduling tests: time is simulated and the timer layer is captured,
 * so fires are driven by hand at exact simulated instants.
 */
@GitHubIssue("https://github.com/museumsvictoria/nodel/issues/411")
public class ManagedCronTest {

    private final static ZoneId MELBOURNE = ZoneId.of("Australia/Melbourne");

    /**
     * A fixed reference point: Thursday 2026-07-16 10:00:30 (Melbourne).
     */
    private final static ZonedDateTime T0 = ZonedDateTime.of(2026, 7, 16, 10, 0, 30, 0, MELBOURNE);

    private CapturingTimers _timers;

    /**
     * Executes inline so tests stay single-threaded and deterministic.
     * (one static instance: ThreadPool registers a uniquely-named diagnostics counter)
     */
    private final static ThreadPool s_inlinePool = new ThreadPool("_inline (test)", 1) {

        @Override
        public void execute(Runnable runnable) {
            runnable.run();
        }

    };

    private CallbackQueue _callbackQueue;

    private AtomicInteger _fires;

    private long _nowMillis;

    private final static H0 NO_OP = new H0() {

        @Override
        public void handle() {
        }

    };

    @BeforeEach
    public void setup() {
        _timers = new CapturingTimers();
        _callbackQueue = new CallbackQueue();
        _fires = new AtomicInteger();
        _nowMillis = T0.toInstant().toEpochMilli();
    }

    private ManagedCron createCron(String expression, String timeZone, boolean stopped) {
        H0 callback = new H0() {

            @Override
            public void handle() {
                _fires.incrementAndGet();
            }

        };

        H1Sink exceptions = new H1Sink();

        return new ManagedCron(callback, expression, timeZone, stopped, NO_OP, _timers, s_inlinePool, exceptions, _callbackQueue) {

            @Override
            protected long currentTimeMillis() {
                return _nowMillis;
            }

        };
    }

    private static class H1Sink implements org.nodel.Handler.H1<Exception> {

        @Override
        public void handle(Exception value) {
        }

    }

    private static long millis(ZonedDateTime value) {
        return value.toInstant().toEpochMilli();
    }

    // construction and validation

    @Test
    public void rejectsInvalidExpressionAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> createCron("61 * * * *", null, false));
        assertThrows(IllegalArgumentException.class, () -> createCron("* * * *", null, false));
    }

    @Test
    public void rejectsUnknownTimezoneAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> createCron("* * * * *", "Not/AZone", false));
    }

    // start / stop lifecycle

    @Test
    public void startSchedulesTheNextMinuteBoundary() {
        ManagedCron cron = createCron("* * * * *", "Australia/Melbourne", false);
        assertTrue(cron.isStopped());
        assertNull(cron.getNextExecution());

        cron.start();

        assertTrue(cron.isStarted());

        // T0 is hh:00:30, so the next occurrence is hh:01:00 — 30 s away
        ZonedDateTime expected = ZonedDateTime.of(2026, 7, 16, 10, 1, 0, 0, MELBOURNE);
        assertEquals(millis(expected), cron.getNextExecution().getMillis());
        assertEquals(1, _timers.scheduled.size());
        assertEquals(30_000, _timers.last().delay);
    }

    @Test
    public void startIsIdempotent() {
        ManagedCron cron = createCron("* * * * *", null, false);
        cron.start();
        cron.start();

        assertEquals(1, _timers.scheduled.size());
    }

    @Test
    public void stopClearsThePendingFire() {
        ManagedCron cron = createCron("* * * * *", null, false);
        cron.start();
        cron.stop();

        assertTrue(cron.isStopped());
        assertNull(cron.getNextExecution());
        assertTrue(_timers.last().task.isCancelled());
    }

    @Test
    public void stoppedTaskDoesNotFire() {
        ManagedCron cron = createCron("* * * * *", null, false);
        cron.start();

        CapturingTimers.Scheduled pending = _timers.last();
        cron.stop();

        // even if the timer layer still delivers it
        _nowMillis += 30_000;
        pending.task.run();

        assertEquals(0, _fires.get());
    }

    // firing and rescheduling

    @Test
    public void firesOnTimeAndReschedules() {
        ManagedCron cron = createCron("* * * * *", "Australia/Melbourne", false);
        cron.start();

        // deliver exactly on target
        _nowMillis = millis(ZonedDateTime.of(2026, 7, 16, 10, 1, 0, 0, MELBOURNE));
        _timers.last().task.run();

        assertEquals(1, _fires.get());
        assertNotNull(cron.getLastFired());

        // rescheduled for the following minute
        ZonedDateTime following = ZonedDateTime.of(2026, 7, 16, 10, 2, 0, 0, MELBOURNE);
        assertEquals(millis(following), cron.getNextExecution().getMillis());
        assertEquals(2, _timers.scheduled.size());
        assertEquals(60_000, _timers.last().delay);
    }

    @Test
    public void delayedFireCoalescesToASingleInvocation() {
        // a fire that arrives late — e.g. the host was suspended — happens once,
        // and the occurrences missed in between are never replayed
        ManagedCron cron = createCron("* * * * *", "Australia/Melbourne", false);
        cron.start();

        // deliver 5 minutes late
        _nowMillis = millis(ZonedDateTime.of(2026, 7, 16, 10, 6, 0, 0, MELBOURNE));
        _timers.last().task.run();

        assertEquals(1, _fires.get());

        // next fire looks forward from 'now' — 10:01 through 10:06 are gone
        ZonedDateTime lookedForward = ZonedDateTime.of(2026, 7, 16, 10, 7, 0, 0, MELBOURNE);
        assertEquals(millis(lookedForward), cron.getNextExecution().getMillis());
    }

    @Test
    public void earlyWakeSleepsOutTheRemainderWithoutFiring() {
        // guards against wall-clock adjustments waking the timer layer prematurely
        ManagedCron cron = createCron("* * * * *", "Australia/Melbourne", false);
        cron.start();

        CapturingTimers.Scheduled pending = _timers.last();

        // deliver 10 s early
        _nowMillis += 20_000;
        pending.task.run();

        assertEquals(0, _fires.get());
        assertEquals(2, _timers.scheduled.size());
        assertEquals(10_000, _timers.last().delay);

        // the remainder then fires normally
        _nowMillis += 10_000;
        _timers.last().task.run();
        assertEquals(1, _fires.get());
    }

    @Test
    public void freshInstanceBeginsAtTheNextFutureOccurrence() {
        // restart policy: state is never persisted, so a schedule re-created after a
        // host restart looks forward only — no replay of occurrences missed while down
        ManagedCron cron = createCron("0 9 * * MON-FRI", "Australia/Melbourne", false);
        cron.start();

        // T0 is Thursday 10:00:30, after today's 09:00 — first target is Friday 09:00
        ZonedDateTime friday9am = ZonedDateTime.of(2026, 7, 17, 9, 0, 0, 0, MELBOURNE);
        assertEquals(millis(friday9am), cron.getNextExecution().getMillis());
        assertEquals(0, _fires.get());
    }

    // reconfiguration

    @Test
    public void setExpressionReschedulesInPlace() {
        ManagedCron cron = createCron("* * * * *", "Australia/Melbourne", false);
        cron.start();

        cron.setExpression("0 12 * * *");

        assertEquals("0 12 * * *", cron.getExpression());
        ZonedDateTime midday = ZonedDateTime.of(2026, 7, 16, 12, 0, 0, 0, MELBOURNE);
        assertEquals(millis(midday), cron.getNextExecution().getMillis());

        // the superseded task can no longer fire
        assertTrue(_timers.scheduled.get(0).task.isCancelled());
        _timers.scheduled.get(0).task.run();
        assertEquals(0, _fires.get());
    }

    @Test
    public void invalidReconfigurationLeavesScheduleInPlace() {
        ManagedCron cron = createCron("* * * * *", "Australia/Melbourne", false);
        cron.start();

        long before = cron.getNextExecution().getMillis();

        assertThrows(IllegalArgumentException.class, () -> cron.setExpression("nope"));
        assertThrows(IllegalArgumentException.class, () -> cron.setTimeZone("Not/AZone"));

        assertEquals("* * * * *", cron.getExpression());
        assertEquals("Australia/Melbourne", cron.getTimeZone());
        assertEquals(before, cron.getNextExecution().getMillis());
        assertFalse(_timers.last().task.isCancelled());
    }

    @Test
    public void setTimeZoneReschedules() {
        ManagedCron cron = createCron("0 12 * * *", "Australia/Melbourne", false);
        cron.start();

        cron.setTimeZone("Pacific/Auckland");

        assertEquals("Pacific/Auckland", cron.getTimeZone());
        // T0 (10:00:30 Melbourne) is 12:00:30 in Auckland — its midday has just passed
        ZonedDateTime aucklandMidday = ZonedDateTime.of(2026, 7, 17, 12, 0, 0, 0, ZoneId.of("Pacific/Auckland"));
        assertEquals(millis(aucklandMidday), cron.getNextExecution().getMillis());
    }

    @Test
    public void reconfigureWhileStoppedStaysStopped() {
        ManagedCron cron = createCron("* * * * *", null, false);

        cron.setExpression("0 12 * * *");

        assertTrue(cron.isStopped());
        assertNull(cron.getNextExecution());
        assertEquals(0, _timers.scheduled.size());
    }

    // introspection

    @Test
    public void exposesDescriptionAndPreviousExecution() {
        ManagedCron cron = createCron("0 9 * * 1-5", "Australia/Melbourne", false);

        assertEquals("at 09:00 every day between Monday and Friday", cron.getDescription());

        // previous occurrence before Thursday 10:00:30 is Thursday 09:00
        ZonedDateTime thursday9am = ZonedDateTime.of(2026, 7, 16, 9, 0, 0, 0, MELBOURNE);
        assertEquals(millis(thursday9am), cron.getPreviousExecution().getMillis());

        assertNull(cron.getLastFired());
    }

    @Test
    public void defaultTimezoneIsTheHostZone() {
        ManagedCron cron = createCron("* * * * *", null, false);
        assertEquals(ZoneId.systemDefault().getId(), cron.getTimeZone());
    }

    @Test
    public void neverFiringExpressionStaysDormant() {
        ManagedCron cron = createCron("0 0 30 2 *", null, false);
        cron.start();

        assertTrue(cron.isStarted());
        assertNull(cron.getNextExecution());
        assertEquals(0, _timers.scheduled.size());
    }

    @Test
    public void carriesTheStoppedAtFirstFlag() {
        assertTrue(createCron("* * * * *", null, true).getStoppedAtFirst());
        assertFalse(createCron("* * * * *", null, false).getStoppedAtFirst());
    }

    // shutdown

    @Test
    public void closePreventsFurtherUse() throws IOException {
        ManagedCron cron = createCron("* * * * *", null, false);
        cron.start();

        CapturingTimers.Scheduled pending = _timers.last();
        cron.close();

        assertTrue(cron.isStopped());
        assertNull(cron.getNextExecution());

        // a late delivery after shutdown is ignored
        _nowMillis += 60_000;
        pending.task.run();
        assertEquals(0, _fires.get());

        // and it cannot be restarted
        cron.start();
        assertTrue(cron.isStopped());
        assertEquals(1, _timers.scheduled.size());
    }

    @Test
    public void closeBeforeStartIsSafe() throws IOException {
        ManagedCron cron = createCron("* * * * *", null, true);
        cron.close();

        assertTrue(cron.isStopped());
    }

    // daylight-saving behaviour through the managed layer

    @Test
    public void dstGapOccurrenceIsSkipped() {
        // Melbourne spring-forward 2026-10-04: 02:30 does not exist that day
        _nowMillis = millis(ZonedDateTime.of(2026, 10, 3, 12, 0, 0, 0, MELBOURNE));

        ManagedCron cron = createCron("30 2 * * *", "Australia/Melbourne", false);
        cron.start();

        ZonedDateTime afterGap = ZonedDateTime.of(2026, 10, 5, 2, 30, 0, 0, MELBOURNE);
        assertEquals(millis(afterGap), cron.getNextExecution().getMillis());
    }

    @Test
    public void dstOverlapOccurrenceFiresOnce() {
        // Melbourne fall-back 2026-04-05: wall-clock 02:30 happens twice; one fire results
        _nowMillis = millis(ZonedDateTime.of(2026, 4, 4, 12, 0, 0, 0, MELBOURNE));

        ManagedCron cron = createCron("30 2 * * *", "Australia/Melbourne", false);
        cron.start();

        ZonedDateTime firstPass = ZonedDateTime.of(2026, 4, 5, 2, 30, 0, 0, MELBOURNE).withEarlierOffsetAtOverlap();
        assertEquals(millis(firstPass), cron.getNextExecution().getMillis());

        // fire it: the next target is the day after — the repeated wall-clock 02:30 is not revisited
        _nowMillis = millis(firstPass);
        _timers.last().task.run();

        assertEquals(1, _fires.get());
        ZonedDateTime nextDay = ZonedDateTime.of(2026, 4, 6, 2, 30, 0, 0, MELBOURNE);
        assertEquals(millis(nextDay), cron.getNextExecution().getMillis());
    }

}
