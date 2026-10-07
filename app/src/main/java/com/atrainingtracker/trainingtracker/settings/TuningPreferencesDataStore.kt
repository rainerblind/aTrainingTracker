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

package com.atrainingtracker.trainingtracker.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldVariant

/**
 * Selectable horizontal X-axis domain for Aftermath elevation profile and scrubbing.
 * (REQ-UI-201 / ATT-1391)
 */
enum class ProfileXAxisDomain {
    DISTANCE,
    TIME
}

/**
 * Encapsulates battle-tested factory defaults and safety clamping bounds for advanced tuning.
 */
object TuningPreferencesDefaults {
    val ELEVATION_X_AXIS_DOMAIN = ProfileXAxisDomain.DISTANCE
    val TELEMETRY_X_AXIS_DOMAIN = ProfileXAxisDomain.TIME
    val PROFILE_X_AXIS_DOMAIN = ELEVATION_X_AXIS_DOMAIN
    val COCKPIT_FONT_FAMILY = CockpitFontFamily.SYSTEM_DEFAULT
    val COCKPIT_FONT_WEIGHT = CockpitFontWeight.SEMI_BOLD
    val SENSOR_FIELD_VARIANT = SensorFieldVariant.CLASSIC_SEAMLESS
    const val FULL_DIM_FACTOR = 0.25f
    const val MEDIUM_DIM_FACTOR = 0.50f
    const val SLOPE_FLAT_THRESHOLD = 2.0f
    const val SLOPE_STEEP_THRESHOLD = 5.0f
    const val WAKEUP_DURATION_SEC = 15
    const val DOWNWARD_DELAY_SEC = 3
    const val GPS_ACCURACY_THRESHOLD_M = 50.0f
    const val ALTITUDE_FILTER_WINDOW_SEC = 21
    const val SLOPE_MIN_SPEED_MPS = 0.5f

    const val MIN_FULL_DIM_FACTOR = 0.05f
    const val MAX_FULL_DIM_FACTOR = 0.50f
    const val MIN_MEDIUM_DIM_FACTOR = 0.20f
    const val MAX_MEDIUM_DIM_FACTOR = 0.90f
    const val MIN_SLOPE_FLAT = 0.0f
    const val MAX_SLOPE_FLAT = 5.0f
    const val MIN_SLOPE_STEEP = 2.0f
    const val MAX_SLOPE_STEEP = 15.0f
    const val MIN_WAKEUP_SEC = 5
    const val MAX_WAKEUP_SEC = 60
    const val MIN_DOWNWARD_DELAY_SEC = 1
    const val MAX_DOWNWARD_DELAY_SEC = 15
    const val MIN_GPS_ACCURACY_M = 10.0f
    const val MAX_GPS_ACCURACY_M = 500.0f
    const val MIN_ALTITUDE_WINDOW_SEC = 5
    const val MAX_ALTITUDE_WINDOW_SEC = 60
    const val MIN_SLOPE_SPEED_MPS = 0.2f
    const val MAX_SLOPE_SPEED_MPS = 2.0f

    const val DEFAULT_PACE_CEILING_MIN_KM = 3.0f
    const val MIN_PACE_CEILING_MIN_KM = 2.0f
    const val MAX_PACE_CEILING_MIN_KM = 6.0f

    const val SHOW_LIVE_CLIMBS = true
    const val CLIMB_MIN_LENGTH_METERS = 500.0f
    const val CLIMB_MIN_GRADIENT_PERCENT = 3.0f

    const val TURN_PROMPTS_ENABLED = true
    const val TURN_AUDIO_ALERTS_ENABLED = true
    const val TURN_CUE_COUNTDOWN_DISTANCE_METERS = 100.0f
    const val MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS = 30.0f
    const val MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS = 300.0f
    const val OFF_ROUTE_CORRIDOR_THRESHOLD_METERS = 50.0f
    const val MIN_OFF_ROUTE_CORRIDOR_THRESHOLD_METERS = 20.0f
    const val MAX_OFF_ROUTE_CORRIDOR_THRESHOLD_METERS = 200.0f


    const val SENSOR_FIELD_CORNER_RADIUS = 0.0f
    const val MIN_SENSOR_FIELD_CORNER_RADIUS = 0.0f
    const val MAX_SENSOR_FIELD_CORNER_RADIUS = 20.0f

    const val SENSOR_FIELD_BORDER_THICKNESS = 1.0f
    const val MIN_SENSOR_FIELD_BORDER_THICKNESS = 0.0f
    const val MAX_SENSOR_FIELD_BORDER_THICKNESS = 4.0f

    const val SENSOR_FIELD_BORDER_CONTRAST = 0.0f
    const val MIN_SENSOR_FIELD_BORDER_CONTRAST = 0.0f
    const val MAX_SENSOR_FIELD_BORDER_CONTRAST = 1.0f

    const val DEFAULT_ROUTE_SELECTION_RADIUS_KM = 1.0f
    const val MIN_ROUTE_SELECTION_RADIUS_KM = 0.5f
    const val MAX_ROUTE_SELECTION_RADIUS_KM = 10.0f
}

/**
 * Immutable snapshot of active tuning preferences.
 */
data class TuningConfig(
    val elevationXAxisDomain: ProfileXAxisDomain = TuningPreferencesDefaults.ELEVATION_X_AXIS_DOMAIN,
    val telemetryXAxisDomain: ProfileXAxisDomain = TuningPreferencesDefaults.TELEMETRY_X_AXIS_DOMAIN,
    val cockpitFontFamily: CockpitFontFamily = TuningPreferencesDefaults.COCKPIT_FONT_FAMILY,
    val cockpitFontWeight: CockpitFontWeight = TuningPreferencesDefaults.COCKPIT_FONT_WEIGHT,
    val sensorFieldVariant: SensorFieldVariant = TuningPreferencesDefaults.SENSOR_FIELD_VARIANT,
    val fullDimFactor: Float = TuningPreferencesDefaults.FULL_DIM_FACTOR,
    val mediumDimFactor: Float = TuningPreferencesDefaults.MEDIUM_DIM_FACTOR,
    val slopeFlatThreshold: Float = TuningPreferencesDefaults.SLOPE_FLAT_THRESHOLD,
    val slopeSteepThreshold: Float = TuningPreferencesDefaults.SLOPE_STEEP_THRESHOLD,
    val wakeupDurationSec: Int = TuningPreferencesDefaults.WAKEUP_DURATION_SEC,
    val downwardDelaySec: Int = TuningPreferencesDefaults.DOWNWARD_DELAY_SEC,
    val gpsAccuracyThresholdMeters: Float = TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M,
    val altitudeFilterWindowSec: Int = TuningPreferencesDefaults.ALTITUDE_FILTER_WINDOW_SEC,
    val slopeMinSpeedMps: Float = TuningPreferencesDefaults.SLOPE_MIN_SPEED_MPS,
    val paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM,
    val showLiveClimbs: Boolean = TuningPreferencesDefaults.SHOW_LIVE_CLIMBS,
    val climbMinLengthMeters: Float = TuningPreferencesDefaults.CLIMB_MIN_LENGTH_METERS,
    val climbMinGradientPercent: Float = TuningPreferencesDefaults.CLIMB_MIN_GRADIENT_PERCENT,
    val turnPromptsEnabled: Boolean = TuningPreferencesDefaults.TURN_PROMPTS_ENABLED,
    val turnAudioAlertsEnabled: Boolean = TuningPreferencesDefaults.TURN_AUDIO_ALERTS_ENABLED,
    val turnCueCountdownDistanceMeters: Float = TuningPreferencesDefaults.TURN_CUE_COUNTDOWN_DISTANCE_METERS,
    val offRouteCorridorThresholdMeters: Float = TuningPreferencesDefaults.OFF_ROUTE_CORRIDOR_THRESHOLD_METERS,
    val sensorFieldCornerRadius: Float = TuningPreferencesDefaults.SENSOR_FIELD_CORNER_RADIUS,
    val sensorFieldBorderThickness: Float = TuningPreferencesDefaults.SENSOR_FIELD_BORDER_THICKNESS,
    val sensorFieldBorderContrast: Float = TuningPreferencesDefaults.SENSOR_FIELD_BORDER_CONTRAST,
    val routeSelectionRadiusKm: Float = TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM
) {
    @Deprecated("Use elevationXAxisDomain or telemetryXAxisDomain", ReplaceWith("elevationXAxisDomain"))
    val profileXAxisDomain: ProfileXAxisDomain
        get() = elevationXAxisDomain

    @Deprecated("Use constructor with elevationXAxisDomain and telemetryXAxisDomain")
    constructor(
        profileXAxisDomain: ProfileXAxisDomain,
        cockpitFontFamily: CockpitFontFamily = TuningPreferencesDefaults.COCKPIT_FONT_FAMILY,
        cockpitFontWeight: CockpitFontWeight = TuningPreferencesDefaults.COCKPIT_FONT_WEIGHT,
        fullDimFactor: Float = TuningPreferencesDefaults.FULL_DIM_FACTOR,
        mediumDimFactor: Float = TuningPreferencesDefaults.MEDIUM_DIM_FACTOR,
        slopeFlatThreshold: Float = TuningPreferencesDefaults.SLOPE_FLAT_THRESHOLD,
        slopeSteepThreshold: Float = TuningPreferencesDefaults.SLOPE_STEEP_THRESHOLD,
        wakeupDurationSec: Int = TuningPreferencesDefaults.WAKEUP_DURATION_SEC,
        downwardDelaySec: Int = TuningPreferencesDefaults.DOWNWARD_DELAY_SEC,
        gpsAccuracyThresholdMeters: Float = TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M,
        altitudeFilterWindowSec: Int = TuningPreferencesDefaults.ALTITUDE_FILTER_WINDOW_SEC,
        slopeMinSpeedMps: Float = TuningPreferencesDefaults.SLOPE_MIN_SPEED_MPS,
        paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM
    ) : this(
        elevationXAxisDomain = profileXAxisDomain,
        telemetryXAxisDomain = profileXAxisDomain,
        cockpitFontFamily = cockpitFontFamily,
        cockpitFontWeight = cockpitFontWeight,
        fullDimFactor = fullDimFactor,
        mediumDimFactor = mediumDimFactor,
        slopeFlatThreshold = slopeFlatThreshold,
        slopeSteepThreshold = slopeSteepThreshold,
        wakeupDurationSec = wakeupDurationSec,
        downwardDelaySec = downwardDelaySec,
        gpsAccuracyThresholdMeters = gpsAccuracyThresholdMeters,
        altitudeFilterWindowSec = altitudeFilterWindowSec,
        slopeMinSpeedMps = slopeMinSpeedMps,
        paceCeilingMinKm = paceCeilingMinKm
    )
}

/**
 * DataStore repository managing expert tuning preferences with strict parameter validation and atomic factory reset.
 * Conforms to REQ-SET-073.
 */
class TuningPreferencesDataStore(private val context: Context) {

    companion object {
        val KEY_ELEVATION_X_AXIS_DOMAIN: Preferences.Key<String> = stringPreferencesKey("tuning_elevation_x_axis_domain")
        val KEY_TELEMETRY_X_AXIS_DOMAIN: Preferences.Key<String> = stringPreferencesKey("tuning_telemetry_x_axis_domain")
        @Deprecated("Use KEY_ELEVATION_X_AXIS_DOMAIN or KEY_TELEMETRY_X_AXIS_DOMAIN")
        val KEY_PROFILE_X_AXIS_DOMAIN: Preferences.Key<String> = stringPreferencesKey("tuning_profile_x_axis_domain")
        val KEY_COCKPIT_FONT_FAMILY: Preferences.Key<String> = stringPreferencesKey("tuning_cockpit_font_family")
        val KEY_COCKPIT_FONT_WEIGHT: Preferences.Key<String> = stringPreferencesKey("tuning_cockpit_font_weight")
        val KEY_SENSOR_FIELD_VARIANT: Preferences.Key<String> = stringPreferencesKey("tuning_sensor_field_variant")
        val KEY_FULL_DIM_FACTOR: Preferences.Key<Float> = floatPreferencesKey("tuning_battery_saver_full_dim")
        val KEY_MEDIUM_DIM_FACTOR: Preferences.Key<Float> = floatPreferencesKey("tuning_battery_saver_medium_dim")
        val KEY_SLOPE_FLAT: Preferences.Key<Float> = floatPreferencesKey("tuning_battery_saver_slope_flat")
        val KEY_SLOPE_STEEP: Preferences.Key<Float> = floatPreferencesKey("tuning_battery_saver_slope_steep")
        val KEY_WAKEUP_DURATION_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_battery_saver_wakeup_sec")
        val KEY_DOWNWARD_DELAY_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_battery_saver_downward_delay_sec")
        val KEY_GPS_ACCURACY_THRESHOLD: Preferences.Key<Float> = floatPreferencesKey("tuning_gps_accuracy_threshold")
        val KEY_ALTITUDE_FILTER_WINDOW: Preferences.Key<Int> = intPreferencesKey("tuning_altitude_filter_window")
        val KEY_SLOPE_MIN_SPEED: Preferences.Key<Float> = floatPreferencesKey("tuning_slope_min_speed")
        val KEY_PACE_CEILING_MIN_KM: Preferences.Key<Float> = floatPreferencesKey("tuning_pace_ceiling_min_km")
        val KEY_SHOW_LIVE_CLIMBS: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_show_live_climbs")
        val KEY_CLIMB_MIN_LENGTH_METERS: Preferences.Key<Float> = floatPreferencesKey("tuning_climb_min_length_meters")
        val KEY_CLIMB_MIN_GRADIENT_PERCENT: Preferences.Key<Float> = floatPreferencesKey("tuning_climb_min_gradient_percent")
        val KEY_TURN_PROMPTS_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_turn_prompts_enabled")
        val KEY_TURN_AUDIO_ALERTS_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_turn_audio_alerts_enabled")
        val KEY_TURN_CUE_COUNTDOWN_DISTANCE: Preferences.Key<Float> = floatPreferencesKey("tuning_turn_cue_countdown_distance")
        val KEY_OFF_ROUTE_CORRIDOR_THRESHOLD: Preferences.Key<Float> = floatPreferencesKey("tuning_off_route_corridor_threshold")

        val KEY_SENSOR_FIELD_CORNER_RADIUS: Preferences.Key<Float> = floatPreferencesKey("tuning_sensor_field_corner_radius")
        val KEY_SENSOR_FIELD_BORDER_THICKNESS: Preferences.Key<Float> = floatPreferencesKey("tuning_sensor_field_border_thickness")
        val KEY_SENSOR_FIELD_BORDER_CONTRAST: Preferences.Key<Float> = floatPreferencesKey("tuning_sensor_field_border_contrast")
        val KEY_ROUTE_SELECTION_RADIUS_KM: Preferences.Key<Float> = floatPreferencesKey("tuning_route_selection_radius_km")

        internal val ALL_KEYS = listOf(
            KEY_ELEVATION_X_AXIS_DOMAIN,
            KEY_TELEMETRY_X_AXIS_DOMAIN,
            KEY_PROFILE_X_AXIS_DOMAIN,
            KEY_COCKPIT_FONT_FAMILY,
            KEY_COCKPIT_FONT_WEIGHT,
            KEY_SENSOR_FIELD_VARIANT,
            KEY_FULL_DIM_FACTOR,
            KEY_MEDIUM_DIM_FACTOR,
            KEY_SLOPE_FLAT,
            KEY_SLOPE_STEEP,
            KEY_WAKEUP_DURATION_SEC,
            KEY_DOWNWARD_DELAY_SEC,
            KEY_GPS_ACCURACY_THRESHOLD,
            KEY_ALTITUDE_FILTER_WINDOW,
            KEY_SLOPE_MIN_SPEED,
            KEY_PACE_CEILING_MIN_KM,
            KEY_SHOW_LIVE_CLIMBS,
            KEY_CLIMB_MIN_LENGTH_METERS,
            KEY_CLIMB_MIN_GRADIENT_PERCENT,
            KEY_TURN_PROMPTS_ENABLED,
            KEY_TURN_AUDIO_ALERTS_ENABLED,
            KEY_TURN_CUE_COUNTDOWN_DISTANCE,
            KEY_OFF_ROUTE_CORRIDOR_THRESHOLD,
            KEY_SENSOR_FIELD_CORNER_RADIUS,
            KEY_SENSOR_FIELD_BORDER_THICKNESS,
            KEY_SENSOR_FIELD_BORDER_CONTRAST,
            KEY_ROUTE_SELECTION_RADIUS_KM
        )
    }

    val tuningConfigFlow: Flow<TuningConfig> = context.dataStore.data.map { prefs ->
        val rawElevationDomainStr = prefs[KEY_ELEVATION_X_AXIS_DOMAIN] ?: prefs[KEY_PROFILE_X_AXIS_DOMAIN]
        val elevationDomain = try {
            if (rawElevationDomainStr != null) ProfileXAxisDomain.valueOf(rawElevationDomainStr) else TuningPreferencesDefaults.ELEVATION_X_AXIS_DOMAIN
        } catch (e: Exception) {
            TuningPreferencesDefaults.ELEVATION_X_AXIS_DOMAIN
        }

        val rawTelemetryDomainStr = prefs[KEY_TELEMETRY_X_AXIS_DOMAIN]
        val telemetryDomain = try {
            if (rawTelemetryDomainStr != null) ProfileXAxisDomain.valueOf(rawTelemetryDomainStr) else TuningPreferencesDefaults.TELEMETRY_X_AXIS_DOMAIN
        } catch (e: Exception) {
            TuningPreferencesDefaults.TELEMETRY_X_AXIS_DOMAIN
        }

        val rawFontFamilyStr = prefs[KEY_COCKPIT_FONT_FAMILY]
        val cockpitFontFamily = try {
            if (rawFontFamilyStr != null) CockpitFontFamily.valueOf(rawFontFamilyStr) else TuningPreferencesDefaults.COCKPIT_FONT_FAMILY
        } catch (e: Exception) {
            TuningPreferencesDefaults.COCKPIT_FONT_FAMILY
        }

        val rawVariantStr = prefs[KEY_SENSOR_FIELD_VARIANT]
        val sensorFieldVariant = SensorFieldVariant.fromStorage(rawVariantStr)

        val rawFontWeightStr = prefs[KEY_COCKPIT_FONT_WEIGHT]
        val cockpitFontWeight = try {
            if (rawFontWeightStr != null) CockpitFontWeight.valueOf(rawFontWeightStr) else TuningPreferencesDefaults.COCKPIT_FONT_WEIGHT
        } catch (e: Exception) {
            TuningPreferencesDefaults.COCKPIT_FONT_WEIGHT
        }
        val rawFullDim = prefs[KEY_FULL_DIM_FACTOR] ?: TuningPreferencesDefaults.FULL_DIM_FACTOR
        val rawMediumDim = prefs[KEY_MEDIUM_DIM_FACTOR] ?: TuningPreferencesDefaults.MEDIUM_DIM_FACTOR
        val clampedFullDim = rawFullDim.coerceIn(
            TuningPreferencesDefaults.MIN_FULL_DIM_FACTOR,
            TuningPreferencesDefaults.MAX_FULL_DIM_FACTOR
        )
        val clampedMediumDim = rawMediumDim.coerceIn(
            clampedFullDim,
            TuningPreferencesDefaults.MAX_MEDIUM_DIM_FACTOR
        )

        val rawSlopeFlat = prefs[KEY_SLOPE_FLAT] ?: TuningPreferencesDefaults.SLOPE_FLAT_THRESHOLD
        val rawSlopeSteep = prefs[KEY_SLOPE_STEEP] ?: TuningPreferencesDefaults.SLOPE_STEEP_THRESHOLD
        val clampedSlopeFlat = rawSlopeFlat.coerceIn(
            TuningPreferencesDefaults.MIN_SLOPE_FLAT,
            TuningPreferencesDefaults.MAX_SLOPE_FLAT
        )
        val clampedSlopeSteep = rawSlopeSteep.coerceIn(
            clampedSlopeFlat,
            TuningPreferencesDefaults.MAX_SLOPE_STEEP
        )

        val rawWakeup = prefs[KEY_WAKEUP_DURATION_SEC] ?: TuningPreferencesDefaults.WAKEUP_DURATION_SEC
        val clampedWakeup = rawWakeup.coerceIn(
            TuningPreferencesDefaults.MIN_WAKEUP_SEC,
            TuningPreferencesDefaults.MAX_WAKEUP_SEC
        )

        val rawDownward = prefs[KEY_DOWNWARD_DELAY_SEC] ?: TuningPreferencesDefaults.DOWNWARD_DELAY_SEC
        val clampedDownward = rawDownward.coerceIn(
            TuningPreferencesDefaults.MIN_DOWNWARD_DELAY_SEC,
            TuningPreferencesDefaults.MAX_DOWNWARD_DELAY_SEC
        )

        val rawAccuracy = prefs[KEY_GPS_ACCURACY_THRESHOLD] ?: TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M
        val clampedAccuracy = rawAccuracy.coerceIn(
            TuningPreferencesDefaults.MIN_GPS_ACCURACY_M,
            TuningPreferencesDefaults.MAX_GPS_ACCURACY_M
        )

        val rawAltWindow = prefs[KEY_ALTITUDE_FILTER_WINDOW] ?: TuningPreferencesDefaults.ALTITUDE_FILTER_WINDOW_SEC
        val clampedAltWindow = rawAltWindow.coerceIn(
            TuningPreferencesDefaults.MIN_ALTITUDE_WINDOW_SEC,
            TuningPreferencesDefaults.MAX_ALTITUDE_WINDOW_SEC
        )

        val rawSlopeSpeed = prefs[KEY_SLOPE_MIN_SPEED] ?: TuningPreferencesDefaults.SLOPE_MIN_SPEED_MPS
        val clampedSlopeSpeed = rawSlopeSpeed.coerceIn(
            TuningPreferencesDefaults.MIN_SLOPE_SPEED_MPS,
            TuningPreferencesDefaults.MAX_SLOPE_SPEED_MPS
        )

        val rawPaceCeiling = prefs[KEY_PACE_CEILING_MIN_KM] ?: TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM
        val clampedPaceCeiling = rawPaceCeiling.coerceIn(
            TuningPreferencesDefaults.MIN_PACE_CEILING_MIN_KM,
            TuningPreferencesDefaults.MAX_PACE_CEILING_MIN_KM
        )

        val showLiveClimbs = prefs[KEY_SHOW_LIVE_CLIMBS] ?: TuningPreferencesDefaults.SHOW_LIVE_CLIMBS
        val climbMinLength = prefs[KEY_CLIMB_MIN_LENGTH_METERS] ?: TuningPreferencesDefaults.CLIMB_MIN_LENGTH_METERS
        val climbMinGradient = prefs[KEY_CLIMB_MIN_GRADIENT_PERCENT] ?: TuningPreferencesDefaults.CLIMB_MIN_GRADIENT_PERCENT

        val turnPromptsEnabled = prefs[KEY_TURN_PROMPTS_ENABLED] ?: TuningPreferencesDefaults.TURN_PROMPTS_ENABLED
        val turnAudioAlertsEnabled = prefs[KEY_TURN_AUDIO_ALERTS_ENABLED] ?: TuningPreferencesDefaults.TURN_AUDIO_ALERTS_ENABLED
        val rawTurnCountdown = prefs[KEY_TURN_CUE_COUNTDOWN_DISTANCE] ?: TuningPreferencesDefaults.TURN_CUE_COUNTDOWN_DISTANCE_METERS
        val clampedTurnCountdown = rawTurnCountdown.coerceIn(
            TuningPreferencesDefaults.MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS,
            TuningPreferencesDefaults.MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS
        )
        val rawOffRoute = prefs[KEY_OFF_ROUTE_CORRIDOR_THRESHOLD] ?: TuningPreferencesDefaults.OFF_ROUTE_CORRIDOR_THRESHOLD_METERS
        val clampedOffRoute = rawOffRoute.coerceIn(
            TuningPreferencesDefaults.MIN_OFF_ROUTE_CORRIDOR_THRESHOLD_METERS,
            TuningPreferencesDefaults.MAX_OFF_ROUTE_CORRIDOR_THRESHOLD_METERS
        )

        val rawCornerRadius = prefs[KEY_SENSOR_FIELD_CORNER_RADIUS] ?: TuningPreferencesDefaults.SENSOR_FIELD_CORNER_RADIUS
        val clampedCornerRadius = rawCornerRadius.coerceIn(
            TuningPreferencesDefaults.MIN_SENSOR_FIELD_CORNER_RADIUS,
            TuningPreferencesDefaults.MAX_SENSOR_FIELD_CORNER_RADIUS
        )
        val rawBorderThickness = prefs[KEY_SENSOR_FIELD_BORDER_THICKNESS] ?: TuningPreferencesDefaults.SENSOR_FIELD_BORDER_THICKNESS
        val clampedBorderThickness = rawBorderThickness.coerceIn(
            TuningPreferencesDefaults.MIN_SENSOR_FIELD_BORDER_THICKNESS,
            TuningPreferencesDefaults.MAX_SENSOR_FIELD_BORDER_THICKNESS
        )
        val rawBorderContrast = prefs[KEY_SENSOR_FIELD_BORDER_CONTRAST] ?: TuningPreferencesDefaults.SENSOR_FIELD_BORDER_CONTRAST
        val clampedBorderContrast = rawBorderContrast.coerceIn(
            TuningPreferencesDefaults.MIN_SENSOR_FIELD_BORDER_CONTRAST,
            TuningPreferencesDefaults.MAX_SENSOR_FIELD_BORDER_CONTRAST
        )

        val rawRadiusKm = prefs[KEY_ROUTE_SELECTION_RADIUS_KM] ?: TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM
        val clampedRadiusKm = rawRadiusKm.coerceIn(
            TuningPreferencesDefaults.MIN_ROUTE_SELECTION_RADIUS_KM,
            TuningPreferencesDefaults.MAX_ROUTE_SELECTION_RADIUS_KM
        )

        TuningConfig(
            elevationXAxisDomain = elevationDomain,
            telemetryXAxisDomain = telemetryDomain,
            cockpitFontFamily = cockpitFontFamily,
            cockpitFontWeight = cockpitFontWeight,
            sensorFieldVariant = sensorFieldVariant,
            fullDimFactor = clampedFullDim,
            mediumDimFactor = clampedMediumDim,
            slopeFlatThreshold = clampedSlopeFlat,
            slopeSteepThreshold = clampedSlopeSteep,
            wakeupDurationSec = clampedWakeup,
            downwardDelaySec = clampedDownward,
            gpsAccuracyThresholdMeters = clampedAccuracy,
            altitudeFilterWindowSec = clampedAltWindow,
            slopeMinSpeedMps = clampedSlopeSpeed,
            paceCeilingMinKm = clampedPaceCeiling,
            showLiveClimbs = showLiveClimbs,
            climbMinLengthMeters = climbMinLength,
            climbMinGradientPercent = climbMinGradient,
            turnPromptsEnabled = turnPromptsEnabled,
            turnAudioAlertsEnabled = turnAudioAlertsEnabled,
            turnCueCountdownDistanceMeters = clampedTurnCountdown,
            offRouteCorridorThresholdMeters = clampedOffRoute,
            sensorFieldCornerRadius = clampedCornerRadius,
            sensorFieldBorderThickness = clampedBorderThickness,
            sensorFieldBorderContrast = clampedBorderContrast,
            routeSelectionRadiusKm = clampedRadiusKm
        )
    }

    suspend fun saveTuningConfig(config: TuningConfig) {
        val clampedFullDim = config.fullDimFactor.coerceIn(
            TuningPreferencesDefaults.MIN_FULL_DIM_FACTOR,
            TuningPreferencesDefaults.MAX_FULL_DIM_FACTOR
        )
        val clampedMediumDim = config.mediumDimFactor.coerceIn(
            clampedFullDim,
            TuningPreferencesDefaults.MAX_MEDIUM_DIM_FACTOR
        )
        val clampedSlopeFlat = config.slopeFlatThreshold.coerceIn(
            TuningPreferencesDefaults.MIN_SLOPE_FLAT,
            TuningPreferencesDefaults.MAX_SLOPE_FLAT
        )
        val clampedSlopeSteep = config.slopeSteepThreshold.coerceIn(
            clampedSlopeFlat,
            TuningPreferencesDefaults.MAX_SLOPE_STEEP
        )
        val clampedWakeup = config.wakeupDurationSec.coerceIn(
            TuningPreferencesDefaults.MIN_WAKEUP_SEC,
            TuningPreferencesDefaults.MAX_WAKEUP_SEC
        )
        val clampedDownward = config.downwardDelaySec.coerceIn(
            TuningPreferencesDefaults.MIN_DOWNWARD_DELAY_SEC,
            TuningPreferencesDefaults.MAX_DOWNWARD_DELAY_SEC
        )
        val clampedAccuracy = config.gpsAccuracyThresholdMeters.coerceIn(
            TuningPreferencesDefaults.MIN_GPS_ACCURACY_M,
            TuningPreferencesDefaults.MAX_GPS_ACCURACY_M
        )
        val clampedAltWindow = config.altitudeFilterWindowSec.coerceIn(
            TuningPreferencesDefaults.MIN_ALTITUDE_WINDOW_SEC,
            TuningPreferencesDefaults.MAX_ALTITUDE_WINDOW_SEC
        )
        val clampedSlopeSpeed = config.slopeMinSpeedMps.coerceIn(
            TuningPreferencesDefaults.MIN_SLOPE_SPEED_MPS,
            TuningPreferencesDefaults.MAX_SLOPE_SPEED_MPS
        )
        val clampedPaceCeiling = config.paceCeilingMinKm.coerceIn(
            TuningPreferencesDefaults.MIN_PACE_CEILING_MIN_KM,
            TuningPreferencesDefaults.MAX_PACE_CEILING_MIN_KM
        )
        val clampedCornerRadius = config.sensorFieldCornerRadius.coerceIn(
            TuningPreferencesDefaults.MIN_SENSOR_FIELD_CORNER_RADIUS,
            TuningPreferencesDefaults.MAX_SENSOR_FIELD_CORNER_RADIUS
        )
        val clampedBorderThickness = config.sensorFieldBorderThickness.coerceIn(
            TuningPreferencesDefaults.MIN_SENSOR_FIELD_BORDER_THICKNESS,
            TuningPreferencesDefaults.MAX_SENSOR_FIELD_BORDER_THICKNESS
        )
        val clampedBorderContrast = config.sensorFieldBorderContrast.coerceIn(
            TuningPreferencesDefaults.MIN_SENSOR_FIELD_BORDER_CONTRAST,
            TuningPreferencesDefaults.MAX_SENSOR_FIELD_BORDER_CONTRAST
        )
        val clampedRadiusKm = config.routeSelectionRadiusKm.coerceIn(
            TuningPreferencesDefaults.MIN_ROUTE_SELECTION_RADIUS_KM,
            TuningPreferencesDefaults.MAX_ROUTE_SELECTION_RADIUS_KM
        )

        context.dataStore.edit { prefs ->
            prefs[KEY_ELEVATION_X_AXIS_DOMAIN] = config.elevationXAxisDomain.name
            prefs[KEY_TELEMETRY_X_AXIS_DOMAIN] = config.telemetryXAxisDomain.name
            prefs[KEY_PROFILE_X_AXIS_DOMAIN] = config.elevationXAxisDomain.name
            prefs[KEY_COCKPIT_FONT_FAMILY] = config.cockpitFontFamily.name
            prefs[KEY_COCKPIT_FONT_WEIGHT] = config.cockpitFontWeight.name
            prefs[KEY_SENSOR_FIELD_VARIANT] = config.sensorFieldVariant.name
            prefs[KEY_FULL_DIM_FACTOR] = clampedFullDim
            prefs[KEY_MEDIUM_DIM_FACTOR] = clampedMediumDim
            prefs[KEY_SLOPE_FLAT] = clampedSlopeFlat
            prefs[KEY_SLOPE_STEEP] = clampedSlopeSteep
            prefs[KEY_WAKEUP_DURATION_SEC] = clampedWakeup
            prefs[KEY_DOWNWARD_DELAY_SEC] = clampedDownward
            prefs[KEY_GPS_ACCURACY_THRESHOLD] = clampedAccuracy
            prefs[KEY_ALTITUDE_FILTER_WINDOW] = clampedAltWindow
            prefs[KEY_SLOPE_MIN_SPEED] = clampedSlopeSpeed
            prefs[KEY_PACE_CEILING_MIN_KM] = clampedPaceCeiling
            prefs[KEY_SHOW_LIVE_CLIMBS] = config.showLiveClimbs
            prefs[KEY_CLIMB_MIN_LENGTH_METERS] = config.climbMinLengthMeters
            prefs[KEY_CLIMB_MIN_GRADIENT_PERCENT] = config.climbMinGradientPercent
            prefs[KEY_TURN_PROMPTS_ENABLED] = config.turnPromptsEnabled
            prefs[KEY_TURN_AUDIO_ALERTS_ENABLED] = config.turnAudioAlertsEnabled
            prefs[KEY_TURN_CUE_COUNTDOWN_DISTANCE] = config.turnCueCountdownDistanceMeters.coerceIn(
                TuningPreferencesDefaults.MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS,
                TuningPreferencesDefaults.MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS
            )
            prefs[KEY_OFF_ROUTE_CORRIDOR_THRESHOLD] = config.offRouteCorridorThresholdMeters.coerceIn(
                TuningPreferencesDefaults.MIN_OFF_ROUTE_CORRIDOR_THRESHOLD_METERS,
                TuningPreferencesDefaults.MAX_OFF_ROUTE_CORRIDOR_THRESHOLD_METERS
            )
            prefs[KEY_SENSOR_FIELD_CORNER_RADIUS] = clampedCornerRadius
            prefs[KEY_SENSOR_FIELD_BORDER_THICKNESS] = clampedBorderThickness
            prefs[KEY_SENSOR_FIELD_BORDER_CONTRAST] = clampedBorderContrast
            prefs[KEY_ROUTE_SELECTION_RADIUS_KM] = clampedRadiusKm
        }
    }


    suspend fun updateShowLiveClimbs(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOW_LIVE_CLIMBS] = show
        }
    }

    suspend fun updateSensorFieldVariant(variant: SensorFieldVariant) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SENSOR_FIELD_VARIANT] = variant.name
        }
    }

    suspend fun updateRouteSelectionRadiusKm(radiusKm: Float) {
        val clamped = radiusKm.coerceIn(
            TuningPreferencesDefaults.MIN_ROUTE_SELECTION_RADIUS_KM,
            TuningPreferencesDefaults.MAX_ROUTE_SELECTION_RADIUS_KM
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_ROUTE_SELECTION_RADIUS_KM] = clamped
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { prefs ->
            for (key in ALL_KEYS) {
                prefs.remove(key)
            }
        }
    }
}
