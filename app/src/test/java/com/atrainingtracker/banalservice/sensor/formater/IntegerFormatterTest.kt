package com.atrainingtracker.banalservice.sensor.formater

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [IntegerFormatter] (TST-UI-314.1, REQ-UI-354.1, ATT-3229).
 */
class IntegerFormatterTest {

    private val formatter = IntegerFormatter()

    @Test
    fun format_null_returnsDashes() {
        assertEquals("--", formatter.format(null))
    }

    @Test
    fun format_nanOrInfinite_returnsDashes() {
        assertEquals("--", formatter.format(Double.NaN))
        assertEquals("--", formatter.format(Float.NaN))
        assertEquals("--", formatter.format(Double.POSITIVE_INFINITY))
        assertEquals("--", formatter.format(Double.NEGATIVE_INFINITY))
        assertEquals("--", formatter.format(Float.POSITIVE_INFINITY))
        assertEquals("--", formatter.format(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun format_integerNumbers_returnsExactString() {
        assertEquals("0", formatter.format(0))
        assertEquals("0", formatter.format(0.0))
        assertEquals("250", formatter.format(250))
        assertEquals("150", formatter.format(150L))
        assertEquals("85", formatter.format(85.toShort()))
    }

    @Test
    fun format_positiveFractions_roundsHalfUp() {
        assertEquals("245", formatter.format(245.0))
        assertEquals("245", formatter.format(245.2))
        assertEquals("245", formatter.format(245.49))
        assertEquals("246", formatter.format(245.5))
        assertEquals("246", formatter.format(245.8))
        assertEquals("250", formatter.format(250.37))
        assertEquals("251", formatter.format(250.7))
    }

    @Test
    fun format_cadenceFractions_roundsCorrectly() {
        assertEquals("89", formatter.format(89.1))
        assertEquals("89", formatter.format(89.49))
        assertEquals("90", formatter.format(89.5))
        assertEquals("90", formatter.format(89.8))
        assertEquals("90", formatter.format(89.99))
    }

    @Test
    fun format_heartRateFractions_roundsCorrectly() {
        assertEquals("154", formatter.format(154.2))
        assertEquals("154", formatter.format(154.49))
        assertEquals("155", formatter.format(154.5))
        assertEquals("155", formatter.format(154.6))
        assertEquals("155", formatter.format(154.9))
    }

    @Test
    fun format_negativeFractions_roundsCorrectly() {
        // Slope can be negative, e.g. -5.2% or -5.8%
        assertEquals("-5", formatter.format(-5.2))
        assertEquals("-6", formatter.format(-5.8))
    }
}
