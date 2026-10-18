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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Resolves the localized display title for a [WorkoutDetailTabConfig] (`REQ-UI-408.1`).
 */
@Composable
fun resolveWorkoutDetailTabTitle(tab: WorkoutDetailTabConfig): String {
    return if (tab.customTitle.isNotBlank()) {
        tab.customTitle
    } else {
        stringResource(tab.preset.defaultTitleRes)
    }
}

/**
 * Interactive modal bottom sheet for configuring Workout Detail tabs (`REQ-UI-408.5` / `ATT-3920`).
 *
 * Supports:
 * - Adding custom tabs (`addCustomTab`)
 * - Renaming tabs (`renameTab`)
 * - Reordering tabs (`moveTab`)
 * - Deleting tabs (`deleteTab`, minimum 1 tab enforced)
 * - Toggling and reordering the 10 [WorkoutDetailSectionType] sections per tab (`toggleSectionInTab`, `moveSectionInTab`),
 *   keeping [WorkoutDetailSectionType.MAP] pinned at index `0` whenever enabled
 * - Resetting to the canonical 4 default tabs ([WorkoutDetailTabConfig.DEFAULT_TABS])
 *
 * Note: Hosted inside [AppModalBottomSheet] which provides its own outer vertical scroll (`scrollable = true`),
 * avoiding nested `verticalScroll` containers (Rule 23 Section 5.8).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutDetailTabsConfigSheet(
    tabs: List<WorkoutDetailTabConfig>,
    onTabsChange: (List<WorkoutDetailTabConfig>) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    val effectiveTabs = remember(tabs) { tabs.ifEmpty { WorkoutDetailTabConfig.DEFAULT_TABS } }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val safeIndex = selectedTabIndex.coerceIn(0, (effectiveTabs.size - 1).coerceAtLeast(0))
    val currentTab = effectiveTabs[safeIndex]
    val defaultTitleForCurrent = stringResource(currentTab.preset.defaultTitleRes)

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.workout_detail_tabs_config_title),
        icon = Icons.Default.Tune,
        actions = {
            AppDialogActions.Confirm(onConfirm = onDismiss)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.workout_detail_tabs_config_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. Tab Selector Pills + Add Tab Action
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                effectiveTabs.forEachIndexed { index, tab ->
                    val isSelected = index == safeIndex
                    val title = resolveWorkoutDetailTabTitle(tab)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = if (isSelected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedTabIndex = index }
                    ) {
                        Text(
                            text = "${index + 1}. $title",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        val updated = WorkoutDetailTabConfig.addCustomTab(effectiveTabs)
                        onTabsChange(updated)
                        selectedTabIndex = updated.lastIndex
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.workout_detail_tabs_add_tab),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // 2. Selected Tab Controls: Rename, Move Left/Up, Move Right/Down, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = currentTab.customTitle,
                    onValueChange = { newName ->
                        onTabsChange(
                            WorkoutDetailTabConfig.renameTab(effectiveTabs, safeIndex, newName)
                        )
                    },
                    label = { Text(stringResource(R.string.workout_detail_tabs_rename_tab)) },
                    placeholder = { Text(defaultTitleForCurrent) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val target = safeIndex - 1
                        onTabsChange(WorkoutDetailTabConfig.moveTab(effectiveTabs, safeIndex, target))
                        selectedTabIndex = target
                    },
                    enabled = safeIndex > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.action_move_up)
                    )
                }

                IconButton(
                    onClick = {
                        val target = safeIndex + 1
                        onTabsChange(WorkoutDetailTabConfig.moveTab(effectiveTabs, safeIndex, target))
                        selectedTabIndex = target
                    },
                    enabled = safeIndex < effectiveTabs.lastIndex,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.action_move_down)
                    )
                }

                IconButton(
                    onClick = {
                        val updated = WorkoutDetailTabConfig.deleteTab(effectiveTabs, safeIndex)
                        onTabsChange(updated)
                        selectedTabIndex = safeIndex.coerceAtMost(updated.lastIndex)
                    },
                    enabled = effectiveTabs.size > 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.workout_detail_tabs_delete_tab),
                        tint = if (effectiveTabs.size > 1) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 3. Section Assignment & Ordering for Selected Tab
            Text(
                text = stringResource(R.string.workout_detail_tabs_sections_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            val activeSections = currentTab.normalizedSections
            val inactiveSections = remember(activeSections) {
                WorkoutDetailSectionType.entries.filter { it !in activeSections }
            }

            if (WorkoutDetailSectionType.MAP in activeSections) {
                Text(
                    text = stringResource(R.string.workout_detail_tabs_map_pinned_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Active Sections (Reorderable, with MAP pinned at index 0)
            activeSections.forEachIndexed { secIdx, section ->
                val isMap = section == WorkoutDetailSectionType.MAP
                val minMovableIdx = if (WorkoutDetailSectionType.MAP in activeSections) 1 else 0
                val canMoveUp = !isMap && secIdx > minMovableIdx
                val canMoveDown = !isMap && secIdx < activeSections.lastIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = true,
                        enabled = activeSections.size > 1,
                        onCheckedChange = {
                            onTabsChange(
                                WorkoutDetailTabConfig.toggleSectionInTab(effectiveTabs, safeIndex, section)
                            )
                        }
                    )
                    Text(
                        text = stringResource(section.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = {
                            onTabsChange(
                                WorkoutDetailTabConfig.moveSectionInTab(
                                    effectiveTabs,
                                    safeIndex,
                                    secIdx,
                                    secIdx - 1
                                )
                            )
                        },
                        enabled = canMoveUp,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.action_move_up),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            onTabsChange(
                                WorkoutDetailTabConfig.moveSectionInTab(
                                    effectiveTabs,
                                    safeIndex,
                                    secIdx,
                                    secIdx + 1
                                )
                            )
                        },
                        enabled = canMoveDown,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.action_move_down),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Unassigned Sections
            inactiveSections.forEach { section ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onTabsChange(
                                WorkoutDetailTabConfig.toggleSectionInTab(effectiveTabs, safeIndex, section)
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = false,
                        onCheckedChange = {
                            onTabsChange(
                                WorkoutDetailTabConfig.toggleSectionInTab(effectiveTabs, safeIndex, section)
                            )
                        }
                    )
                    Text(
                        text = stringResource(section.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 4. Reset to Default Tabs Button
            OutlinedButton(
                onClick = {
                    selectedTabIndex = 0
                    onResetDefaults()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.workout_detail_tabs_reset_defaults))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview(name = "WorkoutDetailTabsConfigSheet - Light", showBackground = true)
@Preview(
    name = "WorkoutDetailTabsConfigSheet - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun WorkoutDetailTabsConfigSheetPreview() {
    ATrainingTrackerTheme {
        Surface {
            WorkoutDetailTabsConfigSheet(
                tabs = WorkoutDetailTabConfig.DEFAULT_TABS,
                onTabsChange = {},
                onResetDefaults = {},
                onDismiss = {}
            )
        }
    }
}
