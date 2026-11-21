# Stage 2: Requirement & Test Specification - ATT-6047: WorkoutClusterEngine Altitude Extrema & Bounds on Move/Delete and WorkoutCluster Encapsulation

**Ticket**: [ATT-6047](https://issue-tracker/ATT-6047)  
**Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Requirement Mapping**: `REQ-REV-017` (`REQ-REV-017.1`..`REQ-REV-017.3` — refines `REQ-REV-007`, `REQ-SET-063`, `REQ-SET-065`)  
**Test Spec ID**: `TST-REV-018` (`TST-REV-018.1`..`TST-REV-018.3`)  
**Branch**: `feature/ATT-6047`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-21  

---

## 1. Requirement Specification (`REQ-REV-017`)

### 1.1 Problem Statement & Rationale
* `WorkoutClusterEngine.kt` duplicates centroid/altitude-extrema arithmetic across 5 callers plus two `createNewClusterFromWorkout` clones and hit-count decrement blocks, leaving `moveWorkoutToCluster` without altitude extrema & bounds updates and `onWorkoutDeleted` without `minAltLat/Lng` / `maxAltLat/Lng` reversal.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **`WorkoutCluster` Encapsulation (`REQ-REV-017.1`)**: `WorkoutCluster` SHALL expose `withAddedWorkout(...)` (centroids, nullable altitude extrema, `minOf`/`maxOf` bounds; preserves `hitCount`) and `withRemovedWorkout(...)` (reverses centroids & nullable altitude extrema with `hitCount <= 1` guard and `decrementHitCount: Boolean = false`). `learnFromWorkout`, `onWorkoutFinished`, `migrateHistory`, `moveWorkoutToCluster`, and `onWorkoutDeleted` SHALL delegate to them.
2. **Move & Delete Extrema/Bounds Completeness (`REQ-REV-017.2`)**: `moveWorkoutToCluster` SHALL fetch `ALTITUDE` `MIN`/`MAX` extrema and workout bounds, apply `withRemovedWorkout` (source) & `withAddedWorkout` (target) before `assignClusterToWorkout`, then invoke `recalculateClusterAnchors(context, refreshedOld)` on source to shrink bounds & refresh sport frequency, and update `refreshedNew` sport. `onWorkoutDeleted` SHALL pass nullable in-memory `w.minAltitudeLatLng`/`w.maxAltitudeLatLng` to `withRemovedWorkout(..., decrementHitCount = true)` before `recalculateClusterAnchors` when `isAnchor` (`hitCount <= 1 -> 0`).
3. **Deduplication & Contract Invariants (`REQ-REV-017.3`)**: Consolidate both `createNewClusterFromWorkout` overloads and `assign`/`unassign` hit-count decrement blocks into shared private helpers; keep `WorkoutClusterEngine.kt <= 1,635` LoC (removing baseline entry in `ArchitectureConformanceContractTest` if `<= 1,500` LoC), `0` `.ui.*` imports (`REQ-PRO-130.2`), and `0` UI strings.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (`TST-REV-018.1`)**: *Given* a `WorkoutCluster`, *When* `withAddedWorkout` or `withRemovedWorkout` executes with valid or `null` altitude extrema and `hitCount = 0/1/3`, *Then* centroids, altitude extrema, bounds expansion, and `hitCount` contracts hold without division by zero.
* **Criterion 2 (`TST-REV-018.2`)**: *Given* `moveWorkoutToCluster` or `onWorkoutDeleted` (post-DB-delete with nullable `w.minAltitudeLatLng`/`maxAltitudeLatLng`), *When* invoked, *Then* target bounds & altitude extrema expand, source altitude extrema reverse, and `recalculateClusterAnchors` updates shrunk source bounds.
* **Criterion 3 (`TST-REV-018.3`)**: *Given* `createNewClusterFromWorkout`, `assign`/`unassign`, and `WorkoutClusterEngine.kt`, *When* compiled and tested, *Then* targeted cluster suites and `ArchitectureConformanceContractTest` (`<= 1,635` LoC / baseline parity, `0` `.ui.*` imports) pass.

---

## 2. Test Specification (`TST-REV-018`)

### Test Case 1: `WorkoutCluster.withAddedWorkout` & `withRemovedWorkout` (`TST-REV-018.1`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterApexTest.kt`)
* **Expected Result**: Verified centroid, nullable altitude extrema, bounds expansion, `hitCount <= 1` guard, and `decrementHitCount` parity.

### Test Case 2: `moveWorkoutToCluster` & `onWorkoutDeleted` Completeness (`TST-REV-018.2`)
* **Scope**: Unit Test (`WorkoutClusterApexTest.kt`, `EditWorkoutClusterTest.kt`)
* **Expected Result**: Target altitude extrema & bounds expand, source altitude extrema reverse, `recalculateClusterAnchors` updates source bounds post-`assignClusterToWorkout`, and `onWorkoutDeleted` reverses nullable in-memory altitude extrema before anchor recalc.

### Test Case 3: Targeted Cluster & Contract Regression (`TST-REV-018.3`)
* **Command**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.database.WorkoutCluster*" --tests "com.atrainingtracker.trainingtracker.database.EditWorkoutClusterTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`
* **Expected Result**: 100% pass (`WorkoutClusterEngine.kt` LoC ratchet valid, `0` `.ui.*` imports).

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-REV-017.1` | `WorkoutCluster.withAddedWorkout`/`withRemovedWorkout` & 5-caller delegation | `TST-REV-018.1` | Unit Test |
| `REQ-REV-017.2` | `moveWorkoutToCluster` & `onWorkoutDeleted` altitude extrema & bounds completeness | `TST-REV-018.2` | Unit Test |
| `REQ-REV-017.3` | `createNewClusterFromWorkout`/hit-count deduplication, LoC & `0` `.ui.*` imports | `TST-REV-018.3` | Unit & Contract Test |
