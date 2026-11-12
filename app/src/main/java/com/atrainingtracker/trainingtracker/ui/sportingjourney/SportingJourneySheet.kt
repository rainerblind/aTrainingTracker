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

package com.atrainingtracker.trainingtracker.ui.sportingjourney

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.monetization.ProFeatureManager
import com.atrainingtracker.trainingtracker.monetization.SupporterTier
import com.atrainingtracker.trainingtracker.ui.WorkoutNavigationEvents
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Full-height Material 3 modal bottom sheet hosting "The Trophy Vault — My Sporting Journey" (REQ-SPT-001..004).
 *
 * Provides retrospective discovery across 4 pillars:
 * 1. Hero Totals & Planetary Scale (Earth Equator %, Mt. Everest multiplier).
 * 2. SQLite Hidden Gems (crank turns, heartbeats, mechanical work in kWh, circadian DNA).
 * 3. Signature Milestones (interactive drill-down to workout details).
 * 4. The Achievement Room (auto-unlocked secret trophies).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportingJourneySheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToWorkout: ((Long) -> Unit)? = null,
    viewModel: SportingJourneyViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val proManager = remember(context) { ProFeatureManager.getInstance(context.applicationContext) }
    val effectiveTier by proManager.effectiveTier.collectAsState()

    val supporterEmblemRes = when (effectiveTier) {
        SupporterTier.PIONEER -> R.drawable.ic_supporter_emblem_pioneer
        SupporterTier.BRONZE -> R.drawable.ic_supporter_emblem_bronze
        SupporterTier.SILVER -> R.drawable.ic_supporter_emblem_silver
        SupporterTier.GOLD -> R.drawable.ic_supporter_emblem_gold
        SupporterTier.DIAMOND -> R.drawable.ic_supporter_emblem_diamond
        SupporterTier.FREE -> null
    }

    val handleWorkoutClick: (Long) -> Unit = { wId ->
        onDismissRequest()
        if (onNavigateToWorkout != null) {
            onNavigateToWorkout(wId)
        } else {
            WorkoutNavigationEvents.triggerNavigateToWorkout(wId)
        }
    }

    AppModalBottomSheet(
        title = stringResource(R.string.sporting_journey_title),
        onDismissRequest = onDismissRequest,
        icon = if (supporterEmblemRes == null) Icons.Default.EmojiEvents else null,
        iconPainter = supporterEmblemRes?.let { painterResource(it) },
        iconTint = if (supporterEmblemRes != null) androidx.compose.ui.graphics.Color.Unspecified else TTColor.VaultAmber,
        actions = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.Cancel),
                    textAlign = TextAlign.Center
                )
            }
        }
    ) {
        // Subtitle & Context
        val subtitle = when (val h = uiState.horizon) {
            is SportingHorizon.AllTime -> {
                if (uiState.firstWorkoutDateFormatted != null) {
                    stringResource(
                        R.string.sporting_journey_subtitle_all_time,
                        uiState.firstWorkoutDateFormatted ?: "",
                        uiState.daysSinceFirstWorkout
                    )
                } else {
                    stringResource(R.string.sporting_journey_horizon_all_time)
                }
            }
            is SportingHorizon.Year -> {
                stringResource(R.string.sporting_journey_subtitle_wrapped, h.year)
            }
        }

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Filter Controls
        SportingJourneyFilters(
            horizon = uiState.horizon,
            sportFilter = uiState.sportFilter,
            availableYears = uiState.availableYears,
            onSelectHorizon = { viewModel.setHorizon(it) },
            onSelectSport = { viewModel.setSportFilter(it) }
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TTColor.VaultAmber)
            }
        } else if (uiState.heroTotals.totalWorkouts == 0) {
            Text(
                text = stringResource(R.string.sporting_journey_no_workouts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            )
        } else {
            // Pillar 1: Hero Totals Card
            HeroTotalsCard(totals = uiState.heroTotals)

            // Pillar 2: Hidden Gems Section
            HiddenGemsSection(gems = uiState.hiddenGems)

            // Pillar 3: Milestones Section
            if (uiState.milestones.isNotEmpty()) {
                MilestonesSection(
                    milestones = uiState.milestones,
                    onMilestoneClick = handleWorkoutClick
                )
            }

            // Pillar 4: The Achievement Room
            if (uiState.achievements.isNotEmpty()) {
                AchievementRoomSection(
                    achievements = uiState.achievements,
                    onWorkoutClick = handleWorkoutClick
                )
            }

            // Share Card Action
            AthletePassportShareCard(
                data = uiState,
                tier = effectiveTier,
                onShareClick = {
                    SportingJourneyShareHelper.sharePassport(context, uiState, effectiveTier)
                }
            )
        }
    }
}
