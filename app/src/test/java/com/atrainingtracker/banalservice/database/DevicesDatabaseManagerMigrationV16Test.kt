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

package com.atrainingtracker.banalservice.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.Manufacturer
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceRawData
import com.atrainingtracker.banalservice.ui.devices.devicedata.raw2UiDeviceData
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceItem
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Unit & Robolectric verification suite for `ATT-8111`:
 * - Removal of `"ATT "` prefix from Fused sensor titles across all 9 locales on fresh installs
 *   and `Devices.db` v15 -> v16 upgrades (while preserving custom user renames).
 * - Correction of `Protocol.SMARTPHONE` virtual sensor manufacturers (`"a Training Tracker"` with
 *   lowercase `'a'` and `"Google"`) across SQLite and `raw2UiDeviceData`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class DevicesDatabaseManagerMigrationV16Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DevicesDatabaseManager.resetForTesting()
    }

    @After
    fun tearDown() {
        unmockkAll()
        DevicesDatabaseManager.resetForTesting()
    }

    @Test
    fun testDbVersion16_andSchemaRegistryParity() {
        assertEquals(16, DevicesDatabaseManager.DevicesDbHelper.DB_VERSION)
        val maxVersion = DatabaseSchemaRegistry.getMaxSupportedVersion("Devices.db")
        assertNotNull("Devices.db must be registered in DatabaseSchemaRegistry", maxVersion)
        assertEquals(16, maxVersion)
    }

    @Test
    fun testFreshInstall_populatesCleanFusedTitlesAndVirtualManufacturers() {
        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns true

        val freshDb = SQLiteDatabase.create(null)
        val helper = DevicesDatabaseManager.DevicesDbHelper(context)
        helper.onCreate(freshDb)

        val odo = readNameAndMfgByType(freshDb, DeviceType.SPEED_AND_LOCATION_ATT_FUSED)
        val alt = readNameAndMfgByType(freshDb, DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED)
        val dem = readNameAndMfgByType(freshDb, DeviceType.ALTITUDE_FROM_POSITION)
        val googleFused = readNameAndMfgByType(freshDb, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val gps = readNameAndMfgByType(freshDb, DeviceType.SPEED_AND_LOCATION_GPS)
        val baro = readNameAndMfgByType(freshDb, DeviceType.ALTITUDE_FROM_PRESSURE)

        assertEquals(context.getString(R.string.devices_location_ATT_fused), odo.first)
        assertFalse("Fused location title must not start with ATT", odo.first.orEmpty().startsWith("ATT "))
        assertEquals("a Training Tracker", odo.second)

        assertEquals(context.getString(R.string.devices_altitude_slope_ATT_fused), alt.first)
        assertFalse("Fused altitude title must not start with ATT", alt.first.orEmpty().startsWith("ATT "))
        assertEquals("a Training Tracker", alt.second)

        assertEquals("a Training Tracker", dem.second)
        assertEquals("Google", googleFused.second)
        assertEquals(Manufacturer.getSmartphoneManufacturer(DeviceType.SPEED_AND_LOCATION_GPS), gps.second)
        assertEquals(Manufacturer.getSmartphoneManufacturer(DeviceType.ALTITUDE_FROM_PRESSURE), baro.second)

        freshDb.close()
    }

    @Test
    fun testOnUpgrade15To16_migratesLegacyTitlesAndManufacturers_preservesCustomRenames_isIdempotent() {
        val freshDb = SQLiteDatabase.create(null)
        val helper = DevicesDatabaseManager.DevicesDbHelper(context)
        helper.onCreate(freshDb)
        val freshColumns = readTableColumns(freshDb)

        val upgradedDb = SQLiteDatabase.create(null)
        upgradedDb.execSQL(
            """
            CREATE TABLE ${DevicesDatabaseManager.DevicesDbHelper.DEVICES} (
                ${DevicesDatabaseManager.DevicesDbHelper.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DevicesDatabaseManager.DevicesDbHelper.PROTOCOL} text,
                ${DevicesDatabaseManager.DevicesDbHelper.ANT_DEVICE_NUMBER} int,
                ${DevicesDatabaseManager.DevicesDbHelper.BT_ADDRESS} text,
                ${DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE} text,
                ${DevicesDatabaseManager.DevicesDbHelper.PAIRED} int,
                ${DevicesDatabaseManager.DevicesDbHelper.NAME} text,
                ${DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME} text,
                ${DevicesDatabaseManager.DevicesDbHelper.CALIBRATION_FACTOR} real,
                ${DevicesDatabaseManager.DevicesDbHelper.LAST_ACTIVE} text,
                ${DevicesDatabaseManager.DevicesDbHelper.LAST_BATTERY_PERCENTAGE} int,
                ${DevicesDatabaseManager.DevicesDbHelper.BASELINE_CIRCUMFERENCE} real DEFAULT 0.0,
                ${DevicesDatabaseManager.DevicesDbHelper.POLAR_H10_OFFLINE_RECORDING} int DEFAULT 1,
                ${DevicesDatabaseManager.DevicesDbHelper.POLAR_LIVE_IMU_ENABLED} int DEFAULT 0
            )
            """.trimIndent()
        )

        val legacyOdoId = insertSmartphoneRow(upgradedDb, DeviceType.SPEED_AND_LOCATION_ATT_FUSED, "ATT Odometrie-Fusion", "samsung")
        val legacyAltId = insertSmartphoneRow(upgradedDb, DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED, "ATT Fused (Höhe und Steigung)", "samsung")
        val customOdoId = insertSmartphoneRow(upgradedDb, DeviceType.SPEED_AND_LOCATION_ATT_FUSED, "Mein Spezial EKF", null)
        val customAltId = insertSmartphoneRow(upgradedDb, DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED, "Custom Elevation Sensor", "Unknown")
        val demId = insertSmartphoneRow(upgradedDb, DeviceType.ALTITUDE_FROM_POSITION, "Topografische Höhe (DEM)", "samsung")
        val googleFusedId = insertSmartphoneRow(upgradedDb, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED, "Google Standort (Fused)", "samsung")
        val gpsId = insertSmartphoneRow(upgradedDb, DeviceType.SPEED_AND_LOCATION_GPS, "Smartphone GPS (Satellit)", "samsung")
        val bleId = insertRow(upgradedDb, Protocol.BLUETOOTH_LE, DeviceType.HRM, "Polar H10", "Polar Electro Oy")

        helper.onUpgrade(upgradedDb, 15, 16)

        assertEquals("onCreate and onUpgrade(15, 16) schemas must match", freshColumns, readTableColumns(upgradedDb))

        val expectedOdoTitle = context.getString(R.string.devices_location_ATT_fused)
        val expectedAltTitle = context.getString(R.string.devices_altitude_slope_ATT_fused)

        assertEquals(expectedOdoTitle to "a Training Tracker", readNameAndMfg(upgradedDb, legacyOdoId))
        assertEquals(expectedAltTitle to "a Training Tracker", readNameAndMfg(upgradedDb, legacyAltId))
        assertEquals("Mein Spezial EKF" to "a Training Tracker", readNameAndMfg(upgradedDb, customOdoId))
        assertEquals("Custom Elevation Sensor" to "a Training Tracker", readNameAndMfg(upgradedDb, customAltId))
        assertEquals("Topografische Höhe (DEM)" to "a Training Tracker", readNameAndMfg(upgradedDb, demId))
        assertEquals("Google Standort (Fused)" to "Google", readNameAndMfg(upgradedDb, googleFusedId))
        assertEquals("Smartphone GPS (Satellit)" to "samsung", readNameAndMfg(upgradedDb, gpsId))
        assertEquals("Polar H10" to "Polar Electro Oy", readNameAndMfg(upgradedDb, bleId))

        // Second invocation must be 100% idempotent with 0 rows modified
        assertEquals(0, DevicesDatabaseMigrationV16Helper.migrateAttFusedTitlesAndSmartphoneManufacturers(upgradedDb, context))
        assertEquals(0, DevicesDatabaseMigrationV16Helper.updateLegacyDeviceNames(upgradedDb, DeviceType.SPEED_AND_LOCATION_ATT_FUSED, "Ignored", emptyArray()))

        freshDb.close()
        upgradedDb.close()
    }

    @Test
    fun testRaw2UiDeviceData_preservesLowercaseATrainingTrackerAndNormalizesVirtualManufacturers() {
        fun rawSmartphone(type: DeviceType, name: String, rawMfg: String?) = DeviceRawData(
            id = 1L,
            protocol = Protocol.SMARTPHONE,
            deviceType = type,
            lastSeen = "Now",
            batteryPercentage = 95,
            manufacturer = rawMfg,
            deviceName = name,
            isPaired = false,
            calibrationValue = null,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null
        )

        val odoUi = raw2UiDeviceData(rawSmartphone(DeviceType.SPEED_AND_LOCATION_ATT_FUSED, "Odometry Fused", "samsung"), context)
        val altUi = raw2UiDeviceData(rawSmartphone(DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED, "Fused (Altitude & Slope)", ""), context)
        val demUi = raw2UiDeviceData(rawSmartphone(DeviceType.ALTITUDE_FROM_POSITION, "Topographic Elevation (DEM)", null), context)
        val googleUi = raw2UiDeviceData(rawSmartphone(DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED, "Google Location (Fused)", "samsung"), context)
        val gpsUi = raw2UiDeviceData(rawSmartphone(DeviceType.SPEED_AND_LOCATION_GPS, "Phone GPS (Satellite)", "google"), context)
        val unknownUi = raw2UiDeviceData(rawSmartphone(DeviceType.ALTITUDE_FROM_PRESSURE, "Barometer", ""), context)

        assertEquals("a Training Tracker", odoUi.manufacturer)
        assertEquals("a Training Tracker", altUi.manufacturer)
        assertEquals("a Training Tracker", demUi.manufacturer)
        assertEquals("Google", googleUi.manufacturer)
        assertEquals("Google", gpsUi.manufacturer)
        assertEquals(
            context.getString(R.string.devices_unknown_manufacturer).replaceFirstChar { it.uppercaseChar() },
            unknownUi.manufacturer
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    DeviceItem(
                        device = odoUi,
                        onPairClick = {},
                        onItemClick = {},
                        onLongClick = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Odometry Fused").assertIsDisplayed()
        composeTestRule.onNodeWithText("a Training Tracker").assertIsDisplayed()
    }

    private fun insertSmartphoneRow(db: SQLiteDatabase, deviceType: DeviceType, name: String?, mfg: String?): Long =
        insertRow(db, Protocol.SMARTPHONE, deviceType, name, mfg)

    private fun insertRow(
        db: SQLiteDatabase,
        protocol: Protocol,
        deviceType: DeviceType,
        name: String?,
        mfg: String?
    ): Long {
        val values = ContentValues().apply {
            put(DevicesDatabaseManager.DevicesDbHelper.PROTOCOL, protocol.name)
            put(DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE, deviceType.name)
            put(DevicesDatabaseManager.DevicesDbHelper.PAIRED, 0)
            if (name != null) put(DevicesDatabaseManager.DevicesDbHelper.NAME, name) else putNull(DevicesDatabaseManager.DevicesDbHelper.NAME)
            if (mfg != null) put(DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME, mfg) else putNull(DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME)
        }
        return db.insert(DevicesDatabaseManager.DevicesDbHelper.DEVICES, null, values)
    }

    private fun readNameAndMfg(db: SQLiteDatabase, rowId: Long): Pair<String?, String?> {
        db.rawQuery(
            "SELECT ${DevicesDatabaseManager.DevicesDbHelper.NAME}, ${DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME} " +
                "FROM ${DevicesDatabaseManager.DevicesDbHelper.DEVICES} WHERE ${DevicesDatabaseManager.DevicesDbHelper.C_ID}=?",
            arrayOf(rowId.toString())
        ).use { c ->
            assertTrue(c.moveToFirst())
            val name = if (c.isNull(0)) null else c.getString(0)
            val mfg = if (c.isNull(1)) null else c.getString(1)
            return name to mfg
        }
    }

    private fun readNameAndMfgByType(db: SQLiteDatabase, deviceType: DeviceType): Pair<String?, String?> {
        db.rawQuery(
            "SELECT ${DevicesDatabaseManager.DevicesDbHelper.NAME}, ${DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME} " +
                "FROM ${DevicesDatabaseManager.DevicesDbHelper.DEVICES} WHERE ${DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE}=?",
            arrayOf(deviceType.name)
        ).use { c ->
            assertTrue("Expected row for $deviceType", c.moveToFirst())
            val name = if (c.isNull(0)) null else c.getString(0)
            val mfg = if (c.isNull(1)) null else c.getString(1)
            return name to mfg
        }
    }

    private fun readTableColumns(db: SQLiteDatabase): Map<String, Pair<String, String?>> {
        val result = linkedMapOf<String, Pair<String, String?>>()
        db.rawQuery("PRAGMA table_info(${DevicesDatabaseManager.DevicesDbHelper.DEVICES})", null).use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)
                val type = c.getString(2)
                val dflt = if (c.isNull(4)) null else c.getString(4)
                result[name] = type to dflt
            }
        }
        return result
    }
}
