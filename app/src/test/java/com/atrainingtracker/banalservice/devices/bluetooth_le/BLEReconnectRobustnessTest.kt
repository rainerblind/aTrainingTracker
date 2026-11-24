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

package com.atrainingtracker.banalservice.devices.bluetooth_le

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.TrackingMode
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying deterministic search timeout lifecycle, exponential reconnect backoff,
 * and shutdown guards in MyBTLEDevice (REQ-CON-020.2, TST-CON-012.2).
 */
class BLEReconnectRobustnessTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockGatt: BluetoothGatt
    private lateinit var mockBluetoothManager: BluetoothManager
    private lateinit var mockAdapter: BluetoothAdapter
    private lateinit var mockRemoteDevice: BluetoothDevice

    private val postedDelayedRunnables = mutableListOf<Pair<Runnable, Long>>()
    private val removedRunnables = mutableListOf<Runnable>()

    private class TestReconnectBTLEDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : MyBTLEDevice(context, sensorManager, DeviceType.HRM, deviceId, address) {
        override fun addSensors() {}
        override fun measurementCharacteristicUpdate(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {}

        fun triggerNotifyStopSearching(success: Boolean) {
            super.notifyStopSearching(success)
        }

        fun setHasConnectedInSession(hasConnected: Boolean) {
            setHasConnectedInSessionForTesting(hasConnected)
        }

        fun isScanning(): Boolean = isScanningForTesting
        fun getScanCallback(): ScanCallback? = scanCallbackForTesting
        fun getState(): State = mState
        fun isBackgroundReconnecting(): Boolean = isBackgroundReconnectingForTesting
        fun isCurrentGattAutoConnect(): Boolean = isCurrentGattAutoConnectForTesting
    }

    @Before
    fun setUp() {
        MyBTLEDevice.clearScanTimestampsForTesting()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        postedDelayedRunnables.clear()
        removedRunnables.clear()

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.equals(any(), any()) } answers {
            java.util.Objects.equals(firstArg<CharSequence?>(), secondArg<CharSequence?>())
        }
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val s = firstArg<CharSequence?>()
            s == null || s.isEmpty()
        }

        mockkStatic(androidx.core.content.ContextCompat::class)
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_SCAN)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_ADMIN)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every { androidx.core.content.ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(ActivityCompat::class)
        every {
            ActivityCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(com.atrainingtracker.banalservice.database.DevicesDatabaseManager::class)
        val mockDevicesDbManager = mockk<com.atrainingtracker.banalservice.database.DevicesDatabaseManager>(relaxed = true)
        every { com.atrainingtracker.banalservice.database.DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setPackage(any()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<String>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Int>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Long>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as android.content.Intent }

        mockkConstructor(android.bluetooth.le.ScanFilter.Builder::class)
        every { anyConstructed<android.bluetooth.le.ScanFilter.Builder>().setDeviceAddress(any()) } answers { self as android.bluetooth.le.ScanFilter.Builder }
        every { anyConstructed<android.bluetooth.le.ScanFilter.Builder>().build() } returns mockk(relaxed = true)

        mockkConstructor(android.bluetooth.le.ScanSettings.Builder::class)
        every { anyConstructed<android.bluetooth.le.ScanSettings.Builder>().setScanMode(any()) } answers { self as android.bluetooth.le.ScanSettings.Builder }
        every { anyConstructed<android.bluetooth.le.ScanSettings.Builder>().build() } returns mockk(relaxed = true)

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockGatt = mockk(relaxed = true)
        mockBluetoothManager = mockk(relaxed = true)
        mockAdapter = mockk(relaxed = true)
        mockRemoteDevice = mockk(relaxed = true)

        val mockScanner = mockk<BluetoothLeScanner>(relaxed = true)
        every { mockAdapter.bluetoothLeScanner } returns mockScanner
        every { mockAdapter.isEnabled } returns true

        val mockLooper = mockk<Looper>(relaxed = true)
        every { mockContext.mainLooper } returns mockLooper
        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBluetoothManager
        every { mockBluetoothManager.adapter } returns mockAdapter
        every { mockAdapter.getRemoteDevice(any<String>()) } returns mockRemoteDevice
    }

    @After
    fun tearDown() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        unmockkAll()
    }

    private fun injectTrackingHandler(device: MyBTLEDevice, runPosted: Boolean = false): Handler {
        val mockHandler = mockk<Handler>(relaxed = true)
        if (runPosted) {
            every { mockHandler.post(any()) } answers {
                firstArg<Runnable>().run()
                true
            }
        }
        every { mockHandler.postDelayed(any(), any()) } answers {
            val r = firstArg<Runnable>()
            val delay = secondArg<Long>()
            postedDelayedRunnables.add(Pair(r, delay))
            true
        }
        every { mockHandler.removeCallbacks(any<Runnable>()) } answers {
            val r = firstArg<Runnable>()
            removedRunnables.add(r)
        }
        every { mockHandler.removeCallbacksAndMessages(null) } answers {}

        val handlerField = MyBTLEDevice::class.java.getDeclaredField("mHandler")
        handlerField.isAccessible = true
        handlerField.set(device, mockHandler)
        return mockHandler
    }

    @Test
    fun testStartSearchingCancelsPriorTimeoutAndSchedulesNew() {
        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 201L, "AA:BB:CC:DD:EE:11")
        injectTrackingHandler(device)

        device.startSearching()

        // Verify prior search timeout was removed
        assertTrue(removedRunnables.contains(device.searchTimeoutRunnable))

        // Verify configurable BLE search timeout (default 5,000ms) was scheduled
        val timeoutScheduled = postedDelayedRunnables.any {
            it.first == device.searchTimeoutRunnable && it.second == TrainingApplication.getBtleSearchPeriodMs()
        }
        assertTrue(timeoutScheduled)
    }

    @Test
    fun testNotifyStopSearchingSuccessCancelsTimeoutAndResetsBackoff() {
        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 202L, "AA:BB:CC:DD:EE:12")
        injectTrackingHandler(device)

        device.triggerNotifyStopSearching(true)

        // Verify search timeout was cancelled
        assertTrue(removedRunnables.contains(device.searchTimeoutRunnable))
        assertEquals(1000L, device.currentReconnectDelayMs)
    }

    @Test
    fun testExponentialReconnectBackoffOnDisconnect() {
        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 203L, "AA:BB:CC:DD:EE:13")
        device.setHasConnectedInSession(true)
        injectTrackingHandler(device)

        val callback = device.gattCallbackForTesting

        // Initial delay starts at 1,000 ms
        assertEquals(1000L, device.currentReconnectDelayMs)

        // 1st Disconnect: schedules at 1,000ms, advances backoff to 2,000ms
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(2000L, device.currentReconnectDelayMs)
        assertEquals(1000L, postedDelayedRunnables.last().second)

        // 2nd Disconnect: schedules at 2,000ms, advances to 4,000ms
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(4000L, device.currentReconnectDelayMs)
        assertEquals(2000L, postedDelayedRunnables.last().second)

        // 3rd Disconnect: schedules at 4,000ms, advances to 8,000ms
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(8000L, device.currentReconnectDelayMs)
        assertEquals(4000L, postedDelayedRunnables.last().second)

        // 4th Disconnect: schedules at 8,000ms, advances to 16,000ms
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(8000L, postedDelayedRunnables.last().second)
        assertEquals(16000L, device.currentReconnectDelayMs)

        // 5th-7th Disconnects: extend 16s -> 32s -> 60s (1 min default final period, ATT-9178)
        val expectedExtendedSchedule = listOf(16_000L, 32_000L, 60_000L, 60_000L)
        for (idx in 0 until expectedExtendedSchedule.size - 1) {
            callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
            assertEquals(expectedExtendedSchedule[idx], postedDelayedRunnables.last().second)
            assertEquals(expectedExtendedSchedule[idx + 1], device.currentReconnectDelayMs)
        }

        // Now simulate successful reconnection: delay must reset to 1,000ms
        device.triggerNotifyStopSearching(true)
        assertEquals(1000L, device.currentReconnectDelayMs)
    }

    @Test
    fun testReconnectBackoff_whenPeriodicResearchDisabled_capsAt16sAndPreservesConstant() {
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        mockkStatic(androidx.preference.PreferenceManager::class)
        every { androidx.preference.PreferenceManager.getDefaultSharedPreferences(mockContext) } returns mockPrefs
        every { mockPrefs.contains(com.atrainingtracker.trainingtracker.settings.SensorSearchSettings.SP_PERIODIC_RESEARCH_ENABLED) } returns true
        every { mockPrefs.getBoolean(com.atrainingtracker.trainingtracker.settings.SensorSearchSettings.SP_PERIODIC_RESEARCH_ENABLED, true) } returns false

        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 211L, "AA:BB:CC:DD:EE:19")
        device.setHasConnectedInSession(true)
        injectTrackingHandler(device)
        val callback = device.gattCallbackForTesting

        repeat(6) {
            callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        }
        assertEquals(16_000L, device.currentReconnectDelayMs)
        assertEquals(16_000L, postedDelayedRunnables.last().second)
    }

    @Test
    fun testLiveSettingsChange_clampsStaleBackoffImmediatelyOnNextRetry() {
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        mockkStatic(androidx.preference.PreferenceManager::class)
        every { androidx.preference.PreferenceManager.getDefaultSharedPreferences(mockContext) } returns mockPrefs
        every { mockPrefs.contains(com.atrainingtracker.trainingtracker.settings.SensorSearchSettings.SP_PERIODIC_RESEARCH_ENABLED) } returns true
        every { mockPrefs.contains(com.atrainingtracker.trainingtracker.settings.SensorSearchSettings.SP_PERIODIC_RESEARCH_FINAL_PERIOD_SEC) } returns true

        var enabledSetting = true
        var periodSecSetting = 600 // legacy 10m clamps to 120s (2m)
        every { mockPrefs.getBoolean(com.atrainingtracker.trainingtracker.settings.SensorSearchSettings.SP_PERIODIC_RESEARCH_ENABLED, true) } answers { enabledSetting }
        every { mockPrefs.getInt(com.atrainingtracker.trainingtracker.settings.SensorSearchSettings.SP_PERIODIC_RESEARCH_FINAL_PERIOD_SEC, 60) } answers { periodSecSetting }

        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 212L, "AA:BB:CC:DD:EE:20")
        device.setHasConnectedInSession(true)
        injectTrackingHandler(device)
        val callback = device.gattCallbackForTesting

        // Back off all the way to 2 min (120,000 ms) from legacy 600s preference
        repeat(8) {
            callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        }
        assertEquals(120_000L, device.currentReconnectDelayMs)

        // User lowers final period to 30s -> next retry clamps immediately to 30,000 ms
        periodSecSetting = 30
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(30_000L, postedDelayedRunnables.last().second)

        // User disables periodic re-search -> next retry clamps immediately to 16,000 ms
        enabledSetting = false
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(16_000L, postedDelayedRunnables.last().second)
    }

    @Test
    fun testShutDownSetsFlagRemovesCallbacksAndSuppressesDisconnect() {
        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 204L, "AA:BB:CC:DD:EE:14")
        val handler = injectTrackingHandler(device)

        assertFalse(device.isShutDown)

        device.shutDown()

        assertTrue(device.isShutDown)
        assertTrue(removedRunnables.contains(device.searchTimeoutRunnable))
        assertTrue(removedRunnables.contains(device.reconnectRunnable))
        verify { handler.removeCallbacksAndMessages(null) }

        // Clear runnables list and verify disconnect after shutdown does not schedule reconnect
        postedDelayedRunnables.clear()
        device.gattCallbackForTesting.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertTrue(postedDelayedRunnables.isEmpty())

        // Also verify startSearching after shutdown is a no-op
        device.startSearching()
        assertTrue(postedDelayedRunnables.isEmpty())
    }

    @Test
    fun testMidWorkoutDisconnect_persistsReconnectionAfterSearchTimeout_reconnectsSuccessfully() {
        // REQ-CON-036.1, REQ-CON-036.2, TST-CON-036.1
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)

        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 205L, "AA:BB:CC:DD:EE:15")
        injectTrackingHandler(device)

        // Device previously connected in session
        device.setHasConnectedInSession(true)
        assertTrue(device.isMidSessionOrWorkoutActive)

        // Mid-workout LSTO Disconnect (HCI status = 8)
        val callback = device.gattCallbackForTesting
        callback.onConnectionStateChange(mockGatt, 8, BluetoothProfile.STATE_DISCONNECTED)

        // 1st Reconnect runnable posted at 1,000ms; next delay advanced to 2,000ms
        assertEquals(2000L, device.currentReconnectDelayMs)
        assertEquals(1000L, postedDelayedRunnables.last().second)
        val firstReconnectRunnable = postedDelayedRunnables.last().first

        // Simulate 1st reconnect firing -> calls startSearching()
        postedDelayedRunnables.clear()
        firstReconnectRunnable.run()

        // startSearching schedules search timeout (5,000ms)
        assertTrue(postedDelayedRunnables.any { it.first == device.searchTimeoutRunnable })

        // 1st Search timeout fires (zero scan results due to body shadowing)
        postedDelayedRunnables.clear()
        device.searchTimeoutRunnable.run()

        // MUST NOT abandon reconnection! Must reschedule reconnect at 2,000ms; next backoff advanced to 4,000ms
        val secondReconnectScheduled = postedDelayedRunnables.any {
            it.first == device.reconnectRunnable && it.second == 2000L
        }
        assertTrue("mReconnectRunnable must be rescheduled after search timeout mid-workout", secondReconnectScheduled)
        assertEquals(4000L, device.currentReconnectDelayMs)

        // 2nd Search timeout fires -> must reschedule at 4,000ms; next backoff 8,000ms
        postedDelayedRunnables.clear()
        device.searchTimeoutRunnable.run()
        val thirdReconnectScheduled = postedDelayedRunnables.any {
            it.first == device.reconnectRunnable && it.second == 4000L
        }
        assertTrue("mReconnectRunnable must back off exponentially on successive timeouts", thirdReconnectScheduled)
        assertEquals(8000L, device.currentReconnectDelayMs)

        // Peripheral returns and connects: backoff must reset to 1,000ms
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        assertEquals(1000L, device.currentReconnectDelayMs)
        assertTrue(device.hasConnectedInSession())
    }

    @Test
    fun testInitialStartupSearch_offlineSensor_terminatesCleanlyWithoutInfiniteRetry() {
        // REQ-CON-036.1, TST-CON-036.2
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)

        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 206L, "AA:BB:CC:DD:EE:16")
        injectTrackingHandler(device)

        assertFalse(device.hasConnectedInSession())
        assertFalse(device.isMidSessionOrWorkoutActive)

        // Startup search starts
        device.startSearching()
        assertTrue(postedDelayedRunnables.any { it.first == device.searchTimeoutRunnable })

        // Search timeout fires for offline sensor
        postedDelayedRunnables.clear()
        device.searchTimeoutRunnable.run()

        // Verify mReconnectRunnable is NOT posted (terminates cleanly without infinite retries)
        val reconnectScheduled = postedDelayedRunnables.any { it.first == device.reconnectRunnable }
        assertFalse("Never-connected offline sensor at startup must NOT schedule infinite reconnect loops", reconnectScheduled)
        assertFalse(device.isReceivingData)
    }

    @Test
    fun testScanQuotaExceeded_throttlesScanAndFallsBackToDirectConnect() {
        // REQ-CON-036.3, TST-CON-036.3
        MyBTLEDevice.clearScanTimestampsForTesting()

        // Seed 4 timestamps within 30s window to saturate quota
        val now = System.currentTimeMillis()
        for (i in 0 until 4) {
            MyBTLEDevice.addScanTimestampForTesting(now - (i * 1000L))
        }
        assertEquals(4, MyBTLEDevice.getScanTimestampsCountForTesting())

        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 207L, "AA:BB:CC:DD:EE:17")
        injectTrackingHandler(device)

        // startSearching should detect quota saturation, skip scan assist, and fall back to direct connect
        device.startSearching()

        assertFalse("Scan assist must be throttled when scan quota is saturated", device.isScanning())
    }

    @Test
    fun testOnScanFailed_fallsBackToGattConnect() {
        // REQ-CON-036.4, TST-CON-036.3
        MyBTLEDevice.clearScanTimestampsForTesting()

        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 208L, "AA:BB:CC:DD:EE:18")
        injectTrackingHandler(device)

        device.startSearching()
        assertTrue(device.isScanning())

        val scanCallback = device.getScanCallback()
        assertNotNull(scanCallback)

        // Trigger onScanFailed (e.g. application registration failed)
        scanCallback!!.onScanFailed(ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED)

        assertFalse("mIsScanning must be cleared on scan failure", device.isScanning())
    }

    /**
     * TST-CON-051.1 / TST-SET-125.1: During TrackingMode.TRACKING or PAUSED with mHasConnectedInSession == false,
     * mSearchTimeoutRunnable must transition mState out of SEARCHING, clear isSearching(), broadcast
     * SEARCHING_STOPPED_FOR_ONE_INTENT(false), converge to passive autoConnect=true without scheduling
     * aggressive mReconnectRunnable retries (ATT-9243).
     */
    @Test
    fun testSearchTimeout_duringTrackingAndPaused_clearsSearchingAndReleasesQueueWhilePersistingReconnect() {
        for (mode in listOf(TrackingMode.TRACKING, TrackingMode.PAUSED)) {
            MyBTLEDevice.clearScanTimestampsForTesting()
            TrainingApplication.setTrackingModeForTesting(mode)
            postedDelayedRunnables.clear()
            every { mockRemoteDevice.connectGatt(mockContext, true, any(), BluetoothDevice.TRANSPORT_LE) } returns mockGatt

            val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 301L, "AA:BB:CC:DD:EE:31")
            injectTrackingHandler(device, runPosted = true)

            assertFalse(device.hasConnectedInSession())
            assertTrue(device.shouldKeepAliveOnSearchTimeout())

            device.startSearching()
            assertTrue(device.isSearching)
            assertEquals(MyBTLEDevice.State.SEARCHING, device.getState())

            // Simulate GATT was not yet created (e.g. scan-assist started first) -> timeout fires
            val gattField = MyBTLEDevice::class.java.getDeclaredField("mBluetoothGatt")
            gattField.isAccessible = true
            gattField.set(device, null)

            postedDelayedRunnables.clear()
            device.searchTimeoutRunnable.run()

            assertEquals("mState must leave SEARCHING on timeout ($mode)", MyBTLEDevice.State.DISCONNECTED, device.getState())
            assertFalse("MyRemoteDevice.mSearching must be cleared on timeout ($mode)", device.isSearching)
            assertTrue("mIsBackgroundReconnecting must arm on timeout ($mode)", device.isBackgroundReconnecting())
            assertFalse(
                "Never-connected workout device must NOT schedule aggressive mReconnectRunnable ($mode)",
                postedDelayedRunnables.any { it.first == device.reconnectRunnable }
            )
            assertTrue("Passive autoConnect=true flag must be recorded ($mode)", device.isCurrentGattAutoConnect())
            verify { mockContext.sendBroadcast(any()) }
            verify { mockRemoteDevice.connectGatt(mockContext, true, any(), BluetoothDevice.TRANSPORT_LE) }
        }
    }

    /**
     * TST-CON-051.2: After STATE_DISCONNECTED -> disconnectFromGatt() clears mIsBackgroundReconnecting,
     * mReconnectRunnable (attemptBackgroundReconnect) re-arms mIsBackgroundReconnecting and triggers
     * autoConnect without setting mSearching=true or State.SEARCHING.
     */
    @Test
    fun testBackgroundReconnect_afterDisconnect_reArmsWithoutSettingSearchingOrStateSearching() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)

        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 302L, "AA:BB:CC:DD:EE:32")
        injectTrackingHandler(device, runPosted = true)
        device.setHasConnectedInSession(true)

        // Disconnect clears mIsBackgroundReconnecting and posts mReconnectRunnable
        device.gattCallbackForTesting.onConnectionStateChange(
            mockGatt,
            8,
            BluetoothProfile.STATE_DISCONNECTED
        )
        assertFalse(device.isBackgroundReconnecting())
        val reconnectRunnable = postedDelayedRunnables.last { it.first == device.reconnectRunnable }.first

        // Run background reconnect
        postedDelayedRunnables.clear()
        reconnectRunnable.run()

        assertTrue("attemptBackgroundReconnect must re-arm mIsBackgroundReconnecting", device.isBackgroundReconnecting())
        assertFalse("Background reconnect must NOT set MyRemoteDevice.mSearching=true", device.isSearching)
        assertEquals("Background reconnect must NOT set State.SEARCHING", MyBTLEDevice.State.DISCONNECTED, device.getState())
        assertTrue(postedDelayedRunnables.any { it.first == device.searchTimeoutRunnable })
        verify { mockRemoteDevice.connectGatt(mockContext, true, any(), BluetoothDevice.TRANSPORT_LE) }

        // Timeout on background reconnect must double backoff without setting mSearching
        postedDelayedRunnables.clear()
        device.searchTimeoutRunnable.run()
        assertFalse(device.isSearching)
        assertEquals(MyBTLEDevice.State.DISCONNECTED, device.getState())
        assertTrue(postedDelayedRunnables.any { it.first == device.reconnectRunnable && it.second == 2000L })

        // ShutDown cleanly clears background reconnect flag & keep-alive
        device.shutDown()
        assertFalse(device.isBackgroundReconnecting())
        assertFalse(device.shouldKeepAliveOnSearchTimeout())
    }

    /**
     * Stage 6 Bounce 1/3 heal (REQ-CON-051.1, TST-CON-051.1): When Binder onConnectionStateChange
     * transitions mState to CONNECTED_TO_GATT before posted discoverServices/notifyStopSearching(true)
     * runs, mSearchTimeoutRunnable must abort without arming mIsBackgroundReconnecting, scheduling
     * mReconnectRunnable, or firing notifyStopSearching(false).
     */
    @Test
    fun testSearchTimeout_whenAlreadyConnectedToGatt_abortsWithoutReconnectOrStopSearching() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)

        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 303L, "AA:BB:CC:DD:EE:33")
        val handler = injectTrackingHandler(device, runPosted = false)
        val postedToMain = mutableListOf<Runnable>()
        every { handler.post(any()) } answers {
            postedToMain.add(firstArg())
            true
        }

        device.startSearching()
        assertTrue(device.isSearching)
        assertEquals(MyBTLEDevice.State.SEARCHING, device.getState())

        // Simulate Binder thread transitioning to CONNECTED_TO_GATT before posted discoverServices/notifyStopSearching(true) runs
        val gattField = MyBTLEDevice::class.java.getDeclaredField("mBluetoothGatt")
        gattField.isAccessible = true
        gattField.set(device, mockGatt)
        postedToMain.clear()
        device.gattCallbackForTesting.onConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
        assertEquals(MyBTLEDevice.State.CONNECTED_TO_GATT, device.getState())
        assertEquals(1, postedToMain.size)

        // Fire mSearchTimeoutRunnable before posted runnable executes
        postedDelayedRunnables.clear()
        clearMocks(mockContext, answers = false, recordedCalls = true)
        device.searchTimeoutRunnable.run()

        assertEquals(MyBTLEDevice.State.CONNECTED_TO_GATT, device.getState())
        assertFalse("Must not arm background reconnect when already CONNECTED_TO_GATT", device.isBackgroundReconnecting())
        assertTrue("Must not schedule mReconnectRunnable when already CONNECTED_TO_GATT", postedDelayedRunnables.isEmpty())
        assertTrue("Queue searching flag must remain untouched until notifyStopSearching(true) runs", device.isSearching)
        verify(exactly = 0) { mockContext.sendBroadcast(any()) }

        // Completing the posted discoverServices/notifyStopSearching(true) runnable finishes cleanly
        postedToMain.first().run()
        assertFalse(device.isSearching)
        verify(exactly = 1) { mockGatt.discoverServices() }
        verify(exactly = 1) { mockContext.sendBroadcast(any()) }
    }

    /**
     * TST-SET-125.1: 3 absent never-connected paired BLE sensors during TRACKING/PAUSED consume at most
     * 3 initial scans (<= 4 in 30s, 0 quota throttles), converge to passive autoConnect=true with 0
     * mReconnectRunnable retries, and upgrade to fast reconnect + scan-assist once passively connected.
     */
    @Test
    fun testNeverConnectedThreeAbsentSensors_duringWorkout_convergePassiveAndUpgradeOnWakeUp_TST_SET_125_1() {
        MyBTLEDevice.clearScanTimestampsForTesting()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        every { mockRemoteDevice.connectGatt(mockContext, true, any(), BluetoothDevice.TRANSPORT_LE) } returns mockGatt

        val devices = listOf(
            TestReconnectBTLEDevice(mockContext, mockSensorManager, 401L, "AA:BB:CC:DD:EE:41"),
            TestReconnectBTLEDevice(mockContext, mockSensorManager, 402L, "AA:BB:CC:DD:EE:42"),
            TestReconnectBTLEDevice(mockContext, mockSensorManager, 403L, "AA:BB:CC:DD:EE:43")
        )
        devices.forEach { injectTrackingHandler(it, runPosted = true) }

        for (device in devices) {
            device.startSearching()
            assertTrue("Initial discovery scan must succeed without throttling", device.isScanning())
            postedDelayedRunnables.clear()
            device.searchTimeoutRunnable.run()

            assertEquals(MyBTLEDevice.State.DISCONNECTED, device.getState())
            assertFalse(device.isSearching)
            assertTrue(device.isBackgroundReconnecting())
            assertTrue(device.isCurrentGattAutoConnect())
            assertFalse(
                "Never-connected workout sensor must not schedule mReconnectRunnable",
                postedDelayedRunnables.any { it.first == device.reconnectRunnable }
            )
        }
        assertEquals("3 absent paired sensors must consume strictly 3 scans (<= 4 quota)", 3, MyBTLEDevice.getScanTimestampsCountForTesting())

        // Sensor #1 wakes up mid-ride via passive autoConnect=true -> upgrades to session-connected
        val awakened = devices.first()
        awakened.gattCallbackForTesting.onConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
        assertTrue(awakened.hasConnectedInSession())
        assertEquals(1000L, awakened.currentReconnectDelayMs)

        // Subsequent mid-workout drop on awakened sensor gets fast 1s reconnect + active scan-assist
        MyBTLEDevice.clearScanTimestampsForTesting()
        postedDelayedRunnables.clear()
        awakened.gattCallbackForTesting.onConnectionStateChange(
            mockGatt,
            8,
            BluetoothProfile.STATE_DISCONNECTED
        )
        assertEquals(1000L, postedDelayedRunnables.last { it.first == awakened.reconnectRunnable }.second)

        postedDelayedRunnables.clear()
        awakened.reconnectRunnable.run()
        assertEquals("Session-connected reconnect must use active scan-assist", 1, MyBTLEDevice.getScanTimestampsCountForTesting())
        assertTrue(
            "Session-connected reconnect must arm mSearchTimeoutRunnable",
            postedDelayedRunnables.any { it.first == awakened.searchTimeoutRunnable }
        )
    }

    /**
     * TST-SET-125.1: Try 1 starts in READY (autoConnect=false GATT) and times out after transitioning
     * to TRACKING -> atomically closes non-autoConnect GATT and opens passive autoConnect=true GATT
     * without scheduling mReconnectRunnable, while guarding against concurrent STATE_CONNECTED or shutDown.
     */
    @Test
    fun testSearchTimeout_readyToTrackingEdge_replacesNonAutoConnectGattAndGuardsConcurrentRaces_TST_SET_125_1() {
        // 1. Happy path: Try 1 in READY (quota full -> direct connectGatt(autoConnect=false)), then TRACKING before timeout
        MyBTLEDevice.clearScanTimestampsForTesting()
        val now = System.currentTimeMillis()
        repeat(4) { MyBTLEDevice.addScanTimestampForTesting(now - it * 500L) }

        val readyGatt = mockk<BluetoothGatt>(relaxed = true)
        val passiveGatt = mockk<BluetoothGatt>(relaxed = true)
        every { mockRemoteDevice.connectGatt(mockContext, false, any(), BluetoothDevice.TRANSPORT_LE) } returns readyGatt
        every { mockRemoteDevice.connectGatt(mockContext, true, any(), BluetoothDevice.TRANSPORT_LE) } returns passiveGatt

        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 404L, "AA:BB:CC:DD:EE:44")
        injectTrackingHandler(device, runPosted = true)

        device.startSearching()
        assertFalse("READY direct connect must have autoConnect=false", device.isCurrentGattAutoConnect())
        verify(exactly = 1) { mockRemoteDevice.connectGatt(mockContext, false, any(), BluetoothDevice.TRANSPORT_LE) }

        // User starts workout before Try 1 timeout fires
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        postedDelayedRunnables.clear()
        device.searchTimeoutRunnable.run()

        verify(exactly = 1) { readyGatt.disconnect() }
        verify(exactly = 1) { readyGatt.close() }
        verify(exactly = 1) { mockRemoteDevice.connectGatt(mockContext, true, any(), BluetoothDevice.TRANSPORT_LE) }
        assertTrue("Replaced GATT must track passive autoConnect=true", device.isCurrentGattAutoConnect())
        assertTrue("mIsBackgroundReconnecting must stay true across replacement", device.isBackgroundReconnecting())
        assertFalse(device.isSearching)
        assertFalse(postedDelayedRunnables.any { it.first == device.reconnectRunnable })

        // 2. Race guard: shutDown before posted replacement closeRunnable completes must NOT open passive GATT
        val raceGatt = mockk<BluetoothGatt>(relaxed = true)
        clearMocks(mockRemoteDevice, answers = false, recordedCalls = true)
        every { mockRemoteDevice.connectGatt(mockContext, false, any(), BluetoothDevice.TRANSPORT_LE) } returns raceGatt
        every { mockRemoteDevice.connectGatt(mockContext, true, any(), BluetoothDevice.TRANSPORT_LE) } returns passiveGatt

        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        val raceDevice = TestReconnectBTLEDevice(mockContext, mockSensorManager, 405L, "AA:BB:CC:DD:EE:45")
        val postedQueue = mutableListOf<Runnable>()
        val handler = injectTrackingHandler(raceDevice, runPosted = false)
        every { handler.post(any()) } answers {
            postedQueue.add(firstArg())
            true
        }

        raceDevice.startSearching()
        // Run triggerGattConnect posted runnable from READY
        postedQueue.forEach { it.run() }
        postedQueue.clear()
        assertFalse(raceDevice.isCurrentGattAutoConnect())

        // Transition to TRACKING -> timeout posts closeRunnable + onGattClosed, while mIsBackgroundReconnecting stays true
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        raceDevice.searchTimeoutRunnable.run()
        assertTrue("mIsBackgroundReconnecting must remain true while closeRunnable is pending", raceDevice.isBackgroundReconnecting())
        assertEquals(1, postedQueue.size)

        // shutDown arrives before posted closeRunnable finishes -> onGattClosed MUST NOT call connectGatt(true)
        raceDevice.shutDown()
        postedQueue.forEach { it.run() }
        verify(exactly = 0) { mockRemoteDevice.connectGatt(mockContext, true, any(), BluetoothDevice.TRANSPORT_LE) }
    }

    /**
     * TST-SET-125.2: Spurious STATE_DISCONNECTED (133/8) on a never-connected workout sensor floors
     * reconnect delay >= 60,000 ms, attemptBackgroundReconnect & resumeGattOperations skip both
     * mSearchTimeoutRunnable and startScanAssistance (0 scan quota), while READY retains mSearchTimeoutRunnable.
     */
    @Test
    fun testNeverConnectedWorkoutDisconnect_floorsDelay60sAndSkipsScanAssistAndTimeout_TST_SET_125_2() {
        MyBTLEDevice.clearScanTimestampsForTesting()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        every { mockRemoteDevice.connectGatt(mockContext, true, any(), BluetoothDevice.TRANSPORT_LE) } returns mockGatt

        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 406L, "AA:BB:CC:DD:EE:46")
        injectTrackingHandler(device, runPosted = true)
        assertFalse(device.hasConnectedInSession())

        // Spurious GATT 133 disconnect during active workout -> delay floored at >= 60_000 ms
        postedDelayedRunnables.clear()
        device.gattCallbackForTesting.onConnectionStateChange(mockGatt, 133, BluetoothProfile.STATE_DISCONNECTED)
        val scheduledDisconnectRetry = postedDelayedRunnables.last { it.first == device.reconnectRunnable }
        assertTrue("Never-connected workout disconnect delay must be >= 60_000 ms", scheduledDisconnectRetry.second >= 60_000L)

        // Executing mReconnectRunnable -> attemptBackgroundReconnect skips scan-assist & mSearchTimeoutRunnable
        MyBTLEDevice.clearScanTimestampsForTesting()
        postedDelayedRunnables.clear()
        scheduledDisconnectRetry.first.run()

        assertEquals("Never-connected attemptBackgroundReconnect must consume 0 scan quota", 0, MyBTLEDevice.getScanTimestampsCountForTesting())
        assertFalse("Never-connected attemptBackgroundReconnect must NOT schedule mSearchTimeoutRunnable", postedDelayedRunnables.any { it.first == device.searchTimeoutRunnable })
        assertTrue(device.isBackgroundReconnecting())
        assertFalse(device.isSearching)
        assertEquals(MyBTLEDevice.State.DISCONNECTED, device.getState())
        verify(atLeast = 1) { mockRemoteDevice.connectGatt(mockContext, true, any(), BluetoothDevice.TRANSPORT_LE) }

        // Pause -> resumeGattOperations while never-connected & mIsBackgroundReconnecting=true -> passive resume (0 scans, 0 timeout)
        postedDelayedRunnables.clear()
        device.pauseGattOperations()
        device.resumeGattOperations()
        assertEquals("Passive resumeGattOperations must consume 0 scan quota", 0, MyBTLEDevice.getScanTimestampsCountForTesting())
        assertFalse("Passive resumeGattOperations must NOT schedule mSearchTimeoutRunnable", postedDelayedRunnables.any { it.first == device.searchTimeoutRunnable })
        assertFalse(device.isSearching)

        // Contrast: in READY mode (!isMidSessionOrWorkoutActive), attemptBackgroundReconnect MUST retain mSearchTimeoutRunnable
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        val readyDevice = TestReconnectBTLEDevice(mockContext, mockSensorManager, 407L, "AA:BB:CC:DD:EE:47")
        injectTrackingHandler(readyDevice, runPosted = true)
        postedDelayedRunnables.clear()
        readyDevice.reconnectRunnable.run()
        assertTrue("READY attemptBackgroundReconnect must retain mSearchTimeoutRunnable", postedDelayedRunnables.any { it.first == readyDevice.searchTimeoutRunnable })
    }

    /**
     * TST-CON-052.2 (ATT-9178): Never-connected BLE device in READY receiving STATE_DISCONNECTED
     * MUST NOT schedule mReconnectRunnable (both when isSearching==true and isSearching==false),
     * and MUST call notifyStopSearching(false) when isSearching==true so DeviceManager never stalls.
     */
    @Test
    fun testNeverConnectedReadyDisconnect_schedulesZeroReconnectsAndStopsInitialSearch() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        every { mockRemoteDevice.connectGatt(mockContext, false, any(), BluetoothDevice.TRANSPORT_LE) } returns mockGatt

        // Case A: isSearching == true (immediate GATT 133 during initial search)
        val searchingDevice = TestReconnectBTLEDevice(mockContext, mockSensorManager, 408L, "AA:BB:CC:DD:EE:48")
        injectTrackingHandler(searchingDevice, runPosted = true)
        searchingDevice.startSearching()
        assertTrue(searchingDevice.isSearching)
        postedDelayedRunnables.clear()

        searchingDevice.gattCallbackForTesting.onConnectionStateChange(mockGatt, 133, BluetoothProfile.STATE_DISCONNECTED)
        assertFalse("Initial READY GATT 133 disconnect must release search queue", searchingDevice.isSearching)
        assertFalse("Never-connected READY disconnect must schedule 0 mReconnectRunnable", postedDelayedRunnables.any { it.first == searchingDevice.reconnectRunnable })

        // Case B: isSearching == false (spurious disconnect on never-connected READY device)
        val idleDevice = TestReconnectBTLEDevice(mockContext, mockSensorManager, 409L, "AA:BB:CC:DD:EE:49")
        injectTrackingHandler(idleDevice, runPosted = true)
        assertFalse(idleDevice.isSearching)
        postedDelayedRunnables.clear()

        idleDevice.gattCallbackForTesting.onConnectionStateChange(mockGatt, 133, BluetoothProfile.STATE_DISCONNECTED)
        assertFalse("Never-connected READY !isSearching disconnect must schedule 0 mReconnectRunnable", postedDelayedRunnables.any { it.first == idleDevice.reconnectRunnable })
    }

    /**
     * TST-CON-052.2 (ATT-9178): resetSessionConnectionState() preserves live connected devices,
     * never cancels mSearchTimeoutRunnable while SEARCHING (no queue wedge), and clears/closes
     * passive GATT handles when DISCONNECTED && !isSearching().
     */
    @Test
    fun testResetSessionConnectionState_safeAcrossConnectedSearchingAndDisconnectedStates() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)

        // 1. CONNECTED_WITH_SERVICE -> preserved intact
        val live = TestReconnectBTLEDevice(mockContext, mockSensorManager, 410L, "AA:BB:CC:DD:EE:50")
        injectTrackingHandler(live, runPosted = true)
        live.setHasConnectedInSession(true)
        live.setStateForTesting(MyBTLEDevice.State.CONNECTED_WITH_SERVICE)
        live.setBluetoothGattForTesting(mockGatt)
        live.resetSessionConnectionState()
        assertTrue("Connected device must keep hasConnectedInSession", live.hasConnectedInSession())

        // 2. SEARCHING -> clears session flag & reconnectRunnable WITHOUT cancelling mSearchTimeoutRunnable
        val searching = TestReconnectBTLEDevice(mockContext, mockSensorManager, 411L, "AA:BB:CC:DD:EE:51")
        injectTrackingHandler(searching, runPosted = true)
        every { mockRemoteDevice.connectGatt(mockContext, any(), any(), BluetoothDevice.TRANSPORT_LE) } returns mockGatt
        searching.setHasConnectedInSession(true)
        searching.startSearching()
        removedRunnables.clear()
        searching.resetSessionConnectionState()
        assertFalse("SEARCHING reset must clear hasConnectedInSession", searching.hasConnectedInSession())
        assertTrue("SEARCHING reset must remove mReconnectRunnable", removedRunnables.contains(searching.reconnectRunnable))
        assertFalse("SEARCHING reset must NOT cancel mSearchTimeoutRunnable (no queue wedge)", removedRunnables.contains(searching.searchTimeoutRunnable))
        assertTrue("SEARCHING reset must keep isSearching true until timeout/callback", searching.isSearching)

        // 3. DISCONNECTED && !isSearching() -> clears flags and closes passive GATT handle atomically
        val disconnected = TestReconnectBTLEDevice(mockContext, mockSensorManager, 412L, "AA:BB:CC:DD:EE:52")
        injectTrackingHandler(disconnected, runPosted = true)
        val passiveGatt = mockk<BluetoothGatt>(relaxed = true)
        disconnected.setHasConnectedInSession(true)
        disconnected.setStateForTesting(MyBTLEDevice.State.DISCONNECTED)
        disconnected.setBluetoothGattForTesting(passiveGatt)
        disconnected.resetSessionConnectionState()
        assertFalse(disconnected.hasConnectedInSession())
        assertFalse(disconnected.isBackgroundReconnecting())
        verify(atLeast = 1) { passiveGatt.disconnect() }
        verify(atLeast = 1) { passiveGatt.close() }

        // 4. CONNECTED_TO_GATT (mid-handshake) -> preserved intact with zero GATT close
        val connecting = TestReconnectBTLEDevice(mockContext, mockSensorManager, 413L, "AA:BB:CC:DD:EE:53")
        injectTrackingHandler(connecting, runPosted = true)
        val liveGatt = mockk<BluetoothGatt>(relaxed = true)
        connecting.setHasConnectedInSession(true)
        connecting.setStateForTesting(MyBTLEDevice.State.CONNECTED_TO_GATT)
        connecting.setBluetoothGattForTesting(liveGatt)
        connecting.resetSessionConnectionState()
        assertTrue("CONNECTED_TO_GATT must keep hasConnectedInSession", connecting.hasConnectedInSession())
        verify(exactly = 0) { liveGatt.disconnect() }
        verify(exactly = 0) { liveGatt.close() }
    }

    /**
     * TST-CON-052.2 (ATT-9178 Bounce 1): GATT-133 during active search in TRACKING (never-connected)
     * and during MIA periodic re-search MUST release the search queue via notifyStopSearching(false)
     * while scheduling the appropriate reconnect backoff (preventing search-queue deadlock).
     */
    @Test
    fun testMidWorkoutAndMiaGatt133DuringActiveSearch_releasesSearchQueueAndSchedulesBackoff() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        every { mockRemoteDevice.connectGatt(mockContext, any(), any(), BluetoothDevice.TRANSPORT_LE) } returns mockGatt

        // Case A: Never-connected sensor during active TRACKING search hits GATT-133
        val neverConnected = TestReconnectBTLEDevice(mockContext, mockSensorManager, 414L, "AA:BB:CC:DD:EE:54")
        injectTrackingHandler(neverConnected, runPosted = true)
        neverConnected.startSearching()
        assertTrue(neverConnected.isSearching)
        postedDelayedRunnables.clear()

        neverConnected.gattCallbackForTesting.onConnectionStateChange(mockGatt, 133, BluetoothProfile.STATE_DISCONNECTED)
        assertFalse("Never-connected TRACKING GATT-133 must call notifyStopSearching(false)", neverConnected.isSearching)
        val neverConnectedDelay = postedDelayedRunnables.last { it.first == neverConnected.reconnectRunnable }.second
        assertTrue("Never-connected TRACKING GATT-133 must floor reconnect >= 60s", neverConnectedDelay >= 60_000L)

        // Case B: MIA sensor during periodic re-search hits GATT-133
        val miaSensor = TestReconnectBTLEDevice(mockContext, mockSensorManager, 415L, "AA:BB:CC:DD:EE:55")
        injectTrackingHandler(miaSensor, runPosted = true)
        miaSensor.setHasConnectedInSession(true)
        miaSensor.startSearching()
        assertTrue(miaSensor.isSearching)
        postedDelayedRunnables.clear()

        miaSensor.gattCallbackForTesting.onConnectionStateChange(mockGatt, 133, BluetoothProfile.STATE_DISCONNECTED)
        assertFalse("MIA GATT-133 during active re-search must call notifyStopSearching(false)", miaSensor.isSearching)
        assertTrue(
            "MIA GATT-133 must still schedule reconnect backoff",
            postedDelayedRunnables.any { it.first == miaSensor.reconnectRunnable }
        )
    }
}
