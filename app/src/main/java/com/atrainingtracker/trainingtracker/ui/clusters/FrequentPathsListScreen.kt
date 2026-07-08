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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.database.RouteCluster
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequentPathsListScreen(
    viewModel: FrequentPathsViewModel,
    onClusterClick: (RouteCluster) -> Unit,
    onTuneClick: () -> Unit
) {
    val clusters by viewModel.allClusters.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_locations)) },
                actions = {
                    IconButton(onClick = onTuneClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings_24),
                            contentDescription = "Tune Clustering"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(clusters) { cluster ->
                ClusterItem(cluster = cluster, onClick = { onClusterClick(cluster) })
            }
        }
    }
}

@Composable
fun ClusterItem(
    cluster: RouteCluster,
    onClick: () -> Unit
) {
    val distanceFormatter = remember { DistanceFormatter() }
    
    MappableListItem(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sport = BSportType.entries.find { it.ordinal.toLong() == cluster.probableSportId } ?: BSportType.UNKNOWN
            Icon(
                painter = painterResource(id = sport.iconResId),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cluster.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.cluster_recordings_format, cluster.hitCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = TTAlpha.Medium)
                )
            }
            
            Text(
                text = distanceFormatter.format_with_units(cluster.refDistance),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = TTAlpha.Subtle)
            )
        }
    }
}
