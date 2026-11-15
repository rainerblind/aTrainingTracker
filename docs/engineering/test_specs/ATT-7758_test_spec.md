# Stage 2: Requirement & Test Specification - ATT-7758: In-Memory RouteCluster Propagation

**Ticket**: [ATT-7758](https://issue-tracker/ATT-7758)  
**Parent Epic**: [ATT-176](https://issue-tracker/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7758`  
**Requirement Mapping**: `REQ-PER-040` | **Test Spec ID**: `TST-PER-048`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-PER-040)

### 1.1 Problem Statement & Rationale
`WorkoutCluster` rename, merge, delete, or split operations update SQLite and `_allClusters`, but in-memory `WorkoutData.clusterName` and `clusterId` across `WorkoutRepository._allWorkouts`, `WorkoutClustersViewModel`, and `PeriodsViewModel` remain stale until restart. Furthermore, `WorkoutSummariesViewModel` active filter chips desync, and cluster renames omit syncing linked routes in `RoutesRepository`.

### 1.2 Functional & Architectural Requirements
1. **In-Memory Workout Propagation (`REQ-PER-040.1`)**: `WorkoutClusterRepository` operations (`updateCluster`, `deleteCluster`, `mergeClusters`, `splitCluster`, and route-sync `syncRouteNameChange`) SHALL immediately notify `WorkoutRepository` to update cached `_allWorkouts` (`clusterName` updated on rename, cleared to `-1L`/`null` on delete, reassigned on merge/split).
2. **ViewModel & Peek Sheet Reconciliation (`REQ-PER-040.2`)**: `WorkoutClustersViewModel` (`_clusterWorkouts`, `_peekedWorkoutDataWithTrack`, `_selectedCluster`, `_unclusteredWorkouts`), `WorkoutClustersScreen` (`inspectedWorkout`), and `PeriodsViewModel` (`_peekedWorkoutDataWithTrack`) SHALL immediately reflect cluster mutations.
3. **Filter Chip Live Synchronization (`REQ-PER-040.3`)**: `WorkoutSummariesViewModel.filterCriteria` and `ActiveFilterChipsRow` SHALL reactively update `clusterName` on rename, clear on delete, and reassign on merge.
4. **Bidirectional Route-Cluster Name Sync (`REQ-PER-040.4`)**: `WorkoutClusterRepository.updateCluster()` SHALL update linked routes in `RoutesRepository` without recursion; `RoutesRepository.updateRouteSummary()` SHALL refresh clusters and propagate in-memory workout updates.

### 1.3 Acceptance Criteria & Invariants
* *Given* workouts cached in `WorkoutRepository._allWorkouts`,
  * *When* cluster is renamed, deleted, merged, or split,
  * *Then* in-memory `clusterName` / `clusterId` update immediately across all screens without changing `WorkoutData.workoutName`.
* *Given* an active cluster filter in `WorkoutSummariesViewModel`,
  * *When* the cluster is renamed, merged, or deleted,
  * *Then* `filterCriteria` and `ActiveFilterChipsRow` reflect the new name or cleared filter immediately.
* *Invariants*: `WorkoutData.workoutName` immutability preserved; non-recursive Route<->Cluster sync; plain JVM unit test isolation (`coding_standards.md` §5.5). Zero new UI strings (9-language localization parity N/A).

---

## 2. Test Specification (TST-PER-048)

### Test Case 1: Cluster Mutation In-Memory Propagation (`TST-PER-048.1`)
* **Scope**: Unit Test (`WorkoutClusterRepositoryTest`, `WorkoutClustersViewModelTest`).
* **Assertions**: `updateCluster()` updates `clusterName` in `WorkoutRepository._allWorkouts`; `deleteCluster()` sets `-1L`/`null`; `mergeClusters()` updates to `targetId`/`targetName`; `splitCluster()` updates variant IDs and names.

### Test Case 2: Periods Peek Sheet Reactive Reconciliation (`TST-PER-048.2`)
* **Scope**: Unit Test (`PeriodsViewModelTest`).
* **Assertions**: `_peekedWorkoutDataWithTrack` updates `clusterName`/`clusterId` when `WorkoutRepository.allWorkouts` changes.

### Test Case 3: Filter Chip Live Synchronization (`TST-PER-048.3`)
* **Scope**: Unit Test (`WorkoutSummariesViewModelFilterTest`).
* **Assertions**: Active cluster filter updates `clusterName` on rename, clears on delete, reassigns on merge.

### Test Case 4: Bidirectional Non-Recursive Route-Cluster Sync (`TST-PER-048.4`)
* **Scope**: Integration Test (`RoutesRepositorySyncTest`, `WorkoutClusterRepositoryTest`).
* **Assertions**: Cluster rename updates route without recursion; route rename propagates cluster and workout updates.

### Test Case 5: Immutability, JVM Isolation & Targeted Regression (`TST-PER-048.5`)
* **Scope**: Unit Test & Regression (`WorkoutRepositoryTest`, `TranslationParityTest`).
* **Assertions**: `workoutName` unchanged; plain JVM tests pass with zero Context/Preference leaks.

---

## 3. Traceability Matrix & 9-Locale Audit

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-048.1]` | Unit | In-memory `WorkoutRepository` & `WorkoutClustersViewModel` propagation | `REQ-PER-040.1`, `REQ-PER-040.2` | Planned |
| `[TST-PER-048.2]` | Unit | `PeriodsViewModel._peekedWorkoutDataWithTrack` reactive sync | `REQ-PER-040.2` | Planned |
| `[TST-PER-048.3]` | Unit | `WorkoutSummariesViewModel` filter criteria & chip synchronization | `REQ-PER-040.3` | Planned |
| `[TST-PER-048.4]` | Integration | Bidirectional Route <-> Cluster sync without infinite loops | `REQ-PER-040.4` | Planned |
| `[TST-PER-048.5]` | Regression | `workoutName` immutability, plain JVM isolation, targeted suite | `REQ-PER-040`, `REQ-PRO-001` | Planned |

* **9-Language Localization Audit Note**: No new or altered user-facing string resources; 9-language localization parity is N/A.
