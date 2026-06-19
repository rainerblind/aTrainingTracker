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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

@Composable
fun PoweredByStrava(
    modifier: Modifier = Modifier,
    useWhite: Boolean = false
) {
    val resId = if (useWhite) R.drawable.api_logo_pwrdby_strava_horiz_white 
                else R.drawable.api_logo_pwrdby_strava_horiz_orange
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = "Powered by Strava",
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.height(40.dp).wrapContentWidth() // Increased from 32dp for even better visibility
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewPoweredByStrava() {
    MaterialTheme {
        PoweredByStrava()
    }
}
