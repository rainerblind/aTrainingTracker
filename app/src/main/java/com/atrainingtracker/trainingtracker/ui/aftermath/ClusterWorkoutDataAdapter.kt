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

package com.atrainingtracker.trainingtracker.ui.aftermath

import com.atrainingtracker.trainingtracker.database.ClusterWorkoutSnapshot

/**
 * Maps UI/Aftermath [WorkoutData] to persistence-layer [ClusterWorkoutSnapshot]
 * so `database.WorkoutClusterEngine` stays free of `ui.aftermath` imports (`IF-SWE-05`, REQ-PRO-130.2).
 */
fun WorkoutData.toClusterWorkoutSnapshot(): ClusterWorkoutSnapshot = ClusterWorkoutSnapshot(
    id = id,
    finished = finished,
    clusterId = clusterId,
    sportId = sportId,
    workoutName = workoutName,
    totalDistance = totalDistance,
    startTimeS = startTimeS,
    mapPolyline = mapPolyline,
    startLatLng = startLatLng,
    endLatLng = endLatLng,
    maxDisplacementLatLng = maxDisplacementLatLng,
    minLat = minLat,
    minLng = minLng,
    maxLat = maxLat,
    maxLng = maxLng,
    minAltitudeLatLng = minAltitudeLatLng,
    maxAltitudeLatLng = maxAltitudeLatLng
)
