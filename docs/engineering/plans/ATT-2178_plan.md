# Stage 3: Implementation Plan - ATT-2178: Ensure Smooth Scrolling in Workout Details When Displaying Many Strava Segments or Laps

**Ticket**: [ATT-2178](https://rainerblind.atlassian.net/browse/ATT-2178)  
**Sub-task**: [ATT-2197](https://rainerblind.atlassian.net/browse/ATT-2197) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Requirement Mapping**: `REQ-UI-254` (*Aftermath/Details: Non-Blocking Nested Scroll Dispatch, Sub-Pixel Precision & Optimized High-Density Metadata Rendering in MapDetailLayout*)  
**Test Mapping**: `TST-UI-213` (*Aftermath/Details: Non-Blocking Nested Scroll Dispatch, Sub-Pixel Precision & Optimized High-Density Metadata Verification*)  
**Branch**: `feature/ATT-2178`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Description & Background

In the Detailed Workout view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`), viewing activities with extensive Strava segment efforts and/or many recorded laps (such as the *Einstein Halbmarathon 28.09.2014* with 40+ segment efforts and 21 lap splits) triggers severe scrolling degradation, gesture trapping, and layout lockup.

### Forensic Root Cause Analysis
1. **Eager Pre-Scroll Interception in Both Directions**:
   `CollapsingAppBarNestedScrollConnection.onPreScroll` eagerly consumes scroll deltas before children can process them.
   - On downward gestures (`available.y > 0`), if `appBarOffset < 0`, the connection immediately expands the app bar. When an athlete has scrolled down into lower telemetry graphs and attempts to scroll back up within those graphs, the app bar drops down prematurely and squashes the view, trapping lower column scrolling.
   - On upward gestures (`available.y < 0`) within the upper metadata container, the connection immediately translates `appBarOffset` towards `-appBarMaxHeight`. The entire upper section is translated off-screen before the internal `metadataContent` scroll state can scroll, making segment rows 4 through 40 completely unreachable.
2. **Integer Truncation & Recomposition Thrashing**:
   `CollapsingAppBarNestedScrollConnection` rounds deltas to integer pixels via `available.y.toInt()`. This discards fractional sub-pixel scroll movements, producing noticeable frame jitter and micro-stutter. Concurrently, reading `connection.appBarOffset` inside `BoxWithConstraints` to compute `currentTopPaddingDp` triggers continuous recomposition and layout re-measurement across the entire map and graph tree during scrolling.
3. **High-Density Allocation Inefficiencies**:
   Rendering 40+ un-virtualized Strava segment rows without memoized formatters creates avoidable garbage collection pressure during animated transitions.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-254` (*Aftermath/Details: Non-Blocking Nested Scroll Dispatch, Sub-Pixel Precision & Optimized High-Density Metadata Rendering in MapDetailLayout*)
* **Test Mapping**: `TST-UI-213` (*Aftermath/Details: Non-Blocking Nested Scroll Dispatch, Sub-Pixel Precision & Optimized High-Density Metadata Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Guaranteed Minimum Map Height in Base State (`REQ-UI-250`)**:
   In the uncollapsed base state (`appBarOffset == 0`), the maximum initial height of the upper metadata container remains bounded by `maxMetadataHeightDp` to ensure the map viewport is never squashed below `SplitPaneMath.MIN_MAP_HEIGHT` (120dp).
2. **Full-Screen Map Expansion upon Complete Collapse (`REQ-UI-250`)**:
   When the app bar is fully collapsed (`appBarOffset == -appBarMaxHeight`), the map and lower analytics viewports expand to consume the full screen height.
3. **Interactive Draggable Splitter Parity (`REQ-UI-223`)**:
   `SplitPaneDivider`, `SplitPaneMath.MIN_MAP_HEIGHT` (120dp), and double-tap reset remain 100% functional.
4. **Persistent Sticky Global Zoom Toolbar (`REQ-UI-225`)**:
   Toolbar remains stationary directly below the map and divider.
5. **Directional Chart Gesture Disambiguation (`REQ-UI-226`)**:
   Horizontal scrubbing and pinch-to-zoom gestures on elevation and telemetry graphs remain completely isolated from vertical viewport scrolling.
6. **Canonical Section Order Parity (`REQ-UI-252`)**:
   Section sequence (Header -> Description -> Extrema -> Laps -> Strava -> Map -> Graphs -> Zones) remains strictly preserved.
7. **9-Language Localization Parity (`REQ-LOC-001`)**:
   Zero missing string resources and 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
8. **Subtask Direct Completion & Parent Human Gate**:
   Subtasks transition directly to `Erledigt` via transition `freigabe` upon passing Gate audit. Parent ticket `ATT-2178` transitions to `Final Review (Human)` after Stage 5.

---

## 4. Proposed Architectural Changes

### Component 1: Non-Blocking Nested Scroll Connection (`CollapsingAppBarNestedScrollConnection.kt`)
* Refactor `appBarOffset` to `Float` (or maintain precise float accumulation) so sub-pixel scroll movements do not truncate and jitter.
* Implement non-blocking scroll dispatch:
  - In `onPreScroll(available: Offset, source: NestedScrollSource)`:
    - For upward gestures (`available.y < 0f`): Consume deltas to collapse `appBarOffset` from `0f` to `-appBarMaxHeight.toFloat()`.
    - For downward gestures (`available.y > 0f`): Return `Offset.Zero` without consuming in `onPreScroll`.
  - In `onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource)`:
    - For downward gestures (`available.y > 0f`): When the child scroll container has reached its top boundary and emits unconsumed downward delta, consume `available.y` to smoothly expand `appBarOffset` back towards `0f`.
* Coordinated Metadata Scroll:
  - Enable `metadataContent` to consume internal upward scroll before collapsing, or decouple `metadataContent` scroll dispatch so athletes can fluidly scroll through all 40+ segment efforts and laps without premature collapse.

### Component 2: Layout & Recomposition Decoupling (`MapDetailLayout.kt`)
* Optimize viewport positioning by decoupling scroll offset from full-tree layout remeasurement loops.
* Ensure `onGloballyPositioned` on the upper metadata column does not trigger feedback recomposition cycles during active scroll gestures.
* Retain `maxMetadataHeightDp` calculation in base state to guarantee `SplitPaneMath.MIN_MAP_HEIGHT` (120dp).

### Component 3: High-Density Allocation & Rendering Optimization (`StravaActivitySection.kt`, `LapSplitVisualizer.kt`)
* In `StravaActivitySection.kt`, memoize `TimeFormatter` across segment effort rows and best effort rows rather than instantiating a new formatter instance per row.
* Structure segment rows with lightweight composables to minimize layout and composition overhead during collapse/expansion animations.
* In `LapSplitVisualizer.kt`, ensure split interval rows format cleanly with zero redundant allocations.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Unit & Contract Test Suite
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/utils/CollapsingAppBarNestedScrollConnectionTest.kt`.
* Verify sub-pixel float precision without integer truncation jitter.
* Verify non-blocking downward scroll (deferred to `onPostScroll`).
* Verify upward scroll collapse bounds `[-appBarMaxHeight, 0]`.

### Step 2: Implement Non-Blocking `CollapsingAppBarNestedScrollConnection.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/utils/CollapsingAppBarNestedScrollConnection.kt`.
* Refactor offset accumulation to `Float`.
* Implement `onPreScroll` (upward collapse) and `onPostScroll` (downward expansion).

### Step 3: Viewport Optimization & Metadata Scroll Coordination in `MapDetailLayout.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`.
* Decouple viewport translation from recomposition thrashing.
* Ensure smooth coordination between internal `metadataContent` scroll and outer app bar collapse.

### Step 4: High-Density Allocation Optimization in `StravaActivitySection.kt` and `LapSplitVisualizer.kt`
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySection.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt`
* Share `TimeFormatter` instance and streamline row composables to eliminate allocation churn.

### Step 5: Contract & Performance Testing
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySectionPerformanceTest.kt`.
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutCollapsingHeaderContractTest.kt`.
* Verify 40+ segment efforts and 20+ laps execute with zero exceptions and fluid rendering.

### Step 6: Full Clean-Room Test Suite Verification
* Command: `./gradlew testDebugUnitTest`
* Ensure 100% pass rate across all project modules.

---

## 6. Verification & Rollback Plan

* **Verification**: Execute `./gradlew testDebugUnitTest` verifying 0 errors and zero regressions.
* **Rollback**: Work is contained on `feature/ATT-2178` branched from `sprint/2026-40.14`. Rollback is accomplished via `git checkout sprint/2026-40.14`.
