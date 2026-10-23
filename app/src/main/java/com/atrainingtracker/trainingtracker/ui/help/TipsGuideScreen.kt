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

package com.atrainingtracker.trainingtracker.ui.help

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.navigation.NavRoutes
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.safePainterResource

/**
 * Data model for a curated power tip in the feature discovery guide.
 *
 * Fulfills REQ-UI-432 / ATT-2276.
 */
data class TipItemConfig(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val actionRes: Int,
    @DrawableRes val iconRes: Int,
    val targetRoute: String
)

/**
 * Curated registry of power tips showcasing core features with 1-tap deep links.
 */
object TipsGuideData {
    val TIPS: List<TipItemConfig> = listOf(
        TipItemConfig(
            id = "routes_clustering",
            titleRes = R.string.tip_routes_clustering_title,
            subtitleRes = R.string.tip_routes_clustering_subtitle,
            descriptionRes = R.string.tip_routes_clustering_desc,
            actionRes = R.string.tip_routes_clustering_action,
            iconRes = R.drawable.ic_favorite_route,
            targetRoute = NavRoutes.LOCATIONS
        ),
        TipItemConfig(
            id = "sensors_failover",
            titleRes = R.string.tip_sensors_failover_title,
            subtitleRes = R.string.tip_sensors_failover_subtitle,
            descriptionRes = R.string.tip_sensors_failover_desc,
            actionRes = R.string.tip_sensors_failover_action,
            iconRes = R.drawable.ic_my_paired_devices,
            targetRoute = NavRoutes.SENSORS
        ),
        TipItemConfig(
            id = "cockpit_customization",
            titleRes = R.string.tip_cockpit_customization_title,
            subtitleRes = R.string.tip_cockpit_customization_subtitle,
            descriptionRes = R.string.tip_cockpit_customization_desc,
            actionRes = R.string.tip_cockpit_customization_action,
            iconRes = R.drawable.ic_display_settings,
            targetRoute = NavRoutes.SPORT_TYPES
        ),
        TipItemConfig(
            id = "live_segments",
            titleRes = R.string.tip_live_segments_title,
            subtitleRes = R.string.tip_live_segments_subtitle,
            descriptionRes = R.string.tip_live_segments_desc,
            actionRes = R.string.tip_live_segments_action,
            iconRes = R.drawable.ic_segment,
            targetRoute = NavRoutes.SEGMENTS
        ),
        TipItemConfig(
            id = "gear_tracking",
            titleRes = R.string.tip_gear_tracking_title,
            subtitleRes = R.string.tip_gear_tracking_subtitle,
            descriptionRes = R.string.tip_gear_tracking_desc,
            actionRes = R.string.tip_gear_tracking_action,
            iconRes = R.drawable.ic_equipment_combined,
            targetRoute = NavRoutes.EQUIPMENT
        ),
        TipItemConfig(
            id = "training_zones",
            titleRes = R.string.tip_training_zones_title,
            subtitleRes = R.string.tip_training_zones_subtitle,
            descriptionRes = R.string.tip_training_zones_desc,
            actionRes = R.string.tip_training_zones_action,
            iconRes = R.drawable.ic_cardiology,
            targetRoute = NavRoutes.trainingZones()
        ),
        TipItemConfig(
            id = "data_backup",
            titleRes = R.string.tip_data_backup_title,
            subtitleRes = R.string.tip_data_backup_subtitle,
            descriptionRes = R.string.tip_data_backup_desc,
            actionRes = R.string.tip_data_backup_action,
            iconRes = R.drawable.ic_save_to_disc,
            targetRoute = NavRoutes.BACKUP_RESTORE
        )
    )
}

/**
 * In-App Feature Discovery & Tips Guide screen (REQ-UI-432).
 * Opt-in, non-intrusive guide presenting power tips with direct navigation actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipsGuideScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
    tips: List<TipItemConfig> = TipsGuideData.TIPS
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.tips_guide_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TipsGuideHeaderCard()
            tips.forEach { tip ->
                TipCard(
                    tip = tip,
                    onActionClick = { onNavigateToRoute(tip.targetRoute) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Motivational header banner introducing feature discovery.
 */
@Composable
fun TipsGuideHeaderCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.tips_guide_header_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.tips_guide_header_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Individual power tip card showcasing an icon, title, subtitle, actionable explanation,
 * and a 1-tap deep link button into the respective app destination.
 */
@Composable
fun TipCard(
    tip: TipItemConfig,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = safePainterResource(tip.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(tip.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(tip.subtitleRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = stringResource(tip.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(tip.actionRes),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Preview(name = "Light Theme", showBackground = true)
@Composable
private fun TipsGuideScreenLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        TipsGuideScreen(
            onNavigateBack = {},
            onNavigateToRoute = {}
        )
    }
}

@Preview(name = "Dark Theme", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TipsGuideScreenDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        TipsGuideScreen(
            onNavigateBack = {},
            onNavigateToRoute = {}
        )
    }
}
