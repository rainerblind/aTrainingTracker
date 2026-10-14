# Stage 2: Requirement & Test Specification - ATT-3274: Crop start and end points of a recorded workout

**Ticket**: [ATT-3274](https://rainerblind.atlassian.net/browse/ATT-3274)  
**Sub-task**: [ATT-3586](https://rainerblind.atlassian.net/browse/ATT-3586) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-3267](https://rainerblind.atlassian.net/browse/ATT-3267)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.4`  
**Requirement Mapping**: `REQ-UI-368` (*Interactive Start/End Workout Cropping with Destructive Sample Pruning and Full Metric Recalculation*), complementing `REQ-EXP-001`, `REQ-EXP-016`, `REQ-TRK-001`, and `REQ-TRK-002`  
**Test Spec ID**: `TST-UI-331`  
**Branch**: `feature/ATT-3274`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-UI-368)

### 1.1 Problem Statement & Rationale
Athletes occasionally start workout tracking prematurely (e.g., waiting in a parking lot, stationary in a vehicle, or indoors with noisy cold-start GPS drift) or forget to stop tracking promptly upon finishing an activity (e.g., driving home with GPS recording active). Currently, **aTrainingTracker** lacks an in-app mechanism to trim these unwanted leading or trailing segments. Unwanted segments corrupt workout summary metrics (`TIME_TOTAL_s`, `TIME_ACTIVE_s`, `DISTANCE_TOTAL_m`, `ASCENDING`, `DESCENDING`, `SPEED_AVERAGE_mps`, `SPEED_MAX_mps`), distort sensor extrema (`ExtremumValues`), pollute track map bounding boxes (`BOUND_MIN_LAT..BOUND_MAX_LNG`), and contaminate export files (`.tcx`, `.fit`, `.gpx`, `.csv`, `.json`).

Providing an interactive, synchronized cropping subview (`CropWorkoutScreen.kt`) with dual-handle range scrubbing, coupled map and elevation previews, strict mathematical clamping, native SQLite transactional rollback, and synchronous export invalidation enables athletes to permanently prune erroneous trackpoints and regenerate pristine metrics and exports directly within the application.

### 1.2 Functional & Architectural Scope (`REQ-UI-368`)

1. **Pure Math Engine, Input Sanitization & Invariant Clamping (`CropWorkoutMath.kt`)**:
   - The system SHALL encapsulate all crop transformation calculations in a pure Kotlin helper object `CropWorkoutMath` in `com.atrainingtracker.trainingtracker.domain.crop`.
   - **Cumulative Monotonic Column Zero-Anchoring & Universal Clamping**:
     - For all cumulative monotonic fields (`DISTANCE_m`, `TIME_TOTAL`, `TIME_ACTIVE`, `ASCENT`, `DESCENT`, `CALORIES`), the newly designated start sample row (`_id == startRowId`) SHALL be explicitly zero-anchored to `0.0` (or `0` for integer columns).
     - For every subsequent retained row (`_id > startRowId`), shifted cumulative values $v' = v - v_0$ SHALL be clamped with universal tolerance `EPSILON = 1e-6`: any shifted value where $|v'| < 10^{-6}$ or $v' < 0.0$ SHALL be clamped to exact `0.0` (or `0`), preventing negative metrics and IEEE-754 subtraction drift.
   - **Row-by-Row Spatial Displacement Recalculation (`LINE_DISTANCE_m`)**:
     - `LINE_DISTANCE_m` SHALL be recalculated row-by-row for all retained samples (`_id BETWEEN startRowId AND endRowId`) using Haversine distance (`WorkoutClusterEngine.distanceBetween(newStartLatLng, rowLatLng)`), anchored to `0.0` at `_id == startRowId`.
     - For indoor and GPS-less workouts where coordinates are null, `LINE_DISTANCE_m` SHALL be explicitly set to `0.0` for all rows without invoking distance calculation.
   - **Sanitized Non-Linear Extrema & Sparse Telemetry Handling**:
     - Telemetry filtering SHALL sanitize inputs via `Double.isFinite(v)`, strictly discarding `NaN` and `Infinite` values.
     - Summary extrema (`MIN`, `MAX`, `AVG`, `START`, `END`) in `ExtremumValues` for instantaneous non-linear streams (`HR`, `POWER`, `CADENCE`, `SPEED_mps`, `ALTITUDE`, `TEMPERATURE`) SHALL be strictly re-derived from valid retained sample points.
     - If a sensor stream contains zero valid readings in the cropped window (e.g. sensor disconnected or only active in the pruned section), all scalar extrema fields (`MIN`, `MAX`, `AVG`, `START`, `END`) SHALL be explicitly set to `0.0` (with `0.0` coordinates), signaling `hasSensorData = false` to UI consumers.
   - **Boundary Duration & Safe Speed Guards**:
     - The cropped interval SHALL enforce a minimum duration guard: `croppedDurationSec >= 1.0` and `retainedRowCount >= 2`.
     - `SPEED_AVERAGE_mps` SHALL be calculated as `DISTANCE_TOTAL_m / TIME_ACTIVE_s` if `TIME_ACTIVE_s >= 1.0`, falling back to `0.0` otherwise, preventing division by zero.

2. **Native SQLite Transactions, Checksum Assertion & Rollback Safety**:
   - The database operations across `WorkoutSamplesDatabaseManager`, `LapsDatabaseManager`, and `WorkoutSummariesDatabaseManager` SHALL execute within native SQLite transactions (`beginTransaction()`) with standard rollback journals.
   - In `WorkoutSamplesDatabaseManager`:
     - Delete unselected samples: `DELETE FROM "<fileBaseName>" WHERE _id < ? OR _id > ?`.
     - Shift cumulative columns with `EPSILON = 1e-6` clamping and update `LINE_DISTANCE_m` row-by-row.
     - Re-index `LAP_NR` in lockstep: `UPDATE "<fileBaseName>" SET LAP_NR = (LAP_NR - ? + 1)`.
     - Verify post-op row-count checksum: `remainingRows == expectedRetainedRowCount && deletedRows == expectedDeletedRowCount && remainingRows >= 2`. If checksum fails, an `IllegalStateException` is thrown, triggering automatic transaction rollback.
   - In `LapsDatabaseManager`:
     - Delete old laps for `workoutId` and insert recalculated $K$ laps with preserved notes.
   - In `WorkoutSummariesDatabaseManager`:
     - Update `WorkoutSummaries.TABLE` scalars, encoded streams (`MAP_POLYLINE`, `DISTANCE_STREAM`, `ALTITUDE_STREAM`), bounding boxes (`BOUND_MIN_LAT..BOUND_MAX_LNG`), and replace `TABLE_EXTREMA_VALUES`.

3. **Deterministic Lap Metadata Mapping & Note Preservation (`LapsDatabaseManager.java`)**:
   - Laps completely outside the retained window (`[startRowId, endRowId]`) SHALL be discarded.
   - Each retained lap $k \in \{1 \dots K\}$ (where $K = N - M + 1$, with $M$ being the original start lap and $N$ being the original end lap) SHALL map directly to original lap $OrigIndex = M + k - 1$.
   - **Note Preservation**: Lap $k$ SHALL strictly preserve the exact user-authored `NAME` and `DESCRIPTION` from original Lap $OrigIndex$ without truncation, alteration, or heuristic merging.
   - Boundary laps $1$ and $K$ SHALL have their `TIME_START`, `TIME_TOTAL_s`, `DISTANCE_TOTAL_m`, and `SPEED_AVERAGE_mps` recalculated strictly from their retained sample subsets.
   - Sequential lap numbers SHALL be re-indexed `1..K` in lockstep across `Laps.TABLE` and `WorkoutSamples`.
   - Verification assertion: $\sum_{k=1}^K \text{DISTANCE\_TOTAL\_m}_k == \text{workout.DISTANCE\_TOTAL\_m}$ within `1e-6`.

4. **Pre-Flight Storage Verification & Ephemeral Backup Lifecycle (`WorkoutRepository.kt`)**:
   - `WorkoutRepository` SHALL inspect internal storage availability via `StatFs(context.filesDir.path).availableBytes`.
   - If available space is $< 10\text{ MB}$, the crop operation SHALL be rejected immediately with `CropResult.Error(R.string.crop_insufficient_storage)`.
   - Prior to executing the SQLite transaction, `WorkoutRepository` SHALL write an ephemeral versioned JSON snapshot to `context.cacheDir/workout_crop_backup_<workoutId>_<timestamp>.json` inside a `try ... finally` block.
   - Upon successful transaction commit, the temporary JSON file SHALL be purged in the `finally` block. If the transaction fails, at most 1 versioned file is retained for recovery.
   - `WorkoutRepository` SHALL maintain a coroutine mutex (`cropMutex`) to serialize crop operations and prevent concurrent data mutations.
   - Post-commit, `WorkoutRepository` SHALL invalidate its in-memory cache and reload `WorkoutData` directly from SQLite.

5. **Synchronous Export Invalidation, Cloud Sync & Failure Recovery (`ExportManager.java`)**:
   - Pre-commit: Synchronously cancel active export tasks via `WorkManager.getInstance(context).cancelUniqueWork("export_" + fileBaseName)` and delete cached export files (`.tcx`, `.fit`, `.gpx`, `.csv`, `.json`).
   - Cloud Sync: Update `SyncStatusDatabaseManager` / `ExportStatusDatabaseManager` to mark the workout status as `PENDING_UPDATE` / `WAITING`.
   - Post-commit: Enqueue fresh export work via `WorkManager.getInstance(context).enqueueUniqueWork("export_" + fileBaseName, ExistingWorkPolicy.REPLACE, exportRequest)`.
   - Failure Fallback: If `enqueueUniqueWork` throws or fails, committed database records remain intact, `ExportStatusDatabaseManager` marks the export as `ExportStatus.ERROR`, an error toast/message is displayed (`R.string.crop_export_failed_retry`), and a "Force Re-Export" button is displayed in the workout details menu (visible exclusively when `ExportStatus == ERROR`).

6. **Interactive Crop Subview, Range Scrubber & Destructive Confirmation (`CropWorkoutScreen.kt`)**:
   - Accessible via action button ("Track zuschneiden" / "Crop workout") in `EditWorkoutScreen.kt` (`EditWorkoutDialog`) for completed activities with $\ge 2$ samples.
   - Dual-handle Material 3 `RangeSlider` with minimum handle distance constraint (`endIndex - startIndex >= minIndexStep`, where `minIndexStep` corresponds to $\ge 1\text{ s}$), preventing handles from colliding or collapsing.
   - Track Map preview (`PathPreviewMap.kt`): The retained track segment (`[startIndex..endIndex]`) SHALL render as a solid primary polyline; trimmed leading and trailing segments SHALL render as dimmed, dashed polylines (`Dash(15f), Gap(15f)`).
   - Elevation Profile preview (`ElevationProfile.kt`): Renders dynamically with adaptive height (`calculateElevationProfileHeight(range)`), highlighting the retained interval.
   - Live Metric Previews: Dynamic readouts for Start trim offset, End trim offset, New total distance (`DistanceFormatter`), and New total duration (`DateUtils.formatElapsedTime`).
   - **Hard Boundary Pre-Condition Guard**: The "Crop" confirmation button SHALL be disabled unless `(endIndex - startIndex >= minIndexStep) && (croppedDurationSec >= 1.0) && (endTimestampMs - startTimestampMs >= 1000L) && !isCroppingInProgress`.
   - **Destructive Confirmation Dialog (`Coding Standards Sec 1.4`)**: Tapping "Crop" SHALL display an explicit `AlertDialog` warning that sample deletion is permanent and irreversible, advising athletes to use *Save as Route* if an untrimmed backup is desired.
   - **UI Concurrency Lock**: While `isCroppingInProgress == true`, `BackHandler(enabled = true)` SHALL intercept and block back-navigation, and a `CircularProgressIndicator` SHALL be displayed.

7. **9-Language Localization Parity**:
   - All crop UI strings, button labels, dialog messages, warnings, and errors SHALL maintain 100% parity across `values/` (EN), `values-de/` (DE), `values-es/` (ES), `values-fr/` (FR), `values-it/` (IT), `values-ja/` (JA), `values-nl/` (NL), `values-pl/` (PL), and `values-pt/` (PT).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-368`). Targets `CropWorkoutScreen.kt`, `CropWorkoutMath.kt`, `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `WorkoutRepository.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, and `ExportManager.java`. Complements `REQ-EXP-001` (*TCX/GPX/FIT Export*), `REQ-EXP-016` (*Export Queue Management*), `REQ-TRK-001` (*Tracking Session Storage*), and `REQ-TRK-002` (*Extremum Values Management*).
2. **Historical Origin & Commit Trace**: Ticket `ATT-3274`, Sprint `2026-42.4`, Epic `ATT-3267`.
3. **Root Reason for Existing Formulation**: Athletes frequently record extraneous track points prior to active movement or after arriving home. Currently, no in-app trimming tool exists, corrupting summary statistics, bounding boxes, and export files.
4. **Preservation of Core Invariants**: Native SQLite rollback journal safety, zero-anchoring with universal `EPSILON = 1e-6` cumulative clamping, lap user note preservation, synchronous export invalidation with unique work replacement, `isCroppingInProgress` UI locking, and 100% clean-room test pass rate remain strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Live Preview & Synchronized Range Scrubbing)**:
  * *Given* an athlete opens a completed workout with $\ge 2$ samples in `CropWorkoutScreen`,
  * *When* dragging the start or end slider handles,
  * *Then* the map polyline highlights the retained range while dimming trimmed segments, the elevation profile highlights the active interval, and live preview values for trimmed offsets, new distance, and new duration update continuously.
* **Criterion 2 (Hard Boundary Pre-Condition & Minimum Duration Guard)**:
  * *Given* an athlete adjusting the slider handles,
  * *When* the selected interval duration is $< 1.0\text{ s}$ or fewer than 2 points remain,
  * *Then* the "Crop" action button is visually disabled and cannot be tapped.
* **Criterion 3 (Destructive Confirmation Dialog Affirmation)**:
  * *Given* a valid crop range selected and the "Crop" button tapped,
  * *When* the confirmation dialog appears,
  * *Then* it displays an explicit irreversible warning informing the athlete that pruned trackpoints cannot be recovered and suggests *Save as Route* if an untrimmed backup is desired.
* **Criterion 4 (Pre-Flight Storage Verification & Insufficient Space Rejection)**:
  * *Given* an athlete confirming the crop operation on a device with $< 10\text{ MB}$ internal storage available,
  * *When* `WorkoutRepository.cropWorkout` executes,
  * *Then* the operation is rejected immediately, returning an error message (`crop_insufficient_storage`), leaving all database tables unmodified.
* **Criterion 5 (Universal `EPSILON = 1e-6` Cumulative Monotonic Clamping & Zero-Anchoring)**:
  * *Given* a valid crop operation with new start sample `startRowId`,
  * *When* cumulative columns (`DISTANCE_m`, `TIME_TOTAL`, `TIME_ACTIVE`, `ASCENT`, `DESCENT`, `CALORIES`) are recalculated,
  * *Then* row `startRowId` is anchored to `0.0` / `0`, and for subsequent rows, any shifted value $v - v_0$ where $|v - v_0| < 10^{-6}$ or negative is clamped to `0.0` / `0`, eliminating floating-point subtraction drift.
* **Criterion 6 (Row-by-Row `LINE_DISTANCE_m` Recalculation & GPS-less Session Fallback)**:
  * *Given* an outdoor workout with GPS coordinates,
  * *When* `LINE_DISTANCE_m` is recalculated,
  * *Then* each retained row receives its Haversine distance from `newStartLatLng` with row `startRowId` set to `0.0`;
  * *Given* an indoor workout without GPS coordinates,
  * *Then* `LINE_DISTANCE_m` is set to `0.0` for all rows without null pointer exceptions.
* **Criterion 7 (Deterministic Lap Mapping & User Note Preservation)**:
  * *Given* a workout with multiple laps where the crop start falls in original Lap $M$ and crop end falls in original Lap $N$,
  * *When* laps are re-indexed to $1..K$,
  * *Then* each retained lap $k$ strictly preserves the exact user-authored `NAME` and `DESCRIPTION` from original Lap $M + k - 1$, boundary laps have their metrics recalculated from retained samples, and `LAP_NR` is updated in lockstep across `Laps.TABLE` and `WorkoutSamples`.
* **Criterion 8 (Sanitized Extrema Re-derivation & Sparse Sensor Fallback)**:
  * *Given* retained samples containing sensor readings,
  * *When* summary extrema (`ExtremumValues`) are re-derived,
  * *Then* all non-finite values (`Double.isFinite`) are discarded, and if a sensor has zero valid readings in the cropped window, its scalar extrema (`MIN`, `MAX`, `AVG`, `START`, `END`) are explicitly set to `0.0`, signaling `hasSensorData = false`.
* **Criterion 9 (Native SQLite Transaction Rollback on Error)**:
  * *Given* an unexpected error or checksum failure during sample pruning or summary update,
  * *When* the transaction aborts,
  * *Then* SQLite rolls back all modifications cleanly, restoring pre-crop tables without orphan rows or corrupted state.
* **Criterion 10 (Synchronous Export Invalidation & Post-Commit Work Replacement)**:
  * *Given* a successfully committed crop transaction,
  * *When* export processing triggers,
  * *Then* previous export files (`.tcx`, `.fit`, `.gpx`, `.csv`, `.json`) are deleted pre-commit, active work is cancelled, and fresh export jobs are enqueued post-commit via `WorkManager.enqueueUniqueWork(..., ExistingWorkPolicy.REPLACE)`.
* **Criterion 11 (Export Enqueue Failure Fallback)**:
  * *Given* a post-commit failure where `WorkManager` rejects the enqueue request,
  * *When* the failure is handled,
  * *Then* database modifications remain committed, `ExportStatus` is set to `ERROR`, and a "Force Re-Export" button is displayed in workout details.
* **Criterion 12 (UI Concurrency Lock & Back-Navigation Interception)**:
  * *Given* a crop operation in progress (`isCroppingInProgress == true`),
  * *When* the user presses the system back button or attempts navigation,
  * *Then* `BackHandler` blocks navigation and interactive controls remain disabled until the transaction completes.

---

## 4. Test Specification (TST-UI-331)

### 4.1 Unit, Repository, ViewModel, & UI Test Cases

1. **Pure Crop Math & Telemetry Aggregation Unit Tests (`CropWorkoutMathTest.kt`)**:
   - Verify `zeroAnchorAndShiftCumulative`:
     - Asserts that row 0 of retained slice is anchored to `0.0`.
     - Asserts that values with small subtraction differences (e.g. $10^{-7}$) clamp to `0.0`.
     - Asserts that negative differences clamp to `0.0`.
   - Verify `recalculateLineDistance`:
     - Computes Haversine distance from start coordinate row-by-row.
     - Anchors start point to `0.0`.
     - Handles null lat/lng gracefully by returning `0.0` for all points (indoor sessions).
   - Verify `recalculateExtrema`:
     - Re-derives `MIN`, `MAX`, `AVG`, `START`, `END` from retained slice.
     - Discards `Double.NaN`, `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY`.
     - Sets all fields to `0.0` if sensor stream is completely absent in retained window.
   - Verify `isCropValid`:
     - Returns `false` if `startIndex >= endIndex`.
     - Returns `false` if `croppedDurationSec < 1.0`.
     - Returns `false` if `endIndex - startIndex < minIndexStep`.
     - Returns `true` for valid interval.

2. **Lap Recalculation & Metadata Preservation Unit Tests (`CropLapsRecalculationTest.kt`)**:
   - Verify deterministic lap interval mapping:
     - Lap $M$ to $N$ retained, laps $< M$ and $> N$ discarded.
     - Retained lap $k$ preserves original `name` and `description` verbatim.
   - Verify boundary lap recalculation:
     - Lap 1 start time equals first retained sample time.
     - Lap 1 and Lap $K$ duration and distance derived strictly from retained samples.
   - Verify checksum assertion:
     - $\sum \text{distance}_k == \text{totalDistance}$ within $10^{-6}$.
   - Verify lockstep `LAP_NR` re-indexing:
     - Re-indexes to $1..K$.

3. **Database Transaction & Rollback Recovery Tests (`WorkoutRepositoryCropTest.kt`)**:
   - Verify pre-flight storage check:
     - Rejects crop when `StatFs` reports $< 10\text{ MB}` available.
   - Verify transaction rollback on failure:
     - Simulates an exception during summary update; asserts `WorkoutSamples` and `Laps` tables roll back to pre-crop state.
   - Verify ephemeral backup lifecycle:
     - Asserts JSON cache snapshot created before transaction and deleted after successful commit inside `try ... finally`.
   - Verify export cache invalidation & replacement:
     - Pre-commit cache files deleted.
     - Post-commit `WorkManager.enqueueUniqueWork` enqueued with `ExistingWorkPolicy.REPLACE`.
   - Verify export enqueue failure fallback:
     - When `enqueueUniqueWork` throws, database stays committed, `ExportStatus` becomes `ERROR`.
   - Verify `cropMutex` serialization:
     - Concurrent crop calls execute sequentially without race conditions.

4. **ViewModel & UI Pre-Condition Contract Tests (`CropWorkoutScreenContractTest.kt`)**:
   - Verify "Crop" button enabled state:
     - Disabled when `isCroppingInProgress == true`.
     - Disabled when `isCropValid == false`.
     - Enabled when valid and not in progress.
   - Verify destructive confirmation dialog:
     - Renders irreversible warning text.
     - Confirm button triggers `viewModel.cropWorkout()`.
     - Dismiss button closes dialog without mutating data.
   - Verify `BackHandler`:
     - Enabled when `isCroppingInProgress == true`.
   - Verify "Force Re-Export" button:
     - Visible in workout details when `exportStatus == ExportStatus.ERROR`.

5. **9-Language Localization Audit (`CropWorkoutLocalizationTest.kt`)**:
   - Verify that all crop strings (`crop_workout_title`, `crop_workout_action`, `crop_workout_confirm_title`, `crop_workout_confirm_message`, `crop_workout_confirm_action`, `crop_workout_progress`, `crop_workout_success`, `crop_insufficient_storage`, `crop_export_failed_retry`, `crop_force_reexport`) exist, are non-empty, and maintain translation parity across:
     - `values/` (EN)
     - `values-de/` (DE)
     - `values-es/` (ES)
     - `values-fr/` (FR)
     - `values-it/` (IT)
     - `values-ja/` (JA)
     - `values-nl/` (NL)
     - `values-pl/` (PL)
     - `values-pt/` (PT)

### 4.2 Traceability Matrix

| Requirement ID | Test Spec ID | Test Class / Method | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-UI-368` | `TST-UI-331` | `CropWorkoutMathTest` | Cumulative zero-anchoring, universal `EPSILON = 1e-6` clamping, `LINE_DISTANCE_m` Haversine row recalculation & indoor fallback, non-linear extrema re-derivation, `Double.isFinite` filtering, sparse sensor stream $0.0$ fallback, duration $\ge 1.0\text{ s}$ & index step guards |
| `REQ-UI-368` | `TST-UI-331` | `CropLapsRecalculationTest` | Deterministic lap mapping ($M \dots N$), user-authored `NAME` & `DESCRIPTION` verbatim preservation, boundary lap recalculation, lockstep `LAP_NR = 1..K` re-indexing, lap distance checksum |
| `REQ-UI-368` | `TST-UI-331` | `WorkoutRepositoryCropTest` | Pre-flight `StatFs` $\ge 10\text{ MB}$ check, ephemeral JSON backup lifecycle in `try ... finally`, SQLite transaction rollback on exception, post-op checksum assert, `cropMutex` concurrency guard, synchronous export invalidation, `ExistingWorkPolicy.REPLACE` work enqueue, and export failure fallback |
| `REQ-UI-368` | `TST-UI-331` | `CropWorkoutScreenContractTest` | UI precondition button enablement, dual `RangeSlider` min thumb distance, `BackHandler(isCroppingInProgress)` lock, destructive confirmation `AlertDialog` contract, and "Force Re-Export" button visibility |
| `REQ-UI-368` | `TST-UI-331` | `CropWorkoutLocalizationTest` | 9-language localization parity across all crop UI strings in `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/` |
| `REQ-UI-368` | `TST-UI-331` | `TranslationParityTest` | Global project localization parity verification across all 9 locales |
