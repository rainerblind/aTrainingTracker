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

package com.atrainingtracker.trainingtracker.ui.aftermath.hrv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvScatterIntensityMode
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvScatterMath
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvScatterPoint
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvScatterRegression
import com.atrainingtracker.trainingtracker.ui.map.PathPoint

typealias HrvScatterIntensityMode = HrvScatterIntensityMode
typealias HrvScatterPoint = HrvScatterPoint
typealias HrvScatterRegression = HrvScatterRegression
val HrvScatterMath = HrvScatterMath

@Composable
fun HrvAlpha1ScatterChart(
    points: List<PathPoint>,
    intensityMode: HrvScatterIntensityMode = HrvScatterIntensityMode.HEART_RATE,
    modifier: Modifier = Modifier
) {
    com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvAlpha1ScatterChart(
        points = points,
        intensityMode = intensityMode,
        modifier = modifier
    )
}
