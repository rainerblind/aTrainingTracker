# Stage 2: Requirement & Test Specification - ATT-3961: No speed and distance in cockpit and not tracked

**Ticket**: [ATT-3961](https://rainerblind.atlassian.net/browse/ATT-3961)  
**Sub-task**: [ATT-3979](https://rainerblind.atlassian.net/browse/ATT-3979) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1340](https://rainerblind.atlassian.net/browse/ATT-1340) (*[Tracking/Cockpit] Real-Time Tracking & Cockpit UI*)  
**Target Release**: `TBD (Set on Completion per Rule 14)`  
**Active Sprint**: `Sprint 2026-42.8`  
**Requirement Mapping**: `REQ-TRK-024`, `REQ-TRK-025`  
**Test Spec ID**: `TST-TRK-016`, `TST-TRK-017`  
**Branch**: `feature/ATT-3961`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-TRK-024` & `REQ-TRK-025`)

### 1.1 Problem Statement & Rationale
Two interacting root causes caused missing speed and distance in the live cockpit (`-- km/h`, `-- km`, `T------AG--` without `D` or `S`) and exported workouts when `SpeedAndLocationDevice_GoogleFused` provided the active GPS track:
1. **Primary App Bug (`REQ-TRK-024`)**: `MySensorManager` (`app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java`) defines device priority arrays for `SPEED_PRIORITY` (`SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`), `ALTITUDE_PRIORITY` (`ALTITUDE`), and `LOCATION_PRIORITY` (`ACCURACY`, `BEARING`, `LATITUDE`, `LONGITUDE`). While `ALTITUDE_PRIORITY` and `LOCATION_PRIORITY` both include `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` immediately after `DeviceType.SPEED_AND_LOCATION_GPS`, `SPEED_PRIORITY` had `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` commented out. Consequently, whenever `SpeedAndLocationDevice_GoogleFused` registers its sensors and raw `SpeedAndLocationDevice_GPS` (or a dedicated wheel/footpod sensor) has not registered, `MySensorManager.getBestSensor(...)` returns `null` for `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, and `DISTANCE_m_LAP`, causing `setBestSensorForProxySensor(...)` to immediately call `removeSensor(sensorType)` and delete the `ProxySensor` for speed and distance (`T------AG--` without `D` or `S`).
2. **Secondary Tooling Trigger (`REQ-TRK-025`, companion ticket `ATT-3971`)**: `AdbGpsInjector` in `tools/replay_workout.py` previously registered `add-test-provider gps/network/fused` at startup and only called `remove-test-provider` at process exit, leaving the device's `gps` provider trapped in `[mock]` mode if unplugged while `PAUSED` or `STOPPED`.

### 1.2 Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**: Net-new requirements `REQ-TRK-024` (targeting `MySensorManager.SPEED_PRIORITY` in `MySensorManager.java`) and `REQ-TRK-025` (targeting `AdbGpsInjector` mock provider lifecycle in `tools/replay_workout.py`, amending `REQ-TOOL-002`).
2. **Historical Origin & Commit Trace**:
   * `MySensorManager.java`: Legacy `SPEED_PRIORITY` definition where `// DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` was commented out on line 48 above `DeviceType.SPEED_AND_LOCATION_GPS`.
   * `tools/replay_workout.py`: Sprint 2026-41.6 (`ATT-2969`, `REQ-TOOL-002`, commit `1523f683`).
3. **Root Reason for Existing Formulation**:
   * In `MySensorManager.java`, the original author wanted raw `SPEED_AND_LOCATION_GPS` to take precedence over `SPEED_AND_LOCATION_GOOGLE_FUSED` when both were active, and commented out `SPEED_AND_LOCATION_GOOGLE_FUSED` (which was listed *above* `SPEED_AND_LOCATION_GPS`) instead of moving it *below* `SPEED_AND_LOCATION_GPS` as was done in `ALTITUDE_PRIORITY` (lines 62–63) and `LOCATION_PRIORITY` (lines 68–69).
   * In `tools/replay_workout.py`, `AdbGpsInjector.setup()` originally registered test providers once at startup assuming the script would always be closed before USB disconnection.
4. **Preservation of Core Invariants**:
   * Placing `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` immediately *after* `DeviceType.SPEED_AND_LOCATION_GPS` in `SPEED_PRIORITY` preserves `RUN_SPEED > BIKE_SPEED > BIKE_SPEED_AND_CADENCE > BIKE_POWER > SPEED_AND_LOCATION_GPS` precedence and $O(1)$ bounded `ProxySensor` listener dispatch without latency spikes, while guaranteeing `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, and `DISTANCE_m_LAP` remain active when Google Fused provides locations.
   * Binding `add-test-provider` strictly to `PLAYING` state and executing `remove-test-provider` immediately on `PAUSED`, `STOPPED`, `kill_gps`, and timeline completion (`EOF`) ensures deterministic `AdbGpsInjector` state and prevents lingering `[mock]` providers.

### 1.3 Functional & Architectural Requirements
1. **`SPEED_PRIORITY` Alignment with `LOCATION_PRIORITY` and `ALTITUDE_PRIORITY` (`REQ-TRK-024.1`)**:
   * `MySensorManager.SPEED_PRIORITY` SHALL include `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` immediately after `DeviceType.SPEED_AND_LOCATION_GPS` and immediately before `DeviceType.SPEED_AND_LOCATION_NETWORK`:
     ```java
     protected static final DeviceType[] SPEED_PRIORITY
             = new DeviceType[]{
             DeviceType.RUN_SPEED,
             DeviceType.BIKE_SPEED,
             DeviceType.BIKE_SPEED_AND_CADENCE,
             DeviceType.BIKE_POWER,
             DeviceType.SPEED_AND_LOCATION_GPS,
             DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED,
             DeviceType.SPEED_AND_LOCATION_NETWORK
     };
     ```
2. **ProxySensor Retention, Zero-Latency Update Loop & Dynamic Source Upgrade/Downgrade (`REQ-TRK-024.2`)**:
   * When a `MyDevice` with `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` registers sensors for `SensorType.SPEED_mps`, `SensorType.PACE_spm`, `SensorType.DISTANCE_m`, and `SensorType.DISTANCE_m_LAP` in `MySensorManager`, `MySensorManager.getBestSensor(sensorType)` SHALL return the Google Fused sensor (when no higher-priority device is registered), `MySensorManager.getSensor(sensorType)` SHALL retain an active `ProxySensor`, and `SensorType.SENSORS` (`mSensorsSensor`) SHALL include `'D'` (`DISTANCE_m` at index `1`) and `'S'` (`SPEED_mps` at index `2`).
   * When a higher-priority device (`RUN_SPEED`, `BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`, or `SPEED_AND_LOCATION_GPS`) subsequently registers or unregisters `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, or `DISTANCE_m_LAP`, `MySensorManager` SHALL switch or fall back the `ProxySensor` source in bounded $O(1)$ time over the 7-element `EnumMap` priority list without introducing latency spikes in the `ProxySensor` update loop.
3. **Deterministic `AdbGpsInjector` Mock Provider Lifecycle (`REQ-TRK-025.1` — `ATT-3961` / `ATT-3971`)**:
   * `AdbGpsInjector` (`tools/replay_workout.py`) SHALL proactively clear leftover `gps`, `network`, and `fused` test providers on startup (`remove-test-provider` while `STOPPED`), SHALL register `add-test-provider` only when entering `PLAYING` (when `not is_killed`), and SHALL immediately execute `remove-test-provider gps/network/fused` upon transitioning to `PAUSED`, `STOPPED`, `kill_gps`, or timeline completion (`EOF`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (`SPEED_AND_LOCATION_GOOGLE_FUSED` Registration — `REQ-TRK-024.1`, `REQ-TRK-024.2`)**:
  * *Given* a `MySensorManager` instance with no wheel, footpod, or raw `GPS` sensor registered,
  * *When* a `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` device registers `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`, `LATITUDE`, `LONGITUDE`, and `ALTITUDE`,
  * *Then* `getBestSensor` and `getSensor` return non-null sensors for all 7 sensor types, and `getSensor(SensorType.SENSORS).getValue()` reflects `'D'`, `'S'`, `'A'`, and `'G'` (`"-DS----AG--"`).
* **Criterion 2 (Priority Hierarchy & Zero-Latency Fallback — `REQ-TRK-024.2`)**:
  * *Given* a `MySensorManager` with `SPEED_AND_LOCATION_NETWORK` and `SPEED_AND_LOCATION_GOOGLE_FUSED` registered,
  * *When* `SPEED_AND_LOCATION_GPS` registers and later unregisters (or `BIKE_SPEED` registers and later unregisters),
  * *Then* `getBestSensor(SensorType.SPEED_mps)` and `getBestSensor(SensorType.DISTANCE_m)` select `BIKE_SPEED > SPEED_AND_LOCATION_GPS > SPEED_AND_LOCATION_GOOGLE_FUSED > SPEED_AND_LOCATION_NETWORK` with immediate `ProxySensor` value propagation.
* **Criterion 3 (Deterministic Mock Provider Cleanup on `PAUSED` / `STOPPED` / `EOF` — `REQ-TRK-025.1`)**:
  * *Given* an active `AdbGpsInjector` during workout replay,
  * *When* playback transitions to `PAUSED`, `STOPPED`, `kill_gps`, or reaches `EOF`,
  * *Then* `remove-test-provider` is invoked immediately for `gps`, `network`, and `fused` so no lingering `[mock]` provider remains on the device.

### 1.5 System Invariants
* Zero regression in existing `MySensorManager` priority rules (`RUN_SPEED > BIKE_SPEED > BIKE_SPEED_AND_CADENCE > BIKE_POWER > SPEED_AND_LOCATION_GPS > SPEED_AND_LOCATION_GOOGLE_FUSED > SPEED_AND_LOCATION_NETWORK`).
* Bounded $O(1)$ `ProxySensor` source switching with zero latency spikes in the sensor update loop.
* Zero new string resources required (9-locale parity remains 100% intact).
* 100% pass rate on `./gradlew testDebugUnitTest` and `python3 -m unittest tools/test_replay_workout.py`.

---

## 2. Test Specification (`TST-TRK-016` & `TST-TRK-017`)

### Test Case 1: `speedPriority_includesGoogleFusedAfterGpsAndBeforeNetwork` (`TST-TRK-016.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/sensor/MySensorManagerPriorityTest.kt`
* **Action**: Inspect `MySensorManager.SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY`.
* **Expected Result**: `SPEED_PRIORITY` equals `[RUN_SPEED, BIKE_SPEED, BIKE_SPEED_AND_CADENCE, BIKE_POWER, SPEED_AND_LOCATION_GPS, SPEED_AND_LOCATION_GOOGLE_FUSED, SPEED_AND_LOCATION_NETWORK]`, matching the relative location ordering (`SPEED_AND_LOCATION_GPS`, `SPEED_AND_LOCATION_GOOGLE_FUSED`, `SPEED_AND_LOCATION_NETWORK`) of `ALTITUDE_PRIORITY` and `LOCATION_PRIORITY`.

### Test Case 2: `registerGoogleFusedSensors_retainsSpeedAndDistanceProxySensorsAndUpdatesGcData` (`TST-TRK-016.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/sensor/MySensorManagerPriorityTest.kt`
* **Action**: Register `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`, `ALTITUDE`, `LATITUDE`, and `LONGITUDE` from a `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` device on `MySensorManager` and emit values (`speed = 6.5`, `distance = 2896.48`).
* **Expected Result**: `getBestSensor` returns the Google Fused sensors for all 4 speed/distance types; `getSensor(SPEED_mps)` and `getSensor(DISTANCE_m)` return active `ProxySensor` instances delivering `6.5` and `2896.48`; `getSensor(SensorType.SENSORS).getValue()` has `'D'` at index `1` and `'S'` at index `2` (`"-DS----AG--"`).

### Test Case 3: `speedAndDistancePriority_upgradesToGpsAndBikeSpeedAndFallsBackToGoogleFused` (`TST-TRK-016.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/sensor/MySensorManagerPriorityTest.kt`
* **Action**: Register `SPEED_mps` and `DISTANCE_m` across `SPEED_AND_LOCATION_NETWORK`, `SPEED_AND_LOCATION_GOOGLE_FUSED`, `SPEED_AND_LOCATION_GPS`, and `BIKE_SPEED`, then unregister in reverse order.
* **Expected Result**: `getBestSensor` and the `ProxySensor` value dynamically track `BIKE_SPEED` -> `SPEED_AND_LOCATION_GPS` -> `SPEED_AND_LOCATION_GOOGLE_FUSED` -> `SPEED_AND_LOCATION_NETWORK`.

### Test Case 4: `test_adb_gps_injector_removes_test_providers_on_pause_stop_and_eof` (`TST-TRK-017.1`)
* **Scope**: Unit Test
* **Target File**: `tools/test_replay_workout.py`
* **Action**: Simulate startup cleanup, transition to `PLAYING` (`inject_location`), and transition to `PAUSED`, `STOPPED`, `kill_gps`, and timeline `EOF` via mocked ADB transport.
* **Expected Result**: Startup clears any lingering test providers (`remove-test-provider`), `add-test-provider` is invoked lazily when entering `PLAYING`, and `remove-test-provider gps/network/fused` is executed immediately on `PAUSED`, `STOPPED`, `kill_gps`, and `EOF`.

### Test Case 5: Clean-Room Regression Suite (`TST-TRK-016.4` / `TST-TRK-017.2`)
* **Command**: `./gradlew testDebugUnitTest && python3 -m unittest tools/test_replay_workout.py`
* **Goal**: Verify 100% pass rate across the full Android and Python test suites.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-016.1` | Unit | `MySensorManager.SPEED_PRIORITY` | `REQ-TRK-024.1` | Specified |
| `TST-TRK-016.2` | Unit | `MySensorManager.registerSensor` / `setBestSensorForProxySensor` | `REQ-TRK-024.2` | Specified |
| `TST-TRK-016.3` | Unit | `MySensorManager.getBestSensor` / `unregisterSensor` | `REQ-TRK-024.1`, `REQ-TRK-024.2` | Specified |
| `TST-TRK-017.1` | Unit | `AdbGpsInjector` lifecycle (`tools/replay_workout.py`) | `REQ-TRK-025.1` | Specified |
| `TST-TRK-016.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
