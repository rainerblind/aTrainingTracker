package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

@Composable
fun ResearchButton(
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(12.dp) // Match PairingItem padding
    ) {
        Icon(
            painter = painterResource(id = R.drawable.research_icon),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            // Use primary color for the active state to make it stand out
            tint = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(id = R.string.research),
            style = MaterialTheme.typography.labelMedium,
            color = if (isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray
        )
    }
}