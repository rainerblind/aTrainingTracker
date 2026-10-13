# Stage 2: Requirement & Test Specification - ATT-2865: Preserve authentic sport type icon colors in route selector sheet

**Ticket**: [ATT-2865](https://rainerblind.atlassian.net/browse/ATT-2865)  
**Sub-task**: [ATT-2916](https://rainerblind.atlassian.net/browse/ATT-2916) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-UI-320` (*Preservation of Authentic Sport Type Icon Colors in Quick Route Selector*)  
**Test Spec ID**: `TST-UI-280`  
**Branch**: `improvement/ATT-2865`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-320)

### 1.1 Problem Statement & Rationale
During Sprint 2026-41.4 review of `ATT-2668` on Google Pixel 10, sprint review feedback highlighted that sport type icons within `RouteCard` (in `RouteSelectorSheet.kt`) were tinted with monochrome theme colors (`if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`). Because the application's sport type assets (`bsport_bike`, `bsport_run`, `bsport_other`) are multi-color vector illustrations, applying a single-color tint destroyed their internal graphic details and visual identity. Athletes need each route card's sport icon rendered in its authentic, full-fidelity multi-color format using `tint = Color.Unspecified`.

### 1.2 Functional & Architectural Requirements
The system SHALL preserve the authentic multi-color vector representation of sport type icons in `RouteSelectorSheet.kt` (`RouteCard`) by rendering the icon painter with `tint = Color.Unspecified`, eliminating monochrome theme tinting (ATT-2865, amending `REQ-UI-310`):
1. **Bypass Monochrome Theme Color Filtering (`RouteCard`)**:
   * The `Icon` composable rendering `route.summary.bSportType.iconResId` in `RouteCard` SHALL specify `tint = Color.Unspecified`.
   * Monochrome tinting (`if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`) SHALL be eliminated, ensuring multi-color sport vector assets (`bsport_bike`, `bsport_run`, `bsport_other`) render with their intrinsic full-fidelity XML path colors regardless of whether the route card is in active or inactive state.
2. **Preservation of System Invariants**:
   * Active route indication via dedicated `"ACTIVE"` chip (`labelSmall`, `onPrimary` text over `primary` background) and `secondaryContainer` card container color MUST remain strictly intact.
   * Localized accessibility description (`contentDescription = stringResource(id = route.summary.bSportType.stringResId)`) MUST be preserved.
   * Sport icon dimensions (`24.dp`) and route title/metric typography MUST remain unchanged.
   * 100% test pass rate across all route selector unit and contract tests.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Authentic Sport Icon Colors)**:
  * *Given* an athlete opening `RouteSelectorModalBottomSheet` on `TrackingTabsScreen`,
  * *When* observing route cards for cycling, running, or other sports,
  * *Then* each route card's sport type icon SHALL render in its authentic original multi-color representation (`tint = Color.Unspecified`).
* **Criterion 2 (Monochrome Tint Elimination)**:
  * *Given* any route card in `RouteSelectorModalBottomSheet`,
  * *When* inspecting the icon composable,
  * *Then* the icon SHALL NOT be tinted by `MaterialTheme.colorScheme.primary` or `MaterialTheme.colorScheme.onSurfaceVariant`.
* **Criterion 3 (Active Route Indication Invariant)**:
  * *Given* an active route in `RouteSelectorModalBottomSheet`,
  * *When* rendered,
  * *Then* the route card SHALL continue to display the `"ACTIVE"` badge chip with primary background and secondaryContainer card container color.

---

## 2. Test Specification (TST-UI-280)

### Test Case 1: RouteCard Authentic Sport Icon Color Contract (`TST-UI-280.1`)
* **Scope**: Architectural & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetTest.kt`
* **Checks**:
  * Assert `RouteCard` in `RouteSelectorSheet.kt` declares `tint = Color.Unspecified`.
  * Assert `RouteCard` does NOT contain `tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`.

### Test Case 2: RouteCard Structural Invariants Contract (`TST-UI-280.2`)
* **Scope**: Architectural & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetTest.kt`
* **Checks**:
  * Assert `RouteCard` renders sport icon via `route.summary.bSportType.iconResId`.
  * Assert `RouteCard` provides localized accessibility description via `stringResource(id = route.summary.bSportType.stringResId)`.
  * Assert `RouteCard` sizes sport icon at `24.dp`.

### Test Case 3: Active Route Chip & Layout Invariants (`TST-UI-280.3`)
* **Scope**: Architectural & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetTest.kt`
* **Checks**:
  * Assert `RouteCard` renders `"ACTIVE"` chip with `labelSmall` and `onPrimary` text color when `isActive == true`.
  * Assert `RouteCard` sets `containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant`.

### Test Case 4: Clean-Room Full Suite Regression Execution (`TST-UI-280.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% test pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-280.1` | Contract | `RouteCard` tint parameter | `REQ-UI-320.1` | Specified |
| `TST-UI-280.2` | Contract | `RouteCard` icon sizing & accessibility | `REQ-UI-320.2` | Specified |
| `TST-UI-280.3` | Contract | `RouteCard` active chip & container styling | `REQ-UI-320.2` | Specified |
| `TST-UI-280.4` | Regression | Full test suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
