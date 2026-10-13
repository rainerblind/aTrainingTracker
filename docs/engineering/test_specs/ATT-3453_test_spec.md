# Stage 2: Requirement & Test Specification - ATT-3453: Prevent clipping of climb category marker badges in elevation profile

**Ticket**: [ATT-3453](https://atrainingtracker.atlassian.net/browse/ATT-3453)  
**Sub-task**: [ATT-3474](https://atrainingtracker.atlassian.net/browse/ATT-3474) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-UI-274` (*Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown*), amended  
**Test Spec ID**: `TST-UI-322`  
**Branch**: `feature/ATT-3453`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Requirement Specification (Amending REQ-UI-274)

### 1.1 Problem Statement & Rationale
In `ElevationProfile.kt`, climb category marker badges (e.g. "UC", "Cat 4", "Cat 3", "Cat 2", "Cat 1", "HC") are rendered above climb summit peaks to provide instant topographic classification. When a climb peaks at or near the route's maximum altitude (`cachedData.maxAlt`), elevation normalization maps the summit point to $Y = 0\text{f}$ (the top edge of the Canvas viewport).

Because the marker badge pill and pin stem are anchored above the summit point (`pillBottom = summitY - 8.dp.toPx()`, `pillTop = pillBottom - pillHeight`), the entire badge is rendered in negative coordinate space relative to the Canvas ($-22\text{dp}$ to $-34\text{dp}$ depending on system font scale). In compact list previews, live tracking HUD (`SensorGridScreen.kt`), or route cards, the base top padding (`topPadding = 16.dp` or `fixedHeight != null` where `effectiveTopPadding = 8.dp`) is insufficient to contain the negative Y offset. Consequently, the badge extends into negative Composable coordinates ($y_{\text{box}} < 0$). Material 3 container surfaces (such as `Surface` or `Card` in `SensorGridScreen.kt`) apply layout boundary clipping, slicing off the top half of the badge.

ATT-3453 resolves this defect by:
1. **Dynamic Font-Scale Aware Clearance**: Dynamically calculating required top clearance based on Compose font density metrics (`10.sp.toDp()`), badge vertical padding, pin stem length, and safety margin, dynamically expanding `effectiveTopPadding` without fragile hard-coded magic numbers.
2. **Container Height Stability & Chart Area Rescaling**: Maintaining container stability (`totalCanvasHeight` unchanged when `fixedHeight == null` and clamped when `fixedHeight != null`), smoothly rescaling the elevation polyline down inside the remaining Canvas height ($\sim 7\%$ delta) to eliminate jumping layout bounds and strictly preserve existing layout contracts.
3. **Two-Layer Boundary Protection**: Enforcing a strict bounding ceiling (`minAllowedPillTop = -effectiveTopPadding.toPx() + 2.dp.toPx()`) guaranteeing $y_{\text{box}} \ge 2\text{dp}$ even under extreme accessibility font scales (up to 3.0x) or tight fixed heights, alongside horizontal safety margin insets (`minMarginPx = 2.dp.toPx()`).
4. **Preserving Layout & Contract Invariants**: Strictly preserving the exact source contract strings expected by `ElevationProfileLayoutTest.kt` (lines 59, 65) and `ElevationProfileClimbSpanContractTest.kt` (lines 146, 150).

---

### 1.2 Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-274` (*Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown*), extending `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet*) and `REQ-UI-225` (*Decoupled Global Telemetry Zoom Toolbar & ElevationProfile Geometry*).
* **Historical Origin & Commit Trace**:
  - `REQ-MAP-027`: Sprint 2026-40.14 (`ATT-1281`, commit `d5c90d81`) introduced `ClimbDetector`, `ClimbsDatabaseManager`, and `LiveClimbSheet.kt`.
  - `REQ-UI-274`: Sprint 2026-41.1 (`ATT-2388`, commit `7e8a912f`) introduced summit category badges on the elevation profile canvas.
  - `REQ-UI-225`: Sprint 2026-41.1 (`ATT-2386`, commit `b24a10d8`) decoupled zoom controls and introduced `val topPadding = if (showZoomControls) 44.dp else 16.dp` with `ElevationProfileLayoutTest.kt`.
  - `REQ-UI-299`: Sprint 2026-41.3 (`ATT-2510`, commit `e1a42f7c`) preserved summit badges while moving climb span indicators to the horizontal X-axis baseline (`ElevationProfileClimbSpanContractTest.kt`).
* **Root Reason for Existing Formulation**: When `topPadding` was optimized in `REQ-UI-225`, `16.dp` was chosen as a compact top padding for list previews without zoom buttons. However, testing was primarily conducted on detail screens (`MapDetailLayout`) where `showZoomControls == true` (`topPadding = 44.dp`), where 44 dp easily absorbed the 22–25 dp badge. In live tracking (`SensorGridScreen.kt`), `showZoomControls == false`, leaving only 16 dp of padding which proved insufficient when a climb peaked at maximum route elevation.
* **Preservation of Core Invariants**:
  - Slope gradient curve coloring (`Zone1`..`Zone5`) per `REQ-UI-299`.
  - Horizontal X-axis climb span bars (`strokeWidth = 4.dp.toPx()`, `cap = StrokeCap.Round`) per `REQ-UI-299` and `REQ-UI-307`.
  - Literal compatibility with `ElevationProfileLayoutTest.kt`:
    - Line 59: `val topPadding = if (showZoomControls) 44.dp else 16.dp`
    - Line 65: `val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight`
  - Literal compatibility with `ElevationProfileClimbSpanContractTest.kt`:
    - Line 146: `canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)`
    - Line 150: `canvas.nativeCanvas.drawRoundRect`
  - 100% 9-language translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  - 100% clean-room test pass rate across all 2573+ unit tests.

---

### 1.3 Functional & Architectural Requirements

1. **Dynamic Font-Scale Aware Top Clearance Calculation**:
   - `ElevationProfile.kt` SHALL compute the required climb category badge clearance dynamically using the active Compose density and font scale:
     ```kotlin
     val climbBadgeTextHeight = with(LocalDensity.current) { 10.sp.toDp() }
     val climbBadgePillHeight = climbBadgeTextHeight + 4.dp
     val climbBadgeStemHeight = 8.dp
     val climbBadgeSafetyMargin = 2.dp
     val requiredClimbTopClearance = climbBadgePillHeight + climbBadgeStemHeight + climbBadgeSafetyMargin
     ```
   - When distance-domain rendering is active and climbs are present (`!isTimeDomain && climbs.isNotEmpty()`):
     ```kotlin
     val baseTopPadding = if (fixedHeight != null) 8.dp else topPadding
     val climbBadgeTopPadding = if (!isTimeDomain && climbs.isNotEmpty()) {
         (requiredClimbTopClearance - baseTopPadding).coerceAtLeast(0.dp)
     } else {
         0.dp
     }
     val effectiveTopPadding = baseTopPadding + climbBadgeTopPadding
     ```
   - When `climbs.isEmpty()` or `isTimeDomain == true`, `climbBadgeTopPadding` SHALL evaluate to `0.dp`, preserving original padding without overhead.
   - When `showZoomControls == true` (e.g. `MapDetailLayout`), `baseTopPadding = 44.dp` exceeds `requiredClimbTopClearance` across standard and accessibility font scales (up to 2.0x), so `climbBadgeTopPadding` evaluates to `0.dp`.

2. **Container Height Stability & Chart Area Rescaling**:
   - In dynamic height mode (`fixedHeight == null`):
     - `totalCanvasHeight` SHALL remain strictly:
       `val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight`
     - The Composable's external layout height SHALL NOT expand dynamically when climbs are present, preventing jumping card UI elements and layout instability.
     - The Canvas chart drawing height (`height = totalCanvasHeight - effectiveTopPadding - bottomPadding`) SHALL absorb the clearance delta, rescaling the elevation polyline down smoothly within the available area.
   - In fixed height mode (`fixedHeight != null`):
     - The container height SHALL remain strictly clamped to `fixedHeight`.
     - `effectiveTopPadding` SHALL expand by `(requiredClimbTopClearance - 8.dp).coerceAtLeast(0.dp)`.
     - The chart plotting area SHALL scale down proportionally, eliminating boundary overflow.

3. **Two-Layer Boundary Protection (Vertical Ceiling & Horizontal Margins)**:
   - In the Canvas draw loop for climb category badges:
     ```kotlin
     val rawPillBottom = summitY - 8.dp.toPx()
     val rawPillTop = rawPillBottom - pillHeight
     val minAllowedPillTop = -effectiveTopPadding.toPx() + 2.dp.toPx()
     val pillTop = maxOf(rawPillTop, minAllowedPillTop)
     val pillBottom = pillTop + pillHeight
     ```
   - This bounding ceiling guarantees that in Composable coordinates, $y_{\text{box}} = \text{effectiveTopPadding} + \text{pillTop} \ge 2\text{dp}$ under all circumstances.
   - For horizontal bounds, safety margins of `2.dp.toPx()` SHALL be enforced:
     ```kotlin
     val minMarginPx = 2.dp.toPx()
     val pillLeft = (summitX - pillWidth / 2f).coerceIn(minMarginPx, (width - pillWidth - minMarginPx).coerceAtLeast(minMarginPx))
     val pillRight = pillLeft + pillWidth
     ```
   - The pin stem line SHALL connect `summitX, summitY` to `summitX, pillBottom` using the exact contract line:
     ```kotlin
     canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)
     ```

4. **Localization Parity**:
   - Zero new user-facing strings are introduced. Category labels ("UC", "Cat 4", "Cat 3", etc.) continue to utilize pre-existing string resources in `strings.xml` (`climb_cat_...`) with 100% translation parity across all 9 supported locales.

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Dynamic Top Clearance Allocation for Routes with Climbs**:
  * *Given* an elevation profile rendered in distance domain (`!isTimeDomain`) with recognized climbs (`climbs.isNotEmpty()`),
  * *When* `showZoomControls == false` (e.g. in `SensorGridScreen.kt` or route previews),
  * *Then* `effectiveTopPadding` SHALL dynamically expand from `16.dp` to at least `24.dp` (at font scale 1.0x) to accommodate badge height, pin stem, and safety margin.
* **Criterion 2: Zero Top Clearance Overhead for Routes without Climbs**:
  * *Given* an elevation profile rendered with `climbs.isEmpty()`,
  * *When* `showZoomControls == false`,
  * *Then* `effectiveTopPadding` SHALL remain strictly `16.dp` with `climbBadgeTopPadding == 0.dp`.
* **Criterion 3: Zero Top Clearance Overhead in Detail View with Zoom Controls**:
  * *Given* an elevation profile rendered in `MapDetailLayout` with `showZoomControls == true` and climbs present,
  * *When* evaluated,
  * *Then* `effectiveTopPadding` SHALL remain `44.dp` with `climbBadgeTopPadding == 0.dp`, since 44 dp exceeds the required clearance.
* **Criterion 4: Container Height Stability & Proportional Rescaling**:
  * *Given* an elevation profile rendered with `fixedHeight == null`,
  * *When* climbs are present,
  * *Then* `totalCanvasHeight` SHALL NOT expand and SHALL remain equal to `cachedData.adaptiveHeight`, while the elevation polyline scales down inside the available canvas height without jumping layout bounds.
* **Criterion 5: Fixed Height Container Support**:
  * *Given* an elevation profile rendered with `fixedHeight = 120.dp` and climbs present,
  * *When* rendered,
  * *Then* the container height remains exactly `120.dp`, `effectiveTopPadding` expands to at least `24.dp`, and the elevation chart polyline scales down without overflowing the container bounds.
* **Criterion 6: Accessibility Font Scaling (1.5x & 2.0x)**:
  * *Given* an athlete device configured with Accessibility FontScale = 1.5x or 2.0x,
  * *When* rendering an elevation profile with climbs,
  * *Then* `requiredClimbTopClearance` dynamically expands to at least `29.dp` (for 1.5x) and `34.dp` (for 2.0x), ensuring zero text clipping or container truncation.
* **Criterion 7: Summit at Peak Elevation Boundary Protection Ceiling**:
  * *Given* a route where a climb reaches the maximum altitude of the profile (`summitY = 0f`),
  * *When* rendered,
  * *Then* `minAllowedPillTop` ensures the badge pill top in Composable coordinates satisfies $y_{\text{box}} \ge 2\text{dp}$, preventing Surface container clipping.
* **Criterion 8: Horizontal Boundary Safety Margins**:
  * *Given* a climb with a summit located at $X = 0$ (route start) or $X = \text{width}$ (route finish),
  * *When* rendered,
  * *Then* `pillLeft` is clamped within `[2.dp, width - pillWidth - 2.dp]`, preventing horizontal clipping.
* **Criterion 9: Full Regression Pass Rate**:
  * *Given* the entire unit test suite,
  * *When* executing `./gradlew testDebugUnitTest`,
  * *Then* 100% of all unit tests (2573+ tests) SHALL pass with zero failures.

---

## 2. Test Specification (TST-UI-322)

### 2.1 Test Architecture & File Structure
* **Test Class**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileClimbBadgeClippingTest.kt`
* **Test Target**: `com.atrainingtracker.trainingtracker.ui.map.ElevationProfile.kt`
* **Test Framework**: JUnit 4, Kotlin Reflection / Source Code AST Inspection, Robolectric / Compose Unit Testing.

### 2.2 Detailed Test Cases Matrix

| Test Method | Target Requirement | Verification Mechanism | Expected Outcome |
|---|---|---|---|
| `testClimbBadge_dynamicTopClearance_allocatesRequiredPadding` | REQ-UI-274 (Amended, 1) | Inspect dynamic clearance formula in `ElevationProfile.kt` | `climbBadgeTopPadding` evaluates to `(requiredClimbTopClearance - baseTopPadding).coerceAtLeast(0.dp)` when `!isTimeDomain && climbs.isNotEmpty()`. |
| `testClimbBadge_zeroTopClearance_whenClimbsEmpty` | REQ-UI-274 (Amended, 1) | Verify clearance when `climbs.isEmpty()` | `climbBadgeTopPadding` evaluates to `0.dp`. |
| `testClimbBadge_zeroTopClearance_inDetailView` | REQ-UI-274 (Amended, 1) | Verify clearance when `showZoomControls == true` | `baseTopPadding = 44.dp` $\ge$ `requiredClimbTopClearance`, resulting in `climbBadgeTopPadding == 0.dp`. |
| `testClimbBadge_containerHeightStability_preservesTotalCanvasHeight` | REQ-UI-274 (Amended, 2) | Source code AST verification of `totalCanvasHeight` | Contains exact invariant `val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight`. |
| `testClimbBadge_fixedHeight_rescalesChartWithoutOverflow` | REQ-UI-274 (Amended, 2) | Layout math verification with `fixedHeight = 120.dp` | Chart height $= 120\text{dp} - \text{effectiveTopPadding} - 18\text{dp}$, badge fits inside container bounds. |
| `testClimbBadge_accessibilityFontScaling_metrics` | REQ-UI-274 (Amended, 3) | Density calculation verification at 1.0x, 1.5x, 2.0x | Clearance $\ge 24\text{dp}$ at 1.0x, $\ge 29\text{dp}$ at 1.5x, $\ge 34\text{dp}$ at 2.0x. |
| `testClimbBadge_verticalCeilingAndHorizontalSafetyMargins` | REQ-UI-274 (Amended, 3) | Mathematical simulation of summit at $Y = 0$, $X = 0$, $X = \text{width}$ | $y_{\text{box}} \ge 2\text{dp}$, $X_{\text{left}} \ge 2\text{dp}$, $X_{\text{right}} \le \text{width} - 2\text{dp}$. |
| `testClimbBadge_preservesExistingLayoutAndClimbContracts` | REQ-UI-274 (Invariants) | Source code AST check against `ElevationProfileLayoutTest` & `ElevationProfileClimbSpanContractTest` | Verbatim preservation of `val topPadding = if (showZoomControls) 44.dp else 16.dp` and `canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)`. |

### 2.3 Localization & Regression Command Execution
* **Localization Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*TranslationParityTest*"
  ```
* **Targeted Unit Test Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*ElevationProfile*"
  ```
* **Full Clean-Room Regression**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
