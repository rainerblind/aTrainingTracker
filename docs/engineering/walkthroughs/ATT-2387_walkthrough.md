# Stage 5: Walkthrough & Verification - ATT-2387: Map Origin Source Attribute in WorkoutDataMapper Batch fromCursor to Display Source Badges

**Ticket**: [ATT-2387](https://atrainingtracker.atlassian.net/browse/ATT-2387)  
**Sub-task**: [ATT-2498](https://atrainingtracker.atlassian.net/browse/ATT-2498) (`[Test]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-DAT-021` (*Workout Origin Source Batch Mapping Symmetry and Cursor Snapshot Propagation*)  
**Test Mapping**: `TST-DAT-016` (*Workout Origin Source Batch Mapping Symmetry Verification*)  
**Branch**: `feature/ATT-2387`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Executive Summary & Verification Overview

During on-device physical testing on a Google Pixel 10 (Sprint 2026-40.15 review of ATT-2186), imported workouts (such as TCX workout *'Runter in die Stadt #1'*) failed to display the origin source badge (`TCX`) in `WorkoutHeader`, despite the underlying SQLite database row containing `source = 'TCX'`.

Forensic investigation revealed an architectural mapping asymmetry introduced during the performance optimization of `ATT-2309` (batch cursor snapshot decoupling):
1. **Root Cause**: `WorkoutDataMapper.RawCursorSnapshot` lacked the `source` property, causing `readCursorSnapshot(cursor)` to skip extracting `WorkoutSummaries.SOURCE`.
2. **Default Fallback**: `fromSnapshot(snapshot, batch)` relied on the default parameter of `WorkoutData.source`, silently falling back to `WorkoutSource.TRACKED` for all workouts loaded through `WorkoutRepository.loadAllWorkouts()`.
3. **UI Badge Suppression**: In `WorkoutHeader.kt`, the origin badge condition `if (data.source != WorkoutSource.TRACKED)` evaluated to `false`, omitting the origin badge across all workout lists, tabs, and detail views.

### Architectural Solution
* **Data Model Symmetry**: Added `val source: WorkoutSource = WorkoutSource.TRACKED` to `WorkoutDataMapper.RawCursorSnapshot`.
* **Deterministic Cursor Extraction**: Updated `readCursorSnapshot(cursor)` to safely resolve `WorkoutSummaries.SOURCE` via `cursor.getColumnIndex(WorkoutSummaries.SOURCE).takeIf { it >= 0 }?.let { WorkoutSource.fromString(cursor.getString(it)) } ?: WorkoutSource.TRACKED`.
* **Batch Propagation**: Updated `fromSnapshot(snapshot, batch)` to forward `source = snapshot.source` into `WorkoutData(...)`.
* **Rule 20 Mapping Symmetry Contract**: Added `WorkoutDataMappingSymmetryTest.kt` verifying strict architectural symmetry and reflection parity between cursor columns, `RawCursorSnapshot`, and `WorkoutData`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-021` | `TST-DAT-016.1` | Unit Tests (`WorkoutDataSourceMappingTest.kt`) | **PASSED** (100%) | `Verified` |
| `REQ-DAT-021` | `TST-DAT-016.2` | Architectural Contract Test (`WorkoutDataMappingSymmetryTest.kt`) | **PASSED** (100%) | `Verified` |
| `REQ-PRO-001` | `TST-DAT-016.3` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 4m 36s
32 actionable tasks: 12 executed, 20 up-to-date
```

### Targeted Unit & Contract Tests
```text
./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataSourceMappingTest --tests com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMappingSymmetryTest
BUILD SUCCESSFUL in 9s
32 actionable tasks: 2 executed, 30 up-to-date
```
* `WorkoutDataSourceMappingTest`:
  - `testWorkoutDataMapper_readCursorSnapshot_mapsValidSources`: Verifies `readCursorSnapshot` extracts `TCX`, `GPX`, `FIT`, and `TRACKED` into `RawCursorSnapshot.source`.
  - `testWorkoutDataMapper_readCursorSnapshot_fallsBackToTrackedForNullOrInvalid`: Verifies null or unknown source strings safely default to `WorkoutSource.TRACKED`.
  - `testWorkoutDataMapper_fromSnapshot_and_fromCursorBatch_mapsValidSources`: Verifies both `fromSnapshot` and `fromCursor(cursor, batch)` propagate the source to `WorkoutData.source` and `WorkoutData.headerData.source`.
  - `testWorkoutDataMapper_fromSnapshot_and_fromCursorBatch_fallsBackToTrackedForNullOrInvalid`: Verifies safe default fallback to `TRACKED` across batch overloads.
* `WorkoutDataMappingSymmetryTest`:
  - `testRule20_rawCursorSnapshot_declaresSourceProperty`: Validates via reflection that `RawCursorSnapshot` declares `source`.
  - `testRule20_workoutData_and_rawCursorSnapshot_sourceTypeParity`: Validates type parity between `RawCursorSnapshot.source` and `WorkoutData.source`.
  - `testRule20_singleAndBatchMapping_yieldIdenticalProvenanceAndFlags`: Compares single-item `fromCursor` vs batch `fromCursor` vs snapshot pipeline, verifying 100% provenance and flag symmetry.

---

## 4. Hardware / Physical Verification (Pixel 10) & UI Consistency (Rule 23)

* **Physical Verification**: On-device physical inspection of imported workouts on Google Pixel 10 confirmed that `WorkoutHeader` renders the origin source badge (`TCX`, `GPX`, `FIT`) with high-contrast badge styling in Row A alongside the sport name and equipment chip.
* **Live-Tracked Workouts**: Workouts recorded directly within the application maintain `source = WorkoutSource.TRACKED` and continue to omit the source badge, preventing visual clutter for native activities.
* **Visual Consistency (Rule 23)**:
  * **Reference Component**: [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt) Row A layout.
  * **Tokens**: Reuses standard theme colors, typography tokens (`MaterialTheme.typography.labelSmall`), and badge corner radius.
  * **Checked against `docs/design_guidelines.md` §5**: shapes ☑ spacing ☑ colors/themes ☑ typography/icons ☑ placement ☑
  * **Deviations & justification**: None.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Full clean-room test suite executed with 100% pass rate.
2. **Batch Streaming Performance**: Zero cursor-seeking invariant established in `ATT-2309` is fully preserved.
3. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-DAT-021`) and `docs/tests.md` (`TST-DAT-016`) updated to `Verified`.
4. **Requirement Governance Validation**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.1` passed with zero violations.
5. **Parent Ticket Final Review**: Parent ticket [ATT-2387](https://atrainingtracker.atlassian.net/browse/ATT-2387) transitioned to `Final Review (Human)` and assigned to `human` for final sign-off.
