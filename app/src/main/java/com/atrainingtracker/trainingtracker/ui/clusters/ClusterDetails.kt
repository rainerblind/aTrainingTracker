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

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.database.RouteCluster
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha

/**
 * A reusable component that displays the core information of a RouteCluster.
 * Shared between list items and detail headers to ensure visual consistency.
 */
@Composable
fun ClusterDetails(
    cluster: RouteCluster,
    viewModel: FrequentPathsViewModel,
    modifier: Modifier = Modifier,
    showRecordingsInPrimaryColor: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    useFullWidthHeader: Boolean = true
) {
    val distanceFormatter = remember { DistanceFormatter() }
    val sportName = remember(cluster.probableSportId) { viewModel.getSportName(cluster.probableSportId) }
    val bSportType = remember(cluster.probableSportId) { viewModel.getBSportType(cluster.probableSportId) }
    val linkedEquipment = remember(cluster.probableSportId) { viewModel.getLinkedEquipment(cluster.probableSportId) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- 1. TOP ROW: Standard Header (Icon + Name) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(id = bSportType.iconResId),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.Unspecified
            )
            Text(
                text = cluster.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // --- 2. DETAILS AREA ---
        Column(
            modifier = if (useFullWidthHeader) Modifier.fillMaxWidth() else Modifier,
            horizontalAlignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start
        ) {
            // 1. Distance
            MetricItem(
                iconRes = R.drawable.ic_distance,
                value = distanceFormatter.format_with_units(cluster.refDistance),
                isPrimary = false,
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium),
                modifier = if (textAlign == TextAlign.Center) Modifier.wrapContentWidth() else Modifier
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 2. Sport Type
            Text(
                text = sportName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = textAlign
            )

            // 3. Resulting Equipment
            if (linkedEquipment.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.cluster_equipment_mapping_format, linkedEquipment.joinToString(", ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium),
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 4. Hit Count (Recordings)
            Text(
                text = stringResource(R.string.cluster_recordings_format, cluster.hitCount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (showRecordingsInPrimaryColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = textAlign
            )
        }
    }
}
