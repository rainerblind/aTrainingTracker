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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.ActivityType;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm;
import com.atrainingtracker.banalservice.sensor.SensorData;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.trainingtracker.MyUnits;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.smartwatch.pebble.PebbleDatabaseManager.PebbleDbHelper;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleAckReceiver;
import com.getpebble.android.kit.PebbleKit.PebbleNackReceiver;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

// import com.getpebble.android.kit.Constants;

// TODO: Regarding loosing messages, from the pebble website (https://developer.getpebble.com/guides/mobile-apps/android/android-comms/)
// Note that you can only have one message in a transit at the time. 
// You need to wait for the acknowledgement to your message before you can send another one 
// (otherwise the second one will fail to send and you will receive a Nack). 
// If you do not get the Acknowledgement, you should resend the message


// TODO create super class for similar services???
public class PebbleService extends Service {
    private static final String TAG = "PebbleService";
    private static final boolean DEBUG = false;


    private static final int MAX_STRING_SIZE = 14;
    private static final int FIELD_0 = 0x00;
    private static final int FIELD_1 = 0x01;
    private static final int FIELD_2 = 0x02;
    private static final int FIELD_3 = 0x03;
    private static final int FIELD_4 = 0x04;
    private static final int FIELD_5 = 0x05;
    private static final int FIELD_6 = 0x06;
    private static final int FIELD_7 = 0x07;
    private static final int FIELD_8 = 0x08;
    private static final int FIELD_9 = 0x09;
    private static final int FIELD_A = 0x0A;
    private static final int FIELD_B = 0x0B;
    private static final int FIELD_C = 0x0C;
    private static final int FIELD_D = 0x0D;
    private static final int FIELD_E = 0x0E;
    private static final int FIELD_F = 0x0F;
    private static final int MESSAGE_TYPE = 0x10;
    private static final int DATA_MESSAGE = 0x11;
    private static final int LAP_SUMMARY_MESSAGE = 0x12;
    private static final int CONFIGURE_MESSAGE = 0x13;
    private static final int PAUSE_MESSAGE = 0x14;
    private static final int RESUME_MESSAGE = 0x15;
    private static final int START_SEARCHING_MSG = 0x16;
    private static final int END_SEARCHING_MSG = 0x17;
    private static final int NUMBER_OF_FIELDS = 0x20;
    private static final int DESCRIPTION_1 = 0x31;
    private static final int DESCRIPTION_2 = 0x32;
    private static final int UNIT_1 = 0x33;
    private static final int UNIT_2 = 0x34;
    private static final int LAYOUT_NAME = 0x35;
    private static final int ACTIVITY_TYPE = 0x36;
    private static final int TEXT_SIZE_1 = 0x37;
    private static final int TEXT_SIZE_2 = 0x38;
    private static final int TEXT_SIZE_SMALL = 0x39;
    private static final int TEXT_SIZE_LARGE = 0x3A;
    private static final int BUTTON_PRESSED_KEY = 0x0;
    private static final int APP_INITIALIZED = 0x1;
    private static final int BUTTON_NEXT_VIEW = 0x0;
    private static final int BUTTON_LAP = 0x1;
    private static final int BUTTON_TOGGLE_PAUSE = 0x2;
    private static final int BUTTON_RESTART_SEARCH = 0x3;
    // This UUID identifies the training tracker app
    private static final UUID TRAINING_TRACKER_UUID = UUID.fromString("1496CBF7-5D9D-4DC2-80AE-F85E5894C8B4");
    // protected final IntentFilter mSearchingFinishedFilter = new IntentFilter(BANALService.SEARCHING_FINISHED_FOR_ALL_INTENT);
    protected final IntentFilter mConfigurePebbleWatchAppFilter = new IntentFilter();  // actions will be added in the onCreate() method
    protected final IntentFilter mUpdatePebbleFilter = new IntentFilter(BANALService.NEW_TIME_EVENT_INTENT);
    // BANAL-Connection banalConnection;
    protected final IntentFilter mPebbleConnectedFilter = new IntentFilter("com.getpebble.action.PEBBLE_CONNECTED");
    protected final IntentFilter mPauseFilter = new IntentFilter(TrainingApplication.REQUEST_PAUSE_TRACKING);
    protected final IntentFilter mResumeFilter = new IntentFilter(TrainingApplication.REQUEST_RESUME_FROM_PAUSED);  // TODO: do we also have to add START_TRACKING_INTENT???
    protected final IntentFilter mLapSummaryFilter = new IntentFilter(BANALService.LAP_SUMMARY);
    protected final IntentFilter mStartSearchingFilter = new IntentFilter(BANALService.SEARCHING_STARTED_FOR_ALL_INTENT);
    protected final IntentFilter mEndSearchingFilter = new IntentFilter(BANALService.SEARCHING_FINISHED_FOR_ALL_INTENT);
    private final BroadcastReceiver mPauseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            PebbleDictionary data = new PebbleDictionary();
            data.addInt32(MESSAGE_TYPE, PAUSE_MESSAGE);
            PebbleKit.sendDataToPebble(getApplicationContext(), TRAINING_TRACKER_UUID, data);
        }
    };
    private final BroadcastReceiver mResumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            PebbleDictionary data = new PebbleDictionary();
            data.addInt32(MESSAGE_TYPE, RESUME_MESSAGE);
            PebbleKit.sendDataToPebble(getApplicationContext(), TRAINING_TRACKER_UUID, data);
        }
    };
    private final BroadcastReceiver mStartSearchingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            PebbleDictionary data = new PebbleDictionary();
            data.addInt32(MESSAGE_TYPE, START_SEARCHING_MSG);
            PebbleKit.sendDataToPebble(getApplicationContext(), TRAINING_TRACKER_UUID, data);
        }
    };
    private final BroadcastReceiver mEndSearchingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            PebbleDictionary data = new PebbleDictionary();
            data.addInt32(MESSAGE_TYPE, END_SEARCHING_MSG);
            PebbleKit.sendDataToPebble(getApplicationContext(), TRAINING_TRACKER_UUID, data);
        }
    };
    private final BroadcastReceiver mLapSummaryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "received Lap Summary");

            PebbleDictionary data = new PebbleDictionary();
            data.addString(0, cutString(getString(R.string.Lap_NR, intent.getIntExtra(BANALService.PREV_LAP_NR, 0))));
            data.addString(1, cutString(intent.getStringExtra(BANALService.PREV_LAP_TIME_STRING)));
            data.addString(2, cutString(intent.getStringExtra(BANALService.PREV_LAP_DISTANCE_STRING)));

            data.addInt32(MESSAGE_TYPE, LAP_SUMMARY_MESSAGE);
            data.addInt32(NUMBER_OF_FIELDS, 3);
            PebbleKit.sendDataToPebble(getApplicationContext(), TRAINING_TRACKER_UUID, data);
        }
    };
    protected long mViewId;
    // protected String mLayoutName;  // called viewName elsewhere => TODO: change names
    protected int mRows = 3;
    protected boolean mReceiversRegistered = false;
    protected Number prevLapDistance = 0;
    protected DistanceFormatter distanceFormatter = new DistanceFormatter();
    private BANALServiceComm banalService;
    private List<SensorType> mSensorTypeList = new LinkedList<SensorType>();
    private final BroadcastReceiver mConfigurePebbleWatchAppReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "ConfigurePebbleWatchAppReceiver");
            configurePebbleWatchApp();
        }
    };
    // TODO: use handler, scheduler and runnable as in TrackerService instead of this construct???
    private final BroadcastReceiver mUpdatePebbleReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "mUpdatePebbleReceiver.onReceive()");
            updatePebbleWatch();
        }
    };
    // class BANALConnection implements ServiceConnection
    private final ServiceConnection mBanalConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            banalService = (BANALServiceComm) service;
            if (DEBUG) Log.i(TAG, "connected to BANAL Service");
            if (!BANALService.isSearching()) {
                // TODO: what to do here?
                // updateSpeedOrPace();
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            banalService = null;
            if (DEBUG) Log.i(TAG, "disconnected from BANAL Service");
        }
    };
    // Pebble stuff
    private final PebbleKit.PebbleDataReceiver mPebbleDataReceiver = new PebbleKit.PebbleDataReceiver(TRAINING_TRACKER_UUID) {
        @Override
        public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {
            // first, we have to send immediately an ack to the pebble
            PebbleKit.sendAckToPebble(context, transactionId);

            if (DEBUG) {
                Log.d(TAG, "response from Pebble: " + data.toJsonString());
            }

            if (data.getUnsignedIntegerAsLong(APP_INITIALIZED) != null) {
                configurePebbleWatchApp();
            } else if (data.getUnsignedIntegerAsLong(BUTTON_PRESSED_KEY) != null) {

                long buttonPressed = data.getUnsignedIntegerAsLong(BUTTON_PRESSED_KEY);

                if (buttonPressed == BUTTON_NEXT_VIEW) {
                    if (DEBUG) Log.d(TAG, "got info from pebble: next view");
                    mViewId = PebbleDatabaseManager.getNextViewId(mViewId);
                    if (mViewId < 0) {
                        mViewId = PebbleDatabaseManager.getFirstViewId(getActivityType());
                    }
                    configurePebbleWatchApp();
                } else if (buttonPressed == BUTTON_RESTART_SEARCH) {
                    sendBroadcast(new Intent(TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES)
                            .setPackage(getPackageName()));
                } else if (buttonPressed == BUTTON_LAP) {
                    if (DEBUG) Log.d(TAG, "got info from pebble: new lap");
                    sendBroadcast(new Intent(TrainingApplication.REQUEST_NEW_LAP)
                            .setPackage(getPackageName()));  // tell the banalservice that there is a new lap,  The banalservice will broadcast an intent with the lap summary
                } else if (buttonPressed == BUTTON_TOGGLE_PAUSE) {
                    if (DEBUG) Log.d(TAG, "got info from pebble: toggle pause");
                    if (TrainingApplication.isPaused()) {
                        sendBroadcast(new Intent(TrainingApplication.REQUEST_RESUME_FROM_PAUSED)
                                .setPackage(getPackageName()));
                    } else {
                        sendBroadcast(new Intent(TrainingApplication.REQUEST_PAUSE_TRACKING)
                                .setPackage(getPackageName()));
                    }
                } else {
                    if (DEBUG) Log.d(TAG, "WTF: something unknown happened!");
                }
            }

            // TODO: does not really help.  Probably, sending an Broadcast is too slow?
            // updatePebbleWatch();
        }
    };
    private final PebbleAckReceiver pebbleAckReceiver = new PebbleAckReceiver(TRAINING_TRACKER_UUID) {
        @Override
        public void receiveAck(Context context, int transactionId) {
            if (DEBUG) Log.d(TAG, "Pebble ACKed id:" + transactionId);
        }
    };
    private final PebbleNackReceiver pebbleNackReceiver = new PebbleNackReceiver(TRAINING_TRACKER_UUID) {
        @Override
        public void receiveNack(Context context, int transactionId) {
            if (DEBUG) Log.d(TAG, "Pebble NACKed id:" + transactionId);
        }
    };
    // Pebble helpers
    protected BroadcastReceiver mPebbleConnectedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "pebble became connected");
            startPebbleWatchApp();
        }
    };

//    private final BroadcastReceiver mSearchingFinishedReceiver = new BroadcastReceiver()
//    {
//    	@Override
//        public void onReceive(Context context, Intent intent)
//        {
//            if (DEBUG) Log.d(TAG, "searching finished");
//            // TODO: inform the user that searching is finished
//            // configurePebbleWatchApp();  // moved to configurePebbleWatchAppReceiver
//        }
//    };

    private static String cutString(String inputString) {
        if (inputString.length() < MAX_STRING_SIZE) {
            return inputString;
        } else {
            return inputString.substring(0, MAX_STRING_SIZE - 2) + ".";
        }
    }

    protected void updateSensorTypeList() {
        if (DEBUG) Log.d(TAG, "updateSensorTypeList");

        List<SensorType> result = new LinkedList<SensorType>();

        SQLiteDatabase db = (new PebbleDbHelper(this)).getReadableDatabase();
        Cursor cursor = db.query(PebbleDbHelper.ROWS_TABLE,
                null,
                PebbleDbHelper.VIEW_ID + "=?",
                new String[]{mViewId + ""},
                null,
                null,
                PebbleDbHelper.ROW_NR + " ASC"); // sorting

        mRows = cursor.getCount();

        while (cursor.moveToNext()) {
            result.add(SensorType.valueOf(cursor.getString(cursor.getColumnIndex(PebbleDbHelper.SENSOR_TYPE))));
        }

        cursor.close();
        db.close();

        mSensorTypeList = result;
    }

    protected ActivityType getActivityType() {
        if (banalService == null) {
            return ActivityType.GENERIC;
        } else {
            return banalService.getActivityType();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) {
            Log.d(TAG, "onCreate");
        }

        // request bind to the BANAL Service
        bindService(new Intent(this, BANALService.class), mBanalConnection, Context.BIND_AUTO_CREATE);

        mConfigurePebbleWatchAppFilter.addAction(BANALService.SEARCHING_FINISHED_FOR_ALL_INTENT);
        mConfigurePebbleWatchAppFilter.addAction(ConfigPebbleViewFragment.PEBBLE_VIEW_CHANGED_INTENT);

        // ContextCompat.registerReceiver(this, mSearchingFinishedReceiver, mSearchingFinishedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mConfigurePebbleWatchAppReceiver, mConfigurePebbleWatchAppFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mUpdatePebbleReceiver, mUpdatePebbleFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mPebbleConnectedReceiver, mPebbleConnectedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mPauseReceiver, mPauseFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mResumeReceiver, mResumeFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mLapSummaryReceiver, mLapSummaryFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mStartSearchingReceiver, mStartSearchingFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mEndSearchingReceiver, mEndSearchingFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) {
            Log.d(TAG, "onStartCommand Received start id " + startId + ": " + intent);
        }
        super.onStartCommand(intent, flags, startId);

        mViewId = PebbleDatabaseManager.getFirstViewId(getActivityType());
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

        // unregisterReceiver(mSearchingFinishedReceiver);
        unregisterReceiver(mConfigurePebbleWatchAppReceiver);
        unregisterReceiver(mUpdatePebbleReceiver);
        unregisterReceiver(mPebbleConnectedReceiver);
        unregisterReceiver(mPauseReceiver);
        unregisterReceiver(mResumeReceiver);
        unregisterReceiver(mLapSummaryReceiver);
        unregisterReceiver(mStartSearchingReceiver);
        unregisterReceiver(mEndSearchingReceiver);

        stopPebbleWatchApp();
    }

    // Send a broadcast to launch the specified application on the connected Pebble
    protected void startPebbleWatchApp() {
        if (DEBUG) {
            Log.d(TAG, "startPebbleWatchApp");
        }

        // first, we update the SensorType list
        updateSensorTypeList();

        // then we start the pebble Watchapp
        if (PebbleKit.areAppMessagesSupported(this)) {
            PebbleKit.startAppOnPebble(this, TRAINING_TRACKER_UUID);
            registerReceivers();

        } else {
            if (DEBUG) Log.d(TAG, "WTF: appMessages are not supported");
            // TODO: close this service
        }
    }

    protected void configurePebbleWatchApp() {
        if (DEBUG) Log.d(TAG, "configurePebbleWatchApp");

        // again, we first update the SensorType list
        updateSensorTypeList();

        // and then update the pebble display
        PebbleDictionary data = new PebbleDictionary();

        if (TrainingApplication.isPaused()) {
            data = new PebbleDictionary();
            data.addInt32(MESSAGE_TYPE, PAUSE_MESSAGE);
            PebbleKit.sendDataToPebble(getApplicationContext(), TRAINING_TRACKER_UUID, data);
        }

        if (BANALService.isSearching()) {
            data = new PebbleDictionary();
            data.addInt32(MESSAGE_TYPE, START_SEARCHING_MSG);
            PebbleKit.sendDataToPebble(getApplicationContext(), TRAINING_TRACKER_UUID, data);
        }

        data = new PebbleDictionary();
        String layoutName = PebbleDatabaseManager.getName(mViewId);
        if (DEBUG) Log.d(TAG, "layoutName = " + layoutName);
        data.addString(LAYOUT_NAME, cutString(layoutName));
        data.addString(ACTIVITY_TYPE, cutString(getString(getActivityType().getShortTitleId())));

        SensorType sensorType = mSensorTypeList.get(1);
        data.addString(DESCRIPTION_1, cutString(getString(sensorType.getShortNameId()) + ":"));
        data.addString(UNIT_1, " " + cutString(getString(MyHelper.getShortUnitsId(sensorType))));
        data.addInt32(TEXT_SIZE_1, getTextSize(sensorType));

        sensorType = mSensorTypeList.get(2);
        data.addString(DESCRIPTION_2, cutString(getString(sensorType.getShortNameId()) + ":"));
        data.addString(UNIT_2, " " + cutString(getString(MyHelper.getShortUnitsId(sensorType))));
        data.addInt32(TEXT_SIZE_2, getTextSize(sensorType));

        data.addInt32(MESSAGE_TYPE, CONFIGURE_MESSAGE);
        data.addInt32(NUMBER_OF_FIELDS, mRows);
        PebbleKit.sendDataToPebble(getApplicationContext(), TRAINING_TRACKER_UUID, data);
    }

    private int getTextSize(SensorType sensorType) {
        int textSize = TEXT_SIZE_LARGE;

        if ((sensorType == SensorType.PACE_spm && TrainingApplication.getUnit() == MyUnits.IMPERIAL)
                || sensorType == SensorType.TIME_ACTIVE
                || sensorType == SensorType.TIME_LAP
                || sensorType == SensorType.TIME_TOTAL) {
            textSize = TEXT_SIZE_SMALL;
        }

        return textSize;
    }

    protected void registerReceivers() {
        PebbleKit.registerReceivedAckHandler(this, pebbleAckReceiver);
        PebbleKit.registerReceivedNackHandler(this, pebbleNackReceiver);
        PebbleKit.registerReceivedDataHandler(this, mPebbleDataReceiver);
        mReceiversRegistered = true;
    }

    protected void updatePebbleWatch() {

        if (banalService != null) {

            PebbleDictionary data = new PebbleDictionary();
            boolean updateWatch = false;

            SensorData sensorData;
            int field = 0;
            for (SensorType sensorType : mSensorTypeList) {
                if (DEBUG) Log.d(TAG, "updating field " + field);
                sensorData = banalService.getBestSensorData(sensorType);
                if (sensorData != null) {
                    if (sensorType == SensorType.LAP_NR && (field == 3 || field == 4)) {
                        if ((Integer) sensorData.getValue() < 9) {
                            data.addString(field, cutString("Lap: " + sensorData.getStringValue()));
                        } else {
                            data.addString(field, cutString(sensorData.getStringValue()));
                        }
                    } else {
                        data.addString(field, cutString(sensorData.getStringValue()));
                    }
                    updateWatch = true;
                } else {
                    // data.addString(field, getString(sensorType.getShortNameId()));
                    data.addString(field, cutString(getString(R.string.NoData)));
                }
                field++;
            }

            if (updateWatch) {
                if (DEBUG) {
                    Log.d(TAG, "updating pebble watch");
                }
                // WTF: Pebble seems to invert the sequence!
                data.addInt32(MESSAGE_TYPE, DATA_MESSAGE);
                data.addInt32(NUMBER_OF_FIELDS, mRows);
                PebbleKit.sendDataToPebble(getApplicationContext(), TRAINING_TRACKER_UUID, data);
            }
        }
    }

    // Send a broadcast to close the specified application on the connected Pebble
    protected void stopPebbleWatchApp() {
        if (DEBUG) {
            Log.d(TAG, "stopPebbleWatchApp");
        }

        // here, we want to disconnect, if this fails, we don't care
        try {
            PebbleKit.closeAppOnPebble(this, TRAINING_TRACKER_UUID);
        } catch (Exception e) {
            Log.e(TAG, "failed to closeAppOnPebble");
        }

        if (mReceiversRegistered) {
            try {
                unregisterReceiver(mPebbleDataReceiver);
            } catch (Exception e) {
                Log.e(TAG, "failed to unregister Data receiver");
            }
            try {
                unregisterReceiver(pebbleAckReceiver);
            } catch (Exception e) {
                Log.e(TAG, "failed to unregister ACK receiver");
            }
            try {
                unregisterReceiver(pebbleNackReceiver);
            } catch (Exception e) {
                Log.e(TAG, "failed to unregister NACK receiver");
            }
        }
    }

}
