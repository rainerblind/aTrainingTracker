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

package com.atrainingtracker.trainingtracker.tracker;

import android.app.Application;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm;
import com.atrainingtracker.banalservice.devices.AltitudeFromPressureDevice;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorData;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.SensorValueType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager;
import com.atrainingtracker.trainingtracker.exporter.ExportManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.ActiveDevicesDbHelper;
import com.atrainingtracker.trainingtracker.database.ActiveDevicesDbHelper.ActiveDevices;
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.ExtremaType;
import com.atrainingtracker.trainingtracker.database.WorkoutCluster;
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository;
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The core background service responsible for live workout tracking and session persistence.
 *
 * This service manages the "1Hz Technical Sampling Loop" which ensures a deterministic
 * time-base for all recorded data. Its primary responsibilities include:
 * 1. **Data Acquisition**: Coordinating with [BANALService] to pull real-time sensor telemetry.
 * 2. **Session Persistence**: Maintaining the `WorkoutSummaries` and `WorkoutSamples` databases.
 * 3. **Lifecycle Management**: Handling Start/Stop/Pause states and implementing WakeLock
 *    protection to prevent data gaps during screen-off periods.
 * 4. **Post-Processing**: Calculating session averages and extrema (peaks) upon completion.
 *
 * Architectural Role: Primary business logic layer for real-time data recording.
 * Threading: Uses a dedicated [ScheduledExecutorService] for the high-precision sampling loop.
 */
/**
 * The core background service responsible for live workout tracking and session persistence.
 *
 * This service manages the "1Hz Technical Sampling Loop" which ensures a deterministic
 * time-base for all recorded data. Its primary responsibilities include:
 * 1. **Data Acquisition**: Coordinating with BANALService to pull real-time sensor telemetry.
 * 2. **Session Persistence**: Maintaining the WorkoutSummaries and WorkoutSamples databases.
 * 3. **Lifecycle Management**: Handling Start/Stop/Pause states and implementing WakeLock
 *    protection to prevent data gaps during screen-off periods.
 * 4. **Post-Processing**: Calculating session averages and extrema (peaks) upon completion.
 *
 * Architectural Role: Primary business logic layer for real-time data recording.
 * Threading: Uses a dedicated ScheduledExecutorService for the high-precision sampling loop.
 */
public class TrackerService extends Service {
    // TODO: probably, we also have to remove this and use the keywords of WorkoutSummaries directly.
    // there seems to be also a problem with workout_name and the base_file_name...
    public static final String WORKOUT_NAME = "de.rainerblind.trainingtracker.TrackerService.WORKOUT_NAME";
    public static final String TRACKING_STARTED_INTENT = "de.rainerblind.trainingtracker.TrackerService.TRACKING_STARTED_INTENT";
    public static final String TRACKING_FINISHED_INTENT = "de.rainerblind.trainingtracker.TrackerService.TRACKING_FINISHED_INTENT";
    public static final String WORKOUT_ID               = "WORKOUT_ID";
    public static final String WORKOUT_UPDATED_INTENT   = "com.atrainingtracker.trainingtracker.WOKRKOUT_UPDATED_INTENT";

    // Same sensor types from the old thread. Used for Averages.
    private static final HashSet<SensorType> IMPORTANT_SENSOR_TYPES = new HashSet<>(SensorType.CORE_METRICS);

    // Sensors to track for Min/Max and used for average if in IMPORTANT_SENSOR_TYPES
    private static final HashSet<SensorType> SENSORS_TO_TRACK = new HashSet<>(IMPORTANT_SENSOR_TYPES);
    static {
        SENSORS_TO_TRACK.add(SensorType.LATITUDE);
        SENSORS_TO_TRACK.add(SensorType.LONGITUDE);
        SENSORS_TO_TRACK.add(SensorType.LINE_DISTANCE_m);
    }
    
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
    private final java.util.concurrent.ExecutorService mDbExecutor = Executors.newSingleThreadExecutor();
    // we assume that we start while the BANAL Service is searching
    protected boolean mSearching = true;
    protected boolean mCreateNewLapWhenConnectedToBanalService = false;
    protected boolean mResumeFromPausedWhenConnectedToBanalService = false;
    protected boolean mResumeTrackingWhenConnectedToBanalService = false;
    @Nullable
    BANALServiceComm mBanalService;
    private android.os.PowerManager.WakeLock wakeLock;
    private TrainingApplication mTrainingApplication;
    private WorkoutRepository mWorkoutRepository;
    private ScheduledFuture mTrackerHandle;

    private int mExtremaDbUpdateCounter = 0;
    private static final int EXTREMA_DB_UPDATE_INTERVAL = 10; // Update Avg in DB every 10 seconds
    // int            mCalories        = 0;
    // double         mSpeedAverage_mps = 0.0;


    // int    mPrevLapTimeTotal_s      = 0;
    // double mPrevLapDistanceTotal_m  = 0.0;


    // private long mSportTypeId = SportTypeDatabaseManager.getDefaultSportTypeId();
    private long mWorkoutID;
    private LiveWorkoutSession mLiveSession;
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
            if (altitudeCorrection == 0.0) return;

            if (DEBUG)
                Log.i(TAG, "Triggering atomic altitude correction by " + altitudeCorrection);

            final long workoutId = mWorkoutID;
            final String samplesTable = mSamplesTableName;

            // 1. Live Session Update
            if (mLiveSession != null) {
                mLiveSession.applyAltitudeCorrection(altitudeCorrection);
            }

            // 2. Database Synchronization (offloaded to DB executor)
            mDbExecutor.submit(() -> {
                WorkoutSamplesDatabaseManager samplesManager = WorkoutSamplesDatabaseManager.getInstance(TrackerService.this);
                WorkoutSummariesDatabaseManager summariesManager = WorkoutSummariesDatabaseManager.getInstance(TrackerService.this);

                // 2a. Raw Samples shift
                String operator = altitudeCorrection >= 0 ? " + " : " - ";
                samplesManager.getDatabase().execSQL("UPDATE " + samplesTable
                        + " set " + SensorType.ALTITUDE.name() + " = " + SensorType.ALTITUDE.name() + operator + Math.abs(altitudeCorrection));

                // 2b. Summary Extrema and Elevation Stream shift (ATT-38)
                summariesManager.shiftAltitudeData(workoutId, altitudeCorrection);

                // 3. Notify UI/Repository to refresh from DB
                LocalBroadcastManager.getInstance(TrackerService.this).sendBroadcast(new Intent(WORKOUT_UPDATED_INTENT).putExtra(WORKOUT_ID, workoutId));
            });
        }
    };
    // private long   mLapNr           = BANALService.INIT_LAP_NR-1;
    int mTimeTotal_s = 0;
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
        mWorkoutRepository = WorkoutRepository.Companion.getInstance((Application) getApplicationContext());

        // Background initialization of database to avoid ANR during upgrade
        new Thread(() -> {
            try {
                WorkoutSummariesDatabaseManager.getInstance(TrackerService.this).getDatabase();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing WorkoutSummaries database", e);
            }
        }).start();

        // request bind to the BANAL Service
        bindService(new Intent(this, BANALService.class), mBanalConnection, Context.BIND_AUTO_CREATE);

        ContextCompat.registerReceiver(this, mSearchingFinishedReceiver, mSearchingFinishedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mAltitudeCorrectionReceiver, mAltitudeCorrectionFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mLapSummaryReceiver, mLapSummaryFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, mUserSelectedSportTypeChangedReceiver, new IntentFilter(BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT), ContextCompat.RECEIVER_NOT_EXPORTED);

        acquireWakeLock();
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "aTrainingTracker:TrackingLock");
            wakeLock.acquire();
            if (DEBUG) Log.d(TAG, "WakeLock acquired");
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
            if (DEBUG) Log.d(TAG, "WakeLock released");
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
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
                mLiveSession = new LiveWorkoutSession(mWorkoutID, IMPORTANT_SENSOR_TYPES);
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

        Notification notification = mTrainingApplication.getSearchingAndTrackingNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            // On API 34+, we MUST specify the type if it's in the manifest.
            // If permissions are missing or if we are in background without background permission,
            // it will throw SecurityException.
            int fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            
            // If we are resuming after being killed, we are likely in the background.
            // We should check if we have the necessary background permission if we want to use location type.
            if (intent == null && !hasBackgroundLocationPermission()) {
                Log.w(TAG, "Resuming TrackerService in background without background location permission. FGS might fail.");
            }
            
            if (!hasLocationPermission()) {
                Log.w(TAG, "Starting TrackerService without foreground location permission granted.");
            }

            startForeground(TrainingApplication.TRACKING_NOTIFICATION_ID, notification, fgsType);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29-33
            startForeground(TrainingApplication.TRACKING_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(TrainingApplication.TRACKING_NOTIFICATION_ID, notification);
        }


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

        mDbExecutor.shutdown();
        try {
            if (!mDbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                Log.w(TAG, "Database executor did not terminate in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // mTrainingApplication.setTracking(false);
        endWorkout();

        unbindService(mBanalConnection);
        mBanalService = null;


        unregisterReceiver(mSearchingFinishedReceiver);
        unregisterReceiver(mAltitudeCorrectionReceiver);
        unregisterReceiver(mLapSummaryReceiver);
        unregisterReceiver(mUserSelectedSportTypeChangedReceiver);

        releaseWakeLock();
    }

    private void recreateValuesWhenResuming() {

        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance(this).getDatabase();
        Cursor cursor = db.query(WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE, null, null, null, null, null, null);
        cursor.moveToLast();

        mBaseFileName = cursor.getString(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME));
        mWorkoutID = cursor.getInt(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID));
        mTimeTotal_s = cursor.getInt(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s));
        mTimeActive_s = cursor.getInt(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s));
        int calories = cursor.getInt(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES));
        int lapNr = cursor.getInt(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS));
        mDistanceTotal_m = cursor.getDouble(cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m));

        cursor.close();

        Log.d(TAG, "resuming with mBaseFileName=" + mBaseFileName
                + ", mWorkoutId=" + mWorkoutID
                + ", time_total_s=" + mTimeTotal_s
                + ", time_active_s=" + mTimeActive_s
                + ", calories=" + calories
                + ", lapNr=" + lapNr
                + ", distance_m=" + mDistanceTotal_m);

        BANALService.setInitialSensorValue(SensorType.TIME_TOTAL, mTimeTotal_s);
        BANALService.setInitialSensorValue(SensorType.TIME_ACTIVE, mTimeActive_s);
        BANALService.setInitialSensorValue(SensorType.CALORIES, calories);
        BANALService.setInitialSensorValue(SensorType.LAP_NR, lapNr);
        BANALService.setInitialSensorValue(SensorType.DISTANCE_m, mDistanceTotal_m);
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

    /**
     *  get the 'best' sport type based on the available equipment and average speed.
     */
    protected long getSportTypeId() {
        EquipmentAndSportTypeDiscoveryManager discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(this);

        if (mBanalService != null) {
            if (averageSpeedCalculateable()) {
                return discoveryManager.resolveSportType(
                        new HashSet<>(mBanalService.getDatabaseIdsOfActiveRemoteDevices()),
                        mBanalService.getBSportType(),
                        getAverageSpeed());
            }
            else {
                return discoveryManager.resolveSportType(
                        new HashSet<>(mBanalService.getDatabaseIdsOfActiveRemoteDevices()),
                        mBanalService.getBSportType());
            }
        }

        return BANALService.getDefaultSportTypeId();
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
        for (long deviceDbId : mBanalService.getDatabaseIdsOfActiveRemoteDevices()) {
            if (DEBUG) Log.d(TAG, "adding deviceId " + deviceDbId + " to list of active devices");
            values.put(ActiveDevices.DEVICE_DB_ID, deviceDbId);
            activeDevicesDb.insert(ActiveDevices.TABLE, null, values);
        }

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

        // Finalize Live Session (Auto Name, Commute, Trainer)
        if (mLiveSession != null) {
            finalizeLiveSession();
        }

        ExportManager exportManager = new ExportManager(this);
        exportManager.workoutFinished(mBaseFileName);

        sendBroadcast(new Intent(TRACKING_FINISHED_INTENT)
                .setPackage(getPackageName()));
    }

    private void sampleAndWriteToDb() {
        if (DEBUG) Log.d(TAG, "sampleAndWriteToDb()");

        if (mBanalService == null) return;

        // 1. Always capture Total Time (SCRUM-97: needs to update during pause)
        SensorData<Integer> totalTimeData = mBanalService.getBestSensorData(SensorType.TIME_TOTAL);
        if (totalTimeData != null && totalTimeData.getValue() != null) {
            mTimeTotal_s = totalTimeData.getValue();
        }

        if (TrainingApplication.isPaused()) {
            final long workoutId = mWorkoutID;
            final int timeTotal = mTimeTotal_s;
            
            mDbExecutor.submit(() -> {
                WorkoutSummariesDatabaseManager summariesManager = WorkoutSummariesDatabaseManager.getInstance(TrackerService.this);
                SQLiteDatabase summariesDb = summariesManager.getDatabase();
                
                ContentValues pausedValues = new ContentValues();
                pausedValues.put(WorkoutSummaries.TIME_TOTAL_s, timeTotal);
                
                summariesDb.beginTransaction();
                try {
                    summariesDb.update(WorkoutSummaries.TABLE, pausedValues, WorkoutSummaries.C_ID + "=?", new String[]{String.valueOf(workoutId)});
                    summariesDb.setTransactionSuccessful();
                } catch (Exception e) {
                    Log.e(TAG, "Error updating total time during pause", e);
                } finally {
                    summariesDb.endTransaction();
                }
                
                // Notify UI to refresh the card
                LocalBroadcastManager.getInstance(TrackerService.this).sendBroadcast(new Intent(WORKOUT_UPDATED_INTENT).putExtra("WORKOUT_ID", workoutId));
            });
            return;
        }

        final ContentValues samplingValues = new ContentValues();
        final ContentValues summaryValues = new ContentValues();
        final List<ExtremaUpdate> extremaUpdates = new java.util.ArrayList<>();
        final boolean updateAvgInDb = (mExtremaDbUpdateCounter >= EXTREMA_DB_UPDATE_INTERVAL);

        LatLng currentPosTemp = null;
        SensorData<Number> latData = mBanalService.getBestSensorData(SensorType.LATITUDE);
        SensorData<Number> lonData = mBanalService.getBestSensorData(SensorType.LONGITUDE);
        if (latData != null && lonData != null && latData.getValue() != null && lonData.getValue() != null) {
            if (SENSORS_TO_TRACK.contains(SensorType.LATITUDE) && SENSORS_TO_TRACK.contains(SensorType.LONGITUDE)) {
                currentPosTemp = new LatLng(latData.getValue().doubleValue(), lonData.getValue().doubleValue());
            }
        }
        final LatLng currentPos = currentPosTemp;

        Map<String, SensorValueType> sensorName2Type = new HashMap<>();

        // 1. Capture all data on the 1Hz sampling thread
        for (SensorData<Number> sensorData : mBanalService.getAllSensorData()) {
            if (sensorData == null || sensorData.getValue() == null) continue;

            SensorType sensorType = sensorData.getSensorType();

            if (mLiveSession != null && SENSORS_TO_TRACK.contains(sensorType)) {
                int changed = mLiveSession.addSample(sensorType, sensorData.getValue().doubleValue(), currentPos);

                if (changed != 0) {
                    LiveWorkoutSession.RunningStats stats = mLiveSession.getSensorStats().get(sensorType);

                    if ((changed & LiveWorkoutSession.RunningStats.CHANGED_MIN) != 0) {
                        mWorkoutRepository.updateExtremaValue(mWorkoutID, sensorType, ExtremaType.MIN, stats.min, stats.minPos);
                        extremaUpdates.add(new ExtremaUpdate(sensorType, ExtremaType.MIN, stats.min, stats.minPos));
                    }
                    if ((changed & LiveWorkoutSession.RunningStats.CHANGED_MAX) != 0) {
                        mWorkoutRepository.updateExtremaValue(mWorkoutID, sensorType, ExtremaType.MAX, stats.max, stats.maxPos);
                        extremaUpdates.add(new ExtremaUpdate(sensorType, ExtremaType.MAX, stats.max, stats.maxPos));
                    }
                    if ((changed & LiveWorkoutSession.RunningStats.CHANGED_AVG) != 0) {
                        // For Speed, we defer the average update until after the loop to use the 
                        // authoritative distance/time calculation (SCRUM-108)
                        if (sensorType != SensorType.SPEED_mps) {
                            mWorkoutRepository.updateExtremaValue(mWorkoutID, sensorType, ExtremaType.AVG, stats.getAverage(), null);
                            if (updateAvgInDb) {
                                extremaUpdates.add(new ExtremaUpdate(sensorType, ExtremaType.AVG, stats.getAverage(), null));
                            }
                        }
                    }
                }
            }

            String sensorName = sensorType.name();
            String deviceName = sensorData.getDeviceName();
            if (deviceName != null) {
                if (deviceName.equals("gps") || deviceName.equals("network") || deviceName.equals("google_fused")) {
                    sensorName += "_" + deviceName;
                } else {
                    sensorName += " (" + deviceName + ")";
                    sensorName = "'" + sensorName + "'";
                }
            }
            SensorValueType type = sensorType.getSensorValueType();
            sensorName2Type.put(sensorName, type);

            switch (type) {
                case INTEGER -> samplingValues.put(sensorName, sensorData.getValue().intValue());
                case DOUBLE -> samplingValues.put(sensorName, sensorData.getValue().doubleValue());
                default -> samplingValues.put(sensorName, sensorData.getStringValue());
            }

            switch (sensorType) {
                case TIME_TOTAL -> {
                    mTimeTotal_s = sensorData.getValue().intValue();
                    summaryValues.put(WorkoutSummaries.TIME_TOTAL_s, mTimeTotal_s);
                }
                case TIME_ACTIVE -> {
                    mTimeActive_s = sensorData.getValue().intValue();
                    summaryValues.put(WorkoutSummaries.TIME_ACTIVE_s, mTimeActive_s);
                }
                case DISTANCE_m -> {
                    mDistanceTotal_m = sensorData.getValue().doubleValue();
                    summaryValues.put(WorkoutSummaries.DISTANCE_TOTAL_m, mDistanceTotal_m);
                }
                case CALORIES -> summaryValues.put(WorkoutSummaries.CALORIES, sensorData.getValue().intValue());
                case LAP_NR -> summaryValues.put(WorkoutSummaries.LAPS, sensorData.getValue().intValue());
                case ASCENT -> summaryValues.put(WorkoutSummaries.ASCENDING, sensorData.getValue().doubleValue());
                case DESCENT -> summaryValues.put(WorkoutSummaries.DESCENDING, sensorData.getValue().doubleValue());
            }
        }

        if (averageSpeedCalculateable()) {
            double authoritativeAvgSpeed = getAverageSpeed();
            summaryValues.put(WorkoutSummaries.SPEED_AVERAGE_mps, authoritativeAvgSpeed);

            // Harmonize with Speed sensor average in Extrema Table (SCRUM-108)
            // We do this every sample to ensure UI consistency while tracking.
            if (mLiveSession != null && mLiveSession.getSensorStats().containsKey(SensorType.SPEED_mps)) {
                mWorkoutRepository.updateExtremaValue(mWorkoutID, SensorType.SPEED_mps, ExtremaType.AVG, authoritativeAvgSpeed, null);
                if (updateAvgInDb) {
                    extremaUpdates.add(new ExtremaUpdate(SensorType.SPEED_mps, ExtremaType.AVG, authoritativeAvgSpeed, null));
                }
            }
        }

        if (updateAvgInDb) {
            mExtremaDbUpdateCounter = 0;
        } else {
            mExtremaDbUpdateCounter++;
        }

        // Handle Streams (every 20s)
        final LiveWorkoutSession.StreamIncrement streamIncrement;
        if (mLiveSession != null) {
            SensorData<Number> altData = mBanalService.getBestSensorData(SensorType.ALTITUDE);
            SensorData<Number> distData = mBanalService.getBestSensorData(SensorType.DISTANCE_m);
            Double alt = (altData != null && altData.getValue() != null) ? altData.getValue().doubleValue() : null;
            Double dist = (distData != null && distData.getValue() != null) ? distData.getValue().doubleValue() : null;
            streamIncrement = mLiveSession.recordStreamPoint(currentPos, alt, dist);
        } else {
            streamIncrement = null;
        }

        final Map<String, SensorValueType> finalSensorName2Type = sensorName2Type;
        final String tableName = mSamplesTableName;
        final long workoutId = mWorkoutID;

        // 2. Offload all DB work to the dedicated executor
        mDbExecutor.submit(() -> {
            WorkoutSamplesDatabaseManager samplesManager = WorkoutSamplesDatabaseManager.getInstance(TrackerService.this);
            WorkoutSummariesDatabaseManager summariesManager = WorkoutSummariesDatabaseManager.getInstance(TrackerService.this);
            SQLiteDatabase samplesDb = samplesManager.getDatabase();
            SQLiteDatabase summariesDb = summariesManager.getDatabase();

            samplesDb.beginTransaction();
            summariesDb.beginTransaction();
            try {
                // Write Samples
                try {
                    samplesDb.insertOrThrow(tableName, null, samplingValues);
                } catch (SQLException e) {
                    // Logic to handle missing columns (simplified for brevity here, 
                    // but in real app we'd need to re-implement the ALTER TABLE logic if necessary)
                    // For SCRUM-100 we focus on the locking.
                    handleMissingColumns(samplesDb, tableName, samplingValues, finalSensorName2Type);
                }

                // Write Extrema
                for (ExtremaUpdate update : extremaUpdates) {
                    summariesManager.updateExtremaValue(summariesDb, workoutId, update.type, update.extrema, update.value, update.pos);
                }

                // Write Summary
                summariesDb.update(WorkoutSummaries.TABLE, summaryValues, WorkoutSummaries.C_ID + "=?", new String[]{String.valueOf(workoutId)});

                // Write Streams
                if (streamIncrement != null) {
                    summariesManager.appendToMapAndStreams(workoutId, streamIncrement.polylineIncrement, streamIncrement.altitudeIncrement, streamIncrement.distanceIncrement);
                }

                samplesDb.setTransactionSuccessful();
                summariesDb.setTransactionSuccessful();
            } catch (Exception e) {
                Log.e(TAG, "Error during async DB write", e);
            } finally {
                samplesDb.endTransaction();
                summariesDb.endTransaction();
            }

            // Notify UI
            LocalBroadcastManager.getInstance(TrackerService.this).sendBroadcast(new Intent(WORKOUT_UPDATED_INTENT).putExtra("WORKOUT_ID", workoutId));
        });
    }

    private void handleMissingColumns(SQLiteDatabase samplesDb, String tableName, ContentValues values, Map<String, SensorValueType> name2Type) {
        // Implementation of the ALTER TABLE logic removed from main flow for readability
        // It ensures that new sensors added during a workout get their own columns.
        try (Cursor cursor = samplesDb.query(tableName, null, null, null, null, null, null, "1")) {
            for (String key : values.keySet()) {
                String queryKey = key.replace("'", "");
                if (cursor.getColumnIndex(queryKey) < 0) {
                    String type = switch (name2Type.get(key)) {
                        case INTEGER -> "int";
                        case DOUBLE -> "double";
                        default -> "text";
                    };
                    samplesDb.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + key + " " + type + " null;");
                }
            }
            samplesDb.insert(tableName, null, values);
        } catch (Exception e) {
            Log.e(TAG, "Failed to fix schema or insert after schema fix", e);
        }
    }

    private static class ExtremaUpdate {
        final SensorType type;
        final ExtremaType extrema;
        final double value;
        final LatLng pos;

        ExtremaUpdate(SensorType type, ExtremaType extrema, double value, LatLng pos) {
            this.type = type;
            this.extrema = extrema;
            this.value = value;
            this.pos = pos;
        }
    }

    protected boolean averageSpeedCalculateable() {
        return mTimeActive_s != 0;
    }

    protected double getAverageSpeed() {
        return mDistanceTotal_m / mTimeActive_s;
    }



    private void finalizeLiveSession() {
        if (mLiveSession == null) return;
        if (DEBUG) Log.i(TAG, "finalizeLiveSession");

        WorkoutSummariesDatabaseManager summariesManager = WorkoutSummariesDatabaseManager.getInstance(this);
        WorkoutRepository repository = WorkoutRepository.Companion.getInstance((Application) getApplicationContext());

        // 1. Save START and END locations
        LatLng startPos = mLiveSession.getStartLatLng();
        LatLng endPos = mLiveSession.getLastLatLng();

        if (startPos != null) {
            summariesManager.updateExtremaValue(mWorkoutID, SensorType.LATITUDE, ExtremaType.START, startPos.latitude, startPos);
            summariesManager.updateExtremaValue(mWorkoutID, SensorType.LONGITUDE, ExtremaType.START, startPos.longitude, startPos);
        }

        if (endPos != null) {
            summariesManager.updateExtremaValue(mWorkoutID, SensorType.LATITUDE, ExtremaType.END, endPos.latitude, endPos);
            summariesManager.updateExtremaValue(mWorkoutID, SensorType.LONGITUDE, ExtremaType.END, endPos.longitude, endPos);
        }

        // 2. Save and Push Extrema Values
        for (Map.Entry<SensorType, LiveWorkoutSession.RunningStats> entry : mLiveSession.getSensorStats().entrySet()) {
            SensorType sensor = entry.getKey();
            LiveWorkoutSession.RunningStats stats = entry.getValue();

            // Min
            summariesManager.updateExtremaValue(mWorkoutID, sensor, ExtremaType.MIN, stats.min, stats.minPos);
            repository.updateExtremaValue(mWorkoutID, sensor, ExtremaType.MIN, stats.min, stats.minPos);
            // Max
            summariesManager.updateExtremaValue(mWorkoutID, sensor, ExtremaType.MAX, stats.max, stats.maxPos);
            repository.updateExtremaValue(mWorkoutID, sensor, ExtremaType.MAX, stats.max, stats.maxPos);
            // Avg
            if (IMPORTANT_SENSOR_TYPES.contains(sensor)) {
                double avgValue = stats.getAverage();
                // Special case: for Speed, use the authoritative distance/time average (SCRUM-108)
                if (sensor == SensorType.SPEED_mps && averageSpeedCalculateable()) {
                    avgValue = getAverageSpeed();
                }
                summariesManager.updateExtremaValue(mWorkoutID, sensor, ExtremaType.AVG, avgValue, null);
                repository.updateExtremaValue(mWorkoutID, sensor, ExtremaType.AVG, avgValue, null);
            }
        }

        // 3. Guess Commute and Trainer
        guessCommuteAndTrainer();

        // 4. Identity Determination (SCRUM-200)
        // Perform arbitration between hardware and route patterns
        EquipmentAndSportTypeDiscoveryManager discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(this);
        
        // Initial arbitration using currently active devices in memory (more reliable than waiting for DB sync)
        EquipmentAndSportTypeDiscoveryManager.InferredIdentity identity = discoveryManager.resolveIdentity(new HashSet<>(mBanalService.getDatabaseIdsOfActiveRemoteDevices()), mBanalService.getBSportType(), getAverageSpeed());

        LatLng startPosRaw = mLiveSession.getStartLatLng();
        LatLng endPosRaw = mLiveSession.getLastLatLng();
        LatLng maxDispPos = summariesManager.getExtremaPosition(mWorkoutID, SensorType.LINE_DISTANCE_m, ExtremaType.MAX);

        if (startPosRaw != null && endPosRaw != null && maxDispPos != null) {
            WorkoutClusterEngine engine = WorkoutClusterEngine.Companion.getInstance(this);
            WorkoutCluster suggestion = engine.suggestCluster(startPosRaw, endPosRaw, maxDispPos, mDistanceTotal_m, null, mBanalService.getBSportType());
            if (suggestion != null) {
                // If hardware confidence is high, we only take the name from the cluster
                if (identity.isHighConfidence()) {
                    ContentValues nameValues = new ContentValues();
                    String autoName = getString(R.string.cluster_autoname_format, suggestion.getName(), suggestion.getHitCount() + 1);
                    nameValues.put(WorkoutSummaries.WORKOUT_NAME, autoName);
                    nameValues.put(WorkoutSummaries.CLUSTER_ID, suggestion.getId());
                    summariesManager.getDatabase().update(WorkoutSummaries.TABLE, nameValues, WorkoutSummaries.C_ID + "=?", new String[]{String.valueOf(mWorkoutID)});
                    
                    // Apply hardware-based identity (Sport, Gear, Strava)
                    summariesManager.applyInferredIdentity(mWorkoutID, identity);
                } else {
                    // Low hardware confidence -> Workout Cluster wins everything
                    engine.assignClusterToWorkout(this, mWorkoutID, suggestion.getId());
                }
            } else {
                // No cluster match -> use hardware identity
                summariesManager.applyInferredIdentity(mWorkoutID, identity);
            }
        } else {
            // No spatial data -> use hardware identity
            summariesManager.applyInferredIdentity(mWorkoutID, identity);
        }

        // 5. Finalize Map and Streams (one last check)
        String polyline = PolyUtil.encode(mLiveSession.getSampledLatLngs());
        String altStream = NumericalEncodingUtils.INSTANCE.encodeDoubles(mLiveSession.getSampledAltitudes());
        String distStream = NumericalEncodingUtils.INSTANCE.encodeDoubles(mLiveSession.getSampledDistances());
        summariesManager.updateMapAndStreams(mWorkoutID, polyline, altStream, distStream);
        repository.setMapPolyline(mWorkoutID, polyline);
        repository.setElevationStreams(mWorkoutID, altStream, distStream);
    }

    private void guessCommuteAndTrainer() {
        if (DEBUG) Log.i(TAG, "guessCommuteAndTrainer");
        WorkoutSummariesDatabaseManager summariesManager = WorkoutSummariesDatabaseManager.getInstance(this);

        Double distance = summariesManager.getDouble(mWorkoutID, WorkoutSummaries.DISTANCE_TOTAL_m);
        Double maxLineDistance = summariesManager.getExtremaValue(mWorkoutID, SensorType.LINE_DISTANCE_m, ExtremaType.MAX);
        Double endLineDistance = summariesManager.getExtremaValue(mWorkoutID, SensorType.LINE_DISTANCE_m, ExtremaType.END);

        boolean commute = false, trainer = false;
        if (maxLineDistance != null) {
            if (distance != null && maxLineDistance < TrainingApplication.DISTANCE_TO_MAX_THRESHOLD_FOR_TRAINER) {
                trainer = true;
            }
            if (endLineDistance != null && maxLineDistance < endLineDistance * TrainingApplication.DISTANCE_TO_MAX_RATIO_FOR_COMMUTE) {
                commute = true;
            }
        } else {
            trainer = true;
        }

        if (commute ^ trainer) {
            ContentValues values = new ContentValues();
            values.put(WorkoutSummaries.COMMUTE, commute);
            values.put(WorkoutSummaries.TRAINER, trainer);
            summariesManager.getDatabase().update(WorkoutSummaries.TABLE, values, WorkoutSummaries.C_ID + "=?", new String[]{Long.toString(mWorkoutID)});
            
            WorkoutRepository.Companion.getInstance((Application) getApplicationContext()).setCommuteAndTrainer(mWorkoutID, commute, trainer);
        }
    }


    public enum StartType {START_NORMAL, RESUME_BY_USER, RESUME_SERVICE_RECREATION}

}
