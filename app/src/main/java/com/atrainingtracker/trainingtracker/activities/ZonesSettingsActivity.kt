package com.atrainingtracker.trainingtracker.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Zone
import kotlinx.coroutines.launch

class ZonesSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SettingsScreenRoute()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenRoute() {
    val context = LocalContext.current
    val dataStore = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()

    // Tab Titles
    val profiles = listOf("HR Run", "HR Bike", "PWR Bike")

    // Pager State handles the swipe logic
    val pagerState = rememberPagerState(pageCount = { profiles.size })

    // Helper: Convert PAGER index to ZoneType Enum
    // We use derivedStateOf so this calculation only happens when currentPage changes
    val selectedZoneType by remember {
        derivedStateOf {
            when (pagerState.currentPage) {
                0 -> ZoneType.HR_RUN
                1 -> ZoneType.HR_BIKE
                else -> ZoneType.PWR_BIKE
            }
        }
    }

    // Collect all values
    val z1Max by key(selectedZoneType) {
        dataStore.getZone1MaxFlow(selectedZoneType).collectAsState(initial = dataStore.getDefaultValue(selectedZoneType, Zone.ZONE_1))
    }
    val z2Max by key(selectedZoneType) {
        dataStore.getZone2MaxFlow(selectedZoneType).collectAsState(initial = dataStore.getDefaultValue(selectedZoneType, Zone.ZONE_2))
    }
    val z3Max by key(selectedZoneType) {
        dataStore.getZone3MaxFlow(selectedZoneType).collectAsState(initial = dataStore.getDefaultValue(selectedZoneType, Zone.ZONE_3))
    }
    val z4Max by key(selectedZoneType) {
        dataStore.getZone4MaxFlow(selectedZoneType).collectAsState(initial = dataStore.getDefaultValue(selectedZoneType, Zone.ZONE_4))
    }

    // TODO: check for consistency

    Scaffold (topBar = { TopAppBar(title = { Text("Edit Zones") })}) {
        padding ->
        Column(modifier = Modifier.padding(padding)) {
            // TABS
            TabRow(selectedTabIndex = pagerState.currentPage) {
                profiles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch {
                                // Animate the pager when a tab is clicked
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { Text(title) }
                    )
                }
            }

            // SWIPABLE CONTENT
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page == pagerState.currentPage) {
                    SettingsScreenContent(
                        title = profiles[page],
                        z1Max = z1Max,
                        z2Max = z2Max,
                        z3Max = z3Max,
                        z4Max = z4Max,

                        onUpdateZone1Max = { newMax ->
                            scope.launch {
                                dataStore.saveHrZoneMax(selectedZoneType, Zone.ZONE_1, newMax)
                            }
                        },

                        onUpdateZone2Max = { newMax ->
                            scope.launch {
                                dataStore.saveHrZoneMax(selectedZoneType, Zone.ZONE_2, newMax)
                            }
                        },

                        onUpdateZone3Max = { newMax ->
                            scope.launch {
                                dataStore.saveHrZoneMax(selectedZoneType, Zone.ZONE_3, newMax)
                            }
                        },

                        onUpdateZone4Max = { newMax ->
                            scope.launch {
                                dataStore.saveHrZoneMax(selectedZoneType, Zone.ZONE_4, newMax)
                            }
                        }
                    )
                } else {
                    // While swiping, show a loading placeholder or the previous values to avoid visual glitching
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    title: String,
    z1Max: Int,
    z2Max: Int,
    z3Max: Int,
    z4Max: Int,
    onUpdateZone1Max: (Int) -> Unit,
    onUpdateZone2Max: (Int) -> Unit,
    onUpdateZone3Max: (Int) -> Unit,
    onUpdateZone4Max: (Int) -> Unit
) {
    // Define standard HR Zone colors (Grey, Blue, Green, Orange, Red)
    val zone1Color = Color(0xFF7FFF00) // Chartreuse
    val zone2Color = Color(0xFF008000) // Green (Standard Green) - or use 0xFF006400 for DarkGreen
    val zone3Color = Color(0xFFFFA500) // Orange
    // val zone3Color = Color(0xFFFFD700) // Gold (More Yellow)
    val zone4Color = Color(0xFFFF0000) // Red
    val zone5Color = Color(0xFF9400D3) // Dark Violet

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp) // Reduced spacing slightly since cards have padding
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )
        // Zone 1
        ZoneRow(
            zoneLabel = "Zone 1 (Recovery)",
            minValue = 100,
            maxValue = z1Max,
            onMinChange = { },  // Z1 Min will never change
            onMaxChange = { onUpdateZone1Max(it) },
            minEnabled = false,
            containerColor = zone1Color // <--- Pass Color
        )

        // Zone 2
        ZoneRow(
            zoneLabel = "Zone 2 (Aerobic)",
            minValue = z1Max + 1,
            maxValue = z2Max,
            onMinChange = { onUpdateZone1Max(it - 1) },
            onMaxChange = { onUpdateZone2Max(it) },
            containerColor = zone2Color // <--- Pass Color
        )

        // Zone 3
        ZoneRow(
            zoneLabel = "Zone 3 (Tempo)",
            minValue = z2Max + 1,
            maxValue = z3Max,
            onMinChange = { onUpdateZone2Max(it - 1) },
            onMaxChange = { onUpdateZone3Max(it) },
            containerColor = zone3Color // <--- Pass Color
        )

        // Zone 4
        ZoneRow(
            zoneLabel = "Zone 4 (Threshold)",
            minValue = z3Max + 1,
            maxValue = z4Max,
            onMinChange = { onUpdateZone3Max(it - 1) },
            onMaxChange = { onUpdateZone4Max(it) },
            containerColor = zone4Color // <--- Pass Color
        )

        // Zone 5
        ZoneRow(
            zoneLabel = "Zone 5 (Anaerobic)",
            minValue = z4Max + 1,
            maxValue = 210,
            onMinChange = { onUpdateZone4Max(it - 1) },
            onMaxChange = { },  // Z5 Max will never change
            maxEnabled = false,
            containerColor = zone5Color // <--- Pass Color
        )

        Spacer(modifier = Modifier.height(16.dp))
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
containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant // Default color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp) // Add padding inside the card
        ) {
            Text(
                text = zoneLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Min Field
                Box(modifier = Modifier.weight(1f)) {
                    IntegerInputField(
                        label = "Min",
                        currentValue = minValue,
                        onValueChange = onMinChange,
                        enabled = minEnabled
                    )
                }
                // Max Field
                Box(modifier = Modifier.weight(1f)) {
                    IntegerInputField(
                        label = "Max",
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
            title = "HR Run",
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