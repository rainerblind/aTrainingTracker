# Stage 3: Implementation Plan - ATT-7758: In-Memory RouteCluster Propagation

**Ticket**: [ATT-7758](https://issue-tracker/ATT-7758)  
**Parent Epic**: [ATT-176](https://issue-tracker/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7758`  
**Requirement Mapping**: `REQ-PER-040` | **Test Mapping**: `TST-PER-048`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-15  

---

## 1. Problem Description & Background
`WorkoutCluster` rename, delete, merge, and split operations mutate SQLite and `WorkoutClusterRepository.allClusters`, leaving cached `WorkoutData.clusterName`/`clusterId` stale across `WorkoutRepository._allWorkouts`, `WorkoutClustersViewModel`, `WorkoutClustersScreen`, `PeriodsViewModel`, `WorkoutSummariesViewModel.filterCriteria` (`ActiveFilterChipsRow`), and linked routes in `RoutesRepository`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-040` (*Immediate In-Memory Propagation of RouteCluster Rename, Merge, Delete & Split and Bidirectional Route Sync*)
* **Test Mapping**: `TST-PER-048` (*RouteCluster In-Memory Propagation, Filter Chip & Bidirectional Route Sync Test Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Workout Name Immutability**: Cluster rename updates `WorkoutData.clusterName` only; `WorkoutData.workoutName` MUST NOT be overwritten.
2. **Non-Recursive Bidirectional Sync**: `Cluster -> Route` and `Route -> Cluster` name sync MUST NOT trigger infinite recursion or re-enter `clusterLock`.
3. **Plain JVM Isolation (`coding_standards.md` §5.5)**: `WorkoutRepository.init` MUST NOT instantiate `WorkoutClusterRepository` or trigger `PreferenceManager`/IO on mocked contexts.
4. **Repository Encapsulation (§2.5)**: Zero public `*DatabaseManager`/`Cursor` getters added.
5. **UI Consistency**: Zero new UI widgets or string resources (`design_guidelines.md` N/A).

---

## 4. Proposed Architectural Changes
* **Data/Repository Layer (`Dispatchers.IO`)**:
  * `WorkoutRepository`: Add atomic `_allWorkouts.update` handlers: `onClusterRenamed(clusterId, newName)`, `onClusterDeleted(clusterId)`, `onClusterMerged(sourceId, targetId, targetName)`, `reloadWorkouts(workoutIds)`.
  * `WorkoutClusterRepository`: Add `sealed class ClusterMutationEvent` (`Renamed`, `Deleted`, `Merged`, `Split`) + `clusterMutationEvents: SharedFlow`; propagate in `updateCluster`, `deleteCluster`, `mergeClusters`, `splitCluster`, and `onRouteSyncedClusterRename`; sync linked route in `updateCluster` via `RoutesRepository.syncClusterNameChange` (non-recursive) and refresh `RoutesRepository` on delete/merge.
  * `RoutesRepository`: Add `syncClusterNameChange(summary, newName)` (updates DB + `refreshRoutes()` without calling `syncRouteNameChange`); in `updateRouteSummary`, call `WorkoutClusterRepository.onRouteSyncedClusterRename(summary.clusterId)`.
* **ViewModel & UI Layer (`viewModelScope` / Compose)**:
  * `WorkoutClustersViewModel`: Observe `repository.clusterMutationEvents` to reconcile `_clusterWorkouts`, `_peekedWorkoutDataWithTrack`, `_selectedCluster`, `_unclusteredWorkouts`; route `executeClusterSplit` through `repository.splitCluster`.
  * `WorkoutClustersScreen`: Store `inspectedWorkoutId: Long?` and derive `inspectedWorkout` reactively from `clusterWorkouts`.
  * `PeriodsViewModel`: Observe `workoutRepo.allWorkouts` to update `_peekedWorkoutDataWithTrack` in-place when cached workout changes.
  * `WorkoutSummariesViewModel`: Observe `clusterRepo.allClusters` & `clusterMutationEvents` to update `filterCriteria.clusterName` on rename, clear on delete, and reassign on merge.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`WorkoutRepository.kt`)**: Add `onClusterRenamed`, `onClusterDeleted`, `onClusterMerged`, and `reloadWorkouts` (`REQ-PER-040.1`).
2. **Step 2 (`RoutesRepository.kt`)**: Add non-recursive `syncClusterNameChange`; notify `WorkoutClusterRepository.onRouteSyncedClusterRename` in `updateRouteSummary` (`REQ-PER-040.4`).
3. **Step 3 (`WorkoutClusterRepository.kt` & `WorkoutClusterEngine.kt`)**: Add `ClusterMutationEvent` + `clusterMutationEvents`, `splitCluster`, `onRouteSyncedClusterRename`, and propagate to `WorkoutRepository` & `RoutesRepository` across `updateCluster`, `deleteCluster`, `mergeClusters`, `splitCluster` (`REQ-PER-040.1`, `REQ-PER-040.4`).
4. **Step 4 (`WorkoutClustersViewModel.kt` & `WorkoutClustersScreen.kt`)**: Reconcile `_clusterWorkouts`, `_peekedWorkoutDataWithTrack`, `_selectedCluster`, `_unclusteredWorkouts`, and `inspectedWorkoutId` lookup (`REQ-PER-040.2`).
5. **Step 5 (`PeriodsViewModel.kt` & `WorkoutSummariesViewModel.kt`)**: Reconcile `PeriodsViewModel._peekedWorkoutDataWithTrack` and `WorkoutSummariesViewModel.filterCriteria` (`REQ-PER-040.2`, `REQ-PER-040.3`).
6. **Step 6 (Unit Tests — `TST-PER-048`)**: Add tests in `WorkoutClusterRepositoryTest`, `WorkoutClustersViewModelTest`, `PeriodsViewModelTest`, `WorkoutSummariesViewModelFilterTest`, `RoutesRepositorySyncTest`, and run targeted Gradle tests + `verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted Tier-1 unit tests (`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.repositories.*"`) and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Isolated on `feature/ATT-7758`; revertible cleanly without DB schema migration.
