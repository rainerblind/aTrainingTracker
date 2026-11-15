# Stage 1 Analysis: ATT-7758 - Propagate RouteCluster Rename/Merge/Delete to In-Memory WorkoutData

**Ticket**: [ATT-7758](https://issue-tracker/ATT-7758)  
**Parent Epic**: [ATT-176](https://issue-tracker/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7758`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
Renaming, merging, deleting, or splitting a `WorkoutCluster` updates `RouteClusters.db` and `WorkoutClusterRepository.allClusters`, but `WorkoutHeader` (`data.clusterName`, lines 300–331 & 402–432) continues displaying stale cluster badges across the Workouts list, Workout Details, Cluster Heatmap/List screens, Period Map peek sheet, and `ActiveFilterChipsRow` until process restart. Additionally, cluster renames do not sync linked routes (`RoutesRepository`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Stale Denormalized Snapshots**: `WorkoutData.clusterId` & `clusterName` (`WorkoutData.kt:122–123`) are hydrated at load time in `WorkoutRepository._allWorkouts` and `WorkoutClusterRepository.getWorkoutsForCluster`.
2. **Missing Mutation Propagation**: `WorkoutClusterRepository.updateCluster` (`356–359`), `deleteCluster` (`459–471`), `mergeClusters` (`476–483`), and `WorkoutClusterEngine.syncRouteNameChange` (`615–624`) / `splitCluster` (`1394–1546`) mutate SQLite and refresh `_allClusters`, but never update `WorkoutRepository._allWorkouts`, `WorkoutClustersViewModel._clusterWorkouts` / `_peekedWorkoutDataWithTrack`, or `PeriodsViewModel._peekedWorkoutDataWithTrack`.
3. **Linked Route & Filter Chip Desync**: `WorkoutClusterRepository.updateCluster` omits syncing linked routes (`RoutesDatabaseManager.getRouteByClusterId`), `RoutesRepository.updateRouteSummary` omits refreshing `WorkoutClusterRepository`, and `WorkoutSummariesViewModel.filterCriteria` caches stale `clusterName` in `ActiveFilterChipsRow`.
* **Independent Peer Review & Verification**:
  * Verified all cited lines against `sprint/2026-46.7` (`WorkoutData.kt:122–123`, `WorkoutHeader.kt:300–331/402–432`, `WorkoutClusterRepository.kt:356–483`, `WorkoutClustersViewModel.kt:443–497/694–716`, `RoutesRepository.kt:297–309`).
  * **Blind Spots Identified**: (a) `PeriodsRepository` stores `PeriodSummary` (not `WorkoutData`), querying SQLite fresh in `getWorkoutsForRange()`; the actual stale cache in Periods is `PeriodsViewModel._peekedWorkoutDataWithTrack` (`PeriodsViewModel.kt:388–401`). (b) Observing `_allClusters` alone cannot distinguish `merge(sourceId -> targetId)` or `splitCluster` from `delete(sourceId)`; explicit mutation propagation events/methods are required. (c) `WorkoutClustersScreen.kt:151,269` holds a snapshot `inspectedWorkout` instead of resolving by ID from reactive `clusterWorkouts`. (d) `WorkoutRepository.init` must avoid instantiating `WorkoutClusterRepository` unconditionally in plain JVM tests (`coding_standards.md` §5.5).

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * Immediate in-memory propagation of `WorkoutCluster` rename, merge, delete, split, and route-sync name changes to `WorkoutRepository._allWorkouts`, `WorkoutClustersViewModel` (`_clusterWorkouts`, `_peekedWorkoutDataWithTrack`, `_selectedCluster`, `_unclusteredWorkouts`), `WorkoutClustersScreen` (`inspectedWorkout`), and `PeriodsViewModel._peekedWorkoutDataWithTrack`.
  * Live `clusterName` (and merge/delete resilience) in `WorkoutSummariesViewModel.filterCriteria` / `ActiveFilterChipsRow`.
  * Bidirectional `Cluster <-> Route` name sync (`WorkoutClusterRepository.updateCluster` -> `RoutesRepository` without recursion; `RoutesRepository.updateRouteSummary` -> `WorkoutClusterRepository`).
* **Out-of-Scope Non-Goals**: Changing `WorkoutSummaries` SQLite schema, altering `WorkoutData.workoutName` on cluster rename, or full PeriodSummary re-aggregation (cluster names are not stored in `PeriodSummaries.db`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
Net-new requirement (`REQ-PER-040`) extending `REQ-SET-059`, `REQ-SET-089`, `REQ-SET-096` without altering existing acceptance criteria or hit-count invariants.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PER-040` (complements `REQ-SET-059`, `REQ-SET-089`, `REQ-SET-096`, `REQ-UI-242`).
* **Affected Architecture Elements**: `WorkoutClusterRepository.kt`, `WorkoutRepository.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClustersScreen.kt`, `PeriodsViewModel.kt`, `WorkoutSummariesViewModel.kt`, `RoutesRepository.kt`, `WorkoutClusterEngine.kt`.
* **Verification Impact & Test Surface**: Net-new `TST-PER-048` plus existing `WorkoutClusterRepositoryTest`, `WorkoutClusterMergeTest`, `WorkoutClusterSplitTest`, `WorkoutClustersViewModelTest`, `WorkoutSummariesViewModelFilterTest`, `PeriodsViewModelTest`, `RoutesRepositorySyncTest`.
* **Risk Assessment & Invariants**: Medium risk due to multi-repository coupling and JVM unit test isolation (`coding_standards.md` §5.5); mitigate via non-recursive sync guards and explicit resettable hooks.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: `WorkoutData.workoutName` MUST NOT be overwritten on simple cluster rename; `Route <-> Cluster` bidirectional sync MUST NOT infinite-loop; plain JVM tests (`WorkoutRepository*Test`) MUST NOT trigger `PreferenceManager`/IO leaks in `init`.
* **Risk Rating**: **MEDIUM** — Multi-repository state propagation (`WorkoutClusterRepository`, `WorkoutRepository`, `RoutesRepository`) + 3 ViewModels (`WorkoutClustersViewModel`, `WorkoutSummariesViewModel`, `PeriodsViewModel`).
