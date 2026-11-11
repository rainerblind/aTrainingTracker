# Stage 3: Implementation Plan - ATT-6704: Publish RadarTrafficSimulator telemetry to scalar MySensor channels, wire hardware preemption, and expose reactive simulation state

**Ticket**: [ATT-6704](https://rainerblind.atlassian.net/browse/ATT-6704)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-CON-029.2`, `REQ-CON-029.3`, `REQ-CON-029.4`, `REQ-CON-029.5`  
**Test Spec Mapping**: `TST-CON-029.1a`, `TST-CON-029.2a`, `TST-CON-029.2b`, `TST-CON-029.3a`, `TST-CON-029.3b`, `TST-CON-029.4a`, `TST-CON-029.5a`, `TST-CON-029.6a`  
**Branch**: `feature/ATT-6704`  
**Author**: AI Agent 1 (Implementer - Pro) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Sprint 2026-46.1 review identified three architectural and usability defects in `RadarTrafficSimulator.kt` and `RadarSettingsSection.kt`:
1. `RadarTrafficSimulator` updates `RadarStateRepository` and its internal `RadarWindowAggregator`, but registers zero sensors with `BANALService`. Cockpit tiles display `--` and metrics are omitted from `WorkoutSamples.db`.
2. Hardware preemption and workout stop guards are unwired: physical radar packet arrival does not halt simulation, causing dual-writer state collisions at 8 Hz, and ending a workout in `TrackerService` leaves simulation running.
3. `isSimulating` is a non-reactive getter; simulation timing uses wall-clock `System.currentTimeMillis()` under unmanaged coroutine scopes; status text hardcodes untranslated English; and 5 scenario chips wrap across lines.

---

## 2. Traceability & Requirements Mapping
* **REQ-CON-029.2 (Scalar MySensor Bridge & 8 Hz Aggregation)**: Implement `SimulatedRadarDevice` extending `MyDevice`, register with `MySensorManager`, and publish 6 scalar radar channels (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`) -> `TST-CON-029.1a`, `TST-CON-029.2a`, `TST-CON-029.2b`
* **REQ-CON-029.3 (Hardware Preemption & Workout Teardown Cleanliness)**: Wire `RadarTrafficSimulator.onPhysicalHardwareConnected()` in `BTLERadarDevice.java` packet processing; invoke `RadarTrafficSimulator.stop()` in `TrackerService.java` `handleActionStop()` and `onDestroy()` -> `TST-CON-029.3a`, `TST-CON-029.3b`
* **REQ-CON-029.4 (Reactive StateFlow, Monotonic Timing & ExposedDropdownMenuBox)**: Expose `isSimulatingFlow: StateFlow<Boolean>`, switch loop timing to `SystemClock.elapsedRealtime()`, use managed `SupervisorJob() + Dispatchers.Default`, and replace `FlowRow` chips with Material 3 `ExposedDropdownMenuBox` -> `TST-CON-029.4a`, `TST-CON-029.5a`
* **REQ-CON-029.5 (9-Language Parity & Localized Status)**: Localize `radar_simulator_status` and `radar_simulator_scenario_label` across EN, DE, ES, FR, IT, JA, NL, PL, PT -> `TST-CON-029.5a`

---

## 3. System Invariants & Preserved Behavior
1. **Physical Precedence**: Physical radar hardware packets immediately preempt and teardown simulated telemetry.
2. **Zero Post-Workout Leakage**: Workout termination in `TrackerService` guarantees zero lingering simulation coroutines or phantom DB writes.
3. **Database Schema Stability**: Reuses existing scalar `SensorType` channels without requiring SQLite migrations.
4. **Design System & Localization Parity**: Single-line text enforcement (Rule 38); 100% 9-language translation parity.

---

## 4. UI Consistency (Design Guidelines Section 5 & Rule 38)
* **Reference Component**: Material 3 `ExposedDropdownMenuBox` with `OutlinedTextField` and `DropdownMenuItem`.
* **Styling & Theme Tokens**: `ExposedDropdownMenuDefaults.outlinedTextFieldColors()`, `MaterialTheme.typography.bodyMedium`, `maxLines = 1`, `TextOverflow.Ellipsis`.
* **Form Factor Compliance**: Replaces 5-item wrapping `FlowRow` (`> 4` mutually exclusive options -> `ExposedDropdownMenuBox` per Design Guidelines §1.3).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement SimulatedRadarDevice & Expose BANALService Accessor
* Implement `SimulatedRadarDevice.kt` in `com.atrainingtracker.banalservice.devices.radar` extending `MyDevice(context, sensorManager, DeviceType.BIKE_RADAR)`.
* Register 6 scalar radar sensors (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`) and expose `updateMetrics(RadarLatchedSnapshot)`.
* Expose public static `BANALService.getSensorManager()` and `BANALService.getServiceContext()`.

### Step 2: Wire SimulatedRadarDevice Bridge & Monotonic Timing in RadarTrafficSimulator
* Update `RadarTrafficSimulator.kt`: expose `val isSimulatingFlow: StateFlow<Boolean>`.
* In `start(scenario, context = null)`: instantiate `SimulatedRadarDevice` if `BANALService.getSensorManager()` is available; calculate loop elapsed time via `SystemClock.elapsedRealtime()`; dispatch latched snapshots to `simulatedDevice?.updateMetrics(snapshot)` at each tick.
* In `stop()`: shut down `simulatedDevice?.shutDown()`, set `_isSimulatingFlow.value = false`, and cancel the active simulation job.

### Step 3: Wire Hardware Preemption & Service Teardown Guards
* In `BTLERadarDevice.java`: invoke `RadarTrafficSimulator.INSTANCE.onPhysicalHardwareConnected()` upon physical packet reception (`measurementCharacteristicUpdate` / `processTargets`).
* In `TrackerService.java`: invoke `RadarTrafficSimulator.INSTANCE.stop()` in `handleActionStop()` and `onDestroy()`.

### Step 4: ExposedDropdownMenuBox & Status Localization in RadarSettingsSection
* In `RadarSettingsSection.kt`: collect `isSimulatingFlow` as state; replace 5-chip `FlowRow` with Material 3 `ExposedDropdownMenuBox` with single-line titles.
* Localize live status using string resource `radar_simulator_status` (`"%1$s • %2$d targets"`).
* In all 9 `strings_radar.xml` shards, add `radar_simulator_status` and `radar_simulator_scenario_label`.

### Step 5: Test Suite Expansion & Clean-Room Verification
* Add unit tests in `RadarTrafficSimulatorTest.kt` verifying `isSimulatingFlow`, `SimulatedRadarDevice` lifecycle/publishing, monotonic elapsed timing, and hardware preemption.
* Add unit tests in `BTLERadarDeviceTest.kt` and `TrackerServiceTest.kt` verifying preemption on packet arrival and stop on workout teardown.
* Run targeted tests and translation parity tests under `/tmp/att_build_merge.lock`.

---

## 6. Verification & Rollback Plan
* **Verification Command**:
  `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.trainingtracker.audio.radar.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Rollback Plan**: `git checkout feature/ATT-6704` to restore clean state.
