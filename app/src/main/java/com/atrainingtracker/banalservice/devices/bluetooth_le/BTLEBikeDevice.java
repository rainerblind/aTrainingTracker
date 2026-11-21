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
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;

abstract public class BTLEBikeDevice extends MyBTLEDevice {
    private static final int CSC_TIME_RESOLUTION_HZ = 1024;
    private static final boolean DEBUG = BANALService.getDebug(false);

    protected final CscRevolutionTracker mCscRevolutionTracker = new CscRevolutionTracker(CSC_TIME_RESOLUTION_HZ);
    protected MySensor<Double> mCadenceSensor;
    protected MySensor<Double> mSpeedSensor;
    protected MySensor<Double> mPaceSensor;
    protected MyDoubleAccumulatorSensor mDistanceSensor;
    protected MyDoubleAccumulatorSensor mLapDistanceSensor;
    private final String TAG = "BTLEBikeDevice";


    /**
     * constructor
     **/
    public BTLEBikeDevice(Context context, MySensorManager mySensorManager, DeviceType deviceType, long deviceID, String address) {
        super(context, mySensorManager, deviceType, deviceID, address);
    }

    protected void addCadenceSensor() {
        if (DEBUG) Log.i(TAG, "addCadenceSensor()");

        mCadenceSensor = new MySensor<Double>(this, SensorType.CADENCE);
        addSensor(mCadenceSensor);
    }

    protected void addSpeedAndDistanceSensors() {
        if (DEBUG) Log.i(TAG, "addSpeedAndDistanceSensors()");

        mSpeedSensor = new MySensor<Double>(this, SensorType.SPEED_mps);
        mPaceSensor = new MySensor<Double>(this, SensorType.PACE_spm);
        mDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m, true);
        mLapDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m_LAP, true);

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

    @Override
    protected void unregisterSensors() {
        super.unregisterSensors();
        mCscRevolutionTracker.reset();
    }

    @Override
    protected void measurementCharacteristicUpdate(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return;
        }
        byte[] value = characteristic.getValue();
        if (value == null || value.length < 1) {
            if (DEBUG) Log.w(TAG, "measurementCharacteristicUpdate: packet too short or null");
            return;
        }

        try {
            Integer flag = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            if (flag == null) {
                return;
            }

            boolean wheelRevolutionDataPresent = (flag & 0x01) == 0x01;
            boolean crankRevolutionDataPresent = (flag & 0x02) == 0x02;

            int crankDataOffset = 1;
            if (wheelRevolutionDataPresent) {
                if (DEBUG) Log.i(TAG, "wheelRevolutionDataPresent");

                crankDataOffset = 7;

                if (value.length >= 7) {
                    Integer cumulativeWheelRevolutionsInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 1);
                    Integer wheelEventTimeInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 5);

                    if (cumulativeWheelRevolutionsInt != null && wheelEventTimeInt != null) {
                        long cumulativeWheelRevolutions = cumulativeWheelRevolutionsInt.longValue() & 0xFFFFFFFFL;
                        long wheelEventTime = wheelEventTimeInt.longValue() & 0xFFFFL;

                        if (DEBUG)
                            Log.i(TAG, "revolutions: " + cumulativeWheelRevolutions + ", time: " + wheelEventTime);

                        if (mSpeedSensor != null) {  // necessary because there was a crash with this case in production.
                            applyWheelSample(
                                    mCscRevolutionTracker,
                                    cumulativeWheelRevolutions,
                                    wheelEventTime,
                                    mCalibrationFactor,
                                    mSpeedSensor,
                                    mPaceSensor,
                                    mDistanceSensor,
                                    mLapDistanceSensor,
                                    mMySensorManager,
                                    TAG,
                                    DEBUG);
                        }
                    }
                } else {
                    Log.w(TAG, "Wheel Revolution Data present in flags but packet truncated: length=" + value.length);
                    return;
                }
            }

            if (crankRevolutionDataPresent) {
                if (DEBUG) Log.i(TAG, "crankRevolutionDataPresent");

                if (value.length >= crankDataOffset + 4) {
                    Integer cumulativeCrankRevolutionsInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, crankDataOffset);
                    Integer crankEventTimeInt = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, crankDataOffset + 2);

                    if (cumulativeCrankRevolutionsInt != null && crankEventTimeInt != null) {
                        long cumulativeCrankRevolutions = cumulativeCrankRevolutionsInt.longValue() & 0xFFFFL;
                        long crankEventTime = crankEventTimeInt.longValue() & 0xFFFFL;

                        if (DEBUG)
                            Log.i(TAG, "revolutions: " + cumulativeCrankRevolutions + ", time: " + crankEventTime);

                        if (mCadenceSensor != null) {
                            applyCrankSample(
                                    mCscRevolutionTracker,
                                    cumulativeCrankRevolutions,
                                    crankEventTime,
                                    mCadenceSensor,
                                    TAG,
                                    DEBUG);
                        }
                    }
                } else {
                    Log.w(TAG, "Crank Revolution Data present in flags but packet truncated: length=" + value.length + ", offset=" + crankDataOffset);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing CSC characteristic notification", e);
        }
    }

    static void applyWheelSample(
            CscRevolutionTracker tracker,
            long cumulativeWheelRevolutions,
            long wheelEventTime,
            double calibrationFactor,
            MySensor<Double> speedSensor,
            MySensor<Double> paceSensor,
            MyDoubleAccumulatorSensor distanceSensor,
            MyDoubleAccumulatorSensor lapDistanceSensor,
            MySensorManager wheelNotificationTarget,
            String tag,
            boolean debug) {
        if (speedSensor == null) {
            return;
        }
        CscRevolutionTracker.WheelUpdate update = tracker.processWheel(
                cumulativeWheelRevolutions, wheelEventTime, calibrationFactor);
        switch (update.getStatus()) {
            case COMPUTED:
                if (debug) Log.i(tag, "got new speed: " + update.getSpeedMps());
                speedSensor.newValue(update.getSpeedMps());
                Double paceSpm = update.getPaceSpm();
                if (paceSpm != null && paceSensor != null) {
                    paceSensor.newValue(paceSpm);
                }
                if (debug) Log.i(tag, "got new distance: " + update.getDistanceMeters());
                if (distanceSensor != null) {
                    distanceSensor.accumulate(update.getDistanceMeters());
                }
                if (lapDistanceSensor != null) {
                    lapDistanceSensor.accumulate(update.getDistanceMeters());
                }
                if (wheelNotificationTarget != null) {
                    wheelNotificationTarget.notifyWheelRevolutions(
                            update.getRevDiff(), cumulativeWheelRevolutions, System.currentTimeMillis());
                }
                break;
            case IDENTICAL_TICK_ZEROED:
                if (debug) Log.i(tag, "identical wheel times => reset speed to zero");
                speedSensor.newValue(0.0);
                if (paceSensor != null) {
                    paceSensor.newValue(null);
                }
                break;
            case INITIAL_BASELINE:
            case NO_UPDATE:
                break;
        }
    }

    static void applyCrankSample(
            CscRevolutionTracker tracker,
            long cumulativeCrankRevolutions,
            long crankEventTime,
            MySensor<Double> cadenceSensor,
            String tag,
            boolean debug) {
        if (cadenceSensor == null) {
            return;
        }
        CscRevolutionTracker.CrankUpdate update = tracker.processCrank(
                cumulativeCrankRevolutions, crankEventTime);
        switch (update.getStatus()) {
            case COMPUTED:
                if (debug) Log.i(tag, "got new cadence: " + update.getCadenceRpm());
                cadenceSensor.newValue(update.getCadenceRpm());
                break;
            case IDENTICAL_TICK_ZEROED:
                if (debug) Log.i(tag, "identical crank times => reset cadence to zero");
                cadenceSensor.newValue(0.0);
                break;
            case IGNORED_OUT_OF_RANGE:
                Log.w(tag, "Discarded unplausible cadence value: " + update.getCadenceRpm()
                        + " rpm (revDiff=" + update.getRevDiff() + ", timeDiff=" + update.getTimeDiff() + ")");
                break;
            case INITIAL_BASELINE:
            case NO_UPDATE:
                break;
        }
    }
}
