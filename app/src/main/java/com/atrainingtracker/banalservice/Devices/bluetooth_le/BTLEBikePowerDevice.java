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

package com.atrainingtracker.banalservice.Devices.bluetooth_le;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.BikePowerSensorsHelper;
import com.atrainingtracker.banalservice.Devices.DeviceType;
import com.atrainingtracker.banalservice.Sensor.MyDoubleAccumulatorSensor;
import com.atrainingtracker.banalservice.Sensor.MySensor;
import com.atrainingtracker.banalservice.Sensor.MySensorManager;
import com.atrainingtracker.banalservice.Sensor.SensorType;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BTLEBikePowerDevice extends MyBTLEDevice {
    protected static final int MAX_IDENTICALS = 4;
    private static final boolean DEBUG = BANALService.DEBUG & false;

    private static final int PEDAL_POWER_BALANCE_SUPPORTED_MASK = 1;
    private static final int ACCUMULATED_TORQUE_SUPPORTED_MASK = 1 << 1;
    private static final int WHEEL_REVOLUTION_DATA_SUPPORTED_MASK = 1 << 2;
    private static final int CRANK_REVOLUTION_DATA_SUPPORTED_MASK = 1 << 3;
    private static final int EXTREME_MAGNITUDES_SUPPORTED_MASK = 1 << 4;
    private static final int EXTREME_ANGLES_SUPPORTED_MASK = 1 << 5;
    private static final int TOP_AND_BOTTOM_DEAD_SPOT_ANGLES_SUPPORTED_MASK = 1 << 6;
    private static final int ACCUMULATED_ENERGY_SUPPORTED_MASK = 1 << 7;

    private static final int PEDAL_POWER_BALANCE_PRESENT_MASK = 1;
    private static final int PEDAL_POWER_BALANCE_REFERENCE_MASK = 1 << 1;
    private static final int ACCUMULATED_TORQUE_PRESENT_MASK = 1 << 2;
    private static final int ACCUMULATED_TORQUE_SOURCE_MASK = 1 << 3;
    private static final int WHEEL_REVOLUTION_DATA_PRESENT_MASK = 1 << 4;
    private static final int CRANK_REVOLUTION_DATA_PRESENT_MASK = 1 << 5;
    private static final int EXTREME_FORCE_MAGNITUDE_PRESENT_MASK = 1 << 6;
    private static final int EXTREME_TORQUE_MAGNITUDE_PRESENT_MASK = 1 << 7;
    private static final int EXTREME_ANGLES_PRESENT_MASK = 1 << 8;
    private static final int TOP_DEAD_SPOT_ANGLE_PRESENT_MASK = 1 << 9;
    private static final int BOTTOM_DEAD_SPOT_ANGLE_PRESENT_MASK = 1 << 10;
    private static final int ACCUMULATED_ENERGY_PRESENT_MASK = 1 << 11;

    private static final int FLAGS_WIDTH = 2;
    private static final int INSTANTANEOUS_POWER_WIDTH = 2;
    private static final int PEDAL_POWER_BALANCE_WIDTH = 1;
    private static final int ACCUMULATED_TORQUE_WIDTH = 2;
    private static final int WHEEL_REVOLUTION_DATA__CUMULATIVE_WHEEL_REVOLUTIONS_WIDTH = 4;
    private static final int WHEEL_REVOLUTION_DATA__LAST_WHEEL_EVENT_TIME_WIDTH = 2;
    private static final int CRANK_REVOLUTION_DATA__CUMULATIVE_CRANK_REVOLUTIONS_WIDTH = 2;
    private static final int CRANK_REVOLUTION_DATA__LAST_CRANK_EVENT_TIME_WIDTH = 2;
    private static final int EXTREME_FORCE_MAGNITUDES__MAXIMUM_FORCE_MAGNITUDE_WIDTH = 2;
    private static final int EXTREME_FORCE_MAGNITUDES__MINIMUM_FORCE_MAGNITUDE_WIDTH = 2;
    private static final int EXTREME_TORQUE_MAGNITUDES__MAXIMUM_TORQUE_MAGNITUDE_WIDTH = 2;
    private static final int EXTREME_TORQUE_MAGNITUDES__MINIMUM_TORQUE_MAGNITUDE_WIDTH = 2;
    private static final int EXTREME_ANGELS_WIDTH = 3;  // really sucks?
    private static final int TOP_DEAD_SPOT_ANGLE_WIDTH = 2;
    private static final int BOTTOM_DEAD_SPOT_ANGLE_WIDTH = 2;
    private static final int ACCUMULATED_ENERGY_WIDTH = 2;


    // some variables to calc the speed, distance, and cadence
    protected boolean mLastWheelRevolutionsValid = false;
    protected boolean mLastCrankRevolutionsValid = false;
    protected long mInitWheelRevolutions = 0;
    protected long mLastWheelRevolutions = 0;
    protected long mLastWheelEventTime = 0;
    protected long mLastCrankRevolutions = 0;
    protected long mLastCrankEventTime = 0;
    protected int mIdenticalWheelTime = 0;
    protected int mIdenticalCrankTime = 0;
    protected MySensor<Double> mCadenceSensor;
    protected MySensor<Double> mSpeedSensor;
    protected MySensor<Double> mPaceSensor;
    protected MyDoubleAccumulatorSensor mDistanceSensor;
    protected MyDoubleAccumulatorSensor mLapDistanceSensor;
    protected MySensor<Integer> mPowerSensor;
    protected MySensor<Double> mPowerBalanceSensor;
    protected boolean mIsPowerBalanceSupported = false;
    protected boolean mIsWheelRevolutionDataSupported = false;
    protected boolean mIsCrankRevolutionDataSupported = false;
    protected boolean mDoublePowerBalanceValues = false;
    protected boolean mInvertPowerBalanceValues = false;
    private String TAG = BTLEBikePowerDevice.class.getName();


    /**
     * constructor
     **/
    public BTLEBikePowerDevice(Context context, MySensorManager mySensorManager, long deviceID, String address) {
        super(context, mySensorManager, DeviceType.BIKE_POWER, deviceID, address);

        if (DEBUG) Log.i(TAG, "BTLEBikePowerDevice, deviceId=" + deviceID + ", address=" + address);
        addSensors();
    }

    private static boolean isFeatureSupported(int feature, int mask) {
        return (feature & mask) != 0;
    }

    public static boolean isPowerBalanceSupported(int feature) {
        return isFeatureSupported(feature, PEDAL_POWER_BALANCE_SUPPORTED_MASK);
    }

    public static boolean isAccumulatedTorqueSupported(int feature) {
        return isFeatureSupported(feature, ACCUMULATED_TORQUE_SUPPORTED_MASK);
    }

    public static boolean isWheelRevolutionDataSupported(int feature) {
        return isFeatureSupported(feature, WHEEL_REVOLUTION_DATA_SUPPORTED_MASK);
    }

    public static boolean isCrankRevolutionDataSupported(int feature) {
        return isFeatureSupported(feature, CRANK_REVOLUTION_DATA_SUPPORTED_MASK);
    }

    public static boolean isExtremeMagnitudesSupported(int feature) {
        return isFeatureSupported(feature, EXTREME_MAGNITUDES_SUPPORTED_MASK);
    }

    public static boolean isExtremeAnglesSupported(int feature) {
        return isFeatureSupported(feature, EXTREME_ANGLES_SUPPORTED_MASK);
    }

    public static boolean isTopAndBottomDeadSpotAnglesSupported(int feature) {
        return isFeatureSupported(feature, TOP_AND_BOTTOM_DEAD_SPOT_ANGLES_SUPPORTED_MASK);
    }

    public static boolean isAccumulatedEnergySupported(int feature) {
        return isFeatureSupported(feature, ACCUMULATED_ENERGY_SUPPORTED_MASK);
    }

    private static boolean isFeaturePresent(int feature, int mask) {
        return (feature & mask) != 0;
    }

    private static boolean isPowerBalancePresent(int feature) {
        return isFeaturePresent(feature, PEDAL_POWER_BALANCE_PRESENT_MASK);
    }

    private static boolean isAccumulatedTorquePresent(int feature) {
        return isFeaturePresent(feature, ACCUMULATED_TORQUE_PRESENT_MASK);
    }

    private static boolean isWheelRevolutionDataPresent(int feature) {
        return isFeaturePresent(feature, WHEEL_REVOLUTION_DATA_PRESENT_MASK);
    }

    private static boolean isCrankRevolutionDataPresent(int feature) {
        return isFeaturePresent(feature, CRANK_REVOLUTION_DATA_PRESENT_MASK);
    }

    private static boolean isExtremeForceMagnitudesPresent(int feature) {
        return isFeaturePresent(feature, EXTREME_FORCE_MAGNITUDE_PRESENT_MASK);
    }

    private static boolean isExtremeTorqueMagnitudesPresent(int feature) {
        return isFeaturePresent(feature, EXTREME_TORQUE_MAGNITUDE_PRESENT_MASK);
    }

    private static boolean isExtremeAnglesPresent(int feature) {
        return isFeaturePresent(feature, EXTREME_ANGLES_PRESENT_MASK);
    }

    private static boolean isTopDeadSpotAnglePresent(int feature) {
        return isFeaturePresent(feature, TOP_DEAD_SPOT_ANGLE_PRESENT_MASK);
    }

    private static boolean isBottomDeadSpotAnglePresent(int feature) {
        return isFeaturePresent(feature, BOTTOM_DEAD_SPOT_ANGLE_PRESENT_MASK);
    }

    private static boolean isAccumulatedEnergyPresent(int feature) {
        return isFeaturePresent(feature, ACCUMULATED_ENERGY_PRESENT_MASK);
    }

    public static int btFeature2BikePowerSensorFlags(int feature) {
        int sensorFlags = 0;
        if (isPowerBalanceSupported(feature)) {
            sensorFlags = BikePowerSensorsHelper.addPowerBalanceFlag(sensorFlags);
        }
        if (isAccumulatedTorqueSupported(feature)) {
            sensorFlags = BikePowerSensorsHelper.addAccumulatedTorqueFlag(sensorFlags);
        }
        if (isWheelRevolutionDataSupported(feature)) {
            sensorFlags = BikePowerSensorsHelper.addWheelRevolutionDataFlag(sensorFlags);
        }
        if (isCrankRevolutionDataSupported(feature)) {
            sensorFlags = BikePowerSensorsHelper.addCrankRevolutionDataFlag(sensorFlags);
        }
        if (isExtremeForceMagnitudesPresent(feature)) {
            sensorFlags = BikePowerSensorsHelper.addExtremeMagnitudesFlag(sensorFlags);
        }
        if (isExtremeAnglesSupported(feature)) {
            sensorFlags = BikePowerSensorsHelper.addExtremeAnglesFlag(sensorFlags);
        }
        if (isTopAndBottomDeadSpotAnglesSupported(feature)) {
            sensorFlags = BikePowerSensorsHelper.addDeadSpotAnglesFlag(sensorFlags);
        }
        if (isAccumulatedEnergySupported(feature)) {
            sensorFlags = BikePowerSensorsHelper.addAccumulatedEnergyFlag(sensorFlags);
        }

        return sensorFlags;
    }

    @Override
    protected void addSensors() {
        if (DEBUG) Log.i(TAG, "addSensors()");

        if (getDeviceId() <= 0) {
            if (DEBUG) Log.i(TAG, "addSensors: aborting because mDeviceId not yet initialized.");
            return;
        }

        addPowerSensor();

        int sensorFlags = DevicesDatabaseManager.getBikePowerSensorFlags(getDeviceId());
        if (BikePowerSensorsHelper.isPowerBalanceSupported(sensorFlags)) {
            mIsPowerBalanceSupported = true;
            addPowerBalanceSensor();
        }
        if (BikePowerSensorsHelper.isWheelRevolutionDataSupported(sensorFlags)) {
            mIsWheelRevolutionDataSupported = true;
            addSpeedAndDistanceSensors();
        }
        if (BikePowerSensorsHelper.isCrankRevolutionDataSupported(sensorFlags)) {
            mIsCrankRevolutionDataSupported = true;
            addCadenceSensor();
        }

        mDoublePowerBalanceValues = BikePowerSensorsHelper.doublePowerBalanceValues(sensorFlags);
        mInvertPowerBalanceValues = BikePowerSensorsHelper.invertPowerBalanceValues(sensorFlags);
    }


    protected void addPowerSensor() {
        if (DEBUG) Log.i(TAG, "addPowerSensor()");

        mPowerSensor = new MySensor<>(this, SensorType.POWER);
        addSensor(mPowerSensor);
    }

    protected void addPowerBalanceSensor() {
        mPowerBalanceSensor = new MySensor<>(this, SensorType.PEDAL_POWER_BALANCE);
        addSensor(mPowerBalanceSensor);
    }

    protected void addCadenceSensor() {
        if (DEBUG) Log.i(TAG, "addCadenceSensor()");

        mCadenceSensor = new MySensor<>(this, SensorType.CADENCE);
        addSensor(mCadenceSensor);
    }

    protected void addSpeedAndDistanceSensors() {
        if (DEBUG) Log.i(TAG, "addSpeedAndDistanceSensors()");

        mSpeedSensor = new MySensor<>(this, SensorType.SPEED_mps);
        mPaceSensor = new MySensor<>(this, SensorType.PACE_spm);
        mDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m);
        mLapDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m_LAP);

        addSensor(mSpeedSensor);
        addSensor(mPaceSensor);
        addSensor(mDistanceSensor);
        addSensor(mLapDistanceSensor);
    }

    @Override
    protected void newLap() {
        if (mLapDistanceSensor != null) {
            mLapDistanceSensor.reset();
        }
    }

    // TODO: also check whether the corresponding features are supported
    @Override
    protected void measurementCharacteristicUpdate(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        int flags = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        int offset = FLAGS_WIDTH;

        int power = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset);
        if (DEBUG) Log.i(TAG, "got new power value: " + power);
        mPowerSensor.newValue(power);
        offset += INSTANTANEOUS_POWER_WIDTH;

        if (isPowerBalancePresent(flags)) {
            if (DEBUG) Log.i(TAG, "Power Balance present");

            if (mIsPowerBalanceSupported) {
                double value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
                value = mDoublePowerBalanceValues ? value : value / 2;                              // according to the spec, the value must be halved.  Optionally, we double it by not halving
                value = mInvertPowerBalanceValues ? 100 - value : value;                            // optionally, invert the value
                mPowerBalanceSensor.newValue(value);
            } else {
                Log.d(TAG, "WTF: BTLE Power Profile not properly implemented: Power Balance present but was not supported");
            }

            offset += PEDAL_POWER_BALANCE_WIDTH;
        }

        if (isAccumulatedTorquePresent(flags)) {
            if (DEBUG) Log.i(TAG, "Accumulated Torque present (but currently not used)");

            // TODO: do something with this???
            offset += ACCUMULATED_TORQUE_WIDTH;
        }

        if (isWheelRevolutionDataPresent(flags)) {
            if (DEBUG) Log.i(TAG, "wheelRevolutionDataPresent");

            if (mIsWheelRevolutionDataSupported) {

                long cumulativeWheelRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, offset);
                long wheelEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset + WHEEL_REVOLUTION_DATA__CUMULATIVE_WHEEL_REVOLUTIONS_WIDTH);
                if (DEBUG)
                    Log.i(TAG, "revolutions: " + cumulativeWheelRevolutions + ", time: " + wheelEventTime);
                // TODO: what to do when these values are negative?

                // calc speed and distance
                if (mLastWheelRevolutionsValid) {
                    if (wheelEventTime > mLastWheelEventTime) {  // avoiding negative values
                        mIdenticalWheelTime = 0;

                        long revDiff = cumulativeWheelRevolutions - mLastWheelRevolutions;
                        long timeDiff = wheelEventTime - mLastWheelEventTime;
                        if (DEBUG) Log.i(TAG, "revDiff=" + revDiff + ", timeDiff=" + timeDiff);

                        double speed = mCalibrationFactor * revDiff * 2048 / timeDiff;   // Note that this value is differs from the one of the bike speed case
                        if (DEBUG) Log.i(TAG, "got new speed: " + speed);
                        mSpeedSensor.newValue(speed);
                        if (revDiff != 0) {
                            mPaceSensor.newValue(1 / speed);
                        }

                        double distance = mCalibrationFactor * (cumulativeWheelRevolutions - mInitWheelRevolutions);
                        if (DEBUG) Log.i(TAG, "got new distance: " + distance);
                        mDistanceSensor.newValue(distance);
                        mLapDistanceSensor.newValue(distance);
                    } else {
                        mIdenticalWheelTime++;
                        if (mIdenticalWheelTime >= MAX_IDENTICALS) {
                            if (DEBUG)
                                Log.i(TAG, mIdenticalWheelTime + " identical wheel times => reset speed to zero");
                            mSpeedSensor.newValue(0.0);
                            mPaceSensor.newValue(null);
                        }
                    }
                } else {
                    mInitWheelRevolutions = cumulativeWheelRevolutions;
                }
                mLastWheelRevolutions = cumulativeWheelRevolutions;
                mLastWheelEventTime = wheelEventTime;
                mLastWheelRevolutionsValid = true;
            } else {
                Log.d(TAG, "WTF: BTLE Power Profile not properly implemented: Wheel Revolution Data present but was not supported");
            }

            offset += WHEEL_REVOLUTION_DATA__CUMULATIVE_WHEEL_REVOLUTIONS_WIDTH + WHEEL_REVOLUTION_DATA__LAST_WHEEL_EVENT_TIME_WIDTH;
        }

        if (isCrankRevolutionDataPresent(flags)) {
            if (DEBUG) Log.i(TAG, "crankRevolutionDataPresent");

            if (mIsCrankRevolutionDataSupported) {

                long cumulativeCrankRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                long crankEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset + CRANK_REVOLUTION_DATA__CUMULATIVE_CRANK_REVOLUTIONS_WIDTH);
                if (DEBUG)
                    Log.i(TAG, "revolutions: " + cumulativeCrankRevolutions + ", time: " + crankEventTime);

                // calc cadence
                if (mLastCrankRevolutionsValid) {
                    if (crankEventTime > mLastCrankEventTime) { // avoiding negative values
                        mIdenticalCrankTime = 0;

                        long revDiff = cumulativeCrankRevolutions - mLastCrankRevolutions;
                        long timeDiff = crankEventTime - mLastCrankEventTime;
                        if (DEBUG) Log.i(TAG, "revDiff=" + revDiff + ", timeDiff=" + timeDiff);

                        double cadence = 60 * revDiff * 1024 / timeDiff;         // Furthermore, note that this time multiplier is different from the one for the speed
                        if (DEBUG) Log.i(TAG, "got new cadence: " + cadence);
                        mCadenceSensor.newValue(cadence);
                    } else {
                        mIdenticalCrankTime++;
                        if (mIdenticalCrankTime >= MAX_IDENTICALS) {
                            if (DEBUG)
                                Log.i(TAG, mIdenticalCrankTime + " identical crank times => reset cadence to zero");
                            mCadenceSensor.newValue(0.0);
                        }
                    }
                }
                mLastCrankRevolutions = cumulativeCrankRevolutions;
                mLastCrankEventTime = crankEventTime;
                mLastCrankRevolutionsValid = true;
            } else {
                Log.d(TAG, "WTF: BTLE Power Profile not properly implemented: Crank Revolution Data present but was not supported");
            }

            offset += CRANK_REVOLUTION_DATA__CUMULATIVE_CRANK_REVOLUTIONS_WIDTH + CRANK_REVOLUTION_DATA__LAST_CRANK_EVENT_TIME_WIDTH;

        }
    }
}
