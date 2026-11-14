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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.database

/**
 * Domain record representing a historical wheel circumference calibration point (REQ-SEN-030.3, TST-SEN-030.3, ATT-7753).
 */
data class WheelCircumferenceRecord(
    val id: Long = 0,
    val equipmentId: Long,
    val timestampMs: Long,
    val wheelPosition: WheelPosition,
    val circumferenceMm: Int,
    val source: String = SOURCE_GNSS_AUTO
) {
    companion object {
        const val SOURCE_GNSS_AUTO = "GNSS_AUTO"
        const val SOURCE_MANUAL_RESET = "MANUAL_RESET"
    }
}
