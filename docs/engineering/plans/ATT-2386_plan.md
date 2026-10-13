# Stage 3: Implementation Plan - ATT-2386: Anchor Elevation Profile to Bottom Navigation Bar and Dynamically Expand Upper Map in Route and Segment Details

**Ticket**: [ATT-2386](https://rainerblind.atlassian.net/browse/ATT-2386)  
**Sub-task**: [ATT-2491](https://rainerblind.atlassian.net/browse/ATT-2491) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-273` (*Routes & Segments: Dynamically Maximized Map with Bottom-Anchored Intrinsic Elevation Profile in MapDetailLayout*)  
**Test Mapping**: `TST-UI-233` (*Routes & Segments: Dynamically Maximized Map with Bottom-Anchored Intrinsic Elevation Profile Verification*)  
**Branch**: `feature/ATT-2386`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Problem Description & Background

In the route and segment detailed views (`RouteOnMapScreen` and `SegmentOnMapScreen`), the screen viewport is split into two panes using a proportional split (`splitFraction`, defaulting to 50/50).

Because route and segment details contain only a single elevation profile (and zoom toolbar) without any additional telemetry charts (HR, Speed/Pace, Power), lap splits, or workout analytics, the lower section only requires its intrinsic height (~100–228 dp depending on elevation range + 40 dp toolbar).

By enforcing a 50/50 split on a typical smartphone screen (~600–700 dp available viewport height below the header):
1. The lower viewport allocates ~300–350 dp, leaving 100–180 dp of blank, empty space between the bottom of the elevation profile and the system Navigation Bar.
2. The upper map viewport is needlessly constrained to 50% of the screen, even though it could dynamically expand to fill the entire remaining vertical space.

Expected behavior:
* The lower elevation profile (including zoom toolbar and x-axis labels) must size dynamically to its intrinsic content height (`wrapContentHeight()`), with its bottom edge sitting flush with the top of the Navigation Bar (`navigationBarsPadding()`).
* The upper map must dynamically expand to occupy all remaining vertical space (`weight(1f)`), maximizing map visibility for compact profiles while keeping taller profiles fully visible and unclipped.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-273` (*Routes & Segments: Dynamically Maximized Map with Bottom-Anchored Intrinsic Elevation Profile in MapDetailLayout*)
  * Refines `REQ-UI-267` and restores the original intent of Clause 4 in `REQ-UI-223`.
  * Differentiates `hasScrollableContent` within the `showMap && hasLowerSection` branch.
  * In non-scrollable viewports (`!hasScrollableContent`): assigns `Modifier.weight(1f)` to `mapBox`, wraps the lower elevation container to intrinsic height, removes `SplitPaneDivider`, and anchors flush to the Navigation Bar.
* **Test Mapping**: `TST-UI-233` (*Routes & Segments: Dynamically Maximized Map with Bottom-Anchored Intrinsic Elevation Profile Verification*)
  * `TST-UI-233.1`: Architectural contract tests in `MapDetailLayoutDynamicViewportContractTest.kt` verifying conditional split-pane differentiation, `mapBox` weight assignment, wrapped lower container height, and Navigation Bar flush padding.
  * `TST-UI-233.2`: Existing MapDetailLayout contract tests regression verification (`MapDetailLayoutCollapsingHeaderContractTest.kt`, `SplitPaneDividerVisualContractTest.kt`).
  * `TST-UI-233.3`: Full clean-room test suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Workout Aftermath Split-Pane Invariant**: `TrackOnMapScreen` (where `hasScrollableContent == true`) retains the interactive `SplitPaneDivider` with proportional resizing and vertical scrolling.
3. **Interactive Zoom & Pan Invariant**: `GlobalTelemetryZoomToolbar` (+, -, 1.0x, pan toggle) and 2-finger pinch zoom on `ElevationProfile` in routes and segments remain 100% active.
4. **Scrubbing Coordination Invariant**: Distance scrubbing on the elevation profile accurately highlights the trackpoint on the route/segment polyline on the map.
5. **Specialized Viewports Invariant**: `WorkoutClusterHeatmapScreen` retains full-screen map; `LiveSegmentSheet` (`showMap == false`) retains wrapped bottom sheet height.
6. **Subtask Self-Sufficiency**: Subtask [ATT-2491](https://rainerblind.atlassian.net/browse/ATT-2491) transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent ticket [ATT-2386](https://rainerblind.atlassian.net/browse/ATT-2386) remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `MapDetailLayout.kt`
* In `MapDetailLayout.kt` around line 590:
  Differentiate viewport construction based on `hasScrollableContent`:
  - When `showMap && hasLowerSection && hasScrollableContent`:
    Retain the existing `BoxWithConstraints` containing `SplitPaneDivider` and proportional `weight(splitFraction)` vs `weight(1f - splitFraction)` for multi-chart workout aftermath.
  - When `showMap && hasLowerSection && !hasScrollableContent`:
    Render a `Column(modifier = Modifier.fillMaxSize())` where:
    - `mapBox` receives `Modifier.weight(1f).heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT).fillMaxWidth()`.
    - Below `mapBox`, the lower container receives `Modifier.fillMaxWidth().wrapContentHeight()`.
    - Inside the lower container, `GlobalTelemetryZoomToolbar` (if `hasZoomToolbar`) and `lowerColumn(Modifier.fillMaxWidth().wrapContentHeight())` are rendered.
    - Inside `lowerColumn`, the `Surface` retains `navigationBarsPadding()` when `analyticsContent == null`, ensuring the bottom of the elevation profile sits flush with the Navigation Bar.
    - `scrubbingOverlay()` is positioned cleanly within the lower container.

### Component 2: Contract Test Verification
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutDynamicViewportContractTest.kt` verifying the architectural layout contracts.

### UI Consistency (Rule 23)
* **Reference screen / component**: [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt), [RouteOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt), and [SegmentOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentOnMapScreen.kt).
* **Reused components**: `mapBox`, `GlobalTelemetryZoomToolbar`, `lowerColumn`, `scrubbingOverlay`, `Surface`.
* **Theme tokens**: `MaterialTheme.colorScheme.surface`, `SplitPaneMath.MIN_MAP_HEIGHT` (120 dp), `Modifier.navigationBarsPadding()`.
* **New one-off styles & justification**: None. Reuses existing tokens and layout primitives.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Check
* Command: `python3 tools/jira_util.py check-gate ATT-2491`

### Step 2: Differentiate Viewport Layout in `MapDetailLayout.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* **Changes**:
  - In `showMap && hasLowerSection`:
    - Add `if (hasScrollableContent)` branch preserving existing `BoxWithConstraints` with `SplitPaneDivider`.
    - Add `else` branch for `!hasScrollableContent` with `mapBox(Modifier.weight(1f).heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT).fillMaxWidth())` and wrapped lower container.

### Step 3: Author Contract Tests
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutDynamicViewportContractTest.kt`
* **Changes**:
  - Implement contract assertions checking `hasScrollableContent` branching, `weight(1f)`, `wrapContentHeight()`, and Navigation Bar padding.

### Step 4: Targeted Unit & Contract Verification
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutDynamicViewportContractTest" --tests "com.atrainingtracker.trainingtracker.ui.map.SplitPaneDividerVisualContractTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutCollapsingHeaderContractTest"
  ```
* **Success Criteria**: 100% test pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted unit/contract tests passing with 0 failures.
  2. Full clean-room test suite (`./gradlew testDebugUnitTest`) verifying 100% pass rate.
  3. Gate 4 and Gate 5 automated audits before in-sprint merge.
* **Rollback**:
  - Branch isolation on `feature/ATT-2386` allows complete rollback via `git checkout sprint/2026-41.1` without affecting other tickets.
