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
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.sensor.formater

import android.content.Context
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import kotlin.math.roundToInt

/**
 * Formats integer-encoded [PhoneCarriageMode] telemetry codes into localized labels for
 * live UI presentation while keeping SQLite and CSV representations strictly numeric
 * (REQ-FIL-028.2, ATT-8890).
 */
class PhoneCarriageModeFormatter : MyFormatter<Number> {

    @Suppress("ReturnCount")
    override fun format(value: Number?): String {
        val context: Context? = try {
            TrainingApplication.getAppContext()
        } catch (_: Exception) {
            null
        }
        return formatWithContext(value, context)
    }

    @Suppress("ReturnCount")
    fun formatWithContext(value: Number?, context: Context?): String {
        if (value == null) return "--"
        val raw = value.toDouble()
        if (!raw.isFinite()) return "--"

        val mode = PhoneCarriageMode.fromCode(raw.roundToInt())
        if (context != null) {
            try {
                return context.getString(mode.labelResId)
            } catch (_: Exception) {
                // Fall back to deterministic English label when resources are unavailable
            }
        }
        return mode.defaultEnglishLabel
    }
}
