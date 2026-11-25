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

@file:Suppress("DEPRECATION")

package com.atrainingtracker.banalservice.devices.bluetooth_le

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineCapabilityStore
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineGattCoordinator
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Unit test suite for [BleDisReconnectBackfillHelper] (`REQ-CON-062`, `TST-CON-062`, `ATT-8723`).
 */
class BleDisReconnectBackfillHelperTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockDbManager: DevicesDatabaseManager
    private lateinit var postedRunnables: MutableList<Runnable>
    private lateinit var mockHandler: Handler

    private var snapshotUuid: UUID = BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME
    private var snapshotBytes: ByteArray? = null

    private open class TestBackfillBTLEDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceType: DeviceType,
        deviceId: Long,
        address: String,
        private val customConfigure: ((BluetoothGatt) -> Unit)? = null
    ) : MyBTLEDevice(context, sensorManager, deviceType, deviceId, address) {
        var configuredCount = 0

        override fun addSensors() {}
        override fun measurementCharacteristicUpdate(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {}

        override fun configureServices(gatt: BluetoothGatt) {
            configuredCount++
            if (customConfigure != null) {
                customConfigure.invoke(gatt)
            } else {
                super.configureServices(gatt)
            }
        }

        fun attachGattAndHandler(gatt: BluetoothGatt?, handler: Handler?) {
            mBluetoothGatt = gatt
            mHandler = handler
        }

        fun setShutDownForTest(flag: Boolean) {
            mIsShutDown = flag
        }

        fun clearCachedManufacturerForTest() {
            val field = com.atrainingtracker.banalservice.devices.MyDevice::class.java
                .getDeclaredField("mCachedManufacturerName")
            field.isAccessible = true
            field.set(this, null)
        }

        fun setCachedManufacturerForTest(value: String?) {
            val field = com.atrainingtracker.banalservice.devices.MyDevice::class.java
                .getDeclaredField("mCachedManufacturerName")
            field.isAccessible = true
            field.set(this, value)
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(ActivityCompat::class)
        every { ActivityCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(DevicesDatabaseManager::class)
        mockDbManager = mockk(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDbManager
        every { mockDbManager.getManufacturerName(any()) } returns null

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setPackage(any()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<String>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Int>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Long>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<java.io.Serializable>()) } answers { self as android.content.Intent }

        mockkConstructor(BluetoothGattCharacteristic::class)
        every { anyConstructed<BluetoothGattCharacteristic>().setValue(any<ByteArray>()) } answers {
            snapshotBytes = firstArg()
            true
        }
        every { anyConstructed<BluetoothGattCharacteristic>().getValue() } answers { snapshotBytes }
        every { anyConstructed<BluetoothGattCharacteristic>().value } answers { snapshotBytes }
        every { anyConstructed<BluetoothGattCharacteristic>().getUuid() } answers { snapshotUuid }
        every { anyConstructed<BluetoothGattCharacteristic>().uuid } answers { snapshotUuid }
        every { anyConstructed<BluetoothGattCharacteristic>().getStringValue(0) } answers {
            snapshotBytes?.let { String(it, StandardCharsets.UTF_8) }
        }
        every { anyConstructed<BluetoothGattCharacteristic>().properties } returns BluetoothGattCharacteristic.PROPERTY_READ
        every { anyConstructed<BluetoothGattCharacteristic>().permissions } returns 0

        mockkStatic(Looper::class)
        val mockLooper = mockk<Looper>(relaxed = true)
        every { Looper.getMainLooper() } returns mockLooper

        mockContext = mockk(relaxed = true)
        every { mockContext.mainLooper } returns mockLooper
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSharedPreferences(any(), any()) } returns null
        mockSensorManager = mockk(relaxed = true)

        postedRunnables = mutableListOf()
        mockHandler = mockk(relaxed = true)
        every { mockHandler.post(any()) } answers {
            postedRunnables.add(firstArg())
            true
        }

        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
    }

    @After
    fun tearDown() {
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
        unmockkAll()
    }

    private fun drainPostedRunnables() {
        while (postedRunnables.isNotEmpty()) {
            postedRunnables.removeAt(0).run()
        }
    }

    @Test
    fun testNeedsManufacturerBackfill_matrix_TST_CON_062_1() {
        assertTrue(BleDisReconnectBackfillHelper.needsManufacturerBackfill(null))
        assertTrue(BleDisReconnectBackfillHelper.needsManufacturerBackfill(""))
        assertTrue(BleDisReconnectBackfillHelper.needsManufacturerBackfill("   "))
        assertTrue(BleDisReconnectBackfillHelper.needsManufacturerBackfill("\u0000"))
        assertTrue(BleDisReconnectBackfillHelper.needsManufacturerBackfill("\u0000\u0000"))
        assertTrue(BleDisReconnectBackfillHelper.needsManufacturerBackfill("Polar Electro Oy\u0000"))
        assertFalse(BleDisReconnectBackfillHelper.needsManufacturerBackfill("Polar Electro Oy"))
        assertFalse(BleDisReconnectBackfillHelper.needsManufacturerBackfill("Garmin"))
        assertFalse(BleDisReconnectBackfillHelper.needsManufacturerBackfill("Wahoo Fitness"))
    }

    @Test
    fun testEnqueueMissingManufacturerRead_enqueuesAfterConfigureServicesAcrossAllSubclassesAndSkipsWhenGuarded_TST_CON_062_1() {
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        val powerService = mockk<BluetoothGattService>(relaxed = true)
        val powerChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val powerCccd = mockk<BluetoothGattDescriptor>(relaxed = true)
        val disService = mockk<BluetoothGattService>(relaxed = true)
        val mfgChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val radarModelChar = mockk<BluetoothGattCharacteristic>(relaxed = true)

        every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_BATTERY) } returns null
        every { mockGatt.getService(BluetoothConstants.getServiceUUID(DeviceType.BIKE_POWER)) } returns powerService
        every { powerService.getCharacteristic(BluetoothConstants.getCharacteristicUUID(DeviceType.BIKE_POWER)) } returns powerChar
        every { powerChar.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG) } returns powerCccd
        every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION) } returns disService
        every { disService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME) } returns mfgChar
        every { mfgChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME
        every { radarModelChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER
        every { mockGatt.writeDescriptor(any()) } returns true
        every { mockGatt.readCharacteristic(any()) } returns true

        // 1. Generic BIKE_POWER subclass with missing manufacturerName: 0x2A29 enqueues AFTER power CCCD
        val powerDevice = TestBackfillBTLEDevice(mockContext, mockSensorManager, DeviceType.BIKE_POWER, 42L, "AA:BB:CC:DD:EE:01")
        powerDevice.attachGattAndHandler(mockGatt, mockHandler)
        powerDevice.gattCallbackForTesting.onServicesDiscovered(mockGatt, BluetoothGatt.GATT_SUCCESS)
        drainPostedRunnables()

        assertEquals(1, powerDevice.configuredCount)
        assertTrue("Power CCCD op should be in-flight first", powerDevice.isGattOperationPendingForTesting)
        assertEquals("0x2A29 read must be queued behind power CCCD", 1, powerDevice.gattQueueForTesting.size)
        val queuedOp = powerDevice.gattQueueForTesting.peek() as MyBTLEDevice.ReadCharacteristicOp
        assertEquals(BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME, queuedOp.characteristic.uuid)

        // Calling enqueue again while 0x2A29 is already queued must deduplicate
        assertFalse(BleDisReconnectBackfillHelper.enqueueMissingManufacturerRead(powerDevice, mockGatt))
        assertEquals(1, powerDevice.gattQueueForTesting.size)

        // 2. BTLERadarDevice-style subclass overriding configureServices WITHOUT calling super:
        //    still gets 0x2A29 queued behind its own 0x2A24 model read!
        var radarDevice: TestBackfillBTLEDevice? = null
        radarDevice = TestBackfillBTLEDevice(
            mockContext,
            mockSensorManager,
            DeviceType.BIKE_RADAR,
            43L,
            "AA:BB:CC:DD:EE:02"
        ) { _ ->
            radarDevice!!.enqueueReadCharacteristic(radarModelChar)
        }
        radarDevice.attachGattAndHandler(mockGatt, mockHandler)
        radarDevice.gattCallbackForTesting.onServicesDiscovered(mockGatt, BluetoothGatt.GATT_SUCCESS)
        drainPostedRunnables()

        assertEquals(1, radarDevice.configuredCount)
        assertTrue("Radar 0x2A24 read should be in-flight first", radarDevice.isGattOperationPendingForTesting)
        assertEquals(1, radarDevice.gattQueueForTesting.size)
        val radarQueuedOp = radarDevice.gattQueueForTesting.peek() as MyBTLEDevice.ReadCharacteristicOp
        assertEquals(BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME, radarQueuedOp.characteristic.uuid)

        // 3. Cleanly populated manufacturerName -> 0 reads enqueued
        every { mockDbManager.getManufacturerName(44L) } returns "Garmin"
        val cleanDevice = TestBackfillBTLEDevice(mockContext, mockSensorManager, DeviceType.BIKE_POWER, 44L, "AA:BB:CC:DD:EE:03")
        cleanDevice.attachGattAndHandler(mockGatt, mockHandler)
        assertFalse(BleDisReconnectBackfillHelper.enqueueMissingManufacturerRead(cleanDevice, mockGatt))
        assertFalse(cleanDevice.isGattOperationPendingForTesting)

        // 4. Guards: deviceId <= 0, missing 0x180A, missing 0x2A29, paused, shut down
        val unpersistedDevice = TestBackfillBTLEDevice(mockContext, mockSensorManager, DeviceType.BIKE_POWER, 0L, "AA:BB:CC:DD:EE:04")
        unpersistedDevice.attachGattAndHandler(mockGatt, mockHandler)
        assertFalse(BleDisReconnectBackfillHelper.enqueueMissingManufacturerRead(unpersistedDevice, mockGatt))

        val noDisGatt = mockk<BluetoothGatt>(relaxed = true)
        every { noDisGatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION) } returns null
        val noDisDevice = TestBackfillBTLEDevice(mockContext, mockSensorManager, DeviceType.BIKE_POWER, 45L, "AA:BB:CC:DD:EE:05")
        noDisDevice.attachGattAndHandler(noDisGatt, mockHandler)
        assertFalse(BleDisReconnectBackfillHelper.enqueueMissingManufacturerRead(noDisDevice, noDisGatt))

        val noMfgDisService = mockk<BluetoothGattService>(relaxed = true)
        every { noMfgDisService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME) } returns null
        val noMfgGatt = mockk<BluetoothGatt>(relaxed = true)
        every { noMfgGatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION) } returns noMfgDisService
        assertFalse(BleDisReconnectBackfillHelper.enqueueMissingManufacturerRead(noDisDevice, noMfgGatt))

        noDisDevice.pauseGattOperations()
        assertFalse(BleDisReconnectBackfillHelper.enqueueMissingManufacturerRead(noDisDevice, mockGatt))

        val shutDownDevice = TestBackfillBTLEDevice(mockContext, mockSensorManager, DeviceType.BIKE_POWER, 46L, "AA:BB:CC:DD:EE:06")
        shutDownDevice.attachGattAndHandler(mockGatt, mockHandler)
        shutDownDevice.setShutDownForTest(true)
        assertFalse(BleDisReconnectBackfillHelper.enqueueMissingManufacturerRead(shutDownDevice, mockGatt))
    }

    @Test
    fun testOnDisCharacteristicRead_sanitizesPostsToHandlerAndGuardsFailureBlankStale_TST_CON_062_2() {
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        val staleGatt = mockk<BluetoothGatt>(relaxed = true)
        val disService = mockk<BluetoothGattService>(relaxed = true)
        val mfgChar = mockk<BluetoothGattCharacteristic>(relaxed = true)

        every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION) } returns disService
        every { disService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME) } returns mfgChar
        every { mfgChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME
        every { mockGatt.readCharacteristic(any()) } returns true

        val device = TestBackfillBTLEDevice(mockContext, mockSensorManager, DeviceType.BIKE_SPEED_AND_CADENCE, 51L, "AA:BB:CC:DD:EE:11")
        device.attachGattAndHandler(mockGatt, mockHandler)

        // Enqueue 0x2A29 read and start executing it
        assertTrue(BleDisReconnectBackfillHelper.enqueueMissingManufacturerRead(device, mockGatt))
        drainPostedRunnables()
        assertTrue(device.isGattOperationPendingForTesting)

        // Simulate GATT_SUCCESS callback with NUL-terminated payload on Binder thread
        snapshotUuid = BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME
        every { mfgChar.value } returns "Wahoo Fitness\u0000".toByteArray(StandardCharsets.UTF_8)
        device.gattCallbackForTesting.onCharacteristicRead(mockGatt, mfgChar, BluetoothGatt.GATT_SUCCESS)

        // Binder thread must immediately advance queue WITHOUT blocking on synchronous SQLite write
        assertFalse("mGattOperationPending must clear immediately on Binder", device.isGattOperationPendingForTesting)
        verify(exactly = 0) { mockDbManager.setManufacturerName(any(), any()) }
        assertEquals("Persistence runnable must be posted to mHandler", 1, postedRunnables.size)

        // Drain mHandler -> sanitized manufacturer persisted & cached
        drainPostedRunnables()
        verify(exactly = 1) { mockDbManager.setManufacturerName(51L, "Wahoo Fitness") }
        assertEquals("Wahoo Fitness", device.manufacturerName)

        // Subsequent reconnect must enqueue 0 reads now that manufacturer is cleanly populated
        assertFalse(BleDisReconnectBackfillHelper.enqueueMissingManufacturerRead(device, mockGatt))

        // Failed read (status != GATT_SUCCESS) must NEVER persist stale bytes and must advance queue
        device.clearCachedManufacturerForTest()
        every { mockDbManager.getManufacturerName(51L) } returns null
        assertTrue(BleDisReconnectBackfillHelper.enqueueMissingManufacturerRead(device, mockGatt))
        drainPostedRunnables()
        device.gattCallbackForTesting.onCharacteristicRead(mockGatt, mfgChar, BluetoothGatt.GATT_READ_NOT_PERMITTED)
        drainPostedRunnables()
        assertFalse(device.isGattOperationPendingForTesting)
        verify(exactly = 1) { mockDbManager.setManufacturerName(51L, any()) }

        // NUL-only payload "\u0000\u0000" must NOT overwrite DB
        every { mfgChar.value } returns "\u0000\u0000".toByteArray(StandardCharsets.UTF_8)
        device.gattCallbackForTesting.onCharacteristicRead(mockGatt, mfgChar, BluetoothGatt.GATT_SUCCESS)
        drainPostedRunnables()
        verify(exactly = 1) { mockDbManager.setManufacturerName(51L, any()) }

        // Stale GATT handle must NOT overwrite DB
        every { mfgChar.value } returns "Stale Brand\u0000".toByteArray(StandardCharsets.UTF_8)
        device.gattCallbackForTesting.onCharacteristicRead(staleGatt, mfgChar, BluetoothGatt.GATT_SUCCESS)
        drainPostedRunnables()
        verify(exactly = 0) { mockDbManager.setManufacturerName(51L, "Stale Brand") }

        // Paused or shut-down device must drop posted persistence
        device.gattCallbackForTesting.onCharacteristicRead(mockGatt, mfgChar, BluetoothGatt.GATT_SUCCESS)
        device.pauseGattOperations()
        drainPostedRunnables()
        verify(exactly = 0) { mockDbManager.setManufacturerName(51L, "Stale Brand") }

        // Null mHandler guard (Gate 3 Tip #1)
        val nullHandlerDevice = TestBackfillBTLEDevice(mockContext, mockSensorManager, DeviceType.BIKE_POWER, 52L, "AA:BB:CC:DD:EE:12")
        nullHandlerDevice.attachGattAndHandler(mockGatt, null)
        snapshotBytes = "Stages\u0000".toByteArray(StandardCharsets.UTF_8)
        val snapshotChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
        every { snapshotChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME
        every { snapshotChar.getStringValue(0) } returns "Stages\u0000"
        assertFalse(BleDisReconnectBackfillHelper.onDisCharacteristicRead(nullHandlerDevice, mockGatt, snapshotChar))
    }

    @Test
    fun testEnqueueMissingPolarDisReads_conditionalBackfillAndColdStartAliasHydration_TST_CON_062_3() {
        val mac = "AA:BB:CC:DD:EE:99"
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        val disService = mockk<BluetoothGattService>(relaxed = true)
        val modelChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val serialChar = mockk<BluetoothGattCharacteristic>(relaxed = true)

        every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION) } returns disService
        every { disService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER) } returns modelChar
        every { disService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER) } returns serialChar
        every { modelChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER
        every { serialChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER
        every { mockGatt.readCharacteristic(any()) } returns true

        // 1. Non-Polar (hasPmd=false, hasPftp=false) -> 0 reads enqueued
        val device = TestBackfillBTLEDevice(mockContext, mockSensorManager, DeviceType.HRM, 61L, mac)
        device.attachGattAndHandler(mockGatt, mockHandler)
        assertEquals(0, BleDisReconnectBackfillHelper.enqueueMissingPolarDisReads(device, mockContext, mockGatt, false, false))
        assertFalse(device.isGattOperationPendingForTesting)

        // 2. Polar with empty store -> both 0x2A24 & 0x2A25 enqueued (0x2A24 first, 0x2A25 queued)
        assertEquals(2, BleDisReconnectBackfillHelper.enqueueMissingPolarDisReads(device, mockContext, mockGatt, true, true))
        assertTrue(device.isGattOperationPendingForTesting)
        assertEquals(1, device.gattQueueForTesting.size)
        val secondOp = device.gattQueueForTesting.peek() as MyBTLEDevice.ReadCharacteristicOp
        assertEquals(BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER, secondOp.characteristic.uuid)

        // 3. Partial store: modelNumber populated, serial missing -> only 0x2A25 enqueued
        device.disconnectFromGatt()
        drainPostedRunnables()
        device.attachGattAndHandler(mockGatt, mockHandler)
        PolarOfflineCapabilityStore.recordDisMetadata(mockContext, mac, "Verity Sense", null, confirmedPolarService = true)
        assertEquals(1, BleDisReconnectBackfillHelper.enqueueMissingPolarDisReads(device, mockContext, mockGatt, true, false))
        assertTrue(device.isGattOperationPendingForTesting)
        assertEquals(0, device.gattQueueForTesting.size)

        // 4. Both modelNumber & polarDeviceId populated + simulated cold-start (PolarOfflineGattCoordinator reset):
        //    0 GATT reads enqueued AND in-memory alias table is re-hydrated!
        device.disconnectFromGatt()
        drainPostedRunnables()
        device.attachGattAndHandler(mockGatt, mockHandler)
        PolarOfflineCapabilityStore.recordDisMetadata(mockContext, mac, "Verity Sense", "BEEF1234", confirmedPolarService = true)
        PolarOfflineGattCoordinator.resetForTesting()
        assertFalse(PolarOfflineGattCoordinator.matchesDeviceIdentifier("BEEF1234", mac))

        assertEquals(0, BleDisReconnectBackfillHelper.enqueueMissingPolarDisReads(device, mockContext, mockGatt, true, true))
        assertFalse("Zero GATT ops should be enqueued when Polar DIS metadata is already populated", device.isGattOperationPendingForTesting)
        assertTrue(
            "Cold-start reconnect must hydrate PolarOfflineGattCoordinator aliases even when 0x2A25 read is skipped",
            PolarOfflineGattCoordinator.matchesDeviceIdentifier("BEEF1234", mac)
        )
    }
}
