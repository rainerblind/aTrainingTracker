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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Behavioral Compose UI test verifying [HrvAlpha1ScatterChart] propagates dynamic [t1] and [t2]
 * thresholds into scatter dot colors (REQ-HRV-044.3, TST-HRV-044.2).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h300dp-xhdpi")
class HrvAlpha1ScatterChartUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testScatterDots_useCustomT1T2ThresholdsRatherThanDefaults() {
        // Sample point at alpha1 = 0.80:
        // Under default T1=0.75 it would be Zone2 (green); under custom T1=0.90, T2=0.60 it MUST be Zone3 (orange).
        val singlePoint = listOf(
            PathPoint(
                distance = 100.0,
                latLng = LatLng(0.0, 0.0),
                timeSec = 10L,
                hr = 145,
                hrvDfaA1 = 0.80,
                hrvArtifactRate = 0.0
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false, amoled = false, setWindowColors = false) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .testTag("scatter_custom_thresholds")
                ) {
                    HrvAlpha1ScatterChart(
                        points = singlePoint,
                        intensityMode = HrvScatterIntensityMode.HEART_RATE,
                        t1 = 0.90f,
                        t2 = 0.60f
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        val bmp = composeTestRule.onNodeWithTag("scatter_custom_thresholds")
            .captureToImage()
            .asAndroidBitmap()

        // Scatter dot draws TTColor.Zone3.copy(alpha = 0.65f) over Color.White
        val expectedBlendedOrange = lerp(Color.White, TTColor.Zone3, 0.65f)
        assertTrue(
            "Scatter dot at alpha1=0.80 with t1=0.90 must render blended Zone3 orange",
            bitmapContainsColorNear(bmp, expectedBlendedOrange, tolerance = 14)
        )
    }

    private fun bitmapContainsColorNear(bitmap: Bitmap, target: Color, tolerance: Int = 14): Boolean {
        val targetArgb = target.toArgb()
        val tr = android.graphics.Color.red(targetArgb)
        val tg = android.graphics.Color.green(targetArgb)
        val tb = android.graphics.Color.blue(targetArgb)

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val px = bitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(px) < 200) continue
                val dr = abs(android.graphics.Color.red(px) - tr)
                val dg = abs(android.graphics.Color.green(px) - tg)
                val db = abs(android.graphics.Color.blue(px) - tb)
                if (dr <= tolerance && dg <= tolerance && db <= tolerance) {
                    return true
                }
            }
        }
        return false
    }
}
