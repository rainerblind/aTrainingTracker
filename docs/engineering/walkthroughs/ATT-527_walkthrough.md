# Walkthrough: Elevation Graph: Support Zooming (ATT-527)

* **Parent Ticket**: [ATT-527](https://rainerblind.atlassian.net/browse/ATT-527) (*[Feature] Elevation Graph: Support zooming*)
* **Sub-Tasks**:
  * [ATT-1636](https://rainerblind.atlassian.net/browse/ATT-1636) (*[Analysis] Problem Domain & Root Cause Analysis*) - `Erledigt`
  * [ATT-1637](https://rainerblind.atlassian.net/browse/ATT-1637) (*[SWE.1 / SWE.4] Requirement & Test Specification*) - `Erledigt`
  * [ATT-1638](https://rainerblind.atlassian.net/browse/ATT-1638) (*[SWE.2 / SWE.3] Implementation Plan*) - `Erledigt`
  * [ATT-1639](https://rainerblind.atlassian.net/browse/ATT-1639) (*[Implementation] Software Construction & Unit Tests*) - `Erledigt`
  * [ATT-1640](https://rainerblind.atlassian.net/browse/ATT-1640) (*[Test] Verification, Clean-Room Regression & Release Verification*) - `In Bearbeitung`
* **Target Version**: `V4.9.38`
* **Requirement**: [`REQ-UI-192`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L347) (*Interactive Elevation Profile Zoom, Pan & Centered Scaling Navigation*)
* **Test Specification**: [`TST-UI-146`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L409) (*Elevation Profile Zoom, Pan, Reset & Synchronized Scrubbing Verification*)
* **Branch**: `feature/ATT-527`

---

## 1. Overview & Problem Statement

Prior to this feature, the elevation profile chart ([`ElevationProfile.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt)) rendered a static overview representing the entire activity distance from $0.0$ to $D_{\text{total}}$:
1. **Compressed Climb Inspection**: On long routes (e.g. 50km – 150km rides or mountain marathons), short steep climbs and interval segments were compressed into a few screen pixels, making it impossible to analyze local gradients or inspect segment topography.
2. **Lack of Navigation Affordances**: Athletes could not zoom or pan into sections of the elevation profile, nor navigate with single-handed accessible buttons.
3. **Static Distance Ticks**: Intermediate X-axis distance markers were statically derived from total distance, providing no meaningful intermediate distance reference once zoomed.

---

## 2. Architecture & Implementation Summary

### 2.1 Pure Mathematical Viewport Engine ([`ElevationProfileZoomMath.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMath.kt))
* Encapsulates all viewport calculations, coordinate mapping, and boundary constraints in a pure Kotlin engine:
  * **Visible Window**: $W_{\text{vis}} = \frac{D_{\text{total}}}{Z}$, with zoom factor $Z \in [1.0f .. 10.0f]$.
  * **Start Distance Clamping**: $D_{\text{start}} \in [0.0 .. (D_{\text{total}} - W_{\text{vis}})]$, preventing whitespace over-scrolling.
  * **Centroid Zoom Scaling**: `applyZoomAtCentroid` guarantees that the distance coordinate directly beneath the gesture centroid ($d_{\text{centroid}} = D_{\text{start}} + \frac{x_{\text{centroid}}}{W} \cdot W_{\text{vis}}$) remains perfectly stationary on screen during pinch-to-zoom or button zooming.
  * **Pan Navigation**: `applyPan` translates horizontal pixel drag deltas into workout distance shifts, strictly clamped within route boundaries.
  * **Coordinate Bi-directionality**: `distanceToCanvasX` and `canvasXToDistance` provide mutually invertible mappings.
  * **Adaptive X-Axis Ticks**: `calculateAdaptiveDistanceStep` dynamically selects optimal tick intervals based on visible distance $W_{\text{vis}}$ rather than total track distance, supporting both Metric and Imperial units.
  * **Short Track Safeguard**: Gracefully disallows zooming when $D_{\text{total}} \le 10\text{m}$.

### 2.2 Interactive Elevation Profile Canvas ([`ElevationProfile.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt))
* **Cohesive Gesture Pipeline**: Built on `awaitEachGesture`:
  * **Multi-Touch Pinch-to-Zoom**: Two fingers dynamically scale zoom factor $Z$ up to $10.0x$ anchored around the touch centroid.
  * **One-Finger Drag Modes**:
    * **Scrub Mode** (`isPanMode == false`): Dragging or tapping translates screen position to absolute workout distance and dispatches to `onDistanceSelected(dist)`.
    * **Pan Mode** (`isPanMode == true`): Dragging smoothly translates the visible distance window across the route.
  * **Double-Tap Reset**: Double-tapping the graph surface immediately restores the full $1.0x$ view ($D_{\text{start}} = 0.0$, $Z = 1.0x$).
  * **Single-Tap Inspection**: Tapping sets the synchronized scrubber position accurately.
* **Canvas Clipping & Performance Culling**:
  * Applies `clipRect(0f, 0f, width, height)` to keep paths and lines strictly bounded within the chart plotting area.
  * Segments fully outside $[D_{\text{start}} .. D_{\text{start}} + W_{\text{vis}}]$ are culled during iteration for optimal frame rates.
* **Synchronized Scrubber Marker**:
  * Renders vertical dashed guide line, altitude/distance badge, and concentric point marker at the zoomed screen position.
  * Gracefully hides when the selected distance lies outside the visible window $[D_{\text{start}} .. D_{\text{start}} + W_{\text{vis}}]$.
* **Adaptive Grid Lines & Axis Labels**:
  * Dynamically renders intermediate ticks and labels based on $W_{\text{vis}}$.
  * When zoomed in, shows exact start distance $D_{\text{start}}$ at the left axis and visible end distance $D_{\text{start}} + W_{\text{vis}}$ at the right axis.
* **Accessible On-Screen Overlay Controls**:
  * Single-touch `+` (Zoom In) and `-` (Zoom Out) buttons.
  * Pan vs Scrub mode toggle button.
  * Zoom Scale badge (e.g. `2.5x`) with instant tap-to-reset action.

### 2.3 Preservation of Core Invariants
* **Vertical Bounds & Adaptive Height**: Vertical bounds sanitization (`calculateElevationBounds` with 15m outlier rejection and 20m minimum span) and adaptive canvas height (`calculateElevationProfileHeight`) remain 100% untouched.
* **Physical Grade Colors**: Grade slope percentages are computed purely from physical coordinates ($\Delta\text{altitude} / \Delta\text{distance}$), completely independent of horizontal screen scaling.
* **External Contract Synchronization**: `onDistanceSelected` emits absolute distance in meters ($0 .. D_{\text{total}}$), maintaining seamless synchronization with `MapDetailLayout` and route map pins.

---

## 3. Verification & Test Evidence

### 3.1 Unit Test Suite (`ElevationProfileZoomMathTest.kt`)
Added comprehensive test coverage for `REQ-UI-192` / `TST-UI-146`:
1. `calculateVisibleDistance_clampsScaleAndComputesWindow`: Verifies 1.0x, 2.0x, 5.0x, 10.0x window spans, min/max clamping, and short track safeguard.
2. `clampStartDistance_enforcesRouteBoundaries`: Verifies start distance containment and boundary enforcement.
3. `distanceAndCanvasX_areMutuallyInvertible`: Verifies mathematical invertibility between distance and canvas pixel coordinates.
4. `canvasXToDistance_clampsToBounds`: Verifies edge touches and margin clamping.
5. `applyZoomAtCentroid_anchorsAtTouchPoint`: Verifies that the distance coordinate at the touch centroid remains stationary under scaling.
6. `applyZoomAtCentroid_clampsAtEdges`: Verifies behavior at left ($x = 0$) and right ($x = W$) boundaries.
7. `calculateAdaptiveDistanceStep_adaptsToVisibleRange`: Validates adaptive metric and imperial tick steps.
8. `isDistanceVisible_correctlyFiltersCoordinates`: Verifies coordinate filtering for scrubber guide lines.
9. `applyPan_correctlyShiftsStartDistanceAndClamps`: Verifies left/right drag shifts and strict boundary clamping.

**Command**:
```bash
./gradlew testDebugUnitTest --tests "*ElevationProfile*"
```
**Result**: `BUILD SUCCESSFUL` (100% test pass rate).

### 3.2 Full Project Regression Suite
Executed the complete clean-room unit test suite:
```bash
./gradlew testDebugUnitTest
```
**Result**: `BUILD SUCCESSFUL` (zero regressions across all application modules).

---

## 4. Traceability & Stage Status

| Artifact / Entity | ID | Status | Notes |
| :--- | :--- | :--- | :--- |
| **User Story** | [ATT-527](https://rainerblind.atlassian.net/browse/ATT-527) | `Test` -> `Final Review` | Feature: Elevation Graph Zooming |
| **Subtask (Stage 1)** | [ATT-1636](https://rainerblind.atlassian.net/browse/ATT-1636) | `Erledigt` | Problem Domain & Root Cause Analysis |
| **Subtask (Stage 2)** | [ATT-1637](https://rainerblind.atlassian.net/browse/ATT-1637) | `Erledigt` | Requirement & Test Specification |
| **Subtask (Stage 3)** | [ATT-1638](https://rainerblind.atlassian.net/browse/ATT-1638) | `Erledigt` | Implementation Plan (SWE.2 / SWE.3) |
| **Subtask (Stage 4)** | [ATT-1639](https://rainerblind.atlassian.net/browse/ATT-1639) | `Erledigt` | Software Construction & Unit Tests |
| **Subtask (Stage 5)** | [ATT-1640](https://rainerblind.atlassian.net/browse/ATT-1640) | `In Bearbeitung` | Verification, Clean-Room Regression & Walkthrough |
| **Requirement** | `REQ-UI-192` | `Verified` | Elevation Profile Zoom, Pan & Centered Scaling Navigation |
| **Test Specification** | `TST-UI-146` | `Verified` | Elevation Profile Zoom, Pan & Scrubbing Verification |
