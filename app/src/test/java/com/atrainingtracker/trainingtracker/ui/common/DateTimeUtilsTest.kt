package com.atrainingtracker.trainingtracker.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Unit tests verifying locale-aware ISO-8601 date formatting in [DateTimeUtils].
 * Verifies REQ-UI-475.3 and TST-UI-475.1.
 */
class DateTimeUtilsTest {

    @Test
    fun formatIsoDate_germanLocale_formatsCorrectly() {
        val result = DateTimeUtils.formatIsoDate("2018-08-12", Locale.GERMANY)
        assertEquals("12.08.2018", result)
    }

    @Test
    fun formatIsoDate_usLocale_formatsCorrectly() {
        val result = DateTimeUtils.formatIsoDate("2018-08-12", Locale.US)
        assertEquals("Aug 12, 2018", result)
    }

    @Test
    fun formatIsoDate_japaneseLocale_formatsCorrectly() {
        val result = DateTimeUtils.formatIsoDate("2018-08-12", Locale.JAPAN)
        assertEquals("2018/08/12", result)
    }

    @Test
    fun formatIsoDate_isoDateTimeWithTimestamp_parsesDatePartSuccessfully() {
        val result = DateTimeUtils.formatIsoDate("2024-05-01T14:30:00", Locale.GERMANY)
        assertEquals("01.05.2024", result)
    }

    @Test
    fun formatIsoDate_isoDateTimeWithSpace_parsesDatePartSuccessfully() {
        val result = DateTimeUtils.formatIsoDate("2024-05-01 14:30:00", Locale.GERMANY)
        assertEquals("01.05.2024", result)
    }

    @Test
    fun formatIsoDate_leapYearDate_formatsCorrectly() {
        val result = DateTimeUtils.formatIsoDate("2024-02-29", Locale.GERMANY)
        assertEquals("29.02.2024", result)
    }

    @Test
    fun formatIsoDate_nullAndBlankStrings_returnEmptyString() {
        assertEquals("", DateTimeUtils.formatIsoDate(null))
        assertEquals("", DateTimeUtils.formatIsoDate(""))
        assertEquals("", DateTimeUtils.formatIsoDate("   "))
    }

    @Test
    fun formatIsoDate_invalidMalformedDate_returnsOriginalFallbackWithoutCrashing() {
        assertEquals("invalid-date-string", DateTimeUtils.formatIsoDate("invalid-date-string"))
        assertEquals("2024-99-99", DateTimeUtils.formatIsoDate("2024-99-99"))
    }

    @Test
    fun formatPeriodDateRange_germanLocale_formatsDayFirst_TST_PER_036_1() {
        // Monday Nov 2, 2026 00:00:00 UTC = 1793577600
        val startS = 1793577600L
        val endS = startS + 7 * 86400 - 1
        val result = DateTimeUtils.formatPeriodDateRange(startS, endS, Locale.GERMANY, java.time.ZoneOffset.UTC)
        assertEquals("2. Nov. - 8. Nov.", result)
    }

    @Test
    fun formatPeriodDateRange_usLocale_formatsMonthFirst_TST_PER_036_1() {
        // Monday Nov 2, 2026 00:00:00 UTC = 1793577600
        val startS = 1793577600L
        val endS = startS + 7 * 86400 - 1
        val result = DateTimeUtils.formatPeriodDateRange(startS, endS, Locale.US, java.time.ZoneOffset.UTC)
        assertEquals("Nov 2 - Nov 8", result)
    }

    @Test
    fun formatPeriodDateRange_invalidOrZeroTimestamps_returnsEmpty() {
        assertEquals("", DateTimeUtils.formatPeriodDateRange(0L, 0L))
        assertEquals("", DateTimeUtils.formatPeriodDateRange(-1L, 1000L))
        assertEquals("", DateTimeUtils.formatPeriodDateRange(1000L, -1L))
    }

    @Test
    fun resolveDateRangePattern_supportsMultipleLocales() {
        assertEquals("d. MMM", DateTimeUtils.resolveDateRangePattern(Locale.GERMANY))
        assertEquals("M月d日", DateTimeUtils.resolveDateRangePattern(Locale.JAPAN))
        assertEquals("d MMM", DateTimeUtils.resolveDateRangePattern(Locale.FRANCE))
        assertEquals("d MMM", DateTimeUtils.resolveDateRangePattern(Locale.forLanguageTag("es-ES")))
        assertEquals("MMM d", DateTimeUtils.resolveDateRangePattern(Locale.US))
    }
}
