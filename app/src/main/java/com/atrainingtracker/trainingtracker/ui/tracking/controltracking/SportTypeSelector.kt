package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType

@Composable
fun SportTypeSelector(
    currentSport: BSportType,
    onSportSelected: (BSportType) -> Unit,
    modifier: Modifier = Modifier
) {
    // Parity with your current implementation's drawables and strings
    val sports = listOf(
        Triple(BSportType.RUN, R.drawable.bsport_run, R.string.sport_type_run),
        Triple(BSportType.BIKE, R.drawable.bsport_bike, R.string.sport_type_bike),
        Triple(BSportType.UNKNOWN, R.drawable.bsport_other, R.string.sport_type_other)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sports.forEach { (sport, iconRes, labelRes) ->
            SportItem(
                isSelected = currentSport == sport,
                iconRes = iconRes,
                labelRes = labelRes,
                onClick = { onSportSelected(sport) }
            )
        }
    }
}

@Composable
private fun SportItem(
    isSelected: Boolean,
    iconRes: Int,
    labelRes: Int,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = if (isSelected) Color.Unspecified else Color.Gray, // selected: No change, unselected: Muted Gray
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(id = labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Gray
        )
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Sport Selector - Bike Selected")
@Composable
fun PreviewSportTypeSelectorBike() {
    MaterialTheme {
        Surface {
            SportTypeSelector(
                currentSport = BSportType.BIKE,
                onSportSelected = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Sport Selector - Run Selected")
@Composable
fun PreviewSportTypeSelectorRun() {
    MaterialTheme {
        Surface {
            SportTypeSelector(
                currentSport = BSportType.RUN,
                onSportSelected = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Sport Selector - Other Selected")
@Composable
fun PreviewSportTypeSelectorOther() {
    MaterialTheme {
        Surface {
            SportTypeSelector(
                currentSport = BSportType.UNKNOWN,
                onSportSelected = {}
            )
        }
    }
}