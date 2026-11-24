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

package com.atrainingtracker.banalservice.devices

import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying search queue integrity and isolation against alien device stop intents
 * in DeviceManager (REQ-CON-020.3, TST-CON-012.3).
 */
class DeviceManagerSearchQueueTest {

    private lateinit var mockBanalService: BANALService
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockAndroidSensorManager: SensorManager
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.startSearchWhenAppStarts() } returns false
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.havePermission(any()) } returns false
        every { TrainingApplication.searchOnlyForSportSpecificDevices() } returns false

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        mockDevicesDbManager = mockk(relaxed = true)
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockLocationManager = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockAndroidSensorManager = mockk(relaxed = true)
        every { mockAndroidSensorManager.getDefaultSensor(any()) } returns null

        mockBanalService = mockk(relaxed = true)
        every { mockBanalService.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { mockBanalService.getSystemService(Context.SENSOR_SERVICE) } returns mockAndroidSensorManager
        every { mockBanalService.packageName } returns "com.atrainingtracker"
        every { mockBanalService.userSelectedBSportType } returns null

        mockkConstructor(ClockDevice::class)
        mockkConstructor(BatteryDevice::class)
    }

    @After
    fun tearDown() {
        try {
            unmockkAll()
        } catch (_: ConcurrentModificationException) {
            unmockkAll()
        }
    }

    private fun setCurrentlySearching(deviceManager: DeviceManager, device: MyRemoteDevice?) {
        val field = DeviceManager::class.java.getDeclaredField("cMyRemoteDeviceCurrentlySearchingFor")
        field.isAccessible = true
        field.set(deviceManager, device)
    }

    private fun setDeviceTries(deviceManager: DeviceManager, device: MyRemoteDevice, tries: Int) {
        val field = DeviceManager::class.java.getDeclaredField("mMyRemoteDeviceTries")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(deviceManager) as MutableMap<MyRemoteDevice, Int>
        map[device] = tries
    }

    private fun getRemoteDevicesMap(deviceManager: DeviceManager): MutableMap<Long, MyRemoteDevice> {
        val field = DeviceManager::class.java.getDeclaredField("mMyRemoteDevices")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(deviceManager) as MutableMap<Long, MyRemoteDevice>
    }

    @Test
    fun testAlienDeviceEventIgnoredAndQueueNotPreempted() {
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)

        val deviceA = mockk<MyRemoteDevice>(relaxed = true)
        every { deviceA.deviceId } returns 101L
        every { deviceA.deviceType } returns DeviceType.HRM

        val deviceB = mockk<MyRemoteDevice>(relaxed = true)
        every { deviceB.deviceId } returns 102L
        every { deviceB.deviceType } returns DeviceType.BIKE_POWER
        every { deviceB.isSearching } returns false
        every { deviceB.isReceivingData } returns false

        setCurrentlySearching(deviceManager, deviceA)
        deviceManager.searchQueue.clear()
        deviceManager.searchQueue.addLast(deviceB)

        // Simulate alien device (ID = 999) firing SEARCHING_STOPPED_FOR_ONE_INTENT
        val alienIntent = mockk<Intent>()
        every { alienIntent.getLongExtra(BANALService.DEVICE_ID, -1) } returns 999L
        every { alienIntent.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false) } returns false

        val receiver = deviceManager.searchingStoppedForOneReceiver
        receiver.onReceive(mockBanalService, alienIntent)

        // Verify Device A remains currently searching
        assertSame(deviceA, deviceManager.currentlySearchingForRemoteDevice)

        // Verify Device B is NOT popped from mSearchQueue
        assertEquals(1, deviceManager.searchQueue.size)
        assertSame(deviceB, deviceManager.searchQueue.peekFirst())

        // Verify Device B was NOT started
        verify(exactly = 0) { deviceB.startSearching() }
    }

    @Test
    fun testMatchingDeviceEventAdvancesQueueToNextDevice() {
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)

        val deviceA = mockk<MyRemoteDevice>(relaxed = true)
        every { deviceA.deviceId } returns 101L
        every { deviceA.deviceType } returns DeviceType.HRM

        val deviceB = mockk<MyRemoteDevice>(relaxed = true)
        every { deviceB.deviceId } returns 102L
        every { deviceB.deviceType } returns DeviceType.HRM
        every { deviceB.isSearching } returns false
        every { deviceB.isReceivingData } returns false

        setCurrentlySearching(deviceManager, deviceA)
        deviceManager.searchQueue.clear()
        deviceManager.searchQueue.addLast(deviceB)

        // Simulate matching device A firing stop intent
        val matchingIntent = mockk<Intent>()
        every { matchingIntent.getLongExtra(BANALService.DEVICE_ID, -1) } returns 101L
        every { matchingIntent.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false) } returns true

        val receiver = deviceManager.searchingStoppedForOneReceiver
        receiver.onReceive(mockBanalService, matchingIntent)

        // Verify queue advanced to Device B
        assertSame(deviceB, deviceManager.currentlySearchingForRemoteDevice)
        assertTrue(deviceManager.searchQueue.isEmpty())
        verify(exactly = 1) { deviceB.startSearching() }
    }

    /**
     * TST-CON-051.1, TST-CON-051.2: Multi-sensor queue [HR1 -> offline BLE Radar -> HR2] during
     * active/paused workout advances immediately to HR2 on Radar timeout, clears isSearching() after
     * queue drain, and preserves the Radar in mMyRemoteDevices when tries <= 1.
     */
    @Test
    fun testMultiSensorQueue_radarTimeoutDuringWorkout_advancesToSecondHrAndPreservesRadarOnTriesExhausted() {
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)

        val hr1 = mockk<MyRemoteDevice>(relaxed = true)
        every { hr1.deviceId } returns 101L
        every { hr1.deviceType } returns DeviceType.HRM

        val offlineRadar = mockk<MyRemoteDevice>(relaxed = true)
        every { offlineRadar.deviceId } returns 102L
        every { offlineRadar.deviceType } returns DeviceType.BIKE_RADAR
        every { offlineRadar.isSearching } returns false
        every { offlineRadar.isReceivingData } returns false
        every { offlineRadar.shouldKeepAliveOnSearchTimeout() } returns true

        val hr2 = mockk<MyRemoteDevice>(relaxed = true)
        every { hr2.deviceId } returns 103L
        every { hr2.deviceType } returns DeviceType.HRM
        every { hr2.isSearching } returns false
        every { hr2.isReceivingData } returns false

        val devicesMap = getRemoteDevicesMap(deviceManager)
        devicesMap[101L] = hr1
        devicesMap[102L] = offlineRadar
        devicesMap[103L] = hr2

        setCurrentlySearching(deviceManager, hr1)
        setDeviceTries(deviceManager, offlineRadar, 1)
        setDeviceTries(deviceManager, hr2, 1)
        deviceManager.searchQueue.clear()
        deviceManager.searchQueue.addLast(offlineRadar)
        deviceManager.searchQueue.addLast(hr2)

        val receiver = deviceManager.searchingStoppedForOneReceiver

        // 1. HR1 found -> queue advances to offline Radar
        val hr1Found = mockk<Intent>()
        every { hr1Found.getLongExtra(BANALService.DEVICE_ID, -1) } returns 101L
        every { hr1Found.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false) } returns true
        receiver.onReceive(mockBanalService, hr1Found)

        assertSame(offlineRadar, deviceManager.currentlySearchingForRemoteDevice)
        assertTrue(DeviceManager.isSearchingForARemoteDevice())
        verify(exactly = 1) { offlineRadar.startSearching() }

        // 2. Radar times out (success=false, tries=1) -> must NOT shut down Radar, must advance to HR2
        val radarTimeout = mockk<Intent>()
        every { radarTimeout.getLongExtra(BANALService.DEVICE_ID, -1) } returns 102L
        every { radarTimeout.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false) } returns false
        receiver.onReceive(mockBanalService, radarTimeout)

        verify(exactly = 0) { offlineRadar.shutDown() }
        assertTrue("Active-workout reconnecting device must stay registered", devicesMap.containsKey(102L))
        assertSame(hr2, deviceManager.currentlySearchingForRemoteDevice)
        verify(exactly = 1) { hr2.startSearching() }

        // 3. HR2 finishes -> queue drains and BANALService.isSearching() / DeviceManager.isSearchingForARemoteDevice() becomes false
        val hr2Found = mockk<Intent>()
        every { hr2Found.getLongExtra(BANALService.DEVICE_ID, -1) } returns 103L
        every { hr2Found.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false) } returns true
        receiver.onReceive(mockBanalService, hr2Found)

        assertEquals(null, deviceManager.currentlySearchingForRemoteDevice)
        assertEquals(false, DeviceManager.isSearchingForARemoteDevice())
        assertTrue(deviceManager.searchQueue.isEmpty())
    }

    /**
     * TST-CON-051.2 / TST-CON-052.2: When periodic re-search is disabled, shouldKeepAliveOnSearchTimeout() == false
     * preserves REQ-CON-036.1 offline startup battery bounding: tries <= 1 shuts down and removes device.
     */
    @Test
    fun testTriesExhausted_whenKeepAliveFalseAndPeriodicDisabled_shutsDownAndRemovesOfflineDevice() {
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        mockkStatic(androidx.preference.PreferenceManager::class)
        every { androidx.preference.PreferenceManager.getDefaultSharedPreferences(mockBanalService) } returns mockPrefs
        every { mockPrefs.contains(com.atrainingtracker.trainingtracker.settings.SensorSearchSettings.SP_PERIODIC_RESEARCH_ENABLED) } returns true
        every { mockPrefs.getBoolean(com.atrainingtracker.trainingtracker.settings.SensorSearchSettings.SP_PERIODIC_RESEARCH_ENABLED, true) } returns false

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)

        val offlineSensor = mockk<MyRemoteDevice>(relaxed = true)
        every { offlineSensor.deviceId } returns 201L
        every { offlineSensor.deviceType } returns DeviceType.BIKE_RADAR
        every { offlineSensor.shouldKeepAliveOnSearchTimeout() } returns false

        val nextHr = mockk<MyRemoteDevice>(relaxed = true)
        every { nextHr.deviceId } returns 202L
        every { nextHr.deviceType } returns DeviceType.HRM
        every { nextHr.isSearching } returns false
        every { nextHr.isReceivingData } returns false

        val devicesMap = getRemoteDevicesMap(deviceManager)
        devicesMap[201L] = offlineSensor
        devicesMap[202L] = nextHr

        setCurrentlySearching(deviceManager, offlineSensor)
        setDeviceTries(deviceManager, offlineSensor, 1)
        deviceManager.searchQueue.clear()
        deviceManager.searchQueue.addLast(nextHr)

        val timeoutIntent = mockk<Intent>()
        every { timeoutIntent.getLongExtra(BANALService.DEVICE_ID, -1) } returns 201L
        every { timeoutIntent.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false) } returns false

        deviceManager.searchingStoppedForOneReceiver.onReceive(mockBanalService, timeoutIntent)

        verify(exactly = 1) { offlineSensor.shutDown() }
        assertEquals(false, devicesMap.containsKey(201L))
        assertSame(nextHr, deviceManager.currentlySearchingForRemoteDevice)
        verify(exactly = 1) { nextHr.startSearching() }
    }

    /**
     * TST-CON-052.2: When periodic re-search is enabled (default), tries <= 1 exhaustion retains
     * a previously connected MIA sensor (hasConnectedInSession() == true) and schedules extended
     * backoff re-enqueue (32s -> 60s default) while advancing the active queue immediately (REQ-CON-051),
     * whereas a never-connected sensor gives up cleanly with 0 timers.
     */
    @Test
    fun testTriesExhausted_whenPeriodicResearchEnabled_retainsOnlyMiaDeviceAndGivesUpNeverConnected() {
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        val scheduled = mutableListOf<Pair<Long, Runnable>>()
        deviceManager.periodicRetryCoordinator.setSchedulerForTesting(
            scheduleDelayed = { delayMs, runnable -> scheduled.add(delayMs to runnable) },
            cancelScheduled = {}
        )

        val neverConnectedSensor = mockk<MyRemoteDevice>(relaxed = true)
        every { neverConnectedSensor.deviceId } returns 300L
        every { neverConnectedSensor.deviceType } returns DeviceType.HRM
        every { neverConnectedSensor.shouldKeepAliveOnSearchTimeout() } returns false
        every { neverConnectedSensor.hasConnectedInSession() } returns false
        every { neverConnectedSensor.isReceivingData } returns false
        every { neverConnectedSensor.isSearching } returns false

        val miaSensor = mockk<MyRemoteDevice>(relaxed = true)
        every { miaSensor.deviceId } returns 301L
        every { miaSensor.deviceType } returns DeviceType.HRM
        every { miaSensor.shouldKeepAliveOnSearchTimeout() } returns false
        every { miaSensor.hasConnectedInSession() } returns true
        every { miaSensor.isReceivingData } returns false
        every { miaSensor.isSearching } returns false

        val nextDevice = mockk<MyRemoteDevice>(relaxed = true)
        every { nextDevice.deviceId } returns 302L
        every { nextDevice.deviceType } returns DeviceType.BIKE_SPEED
        every { nextDevice.isReceivingData } returns false
        every { nextDevice.isSearching } returns false

        val devicesMap = getRemoteDevicesMap(deviceManager)
        devicesMap[300L] = neverConnectedSensor
        devicesMap[301L] = miaSensor
        devicesMap[302L] = nextDevice

        setCurrentlySearching(deviceManager, neverConnectedSensor)
        setDeviceTries(deviceManager, neverConnectedSensor, 1)
        setDeviceTries(deviceManager, miaSensor, 1)
        deviceManager.searchQueue.clear()
        deviceManager.searchQueue.addLast(miaSensor)
        deviceManager.searchQueue.addLast(nextDevice)

        // 1. Never-connected sensor A exhausts tries -> shuts down & removed with ZERO periodic timers
        val neverTimeout = mockk<Intent>()
        every { neverTimeout.getLongExtra(BANALService.DEVICE_ID, -1) } returns 300L
        every { neverTimeout.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false) } returns false
        deviceManager.searchingStoppedForOneReceiver.onReceive(mockBanalService, neverTimeout)

        verify(exactly = 1) { neverConnectedSensor.shutDown() }
        assertEquals(false, devicesMap.containsKey(300L))
        assertEquals(0, scheduled.size)
        assertSame(miaSensor, deviceManager.currentlySearchingForRemoteDevice)

        // 2. MIA sensor B exhausts tries -> retained & schedules 32s retry while advancing to nextDevice
        val timeoutIntent = mockk<Intent>()
        every { timeoutIntent.getLongExtra(BANALService.DEVICE_ID, -1) } returns 301L
        every { timeoutIntent.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false) } returns false
        deviceManager.searchingStoppedForOneReceiver.onReceive(mockBanalService, timeoutIntent)

        verify(exactly = 0) { miaSensor.shutDown() }
        assertTrue(devicesMap.containsKey(301L))
        assertSame(nextDevice, deviceManager.currentlySearchingForRemoteDevice)
        verify(exactly = 1) { nextDevice.startSearching() }

        assertEquals(1, scheduled.size)
        assertEquals(32_000L, scheduled[0].first)

        // Finish nextDevice search so queue becomes idle
        val nextDone = mockk<Intent>()
        every { nextDone.getLongExtra(BANALService.DEVICE_ID, -1) } returns 302L
        every { nextDone.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false) } returns true
        deviceManager.searchingStoppedForOneReceiver.onReceive(mockBanalService, nextDone)
        assertEquals(null, deviceManager.currentlySearchingForRemoteDevice)

        // Fire periodic retry -> miaSensor is re-enqueued and started; next exhaustion steps to 60s (1m default)
        scheduled[0].second.run()
        assertSame(miaSensor, deviceManager.currentlySearchingForRemoteDevice)
        verify(exactly = 2) { miaSensor.startSearching() }

        deviceManager.searchingStoppedForOneReceiver.onReceive(mockBanalService, timeoutIntent)
        assertEquals(2, scheduled.size)
        assertEquals(60_000L, scheduled[1].first)
    }

    /**
     * ATT-8965 Bounce 2 / ATT-9178: If periodic re-search is disabled while a retry timer is pending
     * for a lost/MIA device, firing the runnable MUST clean up delay state, invoke shutDown(), and
     * remove the zombie device from mMyRemoteDevices.
     */
    @Test
    fun testTriesExhausted_whenDisabledWhileRetryPending_givesUpAndRemovesZombieDevice() {
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        mockkStatic(androidx.preference.PreferenceManager::class)
        every { androidx.preference.PreferenceManager.getDefaultSharedPreferences(mockBanalService) } returns mockPrefs
        var enabled = true
        every { mockPrefs.contains(com.atrainingtracker.trainingtracker.settings.SensorSearchSettings.SP_PERIODIC_RESEARCH_ENABLED) } returns true
        every { mockPrefs.getBoolean(com.atrainingtracker.trainingtracker.settings.SensorSearchSettings.SP_PERIODIC_RESEARCH_ENABLED, true) } answers { enabled }

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        val scheduled = mutableListOf<Pair<Long, Runnable>>()
        deviceManager.periodicRetryCoordinator.setSchedulerForTesting(
            scheduleDelayed = { delayMs, runnable -> scheduled.add(delayMs to runnable) },
            cancelScheduled = {}
        )

        val miaSensor = mockk<MyRemoteDevice>(relaxed = true)
        every { miaSensor.deviceId } returns 401L
        every { miaSensor.deviceType } returns DeviceType.HRM
        every { miaSensor.shouldKeepAliveOnSearchTimeout() } returns false
        every { miaSensor.hasConnectedInSession() } returns true
        every { miaSensor.isReceivingData } returns false

        val devicesMap = getRemoteDevicesMap(deviceManager)
        devicesMap[401L] = miaSensor
        setCurrentlySearching(deviceManager, miaSensor)
        setDeviceTries(deviceManager, miaSensor, 1)

        val timeoutIntent = mockk<Intent>()
        every { timeoutIntent.getLongExtra(BANALService.DEVICE_ID, -1) } returns 401L
        every { timeoutIntent.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false) } returns false
        deviceManager.searchingStoppedForOneReceiver.onReceive(mockBanalService, timeoutIntent)

        assertTrue(devicesMap.containsKey(401L))
        assertEquals(1, scheduled.size)

        // Toggle disabled before scheduled periodic retry executes -> MUST shut down and remove zombie device
        enabled = false
        scheduled[0].second.run()

        verify(exactly = 1) { miaSensor.shutDown() }
        assertEquals(false, devicesMap.containsKey(401L))
        verify(exactly = 0) { miaSensor.startSearching() }
    }

    /**
     * TST-CON-052.2 (ATT-9178): DeviceManager eagerly constructs mPeriodicRetryCoordinator capturing
     * mid-workout TRACKING mode; on TRACKING -> READY broadcast, pending retries cancel and
     * resetSessionConnectionState() runs strictly on disconnected MIA devices while live sensors remain untouched.
     */
    @Test
    fun testWorkoutEndTrackingStateChanged_constructedMidWorkout_resetsDisconnectedMiaAndPreservesConnected() {
        every { TrainingApplication.getTrackingMode() } returns com.atrainingtracker.trainingtracker.TrackingMode.TRACKING
        every { TrainingApplication.getNumberOfSearchTries() } returns 3
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)

        val disconnectedMia = mockk<MyRemoteDevice>(relaxed = true)
        every { disconnectedMia.deviceId } returns 501L
        every { disconnectedMia.isReceivingData } returns false
        val liveConnected = mockk<MyRemoteDevice>(relaxed = true)
        every { liveConnected.deviceId } returns 502L
        every { liveConnected.isReceivingData } returns true

        val devicesMap = getRemoteDevicesMap(deviceManager)
        devicesMap[501L] = disconnectedMia
        devicesMap[502L] = liveConnected

        // Trigger TRACKING -> READY via mTrackingStateChangedReceiver
        every { TrainingApplication.getTrackingMode() } returns com.atrainingtracker.trainingtracker.TrackingMode.READY
        val receiverField = DeviceManager::class.java.getDeclaredField("mTrackingStateChangedReceiver")
        receiverField.isAccessible = true
        val receiver = receiverField.get(deviceManager) as android.content.BroadcastReceiver
        setCurrentlySearching(deviceManager, liveConnected) // prevent startSearchForPairedDevices DB queries
        receiver.onReceive(mockBanalService, mockk(relaxed = true))

        verify(exactly = 1) { disconnectedMia.resetSessionConnectionState() }
        verify(exactly = 0) { liveConnected.resetSessionConnectionState() }
        setCurrentlySearching(deviceManager, null)
    }

    /**
     * TST-SET-125.3: 3 absent keep-alive paired BLE sensors during active workout with tries=3 each
     * execute strictly 1 search per device (3 total instead of 9), remove deviceTries entries without
     * re-enqueueing tries 2..3, stay registered in mMyRemoteDevices, and drain the queue cleanly.
     */
    @Test
    fun testThreeAbsentKeepAliveDevices_withTries3_searchedOnceAndDeviceTriesCleared_TST_SET_125_3() {
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        val devicesMap = getRemoteDevicesMap(deviceManager)
        val triesField = DeviceManager::class.java.getDeclaredField("mMyRemoteDeviceTries")
        triesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val triesMap = triesField.get(deviceManager) as MutableMap<MyRemoteDevice, Int>

        val d1 = mockk<MyRemoteDevice>(relaxed = true)
        val d2 = mockk<MyRemoteDevice>(relaxed = true)
        val d3 = mockk<MyRemoteDevice>(relaxed = true)
        listOf(101L to d1, 102L to d2, 103L to d3).forEach { (id, dev) ->
            every { dev.deviceId } returns id
            every { dev.deviceType } returns DeviceType.HRM
            every { dev.isSearching } returns false
            every { dev.isReceivingData } returns false
            every { dev.shouldKeepAliveOnSearchTimeout() } returns true
            devicesMap[id] = dev
            setDeviceTries(deviceManager, dev, 3)
        }

        setCurrentlySearching(deviceManager, d1)
        deviceManager.searchQueue.clear()
        deviceManager.searchQueue.addLast(d2)
        deviceManager.searchQueue.addLast(d3)

        val receiver = deviceManager.searchingStoppedForOneReceiver
        for (id in listOf(101L, 102L, 103L)) {
            val timeoutIntent = mockk<Intent>()
            every { timeoutIntent.getLongExtra(BANALService.DEVICE_ID, -1) } returns id
            every { timeoutIntent.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false) } returns false
            receiver.onReceive(mockBanalService, timeoutIntent)
        }

        // d1 was already searching, d2 & d3 each started strictly once (zero tries 2..3 re-enqueues)
        verify(exactly = 0) { d1.startSearching() }
        verify(exactly = 1) { d2.startSearching() }
        verify(exactly = 1) { d3.startSearching() }
        listOf(d1, d2, d3).forEach { dev ->
            verify(exactly = 0) { dev.shutDown() }
            assertEquals("deviceTries entry must be cleaned for keep-alive device", false, triesMap.containsKey(dev))
        }
        assertTrue(devicesMap.containsKey(101L) && devicesMap.containsKey(102L) && devicesMap.containsKey(103L))
        assertTrue(deviceManager.searchQueue.isEmpty())
        assertEquals(null, deviceManager.currentlySearchingForRemoteDevice)
        assertEquals(false, DeviceManager.isSearchingForARemoteDevice())
    }
}
