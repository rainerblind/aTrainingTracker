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
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.sensor.MySensorManager
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Queue
import java.util.UUID

/**
 * Automated unit test suite verifying BLE GATT lifecycle management, thread concurrency,
 * TOCTOU race condition immunity, queue draining, and NullPointerException shielding
 * in MyBTLEDevice (REQ-CON-012, TST-CON-003, ATT-1348).
 */
class MyBTLEDeviceLifecycleTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockGatt: BluetoothGatt
    private lateinit var mockCharacteristic: BluetoothGattCharacteristic
    private val postedRunnables = mutableListOf<Runnable>()
    private val delayedRunnables = mutableListOf<Runnable>()
    private val removedRunnables = mutableListOf<Runnable>()

    // Concrete test harness subclass exposing protected APIs
    private class TestBTLEDevice(
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

        fun getQueue(): Queue<BluetoothGattCharacteristic> = mReadCharacteristicQueue
        fun setGatt(gatt: BluetoothGatt?) { mBluetoothGatt = gatt }
        fun getGatt(): BluetoothGatt? = mBluetoothGatt
        public override fun readNextCharacteristic() { super.readNextCharacteristic() }
        public override fun disconnectFromGatt() { super.disconnectFromGatt() }

        fun triggerCharacteristicUpdate(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            val method = MyBTLEDevice::class.java.getDeclaredMethod(
                "characteristicUpdate",
                BluetoothGatt::class.java,
                BluetoothGattCharacteristic::class.java
            )
            method.isAccessible = true
            method.invoke(this, gatt, characteristic)
        }

        fun getGattCallback(): BluetoothGattCallback {
            val field = MyBTLEDevice::class.java.getDeclaredField("mGattCallback")
            field.isAccessible = true
            return field.get(this) as BluetoothGattCallback
        }
    }

    @Before
    fun setUp() {
        MyBTLEDevice.clearScanTimestampsForTesting()
        postedRunnables.clear()
        delayedRunnables.clear()
        removedRunnables.clear()

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.equals(any(), any()) } answers {
            java.util.Objects.equals(firstArg<CharSequence?>(), secondArg<CharSequence?>())
        }
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val s = firstArg<CharSequence?>()
            s == null || s.isEmpty()
        }

        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(ActivityCompat::class)
        every {
            ActivityCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDbManager = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setPackage(any()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<String>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Int>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Long>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<java.io.Serializable>()) } answers { self as android.content.Intent }

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers {
            val runnable = firstArg<Runnable>()
            postedRunnables.add(runnable)
            true
        }
        every { anyConstructed<Handler>().postDelayed(any(), any()) } answers {
            val runnable = firstArg<Runnable>()
            delayedRunnables.add(runnable)
            true
        }
        every { anyConstructed<Handler>().removeCallbacks(any<Runnable>()) } answers {
            val runnable = firstArg<Runnable>()
            removedRunnables.add(runnable)
        }
        every { anyConstructed<Handler>().removeCallbacksAndMessages(any()) } answers {
            postedRunnables.clear()
            delayedRunnables.clear()
        }

        mockContext = mockk(relaxed = true)
        val mockLooper = mockk<Looper>(relaxed = true)
        every { mockContext.mainLooper } returns mockLooper
        mockSensorManager = mockk(relaxed = true)

        mockGatt = mockk(relaxed = true)
        mockCharacteristic = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TC-1: Verify that when mReadCharacteristicQueue contains characteristics but mBluetoothGatt
     * is null, readNextCharacteristic() processes the queue and executes posted runnables
     * without throwing NullPointerException (fixing ATT-1348).
     */
    @Test
    fun testReadNextCharacteristic_whenGattIsNull_doesNotThrowNpe() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(null)
        device.getQueue().add(mockCharacteristic)

        // Act
        device.readNextCharacteristic()

        // Assert: Queue was polled, runnable was posted, and running it throws no NPE
        assertTrue(device.getQueue().isEmpty())
        assertEquals(1, postedRunnables.size)

        // Execute posted runnable on looper
        postedRunnables.first().run()
        // Success: No NullPointerException thrown
    }

    /**
     * TC-2: Verify that when mReadCharacteristicQueue is empty and sensors are not registered,
     * the empty-queue service discovery branch does not crash when mBluetoothGatt is null.
     */
    @Test
    fun testReadNextCharacteristic_whenQueueEmpty_andGattIsNull_doesNotThrowNpe() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(null)
        assertTrue(device.getQueue().isEmpty())

        // Act
        device.readNextCharacteristic()

        // Assert: Runnable was posted for service registration
        assertEquals(1, postedRunnables.size)

        // Execute posted runnable
        postedRunnables.first().run()
        // Success: Defensive null guard on mBluetoothGatt returned cleanly without NPE
    }

    /**
     * TC-3: Verify that if mBluetoothGatt is set to null concurrently between the posting of
     * the runnable and its execution on the main Looper (TOCTOU race), no crash occurs.
     */
    @Test
    fun testReadNextCharacteristic_toctouRaceConditionSimulation_doesNotThrowNpe() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(mockGatt)
        device.getQueue().add(mockCharacteristic)

        // Act: Post read runnable
        device.readNextCharacteristic()
        assertEquals(1, postedRunnables.size)

        // Simulate concurrent disconnection on a Binder thread before Looper processes runnable
        device.setGatt(null)

        // Execute the queued runnable on the main Looper
        postedRunnables.first().run()

        // Verify: No NPE was thrown
        verify(exactly = 0) { mockGatt.readCharacteristic(any()) }
    }

    /**
     * TC-4: Verify that invoking disconnectFromGatt() purges all queued characteristics,
     * nullifies mBluetoothGatt under mGattLock, and cancels pending battery re-reads.
     */
    @Test
    fun testDisconnectFromGatt_clearsQueue_andNullifiesGattUnderLock() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(mockGatt)
        device.getQueue().add(mockCharacteristic)
        device.getQueue().add(mockk(relaxed = true))

        // Act
        device.disconnectFromGatt()

        // Assert: Queue is cleared and GATT reference is immediately nullified
        assertTrue(device.getQueue().isEmpty())
        assertNull(device.getGatt())

        // Execute the asynchronous cleanup posted by disconnectFromGatt
        assertEquals(1, postedRunnables.size)
        postedRunnables.first().run()

        verify(exactly = 1) { mockGatt.disconnect() }
        verify(exactly = 1) { mockGatt.close() }
    }

    /**
     * TC-5: Verify that the 5-minute delayed battery re-read runnable is cancelled
     * when disconnectFromGatt() is invoked, preventing execution against dead connections.
     */
    @Test
    fun testDelayedBatteryRunnable_cancelledOnDisconnection_doesNotExecute() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(mockGatt)

        val batteryChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
        every { batteryChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL
        every { batteryChar.value } returns byteArrayOf(85)
        every { batteryChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) } returns 85

        // Act 1: Simulate characteristic update for battery level
        device.triggerCharacteristicUpdate(mockGatt, batteryChar)

        // Verify delayed runnable was scheduled
        assertEquals(1, delayedRunnables.size)
        val scheduledRunnable = delayedRunnables.first()

        // Act 2: Peripheral disconnects
        device.disconnectFromGatt()

        // Verify removeCallbacks was invoked for the battery runnable
        assertTrue(removedRunnables.contains(scheduledRunnable))

        // Act 3: Simulate looper firing the delayed runnable anyway
        scheduledRunnable.run()

        // Verify: Queue remains empty, zero reads attempted because GATT is null
        assertTrue(device.getQueue().isEmpty())
    }

    /**
     * TC-6: Verify that onConnectionStateChange(STATE_CONNECTED) guards discoverServices()
     * if mBluetoothGatt was not initialized or returned null.
     */
    @Test
    fun testOnConnectionStateChange_connected_guardsDiscoverServicesWhenGattNull() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(null)

        val callback = device.getGattCallback()

        // Act
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)

        assertEquals(1, postedRunnables.size)
        postedRunnables.first().run()

        // Verify: Zero exceptions, discoverServices not called on null
        verify(exactly = 0) { mockGatt.discoverServices() }
    }

    /**
     * TC-7: Verify that shutDown() cleanly clears callbacks, queue, and nullifies GATT.
     */
    @Test
    fun testShutDown_cleansUpAllCallbacksAndGatt() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(mockGatt)
        device.getQueue().add(mockCharacteristic)

        // Act
        device.shutDown()

        // Assert
        assertTrue(device.getQueue().isEmpty())
        assertNull(device.getGatt())
    }

    /**
     * TC-8: Verify that when BLUETOOTH_CONNECT permission is missing, operations
     * terminate cleanly without SecurityException.
     */
    @Test
    fun testReadNextCharacteristic_whenPermissionDenied_returnsEarlyWithoutCallingGatt() {
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_DENIED

        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(mockGatt)
        device.getQueue().add(mockCharacteristic)

        // Act
        device.readNextCharacteristic()
        assertEquals(1, postedRunnables.size)
        postedRunnables.first().run()

        // Assert: Permission check aborted execution before calling gatt
        verify(exactly = 0) { mockGatt.readCharacteristic(any()) }
    }

    /**
     * TST-CON-011.1: Verify that on API 23+, startSearching connects GATT with BluetoothDevice.TRANSPORT_LE.
     */
    @Test
    fun testStartSearching_onApi23Plus_connectsGattWithTransportLe() {
        val mockBtManager = mockk<BluetoothManager>(relaxed = true)
        val mockAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val mockDevice = mockk<BluetoothDevice>(relaxed = true)

        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBtManager
        every { mockBtManager.adapter } returns mockAdapter
        every { mockAdapter.getRemoteDevice("AA:BB:CC:DD:EE:01") } returns mockDevice

        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:01")

        // Act
        device.startSearching()
        assertEquals(1, postedRunnables.size)
        postedRunnables.first().run()

        // Assert: connectGatt was called with TRANSPORT_LE
        verify(exactly = 1) {
            mockDevice.connectGatt(mockContext, false, any(), BluetoothDevice.TRANSPORT_LE)
        }
    }

    /**
     * TST-CON-011.1: Verify that when BLUETOOTH_CONNECT is denied, startSearching does not call connectGatt.
     */
    @Test
    fun testStartSearching_whenPermissionDenied_doesNotCallConnectGatt() {
        every {
            ActivityCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_DENIED

        val mockBtManager = mockk<BluetoothManager>(relaxed = true)
        val mockAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val mockDevice = mockk<BluetoothDevice>(relaxed = true)

        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBtManager
        every { mockBtManager.adapter } returns mockAdapter
        every { mockAdapter.getRemoteDevice("AA:BB:CC:DD:EE:02") } returns mockDevice

        val device = TestBTLEDevice(mockContext, mockSensorManager, 2L, "AA:BB:CC:DD:EE:02")

        // Act
        device.startSearching()
        assertEquals(1, postedRunnables.size)
        postedRunnables.first().run()

        // Assert: connectGatt never called
        verify(exactly = 0) { mockDevice.connectGatt(any<Context>(), any<Boolean>(), any<BluetoothGattCallback>(), any<Int>()) }
        verify(exactly = 0) { mockDevice.connectGatt(any<Context>(), any<Boolean>(), any<BluetoothGattCallback>()) }
    }

    /**
     * TST-CON-051.3: Verify pauseGattOperations -> resumeGattOperations while background reconnecting
     * resumes via decoupled reconnect without setting MyRemoteDevice.mSearching=true or State.SEARCHING.
     */
    @Test
    fun testResumeGattOperations_whileBackgroundReconnecting_preservesNonBlockingState() {
        val mockBtManager = mockk<BluetoothManager>(relaxed = true)
        val mockAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val mockBtDevice = mockk<BluetoothDevice>(relaxed = true)
        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBtManager
        every { mockBtManager.adapter } returns mockAdapter
        every { mockAdapter.getRemoteDevice("AA:BB:CC:DD:EE:03") } returns mockBtDevice

        com.atrainingtracker.trainingtracker.TrainingApplication.setTrackingModeForTesting(
            com.atrainingtracker.trainingtracker.TrackingMode.TRACKING
        )
        try {
            val device = TestBTLEDevice(mockContext, mockSensorManager, 3L, "AA:BB:CC:DD:EE:03")
            device.startSearching()
            device.searchTimeoutRunnable.run()
            assertFalse(device.isSearching)

            device.pauseGattOperations()
            assertTrue(device.isGattPaused)
            MyBTLEDevice.clearScanTimestampsForTesting()
            delayedRunnables.clear()
            device.resumeGattOperations()
            assertFalse(device.isGattPaused)
            assertFalse("Resuming background reconnect must not mark mSearching=true", device.isSearching)
            assertEquals("Passive resume must consume 0 scan quota (TST-SET-125.2)", 0, MyBTLEDevice.getScanTimestampsCountForTesting())
            assertFalse("Passive resume must not schedule mSearchTimeoutRunnable (TST-SET-125.2)", delayedRunnables.contains(device.searchTimeoutRunnable))
        } finally {
            com.atrainingtracker.trainingtracker.TrainingApplication.setTrackingModeForTesting(
                com.atrainingtracker.trainingtracker.TrackingMode.READY
            )
        }
    }

    // REQ-CON-054.1 / TST-CON-054.1
    @Test
    fun testOnConnectionStateChange_afterShutDown_immediatelyClosesLateGattCallback_TST_CON_054_1() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 10L, "AA:BB:CC:DD:EE:10")
        device.shutDown()
        postedRunnables.clear()

        val lateGatt = mockk<BluetoothGatt>(relaxed = true)
        device.getGattCallback().onConnectionStateChange(
            lateGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )

        verify(exactly = 1) { lateGatt.disconnect() }
        verify(exactly = 1) { lateGatt.close() }
        verify(exactly = 0) { lateGatt.discoverServices() }
        assertTrue("No post-shutdown discovery runnable should be queued", postedRunnables.isEmpty())
    }

    // REQ-CON-054.1 / TST-CON-054.1
    @Test
    fun testDisconnectFromGatt_whenOnHandlerLooper_closesSynchronouslyWithoutPosting_TST_CON_054_1() {
        val sameLooper = mockk<Looper>(relaxed = true)
        every { anyConstructed<Handler>().looper } returns sameLooper
        mockkStatic(Looper::class)
        every { Looper.myLooper() } returns sameLooper

        val device = TestBTLEDevice(mockContext, mockSensorManager, 11L, "AA:BB:CC:DD:EE:11")
        device.setGatt(mockGatt)
        postedRunnables.clear()

        device.disconnectFromGatt()

        verify(exactly = 1) { mockGatt.disconnect() }
        verify(exactly = 1) { mockGatt.close() }
        assertTrue("Same-looper disconnectFromGatt must run synchronously without posting", postedRunnables.isEmpty())
    }

    // REQ-CON-054.1 / TST-CON-054.1
    @Test
    fun testDisconnectFromGatt_whenPostReturnsFalse_stillDisconnectsClosesAndRunsCallback_TST_CON_054_1() {
        every { anyConstructed<Handler>().post(any()) } returns false
        val device = TestBTLEDevice(mockContext, mockSensorManager, 12L, "AA:BB:CC:DD:EE:12")
        device.setGatt(mockGatt)

        var callbackRan = false
        device.shutDown { callbackRan = true }

        verify(exactly = 1) { mockGatt.disconnect() }
        verify(exactly = 1) { mockGatt.close() }
        assertTrue("onTeardownComplete callback must still execute when Handler.post returns false", callbackRan)
    }

    // REQ-TRK-093.4 / TST-TRK-093.4
    @Suppress("DEPRECATION")
    @Test
    fun testPauseGattOperations_synchronouslyReleasesGattUnregistersSensorsAndReconfiguresOnReconnect_TST_TRK_093_4() {
        val mockBtManager = mockk<BluetoothManager>(relaxed = true)
        val mockAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val mockBtDevice = mockk<BluetoothDevice>(relaxed = true)
        val newGatt = mockk<BluetoothGatt>(relaxed = true)
        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBtManager
        every { mockBtManager.adapter } returns mockAdapter
        every { mockAdapter.getRemoteDevice("AA:BB:CC:DD:EE:93") } returns mockBtDevice
        every { mockBtDevice.connectGatt(any<Context>(), any<Boolean>(), any<BluetoothGattCallback>(), any<Int>()) } returns newGatt
        every { mockBtDevice.connectGatt(any<Context>(), any<Boolean>(), any<BluetoothGattCallback>()) } returns newGatt

        var unregisterCount = 0
        var configureServicesGatt: BluetoothGatt? = null
        val device = object : MyBTLEDevice(mockContext, mockSensorManager, DeviceType.HRM, 93L, "AA:BB:CC:DD:EE:93") {
            override fun addSensors() {}
            override fun measurementCharacteristicUpdate(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {}
            override fun unregisterSensors() {
                unregisterCount++
                super.unregisterSensors()
            }
            override fun configureServices(gatt: BluetoothGatt) {
                configureServicesGatt = gatt
            }
            fun getGattCallbackForTest(): BluetoothGattCallback {
                val field = MyBTLEDevice::class.java.getDeclaredField("mGattCallback")
                field.isAccessible = true
                return field.get(this) as BluetoothGattCallback
            }
            fun registerForTest() { registerSensors() }
            fun setGattForTest(g: BluetoothGatt?) { mBluetoothGatt = g }
            fun getGattForTest(): BluetoothGatt? = mBluetoothGatt
            fun getStateForTest(): State = mState
            fun setStateForTest(s: State) { mState = s }
            fun isSensorsRegisteredForTest(): Boolean = sensorsRegistered()
        }

        // Simulate active connected session with registered sensors
        device.setGattForTest(mockGatt)
        device.setStateForTest(MyBTLEDevice.State.CONNECTED_WITH_SERVICE)
        device.registerForTest()
        assertTrue(device.isSensorsRegisteredForTest())
        postedRunnables.clear()

        // 1. pauseGattOperations() MUST synchronously disconnect & close GATT, null mBluetoothGatt,
        //    set DISCONNECTED, and call unregisterSensors() without posting to mHandler
        device.pauseGattOperations()
        assertTrue(device.isGattPaused)
        assertNull("mBluetoothGatt must be cleared immediately on pause", device.getGattForTest())
        assertEquals(MyBTLEDevice.State.DISCONNECTED, device.getStateForTest())
        assertFalse("sensorsRegistered() must become false on pause so reconnect re-runs configureServices", device.isSensorsRegisteredForTest())
        assertEquals(1, unregisterCount)
        verify(exactly = 1) { mockGatt.disconnect() }
        verify(exactly = 1) { mockGatt.close() }
        assertTrue("GATT close during pauseGattOperations must be synchronous, not posted", postedRunnables.isEmpty())

        // 2. Late/stale onConnectionStateChange while paused closes stale GATT outside lock and returns cleanly
        val staleGatt = mockk<BluetoothGatt>(relaxed = true)
        device.getGattCallbackForTest().onConnectionStateChange(
            staleGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
        verify(exactly = 1) { staleGatt.disconnect() }
        verify(exactly = 1) { staleGatt.close() }
        verify(exactly = 0) { staleGatt.discoverServices() }
        assertEquals(MyBTLEDevice.State.DISCONNECTED, device.getStateForTest())

        // 3. Resume -> reconnect -> onServicesDiscovered -> readNextCharacteristic -> configureServices -> CONNECTED_WITH_SERVICE
        device.resumeGattOperations()
        assertFalse(device.isGattPaused)
        device.setGattForTest(newGatt)
        device.setStateForTest(MyBTLEDevice.State.CONNECTED_TO_GATT)
        postedRunnables.clear()
        every { newGatt.services } returns emptyList()
        every { newGatt.getService(any()) } returns null

        device.getGattCallbackForTest().onServicesDiscovered(newGatt, BluetoothGatt.GATT_SUCCESS)
        // Drain posted onServicesDiscovered runnable AND nested configureServices runnable
        while (postedRunnables.isNotEmpty()) {
            postedRunnables.removeAt(0).run()
        }

        assertEquals(MyBTLEDevice.State.CONNECTED_WITH_SERVICE, device.getStateForTest())
        assertTrue("Sensors must be re-registered on reconnect", device.isSensorsRegisteredForTest())
        assertEquals("configureServices(newGatt) must be invoked on reconnect", newGatt, configureServicesGatt)
    }

    // REQ-TRK-093.4 / TST-TRK-093.4 (Stage 6 Bounce 1/5 Concurrency Hardening)
    @Suppress("DEPRECATION")
    @Test
    fun testPauseGattOperations_blocksQueuedConnectScanTimeoutServicesDiscoveredAndCallbackToctou_TST_TRK_093_4() {
        val mockBtManager = mockk<BluetoothManager>(relaxed = true)
        val mockAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val mockScanner = mockk<android.bluetooth.le.BluetoothLeScanner>(relaxed = true)
        val mockBtDevice = mockk<BluetoothDevice>(relaxed = true)
        val staleGatt = mockk<BluetoothGatt>(relaxed = true)
        val newGatt = mockk<BluetoothGatt>(relaxed = true)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        mockkConstructor(android.bluetooth.le.ScanFilter.Builder::class)
        every { anyConstructed<android.bluetooth.le.ScanFilter.Builder>().setDeviceAddress(any()) } answers { self as android.bluetooth.le.ScanFilter.Builder }
        every { anyConstructed<android.bluetooth.le.ScanFilter.Builder>().build() } returns mockk(relaxed = true)
        mockkConstructor(android.bluetooth.le.ScanSettings.Builder::class)
        every { anyConstructed<android.bluetooth.le.ScanSettings.Builder>().setScanMode(any()) } answers { self as android.bluetooth.le.ScanSettings.Builder }
        every { anyConstructed<android.bluetooth.le.ScanSettings.Builder>().build() } returns mockk(relaxed = true)
        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBtManager
        every { mockBtManager.adapter } returns mockAdapter
        every { mockAdapter.isEnabled } returns true
        every { mockAdapter.bluetoothLeScanner } returns mockScanner
        every { mockAdapter.getRemoteDevice("AA:BB:CC:DD:EE:94") } returns mockBtDevice
        every { mockBtDevice.address } returns "AA:BB:CC:DD:EE:94"
        every { mockBtDevice.connectGatt(any<Context>(), any<Boolean>(), any<BluetoothGattCallback>(), any<Int>()) } returns newGatt
        every { mockBtDevice.connectGatt(any<Context>(), any<Boolean>(), any<BluetoothGattCallback>()) } returns newGatt

        var pauseInsideStopScan = false
        var pauseInsideUnregister = false
        var configureServicesGatt: BluetoothGatt? = null

        val device = object : MyBTLEDevice(mockContext, mockSensorManager, DeviceType.HRM, 94L, "AA:BB:CC:DD:EE:94") {
            override fun addSensors() {}
            override fun measurementCharacteristicUpdate(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {}
            override fun stopScanAssistance() {
                super.stopScanAssistance()
                if (pauseInsideStopScan) {
                    pauseInsideStopScan = false
                    pauseGattOperations()
                }
            }
            override fun unregisterSensors() {
                super.unregisterSensors()
                if (pauseInsideUnregister) {
                    pauseInsideUnregister = false
                    pauseGattOperations()
                }
            }
            override fun configureServices(gatt: BluetoothGatt) {
                configureServicesGatt = gatt
            }
            fun getGattCallbackForTest(): BluetoothGattCallback {
                val field = MyBTLEDevice::class.java.getDeclaredField("mGattCallback")
                field.isAccessible = true
                return field.get(this) as BluetoothGattCallback
            }
            fun callTriggerGattConnect(dev: BluetoothDevice, autoConnect: Boolean) = triggerGattConnect(dev, autoConnect)
            fun callReadNextCharacteristic(gatt: BluetoothGatt?) = readNextCharacteristic(gatt)
            fun setGattForTest(g: BluetoothGatt?) { mBluetoothGatt = g }
            fun getGattForTest(): BluetoothGatt? = mBluetoothGatt
            fun getStateForTest(): State = mState
            fun setStateForTest(s: State) { mState = s }
            fun setBackgroundReconnectingForTest(flag: Boolean) {
                val field = MyBTLEDevice::class.java.getDeclaredField("mIsBackgroundReconnecting")
                field.isAccessible = true
                field.setBoolean(this, flag)
            }
            fun isBackgroundReconnectingForTest(): Boolean = isBackgroundReconnectingForTesting
            fun isSensorsRegisteredForTest(): Boolean = sensorsRegistered()
        }

        // --- Race 1: Queued triggerGattConnect, mSearchTimeoutRunnable, onScanResult, onScanFailed while paused ---
        device.startSearching()
        val scanCallback = device.scanCallbackForTesting
        device.setGattForTest(null)
        device.setStateForTest(MyBTLEDevice.State.DISCONNECTED)
        device.setBackgroundReconnectingForTest(true)
        postedRunnables.clear()
        delayedRunnables.clear()

        // Queue a connect runnable BEFORE pause, then pause before Looper drains it
        device.callTriggerGattConnect(mockBtDevice, true)
        assertEquals(1, postedRunnables.size)
        device.pauseGattOperations()
        while (postedRunnables.isNotEmpty()) {
            postedRunnables.removeAt(0).run()
        }
        // Also fire mSearchTimeoutRunnable and scan callbacks while paused
        device.searchTimeoutRunnable.run()
        val scanResult = mockk<android.bluetooth.le.ScanResult>(relaxed = true)
        every { scanResult.device } returns mockBtDevice
        scanCallback?.onScanResult(1, scanResult)
        scanCallback?.onScanFailed(1)
        while (postedRunnables.isNotEmpty()) {
            postedRunnables.removeAt(0).run()
        }

        assertNull("No competing BluetoothGatt may be opened while paused", device.getGattForTest())
        verify(exactly = 0) { mockBtDevice.connectGatt(any<Context>(), any<Boolean>(), any<BluetoothGattCallback>(), any<Int>()) }
        verify(exactly = 0) { mockBtDevice.connectGatt(any<Context>(), any<Boolean>(), any<BluetoothGattCallback>()) }
        assertTrue("No reconnect runnable may be scheduled while paused", delayedRunnables.isEmpty())

        // --- Race 2: Queued onServicesDiscovered -> readNextCharacteristic after pauseGattOperations() ---
        device.resumeGattOperations()
        postedRunnables.clear()
        device.setGattForTest(staleGatt)
        device.setStateForTest(MyBTLEDevice.State.CONNECTED_TO_GATT)
        every { staleGatt.getService(any()) } returns null

        // Enqueue onServicesDiscovered BEFORE pause, then pause before Looper runs it
        device.getGattCallbackForTest().onServicesDiscovered(staleGatt, BluetoothGatt.GATT_SUCCESS)
        assertEquals(1, postedRunnables.size)
        device.pauseGattOperations()
        while (postedRunnables.isNotEmpty()) {
            postedRunnables.removeAt(0).run()
        }
        // Direct readNextCharacteristic(staleGatt) and 0-arg while paused must also be ignored
        device.callReadNextCharacteristic(staleGatt)
        device.callReadNextCharacteristic(null)
        assertEquals("Paused queued onServicesDiscovered must NOT wedge state into CONNECTED_WITH_SERVICE", MyBTLEDevice.State.DISCONNECTED, device.getStateForTest())
        assertFalse("Paused queued onServicesDiscovered must NOT register sensors", device.isSensorsRegisteredForTest())
        assertNull("Paused queued onServicesDiscovered must NOT invoke configureServices", configureServicesGatt)

        // Resume + fresh GATT discovery must still succeed cleanly after Race 2
        device.resumeGattOperations()
        device.setGattForTest(newGatt)
        device.setStateForTest(MyBTLEDevice.State.CONNECTED_TO_GATT)
        every { newGatt.getService(any()) } returns null
        postedRunnables.clear()
        device.getGattCallbackForTest().onServicesDiscovered(newGatt, BluetoothGatt.GATT_SUCCESS)
        while (postedRunnables.isNotEmpty()) {
            postedRunnables.removeAt(0).run()
        }
        assertEquals(MyBTLEDevice.State.CONNECTED_WITH_SERVICE, device.getStateForTest())
        assertTrue(device.isSensorsRegisteredForTest())
        assertEquals(newGatt, configureServicesGatt)

        // --- Race 3A: STATE_CONNECTED TOCTOU (pause runs inside stopScanAssistance before re-acquiring mGattLock) ---
        val toctouGatt = mockk<BluetoothGatt>(relaxed = true)
        device.setGattForTest(toctouGatt)
        device.setStateForTest(MyBTLEDevice.State.SEARCHING)
        postedRunnables.clear()
        pauseInsideStopScan = true

        device.getGattCallbackForTest().onConnectionStateChange(
            toctouGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
        assertEquals("STATE_CONNECTED TOCTOU after pause must remain DISCONNECTED", MyBTLEDevice.State.DISCONNECTED, device.getStateForTest())
        assertTrue("No discoverServices runnable may be posted on paused STATE_CONNECTED TOCTOU", postedRunnables.isEmpty())
        verify(atLeast = 1) { toctouGatt.disconnect() }
        verify(atLeast = 1) { toctouGatt.close() }

        // --- Race 3B: STATE_DISCONNECTED TOCTOU (pause runs inside unregisterSensors while mIsBackgroundReconnecting=true) ---
        device.resumeGattOperations()
        val discGatt = mockk<BluetoothGatt>(relaxed = true)
        device.setGattForTest(discGatt)
        device.setStateForTest(MyBTLEDevice.State.CONNECTED_WITH_SERVICE)
        device.setBackgroundReconnectingForTest(true)
        delayedRunnables.clear()
        pauseInsideUnregister = true

        device.getGattCallbackForTest().onConnectionStateChange(
            discGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_DISCONNECTED
        )
        assertTrue("Paused STATE_DISCONNECTED TOCTOU must preserve mIsBackgroundReconnecting", device.isBackgroundReconnectingForTest())
        assertTrue("Paused STATE_DISCONNECTED TOCTOU must NOT schedule mReconnectRunnable", delayedRunnables.isEmpty())
    }
}
