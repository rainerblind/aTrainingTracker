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
// Removed Icon imports as they are no longer needed for the FAB
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
        setContent {
            MaterialTheme {
                SettingsScreenRoute()
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
fun SettingsScreenRoute(onFinish: () -> Unit = {}) {
    val context = LocalContext.current
    val dataStore = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()

    val profiles = listOf("HR Run", "HR Bike", "PWR Bike")
    val zoneTypes = listOf(ZoneType.HR_RUN, ZoneType.HR_BIKE, ZoneType.PWR_BIKE)

    // Local state to hold data for swiping performance
    var allProfilesData by remember { mutableStateOf<List<ZoneProfileState>>(emptyList()) }

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

    // --- AUTO-SAVE LOGIC ---
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // rememberUpdatedState ensures the observer uses the very latest data when the event triggers
    val currentData by rememberUpdatedState(allProfilesData)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // ON_STOP is called when the Activity is no longer visible
            if (event == Lifecycle.Event.ON_STOP) {
                if (currentData.isNotEmpty()) {
                    // We use the existing scope. Note: In complex apps, if the scope is
                    // cancelled too quickly, you might use GlobalScope or NonCancellable here,
                    // but for DataStore this usually completes fine.
                    scope.launch(Dispatchers.IO) {
                        currentData.forEach { profile ->
                            dataStore.saveHrZoneMax(profile.type, Zone.ZONE_1, profile.z1)
                            dataStore.saveHrZoneMax(profile.type, Zone.ZONE_2, profile.z2)
                            dataStore.saveHrZoneMax(profile.type, Zone.ZONE_3, profile.z3)
                            dataStore.saveHrZoneMax(profile.type, Zone.ZONE_4, profile.z4)
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Pager State
    val pagerState = rememberPagerState(pageCount = { profiles.size })

    Scaffold(
        topBar = { TopAppBar(title = { Text("Edit Zones") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // TABS
            TabRow(selectedTabIndex = pagerState.currentPage) {
                profiles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
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

                        // Update the LOCAL List State (in memory)
                        onUpdateZone1Max = { new ->
                            allProfilesData = updateProfile(allProfilesData, page) { it.copy(z1 = new) }
                        },
                        onUpdateZone2Max = { new ->
                            allProfilesData = updateProfile(allProfilesData, page) { it.copy(z2 = new) }
                        },
                        onUpdateZone3Max = { new ->
                            allProfilesData = updateProfile(allProfilesData, page) { it.copy(z3 = new) }
                        },
                        onUpdateZone4Max = { new ->
                            allProfilesData = updateProfile(allProfilesData, page) { it.copy(z4 = new) }
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
    // Define standard HR Zone colors (Grey, Blue, Green, Orange, Red)
    val zone1Color = Color(0xFF7FFF00) // Chartreuse
    val zone2Color = Color(0xFF008000) // Green
    val zone3Color = Color(0xFFFFA500) // Orange
    val zone4Color = Color(0xFFFF0000) // Red
    val zone5Color = Color(0xFF9400D3) // Dark Violet

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
            zoneLabel = "Zone 1 (Recovery)",
            minValue = 100,
            maxValue = z1Max,
            onMinChange = { },
            onMaxChange = { onUpdateZone1Max(it) },
            minEnabled = false,
            containerColor = zone1Color
        )

        // Zone 2
        ZoneRow(
            zoneLabel = "Zone 2 (Aerobic)",
            minValue = z1Max + 1,
            maxValue = z2Max,
            onMinChange = { onUpdateZone1Max(it - 1) },
            onMaxChange = { onUpdateZone2Max(it) },
            containerColor = zone2Color
        )

        // Zone 3
        ZoneRow(
            zoneLabel = "Zone 3 (Tempo)",
            minValue = z2Max + 1,
            maxValue = z3Max,
            onMinChange = { onUpdateZone2Max(it - 1) },
            onMaxChange = { onUpdateZone3Max(it) },
            containerColor = zone3Color
        )

        // Zone 4
        ZoneRow(
            zoneLabel = "Zone 4 (Threshold)",
            minValue = z3Max + 1,
            maxValue = z4Max,
            onMinChange = { onUpdateZone3Max(it - 1) },
            onMaxChange = { onUpdateZone4Max(it) },
            containerColor = zone4Color
        )

        // Zone 5
        ZoneRow(
            zoneLabel = "Zone 5 (Anaerobic)",
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