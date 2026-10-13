package com.atrainingtracker.trainingtracker.database

import android.content.Context
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.MyLocation
import com.atrainingtracker.trainingtracker.location.LocationNameResolver
import java.util.Locale
import kotlin.math.abs

/**
 * Helper object providing intelligent, localized workout and cluster proposal naming
 * based on recognized favorite start and destination locations (Lieblingsorte) (ATT-1398, ATT-2247).
 */
object WorkoutAutoNamingHelper {

    /**
     * Formats the total session distance cleanly according to the athlete's unit preferences (REQ-TRK-012).
     *
     * In Metric mode: distance is converted to kilometers (km).
     * In Imperial mode: distance is converted to miles (mi).
     *
     * Precision guidelines:
     * - Distances < 10.0: formatted with 1 decimal place (e.g. "8.2 km", "5.1 mi").
     * - Distances >= 10.0: formatted with 0 decimal places if effectively an integer (e.g. "42 km", "26 mi"),
     *   otherwise with 1 decimal place (e.g. "42.5 km", "26.2 mi").
     */
    @JvmStatic
    @JvmOverloads
    fun formatSessionDistance(
        context: Context,
        distanceMeters: Double,
        unit: MyUnits = TrainingApplication.getUnit()
    ): String {
        val isImperial = unit == MyUnits.IMPERIAL
        val dist = if (isImperial) distanceMeters / BANALService.METER_PER_MILE else distanceMeters / 1000.0
        val unitStr = if (isImperial) "mi" else "km"

        return if (dist < 10.0) {
            String.format(Locale.getDefault(), "%.1f %s", dist, unitStr)
        } else {
            val rounded = Math.round(dist)
            if (abs(dist - rounded) < 0.05) {
                String.format(Locale.getDefault(), "%d %s", rounded, unitStr)
            } else {
                String.format(Locale.getDefault(), "%.1f %s", dist, unitStr)
            }
        }
    }

    /**
     * Generates a localized workout name based on sport type and start/destination favorite locations.
     *
     * @param context Application context for resource access
     * @param sportType Activity sport type (RUN, BIKE, or UNKNOWN/CONFLICT)
     * @param startLocation Recognized favorite start location (or null)
     * @param endLocation Recognized favorite end location (or null)
     * @param distanceBetweenEndpoints Distance in meters between start and end coordinates
     * @param distanceTotalMeters Total distance recorded during the session in meters (REQ-TRK-012)
     * @return Generated localized workout name, or null if neither endpoint is recognized
     */
    @JvmStatic
    @JvmOverloads
    fun generateWorkoutName(
        context: Context,
        sportType: BSportType?,
        startLocation: MyLocation?,
        endLocation: MyLocation?,
        distanceBetweenEndpoints: Float = Float.MAX_VALUE,
        distanceTotalMeters: Double = 0.0
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

        val formattedDistance = if (distanceTotalMeters > 0.0) formatSessionDistance(context, distanceTotalMeters) else null

        // Case 2: Round Trip / Loop (Start matches end by ID or endpoints are within start location geofence)
        val isRoundTrip = startLocation != null && (
            (endLocation != null && startLocation.id == endLocation.id) ||
            (distanceBetweenEndpoints <= startLocation.radius)
        )

        if (isRoundTrip) {
            return if (formattedDistance != null) {
                when (type) {
                    BSportType.RUN -> context.getString(R.string.workout_name_run_from_with_distance, startName, formattedDistance)
                    BSportType.BIKE -> context.getString(R.string.workout_name_ride_from_with_distance, startName, formattedDistance)
                    else -> context.getString(R.string.workout_name_round_trip_with_distance, startName, formattedDistance)
                }
            } else {
                when (type) {
                    BSportType.RUN -> context.getString(R.string.workout_name_run_from, startName)
                    BSportType.BIKE -> context.getString(R.string.workout_name_ride_from, startName)
                    else -> context.getString(R.string.workout_name_round_trip, startName)
                }
            }
        }

        // Case 3: Start Location Only Known
        if (startName != null && endName == null) {
            return if (formattedDistance != null) {
                when (type) {
                    BSportType.RUN -> context.getString(R.string.workout_name_run_from_with_distance, startName, formattedDistance)
                    BSportType.BIKE -> context.getString(R.string.workout_name_ride_from_with_distance, startName, formattedDistance)
                    else -> context.getString(R.string.workout_name_activity_from_with_distance, startName, formattedDistance)
                }
            } else {
                when (type) {
                    BSportType.RUN -> context.getString(R.string.workout_name_run_from, startName)
                    BSportType.BIKE -> context.getString(R.string.workout_name_ride_from, startName)
                    else -> context.getString(R.string.workout_name_activity_from, startName)
                }
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
        val fallbackName = startName ?: endName
        return if (formattedDistance != null) {
            when (type) {
                BSportType.RUN -> context.getString(R.string.workout_name_run_from_with_distance, fallbackName, formattedDistance)
                BSportType.BIKE -> context.getString(R.string.workout_name_ride_from_with_distance, fallbackName, formattedDistance)
                else -> context.getString(R.string.workout_name_round_trip_with_distance, fallbackName, formattedDistance)
            }
        } else {
            when (type) {
                BSportType.RUN -> context.getString(R.string.workout_name_run_from, fallbackName)
                BSportType.BIKE -> context.getString(R.string.workout_name_ride_from, fallbackName)
                else -> context.getString(R.string.workout_name_round_trip, fallbackName)
            }
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

