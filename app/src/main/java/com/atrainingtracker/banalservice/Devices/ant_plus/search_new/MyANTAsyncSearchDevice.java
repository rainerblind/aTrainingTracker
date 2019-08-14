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

package com.atrainingtracker.banalservice.Devices.ant_plus.search_new;

import android.content.Context;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.DeviceType;
import com.atrainingtracker.banalservice.Devices.Manufacturer;
import com.atrainingtracker.banalservice.Devices.ant_plus.search_new.ANTSearchForNewDevicesEngineMultiDeviceSearch.IANTAsyncSearchEngineInterface;
import com.atrainingtracker.banalservice.helpers.BatteryStatusHelper;
import com.dsi.ant.plugins.antplus.pcc.defines.BatteryStatus;
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc.IDeviceStateChangeReceiver;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc.IPluginAccessResultReceiver;
import com.dsi.ant.plugins.antplus.pccbase.AntPlusBikeSpdCadCommonPcc;
import com.dsi.ant.plugins.antplus.pccbase.AntPlusCommonPcc;
import com.dsi.ant.plugins.antplus.pccbase.AntPlusCommonPcc.IBatteryStatusReceiver;
import com.dsi.ant.plugins.antplus.pccbase.AntPlusCommonPcc.IManufacturerIdentificationReceiver;
import com.dsi.ant.plugins.antplus.pccbase.AntPlusLegacyCommonPcc;
import com.dsi.ant.plugins.antplus.pccbase.AntPlusLegacyCommonPcc.IManufacturerAndSerialReceiver;
import com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

import java.math.BigDecimal;
import java.util.EnumSet;

//import de.rainerblind.MyAntPlusApp;

// TODO: where/when to register and unregister the sensors

public abstract class MyANTAsyncSearchDevice {
    private static final boolean DEBUG = BANALService.DEBUG & false;
    /**
     * The state of the device/channel
     **/
    protected DeviceState mDeviceState = null;  // TODO: rename to mANTDeviceState
    protected Context mContext;
    protected MultiDeviceSearchResult mDeviceFound;
    private String TAG = "MyANTAsyncSearchDevice";
    private AntPlusLegacyCommonPcc mLegacyCommonPcc = null;
    private AntPlusCommonPcc mCommonPcc = null;
    private PccReleaseHandle pccReleaseHandle = null;
    private IANTAsyncSearchEngineInterface mCallback;
    private DeviceType mDeviceType;
    private int mBatteryPercentage = -1;
    private String mManufacturer = null;
    private boolean mPairingRecommendation;


    /**
     * constructor
     */
    public MyANTAsyncSearchDevice(Context context, IANTAsyncSearchEngineInterface callback, DeviceType deviceType, MultiDeviceSearchResult deviceFound, boolean pairingRecommendation) {
        if (DEBUG) Log.i(TAG, "creating new ANTAsyncSearchDevice: " + deviceType.name());

        mContext = context;
        mCallback = callback;
        mDeviceType = deviceType;
        mDeviceFound = deviceFound;
        mPairingRecommendation = pairingRecommendation;

        pccReleaseHandle = requestAccess();
    }

    private void notifySomethingNew() {
        mCallback.onNewDeviceFound(mDeviceType, mDeviceFound, mPairingRecommendation, mManufacturer, mBatteryPercentage);
    }

    private void gotManufacturer(String manufacturer) {
        if (DEBUG) Log.i(TAG, "gotManufacturer: " + manufacturer);

        mManufacturer = manufacturer;

        notifySomethingNew();
    }

    private void gotBatteryPercentage(int batteryPercentage) {
        if (DEBUG) Log.i(TAG, "gotBatteryPercentage: " + batteryPercentage);

        mBatteryPercentage = batteryPercentage;

        notifySomethingNew();
    }

    protected void onNewLegacyCommonPccFound(final AntPlusLegacyCommonPcc legacyCommonPcc) {
        if (DEBUG) Log.i(TAG, "onNewLegacyCommonPccFound() " + legacyCommonPcc.getDeviceName());

        mLegacyCommonPcc = legacyCommonPcc;

        legacyCommonPcc.subscribeManufacturerAndSerialEvent(new IManufacturerAndSerialReceiver() {

            @Override
            public void onNewManufacturerAndSerial(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, int manufacturerID, int serialNumber) {
                gotManufacturer(Manufacturer.getName(manufacturerID));
            }
        });
    }

    protected void onNewBikeSpdCadCommonPccFound(AntPlusBikeSpdCadCommonPcc bikeSpdCadCommonPcc) {
        // first of all, handle super class
        onNewLegacyCommonPccFound(bikeSpdCadCommonPcc);

        bikeSpdCadCommonPcc.subscribeBatteryStatusEvent(new AntPlusBikeSpdCadCommonPcc.IBatteryStatusReceiver() {

            @Override
            public void onNewBatteryStatus(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal batteryVoltage, BatteryStatus batteryStatus) {
                if (DEBUG) Log.i(TAG, "got new battery status: " + batteryStatus);
                gotBatteryPercentage(BatteryStatusHelper.getBatterPercentage(batteryStatus));
            }
        });

    }

    protected void onNewCommonPccFound(final AntPlusCommonPcc commonPcc) {
        if (DEBUG)
            Log.i(TAG, "onNewCommonPccFound() " + commonPcc.getDeviceName() + "class: " + commonPcc.getClass().getName());

        mCommonPcc = commonPcc;

        commonPcc.subscribeManufacturerIdentificationEvent(new IManufacturerIdentificationReceiver() {

            @Override
            public void onNewManufacturerIdentification(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, int hardwareRevision, int manufacturerID, int modelNumber) {
                gotManufacturer(Manufacturer.getName(manufacturerID));
            }
        });

        commonPcc.subscribeBatteryStatusEvent(new IBatteryStatusReceiver() {
            @Override
            public void onNewBatteryStatus(long estTimestamp,
                                           java.util.EnumSet<EventFlag> eventFlags,
                                           long cumulativeOperatingTime,
                                           java.math.BigDecimal batteryVoltage,
                                           BatteryStatus batteryStatus,
                                           int cumulativeOperatingTimeResolution,
                                           int numberOfBatteries,
                                           int batteryIdentifier) {
                gotBatteryPercentage(BatteryStatusHelper.getBatterPercentage(batteryStatus));
            }
        });
    }

    protected abstract void subscribeCommonEvents(AntPluginPcc antPluginPcc);

    /**
     * start searching for this Device
     */
    protected abstract PccReleaseHandle requestAccess();

    public void shutDown() {
        releaseAccess();
    }

    protected void releaseAccess() {
        if (DEBUG) Log.i(TAG, "releaseAccess()");

        if (pccReleaseHandle != null) {
            pccReleaseHandle.close();
        }
        if (mLegacyCommonPcc != null) {
            mLegacyCommonPcc.releaseAccess();
        }
        if (mCommonPcc != null) {
            mCommonPcc.releaseAccess();
        }
    }

    protected class MyDeviceStateChangeReceiver implements IDeviceStateChangeReceiver {
        @Override
        public void onDeviceStateChange(DeviceState newDeviceState) {
            if (DEBUG) Log.i(TAG, "onDeviceStateChange: new state = " + newDeviceState);
        }
    }

    protected class MyResultReceiver<T extends AntPluginPcc> implements IPluginAccessResultReceiver<T> {
        //Handle the result, connecting to events on success or reporting failure to user.
        @Override
        public void onResultReceived(T pluginPcc, RequestAccessResult resultCode, DeviceState initialDeviceState) {
            if (DEBUG)
                Log.i(TAG, "onResultReceived, resultCode=" + resultCode + ", initialDeviceState=" + initialDeviceState);

            switch (resultCode) {
                case SUCCESS:
                    subscribeCommonEvents(pluginPcc);
                    break;
            }
        }
    }

}
