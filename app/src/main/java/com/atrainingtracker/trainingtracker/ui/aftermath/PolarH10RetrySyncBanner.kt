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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Material 3 retry sync banner displayed in Aftermath (Workout Details)
 * when a workout has unretrieved offline data stored on a sleeping Polar H10 strap (REQ-SET-106.2, ATT-7919).
 */
@Composable
fun PolarH10RetrySyncBanner(
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isSyncing: Boolean = false,
    errorMessage: String? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            BannerHeader()

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.polar_h10_retry_sync_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            if (isSyncing) {
                BannerSyncingStatus()
            }

            if (!errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            BannerActions(
                isSyncing = isSyncing,
                onDismiss = onDismiss,
                onSyncNow = onSyncNow
            )
        }
    }
}

@Composable
private fun BannerHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.polar_h10_retry_sync_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun BannerSyncingStatus() {
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.polar_h10_retry_syncing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun BannerActions(
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onSyncNow: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onDismiss,
            enabled = !isSyncing
        ) {
            Text(text = stringResource(R.string.polar_h10_retry_sync_dismiss))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onSyncNow,
            enabled = !isSyncing
        ) {
            Text(text = stringResource(R.string.polar_h10_retry_sync_action))
        }
    }
}

@Preview(name = "PolarH10RetrySyncBanner - Light", showBackground = true)
@Preview(name = "PolarH10RetrySyncBanner - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PolarH10RetrySyncBannerPreview() {
    ATrainingTrackerTheme {
        PolarH10RetrySyncBanner(
            onSyncNow = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "PolarH10RetrySyncBanner - Syncing", showBackground = true)
@Composable
private fun PolarH10RetrySyncBannerSyncingPreview() {
    ATrainingTrackerTheme {
        PolarH10RetrySyncBanner(
            onSyncNow = {},
            onDismiss = {},
            isSyncing = true
        )
    }
}
