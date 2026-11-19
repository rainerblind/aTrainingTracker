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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.ui.equipment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WheelPosition
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
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
import kotlin.math.max

/**
 * Behavioral Robolectric Compose UI verification and Light/Dark PNG capture for
 * [WheelPositionSelectionDialog] (`REQ-SEN-032`, `TST-SEN-032`, `REQ-PRO-132`, `Rule 8.5`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class WheelPositionSelectionDialogUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testDialogRendersOnlyFrontAndRear_andInvokesSelectionCallbacks() {
        var selectedPosition: WheelPosition? = null
        var dismissCount = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                WheelPositionSelectionDialog(
                    equipmentName = "Gravel Bike",
                    sensorName = "Speed Sensor 2",
                    currentPosition = WheelPosition.FRONT,
                    isLinked = true,
                    onSelectPosition = { selectedPosition = it },
                    onUnlink = {},
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("wheel_position_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wheel_position_front")
            .assertIsDisplayed()
            .assertIsSelected()
        composeTestRule.onNodeWithTag("wheel_position_rear")
            .assertIsDisplayed()
            .assertIsNotSelected()

        composeTestRule.onNodeWithTag("wheel_position_unspecified").assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.equipment_wheel_position_unspecified))
            .assertDoesNotExist()

        composeTestRule.onNodeWithTag("wheel_position_rear").performClick()
        assertEquals(WheelPosition.REAR, selectedPosition)
        assertEquals(1, dismissCount)
    }

    @Test
    fun testLegacyUnspecifiedPosition_leavesNeitherSelected_preservesUnlinkCancelAndConflictHints() {
        var selectedPosition: WheelPosition? = null
        var unlinkCount = 0
        var dismissCount = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                WheelPositionSelectionDialog(
                    equipmentName = "Road Bike",
                    sensorName = "Garmin Speed",
                    currentPosition = WheelPosition.UNSPECIFIED,
                    isLinked = true,
                    conflictHints = mapOf(WheelPosition.REAR to "Wahoo Rear"),
                    onSelectPosition = { selectedPosition = it },
                    onUnlink = { unlinkCount++ },
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("wheel_position_front").assertIsNotSelected()
        composeTestRule.onNodeWithTag("wheel_position_rear").assertIsNotSelected()
        composeTestRule.onNodeWithTag("wheel_position_unspecified").assertDoesNotExist()

        val expectedConflict = context.getString(R.string.equipment_wheel_position_conflict_swap, "Wahoo Rear")
        composeTestRule.onNodeWithTag("wheel_position_rear_conflict", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains(expectedConflict)

        composeTestRule.onNodeWithTag("wheel_position_cancel").performClick()
        assertEquals(1, dismissCount)
        assertEquals(0, unlinkCount)
        assertEquals(null, selectedPosition)

        composeTestRule.onNodeWithTag("wheel_position_unlink").performClick()
        assertEquals(1, unlinkCount)
        assertEquals(2, dismissCount)

        composeTestRule.onNodeWithTag("wheel_position_front").performClick()
        assertEquals(WheelPosition.FRONT, selectedPosition)
        assertEquals(3, dismissCount)
    }

    @Test
    fun testUnlinkedDialog_hidesUnlinkButton_andSelectsRear() {
        var selectedPosition: WheelPosition? = null
        var dismissCount = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                WheelPositionSelectionDialog(
                    equipmentName = "Gravel Bike",
                    sensorName = "Speed Sensor 2",
                    currentPosition = WheelPosition.UNSPECIFIED,
                    isLinked = false,
                    onSelectPosition = { selectedPosition = it },
                    onUnlink = {},
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("wheel_position_unlink").assertDoesNotExist()
        composeTestRule.onNodeWithTag("wheel_position_unspecified").assertDoesNotExist()
        composeTestRule.onNodeWithTag("wheel_position_front").assertIsNotSelected()
        composeTestRule.onNodeWithTag("wheel_position_rear").assertIsNotSelected()

        composeTestRule.onNodeWithTag("wheel_position_rear").performClick()
        assertEquals(WheelPosition.REAR, selectedPosition)
        assertEquals(1, dismissCount)
    }

    @Test
    fun testLightDarkWheelPositionDialogPreviews_capturePng_rule85() {
        var darkTheme by mutableStateOf(false)

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = darkTheme) {
                WheelPositionSelectionDialog(
                    equipmentName = "Gravel Bike",
                    sensorName = "Speed Sensor 2",
                    currentPosition = WheelPosition.FRONT,
                    isLinked = true,
                    conflictHints = mapOf(WheelPosition.REAR to "Wahoo Rear"),
                    onSelectPosition = {},
                    onUnlink = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        val lightBmp = composeTestRule.onNode(isDialog()).captureToImage().asAndroidBitmap()

        darkTheme = true
        composeTestRule.waitForIdle()
        val darkBmp = composeTestRule.onNode(isDialog()).captureToImage().asAndroidBitmap()

        val gapPx = 24
        val width = max(lightBmp.width, darkBmp.width)
        val height = lightBmp.height + gapPx + darkBmp.height
        val combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)
        canvas.drawColor(android.graphics.Color.GRAY)
        canvas.drawBitmap(lightBmp, ((width - lightBmp.width) / 2f), 0f, null)
        canvas.drawBitmap(darkBmp, ((width - darkBmp.width) / 2f), (lightBmp.height + gapPx).toFloat(), null)

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8859_wheel_position_dialog.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            combined.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }
}
