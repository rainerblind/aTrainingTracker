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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme


@Composable
fun ControlTrackingButton(
    modifier: Modifier = Modifier,
    mode: TrackingMode,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    AnimatedContent(
        targetState = mode,
        label = "ControlTransition",
        modifier = modifier
    ) { targetMode ->
        when (targetMode) {
            TrackingMode.TRACKING -> {
                // PAUSE BUTTON (No Shape, Vertical)
                ControlItem(
                    iconRes = R.drawable.control_pause,
                    labelRes = R.string.pause_tracking,
                    onClick = onPause,
                    iconSize = 100.dp
                )
            }

            TrackingMode.PAUSED -> {
                // RESUME & STOP BUTTONS (No Shape, Vertical)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlItem(
                        iconRes = R.drawable.control_start,
                        labelRes = R.string.resume_tracking,
                        onClick = onResume,
                        iconSize = 80.dp
                    )
                    ControlItem(
                        iconRes = R.drawable.control_stop,
                        labelRes = R.string.stop_tracking,
                        onClick = onStop,
                        iconSize = 80.dp
                    )
                }
            }

            else -> { // IDLE / DEFAULT
                // START BUTTON (No Shape, Vertical)
                ControlItem(
                    iconRes = R.drawable.control_start,
                    labelRes = R.string.start_tracking,
                    onClick = onStart,
                    iconSize = 100.dp
                )
            }
        }
    }
}

/**
 * Helper component to maintain consistency across all control actions.
 * No shape, vertical layout, large icon.
 */
@Composable
private fun ControlItem(
    iconRes: Int,
    labelRes: Int,
    onClick: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(id = labelRes),
            // style = MaterialTheme.typography.labelLarge,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Start State")
@Composable
fun PreviewControlStart() {
    ATrainingTrackerTheme {
        Surface {
            ControlTrackingButton(
                mode = TrackingMode.READY,
                onStart = {}, onPause = {}, onResume = {}, onStop = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Tracking State")
@Composable
fun PreviewControlTracking() {
    ATrainingTrackerTheme {
        Surface {
            ControlTrackingButton(
                mode = TrackingMode.TRACKING,
                onStart = {}, onPause = {}, onResume = {}, onStop = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Paused State")
@Composable
fun PreviewControlPaused() {
    ATrainingTrackerTheme {
        Surface {
            ControlTrackingButton(
                mode = TrackingMode.PAUSED,
                onStart = {}, onPause = {}, onResume = {}, onStop = {}
            )
        }
    }
}


@Preview(showBackground = true, name = "Light Mode - Start")
@Preview(
    showBackground = true,
    name = "Dark Mode - Start",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewControlStartDark() {
    ATrainingTrackerTheme {
        Surface {
            ControlTrackingButton(
                mode = TrackingMode.READY,
                onStart = {}, onPause = {}, onResume = {}, onStop = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Light Mode - Tracking")
@Preview(
    showBackground = true,
    name = "Dark Mode - Tracking",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewControlTrackingDark() {
    ATrainingTrackerTheme {
        Surface {
            ControlTrackingButton(
                mode = TrackingMode.TRACKING,
                onStart = {}, onPause = {}, onResume = {}, onStop = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Light Mode - Paused")
@Preview(
    showBackground = true,
    name = "Dark Mode - Paused",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewControlPausedDark() {
    ATrainingTrackerTheme {
        Surface {
            ControlTrackingButton(
                mode = TrackingMode.PAUSED,
                onStart = {}, onPause = {}, onResume = {}, onStop = {}
            )
        }
    }
}