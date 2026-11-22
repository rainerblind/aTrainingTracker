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
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository;
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository;
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository;
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository;
import com.atrainingtracker.trainingtracker.database.MilestoneAutoNamingManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSource;

import com.atrainingtracker.BuildConfig;
import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm;
import com.atrainingtracker.banalservice.devices.AltitudeFromPressureDevice;
import com.atrainingtracker.banalservice.sensor.LapTelemetryExtractor;
import com.atrainingtracker.banalservice.sensor.LapTelemetrySnapshot;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorData;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.SensorValueType;
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager;
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository;
import com.atrainingtracker.trainingtracker.exporter.ExportManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.ExtremaType;
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository;
import com.atrainingtracker.banalservice.devices.RrBeatEvent;
import com.atrainingtracker.banalservice.devices.RrBeatListener;
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrInterval;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.atrainingtracker.database.SqliteSchemaUtils;
import com.atrainingtracker.trainingtracker.ui.util.NumericalEncodingUtils;
import com.atrainingtracker.banalservice.devices.radar.RadarLightController;
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController;
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarVeritySenseOfflineController;
import com.atrainingtracker.banalservice.devices.radar.RadarSnoozeController;
import com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator;
import com.atrainingtracker.banalservice.devices.radar.SmartBrakeLightDetector;
import com.atrainingtracker.trainingtracker.audio.radar.RadarAlertManager;
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig;
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseManager;
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseState;
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseTransition;
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper;
import com.atrainingtracker.trainingtracker.settings.TuningConfig;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.odometry.MovingSpeedSummary;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
        IMPORTANT_SENSOR_TYPES.add(SensorType.VERTICAL_OSCILLATION);
        IMPORTANT_SENSOR_TYPES.add(SensorType.GROUND_CONTACT_TIME);
    }

    // Sensors to track for Min/Max and used for average if in IMPORTANT_SENSOR_TYPES
    private static final HashSet<SensorType> SENSORS_TO_TRACK = new HashSet<>(IMPORTANT_SENSOR_TYPES);
    static {
        SENSORS_TO_TRACK.add(SensorType.LATITUDE);
        SENSORS_TO_TRACK.add(SensorType.LONGITUDE);
        SENSORS_TO_TRACK.add(SensorType.LINE_DISTANCE_m);
        SENSORS_TO_TRACK.add(SensorType.W_PRIME_BAL);
        SENSORS_TO_TRACK.add(SensorType.RADAR_VEHICLE_COUNT);
        SENSORS_TO_TRACK.add(SensorType.RADAR_APPROACH_SPEED_mps);
        SENSORS_TO_TRACK.add(SensorType.RADAR_PASSING_DISTANCE_m);
        SENSORS_TO_TRACK.add(SensorType.RADAR_LARGE_VEHICLE_COUNT);
    }
    
    public static final String ACTION_STOP = "com.atrainingtracker.trainingtracker.tracker.TrackerService.ACTION_STOP";
    public static final String START_TYPE = "START_TYPE";
    public static final String EXTRA_INDOOR = "com.atrainingtracker.trainingtracker.tracker.EXTRA_INDOOR";
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
    private final AtomicInteger mSessionGeneration = new AtomicInteger(0);
    private volatile boolean mReceiversRegistered = false;
    @Nullable private volatile MilestoneAutoNamingManager.MilestoneCelebrationData mFirstJubileeCelebrationData = null;
    private volatile boolean mDeferHrPhysiologyToPostProcessing = false;
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
    WorkoutRepository mWorkoutRepository;
    SportTypesRepository mSportTypesRepository;
    DeviceDataRepository mDeviceDataRepository;
    KnownLocationsRepository mKnownLocationsRepository;
    WorkoutRrRepository mWorkoutRrRepository;
    private final LiveWorkoutBroadcastTracker mBroadcastTracker = new LiveWorkoutBroadcastTracker(workoutId -> {
        try {
            LocalBroadcastManager.getInstance(TrackerService.this)
                    .sendBroadcast(new Intent(WORKOUT_UPDATED_INTENT).putExtra(WORKOUT_ID, workoutId));
        } catch (Throwable t) {
            Log.w(TAG, "Failed to broadcast WORKOUT_UPDATED_INTENT", t);
        }
    });
    @Nullable
    private volatile DebugHeapWatchdog mDebugHeapWatchdog;

    @NonNull
    @VisibleForTesting
    DebugHeapWatchdog getDebugHeapWatchdog() {
        DebugHeapWatchdog existing = mDebugHeapWatchdog;
        if (existing == null) {
            existing = new DebugHeapWatchdog(getSafeContext());
            mDebugHeapWatchdog = existing;
        }
        return existing;
    }

    private Context getSafeContext() {
        try {
            Context ctx = getApplicationContext();
            if (ctx != null) {
                return ctx;
            }
        } catch (Throwable ignored) {
        }
        return this;
    }

    private WorkoutRepository getWorkoutRepository() {
        if (mWorkoutRepository == null) {
            mWorkoutRepository = WorkoutRepository.Companion.getInstance(getSafeContext());
        }
        return mWorkoutRepository;
    }

    private SportTypesRepository getSportTypesRepository() {
        if (mSportTypesRepository == null) {
            mSportTypesRepository = SportTypesRepository.Companion.getInstance(getSafeContext());
        }
        return mSportTypesRepository;
    }

    private DeviceDataRepository getDeviceDataRepository() {
        if (mDeviceDataRepository == null) {
            mDeviceDataRepository = DeviceDataRepository.Companion.getInstance(getSafeContext());
        }
        return mDeviceDataRepository;
    }

    private KnownLocationsRepository getKnownLocationsRepository() {
        if (mKnownLocationsRepository == null) {
            mKnownLocationsRepository = KnownLocationsRepository.Companion.getInstance(getSafeContext());
        }
        return mKnownLocationsRepository;
    }

    private WorkoutRrRepository getWorkoutRrRepository() {
        if (mWorkoutRrRepository == null) {
            mWorkoutRrRepository = WorkoutRrRepository.Companion.getInstance(getSafeContext());
        }
        return mWorkoutRrRepository;
    }
    private volatile ScheduledFuture mTrackerHandle;
    private static final int RR_FLUSH_BATCH_THRESHOLD = 100;
    private long mLastRrFlushTimeMs = 0L;
    private final Object mRrFlushLock = new Object();
    private final java.util.concurrent.ExecutorService mRrFlushExecutor = Executors.newSingleThreadExecutor();
    private volatile Future<?> mPendingRrFlushFuture = null;
    private final ConcurrentLinkedQueue<WorkoutRrInterval> mLiveRrQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger mRrBeatIndexCounter = new AtomicInteger(0);
    private final AtomicLong mLastRrBeatTimestampMs = new AtomicLong(0L);
    private final RrBeatListener mRrBeatListener = new RrBeatListener() {
        @Override
        public void onRrBeat(@NonNull RrBeatEvent event) {
            if (!event.isOpticalPpi() && mAutoPauseManager.getCurrentState() == AutoPauseState.TRACKING && mWorkoutID > 0) {
                int idx = mRrBeatIndexCounter.getAndIncrement();
                long monotonicTimestampMs = mLastRrBeatTimestampMs.accumulateAndGet(
                        event.getBeatTimestampMs(),
                        Math::max
                );
                mLiveRrQueue.add(new WorkoutRrInterval(
                        null,
                        mWorkoutID,
                        idx,
                        monotonicTimestampMs,
                        (float) event.getRrMs(),
                        event.getArtifactFlag(),
                        event.getGapBeats()
                ));
                if (mLiveRrQueue.size() >= RR_FLUSH_BATCH_THRESHOLD) {
                    flushLiveRrQueueAsync();
                }
            }
        }
    };

    private void maybeFlushLiveRrQueue() {
        if (mLiveRrQueue.isEmpty() || mWorkoutID <= 0 || mAutoPauseManager.getCurrentState() != AutoPauseState.TRACKING) {
            return;
        }
        long now = System.currentTimeMillis();
        if (mLiveRrQueue.size() >= RR_FLUSH_BATCH_THRESHOLD || (now - mLastRrFlushTimeMs) >= 30000L) {
            flushLiveRrQueueAsync();
        }
    }

    private void submitOrInsertRrBatch(List<WorkoutRrInterval> batch, long workoutId, String mode) {
        if (!mRrFlushExecutor.isShutdown()) {
            try {
                mPendingRrFlushFuture = mRrFlushExecutor.submit(() -> {
                    try {
                        getWorkoutRrRepository().insertIntervalsBlocking(batch);
                    } catch (Throwable t) {
                        Log.e(TAG, "Error " + mode + " flushing live RR intervals for workout " + workoutId, t);
                    }
                });
                return;
            } catch (java.util.concurrent.RejectedExecutionException e) {
                Log.w(TAG, "mRrFlushExecutor shut down concurrently; flushing live RR inline for workout " + workoutId);
            }
        }
        try {
            getWorkoutRrRepository().insertIntervalsBlocking(batch);
        } catch (Throwable t) {
            Log.e(TAG, "Error inline flushing live RR intervals for workout " + workoutId, t);
        }
    }

    @VisibleForTesting
    void flushLiveRrQueueAsync() {
        if (mLiveRrQueue.isEmpty() || mWorkoutID <= 0) {
            return;
        }
        mLastRrFlushTimeMs = System.currentTimeMillis();
        final long workoutId = mWorkoutID;
        synchronized (mRrFlushLock) {
            final List<WorkoutRrInterval> batch = new ArrayList<>();
            WorkoutRrInterval item;
            while ((item = mLiveRrQueue.poll()) != null) {
                batch.add(item);
            }
            if (!batch.isEmpty()) {
                submitOrInsertRrBatch(batch, workoutId, "asynchronously");
            }
        }
    }

    @VisibleForTesting
    void flushLiveRrQueueSync(long workoutId) {
        Future<?> pending;
        synchronized (mRrFlushLock) {
            final List<WorkoutRrInterval> batch = new ArrayList<>();
            WorkoutRrInterval item;
            while ((item = mLiveRrQueue.poll()) != null) {
                batch.add(item);
            }
            if (!batch.isEmpty()) {
                submitOrInsertRrBatch(batch, workoutId, "synchronously");
            }
            pending = mPendingRrFlushFuture;
        }
        if (pending != null) {
            try {
                pending.get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Throwable t) {
                Log.e(TAG, "Error waiting for pending RR flush for workout " + workoutId, t);
            }
        }
    }

    @VisibleForTesting
    ConcurrentLinkedQueue<WorkoutRrInterval> getLiveRrQueueForTesting() {
        return mLiveRrQueue;
    }

    @VisibleForTesting
    Future<?> getPendingRrFlushFutureForTesting() {
        return mPendingRrFlushFuture;
    }

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
     * Returns the monotonic session generation counter ({@code REQ-TRK-076.3}).
     */
    @VisibleForTesting
    public int getSessionGenerationForTesting() {
        return mSessionGeneration.get();
    }

    /**
     * Returns whether the background RR flush executor has been shut down ({@code REQ-TRK-076.1}).
     */
    @VisibleForTesting
    public boolean isRrFlushExecutorShutdownForTesting() {
        return mRrFlushExecutor.isShutdown();
    }

    /**
     * Cancels the active 1 Hz sampling tracker handle and clears the reference ({@code REQ-TRK-026.3}, {@code REQ-TRK-033.1}).
     */
    @VisibleForTesting
    void cancelTrackerHandle() {
        ScheduledFuture<?> handle = mTrackerHandle;
        if (handle != null) {
            handle.cancel(true);
            mTrackerHandle = null;
        }
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
        public final double avgSpeed;
        public final boolean avgSpeedCalculable;
        public final double totalDistance_m;
        public final int timeActive_s;
        @Nullable public final MovingSpeedSummary movingSpeedSummary;

        public BanalFinalizationSnapshot(int lapNr, int lapTime_s, double lapDistance_m,
                @Nullable Set<Long> activeRemoteDeviceIds, @Nullable Set<SensorType> accumulatedSensorTypes,
                @Nullable String accumulatedGCDataString, @Nullable BSportType bSportType,
                @Nullable BSportType userSelectedSportType, long sportTypeId) {
            this(lapNr, lapTime_s, lapDistance_m, activeRemoteDeviceIds, accumulatedSensorTypes,
                    accumulatedGCDataString, bSportType, userSelectedSportType, sportTypeId,
                    0.0, false, 0.0, 0, null);
        }

        public BanalFinalizationSnapshot(int lapNr, int lapTime_s, double lapDistance_m,
                @Nullable Set<Long> activeRemoteDeviceIds, @Nullable Set<SensorType> accumulatedSensorTypes,
                @Nullable String accumulatedGCDataString, @Nullable BSportType bSportType,
                @Nullable BSportType userSelectedSportType, long sportTypeId,
                double avgSpeed, boolean avgSpeedCalculable, double totalDistance_m, int timeActive_s) {
            this(lapNr, lapTime_s, lapDistance_m, activeRemoteDeviceIds, accumulatedSensorTypes,
                    accumulatedGCDataString, bSportType, userSelectedSportType, sportTypeId,
                    avgSpeed, avgSpeedCalculable, totalDistance_m, timeActive_s, null);
        }

        public BanalFinalizationSnapshot(int lapNr, int lapTime_s, double lapDistance_m,
                @Nullable Set<Long> activeRemoteDeviceIds, @Nullable Set<SensorType> accumulatedSensorTypes,
                @Nullable String accumulatedGCDataString, @Nullable BSportType bSportType,
                @Nullable BSportType userSelectedSportType, long sportTypeId,
                double avgSpeed, boolean avgSpeedCalculable, double totalDistance_m, int timeActive_s,
                @Nullable MovingSpeedSummary movingSpeedSummary) {
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
            this.avgSpeed = avgSpeed;
            this.avgSpeedCalculable = avgSpeedCalculable;
            this.totalDistance_m = totalDistance_m;
            this.timeActive_s = timeActive_s;
            this.movingSpeedSummary = movingSpeedSummary;
        }

        @NonNull
        public static BanalFinalizationSnapshot capture(
                @Nullable BANALServiceComm banalService,
                @NonNull Context context,
                double avgSpeed,
                boolean avgSpeedCalculable) {
            return capture(banalService, context, avgSpeed, avgSpeedCalculable, 0.0, 0);
        }

        @NonNull
        public static BanalFinalizationSnapshot capture(
                @Nullable BANALServiceComm banalService,
                @NonNull Context context,
                double avgSpeed,
                boolean avgSpeedCalculable,
                double totalDistance_m,
                int timeActive_s) {
            if (banalService == null) {
                return new BanalFinalizationSnapshot(
                        0, 0, 0.0,
                        Collections.emptySet(),
                        Collections.emptySet(),
                        null,
                        BSportType.UNKNOWN,
                        null,
                        BANALService.getDefaultSportTypeId(),
                        avgSpeed,
                        avgSpeedCalculable,
                        totalDistance_m,
                        timeActive_s);
            }

            LapTelemetrySnapshot lap = LapTelemetryExtractor.extract(
                    banalService.getBestSensorData(SensorType.LAP_NR),
                    banalService.getBestSensorData(SensorType.TIME_LAP),
                    banalService.getBestSensorData(SensorType.DISTANCE_m_LAP),
                    false);
            int lapNr = lap.lapNr;
            int lapTime_s = lap.lapTimeSeconds;
            double lapDistance_m = lap.lapDistanceMeters;

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
            try { userSelectedSport = banalService.getUserSelectedBSportType(); } catch (Throwable ignored) {}
            MovingSpeedSummary movingSummary = null;
            try { movingSummary = banalService.getMovingSpeedSummary(); } catch (Throwable ignored) {}

            long sportTypeId = EquipmentAndSportTypeDiscoveryManager.resolveSnapshotSportTypeId(
                    context, banalService, deviceIds, bSport, userSelectedSport, avgSpeed, avgSpeedCalculable, movingSummary);

            return new BanalFinalizationSnapshot(
                    lapNr, lapTime_s, lapDistance_m, deviceIds, accumulatedSensors, gcData,
                    bSport, userSelectedSport, sportTypeId, avgSpeed, avgSpeedCalculable,
                    totalDistance_m, timeActive_s, movingSummary);
        }
    }

    private final AutoPauseManager mAutoPauseManager = new AutoPauseManager();

    public AutoPauseManager getAutoPauseManager() {
        return mAutoPauseManager;
    }

    private RadarAlertManager mRadarAlertManager;

    @VisibleForTesting
    @Nullable
    public RadarAlertManager getRadarAlertManager() {
        return mRadarAlertManager;
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
    private final Set<Long> mActiveSessionDeviceIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile LiveWorkoutSession mLiveSession;
    private volatile boolean mIsRehydrating = false;
    private volatile CompletableFuture<Void> mTableInitializationFuture = CompletableFuture.completedFuture(null);
    private final WorkoutRecordingErrorCollector mDiagnosticCollector = new WorkoutRecordingErrorCollector();
    private volatile StartType mStartType;
    private volatile boolean mWorkoutStartLocationRecorded = false;

    public CompletableFuture<Void> getTableInitializationFuture() {
        return mTableInitializationFuture;
    }

    @VisibleForTesting
    public WorkoutRecordingErrorCollector getDiagnosticCollector() {
        return mDiagnosticCollector;
    }
    final MultisportLapCoordinator mMultisportLapCoordinator = new MultisportLapCoordinator();
    final BroadcastReceiver mLapSummaryReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, @NonNull Intent intent) {
            mMultisportLapCoordinator.onLapSummaryIntent(intent, mWorkoutID, System.currentTimeMillis(),
                    mTimeActive_s, mTimeTotal_s, mDistanceTotal_m, mIsStopping, getWorkoutRepository(),
                    (nr, t, d, s) -> { saveLap(nr, t, d, s); return kotlin.Unit.INSTANCE; },
                    id -> { mBroadcastTracker.onDiscreteEdge(id); return kotlin.Unit.INSTANCE; });
        }
    };
    final BroadcastReceiver mMultisportTransitionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, @NonNull Intent intent) {
            mMultisportLapCoordinator.onMultisportTransitionIntent(TrackerService.this, intent, mBanalService,
                    () -> mWorkoutID, () -> mBaseFileName, () -> mIsStopping, getWorkoutRepository(),
                    mTableInitializationFuture, mDbExecutor,
                    id -> { mBroadcastTracker.onDiscreteEdge(id); return kotlin.Unit.INSTANCE; });
        }
    };
    private volatile String mBaseFileName;

    // private String mSport;  
    // private String mGCDataString;
    // private String[] mSensorNames;
    private volatile String mSamplesTableName;
    private volatile long mSessionEpochStartTimeMs = -1L;
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

            long prevCalTimeMs = intent.getLongExtra(AltitudeFromPressureDevice.PREV_CALIBRATION_TIME_MS_EXTRA, -1L);
            long currentCalTimeMs = intent.getLongExtra(AltitudeFromPressureDevice.CALIBRATION_TIME_MS_EXTRA, System.currentTimeMillis());
            final long sessionEpochStartTimeMs = mSessionEpochStartTimeMs;

            // REQ-FIL-018.2: Only update canonical LiveWorkoutSession & WorkoutSummaries when pressure is the active canonical ALTITUDE source
            boolean isBaroCanonical = isBarometricCanonicalAltitude();

            if (DEBUG)
                Log.i(TAG, "Triggering atomic altitude correction by " + altitudeCorrection
                        + " m (prevCal=" + prevCalTimeMs + ", currentCal=" + currentCalTimeMs
                        + ", epochStart=" + sessionEpochStartTimeMs + ", baroCanonical=" + isBaroCanonical + ")");

            // 1. Live Session Update
            final LiveWorkoutSession session = mLiveSession;
            if (isBaroCanonical && session != null) {
                session.applyRetroactiveAltitudeCorrection(altitudeCorrection, prevCalTimeMs, currentCalTimeMs);
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

                // 2a. Raw Samples drift/shift correction strictly scoped to barometric columns + baro-sourced canonical rows (REQ-FIL-018.1, REQ-FIL-018.2)
                int canonicalShifted = getWorkoutRepository().applyAltitudeDriftCorrection(
                        samplesTable, altitudeCorrection, prevCalTimeMs, currentCalTimeMs, sessionEpochStartTimeMs);

                // 2b. Summary Extrema and Elevation Stream sync only if barometric pressure is canonical (REQ-FIL-018.2, REQ-FIL-018.3)
                if (isBaroCanonical && (canonicalShifted > 0 || sessionEpochStartTimeMs <= 0)) {
                    if (session != null) {
                        LiveWorkoutSession.RunningStats altStats = session.getSensorStats().get(SensorType.ALTITUDE);
                        getWorkoutRepository().syncAltitudeSummaryFromSession(
                                workoutId,
                                session.getSampledAltitudes(),
                                session.getSampledAscent(),
                                session.getSampledDescent(),
                                altStats);
                    } else {
                        getWorkoutRepository().shiftAltitudeData(workoutId, altitudeCorrection, prevCalTimeMs, currentCalTimeMs);
                    }
                }

                // 3. Notify UI/Repository to refresh from DB
                LocalBroadcastManager.getInstance(TrackerService.this).sendBroadcast(new Intent(WORKOUT_UPDATED_INTENT).putExtra(WORKOUT_ID, workoutId));
            }, mDbExecutor).exceptionally(ex -> {
                Log.e(TAG, "Altitude shift skipped due to initialization failure: " + ex.getMessage());
                return null;
            });
        }
    };

    private boolean isBarometricCanonicalAltitude() {
        BANALServiceComm banal = mBanalService;
        if (banal == null) {
            return true;
        }
        try {
            long bestAltDeviceId = banal.getSourceDeviceId(SensorType.ALTITUDE);
            long baroDeviceId = getDeviceDataRepository().getAltitudeFromPressureDeviceId();
            if (bestAltDeviceId > 0 && baroDeviceId > 0 && bestAltDeviceId != baroDeviceId) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        return true;
    }
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
                    maybeFlushLiveRrQueue();
                    if (BuildConfig.DEBUG) {
                        getDebugHeapWatchdog().onTick(mBaseFileName, banalService);
                    }

                    // update notification
                    if (DEBUG) Log.i(TAG, "updating notification");
                    if (mTrainingApplication != null) {
                        long sportTypeId = banalService.getSportTypeId();
                        String sportUiName;
                        if (sportTypeId == mCachedSportTypeId && mCachedSportTypeUiName != null) {
                            sportUiName = mCachedSportTypeUiName;
                        } else {
                            sportUiName = getSportTypesRepository().getUIName(sportTypeId);
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
            if (mBanalService != null) {
                mBanalService.addRrBeatListener(mRrBeatListener);
                try {
                    long sportTypeId = getSportTypeId();
                    boolean isIndoor = getSportTypesRepository().isIndoorSportType(sportTypeId);
                    mBanalService.setIndoorMode(isIndoor);
                } catch (Throwable ignored) {
                }
            }

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
            }

            if (mCreateNewLapWhenConnectedToBanalService) {
                mCreateNewLapWhenConnectedToBanalService = false;
                createNewLap();
            }

            if (!BANALService.isSearching()) {
                onSearchingFinished();
            }

            if (mStartType == StartType.START_NORMAL && mAutoPauseManager.getCurrentState() == AutoPauseState.TRACKING) {
                if (TrainingApplication.isAutoPaused()) {
                    mAutoPauseManager.setState(AutoPauseState.AUTO_PAUSED);
                    mAutoPauseManager.resetDebounceCounter();
                } else {
                    BSportType sportType = mBanalService.getBSportType();
                    if (sportType == null) {
                        sportType = BSportType.UNKNOWN;
                    }
                    AutoPauseConfig config = getAutoPauseConfig();
                    SensorData<Number> speedData = mBanalService.getBestSensorData(SensorType.SPEED_mps);
                    Double speedMps = (speedData != null && speedData.getValue() != null) ? speedData.getValue().doubleValue() : null;
                    boolean hasSpeedSource = (speedData != null);
                    if (AutoPauseManager.shouldStartInAutoPause(config, sportType, speedMps, hasSpeedSource)) {
                        mAutoPauseManager.setState(AutoPauseState.AUTO_PAUSED);
                        mAutoPauseManager.resetDebounceCounter();
                        mTrainingApplication.pauseTracking(true);
                    }
                }
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            if (mBanalService != null) {
                mBanalService.removeRrBeatListener(mRrBeatListener);
            }
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
        mWorkoutRepository = getWorkoutRepository();
        mSportTypesRepository = getSportTypesRepository();
        mDeviceDataRepository = getDeviceDataRepository();
        mKnownLocationsRepository = getKnownLocationsRepository();
        mWorkoutRrRepository = getWorkoutRrRepository();

        // Background initialization of database to avoid ANR during upgrade
        new Thread(() -> {
            try {
                getWorkoutRepository().warmUp();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing WorkoutSummaries database", e);
            }
        }).start();

        // request bind to the BANAL Service
        bindService(new Intent(this, BANALService.class), mBanalConnection, Context.BIND_AUTO_CREATE);
        mBanalBound = true;

        registerTransientReceivers();

        try {
            mRadarAlertManager = new RadarAlertManager(this);
            mRadarAlertManager.start();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to initialize RadarAlertManager", t);
        }

        acquireWakeLock();
    }

    protected synchronized void registerTransientReceivers() {
        if (!mReceiversRegistered) {
            try {
                ContextCompat.registerReceiver(this, mSearchingFinishedReceiver, mSearchingFinishedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
                ContextCompat.registerReceiver(this, mAltitudeCorrectionReceiver, mAltitudeCorrectionFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
                ContextCompat.registerReceiver(this, mLapSummaryReceiver, mLapSummaryFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
                ContextCompat.registerReceiver(this, mMultisportTransitionReceiver, new IntentFilter(BANALService.MULTISPORT_TRANSITION_INTENT), ContextCompat.RECEIVER_NOT_EXPORTED);
                ContextCompat.registerReceiver(this, mUserSelectedSportTypeChangedReceiver, new IntentFilter(BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT), ContextCompat.RECEIVER_NOT_EXPORTED);
                mReceiversRegistered = true;
            } catch (Throwable t) {
                Log.w(TAG, "Error registering transient receivers", t);
            }
        }
    }

    protected synchronized void unregisterTransientReceivers() {
        if (mReceiversRegistered) {
            try {
                LifecycleUtils.safeUnregisterReceiver(this, mSearchingFinishedReceiver, TAG);
                LifecycleUtils.safeUnregisterReceiver(this, mAltitudeCorrectionReceiver, TAG);
                LifecycleUtils.safeUnregisterReceiver(this, mLapSummaryReceiver, TAG);
                LifecycleUtils.safeUnregisterReceiver(this, mMultisportTransitionReceiver, TAG);
                LifecycleUtils.safeUnregisterReceiver(this, mUserSelectedSportTypeChangedReceiver, TAG);
                mReceiversRegistered = false;
            } catch (Throwable t) {
                Log.w(TAG, "Error unregistering transient receivers", t);
            }
        }
    }

    protected void acquireWakeLock() {
        if (wakeLock == null) {
            try {
                android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "aTrainingTracker:TrackingLock");
                    wakeLock.acquire();
                    if (DEBUG) Log.d(TAG, "WakeLock acquired");
                }
            } catch (Throwable t) {
                Log.w(TAG, "Error acquiring WakeLock", t);
            }
        }
    }

    protected synchronized void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                if (DEBUG) Log.d(TAG, "WakeLock released");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error releasing WakeLock", t);
        } finally {
            wakeLock = null;
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

        // ATT-7746 (REQ-TRK-076.3, REQ-TRK-076.4): If a workout was in the middle of stopping,
        // or a new session is starting, reset stopping/interrupted flags, increment session generation,
        // re-register transient receivers, and ensure wake lock and BANAL binding are active.
        if (mIsStopping) {
            if (DEBUG) {
                Log.d(TAG, "onStartCommand: new start received while service was stopping; resetting mIsStopping to false (startType=" + startType + ")");
            }
            mIsStopping = false;
        }
        mSessionGeneration.incrementAndGet();
        mTrackingInterrupted = false;
        registerTransientReceivers();
        acquireWakeLock();
        if (!mBanalBound) {
            try {
                bindService(new Intent(this, BANALService.class), mBanalConnection, Context.BIND_AUTO_CREATE);
                mBanalBound = true;
            } catch (Throwable t) {
                Log.w(TAG, "Error rebinding BANALService in onStartCommand", t);
            }
        }
        if (startType == StartType.START_NORMAL || startType == StartType.RESUME_BY_USER || startType == StartType.RESUME_AUTOMATICALLY) {
            BSportType sportType = (mBanalService != null) ? mBanalService.getBSportType() : null;
            if (sportType == null) {
                try {
                    sportType = getSportTypesRepository().getBSportType(getSportTypeId());
                } catch (Throwable ignored) {
                }
            }
            boolean isCycling = (sportType == null || sportType == BSportType.BIKE);
            try {
                RadarLightController.onWorkoutStarted(this, isCycling);
            } catch (Throwable ignored) {
            }

            long sportTypeId = getSportTypeId();
            boolean isIndoor = false;
            if (intent != null && intent.hasExtra(EXTRA_INDOOR)) {
                isIndoor = intent.getBooleanExtra(EXTRA_INDOOR, false);
            } else {
                try {
                    isIndoor = getSportTypesRepository().isIndoorSportType(sportTypeId);
                } catch (Throwable ignored) {
                }
            }
            if (mBanalService != null) {
                mBanalService.setIndoorMode(isIndoor);
            } else {
                BANALService.setStaticIndoorMode(isIndoor);
            }
        }
        switch (startType) {
            case START_NORMAL:
                if (DEBUG) Log.d(TAG, "starting a new workout");
                mDiagnosticCollector.clear();
                mLiveRrQueue.clear();
                mRrBeatIndexCounter.set(0);
                mLastRrBeatTimestampMs.set(0L);
                if (TrainingApplication.isAutoPaused()) {
                    mAutoPauseManager.setState(AutoPauseState.AUTO_PAUSED);
                    mAutoPauseManager.resetDebounceCounter();
                }
                long startTimeMs = System.currentTimeMillis();
                mSessionEpochStartTimeMs = startTimeMs;
                try {
                    PolarH10OfflineController.setSessionStartTimeMs(startTimeMs);
                    PolarH10OfflineController.onWorkoutStarted(this);
                } catch (Throwable ignored) {
                }
                try {
                    PolarVeritySenseOfflineController.setSessionStartTimeMs(startTimeMs);
                    PolarVeritySenseOfflineController.onWorkoutStarted(this);
                } catch (Throwable ignored) {
                }
                // The workout name is just the date+time
                mBaseFileName = (new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)).format(new Date());
                if (BuildConfig.DEBUG) {
                    getDebugHeapWatchdog().startSession(mBaseFileName);
                }
                try {
                    com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryRecorder.startActiveSession(
                            getApplicationContext(),
                            mBaseFileName,
                            startTimeMs,
                            (int) getSportTypeId()
                    );
                } catch (Throwable t) {
                    Log.w(TAG, "Error starting PolarRawTelemetryRecorder active session: " + t.getMessage());
                }
                try {
                    BSportType startSport = getSportTypesRepository().getBSportType(getSportTypeId());
                    com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.startActiveSession(getApplicationContext(), mBaseFileName, getSportTypeId(), startSport != null ? startSport.ordinal() : -1);
                } catch (Throwable t) { Log.w(TAG, "Error starting SmartphoneRawTelemetryRecorder: " + t.getMessage()); }
                mSamplesTableName = WorkoutRepository.getSamplesTableName(mBaseFileName);
                mLiveSession = new LiveWorkoutSession(0, IMPORTANT_SENSOR_TYPES);
                mLiveSession.setSessionEpochStartTimeMs(startTimeMs);
                mMultisportLapCoordinator.resetForSession(startTimeMs, 0, 0, 0.0);

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
                        getWorkoutRepository().createSamplesTable(mBaseFileName, Arrays.asList(SensorType.values()));
                        initFuture.complete(null);
                        notifyTrackingStarted(mWorkoutID);
                    } catch (Throwable t) {
                        Log.e(TAG, "Fatal error initializing workout database asynchronously", t);
                        mDiagnosticCollector.record(DiagnosticStage.INIT, t, "Fatal error initializing workout database asynchronously");
                        if (mWorkoutID > 0) {
                            String errorJson = mDiagnosticCollector.toJson();
                            if (errorJson != null) {
                                try {
                                    getWorkoutRepository().setRecordingErrors(mWorkoutID, errorJson);
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                        initFuture.completeExceptionally(t);
                        mTrackingInterrupted = true;
                        cancelTrackerHandle();
                        runOnMainThread(this::handleTrackingAborted);
                        showTrackingInterruptedNotification();
                        performStopSelf();
                    }
                });
                break;

            case RESUME_BY_USER:
            case RESUME_AUTOMATICALLY:
                Log.d(TAG, "resuming by user request or automatically");
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
                mSessionEpochStartTimeMs = System.currentTimeMillis();
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
                cancelTrackerHandle();
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
            if (mBanalService != null) {
                try {
                    mBanalService.removeRrBeatListener(mRrBeatListener);
                } catch (Throwable t) {
                    Log.w(TAG, "Error removing RR beat listener during unbind", t);
                }
            }
            if (mBanalBound || mBanalService != null) {
                unbindService(mBanalConnection);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error unbinding BANALService", t);
        } finally {
            mBanalBound = false;
        }
    }

    private void performIdempotentMainThreadTeardown(boolean stopSelf) {
        safeUnbindBanalService();
        mBanalService = null;
        releaseWakeLock();
        try {
            performStopForeground();
        } catch (Throwable t) {
            Log.w(TAG, "Error stopping foreground state during teardown", t);
        }
        if (stopSelf) {
            try {
                performStopSelf();
            } catch (Throwable t) {
                Log.w(TAG, "Error calling stopSelf during teardown", t);
            }
        }
    }

    /**
     * Handles graceful asynchronous workout finalization under foreground service priority
     * ({@code REQ-TRK-034.2}, {@code REQ-TRK-034.3}, {@code REQ-TRK-076.1}).
     *
     * <p>1. Cancels active 1 Hz tracker handle to stop periodic sampling ticks immediately.
     * Complete executor shutdown is deferred strictly to {@code onDestroy()} so newly started
     * sessions are never rejected ({@code REQ-TRK-076.1}).
     * <p>2. Retains active foreground service priority and notification so Android's Low Memory Killer
     * does not terminate the process during lengthy database finalization on {@code mDbExecutor}.
     * <p>3. Captures immutable local session snapshots and enqueues {@code finalizationTask} onto
     * the single-threaded FIFO {@code mDbExecutor} ({@code REQ-TRK-076.2}).
     * <p>4. In the completion callback of {@code finalizationTask}, checks {@code mSessionGeneration}
     * and dispatches main-thread teardown only if no new workout session has started ({@code REQ-TRK-076.3}).
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

        // ATT-7746 (REQ-TRK-076.1): Complete executor shutdown is deferred strictly to onDestroy().
        stopTrackingControllersAndTransientState(false);
        enqueueAsyncFinalization(false, mSessionGeneration.get());

        return Service.START_NOT_STICKY;
    }

    /**
     * Stops active sampling, offline sensor controllers, transient state, and broadcast receivers
     * across both {@link #handleActionStop()} and {@link #onDestroy()} ({@code REQ-TRK-094.1}).
     *
     * @param fromOnDestroy {@code true} during service destruction (shuts down scheduler/watchdog
     *                      and releases {@code mRadarAlertManager}, preserving radar simulator across
     *                      pause); {@code false} during explicit {@code ACTION_STOP} (stops radar
     *                      simulator/snooze and watchdog session while keeping executors alive).
     */
    private void stopTrackingControllersAndTransientState(boolean fromOnDestroy) {
        cancelTrackerHandle();
        if (fromOnDestroy) {
            mScheduler.shutdownNow();
            if (BuildConfig.DEBUG && mDebugHeapWatchdog != null) {
                mDebugHeapWatchdog.shutdown();
            }
        } else if (BuildConfig.DEBUG && mDebugHeapWatchdog != null) {
            mDebugHeapWatchdog.stopSession();
        }

        mAutoPauseManager.release();
        mBroadcastTracker.reset();

        if (fromOnDestroy && mRadarAlertManager != null) {
            try {
                mRadarAlertManager.release();
            } catch (Throwable t) {
                Log.w(TAG, "Error releasing RadarAlertManager", t);
            }
            mRadarAlertManager = null;
        }

        RadarLightController.onWorkoutStopped(this);
        try {
            PolarH10OfflineController.onWorkoutStopped(this);
        } catch (Throwable ignored) {
        }
        try {
            PolarVeritySenseOfflineController.onWorkoutStopped(this);
        } catch (Throwable ignored) {
        }
        if (!fromOnDestroy) {
            RadarSnoozeController.reset();
            RadarTrafficSimulator.INSTANCE.stop();
        }

        unregisterTransientReceivers();
    }

    /**
     * Captures immutable finalization inputs and enqueues {@link #endWorkout} onto FIFO
     * {@code mDbExecutor} with main-thread teardown and executor-shutdown policy parameterized
     * between {@link #handleActionStop()} and {@link #onDestroy()} ({@code REQ-TRK-094.1}).
     */
    private void enqueueAsyncFinalization(boolean fromOnDestroy, @Nullable Integer stoppingGeneration) {
        final BanalFinalizationSnapshot snapshot = BanalFinalizationSnapshot.capture(
                mBanalService, this, getAverageSpeed(), averageSpeedCalculateable(), mDistanceTotal_m, mTimeActive_s);
        final boolean interrupted = mTrackingInterrupted;
        final long stoppingWorkoutId = mWorkoutID;
        final String stoppingBaseFileName = mBaseFileName;
        final LiveWorkoutSession stoppingLiveSession = mLiveSession;
        final CompletableFuture<Void> finalizationFuture = new CompletableFuture<>();
        mFinalizationFuture = finalizationFuture;

        Runnable finalizationTask = () -> {
            try {
                if (!interrupted) {
                    endWorkout(snapshot, stoppingWorkoutId, stoppingBaseFileName, stoppingLiveSession);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Unexpected error during async workout finalization in "
                        + (fromOnDestroy ? "onDestroy" : "handleActionStop"), t);
            } finally {
                if (fromOnDestroy) {
                    mRrFlushExecutor.shutdown();
                }
                // ATT-7746 (REQ-TRK-076.3): Teardown checks, unbinding, and stopping execute
                // atomically on the Main Looper thread to prevent races with concurrent onStartCommand.
                runOnMainThread(() -> {
                    try {
                        if (fromOnDestroy) {
                            performIdempotentMainThreadTeardown(false);
                        } else if (stoppingGeneration != null
                                && mSessionGeneration.get() == stoppingGeneration
                                && mIsStopping) {
                            performIdempotentMainThreadTeardown(true);
                        } else if (DEBUG) {
                            Log.d(TAG, "handleActionStop: New session started (generation="
                                    + mSessionGeneration.get() + " vs stopping=" + stoppingGeneration
                                    + ", isStopping=" + mIsStopping + "); suppressing stopSelf and teardown.");
                        }
                    } finally {
                        finalizationFuture.complete(null);
                    }
                });
            }
        };

        try {
            mDbExecutor.execute(finalizationTask);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            Log.w(TAG, "mDbExecutor already shut down"
                    + (fromOnDestroy ? " in onDestroy" : "") + "; running finalizationTask inline", e);
            finalizationTask.run();
        } finally {
            if (fromOnDestroy) {
                mDbExecutor.shutdown();
            }
        }
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
                .setSmallIcon(R.drawable.ic_stat_notify_logo)
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
        mBroadcastTracker.onSessionInitialized(workoutId, TrainingApplication.isPaused());
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

        // Stop 1 Hz sampling, shut down scheduler/watchdog, release main-thread controllers,
        // and unregister receivers while keeping mBanalService & wakeLock valid until finalization finishes.
        stopTrackingControllersAndTransientState(true);

        if (mIsStopping) {
            // ATT-6178 (REQ-TRK-034.4, REQ-TRK-033.2, REQ-TRK-076.1): Shut down executors without
            // blocking the Main Thread on mFinalizationFuture.get(...) or unbinding BANALService
            // while endWorkout() is still executing; the posted Main-Thread cleanup Runnable owns
            // idempotent teardown once endWorkout() finishes.
            mRrFlushExecutor.shutdown();
            mDbExecutor.shutdown();
            CompletableFuture<Void> future = mFinalizationFuture;
            if (future == null || future.isDone()) {
                safeUnbindBanalService();
                mBanalService = null;
                releaseWakeLock();
            }
            return;
        }

        enqueueAsyncFinalization(true, null);
    }

    @VisibleForTesting
    public static final class ResumedWorkoutState {
        public final String baseFileName;
        public final long workoutId;
        public final int timeTotal_s;
        public final int timeActive_s;
        public final int calories;
        public final int lapNr;
        public final double distanceTotal_m;
        public final double ascent;
        public final double descent;
        public final int timeLap_s;
        public final double distanceLap_m;
        public final double lastAltitudeM;

        public ResumedWorkoutState(
                @NonNull String baseFileName, long workoutId, int timeTotal_s, int timeActive_s,
                int calories, int lapNr, double distanceTotal_m, double ascent, double descent,
                int timeLap_s, double distanceLap_m, double lastAltitudeM) {
            this.baseFileName = baseFileName; this.workoutId = workoutId;
            this.timeTotal_s = timeTotal_s; this.timeActive_s = timeActive_s;
            this.calories = calories; this.lapNr = lapNr;
            this.distanceTotal_m = distanceTotal_m; this.ascent = ascent; this.descent = descent;
            this.timeLap_s = timeLap_s; this.distanceLap_m = distanceLap_m;
            this.lastAltitudeM = lastAltitudeM;
        }

        public ResumedWorkoutState(
                @NonNull String baseFileName, long workoutId, int timeTotal_s, int timeActive_s,
                int calories, int lapNr, double distanceTotal_m, double ascent, double descent,
                int timeLap_s, double distanceLap_m) {
            this(baseFileName, workoutId, timeTotal_s, timeActive_s, calories, lapNr, distanceTotal_m, ascent, descent, timeLap_s, distanceLap_m, Double.NaN);
        }

        public ResumedWorkoutState(
                @NonNull String baseFileName, long workoutId, int timeTotal_s, int timeActive_s,
                int calories, int lapNr, double distanceTotal_m) {
            this(baseFileName, workoutId, timeTotal_s, timeActive_s, calories, lapNr, distanceTotal_m, 0.0, 0.0, timeActive_s, distanceTotal_m, Double.NaN);
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
            return WorkoutRepository.getSamplesTableName(baseFileName);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Cannot resolve samples table name for invalid baseFileName: " + baseFileName, e);
            return null;
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    void recreateValuesWhenResuming() {
        ResumedWorkoutState state = null;
        try {
            state = getWorkoutRepository().getResumedWorkoutState();
            if (state == null) {
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
        try {
            int maxBeat = getWorkoutRrRepository().getMaxBeatIndexBlocking(mWorkoutID);
            mRrBeatIndexCounter.set(maxBeat >= 0 ? maxBeat + 1 : 0);
        } catch (Throwable t) {
            Log.e(TAG, "Error restoring RR beat index counter on resume for workout " + mWorkoutID, t);
        }

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
                + ", distance_m=" + mDistanceTotal_m
                + ", ascent=" + state.ascent
                + ", descent=" + state.descent
                + ", timeLap_s=" + state.timeLap_s
                + ", distanceLap_m=" + state.distanceLap_m);

        BANALService.setInitialSensorValue(SensorType.TIME_TOTAL, mTimeTotal_s);
        BANALService.setInitialSensorValue(SensorType.TIME_ACTIVE, mTimeActive_s);
        BANALService.setInitialSensorValue(SensorType.CALORIES, state.calories);
        BANALService.setInitialSensorValue(SensorType.LAP_NR, state.lapNr);
        BANALService.setInitialSensorValue(SensorType.DISTANCE_m, mDistanceTotal_m);
        BANALService.setInitialSensorValue(SensorType.ASCENT, state.ascent);
        BANALService.setInitialSensorValue(SensorType.DESCENT, state.descent);
        BANALService.setInitialSensorValue(SensorType.TIME_LAP, state.timeLap_s);
        BANALService.setInitialSensorValue(SensorType.DISTANCE_m_LAP, state.distanceLap_m);
        if (Double.isFinite(state.lastAltitudeM)) {
            BANALService.reanchorBarometricAltitudeOnResume(state.lastAltitudeM);
        }
        try {
            long resumedStartEpochMs = Math.max(0L, System.currentTimeMillis() - mTimeTotal_s * 1000L);
            com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryRecorder.resumeOrStartActiveSession(
                    getApplicationContext(), mBaseFileName, resumedStartEpochMs, (int) getSportTypeId());
        } catch (Throwable t) {
            Log.w(TAG, "Error resuming PolarRawTelemetryRecorder active session: " + t.getMessage());
        }
        try {
            BSportType resumedSport = getSportTypesRepository().getBSportType(getSportTypeId());
            com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.resumeOrStartActiveSession(getApplicationContext(), mBaseFileName, getSportTypeId(), resumedSport != null ? resumedSport.ordinal() : -1);
        } catch (Throwable t) { Log.w(TAG, "Error resuming SmartphoneRawTelemetryRecorder: " + t.getMessage()); }

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

        mMultisportLapCoordinator.resetForSession(Math.max(0L, System.currentTimeMillis() - state.timeLap_s * 1000L), Math.max(0, mTimeActive_s - state.timeLap_s), Math.max(0, mTimeTotal_s - state.timeLap_s), Math.max(0.0, mDistanceTotal_m - state.distanceLap_m));

        if (mSessionEpochStartTimeMs <= 0L) {
            mSessionEpochStartTimeMs = System.currentTimeMillis();
        }
        final long epochStartMs = mSessionEpochStartTimeMs;
        // ATT-4095 (REQ-TRK-036.2): Construct, rehydrate, and publish candidate session on mDbExecutor
        if (mTableInitializationFuture == null || mTableInitializationFuture.isDone()) {
            mTableInitializationFuture = new CompletableFuture<>();
        }
        mIsRehydrating = true;
        Runnable rehydrateTask = () -> {
            try {
                LiveWorkoutSession newSession = new LiveWorkoutSession(resumedWorkoutId, IMPORTANT_SENSOR_TYPES);
                rehydrateLiveSession(newSession, resumedWorkoutId, resumedActiveTimeS);
                newSession.setSessionEpochStartTimeMs(epochStartMs);
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
            WorkoutRepository workoutRepository = getWorkoutRepository();
            if (workoutRepository == null) {
                return;
            }

            // 1. Rehydrate encoded streams with independent per-stream fault isolation
            WorkoutSummariesDatabaseManager.EncodedStreams encodedStreams = workoutRepository.getEncodedStreams(workoutId);
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
                    workoutRepository.getExtremaForWorkouts(Collections.singletonList(workoutId));
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
                session.recordObservedSensorTypes(sensorsWithExtrema);
            }

            // Rehydrate previously persisted AccumulatedSensors entries so resumed workouts retain prior sensors (REQ-TRK-084.3)
            try {
                Set<SensorType> persistedObserved = workoutRepository.getAccumulatedSensorTypes(workoutId);
                if (persistedObserved != null && !persistedObserved.isEmpty()) {
                    session.recordObservedSensorTypes(persistedObserved);
                }
            } catch (Throwable t) {
                Log.w(TAG, "rehydrateLiveSession: Could not rehydrate accumulated sensor types for workoutId=" + workoutId, t);
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
        createNewLap(snapshot, mWorkoutID);
    }

    protected void createNewLap(@Nullable BanalFinalizationSnapshot snapshot, long workoutId) {
        if (DEBUG) Log.i(TAG, "createNewLap for workout " + workoutId);

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

            LapTelemetrySnapshot lap = LapTelemetryExtractor.extract(
                    banalService.getBestSensorData(SensorType.LAP_NR),
                    banalService.getBestSensorData(SensorType.TIME_LAP),
                    banalService.getBestSensorData(SensorType.DISTANCE_m_LAP),
                    false);
            prevLapNr = lap.lapNr;
            lapTime_s = lap.lapTimeSeconds;
            lapDistance = lap.lapDistanceMeters;
        }

        // ATT-896 / REQ-TRK-002: Guard against saving zero-duration / zero-distance phantom laps
        if (lapTime_s <= 0 && lapDistance <= 0.0) {
            if (DEBUG) Log.i(TAG, "Discarding zero-duration/zero-distance lap in createNewLap");
            return;
        }

        double lapSpeed = (lapTime_s > 0) ? (lapDistance / lapTime_s) : 0.0;
        mMultisportLapCoordinator.saveFinalLap(getWorkoutRepository(), workoutId, prevLapNr, lapTime_s, lapDistance, lapSpeed,
                (wId, nr, t, d, s) -> { saveLap(wId, nr, t, d, s); return kotlin.Unit.INSTANCE; });
    }

    protected void saveLap(long lapNr, int lapTime, double lapDistance, double averageSpeed) {
        saveLap(mWorkoutID, lapNr, lapTime, lapDistance, averageSpeed);
        if (!mIsStopping) {
            mBroadcastTracker.onDiscreteEdge(mWorkoutID);
        }
    }

    protected void saveLap(long workoutId, long lapNr, int lapTime, double lapDistance, double averageSpeed) {
        if (DEBUG)
            Log.i(TAG, "saveLap: workoutId=" + workoutId + ", lapNr=" + lapNr + ", lapTime=" + lapTime + ", lapDistance=" + lapDistance + ", averageSpeed=" + averageSpeed);

        String timeStart = mMultisportLapCoordinator.consumeLapStartTimeUtc(lapTime); // REQ-TRK-090
        getWorkoutRepository().saveLap(workoutId, lapNr, timeStart, lapTime, lapDistance, averageSpeed, null, null);
    }

    /**
     *  get the 'best' sport type based on the available equipment and average speed.
     */
    protected long getSportTypeId() {
        final BANALServiceComm banalService = mBanalService;
        if (banalService != null) {
            EquipmentAndSportTypeDiscoveryManager discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(this);
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

        return SportTypesRepository.getDefaultSportTypeId();
    }


    // not necessary when resuming an already started workout
    protected long createNewWorkout() {

        if (DEBUG) Log.d(TAG, "createNewWorkout");

        long sportTypeId = getSportTypeId();
        BSportType bSport = getSportTypesRepository().getBSportType(sportTypeId);
        try { com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.updateSportState(sportTypeId, bSport != null ? bSport.ordinal() : -1); } catch (Throwable t) { Log.w(TAG, "Error updating SmartphoneRawTelemetryRecorder sport state: " + t.getMessage()); }
        boolean isIndoor = false;
        try {
            isIndoor = getSportTypesRepository().isIndoorSportType(sportTypeId);
        } catch (Throwable ignored) {
        }
        long workoutId = getWorkoutRepository().createNewTrackedWorkout(mBaseFileName, sportTypeId, bSport, isIndoor);

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
        BSportType bSport = getSportTypesRepository().getBSportType(sportTypeId);
        try { com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.updateSportState(sportTypeId, bSport != null ? bSport.ordinal() : -1); } catch (Throwable t) { Log.w(TAG, "Error updating SmartphoneRawTelemetryRecorder sport state: " + t.getMessage()); }
        String gcData = banalService.getGCDataString();

        mTableInitializationFuture.thenAcceptAsync(v -> {
            try {
                getWorkoutRepository().updateWorkoutSportAndGcData(mWorkoutID, sportTypeId, bSport, gcData);
                if (!mIsStopping && mWorkoutID > 0) {
                    mBroadcastTracker.onDiscreteEdge(mWorkoutID);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update summaries DB on searching finished", e);
            }
        }, mDbExecutor);
    }

    private void onUserSelectedSportTypeChanged() {
        long sportTypeId = getSportTypeId();
        BSportType bSport = getSportTypesRepository().getBSportType(sportTypeId);
        try { com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.updateSportState(sportTypeId, bSport != null ? bSport.ordinal() : -1); } catch (Throwable t) { Log.w(TAG, "Error updating SmartphoneRawTelemetryRecorder sport state: " + t.getMessage()); }
        boolean isIndoor = false;
        try {
            isIndoor = getSportTypesRepository().isIndoorSportType(sportTypeId);
        } catch (Throwable ignored) {
        }
        if (mBanalService != null) {
            mBanalService.setIndoorMode(isIndoor);
        } else {
            BANALService.setStaticIndoorMode(isIndoor);
        }

        if (bSport == BSportType.BIKE) {
            RadarLightController.onWorkoutStarted(this, true);
        } else {
            RadarLightController.onWorkoutStopped(this);
        }

        final boolean finalIsIndoor = isIndoor;
        mTableInitializationFuture.thenAcceptAsync(v -> {
            try {
                getWorkoutRepository().updateWorkoutSport(mWorkoutID, sportTypeId, bSport, finalIsIndoor);
                if (!mIsStopping && mWorkoutID > 0) {
                    mBroadcastTracker.onDiscreteEdge(mWorkoutID);
                }
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
        endWorkout(null, mWorkoutID, mBaseFileName, mLiveSession);
    }

    public void endWorkout(@Nullable BanalFinalizationSnapshot snapshot) {
        endWorkout(snapshot, mWorkoutID, mBaseFileName, mLiveSession);
    }

    public void endWorkout(
            @Nullable BanalFinalizationSnapshot snapshot,
            long workoutId,
            @Nullable String baseFileName,
            @Nullable LiveWorkoutSession liveSession) {
        if (DEBUG) {
            Log.d(TAG, "endWorkout: workoutId=" + workoutId);
        }

        try {
            createNewLap(snapshot, workoutId);
        } catch (Throwable t) {
            Log.e(TAG, "Error in createNewLap during endWorkout", t);
        }

        final BANALServiceComm banalService = mBanalService;

        // store the ANT and BTLE Devices that were active during the workout
        if (DEBUG) Log.d(TAG, "storing active device list");
        try {
            Set<Long> activeDevices = new HashSet<>();
            if (snapshot != null && !snapshot.activeRemoteDeviceIds.isEmpty()) {
                activeDevices.addAll(snapshot.activeRemoteDeviceIds);
            } else if (banalService != null) {
                activeDevices.addAll(banalService.getDatabaseIdsOfActiveRemoteDevices());
            } else {
                activeDevices.addAll(mActiveSessionDeviceIds);
            }
            for (Long deviceDbId : activeDevices) {
                if (deviceDbId != null && deviceDbId >= 0) {
                    getDeviceDataRepository().recordActiveDevice(workoutId, deviceDbId);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error saving active devices during endWorkout", t);
        }

        // Persist remaining raw beat-to-beat RR intervals (REQ-PER-034.1, REQ-PER-034.2)
        try {
            if (workoutId > 0) {
                flushLiveRrQueueSync(workoutId);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error persisting workout RR intervals during endWorkout", t);
        }

        try {
            // 1. Critical state transition: commit FINISHED = 1 first in its own dedicated, minimal transaction (REQ-TRK-030.1)
            try {
                getWorkoutRepository().markWorkoutFinishedBlocking(workoutId);
            } catch (Throwable t) {
                Log.e(TAG, "Error setting FINISHED=1 in summaries table during endWorkout", t);
            }

            // 2. Save the accumulated SensorTypes in an isolated block so failures cannot roll back FINISHED = 1
            // REQ-TRK-084.1: Persist live-observed non-null SensorTypes when liveSession is active;
            // retain snapshot/BANAL fallback when liveSession == null (REQ-TRK-035.3, TST-TRK-027.3).
            Set<SensorType> accumulatedSensors = (liveSession != null)
                    ? liveSession.getObservedSensorTypes()
                    : ((snapshot != null && !snapshot.accumulatedSensorTypes.isEmpty())
                            ? snapshot.accumulatedSensorTypes
                            : (banalService != null ? banalService.getAccumulatedSensorTypeSet() : null));
            if (accumulatedSensors != null) {
                try {
                    getWorkoutRepository().saveAccumulatedSensorTypes(workoutId, accumulatedSensors);
                } catch (Throwable t) {
                    Log.e(TAG, "Error saving accumulated sensor types during endWorkout", t);
                }
            }

            // 3. Update auxiliary summary metadata (GC_DATA, SPORT_ID, B_SPORT) in an isolated block
            try {
                String gcData = (snapshot != null && snapshot.accumulatedGCDataString != null)
                        ? snapshot.accumulatedGCDataString
                        : (banalService != null ? banalService.getAccumulatedGCDataString() : null);

                long sportTypeId = (snapshot != null)
                        ? snapshot.sportTypeId
                        : getSportTypeId();
                BSportType bSportType = (snapshot != null && snapshot.bSportType != BSportType.UNKNOWN)
                        ? snapshot.bSportType
                        : getSportTypesRepository().getBSportType(sportTypeId);

                getWorkoutRepository().updateWorkoutAuxiliaryMetadata(workoutId, gcData, sportTypeId, bSportType);
            } catch (Throwable t) {
                Log.e(TAG, "Error updating auxiliary summary metadata during endWorkout", t);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error accessing WorkoutRepository during endWorkout", t);
        }

        final boolean h10Armed = PolarH10OfflineController.isRecordingArmed();
        mDeferHrPhysiologyToPostProcessing = h10Armed;
        try {
            // Finalize Live Session (Auto Name, Commute, Trainer)
            if (liveSession != null) {
                finalizeLiveSession(snapshot, workoutId, baseFileName, liveSession);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error finalizing live session during endWorkout", t);
        } finally {
            mDeferHrPhysiologyToPostProcessing = false;
        }

        try {
            com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryRecorder.stopActiveSession();
        } catch (Throwable t) {
            Log.w(TAG, "Error stopping PolarRawTelemetryRecorder active session: " + t.getMessage());
        }
        try {
            com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.stopActiveSession();
        } catch (Throwable t) { Log.w(TAG, "Error stopping SmartphoneRawTelemetryRecorder: " + t.getMessage()); }
        try {
            com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryPruner.pruneExpiredSidecarsAsync(getApplicationContext());
        } catch (Throwable t) {
            Log.w(TAG, "Error triggering background raw telemetry pruner: " + t.getMessage());
        }

        try {
            // ATT-9154 (REQ-TRK-091.1) & ATT-8124 (REQ-TRK-093): Capture armed-state snapshot once and enqueue
            // Polar backfill, post-backfill physiology/HRV/ERTS recompute, and export asynchronously (< 1 s).
            final Context appContext = getApplicationContext();
            if (workoutId > 0 || (baseFileName != null && !baseFileName.trim().isEmpty())) {
                long h10StartMs = h10Armed ? PolarH10OfflineController.getEffectiveAnchorStartTimeMs() : 0L;
                long h10Token = h10Armed ? PolarH10OfflineController.getArmedSessionToken() : 0L;
                boolean verityArmed = PolarVeritySenseOfflineController.isRecordingArmed();
                long verityStartMs = verityArmed ? PolarVeritySenseOfflineController.getSessionStartTimeMs() : 0L;
                long verityToken = verityArmed ? PolarVeritySenseOfflineController.getArmedSessionToken() : 0L;
                boolean ertsEnabled = SettingsDataStoreJavaHelper.isPostWorkoutErtsSmoothingEnabled(appContext);
                WorkoutPostProcessingCoordinator.PostProcessingRequest request =
                        new WorkoutPostProcessingCoordinator.PostProcessingRequest(
                                workoutId, baseFileName, h10Armed, h10StartMs, h10Token,
                                verityArmed, verityStartMs, verityToken, ertsEnabled, System.currentTimeMillis());
                WorkoutPostProcessingCoordinator.enqueue(appContext, request);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error enqueueing async post-workout processing for workout " + workoutId, t);
        } finally {
            try {
                // 1. Broadcast TRACKING_FINISHED_INTENT with WORKOUT_ID at system level
                Intent finishedIntent = new Intent(TRACKING_FINISHED_INTENT)
                        .setPackage(getPackageName())
                        .putExtra(WORKOUT_ID, workoutId);
                if (mFirstJubileeCelebrationData != null) {
                    finishedIntent.putExtra(MilestoneAutoNamingManager.EXTRA_IS_FIRST_JUBILEE, true);
                    finishedIntent.putExtra(MilestoneAutoNamingManager.EXTRA_MILESTONE_COUNT, mFirstJubileeCelebrationData.getCount());
                    finishedIntent.putExtra(MilestoneAutoNamingManager.EXTRA_MILESTONE_SPORT_NAME, mFirstJubileeCelebrationData.getSportName());
                }
                sendBroadcast(finishedIntent);

                // 2. Broadcast TRACKING_FINISHED_INTENT via LocalBroadcastManager
                // for internal components (WorkoutRepository, etc.) to trigger reactive period & cluster updates (REQ-TRK-010, ATT-505)
                Intent localFinishedIntent = new Intent(TRACKING_FINISHED_INTENT)
                        .setPackage(getPackageName())
                        .putExtra(WORKOUT_ID, workoutId);
                if (mFirstJubileeCelebrationData != null) {
                    localFinishedIntent.putExtra(MilestoneAutoNamingManager.EXTRA_IS_FIRST_JUBILEE, true);
                    localFinishedIntent.putExtra(MilestoneAutoNamingManager.EXTRA_MILESTONE_COUNT, mFirstJubileeCelebrationData.getCount());
                    localFinishedIntent.putExtra(MilestoneAutoNamingManager.EXTRA_MILESTONE_SPORT_NAME, mFirstJubileeCelebrationData.getSportName());
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(localFinishedIntent);
            } catch (Throwable t) {
                Log.w(TAG, "Error broadcasting tracking finished intents during endWorkout", t);
            } finally {
                mFirstJubileeCelebrationData = null;
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
                        getDeviceDataRepository().recordActiveDevice(mWorkoutID, deviceDbId);
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
                try {
                    getWorkoutRepository().updateWorkoutTotalTime(workoutId, timeTotal);
                } catch (Exception e) {
                    Log.e(TAG, "Error updating total time during pause", e);
                }
                mBroadcastTracker.onPauseStateTick(workoutId, true);
            }, mDbExecutor);
            return;
        }

        boolean isCycling = (banalService.getBSportType() == null || banalService.getBSportType() == BSportType.BIKE);
        if (isCycling && (RadarLightController.isLightControlAvailable().getValue() || RadarTrafficSimulator.INSTANCE.isSimulating())) {
            SensorData<Number> speedData = banalService.getBestSensorData(SensorType.SPEED_mps);
            if (speedData != null && speedData.getValue() != null) {
                SmartBrakeLightDetector.onSpeedChanged(speedData.getValue().floatValue());
            }
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
        if (currentPos != null) {
            com.atrainingtracker.banalservice.devices.radar.SmartRadarLightEngine.onLocationAndTimeChanged(
                    currentPos.latitude,
                    currentPos.longitude,
                    System.currentTimeMillis()
            );
        }

        // ATT-1447 / REQ-DAT-015: Record workout start location atomically on first valid GPS fix
        if (mStartType == StartType.START_NORMAL && !mWorkoutStartLocationRecorded && currentPos != null) {
            mWorkoutStartLocationRecorded = true;
            final LatLng startPos = currentPos;
            SensorData<Number> altData = banalService.getBestSensorData(SensorType.ALTITUDE);
            final Double startAlt = (altData != null && altData.getValue() != null) ? altData.getValue().doubleValue() : null;
            mDbExecutor.execute(() -> {
                getKnownLocationsRepository().recordWorkoutStart(startPos, startAlt);
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
                        extremaUpdates.add(new ExtremaUpdate(sensorType, ExtremaType.MIN, stats.min, stats.minPos));
                    }
                    if ((changed & LiveWorkoutSession.RunningStats.CHANGED_MAX) != 0) {
                        extremaUpdates.add(new ExtremaUpdate(sensorType, ExtremaType.MAX, stats.max, stats.maxPos));
                    }
                    if ((changed & LiveWorkoutSession.RunningStats.CHANGED_AVG) != 0) {
                        // For Speed, we defer the average update until after the loop to use the 
                        // authoritative distance/time calculation (SCRUM-108)
                        if (sensorType != SensorType.SPEED_mps && updateAvgInDb) {
                            extremaUpdates.add(new ExtremaUpdate(sensorType, ExtremaType.AVG, stats.getAverage(), null, stats.getCount()));
                        }
                    }
                }
            }
        }

        // 2. Capture multi-sensor telemetry for raw samples database and session accumulators
        List<SensorData> allSensorDataList = banalService.getAllSensorData();
        Set<SensorType> canonicalSensorTypes = new HashSet<>();
        if (allSensorDataList != null) {
            for (SensorData<?> data : allSensorDataList) {
                if (data != null && data.getValue() != null && data.getDeviceName() == null) {
                    canonicalSensorTypes.add(data.getSensorType());
                }
            }
        }

        if (allSensorDataList != null) {
            for (SensorData sensorDataObj : allSensorDataList) {
                SensorData<Number> sensorData = (SensorData<Number>) sensorDataObj;
                if (sensorData == null || sensorData.getValue() == null) continue;

                SensorType sensorType = sensorData.getSensorType();
                if (mLiveSession != null && !mIsRehydrating
                        && sensorType != SensorType.SENSORS
                        && sensorType != SensorType.ACCUMULATED_SENSORS) {
                    mLiveSession.recordObservedSensorType(sensorType);
                }

                String sensorName = sensorType.name();
                String deviceName = sensorData.getDeviceName();
                if (deviceName != null) {
                    if (deviceName.equals("gps") || deviceName.equals("network") || deviceName.equals("google_fused") || deviceName.equals("att_fused")) {
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

                // REQ-TRK-041.2: Restrict session totals and summary updates to canonical proxy sensor (deviceName == null),
                // while permitting fallback if telemetry provides only a single named source for a sensor type
                boolean isCanonical = (deviceName == null) || !canonicalSensorTypes.contains(sensorType);
                if (isCanonical) {
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
                    canonicalSensorTypes.add(sensorType);
                }
            }
        }

        if (averageSpeedCalculateable()) {
            double authoritativeAvgSpeed = getAverageSpeed();
            summaryValues.put(WorkoutSummaries.SPEED_AVERAGE_mps, authoritativeAvgSpeed);

            // Harmonize with Speed sensor average in Extrema Table (SCRUM-108)
            // We do this every sample to ensure UI consistency while tracking.
            if (mLiveSession != null && !mIsRehydrating && mLiveSession.getSensorStats().containsKey(SensorType.SPEED_mps) && updateAvgInDb) {
                LiveWorkoutSession.RunningStats speedStats = mLiveSession.getSensorStats().get(SensorType.SPEED_mps);
                Integer speedCount = speedStats != null ? speedStats.getCount() : null;
                extremaUpdates.add(new ExtremaUpdate(SensorType.SPEED_mps, ExtremaType.AVG, authoritativeAvgSpeed, null, speedCount));
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

            boolean sampleInserted = getWorkoutRepository().recordLiveSample(
                    mBaseFileName,
                    workoutId,
                    samplingValues,
                    summaryValues,
                    extremaUpdates,
                    streamIncrement,
                    finalSensorName2Type,
                    (msg, ex) -> logSampleInsertFailure(msg, ex)
            );

            if (!sampleInserted) {
                int failureCount = mSampleInsertFailureCount.get();
                if (failureCount == 0 || shouldLogSampleInsertFailure(failureCount)) {
                    Log.w(TAG, "Sample insert failed for table " + tableName + " (sampleInsertFailureCount=" + failureCount + "); proceeding with decoupled summary update");
                }
            }

            mBroadcastTracker.onPauseStateTick(workoutId, false);
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
     * Retrieves the set of all column names currently present in the specified table
     * using {@code PRAGMA table_info}, bypassing Android SQLite prepared statement cache staleness
     * on Android <= 14 (API <= 34) ({@code REQ-TRK-051.1}, {@code ATT-4968}).
     *
     * <p>Ensures every {@link Cursor} is wrapped in a try-with-resources block for leak safety.
     *
     * @param db the database to inspect
     * @param tableName the table name to inspect
     * @return a case-insensitive {@link Set} of column names present in the table
     */
    @VisibleForTesting
    public static Set<String> getTableColumnNames(@NonNull SQLiteDatabase db, @NonNull String tableName) {
        return SqliteSchemaUtils.getTableColumnNames(db, tableName);
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
        boolean shouldSync = mDiagnosticCollector.record(DiagnosticStage.SAMPLE_WRITE, cause, message);
        if (shouldSync && mWorkoutID > 0) {
            String errorJson = mDiagnosticCollector.toJson();
            if (errorJson != null) {
                try {
                    getWorkoutRepository().updateRecordingErrors(mWorkoutID, errorJson);
                } catch (Throwable ignored) {
                }
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
                    getWorkoutRepository().createSamplesTable(mBaseFileName, Arrays.asList(SensorType.values()));
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

        // Implementation of the ALTER TABLE logic using PRAGMA table_info to avoid
        // SQLiteConnection.mPreparedStatementCache staleness on Android <= 14 (REQ-TRK-051.1 / ATT-4968).
        Set<String> existingColumns = getTableColumnNames(samplesDb, tableName);
        if (existingColumns.isEmpty()) {
            // Fallback: If pragma returned no columns, attempt cursor query inspection
            try (Cursor cursor = samplesDb.query(tableName, null, null, null, null, null, null, "1")) {
                if (cursor != null) {
                    String[] cols = cursor.getColumnNames();
                    if (cols != null) {
                        for (String col : cols) {
                            existingColumns.add(col);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        try {
            for (String key : values.keySet()) {
                String queryKey = unquoteSqlColumnIdentifier(key);
                if (!existingColumns.contains(queryKey)) {
                    SensorValueType valueType = name2Type != null ? name2Type.get(key) : null;
                    String type = valueType == null ? "text" : switch (valueType) {
                        case INTEGER -> "int";
                        case DOUBLE -> "double";
                        default -> "text";
                    };
                    String quotedColumn = quoteSqlColumnIdentifier(queryKey);
                    samplesDb.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + quotedColumn + " " + type + " null;");
                    existingColumns.add(queryKey);
                }
            }
            samplesDb.insertOrThrow(tableName, null, values);
            return true;
        } catch (Exception e) {
            logSampleInsertFailure("Failed to fix schema or insert after schema fix for table " + tableName, e);
            return false;
        }
    }

    public static class ExtremaUpdate {
        public final SensorType type;
        public final ExtremaType extrema;
        public final double value;
        public final LatLng pos;
        @Nullable
        public final Integer count;

        public ExtremaUpdate(SensorType type, ExtremaType extrema, double value, LatLng pos) {
            this(type, extrema, value, pos, null);
        }

        public ExtremaUpdate(SensorType type, ExtremaType extrema, double value, LatLng pos, @Nullable Integer count) {
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
        finalizeLiveSession(snapshot, mWorkoutID, mBaseFileName, mLiveSession);
    }

    @VisibleForTesting
    protected void finalizeLiveSession(
            @Nullable BanalFinalizationSnapshot snapshot,
            long workoutId,
            @Nullable String baseFileName,
            @Nullable LiveWorkoutSession liveSession) {
        if (liveSession == null) return;
        final double effectiveAvgSpeed = (snapshot != null) ? snapshot.avgSpeed : getAverageSpeed();
        final boolean effectiveAvgSpeedCalculable = (snapshot != null) ? snapshot.avgSpeedCalculable : averageSpeedCalculateable();
        final double effectiveDistanceTotal_m = (snapshot != null) ? snapshot.totalDistance_m : mDistanceTotal_m;
        final int effectiveTimeActive_s = (snapshot != null) ? snapshot.timeActive_s : mTimeActive_s;

        PostWorkoutFinalizer.FinalizationInput input = new PostWorkoutFinalizer.FinalizationInput(
                this,
                getWorkoutRepository(),
                this::getKnownLocationsRepository,
                mBanalService,
                mDiagnosticCollector,
                snapshot,
                workoutId,
                baseFileName,
                liveSession,
                effectiveAvgSpeed,
                effectiveAvgSpeedCalculable,
                effectiveDistanceTotal_m,
                effectiveTimeActive_s,
                mDeferHrPhysiologyToPostProcessing,
                () -> new HashSet<>(mActiveSessionDeviceIds),
                this::getSportTypeId,
                IMPORTANT_SENSOR_TYPES,
                wId -> {
                    guessCommuteAndTrainer(wId);
                    return kotlin.Unit.INSTANCE;
                },
                DEBUG
        );
        mFirstJubileeCelebrationData = PostWorkoutFinalizer.finalizeLiveSession(input);
    }

    @VisibleForTesting
    protected void guessCommuteAndTrainer() {
        guessCommuteAndTrainer(mWorkoutID);
    }

    @VisibleForTesting
    protected void guessCommuteAndTrainer(long workoutId) {
        PostWorkoutFinalizer.guessCommuteAndTrainer(getWorkoutRepository(), workoutId, DEBUG);
    }


    public enum StartType {START_NORMAL, RESUME_BY_USER, RESUME_AUTOMATICALLY, RESUME_SERVICE_RECREATION}

}
