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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.EditWorkoutFieldPreferences
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapDisplayMode
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitTypography
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Advanced Tuning and Settings dialog structured into modular, collapsible
 * Material 3 accordion subsections with live active-value summary subtitles (REQ-UI-222).
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
    val persistedEditWorkoutPrefs by preferenceManager.editWorkoutFieldPreferencesFlow.collectAsState(initial = null)

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
    var cockpitFontFamily by remember { mutableStateOf(TuningPreferencesDefaults.COCKPIT_FONT_FAMILY) }
    var cockpitFontWeight by remember { mutableStateOf(TuningPreferencesDefaults.COCKPIT_FONT_WEIGHT) }

    var workoutCardPrefs by remember { mutableStateOf(WorkoutCardSectionPreferences()) }
    var editWorkoutPrefs by remember { mutableStateOf(EditWorkoutFieldPreferences()) }
    var isAftermathPrefsInitialized by remember { mutableStateOf(false) }

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

    LaunchedEffect(persistedWorkoutCardPrefs, persistedEditWorkoutPrefs) {
        val cardPrefs = persistedWorkoutCardPrefs
        val editPrefs = persistedEditWorkoutPrefs
        if (!isAftermathPrefsInitialized && cardPrefs != null && editPrefs != null) {
            workoutCardPrefs = cardPrefs
            editWorkoutPrefs = editPrefs
            isAftermathPrefsInitialized = true
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
                        slopeMinSpeedMps = slopeMinSpeed
                    )
                    scope.launch {
                        tuningDataStore.saveTuningConfig(newConfig)
                        preferenceManager.setWorkoutCardPreferences(workoutCardPrefs)
                        preferenceManager.setEditWorkoutFieldPreferences(editWorkoutPrefs)
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
                    onTelemetryDomainChange = { telemetryXAxisDomain = it }
                )
            }

            // Section 5: Workout-Masken & Detailkarten
            TuningAccordionSection(
                icon = Icons.AutoMirrored.Filled.ViewList,
                title = stringResource(R.string.tuning_cat_workout_masks_cards),
                subtitle = TuningSubtitleFormatter.formatWorkoutMasksSubtitle(workoutCardPrefs, editWorkoutPrefs, context),
                isExpanded = isSectionExpanded(TuningSection.WORKOUT_MASKS_CARDS),
                onToggle = { toggleSection(TuningSection.WORKOUT_MASKS_CARDS) }
            ) {
                WorkoutMasksAndCardsSection(
                    workoutCardPrefs = workoutCardPrefs,
                    onWorkoutCardPrefsChange = { workoutCardPrefs = it },
                    editWorkoutPrefs = editWorkoutPrefs,
                    onEditWorkoutPrefsChange = { editWorkoutPrefs = it }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Prominent Reset to Factory Defaults Action Button
            OutlinedButton(
                onClick = {
                    scope.launch {
                        tuningDataStore.resetToDefaults()
                        preferenceManager.setWorkoutCardPreferences(WorkoutCardSectionPreferences())
                        preferenceManager.setEditWorkoutFieldPreferences(EditWorkoutFieldPreferences())
                        workoutCardPrefs = WorkoutCardSectionPreferences()
                        editWorkoutPrefs = EditWorkoutFieldPreferences()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CockpitTypographySection(
    cockpitFontFamily: CockpitFontFamily,
    onFontFamilyChange: (CockpitFontFamily) -> Unit,
    cockpitFontWeight: CockpitFontWeight,
    onFontWeightChange: (CockpitFontWeight) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Font Family Selector
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.tuning_cockpit_font_family_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = stringResource(cockpitFontFamily.getDisplayNameRes()),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = CockpitTypography.resolveFontFamily(cockpitFontFamily),
                        fontWeight = cockpitFontWeight.asFontWeight()
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    CockpitFontFamily.values().forEach { family ->
                        val itemFontFamily = remember(family) {
                            CockpitTypography.resolveFontFamily(family)
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(family.getDisplayNameRes()),
                                    fontFamily = itemFontFamily,
                                    fontWeight = cockpitFontWeight.asFontWeight()
                                )
                            },
                            onClick = {
                                onFontFamilyChange(family)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Boldness (Font Weight) Selector
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.tuning_cockpit_font_weight_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CockpitFontWeight.values().forEach { weight ->
                    FilterChip(
                        selected = cockpitFontWeight == weight,
                        onClick = { onFontWeightChange(weight) },
                        label = {
                            Text(
                                text = stringResource(weight.getDisplayNameRes()),
                                fontWeight = weight.asFontWeight(),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Live Preview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.tuning_cockpit_preview_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val previewFamily = remember(cockpitFontFamily) {
                    CockpitTypography.resolveFontFamily(cockpitFontFamily)
                }
                val previewWeight = cockpitFontWeight.asFontWeight()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "148",
                            fontFamily = previewFamily,
                            fontWeight = previewWeight,
                            fontSize = 30.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "bpm",
                            fontFamily = previewFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "28.5",
                            fontFamily = previewFamily,
                            fontWeight = previewWeight,
                            fontSize = 30.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "km/h",
                            fontFamily = previewFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "1:24:35",
                            fontFamily = previewFamily,
                            fontWeight = previewWeight,
                            fontSize = 30.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "TIME",
                            fontFamily = previewFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AmoledBatterySaverSection(
    fullDimFactor: Float,
    onFullDimChange: (Float) -> Unit,
    mediumDimFactor: Float,
    onMediumDimChange: (Float) -> Unit,
    slopeFlat: Float,
    onSlopeFlatChange: (Float) -> Unit,
    slopeSteep: Float,
    onSlopeSteepChange: (Float) -> Unit,
    wakeupSec: Int,
    onWakeupSecChange: (Int) -> Unit,
    downwardDelaySec: Int,
    onDownwardDelayChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TuningSliderItem(
            title = stringResource(R.string.tuning_full_dim_factor_title),
            valueText = "${(fullDimFactor * 100).roundToInt()}%",
            helperText = stringResource(R.string.tuning_full_dim_factor_desc),
            defaultText = stringResource(R.string.tuning_default_format, "25%"),
            value = fullDimFactor,
            onValueChange = {
                onFullDimChange(it)
                if (it > mediumDimFactor) {
                    onMediumDimChange(it)
                }
            },
            valueRange = TuningPreferencesDefaults.MIN_FULL_DIM_FACTOR..TuningPreferencesDefaults.MAX_FULL_DIM_FACTOR,
            steps = 45
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_medium_dim_factor_title),
            valueText = "${(mediumDimFactor * 100).roundToInt()}%",
            helperText = stringResource(R.string.tuning_medium_dim_factor_desc),
            defaultText = stringResource(R.string.tuning_default_format, "50%"),
            value = mediumDimFactor,
            onValueChange = {
                onMediumDimChange(it)
                if (it < fullDimFactor) {
                    onFullDimChange(it)
                }
            },
            valueRange = TuningPreferencesDefaults.MIN_MEDIUM_DIM_FACTOR..TuningPreferencesDefaults.MAX_MEDIUM_DIM_FACTOR,
            steps = 70
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_slope_flat_title),
            valueText = String.format(Locale.getDefault(), "%.1f%%", slopeFlat),
            helperText = stringResource(R.string.tuning_slope_flat_desc),
            defaultText = stringResource(R.string.tuning_default_format, "2.0%"),
            value = slopeFlat,
            onValueChange = {
                onSlopeFlatChange(it)
                if (it > slopeSteep) {
                    onSlopeSteepChange(it)
                }
            },
            valueRange = TuningPreferencesDefaults.MIN_SLOPE_FLAT..TuningPreferencesDefaults.MAX_SLOPE_FLAT,
            steps = 10
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_slope_steep_title),
            valueText = String.format(Locale.getDefault(), "%.1f%%", slopeSteep),
            helperText = stringResource(R.string.tuning_slope_steep_desc),
            defaultText = stringResource(R.string.tuning_default_format, "5.0%"),
            value = slopeSteep,
            onValueChange = {
                onSlopeSteepChange(it)
                if (it < slopeFlat) {
                    onSlopeFlatChange(it)
                }
            },
            valueRange = TuningPreferencesDefaults.MIN_SLOPE_STEEP..TuningPreferencesDefaults.MAX_SLOPE_STEEP,
            steps = 26
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_wakeup_duration_title),
            valueText = "$wakeupSec s",
            helperText = stringResource(R.string.tuning_wakeup_duration_desc),
            defaultText = stringResource(R.string.tuning_default_format, "15 s"),
            value = wakeupSec.toFloat(),
            onValueChange = { onWakeupSecChange(it.roundToInt()) },
            valueRange = TuningPreferencesDefaults.MIN_WAKEUP_SEC.toFloat()..TuningPreferencesDefaults.MAX_WAKEUP_SEC.toFloat(),
            steps = 55
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_downward_delay_title),
            valueText = "$downwardDelaySec s",
            helperText = stringResource(R.string.tuning_downward_delay_desc),
            defaultText = stringResource(R.string.tuning_default_format, "3 s"),
            value = downwardDelaySec.toFloat(),
            onValueChange = { onDownwardDelayChange(it.roundToInt()) },
            valueRange = TuningPreferencesDefaults.MIN_DOWNWARD_DELAY_SEC.toFloat()..TuningPreferencesDefaults.MAX_DOWNWARD_DELAY_SEC.toFloat(),
            steps = 14
        )
    }
}

@Composable
fun SensorsGpsFilterSection(
    gpsAccuracy: Float,
    onGpsAccuracyChange: (Float) -> Unit,
    altitudeWindowSec: Int,
    onAltitudeWindowChange: (Int) -> Unit,
    slopeMinSpeed: Float,
    onSlopeMinSpeedChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TuningSliderItem(
            title = stringResource(R.string.tuning_gps_accuracy_title),
            valueText = "${gpsAccuracy.roundToInt()} m",
            helperText = stringResource(R.string.tuning_gps_accuracy_desc),
            defaultText = stringResource(R.string.tuning_default_format, "200 m"),
            value = gpsAccuracy,
            onValueChange = onGpsAccuracyChange,
            valueRange = TuningPreferencesDefaults.MIN_GPS_ACCURACY_M..TuningPreferencesDefaults.MAX_GPS_ACCURACY_M,
            steps = 49
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_altitude_window_title),
            valueText = "$altitudeWindowSec s",
            helperText = stringResource(R.string.tuning_altitude_window_desc),
            defaultText = stringResource(R.string.tuning_default_format, "21 s"),
            value = altitudeWindowSec.toFloat(),
            onValueChange = { onAltitudeWindowChange(it.roundToInt()) },
            valueRange = TuningPreferencesDefaults.MIN_ALTITUDE_WINDOW_SEC.toFloat()..TuningPreferencesDefaults.MAX_ALTITUDE_WINDOW_SEC.toFloat(),
            steps = 55
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_slope_min_speed_title),
            valueText = String.format(Locale.getDefault(), "%.1f m/s (%.1f km/h)", slopeMinSpeed, slopeMinSpeed * 3.6f),
            helperText = stringResource(R.string.tuning_slope_min_speed_desc),
            defaultText = stringResource(R.string.tuning_default_format, "0.5 m/s"),
            value = slopeMinSpeed,
            onValueChange = onSlopeMinSpeedChange,
            valueRange = TuningPreferencesDefaults.MIN_SLOPE_SPEED_MPS..TuningPreferencesDefaults.MAX_SLOPE_SPEED_MPS,
            steps = 18
        )
    }
}

@Composable
fun AftermathAnalysisSection(
    elevationXAxisDomain: ProfileXAxisDomain,
    onElevationDomainChange: (ProfileXAxisDomain) -> Unit,
    telemetryXAxisDomain: ProfileXAxisDomain,
    onTelemetryDomainChange: (ProfileXAxisDomain) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Elevation Profile X-Axis Domain (defaults to Distance)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.tuning_profile_x_axis_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.tuning_profile_x_axis_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = elevationXAxisDomain == ProfileXAxisDomain.DISTANCE,
                    onClick = { onElevationDomainChange(ProfileXAxisDomain.DISTANCE) },
                    label = { Text(stringResource(R.string.tuning_profile_x_axis_distance)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = elevationXAxisDomain == ProfileXAxisDomain.TIME,
                    onClick = { onElevationDomainChange(ProfileXAxisDomain.TIME) },
                    label = { Text(stringResource(R.string.tuning_profile_x_axis_time)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Telemetry Graphs X-Axis Domain (defaults to Time)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.tuning_telemetry_x_axis_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.tuning_telemetry_x_axis_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = telemetryXAxisDomain == ProfileXAxisDomain.DISTANCE,
                    onClick = { onTelemetryDomainChange(ProfileXAxisDomain.DISTANCE) },
                    label = { Text(stringResource(R.string.tuning_profile_x_axis_distance)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = telemetryXAxisDomain == ProfileXAxisDomain.TIME,
                    onClick = { onTelemetryDomainChange(ProfileXAxisDomain.TIME) },
                    label = { Text(stringResource(R.string.tuning_profile_x_axis_time)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Deprecated("Use overload accepting elevationXAxisDomain and telemetryXAxisDomain")
@Composable
fun AftermathAnalysisSection(
    profileXAxisDomain: ProfileXAxisDomain,
    onDomainChange: (ProfileXAxisDomain) -> Unit
) {
    AftermathAnalysisSection(
        elevationXAxisDomain = profileXAxisDomain,
        onElevationDomainChange = onDomainChange,
        telemetryXAxisDomain = profileXAxisDomain,
        onTelemetryDomainChange = onDomainChange
    )
}

@Composable
fun WorkoutMasksAndCardsSection(
    workoutCardPrefs: WorkoutCardSectionPreferences,
    onWorkoutCardPrefsChange: (WorkoutCardSectionPreferences) -> Unit,
    editWorkoutPrefs: EditWorkoutFieldPreferences,
    onEditWorkoutPrefsChange: (EditWorkoutFieldPreferences) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sub-block 1: Workout List (Detailed Cards)
        Text(
            text = stringResource(R.string.settings_workout_card_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        TuningToggleItem(
            title = stringResource(R.string.settings_workout_card_description),
            isChecked = workoutCardPrefs.showDescription,
            onCheckedChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showDescription = it)) }
        )
        TuningToggleItem(
            title = stringResource(R.string.settings_workout_card_extrema),
            isChecked = workoutCardPrefs.showExtrema,
            onCheckedChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showExtrema = it)) }
        )
        TuningToggleItem(
            title = stringResource(R.string.settings_workout_card_laps),
            isChecked = workoutCardPrefs.showLaps,
            onCheckedChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showLaps = it)) }
        )
        if (workoutCardPrefs.showLaps) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_lap_display_mode_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = workoutCardPrefs.lapDisplayMode == LapDisplayMode.TABLE_ONLY,
                        onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(lapDisplayMode = LapDisplayMode.TABLE_ONLY)) },
                        label = { Text(stringResource(R.string.settings_lap_display_mode_table)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = workoutCardPrefs.lapDisplayMode == LapDisplayMode.VISUALIZER_ONLY,
                        onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(lapDisplayMode = LapDisplayMode.VISUALIZER_ONLY)) },
                        label = { Text(stringResource(R.string.settings_lap_display_mode_visualizer)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        TuningToggleItem(
            title = stringResource(R.string.settings_workout_card_strava),
            isChecked = workoutCardPrefs.showStrava,
            onCheckedChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showStrava = it)) }
        )
        TuningToggleItem(
            title = stringResource(R.string.settings_workout_card_map),
            isChecked = workoutCardPrefs.showMapPreview,
            onCheckedChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showMapPreview = it)) }
        )
        TuningToggleItem(
            title = stringResource(R.string.settings_workout_card_elevation),
            isChecked = workoutCardPrefs.showElevationProfile,
            onCheckedChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showElevationProfile = it)) }
        )
        TuningToggleItem(
            title = stringResource(R.string.settings_workout_card_charts),
            isChecked = workoutCardPrefs.showTelemetryCharts,
            onCheckedChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showTelemetryCharts = it)) }
        )
        TuningToggleItem(
            title = stringResource(R.string.settings_workout_card_zones),
            isChecked = workoutCardPrefs.showZoneAnalysis,
            onCheckedChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showZoneAnalysis = it)) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Sub-block 2: Edit Workout Fields
        Text(
            text = stringResource(R.string.settings_edit_workout_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        TuningToggleItem(
            title = stringResource(R.string.settings_edit_workout_description),
            isChecked = editWorkoutPrefs.showDescription,
            onCheckedChange = { onEditWorkoutPrefsChange(editWorkoutPrefs.copy(showDescription = it)) }
        )
        TuningToggleItem(
            title = stringResource(R.string.settings_edit_workout_cluster),
            isChecked = editWorkoutPrefs.showCluster,
            onCheckedChange = { onEditWorkoutPrefsChange(editWorkoutPrefs.copy(showCluster = it)) }
        )
        TuningToggleItem(
            title = stringResource(R.string.settings_edit_workout_commute_trainer),
            isChecked = editWorkoutPrefs.showCommuteTrainer,
            onCheckedChange = { onEditWorkoutPrefsChange(editWorkoutPrefs.copy(showCommuteTrainer = it)) }
        )
        TuningToggleItem(
            title = stringResource(R.string.settings_edit_workout_race),
            isChecked = editWorkoutPrefs.showRace,
            onCheckedChange = { onEditWorkoutPrefsChange(editWorkoutPrefs.copy(showRace = it)) }
        )
        TuningToggleItem(
            title = stringResource(R.string.settings_edit_workout_strava),
            isChecked = editWorkoutPrefs.showStravaUpload,
            onCheckedChange = { onEditWorkoutPrefsChange(editWorkoutPrefs.copy(showStravaUpload = it)) }
        )
        TuningToggleItem(
            title = stringResource(R.string.settings_edit_workout_goal),
            isChecked = editWorkoutPrefs.showGoal,
            onCheckedChange = { onEditWorkoutPrefsChange(editWorkoutPrefs.copy(showGoal = it)) }
        )
        TuningToggleItem(
            title = stringResource(R.string.settings_edit_workout_method),
            isChecked = editWorkoutPrefs.showMethod,
            onCheckedChange = { onEditWorkoutPrefsChange(editWorkoutPrefs.copy(showMethod = it)) }
        )
    }
}

@Composable
fun TuningSliderItem(
    title: String,
    valueText: String,
    helperText: String,
    defaultText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = helperText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = defaultText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
    }
}

@Composable
fun TuningToggleItem(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f)
        )
    }
}
