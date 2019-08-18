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

package com.atrainingtracker.banalservice;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.atrainingtracker.banalservice.devices.DeviceManager;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.devices.MyRemoteDevice;
import com.atrainingtracker.banalservice.sensor.MyAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorData;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.banalservice.filters.FilterData;
import com.atrainingtracker.banalservice.filters.FilterManager;
import com.atrainingtracker.banalservice.filters.FilteredSensorData;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static com.atrainingtracker.trainingtracker.TrainingApplication.REQUEST_NEW_LAP;
import static com.atrainingtracker.trainingtracker.TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES;

public class BANALService
        extends Service {
    public static final boolean DEBUG = false;

    /**
     * the Log TAG
     */
    public static final String TAG = "BANALService";
    public static final double METER_PER_MILE = 1609.344;

    public static final double DEFAULT_BIKE_CALIBRATION_FACTOR = 2.1;
    public static final double MIN_SPEED = 0.001;
    public static final int MAX_PACE = 10;

    // TODO: reorganize: All Strings/Intents that are used globally, must be moved to TrainingApplication.
    // TODO: only those, that are only used within BANALService stay here
    //
    // TODO: work with Broadcasts (and helper methods to send these broadcasts -> TrainingApplication.requestStartTracking())
    // TODO: naming: prefix REQUEST when initiated by the user...

    // TODO: super strange/complex might be solved later


    // TODO: rename them because they have nothing to do with tracking???
    // public static final String START_TRACKING_INTENT             = "com.atrainingtracker.banalservice.START_TRACKING_INTENT";
    // public static final String PAUSE_TRACKING_INTENT             = "com.atrainingtracker.banalservice.PAUSE_TRACKING_INTENT";
    // public static final String RESUME_TRACKING_INTENT            = "com.atrainingtracker.banalservice.RESUME_TRACKING_INTENT";
    public static final String SPORT_TYPE_CHANGED_BY_USER_INTENT = "com.atrainingtracker.banalservice.SPORT_TYPE_CHANGED_BY_USER_INTENT";

    public static final String LAP_SUMMARY = "com.atrainingtracker.banalservice.LAP_SUMMARY";
    public static final String PREV_LAP_NR = "com.atrainingtracker.banalservice.PREV_LAP_NR";
    public static final String PREV_LAP_TIME_S = "com.atrainingtracker.banalservice.PREV_LAP_TIME_S";
    public static final String PREV_LAP_TIME_STRING = "com.atrainingtracker.banalservice.PREV_LAP_TIME_STRING";
    public static final String PREV_LAP_DISTANCE_m = "com.atrainingtracker.banalservice.PREV_LAP_DISTANCE_m";
    public static final String PREV_LAP_DISTANCE_STRING = "com.atrainingtracker.banalservice.PREV_LAP_DISTANCE_STRING";
    public static final String PREV_LAP_SPEED_mps = "com.atrainingtracker.banalservice.PREV_LAP_SPEED_mps";
    public static final String PREV_LAP_SPEED_STRING = "com.atrainingtracker.banalservice.PREV_LAP_SPEED_STRING";

    public static final String NEW_TIME_EVENT_INTENT = "com.atrainingtracker.banalservice.NEW_TIME_EVENT_INTENT";
    // public static final String START_TIMER_INTENT    = "de.rainerblind.banalservice.START_TIMER_INTENT";
    // public static final String STOP_TIMER_INTENT     = "de.rainerblind.banalservice.STOP_TIMER_INTENT";

    public static final String RESET_ACCUMULATORS_INTENT = "com.atrainingtracker.banalservice.RESET_ACCUMULATORS_INTENT";

    public static final String PAIRING_CHANGED = "com.atrainingtracker.banalservice.PAIRING_CHANGED";
    public static final String PAIRED = "com.atrainingtracker.banalservice.PAIRED";

    public static final String SENSORS_CHANGED = "com.atrainingtracker.banalservice.SENSORS_CHANGED";

    public static final String LOCATION_AVAILABLE_INTENT = "com.atrainingtracker.banalservice.GPS_AVAILABLE_INTENT";
    public static final String LOCATION_UNAVAILABLE_INTENT = "com.atrainingtracker.banalservice.GPS_UNAVAILABLE_INTENT";
    public static final String NEW_LOCATION_INTENT = "com.atrainingtracker.banalservice.NEW_LOCATION_INTENT";
    public static final String LOCATION_PROVIDER = "com.atrainingtracker.banalservice.LOCATION_PROVIDER";
    public static final String LATITUDE = "com.atrainingtracker.banalservice.LATITUDE";
    public static final String LONGITUDE = "com.atrainingtracker.banalservice.LONGITUDE";

    // public static final String START_SEARCHING_INTENT                 = "com.trainingtracker.banalservice.START_SEARCHING_INTENT";
    // now, via REQUEST_SEARCH_FOR_PAIRED_DEVICES (or similar)

    public static final String START_SEARCHING_FOR_NEW_DEVICES_INTENT = "com.trainingtracker.banalservice.START_SEARCHING_FOR_NEW_DEVICES_INTENT";
    public static final String STOP_SEARCHING_FOR_NEW_DEVICES_INTENT = "com.trainingtracker.banalservice.STOP_SEARCHING_FOR_NEW_DEVICES_INTENT";
    public static final String NEW_DEVICE_FOUND_INTENT = "com.trainingtracker.banalservice.NEW_DEVICE_FOUND_INTENT";  // send when a non paired device is found!

    public static final String SEARCHING_STARTED_FOR_ONE_INTENT = "com.trainingtracker.banalservice.SEARCHING_STARTED_FOR_ONE_INTENT";
    public static final String DEVICE_TYPE = "com.trainingtracker.banalservice.DEVICE_TYPE";
    public static final String DEVICE_ID = "com.trainingtracker.banalservice.DEVICE_ID";
    public static final String DEVICE_NAME = "com.trainingtracker.banalservice.DEVICE_NAME";
    public static final String PROTOCOL = "com.trainingtracker.banalservice.PROTOCOL";

    public static final String SEARCHING_STOPPED_FOR_ONE_INTENT = "com.trainingtracker.banalservice.SEARCHING_STOPPED_FOR_ONE_INTENT";
    public static final String SEARCHING_FINISHED_SUCCESS = "com.trainingtracker.banalservcie.SEARCHING_FINISHED_SUCCESS";

    public static final String SEARCHING_STARTED_FOR_ALL_INTENT = "com.trainingtracker.banalservice.SEARCHING_STARTED_FOR_ALL_INTENT";
    public static final String SEARCHING_FINISHED_FOR_ALL_INTENT = "com.trainingtracker.banalservice.SEARCHING_FINISHED_FOR_ALL_INTENT";

    public static final String CALIBRATION_FACTOR_CHANGED = "com.trainingtracker.banalservice.CALIBRATION_FACTOR_CHANGED";
    public static final String CALIBRATION_FACTOR = "com.trainingtracker.banalservice.CALIBRATION_FACTOR";

    public static final String URI_ANT_RADIO_SERVICE = "com.dsi.ant.service.socket";
    public static final String URI_ANT_USB_SERVICE = "com.dsi.ant.usbservice";

    public static final int INIT_LAP_NR = 1;
    private static DeviceManager cDeviceManager;
    private static MySensorManager cSensorManager;
    private static FilterManager cFilterManager;
    protected ActivityType mActivityTypeMax = ActivityType.GENERIC;
    /***********************************************************************************************/

    protected BroadcastReceiver mStartSearchForPairedDevices = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            startSearchForPairedDevices();
        }
    };
    protected BroadcastReceiver mNewLapReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            newLap();
        }
    };
    private long mUserSelectedSportTypeId = -1;
    private boolean mHaveUserSelectedSportType = false;

    public static long getDefaultSportTypeId() {
        return SportTypeDatabaseManager.getDefaultSportTypeId();
    }

    public static boolean isANTPluginServiceInstalled(Context context) {
        if (DEBUG)
            Log.d(TAG, "plugin Service Version: " + AntPluginPcc.getInstalledPluginsVersionString(context));
        return AntPluginPcc.getInstalledPluginsVersionString(context) != null;
    }

    public static boolean isANTRadioServiceInstalled() {
        return TrainingApplication.isAppInstalled(URI_ANT_RADIO_SERVICE);
    }

    public static boolean isANTUSBServiceInstalled() {
        return TrainingApplication.isAppInstalled(URI_ANT_USB_SERVICE);
    }

    public static boolean hasUsbHostFeature(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
    }

    public static boolean isANTProperlyInstalled(Context context) {
        return (!isANTPluginServiceInstalled(context)
                | !isANTRadioServiceInstalled()
                | !hasUsbHostFeature(context) & isANTUSBServiceInstalled());
    }

    public static boolean isProtocolSupported(Context context, Protocol protocol) {
        switch (protocol) {
            case ANT_PLUS:
                return isANTPluginServiceInstalled(context); // TODO: more precise test possible?

            case BLUETOOTH_LE:
                return (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)
                        && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);

            default:
                return false;
        }
    }

    /* filtered stuff */
    protected static void createFilter(FilterData filterData) {
        cFilterManager.createFilter(filterData);
    }

    protected static FilteredSensorData getFilteredSensorData(FilterData filterData) {
        return cFilterManager.getFilteredSensorData(filterData);
    }

    protected static List<FilteredSensorData> getAllFilteredSensorData() {
        return cFilterManager.getAllFilteredSensorData();
    }

    /* non-filtered stuff */  // outdated?
    protected static SensorData getSensorData(SensorType sensorType) {
        if (DEBUG) Log.d(TAG, "getBestSensorData(" + sensorType.name() + ")");

        MySensor sensor = cSensorManager.getSensor(sensorType);
        return sensor == null ? null : sensor.getSensorData();
    }

    public static boolean isSearching() {
        if (DEBUG)
            Log.d(TAG, "isSearching(): returning " + DeviceManager.isSearchingForARemoteDevice());

        return DeviceManager.isSearchingForARemoteDevice();
    }

    public static DeviceManager getDeviceManager() {
        return cDeviceManager;
    }

    public static MySensorManager getSensorManager() {
        return cSensorManager;
    }

    public static List<MySensor> getSensorList(SensorType sensorType) {
        return cDeviceManager.getSensorList(sensorType);
    }

    public static List<MyAccumulatorSensor> getAccumulatorSensorList(SensorType sensorType) {
        return cDeviceManager.getAccumulatorSensorList(sensorType);
    }

    public static Object getInitialValue(SensorType sensorType) {
        return cSensorManager.getInitialValue(sensorType);
    }

    public static void setInitialSensorValue(SensorType sensorType, Double initialValue) {
        if (DEBUG)
            Log.d(TAG, "setInitialSensorValue: sensorType=" + sensorType + ", value=" + initialValue);
        cSensorManager.setInitialValue(sensorType, initialValue);
        for (MyAccumulatorSensor<Double> myAccumulatorSensor : getAccumulatorSensorList(sensorType)) {
            if (DEBUG) Log.d(TAG, "initializing Sensor " + myAccumulatorSensor);
            myAccumulatorSensor.setInitialValue(initialValue);
        }
    }

    public static void setInitialSensorValue(SensorType sensorType, Integer initialValue) {
        if (DEBUG)
            Log.d(TAG, "setInitialSensorValue: sensorType=" + sensorType + ", value=" + initialValue);
        cSensorManager.setInitialValue(sensorType, initialValue);
        for (MyAccumulatorSensor<Integer> myAccumulatorSensor : getAccumulatorSensorList(sensorType)) {
            if (DEBUG) Log.d(TAG, "initializing Sensor " + myAccumulatorSensor);
            myAccumulatorSensor.setInitialValue(initialValue);
        }
    }

    private void setUserSelectedSportTypeId(long sportTypeId) {
        mHaveUserSelectedSportType = true;
        mUserSelectedSportTypeId = sportTypeId;
        sendBroadcast(new Intent(SPORT_TYPE_CHANGED_BY_USER_INTENT));
        if (TrainingApplication.startSearchWhenUserChangesSport()) {
            sendBroadcast(new Intent(TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES));
        }
    }

    public BSportType getUserSelectedBSportType() {
        return mHaveUserSelectedSportType ? SportTypeDatabaseManager.getBSportType(mUserSelectedSportTypeId) : null;
    }

    private void setUserSelectedBSportType(BSportType bSportType) {
        setUserSelectedSportTypeId(SportTypeDatabaseManager.getSportTypeId(bSportType));
    }

    public BSportType getBSportType() {
        if (mHaveUserSelectedSportType) {
            return SportTypeDatabaseManager.getBSportType(mUserSelectedSportTypeId);
        } else {
            return cDeviceManager.getSportType();
        }
    }

    public long getSportTypeId() {
        if (mHaveUserSelectedSportType) {
            return mUserSelectedSportTypeId;
        } else {
            return SportTypeDatabaseManager.getSportTypeId(cDeviceManager.getSportType());
        }
    }

    public long getSportTypeId(double avgSpd) {
        if (mHaveUserSelectedSportType) {
            return mUserSelectedSportTypeId;
        } else {
            List<Long> sportTypeIds = SportTypeDatabaseManager.getSportTypesIdList(cDeviceManager.getSportType(), avgSpd);
            return sportTypeIds.get(0);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new BANALServiceComm();
    }

    protected void startSearchForPairedDevices() {
        cDeviceManager.startSearchForPairedDevices();
    }

    protected void newLap() {
        if (DEBUG) Log.i(TAG, "newLap");

        // if (cPaused == true) { return; }

        if (TrainingApplication.startSearchWhenNewLap()) {
            sendBroadcast(new Intent(TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES));
        }

        SensorData sensorData;

        int prevLapNr = 0;
        sensorData = getSensorData(SensorType.LAP_NR);
        if (sensorData != null) {
            prevLapNr = (Integer) sensorData.getValue();
        }

        int lapTime_s = 0;
        String lapTime = "??:??:??";
        sensorData = getSensorData(SensorType.TIME_LAP);
        if (sensorData != null && sensorData.getValue() != null) {
            lapTime_s = (Integer) sensorData.getValue();
            lapTime = sensorData.getStringValue();
        }

        double lapDistance = 0.0;
        String lapDistance_String = "0";
        sensorData = getSensorData(SensorType.DISTANCE_m_LAP);
        if (sensorData != null && sensorData.getValue() != null) {
            lapDistance = (Double) sensorData.getValue();
            lapDistance_String = sensorData.getStringValue();
        }

        double lapSpeed = lapDistance / lapTime_s;
        String lapSpeedString = SensorType.SPEED_mps.getMyFormatter().format(lapSpeed);

        cDeviceManager.newLap();

        // send broadcast with these values
        Intent intent = new Intent(BANALService.LAP_SUMMARY);
        intent.putExtra(BANALService.PREV_LAP_NR, prevLapNr);
        intent.putExtra(PREV_LAP_TIME_S, lapTime_s);
        intent.putExtra(PREV_LAP_TIME_STRING, lapTime);
        intent.putExtra(PREV_LAP_DISTANCE_m, lapDistance);
        intent.putExtra(PREV_LAP_DISTANCE_STRING, lapDistance_String);
        intent.putExtra(PREV_LAP_SPEED_mps, lapSpeed);
        intent.putExtra(PREV_LAP_SPEED_STRING, lapSpeedString);
        sendBroadcast(intent);
    }

    protected SensorType[] getSensorTypes()  // TODO: also change to Set?
    {
        if (DEBUG) Log.d(TAG, "getSensorTypes()");

        ArrayList<SensorType> sensorTypeArrayList = new ArrayList<SensorType>();
        for (MySensor mySensor : cSensorManager.getSensors()) {
            sensorTypeArrayList.add(mySensor.getSensorType());
        }

        return sensorTypeArrayList.toArray(new SensorType[]{});
    }

    protected Set<SensorType> getAccumulatedSensorTypeSet() {
        return cSensorManager.getAccumulatedSensorTypeSet();
    }

    protected String getMainSensorStringValue(long deviceID) {
        return cDeviceManager.getMainSensorStringValue(deviceID);
    }

    protected ActivityType getActivityType() {
        if (DEBUG) Log.d(TAG, "getActivityType");

        ActivityType result = ActivityType.GENERIC;

        Set<SensorType> sensorTypes = EnumSet.copyOf(Arrays.asList(getSensorTypes()));
        BSportType sportType = getBSportType();

        switch (sportType) {
            case RUN:
                if (sensorTypes.contains(SensorType.CADENCE)) {
                    result = ActivityType.RUN_SPEED_AND_CADENCE;
                } else {
                    result = ActivityType.RUN_SPEED;
                }
                break;

            case BIKE:
                if (sensorTypes.contains(SensorType.POWER)) {
                    result = ActivityType.BIKE_POWER;
                } else if (sensorTypes.contains(SensorType.CADENCE)) {
                    result = ActivityType.BIKE_SPEED_AND_CADENCE;
                } else {
                    result = ActivityType.BIKE_SPEED;
                }
                break;

            default:
                if (sensorTypes.contains(SensorType.HR)) {
                    result = ActivityType.GENERIC_HR;
                } else {
                    result = ActivityType.GENERIC;
                }
                break;
        }

        if (result.ordinal() > mActivityTypeMax.ordinal()) {
            mActivityTypeMax = result;
        }

        if (!TrainingApplication.changeSportWhenDeviceGetsLost()) {
            result = mActivityTypeMax;
        }

        if (DEBUG) Log.d(TAG, "getActivityType: returning " + result);
        return result;
    }

    protected List<SensorData> getBestSensorData()  // get the SensorData from the SensorListenersSensors, i.e., the 'best' values
    {
        if (DEBUG) Log.d(TAG, "getBestSensorData()");

        LinkedList<SensorData> sensorDataList = new LinkedList<>();
        for (MySensor mySensor : cSensorManager.getSensors()) {  // gets the ProxySensors
            sensorDataList.add(mySensor.getSensorData());
        }

        return sensorDataList;
    }

    protected List<SensorData> getAllButBestSensorData()  // get the SensorData from all but the best Sensors.  Maybe the best sensors are handled special?
    {
        LinkedList<SensorData> sensorDataLinkedList = new LinkedList<>();
        for (MySensor mySensor : cSensorManager.getAllButBestSensors()) {
            sensorDataLinkedList.add(mySensor.getSensorData());
        }

        return sensorDataLinkedList;
    }

    protected List<SensorData> getAllSensorData()  // get the SensorData from all Sensors
    {
        LinkedList<SensorData> sensorDataLinkedList = new LinkedList<>();
        for (MySensor mySensor : cSensorManager.getAllSensors()) {
            sensorDataLinkedList.add(mySensor.getSensorData());
        }

        return sensorDataLinkedList;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "onCreate");

        cSensorManager = new MySensorManager(this);
        cDeviceManager = new DeviceManager(this, cSensorManager);
        cFilterManager = new FilterManager(this, cDeviceManager, cSensorManager);

        registerReceiver(mStartSearchForPairedDevices, new IntentFilter(REQUEST_START_SEARCH_FOR_PAIRED_DEVICES));
        registerReceiver(mNewLapReceiver, new IntentFilter(REQUEST_NEW_LAP));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.d(TAG, "onDestroy");

        // cANTPlusPlusInterface.shutDown();
        cDeviceManager.shutDown();
        cSensorManager.shutDown();
        cFilterManager.shutDown();

        unregisterReceiver(mNewLapReceiver);
        unregisterReceiver(mStartSearchForPairedDevices);
    }

    /***********************************************************************************************/
    /* Broadcast Receivers                                                                         */

    /**
     * should be implemented by an Activity with a connection to the BANALService that wants to share this connection
     * with its Fragments
     */
    public interface GetBanalServiceInterface {
        void registerConnectionStatusListener(ConnectionStatusListener connectionStatusListener);

        BANALServiceComm getBanalServiceComm();

        // corresponding broadcasts should be send by the class that implements this interface
        // TODO: but there must be only one class that sends this broadcast???
        // maybe we need the listeners approach here!!!
        // String CONNECTED_TO_BANALSERVICE      = "com.atrainingtracker.banalservice.BANALService.CONNECTED_TO_BANALSERVICE";
        // String DISCONNECTED_FROM_BANALSERVICE = "com.atrainingtracker.banalservice.BANALService.DISCONNECTED_FROM_BANALSERVICE";
        interface ConnectionStatusListener {
            void connectedToBanalService();

            void disconnectedFromBanalService();
        }
    }

    public class BANALServiceComm extends Binder {

        // only when searching for paired device?
        public String getNameOfSearchingDevice() {
            return cDeviceManager.getNameOfSearchingDevice();
        }

        public void startSearchForNewRemoteDevices(Protocol protocol, DeviceType deviceType) {
            cDeviceManager.startSearchForNewRemoteDevices(protocol, deviceType);
        }

        public void stopSearchForNewRemoteDevices() {
            cDeviceManager.stopSearchForNewRemoteDevices();
        }


        public SensorType[] getSensorTypes() {
            return BANALService.this.getSensorTypes();
        }

        public Set<SensorType> getAccumulatedSensorTypeSet() {
            return BANALService.this.getAccumulatedSensorTypeSet();
        }

        public List<SensorData> getBestSensorData() {
            return BANALService.this.getBestSensorData();
        }

        public List<SensorData> getAllSensorData() {
            return BANALService.this.getAllSensorData();
        }

        public List<SensorData> getAllButBestSensorData() {
            return BANALService.this.getAllButBestSensorData();
        }

        public void createFilter(FilterData filterData) {
            BANALService.createFilter(filterData);
        }

        public FilteredSensorData getFilteredSensorData(FilterData filterData) {
            return BANALService.getFilteredSensorData(filterData);
        }

        public List<FilteredSensorData> getAllFilteredSensorData() {
            return BANALService.getAllFilteredSensorData();
        }

        public SensorData getBestSensorData(SensorType sensorType) {
            return getSensorData(sensorType);
        }

        public String getMainSensorStringValue(long deviceID) {
            return BANALService.this.getMainSensorStringValue(deviceID);
        }

        public BSportType getBSportType() {
            return BANALService.this.getBSportType();
        }

        public long getSportTypeId() {
            return BANALService.this.getSportTypeId();
        }

        public long getSportTypeId(double avgSpd) {
            return BANALService.this.getSportTypeId(avgSpd);
        }

        public void setUserSelectedSportTypeId(long sportTypeId) {
            BANALService.this.setUserSelectedSportTypeId(sportTypeId);
        }

        public void setUserSelectedSportType(BSportType bSportType) {
            BANALService.this.setUserSelectedBSportType(bSportType);
        }


        public List<Long> getDatabaseIdsOfActiveDevices() {
            return cDeviceManager.getDatabaseIdsOfActiveDevices();
        }

        public List<Long> getDatabaseIdsOfActiveDevices(Protocol protocol, DeviceType deviceType) {
            return cDeviceManager.getDatabaseIdsOfActiveDevices(protocol, deviceType);
        }

        public List<MyRemoteDevice> getActiveRemoteDevices() {
            return cDeviceManager.getActiveRemoteDevices();
        }

        public ActivityType getActivityType() {
            return BANALService.this.getActivityType();
        }

        public String getAccumulatedGCDataString() {
            return cSensorManager.getSensor(SensorType.ACCUMULATED_SENSORS).getStringValue();
        }

        public String getGCDataString() {
            return cSensorManager.getSensor(SensorType.SENSORS).getStringValue();
        }
    }


}
