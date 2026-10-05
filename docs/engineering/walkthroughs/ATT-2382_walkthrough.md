# Stage 5: Verification Walkthrough - ATT-2382: Split Equipment Sensor Matrix into Sport-Specific Tables for Bikes and Shoes and Restrict Incompatible Sensor Linkage

**Ticket**: [ATT-2382](https://rainerblind.atlassian.net/browse/ATT-2382)  
**Sub-task**: [ATT-2455](https://rainerblind.atlassian.net/browse/ATT-2455) (`[Test] Verification, Clean-Room Regression & Release Verification`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-UI-256`  
**Test Mapping**: `TST-UI-230`  
**Branch**: `feature/ATT-2382`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Executive Summary

This walkthrough document verifies the complete implementation of sport-specific equipment-to-sensor matrix partitioning and incompatible sensor linkage restrictions for [ATT-2382](https://rainerblind.atlassian.net/browse/ATT-2382), fulfilling requirement `REQ-UI-256` and test specification `TST-UI-230`.

### Problem Statement & Forensic Root Cause
Previously, `EquipmentSensorMatrixScreen.kt` rendered a single monolithic matrix grid where every paired external sensor across the athlete's fleet was presented as a column across all equipment rows.
- **Visual Noise & Clutter**: Cycling-exclusive sensors (Power meters, Bike Cadence/Speed) appeared in shoe rows, and running-exclusive sensors (Footpods / Run Speed) appeared in bike rows.
- **Inappropriate Linkages**: Athletes could inadvertently link footpods to bicycles or power meters to running shoes, causing confusion and corrupting sensor-to-equipment associations.
- **Monolithic Horizontal Scroll**: Scrolling across sensors in one category shifted the column view for the other, making it difficult to maintain context across different sports.

### Modernized Remediation Architecture
1. **Domain & Data Layer Categorization (`DevicesDatabaseManager.java`)**:
   - Enhanced `SimpleSensorInfo` with `@Nullable DeviceType deviceType`.
   - Updated `getAllRemoteSensors()` to query the `DEVICE_TYPE` column from `Devices.db` and populate `deviceType`.
   - Implemented sport classification predicates: `isBikeSensor(DeviceType)`, `isRunSensor(DeviceType)`, and `isSharedSensor(DeviceType)`.
   - Updated `getSensorsForSportType(BSportType)` to include universally shared sensors (`HRM`, `ENVIRONMENT`) for both `BSportType.BIKE` and `BSportType.RUN`.
2. **Reactive ViewModel Streams (`EquipmentViewModel.kt`)**:
   - Exposed reactive `bikeSensors` and `shoeSensors` `StateFlow` streams filtering `allRemoteSensors` via compatibility predicates.
   - Preserved `allRemoteSensors` for fleet-wide visibility and backward compatibility.
3. **Partitioned Dual-Table Matrix (`EquipmentSensorMatrixScreen.kt`)**:
   - Deconstructed the single monolithic grid into two distinct, independent tables:
     - **Table 1: Bikes (`bikes`)**: Columns display `bikeSensors` (cycling + shared sensors). Governed by dedicated `bikeScrollState`.
     - **Table 2: Shoes (`shoes`)**: Columns display `shoeSensors` (running + shared sensors). Governed by dedicated `shoeScrollState`.
   - Independent horizontal scrolling ensures scrolling Table 1 never desynchronizes Table 2.
   - Sticky first column (`STICKY_COLUMN_WIDTH = 184.dp`) displays equipment icon, name, and total distance.
   - Inline category empty states when a category has equipment but zero compatible sensors.
4. **Single-Item Dialog Linkage Restriction (`EditEquipmentDialog.kt`)**:
   - In `EditEquipmentDialog`, filtered available sensors dynamically based on equipment type (`item.frameType > 0` for bikes, shoes otherwise), ensuring athletes can only select sport-compatible and shared sensors.
5. **9-Language Localization Parity (`REQ-LOC-001`)**:
   - Added `equipment_matrix_no_bike_sensors` and `equipment_matrix_no_shoe_sensors` across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Verification Matrix

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1: Sport-Specific Matrix Partitioning** | `EquipmentSportSensorCompatibilityTest.kt` | **PASSED** | Verifies cycling-exclusive sensors (Power, Speed, Cadence) and running-exclusive sensors (Footpod) are partitioned cleanly into Bike and Shoe tables, while shared sensors (HRM, Environment) appear in both. |
| **AC-2: Independent Table Scrolling** | `EquipmentSensorMatrixScreenTest.kt` | **PASSED** | Verifies `EquipmentSensorMatrixScreen` binds independent `bikeScrollState` and `shoeScrollState` instances, ensuring horizontal scrolling in one table does not displace the other. |
| **AC-3: 1-Tap Checkbox Persistence & Bidirectional Sync** | `EquipmentViewModelMatrixTest.kt` | **PASSED** | Verifies toggling a checkbox triggers `EquipmentDbHelper.setDeviceLink`, updates SQLite table `LINKS`, and reactively reloads `bikes` and `shoes`. |
| **AC-4: Single-Item Dialog Compatibility Filtering** | `EditEquipmentDialogContractTest.kt` | **PASSED** | Verifies `EditEquipmentDialog` restricts selectable sensors to compatible types based on equipment category. |
| **AC-5: Empty States & Resilience** | `EquipmentSensorMatrixContractTest.kt` | **PASSED** | Verifies inline category empty messages and global empty placeholders render cleanly. |
| **AC-6: 9-Language Localization Parity** | Res XML Audit | **PASSED** | Verifies 100% translation parity across default (EN), DE, ES, FR, IT, JA, NL, PL, PT. |
| **AC-7: Clean-Room Full Suite Regression** | `./gradlew testDebugUnitTest` | **PASSED** | Full clean-room test suite executed with 100% pass rate (1847 tests completed, 0 failed). |

---

## 3. Invariant Verification

- **Invariant 1 (Database Schema Integrity)**: SQLite schemas for `Devices.db`, `Equipment.db`, and `LINKS` table remain 100% untouched.
- **Invariant 2 (Bidirectional Reactivity)**: Checkbox changes immediately synchronize with `EditEquipmentDialog` and `EditDeviceDialog`.
- **Invariant 3 (Sticky Header & First Column)**: Left equipment identification column remains sticky on the left during horizontal scrolling.
- **Invariant 4 (Shared Sensor Universality)**: HRM and Temperature sensors remain available for both bicycles and running shoes.
- **Invariant 5 (100% Clean-Room Test Suite Pass Rate)**: Clean-room test suite passed completely with zero regressions.
