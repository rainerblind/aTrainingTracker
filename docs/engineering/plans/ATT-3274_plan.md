# Stage 3: Implementation Plan - ATT-3274: Crop start and end points of a recorded workout (Revision: Nested Scroll Constraint Fix)

**Ticket**: [ATT-3274](https://rainerblind.atlassian.net/browse/ATT-3274)  
**Sub-task**: [ATT-3722](https://rainerblind.atlassian.net/browse/ATT-3722) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3267](https://rainerblind.atlassian.net/browse/ATT-3267) (*Workout Analysis, Streams & Data Management*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Requirement Mapping**: `REQ-UI-368`  
**Test Spec Mapping**: `TST-UI-331`  
**Branch**: `feature/ATT-3274`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Architectural Design & SWE.2 Specification

### 1.1 Architectural Motivation & Layer Separation
Athletes occasionally start workout tracking prematurely (e.g. idling in a parking lot with cold-start GPS jitter) or forget to stop tracking promptly upon finishing (e.g. driving home with tracking active). This corrupts summary metrics (`TIME_TOTAL_s`, `TIME_ACTIVE_s`, `DISTANCE_TOTAL_m`, `SPEED_AVERAGE_mps`, `ASCENDING`, `DESCENDING`), distorts sensor extrema, invalidates training zone distributions, and pollutes export files.

During physical device verification on Google Pixel 10 in Sprint 2026-42.4 review, opening `CropWorkoutScreen` caused an immediate fatal crash as soon as sample points finished loading:
```
java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints, which is disallowed.
```
This is caused by nesting a second `Modifier.verticalScroll(rememberScrollState())` inside `AppModalBottomSheet`'s native outer vertical scroll container (`scrollable = true`).

To permanently resolve this defect while maintaining atomic sample pruning, mathematical zero-anchoring, and export synchronization:

1. **Monolithic Single-Table Schema & Lockstep Multi-Sensor Alignment (`WorkoutSamplesDatabaseManager.java`)**:
   - In **aTrainingTracker**, the time-series schema is strictly **single-table-per-workout**: each workout owns exactly one SQLite table named `"<fileBaseName>"` inside `WorkoutSamples.db` (created by `WorkoutSamplesDatabaseManager`).
   - Every recorded 1 Hz row contains primary key `_id`, UTC `time`, all GPS columns (`LATITUDE`, `LONGITUDE`, `ALTITUDE`), all physiological sensor columns (`HEART_RATE`, `POWER`, `CADENCE`, `SPEED_mps`, `TEMPERATURE`), and all cumulative columns (`DISTANCE_m`, `TIME_TOTAL`, `TIME_ACTIVE`, `ASCENT`, `DESCENT`, `CALORIES`).
   - Deleting unselected rows via `DELETE FROM "<fileBaseName>" WHERE _id < startRowId OR _id > endRowId` inherently and atomically prunes GPS, heart rate, cadence, power, speed, and environmental sensor streams in **absolute lockstep**, guaranteeing zero temporal misalignment across sensor channels.

2. **Domain Pure Math Engine, Pause Splitting & Training Zone Recalculation (`CropWorkoutMath.kt`, `ZoneDistributionCalculator.kt`)**:
   - Encapsulates all mathematical transformations, cumulative metric shifting with universal tolerance `EPSILON = 1e-6` clamping, zero-anchoring of row `startRowId`, chunked row-by-row `LINE_DISTANCE_m` Haversine recalculation (anchored to 0.0 for GPS-less sessions), and non-linear sensor extrema re-derivation.
   - Enforces minimum duration ($\ge 1.0\text{ s}$) and index step guards ($\ge 2$ points).

3. **Performance & Batching Strategy (ANR Mitigation on Dispatchers.IO)**:
   - All crop calculations and database operations execute strictly off the main thread on `Dispatchers.IO`.
   - `WorkoutSamplesDatabaseManager` executes updates using a pre-compiled `SQLiteStatement` in chunked batches of `1,000` rows (`BATCH_SIZE = 1000`), invoking coroutine `yield()` between batches.

4. **Database Management, Single SQLite Transaction & ACID Rollback (`WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`)**:
   - **Single Atomic Transaction**: All mutating database operations (sample pruning, cumulative metric shifting, lap re-indexing, summary updates, extrema replacement) execute within a single atomic `SQLiteTransaction` via `beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`.
   - **Zero Partial Deletion Invariant**: If any error, crash, power interruption, or assertion failure occurs before `setTransactionSuccessful()`, SQLite's rollback journal/WAL automatically aborts the entire transaction, restoring the database to its pristine pre-crop state. Partial sample deletions are strictly impossible.
   - Validates post-op row checksum: `remainingRows == expectedRetainedRowCount && deletedRows == expectedDeletedRowCount && remainingRows >= 2`. Throws `IllegalStateException` on mismatch to abort transaction.
   - Strictly preserves user-authored `NAME` and `DESCRIPTION` notes verbatim from original parent laps without truncation or heuristic merging.

5. **Repository & Transaction Orchestration Layer (`WorkoutRepository.kt`)**:
   - Defines process-wide `cropMutex = Mutex()`.
   - Requires available space $\ge \max(50\text{ MB}, 5 \times \text{dbFileSize})$ to safely accommodate SQLite rollback journals.
   - Writes an ephemeral versioned JSON snapshot in `context.cacheDir` inside a `try ... finally` block, purging the temporary file upon transaction commit.

6. **Export Synchronization & File-Lock Race Condition Prevention (`ExportManager.java`)**:
   - Integrates with `WorkoutRepository.cropMutex`: Background exports acquire or observe `cropMutex`, preventing concurrent read/write collisions.
   - **Cancellation & Active Handle Closure**:
     - When `cancelUniqueWork("export_" + fileBaseName)` is dispatched, running export workers receive a cooperative cancellation signal (`CancellationException`).
     - In `ExportManager`, export file writers are wrapped in strict `try-with-resources` / defensive `finally` blocks ensuring that file output streams (`FileOutputStream`, `ZipOutputStream`) are closed and flushed immediately upon cancellation, before file deletion begins.
     - Pre-commit cache deletion verifies file accessibility; if a file is momentarily locked by an external reader, deletion retries with backoff (50ms, 100ms) or schedules overwrite on next export, preventing file-lock contention from blocking the crop transaction.
   - Marks `SyncStatusDatabaseManager` / `ExportStatusDatabaseManager` as `PENDING_UPDATE` / `WAITING`.
   - Post-commit: Enqueues fresh export tasks via `WorkManager.enqueueUniqueWork("export_" + fileBaseName, ExistingWorkPolicy.REPLACE, exportRequest)`.

7. **UI Layer & Non-Conflicting Scrollable Sheet Layout (`CropWorkoutScreen.kt`)**:
   - **Strict Viewport Partitioning & Pinned Action Bar**:
     - `AppModalBottomSheet` explicitly partitions its layout into three distinct structural components:
       1. **Header**: Fixed at the top (title and close button).
       2. **Body**: Wrapped in `Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)`. This allocates all available vertical space between header and footer to the body and makes it scrollable.
       3. **Footer (`actions`)**: Fixed at the bottom, pinned above system navigation and home indicators, containing "Cancel" and "Crop" buttons. The action bar never scrolls off screen regardless of content height.
     - **Elimination of Nested Scroll Constraint Conflict**:
       - Because `AppModalBottomSheet` already provides the scrolling container for the body via `.verticalScroll(...)`, the inner root `Column` inside `CropWorkoutScreen.kt:140-145` MUST NOT declare `Modifier.verticalScroll(rememberScrollState())`.
       - Changing the inner `Column` to `Modifier.fillMaxWidth()` allows `AppModalBottomSheet`'s native scroll container to measure and scroll the map preview, dual-range slider, and telemetry summary cards cleanly, eliminating the `IllegalStateException` while keeping the action buttons permanently visible and pinned.
   - Dual-handle Material 3 `RangeSlider` with minimum handle separation (`endIndex - startIndex >= minIndexStep`).
   - Renders synchronized map preview (`PathPreviewMap` with solid retained line and dashed dimmed trimmed segments) and elevation profile preview.
   - Displays live preview of trimmed offsets, new distance, and new duration.
   - Prompts with an irreversible destructive confirmation `AlertDialog` before executing.
   - Intercepts system back-presses via `BackHandler(enabled = isCroppingInProgress)`.

---

## 2. UI Consistency (Rule 23)

* **Closest Existing Reference Screen / Component**:
  - `EditWorkoutScreen.kt` (`AppModalBottomSheet` hosting edit fields and actions).
* **Reused Components (`ui/components/core/`)**:
  - `AppModalBottomSheet.kt`: Standard modal bottom sheet component. By relying on its native `scrollable = true` implementation (`Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))`), `CropWorkoutScreen` inherits standard edge padding, bottom-sheet drag handling, pinned bottom action row, and consistent scroll physics.
* **Theme Tokens & Design System Alignment**:
  - Spacing: Standard `16.dp` vertical arrangement between content cards.
  - Colors: Primary polyline uses `MaterialTheme.colorScheme.primary`; trimmed preview uses dimmed dashed stroke.
  - Buttons: Standard Material 3 `TextButton` and `Button` with error container color for destructive action confirmation, pinned in the bottom action bar.

---

## 3. Invariants & Guardrails

1. **Zero Nested Scroll Violations**:
   - The root content `Column` of `CropWorkoutScreen.kt` must never apply `Modifier.verticalScroll`, eliminating runtime `IllegalStateException` crashes.
2. **Scrollability & Pinned Action Bar**:
   - Content scrolls smoothly between the top header and pinned bottom action buttons on small viewports ($360\text{ dp}$ width) via `AppModalBottomSheet`'s native scroll container.
3. **Single Transaction ACID Guarantee**:
   - All sample pruning, cumulative shifting, and summary updates execute in a single atomic transaction. No partial deletions can ever persist.
4. **Export Worker Handle Safety**:
   - Cancelled export tasks must close file streams before cache invalidation and re-export.
5. **Clean-Room Test Pass Rate**:
   - 100% of existing tests must pass with zero failures.

---

## 4. Atomic Implementation Steps

### Step 1: Remove Redundant Inner `.verticalScroll` in `CropWorkoutScreen.kt`
- Target file: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/crop/CropWorkoutScreen.kt`
- Change inner `Column` at line 140–145 from `Modifier.fillMaxWidth().verticalScroll(rememberScrollState())` to `Modifier.fillMaxWidth()`:
  ```kotlin
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(16.dp)
  )
  ```

### Step 2: Add Non-Conflicting Layout & Process Death Test Cases
- Target file: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/crop/CropWorkoutScreenContractTest.kt`
- Add test verifying that `CropWorkoutScreen.kt` content container does not declare inner `Modifier.verticalScroll`.
- Target file: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryCropTest.kt`
- Add test verifying that an exception or process interruption mid-transaction rolls back all database modifications cleanly without partial deletions.

### Step 3: Run Targeted Contract & Transaction Tests
- Command:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropWorkoutScreenContractTest --tests com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryCropTest
  ```

### Step 4: Run Clean-Room Regression Suite
- Command:
  ```bash
  ./gradlew testDebugUnitTest
  ```
