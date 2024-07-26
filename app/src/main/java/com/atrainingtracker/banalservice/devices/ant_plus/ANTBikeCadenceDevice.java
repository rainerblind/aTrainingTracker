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
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.ThresholdSensor;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeCadencePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeCadencePcc.ICalculatedCadenceReceiver;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

import java.math.BigDecimal;
import java.util.EnumSet;

/**
 * A Bike Cadence ManagedSensor
 */
public class ANTBikeCadenceDevice extends MyANTDevice {
    protected static final int CADENCE_THRESHOLD = 8;
    private static final String TAG = "ANTBikeCadenceDevice";
    private static final boolean DEBUG = BANALService.getDebug(false);

    // everything is stored as int, so we do not have to take care about all the castings
    protected AntPlusBikeCadencePcc cadencePcc = null;
    protected MySensor<BigDecimal> mCadenceSensor;

    /**
     * constructor with context channelNumber, and deviceID,
     */
    public ANTBikeCadenceDevice(Context context, MySensorManager mySensorManager, long deviceID, int antDeviceNumber) {
        super(context, mySensorManager, DeviceType.BIKE_CADENCE, deviceID, antDeviceNumber);
        myLog("created ANTBikeCadenceDevice");
    }


    @Override
    protected void addSensors() {
        mCadenceSensor = new ThresholdSensor<BigDecimal>(this, SensorType.CADENCE, CADENCE_THRESHOLD);

        addSensor(mCadenceSensor);
    }

    @Override
    protected PccReleaseHandle requestAccess() {
        myLog("requestAccess()");

        return AntPlusBikeCadencePcc.requestAccess(mContext, mAntDeviceNumber, 0, false, new MyResultReceiver<AntPlusBikeCadencePcc>(), new MyDeviceStateChangeReceiver());
    }


    @Override
    protected void setSpecificPcc(AntPluginPcc antPluginPcc) {
        cadencePcc = (AntPlusBikeCadencePcc) antPluginPcc;
    }


    @Override
    protected void subscribeSpecificEvents() {
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
        onNewBikeSpdCadCommonPccFound(cadencePcc);
    }

}
