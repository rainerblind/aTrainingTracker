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
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Behavioral Compose, DataStore, multi-source speed/pace/slope overlay, and Rule 8.5 screenshot
 * verification tests for [TelemetryChartStack] and Workout Details (`TST-PER-052.1`, `.3`, `.4`, `.5`, `TST-UI-055`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1320dp-h2400dp-xhdpi")
class TelemetryChartStackVisibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testTrackTypeEnum_includesAttFusedAndExcludesNetwork() {
        // TST-PER-052.1, TST-UI-055
        val names = TrackType.entries.map { it.name }
        assertEquals(listOf("BEST", "GPS", "FUSED", "ATT_FUSED"), names)
        assertFalse(names.contains("NETWORK"))
        assertEquals("LATITUDE_att_fused", TrackType.ATT_FUSED.latitudeColumn)
        assertEquals("LONGITUDE_att_fused", TrackType.ATT_FUSED.longitudeColumn)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("ATT Fused", context.getString(R.string.track_type_att_fused))
    }

    @Test
    fun testMyPreferenceManager_persistsHiddenChartsAndIgnoresUnknownTokens() = runBlocking {
        // TST-PER-052.4
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefManager = MyPreferenceManager(context)
        prefManager.dataStore.edit { it.remove(MyPreferenceManager.HIDDEN_TELEMETRY_CHARTS) }

        assertEquals(emptySet<TelemetryChartKind>(), prefManager.hiddenTelemetryChartsFlow.first())
        assertEquals(TelemetryChartKind.entries.toSet(), prefManager.visibleTelemetryChartsFlow.first())

        prefManager.setTelemetryChartVisible(TelemetryChartKind.SLOPE, false)
        prefManager.setTelemetryChartVisible(TelemetryChartKind.CADENCE, false)

        val hidden = prefManager.hiddenTelemetryChartsFlow.first()
        assertEquals(setOf(TelemetryChartKind.SLOPE, TelemetryChartKind.CADENCE), hidden)
        assertFalse(TelemetryChartKind.SLOPE in prefManager.visibleTelemetryChartsFlow.first())

        // Inject unknown / corrupt future enum tokens directly into DataStore and verify safe decoding
        prefManager.dataStore.edit { prefs ->
            prefs[MyPreferenceManager.HIDDEN_TELEMETRY_CHARTS] = setOf(
                "UNKNOWN_FUTURE_CHART",
                "slope_lowercase",
                "",
                TelemetryChartKind.CADENCE.name
            )
        }
        assertEquals(setOf(TelemetryChartKind.CADENCE), prefManager.hiddenTelemetryChartsFlow.first())
        assertFalse(TelemetryChartKind.CADENCE in prefManager.visibleTelemetryChartsFlow.first())
        assertTrue(TelemetryChartKind.SLOPE in prefManager.visibleTelemetryChartsFlow.first())

        prefManager.setTelemetryChartVisible(TelemetryChartKind.CADENCE, true)
        prefManager.dataStore.edit { it.remove(MyPreferenceManager.HIDDEN_TELEMETRY_CHARTS) }
        assertEquals(emptySet<TelemetryChartKind>(), prefManager.hiddenTelemetryChartsFlow.first())
    }

    @Test
    fun testTelemetryChartStack_togglesPerChartVisibilityAndShowsEmptyHintWhenAllHidden() {
        // TST-PER-052.4
        val path = buildSamplePath()
        composeTestRule.setContent {
            var hiddenCharts by remember { mutableStateOf(emptySet<TelemetryChartKind>()) }
            ATrainingTrackerTheme {
                Surface {
                    TelemetryChartStack(
                        path = path,
                        bSportType = BSportType.BIKE,
                        tuningConfig = TuningConfig(),
                        selectedDistance = 500.0,
                        onDistanceSelected = {},
                        activeTelemetryDomain = ProfileXAxisDomain.DISTANCE,
                        isTrackless = false,
                        zoomScale = 1.0f,
                        startDist = 0.0,
                        isPanMode = false,
                        onZoomChanged = { _, _ -> },
                        chartModifier = Modifier.fillMaxWidth(),
                        hiddenCharts = hiddenCharts,
                        onToggleChartVisibility = { kind ->
                            hiddenCharts = if (kind in hiddenCharts) hiddenCharts - kind else hiddenCharts + kind
                        }
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("telemetry_chart_section_SPEED_PACE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("telemetry_chart_section_SLOPE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("telemetry_chart_section_HEART_RATE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("telemetry_chart_section_CADENCE").assertIsDisplayed()

        // Toggle Slope off via chart visibility filter
        composeTestRule.onNodeWithTag("charts_visibility_button").performClick()
        composeTestRule.onNodeWithTag("charts_visibility_menu_item_SLOPE").performClick()
        composeTestRule.onNodeWithTag("telemetry_chart_section_SLOPE").assertDoesNotExist()
        composeTestRule.onNodeWithTag("telemetry_chart_section_HEART_RATE").assertIsDisplayed()

        // Hide remaining charts and verify filter remains reachable with empty hint
        for (kind in listOf(
            TelemetryChartKind.SPEED_PACE,
            TelemetryChartKind.HEART_RATE,
            TelemetryChartKind.POWER,
            TelemetryChartKind.CADENCE
        )) {
            composeTestRule.onNodeWithTag("charts_visibility_menu_item_${kind.name}").performClick()
        }
        composeTestRule.onNodeWithTag("charts_visibility_empty_hint").assertIsDisplayed()
        composeTestRule.onNodeWithTag("charts_visibility_button").assertIsDisplayed()
    }

    @Test
    fun testSpeedPaceAndSlopeOverlays_convertUnitsAndToggleLegend() {
        // TST-PER-052.3
        val convertedSpeedKmh = resolveOverlayValueForChart(5.0, TelemetryMetricType.SPEED, MyUnits.METRIC)!!
        assertEquals(18.0, convertedSpeedKmh, 0.01)
        val convertedSpeedMph = resolveOverlayValueForChart(5.0, TelemetryMetricType.SPEED, MyUnits.IMPERIAL)!!
        assertEquals(11.1846, convertedSpeedMph, 0.02)

        val convertedPaceMinPerKm = resolveOverlayValueForChart(1000.0 / 300.0, TelemetryMetricType.PACE, MyUnits.METRIC)!!
        assertEquals(5.0, convertedPaceMinPerKm, 0.01)

        val path = buildSamplePath()
        val overlays = buildOverlays()
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    TelemetryChartStack(
                        path = path,
                        bSportType = BSportType.BIKE,
                        tuningConfig = TuningConfig(),
                        selectedDistance = 500.0,
                        onDistanceSelected = {},
                        activeTelemetryDomain = ProfileXAxisDomain.DISTANCE,
                        isTrackless = false,
                        zoomScale = 1.0f,
                        startDist = 0.0,
                        isPanMode = false,
                        onZoomChanged = { _, _ -> },
                        chartModifier = Modifier.fillMaxWidth(),
                        multiSensorOverlays = overlays
                    )
                }
            }
        }

        // Toggle Speed and Slope overlay layers
        composeTestRule.onNodeWithTag("multi_source_layers_button_SPEED").performClick()
        composeTestRule.onNodeWithText("ATT Fused (", substring = true).performClick()
        composeTestRule.onNodeWithTag("multi_source_mean_legend_SPEED").assertIsDisplayed()

        composeTestRule.onNodeWithTag("multi_source_layers_button_SLOPE").performClick()
        composeTestRule.onNodeWithText("Barometer", substring = true).performClick()
        composeTestRule.onNodeWithTag("multi_source_mean_legend_SLOPE").assertIsDisplayed()
    }

    @Test
    fun testGermanLocaleFormatting_usesCommasUniformlyAcrossHeadersAndOverlays() {
        // TST-PER-052.3 (ATT-8080 Bounce 1 Fix 2)
        val originalLocale = Locale.getDefault()
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        try {
            Locale.setDefault(Locale.GERMANY)
            val point = PathPoint(
                distance = 100.0,
                latLng = LatLng(48.10, 11.50),
                altitude = 1500.0,
                timeSec = 10L,
                power = 220,
                speedMps = 7.0,
                slope = -2.3,
                wPrimeBalKj = 12.4,
                hrvDfaA1 = 0.78,
                hrvRmssd = 42.3
            )
            val speedOverlays = listOf(
                TelemetrySourceSeries(
                    metricType = TelemetryMetricType.SPEED,
                    sourceName = "ATT Fused",
                    columnName = "SPEED_mps_att_fused",
                    points = listOf(TelemetrySourcePoint(100.0, 10L, 7.4)),
                    meanValue = 7.4
                )
            )
            val primarySpeed = formatPrimaryActiveValueText(
                activePoint = point,
                metricType = TelemetryMetricType.SPEED,
                hrZoneThresholds = null,
                powerZoneThresholds = null,
                unit = MyUnits.METRIC,
                speedFormatter = SpeedFormatter(),
                locale = Locale.GERMANY
            )
            assertEquals("25,2 km/h", primarySpeed)

            val speedHeader = formatTelemetryHeaderWithOverlays(
                baseHeading = "Geschwindigkeit",
                activePrimaryText = primarySpeed,
                metricType = TelemetryMetricType.SPEED,
                activeOverlays = speedOverlays,
                overlayScrubTarget = 100.0,
                isTimeDomain = false,
                unit = MyUnits.METRIC,
                locale = Locale.GERMANY
            )
            assertTrue("Expected comma decimal in primary & overlay speed: $speedHeader", speedHeader.contains("25,2 km/h") && speedHeader.contains("26,6 km/h"))
            assertFalse("Must not contain dot decimals in German speed header: $speedHeader", speedHeader.contains('.'))

            val slopePrimary = formatPrimaryActiveValueText(
                activePoint = point,
                metricType = TelemetryMetricType.SLOPE,
                hrZoneThresholds = null,
                powerZoneThresholds = null,
                unit = MyUnits.METRIC,
                locale = Locale.GERMANY
            )
            assertEquals("-2,3%", slopePrimary)
            assertEquals("1.500 m", formatOverlayScalarWithUnit(1500.0, TelemetryMetricType.ALTITUDE, MyUnits.METRIC, locale = Locale.GERMANY))
            assertEquals("5,0 kJ", formatOverlayScalarWithUnit(5.0, TelemetryMetricType.W_PRIME_BAL, MyUnits.METRIC, locale = Locale.GERMANY))
            assertEquals("0,75", formatOverlayScalarWithUnit(0.75, TelemetryMetricType.HRV_DFA_A1, MyUnits.METRIC, locale = Locale.GERMANY))
            assertEquals("42,3 ms", formatOverlayScalarWithUnit(42.3, TelemetryMetricType.HRV_RMSSD, MyUnits.METRIC, locale = Locale.GERMANY))

            val stackPath = listOf(
                point,
                point.copy(distance = 200.0, timeSec = 20L, speedMps = 7.5, slope = 4.2, wPrimeBalKj = 11.8, hrvDfaA1 = 0.81, hrvRmssd = 39.6)
            )
            composeTestRule.setContent {
                val deConfig = Configuration(LocalConfiguration.current).apply {
                    setLocales(LocaleList(Locale.GERMANY))
                }
                CompositionLocalProvider(LocalConfiguration provides deConfig) {
                    ATrainingTrackerTheme {
                        Surface {
                            TelemetryChartStack(
                                path = stackPath,
                                bSportType = BSportType.BIKE,
                                tuningConfig = TuningConfig(enableCriticalPowerModel = true),
                                selectedDistance = 100.0,
                                onDistanceSelected = {},
                                activeTelemetryDomain = ProfileXAxisDomain.DISTANCE,
                                isTrackless = false,
                                zoomScale = 1.0f,
                                startDist = 0.0,
                                isPanMode = false,
                                onZoomChanged = { _, _ -> },
                                chartModifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            composeTestRule.onNodeWithText("25,2 km/h", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("-2,3%", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("12,4 kJ", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("0,78", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("42,3 ms", substring = true).assertIsDisplayed()
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun testLandscapeModeWithoutElevationProfile_visibleChartsDoesNotOverlapZoomToolbar() {
        // TST-PER-052.4 (ATT-8080 Bounce 1 Fix 3)
        val path = buildSamplePath()
        composeTestRule.setContent {
            val landscapeConfig = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            CompositionLocalProvider(LocalConfiguration provides landscapeConfig) {
                ATrainingTrackerTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MapDetailLayout(
                            bSportType = BSportType.BIKE,
                            zoomFocus = MapZoomFocus.FIT_PRIMARY,
                            activeScrubPath = path,
                            header = { Text("Workout Header") },
                            showMap = false,
                            showElevationProfile = false,
                            showTelemetryCharts = true,
                            showZoomControls = true
                        )
                    }
                }
            }
        }

        val toolbarBounds = composeTestRule.onNodeWithTag("global_telemetry_zoom_toolbar")
            .fetchSemanticsNode()
            .boundsInRoot
        val visibleChartsBounds = composeTestRule.onNodeWithTag("charts_visibility_button")
            .fetchSemanticsNode()
            .boundsInRoot

        assertFalse(
            "Visible Charts ($visibleChartsBounds) must not overlap GlobalTelemetryZoomToolbar ($toolbarBounds)",
            toolbarBounds.overlaps(visibleChartsBounds)
        )
        assertTrue(
            "Visible Charts top (${visibleChartsBounds.top}) must sit below GlobalTelemetryZoomToolbar bottom (${toolbarBounds.bottom})",
            toolbarBounds.bottom <= visibleChartsBounds.top
        )

        composeTestRule.onNodeWithTag("charts_visibility_button").performClick()
        composeTestRule.onNodeWithTag("charts_visibility_menu_item_SLOPE").assertIsDisplayed()
    }

    @Test
    fun testCaptureMultiSourceChartsAndVisibilityScreenshot() {
        // TST-PER-052.5 (Rule 8.5)
        val path = buildSamplePath()
        val overlays = buildOverlays()
        val scrubDist = 500.0
        val altEnabled = setOf("ALTITUDE_gps", "ALTITUDE (att_fused_altitude_slope)")
        val speedEnabled = setOf("SPEED_mps_gps", "SPEED_mps_att_fused")
        val slopeEnabled = setOf("SLOPE (att_fused_altitude_slope)", "SLOPE (Barometer)")

        composeTestRule.setContent {
            Column(
                modifier = Modifier
                    .width(1240.dp)
                    .background(Color(0xFF777777))
                    .padding(10.dp)
                    .testTag("att8080_multisource_visibility_comparison"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                        ComparisonPanel(
                            title = "Light Theme",
                            path = path,
                            overlays = overlays,
                            scrubDist = scrubDist,
                            altEnabled = altEnabled,
                            speedEnabled = speedEnabled,
                            slopeEnabled = slopeEnabled
                        )
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                        ComparisonPanel(
                            title = "Dark Theme",
                            path = path,
                            overlays = overlays,
                            scrubDist = scrubDist,
                            altEnabled = altEnabled,
                            speedEnabled = speedEnabled,
                            slopeEnabled = slopeEnabled
                        )
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("att8080_multisource_visibility_comparison")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8080_multisource_charts_visibility.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Screenshot must exist", outFile.exists())
        assertTrue("Screenshot must be non-empty", outFile.length() > 10_000L)
    }

    @Composable
    private fun ComparisonPanel(
        title: String,
        path: List<PathPoint>,
        overlays: Map<TelemetryMetricType, List<TelemetrySourceSeries>>,
        scrubDist: Double,
        altEnabled: Set<String>,
        speedEnabled: Set<String>,
        slopeEnabled: Set<String>
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "ATT-8080 Elevation, Speed & Slope Overlays + Per-Chart Visibility ($title)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ElevationProfile Overlay & Scrubbing",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ElevationProfile(
                        pathPoints = path,
                        currentDistance = scrubDist,
                        showZoomControls = true,
                        showScrubbingBadge = true,
                        alignWithTelemetryCharts = true,
                        bSportType = BSportType.BIKE,
                        overlaySeries = overlays[TelemetryMetricType.ALTITUDE].orEmpty(),
                        enabledOverlayIds = altEnabled,
                        onToggleOverlay = {}
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Slope (%) with Negative Descents",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TelemetryMetricGraph(
                        pathPoints = path,
                        metricType = TelemetryMetricType.SLOPE,
                        currentDistance = scrubDist,
                        onDistanceSelected = {},
                        bSportType = BSportType.BIKE,
                        overlaySeries = overlays[TelemetryMetricType.SLOPE].orEmpty(),
                        enabledOverlayIds = slopeEnabled,
                        onToggleOverlay = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CHARTS Visibility Toggle + Speed Overlay",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TelemetryChartStack(
                        path = path,
                        bSportType = BSportType.BIKE,
                        tuningConfig = TuningConfig(),
                        selectedDistance = scrubDist,
                        onDistanceSelected = {},
                        activeTelemetryDomain = ProfileXAxisDomain.DISTANCE,
                        isTrackless = false,
                        zoomScale = 1.0f,
                        startDist = 0.0,
                        isPanMode = false,
                        onZoomChanged = { _, _ -> },
                        chartModifier = Modifier.fillMaxWidth(),
                        hiddenCharts = setOf(
                            TelemetryChartKind.SPEED_PACE,
                            TelemetryChartKind.SLOPE,
                            TelemetryChartKind.HEART_RATE,
                            TelemetryChartKind.POWER
                        ),
                        onToggleChartVisibility = {},
                        multiSensorOverlays = overlays
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Speed (km/h): ATT Fused & GPS Overlays",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TelemetryMetricGraph(
                        pathPoints = path,
                        metricType = TelemetryMetricType.SPEED,
                        currentDistance = scrubDist,
                        onDistanceSelected = {},
                        bSportType = BSportType.BIKE,
                        overlaySeries = overlays[TelemetryMetricType.SPEED].orEmpty(),
                        enabledOverlayIds = speedEnabled,
                        onToggleOverlay = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    private fun buildSamplePath(): List<PathPoint> = (0..60).map { i ->
        val dist = i * 20.0
        val alt = 510.0 + sin(i * 0.14) * 35.0
        val slope = cos(i * 0.14) * 7.5
        val speed = (6.8 + cos(i * 0.12) * 2.2).coerceAtLeast(1.5)
        PathPoint(
            distance = dist,
            latLng = LatLng(48.10 + i * 0.0001, 11.50 + i * 0.0001),
            altitude = alt,
            timeSec = i * 3L,
            hr = 142 + (i % 18),
            power = 215 + (i % 40),
            cadence = 86 + (i % 10),
            speedMps = speed,
            slope = slope
        )
    }

    private fun buildOverlays(): Map<TelemetryMetricType, List<TelemetrySourceSeries>> {
        val altAttPts = (0..60).map { i ->
            TelemetrySourcePoint(i * 20.0, i * 3L, 512.0 + sin(i * 0.14) * 35.0)
        }
        val altGpsPts = (0..60).map { i ->
            TelemetrySourcePoint(i * 20.0, i * 3L, 518.0 + sin(i * 0.14) * 31.0)
        }
        val spdAttPts = (0..60).map { i ->
            TelemetrySourcePoint(i * 20.0, i * 3L, (7.1 + cos(i * 0.12) * 2.1).coerceAtLeast(1.5))
        }
        val spdGpsPts = (0..60).map { i ->
            TelemetrySourcePoint(i * 20.0, i * 3L, (6.5 + cos(i * 0.12) * 2.0).coerceAtLeast(1.5))
        }
        val slopeAttPts = (0..60).map { i ->
            TelemetrySourcePoint(i * 20.0, i * 3L, cos(i * 0.14) * 7.5)
        }
        val slopeBaroPts = (0..60).map { i ->
            TelemetrySourcePoint(i * 20.0, i * 3L, cos(i * 0.14) * 6.2 - 0.5)
        }
        return mapOf(
            TelemetryMetricType.ALTITUDE to listOf(
                TelemetrySourceSeries(TelemetryMetricType.ALTITUDE, "ATT Fused (Alt/Slope)", "ALTITUDE (att_fused_altitude_slope)", altAttPts, 512.0),
                TelemetrySourceSeries(TelemetryMetricType.ALTITUDE, "GPS", "ALTITUDE_gps", altGpsPts, 518.0)
            ),
            TelemetryMetricType.SPEED to listOf(
                TelemetrySourceSeries(TelemetryMetricType.SPEED, "ATT Fused", "SPEED_mps_att_fused", spdAttPts, 7.1),
                TelemetrySourceSeries(TelemetryMetricType.SPEED, "GPS", "SPEED_mps_gps", spdGpsPts, 6.5)
            ),
            TelemetryMetricType.SLOPE to listOf(
                TelemetrySourceSeries(TelemetryMetricType.SLOPE, "ATT Fused (Alt/Slope)", "SLOPE (att_fused_altitude_slope)", slopeAttPts, 0.4),
                TelemetrySourceSeries(TelemetryMetricType.SLOPE, "Barometer", "SLOPE (Barometer)", slopeBaroPts, -0.3)
            )
        )
    }
}
