package org.nodel.cron;

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.nodel.Strings;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

/**
 * The authoritative CRON expression support for the host: parsing, validation,
 * human-readable descriptions and previous / next execution-time calculation.
 *
 * Standard five-field UNIX expressions apply — minute, hour, day-of-month, month,
 * day-of-week — including names (e.g. 'MON', 'JAN') and Sunday as 0 or 7. The host
 * timezone applies unless an IANA timezone (e.g. 'Australia/Melbourne') is given.
 *
 * Occurrences that fall inside a daylight-saving gap are skipped; an occurrence that
 * wall-clock time visits twice (daylight-saving overlap) applies once, at the first pass.
 */
public class CronExpressions {

    /**
     * Keeps parser and descriptor work bounded for network-facing callers.
     */
    public static final int MAX_EXPRESSION_LENGTH = 512;

    /**
     * Keeps timezone lookup and validation responses bounded.
     */
    public static final int MAX_TIME_ZONE_LENGTH = 128;

    /**
     * Framework-owned compiled form so cron-utils remains an implementation detail.
     */
    public static final class CompiledExpression {

        private final Cron _cron;

        private CompiledExpression(Cron cron) {
            _cron = cron;
        }

    }

    /**
     * (standard five-field UNIX crontab, Sunday as 0 or 7)
     */
    private static final CronDefinition s_definition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);

    /**
     * (thread-safe)
     */
    private static final CronParser s_parser = new CronParser(s_definition);

    /**
     * Parses and validates an expression, throwing with a friendly message on failure.
     */
    public static CompiledExpression parse(String expression) {
        if (expression != null && expression.length() > MAX_EXPRESSION_LENGTH)
            throw new IllegalArgumentException("CRON expression is too long (maximum " + MAX_EXPRESSION_LENGTH + " characters).");

        if (Strings.isBlank(expression))
            throw new IllegalArgumentException("No CRON expression was provided.");

        try {
            return new CompiledExpression(s_parser.parse(expression.trim()).validate());

        } catch (IllegalArgumentException exc) {
            throw new IllegalArgumentException("Invalid CRON expression - " + friendlyMessage(exc), exc);
        }
    }

    /**
     * Whether an expression parses and validates.
     */
    public static boolean isValid(String expression) {
        return validate(expression) == null;
    }

    /**
     * Returns null when the expression is valid, otherwise the failure reason.
     */
    public static String validate(String expression) {
        try {
            parse(expression);
            return null;

        } catch (IllegalArgumentException exc) {
            return exc.getMessage();
        }
    }

    /**
     * A human-readable description of a valid expression e.g. 'at 09:00 every day between Monday and Friday'.
     * (throws IllegalArgumentException when invalid)
     */
    public static String describe(String expression) {
        return describe(parse(expression));
    }

    /**
     * (as above, for an already-parsed expression)
     */
    public static String describe(CompiledExpression expression) {
        return CronDescriptor.instance(Locale.ENGLISH).describe(expression._cron);
    }

    /**
     * Resolves an optional IANA timezone, falling back to the host timezone.
     * (throws IllegalArgumentException when unknown)
     */
    public static ZoneId resolveTimeZone(String timeZone) {
        if (timeZone != null && timeZone.length() > MAX_TIME_ZONE_LENGTH)
            throw new IllegalArgumentException("Timezone is too long (maximum " + MAX_TIME_ZONE_LENGTH + " characters).");

        if (Strings.isBlank(timeZone))
            return ZoneId.systemDefault();

        try {
            return ZoneId.of(timeZone.trim());

        } catch (Exception exc) {
            throw new IllegalArgumentException("Unknown timezone '" + timeZone.trim() + "' - an IANA timezone like 'Australia/Melbourne' is expected.");
        }
    }

    /**
     * The next occurrence strictly after 'from' (or null if the expression can never fire e.g. 'Feb 30').
     */
    public static ZonedDateTime nextOccurrence(CompiledExpression expression, ZonedDateTime from) {
        return nextOccurrence(ExecutionTime.forCron(expression._cron), from);
    }

    /**
     * (as above, reusing an ExecutionTime — construction is not cheap so batch queries should share one)
     */
    private static ZonedDateTime nextOccurrence(ExecutionTime executionTime, ZonedDateTime from) {
        Optional<ZonedDateTime> next = executionTime.nextExecution(from);
        return next.isPresent() ? next.get() : null;
    }

    /**
     * The most recent occurrence strictly before 'from' (or null).
     */
    public static ZonedDateTime previousOccurrence(CompiledExpression expression, ZonedDateTime from) {
        return previousOccurrence(ExecutionTime.forCron(expression._cron), from);
    }

    /**
     * (as above, reusing an ExecutionTime)
     */
    private static ZonedDateTime previousOccurrence(ExecutionTime executionTime, ZonedDateTime from) {
        Optional<ZonedDateTime> previous = executionTime.lastExecution(from);
        return previous.isPresent() ? previous.get() : null;
    }

    /**
     * The next execution after now (or null), e.g. for recipe use.
     * (throws IllegalArgumentException on an invalid expression or timezone)
     */
    public static DateTime nextExecution(String expression, String timeZone) {
        CompiledExpression cron = parse(expression);
        ZoneId zone = resolveTimeZone(timeZone);
        return toDateTime(nextOccurrence(cron, ZonedDateTime.now(zone)));
    }

    /**
     * The most recent execution before now (or null), e.g. for recipe use.
     * (throws IllegalArgumentException on an invalid expression or timezone)
     */
    public static DateTime previousExecution(String expression, String timeZone) {
        CompiledExpression cron = parse(expression);
        ZoneId zone = resolveTimeZone(timeZone);
        return toDateTime(previousOccurrence(cron, ZonedDateTime.now(zone)));
    }

    /**
     * A full, never-throwing summary for UI consumers — validity, description,
     * effective timezone and previous / upcoming execution times.
     */
    public static CronInfo summarize(String expression, String timeZone) {
        CronInfo info = new CronInfo();
        if (expression == null || expression.length() <= MAX_EXPRESSION_LENGTH)
            info.expression = expression;

        CompiledExpression cron;
        ZoneId zone;
        try {
            cron = parse(expression);
            zone = resolveTimeZone(timeZone);

        } catch (IllegalArgumentException exc) {
            info.error = exc.getMessage();
            return info;
        }

        info.valid = true;
        info.timeZone = zone.getId();

        try {
            info.description = describe(cron);
        } catch (Exception exc) {
            // never let a description quirk break the summary
        }

        ExecutionTime executionTime = ExecutionTime.forCron(cron._cron);

        ZonedDateTime now = ZonedDateTime.now(zone);
        info.previous = toDateTime(previousOccurrence(executionTime, now));

        ZonedDateTime next = nextOccurrence(executionTime, now);
        info.next = toDateTime(next);

        if (next != null) {
            DateTime[] upcoming = new DateTime[CronInfo.UPCOMING_COUNT];
            upcoming[0] = info.next;

            ZonedDateTime occurrence = next;
            for (int i = 1; i < CronInfo.UPCOMING_COUNT && occurrence != null; i++) {
                occurrence = nextOccurrence(executionTime, occurrence);
                upcoming[i] = toDateTime(occurrence);
            }
            info.upcoming = upcoming;
        }

        return info;
    }

    /**
     * (java.time to the JODATIME convention used throughout the framework)
     */
    public static DateTime toDateTime(ZonedDateTime value) {
        if (value == null)
            return null;

        DateTimeZone zone;
        try {
            zone = DateTimeZone.forID(value.getZone().getId());

        } catch (IllegalArgumentException exc) {
            // the JODATIME database may trail java.time; preserve the instant using a fixed offset
            zone = DateTimeZone.forOffsetMillis(value.getOffset().getTotalSeconds() * 1000);
        }

        return new DateTime(value.toInstant().toEpochMilli(), zone);
    }

    /**
     * (strips the exception-class prefixes cron-utils nests into its messages)
     */
    private static String friendlyMessage(Exception exc) {
        String message = exc.getMessage();
        if (Strings.isBlank(message))
            return "could not be parsed.";

        // e.g. "Failed to parse cron expression. Value 60 not in range [0, 59]"
        return message.replace("Failed to parse cron expression. ", "")
                      .replace("cron expression contains", "expression contains")
                      .replace("Cron expression contains", "expression contains");
    }

}
