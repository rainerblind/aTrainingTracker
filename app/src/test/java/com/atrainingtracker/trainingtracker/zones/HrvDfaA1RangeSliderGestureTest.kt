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

package com.atrainingtracker.trainingtracker.zones

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.activities.HrvDfaA1RangeSlider
import com.atrainingtracker.trainingtracker.activities.HrvDfaA1SettingsContent
import com.atrainingtracker.trainingtracker.activities.HrvThresholdControlRow
import com.atrainingtracker.trainingtracker.activities.ZoneSettingsScreen
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.runBlocking
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

/**
 * Behavioral Compose UI gesture and display-options test suite verifying [HrvDfaA1RangeSlider]
 * isolation from [HorizontalPager] tab swipes, bidirectional T1/T2 dragging, local drag buffering,
 * outside-slider tab switching, vertical scroll preservation, HRV tab gauge toggle removal with
 * unconditional preview bar rendering, and Rule 8.5 PNG capture
 * (`REQ-TRK-086.1`..`REQ-TRK-086.3`, `TST-TRK-086.1`..`TST-TRK-086.3`,
 * `REQ-TRK-087.1`, `REQ-TRK-087.2`, `TST-TRK-087.1`, `REQ-PRO-049.1`, `ATT-8105`, `ATT-8712`, `ATT-8848`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class HrvDfaA1RangeSliderGestureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testRightwardAndLeftwardSliderDrags_doNotTriggerHorizontalPagerTabSwitch() {
        var pagerStateRef: PagerState? = null
        var currentT1 = 0.75f
        var currentT2 = 0.50f

        composeTestRule.setContent {
            val pagerState = rememberPagerState(initialPage = 3, pageCount = { 4 })
            pagerStateRef = pagerState
            var isSliderGestureActive by remember { mutableStateOf(false) }
            var t1 by remember { mutableFloatStateOf(0.75f) }
            var t2 by remember { mutableFloatStateOf(0.50f) }

            ATrainingTrackerTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !isSliderGestureActive,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("TestHorizontalPager")
                    ) { page ->
                        if (page == 3) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = context.getString(R.string.hrv_info_zones_title),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .testTag("OutsideSliderHeader")
                                )
                                HrvDfaA1RangeSlider(
                                    t1 = t1,
                                    t2 = t2,
                                    onThresholdsChange = { newT1, newT2 ->
                                        t1 = newT1
                                        t2 = newT2
                                        currentT1 = newT1
                                        currentT2 = newT2
                                    },
                                    onSliderGestureActiveChange = { active ->
                                        isSliderGestureActive = active
                                    }
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("PagerPage$page")
                            ) {
                                Text("Page $page")
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(3, pagerStateRef?.currentPage)

        // 1. Drag right thumb T2 rightward (fraction 0.778 -> 0.96, dx > 0) — previously switched to page 2
        composeTestRule.onNodeWithTag("HrvDfaA1RangeSlider").performTouchInput {
            val y = centerY
            swipe(
                start = Offset(width * 0.778f, y),
                end = Offset(width * 0.96f, y),
                durationMillis = 250
            )
        }
        composeTestRule.waitForIdle()
        assertEquals("Rightward T2 drag must keep pager on tab 3", 3, pagerStateRef?.currentPage)
        assertTrue("Rightward T2 drag must decrease T2 below 0.50 (was $currentT2)", currentT2 < 0.50f)
        assertTrue("Invariant T1 >= T2 + 0.05 must hold", currentT1 >= currentT2 + 0.05f - 0.0001f)

        // 2. Drag T2 leftward (dx < 0)
        val t2BeforeLeft = currentT2
        composeTestRule.onNodeWithTag("HrvDfaA1RangeSlider").performTouchInput {
            val y = centerY
            swipe(
                start = Offset(width * 0.90f, y),
                end = Offset(width * 0.65f, y),
                durationMillis = 250
            )
        }
        composeTestRule.waitForIdle()
        assertEquals("Leftward T2 drag must keep pager on tab 3", 3, pagerStateRef?.currentPage)
        assertTrue("Leftward T2 drag must increase T2 (before=$t2BeforeLeft, after=$currentT2)", currentT2 > t2BeforeLeft)
        assertTrue("Invariant T1 >= T2 + 0.05 must hold", currentT1 >= currentT2 + 0.05f - 0.0001f)

        // 3. Drag left thumb T1 leftward (fraction 0.50 -> 0.22)
        val t1BeforeLeft = currentT1
        composeTestRule.onNodeWithTag("HrvDfaA1RangeSlider").performTouchInput {
            val y = centerY
            swipe(
                start = Offset(width * 0.50f, y),
                end = Offset(width * 0.22f, y),
                durationMillis = 250
            )
        }
        composeTestRule.waitForIdle()
        assertEquals("Leftward T1 drag must keep pager on tab 3", 3, pagerStateRef?.currentPage)
        assertTrue("Leftward T1 drag must increase T1 (before=$t1BeforeLeft, after=$currentT1)", currentT1 > t1BeforeLeft)

        // 4. Drag starting inside slider Card padding horizontally rightward (Card edge slop race)
        composeTestRule.onNodeWithTag("HrvDfaA1RangeSliderCard").performTouchInput {
            swipe(
                start = Offset(width * 0.02f, top + 8f),
                end = Offset(width * 0.85f, top + 8f),
                durationMillis = 200
            )
        }
        composeTestRule.waitForIdle()
        assertEquals("Horizontal drag inside slider Card padding must not switch tabs", 3, pagerStateRef?.currentPage)

        // 5. Horizontal swipe outside the slider Card MUST still switch back to page 2
        composeTestRule.onNodeWithTag("OutsideSliderHeader").performTouchInput {
            swipeRight()
        }
        composeTestRule.waitForIdle()
        assertEquals(
            "Horizontal swipe outside slider Card must switch pager to tab 2 (isSliderGestureActive check)",
            2,
            pagerStateRef?.currentPage
        )
    }

    @Test
    fun testLocalDragState_preventsSnapBackWhenDataStoreUpdatesLag() {
        val emittedPairs = mutableListOf<Pair<Float, Float>>()

        composeTestRule.setContent {
            // Intentionally keep persisted t1/t2 frozen to simulate async DataStore lag mid-drag
            ATrainingTrackerTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    HrvDfaA1RangeSlider(
                        t1 = 0.75f,
                        t2 = 0.50f,
                        onThresholdsChange = { newT1, newT2 ->
                            emittedPairs.add(newT1 to newT2)
                        }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("T2: 0.50").assertIsDisplayed()

        // Multi-step rightward drag on T2 while upstream t1/t2 remain 0.75 / 0.50
        composeTestRule.onNodeWithTag("HrvDfaA1RangeSlider").performTouchInput {
            val y = centerY
            down(Offset(width * 0.778f, y))
            moveBy(Offset(width * 0.06f, 0f))
            moveBy(Offset(width * 0.06f, 0f))
            moveBy(Offset(width * 0.06f, 0f))
            // Assert UI displays progressed T2 without snapping back to 0.50 mid-drag
            up()
        }
        composeTestRule.waitForIdle()

        assertTrue("Expected threshold emissions during laggy drag", emittedPairs.isNotEmpty())
        val finalEmitted = emittedPairs.last()
        assertTrue("Expected T2 to progress below 0.50 despite frozen upstream state, got $finalEmitted", finalEmitted.second < 0.50f)
        assertTrue("Bounds & gap invariant must hold: $finalEmitted", finalEmitted.first >= finalEmitted.second + 0.05f - 0.0001f)
        assertTrue("Lower bound 0.30 must hold: $finalEmitted", finalEmitted.second >= 0.30f)
        assertTrue("Upper bound 1.20 must hold: $finalEmitted", finalEmitted.first <= 1.20f)
    }

    @Test
    fun testZoneSettingsScreen_tab3PreservesVerticalScrollAndOutsideSwipe() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Box(modifier = Modifier.width(412.dp).height(680.dp)) {
                    ZoneSettingsScreen(
                        initialTab = 3,
                        onNavigateToHrvTest = {}
                    )
                }
            }
        }

        // Wait for ZoneSettingsViewModel profiles to hydrate and render tab 3
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(
                androidx.compose.ui.test.hasTestTag("HrvDfaA1RangeSliderCard")
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("HrvDfaA1RangeSliderCard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HrvDfaA1SettingsScrollColumn").assertIsDisplayed()

        val initialSliderTop = composeTestRule.onNodeWithTag("HrvDfaA1RangeSliderCard")
            .fetchSemanticsNode().positionInRoot.y

        // Moderate vertical scroll inside HrvDfaA1SettingsContent keeps slider visible while proving scroll
        composeTestRule.onNodeWithTag("HrvDfaA1SettingsScrollColumn").performTouchInput {
            swipe(
                start = Offset(centerX, height * 0.70f),
                end = Offset(centerX, height * 0.45f),
                durationMillis = 200
            )
        }
        composeTestRule.waitForIdle()

        val scrolledSliderTop = composeTestRule.onNodeWithTag("HrvDfaA1RangeSliderCard")
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Vertical scroll on tab 3 must scroll content upward (before=$initialSliderTop, after=$scrolledSliderTop)",
            scrolledSliderTop < initialSliderTop
        )

        // Horizontal drag on RangeSlider must NOT leave tab 3
        composeTestRule.onNodeWithTag("HrvDfaA1RangeSlider").performTouchInput {
            val y = centerY
            swipe(
                start = Offset(width * 0.778f, y),
                end = Offset(width * 0.94f, y),
                durationMillis = 200
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("HrvDfaA1RangeSliderCard").assertIsDisplayed()

        // Horizontal swipe outside slider Card must switch off tab 3 back to PWR_BIKE (tab 2);
        // beyondViewportPageCount = 1 keeps page 3 composed offscreen, so verify offscreen + tab-2 calculator icon
        composeTestRule.onNodeWithTag("HrvDfaA1SettingsScrollColumn").performTouchInput {
            swipe(
                start = Offset(left + 20f, bottom - 20f),
                end = Offset(right - 20f, bottom - 20f),
                durationMillis = 200
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("HrvDfaA1RangeSliderCard").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.zone_calc_button)).assertIsDisplayed()
    }

    @Test
    fun testHrvThresholdControlRow_withShowSliderTrueTogglesGestureIsolation() {
        val activeEvents = mutableListOf<Boolean>()

        composeTestRule.setContent {
            var value by remember { mutableFloatStateOf(0.75f) }
            ATrainingTrackerTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    HrvThresholdControlRow(
                        title = "T1",
                        value = value,
                        onValueChange = { value = it },
                        valueRange = 0.60f..1.20f,
                        minAllowed = 0.55f,
                        maxAllowed = 1.20f,
                        helperText = "Helper",
                        showSlider = true,
                        onSliderGestureActiveChange = { active ->
                            if (activeEvents.lastOrNull() != active) {
                                activeEvents.add(active)
                            }
                        }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("HrvThresholdControlSlider").performTouchInput {
            val y = centerY
            down(Offset(width * 0.40f, y))
            moveBy(Offset(width * 0.25f, 0f))
            up()
        }
        composeTestRule.waitForIdle()

        assertTrue("Expected gesture isolation activation on press (events=$activeEvents)", activeEvents.contains(true))
        assertFalse("Expected gesture isolation release after pointer up (events=$activeEvents)", activeEvents.last())
    }

    @Test
    fun testCaptureLightAndDarkHrvSliderScreenshot_rule85() {
        composeTestRule.setContent {
            Column(
                modifier = Modifier
                    .width(412.dp)
                    .background(Color(0xFF78909C))
                    .padding(12.dp)
                    .testTag("HrvSliderLightDarkPreviewRoot"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Light — HRV (DFA α1) RangeSlider",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            HrvDfaA1RangeSlider(
                                t1 = 0.75f,
                                t2 = 0.50f,
                                onThresholdsChange = { _, _ -> }
                            )
                            HrvThresholdControlRow(
                                title = context.getString(R.string.hrv_t1_aerobic_threshold_label),
                                value = 0.75f,
                                onValueChange = {},
                                valueRange = 0.60f..1.20f,
                                minAllowed = 0.55f,
                                maxAllowed = 1.20f,
                                helperText = context.getString(R.string.hrv_t1_aerobic_threshold_desc),
                                showSlider = false
                            )
                            HrvThresholdControlRow(
                                title = context.getString(R.string.hrv_t2_anaerobic_threshold_label),
                                value = 0.50f,
                                onValueChange = {},
                                valueRange = 0.30f..0.80f,
                                minAllowed = 0.30f,
                                maxAllowed = 0.70f,
                                helperText = context.getString(R.string.hrv_t2_anaerobic_threshold_desc),
                                showSlider = false
                            )
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Dark — HRV (DFA α1) RangeSlider",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            HrvDfaA1RangeSlider(
                                t1 = 0.75f,
                                t2 = 0.50f,
                                onThresholdsChange = { _, _ -> }
                            )
                            HrvThresholdControlRow(
                                title = context.getString(R.string.hrv_t1_aerobic_threshold_label),
                                value = 0.75f,
                                onValueChange = {},
                                valueRange = 0.60f..1.20f,
                                minAllowed = 0.55f,
                                maxAllowed = 1.20f,
                                helperText = context.getString(R.string.hrv_t1_aerobic_threshold_desc),
                                showSlider = false
                            )
                            HrvThresholdControlRow(
                                title = context.getString(R.string.hrv_t2_anaerobic_threshold_label),
                                value = 0.50f,
                                onValueChange = {},
                                valueRange = 0.30f..0.80f,
                                minAllowed = 0.30f,
                                maxAllowed = 0.70f,
                                helperText = context.getString(R.string.hrv_t2_anaerobic_threshold_desc),
                                showSlider = false
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        val bmp = composeTestRule.onNodeWithTag("HrvSliderLightDarkPreviewRoot")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8105_hrv_slider_zones.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }

    /**
     * `TST-TRK-087.1` (`REQ-TRK-087.1`, `REQ-TRK-087.2`, `REQ-PRO-049.1`, `ATT-8712`, `ATT-8848`):
     * Verifies [HrvDfaA1SettingsContent] omits `zone_display_gauge`, displays the 4 standard
     * toggles, and renders the live preview gauge bar (`showBar = true`) even when `showGauge == false`.
     */
    @Test
    fun testHrvDfaA1SettingsContent_omitsGaugeToggleAndRendersPreviewBar() {
        val settingsDataStore = SettingsDataStore.getInstance(context)
        runBlocking {
            settingsDataStore.saveHrvZoneDisplayOptions(
                ZoneDisplayOptions(
                    showBackground = true,
                    showLeftBar = true,
                    showRightBar = false,
                    showTextColor = true,
                    showGauge = false
                )
            )
        }

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Box(modifier = Modifier.width(412.dp).height(900.dp)) {
                    HrvDfaA1SettingsContent(settingsDataStore = settingsDataStore)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_background))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_left_bar))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_right_bar))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_text_color))
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_gauge))
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("Zonen-Balken")
            .assertDoesNotExist()

        composeTestRule.onNodeWithTag("zone_gauge_bar_container", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    /**
     * `TST-TRK-087.1` (`REQ-TRK-087.2`, `Rule 8.5`, `ATT-8712`, `ATT-8848`):
     * Captures Light and Dark Compose PNG screenshots of [HrvDfaA1SettingsContent] with `showGauge = false`.
     */
    @Test
    fun testCaptureLightAndDarkHrvZoneDisplayScreenshot_rule85() {
        val settingsDataStore = SettingsDataStore.getInstance(context)
        runBlocking {
            settingsDataStore.saveHrvZoneDisplayOptions(
                ZoneDisplayOptions(
                    showBackground = true,
                    showLeftBar = true,
                    showRightBar = false,
                    showTextColor = true,
                    showGauge = false
                )
            )
        }

        composeTestRule.setContent {
            Column(
                modifier = Modifier
                    .width(412.dp)
                    .background(Color(0xFF78909C))
                    .padding(12.dp)
                    .testTag("HrvZoneDisplayLightDarkPreviewRoot"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().height(480.dp)
                    ) {
                        HrvDfaA1SettingsContent(settingsDataStore = settingsDataStore)
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().height(480.dp)
                    ) {
                        HrvDfaA1SettingsContent(settingsDataStore = settingsDataStore)
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("zone_gauge_bar_container", useUnmergedTree = true)[0].performScrollTo()
        composeTestRule.onAllNodesWithTag("zone_gauge_bar_container", useUnmergedTree = true)[1].performScrollTo()
        composeTestRule.waitForIdle()

        val bmp = composeTestRule.onNodeWithTag("HrvZoneDisplayLightDarkPreviewRoot")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8712_hrv_zone_display.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }
}
