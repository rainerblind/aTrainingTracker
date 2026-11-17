# Stage 1 Analysis: ATT-8464 - WorkoutClustersScreen: Remove 'Split Favorite Route…' from Long-Click Context Menu

**Ticket**: [ATT-8464](https://rainerblind.atlassian.net/browse/ATT-8464) | **Epic**: `ATT-176` (*Lieblingsstrecken / Route Clusters*) | **Target**: `V5.0.0` | **Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8464` | **SP**: `1` (Recommended)

## 1. Problem Statement & Motivation
During PO review on Pixel 10, long-pressing a cluster card in `WorkoutClustersScreen` opens a context menu (`WorkoutClusterComponents.kt:429-461`) containing *"Lieblingsstrecke aufteilen…"* (`R.string.cluster_split_action`) alongside *"Mit anderer Lieblingsstrecke zusammenführen…"* (`cluster_menu_merge`) and *"Löschen"* (`delete`). Split does not belong in the list-level long-click menu and must be available exclusively from the cluster detail/heatmap view (`WorkoutClusterHeatmapScreen.kt`).

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
- **Root Cause**: `ATT-6318` (`ea954e51`) exposed `R.string.cluster_split_action` in `ClusterItem`'s long-click `DropdownMenu` (`WorkoutClusterComponents.kt:433-442`) and threaded `onSplitRequest` through `WorkoutClustersList.kt` -> `WorkoutClustersTabsScreen.kt` -> `WorkoutClustersScreen.kt:332,352,357-366`.
- **Independent Peer Review & Verification**:
  - `WorkoutClusterComponents.kt`: `ClusterItem` declares `onSplitRequest: ((WorkoutCluster) -> Unit)? = null` (`L256`), renders `DropdownMenuItem(R.string.cluster_split_action, Icons.Default.CallSplit)` when `cluster.hitCount >= 2 && onSplitRequest != null` (`L433-442`), and imports `Icons.Default.CallSplit` (`L27`, unused elsewhere in file).
  - `WorkoutClustersList.kt`: `L51,88` pass `onSplitRequest`; `WorkoutClustersTabsScreen.kt`: `L68,199` pass `onSplitRequest`. No other callers exist in `app/src/main`.
  - `WorkoutClustersScreen.kt`: `clusterToSplit` (`L332`), `onSplitRequest = { clusterToSplit = it }` (`L352`), and `WorkoutClusterSplitSheet(...)` (`L357-366`) are exclusively used for the list context-menu entry point.
  - `WorkoutClusterHeatmapScreen.kt`: `L808-815` (`IconButton(onClick = onSplit)` with `Icons.Default.CallSplit`, `contentDescription = stringResource(R.string.cluster_split_action)`), `L468-471` (`viewModel.openSplitPreview()`), and `L270-279` (`WorkoutClusterSplitSheet`) remain active and independent.
  - `WorkoutClusterSplitSheet.kt:63` (`viewModel.openSplitPreview(cluster)`) and `WorkoutClustersViewModel.kt:781` remain used by `WorkoutClusterHeatmapScreen.kt:271`.
  - `R.string.cluster_split_action` MUST remain across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) because `WorkoutClusterHeatmapScreen.kt:812` and `WorkoutClusterSplitDialogContractTest` depend on it.
  - `app/detekt-baseline.xml` (`L338,361,874,899,7178-7181`) references `onSplitRequest` in `ClusterItem`/`WorkoutClustersTabsScreen` signatures and `WorkoutClusterSplitMapPreviewTest` lines; baseline check needed in Stage 4/5.

## 3. User Scope Grounding (ATT-1250) & Complexity Assessment
- **In-Scope**:
  1. Remove `cluster_split_action` menu item, `onSplitRequest` parameter, and unused `CallSplit` import from `WorkoutClusterComponents.kt`.
  2. Remove `onSplitRequest` forwarding from `WorkoutClustersList.kt` and `WorkoutClustersTabsScreen.kt`, plus `clusterToSplit` / `WorkoutClusterSplitSheet` wiring from `WorkoutClustersScreen.kt`.
  3. Update `WorkoutClusterSplitMapPreviewTest.kt` (`L83-119`), `REQ-UI-505` / `TST-UI-505`, and verify `detekt-baseline.xml` / unit tests.
- **Out-of-Scope**: Any changes to `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterSplitSheet.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterEngine.kt`, or `strings.xml` (`cluster_split_action` kept).
- **Complexity**: **1 SP** (localized UI/wiring removal across 4 Kotlin files + 1 contract test; zero DB/service/algorithm changes).

## 4. Chesterton's Fence Archaeology (REQ-PRO-022)
- **Original Requirement ID & Target**: `REQ-UI-505` (`REQ-UI-505.4` in `docs/requirements/req_ui_design_system.md:16544-16546`) & `TST-UI-505.3` (`docs/tests/tst_ui_design_system.md:8159-8160`); original feature `REQ-SET-096.1` (`docs/requirements/req_settings_profiles.md:1978`).
- **Historical Origin & Commit Trace**: Split cluster action + `WorkoutClusterSplitSheet` introduced in `ATT-5900` (`43a25302`, `REQ-SET-096`) inside `WorkoutClusterHeatmapScreen`; `ATT-6318` (`ea954e51`, `REQ-UI-505`) added the embedded `PathPreviewMap` to `WorkoutClusterSplitSheet` and added list-level context menu entry (`REQ-UI-505.4`).
- **Root Reason for Existing Formulation**: `ATT-6318` added list-level split access so users could split without entering heatmap detail view, and embedded `PathPreviewMap` because the list screen had no background map.
- **Preservation of Core Invariants**: Removing `REQ-UI-505.4` (list menu entry) leaves `REQ-UI-505.1–505.3` (embedded map preview in `WorkoutClusterSplitSheet`) and `REQ-SET-096.1–096.4` (detail/heatmap `CallSplit` entry point, `hitCount >= 2` guard, corridor detection, atomic SQLite split) 100% intact.

## 5. Change-Request Impact Analysis (SUP.10 / REQ-PRO-064)
- **Affected Requirements**: `REQ-UI-505.4` (deprecated/removed from list view), `TST-UI-505.3` (updated to assert list menu excludes split); `REQ-UI-505.1–505.3` & `REQ-SET-096` unchanged.
- **Affected Architecture Elements**: `WorkoutClusterComponents.kt` (`ClusterItem`), `WorkoutClustersList.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersScreen.kt` (no ViewModel/Engine/DB changes).
- **Verification Impact & Test Surface**: `WorkoutClusterSplitMapPreviewTest.kt` (`testClusterItem_and_WorkoutClustersScreen_wireSplitAction` updated), `WorkoutClusterSplitDialogContractTest.kt` (9-locale parity + detail flow), `GlobalDeleteContextMenuAuditTest.kt`, `ClusterItemAndFilterPolishTest.kt`, `MapPreviewLiteModeTest.kt`.
- **Risk Assessment & Invariants**: **LOW** risk. Pure UI callback & menu item removal; Merge & Delete context menu items, `WorkoutClusterHeatmapScreen` `CallSplit` button, `WorkoutClusterSplitSheet` embedded map preview, and 9-language `cluster_split_action` string remain untouched.

## 6. Architectural Strategy & System Invariants
1. Remove `DropdownMenuItem` (`cluster_split_action`), `onSplitRequest`, and unused `Icons.Default.CallSplit` import in `WorkoutClusterComponents.kt`, keeping `onMergeRequest` and `onDeleteRequest` intact.
2. Remove `onSplitRequest` parameter/forwarding in `WorkoutClustersList.kt` and `WorkoutClustersTabsScreen.kt`, and remove `clusterToSplit` / `WorkoutClusterSplitSheet` block in `WorkoutClustersScreen.kt`.
3. Update `WorkoutClusterSplitMapPreviewTest.kt` to verify list screens omit `cluster_split_action`/`onSplitRequest` while `WorkoutClusterSplitSheet` embedded `PathPreviewMap` remains verified.
- **System Invariants**:
  - `WorkoutClusterHeatmapScreen.kt:808-815` (`CallSplit` icon, `hitCount >= 2`) & `WorkoutClusterSplitSheet` remain 100% functional.
  - `R.string.cluster_split_action` preserved in all 9 locales.
  - Long-click context menu in `ClusterItem` retains TopStart anchoring, Merge, and Delete.
- **Risk Rating**: **LOW** (1 SP).
