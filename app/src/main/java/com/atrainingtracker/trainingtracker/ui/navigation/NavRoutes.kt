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

package com.atrainingtracker.trainingtracker.ui.navigation

import com.atrainingtracker.R

/**
 * Types of settings modal bottom sheets hosted in the single-activity architecture (REQ-UI-159).
 */
enum class SettingsBottomSheetType {
    STRAVA,
    DROPBOX,
    GOOGLE_DRIVE,
    EXPORT,
    UNITS,
    DISPLAY,
    ADVANCED_TUNING,
    SEARCH,
    ACTIVITY_TYPE
}

/**
 * Type-safe route identifiers and drawer ID mapping for Jetpack Compose Navigation (REQ-UI-159).
 */
object NavRoutes {
    const val START_TRACKING = "start_tracking"
    const val WORKOUTS = "workouts"
    const val PERIODS = "periods"
    const val MAP = "map"
    const val MAP_PATTERN = "map?locationId={locationId}"
    const val ARG_LOCATION_ID = "locationId"
    const val SEGMENTS = "segments"
    const val ROUTES = "routes"
    const val START_LOCATIONS = "start_locations"
    const val LOCATIONS = "locations"
    const val LOCATIONS_PATTERN = "locations?clusterId={clusterId}"
    const val ARG_CLUSTER_ID = "clusterId"
    const val SENSORS = "sensors"
    const val BIKES = "bikes"
    const val SHOES = "shoes"
    const val SPORT_TYPES = "sport_types"
    const val TRAINING_ZONES = "training_zones"
    const val BACKUP_RESTORE = "backup_restore"

    /**
     * Builds a navigation route to the central map, optionally parameterized with a target location ID.
     */
    fun map(locationId: Long? = null): String =
        if (locationId != null && locationId > 0) "map?$ARG_LOCATION_ID=$locationId" else MAP

    /**
     * Builds a navigation route to the cluster destination, optionally parameterized with a target cluster ID.
     */
    fun locations(clusterId: Long? = null): String =
        if (clusterId != null && clusterId > 0) "locations?$ARG_CLUSTER_ID=$clusterId" else LOCATIONS

    /**
     * Resolves a drawer item resource ID to its primary Compose navigation route.
     * Returns null if the item ID corresponds to a bottom sheet, dialog, or external intent.
     */
    fun fromDrawerItemId(itemId: Int): String? = when (itemId) {
        R.id.drawer_start_tracking -> START_TRACKING
        R.id.drawer_workouts -> WORKOUTS
        R.id.drawer_periods -> PERIODS
        R.id.drawer_map -> MAP
        R.id.drawer_segments -> SEGMENTS
        R.id.drawer_routes -> ROUTES
        R.id.drawer_start_locations -> START_LOCATIONS
        R.id.drawer_my_locations -> LOCATIONS
        R.id.drawer_my_sensors -> SENSORS
        R.id.drawer_bikes -> BIKES
        R.id.drawer_shoes -> SHOES
        R.id.drawer_sport_types -> SPORT_TYPES
        R.id.drawer_training_zones -> TRAINING_ZONES
        R.id.drawer_backup_restore -> BACKUP_RESTORE
        else -> null
    }

    /**
     * Resolves a Compose route to its corresponding drawer navigation menu resource ID.
     */
    fun toDrawerItemId(route: String?): Int = when (route?.substringBefore("?")?.substringBefore("/")) {
        START_TRACKING -> R.id.drawer_start_tracking
        WORKOUTS -> R.id.drawer_workouts
        PERIODS -> R.id.drawer_periods
        MAP -> R.id.drawer_map
        SEGMENTS -> R.id.drawer_segments
        ROUTES -> R.id.drawer_routes
        START_LOCATIONS -> R.id.drawer_start_locations
        LOCATIONS -> R.id.drawer_my_locations
        SENSORS -> R.id.drawer_my_sensors
        BIKES -> R.id.drawer_bikes
        SHOES -> R.id.drawer_shoes
        SPORT_TYPES -> R.id.drawer_sport_types
        TRAINING_ZONES -> R.id.drawer_training_zones
        BACKUP_RESTORE -> R.id.drawer_backup_restore
        else -> R.id.drawer_start_tracking
    }

    /**
     * Resolves a drawer item resource ID to a settings bottom sheet type, or null if not a sheet.
     */
    fun toBottomSheetType(itemId: Int): SettingsBottomSheetType? = when (itemId) {
        R.id.drawer_strava -> SettingsBottomSheetType.STRAVA
        R.id.drawer_dropbox -> SettingsBottomSheetType.DROPBOX
        R.id.drawer_google_drive -> SettingsBottomSheetType.GOOGLE_DRIVE
        R.id.drawer_export -> SettingsBottomSheetType.EXPORT
        R.id.drawer_units -> SettingsBottomSheetType.UNITS
        R.id.drawer_display_settings -> SettingsBottomSheetType.DISPLAY
        R.id.drawer_advanced_tuning -> SettingsBottomSheetType.ADVANCED_TUNING
        R.id.drawer_search_settings -> SettingsBottomSheetType.SEARCH
        R.id.drawer_tracking_layouts -> SettingsBottomSheetType.ACTIVITY_TYPE
        else -> null
    }

    fun toSettingsBottomSheetType(itemId: Int): SettingsBottomSheetType? = toBottomSheetType(itemId)
}
