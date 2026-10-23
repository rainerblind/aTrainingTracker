# Stage 2: Requirement & Test Specification - ATT-4772

**Ticket**: [ATT-4772](https://rainerblind.atlassian.net/browse/ATT-4772)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.10 | **Active Sprint**: `sprint/2026-43.10`  
**Requirement Mapping**: `REQ-PER-019.4` (*Regional Route Network Heatmap Integration & Adaptive Vector Polyline Suppression*), amending `REQ-PER-012`  
**Test Spec ID**: `TST-PER-025.7`  
**Branch**: `feature/ATT-4772`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (REQ-PER-019.4)

### 1.1 Problem Statement & Rationale
In `PeriodSummaryCard.kt` (`PeriodMultiWorkoutMap`), every path in `allPaths` is unconditionally rendered as a vector `Polyline` composable. For Month and Year periods with $> 30$ workouts, this allocates hundreds of native Google Maps `Polyline` JNI objects inside `LazyColumn` and visually washes out the translucent `PolylineHeatmapTileProvider` gradient.

### 1.2 Functional & Architectural Requirements
1. **Adaptive Polyline Suppression**: When active paths exceed `MAX_PERIOD_VECTOR_TRACKS = 30` and `visuals.heatmapProvider != null`, `PeriodMultiWorkoutMap` SHALL suppress rendering individual vector `Polyline` composables in favor of the raster heatmap.
2. **State Flag Exposure**: `PeriodCardMapState` SHALL expose `val renderVectorPolylines: Boolean = true`, computed in `prepareCardMapPreviewSync` as `visuals?.heatmapProvider == null || activePaths.size <= MAX_PERIOD_VECTOR_TRACKS`.
3. **Small-Period & Day Retention**: For periods with $\le 30$ workouts or where `heatmapProvider == null` (such as `DAY` cards), vector polylines SHALL be retained (`renderVectorPolylines == true`).
4. **Source Priority Invariant**: `prepareCardMapPreviewSync` SHALL prioritize `summary.polylines` over `summary.workoutIdToPolylineMap`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1**: *Given* a Month or Year period with $> 30$ workouts and active heatmap, *When* `PeriodSummaryCard` renders, *Then* `renderVectorPolylines` evaluates to `false` and zero vector `Polyline` composables are composed underneath the heatmap.
* **AC-2**: *Given* a period with $\le 30$ workouts or a `DAY` period, *When* `PeriodSummaryCard` renders, *Then* `renderVectorPolylines` evaluates to `true` and vector `Polyline`s are drawn.
* **AC-3**: *Given* a summary with both `summary.polylines` and `summary.workoutIdToPolylineMap` populated, *When* `prepareCardMapPreviewSync` runs, *Then* `summary.polylines` is selected as the primary source.

### 1.4 System Invariants
* Zero schema migrations or queries against `PeriodSummaries.db`.
* Multi-region clustering, bounding, and dark-mode anti-flash overlays remain 100% preserved.
* 100% clean-room unit test pass rate under `./gradlew testDebugUnitTest`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Amends `REQ-PER-012` (*Period Map Scalability, Memory Budget & Adaptive Layering Invariant*) and `REQ-PER-019.4` (*Regional Route Network Heatmap Integration*). Targets `PeriodSummaryCard.kt`, `PeriodMapUtils.kt`, `PeriodCardMapStateTest.kt`.
2. **Historical Origin & Commit Trace**: Commit `1c89f50e` (ATT-1151 / `MAX_SUMMARY_THUMBNAIL_TRACKS = 8`), commit `7e2151ec` (ATT-1107, established `MAX_PERIOD_VECTOR_TRACKS = 30`), and commit `da70647f` (ATT-4477, added `PeriodCardMapState`).
3. **Root Reason for Existing Formulation**: ATT-4477 moved polyline decoding to background dispatchers, but card rendering retained the unconditional `allPaths.forEach { Polyline(...) }` loop underneath `TileOverlay`.
4. **Preservation of Core Invariants**: `DAY` cards and small periods retain crisp vector lines; memory safety in `LazyColumn` scrolling and visual heatmap gradient clarity are guaranteed.

---

## 3. Test Specification (TST-PER-025.7)

### Test Case 1: Adaptive Suppression for $> 30$ Workouts (`TST-PER-025.7a`)
* **Scope**: Unit Test | **File**: `PeriodCardMapStateTest.kt`
* **Precondition**: Create `PeriodSummary` for `PeriodType.MONTH` with 31 distinct polylines.
* **Action**: Invoke `prepareCardMapPreviewSync(summary)`.
* **Expected Result**: `state.isReady == true`, `state.visuals?.heatmapProvider != null`, and `state.renderVectorPolylines == false`.

### Test Case 2: Polyline Retention for $\le 30$ Workouts & Day Cards (`TST-PER-025.7b`)
* **Scope**: Unit Test | **File**: `PeriodCardMapStateTest.kt`
* **Precondition**: Summaries with 1, 20, 30 workouts on `MONTH`, and 35 workouts on `DAY`.
* **Action**: Invoke `prepareCardMapPreviewSync(summary)`.
* **Expected Result**: For Month $\le 30$, `state.renderVectorPolylines == true`. For Day, `state.renderVectorPolylines == true` and `heatmapProvider == null`.

### Test Case 3: Summary Polyline Source Prioritization (`TST-PER-025.7c`)
* **Scope**: Unit Test | **File**: `PeriodCardMapStateTest.kt`
* **Precondition**: `summary` has `polylines = listOf(polyA)` and `workoutIdToPolylineMap = mapOf(1L to polyB)`.
* **Action**: Invoke `prepareCardMapPreviewSync(summary)`.
* **Expected Result**: `state.primaryPaths` decodes `polyA` (not `polyB`).

### Test Case 4: Clean-Room Regression Suite (`TST-PER-025.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`
* **Expected Result**: 100% pass rate with zero regressions.

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-025.7a` | Unit | `prepareCardMapPreviewSync` | `REQ-PER-019.4` | Specified |
| `TST-PER-025.7b` | Unit | `prepareCardMapPreviewSync` | `REQ-PER-019.4`, `REQ-PER-012` | Specified |
| `TST-PER-025.7c` | Unit | `prepareCardMapPreviewSync` | `REQ-PER-019.4` | Specified |
| `TST-PER-025.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
