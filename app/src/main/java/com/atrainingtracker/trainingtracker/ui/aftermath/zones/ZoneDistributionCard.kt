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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

/**
 * Generic reusable card displaying a zone vertical column histogram and time-in-zone breakdown,
 * with an interactive toggle to switch to a fine-grained telemetry frequency histogram when available.
 *
 * The bottom [SingleChoiceSegmentedButtonRow] spans `fillMaxWidth().height(28.dp)` with `icon = {}`,
 * `PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, and single-line `labelSmall` text
 * (`maxLines = 1`, `softWrap = false`, `TextOverflow.Ellipsis`) so all 2 or 3 mode labels render on a
 * single line across all 9 supported locales without mid-word wrapping (REQ-UI-386 / Rule 38).
 *
 * @param distribution Zone distribution data containing entries and optional histogram.
 * @param iconRes Vector drawable resource for the card header icon.
 * @param title Localized title text displayed in the header row.
 * @param unit Unit string for histogram bin readouts (e.g. "W", "bpm", "%").
 * @param modifier Modifier to be applied to the card container.
 * @param displayMode Hoisted display mode; when null, the card uses internal [rememberSaveable] state.
 * @param onDisplayModeChange Callback when athlete toggles display mode; if null, updates internal state.
 * @param showModeSelector Whether to render the bottom [SingleChoiceSegmentedButtonRow] mode switcher.
 *   Defaults to `true` (Workout Details); set to `false` in Workout Summary cards to keep the summary compact (REQ-UI-388).
 * @param defaultDisplayMode Default mode when neither hoisted nor saved state is present (defaults to [ZoneCardDisplayMode.COLUMNS]).
 */
@Composable
fun ZoneDistributionCard(
    distribution: ZoneDistributionData,
    @DrawableRes iconRes: Int,
    title: String,
    unit: String,
    modifier: Modifier = Modifier,
    displayMode: ZoneCardDisplayMode? = null,
    onDisplayModeChange: ((ZoneCardDisplayMode) -> Unit)? = null,
    showModeSelector: Boolean = true,
    defaultDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS
) {
    var internalDisplayMode by rememberSaveable { mutableStateOf(defaultDisplayMode) }
    val effectiveDisplayMode = (displayMode ?: internalDisplayMode)
        .resolveWithHistogramFallback(distribution.histogram != null)
    val setDisplayMode: (ZoneCardDisplayMode) -> Unit = { newMode ->
        if (onDisplayModeChange != null) {
            onDisplayModeChange(newMode)
        } else {
            internalDisplayMode = newMode
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Metric Icon + Localized Title + Total Active Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = ZoneDistributionChartMath.formatZoneDuration(distribution.totalActiveTimeSec),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Body: Stacked Horizontal Bar, Vertical Column Chart, or Fine-Grained Telemetry Frequency Histogram
            when (effectiveDisplayMode) {
                ZoneCardDisplayMode.STACKED_BAR -> {
                    ZoneDistributionStackedBar(
                        distribution = distribution,
                        modifier = Modifier.fillMaxWidth(),
                        showLegend = false
                    )
                }
                ZoneCardDisplayMode.COLUMNS, ZoneCardDisplayMode.FIVE_ZONES -> {
                    ZoneDistributionColumnChart(
                        distribution = distribution,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ZoneCardDisplayMode.HISTOGRAM -> {
                    TelemetryHistogramChart(
                        histogram = distribution.histogram,
                        unit = unit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Bottom Mode Switcher: Centered below chart for ample breathing room (ATT-1959 / ATT-3390 / ATT-3771 / ATT-3773)
            val hasHistogram = distribution.histogram != null
            val buttonCount = if (hasHistogram) 3 else 2

            if (showModeSelector && distribution.entries.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    ) {
                        val isStackedBar = effectiveDisplayMode == ZoneCardDisplayMode.STACKED_BAR
                        SegmentedButton(
                            selected = isStackedBar,
                            onClick = { setDisplayMode(ZoneCardDisplayMode.STACKED_BAR) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = buttonCount),
                            icon = {},
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.zone_mode_stacked_bar),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isStackedBar) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val isColumns = effectiveDisplayMode == ZoneCardDisplayMode.COLUMNS || effectiveDisplayMode == ZoneCardDisplayMode.FIVE_ZONES
                        SegmentedButton(
                            selected = isColumns,
                            onClick = { setDisplayMode(ZoneCardDisplayMode.COLUMNS) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = buttonCount),
                            icon = {},
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.zone_mode_columns),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isColumns) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (hasHistogram) {
                            val isHistogram = effectiveDisplayMode == ZoneCardDisplayMode.HISTOGRAM
                            SegmentedButton(
                                selected = isHistogram,
                                onClick = { setDisplayMode(ZoneCardDisplayMode.HISTOGRAM) },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = buttonCount),
                                icon = {},
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.zone_mode_histogram),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isHistogram) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
