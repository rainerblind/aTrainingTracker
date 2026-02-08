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

package com.atrainingtracker.trainingtracker.tracker;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm;
import com.atrainingtracker.banalservice.devices.AltitudeFromPressureDevice;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorData;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.SensorValueType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.exporter.ExportManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.ActiveDevicesDbHelper;
import com.atrainingtracker.trainingtracker.database.ActiveDevicesDbHelper.ActiveDevices;
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TrackerService extends Service {
    // TODO: probably, we also have to remove this and use the keywords of WorkoutSummaries directly.
    // there seems to be also a problem with workout_name and the base_file_name...
    public static final String WORKOUT_NAME = "de.rainerblind.trainingtracker.TrackerService.WORKOUT_NAME";
    public static final String TRACKING_STARTED_INTENT = "de.rainerblind.trainingtracker.TrackerService.TRACKING_STARTED_INTENT";
    public static final String TRACKING_FINISHED_INTENT = "de.rainerblind.trainingtracker.TrackerService.TRACKING_FINISHED_INTENT";
    // public static final String WORKOUT_ID               = "de.rainerblind.trainingtracker.TrackerService.WORKOUT_ID";
    public static final String START_TYPE = "START_TYPE";
    private static final String TAG = "TrackerService";
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    protected final IntentFilter mAltitudeCorrectionFilter = new IntentFilter(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_INTENT);
    protected final IntentFilter mSearchingFinishedFilter = new IntentFilter(BANALService.SEARCHING_FINISHED_FOR_ALL_INTENT);
    // BANALConnection banalConnection;
    protected final IntentFilter mLapSummaryFilter = new IntentFilter(BANALService.LAP_SUMMARY);

    // protected ContentValues mValues        = new ContentValues();
    // protected ContentValues mSummaryValues = new ContentValues();
    private final ScheduledExecutorService mScheduler = Executors.newScheduledThreadPool(1);
    // we assume that we start while the BANAL Service is searching
    protected boolean mSearching = true;
    protected boolean mCreateNewLapWhenConnectedToBanalService = false;
    protected boolean mResumeFromPausedWhenConnectedToBanalService = false;
    protected boolean mResumeTrackingWhenConnectedToBanalService = false;
    @Nullable
    BANALServiceComm mBanalService;
    private TrainingApplication mTrainingApplication;
    private ScheduledFuture mTrackerHandle;
    // int            mCalories        = 0;
    // double         mSpeedAverage_mps = 0.0;


    // int    mPrevLapTimeTotal_s      = 0;
    // double mPrevLapDistanceTotal_m  = 0.0;


    // private long mSportTypeId = SportTypeDatabaseManager.getDefaultSportTypeId();
    private long mWorkoutID;
    private final BroadcastReceiver mLapSummaryReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, @NonNull Intent intent) {
            if (DEBUG) Log.i(TAG, "received lap summary intent");

            saveLap(intent.getIntExtra(BANALService.PREV_LAP_NR, 0),
                    intent.getIntExtra(BANALService.PREV_LAP_TIME_S, 0),
                    intent.getDoubleExtra(BANALService.PREV_LAP_DISTANCE_m, 0),
                    intent.getDoubleExtra(BANALService.PREV_LAP_SPEED_mps, 0));
        }
    };
    private String mBaseFileName;

    // private String mSport;  
    // private String mGCDataString;
    // private String[] mSensorNames;
    private String mSamplesTableName;
    private final BroadcastReceiver mAltitudeCorrectionReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            double altitudeCorrection = intent.getDoubleExtra(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE, 0.0);

            if (DEBUG)
                Log.i(TAG, "updating all previous altitude measurements by " + altitudeCorrection);
            String operator = altitudeCorrection >= 0 ? " + " : " - ";

            WorkoutSamplesDatabaseManager databaseManager = WorkoutSamplesDatabaseManager.getInstance(TrackerService.this);
            SQLiteDatabase samplesDb = databaseManager.getDatabase();
            samplesDb.execSQL("UPDATE " + mSamplesTableName
                    + " set " + SensorType.ALTITUDE.name() + " = " + SensorType.ALTITUDE.name() + operator + Math.abs(altitudeCorrection));
            // + " where " + Keys.Key_SKU + " = " + SKU + " and " + Keys.Key_STATUS + " = 0");
            // no where statement required because we want to update all previous ones.
        }
    };
    // private long   mLapNr           = BANALService.INIT_LAP_NR-1;
    // int            mTimeTotal_s     = 0;
    private int mTimeActive_s = 0;
    private double mDistanceTotal_m = 0.0;
    private final BroadcastReceiver mSearchingFinishedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "searching finished");
            if (mSearching) {
                // mSearching = false done in updateDbsOnSearchingFinished
                onSearchingFinished();
            }
        }
    };

    private final BroadcastReceiver mUserSelectedSportTypeChangedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "user selected sport type changed");
            onUserSelectedSportTypeChanged();
        }
    };

    // finally the main tracking routine, that is called periodically
    final Runnable tracker = new Runnable() {
        public void run() {
            if (DEBUG) {
                Log.d(TAG, "sampling/tracking");
            }

            if (mBanalService != null) {

                sampleAndWriteToDb();

                // update notification
                if (DEBUG) Log.i(TAG, "updating notification");
                mTrainingApplication.updateTimeAndDistanceToNotification(mBanalService.getBestSensorData(SensorType.TIME_ACTIVE),
                        mBanalService.getBestSensorData(SensorType.DISTANCE_m),
                        SportTypeDatabaseManager.getInstance(TrackerService.this).getUIName(mBanalService.getSportTypeId()));
                if (DEBUG) Log.i(TAG, "updated notification");

            }
        }
    };
    // class BANALConnection implements ServiceConnection
    private final ServiceConnection mBanalConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBanalService = (BANALServiceComm) service;
            if (DEBUG) Log.i(TAG, "connected to BANAL Service");
            if (!BANALService.isSearching()) {
                onSearchingFinished();
            }

            if (mCreateNewLapWhenConnectedToBanalService) {
                mCreateNewLapWhenConnectedToBanalService = false;
                createNewLap();
            }

            if (mResumeFromPausedWhenConnectedToBanalService) {
                recreateValuesWhenResuming();
                mResumeFromPausedWhenConnectedToBanalService = false;
                // mBanalService.resumeFromPaused();
            }

            if (mResumeTrackingWhenConnectedToBanalService) {
                recreateValuesWhenResuming();
                mResumeTrackingWhenConnectedToBanalService = false;
                // mBanalService.resumeTracking();
                sendBroadcast(new Intent(BANALService.RESET_ACCUMULATORS_INTENT)
                        .setPackage(getPackageName()));
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            mBanalService = null;
            if (DEBUG) Log.i(TAG, "disconnected from BANAL Service");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) {
            Log.d(TAG, "onCreate");
        }

        mTrainingApplication = (TrainingApplication) getApplication();

        // request bind to the BANAL Service
        bindService(new Intent(this, BANALService.class), mBanalConnection, Context.BIND_AUTO_CREATE);

        ContextCompat.registerReceiver(this, mSearchingFinishedReceiver, mSearchingFinishedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mAltitudeCorrectionReceiver, mAltitudeCorrectionFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mLapSummaryReceiver, mLapSummaryFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mUserSelectedSportTypeChangedReceiver, new IntentFilter(BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (DEBUG) {
            Log.d(TAG, "onStartCommand Received start id " + startId + ": " + intent);
        }

        StartType startType;
        if (intent == null) {
            startType = StartType.RESUME_SERVICE_RECREATION;
        } else {
            startType = StartType.valueOf(intent.getStringExtra(START_TYPE));
        }
        switch (startType) {
            case START_NORMAL:
                if (DEBUG) Log.d(TAG, "starting a new workout");
                // The workout name is just the date+time
                mBaseFileName = (new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)).format(new Date());
                mWorkoutID = createNewWorkout();
                WorkoutSamplesDatabaseManager.getInstance(this).createNewTable(mBaseFileName, Arrays.asList(SensorType.values()));       // create a new table with a column for each possible sensor
                break;

            case RESUME_BY_USER:
                Log.d(TAG, "resuming by user request");
                if (mBanalService != null) {
                    recreateValuesWhenResuming();
                    // mBanalService.resumeFromPaused();  already started by broadcast?
                } else {
                    mResumeFromPausedWhenConnectedToBanalService = true;
                }
                break;

            case RESUME_SERVICE_RECREATION:
                Log.d(TAG, "resuming after killed service");
                mTrainingApplication.setTracking();
                if (mBanalService != null) {
                    recreateValuesWhenResuming();
                    // mBanalService.resumeTracking(); already started by broadcast?
                } else {
                    mResumeTrackingWhenConnectedToBanalService = true;
                }
                break;
        }

        mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName);

        if (mBanalService != null && !BANALService.isSearching()) {
            onSearchingFinished();
        }

        // start tracking
        mTrackerHandle = mScheduler.scheduleAtFixedRate(tracker, 0, // initial delay
                1, // sampling time
                TimeUnit.SECONDS);

        // notify others
        Intent trackingStartedIntent = new Intent(TRACKING_STARTED_INTENT)
                .putExtra(WorkoutSummaries.WORKOUT_ID, mWorkoutID)
                .setPackage(this.getPackageName());
        this.sendBroadcast(trackingStartedIntent);

        this.startForeground(TrainingApplication.TRACKING_NOTIFICATION_ID, mTrainingApplication.getSearchingAndTrackingNotification());


        // We want this service to continue running until it is explicitly stopped, so return sticky.
        // When the service is stopped due to a lack of memory, it will be recreated and this method called with a null intent, see:
        // https://android-developers.googleblog.com/2010/02/service-api-changes-starting-with.html
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

        // first of all, stop the trackerHandle
        if (mTrackerHandle != null) {
            mTrackerHandle.cancel(true);
        }

        // mTrainingApplication.setTracking(false);
        endWorkout();

        unbindService(mBanalConnection);
        mBanalService = null;


        unregisterReceiver(mSearchingFinishedReceiver);
        unregisterReceiver(mAltitudeCorrectionReceiver);
        unregisterReceiver(mLapSummaryReceiver);
        unregisterReceiver(mUserSelectedSportTypeChangedReceiver);
    }

    private void recreateValuesWhenResuming() {

        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance(this).getDatabase();
        Cursor cursor = db.query(WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE, null, null, null, null, null, null);
        cursor.moveToLast();

        mBaseFileName = cursor.getString(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME));
        mWorkoutID = cursor.getInt(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID));
        int time_total_s = cursor.getInt(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s));
        int time_active_s = cursor.getInt(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s));
        int calories = cursor.getInt(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES));
        int lapNr = cursor.getInt(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS));
        double distance_m = cursor.getDouble(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m));

        cursor.close();

        Log.d(TAG, "resuming with mBaseFileName=" + mBaseFileName
                + ", mWorkoutId=" + mWorkoutID
                + ", time_total_s=" + time_total_s
                + ", time_active_s=" + time_active_s
                + ", calories=" + calories
                + ", lapNr=" + lapNr
                + ", distance_m=" + distance_m);

        BANALService.setInitialSensorValue(SensorType.TIME_TOTAL, time_total_s);
        BANALService.setInitialSensorValue(SensorType.TIME_ACTIVE, time_active_s);
        BANALService.setInitialSensorValue(SensorType.CALORIES, calories);
        BANALService.setInitialSensorValue(SensorType.LAP_NR, lapNr);
        BANALService.setInitialSensorValue(SensorType.DISTANCE_m, distance_m);
    }

    // NullPointerException when mBanalService is null!
    protected void createNewLap() {
        if (DEBUG) Log.i(TAG, "createNewLap");

        if (mBanalService == null) {
            mCreateNewLapWhenConnectedToBanalService = true;
        } else {

            SensorData sensorData;

            int prevLapNr = 0;
            sensorData = mBanalService.getBestSensorData(SensorType.LAP_NR);
            if (sensorData != null) {
                prevLapNr = (Integer) sensorData.getValue();
            }

            int lapTime_s = 0;
            sensorData = mBanalService.getBestSensorData(SensorType.TIME_LAP);
            if (sensorData != null
                    && sensorData.getValue() != null) {
                lapTime_s = (Integer) sensorData.getValue();
            }

            double lapDistance = 0.0;
            sensorData = mBanalService.getBestSensorData(SensorType.DISTANCE_m_LAP);
            if (sensorData != null
                    && sensorData.getValue() != null) {
                lapDistance = (Double) sensorData.getValue();
            }

            double lapSpeed = lapDistance / lapTime_s;

            saveLap(prevLapNr, lapTime_s, lapDistance, lapSpeed);
        }
    }

    protected void saveLap(long lapNr, int lapTime, double lapDistance, double averageSpeed) {
        if (DEBUG)
            Log.i(TAG, "saveLap: lapNr=" + lapNr + ", lapTime=" + lapTime + ", lapDistance=" + lapDistance + ", averageSpeed=" + averageSpeed);

        LapsDatabaseManager.getInstance(this).saveLap(mWorkoutID, lapNr, lapTime, lapDistance, averageSpeed);
    }

    protected long getSportTypeId() {

        long sportTypeId = BANALService.getDefaultSportTypeId();
        if (mBanalService != null) {
            if (averageSpeedCalculateable()) {
                sportTypeId = mBanalService.getSportTypeId(getAverageSpeed());
            } else {
                sportTypeId = mBanalService.getSportTypeId();
            }
        }

        return sportTypeId;
    }


    // not necessary when resuming an already started workout
    protected long createNewWorkout() {

        if (DEBUG) Log.d(TAG, "createNewWorkout");

        long sportTypeId = getSportTypeId();

        ContentValues values = new ContentValues();

        values.put(WorkoutSummaries.GOAL, "");
        values.put(WorkoutSummaries.METHOD, "");
        values.put(WorkoutSummaries.GC_DATA, MySensorManager.EMPTY_GC_DATA);
        values.put(WorkoutSummaries.SPORT_ID, sportTypeId);
        values.put(WorkoutSummaries.B_SPORT, SportTypeDatabaseManager.getInstance(this).getBSportType(sportTypeId).name());
        values.put(WorkoutSummaries.WORKOUT_NAME, mBaseFileName);
        values.put(WorkoutSummaries.FILE_BASE_NAME, mBaseFileName);

        WorkoutSummariesDatabaseManager databaseManager = WorkoutSummariesDatabaseManager.getInstance(this);
        SQLiteDatabase summariesDb = databaseManager.getDatabase();
        long workoutId = summariesDb.insert(WorkoutSummaries.TABLE, null, values);
        //}
        //catch (SQLException e) {
        //    Log.e(TAG, "Error while writing" + e.toString());
        //}

        ExportManager exportManager = new ExportManager(this);
        exportManager.newWorkout(mBaseFileName);

        return workoutId;
    }


    protected void onSearchingFinished() {
        if (DEBUG) {
            Log.d(TAG, "onSearchingFinished()");
        }

        mSearching = false;
        if (mBanalService == null) {
            return;
        }

        long sportTypeId = getSportTypeId();

        // now, that we know the sport and the available sensors, we update the summaries DB
        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.SPORT_ID, sportTypeId);
        values.put(WorkoutSummaries.B_SPORT, SportTypeDatabaseManager.getInstance(this).getBSportType(sportTypeId).name());
        values.put(WorkoutSummaries.GC_DATA, mBanalService.getGCDataString());

        WorkoutSummariesDatabaseManager databaseManager = WorkoutSummariesDatabaseManager.getInstance(this);
        SQLiteDatabase summariesDb = databaseManager.getDatabase();
        summariesDb.update(WorkoutSummaries.TABLE,
                values,
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(mWorkoutID)});
    }

    private void onUserSelectedSportTypeChanged() {
        long sportTypeId = getSportTypeId();

        // when the user changes the sport type, we update the summaries DB
        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.SPORT_ID, sportTypeId);
        values.put(WorkoutSummaries.B_SPORT, SportTypeDatabaseManager.getInstance(this).getBSportType(sportTypeId).name());

        WorkoutSummariesDatabaseManager databaseManager = WorkoutSummariesDatabaseManager.getInstance(this);
        SQLiteDatabase summariesDb = databaseManager.getDatabase();
        summariesDb.update(WorkoutSummaries.TABLE,
                values,
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(mWorkoutID)});
    }



    // TODO: the database entries should be correct even when this method is not called due to a crash
    // some stuff could be written earlier, others from the calling part (and then also executed when a crash is detected...), ...
    // might be best to use a method that is executed ever minute (or only every 5 or 10 minutes?)
    public void endWorkout() {
        if (DEBUG) {
            Log.d(TAG, "endWorkout");
        }

        createNewLap();

        // store the ANT Devices that were active during the workout
        // TODO: store at very start and end of ANT (or BTLE) searching
        if (DEBUG) Log.d(TAG, "storing active device list");
        SQLiteDatabase activeDevicesDb = new ActiveDevicesDbHelper(this).getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(ActiveDevices.WORKOUT_ID, mWorkoutID);
        for (long deviceDbId : mBanalService.getDatabaseIdsOfActiveDevices()) {
            if (DEBUG) Log.d(TAG, "adding deviceId " + deviceDbId + " to list of active devices");
            values.put(ActiveDevices.DEVICE_DB_ID, deviceDbId);
            activeDevicesDb.insert(ActiveDevices.TABLE, null, values);
        }
        activeDevicesDb.close();

        WorkoutSummariesDatabaseManager summariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(this);
        // save the accumulated SensorTypes
        summariesDatabaseManager.saveAccumulatedSensorTypes(mWorkoutID, mBanalService.getAccumulatedSensorTypeSet());

        // update the summaries
        ContentValues summaryValues = new ContentValues();

        summaryValues.put(WorkoutSummaries.FINISHED, 1);  // remove this line for testing
        // WTF, when the service crashes, not only the flag is not set but the whole method is not executed.
        // Thus, use a return statement at the very beginning for debugging

        // TODO: store at very beginning, end of ANT and BTLE searching, GPS found
        summaryValues.put(WorkoutSummaries.GC_DATA, mBanalService.getAccumulatedGCDataString());

        long sportTypeId = getSportTypeId();
        summaryValues.put(WorkoutSummaries.SPORT_ID, sportTypeId);
        summaryValues.put(WorkoutSummaries.B_SPORT, SportTypeDatabaseManager.getInstance(this).getBSportType(sportTypeId).name());

        SQLiteDatabase summariesDb = summariesDatabaseManager.getDatabase();
        summariesDb.update(WorkoutSummaries.TABLE,
                summaryValues,
                WorkoutSummaries.C_ID + "=" + mWorkoutID,
                null);

        ExportManager exportManager = new ExportManager(this);
        exportManager.workoutFinished(mBaseFileName);

        sendBroadcast(new Intent(TRACKING_FINISHED_INTENT)
                .setPackage(getPackageName()));
    }

    private void sampleAndWriteToDb() {
        if (DEBUG) Log.d(TAG, "sampleAndWriteToDb()");

        if (TrainingApplication.isPaused()) {  // when we are pause, nothing is sampled and written.  TODO: is this the correct behaviour?
            return;
        }

        ContentValues samplingValues = new ContentValues();
        ContentValues summaryValues = new ContentValues();

        Map<String, SensorValueType> sensorName2Type = new HashMap<>();

        // sample
        for (SensorData<Number> sensorData : mBanalService.getAllSensorData()) {
            if (sensorData == null) {
                Log.d(TAG, "WTF: sensorData == null!");
                continue;
            }
            if (sensorData.getValue() == null) {
                if (DEBUG)
                    Log.d(TAG, "sensorData.getValue() == null for " + sensorData.getSensorType().name());
                continue;
            }


            SensorType sensorType = sensorData.getSensorType();
            String sensorName = sensorType.name();
            String deviceName = sensorData.getDeviceName();
            if (deviceName != null) {                                       // when it is not the best sensor (or the clock, or...), we add the the name of the source device
                if (deviceName.equals("gps") || deviceName.equals("network") || deviceName.equals("google_fused")) {  // for the location related stuff, we want to be compatible with pre 3.8
                    sensorName += "_" + deviceName;                                                                   // this is especially important for track on map views and so on...
                } else {
                    sensorName += " (" + deviceName + ")";  // the name of the device is added
                    sensorName = "'" + sensorName + "'";
                }
            }
            SensorValueType type = sensorType.getSensorValueType();
            sensorName2Type.put(sensorName, type);

            switch (type) {
                case INTEGER:
                    if (DEBUG)
                        Log.d(TAG, "tracking INTEGER data for " + sensorName + ": " + sensorData.getValue().intValue());
                    samplingValues.put(sensorName, sensorData.getValue().intValue());
                    break;
                case DOUBLE:
                    if (DEBUG)
                        Log.d(TAG, "tracking DOUBLE data for " + sensorName + ": " + sensorData.getValue().doubleValue());
                    samplingValues.put(sensorName, sensorData.getValue().doubleValue());
                    break;
                default:
                    if (DEBUG)
                        Log.i(TAG, "tracking STRING for " + sensorName + ": " + sensorData.getStringValue());
                    samplingValues.put(sensorName, sensorData.getStringValue());
                    // if (DEBUG) Log.d(TAG, "neither INTEGER nor DOUBLE for " + sensorType.name() + " => ignoring");
            }

            // for some sensors, we also write the summary values.
            switch (sensorType) {
                case TIME_TOTAL:
                    summaryValues.put(WorkoutSummaries.TIME_TOTAL_s, sensorData.getValue().intValue());
                    break;
                case TIME_ACTIVE:
                    mTimeActive_s = sensorData.getValue().intValue();
                    summaryValues.put(WorkoutSummaries.TIME_ACTIVE_s, mTimeActive_s);
                    break;
                case DISTANCE_m:
                    mDistanceTotal_m = sensorData.getValue().doubleValue();
                    summaryValues.put(WorkoutSummaries.DISTANCE_TOTAL_m, mDistanceTotal_m);
                    break;
                case CALORIES:
                    summaryValues.put(WorkoutSummaries.CALORIES, sensorData.getValue().intValue());
                    break;
                case LAP_NR:
                    summaryValues.put(WorkoutSummaries.LAPS, sensorData.getValue().intValue());
                    break;
                case ASCENT:
                    summaryValues.put(WorkoutSummaries.ASCENDING, sensorData.getValue().doubleValue());
                    break;
                case DESCENT:
                    summaryValues.put(WorkoutSummaries.DESCENDING, sensorData.getValue().doubleValue());
                    break;
            }

        }
        if (DEBUG) Log.d(TAG, "end of sampling, next: write to db");


        // write the samples to the database
        WorkoutSamplesDatabaseManager samplesDatabaseManager = WorkoutSamplesDatabaseManager.getInstance(this);
        SQLiteDatabase samplesDb = samplesDatabaseManager.getDatabase();

        try {
            samplesDb.insertOrThrow(mSamplesTableName,
                    null,
                    samplingValues);
        } catch (SQLException e) {  // ok, probably the column is missing
            if (DEBUG) Log.i(TAG, "SQLException.  Probably, column(s) missing");
            Cursor cursor = samplesDb.query(mSamplesTableName, null, null, null, null, null, null, "1");

            SortedSet<String> keys = new TreeSet<>(samplingValues.keySet());
            for (String key : keys) {
                String queryKey = key.replace("'", "");
                if (cursor.getColumnIndex(queryKey) < 0) {  // column is really missing
                    if (DEBUG) Log.i(TAG, "column " + key + " is missing.  Adding it.");
                    String type = switch (sensorName2Type.get(key)) {
                        case INTEGER -> "int";
                        case DOUBLE -> "double";
                        default -> "text";
                    };

                    String sqlCommand = "ALTER TABLE " + mSamplesTableName + " ADD COLUMN " + key + " " + type + " null;";
                    if (DEBUG) Log.i(TAG, "sql command: " + sqlCommand);

                    try {
                        samplesDb.execSQL(sqlCommand);
                    } catch (SQLException alterException) {
                        Log.i(TAG, "alter table, adding " + key + " column failed. (Command: " + sqlCommand + ")");
                    }
                }
            }

            // now, try again...
            try {
                samplesDb.insertOrThrow(mSamplesTableName,
                        null,
                        samplingValues);
            } catch (SQLException retryException) {  //  still did not work => try to insert them one by one...
                Log.i(TAG, "Failed to insert the samples after altering the table.  Try to insert them one by one.");
                for (String key : samplingValues.keySet()) {
                    ContentValues oneSamplingValue = new ContentValues();
                    switch (sensorName2Type.get(key)) {
                        case INTEGER:
                            oneSamplingValue.put(key, samplingValues.getAsInteger(key));
                            break;
                        case DOUBLE:
                            oneSamplingValue.put(key, samplingValues.getAsDouble(key));
                            break;
                        default:
                            oneSamplingValue.put(key, samplingValues.getAsString(key));
                    }
                    try {
                        samplesDb.insertOrThrow(mSamplesTableName,
                                null,
                                oneSamplingValue);
                    } catch (SQLException retryRetryException) {
                        Log.i(TAG, "Inserting " + key + " finally failed.  Giving up for this key; still trying to save the rest.");
                    }
                }
            }
        }

        // Update the summary data
        if (DEBUG) Log.d(TAG, "writing to Summaries db");
        summaryValues.put(WorkoutSummaries.EXTREMA_VALUES_CALCULATED, 0);    // force recalculation of the extrema values in the case that they have been calculated
        if (averageSpeedCalculateable()) {
            summaryValues.put(WorkoutSummaries.SPEED_AVERAGE_mps, getAverageSpeed());
        }

        WorkoutSummariesDatabaseManager summariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(this);
        SQLiteDatabase summariesDb = summariesDatabaseManager.getDatabase();
        summariesDb.update(WorkoutSummaries.TABLE,
                summaryValues,
                WorkoutSummaries.C_ID + "=" + mWorkoutID,
                null);
    }

    protected boolean averageSpeedCalculateable() {
        return mTimeActive_s != 0;
    }

    protected double getAverageSpeed() {
        return mDistanceTotal_m / mTimeActive_s;
    }

    public enum StartType {START_NORMAL, RESUME_BY_USER, RESUME_SERVICE_RECREATION}

}
