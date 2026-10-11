package com.atrainingtracker.banalservice.sensor

import com.atrainingtracker.banalservice.sensor.formater.IntegerFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract unit tests for [SensorType] formatters (TST-UI-314.3, REQ-UI-354.3, ATT-3229).
 */
class SensorTypeFormatterTest {

    @Test
    fun powerSensor_usesIntegerFormatter_andRoundsFilteredWattage() {
        val formatter = SensorType.POWER.myFormatter
        assertTrue("SensorType.POWER formatter should be IntegerFormatter", formatter is IntegerFormatter)
        assertEquals("250", formatter.format(250.37))
        assertEquals("251", formatter.format(250.7))
        assertEquals("0", formatter.format(0.0))
        assertEquals("--", formatter.format(null))
    }

    @Test
    fun cadenceSensor_usesIntegerFormatter_andRounds() {
        val formatter = SensorType.CADENCE.myFormatter
        assertTrue("SensorType.CADENCE formatter should be IntegerFormatter", formatter is IntegerFormatter)
        assertEquals("90", formatter.format(89.8))
        assertEquals("89", formatter.format(89.2))
        assertEquals("--", formatter.format(null))
    }

    @Test
    fun heartRateSensor_usesIntegerFormatter_andRounds() {
        val formatter = SensorType.HR.myFormatter
        assertTrue("SensorType.HR formatter should be IntegerFormatter", formatter is IntegerFormatter)
        assertEquals("155", formatter.format(154.6))
        assertEquals("154", formatter.format(154.2))
        assertEquals("--", formatter.format(null))
    }

    @Test
    fun slopeSensor_usesIntegerFormatter_andRounds() {
        val formatter = SensorType.SLOPE.myFormatter
        assertTrue("SensorType.SLOPE formatter should be IntegerFormatter", formatter is IntegerFormatter)
        assertEquals("6", formatter.format(5.7))
        assertEquals("-6", formatter.format(-5.7))
    }
}
