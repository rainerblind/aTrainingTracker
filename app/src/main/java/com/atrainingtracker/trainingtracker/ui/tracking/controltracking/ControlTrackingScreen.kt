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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.ui.devices.DeviceTypeSelectionDialog
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ControlTrackingScreen(
    trackingMode: TrackingMode,
    searchingFor: String?,
    devices: List<RemoteDeviceUIData>,
    currentSport: BSportType,
    isAntSupported: Boolean,
    isBluetoothSupported: Boolean,
    onSearch: () -> Unit,
    onDeviceClick: (RemoteDeviceUIData) -> Unit,
    onSportSelected: (BSportType) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onPairingClicked: (Protocol) -> Unit,
    selectingProtocol: Protocol?,
    onDeviceTypeSelected: (DeviceType) -> Unit,
    onCancelDeviceTypeSelection: () -> Unit,
    showResearchButton: Boolean = true,
    locationCalibrationStatus: com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.LocationCalibrationStatus? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val checkHasLocation = {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val checkHasBackgroundLocation = {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    val checkIsIgnoringBatteryOptimizations = {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(checkHasLocation())
    }

    var rationaleStep by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(RationaleStep.NONE)
    }
    var isPermanentlyDenied by remember { mutableStateOf(false) }

    val proceedAfterPermissions: () -> Unit = {
        if (!checkHasBackgroundLocation()) {
            val activity = context as? android.app.Activity
            if (activity != null) {
                val showRationale = androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
                isPermanentlyDenied = !showRationale && !checkHasBackgroundLocation()
            }
            rationaleStep = RationaleStep.BACKGROUND_LOCATION
        } else if (!checkIsIgnoringBatteryOptimizations()) {
            isPermanentlyDenied = false
            rationaleStep = RationaleStep.BATTERY_OPTIMIZATION
        } else {
            rationaleStep = RationaleStep.NONE
            onStart()
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = results[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val granted = fineGranted || coarseGranted
        hasLocationPermission = granted
        if (granted) {
            isPermanentlyDenied = false
            proceedAfterPermissions()
        } else {
            val activity = context as? android.app.Activity
            if (activity != null) {
                val showRationale = androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
                isPermanentlyDenied = !showRationale
            }
        }
    }

    val bgLocationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || checkHasBackgroundLocation()) {
            isPermanentlyDenied = false
            if (!checkIsIgnoringBatteryOptimizations()) {
                rationaleStep = RationaleStep.BATTERY_OPTIMIZATION
            } else {
                rationaleStep = RationaleStep.NONE
                onStart()
            }
        } else {
            val activity = context as? android.app.Activity
            if (activity != null) {
                val showRationale = androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
                isPermanentlyDenied = !showRationale
            }
        }
    }

    // Observe lifecycle changes to re-check when user returns to the app (e.g. from Settings)
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val permitted = checkHasLocation()
                hasLocationPermission = permitted
                if (permitted && rationaleStep == RationaleStep.FOREGROUND) {
                    isPermanentlyDenied = false
                    proceedAfterPermissions()
                } else if (checkHasBackgroundLocation() && rationaleStep == RationaleStep.BACKGROUND_LOCATION) {
                    isPermanentlyDenied = false
                    proceedAfterPermissions()
                } else if (checkIsIgnoringBatteryOptimizations() && rationaleStep == RationaleStep.BATTERY_OPTIMIZATION) {
                    rationaleStep = RationaleStep.NONE
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val handleStartClick = {
        if (!checkHasLocation()) {
            val activity = context as? android.app.Activity
            if (activity != null) {
                val showRationale = androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
                isPermanentlyDenied = !showRationale && !hasLocationPermission
            }
            rationaleStep = RationaleStep.FOREGROUND
        } else {
            proceedAfterPermissions()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LocationCalibrationBadge(
            status = locationCalibrationStatus,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
        ) {
            // The Information Area - Anchored to the MATHEMATICAL CENTER of the screen
            Column(
                modifier = Modifier.align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SearchArea(
                    searchingFor = searchingFor
                )

                RemoteDevices(
                    devices = devices,
                    onDeviceClick = onDeviceClick
                )
            }

            // Research Button - Anchored to the far left of the screen
            // note that this must be added at the end to get the clicking working...
            if (showResearchButton) {
                Box(modifier = Modifier.align(Alignment.TopStart)) {
                    ResearchButton(
                        isEnabled = searchingFor == null,
                        onClick = onSearch
                    )
                }
            }
        }

        // Pushes the main control buttons to the center
        Spacer(modifier = Modifier.weight(1f))

        // Large Control Buttons (Start/Pause/Stop)
        ControlTrackingButton(
            modifier = Modifier.fillMaxWidth(),
            mode = trackingMode,
            enabled = true,
            hasPermissionWarning = !hasLocationPermission,
            onStart = handleStartClick,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Sport Selection
        SportTypeSelector(
            currentSport = currentSport,
            onSportSelected = onSportSelected
        )

        // Pushes the main control buttons to the center
        Spacer(modifier = Modifier.weight(1f))

        // Pairing Buttons
        PairingButtons(
            isAntSupported = isAntSupported,
            isBluetoothSupported = isBluetoothSupported,
            onPairingClicked = onPairingClicked
        )
    }

    // Material 3 Permission Rationale Sheet (REQ-PRI-003, ATT-2075)
    if (rationaleStep != RationaleStep.NONE) {
        val permissionsToRequest = remember {
            val perms = mutableListOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                perms.add(android.Manifest.permission.BLUETOOTH_CONNECT)
                perms.add(android.Manifest.permission.BLUETOOTH_SCAN)
            }
            perms.toTypedArray()
        }

        val rationaleType = when (rationaleStep) {
            RationaleStep.FOREGROUND -> RationaleType.FOREGROUND
            RationaleStep.BACKGROUND_LOCATION -> RationaleType.BACKGROUND_LOCATION
            RationaleStep.BATTERY_OPTIMIZATION -> RationaleType.BATTERY_OPTIMIZATION
            RationaleStep.NONE -> RationaleType.FOREGROUND
        }

        val openSettingsAction = {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
            rationaleStep = RationaleStep.NONE
        }

        PermissionRationaleSheet(
            rationaleType = rationaleType,
            isPermanentlyDenied = isPermanentlyDenied,
            onContinue = {
                when (rationaleStep) {
                    RationaleStep.FOREGROUND -> {
                        permissionLauncher.launch(permissionsToRequest)
                    }
                    RationaleStep.BACKGROUND_LOCATION -> {
                        if (isPermanentlyDenied) {
                            openSettingsAction()
                        } else {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                bgLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            } else {
                                proceedAfterPermissions()
                            }
                        }
                    }
                    RationaleStep.BATTERY_OPTIMIZATION -> {
                        launchBatteryOptimizationIntent(context)
                        rationaleStep = RationaleStep.NONE
                        onStart()
                    }
                    RationaleStep.NONE -> {}
                }
            },
            onOpenSettings = openSettingsAction,
            onDismissRequest = {
                when (rationaleStep) {
                    RationaleStep.FOREGROUND -> {
                        rationaleStep = RationaleStep.NONE
                    }
                    RationaleStep.BACKGROUND_LOCATION -> {
                        // User chose "Not now" for background location; proceed gracefully
                        if (!checkIsIgnoringBatteryOptimizations()) {
                            rationaleStep = RationaleStep.BATTERY_OPTIMIZATION
                        } else {
                            rationaleStep = RationaleStep.NONE
                            onStart()
                        }
                    }
                    RationaleStep.BATTERY_OPTIMIZATION -> {
                        // User chose "Not now" for battery optimization; start tracking
                        rationaleStep = RationaleStep.NONE
                        onStart()
                    }
                    RationaleStep.NONE -> {}
                }
            }
        )
    }

    // Device Type Selection Dialog
    selectingProtocol?.let { protocol ->
        DeviceTypeSelectionDialog(
            protocol = protocol,
            onSelected = onDeviceTypeSelected,
            onDismiss = onCancelDeviceTypeSelection
        )
    }
}

/**
 * Step states for progressive Just-in-Time permission and power setup flow (REQ-PRI-003, ATT-2075).
 */
enum class RationaleStep {
    NONE,
    FOREGROUND,
    BACKGROUND_LOCATION,
    BATTERY_OPTIMIZATION
}

/**
 * Fail-safe battery optimization intent launcher with 3-tier fallback cascading (ATT-2075).
 */
fun launchBatteryOptimizationIntent(context: android.content.Context) {
    try {
        val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallback = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(fallback)
        } catch (e2: Exception) {
            try {
                val details = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(details)
            } catch (e3: Exception) {
                // Safeguard against extreme OEM ROM restrictions
            }
        }
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Light Mode - Searching")
@Preview(
    showBackground = true,
    name = "Dark Mode - Searching",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewControlTrackingScreenSearching() {
    ATrainingTrackerTheme {
        Surface {
            ControlTrackingScreen(
                trackingMode = TrackingMode.READY,
                searchingFor = "my spd",
                devices = listOf(RemoteDeviceUIData(1, deviceType = DeviceType.HRM, name = "Polar H10", R.drawable.hr)),
                currentSport = BSportType.RUN,
                isAntSupported = true,
                isBluetoothSupported = true,
                onSearch = {}, onDeviceClick = {}, onSportSelected = {},
                onStart = {}, onPause = {}, onResume = {}, onStop = {}, onPairingClicked = {},
                selectingProtocol = null, onDeviceTypeSelected = {}, onCancelDeviceTypeSelection = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Light Mode - Not Searching")
@Preview(
    showBackground = true,
    name = "Dark Mode - Not Searching",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewControlTrackingScreen() {
    ATrainingTrackerTheme {
        Surface {
            ControlTrackingScreen(
                trackingMode = TrackingMode.READY,
                searchingFor = null,
                devices = listOf(RemoteDeviceUIData(1, deviceType = DeviceType.HRM, name = "Polar H10", R.drawable.hr)),
                currentSport = BSportType.RUN,
                isAntSupported = true,
                isBluetoothSupported = true,
                onSearch = {}, onDeviceClick = {}, onSportSelected = {},
                onStart = {}, onPause = {}, onResume = {}, onStop = {}, onPairingClicked = {},
                selectingProtocol = null, onDeviceTypeSelected = {}, onCancelDeviceTypeSelection = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Light Mode - No Remote Devices (Hidden Suchen)")
@Preview(
    showBackground = true,
    name = "Dark Mode - No Remote Devices (Hidden Suchen)",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewControlTrackingScreenNoRemoteDevices() {
    ATrainingTrackerTheme {
        Surface {
            ControlTrackingScreen(
                trackingMode = TrackingMode.READY,
                searchingFor = null,
                devices = emptyList(),
                currentSport = BSportType.RUN,
                isAntSupported = true,
                isBluetoothSupported = true,
                onSearch = {}, onDeviceClick = {}, onSportSelected = {},
                onStart = {}, onPause = {}, onResume = {}, onStop = {}, onPairingClicked = {},
                selectingProtocol = null, onDeviceTypeSelected = {}, onCancelDeviceTypeSelection = {},
                showResearchButton = false
            )
        }
    }
}





