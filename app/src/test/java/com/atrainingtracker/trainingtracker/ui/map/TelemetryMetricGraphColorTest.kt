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

package com.atrainingtracker.trainingtracker.ui.map

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.settings.TelemetryGraphColorPreferences
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.AmoledDarkColorScheme
import com.atrainingtracker.trainingtracker.ui.theme.DarkColorScheme
import com.atrainingtracker.trainingtracker.ui.theme.LightColorScheme
import com.atrainingtracker.trainingtracker.ui.theme.TelemetryGraphColor
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Robolectric Compose UI and native rendering tests verifying [TelemetryMetricGraph] integration
 * with [TelemetryGraphColorPreferences] across Light, Dark, and AMOLED themes, plus live
 * preference update reactivity (REQ-UI-530.4, TST-UI-530.4, ATT-7759).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TelemetryMetricGraphColorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    private val samplePoints = listOf(
        PathPoint(
            distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 500.0,
            hr = 132, power = 180, speedMps = 6.5, wPrimeBalKj = 18.0, timeSec = 0L
        ),
        PathPoint(
            distance = 500.0, latLng = LatLng(48.004, 11.004), altitude = 510.0,
            hr = 148, power = 240, speedMps = 7.2, wPrimeBalKj = 16.5, timeSec = 70L
        ),
        PathPoint(
            distance = 1000.0, latLng = LatLng(48.008, 11.008), altitude = 518.0,
            hr = 162, power = 290, speedMps = 7.8, wPrimeBalKj = 14.0, timeSec = 135L
        ),
        PathPoint(
            distance = 1500.0, latLng = LatLng(48.012, 11.012), altitude = 512.0,
            hr = 155, power = 230, speedMps = 7.0, wPrimeBalKj = 15.2, timeSec = 205L
        ),
        PathPoint(
            distance = 2000.0, latLng = LatLng(48.016, 11.016), altitude = 520.0,
            hr = 171, power = 320, speedMps = 8.1, wPrimeBalKj = 11.8, timeSec = 268L
        )
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
    }

    @Test
    fun resolveAccentColor_defaultsMatchLightDarkAndAmoledSchemes() {
        assertEquals(
            TelemetryGraphColor.CRIMSON.lightColor,
            TelemetryGraphColorPreferences.resolveAccentColor(context, TelemetryMetricType.HEART_RATE, LightColorScheme)
        )
        assertEquals(
            TelemetryGraphColor.CRIMSON.darkColor,
            TelemetryGraphColorPreferences.resolveAccentColor(context, TelemetryMetricType.HEART_RATE, DarkColorScheme)
        )
        assertEquals(
            TelemetryGraphColor.CRIMSON.darkColor,
            TelemetryGraphColorPreferences.resolveAccentColor(
                context,
                TelemetryMetricType.HEART_RATE,
                AmoledDarkColorScheme
            )
        )

        assertEquals(
            TelemetryGraphColor.EMERALD.lightColor,
            TelemetryGraphColorPreferences.resolveAccentColor(
                context,
                TelemetryMetricType.W_PRIME_BAL,
                LightColorScheme
            )
        )
        assertEquals(
            LightColorScheme.primary,
            TelemetryGraphColorPreferences.resolveAccentColor(context, TelemetryMetricType.SPEED, LightColorScheme)
        )
        assertEquals(
            DarkColorScheme.primary,
            TelemetryGraphColorPreferences.resolveAccentColor(context, TelemetryMetricType.PACE, DarkColorScheme)
        )
    }

    @Test
    fun telemetryMetricGraph_rendersDefaultAndUpdatedGlobalColorReactively() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false, setWindowColors = false) {
                TelemetryMetricGraph(
                    pathPoints = samplePoints,
                    metricType = TelemetryMetricType.HEART_RATE,
                    currentDistance = null,
                    onDistanceSelected = {},
                    enableGestures = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("hr_graph_root")
                )
            }
        }

        val defaultBitmap = composeTestRule.onNodeWithTag("hr_graph_root")
            .captureToImage()
            .asAndroidBitmap()
        assertTrue(
            "Default HR graph must contain CRIMSON light stroke pixels",
            bitmapContainsColorNear(defaultBitmap, TelemetryGraphColor.CRIMSON.lightColor)
        )

        // Mutate global HR graph color preference to CYAN while composed
        TelemetryGraphColorPreferences.setGraphColor(
            context,
            TelemetryMetricType.HEART_RATE,
            TelemetryGraphColor.CYAN
        )
        composeTestRule.waitForIdle()

        val updatedBitmap = composeTestRule.onNodeWithTag("hr_graph_root")
            .captureToImage()
            .asAndroidBitmap()
        assertTrue(
            "Updated HR graph must contain CYAN light stroke pixels",
            bitmapContainsColorNear(updatedBitmap, TelemetryGraphColor.CYAN.lightColor)
        )
        assertFalse(
            "Updated HR graph must no longer render CRIMSON stroke pixels",
            bitmapContainsColorNear(updatedBitmap, TelemetryGraphColor.CRIMSON.lightColor)
        )
    }

    @Test
    fun telemetryMetricGraph_rendersLightThemeAndSavesScreenshot() {
        renderThemeGalleryAndAssert(label = "light", isDark = false, isAmoled = false)
    }

    @Test
    fun telemetryMetricGraph_rendersDarkThemeAndSavesScreenshot() {
        renderThemeGalleryAndAssert(label = "dark", isDark = true, isAmoled = false)
    }

    @Test
    fun telemetryMetricGraph_rendersAmoledThemeAndSavesScreenshot() {
        renderThemeGalleryAndAssert(label = "amoled", isDark = true, isAmoled = true)
    }

    private fun renderThemeGalleryAndAssert(label: String, isDark: Boolean, isAmoled: Boolean) {
        val outDir = File("build/outputs/att-7759-screenshots").apply { mkdirs() }
        val previewMetrics = listOf(
            TelemetryMetricType.HEART_RATE,
            TelemetryMetricType.POWER,
            TelemetryMetricType.W_PRIME_BAL,
            TelemetryMetricType.SPEED
        )
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = isDark, amoled = isAmoled, setWindowColors = false) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                        .testTag("theme_gallery_$label")
                ) {
                    previewMetrics.forEach { metric ->
                        TelemetryMetricGraph(
                            pathPoints = samplePoints,
                            metricType = metric,
                            currentDistance = null,
                            onDistanceSelected = {},
                            enableGestures = false
                        )
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("theme_gallery_$label")
            .captureToImage()
            .asAndroidBitmap()

        val colorFor: (TelemetryGraphColor) -> Color = { if (isDark) it.darkColor else it.lightColor }
        assertTrue(
            "[$label] must contain HR CRIMSON stroke",
            bitmapContainsColorNear(bmp, colorFor(TelemetryGraphColor.CRIMSON))
        )
        assertTrue(
            "[$label] must contain Power VIOLET stroke",
            bitmapContainsColorNear(bmp, colorFor(TelemetryGraphColor.VIOLET))
        )
        assertTrue(
            "[$label] must contain W'bal EMERALD stroke",
            bitmapContainsColorNear(bmp, colorFor(TelemetryGraphColor.EMERALD))
        )

        val outFile = File(outDir, "telemetry_graphs_$label.png")
        FileOutputStream(outFile).use { fos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
    }

    private fun bitmapContainsColorNear(bitmap: Bitmap, target: Color, tolerance: Int = 12): Boolean {
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
