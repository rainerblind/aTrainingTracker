# Stage 1 Analysis: ATT-2946 - Calibrate speed-dependent map zoom curve and implement bottom camera padding for forward lookahead

**Ticket**: [ATT-2946](https://atrainingtracker.atlassian.net/browse/ATT-2946)  
**Sub-task**: [ATT-3009](https://atrainingtracker.atlassian.net/browse/ATT-3009) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2946`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Problem Summary

During real-world riding evaluations and on-device desk testing with mock GPS replay (`tools/fake_gps.py`), several significant usability shortcomings and configuration deficiencies in map camera behavior were identified in Follow-Me navigation tracking mode (`MapZoomFocus.FOLLOW_ME`):

1. **Sluggish Speed-Based Zoom Scaling**:
   In `followMeController` (`MapBehaviors.kt:214`), the zoom calculation is hardcoded as:
   ```kotlin
   val targetZoom = (20f - 0.1f * speed).coerceIn(14f, 20f)
   ```
   The input parameter `speed` is supplied directly in meters per second ($\text{m/s}$) from sensor telemetry. Because typical endurance cycling speeds of $20\text{ km/h}$ translate to only $\approx 5.56\text{ m/s}$, the target zoom level only decreases from $20.0$ to $20.0 - (0.1 \times 5.56) \approx 19.44$. This $0.56$ zoom increment is barely perceptible on high-density smartphone screens. Athletes riding at $20\text{ km/h}$ require a noticeably broader field of view ($\approx \text{zoom } 18.0$) to anticipate upcoming crossroads, turns, and descent trajectories safely.

2. **Fixed, Non-Calibratable Base Zoom**:
   The stationary zoom level (standing still at $0\text{ km/h}$) is hardcoded to $20.0\text{f}$. Athletes with varying handlebar mount distances, eyesight requirements, or high-density displays cannot tune this baseline.

3. **Centered Rider Anchor / Absence of Forward Lookahead**:
   In `followMeController`, `CameraPosition.builder().target(currentLocation)` centers the rider coordinate exactly at the geometric center of the viewport with zero bottom padding. Consequently, $50\%$ of the screen real estate is consumed by territory already traversed behind the rider, halving the visible preview of upcoming turns, climbs, and course geometry ahead.

4. **Hardcoded Camera Tilt Angle**:
   Camera tilt is fixed to $70^\circ$, with no configurability for riders who prefer flatter 2D viewing ($0^\circ$) or a moderate 3D perspective ($30^\circ - 45^\circ$).

5. **Lack of Advanced Tuning Integration**:
   There are no user-facing controls in `AdvancedTuningDialog` or `TuningConfig` to adjust Follow-Me camera behavior, disable speed-dependent zoom, or restore defaults.

---

## 2. Forensic Investigation & Root Cause Analysis

### 2.1 The Speed Unit Disconnect in `followMeController`
In `ATrainingTrackerMap.kt:228`, `followMeController` is invoked as:
```kotlin
val filteredBearing = followMeController(zoomFocus, userBearing, userSpeed, currentLocation, cameraPositionState)
```
Here, `userSpeed` is sourced from `BANALServiceRepository.currentSpeed` or `TrackingScreenState.userSpeed`, which is natively emitted in $\text{m/s}$. In `MapBehaviors.kt:214`, the linear coefficient $0.1\text{f}$ was designed assuming speed in $\text{km/h}$ (where $20\text{ km/h} \times 0.1 = 2.0\text{ zoom levels}$), but was applied directly to $\text{m/s}$ without the necessary $3.6\times$ conversion factor ($\text{speedKmh} = \text{speedMps} \times 3.6\text{f}$).

### 2.2 Geometrical Framing in Google Maps Android SDK
The Google Maps Android SDK places the camera target coordinate at the center of the visible map canvas. However, `GoogleMap` natively supports content padding (`setPadding(left, top, right, bottom)`), which shifts the effective viewport center:
$$\text{Effective Center } Y = \frac{\text{Height} - \text{Padding}_{\text{bottom}} + \text{Padding}_{\text{top}}}{2}$$
When bottom content padding is applied (e.g. $30\%$ of map height), the camera target (the rider's location) is visually anchored in the lower third of the display ($\approx 35\%$ from the bottom), leaving $\approx 65\% - 70\%$ of the display area dedicated to the forward corridor. In Maps Compose, this is controlled directly via the `contentPadding` parameter on `GoogleMap`.

---

## 3. Chesterton's Fence Requirement Archaeology

| Mandatory Archaeology Field | Analysis & Traceability Evidence |
| :--- | :--- |
| **Original Requirement ID & Target** | `REQ-MAP-004` (*Zoom depending on speed: Wider context at speed, detail when slow*), targeting `MapBehaviors.kt` and `ATrainingTrackerMap.kt`. |
| **Historical Origin & Commit Trace** | Early architecture prototype commit establishing `followMeController` with `val targetZoom = (20f - 0.1f * speed).coerceIn(14f, 20f)` and fixed $70^\circ$ tilt. |
| **Root Reason for Existing Formulation** | The initial implementation was a proof-of-concept demonstrating dynamic camera movement. Speed was taken directly from sensor telemetry in $\text{m/s}$ without unit normalization or tuning preference integration, and camera padding was not yet integrated into Compose map containers. |
| **Preservation of Core Invariants** | Low-pass filtered bearing stabilization (`REQ-MAP-003`), smooth camera animation via `cameraPositionState.animate(...)`, manual touch override behavior, map snapshot generation, and Map DSL layer rendering must remain $100\%$ intact. |

---

## 4. User Scope Grounding & Boundary Definition

### 4.1 In-Scope Objectives
1. **Calibratable Speed-Dependent Zoom Curve**:
   - Normalize sensor speed to $\text{km/h}$ ($\text{speedKmh} = \text{speedMps} \times 3.6\text{f}$).
   - Implement calibratable linear target zoom curve between base zoom ($0\text{ km/h}$, default $20.0\text{f}$) and cruising zoom ($20\text{ km/h}$, default $18.0\text{f}$):
     $$\text{zoomSlope} = \frac{\text{baseZoom} - \text{cruisingZoom}}{20.0\text{f}}$$
     $$\text{targetZoom} = (\text{baseZoom} - \text{zoomSlope} \times \text{speedKmh}).\text{coerceIn}(12.0\text{f}, 21.0\text{f})$$
   - If speed zoom is toggled OFF, camera locks firmly at `baseZoom`.
2. **Forward Lookahead Bottom Camera Padding**:
   - Wrap `ATrainingTrackerMap` GoogleMap in `BoxWithConstraints` to measure viewport height.
   - When `zoomFocus == MapZoomFocus.FOLLOW_ME`, apply `contentPadding = PaddingValues(bottom = maxHeight * (paddingPercent / 100f))`.
   - When `zoomFocus != MapZoomFocus.FOLLOW_ME` (e.g. `FIT_ALL`, `MANUAL`, `EXPLICIT_BOUNDS`), apply `PaddingValues(0.dp)`.
3. **Calibratable Camera Tilt**:
   - Make Follow-Me camera tilt configurable from $0^\circ$ (flat 2D) up to $70^\circ$ (full 3D perspective).
4. **DataStore & Advanced Tuning Integration (`TuningConfig`)**:
   - Extend `TuningConfig` and `TuningPreferencesDataStore` with 5 new parameters:
     - `mapFollowMeInitialZoom: Float` (default $20.0\text{f}$, range $15.0\text{f} - 21.0\text{f}$)
     - `mapFollowMeSpeedZoomEnabled: Boolean` (default `true`)
     - `mapFollowMeCruisingZoom: Float` (default $18.0\text{f}$, range $14.0\text{f} - 19.5\text{f}$)
     - `mapFollowMeTiltAngle: Float` (default $70.0\text{f}$, range $0.0\text{f} - 70.0\text{f}$)
     - `mapFollowMeLookaheadPaddingPercent: Float` (default $30.0\text{f}$, range $10.0\text{f} - 50.0\text{f}$)
   - Add sliders and switches in `NavigationSection.kt` under Section 6 of `AdvancedTuningDialog`.
   - Wire into `TuningResetDefaultsButton` for atomic factory reset.
   - 9-language localization parity across all supported application locales.

### 4.2 Out-of-Scope (Non-Goals)
- Refactoring `MapBoundsController` or `EXPLICIT_BOUNDS` route framing logic.
- Altering user location marker arrow rendering or orientation mechanics (`ScrubMarkerLayer`).
- Modifying offline tile caching or Google Maps SDK dependencies.

---

## 5. Architectural Decision & Trade-Off Analysis

### Option A: Direct GoogleMap `contentPadding` + Reactive Tuning Injection (Selected)
- **Mechanism**:
  - `ATrainingTrackerMap` collects `tuningConfigFlow` from `TuningPreferencesDataStore(context)`.
  - In `BoxWithConstraints`, calculates `val bottomPadding = if (zoomFocus == MapZoomFocus.FOLLOW_ME) maxHeight * (tuningConfig.mapFollowMeLookaheadPaddingPercent / 100f) else 0.dp`.
  - Passes `contentPadding = PaddingValues(bottom = bottomPadding)` to `GoogleMap`.
  - Passes tuning parameters into `followMeController`.
- **Pros**:
  - Pure declarative Compose architecture; requires zero native platform bridging.
  - Google Maps SDK automatically shifts projection center and gesture anchor smoothly.
  - Preserves standard manual pan/zoom touch gestures.
- **Cons**:
  - Requires `BoxWithConstraints` around `GoogleMap` (negligible performance cost, already common in Compose layouts).

### Option B: Coordinate Target Projection Offset in LatLng Space
- **Mechanism**:
  - Manually compute a projected coordinate ahead of the rider using bearing and trigonometry, passing the offset coordinate to `CameraPosition.builder().target(offsetLatLng)`.
- **Rejected**:
  - Highly susceptible to camera rotation jitter: when bearing swings rapidly in tight corners, the projection point oscillates widely, causing nausea-inducing camera whipping.
  - Google Map content padding handles projection cleanly without altering the physical target anchor.

---

## 6. Mathematical Specification of Speed-Dependent Zoom Curve

Let:
- $v_{\text{mps}}$ be the instantaneous telemetry speed in $\text{m/s}$.
- $v_{\text{kmh}} = v_{\text{mps}} \times 3.6$ be the speed in $\text{km/h}$.
- $Z_{\text{base}}$ be `mapFollowMeInitialZoom` (default $20.0$, range $15.0 .. 21.0$).
- $Z_{\text{cruise}}$ be `mapFollowMeCruisingZoom` (default $18.0$, range $14.0 .. 19.5$).
- $v_{\text{ref}} = 20.0\text{ km/h}$ be the reference cruising speed.

The linear slope $S$ is defined as:
$$S = \frac{Z_{\text{base}} - Z_{\text{cruise}}}{v_{\text{ref}}}$$
When $Z_{\text{base}} = 20.0$ and $Z_{\text{cruise}} = 18.0$, $S = \frac{2.0}{20.0} = 0.10\text{ zoom levels per km/h}$.

The target camera zoom $Z_{\text{target}}$ is:
$$Z_{\text{target}} = \begin{cases} 
Z_{\text{base}}, & \text{if } \text{speedZoomEnabled} == \text{false} \\
(Z_{\text{base}} - S \times v_{\text{kmh}}).\text{coerceIn}(12.0\text{f}, 21.0\text{f}), & \text{if } \text{speedZoomEnabled} == \text{true}
\end{cases}$$

Example Zoom Scaling Values (Default Profile):
| Speed ($\text{km/h}$) | Speed ($\text{m/s}$) | Calculated Zoom | Perceived Viewport Context |
| :--- | :--- | :--- | :--- |
| $0.0\text{ km/h}$ | $0.00\text{ m/s}$ | $20.00$ | Street / intersection level detail |
| $10.0\text{ km/h}$ | $2.78\text{ m/s}$ | $19.00$ | Immediate approaching turns |
| $20.0\text{ km/h}$ | $5.56\text{ m/s}$ | $18.00$ | Cruising speed forward view |
| $30.0\text{ km/h}$ | $8.33\text{ m/s}$ | $17.00$ | Fast gravel / road section |
| $40.0\text{ km/h}$ | $11.11\text{ m/s}$ | $16.00$ | Descent / high-speed travel |
| $50.0\text{ km/h}$ | $13.89\text{ m/s}$ | $15.00$ | Steep alpine descent |

---

## 7. Requirement & Test Specification Mapping

- **New Requirement ID**: `REQ-MAP-042` (*Calibratable Speed-Dependent Map Zoom Curve, Follow-Me Forward Lookahead Camera Padding & Pitch Tuning*)
- **New Test Case ID**: `TST-MAP-044` (*Speed-Dependent Map Zoom Calibration, Lookahead Padding & Tuning Preferences Verification*)
- **Associated Module**: `MapBehaviors.kt`, `ATrainingTrackerMap.kt`, `TuningPreferencesDataStore.kt`, `NavigationSection.kt`, `AdvancedTuningDialog.kt`

---

## 8. Gate 1 Evaluation Checklist

- [x] Problem statement verified against active source code.
- [x] Mathematical equations for zoom slope and speed conversion verified.
- [x] Chesterton's Fence archaeology populated with historical origin and core invariants.
- [x] Architectural options evaluated with explicit trade-off rationale.
- [x] Scope boundary strictly delineated.
