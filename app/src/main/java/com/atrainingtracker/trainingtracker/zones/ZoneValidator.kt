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

/**
 * Result of physiological baseline and zone boundary validation.
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errorResId: Int) : ValidationResult()
}

/**
 * Centralized blocking validation guard for physiological training zones and baselines.
 *
 * Enforces Layer 2 domain-level constraints conforming to REQ-UI-373.3 & REQ-UI-373.4:
 * - Strict integer monotonicity: Zn >= Zn-1 + 1 (adjacent zones cannot share identical cutoffs).
 * - Physical bounds checking: Cycling Power in (0, 2500] W; Heart Rate in [30, 240] bpm.
 * - Primary Karvonen invariant checks: HRmax > HRrest and HRmax - HRrest >= 20 bpm.
 */
object ZoneValidator {

    const val MAX_POWER_CUTOFF = ZoneCalculationMath.ZONE_7_POWER_CEILING
    const val MIN_HR_CUTOFF = 30
    const val MAX_HR_CUTOFF = 240

    /**
     * Validates that a 6-cutoff cycling power zone stack is strictly ascending and within [1, 2500] W.
     *
     * @param cutoffs List of 6 cutoffs [Z1..Z6].
     * @throws IllegalArgumentException if size is not 6, any cutoff is out of bounds,
     * or any cutoff is <= previous cutoff (delta < 1).
     */
    fun validatePowerZoneStack(cutoffs: List<Int>) {
        require(cutoffs.size == 6) {
            "Power zone stack must contain exactly 6 cutoffs, found ${cutoffs.size}"
        }
        for (i in cutoffs.indices) {
            val v = cutoffs[i]
            require(v > 0) { "Power zone cutoff Z${i + 1} ($v W) must be positive" }
            require(v <= MAX_POWER_CUTOFF) {
                "Power zone cutoff Z${i + 1} ($v W) exceeds physical maximum ($MAX_POWER_CUTOFF W)"
            }
            if (i > 0) {
                val prev = cutoffs[i - 1]
                require(v >= prev + 1) {
                    "Non-monotonic power boundaries: Z${i + 1} ($v W) must be at least 1 W greater than Z$i ($prev W)"
                }
            }
        }
    }

    /**
     * Validates that a 4-cutoff heart rate zone stack is strictly ascending and within [30, 240] bpm.
     *
     * @param cutoffs List of 4 cutoffs [Z1..Z4].
     * @throws IllegalArgumentException if size is not 4, any cutoff is out of bounds,
     * or any cutoff is <= previous cutoff.
     */
    fun validateHeartRateZoneStack(cutoffs: List<Int>) {
        require(cutoffs.size == 4) {
            "Heart rate zone stack must contain exactly 4 cutoffs, found ${cutoffs.size}"
        }
        for (i in cutoffs.indices) {
            val v = cutoffs[i]
            require(v >= MIN_HR_CUTOFF) { "Heart rate cutoff Z${i + 1} ($v bpm) must be >= $MIN_HR_CUTOFF bpm" }
            require(v <= MAX_HR_CUTOFF) { "Heart rate cutoff Z${i + 1} ($v bpm) exceeds maximum ($MAX_HR_CUTOFF bpm)" }
            if (i > 0) {
                val prev = cutoffs[i - 1]
                require(v >= prev + 1) {
                    "Non-monotonic heart rate boundaries: Z${i + 1} ($v bpm) must be at least 1 bpm greater than Z$i ($prev bpm)"
                }
            }
        }
    }

    /**
     * Validates athlete Functional Threshold Power (FTP) input.
     */
    fun validateFtp(ftp: Int): ValidationResult {
        return if (ftp in ZoneCalculationMath.MIN_FTP..ZoneCalculationMath.MAX_FTP) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(R.string.zone_calc_error_ftp_range)
        }
    }

    /**
     * Validates athlete heart rate baselines (HRmax and optional HRrest).
     */
    fun validateHrBaselines(hrMax: Int, hrRest: Int?): ValidationResult {
        if (hrMax !in ZoneCalculationMath.MIN_HR_MAX..ZoneCalculationMath.MAX_HR_MAX) {
            return ValidationResult.Invalid(R.string.zone_calc_error_hr_max_range)
        }
        if (hrRest != null) {
            if (hrRest !in ZoneCalculationMath.MIN_HR_REST..ZoneCalculationMath.MAX_HR_REST) {
                return ValidationResult.Invalid(R.string.zone_calc_error_hr_rest_range)
            }
            if (hrMax <= hrRest) {
                return ValidationResult.Invalid(R.string.zone_calc_error_hr_max_greater_rest)
            }
            if (hrMax - hrRest < ZoneCalculationMath.MIN_KARVONEN_RESERVE) {
                return ValidationResult.Invalid(R.string.zone_calc_error_karvonen_delta)
            }
        }
        return ValidationResult.Valid
    }
}
