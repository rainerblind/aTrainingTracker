# Stage 4/5 Walkthrough: ATT-6704 - Publish RadarTrafficSimulator telemetry to scalar MySensor channels, wire hardware preemption, and expose reactive simulation state

**Ticket**: [ATT-6704](https://rainerblind.atlassian.net/browse/ATT-6704)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-CON-029.2`, `REQ-CON-029.3`, `REQ-CON-029.4`, `REQ-CON-029.5`  
**Test Spec Mapping**: `TST-CON-029.1a`, `TST-CON-029.2a`, `TST-CON-029.2b`, `TST-CON-029.3a`, `TST-CON-029.3b`, `TST-CON-029.4a`, `TST-CON-029.5a`, `TST-CON-029.6a`  
**Author**: AI Agent 1 (Flash) | **Date**: 2026-11-11  

---

## 1. Problem & Scope Overview
Sprint 2026-46.1 review identified three architectural and usability defects in `RadarTrafficSimulator.kt` and `RadarSettingsSection.kt`:
1. `RadarTrafficSimulator` updated `RadarStateRepository` and its private `RadarWindowAggregator`, but registered zero sensors with `BANALService`. Consequently, all 6 scalar radar cockpit tiles (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`) displayed `--` and were never persisted to `WorkoutSamples.db` during indoor simulations.
2. Hardware preemption and workout stop guards were unwired: physical radar packet arrival did not halt simulation, causing dual-writer state collisions at 8 Hz, and ending a workout in `TrackerService` failed to stop active simulation.
3. `isSimulating` was a non-reactive getter; simulation timing used wall-clock `System.currentTimeMillis()` under unmanaged coroutine scopes; status text hardcoded untranslated English `"${radarState.connectionState} • ${radarState.targets.size} targets"`; and 5 scenario chips wrapped across multiple lines.

---

## 2. Key Changes Implemented

1. **SimulatedRadarDevice & BANALService Bridge (`SimulatedRadarDevice.kt` & `BANALService.java`)**:
   - Implemented `SimulatedRadarDevice(context, mySensorManager)` extending `MyDevice(context, mySensorManager, DeviceType.BIKE_RADAR)` in `com.atrainingtracker.banalservice.devices.radar`.
   - Registers all 6 scalar radar sensors (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`).
   - Dispatches latched 1 Hz snapshots (`RadarLatchedSnapshot`) to `MySensor.newValue()`, enabling live cockpit grid tiles and `WorkoutSamples.db` recording during indoor simulation.
   - Exposed static accessors `BANALService.getSensorManager()` and `BANALService.getServiceContext()` with `@VisibleForTesting` hooks.
   - Cleanly unregisters sensors upon `shutDown()`.

2. **Reactive Flow, Monotonic Timing & Simulator Bridge (`RadarTrafficSimulator.kt`)**:
   - Exposed `isSimulatingFlow: StateFlow<Boolean>` for reactive Compose observation, preserving `isSimulating: Boolean` property.
   - Replaced wall-clock `System.currentTimeMillis()` with monotonic `clock: () -> Long` (`SystemClock.elapsedRealtime()` on Android with JVM `nanoTime` fallback).
   - Replaced unstructured ad-hoc `CoroutineScope` with managed `SupervisorJob() + Dispatchers.Default`.
   - Wired `SimulatedRadarDevice` lifecycle: instantiates device on `start()` when `BANALService` is active, pushes 1 Hz window snapshots on each tick, and cleanly shuts down on `stop()`.

3. **Hardware Preemption & Workout Teardown Cleanliness (`BTLERadarDevice.java` & `TrackerService.java`)**:
   - In `BTLERadarDevice.processTargets()`: invokes `RadarTrafficSimulator.INSTANCE.onPhysicalHardwareConnected()` upon physical radar measurement packet arrival, immediately halting simulation before physical targets are processed.
   - In `TrackerService.handleActionStop()` and `TrackerService.onDestroy()`: invokes `RadarTrafficSimulator.INSTANCE.stop()`, guaranteeing zero lingering simulation or phantom DB writes post-workout.

4. **Material 3 ExposedDropdownMenuBox & 9-Language Localization (`RadarSettingsSection.kt` & `strings_radar.xml`)**:
   - Subscribed to `isSimulatingFlow` via `collectAsState()` in `RadarSettingsSection.kt`, ensuring immediate reactive recomposition on asynchronous stop or preemption.
   - Replaced 5-chip `FlowRow` with Material 3 `ExposedDropdownMenuBox` with single-line labels and ellipsis truncation (Design Guidelines §1.3, Rule 38).
   - Localized live simulation status using string resource `radar_simulator_status` (`"%1$s • %2$d targets"`) and `radar_simulator_scenario_label` across all 9 supported language directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

5. **Test Suite Expansion**:
   - `SimulatedRadarDeviceTest.kt`: 5 unit tests validating 6-sensor registration, snapshot metrics publishing, `resetSensorsToClear()` behavior, accumulator reset, and clean shutdown unregistration.
   - `RadarTrafficSimulatorTest.kt`: added `testIsSimulatingFlow_reactiveTransitions`, `testOnPhysicalHardwareConnected_preemptsImmediately`, and `testTick_updatesSimulatedRadarDeviceWhenPresent`.
   - `BTLERadarDeviceTest.kt`: added `testProcessTargets_haltsActiveRadarTrafficSimulator` asserting real hardware preemption.
   - `TrackerServiceFinalizationTest.kt`: added assertion verifying `onDestroy()` terminates active simulation.
   - `TranslationParityTest.kt`: verified 100% parity across all 9 localized string resource shards.

---

## 3. Verification & Test Evidence

| Test Suite | Scope | Target | Result |
| :--- | :--- | :--- | :--- |
| `SimulatedRadarDeviceTest` | Unit | 6-sensor registration, snapshot dispatch, clear/reset, teardown | **PASS** (5/5 tests) |
| `RadarTrafficSimulatorTest` | Unit | Scenarios, kinematics, `isSimulatingFlow`, preemption, monotonic clock | **PASS** (10/10 tests) |
| `BTLERadarDeviceTest` | Unit | Physical measurement packet decoding, hardware preemption hook | **PASS** (17/17 tests) |
| `TrackerServiceFinalizationTest` | Unit | Workout stop/destroy teardown stops radar simulation | **PASS** (11/11 tests) |
| `TranslationParityTest` | Localization | 9-locale key, placeholder and format parity audit | **PASS** (4/4 tests) |
| `verify_requirement_governance.py` | Governance | Living doc master table sync, Chesterton's Fence archaeology audit | **PASS** (100% parity) |

---

## 4. Conclusion & Next Steps
All requirements for `REQ-CON-029.2`, `REQ-CON-029.3`, `REQ-CON-029.4`, and `REQ-CON-029.5` have been implemented, tested, and verified.
Submitting Stage 4 Construction for Gate 4 Review by agent2 (`subagent_pro`), parent advancement to `Test`, and fused Stage 5 Verification.

---

## 5. Cold Carbon Review (Stage 6) Defect Resolution & Healing
* **Finding**: The Stage 3 implementation plan artifact was mistakenly named `docs/engineering/plans/ATT-6704_impl_plan.md` instead of the canonical naming pattern `docs/engineering/plans/ATT-6704_plan.md`.
* **Resolution**: The plan file was canonically renamed to `docs/engineering/plans/ATT-6704_plan.md` in commit `ee4b955c`, restoring full conformance with agile ASPICE naming conventions. All requirements, sensor channels, preemption hooks, and test assertions verified with 100% test pass.
