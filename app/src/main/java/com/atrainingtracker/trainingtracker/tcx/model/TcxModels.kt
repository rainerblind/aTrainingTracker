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

package com.atrainingtracker.trainingtracker.tcx.model

/**
 * Root TCX document representing Garmin Training Center Database v2 (TrainingCenterDatabasev2.xsd).
 *
 * Part of REQ-DAT-040 (TCX Structured DTO Serialization, Parsing Decoupling & Round-Trip Fidelity).
 */
data class TcxDocument @JvmOverloads constructor(
    val activities: List<TcxActivity> = emptyList(),
    val courses: List<TcxCourse> = emptyList(),
    val author: TcxAuthor? = null
) {
    // Secondary constructor for Java compatibility: (activities, author)
    constructor(activities: List<TcxActivity>, author: TcxAuthor?) : this(activities, emptyList(), author)
}

/**
 * An individual TCX Course element (<Course> inside <Courses>).
 */
data class TcxCourse(
    val name: String,
    val laps: List<TcxLap> = emptyList(),
    val trackpoints: List<TcxTrackpoint> = emptyList(),
    val coursePoints: List<TcxCoursePoint> = emptyList(),
    val notes: String? = null
)

/**
 * An individual TCX CoursePoint element (<CoursePoint> inside <Course>).
 */
data class TcxCoursePoint(
    val name: String,
    val time: String? = null,
    val position: TcxPosition,
    val altitudeMeters: Double? = null,
    val pointType: String? = null,
    val notes: String? = null
)

/**
 * An individual TCX Activity element.
 *
 * Enforces Garmin TCX v2 sequence order:
 * `Id` -> `Lap`* -> `Notes`? -> `Creator`? -> `Extensions`? -> `Author`?
 */
data class TcxActivity(
    val sport: String,
    val id: String,
    val laps: List<TcxLap> = emptyList(),
    val notes: String? = null,
    val creator: TcxCreator? = null,
    val extensions: TcxActivityExtensions? = null
)

/**
 * An individual TCX Lap within an activity.
 *
 * Enforces Garmin TCX v2 sequence order:
 * `TotalTimeSeconds` -> `DistanceMeters` -> `MaximumSpeed`? -> `Calories`? ->
 * `AverageHeartRateBpm`? -> `MaximumHeartRateBpm`? -> `Intensity`? -> `Cadence`? ->
 * `TriggerMethod`? -> `Track`? -> `Notes`? -> `Extensions`?
 */
data class TcxLap(
    val startTime: String,
    val totalTimeSeconds: Double,
    val distanceMeters: Double,
    val maximumSpeed: Double? = null,
    val calories: Int? = null,
    val averageHeartRateBpm: Int? = null,
    val maximumHeartRateBpm: Int? = null,
    val intensity: String? = null,
    val cadence: Int? = null,
    val triggerMethod: String? = null,
    val trackpoints: List<TcxTrackpoint> = emptyList(),
    val notes: String? = null,
    val extensions: TcxLapExtensions? = null
)

/**
 * An individual TCX Trackpoint within a lap track.
 *
 * Enforces Garmin TCX v2 sequence order:
 * `Time` -> `Position`? -> `AltitudeMeters`? -> `DistanceMeters`? ->
 * `HeartRateBpm`? -> `Cadence`? -> `SensorState`? -> `Extensions`?
 */
data class TcxTrackpoint(
    val time: String,
    val position: TcxPosition? = null,
    val altitudeMeters: Double? = null,
    val distanceMeters: Double? = null,
    val heartRateBpm: Int? = null,
    val cadence: Int? = null,
    val extensions: TcxTrackpointExtensions? = null
)

/**
 * Geographic latitude and longitude coordinates.
 */
data class TcxPosition(
    val latitudeDegrees: Double,
    val longitudeDegrees: Double
)

/**
 * Garmin Trackpoint Extension v2 (`TPX`) elements:
 * `Speed` (Double, m/s), `RunCadence` (Integer, rpm/spm), `Watts` (Integer, W).
 */
data class TcxTrackpointExtensions(
    val speed: Double? = null,
    val watts: Int? = null,
    val runCadence: Int? = null
)

/**
 * Proprietary activity extension (`att:ActivityExtension`) storing custom name and description.
 */
data class TcxActivityExtensions(
    val customName: String? = null,
    val customDescription: String? = null
)

/**
 * Proprietary lap extension (`att:LapExtension`) storing custom lap name and description.
 */
data class TcxLapExtensions(
    val name: String? = null,
    val description: String? = null
)

/**
 * TCX Creator device metadata (`Device_t`).
 */
data class TcxCreator(
    val name: String,
    val unitId: Long = 0L,
    val productId: Int = 1,
    val versionMajor: Int = 4,
    val versionMinor: Int = 9,
    val buildMajor: Int = 0,
    val buildMinor: Int = 0
)

/**
 * TCX Author application metadata (`Application_t`).
 */
data class TcxAuthor(
    val name: String,
    val versionMajor: Int = 4,
    val versionMinor: Int = 9,
    val buildMajor: Int = 0,
    val buildMinor: Int = 0,
    val type: String = "Release",
    val langId: String = "en",
    val partNumber: String = "000-00000-00"
)
