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

package com.atrainingtracker.trainingtracker.ui.routes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.routes.TurnNavigationState
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * High-visibility turn-by-turn navigation HUD prompt and off-route warning banner (REQ-MAP-028, REQ-UI-287 / ATT-1450, ATT-2632).
 *
 * Appears dynamically when approaching upcoming turn decision points, when executing turns,
 * or when the athlete leaves the defined route corridor.
 * Applies configurable transparency alpha and auto-dismiss countdown timer from Expert Settings.
 */
@Composable
fun TurnPromptBanner(
    navigationState: TurnNavigationState,
    promptsEnabled: Boolean = true,
    overlayAlpha: Float = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY,
    dismissDurationSec: Int = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    var isDismissed by remember { mutableStateOf(false) }

    val cueKey = navigationState.upcomingCue?.let { "${it.direction}_${it.wayName}" }
    val triggerKey = "$cueKey#${navigationState.isTurnNow}#${navigationState.isOffRoute}"

    LaunchedEffect(triggerKey) {
        isDismissed = false
        if (dismissDurationSec > 0 && (navigationState.isApproaching || navigationState.isOffRoute)) {
            delay(dismissDurationSec * 1000L)
            isDismissed = true
            onDismiss?.invoke()
        }
    }

    val isVisible = promptsEnabled && !isDismissed && (navigationState.isApproaching || navigationState.isOffRoute)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        if (navigationState.isOffRoute) {
            OffRouteCard(navigationState = navigationState, overlayAlpha = overlayAlpha)
        } else if (navigationState.isApproaching && navigationState.upcomingCue != null) {
            TurnCueCard(navigationState = navigationState, overlayAlpha = overlayAlpha)
        }
    }
}

@Composable
private fun TurnCueCard(
    navigationState: TurnNavigationState,
    overlayAlpha: Float
) {
    val cue = navigationState.upcomingCue ?: return
    val isTurnNow = navigationState.isTurnNow
    val distanceRemaining = navigationState.distanceToNextCueMeters.roundToInt()

    val containerColor = (if (isTurnNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
        .copy(alpha = overlayAlpha)
    val borderColor = TTColor.RouteActiveNavigation.copy(alpha = overlayAlpha.coerceAtLeast(0.4f))

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = cue.direction.iconResId),
                contentDescription = stringResource(id = cue.direction.displayNameResId),
                tint = if (isTurnNow) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isTurnNow) {
                        stringResource(R.string.turn_cue_now)
                    } else {
                        "${distanceRemaining} m"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isTurnNow) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                val cueTitle = if (cue.wayName.isNotBlank()) cue.wayName else stringResource(cue.direction.displayNameResId)
                Text(
                    text = cueTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isTurnNow) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun OffRouteCard(
    navigationState: TurnNavigationState,
    overlayAlpha: Float
) {
    val deviation = navigationState.crossTrackDistanceMeters.roundToInt()

    val containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = overlayAlpha)
    val borderColor = MaterialTheme.colorScheme.error.copy(alpha = overlayAlpha.coerceAtLeast(0.4f))

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_off_route),
                contentDescription = stringResource(id = R.string.turn_cue_off_route, deviation),
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.turn_cue_off_route, deviation),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Text(
                    text = stringResource(R.string.off_route_corridor_threshold_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}
