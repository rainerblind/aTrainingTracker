# Stage 3: Implementation Plan - ATT-6047: WorkoutClusterEngine Altitude Extrema, Bounds & WorkoutCluster Encapsulation

**Ticket**: [ATT-6047](https://issue-tracker/ATT-6047)  
**Sub-task**: Pending creation (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Requirement Mapping**: `REQ-REV-017` (`REQ-REV-017.1`..`REQ-REV-017.3`)  
**Test Mapping**: `TST-REV-018` (`TST-REV-018.1`..`TST-REV-018.3`)  
**Branch**: `feature/ATT-6047`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-21  

---

## 1. Problem Description & Background
* `WorkoutClusterEngine.kt` (`1,634` LoC) duplicates centroid/altitude-extrema arithmetic across 5 methods plus two `createNewClusterFromWorkout` overloads and `assign`/`unassign` hit-count decrement blocks, omitting altitude extrema & bounds in `moveWorkoutToCluster` and altitude extrema reversal in `onWorkoutDeleted`.

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-REV-017` (`REQ-REV-017.1`..`.3`) — `WorkoutCluster` domain encapsulation, move/delete extrema & bounds completeness, and helper deduplication.
* **Test Mapping**: `TST-REV-018` (`TST-REV-018.1`..`.3`) — `WorkoutClusterApexTest`, `EditWorkoutClusterTest`, and `ArchitectureConformanceContractTest`.

## 3. System Invariants & Preserved Behavior
1. **Caller `hitCount` Asymmetry**: `withAddedWorkout` preserves `hitCount` (reconciled by `assignClusterToWorkout`); `withRemovedWorkout(..., decrementHitCount: Boolean = false)` guards `hitCount <= 1` (`decrementHitCount = true` -> `hitCount = 0`).
2. **Delete-Order & Bounds Shrinkage**: `onWorkoutDeleted` reads nullable in-memory `w.minAltitudeLatLng`/`w.maxAltitudeLatLng` (SQLite rows already deleted at `WorkoutRepository.kt:4689->4698`); `moveWorkoutToCluster` calls `recalculateClusterAnchors` on `refreshedOld` post-`assignClusterToWorkout`.
3. **Architecture & SWE.2/SWE.3 (`Rule 8.8`)**: `0` `.ui.*` imports in `WorkoutClusterEngine.kt` (`REQ-PRO-130.2`); `WorkoutClusterEngine.kt <= 1,635` LoC (remove baseline entry in `ArchitectureConformanceContractTest` if `<= 1,500`); `0` UI strings; no `docs/architecture.md` or `detailed_design/` changes needed.

## 4. Proposed Architectural Changes
### Component 1: `WorkoutCluster` (`WorkoutClusterDatabaseManager.kt:152`)
* Add pure `withAddedWorkout(start, end, apex, distance, minLat, minLng, maxLat, maxLng, minAltPos, maxAltPos)` (weighted centroids, nullable altitude extrema via `addWeightedCoord`, `minOf`/`maxOf` bounds) and `withRemovedWorkout(start, end, apex, distance, minAltPos, maxAltPos, decrementHitCount = false)` (`hitCount <= 1` guard, `removeWeightedCoord`).

### Component 2: `WorkoutClusterEngine.kt`
* Delegate `learnFromWorkout`, `onWorkoutFinished`, `migrateHistory`, `moveWorkoutToCluster`, and `onWorkoutDeleted` to `WorkoutCluster.withAddedWorkout` / `withRemovedWorkout`.
* In `moveWorkoutToCluster`: fetch `ALTITUDE` `MIN`/`MAX` + `getWorkoutBounds(workoutId)`, apply `withRemovedWorkout` / `withAddedWorkout` before `assignClusterToWorkout`, then run `recalculateClusterAnchors(context, refreshedOld)` on source and update `refreshedNew` sport frequency.
* Consolidate both `createNewClusterFromWorkout` overloads (`createClusterFromResolvedMetadata`) and `assign`/`unassign` hit-count decrement blocks (`reconcileRemovedClusterHitCount`).

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Gate 3 Check)**: Verify `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` exits `0` (`Rule 4.1`).
2. **Step 2 (`WorkoutClusterDatabaseManager.kt` — `REQ-REV-017.1`)**: Implement `withAddedWorkout`, `withRemovedWorkout`, and private coordinate-averaging helpers on `WorkoutCluster`.
3. **Step 3 (`WorkoutClusterEngine.kt` & `ArchitectureConformanceContractTest.kt` — `REQ-REV-017.1`, `.2`, `.3`)**: Refactor 5 callers, update `moveWorkoutToCluster` & `onWorkoutDeleted`, deduplicate `createNewClusterFromWorkout` and `reconcileRemovedClusterHitCount`, verify `0` `.ui.*` imports, and update monolith baseline if `<= 1,500` LoC.
4. **Step 4 (`WorkoutClusterApexTest.kt` & `EditWorkoutClusterTest.kt` — `TST-REV-018.1`, `.2`)**: Add unit tests tagged `TST-REV-018` / `TST-REV-018.1`..`TST-REV-018.3` for encapsulation, move altitude/bounds + `recalculateClusterAnchors`, and post-delete nullable altitude reversal; adjust `EditWorkoutClusterTest` mocks if needed for `recalculateClusterAnchors`.
5. **Step 5 (`TST-REV-018.3` Verification)**: Run targeted Gradle tests and `verify_requirement_governance.py`.

## 6. Verification & Rollback Plan
* **Verification**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.database.WorkoutCluster*" --tests "com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`.
* **Rollback**: Feature branch `feature/ATT-6047` isolation.
