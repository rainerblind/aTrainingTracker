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

package com.atrainingtracker.banalservice.devices.bluetooth_le;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.MyRemoteDevice;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

//import de.rainerblind.MyAntPlusApp;

// TODO: where/when to register and unregister the sensors

public abstract class MyBTLEDevice extends MyRemoteDevice {
    private static final boolean DEBUG = BANALService.getDebug(false);
    private static final int SEARCH_PERIOD = 10 * 1000; // 10 seconds (same as for ANT+)
    private static final int READ_BATTERY_PERCENTAGE_PERIOD = 5 * 60 * 1000; // 5 minutes
    private static final long INITIAL_RECONNECT_DELAY_MS = 1000L;
    private static final long MAX_RECONNECT_DELAY_MS = 16000L;
    protected String mAddress; // the MAC Address
    // private static final int READ_BATTERY_PERCENTAGE_PERIOD = 10 * 1000; // 10 seconds (just for testing)
    private final Object mGattLock = new Object();
    protected volatile BluetoothGatt mBluetoothGatt;
    protected final Queue<BluetoothGattCharacteristic> mReadCharacteristicQueue = new ConcurrentLinkedQueue<BluetoothGattCharacteristic>();
    protected volatile State mState = State.DISCONNECTED;
    Handler mHandler;
    protected volatile BluetoothGattCharacteristic mBatteryCharacteristic;
    private volatile boolean mIsShutDown = false;
    private volatile long mCurrentReconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;
    private volatile boolean mIsScanning = false;
    private ScanCallback mScanCallback;

    public interface GattOperation {
        boolean execute(BluetoothGatt gatt);
    }

    public static class WriteDescriptorOp implements GattOperation {
        private final BluetoothGattDescriptor descriptor;
        private final byte[] value;

        public WriteDescriptorOp(BluetoothGattDescriptor descriptor, byte[] value) {
            this.descriptor = descriptor;
            this.value = value;
        }

        public BluetoothGattDescriptor getDescriptor() {
            return descriptor;
        }

        public byte[] getValue() {
            return value;
        }

        @Override
        public boolean execute(BluetoothGatt gatt) {
            if (descriptor == null || gatt == null) return false;
            descriptor.setValue(value);
            return gatt.writeDescriptor(descriptor);
        }
    }

    public static class ReadCharacteristicOp implements GattOperation {
        private final BluetoothGattCharacteristic characteristic;

        public ReadCharacteristicOp(BluetoothGattCharacteristic characteristic) {
            this.characteristic = characteristic;
        }

        public BluetoothGattCharacteristic getCharacteristic() {
            return characteristic;
        }

        @Override
        public boolean execute(BluetoothGatt gatt) {
            if (characteristic == null || gatt == null) return false;
            return gatt.readCharacteristic(characteristic);
        }
    }

    public static class WriteCharacteristicOp implements GattOperation {
        private final BluetoothGattCharacteristic characteristic;
        private final byte[] value;

        public WriteCharacteristicOp(BluetoothGattCharacteristic characteristic, byte[] value) {
            this.characteristic = characteristic;
            this.value = value;
        }

        public BluetoothGattCharacteristic getCharacteristic() {
            return characteristic;
        }

        public byte[] getValue() {
            return value;
        }

        @Override
        public boolean execute(BluetoothGatt gatt) {
            if (characteristic == null || gatt == null) return false;
            characteristic.setValue(value);
            return gatt.writeCharacteristic(characteristic);
        }
    }

    protected final Queue<GattOperation> mGattQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean mGattOperationPending = false;
    private static final long GATT_OP_TIMEOUT_MS = 2500L;

    private final Runnable mGattTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsShutDown) return;
            Log.w(TAG, "GATT operation timed out after " + GATT_OP_TIMEOUT_MS + " ms; forcing queue advance");
            onGattOperationCompleted();
        }
    };

    private static final int SNAPSHOT_POOL_SIZE = 8;
    private final Map<UUID, BluetoothGattCharacteristic[]> mCharacteristicPool = new ConcurrentHashMap<>();
    private final AtomicInteger mPoolIndex = new AtomicInteger(0);

    /**
     * Determines whether a given Bluetooth MAC address is a BLE Random Static Address
     * per Bluetooth Core Specification Vol 6, Part B, Section 1.3.2.1.
     *
     * <p>A Random Static Address is identified by the two most significant bits of the
     * first (most significant) octet being 11b (i.e. (firstOctet &amp; 0xC0) == 0xC0).
     *
     * @param address the 48-bit MAC address string (e.g. "C6:CC:E3:F5:F0:37")
     * @return true if the address matches BLE Random Static addressing, false otherwise
     */
    public static boolean isRandomStaticAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }
        String clean = address.trim();
        String[] parts = clean.split(":");
        if (parts.length != 6) {
            return false;
        }
        try {
            int firstOctet = Integer.parseInt(parts[0], 16);
            return (firstOctet & 0xC0) == 0xC0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Initiates active scan assistance to populate Android's BluetoothRemoteDevices cache
     * and accelerate connection to unbonded BLE peripherals with Random Static MAC addresses (REQ-CON-023.2).
     */
    private void startScanAssistance(BluetoothAdapter adapter) {
        if (mIsScanning || mIsShutDown) {
            return;
        }
        if (adapter == null || !adapter.isEnabled()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                if (DEBUG) Log.d(TAG, "Scan assist skipped: BLUETOOTH_SCAN permission not granted");
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (DEBUG) Log.d(TAG, "Scan assist skipped: BLUETOOTH_ADMIN or ACCESS_FINE_LOCATION not granted on API < 31");
                return;
            }
        }

        final BluetoothLeScanner scanner;
        try {
            scanner = adapter.getBluetoothLeScanner();
        } catch (Exception e) {
            Log.w(TAG, "Unable to get BluetoothLeScanner: " + e.getMessage());
            return;
        }
        if (scanner == null) {
            return;
        }

        if (mScanCallback == null) {
            mScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    if (result == null || result.getDevice() == null || mIsShutDown) {
                        return;
                    }
                    BluetoothDevice dev = result.getDevice();
                    if (mAddress != null && mAddress.equalsIgnoreCase(dev.getAddress())) {
                        if (DEBUG) Log.i(TAG, "Scan assist discovered target device: " + mAddress);
                        stopScanAssistance();
                        final BluetoothGatt gattToClose;
                        synchronized (mGattLock) {
                            if (mState == State.SEARCHING) {
                                gattToClose = mBluetoothGatt;
                                mBluetoothGatt = null;
                                if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                    if (DEBUG) Log.i(TAG, "Connecting GATT using scan-resolved device handle");
                                    mBluetoothGatt = dev.connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
                                }
                            } else {
                                gattToClose = null;
                            }
                        }
                        if (gattToClose != null) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                            gattToClose.disconnect();
                                            gattToClose.close();
                                        }
                                    } catch (Exception e) {
                                        Log.w(TAG, "Error closing superseded GATT", e);
                                    }
                                }
                            });
                        }
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.w(TAG, "Scan assist failed with code: " + errorCode);
                    mIsScanning = false;
                }
            };
        }

        try {
            ScanFilter filter = new ScanFilter.Builder()
                    .setDeviceAddress(mAddress)
                    .build();
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            scanner.startScan(Collections.singletonList(filter), settings, mScanCallback);
            mIsScanning = true;
            if (DEBUG) Log.i(TAG, "Scan assist started for " + mAddress);
        } catch (SecurityException | IllegalStateException | IllegalArgumentException e) {
            Log.w(TAG, "Failed to start scan assist: " + e.getMessage());
            mIsScanning = false;
        }
    }

    /**
     * Defensively stops active BLE scan assistance and clears scanning state.
     */
    protected void stopScanAssistance() {
        if (!mIsScanning) {
            return;
        }
        mIsScanning = false;
        try {
            BluetoothManager btManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (btManager != null && btManager.getAdapter() != null && btManager.getAdapter().isEnabled()) {
                BluetoothLeScanner scanner = btManager.getAdapter().getBluetoothLeScanner();
                if (scanner != null && mScanCallback != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                            scanner.stopScan(mScanCallback);
                        }
                    } else {
                        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
                            scanner.stopScan(mScanCallback);
                        }
                    }
                }
            }
        } catch (SecurityException | IllegalStateException | NullPointerException e) {
            Log.w(TAG, "Exception while stopping scan assist: " + e.getMessage());
        }
    }

    /**
     * Obtains an immutable snapshot BluetoothGattCharacteristic from a pre-allocated pool
     * to eliminate GC churn during continuous high-frequency telemetry streaming (e.g. 1-4 Hz).
     */
    protected BluetoothGattCharacteristic obtainSnapshotCharacteristic(BluetoothGattCharacteristic source, byte[] payload) {
        if (mIsShutDown) {
            return null;
        }
        BluetoothGattCharacteristic[] pool = mCharacteristicPool.computeIfAbsent(source.getUuid(), uuid -> {
            BluetoothGattCharacteristic[] array = new BluetoothGattCharacteristic[SNAPSHOT_POOL_SIZE];
            for (int i = 0; i < SNAPSHOT_POOL_SIZE; i++) {
                array[i] = new BluetoothGattCharacteristic(uuid, source.getProperties(), source.getPermissions());
            }
            return array;
        });
        int rawIndex = mPoolIndex.getAndIncrement();
        int idx = ((rawIndex % SNAPSHOT_POOL_SIZE) + SNAPSHOT_POOL_SIZE) % SNAPSHOT_POOL_SIZE;
        BluetoothGattCharacteristic snapshot = pool[idx];
        if (snapshot != null) {
            snapshot.setValue(payload);
        }
        return snapshot;
    }

    /**
     * Timeout runnable for GATT connection and service discovery.
     * Fires after {@link #SEARCH_PERIOD} if data reception has not started,
     * disconnecting and notifying that search failed.
     */
    private final Runnable mSearchTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.i(TAG, "search time out");
            stopScanAssistance();
            if (mIsShutDown) {
                return;
            }
            if (!isReceivingData()) {
                if (DEBUG) Log.i(TAG, "device is not receiving data, so this search failed");
                disconnectFromGatt();
                notifyStopSearching(false);  // we did not find the device
            }
        }
    };

    /**
     * Exponential backoff reconnect runnable.
     * Triggered upon unexpected GATT disconnection when not shut down.
     * Schedules consecutive retry attempts backing off up to {@link #MAX_RECONNECT_DELAY_MS}.
     */
    private final Runnable mReconnectRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mGattLock) {
                if (mIsShutDown) {
                    return;
                }
                if (mState != State.CONNECTED_TO_GATT && mState != State.CONNECTED_WITH_SERVICE) {
                    startSearching();
                }
            }
        }
    };

    /**
     * Periodic runnable to query the BLE battery level characteristic.
     */
    private final Runnable mBatteryReadRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsShutDown) {
                return;
            }
            final BluetoothGatt gatt = mBluetoothGatt;
            final BluetoothGattCharacteristic batteryChar = mBatteryCharacteristic;
            if (gatt != null && batteryChar != null) {
                mReadCharacteristicQueue.add(batteryChar);
                readNextCharacteristic();
            }
        }
    };
    private final String TAG = "MyBTLEDevice";
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (DEBUG)
                Log.i(TAG, "onConnectionStateChange: status=" + status + ", newState=" + newState);

            synchronized (mGattLock) {
                if (mBluetoothGatt != null && gatt != mBluetoothGatt) {
                    if (DEBUG) Log.d(TAG, "Ignoring onConnectionStateChange from stale or superseded BluetoothGatt instance");
                    return;
                }
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (DEBUG) Log.i(TAG, "Connected to GATT server");
                stopScanAssistance();

                synchronized (mGattLock) {
                    mState = State.CONNECTED_TO_GATT;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        final BluetoothGatt gatt = mBluetoothGatt;
                        if (gatt != null) {
                            gatt.discoverServices();
                            notifyStopSearching(true);  // we found the device
                        }
                    }
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                synchronized (mGattLock) {
                    mState = State.DISCONNECTED;
                }
                unregisterSensors();

                disconnectFromGatt();

                if (!mIsShutDown) {
                    long delay;
                    synchronized (mGattLock) {
                        delay = mCurrentReconnectDelayMs;
                        mCurrentReconnectDelayMs = Math.min(mCurrentReconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS);
                    }
                    Log.i(TAG, "Scheduling reconnect in " + delay + " ms (next backoff: " + mCurrentReconnectDelayMs + " ms)");
                    mHandler.removeCallbacks(mReconnectRunnable);
                    mHandler.postDelayed(mReconnectRunnable, delay);
                }
            } else {
                Log.d(TAG, "unhandled state: " + newState);
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            Log.i(TAG, "onServiceDiscovered");

            mHandler.post(new Runnable() {

                @Override
                @android.annotation.SuppressLint("MissingPermission")
                public void run() {
                    BluetoothGattService btGattService;
                    BluetoothGattCharacteristic btGattChar;

                    btGattService = gatt.getService(BluetoothConstants.UUID_SERVICE_BATTERY);
                    if (btGattService != null) { // ok, the battery service is available
                        btGattChar = btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL);
                        if (btGattChar != null) {
                            Log.i(TAG, "go battery service, request to read battery characteristic");
                            mBatteryCharacteristic = btGattChar;
                            mReadCharacteristicQueue.add(btGattChar);
                            gatt.setCharacteristicNotification(btGattChar, true);
                        } else {
                            Log.d(TAG, "WTF: btGattChar == null for battery characteristic");
                        }
                    }
                    readNextCharacteristic();
                }
            });
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (DEBUG) Log.i(TAG, "onCharacteristicRead: " + (characteristic != null ? characteristic.getUuid() : "null"));

            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null && characteristic.getValue() != null && !mIsShutDown) {
                byte[] cloned = characteristic.getValue().clone();
                BluetoothGattCharacteristic snapshot = obtainSnapshotCharacteristic(characteristic, cloned);
                if (snapshot != null) {
                    characteristicUpdate(gatt, snapshot);
                }
            } else if (characteristic != null && !mIsShutDown) {
                characteristicUpdate(gatt, characteristic);
            }
            onGattOperationCompleted();
            readNextCharacteristic();
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                            @NonNull BluetoothGattCharacteristic characteristic,
                                            @NonNull byte[] value) {
            handleCharacteristicChanged(gatt, characteristic, value);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            handleCharacteristicChanged(gatt, characteristic, characteristic != null ? characteristic.getValue() : null);
        }

        /**
         * Dispatches characteristic change notifications defensively.
         * Clones incoming raw telemetry payload bytes and packages them into an immutable
         * {@link BluetoothGattCharacteristic} snapshot (standard API available since API 18)
         * to preserve API compatibility across all {@link #measurementCharacteristicUpdate} subclass
         * implementations while preventing asynchronous buffer mutation races (REQ-CON-020.1).
         * Telemetry rates are bounded (1-4 Hz), resulting in negligible young-generation GC overhead
         * while guaranteeing thread and subscriber isolation without risking object pool cross-contamination.
         *
         * @param gatt the GATT client instance
         * @param characteristic the original framework characteristic containing metadata
         * @param value the raw incoming byte payload (non-null on API 33+) or fallback characteristic value
         */
        private void handleCharacteristicChanged(final BluetoothGatt gatt,
                                                final BluetoothGattCharacteristic characteristic,
                                                final byte[] value) {
            if (mIsShutDown) {
                return;
            }
            if (DEBUG) Log.i(TAG, "handleCharacteristicChanged: " + (characteristic != null ? characteristic.getUuid() : "null"));
            if (characteristic == null) {
                return;
            }
            byte[] rawBytes = value != null ? value : characteristic.getValue();
            if (rawBytes == null) {
                if (DEBUG) Log.w(TAG, "characteristic value is null for " + characteristic.getUuid());
                return;
            }
            byte[] cloned = rawBytes.clone();
            BluetoothGattCharacteristic snapshot = obtainSnapshotCharacteristic(characteristic, cloned);
            if (snapshot != null) {
                characteristicUpdate(gatt, snapshot);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (DEBUG) Log.i(TAG, "onDescriptorWrite: " + (descriptor != null ? descriptor.getUuid() : "null") + " status: " + status);
            onGattOperationCompleted();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (DEBUG) Log.i(TAG, "onCharacteristicWrite: " + (characteristic != null ? characteristic.getUuid() : "null") + " status: " + status);
            onGattOperationCompleted();
        }
    };

    /**
     * constructor
     */
    public MyBTLEDevice(Context context, MySensorManager mySensorManager, DeviceType deviceType, long deviceId, String address) {
        super(context, mySensorManager, deviceType, deviceId);

        mAddress = address;
        mHandler = new Handler(mContext.getMainLooper());
    }

    @Override
    public Protocol getProtocol() {
        return Protocol.BLUETOOTH_LE;
    }


    @Override
    public void startSearching() {
        synchronized (mGattLock) {
            if (mIsShutDown) {
                Log.w(TAG, "Ignoring startSearching() because device is shut down");
                return;
            }
            if (DEBUG) Log.i(TAG, "startSearching()");
            notifyStartSearching();

            mState = State.SEARCHING;

            // Cancel any pending search timeout or reconnect runnable
            mHandler.removeCallbacks(mSearchTimeoutRunnable);
            mHandler.removeCallbacks(mReconnectRunnable);

            // stop searching after configurable timeout
            long timeoutMs = TrainingApplication.getBtleSearchPeriodMs();
            mHandler.postDelayed(mSearchTimeoutRunnable, timeoutMs);
        }

        final BluetoothManager btManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter btAdapter = (btManager != null) ? btManager.getAdapter() : null;

        if (btAdapter == null) {
            Log.w(TAG, "BluetoothAdapter is null, cannot startSearching()");
            notifyStopSearching(false);
            return;
        }

        final BluetoothDevice device;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int addressType = isRandomStaticAddress(mAddress)
                    ? BluetoothDevice.ADDRESS_TYPE_RANDOM
                    : BluetoothDevice.ADDRESS_TYPE_PUBLIC;
            device = btAdapter.getRemoteLeDevice(mAddress, addressType);
        } else {
            device = btAdapter.getRemoteDevice(mAddress);
        }

        startScanAssistance(btAdapter);

        if (!mIsScanning) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mIsShutDown || device == null) {
                        return;
                    }
                    if (DEBUG) Log.i(TAG, "trying to connect gatt (fallback direct connect)");
                    if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    synchronized (mGattLock) {
                        if (mBluetoothGatt == null && mState == State.SEARCHING) {
                            mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
                        }
                    }
                }
            });
        }
    }

    protected void disconnectFromGatt() {
        if (DEBUG) Log.i(TAG, "disconnectFromGatt()");

        stopScanAssistance();
        mHandler.removeCallbacks(mSearchTimeoutRunnable);
        mHandler.removeCallbacks(mBatteryReadRunnable);
        mHandler.removeCallbacks(mGattTimeoutRunnable);
        mReadCharacteristicQueue.clear();

        synchronized (mGattLock) {
            mGattQueue.clear();
            mGattOperationPending = false;
        }

        final BluetoothGatt gattToClose;
        synchronized (mGattLock) {
            gattToClose = mBluetoothGatt;
            mBluetoothGatt = null;
        }

        if (gattToClose != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            gattToClose.disconnect();
                            gattToClose.close();
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error closing GATT", e);
                    }
                }
            });
        }
    }

    @Override
    public boolean isReceivingData() {
        return mState == State.CONNECTED_WITH_SERVICE;
    }

    @Override
    public void shutDown() {
        mIsShutDown = true;
        if (DEBUG) Log.i(TAG, "shutDown()");

        stopScanAssistance();

        synchronized (mGattLock) {
            mState = State.DISCONNECTED;
            mCurrentReconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;
            mHandler.removeCallbacks(mSearchTimeoutRunnable);
            mHandler.removeCallbacks(mReconnectRunnable);
            mHandler.removeCallbacks(mBatteryReadRunnable);
            mHandler.removeCallbacksAndMessages(null);
            mCharacteristicPool.clear();
        }

        disconnectFromGatt();

        super.shutDown();
    }

    @Override
    protected void notifyStopSearching(boolean success) {
        stopScanAssistance();
        if (success) {
            mHandler.removeCallbacks(mSearchTimeoutRunnable);
            synchronized (mGattLock) {
                mCurrentReconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;
            }
        }
        super.notifyStopSearching(success);
    }

    // Package-private inspection methods for unit testing
    boolean isScanningForTesting() {
        return mIsScanning;
    }

    ScanCallback getScanCallbackForTesting() {
        return mScanCallback;
    }
    boolean isShutDown() {
        return mIsShutDown;
    }

    long getCurrentReconnectDelayMs() {
        return mCurrentReconnectDelayMs;
    }

    Runnable getSearchTimeoutRunnable() {
        return mSearchTimeoutRunnable;
    }

    Runnable getReconnectRunnable() {
        return mReconnectRunnable;
    }

    BluetoothGattCallback getGattCallbackForTesting() {
        return mGattCallback;
    }

    protected abstract void measurementCharacteristicUpdate(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);

    private void characteristicUpdate(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
        if (DEBUG) Log.i(TAG, "characteristicUpdate");

        if (characteristic.getValue() == null) {
            if (DEBUG) Log.w(TAG, "characteristic value is null for " + characteristic.getUuid());
            return;
        }

        if (BluetoothConstants.getCharacteristicUUID(getDeviceType()).equals(characteristic.getUuid())) {

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    measurementCharacteristicUpdate(gatt, characteristic);
                }
            });
        }
        if (BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL.equals(characteristic.getUuid())) {
            Integer batteryPercentage = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            if (batteryPercentage != null) {
                if (DEBUG) Log.i(TAG, "got battery percentage: " + batteryPercentage);
                setBatteryPercentage(batteryPercentage);
                // DevicesDatabaseManager.getInstance(mContext).setBatteryPercentage(getDeviceId(), batteryPercentage);
            }

            //reread after some time
            mBatteryCharacteristic = characteristic;
            mHandler.removeCallbacks(mBatteryReadRunnable);
            mHandler.postDelayed(mBatteryReadRunnable, READ_BATTERY_PERCENTAGE_PERIOD);
        }
        // else {
        //	Log.d(TAG, "TODO: implement characteristicUpdate for other services");
        // }
    }

    public void enqueueGattOperation(GattOperation op) {
        if (op == null || mIsShutDown) return;
        mGattQueue.add(op);
        processNextGattOperation();
    }

    public void enqueueWriteDescriptor(BluetoothGattDescriptor descriptor, byte[] value) {
        enqueueGattOperation(new WriteDescriptorOp(descriptor, value));
    }

    public void enqueueReadCharacteristic(BluetoothGattCharacteristic characteristic) {
        enqueueGattOperation(new ReadCharacteristicOp(characteristic));
    }

    public void enqueueWriteCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value) {
        enqueueGattOperation(new WriteCharacteristicOp(characteristic, value));
    }

    protected void processNextGattOperation() {
        synchronized (mGattLock) {
            if (mGattOperationPending || mIsShutDown) {
                return;
            }
            final BluetoothGatt gatt = mBluetoothGatt;
            if (gatt == null) {
                mGattQueue.clear();
                return;
            }
            final GattOperation op = mGattQueue.poll();
            if (op != null) {
                mGattOperationPending = true;
                if (mHandler != null) {
                    mHandler.removeCallbacks(mGattTimeoutRunnable);
                    mHandler.postDelayed(mGattTimeoutRunnable, GATT_OP_TIMEOUT_MS);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            boolean success = false;
                            try {
                                if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                    success = op.execute(gatt);
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Exception executing queued GATT operation", e);
                            }
                            if (!success) {
                                if (DEBUG) Log.w(TAG, "Queued GATT op execute returned false; advancing queue");
                                onGattOperationCompleted();
                            }
                        }
                    });
                }
            }
        }
    }

    protected void onGattOperationCompleted() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mGattTimeoutRunnable);
        }
        synchronized (mGattLock) {
            mGattOperationPending = false;
        }
        processNextGattOperation();
    }

    public Queue<GattOperation> getGattQueueForTesting() {
        return mGattQueue;
    }

    public boolean isGattOperationPendingForTesting() {
        return mGattOperationPending;
    }

    protected void readNextCharacteristic() {
        if (DEBUG) Log.i(TAG, "readNextCharacteristic");

        final BluetoothGattCharacteristic gattChar = mReadCharacteristicQueue.poll();
        if (gattChar != null) {
            if (DEBUG) Log.i(TAG, "queue is not empty, so we read the next characteristic");

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    final BluetoothGatt gatt = mBluetoothGatt;
                    if (gatt != null) {
                        gatt.readCharacteristic(gattChar);
                    }
                }
            });
        } else { // queue is empty
            if (!sensorsRegistered()) {  // but sensors are not yet registered
                synchronized (mGattLock) {
                    mState = State.CONNECTED_WITH_SERVICE;
                }
                setLastActive();
                registerSensors();

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        final BluetoothGatt gatt = mBluetoothGatt;
                        if (gatt == null) {
                            return;
                        }
                        configureServices(gatt);
                    }
                });
            }
        }
    }

    protected void configureServices(BluetoothGatt gatt) {
        BluetoothGattService btGattService = gatt.getService(BluetoothConstants.getServiceUUID(getDeviceType()));
        if (btGattService == null) {
            Log.d(TAG, "WTF, we expected a service here!");
            return;
        }
        BluetoothGattCharacteristic btGattChar = btGattService.getCharacteristic(BluetoothConstants.getCharacteristicUUID(getDeviceType()));
        if (btGattChar == null) {
            Log.d(TAG, "Expected characteristic not found!");
            return;
        }
        gatt.setCharacteristicNotification(btGattChar, true);

        BluetoothGattDescriptor descriptor = btGattChar.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG);
        if (descriptor != null) {
            enqueueWriteDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        }

        // Chained battery CCCD write (REQ-CON-007, ATT-3312) via queue
        if (mBatteryCharacteristic != null && (mBatteryCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            BluetoothGattDescriptor batteryDescriptor = mBatteryCharacteristic.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG);
            if (batteryDescriptor != null) {
                enqueueWriteDescriptor(batteryDescriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
        }
    }

    protected enum State {DISCONNECTED, SEARCHING, CONNECTED_TO_GATT, CONNECTED_WITH_SERVICE}
}
