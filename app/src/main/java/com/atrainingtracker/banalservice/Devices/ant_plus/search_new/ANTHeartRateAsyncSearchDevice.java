package com.atrainingtracker.banalservice.Devices.ant_plus.search_new;

import android.content.Context;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.DeviceType;
import com.atrainingtracker.banalservice.Devices.ant_plus.search_new.ANTSearchForNewDevicesEngineMultiDeviceSearch.IANTAsyncSearchEngineInterface;
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

public class ANTHeartRateAsyncSearchDevice extends MyANTAsyncSearchDevice {

    private static final String TAG = "ANTHeartRateAsyncSearchDevice";
    private static final boolean DEBUG = BANALService.DEBUG & true;

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
