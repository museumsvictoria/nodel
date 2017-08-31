package org.nodel.threading;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * The specialised timer task class for use with the Timers class in this package.
 */
public abstract class TimerTask implements Runnable {

    private boolean _cancelled;

    /**
     * The native Java timer-task.
     */
    java.util.TimerTask nativeTimerTask;

    /**
     * The timer callback.
     */
    public abstract void run();

    /**
     * Cancels this timer.
     */
    public void cancel() {
        _cancelled = true;
        this.nativeTimerTask.cancel();
    }

    public boolean isCancelled() {
        return _cancelled;
    }

} // (method)
