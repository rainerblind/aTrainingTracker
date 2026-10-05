# Stage 1: Problem Domain & Root Cause Analysis - ATT-2382: Split Equipment Sensor Matrix into Sport-Specific Tables for Bikes and Shoes and Restrict Incompatible Sensor Linkage

**Ticket**: [ATT-2382](https://rainerblind.atlassian.net/browse/ATT-2382)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-UI-256` (Refinement)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & User Value

### Current Deficiencies
In Sprint `2026-40.16` (ATT-2126 / `REQ-UI-256`), an interactive fleet-wide equipment-to-sensor mapping matrix was introduced in `EquipmentTabsScreen.kt` (`EquipmentSensorMatrixScreen.kt`). While this central matrix streamlined equipment configuration, feedback during the Sprint Review of ATT-2306 uncovered an ergonomic and domain-modelling shortcoming:
1. **Undifferentiated Monolithic Matrix**:
   The current matrix renders a single, shared horizontal header displaying *all* paired external remote sensors (`allRemoteSensors`) across both Bikes and Shoes.
2. **Incompatible Column Pollution**:
   - Cycling-exclusive sensors (Power Meters, Bike Cadence, Speed sensors, Smart Trainers / FE-C, Shifting, Radar) are rendered as columns across all Running Shoe rows.
   - Running-exclusive sensors (Stride Speed & Distance / Footpods, Running Dynamics) are rendered as columns across all Bicycle rows.
   - This creates nonsensical intersections (e.g. mounting a 4iiii/Stages power meter to a running shoe, or strapping a Stryd footpod to a road bike).
3. **Horizontal Layout Clutter & Synchronized Scroll Fatigue**:
   - Athletes with multi-sport setups (e.g. 3 bikes, 4 pairs of shoes, 2 power meters, 3 cadence/speed sensors, 1 footpod, 1 heart rate monitor) face an excessively wide table with 7+ columns, where 80% of the cells are irrelevant or nonsensical.
   - Because a single horizontal scroll state was shared across the screen, scrolling through bike sensors scrolled the shoe section as well, making visual comparison awkward.
4. **Dialog Linkage Inconsistencies**:
   - In `DevicesDatabaseManager.getSensorsForSportType(BSportType.BIKE)` and `getSensorsForSportType(BSportType.RUN)`, shared sensors (`HRM` and `ENVIRONMENT`) were excluded, preventing athletes from linking heart rate straps or temperature pods to their bikes/shoes in single-item dialogs.

### Proposed User Value
1. **Two Sport-Specific Sensor Matrix Tables**:
   - **Table 1: Fahrräder (Bikes)**:
     Displays bicycles with columns restricted strictly to bike-compatible sensors (`BIKE_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`, `FITNESS_EQUIPMENT`, `RADAR`, `SHIFTING`) plus shared sensors (`HRM`, `ENVIRONMENT`).
     Features an independent horizontal scroll state and dedicated sticky header.
   - **Table 2: Schuhe (Shoes)**:
     Displays running shoes with columns restricted strictly to run-compatible sensors (`RUN_SPEED` / Footpod, Running Dynamics) plus shared sensors (`HRM`, `ENVIRONMENT`).
     Features an independent horizontal scroll state and dedicated sticky header.
2. **Shared Sensor Parity**:
   Heart Rate Monitors (`HRM`) and Temperature Sensors (`ENVIRONMENT`) are universally compatible and correctly appear in both Bike and Shoe tables.
3. **Restricted Incompatible Linkage**:
   - The UI physically prevents selecting incompatible sensor-equipment combinations.
   - `EditEquipmentDialog` and `EditDeviceDialog` enforce sport-compatibility consistency.

---

## 2. Technical Architecture & Forensic Root Cause

### 1. Existing Monolithic Matrix Implementation
In `EquipmentSensorMatrixScreen.kt`:
```kotlin
fun EquipmentSensorMatrixScreen(
    bikes: List<EquipmentItem>,
    shoes: List<EquipmentItem>,
    sensors: List<SimpleSensorInfo>, // Monolithic list across all sensor types
    onToggleLink: (equipmentId: Long, sensorId: Long, isLinked: Boolean) -> Unit,
    ...
)
```
- A single `LazyColumn` contains one sticky header `stickyHeader(key = "matrix_sensor_header")` rendering `sensors.forEach { ... }`.
- Under this single header, both `bikes` and `shoes` are rendered using `MatrixEquipmentRow(item, isBike, sensors, horizontalScrollState, onToggleLink)`.
- Because `sensors` contains every remote sensor, a bike power meter column appears above running shoes, and a footpod column appears above road bikes.

### 2. Sensor Typing & Sport Compatibility Architecture
In `app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceType.java`:
```java
public enum DeviceType {
    HRM(SensorType.HR, BSportType.UNKNOWN, 1),
    BIKE_SPEED(SensorType.SPEED_mps, BSportType.BIKE, BANALService.DEFAULT_BIKE_CALIBRATION_FACTOR),
    BIKE_CADENCE(SensorType.CADENCE, BSportType.BIKE, 1),
    BIKE_SPEED_AND_CADENCE(SensorType.SPEED_mps, BSportType.BIKE, BANALService.DEFAULT_BIKE_CALIBRATION_FACTOR),
    BIKE_POWER(SensorType.POWER, BSportType.BIKE, BANALService.DEFAULT_BIKE_CALIBRATION_FACTOR),
    RUN_SPEED(SensorType.SPEED_mps, BSportType.RUN, 1),
    ENVIRONMENT(SensorType.TEMPERATURE, BSportType.UNKNOWN, 1),
    ...
    public BSportType getSportType() {
        return sportType;
    }
}
```
Each `DeviceType` already declares its domain sport type (`sportType`):
- Cycling sensors have `sportType == BSportType.BIKE`.
- Running sensors have `sportType == BSportType.RUN`.
- Multi-sport / shared sensors have `sportType == BSportType.UNKNOWN`.

In `DevicesDatabaseManager.SimpleSensorInfo`:
```java
public static class SimpleSensorInfo {
    public final long id;
    public final String name;
    // Missing: DeviceType deviceType
}
```
Currently, `SimpleSensorInfo` only holds `id` and `name`. Because `deviceType` is not exposed on `SimpleSensorInfo`, the UI and ViewModel cannot determine whether a sensor is a bike sensor, a run sensor, or a shared sensor without re-querying the database.

### 3. Query Deficiencies in `DevicesDatabaseManager.java`
```java
public List<SimpleSensorInfo> getSensorsForSportType(BSportType sportType) {
    ...
    if (sportType == BSportType.BIKE) {
        selection += " AND (" + DevicesDbHelper.DEVICE_TYPE + " LIKE 'BIKE%')";
    }
    else if (sportType == BSportType.RUN) {
        selection += " AND (" + DevicesDbHelper.DEVICE_TYPE + " LIKE 'RUN%')";
    }
    ...
}
```
This query omits `HRM` and `ENVIRONMENT` because neither starts with `BIKE%` or `RUN%`. Consequently, shared sensors disappear when filtering by sport type.

---

## 3. Proposed Remediation Architecture

### 1. Enrich `SimpleSensorInfo` with `DeviceType`
Extend `DevicesDatabaseManager.SimpleSensorInfo`:
```java
public static class SimpleSensorInfo {
    public final long id;
    public final String name;
    @Nullable public final DeviceType deviceType;

    public SimpleSensorInfo(long id, String name) {
        this(id, name, null);
    }

    public SimpleSensorInfo(long id, String name, @Nullable DeviceType deviceType) {
        this.id = id;
        this.name = name;
        this.deviceType = deviceType;
    }
}
```
In `getAllRemoteSensors()` and `getSensorsForSportType()`:
Query `DevicesDbHelper.DEVICE_TYPE` and populate `SimpleSensorInfo.deviceType`.

### 2. Compatibility Extension Functions
Define clean compatibility rules on `SimpleSensorInfo`:
```kotlin
fun SimpleSensorInfo.isCompatibleWithBike(): Boolean {
    val type = this.deviceType ?: return true
    return type.sportType == BSportType.BIKE || type.sportType == BSportType.UNKNOWN
}

fun SimpleSensorInfo.isCompatibleWithShoe(): Boolean {
    val type = this.deviceType ?: return true
    return type.sportType == BSportType.RUN || type.sportType == BSportType.UNKNOWN
}
```

### 3. Update `getSensorsForSportType` for Shared Sensors
Update SQL query in `DevicesDatabaseManager.getSensorsForSportType(BSportType sportType)`:
- `BSportType.BIKE`: `DEVICE_TYPE LIKE 'BIKE%' OR DEVICE_TYPE = 'HRM' OR DEVICE_TYPE = 'ENVIRONMENT'`
- `BSportType.RUN`: `DEVICE_TYPE LIKE 'RUN%' OR DEVICE_TYPE = 'HRM' OR DEVICE_TYPE = 'ENVIRONMENT'`

### 4. Split Matrix UI into Independent Sport-Specific Tables
In `EquipmentSensorMatrixScreen.kt`:
1. Partition incoming sensors into `bikeSensors` and `shoeSensors`:
   ```kotlin
   val bikeSensors = remember(sensors) { sensors.filter { it.isCompatibleWithBike() } }
   val shoeSensors = remember(sensors) { sensors.filter { it.isCompatibleWithShoe() } }
   ```
2. Provide independent `ScrollState` instances:
   ```kotlin
   val bikeScrollState = rememberScrollState()
   val shoeScrollState = rememberScrollState()
   ```
3. Structure the screen into two distinct table cards/sections:
   - **Bikes Table**:
     - Sticky Header / Section Header: "Fahrräder" (`R.string.equipment_type_bike`) + count.
     - Column Headers: `bikeSensors` with `bikeScrollState`.
     - Equipment Rows: `bikes` displaying checkboxes exclusively for `bikeSensors`.
   - **Shoes Table**:
     - Sticky Header / Section Header: "Schuhe" (`R.string.equipment_type_shoe`) + count.
     - Column Headers: `shoeSensors` with `shoeScrollState`.
     - Equipment Rows: `shoes` displaying checkboxes exclusively for `shoeSensors`.
4. Empty States:
   If `bikeSensors.isEmpty()` or `shoeSensors.isEmpty()`, display a concise inline note explaining no compatible sensors are paired for that sport category.

---

## 4. Scope Bounding & Out-of-Scope (ATT-1250)

### In-Scope
- Exposing `deviceType` on `SimpleSensorInfo` in `DevicesDatabaseManager.java`.
- Enhancing `DevicesDatabaseManager.getSensorsForSportType` to include shared sensors (`HRM`, `ENVIRONMENT`).
- Splitting `EquipmentSensorMatrixScreen.kt` into two sport-specific tables (Bikes and Shoes) with independent column sets and independent horizontal scrolling.
- Restricting `EditEquipmentDialog` to compatible sensors for the edited equipment item type.
- Unit testing sport-specific sensor partitioning, compatibility helpers, and contract integrity.

### Out-of-Scope
- Modifying SQLite database schemas in `Equipment.db` or `Devices.db` (existing relational schema and foreign keys remain 100% intact).
- Adding new hardware protocols or changing ANT+/BLE device connection logic.
- Altering the 3-tab pager layout in `EquipmentTabsScreen.kt` (Bikes, Shoes, Sensor Matrix tabs remain identical).

---

## 5. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**:
   - `REQ-UI-256` (*Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes*), targeting `EquipmentTabsScreen.kt`, `EquipmentSensorMatrixScreen.kt`, `EquipmentViewModel.kt`, and `DevicesDatabaseManager.java`.
2. **Historical Origin & Commit Trace**:
   - Commit `fc10ad81` (Sprint `2026-40.16`), ticket `ATT-2126`.
3. **Root Reason for Existing Formulation**:
   - `REQ-UI-256` was formulated to replace slow, siloed equipment editing with a fleet-wide matrix. At the time of initial implementation, a single unified table was created to quickly establish the matrix grid concept. However, practical athletic usage revealed that displaying power meters on shoes and footpods on bikes created visual noise and nonsensical mapping options.
4. **Preservation of Core Invariants**:
   - 1-tap Material 3 Checkbox persistence via `EquipmentViewModel.setSensorLink` and `EquipmentDbHelper.setDeviceLink` MUST be preserved.
   - Sticky equipment column on the left and sticky sensor headers on the top MUST be preserved.
   - Bidirectional reactive synchronization with `EditEquipmentDialog` and `EditDeviceDialog` MUST be preserved.
   - 9-language translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT MUST be maintained.

---

## 6. Verification & Test Strategy

1. **Unit Testing (`EquipmentSportSensorCompatibilityTest.kt`)**:
   - Verify `isCompatibleWithBike()` returns `true` for `BIKE_SPEED`, `BIKE_CADENCE`, `BIKE_POWER`, `HRM`, `ENVIRONMENT`, and `false` for `RUN_SPEED`.
   - Verify `isCompatibleWithShoe()` returns `true` for `RUN_SPEED`, `HRM`, `ENVIRONMENT`, and `false` for `BIKE_SPEED`, `BIKE_CADENCE`, `BIKE_POWER`.
   - Verify `getSensorsForSportType(BSportType.BIKE)` and `getSensorsForSportType(BSportType.RUN)` include `HRM` and `ENVIRONMENT`.
2. **ViewModel Matrix Filtering Test (`EquipmentViewModelMatrixTest.kt`)**:
   - Verify `EquipmentViewModel` provides distinct bike and shoe sensor lists with shared sensors included.
3. **Contract & Structural Tests (`EquipmentSensorMatrixContractTest.kt`)**:
   - Verify `EquipmentSensorMatrixScreen` renders two independent tables with sport-filtered sensor columns and separate scroll states.
4. **Full Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` verifying 0 test failures across the codebase.
