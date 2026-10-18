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

package com.atrainingtracker.trainingtracker.ui.tracking

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.graphics.Color
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager.TrackingViewsDbHelper.RowData
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests verifying Bike Power cockpit sensor catalog availability,
 * 4-tab default layouts (Standard, Power, Map, Laps), non-destructive Power tab seeding,
 * view-only physiological model gating with round-trip restoration, all-gated empty-state
 * resilience, and 9-locale localization parity for `R.string.tab_power`
 * (`REQ-UI-409` / `TST-UI-372` / `ATT-3921`).
 */
class BikePowerCockpitDefaultsAndGatingTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getString(R.string.text_default) } returns "Standard"
        every { mockContext.getString(R.string.tab_power) } returns "Power"
        every { mockContext.getString(R.string.tab_map) } returns "Map"
        every { mockContext.getString(R.string.tab_laps) } returns "Laps"

        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } returns Unit
        every { anyConstructed<ContentValues>().clear() } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun findProjectRoot(): File {
        var dir: File = File(".").canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, "gradlew").exists() && File(dir, "app").exists()) {
                return dir
            }
            dir = dir.parentFile!!
        }
        return File(".").canonicalFile
    }

    /**
     * TST-UI-372.1: Verifies that `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, context)`
     * includes `W_PRIME_BAL` alongside `POWER`, `POWER_TO_WEIGHT`, `NORMALIZED_POWER`,
     * `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, `PEDAL_POWER_BALANCE`, and `TORQUE`.
     */
    @Test
    fun testBikePowerSensorCatalog_containsWPrimeBalAndAllPowerMetrics() {
        val bikePowerSensors = ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, null).toList()

        assertTrue("BIKE_POWER catalog must contain POWER", bikePowerSensors.contains(SensorType.POWER))
        assertTrue("BIKE_POWER catalog must contain POWER_TO_WEIGHT", bikePowerSensors.contains(SensorType.POWER_TO_WEIGHT))
        assertTrue("BIKE_POWER catalog must contain NORMALIZED_POWER", bikePowerSensors.contains(SensorType.NORMALIZED_POWER))
        assertTrue("BIKE_POWER catalog must contain INTENSITY_FACTOR", bikePowerSensors.contains(SensorType.INTENSITY_FACTOR))
        assertTrue("BIKE_POWER catalog must contain TSS", bikePowerSensors.contains(SensorType.TSS))
        assertTrue("BIKE_POWER catalog must contain W_PRIME_BAL", bikePowerSensors.contains(SensorType.W_PRIME_BAL))
        assertTrue("BIKE_POWER catalog must contain WORK_KJ", bikePowerSensors.contains(SensorType.WORK_KJ))
        assertTrue("BIKE_POWER catalog must contain PEDAL_POWER_BALANCE", bikePowerSensors.contains(SensorType.PEDAL_POWER_BALANCE))
        assertTrue("BIKE_POWER catalog must contain TORQUE", bikePowerSensors.contains(SensorType.TORQUE))

        // Verify W_PRIME_BAL is absent from non-power activity types
        val runSensors = ActivityType.getSensorTypeArray(ActivityType.RUN_SPEED_AND_CADENCE, null).toList()
        val bikeCadenceSensors = ActivityType.getSensorTypeArray(ActivityType.BIKE_SPEED_AND_CADENCE, null).toList()
        assertFalse("RUN_SPEED_AND_CADENCE must not contain W_PRIME_BAL", runSensors.contains(SensorType.W_PRIME_BAL))
        assertFalse("BIKE_SPEED_AND_CADENCE must not contain W_PRIME_BAL", bikeCadenceSensors.contains(SensorType.W_PRIME_BAL))
    }

    /**
     * TST-UI-372.2: Verifies the 4 default tracking tabs for `ActivityType.BIKE_POWER`
     * (Standard, Power, Map, Laps) and their exact row/column/ViewSize sensor layouts.
     */
    @Test
    fun testBikePowerDefaultTabs_standardPowerMapAndLapsLayouts() {
        val dbHelper = spyk(TrackingViewsDatabaseManager.TrackingViewsDbHelper(mockContext))
        every { mockDb.insert(any(), any(), any()) } returns 1L

        // 1. Verify Standard Tab (Layout 1) 6-row layout
        val standardRows = dbHelper.createViewMap()[ActivityType.BIKE_POWER]!!
        val expectedStandard = listOf(
            RowData(SensorType.TIME_ACTIVE, ViewSize.SMALL, 1, 1),
            RowData(SensorType.TIME_OF_DAY, ViewSize.SMALL, 1, 2),
            RowData(SensorType.SPEED_mps, ViewSize.NORMAL, 2, 1),
            RowData(SensorType.DISTANCE_m, ViewSize.NORMAL, 2, 2),
            RowData(SensorType.HR, ViewSize.LARGE, 3, 1),
            RowData(SensorType.CADENCE, ViewSize.LARGE, 3, 2),
            RowData(SensorType.POWER, ViewSize.HUGE, 4, 1),
            RowData(SensorType.POWER_TO_WEIGHT, ViewSize.NORMAL, 5, 1),
            RowData(SensorType.NORMALIZED_POWER, ViewSize.NORMAL, 5, 2),
            RowData(SensorType.ALTITUDE, ViewSize.NORMAL, 6, 1),
            RowData(SensorType.ASCENT, ViewSize.NORMAL, 6, 2)
        )
        assertEquals("Standard tab layout must match REQ-UI-409.2", expectedStandard, standardRows)

        // 2. Verify Dedicated Power Tab (Layout 2) 6-row layout
        val powerRows = dbHelper.defaultPowerRowDataList
        val expectedPower = listOf(
            RowData(SensorType.TIME_ACTIVE, ViewSize.SMALL, 1, 1),
            RowData(SensorType.HR, ViewSize.SMALL, 1, 2),
            RowData(SensorType.CADENCE, ViewSize.SMALL, 1, 3),
            RowData(SensorType.POWER, ViewSize.HUGE, 2, 1),
            RowData(SensorType.POWER_TO_WEIGHT, ViewSize.LARGE, 3, 1),
            RowData(SensorType.NORMALIZED_POWER, ViewSize.LARGE, 3, 2),
            RowData(SensorType.INTENSITY_FACTOR, ViewSize.NORMAL, 4, 1),
            RowData(SensorType.TSS, ViewSize.NORMAL, 4, 2),
            RowData(SensorType.WORK_KJ, ViewSize.NORMAL, 4, 3),
            RowData(SensorType.W_PRIME_BAL, ViewSize.LARGE, 5, 1),
            RowData(SensorType.PEDAL_POWER_BALANCE, ViewSize.SMALL, 6, 1),
            RowData(SensorType.TORQUE, ViewSize.SMALL, 6, 2)
        )
        assertEquals("Power tab layout must match REQ-UI-409.2", expectedPower, powerRows)

        // 3. Verify Map Tab (Layout 3) 3-row header layout for BIKE_POWER
        val mapRows = dbHelper.getDefaultMapRowDataList(ActivityType.BIKE_POWER)
        val expectedMap = listOf(
            RowData(SensorType.TIME_ACTIVE, ViewSize.NORMAL, 1, 1),
            RowData(SensorType.DISTANCE_m, ViewSize.NORMAL, 1, 2),
            RowData(SensorType.SPEED_mps, ViewSize.NORMAL, 2, 1),
            RowData(SensorType.HR, ViewSize.NORMAL, 2, 2),
            RowData(SensorType.POWER, ViewSize.NORMAL, 3, 1),
            RowData(SensorType.POWER_TO_WEIGHT, ViewSize.NORMAL, 3, 2)
        )
        assertEquals("Map tab layout for BIKE_POWER must match REQ-UI-409.2", expectedMap, mapRows)

        // 4. Verify Laps Tab (Layout 4) 5-row layout for BIKE_POWER
        val lapRows = dbHelper.getDefaultLapRowDataList(ActivityType.BIKE_POWER)
        val expectedLaps = listOf(
            RowData(SensorType.TIME_LAP, ViewSize.LARGE, 1, 1),
            RowData(SensorType.LAP_NR, ViewSize.SMALL, 1, 2),
            RowData(SensorType.SPEED_mps, ViewSize.NORMAL, 2, 1),
            RowData(SensorType.DISTANCE_m_LAP, ViewSize.NORMAL, 2, 2),
            RowData(SensorType.HR, ViewSize.NORMAL, 3, 1),
            RowData(SensorType.CADENCE, ViewSize.NORMAL, 3, 2),
            RowData(SensorType.POWER, ViewSize.LARGE, 4, 1),
            RowData(SensorType.POWER_TO_WEIGHT, ViewSize.NORMAL, 4, 2),
            RowData(SensorType.ALTITUDE, ViewSize.NORMAL, 5, 1),
            RowData(SensorType.ASCENT, ViewSize.NORMAL, 5, 2)
        )
        assertEquals("Laps tab layout for BIKE_POWER must match REQ-UI-409.2", expectedLaps, lapRows)

        // 5. Verify onCreate seeds all 4 tabs in order for BIKE_POWER
        dbHelper.onCreate(mockDb)
        verify(exactly = 1) {
            dbHelper.addDefaultTab(mockDb, ActivityType.BIKE_POWER, "Standard", 1, false, false, true, true, true, expectedStandard)
            dbHelper.addDefaultTab(mockDb, ActivityType.BIKE_POWER, "Power", 2, false, false, false, false, true, expectedPower)
            dbHelper.addDefaultTab(mockDb, ActivityType.BIKE_POWER, "Map", 3, false, true, false, false, true, expectedMap)
            dbHelper.addDefaultTab(mockDb, ActivityType.BIKE_POWER, "Laps", 4, true, false, false, false, false, expectedLaps)
        }
    }

    /**
     * Verifies that `ensurePowerTabExistsForBikePower` queries `VIEWS_TABLE` via `SELECT` first,
     * inserts the `Power` tab at `layoutNr = 2` (shifting existing `LayoutNr >= 2` up by 1) only when absent,
     * and never executes any destructive `DELETE` when the `Power` tab already exists.
     */
    @Test
    fun testEnsurePowerTabExistsForBikePower_selectsBeforeNonDestructiveInsert() {
        val dbHelper = spyk(TrackingViewsDatabaseManager.TrackingViewsDbHelper(mockContext))
        every { mockDb.insert(any(), any(), any()) } returns 99L

        // Case A: Power tab does not exist yet -> shifts LayoutNr >= 2 and inserts Power tab at LayoutNr = 2
        val emptyCursor = mockk<Cursor>(relaxed = true)
        every { emptyCursor.moveToFirst() } returns false
        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                arrayOf(TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE}=? AND ${TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME}=?",
                arrayOf(ActivityType.BIKE_POWER.name, "Power"),
                null,
                null,
                null
            )
        } returns emptyCursor

        val expectedPowerRows = dbHelper.defaultPowerRowDataList

        dbHelper.ensurePowerTabExistsForBikePower(mockDb)

        verify(exactly = 1) { mockDb.beginTransaction() }
        verify(exactly = 1) {
            mockDb.execSQL(match {
                it.contains("UPDATE ViewsTable SET LayoutNr = LayoutNr + 1") &&
                    it.contains("ActivityType = 'BIKE_POWER'") &&
                    it.contains("LayoutNr >= 2")
            })
        }
        verify(exactly = 1) {
            dbHelper.addDefaultTab(
                mockDb,
                ActivityType.BIKE_POWER,
                "Power",
                2,
                false,
                false,
                false,
                false,
                true,
                expectedPowerRows
            )
        }
        verify(exactly = 0) { mockDb.delete(any(), any(), any()) }
        verify(exactly = 1) { emptyCursor.close() }
        verify(exactly = 1) { mockDb.setTransactionSuccessful() }
        verify(exactly = 1) { mockDb.endTransaction() }

        // Case B: Power tab already exists -> no-op, zero UPDATE or INSERT calls
        val existingCursor = mockk<Cursor>(relaxed = true)
        every { existingCursor.moveToFirst() } returns true
        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                arrayOf(TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE}=? AND ${TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME}=?",
                arrayOf(ActivityType.BIKE_POWER.name, "Power"),
                null,
                null,
                null
            )
        } returns existingCursor

        dbHelper.ensurePowerTabExistsForBikePower(mockDb)
        verify(exactly = 1) { existingCursor.close() }
        // Still only 1 addDefaultTab call from Case A
        verify(exactly = 1) {
            dbHelper.addDefaultTab(
                mockDb,
                ActivityType.BIKE_POWER,
                "Power",
                2,
                false,
                false,
                false,
                false,
                true,
                any()
            )
        }
    }

    private fun createField(
        id: Long,
        row: Int,
        col: Int,
        sensorType: SensorType,
        viewSize: ViewSize = ViewSize.NORMAL
    ): SensorFieldState {
        return SensorFieldState(
            configHash = id.toInt(),
            sensorFieldId = id,
            rowNr = row,
            colNr = col,
            viewSize = viewSize,
            label = sensorType.name,
            filterDescription = "",
            value = "--",
            units = "",
            zoneColor = Color.Transparent,
            sensorType = sensorType
        )
    }

    /**
     * TST-UI-372.3: Verifies that `packVisibleRowsForTuning` is 100% view-only, filters Coggan (`NP`, `IF`, `TSS`)
     * and Critical Power (`W_PRIME_BAL`) metrics based on `TuningConfig`, never mutates `rowNr` or `colNr`
     * on `SensorFieldState`, and restores the exact original layout on an OFF -> ON toggle round-trip.
     */
    @Test
    fun testPackVisibleRowsForTuning_isViewOnlyAndRestoresOriginalRowNrColNrOnToggleRoundTrip() {
        val powerTabFields = listOf(
            createField(1, 1, 1, SensorType.TIME_ACTIVE, ViewSize.SMALL),
            createField(2, 1, 2, SensorType.HR, ViewSize.SMALL),
            createField(3, 1, 3, SensorType.CADENCE, ViewSize.SMALL),
            createField(4, 2, 1, SensorType.POWER, ViewSize.HUGE),
            createField(5, 3, 1, SensorType.POWER_TO_WEIGHT, ViewSize.LARGE),
            createField(6, 3, 2, SensorType.NORMALIZED_POWER, ViewSize.LARGE),
            createField(7, 4, 1, SensorType.INTENSITY_FACTOR, ViewSize.NORMAL),
            createField(8, 4, 2, SensorType.TSS, ViewSize.NORMAL),
            createField(9, 4, 3, SensorType.WORK_KJ, ViewSize.NORMAL),
            createField(10, 5, 1, SensorType.W_PRIME_BAL, ViewSize.LARGE),
            createField(11, 6, 1, SensorType.PEDAL_POWER_BALANCE, ViewSize.SMALL),
            createField(12, 6, 2, SensorType.TORQUE, ViewSize.SMALL)
        )

        // Snapshot original coordinates to verify strict immutability
        val originalCoordinates = powerTabFields.map { Triple(it.sensorFieldId, it.rowNr, it.colNr) }

        // 1. Default config: enableCogganPowerMetrics = true, enableCriticalPowerModel = false
        // W_PRIME_BAL (Row 5) is hidden; Row 6 follows Row 4 contiguously without an empty row gap
        val defaultConfig = TuningConfig(enableCogganPowerMetrics = true, enableCriticalPowerModel = false)
        val packedDefault = packVisibleRowsForTuning(powerTabFields, defaultConfig, ScreenMode.TRACKING)
        assertEquals(5, packedDefault.size)
        assertEquals(listOf(SensorType.POWER_TO_WEIGHT, SensorType.NORMALIZED_POWER), packedDefault[2].map { it.sensorType })
        assertEquals(listOf(SensorType.INTENSITY_FACTOR, SensorType.TSS, SensorType.WORK_KJ), packedDefault[3].map { it.sensorType })
        assertEquals(listOf(SensorType.PEDAL_POWER_BALANCE, SensorType.TORQUE), packedDefault[4].map { it.sensorType })

        // 2. Both models OFF: enableCogganPowerMetrics = false, enableCriticalPowerModel = false
        // NP is hidden in Row 3 (leaving POWER_TO_WEIGHT full-width); IF & TSS hidden in Row 4 (leaving WORK_KJ full-width); Row 5 hidden
        val bothOffConfig = TuningConfig(enableCogganPowerMetrics = false, enableCriticalPowerModel = false)
        val packedBothOff = packVisibleRowsForTuning(powerTabFields, bothOffConfig, ScreenMode.TRACKING)
        assertEquals(5, packedBothOff.size)
        assertEquals(listOf(SensorType.POWER_TO_WEIGHT), packedBothOff[2].map { it.sensorType })
        assertEquals(listOf(SensorType.WORK_KJ), packedBothOff[3].map { it.sensorType })
        assertEquals(listOf(SensorType.PEDAL_POWER_BALANCE, SensorType.TORQUE), packedBothOff[4].map { it.sensorType })

        // 3. Round-trip back to Both ON: enableCogganPowerMetrics = true, enableCriticalPowerModel = true
        // All 6 rows and all 12 fields are immediately restored with their original rowNr and colNr untouched
        val bothOnConfig = TuningConfig(enableCogganPowerMetrics = true, enableCriticalPowerModel = true)
        val packedBothOn = packVisibleRowsForTuning(powerTabFields, bothOnConfig, ScreenMode.TRACKING)
        assertEquals(6, packedBothOn.size)
        assertEquals(12, packedBothOn.flatten().size)
        assertEquals(listOf(SensorType.POWER_TO_WEIGHT, SensorType.NORMALIZED_POWER), packedBothOn[2].map { it.sensorType })
        assertEquals(listOf(SensorType.INTENSITY_FACTOR, SensorType.TSS, SensorType.WORK_KJ), packedBothOn[3].map { it.sensorType })
        assertEquals(listOf(SensorType.W_PRIME_BAL), packedBothOn[4].map { it.sensorType })
        assertEquals(listOf(SensorType.PEDAL_POWER_BALANCE, SensorType.TORQUE), packedBothOn[5].map { it.sensorType })

        // Verify zero mutation of SensorFieldState.rowNr and colNr across the round-trip
        val currentCoordinates = powerTabFields.map { Triple(it.sensorFieldId, it.rowNr, it.colNr) }
        assertEquals("packVisibleRowsForTuning must never mutate rowNr or colNr", originalCoordinates, currentCoordinates)

        // 4. Verify ScreenMode.CONFIGURATION never hides any field regardless of TuningConfig
        val packedConfigMode = packVisibleRowsForTuning(powerTabFields, bothOffConfig, ScreenMode.CONFIGURATION)
        assertEquals(6, packedConfigMode.size)
        assertEquals(12, packedConfigMode.flatten().size)
    }

    /**
     * TST-UI-372.5: Verifies that when a user configures a custom tab containing exclusively gated fields
     * (`NP`, `IF`, `TSS`, `W'bal`) and disables both toggles (All-Gated state), `packVisibleRowsForTuning`
     * returns an empty list cleanly without throwing any exception, null `tuningConfig` falls back safely
     * during cold boot, and `SensorGridScreen.kt` enforces `defaultMinSize(minHeight = 48.dp)`.
     */
    @Test
    fun testAllGatedState_rendersEmptyPackedRowsAndPreservesMinSizeWithoutCrashing() {
        val allGatedFields = listOf(
            createField(1, 1, 1, SensorType.NORMALIZED_POWER),
            createField(2, 1, 2, SensorType.INTENSITY_FACTOR),
            createField(3, 2, 1, SensorType.TSS),
            createField(4, 3, 1, SensorType.W_PRIME_BAL)
        )

        val bothOffConfig = TuningConfig(enableCogganPowerMetrics = false, enableCriticalPowerModel = false)
        val packedAllGated = packVisibleRowsForTuning(allGatedFields, bothOffConfig, ScreenMode.TRACKING)
        assertTrue("All-Gated tab must produce empty packed rows without throwing", packedAllGated.isEmpty())

        // Cold-boot null tuningConfig fallback: defaults to Coggan = true, Critical Power = false
        val packedNullConfig = packVisibleRowsForTuning(allGatedFields, null, ScreenMode.TRACKING)
        assertEquals(2, packedNullConfig.size)
        assertEquals(
            listOf(SensorType.NORMALIZED_POWER, SensorType.INTENSITY_FACTOR, SensorType.TSS),
            packedNullConfig.flatten().map { it.sensorType }
        )

        // Verify SensorGridScreen.kt applies defaultMinSize(minHeight = 48.dp) and packVisibleRowsForTuning
        val sensorGridScreenFile = File(
            findProjectRoot(),
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt"
        )
        assertTrue("SensorGridScreen.kt must exist", sensorGridScreenFile.exists())
        val content = sensorGridScreenFile.readText()
        assertTrue(
            "SensorGridScreen must apply .defaultMinSize(minHeight = 48.dp) to prevent zero-height collapse when all fields are gated off",
            content.contains(".defaultMinSize(minHeight = 48.dp)")
        )
        assertTrue(
            "SensorGridScreen must memoize packVisibleRowsForTuning(state.fields, tuningConfig, screenMode)",
            content.contains("packVisibleRowsForTuning(state.fields, tuningConfig, screenMode)")
        )
    }

    /**
     * TST-UI-372.4: Verifies that `R.string.tab_power` is present and non-empty across all 9 supported
     * locale resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`,
     * `values-nl`, `values-pl`, `values-pt`), including Japanese `"パワー"`.
     */
    @Test
    fun testTabPowerLocalizationParity_all9LocalesIncludingJapanese() {
        val root = findProjectRoot()
        val expectedTranslations = mapOf(
            "values" to "Power",
            "values-de" to "Leistung",
            "values-es" to "Potencia",
            "values-fr" to "Puissance",
            "values-it" to "Potenza",
            "values-ja" to "パワー",
            "values-nl" to "Vermogen",
            "values-pl" to "Moc",
            "values-pt" to "Potência"
        )

        val tagRegex = Regex("""<string\s+name="tab_power"\s*>([^<]+)</string>""")

        for ((localeDir, expectedValue) in expectedTranslations) {
            val stringsFile = File(root, "app/src/main/res/$localeDir/strings.xml")
            assertTrue("Missing strings.xml for locale $localeDir", stringsFile.exists())
            val xml = stringsFile.readText()
            val match = tagRegex.find(xml)
            assertTrue("Missing <string name=\"tab_power\"> in $localeDir/strings.xml", match != null)
            val actualValue = match!!.groupValues[1].trim()
            assertEquals("Unexpected tab_power translation in $localeDir/strings.xml", expectedValue, actualValue)
        }
    }
}
