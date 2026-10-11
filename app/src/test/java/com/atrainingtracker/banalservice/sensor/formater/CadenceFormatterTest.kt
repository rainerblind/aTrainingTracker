package com.atrainingtracker.banalservice.sensor.formater

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [CadenceFormatter] (TST-UI-314.2, REQ-UI-354.2, ATT-3229).
 */
class CadenceFormatterTest {

    private val formatter = CadenceFormatter()

    @Test
    fun format_null_returnsDashes() {
        assertEquals("--", formatter.format(null))
    }

    @Test
    fun format_nanOrInfinite_returnsDashes() {
        assertEquals("--", formatter.format(Double.NaN))
        assertEquals("--", formatter.format(Double.POSITIVE_INFINITY))
    }

    @Test
    fun format_integerNumbers_returnsExactString() {
        assertEquals("0", formatter.format(0))
        assertEquals("90", formatter.format(90))
        assertEquals("110", formatter.format(110L))
    }

    @Test
    fun format_cadenceFractions_roundsHalfUp() {
        assertEquals("89", formatter.format(89.2))
        assertEquals("90", formatter.format(89.5))
        assertEquals("90", formatter.format(89.8))
        assertEquals("91", formatter.format(90.6))
    }
}
