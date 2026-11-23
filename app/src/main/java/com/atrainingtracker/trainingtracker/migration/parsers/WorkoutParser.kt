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

package com.atrainingtracker.trainingtracker.migration.parsers

import android.content.Context
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * Unified parser contract for workout ingestion formats (`.fit`, `.tcx`, `.gpx`)
 * returning a structured [ParsedWorkoutData] model (`REQ-MIG-061.1`, `REQ-MIG-038`).
 */
interface WorkoutParser {
    /** Canonical lowercase file extension without leading dot (`"fit"`, `"tcx"`, `"gpx"`). */
    val formatExtension: String

    /** Persisted [WorkoutSource] tag recorded for workouts imported by this parser. */
    val workoutSource: WorkoutSource

    /** Parses a workout file on disk into [ParsedWorkoutData]. */
    fun parse(file: File, context: Context): Result<ParsedWorkoutData>

    /** Parses a workout stream into [ParsedWorkoutData]. */
    fun parse(stream: InputStream, context: Context): Result<ParsedWorkoutData>

    companion object {
        val ALL: List<WorkoutParser> = listOf(FitWorkoutParser, TcxWorkoutParser, GpxWorkoutParser)

        /**
         * Resolves the matching [WorkoutParser] for a file extension, filename, or format name
         * (e.g. `"fit"`, `".tcx"`, `"ride.gpx.gz"`), or `null` if unsupported.
         */
        @JvmStatic
        fun forFormat(formatOrFileName: String?): WorkoutParser? {
            val normalized = formatOrFileName?.trim()?.lowercase(Locale.ROOT) ?: return null
            if (normalized.isEmpty()) return null
            val strippedGz = if (normalized.endsWith(".gz")) normalized.removeSuffix(".gz") else normalized
            val ext = strippedGz.substringAfterLast('.').ifEmpty { strippedGz }
            return when (ext) {
                "fit" -> FitWorkoutParser
                "tcx" -> TcxWorkoutParser
                "gpx" -> GpxWorkoutParser
                else -> null
            }
        }
    }
}
