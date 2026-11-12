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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.monetization.SupporterTier
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Filter chips row for selecting Time Horizon and Sport Filter (REQ-SPT-002).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportingJourneyFilters(
    horizon: SportingHorizon,
    sportFilter: SportingSportFilter,
    availableYears: List<Int>,
    onSelectHorizon: (SportingHorizon) -> Unit,
    onSelectSport: (SportingSportFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Horizon chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = horizon is SportingHorizon.AllTime,
                onClick = { onSelectHorizon(SportingHorizon.AllTime) },
                label = { Text(text = stringResource(R.string.sporting_journey_horizon_all_time)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TTColor.VaultCardBackground,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = horizon is SportingHorizon.AllTime,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = TTColor.VaultAmber
                )
            )

            availableYears.forEach { yr ->
                val isSelected = (horizon as? SportingHorizon.Year)?.year == yr
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectHorizon(SportingHorizon.Year(yr)) },
                    label = { Text(text = yr.toString()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TTColor.VaultCardBackground,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = TTColor.VaultAmber
                    )
                )
            }
        }

        // Sport filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = sportFilter == SportingSportFilter.ALL,
                onClick = { onSelectSport(SportingSportFilter.ALL) },
                label = { Text(text = stringResource(R.string.sporting_journey_sport_all)) }
            )
            FilterChip(
                selected = sportFilter == SportingSportFilter.CYCLING,
                onClick = { onSelectSport(SportingSportFilter.CYCLING) },
                label = { Text(text = stringResource(R.string.sporting_journey_sport_cycling)) }
            )
            FilterChip(
                selected = sportFilter == SportingSportFilter.RUNNING,
                onClick = { onSelectSport(SportingSportFilter.RUNNING) },
                label = { Text(text = stringResource(R.string.sporting_journey_sport_running)) }
            )
        }
    }
}

/**
 * Hero Totals Card displaying total distance, Equator percentage, and secondary metrics (REQ-SPT-002, REQ-SPT-005).
 */
@Composable
fun HeroTotalsCard(
    totals: SportingHeroTotals,
    modifier: Modifier = Modifier
) {
    val isImperial = remember {
        try {
            TrainingApplication.getUnit() == MyUnits.IMPERIAL
        } catch (_: Exception) {
            false
        }
    }

    val distanceFormatted = if (isImperial) {
        val distMiles = (totals.totalDistanceKm * 1000.0) / BANALService.METER_PER_MILE
        "${String.format(Locale.getDefault(), "%,.1f", distMiles)} mi"
    } else {
        "${String.format(Locale.getDefault(), "%,.1f", totals.totalDistanceKm)} km"
    }

    val elevationFormatted = if (isImperial) {
        val elevFeet = (totals.totalElevationM / BANALService.METER_PER_FOOT).roundToLong()
        "${String.format(Locale.getDefault(), "%,d", elevFeet)} ft"
    } else {
        "${String.format(Locale.getDefault(), "%,d", totals.totalElevationM)} m"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TTColor.VaultCardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(TTColor.VaultHeroStart, TTColor.VaultHeroEnd)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = distanceFormatted,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.sporting_journey_hero_distance_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TTColor.VaultCardBackground,
                    border = BorderStroke(1.dp, TTColor.VaultCardBorder)
                ) {
                    Text(
                        text = stringResource(R.string.sporting_journey_hero_equator_badge, totals.equatorPercentage),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TTColor.VaultAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 2x2 Grid of secondary totals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecondaryStatBox(
                    title = String.format(Locale.getDefault(), "%,d", totals.totalWorkouts),
                    subtitle = stringResource(R.string.sporting_journey_hero_workouts_label),
                    modifier = Modifier.weight(1f)
                )
                SecondaryStatBox(
                    title = "${String.format(Locale.getDefault(), "%,.0f", totals.totalActiveHours)} h",
                    subtitle = stringResource(R.string.sporting_journey_hero_days_sub, totals.nonstopDays),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecondaryStatBox(
                    title = elevationFormatted,
                    subtitle = stringResource(R.string.sporting_journey_hero_everest_sub, totals.everestMultiplier),
                    modifier = Modifier.weight(1f)
                )
                SecondaryStatBox(
                    title = totals.favoriteRoutesCount.toString(),
                    subtitle = stringResource(R.string.sporting_journey_hero_routes_label),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SecondaryStatBox(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Section displaying the 4 Hidden Gems discovered from offline telemetry (REQ-SPT-003, REQ-SPT-005, Pillar 2).
 */
@Composable
fun HiddenGemsSection(
    gems: SportingHiddenGems,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.sporting_journey_hidden_gems_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = TTColor.VaultCardBackground
            ) {
                Text(
                    text = stringResource(R.string.sporting_journey_hidden_gems_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = TTColor.VaultAmber,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Text(
            text = stringResource(R.string.sporting_journey_hidden_gems_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Gem 1: Biological Engine
        GemCard(
            categoryTitle = stringResource(R.string.sporting_journey_gem_engine_title),
            pillLabel = stringResource(R.string.sporting_journey_gem_engine_pill),
            pillColor = TTColor.VaultBiologicalRed,
            headline = stringResource(
                R.string.sporting_journey_gem_cranks_val,
                String.format(Locale.getDefault(), "%,d", gems.pedalRevolutions)
            ),
            description = stringResource(
                R.string.sporting_journey_gem_cranks_desc,
                gems.cadenceTrackedRidesCount,
                String.format(Locale.getDefault(), "%,d", gems.pedalRevolutions),
                String.format(Locale.getDefault(), "%,d", gems.estimatedHeartbeats)
            )
        )

        // Gem 2: Human Power Plant
        GemCard(
            categoryTitle = stringResource(R.string.sporting_journey_gem_power_title),
            pillLabel = stringResource(R.string.sporting_journey_gem_power_pill, gems.powerTrackedWorkoutsCount),
            pillColor = TTColor.VaultElectricAmber,
            headline = stringResource(R.string.sporting_journey_gem_kwh_val, gems.mechanicalKwh),
            description = stringResource(
                R.string.sporting_journey_gem_kwh_desc,
                String.format(Locale.getDefault(), "%,.0f", gems.mechanicalKj),
                String.format(Locale.getDefault(), "%,.0f", gems.ebikeKmEquivalent),
                gems.espressosEquivalent,
                gems.phoneChargesEquivalent
            )
        )

        // Gem 3: Circadian DNA
        val startH = gems.mostActiveHourStart ?: 6
        val endH = (startH + 2) % 24
        val dowName = remember(gems.mostActiveDayOfWeek) {
            gems.mostActiveDayOfWeek?.let { dow ->
                val dayOfWeek = when (dow) {
                    0 -> DayOfWeek.SUNDAY
                    1 -> DayOfWeek.MONDAY
                    2 -> DayOfWeek.TUESDAY
                    3 -> DayOfWeek.WEDNESDAY
                    4 -> DayOfWeek.THURSDAY
                    5 -> DayOfWeek.FRIDAY
                    6 -> DayOfWeek.SATURDAY
                    else -> DayOfWeek.FRIDAY
                }
                dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            } ?: DayOfWeek.FRIDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
        }
        GemCard(
            categoryTitle = stringResource(R.string.sporting_journey_gem_circadian_title),
            pillLabel = stringResource(R.string.sporting_journey_gem_circadian_pill),
            pillColor = TTColor.VaultCircadianSky,
            headline = stringResource(R.string.sporting_journey_gem_circadian_val, startH, endH),
            description = stringResource(R.string.sporting_journey_gem_circadian_desc, startH, endH, dowName)
        )

        // Gem 4: Explorer vs Ritual Index
        GemCard(
            categoryTitle = stringResource(R.string.sporting_journey_gem_explorer_title),
            pillLabel = stringResource(R.string.sporting_journey_gem_explorer_pill),
            pillColor = TTColor.VaultExplorerEmerald,
            headline = stringResource(
                R.string.sporting_journey_gem_explorer_val,
                gems.ritualPercentage,
                100 - gems.ritualPercentage
            ),
            description = stringResource(
                R.string.sporting_journey_gem_explorer_desc,
                gems.ritualWorkoutsCount,
                gems.recurringRoutesCount,
                gems.explorerWorkoutsCount
            )
        )
    }
}

@Composable
private fun GemCard(
    categoryTitle: String,
    pillLabel: String,
    pillColor: androidx.compose.ui.graphics.Color,
    headline: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = categoryTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = pillColor,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = pillColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = pillLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = pillColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Section presenting Signature Milestones with click-through navigation (REQ-SPT-003, REQ-SPT-005, Pillar 3).
 */
@Composable
fun MilestonesSection(
    milestones: List<SportingMilestone>,
    onMilestoneClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.sporting_journey_milestones_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.sporting_journey_milestones_hint),
                style = MaterialTheme.typography.labelSmall,
                color = TTColor.VaultAmber
            )
        }

        milestones.forEach { item ->
            val icon = when (item.id) {
                "first_workout" -> Icons.Default.History
                "longest_workout" -> Icons.Default.Timer
                "highest_climb" -> Icons.Default.Terrain
                "busiest_month" -> Icons.Default.DateRange
                else -> Icons.Default.Star
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = item.workoutId != null) {
                        item.workoutId?.let { onMilestoneClick(it) }
                    },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = TTColor.VaultAmber.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = TTColor.VaultAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (item.subtitle.isNotBlank()) {
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.value,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = TTColor.VaultAmber
                        )
                        if (item.workoutId != null) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section displaying the Achievement Room with auto-unlocked trophies and uniform 4-column grid (REQ-SPT-003, REQ-SPT-005, Pillar 4).
 */
@Composable
fun AchievementRoomSection(
    achievements: List<SportingAchievement>,
    onWorkoutClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedBadge by remember { mutableStateOf<SportingAchievement?>(achievements.firstOrNull()) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sporting_journey_achievements_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val unlockedCount = achievements.count { it.isUnlocked }
                Text(
                    text = stringResource(R.string.sporting_journey_achievements_count, unlockedCount, achievements.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = TTColor.VaultAmber
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.sporting_journey_achievements_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Badges Uniform 4-Column Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            achievements.chunked(4).forEach { rowBadges ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowBadges.forEach { badge ->
                        val isSelected = selectedBadge?.id == badge.id
                        val badgeIcon = when (badge.id) {
                            "century_rider" -> Icons.Default.DirectionsBike
                            "everest_club" -> Icons.Default.Terrain
                            "dawn_patrol" -> Icons.Default.WbSunny
                            "million_cranks" -> Icons.Default.Repeat
                            "pioneer_athlete" -> Icons.Default.EmojiEvents
                            "local_legend" -> Icons.Default.Place
                            "human_powerplant" -> Icons.Default.Bolt
                            "halfway_world" -> Icons.Default.Public
                            else -> Icons.Default.Star
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(96.dp)
                                .clickable { selectedBadge = badge },
                            shape = RoundedCornerShape(12.dp),
                            color = if (badge.isUnlocked) {
                                if (isSelected) TTColor.VaultCardBackground else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) TTColor.VaultAmber
                                else if (badge.isUnlocked) TTColor.VaultCardBorder
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (badge.isUnlocked) badgeIcon else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (badge.isUnlocked) TTColor.VaultAmber else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = badge.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    color = if (badge.isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = badge.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    color = if (badge.isUnlocked) TTColor.VaultAmber else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    // Pad incomplete rows with equal-weight spacers to guarantee uniform width
                    repeat(4 - rowBadges.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Selected Badge Story Box
        selectedBadge?.let { badge ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = badge.workoutId != null) {
                        badge.workoutId?.let { onWorkoutClick(it) }
                    },
                shape = RoundedCornerShape(12.dp),
                color = TTColor.VaultCardBackground,
                border = BorderStroke(1.dp, TTColor.VaultCardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = badge.storyTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TTColor.VaultAmber
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = badge.storyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Shareable Athlete Passport Card with native [Intent.ACTION_SEND] dispatch (REQ-SPT-004, REQ-SPT-005).
 */
@Composable
fun AthletePassportShareCard(
    data: SportingJourneyData,
    tier: SupporterTier,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TTColor.VaultCardBorder),
        colors = CardDefaults.cardColors(containerColor = TTColor.VaultPassportBgStart)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(TTColor.VaultPassportBgStart, TTColor.VaultPassportBgEnd)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sporting_journey_share_title).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TTColor.VaultAmber
                    )
                    Text(
                        text = stringResource(
                            R.string.sporting_journey_title
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onShareClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TTColor.VaultAmber,
                        contentColor = TTColor.VaultPassportBgStart
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.sporting_journey_share_card),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
