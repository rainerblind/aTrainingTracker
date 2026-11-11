# Stage 2: Requirement & Test Specification - ATT-6704: Publish RadarTrafficSimulator telemetry to scalar MySensor channels, wire hardware preemption, and expose reactive simulation state

**Ticket**: [ATT-6704](https://rainerblind.atlassian.net/browse/ATT-6704)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-CON-029.2`, `REQ-CON-029.3`, `REQ-CON-029.4`, `REQ-CON-029.5`  
**Test Spec Mapping**: `TST-CON-029.1`, `TST-CON-029.2`, `TST-CON-029.3`, `TST-CON-029.4`, `TST-CON-029.5`, `TST-CON-029.6`  
**Branch**: `feature/ATT-6704` | **Author**: AI Agent 1 (Flash) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-CON-029 Refinement)

### 1.1 Problem Statement & Rationale
Sprint 2026-46.1 review of `RadarTrafficSimulator.kt` and `RadarSettingsSection.kt` identified three architectural and usability defects:
1. **Unpublished Scalar Metrics in Indoor Simulator**: `RadarTrafficSimulator` feeds `RadarStateRepository` and its private `RadarWindowAggregator`, but registers zero sensors with `BANALService`. Consequently, all 6 scalar radar cockpit tiles (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`) display `--` and are never persisted to `WorkoutSamples.db` during indoor simulations.
2. **Unwired Hardware Preemption & Dangling Simulation on Workout Finish (`Rule 5.3`)**: `RadarTrafficSimulator.onPhysicalHardwareConnected()` and `registerHardwareActiveProvider()` have zero production callers. Connecting physical radar hardware while simulating leads to 8 Hz dual-writer state corruption, and finishing a workout in `TrackerService` fails to stop active simulation.
3. **Non-Reactive Simulation State, Untranslated Status & Wrapping Chip Row**: `isSimulating` is a non-reactive getter rather than `StateFlow<Boolean>`; elapsed time calculation relies on wall-clock `System.currentTimeMillis()` instead of monotonic `SystemClock.elapsedRealtime()`; status string hardcodes untranslated English `"${radarState.connectionState} • ${radarState.targets.size} targets"`; and `RadarScenario` selection uses a wrapping 5-chip `FlowRow` instead of `ExposedDropdownMenuBox` (Design Guidelines §1.3).

### 1.2 Functional & Architectural Requirements
1. **8 Hz Stream Pipeline & Scalar MySensor Bridge (`REQ-CON-029.2`)**: The simulator SHALL emit synthetic target frames at 8 Hz into `RadarStateRepository` (`StateFlow<RadarState>`) and update `RadarWindowAggregator`. When `BANALService` is active, `SimulatedRadarDevice` (extending `MyDevice`) SHALL register and publish the 6 scalar radar `MySensor` channels (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`), displaying live metrics in cockpit tiles and recording them to `WorkoutSamples.db` during workouts.
2. **Hardware Preemption & Workout Lifecycle Cleanliness (`REQ-CON-029.3`)**: The simulator SHALL immediately halt when physical radar hardware connects or streams data (`onPhysicalHardwareConnected()`), wired directly into `BTLERadarDevice` measurement notifications. `TrackerService` SHALL invoke `RadarTrafficSimulator.stop()` in `handleActionStop()` and `onDestroy()`, guaranteeing zero lingering simulation or phantom DB writes post-workout.
3. **Reactive StateFlow, Monotonic Clock & ExposedDropdownMenuBox Controls (`REQ-CON-029.4`)**: `RadarTrafficSimulator` SHALL expose `isSimulatingFlow: StateFlow<Boolean>` for reactive UI observation and evaluate elapsed simulation timing using monotonic `SystemClock.elapsedRealtime()` under a structured `SupervisorJob` scope. `RadarSettingsSection.kt` SHALL render `RadarScenario` selection using a Material 3 `ExposedDropdownMenuBox` per Design Guidelines §1.3.
4. **9-Language Localization Parity (`REQ-CON-029.5`)**: All simulator UI strings, scenario names, and live target status indicators in `strings_radar.xml` SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis or untranslated concatenations).

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-CON-029` (Built-in Cycling Radar Traffic Simulator & Scenario Engine), targeting `RadarTrafficSimulator.kt`, `SimulatedRadarDevice.kt`, `BANALService.java`, `BTLERadarDevice.java`, `TrackerService.java`, `RadarSettingsSection.kt`, and `strings_radar.xml`.
* `Historical Origin & Commit Trace`: Introduced in `ATT-5087` (commit `69fec8e7`) as an indoor simulator and preview tool; refined in `ATT-6704` to wire scalar `MySensor` channels via `SimulatedRadarDevice`, wire physical preemption in `BTLERadarDevice`, stop on workout finish, expose `isSimulatingFlow`, switch to monotonic clock, and upgrade scenario selection to `ExposedDropdownMenuBox`.
* `Root Reason for Existing Formulation`: The initial simulator was built as a rapid standalone prototype for visual edge overlay and audio testing prior to hardware arrival, leaving `BANALService` scalar integration and hardware preemption hooks as omissions. Wall-clock timing was used for simplicity, and `FlowRow` chips were used before options grew to 5.
* `Preservation of Core Invariants`: Physical hardware retains 100% absolute precedence; zero phantom DB writes outside active workouts; zero SQLite schema modifications; 100% 9-language translation parity.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* Single Overtaking Car scenario selected and an active indoor workout, *When* simulation runs and 1 Hz window latches, *Then* a vehicle approaches from 140m at 12 m/s, passes at 0m, increments `RADAR_VEHICLE_COUNT`, and records scalar metrics to `WorkoutSamples.db`.
* *Given* an active simulation, *When* physical radar connects and streams measurement data, *Then* `BTLERadarDevice` immediately triggers `onPhysicalHardwareConnected()`, halting the simulator and yielding control cleanly to physical hardware.
* *Given* an active simulation during a workout, *When* the workout is stopped in `TrackerService`, *Then* `RadarTrafficSimulator.stop()` is invoked, de-registering simulated sensors and halting coroutines.
* *Given* `RadarSettingsSection.kt` displayed, *When* selecting a scenario, *Then* the scenario is selected via an `ExposedDropdownMenuBox` with localized status formatting and reactive button state driven by `isSimulatingFlow`.

---

## 2. Test Specification (TST-CON-029 Refinement)

### Test Case 1: Scenario Presets & 8 Hz Stream Kinematics (`TST-CON-029.1a`)
* **Scope**: JVM Unit Test (`RadarTrafficSimulatorTest.kt`)
* **Procedure**: Execute each of the 5 scenario presets (`SINGLE_CAR`, `CONVOY`, `HIGH_SPEED`, `DISCONNECT_RECOVERY`, `COUNTRY_ROAD`) and inspect the emitted `RadarState` sequence at 8 Hz intervals.
* **Expected Result**: Verifies range and speed kinematics, multi-target tracking (convoy has 3 distinct target IDs), high-speed threat reaching `FAST_APPROACHING`, and disconnect recovery after 5 seconds.

### Test Case 2: Scalar MySensor Publishing via SimulatedRadarDevice (`TST-CON-029.2a`)
* **Scope**: JVM Unit Test (`SimulatedRadarDeviceTest.kt` / `RadarTrafficSimulatorTest.kt`)
* **Procedure**: Start `RadarTrafficSimulator` with a mocked or initialized `MySensorManager`, advance the simulation clock by > 1.0 second to trigger window aggregation latch.
* **Expected Result**: Verifies `SimulatedRadarDevice` registers 6 sensors (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`) and dispatches aggregated 1 Hz values via `MySensor.newValue()`.

### Test Case 3: SimulatedRadarDevice Lifecycle Teardown (`TST-CON-029.2b`)
* **Scope**: JVM Unit Test (`SimulatedRadarDeviceTest.kt` / `RadarTrafficSimulatorTest.kt`)
* **Procedure**: Start simulation, verify active sensors, invoke `RadarTrafficSimulator.stop()`.
* **Expected Result**: Verifies `SimulatedRadarDevice` cleanly unregisters all 6 sensors from `MySensorManager` and leaves zero dangling callbacks or memory leaks.

### Test Case 4: Hardware Preemption via BTLERadarDevice (`TST-CON-029.3a`)
* **Scope**: JVM Unit Test (`BTLERadarDeviceTest.kt`)
* **Procedure**: Initialize active simulation, simulate incoming physical radar BLE measurement notification in `BTLERadarDevice.processTargets()` / GATT callback.
* **Expected Result**: Verifies `RadarTrafficSimulator.onPhysicalHardwareConnected()` is invoked, immediately terminating simulation and clearing simulated target state before physical target frames are committed to `RadarStateRepository`.

### Test Case 5: Workout Teardown Stops Simulation (`TST-CON-029.3b`)
* **Scope**: JVM Unit Test (`TrackerServiceTest.kt` / `RadarTrafficSimulatorTest.kt`)
* **Procedure**: Start simulation, invoke `TrackerService.handleActionStop()` and `TrackerService.onDestroy()`.
* **Expected Result**: Verifies `RadarTrafficSimulator.stop()` is triggered, ensuring zero phantom DB writes or background simulation activity post-workout.

### Test Case 6: Reactive isSimulatingFlow & Monotonic Clock (`TST-CON-029.4a`)
* **Scope**: JVM Unit Test (`RadarTrafficSimulatorTest.kt`)
* **Procedure**: Observe `isSimulatingFlow` across `start()`, `stop()`, and hardware preemption. Verify simulation loop computes delta time via monotonic clock (`SystemClock.elapsedRealtime()`).
* **Expected Result**: `isSimulatingFlow` emits `true` on start and `false` immediately on stop/preemption. Loop elapsed time is immune to system wall-clock wall shifts.

### Test Case 7: ExposedDropdownMenuBox UI & Localization Parity across 9 Locales (`TST-CON-029.5a`)
* **Scope**: Compose Layout & Static Resource Audit (`TranslationParityTest.kt`)
* **Procedure**: Inspect `RadarSettingsSection.kt` for `ExposedDropdownMenuBox` implementation with single-line labels (Rule 38). Run resource test across `values` and all 8 localized `values-<locale>` directories.
* **Expected Result**: 100% key and parameter parity for `radar_simulator_status_format` and all scenario names (`radar_scenario_*`). Zero missing translations and zero raw string concatenations.

### Test Case 8: Clean-Room Targeted Radar Test Suite (`TST-CON-029.6a`)
* **Scope**: Targeted Package Test Execution
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.trainingtracker.audio.radar.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.radar.*"`
* **Expected Result**: 100% pass rate across all radar simulator, device bridge, alert, and settings tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-029.1a` | Unit | `RadarTrafficSimulatorTest.kt` | `REQ-CON-029.1` | Specified |
| `TST-CON-029.2a` | Unit | `SimulatedRadarDevice.kt` / `RadarTrafficSimulator.kt` | `REQ-CON-029.2` | Specified |
| `TST-CON-029.2b` | Unit | `SimulatedRadarDevice.kt` / `RadarTrafficSimulator.kt` | `REQ-CON-029.2` | Specified |
| `TST-CON-029.3a` | Unit | `BTLERadarDevice.java` / `RadarTrafficSimulator.kt` | `REQ-CON-029.3` | Specified |
| `TST-CON-029.3b` | Unit | `TrackerService.java` / `RadarTrafficSimulator.kt` | `REQ-CON-029.3` | Specified |
| `TST-CON-029.4a` | Unit | `RadarTrafficSimulator.kt` | `REQ-CON-029.4` | Specified |
| `TST-CON-029.5a` | Resource | `RadarSettingsSection.kt`, `strings_radar.xml` (9 locales) | `REQ-CON-029.4`, `REQ-CON-029.5` | Specified |
| `TST-CON-029.6a` | Suite | Full Radar Test Suite | `REQ-CON-029.1..5` | Specified |
