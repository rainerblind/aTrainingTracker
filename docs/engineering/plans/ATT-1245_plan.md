# Architecture & Implementation Plan: Asynchronous Workout Initialization & Main-Thread ANR Immunity in TrackerService (ATT-1245 / Gate 3)

## 1. Context & Objectives
Firebase Crashlytics issue `26191e1ecfe7f1a95a7b8980ba4108cf` (Session `6AAE3DBE0098000160953499E9062ECA_DNE_0_v2`) identified a fatal Application Not Responding (ANR) error in production during workout initialization.

### Root Cause Analysis (RCA) Summary
In `TrackerService.java:408-415`, `onStartCommand(START_NORMAL)` executed `createNewWorkout()` and `WorkoutSamplesDatabaseManager.createNewTable(mBaseFileName, ...)` synchronously on the Android main UI thread. In SQLite, `createNewTable()` executes `DROP TABLE IF EXISTS` and `CREATE TABLE ...`, modifying the `sqlite_master` schema catalog. Under slow flash storage, large databases with hundreds of tables, or concurrent disk I/O, schema page traversals (`pread`, `unixRead`, `checkTreePage`) blocked the main thread far exceeding the Android looper watchdog limit (> 5 seconds), triggering an ANR.

### Core Objectives
1. Eliminate all synchronous SQLite DDL execution from the main UI thread during `onStartCommand()`.
2. Guarantee sub-millisecond (< 10ms) `onStartCommand()` return latency on the main thread, satisfying Android 14+ Foreground Service startup SLAs.
3. Enforce non-blocking reactive barrier synchronization via `CompletableFuture<Void>.thenAcceptAsync(..., mDbExecutor)` to eliminate thread pool starvation while guaranteeing zero sample loss, FIFO ordering, and prevention of `SQLiteException: no such table`.
4. Defer `notifyTrackingStarted(workoutId)` broadcast until table creation is fully committed to disk so UI observers never query an uninitialized table or receive invalid identifiers.
5. Provide defensive error recovery and deterministic cleanup sequence if database initialization encounters a fatal `SQLException`.

---

## 2. Requirement & Test Specification Traceability Matrix

| Requirement Clause | Clause Description | Test Specification (TST-STB-009) | Planned Test Assertion (`TrackerServiceAsyncInitTest.kt`) |
| :--- | :--- | :--- | :--- |
| **REQ-STB-009.1** | *Main-Thread Database Decoupling*: `onStartCommand` SHALL NOT execute synchronous DDL or SQLite disk page reads on the main thread. | TST-STB-009.1 (Main-Thread Execution Latency Verification) | `testOnStartCommand_returnsImmediatelyWithoutBlockingMainThread()`: Assert `onStartCommand` execution latency < 10ms and returns `START_STICKY`. |
| **REQ-STB-009.2** | *Asynchronous Executor Dispatch*: `createNewWorkout()`, `createNewTable()`, and initial `notifyTrackingStarted()` dispatched to `mDbExecutor`. | TST-STB-009.2 (Asynchronous Table Commitment & CompletableFuture Verification) | `testAsyncWorkoutCreation_dispatchesToDbExecutorAndCompletesFuture()`: Assert `createNewWorkout` and `createNewTable` run on background thread and future completes. |
| **REQ-STB-009.3** | *CompletableFuture Synchronization & Sample Buffering*: Reactive chaining buffers tasks without thread starvation; zero sample loss. | TST-STB-009.3 (Zero Sample Loss / Buffer Verification) | `testSampleWrites_queuedBeforeTableCreation_bufferedAndPersistedWithoutLoss()`: Assert early sensor samples submitted during schema DDL execute successfully post-completion with zero dropped ticks. |
| **REQ-STB-009.4** | *Broadcast Delay until Schema Commitment*: `notifyTrackingStarted(workoutId)` dispatched only after schema commits to disk. | TST-STB-009.4 (Broadcast Deferral Verification) | `testTrackingStartedBroadcast_deferredUntilTableCreationCompletes()`: Assert `TRACKING_STARTED_INTENT` is emitted with valid non-zero ID only after future completes. |
| **REQ-STB-009.5** | *Defensive Downstream Error Handling*: Fatal `SQLException` completes future exceptionally, cancels ticker, alerts user, and stops service. | TST-STB-009.5 (Defensive Error Recovery & Graceful Teardown) | `testTableCreationFailure_triggersDefensiveTeardownAndNotifiesUser()`: Assert unhandled `SQLException` triggers `performStopSelf()`, cancels `mTrackerHandle`, and shows notification without crashing. |
| **REQ-STB-009.6** | *Foreground Service Startup SLA & WakeLock Safety*: `performStartForeground()` runs immediately on main thread within Android 14+ window. | TST-STB-009.1 (Main-Thread Execution Latency Verification) | `testForegroundServiceNotification_postedImmediatelyOnStartCommand()`: Assert notification posted synchronously on main thread satisfying 5s SLA. |

---

## 3. Detailed Component Architecture & Modifications

### 3.1 `LiveWorkoutSession.java` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java`)
- **Volatile Workout ID & Mutator**:
  - Change `private final long workoutId;` to `private volatile long workoutId;`.
  - Add mutator:
    ```java
    public void setWorkoutId(long workoutId) {
        this.workoutId = workoutId;
    }
    ```
  - Allows `LiveWorkoutSession` instantiation on the main thread with placeholder ID `0` prior to background database insertion, with thread-safe atomic memory update once `createNewWorkout()` resolves.

### 3.2 `TrackerService.java` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`)
1. **Volatile Synchronization Barrier Primitive**:
   - Declare:
     ```java
     private volatile CompletableFuture<Void> mTableInitializationFuture = CompletableFuture.completedFuture(null);
     ```
   - Provide getter for unit test observability:
     ```java
     public CompletableFuture<Void> getTableInitializationFuture() {
         return mTableInitializationFuture;
     }
     ```
2. **Volatile Session Identifiers**:
   - Ensure `mWorkoutID` and `mSamplesTableName` are declared `volatile`:
     ```java
     private volatile long mWorkoutID;
     private volatile String mSamplesTableName;
     ```
3. **Refactor `onStartCommand()` for `START_NORMAL`**:
   - Compute `mBaseFileName` and `mSamplesTableName` immediately on the calling thread (pure string formatting, 0 I/O).
   - Instantiate `mLiveSession = new LiveWorkoutSession(0, IMPORTANT_SENSOR_TYPES)`.
   - Allocate fresh initialization future:
     ```java
     final CompletableFuture<Void> initFuture = new CompletableFuture<>();
     mTableInitializationFuture = initFuture;
     ```
   - Offload database setup to `mDbExecutor`:
     ```java
     mDbExecutor.submit(() -> {
         try {
             mWorkoutID = createNewWorkout();
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
     ```
   - For `RESUME_BY_USER` and `RESUME_SERVICE_RECREATION`:
     - Set `mTableInitializationFuture = CompletableFuture.completedFuture(null);` and invoke `notifyTrackingStarted(mWorkoutID);` directly since the database table already exists.
   - For `START_NORMAL`, do NOT invoke `notifyTrackingStarted(mWorkoutID)` synchronously on the main thread (it is triggered inside the `initFuture` callback upon schema commit).
   - Start foreground service (`performStartForeground(...)`) and scheduler (`mTrackerHandle = mScheduler.scheduleAtFixedRate(...)`) immediately.
   - Return `Service.START_STICKY` immediately (< 10ms execution time).

4. **Non-Blocking Reactive Barrier Chaining in `sampleAndWriteToDb()`**:
   - Replace blocking `.get(5, TimeUnit.SECONDS)` with non-blocking callback chaining (`thenAcceptAsync`):
     ```java
     mTableInitializationFuture.thenAcceptAsync(v -> {
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
     }, mDbExecutor).exceptionally(ex -> {
         Log.e(TAG, "Sample write skipped due to initialization failure: " + ex.getMessage());
         return null;
     });
     ```
   - **Eliminates Thread Starvation**: Worker threads never block waiting on `.get()`. When table creation is still running, sample tasks attach as listener callbacks. Once table initialization completes, callbacks dispatch in FIFO order on `mDbExecutor`. If table initialization fails, callbacks are bypassed cleanly via `.exceptionally()`.

5. **Offload `onSearchingFinished()` and `onUserSelectedSportTypeChanged()` SQLite Updates**:
   - Wrap the `summariesDb.update(WorkoutSummaries.TABLE, ...)` calls inside `mTableInitializationFuture.thenAcceptAsync(v -> { ... }, mDbExecutor)`:
     ```java
     mTableInitializationFuture.thenAcceptAsync(v -> {
         try {
             WorkoutSummariesDatabaseManager databaseManager = WorkoutSummariesDatabaseManager.getInstance(TrackerService.this);
             SQLiteDatabase summariesDb = databaseManager.getDatabase();
             summariesDb.update(WorkoutSummaries.TABLE,
                     values,
                     WorkoutSummaries.C_ID + "=?",
                     new String[]{Long.toString(mWorkoutID)});
         } catch (Exception e) {
             Log.e(TAG, "Failed to update summaries DB", e);
         }
     }, mDbExecutor);
     ```
   - Eliminates main-thread database writes when sensor discovery completes or user switches sport type during startup, and guarantees updates wait until `mWorkoutID` is valid.

6. **Deterministic Defensive Teardown Sequence**:
   If table creation throws `SQLException` or `Throwable`:
   1. Mark `initFuture.completeExceptionally(t)`.
   2. Set `mTrackingInterrupted = true`.
   3. Cancel and nullify recurring sensor scheduler `mTrackerHandle`.
   4. Clear BANAL connection state if pending.
   5. Post user notification `showTrackingInterruptedNotification()`.
   6. Invoke `performStopSelf()` to terminate the foreground service without lingering background threads or process crash loops.

---

## 4. Invariants & Impact Analysis

| System Dimension | Invariant / Constraint | Verification |
| :--- | :--- | :--- |
| **Main-Thread Latency** | `onStartCommand` SHALL NOT perform synchronous SQLite DDL or page reads. Return time < 10ms. | Eliminates ANR issue `26191e1ecfe7f1a95a7b8980ba4108cf`. |
| **Zero Sample Loss** | All incoming sensor samples during the first 1-2s of startup MUST be buffered and persisted without drops. | Reactive `thenAcceptAsync` chaining on `mDbExecutor` preserves all samples in memory and writes them once table commits. |
| **Zero Thread Starvation** | Background executor threads MUST NOT block on `.get()` waiting for schema DDL. | Enforced by reactive `thenAcceptAsync` callback chaining. |
| **Table Precedence** | No sensor sample persistence SHALL commence before `createNewTable()` transaction commits. | Chained callback execution triggers strictly upon `initFuture.complete(null)`. |
| **Foreground Service SLA** | `performStartForeground` executes immediately on main thread within Android 14+ 5-second window. | FGS notification posted synchronously in `onStartCommand`, while CPU WakeLock keeps background thread active. |
| **Downstream Observers** | UI observers listening to `TRACKING_STARTED_INTENT` MUST NOT query an uninitialized table. | `notifyTrackingStarted(mWorkoutID)` broadcast emitted strictly inside the `initFuture` completion block. |
| **Fatal Error Recovery** | Fatal SQLite exceptions during startup MUST NOT crash the process or leave orphan services. | Caught in `mDbExecutor`, completes future exceptionally, cancels ticker, alerts user via notification, calls `performStopSelf()`. |

---

## 5. Verification & Testing Strategy

### 5.1 Automated Unit Tests (`TrackerServiceAsyncInitTest.kt`)
Create dedicated unit test suite in `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceAsyncInitTest.kt`:
1. **TC-1 (Main-Thread Asynchronous Decoupling - REQ-STB-009.1 / REQ-STB-009.6)**:
   - Verify `onStartCommand(START_NORMAL)` completes immediately with `START_STICKY` while table creation runs on executor.
2. **TC-2 (Barrier Synchronization & Successful Initialization - REQ-STB-009.2 / REQ-STB-009.4)**:
   - Verify `mTableInitializationFuture` completes successfully and `notifyTrackingStarted` is invoked with valid ID.
3. **TC-3 (Zero Sample Loss & Non-Blocking Chaining - REQ-STB-009.3)**:
   - Simulate sensor sample write submitted while table initialization is pending on `mDbExecutor`. Verify sample write succeeds without throwing `SQLiteException` or thread starvation.
4. **TC-4 (Defensive Exception Handling & Teardown - REQ-STB-009.5)**:
   - Mock `createNewTable` throwing `SQLException`. Verify `mTableInitializationFuture` completes exceptionally, `performStopSelf()` is invoked, `mTrackerHandle` is cancelled, and `showTrackingInterruptedNotification()` is triggered without crashing.
5. **TC-5 (LiveWorkoutSession Decoupling)**:
   - Verify `LiveWorkoutSession` accepts placeholder ID `0` and mutates to valid workout ID when initialized.

### 5.2 Clean-Room Regression Verification
Execute full unit test suite:
```bash
./gradlew testDebugUnitTest
```
Ensure 100% pass rate with zero regressions across all modules.
