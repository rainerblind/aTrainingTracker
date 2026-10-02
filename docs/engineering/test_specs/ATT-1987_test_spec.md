# Stage 2: Requirement & Test Specification - ATT-1987: [Aftermath/Graphs] Calibrate Pan Gesture Sensitivity and Travel Distance Across Telemetry Graphs

**Ticket**: [ATT-1987](https://rainerblind.atlassian.net/browse/ATT-1987)  
**Sub-task**: [ATT-2035](https://rainerblind.atlassian.net/browse/ATT-2035) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Requirement Mapping**: `REQ-UI-232` (*Aftermath/Graphs: Synchronized Horizontal Window Panning Across Stacked Telemetry Metric Graphs and Elevation Profile in Pan Mode*), `REQ-UI-234` (*UI & Interaction Design System: Cross-Domain Chart Viewport Separation*)  
**Test Spec ID**: `TST-UI-190`  
**Branch**: `feature/ATT-1987`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-232 Refinement)

### 1.1 Problem Statement & Rationale
When Pan Mode (`isPanMode == true`) is activated in `MapDetailLayout`, athletes expect to pan horizontally across any chart (Elevation Profile, Speed/Pace, Heart Rate, Power) and see the entire multi-metric layout move together in smooth, synchronized lockstep. 

Prior implementation coupled all charts through a single raw scalar `profileStartDist`. When Distance and Time domains were decoupled (`REQ-UI-233`), passing seconds (e.g. 1200s) to distance-based elevation profiles (or meters e.g. 15000m to time-based telemetry graphs) corrupted clamping boundaries in `ElevationProfileZoomMath.applyPan`, effectively locking the elevation profile viewport at `maxStart` or `0.0`. Furthermore, `ElevationProfile.kt` suffered from callback recreation via `::updateZoom` and premature gesture loop aborts.

### 1.2 Functional & Architectural Requirements

1. **Normalized Viewport Start Progress Fraction (`MapDetailLayout.kt`)**:
   - `MapDetailLayout.kt` SHALL store and manage the shared horizontal viewport offset strictly as a dimensionless fraction:
     ```kotlin
     var viewportStartFraction by remember(activeScrubPath) { mutableDoubleStateOf(0.0) }
     var profileZoomScale by remember(activeScrubPath) { mutableFloatStateOf(1.0f) }
     ```
   - At any zoom scale $Z \ge 1.0$:
     - The visible fraction of the activity SHALL equal $V = \frac{1.0}{Z}$.
     - The maximum allowable start fraction SHALL equal $F_{\max} = 1.0 - V = 1.0 - \frac{1.0}{Z}$.
     - `viewportStartFraction` SHALL strictly reside within $[0.0, F_{\max}]$.

2. **Fraction-to-Domain Mapping with Zero-Span Protection**:
   - For any chart with total domain span $S$ ($S_E$ for Elevation Profile, $S_T$ for Telemetry Graphs):
     $$\text{startVal}(F, S, Z) = \begin{cases} 
     0.0 & \text{if } S \le 0.0 \\ 
     (F \times S).\text{coerceIn}\left(0.0, \max\left(0.0, S - \frac{S}{Z}\right)\right) & \text{if } S > 0.0 
     \end{cases}$$
   - When $S \le 0.0$ (e.g. empty or trackless workout without duration), the system SHALL safely return `0.0`, preventing `NaN` or division-by-zero crashes.

3. **Domain-to-Fraction Mapping on Pan / Zoom Emitted by Child Charts**:
   - When any child chart dispatches `onZoomChanged(newZoom: Float, newStart: Double)` in its local domain units:
     $$F_{\text{new}}(\text{newStart}, S, \text{newZoom}) = \begin{cases}
     0.0 & \text{if } S \le 0.0 \\
     \left(\frac{\text{newStart}}{S}\right).\text{coerceIn}\left(0.0, \max\left(0.0, 1.0 - \frac{1.0}{\text{newZoom}}\right)\right) & \text{if } S > 0.0
     \end{cases}$$
   - Updating `viewportStartFraction = F_{\text{new}}` SHALL immediately project the updated offset to all other stacked charts in their respective domain units.

4. **ElevationProfile Callback & Gesture Lifecycle (`ElevationProfile.kt`)**:
   - `ElevationProfile.kt` SHALL reference `onZoomChanged` directly via `rememberUpdatedState(onZoomChanged)` rather than creating new function allocations (`::updateZoom`) on each recomposition.
   - In `ElevationProfile.kt`, `prevCentroid` SHALL be reset to `pointer.position` immediately upon horizontal drag activation (`isDragging == true`), eliminating the abrupt initial jump from touch slop accumulation.
   - In `ElevationProfile.kt`, `isVerticalScrolling` SHALL NOT break the gesture loop prematurely (`if (pointer.isConsumed) break` removed), preserving touch tracking continuity until all pointers are unpressed (`pressed.isEmpty()`).

5. **Multi-Chart Synchronous Lockstep Pan Navigation**:
   - Dragging horizontally on `ElevationProfile` SHALL update `viewportStartFraction`, navigating `ElevationProfile`, `TelemetryMetricGraph` (Speed/Pace, HR, Power), and the map route marker in exact lockstep.
   - Dragging horizontally on any `TelemetryMetricGraph` SHALL update `viewportStartFraction`, navigating `ElevationProfile` and all sister telemetry graphs in exact lockstep.
   - Cross-domain clamping collisions between meters and seconds SHALL be 100% eliminated.

6. **Preservation of Core Invariants**:
   - Vertical scroll freedom (`REQ-UI-226`): dominant vertical gestures ($|\Delta y| > |\Delta x|$) must pass unconsumed to the parent container.
   - Horizontal plot area padding parity (`start = 50.dp, end = 25.dp`) must remain identical across all charts.
   - Zero raw unicode emojis in UI code.
   - 100% 9-language localization parity across strings.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-232` (*Aftermath/Graphs: Synchronized Horizontal Window Panning Across Stacked Telemetry Metric Graphs in Pan Mode*).
   - Linked to `REQ-UI-234` (Cross-Domain Coordinate Space Decoupling in `docs/design_guidelines.md`).
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.8 (`ATT-1876`, Commit `d71b4028`): Global zoom toolbar introduced.
   - Sprint 2026-40.9 (`ATT-1956`, Commit `91611fe1`): Pan mode gesture handling introduced.
   - Sprint 2026-40.10 (`ATT-1987`): Decoupled mutable state from pointerInput keys.
   - Sprint 2026-40.11 (`ATT-1987` Revision): Cross-domain scalar decoupling and Elevation Profile gesture fix.
3. **Root Reason for Existing Formulation**:
   `REQ-UI-232` previously assumed `profileStartDist` could be directly shared across charts because all charts historically operated in the same domain. When `REQ-UI-233` decoupled the X-axis domain (Distance for Elevation, Time for Telemetry), sharing a single raw scalar caused clamping collisions. Refining `REQ-UI-232` to use a normalized dimensionless fraction ($0.0 \dots 1.0$) eliminates cross-domain collisions while preserving 100% lockstep synchrony.
4. **Preservation of Core Invariants**:
   - Single-finger vertical scroll freedom (`REQ-UI-226`) is strictly preserved.
   - Horizontal plot padding parity (`50.dp` start, `25.dp` end) is strictly preserved.
   - Scrubbing when `isPanMode == false` remains 100% intact.
   - 9-language localization parity is maintained.

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Cross-Domain Lockstep Panning)**:
  - *Given* an athlete viewing a workout in `MapDetailLayout` with `isPanMode == true` at zoom scale 2.0x,
  - *When* dragging horizontally across `ElevationProfile`,
  - *Then* the elevation profile viewport SHALL pan smoothly across the swipe travel distance, and all stacked `TelemetryMetricGraph` instances (Speed, Heart Rate, Power) SHALL pan in exact lockstep without clamping lockup.
* **Criterion 2 (Telemetry Drag Lockstep to Elevation)**:
  - *Given* an athlete viewing a workout in `MapDetailLayout` with `isPanMode == true` at zoom scale 2.0x,
  - *When* dragging horizontally across any `TelemetryMetricGraph`,
  - *Then* the telemetry graph SHALL pan smoothly, and `ElevationProfile` SHALL pan in exact lockstep without clamping lockup.
* **Criterion 3 (Boundary Clamping)**:
  - *Given* any chart zoomed in to scale $Z$,
  - *When* dragging past the start or end of the workout,
  - *Then* the viewport start fraction SHALL clamp cleanly to $[0.0, 1.0 - 1.0/Z]$ without jumping or overflow.
* **Criterion 4 (Vertical Scroll Freedom)**:
  - *Given* a single-finger vertical drag over `ElevationProfile` or `TelemetryMetricGraph`,
  - *Then* the gesture SHALL pass unconsumed to `lowerColumn`, allowing natural vertical scrolling without pointer locking.

---

## 2. Test Specification (TST-UI-190)

### 2.1 Test Suite Breakdown

| Test ID | Test Category | Target Class / Method | Verification Procedure | Expected Result |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-190.1]` | Contract Test | `TelemetryMetricGraph.kt` | Verify `pointerInput` keys exclude running `startDist` and `zoomScale`. | Keys match `(totalSpan, isTimeDomain, isPanMode)`. |
| `[TST-UI-190.2]` | Contract Test | `ElevationProfile.kt` | Verify `pointerInput` keys exclude running `currentStartDist` and `currentZoomScale`. | Keys match `(totalSpan, isTimeDomain, isPanMode)`. |
| `[TST-UI-190.3]` | Unit Test | `MapDetailLayoutCrossDomainPanTest.kt` | Test fraction-to-domain calculation and clamping across Distance ($S = 25,000\text{m}$) and Time ($S = 3,600\text{s}$) domains. | Values map to expected meters and seconds without clamping overflow; division by zero guarded. |
| `[TST-UI-190.4]` | Unit Test | `MapDetailLayoutCrossDomainPanTest.kt` | Simulate drag on ElevationProfile (Distance) $\rightarrow$ update fraction $\rightarrow$ compute Telemetry (Time) start offset. | Telemetry start offset updates proportionally in lockstep. |
| `[TST-UI-190.5]` | Unit Test | `MapDetailLayoutCrossDomainPanTest.kt` | Simulate drag on TelemetryMetricGraph (Time) $\rightarrow$ update fraction $\rightarrow$ compute Elevation (Distance) start offset. | Elevation start offset updates proportionally in lockstep. |
| `[TST-UI-190.6]` | Contract Test | `ElevationProfileGestureContractTest.kt` | Inspect `ElevationProfile.kt` source for `rememberUpdatedState(onZoomChanged)` and absence of premature `isVerticalScrolling` loop breaks. | Matches contract pattern. |
| `[TST-UI-190.7]` | Regression | `./gradlew testDebugUnitTest` | Run full clean-room unit test suite across all modules. | 100% pass rate (0 failures). |

---

## 3. Traceability Matrix

| Requirement Clause | Test Case ID | Verification Method | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-232` (Clause 1: Fraction State) | `[TST-UI-190.3]` | Unit Test (`MapDetailLayoutCrossDomainPanTest`) | `Specified` |
| `REQ-UI-232` (Clause 2: Fraction-to-Domain) | `[TST-UI-190.3]` | Unit Test (`MapDetailLayoutCrossDomainPanTest`) | `Specified` |
| `REQ-UI-232` (Clause 3: Domain-to-Fraction) | `[TST-UI-190.3]` | Unit Test (`MapDetailLayoutCrossDomainPanTest`) | `Specified` |
| `REQ-UI-232` (Clause 4: Elevation Gesture) | `[TST-UI-190.2]`, `[TST-UI-190.6]` | Contract Test (`ElevationProfileGestureContractTest`) | `Specified` |
| `REQ-UI-232` (Clause 5: Lockstep Synchrony) | `[TST-UI-190.4]`, `[TST-UI-190.5]` | Unit Test (`MapDetailLayoutCrossDomainPanTest`) | `Specified` |
| `REQ-UI-226` (Vertical Scroll Freedom) | `[TST-UI-190.6]` | Contract Test | `Specified` |
| `REQ-PRO-001` (Regression Safety) | `[TST-UI-190.7]` | Clean-Room Regression (`./gradlew testDebugUnitTest`) | `Specified` |
