# Architecture & Implementation Plan: Asynchronous Workout Initialization & Main-Thread ANR Immunity in TrackerService (ATT-1245 / Gate 3)

## 1. Context & Objectives
Firebase Crashlytics issue `26191e1ecfe7f1a95a7b8980ba4108cf` (Session `6AAE3DBE0098000160953499E9062ECA_DNE_0_v2`) identified a fatal Application Not Responding (ANR) error in production during workout initialization.

### Root Cause Analysis (RCA) Summary
In `TrackerService.java:408-415`, `onStartCommand(START_NORMAL)` executed `createNewWorkout()` and `WorkoutSamplesDatabaseManager.createNewTable(mBaseFileName, ...)` synchronously on the Android main UI thread. In SQLite, `createNewTable()` executes `DROP TABLE IF EXISTS` and `CREATE TABLE ...`, modifying the `sqlite_master` schema catalog. Under slow flash storage, large databases with hundreds of tables, or concurrent disk I/O, schema page traversals (`pread`, `unixRead`, `checkTreePage`) blocked the main thread far exceeding the Android looper watchdog limit (> 5 seconds), triggering an ANR.

### Core Objectives
1. Eliminate all synchronous SQLite DDL execution from the main UI thread during `onStartCommand()`.
2. Guarantee sub-millisecond (< 10ms) `onStartCommand()` return latency on the main thread, satisfying Android 14+ Foreground Service startup SLAs.
3. Enforce barrier synchronization via `CompletableFuture<Void>` on `TrackerService.mDbExecutor` to guarantee zero sample loss, FIFO buffering, and prevention of `SQLiteException: no such table`.
4. Defer `notifyTrackingStarted(workoutId)` broadcast until table creation is fully committed to disk so UI observers never query an uninitialized table.
5. Provide defensive error recovery and graceful teardown if database initialization encounters a fatal `SQLException`.

---

## 2. Requirement & Test Specification Traceability
- **Parent Issue**: `ATT-1245` (`[Bug] com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.createNewTable`)
- **Fix Version**: `V4.9.38`
- **System Requirement**: `REQ-STB-009` (*Asynchronous Workout Initialization & Main-Thread ANR Immunity in TrackerService*) in `docs/requirements.md`
- **Test Specification**: `TST-STB-009` (*Asynchronous Workout Initialization, Barrier Synchronization & ANR Immunity Verification*) in `docs/tests.md`
- **Sub-task**: `ATT-1261` (`[Impl-Plan] WorkoutSamplesDatabaseManager.createNewTable`)

---

## 3. Detailed Component Architecture & Modifications

### 3.1 `LiveWorkoutSession.java` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java`)
- **Volatile Workout ID**:
  - Change `private final long workoutId;` to `private volatile long workoutId;`.
  - Add mutator:
    ```java
    public void setWorkoutId(long workoutId) {
        this.workoutId = workoutId;
    }
    ```
  - Allows `LiveWorkoutSession` instantiation on the main thread with placeholder ID `0` prior to background database insertion, with thread-safe update once `createNewWorkout()` resolves.

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

4. **Synchronization Barrier in `sampleAndWriteToDb()`**:
   - In `sampleAndWriteToDb()` on `mDbExecutor`:
     ```java
     mDbExecutor.submit(() -> {
         try {
             mTableInitializationFuture.get(5, TimeUnit.SECONDS);
         } catch (Exception e) {
             Log.e(TAG, "Table initialization future timed out or failed before sample write", e);
             return;
         }
         ...
     ```
   - Guarantees that any sample queued on `mDbExecutor` while table creation is running awaits completion.
   - Because `mDbExecutor` is a single-thread FIFO executor, table initialization queued first finishes before sample tasks execute. The barrier explicitly verifies successful completion, preventing `SQLiteException: no such table` if an unexpected error occurred.

5. **Offload `onSearchingFinished()` and `onUserSelectedSportTypeChanged()` SQLite Updates**:
   - Wrap the `summariesDb.update(WorkoutSummaries.TABLE, ...)` calls inside `mDbExecutor.submit(() -> { ... })` awaiting `mTableInitializationFuture.get(5, TimeUnit.SECONDS)`.
   - Eliminates main-thread database writes when sensor discovery completes or user switches sport type during startup.

---

## 4. Invariants & Impact Analysis

| System Dimension | Invariant / Constraint | Verification |
| :--- | :--- | :--- |
| **Main-Thread Latency** | `onStartCommand` SHALL NOT perform synchronous SQLite DDL or page reads. Return time < 10ms. | Eliminates ANR issue `26191e1ecfe7f1a95a7b8980ba4108cf`. |
| **Zero Sample Loss** | All incoming sensor samples during the first 1-2s of startup MUST be buffered and persisted without drops. | FIFO queue buffering on `mDbExecutor` preserves all samples in memory and writes them once table commits. |
| **Table Precedence** | No sensor sample persistence SHALL commence before `createNewTable()` transaction commits. | Enforced by FIFO submission order and `mTableInitializationFuture.get(5, TimeUnit.SECONDS)`. |
| **Foreground Service SLA** | `performStartForeground` executes immediately on main thread within Android 14+ 5-second window. | FGS notification posted synchronously in `onStartCommand`, while CPU WakeLock keeps background thread active. |
| **Downstream Observers** | UI observers listening to `TRACKING_STARTED_INTENT` MUST NOT query an uninitialized table. | `notifyTrackingStarted(mWorkoutID)` broadcast emitted strictly inside the `initFuture` completion block. |
| **Fatal Error Recovery** | Fatal SQLite exceptions during startup MUST NOT crash the process or leave orphan services. | Caught in `mDbExecutor`, completes future exceptionally, cancels ticker, alerts user via notification, calls `performStopSelf()`. |

---

## 5. Verification & Testing Strategy

### 5.1 Automated Unit Tests (`TrackerServiceAsyncInitTest.kt`)
Create dedicated unit test suite in `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceAsyncInitTest.kt`:
1. **TC-1 (Main-Thread Asynchronous Decoupling)**:
   - Verify `onStartCommand(START_NORMAL)` completes immediately with `START_STICKY` while table creation runs on executor.
2. **TC-2 (Barrier Synchronization & Successful Initialization)**:
   - Verify `mTableInitializationFuture` completes successfully and `notifyTrackingStarted` is invoked.
3. **TC-3 (Zero Sample Loss & FIFO Buffering)**:
   - Simulate sensor sample write submitted while table initialization is pending on `mDbExecutor`. Verify sample write succeeds without throwing `SQLiteException`.
4. **TC-4 (Defensive Exception Handling & Teardown)**:
   - Mock `createNewTable` throwing `SQLException`. Verify `mTableInitializationFuture` completes exceptionally, `performStopSelf()` is invoked, `mTrackerHandle` is cancelled, and `showTrackingInterruptedNotification()` is triggered without crashing.
5. **TC-5 (LiveWorkoutSession Decoupling)**:
   - Verify `LiveWorkoutSession` accepts placeholder ID `0` and mutates to valid workout ID when initialized.

### 5.2 Clean-Room Regression Verification
Execute full unit test suite:
```bash
./gradlew testDebugUnitTest
```
Ensure 100% pass rate with zero regressions across all modules.
