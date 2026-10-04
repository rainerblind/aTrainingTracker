# Stage 2: Requirement & Test Specification - ATT-2382: Sport-Specific Equipment Sensor Matrix Partitioning and Incompatible Sensor Restriction

**Ticket**: [ATT-2382](https://rainerblind.atlassian.net/browse/ATT-2382)  
**Sub-task**: [ATT-2442](https://rainerblind.atlassian.net/browse/ATT-2442) (`[Req & Test Spec] Sport-Specific Equipment Sensor Matrix Partitioning and Incompatible Sensor Restriction`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-UI-256` (Refined requirement)  
**Test Mapping**: `TST-UI-230`  
**Branch**: `feature/ATT-2382`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Traceability Matrix

| Requirement ID | Requirement Title | Test Specification ID | Verification File(s) | Status |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-UI-256** | Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes | **TST-UI-230** | `EquipmentSportSensorCompatibilityTest.kt`, `EquipmentViewModelMatrixTest.kt`, `EquipmentSensorMatrixScreenTest.kt`, `EditEquipmentDialogContractTest.kt` | **In Progress** |

---

## 2. Requirement Specification: REQ-UI-256 (Refined)

### 2.1 Formal Definition
The system SHALL provide a centralized, interactive equipment-to-sensor mapping matrix with checkboxes in `EquipmentTabsScreen.kt`, partitioned into sport-specific tables to eliminate incompatible sensor clutter (ATT-2126, ATT-2382):

1. **Tab Integration (`EquipmentTabsScreen.kt`)**:
   - `EquipmentTabsScreen.kt` SHALL provide a 3-tab navigation structure: "Bikes" (`@string/equipment_type_bike`), "Shoes" (`@string/equipment_type_shoe`), and "Sensor Matrix" (`@string/equipment_tab_sensor_matrix`).
   - The floating action button (FAB) for adding new equipment SHALL only be rendered on the "Bikes" (page 0) and "Shoes" (page 1) tabs, and SHALL be suppressed on the "Sensor Matrix" (page 2) tab.

2. **Fleet-Wide Remote Sensor Aggregation & Domain Typing (`DevicesDatabaseManager.java` & `EquipmentViewModel.kt`)**:
   - `DevicesDatabaseManager.SimpleSensorInfo` SHALL encapsulate `id`, `name`, and `@Nullable DeviceType deviceType`.
   - `DevicesDatabaseManager.getAllRemoteSensors()` SHALL query `DEVICE_TYPE` from `Devices.db` and populate `deviceType` for all paired external sensors.
   - `DevicesDatabaseManager.getSensorsForSportType(BSportType sportType)` SHALL return sport-compatible sensors including universally shared sensors (`HRM` and `ENVIRONMENT`) for both `BSportType.BIKE` and `BSportType.RUN`.
   - `EquipmentViewModel` SHALL expose `allRemoteSensors`, `bikeSensors`, and `shoeSensors` as reactive `StateFlow` streams.

3. **Sport-Specific Matrix Architecture (`EquipmentSensorMatrixScreen.kt`)**:
   - The matrix SHALL partition sensors and equipment into two distinct, independent tables:
     - **Table 1: Bikes (`bikes`)**: Columns SHALL display strictly bike-compatible sensors (`BIKE_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`, `FITNESS_EQUIPMENT`, `RADAR`, `SHIFTING`) and shared sensors (`HRM`, `ENVIRONMENT`). Running-exclusive sensors (`RUN_SPEED` / Footpod) MUST NOT appear in the bike table. The table SHALL feature a dedicated sticky header and an independent horizontal scroll state (`bikeScrollState`).
     - **Table 2: Shoes (`shoes`)**: Columns SHALL display strictly run-compatible sensors (`RUN_SPEED` / Footpod, Running Dynamics) and shared sensors (`HRM`, `ENVIRONMENT`). Cycling-exclusive sensors (Power Meters, Bike Cadence/Speed) MUST NOT appear in the shoe table. The table SHALL feature a dedicated sticky header and an independent horizontal scroll state (`shoeScrollState`).
   - Horizontal scrolling in Table 1 SHALL NOT scroll or desynchronize Table 2, and vice-versa.
   - Sticky First Column: The equipment name and icon column SHALL remain sticky on the left during horizontal scrolling across sensor columns in each table.

4. **1-Tap Checkbox Persistence (`EquipmentDbHelper.java` & `EquipmentViewModel.kt`)**:
   - Each grid intersection cell SHALL display an interactive Material 3 `Checkbox`.
   - The checkbox SHALL be checked if and only if `equipmentItem.linkedDeviceIds.contains(sensor.id)`.
   - Tapping a checkbox SHALL trigger `EquipmentViewModel.setSensorLink(equipmentId, sensorId, !isCurrentlyLinked)`, updating SQLite table `LINKS` immediately within transactional helper `EquipmentDbHelper.setDeviceLink`, followed by a reactive reload of `bikes` and `shoes`.
   - Changes made in the matrix SHALL immediately synchronize with the single-item configuration bottom sheet (`EditEquipmentDialog`) and sensor settings (`EditDeviceDialog`).

5. **Single-Item Dialog Linkage Restriction (`EditEquipmentDialog.kt` & `EditDeviceDialog.kt`)**:
   - In `EditEquipmentDialog`, bikes SHALL only be offered bike-compatible and shared sensors; shoes SHALL only be offered run-compatible and shared sensors.
   - In `EditDeviceDialog`, sensors SHALL only offer compatible equipment types matching `deviceType.sportType` (cycling sensors to bikes only, running sensors to shoes only, shared sensors to both).

6. **Empty States & Edge Case Resilience**:
   - If zero remote sensors are paired across the fleet, an informative placeholder (`@string/equipment_matrix_no_sensors`) SHALL be displayed.
   - If zero equipment items exist, an informative empty state SHALL be displayed.
   - If a sport category has equipment but zero compatible sensors, an inline informational state SHALL indicate no compatible sensors are available.

7. **9-Language Localization Parity (`REQ-LOC-001`)**:
   - All newly introduced string resources SHALL be translated with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-UI-256` (*Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes*), targeting `EquipmentTabsScreen.kt`, `EquipmentSensorMatrixScreen.kt`, `EquipmentViewModel.kt`, and `DevicesDatabaseManager.java`.
2. *Historical Origin & Commit Trace*: Commit `fc10ad81` (Sprint `2026-40.16`), ticket `ATT-2126`.
3. *Root Reason for Existing Formulation*: In ATT-2126, a monolithic table was created to establish the central matrix concept. However, practical usage revealed that displaying power meters on shoes and footpods on bikes created visual noise and nonsensical mapping options.
4. *Preservation of Core Invariants*: 1-tap Checkbox persistence, bidirectional sync with single-item dialogs, sticky columns/headers, fast scrolling, and 9-language localization parity MUST be preserved. Bike sensors and shoe sensors are cleanly partitioned into two sport-specific tables, with shared sensors (HRM, Temperature) appearing in both.

### 2.3 Acceptance Criteria (Given-When-Then)
- **AC-1 (Sport-Specific Matrix Partitioning)**:
  - *Given* configured bikes and shoes, and paired remote sensors (e.g. Stages Power, Wahoo Speed, Stryd Footpod, Garmin HRM),
  - *When* the athlete navigates to the "Sensor Matrix" tab in `EquipmentTabsScreen`,
  - *Then* two separate tables SHALL be displayed: Table 1 (Fahrräder) containing Stages Power, Wahoo Speed, and Garmin HRM; and Table 2 (Schuhe) containing Stryd Footpod and Garmin HRM.
- **AC-2 (Independent Table Scrolling)**:
  - *Given* Table 1 (Bikes) and Table 2 (Shoes),
  - *When* the athlete scrolls Table 1 horizontally across bike sensors,
  - *Then* Table 2 SHALL NOT scroll and SHALL maintain its own scroll position independently.
- **AC-3 (1-Tap Checkbox Persistence & Bidirectional Sync)**:
  - *Given* a bike sensor row in Table 1,
  - *When* the athlete taps an unlinked checkbox,
  - *Then* the checkbox immediately toggles to checked, SQLite table `LINKS` is updated, and opening `EditEquipmentDialog` for that bike shows the sensor selected.
- **AC-4 (Single-Item Dialog Incompatible Sensor Restriction)**:
  - *Given* `EditEquipmentDialog` opened for a running shoe,
  - *When* viewing available sensors,
  - *Then* only running-compatible and shared sensors SHALL appear, and cycling-exclusive power meters/cadence sensors SHALL NOT be selectable.
- **AC-5 (Empty States)**:
  - *Given* zero paired remote sensors,
  - *When* viewing the "Sensor Matrix" tab,
  - *Then* an informative empty state placeholder is displayed.

---

## 3. Test Specification: TST-UI-230

### 3.1 Verification Scope
The test suite validates sport-specific sensor categorization, database queries, ViewModel reactive StateFlows, UI table partitioning, independent scroll mechanics, single-item dialog filtering, and localization parity.

### 3.2 Test Cases

#### Case 1: Sport-Specific Sensor Compatibility (`EquipmentSportSensorCompatibilityTest.kt`)
- **Objective**: Verify that `DevicesDatabaseManager` filters sensors accurately by sport type.
- **Setup**: Paired sensors: `BIKE_POWER`, `BIKE_SPEED`, `BIKE_CADENCE`, `RUN_SPEED`, `HEART_RATE`, `ENVIRONMENT`.
- **Execution & Assertions**:
  - `getSensorsForSportType(BSportType.BIKE)` returns Power, Speed, Cadence, HRM, Environment; excludes `RUN_SPEED`.
  - `getSensorsForSportType(BSportType.RUN)` returns Footpod (`RUN_SPEED`), HRM, Environment; excludes Power, Speed, Cadence.

#### Case 2: ViewModel Partitioned StateFlows (`EquipmentViewModelMatrixTest.kt`)
- **Objective**: Verify `EquipmentViewModel` provides distinct `bikeSensors` and `shoeSensors` flows.
- **Execution & Assertions**:
  - Check that `bikeSensors` contains strictly bike-compatible and shared sensors.
  - Check that `shoeSensors` contains strictly run-compatible and shared sensors.
  - Verify toggling links correctly updates the respective equipment items.

#### Case 3: Matrix UI Partitioning & Independent Scroll (`EquipmentSensorMatrixScreenTest.kt`)
- **Objective**: Verify that `EquipmentSensorMatrixScreen` renders two independent tables with independent horizontal scroll states.
- **Execution & Assertions**:
  - Two tables are rendered with sticky headers and sticky left columns.
  - Scrolling Table 1 horizontally does not displace Table 2.

#### Case 4: Single-Item Dialog Compatibility Filtering (`EditEquipmentDialogContractTest.kt`)
- **Objective**: Verify that `EditEquipmentDialog` restricts selectable sensors by equipment type.
- **Execution & Assertions**:
  - Bike dialog only lists bike-compatible and shared sensors.
  - Shoe dialog only lists run-compatible and shared sensors.

#### Case 5: 9-Language Localization Audit
- **Objective**: Verify 100% string resource parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

#### Case 6: Clean-Room Full Suite Regression
- **Objective**: Execute `./gradlew testDebugUnitTest` across all modules with 0 failures.
