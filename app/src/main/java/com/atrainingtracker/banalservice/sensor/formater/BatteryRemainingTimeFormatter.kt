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

package com.atrainingtracker.banalservice.sensor.formater

import android.content.Context
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import java.util.Locale

/**
 * Formats smartphone battery remaining duration into hours and minutes (e.g. "8:45 h").
 * Handles special status values:
 * - null or -2: initial stabilization or unknown drain ("--:--")
 * - -1: host device actively connected to charger ("Lädt" / "Charging")
 */
class BatteryRemainingTimeFormatter : MyFormatter<Number> {

    @Suppress("ReturnCount")
    override fun format(value: Number?): String {
        if (value == null) {
            return "--:--"
        }
        val seconds = value.toInt()
        if (seconds == CHARGING_STATUS_CODE) {
            try {
                val context = TrainingApplication.getAppContext()
                if (context != null) {
                    return context.getString(R.string.battery_charging)
                }
            } catch (ignored: Exception) {
            }
            return "Charging"
        }
        if (seconds <= STABILIZING_STATUS_CODE) {
            return "--:--"
        }
        val hours = seconds / SECONDS_PER_HOUR
        val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        return String.format(Locale.getDefault(), "%d:%02d h", hours, minutes)
    }

    companion object {
        const val CHARGING_STATUS_CODE: Int = -1
        const val STABILIZING_STATUS_CODE: Int = -2
        private const val SECONDS_PER_HOUR = 3600
        private const val SECONDS_PER_MINUTE = 60
    }
}
