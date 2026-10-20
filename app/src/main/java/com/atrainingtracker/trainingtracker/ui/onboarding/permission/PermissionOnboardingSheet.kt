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

package com.atrainingtracker.trainingtracker.ui.onboarding.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Sequential permission onboarding steps for initial app launch (REQ-PRI-007 / ATT-4455).
 */
enum class OnboardingStep {
    LOCATION,
    BLUETOOTH,
    NOTIFICATION,
    BATTERY_OPTIMIZATION
}

val OnboardingStep.iconRes: Int get() = when (this) {
    OnboardingStep.LOCATION -> R.drawable.ic_location
    OnboardingStep.BLUETOOTH -> R.drawable.ic_my_paired_devices
    OnboardingStep.NOTIFICATION -> R.drawable.ic_lap_timer
    OnboardingStep.BATTERY_OPTIMIZATION -> R.drawable.ic_battery_full
}

val OnboardingStep.titleRes: Int get() = when (this) {
    OnboardingStep.LOCATION -> R.string.permission_onboarding_location_title
    OnboardingStep.BLUETOOTH -> R.string.permission_onboarding_bluetooth_title
    OnboardingStep.NOTIFICATION -> R.string.permission_onboarding_notification_title
    OnboardingStep.BATTERY_OPTIMIZATION -> R.string.permission_onboarding_battery_title
}

val OnboardingStep.descRes: Int get() = when (this) {
    OnboardingStep.LOCATION -> R.string.permission_onboarding_location_desc
    OnboardingStep.BLUETOOTH -> R.string.permission_onboarding_bluetooth_desc
    OnboardingStep.NOTIFICATION -> R.string.permission_onboarding_notification_desc
    OnboardingStep.BATTERY_OPTIMIZATION -> R.string.permission_onboarding_battery_desc
}

/**
 * Helper checking whether the specified onboarding step is already granted / satisfied on this device.
 */
fun isStepPermissionGranted(context: Context, step: OnboardingStep): Boolean = when (step) {
    OnboardingStep.LOCATION -> ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    OnboardingStep.BLUETOOTH -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else true
    OnboardingStep.NOTIFICATION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    OnboardingStep.BATTERY_OPTIMIZATION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else true
}

/**
 * Modal bottom sheet presenting an educational, multi-step onboarding wizard for first-time app launch.
 * Enables athletes to configure Location, Bluetooth, Notifications, and Battery Optimization exemption (REQ-PRI-007 / ATT-4455).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionOnboardingSheet(
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val steps = remember { listOf(OnboardingStep.LOCATION, OnboardingStep.BLUETOOTH, OnboardingStep.NOTIFICATION, OnboardingStep.BATTERY_OPTIMIZATION) }
    var currentStepIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentStep = steps[currentStepIndex.coerceIn(0, steps.lastIndex)]

    var isCurrentGranted by remember(currentStepIndex) {
        mutableStateOf(isStepPermissionGranted(context, currentStep))
    }

    fun advanceOrFinish() {
        if (currentStepIndex < steps.lastIndex) {
            currentStepIndex++
        } else {
            onFinish()
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        isCurrentGranted = fineGranted
        if (fineGranted) advanceOrFinish()
    }

    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = isStepPermissionGranted(context, OnboardingStep.BLUETOOTH)
        isCurrentGranted = granted
        if (granted) advanceOrFinish()
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val granted = isStepPermissionGranted(context, OnboardingStep.NOTIFICATION)
        isCurrentGranted = granted
        if (granted) advanceOrFinish()
    }

    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = isStepPermissionGranted(context, OnboardingStep.BATTERY_OPTIMIZATION)
        isCurrentGranted = granted
        if (granted) advanceOrFinish()
    }

    ModalBottomSheet(
        onDismissRequest = onFinish,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        PermissionOnboardingContent(
            currentStep = currentStep,
            totalSteps = steps.size,
            currentStepIndex = currentStepIndex,
            isCurrentStepGranted = isCurrentGranted,
            onGrant = {
                when (currentStep) {
                    OnboardingStep.LOCATION -> locationLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                    OnboardingStep.BLUETOOTH -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            bluetoothLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                        } else advanceOrFinish()
                    }
                    OnboardingStep.NOTIFICATION -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else advanceOrFinish()
                    }
                    OnboardingStep.BATTERY_OPTIMIZATION -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                batteryLauncher.launch(intent)
                            } catch (_: Exception) {
                                advanceOrFinish()
                            }
                        } else advanceOrFinish()
                    }
                }
            },
            onContinue = { advanceOrFinish() },
            onSkipStep = { advanceOrFinish() },
            onSkipSetup = onFinish
        )
    }
}

/**
 * Pure presentational content for the permission onboarding bottom sheet.
 */
@Composable
fun PermissionOnboardingContent(
    currentStep: OnboardingStep,
    totalSteps: Int,
    currentStepIndex: Int,
    isCurrentStepGranted: Boolean,
    onGrant: () -> Unit,
    onContinue: () -> Unit,
    onSkipStep: () -> Unit,
    onSkipSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.permission_onboarding_step_format, currentStepIndex + 1, totalSteps),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onSkipSetup) {
                Text(text = stringResource(R.string.permission_onboarding_skip_setup), style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Icon(
            painter = painterResource(id = currentStep.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(id = currentStep.titleRes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(id = currentStep.descRes),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = currentStep.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(id = currentStep.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        val isFinalStep = currentStepIndex == totalSteps - 1
        val primaryText = if (isCurrentStepGranted) {
            if (isFinalStep) stringResource(R.string.permission_onboarding_finish)
            else stringResource(R.string.permission_onboarding_continue)
        } else {
            stringResource(R.string.permission_onboarding_grant)
        }

        Button(
            onClick = if (isCurrentStepGranted) onContinue else onGrant,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = primaryText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onSkipStep, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.permission_onboarding_skip_step),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "Light Mode - Location Step", showBackground = true)
@Composable
private fun PreviewPermissionOnboardingLight() {
    ATrainingTrackerTheme {
        Surface {
            PermissionOnboardingContent(
                currentStep = OnboardingStep.LOCATION,
                totalSteps = 4,
                currentStepIndex = 0,
                isCurrentStepGranted = false,
                onGrant = {}, onContinue = {}, onSkipStep = {}, onSkipSetup = {}
            )
        }
    }
}

@Preview(name = "Dark Mode - Bluetooth Step", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewPermissionOnboardingDark() {
    ATrainingTrackerTheme {
        Surface {
            PermissionOnboardingContent(
                currentStep = OnboardingStep.BLUETOOTH,
                totalSteps = 4,
                currentStepIndex = 1,
                isCurrentStepGranted = true,
                onGrant = {}, onContinue = {}, onSkipStep = {}, onSkipSetup = {}
            )
        }
    }
}

@Preview(name = "AMOLED Dark Mode - Battery Step", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewPermissionOnboardingAmoled() {
    ATrainingTrackerTheme {
        Surface {
            PermissionOnboardingContent(
                currentStep = OnboardingStep.BATTERY_OPTIMIZATION,
                totalSteps = 4,
                currentStepIndex = 3,
                isCurrentStepGranted = false,
                onGrant = {}, onContinue = {}, onSkipStep = {}, onSkipSetup = {}
            )
        }
    }
}
