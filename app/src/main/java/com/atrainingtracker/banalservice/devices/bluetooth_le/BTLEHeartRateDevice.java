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

package com.atrainingtracker.banalservice.devices.bluetooth_le;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BTLEHeartRateDevice extends MyBTLEDevice {
    private static final boolean DEBUG = BANALService.DEBUG & false;
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
        int flag = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

        int format = -1;
        if ((flag & 0x01) != 0) {
            format = BluetoothGattCharacteristic.FORMAT_UINT16;
            // Log.i(TAG, "Heart rate format UINT16.");
        } else {
            format = BluetoothGattCharacteristic.FORMAT_UINT8;
            // Log.i(TAG, "Heart rate format UINT8.");
        }
        int heartRate = characteristic.getIntValue(format, 1);
        if (DEBUG) Log.i(TAG, String.format("Received heart rate: %d", heartRate));
        mHeartRateSensor.newValue(heartRate);

    }
}
