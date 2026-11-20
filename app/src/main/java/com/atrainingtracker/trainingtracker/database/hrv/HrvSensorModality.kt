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

package com.atrainingtracker.trainingtracker.database.hrv

/**
 * Sensor acquisition modality for morning HRV readiness measurements (`REQ-HRV-049.3`, `REQ-HRV-049.4`).
 *
 * Isolates optical PPG pulse-to-pulse interval (PPI) cohorts (e.g. Polar Verity Sense) from
 * ECG chest-strap cohorts so systematic PPG-vs-ECG offsets never contaminate 60-day SWC baselines.
 */
enum class HrvSensorModality {
    ECG,
    OPTICAL_PPI;

    companion object {
        @JvmStatic
        fun fromNameOrDefault(name: String?): HrvSensorModality {
            if (name.isNullOrBlank()) return ECG
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ECG
        }
    }
}
