# Stage 2: Requirement & Test Specification (ATT-3980)

## 1. Traceability Links
- **Parent Ticket**: `ATT-3980` (`[Highest] Resumed workout writes all samples to a table named "null"`)
- **Stage 1 Analysis**: `docs/engineering/analysis/ATT-3980_analysis.md` (`ATT-4045`)
- **Stage 2 Subtask**: `ATT-4046`
- **Requirements Addressed**: `REQ-TRK-026`
- **Test Cases Specified**: `TST-TRK-018`

## 2. Formal Requirements

### `REQ-TRK-026`: Resumed Workout Sample Table Resolution, Asynchronous BANAL Binding Barrier, and Sample Write Error Surfacing in `TrackerService`
- **Source**: `ATT-3980`
- **Status**: `Specified`
- **Description**:
  1. **Sample Table Name Synchronization on Resume**: Whenever `TrackerService.applyResumedState(@NonNull ResumedWorkoutState state)` restores a resumed workout's `mBaseFileName` (whether synchronously during `onStartCommand()` or asynchronously inside `mBanalConnection.onServiceConnected()`), it MUST immediately recompute and assign `mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName)` so that `mSamplesTableName` is always synchronized with `mBaseFileName`.
  2. **Non-Null Guards for Sample Table Name Computation**:
     - `TrackerService.onStartCommand()` MUST NOT compute `mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName)` when `mBaseFileName` is `null` or blank.
     - `WorkoutSamplesDatabaseManager.getTableName(@NonNull String baseFileName)` MUST reject `null`, empty, or literal `"null"` (case-insensitive) input by throwing an `IllegalArgumentException`, preventing silent generation of `"\"null\""` table identifiers.
  3. **Asynchronous Resume Readiness Barrier (`mTableInitializationFuture`) & Ordered `onServiceConnected()`**:
     - At the start of `TrackerService.onStartCommand()` for `START_NORMAL`, `RESUME_BY_USER`, and `RESUME_SERVICE_RECREATION`, `mTableInitializationFuture` MUST be initialized as an incomplete `new CompletableFuture<>()` barrier and MUST only be completed (`mTableInitializationFuture.complete(null)`) inside `applyResumedState()` (for resume flows) or after normal startup database table creation completes (for `START_NORMAL`).
     - In `mBanalConnection.onServiceConnected()`, when `mStartType` is `RESUME_BY_USER` or `RESUME_SERVICE_RECREATION`, `recreateValuesWhenResuming()` MUST be executed **before** `mBanalService.createNewLap()` and `onSearchingFinished()` so that `mWorkoutID`, `mLapNr`, `mBaseFileName`, and `mSamplesTableName` are restored before lap increments or active tracking transitions occur.
     - For deferred resume flows (`mBanalService == null` during `onStartCommand()`), `notifyTrackingStarted(mWorkoutID)` MUST be invoked inside `applyResumedState()` once the valid `mWorkoutID` has been restored (guarded against a `null` base context in unit tests), rather than prematurely broadcasting `mWorkoutID == 0` in `onStartCommand()`.
     - In `mAltitudeCorrectionReceiver`, `mWorkoutID` and `mSamplesTableName` MUST be captured inside `mTableInitializationFuture.thenAcceptAsync(...)` so deferred resume completion is respected before reading those fields.
  4. **Explicit Error Surfacing & Diagnostic Telemetry on Sample Write Failure**:
     - `TrackerService.sampleAndWriteToDb()` MUST guard against a `null`, empty, or `"\"null\""` `mSamplesTableName`, logging `Log.e(TAG, ...)` and aborting the sample write rather than attempting SQL against an invalid table name.
     - In `TrackerService.handleMissingColumns()`, if `samplesDb.insertOrThrow(...)` fails for any reason other than a missing column (`!message.contains(" has no column named ")`), or if adding a missing column fails, `TrackerService` MUST log `Log.e(TAG, ...)` with the exception and table name and rethrow the `SQLiteException` rather than silently swallowing it and allowing `summariesDb.setTransactionSuccessful()` to commit an orphaned summary row count.

## 3. Acceptance Criteria (Given-When-Then)

### Scenario 1: Asynchronous Service Recreation (`RESUME_SERVICE_RECREATION` before `BANALService` is bound)
- **Given** a workout is active in `WorkoutSummariesDatabaseManager` with `workoutId = 42`, `BASE_FILE_NAME = "2026-06-15_100000"`, `SPORT_ID = 2`, and `LAPS = 3`
- **When** `TrackerService.onCreate()` and `TrackerService.onStartCommand(intent, 0, 1)` are invoked with `START_TYPE = RESUME_SERVICE_RECREATION` while `mBanalService` is still `null`, and `mBanalConnection.onServiceConnected()` subsequently fires with a valid `BANALService` binder
- **Then** `mSamplesTableName` MUST NOT be `"\"null\""` during `onStartCommand()`, `applyResumedState()` MUST set `mBaseFileName = "2026-06-15_100000"` and `mSamplesTableName = "\"2026-06-15_100000\""`, `mTableInitializationFuture` MUST transition from incomplete to completed upon `applyResumedState()`, `recreateValuesWhenResuming()` MUST run before `onSearchingFinished()`, and subsequent `sampleAndWriteToDb()` ticks MUST write samples into table `"\"2026-06-15_100000\""`.

### Scenario 2: Asynchronous User Resume (`RESUME_BY_USER` before `BANALService` is bound)
- **Given** a paused workout exists in `WorkoutSummariesDatabaseManager` with `workoutId = 99`, `BASE_FILE_NAME = "2026-06-15_143000"`, and `LAPS = 2`
- **When** `TrackerService.onStartCommand()` runs with `START_TYPE = RESUME_BY_USER` while `mBanalService` is `null`, and `mBanalConnection.onServiceConnected()` subsequently fires
- **Then** `recreateValuesWhenResuming()` MUST restore `mWorkoutID = 99`, `mLapNr = 2`, `mBaseFileName = "2026-06-15_143000"`, and `mSamplesTableName = "\"2026-06-15_143000\""` **before** `mBanalService.createNewLap()` increments the lap counter, and `mTableInitializationFuture` MUST complete with the valid table name ready.

### Scenario 3: Reject Invalid `baseFileName` in `WorkoutSamplesDatabaseManager.getTableName()`
- **Given** a caller invokes `WorkoutSamplesDatabaseManager.getTableName(baseFileName)`
- **When** `baseFileName` is `null`, `""`, `"   "`, or `"null"` / `"NULL"`
- **Then** `WorkoutSamplesDatabaseManager.getTableName()` MUST throw an `IllegalArgumentException` and NEVER return `"\"null\""`.

### Scenario 4: Surface Non-Column `SQLiteException` During Sample Insertion
- **Given** `TrackerService.sampleAndWriteToDb()` attempts to insert a sample into `samplesDb`
- **When** `samplesDb.insertOrThrow(...)` throws a `SQLiteException` whose message does not indicate a missing column (e.g., `no such table: "missing_table"`)
- **Then** `handleMissingColumns()` MUST log the error via `Log.e` and rethrow the `SQLiteException`, preventing `summariesDb.setTransactionSuccessful()` from committing a mismatched summary state when the sample insert failed.

## 4. Test Specification

### `TST-TRK-018`: Resumed Workout Sample Table Resolution, Asynchronous BANAL Binding Barrier & Sample Write Error Surfacing Verification Suite
- **Requirement**: `REQ-TRK-026`
- **Test Class**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`
- **Test Cases**:
  1. `testApplyResumedState_updatesSamplesTableNameAndCompletesFuture()`: Verifies that `applyResumedState(state)` sets `mBaseFileName = state.baseFileName`, updates `mSamplesTableName = "\"<baseFileName>\""`, and completes `mTableInitializationFuture`.
  2. `testDeferredResumeServiceRecreation_whenBanalServiceConnectsLater_restoresTableNameAndWritesSamplesToResumedTable()`: Simulates `onStartCommand(RESUME_SERVICE_RECREATION)` with `mBanalService == null` followed by `mBanalConnection.onServiceConnected()`; verifies `mSamplesTableName` is never `"\"null\""`, `mTableInitializationFuture` is incomplete before connection and completed after connection, and `sampleAndWriteToDb()` inserts rows into the resumed workout's sample table (`"\"2026-06-15_120000\""`).
  3. `testDeferredResumeByUser_whenBanalServiceConnectsLater_recreatesValuesBeforeCreateNewLapAndWritesSamples()`: Simulates `onStartCommand(RESUME_BY_USER)` with `mBanalService == null` followed by `mBanalConnection.onServiceConnected()`; verifies `recreateValuesWhenResuming()` runs before `createNewLap()` and `onSearchingFinished()`, and `mSamplesTableName` resolves to the resumed workout's table name.
  4. `testGetTableName_rejectsNullEmptyOrLiteralNullString()`: Verifies `WorkoutSamplesDatabaseManager.getTableName()` throws `IllegalArgumentException` for `null`, `""`, `"   "`, and `"null"`.
  5. `testSampleAndWriteToDb_whenTableMissing_rethrowsSQLiteExceptionAndDoesNotSilentlySwallow()`: Verifies that a `no such table` `SQLiteException` in `handleMissingColumns()` is not silently swallowed and prevents silent sample loss.

## 5. Localization & UI String Impact
- **Impact**: None. This fix operates strictly within background service state restoration, database table name resolution, and log diagnostics (`Log.e`). No user-facing string resources (`strings.xml`) across the 9 supported locales are added or modified.
