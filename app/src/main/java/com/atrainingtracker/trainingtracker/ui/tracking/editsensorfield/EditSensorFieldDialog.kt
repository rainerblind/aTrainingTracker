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

package com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorCategory
import com.atrainingtracker.banalservice.sensor.SensorSubCategory
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.settings.AthleteWeightDialog
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.safePainterResource
import com.atrainingtracker.trainingtracker.ui.tracking.getDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Pure search filter helper for matching [SensorType] items in [SensorTypeSelectorSection]
 * across localized full name, localized short name, localized unit, and enum identifier (ATT-3852, REQ-UI-395.2).
 */
object SensorTypeSearchFilter {
    /**
     * Filters [items] case-insensitively by [query]. Returns all [items] when [query] is blank.
     */
    fun filterByQuery(
        items: List<SensorType>,
        query: String,
        fullNameResolver: (SensorType) -> String,
        shortNameResolver: (SensorType) -> String,
        unitResolver: (SensorType) -> String = { "" }
    ): List<SensorType> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return items
        return items.filter { sensorType ->
            fullNameResolver(sensorType).contains(trimmed, ignoreCase = true) ||
                shortNameResolver(sensorType).contains(trimmed, ignoreCase = true) ||
                unitResolver(sensorType).contains(trimmed, ignoreCase = true) ||
                sensorType.name.contains(trimmed, ignoreCase = true)
        }
    }
}

/**
 * Modernized bottom sheet dialog for configuring a tracking grid sensor field (REQ-UI-149, REQ-UI-395, TST-UI-102, TST-UI-358).
 * Replaces the flat 40-item SensorType dropdown with a categorized and searchable selector ([SensorTypeSelectorSection]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSensorFieldDialog(
    title: String,
    viewModel: EditSensorFieldViewModel,
    onDismissRequest: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    AppModalBottomSheet(
        onDismissRequest = onDismissRequest,
        title = title,
        icon = Icons.Default.Edit,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    viewModel.saveChanges()
                    onDismissRequest()
                },
                onCancel = onDismissRequest,
                saveText = stringResource(R.string.save)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Categorized & Searchable Sensor Type Selector (ATT-3852 / REQ-UI-395) ---
            val availableSensorTypes = uiState.availableSensorTypesForCurrentActivityType
                .ifEmpty { SensorType.entries }
            SensorTypeSelectorSection(
                availableSensorTypes = availableSensorTypes,
                selectedSensorType = uiState.selectedSensorType,
                sportType = uiState.sportType,
                onSensorTypeSelected = { newSensorType ->
                    viewModel.onSensorTypeChanged(newSensorType)
                }
            )

            Spacer(Modifier.height(12.dp))

            // --- Source Device Spinner ---
            val deviceList = uiState.availableDevices
            if (deviceList.size == 1 && deviceList[0].first == -1L) {
                // when there is only the 'best' sensor available, there is no choice.
            } else {
                Spinner(
                    label = stringResource(R.string.source),
                    items = deviceList.map { it.second },
                    selectedItem = deviceList.find { it.first == uiState.selectedDeviceId }?.second ?: stringResource(R.string.bestSensor),
                    onItemSelected = { index ->
                        viewModel.onDeviceChanged(deviceList[index].first, deviceList[index].second)
                    }
                )

                Spacer(Modifier.height(12.dp))
            }

            // --- View Size Spinner ---
            Spinner(
                label = stringResource(R.string.text_size),
                items = uiState.availableViewSizes.map { it.getDisplayName(context) },
                selectedItem = uiState.selectedViewSize.getDisplayName(context),
                onItemSelected = { index ->
                    viewModel.onViewSizeChanged(uiState.availableViewSizes[index])
                }
            )

            Spacer(Modifier.height(16.dp))

            // --- Display Elements Selection (REQ-TRK-046, ATT-4841, ATT-1294) ---
            if (uiState.isBarSupported || uiState.isGraphSupported) {
                Text(
                    text = stringResource(R.string.sensor_field_display_elements_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.showValue,
                        onClick = { viewModel.onShowValueChanged(!uiState.showValue) },
                        label = { Text(stringResource(R.string.sensor_field_show_value)) }
                    )
                    if (uiState.isBarSupported) {
                        FilterChip(
                            selected = uiState.showBar,
                            onClick = { viewModel.onShowBarChanged(!uiState.showBar) },
                            label = { Text(stringResource(R.string.sensor_field_show_bar)) }
                        )
                    }
                    if (uiState.isGraphSupported) {
                        FilterChip(
                            selected = uiState.showGraph,
                            onClick = { viewModel.onShowGraphChanged(!uiState.showGraph) },
                            label = { Text(stringResource(R.string.sensor_field_show_graph)) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // --- Sparkline Time Horizon Selection (REQ-TRK-059, ATT-1294) ---
            if (uiState.isGraphSupported && uiState.showGraph) {
                Text(
                    text = stringResource(R.string.sensor_field_graph_horizon),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                val horizons = listOf(
                    60 to R.string.graph_horizon_1m,
                    120 to R.string.graph_horizon_2m,
                    300 to R.string.graph_horizon_5m,
                    600 to R.string.graph_horizon_10m,
                    1200 to R.string.graph_horizon_20m,
                    3600 to R.string.graph_horizon_1h,
                    0 to R.string.graph_horizon_full
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    horizons.forEach { (durationSeconds, labelResId) ->
                        FilterChip(
                            selected = (uiState.graphHorizonSeconds == durationSeconds),
                            onClick = { viewModel.onGraphHorizonChanged(durationSeconds) },
                            label = { Text(stringResource(labelResId)) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // --- Relative Width (REQ-UI-437, ATT-4861, ATT-5077, ATT-5192) ---
            if (uiState.isMultiFieldRow) {
                Text(
                    text = stringResource(R.string.sensor_field_relative_weight),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                val weights = listOf(1f to "1x", 2f to "2x", 3f to "3x")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    weights.forEach { (w, label) ->
                        FilterChip(
                            selected = (uiState.colWeight == w),
                            onClick = { viewModel.onColWeightChanged(w) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // --- Optionally: Configure Filter Button ---
            if (uiState.selectedSensorType?.filteringPossible == true) {
                OutlinedButton(
                    onClick = { viewModel.onConfigureFilterClicked() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(R.string.filter)}: ${uiState.filterSummary}",
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Athlete Weight Configuration for Power-to-Weight (ATT-1289, REQ-TRK-019)
            if (uiState.selectedSensorType == SensorType.POWER_TO_WEIGHT) {
                val dataStore = remember { SettingsDataStore.getInstance(context) }
                val athleteWeight by dataStore.athleteWeightKgFlow.collectAsState(initial = 0f)
                val coroutineScope = rememberCoroutineScope()
                var showWeightDialog by remember { mutableStateOf(false) }
                val isImperial = TrainingApplication.getUnit() == MyUnits.IMPERIAL
                val weightStr = if (athleteWeight > 0f) {
                    val displayWeight = if (isImperial) athleteWeight * SettingsDataStore.LBS_PER_KG else athleteWeight
                    val unitStr = if (isImperial) "lbs" else "kg"
                    String.format(java.util.Locale.US, "%.1f %s", displayWeight, unitStr)
                } else {
                    stringResource(R.string.athlete_weight_unconfigured)
                }

                OutlinedButton(
                    onClick = { showWeightDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${stringResource(R.string.athlete_weight_title)}: $weightStr")
                }
                Spacer(Modifier.height(8.dp))

                if (showWeightDialog) {
                    AthleteWeightDialog(
                        initialWeightKg = athleteWeight,
                        onDismiss = { showWeightDialog = false },
                        onSaveWeightKg = { newWeight ->
                            coroutineScope.launch(Dispatchers.IO) {
                                if (newWeight > 0f) {
                                    com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepository
                                        .getInstance(context)
                                        .addEntry(
                                            metricType = com.atrainingtracker.trainingtracker.database.AthleteMetricType.WEIGHT_KG,
                                            value = newWeight.toDouble()
                                        )
                                } else {
                                    dataStore.setAthleteWeightKg(newWeight)
                                }
                            }
                        }
                    )
                }
            }

            // Intensity Factor & TSS FTP Baseline Display (ATT-1290, ATT-1291, REQ-TRK-020, REQ-TRK-021)
            if (uiState.selectedSensorType == SensorType.INTENSITY_FACTOR || uiState.selectedSensorType == SensorType.TSS) {
                val dataStore = remember { SettingsDataStore.getInstance(context) }
                val storedFtp by dataStore.ftpFlow.collectAsState(initial = null)
                val ftpStr = if (storedFtp != null && storedFtp!! > 0) {
                    "$storedFtp W"
                } else {
                    stringResource(R.string.athlete_weight_unconfigured)
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FTP: $ftpStr",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // HRV Guidance & Aerobic Threshold Information (ATT-3542, REQ-TRK-061.5)
            val isHrvMetric = uiState.selectedSensorType in listOf(
                SensorType.HRV_DFA_A1,
                SensorType.HRV_RMSSD,
                SensorType.RR_INTERVAL,
                SensorType.HRV_ARTIFACT_RATE
            )
            if (isHrvMetric) {
                var showHrvInfo by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showHrvInfo = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.hrv_info_title))
                }
                Spacer(Modifier.height(8.dp))

                if (showHrvInfo) {
                    com.atrainingtracker.trainingtracker.ui.tracking.HrvInfoBottomSheet(
                        onDismissRequest = { showHrvInfo = false }
                    )
                }
            }
        }
    }

    // when necessary, also show the ConfigFilterDialog.
    if (uiState.showFilterConfigDialog) {
        ConfigureFilterDialog(
            viewModel = viewModel,
            onDismissRequest = { viewModel.onFilterConfigDismissed() },
            onSave = {
                viewModel.onSaveFilterConfig()
            }
        )
    }
}

/**
 * Categorized and searchable selector for [SensorType] values inside [EditSensorFieldDialog] (ATT-3852, ATT-6726, REQ-UI-395, REQ-TRK-070.4).
 * Presents a 2-level accordion hierarchy (direct core metrics top + sub-category cards) and visual Bar/Graph capability badges.
 * Respects `AppModalBottomSheet` scroll constraints (`docs/design_guidelines.md` Section 3 & Section 5.8) by rendering
 * collapsible category accordions inside a non-scrollable [Column].
 */
@Composable
fun SensorTypeSelectorSection(
    availableSensorTypes: List<SensorType>,
    selectedSensorType: SensorType?,
    onSensorTypeSelected: (SensorType) -> Unit,
    modifier: Modifier = Modifier,
    sportType: BSportType = BSportType.UNKNOWN,
    initiallyExpanded: Boolean = false
) {
    var isPickerExpanded by remember { mutableStateOf(initiallyExpanded) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedCategories by remember(selectedSensorType) {
        mutableStateOf(setOfNotNull(selectedSensorType?.category))
    }
    var expandedSubCategories by remember(selectedSensorType) {
        mutableStateOf(setOfNotNull(selectedSensorType?.subCategory))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Summary trigger card showing currently selected SensorType, category, short name, and unit
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isPickerExpanded = !isPickerExpanded },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                width = if (isPickerExpanded) 1.5.dp else 1.dp,
                color = if (isPickerExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedSensorType != null) {
                        Icon(
                            painter = safePainterResource(
                                id = selectedSensorType.iconResId,
                                fallback = Icons.Default.Sensors
                            ),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val categoryTitle = selectedSensorType?.category?.let { stringResource(it.titleResId) }
                        val subCategoryTitle = selectedSensorType?.subCategory?.let { stringResource(it.titleResId) }
                        Text(
                            text = when {
                                categoryTitle != null && subCategoryTitle != null -> "${stringResource(R.string.sensor)} • $categoryTitle • $subCategoryTitle"
                                categoryTitle != null -> "${stringResource(R.string.sensor)} • $categoryTitle"
                                else -> stringResource(R.string.sensor)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = selectedSensorType?.let { stringResource(it.fullNameId) } ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedSensorType != null) {
                            val shortName = stringResource(selectedSensorType.shortNameId)
                            val unitStr = resolveSensorUnitText(selectedSensorType)
                            val subtitle = if (unitStr.isNotEmpty()) "$shortName ($unitStr)" else shortName
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (isPickerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = isPickerExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.sensor_type_search_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                )

                val fullNames = availableSensorTypes.associateWith { stringResource(it.fullNameId) }
                val shortNames = availableSensorTypes.associateWith { stringResource(it.shortNameId) }
                val unitTexts = availableSensorTypes.associateWith { resolveSensorUnitText(it) }

                val filteredSensors = SensorTypeSearchFilter.filterByQuery(
                    items = availableSensorTypes,
                    query = searchQuery,
                    fullNameResolver = { fullNames[it] ?: "" },
                    shortNameResolver = { shortNames[it] ?: "" },
                    unitResolver = { unitTexts[it] ?: "" }
                )

                if (filteredSensors.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.sensor_type_no_matches),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    val isSearching = searchQuery.isNotBlank()
                    val groupedByCategory = SensorCategory.entries.mapNotNull { category ->
                        val itemsInCategory = filteredSensors.filter { it.category == category }
                        if (itemsInCategory.isEmpty()) null else category to itemsInCategory
                    }

                    groupedByCategory.forEach { (category, categoryItems) ->
                        val isCategoryExpanded = isSearching || expandedCategories.contains(category)
                        val containsSelected = categoryItems.any { it == selectedSensorType }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (containsSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Category Accordion Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedCategories = if (expandedCategories.contains(category)) {
                                                expandedCategories - category
                                            } else {
                                                expandedCategories + category
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = safePainterResource(
                                                id = category.iconResId,
                                                fallback = Icons.Default.Sensors
                                            ),
                                            contentDescription = null,
                                            tint = if (containsSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "${stringResource(category.titleResId)} (${categoryItems.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (containsSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (containsSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isCategoryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isCategoryExpanded) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        val directItems = categoryItems.filter { it.subCategory == null }
                                        val subCategoryGroups = categoryItems.filter { it.subCategory != null }
                                            .groupBy { it.subCategory!! }

                                        // 1. Direct core metrics at category top
                                        directItems.forEach { sensorType ->
                                            SensorTypeRow(
                                                sensorType = sensorType,
                                                isSelected = sensorType == selectedSensorType,
                                                fullName = fullNames[sensorType].orEmpty(),
                                                shortName = shortNames[sensorType].orEmpty(),
                                                unitStr = unitTexts[sensorType].orEmpty(),
                                                sportType = sportType,
                                                onSelected = {
                                                    onSensorTypeSelected(sensorType)
                                                    isPickerExpanded = false
                                                    searchQuery = ""
                                                }
                                            )
                                        }

                                        // 2. Sub-categories as collapsible cards
                                        subCategoryGroups.forEach { (subCategory, subItems) ->
                                            val isSubExpanded = isSearching || expandedSubCategories.contains(subCategory)
                                            val containsSubSelected = subItems.any { it == selectedSensorType }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainer,
                                                border = BorderStroke(
                                                    width = 0.5.dp,
                                                    color = if (containsSubSelected) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                                    } else {
                                                        MaterialTheme.colorScheme.outlineVariant
                                                    }
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                expandedSubCategories = if (expandedSubCategories.contains(subCategory)) {
                                                                    expandedSubCategories - subCategory
                                                                } else {
                                                                    expandedSubCategories + subCategory
                                                                }
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "${stringResource(subCategory.titleResId)} (${subItems.size})",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = if (containsSubSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                            color = if (containsSubSelected) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurface
                                                            },
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Icon(
                                                            imageVector = if (isSubExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }

                                                    if (isSubExpanded) {
                                                        HorizontalDivider(
                                                            thickness = 0.5.dp,
                                                            color = MaterialTheme.colorScheme.outlineVariant
                                                        )
                                                        Column(modifier = Modifier.fillMaxWidth()) {
                                                            subItems.forEach { sensorType ->
                                                                SensorTypeRow(
                                                                    sensorType = sensorType,
                                                                    isSelected = sensorType == selectedSensorType,
                                                                    fullName = fullNames[sensorType].orEmpty(),
                                                                    shortName = shortNames[sensorType].orEmpty(),
                                                                    unitStr = unitTexts[sensorType].orEmpty(),
                                                                    sportType = sportType,
                                                                    onSelected = {
                                                                        onSensorTypeSelected(sensorType)
                                                                        isPickerExpanded = false
                                                                        searchQuery = ""
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorTypeRow(
    sensorType: SensorType,
    isSelected: Boolean,
    fullName: String,
    shortName: String,
    unitStr: String,
    sportType: BSportType,
    onSelected: () -> Unit
) {
    val isBar = TrackingViewsRepository.isBarSupported(sensorType, sportType)
    val isGraph = TrackingViewsRepository.isGraphSupported(sensorType, sportType)

    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelected)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fullName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = shortName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isBar) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = stringResource(R.string.sensor_capability_bar),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                if (isGraph) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = stringResource(R.string.sensor_capability_graph),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                if (unitStr.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = unitStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun resolveSensorUnitText(sensorType: SensorType): String {
    val unitId = try {
        MyHelper.getShortUnitsId(sensorType)
    } catch (_: Exception) {
        sensorType.unitId
    }
    if (unitId == 0 || unitId == R.string.units_none) return ""
    return stringResource(unitId).trim()
}

// Helper composable for a dropdown spinner
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Spinner(
    label: String,
    items: List<String>,
    selectedItem: String,
    onItemSelected: (index: Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            value = selectedItem,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEachIndexed { index, text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onItemSelected(index)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Preview(name = "SensorTypeSelectorSection - Light", showBackground = true)
@Preview(
    name = "SensorTypeSelectorSection - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun SensorTypeSelectorSectionPreview() {
    ATrainingTrackerTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                SensorTypeSelectorSection(
                    availableSensorTypes = SensorType.entries,
                    selectedSensorType = SensorType.POWER_TO_WEIGHT,
                    onSensorTypeSelected = {},
                    initiallyExpanded = true
                )
            }
        }
    }
}