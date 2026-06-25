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

package com.atrainingtracker.trainingtracker.ui.components.workoutdescription

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

@Composable
fun WorkoutDescription(
    data: DescriptionData,
    modifier: Modifier = Modifier
) {
    // Only render the Column if at least one field is not null/blank
    if (!data.description.isNullOrBlank() ||
        !data.goal.isNullOrBlank() ||
        !data.method.isNullOrBlank()) {

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Description Row
            DescriptionRow(
                iconRes = R.drawable.ic_workout_description,
                text = data.description
            )

            // Goal Row
            DescriptionRow(
                iconRes = R.drawable.ic_workout_goal,
                text = data.goal
            )

            // Method Row
            DescriptionRow(
                iconRes = R.drawable.ic_workout_method,
                text = data.method
            )
        }
    }
}

@Composable
private fun DescriptionRow(
    iconRes: Int,
    text: String?
) {
    // Replicates "android:visibility="gone"" logic
    if (!text.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(8.dp)) // Matches android:drawablePadding="8dp"

            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, // Matches android:maxLines="3"
                overflow = TextOverflow.Ellipsis // Matches android:ellipsize="end"
            )
        }
    }
}

// --- Previews ---

@Preview(showBackground = true)
@Composable
fun PreviewWorkoutDescription() {
    MaterialTheme {
        WorkoutDescription(
            data = DescriptionData(
                description = "A short run through the forest to test the new shoes.",
                goal = "Keep a steady pace below 6:00 min/km.",
                method = "Fartlek with 5 intervals."
            )
        )
    }
}

@Preview(showBackground = true, name = "Partial Content")
@Composable
fun PreviewWorkoutDescriptionPartial() {
    MaterialTheme {
        WorkoutDescription(
            data = DescriptionData(
                description = "Just a quick test description.",
                method = null,
                goal = null
            )
        )
    }
}