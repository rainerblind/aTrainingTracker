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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZonesSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve the target tab index from the intent, default to 0
        val targetTab = intent.getIntExtra("TARGET_ZONE_TAB", 0)

        setContent {
            MaterialTheme {
                // Pass the initial tab to the route
                SettingsScreenRoute(initialTab = targetTab)
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
    val z4: Int
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreenRoute(
    initialTab: Int = 0,
    onFinish: () -> Unit = {}
) {
    val context = LocalContext.current
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
        }
    }

    // Initial Load
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loadedData = zoneTypes.mapIndexed { index, type ->
                ZoneProfileState(
                    type = type,
                    z1 = dataStore.getZone1MaxFlow(type).first(),
                    z2 = dataStore.getZone2MaxFlow(type).first(),
                    z3 = dataStore.getZone3MaxFlow(type).first(),
                    z4 = dataStore.getZone4MaxFlow(type).first()
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

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.title_edit_zones)) }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // TABS
            TabRow(selectedTabIndex = pagerState.currentPage) {
                profileNameResIds.forEachIndexed { index, titleResId ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(titleResId)) }                    )
                }
            }

            if (allProfilesData.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // SWIPABLE CONTENT
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

                        onUpdateZone1Max = { new ->
                            val updated = updateProfile(allProfilesData, page) { it.copy(z1 = new) }
                            allProfilesData = updated
                            saveProfile(updated[page]) // Save immediately on change}
                        },
                        onUpdateZone2Max = { new ->
                            val updated = updateProfile(allProfilesData, page) { it.copy(z2 = new) }
                            allProfilesData = updated
                            saveProfile(updated[page]) // Save immediately on change
                        },
                        onUpdateZone3Max = { new ->
                            val updated = updateProfile(allProfilesData, page) { it.copy(z3 = new) }
                            allProfilesData = updated
                            saveProfile(updated[page]) // Save immediately on change
                        },
                        onUpdateZone4Max = { new ->
                            val updated = updateProfile(allProfilesData, page) { it.copy(z4 = new) }
                            allProfilesData = updated
                            saveProfile(updated[page]) // Save immediately on change
                        }
                    )
                }
            }
        }
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

    // Apply update
    var newItem = update(item)

    // Apply Cascading Logic
    newItem = newItem.copy(
        z1 = newItem.z1,
        z2 = newItem.z2,
        z3 = newItem.z3,
        z4 = newItem.z4
    )

    mutable[index] = newItem
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
    onUpdateZone1Max: (Int) -> Unit,
    onUpdateZone2Max: (Int) -> Unit,
    onUpdateZone3Max: (Int) -> Unit,
    onUpdateZone4Max: (Int) -> Unit
) {
    // Load Colors from resources
    val zone1Color = colorResource(id = R.color.zone_1)
    val zone2Color = colorResource(id = R.color.zone_2)
    val zone3Color = colorResource(id = R.color.zone_3)
    val zone4Color = colorResource(id = R.color.zone_4)
    val zone5Color = colorResource(id = R.color.zone_5)

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
            minValue = 100,
            maxValue = z1Max,
            onMinChange = { },
            onMaxChange = { onUpdateZone1Max(it) },
            minEnabled = false,
            containerColor = zone1Color
        )

        // Zone 2
        ZoneRow(
            zoneLabel = stringResource(R.string.zone_2_label),
            minValue = z1Max + 1,
            maxValue = z2Max,
            onMinChange = { onUpdateZone1Max(it - 1) },
            onMaxChange = { onUpdateZone2Max(it) },
            containerColor = zone2Color
        )

        // Zone 3
        ZoneRow(
            zoneLabel = stringResource(R.string.zone_3_label),
            minValue = z2Max + 1,
            maxValue = z3Max,
            onMinChange = { onUpdateZone2Max(it - 1) },
            onMaxChange = { onUpdateZone3Max(it) },
            containerColor = zone3Color
        )

        // Zone 4
        ZoneRow(
            zoneLabel = stringResource(R.string.zone_4_label),
            minValue = z3Max + 1,
            maxValue = z4Max,
            onMinChange = { onUpdateZone3Max(it - 1) },
            onMaxChange = { onUpdateZone4Max(it) },
            containerColor = zone4Color
        )

        // Zone 5
        ZoneRow(
            zoneLabel = stringResource(R.string.zone_5_label),
            minValue = z4Max + 1,
            maxValue = 210,
            onMinChange = { onUpdateZone4Max(it - 1) },
            onMaxChange = { },
            maxEnabled = false,
            containerColor = zone5Color
        )

        Spacer(modifier = Modifier.height(8.dp))
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
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = zoneLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        enabled = maxEnabled
                    )
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
    enabled : Boolean = true
) {
    var textState by remember(currentValue) { mutableStateOf(currentValue.toString()) }

    OutlinedTextField(
        value = textState,
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