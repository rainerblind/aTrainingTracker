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

package com.atrainingtracker.trainingtracker.onlinecommunities.strava

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

@Serializable
data class StravaMap(
    val id: String,
    val polyline: String? = null,
    @SerialName("summary_polyline") val summaryPolyline: String? = null
)

@Serializable
data class StravaRoute(
    val id: Long,
    @SerialName("id_str") val idStr: String,
    val name: String,
    val description: String? = null,
    val distance: Double,
    @SerialName("elevation_gain") val elevationGain: Double,
    val map: StravaMap,
    val type: Int, // 1 for Ride, 2 for Run
    @SerialName("sub_type") val subType: Int,
    val starred: Boolean = false
)

@Serializable
data class StravaSegment(
    val id: Long,
    val name: String,
    @SerialName("activity_type") val activityType: String,
    val distance: Double,
    @SerialName("average_grade") val averageGrade: Double,
    @SerialName("maximum_grade") val maximumGrade: Double,
    @SerialName("elevation_high") val elevationHigh: Double,
    @SerialName("elevation_low") val elevationLow: Double,
    @SerialName("total_elevation_gain") val totalElevationGain: Double? = null,
    @SerialName("start_latlng") val startLatLng: List<Double>,
    @SerialName("end_latlng") val endLatLng: List<Double>,
    @SerialName("climb_category") val climbCategory: Int,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val map: StravaMap? = null,
    @SerialName("pr_time") var prTime: Int? = null
)

@Serializable
data class StravaStream(
    val type: String,
    val data: List<JsonElement>,
    @SerialName("series_type") val seriesType: String,
    val resolution: String,
    @SerialName("original_size") val originalSize: Int
)
