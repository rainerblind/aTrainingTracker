# Stage 1 Analysis: ATT-1819 - Fix unreadable x-Axis milestone collisions and text overlapping on HR and Pace graphs

**Ticket**: [ATT-1819](https://rainerblind.atlassian.net/browse/ATT-1819)  
**Sub-task**: [ATT-1885](https://rainerblind.atlassian.net/browse/ATT-1885) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Branch**: `feature/ATT-1819`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During post-workout inspection on the Aftermath screen (`MapDetailLayout.kt`, `TrackOnMapScreen.kt`), athletes can review continuous telemetry graphs (Speed/Pace, Heart Rate, and Power) alongside the route track and elevation profile.

On workouts with moderate to long distances (such as the 17.82 km run *"Mit Jonas durch den Wald"*, see attachment `screenshot_pace_scrubbing_and_xaxis.png`), the X-axis distance milestone markers on secondary continuous graphs (Heart Rate and Speed/Pace) suffer from severe visual collisions:
* **Solid Text Bleed**: Every kilometer marker from `0 km` to `17 km` is drawn unconditionally without minimum pixel spacing collision checks:
  `0 km1 km2 km3 km4 km5 km6 km7 km8 km9 km10 km11 km12 km13 km14 km15 km16 km17 km`
* **Lack of Boundary Anchoring**: Unlike the `ElevationProfile` chart above it—which anchors an explicit end label (`17,82 km`) and renders compact, uncluttered numbers (`2`, `3`, ..., `14`) while suppressing edge collisions—the secondary continuous graphs lack start and end boundary labels, drawing `0 km` directly over the Y-axis origin line and bleeding markers into the right margin.
* **Redundant Unit Suffixes**: Every intermediate tick redundantly renders `" km"` (or `" mi"`), multiplying the required horizontal space per label by a factor of 3 to 4, making collisions inevitable on standard smartphone screens (~360–400 dp wide).

The expected behavior is that all continuous telemetry graphs employ adaptive milestone decimation, minimum pixel clearance checks, and standardized boundary labeling aligned with `ElevationProfile`, ensuring zero overlapping text under any workout distance, screen width, or zoom level.

---

## 2. Root Cause Analysis (Forensic Investigation)

A forensic comparison between `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `ElevationProfileZoomMath.kt` reveals five interacting root causes:

### 2.1 Lack of a 2 km Adaptive Distance Step in `ElevationProfileZoomMath`
In `ElevationProfileZoomMath.kt`:
```kotlin
fun calculateAdaptiveDistanceStep(visibleDist: Double, unit: MyUnits): Float {
    return if (unit == MyUnits.METRIC) {
        when {
            visibleDist > 50_000 -> 10_000f
            visibleDist > 20_000 -> 5_000f
            visibleDist > 5_000 -> 1_000f
            visibleDist > 1_500 -> 500f
            visibleDist > 500 -> 100f
            else -> 50f
        }
    } ...
```
For a 17.82 km workout ($W_{\text{vis}} = 17,820\text{ m}$), `visibleDist > 5_000` evaluates to true, assigning `distStep = 1000f`. This produces 17 intermediate ticks!
On a mobile display with available chart width $W_{\text{chart}} \approx 300\text{ dp}$ (screen width minus 50 dp start and 25 dp end paddings), 17 ticks leave only:
$$\frac{300\text{ dp}}{17} \approx 17.6\text{ dp per tick}$$
In contrast, Imperial units already implement a 2-mile step (`visibleMiles > 10 -> 2f`). Metric units abruptly drop from 5,000 m directly to 1,000 m without an intermediate 2,000 m step (`visibleDist > 10_000 -> 2_000f`).

### 2.2 Redundant Unit Suffix on Intermediate Milestones
In `TelemetryMetricGraph.kt` (lines 420–432):
```kotlin
val label = if (unit == MyUnits.METRIC) {
    if (visibleSpan < 1500) {
        "${currentDist.toInt()} m"
    } else if (currentDist % 1000.0 != 0.0) {
        String.format(Locale.US, "%.1f km", currentDist / 1000.0)
    } else {
        "${(currentDist / 1000.0).toInt()} km"
    }
} else {
    val miles = currentDist / BANALService.METER_PER_MILE
    String.format(Locale.US, "%.1f mi", miles)
}
nativeCanvas.drawText(label, tickX - 20f, size.height - 4.dp.toPx(), axisTextPaint)
```
Each milestone label repeats `" km"` or `" mi"`, expanding its rendered width to ~35–45 dp (~100–125 px). Since tick separation is only ~17 dp (~50 px), every label overlaps the subsequent label by ~20–25 dp, creating a continuous text mash.
By contrast, `ElevationProfile.kt` formats intermediate ticks simply as `"${(currentD / 1000.0).toInt()}"` (`"1"`, `"2"`, `"3"`), since the unit is clearly established by the chart boundary label.

### 2.3 Absence of Adjacent Tick Collision Clearance / Decimation
`TelemetryMetricGraph.kt` unconditionally loops across all ticks within `[startDist, startDist + visibleSpan]` and draws each label without tracking the position of the previously drawn label (`lastDrawnRightX`). If two adjacent labels are closer than a readable threshold (e.g. text width + 12 dp safety margin), neither is suppressed or decimated.

### 2.4 Missing Chart Boundary Labels & Edge Collisions
* `ElevationProfile.kt` anchors an explicit `endLabel` with units (e.g. `"17,82 km"`) at the right boundary (`width - endLabelWidth`) and, when zoomed ($Z > 1.01$), an explicit `startLabel` at the left boundary (`0f`).
* `ElevationProfile.kt` prevents intermediate ticks from colliding with the boundaries by checking:
  ```kotlin
  if (x > 60f && (width - x) > (endLabelWidth + 50f))
  ```
* `TelemetryMetricGraph.kt` lacks boundary labels entirely and lacks start/end margin suppression, causing `0 km` to collide directly with the Y-axis tick mark and rightward ticks to draw right up to or past the chart edge.

### 2.5 Text Centering Asymmetry
`TelemetryMetricGraph.kt` renders labels at `tickX - 20f`, an arbitrary fixed offset that assumes labels are exactly 40 px wide. For varying label widths, this results in off-center, right-skewed text placement. Proper centering requires `tickX - (textWidth / 2f)`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Refine `ElevationProfileZoomMath.calculateAdaptiveDistanceStep`**:
     Add an intermediate `visibleDist > 10_000 -> 2_000f` step in Metric mode, ensuring workouts between 10 km and 20 km generate ~5–8 milestones rather than 10–19 milestones.
  2. **Harmonize Milestone Label Formatting (`TelemetryMetricGraph.kt`)**:
     Format intermediate milestone labels cleanly without repeating `" km"` or `" mi"` on every tick (e.g. `"2"`, `"4"`, `"6"`), matching `ElevationProfile.kt`.
  3. **Add Boundary Start and End Labels (`TelemetryMetricGraph.kt`)**:
     Render the total/visible end distance formatted with units (via `DistanceFormatter` or `formatTimeTick`) anchored at `chartWidthPx - endLabelWidth`, and render the start label when zoomed ($Z > 1.01$), achieving 100% visual parity with `ElevationProfile.kt`.
  4. **Implement Minimum Pixel Clearance & Decimation (`TelemetryMetricGraph.kt`)**:
     Enforce edge clearance margins (suppressing ticks within 40 dp of start or within `endLabelWidth + 16 dp` of end) and enforce a minimum horizontal spacing between adjacent labels (at least 32 dp / `lastLabelRight + minSpacing`), decimating overlapping labels while retaining underlying grid lines.
  5. **Centering Alignment**:
     Center-align milestone text labels precisely under the tick grid lines using `tickX - (measuredWidth / 2f)`.
  6. **Apply to Both Distance and Time Domains**:
     Ensure collision clearance and boundary labeling function identically in both Distance (`ProfileXAxisDomain.DISTANCE`) and Time (`ProfileXAxisDomain.TIME`) domains.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying the scrubbing telemetry cards (`ScrubbingTelemetryBadge` or `ScrubbingInfoCard`) or pace decoding (completed in ATT-1818).
  * Modifying Y-axis scale bounds, clamping, or `mm:ss` formatting (completed in ATT-1818).
  * Modifying horizontal pinch zoom or global zoom synchronization (completed in ATT-1814).
  * Modifying graph ordering or visual placement in `MapDetailLayout.kt` (completed in ATT-1813).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-206` (*Aftermath: Continuous Telemetry Graph Suite for Heart Rate, Speed/Pace, and Power*), targeting `TelemetryMetricGraph.kt`.
* **Historical Origin & Commit Trace**: Commit for `ATT-1740` (Sprint `2026-40.6`), refined in `ATT-1813`, `ATT-1814`, and `ATT-1818`.
* **Root Reason for Existing Formulation**: When continuous telemetry graphs were initially constructed in ATT-1740, the primary focus was on continuous curve rendering, dynamic Y-axis scaling, and synchronized scrubbing touch interactions. The X-axis tick loop was implemented as a basic iteration over `ElevationProfileZoomMath.calculateAdaptiveDistanceStep`. Because initial test workouts were short (< 5 km), the tick collision issue at longer distances (e.g. 17.82 km) was not apparent until multi-sport testing on long runs.
* **Preservation of Core Invariants**:
  * Shared horizontal paddings (50 dp start, 25 dp end) are strictly preserved, maintaining pixel-perfect vertical alignment with `ElevationProfile`.
  * Single-source viewport mathematics (`ElevationProfileZoomMath`) is preserved and enhanced.
  * Synchronized scrubbing touch mapping (`canvasXToDistance`) and cursor positioning are completely untouched.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Refine `calculateAdaptiveDistanceStep` in `ElevationProfileZoomMath.kt`
```kotlin
fun calculateAdaptiveDistanceStep(visibleDist: Double, unit: MyUnits): Float {
    return if (unit == MyUnits.METRIC) {
        when {
            visibleDist > 50_000 -> 10_000f
            visibleDist > 20_000 -> 5_000f
            visibleDist > 10_000 -> 2_000f  // Refinement: 2km step for 10-20km workouts
            visibleDist > 5_000 -> 1_000f
            visibleDist > 1_500 -> 500f
            visibleDist > 500 -> 100f
            else -> 50f
        }
    } else {
        // Imperial steps already include 2mi step: > 30 -> 5f, > 10 -> 2f, > 3 -> 1f
        ...
    }
}
```

### 5.2 Standardize Boundary & Intermediate Label Rendering in `TelemetryMetricGraph.kt`
1. **Initialize `DistanceFormatter`**:
   `val distanceFormatter = remember(unit) { DistanceFormatter() }`
2. **Render Boundary Labels**:
   - Compute `endLabel`:
     - Distance domain: `distanceFormatter.format_with_units(startDist + visibleSpan)` (e.g. `"17,82 km"`).
     - Time domain: `ElevationProfileZoomMath.formatTimeTick((startDist + visibleSpan).toLong())`.
   - Draw `endLabel` at `startPaddingPx + chartWidthPx - endLabelWidth`.
   - When zoomed ($Z > 1.01$), compute `startLabel` (`distanceFormatter.format_with_units(startDist)` or `formatTimeTick`) and draw at `startPaddingPx`.
3. **Render Intermediate Milestones with Decimation**:
   - Format milestone labels without repeating unit:
     - Metric: if `visibleSpan < 1500`: `"${currentDist.toInt()}m"`; else if decimal: `"%.1f"`; else `"${(currentDist / 1000.0).toInt()}"`.
     - Imperial: if decimal: `"%.1f"`; else `"${miles.toInt()}"`.
   - Measure text width: `val labelWidth = axisTextPaint.measureText(label)`.
   - Check edge clearance:
     `tickX > startPaddingPx + 40.dp.toPx() && (startPaddingPx + chartWidthPx - tickX) > (endLabelWidth + 16.dp.toPx())`.
   - Check adjacent label collision clearance:
     `val labelLeft = tickX - (labelWidth / 2f)`.
     `if (labelLeft >= lastDrawnRight + 12.dp.toPx())` -> draw text and update `lastDrawnRight = labelLeft + labelWidth`.
   - Draw vertical grid line across all ticks for visual structure, but suppress text labels that collide.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Horizontal padding contract (50 dp start, 25 dp end) MUST NOT change.
  2. Cursor line positioning and touch scrubbing calculations MUST NOT be altered.
  3. Viewport math invertibility in `ElevationProfileZoomMathTest` MUST remain 100% passing.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**.
  The changes are purely presentational logic in X-axis label positioning and a minor step refinement in `ElevationProfileZoomMath`. No database schemas, sensor pipelines, or state hoisting architectures are affected.
