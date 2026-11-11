# Stage 1: Problem Domain & Root Cause Analysis - ATT-6704: Publish RadarTrafficSimulator telemetry to scalar MySensor channels, wire hardware preemption, and expose reactive simulation state

**Ticket**: [ATT-6704](https://rainerblind.atlassian.net/browse/ATT-6704)  
**Sub-task**: [ATT-6996](https://rainerblind.atlassian.net/browse/ATT-6996) (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-CON-029.2`, `REQ-CON-029.3`, `REQ-CON-029.4`, `REQ-CON-029.5`  
**Test Spec ID**: `TST-CON-029`  
**Branch**: `feature/ATT-6704`  
**Author**: AI Agent 1 (Analyst - Pro) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Symptoms
Sprint 2026-46.1 review of `RadarTrafficSimulator.kt` and `RadarSettingsSection.kt` identified three architectural and usability defects:
1. **Unpublished Scalar Metrics in Indoor Simulator**: `RadarTrafficSimulator` feeds `RadarStateRepository` (visual edge overlay) and its private `RadarWindowAggregator`, but registers zero sensors with `BANALService`. Consequently, all 6 scalar radar cockpit tiles (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`) display `--` and are never persisted to `WorkoutSamples.db`.
2. **Unwired Hardware Preemption & Dangling Simulation on Workout Finish (`Rule 5.3`)**: `RadarTrafficSimulator.onPhysicalHardwareConnected()` and `registerHardwareActiveProvider()` have zero production callers. Connecting physical radar hardware while simulating leads to 8 Hz dual-writer state corruption, and finishing a workout fails to stop active simulation.
3. **Non-Reactive Simulation State, Untranslated Status & Wrapping Chip Row**: `isSimulating` is a non-reactive getter rather than `StateFlow<Boolean>`; elapsed time calculation relies on wall-clock `System.currentTimeMillis()` instead of monotonic `SystemClock.elapsedRealtime()`; status string hardcodes untranslated English `"${radarState.connectionState} • ${radarState.targets.size} targets"`; and `RadarScenario` selection uses a wrapping 5-chip `FlowRow` instead of `ExposedDropdownMenuBox` (Design Guidelines §1.3).

---

## 2. Forensic Root Cause Analysis & Call Site Audit
1. **Missing BANALService Bridge in `RadarTrafficSimulator.kt` (`lines 400–420`)**:
   `RadarTrafficSimulator` runs in isolation from `BANALService` and `MySensorManager`. `BTLERadarDevice.java` (`line 112`) binds 6 `MySensor` instances to `BANALService.cSensorManager`. Without a simulated `MyDevice` bridge, `TrackerService`'s 1 Hz recording loop receives no radar sensor data.
2. **Missing Preemption Calls in `BTLERadarDevice.java` (`lines 269–287`) & `TrackerService.java` (`line 1195`)**:
   Neither `measurementCharacteristicUpdate()` nor GATT connection callbacks in `BTLERadarDevice` call `RadarTrafficSimulator.onPhysicalHardwareConnected()`. In `TrackerService.handleActionStop()`, `RadarLightController` and `RadarSnoozeController` are reset, but `RadarTrafficSimulator.stop()` is omitted.
3. **Unstructured Scope & Non-Reactive UI in `RadarTrafficSimulator.kt` (`lines 61, 394`) & `RadarSettingsSection.kt` (`lines 817–878`)**:
   `isSimulating` queries `simulationJob?.isActive == true`, which does not trigger Compose recomposition upon external or asynchronous stop. `start()` creates an ad-hoc `CoroutineScope(Dispatchers.Default)` on each invocation. `RadarSettingsSection.kt` uses a 5-chip `FlowRow` with raw string concatenation for live target status.

---

## 3. Independent Peer Review & Verification (4-Augen-Prinzip)
- Confirmed introducing `SimulatedRadarDevice(context, sensorManager)` extending `MyDevice` registering the 6 radar `SensorType`s allows `RadarTrafficSimulator` to publish 1 Hz aggregated snapshots into `MySensor.newValue()` whenever `BANALService` is running, matching physical hardware behavior.
- Verified calling `RadarTrafficSimulator.onPhysicalHardwareConnected()` in `BTLERadarDevice` upon physical measurement notifications cleanly preempts indoor simulation before physical targets are processed.
- Verified exposing `isSimulatingFlow: StateFlow<Boolean>` drives reactive state changes across `RadarSettingsSection.kt` and `AdvancedTuningAccordion.kt`.
- Verified replacing the 5-chip `FlowRow` with `ExposedDropdownMenuBox` adheres to Material 3 and Design Guidelines §1.3 (> 4 options).

---

## 4. Chesterton's Fence Archaeology & Invariants
- **Original Formulation (`ATT-5087` / commit `69fec8e7`)**: `RadarTrafficSimulator` was fast-tracked as a standalone preview tool for UI/audio testing prior to hardware arrival. The lack of `BANALService` wiring and hardware preemption hooks were omissions from rapid initial prototyping.
- **System Invariants**:
  1. Absolute physical hardware precedence: Physical radar telemetry immediately halts simulation and claims sensor priority.
  2. Zero phantom DB writes: Scalar sensors only record when workout tracking is active in `TrackerService`.
  3. Clean lifecycle teardown: Stopping simulation or ending a workout immediately de-registers simulated sensors.
  4. Design system compliance: Material 3 `ExposedDropdownMenuBox` for scenario selection; 100% 9-language translation parity.

---

## 5. Scope Bounding & Complexity Evaluation
- **In-Scope**:
  - Implement `SimulatedRadarDevice` in `banalservice` and wire `RadarTrafficSimulator` to publish 1 Hz latched metrics to `MySensor` channels via `BANALService.getSensorManager()`.
  - Wire `RadarTrafficSimulator.onPhysicalHardwareConnected()` in `BTLERadarDevice.java` and invoke `RadarTrafficSimulator.stop()` in `TrackerService.handleActionStop()` / `onDestroy()`.
  - Expose `isSimulatingFlow: StateFlow<Boolean>`, switch to monotonic clock (`SystemClock.elapsedRealtime()`), and use managed `SupervisorJob` scope in `RadarTrafficSimulator.kt`.
  - Replace 5-chip `FlowRow` with `ExposedDropdownMenuBox` and localize simulator status in `RadarSettingsSection.kt` and `strings_radar.xml` (9 locales).
  - Expand `RadarTrafficSimulatorTest.kt` and `BTLERadarDeviceTest.kt`.
- **Out-of-Scope**: Modifying radar BLE GATT handshake (`ATT-6706`) or audio chime frequencies (`ATT-6705`).
- **Complexity Estimate**: 2 SP (Standard bugfix and simulator-to-service bridge wiring).
