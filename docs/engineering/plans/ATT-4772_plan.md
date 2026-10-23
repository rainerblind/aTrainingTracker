# Stage 3: Implementation Plan - ATT-4772: Suppress Redundant Vector Polylines in PeriodSummaryCard Mini-Map

**Ticket**: [ATT-4772](https://rainerblind.atlassian.net/browse/ATT-4772)  
**Sub-task**: [ATT-4806](https://rainerblind.atlassian.net/browse/ATT-4806) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.10 | **Active Sprint**: `sprint/2026-43.10`  
**Requirement Mapping**: `REQ-PER-019.4` (amending `REQ-PER-012`) | **Test Mapping**: `TST-PER-025.7`  
**Branch**: `feature/ATT-4772`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Description & Background

In `PeriodSummaryCard.kt` (`PeriodMultiWorkoutMap:551-565`), every route in `allPaths` is unconditionally rendered as a vector `Polyline` composable. For Month and Year periods with $> 30$ workouts, this allocates hundreds of native Google Maps `Polyline` JNI objects underneath the `TileOverlay` heatmap layer, causing UI thread/GPU overhead in `LazyColumn` and washing out the translucent heatmap gradient.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PER-019.4` (*Adaptive Vector Polyline Suppression & Source Priority*) & `REQ-PER-012` (*Period Map Scalability Invariant*)
  * `TST-PER-025.7a`: Adaptive polyline suppression for periods with $> 30$ workouts and active heatmap.
  * `TST-PER-025.7b`: Vector polyline retention for periods with $\le 30$ workouts and `DAY` cards.
  * `TST-PER-025.7c`: Prioritization of `summary.polylines` over `summary.workoutIdToPolylineMap`.
  * `TST-PER-025.6`: Clean-room regression verification for `ui.aftermath.periodlist.*`.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Database Migrations**: Zero changes to `PeriodSummaries.db` schema or queries.
2. **Day Card Polyline Crispness**: Day periods (`PeriodType.DAY`) continue to render vector polylines exclusively (`heatmapProvider == null`).
3. **Single Source of Truth (DRY)**: Reuses canonical `MAX_PERIOD_VECTOR_TRACKS = 30` from `PeriodData.kt` across card previews and full-screen map views.
4. **Visual & Anti-Flash Parity**: Map height (250 dp), camera bounds framing, and `DarkMapAntiFlashOverlay` remain intact.

---

## 4. Proposed Architectural Changes

### Component 1: `PeriodMapUtils.kt` (`ui.aftermath.periodlist`)
* Add `val renderVectorPolylines: Boolean = true` to `PeriodCardMapState`.
* In `prepareCardMapPreviewSync(summary)`: Compute `renderVectorPolylines = visuals?.heatmapProvider == null || activePaths.size <= MAX_PERIOD_VECTOR_TRACKS`.

### Component 2: `PeriodSummaryCard.kt` (`ui.aftermath.periodlist`) & UI Consistency (Rule 23)
* Pass `renderVectorPolylines = mapState.renderVectorPolylines` to `PeriodMultiWorkoutMap`.
* Guard `allPaths.forEach { Polyline(...) }` with `if (renderVectorPolylines)`.
* **UI Consistency**:
  * *Reference Screen*: `PeriodSummaryCard` & `PeriodMapScreen` (`ui/aftermath/periodlist/`).
  * *Reused Components*: `GoogleMap`, `TileOverlay`, `Polyline`, `DarkMapAntiFlashOverlay`.
  * *Theme Tokens*: Preserves existing `TTColor.DarkMapCanvas`, `TrackType.BEST.color`, `visuals.polylineWidth`, and 250 dp card height.
  * *New One-Off Styles*: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `PeriodCardMapState` and Builder in `PeriodMapUtils.kt`
* Add `renderVectorPolylines: Boolean = true` to `PeriodCardMapState`.
* Update `prepareCardMapPreviewSync` to evaluate `renderVectorPolylines` against `MAX_PERIOD_VECTOR_TRACKS`.

### Step 2: Guard Polyline Composition in `PeriodSummaryCard.kt`
* Add `renderVectorPolylines: Boolean = true` parameter to `PeriodMultiWorkoutMap`.
* Wrap the `Polyline` drawing loop in `if (renderVectorPolylines)`.
* Pass `mapState.renderVectorPolylines` from `PeriodSummaryCard`.

### Step 3: Implement Unit Tests in `PeriodCardMapStateTest.kt`
* Add test methods for `TST-PER-025.7a`, `TST-PER-025.7b`, and `TST-PER-025.7c`.
* Verify targeted test execution:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted package unit tests, UI theme audit test, requirement governance audit (`verify_requirement_governance.py`), and full test suite in Stage 5.
* **Rollback**: Clean git checkout / branch revert of `feature/ATT-4772` with zero database or shared preference side effects.
