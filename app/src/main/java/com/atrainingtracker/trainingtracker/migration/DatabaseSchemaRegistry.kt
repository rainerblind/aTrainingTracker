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
        com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.database.LapsDatabaseManager.LapsDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.database.LapsDatabaseManager.LapsDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.RoutesDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.RoutesDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.KnownLocationsDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.KnownLocationsDbHelper.DB_VERSION,
        // Legacy backup filename alias retained alongside FlexibleGenericView.db for backward compatibility (REQ-MIG-057.1)
        "TrackingViews.db" to
                com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager.TrackingViewsDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager.TrackingViewsDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager.TrackingViewsDbHelper.DB_VERSION,
        com.atrainingtracker.banalservice.database.DevicesDatabaseManager.DevicesDbHelper.DB_NAME to
                com.atrainingtracker.banalservice.database.DevicesDatabaseManager.DevicesDbHelper.DB_VERSION,
        com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SportTypeDbHelper.DB_NAME to
                com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SportTypeDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.SegmentsDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.SegmentsDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.database.EquipmentDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.database.EquipmentDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.database.AthleteMetricsDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.database.AthleteMetricsDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.database.ActiveDevicesDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.database.ActiveDevicesDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager.ExportStatusDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager.ExportStatusDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.database.ElevationCacheDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.database.ElevationCacheDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrDbHelper.DB_NAME to
                com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrDbHelper.DB_VERSION,
        com.atrainingtracker.trainingtracker.database.ClimbsDbHelper.DATABASE_NAME to
                com.atrainingtracker.trainingtracker.database.ClimbsDbHelper.DATABASE_VERSION,
        com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager.DB_NAME to
                com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager.DB_VERSION,
        com.atrainingtracker.trainingtracker.database.SportTypeEquipmentLinkManager.DB_NAME to
                com.atrainingtracker.trainingtracker.database.SportTypeEquipmentLinkManager.DB_VERSION
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
