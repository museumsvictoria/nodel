package org.nodel.cron;

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import org.joda.time.DateTime;
import org.nodel.reflection.Value;

/**
 * A serialisable summary of a CRON expression for UI and REST consumers.
 * (see CronExpressions.summarize)
 */
public class CronInfo {

    /**
     * The number of execution times carried by 'upcoming'.
     */
    public final static int UPCOMING_COUNT = 5;

    @Value(name = "expression", order = 1, title = "Expression", desc = "The five-field UNIX CRON expression, as provided.", required = false)
    public String expression;

    @Value(name = "valid", order = 2, title = "Valid", desc = "Whether the expression parsed and validated.")
    public boolean valid;

    @Value(name = "error", order = 3, title = "Error", desc = "The failure reason when not valid.", required = false)
    public String error;

    @Value(name = "description", order = 4, title = "Description", desc = "A human-readable description of the schedule.", required = false)
    public String description;

    @Value(name = "timeZone", order = 5, title = "Timezone", desc = "The effective IANA timezone.", required = false)
    public String timeZone;

    @Value(name = "previous", order = 6, title = "Previous", desc = "The most recent execution time before now (if any).", required = false)
    public DateTime previous;

    @Value(name = "next", order = 7, title = "Next", desc = "The next execution time (absent when the schedule can never fire).", required = false)
    public DateTime next;

    @Value(name = "upcoming", order = 8, title = "Upcoming", desc = "The next few execution times, starting with 'next'.", required = false, genericClassA = DateTime.class)
    public DateTime[] upcoming;

}
