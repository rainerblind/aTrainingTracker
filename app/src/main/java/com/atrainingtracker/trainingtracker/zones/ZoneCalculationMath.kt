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

import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds

/**
 * Pure mathematical engine for calculating physiological training zone boundaries
 * from Functional Threshold Power (FTP) and Heart Rate baselines.
 *
 * Conforms to REQ-UI-373 under ASPICE SWE.2:
 * - Coggan 7-Zone Cycling Power Framework (Z1..Z6 cutoffs, Z7 capped at 2500W).
 * - Heart Rate Model A (% of HRmax) and Model B (Karvonen Heart Rate Reserve).
 * - Deterministic half-up rounding with strict monotonic ascending boundary rationalization (Zn >= Zn-1 + 1).
 */
object ZoneCalculationMath {

    const val MIN_FTP = 50
    const val MAX_FTP = 600

    const val MIN_HR_MAX = 80
    const val MAX_HR_MAX = 240

    const val MIN_HR_REST = 30
    const val MAX_HR_REST = 120

    const val MIN_KARVONEN_RESERVE = 20
    const val ZONE_7_POWER_CEILING = 2500

    /**
     * Calculates 7 Coggan cycling power zone boundaries from Functional Threshold Power (FTP, in Watts).
     *
     * Percentages:
     * - Zone 1 (Active Recovery): <= 55% FTP
     * - Zone 2 (Endurance): <= 75% FTP
     * - Zone 3 (Tempo): <= 90% FTP
     * - Zone 4 (Lactate Threshold): <= 105% FTP
     * - Zone 5 (VO2 Max): <= 120% FTP
     * - Zone 6 (Anaerobic Capacity): <= 150% FTP
     * - Zone 7 (Neuromuscular Power): > Z6, capped at physical ceiling 2500W.
     *
     * @param ftp Functional Threshold Power in Watts (must be in [50, 600]).
     * @return [PowerZoneThresholds] containing strictly ascending integer cutoffs Z1..Z6.
     * @throws IllegalArgumentException if FTP is out of bounds or calculation violates invariants.
     */
    fun calculateCogganPowerZones(ftp: Int): PowerZoneThresholds {
        require(ftp in MIN_FTP..MAX_FTP) { "FTP ($ftp W) must be in [$MIN_FTP, $MAX_FTP]" }

        var z1 = Math.round(0.55 * ftp).toInt()
        var z2 = Math.round(0.75 * ftp).toInt()
        var z3 = Math.round(0.90 * ftp).toInt()
        var z4 = Math.round(1.05 * ftp).toInt()
        var z5 = Math.round(1.20 * ftp).toInt()
        var z6 = Math.round(1.50 * ftp).toInt()

        // Monotonic ascending boundary rationalization (Zn >= Zn-1 + 1)
        if (z2 <= z1) z2 = z1 + 1
        if (z3 <= z2) z3 = z2 + 1
        if (z4 <= z3) z4 = z3 + 1
        if (z5 <= z4) z5 = z4 + 1
        if (z6 <= z5) z6 = z5 + 1

        require(z6 < ZONE_7_POWER_CEILING) {
            "Zone 6 cutoff ($z6 W) must be strictly less than Zone 7 physical ceiling ($ZONE_7_POWER_CEILING W)"
        }

        return PowerZoneThresholds(
            z1Max = z1,
            z2Max = z2,
            z3Max = z3,
            z4Max = z4,
            z5Max = z5,
            z6Max = z6
        )
    }

    /**
     * Calculates 5 heart rate zone cutoffs using standard percentage of maximum heart rate (% of HRmax).
     *
     * Cutoffs:
     * - Zone 1: <= 59% HRmax
     * - Zone 2: <= 69% HRmax
     * - Zone 3: <= 79% HRmax
     * - Zone 4: <= 89% HRmax
     * - Zone 5: > Z4, up to HRmax.
     *
     * @param hrMax Maximum Heart Rate in bpm (must be in [80, 240]).
     * @return [HeartRateZoneThresholds] containing strictly ascending cutoffs Z1..Z4.
     */
    fun calculateHeartRateZonesMax(hrMax: Int): HeartRateZoneThresholds {
        require(hrMax in MIN_HR_MAX..MAX_HR_MAX) { "HRmax ($hrMax bpm) must be in [$MIN_HR_MAX, $MAX_HR_MAX]" }

        var z1 = Math.round(0.59 * hrMax).toInt()
        var z2 = Math.round(0.69 * hrMax).toInt()
        var z3 = Math.round(0.79 * hrMax).toInt()
        var z4 = Math.round(0.89 * hrMax).toInt()

        // Monotonic rationalization
        if (z2 <= z1) z2 = z1 + 1
        if (z3 <= z2) z3 = z2 + 1
        if (z4 <= z3) z4 = z3 + 1

        require(z4 < hrMax) { "Zone 4 cutoff ($z4 bpm) must be strictly less than HRmax ($hrMax bpm)" }

        return HeartRateZoneThresholds(
            z1Max = z1,
            z2Max = z2,
            z3Max = z3,
            z4Max = z4
        )
    }

    /**
     * Calculates 5 heart rate zone cutoffs using the Karvonen formula (Heart Rate Reserve).
     *
     * Formula:
     * Target HR = round(HRrest + intensity% * (HRmax - HRrest))
     *
     * Cutoffs:
     * - Zone 1: <= round(HRrest + 0.59 * HRR)
     * - Zone 2: <= round(HRrest + 0.69 * HRR)
     * - Zone 3: <= round(HRrest + 0.79 * HRR)
     * - Zone 4: <= round(HRrest + 0.89 * HRR)
     * - Zone 5: > Z4, up to HRmax.
     *
     * @param hrMax Maximum Heart Rate in bpm (must be in [80, 240]).
     * @param hrRest Resting Heart Rate in bpm (must be in [30, 120]).
     * @return [HeartRateZoneThresholds] containing strictly ascending cutoffs Z1..Z4.
     * @throws IllegalArgumentException if bounds are violated, HRmax <= HRrest, or HRR < 20 bpm.
     */
    fun calculateKarvonenHeartRateZones(hrMax: Int, hrRest: Int): HeartRateZoneThresholds {
        require(hrMax in MIN_HR_MAX..MAX_HR_MAX) { "HRmax ($hrMax bpm) must be in [$MIN_HR_MAX, $MAX_HR_MAX]" }
        require(hrRest in MIN_HR_REST..MAX_HR_REST) { "HRrest ($hrRest bpm) must be in [$MIN_HR_REST, $MAX_HR_REST]" }
        require(hrMax > hrRest) { "HRmax ($hrMax bpm) must be greater than HRrest ($hrRest bpm)" }

        val hrr = hrMax - hrRest
        require(hrr >= MIN_KARVONEN_RESERVE) {
            "Heart Rate Reserve ($hrr bpm) must be at least $MIN_KARVONEN_RESERVE bpm"
        }

        var z1 = Math.round(hrRest + 0.59 * hrr).toInt()
        var z2 = Math.round(hrRest + 0.69 * hrr).toInt()
        var z3 = Math.round(hrRest + 0.79 * hrr).toInt()
        var z4 = Math.round(hrRest + 0.89 * hrr).toInt()

        // Monotonic rationalization
        if (z2 <= z1) z2 = z1 + 1
        if (z3 <= z2) z3 = z2 + 1
        if (z4 <= z3) z4 = z3 + 1

        require(z4 < hrMax) { "Zone 4 cutoff ($z4 bpm) must be strictly less than HRmax ($hrMax bpm)" }

        return HeartRateZoneThresholds(
            z1Max = z1,
            z2Max = z2,
            z3Max = z3,
            z4Max = z4
        )
    }
}
