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
* `mDbExecutor`: `Executors.newSingleThreadExecutor()`. A FIFO unbounded single-thread queue.
* `mTrackerHandle`: `mScheduler.scheduleAtFixedRate(tracker, 0, 1, TimeUnit.SECONDS)`.
* `tracker.run()` calls `sampleAndWriteToDb()`, which submits writing samples to `mDbExecutor`.
* **Key Concurrency Invariant**: Because `mDbExecutor` is a single-thread executor, submitting the initial workout and table creation to `mDbExecutor` during `onStartCommand` guarantees that Task 1 (table & workout creation) executes **strictly before** Task 2 (sample insertion). Sample writes will never encounter a missing table.

---

## 4. Requirement Mapping

* **Requirement ID**: **`REQ-STB-009`**
* **Title**: `Asynchronous Workout Initialization & Main-Thread ANR Immunity in TrackerService`
* **Target Specification**:
  1. *Main-Thread Database Decoupling*: `TrackerService.onStartCommand` SHALL NOT execute synchronous database queries, schema modifications (`CREATE TABLE`, `DROP TABLE`), or multi-row inserts on the main UI thread.
  2. *Asynchronous Workout & Table Creation*: Initial workout summary creation (`createNewWorkout()`) and sample table creation (`WorkoutSamplesDatabaseManager.createNewTable()`) SHALL be dispatched to `TrackerService`'s dedicated single-thread database executor (`mDbExecutor`).
  3. *FIFO Execution & Table Readiness Guarantee*: Subsequent sample writes submitted by `sampleAndWriteToDb()` SHALL execute in FIFO sequence after table creation completes, guaranteeing zero data loss or missing-table exceptions.
  4. *Defensive Exception Shielding*: `WorkoutSamplesDatabaseManager.createNewTable()` and `createNewWorkout()` SHALL be encapsulated in defensive `try ... catch (SQLException | IllegalStateException)` blocks with diagnostic error logging.
  5. *Immediate Foreground Promotion*: `performStartForeground()` SHALL execute promptly on the main thread within Android's 5-second deadline without being delayed by database storage I/O.

---

## 5. System Invariants

1. **Sampling Invariant**: The 1-second sampling rate and live metric calculations (speed, heart rate, distance, cadence, elevation, power) MUST NOT be altered.
2. **Schema Invariant**: The columns created by `makeColumns(sensorTypes)` in `WorkoutSamples.db` MUST remain identical to preserve compatibility with all export formats (GPX, TCX, CSV, GC) and UI charts.
3. **Lifecycle Invariant**: `START_STICKY`, foreground notification management, and Android 14+ background launch guards (from `REQ-STB-002`) MUST remain fully intact.
4. **Shutdown Integrity**: In `onDestroy()`, `mDbExecutor.shutdown()` and `awaitTermination` MUST ensure all queued database tasks are committed before releasing database resources.

---

## 6. Risk Rating & Recommendation

* **Risk Level**: **`LOW`**
  - The single-thread FIFO nature of `mDbExecutor` natively guarantees serialization between table creation and sample insertion.
  - Offloading from the main thread directly adheres to standard Android architectural best practices and eliminates the root cause of ANRs.
* **Recommendation**: **`RECOMMEND PASS`**
