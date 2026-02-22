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

package com.atrainingtracker.banalservice.devices;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeCadenceDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTBikePowerDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeSpeedAndCadenceDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeSpeedDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTEnvironmentDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTHeartRateDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.ANTRunSpeedDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.MyANTDevice;
import com.atrainingtracker.banalservice.devices.ant_plus.search_new.ANTSearchForNewDevicesEngineMultiDeviceSearch;
import com.atrainingtracker.banalservice.devices.ant_plus.search_new.ANTSearchForNewDevicesEngineMultiDeviceSearch.IANTAsyncSearchEngineInterface;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEBikeCadenceDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEBikePowerDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEBikeSpeedAndCadenceDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEBikeSpeedDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEHeartRateDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERunSpeedDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice;
import com.atrainingtracker.banalservice.devices.bluetooth_le.search_new.BTSearchForNewDevicesEngine;
import com.atrainingtracker.banalservice.devices.bluetooth_le.search_new.BTSearchForNewDevicesEngine.IBTSearchForNewDevicesEngineInterface;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.sensor.MyAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.DevicesDbHelper;
import com.atrainingtracker.banalservice.helpers.HavePressureSensor;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.atrainingtracker.banalservice.BSportType.UNKNOWN;

import androidx.core.content.ContextCompat;


public class DeviceManager {
    private static final String TAG = "DeviceManager";
    private static final boolean DEBUG = BANALService.getDebug(false);
    protected static MyRemoteDevice cMyRemoteDeviceCurrentlySearchingFor = null;
    protected Context mContext;
    protected ClockDevice mClockDevice;
    protected SpeedAndLocationDevice mSpeedAndLocationDevice_GPS, mSpeedAndLocationDevice_GoogleFused, mSpeedAndLocationDevice_Network;
    protected AltitudeFromPressureDevice mAltitudeFromPressureDevice;
    protected VerticalSpeedAndSlopeDevice mVerticalSpeedAndSlopeDevice;
    protected boolean mHavePressureSensor = false;
    protected IntentFilter mPairingChangedFilter = new IntentFilter(BANALService.PAIRING_CHANGED);
    // protected IntentFilter mRemoveDeviceFilter            = new IntentFilter(BANALService.REMOVE_DEVICE);
    // protected IntentFilter mCreateNewDeviceFilter    = new IntentFilter(BANALService.CREATE_NEW_DEVICE);
    protected IntentFilter mSearchingStoppedForOneFilter = new IntentFilter(BANALService.SEARCHING_STOPPED_FOR_ONE_INTENT);
    protected IntentFilter mStartSearchingForNewDevicesFilter = new IntentFilter(BANALService.START_SEARCHING_FOR_NEW_DEVICES_INTENT);
    protected IntentFilter mStopSearchingForNewDevicesFilter = new IntentFilter(BANALService.STOP_SEARCHING_FOR_NEW_DEVICES_INTENT);
    protected MySensorManager mSensorManager;
    protected Map<Long, MyRemoteDevice> mMyRemoteDevices = new HashMap<Long, MyRemoteDevice>();
    protected Map<MyRemoteDevice, Integer> mMyRemoteDeviceTries = new HashMap<>();
    protected LinkedList<MyRemoteDevice> mSearchQueue = new LinkedList<MyRemoteDevice>();
    protected LinkedList<Long> mFoundDevices = new LinkedList<>();
    protected ANTSearchForNewDevicesEngineMultiDeviceSearch mAntAsyncSearchEngine = null;
    protected BTSearchForNewDevicesEngine mBTSearchForNewDevicesEngine = null;
    private final DevicesDatabaseManager mDevicesDatabaseManager;
    private final BroadcastReceiver mStopSearchingForNewDevicesReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "Received StopSearchingForNewDevices Broadcast");

            stopSearchForNewRemoteDevices();
        }
    };
    BANALService mBanalService = null;
    private final BroadcastReceiver mSearchingStoppedForOneReceiver = new BroadcastReceiver() {
        // it was observed that this might be called from Remote Devices that are currently tracking
        // => not only from the device we are currently searching for
        // thus, we must be a little bit careful here.
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false);
            long deviceID = intent.getLongExtra(BANALService.DEVICE_ID, -1);
            if (DEBUG)
                Log.i(TAG, "finished searching for a remote device, success=" + success + ", deviceID=" + deviceID);
            if (cMyRemoteDeviceCurrentlySearchingFor != null
                    && cMyRemoteDeviceCurrentlySearchingFor.getDeviceId() == deviceID) {  // it is indeed the device that we are currently searching
                if (!success) {                                                     // it was not found
                    if (!mMyRemoteDeviceTries.containsKey(cMyRemoteDeviceCurrentlySearchingFor)        // device not in the list
                            || mMyRemoteDeviceTries.get(cMyRemoteDeviceCurrentlySearchingFor) <= 1) {  // no longer try to search for this device
                        if (DEBUG)
                            Log.i(TAG, "max number of tries <= 1 -> give up searching for this device");
                        cMyRemoteDeviceCurrentlySearchingFor.shutDown();                // so we shut it down
                        mMyRemoteDevices.remove(deviceID);                              // and remove it
                    } else {  // continue searching
                        if (DEBUG)
                            Log.i(TAG, "max number of tries > 1 -> give this device another chance");
                        mMyRemoteDeviceTries.put(cMyRemoteDeviceCurrentlySearchingFor, mMyRemoteDeviceTries.get(cMyRemoteDeviceCurrentlySearchingFor) - 1);
                        mSearchQueue.addLast(cMyRemoteDeviceCurrentlySearchingFor);
                    }

                }
            } else {
                Log.d(TAG, "WTF: a remote device we are not searching for stopped searching.");
            }
            searchForNextRemoteDevice();
        }
    };
    private final BroadcastReceiver mPairingChangedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "pairing changed!");

            if (!(intent.hasExtra(BANALService.PAIRED) && intent.hasExtra(BANALService.DEVICE_ID))) {
                Log.d(TAG, "no extras passed, returning");
                return;
            }

            long deviceId = intent.getLongExtra(BANALService.DEVICE_ID, -1);
            boolean paired = intent.getBooleanExtra(BANALService.PAIRED, true);

            pairingChanged(deviceId, paired);
        }
    };
    // Callback interface for searching for ANT+
    IANTAsyncSearchEngineInterface mANTAsyncSearchEngineInterface = new IANTAsyncSearchEngineInterface() {
        public void onSearchStopped() {
            // TODO:inform user with a toast?
        }

        public void onNewDeviceFound(DeviceType deviceType, MultiDeviceSearch.MultiDeviceSearchResult deviceFound, boolean pairingRecommendation, String manufacturer, int batteryPercentage) {
            if (DEBUG)
                Log.d(TAG, "onNewANTDeviceFound: " + deviceType.name() + ", Ant Nr:" + deviceFound.getAntDeviceNumber());

            long deviceId = -1;

            // check whether this device is already in DB
            // ANTDeviceID antDeviceID = new ANTDeviceID(antDeviceType.getDeviceTypeByte(), deviceFound.getAntDeviceNumber());
            if (!mDevicesDatabaseManager.isANTDeviceInDB(deviceType, deviceFound.getAntDeviceNumber())) { // not yet known
                if (DEBUG)
                    Log.d(TAG, "device is not yet in the database, so we have to add it to the database");

                deviceId = mDevicesDatabaseManager.insertNewAntDeviceIntoDB(deviceType, deviceFound.getDeviceDisplayName(), deviceFound.getAntDeviceNumber(), pairingRecommendation, manufacturer, batteryPercentage);

                if (deviceId > 0 && pairingRecommendation) {
                    pairingChanged(deviceId, pairingRecommendation);
                }

                // TODO: when manufacturer == null, we should indicate that we are searching for it
            } else {
                if (DEBUG)
                    Log.i(TAG, "Device already in database, so we do not add it to the database");
                deviceId = mDevicesDatabaseManager.getDeviceId(deviceType, deviceFound.getAntDeviceNumber());

                if (manufacturer != null) {
                    if (DEBUG) Log.i(TAG, "wow, we got a manufacturer: " + manufacturer);
                    mDevicesDatabaseManager.setManufacturerName(mDevicesDatabaseManager.getDeviceId(deviceType, deviceFound.getAntDeviceNumber()), manufacturer);
                }

                if (batteryPercentage >= 0) {
                    if (DEBUG) Log.i(TAG, "wow, we got a battery percentage: " + batteryPercentage);
                    mDevicesDatabaseManager.setBatteryPercentage(mDevicesDatabaseManager.getDeviceId(deviceType, deviceFound.getAntDeviceNumber()), batteryPercentage);
                }
            }

            mFoundDevices.add(deviceId);
            sendNewDeviceFoundBroadcast(deviceId);
        }
    };
    // stuff for searching for Bluetooth le
    IBTSearchForNewDevicesEngineInterface mBTSearchInterface = new IBTSearchForNewDevicesEngineInterface() {
        public void onSearchStopped() {
            // TODO:inform user with a toast?
        }

        public void onNewDeviceFound(DeviceType deviceType,
                                     String BluetoothMACAddress,
                                     String name,
                                     String manufacturer,
                                     int batteryPercentage) {
            if (DEBUG)
                Log.d(TAG, "onNewBluetoothDeviceFound: " + deviceType.name() + ", BT address: " + BluetoothMACAddress);

            long deviceId = -1;

            // check whether this device is already in DB
            if (!mDevicesDatabaseManager.isBluetoothDeviceInDB(deviceType, BluetoothMACAddress)) {
                if (DEBUG) Log.d(TAG, "device is not yet in the database, so we have to add it");

                deviceId = mDevicesDatabaseManager.insertNewBluetoothDeviceIntoDB(deviceType, BluetoothMACAddress, name, manufacturer, batteryPercentage, false);
            } else {
                if (DEBUG) Log.d(TAG, "Device already in database, so we do not add it");
                deviceId = mDevicesDatabaseManager.getDeviceId(deviceType, BluetoothMACAddress);
            }

            mFoundDevices.add(deviceId);
            sendNewDeviceFoundBroadcast(deviceId);
        }
    };
    // TODO: start search fo new
    private final BroadcastReceiver mStartSearchingForNewDevicesReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "Received StartSearchingForNewDevices Broadcast");

            if (!(intent.hasExtra(BANALService.PROTOCOL) && intent.hasExtra(BANALService.DEVICE_TYPE))) {
                Log.d(TAG, "no extras passed, returning");
                return;
            }

            Protocol protocol = Protocol.valueOf(intent.getStringExtra(BANALService.PROTOCOL));
            DeviceType deviceType = DeviceType.valueOf(intent.getStringExtra(BANALService.DEVICE_TYPE));
            startSearchForNewRemoteDevices(protocol, deviceType);
        }
    };

    /**
     * Constructor
     **/
    public DeviceManager(BANALService banalService, MySensorManager mySensorManager) {
        mContext = banalService;
        mBanalService = banalService;

        mDevicesDatabaseManager = DevicesDatabaseManager.getInstance(mContext);

        mSensorManager = mySensorManager;

        mHavePressureSensor = HavePressureSensor.havePressureSensor(mContext);

        mClockDevice = new ClockDevice(mContext, mSensorManager);
        if (mHavePressureSensor) {
            mAltitudeFromPressureDevice = new AltitudeFromPressureDevice(mContext, mSensorManager);
        }

        // create the location devices when they are paired.
        DevicesDatabaseManager devicesDatabaseManager = DevicesDatabaseManager.getInstance(mContext);
        long gpsDeviceId = devicesDatabaseManager.getSpeedAndLocationGPSDeviceId();
        if (devicesDatabaseManager.isPaired(gpsDeviceId)
                && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (DEBUG) Log.i(TAG, "creating GPS location device");
            mSpeedAndLocationDevice_GPS = new SpeedAndLocationDevice_GPS(mContext, mSensorManager);
        }

        long fusedDeviceId = devicesDatabaseManager.getSpeedAndLocationGoogleFusedDeviceId();
        if(devicesDatabaseManager.isPaired(fusedDeviceId)
                && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)
                && GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS) {
            if (DEBUG) Log.i(TAG, "creating google fused location device");
            mSpeedAndLocationDevice_GoogleFused = new SpeedAndLocationDevice_GoogleFused(mContext, mSensorManager);
        }

        long networkDeviceId = devicesDatabaseManager.getSpeedAndLocationNetworkDeviceId();
        if (devicesDatabaseManager.isPaired(networkDeviceId)
                && TrainingApplication.havePermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            if (DEBUG) Log.i(TAG, "creating network location device");
            mSpeedAndLocationDevice_Network = new SpeedAndLocationDevice_Network(mContext, mSensorManager);
        }

        mVerticalSpeedAndSlopeDevice = new VerticalSpeedAndSlopeDevice(mContext, mSensorManager);

        // also create paired remote devices and start searching for them
        if (TrainingApplication.startSearchWhenAppStarts()) {
            startSearchForPairedDevices();
        }

        ContextCompat.registerReceiver(mContext, mPairingChangedReceiver, mPairingChangedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(mContext, mSearchingStoppedForOneReceiver, mSearchingStoppedForOneFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(mContext, mStartSearchingForNewDevicesReceiver, mStartSearchingForNewDevicesFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(mContext, mStopSearchingForNewDevicesReceiver, mStopSearchingForNewDevicesFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public static boolean isSearchingForARemoteDevice() {
        return cMyRemoteDeviceCurrentlySearchingFor != null;
    }

    public void shutDown() {
        if (DEBUG) Log.d(TAG, "shutDown()");

        for (MyDevice myDevice : getMyDeviceList()) {
            myDevice.shutDown();
        }

        cMyRemoteDeviceCurrentlySearchingFor = null;
        mMyRemoteDevices = null;
        mSearchQueue = null;
        // TODO: also remove entries of the lists?

        mContext.unregisterReceiver(mPairingChangedReceiver);
        mContext.unregisterReceiver(mSearchingStoppedForOneReceiver);
        // mContext.unregisterReceiver(mStartSearchingReceiver);
        mContext.unregisterReceiver(mStartSearchingForNewDevicesReceiver);
        mContext.unregisterReceiver(mStopSearchingForNewDevicesReceiver);
    }

    private void pairingChanged(long deviceId, boolean paired) {
        // first, check for the location devices.
        DevicesDatabaseManager devicesDatabaseManager = DevicesDatabaseManager.getInstance(mContext);
        long gpsDeviceId = devicesDatabaseManager.getSpeedAndLocationGPSDeviceId();
        if (deviceId == gpsDeviceId) {
            if (paired && mSpeedAndLocationDevice_GPS == null // paired and not yet there -> create (if we have the permission)
                    && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                mSpeedAndLocationDevice_GPS = new SpeedAndLocationDevice_GPS(mContext, mSensorManager);
            }
            else {                                           // destroy
                if (mSpeedAndLocationDevice_GPS != null) {   // if it exists
                    mSpeedAndLocationDevice_GPS.shutDown();
                    mSpeedAndLocationDevice_GPS = null;
                }
            }
            return;
        }

        long fusedDeviceId = devicesDatabaseManager.getSpeedAndLocationGoogleFusedDeviceId();
        if (deviceId == fusedDeviceId) {
            if (paired && mSpeedAndLocationDevice_GoogleFused == null // if it does not exist
                    && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)) { // and we have the permission to do so
                mSpeedAndLocationDevice_GoogleFused = new SpeedAndLocationDevice_GoogleFused(mContext, mSensorManager);
            }
            else {                                           // destroy
                if (mSpeedAndLocationDevice_GoogleFused != null) {   // if it exists
                    mSpeedAndLocationDevice_GoogleFused.shutDown();
                    mSpeedAndLocationDevice_GoogleFused = null;
                }
            }
            return;
        }
        long networkDeviceId = devicesDatabaseManager.getSpeedAndLocationNetworkDeviceId();
        if (deviceId == networkDeviceId) {
            if (paired && mSpeedAndLocationDevice_Network == null  // if it does not exist
                    && TrainingApplication.havePermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {  // and we have the permission to do so
                mSpeedAndLocationDevice_Network = new SpeedAndLocationDevice_Network(mContext, mSensorManager);
            }
            else {                                           // destroy
                if (mSpeedAndLocationDevice_Network != null) {   // if it exists
                    mSpeedAndLocationDevice_Network.shutDown();
                    mSpeedAndLocationDevice_Network = null;
                }
            }
            return;
        }


        if (mMyRemoteDevices.containsKey(deviceId)) {
            if (paired) {  // mMyRemoteDevices should contain only paired devices, so this should never ever happen!
                Log.d(TAG, "BUG: an already paired device became paired");
            } else {
                removeAndStopRemoteDevice(deviceId);
            }
        } else { // device not yet in map
            if (!paired) {
                Log.d(TAG, "BUG: an unpaired device became unpaired");
            } else {
                MyRemoteDevice myRemoteDevice = createRemoteDevice(deviceId);
                if (myRemoteDevice != null) {
                    mMyRemoteDeviceTries.put(myRemoteDevice, 1);   // 1 should be sufficient here.
                    if (!isSearchingForARemoteDevice()) {
                        searchForNextRemoteDevice();
                    }
                }
            }
        }
    }

    protected void searchForNextRemoteDevice() {
        if (DEBUG) Log.d(TAG, "searchForNextRemoteDevice");

        if (mSearchQueue.isEmpty()) {
            if (DEBUG) Log.d(TAG, "empty search queue");
            cMyRemoteDeviceCurrentlySearchingFor = null;
            // finished searching, broadcast this
            mContext.sendBroadcast(new Intent(BANALService.SEARCHING_FINISHED_FOR_ALL_INTENT)
                    .setPackage(mContext.getPackageName()));
        } else {
            cMyRemoteDeviceCurrentlySearchingFor = mSearchQueue.pollFirst();
            if (cMyRemoteDeviceCurrentlySearchingFor.isSearching()) {
                // the device is still searching => probably something went wrong
                Log.d(TAG, "BUG: should start searching for an already searching device");
            } else if (cMyRemoteDeviceCurrentlySearchingFor.isReceivingData()) { // if the device is already receiving data, we do not have to search for it
                if (DEBUG) Log.i(TAG, "device is already receiving data, so we continue");
                searchForNextRemoteDevice();
            } else {

                BSportType bSportType = mBanalService.getUserSelectedBSportType();

                // TODO: We should always search for BSportType.UNKNOWN devices!
                if (!TrainingApplication.searchOnlyForSportSpecificDevices()                                       // either, the sport type is ignored
                        || bSportType == null || bSportType == UNKNOWN                                              // or the sport type is not yet defined
                        || bSportType == cMyRemoteDeviceCurrentlySearchingFor.getDeviceType().getSportType()) {     // or it is the correct sport type
                    if (DEBUG)
                        Log.i(TAG, "starting to search for the device at the head of the queue");
                    cMyRemoteDeviceCurrentlySearchingFor.startSearching();                                          // then, we start searching for this device.
                } else {
                    if (DEBUG)
                        Log.i(TAG, "Device type at the head of the search queue does not fit to the user selected sport type => this device will be ignored");
                    searchForNextRemoteDevice();                                                                    // Otherwise, we search for the next device.
                }
            }
        }
    }

    public MyRemoteDevice getCurrentlySearchingForRemoteDevice() {
        return cMyRemoteDeviceCurrentlySearchingFor;
    }

    public String getNameOfSearchingDevice() {
        return cMyRemoteDeviceCurrentlySearchingFor == null ? null : cMyRemoteDeviceCurrentlySearchingFor.getName();
    }

    public void startSearchForPairedDevices() {
        if (DEBUG) Log.i(TAG, "startSearchForPairedDevices()");

        if (isSearchingForARemoteDevice()) {
            if (DEBUG)
                Log.i(TAG, "in startSearchForPairedDevices(): already searching => stop this search");
            return;  //immediately return when we are already searching
        }


        // first, save all existing devices
        LinkedList<MyRemoteDevice> existingRemoteDevices = getRemoteDeviceList();

        // create all paired devices and add them to the search queue (thereby existing devices will not be recreated)
        createPairedRemoteDevices(Protocol.ANT_PLUS);  // TODO: first check whether ANT_PLUS is available?
        createPairedRemoteDevices(Protocol.BLUETOOTH_LE); // TODO: check whether BT is available,
        if (DEBUG) Log.i(TAG, "in startSearchForPairedDevices(): added all paired remote devices");

        // copy the existing remote devices to the head of the search queue
        // we want to search for existing devices first
        // they were already available
        for (MyRemoteDevice myRemoteDevice : existingRemoteDevices) {
            if (DEBUG)
                Log.i(TAG, "adding existing device with ID " + myRemoteDevice.getDeviceId() + " to the head of the search queue");
            mSearchQueue.addFirst(myRemoteDevice);
        }

        // give all devices the same number of chances ...
        mMyRemoteDeviceTries = new HashMap<>();
        for (MyRemoteDevice myRemoteDevice : mSearchQueue) {
            mMyRemoteDeviceTries.put(myRemoteDevice, TrainingApplication.getNumberOfSearchTries());
        }

        // now, start searching
        mContext.sendBroadcast(new Intent(BANALService.SEARCHING_STARTED_FOR_ALL_INTENT)
                .setPackage(mContext.getPackageName()));
        searchForNextRemoteDevice();
    }

    public BSportType getSportType() {
        if (DEBUG) Log.d(TAG, "getSportType");

        BSportType sportType = BSportType.UNKNOWN;

        for (MyRemoteDevice remoteDevice : getRemoteDeviceList()) {
            if (remoteDevice.isReceivingData()) {
                sportType = sportType.or(remoteDevice.getDeviceType().getSportType());
            }
        }

        if (DEBUG) Log.d(TAG, "getSportType: returning " + sportType);

        return sportType;
    }

    public String getMainSensorStringValue(long deviceId) {
        MyRemoteDevice remoteDevice = mMyRemoteDevices.get(deviceId);
        return (remoteDevice == null) ? mContext.getString(R.string.NoData) : remoteDevice.getMainSensorStringValue();
    }

    public List<MyRemoteDevice> getActiveRemoteDevices() {
        List<MyRemoteDevice> result = new LinkedList<>();

        for (MyRemoteDevice remoteDevice : getRemoteDeviceList()) {
            if (remoteDevice.isReceivingData()) {
                result.add(remoteDevice);
            }
        }

        return result;
    }

    public List<SpeedAndLocationDevice> getActiveSpeedAndLocationDevices() {
        List<SpeedAndLocationDevice> result = new LinkedList<>();

        if (mSpeedAndLocationDevice_GPS != null) {
            result.add(mSpeedAndLocationDevice_GPS);
        }

        if (mSpeedAndLocationDevice_GoogleFused != null) {
            result.add(mSpeedAndLocationDevice_GoogleFused);
        }

        if (mSpeedAndLocationDevice_Network != null) {
            result.add(mSpeedAndLocationDevice_Network);
        }

        return result;
    }

    public List<MyDevice> getActiveDevicesForUI() {
        List<MyDevice> result = new LinkedList<>();

        result.addAll(getActiveRemoteDevices());
        result.addAll(getActiveSpeedAndLocationDevices());

        return result;
    }

    public List<Long> getDatabaseIdsOfActiveDevices() {
        List<Long> result = new LinkedList<Long>();

        for (MyRemoteDevice remoteDevice : getRemoteDeviceList()) {
            if (remoteDevice.isReceivingData()) {
                result.add(remoteDevice.getDeviceId());
            }
        }

        return result;
    }

    public List<Long> getDatabaseIdsOfActiveDevices(Protocol protocol, DeviceType deviceType) {
        if (DEBUG) Log.i(TAG, "getDatabaseIdsOfActiveDevices");


        List<Long> availableDevicesList = new LinkedList<>();

        for (MyRemoteDevice remoteDevice : getRemoteDeviceList()) {
            if ((protocol == Protocol.ALL || remoteDevice.getProtocol() == protocol)
                    && (deviceType == DeviceType.ALL || remoteDevice.getDeviceType() == deviceType)
                    && remoteDevice.isReceivingData()) {
                if (DEBUG) Log.i(TAG, "adding " + remoteDevice.getName());
                availableDevicesList.add(remoteDevice.getDeviceId());
            }
        }

        Set<Long> availableDevicesSet = new HashSet<>();
        availableDevicesSet.addAll(availableDevicesList);

        if (DEBUG) Log.i(TAG, "now, we add the found devices");
        for (long deviceId : mFoundDevices) {
            if (!availableDevicesSet.contains(deviceId)) { // not yet in the list, so we add it
                if (DEBUG) Log.i(TAG, "adding device Id " + deviceId);
                availableDevicesList.add(deviceId);
            }
        }

        return availableDevicesList;
    }

    public LinkedList<MyRemoteDevice> getRemoteDeviceList() {
        if (DEBUG) Log.d(TAG, "getRemoteDeviceList");

        LinkedList<MyRemoteDevice> list = new LinkedList<MyRemoteDevice>();
        list.addAll(mMyRemoteDevices.values());

        return list;
    }

    // TODO: not the best solution/approach
    protected List<MyDevice> getMyDeviceList() {
        if (DEBUG) Log.d(TAG, "getMyDeviceList");

        List<MyDevice> myDeviceList = new ArrayList<MyDevice>();

        myDeviceList.add(mClockDevice);
        if (mSpeedAndLocationDevice_GPS != null) {
            myDeviceList.add(mSpeedAndLocationDevice_GPS);
        }
        if (mSpeedAndLocationDevice_Network != null) {
            myDeviceList.add(mSpeedAndLocationDevice_Network);
        }
        if (mSpeedAndLocationDevice_GoogleFused != null) {
            myDeviceList.add(mSpeedAndLocationDevice_GoogleFused);
        }
        if (mAltitudeFromPressureDevice != null) {
            myDeviceList.add(mAltitudeFromPressureDevice);
        }
        myDeviceList.add(mVerticalSpeedAndSlopeDevice);
        myDeviceList.addAll(getRemoteDeviceList());

        return myDeviceList;
    }

    public MyDevice getMyDeviceByName(String deviceName) {
        if (deviceName == null) {
            return null;
        }

        for (MyDevice myDevice : getMyDeviceList()) {
            if (deviceName.equals(myDevice.getName())) {
                return myDevice;
            }
        }

        return null;
    }

    public void newLap() {
        if (DEBUG) Log.i(TAG, "newLap()");

        for (MyDevice myDevice : getMyDeviceList()) {
            myDevice.newLap();
        }
    }

    public List<MySensor> getSensorList(SensorType sensorType) {
        List<MySensor> mySensorList = new LinkedList<MySensor>();

        for (MyDevice myDevice : getMyDeviceList()) {
            MySensor mySensor = myDevice.getSensor(sensorType);
            if (mySensor != null) {
                mySensorList.add(mySensor);
            }
        }

        return mySensorList;
    }

    public List<MyAccumulatorSensor> getAccumulatorSensorList(SensorType sensorType) {
        List<MyAccumulatorSensor> myAccumulatorSensorList = new LinkedList<MyAccumulatorSensor>();

        for (MyDevice myDevice : getMyDeviceList()) {
            MySensor mySensor = myDevice.getSensor(sensorType);
            if (mySensor != null && mySensor instanceof MyAccumulatorSensor) {
                myAccumulatorSensorList.add((MyAccumulatorSensor) mySensor);
            }
        }

        return myAccumulatorSensorList;
    }

    protected void removeAndStopRemoteDevice(long deviceId) {
        if (DEBUG) Log.d(TAG, "removeAndStopRemoteDevice(" + deviceId + ")");

        MyRemoteDevice myRemoteDevice = mMyRemoteDevices.get(deviceId);

        mSearchQueue.remove(myRemoteDevice);
        if (cMyRemoteDeviceCurrentlySearchingFor == myRemoteDevice) {
            searchForNextRemoteDevice();
        }
        mMyRemoteDevices.remove(deviceId);
        myRemoteDevice.shutDown();
    }

    public MyRemoteDevice createNewANTDevice(long deviceID, DeviceType deviceType, int antDeviceNumber) {
        if (DEBUG) Log.d(TAG, "createNewANTDevice: " + deviceType.name());

        if (mMyRemoteDevices.containsKey(deviceID)) {
            if (DEBUG) Log.d(TAG, "device already created");
            return null;
        }

        MyANTDevice myANTDevice = null;

        switch (deviceType) {
            case HRM:
                if (DEBUG) Log.d(TAG, "create new HR device");
                myANTDevice = new ANTHeartRateDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            case RUN_SPEED:
                if (DEBUG) Log.d(TAG, "create new RUN SPEED device");
                myANTDevice = new ANTRunSpeedDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            case BIKE_SPEED:
                if (DEBUG) Log.d(TAG, "create new BIKE SPEED device");
                myANTDevice = new ANTBikeSpeedDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            case BIKE_CADENCE:
                if (DEBUG) Log.d(TAG, "create new BIKE CADENCE device");
                myANTDevice = new ANTBikeCadenceDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            case BIKE_SPEED_AND_CADENCE:
                if (DEBUG)
                    Log.d(TAG, "creating combined BikeSpeedAndCadence device => speed and cadence");
                // MyANTDevice device = new ANTBikeCadenceDevice(mContext, mSensorManager, deviceID, true);
                // device.startSearching();
                // mMyANTDeviceMap.put(deviceIDString + "-combined", device);
                myANTDevice = new ANTBikeSpeedAndCadenceDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            case BIKE_POWER:
                if (DEBUG) Log.d(TAG, "create new BIKE POWER device");
                myANTDevice = new ANTBikePowerDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            case ENVIRONMENT:
                if (DEBUG) Log.d(TAG, "create new ENVIRONMENT device");
                myANTDevice = new ANTEnvironmentDevice(mContext, mSensorManager, deviceID, antDeviceNumber);
                break;
            default:
                if (DEBUG) Log.d(TAG, "ANT device not supported");
        }

        if (myANTDevice != null) {
            mMyRemoteDevices.put(deviceID, myANTDevice);
            mSearchQueue.addFirst(myANTDevice);
        }

        return myANTDevice;
    }

    public MyRemoteDevice createNewBluetoothLEDevice(long deviceID, DeviceType deviceType, String address) {
        if (DEBUG) Log.d(TAG, "createNewBluetoothDevice: " + deviceType.name());

        if (mMyRemoteDevices.containsKey(deviceID)) {
            if (DEBUG) Log.d(TAG, "device already created");
            return null;
        }

        MyBTLEDevice myBTLEDevice = null;

        switch (deviceType) {
            case HRM:
                if (DEBUG) Log.d(TAG, "create new HR device");
                myBTLEDevice = new BTLEHeartRateDevice(mContext, mSensorManager, deviceID, address);
                break;

            case RUN_SPEED:
                if (DEBUG) Log.d(TAG, "create new bluetooth run speed device");
                myBTLEDevice = new BTLERunSpeedDevice(mContext, mSensorManager, deviceID, address);
                break;

            case BIKE_SPEED:
                if (DEBUG) Log.i(TAG, "create new bluetooth bike speed device");
                myBTLEDevice = new BTLEBikeSpeedDevice(mContext, mSensorManager, deviceID, address);
                break;

            case BIKE_CADENCE:
                if (DEBUG) Log.i(TAG, "create new bluetooth bike cadence device");
                myBTLEDevice = new BTLEBikeCadenceDevice(mContext, mSensorManager, deviceID, address);
                break;

            case BIKE_SPEED_AND_CADENCE:
                if (DEBUG) Log.i(TAG, "create new bluetooth bike speed and cadence device");
                myBTLEDevice = new BTLEBikeSpeedAndCadenceDevice(mContext, mSensorManager, deviceID, address);
                break;

            case BIKE_POWER:
                if (DEBUG) Log.i(TAG, "create new bluetooth bike power device");
                myBTLEDevice = new BTLEBikePowerDevice(mContext, mSensorManager, deviceID, address);
                break;

            default:
                Log.i(TAG, "Bluetooth device not yet supported");
        }

        if (myBTLEDevice != null) {
            mMyRemoteDevices.put(deviceID, myBTLEDevice);
            mSearchQueue.addFirst(myBTLEDevice);
        }

        return myBTLEDevice;
    }

    // TODO: code is very similar to createPairedRemoteDevices, so the code might be merged?
    protected MyRemoteDevice createRemoteDevice(long deviceId) {
        Protocol protocol = mDevicesDatabaseManager.getProtocol(deviceId);
        DeviceType deviceType = mDevicesDatabaseManager.getDeviceType(deviceId);
        MyRemoteDevice myRemoteDevice = null;

        switch (protocol) {
            case ANT_PLUS:
                int antDeviceNumber = mDevicesDatabaseManager.getAntDeviceNumber(deviceId);
                myRemoteDevice = createNewANTDevice(deviceId, deviceType, antDeviceNumber);
                break;
            case BLUETOOTH_LE:
                String address = mDevicesDatabaseManager.getBluetoothMACAddress(deviceId);
                myRemoteDevice = createNewBluetoothLEDevice(deviceId, deviceType, address);
                break;
        }

        return myRemoteDevice;
    }

    public void createPairedRemoteDevices(Protocol protocol) {
        if (DEBUG) Log.d(TAG, "createPairedRemoteDevices()");

        SQLiteDatabase db = mDevicesDatabaseManager.getDatabase();

        // TODO: sort according to the LAST_ACTIVE field?
        Cursor cursor = db.query(DevicesDbHelper.DEVICES,
                new String[]{DevicesDbHelper.C_ID, DevicesDbHelper.PAIRED, DevicesDbHelper.DEVICE_TYPE, DevicesDbHelper.ANT_DEVICE_NUMBER, DevicesDbHelper.BT_ADDRESS},
                DevicesDbHelper.PROTOCOL + "=?",
                new String[]{protocol.name()},
                null,
                null,
                null);

        while (cursor.moveToNext()) {
            if (cursor.getInt(cursor.getColumnIndexOrThrow(DevicesDbHelper.PAIRED)) > 0) {

                long deviceID = cursor.getLong(cursor.getColumnIndex(DevicesDbHelper.C_ID));
                if (mMyRemoteDevices.containsKey(deviceID)) {
                    if (DEBUG) Log.i(TAG, "device with ID " + deviceID + " is already created");
                    continue;
                }
                if (DEBUG) Log.d(TAG, "create new device with ID: " + deviceID);

                DeviceType deviceType = DeviceType.valueOf(cursor.getString(cursor.getColumnIndex(DevicesDbHelper.DEVICE_TYPE)));

                switch (protocol) {
                    case ANT_PLUS:
                        int antDeviceNumber = cursor.getInt(cursor.getColumnIndex(DevicesDbHelper.ANT_DEVICE_NUMBER));
                        createNewANTDevice(deviceID, deviceType, antDeviceNumber);
                        break;

                    case BLUETOOTH_LE:
                        String address = cursor.getString(cursor.getColumnIndex(DevicesDbHelper.BT_ADDRESS));
                        createNewBluetoothLEDevice(deviceID, deviceType, address);
                        break;
                }
            } else {
                if (DEBUG) Log.d(TAG, "createPairedDevices(): ignoring unpaired device");
            }
        }
        cursor.close();

        if (DEBUG) Log.d(TAG, "finished createPairedDevices");
    }

    public void startSearchForNewRemoteDevices(Protocol protocol, DeviceType deviceType) {
        if (DEBUG) Log.i(TAG, "startSearchForNewRemoteDevices");

        mFoundDevices.clear();

        switch (protocol) {
            case ANT_PLUS:

                if (mAntAsyncSearchEngine != null) {
                    mAntAsyncSearchEngine.stopAsyncSearch();
                }

                mAntAsyncSearchEngine = new ANTSearchForNewDevicesEngineMultiDeviceSearch(mContext, deviceType, mANTAsyncSearchEngineInterface);
                mAntAsyncSearchEngine.startAsyncSearch();

                break;

            case BLUETOOTH_LE:

                if (mBTSearchForNewDevicesEngine != null) {
                    if (DEBUG) Log.i(TAG, "stopping previous asyncSearchEngine");
                    mBTSearchForNewDevicesEngine.stopAsyncSearch();
                }

                mBTSearchForNewDevicesEngine = new BTSearchForNewDevicesEngine(mContext, deviceType, mBTSearchInterface);
                mBTSearchForNewDevicesEngine.startAsyncSearch();

                break;
        }
    }

    public void stopSearchForNewRemoteDevices() {
        if (DEBUG) Log.i(TAG, "stopSearchForNewRemoteDevices");

        mFoundDevices.clear();

        // ANT_PLUS:
        if (mAntAsyncSearchEngine != null) {
            mAntAsyncSearchEngine.stopAsyncSearch();
            mAntAsyncSearchEngine = null;
        }

        // BLUETOOTH_LE:
        if (mBTSearchForNewDevicesEngine != null) {
            mBTSearchForNewDevicesEngine.stopAsyncSearch();
            mBTSearchForNewDevicesEngine = null;
        }
    }

    protected void sendNewDeviceFoundBroadcast(long deviceId) {
        if (DEBUG) Log.i(TAG, "sendNewDeviceFountBroadcast");

        Intent intent = new Intent(BANALService.NEW_DEVICE_FOUND_INTENT)
                .putExtra(BANALService.DEVICE_ID, deviceId)
                .setPackage(mContext.getPackageName());
        mContext.sendBroadcast(intent);
    }

}
