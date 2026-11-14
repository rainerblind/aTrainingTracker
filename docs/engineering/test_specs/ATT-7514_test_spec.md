# Stage 2 Test Specification: ATT-7514

**Ticket:** ATT-7514 (`[Bug] SmartBrakeLightDetector: Auto-bypass leak across sequential workouts and stuck brake light on pause in RADAR_ONLY_LIGHT_OFF`)  
**Sub-task:** [ATT-7894](https://rainerblind.atlassian.net/browse/ATT-7894) (`[Req & Test Spec]`)  
**Parent Epic:** ATT-1286 | **Sprint:** 2026-46.6 | **Target Release:** V5.0.0 | **Story Points:** 1 SP  
**Role:** `agent1` (Subagent Flash) / Review: `agent2` (Subagent Pro) | **Status:** In Progress (Stage 2)  

---

## 1. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Target Component | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-035.5` | `TST-CON-035.5` | Unit | `SmartBrakeLightDetector.kt`, `RadarLightController.kt` | Proposed |
| `REQ-CON-035.7` | `TST-CON-035.7` | Unit | `RadarLightController.kt`, `SmartBrakeLightDetector.kt` | Proposed |

---

## 2. Requirement Specification (`REQ-CON-035`)

### REQ-CON-035: Smartphone IMU/GPS Deceleration Brake-Light Trigger for BLE & ANT+ (0x23) Radar Tail Lights [Verified]
- **Tests**: `TST-CON-035`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SmartBrakeLightDetector.kt`, `RadarLightController.kt`, `RadarLightMode.kt`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Rationale**: Cycling radar tail lights lacking internal brake sensors cannot warn approaching traffic or group ride partners during emergency or sudden braking. Smartphone devices mounted on handlebars or carried during rides feature high-frequency IMU sensors (`Sensor.TYPE_LINEAR_ACCELERATION`, `Sensor.TYPE_GRAVITY`) and GPS/wheel-speed telemetry. The system provides an automated software brake-light trigger that fuses linear deceleration and attitude compensation, rejects road shocks/potholes, immediately overrides tail light rate-limiting with high-visibility illumination, preserves a 2.0s hold window, and automatically bypasses when radar hardware with onboard brake sensing (Garmin Varia RearVue 820) is detected (ATT-5121, Parent Epic ATT-1286, extending `REQ-CON-031`, `REQ-CON-033`, and `REQ-CON-034`):
- **Refined Clauses**:
  1. *Hardware Brake Sensor Auto-Bypass Retention (`REQ-CON-035.5`)*:
     - When a connected radar peripheral is identified as having an onboard hardware brake sensor (Garmin Varia RearVue 820), software deceleration triggering SHALL be automatically bypassed (`isAutoBypassed = true`).
     - Auto-bypass status SHALL persist across sequential workout sessions while the hardware device remains connected; stopping a workout (`onWorkoutStopped()`) SHALL reset transient motion state without resetting the hardware auto-bypass flag.
  2. *Active Workout Lifecycle Management & Symmetric Pause Restoration (`REQ-CON-035.7`)*:
     - `SmartBrakeLightDetector` SHALL register Android `SensorEventListener` exclusively during active cycling tracking sessions when radar light control is connected and enabled.
     - Upon workout pause, stop, or destruction, sensor listeners SHALL be immediately unregistered and pending post-brake holds cancelled to prevent battery drain.
     - Upon workout pause while a brake event is active (`isBrakeActive == true`), `RadarLightController` SHALL unconditionally restore `preBrakeMode` (including restoring `RadarLightMode.OFF` when `preBrakeMode == OFF`), rather than skipping restoration and stranding the brake light ON.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-CON-035.5` and `REQ-CON-035.7` in `docs/requirements/req_tracking_cockpit.md`.
2. *Historical Origin & Commit Trace*: Commit `1a6606d501417727dff28b9f38ca6cb581c9d040` (ATT-5121).
3. *Root Reason for Existing Formulation*: In ATT-5121, `SmartBrakeLightDetector.reset()` was added as a test isolation helper; calling it in `onWorkoutStopped()` cleared transient motion state but accidentally wiped `isAutoBypassed`. In `onWorkoutPaused()`, `if (preBrakeMode != RadarLightMode.OFF)` was written under the false assumption that turning off an already-off light was redundant, overlooking that active braking had toggled the light on.
4. *Preservation of Core Invariants*: StVZO steady mode clamping invariant preserved; sensor shutdown on pause/stop preserved; test isolation across unit test suites preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a connected Garmin Varia RearVue 820 with hardware brake sensing (`isAutoBypassed == true`),  
• *When* a workout is stopped and a subsequent cycling workout is started without disconnecting the peripheral,  
• *Then* `SmartBrakeLightDetector.isAutoBypassed` remains `true` and smartphone IMU sensor listeners are NOT registered.  
• *Given* an active cycling workout with tail light in `OFF` mode (`RadarLightMode.OFF`),  
• *When* the cyclist decelerates hard triggering a brake light event and the workout pauses,  
• *Then* `RadarLightController` immediately reverts the tail light to `preBrakeMode` (`OFF`), clearing active brake state and cancelling pending holds.  

**Invariants**: StVZO flashing prohibition compliance; road shock/pothole immunity; 2.0s hold window with exact pre-brake mode restoration; zero SQLite schema modifications; 100% 9-language translation parity.

---

## 3. Test Specification (`TST-CON-035`)

### Test Case 1: Sequential Workouts Hardware Auto-Bypass Persistence (`TST-CON-035.5`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/radar/RadarLightControllerTest.kt`
* **Preconditions**: Mock `BTLERadarDevice` registered with `hasHardwareBrakeSensor = true`. `RadarLightController.isAutoBypassed` and `SmartBrakeLightDetector.isAutoBypassed` are `true`.
* **Action**:
  1. Invoke `RadarLightController.onWorkoutStarted(mockContext, isCycling = true)`.
  2. Invoke `RadarLightController.onWorkoutStopped(mockContext)`.
  3. Assert `SmartBrakeLightDetector.isAutoBypassed` remains `true` and `RadarLightController.isAutoBypassed.value` remains `true`.
  4. Invoke `RadarLightController.onWorkoutStarted(mockContext, isCycling = true)` (second sequential workout).
* **Expected Result**: `SmartBrakeLightDetector.isListening` remains `false`. 50 Hz smartphone IMU sensors are NOT registered.

### Test Case 2: SmartBrakeLightDetector Transient Motion State Reset (`TST-CON-035.5`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/radar/SmartBrakeLightDetectorTest.kt`
* **Preconditions**: `SmartBrakeLightDetector.isAutoBypassed = true`, `isEnabled = true`. Simulate non-zero speed (`currentSpeedMps = 10f`).
* **Action**: Invoke `SmartBrakeLightDetector.resetMotionState()`.
* **Expected Result**: `currentSpeedMps == 0f`, `isBraking == false`, while `isAutoBypassed == true` and `isEnabled == true` are preserved. Calling `reset()` clears `isAutoBypassed` to `false` and `isEnabled` to `true`.

### Test Case 3: Stuck Brake Light on Pause in RADAR_ONLY_LIGHT_OFF Restored to OFF (`TST-CON-035.7`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/radar/RadarLightControllerTest.kt`
* **Preconditions**: Mock `BTLERadarDevice` registered with `lightSupported = true`. Workout started. Current mode set to `RadarLightMode.OFF`.
* **Action**:
  1. Trigger brake: `RadarLightController.triggerBrakeLight(active = true, brakeMode = DAY_FLASH, nowMs = 1000L)`.
  2. Verify `currentMode.value == DAY_FLASH` (or `SOLID`) and `isBrakeActive == true`.
  3. Invoke `RadarLightController.onWorkoutPaused()`.
* **Expected Result**: `isBrakeActive` is `false`, `SmartBrakeLightDetector.isListening` is `false`, `RadarLightController.currentMode.value == RadarLightMode.OFF`, and `mockDevice.setLightMode(RadarLightMode.OFF)` was dispatched.

### Test Case 4: Fast Targeted Regression (`TST-CON-035.8`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*"`
* **Expected Result**: 100% pass rate across all radar unit tests.
