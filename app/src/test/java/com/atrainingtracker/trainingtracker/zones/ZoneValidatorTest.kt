/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.zones

import com.atrainingtracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [ZoneValidator], verifying boundary constraints, strict integer
 * monotonicity (Zn >= Zn-1 + 1), and Karvonen safety checks.
 *
 * Covers [TST-UI-336.3].
 */
class ZoneValidatorTest {

    @Test
    fun validatePowerZoneStack_validMonotonicStack_succeeds() {
        val validStack = listOf(138, 188, 225, 263, 300, 375)
        // Should not throw
        ZoneValidator.validatePowerZoneStack(validStack)
    }

    @Test
    fun validatePowerZoneStack_wrongSize_throws() {
        try {
            ZoneValidator.validatePowerZoneStack(listOf(100, 200, 300))
            fail("Expected IllegalArgumentException for stack size < 6")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("exactly 6") == true)
        }
    }

    @Test
    fun validatePowerZoneStack_equalAdjacentValues_throws() {
        // Delta must be at least 1 W: equal values are invalid
        val equalStack = listOf(138, 188, 225, 225, 300, 375)
        try {
            ZoneValidator.validatePowerZoneStack(equalStack)
            fail("Expected IllegalArgumentException for equal adjacent cutoffs")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Non-monotonic") == true)
        }
    }

    @Test
    fun validatePowerZoneStack_invertedValues_throws() {
        val invertedStack = listOf(138, 188, 263, 225, 300, 375)
        try {
            ZoneValidator.validatePowerZoneStack(invertedStack)
            fail("Expected IllegalArgumentException for inverted cutoffs")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Non-monotonic") == true)
        }
    }

    @Test
    fun validatePowerZoneStack_exceedsPhysicalCeiling2500W_throws() {
        val ceilingStack = listOf(138, 188, 225, 263, 300, 2501)
        try {
            ZoneValidator.validatePowerZoneStack(ceilingStack)
            fail("Expected IllegalArgumentException for cutoff > 2500W")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("exceeds physical maximum") == true)
        }
    }

    @Test
    fun validateHeartRateZoneStack_validMonotonicStack_succeeds() {
        val validHrStack = listOf(112, 131, 150, 169)
        ZoneValidator.validateHeartRateZoneStack(validHrStack)
    }

    @Test
    fun validateHeartRateZoneStack_equalAdjacent_throws() {
        val equalHrStack = listOf(112, 131, 131, 169)
        try {
            ZoneValidator.validateHeartRateZoneStack(equalHrStack)
            fail("Expected IllegalArgumentException for equal adjacent HR cutoffs")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Non-monotonic") == true)
        }
    }

    @Test
    fun validateHeartRateZoneStack_outOfBounds_throws() {
        try {
            ZoneValidator.validateHeartRateZoneStack(listOf(25, 100, 140, 180))
            fail("Expected IllegalArgumentException for HR cutoff < 30")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains(">= 30") == true)
        }

        try {
            ZoneValidator.validateHeartRateZoneStack(listOf(100, 140, 180, 245))
            fail("Expected IllegalArgumentException for HR cutoff > 240")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("exceeds maximum") == true)
        }
    }

    @Test
    fun validateFtp_validRange_returnsValid() {
        assertEquals(ValidationResult.Valid, ZoneValidator.validateFtp(200))
        assertEquals(ValidationResult.Valid, ZoneValidator.validateFtp(50))
        assertEquals(ValidationResult.Valid, ZoneValidator.validateFtp(600))
    }

    @Test
    fun validateFtp_outOfRange_returnsInvalid() {
        assertEquals(
            ValidationResult.Invalid(R.string.zone_calc_error_ftp_range),
            ZoneValidator.validateFtp(49)
        )
        assertEquals(
            ValidationResult.Invalid(R.string.zone_calc_error_ftp_range),
            ZoneValidator.validateFtp(601)
        )
    }

    @Test
    fun validateHrBaselines_validKarvonen_returnsValid() {
        val res = ZoneValidator.validateHrBaselines(hrMax = 190, hrRest = 50)
        assertEquals(ValidationResult.Valid, res)
    }

    @Test
    fun validateHrBaselines_hrMaxLessOrEqualHrRest_returnsSpecificError() {
        val res = ZoneValidator.validateHrBaselines(hrMax = 80, hrRest = 80)
        assertEquals(ValidationResult.Invalid(R.string.zone_calc_error_hr_max_greater_rest), res)
    }

    @Test
    fun validateHrBaselines_hrReserveTooLow_returnsSpecificError() {
        val res = ZoneValidator.validateHrBaselines(hrMax = 95, hrRest = 80)
        assertEquals(ValidationResult.Invalid(R.string.zone_calc_error_karvonen_delta), res)
    }
}
