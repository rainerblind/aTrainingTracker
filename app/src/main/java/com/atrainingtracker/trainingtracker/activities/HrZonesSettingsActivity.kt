package com.atrainingtracker.trainingtracker.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import kotlinx.coroutines.launch
import kotlin.math.max

class HrZonesSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SettingsScreenRoute()
            }
        }
    }
}

@Composable
fun SettingsScreenRoute() {
    val context = LocalContext.current
    val dataStore = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()

    // Collect all values
    val z1Max by dataStore.hrZone1MaxFlow.collectAsState(initial = 140)

    val z2Min by dataStore.hrZone2MinFlow.collectAsState(initial = 141)
    val z2Max by dataStore.hrZone2MaxFlow.collectAsState(initial = 160)

    val z3Min by dataStore.hrZone3MinFlow.collectAsState(initial = 161)
    val z3Max by dataStore.hrZone3MaxFlow.collectAsState(initial = 170)

    val z4Min by dataStore.hrZone4MinFlow.collectAsState(initial = 171)
    val z4Max by dataStore.hrZone4MaxFlow.collectAsState(initial = 180)

    val z5Min by dataStore.hrZone5MinFlow.collectAsState(initial = 181)

    SettingsScreenContent(
                       z1Max = z1Max,
        z2Min = z2Min, z2Max = z2Max,
        z3Min = z3Min, z3Max = z3Max,
        z4Min = z4Min, z4Max = z4Max,
        z5Min = z5Min,

        // --- LOGIC: Validate Min/Max relationships ---
        onUpdateZone1 = { newMin, newMax ->
            scope.launch {

                val newZ1Max = max(newMax, 100)  // constraint for Z1 Max
                dataStore.saveHrZone1Max(newZ1Max)

                // Constraint: Zone 2 Min must be Zone 1 Max + 1
                val newZ2Min = newZ1Max + 1
                dataStore.saveHrZone2Min(newZ2Min)
                // If pushing Z2 Min up makes it hit Z2 Max, push Z2 Max by 5, too
                if (newZ2Min >= z2Max) dataStore.saveHrZone2Max(newZ2Min + 5)
            }
        },
        onUpdateZone2 = { newMin, newMax ->
            scope.launch {
                // TODO: constraint on Z2Min?
                dataStore.saveHrZone2Min(newMin)
                dataStore.saveHrZone2Max(newMax)

                // Backward Constraint: Zone 1 Max must be Zone 2 Min -1
                val newZ1Max = newMin - 1
                dataStore.saveHrZone1Max(newZ1Max)

                // Forward Constraint: Zone 3 Min must be Zone 2 Max + 1
                val newZ3Min = newMax + 1
                dataStore.saveHrZone3Min(newZ3Min)
                // If pushing Z3 Min up makes it hit Z3 Max, push Z3 Max by 5, too
                if (newZ3Min >= z3Max) dataStore.saveHrZone3Max(newZ3Min + 5)
            }
        },
        onUpdateZone3 = { newMin, newMax ->
            scope.launch {
                dataStore.saveHrZone3Min(newMin)
                dataStore.saveHrZone3Max(newMax)

                // Backward Constraint
                val newZ2Max = newMin - 1
                dataStore.saveHrZone2Max(newZ2Max)
                if (newZ2Max <= z2Min) dataStore.saveHrZone2Min(newZ2Max - 5)

                // Forward Constraint
                val newZ4Min = newMax + 1
                dataStore.saveHrZone4Min(newZ4Min)
                if (newZ4Min >= z4Max) dataStore.saveHrZone4Max(newZ4Min + 5)
            }
        },
        onUpdateZone4 = { newMin, newMax ->
            scope.launch {
                dataStore.saveHrZone4Min(newMin)
                dataStore.saveHrZone4Max(newMax)

                // Backward Constraint
                val newZ3Max = newMin - 1
                dataStore.saveHrZone3Max(newZ3Max)
                if (newZ3Max <= z3Min) dataStore.saveHrZone3Min(newZ3Max - 5)

                // Forward Constraint
                val newZ5Min = newMax + 1
                dataStore.saveHrZone5Min(newZ5Min)
            }
        },
        onUpdateZone5 = { newMin, newMax ->
            scope.launch {
                dataStore.saveHrZone5Min(newMin)

                // Backward Constraint
                val newZ4Max = newMin - 1
                dataStore.saveHrZone4Max(newZ4Max)
                if (newZ4Max <= z4Min) dataStore.saveHrZone4Min(newZ4Max - 5)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
                z1Max: Int,
    z2Min: Int, z2Max: Int,
    z3Min: Int, z3Max: Int,
    z4Min: Int, z4Max: Int,
    z5Min: Int,
    onUpdateZone1: (Int, Int) -> Unit,
    onUpdateZone2: (Int, Int) -> Unit,
    onUpdateZone3: (Int, Int) -> Unit,
    onUpdateZone4: (Int, Int) -> Unit,
    onUpdateZone5: (Int, Int) -> Unit
) {
    // Define standard HR Zone colors (Grey, Blue, Green, Orange, Red)
    val zone1Color = Color(0xFF7FFF00) // Chartreuse
    val zone2Color = Color(0xFF008000) // Green (Standard Green) - or use 0xFF006400 for DarkGreen
    val zone3Color = Color(0xFFFFA500) // Orange
    // val zone3Color = Color(0xFFFFD700) // Gold (More Yellow)
    val zone4Color = Color(0xFFFF0000) // Red
    val zone5Color = Color(0xFF9400D3) // Dark Violet

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Edit HR Zones") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Reduced spacing slightly since cards have padding
        ) {
            // Zone 1
            ZoneRow(
                zoneLabel = "Zone 1 (Recovery)",
                minValue = 100,
                maxValue = z1Max,
                onMinChange = { onUpdateZone1(it, z1Max) },
                onMaxChange = { onUpdateZone1(100, it) },
                minEnabled = false,
                containerColor = zone1Color // <--- Pass Color
            )

            // Zone 2
            ZoneRow(
                zoneLabel = "Zone 2 (Aerobic)",
                minValue = z2Min,
                maxValue = z2Max,
                onMinChange = { onUpdateZone2(it, z2Max) },
                onMaxChange = { onUpdateZone2(z2Min, it) },
                containerColor = zone2Color // <--- Pass Color
            )

            // Zone 3
            ZoneRow(
                zoneLabel = "Zone 3 (Tempo)",
                minValue = z3Min,
                maxValue = z3Max,
                onMinChange = { onUpdateZone3(it, z3Max) },
                onMaxChange = { onUpdateZone3(z3Min, it) },
                containerColor = zone3Color // <--- Pass Color
            )

            // Zone 4
            ZoneRow(
                zoneLabel = "Zone 4 (Threshold)",
                minValue = z4Min,
                maxValue = z4Max,
                onMinChange = { onUpdateZone4(it, z4Max) },
                onMaxChange = { onUpdateZone4(z4Min, it) },
                containerColor = zone4Color // <--- Pass Color
            )

            // Zone 5
            ZoneRow(
                zoneLabel = "Zone 5 (Anaerobic)",
                minValue = z5Min,
                maxValue = 210,
                onMinChange = { onUpdateZone5(it, 210) },
                onMaxChange = { onUpdateZone5(z5Min, it) },
                maxEnabled = false,
                containerColor = zone5Color // <--- Pass Color
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
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
                         z1Max = 130,
            z2Min = 131, z2Max = 150,
            z3Min = 151, z3Max = 170,
            z4Min = 171, z4Max = 180,
            z5Min = 181,
            onUpdateZone1 = {_,_ ->},
            onUpdateZone2 = {_,_ ->},
            onUpdateZone3 = {_,_ ->},
            onUpdateZone4 = {_,_ ->},
            onUpdateZone5 = {_,_ ->}
        )
    }
}