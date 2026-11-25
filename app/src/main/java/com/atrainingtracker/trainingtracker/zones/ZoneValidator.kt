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
import com.atrainingtracker.trainingtracker.database.AthleteMetricType

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

    /**
     * Validates cycling power physiological thresholds against canonical [AthleteMetricType] bounds
     * and the cross-field invariant (`LT1_W < FTP`) (`REQ-SET-136.2`).
     */
    fun validatePowerThresholds(
        ftp: Int? = null,
        cp: Int? = null,
        wPrimeKj: Double? = null,
        lt1Power: Int? = null
    ): ValidationResult {
        if (ftp != null && !AthleteMetricType.FTP_WATTS.isValidMetricValue(ftp.toDouble())) {
            return ValidationResult.Invalid(R.string.zone_calc_error_ftp_range)
        }
        if (cp != null && !AthleteMetricType.CP_WATTS.isValidMetricValue(cp.toDouble())) {
            return ValidationResult.Invalid(R.string.zone_thresholds_error_cp_range)
        }
        if (wPrimeKj != null && !AthleteMetricType.W_PRIME_KJ.isValidMetricValue(wPrimeKj)) {
            return ValidationResult.Invalid(R.string.zone_thresholds_error_w_prime_range)
        }
        if (lt1Power != null && !AthleteMetricType.LT1_PWR_BIKE_WATTS.isValidMetricValue(lt1Power.toDouble())) {
            return ValidationResult.Invalid(R.string.zone_thresholds_error_lt1_pwr_range)
        }
        if (lt1Power != null && ftp != null && lt1Power >= ftp) {
            return ValidationResult.Invalid(R.string.zone_thresholds_error_lt1_lt_ftp)
        }
        return ValidationResult.Valid
    }

    /**
     * Validates heart rate physiological thresholds against canonical [AthleteMetricType] bounds
     * and strict ascending ordering (`HRrest < LT1 < LTHR < HRmax`) across all provided pairs (`REQ-SET-136.2`).
     */
    fun validateHeartRateThresholds(
        hrMax: Int? = null,
        hrRest: Int? = null,
        lthr: Int? = null,
        lt1Hr: Int? = null
    ): ValidationResult {
        if (hrMax != null && !AthleteMetricType.HR_MAX_RUN_BPM.isValidMetricValue(hrMax.toDouble())) {
            return ValidationResult.Invalid(R.string.zone_calc_error_hr_max_range)
        }
        if (hrRest != null && !AthleteMetricType.HR_REST_BPM.isValidMetricValue(hrRest.toDouble())) {
            return ValidationResult.Invalid(R.string.zone_calc_error_hr_rest_range)
        }
        if (lthr != null && !AthleteMetricType.LTHR_RUN_BPM.isValidMetricValue(lthr.toDouble())) {
            return ValidationResult.Invalid(R.string.zone_calc_error_lthr_range)
        }
        if (lt1Hr != null && !AthleteMetricType.LT1_HR_RUN_BPM.isValidMetricValue(lt1Hr.toDouble())) {
            return ValidationResult.Invalid(R.string.zone_calc_error_lt1_hr_range)
        }
        val ordered = listOfNotNull(hrRest, lt1Hr, lthr, hrMax)
        for (i in 1 until ordered.size) {
            if (ordered[i] <= ordered[i - 1]) {
                return ValidationResult.Invalid(R.string.zone_thresholds_error_hr_order)
            }
        }
        return ValidationResult.Valid
    }
}
