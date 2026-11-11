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

package com.atrainingtracker.trainingtracker.routes.parsers

import com.atrainingtracker.trainingtracker.tcx.model.TcxDocument
import com.atrainingtracker.trainingtracker.tcx.model.TcxTrackpoint
import com.atrainingtracker.trainingtracker.tcx.parser.TcxParser
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Unified streaming XML pull parser for Garmin TCX files (Workouts and Courses).
 *
 * Traceability:
 * - REQ-MIG-037: Unified Streaming TCX and GPX XML Trackpoint Parsing Engine (Rule 36).
 * - REQ-DAT-040.3: Delegated to canonical [TcxParser].
 * - TST-MIG-034: Unified Streaming TCX and GPX XML Trackpoint Parsing Engine Verification.
 */
object TcxTrackpointParser {

    fun parse(xmlContent: String): Result<ParsedTcxData> {
        return parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
    }

    fun parse(inputStream: InputStream): Result<ParsedTcxData> {
        val docResult = TcxParser.parse(inputStream)
        if (docResult.isFailure) {
            return Result.failure(docResult.exceptionOrNull() ?: Exception("Failed to parse TCX"))
        }
        val document = docResult.getOrThrow()
        val data = ParsedTcxData()

        if (document.courses.isNotEmpty()) {
            val course = document.courses.first()
            data.name = course.name.takeIf { it.isNotBlank() }
            data.notes = course.notes

            for (cp in course.coursePoints) {
                data.coursePoints.add(
                    RawCoursePoint(
                        name = cp.name,
                        lat = cp.position.latitudeDegrees,
                        lng = cp.position.longitudeDegrees,
                        altitude = cp.altitudeMeters,
                        type = cp.pointType,
                        notes = cp.notes,
                        time = cp.time
                    )
                )
            }

            if (course.laps.isNotEmpty()) {
                for (lap in course.laps) {
                    val rawLap = RawLap(
                        lapNr = data.laps.size.toLong(),
                        startTime = lap.startTime.takeIf { it.isNotBlank() },
                        totalTimeSeconds = lap.totalTimeSeconds,
                        distanceMeters = lap.distanceMeters,
                        maxSpeed = lap.maximumSpeed,
                        calories = lap.calories,
                        avgHeartRate = lap.averageHeartRateBpm,
                        name = lap.extensions?.name,
                        description = lap.extensions?.description,
                        trackpoints = lap.trackpoints.map { toRawTrackpoint(it) }.toMutableList()
                    )
                    data.laps.add(rawLap)
                }
            } else if (course.trackpoints.isNotEmpty()) {
                val rawLap = RawLap(
                    lapNr = 0L,
                    trackpoints = course.trackpoints.map { toRawTrackpoint(it) }.toMutableList()
                )
                data.laps.add(rawLap)
            }
        } else if (document.activities.isNotEmpty()) {
            val activity = document.activities.first()
            data.sport = activity.sport
            data.name = activity.extensions?.customName
            data.notes = activity.notes ?: activity.extensions?.customDescription

            for ((lapIndex, lap) in activity.laps.withIndex()) {
                val rawLap = RawLap(
                    lapNr = lapIndex.toLong(),
                    startTime = lap.startTime.takeIf { it.isNotBlank() },
                    totalTimeSeconds = lap.totalTimeSeconds,
                    distanceMeters = lap.distanceMeters,
                    maxSpeed = lap.maximumSpeed,
                    calories = lap.calories,
                    avgHeartRate = lap.averageHeartRateBpm,
                    name = lap.extensions?.name,
                    description = lap.extensions?.description ?: lap.notes,
                    trackpoints = lap.trackpoints.map { toRawTrackpoint(it) }.toMutableList()
                )
                data.laps.add(rawLap)
            }
        }

        return Result.success(data)
    }

    private fun toRawTrackpoint(tp: TcxTrackpoint): RawTrackpoint {
        return RawTrackpoint(
            lat = tp.position?.latitudeDegrees,
            lng = tp.position?.longitudeDegrees,
            altitude = tp.altitudeMeters,
            distanceMeters = tp.distanceMeters,
            time = tp.time.takeIf { it.isNotBlank() },
            hr = tp.heartRateBpm,
            cadence = tp.cadence,
            power = tp.extensions?.watts,
            speedMps = tp.extensions?.speed
        )
    }
}
