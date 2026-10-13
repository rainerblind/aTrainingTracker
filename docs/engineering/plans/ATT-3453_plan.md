# Stage 3: Implementation Plan - ATT-3453: Prevent clipping of climb category marker badges in elevation profile

**Ticket**: [ATT-3453](https://atrainingtracker.atlassian.net/browse/ATT-3453)  
**Sub-task**: [ATT-3475](https://atrainingtracker.atlassian.net/browse/ATT-3475) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-UI-274` (Clause 4, Dynamic Top Clearance & Boundary Protection)  
**Test Mapping**: `TST-UI-322` (`ElevationProfileClimbBadgeClippingTest.kt`)  
**Branch**: `feature/ATT-3453`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Description & Background

In `ElevationProfile.kt`, climb category marker badges (e.g. "UC", "Cat 4", "Cat 3", "Cat 2", "Cat 1", "HC") are drawn above summit peaks to classify categorized cycling climbs. When a climb peaks at or near the maximum altitude of the profile (`cachedData.maxAlt`), elevation coordinate normalization places the summit at Canvas $Y = 0\text{f}$.

Because badges are drawn with an upward vertical offset (pin stem length of $8\text{dp}$, pill height of $\sim 14\text{dp}$ at standard font scale, plus safety margins), the badge extends into negative Canvas space ($-22\text{dp}$ to $-34\text{dp}$). In compact list previews or live tracking (`SensorGridScreen.kt`), base top padding is only $16\text{dp}$ (or $8\text{dp}$ for `fixedHeight != null`). Consequently, the top portion of the badge extends above the outer Composable container ($y_{\text{box}} < 0$). Material 3 container surfaces (such as `Surface` or `Card` in `SensorGridScreen.kt`) apply layout clipping, truncating the marker badges.

ATT-3453 resolves this defect by dynamically computing required top clearance, preserving outer container dimensions to prevent layout jitter, guaranteeing minimum chart plotting height under constrained heights, and enforcing boundary ceiling and margin clamping.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-274` (*Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown*, Clause 4)
* **Test Mapping**: `TST-UI-322` (*Prevent Clipping of Climb Category Marker Badges in Elevation Profile Canvas Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Exact Verbatim Top Padding Baseline Contract**:
   * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileLayoutTest.kt` line 59 mandates:
     `val topPadding = if (showZoomControls) 44.dp else 16.dp`
   * This statement MUST be preserved verbatim in `ElevationProfile.kt`.
2. **Exact Verbatim Canvas Height Layout Invariant**:
   * `ElevationProfileLayoutTest.kt` line 65 mandates:
     `val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight`
   * This definition MUST remain verbatim; `totalCanvasHeight` must NOT expand when climbs are present, ensuring zero layout shifts or card jumping in parent layouts (`RouteItem.kt`, `SensorGridScreen.kt`).
3. **Exact Verbatim Pin Stem Contract**:
   * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileClimbSpanContractTest.kt` line 146 mandates:
     `canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)`
   * This drawing call MUST remain verbatim.
4. **Slope Gradient Coloring & Span Bar Invariants**:
   * Intrinsic slope gradient coloration (`REQ-UI-299`) and horizontal baseline climb span bars (`REQ-UI-299`, `REQ-UI-307`) MUST NOT be altered.
5. **Constrained Viewport Minimum Chart Height Guarantee**:
   * If `fixedHeight` is constrained ($< 60\text{dp}$), the system reserves at least $H_{\text{chart, min}} = 32\text{dp}$ of plotting area, capping top clearance and relying on boundary ceiling clamping.
6. **100% Clean-Room Unit Test Pass Rate**:
   * All 2573+ unit tests must continue to pass cleanly (`./gradlew testDebugUnitTest`).
7. **Human Decision Gate Guard**:
   * Parent ticket ATT-3453 terminal transition to `Erledigt` is reserved strictly for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component: `ElevationProfile.kt` (`ui/map`)

#### 1. Dynamic Font-Scale Aware Clearance Evaluation
Calculate the required top clearance using `LocalDensity.current` to dynamically scale with accessibility settings:
```kotlin
val badgeTextSizeDp = with(LocalDensity.current) { 10.sp.toDp() }
val requiredClimbTopClearance = badgeTextSizeDp + 4.dp + 8.dp + 2.dp // text + pill padding (2*2dp) + stem (8dp) + safety margin (2dp)
val climbBadgeTopPadding = if (!isTimeDomain && climbs.isNotEmpty()) {
    (requiredClimbTopClearance - topPadding).coerceAtLeast(0.dp)
} else {
    0.dp
}
```

#### 2. Adaptive Effective Top Padding Calculation
Preserving the verbatim base `topPadding` and `totalCanvasHeight`, adjust `effectiveTopPadding`:
```kotlin
val topPadding = if (showZoomControls) 44.dp else 16.dp
val bottomPadding = if (fixedHeight != null) 18.dp else 24.dp
val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight

val effectiveTopPadding = if (fixedHeight != null) {
    if (!isTimeDomain && climbs.isNotEmpty()) {
        val maxAllowedTop = (fixedHeight - bottomPadding - 32.dp).coerceAtLeast(8.dp)
        requiredClimbTopClearance.coerceIn(8.dp, maxAllowedTop)
    } else {
        8.dp
    }
} else {
    topPadding + climbBadgeTopPadding
}
```
* In compact dynamic viewports (`fixedHeight == null`, `showZoomControls == false`): `effectiveTopPadding` expands from $16\text{dp}$ to at least $24\text{dp}$ (1.0x), $29\text{dp}$ (1.5x), or $34\text{dp}$ (2.0x).
* In detail viewports (`showZoomControls == true`): `topPadding` is already $44\text{dp} \ge \text{requiredClimbTopClearance}$, resulting in `climbBadgeTopPadding = 0.dp` and `effectiveTopPadding = 44.dp`.
* In fixed height viewports (`fixedHeight != null`): reserves $32\text{dp}$ chart height while granting up to `requiredClimbTopClearance`.

#### 3. Two-Layer Boundary Protection & Clamping
Inside Canvas drawing:
```kotlin
val minAllowedPillTop = -effectiveTopPadding.toPx() + 2.dp.toPx()
val unconstrainedPillBottom = summitY - 8.dp.toPx()
val unconstrainedPillTop = unconstrainedPillBottom - pillHeight
val pillTop = unconstrainedPillTop.coerceAtLeast(minAllowedPillTop)
val pillBottom = pillTop + pillHeight
val pillLeft = (summitX - pillWidth / 2f).coerceIn(2.dp.toPx(), width - pillWidth - 2.dp.toPx())
val pillRight = pillLeft + pillWidth
```
Guarantees:
* Container ceiling margin: $y_{\text{box}} = \text{effectiveTopPadding} + \text{pillTop} \ge 2\text{dp}$.
* Horizontal margin: at least $2\text{dp}$ from canvas left and right edges.
* Verbatim pin stem drawing: `canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)`.

---

### UI Consistency (Rule 23 — Mandatory Section)

* **Reference Screen / Component**: `ElevationProfile.kt` (rendered in `RouteOnMapScreen`, `SensorGridScreen`, `RouteItem`, `SegmentItem`).
* **Reused Components**: Pre-existing `ElevationProfile` canvas, `getClimbCategoryColors(climb.category)`.
* **Theme Tokens**: `MaterialTheme.colorScheme.onSurface`, `TTColor` climb category palette, standard Material 3 $4\text{dp}$ rounded corners for pill background.
* **New One-Off Styles & Justification**: None. Fully leverages pre-existing tokens and typography.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Flight Gate 3 Invariant Check
* Command: `python3 tools/jira_util.py check-gate ATT-3475`
* Verify exit code 0 before any production code modification.

### Step 2: Create Comprehensive Test Suite (`TST-UI-322`)
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileClimbBadgeClippingTest.kt`
* Methods:
  1. `testClimbBadge_dynamicTopClearance_allocatesRequiredPadding`
  2. `testClimbBadge_zeroTopClearance_whenClimbsEmpty`
  3. `testClimbBadge_zeroTopClearance_inDetailView`
  4. `testClimbBadge_containerHeightStability_preservesTotalCanvasHeight`
  5. `testClimbBadge_fixedHeight_rescalesChartWithoutOverflow`
  6. `testClimbBadge_accessibilityFontScaling_metrics`
  7. `testClimbBadge_constrainedHeight_minimumChartHeightGuarantee`
  8. `testClimbBadge_verticalCeilingAndHorizontalSafetyMargins`
  9. `testClimbBadge_preservesExistingLayoutAndClimbContracts`

### Step 3: Implement Dynamic Clearance & Boundary Clamping in Production Code
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
* Integrate `requiredClimbTopClearance`, `climbBadgeTopPadding`, `effectiveTopPadding`, and boundary clamping into `ElevationProfile`.

### Step 4: Execute Targeted Unit Tests
* Commands:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileClimbBadgeClippingTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileLayoutTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileClimbSpanContractTest"
  ```

### Step 5: Execute Clean-Room Full Regression Suite
* Command:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction (Step 4), full clean-room suite in Stage 5 (Step 5), and on-device rendering check.
* **Rollback**: Branch isolation on `feature/ATT-3453` allows immediate clean git revert without impacting `sprint/2026-42.2` or `develop`.
