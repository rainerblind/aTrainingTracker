# Stage 1 Analysis: ATT-1890 - [Aftermath/Map] Interactive Draggable Splitter to Resize Map and Telemetry Viewports in MapDetailLayout

**Ticket**: [ATT-1890](https://rainerblind.atlassian.net/browse/ATT-1890)  
**Sub-task**: [ATT-1907](https://rainerblind.atlassian.net/browse/ATT-1907) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Branch**: `feature/ATT-1890`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

On the detailed post-workout inspection screen (`TrackOnMapScreen` / `MapDetailLayout`), screen real estate is currently partitioned according to a static, immutable ratio: the Google Map at the top receives `weight(1f)` with a hardcoded minimum height of `240.dp`, while the lower scrollable container hosting the elevation profile, continuous telemetry graphs (Heart Rate, Speed/Pace, Power), and analytics cards receives `weight(1.2f)`.

Athletes have divergent analytical workflows:
1. **Route & Geography Inspection**: When zooming in on winding mountain trails, single-tracks, or complex intersections, athletes need an expansive map viewport occupying 70–80% of the screen height.
2. **Telemetry & Split Analysis**: When deeply inspecting heart rate zones, interval splits, elevation gradients, or power surges across multiple stacked graphs, athletes need maximum vertical height for the charts so multiple metrics can be viewed simultaneously without excessive vertical scrolling.

Because the current split ratio is statically hardcoded, athletes cannot customize viewport allocation. This ticket introduces a tactile, interactive draggable horizontal splitter bar between the map and the telemetry graphs, supporting real-time vertical drag resizing, double-tap snapping back to balanced 50/50 default, and robust boundary clamping to prevent viewport collapse.

---

## 2. Root Cause Analysis (Forensic Investigation)

Investigation of `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt` reveals the architectural structure:

```kotlin
// 2. MAP AREA with OVERLAYED SHARE BUTTON
if (showMap) {
    val mapModifier = if (hasScrollableContent) {
        Modifier
            .weight(1f)
            .heightIn(min = 240.dp)
            .fillMaxWidth()
    } else {
        Modifier
            .weight(1f)
            .fillMaxWidth()
    }
    Box(modifier = mapModifier) { ... }
}

// 3. ELEVATION PROFILE & CONTINUOUS METRIC GRAPHS AND 4. ANALYTICS
val lowerModifier = if (showMap && hasScrollableContent) {
    Modifier
        .weight(1.2f)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
} else {
    Modifier
        .fillMaxWidth()
        .wrapContentHeight()
}
Column(modifier = lowerModifier) { ... }
```

### Architectural Findings:
1. **Static Weights**: `weight(1f)` and `weight(1.2f)` are hardcoded constants. There is no state variable (`splitFraction` or `mapWeight`) allowing dynamic redistribution of vertical screen space.
2. **Static Minimum Floor**: `heightIn(min = 240.dp)` was introduced in Sprint 2026-40.7 (`REQ-UI-213` / `ATT-1812`) to prevent the map from collapsing when multiple telemetry graphs were appended to the lower container. However, with an interactive splitter, this 240 dp floor prevents athletes from shrinking the map down to 120 dp to prioritize telemetry analysis.
3. **Missing Splitter Affordance**: There is no divider composable between the Map Box and the lower scroll container, offering zero visual grip affordance or gesture handling.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **Dynamic Split State**: Introduce `rememberSaveable` state (`splitFraction` initialized to default `0.50f` or `0.455f`) that survives configuration changes and process recreation.
  * **Reusable SplitPaneDivider**: Create a standalone, modular composable (`SplitPaneDivider.kt`) in `ui.components.core` featuring:
    - 24 dp comfortable touch target height.
    - Centered visual pill grip (`32.dp x 4.dp`, rounded corners, subtle surface/outline styling).
    - Smooth vertical drag gesture handling via `draggable` / `pointerInput`.
    - Double-tap gesture detecting quick taps to reset the split back to default.
    - Accessibility content description with 100% 9-language localization parity.
  * **Pure Math Helper (`SplitPaneMath`)**: Encapsulate fraction calculations, pixel bounds clamping, and delta conversions into an isolated, 100% testable utility.
  * **Safe Boundary Clamping**: Enforce strict minimum boundaries:
    - `minMapHeight = 120.dp`: Map cannot shrink below 120 dp.
    - `minLowerHeight = 160.dp`: Graph container cannot shrink below 160 dp (guaranteeing at least one full elevation graph is visible).
  * **Synergy with Zoom Toolbar (`ATT-1876`)**: Ensure splitter placement cleanly accommodates the future persistent global zoom toolbar directly below or adjacent to the divider.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying Google Maps camera controllers, polylines, or tile renderers.
  * Modifying zoom math, distance decimation, or rendering curves in `ElevationProfile` or `TelemetryMetricGraph`.
  * Modifying snapshot sharing stitching logic (`combineWorkoutAndShare`).
  * Changing non-detailed screens (e.g. `RouteOnMapScreen` or `SegmentOnMapScreen` where `hasScrollableContent` is false and the map already fills the screen cleanly).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-UI-213` (*Aftermath: Resilient Map Preview Visibility & Scrollable Telemetry Layout Architecture*), refined and extended by net-new requirement `REQ-UI-223` (*Aftermath/Map: Interactive Draggable Splitter and Dynamic Viewport Resizing in MapDetailLayout*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
* **Historical Origin & Commit Trace**:
  - `REQ-UI-213`: Sprint 2026-40.7 (`ATT-1812`), commit `4e6224aa`.
* **Root Reason for Existing Formulation**:
  - When continuous telemetry graphs (Heart Rate, Pace/Speed, Power) were introduced (`ATT-1740`), the lower container expanded without a scroll container, squishing the map to 0dp. `REQ-UI-213` resolved this starvation defect by introducing a vertical scroll container with fixed weights (`1f` vs `1.2f`) and a static protective floor of `heightIn(min = 240.dp)`.
* **Preservation of Core Invariants**:
  - The protective invariant (neither the map nor the lower graphs may collapse to 0dp) is 100% preserved and strengthened. Instead of a rigid 240 dp barrier that blocks athlete customization, `ATT-1890` establishes an active dynamic range bounded between `120.dp` and `maxHeight - 160.dp`.
  - Routes and segments without scrollable graphs retain full-screen expansion (`hasScrollableContent == false`).
  - Gesture isolation between the map and lower container remains strictly intact.
  - Multi-chart synchronized scrubbing cursor parity is 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

### Component 1: `SplitPaneDivider.kt` & `SplitPaneMath` (`ui.components.core`)
* **Package**: `com.atrainingtracker.trainingtracker.ui.components.core`
* **Pure Math Object**:
  ```kotlin
  object SplitPaneMath {
      const val DEFAULT_SPLIT_FRACTION: Float = 0.50f
      val MIN_MAP_HEIGHT: Dp = 120.dp
      val MIN_LOWER_HEIGHT: Dp = 160.dp
      val DIVIDER_TOUCH_HEIGHT: Dp = 24.dp
      val GRIP_WIDTH: Dp = 32.dp
      val GRIP_HEIGHT: Dp = 4.dp

      fun calculateAvailableHeight(totalHeightPx: Float, dividerHeightPx: Float): Float =
          (totalHeightPx - dividerHeightPx).coerceAtLeast(1f)

      fun calculateMinFraction(minTopHeightPx: Float, availableHeightPx: Float): Float =
          (minTopHeightPx / availableHeightPx).coerceIn(0.05f, 0.95f)

      fun calculateMaxFraction(minBottomHeightPx: Float, availableHeightPx: Float, minFraction: Float): Float =
          (1f - (minBottomHeightPx / availableHeightPx)).coerceIn(minFraction, 0.95f)

      fun updateFraction(currentFraction: Float, deltaPx: Float, availableHeightPx: Float, minFraction: Float, maxFraction: Float): Float {
          val deltaFraction = deltaPx / availableHeightPx
          return (currentFraction + deltaFraction).coerceIn(minFraction, maxFraction)
      }
  }
  ```
* **Composable**:
  - Renders a horizontal divider bar with a centered tactile pill grip (`32.dp x 4.dp`).
  - Supports dragging via `draggable(orientation = Orientation.Vertical)` reporting $\Delta y$.
  - Supports double-tap via `pointerInput { detectTapGestures(onDoubleTap = { onReset() }) }`.
  - Accessible semantics via `contentDescription`.

### Component 2: `MapDetailLayout.kt` Integration
* In `MapDetailLayout`, when `showMap && hasScrollableContent`:
  - Manage `var splitFraction by rememberSaveable { mutableFloatStateOf(SplitPaneMath.DEFAULT_SPLIT_FRACTION) }`.
  - Use `BoxWithConstraints` around the resizable area.
  - Map Box receives `Modifier.weight(splitFraction).heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT)`.
  - `SplitPaneDivider` is positioned between Map Box and lower scroll container.
  - Lower container receives `Modifier.weight(1f - splitFraction).verticalScroll(rememberScrollState())`.
  - When `hasScrollableContent` is false (Routes & Segments), Map Box retains full `Modifier.weight(1f)` and lower container uses `wrapContentHeight()`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Layout Regressions**: Route and segment maps without graphs retain full-viewport display.
  2. **Synchronized Scrubbing Parity**: Scrubbing on graphs continues to update the map pin synchronously.
  3. **Snapshot Sharing Integrity**: Full composite workout summary snapshot generation remains unimpacted.
  4. **9-Language Localization Parity**: Accessibility strings provided across all 9 supported locales.
  5. **Human Decision Gate**: AI agents must never transition parent ticket to `Erledigt`.

* **Risk Rating**: **LOW**
  - All changes are localized strictly to the presentation layout layer in `MapDetailLayout.kt` and a new isolated core component `SplitPaneDivider.kt`.
  - Mathematical clamping strictly eliminates negative heights or layout overflows.
