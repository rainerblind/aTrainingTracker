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
import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldVariant
import com.atrainingtracker.util.safeEnumValueOf

/**
 * Selectable horizontal X-axis domain for Aftermath elevation profile and scrubbing.
 * (REQ-UI-201 / ATT-1391)
 */
enum class ProfileXAxisDomain {
    DISTANCE,
    TIME
}

/**
 * Radar edge overlay positioning on screen (REQ-TRK-062.5).
 */
enum class RadarEdgeSide {
    LEFT,
    RIGHT
}

/**
 * Radar clear-state behavior when no active threats are detected (REQ-TRK-062.5).
 */
enum class RadarClearMode {
    SLIM_RAIL,
    BADGE_ONLY,
    AUTO_HIDE_3S
}

/**
 * Radar audio alert mode filtering threat level transitions (REQ-TRK-063.6).
 */
enum class RadarAudioAlertMode {
    ALL_ALERTS,
    APPROACH_AND_FAST,
    FAST_ONLY,
    MUTED
}

/**
 * Radar audio output route preference (REQ-TRK-063.6).
 */
enum class RadarAudioRoute {
    SYSTEM_DEFAULT,
    FORCE_SPEAKER
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
    val RADAR_EDGE_SIDE = RadarEdgeSide.RIGHT
    val RADAR_CLEAR_MODE = RadarClearMode.SLIM_RAIL
    val RADAR_AUDIO_ALERT_MODE = RadarAudioAlertMode.ALL_ALERTS
    const val RADAR_ALERT_VOLUME = 90
    const val RADAR_SPEAKER_BOOST = true
    val RADAR_AUDIO_ROUTE = RadarAudioRoute.SYSTEM_DEFAULT
    const val RADAR_HAPTIC_ALERT = true
    val RADAR_LIGHT_DEFAULT_MODE = RadarLightMode.DAY_FLASH
    const val RADAR_AUTO_LIGHT_CONTROL = true
    const val RADAR_PELOTON_AUDIO_SYNC = true
    const val RADAR_SMART_URBAN_MUTE = false
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

    @Deprecated("Navigation cue overlays are permanently solid (1.0f) per REQ-UI-287 / ATT-3322")
    const val DEFAULT_NAVIGATION_CUE_TRANSPARENCY = 1.00f
    @Deprecated("Navigation cue overlays are permanently solid (1.0f) per REQ-UI-287 / ATT-3322")
    const val MIN_NAVIGATION_CUE_TRANSPARENCY = 0.20f
    @Deprecated("Navigation cue overlays are permanently solid (1.0f) per REQ-UI-287 / ATT-3322")
    const val MAX_NAVIGATION_CUE_TRANSPARENCY = 1.00f
    @Deprecated("Navigation cue overlays are permanently solid (1.0f) per REQ-UI-287 / ATT-3322")
    const val STEP_NAVIGATION_CUE_TRANSPARENCY = 0.05f

    const val DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC = 4
    const val MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC = 1
    const val MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC = 10
    const val STEP_NAVIGATION_CUE_DISMISS_DURATION_SEC = 1
    val NAVIGATION_CUE_DISMISS_OPTIONS = (MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC..MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC).toList()

    const val DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS = 21.0f
    const val MIN_ELEVATION_SMOOTHING_SIGMA_METERS = 10.0f
    const val MAX_ELEVATION_SMOOTHING_SIGMA_METERS = 50.0f
    const val STEP_ELEVATION_SMOOTHING_SIGMA_METERS = 1.0f

    const val DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM = 20.0f
    const val MIN_MAP_FOLLOW_ME_INITIAL_ZOOM = 15.0f
    const val MAX_MAP_FOLLOW_ME_INITIAL_ZOOM = 21.0f
    const val STEP_MAP_FOLLOW_ME_INITIAL_ZOOM = 0.5f

    const val DEFAULT_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED = true

    const val DEFAULT_MAP_FOLLOW_ME_CRUISING_ZOOM = 18.0f
    const val DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM = 16.0f
    const val MIN_MAP_FOLLOW_ME_CRUISING_ZOOM = 14.0f
    const val MAX_MAP_FOLLOW_ME_CRUISING_ZOOM = 19.5f
    const val STEP_MAP_FOLLOW_ME_CRUISING_ZOOM = 0.5f

    const val DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE = 70.0f
    const val MIN_MAP_FOLLOW_ME_TILT_ANGLE = 0.0f
    const val MAX_MAP_FOLLOW_ME_TILT_ANGLE = 70.0f
    const val STEP_MAP_FOLLOW_ME_TILT_ANGLE = 5.0f

    const val DEFAULT_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT = 30.0f
    const val MIN_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT = 10.0f
    const val MAX_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT = 50.0f
    const val STEP_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT = 5.0f

    const val DEFAULT_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA = 0.10f
    const val MIN_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA = 0.02f
    const val MAX_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA = 0.50f
    const val STEP_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA = 0.02f

    const val DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA = 0.15f
    const val MIN_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA = 0.05f
    const val MAX_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA = 0.50f
    const val STEP_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA = 0.05f

    const val SUPPRESS_ANT_MISSING_ADAPTER_DIALOG = false
    const val DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED = true

    const val CLUSTER_TOL_ENDPOINTS = 200.0f
    const val MIN_CLUSTER_TOL_ENDPOINTS = 10.0f
    const val MAX_CLUSTER_TOL_ENDPOINTS = 500.0f

    const val CLUSTER_TOL_APEX = 400.0f
    const val MIN_CLUSTER_TOL_APEX = 10.0f
    const val MAX_CLUSTER_TOL_APEX = 500.0f

    const val CLUSTER_TOL_DISTANCE = 0.20f
    const val MIN_CLUSTER_TOL_DISTANCE = 0.01f
    const val MAX_CLUSTER_TOL_DISTANCE = 0.20f

    const val CLUSTER_TOL_ALTITUDE_POS = 400.0f
    const val MIN_CLUSTER_TOL_ALTITUDE_POS = 10.0f
    const val MAX_CLUSTER_TOL_ALTITUDE_POS = 500.0f

    const val CLUSTER_USE_SPORT_TYPE = true
    const val CLUSTER_USE_ALTITUDE_POS = true

    const val CLUSTER_SCORE_THRESHOLD = 1.2f
    const val MIN_CLUSTER_SCORE_THRESHOLD = 0.5f
    const val MAX_CLUSTER_SCORE_THRESHOLD = 2.5f
    const val STEP_CLUSTER_SCORE_THRESHOLD = 0.1f

    /** First-time stop tracking coachmark banner acknowledgement default (REQ-UI-367 / ATT-3387). */
    const val DEFAULT_HAS_SEEN_STOP_TRACKING_HINT = false

    /** First-install permission onboarding flow completion default (REQ-PRI-007 / ATT-4455). */
    const val DEFAULT_HAS_COMPLETED_PERMISSION_ONBOARDING = false

    const val DEFAULT_AUTO_PAUSE_BIKE_ENABLED = true
    const val DEFAULT_AUTO_PAUSE_RUN_ENABLED = false
    const val DEFAULT_AUTO_PAUSE_OTHER_ENABLED = false
    const val DEFAULT_START_IN_AUTO_PAUSE_ENABLED = false

    const val MIN_AUTO_PAUSE_SPEED_THRESHOLD = 1
    const val MAX_AUTO_PAUSE_SPEED_THRESHOLD = 5
    const val STEP_AUTO_PAUSE_SPEED_THRESHOLD = 1

    const val DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_METRIC = 3
    const val DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_IMPERIAL = 2
    const val DEFAULT_AUTO_PAUSE_BIKE_DEBOUNCE_SEC = 2
    const val MIN_AUTO_PAUSE_BIKE_DEBOUNCE_SEC = 1
    const val MAX_AUTO_PAUSE_BIKE_DEBOUNCE_SEC = 20
    const val DEFAULT_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC = 2
    const val MIN_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC = 1
    const val MAX_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC = 20

    const val DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_METRIC = 2
    const val DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_IMPERIAL = 1
    const val DEFAULT_AUTO_PAUSE_RUN_DEBOUNCE_SEC = 5
    const val MIN_AUTO_PAUSE_RUN_DEBOUNCE_SEC = 1
    const val MAX_AUTO_PAUSE_RUN_DEBOUNCE_SEC = 20
    const val DEFAULT_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC = 5
    const val MIN_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC = 1
    const val MAX_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC = 20

    const val DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_METRIC = 1
    const val DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_IMPERIAL = 1
    const val DEFAULT_AUTO_PAUSE_OTHER_DEBOUNCE_SEC = 10
    const val MIN_AUTO_PAUSE_OTHER_DEBOUNCE_SEC = 1
    const val MAX_AUTO_PAUSE_OTHER_DEBOUNCE_SEC = 60
    const val DEFAULT_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC = 10
    const val MIN_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC = 1
    const val MAX_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC = 60

    // Legacy compatibility constants
    const val DEFAULT_AUTO_PAUSE_SPEED_THRESHOLD_KMH = 1.5f
    const val MIN_AUTO_PAUSE_SPEED_THRESHOLD_KMH = 0.5f
    const val MAX_AUTO_PAUSE_SPEED_THRESHOLD_KMH = 5.0f
    const val STEP_AUTO_PAUSE_SPEED_THRESHOLD_KMH = 0.1f
    const val DEFAULT_AUTO_PAUSE_DEBOUNCE_SEC = 2
    const val MIN_AUTO_PAUSE_DEBOUNCE_SEC = 1
    const val MAX_AUTO_PAUSE_DEBOUNCE_SEC = 5
    const val STEP_AUTO_PAUSE_DEBOUNCE_SEC = 1

    const val DEFAULT_ENABLE_COGGAN_POWER_METRICS = true
    const val DEFAULT_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY = true
    const val DEFAULT_ENABLE_PMC_MODEL = false
    const val DEFAULT_ENABLE_AEROBIC_EFFICIENCY_MODEL = false
    const val DEFAULT_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY = true
    const val DEFAULT_ENABLE_CRITICAL_POWER_MODEL = false
    const val DEFAULT_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY = true
    const val DEFAULT_ENABLE_CARDIO_POWER_DYNAMICS = false
    const val DEFAULT_SHOW_CARDIO_POWER_IN_WORKOUT_SUMMARY = true

    const val DEFAULT_DFA_A1_THRESHOLD_T1 = 0.75f
    const val DEFAULT_DFA_A1_THRESHOLD_T2 = 0.50f
    const val MIN_DFA_A1_THRESHOLD = 0.20f
    const val MAX_DFA_A1_THRESHOLD = 1.50f
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
    val routeSelectionRadiusKm: Float = TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM,
    @Deprecated("Navigation cue overlays are permanently solid (1.0f) per REQ-UI-287 / ATT-3322")
    val navigationCueTransparency: Float = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY,
    val navigationCueDismissDurationSec: Int = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC,
    val elevationSmoothingSigmaMeters: Float = TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS,
    val mapFollowMeInitialZoom: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM,
    val mapFollowMeSpeedZoomEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED,
    val mapFollowMeCruisingZoom: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_CRUISING_ZOOM,
    val mapFollowMeTiltAngle: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE,
    val mapFollowMeLookaheadPaddingPercent: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT,
    val mapFollowMeSpeedSmoothingAlpha: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA,
    val mapFollowMeBearingSmoothingAlpha: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA,
    val suppressAntMissingAdapterDialog: Boolean = TuningPreferencesDefaults.SUPPRESS_ANT_MISSING_ADAPTER_DIALOG,
    val autoFollowUniqueRouteEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED,
    val clusterTolEndpoints: Float = TuningPreferencesDefaults.CLUSTER_TOL_ENDPOINTS,
    val clusterTolApex: Float = TuningPreferencesDefaults.CLUSTER_TOL_APEX,
    val clusterTolDistance: Float = TuningPreferencesDefaults.CLUSTER_TOL_DISTANCE,
    val clusterTolAltitudePos: Float = TuningPreferencesDefaults.CLUSTER_TOL_ALTITUDE_POS,
    val clusterUseSportType: Boolean = TuningPreferencesDefaults.CLUSTER_USE_SPORT_TYPE,
    val clusterUseAltitudePos: Boolean = TuningPreferencesDefaults.CLUSTER_USE_ALTITUDE_POS,
    val clusterScoreThreshold: Float = TuningPreferencesDefaults.CLUSTER_SCORE_THRESHOLD,
    val autoPauseBikeEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_ENABLED,
    val autoPauseRunEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_ENABLED,
    val autoPauseOtherEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_ENABLED,
    val startInAutoPauseEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_START_IN_AUTO_PAUSE_ENABLED,
    val autoPauseBikeSpeedThreshold: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_METRIC,
    val autoPauseBikeDebounceSec: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_DEBOUNCE_SEC,
    val autoPauseBikeResumeDebounceSec: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC,
    val autoPauseRunSpeedThreshold: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_METRIC,
    val autoPauseRunDebounceSec: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_DEBOUNCE_SEC,
    val autoPauseRunResumeDebounceSec: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC,
    val autoPauseOtherSpeedThreshold: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_METRIC,
    val autoPauseOtherDebounceSec: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_DEBOUNCE_SEC,
    val autoPauseOtherResumeDebounceSec: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC,
    val autoPauseSpeedThresholdKmh: Float = autoPauseBikeSpeedThreshold.toFloat(),
    val autoPauseDebounceSec: Int = autoPauseBikeDebounceSec,
    val enableCogganPowerMetrics: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_COGGAN_POWER_METRICS,
    val showCogganPowerInWorkoutSummary: Boolean = TuningPreferencesDefaults.DEFAULT_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY,
    val enablePmcModel: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_PMC_MODEL,
    val enableAerobicEfficiencyModel: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_AEROBIC_EFFICIENCY_MODEL,
    val showAerobicEfficiencyInWorkoutSummary: Boolean = TuningPreferencesDefaults.DEFAULT_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY,
    val enableCriticalPowerModel: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_CRITICAL_POWER_MODEL,
    val showCriticalPowerInWorkoutSummary: Boolean = TuningPreferencesDefaults.DEFAULT_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY,
    val enableCardioPowerDynamics: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_CARDIO_POWER_DYNAMICS,
    val showCardioPowerInWorkoutSummary: Boolean = TuningPreferencesDefaults.DEFAULT_SHOW_CARDIO_POWER_IN_WORKOUT_SUMMARY,
    val dfaA1ThresholdT1: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1,
    val dfaA1ThresholdT2: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2,
    val radarEdgeSide: RadarEdgeSide = TuningPreferencesDefaults.RADAR_EDGE_SIDE,
    val radarClearMode: RadarClearMode = TuningPreferencesDefaults.RADAR_CLEAR_MODE,
    val radarAudioAlertMode: RadarAudioAlertMode = TuningPreferencesDefaults.RADAR_AUDIO_ALERT_MODE,
    val radarAlertVolume: Int = TuningPreferencesDefaults.RADAR_ALERT_VOLUME,
    val radarSpeakerBoost: Boolean = TuningPreferencesDefaults.RADAR_SPEAKER_BOOST,
    val radarAudioRoute: RadarAudioRoute = TuningPreferencesDefaults.RADAR_AUDIO_ROUTE,
    val radarHapticAlert: Boolean = TuningPreferencesDefaults.RADAR_HAPTIC_ALERT,
    val radarLightDefaultMode: RadarLightMode = TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE,
    val radarAutoLightControl: Boolean = TuningPreferencesDefaults.RADAR_AUTO_LIGHT_CONTROL,
    val radarPelotonAudioSync: Boolean = TuningPreferencesDefaults.RADAR_PELOTON_AUDIO_SYNC,
    val radarSmartUrbanMute: Boolean = TuningPreferencesDefaults.RADAR_SMART_URBAN_MUTE
) {
    val showAntMissingAdapterWarnings: Boolean
        get() = !suppressAntMissingAdapterDialog

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
        @Deprecated("Navigation cue overlays are permanently solid (1.0f) per REQ-UI-287 / ATT-3322")
        val KEY_NAVIGATION_CUE_TRANSPARENCY: Preferences.Key<Float> = floatPreferencesKey("tuning_navigation_cue_transparency")
        val KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_navigation_cue_dismiss_duration_sec")
        val KEY_ELEVATION_SMOOTHING_SIGMA_METERS: Preferences.Key<Float> = floatPreferencesKey("tuning_elevation_smoothing_sigma_meters")

        @Deprecated("Map camera follow-me zoom/tilt/smoothing are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
        val KEY_MAP_FOLLOW_ME_INITIAL_ZOOM: Preferences.Key<Float> = floatPreferencesKey("tuning_map_follow_me_initial_zoom")
        @Deprecated("Map camera follow-me zoom/tilt/smoothing are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
        val KEY_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_map_follow_me_speed_zoom_enabled")
        @Deprecated("Map camera follow-me zoom/tilt/smoothing are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
        val KEY_MAP_FOLLOW_ME_CRUISING_ZOOM: Preferences.Key<Float> = floatPreferencesKey("tuning_map_follow_me_cruising_zoom")
        @Deprecated("Map camera follow-me zoom/tilt/smoothing are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
        val KEY_MAP_FOLLOW_ME_TILT_ANGLE: Preferences.Key<Float> = floatPreferencesKey("tuning_map_follow_me_tilt_angle")
        @Deprecated("Map camera follow-me zoom/tilt/smoothing are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
        val KEY_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT: Preferences.Key<Float> = floatPreferencesKey("tuning_map_follow_me_lookahead_padding_percent")
        @Deprecated("Map camera follow-me zoom/tilt/smoothing are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
        val KEY_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA: Preferences.Key<Float> = floatPreferencesKey("tuning_map_follow_me_speed_smoothing_alpha")
        @Deprecated("Map camera follow-me zoom/tilt/smoothing are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
        val KEY_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA: Preferences.Key<Float> = floatPreferencesKey("tuning_map_follow_me_bearing_smoothing_alpha")

        /**
         * List of legacy map camera tuning keys retained for migration cleanup and factory reset (REQ-MAP-050).
         */
        val LEGACY_MAP_CAMERA_KEYS = listOf(
            KEY_MAP_FOLLOW_ME_INITIAL_ZOOM,
            KEY_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED,
            KEY_MAP_FOLLOW_ME_CRUISING_ZOOM,
            KEY_MAP_FOLLOW_ME_TILT_ANGLE,
            KEY_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT,
            KEY_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA,
            KEY_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA
        )
        val KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_suppress_ant_missing_adapter_dialog")
        val KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_auto_follow_unique_route_enabled")
        val KEY_CLUSTER_TOL_ENDPOINTS: Preferences.Key<Float> = floatPreferencesKey("tuning_cluster_tol_endpoints")
        val KEY_CLUSTER_TOL_APEX: Preferences.Key<Float> = floatPreferencesKey("tuning_cluster_tol_apex")
        val KEY_CLUSTER_TOL_DISTANCE: Preferences.Key<Float> = floatPreferencesKey("tuning_cluster_tol_distance")
        val KEY_CLUSTER_TOL_ALTITUDE_POS: Preferences.Key<Float> = floatPreferencesKey("tuning_cluster_tol_altitude_pos")
        val KEY_CLUSTER_USE_SPORT_TYPE: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_cluster_use_sport_type")
        val KEY_CLUSTER_USE_ALTITUDE_POS: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_cluster_use_altitude_pos")
        val KEY_CLUSTER_SCORE_THRESHOLD: Preferences.Key<Float> = floatPreferencesKey("tuning_cluster_score_threshold")
        val KEY_HAS_SEEN_STOP_TRACKING_HINT: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_has_seen_stop_tracking_hint")
        val KEY_HAS_COMPLETED_PERMISSION_ONBOARDING: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_has_completed_permission_onboarding")
        val KEY_AUTO_PAUSE_BIKE_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_auto_pause_bike_enabled")
        val KEY_AUTO_PAUSE_RUN_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_auto_pause_run_enabled")
        val KEY_AUTO_PAUSE_OTHER_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_auto_pause_other_enabled")
        val KEY_START_IN_AUTO_PAUSE_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_start_in_auto_pause_enabled")
        val KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_bike_speed_threshold")
        val KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_bike_debounce_sec")
        val KEY_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_bike_resume_debounce_sec")
        val KEY_AUTO_PAUSE_RUN_SPEED_THRESHOLD: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_run_speed_threshold")
        val KEY_AUTO_PAUSE_RUN_DEBOUNCE_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_run_debounce_sec")
        val KEY_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_run_resume_debounce_sec")
        val KEY_AUTO_PAUSE_OTHER_SPEED_THRESHOLD: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_other_speed_threshold")
        val KEY_AUTO_PAUSE_OTHER_DEBOUNCE_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_other_debounce_sec")
        val KEY_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_other_resume_debounce_sec")
        val KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH: Preferences.Key<Float> = floatPreferencesKey("tuning_auto_pause_speed_threshold_kmh")
        val KEY_AUTO_PAUSE_DEBOUNCE_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_auto_pause_debounce_sec")
        val KEY_ENABLE_COGGAN_POWER_METRICS: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_enable_coggan_power_metrics")
        val KEY_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_show_coggan_power_in_workout_summary")
        val KEY_ENABLE_PMC_MODEL: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_enable_pmc_model")
        val KEY_ENABLE_AEROBIC_EFFICIENCY_MODEL: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_enable_aerobic_efficiency_model")
        val KEY_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_show_aerobic_efficiency_in_workout_summary")
        val KEY_ENABLE_CRITICAL_POWER_MODEL: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_enable_critical_power_model")
        val KEY_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_show_critical_power_in_workout_summary")
        val KEY_ENABLE_CARDIO_POWER_DYNAMICS: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_enable_cardio_power_dynamics")
        val KEY_SHOW_CARDIO_POWER_IN_WORKOUT_SUMMARY: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_show_cardio_power_in_workout_summary")
        val KEY_DFA_A1_THRESHOLD_T1: Preferences.Key<Float> = floatPreferencesKey("tuning_dfa_a1_threshold_t1")
        val KEY_DFA_A1_THRESHOLD_T2: Preferences.Key<Float> = floatPreferencesKey("tuning_dfa_a1_threshold_t2")
        val KEY_RADAR_EDGE_SIDE: Preferences.Key<String> = stringPreferencesKey("tuning_radar_edge_side")
        val KEY_RADAR_CLEAR_MODE: Preferences.Key<String> = stringPreferencesKey("tuning_radar_clear_mode")
        val KEY_RADAR_AUDIO_ALERT_MODE: Preferences.Key<String> = stringPreferencesKey("tuning_radar_audio_alert_mode")
        val KEY_RADAR_ALERT_VOLUME: Preferences.Key<Int> = intPreferencesKey("tuning_radar_alert_volume")
        val KEY_RADAR_SPEAKER_BOOST: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_speaker_boost")
        val KEY_RADAR_AUDIO_ROUTE: Preferences.Key<String> = stringPreferencesKey("tuning_radar_audio_route")
        val KEY_RADAR_HAPTIC_ALERT: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_haptic_alert")
        val KEY_RADAR_LIGHT_DEFAULT_MODE: Preferences.Key<String> = stringPreferencesKey("tuning_radar_light_default_mode")
        val KEY_RADAR_AUTO_LIGHT_CONTROL: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_auto_light_control")
        val KEY_RADAR_PELOTON_AUDIO_SYNC: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_peloton_audio_sync")
        val KEY_RADAR_SMART_URBAN_MUTE: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_smart_urban_mute")

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
            KEY_ROUTE_SELECTION_RADIUS_KM,
            KEY_NAVIGATION_CUE_TRANSPARENCY,
            KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC,
            KEY_ELEVATION_SMOOTHING_SIGMA_METERS,
            KEY_MAP_FOLLOW_ME_INITIAL_ZOOM,
            KEY_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED,
            KEY_MAP_FOLLOW_ME_CRUISING_ZOOM,
            KEY_MAP_FOLLOW_ME_TILT_ANGLE,
            KEY_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT,
            KEY_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA,
            KEY_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA,
            KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG,
            KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED,
            KEY_CLUSTER_TOL_ENDPOINTS,
            KEY_CLUSTER_TOL_APEX,
            KEY_CLUSTER_TOL_DISTANCE,
            KEY_CLUSTER_TOL_ALTITUDE_POS,
            KEY_CLUSTER_USE_SPORT_TYPE,
            KEY_CLUSTER_USE_ALTITUDE_POS,
            KEY_CLUSTER_SCORE_THRESHOLD,
            KEY_HAS_SEEN_STOP_TRACKING_HINT,
            KEY_HAS_COMPLETED_PERMISSION_ONBOARDING,
            KEY_AUTO_PAUSE_BIKE_ENABLED,
            KEY_AUTO_PAUSE_RUN_ENABLED,
            KEY_AUTO_PAUSE_OTHER_ENABLED,
            KEY_START_IN_AUTO_PAUSE_ENABLED,
            KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD,
            KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC,
            KEY_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC,
            KEY_AUTO_PAUSE_RUN_SPEED_THRESHOLD,
            KEY_AUTO_PAUSE_RUN_DEBOUNCE_SEC,
            KEY_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC,
            KEY_AUTO_PAUSE_OTHER_SPEED_THRESHOLD,
            KEY_AUTO_PAUSE_OTHER_DEBOUNCE_SEC,
            KEY_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC,
            KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH,
            KEY_AUTO_PAUSE_DEBOUNCE_SEC,
            KEY_ENABLE_COGGAN_POWER_METRICS,
            KEY_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY,
            KEY_ENABLE_PMC_MODEL,
            KEY_ENABLE_AEROBIC_EFFICIENCY_MODEL,
            KEY_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY,
            KEY_ENABLE_CRITICAL_POWER_MODEL,
            KEY_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY,
            KEY_ENABLE_CARDIO_POWER_DYNAMICS,
            KEY_SHOW_CARDIO_POWER_IN_WORKOUT_SUMMARY,
            KEY_DFA_A1_THRESHOLD_T1,
            KEY_DFA_A1_THRESHOLD_T2,
            KEY_RADAR_EDGE_SIDE,
            KEY_RADAR_CLEAR_MODE,
            KEY_RADAR_AUDIO_ALERT_MODE,
            KEY_RADAR_ALERT_VOLUME,
            KEY_RADAR_SPEAKER_BOOST,
            KEY_RADAR_AUDIO_ROUTE,
            KEY_RADAR_HAPTIC_ALERT,
            KEY_RADAR_LIGHT_DEFAULT_MODE,
            KEY_RADAR_AUTO_LIGHT_CONTROL,
            KEY_RADAR_PELOTON_AUDIO_SYNC,
            KEY_RADAR_SMART_URBAN_MUTE
        )

        /**
         * Resolves and maps sport-specific auto-pause preferences with fallback to legacy keys and default values.
         */
        fun mapAutoPausePreferences(prefs: Preferences): AutoPausePreferences {
            val bikeEnabled = prefs[KEY_AUTO_PAUSE_BIKE_ENABLED] ?: TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_ENABLED
            val runEnabled = prefs[KEY_AUTO_PAUSE_RUN_ENABLED] ?: TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_ENABLED
            val otherEnabled = prefs[KEY_AUTO_PAUSE_OTHER_ENABLED] ?: TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_ENABLED
            val startInAutoPauseEnabled = prefs[KEY_START_IN_AUTO_PAUSE_ENABLED] ?: TuningPreferencesDefaults.DEFAULT_START_IN_AUTO_PAUSE_ENABLED

            val legacyThreshold = prefs[KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH]?.roundToInt()
            val legacyDebounce = prefs[KEY_AUTO_PAUSE_DEBOUNCE_SEC]

            val bikeThreshold = (prefs[KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD] ?: legacyThreshold ?: TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_METRIC)
                .coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD, TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD)
            val bikeDebounce = (prefs[KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC] ?: legacyDebounce ?: TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_DEBOUNCE_SEC)
                .coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_BIKE_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_BIKE_DEBOUNCE_SEC)
            val bikeResumeDebounce = (prefs[KEY_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC] ?: TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC)
                .coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC)

            val runThreshold = (prefs[KEY_AUTO_PAUSE_RUN_SPEED_THRESHOLD] ?: legacyThreshold ?: TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_METRIC)
                .coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD, TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD)
            val runDebounce = (prefs[KEY_AUTO_PAUSE_RUN_DEBOUNCE_SEC] ?: legacyDebounce ?: TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_DEBOUNCE_SEC)
                .coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_RUN_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_RUN_DEBOUNCE_SEC)
            val runResumeDebounce = (prefs[KEY_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC] ?: TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC)
                .coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC)

            val otherThreshold = (prefs[KEY_AUTO_PAUSE_OTHER_SPEED_THRESHOLD] ?: legacyThreshold ?: TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_METRIC)
                .coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD, TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD)
            val otherDebounce = (prefs[KEY_AUTO_PAUSE_OTHER_DEBOUNCE_SEC] ?: legacyDebounce ?: TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_DEBOUNCE_SEC)
                .coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_OTHER_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_OTHER_DEBOUNCE_SEC)
            val otherResumeDebounce = (prefs[KEY_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC] ?: TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC)
                .coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC)

            return AutoPausePreferences(
                bikeEnabled = bikeEnabled,
                runEnabled = runEnabled,
                otherEnabled = otherEnabled,
                startInAutoPauseEnabled = startInAutoPauseEnabled,
                bikeSpeedThreshold = bikeThreshold,
                bikeDebounceSec = bikeDebounce,
                bikeResumeDebounceSec = bikeResumeDebounce,
                runSpeedThreshold = runThreshold,
                runDebounceSec = runDebounce,
                runResumeDebounceSec = runResumeDebounce,
                otherSpeedThreshold = otherThreshold,
                otherDebounceSec = otherDebounce,
                otherResumeDebounceSec = otherResumeDebounce
            )
        }

        /**
         * Maps follow-me camera preferences to calibrated defaults, ignoring any legacy stored overrides (ATT-3581 / REQ-MAP-050).
         */
        fun mapFollowMePreferences(prefs: Preferences): FollowMeCameraPreferences {
            return FollowMeCameraPreferences(
                initialZoom = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM,
                speedZoomEnabled = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED,
                cruisingZoom = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_CRUISING_ZOOM,
                tiltAngle = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE,
                lookaheadPaddingPercent = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT,
                speedSmoothingAlpha = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA,
                bearingSmoothingAlpha = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA,
                minZoom = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM
            )
        }

        /**
         * Purges legacy map camera keys from a [MutablePreferences] instance (ATT-3581 / REQ-MAP-050).
         */
        fun purgeLegacyMapCameraKeys(prefs: MutablePreferences) {
            for (key in LEGACY_MAP_CAMERA_KEYS) {
                prefs.remove(key)
            }
        }

        @Volatile
        private var INSTANCE: TuningPreferencesDataStore? = null

        @JvmStatic
        fun getInstance(context: Context): TuningPreferencesDataStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TuningPreferencesDataStore(context.applicationContext ?: context).also { INSTANCE = it }
            }
        }

        @VisibleForTesting
        fun setInstanceForTesting(instance: TuningPreferencesDataStore?) {
            synchronized(this) {
                INSTANCE = instance
            }
        }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val tuningConfigStateFlow: StateFlow<TuningConfig> by lazy {
        tuningConfigFlow.stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = TuningConfig()
        )
    }

    fun getTuningConfig(): TuningConfig = tuningConfigStateFlow.value

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                purgeLegacyMapCameraPreferences()
            } catch (_: Exception) {
                // Best-effort legacy key purge on startup
            }
        }
    }

/**
 * Resolved follow-me camera preferences slice (REQ-MAP-050).
 */
data class FollowMeCameraPreferences(
    val initialZoom: Float,
    val speedZoomEnabled: Boolean,
    val cruisingZoom: Float,
    val tiltAngle: Float,
    val lookaheadPaddingPercent: Float,
    val speedSmoothingAlpha: Float,
    val bearingSmoothingAlpha: Float,
    val minZoom: Float
)

/**
 * Resolved sport-specific auto-pause preferences slice.
 */
data class AutoPausePreferences(
    val bikeEnabled: Boolean,
    val runEnabled: Boolean,
    val otherEnabled: Boolean,
    val startInAutoPauseEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_START_IN_AUTO_PAUSE_ENABLED,
    val bikeSpeedThreshold: Int,
    val bikeDebounceSec: Int,
    val bikeResumeDebounceSec: Int,
    val runSpeedThreshold: Int,
    val runDebounceSec: Int,
    val runResumeDebounceSec: Int,
    val otherSpeedThreshold: Int,
    val otherDebounceSec: Int,
    val otherResumeDebounceSec: Int
)

    val tuningConfigFlow: Flow<TuningConfig> = context.dataStore.data.map { prefs ->
        val rawElevationDomainStr = prefs[KEY_ELEVATION_X_AXIS_DOMAIN] ?: prefs[KEY_PROFILE_X_AXIS_DOMAIN]
        val elevationDomain = safeEnumValueOf(rawElevationDomainStr, TuningPreferencesDefaults.ELEVATION_X_AXIS_DOMAIN)

        val rawTelemetryDomainStr = prefs[KEY_TELEMETRY_X_AXIS_DOMAIN]
        val telemetryDomain = safeEnumValueOf(rawTelemetryDomainStr, TuningPreferencesDefaults.TELEMETRY_X_AXIS_DOMAIN)

        val rawFontFamilyStr = prefs[KEY_COCKPIT_FONT_FAMILY]
        val cockpitFontFamily = safeEnumValueOf(rawFontFamilyStr, TuningPreferencesDefaults.COCKPIT_FONT_FAMILY)

        val rawVariantStr = prefs[KEY_SENSOR_FIELD_VARIANT]
        val sensorFieldVariant = SensorFieldVariant.fromStorage(rawVariantStr)

        val rawRadarEdgeSideStr = prefs[KEY_RADAR_EDGE_SIDE]
        val radarEdgeSide = safeEnumValueOf(rawRadarEdgeSideStr, TuningPreferencesDefaults.RADAR_EDGE_SIDE)

        val rawRadarClearModeStr = prefs[KEY_RADAR_CLEAR_MODE]
        val radarClearMode = safeEnumValueOf(rawRadarClearModeStr, TuningPreferencesDefaults.RADAR_CLEAR_MODE)

        val rawAudioAlertModeStr = prefs[KEY_RADAR_AUDIO_ALERT_MODE]
        val radarAudioAlertMode = safeEnumValueOf(rawAudioAlertModeStr, TuningPreferencesDefaults.RADAR_AUDIO_ALERT_MODE)

        val rawAlertVolume = prefs[KEY_RADAR_ALERT_VOLUME] ?: TuningPreferencesDefaults.RADAR_ALERT_VOLUME
        val radarAlertVolume = rawAlertVolume.coerceIn(10, 100)

        val radarSpeakerBoost = prefs[KEY_RADAR_SPEAKER_BOOST] ?: TuningPreferencesDefaults.RADAR_SPEAKER_BOOST

        val rawAudioRouteStr = prefs[KEY_RADAR_AUDIO_ROUTE]
        val radarAudioRoute = safeEnumValueOf(rawAudioRouteStr, TuningPreferencesDefaults.RADAR_AUDIO_ROUTE)

        val radarHapticAlert = prefs[KEY_RADAR_HAPTIC_ALERT] ?: TuningPreferencesDefaults.RADAR_HAPTIC_ALERT
        val rawLightDefaultModeStr = prefs[KEY_RADAR_LIGHT_DEFAULT_MODE]
        val radarLightDefaultMode = safeEnumValueOf(rawLightDefaultModeStr, TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE)
        val radarAutoLightControl = prefs[KEY_RADAR_AUTO_LIGHT_CONTROL] ?: TuningPreferencesDefaults.RADAR_AUTO_LIGHT_CONTROL
        val radarPelotonAudioSync = prefs[KEY_RADAR_PELOTON_AUDIO_SYNC] ?: TuningPreferencesDefaults.RADAR_PELOTON_AUDIO_SYNC
        val radarSmartUrbanMute = prefs[KEY_RADAR_SMART_URBAN_MUTE] ?: TuningPreferencesDefaults.RADAR_SMART_URBAN_MUTE

        val rawFontWeightStr = prefs[KEY_COCKPIT_FONT_WEIGHT]
        val cockpitFontWeight = safeEnumValueOf(rawFontWeightStr, TuningPreferencesDefaults.COCKPIT_FONT_WEIGHT)
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

        val autoPausePrefs = mapAutoPausePreferences(prefs)

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
            routeSelectionRadiusKm = clampedRadiusKm,
            navigationCueTransparency = (prefs[KEY_NAVIGATION_CUE_TRANSPARENCY] ?: TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY).coerceIn(
                TuningPreferencesDefaults.MIN_NAVIGATION_CUE_TRANSPARENCY,
                TuningPreferencesDefaults.MAX_NAVIGATION_CUE_TRANSPARENCY
            ),
            navigationCueDismissDurationSec = (prefs[KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC] ?: TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC).let {
                if (it == 0) {
                    TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
                } else {
                    it.coerceIn(
                        TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC,
                        TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC
                    )
                }
            },
            elevationSmoothingSigmaMeters = (prefs[KEY_ELEVATION_SMOOTHING_SIGMA_METERS] ?: TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS).coerceIn(
                TuningPreferencesDefaults.MIN_ELEVATION_SMOOTHING_SIGMA_METERS,
                TuningPreferencesDefaults.MAX_ELEVATION_SMOOTHING_SIGMA_METERS
            ),
            mapFollowMeInitialZoom = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM,
            mapFollowMeSpeedZoomEnabled = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED,
            mapFollowMeCruisingZoom = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_CRUISING_ZOOM,
            mapFollowMeTiltAngle = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE,
            mapFollowMeLookaheadPaddingPercent = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT,
            mapFollowMeSpeedSmoothingAlpha = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA,
            mapFollowMeBearingSmoothingAlpha = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA,
            suppressAntMissingAdapterDialog = prefs[KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG] ?: TuningPreferencesDefaults.SUPPRESS_ANT_MISSING_ADAPTER_DIALOG,
            autoFollowUniqueRouteEnabled = prefs[KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED] ?: TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED,
            clusterTolEndpoints = (prefs[KEY_CLUSTER_TOL_ENDPOINTS] ?: TrainingApplication.getClusterTolEndpoints()).coerceIn(
                TuningPreferencesDefaults.MIN_CLUSTER_TOL_ENDPOINTS,
                TuningPreferencesDefaults.MAX_CLUSTER_TOL_ENDPOINTS
            ),
            clusterTolApex = (prefs[KEY_CLUSTER_TOL_APEX] ?: TrainingApplication.getClusterTolApex()).coerceIn(
                TuningPreferencesDefaults.MIN_CLUSTER_TOL_APEX,
                TuningPreferencesDefaults.MAX_CLUSTER_TOL_APEX
            ),
            clusterTolDistance = (prefs[KEY_CLUSTER_TOL_DISTANCE] ?: TrainingApplication.getClusterTolDistance()).coerceIn(
                TuningPreferencesDefaults.MIN_CLUSTER_TOL_DISTANCE,
                TuningPreferencesDefaults.MAX_CLUSTER_TOL_DISTANCE
            ),
            clusterTolAltitudePos = (prefs[KEY_CLUSTER_TOL_ALTITUDE_POS] ?: TrainingApplication.getClusterTolAltitudePos()).coerceIn(
                TuningPreferencesDefaults.MIN_CLUSTER_TOL_ALTITUDE_POS,
                TuningPreferencesDefaults.MAX_CLUSTER_TOL_ALTITUDE_POS
            ),
            clusterUseSportType = prefs[KEY_CLUSTER_USE_SPORT_TYPE] ?: TrainingApplication.useSportTypeForClustering(),
            clusterUseAltitudePos = prefs[KEY_CLUSTER_USE_ALTITUDE_POS] ?: TrainingApplication.useAltitudePosForClustering(),
            clusterScoreThreshold = (prefs[KEY_CLUSTER_SCORE_THRESHOLD] ?: TrainingApplication.getClusterScoreThreshold()).coerceIn(
                TuningPreferencesDefaults.MIN_CLUSTER_SCORE_THRESHOLD,
                TuningPreferencesDefaults.MAX_CLUSTER_SCORE_THRESHOLD
            ),
            autoPauseBikeEnabled = autoPausePrefs.bikeEnabled,
            autoPauseRunEnabled = autoPausePrefs.runEnabled,
            autoPauseOtherEnabled = autoPausePrefs.otherEnabled,
            startInAutoPauseEnabled = autoPausePrefs.startInAutoPauseEnabled,
            autoPauseBikeSpeedThreshold = autoPausePrefs.bikeSpeedThreshold,
            autoPauseBikeDebounceSec = autoPausePrefs.bikeDebounceSec,
            autoPauseBikeResumeDebounceSec = autoPausePrefs.bikeResumeDebounceSec,
            autoPauseRunSpeedThreshold = autoPausePrefs.runSpeedThreshold,
            autoPauseRunDebounceSec = autoPausePrefs.runDebounceSec,
            autoPauseRunResumeDebounceSec = autoPausePrefs.runResumeDebounceSec,
            autoPauseOtherSpeedThreshold = autoPausePrefs.otherSpeedThreshold,
            autoPauseOtherDebounceSec = autoPausePrefs.otherDebounceSec,
            autoPauseOtherResumeDebounceSec = autoPausePrefs.otherResumeDebounceSec,
            autoPauseSpeedThresholdKmh = autoPausePrefs.bikeSpeedThreshold.toFloat(),
            autoPauseDebounceSec = autoPausePrefs.bikeDebounceSec,
            enableCogganPowerMetrics = prefs[KEY_ENABLE_COGGAN_POWER_METRICS] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_COGGAN_POWER_METRICS,
            showCogganPowerInWorkoutSummary = prefs[KEY_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY] ?: TuningPreferencesDefaults.DEFAULT_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY,
            enablePmcModel = prefs[KEY_ENABLE_PMC_MODEL] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_PMC_MODEL,
            enableAerobicEfficiencyModel = prefs[KEY_ENABLE_AEROBIC_EFFICIENCY_MODEL] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_AEROBIC_EFFICIENCY_MODEL,
            showAerobicEfficiencyInWorkoutSummary = prefs[KEY_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY] ?: TuningPreferencesDefaults.DEFAULT_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY,
            enableCriticalPowerModel = prefs[KEY_ENABLE_CRITICAL_POWER_MODEL] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_CRITICAL_POWER_MODEL,
            showCriticalPowerInWorkoutSummary = prefs[KEY_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY] ?: TuningPreferencesDefaults.DEFAULT_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY,
            enableCardioPowerDynamics = prefs[KEY_ENABLE_CARDIO_POWER_DYNAMICS] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_CARDIO_POWER_DYNAMICS,
            showCardioPowerInWorkoutSummary = prefs[KEY_SHOW_CARDIO_POWER_IN_WORKOUT_SUMMARY] ?: TuningPreferencesDefaults.DEFAULT_SHOW_CARDIO_POWER_IN_WORKOUT_SUMMARY,
            dfaA1ThresholdT1 = prefs[KEY_DFA_A1_THRESHOLD_T1] ?: TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1,
            dfaA1ThresholdT2 = prefs[KEY_DFA_A1_THRESHOLD_T2] ?: TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2,
            radarEdgeSide = radarEdgeSide,
            radarClearMode = radarClearMode,
            radarAudioAlertMode = radarAudioAlertMode,
            radarAlertVolume = radarAlertVolume,
            radarSpeakerBoost = radarSpeakerBoost,
            radarAudioRoute = radarAudioRoute,
            radarHapticAlert = radarHapticAlert,
            radarLightDefaultMode = radarLightDefaultMode,
            radarAutoLightControl = radarAutoLightControl,
            radarPelotonAudioSync = radarPelotonAudioSync,
            radarSmartUrbanMute = radarSmartUrbanMute
        )
    }

    val enableCogganPowerMetricsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_COGGAN_POWER_METRICS] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_COGGAN_POWER_METRICS
    }

    val enablePmcModelFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_PMC_MODEL] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_PMC_MODEL
    }

    val enableAerobicEfficiencyModelFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_AEROBIC_EFFICIENCY_MODEL] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_AEROBIC_EFFICIENCY_MODEL
    }

    val enableCriticalPowerModelFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_CRITICAL_POWER_MODEL] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_CRITICAL_POWER_MODEL
    }

    val enableCardioPowerDynamicsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_CARDIO_POWER_DYNAMICS] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_CARDIO_POWER_DYNAMICS
    }

    suspend fun setEnableCardioPowerDynamics(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLE_CARDIO_POWER_DYNAMICS] = enabled
        }
    }

    suspend fun setEnableCogganPowerMetrics(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLE_COGGAN_POWER_METRICS] = enabled
        }
    }

    suspend fun setEnablePmcModel(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLE_PMC_MODEL] = enabled
        }
    }

    suspend fun setEnableAerobicEfficiencyModel(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLE_AEROBIC_EFFICIENCY_MODEL] = enabled
        }
    }

    suspend fun setEnableCriticalPowerModel(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLE_CRITICAL_POWER_MODEL] = enabled
        }
    }

    val dfaA1ThresholdT1Flow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_DFA_A1_THRESHOLD_T1] ?: TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1
    }

    val dfaA1ThresholdT2Flow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_DFA_A1_THRESHOLD_T2] ?: TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2
    }

    suspend fun setDfaA1ThresholdT1(threshold: Float) {
        val clamped = threshold.coerceIn(
            TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD,
            TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_DFA_A1_THRESHOLD_T1] = clamped
        }
    }

    suspend fun setDfaA1ThresholdT2(threshold: Float) {
        val clamped = threshold.coerceIn(
            TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD,
            TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_DFA_A1_THRESHOLD_T2] = clamped
        }
    }

    suspend fun setDfaA1Thresholds(t1: Float, t2: Float) {
        val clampedT1 = t1.coerceIn(
            TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD,
            TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD
        )
        val clampedT2 = t2.coerceIn(
            TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD,
            TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_DFA_A1_THRESHOLD_T1] = clampedT1
            prefs[KEY_DFA_A1_THRESHOLD_T2] = clampedT2
        }
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
        val clampedClusterTolEndpoints = config.clusterTolEndpoints.coerceIn(
            TuningPreferencesDefaults.MIN_CLUSTER_TOL_ENDPOINTS,
            TuningPreferencesDefaults.MAX_CLUSTER_TOL_ENDPOINTS
        )
        val clampedClusterTolApex = config.clusterTolApex.coerceIn(
            TuningPreferencesDefaults.MIN_CLUSTER_TOL_APEX,
            TuningPreferencesDefaults.MAX_CLUSTER_TOL_APEX
        )
        val clampedClusterTolDistance = config.clusterTolDistance.coerceIn(
            TuningPreferencesDefaults.MIN_CLUSTER_TOL_DISTANCE,
            TuningPreferencesDefaults.MAX_CLUSTER_TOL_DISTANCE
        )
        val clampedClusterTolAltitudePos = config.clusterTolAltitudePos.coerceIn(
            TuningPreferencesDefaults.MIN_CLUSTER_TOL_ALTITUDE_POS,
            TuningPreferencesDefaults.MAX_CLUSTER_TOL_ALTITUDE_POS
        )
        val clampedClusterScoreThreshold = config.clusterScoreThreshold.coerceIn(
            TuningPreferencesDefaults.MIN_CLUSTER_SCORE_THRESHOLD,
            TuningPreferencesDefaults.MAX_CLUSTER_SCORE_THRESHOLD
        )
        val clampedBikeSpeedThreshold = config.autoPauseBikeSpeedThreshold.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD
        )
        val clampedBikeDebounce = config.autoPauseBikeDebounceSec.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_BIKE_DEBOUNCE_SEC,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_BIKE_DEBOUNCE_SEC
        )
        val clampedRunSpeedThreshold = config.autoPauseRunSpeedThreshold.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD
        )
        val clampedRunDebounce = config.autoPauseRunDebounceSec.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_RUN_DEBOUNCE_SEC,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_RUN_DEBOUNCE_SEC
        )
        val clampedOtherSpeedThreshold = config.autoPauseOtherSpeedThreshold.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD
        )
        val clampedOtherDebounce = config.autoPauseOtherDebounceSec.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_OTHER_DEBOUNCE_SEC,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_OTHER_DEBOUNCE_SEC
        )
        val clampedDfaA1T1 = config.dfaA1ThresholdT1.coerceIn(
            TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD,
            TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD
        )
        val clampedDfaA1T2 = config.dfaA1ThresholdT2.coerceIn(
            TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD,
            TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD
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
            prefs[KEY_NAVIGATION_CUE_TRANSPARENCY] = config.navigationCueTransparency.coerceIn(
                TuningPreferencesDefaults.MIN_NAVIGATION_CUE_TRANSPARENCY,
                TuningPreferencesDefaults.MAX_NAVIGATION_CUE_TRANSPARENCY
            )
            prefs[KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC] = if (config.navigationCueDismissDurationSec == 0) {
                TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
            } else {
                config.navigationCueDismissDurationSec.coerceIn(
                    TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC,
                    TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC
                )
            }
            prefs[KEY_ELEVATION_SMOOTHING_SIGMA_METERS] = config.elevationSmoothingSigmaMeters.coerceIn(
                TuningPreferencesDefaults.MIN_ELEVATION_SMOOTHING_SIGMA_METERS,
                TuningPreferencesDefaults.MAX_ELEVATION_SMOOTHING_SIGMA_METERS
            )
            // ATT-3581: Actively purge any legacy camera preferences to prevent stale override interference
            for (legacyKey in LEGACY_MAP_CAMERA_KEYS) {
                prefs.remove(legacyKey)
            }
            prefs[KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG] = config.suppressAntMissingAdapterDialog
            prefs[KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED] = config.autoFollowUniqueRouteEnabled
            prefs[KEY_CLUSTER_TOL_ENDPOINTS] = clampedClusterTolEndpoints
            prefs[KEY_CLUSTER_TOL_APEX] = clampedClusterTolApex
            prefs[KEY_CLUSTER_TOL_DISTANCE] = clampedClusterTolDistance
            prefs[KEY_CLUSTER_TOL_ALTITUDE_POS] = clampedClusterTolAltitudePos
            prefs[KEY_CLUSTER_USE_SPORT_TYPE] = config.clusterUseSportType
            prefs[KEY_CLUSTER_USE_ALTITUDE_POS] = config.clusterUseAltitudePos
            prefs[KEY_CLUSTER_SCORE_THRESHOLD] = clampedClusterScoreThreshold
            prefs[KEY_AUTO_PAUSE_BIKE_ENABLED] = config.autoPauseBikeEnabled
            prefs[KEY_AUTO_PAUSE_RUN_ENABLED] = config.autoPauseRunEnabled
            prefs[KEY_AUTO_PAUSE_OTHER_ENABLED] = config.autoPauseOtherEnabled
            prefs[KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD] = clampedBikeSpeedThreshold
            prefs[KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC] = clampedBikeDebounce
            prefs[KEY_AUTO_PAUSE_RUN_SPEED_THRESHOLD] = clampedRunSpeedThreshold
            prefs[KEY_AUTO_PAUSE_RUN_DEBOUNCE_SEC] = clampedRunDebounce
            prefs[KEY_AUTO_PAUSE_OTHER_SPEED_THRESHOLD] = clampedOtherSpeedThreshold
            prefs[KEY_AUTO_PAUSE_OTHER_DEBOUNCE_SEC] = clampedOtherDebounce
            prefs[KEY_ENABLE_COGGAN_POWER_METRICS] = config.enableCogganPowerMetrics
            prefs[KEY_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY] = config.showCogganPowerInWorkoutSummary
            prefs[KEY_ENABLE_PMC_MODEL] = config.enablePmcModel
            prefs[KEY_ENABLE_AEROBIC_EFFICIENCY_MODEL] = config.enableAerobicEfficiencyModel
            prefs[KEY_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY] = config.showAerobicEfficiencyInWorkoutSummary
            prefs[KEY_ENABLE_CRITICAL_POWER_MODEL] = config.enableCriticalPowerModel
            prefs[KEY_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY] = config.showCriticalPowerInWorkoutSummary
            prefs[KEY_ENABLE_CARDIO_POWER_DYNAMICS] = config.enableCardioPowerDynamics
            prefs[KEY_SHOW_CARDIO_POWER_IN_WORKOUT_SUMMARY] = config.showCardioPowerInWorkoutSummary
            prefs[KEY_DFA_A1_THRESHOLD_T1] = clampedDfaA1T1
            prefs[KEY_DFA_A1_THRESHOLD_T2] = clampedDfaA1T2
            prefs[KEY_RADAR_EDGE_SIDE] = config.radarEdgeSide.name
            prefs[KEY_RADAR_CLEAR_MODE] = config.radarClearMode.name
            prefs[KEY_RADAR_AUDIO_ALERT_MODE] = config.radarAudioAlertMode.name
            prefs[KEY_RADAR_ALERT_VOLUME] = config.radarAlertVolume.coerceIn(10, 100)
            prefs[KEY_RADAR_SPEAKER_BOOST] = config.radarSpeakerBoost
            prefs[KEY_RADAR_AUDIO_ROUTE] = config.radarAudioRoute.name
            prefs[KEY_RADAR_HAPTIC_ALERT] = config.radarHapticAlert
            prefs[KEY_RADAR_LIGHT_DEFAULT_MODE] = config.radarLightDefaultMode.name
            prefs[KEY_RADAR_AUTO_LIGHT_CONTROL] = config.radarAutoLightControl
            prefs[KEY_RADAR_PELOTON_AUDIO_SYNC] = config.radarPelotonAudioSync
            prefs[KEY_RADAR_SMART_URBAN_MUTE] = config.radarSmartUrbanMute
        }

        TrainingApplication.setClusterTolEndpoints(clampedClusterTolEndpoints)
        TrainingApplication.setClusterTolApex(clampedClusterTolApex)
        TrainingApplication.setClusterTolDistance(clampedClusterTolDistance)
        TrainingApplication.setClusterTolAltitudePos(clampedClusterTolAltitudePos)
        TrainingApplication.setUseSportTypeForClustering(config.clusterUseSportType)
        TrainingApplication.setUseAltitudePosForClustering(config.clusterUseAltitudePos)
        TrainingApplication.setClusterScoreThreshold(clampedClusterScoreThreshold)
        TrackerService.invalidateAutoPauseCache()
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

    @Deprecated("Navigation cue overlays are permanently solid (1.0f) per REQ-UI-287 / ATT-3322")
    suspend fun updateNavigationCueTransparency(transparency: Float) {
        val clamped = transparency.coerceIn(
            TuningPreferencesDefaults.MIN_NAVIGATION_CUE_TRANSPARENCY,
            TuningPreferencesDefaults.MAX_NAVIGATION_CUE_TRANSPARENCY
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_NAVIGATION_CUE_TRANSPARENCY] = clamped
        }
    }

    suspend fun updateNavigationCueDismissDurationSec(durationSec: Int) {
        val valid = if (durationSec == 0) {
            TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
        } else {
            durationSec.coerceIn(
                TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC,
                TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC
            )
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC] = valid
        }
    }

    suspend fun updateElevationSmoothingSigmaMeters(sigma: Float) {
        val clamped = sigma.coerceIn(
            TuningPreferencesDefaults.MIN_ELEVATION_SMOOTHING_SIGMA_METERS,
            TuningPreferencesDefaults.MAX_ELEVATION_SMOOTHING_SIGMA_METERS
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_ELEVATION_SMOOTHING_SIGMA_METERS] = clamped
        }
    }

    /**
     * Purges legacy map camera tuning keys from Preferences DataStore so old stored values
     * cannot persist or interfere with calibrated defaults (REQ-MAP-050 / ATT-3581).
     */
    suspend fun purgeLegacyMapCameraPreferences() {
        context.dataStore.edit { prefs ->
            purgeLegacyMapCameraKeys(prefs)
        }
    }

    @Deprecated("Camera parameters are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
    suspend fun updateMapFollowMeInitialZoom(zoom: Float) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_MAP_FOLLOW_ME_INITIAL_ZOOM)
        }
    }

    @Deprecated("Camera parameters are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
    suspend fun updateMapFollowMeSpeedZoomEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED)
        }
    }

    @Deprecated("Camera parameters are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
    suspend fun updateMapFollowMeCruisingZoom(zoom: Float) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_MAP_FOLLOW_ME_CRUISING_ZOOM)
        }
    }

    @Deprecated("Camera parameters are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
    suspend fun updateMapFollowMeTiltAngle(angle: Float) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_MAP_FOLLOW_ME_TILT_ANGLE)
        }
    }

    @Deprecated("Camera parameters are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
    suspend fun updateMapFollowMeLookaheadPaddingPercent(percent: Float) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT)
        }
    }

    val suppressAntMissingAdapterDialogFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG] ?: TuningPreferencesDefaults.SUPPRESS_ANT_MISSING_ADAPTER_DIALOG
    }

    suspend fun setSuppressAntMissingAdapterDialog(suppress: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG] = suppress
        }
    }

    suspend fun updateAutoFollowUniqueRouteEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED] = enabled
        }
    }

    suspend fun updateTurnCueCountdownDistanceMeters(distanceMeters: Float) {
        val clamped = distanceMeters.coerceIn(
            TuningPreferencesDefaults.MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS,
            TuningPreferencesDefaults.MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_TURN_CUE_COUNTDOWN_DISTANCE] = clamped
        }
    }

    val radarEdgeSideFlow: Flow<RadarEdgeSide> = context.dataStore.data.map { prefs ->
        safeEnumValueOf(prefs[KEY_RADAR_EDGE_SIDE], TuningPreferencesDefaults.RADAR_EDGE_SIDE)
    }

    val radarClearModeFlow: Flow<RadarClearMode> = context.dataStore.data.map { prefs ->
        safeEnumValueOf(prefs[KEY_RADAR_CLEAR_MODE], TuningPreferencesDefaults.RADAR_CLEAR_MODE)
    }

    suspend fun updateRadarEdgeSide(side: RadarEdgeSide) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RADAR_EDGE_SIDE] = side.name
        }
    }

    suspend fun updateRadarClearMode(mode: RadarClearMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RADAR_CLEAR_MODE] = mode.name
        }
    }

    val radarAudioAlertModeFlow: Flow<RadarAudioAlertMode> = context.dataStore.data.map { prefs ->
        safeEnumValueOf(prefs[KEY_RADAR_AUDIO_ALERT_MODE], TuningPreferencesDefaults.RADAR_AUDIO_ALERT_MODE)
    }

    val radarAlertVolumeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[KEY_RADAR_ALERT_VOLUME] ?: TuningPreferencesDefaults.RADAR_ALERT_VOLUME).coerceIn(10, 100)
    }

    val radarSpeakerBoostFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_RADAR_SPEAKER_BOOST] ?: TuningPreferencesDefaults.RADAR_SPEAKER_BOOST
    }

    val radarAudioRouteFlow: Flow<RadarAudioRoute> = context.dataStore.data.map { prefs ->
        safeEnumValueOf(prefs[KEY_RADAR_AUDIO_ROUTE], TuningPreferencesDefaults.RADAR_AUDIO_ROUTE)
    }

    val radarHapticAlertFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_RADAR_HAPTIC_ALERT] ?: TuningPreferencesDefaults.RADAR_HAPTIC_ALERT
    }

    suspend fun updateRadarAudioAlertMode(mode: RadarAudioAlertMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RADAR_AUDIO_ALERT_MODE] = mode.name
        }
    }

    suspend fun updateRadarAlertVolume(volume: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RADAR_ALERT_VOLUME] = volume.coerceIn(10, 100)
        }
    }

    suspend fun updateRadarSpeakerBoost(boost: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RADAR_SPEAKER_BOOST] = boost
        }
    }

    suspend fun updateRadarAudioRoute(route: RadarAudioRoute) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RADAR_AUDIO_ROUTE] = route.name
        }
    }

    suspend fun updateRadarHapticAlert(haptic: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RADAR_HAPTIC_ALERT] = haptic
        }
    }

    val radarLightDefaultModeFlow: Flow<RadarLightMode> = context.dataStore.data.map { prefs ->
        safeEnumValueOf(prefs[KEY_RADAR_LIGHT_DEFAULT_MODE], TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE)
    }

    val radarAutoLightControlFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_RADAR_AUTO_LIGHT_CONTROL] ?: TuningPreferencesDefaults.RADAR_AUTO_LIGHT_CONTROL
    }

    suspend fun updateRadarLightDefaultMode(mode: RadarLightMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RADAR_LIGHT_DEFAULT_MODE] = mode.name
        }
    }

    suspend fun updateRadarAutoLightControl(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RADAR_AUTO_LIGHT_CONTROL] = enabled
        }
    }

    val radarPelotonAudioSyncFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_RADAR_PELOTON_AUDIO_SYNC] ?: TuningPreferencesDefaults.RADAR_PELOTON_AUDIO_SYNC
    }

    val radarSmartUrbanMuteFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_RADAR_SMART_URBAN_MUTE] ?: TuningPreferencesDefaults.RADAR_SMART_URBAN_MUTE
    }

    suspend fun updateRadarPelotonAudioSync(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RADAR_PELOTON_AUDIO_SYNC] = enabled
        }
    }

    suspend fun updateRadarSmartUrbanMute(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RADAR_SMART_URBAN_MUTE] = enabled
        }
    }

    /**
     * Reactive flow emitting whether the first-time stop tracking coachmark hint on the cockpit screen
     * has already been seen/dismissed by the user (REQ-UI-367 / ATT-3387).
     */
    val hasSeenStopTrackingHintFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HAS_SEEN_STOP_TRACKING_HINT] ?: TuningPreferencesDefaults.DEFAULT_HAS_SEEN_STOP_TRACKING_HINT
    }

    /**
     * Atomically persists whether the first-time stop tracking coachmark hint has been acknowledged (REQ-UI-367 / ATT-3387).
     */
    suspend fun setHasSeenStopTrackingHint(seen: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_STOP_TRACKING_HINT] = seen
        }
    }

    /**
     * Reactive flow emitting whether the first-install permission onboarding flow has been completed or dismissed (REQ-PRI-007 / ATT-4455).
     */
    val hasCompletedPermissionOnboardingFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HAS_COMPLETED_PERMISSION_ONBOARDING] ?: TuningPreferencesDefaults.DEFAULT_HAS_COMPLETED_PERMISSION_ONBOARDING
    }

    /**
     * Atomically persists whether the first-install permission onboarding flow has been completed or dismissed (REQ-PRI-007 / ATT-4455).
     */
    suspend fun setHasCompletedPermissionOnboarding(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAS_COMPLETED_PERMISSION_ONBOARDING] = completed
        }
    }

    suspend fun updateStartInAutoPauseEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_START_IN_AUTO_PAUSE_ENABLED] = enabled
        }
        TrackerService.invalidateAutoPauseCache()
    }

    suspend fun updateAutoPauseBikeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_BIKE_ENABLED] = enabled
        }
    }

    suspend fun updateAutoPauseRunEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_RUN_ENABLED] = enabled
        }
    }

    suspend fun updateAutoPauseOtherEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_OTHER_ENABLED] = enabled
        }
    }

    suspend fun updateAutoPauseSpeedThresholdKmh(thresholdKmh: Float) {
        val clamped = thresholdKmh.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD_KMH,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD_KMH
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH] = clamped
        }
        TrackerService.invalidateAutoPauseCache()
    }

    suspend fun updateAutoPauseDebounceSec(debounceSec: Int) {
        val clamped = debounceSec.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_DEBOUNCE_SEC,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_DEBOUNCE_SEC
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_DEBOUNCE_SEC] = clamped
        }
        TrackerService.invalidateAutoPauseCache()
    }

    suspend fun updateAutoPauseBikeSpeedThreshold(threshold: Int) {
        val clamped = threshold.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD] = clamped
            prefs[KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH] = clamped.toFloat()
        }
        TrackerService.invalidateAutoPauseCache()
    }

    suspend fun updateAutoPauseBikeDebounceSec(debounceSec: Int) {
        val clamped = debounceSec.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_BIKE_DEBOUNCE_SEC,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_BIKE_DEBOUNCE_SEC
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC] = clamped
            prefs[KEY_AUTO_PAUSE_DEBOUNCE_SEC] = clamped
        }
        TrackerService.invalidateAutoPauseCache()
    }

    suspend fun updateAutoPauseBikeResumeDebounceSec(debounceSec: Int) {
        val clamped = debounceSec.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC] = clamped
        }
        TrackerService.invalidateAutoPauseCache()
    }

    suspend fun updateAutoPauseRunSpeedThreshold(threshold: Int) {
        val clamped = threshold.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_RUN_SPEED_THRESHOLD] = clamped
        }
        TrackerService.invalidateAutoPauseCache()
    }

    suspend fun updateAutoPauseRunDebounceSec(debounceSec: Int) {
        val clamped = debounceSec.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_RUN_DEBOUNCE_SEC,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_RUN_DEBOUNCE_SEC
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_RUN_DEBOUNCE_SEC] = clamped
        }
        TrackerService.invalidateAutoPauseCache()
    }

    suspend fun updateAutoPauseRunResumeDebounceSec(debounceSec: Int) {
        val clamped = debounceSec.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC] = clamped
        }
        TrackerService.invalidateAutoPauseCache()
    }

    suspend fun updateAutoPauseOtherSpeedThreshold(threshold: Int) {
        val clamped = threshold.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_OTHER_SPEED_THRESHOLD] = clamped
        }
        TrackerService.invalidateAutoPauseCache()
    }

    suspend fun updateAutoPauseOtherDebounceSec(debounceSec: Int) {
        val clamped = debounceSec.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_OTHER_DEBOUNCE_SEC,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_OTHER_DEBOUNCE_SEC
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_OTHER_DEBOUNCE_SEC] = clamped
        }
        TrackerService.invalidateAutoPauseCache()
    }

    suspend fun updateAutoPauseOtherResumeDebounceSec(debounceSec: Int) {
        val clamped = debounceSec.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC
        )
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC] = clamped
        }
        TrackerService.invalidateAutoPauseCache()
    }

    suspend fun resetAutoPausePreferences(sportType: BSportType? = null) {
        context.dataStore.edit { prefs ->
            when (sportType) {
                BSportType.BIKE -> {
                    prefs.remove(KEY_AUTO_PAUSE_BIKE_ENABLED)
                    prefs.remove(KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD)
                    prefs.remove(KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC)
                    prefs.remove(KEY_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC)
                    prefs.remove(KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH)
                    prefs.remove(KEY_AUTO_PAUSE_DEBOUNCE_SEC)
                }
                BSportType.RUN -> {
                    prefs.remove(KEY_AUTO_PAUSE_RUN_ENABLED)
                    prefs.remove(KEY_AUTO_PAUSE_RUN_SPEED_THRESHOLD)
                    prefs.remove(KEY_AUTO_PAUSE_RUN_DEBOUNCE_SEC)
                    prefs.remove(KEY_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC)
                }
                BSportType.UNKNOWN, BSportType.CONFLICT -> {
                    prefs.remove(KEY_AUTO_PAUSE_OTHER_ENABLED)
                    prefs.remove(KEY_AUTO_PAUSE_OTHER_SPEED_THRESHOLD)
                    prefs.remove(KEY_AUTO_PAUSE_OTHER_DEBOUNCE_SEC)
                    prefs.remove(KEY_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC)
                }
                null -> {
                    prefs.remove(KEY_START_IN_AUTO_PAUSE_ENABLED)
                    prefs.remove(KEY_AUTO_PAUSE_BIKE_ENABLED)
                    prefs.remove(KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD)
                    prefs.remove(KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC)
                    prefs.remove(KEY_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC)
                    prefs.remove(KEY_AUTO_PAUSE_RUN_ENABLED)
                    prefs.remove(KEY_AUTO_PAUSE_RUN_SPEED_THRESHOLD)
                    prefs.remove(KEY_AUTO_PAUSE_RUN_DEBOUNCE_SEC)
                    prefs.remove(KEY_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC)
                    prefs.remove(KEY_AUTO_PAUSE_OTHER_ENABLED)
                    prefs.remove(KEY_AUTO_PAUSE_OTHER_SPEED_THRESHOLD)
                    prefs.remove(KEY_AUTO_PAUSE_OTHER_DEBOUNCE_SEC)
                    prefs.remove(KEY_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC)
                    prefs.remove(KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH)
                    prefs.remove(KEY_AUTO_PAUSE_DEBOUNCE_SEC)
                }
            }
        }
        TrackerService.invalidateAutoPauseCache()
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { prefs ->
            for (key in ALL_KEYS) {
                prefs.remove(key)
            }
        }
        TrainingApplication.setClusterTolEndpoints(TuningPreferencesDefaults.CLUSTER_TOL_ENDPOINTS)
        TrainingApplication.setClusterTolApex(TuningPreferencesDefaults.CLUSTER_TOL_APEX)
        TrainingApplication.setClusterTolDistance(TuningPreferencesDefaults.CLUSTER_TOL_DISTANCE)
        TrainingApplication.setClusterTolAltitudePos(TuningPreferencesDefaults.CLUSTER_TOL_ALTITUDE_POS)
        TrainingApplication.setUseSportTypeForClustering(TuningPreferencesDefaults.CLUSTER_USE_SPORT_TYPE)
        TrainingApplication.setUseAltitudePosForClustering(TuningPreferencesDefaults.CLUSTER_USE_ALTITUDE_POS)
        TrainingApplication.setClusterScoreThreshold(TuningPreferencesDefaults.CLUSTER_SCORE_THRESHOLD)
    }
}
