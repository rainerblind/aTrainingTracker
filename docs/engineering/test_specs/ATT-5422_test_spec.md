# Stage 2: Requirement & Test Specification - ATT-5422: SensorStatus Top-Bar 16 dp Randabstand (Option A)

**Ticket**: [ATT-5422](https://rainerblind.atlassian.net/browse/ATT-5422)  
**Parent Epic**: Standalone Ticket (None)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.6`  
**Requirement Mapping**: `REQ-UI-451` (*SensorStatus Top Bar 16 dp Screen Edge Offset and Centering*)  
**Test Spec ID**: `TST-UI-451`  
**Branch**: `feature/ATT-5422`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-05  

---

## 1. Requirement Specification (REQ-UI-451)

### 1.1 Problem Statement & Rationale
In `SensorStatus.kt` (embedded in `TrackingTabsScreen.kt`), the drawer hamburger menu button (`☰`) and supporter emblem (e.g. Diamond `💎`) sit only ~4 dp from the display edge, visual inspection confirming edge clinging. Option A establishes 16 dp screen edge spacing aligned with Material 3 `TopAppBar` standard and cockpit card headers while increasing cramped 24 dp touch targets to 32 dp.

### 1.2 Functional & Architectural Requirements
1. **Edge Margin Geometry (`REQ-UI-451.1`)**: In portrait mode, `SensorStatus.kt` outer `Row` SHALL apply `padding(horizontal = 8.dp, vertical = 4.dp)`. Combined with outer `Surface` padding (`4.dp`) and internal icon inset (`(32 - 24) / 2 = 4.dp`), the visual margin from screen edge to icon outer contours equals exactly 16 dp (`4 + 8 + 4 = 16.dp`).
2. **Symmetrical 32 dp Touch Targets & Centering (`REQ-UI-451.2`)**: The menu button, supporter emblem, and their respective fallback `Spacer`s SHALL all measure `Modifier.size(32.dp)` (housing 24 dp icons), guaranteeing that `Box(Modifier.weight(1f))` holding the 8 sensor icons remains mathematically centered.
3. **Landscape & Non-Regression Invariant (`REQ-UI-451.3`)**: Compact landscape layout (`REQ-UI-434.1`, max 28 dp) and sensor source dialog behavior SHALL remain unchanged.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extending `REQ-UI-445.3` (Live Cockpit Status Emblem), targets `SensorStatus.kt` and `TrackingTabsScreen.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-45.4 (`ATT-5397`, commit `8baf42c8`) and Sprint 2026-40.8 (`ATT-1406`, commit `8f8b8098`).
* **Root Reason for Existing Formulation**: `REQ-UI-445.3` balanced the hamburger button with the supporter emblem at 24 dp, but omitted outer row padding, resulting in 4 dp display clinging.
* **Preservation of Core Invariants**: 8 sensor icons remain mathematically centered via equal 32 dp outer slots; 24 dp icon vectors and click handlers are preserved; touch target increases from 24 dp to 32 dp.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (16 dp Edge Margin)**: *Given* the tracking cockpit in portrait mode, *When* `SensorStatus` renders, *Then* the menu button and supporter emblem (or fallback spacer) start at exactly 16 dp from the outer display boundary.
* **AC-2 (Exact Sensor Centering)**: *Given* an athlete with any supporter tier or free tier, *When* rendering the cockpit bar, *Then* the 8 sensor icons remain mathematically centered in the horizontal viewport.
* **AC-3 (Touch Target Ergonomics)**: *Given* athlete interaction, *When* tapping the menu or supporter button, *Then* the enlarged 32 dp touch targets respond reliably.

---

## 2. Test Specification (TST-UI-451)

### Test Case 1: `testSensorStatus_portraitPaddingAndSymmetricSpacers` (`TST-UI-451.LAYOUT`)
* **Scope**: Contract Test (`SensorStatusLayoutTest.kt`)
* **Action**: Verify outer `Row` has `padding(horizontal = 8.dp, vertical = 4.dp)` and both outer slots measure `32.dp`.
* **Expected Result**: 16 dp visual offset achieved and central `Box(Modifier.weight(1f))` centers 8 sensor icons.

### Test Case 2: Landscape & Alpha Non-Regression (`TST-UI-451.NONREG`)
* **Scope**: Unit Tests (`SensorStatusLegibilityTest.kt`, `TrackingTabsLandscapeLayoutTest.kt`)
* **Expected Result**: `INACTIVE_SENSOR_ALPHA = 0.38f` and compact landscape layout remain 100% compliant.

### Test Case 3: Targeted Clean-Room Test Suite (`TST-UI-451.SUITE`)
* **Scope**: Targeted Unit Tests (`ui.tracking.controltracking.*`, `ui.branding.*`, `ThemeColorCentralizationAuditTest`, `build.*`)
* **Expected Result**: 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-451.LAYOUT` | Contract | `SensorStatusLayoutTest.kt` | `REQ-UI-451.1`, `REQ-UI-451.2` | Specified |
| `TST-UI-451.NONREG` | Regression | `SensorStatusLegibilityTest.kt` | `REQ-UI-451.3` | Specified |
| `TST-UI-451.SUITE` | Unit Suite | Package wildcards & audit tests | `REQ-PRO-001` | Specified |
