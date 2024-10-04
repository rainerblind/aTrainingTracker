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

package com.atrainingtracker.banalservice.devices.ant_plus.search_new;

import android.content.Context;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.ant_plus.search_new.ANTSearchForNewDevicesEngineMultiDeviceSearch.IANTAsyncSearchEngineInterface;
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

public class ANTHeartRateAsyncSearchDevice extends MyANTAsyncSearchDevice {

    private static final String TAG = "ANTHeartRateAsyncSearchDevice";
    private static final boolean DEBUG = BANALService.getDebug(true);

    public ANTHeartRateAsyncSearchDevice(Context context, IANTAsyncSearchEngineInterface callback, MultiDeviceSearchResult deviceFound, boolean pairingRecommendation) {
        super(context, callback, DeviceType.HRM, deviceFound, pairingRecommendation);

        if (DEBUG) Log.i(TAG, "ANTHeartRateAsyncSearchDevice");
    }

    @Override
    protected void subscribeCommonEvents(AntPluginPcc antPluginPcc) {
        if (DEBUG) Log.i(TAG, "subscribeCommonEvents()");

        onNewLegacyCommonPccFound((AntPlusHeartRatePcc) antPluginPcc);
    }

    @Override
    protected PccReleaseHandle requestAccess() {
        if (DEBUG) Log.i(TAG, "requestAccess()");

        return AntPlusHeartRatePcc.requestAccess(mContext, mDeviceFound.getAntDeviceNumber(), 0, new MyResultReceiver<AntPlusHeartRatePcc>(), new MyDeviceStateChangeReceiver());
    }

}
