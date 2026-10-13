# Stage 1 Analysis: ATT-3264 - Refactor LiveClimbSheet to reuse LiveSegments layout and shared ElevationProfile

**Ticket**: [ATT-3264](https://atrainingtracker.atlassian.net/browse/ATT-3264)  
**Sub-task**: [ATT-3272](https://atrainingtracker.atlassian.net/browse/ATT-3272) (`[Analysis]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*[Epic] Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Branch**: `feature/ATT-3264`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation

During initial ClimbPro development under Sprint 2026-40.14 (`ATT-1281`), `LiveClimbSheet.kt` was constructed rapidly to provide in-ride telemetry and visual gradient feedback for ascending route climbs. To accelerate delivery, several components were implemented in an ad-hoc, isolated manner rather than reusing the established design system:

1. **Elevation Profile Duplication**:
   - `LiveClimbSheet.kt` contains a bespoke 170-line private composable `ClimbProfileCanvas`.
   - It manually recalculates coordinate projection, min/max altitude bounds, path construction, usable height padding, ground baseline, and rider pin positioning—all of which are already maintained and hardened in the canonical `ElevationProfile.kt`.
   - Any enhancements made to `ElevationProfile.kt` (e.g. altitude smoothing, axis formatting, boundary handling, outlier protection, and theming improvements) do not automatically benefit climbs.

2. **Component & Badge Duplication**:
   - `LiveClimbSheet.kt` introduces standalone, bespoke `ClimbCategoryChip` and `ClimbStatusBadge` composables built with raw `Surface` and hardcoded corner shapes, instead of leveraging the design system's standardized `MetricBadge.kt`.

3. **Layout Structure Duplication**:
   - The two-row header (Icon + Title + Category Chip, Status + Counter) and the telemetry HUD row duplicate the layout structure, modifiers, and typographic hierarchy of `SegmentHeader.kt` and `SegmentLiveDetails.kt`.

This architecture violates DRY ("Don't Repeat Yourself"), inflates maintenance overhead, and causes subtle visual divergences across live tracking cockpit sheets.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1. Bespoke `ClimbProfileCanvas` in `LiveClimbSheet.kt`
Lines 283–411 of `LiveClimbSheet.kt` implement `ClimbProfileCanvas(liveClimb, modifier)`.
- It takes `liveClimb.climb.pathPoints`, calculates:
  ```kotlin
  val startDist = pathPoints.first().distance
  val totalDist = (pathPoints.last().distance - startDist).coerceAtLeast(1.0)
  val minAlt = pathPoints.minOf { it.altitude }
  val maxAlt = pathPoints.maxOf { it.altitude }
  val altSpan = (maxAlt - minAlt).coerceAtLeast(10.0)
  ```
- It manually iterates segments, calculates gradient per segment, draws fill and stroke using slope gradient color bands, and manually calculates rider pin X/Y coordinates using linear interpolation.
- In contrast, `ElevationProfile.kt` already performs:
  - Robust altitude smoothing (`effectiveSigma`), outlier protection (`calculateElevationBounds`), adaptive height calculation (`calculateElevationProfileHeight`), and resolution downsampling.
  - Path construction with segment coloring and alpha fill.
  - Metric distance/altitude axis tick formatting and baseline drawing.
  - Real-time rider marker drawing when `currentDistance` is provided.

### 2.2. Isolated Badges in `LiveClimbSheet.kt`
Lines 215–280 of `LiveClimbSheet.kt` define:
- `ClimbCategoryChip(category: ClimbCategory, modifier: Modifier)`: wraps `Surface(color = bgColor, shape = RoundedCornerShape(6.dp))` and `Text`.
- `ClimbStatusBadge(status: LiveClimbStatus, distanceToStart: Double, distanceFormatter: DistanceFormatter, modifier: Modifier)`: wraps `Surface(color = containerColor, shape = RoundedCornerShape(12.dp))` and `Text`.
- Both replicate the behavior of `MetricBadge.kt` (`Surface(color = containerColor, contentColor = contentColor, shape = ...) { Text(...) }`).

### 2.3. Header & HUD Structure
`SegmentHeader.kt` and `SegmentLiveDetails.kt` establish the canonical two-row header and HUD telemetry patterns for live cockpit sheets:
- Top Row: 32dp Icon + bold title (`MaterialTheme.typography.titleLarge`, `weight(1f)`) + right-aligned metadata badge.
- Subtitle Row: Status badge / subtitle on left + primary-colored counter / label on right (`labelLarge`, bold).
- Standardized divider: 0.5dp `HorizontalDivider` with 50% opacity outlineVariant.
- Telemetry HUD: Left column for distance & elevation progress (`titleLarge` / `bodyLarge` bold); right column for monospace live rate / grade (`headlineMedium` bold, `FontFamily.Monospace`).

---

## 3. Scope Bounding (In-Scope vs. Out-of-Scope)

### In-Scope:
1. **Extend `ElevationProfile.kt` for Slope Gradient Shading**:
   - Add support for slope gradient color shading (`climbGradientShading: Boolean = false`, with optional `segmentColorProvider: ((Double) -> Color)? = null`) to `ElevationProfile.kt`.
   - When enabled, segments are colored according to standard climb grade bands (`<3%` Green, `3-6%` Light Green, `6-9%` Amber, `9-12%` Orange, `12-20%` Red, `>20%` Dark/Black) via shared helper `getClimbGradeColor(gradePercent: Double)`.
   - Add optional `fixedHeight: Dp? = null` parameter to `ElevationProfile` so callers can request a compact fixed profile height if needed.
2. **Harmonize `LiveClimbSheet.kt` Elevation Profile**:
   - Replace private `ClimbProfileCanvas` in `LiveClimbSheet.kt` with a call to the canonical `ElevationProfile` composable passing `pathPoints = climb.pathPoints`, `currentDistance = currentDistance`, and `climbGradientShading = true`.
   - Remove obsolete `ClimbProfileCanvas` code from `LiveClimbSheet.kt`.
3. **Boundary Input Validation & Numerical Safeguards**:
   - Ensure `ElevationProfile` gracefully handles degenerate geometries:
     - `pathPoints.size < 2`: early returns or renders placeholder without throwing exceptions.
     - Flat climbs (`maxAlt == minAlt` / `altSpan == 0.0`): `calculateElevationBounds` enforces `minSpanMeters = 20.0` and range `.coerceAtLeast(1.0)` to eliminate division-by-zero or `NaN` coordinate outputs.
     - Zero distance spans: `totalDist` is clamped with `.coerceAtLeast(1.0)` so X-coordinate projections remain valid floats.
   - Altitude smoothing parameterization: for climbs, allow passing `smoothingSigma = 0.0` (or preserving the climb's raw grade envelope) so that short, steep grade transitions (e.g. 20% ramps on short climbs) are not artificially flattened by route-level smoothing.
4. **Harmonize Badges with `MetricBadge.kt`**:
   - Extend `MetricBadge.kt` with optional `shape: Shape = RoundedCornerShape(4.dp)` to allow custom corner radii without disrupting existing callers.
   - Refactor `ClimbCategoryChip` and `ClimbStatusBadge` in `LiveClimbSheet.kt` to delegate to `MetricBadge`.
5. **Harmonize Header Layout**:
   - Ensure `LiveClimbSheet.kt` header and telemetry HUD strictly follow the structure and token hierarchy of `SegmentHeader.kt` and `SegmentLiveDetails.kt`.
6. **Contract Tests & Previews**:
   - Update `LiveClimbSheetLayoutTest.kt` to verify that `ElevationProfile` is invoked and that `MetricBadge` is utilized.
   - Add test coverage for degenerate geometries (`pathPoints.isEmpty()`, `pathPoints.size == 1`, flat climbs with `altSpan == 0.0`).
   - Maintain multi-state `@Preview` composables (`OnClimb`, `OnClimb_Dark`, `Approaching`, `Finished`) in `LiveClimbSheet.kt`.

### Out-of-Scope:
- Polyline progress engine unification between segments and climbs (this is the specific focus of the companion ticket `ATT-3266`).
- Modifying climb detection thresholds in `ClimbDetector.kt`.
- Modifying route database schema or SQLite tables.

---

## 4. Chesterton's Fence & Requirement Archaeology

### 4.1. Affected Requirements
- `REQ-UI-328` (*Live Climb Cockpit Sheet Visual Design Alignment with Live Segment System*, ATT-2947).
- `REQ-UI-341` (*Live Climb Cockpit Sheet Interactive Visual Design Optimization, Compose Previews & Ground Line Presentation*, ATT-3103).
- `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet*, ATT-1281).

### 4.2. Mandatory Chesterton's Fence Audit
1. **Original Requirement ID & Target**:
   - Refines `REQ-UI-328` and `REQ-UI-341`, targeting `LiveClimbSheet.kt`, `ElevationProfile.kt`, and `MetricBadge.kt`.
2. **Historical Origin & Commit Trace**:
   - `ATT-1281` (Sprint 2026-40.14) created the initial `ClimbProfileCanvas`.
   - `ATT-2947` (Sprint 2026-41.7) aligned the typography with `SegmentHeader`.
   - `ATT-3103` (Sprint 2026-41.8) added `@Preview` tooling and canvas ground line.
3. **Root Reason for Existing Formulation**:
   - `ClimbProfileCanvas` was kept private in `LiveClimbSheet.kt` during initial sprints because `ElevationProfile.kt` did not yet support custom slope gradient color providers. With the maturity of `ElevationProfile.kt`, maintaining two independent canvas drawing engines is tech debt.
4. **Preservation of Core Invariants**:
   - Exact mathematical calculation of distance to summit, remaining elevation gain, and instantaneous gradient is 100% preserved.
   - 100% 9-language localization parity is strictly preserved.
   - Zero regression in `SegmentHeader`, `SegmentLiveDetails`, or existing `ElevationProfile` callers.

---

## 5. Architectural Proposal (SWE.2 High-Level Design)

```
+-------------------------------------------------------------------+
| LiveClimbSheet.kt                                                 |
|                                                                   |
| +---------------------------------------------------------------+ |
| | Top Header Row                                                | |
| | [Terrain Icon 32dp] [Climb Title]         [MetricBadge (Cat)] | |
| +---------------------------------------------------------------+ |
| | Subtitle Row                                                  | |
| | [MetricBadge (Status)]                    [Route Counter]     | |
| +---------------------------------------------------------------+ |
| | HorizontalDivider (0.5dp, outlineVariant copy(0.5f))          | |
| +---------------------------------------------------------------+ |
| | Telemetry HUD (SegmentLiveDetails structure)                  | |
| | Rem. Distance & Gain                      Monospace Grade %   | |
| +---------------------------------------------------------------+ |
| | Shared ElevationProfile.kt                                    | |
| |  - climbGradientShading = true                                | |
| |  - currentDistance = riderDistance                            | |
| |  - showZoomControls = false                                   | |
| |  - Guards: pathPoints.size >= 2 check, altSpan >= 1.0 coerce  | |
| |  - shows real-time rider position & altitude bounds           | |
| +---------------------------------------------------------------+ |
+-------------------------------------------------------------------+
```

### Detailed Boundary & Numerical Invariant Specifications:
1. **Zero / Single Point Path Guard**:
   In `ElevationProfile.kt`:
   ```kotlin
   if (pathPoints.size < 2) {
       // Graceful placeholder or early return preventing invalid segment loops
       return
   }
   ```
2. **Vertical Altitude Span Coercion**:
   `calculateElevationBounds` guarantees:
   ```kotlin
   val range = (max - min).coerceAtLeast(1.0)
   ```
   This strictly prevents division by zero when calculating `y = height - ((sAlt - min) / range * height)`.
3. **Horizontal Distance Span Coercion**:
   ```kotlin
   val totalDist = (pathPoints.last().distance - pathPoints.first().distance).coerceAtLeast(1.0)
   ```
   Ensures `distanceToCanvasX` never encounters zero denominator.
4. **Gradient Shading Provider**:
   `getClimbGradeColor(grade: Double): Color` is extracted as a top-level shared function in `com.atrainingtracker.trainingtracker.ui.climbs`, shared across `LiveClimbSheet.kt`, `ClimbDetailSheet.kt`, and `ElevationProfile.kt`.

---

## 6. Verification Strategy

1. **Static Analysis & Contract Testing**:
   - Update `LiveClimbSheetLayoutTest.kt` to assert that `ElevationProfile` is called instead of `ClimbProfileCanvas`.
   - Verify `ClimbCategoryChip` and `ClimbStatusBadge` compile and render via `MetricBadge`.
   - Add unit tests verifying degenerate geometries:
     - `pathPoints` is empty -> no crash.
     - `pathPoints` has 1 point -> no crash.
     - `pathPoints` has flat altitude (`minAlt == maxAlt`) -> no division-by-zero or `NaN`.
2. **Targeted Unit Tests**:
   - Execute all climb and elevation tests:
     ```bash
     ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.climbs.*" --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfile*"
     ```
3. **Full Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` verifying 0 regressions.
4. **Requirement Governance**:
   - Run `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.10`.
