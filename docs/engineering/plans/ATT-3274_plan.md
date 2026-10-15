# Stage 3: Implementation Plan - ATT-3274: Crop start and end points of a recorded workout

**Ticket**: [ATT-3274](https://rainerblind.atlassian.net/browse/ATT-3274)  
**Sub-task**: [ATT-3587](https://rainerblind.atlassian.net/browse/ATT-3587) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3267](https://rainerblind.atlassian.net/browse/ATT-3267)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.4`  
**Requirement Mapping**: `REQ-UI-368`  
**Test Spec Mapping**: `TST-UI-331`  
**Branch**: `feature/ATT-3274`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Architectural Design & SWE.2 Specification

### 1.1 Architectural Motivation & Layer Separation
Athletes occasionally start workout tracking prematurely (e.g. idling in a parking lot with cold-start GPS jitter) or forget to stop tracking promptly upon finishing (e.g. driving home with tracking active). This corrupts summary metrics (`TIME_TOTAL_s`, `TIME_ACTIVE_s`, `DISTANCE_TOTAL_m`, `SPEED_AVERAGE_mps`, `ASCENDING`, `DESCENDING`), distorts sensor extrema, invalidates training zone distributions, and pollutes export files.

To support atomic, destructive cropping of leading and trailing samples without storage exhaustion, race conditions, floating-point subtraction drift, or UI thread freezes:

1. **Monolithic Single-Table Schema & Lockstep Multi-Sensor Alignment (`WorkoutSamplesDatabaseManager.java`)**:
   - In **aTrainingTracker**, the time-series schema is strictly **single-table-per-workout**: each workout owns exactly one SQLite table named `"<fileBaseName>"` inside `WorkoutSamples.db` (created by `WorkoutSamplesDatabaseManager`).
   - Every recorded 1 Hz row contains primary key `_id`, UTC `time`, all GPS columns (`LATITUDE`, `LONGITUDE`, `ALTITUDE`), all physiological sensor columns (`HEART_RATE`, `POWER`, `CADENCE`, `SPEED_mps`, `TEMPERATURE`), and all cumulative columns (`DISTANCE_m`, `TIME_TOTAL`, `TIME_ACTIVE`, `ASCENT`, `DESCENT`, `CALORIES`).
   - Because no separate normalized sensor tables exist, deleting unselected rows via `DELETE FROM "<fileBaseName>" WHERE _id < startRowId OR _id > endRowId` inherently and atomically prunes GPS, heart rate, cadence, power, speed, and environmental sensor streams in **absolute lockstep**, guaranteeing zero temporal misalignment across sensor channels.

2. **Domain Pure Math Engine, Pause Splitting & Training Zone Recalculation (`CropWorkoutMath.kt`, `ZoneDistributionCalculator.kt`)**:
   - Encapsulates all mathematical transformations, cumulative metric shifting with universal tolerance `EPSILON = 1e-6` clamping, zero-anchoring of row `startRowId`, chunked row-by-row `LINE_DISTANCE_m` Haversine recalculation (anchored to 0.0 for GPS-less sessions), and non-linear sensor extrema re-derivation.
   - **Inactivity & Pause-Boundary Splitting**:
     - `TIME_TOTAL` increments continuously every 1 Hz epoch; `TIME_ACTIVE` increments strictly when speed exceeds the auto-pause threshold.
     - When cropping leading samples up to `startRowId`, shifted cumulative active time at retained row $i$ is:
       $$T_{\text{active}}'(i) = \max(0, T_{\text{active}}(i) - T_{\text{active}}(startRowId))$$
     - If `startRowId` falls inside an auto-paused or stopped period, $T_{\text{active}}(i) - T_{\text{active}}(startRowId)$ remains `0` until movement resumes, correctly attributing 0 active time to that initial paused tail.
     - Session active time is derived as $T_{\text{active}}(endRowId) - T_{\text{active}}(startRowId)$ clamped within $[0, \text{totalDuration}]$. If $T_{\text{active}} < 1.0\text{ s}$, average speed is safely set to `0.0`.
   - **Heart Rate & Cycling Power Training Zone Distribution Re-derivation**:
     - Cropping alters total active time and intensity durations.
     - `WorkoutRepository` invalidates its in-memory zone distribution cache (`clearZoneDistributionCache(workoutId)`).
     - `ZoneDistributionCalculator` strictly re-derives time-in-zone (Z1–Z5) histograms and percentages exclusively from valid retained sample rows (`_id BETWEEN startRowId AND endRowId`) using the athlete's configured threshold values from `SettingsDataStoreJavaHelper.getZoneMax`.
   - Filters inputs via `Double.isFinite(v)`, discarding `NaN` and `Infinite` values. Sets extrema to `0.0` when a sensor stream is completely absent in the retained window.
   - Enforces minimum duration ($\ge 1.0\text{ s}$) and index step guards ($\ge 2$ points).

3. **Performance & Batching Strategy (ANR Mitigation on Dispatchers.IO)**:
   - All crop calculations and database operations execute strictly off the main thread on `Dispatchers.IO`.
   - **Chunked Batching for `LINE_DISTANCE_m` Recalculation**:
     - For large workouts ($> 5{,}000$ points, e.g. 5–10 hour endurance activities), updating `LINE_DISTANCE_m` row-by-row can create lock contention.
     - `WorkoutSamplesDatabaseManager` executes updates using a pre-compiled `SQLiteStatement` in chunked batches of `1,000` rows (`BATCH_SIZE = 1000`), invoking coroutine `yield()` between batches to guarantee responsive I/O and zero thread starvation.

4. **Database Management & Maintenance Layer (`WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`)**:
   - Executes inside native SQLite transactions (`beginTransaction()`) with standard rollback journals.
   - Prunes sample rows outside `[startRowId, endRowId]`.
   - Shifts cumulative columns, updates `LINE_DISTANCE_m` in chunked batches, and updates `LAP_NR` in lockstep.
   - Validates post-op row checksum: `remainingRows == expectedRetainedRowCount && deletedRows == expectedDeletedRowCount && remainingRows >= 2`. Throws `IllegalStateException` on checksum mismatch to trigger automatic SQLite rollback.
   - In `LapsDatabaseManager`:
     - Discards all laps completely outside `[startRowId, endRowId]`.
     - For boundary laps spanning the crop cuts (original Lap $M$ and Lap $N$), recalculates duration, distance, and average speed strictly from retained samples belonging to those respective laps.
     - Updates `TIME_START` of new Lap 1 to match the timestamp of `startRowId`.
     - Strictly preserves user-authored `NAME` and `DESCRIPTION` notes verbatim from original parent laps without truncation or heuristic merging.
     - Re-indexes sequential lap numbers strictly contiguously $1..K$ without gaps in lockstep across `Laps.TABLE` and `WorkoutSamples` (`UPDATE "<fileBaseName>" SET LAP_NR = (LAP_NR - lapOffset + 1)`), eliminating orphaned laps and foreign-key mismatches.
   - In `WorkoutSummariesDatabaseManager`: updates summary scalars, encoded polyline/stream blobs, bounding boxes, and replaces `TABLE_EXTREMA_VALUES`.
   - **Post-Commit Database Maintenance (Incremental Vacuum)**:
     - After successful transaction commit, if the deleted row count exceeds 20% of the original table size (`deletedRows > 0.20 * totalRows`), `WorkoutSamplesDatabaseManager` runs `PRAGMA incremental_vacuum` in the background on `Dispatchers.IO` to reclaim unallocated pages and eliminate file fragmentation.

5. **Repository & Transaction Orchestration Layer (`WorkoutRepository.kt`)**:
   - **Process-Wide Concurrency Mutex**: Defines `companion object { val cropMutex = Mutex() }` (Process Singleton) shared with `ExportManager` to guarantee that background export workers never read `WorkoutSamples.db` while a crop transaction is executing.
   - **Dynamic Storage Pre-Flight Check & Disk-Full Handling**:
     - Inspects internal storage via `StatFs(context.filesDir.path).availableBytes`.
     - Requires available space $\ge \max(50\text{ MB}, 5 \times \text{dbFileSize})$ to safely accommodate SQLite rollback journal spikes and temp pages during multi-hour high-frequency workout transactions.
     - **Disk Full Scenario (`SQLiteFullException`)**: If disk space is exhausted during write operations, the `catch` block catches `SQLiteFullException`, immediately calls `endTransaction()` (triggering SQLite rollback and clearing rollback journal files), purges WAL/journal remnants, removes temporary backup files, releases `cropMutex`, and surfaces `R.string.crop_insufficient_storage` to the user.
   - Writes an ephemeral versioned JSON snapshot to `context.cacheDir/workout_crop_backup_<workoutId>_<timestamp>.json` inside a `try ... finally` block, purging the temporary file upon transaction commit.
   - **Startup Crash & Process-Death Recovery**: On repository initialization, `WorkoutRepository.reconcilePendingCropRecovery()` inspects `context.cacheDir` for `workout_crop_backup_*.json`. If a stale backup from an aborted/killed process is detected, it logs an audit warning, purges the orphaned file, and triggers `reloadWorkoutData()` to ensure memory state matches SQLite.
   - Post-commit, invalidates in-memory cache and reloads `WorkoutData` directly from SQLite.

6. **Export Synchronization Layer (`ExportManager.java`)**:
   - Integrates with `WorkoutRepository.cropMutex`: Background exports acquire or observe `cropMutex`, preventing concurrent read/write collisions.
   - Pre-commit: Synchronously cancels running export work (`cancelUniqueWork("export_" + fileBaseName)`).
   - **Defensive File Invalidation with Locked-Handle Protection**: Wraps cached export file (`.tcx`, `.fit`, `.gpx`, `.csv`, `.json`) deletion in robust `try-catch` blocks. If an active file handle is locked by an in-flight background reader, logs a warning, retries after a brief yield, and flags the file for overwrite rather than allowing file I/O errors to crash or corrupt database transactions.
   - Marks `SyncStatusDatabaseManager` / `ExportStatusDatabaseManager` as `PENDING_UPDATE` / `WAITING`.
   - Post-commit: Enqueues fresh export tasks via `WorkManager.enqueueUniqueWork("export_" + fileBaseName, ExistingWorkPolicy.REPLACE, exportRequest)`.
   - Fallback: If `enqueueUniqueWork` throws, database records remain committed, status transitions to `ExportStatus.ERROR`, and a "Force Re-Export" button is displayed in the UI.

7. **ViewModel Layer (`CropWorkoutViewModel.kt`, `EditWorkoutViewModel.kt`)**:
   - Exposes `isCroppingInProgress: StateFlow<Boolean>`, `cropRange: StateFlow<ClosedFloatingPointRange<Float>>`, and `isCropValid: StateFlow<Boolean>`.
   - Dispatches crop execution on `Dispatchers.IO` within `viewModelScope`.

8. **UI Layer (`CropWorkoutScreen.kt`, `EditWorkoutScreen.kt`)**:
   - Provides entry button ("Track zuschneiden" / "Crop workout") in `EditWorkoutScreen.kt`.
   - Renders interactive dual-handle `RangeSlider` with minimum handle separation (`endIndex - startIndex >= minIndexStep`).
   - Renders synchronized map preview (`PathPreviewMap` with solid retained line and dashed dimmed trimmed segments) and elevation profile preview.
   - Displays live preview of trimmed offsets, new distance, and new duration.
   - Disables "Crop" button when duration $< 1.0\text{ s}$ or `isCroppingInProgress == true`.
   - Prompts with an irreversible destructive confirmation `AlertDialog` before executing.
   - Intercepts system back-presses via `BackHandler(enabled = isCroppingInProgress)`.

---

## 2. UI Consistency (Rule 23)

* **Closest Existing Reference Screen / Component**:
  - `EditWorkoutScreen.kt` (`AppModalBottomSheet` / dialog hosting edit fields).
  - `PathPreviewMap.kt` and `ElevationProfile.kt` (map and elevation profile previews).
  - `GlobalTelemetryZoomToolbar.kt` and `RangeSlider` patterns in `ui/map/` and `ui/components/`.
* **Reused Components & Icons**:
  - `AppModalBottomSheet` or fullscreen Dialog container.
  - Material 3 `RangeSlider` with custom range track styling.
  - `AlertDialog` with `AppDialogActions.SaveCancel` or explicit confirm/dismiss buttons.
  - `CircularProgressIndicator` and `LinearProgressIndicator` for in-progress transaction feedback.
  - Material Icons: `Icons.Default.ContentCut` (Crop icon), `Icons.Default.Warning`, `Icons.Default.Refresh`.
* **Theme Tokens**:
  - **Shape**: `RoundedCornerShape(12.dp)` for cards and dialog containers per `docs/design_guidelines.md`.
  - **Colors**:
    - Retained polyline: `MaterialTheme.colorScheme.primary` or `TTColor.RouteSelected`.
    - Trimmed polyline: `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)` with dashed stroke (`Dash(15f), Gap(15f)`).
    - Destructive action: `MaterialTheme.colorScheme.error` container/text for irreversible deletion confirmation.
    - Surfaces: `MaterialTheme.colorScheme.surface`, `surfaceContainer`, and `onSurface`.
  - **Typography**: `MaterialTheme.typography.titleMedium` (headers), `bodyMedium` (descriptions), `labelSmall` / `bodySmall` (live preview readouts).
  - **Spacing & Touch Targets**: Standard `16.dp` horizontal padding, `8.dp` inter-item vertical spacing, $\ge 48\text{ dp}$ minimum touch targets on all buttons and slider handles.
* **Justification for New One-Off Styles**:
  - Zero new one-off styles. Reuses canonical Material 3 tokens, `RoundedCornerShape(12.dp)`, and standard `TTColor` palette.

---

## 3. Invariants & Safety Measures

1. **Monolithic Table & Lockstep Multi-Sensor Alignment Invariant**:
   - In `WorkoutSamples.db`, every workout is stored in a single table named `"<fileBaseName>"`.
   - Pruning `WHERE _id < startRowId OR _id > endRowId` trims GPS, heart rate, power, cadence, and speed in absolute lockstep at the exact same row indices.
2. **Universal `EPSILON = 1e-6` Cumulative Monotonic Clamping & Zero-Anchoring Invariant**:
   - `startRowId` row is explicitly zero-anchored (`0.0` / `0`).
   - For all subsequent rows, shifted cumulative metrics (`DISTANCE_m`, `TIME_TOTAL`, `TIME_ACTIVE`, `ASCENT`, `DESCENT`, `CALORIES`) with $|v'| < 10^{-6}$ or $v' < 0$ are clamped to `0.0` / `0`.
   - Inactive periods split by `startRowId` maintain $T_{\text{active}} = 0$ until movement resumes.
3. **Training Zone Recalculation & Invalidation Invariant**:
   - In-memory Heart Rate and Power zone distribution caches are purged upon crop commit.
   - `ZoneDistributionCalculator` re-derives time-in-zone allocations strictly from retained sample rows using the athlete's configured thresholds.
4. **Performance & Chunked Batching Invariant (ANR Protection)**:
   - Operations run on `Dispatchers.IO`. `LINE_DISTANCE_m` recalculation executes in batches of `1,000` rows using pre-compiled `SQLiteStatement`s with `yield()` calls.
5. **Native SQLite Transaction & Checksum Assertion Invariant**:
   - In-place row deletion and updates operate within `beginTransaction()` / `endTransaction()`.
   - Post-op row count checksum (`remainingRows == expectedRetainedRowCount && remainingRows >= 2`) verifies data integrity before commit.
6. **Dynamic Storage Pre-Flight Check ($\ge \max(50\text{ MB}, 5 \times \text{dbFileSize})$) & Disk-Full Rollback Invariant**:
   - Requires dynamic storage headroom via `StatFs` before starting.
   - On `SQLiteFullException`, `endTransaction()` rolls back all modifications and releases rollback journal locks cleanly.
   - Background incremental vacuum is executed post-commit if deleted rows exceed 20% of original rows.
   - Startup reconciliation cleans up aborted process remnants.
7. **Deterministic Lap Mapping, Boundary Lap Handling & Contiguous Note Preservation Invariant**:
   - Retained lap $k \in 1..K$ strictly retains original `name` and `description` notes without truncation or heuristic merging.
   - Boundary laps spanning crop borders have their metrics recalculated from retained samples. Fully pruned laps are completely deleted.
   - Sequential re-indexing to $1..K$ is strictly contiguous without gaps across `Laps.TABLE` and `WorkoutSamples`.
8. **Defensive Export Invalidation, Handle-Lock Protection & Concurrency Invariant**:
   - `ExportManager` respects process-singleton `WorkoutRepository.cropMutex`, preventing background exports from reading during crop execution.
   - Cached files (`.tcx`, `.fit`, `.gpx`, `.csv`, `.json`) are deleted pre-commit inside `try-catch` blocks handling file locks.
   - Fresh export tasks are enqueued post-commit via `WorkManager.enqueueUniqueWork(..., ExistingWorkPolicy.REPLACE)`.
   - If `WorkManager` enqueue fails, `ExportStatus.ERROR` is set, and a "Force Re-Export" button is displayed.
9. **Irreversible Confirmation & UI Concurrency Lock Invariant**:
   - Explicit `AlertDialog` warns that pruning is permanent.
   - `isCroppingInProgress == true` disables UI controls and activates `BackHandler` interception.

---

## 4. Bite-Sized Atomic Implementation Steps

### Step 1: Add 9-Locale String Resources (`strings.xml`)
- Add crop strings to all 9 locale directories:
  - `crop_workout_title`: "Track zuschneiden" (DE) / "Crop workout" (EN) / ...
  - `crop_workout_action`: "Zuschneiden" / "Crop"
  - `crop_workout_confirm_title`: "Track dauerhaft zuschneiden?" / "Permanently crop workout?"
  - `crop_workout_confirm_message`: "Möchtest du den Track wirklich zuschneiden? Die Daten außerhalb des gewählten Bereichs werden dauerhaft gelöscht. Dieser Vorgang kann nicht rückgängig gemacht werden." / "Are you sure you want to crop this workout? Data outside the selected range will be permanently deleted. This action cannot be undone."
  - `crop_workout_confirm_action`: "Unwiderruflich zuschneiden" / "Crop permanently"
  - `crop_workout_progress`: "Track wird zugeschnitten..." / "Cropping workout..."
  - `crop_workout_success`: "Track erfolgreich zugeschnitten" / "Workout cropped successfully"
  - `crop_insufficient_storage`: "Nicht genügend freier Speicherplatz zum Zuschneiden (mindestens 50 MB erforderlich)." / "Insufficient storage to crop workout (at least 50 MB required)."
  - `crop_export_failed_retry`: "Neuerstellung der Exportdateien fehlgeschlagen. Bitte erneut versuchen." / "Failed to recreate export files. Please retry."
  - `crop_force_reexport`: "Export wiederholen" / "Force Re-Export"
- **Target Files**: `app/src/main/res/values*/strings.xml` (all 9 canonical locales)
- **Verification Command**: `./gradlew testDebugUnitTest --tests "*TranslationParityTest*"`

### Step 2: Pure Crop Math & Telemetry Aggregation Engine (`CropWorkoutMath.kt`)
- Create `app/src/main/java/com/atrainingtracker/trainingtracker/domain/crop/CropWorkoutMath.kt`:
  - `const val EPSILON = 1e-6`
  - `fun zeroAnchorAndShiftCumulative(values: List<Double>, startIndex: Int): List<Double>`
  - `fun zeroAnchorAndShiftCumulativeLong(values: List<Long>, startIndex: Int): List<Long>`
  - `fun recalculateLineDistance(latLngs: List<LatLng?>, startIndex: Int): List<Double>`
  - `fun recalculateExtrema(values: List<Double?>): CropExtremaResult`
  - `fun isCropValid(startIndex: Int, endIndex: Int, totalPoints: Int, durationSec: Double, minIndexStep: Int = 1): Boolean`
  - `fun calculateAverageSpeed(distanceMeters: Double, activeTimeSec: Int): Double`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/domain/crop/CropWorkoutMath.kt`
- **Verification Command**: `./gradlew testDebugUnitTest --tests "*CropWorkoutMathTest*"`

### Step 3: Database Pruning, Batch Updates, Partial Lap Handling & Maintenance Methods
- In `WorkoutSamplesDatabaseManager.java`:
  - Implement `pruneAndShiftSamples(String fileBaseName, long startRowId, long endRowId, int expectedRetainedRows, int lapOffset, LatLng newStartLatLng)`:
    - Runs in SQLite transaction on `Dispatchers.IO`.
    - Monolithic table atomic pruning: `DELETE FROM "<fileBaseName>" WHERE _id < ? OR _id > ?`.
    - Zero-anchors row `startRowId`, shifts cumulative fields with `EPSILON = 1e-6` clamping (handling inactivity splits).
    - Recalculates `LINE_DISTANCE_m` in chunked batches of `1,000` rows using pre-compiled `SQLiteStatement`s with `yield()` calls.
    - Updates `LAP_NR = (LAP_NR - lapOffset + 1)`.
    - Asserts row count checksum (`remainingRows == expectedRetainedRows && remainingRows >= 2`).
  - Implement `vacuumIfFragmented(String fileBaseName, int deletedRows, int totalRows)`:
    - If `deletedRows > 0.20 * totalRows`, executes `PRAGMA incremental_vacuum;`.
- In `LapsDatabaseManager.java`:
  - Implement `replaceCroppedLaps(long workoutId, List<LapData> recalculatedLaps)`:
    - Deletes existing laps for `workoutId`.
    - Inserts recalculated laps with preserved `name` and `description` notes and strictly contiguous sequential `lapNr = 1..K`.
    - Handles boundary laps (updating start time and calculating metrics strictly from retained samples).
    - Discards laps that are completely pruned out of existence.
- In `WorkoutSummariesDatabaseManager.java`:
  - Implement `updateCroppedWorkoutSummary(long workoutId, ContentValues summaryValues, Map<SensorType, ExtremumValues> newExtrema)`:
    - Updates `WorkoutSummaries.TABLE` scalars, encoded streams, and bounding boxes.
    - Replaces rows in `WorkoutSummaries.TABLE_EXTREMA_VALUES`.
- **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSamplesDatabaseManager.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/LapsDatabaseManager.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`
- **Verification Command**: `./gradlew testDebugUnitTest --tests "*CropLapsRecalculationTest*"`

### Step 4: Repository Crop Transaction, Storage Pre-Flight, Startup Recovery & Export Synchronization
- In `ExportManager.java`:
  - Add helper `invalidateWorkoutExport(String fileBaseName)`:
    - Cancels unique work `"export_" + fileBaseName`.
    - Deletes cached export files (`.tcx`, `.fit`, `.gpx`, `.csv`, `.json`) inside defensive `try-catch` blocks handling file locks.
    - Marks export statuses in database as `PENDING_UPDATE` / `WAITING`.
  - Respect `WorkoutRepository.cropMutex` during background export execution.
- In `WorkoutRepository.kt`:
  - Define `companion object { val cropMutex = Mutex() }` (Process Singleton).
  - Implement startup check `reconcilePendingCropRecovery()` in initialization.
  - Implement `suspend fun cropWorkout(workoutId: Long, startIndex: Int, endIndex: Int): Result<Unit>`:
    - Checks `StatFs` ($\ge \max(50\text{ MB}, 5 \times \text{dbFileSize})$).
    - Acquires `cropMutex`.
    - Pre-commit: Invalidate export cache via `exportManager.invalidateWorkoutExport`.
    - Writes ephemeral JSON backup to `cacheDir` inside `try { ... } finally { ... }`.
    - Catches `SQLiteFullException` and general exceptions to guarantee `endTransaction()` rollback and journal cleanup.
    - Purges ephemeral JSON backup on success.
    - Clears zone distribution cache via `clearZoneDistributionCache(workoutId)`.
    - Invalidates in-memory cache and reloads workout via `reloadWorkoutData(workoutId)`.
    - Enqueues fresh export via `exportManager.exportWorkout(updatedWorkoutData)`.
    - Triggers `samplesDatabaseManager.vacuumIfFragmented(...)` on IO thread if deleted rows $> 20\%$.
    - If `exportWorkout` throws, sets `ExportStatus.ERROR` without rolling back committed database.
- **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/ExportManager.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
- **Verification Command**: `./gradlew testDebugUnitTest --tests "*WorkoutRepositoryCropTest*"`

### Step 5: Crop ViewModel & UI State Management (`CropWorkoutViewModel.kt`)
- Create `CropWorkoutViewModel.kt` in `com.atrainingtracker.trainingtracker.ui.aftermath.crop`:
  - Holds `val isCroppingInProgress = MutableStateFlow(false)`.
  - Holds `val startIndex = MutableStateFlow(0)`.
  - Holds `val endIndex = MutableStateFlow(0)`.
  - Calculates `isCropValid = combine(...) { ... }` enforcing `CropWorkoutMath.isCropValid`.
  - Exposes `fun executeCrop(onSuccess: () -> Unit, onError: (Int) -> Unit)`.
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/crop/CropWorkoutViewModel.kt`
- **Verification Command**: `./gradlew testDebugUnitTest --tests "*CropWorkoutScreenContractTest*"`

### Step 6: Interactive Crop Subview, Range Scrubber & Confirmation Dialog
- Create `CropWorkoutScreen.kt` in `com.atrainingtracker.trainingtracker.ui.aftermath.crop`:
  - Material 3 `RangeSlider` with minimum handle distance constraint (`endIndex - startIndex >= minIndexStep`).
  - Preview map (`PathPreviewMap`) with solid retained polyline and dimmed dashed trimmed segments.
  - Preview elevation profile with highlighted interval.
  - Live metric summary: Start trim, End trim, New distance (`DistanceFormatter`), New duration (`DateUtils.formatElapsedTime`).
  - Irreversible confirmation `AlertDialog` before executing crop.
  - `BackHandler(enabled = isCroppingInProgress)` blocking navigation during execution.
- In `EditWorkoutScreen.kt`:
  - Add action button "Track zuschneiden" (`Icons.Default.ContentCut`) which opens `CropWorkoutScreen` or `CropWorkoutDialog`.
- In `WorkoutDetailsScreen` / Aftermath menu:
  - Add "Force Re-Export" button visible when `exportStatus == ExportStatus.ERROR`.
- **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/crop/CropWorkoutScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt`
- **Verification Command**: `./gradlew testDebugUnitTest --tests "*CropWorkoutScreenContractTest*"`

### Step 7: Targeted Unit, Contract, Rollback & Localization Test Suite
- Author comprehensive unit tests:
  - `CropWorkoutMathTest.kt`:
    - 2-point boundary conditions (`minIndexStep`).
    - Non-linear extrema sanitization and $0.0$ fallback.
    - Pause-split cumulative active time calculations.
  - `CropLapsRecalculationTest.kt`:
    - Partial boundary lap truncation (Lap $M$ and $N$).
    - Completely pruned lap removal ($< M$ and $> N$).
    - Strictly contiguous sequential lap indexing ($1..K$).
    - User note verbatim preservation.
  - `WorkoutRepositoryCropTest.kt`:
    - Dynamic $\ge \max(50\text{ MB}, 5 \times \text{dbFileSize})$ storage pre-flight check.
    - `testDiskFullDuringTransactionRollback()`: `SQLiteFullException` rollback & journal release.
    - `testProcessDeathDuringTransactionRestoration()`: Startup recovery from aborted ephemeral snapshot.
    - Process-singleton `cropMutex` serialization across Repository and ExportManager.
    - Incremental vacuum execution when deleted rows $> 20\%$.
    - Zone distribution cache invalidation and re-derivation.
  - `CropWorkoutScreenContractTest.kt`:
    - UI enablement, confirmation dialog contract, and `BackHandler`.
  - `CropWorkoutLocalizationTest.kt`:
    - 9-locale parity verification.
- **Verification Command**: `./gradlew testDebugUnitTest --tests "*Crop*"`

---

## 5. Gate 3 Readiness Verification
- All functional requirements, edge cases, and auditor recommendations mapped to `REQ-UI-368` and `TST-UI-331`.
- Clean separation across pure domain math, SQLite transaction management, repository caching, export synchronization, and Compose UI.
- All invariants strictly safeguarded: monolithic single-table multi-sensor lockstep pruning, pause-split active time calculation, dynamic $\ge \max(50\text{ MB}, 5 \times \text{dbFileSize})$ storage check, `SQLiteFullException` rollback, chunked batching for ANR protection, training zone re-derivation, incremental vacuum maintenance, startup crash reconciliation, partial/empty lap boundary handling, defensive export file deletion with handle lock protection, and irreversible destructive confirmation dialog.
