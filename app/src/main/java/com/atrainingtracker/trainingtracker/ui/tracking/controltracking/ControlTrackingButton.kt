package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrackingMode

@Composable
fun ControlTrackingButton(mode: TrackingMode, viewModel: TrackingViewModel) {
    AnimatedContent(
        targetState = mode,
        label = "ControlTransition"
    ) { targetMode ->
        when (targetMode) {
            TrackingMode.TRACKING -> {
                // PAUSE BUTTON
                Button(
                    onClick = { viewModel.onPauseTracking() },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(64.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.control_pause),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.Unspecified // Keeps the blue color from your image
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(id = R.string.pause_tracking).uppercase(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            TrackingMode.PAUSED -> {
                // RESUME & STOP BUTTONS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    Button(
                        onClick = { viewModel.onResumeTracking() },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.control_start),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(id = R.string.resume_tracking).uppercase())
                    }

                    OutlinedButton(
                        onClick = { viewModel.onStopTracking() },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.control_stop),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(id = R.string.stop_tracking).uppercase())
                    }
                }
            }

            else -> { // IDLE / DEFAULT
                // START BUTTON
                Button(
                    onClick = { viewModel.onStartTracking() },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(64.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.control_start),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(id = R.string.start_tracking).uppercase(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "State: Idle/Start")
@Composable
fun PreviewControlTrackingButtonIdle() {MaterialTheme {
    Box(Modifier.padding(16.dp)) {
        // Mocking IDLE state: Shows the START button
        ControlTrackingButton(
            mode = TrackingMode.SEARCHING,
            viewModel = TODO("Pass mock or make VM an interface")
        )
    }
}
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "State: Tracking/Pause")
@Composable
fun PreviewControlTrackingButtonTracking() {
    MaterialTheme {
        Box(Modifier.padding(16.dp)) {
            // Mocking TRACKING state: Shows the PAUSE button
            ControlTrackingButton(
                mode = TrackingMode.TRACKING,
                viewModel = TODO("Pass mock or make VM an interface")
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "State: Paused")
@Composable
fun PreviewControlTrackingButtonPaused() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Paused Mode UI:", style = MaterialTheme.typography.labelLarge)

            // This replicates the Row inside the PAUSED branch of your when statement
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.control_start),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("RESUME")
                }

                OutlinedButton(
                    onClick = {},
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.control_stop),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("STOP")
                }
            }
        }
    }
}