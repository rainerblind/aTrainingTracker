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

package com.atrainingtracker.banalservice.ui.devices.devicedata

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.testutil.MockCursorFactory
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [DeviceDataRepository.getPairedRemoteDevices] filters strictly on
 * `protocol=? AND paired=1` in SQL (REQ-DAT-048.3, TST-DAT-044.3, ATT-8128).
 */
class DeviceDataRepositoryPairedRemoteTest {

    private lateinit var mockApplication: Application
    private lateinit var mockDevicesDb: DevicesDatabaseManager
    private lateinit var mockSqlDb: SQLiteDatabase

    @Before
    fun setUp() {
        DeviceDataRepository.resetForTesting(null)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockApplication = mockk(relaxed = true)
        mockDevicesDb = mockk(relaxed = true)
        mockSqlDb = mockk(relaxed = true)
        every { mockDevicesDb.database } returns mockSqlDb

        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb

        EquipmentAndSportTypeDiscoveryManager.resetForTesting(mockk(relaxed = true))
        mockkConstructor(EquipmentDbHelper::class)
        every { anyConstructed<EquipmentDbHelper>().getLinkedEquipmentFromDeviceId(any()) } returns emptyList()
        every { anyConstructed<EquipmentDbHelper>().getEquipment(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        DeviceDataRepository.resetForTesting(null)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun getPairedRemoteDevices_filtersByProtocolAndPairedInSql_TST_DAT_044_3() {
        val selectionSlot = slot<String>()
        val selectionArgsSlot = slot<Array<String>>()

        val pairedCursor = MockCursorFactory.create(
            columns = listOf(
                DevicesDatabaseManager.DevicesDbHelper.C_ID,
                DevicesDatabaseManager.DevicesDbHelper.PAIRED,
                DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE,
                DevicesDatabaseManager.DevicesDbHelper.ANT_DEVICE_NUMBER,
                DevicesDatabaseManager.DevicesDbHelper.BT_ADDRESS
            ),
            rows = listOf(
                listOf(101L, 1, DeviceType.HRM.name, 0, "AA:BB:CC:DD:EE:01")
            )
        )

        every {
            mockSqlDb.query(
                DevicesDatabaseManager.DevicesDbHelper.DEVICES,
                any<Array<String>>(),
                capture(selectionSlot),
                capture(selectionArgsSlot),
                isNull(),
                isNull(),
                isNull()
            )
        } returns pairedCursor

        val repo = DeviceDataRepository.getInstance(mockApplication)
        val result = repo.getPairedRemoteDevices(Protocol.BLUETOOTH_LE)

        assertEquals(
            "${DevicesDatabaseManager.DevicesDbHelper.PROTOCOL}=? AND ${DevicesDatabaseManager.DevicesDbHelper.PAIRED}=1",
            selectionSlot.captured
        )
        assertEquals(listOf(Protocol.BLUETOOTH_LE.name), selectionArgsSlot.captured.toList())
        assertEquals(1, result.size)
        assertEquals(101L, result[0].id)
        assertTrue(result[0].isPaired)
        assertEquals(DeviceType.HRM, result[0].deviceType)
        assertEquals("AA:BB:CC:DD:EE:01", result[0].btAddress)
    }
}
