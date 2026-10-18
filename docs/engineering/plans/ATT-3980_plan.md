# Stage 3: Implementation Plan - ATT-3980: [Highest] Resumed workout writes all samples to a table named "null"

**Ticket**: [ATT-3980](https://rainerblind.atlassian.net/browse/ATT-3980)  
**Sub-task**: [ATT-4047](https://rainerblind.atlassian.net/browse/ATT-4047) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2135](https://rainerblind.atlassian.net/browse/ATT-2135) (*Tracking & Recording*)  
**Target Release**: `TBD (Set on completion per Rule 14)`  
**Active Sprint**: `2026-42.9` (`id = 377`)  
**Requirement Mapping**: `REQ-TRK-026`  
**Test Mapping**: `TST-TRK-018`  
**Branch**: `feature/ATT-3980`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

When an active workout is resumed via user action (`RESUME_BY_USER`) or recreated by Android after a process restart (`RESUME_SERVICE_RECREATION`), `TrackerService.onCreate()` binds asynchronously to `BANALService`, so `mBanalService` is `null` when `onStartCommand()` executes. Because `recreateValuesWhenResuming()` is deferred to `mBanalConnection.onServiceConnected()`, `mBaseFileName` is still `null` at line 613 of `onStartCommand()`, which evaluates `mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(null)` -> `"\"null\""`. Furthermore, when `mBanalConnection.onServiceConnected()` subsequently invokes `recreateValuesWhenResuming()` -> `applyResumedState(state)`, `applyResumedState()` sets `mBaseFileName = state.baseFileName` **without recomputing `mSamplesTableName`**, leaving `mSamplesTableName` permanently set to `"\"null\""` for the remainder of the workout. Finally, `handleMissingColumns()` silently catches and discards the `SQLiteException` (`no such table: "null"`) while `summariesDb.setTransactionSuccessful()` still commits, causing total post-resume sample loss with zero error logs.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-026` (*Resumed Workout Sample Table Resolution, Asynchronous BANAL Binding Barrier, and Sample Write Error Surfacing in `TrackerService`*)
* **Test Mapping**: `TST-TRK-018` (*Resumed Workout Sample Table Resolution, Asynchronous BANAL Binding Barrier & Sample Write Error Surfacing Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing `START_NORMAL` (`TrackerServiceWorkoutStartTest`, `TrackerServiceAsyncInitTest`) and synchronous resume (`TrackerServiceResumeTest`) suites continue to pass cleanly.
2. **Structural Wiring Contract**: `BANALService.checkOrInitializeLocationDevices();` in `TrackerService.onCreate()` and `mBanalConnection.onServiceConnected()` remains intact (`FreshInstallLocationInitTest`).
3. **Thread Safety, Timeout & Teardown Safety**: `mTableInitializationFuture` is initialized idempotently, bounded by a timeout handler on deferred resume, and cancelled cleanly in `onDestroy()` to prevent memory leaks or stale post-destroy execution.
4. **No Uncaught Service Crash on Sample Write**: `sampleAndWriteToDb()` and `handleMissingColumns()` never throw an uncaught `RuntimeException` onto `mSamplingExecutor` (which would halt periodic 1 Hz ticks or crash `TrackerService`); instead, missing tables for valid `mBaseFileName`s are self-healed via `createNewTable(...)`, and unrecoverable `SQLiteException`s are logged via `Log.e` and abort `summariesDb.setTransactionSuccessful()` for that tick.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutSamplesDatabaseManager.java` (`com.atrainingtracker.trainingtracker.database`) & Full Call-Site Audit
* **Validation in `getTableName(String workoutName)`**:
  * Reject `null`, empty/blank (`workoutName.trim().isEmpty()`), or literal `"null"` (`"null".equalsIgnoreCase(workoutName.trim())`) by throwing an `IllegalArgumentException("Invalid workout baseFileName for sample table: " + workoutName)`.
* **Complete Call-Site Audit Across Codebase (26 Call Sites Audited)**:
  1. `WorkoutSamplesDatabaseManager.java` (12 internal call sites: lines 133, 141, 154, 175, 238, 242, 350, 380, 422, 435, 453, 596): All callers guard or pass a non-null `baseFileName` from `WorkoutSummaries`. Additionally, `existsTable(String workoutName)` will return `false` immediately if `workoutName == null || workoutName.trim().isEmpty() || "null".equalsIgnoreCase(workoutName.trim())`.
  2. `WorkoutSummariesDatabaseManager.java` (2 call sites: lines 1180, 1968): Both check `if (baseFileName != null && !baseFileName.isEmpty())` before calling `getTableName(baseFileName)`.
  3. Exporter Writers (`CSVFileWriter.java:72`, `FitFileWriter.java:151`, `GCFileWriter.java:116`, `GPXFileWriter.java:91`, `TCXFileWriter.java:135`): All guarded by `ExportManager` which verifies `FILE_BASE_NAME` from `WorkoutSummaries`.
  4. `TrackOnMapHelper.java:76`, `ImportEngine.kt:332`, `LegacyImportEngine.kt:687, 1077`, `WorkoutRepository.kt:332, 418, 523, 584, 792, 866`: All check `if (baseFileName.isNullOrBlank()) return ...` prior to calling `getTableName(baseFileName)`.
  5. `TrackerService.java` (lines 536, 613, 1565): Line 613 was the sole unguarded caller passing `null` during deferred resume. All `TrackerService` calls to `getTableName(mBaseFileName)` will be wrapped in a helper `resolveSamplesTableNameSafely(String baseFileName)` that catches `IllegalArgumentException`, logs `Log.e(TAG, ...)`, and returns `null` rather than crashing the service.

### Component 2: `TrackerService.java` (`com.atrainingtracker.trainingtracker.tracker`)
1. **`resolveSamplesTableNameSafely(String baseFileName)` Service Boundary Helper**:
   * Validates `baseFileName != null && !baseFileName.trim().isEmpty() && !"null".equalsIgnoreCase(baseFileName.trim())` and invokes `WorkoutSamplesDatabaseManager.getTableName(baseFileName)` inside a `try-catch (IllegalArgumentException e)` block. If invalid, logs `Log.e(TAG, "Cannot resolve samples table name for invalid baseFileName: " + baseFileName, e)` and returns `null`.
2. **`applyResumedState(@NonNull ResumedWorkoutState state)`**:
   * Sets `mBaseFileName = state.baseFileName` and immediately updates `mSamplesTableName = resolveSamplesTableNameSafely(mBaseFileName)`.
   * If `mSamplesTableName == null` (e.g. corrupt database row with null/empty `BASE_FILE_NAME`), logs `Log.e(TAG, ...)`, marks `mTrackingInterrupted = true`, completes `mTableInitializationFuture.completeExceptionally(new IllegalStateException("Corrupt baseFileName on resume"))`, shows the interrupted notification, and calls `performStopSelf()`.
   * Otherwise, completes `mTableInitializationFuture.complete(null)` and invokes `notifyTrackingStarted(mWorkoutID)` when `mWorkoutID > 0`.
3. **`notifyTrackingStarted(long workoutId)`**:
   * Guards `if (getBaseContext() == null) return;` so direct unit test instantiations of `TrackerService` without a base context do not throw `NullPointerException`.
4. **Idempotent & Timeout-Bounded `mTableInitializationFuture` in `onStartCommand()` & Teardown in `onDestroy()`**:
   * For `RESUME_BY_USER` and `RESUME_SERVICE_RECREATION`, `onStartCommand()` checks idempotently:
     ```java
     if (mTableInitializationFuture == null || mTableInitializationFuture.isDone()) {
         mTableInitializationFuture = new CompletableFuture<>();
     }
     ```
     so rapid duplicate `onStartCommand()` calls during an in-flight resume do not overwrite an active pending future.
   * When `mBanalService == null` during `RESUME_BY_USER` or `RESUME_SERVICE_RECREATION`, attaches a bounded timeout handler (`scheduleResumeTimeoutGuard(mTableInitializationFuture)`) on `mSingleThreadPool` / `CompletableFuture.orTimeout(10, TimeUnit.SECONDS)` so that if `onServiceConnected()` does not complete within 10 seconds, `TrackerService` logs `Log.e(TAG, "Timed out waiting for BANALService connection during resume")`, sets `mTrackingInterrupted = true`, calls `showTrackingInterruptedNotification()`, and invokes `performStopSelf()`.
   * Removes the premature `mTableInitializationFuture = CompletableFuture.completedFuture(null)` assignments and guards `onStartCommand()` so `mSamplesTableName` is only computed when `mBaseFileName` is already non-null.
   * In `onDestroy()`, cancels any still-pending `mTableInitializationFuture` (`if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) { mTableInitializationFuture.cancel(false); }`) to prevent memory leaks or post-destroy callback execution.
5. **Ordered `mBanalConnection.onServiceConnected(ComponentName name, IBinder service)`**:
   * Executes `recreateValuesWhenResuming()` **before** `mBanalService.createNewLap()` and `onSearchingFinished()` when `mStartType == RESUME_BY_USER` or `mStartType == RESUME_SERVICE_RECREATION`, ensuring `mWorkoutID`, `mLapNr`, `mBaseFileName`, and `mSamplesTableName` are restored before lap increments or active tracking transitions (`sampleAndWriteToDb()`).
6. **`mAltitudeCorrectionReceiver` Post-Completion & Teardown Guard**:
   * Captures `final long workoutId = mWorkoutID;` and `final String samplesTable = mSamplesTableName;` **inside** `mTableInitializationFuture.thenAcceptAsync(...)`, and verifies `if (mTrackingInterrupted || workoutId <= 0 || samplesTable == null || samplesTable.isEmpty()) return;` before touching SQLite.
7. **Self-Healing Recovery & Transaction Rollback in `sampleAndWriteToDb()` and `handleMissingColumns()`**:
   * In `sampleAndWriteToDb()`:
     * If `mSamplesTableName == null || mSamplesTableName.trim().isEmpty() || "\"null\"".equalsIgnoreCase(mSamplesTableName.trim())`, first attempts lazy recovery via `mSamplesTableName = resolveSamplesTableNameSafely(mBaseFileName)`. If still `null`, logs `Log.e(TAG, "Cannot write workout sample: mSamplesTableName is invalid (" + mSamplesTableName + ") for workoutId=" + mWorkoutID)` and returns early before opening the database transaction.
     * Invokes `boolean sampleInserted = insertSampleWithRecovery(samplesDb, values);` inside the `try` block and only calls `summariesDb.setTransactionSuccessful()` if `sampleInserted` is `true` (rolling back `summariesDb` for that tick if sample insertion failed, without crashing the service or halting `mSamplingExecutor`).
   * In `handleMissingColumns(SQLiteDatabase samplesDb, ContentValues values, SQLiteException e)` (returning `boolean`):
     * **Case 1 (Missing Column)**: When `message != null && message.contains(" has no column named ")`, adds the missing column via `ALTER TABLE` and retries `samplesDb.insertOrThrow(mSamplesTableName, null, values)`, returning `true`.
     * **Case 2 (Missing Table Self-Healing)**: When `message != null && message.contains("no such table")` and `mBaseFileName` is valid (`resolveSamplesTableNameSafely(mBaseFileName) != null`), logs `Log.w(TAG, "Sample table " + mSamplesTableName + " missing; recreating table for " + mBaseFileName, e)`, invokes `WorkoutSamplesDatabaseManager.getInstance(this).createNewTable(mBaseFileName, values.keySet())`, updates `mSamplesTableName`, and retries `samplesDb.insertOrThrow(mSamplesTableName, null, values)`, returning `true`.
     * **Case 3 (Unrecoverable SQLite Error)**: Logs `Log.e(TAG, "Unrecoverable SQLiteException writing sample to table " + mSamplesTableName, e)` and returns `false` so the caller aborts `summariesDb.setTransactionSuccessful()` cleanly without crashing `TrackerService`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Harden `WorkoutSamplesDatabaseManager.getTableName()` & `existsTable()`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSamplesDatabaseManager.java`
* **Changes**:
  * Validate `workoutName != null && !workoutName.trim().isEmpty() && !"null".equalsIgnoreCase(workoutName.trim())` in `getTableName()`, throwing `IllegalArgumentException` if violated.
  * Guard `existsTable(String workoutName)` to return `false` immediately if `workoutName` is `null`, blank, or `"null"`.

### Step 2: Fix Deferred Resume State Synchronization, Idempotent/Bounded `CompletableFuture`, Callback Ordering, and Self-Healing Sample Write in `TrackerService.java`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* **Changes**:
  * Add `resolveSamplesTableNameSafely(String baseFileName)` catching `IllegalArgumentException` at the service boundary.
  * Update `applyResumedState()` to recompute `mSamplesTableName`, handle corrupt `baseFileName` safely, complete `mTableInitializationFuture`, and call `notifyTrackingStarted(mWorkoutID)`.
  * Guard `notifyTrackingStarted()` against `getBaseContext() == null`.
  * Update `onStartCommand()` to idempotently initialize `mTableInitializationFuture`, attach a 10-second timeout handler on deferred resume, and guard `resolveSamplesTableNameSafely(mBaseFileName)`.
  * Cancel pending `mTableInitializationFuture` in `onDestroy()`.
  * Reorder `mBanalConnection.onServiceConnected()` so `recreateValuesWhenResuming()` runs before `createNewLap()` and `onSearchingFinished()`.
  * Move `workoutId` and `samplesTable` reads inside `mTableInitializationFuture.thenAcceptAsync(...)` with null/interrupted guards in `mAltitudeCorrectionReceiver`.
  * Implement self-healing missing-table/missing-column recovery and boolean transaction gating in `sampleAndWriteToDb()` and `handleMissingColumns()`.

### Step 3: Expand Automated Unit Tests (`TST-TRK-018`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`
* **Changes**: Add unit tests covering:
  1. `applyResumedState` table name synchronization and `mTableInitializationFuture` completion.
  2. Deferred `RESUME_SERVICE_RECREATION` and `RESUME_BY_USER` via `mBanalConnection.onServiceConnected()` (verifying `mSamplesTableName` is never `"\"null\""`, `recreateValuesWhenResuming()` runs before `createNewLap()`/`onSearchingFinished()`, and `sampleAndWriteToDb()` persists rows into the resumed workout's table).
  3. Idempotent `mTableInitializationFuture` handling across repeated `onStartCommand()` calls and cancellation on `onDestroy()`.
  4. `WorkoutSamplesDatabaseManager.getTableName()` invalid input rejection (`null`, `""`, `"   "`, `"null"`).
  5. `sampleAndWriteToDb()` / `handleMissingColumns()` self-healing when the sample table is missing for a valid `mBaseFileName` and transaction rollback (without service crash) when an unrecoverable `SQLiteException` occurs.
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.banalservice.devices.FreshInstallLocationInitTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests (`TrackerServiceResumeTest`, `TrackerServiceWorkoutStartTest`, `TrackerServiceAsyncInitTest`, `FreshInstallLocationInitTest`) during Stage 4 construction, followed by the full clean-room unit test suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3980` allows full revert without affecting `sprint/2026-42.9` or `develop`.
