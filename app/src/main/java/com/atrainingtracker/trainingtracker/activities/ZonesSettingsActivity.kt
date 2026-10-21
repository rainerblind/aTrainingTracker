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

package com.atrainingtracker.trainingtracker.activities

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Zone
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.settings.ZoneProfileState
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.CockpitThemeMode
import com.atrainingtracker.trainingtracker.ui.theme.resolveEffectiveCockpitDarkTheme
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldView
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.atrainingtracker.trainingtracker.ui.tracking.ZoneGaugeMath
import com.atrainingtracker.trainingtracker.ui.settings.HrMethod
import com.atrainingtracker.trainingtracker.ui.settings.ZoneCalculatorDialog
import com.atrainingtracker.trainingtracker.ui.settings.ZoneCalculatorStateHolder
import com.atrainingtracker.trainingtracker.ui.settings.ZoneOnboardingBanner
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceListViewModel
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

class ZonesSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve the target tab index from the intent, default to 0
        val targetTab = intent.getIntExtra("TARGET_ZONE_TAB", 0)
        val promptReason = intent.getStringExtra("PROMPT_REASON")

        setContent {
            ATrainingTrackerTheme {
                // Pass the initial tab to the route
                ZoneSettingsScreen(
                    initialTab = targetTab,
                    promptReason = promptReason
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ZoneSettingsScreen(
    initialTab: Int = 0,
    promptReason: String? = null,
    onFinish: () -> Unit = {},
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

    val dataStore = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()

    val profileNameResIds = listOf(
        R.string.tab_hr_run,
        R.string.tab_hr_bike,
        R.string.tab_pwr_bike
    )
    val zoneTypes = listOf(ZoneType.HR_RUN, ZoneType.HR_BIKE, ZoneType.PWR_BIKE)

    val allProfilesData by viewModel.allProfiles.collectAsState()

    // Update the Pager State to start at initialTab
    val pagerState = rememberPagerState(
        initialPage = initialTab, // Initialize with the passed value
        pageCount = { profileNameResIds.size }
    )

    val currentType = zoneTypes[pagerState.currentPage]
    val isCustomized by dataStore.isZoneCustomizedFlow(currentType).collectAsState(initial = false)
    val storedFtp by dataStore.ftpFlow.collectAsState(initial = null)
    val storedHrMax by dataStore.hrMaxFlow.collectAsState(initial = null)
    val storedHrRest by dataStore.hrRestFlow.collectAsState(initial = null)
    val storedHrMethod by dataStore.hrCalcMethodFlow.collectAsState(initial = null)

    var showCalculatorDialog by remember { mutableStateOf(false) }
    var bannerVisible by rememberSaveable { mutableStateOf(promptReason != null) }

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
                            IconButton(
                                onClick = { showCalculatorDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = stringResource(R.string.zone_calc_button),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
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
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { page ->
                    // Get the specific state for this page
                    val profileState = allProfilesData.getOrNull(page) ?: return@HorizontalPager

                    SettingsScreenContent(
                        z1Max = profileState.z1,
                        z2Max = profileState.z2,
                        z3Max = profileState.z3,
                        z4Max = profileState.z4,
                        z5Max = profileState.z5,
                        z6Max = profileState.z6,
                        displayOptions = profileState.displayOptions,
                        zoneType = profileState.type,

                        onUpdateZone1Max = { new -> viewModel.updateThreshold(profileState.type, 1, new) },
                        onUpdateZone2Max = { new -> viewModel.updateThreshold(profileState.type, 2, new) },
                        onUpdateZone3Max = { new -> viewModel.updateThreshold(profileState.type, 3, new) },
                        onUpdateZone4Max = { new -> viewModel.updateThreshold(profileState.type, 4, new) },
                        onUpdateZone5Max = { new -> viewModel.updateThreshold(profileState.type, 5, new) },
                        onUpdateZone6Max = { new -> viewModel.updateThreshold(profileState.type, 6, new) },
                        onUpdateDisplayOptions = { newOptions -> viewModel.updateDisplayOptions(profileState.type, newOptions) }
                    )
                }
            }
        }

        if (showCalculatorDialog) {
            val holder = remember(currentType, isCustomized, storedFtp, storedHrMax, storedHrRest, storedHrMethod) {
                ZoneCalculatorStateHolder(
                    zoneType = currentType,
                    initialCustomized = isCustomized,
                    initialFtp = storedFtp,
                    initialHrMax = storedHrMax,
                    initialHrRest = storedHrRest,
                    initialHrMethod = if (storedHrMethod == "MAX_HR") HrMethod.MAX_HR else HrMethod.KARVONEN
                )
            }
            ZoneCalculatorDialog(
                holder = holder,
                onDismiss = { showCalculatorDialog = false },
                onSavePower = { ftp, cutoffs ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            dataStore.saveCalculatedPowerZones(ftp, cutoffs)
                            com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepository
                                .getInstance(context)
                                .addEntry(
                                    metricType = com.atrainingtracker.trainingtracker.database.AthleteMetricType.FTP_WATTS,
                                    value = ftp.toDouble()
                                )
                            withContext(Dispatchers.Main) {
                                holder.onSaveCompleted()
                                showCalculatorDialog = false
                            }
                            viewModel.reloadProfileFromDataStore(ZoneType.PWR_BIKE)
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                holder.onSaveFailed()
                            }
                        }
                    }
                },
                onSaveHeartRate = { zType, hrMax, hrRest, method, cutoffs ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            dataStore.saveCalculatedHeartRateZones(zType, hrMax, hrRest, method, cutoffs)
                            if (hrRest != null && hrRest > 0) {
                                com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepository
                                    .getInstance(context)
                                    .addEntry(
                                        metricType = com.atrainingtracker.trainingtracker.database.AthleteMetricType.HR_REST_BPM,
                                        value = hrRest.toDouble()
                                    )
                            }
                            withContext(Dispatchers.Main) {
                                holder.onSaveCompleted()
                                showCalculatorDialog = false
                            }
                            viewModel.reloadProfileFromDataStore(zType)
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                holder.onSaveFailed()
                            }
                        }
                    }
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
    onUpdateDisplayOptions: (ZoneDisplayOptions) -> Unit = {}
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
                // Toggle: Light background
                ZoneDisplayToggleRow(
                    label = stringResource(R.string.zone_display_background),
                    checked = displayOptions.showBackground,
                    onCheckedChange = { onUpdateDisplayOptions(displayOptions.copy(showBackground = it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Toggle: Left bar
                ZoneDisplayToggleRow(
                    label = stringResource(R.string.zone_display_left_bar),
                    checked = displayOptions.showLeftBar,
                    onCheckedChange = { onUpdateDisplayOptions(displayOptions.copy(showLeftBar = it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Toggle: Right bar
                ZoneDisplayToggleRow(
                    label = stringResource(R.string.zone_display_right_bar),
                    checked = displayOptions.showRightBar,
                    onCheckedChange = { onUpdateDisplayOptions(displayOptions.copy(showRightBar = it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Toggle: Text color
                ZoneDisplayToggleRow(
                    label = stringResource(R.string.zone_display_text_color),
                    checked = displayOptions.showTextColor,
                    onCheckedChange = { onUpdateDisplayOptions(displayOptions.copy(showTextColor = it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Toggle: Zone gauge bar (REQ-UI-376)
                ZoneDisplayToggleRow(
                    label = stringResource(R.string.zone_display_gauge),
                    checked = displayOptions.showGauge,
                    onCheckedChange = { onUpdateDisplayOptions(displayOptions.copy(showGauge = it)) }
                )
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
        val previewGaugeState = if (displayOptions.showGauge) {
            ZoneGaugeMath.calculateZoneGaugeState(
                value = previewValue.toDoubleOrNull(),
                zoneType = zoneType,
                cutoffs = previewCutoffs
            )
        } else null

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
            zoneGaugeState = previewGaugeState
        )

        val isSystemDark = isSystemInDarkTheme()
        val cockpitThemeMode = remember {
            try {
                TrainingApplication.getCockpitThemeMode()
            } catch (e: Exception) {
                CockpitThemeMode.SYSTEM
            }
        }
        val isCockpitDark = resolveEffectiveCockpitDarkTheme(cockpitThemeMode, isSystemDark)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            ATrainingTrackerTheme(
                darkTheme = isCockpitDark,
                amoled = isCockpitDark,
                setWindowColors = false
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium
                ) {
                    SensorFieldView(
                        fieldState = previewFieldState,
                        screenMode = ScreenMode.TRACKING
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Compact zone-tinted bordered selector pill for the live zone preview row (`REQ-UI-384` / Rule 38).
 * Replaces Material 3 `FilterChip` + `leadingIcon` so that all 5 (HR) or 7 (Cycling Power) zones
 * render on a single line without vertical character wrapping (`"Z\n1"`..`"Z\n7"`).
 */
@Composable
fun ZonePreviewPill(
    label: String,
    zoneColor: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = RoundedCornerShape(8.dp),
        color = zoneColor.copy(alpha = if (selected) 0.28f else 0.12f),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) zoneColor else zoneColor.copy(alpha = 0.6f)
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun ZoneDisplayToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}


@Composable
fun ZoneRow(
    zoneLabel: String,
    minValue: Int,
    maxValue: Int,
    onMinChange: (Int) -> Unit,
    onMaxChange: (Int) -> Unit,
    minEnabled: Boolean = true,
    maxEnabled: Boolean = true,
    minAllowed: Int? = null,
    maxAllowed: Int? = null,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface
) {
    Card(
        // We use the containerColor with low alpha for the card body
        colors = CardDefaults.cardColors(
            containerColor = containerColor.copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        // Row with IntrinsicSize.Min allows the Spacer to match the Column height
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {

            // 1. The Indicator Strip (Solid Color)
            Spacer(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(containerColor)
            )

            // 2. The Content
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = zoneLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Min Field
                    Box(modifier = Modifier.weight(1f)) {
                        IntegerInputField(
                            label = stringResource(R.string.label_min),
                            currentValue = minValue,
                            onValueChange = onMinChange,
                            enabled = minEnabled
                        )
                    }
                    // Max Field
                    Box(modifier = Modifier.weight(1f)) {
                        IntegerInputField(
                            label = stringResource(R.string.label_max),
                            currentValue = maxValue,
                            onValueChange = onMaxChange,
                            enabled = maxEnabled,
                            minAllowed = minAllowed,
                            maxAllowed = maxAllowed
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IntegerInputField(
    label: String,
    currentValue: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
    minAllowed: Int? = null,
    maxAllowed: Int? = null
) {
    var textState by remember(currentValue) { mutableStateOf(currentValue.toString()) }
    val isOutOfRange = (minAllowed != null && currentValue < minAllowed) || (maxAllowed != null && currentValue > maxAllowed)

    OutlinedTextField(
        value = if (enabled) textState else "--",
        onValueChange = { input ->
            if (input.all { it.isDigit() }) {
                textState = input
                if (input.isNotEmpty()) {
                    try {
                        onValueChange(input.toInt())
                    } catch (e: NumberFormatException) { }
                } else {
                    onValueChange(0)
                }
            }
        },
        label = { Text(label) },
        enabled = enabled,
        readOnly = !enabled,
        isError = enabled && isOutOfRange,
        supportingText = if (enabled && isOutOfRange && minAllowed != null && maxAllowed != null) {
            {
                Text(
                    text = stringResource(R.string.zone_calc_error_monotonicity, minAllowed, maxAllowed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
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
    viewModel: ZonesSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val dataStore = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()

    val allProfiles by viewModel.allProfiles.collectAsState()
    val currentProfile = allProfiles.find { it.type == zoneType }

    val isCustomized by dataStore.isZoneCustomizedFlow(zoneType).collectAsState(initial = false)
    val storedFtp by dataStore.ftpFlow.collectAsState(initial = null)
    val storedHrMax by dataStore.hrMaxFlow.collectAsState(initial = null)
    val storedHrRest by dataStore.hrRestFlow.collectAsState(initial = null)
    val storedHrMethod by dataStore.hrCalcMethodFlow.collectAsState(initial = null)

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
            onUpdateDisplayOptions = { newOptions -> viewModel.updateDisplayOptions(zoneType, newOptions) }
        )
    }

    if (showCalculatorDialog) {
        val holder = remember(zoneType, isCustomized, storedFtp, storedHrMax, storedHrRest, storedHrMethod) {
            ZoneCalculatorStateHolder(
                zoneType = zoneType,
                initialCustomized = isCustomized,
                initialFtp = storedFtp,
                initialHrMax = storedHrMax,
                initialHrRest = storedHrRest,
                initialHrMethod = if (storedHrMethod == "MAX_HR") HrMethod.MAX_HR else HrMethod.KARVONEN
            )
        }
        ZoneCalculatorDialog(
            holder = holder,
            onDismiss = onDismissCalculatorDialog,
            onSavePower = { ftp, cutoffs ->
                scope.launch(Dispatchers.IO) {
                    try {
                        dataStore.saveCalculatedPowerZones(ftp, cutoffs)
                        com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepository
                            .getInstance(context)
                            .addEntry(
                                metricType = com.atrainingtracker.trainingtracker.database.AthleteMetricType.FTP_WATTS,
                                value = ftp.toDouble()
                            )
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
            onSaveHeartRate = { zType, hrMax, hrRest, method, cutoffs ->
                scope.launch(Dispatchers.IO) {
                    try {
                        dataStore.saveCalculatedHeartRateZones(zType, hrMax, hrRest, method, cutoffs)
                        if (hrRest != null && hrRest > 0) {
                            com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepository
                                .getInstance(context)
                                .addEntry(
                                    metricType = com.atrainingtracker.trainingtracker.database.AthleteMetricType.HR_REST_BPM,
                                    value = hrRest.toDouble()
                                )
                        }
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