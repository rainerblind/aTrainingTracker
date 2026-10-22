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
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.MyRemoteDevice;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;

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
    private volatile BluetoothGattCharacteristic mBatteryCharacteristic;
    private volatile boolean mIsShutDown = false;
    private volatile long mCurrentReconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;

    private static final int SNAPSHOT_POOL_SIZE = 8;
    private final Map<UUID, BluetoothGattCharacteristic[]> mCharacteristicPool = new ConcurrentHashMap<>();
    private final AtomicInteger mPoolIndex = new AtomicInteger(0);

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

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (DEBUG) Log.i(TAG, "Connected to GATT server");

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
            if (DEBUG) Log.i(TAG, "onDescriptorWrite: " + descriptor.getUuid() + " status: " + status);
            // After primary measurement descriptor is written, chain writing battery CCCD if supported (REQ-CON-007, ATT-3312)
            if (mBatteryCharacteristic != null && (mBatteryCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                BluetoothGattDescriptor batteryDescriptor = mBatteryCharacteristic.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG);
                if (batteryDescriptor != null && !batteryDescriptor.equals(descriptor)) {
                    if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        batteryDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(batteryDescriptor);
                    }
                }
            }
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

            // stop searching after some time
            mHandler.postDelayed(mSearchTimeoutRunnable, SEARCH_PERIOD);
        }

        final BluetoothDevice device = ((BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter().getRemoteDevice(mAddress);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mIsShutDown) {
                    return;
                }
                if (DEBUG) Log.i(TAG, "trying to connect gatt");
                if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                synchronized (mGattLock) {
                    mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
                }
            }
        });
    }

    protected void disconnectFromGatt() {
        if (DEBUG) Log.i(TAG, "disconnectFromGatt()");

        mHandler.removeCallbacks(mSearchTimeoutRunnable);
        mHandler.removeCallbacks(mBatteryReadRunnable);
        mReadCharacteristicQueue.clear();

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
        if (success) {
            mHandler.removeCallbacks(mSearchTimeoutRunnable);
            synchronized (mGattLock) {
                mCurrentReconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;
            }
        }
        super.notifyStopSearching(success);
    }

    // Package-private inspection methods for unit testing
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
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }
                    }
                });
            }
        }
    }

    protected enum State {DISCONNECTED, SEARCHING, CONNECTED_TO_GATT, CONNECTED_WITH_SERVICE}
}
