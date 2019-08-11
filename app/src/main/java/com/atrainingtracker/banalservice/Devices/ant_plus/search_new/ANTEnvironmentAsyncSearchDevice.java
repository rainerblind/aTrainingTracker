package com.atrainingtracker.banalservice.Devices.ant_plus.search_new;

import android.content.Context;

import com.atrainingtracker.banalservice.Devices.DeviceType;
import com.atrainingtracker.banalservice.Devices.ant_plus.search_new.ANTSearchForNewDevicesEngineMultiDeviceSearch.IANTAsyncSearchEngineInterface;
import com.dsi.ant.plugins.antplus.pcc.AntPlusEnvironmentPcc;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

public class ANTEnvironmentAsyncSearchDevice extends MyANTAsyncSearchDevice {

    public ANTEnvironmentAsyncSearchDevice(Context context, IANTAsyncSearchEngineInterface callback, MultiDeviceSearchResult deviceFound, boolean pairingRecommendation) {
        super(context, callback, DeviceType.ENVIRONMENT, deviceFound, pairingRecommendation);
    }

    @Override
    protected void subscribeCommonEvents(AntPluginPcc antPluginPcc) {
        onNewCommonPccFound((AntPlusEnvironmentPcc) antPluginPcc);
    }

    @Override
    protected PccReleaseHandle requestAccess() {
        return AntPlusEnvironmentPcc.requestAccess(mContext, mDeviceFound.getAntDeviceNumber(), 0, new MyResultReceiver<AntPlusEnvironmentPcc>(), new MyDeviceStateChangeReceiver());
    }

}
