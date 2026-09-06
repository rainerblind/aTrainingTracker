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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.components.EmptyStatePlaceholder

/**
 * The scrollable list of period summaries (weeks, months, years) or an empty state placeholder.
 *
 * @param scrollState The [LazyListState] governing the scroll position and fast scroll interactions.
 * @param periods The list of [PeriodSummary] domain models to render. If empty, an [EmptyStatePlaceholder] is presented.
 * @param isPlayServiceAvailable Flag indicating whether Google Play Services are available on the device for map rendering.
 * @param onHeaderClick Callback triggered when tapping a period card header to toggle expansion.
 * @param onMapClick Callback triggered when tapping the period overview map to enter the fullscreen map view.
 * @param onSportClick Callback triggered when tapping an individual sport breakdown row.
 * @param onLongestWorkoutClick Callback triggered when tapping the highlighted longest workout in a period.
 * @param modifier The layout [Modifier] applied to the root container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodList(
    scrollState: LazyListState,
    periods: List<PeriodSummary>,
    isPlayServiceAvailable: Boolean,
    onHeaderClick: (PeriodSummary) -> Unit,
    onMapClick: (PeriodSummary) -> Unit,
    onSportClick: (PeriodSummary, BSportType) -> Unit,
    onLongestWorkoutClick: (PeriodSummary, BSportType, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    if (periods.isEmpty()) {
        EmptyStatePlaceholder(
            modifier = modifier.padding(bottom = bottomPadding),
            icon = Icons.Default.DateRange,
            message = stringResource(R.string.no_periods_available),
            hint = stringResource(R.string.no_periods_available_hint)
        )
    } else {
        LazyColumn(
            state = scrollState,
            modifier = modifier,
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = bottomPadding + 16.dp,
                start = 8.dp,
                end = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = periods,
                key = { "${it.periodType.name}_${it.startTimestampS}" }
            ) { periodSummary ->
                PeriodSummaryCard(
                    summary = periodSummary,
                    isPlayServiceAvailable = isPlayServiceAvailable,
                    onHeaderClick = onHeaderClick,
                    onMapClick = { onMapClick(periodSummary) },
                    onSportClick = onSportClick,
                    onLongestWorkoutClick = onLongestWorkoutClick
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Empty Period List")
@Composable
private fun PeriodListEmptyPreview() {
    MaterialTheme {
        PeriodList(
            scrollState = rememberLazyListState(),
            periods = emptyList(),
            isPlayServiceAvailable = false,
            onHeaderClick = {},
            onMapClick = {},
            onSportClick = { _, _ -> },
            onLongestWorkoutClick = { _, _, _ -> }
        )
    }
}
