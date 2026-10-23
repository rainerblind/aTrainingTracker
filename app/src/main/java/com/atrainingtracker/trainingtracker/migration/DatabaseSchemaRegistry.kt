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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.migration


/**
 * Registry mapping database filenames to their maximum supported schema versions (REQ-MIG-041.2).
 * Prevents restoring forward-incompatible databases created by newer app versions that would
 * otherwise trigger SQLiteDowngradeException startup crash loops.
 */
object DatabaseSchemaRegistry {

    private val SUPPORTED_DB_VERSIONS: Map<String, Int> = mapOf(
        "WorkoutSummaries.db" to 25,
        "WorkoutSamples.db" to 1,
        "Laps.db" to 2,
        "Routes.db" to 10,
        "KnownLocations.db" to 6,
        "TrackingViews.db" to 15,
        "FlexibleGenericView.db" to 15,
        "Devices.db" to 7,
        "SportType.db" to 3,
        "Segments.db" to 9,
        "StravaUpload.db" to 6,
        "Equipment.db" to 2,
        "Pebble.db" to 3,
        "AthleteMetrics.db" to 1,
        "ActiveDevices.db" to 1,
        "ExportStatus.db" to 1,
        com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodSummariesDatabaseManager.DATABASE_NAME to
                com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodSummariesDatabaseManager.DB_VERSION
    )

    @JvmStatic
    fun getMaxSupportedVersion(dbName: String): Int? = SUPPORTED_DB_VERSIONS[dbName]

    @JvmStatic
    fun isVersionSupported(dbName: String, userVersion: Int): Boolean {
        val maxSupported = SUPPORTED_DB_VERSIONS[dbName] ?: return true
        return userVersion <= maxSupported
    }

    @JvmStatic
    fun getSupportedDatabases(): Set<String> = SUPPORTED_DB_VERSIONS.keys
}
