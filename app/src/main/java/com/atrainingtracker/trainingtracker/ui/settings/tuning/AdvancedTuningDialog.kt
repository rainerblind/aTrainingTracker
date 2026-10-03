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
import androidx.compose.material.icons.filled.RestartAlt
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
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorsGpsFilterSection
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSection
import kotlinx.coroutines.launch

/**
 * Advanced Tuning and Settings dialog structured into modular, collapsible
 * Material 3 accordion subsections with live active-value summary subtitles (REQ-UI-222, REQ-UI-262).
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

    var workoutCardPrefs by remember { mutableStateOf(WorkoutCardSectionPreferences()) }
    var isAftermathPrefsInitialized by remember { mutableStateOf(false) }

    var workoutDetailPrefs by remember { mutableStateOf(WorkoutDetailPreferences()) }
    var isDetailPrefsInitialized by remember { mutableStateOf(false) }

    var workoutSectionsOrder by remember { mutableStateOf(WorkoutSectionType.DEFAULT_ORDER) }
    var isSectionsOrderInitialized by remember { mutableStateOf(false) }

    // Multi-section expansion state tracked across configuration changes via string identifiers
    // Initially all sections are collapsed (emptySet) providing a clean, compact overview (ATT-1957)
    var expandedSections by rememberSaveable { mutableStateOf(emptySet<String>()) }

    fun isSectionExpanded(section: TuningSection): Boolean = expandedSections.contains(section.name)

    fun toggleSection(section: TuningSection) {
        expandedSections = if (expandedSections.contains(section.name)) {
            expandedSections - section.name
        } else {
            expandedSections + section.name
        }
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
                        fullDimFactor = fullDimFactor,
                        mediumDimFactor = mediumDimFactor,
                        slopeFlatThreshold = slopeFlat,
                        slopeSteepThreshold = slopeSteep,
                        wakeupDurationSec = wakeupSec,
                        downwardDelaySec = downwardDelaySec,
                        gpsAccuracyThresholdMeters = gpsAccuracy,
                        altitudeFilterWindowSec = altitudeWindowSec,
                        slopeMinSpeedMps = slopeMinSpeed,
                        paceCeilingMinKm = paceCeilingMinKm
                    )
                    scope.launch {
                        tuningDataStore.saveTuningConfig(newConfig)
                        preferenceManager.setWorkoutCardPreferences(workoutCardPrefs)
                        preferenceManager.setWorkoutDetailPreferences(workoutDetailPrefs)
                        preferenceManager.setWorkoutSectionsOrder(workoutSectionsOrder)
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
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.advanced_tuning_warning_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.advanced_tuning_warning_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Section 1: Cockpit & Typografie
            TuningAccordionSection(
                icon = Icons.Default.TextFields,
                title = stringResource(R.string.tuning_cat_cockpit_typography),
                subtitle = TuningSubtitleFormatter.formatCockpitSubtitle(cockpitFontFamily, cockpitFontWeight, context),
                isExpanded = isSectionExpanded(TuningSection.COCKPIT_TYPOGRAPHY),
                onToggle = { toggleSection(TuningSection.COCKPIT_TYPOGRAPHY) }
            ) {
                CockpitTypographySection(
                    cockpitFontFamily = cockpitFontFamily,
                    onFontFamilyChange = { cockpitFontFamily = it },
                    cockpitFontWeight = cockpitFontWeight,
                    onFontWeightChange = { cockpitFontWeight = it }
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
                    fullDimFactor = fullDimFactor,
                    onFullDimChange = { fullDimFactor = it },
                    mediumDimFactor = mediumDimFactor,
                    onMediumDimChange = { mediumDimFactor = it },
                    slopeFlat = slopeFlat,
                    onSlopeFlatChange = { slopeFlat = it },
                    slopeSteep = slopeSteep,
                    onSlopeSteepChange = { slopeSteep = it },
                    wakeupSec = wakeupSec,
                    onWakeupSecChange = { wakeupSec = it },
                    downwardDelaySec = downwardDelaySec,
                    onDownwardDelayChange = { downwardDelaySec = it }
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
                    gpsAccuracy = gpsAccuracy,
                    onGpsAccuracyChange = { gpsAccuracy = it },
                    altitudeWindowSec = altitudeWindowSec,
                    onAltitudeWindowChange = { altitudeWindowSec = it },
                    slopeMinSpeed = slopeMinSpeed,
                    onSlopeMinSpeedChange = { slopeMinSpeed = it }
                )
            }

            // Section 4: Aftermath & Analyse
            TuningAccordionSection(
                icon = Icons.AutoMirrored.Filled.ShowChart,
                title = stringResource(R.string.tuning_cat_aftermath),
                subtitle = TuningSubtitleFormatter.formatAftermathSubtitle(elevationXAxisDomain, telemetryXAxisDomain, context),
                isExpanded = isSectionExpanded(TuningSection.AFTERMATH_ANALYSIS),
                onToggle = { toggleSection(TuningSection.AFTERMATH_ANALYSIS) }
            ) {
                AftermathAnalysisSection(
                    elevationXAxisDomain = elevationXAxisDomain,
                    onElevationDomainChange = { elevationXAxisDomain = it },
                    telemetryXAxisDomain = telemetryXAxisDomain,
                    onTelemetryDomainChange = { telemetryXAxisDomain = it },
                    paceCeilingMinKm = paceCeilingMinKm,
                    onPaceCeilingChange = { paceCeilingMinKm = it }
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
                    workoutCardPrefs = workoutCardPrefs,
                    onWorkoutCardPrefsChange = { workoutCardPrefs = it },
                    workoutDetailPrefs = workoutDetailPrefs,
                    onWorkoutDetailPrefsChange = { workoutDetailPrefs = it },
                    workoutSectionsOrder = workoutSectionsOrder,
                    onWorkoutSectionsOrderChange = { workoutSectionsOrder = it }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Prominent Reset to Factory Defaults Action Button
            OutlinedButton(
                onClick = {
                    scope.launch {
                        tuningDataStore.resetToDefaults()
                        preferenceManager.setWorkoutCardPreferences(WorkoutCardSectionPreferences())
                        preferenceManager.setWorkoutDetailPreferences(WorkoutDetailPreferences())
                        preferenceManager.setWorkoutSectionsOrder(WorkoutSectionType.DEFAULT_ORDER)
                        workoutCardPrefs = WorkoutCardSectionPreferences()
                        workoutDetailPrefs = WorkoutDetailPreferences()
                        workoutSectionsOrder = WorkoutSectionType.DEFAULT_ORDER
                        elevationXAxisDomain = TuningPreferencesDefaults.ELEVATION_X_AXIS_DOMAIN
                        telemetryXAxisDomain = TuningPreferencesDefaults.TELEMETRY_X_AXIS_DOMAIN
                        cockpitFontFamily = TuningPreferencesDefaults.COCKPIT_FONT_FAMILY
                        cockpitFontWeight = TuningPreferencesDefaults.COCKPIT_FONT_WEIGHT
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
                        onSettingsChanged?.invoke()
                        Toast.makeText(
                            context,
                            context.getString(R.string.reset_to_defaults_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.reset_to_defaults))
            }
        }
    }
}
