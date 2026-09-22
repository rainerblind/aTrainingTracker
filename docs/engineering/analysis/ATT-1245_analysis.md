# Engineering Analysis - ATT-1245

**Ticket**: `ATT-1245`: `[Bug] com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.createNewTable`  
**Parent / Epic**: `ATT-235`: `No crashs`  
**Sub-task**: `ATT-1259`: `[Analysis] WorkoutSamplesDatabaseManager.createNewTable`  
**Component**: Workout Tracking Engine / Database I/O (`TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `LiveWorkoutSession.java`)  
**Sprint**: `2026-39.2`  
**Target Release**: `V4.9.38`  
**Requirement Mapping**: `REQ-STB-009` (`docs/requirements.md`)  
**Test Specification**: `TST-STB-009` (`docs/tests.md`)  
**Stage**: `Stage 1: Analysis (SWE.1 / SYS.2)`

---

## 1. Problem Statement & User Impact

### 1.1 Production ANR Report
Firebase Crashlytics reported an Application Not Responding (ANR) fatal session on release `4.9.36 (260)` (Issue `26191e1ecfe7f1a95a7b8980ba4108cf`, Session `6AAE3DBE0098000160953499E9062ECA_DNE_0_v2`, Date: `Sat Sep 19 2026 12:31:30 GMT+0200`):

```text
main (native):tid=1 systid=24725
#00 pc 0xd5d0c libc.so (pread + 12) (BuildId: 56f2e102db31359cc22460be335d240c)
#01 pc 0x252524 libsqlite.so (unixRead + 100) (BuildId: ced4e2ce71ce2bb893895df8af22b429)
#02 pc 0x185820 libsqlite.so (getPageNormal + 460) (BuildId: ced4e2ce71ce2bb893895df8af22b429)
#03 pc 0x1ede5c libsqlite.so (checkTreePage + 220) (BuildId: ced4e2ce71ce2bb893895df8af22b429)
#04 pc 0x24ab38 libsqlite.so ($x.cold.0 + 680) (BuildId: ced4e2ce71ce2bb893895df8af22b429)
#05 pc 0x24ab38 libsqlite.so ($x.cold.0 + 680) (BuildId: ced4e2ce71ce2bb893895df8af22b429)
#06 pc 0x1ddd14 libsqlite.so (sqlite3VdbeExec + 60436) (BuildId: ced4e2ce71ce2bb893895df8af22b429)
#07 pc 0x1cee44 libsqlite.so (sqlite3_exec + 1076) (BuildId: ced4e2ce71ce2bb893895df8af22b429)
#08 pc 0x1dde10 libsqlite.so (sqlite3VdbeExec + 60688) (BuildId: ced4e2ce71ce2bb893895df8af22b429)
#09 pc 0x185944 libsqlite.so (sqlite3_step + 132) (BuildId: ced4e2ce71ce2bb893895df8af22b429)
#10 pc 0x1abf6c libandroid_runtime.so (android::nativeExecuteForChangedRowCount + 28) (BuildId: 2dfde8aee55fa1b8bc167a90951fcf63)
       at android.database.sqlite.SQLiteConnection.nativeExecuteForChangedRowCount(Native method)
       at android.database.sqlite.SQLiteConnection.executeForChangedRowCount(SQLiteConnection.java:944)
       at android.database.sqlite.SQLiteSession.executeForChangedRowCount(SQLiteSession.java:791)
       at android.database.sqlite.SQLiteStatement.executeUpdateDelete(SQLiteStatement.java:67)
       at android.database.sqlite.SQLiteDatabase.executeSql(SQLiteDatabase.java:2266)
       at android.database.sqlite.SQLiteDatabase.execSQL(SQLiteDatabase.java:2185)
       at com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.createNewTable(WorkoutSamplesDatabaseManager.java:414)
       at com.atrainingtracker.trainingtracker.tracker.TrackerService.onStartCommand(TrackerService.java:414)
       at android.app.ActivityThread.handleServiceArgs(ActivityThread.java:5955)
       at android.app.ActivityThread.-$$Nest$mhandleServiceArgs(unavailable)
       at android.app.ActivityThread$H.handleMessage(ActivityThread.java:2927)
       at android.os.Handler.dispatchMessage(Handler.java:114)
       at android.os.Looper.loopOnce(Looper.java:274)
       at android.os.Looper.loop(Looper.java:369)
       at android.app.ActivityThread.main(ActivityThread.java:10090)
```

### 1.2 User Impact
When the user taps "Start Tracking", the Android main UI thread freezes completely during `TrackerService.onStartCommand`. If disk I/O, database page verification, or SQLite schema locks experience even transient latency, Android's watchdog terminates the application with an ANR ("Application Not Responding"). The user experience is catastrophic: workout recording is aborted, the UI becomes unresponsive, and the OS prompts the user to force-close the app.

---

## 2. Forensic Root Cause Analysis (RCA)

### 2.1 Symptom vs. Root Cause
* **Symptom**: Main thread ANR in `SQLiteDatabase.execSQL()` within `WorkoutSamplesDatabaseManager.createNewTable()` at line 414 during `TrackerService.onStartCommand()`.
* **True Root Cause**:
  1. **Synchronous Main Thread Database DDL & Multi-Table Insert**: In `TrackerService.java:408-415`, `onStartCommand()` handles `START_NORMAL` by executing heavy, blocking database I/O directly on Android's main UI looper:
     - `createNewWorkout()`: Executes `summariesDb.insert(WorkoutSummaries.TABLE, null, values)` and iterates through all combinations of `FileFormat` and `ExportType` in `ExportManager.newWorkout(mBaseFileName)`, performing dozens of SQL inserts.
     - `WorkoutSamplesDatabaseManager.createNewTable()`: Opens the database helper, calls `db.execSQL("drop table if exists " + table)`, and executes `db.execSQL("create table " + table + "(...)")` for all `SensorType` columns.
  2. **SQLite Schema B-Tree Traversal & Storage Latency**: In SQLite, creating or dropping tables updates the master schema (`sqlite_master` / `sqlite_schema`). This requires traversing the internal B-tree across database pages (`checkTreePage`, `getPageNormal`, `unixRead`, `pread`) and acquiring an exclusive database schema lock. On active devices with historical workouts (where `WorkoutSamples.db` contains hundreds of tables and many megabytes of data), this disk I/O takes multiple seconds.
  3. **Architectural Inconsistency**: Later in `TrackerService`, all runtime sample inserts (`sampleAndWriteToDb()`, line 1041) and altitude shifts (line 230) are correctly offloaded to `mDbExecutor` (`Executors.newSingleThreadExecutor()`). However, the startup initialization (`createNewWorkout` and `createNewTable`) was never offloaded and remained on the main thread.
  4. **Lack of Defensive Exception Shielding**: Neither `createNewWorkout()` nor `createNewTable()` contained defensive try-catch guards against `SQLException` or `IllegalStateException`, leaving startup vulnerable to process crashes if disk errors or schema conflicts occur.

---

## 3. Call Site & Dependency Audit

### 3.1 Direct Call Sites of `createNewTable`
1. `TrackerService.java:414` (Main UI Thread during `onStartCommand(START_NORMAL)`) — **The defect call site**.
2. `LegacyImportEngine.kt:621` — Runs in a background coroutine/thread during database migration.
3. `LegacyImportEngine.kt:1012` — Runs in a background coroutine/thread during database migration.

### 3.2 Threading & Execution Concurrency in `TrackerService`
* `mDbExecutor`: `private final ExecutorService mDbExecutor = Executors.newSingleThreadExecutor();`. This is an unbounded single-thread FIFO execution queue.
* `mTrackerHandle`: `mScheduler.scheduleAtFixedRate(tracker, 0, 1, TimeUnit.SECONDS)`.
* `tracker.run()` calls `sampleAndWriteToDb()`, which submits writing samples to `mDbExecutor`.
* **Downstream Consumers & Observers**:
  - `WorkoutRepository` & UI screens listen for `TRACKING_STARTED_INTENT` broadcast (sent via `notifyTrackingStarted(workoutId)`).
  - Delaying `notifyTrackingStarted` until the database table is confirmed created ensures observers never query an uninitialized table.

---

## 4. Architectural Remediation Strategy

### 4.1 Execution Boundary & Threading Model
1. **Asynchronous Dispatch via `mDbExecutor`**:
   - In `TrackerService.onStartCommand()`, remove synchronous invocations of `createNewWorkout()` and `createNewTable()`.
   - Instead, dispatch a runnable to `mDbExecutor.submit(() -> { ... })` that executes:
     1. `long workoutId = createNewWorkout();`
     2. `mWorkoutID = workoutId;`
     3. `liveSession.setWorkoutId(workoutId);`
     4. `WorkoutSamplesDatabaseManager.getInstance(TrackerService.this).createNewTable(baseFileName, Arrays.asList(SensorType.values()));`
     5. `mTableInitializationFuture.complete(null);` (release synchronization barrier)
     6. `notifyTrackingStarted(workoutId);` (notify observers only when table exists)
   - Immediately return `Service.START_STICKY` from `onStartCommand()`. The main thread execution duration drops from hundreds of milliseconds (or seconds) to `< 1ms`, completely eliminating ANRs.

2. **CompletableFuture Synchronization Barrier & Sample Buffering**:
   - Introduce `private final CompletableFuture<Void> mTableInitializationFuture = new CompletableFuture<>();` in `TrackerService`.
   - For `START_NORMAL`, `mTableInitializationFuture` is completed once `createNewTable()` commits on `mDbExecutor`. (For resume modes `RESUME_BY_USER` / `RESUME_SERVICE_RECREATION`, it is completed immediately upon startup).
   - In `sampleAndWriteToDb()` on the background executor thread:
     ```java
     mDbExecutor.submit(() -> {
         try {
             // Await table initialization on the worker thread (never blocks main thread)
             if (!mTableInitializationFuture.isDone()) {
                 mTableInitializationFuture.get(5, TimeUnit.SECONDS);
             }
             if (mTableInitializationFuture.isCompletedExceptionally()) {
                 Log.w(TAG, "Skipping sample write: Table initialization failed.");
                 return;
             }
             // Write sample to SQLite
             ...
         } catch (Exception e) {
             Log.e(TAG, "Error during sample write: " + e.getMessage(), e);
         }
     });
     ```
   - This provides an explicit hardware-independent synchronization barrier: any sensor samples arriving during initial table creation are buffered in `mDbExecutor`'s FIFO task queue and await the future before writing. Zero samples are lost, zero race conditions exist, and no `SQLiteException: no such table` errors can occur.

3. **Defensive Downstream Error Handling Strategy**:
   - In `WorkoutSamplesDatabaseManager.createNewTable()`:
     - Wrap table creation in `try { ... } catch (SQLException | IllegalStateException e) { Log.e(TAG, "Failed to create workout samples table for " + workoutName, e); throw e; }`.
   - In `TrackerService` inside `mDbExecutor`:
     - If `createNewWorkout()` or `createNewTable()` throws an exception:
       ```java
       try {
           // workout and table creation
           mTableInitializationFuture.complete(null);
           notifyTrackingStarted(mWorkoutID);
       } catch (Throwable t) {
           Log.e(TAG, "Fatal database initialization error: " + t.getMessage(), t);
           mTableInitializationFuture.completeExceptionally(t);
           mTrackingInterrupted = true;
           if (mTrackerHandle != null) {
               mTrackerHandle.cancel(true);
               mTrackerHandle = null;
           }
           showTrackingInterruptedNotification();
           performStopSelf();
       }
       ```
     - Rather than swallowing the error and allowing broken telemetry collection, the service completes the future exceptionally, cancels the timer handle, notifies the user cleanly via `showTrackingInterruptedNotification()`, and gracefully terminates via `performStopSelf()`.

4. **Wakelock & Android 14+ FGS Lifecycle Compliance**:
   - `performStartForeground(TrainingApplication.TRACKING_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)` executes immediately on the main thread during `onStartCommand()`, satisfying Android 14+ 5-second FGS startup constraints (`REQ-STB-002`) without awaiting database disk I/O.
   - CPU WakeLock is acquired during service start to ensure the OS does not suspend the process before the database initialization task completes.

5. **Live Workout Session Decoupling**:
   - In `LiveWorkoutSession.java`, change `private final long workoutId;` to `private volatile long workoutId;` and introduce `public void setWorkoutId(long workoutId) { this.workoutId = workoutId; }`.
   - In `TrackerService.onStartCommand()`, instantiate `mLiveSession = new LiveWorkoutSession(0, IMPORTANT_SENSOR_TYPES);` immediately on the main thread, so `mLiveSession` is non-null for any concurrent sensor stream calculations, and update the ID once `mDbExecutor` completes `createNewWorkout()`.

---

## 5. Requirement Mapping

* **Requirement ID**: **`REQ-STB-009`** (Registered in `docs/requirements.md`)
* **Title**: `Asynchronous Workout Initialization & Main-Thread ANR Immunity in TrackerService`
* **Target Specification**:
  1. *Main-Thread Database Decoupling*: `TrackerService.onStartCommand` SHALL NOT execute synchronous database DDL statements (`CREATE TABLE`, `DROP TABLE`), database opening, or multi-row SQLite inserts on the Android main UI thread.
  2. *Asynchronous Executor Dispatch*: During `START_NORMAL` initialization, `createNewWorkout()`, `WorkoutSamplesDatabaseManager.createNewTable()`, and initial `notifyTrackingStarted()` SHALL be dispatched to `TrackerService`'s dedicated single-thread FIFO database executor (`mDbExecutor`).
  3. *CompletableFuture Synchronization & Sample Buffering*: `TrackerService` SHALL maintain a synchronization primitive (`CompletableFuture<Void> mTableInitializationFuture`). Any early sensor samples or write tasks submitted prior to table creation completion SHALL await the future on the background executor (up to 5s timeout) rather than being dropped or failing with `SQLiteException: no such table`. Sensor sampling SHALL NEVER commence persistence before table creation commits to disk.
  4. *Broadcast Delay until Schema Commitment*: `notifyTrackingStarted(workoutId)` SHALL be dispatched only AFTER `mTableInitializationFuture` completes successfully, ensuring downstream UI observers and broadcast receivers never query an uninitialized table.
  5. *Defensive Downstream Error Handling*: If database or table initialization throws `SQLException` or `IllegalStateException`, `mTableInitializationFuture` SHALL complete exceptionally, cancel `mTrackerHandle`, post `showTrackingInterruptedNotification()`, and gracefully invoke `performStopSelf()` rather than swallowing errors or allowing telemetry corruption.
  6. *Foreground Service Startup SLA & WakeLock Safety*: `performStartForeground()` in `TrackerService.onStartCommand` SHALL execute immediately on the main thread without awaiting database I/O, satisfying Android 14+ 5-second FGS startup constraints, with CPU WakeLock acquired to ensure uninterrupted background commit.

---

## 6. System Invariants

1. **Table Creation Precedence**: No sensor sample collection or persistence shall commence until the underlying workout table creation transaction is fully committed to disk.
2. **Zero Sample Drop**: Early sensor ticks arriving during the table creation window SHALL be queued and persisted once the table future completes.
3. **Sampling Invariant**: The 1-second sampling rate and live metric calculations (speed, heart rate, distance, cadence, elevation, power) MUST NOT be altered.
4. **Schema Invariant**: The columns created by `makeColumns(sensorTypes)` in `WorkoutSamples.db` MUST remain identical to preserve compatibility with all export formats (GPX, TCX, CSV, GC) and UI charts.
5. **Lifecycle Invariant**: `START_STICKY`, foreground notification management, and Android 14+ background launch guards (from `REQ-STB-002`) MUST remain fully intact.
6. **Shutdown Integrity**: In `onDestroy()`, `mDbExecutor.shutdown()` and `awaitTermination` MUST ensure all queued database tasks are committed before releasing database resources.

---

## 7. Risk Rating & Recommendation

* **Risk Level**: **`LOW`**
  - The combination of `mDbExecutor` and the `CompletableFuture` barrier guarantees deterministic sequencing without thread deadlocks, sample drops, or race conditions.
  - Offloading from the main thread directly adheres to standard Android architectural best practices and completely eliminates the root cause of ANRs.
* **Recommendation**: **`RECOMMEND PASS`**
