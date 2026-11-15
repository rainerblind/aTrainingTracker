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

import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import java.util.Locale

class SpeedFormatter : MyFormatter<Number> {
    override fun format(value: Number?): String {
        if (value == null) {
            return "--"
        }

        val speed = MyHelper.mps2userUnit(value.toDouble())
        return String.format(Locale.getDefault(), "%.1f", speed)
    }

    @Suppress("FunctionNaming")
    fun format_with_units(value: Number?): String {
        val units = when (TrainingApplication.getUnit()) {
            MyUnits.METRIC -> "km/h"
            MyUnits.IMPERIAL -> "mile/h"
            null -> ""
        }

        return "${format(value)} $units"
    }
}
