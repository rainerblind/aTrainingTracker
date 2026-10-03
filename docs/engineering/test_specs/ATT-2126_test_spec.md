# Stage 2: Requirement & Test Specification - ATT-2126: Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes

**Ticket**: [ATT-2126](https://rainerblind.atlassian.net/browse/ATT-2126)  
**Sub-task**: [ATT-2206](https://rainerblind.atlassian.net/browse/ATT-2206) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-2126`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (`REQ-UI-256`)

| Field | Specification |
| :--- | :--- |
| **Requirement ID** | `REQ-UI-256` |
| **Title** | **Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes.** |
| **Category** | Functional / UI / User Experience |
| **Scope** | `EquipmentTabsScreen.kt`, `EquipmentSensorMatrixScreen.kt`, `EquipmentViewModel.kt`, `EquipmentDbHelper.java`, `DevicesDatabaseManager.java` |
| **Status** | Specified |

### Clause Breakdown & Architectural Constraints

1. **Tab Integration (`EquipmentTabsScreen.kt`)**:
   - `EquipmentTabsScreen.kt` SHALL provide a 3-tab navigation structure: "Bikes" (`@string/equipment_type_bike`), "Shoes" (`@string/equipment_type_shoe`), and "Sensor Matrix" (`@string/equipment_tab_sensor_matrix`).
   - The floating action button (FAB) for adding new equipment SHALL only be rendered on the "Bikes" (page 0) and "Shoes" (page 1) tabs, and SHALL be suppressed on the "Sensor Matrix" (page 2) tab to maintain an uncluttered data-entry grid.
2. **Fleet-Wide Remote Sensor Aggregation (`DevicesDatabaseManager.java` & `EquipmentViewModel.kt`)**:
   - `DevicesDatabaseManager` SHALL provide `getAllRemoteSensors()`, returning all paired and named external devices (supporting `ANT_PLUS` and `BLUETOOTH_LE` protocols, including HRM, Bike Power, Speed, Cadence, Run Speed, and Environmental sensors), sorted alphabetically by name.
   - `EquipmentViewModel` SHALL expose `allRemoteSensors` as a reactive `StateFlow<List<SimpleSensorInfo>>`.
3. **Matrix Grid Architecture (`EquipmentSensorMatrixScreen.kt`)**:
   - **Columns**: Each remote sensor returned by `allRemoteSensors` SHALL form a column with its name in the header row.
   - **Rows**: Equipment items SHALL be displayed in two distinct sections: 🚴 **Bikes** (`bikes`) and 👟 **Shoes** (`shoes`).
   - **Sticky First Column**: The equipment name and icon column SHALL remain sticky on the left during horizontal scrolling across sensor columns.
   - **Sticky Header Row**: The sensor names header row SHALL remain sticky at the top during vertical scrolling through equipment rows.
4. **1-Tap Checkbox Persistence (`EquipmentDbHelper.java` & `EquipmentViewModel.kt`)**:
   - Each grid intersection cell SHALL display an interactive Material 3 `Checkbox`.
   - The checkbox SHALL be checked if and only if `equipmentItem.linkedDeviceIds.contains(sensor.id)`.
   - Tapping a checkbox SHALL trigger `EquipmentViewModel.setSensorLink(equipmentId, sensorId, !isCurrentlyLinked)`, updating SQLite table `LINKS` immediately within a transactional helper method `EquipmentDbHelper.setDeviceLink`, followed by a reactive reload of `bikes` and `shoes`.
   - Changes made in the matrix SHALL immediately synchronize with the single-item configuration bottom sheet (`EditEquipmentDialog`) without requiring an app restart or navigation reload.
5. **Empty States & Edge Case Resilience**:
   - If zero remote sensors are paired in the database, `EquipmentSensorMatrixScreen` SHALL display an informative placeholder (`@string/equipment_matrix_no_sensors`) guiding the athlete to pair sensors.
   - If zero equipment items exist, an informative empty state SHALL be displayed.
6. **9-Language Localization Parity (`REQ-LOC-001`)**:
   - All newly introduced string resources (`@string/equipment_tab_sensor_matrix`, `@string/equipment_matrix_no_sensors`, `@string/equipment_matrix_no_equipment`) SHALL be translated with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Net-new requirement (`REQ-UI-256`), extending `REQ-UI-158` (*Equipment Management*) and `REQ-UI-160` (*Equipment-to-Sensor Mapping*) under Epic `ATT-355` (*Good and consistent UI*).
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.1 / Sprint 2026-40.2 (commits establishing `EquipmentTabsScreen`, `EditEquipmentDialog`, and `EquipmentDbHelper`).
3. **Root Reason for Existing Formulation**:
   - Sensor mapping was initially attached exclusively to the equipment editing modal bottom sheet (`EditEquipmentDialog`) because early designs focused on creating and updating individual bikes or shoes. As athletes accrued multiple bikes (e.g. Road, MTB, Gravel) and multiple sensors (Powermeter, HR monitor, Speed sensor), configuring equipment in silos became inefficient.
4. **Preservation of Core Invariants**:
   - **Database Schema Integrity**: Preserves existing `Equipment` and `Links` SQLite tables without schema migrations.
   - **Bidirectional Consistency**: Changes made in the matrix immediately reflect in the single-equipment dialog and vice versa.
   - **Touch Target Accessibility**: Checkbox cells adhere to Material 3 accessibility guidelines.
   - **Collapsing Header Synergy**: Integrates cleanly into `EquipmentTabsScreen` with `CollapsingAppBarNestedScrollConnection`.
   - **9-Language Localization Parity (`REQ-LOC-001`)**: All new UI strings fully translated in EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 3. Given-When-Then Acceptance Criteria

* **AC-1 (Matrix Presentation by Section)**:
  * **Given** configured bikes and shoes, and paired remote sensors (e.g. Stages Power, Garmin HRM, Wahoo Speed),
  * **When** the athlete navigates to the "Sensor Matrix" tab in `EquipmentTabsScreen`,
  * **Then** all remote sensors appear as columns and all bikes and shoes appear in their respective sections (🚴 Bikes, 👟 Shoes) with interactive checkboxes.
* **AC-2 (1-Tap Checkbox Persistence & Bidirectional Sync)**:
  * **Given** an unlinked sensor in the matrix row for a specific bike,
  * **When** the athlete taps the checkbox,
  * **Then** the checkbox immediately transitions to checked, SQLite table `LINKS` is updated via `EquipmentDbHelper.setDeviceLink`, and opening `EditEquipmentDialog` for that bike shows the sensor selected.
* **AC-3 (Sticky Header & First Column Scrolling)**:
  * **Given** a fleet with many sensors exceeding the device screen width,
  * **When** the athlete scrolls horizontally,
  * **Then** the left equipment column (icon and name) remains sticky while sensor columns scroll smoothly.
* **AC-4 (Zero-Sensor Empty State)**:
  * **Given** a fresh install or an environment with zero paired remote sensors,
  * **When** the athlete opens the "Sensor Matrix" tab,
  * **Then** an informative empty state placeholder (`@string/equipment_matrix_no_sensors`) is displayed with zero crashes or layout distortions.

---

## 4. Test Case Specifications (`TST-UI-215`)

| Test Spec ID | Target Component | Description / Assertion | Verification Type |
| :--- | :--- | :--- | :--- |
| `TST-UI-215-A` | `EquipmentDbHelper` | Verify `addDeviceLink`, `removeDeviceLink`, and `setDeviceLink` accurately modify rows in SQLite `LINKS` table idempotently. | Unit / DB Contract Test |
| `TST-UI-215-B` | `DevicesDatabaseManager` | Verify `getAllRemoteSensors()` returns all paired ANT+ and BLE devices while excluding phone-internal sensors. | Unit / DB Contract Test |
| `TST-UI-215-C` | `EquipmentViewModel` | Verify `allRemoteSensors` flow emits sensor list, and `setSensorLink` triggers DB persistence and list reload reactively. | ViewModel Unit Test |
| `TST-UI-215-D` | `EquipmentTabsScreen` | Structural contract test verifying 3 tabs defined, FAB hidden on page 2, and `EquipmentSensorMatrixScreen` slotted. | UI Contract Test |
| `TST-UI-215-E` | `EquipmentSensorMatrixScreen` | Verify sticky column, section headers (Bikes, Shoes), and checkbox state binding. | UI Contract Test |
| `TST-UI-215-F` | Localization | 9-language audit across EN, DE, ES, FR, IT, JA, NL, PL, PT for all new string keys. | Localization Parity Audit |
| `TST-UI-215-G` | Full Regression | Clean-room `./gradlew testDebugUnitTest` verifying 100% pass rate. | Clean-Room Suite |

---

## 5. Traceability Matrix

| Requirement Clause | Test Case ID | Target Artifact / Test File |
| :--- | :--- | :--- |
| `REQ-UI-256.1` (3 Tabs & FAB Gating) | `TST-UI-215-D` | `EquipmentTabsScreenContractTest.kt` |
| `REQ-UI-256.2` (Remote Sensor Discovery) | `TST-UI-215-B`, `TST-UI-215-C` | `DevicesDatabaseManagerSensorsTest.kt`, `EquipmentViewModelTest.kt` |
| `REQ-UI-256.3` (Matrix Grid Architecture) | `TST-UI-215-E` | `EquipmentSensorMatrixContractTest.kt` |
| `REQ-UI-256.4` (1-Tap Checkbox Persistence) | `TST-UI-215-A`, `TST-UI-215-C` | `EquipmentDbLinkContractTest.kt`, `EquipmentViewModelTest.kt` |
| `REQ-UI-256.5` (Empty States) | `TST-UI-215-E` | `EquipmentSensorMatrixContractTest.kt` |
| `REQ-UI-256.6` (9-Language Parity) | `TST-UI-215-F` | XML String Audit across 9 Locales |
