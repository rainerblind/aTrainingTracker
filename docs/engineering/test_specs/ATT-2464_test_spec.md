# Stage 2: Requirement & Test Specification - ATT-2464: Order equipment sensor matrix columns with sport-specific sensors first, then shared sensors

**Ticket**: [ATT-2464](https://rainerblind.atlassian.net/browse/ATT-2464)  
**Sub-task**: [ATT-2602](https://rainerblind.atlassian.net/browse/ATT-2602) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-283` (Refines and extends `REQ-UI-256`)  
**Test Spec ID**: `TST-UI-243`  
**Branch**: `feature/ATT-2464`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (REQ-UI-283)

### 1.1 Problem Statement & Rationale
In Sprint 2026-40.16 (ATT-2382), the equipment-sensor matrix was partitioned into dedicated Bikes and Shoes tables to eliminate cross-sport clutter (`REQ-UI-256`). However, on physical test hardware (Pixel 10), human testing revealed that sensor column order within each table and the sensor selection dropdown in `EditEquipmentDialog` remained purely alphabetical by name (`NAME COLLATE NOCASE ASC`). As a result, auxiliary sensors (like Heart Rate Monitors or temperature sensors) frequently appeared before primary athletic telemetry sources (like Power Meters or Speed sensors).

**Human Tester Observation**:
> *"For the bike, the ride specific sensors must come first, then the generic ones (like HR). Similar for the shoes."*

To provide an intuitive, high-efficiency equipment configuration experience, the system SHALL deterministically order sensor columns and selection lists by sport-specific domain priority first, followed by shared/generic sensors, with standardized case-insensitive alphabetical tie-breaking.

### 1.2 Functional & Architectural Requirements
1. **Cycling Sensor Precedence (`BSportType.BIKE`)**:
   - The system SHALL order bike-compatible sensors into distinct priority tiers:
     - *Primary Tier (Cycling-Specific)*:
       1. `BIKE_POWER` (Priority Rank 10)
       2. `BIKE_SPEED_AND_CADENCE` (Priority Rank 20)
       3. `BIKE_SPEED` (Priority Rank 30)
       4. `BIKE_CADENCE` (Priority Rank 40)
       5. Any other bike-exclusive sensors (Priority Rank 50)
     - *Secondary Tier (Shared / Generic)*:
       1. `HRM` (Priority Rank 100)
       2. `ENVIRONMENT` (Priority Rank 110)
       3. Any other shared sensors (Priority Rank 120)
     - *Tertiary Tier (Unclassified / Other)*: Priority Rank 200.
   - Within the same priority rank, sensors SHALL be ordered case-insensitively alphabetically by `name` (`String.CASE_INSENSITIVE_ORDER`), tie-broken by `id` ascending.
2. **Running Sensor Precedence (`BSportType.RUN`)**:
   - The system SHALL order run-compatible sensors into distinct priority tiers:
     - *Primary Tier (Running-Specific)*:
       1. `RUN_SPEED` (Footpod / Run speed & distance, Priority Rank 10)
       2. Any other run-exclusive sensors (Priority Rank 20)
     - *Secondary Tier (Shared / Generic)*:
       1. `HRM` (Priority Rank 100)
       2. `ENVIRONMENT` (Priority Rank 110)
       3. Any other shared sensors (Priority Rank 120)
     - *Tertiary Tier (Unclassified / Other)*: Priority Rank 200.
   - Within the same priority rank, sensors SHALL be ordered case-insensitively alphabetically by `name` (`String.CASE_INSENSITIVE_ORDER`), tie-broken by `id` ascending.
3. **Centralized Comparator Utility (`EquipmentSensorOrdering`)**:
   - The system SHALL centralize sensor comparator logic in `EquipmentSensorOrdering` to ensure 100% ordering consistency across the ViewModel, database queries, and UI components.
   - The comparator SHALL safely handle `null` sensor references or sensors with `deviceType == null` by placing them in the lowest priority tier (Rank 200).
4. **Database Query Integration (`DevicesDatabaseManager.java`)**:
   - `DevicesDatabaseManager.getSensorsForSportType(BSportType sportType)` SHALL return sensor lists deterministically sorted using `EquipmentSensorOrdering` for the requested sport.
5. **ViewModel Reactive State (`EquipmentViewModel.kt`)**:
   - `EquipmentViewModel._bikeSensors` and `bikeSensorsList` SHALL emit bike sensors sorted by `EquipmentSensorOrdering` for `BSportType.BIKE`.
   - `EquipmentViewModel._shoeSensors` and `runSensorsList` SHALL emit shoe sensors sorted by `EquipmentSensorOrdering` for `BSportType.RUN`.
6. **UI Presentation Consistency (`EquipmentSensorMatrixScreen.kt` & `EditEquipmentDialog.kt`)**:
   - `EquipmentSensorMatrixScreen.kt` Table 1 (Bikes) and Table 2 (Shoes) column headers SHALL be displayed in prioritized order. The fallback path using raw `sensors` SHALL also apply `EquipmentSensorOrdering`.
   - In `EditEquipmentDialog.kt`, `compatibleSensors` displayed in `MultiSelectSensorSpinner` SHALL be sorted using `EquipmentSensorOrdering` according to whether the item is a bike (`item.frameType > 0`) or shoe (`item.frameType == 0`).

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-256` (*Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes*), targeting `EquipmentSensorMatrixScreen.kt`, `EquipmentViewModel.kt`, `DevicesDatabaseManager.java`, and `EditEquipmentDialog.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-2126` (commit `fc10ad81`, Sprint `2026-40.16`) and Ticket `ATT-2382` (commit `001d9326`, Sprint `2026-40.16`).
* **Root Reason for Existing Formulation**: `ATT-2382` partitioned the matrix into separate tables and filtered sensors by sport compatibility, but retained SQLite's default `NAME COLLATE NOCASE ASC` order.
* **Preservation of Core Invariants**: 1-tap checkbox persistence, bidirectional synchronization (`REQ-UI-257`), independent horizontal scroll states for Bikes and Shoes tables, and 100% clean-room test pass rate MUST NOT be compromised.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* paired sensors including "Garmin HRM-Dual" (HRM), "Stages Power" (BIKE_POWER), "Wahoo SPEED" (BIKE_SPEED), and "Garmin Tempe" (ENVIRONMENT),
* *When* viewing the Bikes table in `EquipmentSensorMatrixScreen`,
* *Then* columns SHALL be ordered: "Stages Power", "Wahoo SPEED", "Garmin HRM-Dual", "Garmin Tempe".
* *Given* paired sensors including "Garmin HRM-Dual" (HRM), "Stryd Footpod" (RUN_SPEED), and "Garmin Tempe" (ENVIRONMENT),
* *When* viewing the Shoes table in `EquipmentSensorMatrixScreen`,
* *Then* columns SHALL be ordered: "Stryd Footpod", "Garmin HRM-Dual", "Garmin Tempe".
* *Given* multiple bike power meters (e.g. "Stages L", "4iiii Precision"),
* *When* ordering bike sensors,
* *Then* power meters SHALL appear before speed/cadence/shared sensors, sorted alphabetically: "4iiii Precision" before "Stages L".
* *Given* opening `EditEquipmentDialog` for a bike,
* *When* expanding the sensor selection spinner,
* *Then* available sensors SHALL appear in the same prioritized order (cycling sensors first, then shared).
* *Given* opening `EditEquipmentDialog` for a running shoe,
* *When* expanding the sensor selection spinner,
* *Then* available sensors SHALL appear in the same prioritized order (running sensors first, then shared).

---

## 2. Test Specification (TST-UI-243)

### Test Case 1: Pure Unit Ordering Verification (`TST-UI-243.1`)
* **Scope**: Mathematical & Algorithmic Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorOrderingTest.kt`
* **Test Procedures**:
  1. `testBikeSensorOrdering_sportSpecificBeforeShared`: Assert `BIKE_POWER` < `BIKE_SPEED_AND_CADENCE` < `BIKE_SPEED` < `BIKE_CADENCE` < `HRM` < `ENVIRONMENT`.
  2. `testShoeSensorOrdering_sportSpecificBeforeShared`: Assert `RUN_SPEED` < `HRM` < `ENVIRONMENT`.
  3. `testSecondaryAlphabeticalSorting`: Assert sensors of the same `DeviceType` are sorted case-insensitively by name.
  4. `testTertiaryIdSorting`: Assert sensors with identical type and name are sorted by ID ascending.
  5. `testNullAndUnrecognizedHandling`: Assert sensors with null `deviceType` or non-matching sports sort to the end without crashing.
* **Expected Result**: All ordering assertions pass.

### Test Case 2: ViewModel Reactive Ordering Pipeline (`TST-UI-243.2`)
* **Scope**: ViewModel StateFlow Integration Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModelMatrixTest.kt`
* **Test Procedures**:
  1. Update `testSportPartitionedSensors_emitsFilteredSensors` to assert that `bikeSensors` emits sport-specific sensors first, then shared sensors (e.g. `[BIKE_POWER, HRM, ENVIRONMENT]`).
  2. Assert that `shoeSensors` emits `[RUN_SPEED, HRM, ENVIRONMENT]`.
  3. Verify fallback getters `bikeSensorsList` and `runSensorsList` maintain identical ordering.
* **Expected Result**: StateFlow emissions match the prioritized domain hierarchy.

### Test Case 3: Database Manager Sport Query Ordering (`TST-UI-243.3`)
* **Scope**: Database Helper Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManagerOrderingTest.kt`
* **Test Procedures**:
  1. Verify `getSensorsForSportType(BSportType.BIKE)` returns sport-specific sensors ahead of shared sensors.
  2. Verify `getSensorsForSportType(BSportType.RUN)` returns run-specific sensors ahead of shared sensors.
* **Expected Result**: Database helper query outputs comply with `EquipmentSensorOrdering`.

### Test Case 4: Dialog & Screen Architectural Contract (`TST-UI-243.4`)
* **Scope**: Structural AST / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixContractTest.kt`
* **Test Procedures**:
  1. Verify `EditEquipmentDialog.kt` applies `EquipmentSensorOrdering` to `compatibleSensors`.
  2. Verify `EquipmentSensorMatrixScreen.kt` fallback applies `EquipmentSensorOrdering`.
* **Expected Result**: Code structure enforces domain-ordered sensors across all matrix and dialog components.

### Test Case 5: Clean-Room Full Regression Suite (`TST-UI-243.5`)
* **Scope**: System-wide Clean-Room Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% test pass rate with 0 failures and 0 skipped tests.

---

## 3. Traceability Matrix

| Requirement ID | Requirement Summary | Verification ID | Test Method / File | Status |
|:---|:---|:---|:---|:---|
| `REQ-UI-283.1` | Cycling Sensor Precedence (`BIKE`) | `TST-UI-243.1`, `TST-UI-243.2` | `EquipmentSensorOrderingTest`, `EquipmentViewModelMatrixTest` | Planned |
| `REQ-UI-283.2` | Running Sensor Precedence (`RUN`) | `TST-UI-243.1`, `TST-UI-243.2` | `EquipmentSensorOrderingTest`, `EquipmentViewModelMatrixTest` | Planned |
| `REQ-UI-283.3` | Centralized Comparator (`EquipmentSensorOrdering`) | `TST-UI-243.1` | `EquipmentSensorOrderingTest` | Planned |
| `REQ-UI-283.4` | Database Query Ordering Integration | `TST-UI-243.3` | `DevicesDatabaseManagerOrderingTest` | Planned |
| `REQ-UI-283.5` | ViewModel Reactive State Prioritization | `TST-UI-243.2` | `EquipmentViewModelMatrixTest` | Planned |
| `REQ-UI-283.6` | UI Dialog & Matrix Screen Presentation Consistency | `TST-UI-243.4` | `EquipmentSensorMatrixContractTest` | Planned |
