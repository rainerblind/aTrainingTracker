# Stage 2 Requirement & Test Specification: Elevation Graph Support Zooming

**Ticket**: `ATT-527` / Subtask: `ATT-1637`  
**Date**: 2026-09-29  
**Status**: Completed  
**Author**: AI Agent 1 (Implementer)  
**Requirements**: `REQ-UI-192`  
**Test Cases**: `TST-UI-146`  

---

## 1. Specification Overview

This document specifies the formal functional requirements, mathematical formulas, state invariants, gesture interaction models, and verification tests for horizontal zooming and panning within the [`ElevationProfile`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt) component.

The objective is to provide athletes with deep, high-resolution inspection of specific climbs, interval efforts, and gradient pitches on routes of any length without losing spatial context, while preserving synchronized coordinate scrubbing and vertical elevation bounds.

---

## 2. Requirement Details (`REQ-UI-192`)

### 2.1 State Representation & Mathematical Coordinate Transformations
1. **State Parameters**:
   * $D_{\text{total}} = \text{totalDist}$: Total route distance (in meters).
   * $Z \in [1.0f, 10.0f]$: Horizontal zoom scale, initialized to $1.0f$.
   * $W_{\text{vis}} = \frac{D_{\text{total}}}{Z}$: Visible distance window width.
   * $D_{\text{start}} \in [0.0, (D_{\text{total}} - W_{\text{vis}}).coerceAtLeast(0.0)]$: Visible window start distance, initialized to $0.0$.
   * $D_{\text{end}} = D_{\text{start}} + W_{\text{vis}} \le D_{\text{total}}$.
2. **Forward Mapping (Distance to Screen Coordinate)**:
   For any distance $d \in [0.0, D_{\text{total}}]$:
   * Normalized horizontal ratio:
     $$u(d) = \frac{d - D_{\text{start}}}{W_{\text{vis}}}$$
   * Pixel X position on canvas of width $W$:
     $$x(d) = u(d) \cdot W$$
   * Note: When $Z = 1.0f$, $u(d) = \frac{d}{D_{\text{total}}}$ (100% backward compatible).
3. **Inverse Mapping (Screen Coordinate to Distance)**:
   For any pixel X position $x \in [0, W]$:
   * Normalized ratio: $u = \frac{x}{W}$
   * Absolute distance:
     $$d(x) = (D_{\text{start}} + u \cdot W_{\text{vis}}).\text{coerceIn}(0.0, D_{\text{total}})$$

### 2.2 Gesture Model & Interaction Semantics
1. **Multi-Touch Pinch-to-Zoom (Centroid Anchored)**:
   * Two-pointer pinch gestures compute horizontal scale multiplier $\Delta Z$.
   * New zoom scale: $Z' = (Z \cdot \Delta Z).\text{coerceIn}(1.0f, 10.0f)$.
   * Anchor Invariant: The distance at the gesture centroid $x_{\text{centroid}}$ MUST remain stationary under the user's fingers:
     $$d_{\text{centroid}} = D_{\text{start}} + \frac{x_{\text{centroid}}}{W} \cdot W_{\text{vis}}$$
     $$D_{\text{start}}' = \left(d_{\text{centroid}} - \frac{x_{\text{centroid}}}{W} \cdot \frac{D_{\text{total}}}{Z'}\right).\text{coerceIn}(0.0, D_{\text{total}} - W_{\text{vis}}')$$
2. **Pan Navigation**:
   * Dragging horizontally by pixel delta $\Delta x$:
     $$\Delta d = -\frac{\Delta x}{W} \cdot W_{\text{vis}}$$
     $$D_{\text{start}}' = (D_{\text{start}} + \Delta d).\text{coerceIn}(0.0, (D_{\text{total}} - W_{\text{vis}}).\text{coerceAtLeast}(0.0))$$
3. **Double-Tap Reset**:
   * Double-tapping anywhere on the elevation profile canvas resets $Z = 1.0f$ and $D_{\text{start}} = 0.0$.
4. **Accessible Controls & UI Overlay**:
   * When $Z > 1.0f$, display a compact overlay containing:
     * Zoom level badge (e.g. `2.5x`).
     * Tap-to-reset button (restores $1.0x$).
     * Zoom In (`+`) and Zoom Out (`-`) buttons for one-handed operation.
5. **Synchronized Scrubbing**:
   * When the user taps or scrubs along the curve, the touched pixel $x$ maps to $d(x)$ and emits to `onDistanceSelected(d)`.
   * Downstream views (`MapDetailLayout`) receive the exact absolute distance and update map markers without discrepancy.
   * If a selected distance $d$ falls outside the currently visible window $[D_{\text{start}}, D_{\text{end}}]$, the vertical dashed line and marker are cleanly hidden.
6. **Adaptive X-Axis Distance Ticks**:
   * Ticks automatically recalculate their spacing interval based on $W_{\text{vis}}$ rather than $D_{\text{total}}$:
     * Metric: $> 50\text{km} \to 10\text{km}$; $> 20\text{km} \to 5\text{km}$; $> 5\text{km} \to 1\text{km}$; $> 1.5\text{km} \to 500\text{m}$; $\le 1.5\text{km} \to 200\text{m}$ or $100\text{m}$.
     * Imperial: Adaptive steps in miles / fractions of miles.
   * Eliminates cluttered or empty axes when zoomed in.

---

## 3. Test Specification (`TST-UI-146`)

### 3.1 Suite 1: Mathematical Window & Clamping Unit Verification (`ElevationProfileZoomMathTest.kt`)
* **Test 1.1 (Default State)**: Verify that with default parameters, $Z = 1.0f$, $D_{\text{start}} = 0.0$, and $W_{\text{vis}} = D_{\text{total}}$.
* **Test 1.2 (Pinch Scaling with Centroid Anchoring)**: For a 20km route, simulate zooming in $2.0x$ anchored at $x = 0.5 \cdot W$ (10km mark). Verify that $W_{\text{vis}} = 10\text{km}$ and $D_{\text{start}} = 5\text{km}$ (center remains at 10km).
* **Test 1.3 (Boundary Clamping)**:
  * Attempt to set $Z = 0.5f \to$ clamped to $1.0f$.
  * Attempt to set $Z = 25.0f \to$ clamped to $10.0f$.
  * Attempt to pan $D_{\text{start}} < 0.0 \to$ clamped to $0.0$.
  * Attempt to pan $D_{\text{start}} > D_{\text{total}} - W_{\text{vis}} \to$ clamped to $D_{\text{total}} - W_{\text{vis}}$.
* **Test 1.4 (Short Track Safeguard)**: For a route with $D_{\text{total}} = 5.0\text{m}$, verify that zooming is locked to $1.0f$.

### 3.2 Suite 2: Coordinate & Scrubbing Verification
* **Test 2.1 (Forward/Inverse Invertibility)**: For any $x \in [0, W]$, verify that $x(d(x)) \approx x$ within 0.01px precision across various zoom levels ($1.0x, 2.5x, 8.0x$).
* **Test 2.2 (Absolute Distance Emission)**: Verify that tapping at the center of the screen when $D_{\text{start}} = 5\text{km}$ and $W_{\text{vis}} = 4\text{km}$ accurately resolves to $7.0\text{km}$ ($5 + 0.5 \times 4$).
* **Test 2.3 (Marker Visibility Filter)**: Verify that when $d = 2\text{km}$ and visible window is $[5\text{km}, 10\text{km}]$, the marker is reported as outside the visible range.

### 3.3 Suite 3: Adaptive Distance Tick Calculation
* **Test 3.1 (Metric Adaptive Ticks)**:
  * $W_{\text{vis}} = 80\text{km} \to \text{step} = 10\text{km}$.
  * $W_{\text{vis}} = 15\text{km} \to \text{step} = 5\text{km}$.
  * $W_{\text{vis}} = 3\text{km} \to \text{step} = 1\text{km}$.
  * $W_{\text{vis}} = 1\text{km} \to \text{step} = 200\text{m}$.
* **Test 3.2 (Imperial Adaptive Ticks)**: Verify equivalent adaptive progression in miles.

### 3.4 Suite 4: Clean-Room Regression Verification
* Execute full project test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all map, workout, route, and tracking modules.

---

## 4. Invariants & Chesterton's Fence Matrix

| Invariant | Scope | Preservation Mechanism |
|---|---|---|
| **Vertical Altitude Bounds** | `calculateElevationBounds` | Horizontal zoom does not mutate vertical min/max/range. |
| **Adaptive Canvas Height** | `calculateElevationProfileHeight` | Canvas height depends purely on altitude range, unchanged by horizontal zoom. |
| **Grade Percentages & Colors** | Slope calculation | Slope is calculated from real altitude differences $\frac{\Delta alt}{\Delta dist}$, independent of screen zoom. |
| **Scrubbing Contract** | `onDistanceSelected(Double?)` | Callback emits absolute distance in meters, maintaining exact synchronization with map track. |
| **Consumer Parameter Parity** | `ElevationProfile` signature | Signatures in `ElevationProfile.kt` maintain 100% backward compatibility. |
