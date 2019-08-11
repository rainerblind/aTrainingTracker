package com.atrainingtracker.banalservice.Devices.ant_plus.search_new;

import android.content.Context;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.DeviceType;
import com.atrainingtracker.banalservice.Devices.SearchForNewDevicesInterface;
import com.dsi.ant.plugins.antplus.pcc.MultiDeviceSearch;
import com.dsi.ant.plugins.antplus.pcc.MultiDeviceSearch.RssiSupport;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;
import com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;


public class ANTSearchForNewDevicesEngineMultiDeviceSearch
        implements SearchForNewDevicesInterface {
    private static final String TAG = "ANTSearchForNewDevicesEngineMultiDeviceSearch";
    private static final boolean DEBUG = BANALService.DEBUG & true;

    protected Context mContext;
    protected DeviceType mDeviceType;
    protected MultiDeviceSearch mMultiDeviceSearch;

    protected List<MyANTAsyncSearchDevice> mFoundDevices = new LinkedList<MyANTAsyncSearchDevice>();
    private IANTAsyncSearchEngineInterface mCallbackInterface;

    public ANTSearchForNewDevicesEngineMultiDeviceSearch(Context context, DeviceType deviceType, IANTAsyncSearchEngineInterface callbackInterface) {
        mContext = context;
        mDeviceType = deviceType;
        mCallbackInterface = callbackInterface;
    }

    @Override
    public void startAsyncSearch() {
        com.dsi.ant.plugins.antplus.pcc.defines.DeviceType antDeviceType = DeviceType.getAntPluginDeviceType(mDeviceType);

        if (antDeviceType == null) {
            Log.d(TAG, "in startAsyncSearch: non supported deviceType: " + mDeviceType);
            return;
        }

        EnumSet<com.dsi.ant.plugins.antplus.pcc.defines.DeviceType> devices = EnumSet.of(antDeviceType);

        mMultiDeviceSearch = new MultiDeviceSearch(mContext, devices, new MyMultiDeviceSearchCallback());
    }

    @Override
    public void stopAsyncSearch() {
        if (mMultiDeviceSearch != null) {
            mMultiDeviceSearch.close();
        }

        for (MyANTAsyncSearchDevice myANTAsyncSearchDevice : mFoundDevices) {
            myANTAsyncSearchDevice.shutDown();
        }
    }

    protected void myOnDeviceFound(MultiDeviceSearchResult deviceFound) {
        if (DEBUG) Log.i(TAG, "onDeviceFound(): " + deviceFound.getDeviceDisplayName());

        boolean pairingRecommendation = deviceFound.isPreferredDevice() | deviceFound.isAlreadyConnected();

        DeviceType deviceType = DeviceType.getDeviceType(deviceFound.getAntDeviceType());

        if (deviceType == null) {
            Log.d(TAG, "onDeviceFound: non-supported deviceType: " + deviceFound.getAntDeviceType());
            return;
        }
        if (deviceType != mDeviceType) {
            Log.d(TAG, "deviceType of found device is not the one we are searching for: mDeviceType=" + mDeviceType + "deviceType=" + deviceType);
            return;
        }

        mCallbackInterface.onNewDeviceFound(deviceType, deviceFound, pairingRecommendation, null, -1);

        MyANTAsyncSearchDevice myANTAsyncSearchDevice = null;
        switch (deviceType) {
            case HRM:
                myANTAsyncSearchDevice = new ANTHeartRateAsyncSearchDevice(mContext, mCallbackInterface, deviceFound, pairingRecommendation);
                break;
            case BIKE_SPEED:
                myANTAsyncSearchDevice = new ANTBikeSpeedAsyncSearchDevice(mContext, mCallbackInterface, deviceFound, pairingRecommendation);
                break;
            case BIKE_CADENCE:
                myANTAsyncSearchDevice = new ANTBikeCadenceAsyncSearchDevice(mContext, mCallbackInterface, deviceFound, pairingRecommendation);
                break;
            case BIKE_SPEED_AND_CADENCE:
                myANTAsyncSearchDevice = new ANTBikeSpeedAndCadenceAsyncSearchDevice(mContext, mCallbackInterface, deviceFound, pairingRecommendation);
                break;
            case BIKE_POWER:
                myANTAsyncSearchDevice = new ANTBikePowerAsyncSearchDevice(mContext, mCallbackInterface, deviceFound, pairingRecommendation);
                break;
            case RUN_SPEED:
                myANTAsyncSearchDevice = new ANTRunSpeedAsyncSearchDevice(mContext, mCallbackInterface, deviceFound, pairingRecommendation);
                break;
            case ENVIRONMENT:
                myANTAsyncSearchDevice = new ANTEnvironmentAsyncSearchDevice(mContext, mCallbackInterface, deviceFound, pairingRecommendation);
                break;
        }

        if (myANTAsyncSearchDevice != null) {
            mFoundDevices.add(myANTAsyncSearchDevice);
        }
    }

    public interface IANTAsyncSearchEngineInterface {
        void onSearchStopped();

        void onNewDeviceFound(DeviceType deviceType, MultiDeviceSearchResult mDeviceFound, boolean pairingRecommendation, String manufacturer, int batteryPercentage);
    }

    protected class MyMultiDeviceSearchCallback implements MultiDeviceSearch.SearchCallbacks {

        @Override
        public void onDeviceFound(MultiDeviceSearchResult device) {
            if (DEBUG) Log.i(TAG, "onDeviceFound: " + device.getDeviceDisplayName());
            myOnDeviceFound(device);
        }

        @Override
        public void onSearchStarted(RssiSupport arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onSearchStopped(RequestAccessResult arg0) {
            mCallbackInterface.onSearchStopped();
        }

    }
}
