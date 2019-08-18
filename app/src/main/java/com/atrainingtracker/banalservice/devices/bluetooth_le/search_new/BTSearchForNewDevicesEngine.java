/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
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

package com.atrainingtracker.banalservice.devices.bluetooth_le.search_new;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.SearchForNewDevicesInterface;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEBikePowerDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BluetoothConstants;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;


@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BTSearchForNewDevicesEngine
        implements SearchForNewDevicesInterface {
    private static final String TAG = "BTSearchForNewDevicesEngine";
    private static final boolean DEBUG = BANALService.DEBUG & false;

    protected Context mContext;
    protected Handler mHandler;
    protected DeviceType mDeviceType;
    protected Map<String, BluetoothGatt> mBTGatts = new HashMap<String, BluetoothGatt>();
    protected Map<String, Boolean> mInformedDevices = new HashMap<String, Boolean>();
    protected Map<String, String> mNameMap = new HashMap<String, String>();
    protected Map<String, String> mManufacturerMap = new HashMap<String, String>();
    protected Map<String, Integer> mBatteryPercentage = new HashMap<String, Integer>();
    protected Map<String, Queue<BluetoothGattCharacteristic>> mReadCharacteristicQueue = new HashMap<String, Queue<BluetoothGattCharacteristic>>();
    BluetoothAdapter mBluetoothAdapter;
    boolean scanning = false;
    private IBTSearchForNewDevicesEngineInterface mCallbackInterface;
    // callback to get the manufacturer and battery percentage
    // also check whether this is a bike speed, bike cadence, or combined speed and cadence device
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            if (DEBUG)
                Log.i(TAG, "onConnectionStateChange: status=" + status + ", newState=" + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (DEBUG)
                    Log.i(TAG, "Connected to GATT server, attempting to start service discovery");

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mNameMap.put(gatt.getDevice().getAddress(), gatt.getDevice().getName());
                        gatt.discoverServices();
                    }
                });


            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (DEBUG) Log.i(TAG, "Disconnected from GATT server.");
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            if (DEBUG) Log.i(TAG, "onServiceDiscovered");

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    BluetoothGattService btGattService;
                    String address = gatt.getDevice().getAddress();
                    mReadCharacteristicQueue.put(address, new LinkedList<BluetoothGattCharacteristic>());

                    btGattService = gatt.getService(BluetoothConstants.UUID_SERVICE_DEVICE_INFORMATION);
                    if (btGattService != null) {
                        Log.i(TAG, "go device information service, adding manufacturer name characteristic to the read queue");
                        mReadCharacteristicQueue.get(address).add(btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME));
                    }

                    btGattService = gatt.getService(BluetoothConstants.UUID_SERVICE_BATTERY);
                    if (btGattService != null) {
                        Log.i(TAG, "go battery service, adding battery characteristic top the read queue");
                        mReadCharacteristicQueue.get(address).add(btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL));
                    }

                    btGattService = gatt.getService(BluetoothConstants.getServiceUUID(DeviceType.BIKE_SPEED_AND_CADENCE));
                    if (btGattService != null) {
                        Log.i(TAG, "got cycling speed and cadence service, adding feature characteristic to read queue");
                        mReadCharacteristicQueue.get(address).add(btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_CYCLING_SPEED_AND_CADENCE_FEATURE));
                    }

                    btGattService = gatt.getService(BluetoothConstants.getServiceUUID(DeviceType.BIKE_POWER));
                    if (btGattService != null) {
                        Log.i(TAG, "got bike power service, adding feature characteristic to read queue");
                        mReadCharacteristicQueue.get(address).add(btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_CYCLING_POWER_FEATURE));
                    }

                    readNextCharacteristic(address);
                }
            });
        }

        @Override
        public void onCharacteristicRead(final BluetoothGatt gatt,
                                         final BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (DEBUG) Log.i(TAG, "onCharacteristicRead: " + characteristic.getUuid());

            mHandler.post(new Runnable() {

                @SuppressLint("LongLogTag")
                @Override
                public void run() {
                    String address = gatt.getDevice().getAddress();

                    if (BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL.equals(characteristic.getUuid())) {
                        int batteryPercentage = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                        if (DEBUG) Log.d(TAG, "got battery percentage: " + batteryPercentage);
                        mBatteryPercentage.put(address, batteryPercentage);
                    } else if (BluetoothConstants.UUID_CHARACTERISTIC_MANUFACTURER_NAME.equals(characteristic.getUuid())) {
                        String manufacturerName = characteristic.getStringValue(0);
                        if (DEBUG) Log.d(TAG, "got manufacturer: " + manufacturerName);
                        mManufacturerMap.put(address, manufacturerName);
                    } else if (BluetoothConstants.UUID_CHARACTERISTIC_CYCLING_SPEED_AND_CADENCE_FEATURE.equals(characteristic.getUuid())) {
                        if (DEBUG) Log.i(TAG, "Received cycling speed and cadence feature");

                        final int MASK_BIKE_SPEED_AND_CADENCE = 0x03;
                        final int BIKE_SPEED = 0x01;
                        final int BIKE_CADENCE = 0x02;
                        final int BIKE_SPEED_AND_CADENCE = 0x03;

                        int flag = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

                        DeviceType deviceType = null;
                        switch (flag & MASK_BIKE_SPEED_AND_CADENCE) {
                            case BIKE_SPEED:
                                deviceType = DeviceType.BIKE_SPEED;
                                break;
                            case BIKE_CADENCE:
                                deviceType = DeviceType.BIKE_CADENCE;
                                break;
                            case BIKE_SPEED_AND_CADENCE:
                                deviceType = DeviceType.BIKE_SPEED_AND_CADENCE;
                                break;
                        }

                        if (deviceType == getDeviceType()) { // are we searching for this device type?
                            if (DEBUG)
                                Log.i(TAG, "FTW: we found a device with the correct device type");
                            newDeviceFound(gatt.getDevice().getAddress());
                        } else {
                            if (DEBUG) Log.i(TAG, "hm, the device type is not correct");
                        }
                    } else if (BluetoothConstants.UUID_CHARACTERISTIC_CYCLING_POWER_FEATURE.equals(characteristic.getUuid())
                            && getDeviceType() == DeviceType.BIKE_POWER) {
                        newDeviceFound(gatt.getDevice().getAddress());  // first, we have to put this device into the database, then we can add its features...
                        long deviceId = DevicesDatabaseManager.getDeviceId(DeviceType.BIKE_POWER, gatt.getDevice().getAddress());
                        DevicesDatabaseManager.putBikePowerSensorFlags(deviceId, BTLEBikePowerDevice.btFeature2BikePowerSensorFlags(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0)));
                    } else {
                        Log.d(TAG, "unknown characteristic: " + characteristic.getUuid());
                    }

                    readNextCharacteristic(address);

                }
            });


        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (DEBUG) Log.i(TAG, "onCharacteristicChanged: " + characteristic.getUuid());
            // should never ever be called here
        }
    };
    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (DEBUG)
                Log.i(TAG, "wow, we found a device: address=" + device.getAddress() + ", name=" + device.getName());

            if (!mBTGatts.containsKey(device.getAddress())) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (DEBUG) Log.i(TAG, "trying to connect to Gatt");
                        mBTGatts.put(device.getAddress(), device.connectGatt(mContext, false, mGattCallback));
                    }
                });
            }
        }
    };

    public BTSearchForNewDevicesEngine(Context context, DeviceType deviceType, IBTSearchForNewDevicesEngineInterface callbackInterface) {
        mContext = context;

        mHandler = new Handler(mContext.getMainLooper());

        mDeviceType = deviceType;
        mCallbackInterface = callbackInterface;

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    // 	@Override
    public DeviceType getDeviceType() {
        return mDeviceType;
    }

    @Override
    public void startAsyncSearch() {
        if (DEBUG) Log.i(TAG, "startAsyncSearch()");

        if (!scanning) {
            if (DEBUG) Log.i(TAG, "starting to search for " + getDeviceType().name() + " devices");
            scanning = true;
            mBluetoothAdapter.startLeScan(new UUID[]{BluetoothConstants.getServiceUUID(getDeviceType())}, mLeScanCallback);
        } else if (DEBUG) {
            Log.i(TAG, "already searching");
        }
    }

    @Override
    public void stopAsyncSearch() {
        if (DEBUG) Log.i(TAG, "stopAsyncSearch()");

        if (scanning) {
            scanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }

        for (final BluetoothGatt btGatt : mBTGatts.values()) {
            if (btGatt != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        btGatt.disconnect();
                        btGatt.close();
                    }
                });
            } else {
                Log.d(TAG, "WTF: btGatt == null");
            }
        }
    }

    protected void newDeviceFound(String address) {
        if (!mInformedDevices.containsKey(address)) {
            mInformedDevices.put(address, true);
            mCallbackInterface.onNewDeviceFound(getDeviceType(), address, mNameMap.get(address), mManufacturerMap.get(address),
                    mBatteryPercentage.containsKey(address) ? mBatteryPercentage.get(address) : -1);
        }
    }

    protected void readNextCharacteristic(String address) {
        if (DEBUG) Log.i(TAG, "readNextCharacteristic: " + address);

        final BluetoothGatt gatt = mBTGatts.get(address);
        if (gatt == null) {  // TODO: check when this happens and how to handle the situation properly!
            Log.d(TAG, "WTF: gatt == null!");
            return;
        }

        if (!mReadCharacteristicQueue.get(address).isEmpty()) {
            if (DEBUG) Log.i(TAG, "queue is not empty, so we read the next characteristic");
            final BluetoothGattCharacteristic characteristic = mReadCharacteristicQueue.get(address).poll();
            if (characteristic != null) {
                if (DEBUG) Log.i(TAG, "UUID of characteristic: " + characteristic.getUuid());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        gatt.readCharacteristic(characteristic); // the result is reported via the onCharacteristicRead method
                    }
                });
            }
        }
        // queue is empty => everything is read => inform the callback that a device was found
        // except for the case that it is a bike device.  In this case, we have to check the type
        else if (mDeviceType == DeviceType.BIKE_CADENCE || mDeviceType == DeviceType.BIKE_SPEED || mDeviceType == DeviceType.BIKE_SPEED_AND_CADENCE
                || mDeviceType == DeviceType.BIKE_POWER) {
            // the device will be found somewhere else (when reading the csc feature)
        } else {
            newDeviceFound(address);
        }
    }

    public interface IBTSearchForNewDevicesEngineInterface {
        void onSearchStopped();

        void onNewDeviceFound(DeviceType deviceType,
                              String BluetoothMACAddress,
                              String name,
                              String manufacturer,
                              int batteryPercentage);
    }

}
