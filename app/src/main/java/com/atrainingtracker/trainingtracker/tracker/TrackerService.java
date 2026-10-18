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
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import java.util.concurrent.CompletableFuture;
import com.atrainingtracker.util.EnumUtils;
import com.atrainingtracker.util.LifecycleUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation;
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutAutoNamingHelper;
import com.atrainingtracker.trainingtracker.database.WorkoutSource;

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
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig;
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseManager;
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseState;
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseTransition;
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper;
import com.atrainingtracker.trainingtracker.settings.TuningConfig;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import com.atrainingtracker.banalservice.BSportType;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    static {
        IMPORTANT_SENSOR_TYPES.add(SensorType.W_PRIME_BAL);
    }

    // Sensors to track for Min/Max and used for average if in IMPORTANT_SENSOR_TYPES
    private static final HashSet<SensorType> SENSORS_TO_TRACK = new HashSet<>(IMPORTANT_SENSOR_TYPES);
    static {
        SENSORS_TO_TRACK.add(SensorType.LATITUDE);
        SENSORS_TO_TRACK.add(SensorType.LONGITUDE);
        SENSORS_TO_TRACK.add(SensorType.LINE_DISTANCE_m);
        SENSORS_TO_TRACK.add(SensorType.W_PRIME_BAL);
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
    volatile BANALServiceComm mBanalService;
    private final AtomicInteger mSamplingErrorCount = new AtomicInteger(0);
    public static final int SAMPLE_INSERT_FAILURE_INITIAL_BURST = 3;
    public static final int SAMPLE_INSERT_FAILURE_LOG_INTERVAL = 60;
    private final AtomicInteger mSampleInsertFailureCount = new AtomicInteger(0);
    private android.os.PowerManager.WakeLock wakeLock;
    private TrainingApplication mTrainingApplication;
    private WorkoutRepository mWorkoutRepository;
    private ScheduledFuture mTrackerHandle;

    /**
     * Returns the cumulative count of sample row insertion failures during the current service
     * lifecycle ({@code REQ-TRK-029.3}).
     */
    @VisibleForTesting
    public int getSampleInsertFailureCount() {
        return mSampleInsertFailureCount.get();
    }

    private final AutoPauseManager mAutoPauseManager = new AutoPauseManager();

    public AutoPauseManager getAutoPauseManager() {
        return mAutoPauseManager;
    }

    private static volatile TrackerService sInstance = null;

    public static void invalidateAutoPauseCache() {
        TrackerService service = sInstance;
        if (service != null) {
            service.invalidateAutoPauseConfigCache();
        }
    }

    public static void notifyManualResume() {
        TrackerService service = sInstance;
        if (service != null) {
            service.mAutoPauseManager.onManualResume();
        }
    }

    private volatile AutoPauseConfig mCachedAutoPauseConfig = null;
    private long mLastAutoPauseConfigFetchMs = 0L;
    private static final long AUTO_PAUSE_CONFIG_CACHE_TTL_MS = 5000L;

    public AutoPauseConfig getAutoPauseConfig() {
        long now = System.currentTimeMillis();
        boolean currentIsMetric = (TrainingApplication.getUnit() == com.atrainingtracker.trainingtracker.MyUnits.METRIC);
        if (mCachedAutoPauseConfig == null 
                || (now - mLastAutoPauseConfigFetchMs) > AUTO_PAUSE_CONFIG_CACHE_TTL_MS
                || mCachedAutoPauseConfig.isMetric() != currentIsMetric) {
            mCachedAutoPauseConfig = SettingsDataStoreJavaHelper.getAutoPauseConfig(this);
            mLastAutoPauseConfigFetchMs = now;
        }
        return mCachedAutoPauseConfig;
    }

    public void invalidateAutoPauseConfigCache() {
        mCachedAutoPauseConfig = null;
    }

    private int mExtremaDbUpdateCounter = 0;
    private static final int EXTREMA_DB_UPDATE_INTERVAL = 10; // Update Avg in DB every 10 seconds
    // int            mCalories        = 0;
    // double         mSpeedAverage_mps = 0.0;


    // int    mPrevLapTimeTotal_s      = 0;
    // double mPrevLapDistanceTotal_m  = 0.0;


    public static final int TRACKING_INTERRUPTED_NOTIFICATION_ID = 2;
    private boolean mTrackingInterrupted = false;
    private volatile long mWorkoutID;
    private final Set<Long> mActiveSessionDeviceIds = Collections.synchronizedSet(new HashSet<>());
    private LiveWorkoutSession mLiveSession;
    private volatile CompletableFuture<Void> mTableInitializationFuture = CompletableFuture.completedFuture(null);
    private StartType mStartType;
    private boolean mWorkoutStartLocationRecorded = false;

    public CompletableFuture<Void> getTableInitializationFuture() {
        return mTableInitializationFuture;
    }
    final BroadcastReceiver mLapSummaryReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, @NonNull Intent intent) {
            if (DEBUG) Log.i(TAG, "received lap summary intent");

            int lapTime = intent.getIntExtra(BANALService.PREV_LAP_TIME_S, 0);
            double lapDistance = intent.getDoubleExtra(BANALService.PREV_LAP_DISTANCE_m, 0);

            // ATT-896 / REQ-TRK-002: Discard empty split summaries
            if (lapTime <= 0 && lapDistance <= 0.0) {
                if (DEBUG) Log.i(TAG, "Ignoring zero-duration lap summary broadcast");
                return;
            }

            double lapSpeed = intent.getDoubleExtra(BANALService.PREV_LAP_SPEED_mps, 0);
            if (Double.isNaN(lapSpeed) || Double.isInfinite(lapSpeed)) {
                lapSpeed = (lapTime > 0) ? (lapDistance / lapTime) : 0.0;
            }

            saveLap(intent.getIntExtra(BANALService.PREV_LAP_NR, 0),
                    lapTime,
                    lapDistance,
                    lapSpeed);
        }
    };
    private String mBaseFileName;

    // private String mSport;  
    // private String mGCDataString;
    // private String[] mSensorNames;
    private volatile String mSamplesTableName;
    private final BroadcastReceiver mAltitudeCorrectionReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            double altitudeCorrection = intent.getDoubleExtra(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE, 0.0);
            if (altitudeCorrection == 0.0) return;

            // REQ-CON-017: Reject runaway retroactive shifts exceeding 500m
            if (Math.abs(altitudeCorrection) > 500.0) {
                Log.w(TAG, "Rejecting non-physical altitude correction shift: " + altitudeCorrection + " m");
                return;
            }

            if (DEBUG)
                Log.i(TAG, "Triggering atomic altitude correction by " + altitudeCorrection);

            // 1. Live Session Update
            if (mLiveSession != null) {
                mLiveSession.applyAltitudeCorrection(altitudeCorrection);
            }

            // 2. Database Synchronization (offloaded to DB executor via non-blocking barrier)
            mTableInitializationFuture.thenAcceptAsync(v -> {
                final long workoutId = mWorkoutID;
                final String samplesTable = (mSamplesTableName != null && !mSamplesTableName.trim().isEmpty())
                        ? mSamplesTableName
                        : resolveSamplesTableNameSafely(mBaseFileName);
                if (mTrackingInterrupted || workoutId <= 0 || samplesTable == null || samplesTable.isEmpty()) {
                    Log.w(TAG, "Skipping altitude correction: invalid workoutId (" + workoutId + ") or samplesTable (" + samplesTable + ")");
                    return;
                }

                WorkoutSamplesDatabaseManager samplesManager = WorkoutSamplesDatabaseManager.getInstance(TrackerService.this);
                WorkoutSummariesDatabaseManager summariesManager = WorkoutSummariesDatabaseManager.getInstance(TrackerService.this);

                // 2a. Raw Samples shift in explicit atomic SQLite transaction (REQ-CON-017)
                android.database.sqlite.SQLiteDatabase db = samplesManager.getDatabase();
                db.beginTransaction();
                try {
                    String operator = altitudeCorrection >= 0 ? " + " : " - ";
                    db.execSQL("UPDATE " + samplesTable
                            + " set " + SensorType.ALTITUDE.name() + " = " + SensorType.ALTITUDE.name() + operator + Math.abs(altitudeCorrection));
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }

                // 2b. Summary Extrema and Elevation Stream shift (ATT-38)
                summariesManager.shiftAltitudeData(workoutId, altitudeCorrection);

                // 3. Notify UI/Repository to refresh from DB
                LocalBroadcastManager.getInstance(TrackerService.this).sendBroadcast(new Intent(WORKOUT_UPDATED_INTENT).putExtra(WORKOUT_ID, workoutId));
            }, mDbExecutor).exceptionally(ex -> {
                Log.e(TAG, "Altitude shift skipped due to initialization failure: " + ex.getMessage());
                return null;
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
        @Override
        public void run() {
            try {
                if (DEBUG) {
                    Log.d(TAG, "sampling/tracking");
                }

                final BANALServiceComm banalService = mBanalService;
                if (banalService != null) {

                    sampleAndWriteToDb(banalService);

                    // update notification
                    if (DEBUG) Log.i(TAG, "updating notification");
                    if (mTrainingApplication != null) {
                        mTrainingApplication.updateTimeAndDistanceToNotification(
                                banalService.getBestSensorData(SensorType.TIME_ACTIVE),
                                banalService.getBestSensorData(SensorType.DISTANCE_m),
                                SportTypeDatabaseManager.getInstance(TrackerService.this).getUIName(banalService.getSportTypeId()));
                    }
                    if (DEBUG) Log.i(TAG, "updated notification");

                }
            } catch (Throwable t) {
                mSamplingErrorCount.incrementAndGet();
                Log.e(TAG, "Error during 1 Hz sampling tick; continuing tracking loop", t);
            }
        }
    };
    // class BANALConnection implements ServiceConnection
    private final ServiceConnection mBanalConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBanalService = (BANALServiceComm) service;
            if (DEBUG) Log.i(TAG, "connected to BANAL Service");

            // ATT-3045: Ensure location devices are checked/initialized defensively when service connects
            BANALService.checkOrInitializeLocationDevices();

            // REQ-TRK-026.3 (ATT-3980): Restore resumed workout state (mWorkoutID, mBaseFileName,
            // mSamplesTableName, and initial sensor values including LAP_NR) BEFORE createNewLap()
            // or onSearchingFinished() executes.
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

            if (mCreateNewLapWhenConnectedToBanalService) {
                mCreateNewLapWhenConnectedToBanalService = false;
                createNewLap();
            }

            if (!BANALService.isSearching()) {
                onSearchingFinished();
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
        sInstance = this;
        if (DEBUG) {
            Log.d(TAG, "onCreate");
        }

        // ATT-3045: Ensure location devices are checked/initialized defensively upon service creation
        BANALService.checkOrInitializeLocationDevices();

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

    protected boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    protected boolean hasBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Returns the active Android platform API version code.
     * Overridable in test suites to verify version-gated Foreground Service behavior.
     */
    protected int getBuildVersionSdkInt() {
        return Build.VERSION.SDK_INT;
    }

    /**
     * Verifies whether the application has been granted permissions required for the
     * Android 14+ (API 34+) Health Foreground Service type.
     * Enforces the Android contract requiring both FOREGROUND_SERVICE_HEALTH and a health
     * runtime permission (e.g. ACTIVITY_RECOGNITION).
     */
    protected boolean hasHealthPermission() {
        if (getBuildVersionSdkInt() >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            boolean hasHealthFgs = ContextCompat.checkSelfPermission(this, "android.permission.FOREGROUND_SERVICE_HEALTH") == android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean hasActivityRec = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            return hasHealthFgs && hasActivityRec;
        }
        return false;
    }

    /**
     * Dynamically derives the permitted Foreground Service type bitmask based on held runtime permissions.
     * Strictly omits FOREGROUND_SERVICE_TYPE_HEALTH unless health runtime permissions are granted,
     * preventing fatal SecurityExceptions on Android 14+ (REQ-TRK-015, REQ-TRK-022, ATT-2972, ATT-3567).
     */
    protected int determineForegroundServiceType() {
        int foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
        if (getBuildVersionSdkInt() >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (hasHealthPermission()) {
                foregroundServiceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH;
            }
        }
        return foregroundServiceType;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (DEBUG) {
            Log.d(TAG, "onStartCommand Received start id " + startId + ": " + intent);
        }

        // On Android 14+ (API 34+), promoting a background service to a foreground service of type location
        // requires background location permission. If the service was recreated by the OS after being killed
        // (intent == null) and the user has not granted background location permission, starting the FGS
        // will throw a fatal SecurityException. We catch this condition early, notify the user, and gracefully stop.
        if (intent == null && !hasBackgroundLocationPermission()) {
            Log.w(TAG, "Recreating TrackerService in background without background location permission. Cannot start location FGS.");
            mTrackingInterrupted = true;
            showTrackingInterruptedNotification();
            performStopSelf();
            return Service.START_STICKY;
        }

        StartType startType;
        if (intent == null) {
            startType = StartType.RESUME_SERVICE_RECREATION;
        } else {
            startType = EnumUtils.safeValueOf(StartType.class, intent.getStringExtra(START_TYPE), StartType.START_NORMAL, TAG);
        }
        mStartType = startType;
        mWorkoutStartLocationRecorded = (startType != StartType.START_NORMAL);
        switch (startType) {
            case START_NORMAL:
                if (DEBUG) Log.d(TAG, "starting a new workout");
                // The workout name is just the date+time
                mBaseFileName = (new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)).format(new Date());
                mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName);
                mLiveSession = new LiveWorkoutSession(0, IMPORTANT_SENSOR_TYPES);

                final CompletableFuture<Void> initFuture = new CompletableFuture<>();
                mTableInitializationFuture = initFuture;

                mDbExecutor.submit(() -> {
                    try {
                        mWorkoutID = createNewWorkout();
                        mActiveSessionDeviceIds.clear();
                        updateActiveSessionDevices();
                        if (mLiveSession != null) {
                            mLiveSession.setWorkoutId(mWorkoutID);
                        }
                        WorkoutSamplesDatabaseManager.getInstance(TrackerService.this)
                                .createNewTable(mBaseFileName, Arrays.asList(SensorType.values()));
                        initFuture.complete(null);
                        notifyTrackingStarted(mWorkoutID);
                    } catch (Throwable t) {
                        Log.e(TAG, "Fatal error initializing workout database asynchronously", t);
                        initFuture.completeExceptionally(t);
                        mTrackingInterrupted = true;
                        if (mTrackerHandle != null) {
                            mTrackerHandle.cancel(true);
                            mTrackerHandle = null;
                        }
                        showTrackingInterruptedNotification();
                        performStopSelf();
                    }
                });
                break;

            case RESUME_BY_USER:
                Log.d(TAG, "resuming by user request");
                mAutoPauseManager.onManualResume();
                if (mTableInitializationFuture == null || mTableInitializationFuture.isDone()) {
                    mTableInitializationFuture = new CompletableFuture<>();
                }
                if (mBanalService != null) {
                    recreateValuesWhenResuming();
                    // mBanalService.resumeFromPaused();  already started by broadcast?
                } else {
                    mResumeFromPausedWhenConnectedToBanalService = true;
                }
                break;

            case RESUME_SERVICE_RECREATION:
                Log.d(TAG, "resuming after killed service");
                if (mTableInitializationFuture == null || mTableInitializationFuture.isDone()) {
                    mTableInitializationFuture = new CompletableFuture<>();
                }
                // 3-Tier Startup State-Recovery Hierarchy (REQ-TRK-018):
                if (TrainingApplication.isManualPauseLocked()) {
                    Log.i(TAG, "Startup recovery Tier 1: restoring MANUAL_PAUSED");
                    if (mTrainingApplication != null) {
                        mTrainingApplication.pauseTracking(false);
                    }
                    mAutoPauseManager.setState(AutoPauseState.MANUAL_PAUSED);
                } else if (TrainingApplication.isAutoPaused()) {
                    Log.i(TAG, "Startup recovery Tier 2: restoring AUTO_PAUSED");
                    if (mTrainingApplication != null) {
                        mTrainingApplication.pauseTracking(true);
                    }
                    mAutoPauseManager.setState(AutoPauseState.AUTO_PAUSED);
                } else {
                    Log.i(TAG, "Startup recovery Tier 3: restoring TRACKING");
                    if (mTrainingApplication != null) {
                        mTrainingApplication.setTracking();
                    }
                    mAutoPauseManager.setState(AutoPauseState.TRACKING);
                }
                mAutoPauseManager.resetDebounceCounter();
                if (mBanalService != null) {
                    recreateValuesWhenResuming();
                    // mBanalService.resumeTracking(); already started by broadcast?
                } else {
                    mResumeTrackingWhenConnectedToBanalService = true;
                }
                break;
        }

        if (startType != StartType.START_NORMAL && mBaseFileName != null && !mBaseFileName.trim().isEmpty()) {
            mSamplesTableName = resolveSamplesTableNameSafely(mBaseFileName);
        }

        if (mBanalService != null && !BANALService.isSearching()) {
            onSearchingFinished();
        }

        // start tracking
        mTrackerHandle = mScheduler.scheduleAtFixedRate(tracker, 0, // initial delay
                1, // sampling time
                TimeUnit.SECONDS);

        Notification notification = mTrainingApplication != null ? mTrainingApplication.getSearchingAndTrackingNotification() : null;
        if (notification != null) {
            try {
                performStartForeground(TrainingApplication.TRACKING_NOTIFICATION_ID, notification, determineForegroundServiceType());
            } catch (SecurityException | IllegalStateException e) {
                Log.e(TAG, "Failed to start foreground service: " + e.getMessage(), e);
                mTrackingInterrupted = true;
                if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) {
                    mTableInitializationFuture.cancel(false);
                }
                if (mTrackerHandle != null) {
                    mTrackerHandle.cancel(true);
                    mTrackerHandle = null;
                }
                showTrackingInterruptedNotification();
                performStopSelf();
                return Service.START_STICKY;
            }
        }

        // We want this service to continue running until it is explicitly stopped, so return sticky.
        // When the service is stopped due to a lack of memory, it will be recreated and this method called with a null intent, see:
        // https://android-developers.googleblog.com/2010/02/service-api-changes-starting-with.html
        return Service.START_STICKY;
    }

    protected void performStopSelf() {
        stopSelf();
    }

    protected void performStartForeground(int id, Notification notification, int foregroundServiceType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, foregroundServiceType);
        } else {
            startForeground(id, notification);
        }
    }

    public static void showTrackingInterruptedNotification(Context context) {
        if (context == null) {
            return;
        }
        Intent resumeIntent = new Intent(context, MainActivityWithNavigation.class);
        resumeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        resumeIntent.putExtra(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.START_OR_TRACKING.name());
        resumeIntent.putExtra(MainActivityWithNavigation.EXTRA_RESUME_INTERRUPTED_WORKOUT, true);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, resumeIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, TrainingApplication.NOTIFICATION_CHANNEL__TRACKING_2)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(context.getString(R.string.tracking_interrupted_notification_title))
                .setContentText(context.getString(R.string.tracking_interrupted_notification_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notificationManager.notify(TRACKING_INTERRUPTED_NOTIFICATION_ID, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to show tracking interrupted notification: " + e.getMessage(), e);
        }
    }

    protected void showTrackingInterruptedNotification() {
        showTrackingInterruptedNotification(this);
    }

    public boolean isTrackingInterrupted() {
        return mTrackingInterrupted;
    }

    void setTrackingInterruptedForTesting(boolean interrupted) {
        this.mTrackingInterrupted = interrupted;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    protected void notifyTrackingStarted(long workoutId) {
        try {
            Intent trackingStartedIntent = new Intent(TRACKING_STARTED_INTENT)
                    .putExtra(WorkoutSummaries.WORKOUT_ID, workoutId)
                    .setPackage(this.getPackageName());
            this.sendBroadcast(trackingStartedIntent);
        } catch (NullPointerException e) {
            if (getBaseContext() != null) {
                throw e;
            }
        }
    }

    protected void performSuperOnDestroy() {
        super.onDestroy();
    }

    @Override
    public void onDestroy() {
        performSuperOnDestroy();
        if (sInstance == this) {
            sInstance = null;
        }
        if (DEBUG) Log.d(TAG, "onDestroy");

        if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) {
            mTableInitializationFuture.cancel(false);
        }

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

        if (!mTrackingInterrupted) {
            endWorkout();
        }

        unbindService(mBanalConnection);
        mBanalService = null;


        LifecycleUtils.safeUnregisterReceiver(this, mSearchingFinishedReceiver, TAG);
        LifecycleUtils.safeUnregisterReceiver(this, mAltitudeCorrectionReceiver, TAG);
        LifecycleUtils.safeUnregisterReceiver(this, mLapSummaryReceiver, TAG);
        LifecycleUtils.safeUnregisterReceiver(this, mUserSelectedSportTypeChangedReceiver, TAG);

        mAutoPauseManager.release();
        releaseWakeLock();
    }

    private static final String[] RESUME_WORKOUT_PROJECTION = new String[] {
            WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m
    };

    @VisibleForTesting
    static final class ResumedWorkoutState {
        final String baseFileName;
        final long workoutId;
        final int timeTotal_s;
        final int timeActive_s;
        final int calories;
        final int lapNr;
        final double distanceTotal_m;

        ResumedWorkoutState(@NonNull String baseFileName, long workoutId, int timeTotal_s, int timeActive_s, int calories, int lapNr, double distanceTotal_m) {
            this.baseFileName = baseFileName;
            this.workoutId = workoutId;
            this.timeTotal_s = timeTotal_s;
            this.timeActive_s = timeActive_s;
            this.calories = calories;
            this.lapNr = lapNr;
            this.distanceTotal_m = distanceTotal_m;
        }
    }

    /**
     * Resolves the quoted sample table name for the given {@code baseFileName} safely at the
     * service boundary ({@code REQ-TRK-026.2}, {@code ATT-3980}).
     */
    @Nullable
    @VisibleForTesting
    static String resolveSamplesTableNameSafely(@Nullable String baseFileName) {
        if (baseFileName == null || baseFileName.trim().isEmpty() || "null".equalsIgnoreCase(baseFileName.trim())) {
            Log.e(TAG, "Cannot resolve samples table name for invalid baseFileName: " + baseFileName);
            return null;
        }
        try {
            return WorkoutSamplesDatabaseManager.getTableName(baseFileName);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Cannot resolve samples table name for invalid baseFileName: " + baseFileName, e);
            return null;
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    void recreateValuesWhenResuming() {
        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance(this).getDatabase();
        ResumedWorkoutState state = null;

        try (Cursor cursor = db.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                RESUME_WORKOUT_PROJECTION,
                null,
                null,
                null,
                null,
                WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID + " DESC",
                "1")) {

            if (cursor != null && cursor.moveToFirst()) {
                int baseFileNameCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME);
                int idCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID);
                int timeTotalCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s);
                int timeActiveCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s);
                int caloriesCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES);
                int lapsCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS);
                int distanceCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m);

                state = new ResumedWorkoutState(
                        cursor.getString(baseFileNameCol),
                        cursor.getLong(idCol),
                        cursor.getInt(timeTotalCol),
                        cursor.getInt(timeActiveCol),
                        cursor.getInt(caloriesCol),
                        cursor.getInt(lapsCol),
                        cursor.getDouble(distanceCol)
                );
            } else {
                Log.w(TAG, "recreateValuesWhenResuming: No workout summary record found to resume.");
                if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) {
                    mTableInitializationFuture.completeExceptionally(
                            new IllegalStateException("No workout summary record found to resume"));
                }
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "recreateValuesWhenResuming: Failed to extract resume record from database", e);
            if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) {
                mTableInitializationFuture.completeExceptionally(e);
            }
            return;
        }

        applyResumedState(state);
    }

    /**
     * Applies restored workout state on resume ({@code RESUME_BY_USER} or {@code RESUME_SERVICE_RECREATION}),
     * synchronizing {@code mSamplesTableName} with {@code mBaseFileName}, completing
     * {@code mTableInitializationFuture}, and broadcasting {@code notifyTrackingStarted(mWorkoutID)}
     * ({@code REQ-TRK-026.1}, {@code REQ-TRK-026.3}, {@code ATT-3980}).
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    void applyResumedState(@NonNull ResumedWorkoutState state) {
        mBaseFileName = state.baseFileName;
        mSamplesTableName = resolveSamplesTableNameSafely(mBaseFileName);
        mWorkoutID = state.workoutId;
        mTimeTotal_s = state.timeTotal_s;
        mTimeActive_s = state.timeActive_s;
        mDistanceTotal_m = state.distanceTotal_m;

        if (mSamplesTableName == null) {
            Log.e(TAG, "applyResumedState: Invalid baseFileName (" + mBaseFileName + ") for workoutId=" + mWorkoutID);
            if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) {
                mTableInitializationFuture.completeExceptionally(
                        new IllegalStateException("Invalid baseFileName on resume: " + mBaseFileName));
            }
            mTrackingInterrupted = true;
            showTrackingInterruptedNotification();
            performStopSelf();
            return;
        }

        Log.d(TAG, "resuming with mBaseFileName=" + mBaseFileName
                + ", mSamplesTableName=" + mSamplesTableName
                + ", mWorkoutId=" + mWorkoutID
                + ", time_total_s=" + mTimeTotal_s
                + ", time_active_s=" + mTimeActive_s
                + ", calories=" + state.calories
                + ", lapNr=" + state.lapNr
                + ", distance_m=" + mDistanceTotal_m);

        BANALService.setInitialSensorValue(SensorType.TIME_TOTAL, mTimeTotal_s);
        BANALService.setInitialSensorValue(SensorType.TIME_ACTIVE, mTimeActive_s);
        BANALService.setInitialSensorValue(SensorType.CALORIES, state.calories);
        BANALService.setInitialSensorValue(SensorType.LAP_NR, state.lapNr);
        BANALService.setInitialSensorValue(SensorType.DISTANCE_m, mDistanceTotal_m);

        if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) {
            mTableInitializationFuture.complete(null);
        }
        if (mWorkoutID > 0) {
            notifyTrackingStarted(mWorkoutID);
        }
    }

    @VisibleForTesting
    long getWorkoutID() {
        return mWorkoutID;
    }

    @VisibleForTesting
    String getBaseFileName() {
        return mBaseFileName;
    }

    @VisibleForTesting
    String getSamplesTableName() {
        return mSamplesTableName;
    }

    @VisibleForTesting
    ServiceConnection getBanalConnectionForTesting() {
        return mBanalConnection;
    }

    @VisibleForTesting
    int getTimeTotal_s() {
        return mTimeTotal_s;
    }

    @VisibleForTesting
    int getTimeActive_s() {
        return mTimeActive_s;
    }

    @VisibleForTesting
    double getDistanceTotal_m() {
        return mDistanceTotal_m;
    }

    /**
     * Returns the number of caught transient exceptions during the 1 Hz sampling loop ({@code REQ-TRK-027.1}, {@code ATT-3981}).
     */
    @VisibleForTesting
    int getSamplingErrorCount() {
        return mSamplingErrorCount.get();
    }

    /**
     * Finalizes and records the currently active lap at the conclusion of a workout session.
     *
     * <p>Functional Description: Queries {@link BANALService} for accumulated lap metrics
     * (lap number, lap time, and lap distance). If the lap metrics represent non-zero athletic effort,
     * it calculates the average lap speed and persists the lap record to {@link LapsDatabaseManager}.
     *
     * <p>Implementation Logic: In accordance with {@code REQ-TRK-002} and defect fix {@code ATT-896},
     * zero-duration/zero-distance phantom laps are strictly discarded to avoid persisting empty 0s splits
     * when a session is ended without active movement or immediately following a split. Additionally,
     * division by zero when calculating {@code lapSpeed} is safely guarded.
     */
    protected void createNewLap() {
        if (DEBUG) Log.i(TAG, "createNewLap");

        final BANALServiceComm banalService = mBanalService;
        if (banalService == null) {
            mCreateNewLapWhenConnectedToBanalService = true;
        } else {

            SensorData sensorData;

            int prevLapNr = 0;
            sensorData = banalService.getBestSensorData(SensorType.LAP_NR);
            if (sensorData != null && sensorData.getValue() != null) {
                prevLapNr = (Integer) sensorData.getValue();
            }

            int lapTime_s = 0;
            sensorData = banalService.getBestSensorData(SensorType.TIME_LAP);
            if (sensorData != null
                    && sensorData.getValue() != null) {
                lapTime_s = (Integer) sensorData.getValue();
            }

            double lapDistance = 0.0;
            sensorData = banalService.getBestSensorData(SensorType.DISTANCE_m_LAP);
            if (sensorData != null
                    && sensorData.getValue() != null) {
                lapDistance = (Double) sensorData.getValue();
            }

            // ATT-896 / REQ-TRK-002: Guard against saving zero-duration / zero-distance phantom laps
            if (lapTime_s <= 0 && lapDistance <= 0.0) {
                if (DEBUG) Log.i(TAG, "Discarding zero-duration/zero-distance lap in createNewLap");
                return;
            }

            double lapSpeed = (lapTime_s > 0) ? (lapDistance / lapTime_s) : 0.0;

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

        final BANALServiceComm banalService = mBanalService;
        if (banalService != null) {
            if (averageSpeedCalculateable()) {
                return discoveryManager.resolveSportType(
                        new HashSet<>(banalService.getDatabaseIdsOfActiveRemoteDevices()),
                        banalService.getBSportType(),
                        getAverageSpeed());
            }
            else {
                return discoveryManager.resolveSportType(
                        new HashSet<>(banalService.getDatabaseIdsOfActiveRemoteDevices()),
                        banalService.getBSportType());
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
        values.put(WorkoutSummaries.SOURCE, WorkoutSource.TRACKED.name());
        values.put(WorkoutSummaries.FINISHED, 0);

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
        final BANALServiceComm banalService = mBanalService;
        if (banalService == null) {
            return;
        }

        long sportTypeId = getSportTypeId();

        // now, that we know the sport and the available sensors, we update the summaries DB
        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.SPORT_ID, sportTypeId);
        values.put(WorkoutSummaries.B_SPORT, SportTypeDatabaseManager.getInstance(this).getBSportType(sportTypeId).name());
        values.put(WorkoutSummaries.GC_DATA, banalService.getGCDataString());

        mTableInitializationFuture.thenAcceptAsync(v -> {
            try {
                WorkoutSummariesDatabaseManager databaseManager = WorkoutSummariesDatabaseManager.getInstance(TrackerService.this);
                SQLiteDatabase summariesDb = databaseManager.getDatabase();
                summariesDb.update(WorkoutSummaries.TABLE,
                        values,
                        WorkoutSummaries.C_ID + "=?",
                        new String[]{Long.toString(mWorkoutID)});
            } catch (Exception e) {
                Log.e(TAG, "Failed to update summaries DB on searching finished", e);
            }
        }, mDbExecutor);
    }

    private void onUserSelectedSportTypeChanged() {
        long sportTypeId = getSportTypeId();
        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.SPORT_ID, sportTypeId);
        values.put(WorkoutSummaries.B_SPORT, SportTypeDatabaseManager.getInstance(this).getBSportType(sportTypeId).name());

        mTableInitializationFuture.thenAcceptAsync(v -> {
            try {
                WorkoutSummariesDatabaseManager databaseManager = WorkoutSummariesDatabaseManager.getInstance(TrackerService.this);
                SQLiteDatabase summariesDb = databaseManager.getDatabase();
                summariesDb.update(WorkoutSummaries.TABLE,
                        values,
                        WorkoutSummaries.C_ID + "=?",
                        new String[]{Long.toString(mWorkoutID)});
            } catch (Exception e) {
                Log.e(TAG, "Failed to update sport type in summaries DB", e);
            }
        }, mDbExecutor);
    }



    // TODO: the database entries should be correct even when this method is not called due to a crash
    // some stuff could be written earlier, others from the calling part (and then also executed when a crash is detected...), ...
    /**
     * Finalizes the active recording session and persists terminal summaries.
     *
     * <p>Functional Description: Closes out active laps, saves accumulated sensor types and
     * hardware metadata, marks the workout as finished in the summaries database, and dispatches
     * completion broadcasts.
     *
     * <p>Implementation Logic:
     * 1. Creates a final lap and persists active sensor devices and GC data.
     * 2. Sets {@link WorkoutSummaries#FINISHED} flag in {@link WorkoutSummaries#TABLE}.
     * 3. Finalizes live session auto-naming, triggers file export via {@link ExportManager}.
     * 4. Dispatches a system-level {@link #TRACKING_FINISHED_INTENT} with {@link #WORKOUT_ID} for app-level listeners.
     * 5. Dispatches {@link #WORKOUT_UPDATED_INTENT} and {@link #TRACKING_FINISHED_INTENT} containing {@link #WORKOUT_ID}
     *    via {@link LocalBroadcastManager} to notify internal data layers (e.g. {@code WorkoutRepository},
     *    {@code PeriodsRepository}, and {@code WorkoutClusterEngine}) for reactive period and cluster aggregation.
     */
    public void endWorkout() {
        if (DEBUG) {
            Log.d(TAG, "endWorkout");
        }

        try {
            createNewLap();
        } catch (Throwable t) {
            Log.e(TAG, "Error in createNewLap during endWorkout", t);
        }

        final BANALServiceComm banalService = mBanalService;

        // store the ANT and BTLE Devices that were active during the workout
        if (DEBUG) Log.d(TAG, "storing active device list");
        try {
            updateActiveSessionDevices(banalService);
            ActiveDevicesDbHelper activeDevicesDbHelper = new ActiveDevicesDbHelper(this);
            for (Long deviceDbId : mActiveSessionDeviceIds) {
                if (deviceDbId != null && deviceDbId >= 0) {
                    activeDevicesDbHelper.addActiveDevice(mWorkoutID, deviceDbId);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error saving active devices during endWorkout", t);
        }

        WorkoutSummariesDatabaseManager summariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(this);
        SQLiteDatabase summariesDb = summariesDatabaseManager.getDatabase();
        summariesDb.beginTransaction();
        try {
            // save the accumulated SensorTypes
            if (banalService != null) {
                summariesDatabaseManager.saveAccumulatedSensorTypes(mWorkoutID, banalService.getAccumulatedSensorTypeSet());
            }

            // update the summaries
            ContentValues summaryValues = new ContentValues();
            summaryValues.put(WorkoutSummaries.FINISHED, 1);

            // TODO: store at very beginning, end of ANT and BTLE searching, GPS found
            if (banalService != null) {
                summaryValues.put(WorkoutSummaries.GC_DATA, banalService.getAccumulatedGCDataString());
            }

            long sportTypeId = getSportTypeId();
            summaryValues.put(WorkoutSummaries.SPORT_ID, sportTypeId);
            summaryValues.put(WorkoutSummaries.B_SPORT, SportTypeDatabaseManager.getInstance(this).getBSportType(sportTypeId).name());

            summariesDb.update(WorkoutSummaries.TABLE,
                    summaryValues,
                    WorkoutSummaries.C_ID + "=" + mWorkoutID,
                    null);

            summariesDb.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error updating summaries table during endWorkout transaction", e);
        } finally {
            summariesDb.endTransaction();
        }

        try {
            // Finalize Live Session (Auto Name, Commute, Trainer)
            if (mLiveSession != null) {
                finalizeLiveSession();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error finalizing live session during endWorkout", t);
        }

        try {
            ExportManager exportManager = new ExportManager(this);
            exportManager.workoutFinished(mBaseFileName);
        } catch (Throwable t) {
            Log.e(TAG, "Error finishing workout export during endWorkout", t);
        } finally {
            // 1. Broadcast TRACKING_FINISHED_INTENT with WORKOUT_ID at system level
            Intent finishedIntent = new Intent(TRACKING_FINISHED_INTENT)
                    .setPackage(getPackageName())
                    .putExtra(WORKOUT_ID, mWorkoutID);
            sendBroadcast(finishedIntent);

            // 2. Broadcast TRACKING_FINISHED_INTENT via LocalBroadcastManager
            // for internal components (WorkoutRepository, etc.) to trigger reactive period & cluster updates (REQ-TRK-010, ATT-505)
            Intent localFinishedIntent = new Intent(TRACKING_FINISHED_INTENT)
                    .putExtra(WORKOUT_ID, mWorkoutID);
            LocalBroadcastManager.getInstance(this).sendBroadcast(localFinishedIntent);
        }
    }

    /**
     * Continuously records active session remote devices into ActiveDevicesDbHelper (REQ-CON-007, ATT-3312).
     */
    private void updateActiveSessionDevices() {
        updateActiveSessionDevices(mBanalService);
    }

    private void updateActiveSessionDevices(@Nullable final BANALServiceComm banalService) {
        if (banalService != null && mWorkoutID > 0) {
            try {
                for (long deviceDbId : banalService.getDatabaseIdsOfActiveRemoteDevices()) {
                    if (deviceDbId >= 0 && mActiveSessionDeviceIds.add(deviceDbId)) {
                        if (DEBUG) Log.d(TAG, "New active session device detected: " + deviceDbId);
                        new ActiveDevicesDbHelper(this).addActiveDevice(mWorkoutID, deviceDbId);
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error updating active session devices", t);
            }
        }
    }

    @VisibleForTesting
    void sampleAndWriteToDb() {
        sampleAndWriteToDb(mBanalService);
    }

    @VisibleForTesting
    void sampleAndWriteToDb(@Nullable final BANALServiceComm banalService) {
        if (DEBUG) Log.d(TAG, "sampleAndWriteToDb()");

        if (banalService == null) return;

        updateActiveSessionDevices(banalService);

        // 1. Always capture Total Time (SCRUM-97: needs to update during pause)
        SensorData<Integer> totalTimeData = banalService.getBestSensorData(SensorType.TIME_TOTAL);
        if (totalTimeData != null && totalTimeData.getValue() != null) {
            mTimeTotal_s = totalTimeData.getValue();
        }

        final AutoPauseTransition autoPauseTransition = evaluateAutoPauseTick(banalService);

        if (TrainingApplication.isPaused()) {
            if (autoPauseTransition != null && autoPauseTransition.getTriggerResume()) {
                sendBroadcast(new Intent(TrainingApplication.REQUEST_RESUME_FROM_PAUSED)
                        .setPackage(getPackageName()));
            }

            final long workoutId = mWorkoutID;
            final int timeTotal = mTimeTotal_s;
            
            mTableInitializationFuture.thenAcceptAsync(v -> {
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
            }, mDbExecutor);
            return;
        }

        final ContentValues samplingValues = new ContentValues();
        final ContentValues summaryValues = new ContentValues();
        final List<ExtremaUpdate> extremaUpdates = new java.util.ArrayList<>();
        final boolean updateAvgInDb = (mExtremaDbUpdateCounter >= EXTREMA_DB_UPDATE_INTERVAL);

        LatLng currentPosTemp = null;
        SensorData<Number> latData = banalService.getBestSensorData(SensorType.LATITUDE);
        SensorData<Number> lonData = banalService.getBestSensorData(SensorType.LONGITUDE);
        if (latData != null && lonData != null && latData.getValue() != null && lonData.getValue() != null) {
            if (SENSORS_TO_TRACK.contains(SensorType.LATITUDE) && SENSORS_TO_TRACK.contains(SensorType.LONGITUDE)) {
                currentPosTemp = new LatLng(latData.getValue().doubleValue(), lonData.getValue().doubleValue());
            }
        }
        final LatLng currentPos = currentPosTemp;

        // ATT-1447 / REQ-DAT-015: Record workout start location atomically on first valid GPS fix
        if (mStartType == StartType.START_NORMAL && !mWorkoutStartLocationRecorded && currentPos != null) {
            mWorkoutStartLocationRecorded = true;
            final LatLng startPos = currentPos;
            SensorData<Number> altData = banalService.getBestSensorData(SensorType.ALTITUDE);
            final Double startAlt = (altData != null && altData.getValue() != null) ? altData.getValue().doubleValue() : null;
            mDbExecutor.execute(() -> {
                KnownLocationsDatabaseManager.getInstance(TrackerService.this).recordWorkoutStart(startPos, startAlt);
            });
        }

        Map<String, SensorValueType> sensorName2Type = new HashMap<>();

        // 1. Update LiveSession running statistics ONLY from authoritative best sensor telemetry (REQ-MAP-019, ATT-499)
        if (mLiveSession != null) {
            for (SensorType sensorType : SENSORS_TO_TRACK) {
                SensorData<Number> bestData = banalService.getBestSensorData(sensorType);
                if (bestData == null || bestData.getValue() == null) continue;

                double sampleVal = bestData.getValue().doubleValue();
                // REQ-MAP-020 (ATT-528): Authoritative dynamic line distance anchored to session start
                if (sensorType == SensorType.LINE_DISTANCE_m && currentPos != null && mLiveSession.getStartLatLng() != null) {
                    sampleVal = WorkoutClusterEngine.Companion.distanceBetween(mLiveSession.getStartLatLng(), currentPos);
                }

                int changed = mLiveSession.addSample(sensorType, sampleVal, currentPos);

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
        }

        // 2. Capture multi-sensor telemetry for raw samples database and session accumulators
        for (SensorData<Number> sensorData : banalService.getAllSensorData()) {
            if (sensorData == null || sensorData.getValue() == null) continue;

            SensorType sensorType = sensorData.getSensorType();

            String sensorName = sensorType.name();
            String deviceName = sensorData.getDeviceName();
            if (deviceName != null) {
                if (deviceName.equals("gps") || deviceName.equals("network") || deviceName.equals("google_fused")) {
                    sensorName += "_" + deviceName;
                } else {
                    sensorName = quoteSqlColumnIdentifier(sensorName + " (" + deviceName + ")");
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
            SensorData<Number> altData = banalService.getBestSensorData(SensorType.ALTITUDE);
            SensorData<Number> distData = banalService.getBestSensorData(SensorType.DISTANCE_m);
            Double alt = (altData != null && altData.getValue() != null) ? altData.getValue().doubleValue() : null;
            Double dist = (distData != null && distData.getValue() != null) ? distData.getValue().doubleValue() : null;
            streamIncrement = mLiveSession.recordStreamPoint(currentPos, alt, dist);
        } else {
            streamIncrement = null;
        }

        final Map<String, SensorValueType> finalSensorName2Type = sensorName2Type;

        // 2. Offload all DB work to the dedicated executor via non-blocking reactive barrier
        mTableInitializationFuture.thenAcceptAsync(v -> {
            String resolvedTableName = mSamplesTableName;
            if (resolvedTableName == null || resolvedTableName.trim().isEmpty() || "\"null\"".equalsIgnoreCase(resolvedTableName.trim())) {
                resolvedTableName = resolveSamplesTableNameSafely(mBaseFileName);
                if (resolvedTableName != null) {
                    mSamplesTableName = resolvedTableName;
                }
            }
            if (resolvedTableName == null) {
                logSampleInsertFailure("Cannot write workout sample: mSamplesTableName is invalid (" + mSamplesTableName + ") for workoutId=" + mWorkoutID + ", baseFileName=" + mBaseFileName, null);
                return;
            }
            final String tableName = resolvedTableName;
            final long workoutId = mWorkoutID;

            WorkoutSamplesDatabaseManager samplesManager = WorkoutSamplesDatabaseManager.getInstance(TrackerService.this);
            WorkoutSummariesDatabaseManager summariesManager = WorkoutSummariesDatabaseManager.getInstance(TrackerService.this);
            SQLiteDatabase samplesDb = samplesManager.getDatabase();
            SQLiteDatabase summariesDb = summariesManager.getDatabase();

            samplesDb.beginTransaction();
            summariesDb.beginTransaction();
            boolean sampleInserted = false;
            try {
                // Write Samples
                try {
                    samplesDb.insertOrThrow(tableName, null, samplingValues);
                    sampleInserted = true;
                } catch (SQLException e) {
                    sampleInserted = handleMissingColumns(samplesDb, tableName, samplingValues, finalSensorName2Type, e);
                }

                if (!sampleInserted) {
                    int failureCount = mSampleInsertFailureCount.get();
                    if (failureCount == 0 || shouldLogSampleInsertFailure(failureCount)) {
                        Log.w(TAG, "Aborting sample and summary transaction because sample insert failed for table " + tableName + " (sampleInsertFailureCount=" + failureCount + ")");
                    }
                    return;
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
                logSampleInsertFailure("Error during async DB write for table " + tableName, e);
                sampleInserted = false;
            } finally {
                samplesDb.endTransaction();
                summariesDb.endTransaction();
            }

            if (sampleInserted) {
                // Notify UI
                try {
                    LocalBroadcastManager.getInstance(TrackerService.this).sendBroadcast(new Intent(WORKOUT_UPDATED_INTENT).putExtra("WORKOUT_ID", workoutId));
                } catch (Exception e) {
                    if (getBaseContext() != null) {
                        Log.w(TAG, "Failed to broadcast WORKOUT_UPDATED_INTENT", e);
                    }
                }
            }
        }, mDbExecutor).exceptionally(ex -> {
            Log.e(TAG, "Sample write skipped due to initialization failure: " + ex.getMessage());
            return null;
        });

        // REQ-TRK-018: Strict FIFO flush ordering: final stationary sample is inserted before pause broadcast
        if (autoPauseTransition != null && autoPauseTransition.getTriggerPause()) {
            mDbExecutor.execute(() -> {
                sendBroadcast(new Intent(TrainingApplication.REQUEST_PAUSE_TRACKING)
                        .putExtra(TrainingApplication.EXTRA_IS_AUTO_PAUSE, true)
                        .setPackage(getPackageName()));
            });
        }
    }

    private AutoPauseTransition evaluateAutoPauseTick(@Nullable final BANALServiceComm banalService) {
        if (banalService == null) return null;

        SensorData<Number> speedData = banalService.getBestSensorData(SensorType.SPEED_mps);
        Double speedMps = (speedData != null && speedData.getValue() != null) ? speedData.getValue().doubleValue() : null;
        long packetTimestamp = (speedData != null) ? speedData.getTimestamp() : 0L;
        long nowMs = System.currentTimeMillis();

        BSportType sportType = banalService.getBSportType();
        if (sportType == null) {
            sportType = BSportType.UNKNOWN;
        }

        AutoPauseConfig config = getAutoPauseConfig();
        boolean manualPauseLocked = TrainingApplication.isManualPauseLocked();
        if (mAutoPauseManager.isAutoPaused() && !TrainingApplication.isAutoPaused() && !manualPauseLocked) {
            mAutoPauseManager.onManualResume();
        }
        boolean hasSpeedSource = (speedData != null);

        return mAutoPauseManager.evaluateTick(
                speedMps,
                packetTimestamp,
                nowMs,
                sportType,
                config,
                manualPauseLocked,
                hasSpeedSource
        );
    }

    /**
     * Quotes a raw SQLite column identifier using SQL-standard double quotes ({@code "..."}) and
     * escapes any embedded double quote character by doubling it ({@code ""}).
     *
     * <p>Using SQL-standard double quotes preserves single quotes / apostrophes (e.g.,
     * {@code Rainer's HRM}), parentheses, spaces, and embedded double quotes inside both
     * {@link SQLiteDatabase#insertOrThrow} (via {@link ContentValues} keys) and
     * {@code ALTER TABLE ... ADD COLUMN} DDL statements ({@code REQ-TRK-029.1}, {@code ATT-3985}).
     *
     * @param rawColumnName the unquoted column name (e.g. {@code HR (Rainer's HRM)})
     * @return the safely double-quoted SQLite identifier (e.g. {@code "HR (Rainer's HRM)"})
     */
    @VisibleForTesting
    @NonNull
    public static String quoteSqlColumnIdentifier(@NonNull String rawColumnName) {
        return "\"" + rawColumnName.replace("\"", "\"\"") + "\"";
    }

    /**
     * Unquotes a SQLite column identifier key from {@link ContentValues} to recover the exact
     * unquoted column name stored in {@code sqlite_master} and returned by
     * {@link Cursor#getColumnNames()} ({@code REQ-TRK-029.1}, {@code ATT-3985}).
     *
     * <p>Unlike the legacy {@code key.replace("'", "")} implementation, this method strips only
     * outer SQL identifier delimiters ({@code "..."} or legacy {@code '...'}) and unescapes doubled
     * delimiter characters without removing internal apostrophes or quotes.
     *
     * @param sqlColumnIdentifier the potentially quoted SQLite identifier
     * @return the unquoted column name as stored in SQLite schema metadata
     */
    @VisibleForTesting
    @NonNull
    public static String unquoteSqlColumnIdentifier(@Nullable String sqlColumnIdentifier) {
        if (sqlColumnIdentifier == null) {
            return "";
        }
        if (sqlColumnIdentifier.length() >= 2
                && sqlColumnIdentifier.startsWith("\"")
                && sqlColumnIdentifier.endsWith("\"")) {
            return sqlColumnIdentifier.substring(1, sqlColumnIdentifier.length() - 1)
                    .replace("\"\"", "\"");
        }
        if (sqlColumnIdentifier.length() >= 2
                && sqlColumnIdentifier.startsWith("'")
                && sqlColumnIdentifier.endsWith("'")) {
            return sqlColumnIdentifier.substring(1, sqlColumnIdentifier.length() - 1)
                    .replace("''", "'");
        }
        return sqlColumnIdentifier;
    }

    /**
     * Checks whether a {@link Cursor} already contains the specified unquoted column name.
     *
     * <p>First checks {@link Cursor#getColumnIndex(String)}; if that returns {@code < 0} (which can
     * happen in Android's {@code AbstractWindowedCursor} when a column name contains a period
     * {@code .} due to table-prefix stripping), falls back to inspecting
     * {@link Cursor#getColumnNames()} directly ({@code REQ-TRK-029.2}).
     */
    @VisibleForTesting
    static boolean hasColumnInCursor(@NonNull Cursor cursor, @NonNull String rawColumnName) {
        if (cursor.getColumnIndex(rawColumnName) >= 0) {
            return true;
        }
        String[] columnNames = cursor.getColumnNames();
        if (columnNames != null) {
            for (String columnName : columnNames) {
                if (rawColumnName.equals(columnName) || rawColumnName.equalsIgnoreCase(columnName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determines whether a sample insertion failure with the given 1-based counter should be
     * emitted to logcat under the rate-limiting policy ({@code REQ-TRK-029.3}): log the initial
     * burst ({@code 1..SAMPLE_INSERT_FAILURE_INITIAL_BURST}) and every
     * {@code SAMPLE_INSERT_FAILURE_LOG_INTERVAL}-th failure thereafter.
     */
    @VisibleForTesting
    static boolean shouldLogSampleInsertFailure(int failureCount) {
        return failureCount > 0
                && (failureCount <= SAMPLE_INSERT_FAILURE_INITIAL_BURST
                || failureCount % SAMPLE_INSERT_FAILURE_LOG_INTERVAL == 0);
    }

    private void logSampleInsertFailure(@NonNull String message, @Nullable Throwable cause) {
        int count = mSampleInsertFailureCount.incrementAndGet();
        if (shouldLogSampleInsertFailure(count)) {
            String formattedMessage = message + " (sampleInsertFailureCount=" + count + ")";
            if (cause != null) {
                Log.e(TAG, formattedMessage, cause);
            } else {
                Log.e(TAG, formattedMessage);
            }
        }
    }

    /**
     * Recovers from missing columns (when new sensors are discovered mid-workout) or a missing
     * sample table for a valid {@code mBaseFileName}, and logs/rejects unrecoverable SQLite errors
     * ({@code REQ-TRK-026.4}, {@code REQ-TRK-029}, {@code ATT-3980}, {@code ATT-3985}).
     *
     * @return {@code true} if the sample row was successfully inserted after recovery, {@code false} otherwise
     */
    @VisibleForTesting
    boolean handleMissingColumns(SQLiteDatabase samplesDb, String tableName, ContentValues values, Map<String, SensorValueType> name2Type, @Nullable SQLException insertException) {
        String errMsg = insertException != null ? insertException.getMessage() : null;
        if (errMsg != null && errMsg.contains("no such table")) {
            String validTable = resolveSamplesTableNameSafely(mBaseFileName);
            if (validTable != null) {
                Log.w(TAG, "Sample table " + tableName + " missing for valid baseFileName " + mBaseFileName + "; recreating table", insertException);
                try {
                    WorkoutSamplesDatabaseManager.getInstance(this)
                            .createNewTable(mBaseFileName, Arrays.asList(SensorType.values()));
                    tableName = validTable;
                    mSamplesTableName = validTable;
                } catch (Exception createEx) {
                    logSampleInsertFailure("Failed to self-heal missing sample table " + validTable, createEx);
                    return false;
                }
            } else {
                logSampleInsertFailure("Unrecoverable SQLite error: sample table " + tableName + " does not exist and baseFileName (" + mBaseFileName + ") is invalid", insertException);
                return false;
            }
        } else if (errMsg != null && !errMsg.contains("no such column") && !errMsg.contains("has no column named")) {
            logSampleInsertFailure("Unrecoverable SQLite error inserting sample into table " + tableName, insertException);
            return false;
        }

        // Implementation of the ALTER TABLE logic removed from main flow for readability.
        // It ensures that new sensors added during a workout get their own columns.
        try (Cursor cursor = samplesDb.query(tableName, null, null, null, null, null, null, "1")) {
            if (cursor == null) {
                logSampleInsertFailure("Failed to inspect columns of sample table " + tableName + ": query returned null cursor", null);
                return false;
            }
            for (String key : values.keySet()) {
                String queryKey = unquoteSqlColumnIdentifier(key);
                if (!hasColumnInCursor(cursor, queryKey)) {
                    SensorValueType valueType = name2Type != null ? name2Type.get(key) : null;
                    String type = valueType == null ? "text" : switch (valueType) {
                        case INTEGER -> "int";
                        case DOUBLE -> "double";
                        default -> "text";
                    };
                    String quotedColumn = quoteSqlColumnIdentifier(queryKey);
                    samplesDb.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + quotedColumn + " " + type + " null;");
                }
            }
            samplesDb.insertOrThrow(tableName, null, values);
            return true;
        } catch (Exception e) {
            logSampleInsertFailure("Failed to fix schema or insert after schema fix for table " + tableName, e);
            return false;
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
            if (sensor == SensorType.W_PRIME_BAL && stats.count > 0) {
                summariesManager.updateExtremaValue(mWorkoutID, sensor, ExtremaType.END, stats.last, endPos);
                repository.updateExtremaValue(mWorkoutID, sensor, ExtremaType.END, stats.last, endPos);
            }
        }

        // 2b. Calculate and persist Efficiency Factor (EF) & Aerobic Decoupling (Pw:Hr / Pa:Hr) from recorded samples (REQ-UI-400.2)
        try {
            if (mBaseFileName != null && !mBaseFileName.isEmpty()) {
                WorkoutSamplesDatabaseManager samplesManager = WorkoutSamplesDatabaseManager.getInstance(this);
                if (samplesManager.existsTable(mBaseFileName)) {
                    String tableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName);
                    java.util.List<Double> powerSamples = new java.util.ArrayList<>();
                    java.util.List<Double> speedSamples = new java.util.ArrayList<>();
                    java.util.List<Double> hrSamples = new java.util.ArrayList<>();
                    try (Cursor sampleCursor = samplesManager.getDatabase().query(tableName, null, null, null, null, null, null)) {
                        if (sampleCursor != null) {
                            int powerIdx = sampleCursor.getColumnIndex(SensorType.POWER.name());
                            int speedIdx = sampleCursor.getColumnIndex(SensorType.SPEED_mps.name());
                            int hrIdx = sampleCursor.getColumnIndex(SensorType.HR.name());
                            while (sampleCursor.moveToNext()) {
                                Double p = (powerIdx != -1 && !sampleCursor.isNull(powerIdx)) ? sampleCursor.getDouble(powerIdx) : null;
                                Double s = (speedIdx != -1 && !sampleCursor.isNull(speedIdx)) ? sampleCursor.getDouble(speedIdx) : null;
                                Double hr = (hrIdx != -1 && !sampleCursor.isNull(hrIdx)) ? sampleCursor.getDouble(hrIdx) : null;
                                powerSamples.add(p);
                                speedSamples.add(s);
                                hrSamples.add(hr);
                            }
                        }
                    }
                    BSportType liveSport = (mBanalService != null && mBanalService.getBSportType() != null)
                            ? mBanalService.getBSportType()
                            : BSportType.UNKNOWN;
                    com.atrainingtracker.trainingtracker.physiology.AerobicEfficiencyResult aerobicResult =
                            com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(
                                    powerSamples, speedSamples, hrSamples, liveSport
                            );
                    SensorType targetSensor = aerobicResult.isPowerBased() ? SensorType.POWER : SensorType.SPEED_mps;
                    if (aerobicResult.getEfficiencyFactor() != null) {
                        double ef = aerobicResult.getEfficiencyFactor();
                        summariesManager.updateExtremaValue(mWorkoutID, targetSensor, ExtremaType.EFFICIENCY_FACTOR, ef, null);
                        repository.updateExtremaValue(mWorkoutID, targetSensor, ExtremaType.EFFICIENCY_FACTOR, ef, null);
                    }
                    if (aerobicResult.getDecouplingPct() != null) {
                        double dec = aerobicResult.getDecouplingPct();
                        summariesManager.updateExtremaValue(mWorkoutID, targetSensor, ExtremaType.AEROBIC_DECOUPLING, dec, null);
                        repository.updateExtremaValue(mWorkoutID, targetSensor, ExtremaType.AEROBIC_DECOUPLING, dec, null);
                    }

                    // REQ-MIG-039.4 (ATT-3860 / ATT-3858): Calculate and persist session power training metrics (NP, IF, TSS, Work kJ, W/kg)
                    long sessionTimeMs = System.currentTimeMillis();
                    Integer effectiveFtp = SettingsDataStoreJavaHelper.resolveEffectiveFtpAt(this, sessionTimeMs);
                    float effectiveWeightKg = SettingsDataStoreJavaHelper.resolveEffectiveWeightKgAt(this, sessionTimeMs);
                    Double ftpWattsObj = (effectiveFtp != null && effectiveFtp > 0) ? effectiveFtp.doubleValue() : null;
                    Double weightKgObj = (effectiveWeightKg > 0.0f) ? (double) effectiveWeightKg : null;
                    com.atrainingtracker.trainingtracker.physiology.PowerTrainingMetricsResult powerMetrics =
                            com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.computePowerTrainingMetrics(
                                    powerSamples,
                                    mTimeActive_s,
                                    ftpWattsObj,
                                    weightKgObj
                            );
                    if (powerMetrics.getNormalizedPowerWatts() != null) {
                        double np = powerMetrics.getNormalizedPowerWatts();
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.NORMALIZED_POWER, ExtremaType.AVG, np, null);
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.NORMALIZED_POWER, ExtremaType.END, np, null);
                        repository.updateExtremaValue(mWorkoutID, SensorType.NORMALIZED_POWER, ExtremaType.AVG, np, null);
                    }
                    if (powerMetrics.getIntensityFactor() != null) {
                        double ifVal = powerMetrics.getIntensityFactor();
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.INTENSITY_FACTOR, ExtremaType.AVG, ifVal, null);
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.INTENSITY_FACTOR, ExtremaType.END, ifVal, null);
                        repository.updateExtremaValue(mWorkoutID, SensorType.INTENSITY_FACTOR, ExtremaType.AVG, ifVal, null);
                    }
                    if (powerMetrics.getTss() != null) {
                        double tss = powerMetrics.getTss();
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.TSS, ExtremaType.AVG, tss, null);
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.TSS, ExtremaType.MAX, tss, null);
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.TSS, ExtremaType.END, tss, null);
                        repository.updateExtremaValue(mWorkoutID, SensorType.TSS, ExtremaType.AVG, tss, null);
                    }
                    if (powerMetrics.getWorkKj() != null) {
                        double workKj = powerMetrics.getWorkKj();
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.WORK_KJ, ExtremaType.AVG, workKj, null);
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.WORK_KJ, ExtremaType.MAX, workKj, null);
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.WORK_KJ, ExtremaType.END, workKj, null);
                        repository.updateExtremaValue(mWorkoutID, SensorType.WORK_KJ, ExtremaType.AVG, workKj, null);
                    }
                    if (powerMetrics.getAvgPowerToWeight() != null) {
                        double avgWPerKg = powerMetrics.getAvgPowerToWeight();
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.POWER_TO_WEIGHT, ExtremaType.AVG, avgWPerKg, null);
                        repository.updateExtremaValue(mWorkoutID, SensorType.POWER_TO_WEIGHT, ExtremaType.AVG, avgWPerKg, null);
                    }
                    if (powerMetrics.getMaxPowerToWeight() != null) {
                        double maxWPerKg = powerMetrics.getMaxPowerToWeight();
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.POWER_TO_WEIGHT, ExtremaType.MAX, maxWPerKg, null);
                        repository.updateExtremaValue(mWorkoutID, SensorType.POWER_TO_WEIGHT, ExtremaType.MAX, maxWPerKg, null);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "finalizeLiveSession: Could not compute Aerobic Efficiency / Decoupling or Power Training extrema", e);
        }

        // REQ-MAP-020 (ATT-528): Authoritative deterministic calculation of max line distance and apex from recorded track
        if (startPos != null && mLiveSession.getSampledLatLngs() != null && !mLiveSession.getSampledLatLngs().isEmpty()) {
            double calculatedMaxDisp = 0.0;
            LatLng calculatedApex = startPos;
            for (LatLng pt : mLiveSession.getSampledLatLngs()) {
                if (pt != null) {
                    float dist = WorkoutClusterEngine.Companion.distanceBetween(startPos, pt);
                    if (dist > calculatedMaxDisp) {
                        calculatedMaxDisp = dist;
                        calculatedApex = pt;
                    }
                }
            }
            summariesManager.updateExtremaValue(mWorkoutID, SensorType.LINE_DISTANCE_m, ExtremaType.MAX, calculatedMaxDisp, calculatedApex);
            repository.updateExtremaValue(mWorkoutID, SensorType.LINE_DISTANCE_m, ExtremaType.MAX, calculatedMaxDisp, calculatedApex);
        }

        // 3. Guess Commute and Trainer
        guessCommuteAndTrainer();

        // 4. Identity Determination (SCRUM-200)
        // Perform arbitration between hardware and route patterns
        EquipmentAndSportTypeDiscoveryManager discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(this);
        final BANALServiceComm banalService = mBanalService;
        final Set<Long> activeRemoteDeviceIds = (banalService != null)
                ? new HashSet<>(banalService.getDatabaseIdsOfActiveRemoteDevices())
                : new HashSet<>(mActiveSessionDeviceIds);
        final BSportType banalSportType = (banalService != null && banalService.getBSportType() != null)
                ? banalService.getBSportType()
                : BSportType.UNKNOWN;

        // Initial arbitration using currently active devices in memory (more reliable than waiting for DB sync)
        EquipmentAndSportTypeDiscoveryManager.InferredIdentity identity = discoveryManager.resolveIdentity(activeRemoteDeviceIds, banalSportType, getAverageSpeed());

        LatLng startPosRaw = mLiveSession.getStartLatLng();
        LatLng endPosRaw = mLiveSession.getLastLatLng();
        LatLng maxDispPos = summariesManager.getExtremaPosition(mWorkoutID, SensorType.LINE_DISTANCE_m, ExtremaType.MAX);

        if (startPosRaw != null && endPosRaw != null && maxDispPos != null) {
            WorkoutClusterEngine engine = WorkoutClusterEngine.Companion.getInstance(this);

            // Determine candidate sport types for clustering (ATT-773, REQ-SET-064)
            Set<BSportType> candidateSports;
            BSportType userSelectedSport = (banalService != null) ? banalService.getUserSelectedBSportType() : null;
            if (userSelectedSport != null && userSelectedSport != BSportType.UNKNOWN) {
                // Tier 1: User Pre-Selection Sovereignty (Option A)
                candidateSports = Collections.singleton(userSelectedSport);
            } else if (identity.isHighConfidence()) {
                // Tier 2: Dedicated Hardware Sensor Sovereignty (REQ-SET-030)
                candidateSports = Collections.singleton(identity.getBSportType());
            } else {
                // Tier 3: Speed-Based Multi-Sport Candidate Set
                candidateSports = discoveryManager.getCandidateBSportTypes(banalSportType, getAverageSpeed());
            }

            LatLng minAltPos = summariesManager.getExtremaPosition(mWorkoutID, SensorType.ALTITUDE, ExtremaType.MIN);
            LatLng maxAltPos = summariesManager.getExtremaPosition(mWorkoutID, SensorType.ALTITUDE, ExtremaType.MAX);
            WorkoutCluster suggestion = engine.suggestCluster(startPosRaw, endPosRaw, maxDispPos, mDistanceTotal_m, null, candidateSports, minAltPos, maxAltPos);
            if (suggestion != null) {
                // If hardware confidence is high, we only take the name from the cluster
                if (identity.isHighConfidence()) {
                    ContentValues nameValues = new ContentValues();
                    int displayCount = suggestion.getHitCount() + 1;
                    String autoName = WorkoutClusterEngine.formatClusterWorkoutName(this, suggestion.getName(), displayCount, suggestion.getHasCounter());
                    nameValues.put(WorkoutSummaries.WORKOUT_NAME, autoName);
                    nameValues.put(WorkoutSummaries.CLUSTER_ID, suggestion.getId());
                    summariesManager.getDatabase().update(WorkoutSummaries.TABLE, nameValues, WorkoutSummaries.C_ID + "=?", new String[]{String.valueOf(mWorkoutID)});
                    
                    // Apply hardware-based identity (Sport, Gear, Strava)
                    summariesManager.applyInferredIdentity(mWorkoutID, identity);
                } else {
                    // Low hardware confidence -> Workout Cluster wins everything and resolves ambiguous sport type
                    engine.assignClusterToWorkout(this, mWorkoutID, suggestion.getId());
                }
            } else {
                // No cluster match -> use hardware identity
                summariesManager.applyInferredIdentity(mWorkoutID, identity);

                // ATT-1398: Intelligent Workout Auto-Naming based on recognized start/destination Lieblingsorte
                KnownLocationsDatabaseManager knownLocationsManager = KnownLocationsDatabaseManager.getInstance(this);
                KnownLocationsDatabaseManager.MyLocation startLoc = knownLocationsManager.getMyLocation(startPosRaw);
                KnownLocationsDatabaseManager.MyLocation endLoc = knownLocationsManager.getMyLocation(endPosRaw);
                float endpointDist = engine.distanceBetween(startPosRaw, endPosRaw);
                BSportType resolvedSport = (identity != null && identity.getBSportType() != null && identity.getBSportType() != BSportType.UNKNOWN)
                    ? identity.getBSportType()
                    : banalSportType;
                String autoName = WorkoutAutoNamingHelper.generateWorkoutName(
                    this,
                    resolvedSport,
                    startLoc,
                    endLoc,
                    endpointDist,
                    mDistanceTotal_m
                );
                if (autoName != null && !autoName.trim().isEmpty()) {
                    ContentValues nameValues = new ContentValues();
                    nameValues.put(WorkoutSummaries.WORKOUT_NAME, autoName);
                    summariesManager.getDatabase().update(WorkoutSummaries.TABLE, nameValues, WorkoutSummaries.C_ID + "=?", new String[]{String.valueOf(mWorkoutID)});
                }
            }
        } else {
            // No spatial data -> use hardware identity
            summariesManager.applyInferredIdentity(mWorkoutID, identity);
        }

        // 5. Finalize Map and Streams (one last check)
        List<LatLng> pointsToSimplify = mLiveSession.getRawLatLngs();
        if (pointsToSimplify.isEmpty()) {
            pointsToSimplify = mLiveSession.getSampledLatLngs();
        }
        List<LatLng> simplifiedPoints = (pointsToSimplify != null && !pointsToSimplify.isEmpty())
                ? PolyUtil.simplify(pointsToSimplify, 10.0)
                : Collections.emptyList();
        String polyline = PolyUtil.encode(simplifiedPoints);
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
