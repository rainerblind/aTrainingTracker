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

import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import kotlin.math.floor

class PaceFormatter : MyFormatter<Number> {
    @Suppress("ReturnCount")
    override fun format(value: Number?): String {
        if (value == null) {
            return "--"
        }

        var pace = value.toDouble()

        if (pace > BANALService.MAX_PACE) {
            return "~~"
        }

        when (TrainingApplication.getUnit()) {
            MyUnits.METRIC -> pace = pace * METERS_PER_KILOMETER / SECONDS_PER_MINUTE
            MyUnits.IMPERIAL -> pace = pace * BANALService.METER_PER_MILE / SECONDS_PER_MINUTE
            null -> {}
        }

        return formatPaceMinutes(pace)
    }

    /**
     * Formats a pace value already expressed in decimal minutes per user distance unit
     * (`min/km` or `min/mile`) without round-tripping through seconds-per-meter (`REQ-UI-2803.1`),
     * enforcing negative-value (`"--"`) and [BANALService.MAX_PACE] (`"~~"`) parity with [format].
     */
    @Suppress("ReturnCount")
    fun formatPaceMinutes(
        paceMinutes: Double?,
        unit: MyUnits? = TrainingApplication.getUnit()
    ): String {
        if (paceMinutes == null || !paceMinutes.isFinite() || paceMinutes < 0.0) {
            return "--"
        }
        if (paceMinutes > maxPaceMinutesForUnit(unit)) {
            return "~~"
        }
        val min = floor(paceMinutes).toInt()
        val sec = floor((paceMinutes - min) * SECONDS_PER_MINUTE).toInt()
        val secFormatted = if (sec <= TWO_DIGIT_THRESHOLD) "0$sec" else sec.toString()
        return "$min:$secFormatted"
    }

    /**
     * Formats a pace value already in decimal minutes per user distance unit with its canonical
     * unit suffix (`min/km` or `min/mile`) in a single pass (`REQ-UI-2803.1`).
     */
    fun formatPaceMinutesWithUnits(
        paceMinutes: Double?,
        unit: MyUnits = TrainingApplication.getUnit()
    ): String {
        val units = unitLabel(unit)
        return "${formatPaceMinutes(paceMinutes, unit)} $units"
    }

    @Suppress("FunctionNaming")
    fun format_with_units(value: Number?): String {
        val units = unitLabel(TrainingApplication.getUnit())
        return "${format(value)} $units"
    }

    private fun maxPaceMinutesForUnit(unit: MyUnits?): Double = when (unit) {
        MyUnits.METRIC -> BANALService.MAX_PACE.toDouble() * METERS_PER_KILOMETER / SECONDS_PER_MINUTE
        MyUnits.IMPERIAL -> BANALService.MAX_PACE.toDouble() * BANALService.METER_PER_MILE / SECONDS_PER_MINUTE
        null -> BANALService.MAX_PACE.toDouble()
    }

    private fun unitLabel(unit: MyUnits?): String = when (unit) {
        MyUnits.METRIC -> "min/km"
        MyUnits.IMPERIAL -> "min/mile"
        null -> ""
    }

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        private const val METERS_PER_KILOMETER = 1000
        private const val TWO_DIGIT_THRESHOLD = 9
    }
}
