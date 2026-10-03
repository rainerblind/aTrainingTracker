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

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;

public class BTLEHeartRateDevice extends MyBTLEDevice {
    private static final boolean DEBUG = BANALService.getDebug(false);
    protected MySensor<Integer> mHeartRateSensor;
    private final String TAG = "ANTHeartRateDevice";


    /**
     * constructor
     **/
    public BTLEHeartRateDevice(Context context, MySensorManager mySensorManager, long deviceID, String address) {
        super(context, mySensorManager, DeviceType.HRM, deviceID, address);
        if (DEBUG) {
            Log.d(TAG, "creating HR device");
        }
    }

    @Override
    protected void addSensors() {
        mHeartRateSensor = new MySensor<Integer>(this, SensorType.HR);

        addSensor(mHeartRateSensor);
    }

    @Override
    protected void measurementCharacteristicUpdate(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return;
        }
        byte[] value = characteristic.getValue();
        if (value == null || value.length < 2) {
            if (DEBUG) Log.w(TAG, "measurementCharacteristicUpdate: packet too short or null");
            return;
        }

        try {
            Integer flag = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            if (flag == null) {
                return;
            }

            int format;
            int requiredLength;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                requiredLength = 3;
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                requiredLength = 2;
            }

            if (value.length >= requiredLength) {
                Integer heartRate = characteristic.getIntValue(format, 1);
                if (heartRate != null) {
                    if (DEBUG) Log.i(TAG, String.format("Received heart rate: %d", heartRate));
                    mHeartRateSensor.newValue(heartRate);
                }
            } else {
                Log.w(TAG, "Heart rate characteristic truncated: length=" + value.length + ", required=" + requiredLength);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing HR characteristic notification", e);
        }
    }
}
