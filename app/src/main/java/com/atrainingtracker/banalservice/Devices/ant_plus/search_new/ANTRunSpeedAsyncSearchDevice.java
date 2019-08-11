package com.atrainingtracker.banalservice.Devices.ant_plus.search_new;

import android.content.Context;

import com.atrainingtracker.banalservice.Devices.DeviceType;
import com.atrainingtracker.banalservice.Devices.ant_plus.search_new.ANTSearchForNewDevicesEngineMultiDeviceSearch.IANTAsyncSearchEngineInterface;
import com.dsi.ant.plugins.antplus.pcc.AntPlusStrideSdmPcc;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

public class ANTRunSpeedAsyncSearchDevice extends MyANTAsyncSearchDevice {

    public ANTRunSpeedAsyncSearchDevice(Context context, IANTAsyncSearchEngineInterface callback, MultiDeviceSearchResult deviceFound, boolean pairingRecommendation) {
        super(context, callback, DeviceType.RUN_SPEED, deviceFound, pairingRecommendation);
    }

    @Override
    protected void subscribeCommonEvents(AntPluginPcc antPluginPcc) {
        onNewCommonPccFound((AntPlusStrideSdmPcc) antPluginPcc);
    }

    @Override
    protected PccReleaseHandle requestAccess() {
        return AntPlusStrideSdmPcc.requestAccess(mContext, mDeviceFound.getAntDeviceNumber(), 0, new MyResultReceiver<AntPlusStrideSdmPcc>(), new MyDeviceStateChangeReceiver());
    }

}
