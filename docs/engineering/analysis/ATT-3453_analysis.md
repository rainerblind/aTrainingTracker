# Stage 1: Problem Domain & Root Cause Analysis - ATT-3453

**Ticket**: [ATT-3453](https://atrainingtracker.atlassian.net/browse/ATT-3453)  
**Summary**: Prevent clipping of climb category marker badges in elevation profile  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-UI-274` (amended)  
**Test Mapping**: `TST-UI-234` / `TST-UI-322`  
**Author**: AI Agent 1 (Analyst)  
**Date**: 2026-10-13  

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
   * `pillHeight = badgePaint.textSize (10.sp) + pillPaddingV * 2 (4.dp)` $\approx 16\text{dp} - 18\text{dp}$.
   * Total badge height above summit is $8\text{dp} + 17\text{dp} \approx 25\text{dp}$.
   When `summitY = 0f`, `pillBottom = -8.dp.toPx()` and `pillTop = -25.dp.toPx()`. The entire badge is drawn in negative Y space relative to the Canvas.

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
   * When `pillTop = -25.dp` in Canvas coordinates, its position in root `Box` coordinates is:
     $$y_{\text{box}} = \text{effectiveTopPadding} + \text{pillTop} = 16\text{dp} - 25\text{dp} = -9\text{dp}$$
   * Because $y_{\text{box}} < 0$, the top 9–10 dp of the badge extends outside the `ElevationProfile` composable bounds.
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
  `16.dp` was chosen in `REQ-UI-225` for compact list previews when no zoom toolbar was present. At that time, test routes with climbs were tested in `MapDetailLayout` where `showZoomControls == true` (`topPadding = 44.dp`), where 44 dp absorbed the 25 dp badge. In live tracking (`SensorGridScreen.kt`), `showZoomControls == false`, leaving only 16 dp of padding.
* **Why did `pillLeft` have clamping but not `pillTop`?**  
  The author implemented horizontal clamping `coerceIn(0f, width - pillWidth)` to prevent horizontal clipping when a climb starts at 0 km or ends at the route finish, but assumed `effectiveTopPadding` would always accommodate vertical extension.
* **What invariants must be preserved?**  
  1. Slope gradient curve coloring (`Zone1`..`Zone5`) per `REQ-UI-299`.
  2. Horizontal X-axis climb span bars per `REQ-UI-299` and `REQ-UI-307`.
  3. Literal compatibility with `ElevationProfileLayoutTest.kt` (`val topPadding = if (showZoomControls) 44.dp else 16.dp` and `totalCanvasHeight` contract checks).
  4. Contract check compatibility with `ElevationProfileClimbSpanContractTest.kt` (`canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)` and `drawRoundRect`).
  5. 100% clean-room test pass rate across all 2573+ unit tests.

---

## 3. Scope & Target Architecture

### 3.1 In-Scope Deliverables
1. **Dynamic Top Clearance Allocation (`ElevationProfile.kt`)**:
   * Detect when climb badges are active: `val hasClimbBadges = !isTimeDomain && climbs.isNotEmpty()`.
   * Allocate dedicated climb badge headroom: `val climbBadgeTopPadding = if (hasClimbBadges) 18.dp else 0.dp`.
   * Incorporate `climbBadgeTopPadding` into `effectiveTopPadding` and `totalCanvasHeight`:
     ```kotlin
     val topPadding = if (showZoomControls) 44.dp else 16.dp
     val climbBadgeTopPadding = if (!isTimeDomain && climbs.isNotEmpty()) 18.dp else 0.dp
     val effectiveTopPadding = (if (fixedHeight != null) 8.dp else topPadding) + climbBadgeTopPadding
     val bottomPadding = if (fixedHeight != null) 18.dp else 24.dp
     val totalCanvasHeight = (fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight) + climbBadgeTopPadding
     ```
   * This provides $16\text{dp} + 18\text{dp} = 34\text{dp}$ of top clearance in standard views, leaving $\sim 9\text{dp}$ of breathing room above the badge.
   * Canvas plotting height is exactly preserved ($(\text{totalHeight} + 18) - (\text{topPadding} + 18) - \text{bottomPadding} = \text{height}$), ensuring zero distortion of the elevation curve.

2. **Vertical & Horizontal Boundary Protection (`ElevationProfile.kt`)**:
   * Add a top boundary ceiling constraint so that `pillTop` never exceeds the container's top boundary:
     ```kotlin
     val minAllowedPillTop = -effectiveTopPadding.toPx() + 2.dp.toPx()
     val clampedPillTop = maxOf(pillTop, minAllowedPillTop)
     val clampedPillBottom = clampedPillTop + pillHeight
     ```
   * Add a horizontal margin safety inset (`2.dp.toPx()`):
     ```kotlin
     val minMarginPx = 2.dp.toPx()
     val pillLeft = (summitX - pillWidth / 2f).coerceIn(minMarginPx, (width - pillWidth - minMarginPx).coerceAtLeast(minMarginPx))
     val pillRight = pillLeft + pillWidth
     ```
   * Ensure the pin stem connects from `summitY` to `clampedPillBottom`:
     ```kotlin
     val stemBottom = minOf(summitY, clampedPillBottom)
     val stemTop = clampedPillBottom
     if (stemBottom > stemTop) {
         canvas.nativeCanvas.drawLine(summitX, stemBottom, summitX, stemTop, stemPaint)
     }
     ```

3. **Contract Test & Architectural Verification**:
   * Author `ElevationProfileClimbBadgeClippingTest.kt` verifying:
     - Top clearance expansion when `climbs.isNotEmpty()`.
     - Zero clearance expansion when `climbs.isEmpty()`.
     - Clamping of `pillTop` against negative container boundary.
     - Preservation of existing layout contracts in `ElevationProfileLayoutTest.kt` and `ElevationProfileClimbSpanContractTest.kt`.

### 3.2 Out-of-Scope (Non-Goals)
* Modifying climb detection algorithms in `ClimbDetector.kt`.
* Altering climb classification thresholds or color palettes in `ClimbCategory.kt` / `ClimbCategoryColors.kt`.
* Modifying map polyline highlighting or live climb bottom sheets.

---

## 4. ASPICE Governance & Gate 1 Criteria

| Gate Requirement | Status / Evidence |
|---|---|
| Forensic Root Cause Identified | Yes — mathematical proof of $y_{\text{box}} < 0$ causing Surface clipping |
| Chesterton's Fence Audit Completed | Yes — historical context from ATT-2388 & ATT-2386 analyzed |
| Invariants Documented | Yes — curve gradient coloring, X-axis span bars, zoom layout contracts |
| Scope Bounded | Yes — targeted exclusively to `ElevationProfile.kt` clearance & boundary guard |
| Target Test Strategy Defined | Yes — unit contract tests in `ElevationProfileClimbBadgeClippingTest.kt` |
