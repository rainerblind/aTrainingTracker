# Stage 3: Implementation Plan - ATT-2387: Map Origin Source Attribute in WorkoutDataMapper Batch fromCursor to Display Source Badges

**Ticket**: [ATT-2387](https://rainerblind.atlassian.net/browse/ATT-2387)  
**Sub-task**: [ATT-2496](https://rainerblind.atlassian.net/browse/ATT-2496) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-DAT-021` (*Workout Origin Source Batch Mapping Symmetry and Cursor Snapshot Propagation*)  
**Test Mapping**: `TST-DAT-016` (*Workout Origin Source Batch Mapping Symmetry Verification*)  
**Branch**: `feature/ATT-2387`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Problem Description & Background

During the Sprint `2026-40.15` Joint Review of [ATT-2186](https://rainerblind.atlassian.net/browse/ATT-2186) (*Add Origin Source Attribute to Workouts*), imported TCX and GPX workouts (e.g. `Runter in die Stadt #1`) were inspected on a physical Google Pixel 10 device. While the workout correctly displayed its sport (`Radeln`) and equipment (`endurance`), the origin source badge (`TCX` / `GPX`) was completely absent from `WorkoutHeader`.

Forensic investigation showed that while SQLite database rows stored `source = 'TCX'` correctly, the batch loading pipeline in `WorkoutDataMapper.kt` (`readCursorSnapshot` and `fromSnapshot`, introduced in `ATT-2309`) omitted extracting and forwarding the `source` attribute. Because `WorkoutData.source` defaults to `WorkoutSource.TRACKED`, all batch-loaded workouts (which populate workout list cards and detail views) defaulted in memory to `TRACKED`. This caused `WorkoutHeader.kt` (`if (data.source != WorkoutSource.TRACKED)`) to evaluate to `false`, silently hiding the badge.

In accordance with ASPICE **Rule 20 (Database & DTO Mapping Symmetry)**, all mapping pathways (single-item `fromCursor`, batch `fromCursor(cursor, batch)`, snapshot streaming `fromSnapshot`, and repository updates) must map database columns symmetrically.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-021` (*Workout Origin Source Batch Mapping Symmetry and Cursor Snapshot Propagation*)
  * Refines Clause 4 of `REQ-DAT-017` (*Workout Origin Source Provenance Attribute*).
  * Adds `source: WorkoutSource = WorkoutSource.TRACKED` to `WorkoutDataMapper.RawCursorSnapshot`.
  * Extracts `WorkoutSummaries.SOURCE` in `readCursorSnapshot(cursor)` with safe fallback to `TRACKED`.
  * Forwards `snapshot.source` to `WorkoutData(...)` in `fromSnapshot(snapshot, batch)`.
  * Guarantees that `fromCursor(cursor, batch)` inherits symmetrical mapping via delegation to `fromSnapshot`.
* **Test Mapping**: `TST-DAT-016` (*Workout Origin Source Batch Mapping Symmetry Verification*)
  * `TST-DAT-016.1`: Unit & domain mapping tests in `WorkoutDataSourceMappingTest.kt` verifying `fromCursor(cursor, batch)`, `readCursorSnapshot(cursor)`, and `fromSnapshot(snapshot, batch)` across all source variants (`TCX`, `GPX`, `FIT`, `TRACKED`, `null`, invalid).
  * `TST-DAT-016.2`: Architectural contract test in `WorkoutDataMappingSymmetryTest.kt` verifying Rule 20 compliance across all cursor columns mapped in `WorkoutDataMapper`.
  * `TST-DAT-016.3`: Full clean-room test suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing single-row mapping, database queries, and test suites must continue to pass cleanly.
2. **Provenance Immutability**: Historical workouts before schema v24 and live-tracked workouts continue to evaluate to `WorkoutSource.TRACKED` and display no badge.
3. **Batch Streaming Invariant (ATT-2309 / REQ-STB-013)**: The zero cursor-seeking invariant during batch streaming in `WorkoutRepository.loadAllWorkouts()` is preserved with zero additional database round-trips.
4. **UI Presentation Consistency**: The existing origin badge in `WorkoutHeader.kt` and workout cards renders as originally designed in ATT-2186 without any styling alterations.
5. **Subtask Self-Sufficiency**: Subtask [ATT-2496](https://rainerblind.atlassian.net/browse/ATT-2496) transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket [ATT-2387](https://rainerblind.atlassian.net/browse/ATT-2387) remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutDataMapper.kt`
* **File**: [WorkoutDataMapper.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt)
* **Changes**:
  1. In `RawCursorSnapshot`:
     Add field `val source: WorkoutSource = WorkoutSource.TRACKED`.
  2. In `readCursorSnapshot(cursor: Cursor)`:
     Extract column `WorkoutSummaries.SOURCE`:
     ```kotlin
     val source = cursor.getColumnIndex(WorkoutSummaries.SOURCE).takeIf { it >= 0 }?.let {
         WorkoutSource.fromString(cursor.getString(it))
     } ?: WorkoutSource.TRACKED
     ```
     Pass `source = source` to `RawCursorSnapshot(...)`.
  3. In `fromSnapshot(snapshot: RawCursorSnapshot, batch: BatchMetadata)`:
     Pass `source = snapshot.source` to `WorkoutData(...)`.

### Component 2: `WorkoutDataSourceMappingTest.kt`
* **File**: [WorkoutDataSourceMappingTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataSourceMappingTest.kt)
* **Changes**:
  - Add tests validating that `fromCursor(cursor, batch)`, `readCursorSnapshot(cursor)`, and `fromSnapshot(snapshot, batch)` map all sources symmetrically with `fromCursor(cursor)`.

### Component 3: `WorkoutDataMappingSymmetryTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMappingSymmetryTest.kt`
* **Changes**:
  - Implement an architectural contract test asserting that all domain fields extracted from SQLite cursor in `fromCursor(cursor)` are declared in `RawCursorSnapshot` and mapped to `WorkoutData` in `fromSnapshot`.

### UI Consistency (Rule 23)
* **Reference screen / component**: [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt) (lines 256–265).
* **Reused components**: `WorkoutHeader` Row A origin source badge (`Surface`, `Text`).
* **Theme tokens**: `MaterialTheme.colorScheme.surfaceVariant`, `MaterialTheme.colorScheme.onSurfaceVariant`, `MaterialTheme.typography.labelSmall`, `MaterialTheme.shapes.extraSmall`.
* **New one-off styles & justification**: None. Reuses existing tokens and components without modification.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Check
* Command: `python3 tools/jira_util.py check-gate ATT-2496`
* Confirm exit code `0` before modifying production code.

### Step 2: Implement Symmetric Mapping in `WorkoutDataMapper.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt`
* Add `source` to `RawCursorSnapshot`, extract in `readCursorSnapshot`, and forward in `fromSnapshot`.

### Step 3: Expand Unit Tests in `WorkoutDataSourceMappingTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataSourceMappingTest.kt`
* Add comprehensive test cases asserting symmetrical behavior across single, batch, and snapshot mapping.

### Step 4: Implement Architectural Symmetry Contract Test
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMappingSymmetryTest.kt`
* Implement reflection/contract assertions guarding against field omission between cursor, snapshot, and WorkoutData.

### Step 5: Targeted Unit & Contract Verification
* Execute:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataSourceMappingTest --tests com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMappingSymmetryTest
  ```
* Verify 100% pass rate.

### Step 6: Stage 5 Full Clean-Room Regression Suite
* Execute:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Verify 0 failures across all test suites.
