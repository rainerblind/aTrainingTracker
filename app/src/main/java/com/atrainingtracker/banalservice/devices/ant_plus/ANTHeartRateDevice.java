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
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc.DataState;
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc.IHeartRateDataReceiver;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

public class ANTHeartRateDevice extends MyANTDevice {
    private static final boolean DEBUG = BANALService.DEBUG & false;
    protected AntPlusHeartRatePcc hrPcc = null;

    protected MySensor<Number> mHeartRateSensor;
    private String TAG = "ANTHeartRateDevice";


    /**
     * constructor
     **/
    public ANTHeartRateDevice(Context context, MySensorManager mySensorManager, long deviceID, int antDeviceNumber) {
        super(context, mySensorManager, DeviceType.HRM, deviceID, antDeviceNumber);
        if (DEBUG) {
            Log.d(TAG, "creating HR device");
        }
    }

    @Override
    protected void addSensors() {
        mHeartRateSensor = new MySensor<Number>(this, SensorType.HR);

        addSensor(mHeartRateSensor);
    }


    @Override
    protected PccReleaseHandle requestAccess() {
        if (DEBUG) {
            Log.d(TAG, "requestAccess(" + getANTDeviceNumber() + ")");
        }

        return AntPlusHeartRatePcc.requestAccess(mContext, getANTDeviceNumber(), 0, new MyResultReceiver<AntPlusHeartRatePcc>(), new MyDeviceStateChangeReceiver());
    }


    @Override
    protected void setSpecificPcc(AntPluginPcc antPluginPcc) {
        hrPcc = (AntPlusHeartRatePcc) antPluginPcc;
    }


    @Override
    protected void subscribeSpecificEvents() {
        if (hrPcc != null) {
            hrPcc.subscribeHeartRateDataEvent(new IHeartRateDataReceiver() {
                @Override
                public void onNewHeartRateData(long estTimestamp,
                                               java.util.EnumSet<EventFlag> eventFlags,
                                               int computedHeartRate,
                                               long heartBeatCount,
                                               java.math.BigDecimal heartBeatEventTime,
                                               AntPlusHeartRatePcc.DataState dataState) {
                    if (DEBUG) {
                        Log.d(TAG, "got new HR value: " + computedHeartRate);
                    }

                    if (dataState == DataState.LIVE_DATA) {
                        mHeartRateSensor.newValue(computedHeartRate);
                    } else if (dataState == DataState.ZERO_DETECTED) {
                        mHeartRateSensor.newValue(null);
                    }
                }

            });
        }
    }


    @Override
    protected void subscribeCommonEvents() {
        onNewLegacyCommonPccFound(hrPcc);
    }

}
