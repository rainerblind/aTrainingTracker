# Stage 3: Implementation Plan - ATT-5845: ElevationProfile Y-Axis Grid Clearance & Zero-Altitude Profile Suppression in RouteItem

**Ticket**: [ATT-5845](https://rainerblind.atlassian.net/browse/ATT-5845)  
**Sub-task**: [ATT-6056](https://rainerblind.atlassian.net/browse/ATT-6056) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Karten & Routen*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5845`  
**Requirement Mapping**: `REQ-UI-494` (`REQ-UI-494.1`–`REQ-UI-494.3`)  
**Test Mapping**: `TST-UI-494` (`TST-UI-494.1`–`TST-UI-494.5`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
Live testing of the Routes screen (`screen_04_routen.png`) identified two elevation profile bugs:
1. In `ElevationProfile.kt:801-808`, intermediate grid labels (e.g. `400`) overlap the bottom `minAltLabel` (`383 m`) due to omitting the `+ 10f` baseline offset in clearance checks and using a hardcoded `-110f` X-offset.
2. In `RouteItem.kt:113`, `ElevationProfile` is rendered unconditionally for flat GPX routes lacking altitude data (e.g. `bike2work`, `↑ 0 m`), displaying a meaningless synthetic `[-10 m, 10 m]` green bar.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-494` (*ElevationProfile Y-Axis Grid Clearance & Zero-Altitude Profile Suppression*)
  * `REQ-UI-494.1`: Baseline clearance guard evaluating `labelBaselineY = y + 10f` against `height`, `highlightPaint.textSize`, and adjacent ticks with `>= textPaint.textSize * 1.35f`.
  * `REQ-UI-494.2`: Right-aligned intermediate labels at `-textPaint.measureText(altText) - 10f`.
  * `REQ-UI-494.3`: Conditional suppression of `ElevationProfile` in `RouteItem.kt` when `hasElevationData` is false.
* **Test Mapping**: `TST-UI-494` (*ElevationProfile Clearance & RouteItem Verification Suite*)
  * `TST-UI-494.1` & `TST-UI-494.2`: Unit and contract tests in `ElevationProfileClearanceContractTest.kt`.
  * `TST-UI-494.3`: Zero-altitude profile suppression contract test in `RouteItemZeroElevationContractTest.kt`.
  * `TST-UI-494.4`: Invariant non-regression in `ElevationProfileLayoutTest` and `ClimbBadgeClippingTest`.
  * `TST-UI-494.5`: Tier 1 targeted regression suite (`ui.map.*`, `ui.routes.*`, `ThemeColorCentralizationAuditTest`).

---

## 3. System Invariants & Preserved Behavior
1. **ATT-5853 Top Padding Invariant**: `val topPadding = if (showZoomControls) 24.dp else 16.dp` and `totalCanvasHeight` additions remain strictly unchanged.
2. **Detail Screen Invariant**: Interactive zooming, scrubbing, and `MapDetailLayout` elevation analysis remain 100% functional.
3. **Valid Elevation Invariant**: Routes with authentic elevation continue to render full elevation profiles cleanly.

---

## 4. Proposed Architectural Changes & UI Consistency
* **`ElevationProfile.kt`**:
  * In vertical tick loop, compute `val labelBaselineY = y + 10f` and `val minClearance = textPaint.textSize * 1.35f`.
  * Guard drawing: `(height - labelBaselineY) >= minClearance && (labelBaselineY - highlightPaint.textSize) >= minClearance && Math.abs(y - lastY) >= minClearance`.
  * Right-align intermediate label: `val altText = altitudeFormatter.format(currentA.toDouble())`; `val altWidth = textPaint.measureText(altText)`; draw at `-altWidth - 10f, labelBaselineY`.
* **`RouteItem.kt`**:
  * Wrap `ElevationProfile` in `val hasElevationData = remember(pathPoints, summary.elevationGain) { pathPoints.any { it.altitude != null && !it.altitude.isNaN() && Math.abs(it.altitude) > 1e-3 } || summary.elevationGain > 0.0 }`.
  * Render `ElevationProfile` only when `hasElevationData == true`.
* **UI Consistency**:
  * Reference screens: Existing `ElevationProfile.kt` and `RouteItem.kt`.
  * Reused tokens: `highlightPaint.textSize`, `textPaint.textSize`, `10.dp`, `TTAlpha.Ghost`. No new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: ElevationProfile Label Clearance & Right Alignment (`ElevationProfile.kt`)**:
   - Update Y-axis grid label loop with baseline clearance guard and dynamic right alignment.
2. **Step 2: RouteItem Zero-Altitude Suppression (`RouteItem.kt`)**:
   - Add `hasElevationData` guard around `ElevationProfile`.
3. **Step 3: Automated Contract & Unit Tests**:
   - Create `ElevationProfileClearanceContractTest.kt` (`TST-UI-494.1`, `TST-UI-494.2`).
   - Create `RouteItemZeroElevationContractTest.kt` (`TST-UI-494.3`).
4. **Step 4: Test Execution & Verification**:
   - Run targeted tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.routes.*"`.
   - Run `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit & contract tests in Stage 4; full clean-room suite in Stage 5.
* **Rollback Plan**: Atomic commit on `feature/ATT-5845`; clean revert via `git reset --hard` if tests fail.
