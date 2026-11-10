# Stage 1 Analysis: WorkoutClusterSplitSheet Embedded Map Preview (ATT-6318)

## 1. Problem Domain & Forensic Root Cause
* **Ticket Key**: `ATT-6318` | **Target Release**: `V5.0.0` | **Epic**: `ATT-176` (Route Clusters) | **SP**: 2
* **Root Cause**: `WorkoutClusterSplitSheet` opens as an `AppModalBottomSheet`. The detected sub-corridors (`splitVariantPaths`) are currently only drawn on the underlying `WorkoutClusterHeatmapScreen` map, which is obscured by the sheet's darkened modal scrim. When opened from `WorkoutClustersScreen` (list view), no map exists behind the sheet, and the list card 3-dot menu lacks a split action altogether.
* **Goal**: Embed a color-coded map preview directly above the corridor sensitivity slider inside `WorkoutClusterSplitSheet.kt`, updating dynamically as the slider moves, and wire the split action into `WorkoutClustersScreen` card context menus.

## 2. Independent Peer Review & Verification (4-Augen-Prinzip)
* **Codebase Audit**: Audited `WorkoutClusterSplitSheet.kt`, `PathPreviewMap.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersScreen.kt`, and `WorkoutClustersViewModel.kt`.
* **Verification**: `PathPreviewMap.kt` currently accepts only a single `path: MappablePath?`. Adding `paths: List<MappablePath> = emptyList()` allows multi-polyline rendering while preserving `liteMode(true)` (`MapPreviewLiteModeTest`) and avoiding bottom-sheet touch conflicts.
* **Invariant Guard**: `WorkoutClusterEngine.kt` MUST NOT be modified.

## 3. Chesterton's Fence & Cross-Caller Side-Effect Audit
* **Historical Requirement**: `REQ-SET-096.3` (`ATT-5900`) established corridor detection and split previews. Preserving `splitVariantPaths` emission ensures `WorkoutClusterHeatmapScreen` continues to work.
* **Cross-Caller Impact (`Rule 68`)**:
  * `PathPreviewMap.kt`: Extended with `paths: List<MappablePath> = emptyList()`. Existing single-path callers (`CreateLapOnMapSheet`, `MoveLapBoundaryOnMapSheet`, `CropWorkoutScreen`, `WorkoutSummary`) remain intact.
  * `WorkoutClustersViewModel.kt`: Add `openSplitPreview(cluster: WorkoutCluster? = null)` to hydrate workouts asynchronously when invoked from `WorkoutClustersScreen` without corrupting `_selectedCluster`.
  * `WorkoutClusterComponents.kt`: Add `onSplitRequest` to `WorkoutClusterCard` menu, guarded by `cluster.hitCount >= 2`.

## 4. Scope Grounding & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope**:
  1. Multi-path rendering and cluster bounding box auto-zoom in `PathPreviewMap.kt`.
  2. Embedded 200dp map preview above slider in `WorkoutClusterSplitSheet.kt` observing `splitVariantPaths`.
  3. `openSplitPreview(cluster)` overload in `WorkoutClustersViewModel.kt` for standalone split execution.
  4. 3-dot menu item `cluster_split_action` in `WorkoutClusterComponents.kt`, bubbled through `WorkoutClustersTabsScreen` to `WorkoutClustersScreen.kt`.
* **Out-of-Scope**: Modifications to `WorkoutClusterEngine.kt`, clustering heuristics, or list sorting. Complexity is bounded at 2 SP.

## 5. Implementation Surfaces & Requirement Mapping
* **Mapped Requirement**: `REQ-UI-505` (`req_ui_design_system.md`) / `TST-UI-505` (`tst_ui_design_system.md`).

| Component | File Path | Scope of Modification |
| :--- | :--- | :--- |
| **Map Preview** | `ui/map/PathPreviewMap.kt` | Support `paths: List<MappablePath>`, multi-polyline loop, multi-path bounds |
| **Split Sheet** | `ui/clusters/WorkoutClusterSplitSheet.kt` | Surface container with `PathPreviewMap` directly above slider |
| **ViewModel** | `ui/clusters/WorkoutClustersViewModel.kt` | Support `openSplitPreview(cluster)` with async workout loading |
| **List & Cards**| `ui/clusters/WorkoutClusterComponents.kt`<br/>`ui/clusters/WorkoutClustersScreen.kt` | Wire `onSplitRequest` in 3-dot menu (`cluster.hitCount >= 2`) and show sheet |
| **Governance** | `docs/requirements/req_ui_design_system.md`<br/>`docs/tests/tst_ui_design_system.md` | Formalize `REQ-UI-505` / `TST-UI-505` specification and verification criteria |
