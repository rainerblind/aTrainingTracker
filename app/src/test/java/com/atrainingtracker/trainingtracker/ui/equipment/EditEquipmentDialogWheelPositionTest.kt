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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.SimpleSensorInfo
import com.atrainingtracker.trainingtracker.database.WheelPosition
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Unit and behavioral Robolectric Compose UI verification for [EditEquipmentDialog] wheel mounting
 * position prompting, display, conflict swap, unlink, and non-wheel sensor passthrough
 * (`REQ-SEN-035`, `TST-SEN-035.1`, `TST-SEN-035.2`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class EditEquipmentDialogWheelPositionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    private val hrmSensor = SimpleSensorInfo(1L, "Garmin HRM", DeviceType.HRM)
    private val speedSensorFront = SimpleSensorInfo(2L, "Wahoo Speed", DeviceType.BIKE_SPEED)
    private val speedCadSensorRear = SimpleSensorInfo(3L, "Sigma Speed+Cad", DeviceType.BIKE_SPEED_AND_CADENCE)
    private val powerSensor = SimpleSensorInfo(4L, "Stages Power", DeviceType.BIKE_POWER)
    private val cadenceSensor = SimpleSensorInfo(5L, "Wahoo Cadence", DeviceType.BIKE_CADENCE)
    private val radarSensor = SimpleSensorInfo(6L, "Garmin Varia", DeviceType.BIKE_RADAR)
    private val thirdSpeedSensor = SimpleSensorInfo(7L, "Garmin Speed 2", DeviceType.BIKE_SPEED)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun emptyStats() = StatsData(
        primaryTitle = "Bike",
        secondaryTitle = "Total",
        totalWorkouts = 0,
        totalDistanceWithUnits = "0 km",
        timeWithUnits = "0 h",
        totalAscentWithUnits = "0 m"
    )

    @Test
    fun testDraftWheelPositionHelpers_conflictHintsSwapAndThirdSensorDemotion_TST_SEN_035_1() {
        val sensors = listOf(hrmSensor, speedSensorFront, speedCadSensorRear, thirdSpeedSensor)

        // 1. Link sensor 2 -> FRONT
        val (sel1, pos1) = applyDraftWheelPositionSelection(2L, WheelPosition.FRONT, emptySet(), emptyMap())
        assertEquals(setOf(2L), sel1)
        assertEquals(mapOf(2L to WheelPosition.FRONT), pos1)

        // Conflict hints for sensor 3 show sensor 2 on FRONT
        val hintsFor3 = computeWheelPositionConflictHints(3L, sel1, pos1, sensors)
        assertEquals(mapOf(WheelPosition.FRONT to "Wahoo Speed"), hintsFor3)

        // 2. Link sensor 3 onto occupied FRONT -> sensor 2 automatically moves to free REAR
        val (sel2, pos2) = applyDraftWheelPositionSelection(3L, WheelPosition.FRONT, sel1, pos1)
        assertEquals(setOf(2L, 3L), sel2)
        assertEquals(mapOf(3L to WheelPosition.FRONT, 2L to WheelPosition.REAR), pos2)

        // 3. Reassign sensor 3 to REAR (occupied by sensor 2) -> exact 2-sensor swap
        val (sel3, pos3) = applyDraftWheelPositionSelection(3L, WheelPosition.REAR, sel2, pos2)
        assertEquals(setOf(2L, 3L), sel3)
        assertEquals(mapOf(2L to WheelPosition.FRONT, 3L to WheelPosition.REAR), pos3)

        // 4. Link 3rd wheel sensor 7 onto FRONT when BOTH FRONT and REAR are occupied -> displaced sensor 2 demotes to UNSPECIFIED
        val (sel4, pos4) = applyDraftWheelPositionSelection(7L, WheelPosition.FRONT, sel3, pos3)
        assertEquals(setOf(2L, 3L, 7L), sel4)
        assertEquals(
            mapOf(7L to WheelPosition.FRONT, 3L to WheelPosition.REAR, 2L to WheelPosition.UNSPECIFIED),
            pos4
        )

        // 5. Summary formatting appends wheel positions strictly on bikes for wheel speed sensors
        val bikeSummary = formatSensorSummaryText(
            allSensors = listOf(hrmSensor, speedSensorFront, speedCadSensorRear),
            selectedIds = setOf(1L, 2L, 3L),
            wheelPositions = mapOf(2L to WheelPosition.FRONT, 3L to WheelPosition.REAR),
            isBike = true,
            frontLabel = "Front Wheel",
            rearLabel = "Rear Wheel",
            unspecifiedLabel = "Unspecified Position",
            emptyLabel = "None"
        )
        assertEquals("Garmin HRM, Wahoo Speed (Front Wheel), Sigma Speed+Cad (Rear Wheel)", bikeSummary)

        val shoeSummary = formatSensorSummaryText(
            allSensors = listOf(hrmSensor, speedSensorFront),
            selectedIds = setOf(1L, 2L),
            wheelPositions = mapOf(2L to WheelPosition.FRONT),
            isBike = false,
            frontLabel = "Front Wheel",
            rearLabel = "Rear Wheel",
            unspecifiedLabel = "Unspecified Position",
            emptyLabel = "None"
        )
        assertEquals("Garmin HRM, Wahoo Speed", shoeSummary)
    }

    @Test
    fun testAddBike_tappingWheelSpeedSensorPromptsDialogAndPersistsFrontRearOnSave_TST_SEN_035_1() {
        val newBike = EquipmentItem(
            id = -1L,
            name = "Gravel Bike",
            linkedDeviceIds = emptyList(),
            linkedDeviceNames = "",
            linkedSportTypeIds = emptyList(),
            linkedSportTypeNames = "",
            frameType = 2,
            stravaName = null,
            stravaId = null,
            firstUsed = null,
            lastUsed = null,
            statsData = emptyStats()
        )
        var savedItem: EquipmentItem? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                EditEquipmentDialog(
                    item = newBike,
                    availableSensors = listOf(hrmSensor, speedSensorFront, speedCadSensorRear, powerSensor),
                    availableSportTypes = emptyList(),
                    onConfirm = { savedItem = it },
                    onDismiss = {}
                )
            }
        }

        // Open sensor dropdown & tap unlinked BIKE_SPEED sensor -> prompts WheelPositionSelectionDialog (without Unlink)
        composeTestRule.onNodeWithTag("edit_equipment_sensor_field").performClick()
        composeTestRule.onNodeWithTag("edit_equipment_sensor_row_2").performClick()
        composeTestRule.onNodeWithTag("wheel_position_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wheel_position_unlink").assertDoesNotExist()

        // Choose Front Wheel
        composeTestRule.onNodeWithTag("wheel_position_front").performClick()
        composeTestRule.onNodeWithTag("wheel_position_dialog").assertDoesNotExist()

        val frontLabel = context.getString(R.string.equipment_wheel_position_front)
        val rearLabel = context.getString(R.string.equipment_wheel_position_rear)
        composeTestRule.onNodeWithTag("edit_equipment_sensor_field")
            .assertTextContains("Wahoo Speed ($frontLabel)", substring = true)

        // Reopen dropdown -> verify badge pill is shown on sensor 2, then tap BIKE_SPEED_AND_CADENCE sensor 3
        composeTestRule.onNodeWithTag("edit_equipment_sensor_field").performClick()
        composeTestRule.onNodeWithTag("edit_equipment_sensor_badge_2", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_equipment_sensor_row_3").performClick()

        // Conflict hint on Front Wheel must mention "Wahoo Speed"
        val expectedConflict = context.getString(R.string.equipment_wheel_position_conflict_swap, "Wahoo Speed")
        composeTestRule.onNodeWithTag("wheel_position_front_conflict", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains(expectedConflict)

        // Choose Rear Wheel
        composeTestRule.onNodeWithTag("wheel_position_rear").performClick()
        composeTestRule.onNodeWithTag("edit_equipment_sensor_field")
            .assertTextContains("Sigma Speed+Cad ($rearLabel)", substring = true)

        // Save -> assert linkedDeviceIds & linkedDeviceWheelPositions persisted in callback
        composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
        assertNotNull("onConfirm must fire on Save", savedItem)
        assertEquals(setOf(2L, 3L), savedItem!!.linkedDeviceIds.toSet())
        assertEquals(
            mapOf(2L to WheelPosition.FRONT, 3L to WheelPosition.REAR),
            savedItem!!.linkedDeviceWheelPositions
        )
    }

    @Test
    fun testEditBike_conflictSwapUnlinkAndPreservesUntouchedPositions_TST_SEN_035_1() {
        val existingBike = EquipmentItem(
            id = 42L,
            name = "Aero Road",
            linkedDeviceIds = listOf(1L, 2L, 3L),
            linkedDeviceNames = "Garmin HRM, Wahoo Speed, Sigma Speed+Cad",
            linkedSportTypeIds = emptyList(),
            linkedSportTypeNames = "",
            frameType = 3,
            stravaName = null,
            stravaId = null,
            firstUsed = null,
            lastUsed = null,
            statsData = emptyStats(),
            linkedDeviceWheelPositions = mapOf(
                2L to WheelPosition.FRONT,
                3L to WheelPosition.REAR
            )
        )
        var savedItem: EquipmentItem? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                EditEquipmentDialog(
                    item = existingBike,
                    availableSensors = listOf(hrmSensor, speedSensorFront, speedCadSensorRear, powerSensor),
                    availableSportTypes = emptyList(),
                    onConfirm = { savedItem = it },
                    onDismiss = {}
                )
            }
        }

        // Tap sensor 2 (currently FRONT) -> verify FRONT selected & conflict hint on REAR -> select REAR to swap!
        composeTestRule.onNodeWithTag("edit_equipment_sensor_field").performClick()
        composeTestRule.onNodeWithTag("edit_equipment_sensor_row_2").performClick()
        composeTestRule.onNodeWithTag("wheel_position_front").assertIsSelected()
        composeTestRule.onNodeWithTag("wheel_position_rear_conflict", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("wheel_position_rear").performClick()

        // Tap sensor 3 (now swapped to FRONT) -> unlink it while keeping sensor 2 as REAR
        composeTestRule.onNodeWithTag("edit_equipment_sensor_field").performClick()
        composeTestRule.onNodeWithTag("edit_equipment_sensor_row_3").performClick()
        composeTestRule.onNodeWithTag("wheel_position_front").assertIsSelected()
        composeTestRule.onNodeWithTag("wheel_position_unlink").performClick()

        // Toggle non-wheel sensor 4 (Stages Power) directly without opening wheel dialog -> sensor 2 stays REAR
        composeTestRule.onNodeWithTag("edit_equipment_sensor_field").performClick()
        composeTestRule.onNodeWithTag("edit_equipment_sensor_row_4").performClick()
        composeTestRule.onNodeWithTag("wheel_position_dialog").assertDoesNotExist()

        // Dismiss dropdown before tapping Save
        composeTestRule.onNodeWithTag("edit_equipment_sensor_field").performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()

        assertNotNull(savedItem)
        assertEquals(setOf(1L, 2L, 4L), savedItem!!.linkedDeviceIds.toSet())
        assertEquals(mapOf(2L to WheelPosition.REAR), savedItem!!.linkedDeviceWheelPositions)
    }

    @Test
    fun testNonWheelSensorsAndShoes_toggleDirectlyWithoutWheelPositionDialog_TST_SEN_035_2() {
        val bike = EquipmentItem(
            id = 5L,
            name = "Commuter",
            linkedDeviceIds = emptyList(),
            linkedDeviceNames = "",
            linkedSportTypeIds = emptyList(),
            linkedSportTypeNames = "",
            frameType = 2,
            stravaName = null,
            stravaId = null,
            firstUsed = null,
            lastUsed = null,
            statsData = emptyStats()
        )
        var savedBike: EquipmentItem? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                EditEquipmentDialog(
                    item = bike,
                    availableSensors = listOf(hrmSensor, powerSensor, cadenceSensor, radarSensor),
                    availableSportTypes = emptyList(),
                    onConfirm = { savedBike = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("edit_equipment_sensor_field").performClick()
        for (sensorId in listOf(1L, 4L, 5L, 6L)) {
            composeTestRule.onNodeWithTag("edit_equipment_sensor_row_$sensorId").performClick()
            composeTestRule.onNodeWithTag("wheel_position_dialog").assertDoesNotExist()
        }
        composeTestRule.onNodeWithTag("edit_equipment_sensor_field").performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()

        assertNotNull(savedBike)
        assertEquals(setOf(1L, 4L, 5L, 6L), savedBike!!.linkedDeviceIds.toSet())
        assertTrue("Non-wheel sensors must not populate wheel positions", savedBike!!.linkedDeviceWheelPositions.isEmpty())
    }
}
