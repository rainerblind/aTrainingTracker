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

package com.atrainingtracker.trainingtracker.ui.map.offline

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/** Test tag for the OpenStreetMap ODbL attribution pill (`REQ-MAP-071.4`). */
const val OFFLINE_OSM_ATTRIBUTION_TEST_TAG = "offline_osm_attribution"

/**
 * Compact, high-contrast OpenStreetMap ODbL attribution badge displayed over `ATrainingTrackerMap`
 * whenever offline Mapsforge OSM tiles are actively rendered (`REQ-MAP-071.4`).
 *
 * Positioned at `Alignment.BottomEnd` respecting `contentPadding` so it clears both the bottom-start
 * Google Maps watermark and bottom sheets/scrubber panels (Gate 3 Watchpoint 5).
 */
@Composable
fun OfflineOsmAttributionBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .testTag(OFFLINE_OSM_ATTRIBUTION_TEST_TAG),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        tonalElevation = 1.dp
    ) {
        Text(
            text = stringResource(R.string.offline_map_osm_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Preview(name = "OSM Attribution Light", showBackground = true)
@Composable
private fun OfflineOsmAttributionBadgeLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        OfflineOsmAttributionBadge(modifier = Modifier.padding(8.dp))
    }
}

@Preview(name = "OSM Attribution Dark", showBackground = true)
@Composable
private fun OfflineOsmAttributionBadgeDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        OfflineOsmAttributionBadge(modifier = Modifier.padding(8.dp))
    }
}

@Preview(name = "OSM Attribution AMOLED", showBackground = true)
@Composable
private fun OfflineOsmAttributionBadgeAmoledPreview() {
    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
        OfflineOsmAttributionBadge(modifier = Modifier.padding(8.dp))
    }
}
