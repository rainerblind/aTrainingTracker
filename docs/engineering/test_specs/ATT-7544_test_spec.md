# Stage 2: Requirement & Test Specification - ATT-7544: Bike Radar Sensor Status Icon & Source Dialog

**Ticket**: [ATT-7544](https://rainerblind.atlassian.net/browse/ATT-7544) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7544`  
**Requirement Mapping**: `REQ-UI-451` (`REQ-UI-451.1`–`REQ-UI-451.6`) | **Test Spec ID**: `TST-UI-451` (`TST-UI-451.1`–`TST-UI-451.7`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (`REQ-UI-451`)

### 1.1 Attributes & Problem Statement
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- The Cockpit top bar (`SensorStatus`) renders 8 core sensor indicators, displaying active (`1.0f`) vs. inactive (`0.38f`) alpha and opening `SensorSourceDialog` on click. With Cycling Rearview Radar (`DeviceType.BIKE_RADAR`) integrated, Cockpit requires a dedicated radar status indicator and source inspection dialog fitted cleanly within 360 dp portrait viewports.

### 1.2 Functional & Architectural Requirements
1. `res/drawable/ic_radar.xml` SHALL define a canonical standalone 24dp vector drawable (concentric radar waves and center origin point).
2. `SensorType.getIconResId()` SHALL return `R.drawable.ic_radar` for `SensorType.RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, and `RADAR_VEHICLE_COUNT`.
3. `SensorStatus.kt` `sensorDefinitions` SHALL include `SensorType.RADAR_THREAT_COUNT` as the 9th sensor icon in portrait and landscape modes.
4. In portrait mode, horizontal padding per icon SHALL adjust to `4.dp` (`size(22.dp)` + `padding(horizontal = 4.dp)` = `30.dp` slot), ensuring 9 icons consume `270.dp <= 272.dp` available width on 360 dp screens without clipping.
5. `SensorSourceDialog.kt` for `RADAR_THREAT_COUNT` SHALL format the dialog title as localized "Bike Radar" (`R.string.device_type_bike_radar`), list `BIKE_RADAR` devices, and suppress scalar `valueWithUnit` on device rows.
6. Radar status and source dialog strings SHALL maintain 100% localization parity across all 9 supported languages.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* an athlete on a 360 dp portrait screen, *When* `SensorStatus` renders, *Then* all 9 sensor icons fit without clipping with 16 dp edge offset and 32 dp action buttons.
- *Given* radar hardware connected or simulator active, *When* the radar icon renders, *Then* it displays at `1.0f` alpha when active and `0.38f` alpha when inactive.
- *Given* an athlete tapping the radar icon, *When* `SensorSourceDialog` opens, *Then* title displays "Bike Radar", lists `BIKE_RADAR` devices without scalar value text, and clicks open `EditDeviceDialog`.

### Requirement Archaeology & Chesterton's Fence Audit
- *Original Requirement ID & Target*: Extending `REQ-UI-445.3` (*Live Cockpit Status Emblem*), targets `SensorStatus.kt`, `SensorSourceDialog.kt`, `SensorType.java`.
- *Historical Origin & Commit Trace*: Sprint 2026-45.4 (`ATT-5397`, commit `8baf42c8`), Sprint 2026-45.4 (`ATT-5422`, commit `fb6797a7`), and Sprint 2026-46.5 (`ATT-7544`).
- *Root Reason for Existing Formulation*: `REQ-UI-451` originally budgeted 272 dp for 8 legacy sensors (8 * 34 dp = 272 dp). Adding the 9th sensor for Cycling Rearview Radar (`DeviceType.BIKE_RADAR`) without padding adjustment would require 306 dp (> 272 dp) and cause clipping on 360 dp screens.
- *Preservation of Core Invariants*: 8 legacy sensors maintain 100% functional and visual fidelity; 32 dp symmetrical action buttons and 16 dp total display edge offsets are strictly preserved; central alignment and compact landscape mode remain preserved.

---

## 2. Test Specification (`TST-UI-451`)

### Test Cases
- `[TST-UI-451.1]` (Unit - `SensorStatusTopBarSpacingTest.kt`): Verify 16 dp total screen margin, 32 dp action buttons, 9 icons in `sensorDefinitions`, and 4 dp padding yielding 270 dp <= 272 dp width.
- `[TST-UI-451.2]` (Unit - `TrackingTabsLandscapeLayoutTest.kt`): Verify 9-icon compact landscape invariant (max 28 dp height).
- `[TST-UI-451.3]` (Unit - `SensorStatusLegibilityTest.kt`): Verify `INACTIVE_SENSOR_ALPHA = 0.38f` and WCAG AAA/AA contrast ratio compliance.
- `[TST-UI-451.4]` (Unit - `SensorStatusTopBarSpacingTest.kt`): Verify `ic_radar.xml` 24dp vector asset existence and `SensorType.RADAR_THREAT_COUNT.getIconResId()` mapping.
- `[TST-UI-451.5]` (Unit - `SensorSourceDialogContractTest.kt`): Verify localized "Bike Radar" title and scalar value suppression for `RADAR_THREAT_COUNT`.
- `[TST-UI-451.6]` (Unit - `SensorStatusTopBarSpacingTest.kt`): Verify 9-language translation parity for `device_type_bike_radar` and content descriptions.
- `[TST-UI-451.7]` (Tier 1 Regression): Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*" --tests "com.atrainingtracker.banalservice.sensor.*"` asserting 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component & Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-451.1]` | Unit | `SensorStatus.kt` (Portrait 9-icon spacing budget) | `REQ-UI-451.1`, `REQ-UI-451.4` | Specified |
| `[TST-UI-451.2]` | Unit | `SensorStatus.kt` (Landscape 9-icon layout) | `REQ-UI-451.3`, `REQ-UI-451.4` | Specified |
| `[TST-UI-451.3]` | Unit | `SensorStatus.kt` (Active/inactive alpha contrast) | `REQ-UI-451.4` | Specified |
| `[TST-UI-451.4]` | Unit | `SensorType.getIconResId`, `res/drawable/ic_radar.xml` | `REQ-UI-451.4` | Specified |
| `[TST-UI-451.5]` | Unit | `SensorSourceDialog.kt` (Title & scalar omission) | `REQ-UI-451.5` | Specified |
| `[TST-UI-451.6]` | Unit | `strings_radar.xml` (9-locale parity) | `REQ-UI-451.6` | Specified |
| `[TST-UI-451.7]` | Regression | `controltracking.*` and `banalservice.sensor.*` | `REQ-PRO-001` | Specified |
