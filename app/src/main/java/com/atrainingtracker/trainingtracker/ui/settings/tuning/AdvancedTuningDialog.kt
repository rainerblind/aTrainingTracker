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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionType
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.AftermathAnalysisSection
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.AmoledBatterySaverSection
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.CockpitTypographySection
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.NavigationSection
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorsGpsFilterSection
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorSearchPreferences
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorSearchTuningSection
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSection
import kotlinx.coroutines.launch

/**
 * Advanced Tuning and Settings dialog structured into modular, collapsible
 * Material 3 accordion subsections with live active-value summary subtitles (REQ-UI-222, REQ-UI-262, REQ-UI-281).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedTuningDialog(
    onDismiss: () -> Unit,
    onSettingsChanged: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tuningDataStore = remember { TuningPreferencesDataStore(context) }
    val persistedConfig by tuningDataStore.tuningConfigFlow.collectAsState(initial = TuningConfig())

    val preferenceManager = remember { MyPreferenceManager(context.applicationContext) }
    val persistedWorkoutCardPrefs by preferenceManager.workoutCardPreferencesFlow.collectAsState(initial = null)
    val persistedWorkoutDetailPrefs by preferenceManager.workoutDetailPreferencesFlow.collectAsState(initial = null)
    val persistedWorkoutSectionsOrder by preferenceManager.workoutSectionsOrderFlow.collectAsState(initial = null)

    var elevationXAxisDomain by remember { mutableStateOf(TuningPreferencesDefaults.ELEVATION_X_AXIS_DOMAIN) }
    var telemetryXAxisDomain by remember { mutableStateOf(TuningPreferencesDefaults.TELEMETRY_X_AXIS_DOMAIN) }
    var fullDimFactor by remember { mutableFloatStateOf(TuningPreferencesDefaults.FULL_DIM_FACTOR) }
    var mediumDimFactor by remember { mutableFloatStateOf(TuningPreferencesDefaults.MEDIUM_DIM_FACTOR) }
    var slopeFlat by remember { mutableFloatStateOf(TuningPreferencesDefaults.SLOPE_FLAT_THRESHOLD) }
    var slopeSteep by remember { mutableFloatStateOf(TuningPreferencesDefaults.SLOPE_STEEP_THRESHOLD) }
    var wakeupSec by remember { mutableIntStateOf(TuningPreferencesDefaults.WAKEUP_DURATION_SEC) }
    var downwardDelaySec by remember { mutableIntStateOf(TuningPreferencesDefaults.DOWNWARD_DELAY_SEC) }
    var gpsAccuracy by remember { mutableFloatStateOf(TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M) }
    var altitudeWindowSec by remember { mutableIntStateOf(TuningPreferencesDefaults.ALTITUDE_FILTER_WINDOW_SEC) }
    var slopeMinSpeed by remember { mutableFloatStateOf(TuningPreferencesDefaults.SLOPE_MIN_SPEED_MPS) }
    var paceCeilingMinKm by remember { mutableFloatStateOf(TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM) }
    var cockpitFontFamily by remember { mutableStateOf(TuningPreferencesDefaults.COCKPIT_FONT_FAMILY) }
    var cockpitFontWeight by remember { mutableStateOf(TuningPreferencesDefaults.COCKPIT_FONT_WEIGHT) }
    var sensorFieldVariant by remember { mutableStateOf(TuningPreferencesDefaults.SENSOR_FIELD_VARIANT) }
    var sensorFieldCornerRadius by remember { mutableFloatStateOf(TuningPreferencesDefaults.SENSOR_FIELD_CORNER_RADIUS) }
    var sensorFieldBorderThickness by remember { mutableFloatStateOf(TuningPreferencesDefaults.SENSOR_FIELD_BORDER_THICKNESS) }
    var sensorFieldBorderContrast by remember { mutableFloatStateOf(TuningPreferencesDefaults.SENSOR_FIELD_BORDER_CONTRAST) }
    var routeSelectionRadiusKm by remember { mutableFloatStateOf(TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM) }
    var navigationCueTransparency by remember { mutableFloatStateOf(TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY) }
    var navigationCueDismissDurationSec by remember { mutableIntStateOf(TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC) }
    var elevationSmoothingSigmaMeters by remember { mutableFloatStateOf(TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS) }
    var sensorSearchPrefs by remember { mutableStateOf(SensorSearchPreferences.load(context)) }

    var workoutCardPrefs by remember { mutableStateOf(WorkoutCardSectionPreferences()) }
    var isAftermathPrefsInitialized by remember { mutableStateOf(false) }
    var workoutDetailPrefs by remember { mutableStateOf(WorkoutDetailPreferences()) }
    var isDetailPrefsInitialized by remember { mutableStateOf(false) }
    var workoutSectionsOrder by remember { mutableStateOf(WorkoutSectionType.DEFAULT_ORDER) }
    var isSectionsOrderInitialized by remember { mutableStateOf(false) }

    // Multi-section expansion state tracked across configuration changes via string identifiers
    var expandedSections by rememberSaveable { mutableStateOf(emptySet<String>()) }
    fun isSectionExpanded(section: TuningSection): Boolean = expandedSections.contains(section.name)
    fun toggleSection(section: TuningSection) {
        expandedSections = if (expandedSections.contains(section.name)) expandedSections - section.name else expandedSections + section.name
    }

    LaunchedEffect(persistedWorkoutCardPrefs) {
        val cardPrefs = persistedWorkoutCardPrefs
        if (!isAftermathPrefsInitialized && cardPrefs != null) {
            workoutCardPrefs = cardPrefs
            isAftermathPrefsInitialized = true
        }
    }
    LaunchedEffect(persistedWorkoutDetailPrefs) {
        val detailPrefs = persistedWorkoutDetailPrefs
        if (!isDetailPrefsInitialized && detailPrefs != null) {
            workoutDetailPrefs = detailPrefs
            isDetailPrefsInitialized = true
        }
    }
    LaunchedEffect(persistedWorkoutSectionsOrder) {
        val order = persistedWorkoutSectionsOrder
        if (!isSectionsOrderInitialized && order != null) {
            workoutSectionsOrder = order
            isSectionsOrderInitialized = true
        }
    }
    LaunchedEffect(persistedConfig) {
        elevationXAxisDomain = persistedConfig.elevationXAxisDomain
        telemetryXAxisDomain = persistedConfig.telemetryXAxisDomain
        cockpitFontFamily = persistedConfig.cockpitFontFamily
        cockpitFontWeight = persistedConfig.cockpitFontWeight
        sensorFieldVariant = persistedConfig.sensorFieldVariant
        sensorFieldCornerRadius = persistedConfig.sensorFieldCornerRadius
        sensorFieldBorderThickness = persistedConfig.sensorFieldBorderThickness
        sensorFieldBorderContrast = persistedConfig.sensorFieldBorderContrast
        fullDimFactor = persistedConfig.fullDimFactor
        mediumDimFactor = persistedConfig.mediumDimFactor
        slopeFlat = persistedConfig.slopeFlatThreshold
        slopeSteep = persistedConfig.slopeSteepThreshold
        wakeupSec = persistedConfig.wakeupDurationSec
        downwardDelaySec = persistedConfig.downwardDelaySec
        gpsAccuracy = persistedConfig.gpsAccuracyThresholdMeters
        altitudeWindowSec = persistedConfig.altitudeFilterWindowSec
        slopeMinSpeed = persistedConfig.slopeMinSpeedMps
        paceCeilingMinKm = persistedConfig.paceCeilingMinKm
        routeSelectionRadiusKm = persistedConfig.routeSelectionRadiusKm
        navigationCueTransparency = persistedConfig.navigationCueTransparency
        navigationCueDismissDurationSec = persistedConfig.navigationCueDismissDurationSec
        elevationSmoothingSigmaMeters = persistedConfig.elevationSmoothingSigmaMeters
    }

    AppBottomSheetContent(
        title = stringResource(R.string.advanced_tuning_title),
        icon = Icons.Default.Tune,
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    val newConfig = TuningConfig(
                        elevationXAxisDomain = elevationXAxisDomain,
                        telemetryXAxisDomain = telemetryXAxisDomain,
                        cockpitFontFamily = cockpitFontFamily,
                        cockpitFontWeight = cockpitFontWeight,
                        sensorFieldVariant = sensorFieldVariant,
                        sensorFieldCornerRadius = sensorFieldCornerRadius,
                        sensorFieldBorderThickness = sensorFieldBorderThickness,
                        sensorFieldBorderContrast = sensorFieldBorderContrast,
                        fullDimFactor = fullDimFactor,
                        mediumDimFactor = mediumDimFactor,
                        slopeFlatThreshold = slopeFlat,
                        slopeSteepThreshold = slopeSteep,
                        wakeupDurationSec = wakeupSec,
                        downwardDelaySec = downwardDelaySec,
                        gpsAccuracyThresholdMeters = gpsAccuracy,
                        altitudeFilterWindowSec = altitudeWindowSec,
                        slopeMinSpeedMps = slopeMinSpeed,
                        paceCeilingMinKm = paceCeilingMinKm,
                        routeSelectionRadiusKm = routeSelectionRadiusKm,
                        navigationCueTransparency = navigationCueTransparency,
                        navigationCueDismissDurationSec = navigationCueDismissDurationSec,
                        elevationSmoothingSigmaMeters = elevationSmoothingSigmaMeters
                    )
                    scope.launch {
                        tuningDataStore.saveTuningConfig(newConfig)
                        preferenceManager.setWorkoutCardPreferences(workoutCardPrefs)
                        preferenceManager.setWorkoutDetailPreferences(workoutDetailPrefs)
                        preferenceManager.setWorkoutSectionsOrder(workoutSectionsOrder)
                        SensorSearchPreferences.save(context, sensorSearchPrefs)
                        onSettingsChanged?.invoke()
                        onDismiss()
                    }
                },
                onCancel = onDismiss,
                saveText = stringResource(R.string.save)
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Advisory Warning Notice
            TuningWarningNotice()

            // Section 1: Cockpit & Typografie
            TuningAccordionSection(
                icon = Icons.Default.TextFields,
                title = stringResource(R.string.tuning_cat_cockpit_typography),
                subtitle = TuningSubtitleFormatter.formatCockpitSubtitle(cockpitFontFamily, cockpitFontWeight, sensorFieldVariant, context),
                isExpanded = isSectionExpanded(TuningSection.COCKPIT_TYPOGRAPHY),
                onToggle = { toggleSection(TuningSection.COCKPIT_TYPOGRAPHY) }
            ) {
                CockpitTypographySection(
                    cockpitFontFamily = cockpitFontFamily, onFontFamilyChange = { cockpitFontFamily = it },
                    cockpitFontWeight = cockpitFontWeight, onFontWeightChange = { cockpitFontWeight = it },
                    sensorFieldVariant = sensorFieldVariant, onSensorFieldVariantChange = { sensorFieldVariant = it },
                    sensorFieldCornerRadius = sensorFieldCornerRadius, onCornerRadiusChange = { sensorFieldCornerRadius = it },
                    sensorFieldBorderThickness = sensorFieldBorderThickness, onBorderThicknessChange = { sensorFieldBorderThickness = it },
                    sensorFieldBorderContrast = sensorFieldBorderContrast, onBorderContrastChange = { sensorFieldBorderContrast = it }
                )
            }

            // Section 2: AMOLED-Akkuschoner & Helligkeit
            TuningAccordionSection(
                icon = Icons.Default.BrightnessMedium,
                title = stringResource(R.string.tuning_cat_battery_saver),
                subtitle = TuningSubtitleFormatter.formatBatterySaverSubtitle(fullDimFactor, mediumDimFactor, slopeFlat, slopeSteep),
                isExpanded = isSectionExpanded(TuningSection.BATTERY_SAVER),
                onToggle = { toggleSection(TuningSection.BATTERY_SAVER) }
            ) {
                AmoledBatterySaverSection(
                    fullDimFactor = fullDimFactor, onFullDimChange = { fullDimFactor = it },
                    mediumDimFactor = mediumDimFactor, onMediumDimChange = { mediumDimFactor = it },
                    slopeFlat = slopeFlat, onSlopeFlatChange = { slopeFlat = it },
                    slopeSteep = slopeSteep, onSlopeSteepChange = { slopeSteep = it },
                    wakeupSec = wakeupSec, onWakeupSecChange = { wakeupSec = it },
                    downwardDelaySec = downwardDelaySec, onDownwardDelayChange = { downwardDelaySec = it }
                )
            }

            // Section 3: Sensoren, GPS & Filter
            TuningAccordionSection(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.tuning_cat_sensors_gps),
                subtitle = TuningSubtitleFormatter.formatSensorsGpsSubtitle(gpsAccuracy, altitudeWindowSec, slopeMinSpeed),
                isExpanded = isSectionExpanded(TuningSection.SENSORS_GPS),
                onToggle = { toggleSection(TuningSection.SENSORS_GPS) }
            ) {
                SensorsGpsFilterSection(
                    gpsAccuracy = gpsAccuracy, onGpsAccuracyChange = { gpsAccuracy = it },
                    altitudeWindowSec = altitudeWindowSec, onAltitudeWindowChange = { altitudeWindowSec = it },
                    slopeMinSpeed = slopeMinSpeed, onSlopeMinSpeedChange = { slopeMinSpeed = it }
                )
            }

            // Section 4: Sensorsuche & Verhalten
            TuningAccordionSection(
                icon = Icons.Default.Search,
                title = stringResource(R.string.tuning_cat_sensor_search),
                subtitle = TuningSubtitleFormatter.formatSensorSearchSubtitle(
                    sensorSearchPrefs.numberOfSearchTries,
                    sensorSearchPrefs.startSearchWhenAppStarts,
                    sensorSearchPrefs.startSearchWhenResumeFromPaused,
                    sensorSearchPrefs.startSearchWhenUserChangesSport,
                    sensorSearchPrefs.startSearchWhenTrackingStarts,
                    context
                ),
                isExpanded = isSectionExpanded(TuningSection.SENSOR_SEARCH),
                onToggle = { toggleSection(TuningSection.SENSOR_SEARCH) }
            ) {
                SensorSearchTuningSection(
                    prefs = sensorSearchPrefs,
                    onPrefsChange = { sensorSearchPrefs = it }
                )
            }

            // Section 4: Aftermath & Analyse
            TuningAccordionSection(
                icon = Icons.AutoMirrored.Filled.ShowChart,
                title = stringResource(R.string.tuning_cat_aftermath),
                subtitle = TuningSubtitleFormatter.formatAftermathSubtitle(
                    elevationXAxisDomain,
                    telemetryXAxisDomain,
                    context,
                    elevationSmoothingSigmaMeters
                ),
                isExpanded = isSectionExpanded(TuningSection.AFTERMATH_ANALYSIS),
                onToggle = { toggleSection(TuningSection.AFTERMATH_ANALYSIS) }
            ) {
                AftermathAnalysisSection(
                    elevationXAxisDomain = elevationXAxisDomain, onElevationDomainChange = { elevationXAxisDomain = it },
                    telemetryXAxisDomain = telemetryXAxisDomain, onTelemetryDomainChange = { telemetryXAxisDomain = it },
                    paceCeilingMinKm = paceCeilingMinKm, onPaceCeilingChange = { paceCeilingMinKm = it },
                    elevationSmoothingSigmaMeters = elevationSmoothingSigmaMeters,
                    onElevationSmoothingSigmaChange = { elevationSmoothingSigmaMeters = it }
                )
            }

            // Section 5: Workout-Masken & Detailkarten
            TuningAccordionSection(
                icon = Icons.AutoMirrored.Filled.ViewList,
                title = stringResource(R.string.tuning_cat_workout_masks_cards),
                subtitle = TuningSubtitleFormatter.formatWorkoutMatrixSubtitle(workoutCardPrefs, workoutDetailPrefs, context),
                isExpanded = isSectionExpanded(TuningSection.WORKOUT_MASKS_CARDS),
                onToggle = { toggleSection(TuningSection.WORKOUT_MASKS_CARDS) }
            ) {
                WorkoutMasksAndCardsSection(
                    workoutCardPrefs = workoutCardPrefs, onWorkoutCardPrefsChange = { workoutCardPrefs = it },
                    workoutDetailPrefs = workoutDetailPrefs, onWorkoutDetailPrefsChange = { workoutDetailPrefs = it },
                    workoutSectionsOrder = workoutSectionsOrder, onWorkoutSectionsOrderChange = { workoutSectionsOrder = it }
                )
            }

            // Section 6: Navigation
            TuningAccordionSection(
                icon = Icons.Default.Navigation,
                title = stringResource(R.string.tuning_cat_navigation),
                subtitle = TuningSubtitleFormatter.formatNavigationSubtitle(
                    radiusKm = routeSelectionRadiusKm,
                    transparency = navigationCueTransparency,
                    dismissSec = navigationCueDismissDurationSec,
                    context = context
                ),
                isExpanded = isSectionExpanded(TuningSection.NAVIGATION),
                onToggle = { toggleSection(TuningSection.NAVIGATION) }
            ) {
                NavigationSection(
                    routeSelectionRadiusKm = routeSelectionRadiusKm,
                    onRadiusChange = { routeSelectionRadiusKm = it },
                    navigationCueTransparency = navigationCueTransparency,
                    onTransparencyChange = { navigationCueTransparency = it },
                    navigationCueDismissDurationSec = navigationCueDismissDurationSec,
                    onDismissDurationChange = { navigationCueDismissDurationSec = it }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            TuningResetDefaultsButton(
                onClick = {
                    scope.launch {
                        tuningDataStore.resetToDefaults()
                        preferenceManager.setWorkoutCardPreferences(WorkoutCardSectionPreferences())
                        preferenceManager.setWorkoutDetailPreferences(WorkoutDetailPreferences())
                        preferenceManager.setWorkoutSectionsOrder(WorkoutSectionType.DEFAULT_ORDER)
                        SensorSearchPreferences.reset(context)
                        sensorSearchPrefs = SensorSearchPreferences()
                        workoutCardPrefs = WorkoutCardSectionPreferences()
                        workoutDetailPrefs = WorkoutDetailPreferences()
                        workoutSectionsOrder = WorkoutSectionType.DEFAULT_ORDER
                        elevationXAxisDomain = TuningPreferencesDefaults.ELEVATION_X_AXIS_DOMAIN
                        telemetryXAxisDomain = TuningPreferencesDefaults.TELEMETRY_X_AXIS_DOMAIN
                        cockpitFontFamily = TuningPreferencesDefaults.COCKPIT_FONT_FAMILY
                        cockpitFontWeight = TuningPreferencesDefaults.COCKPIT_FONT_WEIGHT
                        sensorFieldVariant = TuningPreferencesDefaults.SENSOR_FIELD_VARIANT
                        sensorFieldCornerRadius = TuningPreferencesDefaults.SENSOR_FIELD_CORNER_RADIUS
                        sensorFieldBorderThickness = TuningPreferencesDefaults.SENSOR_FIELD_BORDER_THICKNESS
                        sensorFieldBorderContrast = TuningPreferencesDefaults.SENSOR_FIELD_BORDER_CONTRAST
                        fullDimFactor = TuningPreferencesDefaults.FULL_DIM_FACTOR
                        mediumDimFactor = TuningPreferencesDefaults.MEDIUM_DIM_FACTOR
                        slopeFlat = TuningPreferencesDefaults.SLOPE_FLAT_THRESHOLD
                        slopeSteep = TuningPreferencesDefaults.SLOPE_STEEP_THRESHOLD
                        wakeupSec = TuningPreferencesDefaults.WAKEUP_DURATION_SEC
                        downwardDelaySec = TuningPreferencesDefaults.DOWNWARD_DELAY_SEC
                        gpsAccuracy = TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M
                        altitudeWindowSec = TuningPreferencesDefaults.ALTITUDE_FILTER_WINDOW_SEC
                        slopeMinSpeed = TuningPreferencesDefaults.SLOPE_MIN_SPEED_MPS
                        paceCeilingMinKm = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM
                        routeSelectionRadiusKm = TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM
                        navigationCueTransparency = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY
                        navigationCueDismissDurationSec = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
                        elevationSmoothingSigmaMeters = TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS
                        onSettingsChanged?.invoke()
                        Toast.makeText(context, context.getString(R.string.reset_to_defaults_success), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
