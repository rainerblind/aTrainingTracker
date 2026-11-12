# Stage 3: Implementation Plan - ATT-7311: [Bug] Deduplicate overlapping climbs in Climbs.db and map layer after historical backfill

**Ticket**: [ATT-7311](https://rainerblind.atlassian.net/browse/ATT-7311)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-CLM-011` (`REQ-CLM-011.1`..`REQ-CLM-011.3`)  
**Test Mapping**: `TST-CLM-011` (`TST-CLM-011.1`..`TST-CLM-011.5`)  
**Branch**: `subagent-Lane-3-Agent---ATT-7311-self-b7801336`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

During Sprint 2026-46.3 Joint Review of ATT-6692, duplicate climb polylines were observed on the map layer and in `Climbs.db` on device.
The existing deduplication in `ClimbsDatabaseManager` checks only start and end coordinates with a rigid 50m tolerance (`startDist <= 50m && endDist <= 50m`). Real-world GPS drift and barometric detection latency between multiple rides on the same hill exceed 50m, causing historical backfills and workout imports to persist redundant climb entries. Furthermore, the engine lacks corridor overlap deduplication, existing databases on devices have un-deduplicated historical records, and the map layer renders all stored climbs without display deduplication.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-CLM-011` (*Deduplicate Overlapping Climbs in Climbs.db & Map Layer*)
  - `REQ-CLM-011.1`: Spatial Endpoint & Corridor Overlap Deduplication
  - `REQ-CLM-011.2`: Idempotent Historical Database Consolidation Migration
  - `REQ-CLM-011.3`: Map Layer Display Deduplication Parity
* **Test Mapping**: `TST-CLM-011` (*Deduplicate Overlapping Climbs in Climbs.db & Map Layer Verification Suite*)
  - `TST-CLM-011.1`: Spatial Endpoint & Corridor Overlap Deduplication Test (`ClimbsDatabaseManagerTest.kt`)
  - `TST-CLM-011.2`: Idempotent Historical Database Consolidation Migration Test (`WorkoutHistoricalClimbBackfillTest.kt`, `ClimbsDatabaseManagerTest.kt`)
  - `TST-CLM-011.3`: Map ViewModel Display Deduplication Test (`MapFragmentWithTrackViewModelTest.kt`)
  - `TST-CLM-011.4`: Localization & Invariant Audit (`TranslationParityTest.kt`)
  - `TST-CLM-011.5`: Targeted Regression Verification Suite

---

## 3. System Invariants & Preserved Behavior

1. **Route Climb Integrity**: Route climb associations (`routeId`) are never dropped or overwritten with null; route climbs retain precedence over free-riding climbs.
2. **Schema Invariance**: `Climbs.db` SQLite schema v1 (columns, types, table names) remains unchanged.
3. **Thread Safety**: Database insertions, queries, and deletions remain bound to `Dispatchers.IO`.
4. **Zero Detection Regressions**: Climb detection mathematical formulas (`ClimbDetector.kt`) and scoring formulas (`ClimbCategory`) remain completely untouched.

---

## 4. Proposed Architectural Changes

### Component 1: `ClimbsDatabaseManager.kt`
* Implement expanded endpoint proximity matching ($\le 100\text{ m}$) in `isDuplicateOrOverlapping`.
* Implement corridor overlap matching: compute start-to-summit heading alignment ($\le 45^\circ$) and point-to-polyline cross-track overlap ($\ge 70\%$ overlap within $40\text{ m}$ corridor).
* Add `updateClimb(climb: Climb): Int` to update existing records with route association or better geometry.
* Add `suspend fun deduplicateExistingClimbs(thresholdMeters: Float = 100.0f): Int` running in a single SQLite transaction to purge legacy duplicate rows while preserving route links.

### Component 2: `WorkoutRepository.kt`
* Add `const val PREF_CLIMBS_WORKOUT_DEDUPLICATION_V3 = "climbs_workout_deduplication_v3"`.
* Add `suspend fun deduplicateHistoricalClimbs(): Int` wired into `triggerHistoricalClimbBackfillIfPending()`.
* On completion, mark preference true and refresh `LiveClimbsRepository` if any climbs were consolidated.

### Component 3: `MapFragmentWithTrackViewModel.kt`
* Filter `allStoredClimbs` in `mapClimbsFlow` to ensure that only non-overlapping canonical climbs are emitted to `uiState.climbs`.

### UI Consistency (Rule 23)
* No UI layout or design tokens changed. Map polyline styling remains identical to existing `ClimbHighlightData` contracts.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Corridor Overlap & Enhanced Deduplication in `ClimbsDatabaseManager.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/database/ClimbsDatabaseManager.kt`
* Changes:
  - Add helper functions `isDuplicateOrOverlapping(climbA, climbB, thresholdMeters)` evaluating endpoint proximity ($\le 100\text{m}$) and corridor overlap ($\ge 70\%$ shared path, cross-track $\le 40\text{m}$, heading diff $\le 45^\circ$).
  - Add `updateClimb(climb: Climb): Int`.
  - Update `insertClimbWithDeduplication` and `insertClimbsWithDeduplicationBatch` to use `isDuplicateOrOverlapping`, merge `routeId` when candidate has route and existing does not, and return matched ID.
  - Implement `deduplicateExistingClimbs(thresholdMeters: Float = 100.0f): Int`.

### Step 2: Implement One-Time Database Deduplication Migration in `WorkoutRepository.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
* Changes:
  - Declare `PREF_CLIMBS_WORKOUT_DEDUPLICATION_V3`.
  - Implement `deduplicateHistoricalClimbs(): Int`.
  - Wire into `triggerHistoricalClimbBackfillIfPending()`.

### Step 3: Implement Display Deduplication in `MapFragmentWithTrackViewModel.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrackViewModel.kt`
* Changes:
  - In `mapClimbsFlow`, apply `deduplicateForDisplay` to filter out visually overlapping duplicates before emitting to `uiState.climbs`.

### Step 4: Unit & Integration Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/ClimbsDatabaseManagerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutHistoricalClimbBackfillTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrackViewModelTest.kt`
* Changes:
  - Add unit tests for 100m endpoint deduplication, corridor overlap deduplication, reverse heading isolation, and `deduplicateExistingClimbs`.
  - Add test for `WorkoutRepository.deduplicateHistoricalClimbs()`.
  - Add test for `MapFragmentWithTrackViewModel` display deduplication.

### Step 5: Execute Targeted Unit Tests
* Command: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutHistoricalClimbBackfillTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapFragmentWithTrackViewModelTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction (Step 5), followed by full clean-room suite in Stage 5.
* **Rollback**: Git branch isolation (`subagent-Lane-3-Agent---ATT-7311-self-b7801336`) allows full revert without collateral risk.
