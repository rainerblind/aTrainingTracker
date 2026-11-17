# Stage 3: Implementation Plan - ATT-8464: Remove Split Action from List Context Menu

**Ticket**: [ATT-8464](https://rainerblind.atlassian.net/browse/ATT-8464) | **Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Route Clusters*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8464`  
**Requirements**: `REQ-UI-505` (`REQ-UI-505.4`, `REQ-UI-505.5`), `REQ-SET-096.1` (preserved)  
**Tests**: `TST-UI-505` (`TST-UI-505.3`, `TST-UI-505.4`) | **Risk**: `LOW` (`1 SP`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Problem Description & Architectural Scope
`WorkoutClusterComponents.kt` (`ClusterItem`, `L433-442`) currently exposes *"Lieblingsstrecke aufteilen…"* (`R.string.cluster_split_action`) inside the list-level long-click `DropdownMenu`, threaded via `onSplitRequest` through `WorkoutClustersList.kt`, `WorkoutClustersTabsScreen.kt`, and `WorkoutClustersScreen.kt`. Per PO decision, route splitting belongs exclusively in the detail/heatmap screen (`WorkoutClusterHeatmapScreen.kt:808-815`). This plan removes the list context menu item and callback wiring cleanly while preserving all detail-view split functionality, `WorkoutClusterSplitSheet`, `WorkoutClustersViewModel.openSplitPreview()`, and 9-locale `cluster_split_action` parity.

## 2. Traceability & Requirements Mapping
* `REQ-UI-505.4`: Exclude `cluster_split_action`, `Icons.Default.CallSplit`, and `onSplitRequest` from `ClusterItem`; remove `onSplitRequest`, `clusterToSplit`, and `WorkoutClusterSplitSheet` host from `WorkoutClustersList.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersScreen.kt`.
* `REQ-UI-505.5` & `REQ-SET-096.1`: Preserve `WorkoutClusterHeatmapScreen.kt:808-815` `CallSplit` action, `WorkoutClusterSplitSheet.kt`, `WorkoutClustersViewModel.openSplitPreview()`, and 9-locale `R.string.cluster_split_action`.
* `TST-UI-505.3`: Update `WorkoutClusterSplitMapPreviewTest.kt` (`// Traces to: REQ-UI-505`) to assert list-level exclusion alongside Heatmap/9-locale preservation.
* `TST-UI-505.4`: Targeted unit test + detekt verification across `ui.clusters.*` and `GlobalDeleteContextMenuAuditTest`.

## 3. System Invariants & Code Lock
1. **Stage 3 Code Lock**: ZERO Kotlin, XML, or `detekt-baseline.xml` edits during Stage 3.
2. **Detail-View Split Intact**: `WorkoutClusterHeatmapScreen.kt` (`CallSplit` icon for `hitCount >= 2`), `WorkoutClusterSplitSheet.kt` (embedded `PathPreviewMap`), and `WorkoutClustersViewModel.openSplitPreview()` remain untouched.
3. **Localization Parity**: `R.string.cluster_split_action` preserved in all 9 `values*/strings.xml` files (`WorkoutClusterSplitDialogContractTest`).
4. **List Context Menu Hygiene**: `ClusterItem` retains TopStart-anchored `DropdownMenu` with Merge (`cluster_menu_merge`) and Delete (`delete`) (`GlobalDeleteContextMenuAuditTest`).
5. **Layering**: Pure UI layer cleanup; zero ViewModel, Repository, Engine, or SQLite changes.

## 4. UI Consistency (`docs/design_guidelines.md`)
* Complies with single-purpose context menu ergonomics: list long-click menu retains strictly list-level actions (Merge & Delete), leaving single-route structural editing (`CallSplit`) inside the detail view (`WorkoutClusterHeatmapScreen`). No new controls (`FilterChip`/`Switch`) introduced.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (`WorkoutClusterComponents.kt`)**: Remove `onSplitRequest: ((WorkoutCluster) -> Unit)? = null` from `ClusterItem` (`L256`), remove the `if (cluster.hitCount >= 2 && onSplitRequest != null)` `DropdownMenuItem` block (`L433-442`), and remove dead `import androidx.compose.material.icons.filled.CallSplit` (`L27`). Retain `onMergeRequest` and `onDeleteRequest`. *(Traces to: `REQ-UI-505.4`)*
* **Step 2 (`WorkoutClustersList.kt` & `WorkoutClustersTabsScreen.kt`)**: Remove `onSplitRequest` parameter (`L51` / `L68`) and forwarding argument (`L88` / `L199`). *(Traces to: `REQ-UI-505.4`)*
* **Step 3 (`WorkoutClustersScreen.kt`)**: Remove `clusterToSplit` state (`L332`), `onSplitRequest = { clusterToSplit = it }` (`L352`), and `if (clusterToSplit != null) { WorkoutClusterSplitSheet(...) }` (`L357-366`). *(Traces to: `REQ-UI-505.4`)*
* **Step 4 (`WorkoutClusterSplitMapPreviewTest.kt`)**: Update `testClusterItem_and_WorkoutClustersScreen_wireSplitAction` (with `// Traces to: REQ-UI-505`) to assert absence of `onSplitRequest`, `clusterToSplit`, `cluster_split_action`, `Icons.Default.CallSplit`, and `WorkoutClusterSplitSheet(` across list files, while asserting `ClusterItem` retains `cluster_menu_merge`/`delete` and `WorkoutClusterHeatmapScreen.kt` retains `CallSplit` & `WorkoutClusterSplitSheet(`. *(Traces to: `TST-UI-505.3`, `REQ-UI-505.5`)*
* **Step 5 (`app/detekt-baseline.xml` — only if needed)**: Update obsolete `ClusterItem` / `WorkoutClustersTabsScreen` / `WorkoutClusterSplitMapPreviewTest` baseline signature entries only if `./gradlew detekt` flags signature drift (keep baseline valid and non-expanding).
* **Step 6 (Verification)**: Run targeted tests & detekt:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.GlobalDeleteContextMenuAuditTest"
  ./gradlew detekt
  ```

## 6. Verification & Rollback Plan
* **Verification**: Targeted `ui.clusters.*` + `GlobalDeleteContextMenuAuditTest` + `detekt` pass 100%, followed by clean-room full suite & living doc `[Verified]` promotion in Stage 5.
* **Rollback**: Isolated on `feature/ATT-8464`; trivial `git revert` restores list-level split entry if required.
