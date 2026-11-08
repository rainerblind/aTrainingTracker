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
}
