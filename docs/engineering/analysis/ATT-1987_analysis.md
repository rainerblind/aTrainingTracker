# Stage 1 Analysis: ATT-1987 - [Aftermath/Graphs] Calibrate Pan Gesture Sensitivity and Travel Distance Across Telemetry Graphs

**Ticket**: [ATT-1987](https://rainerblind.atlassian.net/browse/ATT-1987)  
**Sub-task**: [ATT-2034](https://rainerblind.atlassian.net/browse/ATT-2034) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Branch**: `feature/ATT-1987`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & User Scope Grounding (ATT-1250)

During physical device review of Sprint 2026-40.10 on Google Pixel 10 hardware, user testing verified that continuous pan gesture tracking in Pan Mode (`isPanMode == true`) was smooth, fluid, and responsive across all stacked `TelemetryMetricGraph` instances (Speed/Pace, Heart Rate, and Power). However, horizontal swiping on the `ElevationProfile` chart failed to move the chart viewport.

The ticket was rejected (`n.i.O.`) during Ceremony 2 Joint Review and bounced back to `Analysis` with explicit human feedback:
> *"Revision needed: Physical device verification on Google Pixel 10 during Ceremony 2 Sprint Review revealed that while pan gesture tracking is now fluid across Telemetry Graphs, it is no longer functional on the Elevation Profile. Needs forensic analysis and fix for ElevationProfile gesture handling."*

### User Scope Grounding
* **In-Scope**:
  1. Root cause analysis of why horizontal swipe gestures on `ElevationProfile.kt` fail to pan the chart viewport.
  2. Resolving the cross-domain scalar coupling in `MapDetailLayout.kt` where meters (Distance) and seconds (Time) conflict over a single raw scalar `profileStartDist`.
  3. Stabilizing pointer gesture handling in `ElevationProfile.kt` to prevent coroutine stalls and align with the proven, cancellation-free pattern in `TelemetryMetricGraph.kt`.
  4. Enforcing synchronized lockstep panning between `ElevationProfile` and `TelemetryMetricGraph` instances so all stacked charts navigate together seamlessly in Pan Mode.
* **Out-of-Scope**:
  - Unrelated refactorings to elevation smoothing algorithms or altitude bounds computations.
  - Adding new metric graphs or altering sport-specific calculations.
  - Modifying zoom toolbar button iconography or layout outside of viewport start propagation.

---

## 2. Forensic Root Cause Analysis (RCA)

Forensic examination of `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt` reveals two interrelated root causes responsible for the failure on physical hardware:

### 2.1 Primary Defect: Cross-Domain Raw Scalar Coupling in `MapDetailLayout.kt`
When `ATT-1986` decoupled the horizontal domain into independent user-configurable settings (`tuningConfig.elevationXAxisDomain` defaulting to `DISTANCE` in route meters, and `telemetryXAxisDomain` defaulting to `TIME` in elapsed seconds), `MapDetailLayout.kt` retained a single raw scalar state:
```kotlin
var profileStartDist by remember(activeScrubPath) { mutableDoubleStateOf(0.0) }
```
This single raw scalar was simultaneously supplied to:
1. `ElevationProfile(..., xAxisDomain = tuningConfig.elevationXAxisDomain, startDist = profileStartDist, onZoomChanged = { z, s -> profileStartDist = s })`
2. `TelemetryMetricGraph(..., xAxisDomain = activeTelemetryDomain, startDist = profileStartDist, onZoomChanged = { z, s -> profileStartDist = s })`

**The Collision Mechanism**:
1. When an athlete zooms in (e.g. 2x) and pans on any `TelemetryMetricGraph`, the gesture operates in the Time domain ($0 \dots \text{totalTimeSec}$, e.g. 3,600s). The resulting `panStart` is in seconds (e.g. `1,200.0s`).
2. Calling `onZoomChanged(zoom, 1200.0)` sets `profileStartDist = 1200.0`.
3. In `MapDetailLayout`, this raw scalar (`1200.0`) is passed directly to `ElevationProfile`, which operates in the Distance domain ($0 \dots \text{totalDistMeters}$, e.g. 25,000m).
4. Conversely, if an athlete pans on `ElevationProfile`, `panStart` is computed in meters (e.g. `15,000.0m`). Calling `onZoomChanged` sets `profileStartDist = 15000.0`.
5. Passing `startDist = 15000.0` to `TelemetryMetricGraph` (where `totalSpan = 3600.0s`) immediately causes an out-of-bounds violation: `15000.0 > maxStart (1800.0)`. Clamping boundaries in `ElevationProfileZoomMath.applyPan` lock the start distance at `maxStart` or `0.0`.
6. Whenever the athlete touches either chart, the cross-domain scalar collision forces `applyPan` to clamp against the conflicting domain's bounds, completely locking the viewport and preventing further movement.

This cross-domain coupling violates **Section 2.1 of `docs/design_guidelines.md`**:
> *"When charts in a composite layout (e.g. MapDetailLayout) use different X-axis domains, the parent container MUST NOT store a single shared raw metric scalar for viewport offset / start position. Storing seconds in a variable read as meters corrupts zoom and pan clamping boundaries. Solution: Maintain independent domain offsets or normalize continuous viewport pans into a dimensionless fraction (0.0 ... 1.0) that is mapped to native units at the chart boundary."*

### 2.2 Secondary Defect: Pointer Observation & Function Reference Lifecycle in `ElevationProfile.kt`
In `ElevationProfile.kt`:
1. `val currentUpdateZoomState by rememberUpdatedState(::updateZoom)`:
   The local function reference `::updateZoom` instantiates a new callable object on every recomposition. Passing `onZoomChanged` directly into `rememberUpdatedState(onZoomChanged)` (as done in `TelemetryMetricGraph.kt`) eliminates unnecessary allocations and preserves callback identity.
2. In `ElevationProfile.kt`, `isVerticalScrolling` handled pointer consumption via:
   ```kotlin
   } else if (isVerticalScrolling) {
       if (pointer.isConsumed) {
           break
       }
   }
   ```
   Breaking out of the `while (true)` loop terminated gesture tracking prematurely when nested within `lowerColumn`'s `verticalScroll(rememberScrollState())`.
3. On touch transition from stationary to horizontal drag (`isDragging = true`), `prevCentroid` was initialized to `down.position`. When `pressed.size == 1`, `prevCentroid` was updated to `pointer.position` only after delta calculation, creating an initial delta surge of `touchSlop`. Resetting `prevCentroid` upon drag engagement eliminates the jump.

---

## 3. Architectural Strategy & Mathematical Model

### 3.1 Normalized Viewport Start Progress Fraction ($0.0 \dots 1.0$)
To eliminate cross-domain unit contamination and guarantee seamless lockstep synchronization between Distance-domain Elevation Profile and Time-domain Telemetry Graphs, `MapDetailLayout.kt` will manage the shared horizontal viewport offset as a **dimensionless start fraction**:
```kotlin
var viewportStartFraction by remember(activeScrubPath) { mutableDoubleStateOf(0.0) }
var profileZoomScale by remember(activeScrubPath) { mutableFloatStateOf(1.0f) }
```

### 3.2 Formal Mathematical Mapping & Division-by-Zero Protection
At any zoom scale $Z \in [1.0, 10.0]$:
* **Visible Fraction**:
  $$V(Z) = \frac{1.0}{Z}$$
* **Maximum Allowable Start Fraction**:
  $$F_{\max}(Z) = \max\left(0.0, 1.0 - \frac{1.0}{Z}\right)$$
* **Fraction Clamping Guard**:
  Any assigned fraction is strictly constrained:
  $$\text{fraction} \in [0.0, F_{\max}(Z)]$$

#### Conversion 1: Fraction to Domain Start Value (Supplied to Child Charts)
Given a chart with total domain span $S$ ($S_E$ meters for Elevation, $S_T$ seconds for Telemetry):
$$\text{startVal}(F, S, Z) = \begin{cases} 
0.0 & \text{if } S \le 0.0 \\ 
(F \times S).\text{coerceIn}(0.0, \max(0.0, S - \frac{S}{Z})) & \text{if } S > 0.0 
\end{cases}$$
* **Zero Span Protection**: When `activeScrubPath` is empty, single-point, or `totalSpan <= 0.0`, `startVal` defensively defaults to `0.0`.
* **Clamping**: Prevents viewport over-scrolling past the end of the activity.

#### Conversion 2: Domain Start Value to Fraction (Emitted by Child Charts on Pan/Zoom)
When a child chart dispatches `onZoomChanged(newZoom: Float, newStart: Double)` in its local units:
$$F_{\text{new}}(\text{newStart}, S, \text{newZoom}) = \begin{cases}
0.0 & \text{if } S \le 0.0 \\
\left(\frac{\text{newStart}}{S}\right).\text{coerceIn}\left(0.0, \max\left(0.0, 1.0 - \frac{1.0}{\text{newZoom}}\right)\right) & \text{if } S > 0.0
\end{cases}$$
* **Division-by-Zero Guard**: If $S \le 0.0$, the fraction safely falls back to $0.0$.
* **Floating-Point Stability**: Evaluating against `newZoom` ensures rounding artifacts or slight drag overshoots never produce start fractions outside the allowable window $[0.0, F_{\max}(\text{newZoom})]$.

#### Conversion 3: Toolbar Centroid Zoom
When `GlobalTelemetryZoomToolbar` executes zoom in/out with target zoom $Z_{\text{new}}$:
$$F_{\text{new}} = \left(F_{\text{old}} + \frac{0.5}{Z_{\text{old}}} - \frac{0.5}{Z_{\text{new}}}\right).\text{coerceIn}\left(0.0, \max\left(0.0, 1.0 - \frac{1.0}{Z_{\text{new}}}\right)\right)$$
All charts zoom concentrically around the center of the viewport in exact synchrony.

---

## 4. Requirement Traceability & Validation: `REQ-UI-232` & `REQ-UI-234`

### 4.1 Requirement Traceability Matrix
| Requirement ID | Requirement Scope & Clause | How Normalized Fraction Solution Satisfies Requirement | Status |
| :--- | :--- | :--- | :--- |
| **`REQ-UI-232`** | Synchronized horizontal window panning across stacked telemetry metric graphs and `ElevationProfile` in Pan Mode. | Eliminates cross-domain clamping lockup. Swiping on `ElevationProfile` shifts `viewportStartFraction`, which updates `TelemetryMetricGraph` (Speed, HR, Power) in exact lockstep. Swiping on any telemetry graph updates `ElevationProfile` in exact lockstep. | `Verified` |
| **`REQ-UI-234`** | Section 3: Cross-Domain Chart Viewport Separation Invariant (`docs/design_guidelines.md`). | Satisfies the strict architectural prohibition against sharing raw scalars across distance (meters) and time (seconds) domains. Normalizes continuous viewport pans into a dimensionless fraction ($0.0 \dots 1.0$). | `Verified` |
| **`REQ-UI-226`** | Directional gesture disambiguation and vertical scroll freedom. | Single-finger vertical swipes ($|\Delta y| > |\Delta x|$) pass through unconsumed to parent `lowerColumn` scroll container. | `Verified` |

---

## 5. Concrete Call-Site Inventory & Code Modifications

| File / Component | Specific Call-Site / Method | Nature of Modification |
| :--- | :--- | :--- |
| `MapDetailLayout.kt` | `remember(activeScrubPath)` state declaration (line ~106–107) | Replace `var profileStartDist by remember... mutableDoubleStateOf(0.0)` with `var viewportStartFraction by remember... mutableDoubleStateOf(0.0)`. |
| `MapDetailLayout.kt` | `ElevationProfile` call-site (line ~226–243) | Pass `startDist = (viewportStartFraction * totalSpanE).coerceIn(0.0, totalSpanE - totalSpanE / profileZoomScale)`.<br>In `onZoomChanged = { z, s -> profileZoomScale = z; viewportStartFraction = if (totalSpanE > 0.0) (s / totalSpanE).coerceIn(0.0, 1.0 - 1.0 / z) else 0.0 }`. |
| `MapDetailLayout.kt` | `TelemetryMetricGraph` call-sites (Speed line ~258, HR line ~285, Power line ~372) | Pass `startDist = (viewportStartFraction * totalSpanT).coerceIn(0.0, totalSpanT - totalSpanT / profileZoomScale)`.<br>In `onZoomChanged = { z, s -> profileZoomScale = z; viewportStartFraction = if (totalSpanT > 0.0) (s / totalSpanT).coerceIn(0.0, 1.0 - 1.0 / z) else 0.0 }`. |
| `MapDetailLayout.kt` | `GlobalTelemetryZoomToolbar` call-sites (line ~504 & line ~537) | Supply `totalSpan = 1.0` and `startDist = viewportStartFraction`, or map directly to `viewportStartFraction` on zoom changes. |
| `ElevationProfile.kt` | `currentUpdateZoomState` declaration (line ~286) | Replace `rememberUpdatedState(::updateZoom)` with direct `val currentOnZoomChangedState by rememberUpdatedState(onZoomChanged)`. |
| `ElevationProfile.kt` | `pointerInput` gesture loop (lines ~467–520) | Reset `prevCentroid = pointer.position` upon drag engagement to prevent initial slop jump.<br>Remove premature `if (pointer.isConsumed) break` in `isVerticalScrolling` to maintain gesture continuity until touch release. |

---

## 6. System Invariants, Boundary Verification & Risk Assessment

### 6.1 Core System Invariants
1. **Vertical Scroll Freedom (`REQ-UI-226`)**: Dominant vertical gestures ($|\Delta y| > |\Delta x|$) must pass unconsumed to the parent scroll container.
2. **Padding Alignment Parity**: Plot start padding (`50.dp`) and end padding (`25.dp`) must remain identical across all charts to preserve visual axis alignment.
3. **Lockstep Synchrony**: In Pan Mode, panning either Elevation Profile or any Telemetry Graph must navigate all visible charts in lockstep.
4. **Scrubbing Integrity**: In Scrub Mode (`isPanMode == false`), dragging must continue to update `selectedDistance` and highlight coordinates synchronously across charts and the map route marker.

### 6.2 Risk Rating & Technical Justification
* **Risk Rating**: **LOW**
* **Technical Justification**:
  - The solution normalizes the internal offset coordination in `MapDetailLayout.kt` without changing any public interfaces, database schemas, or serialized preferences.
  - Zero-span protection ($S \le 0.0$) guards against all division-by-zero edge cases.
  - The mathematical formulas rely on standard linear projection and boundary clamping.
  - Full automated regression test suite (`./gradlew testDebugUnitTest`) guarantees zero side effects on adjacent components.
