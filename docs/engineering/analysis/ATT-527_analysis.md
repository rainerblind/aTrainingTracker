# Stage 1 Analysis: Elevation Graph Support Zooming (ATT-527)

* **Parent Ticket**: [ATT-527](https://rainerblind.atlassian.net/browse/ATT-527) (*[Feature] Elevation Graph: Support zooming*)
* **Sub-Task**: [ATT-1636](https://rainerblind.atlassian.net/browse/ATT-1636) (*[Analysis] Problem Domain & Root Cause Analysis*)
* **Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)
* **Target Version**: `V4.9.38`
* **Target Sprint**: `2026-40.4`
* **Status**: `Analysis`

---

## 1. Executive Summary & Problem Statement

[`ElevationProfile`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt) provides athletes with an elevation curve plotted against workout distance ($0 .. \text{totalDist}$). Currently, it renders a strictly static horizontal view of the entire route distance. While athletes can scrub to inspect coordinates along the route, the profile lacks zooming and panning capabilities. On long workouts (e.g. 50km–150km rides or mountain trail runs), critical climbing sections, steep gradient pitches, and interval segments compress into narrow horizontal spans, preventing detailed inspection.

`ATT-527` introduces horizontal zooming and panning along the distance axis:
1. **Horizontal Pinch-to-Zoom**: Pinch gestures scale the visible distance window ($Z \in [1.0x .. 10.0x]$), expanding local sections while preserving vertical grade proportions.
2. **Pan Navigation**: When zoomed in ($Z > 1.0x$), dragging horizontally pans across the distance axis, strictly bounded by $[0 .. \text{totalDist}]$.
3. **Reset Affordance**: Double-tapping the graph surface or tapping an overlay reset control immediately restores the unzoomed $1.0x$ view ($0 .. \text{totalDist}$).
4. **Synchronized Scrubbing in Zoomed State**: Distance selection, coordinate highlighting on the map, and marker rendering dynamically respect the zoomed distance window.

---

## 2. Forensic Investigation of `ElevationProfile.kt`

### 2.1 Existing Coordinate Mapping
Currently, `ElevationProfile.kt` calculates horizontal coordinates linearly using the global `totalDist`:
```kotlin
val x = (currentD / cachedData.totalDist) * width
val x1 = seg.p1.x * width // seg.p1.x = p1.distance / totalDist
val markerX = (clampedDist / cachedData.totalDist) * width
```
Drag gestures in `detectDragGestures` assume a 1:1 correspondence between chart pixel width and `totalDist`:
```kotlin
val dist = (adjustedX / chartWidthPx) * cachedData.totalDist
onDistanceSelected(dist)
```

### 2.2 Call Sites & Consumer Impact
`ElevationProfile` is consumed across 6 key UI surfaces:
1. [`MapDetailLayout.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt): Interactive workout map detail view with synchronized scrubbing (`selectedDistance`).
2. [`WorkoutSummary.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt): Workout cards in aftermath lists.
3. [`SensorGridScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt): Live tracking dashboard.
4. [`RouteOnMapScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt): Route inspection.
5. [`SegmentOnMapScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentOnMapScreen.kt): Segment details.
6. [`RouteItem.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItem.kt) / [`SegmentItem.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/segmentlist/SegmentItem.kt): List previews.

The zoom functionality must be self-contained within `ElevationProfile` so that existing consumers continue to function without breaking changes to their parameter signatures (`pathPoints`, `currentDistance`, `minAltitudeOverride`, `maxAltitudeOverride`, `onDistanceSelected`, `modifier`).

---

## 3. Chesterton's Fence Archaeology & Invariant Identification

### 3.1 Requirement Archaeology
* **Origin**: Introduced in `ATT-508` / `REQ-UI-126` (adaptive elevation profile bounds and vertical sanitization).
* **Chesterton's Fence Audit**:
  * *Sanitized Vertical Bounds* (`calculateElevationBounds`): Prevents vertical compression and millibar noise on flat coastal routes. **Invariant**: Vertical bounds and height adaptation (`calculateElevationProfileHeight`) MUST NOT be destabilized during horizontal zooming.
  * *Grade-Colored Segments* (`TTColor.Zone1 .. Zone5`): Segments are colored based on slope grade ($<2\%$, $2-5\%$, $5-10\%$, $10-15\%$, $15-20\%$, $>20\%$). **Invariant**: Grade calculation is derived from real segment altitude and distance differences ($d_2 - d_1$), independent of the screen zoom scale. Zooming must NEVER alter the calculated grade percentage or segment colors.
  * *Synchronized Scrubbing Contract* (`onDistanceSelected(Double?)`): When an athlete scrubs, the callback receives absolute distance in meters ($0 .. \text{totalDist}$), which downstream views (`MapDetailLayout`) use to highlight map markers and metrics. **Invariant**: Distance values emitted to `onDistanceSelected` must remain absolute workout distances, accurately mapped from the visible sub-window.

---

## 4. Technical Architecture: Visible Window Model

### 4.1 Window Coordinate Mathematics
Let:
* $D_{\text{total}} = \text{totalDist}$
* $Z \in [1.0, 10.0]$: Current horizontal zoom scale.
* $W_{\text{vis}} = \frac{D_{\text{total}}}{Z}$: Size of the visible distance window.
* $D_{\text{start}} \in [0.0, D_{\text{total}} - W_{\text{vis}}]$: Start of the visible window.
* $D_{\text{end}} = D_{\text{start}} + W_{\text{vis}} \le D_{\text{total}}$: End of the visible window.

For any absolute distance $d \in [0.0, D_{\text{total}}]$:
* Normalized screen position:
  $$u(d) = \frac{d - D_{\text{start}}}{W_{\text{vis}}}$$
* Canvas X coordinate:
  $$x = u(d) \cdot \text{width}$$

For any screen X coordinate $x \in [0, \text{width}]$:
* Normalized screen position: $u = \frac{x}{\text{width}}$
* Corresponding absolute distance:
  $$d = D_{\text{start}} + u \cdot W_{\text{vis}}$$

When $Z = 1.0$, $D_{\text{start}} = 0.0$ and $W_{\text{vis}} = D_{\text{total}}$, collapsing exactly to the existing formulas:
$$u(d) = \frac{d}{D_{\text{total}}}$$

### 4.2 Gesture Architecture & Disambiguation
To provide an intuitive user experience without gesture conflicts:
1. **Multi-Touch (2 Pointers)**:
   * Horizontal pinch-to-zoom updates $Z$ and anchors around gesture centroid $x_{\text{centroid}}$.
   * Two-finger drag translates $D_{\text{start}}$.
2. **Single-Touch (1 Pointer)**:
   * When unzoomed ($Z = 1.0x$): 1-finger drag operates the scrubber (`onDistanceSelected`).
   * When zoomed ($Z > 1.0x$):
     * A clean mode toggle in the top-end overlay allows toggling between **Pan** mode and **Scrub** mode.
     * In Pan mode: 1-finger horizontal dragging pans the visible window ($D_{\text{start}}$), while tapping sets the scrubber point.
     * In Scrub mode: 1-finger horizontal dragging scrubs along the visible curve.
3. **Double-Tap**:
   * Double-tapping the graph surface smoothly resets zoom to $1.0x$ ($D_{\text{start}} = 0.0$).
4. **Accessible Overlay Controls**:
   * Zoom indicator badge (e.g. `2.5x`).
   * Reset button `[1.0x]` to instantly reset zoom.
   * `+` and `-` zoom buttons for one-handed / non-pinch accessibility.

---

## 5. Edge Cases & Safeguards

1. **Short / Zero-Distance Activities**:
   * If $\text{totalDist} \le 10\text{m}$, zooming is disabled ($Z = 1.0x$) to prevent mathematical instability.
2. **Extreme Pan Out-of-Bounds**:
   * $D_{\text{start}}$ is clamped strictly to $[0.0, (D_{\text{total}} - W_{\text{vis}}).coerceAtLeast(0.0)]$.
3. **Marker Clipping**:
   * When $Z > 1.0x$, if the selected distance $d$ falls outside $[D_{\text{start}}, D_{\text{end}}]$, the dashed line and coordinate dot are gracefully hidden or pinned to the border, avoiding visual artifacts outside the chart bounds.
4. **Adaptive X-Axis Labels**:
   * X-axis distance labels automatically recompute tick step sizes based on $W_{\text{vis}}$ rather than $D_{\text{total}}$ when zoomed in, ensuring meaningful distance marks (e.g. every 500m instead of every 10km).

---

## 6. Conclusion & Gate 1 Readiness

Stage 1 analysis confirms feasibility with zero risk to existing database schemas or service background processing. All changes are concentrated in `ElevationProfile.kt`.
Ready for Gate 1 review and progression to Stage 2 (Requirement & Test Specification).
