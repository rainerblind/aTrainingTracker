# Stage 1: Problem Domain & Root Cause Analysis - ATT-3453 (Revised)

**Ticket**: [ATT-3453](https://atrainingtracker.atlassian.net/browse/ATT-3453)  
**Summary**: Prevent clipping of climb category marker badges in elevation profile  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-UI-274` (amended)  
**Test Mapping**: `TST-UI-234` / `TST-UI-322`  
**Author**: AI Agent 1 (Analyst)  
**Date**: 2026-10-13 (Revised per Gate 1 Audit)  

---

## 1. Problem Domain & Defect Analysis

### 1.1 Observed Defect
During Sprint 2026-42.2 testing on a physical Pixel 10 (as evidenced in attached screenshot `docs/attachments/ATT-3343_review_evidence.png`), climb category marker badges (e.g. "UC" / Uncategorized Climb, "Cat 4", "Cat 3", etc.) rendered on the elevation profile canvas are partially clipped/cut off at the top boundary.

In the recorded review evidence:
* A route featuring a climb peaking at 477 m (the route's maximum elevation) renders its summit category marker badge ("UC") directly above the peak.
* The badge pill is sliced in half horizontally: only the bottom 6–8 dp of the badge is visible, while the top half (and the upper text of "UC") is clipped by the top edge of the elevation profile card/surface.

### 1.2 Forensic Root Cause Analysis (RCA)

1. **Elevation Normalization & Summit Coordinate Mapping**:
   In `ElevationProfile.kt`, the elevation profile curve maps physical altitude to canvas coordinates using:
   ```kotlin
   val summitY = height - (((summitPt.altitude - cachedData.minAlt) / cachedData.altRange).toFloat() * height)
   ```
   When a climb's summit reaches or nears the route's maximum altitude (`cachedData.maxAlt`), `(summitPt.altitude - minAlt) / altRange` evaluates to `1.0f`. Consequently:
   ```kotlin
   summitY = height - 1.0f * height = 0f
   ```
   The summit point is plotted at `y = 0f`—the absolute top edge of the `Canvas` viewport.

2. **Negative Coordinate Offset of Summit Badges**:
   The category badge pill and pin stem are anchored above `summitY` (lines 746–747):
   ```kotlin
   val pillBottom = summitY - 8.dp.toPx()
   val pillTop = pillBottom - pillHeight
   ```
   Where:
   * The stem height is `8.dp`.
   * `pillHeight = badgePaint.textSize (10.sp) + pillPaddingV * 2 (4.dp)`.
   * At default font scale (1.0x), `pillHeight` is $\approx 14\text{dp}$.
   * At 1.5x font scale, `pillHeight` is $\approx 19\text{dp}$.
   * Total badge height above summit is $8\text{dp} + 14\text{dp} = 22\text{dp}$ (up to $27\text{dp} - 32\text{dp}$ under accessibility font scaling).
   When `summitY = 0f`, `pillBottom = -8.dp.toPx()` and `pillTop = -22.dp.toPx()`. The entire badge is drawn in negative Y space relative to the Canvas.

3. **Insufficient Canvas Top Clearance & Container Boundary Clipping**:
   In `ElevationProfile.kt` lines 427–430:
   ```kotlin
   val topPadding = if (showZoomControls) 44.dp else 16.dp
   val effectiveTopPadding = if (fixedHeight != null) 8.dp else topPadding
   val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight
   ```
   The Canvas is embedded within a root `Box` of height `totalCanvasHeight` with:
   ```kotlin
   Canvas(
       modifier = canvasModifier
           .padding(bottom = bottomPadding, start = 50.dp, end = 25.dp, top = effectiveTopPadding)
   )
   ```
   When `showZoomControls == false` (e.g. in live tracking HUD `SensorGridScreen.kt` or compact route previews):
   * `effectiveTopPadding = 16.dp`.
   * The top of the Canvas content is 16 dp below the top of the root `Box`.
   * When `pillTop = -22.dp` to `-25.dp` in Canvas coordinates, its position in root `Box` coordinates is:
     $$y_{\text{box}} = \text{effectiveTopPadding} + \text{pillTop} = 16\text{dp} - 25\text{dp} = -9\text{dp}$$
   * Because $y_{\text{box}} < 0$, the top 6–10 dp of the badge extends outside the `ElevationProfile` composable bounds.
   * In `SensorGridScreen.kt`, `ElevationProfile` is wrapped in `Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))`. Material 3 `Surface` clips its children to its layout bounds. Any content with $y < 0$ is clipped!
   * Furthermore, if `fixedHeight != null` (e.g. in compact route cards), `effectiveTopPadding = 8.dp`, resulting in $y_{\text{box}} = 8\text{dp} - 25\text{dp} = -17\text{dp}$ (almost the entire badge clipped).

4. **Absence of Vertical Boundary Clamping**:
   While `pillLeft` was explicitly guarded against horizontal clipping (`(summitX - pillWidth / 2f).coerceIn(0f, width - pillWidth)`), `pillTop` had no vertical boundary clamping, leaving it vulnerable to container clipping whenever summits peak near the maximum profile altitude.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

### 2.1 Historical Context & Commit Trace
1. **Sprint 2026-40.14 (ATT-1281 / REQ-MAP-027)**:
   * Introduced `ClimbDetector`, `ClimbsDatabaseManager`, and `LiveClimbSheet.kt`.
2. **Sprint 2026-41.1 (ATT-2388 / REQ-UI-274)**:
   * Added climb visualization across routes and the elevation profile canvas, introducing summit category badges (`// Render Summit Category Badges (REQ-UI-274)`).
   * Badges were anchored above summits with a fixed `8.dp` stem and pill offset.
3. **Sprint 2026-41.1 (ATT-2386 / REQ-UI-225)**:
   * Decoupled zoom buttons from `ElevationProfile.kt` to `GlobalTelemetryZoomToolbar.kt`.
   * Optimized top padding: `val topPadding = if (showZoomControls) 44.dp else 16.dp`.
   * Added `ElevationProfileLayoutTest.kt` verifying non-overlapping bounds.
4. **Sprint 2026-41.3 (ATT-2510 / REQ-UI-299)**:
   * Preserved summit badges while moving climb span indicators to horizontal bars along the X-axis baseline.

### 2.2 Chesterton's Fence Analysis
* **Why was `topPadding` set to 16.dp when `showZoomControls == false`?**  
  `16.dp` was chosen in `REQ-UI-225` for compact list previews when no zoom toolbar was present. In `MapDetailLayout` where `showZoomControls == true` (`topPadding = 44.dp`), 44 dp easily absorbed the 22–25 dp badge. In live tracking (`SensorGridScreen.kt`), `showZoomControls == false`, leaving only 16 dp of padding.
* **Why did `pillLeft` have clamping but not `pillTop`?**  
  The author implemented horizontal clamping `coerceIn(0f, width - pillWidth)` to prevent horizontal clipping when a climb starts at 0 km or ends at the route finish, but assumed `effectiveTopPadding` would always accommodate vertical extension.
* **What invariants must be preserved?**  
  1. Slope gradient curve coloring (`Zone1`..`Zone5`) per `REQ-UI-299`.
  2. Horizontal X-axis climb span bars per `REQ-UI-299` and `REQ-UI-307`.
  3. Exact source code literal compatibility with `ElevationProfileLayoutTest.kt`:
     * Line 59: `val topPadding = if (showZoomControls) 44.dp else 16.dp`
     * Line 65: `val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight`
  4. Contract check compatibility with `ElevationProfileClimbSpanContractTest.kt`:
     * Line 146: `canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)`
     * Line 150: `canvas.nativeCanvas.drawRoundRect`
  5. 100% clean-room test pass rate across all 2573+ unit tests.

---

## 3. Scope & Target Architecture (Revised)

### 3.1 Refactored Top Clearance & Layout Strategy (Addressing Gate 1 Feedback)

#### A. Dynamic Font-Scale Aware Clearance (Eliminating Magic Numbers)
Instead of a static "magic number" (e.g. 18.dp), the required top clearance is calculated dynamically using Compose text density metrics:
```kotlin
val climbBadgeTextHeight = with(LocalDensity.current) { 10.sp.toDp() }
val climbBadgePillHeight = climbBadgeTextHeight + 4.dp // 2.dp top + 2.dp bottom internal padding
val climbBadgeStemHeight = 8.dp
val climbBadgeSafetyMargin = 2.dp
val requiredClimbTopClearance = climbBadgePillHeight + climbBadgeStemHeight + climbBadgeSafetyMargin
```
* **At standard FontScale (1.0x)**: `10.sp.toDp() = 10.dp`, `pillHeight = 14.dp`, `requiredClimbTopClearance = 24.dp`.
* **At Accessibility FontScale (1.5x)**: `10.sp.toDp() = 15.dp`, `pillHeight = 19.dp`, `requiredClimbTopClearance = 29.dp`.
* **At Accessibility FontScale (2.0x)**: `10.sp.toDp() = 20.dp`, `pillHeight = 24.dp`, `requiredClimbTopClearance = 34.dp`.

The dynamic padding addition is then computed relative to the base top padding:
```kotlin
val topPadding = if (showZoomControls) 44.dp else 16.dp
val baseTopPadding = if (fixedHeight != null) 8.dp else topPadding
val climbBadgeTopPadding = if (!isTimeDomain && climbs.isNotEmpty()) {
    (requiredClimbTopClearance - baseTopPadding).coerceAtLeast(0.dp)
} else {
    0.dp
}
val effectiveTopPadding = baseTopPadding + climbBadgeTopPadding
val bottomPadding = if (fixedHeight != null) 18.dp else 24.dp
val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight
```

#### B. Clarification of Layout Impact: Container Stability vs Chart Rescaling
Agent 2 rightly scrutinized whether the parent container expands or the chart scales down:
1. **Dynamic Container (`fixedHeight == null`)**:
   * `totalCanvasHeight` is intentionally **kept stable** (`if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight`).
   * **Architectural Rationale**: Expanding the parent container dynamically when climbs appear would cause "jumping" cards and layout instability in `SensorGridScreen.kt` and list views. Furthermore, keeping `val totalCanvasHeight = ...` identical preserves the exact contract in `ElevationProfileLayoutTest.kt` line 65.
   * **Chart Rescaling**: The Canvas height is `height = size.height = totalCanvasHeight - effectiveTopPadding - bottomPadding`.
   * When `effectiveTopPadding` increases by 8 dp (from 16 dp to 24 dp at 1.0x font scale), `height` decreases by 8 dp (e.g. from 110 dp to 102 dp). Because altitude normalization maps `y = height - (altNorm * height)`, the elevation polyline seamlessly and proportionally scales down by ~7%, allocating the necessary top headroom for the badge without changing the Composable's external dimensions or causing layout jumps.
2. **Fixed Height Container (`fixedHeight != null`)**:
   * The container height is strictly clamped to `fixedHeight` (e.g. 120 dp).
   * `baseTopPadding = 8.dp`. With climbs present, `effectiveTopPadding = 24.dp` (at 1.0x).
   * The chart plotting area `height = fixedHeight - 24.dp - 18.dp = fixedHeight - 42.dp` rescales the elevation polyline down.
   * The badge fits entirely within the 24 dp top padding area, ensuring zero overlap with the parent container boundaries and zero overflow.

#### C. Two-Layer Boundary Protection (Vertical Ceiling & Horizontal Margins)
Even with dynamic padding, extreme accessibility font scaling (e.g. 3.0x) or unusually small `fixedHeight` values could theoretically threaten boundary limits. Therefore, a strict bounding ceiling is enforced inside the Canvas drawing loop:
```kotlin
val rawPillBottom = summitY - 8.dp.toPx()
val rawPillTop = rawPillBottom - pillHeight
val minAllowedPillTop = -effectiveTopPadding.toPx() + 2.dp.toPx()
val pillTop = maxOf(rawPillTop, minAllowedPillTop)
val pillBottom = pillTop + pillHeight

val minMarginPx = 2.dp.toPx()
val pillLeft = (summitX - pillWidth / 2f).coerceIn(minMarginPx, (width - pillWidth - minMarginPx).coerceAtLeast(minMarginPx))
val pillRight = pillLeft + pillWidth
```
And the pin stem is connected preserving the exact contract line:
```kotlin
canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)
```
Because `pillBottom` is positioned at or above `summitY` (with `minAllowedPillTop` ensuring the pill stays within container bounds), the pin stem cleanly connects the summit coordinate to the bottom of the category badge.

---

## 4. Test Strategy & Contract Safeguards

### 4.1 Regression & Invariant Safeguards
* **`ElevationProfileLayoutTest.kt`**:
  * Line 59: `val topPadding = if (showZoomControls) 44.dp else 16.dp` is preserved verbatim.
  * Line 65: `val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight` is preserved verbatim.
* **`ElevationProfileClimbSpanContractTest.kt`**:
  * Line 146: `canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)` is preserved verbatim.
  * Line 150: `canvas.nativeCanvas.drawRoundRect` is preserved verbatim.

### 4.2 Targeted Unit Test Suite (`ElevationProfileClimbBadgeClippingTest.kt`)
The new test suite will cover the following scenarios:
1. `testClimbBadge_dynamicTopClearance_allocatesRequiredPadding()`: Verifies that when `climbs.isNotEmpty()`, `effectiveTopPadding` expands dynamically to accommodate font-scaled badge height + stem + safety margin.
2. `testClimbBadge_zeroClimbBadgeTopPadding_whenClimbsEmpty()`: Verifies that when `climbs.isEmpty()`, `climbBadgeTopPadding == 0.dp`, preserving baseline preview padding.
3. `testClimbBadge_detailViewWithZoomControls_noExtraPaddingNeeded()`: Verifies that when `showZoomControls == true` (`topPadding = 44.dp`), `climbBadgeTopPadding == 0.dp` since 44 dp is already $\ge$ required clearance.
4. `testClimbBadge_fixedHeight_rescalesChartWithoutContainerOverflow()`: Verifies that when `fixedHeight != null`, the container height remains constant while the chart area rescales and the badge fits within bounds.
5. `testClimbBadge_accessibilityFontScale_expandsClearance()`: Simulates font scales 1.0x, 1.5x, and 2.0x, verifying clearance $\ge 24\text{dp}, 29\text{dp}, 34\text{dp}$ respectively.
6. `testClimbBadge_boundaryCeiling_preventsNegativeBoxCoordinates()`: Verifies that even when `summitY = 0f`, the computed badge top in Composable coordinates satisfies $y_{\text{box}} \ge 2\text{dp}$.
7. `testClimbBadge_horizontalMarginInset_preventsEdgeClipping()`: Verifies `minMarginPx` horizontal boundary clamping at $X = 0$ and $X = \text{width}$.

---

## 5. ASPICE Governance & Gate 1 Checklist

| Gate Requirement | Status / Evidence |
|---|---|
| Forensic Root Cause Identified | Yes — mathematical proof of $y_{\text{box}} < 0$ causing Surface clipping |
| Chesterton's Fence Audit Completed | Yes — historical context from ATT-2388 & ATT-2386 analyzed |
| Invariants Documented | Yes — curve gradient coloring, X-axis span bars, zoom layout contracts |
| Dynamic Font-Scaling Addressed | Yes — `10.sp.toDp()` + dynamic density calculation replacing 18dp magic number |
| Layout Impact Clarified | Yes — stable container height, chart area rescaling proportionally |
| Preserved Layout Contracts Verified | Yes — verified against `ElevationProfileLayoutTest.kt` & `ElevationProfileClimbSpanContractTest.kt` |
| Scope Bounded | Yes — targeted exclusively to `ElevationProfile.kt` clearance & boundary guard |
| Target Test Strategy Defined | Yes — comprehensive matrix in `ElevationProfileClimbBadgeClippingTest.kt` |
