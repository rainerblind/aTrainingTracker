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
import java.util.Locale

class DistanceFormatter : MyFormatter<Number> {
    override fun format(value: Number?): String {
        if (value == null) {
            return "--"
        }

        return when (TrainingApplication.getUnit()) {
            MyUnits.METRIC -> {
                val km = value.toDouble() / METERS_PER_KILOMETER
                String.format(Locale.getDefault(), "%.2f", km)
            }
            MyUnits.IMPERIAL -> {
                val miles = value.toDouble() / BANALService.METER_PER_MILE
                String.format(Locale.getDefault(), "%.2f", miles)
            }
            else -> "--"
        }
    }

    @Suppress("FunctionNaming")
    fun format_with_units(value: Number?): String {
        if (value == null) {
            return "--"
        }

        return when (TrainingApplication.getUnit()) {
            MyUnits.METRIC -> formatMetricWithUnits(value)
            MyUnits.IMPERIAL -> formatImperialWithUnits(value)
            else -> "--"
        }
    }

    private fun formatMetricWithUnits(value: Number): String {
        val intVal = value.toInt()
        val km = value.toDouble() / METERS_PER_KILOMETER
        return when {
            intVal < METERS_THRESHOLD_KM -> String.format(Locale.getDefault(), "%d m", intVal)
            intVal < DISTANCE_100_KM -> String.format(Locale.getDefault(), "%.2f km", km)
            intVal < DISTANCE_1000_KM -> String.format(Locale.getDefault(), "%.1f km", km)
            else -> String.format(Locale.getDefault(), "%,.0f km", km)
        }
    }

    private fun formatImperialWithUnits(value: Number): String {
        val d = value.toDouble()
        val miles = d / BANALService.METER_PER_MILE
        val feet = d / BANALService.METER_PER_FOOT
        return when {
            d < IMPERIAL_FEET_THRESHOLD -> String.format(Locale.getDefault(), "%.0f ft", feet)
            d < IMPERIAL_MILE_THRESHOLD -> String.format(Locale.getDefault(), "%.2f mile", miles)
            else -> String.format(Locale.getDefault(), "%,.0f mile", miles)
        }
    }

    @Suppress("FunctionNaming")
    fun format_3(value: Number?): String {
        if (value == null) {
            return "--"
        }

        return when (TrainingApplication.getUnit()) {
            MyUnits.METRIC -> {
                val km = value.toDouble() / METERS_PER_KILOMETER
                String.format(Locale.getDefault(), "%.3f", km)
            }
            MyUnits.IMPERIAL -> {
                val miles = value.toDouble() / BANALService.METER_PER_MILE
                String.format(Locale.getDefault(), "%.3f", miles)
            }
            else -> "--"
        }
    }

    companion object {
        private const val METERS_PER_KILOMETER = 1000.0
        private const val METERS_THRESHOLD_KM = 1000
        private const val DISTANCE_100_KM = 100_000
        private const val DISTANCE_1000_KM = 1_000_000
        private const val IMPERIAL_FEET_THRESHOLD = 1000.0
        private const val IMPERIAL_MILE_THRESHOLD = 1609344.0
    }
}
