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

package com.atrainingtracker.trainingtracker.ui.equipment

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.SimpleSensorInfo
import com.atrainingtracker.trainingtracker.ui.components.EmptyStatePlaceholder
import com.atrainingtracker.trainingtracker.ui.components.FastScrollableBox

@VisibleForTesting
internal val STICKY_COLUMN_WIDTH = 184.dp
@VisibleForTesting
internal val SENSOR_COLUMN_WIDTH = 88.dp
@VisibleForTesting
internal val ROW_HEIGHT = 56.dp
@VisibleForTesting
internal val SECTION_HEADER_HEIGHT = 36.dp
@VisibleForTesting
internal val HEADER_ROW_HEIGHT = 60.dp

/**
 * Screen providing a centralized, interactive equipment-to-sensor mapping matrix with checkboxes
 * for bikes and shoes, partitioned into sport-specific tables (REQ-UI-256, TST-UI-230, ATT-2126, ATT-2382).
 *
 * Features:
 * - Table 1 (Bikes): Displays bikes and bike-compatible/shared sensors with independent horizontal scroll.
 * - Table 2 (Shoes): Displays shoes and run-compatible/shared sensors with independent horizontal scroll.
 * - Sticky left column showing equipment icon, name, and retirement status during horizontal scroll.
 * - Independent horizontal scroll states ensuring scrolling bikes never affects shoes.
 * - Responsive 1-tap Material 3 Checkbox persistence.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EquipmentSensorMatrixScreen(
    bikes: List<EquipmentItem>,
    shoes: List<EquipmentItem>,
    bikeSensors: List<SimpleSensorInfo> = emptyList(),
    shoeSensors: List<SimpleSensorInfo> = emptyList(),
    sensors: List<SimpleSensorInfo> = emptyList(),
    onToggleLink: (equipmentId: Long, sensorId: Long, isLinked: Boolean) -> Unit,
    appBarOffsetPx: Int,
    headerHeightPx: Float,
    modifier: Modifier = Modifier,
    scrollState: LazyListState = rememberLazyListState()
) {
    val effectiveBikeSensors = if (bikeSensors.isNotEmpty()) {
        bikeSensors
    } else {
        EquipmentSensorOrdering.sortSensors(
            sensors.filter {
                DevicesDatabaseManager.isBikeSensor(it.deviceType) || DevicesDatabaseManager.isSharedSensor(it.deviceType)
            },
            BSportType.BIKE
        )
    }

    val effectiveShoeSensors = if (shoeSensors.isNotEmpty()) {
        shoeSensors
    } else {
        EquipmentSensorOrdering.sortSensors(
            sensors.filter {
                DevicesDatabaseManager.isRunSensor(it.deviceType) || DevicesDatabaseManager.isSharedSensor(it.deviceType)
            },
            BSportType.RUN
        )
    }

    val density = LocalDensity.current
    val topPadding = with(density) { (headerHeightPx + appBarOffsetPx).toDp() }

    if (effectiveBikeSensors.isEmpty() && effectiveShoeSensors.isEmpty() && sensors.isEmpty()) {
        EmptyStatePlaceholder(
            modifier = modifier.padding(top = topPadding + 16.dp),
            iconRes = R.drawable.ic_equipment_bike,
            message = stringResource(R.string.equipment_matrix_no_sensors)
        )
        return
    }

    if (bikes.isEmpty() && shoes.isEmpty()) {
        EmptyStatePlaceholder(
            modifier = modifier.padding(top = topPadding + 16.dp),
            iconRes = R.drawable.ic_equipment_bike,
            message = stringResource(R.string.equipment_matrix_no_equipment)
        )
        return
    }

    val bottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    val bikeScrollState = rememberScrollState()
    val shoeScrollState = rememberScrollState()

    FastScrollableBox(
        state = scrollState,
        modifier = modifier.fillMaxSize(),
        topPadding = topPadding,
        bottomPadding = bottomPadding
    ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topPadding + 8.dp,
                bottom = bottomPadding + 16.dp,
                start = 0.dp,
                end = 0.dp
            )
        ) {
            // BIKES SECTION
            if (bikes.isNotEmpty()) {
                item(key = "section_header_bikes") {
                    MatrixSectionHeader(
                        title = stringResource(R.string.equipment_type_bike),
                        count = bikes.size,
                        iconRes = R.drawable.ic_equipment_bike
                    )
                }

                stickyHeader(key = "table_header_bikes") {
                    MatrixTableHeaderRow(
                        title = stringResource(R.string.Equipment),
                        sensors = effectiveBikeSensors,
                        horizontalScrollState = bikeScrollState,
                        emptySensorsMessage = stringResource(R.string.equipment_matrix_no_bike_sensors)
                    )
                }

                items(bikes, key = { "bike_${it.id}" }) { bike ->
                    MatrixEquipmentRow(
                        item = bike,
                        isBike = true,
                        sensors = effectiveBikeSensors,
                        horizontalScrollState = bikeScrollState,
                        onToggleLink = onToggleLink
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.5.dp
                    )
                }
            }

            // SHOES SECTION
            if (shoes.isNotEmpty()) {
                item(key = "section_header_shoes") {
                    MatrixSectionHeader(
                        title = stringResource(R.string.equipment_type_shoe),
                        count = shoes.size,
                        iconRes = R.drawable.ic_equipment_shoe
                    )
                }

                stickyHeader(key = "table_header_shoes") {
                    MatrixTableHeaderRow(
                        title = stringResource(R.string.Equipment),
                        sensors = effectiveShoeSensors,
                        horizontalScrollState = shoeScrollState,
                        emptySensorsMessage = stringResource(R.string.equipment_matrix_no_shoe_sensors)
                    )
                }

                items(shoes, key = { "shoe_${it.id}" }) { shoe ->
                    MatrixEquipmentRow(
                        item = shoe,
                        isBike = false,
                        sensors = effectiveShoeSensors,
                        horizontalScrollState = shoeScrollState,
                        onToggleLink = onToggleLink
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixTableHeaderRow(
    title: String,
    sensors: List<SimpleSensorInfo>,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    emptySensorsMessage: String? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_ROW_HEIGHT),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top-left corner cell (sticky)
            Box(
                modifier = Modifier
                    .width(STICKY_COLUMN_WIDTH)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Vertical separator
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )

            if (sensors.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = emptySensorsMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                // Horizontally scrollable sensor name headers
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(horizontalScrollState),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sensors.forEach { sensor ->
                        Box(
                            modifier = Modifier
                                .width(SENSOR_COLUMN_WIDTH)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sensor.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                            )
                            // Subtle vertical guide aligned to column edge
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .align(Alignment.CenterEnd)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixSectionHeader(
    title: String,
    count: Int,
    iconRes: Int
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SECTION_HEADER_HEIGHT),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sticky section title and icon aligned with equipment column
            Row(
                modifier = Modifier
                    .width(STICKY_COLUMN_WIDTH)
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$title ($count)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Continuous vertical divider matching header and row dividers
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )

            // Surface container covering the sensor column area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    }
}

@Composable
private fun MatrixEquipmentRow(
    item: EquipmentItem,
    isBike: Boolean,
    sensors: List<SimpleSensorInfo>,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    onToggleLink: (equipmentId: Long, sensorId: Long, isLinked: Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(
                if (item.isRetired) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sticky Equipment Info Column
        Row(
            modifier = Modifier
                .width(STICKY_COLUMN_WIDTH)
                .fillMaxHeight()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(
                    id = if (isBike) R.drawable.ic_equipment_bike else R.drawable.ic_equipment_shoe
                ),
                contentDescription = null,
                tint = if (item.isRetired) MaterialTheme.colorScheme.outline
                       else if (isBike) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (item.isRetired) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onSurface
                )
                if (item.isRetired) {
                    Text(
                        text = stringResource(R.string.equipment_retired),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1
                    )
                }
            }
        }

        // Vertical separator line
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )

        // Horizontally scrollable row cells
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(horizontalScrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sensors.forEach { sensor ->
                val isLinked = item.linkedDeviceIds.contains(sensor.id)
                Box(
                    modifier = Modifier
                        .width(SENSOR_COLUMN_WIDTH)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Checkbox(
                        checked = isLinked,
                        onCheckedChange = { checked ->
                            onToggleLink(item.id, sensor.id, checked)
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    // Subtle vertical column guide on the right
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .align(Alignment.CenterEnd)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    )
                }
            }
        }
    }
}
