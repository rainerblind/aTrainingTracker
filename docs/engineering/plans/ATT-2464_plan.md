# Stage 3: Implementation Plan - ATT-2464: Order equipment sensor matrix columns with sport-specific sensors first, then shared sensors

**Ticket**: [ATT-2464](https://rainerblind.atlassian.net/browse/ATT-2464)  
**Sub-task**: [ATT-2603](https://rainerblind.atlassian.net/browse/ATT-2603) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-283` (Refines and extends `REQ-UI-256`)  
**Test Mapping**: `TST-UI-243`  
**Branch**: `feature/ATT-2464`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Background

In Sprint 2026-40.16 (ATT-2382), the equipment-sensor matrix was partitioned into dedicated Bikes and Shoes tables to eliminate cross-sport sensor clutter (`REQ-UI-256`). While this successfully prevented footpods from appearing on bikes and power meters on shoes, on-device review on a Pixel 10 revealed that sensor columns within each table and the sensor selection dropdown in `EditEquipmentDialog` were sorted purely alphabetically by sensor name (`NAME COLLATE NOCASE ASC`).

**Human Tester Observation**:
> *"For the bike, the ride specific sensors must come first, then the generic ones (like HR). Similar for the shoes."*

This implementation plan defines the architectural construction sequence to implement deterministic sport-specific sensor domain ordering: cycling-specific sensors first, then shared sensors for bikes; running-specific sensors first, then shared sensors for shoes; with standardized case-insensitive alphabetical tie-breaking.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-283` (*Equipment Sensor Matrix and Configuration Dialog: Sport-Specific Sensor Precedence Ordering*)
  * Refines `REQ-UI-256` Clause 3 (*Sport-Specific Matrix Architecture*) and Clause 5 (*Single-Item Dialog Linkage Restriction*).
* **Test Mapping**: `TST-UI-243` (*Equipment Sensor Precedence Ordering Verification*)
  * `TST-UI-243.1`: Pure unit ordering verification (`EquipmentSensorOrderingTest.kt`).
  * `TST-UI-243.2`: ViewModel StateFlow reactive ordering (`EquipmentViewModelMatrixTest.kt`).
  * `TST-UI-243.3`: Database manager sport query ordering (`DevicesDatabaseManagerOrderingTest.kt`).
  * `TST-UI-243.4`: Dialog and matrix screen architectural contract (`EquipmentSensorMatrixContractTest.kt`).
  * `TST-UI-243.5`: Clean-room full regression suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Persistence Regressions**: 1-tap checkbox persistence and SQLite `LINKS` table transactions remain completely unchanged.
2. **Bidirectional Reactivity (`REQ-UI-257`)**: Event streams from `EquipmentRepository.equipmentLinksChanged` continue to trigger atomic ViewModel reloads across dialogs and matrix views.
3. **Defensive Null-Safety**: Null sensors, null `deviceType` properties, or unrecognized device types are safely routed to the lowest priority tier (Rank 200) without crashing.
4. **Independent Table Scrolling**: Table 1 (Bikes) and Table 2 (Shoes) maintain independent horizontal scroll states.
5. **Subtask Governance**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `EquipmentSensorOrdering.kt` (Centralized Comparator Utility)
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorOrdering.kt`.
* Functions:
  * `getPriorityRank(deviceType: DeviceType?, sportType: BSportType): Int`:
    - For `BSportType.BIKE`:
      - `BIKE_POWER` -> 10
      - `BIKE_SPEED_AND_CADENCE` -> 20
      - `BIKE_SPEED` -> 30
      - `BIKE_CADENCE` -> 40
      - Other bike sensors (`isBikeSensor`) -> 50
      - `HRM` -> 100
      - `ENVIRONMENT` -> 110
      - Other shared sensors (`isSharedSensor`) -> 120
      - Other / Unknown / Null -> 200
    - For `BSportType.RUN`:
      - `RUN_SPEED` -> 10
      - Other run sensors (`isRunSensor`) -> 20
      - `HRM` -> 100
      - `ENVIRONMENT` -> 110
      - Other shared sensors (`isSharedSensor`) -> 120
      - Other / Unknown / Null -> 200
  * `getComparator(sportType: BSportType): Comparator<SimpleSensorInfo>`:
    - Primary: `getPriorityRank(s1.deviceType, sportType).compareTo(getPriorityRank(s2.deviceType, sportType))`
    - Secondary: `String.CASE_INSENSITIVE_ORDER.compare(s1.name ?: "", s2.name ?: "")`
    - Tertiary: `s1.id.compareTo(s2.id)`
  * `sortSensors(sensors: List<SimpleSensorInfo>, sportType: BSportType): List<SimpleSensorInfo>`:
    - Convenience sorting method returning `sensors.sortedWith(getComparator(sportType))`.

### Component 2: `DevicesDatabaseManager.java` (Database Query Integration)
* In `getSensorsForSportType(BSportType sportType)`:
  - After querying SQLite cursor and populating `sensors: List<SimpleSensorInfo>`, apply `EquipmentSensorOrdering.INSTANCE.sortSensors(sensors, sportType)` before returning.

### Component 3: `EquipmentViewModel.kt` (StateFlow & List Prioritization)
* In `loadEquipment()`:
  - Sort `_bikeSensors.value` with `EquipmentSensorOrdering.sortSensors(..., BSportType.BIKE)`.
  - Sort `_shoeSensors.value` with `EquipmentSensorOrdering.sortSensors(..., BSportType.RUN)`.
* In `bikeSensorsList` & `runSensorsList`:
  - Delegates to `_bikeSensors.value` / `_shoeSensors.value` or `dbDevicesHelper.getSensorsForSportType(...)`, ensuring consistent ordered outputs in all fallback paths.

### Component 4: `EquipmentSensorMatrixScreen.kt` & `EditEquipmentDialog.kt` (UI Consistency)
* In `EquipmentSensorMatrixScreen.kt`:
  - Ensure fallback paths (`effectiveBikeSensors` and `effectiveShoeSensors` when raw `sensors` is passed) apply `EquipmentSensorOrdering.sortSensors`.
* In `EditEquipmentDialog.kt`:
  - In `compatibleSensors`, determine `sportType = if (item.frameType > 0) BSportType.BIKE else BSportType.RUN`, filter sensors, and sort via `EquipmentSensorOrdering.sortSensors(filtered, sportType)`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `EquipmentSensorMatrixScreen.kt` (Bikes and Shoes tables) and `EditEquipmentDialog.kt` (`MultiSelectSensorSpinner`).
* **Reused components**: Material 3 Checkboxes, LazyColumn sticky headers, ExposedDropdownMenu from `EditEquipmentDialog.kt` and `EquipmentSensorMatrixScreen.kt`.
* **Theme tokens**: Existing `STICKY_COLUMN_WIDTH`, `SENSOR_COLUMN_WIDTH`, `ROW_HEIGHT`, and Material 3 theme colors.
* **New one-off styles & justification**: None. Reuses 100% of existing components and styles; changes column and item display order only.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `EquipmentSensorOrdering.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorOrdering.kt`
* Implementation: Define `getPriorityRank`, `getComparator`, and `sortSensors`.
* Unit Test: Author `EquipmentSensorOrderingTest.kt` verifying all priority tiers, secondary alphabetical sorting, and null tolerance.

### Step 2: Integrate `EquipmentSensorOrdering` into `DevicesDatabaseManager.java`
* Files: `app/src/main/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManager.java`
* Implementation: Update `getSensorsForSportType(BSportType sportType)` to sort via `EquipmentSensorOrdering`.
* Unit Test: Author `DevicesDatabaseManagerOrderingTest.kt` verifying SQLite query results sort by domain priority.

### Step 3: Update `EquipmentViewModel.kt` StateFlows
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModel.kt`
* Implementation: Apply `EquipmentSensorOrdering.sortSensors` when setting `_bikeSensors.value` and `_shoeSensors.value`.
* Unit Test: Update `EquipmentViewModelMatrixTest.kt` to assert prioritized order in `bikeSensors` and `shoeSensors`.

### Step 4: Update `EditEquipmentDialog.kt` and `EquipmentSensorMatrixScreen.kt`
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EditEquipmentDialog.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt`
* Implementation: Apply `EquipmentSensorOrdering.sortSensors` to `compatibleSensors` and fallback sensor filtering.
* Unit Test: Update `EquipmentSensorMatrixContractTest.kt`.

### Step 5: Verification & Full Clean-Room Test Suite
* Command: `./gradlew testDebugUnitTest`
* Assert 100% pass rate with 0 regressions.
