package com.atrainingtracker.banalservice.Devices.bluetooth_le;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.DeviceType;
import com.atrainingtracker.banalservice.Sensor.MySensor;
import com.atrainingtracker.banalservice.Sensor.MySensorManager;
import com.atrainingtracker.banalservice.Sensor.SensorType;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BTLEHeartRateDevice extends MyBTLEDevice {
    private static final boolean DEBUG = BANALService.DEBUG & false;
    protected MySensor<Integer> mHeartRateSensor;
    private String TAG = "ANTHeartRateDevice";


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
