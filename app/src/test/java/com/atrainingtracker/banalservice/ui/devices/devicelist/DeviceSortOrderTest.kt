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

package com.atrainingtracker.banalservice.ui.devices.devicelist

import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import org.junit.Assert.*
import org.junit.Test
import java.text.DateFormat
import java.util.Date

/**
 * Unit test verifying DeviceSortOrder domain enumeration and comparators
 * (REQ-UI-528.1, REQ-UI-528.2, TST-UI-528.1, ATT-7663).
 */
class DeviceSortOrderTest {

    private fun createDevice(
        id: Long,
        name: String,
        batteryPercentage: Int = 100,
        isConnected: Boolean = false,
        lastSeen: String? = null,
        isNewlyDiscovered: Boolean = false
    ): DeviceUiData {
        return DeviceUiData(
            id = id,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.HRM,
            lastSeen = lastSeen,
            manufacturer = "Manufacturer",
            deviceName = name,
            isPaired = true,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = batteryPercentage,
            linkedSportTypes = emptyList(),
            deviceTypeIconRes = 0,
            batteryStatusIconRes = 0,
            onEquipmentResId = 0,
            wheelCircumference = null,
            calibrationFactor = null,
            powerFeatures = null,
            isConnected = isConnected,
            mainValue = null,
            allValues = null,
            isNewlyDiscovered = isNewlyDiscovered
        )
    }

    @Test
    fun testDeviceSortOrder_enumEntries() {
        val entries = DeviceSortOrder.entries
        assertEquals(4, entries.size)
        assertEquals(DeviceSortOrder.ALPHABETICAL, entries[0])
        assertEquals(DeviceSortOrder.FIRST_SEEN, entries[1])
        assertEquals(DeviceSortOrder.LAST_USAGE, entries[2])
        assertEquals(DeviceSortOrder.BATTERY_LEVEL, entries[3])
    }

    @Test
    fun testParseLastSeenTimestamp_handlesVariousFormats() {
        // Null or blank
        assertEquals(0L, DeviceSortOrder.parseLastSeenTimestamp(null))
        assertEquals(0L, DeviceSortOrder.parseLastSeenTimestamp(""))
        assertEquals(0L, DeviceSortOrder.parseLastSeenTimestamp("   "))

        // Standard DateFormat
        val now = System.currentTimeMillis()
        val standardFormatted = DateFormat.getDateTimeInstance().format(Date(now))
        val parsed = DeviceSortOrder.parseLastSeenTimestamp(standardFormatted)
        // DateTimeInstance has seconds resolution
        assertTrue("Parsed time should be close to now", Math.abs(now - parsed) < 2000L)

        // ISO pattern (local and UTC)
        val isoTimestamp = DeviceSortOrder.parseLastSeenTimestamp("2026-11-14 10:30:00")
        assertTrue(isoTimestamp > 0L)
        val utcIsoTimestamp = DeviceSortOrder.parseLastSeenTimestamp("2026-11-22T17:30:00Z")
        assertTrue(utcIsoTimestamp > isoTimestamp)

        // Unparseable string
        assertEquals(0L, DeviceSortOrder.parseLastSeenTimestamp("not-a-date"))
    }

    @Test
    fun testAlphabeticalComparator_ordersCaseInsensitiveWithIdTieBreaker() {
        val dev1 = createDevice(id = 10L, name = "wahoo TICKR")
        val dev2 = createDevice(id = 20L, name = "Garmin HRM")
        val dev3 = createDevice(id = 5L, name = "garmin hrm")
        val dev4 = createDevice(id = 15L, name = "Polar H10")

        val list = listOf(dev1, dev2, dev3, dev4)
        val sorted = list.sortedWith(DeviceSortOrder.getComparator(DeviceSortOrder.ALPHABETICAL))

        // "Garmin HRM" and "garmin hrm" are case-insensitively equal -> tie-broken by id (5L then 20L)
        // Polar H10 -> 15L
        // wahoo TICKR -> 10L
        assertEquals(listOf(5L, 20L, 15L, 10L), sorted.map { it.id })
    }

    @Test
    fun testFirstSeenComparator_ordersByIdDescending() {
        val dev1 = createDevice(id = 1L, name = "Sensor A")
        val dev2 = createDevice(id = 45L, name = "Sensor B")
        val dev3 = createDevice(id = 12L, name = "Sensor C")

        val sorted = listOf(dev1, dev2, dev3).sortedWith(DeviceSortOrder.getComparator(DeviceSortOrder.FIRST_SEEN))

        // Row ID descending: 45L, 12L, 1L
        assertEquals(listOf(45L, 12L, 1L), sorted.map { it.id })
    }

    @Test
    fun testLastUsageComparator_connectedFirstThenRecentTimestampThenNeverSeen() {
        val devConnected = createDevice(id = 1L, name = "Connected Sensor", isConnected = true, lastSeen = "2026-11-10 10:00:00")
        val devRecent = createDevice(id = 2L, name = "Recent Sensor", isConnected = false, lastSeen = "2026-11-14 08:00:00")
        val devOlder = createDevice(id = 3L, name = "Older Sensor", isConnected = false, lastSeen = "2026-11-01 12:00:00")
        val devNeverSeenA = createDevice(id = 4L, name = "Alpha Never Seen", isConnected = false, lastSeen = null)
        val devNeverSeenZ = createDevice(id = 5L, name = "Zulu Never Seen", isConnected = false, lastSeen = null)

        val list = listOf(devNeverSeenZ, devOlder, devConnected, devNeverSeenA, devRecent)
        val sorted = list.sortedWith(DeviceSortOrder.getComparator(DeviceSortOrder.LAST_USAGE))

        // 1. Connected (id 1)
        // 2. Recent (id 2, 2026-11-14)
        // 3. Older (id 3, 2026-11-01)
        // 4. Alpha Never Seen (id 4, alphabetical name tie-breaker)
        // 5. Zulu Never Seen (id 5)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), sorted.map { it.id })
    }

    @Test
    fun testBatteryLevelComparator_lowestKnownFirstThenUnknownTieBrokenByName() {
        val devCrit = createDevice(id = 1L, name = "Sensor Crit 10%", batteryPercentage = 10)
        val devMid = createDevice(id = 2L, name = "Sensor Mid 50%", batteryPercentage = 50)
        val devFull = createDevice(id = 3L, name = "Sensor Full 100%", batteryPercentage = 100)
        val devUnknownA = createDevice(id = 4L, name = "Alpha Unknown", batteryPercentage = -1)
        val devUnknownZ = createDevice(id = 5L, name = "Zulu Unknown", batteryPercentage = -1)

        val list = listOf(devFull, devUnknownZ, devCrit, devUnknownA, devMid)
        val sorted = list.sortedWith(DeviceSortOrder.getComparator(DeviceSortOrder.BATTERY_LEVEL))

        // 1. 10% (id 1)
        // 2. 50% (id 2)
        // 3. 100% (id 3)
        // 4. Unknown Alpha (id 4)
        // 5. Unknown Zulu (id 5)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), sorted.map { it.id })
    }
}
