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

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.activities.ZoneProfileTabContent
import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.physiology.AcwrZone
import com.atrainingtracker.trainingtracker.physiology.PmcDailyPoint
import com.atrainingtracker.trainingtracker.physiology.PmcSummaryResult
import com.atrainingtracker.trainingtracker.physiology.TsbReadinessZone
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import com.atrainingtracker.trainingtracker.ui.components.DeleteConfirmationDialog
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.LayoutConstants
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Stateful entry composable for the unified **Profile & Zones** screen (`REQ-UI-391.5`, `REQ-UI-397.2`).
 */
@Composable
fun AthleteProfileScreen(
    viewModel: AthleteProfileViewModel,
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    AthleteProfileContent(
        uiState = uiState,
        onSelectPlotMetric = viewModel::selectPlotMetric,
        onSelectTimeRange = viewModel::selectTimeRange,
        onSelectHistoryFilter = viewModel::selectHistoryFilter,
        onAddEntry = viewModel::addEntry,
        onUpdateEntry = viewModel::updateEntry,
        onDeleteEntry = viewModel::deleteEntry,
        initialTab = initialTab,
        modifier = modifier
    )
}

/**
 * Stateless UI content for the unified **Profile & Zones** screen (`REQ-UI-391.5`, `REQ-UI-396.1`, `REQ-UI-397.2`, Rule 23, Rule 38, Rule 41).
 *
 * Hosts 4 tabs in a [PrimaryScrollableTabRow] + [HorizontalPager]:
 * - Page 0: Athlete Profile (`R.string.athlete_profile_tab_profile`)
 * - Page 1: Running Heart Rate Zones (`R.string.tab_hr_run`)
 * - Page 2: Cycling Heart Rate Zones (`R.string.tab_hr_bike`)
 * - Page 3: Cycling Power Zones (`R.string.tab_pwr_bike`)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AthleteProfileContent(
    uiState: AthleteProfileUiState,
    onSelectPlotMetric: (PlotMetricType) -> Unit,
    onSelectTimeRange: (MetricsTimeRange) -> Unit,
    onSelectHistoryFilter: (AthleteMetricType?) -> Unit,
    onAddEntry: (AthleteMetricType, Double, Long) -> Unit,
    onUpdateEntry: (Long, AthleteMetricType, Double, Long) -> Unit,
    onDeleteEntry: (Long, AthleteMetricType) -> Unit,
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var showAddDialogForMetric by remember { mutableStateOf<AthleteMetricType?>(null) }
    var editingEntry by remember { mutableStateOf<AthleteMetricEntry?>(null) }
    var deletingEntry by remember { mutableStateOf<AthleteMetricEntry?>(null) }
    var showZoneCalculatorDialog by remember { mutableStateOf(false) }
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val tabTitleResIds = remember {
        listOf(
            R.string.athlete_profile_tab_profile,
            R.string.tab_hr_run,
            R.string.tab_hr_bike,
            R.string.tab_pwr_bike
        )
    }
    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, tabTitleResIds.lastIndex),
        pageCount = { tabTitleResIds.size }
    )
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Standard App Header + Tab Row (Rule 23, REQ-UI-397.2)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(LayoutConstants.HEADER_TITLE_ROW_HEIGHT)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.athlete_profile_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (pagerState.currentPage == 0) {
                            IconButton(
                                onClick = {
                                    val defaultMetric = when (uiState.selectedPlotMetric) {
                                        PlotMetricType.WEIGHT -> AthleteMetricType.WEIGHT_KG
                                        PlotMetricType.HR_REST -> AthleteMetricType.HR_REST_BPM
                                        PlotMetricType.FTP, PlotMetricType.POWER_TO_WEIGHT -> AthleteMetricType.FTP_WATTS
                                        PlotMetricType.CP -> AthleteMetricType.CP_WATTS
                                        PlotMetricType.W_PRIME -> AthleteMetricType.W_PRIME_KJ
                                    }
                                    showAddDialogForMetric = defaultMetric
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.athlete_profile_add_entry),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { showZoneCalculatorDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = stringResource(R.string.zone_calc_button),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        divider = {}
                    ) {
                        tabTitleResIds.forEachIndexed { index, titleResId ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(stringResource(titleResId)) }
                            )
                        }
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                when (page) {
                    0 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp + navBarBottomPadding
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                // 2. Summary Cards Grid (3x2 including CP and W', REQ-UI-399.1)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val weightText = uiState.latestWeightEntry?.let {
                                "${AthleteMetricsPlotMath.formatWeightValue(it.value, uiState.isImperial)} ${AthleteMetricsPlotMath.weightUnitLabel(uiState.isImperial)}"
                            } ?: stringResource(R.string.athlete_weight_unconfigured)
                            val weightDate = uiState.latestWeightEntry?.let {
                                AthleteMetricsPlotMath.formatMediumDate(it.timestampMs)
                            } ?: ""

                            MetricSummaryCard(
                                title = stringResource(R.string.athlete_profile_metric_weight),
                                valueText = weightText,
                                dateText = weightDate,
                                isSelected = uiState.selectedPlotMetric == PlotMetricType.WEIGHT,
                                onCardClick = { onSelectPlotMetric(PlotMetricType.WEIGHT) },
                                onAddClick = { showAddDialogForMetric = AthleteMetricType.WEIGHT_KG },
                                modifier = Modifier.weight(1f)
                            )

                            val hrRestText = uiState.latestHrRestEntry?.let {
                                "${AthleteMetricsPlotMath.formatHrRestValue(it.value)} bpm"
                            } ?: stringResource(R.string.athlete_weight_unconfigured)
                            val hrRestDate = uiState.latestHrRestEntry?.let {
                                AthleteMetricsPlotMath.formatMediumDate(it.timestampMs)
                            } ?: ""

                            MetricSummaryCard(
                                title = stringResource(R.string.athlete_profile_metric_hr_rest),
                                valueText = hrRestText,
                                dateText = hrRestDate,
                                isSelected = uiState.selectedPlotMetric == PlotMetricType.HR_REST,
                                onCardClick = { onSelectPlotMetric(PlotMetricType.HR_REST) },
                                onAddClick = { showAddDialogForMetric = AthleteMetricType.HR_REST_BPM },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val ftpText = uiState.latestFtpEntry?.let {
                                "${AthleteMetricsPlotMath.formatFtpValue(it.value)} W"
                            } ?: stringResource(R.string.athlete_weight_unconfigured)
                            val ftpDate = uiState.latestFtpEntry?.let {
                                AthleteMetricsPlotMath.formatMediumDate(it.timestampMs)
                            } ?: ""

                            MetricSummaryCard(
                                title = stringResource(R.string.athlete_profile_metric_ftp),
                                valueText = ftpText,
                                dateText = ftpDate,
                                isSelected = uiState.selectedPlotMetric == PlotMetricType.FTP,
                                onCardClick = { onSelectPlotMetric(PlotMetricType.FTP) },
                                onAddClick = { showAddDialogForMetric = AthleteMetricType.FTP_WATTS },
                                modifier = Modifier.weight(1f)
                            )

                            val wPerKgText = uiState.currentPowerToWeight?.let {
                                "${AthleteMetricsPlotMath.formatPowerToWeightValue(it)} W/kg"
                            } ?: stringResource(R.string.athlete_weight_unconfigured)
                            val wPerKgDate = uiState.latestPowerToWeightTimestampMs?.let {
                                AthleteMetricsPlotMath.formatMediumDate(it)
                            } ?: ""

                            MetricSummaryCard(
                                title = stringResource(R.string.athlete_profile_metric_w_per_kg),
                                valueText = wPerKgText,
                                dateText = wPerKgDate,
                                isSelected = uiState.selectedPlotMetric == PlotMetricType.POWER_TO_WEIGHT,
                                onCardClick = { onSelectPlotMetric(PlotMetricType.POWER_TO_WEIGHT) },
                                onAddClick = null,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val effectiveCpEntry = uiState.latestCpEntry ?: uiState.latestFtpEntry
                            val cpText = effectiveCpEntry?.let {
                                "${AthleteMetricsPlotMath.formatCpValue(it.value)} W"
                            } ?: stringResource(R.string.athlete_weight_unconfigured)
                            val cpDate = uiState.latestCpEntry?.let {
                                AthleteMetricsPlotMath.formatMediumDate(it.timestampMs)
                            } ?: ""

                            MetricSummaryCard(
                                title = stringResource(R.string.athlete_profile_metric_cp),
                                valueText = cpText,
                                dateText = cpDate,
                                isSelected = uiState.selectedPlotMetric == PlotMetricType.CP,
                                onCardClick = { onSelectPlotMetric(PlotMetricType.CP) },
                                onAddClick = { showAddDialogForMetric = AthleteMetricType.CP_WATTS },
                                modifier = Modifier.weight(1f)
                            )

                            val wPrimeText = uiState.latestWPrimeEntry?.let {
                                "${AthleteMetricsPlotMath.formatWPrimeValue(it.value)} kJ"
                            } ?: "${AthleteMetricsPlotMath.formatWPrimeValue(SettingsDataStore.DEFAULT_W_PRIME_KJ.toDouble())} kJ"
                            val wPrimeDate = uiState.latestWPrimeEntry?.let {
                                AthleteMetricsPlotMath.formatMediumDate(it.timestampMs)
                            } ?: ""

                            MetricSummaryCard(
                                title = stringResource(R.string.athlete_profile_metric_w_prime),
                                valueText = wPrimeText,
                                dateText = wPrimeDate,
                                isSelected = uiState.selectedPlotMetric == PlotMetricType.W_PRIME,
                                onCardClick = { onSelectPlotMetric(PlotMetricType.W_PRIME) },
                                onAddClick = { showAddDialogForMetric = AthleteMetricType.W_PRIME_KJ },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3. Interactive Time-Series Chart Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.athlete_profile_chart_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Metric Selector Pills (Rule 38: compact non-wrapping single-line pills)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val metricOptions = listOf(
                                    PlotMetricType.WEIGHT to stringResource(R.string.athlete_profile_metric_weight),
                                    PlotMetricType.HR_REST to stringResource(R.string.athlete_profile_metric_hr_rest),
                                    PlotMetricType.FTP to stringResource(R.string.athlete_profile_metric_ftp),
                                    PlotMetricType.CP to stringResource(R.string.athlete_profile_metric_cp),
                                    PlotMetricType.W_PRIME to stringResource(R.string.athlete_profile_metric_w_prime),
                                    PlotMetricType.POWER_TO_WEIGHT to stringResource(R.string.athlete_profile_metric_w_per_kg)
                                )
                                metricOptions.forEach { (type, label) ->
                                    CompactSelectorPill(
                                        label = label,
                                        selected = uiState.selectedPlotMetric == type,
                                        onClick = { onSelectPlotMetric(type) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Time Range Selector Pills (Rule 38: compact non-wrapping single-line pills)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val rangeOptions = listOf(
                                    MetricsTimeRange.ONE_MONTH to stringResource(R.string.athlete_profile_range_1m),
                                    MetricsTimeRange.THREE_MONTHS to stringResource(R.string.athlete_profile_range_3m),
                                    MetricsTimeRange.SIX_MONTHS to stringResource(R.string.athlete_profile_range_6m),
                                    MetricsTimeRange.ONE_YEAR to stringResource(R.string.athlete_profile_range_1y),
                                    MetricsTimeRange.ALL to stringResource(R.string.athlete_profile_range_all)
                                )
                                rangeOptions.forEach { (range, label) ->
                                    CompactSelectorPill(
                                        label = label,
                                        selected = uiState.selectedTimeRange == range,
                                        onClick = { onSelectTimeRange(range) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Time-Series Canvas Plot
                            AthleteMetricTimeSeriesChart(
                                points = uiState.plotPoints,
                                metricType = uiState.selectedPlotMetric,
                                isImperial = uiState.isImperial,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                            )
                        }
                    }
                }

                // 3b. Performance Management Chart (PMC: CTL / ATL / TSB / ACWR / Ramp Rate, REQ-UI-401.3)
                item {
                    PerformanceManagementChartCard(
                        pmcSummary = uiState.pmcSummary,
                        pmcPlotPoints = uiState.pmcPlotPoints
                    )
                }

                // 4. History Log Header & Filter Row
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.athlete_profile_history_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val filterOptions: List<Pair<AthleteMetricType?, String>> = listOf(
                                null to stringResource(R.string.athlete_profile_filter_all),
                                AthleteMetricType.WEIGHT_KG to stringResource(R.string.athlete_profile_metric_weight),
                                AthleteMetricType.HR_REST_BPM to stringResource(R.string.athlete_profile_metric_hr_rest),
                                AthleteMetricType.FTP_WATTS to stringResource(R.string.athlete_profile_metric_ftp),
                                AthleteMetricType.CP_WATTS to stringResource(R.string.athlete_profile_metric_cp),
                                AthleteMetricType.W_PRIME_KJ to stringResource(R.string.athlete_profile_metric_w_prime)
                            )
                            filterOptions.forEach { (filterType, label) ->
                                CompactSelectorPill(
                                    label = label,
                                    selected = uiState.historyFilterMetric == filterType,
                                    onClick = { onSelectHistoryFilter(filterType) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 5. History Entries List
                if (uiState.filteredHistory.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.athlete_profile_empty_history),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = uiState.filteredHistory,
                        key = { it.id }
                    ) { entry ->
                        HistoryEntryRow(
                            entry = entry,
                            isImperial = uiState.isImperial,
                            onEditClick = { editingEntry = entry },
                            onDeleteClick = { deletingEntry = entry }
                        )
                    }
                }
                        }
                    }
                    1 -> {
                        ZoneProfileTabContent(
                            zoneType = ZoneType.HR_RUN,
                            showCalculatorDialog = showZoneCalculatorDialog && pagerState.currentPage == 1,
                            onDismissCalculatorDialog = { showZoneCalculatorDialog = false }
                        )
                    }
                    2 -> {
                        ZoneProfileTabContent(
                            zoneType = ZoneType.HR_BIKE,
                            showCalculatorDialog = showZoneCalculatorDialog && pagerState.currentPage == 2,
                            onDismissCalculatorDialog = { showZoneCalculatorDialog = false }
                        )
                    }
                    else -> {
                        ZoneProfileTabContent(
                            zoneType = ZoneType.PWR_BIKE,
                            showCalculatorDialog = showZoneCalculatorDialog && pagerState.currentPage == 3,
                            onDismissCalculatorDialog = { showZoneCalculatorDialog = false }
                        )
                    }
                }
            }
        }
    }

    // Add Entry Dialog
    showAddDialogForMetric?.let { initialMetric ->
        val defaultVal = when (initialMetric) {
            AthleteMetricType.WEIGHT_KG -> uiState.latestWeightEntry?.value
            AthleteMetricType.HR_REST_BPM -> uiState.latestHrRestEntry?.value
            AthleteMetricType.FTP_WATTS -> uiState.latestFtpEntry?.value
            AthleteMetricType.CP_WATTS -> uiState.latestCpEntry?.value ?: uiState.latestFtpEntry?.value
            AthleteMetricType.W_PRIME_KJ -> uiState.latestWPrimeEntry?.value ?: SettingsDataStore.DEFAULT_W_PRIME_KJ.toDouble()
        }
        AthleteMetricEntryDialog(
            isEditMode = false,
            initialMetricType = initialMetric,
            initialCanonicalValue = defaultVal,
            initialTimestampMs = System.currentTimeMillis(),
            isImperial = uiState.isImperial,
            onDismiss = { showAddDialogForMetric = null },
            onSave = { metricType, canonicalValue, timestampMs ->
                onAddEntry(metricType, canonicalValue, timestampMs)
                showAddDialogForMetric = null
            }
        )
    }

    // Edit Entry Dialog
    editingEntry?.let { entry ->
        AthleteMetricEntryDialog(
            isEditMode = true,
            initialMetricType = entry.metricType,
            initialCanonicalValue = entry.value,
            initialTimestampMs = entry.timestampMs,
            isImperial = uiState.isImperial,
            onDismiss = { editingEntry = null },
            onSave = { metricType, canonicalValue, timestampMs ->
                onUpdateEntry(entry.id, metricType, canonicalValue, timestampMs)
                editingEntry = null
            }
        )
    }

    // Delete Confirmation Dialog (Rule 23: Reused DeleteConfirmationDialog)
    deletingEntry?.let { entry ->
        val metricLabel = metricDisplayName(entry.metricType)
        val formattedVal = formatEntryValueWithUnit(entry, uiState.isImperial)
        val formattedDate = AthleteMetricsPlotMath.formatMediumDate(entry.timestampMs)

        DeleteConfirmationDialog(
            title = stringResource(R.string.athlete_profile_delete_title),
            message = stringResource(
                R.string.athlete_profile_delete_message,
                metricLabel,
                formattedVal,
                formattedDate
            ),
            onConfirm = {
                onDeleteEntry(entry.id, entry.metricType)
                deletingEntry = null
            },
            onDismiss = { deletingEntry = null }
        )
    }
}

@Composable
private fun MetricSummaryCard(
    title: String,
    valueText: String,
    dateText: String,
    isSelected: Boolean,
    onCardClick: () -> Unit,
    onAddClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = modifier.clickable(onClick = onCardClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onAddClick != null) {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.athlete_profile_add_entry),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateText.ifEmpty { " " },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact bordered selector pill preventing multi-line text wrapping (`REQ-UI-391.5` / Rule 38).
 */
@Composable
fun CompactSelectorPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 34.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) primary.copy(alpha = 0.18f) else Color.Transparent,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) primary else outline
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compose Canvas time-series chart rendering gridlines, Y-axis value labels, X-axis date labels,
 * connecting line path, and data point markers (`REQ-UI-391.4`, `REQ-UI-391.5`).
 */
@Composable
fun AthleteMetricTimeSeriesChart(
    points: List<MetricPlotPoint>,
    metricType: PlotMetricType,
    isImperial: Boolean,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.athlete_profile_empty_chart),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val leftPad = 48.dp.toPx()
        val rightPad = 14.dp.toPx()
        val topPad = 12.dp.toPx()
        val bottomPad = 24.dp.toPx()

        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val minValRaw = points.minOf { it.value }
        val maxValRaw = points.maxOf { it.value }
        val rawSpan = maxValRaw - minValRaw
        val pad = if (rawSpan < 1e-3) {
            when (metricType) {
                PlotMetricType.POWER_TO_WEIGHT -> 0.25
                PlotMetricType.WEIGHT -> 2.0
                PlotMetricType.HR_REST -> 3.0
                PlotMetricType.FTP, PlotMetricType.CP -> 10.0
                PlotMetricType.W_PRIME -> 2.0
            }
        } else {
            rawSpan * 0.15
        }
        val yMin = (minValRaw - pad).coerceAtLeast(0.0)
        val yMax = maxValRaw + pad
        val yRange = (yMax - yMin).coerceAtLeast(1e-3)

        val minTs = points.first().timestampMs
        val maxTs = points.last().timestampMs
        val tsSpan = (maxTs - minTs).coerceAtLeast(1L)

        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
        }

        // Draw 3 horizontal grid lines + Y-axis labels
        val gridSteps = 2
        for (i in 0..gridSteps) {
            val frac = i.toFloat() / gridSteps.toFloat()
            val y = topPad + plotH * (1f - frac)
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1.dp.toPx()
            )
            val v = yMin + frac * yRange
            val label = when (metricType) {
                PlotMetricType.WEIGHT, PlotMetricType.W_PRIME -> String.format(Locale.US, "%.1f", v)
                PlotMetricType.HR_REST, PlotMetricType.FTP, PlotMetricType.CP -> v.roundToInt().toString()
                PlotMetricType.POWER_TO_WEIGHT -> String.format(Locale.US, "%.2f", v)
            }
            drawContext.canvas.nativeCanvas.drawText(
                label,
                4.dp.toPx(),
                y + 4.dp.toPx(),
                textPaint
            )
        }

        // Map points to canvas coordinates
        val canvasPoints = points.mapIndexed { idx, pt ->
            val x = if (points.size == 1 || maxTs == minTs) {
                leftPad + plotW * 0.5f
            } else {
                leftPad + ((pt.timestampMs - minTs).toDouble() / tsSpan.toDouble()).toFloat() * plotW
            }
            val normY = ((pt.value - yMin) / yRange).toFloat().coerceIn(0f, 1f)
            val y = topPad + plotH * (1f - normY)
            Offset(x, y)
        }

        // Subtle gradient area fill + connecting line when >= 2 points
        if (canvasPoints.size >= 2) {
            val fillPath = Path().apply {
                moveTo(canvasPoints.first().x, topPad + plotH)
                canvasPoints.forEach { lineTo(it.x, it.y) }
                lineTo(canvasPoints.last().x, topPad + plotH)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                    startY = topPad,
                    endY = topPad + plotH
                )
            )

            val strokePath = Path().apply {
                moveTo(canvasPoints.first().x, canvasPoints.first().y)
                for (i in 1 until canvasPoints.size) {
                    lineTo(canvasPoints[i].x, canvasPoints[i].y)
                }
            }
            drawPath(
                path = strokePath,
                color = lineColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Data point markers
        canvasPoints.forEach { offset ->
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = offset
            )
        }

        // X-axis date ticks (start and end)
        if (points.size == 1 || minTs == maxTs) {
            val dateStr = AthleteMetricsPlotMath.formatShortDate(minTs)
            val w = textPaint.measureText(dateStr)
            drawContext.canvas.nativeCanvas.drawText(
                dateStr,
                (leftPad + plotW * 0.5f - w * 0.5f).coerceAtLeast(leftPad),
                size.height - 4.dp.toPx(),
                textPaint
            )
        } else {
            val startStr = AthleteMetricsPlotMath.formatShortDate(minTs)
            val endStr = AthleteMetricsPlotMath.formatShortDate(maxTs)
            val endW = textPaint.measureText(endStr)
            drawContext.canvas.nativeCanvas.drawText(
                startStr,
                leftPad,
                size.height - 4.dp.toPx(),
                textPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                endStr,
                (leftPad + plotW - endW).coerceAtLeast(leftPad),
                size.height - 4.dp.toPx(),
                textPaint
            )
        }
    }
}

@Composable
private fun HistoryEntryRow(
    entry: AthleteMetricEntry,
    isImperial: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatEntryValueWithUnit(entry, isImperial),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${metricDisplayName(entry.metricType)} • ${AthleteMetricsPlotMath.formatMediumDate(entry.timestampMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.athlete_weight_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.athlete_weight_clear),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthleteMetricEntryDialog(
    isEditMode: Boolean,
    initialMetricType: AthleteMetricType,
    initialCanonicalValue: Double?,
    initialTimestampMs: Long,
    isImperial: Boolean,
    onDismiss: () -> Unit,
    onSave: (AthleteMetricType, Double, Long) -> Unit
) {
    var selectedMetric by remember(initialMetricType) { mutableStateOf(initialMetricType) }
    var selectedTimestampMs by remember(initialTimestampMs) { mutableStateOf(initialTimestampMs) }
    var showDatePicker by remember { mutableStateOf(false) }

    val initialText = remember(selectedMetric, initialCanonicalValue, isImperial) {
        if (initialCanonicalValue == null || initialCanonicalValue <= 0.0) {
            ""
        } else {
            when (selectedMetric) {
                AthleteMetricType.WEIGHT_KG -> AthleteMetricsPlotMath.formatWeightValue(initialCanonicalValue, isImperial)
                AthleteMetricType.HR_REST_BPM -> AthleteMetricsPlotMath.formatHrRestValue(initialCanonicalValue)
                AthleteMetricType.FTP_WATTS -> AthleteMetricsPlotMath.formatFtpValue(initialCanonicalValue)
                AthleteMetricType.CP_WATTS -> AthleteMetricsPlotMath.formatCpValue(initialCanonicalValue)
                AthleteMetricType.W_PRIME_KJ -> AthleteMetricsPlotMath.formatWPrimeValue(initialCanonicalValue)
            }
        }
    }

    var textValue by remember(selectedMetric) { mutableStateOf(initialText) }
    var hasValidationError by remember(selectedMetric, textValue) { mutableStateOf(false) }

    // 2-Point CP & W' Estimator state (`REQ-UI-399.1`)
    var shortPowerInput by remember { mutableStateOf("400") }
    var shortSecInput by remember { mutableStateOf("180") }
    var longPowerInput by remember { mutableStateOf("310") }
    var longSecInput by remember { mutableStateOf("720") }

    val twoPointEstimate = remember(shortPowerInput, shortSecInput, longPowerInput, longSecInput) {
        val p1 = shortPowerInput.trim().replace(',', '.').toDoubleOrNull()
        val t1 = shortSecInput.trim().toIntOrNull()
        val p2 = longPowerInput.trim().replace(',', '.').toDoubleOrNull()
        val t2 = longSecInput.trim().toIntOrNull()
        if (p1 != null && t1 != null && p2 != null && t2 != null) {
            com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.estimateCpAndWPrime(p1, t1, p2, t2)
        } else {
            null
        }
    }

    val unitStr = when (selectedMetric) {
        AthleteMetricType.WEIGHT_KG -> AthleteMetricsPlotMath.weightUnitLabel(isImperial)
        AthleteMetricType.HR_REST_BPM -> "bpm"
        AthleteMetricType.FTP_WATTS, AthleteMetricType.CP_WATTS -> "W"
        AthleteMetricType.W_PRIME_KJ -> "kJ"
    }

    fun parseToCanonicalValue(input: String): Double? {
        val normalized = input.trim().replace(',', '.')
        val raw = normalized.toDoubleOrNull() ?: return null
        val canonical = if (selectedMetric == AthleteMetricType.WEIGHT_KG && isImperial) {
            raw / SettingsDataStore.LBS_PER_KG
        } else {
            raw
        }
        return canonical.takeIf { selectedMetric.isValidMetricValue(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isEditMode) R.string.athlete_profile_edit_entry else R.string.athlete_profile_add_entry
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isEditMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val metrics = listOf(
                            AthleteMetricType.WEIGHT_KG to stringResource(R.string.athlete_profile_metric_weight),
                            AthleteMetricType.HR_REST_BPM to stringResource(R.string.athlete_profile_metric_hr_rest),
                            AthleteMetricType.FTP_WATTS to stringResource(R.string.athlete_profile_metric_ftp),
                            AthleteMetricType.CP_WATTS to stringResource(R.string.athlete_profile_metric_cp),
                            AthleteMetricType.W_PRIME_KJ to stringResource(R.string.athlete_profile_metric_w_prime)
                        )
                        metrics.forEach { (mType, label) ->
                            CompactSelectorPill(
                                label = label,
                                selected = selectedMetric == mType,
                                onClick = { selectedMetric = mType },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        hasValidationError = false
                    },
                    label = {
                        Text(stringResource(R.string.athlete_profile_value_label, unitStr))
                    },
                    isError = hasValidationError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (selectedMetric == AthleteMetricType.WEIGHT_KG || selectedMetric == AthleteMetricType.W_PRIME_KJ) {
                            KeyboardType.Decimal
                        } else {
                            KeyboardType.Number
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedMetric == AthleteMetricType.CP_WATTS || selectedMetric == AthleteMetricType.W_PRIME_KJ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.athlete_profile_cp_estimator_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = shortPowerInput,
                                    onValueChange = { shortPowerInput = it },
                                    label = { Text(stringResource(R.string.athlete_profile_cp_estimator_short_power)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = shortSecInput,
                                    onValueChange = { shortSecInput = it },
                                    label = { Text(stringResource(R.string.athlete_profile_cp_estimator_short_sec)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = longPowerInput,
                                    onValueChange = { longPowerInput = it },
                                    label = { Text(stringResource(R.string.athlete_profile_cp_estimator_long_power)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = longSecInput,
                                    onValueChange = { longSecInput = it },
                                    label = { Text(stringResource(R.string.athlete_profile_cp_estimator_long_sec)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            twoPointEstimate?.let { est ->
                                OutlinedButton(
                                    onClick = {
                                        textValue = if (selectedMetric == AthleteMetricType.CP_WATTS) {
                                            est.cpWattsRounded.toString()
                                        } else {
                                            String.format(Locale.US, "%.1f", est.wPrimeKj)
                                        }
                                        hasValidationError = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.athlete_profile_cp_estimator_apply,
                                            est.cpWattsRounded,
                                            est.wPrimeKj
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = stringResource(R.string.athlete_profile_date_label),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(R.string.athlete_profile_date_label)}: ${AthleteMetricsPlotMath.formatMediumDate(selectedTimestampMs)}"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val canonical = parseToCanonicalValue(textValue)
                    if (canonical != null) {
                        onSave(selectedMetric, canonical, selectedTimestampMs)
                    } else {
                        hasValidationError = true
                    }
                }
            ) {
                Text(stringResource(R.string.athlete_weight_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.zone_calc_cancel_button))
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedTimestampMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedMs ->
                            selectedTimestampMs = pickedMs
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.athlete_weight_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.zone_calc_cancel_button))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun metricDisplayName(metricType: AthleteMetricType): String = when (metricType) {
    AthleteMetricType.WEIGHT_KG -> stringResource(R.string.athlete_profile_metric_weight)
    AthleteMetricType.HR_REST_BPM -> stringResource(R.string.athlete_profile_metric_hr_rest)
    AthleteMetricType.FTP_WATTS -> stringResource(R.string.athlete_profile_metric_ftp)
    AthleteMetricType.CP_WATTS -> stringResource(R.string.athlete_profile_metric_cp)
    AthleteMetricType.W_PRIME_KJ -> stringResource(R.string.athlete_profile_metric_w_prime)
}

private fun formatEntryValueWithUnit(entry: AthleteMetricEntry, isImperial: Boolean): String =
    when (entry.metricType) {
        AthleteMetricType.WEIGHT_KG ->
            "${AthleteMetricsPlotMath.formatWeightValue(entry.value, isImperial)} ${AthleteMetricsPlotMath.weightUnitLabel(isImperial)}"
        AthleteMetricType.HR_REST_BPM ->
            "${AthleteMetricsPlotMath.formatHrRestValue(entry.value)} bpm"
        AthleteMetricType.FTP_WATTS ->
            "${AthleteMetricsPlotMath.formatFtpValue(entry.value)} W"
        AthleteMetricType.CP_WATTS ->
            "${AthleteMetricsPlotMath.formatCpValue(entry.value)} W"
        AthleteMetricType.W_PRIME_KJ ->
            "${AthleteMetricsPlotMath.formatWPrimeValue(entry.value)} kJ"
    }

/**
 * Performance Management Chart (PMC) card displaying current Fitness (CTL), Fatigue (ATL),
 * Form / Race-Readiness (TSB), Workload Ratio (ACWR), 7d Ramp Rate, and the interactive
 * multi-curve time-series Canvas chart (`REQ-UI-401.3`, `ATT-3859`).
 */
@Composable
fun PerformanceManagementChartCard(
    pmcSummary: PmcSummaryResult,
    pmcPlotPoints: List<PmcDailyPoint>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.athlete_profile_pmc_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Row 1: CTL (Fitness), ATL (Fatigue), TSB (Form + Readiness Zone Badge)
            val tsbZoneText = when (pmcSummary.currentTsbZone) {
                TsbReadinessZone.FRESH_RACE_READY -> stringResource(R.string.athlete_profile_pmc_zone_fresh)
                TsbReadinessZone.NEUTRAL -> stringResource(R.string.athlete_profile_pmc_zone_neutral)
                TsbReadinessZone.OPTIMAL_OVERLOAD -> stringResource(R.string.athlete_profile_pmc_zone_optimal)
                TsbReadinessZone.HIGH_FATIGUE_RISK -> stringResource(R.string.athlete_profile_pmc_zone_fatigue)
            }
            val tsbColor = when (pmcSummary.currentTsbZone) {
                TsbReadinessZone.FRESH_RACE_READY -> TTColor.PmcFreshGreen
                TsbReadinessZone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                TsbReadinessZone.OPTIMAL_OVERLOAD -> TTColor.PmcOptimalBlue
                TsbReadinessZone.HIGH_FATIGUE_RISK -> MaterialTheme.colorScheme.error
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PmcStatBadge(
                    label = stringResource(R.string.athlete_profile_pmc_ctl_label),
                    valueText = String.format(Locale.US, "%.1f", pmcSummary.currentCtl),
                    badgeText = null,
                    accentColor = TTColor.PmcCtlBlue,
                    modifier = Modifier.weight(1f)
                )
                PmcStatBadge(
                    label = stringResource(R.string.athlete_profile_pmc_atl_label),
                    valueText = String.format(Locale.US, "%.1f", pmcSummary.currentAtl),
                    badgeText = null,
                    accentColor = TTColor.PmcAtlMagenta,
                    modifier = Modifier.weight(1f)
                )
                PmcStatBadge(
                    label = stringResource(R.string.athlete_profile_pmc_tsb_label),
                    valueText = String.format(Locale.US, "%+.1f", pmcSummary.currentTsb),
                    badgeText = tsbZoneText,
                    accentColor = tsbColor,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: ACWR (Workload Ratio) & 7d Ramp Rate
            val acwrZoneText = when (pmcSummary.currentAcwrZone) {
                AcwrZone.UNDER_TRAINING -> stringResource(R.string.athlete_profile_pmc_acwr_under)
                AcwrZone.SWEET_SPOT -> stringResource(R.string.athlete_profile_pmc_acwr_sweet_spot)
                AcwrZone.HIGH_LOAD_CAUTION -> stringResource(R.string.athlete_profile_pmc_acwr_caution)
                AcwrZone.DANGER_ZONE -> stringResource(R.string.athlete_profile_pmc_acwr_danger)
                null -> null
            }
            val acwrColor = when (pmcSummary.currentAcwrZone) {
                AcwrZone.SWEET_SPOT -> TTColor.PmcFreshGreen
                AcwrZone.HIGH_LOAD_CAUTION -> TTColor.PmcCautionOrange
                AcwrZone.DANGER_ZONE -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PmcStatBadge(
                    label = stringResource(R.string.athlete_profile_pmc_acwr_label),
                    valueText = pmcSummary.currentAcwr?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                    badgeText = acwrZoneText,
                    accentColor = acwrColor,
                    modifier = Modifier.weight(1f)
                )
                PmcStatBadge(
                    label = stringResource(R.string.athlete_profile_pmc_ramp_label),
                    valueText = stringResource(
                        R.string.athlete_profile_pmc_ramp_format,
                        pmcSummary.currentRampRate7d
                    ),
                    badgeText = if (pmcSummary.isAggressiveRampUp) {
                        stringResource(R.string.athlete_profile_pmc_ramp_warning)
                    } else {
                        null
                    },
                    accentColor = if (pmcSummary.isAggressiveRampUp) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            PerformanceManagementChartCanvas(
                points = pmcPlotPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            )
        }
    }
}

@Composable
private fun PmcStatBadge(
    label: String,
    valueText: String,
    badgeText: String?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (!badgeText.isNullOrEmpty()) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Multi-curve Canvas chart plotting Fitness (CTL — blue area/line), Fatigue (ATL — magenta line),
 * and Form / Race-Readiness (TSB — amber/green line) over time (`REQ-UI-401.3`).
 */
@Composable
fun PerformanceManagementChartCanvas(
    points: List<PmcDailyPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.athlete_profile_pmc_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val ctlColor = TTColor.PmcCtlBlue
    val atlColor = TTColor.PmcAtlMagenta
    val tsbColor = TTColor.PmcTsbAmber
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val leftPad = 44.dp.toPx()
        val rightPad = 14.dp.toPx()
        val topPad = 12.dp.toPx()
        val bottomPad = 24.dp.toPx()

        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val minValRaw = points.minOf { minOf(it.ctl, it.atl, it.tsb) }
        val maxValRaw = points.maxOf { maxOf(it.ctl, it.atl, it.tsb) }
        val rawSpan = (maxValRaw - minValRaw).coerceAtLeast(10.0)
        val pad = rawSpan * 0.12
        val yMin = minValRaw - pad
        val yMax = maxValRaw + pad
        val yRange = (yMax - yMin).coerceAtLeast(1.0)

        val minTs = points.first().dayStartTimestampMs
        val maxTs = points.last().dayStartTimestampMs
        val tsSpan = (maxTs - minTs).coerceAtLeast(1L)

        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
        }

        // Draw horizontal grid lines + Y-axis labels
        val gridSteps = 2
        for (i in 0..gridSteps) {
            val frac = i.toFloat() / gridSteps.toFloat()
            val y = topPad + plotH * (1f - frac)
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1.dp.toPx()
            )
            val v = yMin + frac * yRange
            drawContext.canvas.nativeCanvas.drawText(
                v.roundToInt().toString(),
                4.dp.toPx(),
                y + 4.dp.toPx(),
                textPaint
            )
        }

        // Zero reference line when 0 is within [yMin, yMax]
        if (yMin < 0.0 && yMax > 0.0) {
            val zeroNorm = ((0.0 - yMin) / yRange).toFloat().coerceIn(0f, 1f)
            val zeroY = topPad + plotH * (1f - zeroNorm)
            drawLine(
                color = gridColor.copy(alpha = 0.75f),
                start = Offset(leftPad, zeroY),
                end = Offset(leftPad + plotW, zeroY),
                strokeWidth = 1.2.dp.toPx()
            )
        }

        fun mapSeries(selector: (PmcDailyPoint) -> Double): List<Offset> =
            points.map { pt ->
                val x = if (points.size == 1 || maxTs == minTs) {
                    leftPad + plotW * 0.5f
                } else {
                    leftPad + ((pt.dayStartTimestampMs - minTs).toDouble() / tsSpan.toDouble()).toFloat() * plotW
                }
                val normY = ((selector(pt) - yMin) / yRange).toFloat().coerceIn(0f, 1f)
                val y = topPad + plotH * (1f - normY)
                Offset(x, y)
            }

        val ctlOffsets = mapSeries { it.ctl }
        val atlOffsets = mapSeries { it.atl }
        val tsbOffsets = mapSeries { it.tsb }

        if (ctlOffsets.size >= 2) {
            val fillPath = Path().apply {
                moveTo(ctlOffsets.first().x, topPad + plotH)
                ctlOffsets.forEach { lineTo(it.x, it.y) }
                lineTo(ctlOffsets.last().x, topPad + plotH)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(ctlColor.copy(alpha = 0.22f), Color.Transparent),
                    startY = topPad,
                    endY = topPad + plotH
                )
            )

            fun drawCurve(offsets: List<Offset>, color: Color, strokeDp: Float) {
                val path = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    for (i in 1 until offsets.size) {
                        lineTo(offsets[i].x, offsets[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeDp.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            drawCurve(tsbOffsets, tsbColor, 1.8f)
            drawCurve(atlOffsets, atlColor, 2.0f)
            drawCurve(ctlOffsets, ctlColor, 2.5f)
        } else {
            drawCircle(color = tsbColor, radius = 3.5.dp.toPx(), center = tsbOffsets.first())
            drawCircle(color = atlColor, radius = 3.5.dp.toPx(), center = atlOffsets.first())
            drawCircle(color = ctlColor, radius = 4.dp.toPx(), center = ctlOffsets.first())
        }

        // X-axis date ticks (start and end)
        val startStr = AthleteMetricsPlotMath.formatShortDate(minTs)
        drawContext.canvas.nativeCanvas.drawText(
            startStr,
            leftPad,
            size.height - 4.dp.toPx(),
            textPaint
        )
        if (points.size > 1 && maxTs > minTs) {
            val endStr = AthleteMetricsPlotMath.formatShortDate(maxTs)
            val endW = textPaint.measureText(endStr)
            drawContext.canvas.nativeCanvas.drawText(
                endStr,
                (leftPad + plotW - endW).coerceAtLeast(leftPad),
                size.height - 4.dp.toPx(),
                textPaint
            )
        }
    }
}

@Preview(showBackground = true, name = "AthleteProfile - Populated Light")
@Composable
private fun AthleteProfilePopulatedLightPreview() {
    val now = 1_760_000_000_000L
    val day = 86_400_000L
    val sampleEntries = listOf(
        AthleteMetricEntry(3L, now, AthleteMetricType.WEIGHT_KG, 71.8),
        AthleteMetricEntry(2L, now - 7 * day, AthleteMetricType.FTP_WATTS, 285.0),
        AthleteMetricEntry(1L, now - 14 * day, AthleteMetricType.HR_REST_BPM, 48.0)
    )
    ATrainingTrackerTheme(darkTheme = false) {
        AthleteProfileContent(
            uiState = AthleteProfileUiState(
                latestWeightEntry = sampleEntries[0],
                latestFtpEntry = sampleEntries[1],
                latestHrRestEntry = sampleEntries[2],
                currentPowerToWeight = 285.0 / 71.8,
                latestPowerToWeightTimestampMs = now,
                selectedPlotMetric = PlotMetricType.WEIGHT,
                selectedTimeRange = MetricsTimeRange.THREE_MONTHS,
                plotPoints = listOf(
                    MetricPlotPoint(now - 30 * day, 73.2),
                    MetricPlotPoint(now - 14 * day, 72.4),
                    MetricPlotPoint(now, 71.8)
                ),
                filteredHistory = sampleEntries
            ),
            onSelectPlotMetric = {},
            onSelectTimeRange = {},
            onSelectHistoryFilter = {},
            onAddEntry = { _, _, _ -> },
            onUpdateEntry = { _, _, _, _ -> },
            onDeleteEntry = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "AthleteProfile - Dark AMOLED Empty")
@Composable
private fun AthleteProfileEmptyDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
        AthleteProfileContent(
            uiState = AthleteProfileUiState(),
            onSelectPlotMetric = {},
            onSelectTimeRange = {},
            onSelectHistoryFilter = {},
            onAddEntry = { _, _, _ -> },
            onUpdateEntry = { _, _, _, _ -> },
            onDeleteEntry = { _, _ -> }
        )
    }
}

