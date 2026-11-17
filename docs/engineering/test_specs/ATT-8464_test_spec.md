# Stage 2: Requirement & Test Specification - ATT-8464: Remove Split Action from List Context Menu

**Ticket**: [ATT-8464](https://rainerblind.atlassian.net/browse/ATT-8464) | **Sub-task**: `[Req & Test Spec]` | **Epic**: `ATT-176` (*Route Clusters*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8464`  
**Requirement Mapping**: `REQ-UI-505` (`REQ-UI-505.4`, `REQ-UI-505.5`) | **Test Spec ID**: `TST-UI-505` (`TST-UI-505.3`, `TST-UI-505.4`)  
**Invariant Preserved**: `REQ-SET-096.1` (`WorkoutClusterHeatmapScreen.kt:808-815`) | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-UI-505.4` Amendment)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.2 Functional & Architectural Requirements
1. **List-Level Context Menu Exclusion (`REQ-UI-505.4`)**:
   - `ClusterItem` (`WorkoutClusterComponents.kt`) SHALL NOT declare `onSplitRequest`, import `Icons.Default.CallSplit`, or render `R.string.cluster_split_action` in its long-click `DropdownMenu`, retaining exclusively Merge (`onMergeRequest`, `R.string.cluster_menu_merge`) and Delete (`onDeleteRequest`, `R.string.delete`).
   - `WorkoutClustersList.kt`, `WorkoutClustersTabsScreen.kt`, and `WorkoutClustersScreen.kt` SHALL NOT thread `onSplitRequest`, hold `clusterToSplit` state, or present `WorkoutClusterSplitSheet`.
2. **Detail View & Localization Invariants (`REQ-UI-505.5`, `REQ-SET-096.1`)**:
   - `WorkoutClusterHeatmapScreen.kt:808-815` (`Icons.Default.CallSplit` for `cluster.hitCount >= 2`), `WorkoutClusterSplitSheet.kt` (embedded `PathPreviewMap`), and `WorkoutClustersViewModel.openSplitPreview()` SHALL remain 100% functional.
   - `R.string.cluster_split_action` SHALL remain across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* any cluster card (including `hitCount >= 2`) in `WorkoutClustersScreen`, *When* the athlete long-presses the card, *Then* the context menu displays only *"Mit anderer Lieblingsstrecke zusammenführen…"* (`cluster_menu_merge`) and *"Löschen"* (`delete`), and *"Lieblingsstrecke aufteilen…"* (`cluster_split_action`) is absent.
* *Given* a cluster with `hitCount >= 2` in `WorkoutClusterHeatmapScreen`, *When* the athlete taps the header `CallSplit` icon (`R.string.cluster_split_action`), *Then* `WorkoutClusterSplitSheet` opens via `openSplitPreview()` with the embedded `PathPreviewMap` intact.
* *Given* all 9 locale string files, *When* `WorkoutClusterSplitDialogContractTest` runs, *Then* `cluster_split_action` and all `cluster_split_*` keys maintain 100% parity with zero inline emojis.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-505` (`REQ-UI-505.4`, introduced in `ATT-6318`) in `docs/requirements/req_ui_design_system.md`.
* **Historical Origin & Commit Trace**: Detail-view split (`REQ-SET-096.1`) introduced in `ATT-5900` (`43a25302`); list-level context menu entry (`REQ-UI-505.4`) and embedded `PathPreviewMap` added in `ATT-6318` (`ea954e51`).
* **Root Reason for Existing Formulation**: `ATT-6318` added list-level split access so users could split from the list view without opening the detail heatmap.
* **Preservation of Core Invariants**: Removing `REQ-UI-505.4` leaves `REQ-UI-505.1–505.3` (embedded map preview in `WorkoutClusterSplitSheet`), `REQ-SET-096.1–096.6` (`WorkoutClusterHeatmapScreen` `CallSplit` entry point, `hitCount >= 2`, corridor detector, atomic DB split), and 9-locale `cluster_split_action` parity 100% intact.

---

## 2. Test Specification (`TST-UI-505.3`, `TST-UI-505.4`)

1. **`TST-UI-505.3` (`WorkoutClusterSplitMapPreviewTest.kt` & `WorkoutClusterSplitDialogContractTest.kt`)**:
   - Update `testClusterItem_and_WorkoutClustersScreen_wireSplitAction` (renamed/refactored to assert exclusion) to verify `WorkoutClusterComponents.kt`, `WorkoutClustersList.kt`, `WorkoutClustersTabsScreen.kt`, and `WorkoutClustersScreen.kt` contain NO `onSplitRequest`, `clusterToSplit`, `cluster_split_action`, `Icons.Default.CallSplit`, or `WorkoutClusterSplitSheet(`.
   - Assert `WorkoutClusterComponents.kt` retains `cluster_menu_merge` and `R.string.delete` in `ClusterItem` context menu.
   - Assert `WorkoutClusterHeatmapScreen.kt` retains `Icons.Default.CallSplit`, `R.string.cluster_split_action`, `cluster.hitCount >= 2`, and `WorkoutClusterSplitSheet(`, and `WorkoutClusterSplitDialogContractTest` passes 9-locale parity for `cluster_split_action`.
2. **`TST-UI-505.4` (Targeted Unit Test & Lint Regression)**:
   - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.GlobalDeleteContextMenuAuditTest"` (plus `detekt` baseline check if needed) asserting 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-505.1]` | Contract | `WorkoutClusterSplitSheet.kt`: embedded `PathPreviewMap` above slider | `REQ-UI-505.1`, `REQ-UI-505.2` | Verified |
| `[TST-UI-505.2]` | Contract | `PathPreviewMap.kt`: multi-path rendering & composite bounds | `REQ-UI-505.3` | Verified |
| `[TST-UI-505.3]` | Contract / i18n | List screens exclude split menu/wiring; Heatmap & 9 locales preserve `cluster_split_action` | `REQ-UI-505.4`, `REQ-UI-505.5`, `REQ-SET-096.1` | Planned |
| `[TST-UI-505.4]` | Regression | Targeted `ui.clusters.*` + `GlobalDeleteContextMenuAuditTest` suite | `REQ-UI-505`, `REQ-PRO-001` | Planned |
