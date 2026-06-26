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

package com.atrainingtracker.trainingtracker.ui.util

import androidx.compose.runtime.staticCompositionLocalOf
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.CadenceFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter

/**
 * A central context for all metric formatters to ensure consistency across the app.
 */
data class MetricFormatterContext(
    val distance: DistanceFormatter = DistanceFormatter(),
    val altitude: AltitudeFormatter = AltitudeFormatter(),
    val time: TimeFormatter = TimeFormatter(),
    val speed: SpeedFormatter = SpeedFormatter(),
    val pace: PaceFormatter = PaceFormatter(),
    val cadence: CadenceFormatter = CadenceFormatter()
)

val LocalMetricFormatter = staticCompositionLocalOf { MetricFormatterContext() }
