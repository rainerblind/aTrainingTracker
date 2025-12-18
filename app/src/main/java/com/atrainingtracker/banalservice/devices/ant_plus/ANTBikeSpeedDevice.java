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
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.ThresholdSensor;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc.CalculatedAccumulatedDistanceReceiver;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc.CalculatedSpeedReceiver;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

import java.math.BigDecimal;
import java.util.EnumSet;

/**
 * Bike Speed
 */
public class ANTBikeSpeedDevice extends MyANTDevice {
    protected static final int SPEED_THRESHOLD = 8;
    private static final String TAG = "ANTBikeSpeedDevice";
    private static final boolean DEBUG = BANALService.getDebug(false);


    // everything is stored as int, so we do not have to take care about all the castings
    // protected int mEventTime;
    // protected int mRevolutionCount;

    // protected int mAccumulatedRevolutionCount;
    protected AntPlusBikeSpeedDistancePcc bikeSpeedDistancePcc = null;
    protected MySensor<BigDecimal> mSpeedSensor;
    protected MySensor<Double> mPaceSensor;
    protected MyDoubleAccumulatorSensor mDistanceSensor;
    protected MyDoubleAccumulatorSensor mLapDistanceSensor;

    // protected int mNrZeroSpeed;
    // protected static final int NR_ZERO_SPEED_THRESHOLD = 8; 

    /**
     * constructor
     */
    public ANTBikeSpeedDevice(Context context, MySensorManager mySensorManager, long deviceID, int antDeviceNumber) {
        super(context, mySensorManager, DeviceType.BIKE_SPEED, deviceID, antDeviceNumber);
        myLog("created ANTBikeSpeedDevice");
    }

    @Override
    protected void addSensors() {
        mSpeedSensor = new ThresholdSensor<BigDecimal>(this, SensorType.SPEED_mps, SPEED_THRESHOLD);
        mPaceSensor = new ThresholdSensor<Double>(this, SensorType.PACE_spm, SPEED_THRESHOLD);
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

    @Override
    protected PccReleaseHandle requestAccess() {
        myLog("requestAccess()");

        return AntPlusBikeSpeedDistancePcc.requestAccess(mContext, getANTDeviceNumber(), 0, false, new MyResultReceiver<AntPlusBikeSpeedDistancePcc>(), new MyDeviceStateChangeReceiver());
    }


    @Override
    protected void setSpecificPcc(AntPluginPcc antPluginPcc) {
        bikeSpeedDistancePcc = (AntPlusBikeSpeedDistancePcc) antPluginPcc;
    }


    @Override
    protected void subscribeSpecificEvents() {
        if (bikeSpeedDistancePcc != null) {
            myLog("subscribing specific events");

            bikeSpeedDistancePcc.subscribeCalculatedSpeedEvent(new CalculatedSpeedReceiver(new BigDecimal(mCalibrationFactor)) {
                @Override
                public void onNewCalculatedSpeed(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal calculatedSpeed) {
                    myLog("onNewCalculatedSpeed:timeStamp=" + estTimestamp + ": " + calculatedSpeed.doubleValue());

                    mSpeedSensor.newValue(calculatedSpeed);
                    mPaceSensor.newValue(1 / calculatedSpeed.doubleValue());   // TODO: correct?
                }
            });

            bikeSpeedDistancePcc.subscribeCalculatedAccumulatedDistanceEvent(new CalculatedAccumulatedDistanceReceiver(new BigDecimal(mCalibrationFactor)) {
                @Override
                public void onNewCalculatedAccumulatedDistance(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal calculatedAccumulatedDistance) {
                    if (calculatedAccumulatedDistance != null) {
                        mDistanceSensor.newValue(calculatedAccumulatedDistance.doubleValue());
                        mLapDistanceSensor.newValue(calculatedAccumulatedDistance.doubleValue());
                    } else {
                        mDistanceSensor.newValue(0.0);
                        mLapDistanceSensor.newValue(0.0);

                    }
                }
            });
        }
    }

    @Override
    protected void subscribeCommonEvents() {
        onNewBikeSpdCadCommonPccFound(bikeSpeedDistancePcc);
    }

}
