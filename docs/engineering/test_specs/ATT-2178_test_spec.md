# Stage 2: Requirement & Test Specification - ATT-2178: Ensure Smooth Scrolling in Workout Details When Displaying Many Strava Segments or Laps

**Ticket**: [ATT-2178](https://rainerblind.atlassian.net/browse/ATT-2178)  
**Sub-task**: [ATT-2196](https://rainerblind.atlassian.net/browse/ATT-2196) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Requirement Mapping**: `REQ-UI-254` (*Aftermath/Details: Non-Blocking Nested Scroll Dispatch, Sub-Pixel Precision & Optimized High-Density Metadata Rendering in MapDetailLayout*)  
**Test Spec ID**: `TST-UI-213`  
**Branch**: `feature/ATT-2178`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (REQ-UI-254)

### 1.1 Problem Statement & Rationale
When viewing workouts containing a high density of Strava segments and/or recorded laps (e.g. the *Einstein Halbmarathon from 28.09.2014*, featuring 40+ segment efforts and extensive split intervals), scrolling within the Detailed Workout view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`) suffers from gesture trapping, stutter, and layout lockup.
This breakdown stems from three root causes:
1. **Blanket Pre-Scroll Trapping**: `CollapsingAppBarNestedScrollConnection.onPreScroll` unconditionally consumes both upward and downward scroll deltas before any child scroll container can act. On downward gestures, it immediately expands the app bar even when the user is scrolled deep into lower telemetry graphs and merely attempting to scroll back up within the graphs. On upward gestures within upper metadata, it immediately collapses the outer app bar offscreen before internal content can scroll, preventing athletes from reaching lower segment rows.
2. **Integer Truncation & Recomposition Thrashing**: `onPreScroll` casts floating-point delta offsets to integers (`available.y.toInt()`), losing fractional sub-pixel scroll information and inducing micro-stutters. Concurrently, reading `connection.appBarOffset` inside `BoxWithConstraints` to compute `currentTopPaddingDp` triggers continuous recomposition and layout re-measurement cycles on every scroll frame.
3. **High-Density Allocation Inefficiencies**: Rendering 40+ un-virtualized segment effort rows and extensive lap cards without memoized formatters creates avoidable garbage collection pressure during animated transitions.

### 1.2 Functional & Architectural Requirements
The system SHALL fulfill the following architectural and behavioral criteria:

1. **Non-Blocking Nested Scroll Dispatch (`CollapsingAppBarNestedScrollConnection.kt`)**:
   - The connection SHALL NOT eagerly consume downward scroll deltas (`available.y > 0`) in `onPreScroll` when child containers are scrolled away from their top boundary. Downward expansion of the app bar SHALL be deferred to `onPostScroll` (or gated by the child reaching its top boundary), allowing lower telemetry graphs and internal metadata lists to scroll up/down naturally.
   - For upward scroll gestures (`available.y < 0`), the connection SHALL consume deltas in `onPreScroll` to smoothly collapse `appBarOffset` from `0` to `-appBarMaxHeight`.
   - The connection SHALL maintain high-precision floating-point delta accumulation without integer truncation (`available.y.toInt()`), ensuring 60/120 fps sub-pixel fluid motion.

2. **Decoupled Viewport Offset & Composition Optimization (`MapDetailLayout.kt`)**:
   - The viewport container's vertical offset and padding SHALL be decoupled from layout thrashing and recomposition loops, eliminating redundant layout passes and `onGloballyPositioned` state writes during scroll gestures.
   - In the uncollapsed base state (`appBarOffset == 0`), upper metadata maximum height SHALL remain bounded to ensure the map viewport is never squashed below `SplitPaneMath.MIN_MAP_HEIGHT` (120dp) (`REQ-UI-250`), while allowing smooth, uninhibited vertical scrolling of all enclosed content.

3. **High-Density Performance Optimization (`StravaActivitySection.kt`, `LapSplitVisualizer.kt`)**:
   - When rendering a high volume of Strava segment efforts (e.g. 30+ items) or many lap splits, rows SHALL avoid expensive redundant object allocations (such as instantiating new `TimeFormatter` instances or unneeded tooltip state on every item).
   - Rows SHALL be cleanly structured to ensure high frame rates during collapse and expansion animations.

4. **Preserved Invariants**:
   - `REQ-UI-223`: Interactive draggable splitter (`SplitPaneDivider.kt`, `SplitPaneMath.MIN_MAP_HEIGHT` = 120dp, double-tap reset).
   - `REQ-UI-225`: Persistent sticky global zoom toolbar.
   - `REQ-UI-226`: Directional chart gesture disambiguation on stacked charts.
   - `REQ-UI-250`: Collapsible upper metadata architecture.
   - `REQ-UI-252`: Canonical section order parity (Header -> Description -> Extrema -> Laps -> Strava -> Map -> Graphs -> Zones).
   - `REQ-LOC-001`: 100% 9-language localization parity across all supported application locales.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1: Fluid Non-Blocking Scrolling Across Viewports**:
  * *Given* a workout with many Strava segments and/or many laps (such as Einstein HM 28.09.2014),
  * *When* the athlete scrolls within the detailed workout screen (both upper metadata and lower analytics),
  * *Then* scrolling SHALL be fluid, responsive, and reach all content without gesture trapping, stutter, or layout lockup.
* **AC-2: Smooth Upward Collapse from Any Point**:
  * *Given* the collapsible upper metadata architecture (`REQ-UI-250`),
  * *When* scrolling upward from any point in the view,
  * *Then* the upper metadata SHALL collapse cleanly out of view and the map/graphs expand smoothly without stutter.
* **AC-3: Natural Lower Column Downward Scrolling**:
  * *Given* lower analytics content scrolled down to telemetry charts or zone cards,
  * *When* the athlete scrolls downward to inspect preceding charts,
  * *Then* the lower analytics column SHALL scroll smoothly without prematurely expanding the upper app bar until the child reaches the top.
* **AC-4: Sub-Pixel Float Precision**:
  * *Given* continuous touch drag gestures dispatched to `CollapsingAppBarNestedScrollConnection`,
  * *When* `onPreScroll` and `onPostScroll` process delta offsets,
  * *Then* offsets SHALL accumulate using floating-point precision, preventing truncation jitter.

### 1.4 System Invariants
* **Invariant 1**: In the uncollapsed base state, the map viewport SHALL NEVER be squashed below `SplitPaneMath.MIN_MAP_HEIGHT` (120dp).
* **Invariant 2**: Google Maps gesture handling (pan, pinch, tilt) within the map viewport remains isolated and uninterrupted.
* **Invariant 3**: Multi-chart synchronized scrubbing cursor parity across elevation and telemetry graphs remains strictly functional.
* **Invariant 4**: 100% clean-room test suite pass rate across all project modules.

---

## 2. Test Specification (TST-UI-213)

### Test Case 1: `testNestedScrollConnection_preservesSubPixelFloatPrecision` (`TST-UI-213.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/utils/CollapsingAppBarNestedScrollConnectionTest.kt`
* **Preconditions**: `CollapsingAppBarNestedScrollConnection` initialized with `initialAppBarMaxHeight = 300`.
* **Action**: Dispatch sub-pixel fractional scroll deltas (`Offset(0f, -0.4f)`).
* **Expected Result**: Delta is accumulated smoothly without loss of fractional values; `appBarOffset` reflects accumulated displacement without integer rounding jitter.

### Test Case 2: `testNestedScrollConnection_defersDownwardExpansionWhenChildScrolled` (`TST-UI-213.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/utils/CollapsingAppBarNestedScrollConnectionTest.kt`
* **Preconditions**: Connection is collapsed (`appBarOffset = -300`).
* **Action**: Dispatch downward scroll delta (`available.y > 0`) during `onPreScroll`.
* **Expected Result**: Downward delta is NOT eagerly consumed in `onPreScroll` when the child container has scroll headroom, allowing child scroll container to handle upward scroll first.

### Test Case 3: `testMapDetailLayout_maintainsMinMapHeightInBaseState` (`TST-UI-213.3`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutCollapsingHeaderContractTest.kt`
* **Preconditions**: MapDetailLayout rendered with large metadata container.
* **Action**: Evaluate maximum height constraints for upper metadata.
* **Expected Result**: Maximum metadata height calculation guarantees at least `SplitPaneMath.MIN_MAP_HEIGHT` (120dp) for the map viewport.

### Test Case 4: `testStravaActivitySection_handlesHighDensityEffortsWithoutLockup` (`TST-UI-213.4`)
* **Scope**: Unit / Performance Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySectionPerformanceTest.kt`
* **Preconditions**: Mock Strava activity JSON containing 40+ segment efforts and 15 best efforts (Einstein HM volume).
* **Action**: Parse and compose/measure `StravaActivitySection`.
* **Expected Result**: Section parses and formats all 40+ segment efforts with zero exceptions, stable row counts, and no memory leaks.

### Test Case 5: 9-Language Localization & Specifier Audit (`TST-UI-213.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Goal**: Verify 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT string resources.
* **Expected Result**: Zero missing string resources, zero format specifier mismatches.

### Test Case 6: Clean-Room Regression Suite (`TST-UI-213.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.
* **Expected Result**: All tests pass cleanly with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-213.1]` | Unit | `CollapsingAppBarNestedScrollConnection.onPreScroll` | `REQ-UI-254` (Clause 1) | Specified |
| `[TST-UI-213.2]` | Unit | `CollapsingAppBarNestedScrollConnection.onPostScroll` | `REQ-UI-254` (Clause 1) | Specified |
| `[TST-UI-213.3]` | Contract | `MapDetailLayout` viewport bounding | `REQ-UI-254` (Clause 2), `REQ-UI-250` | Specified |
| `[TST-UI-213.4]` | Performance | `StravaActivitySection` high-density parsing/rendering | `REQ-UI-254` (Clause 3) | Specified |
| `[TST-UI-213.5]` | Localization | `TranslationParityTest` | `REQ-UI-254`, `REQ-LOC-001` | Specified |
| `[TST-UI-213.6]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
