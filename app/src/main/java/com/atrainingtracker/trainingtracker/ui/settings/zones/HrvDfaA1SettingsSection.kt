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

package com.atrainingtracker.trainingtracker.ui.settings.zones

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.settings.ZoneDisplayOptionsToggleList
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.tracking.HrvInfoBottomSheet
import com.atrainingtracker.trainingtracker.ui.tracking.HrvZoneIndicator
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Settings content for the dedicated HRV DFA a1 physiological zones tab (REQ-TRK-061.4 / ATT-7317).
 *
 * Displays:
 * 1. Explanatory intro text and optional CTA to launch [onOpenHrvTest] (Guided DFA a1 threshold test)
 *    positioned immediately above the zone cards and threshold controls (`REQ-TRK-072.7`).
 * 2. 3 inverse-intensity physiological bands (Zone 2/Green > T1, Zone 3/Orange T2..T1, Zone 4/Red < T2)
 *    conforming to [TTColor] and Rule 71.
 * 3. Fine 0.01-step threshold controls for T1 (aerobic, default 0.75) and T2 (anaerobic, default 0.50),
 *    strictly enforcing T1 >= T2 + 0.05 invariant.
 * 4. Reset to Defaults (0.75 / 0.50) action.
 * 5. Info sheet action opening [HrvInfoBottomSheet] with live T1 and T2 values.
 * 6. Live theme-aware [SensorFieldView] preview with interactive pills for Aerobic, Transition, and Anaerobic.
 */
@Composable
fun HrvDfaA1SettingsContent(
    tuningPreferencesDataStore: TuningPreferencesDataStore? = null,
    settingsDataStore: SettingsDataStore? = null,
    onOpenHrvTest: (() -> Unit)? = null,
    onSliderGestureActiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dataStore = tuningPreferencesDataStore ?: remember { TuningPreferencesDataStore.getInstance(context) }
    val appSettingsDataStore = settingsDataStore ?: remember { SettingsDataStore.getInstance(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val t1 by dataStore.dfaA1ThresholdT1Flow.collectAsState(
        initial = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1
    )
    val t2 by dataStore.dfaA1ThresholdT2Flow.collectAsState(
        initial = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2
    )

    var showInfoSheet by rememberSaveable { mutableStateOf(false) }

    val displayOptions by appSettingsDataStore.hrvZoneDisplayOptionsFlow.collectAsState(
        initial = appSettingsDataStore.getHrvZoneDisplayOptionsSync()
    )
    var selectedPreviewBand by rememberSaveable { mutableIntStateOf(2) } // 2=Aerobic, 3=Transition, 4=Anaerobic

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("HrvDfaA1SettingsScrollColumn")
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Header with Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.hrv_info_zones_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )

            IconButton(onClick = { showInfoSheet = true }) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(R.string.hrv_info_title),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Guidance text
        Text(
            text = stringResource(R.string.hrv_zones_guidance_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (onOpenHrvTest != null) {
            OutlinedButton(
                onClick = onOpenHrvTest,
                modifier = Modifier.fillMaxWidth().testTag("HrvGuidedThresholdTestCta"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.hrv_start_guided_ramp_test), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 3 Inverse-Intensity Physiological Bands
        // Zone 2: Aerobic Base (a1 > T1)
        HrvZoneBandCard(
            zoneLabel = stringResource(R.string.hrv_zone_aerobic_base_label),
            rangeDescription = String.format(Locale.US, "α1 > %.2f", t1),
            guidance = stringResource(R.string.hrv_zone_aerobic_base_desc),
            containerColor = TTColor.Zone2
        )

        // Zone 3: Threshold Transition (T2 <= a1 <= T1)
        HrvZoneBandCard(
            zoneLabel = stringResource(R.string.hrv_zone_threshold_transition_label),
            rangeDescription = String.format(Locale.US, "%.2f ≤ α1 ≤ %.2f", t2, t1),
            guidance = stringResource(R.string.hrv_zone_threshold_transition_desc),
            containerColor = TTColor.Zone3
        )

        // Zone 4: High Intensity / Anaerobic (a1 < T2)
        HrvZoneBandCard(
            zoneLabel = stringResource(R.string.hrv_zone_anaerobic_label),
            rangeDescription = String.format(Locale.US, "α1 < %.2f", t2),
            guidance = stringResource(R.string.hrv_zone_anaerobic_desc),
            containerColor = TTColor.Zone4
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Threshold Controls Header with Reset to Defaults
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.title_edit_zones),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            TextButton(
                onClick = {
                    scope.launch {
                        dataStore.setDfaA1Thresholds(
                            TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1,
                            TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2
                        )
                    }
                },
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.reset_to_defaults),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Unified 3-Zone Colored Dual-Thumb RangeSlider
        HrvDfaA1RangeSlider(
            t1 = t1,
            t2 = t2,
            onThresholdsChange = { newT1, newT2 ->
                scope.launch {
                    dataStore.setDfaA1Thresholds(newT1, newT2)
                }
            },
            onSliderGestureActiveChange = onSliderGestureActiveChange
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Fine-Tuning Stepper Controls (0.01 step precision)
        HrvThresholdControlRow(
            title = stringResource(R.string.hrv_t1_aerobic_threshold_label),
            value = t1,
            onValueChange = { newVal ->
                val rounded = Math.round(newVal * 100f) / 100f
                val clamped = rounded.coerceIn(
                    t2 + TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP,
                    1.20f
                )
                scope.launch {
                    dataStore.setDfaA1ThresholdT1(clamped)
                }
            },
            valueRange = 0.60f..1.20f,
            minAllowed = t2 + TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP,
            maxAllowed = 1.20f,
            helperText = stringResource(R.string.hrv_t1_aerobic_threshold_desc),
            showSlider = false,
            onSliderGestureActiveChange = onSliderGestureActiveChange
        )

        Spacer(modifier = Modifier.height(4.dp))

        HrvThresholdControlRow(
            title = stringResource(R.string.hrv_t2_anaerobic_threshold_label),
            value = t2,
            onValueChange = { newVal ->
                val rounded = Math.round(newVal * 100f) / 100f
                val clamped = rounded.coerceIn(
                    0.30f,
                    t1 - TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP
                )
                scope.launch {
                    dataStore.setDfaA1ThresholdT2(clamped)
                }
            },
            valueRange = 0.30f..0.80f,
            minAllowed = 0.30f,
            maxAllowed = t1 - TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP,
            helperText = stringResource(R.string.hrv_t2_anaerobic_threshold_desc),
            showSlider = false,
            onSliderGestureActiveChange = onSliderGestureActiveChange
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Section: Zone Visualization Options
        Text(
            text = stringResource(R.string.zone_display_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                ZoneDisplayOptionsToggleList(
                    displayOptions = displayOptions,
                    onUpdateDisplayOptions = { updated ->
                        scope.launch { appSettingsDataStore.saveHrvZoneDisplayOptions(updated) }
                    },
                    showGaugeToggle = false
                ) { label, checked, onCheckedChange ->
                    ZoneDisplayToggleRow(
                        label = label,
                        checked = checked,
                        onCheckedChange = onCheckedChange
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Section: Live Theme-Aware SensorFieldView Preview
        Text(
            text = stringResource(R.string.zone_display_preview_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ZonePreviewPill(
                label = stringResource(R.string.hrv_preview_pill_aerobic),
                zoneColor = TTColor.Zone2,
                selected = selectedPreviewBand == 2,
                onClick = { selectedPreviewBand = 2 },
                modifier = Modifier.weight(1f)
            )
            ZonePreviewPill(
                label = stringResource(R.string.hrv_preview_pill_transition),
                zoneColor = TTColor.Zone3,
                selected = selectedPreviewBand == 3,
                onClick = { selectedPreviewBand = 3 },
                modifier = Modifier.weight(1f)
            )
            ZonePreviewPill(
                label = stringResource(R.string.hrv_preview_pill_anaerobic),
                zoneColor = TTColor.Zone4,
                selected = selectedPreviewBand == 4,
                onClick = { selectedPreviewBand = 4 },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val previewAlpha1 = when (selectedPreviewBand) {
            2 -> (t1 + 0.15f).coerceAtMost(1.10f).toDouble()
            3 -> ((t1 + t2) / 2.0)
            else -> (t2 - 0.10f).coerceAtLeast(0.30f).toDouble()
        }
        val previewZoneColor = when (selectedPreviewBand) {
            2 -> TTColor.Zone2
            3 -> TTColor.Zone3
            else -> TTColor.Zone4
        }
        val localizedHrvLabels = listOf(
            stringResource(R.string.hrv_preview_pill_aerobic),
            stringResource(R.string.hrv_preview_pill_transition),
            stringResource(R.string.hrv_preview_pill_anaerobic)
        )
        val previewGaugeState = HrvZoneIndicator.calculateZoneGaugeState(
            alpha1 = previewAlpha1,
            t1 = t1,
            t2 = t2,
            zoneLabels = localizedHrvLabels
        )

        val previewFieldState = SensorFieldState(
            configHash = 0,
            sensorFieldId = 0,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = stringResource(R.string.hrv_dfa_a1),
            filterDescription = "",
            value = String.format(Locale.US, "%.2f", previewAlpha1),
            units = "",
            zoneColor = previewZoneColor,
            zoneDisplayOptions = displayOptions,
            zoneGaugeState = previewGaugeState,
            showValue = true,
            showBar = true
        )

        ZoneLivePreviewTileCard(fieldState = previewFieldState)

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showInfoSheet) {
        HrvInfoBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            dfaA1ThresholdT1 = t1,
            dfaA1ThresholdT2 = t2
        )
    }
}

/**
 * Renders an inverse-intensity physiological band card with a solid left indicator strip
 * and semantic [TTColor] background tint (REQ-TRK-061.4).
 */
@Composable
fun HrvZoneBandCard(
    zoneLabel: String,
    rangeDescription: String,
    guidance: String,
    containerColor: androidx.compose.ui.graphics.Color
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor.copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Spacer(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(containerColor)
            )
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = zoneLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = containerColor.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = rangeDescription,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = guidance,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Creates a gesture-isolation [Modifier] that disables ancestor `HorizontalPager` horizontal
 * scrolling (`userScrollEnabled = false`) immediately on `PointerEventPass.Initial` pointer down,
 * tracks M3 [MutableInteractionSource] press/drag states, consumes residual horizontal-dominant
 * deltas on `PointerEventPass.Main` (while leaving vertical movement for `verticalScroll`), and
 * resets cleanly on release, cancel, or disposal (`REQ-TRK-086.1`).
 */
@Composable
private fun rememberSliderGestureIsolationModifier(
    vararg interactionSources: MutableInteractionSource,
    onGestureActiveChange: (Boolean) -> Unit
): Modifier {
    val currentOnGestureActiveChange by rememberUpdatedState(onGestureActiveChange)
    var isPointerActive by remember { mutableStateOf(false) }

    val interactionActiveStates = interactionSources.map { source ->
        val isPressed by source.collectIsPressedAsState()
        val isDragged by source.collectIsDraggedAsState()
        isPressed || isDragged
    }
    val isAnyInteractionActive = interactionActiveStates.any { it }
    val currentAnyInteractionActive by rememberUpdatedState(isAnyInteractionActive)
    val isGestureActive = isPointerActive || isAnyInteractionActive

    var lastReportedActive by remember { mutableStateOf<Boolean?>(null) }
    val reportGestureActive: (Boolean) -> Unit = { active ->
        if (lastReportedActive != active) {
            lastReportedActive = active
            currentOnGestureActiveChange(active)
        }
    }

    LaunchedEffect(isGestureActive) {
        reportGestureActive(isGestureActive)
    }

    DisposableEffect(Unit) {
        onDispose {
            isPointerActive = false
            reportGestureActive(false)
        }
    }

    return Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            isPointerActive = true
            reportGestureActive(true)
            try {
                while (true) {
                    val initialEvent = awaitPointerEvent(PointerEventPass.Initial)
                    if (initialEvent.changes.none { it.pressed }) break
                    val mainEvent = awaitPointerEvent(PointerEventPass.Main)
                    mainEvent.changes.forEach { change ->
                        val delta = change.positionChange()
                        if (!change.isConsumed && abs(delta.x) > abs(delta.y) && delta.x != 0f) {
                            change.consume()
                        }
                    }
                    if (mainEvent.changes.none { it.pressed }) break
                }
            } finally {
                isPointerActive = false
                reportGestureActive(currentAnyInteractionActive)
            }
        }
    }
}

/**
 * Unified 3-zone colored dual-thumb range slider for DFA α1 thresholds (T1 and T2)
 * across continuous domain 0.30..1.20 with semantic track colors (REQ-TRK-061.4a, REQ-TRK-061.4b, REQ-TRK-086).
 * Inverted orientation renders Zone 2 (Green) on left and Zone 4 (Red) on right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HrvDfaA1RangeSlider(
    t1: Float,
    t2: Float,
    onThresholdsChange: (t1: Float, t2: Float) -> Unit,
    onSliderGestureActiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val domainStart = 0.30f
    val domainEnd = 1.20f
    val gap = TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP

    val startInteractionSource = remember { MutableInteractionSource() }
    val endInteractionSource = remember { MutableInteractionSource() }
    val isolationModifier = rememberSliderGestureIsolationModifier(
        startInteractionSource,
        endInteractionSource,
        onGestureActiveChange = onSliderGestureActiveChange
    )

    var isDragging by remember { mutableStateOf(false) }
    var awaitSyncAfterDrag by remember { mutableStateOf(false) }
    var dragT1 by remember { mutableFloatStateOf(t1) }
    var dragT2 by remember { mutableFloatStateOf(t2) }
    var lastEmittedT1 by remember { mutableFloatStateOf(t1) }
    var lastEmittedT2 by remember { mutableFloatStateOf(t2) }

    LaunchedEffect(t1, t2, isDragging) {
        if (!isDragging) {
            if (awaitSyncAfterDrag) {
                val synced = abs(t1 - lastEmittedT1) < 0.001f && abs(t2 - lastEmittedT2) < 0.001f
                if (synced) {
                    awaitSyncAfterDrag = false
                    dragT1 = t1
                    dragT2 = t2
                }
            } else {
                dragT1 = t1
                dragT2 = t2
                lastEmittedT1 = t1
                lastEmittedT2 = t2
            }
        }
    }

    LaunchedEffect(awaitSyncAfterDrag) {
        if (awaitSyncAfterDrag) {
            kotlinx.coroutines.delay(300L)
            awaitSyncAfterDrag = false
            dragT1 = t1
            dragT2 = t2
            lastEmittedT1 = t1
            lastEmittedT2 = t2
        }
    }

    val effectiveT1 = if (isDragging || awaitSyncAfterDrag) dragT1 else t1
    val effectiveT2 = if (isDragging || awaitSyncAfterDrag) dragT2 else t2
    val displayT1 = Math.round(effectiveT1 * 100f) / 100f
    val displayT2 = Math.round(effectiveT2 * 100f) / 100f

    // Inverted coordinate transform: s = domainEnd + domainStart - alpha1 (1.50f - alpha1)
    // Left thumb (sliderStart) maps to T1 (Green, Zone 2); Right thumb (sliderEnd) maps to T2 (Red, Zone 4)
    val sliderStart = domainEnd + domainStart - effectiveT1
    val sliderEnd = domainEnd + domainStart - effectiveT2

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("HrvDfaA1RangeSliderCard")
            .then(isolationModifier),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Zone label summary row: Zone 2 (Green, left) | Zone 3 (Orange, center) | Zone 4 (Red, right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "T1: %.2f", displayT1),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TTColor.Zone2
                )
                Text(
                    text = stringResource(R.string.hrv_preview_pill_transition),
                    style = MaterialTheme.typography.labelSmall,
                    color = TTColor.Zone3
                )
                Text(
                    text = String.format(Locale.US, "T2: %.2f", displayT2),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TTColor.Zone4
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            RangeSlider(
                value = sliderStart..sliderEnd,
                onValueChange = { range ->
                    isDragging = true
                    awaitSyncAfterDrag = false
                    val newT1Raw = domainEnd + domainStart - range.start
                    val newT2Raw = domainEnd + domainStart - range.endInclusive

                    val prevRoundedT1 = Math.round(dragT1 * 100f) / 100f
                    val prevRoundedT2 = Math.round(dragT2 * 100f) / 100f
                    val rawMovedT1 = abs(newT1Raw - dragT1) >= abs(newT2Raw - dragT2)

                    val (clampedRawT1, clampedRawT2) = if (rawMovedT1 && abs(newT1Raw - dragT1) > 0.0001f) {
                        val cT1 = newT1Raw.coerceIn(dragT2 + gap, domainEnd)
                        cT1 to dragT2.coerceIn(domainStart, cT1 - gap)
                    } else if (abs(newT2Raw - dragT2) > 0.0001f) {
                        val cT2 = newT2Raw.coerceIn(domainStart, dragT1 - gap)
                        dragT1.coerceIn(cT2 + gap, domainEnd) to cT2
                    } else {
                        val cT1 = newT1Raw.coerceIn(domainStart + gap, domainEnd)
                        val cT2 = newT2Raw.coerceIn(domainStart, cT1 - gap)
                        cT1 to cT2
                    }

                    dragT1 = clampedRawT1
                    dragT2 = clampedRawT2

                    val roundedT1 = (Math.round(clampedRawT1 * 100f) / 100f).coerceIn(domainStart + gap, domainEnd)
                    val roundedT2 = (Math.round(clampedRawT2 * 100f) / 100f).coerceIn(domainStart, roundedT1 - gap)
                    if (roundedT1 != prevRoundedT1 || roundedT2 != prevRoundedT2 ||
                        roundedT1 != lastEmittedT1 || roundedT2 != lastEmittedT2
                    ) {
                        lastEmittedT1 = roundedT1
                        lastEmittedT2 = roundedT2
                        onThresholdsChange(roundedT1, roundedT2)
                    }
                },
                onValueChangeFinished = {
                    val finalT1 = (Math.round(dragT1 * 100f) / 100f).coerceIn(domainStart + gap, domainEnd)
                    val finalT2 = (Math.round(dragT2 * 100f) / 100f).coerceIn(domainStart, finalT1 - gap)
                    dragT1 = finalT1
                    dragT2 = finalT2
                    if (finalT1 != lastEmittedT1 || finalT2 != lastEmittedT2) {
                        lastEmittedT1 = finalT1
                        lastEmittedT2 = finalT2
                        onThresholdsChange(finalT1, finalT2)
                    }
                    isDragging = false
                    awaitSyncAfterDrag = true
                },
                valueRange = 0.30f..1.20f,
                startInteractionSource = startInteractionSource,
                endInteractionSource = endInteractionSource,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = TTColor.Zone3,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                track = { rangeSliderState ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        val w = size.width
                        val h = size.height

                        val span = (rangeSliderState.valueRange.endInclusive - rangeSliderState.valueRange.start)
                            .coerceAtLeast(0.0001f)
                        val fracStart = ((rangeSliderState.activeRangeStart - rangeSliderState.valueRange.start) / span)
                            .coerceIn(0f, 1f)
                        val fracEnd = ((rangeSliderState.activeRangeEnd - rangeSliderState.valueRange.start) / span)
                            .coerceIn(0f, 1f)

                        val xStart = w * fracStart
                        val xEnd = w * fracEnd

                        // Zone 2: Green (alpha1 > T1, left side of slider)
                        drawRect(
                            color = TTColor.Zone2,
                            topLeft = Offset(0f, 0f),
                            size = Size(xStart, h)
                        )
                        // Zone 3: Orange (T2 <= alpha1 <= T1, middle segment)
                        drawRect(
                            color = TTColor.Zone3,
                            topLeft = Offset(xStart, 0f),
                            size = Size((xEnd - xStart).coerceAtLeast(0f), h)
                        )
                        // Zone 4: Red (alpha1 < T2, right side of slider)
                        drawRect(
                            color = TTColor.Zone4,
                            topLeft = Offset(xEnd, 0f),
                            size = Size((w - xEnd).coerceAtLeast(0f), h)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("HrvDfaA1RangeSlider")
            )
        }
    }
}

/**
 * Renders a fine 0.01-step threshold control row with stepper buttons and an optional continuous slider (REQ-TRK-061.4, REQ-TRK-086).
 */
@Composable
fun HrvThresholdControlRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    minAllowed: Float,
    maxAllowed: Float,
    helperText: String,
    showSlider: Boolean = true,
    onSliderGestureActiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val sliderIsolationModifier = if (showSlider) {
        rememberSliderGestureIsolationModifier(
            sliderInteractionSource,
            onGestureActiveChange = onSliderGestureActiveChange
        )
    } else {
        Modifier
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = helperText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val next = (Math.round((value - 0.01f) * 100f) / 100f).coerceIn(minAllowed, maxAllowed)
                            onValueChange(next)
                        },
                        modifier = Modifier.size(32.dp),
                        enabled = value > minAllowed + 0.001f
                    ) {
                        Text("-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = String.format(Locale.US, "%.2f", value),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    FilledTonalIconButton(
                        onClick = {
                            val next = (Math.round((value + 0.01f) * 100f) / 100f).coerceIn(minAllowed, maxAllowed)
                            onValueChange(next)
                        },
                        modifier = Modifier.size(32.dp),
                        enabled = value < maxAllowed - 0.001f
                    ) {
                        Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (showSlider) {
                Slider(
                    value = value.coerceIn(minAllowed, maxAllowed),
                    onValueChange = { newVal ->
                        val rounded = Math.round(newVal * 100f) / 100f
                        val clamped = rounded.coerceIn(minAllowed, maxAllowed)
                        onValueChange(clamped)
                    },
                    valueRange = valueRange,
                    interactionSource = sliderInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("HrvThresholdControlSlider")
                        .then(sliderIsolationModifier)
                )
            }
        }
    }
}