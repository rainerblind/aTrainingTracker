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

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.Manufacturer;
import com.atrainingtracker.banalservice.devices.MyRemoteDevice;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
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
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

import java.math.BigDecimal;
import java.util.EnumSet;

//import de.rainerblind.MyAntPlusApp;

// TODO: where/when to register and unregister the sensors

public abstract class MyANTDevice extends MyRemoteDevice {
    private static final boolean DEBUG = BANALService.DEBUG & true;
    protected int mAntDeviceNumber;
    /**
     * The state of the device/channel
     **/
    protected DeviceState mDeviceState = null;  // TODO: rename to mANTDeviceState
    private final String TAG = "MyANTDevice";
    private AntPlusLegacyCommonPcc mLegacyCommonPcc = null;
    private AntPlusCommonPcc mCommonPcc = null;
    private PccReleaseHandle pccReleaseHandle = null;


    // not really interesting
//    /** will be learned by listening to the ANT+ Data **/
//    protected long          mSerialNumber;
//    protected int           mSoftwareVersion; // or Software Revision
//    protected int           mModelNumber;
//    protected int           mHardwareVersion;
//    protected int           mHardwareRevision;
//    protected long          mCumulativeOperatingTime;
//	// protected BigDecimal    mBatteryVoltage;
//	// protected BatteryStatus mBatteryStatus; 
//	// protected int           mCumulativeOperatingTimeResolution;
//	protected int           mSoftwareRevision;

    // TODO: flags to indicate what is supported???


    /**
     * constructor
     */
    public MyANTDevice(Context context, MySensorManager mySensorManager, DeviceType deviceType, long deviceId, int antDeviceNumber) {
        super(context, mySensorManager, deviceType, deviceId);

        mAntDeviceNumber = antDeviceNumber;
    }

    @Override
    public Protocol getProtocol() {
        return Protocol.ANT_PLUS;
    }

    @Override
    protected void onNewCalibrationFactor() {
        // TODO: more specific?
        subscribeSpecificEvents();
    }

    @Override
    public void startSearching() {
        myLog("startSearching()");
        super.notifyStartSearching();

        pccReleaseHandle = requestAccess();
    }

    @Override
    public boolean isReceivingData() {
        return mDeviceState == DeviceState.TRACKING;
    }

    protected int getANTDeviceNumber() {
        return mAntDeviceNumber;
    }


    @Override
    public void shutDown() {
        releaseAccess();
        super.shutDown();
    }

    protected void onDeviceStateChangeHandler(DeviceState newDeviceState) {
        myLog("onDeviceStateChangedHandler: " + newDeviceState);

        if (isSearching() && newDeviceState != DeviceState.SEARCHING) {
            if (DEBUG) Log.i(TAG, "stopped searching");
            boolean success = newDeviceState == DeviceState.TRACKING;
            notifyStopSearching(success);  // WTF might be called several times?
        } else if (!isSearching() && newDeviceState == DeviceState.SEARCHING) {
            notifyStartSearching();
        }

        mDeviceState = newDeviceState;

        switch (newDeviceState) {
            case CLOSED: // The device is not connected and not trying to connect but will still respond to commands.
                unregisterSensors();
                //TODO: something else?
                break;

            case DEAD: //  The device is no longer usable and will not respond to commands.
                unregisterSensors();
                // TODO: something else?
                break;

            case PROCESSING_REQUEST: //  The plugin is currently processing a command request from a client.
                // TODO
                break;

            case SEARCHING: // The device is attempting to establish a connection.
                unregisterSensors();
                // TODO: something else?
                break;

            case TRACKING: // The device has an open connection, and can receive and transmit data.
                setLastActive();

                registerSensors();
                break;

            default:
                myLog("onDeviceStateChangedHandler: unknown case");
        }
    }


//    protected void onResultReceivedHandler(RequestAccessResult resultCode, DeviceState initialDeviceState)
//    {
//    	myLog("onResultReceivedHandler: resultCode=" + resultCode + ", initialDeviceStat=" + initialDeviceState);
//    	
//    	switch(resultCode)
//    	{
//    	// case AntPluginMsgDefines.MSG_REQACC_RESULT_whatSUCCESS:
//    	// should be handled by calling class???
//    	case CHANNEL_NOT_AVAILABLE:
//    		myLog("onResultReceivedHandler: channel not available");
//    		break;
//    		
//    	case OTHER_FAILURE:
//    		myLog("onResultReceivedHandler: other failure");
//    		break;
//    		
//    	case DEPENDENCY_NOT_INSTALLED:
//    		myLog("onResultReceivedHandler: dependency not installed");
//    		dependencyNotInstalled();    		
//    		break;
//    		
//    	case USER_CANCELLED:
//    		myLog("onResultReceiverHandler: user cancelled");
//    		break;
//    		
//    	default:
//    		myLog("onResultReceivedHandler: unknown case: RequestAccessResult=" + resultCode + ", DeviceState=" + initialDeviceState);
//    		break;
//    	} 
//    }

    private void dependencyNotInstalled() {
        myLog("dependencyNotInstalled()");
        // TODO: is this the right place to install the dependencies or should we just send a broadcast intent???

        if (mContext == null) {
            Log.d(TAG, "WTF: mContext == null");
        }

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        alertDialogBuilder.setTitle("Missing Dependency");
        alertDialogBuilder.setMessage("The required application\n\"" + AntPluginPcc.getMissingDependencyName() + "\"\n is not installed. Do you want to launch the Play Store to search for it?");
        alertDialogBuilder.setCancelable(true);
        alertDialogBuilder.setPositiveButton("Go to Store", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent startStore = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + AntPluginPcc.getMissingDependencyPackageName()));
                startStore.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                mContext.startActivity(startStore);
            }
        });
        alertDialogBuilder.setNegativeButton("Cancel", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        final AlertDialog waitDialog = alertDialogBuilder.create();
        waitDialog.show();
    }


    protected void onNewLegacyCommonPccFound(final AntPlusLegacyCommonPcc legacyCommonPcc) {
        myLog("onNewLegacyCommonPccFound() " + legacyCommonPcc.getDeviceName());

        mLegacyCommonPcc = legacyCommonPcc;

//    	myLog("before subscribeCumulativeOperatingTimeEvent");
//    	legacyCommonPcc.subscribeCumulativeOperatingTimeEvent(new ICumulativeOperatingTimeReceiver(){
//
//			@Override
//			public void onNewCumulativeOperatingTime(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, long cumulativeOperatingTime) 
//			{
//				mCumulativeOperatingTime = cumulativeOperatingTime;				
//			}});

        myLog("before subscribeManufacturerAndSerialEvent");
        if (getManufacturerName() == null) {
            legacyCommonPcc.subscribeManufacturerAndSerialEvent(new IManufacturerAndSerialReceiver() {

                @Override
                public void onNewManufacturerAndSerial(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, int manufacturerID, int serialNumber) {
                    String manufacturerName = Manufacturer.getName(manufacturerID);
                    setManufacturerName(manufacturerName);
                    // 		mSerialNumber = serialNumber;

                    // now, that we have the manufacturer name, we can unsubscribe
                    myLog("got manufacturer name, now unsubscribe");
                    legacyCommonPcc.subscribeManufacturerAndSerialEvent(null);
                }
            });

        }

//    	myLog("before subscribeVersionAndModelEvent");
//    	legacyCommonPcc.subscribeVersionAndModelEvent(new IVersionAndModelReceiver(){
//
//			@Override
//			public void onNewVersionAndModel(long estTimestamp,
//                    java.util.EnumSet<EventFlag> eventFlags,
//                    int hardwareVersion,
//                    int softwareVersion,
//                    int modelNumber) 
//			{
//				mHardwareVersion = hardwareVersion;
//				mSoftwareVersion = softwareVersion;
//				mModelNumber     = modelNumber;
//			}});
    }

    protected void onNewBikeSpdCadCommonPccFound(AntPlusBikeSpdCadCommonPcc bikeSpdCadCommonPcc) {
        // first of all, handle super class
        onNewLegacyCommonPccFound(bikeSpdCadCommonPcc);

        bikeSpdCadCommonPcc.subscribeBatteryStatusEvent(new AntPlusBikeSpdCadCommonPcc.IBatteryStatusReceiver() {

            @Override
            public void onNewBatteryStatus(long estTimestamp, EnumSet<EventFlag> eventFlags, BigDecimal batteryVoltage, BatteryStatus batteryStatus) {
                myLog("got new battery status: " + batteryStatus);
                setBatteryPercentage(BatteryStatusHelper.getBatterPercentage(batteryStatus));
            }
        });

    }

    protected void onNewCommonPccFound(final AntPlusCommonPcc commonPcc) {
        myLog("onNewCommonPccFound() " + commonPcc.getDeviceName() + "class: " + commonPcc.getClass().getName());

        mCommonPcc = commonPcc;

        if (getManufacturerName() == null) {
            commonPcc.subscribeManufacturerIdentificationEvent(new IManufacturerIdentificationReceiver() {
                @Override
                public void onNewManufacturerIdentification(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, int hardwareRevision, int manufacturerID, int modelNumber) {
                    // mHardwareRevision = hardwareRevision;
                    // mModelNumber      = modelNumber;
                    String manufacturer = Manufacturer.getName(manufacturerID);
                    setManufacturerName(manufacturer);
                    // now, that we have the manufacturer, we unsubscribe
                    commonPcc.subscribeManufacturerIdentificationEvent(null);
                }
            });
        }

        myLog("before subscribeBatteryStatusEvent");
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
                myLog("got new battery status: " + batteryStatus);
                setBatteryPercentage(BatteryStatusHelper.getBatterPercentage(batteryStatus));
                // mCumulativeOperatingTime = cumulativeOperatingTime;
            }
        });

//    	myLog("before subscribeProductInformationEvent");
//    	commonPcc.subscribeProductInformationEvent(new IProductInformationReceiver()
//    	{
//    		@Override
//    		public void onNewProductInformation(long estTimestamp, java.util.EnumSet<EventFlag> eventFlags, int mainSoftwareRevision, int supplementalSoftwareRevision, long serialNumber)
//    		{
//    			mSoftwareRevision = mainSoftwareRevision;
//    			mSerialNumber     = serialNumber;
//            }});
    }


    /**
     * will be called when a new Pcc is found
     */
    final protected void onNewPluginPccFound(AntPluginPcc antPluginPcc) {
        myLog("onNewPluginPccFound()");

        myLog("AntPluginPcc class name: " + antPluginPcc.getClass().getName());

        setSpecificPcc(antPluginPcc);

        subscribeSpecificEvents();

        subscribeCommonEvents();
    }

    abstract protected void setSpecificPcc(AntPluginPcc antPluginPcc);

    abstract protected void subscribeSpecificEvents();

    abstract protected void subscribeCommonEvents();


    /**
     * start searching for this Device
     */
    protected abstract PccReleaseHandle requestAccess();

    protected void releaseAccess() {
        myLog("releaseAccess()");

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
            myLog("onDeviceStatChange: " + newDeviceState);

            MyANTDevice.this.onDeviceStateChangeHandler(newDeviceState);
        }
    }


    protected class MyResultReceiver<T extends AntPluginPcc> implements IPluginAccessResultReceiver<T> {
        //Handle the result, connecting to events on success or reporting failure to user.
        @Override
        public void onResultReceived(T pluginPcc, RequestAccessResult resultCode, DeviceState initialDeviceState) {
            myLog("onResultReceived: resultCode=" + resultCode + ", initialDeviceState=" + initialDeviceState);

            onDeviceStateChangeHandler(initialDeviceState);  // TODO: correct to do this at the beginning?

            switch (resultCode) {
                case SUCCESS:
                    onNewPluginPccFound(pluginPcc);
                    break;

                case DEPENDENCY_NOT_INSTALLED:
                    dependencyNotInstalled();
                    break;

                case CHANNEL_NOT_AVAILABLE:
                    break;
                case OTHER_FAILURE:
                    break;
                case USER_CANCELLED:
                    break;
                case ADAPTER_NOT_DETECTED:
                    break;
                case ALREADY_SUBSCRIBED:
                    break;
                case BAD_PARAMS:
                    break;
                case DEVICE_ALREADY_IN_USE:
                    break;
                case SEARCH_TIMEOUT:
                    break;
                case UNRECOGNIZED:
                    break;
            }
        }
    }

}
