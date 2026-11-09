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
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying BLE Random Static MAC address classification,
 * scan assistance lifecycle management, and configurable search timeouts (REQ-CON-023, TST-CON-023).
 */
class MyBTLEDeviceAddressAndSearchTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockBtManager: BluetoothManager
    private lateinit var mockAdapter: BluetoothAdapter
    private lateinit var mockScanner: BluetoothLeScanner
    private lateinit var mockDevice: BluetoothDevice

    private class ConcreteBTLEDevice(
        context: Context,
        sensorManager: MySensorManager,
        address: String
    ) : MyBTLEDevice(context, sensorManager, DeviceType.HRM, 1L, address) {
        override fun addSensors() {}
        override fun measurementCharacteristicUpdate(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {}

        fun isScanning(): Boolean = isScanningForTesting
        fun getScanCallback(): ScanCallback? = scanCallbackForTesting
        fun invokeDisconnectFromGatt() = disconnectFromGatt()
        fun getSearchTimeoutRunnableRef(): Runnable = searchTimeoutRunnable
        fun setGatt(gatt: BluetoothGatt?) { mBluetoothGatt = gatt }
        fun getGatt(): BluetoothGatt? = mBluetoothGatt
        fun getGattCallback(): BluetoothGattCallback = gattCallbackForTesting
        fun getState(): State = mState
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_SCAN)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_ADMIN)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(ActivityCompat::class)
        every {
            ActivityCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setPackage(any()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<String>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Int>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Long>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<java.io.Serializable>()) } answers { self as android.content.Intent }

        mockkStatic(Looper::class)
        val mockLooper = mockk<Looper>(relaxed = true)
        every { Looper.getMainLooper() } returns mockLooper

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers {
            firstArg<Runnable>().run()
            true
        }
        every { anyConstructed<Handler>().postDelayed(any(), any()) } answers {
            true
        }
        every { anyConstructed<Handler>().removeCallbacks(any<Runnable>()) } answers {}
        every { anyConstructed<Handler>().removeCallbacksAndMessages(any()) } answers {}

        mockkConstructor(android.bluetooth.le.ScanFilter.Builder::class)
        every { anyConstructed<android.bluetooth.le.ScanFilter.Builder>().setDeviceAddress(any()) } answers { self as android.bluetooth.le.ScanFilter.Builder }
        every { anyConstructed<android.bluetooth.le.ScanFilter.Builder>().build() } returns mockk(relaxed = true)

        mockkConstructor(android.bluetooth.le.ScanSettings.Builder::class)
        every { anyConstructed<android.bluetooth.le.ScanSettings.Builder>().setScanMode(any()) } answers { self as android.bluetooth.le.ScanSettings.Builder }
        every { anyConstructed<android.bluetooth.le.ScanSettings.Builder>().build() } returns mockk(relaxed = true)

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockBtManager = mockk(relaxed = true)
        mockAdapter = mockk(relaxed = true)
        mockScanner = mockk(relaxed = true)
        mockDevice = mockk(relaxed = true)

        every { mockContext.mainLooper } returns mockLooper
        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBtManager
        every { mockBtManager.adapter } returns mockAdapter
        every { mockAdapter.isEnabled } returns true
        every { mockAdapter.bluetoothLeScanner } returns mockScanner
        every { mockAdapter.getRemoteDevice(any<String>()) } returns mockDevice
        every { mockDevice.connectGatt(any(), any(), any(), any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testIsRandomStaticAddress_classifiesCorrectly() {
        // Bit 47 and 46 == 11b (first byte & 0xC0 == 0xC0)
        assertTrue(MyBTLEDevice.isRandomStaticAddress("C6:CC:E3:F5:F0:37")) // 0xC6 = 1100_0110
        assertTrue(MyBTLEDevice.isRandomStaticAddress("E8:4D:74:07:F4:DD")) // 0xE8 = 1110_1000
        assertTrue(MyBTLEDevice.isRandomStaticAddress("FE:00:11:22:33:44")) // 0xFE = 1111_1110
        assertTrue(MyBTLEDevice.isRandomStaticAddress("C0:00:00:00:00:00")) // 0xC0 = 1100_0000
        assertTrue(MyBTLEDevice.isRandomStaticAddress("c6:cc:e3:f5:f0:37")) // case-insensitive

        // Public addresses (bit 47..46 != 11b)
        assertFalse(MyBTLEDevice.isRandomStaticAddress("00:1A:7D:DA:71:13")) // 0x00
        assertFalse(MyBTLEDevice.isRandomStaticAddress("44:55:66:77:88:99")) // 0x44 = 0100_0100
        assertFalse(MyBTLEDevice.isRandomStaticAddress("80:00:00:00:00:00")) // 0x80 = 1000_0000
        assertFalse(MyBTLEDevice.isRandomStaticAddress("3F:FF:FF:FF:FF:FF")) // 0x3F = 0011_1111

        // Malformed / invalid / null
        assertFalse(MyBTLEDevice.isRandomStaticAddress(null))
        assertFalse(MyBTLEDevice.isRandomStaticAddress(""))
        assertFalse(MyBTLEDevice.isRandomStaticAddress("   "))
        assertFalse(MyBTLEDevice.isRandomStaticAddress("C6:CC:E3"))
        assertFalse(MyBTLEDevice.isRandomStaticAddress("not-a-valid-address"))
        assertFalse(MyBTLEDevice.isRandomStaticAddress("ZZ:00:11:22:33:44"))
    }

    @Test
    fun testScanAssistance_startsScanWithAddressFilter() {
        val device = ConcreteBTLEDevice(mockContext, mockSensorManager, "C6:CC:E3:F5:F0:37")

        device.startSearching()

        assertTrue(device.isScanning())
        verify { mockScanner.startScan(any<List<android.bluetooth.le.ScanFilter>>(), any<android.bluetooth.le.ScanSettings>(), any<ScanCallback>()) }
    }

    @Test
    fun testScanAssistance_stopsScanOnTimeout() {
        val device = ConcreteBTLEDevice(mockContext, mockSensorManager, "C6:CC:E3:F5:F0:37")
        device.startSearching()
        assertTrue(device.isScanning())

        device.getSearchTimeoutRunnableRef().run()

        assertFalse(device.isScanning())
        verify { mockScanner.stopScan(any<ScanCallback>()) }
    }

    @Test
    fun testScanAssistance_stopsScanOnDisconnect() {
        val device = ConcreteBTLEDevice(mockContext, mockSensorManager, "C6:CC:E3:F5:F0:37")
        device.startSearching()
        assertTrue(device.isScanning())

        device.invokeDisconnectFromGatt()

        assertFalse(device.isScanning())
        verify { mockScanner.stopScan(any<ScanCallback>()) }
    }

    @Test
    fun testScanAssistance_stopsScanOnShutDown() {
        val device = ConcreteBTLEDevice(mockContext, mockSensorManager, "C6:CC:E3:F5:F0:37")
        device.startSearching()
        assertTrue(device.isScanning())

        device.shutDown()

        assertFalse(device.isScanning())
        verify { mockScanner.stopScan(any<ScanCallback>()) }
    }

    @Test
    fun testScanAssistance_connectsGattUponScanResultDiscovery() {
        val address = "C6:CC:E3:F5:F0:37"
        val device = ConcreteBTLEDevice(mockContext, mockSensorManager, address)
        device.startSearching()
        assertTrue(device.isScanning())

        val callback = device.getScanCallback()
        assertNotNull(callback)

        val mockScanResult = mockk<ScanResult>(relaxed = true)
        val mockDiscoveredDevice = mockk<BluetoothDevice>(relaxed = true)
        every { mockScanResult.device } returns mockDiscoveredDevice
        every { mockDiscoveredDevice.address } returns address
        every { mockDiscoveredDevice.connectGatt(any(), any(), any(), any()) } returns mockk(relaxed = true)

        callback!!.onScanResult(0, mockScanResult)

        assertFalse(device.isScanning())
        verify { mockDiscoveredDevice.connectGatt(mockContext, false, any<BluetoothGattCallback>(), BluetoothDevice.TRANSPORT_LE) }
    }

    @Test
    fun testScanAssistance_connectsPublicAddressWithC0ToFCOui() {
        // Public IEEE MAC with 0xD0 OUI (e.g. Intel controller D0:C6:37:33:67:AA) - REQ-SET-099.1, TST-SET-099.1
        val address = "D0:C6:37:33:67:AA"
        val device = ConcreteBTLEDevice(mockContext, mockSensorManager, address)
        device.startSearching()
        assertTrue(device.isScanning())

        val callback = device.getScanCallback()
        assertNotNull(callback)

        val mockScanResult = mockk<ScanResult>(relaxed = true)
        val mockDiscoveredDevice = mockk<BluetoothDevice>(relaxed = true)
        every { mockScanResult.device } returns mockDiscoveredDevice
        every { mockDiscoveredDevice.address } returns address
        every { mockDiscoveredDevice.connectGatt(any(), any(), any(), any()) } returns mockk(relaxed = true)

        callback!!.onScanResult(0, mockScanResult)

        assertFalse(device.isScanning())
        verify { mockDiscoveredDevice.connectGatt(mockContext, false, any<BluetoothGattCallback>(), BluetoothDevice.TRANSPORT_LE) }
    }

    @Test
    fun testScanAssistance_connectsRandomStaticAddress() {
        // Random Static MAC - REQ-SET-099.1, TST-SET-099.2
        val address = "E8:4D:74:07:F4:DD"
        val device = ConcreteBTLEDevice(mockContext, mockSensorManager, address)
        device.startSearching()
        assertTrue(device.isScanning())

        val callback = device.getScanCallback()
        assertNotNull(callback)

        val mockScanResult = mockk<ScanResult>(relaxed = true)
        val mockDiscoveredDevice = mockk<BluetoothDevice>(relaxed = true)
        every { mockScanResult.device } returns mockDiscoveredDevice
        every { mockDiscoveredDevice.address } returns address
        every { mockDiscoveredDevice.connectGatt(any(), any(), any(), any()) } returns mockk(relaxed = true)

        callback!!.onScanResult(0, mockScanResult)

        assertFalse(device.isScanning())
        verify { mockDiscoveredDevice.connectGatt(mockContext, false, any<BluetoothGattCallback>(), BluetoothDevice.TRANSPORT_LE) }
    }

    @Test
    fun testScanAssistance_replacesExistingGattAndIgnoresStaleCallbacks() {
        // REQ-SET-099.2, REQ-SET-099.4, TST-SET-099.3
        val address = "D0:C6:37:33:67:AA"
        val device = ConcreteBTLEDevice(mockContext, mockSensorManager, address)
        device.startSearching()
        assertTrue(device.isScanning())

        // Simulate an existing speculative GATT handle
        val mockSpeculativeGatt = mockk<BluetoothGatt>(relaxed = true)
        device.setGatt(mockSpeculativeGatt)

        val callback = device.getScanCallback()
        assertNotNull(callback)

        val mockScanResult = mockk<ScanResult>(relaxed = true)
        val mockDiscoveredDevice = mockk<BluetoothDevice>(relaxed = true)
        val mockNewGatt = mockk<BluetoothGatt>(relaxed = true)
        every { mockScanResult.device } returns mockDiscoveredDevice
        every { mockDiscoveredDevice.address } returns address
        every { mockDiscoveredDevice.connectGatt(any(), any(), any(), any()) } returns mockNewGatt

        callback!!.onScanResult(0, mockScanResult)

        // Speculative GATT was closed and replaced
        verify { mockSpeculativeGatt.disconnect() }
        verify { mockSpeculativeGatt.close() }
        assertEquals(mockNewGatt, device.getGatt())

        // Stale callback from old speculative GATT must be ignored
        val gattCallback = device.getGattCallback()
        gattCallback.onConnectionStateChange(mockSpeculativeGatt, 0, android.bluetooth.BluetoothProfile.STATE_DISCONNECTED)

        // Device should NOT transition to DISCONNECTED or clobber mBluetoothGatt
        assertEquals(mockNewGatt, device.getGatt())
        assertNotEquals(MyBTLEDevice.State.DISCONNECTED, device.getState())
    }

    @Test
    fun testStartSearching_scanFailsToStart_fallsBackToDirectConnect() {
        // REQ-SET-099.1, TST-SET-099.4
        every { mockAdapter.bluetoothLeScanner } returns null

        val address = "D0:C6:37:33:67:AA"
        val device = ConcreteBTLEDevice(mockContext, mockSensorManager, address)
        device.startSearching()

        assertFalse(device.isScanning())
        verify { mockDevice.connectGatt(mockContext, false, any<BluetoothGattCallback>(), BluetoothDevice.TRANSPORT_LE) }
    }
}
