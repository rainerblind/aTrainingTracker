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

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MyIntegerAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.dsi.ant.plugins.antplus.pcc.AntPlusStrideSdmPcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusStrideSdmPcc.ICalorieDataReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusStrideSdmPcc.IDistanceReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusStrideSdmPcc.IInstantaneousCadenceReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusStrideSdmPcc.IInstantaneousSpeedReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusStrideSdmPcc.IStrideCountReceiver;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

import java.math.BigDecimal;

/**
 * Stride Based Speed and Distance MyANTDevice
 */
public class ANTRunSpeedDevice extends MyANTDevice {
    private static final String TAG = "ANTRunSpeedDevice";
    private static final boolean DEBUG = BANALService.getDebug(false);
    protected MySensor<BigDecimal> mCadenceSensor;
    protected MySensor<Double> mSpeedSensor;
    protected MySensor<Double> mPaceSensor;
    protected MyDoubleAccumulatorSensor mDistanceSensor;
    protected MyDoubleAccumulatorSensor mLapDistanceSensor;
    protected MyIntegerAccumulatorSensor mStridesSensor;
    protected MyIntegerAccumulatorSensor mCaloriesSensor;
    private AntPlusStrideSdmPcc mStrideSdmPcc = null;


    /**
     * constructor
     */
    public ANTRunSpeedDevice(Context context, MySensorManager mySensorManager, long deviceID, int antDeviceNumber) {
        super(context, mySensorManager, DeviceType.RUN_SPEED, deviceID, antDeviceNumber);
    }

    @Override
    protected void addSensors() {
        mCadenceSensor = new MySensor<BigDecimal>(this, SensorType.CADENCE);
        mSpeedSensor = new MySensor<Double>(this, SensorType.SPEED_mps);
        mPaceSensor = new MySensor<Double>(this, SensorType.PACE_spm);
        mDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m);
        mLapDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m_LAP);
        mStridesSensor = new MyIntegerAccumulatorSensor(this, SensorType.STRIDES);
        mCaloriesSensor = new MyIntegerAccumulatorSensor(this, SensorType.CALORIES);

        addSensor(mCadenceSensor);
        addSensor(mSpeedSensor);
        addSensor(mPaceSensor);
        addSensor(mDistanceSensor);
        addSensor(mLapDistanceSensor);
        addSensor(mStridesSensor);
        addSensor(mCaloriesSensor);
    }

    @Override
    protected void newLap() {
        if (mLapDistanceSensor != null) {
            mLapDistanceSensor.reset();
        }
    }

    @Override
    protected PccReleaseHandle requestAccess() {
        return AntPlusStrideSdmPcc.requestAccess(mContext, getANTDeviceNumber(), 0, new MyResultReceiver<AntPlusStrideSdmPcc>(), new MyDeviceStateChangeReceiver());
    }


    @Override
    protected void setSpecificPcc(AntPluginPcc antPluginPcc) {
        mStrideSdmPcc = (AntPlusStrideSdmPcc) antPluginPcc;
    }


    @Override
    protected void subscribeSpecificEvents() {
        if (mStrideSdmPcc != null) {
            mStrideSdmPcc.subscribeCalorieDataEvent(new ICalorieDataReceiver() {

                @Override
                public void onNewCalorieData(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, long cumulativeCalories) {
                    mCaloriesSensor.newValue((int) cumulativeCalories);
                }
            });

            mStrideSdmPcc.subscribeDistanceEvent(new IDistanceReceiver() {
                @Override
                public void onNewDistance(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, java.math.BigDecimal cumulativeDistance) {
                    if (cumulativeDistance != null) {
                        mDistanceSensor.newValue(mCalibrationFactor * cumulativeDistance.doubleValue());
                        mLapDistanceSensor.newValue(mCalibrationFactor * cumulativeDistance.doubleValue());
                    } else {
                        mDistanceSensor.newValue(0.0);
                        mLapDistanceSensor.newValue(0.0);
                    }
                }
            });

            mStrideSdmPcc.subscribeInstantaneousCadenceEvent(new IInstantaneousCadenceReceiver() {
                @Override
                public void onNewInstantaneousCadence(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, java.math.BigDecimal instantaneousCadence) {
                    mCadenceSensor.newValue(instantaneousCadence);
                }

            });

            mStrideSdmPcc.subscribeInstantaneousSpeedEvent(new IInstantaneousSpeedReceiver() {
                @Override
                public void onNewInstantaneousSpeed(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, java.math.BigDecimal instantaneousSpeed) {
                    if (instantaneousSpeed != null) {
                        double speed = mCalibrationFactor * instantaneousSpeed.doubleValue();
                        mSpeedSensor.newValue(speed);
                        mPaceSensor.newValue(1 / speed);
                    } else {
                        mSpeedSensor.newValue(0.0);
                        mPaceSensor.newValue(null);
                    }
                }
            });

            mStrideSdmPcc.subscribeStrideCountEvent(new IStrideCountReceiver() {
                @Override
                public void onNewStrideCount(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, long cumulativeStrides) {
                    mStridesSensor.newValue((int) cumulativeStrides);
                }
            });
        }
    }

    @Override
    protected void subscribeCommonEvents() {
        onNewCommonPccFound(mStrideSdmPcc);
    }

}
