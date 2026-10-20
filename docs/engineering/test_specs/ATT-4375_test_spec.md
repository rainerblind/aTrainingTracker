# Stage 2: Requirement & Test Specification - ATT-4375: Align location and sensor priority order across DeviceType, MySensorManager, DevicesDatabaseManager, and SensorSourceDialog

**Ticket**: [[ATT-4375]](https://rainerblind.atlassian.net/browse/ATT-4375)  
**Sub-task**: [[ATT-4378]](https://rainerblind.atlassian.net/browse/ATT-4378) (`[Req & Test Spec]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `TBD (Set on Completion per Rule 14)`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-TRK-024` (*Google Fused Location Provider Inclusion in MySensorManager.SPEED_PRIORITY, Enum Declaration Alignment & LINE_DISTANCE_m Priority*), `REQ-UI-268` (*Truthful Live Sensor Connection Status and Canonical Priority Sorting in SensorSourceDialog*)  
**Test Spec ID**: `TST-TRK-016` (Suite), `TST-UI-227` (Suite)  
**Branch**: `feature/ATT-4375`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification ([REQ-TRK-024], [REQ-UI-268])

### 1.1 Problem Statement & Rationale
While [`MySensorManager.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java) explicitly defines `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY` with the canonical location provider priority hierarchy:
$$\text{GPS} \longrightarrow \text{GOOGLE\_FUSED} \longrightarrow \text{NETWORK}$$
three fundamental ordering discrepancies were uncovered across domain, storage, and UI components:
1. **`DeviceType.java` Enum Declaration Order & Missing `LINE_DISTANCE_m` Priority**:
   - In [`DeviceType.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceType.java#L45-L47), `SPEED_AND_LOCATION_NETWORK` is declared *before* `SPEED_AND_LOCATION_GOOGLE_FUSED`.
   - In [`MySensorManager.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java#L107-L110), `setPriorityList(..., LOCATION_PRIORITY)` is invoked for `ACCURACY`, `BEARING`, `LATITUDE`, and `LONGITUDE`, but **`SensorType.LINE_DISTANCE_m` was omitted**.
   - Because `mAllSensorsMapMap` is an `EnumMap<DeviceType, ...>`, when `priorityList == null`, `getBestSensor()` falls back to `EnumMap.keySet()` which iterates strictly in `DeviceType` enum declaration order (`ordinal()`). As a result, `getBestSensor(SensorType.LINE_DISTANCE_m)` mistakenly selects `SPEED_AND_LOCATION_NETWORK` over `SPEED_AND_LOCATION_GOOGLE_FUSED` when both sensors are active.
2. **Database Insertion Order in `DevicesDatabaseManager.java` (`NETWORK` Before `GOOGLE_FUSED`)**:
   - In [`DevicesDatabaseManager.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManager.java#L1184-L1208) (`addLocationDevicesToDb`), smartphone location devices are inserted in the order `GPS`, `NETWORK`, `GOOGLE_FUSED` instead of `GPS`, `GOOGLE_FUSED`, `NETWORK`.
   - Consequently, SQLite assigns a lower autoincrement row ID (`_id`) to Network than to Google Fused, causing queries ordered by row ID to present Network above Google Fused.
3. **`SensorSourceDialog.kt` Does Not Sort `activeBackups` or `notConnected` by `MySensorManager` Priority**:
   - In [`SensorSourceDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt#L74-L80), `activeBackups` and `notConnected` are not sorted according to `MySensorManager`'s canonical priority hierarchy.
   - `activeBackups` preserves the order of `allTelemetry` (originating from `DeviceManager.getRemoteDeviceList()` iterating a `ConcurrentHashMap` with non-deterministic order).
   - `notConnected` preserves database cursor row order, showing Network above Google Fused.
   - This causes visual disorder and contradicts `MySensorManager`'s actual operational fallback logic.

### 1.2 Functional & Architectural Requirements
1. **`SPEED_PRIORITY` Ordering Invariant (`REQ-TRK-024.1`)**:
   - `MySensorManager.SPEED_PRIORITY` SHALL be ordered:
     `[DeviceType.RUN_SPEED, DeviceType.BIKE_SPEED, DeviceType.BIKE_SPEED_AND_CADENCE, DeviceType.BIKE_POWER, DeviceType.SPEED_AND_LOCATION_GPS, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED, DeviceType.SPEED_AND_LOCATION_NETWORK]`.
2. **`ProxySensor` Retention & Dynamic Source Upgrade/Downgrade (`REQ-TRK-024.2`)**:
   - When a `SPEED_AND_LOCATION_GOOGLE_FUSED` device registers speed/distance metrics, `MySensorManager` SHALL retain active `ProxySensor` instances and dynamically upgrade/downgrade sources in bounded $O(1)$ time over the priority list without introducing latency spikes.
3. **`DeviceType.java` Enum Declaration Order Alignment (`REQ-TRK-024.3`)**:
   - In `DeviceType.java`, the declaration order of location enum constants SHALL be:
     `SPEED_AND_LOCATION_GPS`, then `SPEED_AND_LOCATION_GOOGLE_FUSED`, then `SPEED_AND_LOCATION_NETWORK`.
   - Natural enum `ordinal()` order SHALL match the canonical location priority hierarchy, guaranteeing that `EnumMap.keySet()` iteration respects `GOOGLE_FUSED > NETWORK` when no explicit priority list is registered.
4. **`LINE_DISTANCE_m` Location Priority Registration (`REQ-TRK-024.4`)**:
   - In `MySensorManager` constructor, `setPriorityList(SensorType.LINE_DISTANCE_m, LOCATION_PRIORITY)` SHALL be registered.
   - `getBestSensor(SensorType.LINE_DISTANCE_m)` SHALL prioritize `SPEED_AND_LOCATION_GOOGLE_FUSED` over `SPEED_AND_LOCATION_NETWORK` when both sensors are active.
5. **Canonical Priority Rank Helper (`REQ-TRK-024.5`)**:
   - `MySensorManager` SHALL expose a public static method `int getPriorityRank(SensorType sensorType, DeviceType deviceType)` returning the 0-based priority index of `deviceType` for `sensorType` across all sensor types (`SPEED`, `ALTITUDE`, `LOCATION`, `CADENCE`, single-device sensors).
   - If `deviceType` is not in the priority list, it SHALL return fallback rank `1000 + deviceType.ordinal()`. If `deviceType == null`, it SHALL return `Integer.MAX_VALUE`.
6. **`DevicesDatabaseManager.java` Location Insertion Order (`REQ-TRK-024.6`)**:
   - In `DevicesDatabaseManager.addLocationDevicesToDb(SQLiteDatabase db)`, location devices SHALL be inserted in sequence: `SPEED_AND_LOCATION_GPS` (paired=true), `SPEED_AND_LOCATION_GOOGLE_FUSED` (paired=true), and `SPEED_AND_LOCATION_NETWORK` (paired=false), guaranteeing autoincrement primary key (`_id`) order aligns with priority hierarchy.
7. **`DeviceStatusRow` Parameterization & Backward Compatibility (`REQ-UI-268.1` - `REQ-UI-268.4`)**:
   - `DeviceStatusRow` SHALL declare `isConnected: Boolean = device.isConnected` as an optional parameter, evaluating `(verfügbar)` when `true` and `(nicht verbunden)` / last seen when `false`.
   - In `SensorSourceDialog.kt`, `DeviceStatusRow` SHALL be invoked with `isConnected = isConnected` from `DeviceIdentityBlock`.
8. **Canonical Priority Sorting in `SensorSourceDialog.kt` (`REQ-UI-268.5`)**:
   - In `SensorSourceDialog.kt`, `activeBackups` SHALL be sorted deterministically:
     ```kotlin
     allActiveTelemetries
         .filter { it.deviceId != sourceDevice?.id }
         .sortedWith(
             compareBy<DeviceTelemetry> { telemetry ->
                 val dev = allDevices.find { it.id == telemetry.deviceId }
                 MySensorManager.getPriorityRank(sensorType, dev?.deviceType)
             }
             .thenBy { telemetry ->
                 allDevices.find { it.id == telemetry.deviceId }?.deviceName ?: ""
             }
             .thenBy { it.deviceId }
         )
     ```
   - In `SensorSourceDialog.kt`, `notConnected` SHALL be sorted deterministically:
     ```kotlin
     allDevices
         .filter { potentialTypes.contains(it.deviceType) && !activeDeviceIds.contains(it.id) }
         .sortedWith(
             compareBy<DeviceUiData> { MySensorManager.getPriorityRank(sensorType, it.deviceType) }
                 .thenBy { it.deviceName }
                 .thenBy { it.id }
         )
     ```
   - Active backups and disconnected devices SHALL render in strict accordance with `MySensorManager`'s fallback hierarchy (e.g. Google Fused above Network) rather than arbitrary hash map or SQLite row ID order.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Line Distance Priority Resolution)**:
  * *Given* `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_NETWORK` sensors registered in `MySensorManager` for `SensorType.LINE_DISTANCE_m`,
  * *When* `getBestSensor(SensorType.LINE_DISTANCE_m)` is evaluated,
  * *Then* it SHALL return `SPEED_AND_LOCATION_GOOGLE_FUSED`.
* **Criterion 2 (DeviceType Enum Declaration Order)**:
  * *Given* `DeviceType.java`,
  * *When* comparing enum ordinals,
  * *Then* `SPEED_AND_LOCATION_GPS.ordinal() < SPEED_AND_LOCATION_GOOGLE_FUSED.ordinal()` and `SPEED_AND_LOCATION_GOOGLE_FUSED.ordinal() < SPEED_AND_LOCATION_NETWORK.ordinal()`.
* **Criterion 3 (Canonical Priority Rank Helper)**:
  * *Given* any `SensorType` and `DeviceType`,
  * *When* `MySensorManager.getPriorityRank(sensorType, deviceType)` is called,
  * *Then* it returns the canonical priority rank index (e.g. 0 for GPS, 1 for GOOGLE_FUSED, 2 for NETWORK in location/altitude metrics; 4 for GPS, 5 for GOOGLE_FUSED, 6 for NETWORK in speed metrics).
* **Criterion 4 (Database Insertion Order)**:
  * *Given* a fresh database initialization (`addLocationDevicesToDb`),
  * *When* queried from SQLite table `devices`,
  * *Then* `SPEED_AND_LOCATION_GPS` is inserted first (`_id = 1`), `SPEED_AND_LOCATION_GOOGLE_FUSED` second (`_id = 2`), and `SPEED_AND_LOCATION_NETWORK` third (`_id = 3`).
* **Criterion 5 (SensorSourceDialog Active Backups Priority Sorting)**:
  * *Given* an active tracking session with multiple active backup sensors transmitting telemetry for `sensorType` (e.g. GPS active primary, Google Fused and Network both sending telemetry),
  * *When* opening `SensorSourceDialog`,
  * *Then* Google Fused SHALL appear before Network in the "Aktive Backups" section.
* **Criterion 6 (SensorSourceDialog Disconnected Devices Priority Sorting)**:
  * *Given* paired location devices disconnected in "Nicht verbunden",
  * *When* opening `SensorSourceDialog`,
  * *Then* Google Fused SHALL be rendered above Network.
* **Criterion 7 (Backward Compatibility & Invariant Preservation)**:
  * *Given* existing SQLite database records where `deviceType` is stored as text (`DeviceType.name()`),
  * *When* deserialized by the app,
  * *Then* all records deserialize identically without data loss or corruption, and all existing unit tests pass cleanly.

### 1.4 System Invariants
1. **Zero Database Schema Migration**: SQLite table `devices` stores `deviceType` as text (`DeviceType.name()`), so enum constant reordering has zero impact on deserialization. In-memory priority sorting in `SensorSourceDialog` dynamically fixes display order for both existing and new installations without database migration.
2. **Strict Fallback Priority Consistency**: For any given `SensorType`, `MySensorManager.getBestSensor()` and `SensorSourceDialog` rendering must strictly agree on priority rank:
   - Location & Line Distance: $\text{GPS (0)} > \text{GOOGLE\_FUSED (1)} > \text{NETWORK (2)}$
   - Speed & Distance: $\text{RUN\_SPEED (0)} > \text{BIKE\_SPEED (1)} > \text{BIKE\_SPEED\_AND\_CADENCE (2)} > \text{BIKE\_POWER (3)} > \text{GPS (4)} > \text{GOOGLE\_FUSED (5)} > \text{NETWORK (6)}$
   - Altitude: $\text{ALTITUDE\_FROM\_PRESSURE (0)} > \text{GPS (1)} > \text{GOOGLE\_FUSED (2)} > \text{NETWORK (3)}$
3. **Thread Safety**: The priority ranking helper `getPriorityRank` is a pure function operating on immutable/static priority definitions, introducing zero lock contention or concurrency hazard.
4. **Clean-Room Full Test Suite Pass**: All 3,100+ automated unit and integration tests must pass cleanly (`./gradlew testDebugUnitTest`).

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Extends `REQ-TRK-024` (Google Fused Location Provider Inclusion in MySensorManager.SPEED_PRIORITY) and amends `REQ-UI-268` (SensorSourceDialog Telemetry & Backup Sources). Targets `DeviceType.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, and `SensorSourceDialog.kt`.
* `Historical Origin & Commit Trace`:
  - `REQ-TRK-024` was established in Sprint 2026-43.1 via commit `fc28443ccfed2ef72316e704c475549a013cdc39` (`ATT-3961`). It added `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` to `SPEED_PRIORITY` right after `GPS` and before `NETWORK` to match `ALTITUDE_PRIORITY` and `LOCATION_PRIORITY`.
  - `REQ-UI-268` was established in Sprint 2026-41.2 via `ATT-2194` introducing `SensorSourceDialog` to display active source, active backups, and disconnected devices.
  - Refined in Sprint 2026-43.2 via `ATT-4375`.
* `Root Reason for Existing Formulation`:
  - In `ATT-3961`, the focus was strictly on restoring speed and distance telemetry (`SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`) from Google Fused when GPS was silenced by ADB mock providers. `LINE_DISTANCE_m` was overlooked because it is an elevation/navigation metric rather than a primary cockpit speed/distance metric.
  - `DeviceType.java` enum declaration order and `DevicesDatabaseManager.java` insertion order originated in legacy pre-Compose releases when Network location was initially implemented before Google Fused was added as a secondary provider.
  - `SensorSourceDialog.kt` assumed that listing active backups directly from `allTelemetry` (a `ConcurrentHashMap` with non-deterministic iteration) and inactive devices in cursor order was sufficient.
* `Preservation of Core Invariants`:
  - Aligning `DeviceType.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, and `SensorSourceDialog.kt` preserves and reinforces the core invariant that:
    1. Dedicated hardware wheel/footpod sensors take highest priority for speed/distance.
    2. Barometric pressure sensors take highest priority for altitude.
    3. Hardware GPS (`SPEED_AND_LOCATION_GPS`) takes primary precedence for location and baseline altitude/speed.
    4. Google Fused (`SPEED_AND_LOCATION_GOOGLE_FUSED`) takes secondary precedence over Network (`SPEED_AND_LOCATION_NETWORK`) across all location, altitude, and line distance telemetry.
  - String serialization in SQLite ensures that reordering enum declarations in `DeviceType.java` causes zero data corruption or deserialization failures.

---

## 2. Test Specification ([TST-TRK-016], [TST-UI-227])

### Test Case 1: `DeviceType` Enum Declaration Order Invariant Test (`TST-TRK-016.1`)
* **Scope**: Automated Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/DeviceTypeEnumOrderTest.kt`
* **Preconditions**: `DeviceType` enum loaded.
* **Action**:
  - Compare `DeviceType.SPEED_AND_LOCATION_GPS.ordinal`, `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED.ordinal`, and `DeviceType.SPEED_AND_LOCATION_NETWORK.ordinal`.
* **Expected Result**:
  - `SPEED_AND_LOCATION_GPS.ordinal < SPEED_AND_LOCATION_GOOGLE_FUSED.ordinal`.
  - `SPEED_AND_LOCATION_GOOGLE_FUSED.ordinal < SPEED_AND_LOCATION_NETWORK.ordinal`.

### Test Case 2: `MySensorManager.LINE_DISTANCE_m` Priority Mapping & Best Sensor Selection Test (`TST-TRK-016.2`)
* **Scope**: Automated Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/sensor/MySensorManagerPriorityTest.kt`
* **Preconditions**: Instantiate `MySensorManager`.
* **Action**:
  - Query `mySensorManager.getPriorityList(SensorType.LINE_DISTANCE_m)`.
  - Register dummy `MySensor` for `SensorType.LINE_DISTANCE_m` on `DeviceType.SPEED_AND_LOCATION_NETWORK` and on `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED`.
  - Evaluate `mySensorManager.getBestSensor(SensorType.LINE_DISTANCE_m)`.
* **Expected Result**:
  - `getPriorityList(SensorType.LINE_DISTANCE_m)` is non-null and equals `LOCATION_PRIORITY`.
  - `getBestSensor(SensorType.LINE_DISTANCE_m)` returns the sensor associated with `SPEED_AND_LOCATION_GOOGLE_FUSED`.

### Test Case 3: `MySensorManager.getPriorityRank` Canonical Priority Helper Test (`TST-TRK-016.3`)
* **Scope**: Automated Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/sensor/MySensorManagerPriorityTest.kt`
* **Preconditions**: Static helper `MySensorManager.getPriorityRank(SensorType, DeviceType)`.
* **Action**:
  - Test Location types (`LATITUDE`, `LONGITUDE`, `ACCURACY`, `BEARING`, `LINE_DISTANCE_m`):
    - Assert `getPriorityRank(sensorType, SPEED_AND_LOCATION_GPS) == 0`.
    - Assert `getPriorityRank(sensorType, SPEED_AND_LOCATION_GOOGLE_FUSED) == 1`.
    - Assert `getPriorityRank(sensorType, SPEED_AND_LOCATION_NETWORK) == 2`.
  - Test Speed types (`SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`):
    - Assert `RUN_SPEED == 0`, `BIKE_SPEED == 1`, `BIKE_SPEED_AND_CADENCE == 2`, `BIKE_POWER == 3`, `GPS == 4`, `GOOGLE_FUSED == 5`, `NETWORK == 6`.
  - Test Altitude (`ALTITUDE`):
    - Assert `ALTITUDE_FROM_PRESSURE == 0`, `GPS == 1`, `GOOGLE_FUSED == 2`, `NETWORK == 3`.
  - Test Cadence (`CADENCE`):
    - Assert `BIKE_CADENCE == 0`, `BIKE_SPEED_AND_CADENCE == 1`, `BIKE_POWER == 2`, `RUN_CADENCE == 3`.
  - Test unmapped / fallback device:
    - Assert `getPriorityRank(SensorType.LATITUDE, DeviceType.HEART_RATE) == 1000 + DeviceType.HEART_RATE.ordinal`.
  - Test null device type:
    - Assert `getPriorityRank(SensorType.LATITUDE, null) == Integer.MAX_VALUE`.
* **Expected Result**: All assertions pass deterministically.

### Test Case 4: `DevicesDatabaseManager` Location Devices Insertion Order Test (`TST-TRK-016.4`)
* **Scope**: Robolectric Database Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManagerTest.kt`
* **Preconditions**: Clean SQLite database created with `DevicesDatabaseManager`.
* **Action**:
  - Query all location devices inserted by `addLocationDevicesToDb`.
* **Expected Result**:
  - `SPEED_AND_LOCATION_GPS` has `_id = 1` and `paired = true`.
  - `SPEED_AND_LOCATION_GOOGLE_FUSED` has `_id = 2` and `paired = true`.
  - `SPEED_AND_LOCATION_NETWORK` has `_id = 3` and `paired = false`.
  - Row ID order: `GPS < GOOGLE_FUSED < NETWORK`.

### Test Case 5: `SensorSourceDialog` Active Backups Canonical Priority Sorting Test (`TST-UI-227.1`)
* **Scope**: Automated UI / ViewModel Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialogPriorityTest.kt`
* **Preconditions**:
  - Primary source: `SPEED_AND_LOCATION_GPS` (ID 1).
  - Active telemetries containing `SPEED_AND_LOCATION_NETWORK` (ID 2, "Netzwerk") and `SPEED_AND_LOCATION_GOOGLE_FUSED` (ID 3, "Google Fused") in arbitrary/reversed order.
* **Action**:
  - Evaluate sorting logic for `activeBackups` for `SensorType.LATITUDE` and `SensorType.SPEED_mps`.
* **Expected Result**:
  - `activeBackups[0].deviceId == 3` (Google Fused).
  - `activeBackups[1].deviceId == 2` (Network).
  - Deterministic sort order regardless of input collection order.

### Test Case 6: `SensorSourceDialog` Disconnected Devices Canonical Priority Sorting Test (`TST-UI-227.2`)
* **Scope**: Automated UI / ViewModel Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialogPriorityTest.kt`
* **Preconditions**:
  - `allDevices` containing disconnected `DeviceUiData` for Network (ID 2) and Google Fused (ID 3) in database cursor order (ID 2 then ID 3).
* **Action**:
  - Evaluate sorting logic for `notConnected` for `SensorType.LATITUDE`.
* **Expected Result**:
  - `notConnected[0].id == 3` (Google Fused).
  - `notConnected[1].id == 2` (Network).
  - Google Fused is rendered before Network.

### Test Case 7: Clean-Room Full Suite Regression Execution (`TST-TRK-016.5`, `TST-UI-227.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full 3,100+ test suite with zero regressions.
* **Expected Result**: Build SUCCESSFUL, 0 failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-TRK-016.1]` | Unit | `DeviceType.ordinal()` | `REQ-TRK-024.3` | Planned |
| `[TST-TRK-016.2]` | Unit | `MySensorManager.getBestSensor(LINE_DISTANCE_m)` | `REQ-TRK-024.4` | Planned |
| `[TST-TRK-016.3]` | Unit | `MySensorManager.getPriorityRank(SensorType, DeviceType)` | `REQ-TRK-024.5` | Planned |
| `[TST-TRK-016.4]` | DB / Unit | `DevicesDatabaseManager.addLocationDevicesToDb` | `REQ-TRK-024.6` | Planned |
| `[TST-UI-227.1]` | UI / Unit | `SensorSourceDialog.activeBackups.sortedWith` | `REQ-UI-268.5` | Planned |
| `[TST-UI-227.2]` | UI / Unit | `SensorSourceDialog.notConnected.sortedWith` | `REQ-UI-268.5` | Planned |
| `[TST-TRK-016.5]` / `[TST-UI-227.3]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Planned |

> [!NOTE]
> **Traceability Alignment Note**: In `docs/requirements.md`, `REQ-TRK-024` maps to verification suite `TST-TRK-016` and `REQ-UI-268` maps to `TST-UI-227` (since `TST-TRK-024` was previously assigned to `ATT-3988` / `REQ-TRK-032`, and `TST-UI-268` to `ATT-2763` / `REQ-UI-308`). Both `TST-TRK-016` and `TST-UI-227` are synchronized in `docs/tests.md` with status `Planned` and complete Given-When-Then test cases.
