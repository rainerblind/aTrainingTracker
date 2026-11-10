# Stage 3: Implementation Plan - ATT-6318: WorkoutClusterSplitSheet Embedded Map Preview

**Ticket**: [ATT-6318](https://atrainingtracker.atlassian.net/browse/ATT-6318) | **Epic**: [ATT-176](https://atrainingtracker.atlassian.net/browse/ATT-176) | **Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.1` | **Branch**: `feature/ATT-6318` | **Req/Test**: `REQ-UI-505` / `TST-UI-505`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Architecture Rationale
`WorkoutClusterSplitSheet` is a modal bottom sheet whose darkened scrim obscures background map sub-corridors (`splitVariantPaths`), while list views lack a background map entirely. We embed a color-coded `PathPreviewMap` directly above the sensitivity slider inside `WorkoutClusterSplitSheet`, dynamically updating on slider drag, and wire the split action into `WorkoutClustersScreen` cluster card 3-dot context menus.

---

## 2. System Invariants & Preserved Behavior
1. **Core Clustering Invariant**: `WorkoutClusterEngine.kt` heuristics and medoid algorithms SHALL NOT be modified.
2. **Map Preview Compatibility**: `PathPreviewMap.kt` single-path callers (`CreateLapOnMapSheet`, `MoveLapBoundaryOnMapSheet`, `CropWorkoutScreen`, `WorkoutSummary`) and `liteMode(true)` touch isolation remain 100% intact.
3. **Localization & Governance**: 9-language translation parity (`cluster_split_action`) and `verify_requirement_governance.py` remain clean.

---

## 3. UI Consistency (Design Guidelines)
* **Reference UI**: `CreateLapOnMapSheet.kt` & `MoveLapBoundaryOnMapSheet.kt` (embedded 200dp `PathPreviewMap` inside `RoundedCornerShape(12.dp)` Surface container directly above precision slider).
* **Color System**: Corridor variants rendered via `getVariantColor(index)` matching `TTColor.ClusterVariantPalette` and numbered variant badges 1, 2, 3...

---

## 4. Step-by-Step Implementation Sequence

### Step 1: Multi-Path Support in PathPreviewMap (`REQ-UI-505.3`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt`
* **Changes**: Add parameter `paths: List<MappablePath> = emptyList()`; iterate and render each path as `Polyline`; calculate composite bounding box across all paths and `targetBounds`.

### Step 2: Embedded Preview in WorkoutClusterSplitSheet (`REQ-UI-505.1`, `REQ-UI-505.2`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterSplitSheet.kt`
* **Changes**: Add 200dp rounded `Surface` container directly above slider column hosting `PathPreviewMap(paths = splitVariantPaths, targetBounds = clusterBounds)`; call `LaunchedEffect(cluster.id) { viewModel.openSplitPreview(cluster) }`.

### Step 3: Standalone Split Preparation in ViewModel (`REQ-UI-505.2`, `REQ-UI-505.4`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt`
* **Changes**: Enhance `openSplitPreview(cluster: WorkoutCluster? = null)` to asynchronously load cluster workouts when invoked without an active `_selectedCluster`; update `executeClusterSplit` and `closeSplitPreview`.

### Step 4: List-Level Context Menu & Screen Integration (`REQ-UI-505.4`)
* **Files**: `WorkoutClusterComponents.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersList.kt`, `WorkoutClustersScreen.kt`
* **Changes**: Add `onSplitRequest` to `WorkoutClusterCard` 3-dot menu (`cluster.hitCount >= 2`, `Icons.Default.CallSplit`, `R.string.cluster_split_action`); bubble up to `WorkoutClustersScreen.kt`; display `WorkoutClusterSplitSheet` when `clusterToSplit != null`.

### Step 5: Contract Test Verification Suite (`TST-UI-505.1` – `TST-UI-505.3`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterSplitMapPreviewTest.kt`
* **Changes**: Assert `WorkoutClusterSplitSheet` embeds `PathPreviewMap`; `PathPreviewMap` has multi-path parameter; `WorkoutClusterCard` menu includes split action.

### Step 6: Targeted Unit & Governance Test Execution (`TST-UI-505.4`)
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * `python3 tools/verify_requirement_governance.py`
