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
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.climbs.ClimbScoringFormula
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
    FULL_SCREEN,
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
 * Rearview radar tail light control mode (REQ-CON-034.7 / ATT-7538).
 */
enum class RadarLightControlMode {
    MANUAL,
    FIXED_WORKOUT_MODE,
    SMART_AUTOMATION
}

/**
 * Display unit for configured swimming pool length (REQ-SET-107.3).
 */
enum class PoolLengthUnit {
    METERS,
    YARDS
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
    val RADAR_LIGHT_CONTROL_MODE = RadarLightControlMode.SMART_AUTOMATION
    const val RADAR_LIGHT_AUTO_MODE = true
    const val RADAR_AUTO_LIGHT_CONTROL = false
    const val RADAR_SMART_LIGHT_AUTOMATION = true
    const val RADAR_BRAKE_LIGHT_ENABLED = true
    const val RADAR_PELOTON_AUDIO_SYNC = true
    const val RADAR_SMART_URBAN_MUTE = false
    const val RADAR_SPATIAL_AUDIO_ENABLED = true
    const val RADAR_SPEED_PITCH_MODULATION = true
    val RADAR_TRAFFIC_ORIENTATION = RadarTrafficOrientation.RIGHT_HAND
    const val RADAR_LANE_CHANGE_TICK_ENABLED = true
    const val RADAR_CLOSE_PASS_THRESHOLD_M = 1.5f
    const val FULL_DIM_FACTOR = 0.25f
    const val MEDIUM_DIM_FACTOR = 0.50f
    const val SLOPE_FLAT_THRESHOLD = 2.0f
    const val SLOPE_STEEP_THRESHOLD = 5.0f
    const val WAKEUP_DURATION_SEC = 15
    const val DOWNWARD_DELAY_SEC = 3
    const val GPS_ACCURACY_THRESHOLD_M = 50.0f
    const val ALTITUDE_FILTER_WINDOW_SEC = 21
    const val SLOPE_MIN_SPEED_MPS = 0.5f
    const val DEFAULT_POST_WORKOUT_ERTS_SMOOTHING_ENABLED = false
    val RAW_SENSOR_RETENTION_POLICY = RawSensorRetentionPolicy.DAYS_30
    const val DEFAULT_RECORD_SMARTPHONE_RAW_TELEMETRY_ENABLED = false

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
    val DEFAULT_CLIMB_SCORING_FORMULA = ClimbScoringFormula.FIETS

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

    const val DEFAULT_ENABLE_MORNING_SLEEP_AND_WELLNESS = false
    const val DEFAULT_ENABLE_SLEEP_DURATION_AND_QUALITY = true
    const val DEFAULT_ENABLE_MORNING_ENERGY = true
    const val DEFAULT_ENABLE_MUSCLE_SORENESS = false
    const val DEFAULT_ENABLE_CONTEXT_TAGS = false

    const val DEFAULT_DFA_A1_THRESHOLD_T1 = 0.75f
    const val DEFAULT_DFA_A1_THRESHOLD_T2 = 0.50f
    const val MIN_DFA_A1_THRESHOLD = 0.20f
    const val MAX_DFA_A1_THRESHOLD = 1.50f
    const val MIN_DFA_A1_THRESHOLD_GAP = 0.05f

    const val DEFAULT_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT = 5.0f
    const val MIN_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT = 3.0f
    const val MAX_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT = 5.0f

    const val DEFAULT_SHOW_MORNING_CHECK_ON_START_SCREEN = true
    const val DEFAULT_MORNING_CHECK_CUTOFF_HOUR = 8
    const val MIN_MORNING_CHECK_CUTOFF_HOUR = 6
    const val MAX_MORNING_CHECK_CUTOFF_HOUR = 11

    const val DEFAULT_POOL_LENGTH_METERS = 25.0f
    const val MIN_POOL_LENGTH_METERS = 10.0f
    const val MAX_POOL_LENGTH_METERS = 100.0f
    const val POOL_LENGTH_25M = 25.0f
    const val POOL_LENGTH_50M = 50.0f
    const val POOL_LENGTH_25YD_METERS = 22.86f
    val DEFAULT_POOL_LENGTH_UNIT = PoolLengthUnit.METERS
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
    val postWorkoutErtsSmoothingEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_POST_WORKOUT_ERTS_SMOOTHING_ENABLED,
    val rawSensorRetentionPolicy: RawSensorRetentionPolicy = TuningPreferencesDefaults.RAW_SENSOR_RETENTION_POLICY,
    val recordSmartphoneRawTelemetryEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_RECORD_SMARTPHONE_RAW_TELEMETRY_ENABLED,
    val paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM,
    val showLiveClimbs: Boolean = TuningPreferencesDefaults.SHOW_LIVE_CLIMBS,
    val climbMinLengthMeters: Float = TuningPreferencesDefaults.CLIMB_MIN_LENGTH_METERS,
    val climbMinGradientPercent: Float = TuningPreferencesDefaults.CLIMB_MIN_GRADIENT_PERCENT,
    val climbScoringFormula: ClimbScoringFormula = TuningPreferencesDefaults.DEFAULT_CLIMB_SCORING_FORMULA,
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
    val enableMorningSleepAndWellness: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_MORNING_SLEEP_AND_WELLNESS,
    val enableSleepDurationAndQuality: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_SLEEP_DURATION_AND_QUALITY,
    val enableMorningEnergy: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_MORNING_ENERGY,
    val enableMuscleSoreness: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_MUSCLE_SORENESS,
    val enableContextTags: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_CONTEXT_TAGS,
    val showMorningCheckOnStartScreen: Boolean = TuningPreferencesDefaults.DEFAULT_SHOW_MORNING_CHECK_ON_START_SCREEN,
    val morningCheckCutoffHour: Int = TuningPreferencesDefaults.DEFAULT_MORNING_CHECK_CUTOFF_HOUR,
    val dfaA1ThresholdT1: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1,
    val dfaA1ThresholdT2: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2,
    val hrvArtifactGateThresholdPercent: Float = TuningPreferencesDefaults.DEFAULT_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT,
    val radarEdgeSide: RadarEdgeSide = TuningPreferencesDefaults.RADAR_EDGE_SIDE,
    val radarClearMode: RadarClearMode = TuningPreferencesDefaults.RADAR_CLEAR_MODE,
    val radarAudioAlertMode: RadarAudioAlertMode = TuningPreferencesDefaults.RADAR_AUDIO_ALERT_MODE,
    val radarAlertVolume: Int = TuningPreferencesDefaults.RADAR_ALERT_VOLUME,
    val radarSpeakerBoost: Boolean = TuningPreferencesDefaults.RADAR_SPEAKER_BOOST,
    val radarAudioRoute: RadarAudioRoute = TuningPreferencesDefaults.RADAR_AUDIO_ROUTE,
    val radarHapticAlert: Boolean = TuningPreferencesDefaults.RADAR_HAPTIC_ALERT,
    val radarLightDefaultMode: RadarLightMode = TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE,
    val radarLightControlMode: RadarLightControlMode = TuningPreferencesDefaults.RADAR_LIGHT_CONTROL_MODE,
    val radarBrakeLightEnabled: Boolean = TuningPreferencesDefaults.RADAR_BRAKE_LIGHT_ENABLED,
    val radarPelotonAudioSync: Boolean = TuningPreferencesDefaults.RADAR_PELOTON_AUDIO_SYNC,
    val radarSmartUrbanMute: Boolean = TuningPreferencesDefaults.RADAR_SMART_URBAN_MUTE,
    val radarSpatialAudioEnabled: Boolean = TuningPreferencesDefaults.RADAR_SPATIAL_AUDIO_ENABLED,
    val radarSpeedPitchModulation: Boolean = TuningPreferencesDefaults.RADAR_SPEED_PITCH_MODULATION,
    val radarTrafficOrientation: RadarTrafficOrientation = TuningPreferencesDefaults.RADAR_TRAFFIC_ORIENTATION,
    val radarLaneChangeTickEnabled: Boolean = TuningPreferencesDefaults.RADAR_LANE_CHANGE_TICK_ENABLED,
    val radarClosePassThresholdM: Float = TuningPreferencesDefaults.RADAR_CLOSE_PASS_THRESHOLD_M,
    val defaultPoolLengthMeters: Float = TuningPreferencesDefaults.DEFAULT_POOL_LENGTH_METERS,
    val defaultPoolLengthUnit: PoolLengthUnit = TuningPreferencesDefaults.DEFAULT_POOL_LENGTH_UNIT
) {
    val showAntMissingAdapterWarnings: Boolean
        get() = !suppressAntMissingAdapterDialog

    /** Derived from canonical [radarLightControlMode] (REQ-CON-056.1). */
    val radarLightAutoMode: Boolean
        get() = radarLightControlMode == RadarLightControlMode.SMART_AUTOMATION

    /** Derived from canonical [radarLightControlMode] (REQ-CON-056.1). */
    val radarAutoLightControl: Boolean
        get() = radarLightControlMode == RadarLightControlMode.FIXED_WORKOUT_MODE

    /** Derived from canonical [radarLightControlMode] (REQ-CON-056.1). */
    val radarSmartLightAutomation: Boolean
        get() = radarLightControlMode == RadarLightControlMode.SMART_AUTOMATION

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
 * Declarative descriptor pairing a DataStore preference key with its factory default and inclusive clamping bounds.
 */
internal class BoundedPref<T : Comparable<T>>(
    val key: Preferences.Key<T>,
    val default: T,
    val min: T,
    val max: T
) {
    fun clamp(value: T): T = value.coerceIn(min, max)
    fun read(prefs: Preferences, fallback: T = default): T = clamp(prefs[key] ?: fallback)
    fun write(prefs: MutablePreferences, value: T) {
        prefs[key] = clamp(value)
    }
}

/**
 * DataStore repository managing expert tuning preferences with strict parameter validation and atomic factory reset.
 * Conforms to REQ-SET-073 and REQ-SET-121.
 */
class TuningPreferencesDataStore private constructor(
    context: Context,
    private val appScope: CoroutineScope,
    dataStoreOverride: DataStore<Preferences>? = null
) {
    private val context: Context = context.applicationContext ?: context
    private val dataStore: DataStore<Preferences> = dataStoreOverride ?: this.context.dataStore

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
        val KEY_POST_WORKOUT_ERTS_SMOOTHING: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_post_workout_erts_smoothing")
        val KEY_RAW_SENSOR_RETENTION_POLICY: Preferences.Key<String> = stringPreferencesKey("tuning_raw_sensor_retention_policy")
        val KEY_RECORD_SMARTPHONE_RAW_TELEMETRY: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_record_smartphone_raw_telemetry")
        val KEY_PACE_CEILING_MIN_KM: Preferences.Key<Float> = floatPreferencesKey("tuning_pace_ceiling_min_km")
        val KEY_SHOW_LIVE_CLIMBS: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_show_live_climbs")
        val KEY_CLIMB_MIN_LENGTH_METERS: Preferences.Key<Float> = floatPreferencesKey("tuning_climb_min_length_meters")
        val KEY_CLIMB_MIN_GRADIENT_PERCENT: Preferences.Key<Float> = floatPreferencesKey("tuning_climb_min_gradient_percent")
        val KEY_CLIMB_SCORING_FORMULA: Preferences.Key<String> = stringPreferencesKey("tuning_climb_scoring_formula")
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
        val KEY_ENABLE_MORNING_SLEEP_AND_WELLNESS: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_enable_morning_sleep_and_wellness")
        val KEY_ENABLE_SLEEP_DURATION_AND_QUALITY: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_enable_sleep_duration_and_quality")
        val KEY_ENABLE_MORNING_ENERGY: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_enable_morning_energy")
        val KEY_ENABLE_MUSCLE_SORENESS: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_enable_muscle_soreness")
        val KEY_ENABLE_CONTEXT_TAGS: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_enable_context_tags")
        val KEY_SHOW_MORNING_CHECK_ON_START_SCREEN: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_show_morning_check_on_start_screen")
        val KEY_MORNING_CHECK_CUTOFF_HOUR: Preferences.Key<Int> = intPreferencesKey("tuning_morning_check_cutoff_hour")
        val KEY_DFA_A1_THRESHOLD_T1: Preferences.Key<Float> = floatPreferencesKey("tuning_dfa_a1_threshold_t1")
        val KEY_DFA_A1_THRESHOLD_T2: Preferences.Key<Float> = floatPreferencesKey("tuning_dfa_a1_threshold_t2")
        val KEY_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT: Preferences.Key<Float> = floatPreferencesKey("tuning_hrv_artifact_gate_threshold_percent")
        val KEY_RADAR_EDGE_SIDE: Preferences.Key<String> = stringPreferencesKey("tuning_radar_edge_side")
        val KEY_RADAR_CLEAR_MODE: Preferences.Key<String> = stringPreferencesKey("tuning_radar_clear_mode")
        val KEY_RADAR_AUDIO_ALERT_MODE: Preferences.Key<String> = stringPreferencesKey("tuning_radar_audio_alert_mode")
        val KEY_RADAR_ALERT_VOLUME: Preferences.Key<Int> = intPreferencesKey("tuning_radar_alert_volume")
        val KEY_RADAR_SPEAKER_BOOST: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_speaker_boost")
        val KEY_RADAR_AUDIO_ROUTE: Preferences.Key<String> = stringPreferencesKey("tuning_radar_audio_route")
        val KEY_RADAR_HAPTIC_ALERT: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_haptic_alert")
        val KEY_RADAR_LIGHT_DEFAULT_MODE: Preferences.Key<String> = stringPreferencesKey("tuning_radar_light_default_mode")
        val KEY_RADAR_LIGHT_CONTROL_MODE: Preferences.Key<String> = stringPreferencesKey("tuning_radar_light_control_mode")
        @Deprecated("Consolidated into KEY_RADAR_LIGHT_CONTROL_MODE (REQ-CON-056.1)")
        val KEY_RADAR_LIGHT_AUTO_MODE: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_light_auto_mode")
        @Deprecated("Consolidated into KEY_RADAR_LIGHT_CONTROL_MODE (REQ-CON-056.1)")
        val KEY_RADAR_AUTO_LIGHT_CONTROL: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_auto_light_control")
        @Deprecated("Consolidated into KEY_RADAR_LIGHT_CONTROL_MODE (REQ-CON-056.1)")
        val KEY_RADAR_SMART_LIGHT_AUTOMATION: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_smart_light_automation")

        @Suppress("DEPRECATION")
        val LEGACY_RADAR_LIGHT_KEYS: List<Preferences.Key<*>> = listOf(
            KEY_RADAR_LIGHT_AUTO_MODE,
            KEY_RADAR_AUTO_LIGHT_CONTROL,
            KEY_RADAR_SMART_LIGHT_AUTOMATION
        )
        val KEY_RADAR_BRAKE_LIGHT_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_brake_light_enabled")
        val KEY_RADAR_PELOTON_AUDIO_SYNC: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_peloton_audio_sync")
        val KEY_RADAR_SMART_URBAN_MUTE: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_smart_urban_mute")
        val KEY_RADAR_SPATIAL_AUDIO_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_spatial_audio_enabled")
        val KEY_RADAR_SPEED_PITCH_MODULATION: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_speed_pitch_modulation")
        val KEY_RADAR_TRAFFIC_ORIENTATION: Preferences.Key<String> = stringPreferencesKey("tuning_radar_traffic_orientation")
        val KEY_RADAR_LANE_CHANGE_TICK_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_radar_lane_change_tick_enabled")
        val KEY_RADAR_CLOSE_PASS_THRESHOLD: Preferences.Key<Float> = floatPreferencesKey("tuning_radar_close_pass_threshold_m")
        val KEY_DEFAULT_POOL_LENGTH_METERS: Preferences.Key<Float> = floatPreferencesKey("tuning_default_pool_length_meters")
        val KEY_DEFAULT_POOL_LENGTH_UNIT: Preferences.Key<String> = stringPreferencesKey("tuning_default_pool_length_unit")

        internal val PREF_RADAR_ALERT_VOLUME = BoundedPref(KEY_RADAR_ALERT_VOLUME, TuningPreferencesDefaults.RADAR_ALERT_VOLUME, 10, 100)
        internal val PREF_DEFAULT_POOL_LENGTH_METERS = BoundedPref(KEY_DEFAULT_POOL_LENGTH_METERS, TuningPreferencesDefaults.DEFAULT_POOL_LENGTH_METERS, TuningPreferencesDefaults.MIN_POOL_LENGTH_METERS, TuningPreferencesDefaults.MAX_POOL_LENGTH_METERS)
        internal val PREF_FULL_DIM_FACTOR = BoundedPref(KEY_FULL_DIM_FACTOR, TuningPreferencesDefaults.FULL_DIM_FACTOR, TuningPreferencesDefaults.MIN_FULL_DIM_FACTOR, TuningPreferencesDefaults.MAX_FULL_DIM_FACTOR)
        internal val PREF_MEDIUM_DIM_FACTOR = BoundedPref(KEY_MEDIUM_DIM_FACTOR, TuningPreferencesDefaults.MEDIUM_DIM_FACTOR, TuningPreferencesDefaults.MIN_MEDIUM_DIM_FACTOR, TuningPreferencesDefaults.MAX_MEDIUM_DIM_FACTOR)
        internal val PREF_SLOPE_FLAT = BoundedPref(KEY_SLOPE_FLAT, TuningPreferencesDefaults.SLOPE_FLAT_THRESHOLD, TuningPreferencesDefaults.MIN_SLOPE_FLAT, TuningPreferencesDefaults.MAX_SLOPE_FLAT)
        internal val PREF_SLOPE_STEEP = BoundedPref(KEY_SLOPE_STEEP, TuningPreferencesDefaults.SLOPE_STEEP_THRESHOLD, TuningPreferencesDefaults.MIN_SLOPE_STEEP, TuningPreferencesDefaults.MAX_SLOPE_STEEP)
        internal val PREF_WAKEUP_DURATION_SEC = BoundedPref(KEY_WAKEUP_DURATION_SEC, TuningPreferencesDefaults.WAKEUP_DURATION_SEC, TuningPreferencesDefaults.MIN_WAKEUP_SEC, TuningPreferencesDefaults.MAX_WAKEUP_SEC)
        internal val PREF_DOWNWARD_DELAY_SEC = BoundedPref(KEY_DOWNWARD_DELAY_SEC, TuningPreferencesDefaults.DOWNWARD_DELAY_SEC, TuningPreferencesDefaults.MIN_DOWNWARD_DELAY_SEC, TuningPreferencesDefaults.MAX_DOWNWARD_DELAY_SEC)
        internal val PREF_GPS_ACCURACY_THRESHOLD = BoundedPref(KEY_GPS_ACCURACY_THRESHOLD, TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M, TuningPreferencesDefaults.MIN_GPS_ACCURACY_M, TuningPreferencesDefaults.MAX_GPS_ACCURACY_M)
        internal val PREF_ALTITUDE_FILTER_WINDOW = BoundedPref(KEY_ALTITUDE_FILTER_WINDOW, TuningPreferencesDefaults.ALTITUDE_FILTER_WINDOW_SEC, TuningPreferencesDefaults.MIN_ALTITUDE_WINDOW_SEC, TuningPreferencesDefaults.MAX_ALTITUDE_WINDOW_SEC)
        internal val PREF_SLOPE_MIN_SPEED = BoundedPref(KEY_SLOPE_MIN_SPEED, TuningPreferencesDefaults.SLOPE_MIN_SPEED_MPS, TuningPreferencesDefaults.MIN_SLOPE_SPEED_MPS, TuningPreferencesDefaults.MAX_SLOPE_SPEED_MPS)
        internal val PREF_PACE_CEILING_MIN_KM = BoundedPref(KEY_PACE_CEILING_MIN_KM, TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM, TuningPreferencesDefaults.MIN_PACE_CEILING_MIN_KM, TuningPreferencesDefaults.MAX_PACE_CEILING_MIN_KM)
        internal val PREF_TURN_CUE_COUNTDOWN_DISTANCE = BoundedPref(KEY_TURN_CUE_COUNTDOWN_DISTANCE, TuningPreferencesDefaults.TURN_CUE_COUNTDOWN_DISTANCE_METERS, TuningPreferencesDefaults.MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS, TuningPreferencesDefaults.MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS)
        internal val PREF_OFF_ROUTE_CORRIDOR_THRESHOLD = BoundedPref(KEY_OFF_ROUTE_CORRIDOR_THRESHOLD, TuningPreferencesDefaults.OFF_ROUTE_CORRIDOR_THRESHOLD_METERS, TuningPreferencesDefaults.MIN_OFF_ROUTE_CORRIDOR_THRESHOLD_METERS, TuningPreferencesDefaults.MAX_OFF_ROUTE_CORRIDOR_THRESHOLD_METERS)
        internal val PREF_SENSOR_FIELD_CORNER_RADIUS = BoundedPref(KEY_SENSOR_FIELD_CORNER_RADIUS, TuningPreferencesDefaults.SENSOR_FIELD_CORNER_RADIUS, TuningPreferencesDefaults.MIN_SENSOR_FIELD_CORNER_RADIUS, TuningPreferencesDefaults.MAX_SENSOR_FIELD_CORNER_RADIUS)
        internal val PREF_SENSOR_FIELD_BORDER_THICKNESS = BoundedPref(KEY_SENSOR_FIELD_BORDER_THICKNESS, TuningPreferencesDefaults.SENSOR_FIELD_BORDER_THICKNESS, TuningPreferencesDefaults.MIN_SENSOR_FIELD_BORDER_THICKNESS, TuningPreferencesDefaults.MAX_SENSOR_FIELD_BORDER_THICKNESS)
        internal val PREF_SENSOR_FIELD_BORDER_CONTRAST = BoundedPref(KEY_SENSOR_FIELD_BORDER_CONTRAST, TuningPreferencesDefaults.SENSOR_FIELD_BORDER_CONTRAST, TuningPreferencesDefaults.MIN_SENSOR_FIELD_BORDER_CONTRAST, TuningPreferencesDefaults.MAX_SENSOR_FIELD_BORDER_CONTRAST)
        internal val PREF_ROUTE_SELECTION_RADIUS_KM = BoundedPref(KEY_ROUTE_SELECTION_RADIUS_KM, TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM, TuningPreferencesDefaults.MIN_ROUTE_SELECTION_RADIUS_KM, TuningPreferencesDefaults.MAX_ROUTE_SELECTION_RADIUS_KM)
        internal val PREF_NAVIGATION_CUE_TRANSPARENCY = BoundedPref(KEY_NAVIGATION_CUE_TRANSPARENCY, TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY, TuningPreferencesDefaults.MIN_NAVIGATION_CUE_TRANSPARENCY, TuningPreferencesDefaults.MAX_NAVIGATION_CUE_TRANSPARENCY)
        internal val PREF_NAVIGATION_CUE_DISMISS_DURATION_SEC = BoundedPref(KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC, TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC, TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC, TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC)
        internal val PREF_ELEVATION_SMOOTHING_SIGMA_METERS = BoundedPref(KEY_ELEVATION_SMOOTHING_SIGMA_METERS, TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS, TuningPreferencesDefaults.MIN_ELEVATION_SMOOTHING_SIGMA_METERS, TuningPreferencesDefaults.MAX_ELEVATION_SMOOTHING_SIGMA_METERS)
        internal val PREF_CLUSTER_TOL_ENDPOINTS = BoundedPref(KEY_CLUSTER_TOL_ENDPOINTS, TuningPreferencesDefaults.CLUSTER_TOL_ENDPOINTS, TuningPreferencesDefaults.MIN_CLUSTER_TOL_ENDPOINTS, TuningPreferencesDefaults.MAX_CLUSTER_TOL_ENDPOINTS)
        internal val PREF_CLUSTER_TOL_APEX = BoundedPref(KEY_CLUSTER_TOL_APEX, TuningPreferencesDefaults.CLUSTER_TOL_APEX, TuningPreferencesDefaults.MIN_CLUSTER_TOL_APEX, TuningPreferencesDefaults.MAX_CLUSTER_TOL_APEX)
        internal val PREF_CLUSTER_TOL_DISTANCE = BoundedPref(KEY_CLUSTER_TOL_DISTANCE, TuningPreferencesDefaults.CLUSTER_TOL_DISTANCE, TuningPreferencesDefaults.MIN_CLUSTER_TOL_DISTANCE, TuningPreferencesDefaults.MAX_CLUSTER_TOL_DISTANCE)
        internal val PREF_CLUSTER_TOL_ALTITUDE_POS = BoundedPref(KEY_CLUSTER_TOL_ALTITUDE_POS, TuningPreferencesDefaults.CLUSTER_TOL_ALTITUDE_POS, TuningPreferencesDefaults.MIN_CLUSTER_TOL_ALTITUDE_POS, TuningPreferencesDefaults.MAX_CLUSTER_TOL_ALTITUDE_POS)
        internal val PREF_CLUSTER_SCORE_THRESHOLD = BoundedPref(KEY_CLUSTER_SCORE_THRESHOLD, TuningPreferencesDefaults.CLUSTER_SCORE_THRESHOLD, TuningPreferencesDefaults.MIN_CLUSTER_SCORE_THRESHOLD, TuningPreferencesDefaults.MAX_CLUSTER_SCORE_THRESHOLD)
        internal val PREF_MORNING_CHECK_CUTOFF_HOUR = BoundedPref(KEY_MORNING_CHECK_CUTOFF_HOUR, TuningPreferencesDefaults.DEFAULT_MORNING_CHECK_CUTOFF_HOUR, TuningPreferencesDefaults.MIN_MORNING_CHECK_CUTOFF_HOUR, TuningPreferencesDefaults.MAX_MORNING_CHECK_CUTOFF_HOUR)
        internal val PREF_DFA_A1_THRESHOLD_T1 = BoundedPref(KEY_DFA_A1_THRESHOLD_T1, TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1, TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD, TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD)
        internal val PREF_DFA_A1_THRESHOLD_T2 = BoundedPref(KEY_DFA_A1_THRESHOLD_T2, TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2, TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD, TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD)
        internal val PREF_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT = BoundedPref(KEY_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT, TuningPreferencesDefaults.DEFAULT_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT, TuningPreferencesDefaults.MIN_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT, TuningPreferencesDefaults.MAX_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT)
        internal val PREF_AUTO_PAUSE_BIKE_SPEED_THRESHOLD = BoundedPref(KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_METRIC, TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD, TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD)
        internal val PREF_AUTO_PAUSE_BIKE_DEBOUNCE_SEC = BoundedPref(KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_DEBOUNCE_SEC, TuningPreferencesDefaults.MIN_AUTO_PAUSE_BIKE_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_BIKE_DEBOUNCE_SEC)
        internal val PREF_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC = BoundedPref(KEY_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC, TuningPreferencesDefaults.MIN_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC)
        internal val PREF_AUTO_PAUSE_RUN_SPEED_THRESHOLD = BoundedPref(KEY_AUTO_PAUSE_RUN_SPEED_THRESHOLD, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_METRIC, TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD, TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD)
        internal val PREF_AUTO_PAUSE_RUN_DEBOUNCE_SEC = BoundedPref(KEY_AUTO_PAUSE_RUN_DEBOUNCE_SEC, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_DEBOUNCE_SEC, TuningPreferencesDefaults.MIN_AUTO_PAUSE_RUN_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_RUN_DEBOUNCE_SEC)
        internal val PREF_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC = BoundedPref(KEY_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC, TuningPreferencesDefaults.MIN_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC)
        internal val PREF_AUTO_PAUSE_OTHER_SPEED_THRESHOLD = BoundedPref(KEY_AUTO_PAUSE_OTHER_SPEED_THRESHOLD, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_METRIC, TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD, TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD)
        internal val PREF_AUTO_PAUSE_OTHER_DEBOUNCE_SEC = BoundedPref(KEY_AUTO_PAUSE_OTHER_DEBOUNCE_SEC, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_DEBOUNCE_SEC, TuningPreferencesDefaults.MIN_AUTO_PAUSE_OTHER_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_OTHER_DEBOUNCE_SEC)
        internal val PREF_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC = BoundedPref(KEY_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC, TuningPreferencesDefaults.MIN_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC)
        internal val PREF_AUTO_PAUSE_SPEED_THRESHOLD_KMH = BoundedPref(KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_SPEED_THRESHOLD_KMH, TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD_KMH, TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD_KMH)
        internal val PREF_AUTO_PAUSE_DEBOUNCE_SEC = BoundedPref(KEY_AUTO_PAUSE_DEBOUNCE_SEC, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_DEBOUNCE_SEC, TuningPreferencesDefaults.MIN_AUTO_PAUSE_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_DEBOUNCE_SEC)

        private fun clampMediumDim(raw: Float, clampedFullDim: Float): Float =
            raw.coerceIn(clampedFullDim, TuningPreferencesDefaults.MAX_MEDIUM_DIM_FACTOR)

        private fun clampSlopeSteep(raw: Float, clampedSlopeFlat: Float): Float =
            raw.coerceIn(clampedSlopeFlat, TuningPreferencesDefaults.MAX_SLOPE_STEEP)

        private fun clampNavCueDismiss(durationSec: Int): Int =
            if (durationSec == 0) TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
            else PREF_NAVIGATION_CUE_DISMISS_DURATION_SEC.clamp(durationSec)

        private fun clampDfaA1Pair(t1: Float, t2: Float): Pair<Float, Float> {
            var clampedT1 = PREF_DFA_A1_THRESHOLD_T1.clamp(t1)
            var clampedT2 = PREF_DFA_A1_THRESHOLD_T2.clamp(t2)
            if (clampedT1 < clampedT2 + TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP) {
                if (clampedT2 + TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP <= TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD) {
                    clampedT1 = clampedT2 + TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP
                } else {
                    clampedT2 = (clampedT1 - TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP)
                        .coerceAtLeast(TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD)
                }
            }
            return clampedT1 to clampedT2
        }

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
            KEY_POST_WORKOUT_ERTS_SMOOTHING,
            KEY_RAW_SENSOR_RETENTION_POLICY,
            KEY_RECORD_SMARTPHONE_RAW_TELEMETRY,
            KEY_PACE_CEILING_MIN_KM,
            KEY_SHOW_LIVE_CLIMBS,
            KEY_CLIMB_MIN_LENGTH_METERS,
            KEY_CLIMB_MIN_GRADIENT_PERCENT,
            KEY_CLIMB_SCORING_FORMULA,
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
            KEY_ENABLE_MORNING_SLEEP_AND_WELLNESS,
            KEY_ENABLE_SLEEP_DURATION_AND_QUALITY,
            KEY_ENABLE_MORNING_ENERGY,
            KEY_ENABLE_MUSCLE_SORENESS,
            KEY_ENABLE_CONTEXT_TAGS,
            KEY_SHOW_MORNING_CHECK_ON_START_SCREEN,
            KEY_MORNING_CHECK_CUTOFF_HOUR,
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
            KEY_RADAR_LIGHT_CONTROL_MODE,
            KEY_RADAR_BRAKE_LIGHT_ENABLED,
            KEY_RADAR_PELOTON_AUDIO_SYNC,
            KEY_RADAR_SMART_URBAN_MUTE,
            KEY_RADAR_SPATIAL_AUDIO_ENABLED,
            KEY_RADAR_SPEED_PITCH_MODULATION,
            KEY_RADAR_TRAFFIC_ORIENTATION,
            KEY_RADAR_LANE_CHANGE_TICK_ENABLED,
            KEY_RADAR_CLOSE_PASS_THRESHOLD,
            KEY_DEFAULT_POOL_LENGTH_METERS,
            KEY_DEFAULT_POOL_LENGTH_UNIT
        )

        /**
         * Resolves canonical radar tail-light control mode from [KEY_RADAR_LIGHT_CONTROL_MODE] (REQ-CON-056.1).
         */
        @VisibleForTesting
        fun resolveRadarLightControlMode(prefs: Preferences): RadarLightControlMode {
            val rawControlModeStr = prefs[KEY_RADAR_LIGHT_CONTROL_MODE]
            return if (rawControlModeStr != null) {
                safeEnumValueOf(rawControlModeStr, TuningPreferencesDefaults.RADAR_LIGHT_CONTROL_MODE)
            } else {
                TuningPreferencesDefaults.RADAR_LIGHT_CONTROL_MODE
            }
        }

        /**
         * Resolves derived auto-mode flag strictly from canonical [resolveRadarLightControlMode] (REQ-CON-056.1).
         */
        @VisibleForTesting
        fun resolveRadarLightAutoMode(prefs: Preferences): Boolean {
            return resolveRadarLightControlMode(prefs) == RadarLightControlMode.SMART_AUTOMATION
        }

        /**
         * Resolves canonical physical tail-light mode from [KEY_RADAR_LIGHT_DEFAULT_MODE] (REQ-CON-056.1).
         */
        @VisibleForTesting
        fun resolveRadarLightDefaultMode(prefs: Preferences): RadarLightMode {
            val raw = prefs[KEY_RADAR_LIGHT_DEFAULT_MODE]
            if (raw != null) {
                return safeEnumValueOf(raw, TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE)
            }
            if (resolveRadarLightControlMode(prefs) == RadarLightControlMode.MANUAL) {
                return RadarLightMode.OFF
            }
            return TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE
        }

        /**
         * Migrates legacy 5-key radar-light preferences in-place onto the canonical 2-key model
         * while preserving the user's prior effective behavior (REQ-CON-056.1, Coding Standards §5.6).
         */
        @Suppress("DEPRECATION")
        @VisibleForTesting
        internal fun migrateLegacyRadarLightPreferences(prefs: MutablePreferences) {
            val hasAnyLegacy = LEGACY_RADAR_LIGHT_KEYS.any { prefs.contains(it) }
            if (!hasAnyLegacy) return

            val resolvedControlMode = run {
                val rawControl = prefs[KEY_RADAR_LIGHT_CONTROL_MODE]
                if (rawControl != null) {
                    safeEnumValueOf(rawControl, TuningPreferencesDefaults.RADAR_LIGHT_CONTROL_MODE)
                } else {
                    val legacyAutoMode = prefs[KEY_RADAR_LIGHT_AUTO_MODE]
                    if (legacyAutoMode != null) {
                        if (legacyAutoMode) RadarLightControlMode.SMART_AUTOMATION else RadarLightControlMode.FIXED_WORKOUT_MODE
                    } else {
                        val legacySmart = prefs[KEY_RADAR_SMART_LIGHT_AUTOMATION]
                        val legacyAuto = prefs[KEY_RADAR_AUTO_LIGHT_CONTROL]
                        if (legacySmart == true) {
                            RadarLightControlMode.SMART_AUTOMATION
                        } else if (legacyAuto == false) {
                            RadarLightControlMode.MANUAL
                        } else {
                            // Fallthrough when legacySmart == false/null and legacyAuto == true/null
                            // preserves historical RADAR_AUTO_LIGHT_CONTROL = true (FIXED_WORKOUT_MODE)
                            RadarLightControlMode.FIXED_WORKOUT_MODE
                        }
                    }
                }
            }

            prefs[KEY_RADAR_LIGHT_CONTROL_MODE] = resolvedControlMode.name
            if (prefs[KEY_RADAR_LIGHT_DEFAULT_MODE] == null && resolvedControlMode == RadarLightControlMode.MANUAL) {
                prefs[KEY_RADAR_LIGHT_DEFAULT_MODE] = RadarLightMode.OFF.name
            }
            for (legacyKey in LEGACY_RADAR_LIGHT_KEYS) {
                prefs.remove(legacyKey)
            }
        }

        private val LEGACY_CLUSTER_SP_KEYS = listOf(
            TrainingApplication.SP_CLUSTER_TOL_ENDPOINTS,
            TrainingApplication.SP_CLUSTER_TOL_APEX,
            TrainingApplication.SP_CLUSTER_TOL_DISTANCE,
            TrainingApplication.SP_CLUSTER_TOL_ALTITUDE_POS,
            TrainingApplication.SP_CLUSTER_USE_SPORT_TYPE,
            TrainingApplication.SP_CLUSTER_USE_ALTITUDE_POS,
            TrainingApplication.SP_CLUSTER_SCORE_THRESHOLD
        )

        /**
         * One-time [DataMigration] consolidating legacy radar-light keys, purging [LEGACY_MAP_CAMERA_KEYS],
         * and migrating legacy SharedPreferences cluster tolerances into DataStore (REQ-CON-056.1).
         */
        @Suppress("DEPRECATION")
        @VisibleForTesting
        fun createPreferencesMigration(
            context: Context? = null,
            sharedPreferencesProvider: (() -> SharedPreferences?)? = null
        ): DataMigration<Preferences> {
            val resolveSp: () -> SharedPreferences? = {
                try {
                    sharedPreferencesProvider?.invoke()
                        ?: context?.let { PreferenceManager.getDefaultSharedPreferences(it.applicationContext ?: it) }
                } catch (_: Throwable) {
                    null
                }
            }
            return object : DataMigration<Preferences> {
                override suspend fun shouldMigrate(currentData: Preferences): Boolean {
                    if (LEGACY_RADAR_LIGHT_KEYS.any { currentData.contains(it) }) return true
                    if (LEGACY_MAP_CAMERA_KEYS.any { currentData.contains(it) }) return true
                    val sp = resolveSp() ?: return false
                    return LEGACY_CLUSTER_SP_KEYS.any { sp.contains(it) }
                }

                override suspend fun migrate(currentData: Preferences): Preferences {
                    val mutable = currentData.toMutablePreferences()
                    migrateLegacyRadarLightPreferences(mutable)
                    purgeLegacyMapCameraKeys(mutable)
                    val sp = resolveSp()
                    if (sp != null) {
                        if (!mutable.contains(KEY_CLUSTER_TOL_ENDPOINTS) && sp.contains(TrainingApplication.SP_CLUSTER_TOL_ENDPOINTS)) {
                            PREF_CLUSTER_TOL_ENDPOINTS.write(mutable, sp.getFloat(TrainingApplication.SP_CLUSTER_TOL_ENDPOINTS, TuningPreferencesDefaults.CLUSTER_TOL_ENDPOINTS))
                        }
                        if (!mutable.contains(KEY_CLUSTER_TOL_APEX) && sp.contains(TrainingApplication.SP_CLUSTER_TOL_APEX)) {
                            PREF_CLUSTER_TOL_APEX.write(mutable, sp.getFloat(TrainingApplication.SP_CLUSTER_TOL_APEX, TuningPreferencesDefaults.CLUSTER_TOL_APEX))
                        }
                        if (!mutable.contains(KEY_CLUSTER_TOL_DISTANCE) && sp.contains(TrainingApplication.SP_CLUSTER_TOL_DISTANCE)) {
                            PREF_CLUSTER_TOL_DISTANCE.write(mutable, sp.getFloat(TrainingApplication.SP_CLUSTER_TOL_DISTANCE, TuningPreferencesDefaults.CLUSTER_TOL_DISTANCE))
                        }
                        if (!mutable.contains(KEY_CLUSTER_TOL_ALTITUDE_POS) && sp.contains(TrainingApplication.SP_CLUSTER_TOL_ALTITUDE_POS)) {
                            PREF_CLUSTER_TOL_ALTITUDE_POS.write(mutable, sp.getFloat(TrainingApplication.SP_CLUSTER_TOL_ALTITUDE_POS, TuningPreferencesDefaults.CLUSTER_TOL_ALTITUDE_POS))
                        }
                        if (!mutable.contains(KEY_CLUSTER_USE_SPORT_TYPE) && sp.contains(TrainingApplication.SP_CLUSTER_USE_SPORT_TYPE)) {
                            mutable[KEY_CLUSTER_USE_SPORT_TYPE] = sp.getBoolean(TrainingApplication.SP_CLUSTER_USE_SPORT_TYPE, TuningPreferencesDefaults.CLUSTER_USE_SPORT_TYPE)
                        }
                        if (!mutable.contains(KEY_CLUSTER_USE_ALTITUDE_POS) && sp.contains(TrainingApplication.SP_CLUSTER_USE_ALTITUDE_POS)) {
                            mutable[KEY_CLUSTER_USE_ALTITUDE_POS] = sp.getBoolean(TrainingApplication.SP_CLUSTER_USE_ALTITUDE_POS, TuningPreferencesDefaults.CLUSTER_USE_ALTITUDE_POS)
                        }
                        if (!mutable.contains(KEY_CLUSTER_SCORE_THRESHOLD) && sp.contains(TrainingApplication.SP_CLUSTER_SCORE_THRESHOLD)) {
                            PREF_CLUSTER_SCORE_THRESHOLD.write(mutable, sp.getFloat(TrainingApplication.SP_CLUSTER_SCORE_THRESHOLD, TuningPreferencesDefaults.CLUSTER_SCORE_THRESHOLD))
                        }
                    }
                    return mutable.toPreferences()
                }

                override suspend fun cleanUp() {
                    val sp = resolveSp() ?: return
                    val hasAny = LEGACY_CLUSTER_SP_KEYS.any { sp.contains(it) }
                    if (!hasAny) return
                    val editor = sp.edit()
                    for (spKey in LEGACY_CLUSTER_SP_KEYS) {
                        editor.remove(spKey)
                    }
                    editor.apply()
                }
            }
        }

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

            val bikeThreshold = PREF_AUTO_PAUSE_BIKE_SPEED_THRESHOLD.read(prefs, legacyThreshold ?: PREF_AUTO_PAUSE_BIKE_SPEED_THRESHOLD.default)
            val bikeDebounce = PREF_AUTO_PAUSE_BIKE_DEBOUNCE_SEC.read(prefs, legacyDebounce ?: PREF_AUTO_PAUSE_BIKE_DEBOUNCE_SEC.default)
            val bikeResumeDebounce = PREF_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC.read(prefs)

            val runThreshold = PREF_AUTO_PAUSE_RUN_SPEED_THRESHOLD.read(prefs, legacyThreshold ?: PREF_AUTO_PAUSE_RUN_SPEED_THRESHOLD.default)
            val runDebounce = PREF_AUTO_PAUSE_RUN_DEBOUNCE_SEC.read(prefs, legacyDebounce ?: PREF_AUTO_PAUSE_RUN_DEBOUNCE_SEC.default)
            val runResumeDebounce = PREF_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC.read(prefs)

            val otherThreshold = PREF_AUTO_PAUSE_OTHER_SPEED_THRESHOLD.read(prefs, legacyThreshold ?: PREF_AUTO_PAUSE_OTHER_SPEED_THRESHOLD.default)
            val otherDebounce = PREF_AUTO_PAUSE_OTHER_DEBOUNCE_SEC.read(prefs, legacyDebounce ?: PREF_AUTO_PAUSE_OTHER_DEBOUNCE_SEC.default)
            val otherResumeDebounce = PREF_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC.read(prefs)

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
                INSTANCE ?: TuningPreferencesDataStore(
                    context.applicationContext ?: context,
                    CoroutineScope(SupervisorJob() + Dispatchers.Default)
                ).also { INSTANCE = it }
            }
        }

        @VisibleForTesting
        fun createForTesting(
            context: Context,
            testScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            dataStoreOverride: DataStore<Preferences>? = null
        ): TuningPreferencesDataStore {
            return TuningPreferencesDataStore(context.applicationContext ?: context, testScope, dataStoreOverride)
        }

        @VisibleForTesting
        fun setInstanceForTesting(instance: TuningPreferencesDataStore?) {
            synchronized(this) {
                INSTANCE = instance
            }
        }

        @JvmStatic
        fun hasInitializedInstance(): Boolean = INSTANCE != null
    }

    private val _tuningConfigStateFlow = MutableStateFlow(TuningConfig())
    val tuningConfigStateFlow: StateFlow<TuningConfig> = _tuningConfigStateFlow.asStateFlow()

    fun getTuningConfig(): TuningConfig = tuningConfigStateFlow.value

    private suspend inline fun editAndSync(crossinline transform: (MutablePreferences) -> Unit) {
        val updated = dataStore.edit { prefs ->
            transform(prefs)
        }
        val cfg = mapPreferencesToConfig(updated)
        _tuningConfigStateFlow.value = cfg
        com.atrainingtracker.banalservice.devices.radar.SmartBrakeLightDetector.isEnabled = cfg.radarBrakeLightEnabled
        com.atrainingtracker.banalservice.devices.radar.RadarLightController.onPreferencesUpdated(cfg)
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

    val tuningConfigFlow: Flow<TuningConfig> = dataStore.data.map { prefs ->
        mapPreferencesToConfig(prefs)
    }

    @VisibleForTesting
    internal fun mapPreferencesToConfig(prefs: Preferences): TuningConfig {
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

        val radarAlertVolume = PREF_RADAR_ALERT_VOLUME.read(prefs)

        val radarSpeakerBoost = prefs[KEY_RADAR_SPEAKER_BOOST] ?: TuningPreferencesDefaults.RADAR_SPEAKER_BOOST

        val rawAudioRouteStr = prefs[KEY_RADAR_AUDIO_ROUTE]
        val radarAudioRoute = safeEnumValueOf(rawAudioRouteStr, TuningPreferencesDefaults.RADAR_AUDIO_ROUTE)

        val radarHapticAlert = prefs[KEY_RADAR_HAPTIC_ALERT] ?: TuningPreferencesDefaults.RADAR_HAPTIC_ALERT
        val radarLightControlMode = resolveRadarLightControlMode(prefs)
        val radarLightDefaultMode = resolveRadarLightDefaultMode(prefs)
        val radarBrakeLightEnabled = prefs[KEY_RADAR_BRAKE_LIGHT_ENABLED] ?: TuningPreferencesDefaults.RADAR_BRAKE_LIGHT_ENABLED
        val radarPelotonAudioSync = prefs[KEY_RADAR_PELOTON_AUDIO_SYNC] ?: TuningPreferencesDefaults.RADAR_PELOTON_AUDIO_SYNC
        val radarSmartUrbanMute = prefs[KEY_RADAR_SMART_URBAN_MUTE] ?: TuningPreferencesDefaults.RADAR_SMART_URBAN_MUTE
        val radarSpatialAudioEnabled = prefs[KEY_RADAR_SPATIAL_AUDIO_ENABLED] ?: TuningPreferencesDefaults.RADAR_SPATIAL_AUDIO_ENABLED
        val radarSpeedPitchModulation = prefs[KEY_RADAR_SPEED_PITCH_MODULATION] ?: TuningPreferencesDefaults.RADAR_SPEED_PITCH_MODULATION
        val rawTrafficOrientationStr = prefs[KEY_RADAR_TRAFFIC_ORIENTATION]
        val radarTrafficOrientation = safeEnumValueOf(rawTrafficOrientationStr, TuningPreferencesDefaults.RADAR_TRAFFIC_ORIENTATION)
        val radarLaneChangeTickEnabled = prefs[KEY_RADAR_LANE_CHANGE_TICK_ENABLED] ?: TuningPreferencesDefaults.RADAR_LANE_CHANGE_TICK_ENABLED
        val radarClosePassThresholdM = prefs[KEY_RADAR_CLOSE_PASS_THRESHOLD] ?: TuningPreferencesDefaults.RADAR_CLOSE_PASS_THRESHOLD_M
        val defaultPoolLengthMeters = PREF_DEFAULT_POOL_LENGTH_METERS.read(prefs)
        val defaultPoolLengthUnit = safeEnumValueOf(prefs[KEY_DEFAULT_POOL_LENGTH_UNIT], TuningPreferencesDefaults.DEFAULT_POOL_LENGTH_UNIT)

        val rawFontWeightStr = prefs[KEY_COCKPIT_FONT_WEIGHT]
        val cockpitFontWeight = safeEnumValueOf(rawFontWeightStr, TuningPreferencesDefaults.COCKPIT_FONT_WEIGHT)
        val clampedFullDim = PREF_FULL_DIM_FACTOR.read(prefs)
        val clampedMediumDim = clampMediumDim(prefs[KEY_MEDIUM_DIM_FACTOR] ?: PREF_MEDIUM_DIM_FACTOR.default, clampedFullDim)

        val clampedSlopeFlat = PREF_SLOPE_FLAT.read(prefs)
        val clampedSlopeSteep = clampSlopeSteep(prefs[KEY_SLOPE_STEEP] ?: PREF_SLOPE_STEEP.default, clampedSlopeFlat)

        val clampedWakeup = PREF_WAKEUP_DURATION_SEC.read(prefs)
        val clampedDownward = PREF_DOWNWARD_DELAY_SEC.read(prefs)
        val clampedAccuracy = PREF_GPS_ACCURACY_THRESHOLD.read(prefs)
        val clampedAltWindow = PREF_ALTITUDE_FILTER_WINDOW.read(prefs)
        val clampedSlopeSpeed = PREF_SLOPE_MIN_SPEED.read(prefs)
        val clampedPaceCeiling = PREF_PACE_CEILING_MIN_KM.read(prefs)

        val showLiveClimbs = prefs[KEY_SHOW_LIVE_CLIMBS] ?: TuningPreferencesDefaults.SHOW_LIVE_CLIMBS
        val climbMinLength = prefs[KEY_CLIMB_MIN_LENGTH_METERS] ?: TuningPreferencesDefaults.CLIMB_MIN_LENGTH_METERS
        val climbMinGradient = prefs[KEY_CLIMB_MIN_GRADIENT_PERCENT] ?: TuningPreferencesDefaults.CLIMB_MIN_GRADIENT_PERCENT
        val climbScoringFormula = prefs[KEY_CLIMB_SCORING_FORMULA]?.let { name ->
            try {
                ClimbScoringFormula.valueOf(name)
            } catch (e: Exception) {
                TuningPreferencesDefaults.DEFAULT_CLIMB_SCORING_FORMULA
            }
        } ?: TuningPreferencesDefaults.DEFAULT_CLIMB_SCORING_FORMULA

        val turnPromptsEnabled = prefs[KEY_TURN_PROMPTS_ENABLED] ?: TuningPreferencesDefaults.TURN_PROMPTS_ENABLED
        val turnAudioAlertsEnabled = prefs[KEY_TURN_AUDIO_ALERTS_ENABLED] ?: TuningPreferencesDefaults.TURN_AUDIO_ALERTS_ENABLED
        val clampedTurnCountdown = PREF_TURN_CUE_COUNTDOWN_DISTANCE.read(prefs)
        val clampedOffRoute = PREF_OFF_ROUTE_CORRIDOR_THRESHOLD.read(prefs)
        val clampedCornerRadius = PREF_SENSOR_FIELD_CORNER_RADIUS.read(prefs)
        val clampedBorderThickness = PREF_SENSOR_FIELD_BORDER_THICKNESS.read(prefs)
        val clampedBorderContrast = PREF_SENSOR_FIELD_BORDER_CONTRAST.read(prefs)
        val clampedRadiusKm = PREF_ROUTE_SELECTION_RADIUS_KM.read(prefs)

        val autoPausePrefs = mapAutoPausePreferences(prefs)

        return TuningConfig(
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
            postWorkoutErtsSmoothingEnabled = prefs[KEY_POST_WORKOUT_ERTS_SMOOTHING] ?: TuningPreferencesDefaults.DEFAULT_POST_WORKOUT_ERTS_SMOOTHING_ENABLED,
            rawSensorRetentionPolicy = safeEnumValueOf(prefs[KEY_RAW_SENSOR_RETENTION_POLICY], TuningPreferencesDefaults.RAW_SENSOR_RETENTION_POLICY),
            recordSmartphoneRawTelemetryEnabled = prefs[KEY_RECORD_SMARTPHONE_RAW_TELEMETRY] ?: TuningPreferencesDefaults.DEFAULT_RECORD_SMARTPHONE_RAW_TELEMETRY_ENABLED,
            paceCeilingMinKm = clampedPaceCeiling,
            showLiveClimbs = showLiveClimbs,
            climbMinLengthMeters = climbMinLength,
            climbMinGradientPercent = climbMinGradient,
            climbScoringFormula = climbScoringFormula,
            turnPromptsEnabled = turnPromptsEnabled,
            turnAudioAlertsEnabled = turnAudioAlertsEnabled,
            turnCueCountdownDistanceMeters = clampedTurnCountdown,
            offRouteCorridorThresholdMeters = clampedOffRoute,
            sensorFieldCornerRadius = clampedCornerRadius,
            sensorFieldBorderThickness = clampedBorderThickness,
            sensorFieldBorderContrast = clampedBorderContrast,
            routeSelectionRadiusKm = clampedRadiusKm,
            navigationCueTransparency = PREF_NAVIGATION_CUE_TRANSPARENCY.read(prefs),
            navigationCueDismissDurationSec = clampNavCueDismiss(prefs[KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC] ?: PREF_NAVIGATION_CUE_DISMISS_DURATION_SEC.default),
            elevationSmoothingSigmaMeters = PREF_ELEVATION_SMOOTHING_SIGMA_METERS.read(prefs),
            mapFollowMeInitialZoom = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM,
            mapFollowMeSpeedZoomEnabled = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED,
            mapFollowMeCruisingZoom = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_CRUISING_ZOOM,
            mapFollowMeTiltAngle = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE,
            mapFollowMeLookaheadPaddingPercent = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT,
            mapFollowMeSpeedSmoothingAlpha = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA,
            mapFollowMeBearingSmoothingAlpha = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA,
            suppressAntMissingAdapterDialog = prefs[KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG] ?: TuningPreferencesDefaults.SUPPRESS_ANT_MISSING_ADAPTER_DIALOG,
            autoFollowUniqueRouteEnabled = prefs[KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED] ?: TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED,
            clusterTolEndpoints = PREF_CLUSTER_TOL_ENDPOINTS.read(prefs, TuningPreferencesDefaults.CLUSTER_TOL_ENDPOINTS),
            clusterTolApex = PREF_CLUSTER_TOL_APEX.read(prefs, TuningPreferencesDefaults.CLUSTER_TOL_APEX),
            clusterTolDistance = PREF_CLUSTER_TOL_DISTANCE.read(prefs, TuningPreferencesDefaults.CLUSTER_TOL_DISTANCE),
            clusterTolAltitudePos = PREF_CLUSTER_TOL_ALTITUDE_POS.read(prefs, TuningPreferencesDefaults.CLUSTER_TOL_ALTITUDE_POS),
            clusterUseSportType = prefs[KEY_CLUSTER_USE_SPORT_TYPE] ?: TuningPreferencesDefaults.CLUSTER_USE_SPORT_TYPE,
            clusterUseAltitudePos = prefs[KEY_CLUSTER_USE_ALTITUDE_POS] ?: TuningPreferencesDefaults.CLUSTER_USE_ALTITUDE_POS,
            clusterScoreThreshold = PREF_CLUSTER_SCORE_THRESHOLD.read(prefs, TuningPreferencesDefaults.CLUSTER_SCORE_THRESHOLD),
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
            enableMorningSleepAndWellness = prefs[KEY_ENABLE_MORNING_SLEEP_AND_WELLNESS] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_MORNING_SLEEP_AND_WELLNESS,
            enableSleepDurationAndQuality = prefs[KEY_ENABLE_SLEEP_DURATION_AND_QUALITY] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_SLEEP_DURATION_AND_QUALITY,
            enableMorningEnergy = prefs[KEY_ENABLE_MORNING_ENERGY] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_MORNING_ENERGY,
            enableMuscleSoreness = prefs[KEY_ENABLE_MUSCLE_SORENESS] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_MUSCLE_SORENESS,
            enableContextTags = prefs[KEY_ENABLE_CONTEXT_TAGS] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_CONTEXT_TAGS,
            showMorningCheckOnStartScreen = prefs[KEY_SHOW_MORNING_CHECK_ON_START_SCREEN] ?: TuningPreferencesDefaults.DEFAULT_SHOW_MORNING_CHECK_ON_START_SCREEN,
            morningCheckCutoffHour = PREF_MORNING_CHECK_CUTOFF_HOUR.read(prefs),
            dfaA1ThresholdT1 = prefs[KEY_DFA_A1_THRESHOLD_T1] ?: TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1,
            dfaA1ThresholdT2 = prefs[KEY_DFA_A1_THRESHOLD_T2] ?: TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2,
            hrvArtifactGateThresholdPercent = prefs[KEY_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT] ?: TuningPreferencesDefaults.DEFAULT_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT,
            radarEdgeSide = radarEdgeSide,
            radarClearMode = radarClearMode,
            radarAudioAlertMode = radarAudioAlertMode,
            radarAlertVolume = radarAlertVolume,
            radarSpeakerBoost = radarSpeakerBoost,
            radarAudioRoute = radarAudioRoute,
            radarHapticAlert = radarHapticAlert,
            radarLightDefaultMode = radarLightDefaultMode,
            radarLightControlMode = radarLightControlMode,
            radarBrakeLightEnabled = radarBrakeLightEnabled,
            radarPelotonAudioSync = radarPelotonAudioSync,
            radarSmartUrbanMute = radarSmartUrbanMute,
            radarSpatialAudioEnabled = radarSpatialAudioEnabled,
            radarSpeedPitchModulation = radarSpeedPitchModulation,
            radarTrafficOrientation = radarTrafficOrientation,
            radarLaneChangeTickEnabled = radarLaneChangeTickEnabled,
            radarClosePassThresholdM = radarClosePassThresholdM,
            defaultPoolLengthMeters = defaultPoolLengthMeters,
            defaultPoolLengthUnit = defaultPoolLengthUnit
        )
    }

    init {
        appScope.launch {
            tuningConfigFlow.collect { cfg ->
                _tuningConfigStateFlow.value = cfg
                com.atrainingtracker.banalservice.devices.radar.SmartBrakeLightDetector.isEnabled = cfg.radarBrakeLightEnabled
                com.atrainingtracker.banalservice.devices.radar.RadarLightController.onPreferencesUpdated(cfg)
            }
        }
    }

    val enableCogganPowerMetricsFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_COGGAN_POWER_METRICS] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_COGGAN_POWER_METRICS
    }

    val enablePmcModelFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_PMC_MODEL] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_PMC_MODEL
    }

    val enableAerobicEfficiencyModelFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_AEROBIC_EFFICIENCY_MODEL] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_AEROBIC_EFFICIENCY_MODEL
    }

    val enableCriticalPowerModelFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_CRITICAL_POWER_MODEL] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_CRITICAL_POWER_MODEL
    }

    val enableCardioPowerDynamicsFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_CARDIO_POWER_DYNAMICS] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_CARDIO_POWER_DYNAMICS
    }

    val enableMorningSleepAndWellnessFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_MORNING_SLEEP_AND_WELLNESS] ?: TuningPreferencesDefaults.DEFAULT_ENABLE_MORNING_SLEEP_AND_WELLNESS
    }

    suspend fun setEnableMorningSleepAndWellness(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLE_MORNING_SLEEP_AND_WELLNESS] = enabled
        }
    }

    val showMorningCheckOnStartScreenFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHOW_MORNING_CHECK_ON_START_SCREEN] ?: TuningPreferencesDefaults.DEFAULT_SHOW_MORNING_CHECK_ON_START_SCREEN
    }

    suspend fun setShowMorningCheckOnStartScreen(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_MORNING_CHECK_ON_START_SCREEN] = enabled
        }
    }

    val morningCheckCutoffHourFlow: Flow<Int> = dataStore.data.map { prefs ->
        PREF_MORNING_CHECK_CUTOFF_HOUR.read(prefs)
    }

    suspend fun setMorningCheckCutoffHour(hour: Int) {
        dataStore.edit { prefs ->
            PREF_MORNING_CHECK_CUTOFF_HOUR.write(prefs, hour)
        }
    }

    suspend fun setEnableCardioPowerDynamics(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLE_CARDIO_POWER_DYNAMICS] = enabled
        }
    }

    suspend fun setEnableCogganPowerMetrics(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLE_COGGAN_POWER_METRICS] = enabled
        }
    }

    suspend fun setEnablePmcModel(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLE_PMC_MODEL] = enabled
        }
    }

    suspend fun setEnableAerobicEfficiencyModel(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLE_AEROBIC_EFFICIENCY_MODEL] = enabled
        }
    }

    suspend fun setEnableCriticalPowerModel(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLE_CRITICAL_POWER_MODEL] = enabled
        }
    }

    val dfaA1ThresholdT1Flow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_DFA_A1_THRESHOLD_T1] ?: TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1
    }

    val dfaA1ThresholdT2Flow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_DFA_A1_THRESHOLD_T2] ?: TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2
    }

    val hrvArtifactGateThresholdPercentFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT] ?: TuningPreferencesDefaults.DEFAULT_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT
    }

    suspend fun setHrvArtifactGateThresholdPercent(percent: Float) {
        dataStore.edit { prefs ->
            PREF_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT.write(prefs, percent)
        }
    }

    suspend fun setDfaA1ThresholdT1(threshold: Float) {
        dataStore.edit { prefs ->
            val currentT2 = prefs[KEY_DFA_A1_THRESHOLD_T2] ?: TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2
            val minT1 = (currentT2 + TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP).coerceAtMost(TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD)
            prefs[KEY_DFA_A1_THRESHOLD_T1] = threshold.coerceIn(minT1, TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD)
        }
    }

    suspend fun setDfaA1ThresholdT2(threshold: Float) {
        dataStore.edit { prefs ->
            val currentT1 = prefs[KEY_DFA_A1_THRESHOLD_T1] ?: TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1
            val maxT2 = (currentT1 - TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP).coerceAtLeast(TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD)
            prefs[KEY_DFA_A1_THRESHOLD_T2] = threshold.coerceIn(TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD, maxT2)
        }
    }

    suspend fun setDfaA1Thresholds(t1: Float, t2: Float) {
        val (clampedT1, clampedT2) = clampDfaA1Pair(t1, t2)
        dataStore.edit { prefs ->
            prefs[KEY_DFA_A1_THRESHOLD_T1] = clampedT1
            prefs[KEY_DFA_A1_THRESHOLD_T2] = clampedT2
        }
    }

    suspend fun saveTuningConfig(config: TuningConfig) {
        val clampedFullDim = PREF_FULL_DIM_FACTOR.clamp(config.fullDimFactor)
        val clampedMediumDim = clampMediumDim(config.mediumDimFactor, clampedFullDim)
        val clampedSlopeFlat = PREF_SLOPE_FLAT.clamp(config.slopeFlatThreshold)
        val clampedSlopeSteep = clampSlopeSteep(config.slopeSteepThreshold, clampedSlopeFlat)
        val clampedClusterTolEndpoints = PREF_CLUSTER_TOL_ENDPOINTS.clamp(config.clusterTolEndpoints)
        val clampedClusterTolApex = PREF_CLUSTER_TOL_APEX.clamp(config.clusterTolApex)
        val clampedClusterTolDistance = PREF_CLUSTER_TOL_DISTANCE.clamp(config.clusterTolDistance)
        val clampedClusterTolAltitudePos = PREF_CLUSTER_TOL_ALTITUDE_POS.clamp(config.clusterTolAltitudePos)
        val clampedClusterScoreThreshold = PREF_CLUSTER_SCORE_THRESHOLD.clamp(config.clusterScoreThreshold)
        val (clampedDfaA1T1, clampedDfaA1T2) = clampDfaA1Pair(config.dfaA1ThresholdT1, config.dfaA1ThresholdT2)

        editAndSync { prefs ->
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
            PREF_WAKEUP_DURATION_SEC.write(prefs, config.wakeupDurationSec)
            PREF_DOWNWARD_DELAY_SEC.write(prefs, config.downwardDelaySec)
            PREF_GPS_ACCURACY_THRESHOLD.write(prefs, config.gpsAccuracyThresholdMeters)
            PREF_ALTITUDE_FILTER_WINDOW.write(prefs, config.altitudeFilterWindowSec)
            PREF_SLOPE_MIN_SPEED.write(prefs, config.slopeMinSpeedMps)
            prefs[KEY_POST_WORKOUT_ERTS_SMOOTHING] = config.postWorkoutErtsSmoothingEnabled
            prefs[KEY_RAW_SENSOR_RETENTION_POLICY] = config.rawSensorRetentionPolicy.name
            prefs[KEY_RECORD_SMARTPHONE_RAW_TELEMETRY] = config.recordSmartphoneRawTelemetryEnabled
            PREF_PACE_CEILING_MIN_KM.write(prefs, config.paceCeilingMinKm)
            prefs[KEY_SHOW_LIVE_CLIMBS] = config.showLiveClimbs
            prefs[KEY_CLIMB_MIN_LENGTH_METERS] = config.climbMinLengthMeters
            prefs[KEY_CLIMB_MIN_GRADIENT_PERCENT] = config.climbMinGradientPercent
            prefs[KEY_CLIMB_SCORING_FORMULA] = config.climbScoringFormula.name
            prefs[KEY_TURN_PROMPTS_ENABLED] = config.turnPromptsEnabled
            prefs[KEY_TURN_AUDIO_ALERTS_ENABLED] = config.turnAudioAlertsEnabled
            PREF_TURN_CUE_COUNTDOWN_DISTANCE.write(prefs, config.turnCueCountdownDistanceMeters)
            PREF_OFF_ROUTE_CORRIDOR_THRESHOLD.write(prefs, config.offRouteCorridorThresholdMeters)
            PREF_SENSOR_FIELD_CORNER_RADIUS.write(prefs, config.sensorFieldCornerRadius)
            PREF_SENSOR_FIELD_BORDER_THICKNESS.write(prefs, config.sensorFieldBorderThickness)
            PREF_SENSOR_FIELD_BORDER_CONTRAST.write(prefs, config.sensorFieldBorderContrast)
            PREF_ROUTE_SELECTION_RADIUS_KM.write(prefs, config.routeSelectionRadiusKm)
            PREF_NAVIGATION_CUE_TRANSPARENCY.write(prefs, config.navigationCueTransparency)
            prefs[KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC] = clampNavCueDismiss(config.navigationCueDismissDurationSec)
            PREF_ELEVATION_SMOOTHING_SIGMA_METERS.write(prefs, config.elevationSmoothingSigmaMeters)
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
            prefs[KEY_START_IN_AUTO_PAUSE_ENABLED] = config.startInAutoPauseEnabled
            PREF_AUTO_PAUSE_BIKE_SPEED_THRESHOLD.write(prefs, config.autoPauseBikeSpeedThreshold)
            PREF_AUTO_PAUSE_BIKE_DEBOUNCE_SEC.write(prefs, config.autoPauseBikeDebounceSec)
            PREF_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC.write(prefs, config.autoPauseBikeResumeDebounceSec)
            PREF_AUTO_PAUSE_RUN_SPEED_THRESHOLD.write(prefs, config.autoPauseRunSpeedThreshold)
            PREF_AUTO_PAUSE_RUN_DEBOUNCE_SEC.write(prefs, config.autoPauseRunDebounceSec)
            PREF_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC.write(prefs, config.autoPauseRunResumeDebounceSec)
            PREF_AUTO_PAUSE_OTHER_SPEED_THRESHOLD.write(prefs, config.autoPauseOtherSpeedThreshold)
            PREF_AUTO_PAUSE_OTHER_DEBOUNCE_SEC.write(prefs, config.autoPauseOtherDebounceSec)
            PREF_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC.write(prefs, config.autoPauseOtherResumeDebounceSec)
            prefs[KEY_ENABLE_COGGAN_POWER_METRICS] = config.enableCogganPowerMetrics
            prefs[KEY_SHOW_COGGAN_POWER_IN_WORKOUT_SUMMARY] = config.showCogganPowerInWorkoutSummary
            prefs[KEY_ENABLE_PMC_MODEL] = config.enablePmcModel
            prefs[KEY_ENABLE_AEROBIC_EFFICIENCY_MODEL] = config.enableAerobicEfficiencyModel
            prefs[KEY_SHOW_AEROBIC_EFFICIENCY_IN_WORKOUT_SUMMARY] = config.showAerobicEfficiencyInWorkoutSummary
            prefs[KEY_ENABLE_CRITICAL_POWER_MODEL] = config.enableCriticalPowerModel
            prefs[KEY_SHOW_CRITICAL_POWER_IN_WORKOUT_SUMMARY] = config.showCriticalPowerInWorkoutSummary
            prefs[KEY_ENABLE_CARDIO_POWER_DYNAMICS] = config.enableCardioPowerDynamics
            prefs[KEY_SHOW_CARDIO_POWER_IN_WORKOUT_SUMMARY] = config.showCardioPowerInWorkoutSummary
            prefs[KEY_ENABLE_MORNING_SLEEP_AND_WELLNESS] = config.enableMorningSleepAndWellness
            prefs[KEY_ENABLE_SLEEP_DURATION_AND_QUALITY] = config.enableSleepDurationAndQuality
            prefs[KEY_ENABLE_MORNING_ENERGY] = config.enableMorningEnergy
            prefs[KEY_ENABLE_MUSCLE_SORENESS] = config.enableMuscleSoreness
            prefs[KEY_ENABLE_CONTEXT_TAGS] = config.enableContextTags
            prefs[KEY_SHOW_MORNING_CHECK_ON_START_SCREEN] = config.showMorningCheckOnStartScreen
            PREF_MORNING_CHECK_CUTOFF_HOUR.write(prefs, config.morningCheckCutoffHour)
            prefs[KEY_DFA_A1_THRESHOLD_T1] = clampedDfaA1T1
            prefs[KEY_DFA_A1_THRESHOLD_T2] = clampedDfaA1T2
            PREF_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT.write(prefs, config.hrvArtifactGateThresholdPercent)
            prefs[KEY_RADAR_EDGE_SIDE] = config.radarEdgeSide.name
            prefs[KEY_RADAR_CLEAR_MODE] = config.radarClearMode.name
            prefs[KEY_RADAR_AUDIO_ALERT_MODE] = config.radarAudioAlertMode.name
            PREF_RADAR_ALERT_VOLUME.write(prefs, config.radarAlertVolume)
            prefs[KEY_RADAR_SPEAKER_BOOST] = config.radarSpeakerBoost
            prefs[KEY_RADAR_AUDIO_ROUTE] = config.radarAudioRoute.name
            prefs[KEY_RADAR_HAPTIC_ALERT] = config.radarHapticAlert
            prefs[KEY_RADAR_LIGHT_DEFAULT_MODE] = config.radarLightDefaultMode.name
            prefs[KEY_RADAR_LIGHT_CONTROL_MODE] = config.radarLightControlMode.name
            prefs[KEY_RADAR_BRAKE_LIGHT_ENABLED] = config.radarBrakeLightEnabled
            prefs[KEY_RADAR_PELOTON_AUDIO_SYNC] = config.radarPelotonAudioSync
            prefs[KEY_RADAR_SMART_URBAN_MUTE] = config.radarSmartUrbanMute
            prefs[KEY_RADAR_SPATIAL_AUDIO_ENABLED] = config.radarSpatialAudioEnabled
            prefs[KEY_RADAR_SPEED_PITCH_MODULATION] = config.radarSpeedPitchModulation
            prefs[KEY_RADAR_TRAFFIC_ORIENTATION] = config.radarTrafficOrientation.name
            prefs[KEY_RADAR_LANE_CHANGE_TICK_ENABLED] = config.radarLaneChangeTickEnabled
            prefs[KEY_RADAR_CLOSE_PASS_THRESHOLD] = config.radarClosePassThresholdM
            PREF_DEFAULT_POOL_LENGTH_METERS.write(prefs, config.defaultPoolLengthMeters)
            prefs[KEY_DEFAULT_POOL_LENGTH_UNIT] = config.defaultPoolLengthUnit.name
        }
    }

    /**
     * Atomically updates cluster matching tolerances in DataStore (single source of truth, REQ-CON-056.1)
     * and synchronizes the in-memory [tuningConfigStateFlow] before returning.
     */
    suspend fun updateClusterTuning(
        endpointTol: Float? = null,
        apexTol: Float? = null,
        distTol: Float? = null,
        altPosTol: Float? = null,
        useSport: Boolean? = null,
        useAlt: Boolean? = null,
        scoreThreshold: Float? = null
    ) {
        editAndSync { prefs ->
            endpointTol?.let { PREF_CLUSTER_TOL_ENDPOINTS.write(prefs, it) }
            apexTol?.let { PREF_CLUSTER_TOL_APEX.write(prefs, it) }
            distTol?.let { PREF_CLUSTER_TOL_DISTANCE.write(prefs, it) }
            altPosTol?.let { PREF_CLUSTER_TOL_ALTITUDE_POS.write(prefs, it) }
            useSport?.let { prefs[KEY_CLUSTER_USE_SPORT_TYPE] = it }
            useAlt?.let { prefs[KEY_CLUSTER_USE_ALTITUDE_POS] = it }
            scoreThreshold?.let { PREF_CLUSTER_SCORE_THRESHOLD.write(prefs, it) }
        }
    }


    suspend fun updateShowLiveClimbs(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_LIVE_CLIMBS] = show
        }
    }

    suspend fun updateClimbScoringFormula(formula: ClimbScoringFormula) {
        dataStore.edit { prefs ->
            prefs[KEY_CLIMB_SCORING_FORMULA] = formula.name
        }
    }

    suspend fun updateSensorFieldVariant(variant: SensorFieldVariant) {
        dataStore.edit { prefs ->
            prefs[KEY_SENSOR_FIELD_VARIANT] = variant.name
        }
    }

    suspend fun updateRouteSelectionRadiusKm(radiusKm: Float) {
        dataStore.edit { prefs ->
            PREF_ROUTE_SELECTION_RADIUS_KM.write(prefs, radiusKm)
        }
    }

    @Deprecated("Navigation cue overlays are permanently solid (1.0f) per REQ-UI-287 / ATT-3322")
    suspend fun updateNavigationCueTransparency(transparency: Float) {
        dataStore.edit { prefs ->
            PREF_NAVIGATION_CUE_TRANSPARENCY.write(prefs, transparency)
        }
    }

    suspend fun updateNavigationCueDismissDurationSec(durationSec: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC] = clampNavCueDismiss(durationSec)
        }
    }

    suspend fun updateElevationSmoothingSigmaMeters(sigma: Float) {
        dataStore.edit { prefs ->
            PREF_ELEVATION_SMOOTHING_SIGMA_METERS.write(prefs, sigma)
        }
    }

    @Deprecated("Camera parameters are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
    suspend fun updateMapFollowMeInitialZoom(zoom: Float) {
        dataStore.edit { prefs ->
            prefs.remove(KEY_MAP_FOLLOW_ME_INITIAL_ZOOM)
        }
    }

    @Deprecated("Camera parameters are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
    suspend fun updateMapFollowMeSpeedZoomEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs.remove(KEY_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED)
        }
    }

    @Deprecated("Camera parameters are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
    suspend fun updateMapFollowMeCruisingZoom(zoom: Float) {
        dataStore.edit { prefs ->
            prefs.remove(KEY_MAP_FOLLOW_ME_CRUISING_ZOOM)
        }
    }

    @Deprecated("Camera parameters are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
    suspend fun updateMapFollowMeTiltAngle(angle: Float) {
        dataStore.edit { prefs ->
            prefs.remove(KEY_MAP_FOLLOW_ME_TILT_ANGLE)
        }
    }

    @Deprecated("Camera parameters are permanently hardcoded to calibrated defaults per ATT-3581 / REQ-MAP-050")
    suspend fun updateMapFollowMeLookaheadPaddingPercent(percent: Float) {
        dataStore.edit { prefs ->
            prefs.remove(KEY_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT)
        }
    }

    val suppressAntMissingAdapterDialogFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG] ?: TuningPreferencesDefaults.SUPPRESS_ANT_MISSING_ADAPTER_DIALOG
    }

    val postWorkoutErtsSmoothingFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_POST_WORKOUT_ERTS_SMOOTHING] ?: TuningPreferencesDefaults.DEFAULT_POST_WORKOUT_ERTS_SMOOTHING_ENABLED
    }

    val rawSensorRetentionPolicyFlow: Flow<RawSensorRetentionPolicy> = dataStore.data.map { prefs ->
        safeEnumValueOf(prefs[KEY_RAW_SENSOR_RETENTION_POLICY], TuningPreferencesDefaults.RAW_SENSOR_RETENTION_POLICY)
    }

    val rawSensorRetentionPolicy: StateFlow<RawSensorRetentionPolicy> by lazy {
        rawSensorRetentionPolicyFlow.stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = TuningPreferencesDefaults.RAW_SENSOR_RETENTION_POLICY
        )
    }

    suspend fun updateRawSensorRetentionPolicy(policy: RawSensorRetentionPolicy) {
        dataStore.edit { prefs ->
            prefs[KEY_RAW_SENSOR_RETENTION_POLICY] = policy.name
        }
    }

    val recordSmartphoneRawTelemetryFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_RECORD_SMARTPHONE_RAW_TELEMETRY] ?: TuningPreferencesDefaults.DEFAULT_RECORD_SMARTPHONE_RAW_TELEMETRY_ENABLED
    }

    val recordSmartphoneRawTelemetryEnabled: StateFlow<Boolean> by lazy {
        recordSmartphoneRawTelemetryFlow.stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = TuningPreferencesDefaults.DEFAULT_RECORD_SMARTPHONE_RAW_TELEMETRY_ENABLED
        )
    }

    suspend fun updateRecordSmartphoneRawTelemetryEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_RECORD_SMARTPHONE_RAW_TELEMETRY] = enabled
        }
    }

    suspend fun updatePostWorkoutErtsSmoothingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_POST_WORKOUT_ERTS_SMOOTHING] = enabled
        }
    }

    suspend fun setSuppressAntMissingAdapterDialog(suppress: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG] = suppress
        }
    }

    suspend fun updateAutoFollowUniqueRouteEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED] = enabled
        }
    }

    suspend fun updateTurnCueCountdownDistanceMeters(distanceMeters: Float) {
        dataStore.edit { prefs ->
            PREF_TURN_CUE_COUNTDOWN_DISTANCE.write(prefs, distanceMeters)
        }
    }

    val radarEdgeSideFlow: Flow<RadarEdgeSide> = dataStore.data.map { prefs ->
        safeEnumValueOf(prefs[KEY_RADAR_EDGE_SIDE], TuningPreferencesDefaults.RADAR_EDGE_SIDE)
    }

    val radarClearModeFlow: Flow<RadarClearMode> = dataStore.data.map { prefs ->
        safeEnumValueOf(prefs[KEY_RADAR_CLEAR_MODE], TuningPreferencesDefaults.RADAR_CLEAR_MODE)
    }

    suspend fun updateRadarEdgeSide(side: RadarEdgeSide) {
        dataStore.edit { prefs ->
            prefs[KEY_RADAR_EDGE_SIDE] = side.name
        }
    }

    suspend fun updateRadarClearMode(mode: RadarClearMode) {
        dataStore.edit { prefs ->
            prefs[KEY_RADAR_CLEAR_MODE] = mode.name
        }
    }

    val radarAudioAlertModeFlow: Flow<RadarAudioAlertMode> = dataStore.data.map { prefs ->
        safeEnumValueOf(prefs[KEY_RADAR_AUDIO_ALERT_MODE], TuningPreferencesDefaults.RADAR_AUDIO_ALERT_MODE)
    }

    val radarAlertVolumeFlow: Flow<Int> = dataStore.data.map { prefs ->
        PREF_RADAR_ALERT_VOLUME.read(prefs)
    }

    val radarSpeakerBoostFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_RADAR_SPEAKER_BOOST] ?: TuningPreferencesDefaults.RADAR_SPEAKER_BOOST
    }

    val radarAudioRouteFlow: Flow<RadarAudioRoute> = dataStore.data.map { prefs ->
        safeEnumValueOf(prefs[KEY_RADAR_AUDIO_ROUTE], TuningPreferencesDefaults.RADAR_AUDIO_ROUTE)
    }

    val radarHapticAlertFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_RADAR_HAPTIC_ALERT] ?: TuningPreferencesDefaults.RADAR_HAPTIC_ALERT
    }

    suspend fun updateRadarAudioAlertMode(mode: RadarAudioAlertMode) {
        dataStore.edit { prefs ->
            prefs[KEY_RADAR_AUDIO_ALERT_MODE] = mode.name
        }
    }

    suspend fun updateRadarAlertVolume(volume: Int) {
        dataStore.edit { prefs ->
            PREF_RADAR_ALERT_VOLUME.write(prefs, volume)
        }
    }

    suspend fun updateRadarSpeakerBoost(boost: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_RADAR_SPEAKER_BOOST] = boost
        }
    }

    suspend fun updateRadarAudioRoute(route: RadarAudioRoute) {
        dataStore.edit { prefs ->
            prefs[KEY_RADAR_AUDIO_ROUTE] = route.name
        }
    }

    suspend fun updateRadarHapticAlert(haptic: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_RADAR_HAPTIC_ALERT] = haptic
        }
    }

    val radarLightDefaultModeFlow: Flow<RadarLightMode> = dataStore.data.map { prefs ->
        resolveRadarLightDefaultMode(prefs)
    }

    val radarLightAutoModeFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        resolveRadarLightAutoMode(prefs)
    }

    val radarLightControlModeFlow: Flow<RadarLightControlMode> = dataStore.data.map { prefs ->
        resolveRadarLightControlMode(prefs)
    }

    val radarAutoLightControlFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        resolveRadarLightControlMode(prefs) == RadarLightControlMode.FIXED_WORKOUT_MODE
    }

    val radarSmartLightAutomationFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        resolveRadarLightControlMode(prefs) == RadarLightControlMode.SMART_AUTOMATION
    }

    suspend fun updateRadarLightDefaultMode(mode: RadarLightMode) {
        editAndSync { prefs ->
            prefs[KEY_RADAR_LIGHT_DEFAULT_MODE] = mode.name
        }
    }

    suspend fun updateRadarLightAutoMode(autoMode: Boolean) {
        editAndSync { prefs ->
            prefs[KEY_RADAR_LIGHT_CONTROL_MODE] = if (autoMode) {
                RadarLightControlMode.SMART_AUTOMATION.name
            } else {
                RadarLightControlMode.FIXED_WORKOUT_MODE.name
            }
        }
    }

    suspend fun updateRadarLightSelection(isAuto: Boolean, mode: RadarLightMode? = null) {
        editAndSync { prefs ->
            if (mode != null) {
                prefs[KEY_RADAR_LIGHT_DEFAULT_MODE] = mode.name
            }
            prefs[KEY_RADAR_LIGHT_CONTROL_MODE] = if (isAuto) {
                RadarLightControlMode.SMART_AUTOMATION.name
            } else {
                RadarLightControlMode.FIXED_WORKOUT_MODE.name
            }
        }
    }

    suspend fun updateRadarLightControlMode(mode: RadarLightControlMode) {
        editAndSync { prefs ->
            prefs[KEY_RADAR_LIGHT_CONTROL_MODE] = mode.name
        }
    }

    suspend fun updateRadarAutoLightControl(enabled: Boolean) {
        editAndSync { prefs ->
            prefs[KEY_RADAR_LIGHT_CONTROL_MODE] = if (enabled) {
                RadarLightControlMode.FIXED_WORKOUT_MODE.name
            } else {
                RadarLightControlMode.MANUAL.name
            }
        }
    }

    suspend fun updateRadarSmartLightAutomation(enabled: Boolean) {
        editAndSync { prefs ->
            prefs[KEY_RADAR_LIGHT_CONTROL_MODE] = if (enabled) {
                RadarLightControlMode.SMART_AUTOMATION.name
            } else {
                val current = resolveRadarLightControlMode(prefs)
                if (current == RadarLightControlMode.FIXED_WORKOUT_MODE) {
                    RadarLightControlMode.FIXED_WORKOUT_MODE.name
                } else {
                    RadarLightControlMode.MANUAL.name
                }
            }
        }
    }

    val radarBrakeLightEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_RADAR_BRAKE_LIGHT_ENABLED] ?: TuningPreferencesDefaults.RADAR_BRAKE_LIGHT_ENABLED
    }

    suspend fun updateRadarBrakeLightEnabled(enabled: Boolean) {
        editAndSync { prefs ->
            prefs[KEY_RADAR_BRAKE_LIGHT_ENABLED] = enabled
        }
    }

    val radarPelotonAudioSyncFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_RADAR_PELOTON_AUDIO_SYNC] ?: TuningPreferencesDefaults.RADAR_PELOTON_AUDIO_SYNC
    }

    val radarSmartUrbanMuteFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_RADAR_SMART_URBAN_MUTE] ?: TuningPreferencesDefaults.RADAR_SMART_URBAN_MUTE
    }

    suspend fun updateRadarPelotonAudioSync(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_RADAR_PELOTON_AUDIO_SYNC] = enabled
        }
    }

    suspend fun updateRadarSmartUrbanMute(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_RADAR_SMART_URBAN_MUTE] = enabled
        }
    }

    val radarSpatialAudioEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_RADAR_SPATIAL_AUDIO_ENABLED] ?: TuningPreferencesDefaults.RADAR_SPATIAL_AUDIO_ENABLED
    }

    val radarSpeedPitchModulationFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_RADAR_SPEED_PITCH_MODULATION] ?: TuningPreferencesDefaults.RADAR_SPEED_PITCH_MODULATION
    }

    val radarTrafficOrientationFlow: Flow<RadarTrafficOrientation> = dataStore.data.map { prefs ->
        safeEnumValueOf(prefs[KEY_RADAR_TRAFFIC_ORIENTATION], TuningPreferencesDefaults.RADAR_TRAFFIC_ORIENTATION)
    }

    val radarLaneChangeTickEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_RADAR_LANE_CHANGE_TICK_ENABLED] ?: TuningPreferencesDefaults.RADAR_LANE_CHANGE_TICK_ENABLED
    }

    val radarClosePassThresholdFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_RADAR_CLOSE_PASS_THRESHOLD] ?: TuningPreferencesDefaults.RADAR_CLOSE_PASS_THRESHOLD_M
    }

    suspend fun updateRadarSpatialAudioEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_RADAR_SPATIAL_AUDIO_ENABLED] = enabled
        }
    }

    suspend fun updateRadarSpeedPitchModulation(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_RADAR_SPEED_PITCH_MODULATION] = enabled
        }
    }

    suspend fun updateRadarTrafficOrientation(orientation: RadarTrafficOrientation) {
        dataStore.edit { prefs ->
            prefs[KEY_RADAR_TRAFFIC_ORIENTATION] = orientation.name
        }
    }

    suspend fun updateRadarLaneChangeTickEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_RADAR_LANE_CHANGE_TICK_ENABLED] = enabled
        }
    }

    suspend fun updateRadarClosePassThreshold(thresholdM: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_RADAR_CLOSE_PASS_THRESHOLD] = thresholdM
        }
    }

    val defaultPoolLengthMetersFlow: Flow<Float> = dataStore.data.map { prefs ->
        PREF_DEFAULT_POOL_LENGTH_METERS.read(prefs)
    }

    val defaultPoolLengthUnitFlow: Flow<PoolLengthUnit> = dataStore.data.map { prefs ->
        safeEnumValueOf(prefs[KEY_DEFAULT_POOL_LENGTH_UNIT], TuningPreferencesDefaults.DEFAULT_POOL_LENGTH_UNIT)
    }

    suspend fun updateDefaultPoolLength(lengthMeters: Float, unit: PoolLengthUnit) {
        dataStore.edit { prefs ->
            PREF_DEFAULT_POOL_LENGTH_METERS.write(prefs, lengthMeters)
            prefs[KEY_DEFAULT_POOL_LENGTH_UNIT] = unit.name
        }
    }

    /**
     * Reactive flow emitting whether the first-time stop tracking coachmark hint on the cockpit screen
     * has already been seen/dismissed by the user (REQ-UI-367 / ATT-3387).
     */
    val hasSeenStopTrackingHintFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_HAS_SEEN_STOP_TRACKING_HINT] ?: TuningPreferencesDefaults.DEFAULT_HAS_SEEN_STOP_TRACKING_HINT
    }

    /**
     * Atomically persists whether the first-time stop tracking coachmark hint has been acknowledged (REQ-UI-367 / ATT-3387).
     */
    suspend fun setHasSeenStopTrackingHint(seen: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_STOP_TRACKING_HINT] = seen
        }
    }

    /**
     * Reactive flow emitting whether the first-install permission onboarding flow has been completed or dismissed (REQ-PRI-007 / ATT-4455).
     */
    val hasCompletedPermissionOnboardingFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_HAS_COMPLETED_PERMISSION_ONBOARDING] ?: TuningPreferencesDefaults.DEFAULT_HAS_COMPLETED_PERMISSION_ONBOARDING
    }

    /**
     * Atomically persists whether the first-install permission onboarding flow has been completed or dismissed (REQ-PRI-007 / ATT-4455).
     */
    suspend fun setHasCompletedPermissionOnboarding(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HAS_COMPLETED_PERMISSION_ONBOARDING] = completed
        }
    }

    suspend fun updateStartInAutoPauseEnabled(enabled: Boolean) {
        editAndSync { prefs ->
            prefs[KEY_START_IN_AUTO_PAUSE_ENABLED] = enabled
        }
    }

    suspend fun updateAutoPauseBikeEnabled(enabled: Boolean) {
        editAndSync { prefs ->
            prefs[KEY_AUTO_PAUSE_BIKE_ENABLED] = enabled
        }
    }

    suspend fun updateAutoPauseRunEnabled(enabled: Boolean) {
        editAndSync { prefs ->
            prefs[KEY_AUTO_PAUSE_RUN_ENABLED] = enabled
        }
    }

    suspend fun updateAutoPauseOtherEnabled(enabled: Boolean) {
        editAndSync { prefs ->
            prefs[KEY_AUTO_PAUSE_OTHER_ENABLED] = enabled
        }
    }

    suspend fun updateAutoPauseSpeedThresholdKmh(thresholdKmh: Float) {
        editAndSync { prefs ->
            PREF_AUTO_PAUSE_SPEED_THRESHOLD_KMH.write(prefs, thresholdKmh)
        }
    }

    suspend fun updateAutoPauseDebounceSec(debounceSec: Int) {
        editAndSync { prefs ->
            PREF_AUTO_PAUSE_DEBOUNCE_SEC.write(prefs, debounceSec)
        }
    }

    suspend fun updateAutoPauseBikeSpeedThreshold(threshold: Int) {
        val clamped = PREF_AUTO_PAUSE_BIKE_SPEED_THRESHOLD.clamp(threshold)
        editAndSync { prefs ->
            prefs[KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD] = clamped
            prefs[KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH] = clamped.toFloat()
        }
    }

    suspend fun updateAutoPauseBikeDebounceSec(debounceSec: Int) {
        val clamped = PREF_AUTO_PAUSE_BIKE_DEBOUNCE_SEC.clamp(debounceSec)
        editAndSync { prefs ->
            prefs[KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC] = clamped
            prefs[KEY_AUTO_PAUSE_DEBOUNCE_SEC] = clamped
        }
    }

    suspend fun updateAutoPauseBikeResumeDebounceSec(debounceSec: Int) {
        editAndSync { prefs ->
            PREF_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC.write(prefs, debounceSec)
        }
    }

    suspend fun updateAutoPauseRunSpeedThreshold(threshold: Int) {
        editAndSync { prefs ->
            PREF_AUTO_PAUSE_RUN_SPEED_THRESHOLD.write(prefs, threshold)
        }
    }

    suspend fun updateAutoPauseRunDebounceSec(debounceSec: Int) {
        editAndSync { prefs ->
            PREF_AUTO_PAUSE_RUN_DEBOUNCE_SEC.write(prefs, debounceSec)
        }
    }

    suspend fun updateAutoPauseRunResumeDebounceSec(debounceSec: Int) {
        editAndSync { prefs ->
            PREF_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC.write(prefs, debounceSec)
        }
    }

    suspend fun updateAutoPauseOtherSpeedThreshold(threshold: Int) {
        editAndSync { prefs ->
            PREF_AUTO_PAUSE_OTHER_SPEED_THRESHOLD.write(prefs, threshold)
        }
    }

    suspend fun updateAutoPauseOtherDebounceSec(debounceSec: Int) {
        editAndSync { prefs ->
            PREF_AUTO_PAUSE_OTHER_DEBOUNCE_SEC.write(prefs, debounceSec)
        }
    }

    suspend fun updateAutoPauseOtherResumeDebounceSec(debounceSec: Int) {
        editAndSync { prefs ->
            PREF_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC.write(prefs, debounceSec)
        }
    }

    suspend fun resetAutoPausePreferences(sportType: BSportType? = null) {
        editAndSync { prefs ->
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
    }

    suspend fun resetToDefaults() {
        editAndSync { prefs ->
            for (key in ALL_KEYS) {
                prefs.remove(key)
            }
            for (legacyKey in LEGACY_RADAR_LIGHT_KEYS) {
                prefs.remove(legacyKey)
            }
            purgeLegacyMapCameraKeys(prefs)
        }
    }
}
