# Stage 5: Walkthrough & Verification - ATT-2126: Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes

**Ticket**: [ATT-2126](https://rainerblind.atlassian.net/browse/ATT-2126)  
**Sub-task**: [ATT-2209](https://rainerblind.atlassian.net/browse/ATT-2209) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Requirement Mapping**: `REQ-UI-256` (*Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes*)  
**Test Spec Mapping**: `TST-UI-215` (*Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes Verification*)  
**Branch**: `feature/ATT-2126`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Executive Summary & Verification Overview

Previously, mapping external sensors (heart rate monitors, bike speed/cadence, power meters, foot pods, environmental sensors) to bikes and shoes required opening individual equipment items in isolated configuration dialogs (`EditEquipmentDialog`). Athletes managing multiple bikes and sensors lacked a single fleet-wide overview, making it cumbersome to inspect and adjust sensor assignments across gear.

### Forensic Implementation & Architectural Solution
1. **Interactive 2D Grid Matrix (`EquipmentSensorMatrixScreen.kt`)**:
   - Implemented a responsive 2D matrix featuring a sticky equipment column (width 156dp) displaying gear icon (`ic_equipment_bike`, `ic_equipment_shoe`), equipment name (with single-line ellipsis), and retirement badge (`@string/equipment_retired`).
   - Sticky top header row (height 60dp) displaying all paired external sensor names.
   - Synchronized horizontal scrolling across all sensor column cells and headers via shared `rememberScrollState()`.
   - Distinct vertically scrollable sections for 🚴 **Bikes** and 👟 **Shoes**, complete with section item counters.
   - Responsive 1-tap Material 3 `Checkbox` at each grid intersection, maintaining accessible touch targets (>=48dp).
2. **Atomic SQLite Link Operations (`EquipmentDbHelper.java`)**:
   - Added atomic, transactional methods `addDeviceLink(equipmentId, deviceId)`, `removeDeviceLink(equipmentId, deviceId)`, and `setDeviceLink(equipmentId, deviceId, isLinked)` targeting the `LINKS` table (`EquipmentId`, `ANTDeviceId`).
   - Built idempotent existence checks preventing duplicate link records.
3. **Fleet-Wide Sensor Discovery (`DevicesDatabaseManager.java`)**:
   - Added `getAllRemoteSensors(): List<SimpleSensorInfo>`, querying the `Devices` table for all user-named external ANT+ and Bluetooth LE sensors while strictly filtering out internal phone sensors.
   - Alphabetically sorts sensors case-insensitively (`COLLATE NOCASE ASC`).
4. **Reactive State Dispatch (`EquipmentViewModel.kt`)**:
   - Exposed `allRemoteSensors: StateFlow<List<SimpleSensorInfo>>`.
   - Added `setSensorLink(equipmentId, sensorId, isLinked)` executing asynchronously on `Dispatchers.IO` and sequentially triggering `loadEquipment()`, ensuring instant bidirectional synchronization between the matrix and single-item edit sheets.
5. **Navigation & FAB Gating (`EquipmentTabsScreen.kt`)**:
   - Extended `HorizontalPager` to 3 tabs: "Bikes" (`@string/equipment_type_bike`), "Shoes" (`@string/equipment_type_shoe`), and "Sensor Matrix" (`@string/equipment_tab_sensor_matrix`).
   - Gated the Floating Action Button to ensure it renders exclusively on tabs 0 and 1, suppressing it on the Sensor Matrix tab (tab 2).
6. **9-Language Localization Parity (`REQ-LOC-001`)**:
   - 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, and PT for all newly introduced string resources (`equipment_tab_sensor_matrix`, `equipment_matrix_no_sensors`, `equipment_matrix_no_equipment`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-256` | `TST-UI-215.1` | Unit Contract Test: `EquipmentDbLinkContractTest.kt` (atomic link insert/delete idempotency, invalid ID handling, `getAllRemoteSensors` query selection and sort order) | **PASSED** (7/7 tests) | `Verified` |
| `REQ-UI-256` | `TST-UI-215.2` | Unit State Test: `EquipmentViewModelMatrixTest.kt` (`allRemoteSensors` emission, `setSensorLink` reactivity, bidirectional synchronization) | **PASSED** (2/2 tests) | `Verified` |
| `REQ-UI-256` | `TST-UI-215.3` | Architectural Contract Test: `EquipmentSensorMatrixContractTest.kt` (3-tab pager integration, FAB suppression on page 2, sticky column & header contract, 9-locale parity) | **PASSED** (3/3 tests) | `Verified` |
| `REQ-LOC-001` | `TST-UI-215.4` | 9-Language Localization Audit across EN, DE, ES, FR, IT, JA, NL, PL, PT | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-215.5` | Full Clean-Room Regression Test Suite (`./gradlew testDebugUnitTest`) | **PASSED** (1489/1489 tests, 0 failures, 0 errors) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Tests
```text
> Task :app:testDebugUnitTest
EquipmentDbLinkContractTest > testSetDeviceLink_delegatesCorrectly PASSED
EquipmentDbLinkContractTest > testAddDeviceLink_whenAlreadyPresent_skipsInsert PASSED
EquipmentDbLinkContractTest > testRemoveDeviceLink_validIds_deletesMatchingRow PASSED
EquipmentDbLinkContractTest > testAddDeviceLink_whenNotPresent_insertsRow PASSED
EquipmentDbLinkContractTest > testAddDeviceLink_invalidIds_noop PASSED
EquipmentDbLinkContractTest > testGetAllRemoteSensors_queriesWithExpectedSelectionAndSortOrder PASSED
EquipmentDbLinkContractTest > testRemoveDeviceLink_invalidIds_noop PASSED
EquipmentViewModelMatrixTest > testAllRemoteSensors_initializationAndEmission PASSED
EquipmentViewModelMatrixTest > testSetSensorLink_invokesDbHelperAndReloadsEquipment PASSED
EquipmentSensorMatrixContractTest > testEquipmentTabsScreen_threeTabsAndFabGatingContract PASSED
EquipmentSensorMatrixContractTest > testEquipmentSensorMatrixScreen_structuralContract PASSED
EquipmentSensorMatrixContractTest > testLocalizationParity_allNineLocalesMustContainNewStringKeys PASSED
BUILD SUCCESSFUL in 17s
```

### Full Clean-Room Regression Suite
```text
BUILD SUCCESSFUL in 4m 32s
32 actionable tasks: 12 executed, 20 up-to-date
Total Test Suites: 290
Total Tests Executed: 1489
Failures: 0
Errors: 0
```

---

## 4. Hardware / Physical Verification (Pixel 10)

- Navigated to Equipment Management screen. Verified 3 tabs: "Bikes", "Shoes", and "Sensor Matrix".
- Switching to "Sensor Matrix" tab seamlessly displays the fleet-wide grid.
- Floating Action Button ("+") is visible on "Bikes" and "Shoes" tabs, and cleanly suppressed on "Sensor Matrix" tab.
- Horizontal scrolling smoothly moves across sensor columns while the left equipment column (icon and name) remains pinned to the left edge.
- Vertical scrolling scrolls equipment items while the sensor name header remains pinned at the top.
- Tapping a checkbox toggles the sensor link immediately, updating the database and reflecting across tabs.
- Switching to "Bikes" tab and opening the single-item configuration dialog confirms the toggled sensor is checked.
- Empty states tested: Displays informative placeholder when no remote sensors or equipment exist.

---

## 5. Invariant & Governance Verification

1. **Direct SQLite Schema Stability**: `Equipment.db` table schemas (`Equipment`, `LINKS`) remain unchanged; zero schema migration needed.
2. **Bidirectional Synchronization**: Modifications made via the matrix reflect instantly in single-item dialogs (`EditEquipmentDialog`) and vice versa.
3. **Data Safety**: Single-link operations operate transactionally without impacting unaffected equipment or sensors.
4. **Touch Accessibility**: Checkboxes provide touch targets >=48dp.
5. **9-Language Parity (`REQ-LOC-001`)**: 100% translation coverage across EN, DE, ES, FR, IT, JA, NL, PL, PT.
6. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-UI-256`) and `docs/tests.md` (`TST-UI-215`) updated to `Verified`.
7. **Subtask Completion**: Stage 5 subtask (`ATT-2209`) transitioned to `Erledigt` via transition `freigabe` upon Gate 5 approval.
8. **Parent Ticket Final Review**: Parent ticket `ATT-2126` transitioned to `Final Review (Human)` and assigned to `human` for human sign-off.
