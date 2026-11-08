# Stage 2: Requirement & Test Specification - ATT-5845: ElevationProfile Y-Axis Grid Clearance & Zero-Altitude Profile Suppression in RouteItem

**Ticket**: [ATT-5845](https://rainerblind.atlassian.net/browse/ATT-5845)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Karten & Routen*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5845`  
**Requirement Mapping**: `REQ-UI-494` (`REQ-UI-494.1`–`REQ-UI-494.3`) | **Test Spec ID**: `TST-UI-494` (`TST-UI-494.1`–`TST-UI-494.5`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-08  

---

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Refines `REQ-UI-197` (*Elevation Profile Layout Architecture*) and `REQ-UI-225` (*Route Item Layout*).
* `Historical Origin & Commit Trace`: Commit `ATT-508` (bounds calculation) and `ATT-2463` (`RouteItem` layout).
* `Root Reason for Existing Formulation`: The clearance guard in `ElevationProfile.kt` was written before the `+ 10f` baseline downward shift was introduced for vertical text centration on grid lines; `RouteItem` assumed all imported GPX routes would contain elevation data.
* `Preservation of Core Invariants`: `ATT-5853` top padding (`24.dp` / `16.dp`) and climb badge clearance invariants, chart zoom/pan math, full-screen route detail elevation analysis (`MapDetailLayout`), and authentic route elevation visualization MUST remain strictly preserved.

---

## 1. Requirement Specification (`REQ-UI-494`)

### 1.1 Problem Statement & Rationale
UI audits of the Routes screen (`screen_04_routen.png`) identified two defects in `ElevationProfile` and `RouteItem`:
1. **Y-Axis Label Collision**: Intermediate elevation grid labels (e.g. `400`) overlap bottom minimum altitude label (`383 m`) due to omitting the `+ 10f` baseline offset in clearance calculations and using a fixed hardcoded `-110f` X-offset.
2. **Empty Elevation Profile on Flat GPX Routes**: Flat routes lacking altitude data (e.g. `bike2work`, `↑ 0 m`) render a meaningless synthetic `-10 m` to `10 m` flat green elevation profile in `RouteItem`.

### 1.2 Functional & Architectural Requirements
1. **Y-Axis Intermediate Label Baseline Clearance Guard (`REQ-UI-494.1`)**: In `ElevationProfile.kt`, the clearance check SHALL evaluate actual drawn baseline `labelBaselineY = y + 10f`. Intermediate labels SHALL be drawn only if `(height - labelBaselineY) >= textPaint.textSize * 1.35f`, `(labelBaselineY - highlightPaint.textSize) >= textPaint.textSize * 1.35f`, and `Math.abs(y - lastY) >= textPaint.textSize * 1.35f`.
2. **Right-Aligned Intermediate Elevation Labels (`REQ-UI-494.2`)**: Intermediate labels SHALL be right-aligned dynamically to `-textPaint.measureText(altText) - 10f`, matching `minAltLabel` and `maxAltLabel`.
3. **Zero-Altitude Profile Suppression (`REQ-UI-494.3`)**: In `RouteItem.kt`, `ElevationProfile` SHALL only be rendered when `hasElevationData = pathPoints.any { it.altitude != null && !it.altitude.isNaN() && Math.abs(it.altitude) > 1e-3 } || summary.elevationGain > 0.0`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Clearance Guard)**: *Given* `cachedData.minAlt = 383 m` and an intermediate tick at `400 m` where `labelBaselineY` is within `textPaint.textSize * 1.35f` of `height`, *When* drawn, *Then* intermediate label `400` is suppressed.
* **AC-2 (Right Alignment)**: *Given* intermediate altitude labels rendered on the Y-axis, *When* drawn, *Then* labels are right-aligned to `-textPaint.measureText(altText) - 10f`.
* **AC-3 (Zero Altitude Suppression)**: *Given* a route with `elevationGain == 0.0` and no non-zero altitude points, *When* `RouteItem` renders, *Then* `ElevationProfile` is hidden.

---

## 2. Test Specification (`TST-UI-494`) & Traceability Matrix

| Test Case | Scope | Target Test Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-494.1` | Baseline Clearance Contract | `ElevationProfileClearanceContractTest.kt` | `REQ-UI-494.1` | Specified |
| `TST-UI-494.2` | Right Alignment Contract | `ElevationProfileClearanceContractTest.kt` | `REQ-UI-494.2` | Specified |
| `TST-UI-494.3` | Zero Altitude Suppression | `RouteItemZeroElevationContractTest.kt` | `REQ-UI-494.3` | Specified |
| `TST-UI-494.4` | Invariant Non-Regression | `ElevationProfileLayoutTest.kt`, `ClimbBadgeClippingTest.kt` | `ATT-5853` | Specified |
| `TST-UI-494.5` | Targeted Regression | `ui.map.*`, `ui.routes.*`, `ThemeColorCentralizationAuditTest` | `REQ-PRO-001` | Specified |
