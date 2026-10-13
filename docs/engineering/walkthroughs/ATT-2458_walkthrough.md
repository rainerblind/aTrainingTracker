# Stage 5: Walkthrough & Verification - ATT-2458: Relocate Route Selection Button from Cockpit Sensor Grid Tabs to Exclusively Control Tracking Screen

**Ticket**: [ATT-2458](https://atrainingtracker.atlassian.net/browse/ATT-2458)  
**Sub-task**: [ATT-2566](https://atrainingtracker.atlassian.net/browse/ATT-2566) (`[Test]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-279` (*Branded Route Selection Entry Point on Control Tracking Screen & Cockpit Sensor Grid Decoupling*)  
**Test Mapping**: `TST-UI-239` (*Branded Route Selection Button on Control Tracking Screen & Cockpit Sensor Grid Decoupling Verification*)  
**Branch**: `feature/ATT-2458`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Executive Summary & Verification Overview

Under ATT-1835, a quick route selector chip (`RouteActionChipRow`) was previously rendered at the top of every swipeable sensor grid tab (`SensorGridScreen.kt`). This consumed 48–56dp of vertical glanceability and placed a pre-ride setup workflow into the real-time execution cockpit. With the completion of ATT-2189, pairing buttons were relocated to the sensor management tabs, establishing a dedicated `bottomContent` slot on `ControlTrackingScreen.kt`.

ATT-2458 successfully:
1. **Decoupled SensorGridScreen**: Cleanly removed `RouteActionChipRow` from all cockpit sensor tabs, reclaiming full vertical screen density for athlete telemetry.
2. **Preserved In-Ride Automated Detection**: Maintained `AutoDetectedRouteBanner` and candidate activation/dismissal hooks intact in `SensorGridScreen.kt`.
3. **Implemented RouteSelectionButton**: Created an app-consistent, branded entry point (`RouteSelectionButton.kt`) featuring Rule 23 design tokens and Section 5.4 green domain semantic accents (`RoundedCornerShape(12.dp)`, `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))`, 36.dp green-tinted icon container).
4. **Wired Control Tracking Screen**: Populated `ControlTrackingScreen`'s `bottomContent` slot via `TrackingTabsScreen.kt` and wired it to `RouteSelectorModalBottomSheet`.
5. **Maintained 100% 9-Language Localization Parity**: Verified `route_action_select`, `route_action_select_desc`, `route_action_clear`, and `route_metrics_format` across all 9 application locales.

All changes were validated through dedicated architectural contract tests, 9-language localization audits, and a 100% clean-room full suite regression (`./gradlew testDebugUnitTest` passing with 0 failures).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-279.1` (Removal from Cockpit Tabs & Auto-Detect Preservation) | `TST-UI-239.1`, `TST-UI-239.2` | Automated Contract & Integration Tests (`SensorGridScreenRouteIntegrationTest.kt`, `ControlTrackingRouteSelectionContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-279.2` (Control Tracking Bottom Slot Entry Point) | `TST-UI-239.1` | Automated Contract Tests (`ControlTrackingRouteSelectionContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-279.3` (Branded Green Domain Semantic Accents) | `TST-UI-239.1` | Design Token & AST Contract Tests (`ControlTrackingRouteSelectionContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-279.4` (Full Functional Parity & Sheet Hosting) | `TST-UI-239.1`, `TST-UI-239.2` | ViewModel & Sheet Contract Tests (`RouteSelectorViewModelTest.kt`, `SensorGridScreenRouteIntegrationTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-279.5` (9-Language Parity) | `TST-UI-239.3` | Multi-Locale XML Parity Tests (`RouteSelectionLocalizationTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` (Clean-Room Full Suite Regression) | `TST-UI-239.4` | `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 9m 15s
32 actionable tasks: 12 executed, 20 up-to-date
```
Zero failures, zero regressions across all modules.

### Targeted Contract & Unit Tests
```text
ControlTrackingRouteSelectionContractTest:
  - testSensorGridScreen_doesNotContainRouteActionChipRow: PASSED
  - testSensorGridScreen_preservesAutoDetectedRouteBanner: PASSED
  - testRouteSelectionButton_adheresToRule23AndBrandingDesignTokens: PASSED
  - testTrackingTabsScreen_wiresRouteSelectionButtonToBottomContentSlot: PASSED

ControlTrackingLayoutContractTest:
  - testControlTrackingScreenHasNoPairingButtonsInBody: PASSED
  - testControlTrackingScreenExposesBottomContentSlot: PASSED
  - testControlTrackingButtonIsCenteredWithWeight1f: PASSED
  - testLocationPermissionGatingIntact: PASSED

SensorGridScreenRouteIntegrationTest:
  - testSensorGridScreen_declaresRouteSelectorViewModelParameter: PASSED
  - testSensorGridScreen_doesNotIntegrateRouteActionChipRow: PASSED
  - testSensorGridScreen_integratesAutoDetectedRouteBanner: PASSED
  - testSensorGridScreen_doesNotHostRedundantRouteSelectorModalBottomSheet: PASSED
  - testSensorGridScreen_forwardsLocationUpdatesToRouteSelectorViewModel: PASSED
  - testRouteSelectionButton_integratesClearRouteAction: PASSED

RouteSelectionLocalizationTest:
  - testRouteActionSelectParityAcrossAllLocales: PASSED (9/9 locales)
  - testRouteActionSelectDescParityAcrossAllLocales: PASSED (9/9 locales)
  - testRouteActionClearParityAcrossAllLocales: PASSED (9/9 locales)
  - testRouteMetricsFormatParityAcrossAllLocales: PASSED (9/9 locales)
```

---

## 4. Hardware / Physical Verification & Visual Consistency

### Visual Consistency (Rule 23 & Design Guidelines Section 5)

* **Shapes (§5.3)**: Card uses `RoundedCornerShape(12.dp)`; inner icon container uses `RoundedCornerShape(8.dp)`. Fully compliant.
* **Spacing (§5.2)**: `16.dp` horizontal padding, `10.dp` vertical padding, `12.dp` spacing between icon and text. Fully compliant with standard spacing scale.
* **Colors & Themes (§5.4)**:
  - Subtle green border accent: `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))`.
  - Icon container: `TTColor.RouteSelected.copy(alpha = 0.12f)` background with `TTColor.RouteSelected` icon tint.
  - Surface: `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)`.
  - Adapts seamlessly to Light, Dark, and AMOLED themes without hardcoded hex values.
* **Typography & Icons (§5.5)**:
  - Title: `MaterialTheme.typography.titleMedium` with `FontWeight.SemiBold`.
  - Subtitle: `MaterialTheme.typography.bodySmall` with `onSurfaceVariant`.
  - Icons: `R.drawable.ic_route`, `Icons.AutoMirrored.Default.ArrowForwardIos`, and `Icons.Default.Close`.
* **Placement & Entry Points (§5.6)**: Placed in the dedicated `bottomContent` slot of `ControlTrackingScreen.kt`, right where the athlete configures workout settings before riding.
* **Deviations & Justification**: None.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Full clean-room test suite passed with 100% success rate.
2. **Living Documentation Synchronized**: Status for `REQ-UI-279` in `docs/requirements.md` and `TST-UI-239` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask ([ATT-2566](https://atrainingtracker.atlassian.net/browse/ATT-2566)) transitioned to `Erledigt` via reviewer audit.
4. **Parent Ticket Final Review**: Parent ticket ([ATT-2458](https://atrainingtracker.atlassian.net/browse/ATT-2458)) transitioned to `Final Review (Human)` and assigned to the user for final gate sign-off.
5. **No Premature Fix Version**: Subtask has no `fixVersions`; parent ticket fixVersion assignment is deferred until final acceptance.
