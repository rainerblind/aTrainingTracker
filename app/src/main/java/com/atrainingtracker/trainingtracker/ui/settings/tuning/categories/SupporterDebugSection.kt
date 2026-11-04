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

package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.monetization.GarageLeague
import com.atrainingtracker.trainingtracker.monetization.GarageNudgeEvaluator
import com.atrainingtracker.trainingtracker.monetization.GarageNudgeEvent
import com.atrainingtracker.trainingtracker.monetization.GarageStats
import com.atrainingtracker.trainingtracker.monetization.ProFeatureManager
import com.atrainingtracker.trainingtracker.monetization.SupporterTier
import com.atrainingtracker.trainingtracker.monetization.SupporterTierOverride
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.ui.dialogs.FirstJubileeActionHelper
import com.atrainingtracker.trainingtracker.ui.dialogs.FirstJubileeCelebrationDialog
import com.atrainingtracker.trainingtracker.ui.settings.supporter.GarageUpgradeNudgeDialog
import kotlinx.coroutines.launch

/**
 * Diagnostic Supporter Tier Override UI strictly compiled and exposed in BuildConfig.DEBUG (REQ-SET-087.2).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupporterDebugSection() {
    if (!BuildConfig.DEBUG) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember { SettingsDataStore.getInstance(context) }
    val currentOverride by settingsDataStore.supporterTierOverride.collectAsState(initial = SupporterTierOverride.AUTO)
    var showPreviewIconPickerDialog by remember { mutableStateOf(false) }
    var showPreviewJubileeDialog by remember { mutableStateOf(false) }
    var showPreviewGoldGarageDialog by remember { mutableStateOf(false) }
    var showPreviewDiamondGarageDialog by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.debug_supporter_override_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.debug_supporter_override_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SupporterTierOverride.values().forEach { override ->
                    val isSelected = currentOverride == override
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            scope.launch {
                                settingsDataStore.setSupporterTierOverride(override)
                            }
                        },
                        label = {
                            Text(
                                text = override.name.removePrefix("FORCE_"),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showPreviewIconPickerDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.supporter_quick_picker_title))
            }

            // Jubilee Celebration Dialog 1-Tap Preview (ATT-2308 / REQ-REV-005)
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.debug_jubilee_preview_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.debug_jubilee_preview_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(10.dp))

            FilledTonalButton(
                onClick = { showPreviewJubileeDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(R.string.debug_jubilee_preview_button))
            }

            // N+1 Garage Detector 1-Tap Preview & Flag Reset (ATT-5299 / REQ-SET-088.6)
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.debug_garage_nudge_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.debug_garage_nudge_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { showPreviewGoldGarageDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(text = stringResource(R.string.debug_garage_preview_gold), fontSize = 11.sp)
                }
                FilledTonalButton(
                    onClick = { showPreviewDiamondGarageDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Diamond,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(text = stringResource(R.string.debug_garage_preview_diamond), fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        settingsDataStore.resetGarageNudgeFlags()
                        Toast.makeText(
                            context,
                            context.getString(R.string.debug_garage_flags_reset_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(R.string.debug_garage_reset_flags))
            }
        }
    }

    if (showPreviewJubileeDialog) {
        val proFeatureManager = remember { ProFeatureManager(context) }
        val isProActive by proFeatureManager.isProActive.collectAsState()

        FirstJubileeCelebrationDialog(
            count = 100,
            sportName = stringResource(R.string.sport_types),
            isProActive = isProActive,
            onRequestInAppReview = {
                showPreviewJubileeDialog = false
                FirstJubileeActionHelper.launchInAppReviewOrStore(context)
            },
            onSendFeedbackEmail = {
                showPreviewJubileeDialog = false
                FirstJubileeActionHelper.launchFeedbackEmail(context)
            },
            onNavigateToSupporter = {
                showPreviewJubileeDialog = false
            },
            onDismiss = {
                showPreviewJubileeDialog = false
            }
        )
    }

    if (showPreviewGoldGarageDialog) {
        val dummyGoldEvent = GarageNudgeEvent(
            league = GarageLeague.GOLD,
            stats = GarageStats(bikeCount = 3, shoeCount = 2, sensorCount = 5, powerMeterCount = 1),
            recommendedPlanId = GarageNudgeEvaluator.PLAN_GOLD,
            currentTier = SupporterTier.SILVER
        )
        GarageUpgradeNudgeDialog(
            event = dummyGoldEvent,
            onUpgrade = { showPreviewGoldGarageDialog = false },
            onDismiss = { showPreviewGoldGarageDialog = false }
        )
    }

    if (showPreviewDiamondGarageDialog) {
        val dummyDiamondEvent = GarageNudgeEvent(
            league = GarageLeague.DIAMOND,
            stats = GarageStats(bikeCount = 4, shoeCount = 3, sensorCount = 8, powerMeterCount = 2),
            recommendedPlanId = GarageNudgeEvaluator.PLAN_DIAMOND,
            currentTier = SupporterTier.GOLD
        )
        GarageUpgradeNudgeDialog(
            event = dummyDiamondEvent,
            onUpgrade = { showPreviewDiamondGarageDialog = false },
            onDismiss = { showPreviewDiamondGarageDialog = false }
        )
    }

    if (showPreviewIconPickerDialog) {
        com.atrainingtracker.trainingtracker.ui.branding.SupporterQuickIconPickerDialog(
            onDismiss = { showPreviewIconPickerDialog = false }
        )
    }
}
