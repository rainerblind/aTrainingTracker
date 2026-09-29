package com.atrainingtracker.trainingtracker.database

import android.content.Context
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.MyLocation
import com.atrainingtracker.trainingtracker.location.LocationNameResolver

/**
 * Helper object providing intelligent, localized workout and cluster proposal naming
 * based on recognized favorite start and destination locations (Lieblingsorte) (ATT-1398).
 */
object WorkoutAutoNamingHelper {

    /**
     * Generates a localized workout name based on sport type and start/destination favorite locations.
     *
     * @param context Application context for resource access
     * @param sportType Activity sport type (RUN, BIKE, or UNKNOWN/CONFLICT)
     * @param startLocation Recognized favorite start location (or null)
     * @param endLocation Recognized favorite end location (or null)
     * @param distanceBetweenEndpoints Distance in meters between start and end coordinates
     * @return Generated localized workout name, or null if neither endpoint is recognized
     */
    @JvmStatic
    @JvmOverloads
    fun generateWorkoutName(
        context: Context,
        sportType: BSportType?,
        startLocation: MyLocation?,
        endLocation: MyLocation?,
        distanceBetweenEndpoints: Float = Float.MAX_VALUE
    ): String? {
        val startName = getDisplayName(context, startLocation)
        val endName = getDisplayName(context, endLocation)

        if (startName == null && endName == null) {
            return null
        }

        val type = sportType ?: BSportType.UNKNOWN

        // Case 1: Point-to-Point (Both start and end are recognized and distinct)
        if (startLocation != null && endLocation != null && startLocation.id != endLocation.id) {
            return when (type) {
                BSportType.RUN -> context.getString(R.string.workout_name_run_from_to, startName, endName)
                BSportType.BIKE -> context.getString(R.string.workout_name_ride_from_to, startName, endName)
                else -> context.getString(R.string.workout_name_activity_from_to, startName, endName)
            }
        }

        // Case 2: Round Trip / Loop (Start matches end by ID or endpoints are within start location geofence)
        val isRoundTrip = startLocation != null && (
            (endLocation != null && startLocation.id == endLocation.id) ||
            (distanceBetweenEndpoints <= startLocation.radius)
        )

        if (isRoundTrip) {
            return when (type) {
                BSportType.RUN -> context.getString(R.string.workout_name_run_from, startName)
                BSportType.BIKE -> context.getString(R.string.workout_name_ride_from, startName)
                else -> context.getString(R.string.workout_name_round_trip, startName)
            }
        }

        // Case 3: Start Location Only Known
        if (startName != null && endName == null) {
            return when (type) {
                BSportType.RUN -> context.getString(R.string.workout_name_run_from, startName)
                BSportType.BIKE -> context.getString(R.string.workout_name_ride_from, startName)
                else -> context.getString(R.string.workout_name_activity_from, startName)
            }
        }

        // Case 4: Destination Location Only Known
        if (startName == null && endName != null) {
            return when (type) {
                BSportType.RUN -> context.getString(R.string.workout_name_run_to, endName)
                BSportType.BIKE -> context.getString(R.string.workout_name_ride_to, endName)
                else -> context.getString(R.string.workout_name_activity_to, endName)
            }
        }

        // Fallback if both resolve to same location name but not identified as round-trip
        return when (type) {
            BSportType.RUN -> context.getString(R.string.workout_name_run_from, startName ?: endName)
            BSportType.BIKE -> context.getString(R.string.workout_name_ride_from, startName ?: endName)
            else -> context.getString(R.string.workout_name_round_trip, startName ?: endName)
        }
    }

    /**
     * Generates a default cluster seed name for an unclustered track starting at a recognized location.
     *
     * @param context Application context for resource access
     * @param startLocation Recognized favorite start location
     * @return Localized cluster seed name (e.g. "Loop from Home" / "Runde ab Zuhause"), or null if not recognized
     */
    @JvmStatic
    fun generateClusterSeedName(
        context: Context,
        startLocation: MyLocation?
    ): String? {
        val startName = getDisplayName(context, startLocation) ?: return null
        return context.getString(R.string.cluster_seed_name_loop_format, startName)
    }

    @JvmStatic
    fun getDisplayName(context: Context, location: MyLocation?): String? {
        if (location == null) return null
        val name = location.name?.trim()
        if (!name.isNullOrEmpty()) {
            return name
        }
        return LocationNameResolver.formatFallback(context, location.latLng.latitude, location.latLng.longitude)
    }
}

