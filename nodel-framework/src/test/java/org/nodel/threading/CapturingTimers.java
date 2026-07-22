package org.nodel.threading;

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import java.util.ArrayList;
import java.util.List;

/**
 * A test-only Timers that records schedule requests instead of arming real
 * timer threads, letting tests fire tasks deterministically.
 * (lives in this package for access to 'TimerTask.nativeTimerTask')
 */
public class CapturingTimers extends Timers {

    public static class Scheduled {

        public final TimerTask task;

        public final long delay;

        Scheduled(TimerTask task, long delay) {
            this.task = task;
            this.delay = delay;
        }

    }

    public final List<Scheduled> scheduled = new ArrayList<>();

    public CapturingTimers() {
        // '_' prefix keeps it out of the diagnostics framework
        super("_capturing");
    }

    @Override
    public TimerTask schedule(TimerTask task, long delay) {
        // a placeholder native task so 'TimerTask.cancel' has something to cancel
        task.nativeTimerTask = new java.util.TimerTask() {

            @Override
            public void run() {
            }

        };

        scheduled.add(new Scheduled(task, delay));

        return task;
    }

    /**
     * The most recent schedule request.
     */
    public Scheduled last() {
        return scheduled.isEmpty() ? null : scheduled.get(scheduled.size() - 1);
    }

}
