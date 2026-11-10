# Stage 2: Requirement & Test Specification - ATT-6318: WorkoutClusterSplitSheet Embedded Map Preview

**Ticket**: [ATT-6318](https://atrainingtracker.atlassian.net/browse/ATT-6318)  
**Parent Epic**: [ATT-176](https://atrainingtracker.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6318`  
**Requirement Mapping**: `REQ-UI-505` (`req_ui_design_system.md`) | **Test Spec ID**: `TST-UI-505` (`tst_ui_design_system.md`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-UI-505)

### 1.1 Problem Statement & Rationale
When opening `WorkoutClusterSplitSheet` (`AppModalBottomSheet`), detected sub-corridors (`splitVariantPaths`) are currently rendered only on the background heatmap of `WorkoutClusterHeatmapScreen`. The modal sheet and darkened scrim obscure the map. Furthermore, when browsing clusters in `WorkoutClustersScreen` (list view), no background map exists and the card 3-dot menu lacks a split action. The system SHALL embed an interactive color-coded map preview directly above the corridor sensitivity slider inside `WorkoutClusterSplitSheet.kt` and expose the split action in cluster card context menus.

### 1.2 Functional & Architectural Requirements
1. **Embedded Color-Coded Map Preview (`REQ-UI-505.1`)**:
   - `WorkoutClusterSplitSheet.kt` SHALL embed `PathPreviewMap` directly above the sensitivity slider inside a `RoundedCornerShape(12.dp)` container (`height = 200.dp`).
   - The embedded map SHALL render all detected corridor variants (`splitVariantPaths`) in their corresponding group colors (`getVariantColor(index)`).
   - The map SHALL auto-zoom to the cluster's bounding box (`cluster.bounds`) or encompass all detected variant polylines.
2. **Live Dynamic Map Synchronization (`REQ-UI-505.2`)**:
   - Moving the corridor sensitivity slider SHALL dynamically recalculate variant polylines and update the embedded map preview in real time.
3. **Multi-Path Support in PathPreviewMap (`REQ-UI-505.3`)**:
   - `PathPreviewMap.kt` SHALL support `paths: List<MappablePath> = emptyList()`, calculating composite bounds across all paths while preserving `liteMode(true)` touch isolation and single-path caller compatibility.
4. **List-Level Split Action Entry Point (`REQ-UI-505.4`)**:
   - In `WorkoutClusterComponents.kt` (`ClusterItem`), for clusters with `hitCount >= 2`, the 3-dot menu SHALL expose "Split Favorite Route…" (`cluster_split_action`, `Icons.Default.CallSplit`), opening `WorkoutClusterSplitSheet` directly from `WorkoutClustersScreen.kt`.
5. **System Invariants (`REQ-UI-505.5`)**:
   - `WorkoutClusterEngine.kt` heuristics SHALL NOT be modified; single-path callers of `PathPreviewMap` SHALL remain backward-compatible; 9-language localization parity SHALL be preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Embedded Preview Layout)**:
  * *Given* `WorkoutClusterSplitSheet` open for a route cluster with detected variants,
  * *When* viewing the sheet content,
  * *Then* a 200dp `PathPreviewMap` is rendered directly above the corridor sensitivity slider displaying color-coded variant polylines matching the variant badges.
* **Criterion 2 (Live Sensitivity Modulation)**:
  * *Given* the corridor sensitivity slider inside `WorkoutClusterSplitSheet`,
  * *When* dragged to modulate corridor threshold between 250m and 1500m,
  * *Then* `splitVariantPaths` recalculate and map polylines update dynamically.
* **Criterion 3 (List Context Menu Entry Point)**:
  * *Given* a cluster card in `WorkoutClustersScreen` with `hitCount >= 2`,
  * *When* opening the 3-dot context menu,
  * *Then* `cluster_split_action` is displayed, and selecting it opens `WorkoutClusterSplitSheet` with loaded workouts and embedded preview.

---

## 2. Test Specification (TST-UI-505)

### Test Case 1: Split Sheet Embedded Map Contract Test (`TST-UI-505.1`)
* **Scope**: `WorkoutClusterSplitMapPreviewTest.kt`.
* **Assertions**: Assert `WorkoutClusterSplitSheet.kt` embeds `PathPreviewMap` above sensitivity slider with `paths = splitVariantPaths`.

### Test Case 2: PathPreviewMap Multi-Path Support Contract Test (`TST-UI-505.2`)
* **Scope**: `PathPreviewMapContractTest.kt` / `WorkoutClusterSplitMapPreviewTest.kt`.
* **Assertions**: Assert `PathPreviewMap` supports `paths: List<MappablePath> = emptyList()` and composite bounding box calculation.

### Test Case 3: List Context Menu Split Action Contract Test (`TST-UI-505.3`)
* **Scope**: `WorkoutClusterComponentsContractTest.kt` / `WorkoutClusterSplitMapPreviewTest.kt`.
* **Assertions**: Assert `ClusterItem` context menu contains `cluster_split_action` for `hitCount >= 2` and triggers split sheet callback.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-UI-505.4`)
* **Scope**: Targeted unit tests (`ui.clusters.*`, `ui.map.*`, `ui.theme.*`, `ui.components.core.*`, `build.*`).
* **Assertions**: 100% pass across touched packages and architectural rules.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-505.1]` | Contract | `WorkoutClusterSplitSheet.kt`: Embedded `PathPreviewMap` above slider | `REQ-UI-505.1`, `REQ-UI-505.2` | Planned |
| `[TST-UI-505.2]` | Contract | `PathPreviewMap.kt`: Multi-path support & composite bounds | `REQ-UI-505.3` | Planned |
| `[TST-UI-505.3]` | Contract | `WorkoutClusterComponents.kt`: Context menu split action (`hitCount >= 2`) | `REQ-UI-505.4` | Planned |
| `[TST-UI-505.4]` | Regression | Tier 1 targeted suite (`ui.clusters.*`, `ui.map.*`, `build.*`) | `REQ-UI-505.5`, `REQ-PRO-001` | Planned |
