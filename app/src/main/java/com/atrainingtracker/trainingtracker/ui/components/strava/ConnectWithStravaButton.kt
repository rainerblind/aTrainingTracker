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

package com.atrainingtracker.trainingtracker.ui.components.strava

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

/**
 * Official "Connect with Strava" button using the approved assets.
 */
@Composable
fun ConnectWithStravaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useWhiteButton: Boolean = false
) {
    val resId = if (useWhiteButton) R.drawable.btn_strava_connect_with_white 
                else R.drawable.btn_strava_connect_with_orange
    
    Image(
        painter = painterResource(id = resId),
        contentDescription = "Connect with Strava",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth() // Allow it to fill the container width
            .heightIn(max = 120.dp) // Set a high max height, but let it scale based on width
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    )
}
