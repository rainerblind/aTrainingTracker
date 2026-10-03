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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import com.atrainingtracker.banalservice.BANALService
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Formats pace ceiling values for Expert Settings in Metric (min/km) and Imperial (min/mi).
 * (REQ-UI-243 / ATT-2014)
 */
object TuningPaceCeilingFormatter {

    /**
     * Formats a pace ceiling given in min/km into human-readable text.
     * In Metric: e.g. "3:00 min/km"
     * In Imperial: converts to min/mi, e.g. "4:50 min/mi"
     */
    fun formatPaceCeiling(paceMinKm: Float, isMetric: Boolean): String {
        return if (isMetric) {
            val totalSec = (paceMinKm * 60f).roundToInt()
            val minutes = totalSec / 60
            val seconds = totalSec % 60
            String.format(Locale.getDefault(), "%d:%02d min/km", minutes, seconds)
        } else {
            val totalSecPerMi = (paceMinKm * 60.0 * (BANALService.METER_PER_MILE / 1000.0)).roundToInt()
            val minutes = totalSecPerMi / 60
            val seconds = totalSecPerMi % 60
            String.format(Locale.getDefault(), "%d:%02d min/mi", minutes, seconds)
        }
    }
}
