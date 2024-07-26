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

package com.atrainingtracker.banalservice.devices.ant_plus;

import android.content.Context;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.BikePowerSensorsHelper;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.MyBanalDebugHelper;
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.ThresholdSensor;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.CalculatedWheelDistanceReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.CalculatedWheelSpeedReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.DataSource;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.ICalculatedCrankCadenceReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.ICalculatedPowerReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.ICalculatedTorqueReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.IInstantaneousCadenceReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.IPedalPowerBalanceReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.IPedalSmoothnessReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.ITorqueEffectivenessReceiver;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

import java.math.BigDecimal;
import java.util.EnumSet;

/**
 * Bike Power ManagedSensor MyANTDevice
 */
public class ANTBikePowerDevice extends MyANTDevice {
    protected static final int SPEED_THRESHOLD = 8;
    protected static final int POWER_THRESHOLD = 8;
    protected static final int TORQUE_THRESHOLD = 8;
    private static final boolean DEBUG = BANALService.getDebug(false);
    private static final MyBanalDebugHelper myDebugHelper = new MyBanalDebugHelper();
    protected MySensor<Integer> mPowerSensor;
    protected MySensor<Double> mTorqueSensor;
    protected MySensor<Double> mSpeedSensor;
    protected MySensor<Double> mPaceSensor;
    protected MyDoubleAccumulatorSensor mDistanceSensor;
    protected MyDoubleAccumulatorSensor mLapDistanceSensor;
    protected MySensor<Integer> mCadenceSensor;
    protected MySensor<Integer> mPowerBalanceSensor;
    protected MySensor<Integer> mPedalSmoothnessRightSensor;
    protected MySensor<Integer> mPedalSmoothnessLeftSensor;
    protected MySensor<Integer> mPedalSmoothnessSensor;
    protected MySensor<Integer> mTorqueEffectivenessRightSensor;
    protected MySensor<Integer> mTorqueEffectivenessLeftSensor;
    protected boolean mInvertPowerBalanceValues;
    AntPlusBikePowerPcc bikePowerPcc = null;
    private final String TAG = "ANTBikePowerDevice";

    /**
     * constructor
     **/
    public ANTBikePowerDevice(Context context, MySensorManager mySensorManager, long deviceID, int antDeviceNumber) {
        super(context, mySensorManager, DeviceType.BIKE_POWER, deviceID, antDeviceNumber);

        int sensorFlags = DevicesDatabaseManager.getBikePowerSensorFlags(getDeviceId());
        mInvertPowerBalanceValues = BikePowerSensorsHelper.invertPowerBalanceValues(sensorFlags);
    }

    @Override
    protected void addSensors() {
        mPowerSensor = new ThresholdSensor<>(this, SensorType.POWER, POWER_THRESHOLD);
        addSensor(mPowerSensor);

        // all other sensors are added and registered whenever, they appear ...
        // the ANT+ Plugin implementation really really sucks!
    }

    @Override
    protected void newLap() {
        if (mLapDistanceSensor != null) {
            mLapDistanceSensor.reset();
        }
    }

    @Override
    protected PccReleaseHandle requestAccess() {
        return AntPlusBikePowerPcc.requestAccess(mContext, getANTDeviceNumber(), 0, new MyResultReceiver<AntPlusBikePowerPcc>(), new MyDeviceStateChangeReceiver());
    }


    @Override
    protected void setSpecificPcc(AntPluginPcc antPluginPcc) {
        bikePowerPcc = (AntPlusBikePowerPcc) antPluginPcc;
    }

    private void createCadenceSensor() {
        // first, update the database
        int sensorFlags = DevicesDatabaseManager.getBikePowerSensorFlags(getDeviceId());
        sensorFlags = BikePowerSensorsHelper.addCrankRevolutionDataFlag(sensorFlags);
        DevicesDatabaseManager.putBikePowerSensorFlags(getDeviceId(), sensorFlags);

        // then, create, add and register the cadence sensor
        mCadenceSensor = new MySensor<>(ANTBikePowerDevice.this, SensorType.CADENCE);
        addAndRegisterSensor(mCadenceSensor);
    }

    private void createTorqueSensor() {
        // first, update the database
        int sensorFlags = DevicesDatabaseManager.getBikePowerSensorFlags(getDeviceId());
        sensorFlags = BikePowerSensorsHelper.addTorqueDataFlag(sensorFlags);
        DevicesDatabaseManager.putBikePowerSensorFlags(getDeviceId(), sensorFlags);

        // then, create, add and register the torque sensor
        mTorqueSensor = new ThresholdSensor<>(this, SensorType.TORQUE, TORQUE_THRESHOLD);
        addAndRegisterSensor(mTorqueSensor);
    }

    private void createWheelSpeedSensors() {
        // first, update the database
        int sensorFlags = DevicesDatabaseManager.getBikePowerSensorFlags(getDeviceId());
        sensorFlags = BikePowerSensorsHelper.addWheelSpeedDataFlag(sensorFlags);
        DevicesDatabaseManager.putBikePowerSensorFlags(getDeviceId(), sensorFlags);

        // then, create, add and register the speed and pace sensor
        mSpeedSensor = new ThresholdSensor<>(this, SensorType.SPEED_mps, SPEED_THRESHOLD);
        mPaceSensor = new ThresholdSensor<>(this, SensorType.PACE_spm, SPEED_THRESHOLD);
        addAndRegisterSensor(mSpeedSensor);
        addAndRegisterSensor(mPaceSensor);
    }

    private void createWheelDistanceSensors() {
        // first, update the database
        int sensorFlags = DevicesDatabaseManager.getBikePowerSensorFlags(getDeviceId());
        sensorFlags = BikePowerSensorsHelper.addWheelDistanceDataFlag(sensorFlags);
        DevicesDatabaseManager.putBikePowerSensorFlags(getDeviceId(), sensorFlags);

        // then, create, add and register the distance sensors
        mDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m);
        mLapDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m_LAP);

        addAndRegisterSensor(mDistanceSensor);
        addAndRegisterSensor(mLapDistanceSensor);
    }

    private void createPowerBalanceSensor() {
        // first, update the database
        int sensorFlags = DevicesDatabaseManager.getBikePowerSensorFlags(getDeviceId());
        sensorFlags = BikePowerSensorsHelper.addPowerBalanceFlag(sensorFlags);
        DevicesDatabaseManager.putBikePowerSensorFlags(getDeviceId(), sensorFlags);

        // then, create, add and register the power balance sensor
        mPowerBalanceSensor = new MySensor<>(this, SensorType.PEDAL_POWER_BALANCE);
        addAndRegisterSensor(mPowerBalanceSensor);
    }

    private void createSeparatePedalSmoothnessSensors() {
        // first, update the database
        int sensorFlags = DevicesDatabaseManager.getBikePowerSensorFlags(getDeviceId());
        sensorFlags = BikePowerSensorsHelper.addPedalSmoothnessFlag(sensorFlags);
        DevicesDatabaseManager.putBikePowerSensorFlags(getDeviceId(), sensorFlags);

        // then, create, add and register the pedal smoothness sensors
        mPedalSmoothnessRightSensor = new MySensor<>(this, SensorType.PEDAL_SMOOTHNESS_R);
        mPedalSmoothnessLeftSensor = new MySensor<>(this, SensorType.PEDAL_SMOOTHNESS_L);

        addAndRegisterSensor(mPedalSmoothnessRightSensor);
        addAndRegisterSensor(mPedalSmoothnessLeftSensor);
    }

    private void createCombinedPedalSmoothnessSensors() {
        // first, update the database
        int sensorFlags = DevicesDatabaseManager.getBikePowerSensorFlags(getDeviceId());
        sensorFlags = BikePowerSensorsHelper.addPedalSmoothnessFlag(sensorFlags);
        DevicesDatabaseManager.putBikePowerSensorFlags(getDeviceId(), sensorFlags);

        // then, create, add and register the pedal smoothness sensors
        mPedalSmoothnessSensor = new MySensor<>(this, SensorType.PEDAL_SMOOTHNESS);

        addAndRegisterSensor(mPedalSmoothnessSensor);
    }

    private void createTorqueEffectivenessSensors() {
        // first, update the database
        int sensorFlags = DevicesDatabaseManager.getBikePowerSensorFlags(getDeviceId());
        sensorFlags = BikePowerSensorsHelper.addTorqueEffectivenessFlag(sensorFlags);
        DevicesDatabaseManager.putBikePowerSensorFlags(getDeviceId(), sensorFlags);

        // then, create, add and register the torque effectiveness sensors
        mTorqueEffectivenessRightSensor = new MySensor<>(this, SensorType.TORQUE_EFFECTIVENESS_R);
        mTorqueEffectivenessLeftSensor = new MySensor<>(this, SensorType.TORQUE_EFFECTIVENESS_L);

        addAndRegisterSensor(mTorqueEffectivenessRightSensor);
        addAndRegisterSensor(mTorqueEffectivenessLeftSensor);
    }


    @Override
    protected void subscribeSpecificEvents() {
        if (bikePowerPcc != null) {

            bikePowerPcc.subscribeCalculatedPowerEvent(new ICalculatedPowerReceiver() {

                @Override
                public void onNewCalculatedPower(long estTimestamp, EnumSet<EventFlag> eventFlags, AntPlusBikePowerPcc.DataSource dataSource, BigDecimal calculatedPower) {
                    mPowerSensor.newValue(calculatedPower.intValue());
                }
            });


            // cadence stuff
            bikePowerPcc.subscribeCalculatedCrankCadenceEvent(new ICalculatedCrankCadenceReceiver() {

                @Override
                public void onNewCalculatedCrankCadence(long estTimestamp, EnumSet<EventFlag> eventFlags, DataSource dataSource, BigDecimal calculatedCrankCadence) {
                    if (DEBUG) Log.d(TAG, "new calculatedCrankCadence: " + calculatedCrankCadence);

                    if (mCadenceSensor == null) {  // first time, we get a cadence value ...
                        createCadenceSensor();
                    }

                    mCadenceSensor.newValue(calculatedCrankCadence.intValue());
                }
            });

            bikePowerPcc.subscribeInstantaneousCadenceEvent(new IInstantaneousCadenceReceiver() {

                @Override
                public void onNewInstantaneousCadence(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, AntPlusBikePowerPcc.DataSource dataSource, int instantaneousCadence) {
                    if (DEBUG) Log.d(TAG, "new instantaneousCadence: " + instantaneousCadence);

                    if (mCadenceSensor == null) {  // first time, we get a cadence value ...
                        createCadenceSensor();
                    }

                    if ((instantaneousCadence <= -1) || (instantaneousCadence > 255)) { // invalid data
                        if (DEBUG) Log.d(TAG, "invalid cadence: " + instantaneousCadence);
                        mCadenceSensor.newValue(null);
                    } else {
                        mCadenceSensor.newValue(instantaneousCadence);
                    }

                }

            });

            bikePowerPcc.subscribeCalculatedTorqueEvent(new ICalculatedTorqueReceiver() {

                @Override
                public void onNewCalculatedTorque(long estTimestamp, EnumSet<EventFlag> eventFlags, AntPlusBikePowerPcc.DataSource dataSource, BigDecimal calculatedTorque) {
                    if (mTorqueSensor == null) {
                        createTorqueSensor();
                    }

                    mTorqueSensor.newValue(calculatedTorque.doubleValue());
                }
            });

            bikePowerPcc.subscribeCalculatedWheelDistanceEvent(new CalculatedWheelDistanceReceiver(new BigDecimal(mCalibrationFactor)) {

                @Override
                public void onNewCalculatedWheelDistance(long estTimestamp, EnumSet<EventFlag> eventFlags, AntPlusBikePowerPcc.DataSource dataSource, BigDecimal calculatedWheelDistance) {
                    if (mDistanceSensor == null) {
                        createWheelDistanceSensors();
                    }

                    if (calculatedWheelDistance != null) {
                        mDistanceSensor.newValue(calculatedWheelDistance.doubleValue());
                        mLapDistanceSensor.newValue(calculatedWheelDistance.doubleValue());
                    } else {
                        mDistanceSensor.newValue(0.0);
                        mLapDistanceSensor.newValue(0.0);
                    }
                }
            });

            if (DEBUG)
                Log.i(TAG, "subscribing calculatedWheelSpeed with calibrationFactor=" + mCalibrationFactor);
            bikePowerPcc.subscribeCalculatedWheelSpeedEvent(new CalculatedWheelSpeedReceiver(new BigDecimal(mCalibrationFactor)) {

                @Override
                public void onNewCalculatedWheelSpeed(long estTimestamp, EnumSet<EventFlag> eventFlags, AntPlusBikePowerPcc.DataSource dataSource, BigDecimal calculatedWheelSpeed) {
                    if (DEBUG) Log.i(TAG, "got new speed: " + calculatedWheelSpeed);

                    if (mSpeedSensor == null) {
                        createWheelSpeedSensors();
                    }

                    double speed_mps = calculatedWheelSpeed.doubleValue() / 3.6;

                    mSpeedSensor.newValue(speed_mps);
                    if (calculatedWheelSpeed != null) {
                        mPaceSensor.newValue(1 / speed_mps);
                    }
                }
            });

            bikePowerPcc.subscribePedalPowerBalanceEvent(new IPedalPowerBalanceReceiver() {
                @Override
                public void onNewPedalPowerBalance(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, boolean rightPedalIndicator, int pedalPowerPercentage) {
                    if (mPowerBalanceSensor == null
                            && pedalPowerPercentage >= 0) {
                        createPowerBalanceSensor();
                    }

                    if (mPowerBalanceSensor != null) {
                        if (pedalPowerPercentage >= 0) {
                            if (mInvertPowerBalanceValues) {
                                pedalPowerPercentage = 100 - pedalPowerPercentage;
                            }

                            mPowerBalanceSensor.newValue(pedalPowerPercentage);
                        } else {
                            mPowerBalanceSensor.newValue(null);
                        }
                    }
                }
            });

            bikePowerPcc.subscribePedalSmoothnessEvent(new IPedalSmoothnessReceiver() {
                @Override
                public void onNewPedalSmoothness(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, long powerOnlyUpdateEventCount, boolean separatePedalSmoothnessSupport, java.math.BigDecimal leftOrCombinedPedalSmoothness, java.math.BigDecimal rightPedalSmoothness) {
                    if (separatePedalSmoothnessSupport) {

                        if (mPedalSmoothnessLeftSensor == null
                                && leftOrCombinedPedalSmoothness.intValue() != -1
                                && rightPedalSmoothness.intValue() != -1) {  // sensor not yet added and we get a valid value
                            createSeparatePedalSmoothnessSensors();
                        }

                        if (mPedalSmoothnessLeftSensor != null) {
                            mPedalSmoothnessLeftSensor.newValue(leftOrCombinedPedalSmoothness.intValue() != -1 ? leftOrCombinedPedalSmoothness.intValue() : null);
                            mPedalSmoothnessRightSensor.newValue(rightPedalSmoothness.intValue() != -1 ? rightPedalSmoothness.intValue() : null);
                        }
                    } else {
                        if (mPedalSmoothnessSensor == null
                                && leftOrCombinedPedalSmoothness.intValue() != -1) {  // sensor not yet added and we get a valid value
                            createCombinedPedalSmoothnessSensors();
                        }

                        if (mPedalSmoothnessLeftSensor != null) {
                            mPedalSmoothnessSensor.newValue(leftOrCombinedPedalSmoothness.intValue() != -1 ? leftOrCombinedPedalSmoothness.intValue() : null);
                        }
                    }


                }
            });

            bikePowerPcc.subscribeTorqueEffectivenessEvent(new ITorqueEffectivenessReceiver() {

                @Override
                public void onNewTorqueEffectiveness(long estTimestamp,
                                                     java.util.EnumSet<EventFlag> eventFlags,
                                                     long powerOnlyUpdateEventCount,
                                                     java.math.BigDecimal leftTorqueEffectiveness,
                                                     java.math.BigDecimal rightTorqueEffectiveness) {
                    if (mTorqueEffectivenessLeftSensor == null
                            && leftTorqueEffectiveness.intValue() != -1
                            && rightTorqueEffectiveness.intValue() != -1) {
                        createTorqueEffectivenessSensors();
                    }

                    if (mTorqueEffectivenessLeftSensor != null) {
                        mTorqueEffectivenessLeftSensor.newValue(leftTorqueEffectiveness.intValue() != -1 ? leftTorqueEffectiveness.intValue() : null);
                        mTorqueEffectivenessRightSensor.newValue(rightTorqueEffectiveness.intValue() != -1 ? rightTorqueEffectiveness.intValue() : null);
                    }
                }

            });
        }
    }


    @Override
    protected void subscribeCommonEvents() {
        onNewCommonPccFound(bikePowerPcc);
    }
}
