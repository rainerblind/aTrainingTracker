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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Zone
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.CockpitThemeMode
import com.atrainingtracker.trainingtracker.ui.theme.resolveEffectiveCockpitDarkTheme
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldView
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import android.text.format.DateUtils
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.ui.settings.AthleteWeightDialog
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

// Data class to hold the full state of a profile
data class ZoneProfileState(
    val type: ZoneType,
    val z1: Int,
    val z2: Int,
    val z3: Int,
    val z4: Int,
    val z5: Int = 0,
    val z6: Int = 0,
    val displayOptions: ZoneDisplayOptions = ZoneDisplayOptions()
)


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ZoneSettingsScreen(
    initialTab: Int = 0,
    promptReason: String? = null,
    onFinish: () -> Unit = {}
) {
    val context = LocalContext.current

    val view = androidx.compose.ui.platform.LocalView.current
    val isDarkTheme = isSystemInDarkTheme()

    // Force status bar icons to be dark when in Light Mode
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as android.app.Activity).window
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)

            // appearanceLightStatusBars = true makes icons DARK
            // appearanceLightStatusBars = false makes icons WHITE
            windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
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

    // Local state to hold data for swiping performance
    var allProfilesData by remember { mutableStateOf<List<ZoneProfileState>>(emptyList()) }

    // save helper
    val saveProfile = { profile: ZoneProfileState ->
        scope.launch(Dispatchers.IO) {
            dataStore.saveHrZoneMax(profile.type, Zone.ZONE_1, profile.z1)
            dataStore.saveHrZoneMax(profile.type, Zone.ZONE_2, profile.z2)
            dataStore.saveHrZoneMax(profile.type, Zone.ZONE_3, profile.z3)
            dataStore.saveHrZoneMax(profile.type, Zone.ZONE_4, profile.z4)
            if (profile.type == ZoneType.PWR_BIKE) {
                dataStore.saveHrZoneMax(profile.type, Zone.ZONE_5, profile.z5)
                dataStore.saveHrZoneMax(profile.type, Zone.ZONE_6, profile.z6)
            }
            dataStore.saveZoneDisplayOptions(profile.type, profile.displayOptions)
        }
    }

    val reloadProfiles = {
        scope.launch(Dispatchers.IO) {
            val loadedData = zoneTypes.mapIndexed { _, type ->
                ZoneProfileState(
                    type = type,
                    z1 = dataStore.getZone1MaxFlow(type).first(),
                    z2 = dataStore.getZone2MaxFlow(type).first(),
                    z3 = dataStore.getZone3MaxFlow(type).first(),
                    z4 = dataStore.getZone4MaxFlow(type).first(),
                    z5 = if (type == ZoneType.PWR_BIKE) dataStore.getZone5MaxFlow(type).first() else 0,
                    z6 = if (type == ZoneType.PWR_BIKE) dataStore.getZone6MaxFlow(type).first() else 0,
                    displayOptions = dataStore.getZoneDisplayOptionsFlow(type).first()
                )
            }
            allProfilesData = loadedData
        }
    }

    // Initial Load & Migration
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            dataStore.checkAndMigratePowerZones()
            val loadedData = zoneTypes.mapIndexed { _, type ->
                ZoneProfileState(
                    type = type,
                    z1 = dataStore.getZone1MaxFlow(type).first(),
                    z2 = dataStore.getZone2MaxFlow(type).first(),
                    z3 = dataStore.getZone3MaxFlow(type).first(),
                    z4 = dataStore.getZone4MaxFlow(type).first(),
                    z5 = if (type == ZoneType.PWR_BIKE) dataStore.getZone5MaxFlow(type).first() else 0,
                    z6 = if (type == ZoneType.PWR_BIKE) dataStore.getZone6MaxFlow(type).first() else 0,
                    displayOptions = dataStore.getZoneDisplayOptionsFlow(type).first()
                )
            }
            allProfilesData = loadedData
        }
    }

    // Update the Pager State to start at initialTab
    val pagerState = rememberPagerState(
        initialPage = initialTab, // Initialize with the passed value
        pageCount = { profileNameResIds.size }
    )

    val currentType = zoneTypes[pagerState.currentPage]
    val isCustomized by dataStore.isZoneCustomizedFlow(currentType).collectAsState(initial = false)
    val storedFtp by dataStore.ftpFlow.collectAsState(initial = null)
    val ftpUpdatedAt by dataStore.ftpUpdatedAtFlow.collectAsState(initial = null)
    val storedHrMax by dataStore.hrMaxFlow.collectAsState(initial = null)
    val storedHrRest by dataStore.hrRestFlow.collectAsState(initial = null)
    val storedHrMethod by dataStore.hrCalcMethodFlow.collectAsState(initial = null)
    val storedAthleteWeightKg by dataStore.athleteWeightKgFlow.collectAsState(initial = 0.0f)

    var showCalculatorDialog by remember { mutableStateOf(false) }
    var showAthleteWeightDialog by remember { mutableStateOf(false) }
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.title_edit_zones),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = stringResource(
                                            if (isCustomized) R.string.zone_calc_status_customized
                                            else R.string.zone_calc_status_calculated
                                        ),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                shape = MaterialTheme.shapes.extraSmall
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { showCalculatorDialog = true }
                            ) {
                                Text(
                                    text = stringResource(R.string.zone_calc_button),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            TextButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        dataStore.resetToDefaults(currentType)
                                        reloadProfiles()
                                    }
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.reset_to_defaults),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    if (currentType == ZoneType.PWR_BIKE && ftpUpdatedAt != null) {
                        val dateStr = DateUtils.formatDateTime(
                            context,
                            ftpUpdatedAt!!,
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
                        )
                        val isStale = (System.currentTimeMillis() - ftpUpdatedAt!!) > 180L * 24 * 60 * 60 * 1000
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.zone_calc_last_updated, dateStr),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            if (isStale) {
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            text = stringResource(R.string.zone_calc_calibration_recommended),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                )
                            }
                        }
                    }

                    if (currentType == ZoneType.PWR_BIKE) {
                        val isImperial = TrainingApplication.getUnit() == MyUnits.IMPERIAL
                        val weightText = if (storedAthleteWeightKg > 0f) {
                            val displayWeight = if (isImperial) storedAthleteWeightKg * SettingsDataStore.LBS_PER_KG else storedAthleteWeightKg
                            val unitStr = if (isImperial) "lbs" else "kg"
                            String.format(java.util.Locale.US, "%.1f %s", displayWeight, unitStr)
                        } else {
                            stringResource(R.string.athlete_weight_unconfigured)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${stringResource(R.string.athlete_weight_title)}: $weightText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                            )
                            TextButton(
                                onClick = { showAthleteWeightDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.athlete_weight_edit),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
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
                    val profileState = allProfilesData[page]

                    SettingsScreenContent(
                        z1Max = profileState.z1,
                        z2Max = profileState.z2,
                        z3Max = profileState.z3,
                        z4Max = profileState.z4,
                        z5Max = profileState.z5,
                        z6Max = profileState.z6,
                        displayOptions = profileState.displayOptions,
                        zoneType = profileState.type,

                        onUpdateZone1Max = { new ->
                            val updated = updateProfile(allProfilesData, page) { it.copy(z1 = new) }
                            allProfilesData = updated
                            if (isProfileMonotonic(updated[page])) {
                                saveProfile(updated[page])
                            }
                        },
                        onUpdateZone2Max = { new ->
                            val updated = updateProfile(allProfilesData, page) { it.copy(z2 = new) }
                            allProfilesData = updated
                            if (isProfileMonotonic(updated[page])) {
                                saveProfile(updated[page])
                            }
                        },
                        onUpdateZone3Max = { new ->
                            val updated = updateProfile(allProfilesData, page) { it.copy(z3 = new) }
                            allProfilesData = updated
                            if (isProfileMonotonic(updated[page])) {
                                saveProfile(updated[page])
                            }
                        },
                        onUpdateZone4Max = { new ->
                            val updated = updateProfile(allProfilesData, page) { it.copy(z4 = new) }
                            allProfilesData = updated
                            if (isProfileMonotonic(updated[page])) {
                                saveProfile(updated[page])
                            }
                        },
                        onUpdateZone5Max = { new ->
                            val updated = updateProfile(allProfilesData, page) { it.copy(z5 = new) }
                            allProfilesData = updated
                            if (isProfileMonotonic(updated[page])) {
                                saveProfile(updated[page])
                            }
                        },
                        onUpdateZone6Max = { new ->
                            val updated = updateProfile(allProfilesData, page) { it.copy(z6 = new) }
                            allProfilesData = updated
                            if (isProfileMonotonic(updated[page])) {
                                saveProfile(updated[page])
                            }
                        },
                        onUpdateDisplayOptions = { newOptions ->
                            val updated = updateProfile(allProfilesData, page) { it.copy(displayOptions = newOptions) }
                            allProfilesData = updated
                            saveProfile(updated[page])
                        }
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
                            withContext(Dispatchers.Main) {
                                holder.onSaveCompleted()
                                showCalculatorDialog = false
                            }
                            reloadProfiles()
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
                            withContext(Dispatchers.Main) {
                                holder.onSaveCompleted()
                                showCalculatorDialog = false
                            }
                            reloadProfiles()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                holder.onSaveFailed()
                            }
                        }
                    }
                }
            )
        }

        if (showAthleteWeightDialog) {
            AthleteWeightDialog(
                initialWeightKg = storedAthleteWeightKg,
                onDismiss = { showAthleteWeightDialog = false },
                onSaveWeightKg = { weightKg ->
                    scope.launch(Dispatchers.IO) {
                        dataStore.setAthleteWeightKg(weightKg)
                    }
                }
            )
        }
    }
}

private fun isProfileMonotonic(p: ZoneProfileState): Boolean {
    return if (p.type == ZoneType.PWR_BIKE) {
        p.z1 > 0 && p.z2 > p.z1 && p.z3 > p.z2 && p.z4 > p.z3 && p.z5 > p.z4 && p.z6 > p.z5
    } else {
        p.z1 > 0 && p.z2 > p.z1 && p.z3 > p.z2 && p.z4 > p.z3
    }
}

// Helper to update one item in the list immutably
private fun updateProfile(
    list: List<ZoneProfileState>,
    index: Int,
    update: (ZoneProfileState) -> ZoneProfileState
): List<ZoneProfileState> {
    val mutable = list.toMutableList()
    val item = mutable[index]
    mutable[index] = update(item)
    return mutable
}

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
                FilterChip(
                    selected = (previewZone == zIndex),
                    onClick = { previewZone = zIndex },
                    label = { Text(zoneLabels[zIndex - 1]) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(zoneColorsList[zIndex - 1], shape = CircleShape)
                        )
                    },
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
            zoneDisplayOptions = displayOptions
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