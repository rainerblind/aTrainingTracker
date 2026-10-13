# Stage 2: Requirement & Test Specification - ATT-3453: Prevent clipping of climb category marker badges in elevation profile (Revised)

**Ticket**: [ATT-3453](https://atrainingtracker.atlassian.net/browse/ATT-3453)  
**Sub-task**: [ATT-3474](https://atrainingtracker.atlassian.net/browse/ATT-3474) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-UI-274` (*Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown*), amended  
**Test Spec ID**: `TST-UI-322`  
**Branch**: `feature/ATT-3453`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13 (Revised per Gate 2 Audit)  

---

## 1. Requirement Specification (Amending REQ-UI-274)

### 1.1 Problem Statement & Rationale
In `ElevationProfile.kt`, climb category marker badges (e.g. "UC", "Cat 4", "Cat 3", "Cat 2", "Cat 1", "HC") are rendered above climb summit peaks to provide instant topographic classification. When a climb peaks at or near the route's maximum altitude (`cachedData.maxAlt`), elevation normalization maps the summit point to $Y = 0\text{f}$ (the top edge of the Canvas viewport).

Because the marker badge pill and pin stem are anchored above the summit point, the entire badge is rendered in negative coordinate space relative to the Canvas ($-22\text{dp}$ to $-34\text{dp}$ depending on system font scale). In compact list previews, live tracking HUD (`SensorGridScreen.kt`), or route cards, the base top padding (`topPadding = 16.dp` or `fixedHeight != null` where `effectiveTopPadding = 8.dp`) is insufficient to contain the negative Y offset. Consequently, the badge extends into negative Composable coordinates ($y_{\text{box}} < 0$). Material 3 container surfaces (such as `Surface` or `Card` in `SensorGridScreen.kt`) apply layout boundary clipping, slicing off the top half of the badge.

ATT-3453 resolves this defect by establishing functional requirements for:
1. **Dynamic Vertical Top Clearance**: Ensuring the elevation profile allocates sufficient vertical headroom whenever climbs are present, adapting dynamically to the active system font scale (1.0x to 2.0x+) so badges are rendered completely without clipping.
2. **Container Dimension Stability & Proportional Rescaling**: Preserving the outer container height of the elevation profile to eliminate layout shifts or card jumping, while rescaling the elevation curve proportionally within the available drawing area.
3. **Two-Layer Boundary Protection & Constrained Overflow Guarantees**: Enforcing a strict container ceiling guard ($y_{\text{box}} \ge 2\text{dp}$) and horizontal margins, alongside a guaranteed minimum chart height ($H_{\text{chart, min}} = 32\text{dp}$) under constrained viewports.
4. **Preserving Layout & Contract Invariants**: Strictly preserving the existing layout and drawing contracts.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-274` (*Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown*), Clause 4 (*Elevation Profile Climb Ridge Highlighting & Category Badges*), extending `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet*) and `REQ-UI-225` (*Decoupled Global Telemetry Zoom Toolbar & ElevationProfile Geometry*). Target: Prevent summit marker badge truncation in compact elevation profiles.
* **Historical Origin & Commit Trace**: `Sprint 2026-40.14` (`ATT-1281`, commit `d5c90d81`): Introduced `REQ-MAP-027`, establishing `ClimbDetector`, `ClimbsDatabaseManager`, and `LiveClimbSheet.kt`. `Sprint 2026-41.1` (`ATT-2388`, commit `7e8a912f`): Introduced summit category badges in `ElevationProfile.kt`. `Sprint 2026-41.1` (`ATT-2386`, commit `b24a10d8`): Decoupled zoom controls and established baseline top paddings (`44.dp` detail, `16.dp` preview) in `REQ-UI-225`. `Sprint 2026-41.3` (`ATT-2510`, commit `e1a42f7c`): Preserved summit badges while moving climb span indicators to horizontal baseline bars in `REQ-UI-299`. `Sprint 2026-42.2` (`ATT-3453`): Resolves badge clipping in compact and live tracking elevation profiles.
* **Root Reason for Existing Formulation**: In `REQ-UI-225`, `topPadding = 16.dp` was calibrated for compact list preview cards when no zoom toolbar was present. In `MapDetailLayout` (`showZoomControls = true`), 44 dp absorbed summit badges. In live tracking (`SensorGridScreen.kt`), `showZoomControls = false` left only 16 dp, cutting off summit badges peaking near the maximum profile altitude (Y = 0).
* **Preservation of Core Invariants**: Curve slope gradient coloration (`Zone1`..`Zone5`) per `REQ-UI-299`. Horizontal X-axis climb span bars (`strokeWidth = 4.dp.toPx()`, `cap = StrokeCap.Round`) per `REQ-UI-299` and `REQ-UI-307`. Verbatim layout contract strings in `ElevationProfileLayoutTest.kt` (lines 59, 65). Verbatim pin stem line contracts in `ElevationProfileClimbSpanContractTest.kt` (line 146). 100% 9-language translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT. 100% clean-room test suite pass rate across all 2573+ unit tests.

---

### 1.3 Functional Requirements (Behavioral Specification - "What, Not How")

1. **Functional Clearance Requirement**:
   - The system SHALL ensure that climb category marker badges rendered above summit peaks on distance-domain elevation profiles maintain a minimum vertical clearance of at least `24.dp` at standard (1.0x) font scale, expanding dynamically with system font scaling (at least `29.dp` at 1.5x, and at least `34.dp` at 2.0x).
   - In detail viewports where zoom controls are active (`showZoomControls == true`), the existing top padding (`44.dp`) exceeds the required clearance across all supported font scales; therefore, zero additional clearance SHALL be added.
   - When no climbs are present (`climbs.isEmpty()`) or in time-domain mode (`isTimeDomain == true`), the baseline preview padding (`16.dp`) SHALL be strictly preserved with zero additional clearance.

2. **Container Stability & Rescaling Constraint**:
   - In dynamic height viewports (`fixedHeight == null`), the external container height of `ElevationProfile` SHALL remain constant (`cachedData.adaptiveHeight`), preventing layout jumps, card resizing, or UI jitter in live tracking (`SensorGridScreen.kt`) and list cards (`RouteItem.kt`, `SegmentItem.kt`).
   - The internal elevation curve plotting height SHALL scale down proportionally to accommodate the required badge clearance within the allocated container bounds.
   - In fixed-height viewports (`fixedHeight != null`), the container height SHALL remain strictly clamped to `fixedHeight`, with the elevation curve scaling down proportionally.

3. **Constrained Viewport Overflow & Minimum Chart Height Guarantee**:
   - If an elevation profile is hosted in an extremely constrained container (e.g. `fixedHeight < 60.dp`), the system SHALL guarantee a minimum viable chart plotting height of $H_{\text{chart, min}} = 32\text{dp}$.
   - Under such constrained conditions, top clearance SHALL NOT compress the chart area below $H_{\text{chart, min}}$. Instead, the system SHALL enforce boundary ceiling clamping on the badge, ensuring that the badge pill never extends above the container's top boundary ($y_{\text{box}} \ge 2\text{dp}$).

4. **Two-Layer Boundary Protection**:
   - The system SHALL enforce a vertical bounding ceiling such that the top edge of any climb category badge maintains at least `2.dp` of clearance inside the container bounds ($y_{\text{box}} \ge 2\text{dp}$), even when a climb summit peaks at the exact maximum route elevation ($Y = 0$).
   - The system SHALL enforce horizontal safety margin insets of at least `2.dp` from the left and right canvas edges, preventing badge clipping when a summit occurs at the inception ($0\text{ km}$) or finish of a route.
   - The pin stem SHALL connect the summit coordinate to the base of the badge pill.

5. **Localization Parity**:
   - Zero new user-facing strings are introduced. Category labels ("UC", "Cat 4", "Cat 3", etc.) continue to utilize pre-existing string resources in `strings.xml` (`climb_cat_...`) with 100% translation parity across all 9 supported locales.

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Dynamic Top Clearance Allocation for Routes with Climbs**:
  * *Given* an elevation profile rendered in distance domain (`!isTimeDomain`) with recognized climbs (`climbs.isNotEmpty()`),
  * *When* `showZoomControls == false` (e.g. in `SensorGridScreen.kt` or route previews),
  * *Then* the effective top clearance SHALL dynamically expand to at least `24.dp` (at font scale 1.0x) to accommodate badge height, pin stem, and safety margin.
* **Criterion 2: Zero Top Clearance Overhead for Routes without Climbs**:
  * *Given* an elevation profile rendered with `climbs.isEmpty()`,
  * *When* `showZoomControls == false`,
  * *Then* the effective top clearance SHALL remain strictly `16.dp` with zero clearance overhead.
* **Criterion 3: Zero Top Clearance Overhead in Detail View with Zoom Controls**:
  * *Given* an elevation profile rendered in `MapDetailLayout` with `showZoomControls == true` and climbs present,
  * *When* evaluated,
  * *Then* the effective top clearance SHALL remain `44.dp` with zero clearance overhead, since 44 dp exceeds the required clearance.
* **Criterion 4: Container Height Stability & Proportional Rescaling**:
  * *Given* an elevation profile rendered with `fixedHeight == null`,
  * *When* climbs are present,
  * *Then* the container height SHALL NOT expand and SHALL remain equal to `cachedData.adaptiveHeight`, while the elevation polyline scales down inside the available canvas height without jumping layout bounds.
* **Criterion 5: Fixed Height Container Support**:
  * *Given* an elevation profile rendered with `fixedHeight = 120.dp` and climbs present,
  * *When* rendered,
  * *Then* the container height remains exactly `120.dp`, top clearance expands to at least `24.dp`, and the elevation chart polyline scales down without overflowing the container bounds.
* **Criterion 6: Accessibility Font Scaling (1.5x & 2.0x)**:
  * *Given* an athlete device configured with Accessibility FontScale = 1.5x or 2.0x,
  * *When* rendering an elevation profile with climbs,
  * *Then* required top clearance dynamically expands to at least `29.dp` (for 1.5x) and `34.dp` (for 2.0x), ensuring zero text clipping or container truncation.
* **Criterion 7: Summit at Peak Elevation Boundary Protection Ceiling**:
  * *Given* a route where a climb reaches the maximum altitude of the profile ($Y = 0$),
  * *When* rendered,
  * *Then* boundary ceiling protection ensures the badge pill top satisfies $y_{\text{box}} \ge 2\text{dp}$, preventing Surface container clipping.
* **Criterion 8: Constrained Container Overflow Guarantee**:
  * *Given* an elevation profile rendered in an extremely tight container (e.g. `fixedHeight = 50.dp`),
  * *When* climbs are present,
  * *Then* the system reserves at least $32\text{dp}$ of chart plotting height and clamps the badge ceiling to keep the badge within container bounds ($y_{\text{box}} \ge 2\text{dp}$).
* **Criterion 9: Horizontal Boundary Safety Margins**:
  * *Given* a climb with a summit located at $X = 0$ (route start) or $X = \text{width}$ (route finish),
  * *When* rendered,
  * *Then* badge horizontal bounds are clamped within `[2.dp, width - pillWidth - 2.dp]`, preventing horizontal clipping.
* **Criterion 10: Full Regression Pass Rate**:
  * *Given* the entire unit test suite,
  * *When* executing `./gradlew testDebugUnitTest`,
  * *Then* 100% of all unit tests (2573+ tests) SHALL pass with zero failures.

---

## 2. Test Specification (TST-UI-322)

### 2.1 Test Architecture & File Structure
* **Test Class**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileClimbBadgeClippingTest.kt`
* **Test Target**: `com.atrainingtracker.trainingtracker.ui.map.ElevationProfile.kt`
* **Test Framework**: JUnit 4, Kotlin Reflection / Source Code AST Inspection, Compose Unit Testing.

### 2.2 Detailed Test Cases Matrix

| Test Method | Target Requirement | Verification Mechanism | Expected Outcome |
|---|---|---|---|
| `testClimbBadge_dynamicTopClearance_allocatesRequiredPadding` | REQ-UI-274 (Amended, 1) | Inspect dynamic clearance formula in `ElevationProfile.kt` | Clearance expands dynamically to at least `24.dp` when `!isTimeDomain && climbs.isNotEmpty()`. |
| `testClimbBadge_zeroTopClearance_whenClimbsEmpty` | REQ-UI-274 (Amended, 1) | Verify clearance when `climbs.isEmpty()` | Clearance overhead evaluates to `0.dp`, preserving 16 dp padding. |
| `testClimbBadge_zeroTopClearance_inDetailView` | REQ-UI-274 (Amended, 1) | Verify clearance when `showZoomControls == true` | Baseline `44.dp` exceeds required clearance, yielding `0.dp` clearance overhead. |
| `testClimbBadge_containerHeightStability_preservesTotalCanvasHeight` | REQ-UI-274 (Amended, 2) | Source code AST verification of `totalCanvasHeight` | Contains exact invariant `val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight`. |
| `testClimbBadge_fixedHeight_rescalesChartWithoutOverflow` | REQ-UI-274 (Amended, 2) | Layout math verification with `fixedHeight = 120.dp` | Container height remains 120 dp, chart area rescales, badge fits inside container bounds. |
| `testClimbBadge_accessibilityFontScaling_metrics` | REQ-UI-274 (Amended, 1 & 4) | Density calculation verification at 1.0x, 1.5x, 2.0x | Clearance $\ge 24\text{dp}$ at 1.0x, $\ge 29\text{dp}$ at 1.5x, $\ge 34\text{dp}$ at 2.0x. |
| `testClimbBadge_constrainedHeight_minimumChartHeightGuarantee` | REQ-UI-274 (Amended, 3) | Simulated tight container ($H < 60\text{dp}$) | Chart plotting height $\ge 32\text{dp}$, badge clamped with $y_{\text{box}} \ge 2\text{dp}$. |
| `testClimbBadge_verticalCeilingAndHorizontalSafetyMargins` | REQ-UI-274 (Amended, 4) | Mathematical simulation of summit at $Y = 0$, $X = 0$, $X = \text{width}$ | $y_{\text{box}} \ge 2\text{dp}$, $X_{\text{left}} \ge 2\text{dp}$, $X_{\text{right}} \le \text{width} - 2\text{dp}$. |
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
