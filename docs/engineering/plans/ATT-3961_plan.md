# Stage 3: Implementation Plan - ATT-3961: No speed and distance in cockpit and not tracked

**Ticket**: [ATT-3961](https://rainerblind.atlassian.net/browse/ATT-3961)  
**Sub-task**: [ATT-4034](https://rainerblind.atlassian.net/browse/ATT-4034) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1340](https://rainerblind.atlassian.net/browse/ATT-1340) (*[Tracking/Cockpit] Real-Time Tracking & Cockpit UI*)  
**Target Release**: `TBD (Set on Completion per Rule 14)`  
**Active Sprint**: `Sprint 2026-42.8`  
**Requirement Mapping**: `REQ-TRK-024`, `REQ-TRK-025`  
**Test Mapping**: `TST-TRK-016`, `TST-TRK-017`  
**Branch**: `feature/ATT-3961`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

Two interacting mechanisms caused missing speed (`-- km/h`) and distance (`-- km`) in the live cockpit (`T------AG--` without `D` or `S`) and exported workouts when `SpeedAndLocationDevice_GoogleFused` served as the active location provider:
1. **Primary App Bug (`REQ-TRK-024`)**: In `MySensorManager.java` (lines 42–51), `SPEED_PRIORITY` had `// DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` commented out while `ALTITUDE_PRIORITY` (lines 59–65) and `LOCATION_PRIORITY` (lines 66–71) both included `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` immediately after `DeviceType.SPEED_AND_LOCATION_GPS`. Consequently, when `SpeedAndLocationDevice_GoogleFused` registered `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, and `DISTANCE_m_LAP` without a raw `GPS` or wheel/footpod sensor registered, `MySensorManager.getBestSensor(...)` returned `null` and `setBestSensorForProxySensor(...)` called `removeSensor(sensorType)` (line 271), deleting the `ProxySensor` for speed and distance (`T------AG--`).
2. **Secondary Tooling Trigger (`REQ-TRK-025`, companion ticket `ATT-3971`)**: In `tools/replay_workout.py`, `AdbGpsInjector.setup()` registered `add-test-provider gps/network/fused` at startup (`STOPPED`) and only executed `remove-test-provider` in `teardown()` at process exit. Unplugging the phone while `PAUSED` or `STOPPED` left `LocationManager.GPS_PROVIDER` stuck in `[mock]` mode (`gps provider [mock]`), suppressing hardware GNSS fixes for `SpeedAndLocationDevice_GPS`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**:
  * `REQ-TRK-024` (*MySensorManager Speed & Distance Priority Hierarchy for Google Fused Location (`SPEED_PRIORITY`)*)
  * `REQ-TRK-025` (*Deterministic ADB Mock Location Provider Lifecycle on Startup, Pause, Stop & EOF*)
* **Test Mapping**:
  * `TST-TRK-016` (*MySensorManager Speed & Distance Priority & ProxySensor Retention Test*)
  * `TST-TRK-017` (*AdbGpsInjector Mock Location Provider Lifecycle & Cleanup Test*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing priority rules (`RUN_SPEED > BIKE_SPEED > BIKE_SPEED_AND_CADENCE > BIKE_POWER > SPEED_AND_LOCATION_GPS > SPEED_AND_LOCATION_GOOGLE_FUSED > SPEED_AND_LOCATION_NETWORK`) and all existing test suites continue to pass cleanly.
2. **Bounded $O(1)$ Sensor Switching**: `MySensorManager.getBestSensor(sensorType)` iterates over a bounded 7-element `LinkedList<DeviceType>` in `EnumMap<SensorType, LinkedList<DeviceType>>`, preserving $O(1)$ source switching with zero latency spikes in the `ProxySensor` update loop.
3. **Thread Safety & Dispatcher Affinity**: No database or coroutine dispatcher changes are introduced.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `MySensorManager.SPEED_PRIORITY` (`app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java`)
* **Layer**: Service / Sensor Management (`com.atrainingtracker.banalservice.sensor`).
* **Modification**:
  * Uncomment `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` and position it immediately after `DeviceType.SPEED_AND_LOCATION_GPS` and immediately before `DeviceType.SPEED_AND_LOCATION_NETWORK` in `SPEED_PRIORITY`:
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
  * This brings `SPEED_PRIORITY` into 1:1 structural parity with `ALTITUDE_PRIORITY` and `LOCATION_PRIORITY` and ensures `ProxySensor` instances for `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, and `DISTANCE_m_LAP` are retained when Google Fused provides locations.

### Component 2: `AdbGpsInjector` Lifecycle & Cleanup (`tools/replay_workout.py`)
* **Layer**: Developer Tooling (`tools/replay_workout.py`).
* **Modification**:
  * Add `register_providers()`, `release_providers()`, and `cleanup_test_providers(device_serial)` on `AdbGpsInjector`:
    * `setup()` grants `android:mock_location allow`, proactively calls `release_providers()` to clear any leftover `[mock]` providers from previous sessions, and starts the worker thread while keeping `is_registered = False` in `STOPPED` state.
    * `register_providers()` executes `add-test-provider` and `set-test-provider-enabled true` for `gps`, `network`, and `fused` and sets `is_registered = True` when entering `PLAYING` (when `not self.is_killed`).
    * `release_providers()` drains queued fixes, executes `set-test-provider-enabled <p> false ; remove-test-provider <p>` for `gps`, `network`, and `fused`, and sets `is_registered = False` immediately on `pause()`, `stop()`, `kill()`, `teardown()`, and timeline `EOF`.
    * `revive()` re-registers providers if `status == "PLAYING"`.
    * Add `--cleanup` CLI flag to `build_arg_parser()` and `main()` to allow immediate standalone cleanup of stuck `[mock]` providers and exit `0`.

### Component 3: Unit Test Suites (`MySensorManagerPriorityTest.kt` & `tools/test_replay_workout.py`)
* **Files**:
  * `app/src/test/java/com/atrainingtracker/banalservice/sensor/MySensorManagerPriorityTest.kt` (new unit test class verifying `TST-TRK-016.1`, `TST-TRK-016.2`, `TST-TRK-016.3`).
  * `tools/test_replay_workout.py` (unit tests verifying `TST-TRK-017.1`: startup cleanup, lazy registration on `PLAYING`, immediate `remove-test-provider` on `PAUSED`, `STOPPED`, `kill`, `EOF`, and `--cleanup`).

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* No UI changes. Once `MySensorManager.SPEED_PRIORITY` retains `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, and `DISTANCE_m_LAP` for `SPEED_AND_LOCATION_GOOGLE_FUSED`, existing cockpit sensor fields and `TrackerService` database tracking automatically receive live speed and distance values.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Mandatory Gate 3 Pre-Check (Rule 4)
* Command: `python3 tools/jira_util.py check-gate ATT-4034`
* Verify `GATE_PASSED: ATT-4034 is Erledigt` before modifying any production file.

### Step 2: Update `MySensorManager.SPEED_PRIORITY`
* File: `app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java`
* Changes: Move and uncomment `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` immediately below `DeviceType.SPEED_AND_LOCATION_GPS` in `SPEED_PRIORITY`, with JavaDoc documenting the priority hierarchy (`REQ-TRK-024`).

### Step 3: Update `AdbGpsInjector` Mock Provider Lifecycle in `tools/replay_workout.py`
* File: `tools/replay_workout.py`
* Changes:
  * Update `AdbGpsInjector` so `setup()` clears leftover test providers instead of registering them while `STOPPED`.
  * Add `register_providers()` and `release_providers()` and wire `ReplayEngine` / `ReplayGuiWindow` / `run_headless_cli` / `kill()` / `revive()` so test providers are registered only during `PLAYING` and immediately removed on `PAUSED`, `STOPPED`, `kill`, and `EOF`.
  * Add `--cleanup` CLI option to `build_arg_parser()` and `main()`.

### Step 4: Create `MySensorManagerPriorityTest.kt` and Update `tools/test_replay_workout.py`
* Files:
  * `app/src/test/java/com/atrainingtracker/banalservice/sensor/MySensorManagerPriorityTest.kt`
  * `tools/test_replay_workout.py`
* Changes: Implement `TST-TRK-016.1`, `TST-TRK-016.2`, `TST-TRK-016.3`, and `TST-TRK-017.1`.

### Step 5: Execute Targeted Unit Tests
* Commands:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.MySensorManagerPriorityTest"`
  * `python3 -m unittest tools/test_replay_workout.py`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests (`MySensorManagerPriorityTest` and `tools/test_replay_workout.py`) during Stage 4 construction, followed by the full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3961` allows clean revert without affecting `sprint/2026-42.8` or `develop`.
