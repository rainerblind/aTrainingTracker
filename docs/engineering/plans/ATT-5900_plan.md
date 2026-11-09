# Stage 3: Implementation Plan - ATT-5900: Split Workout Cluster Dialog

**Ticket**: [ATT-5900](https://atrainingtracker.atlassian.net/browse/ATT-5900) | **Target Release**: `V5.0.0` | **Sprint**: `2026-45.13` | **Branch**: `feature/ATT-5900`  
**Requirement Mapping**: `REQ-SET-096` | **Test Mapping**: `TST-SET-096` | **Author**: AI Agent 1 (Implementer, `subagent_pro`)  

---

## 1. Problem Description & Architectural Context
Historical clusters (e.g. `Bike2Home (kurz)`, 350 sessions) aggregate multiple distinct route corridors (e.g. forest route vs. central city route) due to shared start/end/distance fingerprints. Blending these variants distorts `Bestzeit`, `Ø Zeit`, `Ø Geschwindigkeit`, and the `Leistungsverlauf` trend chart. Polyline data (`heatmapPaths`) is already loaded in `WorkoutClustersViewModel`, allowing automated sub-corridor detection and an interactive split dialog without extra disk reads.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-096` (*Workout Cluster Split via Polyline Sub-Corridor Detection & Interactive Map Preview*)
  * `REQ-SET-096.1`: `Split Cluster` action button (`Icons.Default.CallSplit`) in `WorkoutClusterHeatmapScreen` when `hitCount >= 2`.
  * `REQ-SET-096.2`: Fractional station polyline sampling $\{0.15, 0.30, 0.50, 0.70, 0.85\}$ & greedy medoid clustering against threshold $W \in [250\text{ m}, 1500\text{ m}]$.
  * `REQ-SET-096.3`: Interactive `WorkoutClusterSplitSheet` (`AppModalBottomSheet`) with live slider and color-coded map preview.
  * `REQ-SET-096.4`: Variant list with checkboxes, color pills, workout count, average distance, editable name fields, and workout renaming option.
  * `REQ-SET-096.5`: Atomic SQLite transaction inserting new clusters, reassigning workouts in `WorkoutSummaries`, and recalculating centroids, bounds, and stats.
  * `REQ-SET-096.6`: 100% 9-language localization parity with zero inline emojis (`ATT-5400`).
* **Test Mapping**: `TST-SET-096` (`TST-SET-096.1` to `TST-SET-096.5`).

---

## 3. System Invariants & Preserved Behavior
1. **Transactional Atomicity**: Any failure during split rolls back completely; workouts not part of selected variants remain in the primary cluster.
2. **Hit Count & Stats Parity**: Cluster `hitCount` and `WorkoutClusterStats` strictly derived from `WorkoutSummaries` without desynchronization.
3. **UI Consistency (Design Guidelines §5.8)**: Bottom popup standard (`AppModalBottomSheet`), theme color tokens (`TTColor`), and non-unmounting overlay.
4. **Non-Blocking Geometry Computation**: Polyline station sampling and clustering executed strictly on `Dispatchers.Default`.

---

## 4. Proposed Architectural Changes
* **Component 1: Sub-Corridor Detection (`WorkoutClusterCorridorDetector.kt`)**: Pure algorithmic utility sampling polylines at 5 fractional stations, computing max geodesic deviation, and clustering into variant groups ranked by size ($< 150$ lines).
* **Component 2: Split Bottom Sheet UI (`WorkoutClusterSplitSheet.kt`)**: Modular `AppModalBottomSheet` containing the sensitivity slider, variant rows with color badges, editable cluster names, workout rename checkbox, and split CTA ($< 220$ lines).
* **Component 3: ViewModel State & Dispatch (`WorkoutClustersViewModel.kt`)**: Expose split preview state, reactive corridor recalculation on slider change, and `splitCluster(...)` action triggering repository refresh.
* **Component 4: Heatmap Screen Integration (`WorkoutClusterHeatmapScreen.kt`)**: Add `CallSplit` button to `WorkoutClusterSummaryHeader` and conditionally render color-coded variant tracks during split preview.
* **Component 5: Transactional Database Split (`WorkoutClusterEngine.kt`, `WorkoutSummariesDatabaseManager.java`)**: Implement `reassignWorkoutsToCluster(...)` in `WorkoutSummariesDatabaseManager` and `splitCluster(...)` in `WorkoutClusterEngine` with weighted centroid synthesis and stats recomputation.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Sub-Corridor Algorithm**: Implement `WorkoutClusterCorridorDetector.kt` and unit test `WorkoutClusterCorridorDetectorTest.kt` (`TST-SET-096.1`).
* **Step 2: Database Reassignment & Centroid Synthesis**: Add `reassignWorkoutsToCluster` in `WorkoutSummariesDatabaseManager.java` and `splitCluster` in `WorkoutClusterEngine.kt`.
* **Step 3: Engine Split Unit Tests**: Author `WorkoutClusterSplitTest.kt` verifying multi-group transactional split, workout reassignment, and centroid recalculation (`TST-SET-096.2`).
* **Step 4: ViewModel Split Orchestration**: Add split preview state and action handlers in `WorkoutClustersViewModel.kt`.
* **Step 5: UI Construction & Heatmap Wiring**: Create `WorkoutClusterSplitSheet.kt` and wire `CallSplit` button & variant polyline preview in `WorkoutClusterHeatmapScreen.kt`.
* **Step 6: UI Contract & Regression Verification**: Author `WorkoutClusterSplitDialogContractTest.kt` (`TST-SET-096.3`), run `TranslationParityTest` (`TST-SET-096.4`), and execute Tier 1 test suite (`TST-SET-096.5`).

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`WorkoutClusterCorridorDetectorTest`, `WorkoutClusterSplitTest`, `WorkoutClusterSplitDialogContractTest`), `TranslationParityTest`, and `verify_requirement_governance.py`.
* **Rollback**: Clean git restore / branch reset on `feature/ATT-5900` without database schema migrations.
