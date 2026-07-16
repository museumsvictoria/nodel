package org.nodel.cron;

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

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.nodel.GitHubIssue;

import com.cronutils.model.Cron;

@GitHubIssue("https://github.com/museumsvictoria/nodel/issues/411")
public class CronExpressionsTest {

    private final static ZoneId MELBOURNE = ZoneId.of("Australia/Melbourne");

    private final static ZoneId LONDON = ZoneId.of("Europe/London");

    private static ZonedDateTime next(String expression, ZonedDateTime from) {
        return CronExpressions.nextOccurrence(CronExpressions.parse(expression), from);
    }

    private static ZonedDateTime previous(String expression, ZonedDateTime from) {
        return CronExpressions.previousOccurrence(CronExpressions.parse(expression), from);
    }

    // syntax and validation

    @ParameterizedTest
    @ValueSource(strings = {
            "* * * * *",
            "0 9 * * 1-5",
            "*/15 8-18 * * MON-FRI",
            "0 0 1 JAN,JUL *",
            "30 4 1,15 * 5",
            "0 22 * * 0",
            "0 22 * * 7",
            "0 22 * * SUN",
            "5 0 * 8 *",
            "0 0 30 2 *",
            "  * * * * *  " })
    public void acceptsStandardFiveFieldExpressions(String expression) {
        assertTrue(CronExpressions.isValid(expression), expression);
        assertNull(CronExpressions.validate(expression), expression);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "* * * *",
            "* * * * * *",
            "60 * * * *",
            "* 24 * * *",
            "* * 0 * *",
            "* * * 13 *",
            "* * * * 8",
            "a b c d e",
            "*/0 * * * *",
            "1-5-7 * * * *",
            "@daily" })
    public void rejectsInvalidExpressions(String expression) {
        assertFalse(CronExpressions.isValid(expression), expression);
        assertNotNull(CronExpressions.validate(expression), expression);
        assertThrows(IllegalArgumentException.class, () -> CronExpressions.parse(expression), expression);
    }

    @Test
    public void rejectsNull() {
        assertFalse(CronExpressions.isValid(null));
        assertThrows(IllegalArgumentException.class, () -> CronExpressions.parse(null));
    }

    @Test
    public void validationMessagesCarryTheReason() {
        assertTrue(CronExpressions.validate("60 * * * *").contains("[0, 59]"));
        assertTrue(CronExpressions.validate("* * * *").contains("4"));
    }

    // human-readable descriptions

    @Test
    public void describesCommonExpressions() {
        assertEquals("every minute", CronExpressions.describe("* * * * *"));
        assertEquals("at 09:00 every day between Monday and Friday", CronExpressions.describe("0 9 * * 1-5"));
        assertEquals("every 5 minutes", CronExpressions.describe("*/5 * * * *"));
    }

    @Test
    public void describeRejectsInvalidExpressions() {
        assertThrows(IllegalArgumentException.class, () -> CronExpressions.describe("not a cron"));
    }

    // timezones

    @Test
    public void hostTimezoneAppliesByDefault() {
        assertEquals(ZoneId.systemDefault(), CronExpressions.resolveTimeZone(null));
        assertEquals(ZoneId.systemDefault(), CronExpressions.resolveTimeZone(""));
        assertEquals(ZoneId.systemDefault(), CronExpressions.resolveTimeZone("  "));
    }

    @Test
    public void resolvesIanaTimezones() {
        assertEquals(MELBOURNE, CronExpressions.resolveTimeZone("Australia/Melbourne"));
        assertEquals(LONDON, CronExpressions.resolveTimeZone(" Europe/London "));
    }

    @Test
    public void rejectsUnknownTimezones() {
        assertThrows(IllegalArgumentException.class, () -> CronExpressions.resolveTimeZone("Mars/OlympusMons"));
    }

    // Sunday semantics: 0, 7 and 'SUN' are the same day

    @ParameterizedTest
    @ValueSource(strings = { "0 22 * * 0", "0 22 * * 7", "0 22 * * SUN" })
    public void sundayAcceptedAsZeroSevenOrName(String expression) {
        // from a Saturday midday
        ZonedDateTime saturday = ZonedDateTime.of(2026, 7, 18, 12, 0, 0, 0, MELBOURNE);

        assertEquals(ZonedDateTime.of(2026, 7, 19, 22, 0, 0, 0, MELBOURNE), next(expression, saturday));
        assertEquals(ZonedDateTime.of(2026, 7, 12, 22, 0, 0, 0, MELBOURNE), previous(expression, saturday));
    }

    // named months and weekdays

    @Test
    public void namedMonths() {
        ZonedDateTime midJuly = ZonedDateTime.of(2026, 7, 16, 12, 0, 0, 0, MELBOURNE);
        assertEquals(ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, MELBOURNE), next("0 0 1 JAN *", midJuly));
    }

    @Test
    public void namedWeekdays() {
        // 2026-07-16 is a Thursday
        ZonedDateTime thursday = ZonedDateTime.of(2026, 7, 16, 12, 0, 0, 0, MELBOURNE);
        assertEquals(ZonedDateTime.of(2026, 7, 17, 9, 0, 0, 0, MELBOURNE), next("0 9 * * MON-FRI", thursday));
        assertEquals(ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0, MELBOURNE), next("0 9 * * MON", thursday));
    }

    // previous / next calculation

    @Test
    public void nextIsStrictlyAfterAndPreviousStrictlyBefore() {
        // from exactly an occurrence (Friday 09:00) neither direction returns that occurrence
        ZonedDateTime friday9am = ZonedDateTime.of(2026, 7, 10, 9, 0, 0, 0, MELBOURNE);

        assertEquals(ZonedDateTime.of(2026, 7, 13, 9, 0, 0, 0, MELBOURNE), next("0 9 * * MON-FRI", friday9am));
        assertEquals(ZonedDateTime.of(2026, 7, 9, 9, 0, 0, 0, MELBOURNE), previous("0 9 * * MON-FRI", friday9am));
    }

    @Test
    public void dayOfMonthAndDayOfWeekActAsAUnion() {
        // standard UNIX CRON: when both are restricted, either match fires
        // (2026-07-17 is a Friday, not the 13th)
        ZonedDateTime thursday = ZonedDateTime.of(2026, 7, 16, 12, 0, 0, 0, MELBOURNE);
        assertEquals(ZonedDateTime.of(2026, 7, 17, 0, 0, 0, 0, MELBOURNE), next("0 0 13 * FRI", thursday));
    }

    @Test
    public void impossibleDateNeverFires() {
        // February 30 is syntactically valid but has no occurrence
        ZonedDateTime from = ZonedDateTime.of(2026, 7, 16, 12, 0, 0, 0, MELBOURNE);
        assertNull(next("0 0 30 2 *", from));
        assertNull(previous("0 0 30 2 *", from));
    }

    // daylight-saving transitions

    @Test
    public void dstGapSkipsNonexistentOccurrence() {
        // Melbourne spring-forward 2026-10-04: 02:00 jumps to 03:00, so 02:30 never happens that day
        ZonedDateTime beforeGap = ZonedDateTime.of(2026, 10, 3, 12, 0, 0, 0, MELBOURNE);

        ZonedDateTime next = next("30 2 * * *", beforeGap);
        assertEquals(ZonedDateTime.of(2026, 10, 5, 2, 30, 0, 0, MELBOURNE), next);
        assertEquals("+11:00", next.getOffset().getId());
    }

    @Test
    public void dstGapSkipsNonexistentOccurrenceNorthernHemisphere() {
        // London spring-forward 2026-03-29: 01:00 jumps to 02:00, so 01:30 never happens that day
        ZonedDateTime beforeGap = ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, LONDON);

        ZonedDateTime next = next("30 1 * * *", beforeGap);
        assertEquals(ZonedDateTime.of(2026, 3, 30, 1, 30, 0, 0, LONDON), next);
        assertEquals("+01:00", next.getOffset().getId());
    }

    @Test
    public void dstOverlapFiresOnceAtTheFirstPass() {
        // Melbourne fall-back 2026-04-05: 03:00 rewinds to 02:00, so wall-clock 02:30 happens twice;
        // the occurrence applies once, at the first (daylight-saving) pass
        ZonedDateTime beforeOverlap = ZonedDateTime.of(2026, 4, 4, 12, 0, 0, 0, MELBOURNE);

        ZonedDateTime first = next("30 2 * * *", beforeOverlap);
        assertEquals(ZonedDateTime.of(2026, 4, 5, 2, 30, 0, 0, MELBOURNE).withEarlierOffsetAtOverlap(), first);
        assertEquals("+11:00", first.getOffset().getId());

        // ... and the second wall-clock pass (+10:00) is not revisited
        ZonedDateTime following = next("30 2 * * *", first);
        assertEquals(ZonedDateTime.of(2026, 4, 6, 2, 30, 0, 0, MELBOURNE), following);
        assertEquals("+10:00", following.getOffset().getId());
    }

    // convenience (recipe-facing) calculations

    @Test
    public void nextAndPreviousExecutionUseTheGivenTimezone() {
        DateTime next = CronExpressions.nextExecution("* * * * *", "Australia/Melbourne");
        assertNotNull(next);
        assertTrue(next.isAfterNow());

        DateTime previous = CronExpressions.previousExecution("* * * * *", "Australia/Melbourne");
        assertNotNull(previous);
        assertTrue(previous.isBeforeNow());
    }

    @Test
    public void nextExecutionRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> CronExpressions.nextExecution("bad", null));
        assertThrows(IllegalArgumentException.class, () -> CronExpressions.nextExecution("* * * * *", "Not/AZone"));
    }

    // summaries (UI-facing)

    @Test
    public void summarizeValidExpression() {
        CronInfo info = CronExpressions.summarize("0 9 * * 1-5", "Australia/Melbourne");

        assertEquals("0 9 * * 1-5", info.expression);
        assertTrue(info.valid);
        assertNull(info.error);
        assertEquals("at 09:00 every day between Monday and Friday", info.description);
        assertEquals("Australia/Melbourne", info.timeZone);
        assertNotNull(info.previous);
        assertNotNull(info.next);
        assertTrue(info.next.isAfter(info.previous));

        assertNotNull(info.upcoming);
        assertEquals(CronInfo.UPCOMING_COUNT, info.upcoming.length);
        assertEquals(info.next, info.upcoming[0]);
        for (int i = 1; i < info.upcoming.length; i++)
            assertTrue(info.upcoming[i].isAfter(info.upcoming[i - 1]));
    }

    @Test
    public void summarizeInvalidExpression() {
        CronInfo info = CronExpressions.summarize("61 * * * *", null);

        assertFalse(info.valid);
        assertNotNull(info.error);
        assertNull(info.description);
        assertNull(info.next);
        assertNull(info.upcoming);
    }

    @Test
    public void summarizeUnknownTimezone() {
        CronInfo info = CronExpressions.summarize("* * * * *", "Nowhere/Special");

        assertFalse(info.valid);
        assertTrue(info.error.contains("Nowhere/Special"));
    }

    @Test
    public void summarizeDefaultsToHostTimezone() {
        CronInfo info = CronExpressions.summarize("* * * * *", null);

        assertTrue(info.valid);
        assertEquals(ZoneId.systemDefault().getId(), info.timeZone);
    }

    @Test
    public void summarizeNeverFiringExpression() {
        CronInfo info = CronExpressions.summarize("0 0 30 2 *", null);

        assertTrue(info.valid);
        assertNull(info.next);
        assertNull(info.upcoming);
    }

    // java.time to JODATIME conversion

    @Test
    public void toDateTimePreservesInstantAndZone() {
        ZonedDateTime source = ZonedDateTime.of(2026, 7, 16, 9, 30, 0, 0, MELBOURNE);
        DateTime converted = CronExpressions.toDateTime(source);

        assertEquals(source.toInstant().toEpochMilli(), converted.getMillis());
        assertEquals("Australia/Melbourne", converted.getZone().getID());
        assertNull(CronExpressions.toDateTime(null));
    }

    @Test
    public void parseTrimsWhitespace() {
        Cron cron = CronExpressions.parse("  0 9 * * 1-5  ");
        assertNotNull(cron);
    }

}
