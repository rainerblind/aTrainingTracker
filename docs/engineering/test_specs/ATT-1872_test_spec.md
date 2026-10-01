# Stage 2: Requirement & Test Specification - ATT-1872: Resolve Gesture Conflict to Enable Smooth Vertical Scrolling of Graphs in MapDetailLayout

**Ticket**: [ATT-1872](https://rainerblind.atlassian.net/browse/ATT-1872)  
**Sub-task**: [ATT-1923](https://rainerblind.atlassian.net/browse/ATT-1923) (`[Specification]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-226` (*Aftermath/Map: Directional Gesture Disambiguation and Smooth Vertical Scrolling in MapDetailLayout*)  
**Test Mapping**: `TST-UI-180`  
**Author**: AI Agent 1 (Specification Engineer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (`REQ-UI-226`)

The system SHALL eliminate gesture trapping across all stacked charts in `MapDetailLayout.kt` (`ElevationProfile.kt` and `TelemetryMetricGraph.kt`), allowing effortless vertical scrolling through analytics while preserving precision horizontal scrubbing and pinch-to-zoom (ATT-1872):

1. **Directional Drag Disambiguation Formulation (`ChartGestureDisambiguator` / `pointerInput`)**:
   - In `ElevationProfile.kt` and `TelemetryMetricGraph.kt`, single-pointer drag gestures SHALL be classified dynamically against the system touch slop (`viewConfiguration.touchSlop`):
     - **Dominant Vertical Gesture** ($|\Delta y| > |\Delta x|$ and $|\Delta y| > \text{touchSlop}$): The gesture SHALL be recognized as a viewport scroll intent. The chart pointer input handler SHALL NOT consume pointer changes (`change.consume()`), SHALL NOT initiate scrubbing or pan, and SHALL yield the event to the parent `Modifier.verticalScroll` container.
     - **Dominant Horizontal Gesture** ($|\Delta x| \ge |\Delta y|$ and $|\Delta x| > \text{touchSlop}$): The gesture SHALL be recognized as a chart inspection/scrubbing intent. The chart pointer input handler SHALL consume the pointer change (`change.consume()`) and update scrubbing distance or horizontal pan offset.
     - **Intentional Tap Gesture**: If a single pointer is pressed and released without exceeding `touchSlop` in either axis, it SHALL trigger tap inspection (point selection in `TelemetryMetricGraph` or point selection / double-tap reset in `ElevationProfile`). If a vertical scroll was detected, tap inspection MUST be suppressed upon release.
2. **ElevationProfile Multi-Touch & Pan Invariant Preservation**:
   - 2-finger pinch-to-zoom transforms (`pressed.size >= 2`) in `ElevationProfile.kt` SHALL continue to consume pointer events and scale/pan the viewport per `REQ-UI-192` and `REQ-UI-225`.
   - When `isPanMode == true`, dominant horizontal dragging SHALL pan the visible chart window without scrubbing cursor updates.
   - When `isPanMode == false`, dominant horizontal dragging SHALL scrub the cursor and map marker, clearing on release (`onDistanceSelected(null)`).
3. **TelemetryMetricGraph Directional Scrubbing Architecture**:
   - `TelemetryMetricGraph.kt` SHALL replace direction-agnostic `detectDragGestures` with directional horizontal drag detection, ensuring vertical swipe deltas propagate unconsumed to the parent container.
   - `detectTapGestures(onPress = ...)` SHALL NOT eagerly lock out subsequent vertical swipe gestures.
4. **Preservation of Core Invariants**:
   - Synchronized multi-metric scrubbing cursor parity across all stacked charts (Elevation, Pace/Speed, HR, Power) remains strictly preserved.
   - Google Maps gestures (pan, pinch, rotate) remain isolated within the upper viewport per `REQ-UI-223`.
   - SplitPaneDivider draggable splitter resizing and double-tap reset remain intact.
   - List previews (`RouteItem`, `SegmentItem`, `WorkoutSummary`) remain smooth without touch interference.

---

## 2. Test Cases (`TST-UI-180`)

### TST-UI-180.1: Pure Unit Tests (`ChartGestureDisambiguatorTest.kt`)
- **TST-UI-180.1.1 (Dominant Vertical Detection)**:
  - Input: $\Delta x = 5\text{ px}, \Delta y = 25\text{ px}, \text{touchSlop} = 16\text{ px}$.
  - Expected: `isDominantVertical == true`, `isDominantHorizontal == false`.
- **TST-UI-180.1.2 (Dominant Horizontal Detection)**:
  - Input: $\Delta x = 30\text{ px}, \Delta y = 4\text{ px}, \text{touchSlop} = 16\text{ px}$.
  - Expected: `isDominantHorizontal == true`, `isDominantVertical == false`.
- **TST-UI-180.1.3 (Sub-Slop Threshold Protection)**:
  - Input: $\Delta x = 8\text{ px}, \Delta y = 6\text{ px}, \text{touchSlop} = 16\text{ px}$.
  - Expected: `isDominantHorizontal == false`, `isDominantVertical == false` (pending gesture / tap).
- **TST-UI-180.1.4 (Equal Delta Boundary)**:
  - Input: $\Delta x = 20\text{ px}, \Delta y = 20\text{ px}, \text{touchSlop} = 16\text{ px}$.
  - Expected: Evaluated deterministically (`isDominantHorizontal == true` or classified consistently).
- **TST-UI-180.1.5 (Negative Delta / Cardinal Directions)**:
  - Verify all 4 quadrants (top-left, top-right, bottom-left, bottom-right) handle absolute values correctly.

### TST-UI-180.2: Contract & Gesture Tests (`ElevationProfileGestureTest.kt`)
- **TST-UI-180.2.1**: Verify `ElevationProfile.kt` uses directional slope comparison and does not consume vertical drag events.
- **TST-UI-180.2.2**: Verify `ElevationProfile.kt` does not trigger tap inspection when `isVerticalScrolling == true`.
- **TST-UI-180.2.3**: Verify multi-touch (`pressed.size >= 2`) consumes both pointers for zoom/pan transforms.
- **TST-UI-180.2.4**: Verify `isPanMode` routing on horizontal drag.

### TST-UI-180.3: Contract & Gesture Tests (`TelemetryMetricGraphGestureTest.kt`)
- **TST-UI-180.3.1**: Verify `TelemetryMetricGraph.kt` does NOT call unconditional `detectDragGestures`.
- **TST-UI-180.3.2**: Verify directional gesture detection allows vertical drag events to pass unconsumed.
- **TST-UI-180.3.3**: Verify horizontal drag invokes `onDistanceSelected`.

### TST-UI-180.4: Clean-Room Full Suite Regression
- Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with 0 regressions.

---

## 3. Traceability Matrix

| Requirement | Test Spec | Target Class / File | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-226` (item 1) | `TST-UI-180.1` | `ChartGestureDisambiguator.kt` | Pure Unit Tests (`ChartGestureDisambiguatorTest.kt`) |
| `REQ-UI-226` (item 1, 2) | `TST-UI-180.2` | `ElevationProfile.kt` | Unit & Contract Tests (`ElevationProfileGestureTest.kt`) |
| `REQ-UI-226` (item 1, 3) | `TST-UI-180.3` | `TelemetryMetricGraph.kt` | Unit & Contract Tests (`TelemetryMetricGraphGestureTest.kt`) |
| `REQ-PRO-001` | `TST-UI-180.4` | Full Project Test Suite | `./gradlew testDebugUnitTest` |
