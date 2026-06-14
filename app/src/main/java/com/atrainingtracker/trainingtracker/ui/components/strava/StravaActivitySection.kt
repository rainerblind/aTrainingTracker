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
 */

package com.atrainingtracker.trainingtracker.ui.components.strava

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaActivity
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaActivityParser
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaBestEffort
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaSegmentEffort

@Composable
fun StravaActivitySection(
    rawActivityJson: String?,
    modifier: Modifier = Modifier
) {
    val activity = remember(rawActivityJson) {
        StravaActivityParser.parse(rawActivityJson)
    } ?: return

    if (activity.segmentEfforts.isEmpty() && activity.bestEfforts.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- Header Row ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.logo_square_strava),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.strava_results_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // --- Best Efforts (e.g. for runs) ---
        if (activity.bestEfforts.isNotEmpty()) {
            Text(
                text = stringResource(R.string.strava_best_efforts),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            activity.bestEfforts.forEach { effort ->
                BestEffortRow(effort)
            }
        }

        // --- Segment Efforts ---
        if (activity.segmentEfforts.isNotEmpty()) {
            val prCount = activity.segmentEfforts.count { it.prRank != null }
            val segmentTitle = if (prCount > 0) {
                stringResource(R.string.strava_segments_with_prs_format, prCount)
            } else {
                stringResource(R.string.strava_segments_title)
            }
            Text(
                text = segmentTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            
            activity.segmentEfforts.forEach { effort ->
                SegmentEffortRow(effort)
            }
        }
    }
}

@Composable
private fun BestEffortRow(effort: StravaBestEffort) {
    val tf = TimeFormatter()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = effort.name, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (effort.prRank != null) {
                RankBadge(effort.prRank)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = tf.format(effort.elapsedTimeSec.toLong()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SegmentEffortRow(effort: StravaSegmentEffort) {
    val tf = TimeFormatter()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = effort.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (effort.prRank != null) {
                RankBadge(effort.prRank)
                Spacer(Modifier.width(8.dp))
            }
            if (effort.komRank == 1) {
                Icon(
                    painter = painterResource(R.drawable.ic_pr_time),
                    contentDescription = "KOM",
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFFFD700) // Gold
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = tf.format(effort.elapsedTimeSec.toLong()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val color = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> MaterialTheme.colorScheme.primary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.WorkspacePremium,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewStravaActivitySection() {
    val dummyJson = """
        {
          "segment_efforts": [
            { "name": "Alpe d'Huez", "elapsed_time": 3600, "pr_rank": 1, "kom_rank": 1 },
            { "name": "Col du Galibier", "elapsed_time": 4800, "pr_rank": 2 },
            { "name": "Flat Sprint", "elapsed_time": 120 }
          ],
          "best_efforts": [
            { "name": "5k", "elapsed_time": 1200, "pr_rank": 1 },
            { "name": "10k", "elapsed_time": 2500 }
          ]
        }
    """.trimIndent()

    MaterialTheme {
        StravaActivitySection(rawActivityJson = dummyJson)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewStravaActivitySectionNoPRs() {
    val dummyJson = """
        {
          "segment_efforts": [
            { "name": "Local Hill", "elapsed_time": 300 }
          ],
          "best_efforts": []
        }
    """.trimIndent()

    MaterialTheme {
        StravaActivitySection(rawActivityJson = dummyJson)
    }
}
