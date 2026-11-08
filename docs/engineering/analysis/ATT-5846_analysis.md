# Stage 1 Analysis: ATT-5846 - ClusterItem & Filter (Lieblingsstrecken): Fix '0 Aufzeichnungen' count, destination location, landscape thumbnail & reset button wrap

**Ticket**: [ATT-5846](https://rainerblind.atlassian.net/browse/ATT-5846)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5846`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
Live UI inspections of `Lieblingsstrecken` (`screen_05_lieblingsstrecken.png`, `round5_05_lieblingsort_detail.png`, `round5_11_filter_sheet.png`) revealed four key presentation and telemetry defects:
1. **`0 Aufzeichnungen` badge on clusters**: In filtered views (e.g. `round5_05_lieblingsort_detail.png`), cluster cards (`bike2home (kurz)`, `Way Back Home`) display `0 Aufzeichnungen` despite having matching workouts in `WorkoutSummaries`.
2. **Missing destination location**: Cards only show `Start: [Location]` and fail to resolve destination coordinates (`endLat`, `endLng`), omitting critical context for point-to-point routes (`Start -> Ziel`) and loop routes (`Start & Ziel`).
3. **Map thumbnail whitespace & unconstrained equipment list**: Square 100x100dp map thumbnails force awkward vertical whitespace beside metadata, while multi-gear equipment lists wrap unbounded over 4–5 lines without ellipsis.
4. **2-line button wrap in filter sheet**: `FilterBottomSheetScaffold` formats the reset action as `Alle zurücksetzen`, which wraps onto two lines in the 50% split button row, producing uneven heights next to `OK`.

---

## 2. Forensic Root Cause Analysis
1. **Count desynchronization**:
   - `WorkoutClusterComponents.kt` directly renders `cluster.hitCount` from SQLite `RouteClusters`, bypassing authoritative aggregate metrics in `viewModel.clusterStats` (`WorkoutClusterStats.workoutCount`).
   - `WorkoutClusterRepository.loadAllClustersLightweight()` and `refreshClusters()` do not reconcile `cluster.hitCount` against `WorkoutSummaries` when loading or when `needsEnrichment` is false.
   - Initial route cluster generation (`WorkoutClusterEngine.learnFromRoute`, `manuallyCreateCluster`) seeds `hitCount = 0`, leaving existing route clusters at 0 until explicit reconciliation occurs.
2. **Unidirectional location resolution**:
   - `WorkoutClustersViewModel` only exposes `getStartLocationName(startLat, startLng)`. It lacks `getEndLocationName` or generalized `getLocationName(lat, lng)`.
   - `WorkoutClusterMetadataBlock` solely queries start coordinates, never resolving `cluster.endLat, cluster.endLng`.
3. **Card layout geometry & unbounded text**:
   - Map thumbnail is hardcoded as `Modifier.size(100.dp)` inside an `IntrinsicSize.Min` row. An aspect ratio of ~3:2 (e.g. 110.dp x 75.dp) matches route geography and eliminates whitespace.
   - Equipment string formatting lacks `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.
4. **Scaffold reset label**:
   - `FilterBottomSheetScaffold.kt` hardcodes `R.string.filter_clear_all` ("Alle zurücksetzen"). In a 50% button slot, the 17-character German string line-breaks. Replacing with concise `R.string.filter_reset` ("Zurücksetzen") across all 9 languages ensures single-line parity with uniform height.

---

## 3. Chesterton's Fence & Invariant Archaeology
- **`MapPreviewLiteModeTest` & `DarkMapAntiFlashOverlayTest`**: Any modification to `ClusterItem` map thumbnails must retain `GoogleMapOptions().liteMode(true)` and `DarkMapAntiFlashOverlay` to prevent rendering regressions or white flashes.
- **`ActiveFilterChipsRowClearAllContractTest`**: `filter_clear_all` ("Alle zurücksetzen") is strictly enforced for horizontal filter chip rows (`ActiveFilterChipsRow.kt`), but is NOT required for bottom-sheet action buttons.
- **Zero-touch auto-naming (`ATT-714`, `REQ-SET-067`)**: `hitCount` in `WorkoutCluster` controls autonaming counters (`#1`, `#2`); healing `hitCount` must strictly align with actual `WorkoutSummaries` counts.

---

## 4. Scope Bounding & Proposed Architecture
- **Non-destructive Reconciliation**:
  - `WorkoutClusterMetadataBlock`: Derive displayed count via `stats?.workoutCount ?: cluster.hitCount` (or `maxOf(cluster.hitCount, stats?.workoutCount ?: 0)`), guaranteeing accurate display even before DB reconciliation.
  - `WorkoutClusterRepository`: Heal cluster `hitCount` from `WorkoutSummaries` during `loadAllClustersLightweight()`.
- **Bidirectional Location Display**:
  - Expose `getLocationName(lat, lng)` in `WorkoutClustersViewModel`.
  - In `WorkoutClusterMetadataBlock`: If start == end, show `Start & Ziel: [Name]` (`R.string.workout_start_and_destination`); if different, show `[Start] → [Ziel]`; if only one endpoint resolved, show `Start: [Start]` or `Ziel: [Ziel]`.
- **Card Polish**:
  - Map thumbnail sized to `width = 110.dp, height = 75.dp` with rounded corners.
  - Linked equipment constrained to `maxLines = 1, overflow = TextOverflow.Ellipsis`.
- **Filter Sheet Button**:
  - Add `filter_reset` ("Zurücksetzen" / "Reset") across all 9 supported languages without emojis (`ATT-5400`) and apply to `FilterBottomSheetScaffold` with `maxLines = 1`.

---

## 5. Independent Peer Review & Verification
- **Verification Strategy**:
  - Unit tests for location resolution (loop route vs point-to-point vs single endpoint).
  - Repository lightweight load reconciliation unit test (`hitCount` 0 healed to workout count).
  - UI visual contract tests verifying landscape dimensions (110x75dp) and single-line ellipsis for equipment.
  - 9-language localization audit verifying `filter_reset` string parity.
