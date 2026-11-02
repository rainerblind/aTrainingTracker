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

package com.atrainingtracker.trainingtracker.ui.settings.autopause

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningToggleItem
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.LayoutConstants
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class AutoPauseTabItem(
    val titleRes: Int,
    val sportType: BSportType
)

/**
 * Dedicated tabbed full-screen settings cockpit for sport-specific Auto-Pause configuration
 * (REQ-SET-080, TST-SET-080, ATT-4836, ATT-4924).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPauseTabsScreen(
    viewModel: AutoPauseViewModel,
    onOpenDrawer: () -> Unit = {}
) {
    val tuningConfig by viewModel.tuningConfig.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val tabs = remember {
        listOf(
            AutoPauseTabItem(R.string.tuning_auto_pause_bike_title, BSportType.BIKE),
            AutoPauseTabItem(R.string.tuning_auto_pause_run_title, BSportType.RUN),
            AutoPauseTabItem(R.string.tuning_auto_pause_other_title, BSportType.UNKNOWN)
        )
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed top header surface (title row + global card + sport tabs)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(LayoutConstants.HEADER_TITLE_ROW_HEIGHT)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = stringResource(R.string.auto_pause_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        )
                        IconButton(
                            onClick = {
                                val currentSport = tabs[pagerState.currentPage].sportType
                                viewModel.resetToDefaults(currentSport)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.reset_to_defaults_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = stringResource(R.string.reset_to_defaults),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TuningToggleItem(
                                title = stringResource(R.string.tuning_auto_pause_start_in_auto_pause_title),
                                isChecked = tuningConfig.startInAutoPauseEnabled,
                                onCheckedChange = { viewModel.updateStartInAutoPauseEnabled(it) },
                                summary = stringResource(R.string.tuning_auto_pause_start_in_auto_pause_desc)
                            )
                        }
                    }

                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = { Text(text = stringResource(tab.titleRes)) }
                            )
                        }
                    }
                }
            }

            // Content Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val tab = tabs[page]
                val contentPadding = PaddingValues(16.dp)

                when (tab.sportType) {
                    BSportType.BIKE -> {
                        AutoPauseSportTabContent(
                            sportType = BSportType.BIKE,
                            enabled = tuningConfig.autoPauseBikeEnabled,
                            onEnabledChange = { viewModel.updateAutoPauseBikeEnabled(it) },
                            speedThreshold = tuningConfig.autoPauseBikeSpeedThreshold,
                            onSpeedThresholdChange = { viewModel.updateAutoPauseBikeSpeedThreshold(it) },
                            debounceSec = tuningConfig.autoPauseBikeDebounceSec,
                            onDebounceSecChange = { viewModel.updateAutoPauseBikeDebounceSec(it) },
                            resumeDebounceSec = tuningConfig.autoPauseBikeResumeDebounceSec,
                            onResumeDebounceSecChange = { viewModel.updateAutoPauseBikeResumeDebounceSec(it) },
                            minDebounce = TuningPreferencesDefaults.MIN_AUTO_PAUSE_BIKE_DEBOUNCE_SEC,
                            maxDebounce = TuningPreferencesDefaults.MAX_AUTO_PAUSE_BIKE_DEBOUNCE_SEC,
                            debounceSteps = 18,
                            defaultDebounce = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_DEBOUNCE_SEC,
                            defaultResumeDebounce = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC,
                            contentPadding = contentPadding
                        )
                    }
                    BSportType.RUN -> {
                        AutoPauseSportTabContent(
                            sportType = BSportType.RUN,
                            enabled = tuningConfig.autoPauseRunEnabled,
                            onEnabledChange = { viewModel.updateAutoPauseRunEnabled(it) },
                            speedThreshold = tuningConfig.autoPauseRunSpeedThreshold,
                            onSpeedThresholdChange = { viewModel.updateAutoPauseRunSpeedThreshold(it) },
                            debounceSec = tuningConfig.autoPauseRunDebounceSec,
                            onDebounceSecChange = { viewModel.updateAutoPauseRunDebounceSec(it) },
                            resumeDebounceSec = tuningConfig.autoPauseRunResumeDebounceSec,
                            onResumeDebounceSecChange = { viewModel.updateAutoPauseRunResumeDebounceSec(it) },
                            minDebounce = TuningPreferencesDefaults.MIN_AUTO_PAUSE_RUN_DEBOUNCE_SEC,
                            maxDebounce = TuningPreferencesDefaults.MAX_AUTO_PAUSE_RUN_DEBOUNCE_SEC,
                            debounceSteps = 18,
                            defaultDebounce = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_DEBOUNCE_SEC,
                            defaultResumeDebounce = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC,
                            contentPadding = contentPadding
                        )
                    }
                    else -> {
                        AutoPauseSportTabContent(
                            sportType = BSportType.UNKNOWN,
                            enabled = tuningConfig.autoPauseOtherEnabled,
                            onEnabledChange = { viewModel.updateAutoPauseOtherEnabled(it) },
                            speedThreshold = tuningConfig.autoPauseOtherSpeedThreshold,
                            onSpeedThresholdChange = { viewModel.updateAutoPauseOtherSpeedThreshold(it) },
                            debounceSec = tuningConfig.autoPauseOtherDebounceSec,
                            onDebounceSecChange = { viewModel.updateAutoPauseOtherDebounceSec(it) },
                            resumeDebounceSec = tuningConfig.autoPauseOtherResumeDebounceSec,
                            onResumeDebounceSecChange = { viewModel.updateAutoPauseOtherResumeDebounceSec(it) },
                            minDebounce = TuningPreferencesDefaults.MIN_AUTO_PAUSE_OTHER_DEBOUNCE_SEC,
                            maxDebounce = TuningPreferencesDefaults.MAX_AUTO_PAUSE_OTHER_DEBOUNCE_SEC,
                            debounceSteps = 58,
                            defaultDebounce = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_DEBOUNCE_SEC,
                            defaultResumeDebounce = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC,
                            contentPadding = contentPadding
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoPauseSportTabContent(
    sportType: BSportType,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    speedThreshold: Int,
    onSpeedThresholdChange: (Int) -> Unit,
    debounceSec: Int,
    onDebounceSecChange: (Int) -> Unit,
    resumeDebounceSec: Int,
    onResumeDebounceSecChange: (Int) -> Unit,
    minDebounce: Int,
    maxDebounce: Int,
    debounceSteps: Int,
    defaultDebounce: Int,
    defaultResumeDebounce: Int,
    contentPadding: PaddingValues
) {
    val speedUnit = stringResource(MyHelper.getShortUnitsId(SensorType.SPEED_mps))
    val isMetric = (TrainingApplication.getUnit() == MyUnits.METRIC)

    val defaultThreshold = when (sportType) {
        BSportType.BIKE -> if (isMetric) TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_METRIC else TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_IMPERIAL
        BSportType.RUN -> if (isMetric) TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_METRIC else TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_IMPERIAL
        else -> if (isMetric) TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_METRIC else TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_IMPERIAL
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.tuning_auto_pause_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TuningToggleItem(
                        title = stringResource(R.string.tuning_auto_pause_enable_title),
                        isChecked = enabled,
                        onCheckedChange = onEnabledChange
                    )

                    if (enabled) {
                        TuningSliderItem(
                            title = stringResource(R.string.tuning_auto_pause_speed_threshold_title),
                            valueText = stringResource(R.string.tuning_speed_value_format, speedThreshold, speedUnit),
                            helperText = stringResource(R.string.tuning_auto_pause_speed_threshold_desc),
                            defaultText = stringResource(
                                R.string.tuning_default_format,
                                stringResource(R.string.tuning_speed_value_format, defaultThreshold, speedUnit)
                            ),
                            value = speedThreshold.toFloat(),
                            onValueChange = { onSpeedThresholdChange(it.roundToInt()) },
                            valueRange = TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD.toFloat()..TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD.toFloat(),
                            steps = 3
                        )

                        TuningSliderItem(
                            title = stringResource(R.string.tuning_auto_pause_debounce_title),
                            valueText = stringResource(R.string.tuning_seconds_value_format, debounceSec),
                            helperText = stringResource(R.string.tuning_auto_pause_debounce_desc),
                            defaultText = stringResource(
                                R.string.tuning_default_format,
                                stringResource(R.string.tuning_seconds_value_format, defaultDebounce)
                            ),
                            value = debounceSec.toFloat(),
                            onValueChange = { onDebounceSecChange(it.roundToInt()) },
                            valueRange = minDebounce.toFloat()..maxDebounce.toFloat(),
                            steps = debounceSteps
                        )

                        TuningSliderItem(
                            title = stringResource(R.string.tuning_auto_pause_resume_debounce_title),
                            valueText = stringResource(R.string.tuning_seconds_value_format, resumeDebounceSec),
                            helperText = stringResource(R.string.tuning_auto_pause_resume_debounce_desc),
                            defaultText = stringResource(
                                R.string.tuning_default_format,
                                stringResource(R.string.tuning_seconds_value_format, defaultResumeDebounce)
                            ),
                            value = resumeDebounceSec.toFloat(),
                            onValueChange = { onResumeDebounceSecChange(it.roundToInt()) },
                            valueRange = minDebounce.toFloat()..maxDebounce.toFloat(),
                            steps = debounceSteps
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "AutoPauseTabsScreen Light")
@Composable
private fun PreviewAutoPauseTabsScreenLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface {
            AutoPauseSportTabContent(
                sportType = BSportType.BIKE,
                enabled = true,
                onEnabledChange = {},
                speedThreshold = 3,
                onSpeedThresholdChange = {},
                debounceSec = 5,
                onDebounceSecChange = {},
                resumeDebounceSec = 2,
                onResumeDebounceSecChange = {},
                minDebounce = 1,
                maxDebounce = 20,
                debounceSteps = 18,
                defaultDebounce = 5,
                defaultResumeDebounce = 2,
                contentPadding = PaddingValues(16.dp)
            )
        }
    }
}

@Preview(name = "AutoPauseTabsScreen Dark")
@Composable
private fun PreviewAutoPauseTabsScreenDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            AutoPauseSportTabContent(
                sportType = BSportType.BIKE,
                enabled = true,
                onEnabledChange = {},
                speedThreshold = 3,
                onSpeedThresholdChange = {},
                debounceSec = 5,
                onDebounceSecChange = {},
                resumeDebounceSec = 2,
                onResumeDebounceSecChange = {},
                minDebounce = 1,
                maxDebounce = 20,
                debounceSteps = 18,
                defaultDebounce = 5,
                defaultResumeDebounce = 2,
                contentPadding = PaddingValues(16.dp)
            )
        }
    }
}
