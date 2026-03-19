package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.content.res.Configuration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
    searchingFor: String?,
    bSportType: BSportType,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // Always on the left: The Research Button
        ResearchButton(
            isEnabled = searchingFor == null,
            onSearch = onSearch
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Dynamic Status Area
        Column(verticalArrangement = Arrangement.Center) {

            if (searchingFor != null) {
                StatusInfo(
                    text = stringResource(R.string.searching_for_device_format, searchingFor),
                    showProgress = true
                )
            }
            else {
                val actionText = stringResource(id = getReadyActionString(bSportType))
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Helper to map BSportType to the "ready" action strings from strings.xml
 */
private fun getReadyActionString(type: BSportType): Int {
    return when (type) {
        BSportType.RUN -> R.string.control_tracking_ready_run
        BSportType.BIKE -> R.string.control_tracking_ready_bike
        else -> R.string.control_tracking_ready_other
    }
}

@Composable
private fun ResearchButton(isEnabled: Boolean, onSearch: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = isEnabled, onClick = onSearch)
            .padding(8.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.research_icon),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(id = R.string.research),
            style = MaterialTheme.typography.labelSmall,
            color = if (isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray
        )
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

private fun getSportIcon(type: BSportType): Int {
    return when (type) {
        BSportType.RUN -> R.drawable.bsport_run
        BSportType.BIKE -> R.drawable.bsport_bike
        else -> R.drawable.bsport_other
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Tracking - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewSearchAreaTrackingDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            SearchArea(
                searchingFor = null,
                bSportType = BSportType.BIKE,
                onSearch = {}
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
                searchingFor = "Polar H10",
                bSportType = BSportType.RUN,
                onSearch = {}
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
                searchingFor = null,
                bSportType = BSportType.RUN,
                onSearch = {}
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
                searchingFor = null,
                bSportType = BSportType.RUN,
                onSearch = {}
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
                searchingFor = null,
                bSportType = BSportType.RUN,
                onSearch = {}
            )
        }
    }
}