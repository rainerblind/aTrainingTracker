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
fun SportTypeSelector(viewModel: TrackingViewModel) {
    val currentSport = BSportType.UNKNOWN  // TODO: get from viewModel

    // Parity with your current implementation's drawables and strings
    val sports = listOf(
        Triple(BSportType.RUN, R.drawable.bsport_run, R.string.sport_type_run),
        Triple(BSportType.BIKE, R.drawable.bsport_bike, R.string.sport_type_bike),
        Triple(BSportType.UNKNOWN, R.drawable.bsport_other, R.string.sport_type_other)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        sports.forEach { (sport, iconRes, labelRes) ->
            SportItem(
                isSelected = currentSport == sport,
                iconRes = iconRes,
                labelRes = labelRes,
                onClick = { viewModel.setSport(sport) }
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
            .padding(horizontal = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            // Uses your theme's primary color when selected, otherwise a muted gray
            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(id = labelRes),
            fontSize = 15.sp,
            // Matches the high contrast text colors of your current UI
            color = if (isSelected) Color.Black else Color.Gray
        )
    }
}

@Preview(showBackground = true, name = "Sport Selector - Bike Selected")
@Composable
fun PreviewSportTypeSelector() {
    MaterialTheme {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Mocking the Bike Selected state for the preview
            SportPreviewItem(true, "BIKE")
            SportPreviewItem(false, "RUN")
            SportPreviewItem(false, "OTHER")
        }
    }
}

@Composable
private fun SportPreviewItem(isSelected: Boolean, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Placeholder box to represent the icon in preview
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.LightGray
                )
        )
        Text(
            text = label,
            fontSize = 15.sp,
            color = if (isSelected) Color.Black else Color.Gray
        )
    }
}