/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
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

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.SearchForNewDevicesInterface;
import com.atrainingtracker.banalservice.devices.ant_plus.AntChannelHelper;
import com.dsi.ant.AntService;
import com.dsi.ant.channel.AntChannel;
import com.dsi.ant.channel.AntChannelProvider;
import com.dsi.ant.channel.IAntChannelEventHandler;
import com.dsi.ant.message.ChannelId;
import com.dsi.ant.message.ChannelType;
import com.dsi.ant.message.LibConfig;
import com.dsi.ant.message.LowPrioritySearchTimeout;
import com.dsi.ant.message.fromant.BroadcastDataMessage;
import com.dsi.ant.message.fromant.MessageFromAntType;
import com.dsi.ant.message.ipc.AntMessageParcel;
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
    private static final boolean DEBUG = BANALService.getDebug(true);

    protected Context mContext;
    protected DeviceType mDeviceType;
    protected MultiDeviceSearch mMultiDeviceSearch;

    protected List<MyANTAsyncSearchDevice> mFoundDevices = new LinkedList<MyANTAsyncSearchDevice>();
    private final IANTAsyncSearchEngineInterface mCallbackInterface;

    protected AntService mRadarAntService;
    protected AntChannel mRadarAntChannel;
    protected boolean mIsRadarServiceBound = false;

    private final ServiceConnection mRadarServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                mRadarAntService = new AntService(service);
                AntChannelProvider provider = mRadarAntService.getChannelProvider();
                mRadarAntChannel = AntChannelHelper.acquireAntPlusChannel(mContext, provider);
                mRadarAntChannel.assign(ChannelType.BIDIRECTIONAL_SLAVE);
                mRadarAntChannel.setChannelId(new ChannelId(0, AntChannelHelper.BIKE_RADAR_DEVICE_TYPE, 0));
                mRadarAntChannel.setPeriod(AntChannelHelper.BIKE_RADAR_CHANNEL_PERIOD);
                mRadarAntChannel.setRfFrequency(AntChannelHelper.BIKE_RADAR_RF_FREQUENCY);
                mRadarAntChannel.setAdapterWideLibConfig(new LibConfig(true, true, false));
                mRadarAntChannel.setSearchTimeout(LowPrioritySearchTimeout.TWENTY_FIVE_SECONDS);
                mRadarAntChannel.setChannelEventHandler(new IAntChannelEventHandler() {
                    @Override
                    public void onReceiveMessage(MessageFromAntType messageType, AntMessageParcel messageParcel) {
                        if (messageType == MessageFromAntType.BROADCAST_DATA || messageType == MessageFromAntType.ACKNOWLEDGED_DATA) {
                            BroadcastDataMessage msg = new BroadcastDataMessage(messageParcel);
                            if (msg.hasExtendedData() && msg.getExtendedData().hasChannelId()) {
                                int deviceNumber = msg.getExtendedData().getChannelId().getDeviceNumber();
                                if (deviceNumber > 0) {
                                    mCallbackInterface.onNewDeviceFound(
                                            DeviceType.BIKE_RADAR,
                                            deviceNumber,
                                            "ANT+ Radar " + deviceNumber,
                                            true,
                                            null,
                                            -1
                                    );
                                }
                            }
                        }
                    }

                    @Override
                    public void onChannelDeath() {
                        mCallbackInterface.onSearchStopped();
                    }
                });
                mRadarAntChannel.open();
                if (DEBUG) Log.i(TAG, "Wildcard ANT+ Radar scan channel opened");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start ANT+ radar async search", e);
                mCallbackInterface.onSearchStopped();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mRadarAntService = null;
            mRadarAntChannel = null;
            mCallbackInterface.onSearchStopped();
        }
    };

    public ANTSearchForNewDevicesEngineMultiDeviceSearch(Context context, DeviceType deviceType, IANTAsyncSearchEngineInterface callbackInterface) {
        mContext = context;
        mDeviceType = deviceType;
        mCallbackInterface = callbackInterface;
    }

    public DeviceType getDeviceType() {
        return mDeviceType;
    }

    @Override
    public void startAsyncSearch() {
        if (mDeviceType == DeviceType.BIKE_RADAR) {
            startRadarAsyncSearch();
            return;
        }

        com.dsi.ant.plugins.antplus.pcc.defines.DeviceType antDeviceType = DeviceType.getAntPluginDeviceType(mDeviceType);

        if (antDeviceType == null) {
            Log.d(TAG, "in startAsyncSearch: non supported deviceType: " + mDeviceType);
            return;
        }

        EnumSet<com.dsi.ant.plugins.antplus.pcc.defines.DeviceType> devices = EnumSet.of(antDeviceType);

        mMultiDeviceSearch = new MultiDeviceSearch(mContext, devices, new MyMultiDeviceSearchCallback());
    }

    private void startRadarAsyncSearch() {
        try {
            mIsRadarServiceBound = AntService.bindService(mContext, mRadarServiceConnection);
            if (!mIsRadarServiceBound) {
                Log.w(TAG, "AntService.bindService returned false for radar search");
                mCallbackInterface.onSearchStopped();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error binding AntService for radar search", e);
            mCallbackInterface.onSearchStopped();
        }
    }

    @Override
    public void stopAsyncSearch() {
        if (mMultiDeviceSearch != null) {
            mMultiDeviceSearch.close();
        }

        for (MyANTAsyncSearchDevice myANTAsyncSearchDevice : mFoundDevices) {
            myANTAsyncSearchDevice.shutDown();
        }

        if (mRadarAntChannel != null) {
            try {
                mRadarAntChannel.close();
                mRadarAntChannel.release();
            } catch (Exception ignored) {
            }
            mRadarAntChannel = null;
        }
        if (mIsRadarServiceBound) {
            try {
                mContext.unbindService(mRadarServiceConnection);
            } catch (Exception ignored) {
            }
            mIsRadarServiceBound = false;
        }
        mRadarAntService = null;
    }

    protected void myOnDeviceFound(MultiDeviceSearchResult deviceFound) {
        if (DEBUG) Log.i(TAG, "onDeviceFound(): " + deviceFound.getDeviceDisplayName());

        // Default auto-pairing on discovery (REQ-SEN-020, ATT-4051)
        boolean pairingRecommendation = true;

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

        default void onNewDeviceFound(DeviceType deviceType, int antDeviceNumber, String deviceDisplayName, boolean pairingRecommendation, String manufacturer, int batteryPercentage) {
        }
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
