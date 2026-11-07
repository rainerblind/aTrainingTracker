# Stage 3: Implementation Plan - ATT-5666: Workout Cluster Merge

**Ticket**: [ATT-5666](https://rainerblind.atlassian.net/browse/ATT-5666)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 3 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5666`  
**Requirement Mapping**: `REQ-SET-089` (`REQ-SET-089.1`–`REQ-SET-089.5`) | **Test Mapping**: `TST-SET-089` (`TST-SET-089.1`–`TST-SET-089.4`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Background
When athletes inadvertently create duplicate Workout Clusters (*Lieblingsstrecken*) for the same real-world route, deleting a cluster drops historical workout links (`clusterId = -1L`). Athletes need a directional long-click Merge workflow: selecting *"Mit anderer Lieblingsstrecke zusammenführen…"*, choosing from candidates ranked by spatial similarity, confirming the operation, and atomically reassigning all workouts/routes from source `A` into target `B` while synthesizing combined weighted centroids and deleting `A`.

---

## 2. Invariants & Preserved Behavior
1. **Centroid & Weighting Invariant (`REQ-SET-089.4`)**: Target `B`'s updated centroid is strictly weighted by workout counts: $\text{coord}_{\text{new}} = \frac{\text{coord}_B \cdot N_B + \text{coord}_A \cdot N_A}{N_B + N_A}$. If $N_A + N_B == 0$, fall back to target coordinates.
2. **Data Isolation Invariant (`REQ-SET-089.4`)**: Workouts and routes outside clusters `A` and `B` remain untouched. Reassignment is executed inside a transaction.
3. **9-Language Parity & Specifiers (`REQ-SET-089.5`)**: All new strings exist across all 9 locales; confirmation message strictly uses indexed positional specifiers (`%1$s`, `%2$d`, `%3$s`) with zero emojis (`ATT-5400`).
4. **Code Lock (Rule 4)**: No production source code modifications until Gate 3 audit passes.

---

## 3. UI Consistency & Architectural Strategy
* **Reference Patterns**: `DeleteConfirmationDialog` / M3 `AlertDialog` for confirmation; `WorkoutClusterSelectionDialog` (`AppModalBottomSheet`) for candidate selection.
* **Component Reusability**: Reuses `WorkoutClusterEngine.scoreClusters` for similarity calculation and `WorkoutClusterSelectionDialog` for presentation.
* **Iconography**: Material `Icons.Default.CallMerge` for the context menu item.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language Localization Parity (`REQ-SET-089.3`, `REQ-SET-089.5`, `TST-SET-089.3`)
* **Files**: `app/src/main/res/values*/strings.xml` (all 9 locales)
* **Changes**: Add `cluster_menu_merge`, `cluster_merge_dialog_title`, `cluster_merge_dialog_message` (`%1$s`, `%2$d`, `%3$s`), and `cluster_merge_action` with 100% translation parity.

### Step 2: Database Reassignment Queries (`REQ-SET-089.4`)
* **Files**:
  - `WorkoutSummariesDatabaseManager.java`: Add `reassignClusterWorkouts(long sourceId, long targetId): int`.
  - `RoutesDatabaseManager.kt`: Add `reassignClusterRoutes(sourceId: Long, targetId: Long): Int`.

### Step 3: Transactional Engine Merge & Centroid Synthesis (`REQ-SET-089.4`, `TST-SET-089.1`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`
* **Changes**: Implement `mergeClusters(context: Context, sourceId: Long, targetId: Long): Boolean`:
  - Reassign workouts & routes from `sourceId` to `targetId`.
  - Synthesize combined weighted centroid (`start`, `end`, `apex`, `refDistance`, `minAlt`, `maxAlt`).
  - Merge bounding box (`minLat`, `maxLat`, `minLng`, `maxLng`) and preview paths.
  - Reconcile probable sport via `getMostFrequentSportIdForCluster`.
  - Update `targetCluster`, delete `sourceCluster`, and invoke `repository.refreshClusters()`.

### Step 4: ViewModel Scoring & Merge Operations (`REQ-SET-089.2`, `TST-SET-089.2`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt`
* **Changes**:
  - Add `getCandidateClustersForCluster(cluster: WorkoutCluster): List<Pair<WorkoutCluster, Double>>` scoring remaining clusters ascending by similarity.
  - Add `mergeClusters(source: WorkoutCluster, target: WorkoutCluster)` launching IO coroutine.

### Step 5: Compose Context Menu & Dialog Orchestration (`REQ-SET-089.1`, `REQ-SET-089.2`, `REQ-SET-089.3`)
* **Files**:
  - `WorkoutClusterComponents.kt`: Add `onMergeRequest: (WorkoutCluster) -> Unit` to `ClusterItem` with `DropdownMenuItem` (`Icons.Default.CallMerge`).
  - `WorkoutClustersList.kt` / `WorkoutClustersTabsScreen.kt`: Forward `onMergeRequest`.
  - `WorkoutClustersScreen.kt`: Manage `clusterToMerge` and `mergeTargetCluster`; display `WorkoutClusterSelectionDialog` and confirmation `AlertDialog`.

### Step 6: Automated Testing & Governance (`TST-SET-089.1`–`TST-SET-089.4`)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterMergeTest.kt`: Author unit test for centroid math and reassignment logic.
  - Run `TranslationParityTest` and `verify_requirement_governance.py`.

---

## 5. Verification Commands (`TST-SET-089.4`)
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`
* `python3 tools/verify_requirement_governance.py`
