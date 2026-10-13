# Stage 1 Analysis: ATT-2464 - Order equipment sensor matrix columns with sport-specific sensors first, then shared sensors

**Ticket**: [ATT-2464](https://rainerblind.atlassian.net/browse/ATT-2464)  
**Sub-task**: [ATT-2601](https://rainerblind.atlassian.net/browse/ATT-2601) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2464`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-40.16 Joint Review on physical test hardware (Pixel 10), the human tester/developer evaluated the sport-partitioned equipment-to-sensor matrix introduced in `ATT-2382` (`REQ-UI-256`). While the partitioning into Bikes and Shoes tables successfully eliminated cross-sport clutter (e.g. footpods on bikes or power meters on shoes), the column ordering within each table remained purely alphabetical by sensor name.

**Human Observation**:
> *"For the bike, the ride specific sensors must come first, then the generic ones (like HR). Similar for the shoes."*

### Current Deficiencies:
1. **Alphabetical Sorting Obscures Primary Telemetry**: When sensors are sorted purely alphabetically (`NAME COLLATE NOCASE ASC`), generic/shared sensors (such as "Garmin HRM-Dual" or "Tempe") frequently precede core sport-specific sensors (such as "Stages Power" or "Wahoo SPEED").
2. **Inconsistent Scanning Experience**: Athletes scanning the equipment matrix expect to verify primary powertrain and movement telemetry first (power, speed, cadence for bikes; footpod / run speed for shoes) before checking auxiliary metrics like heart rate or ambient temperature.
3. **Dialog Inconsistency**: `EditEquipmentDialog` presents available sensors in an unranked multi-select dropdown, inheriting the alphabetical order rather than prioritizing sport-specific hardware.

The goal of `ATT-2464` is to establish deterministic, sport-prioritized column ordering for the equipment-sensor matrix and sensor configuration dialogs: sport-specific sensors first, followed by shared/generic sensors, with standardized secondary sorting.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Sensor List Aggregation in `DevicesDatabaseManager.java`
* `DevicesDatabaseManager.java` contains `getSensorsForSportType(BSportType sportType)` and `getAllRemoteSensors()`.
* In both methods, the database query executes:
  ```sql
  Cursor cursor = getDatabase().query(DevicesDbHelper.DEVICES,
          new String[]{DevicesDbHelper.C_ID, DevicesDbHelper.NAME, DevicesDbHelper.DEVICE_TYPE},
          selection,
          null, null, null, DevicesDbHelper.NAME + " COLLATE NOCASE ASC");
  ```
* While SQLite sorts by name alphabetically, it does not apply any domain ranking by `deviceType`.
* As a result, `getSensorsForSportType(BSportType.BIKE)` returns "Garmin HRM-Dual" before "Stages Power" simply due to letter 'G' vs 'S'.

### 2.2 ViewModel Emission in `EquipmentViewModel.kt`
* In `EquipmentViewModel.kt`:
  ```kotlin
  val remoteSensors = dbDevicesHelper.allRemoteSensors
  _allRemoteSensors.value = remoteSensors
  _bikeSensors.value = remoteSensors.filter {
      DevicesDatabaseManager.isBikeSensor(it.deviceType) || DevicesDatabaseManager.isSharedSensor(it.deviceType)
  }
  _shoeSensors.value = remoteSensors.filter {
      DevicesDatabaseManager.isRunSensor(it.deviceType) || DevicesDatabaseManager.isSharedSensor(it.deviceType)
  }
  ```
* Kotlin's `Iterable.filter` preserves the original iteration order. Because `allRemoteSensors` is alphabetical, `_bikeSensors` and `_shoeSensors` retain purely alphabetical ordering.
* Fallback properties `bikeSensorsList` and `runSensorsList` delegate to `dbDevicesHelper.getSensorsForSportType(BSportType)`, which also returns alphabetical results.

### 2.3 UI Consumption in `EquipmentSensorMatrixScreen.kt` and `EditEquipmentDialog.kt`
* `EquipmentSensorMatrixScreen.kt` iterates directly over `effectiveBikeSensors` and `effectiveShoeSensors` to build table column headers.
* `EditEquipmentDialog.kt` filters `availableSensors` for compatibility but performs no sorting, preserving whatever list order is passed from `EquipmentTabsScreen.kt`.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Objectives:
1. **Domain-Prioritized Sensor Ordering Specification**:
   - **Bikes (`BSportType.BIKE`)**:
     1. Cycling-specific sensors first:
        - `BIKE_POWER` (Priority 10)
        - `BIKE_SPEED_AND_CADENCE` (Priority 20)
        - `BIKE_SPEED` (Priority 30)
        - `BIKE_CADENCE` (Priority 40)
        - Other bike sensors (Priority 50)
     2. Shared / Generic sensors second:
        - `HRM` (Priority 100)
        - `ENVIRONMENT` (Priority 110)
        - Other shared sensors (Priority 120)
     3. Secondary sort within priority tier: Case-insensitive alphabetical by `name`, tie-broken by `id` ascending.
   - **Shoes (`BSportType.RUN`)**:
     1. Running-specific sensors first:
        - `RUN_SPEED` (Footpod / Run speed & distance, Priority 10)
        - Other run sensors (Priority 20)
     2. Shared / Generic sensors second:
        - `HRM` (Priority 100)
        - `ENVIRONMENT` (Priority 110)
        - Other shared sensors (Priority 120)
     3. Secondary sort within priority tier: Case-insensitive alphabetical by `name`, tie-broken by `id` ascending.
2. **Centralized Ordering Utility**:
   - Provide `EquipmentSensorOrdering` (or helper methods in `DevicesDatabaseManager` / `EquipmentViewModel`) ensuring identical comparator logic across `EquipmentViewModel`, `DevicesDatabaseManager`, `EditEquipmentDialog`, and `EquipmentSensorMatrixScreen`.
3. **Database Query & ViewModel Alignment**:
   - Ensure `DevicesDatabaseManager.getSensorsForSportType` applies this deterministic ordering.
   - Ensure `EquipmentViewModel._bikeSensors` and `_shoeSensors` apply this ordering.
4. **Dialog Consistency**:
   - Ensure `EditEquipmentDialog` presents sensors according to the same sport-specific hierarchy.
5. **Unit Verification Suite**:
   - Update `EquipmentViewModelMatrixTest.kt` to assert the prioritized sensor ordering.
   - Create comprehensive unit tests for `EquipmentSensorOrdering` verifying all sensor combinations, tie-breaking, and null deviceType resilience.

### Out-of-Scope Non-Goals (Scope Bounding):
* Do not modify `getAllRemoteSensors()` flat alphabetical ordering, as it serves global un-partitioned sensor inventory views.
* Do not alter SQLite schema or table definitions in `Devices.db`, `Equipment.db`, or `Links.db`.
* Do not alter the 1-tap checkbox persistence logic or bidirectional synchronization (`REQ-UI-257`).
* Do not modify UI layout dimensions, colors, or fonts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-256` (*Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes*), targeting `EquipmentSensorMatrixScreen.kt`, `EquipmentViewModel.kt`, `DevicesDatabaseManager.java`, and `EditEquipmentDialog.kt`.
* **Historical Origin & Commit Trace**:
  - `ATT-2126` (commit `fc10ad81`, Sprint `2026-40.16`): Created initial monolithic equipment-sensor matrix.
  - `ATT-2382` (commit `001d9326`, Sprint `2026-40.16`): Partitioned the matrix into Bikes and Shoes tables, filtering incompatible sensors.
* **Root Reason for Existing Formulation**: `ATT-2382` addressed the incompatibility clutter (removing footpods from bikes and power meters from shoes) by applying basic set filtering on `allRemoteSensors`. Because `allRemoteSensors` was sorted alphabetically, the resulting sub-lists naturally retained alphabetical order. Sport-specific domain priority was not yet formalized.
* **Preservation of Core Invariants**:
  - All sensor compatibility filters (`isBikeSensor`, `isRunSensor`, `isSharedSensor`) remain strictly intact.
  - Independent horizontal scroll states for Bikes and Shoes tables are preserved.
  - 1-tap checkbox persistence and reactive reload (`REQ-UI-257`) remain fully functional.
  - 100% full regression test suite pass rate is strictly maintained.

---

## 5. Architectural Strategy & High-Level Solution

```
                                 allRemoteSensors
                                        │
             ┌──────────────────────────┴──────────────────────────┐
             ▼                                                     ▼
      Filter BIKE & SHARED                                  Filter RUN & SHARED
             │                                                     │
             ▼                                                     ▼
  EquipmentSensorOrdering                               EquipmentSensorOrdering
  for BSportType.BIKE                                   for BSportType.RUN
  1. BIKE_POWER                                         1. RUN_SPEED (Footpod)
  2. BIKE_SPEED_AND_CADENCE                             2. Other Run Sensors
  3. BIKE_SPEED                                         3. HRM
  4. BIKE_CADENCE                                       4. ENVIRONMENT
  5. Other Bike Sensors                                 5. Other Shared Sensors
  6. HRM                                                [Secondary: Name ASC, Id ASC]
  7. ENVIRONMENT                                                   │
  8. Other Shared Sensors                                          │
  [Secondary: Name ASC, Id ASC]                                    │
             │                                                     │
             ▼                                                     ▼
        bikeSensors                                           shoeSensors
             │                                                     │
    ┌────────┴────────┐                                   ┌────────┴────────┐
    ▼                 ▼                                   ▼                 ▼
Table 1 Columns   EditEquipmentDialog                 Table 2 Columns   EditEquipmentDialog
 (Bikes Matrix)     (Bike Selected)                    (Shoes Matrix)    (Shoe Selected)
```

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero production regressions: All sensor linking, unlinking, and persistence operations function identically.
  2. Deterministic stability: Sensor column order is completely deterministic and stable across app restarts and data reloads.
  3. Defensive null-handling: Sensors with null or unrecognized `deviceType` are sorted gracefully at the end of the list.
* **Risk Rating**: **LOW**
  - Justification: Pure in-memory comparator ordering applied to UI presentation lists without altering database schemas or asynchronous state flow contracts.
