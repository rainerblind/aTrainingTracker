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

package com.atrainingtracker.trainingtracker.smartwatch.pebble;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.sensor.SensorData;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.MyUnits;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.getpebble.android.kit.Constants;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

// import com.getpebble.android.kit.Constants;


// TODO create super class for similar services???
public class PebbleServiceBuildIn extends Service {
    private static final String TAG = "PebbleService";
    private static final boolean DEBUG = false;
    protected final IntentFilter mSearchingFinishedFilter = new IntentFilter(BANALService.SEARCHING_FINISHED_FOR_ALL_INTENT);
    // BANAL-Connection banalConnection;
    protected final IntentFilter mUpdatePebbleFilter = new IntentFilter(BANALService.NEW_TIME_EVENT_INTENT);
    protected final IntentFilter mPebbleConnectedFilter = new IntentFilter("com.getpebble.action.PEBBLE_CONNECTED");
    // Pebble helpers
    protected BroadcastReceiver mPebbleConnectedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "pebble became connected");
            startPebbleWatchApp();
        }
    };
    boolean mShowPace = false;
    MyUnits mUnits = MyUnits.METRIC;
    // protected final IntentFilter mPauseFilter             = new IntentFilter(BANALService.PAUSE_INTENT);
    // protected final IntentFilter mResumeFilter            = new IntentFilter(BANALService.RESUME_INTENT);
    private BANALServiceComm banalService;
    private final BroadcastReceiver mUpdatePebbleReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "mUpdatePebbleReceiver.onReceive()");
            updatePebbleWatch();
        }
    };
    private final BroadcastReceiver mSearchingFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "searching finished");
            setShowPace();
            // TODO: inform the user that searching is finished?
        }
    };
    // class BANALConnection implements ServiceConnection
    private final ServiceConnection mBanalConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            banalService = (BANALServiceComm) service;
            if (DEBUG) Log.i(TAG, "connected to BANAL Service");
            setShowPace();
        }

        public void onServiceDisconnected(ComponentName name) {
            banalService = null;
            if (DEBUG) Log.i(TAG, "disconnected from BANAL Service");
        }
    };

    private void setShowPace() {
        mShowPace = (banalService.getBSportType() == BSportType.RUN);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) {
            Log.d(TAG, "onCreate");
        }

        mUnits = TrainingApplication.getUnit();

        // request bind to the BANAL Service
        bindService(new Intent(this, BANALService.class), mBanalConnection, Context.BIND_AUTO_CREATE);

        registerReceiver(mUpdatePebbleReceiver, mUpdatePebbleFilter);
        registerReceiver(mPebbleConnectedReceiver, mPebbleConnectedFilter);
        registerReceiver(mSearchingFinishedReceiver, mSearchingFinishedFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) {
            Log.d(TAG, "onStartCommand Received start id " + startId + ": " + intent);
        }
        super.onStartCommand(intent, flags, startId);

        startPebbleWatchApp();

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.d(TAG, "onDestroy");

        unbindService(mBanalConnection);
        banalService = null;

        unregisterReceiver(mUpdatePebbleReceiver);
        unregisterReceiver(mSearchingFinishedReceiver);

        stopPebbleWatchApp();
    }

    // Send a broadcast to launch the specified application on the connected Pebble
    protected void startPebbleWatchApp() {
        if (DEBUG) {
            Log.d(TAG, "startPebbleWatchApp");
        }

        // then we start the pebble Watchapp
        if (PebbleKit.areAppMessagesSupported(this)) {
            PebbleKit.startAppOnPebble(getApplicationContext(), Constants.SPORTS_UUID);
        } else {
            if (DEBUG) Log.d(TAG, "WTF: appMessages are not supported");
            // TODO: close this service
        }
    }


    public void updatePebbleWatch() {

        PebbleDictionary data = new PebbleDictionary();
        boolean updateWatch = false;

        SensorData sensorData = banalService.getBestSensorData(SensorType.TIME_ACTIVE);
        if (sensorData != null) {
            data.addString(Constants.SPORTS_TIME_KEY, sensorData.getStringValue());
            updateWatch = true;
        }

        sensorData = banalService.getBestSensorData(SensorType.DISTANCE_m);
        if (sensorData != null) {
            data.addString(Constants.SPORTS_DISTANCE_KEY, sensorData.getStringValue());
            updateWatch = true;
        }

        if (mShowPace) {
            sensorData = banalService.getBestSensorData(SensorType.PACE_spm);
            data.addUint8(Constants.SPORTS_LABEL_KEY, (byte) Constants.SPORTS_DATA_PACE);
        } else {
            sensorData = banalService.getBestSensorData(SensorType.SPEED_mps);
            data.addUint8(Constants.SPORTS_LABEL_KEY, (byte) Constants.SPORTS_DATA_SPEED);
        }
        if (sensorData != null) {
            data.addString(Constants.SPORTS_DATA_KEY, sensorData.getStringValue());
            updateWatch = true;
        }

        if (updateWatch) {
            data.addUint8(Constants.SPORTS_UNITS_KEY, (byte) (mUnits == MyUnits.METRIC ? Constants.SPORTS_UNITS_METRIC : Constants.SPORTS_UNITS_IMPERIAL));
            PebbleKit.sendDataToPebble(getApplicationContext(), Constants.SPORTS_UUID, data);
        }
    }


    // Send a broadcast to close the specified application on the connected Pebble
    protected void stopPebbleWatchApp() {
        if (DEBUG) {
            Log.d(TAG, "stopPebbleWatchApp");
        }

        // here, we want to disconnect, if this fails, we don't care
        try {
            PebbleKit.closeAppOnPebble(getApplicationContext(), Constants.SPORTS_UUID);
        } catch (Exception e) {
            Log.d(TAG, "failed to closeAppOnPebble");
        }
    }

}
