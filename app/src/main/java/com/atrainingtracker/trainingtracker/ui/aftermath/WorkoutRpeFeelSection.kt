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

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import kotlin.math.roundToInt

object WorkoutRpeFeelTestTags {
    const val SECTION = "workout_rpe_feel_section"
    const val RPE_BADGE = "workout_rpe_badge"
    const val FEEL_BADGE = "workout_feel_badge"
    const val SRPE_BADGE = "workout_srpe_badge"
    const val EDITOR = "workout_rpe_feel_editor"
    const val RPE_SLIDER = "workout_rpe_slider"
    const val RPE_CLEAR = "workout_rpe_clear"
    const val FEEL_CLEAR = "workout_feel_clear"
}

@StringRes
fun getRpeAnchorStringRes(rpe: Int): Int = when (rpe) {
    1 -> R.string.rpe_anchor_1
    2 -> R.string.rpe_anchor_2
    3 -> R.string.rpe_anchor_3
    4 -> R.string.rpe_anchor_4
    5 -> R.string.rpe_anchor_5
    6 -> R.string.rpe_anchor_6
    7 -> R.string.rpe_anchor_7
    8 -> R.string.rpe_anchor_8
    9 -> R.string.rpe_anchor_9
    10 -> R.string.rpe_anchor_10
    else -> R.string.rpe_anchor_1
}

/**
 * Retained for backward compatibility (REQ-WKO-037).
 */
fun getFeelEmoji(feel: Int): String = when (feel) {
    1 -> "😫"
    2 -> "😕"
    3 -> "😐"
    4 -> "🙂"
    5 -> "🤩"
    else -> "😐"
}

/**
 * Maps a subjective feel rating (1..5) to its corresponding Material 3 sentiment vector icon (REQ-WKO-037.2).
 */
fun getFeelIcon(feel: Int): ImageVector = when (feel) {
    1 -> Icons.Filled.SentimentVeryDissatisfied
    2 -> Icons.Filled.SentimentDissatisfied
    3 -> Icons.Filled.SentimentNeutral
    4 -> Icons.Filled.SentimentSatisfied
    5 -> Icons.Filled.SentimentVerySatisfied
    else -> Icons.Filled.SentimentNeutral
}

@StringRes
fun getFeelAnchorStringRes(feel: Int): Int = when (feel) {
    1 -> R.string.feel_anchor_1
    2 -> R.string.feel_anchor_2
    3 -> R.string.feel_anchor_3
    4 -> R.string.feel_anchor_4
    5 -> R.string.feel_anchor_5
    else -> R.string.feel_anchor_3
}

/**
 * Compact, color-coded badge row displaying perceived exertion (RPE 1–10),
 * subjective workout feel (1–5), and Foster Session-RPE training load (`REQ-WKO-038`).
 */
@Composable
fun WorkoutRpeFeelSection(
    rpe: Int?,
    feel: Int?,
    activeTimeSec: Long,
    modifier: Modifier = Modifier
) {
    if (rpe == null && feel == null) return

    val sRpe = if (rpe != null && activeTimeSec > 0) {
        PowerPhysiologyCalculator.calculateSessionRpe(rpe, activeTimeSec.toDouble()).roundToInt()
    } else null

    Row(
        modifier = modifier
            .testTag(WorkoutRpeFeelTestTags.SECTION),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rpe != null && rpe in 1..10) {
            val rpeColor = TTColor.rpeColor(rpe)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = rpeColor.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, rpeColor.copy(alpha = 0.6f)),
                modifier = Modifier.testTag(WorkoutRpeFeelTestTags.RPE_BADGE)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(rpeColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(getRpeAnchorStringRes(rpe)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (feel != null && feel in 1..5) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.testTag(WorkoutRpeFeelTestTags.FEEL_BADGE)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getFeelIcon(feel),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(getFeelAnchorStringRes(feel)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        if (sRpe != null && sRpe > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.testTag(WorkoutRpeFeelTestTags.SRPE_BADGE)
            ) {
                Text(
                    text = stringResource(R.string.srpe_load_badge, sRpe),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Interactive editor controls for Perceived Exertion (RPE 1–10) and Workout Feel (1–5)
 * positioned in [EditWorkoutScreen] directly above the Description field (`REQ-WKO-037.1`, `REQ-WKO-037.2`).
 */
@Composable
fun RpeFeelEditorControls(
    rpe: Int?,
    feel: Int?,
    onRpeChange: (Int?) -> Unit,
    onFeelChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WorkoutRpeFeelTestTags.EDITOR),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. RPE (1..10) Discrete Borg CR10 Slider & Non-clipping Button Row (REQ-WKO-037.1)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.workout_rpe_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (rpe != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(getRpeAnchorStringRes(rpe)),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TTColor.rpeColor(rpe)
                        )
                        IconButton(
                            onClick = { onRpeChange(null) },
                            modifier = Modifier
                                .size(24.dp)
                                .testTag(WorkoutRpeFeelTestTags.RPE_CLEAR)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Interactive discrete Material 3 Slider (1..10, steps = 8)
            Slider(
                value = (rpe ?: 1).toFloat(),
                onValueChange = { newValue ->
                    onRpeChange(newValue.roundToInt().coerceIn(1, 10))
                },
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = if (rpe != null) TTColor.rpeColor(rpe) else MaterialTheme.colorScheme.primary,
                    activeTrackColor = if (rpe != null) TTColor.rpeColor(rpe) else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WorkoutRpeFeelTestTags.RPE_SLIDER)
            )

            // Equal-width buttons row (weight(1f), no horizontal clipping on standard portrait viewports)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (score in 1..10) {
                    val isSelected = rpe == score
                    val color = TTColor.rpeColor(score)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) color.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) color else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                onRpeChange(if (isSelected) null else score)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = score.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Feel (1..5) M3 Tonal Cards with Vector Sentiment Icons (REQ-WKO-037.2)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.workout_feel_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (feel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(getFeelAnchorStringRes(feel)),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = { onFeelChange(null) },
                            modifier = Modifier
                                .size(24.dp)
                                .testTag(WorkoutRpeFeelTestTags.FEEL_CLEAR)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 5 equal-width tonal cards rendering Material 3 vector iconography
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (f in 1..5) {
                    val isSelected = feel == f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                onFeelChange(if (isSelected) null else f)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getFeelIcon(f),
                            contentDescription = stringResource(getFeelAnchorStringRes(f)),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
