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
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeCadencePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeCadencePcc.ICalculatedCadenceReceiver;
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
public class ANTBikeSpeedAndCadenceDevice extends MyANTDevice {
    protected static final int SPEED_THRESHOLD = 8;
    protected static final int CADENCE_THRESHOLD = 8;
    private static final String TAG = "ANTBikeSpeedAndCadenceD";
    private static final boolean DEBUG = BANALService.getDebug(false);
    protected AntPlusBikeSpeedDistancePcc bikeSpeedDistancePcc = null;
    protected AntPlusBikeCadencePcc cadencePcc = null;
    protected PccReleaseHandle pccReleaseHandle2 = null;
    protected MySensor<BigDecimal> mSpeedSensor;
    protected MySensor<Double> mPaceSensor;
    protected MyDoubleAccumulatorSensor mDistanceSensor;
    protected MyDoubleAccumulatorSensor mLapDistanceSensor;
    protected MySensor<BigDecimal> mCadenceSensor;

    /**
     * constructor
     */
    public ANTBikeSpeedAndCadenceDevice(Context context, MySensorManager mySensorManager, long deviceID, int antDeviceNumber) {
        super(context, mySensorManager, DeviceType.BIKE_SPEED_AND_CADENCE, deviceID, antDeviceNumber);
        myLog("created ANTBikeSpeedAndCadenceDevice");
    }

    @Override
    protected void addSensors() {
        mSpeedSensor = new ThresholdSensor<BigDecimal>(this, SensorType.SPEED_mps, SPEED_THRESHOLD);
        mPaceSensor = new ThresholdSensor<Double>(this, SensorType.PACE_spm, SPEED_THRESHOLD);
        mDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m, false);
        mLapDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m_LAP, false);
        mCadenceSensor = new ThresholdSensor<BigDecimal>(this, SensorType.CADENCE, CADENCE_THRESHOLD);

        addSensor(mSpeedSensor);
        addSensor(mPaceSensor);
        addSensor(mDistanceSensor);
        addSensor(mLapDistanceSensor);
        addSensor(mCadenceSensor);
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

        // we start with searching for the speed part.  Searching for the cadence part will be started when the device is found.
        return AntPlusBikeSpeedDistancePcc.requestAccess(mContext, getANTDeviceNumber(), 0, true, new MyResultReceiver<AntPlusBikeSpeedDistancePcc>(), new MyDeviceStateChangeReceiver());
    }

    @Override
    protected void releaseAccess() {
        if (pccReleaseHandle2 != null) {
            pccReleaseHandle2.close();
            super.releaseAccess();
        }

    }


    @Override
    protected void setSpecificPcc(AntPluginPcc antPluginPcc) {
        if (antPluginPcc.getClass() == AntPlusBikeSpeedDistancePcc.class) {
            bikeSpeedDistancePcc = (AntPlusBikeSpeedDistancePcc) antPluginPcc;

            // also search for the cadence part
            // TODO is this the right place???
            myLog("before AntPlusBikeCadencePcc.requestAccess()");
            pccReleaseHandle2 = AntPlusBikeCadencePcc.requestAccess(mContext, getANTDeviceNumber(), 0, true, new MyResultReceiver<AntPlusBikeCadencePcc>(), new MyDeviceStateChangeReceiver());
        } else if (antPluginPcc.getClass() == AntPlusBikeCadencePcc.class) {
            cadencePcc = (AntPlusBikeCadencePcc) antPluginPcc;
        }
    }

    @Override
    protected void subscribeSpecificEvents() {
        if (bikeSpeedDistancePcc != null) {
            // TODO: might be called a second time, when the cadencePcc is found.
            // is this a problem???
            myLog("before subscribeCalculatedSpeedEvent");
            bikeSpeedDistancePcc.subscribeCalculatedSpeedEvent(new CalculatedSpeedReceiver(new BigDecimal(mCalibrationFactor)) {

                @Override
                public void onNewCalculatedSpeed(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal calculatedSpeed) {
                    mSpeedSensor.newValue(calculatedSpeed);
                    mPaceSensor.newValue(1 / calculatedSpeed.doubleValue());   // TODO: correct?
                }
            });

            myLog("before subscribeCalculatedAccumulatedDistanceEvent");
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

        if (cadencePcc != null) {
            myLog("before subscribeCalculatedCadenceEvent");
            cadencePcc.subscribeCalculatedCadenceEvent(new ICalculatedCadenceReceiver() {
                @Override
                public void onNewCalculatedCadence(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal calculatedCadence) {
                    mCadenceSensor.newValue(calculatedCadence);
                }
            });
        }
    }

    @Override
    protected void subscribeCommonEvents() {
        // according to an answer to my question in the ANT+ forum, this is not possible for this kind of device.
        // onNewLegacyCommonPccFound(bikeSpeedDistancePcc);
    }


//    @Override
//    protected void onNewPluginPccFound(AntPluginPcc antPluginPcc) 
//    {
//    	myLog("onNewPluginPccFound()");
//    	
//    	myLog("AntPluginPcc class name: " + antPluginPcc.getClass().getName());
//    	if (antPluginPcc.getClass() == AntPlusBikeSpeedDistancePcc.class) {        	
//    		bikeSpeedDistancePcc = (AntPlusBikeSpeedDistancePcc) antPluginPcc;
//
//    		myLog("before subscribeCalculatedSpeedEvent");
//    		bikeSpeedDistancePcc.subscribeCalculatedSpeedEvent(new CalculatedSpeedReceiver(mCalibrationFactor)
//    		{
//
//    			@Override
//    			public void onNewCalculatedSpeed(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal calculatedSpeed) 
//    			{
//    				mSpeedSensor.newValue(calculatedSpeed);
//    				mPaceSensor.newValue(1/calculatedSpeed.doubleValue());   // TODO: correct?				
//    			}	
//    		});
//
//    		myLog("before subscribeCalculatedAccumulatedDistanceEvent");
//    		bikeSpeedDistancePcc.subscribeCalculatedAccumulatedDistanceEvent(new CalculatedAccumulatedDistanceReceiver(mCalibrationFactor)
//    		{
//
//    			@Override
//    			public void onNewCalculatedAccumulatedDistance(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal calculatedAccumulatedDistance) 
//    			{
//    				mDistanceSensor.newValue(calculatedAccumulatedDistance.subtract(mInitialDistance));
//    				mLapDistanceSensor.newValue(calculatedAccumulatedDistance.subtract(mInitialLapDistance));
//    			}
//
//    		});
//    		
//    		// finally, we search for the cadence part
//    		myLog("before AntPlusBikeCadencePcc.requestAccess()");
//    		AntPlusBikeCadencePcc.requestAccess(mContext, mDeviceID.getDeviceNumber(), 0, true, new MyResultReceiver<AntPlusBikeCadencePcc>(), new MyDeviceStateChangeReceiver());
//    		
//    	}
//    	else if (antPluginPcc.getClass() == AntPlusBikeCadencePcc.class) {
//    		cadencePcc = (AntPlusBikeCadencePcc) antPluginPcc;
//        	
//        	myLog("before subscribeCalculatedCadenceEvent");
//        	cadencePcc.subscribeCalculatedCadenceEvent(new ICalculatedCadenceReceiver()
//        	{
//    			@Override
//    			public void onNewCalculatedCadence(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal calculatedCadence) 
//    			{
//    				mCadenceSensor.newValue(calculatedCadence);
//    			}	
//        	});
//    	}
//
//    	// finally, subscribe to legacy common Pcc stuff
//    	// according to an answer to my question in the ANT+ forum, this is not possible for this kind of device.
//    	// onNewLegacyCommonPccFound(bikeSpeedDistancePcc); 
//    }

}
