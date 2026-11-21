# Stage 1 Analysis: ATT-6047 - WorkoutClusterEngine: Altitude Extrema & Bounds on Move/Delete and WorkoutCluster Centroid Encapsulation

**Ticket**: [ATT-6047](https://issue-tracker/ATT-6047)  
**Sub-task**: Pending creation (`[Analysis]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Branch**: `feature/ATT-6047` (from `sprint/2026-11-20_03_Nacht` @ `d60bf458`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-21  

---

## 1. Problem Statement & Motivation
* **Current Behavior**: In `WorkoutClusterEngine.kt` (`1,634` LoC), running-mean centroid and altitude-extrema arithmetic is copy-pasted across 5 paths (`learnFromWorkout` `:236-276`, `onWorkoutFinished` `:398-434`, `onWorkoutDeleted` `:454-475`, `migrateHistory` `:746-784`, `moveWorkoutToCluster` `:1267-1290`; plus weighted merge in `mergeClusters` `:1338-1407`), alongside two ~65-line `createNewClusterFromWorkout` clones (`:940-1076`) and duplicated hit-count decrement blocks (`:868-879` vs `:920-931`). Consequently:
  1. `moveWorkoutToCluster()` (`:1257-1317`) omits `minAltLat/Lng`, `maxAltLat/Lng`, and spatial bounds (`minLat/maxLat/minLng/maxLng`) updates for both source (`oldCluster`) and target (`newCluster`).
  2. `onWorkoutDeleted()` (`:442-477`) recalculates bounds via `recalculateClusterAnchors` when `isAnchor`, but omits `minAltLat/Lng` and `maxAltLat/Lng` reversal.
* **Expected Behavior**: Encapsulate `withAddedWorkout(...)` (centroids, altitude extrema, bounds expansion) and `withRemovedWorkout(...)` (centroids, altitude extrema) on `WorkoutCluster` (`WorkoutClusterDatabaseManager.kt:152`); fix `moveWorkoutToCluster` (altitude extrema + target bounds via `withAddedWorkout`, source bounds via `recalculateClusterAnchors` after `assignClusterToWorkout`) and `onWorkoutDeleted` (altitude extrema from in-memory `w.minAltitudeLatLng`/`w.maxAltitudeLatLng` + existing anchor recalc); deduplicate `createNewClusterFromWorkout` and hit-count reconciliation.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification (Live `sprint/2026-11-20_03_Nacht` @ `d60bf458`)**:
  * **Upstream `ATT-6159` Confirmed Landed (`07f4fcac`)**: `moveWorkoutToCluster` (`:1266-1316`) already fixed stale-copy overwrite and moved `assignClusterToWorkout` before `getMostFrequentSportIdForCluster`. What remains is strictly `ATT-6047`: `workoutRepo.getExtremaPosition(workoutId, SensorType.ALTITUDE, MIN/MAX)`, `workoutRepo.getWorkoutBounds(workoutId)`, altitude/bounds updates, `recalculateClusterAnchors` on source cluster, and `WorkoutCluster` encapsulation.
  * **Critical `hitCount` Asymmetry Discovered (Blind Spot)**: `learnFromWorkout`, `onWorkoutFinished`, `migrateHistory`, and `moveWorkoutToCluster` leave `hitCount` unchanged during centroid update (reconciled later by `assignClusterToWorkout`), whereas `onWorkoutDeleted` (`:457`) decrements `hitCount = cluster.hitCount - 1` (asserted in `WorkoutClusterApexTest.kt:184`). `withAddedWorkout` / `withRemovedWorkout` MUST preserve this exact `hitCount` contract (e.g., `withAddedWorkout` keeps `hitCount`, `withRemovedWorkout` preserves or parameterizes `decrementHitCount`).
  * **Delete-Order & Bounds Constraints Confirmed**: `WorkoutRepository.kt:4689->4698` deletes SQLite rows *before* `onWorkoutDeleted`, so DB `getExtremaPosition` is gone and `onWorkoutDeleted` MUST read nullable `w.minAltitudeLatLng`/`w.maxAltitudeLatLng`. Because bounds are `minOf`/`maxOf` envelopes, `withRemovedWorkout` cannot shrink them; `moveWorkoutToCluster` must call `recalculateClusterAnchors(context, refreshedOld)` post-`assignClusterToWorkout` (noting `recalculateClusterAnchors:518-522` already updates `probableSportId`/`bSportType`).
  * **Monolith LoC Ratchet Trap (`ArchitectureConformanceContractTest.kt:131, :518-520`)**: `WorkoutClusterEngine.kt` ceiling is `1,635` (live `1,634` LoC) with `MAX_MONOLITH_LOC_THRESHOLD = 1500`. If deduplication shrinks `WorkoutClusterEngine.kt <= 1,500` LoC, `ArchitectureConformanceContractTest.MAX_MONOLITH_FILE_LOC_BASELINE` MUST remove its entry or the contract test fails as "stale". Also maintain **0 `.ui.*` imports** (`REQ-PRO-130.2`).

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals (`3 SP`)**: (1) `WorkoutCluster.withAddedWorkout` & `withRemovedWorkout` (with `hitCount <= 1` guard & nullable altitude extrema); (2) Fix `moveWorkoutToCluster` & `onWorkoutDeleted` altitude extrema/bounds; (3) Deduplicate `createNewClusterFromWorkout` overloads and `assign`/`unassign` hit-count reconciliation; (4) Add unit tests in `WorkoutClusterApexTest.kt` (move/delete altitude extrema & bounds, `WorkoutCluster` encapsulation).
* **Out-of-Scope Non-Goals**: No `ClusterData` rename (`WorkoutCluster` is the real entity), no SQLite schema bump, no `mergeClusters` signature changes (`mergeClusters` combines two clusters rather than adding/removing one workout), no `ClusterWorkoutDataPort` API changes, no UI/strings edits.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-063` (Apex/Mean maintenance), `REQ-SET-065` (`ATT-502` 3D altitude extrema), `REQ-SET-059` (Cluster assignment), `REQ-PRO-130.2` (`0` `.ui.*` imports), `REQ-PRO-129.2` (`LoC` ratchet).
* **Historical Origin & Commit Trace**: `ATT-354` (bounds/surgical updates), `ATT-502` (altitude extrema), `ATT-318` (hit-count reconciliation), `07f4fcac` (`ATT-6159` move ordering), `521d6379` (`ATT-8202` `ClusterWorkoutDataPort`).
* **Root Reason for Existing Formulation**: `hitCount = 0` legacy/seeded clusters and post-delete `WorkoutSummaries` removal require `hitCount <= 1` guards, nullable altitude extrema fallbacks, and anchor-based bounds recalculation from surviving workouts.
* **Preservation of Core Invariants**: Preserve `clusterLock` synchronization, `onWorkoutDeleted` `hitCount <= 1 -> hitCount = 0`, `recalculateClusterAnchors` on `isAnchor`, `0` `.ui.*` imports in `database/`, and `ArchitectureConformanceContractTest` baseline consistency.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-SET-065` / adds `REQ-SET-*` (`TST-SET-*`) for `moveWorkoutToCluster` & `onWorkoutDeleted` altitude-extrema/bounds completeness and `WorkoutCluster` encapsulation. `SYS.2`/`SYS.3`: None (internal SQLite clustering only). Localization: N/A (`0` UI strings).
* **Affected Architecture Elements**: `WorkoutClusterDatabaseManager.kt` (`WorkoutCluster`), `WorkoutClusterEngine.kt`, (if `<= 1,500` LoC: `ArchitectureConformanceContractTest.kt` baseline entry); `docs/architecture.md` unchanged (`Rule 8.8`).
* **Verification Impact & Test Surface**: `WorkoutClusterApexTest.kt`, `EditWorkoutClusterTest.kt`, `WorkoutClusterDeleteTest.kt`, `WorkoutClusterEngine*Test.kt`, `ArchitectureConformanceContractTest.kt`.
* **Risk Assessment & Invariants**: **MEDIUM** (`3 SP`, `P2`) — mitigated by preserving exact `hitCount` semantics per caller, handling `null` altitude extrema post-DB-delete, and testing move/delete bounds + altitude extrema in `WorkoutClusterApexTest.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: `clusterLock` thread safety; `0` `.ui.*` imports in `WorkoutClusterEngine.kt` (`REQ-PRO-130.2`); `WorkoutClusterEngine.kt <= 1,635` LoC (and baseline cleanup if `<= 1,500`); post-delete `w.minAltitudeLatLng`/`maxAltitudeLatLng` in-memory safety.
* **Risk Rating**: **MEDIUM** — core clustering math refactor across 5 callers, strongly bounded by existing + new unit tests.
