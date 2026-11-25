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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceListViewModel
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.settings.ZoneProfileState
import com.atrainingtracker.trainingtracker.ui.settings.HrMethod
import com.atrainingtracker.trainingtracker.ui.settings.ZoneCalculatorDialog
import com.atrainingtracker.trainingtracker.ui.settings.ZoneCalculatorStateHolder
import com.atrainingtracker.trainingtracker.ui.settings.ZoneDisplayOptionsToggleList
import com.atrainingtracker.trainingtracker.ui.settings.ZoneHistoryDialog
import com.atrainingtracker.trainingtracker.ui.settings.ZoneOnboardingBanner
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.atrainingtracker.trainingtracker.ui.tracking.ZoneGaugeMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Traverses context hierarchy to safely resolve [Activity] without unsafe casting (REQ-UI-428.4).
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ZoneSettingsScreen(
    initialTab: Int = 0,
    promptReason: String? = null,
    onFinish: () -> Unit = {},
    onOpenDrawer: (() -> Unit)? = null,
    onNavigateToHrvTest: (() -> Unit)? = null,
    viewModel: ZonesSettingsViewModel = viewModel()
) {
    val context = LocalContext.current

    val view = androidx.compose.ui.platform.LocalView.current
    val isDarkTheme = isSystemInDarkTheme()

    // Force status bar icons to be dark when in Light Mode
    if (!view.isInEditMode) {
        SideEffect {
            val window = context.findActivity()?.window
            if (window != null) {
                val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)

                // appearanceLightStatusBars = true makes icons DARK
                // appearanceLightStatusBars = false makes icons WHITE
                windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
            }
        }
    }

    val dataStore = remember { SettingsDataStore.getInstance(context) }
    val scope = rememberCoroutineScope()

    val profileNameResIds = listOf(
        R.string.tab_hr_run,
        R.string.tab_hr_bike,
        R.string.tab_pwr_bike,
        R.string.tab_hrv_dfa_a1
    )
    val zoneTypes = listOf(ZoneType.HR_RUN, ZoneType.HR_BIKE, ZoneType.PWR_BIKE)

    val allProfilesData by viewModel.allProfiles.collectAsState()

    // Update the Pager State to start at initialTab
    val pagerState = rememberPagerState(
        initialPage = initialTab, // Initialize with the passed value
        pageCount = { profileNameResIds.size }
    )

    val currentType = zoneTypes.getOrElse(pagerState.currentPage) { ZoneType.HR_RUN }

    var showCalculatorDialog by remember { mutableStateOf(false) }
    var calculatorPendingThresholds by remember {
        mutableStateOf<Map<com.atrainingtracker.trainingtracker.database.AthleteMetricType, Double>>(emptyMap())
    }
    var showHistoryDialog by rememberSaveable { mutableStateOf(false) }
    val zoneHistoryMap by viewModel.zoneHistoryFlow.collectAsState()
    var bannerVisible by rememberSaveable { mutableStateOf(promptReason != null) }
    var isSliderGestureActive by remember { mutableStateOf(false) }

    val openCalculatorWithFlush = {
        calculatorPendingThresholds = viewModel.flushPendingThresholdSaves()
        showCalculatorDialog = true
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.flushPendingThresholdSaves()
        if (pagerState.currentPage != 3 && isSliderGestureActive) {
            isSliderGestureActive = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column {

            // 1. THE HEADER (Matches Segments Style)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onOpenDrawer != null) {
                                IconButton(onClick = onOpenDrawer) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = stringResource(R.string.navigation_drawer_open),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            } else {
                                IconButton(onClick = onFinish) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.title_edit_zones),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (pagerState.currentPage < 3) {
                                IconButton(
                                    onClick = openCalculatorWithFlush
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Calculate,
                                        contentDescription = stringResource(R.string.zone_calc_button),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                IconButton(
                                    onClick = { showHistoryDialog = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = stringResource(R.string.zone_history_action_history),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        divider = {}
                    ) {
                        profileNameResIds.forEachIndexed { index, titleResId ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(stringResource(titleResId)) }                    )
                        }
                    }
                }
            }

            // 2. THE CONTENT (Pager)
            if (bannerVisible && promptReason != null) {
                ZoneOnboardingBanner(
                    promptReason = promptReason,
                    onDismiss = {
                        bannerVisible = false
                        scope.launch(Dispatchers.IO) {
                            if (promptReason == DeviceListViewModel.PROMPT_REASON_HR_PAIRED) {
                                dataStore.setHrZonesConfigured(true)
                            } else if (promptReason == DeviceListViewModel.PROMPT_REASON_PWR_PAIRED) {
                                dataStore.setPwrZonesConfigured(true)
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (allProfilesData.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !isSliderGestureActive,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("ZoneSettingsHorizontalPager"),
                    beyondViewportPageCount = 1
                ) { page ->
                    if (page == 3) {
                        HrvDfaA1SettingsContent(
                            onOpenHrvTest = onNavigateToHrvTest,
                            onSliderGestureActiveChange = { active -> isSliderGestureActive = active }
                        )
                    } else {
                        val pageZoneType = zoneTypes.getOrNull(page) ?: return@HorizontalPager
                        ZoneProfileTabContent(
                            zoneType = pageZoneType,
                            showCalculatorDialog = showCalculatorDialog && pagerState.currentPage == page,
                            onDismissCalculatorDialog = {
                                showCalculatorDialog = false
                                calculatorPendingThresholds = emptyMap()
                            },
                            onOpenCalculatorDialog = openCalculatorWithFlush,
                            calculatorThresholdOverrides = calculatorPendingThresholds,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        if (showHistoryDialog) {
            val historyForCurrentType = zoneHistoryMap[currentType] ?: emptyList()
            ZoneHistoryDialog(
                zoneType = currentType,
                history = historyForCurrentType,
                onDismiss = { showHistoryDialog = false },
                onSaveSnapshot = { timestampMs, boundaries ->
                    viewModel.saveBackdatedSnapshot(currentType, boundaries, timestampMs)
                },
                onUpdateSnapshot = { id, timestampMs, boundaries ->
                    viewModel.updateHistoricalSnapshot(id, currentType, boundaries, timestampMs)
                },
                onDeleteSnapshot = { id ->
                    viewModel.deleteHistoricalSnapshot(id, currentType)
                }
            )
        }
    }
}

fun isProfileMonotonic(p: ZoneProfileState): Boolean = p.isMonotonic()

// ... SettingsScreenContent, ZoneRow, and IntegerInputField remain unchanged ...
// (Include them below if copying the whole file, they are identical to previous version)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    z1Max: Int,
    z2Max: Int,
    z3Max: Int,
    z4Max: Int,
    z5Max: Int = 0,
    z6Max: Int = 0,
    displayOptions: ZoneDisplayOptions = ZoneDisplayOptions(),
    zoneType: ZoneType = ZoneType.HR_RUN,
    onUpdateZone1Max: (Int) -> Unit,
    onUpdateZone2Max: (Int) -> Unit,
    onUpdateZone3Max: (Int) -> Unit,
    onUpdateZone4Max: (Int) -> Unit,
    onUpdateZone5Max: (Int) -> Unit = {},
    onUpdateZone6Max: (Int) -> Unit = {},
    onUpdateDisplayOptions: (ZoneDisplayOptions) -> Unit = {},
    thresholdsSection: (@Composable () -> Unit)? = null
) {
    // Load Colors from resources
    val zone1Color = colorResource(id = R.color.zone_1)
    val zone2Color = colorResource(id = R.color.zone_2)
    val zone3Color = colorResource(id = R.color.zone_3)
    val zone4Color = colorResource(id = R.color.zone_4)
    val zone5Color = colorResource(id = R.color.zone_5)
    val zone6Color = colorResource(id = R.color.zone_6)
    val zone7Color = colorResource(id = R.color.zone_7)

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (thresholdsSection != null) {
            thresholdsSection()
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Zone 1
        ZoneRow(
            zoneLabel = stringResource(R.string.zone_1_label),
            minValue = if (zoneType == ZoneType.PWR_BIKE) 0 else 60,
            maxValue = z1Max,
            onMinChange = { },
            onMaxChange = { onUpdateZone1Max(it) },
            minEnabled = false,
            minAllowed = if (zoneType == ZoneType.PWR_BIKE) 1 else 30,
            maxAllowed = z2Max - 1,
            containerColor = zone1Color
        )

        // Zone 2
        ZoneRow(
            zoneLabel = stringResource(R.string.zone_2_label),
            minValue = z1Max + 1,
            maxValue = z2Max,
            onMinChange = { onUpdateZone1Max(it - 1) },
            onMaxChange = { onUpdateZone2Max(it) },
            minAllowed = z1Max + 1,
            maxAllowed = z3Max - 1,
            containerColor = zone2Color
        )

        // Zone 3
        ZoneRow(
            zoneLabel = stringResource(R.string.zone_3_label),
            minValue = z2Max + 1,
            maxValue = z3Max,
            onMinChange = { onUpdateZone2Max(it - 1) },
            onMaxChange = { onUpdateZone3Max(it) },
            minAllowed = z2Max + 1,
            maxAllowed = z4Max - 1,
            containerColor = zone3Color
        )

        // Zone 4
        ZoneRow(
            zoneLabel = stringResource(R.string.zone_4_label),
            minValue = z3Max + 1,
            maxValue = z4Max,
            onMinChange = { onUpdateZone3Max(it - 1) },
            onMaxChange = { onUpdateZone4Max(it) },
            minAllowed = z3Max + 1,
            maxAllowed = if (zoneType == ZoneType.PWR_BIKE) z5Max - 1 else 240,
            containerColor = zone4Color
        )

        if (zoneType == ZoneType.PWR_BIKE) {
            // Zone 5 (VO2 Max)
            ZoneRow(
                zoneLabel = stringResource(R.string.zone_5_label),
                minValue = z4Max + 1,
                maxValue = z5Max,
                onMinChange = { onUpdateZone4Max(it - 1) },
                onMaxChange = { onUpdateZone5Max(it) },
                minAllowed = z4Max + 1,
                maxAllowed = z6Max - 1,
                containerColor = zone5Color
            )

            // Zone 6 (Anaerobic Capacity)
            ZoneRow(
                zoneLabel = stringResource(R.string.zone_6_label),
                minValue = z5Max + 1,
                maxValue = z6Max,
                onMinChange = { onUpdateZone5Max(it - 1) },
                onMaxChange = { onUpdateZone6Max(it) },
                minAllowed = z5Max + 1,
                maxAllowed = 2499,
                containerColor = zone6Color
            )

            // Zone 7 (Neuromuscular Power, open-ended)
            ZoneRow(
                zoneLabel = stringResource(R.string.zone_7_label),
                minValue = z6Max + 1,
                maxValue = z6Max + 1,
                onMinChange = { onUpdateZone6Max(it - 1) },
                onMaxChange = { },
                maxEnabled = false,
                containerColor = zone7Color
            )
        } else {
            // Zone 5 for Heart Rate (open-ended)
            ZoneRow(
                zoneLabel = stringResource(R.string.zone_5_label),
                minValue = z4Max + 1,
                maxValue = 210,
                onMinChange = { onUpdateZone4Max(it - 1) },
                onMaxChange = { },
                maxEnabled = false,
                containerColor = zone5Color
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Zonendarstellung / Zone Visualization
        Text(
            text = stringResource(R.string.zone_display_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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
                    onUpdateDisplayOptions = onUpdateDisplayOptions,
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

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Live Theme-Aware Preview
        Text(
            text = stringResource(R.string.zone_display_preview_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )

        // Interactive Zone selector chips (Z1..Z5 for HR, Z1..Z7 for Power)
        val zoneCount = if (zoneType == ZoneType.PWR_BIKE) 7 else 5
        var previewZone by remember(zoneType) { mutableStateOf(2) } // Default to Zone 2
        val zoneLabels = (1..zoneCount).map { "Z$it" }
        val zoneColorsList = if (zoneType == ZoneType.PWR_BIKE) {
            listOf(zone1Color, zone2Color, zone3Color, zone4Color, zone5Color, zone6Color, zone7Color)
        } else {
            listOf(zone1Color, zone2Color, zone3Color, zone4Color, zone5Color)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..zoneCount).forEach { zIndex ->
                ZonePreviewPill(
                    label = zoneLabels[zIndex - 1],
                    zoneColor = zoneColorsList[zIndex - 1],
                    selected = (previewZone == zIndex),
                    onClick = { previewZone = zIndex },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Preview Tile: uses value within the athlete's configured interval
        val safePreviewZone = previewZone.coerceIn(1, zoneCount)
        val previewZoneColor = zoneColorsList[safePreviewZone - 1]
        val previewValue = when (safePreviewZone) {
            1 -> z1Max.toString()
            2 -> (z1Max + 1).toString()
            3 -> (z2Max + 1).toString()
            4 -> (z3Max + 1).toString()
            5 -> if (zoneType == ZoneType.PWR_BIKE) (z4Max + 1).toString() else (z4Max + 1).toString()
            6 -> (z5Max + 1).toString()
            else -> (z6Max + 1).toString()
        }
        val previewLabel = if (zoneType == ZoneType.PWR_BIKE) "Power" else "Heart Rate"
        val previewUnits = if (zoneType == ZoneType.PWR_BIKE) "W" else "bpm"

        val previewCutoffs = if (zoneType == ZoneType.PWR_BIKE) {
            listOf(z1Max, z2Max, z3Max, z4Max, z5Max, z6Max)
        } else {
            listOf(z1Max, z2Max, z3Max, z4Max)
        }
        val previewGaugeState = ZoneGaugeMath.calculateZoneGaugeState(
            value = previewValue.toDoubleOrNull(),
            zoneType = zoneType,
            cutoffs = previewCutoffs
        )

        val previewFieldState = SensorFieldState(
            configHash = 0,
            sensorFieldId = 0,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = previewLabel,
            filterDescription = "",
            value = previewValue,
            units = previewUnits,
            zoneColor = previewZoneColor,
            zoneDisplayOptions = displayOptions,
            zoneGaugeState = previewGaugeState,
            showValue = true,
            showBar = true
        )

        ZoneLivePreviewTileCard(fieldState = previewFieldState)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreenContent(
            z1Max = 130,
            z2Max = 150,
            z3Max = 170,
            z4Max = 180,
            onUpdateZone1Max = {_ ->},
            onUpdateZone2Max = {_ ->},
            onUpdateZone3Max = {_ ->},
            onUpdateZone4Max = {_ ->}
        )
    }
}

/**
 * Reusable single-zone profile tab content composable (`REQ-UI-397.2`, `ATT-3851`).
 *
 * Encapsulates DataStore state loading, monotonicity-guarded zone persistence, [SettingsScreenContent],
 * and [ZoneCalculatorDialog] for a given [zoneType] so it can be embedded directly inside the unified
 * **Profile & Zones** tabbed screen (`AthleteProfileScreen`) without duplicating zone editing logic.
 */
@Composable
fun ZoneProfileTabContent(
    zoneType: ZoneType,
    showCalculatorDialog: Boolean,
    onDismissCalculatorDialog: () -> Unit,
    onOpenCalculatorDialog: () -> Unit = {},
    calculatorThresholdOverrides: Map<com.atrainingtracker.trainingtracker.database.AthleteMetricType, Double> = emptyMap(),
    viewModel: ZonesSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val dataStore = remember { SettingsDataStore.getInstance(context) }
    val scope = rememberCoroutineScope()

    val allProfiles by viewModel.allProfiles.collectAsState()
    val tuningConfig by viewModel.tuningConfigFlow.collectAsState()
    val currentProfile = allProfiles.find { it.type == zoneType }

    val isCustomized by dataStore.isZoneCustomizedFlow(zoneType).collectAsState(initial = false)
    val storedFtp by dataStore.ftpFlow.collectAsState(initial = null)
    val storedCp by dataStore.cpWattsFlow.collectAsState(initial = null)
    val storedWPrime by dataStore.wPrimeKjFlow.collectAsState(initial = null)
    val storedLt1PwrBike by dataStore.lt1PwrBikeFlow.collectAsState(initial = null)
    val storedHrMaxRun by dataStore.hrMaxRunFlow.collectAsState(initial = null)
    val storedHrMaxBike by dataStore.hrMaxBikeFlow.collectAsState(initial = null)
    val storedHrMaxLegacy by dataStore.hrMaxFlow.collectAsState(initial = null)
    val storedHrMax = when (zoneType) {
        ZoneType.HR_RUN -> storedHrMaxRun ?: storedHrMaxLegacy
        ZoneType.HR_BIKE -> storedHrMaxBike ?: storedHrMaxLegacy
        ZoneType.PWR_BIKE -> storedHrMaxLegacy
    }
    val storedHrRest by dataStore.hrRestFlow.collectAsState(initial = null)
    val storedLthrRun by dataStore.lthrRunFlow.collectAsState(initial = null)
    val storedLthrBike by dataStore.lthrBikeFlow.collectAsState(initial = null)
    val storedLthr = when (zoneType) {
        ZoneType.HR_RUN -> storedLthrRun
        ZoneType.HR_BIKE -> storedLthrBike
        ZoneType.PWR_BIKE -> null
    }
    val storedLt1HrRun by dataStore.lt1HrRunFlow.collectAsState(initial = null)
    val storedLt1HrBike by dataStore.lt1HrBikeFlow.collectAsState(initial = null)
    val storedLt1Hr = when (zoneType) {
        ZoneType.HR_RUN -> storedLt1HrRun
        ZoneType.HR_BIKE -> storedLt1HrBike
        ZoneType.PWR_BIKE -> null
    }
    val storedHrMethod by dataStore.hrCalcMethodFlow.collectAsState(initial = null)

    val thresholdValues = remember(
        storedFtp, storedCp, storedWPrime, storedLt1PwrBike,
        storedHrMax, storedHrRest, storedLthr, storedLt1Hr
    ) {
        ZoneThresholdsValues(
            ftp = storedFtp,
            cp = storedCp,
            wPrimeKj = storedWPrime,
            lt1PwrBike = storedLt1PwrBike,
            hrMax = storedHrMax,
            hrRest = storedHrRest,
            lthr = storedLthr,
            lt1Hr = storedLt1Hr
        )
    }

    if (currentProfile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        SettingsScreenContent(
            z1Max = currentProfile.z1,
            z2Max = currentProfile.z2,
            z3Max = currentProfile.z3,
            z4Max = currentProfile.z4,
            z5Max = currentProfile.z5,
            z6Max = currentProfile.z6,
            displayOptions = currentProfile.displayOptions,
            zoneType = currentProfile.type,
            onUpdateZone1Max = { new -> viewModel.updateThreshold(zoneType, 1, new) },
            onUpdateZone2Max = { new -> viewModel.updateThreshold(zoneType, 2, new) },
            onUpdateZone3Max = { new -> viewModel.updateThreshold(zoneType, 3, new) },
            onUpdateZone4Max = { new -> viewModel.updateThreshold(zoneType, 4, new) },
            onUpdateZone5Max = { new -> viewModel.updateThreshold(zoneType, 5, new) },
            onUpdateZone6Max = { new -> viewModel.updateThreshold(zoneType, 6, new) },
            onUpdateDisplayOptions = { newOptions -> viewModel.updateDisplayOptions(zoneType, newOptions) },
            thresholdsSection = {
                PhysiologicalThresholdsCard(
                    zoneType = zoneType,
                    tuningConfig = tuningConfig,
                    values = thresholdValues,
                    onSaveThreshold = { metricType, value ->
                        viewModel.savePhysiologicalThreshold(metricType, value)
                    },
                    onSaveCpAndWPrime = { cp, wPrime ->
                        viewModel.saveCpAndWPrimeEstimate(cp, wPrime)
                    },
                    onOpenRecalculateDialog = onOpenCalculatorDialog
                )
            }
        )
    }

    if (showCalculatorDialog) {
        // Key remember strictly on zoneType (NOT live DataStore flows) so opening the dialog
        // snapshots latest + freshly flushed thresholds once and never resets mid-edit or mid-save.
        val holder = remember(zoneType) {
            val hrMaxKey = if (zoneType == ZoneType.HR_RUN) {
                com.atrainingtracker.trainingtracker.database.AthleteMetricType.HR_MAX_RUN_BPM
            } else {
                com.atrainingtracker.trainingtracker.database.AthleteMetricType.HR_MAX_BIKE_BPM
            }
            val lthrKey = if (zoneType == ZoneType.HR_RUN) {
                com.atrainingtracker.trainingtracker.database.AthleteMetricType.LTHR_RUN_BPM
            } else {
                com.atrainingtracker.trainingtracker.database.AthleteMetricType.LTHR_BIKE_BPM
            }
            val lt1Key = if (zoneType == ZoneType.HR_RUN) {
                com.atrainingtracker.trainingtracker.database.AthleteMetricType.LT1_HR_RUN_BPM
            } else {
                com.atrainingtracker.trainingtracker.database.AthleteMetricType.LT1_HR_BIKE_BPM
            }
            ZoneCalculatorStateHolder(
                zoneType = zoneType,
                initialCustomized = isCustomized,
                initialFtp = calculatorThresholdOverrides[com.atrainingtracker.trainingtracker.database.AthleteMetricType.FTP_WATTS]?.let { Math.round(it).toInt() } ?: storedFtp,
                initialHrMax = calculatorThresholdOverrides[hrMaxKey]?.let { Math.round(it).toInt() } ?: storedHrMax,
                initialHrRest = calculatorThresholdOverrides[com.atrainingtracker.trainingtracker.database.AthleteMetricType.HR_REST_BPM]?.let { Math.round(it).toInt() } ?: storedHrRest,
                initialLthr = calculatorThresholdOverrides[lthrKey]?.let { Math.round(it).toInt() } ?: storedLthr,
                initialLt1Hr = calculatorThresholdOverrides[lt1Key]?.let { Math.round(it).toInt() } ?: storedLt1Hr,
                initialHrMethod = when (storedHrMethod) {
                    "MAX_HR" -> HrMethod.MAX_HR
                    "LTHR" -> HrMethod.LTHR
                    "HRVT_ANCHORS" -> HrMethod.THRESHOLD_ANCHORED
                    else -> HrMethod.KARVONEN
                }
            )
        }
        ZoneCalculatorDialog(
            holder = holder,
            onDismiss = onDismissCalculatorDialog,
            onSavePower = { ftp, cutoffs ->
                scope.launch(Dispatchers.IO) {
                    try {
                        com.atrainingtracker.trainingtracker.zones.ZoneValidator.validatePowerZoneStack(cutoffs)
                        com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepository
                            .getInstance(context)
                            .addEntry(
                                metricType = com.atrainingtracker.trainingtracker.database.AthleteMetricType.FTP_WATTS,
                                value = ftp.toDouble()
                            )
                        dataStore.saveCalculatedPowerZones(ftp, cutoffs)
                        viewModel.saveRecalculatedZonesSnapshot(ZoneType.PWR_BIKE, cutoffs)
                        withContext(Dispatchers.Main) {
                            holder.onSaveCompleted()
                            onDismissCalculatorDialog()
                        }
                        viewModel.reloadProfileFromDataStore(ZoneType.PWR_BIKE)
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            holder.onSaveFailed()
                        }
                    }
                }
            },
            onSaveHeartRateThresholds = { lthr, lt1Hr ->
                val lthrMetric = if (zoneType == ZoneType.HR_RUN) {
                    com.atrainingtracker.trainingtracker.database.AthleteMetricType.LTHR_RUN_BPM
                } else {
                    com.atrainingtracker.trainingtracker.database.AthleteMetricType.LTHR_BIKE_BPM
                }
                val lt1Metric = if (zoneType == ZoneType.HR_RUN) {
                    com.atrainingtracker.trainingtracker.database.AthleteMetricType.LT1_HR_RUN_BPM
                } else {
                    com.atrainingtracker.trainingtracker.database.AthleteMetricType.LT1_HR_BIKE_BPM
                }
                if (lthr != null && lthrMetric.isValidMetricValue(lthr.toDouble())) {
                    viewModel.savePhysiologicalThreshold(lthrMetric, lthr.toDouble(), debounceMs = 0L)
                }
                if (lt1Hr != null && lt1Metric.isValidMetricValue(lt1Hr.toDouble())) {
                    viewModel.savePhysiologicalThreshold(lt1Metric, lt1Hr.toDouble(), debounceMs = 0L)
                }
            },
            onSaveHeartRate = { zType, hrMax, hrRest, method, cutoffs ->
                scope.launch(Dispatchers.IO) {
                    try {
                        require(
                            com.atrainingtracker.trainingtracker.zones.ZoneValidator.validateHrBaselines(hrMax, hrRest)
                                is com.atrainingtracker.trainingtracker.zones.ValidationResult.Valid
                        ) { "Invalid HR baselines: hrMax=$hrMax, hrRest=$hrRest" }
                        com.atrainingtracker.trainingtracker.zones.ZoneValidator.validateHeartRateZoneStack(cutoffs)
                        val metricsRepo = com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepository
                            .getInstance(context)
                        val hrMaxMetric = if (zType == ZoneType.HR_RUN) {
                            com.atrainingtracker.trainingtracker.database.AthleteMetricType.HR_MAX_RUN_BPM
                        } else {
                            com.atrainingtracker.trainingtracker.database.AthleteMetricType.HR_MAX_BIKE_BPM
                        }
                        // Persist through AthleteMetricsRepository BEFORE overwriting legacy KEY_HR_MAX
                        // so upgraded users preserve the other sport's legacy HRmax without duplicate rows.
                        if (hrMaxMetric.isValidMetricValue(hrMax.toDouble())) {
                            metricsRepo.addEntry(metricType = hrMaxMetric, value = hrMax.toDouble())
                        }
                        if (hrRest != null && hrRest > 0) {
                            metricsRepo.addEntry(
                                metricType = com.atrainingtracker.trainingtracker.database.AthleteMetricType.HR_REST_BPM,
                                value = hrRest.toDouble()
                            )
                        }
                        dataStore.saveCalculatedHeartRateZones(zType, hrMax, hrRest, method, cutoffs)
                        viewModel.saveRecalculatedZonesSnapshot(zType, cutoffs)
                        withContext(Dispatchers.Main) {
                            holder.onSaveCompleted()
                            onDismissCalculatorDialog()
                        }
                        viewModel.reloadProfileFromDataStore(zType)
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            holder.onSaveFailed()
                        }
                    }
                }
            }
        )
    }
}
