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

4. **Database Management & Maintenance Layer (`WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`)**:
   - Executes inside native SQLite transactions (`beginTransaction()`) with standard rollback journals.
   - Shifts cumulative columns, updates `LINE_DISTANCE_m` in chunked batches, and updates `LAP_NR` in lockstep.
   - Validates post-op row checksum: `remainingRows == expectedRetainedRowCount && deletedRows == expectedDeletedRowCount && remainingRows >= 2`.
   - Strictly preserves user-authored `NAME` and `DESCRIPTION` notes verbatim from original parent laps without truncation or heuristic merging.

5. **Repository & Transaction Orchestration Layer (`WorkoutRepository.kt`)**:
   - Defines process-wide `cropMutex = Mutex()`.
   - Requires available space $\ge \max(50\text{ MB}, 5 \times \text{dbFileSize})$ to safely accommodate SQLite rollback journals.
   - Writes an ephemeral versioned JSON snapshot in `context.cacheDir` inside a `try ... finally` block, purging the temporary file upon transaction commit.

6. **Export Synchronization Layer (`ExportManager.java`)**:
   - Integrates with `WorkoutRepository.cropMutex`: Background exports acquire or observe `cropMutex`.
   - Pre-commit: Synchronously cancels running export work (`cancelUniqueWork("export_" + fileBaseName)`).
   - Marks `SyncStatusDatabaseManager` / `ExportStatusDatabaseManager` as `PENDING_UPDATE` / `WAITING`.
   - Post-commit: Enqueues fresh export tasks via `WorkManager.enqueueUniqueWork("export_" + fileBaseName, ExistingWorkPolicy.REPLACE, exportRequest)`.

7. **UI Layer & Non-Conflicting Scrollable Sheet Layout (`CropWorkoutScreen.kt`)**:
   - Embedded inside `AppModalBottomSheet`: The inner root `Column` containing map preview, dual-range slider, and telemetry summary cards SHALL NOT apply `Modifier.verticalScroll(rememberScrollState())`.
   - `AppModalBottomSheet`'s native scroll container (`scrollable = true`) hosts the scrolling body, preventing nested scroll infinity height constraint violations (`java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`).
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
  - `AppModalBottomSheet.kt`: Standard modal bottom sheet component. By relying on its native `scrollable = true` implementation (`Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))`), `CropWorkoutScreen` inherits standard edge padding, bottom-sheet drag handling, and consistent scroll physics.
* **Theme Tokens & Design System Alignment**:
  - Spacing: Standard `16.dp` vertical arrangement between content cards.
  - Colors: Primary polyline uses `MaterialTheme.colorScheme.primary`; trimmed preview uses dimmed dashed stroke.
  - Buttons: Standard Material 3 `TextButton` and `Button` with error container color for destructive action confirmation.

---

## 3. Invariants & Guardrails

1. **Zero Nested Scroll Violations**:
   - The root content `Column` of `CropWorkoutScreen.kt` must never apply `Modifier.verticalScroll`, eliminating runtime `IllegalStateException` crashes.
2. **Scrollability on Small Viewports**:
   - Full content must remain smoothly scrollable on small screens ($360\text{ dp}$ width) via `AppModalBottomSheet`'s outer scroll container.
3. **Database & Export Integrity**:
   - Zero modifications to mathematical sample pruning, checksum validation, lap note preservation, or export invalidation routines.
4. **Clean-Room Test Pass Rate**:
   - 100% of existing tests must pass with zero failures.

---

## 4. Atomic Implementation Steps

### Step 1: Remove Redundant Inner `.verticalScroll` in `CropWorkoutScreen.kt`
- Target file: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/crop/CropWorkoutScreen.kt`
- Remove `.verticalScroll(rememberScrollState())` from line 143:
  ```kotlin
  // Before:
  Column(
      modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp)
  )

  // After:
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(16.dp)
  )
  ```

### Step 2: Add Non-Conflicting Layout Contract Test in `CropWorkoutScreenContractTest.kt`
- Target file: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/crop/CropWorkoutScreenContractTest.kt`
- Add test verifying that `CropWorkoutScreen.kt` does not contain inner `Modifier.verticalScroll` calls on its content column.

### Step 3: Run Targeted Contract & Math Tests
- Command:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropWorkoutScreenContractTest --tests com.atrainingtracker.trainingtracker.domain.crop.CropWorkoutMathTest
  ```

### Step 4: Run Clean-Room Regression Suite
- Command:
  ```bash
  ./gradlew testDebugUnitTest
  ```
