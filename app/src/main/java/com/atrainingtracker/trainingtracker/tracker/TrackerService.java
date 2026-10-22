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
    public static final String TRACKING_ABORTED_INTENT = "com.atrainingtracker.trainingtracker.tracker.TrackerService.TRACKING_ABORTED_INTENT";
    public static final String WORKOUT_ID               = "WORKOUT_ID";
    public static final String WORKOUT_UPDATED_INTENT   = "com.atrainingtracker.trainingtracker.WOKRKOUT_UPDATED_INTENT";

    // Same sensor types from the old thread. Used for Averages.
    @VisibleForTesting
    static final HashSet<SensorType> IMPORTANT_SENSOR_TYPES = new HashSet<>(SensorType.CORE_METRICS);
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
    
    public static final String ACTION_STOP = "com.atrainingtracker.trainingtracker.tracker.TrackerService.ACTION_STOP";
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
    private volatile CompletableFuture<Void> mFinalizationFuture = CompletableFuture.completedFuture(null);
    private volatile boolean mIsStopping = false;
    private volatile boolean mBanalBound = false;
    @Nullable private volatile BanalFinalizationSnapshot mFinalizationSnapshot = null;

    @VisibleForTesting
    @Nullable
    public BanalFinalizationSnapshot getFinalizationSnapshotForTesting() {
        return mFinalizationSnapshot;
    }
    // we assume that we start while the BANAL Service is searching
    protected volatile boolean mSearching = true;
    protected volatile boolean mCreateNewLapWhenConnectedToBanalService = false;
    protected volatile boolean mResumeFromPausedWhenConnectedToBanalService = false;
    protected volatile boolean mResumeTrackingWhenConnectedToBanalService = false;
    @Nullable
    volatile BANALServiceComm mBanalService;
    private final AtomicInteger mSamplingErrorCount = new AtomicInteger(0);
    private volatile long mCachedSportTypeId = -1;
    private volatile String mCachedSportTypeUiName = null;
    public static final int SAMPLE_INSERT_FAILURE_INITIAL_BURST = 3;
    public static final int SAMPLE_INSERT_FAILURE_LOG_INTERVAL = 60;
    private final AtomicInteger mSampleInsertFailureCount = new AtomicInteger(0);
    private android.os.PowerManager.WakeLock wakeLock;
    private TrainingApplication mTrainingApplication;
    private WorkoutRepository mWorkoutRepository;
    private volatile ScheduledFuture mTrackerHandle;

    /**
     * Returns the cumulative count of sample row insertion failures during the current service
     * lifecycle ({@code REQ-TRK-029.3}).
     */
    @VisibleForTesting
    public int getSampleInsertFailureCount() {
        return mSampleInsertFailureCount.get();
    }

    /**
     * Returns the {@link CompletableFuture} that completes once the terminal {@code onDestroy()}
     * or {@code ACTION_STOP} workout finalization task on {@code mDbExecutor} has finished
     * ({@code REQ-TRK-033.2}, {@code REQ-TRK-034.3}).
     */
    @VisibleForTesting
    @NonNull
    public CompletableFuture<Void> getFinalizationFuture() {
        return mFinalizationFuture;
    }

    /**
     * Returns whether the 1 Hz {@code mScheduler} thread pool has been shut down ({@code REQ-TRK-033.1}).
     */
    @VisibleForTesting
    public boolean isSchedulerShutdownForTesting() {
        return mScheduler.isShutdown();
    }

    /**
     * Returns whether the single-threaded {@code mDbExecutor} has been shut down ({@code REQ-TRK-033.2}).
     */
    @VisibleForTesting
    public boolean isDbExecutorShutdownForTesting() {
        return mDbExecutor.isShutdown();
    }

    /**
     * Returns whether the service is currently executing graceful stop finalization ({@code REQ-TRK-034.2}).
     */
    @VisibleForTesting
    public boolean isStoppingForTesting() {
        return mIsStopping;
    }

    /**
     * Returns whether an instance of {@link TrackerService} is currently active ({@code REQ-TRK-034.1}).
     */
    public static boolean isRunning() {
        return sInstance != null;
    }

    @Nullable
    public static TrackerService getInstance() {
        return sInstance;
    }

    @VisibleForTesting
    public static void setInstanceForTesting(@Nullable TrackerService instance) {
        sInstance = instance;
    }

    @VisibleForTesting
    public void setBanalBoundForTesting(boolean bound) {
        mBanalBound = bound;
    }

    /**
     * Immutable snapshot capturing all {@link BANALService} state and sensor telemetry required
     * for asynchronous workout database finalization on {@code mDbExecutor} ({@code REQ-TRK-035.1}).
     *
     * <p>Captured synchronously on the Android main thread at workout stop time, ensuring
     * deterministic persistence of the final lap, sensor accumulations, active device IDs,
     * and sport type classification even if {@code BANALService} is unbound or destroyed concurrently.
     */
    public static class BanalFinalizationSnapshot {
        public final int lapNr;
        public final int lapTime_s;
        public final double lapDistance_m;
        @NonNull public final Set<Long> activeRemoteDeviceIds;
        @NonNull public final Set<SensorType> accumulatedSensorTypes;
        @Nullable public final String accumulatedGCDataString;
        @NonNull public final BSportType bSportType;
        @Nullable public final BSportType userSelectedSportType;
        public final long sportTypeId;

        public BanalFinalizationSnapshot(
                int lapNr,
                int lapTime_s,
                double lapDistance_m,
                @Nullable Set<Long> activeRemoteDeviceIds,
                @Nullable Set<SensorType> accumulatedSensorTypes,
                @Nullable String accumulatedGCDataString,
                @Nullable BSportType bSportType,
                @Nullable BSportType userSelectedSportType,
                long sportTypeId) {
            this.lapNr = lapNr;
            this.lapTime_s = lapTime_s;
            this.lapDistance_m = lapDistance_m;
            this.activeRemoteDeviceIds = activeRemoteDeviceIds != null
                    ? Collections.unmodifiableSet(new HashSet<>(activeRemoteDeviceIds))
                    : Collections.emptySet();
            this.accumulatedSensorTypes = accumulatedSensorTypes != null
                    ? Collections.unmodifiableSet(new HashSet<>(accumulatedSensorTypes))
                    : Collections.emptySet();
            this.accumulatedGCDataString = accumulatedGCDataString;
            this.bSportType = bSportType != null ? bSportType : BSportType.UNKNOWN;
            this.userSelectedSportType = userSelectedSportType;
            this.sportTypeId = sportTypeId;
        }

        @NonNull
        public static BanalFinalizationSnapshot capture(
                @Nullable BANALServiceComm banalService,
                @NonNull Context context,
                double avgSpeed,
                boolean avgSpeedCalculable) {
            if (banalService == null) {
                return new BanalFinalizationSnapshot(
                        0, 0, 0.0,
                        Collections.emptySet(),
                        Collections.emptySet(),
                        null,
                        BSportType.UNKNOWN,
                        null,
                        BANALService.getDefaultSportTypeId());
            }

            int lapNr = 0;
            SensorData<?> lapNrData = banalService.getBestSensorData(SensorType.LAP_NR);
            if (lapNrData != null && lapNrData.getValue() instanceof Integer) {
                lapNr = (Integer) lapNrData.getValue();
            }

            int lapTime_s = 0;
            SensorData<?> lapTimeData = banalService.getBestSensorData(SensorType.TIME_LAP);
            if (lapTimeData != null && lapTimeData.getValue() instanceof Integer) {
                lapTime_s = (Integer) lapTimeData.getValue();
            }

            double lapDistance_m = 0.0;
            SensorData<?> lapDistanceData = banalService.getBestSensorData(SensorType.DISTANCE_m_LAP);
            if (lapDistanceData != null && lapDistanceData.getValue() instanceof Number) {
                lapDistance_m = ((Number) lapDistanceData.getValue()).doubleValue();
            }

            Set<Long> deviceIds = Collections.emptySet();
            try {
                List<Long> ids = banalService.getDatabaseIdsOfActiveRemoteDevices();
                if (ids != null) {
                    deviceIds = new HashSet<>(ids);
                }
            } catch (Throwable ignored) {
            }

            Set<SensorType> accumulatedSensors = Collections.emptySet();
            try {
                Set<SensorType> sensors = banalService.getAccumulatedSensorTypeSet();
                if (sensors != null) {
                    accumulatedSensors = new HashSet<>(sensors);
                }
            } catch (Throwable ignored) {
            }

            String gcData = null;
            try {
                gcData = banalService.getAccumulatedGCDataString();
            } catch (Throwable ignored) {
            }

            BSportType bSport = BSportType.UNKNOWN;
            try {
                BSportType sport = banalService.getBSportType();
                if (sport != null) {
                    bSport = sport;
                }
            } catch (Throwable ignored) {
            }

            BSportType userSelectedSport = null;
            try {
                userSelectedSport = banalService.getUserSelectedBSportType();
            } catch (Throwable ignored) {
            }

            long sportTypeId = BANALService.getDefaultSportTypeId();
            try {
                EquipmentAndSportTypeDiscoveryManager discoveryManager =
                        EquipmentAndSportTypeDiscoveryManager.getInstance(context);
                if (avgSpeedCalculable) {
                    sportTypeId = discoveryManager.resolveSportType(deviceIds, bSport, avgSpeed);
                } else {
                    sportTypeId = discoveryManager.resolveSportType(deviceIds, bSport);
                }
            } catch (Throwable ignored) {
            }

            return new BanalFinalizationSnapshot(
                    lapNr,
                    lapTime_s,
                    lapDistance_m,
                    deviceIds,
                    accumulatedSensors,
                    gcData,
                    bSport,
                    userSelectedSport,
                    sportTypeId);
        }
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
            service.mAutoPauseManager.onManualResume(TrainingApplication.isManualPauseLocked());
        }
    }

    private volatile AutoPauseConfig mCachedAutoPauseConfig = null;
    private volatile long mLastAutoPauseConfigFetchMs = 0L;
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
    private volatile boolean mTrackingInterrupted = false;
    private volatile long mWorkoutID;
    private final Set<Long> mActiveSessionDeviceIds = Collections.synchronizedSet(new HashSet<>());
    private volatile LiveWorkoutSession mLiveSession;
    private volatile boolean mIsRehydrating = false;
    private volatile CompletableFuture<Void> mTableInitializationFuture = CompletableFuture.completedFuture(null);
    private volatile StartType mStartType;
    private volatile boolean mWorkoutStartLocationRecorded = false;

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
    private volatile String mBaseFileName;

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
            LiveWorkoutSession session = mLiveSession;
            if (session != null) {
                session.applyAltitudeCorrection(altitudeCorrection);
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
    volatile int mTimeTotal_s = 0;
    private volatile int mTimeActive_s = 0;
    private volatile double mDistanceTotal_m = 0.0;
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
                if (mIsStopping) {
                    return;
                }
                if (DEBUG) {
                    Log.d(TAG, "sampling/tracking");
                }

                final BANALServiceComm banalService = mBanalService;
                if (banalService != null) {

                    sampleAndWriteToDb(banalService);

                    // update notification
                    if (DEBUG) Log.i(TAG, "updating notification");
                    if (mTrainingApplication != null) {
                        long sportTypeId = banalService.getSportTypeId();
                        String sportUiName;
                        if (sportTypeId == mCachedSportTypeId && mCachedSportTypeUiName != null) {
                            sportUiName = mCachedSportTypeUiName;
                        } else {
                            sportUiName = SportTypeDatabaseManager.getInstance(TrackerService.this).getUIName(sportTypeId);
                            mCachedSportTypeId = sportTypeId;
                            mCachedSportTypeUiName = sportUiName;
                        }
                        mTrainingApplication.updateTimeAndDistanceToNotification(
                                banalService.getBestSensorData(SensorType.TIME_ACTIVE),
                                banalService.getBestSensorData(SensorType.DISTANCE_m),
                                sportUiName);
                    }
                    if (DEBUG) Log.i(TAG, "updated notification");

                }
            } catch (Throwable t) {
                int errorCount = mSamplingErrorCount.incrementAndGet();
                if (shouldLogSampleInsertFailure(errorCount)) {
                    Log.e(TAG, "Error during 1 Hz sampling tick; continuing tracking loop (samplingErrorCount=" + errorCount + ")", t);
                }
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
        mBanalBound = true;

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

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            return handleActionStop();
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
                        ScheduledFuture<?> handle = mTrackerHandle;
                        if (handle != null) {
                            handle.cancel(true);
                            mTrackerHandle = null;
                        }
                        runOnMainThread(this::handleTrackingAborted);
                        showTrackingInterruptedNotification();
                        performStopSelf();
                    }
                });
                break;

            case RESUME_BY_USER:
                Log.d(TAG, "resuming by user request");
                mAutoPauseManager.onManualResume(TrainingApplication.isManualPauseLocked());
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

    protected void performStopForeground() {
        stopForeground(true);
    }

    protected synchronized void safeUnbindBanalService() {
        try {
            if (mBanalBound || mBanalService != null) {
                unbindService(mBanalConnection);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error unbinding BANALService", t);
        } finally {
            mBanalBound = false;
        }
    }

    /**
     * Handles graceful asynchronous workout finalization under foreground service priority
     * ({@code REQ-TRK-034.2}, {@code REQ-TRK-034.3}).
     *
     * <p>1. Shuts down {@code mScheduler} immediately to prevent any further 1 Hz ticks from being enqueued.
     * <p>2. Retains active foreground service priority and notification so Android's Low Memory Killer
     * does not terminate the process during lengthy database finalization on {@code mDbExecutor}.
     * <p>3. Enqueues {@code finalizationTask} onto the single-threaded FIFO {@code mDbExecutor}.
     * <p>4. In the completion callback of {@code finalizationTask}, dispatches main-thread unbinding
     * of {@code mBanalConnection}, {@code performStopForeground()}, and {@code performStopSelf()}.
     */
    protected int handleActionStop() {
        if (mIsStopping) {
            Log.w(TAG, "handleActionStop: ACTION_STOP received while already stopping; ignoring duplicate");
            return Service.START_NOT_STICKY;
        }
        if (mWorkoutID <= 0 && mBaseFileName == null) {
            Log.w(TAG, "handleActionStop: ACTION_STOP received while not tracking; stopping self");
            performStopSelf();
            return Service.START_NOT_STICKY;
        }
        mIsStopping = true;
        if (DEBUG) Log.d(TAG, "handleActionStop: initiating foreground-protected async finalization");

        ScheduledFuture<?> handle = mTrackerHandle;
        if (handle != null) {
            handle.cancel(true);
            mTrackerHandle = null;
        }
        mScheduler.shutdownNow();

        mAutoPauseManager.release();

        LifecycleUtils.safeUnregisterReceiver(this, mSearchingFinishedReceiver, TAG);
        LifecycleUtils.safeUnregisterReceiver(this, mAltitudeCorrectionReceiver, TAG);
        LifecycleUtils.safeUnregisterReceiver(this, mLapSummaryReceiver, TAG);
        LifecycleUtils.safeUnregisterReceiver(this, mUserSelectedSportTypeChangedReceiver, TAG);

        final BanalFinalizationSnapshot snapshot = BanalFinalizationSnapshot.capture(
                mBanalService, this, getAverageSpeed(), averageSpeedCalculateable());
        final boolean interrupted = mTrackingInterrupted;
        final CompletableFuture<Void> finalizationFuture = new CompletableFuture<>();
        mFinalizationFuture = finalizationFuture;

        Runnable finalizationTask = () -> {
            try {
                if (!interrupted) {
                    endWorkout(snapshot);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Unexpected error during async workout finalization in handleActionStop", t);
            } finally {
                mBanalService = null;
                releaseWakeLock();
                runOnMainThread(() -> {
                    try {
                        safeUnbindBanalService();
                        performStopForeground();
                        performStopSelf();
                    } finally {
                        finalizationFuture.complete(null);
                    }
                });
            }
        };

        try {
            mDbExecutor.execute(finalizationTask);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            Log.w(TAG, "mDbExecutor already shut down; running finalizationTask inline", e);
            finalizationTask.run();
        } finally {
            mDbExecutor.shutdown();
        }

        return Service.START_NOT_STICKY;
    }

    /**
     * Dispatches {@code action} to the Android main looper if available and the caller is on a
     * background thread; otherwise executes {@code action} inline ({@code REQ-TRK-033.3}).
     */
    protected void runOnMainThread(@NonNull Runnable action) {
        try {
            android.os.Looper mainLooper = android.os.Looper.getMainLooper();
            if (mainLooper != null && android.os.Looper.myLooper() != mainLooper) {
                if (new android.os.Handler(mainLooper).post(action)) {
                    return;
                }
            }
        } catch (Exception ignored) {
            // Fallback to inline execution if main thread handler dispatch fails
        }
        action.run();
    }

    /**
     * Resets {@link TrainingApplication} tracking state to {@code READY} and broadcasts
     * {@link #TRACKING_ABORTED_INTENT} when asynchronous workout initialization fails
     * ({@code REQ-TRK-033.3}).
     */
    protected void handleTrackingAborted() {
        TrainingApplication.resetTrackingStateOnAbort();
        if (mTrainingApplication != null) {
            mTrainingApplication.onTrackingAborted();
        }
        notifyTrackingAborted();
    }

    protected void notifyTrackingAborted() {
        try {
            Intent abortedIntent = new Intent(TRACKING_ABORTED_INTENT)
                    .setPackage(this.getPackageName());
            this.sendBroadcast(abortedIntent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to broadcast TRACKING_ABORTED_INTENT", e);
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
        } catch (Exception e) {
            Log.w(TAG, "Failed to broadcast TRACKING_STARTED_INTENT", e);
        }
    }

    protected void performSuperOnDestroy() {
        super.onDestroy();
    }

    /**
     * Tears down the tracking service without blocking the Android main thread ({@code REQ-TRK-033}).
     *
     * <p>1. Cancels {@code mTrackerHandle} and shuts down {@code mScheduler} ({@code REQ-TRK-033.1})
     * so the 1 Hz sampling pool thread is reclaimed and cannot enqueue further tasks onto
     * {@code mDbExecutor}.
     * <p>2. Releases main-thread resources ({@code mAutoPauseManager}, broadcast receivers,
     * {@code unbindService(mBanalConnection)}, {@code stopForeground}).
     * <p>3. Enqueues a terminal {@code finalizationTask} onto the single-threaded FIFO
     * {@code mDbExecutor} followed immediately by {@code mDbExecutor.shutdown()} ({@code REQ-TRK-033.2}),
     * ensuring all pending 1 Hz sample writes finish in FIFO order before {@link #endWorkout()}
     * executes off the main thread.
     */
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

        // Stop the periodic 1 Hz tracker task and shut down the scheduler pool (REQ-TRK-033.1)
        ScheduledFuture<?> handle = mTrackerHandle;
        if (handle != null) {
            handle.cancel(true);
            mTrackerHandle = null;
        }
        mScheduler.shutdownNow();

        // Release main-thread resources immediately while keeping mBanalService and wakeLock
        // valid until the terminal finalization task on mDbExecutor completes (REQ-TRK-033.2).
        mAutoPauseManager.release();

        LifecycleUtils.safeUnregisterReceiver(this, mSearchingFinishedReceiver, TAG);
        LifecycleUtils.safeUnregisterReceiver(this, mAltitudeCorrectionReceiver, TAG);
        LifecycleUtils.safeUnregisterReceiver(this, mLapSummaryReceiver, TAG);
        LifecycleUtils.safeUnregisterReceiver(this, mUserSelectedSportTypeChangedReceiver, TAG);

        if (mIsStopping) {
            CompletableFuture<Void> future = mFinalizationFuture;
            if (future != null && !future.isDone()) {
                try {
                    future.get(2, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Throwable ignored) {
                }
            }
            safeUnbindBanalService();
            performStopForeground();
            return;
        }

        final BanalFinalizationSnapshot snapshot = BanalFinalizationSnapshot.capture(
                mBanalService, this, getAverageSpeed(), averageSpeedCalculateable());

        final boolean interrupted = mTrackingInterrupted;
        final CompletableFuture<Void> finalizationFuture = new CompletableFuture<>();
        mFinalizationFuture = finalizationFuture;

        Runnable finalizationTask = () -> {
            try {
                if (!interrupted) {
                    endWorkout(snapshot);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Unexpected error during async workout finalization in onDestroy", t);
            } finally {
                mBanalService = null;
                releaseWakeLock();
                runOnMainThread(() -> {
                    try {
                        safeUnbindBanalService();
                        performStopForeground();
                    } finally {
                        finalizationFuture.complete(null);
                    }
                });
            }
        };

        try {
            mDbExecutor.execute(finalizationTask);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            Log.w(TAG, "mDbExecutor already shut down in onDestroy; running finalizationTask inline", e);
            finalizationTask.run();
        } finally {
            mDbExecutor.shutdown();
        }
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
     * synchronizing {@code mSamplesTableName} with {@code mBaseFileName}, instantiating and rehydrating
     * {@code mLiveSession} on {@code mDbExecutor}, completing {@code mTableInitializationFuture}, and
     * broadcasting {@code notifyTrackingStarted(mWorkoutID)}
     * ({@code REQ-TRK-026.1}, {@code REQ-TRK-026.3}, {@code REQ-TRK-031.3}, {@code ATT-3980}, {@code ATT-3987}).
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

        final long resumedWorkoutId = mWorkoutID;
        final int resumedActiveTimeS = mTimeActive_s;

        // ATT-4095 (REQ-TRK-036.1): Reuse existing in-memory LiveWorkoutSession on RESUME_BY_USER when service stayed alive
        if (mLiveSession != null && mLiveSession.getWorkoutId() == resumedWorkoutId && !mIsRehydrating) {
            Log.i(TAG, "applyResumedState: Reusing existing in-memory LiveWorkoutSession for workoutId=" + resumedWorkoutId);
            if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) {
                mTableInitializationFuture.complete(null);
            }
            if (resumedWorkoutId > 0) {
                notifyTrackingStarted(resumedWorkoutId);
            }
            return;
        }

        // ATT-4095 (REQ-TRK-036.2): Construct, rehydrate, and publish candidate session on mDbExecutor
        if (mTableInitializationFuture == null || mTableInitializationFuture.isDone()) {
            mTableInitializationFuture = new CompletableFuture<>();
        }
        mIsRehydrating = true;
        Runnable rehydrateTask = () -> {
            try {
                LiveWorkoutSession newSession = new LiveWorkoutSession(resumedWorkoutId, IMPORTANT_SENSOR_TYPES);
                rehydrateLiveSession(newSession, resumedWorkoutId, resumedActiveTimeS);
                mLiveSession = newSession;
            } finally {
                mIsRehydrating = false;
                if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) {
                    mTableInitializationFuture.complete(null);
                }
                if (resumedWorkoutId > 0) {
                    notifyTrackingStarted(resumedWorkoutId);
                }
            }
        };
        if (mDbExecutor != null && !mDbExecutor.isShutdown()) {
            mDbExecutor.execute(rehydrateTask);
        } else {
            rehydrateTask.run();
        }
    }

    /**
     * Overload for testing and backward compatibility delegating to {@link #rehydrateLiveSession(LiveWorkoutSession, long, int)}.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    void rehydrateLiveSession(long workoutId, int activeTimeSeconds) {
        final LiveWorkoutSession session = mLiveSession;
        if (session != null) {
            rehydrateLiveSession(session, workoutId, activeTimeSeconds);
        }
    }

    /**
     * Rehydrates the in-memory {@link LiveWorkoutSession} streams and per-sensor running statistics
     * from {@link WorkoutSummariesDatabaseManager} upon workout resumption ({@code REQ-TRK-031.3}, {@code REQ-TRK-036.2}, {@code ATT-3987}, {@code ATT-4095}).
     *
     * <p>Each encoded stream ({@code mapPolyline}, {@code altitudeStream}, {@code distanceStream}) is
     * decoded in its own independent {@code try / catch} block so a corrupted or empty stream falls back
     * to {@link Collections#emptyList()} while preserving sibling streams.
     *
     * @param session           The {@link LiveWorkoutSession} instance to populate.
     * @param workoutId         The resumed workout database ID.
     * @param activeTimeSeconds Restored pre-resume active duration in seconds (used as fallback weight for legacy rows).
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    void rehydrateLiveSession(@NonNull LiveWorkoutSession session, long workoutId, int activeTimeSeconds) {
        if (workoutId <= 0) {
            return;
        }
        try {
            WorkoutSummariesDatabaseManager summariesManager = WorkoutSummariesDatabaseManager.getInstance(this);
            if (summariesManager == null) {
                return;
            }

            // 1. Rehydrate encoded streams with independent per-stream fault isolation
            WorkoutSummariesDatabaseManager.EncodedStreams encodedStreams = summariesManager.getEncodedStreams(workoutId);
            if (encodedStreams != null) {
                List<LatLng> decodedLatLngs = Collections.emptyList();
                if (encodedStreams.mapPolyline != null && !encodedStreams.mapPolyline.isEmpty()) {
                    try {
                        decodedLatLngs = PolyUtil.decode(encodedStreams.mapPolyline);
                    } catch (Exception e) {
                        Log.w(TAG, "rehydrateLiveSession: Failed to decode mapPolyline for workout " + workoutId, e);
                        decodedLatLngs = Collections.emptyList();
                    }
                }

                List<Double> decodedAltitudes = Collections.emptyList();
                if (encodedStreams.altitudeStream != null && !encodedStreams.altitudeStream.isEmpty()) {
                    try {
                        decodedAltitudes = NumericalEncodingUtils.INSTANCE.decodeDoubles(encodedStreams.altitudeStream);
                    } catch (Exception e) {
                        Log.w(TAG, "rehydrateLiveSession: Failed to decode altitudeStream for workout " + workoutId, e);
                        decodedAltitudes = Collections.emptyList();
                    }
                }

                List<Double> decodedDistances = Collections.emptyList();
                if (encodedStreams.distanceStream != null && !encodedStreams.distanceStream.isEmpty()) {
                    try {
                        decodedDistances = NumericalEncodingUtils.INSTANCE.decodeDoubles(encodedStreams.distanceStream);
                    } catch (Exception e) {
                        Log.w(TAG, "rehydrateLiveSession: Failed to decode distanceStream for workout " + workoutId, e);
                        decodedDistances = Collections.emptyList();
                    }
                }

                session.rehydrateStreams(decodedLatLngs, decodedAltitudes, decodedDistances);
            }

            // 2. Rehydrate per-sensor extrema and running average sample counts
            List<WorkoutSummariesDatabaseManager.ExtremaRecord> extremaRecords =
                    summariesManager.getExtremaForWorkouts(Collections.singletonList(workoutId));
            if (extremaRecords != null && !extremaRecords.isEmpty()) {
                Map<SensorType, Double> minMap = new HashMap<>();
                Map<SensorType, LatLng> minPosMap = new HashMap<>();
                Map<SensorType, Double> maxMap = new HashMap<>();
                Map<SensorType, LatLng> maxPosMap = new HashMap<>();
                Map<SensorType, Double> avgMap = new HashMap<>();
                Map<SensorType, Integer> countMap = new HashMap<>();
                Set<SensorType> sensorsWithExtrema = new HashSet<>();
                LatLng persistedStartPos = null;
                Double persistedStartLat = null;
                Double persistedStartLon = null;

                for (WorkoutSummariesDatabaseManager.ExtremaRecord record : extremaRecords) {
                    if (record == null || record.sensorType == null || record.extremaType == null) continue;
                    if (record.extremaType == ExtremaType.START) {
                        if (record.position != null) {
                            persistedStartPos = record.position;
                        }
                        if (record.sensorType == SensorType.LATITUDE) {
                            persistedStartLat = record.value;
                        } else if (record.sensorType == SensorType.LONGITUDE) {
                            persistedStartLon = record.value;
                        }
                        continue;
                    }
                    if (record.extremaType == ExtremaType.MIN) {
                        minMap.put(record.sensorType, record.value);
                        if (record.position != null) {
                            minPosMap.put(record.sensorType, record.position);
                        }
                        sensorsWithExtrema.add(record.sensorType);
                    } else if (record.extremaType == ExtremaType.MAX) {
                        maxMap.put(record.sensorType, record.value);
                        if (record.position != null) {
                            maxPosMap.put(record.sensorType, record.position);
                        }
                        sensorsWithExtrema.add(record.sensorType);
                    } else if (record.extremaType == ExtremaType.AVG) {
                        avgMap.put(record.sensorType, record.value);
                        if (record.count != null && record.count > 0) {
                            countMap.put(record.sensorType, record.count);
                        }
                        sensorsWithExtrema.add(record.sensorType);
                    }
                }

                if (session.getStartLatLng() == null) {
                    if (persistedStartPos != null) {
                        session.setStartLatLng(persistedStartPos);
                    } else if (persistedStartLat != null && persistedStartLon != null) {
                        session.setStartLatLng(new LatLng(persistedStartLat, persistedStartLon));
                    }
                }

                for (SensorType sensorType : sensorsWithExtrema) {
                    Double minVal = minMap.get(sensorType);
                    LatLng minPos = minPosMap.get(sensorType);
                    Double maxVal = maxMap.get(sensorType);
                    LatLng maxPos = maxPosMap.get(sensorType);
                    Double avgVal = avgMap.get(sensorType);
                    Integer persistedCount = countMap.get(sensorType);
                    int sampleCount = (persistedCount != null && persistedCount > 0)
                            ? persistedCount
                            : (activeTimeSeconds > 0 ? activeTimeSeconds : 1);
                    session.rehydrateSensorStats(sensorType, minVal, minPos, maxVal, maxPos, avgVal, sampleCount);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "rehydrateLiveSession: Failed to rehydrate LiveWorkoutSession for workoutId=" + workoutId, t);
        }
    }

    @VisibleForTesting
    @Nullable
    LiveWorkoutSession getLiveSession() {
        return mLiveSession;
    }

    @VisibleForTesting
    boolean isRehydrating() {
        return mIsRehydrating;
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
        createNewLap(null);
    }

    protected void createNewLap(@Nullable BanalFinalizationSnapshot snapshot) {
        if (DEBUG) Log.i(TAG, "createNewLap");

        int prevLapNr = 0;
        int lapTime_s = 0;
        double lapDistance = 0.0;

        if (snapshot != null) {
            prevLapNr = snapshot.lapNr;
            lapTime_s = snapshot.lapTime_s;
            lapDistance = snapshot.lapDistance_m;
        } else {
            final BANALServiceComm banalService = mBanalService;
            if (banalService == null) {
                mCreateNewLapWhenConnectedToBanalService = true;
                return;
            }

            SensorData sensorData;

            sensorData = banalService.getBestSensorData(SensorType.LAP_NR);
            if (sensorData != null && sensorData.getValue() != null) {
                prevLapNr = (Integer) sensorData.getValue();
            }

            sensorData = banalService.getBestSensorData(SensorType.TIME_LAP);
            if (sensorData != null
                    && sensorData.getValue() != null) {
                lapTime_s = (Integer) sensorData.getValue();
            }

            sensorData = banalService.getBestSensorData(SensorType.DISTANCE_m_LAP);
            if (sensorData != null
                    && sensorData.getValue() != null) {
                lapDistance = (Double) sensorData.getValue();
            }
        }

        // ATT-896 / REQ-TRK-002: Guard against saving zero-duration / zero-distance phantom laps
        if (lapTime_s <= 0 && lapDistance <= 0.0) {
            if (DEBUG) Log.i(TAG, "Discarding zero-duration/zero-distance lap in createNewLap");
            return;
        }

        double lapSpeed = (lapTime_s > 0) ? (lapDistance / lapTime_s) : 0.0;

        saveLap(prevLapNr, lapTime_s, lapDistance, lapSpeed);
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
    public void endWorkout(@Nullable BanalFinalizationSnapshot snapshot) {
        mFinalizationSnapshot = snapshot;
        endWorkout();
    }

    public void endWorkout() {
        if (DEBUG) {
            Log.d(TAG, "endWorkout");
        }

        final BanalFinalizationSnapshot snapshot = mFinalizationSnapshot;

        try {
            createNewLap(snapshot);
        } catch (Throwable t) {
            Log.e(TAG, "Error in createNewLap during endWorkout", t);
        }

        final BANALServiceComm banalService = mBanalService;

        // store the ANT and BTLE Devices that were active during the workout
        if (DEBUG) Log.d(TAG, "storing active device list");
        try {
            if (snapshot != null && !snapshot.activeRemoteDeviceIds.isEmpty()) {
                mActiveSessionDeviceIds.addAll(snapshot.activeRemoteDeviceIds);
            } else {
                updateActiveSessionDevices(banalService);
            }
            ActiveDevicesDbHelper activeDevicesDbHelper = ActiveDevicesDbHelper.getInstance(this);
            for (Long deviceDbId : mActiveSessionDeviceIds) {
                if (deviceDbId != null && deviceDbId >= 0) {
                    activeDevicesDbHelper.addActiveDevice(mWorkoutID, deviceDbId);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error saving active devices during endWorkout", t);
        }

        try {
            WorkoutSummariesDatabaseManager summariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(this);
            SQLiteDatabase summariesDb = summariesDatabaseManager.getDatabase();

            // 1. Critical state transition: commit FINISHED = 1 first in its own dedicated, minimal transaction (REQ-TRK-030.1)
            summariesDb.beginTransaction();
            try {
                ContentValues finishedValues = new ContentValues();
                finishedValues.put(WorkoutSummaries.FINISHED, 1);
                summariesDb.update(WorkoutSummaries.TABLE,
                        finishedValues,
                        WorkoutSummaries.C_ID + "=" + mWorkoutID,
                        null);
                summariesDb.setTransactionSuccessful();
            } catch (Throwable t) {
                Log.e(TAG, "Error setting FINISHED=1 in summaries table during endWorkout", t);
            } finally {
                summariesDb.endTransaction();
            }

            // 2. Save the accumulated SensorTypes in an isolated block so failures cannot roll back FINISHED = 1
            Set<SensorType> accumulatedSensors = (snapshot != null && !snapshot.accumulatedSensorTypes.isEmpty())
                    ? snapshot.accumulatedSensorTypes
                    : (banalService != null ? banalService.getAccumulatedSensorTypeSet() : null);
            if (accumulatedSensors != null) {
                try {
                    summariesDatabaseManager.saveAccumulatedSensorTypes(mWorkoutID, accumulatedSensors);
                } catch (Throwable t) {
                    Log.e(TAG, "Error saving accumulated sensor types during endWorkout", t);
                }
            }

            // 3. Update auxiliary summary metadata (GC_DATA, SPORT_ID, B_SPORT) in an isolated block
            try {
                ContentValues summaryValues = new ContentValues();
                String gcData = (snapshot != null && snapshot.accumulatedGCDataString != null)
                        ? snapshot.accumulatedGCDataString
                        : (banalService != null ? banalService.getAccumulatedGCDataString() : null);
                if (gcData != null) {
                    summaryValues.put(WorkoutSummaries.GC_DATA, gcData);
                }

                try {
                    long sportTypeId = (snapshot != null)
                            ? snapshot.sportTypeId
                            : getSportTypeId();
                    summaryValues.put(WorkoutSummaries.SPORT_ID, sportTypeId);
                    BSportType bSportType = (snapshot != null && snapshot.bSportType != BSportType.UNKNOWN)
                            ? snapshot.bSportType
                            : SportTypeDatabaseManager.getInstance(this).getBSportType(sportTypeId);
                    if (bSportType != null) {
                        summaryValues.put(WorkoutSummaries.B_SPORT, bSportType.name());
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Error resolving sport type metadata during endWorkout", t);
                }

                if (summaryValues.size() > 0) {
                    summariesDb.beginTransaction();
                    try {
                        summariesDb.update(WorkoutSummaries.TABLE,
                                summaryValues,
                                WorkoutSummaries.C_ID + "=" + mWorkoutID,
                                null);
                        summariesDb.setTransactionSuccessful();
                    } finally {
                        summariesDb.endTransaction();
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error updating auxiliary summary metadata during endWorkout", t);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error accessing WorkoutSummariesDatabaseManager during endWorkout", t);
        }

        try {
            // Finalize Live Session (Auto Name, Commute, Trainer)
            if (mLiveSession != null) {
                finalizeLiveSession(snapshot);
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
            try {
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
            } catch (Throwable t) {
                Log.w(TAG, "Error broadcasting tracking finished intents during endWorkout", t);
            } finally {
                mFinalizationSnapshot = null;
            }
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
                        ActiveDevicesDbHelper.getInstance(this).addActiveDevice(mWorkoutID, deviceDbId);
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

        if (mIsStopping || banalService == null) return;

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

        // 1. Update LiveSession running statistics ONLY from authoritative best sensor telemetry (REQ-MAP-019, ATT-499, REQ-TRK-031.3)
        if (mLiveSession != null && !mIsRehydrating) {
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
                        if (mWorkoutRepository != null) {
                            mWorkoutRepository.updateExtremaValue(mWorkoutID, sensorType, ExtremaType.MIN, stats.min, stats.minPos);
                        }
                        extremaUpdates.add(new ExtremaUpdate(sensorType, ExtremaType.MIN, stats.min, stats.minPos));
                    }
                    if ((changed & LiveWorkoutSession.RunningStats.CHANGED_MAX) != 0) {
                        if (mWorkoutRepository != null) {
                            mWorkoutRepository.updateExtremaValue(mWorkoutID, sensorType, ExtremaType.MAX, stats.max, stats.maxPos);
                        }
                        extremaUpdates.add(new ExtremaUpdate(sensorType, ExtremaType.MAX, stats.max, stats.maxPos));
                    }
                    if ((changed & LiveWorkoutSession.RunningStats.CHANGED_AVG) != 0) {
                        // For Speed, we defer the average update until after the loop to use the 
                        // authoritative distance/time calculation (SCRUM-108)
                        if (sensorType != SensorType.SPEED_mps) {
                            if (mWorkoutRepository != null) {
                                mWorkoutRepository.updateExtremaValue(mWorkoutID, sensorType, ExtremaType.AVG, stats.getAverage(), null);
                            }
                            if (updateAvgInDb) {
                                extremaUpdates.add(new ExtremaUpdate(sensorType, ExtremaType.AVG, stats.getAverage(), null, stats.getCount()));
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
            if (mLiveSession != null && !mIsRehydrating && mLiveSession.getSensorStats().containsKey(SensorType.SPEED_mps)) {
                if (mWorkoutRepository != null) {
                    mWorkoutRepository.updateExtremaValue(mWorkoutID, SensorType.SPEED_mps, ExtremaType.AVG, authoritativeAvgSpeed, null);
                }
                if (updateAvgInDb) {
                    LiveWorkoutSession.RunningStats speedStats = mLiveSession.getSensorStats().get(SensorType.SPEED_mps);
                    Integer speedCount = speedStats != null ? speedStats.getCount() : null;
                    extremaUpdates.add(new ExtremaUpdate(SensorType.SPEED_mps, ExtremaType.AVG, authoritativeAvgSpeed, null, speedCount));
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
        if (mLiveSession != null && !mIsRehydrating) {
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
                    if (update.count != null) {
                        summariesManager.updateExtremaValue(summariesDb, workoutId, update.type, update.extrema, update.value, update.pos, update.count);
                    } else {
                        summariesManager.updateExtremaValue(summariesDb, workoutId, update.type, update.extrema, update.value, update.pos);
                    }
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
                    Log.w(TAG, "Failed to broadcast WORKOUT_UPDATED_INTENT", e);
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
            mAutoPauseManager.onManualResume(manualPauseLocked);
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
    public static boolean shouldLogSampleInsertFailure(int failureCount) {
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
        @Nullable
        final Integer count;

        ExtremaUpdate(SensorType type, ExtremaType extrema, double value, LatLng pos) {
            this(type, extrema, value, pos, null);
        }

        ExtremaUpdate(SensorType type, ExtremaType extrema, double value, LatLng pos, @Nullable Integer count) {
            this.type = type;
            this.extrema = extrema;
            this.value = value;
            this.pos = pos;
            this.count = count;
        }
    }

    /**
     * Checks whether a valid, finite average speed can be computed from active duration and distance
     * ({@code REQ-TRK-030.3}).
     *
     * @return {@code true} when {@code mTimeActive_s > 0} and {@code mDistanceTotal_m} is finite and non-negative.
     */
    protected boolean averageSpeedCalculateable() {
        return mTimeActive_s > 0 && Double.isFinite(mDistanceTotal_m) && mDistanceTotal_m >= 0.0;
    }

    /**
     * Returns the session average speed in meters per second, guarded against zero or negative
     * active duration and non-finite division results ({@code REQ-TRK-030.3}).
     *
     * @return finite average speed in m/s, or {@code 0.0} when active duration is {@code <= 0}.
     */
    protected double getAverageSpeed() {
        if (!averageSpeedCalculateable()) {
            return 0.0;
        }
        double speed = mDistanceTotal_m / mTimeActive_s;
        return Double.isFinite(speed) ? speed : 0.0;
    }

    /**
     * Finalizes the in-memory {@link LiveWorkoutSession} upon workout stop, persisting map polyline
     * and elevation/distance streams first, followed by extrema, physiology metrics, commute/trainer
     * heuristics, and identity/clustering/auto-naming in independently fault-isolated blocks
     * ({@code REQ-TRK-030.4}).
     */
    @VisibleForTesting
    protected void finalizeLiveSession() {
        finalizeLiveSession(null);
    }

    @VisibleForTesting
    protected void finalizeLiveSession(@Nullable BanalFinalizationSnapshot snapshot) {
        if (mLiveSession == null) return;
        if (DEBUG) Log.i(TAG, "finalizeLiveSession");

        final BANALServiceComm banalService = mBanalService;
        WorkoutSummariesDatabaseManager summariesManager = WorkoutSummariesDatabaseManager.getInstance(this);
        WorkoutRepository repository = (mWorkoutRepository != null)
                ? mWorkoutRepository
                : WorkoutRepository.Companion.getInstance((Application) getApplicationContext());

        // 1. Finalize Map and Streams first so route geometry and profile streams are never lost if downstream steps fail (REQ-TRK-030.4)
        try {
            List<LatLng> pointsToSimplify = mLiveSession.getRawLatLngs();
            if (pointsToSimplify == null || pointsToSimplify.isEmpty()) {
                pointsToSimplify = mLiveSession.getSampledLatLngs();
            }
            List<LatLng> simplifiedPoints = (pointsToSimplify != null && !pointsToSimplify.isEmpty())
                    ? PolyUtil.simplify(pointsToSimplify, 10.0)
                    : Collections.emptyList();
            String polyline = PolyUtil.encode(simplifiedPoints);
            List<Double> sampledAltitudes = mLiveSession.getSampledAltitudes();
            List<Double> sampledDistances = mLiveSession.getSampledDistances();
            String altStream = NumericalEncodingUtils.INSTANCE.encodeDoubles(sampledAltitudes != null ? sampledAltitudes : Collections.emptyList());
            String distStream = NumericalEncodingUtils.INSTANCE.encodeDoubles(sampledDistances != null ? sampledDistances : Collections.emptyList());
            summariesManager.updateMapAndStreams(mWorkoutID, polyline, altStream, distStream);
            if (repository != null) {
                repository.setMapPolyline(mWorkoutID, polyline);
                repository.setElevationStreams(mWorkoutID, altStream, distStream);
            }
        } catch (Throwable t) {
            Log.e(TAG, "finalizeLiveSession: Error persisting map polyline and elevation/distance streams", t);
        }

        // 2. Save START and END locations, Sensor Extrema, and Max Line Distance (REQ-TRK-030.4)
        try {
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

            for (Map.Entry<SensorType, LiveWorkoutSession.RunningStats> entry : mLiveSession.getSensorStats().entrySet()) {
                SensorType sensor = entry.getKey();
                LiveWorkoutSession.RunningStats stats = entry.getValue();
                if (stats == null) continue;

                // Min
                if (Double.isFinite(stats.min) && stats.min != Double.MAX_VALUE) {
                    summariesManager.updateExtremaValue(mWorkoutID, sensor, ExtremaType.MIN, stats.min, stats.minPos);
                    if (repository != null) {
                        repository.updateExtremaValue(mWorkoutID, sensor, ExtremaType.MIN, stats.min, stats.minPos);
                    }
                }
                // Max
                if (Double.isFinite(stats.max) && stats.max != -Double.MAX_VALUE) {
                    summariesManager.updateExtremaValue(mWorkoutID, sensor, ExtremaType.MAX, stats.max, stats.maxPos);
                    if (repository != null) {
                        repository.updateExtremaValue(mWorkoutID, sensor, ExtremaType.MAX, stats.max, stats.maxPos);
                    }
                }
                // Avg
                if (IMPORTANT_SENSOR_TYPES.contains(sensor)) {
                    double avgValue = stats.getAverage();
                    // Special case: for Speed, use the authoritative distance/time average (SCRUM-108)
                    if (sensor == SensorType.SPEED_mps && averageSpeedCalculateable()) {
                        avgValue = getAverageSpeed();
                    }
                    if (Double.isFinite(avgValue)) {
                        Integer avgCount = stats.getCount() > 0 ? stats.getCount() : null;
                        summariesManager.updateExtremaValue(mWorkoutID, sensor, ExtremaType.AVG, avgValue, null, avgCount);
                        if (repository != null) {
                            repository.updateExtremaValue(mWorkoutID, sensor, ExtremaType.AVG, avgValue, null);
                        }
                    }
                }
                if (sensor == SensorType.W_PRIME_BAL && stats.count > 0 && Double.isFinite(stats.last)) {
                    summariesManager.updateExtremaValue(mWorkoutID, sensor, ExtremaType.END, stats.last, endPos);
                    if (repository != null) {
                        repository.updateExtremaValue(mWorkoutID, sensor, ExtremaType.END, stats.last, endPos);
                    }
                }
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
                if (repository != null) {
                    repository.updateExtremaValue(mWorkoutID, SensorType.LINE_DISTANCE_m, ExtremaType.MAX, calculatedMaxDisp, calculatedApex);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "finalizeLiveSession: Error persisting start/end locations and sensor extrema", t);
        }

        // 3. Calculate and persist Efficiency Factor (EF), Aerobic Decoupling, and Power Training Metrics from recorded samples (REQ-UI-400.2, REQ-MIG-039.4)
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
                    BSportType liveSport = (snapshot != null && snapshot.bSportType != BSportType.UNKNOWN)
                            ? snapshot.bSportType
                            : ((banalService != null && banalService.getBSportType() != null)
                                    ? banalService.getBSportType()
                                    : BSportType.UNKNOWN);
                    com.atrainingtracker.trainingtracker.physiology.AerobicEfficiencyResult aerobicResult =
                            com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(
                                    powerSamples, speedSamples, hrSamples, liveSport
                            );
                    SensorType targetSensor = aerobicResult.isPowerBased() ? SensorType.POWER : SensorType.SPEED_mps;
                    if (aerobicResult.getEfficiencyFactor() != null) {
                        double ef = aerobicResult.getEfficiencyFactor();
                        summariesManager.updateExtremaValue(mWorkoutID, targetSensor, ExtremaType.EFFICIENCY_FACTOR, ef, null);
                        if (repository != null) {
                            repository.updateExtremaValue(mWorkoutID, targetSensor, ExtremaType.EFFICIENCY_FACTOR, ef, null);
                        }
                    }
                    if (aerobicResult.getDecouplingPct() != null) {
                        double dec = aerobicResult.getDecouplingPct();
                        summariesManager.updateExtremaValue(mWorkoutID, targetSensor, ExtremaType.AEROBIC_DECOUPLING, dec, null);
                        if (repository != null) {
                            repository.updateExtremaValue(mWorkoutID, targetSensor, ExtremaType.AEROBIC_DECOUPLING, dec, null);
                        }
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
                        if (repository != null) {
                            repository.updateExtremaValue(mWorkoutID, SensorType.NORMALIZED_POWER, ExtremaType.AVG, np, null);
                        }
                    }
                    if (powerMetrics.getIntensityFactor() != null) {
                        double ifVal = powerMetrics.getIntensityFactor();
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.INTENSITY_FACTOR, ExtremaType.AVG, ifVal, null);
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.INTENSITY_FACTOR, ExtremaType.END, ifVal, null);
                        if (repository != null) {
                            repository.updateExtremaValue(mWorkoutID, SensorType.INTENSITY_FACTOR, ExtremaType.AVG, ifVal, null);
                        }
                    }
                    if (powerMetrics.getTss() != null) {
                        double tss = powerMetrics.getTss();
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.TSS, ExtremaType.AVG, tss, null);
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.TSS, ExtremaType.MAX, tss, null);
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.TSS, ExtremaType.END, tss, null);
                        if (repository != null) {
                            repository.updateExtremaValue(mWorkoutID, SensorType.TSS, ExtremaType.AVG, tss, null);
                        }
                    }
                    if (powerMetrics.getWorkKj() != null) {
                        double workKj = powerMetrics.getWorkKj();
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.WORK_KJ, ExtremaType.AVG, workKj, null);
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.WORK_KJ, ExtremaType.MAX, workKj, null);
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.WORK_KJ, ExtremaType.END, workKj, null);
                        if (repository != null) {
                            repository.updateExtremaValue(mWorkoutID, SensorType.WORK_KJ, ExtremaType.AVG, workKj, null);
                        }
                    }
                    if (powerMetrics.getAvgPowerToWeight() != null) {
                        double avgWPerKg = powerMetrics.getAvgPowerToWeight();
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.POWER_TO_WEIGHT, ExtremaType.AVG, avgWPerKg, null);
                        if (repository != null) {
                            repository.updateExtremaValue(mWorkoutID, SensorType.POWER_TO_WEIGHT, ExtremaType.AVG, avgWPerKg, null);
                        }
                    }
                    if (powerMetrics.getMaxPowerToWeight() != null) {
                        double maxWPerKg = powerMetrics.getMaxPowerToWeight();
                        summariesManager.updateExtremaValue(mWorkoutID, SensorType.POWER_TO_WEIGHT, ExtremaType.MAX, maxWPerKg, null);
                        if (repository != null) {
                            repository.updateExtremaValue(mWorkoutID, SensorType.POWER_TO_WEIGHT, ExtremaType.MAX, maxWPerKg, null);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "finalizeLiveSession: Could not compute Aerobic Efficiency / Decoupling or Power Training extrema", t);
        }

        // 4. Guess Commute and Trainer (REQ-TRK-030.4)
        try {
            guessCommuteAndTrainer();
        } catch (Throwable t) {
            Log.e(TAG, "finalizeLiveSession: Error in guessCommuteAndTrainer", t);
        }

        // 5. Identity Determination, Route Clustering & Auto-Naming (SCRUM-200, ATT-1398, REQ-TRK-030.4)
        try {
            EquipmentAndSportTypeDiscoveryManager discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(this);
            final Set<Long> activeRemoteDeviceIds = (snapshot != null && !snapshot.activeRemoteDeviceIds.isEmpty())
                    ? snapshot.activeRemoteDeviceIds
                    : ((banalService != null)
                            ? new HashSet<>(banalService.getDatabaseIdsOfActiveRemoteDevices())
                            : new HashSet<>(mActiveSessionDeviceIds));
            final BSportType banalSportType = (snapshot != null && snapshot.bSportType != BSportType.UNKNOWN)
                    ? snapshot.bSportType
                    : ((banalService != null && banalService.getBSportType() != null)
                            ? banalService.getBSportType()
                            : BSportType.UNKNOWN);

            // Initial arbitration using currently active devices in memory (more reliable than waiting for DB sync)
            EquipmentAndSportTypeDiscoveryManager.InferredIdentity identity = discoveryManager.resolveIdentity(activeRemoteDeviceIds, banalSportType, getAverageSpeed());

            LatLng startPosRaw = mLiveSession.getStartLatLng();
            LatLng endPosRaw = mLiveSession.getLastLatLng();
            LatLng maxDispPos = summariesManager.getExtremaPosition(mWorkoutID, SensorType.LINE_DISTANCE_m, ExtremaType.MAX);

            if (startPosRaw != null && endPosRaw != null && maxDispPos != null) {
                WorkoutClusterEngine engine = WorkoutClusterEngine.Companion.getInstance(this);

                // Determine candidate sport types for clustering (ATT-773, REQ-SET-064)
                Set<BSportType> candidateSports;
                BSportType userSelectedSport = (snapshot != null)
                        ? snapshot.userSelectedSportType
                        : ((banalService != null) ? banalService.getUserSelectedBSportType() : null);
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
        } catch (Throwable t) {
            Log.e(TAG, "finalizeLiveSession: Error in identity determination, clustering, or auto-naming", t);
        }
    }

    @VisibleForTesting
    protected void guessCommuteAndTrainer() {
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

            WorkoutRepository repository = (mWorkoutRepository != null)
                    ? mWorkoutRepository
                    : WorkoutRepository.Companion.getInstance((Application) getApplicationContext());
            if (repository != null) {
                repository.setCommuteAndTrainer(mWorkoutID, commute, trainer);
            }
        }
    }


    public enum StartType {START_NORMAL, RESUME_BY_USER, RESUME_SERVICE_RECREATION}

}
