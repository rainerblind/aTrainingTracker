package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.content.res.Configuration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

@Composable
fun SearchArea(
    searchingFor: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (searchingFor != null) {
            StatusInfo(
                text = stringResource(R.string.searching_for_device_format, searchingFor),
                showProgress = true
            )
        }
    }
}


@Composable
private fun StatusInfo(
    text: String,
    showProgress: Boolean = false,
    iconRes: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
        } else if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconTint
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Tracking - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewSearchAreaTrackingDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            SearchArea(
                searchingFor = null
            )
        }
    }
}


@Preview(showBackground = true, name = "Searching - Light")
@Composable
fun PreviewSearchAreaSearching() {
    ATrainingTrackerTheme {
        Surface {
            SearchArea(
                searchingFor = "Polar H10"
            )
        }
    }
}
@Preview(showBackground = true, name = "Ready - Light")
@Composable
fun PreviewSearchAreaReady() {
    ATrainingTrackerTheme {
        Surface {
            SearchArea(
                searchingFor = null
            )
        }
    }
}
@Preview(showBackground = true, name = "Tracking - Light")
@Composable
fun PreviewSearchAreaTracking() {
    ATrainingTrackerTheme {
        Surface {
            SearchArea(
                searchingFor = null
            )
        }
    }
}
@Preview(showBackground = true, name = "Paused - Light")
@Composable
fun PreviewSearchAreaPaused() {
    ATrainingTrackerTheme {
        Surface {
            SearchArea(
                searchingFor = null
            )
        }
    }
}