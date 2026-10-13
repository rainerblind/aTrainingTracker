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

package com.atrainingtracker.banalservice.ui.devices.ant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.theme.safePainterResource

/**
 * Modern Material 3 Bottom Sheet detailing ANT+ system services, hardware prerequisites
 * (USB-OTG adapter + ANT+ USB dongle), live package installation states, and Google Play install actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntServicesStatusSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var checkTrigger by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isPluginInstalled = remember(checkTrigger) {
        BANALService.isANTPluginServiceInstalled(context)
    }
    val isRadioInstalled = remember(checkTrigger) {
        BANALService.isANTRadioServiceInstalled()
    }
    val hasUsbHost = remember {
        BANALService.hasUsbHostFeature(context)
    }
    val isUsbServiceInstalled = remember(checkTrigger, hasUsbHost) {
        if (hasUsbHost) BANALService.isANTUSBServiceInstalled() else false
    }

    AppModalBottomSheet(
        title = stringResource(R.string.ant_status_sheet_title),
        onDismissRequest = onDismiss,
        iconPainter = safePainterResource(id = R.drawable.ant_logo),
        iconTint = Color.Unspecified,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hardware notice
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.ant_status_usb_dongle_note),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Service status list
            Text(
                text = stringResource(R.string.ANT_Installation),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 1. ANT+ Plugins Service
            ServiceStatusRow(
                serviceName = stringResource(R.string.ant_service_plugin_name),
                isInstalled = isPluginInstalled,
                onInstallClick = {
                    launchServiceInstall(context, "com.dsi.ant.plugins.antplus")
                }
            )

            HorizontalDivider()

            // 2. ANT Radio Service
            ServiceStatusRow(
                serviceName = stringResource(R.string.ant_service_radio_name),
                isInstalled = isRadioInstalled,
                onInstallClick = {
                    launchServiceInstall(context, BANALService.URI_ANT_RADIO_SERVICE)
                }
            )

            if (hasUsbHost) {
                HorizontalDivider()

                // 3. ANT USB Service
                ServiceStatusRow(
                    serviceName = stringResource(R.string.ant_service_usb_name),
                    isInstalled = isUsbServiceInstalled,
                    onInstallClick = {
                        launchServiceInstall(context, BANALService.URI_ANT_USB_SERVICE)
                    }
                )
            }


            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.OK))
            }
        }
    }
}

@Composable
private fun ServiceStatusRow(
    serviceName: String,
    isInstalled: Boolean,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = serviceName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isInstalled) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(
                        if (isInstalled) R.string.ant_service_installed
                        else R.string.ant_service_not_installed
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        if (!isInstalled) {
            Button(
                onClick = onInstallClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.ant_service_install_button))
            }
        }
    }
}

fun launchServiceInstall(context: Context, packageName: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (anfe: ActivityNotFoundException) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
