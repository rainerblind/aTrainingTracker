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
 * Physical athlete posture during morning resting HRV acquisition (`REQ-PER-030.4`, `REQ-PER-031.3`).
 *
 * Orthostatic vagal withdrawal causes substantial physiological divergence between postures.
 * Morning HRV baselines, rolling averages, and Smallest Worthwhile Change (SWC) corridors
 * are strictly isolated per posture. Nocturnal passive wearable monitoring is tracked under [OVERNIGHT_WEARABLE].
 */
enum class HrvPosture {
    SUPINE,
    SEATED,
    STANDING,
    OVERNIGHT_WEARABLE;

    companion object {
        @JvmField
        val OVERNIGHT_HC = OVERNIGHT_WEARABLE

        @JvmStatic
        fun fromNameOrNull(name: String?): HrvPosture? {
            if (name.equals("OVERNIGHT_HC", ignoreCase = true)) return OVERNIGHT_WEARABLE
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }
}
