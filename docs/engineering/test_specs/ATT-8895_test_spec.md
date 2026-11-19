# Stage 2: Requirement & Test Specification - ATT-8895: Carriage-Classified Smartphone IMU Cycling & Running Cadence

**Ticket**: `ATT-8895` (`3 SP`, `Feature`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_01_Früh` | **Requirement Mapping**: `REQ-FIL-029` (`[Verified]`; `SYSREQ-TRK-001`, `SYSREQ-TRK-003`, `SYSREQ-TRK-010`) | **Test Spec ID**: `TST-FIL-029` (`[Verified]`)  
**Branch**: `feature/ATT-8895` | **Sub-task**: `[Req & Test Spec]` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-FIL-029`)

### 1.1 Problem Statement & Rationale
* When cycling with the smartphone in a trouser/jersey pocket or running without an external footpod/Polar IMU, `SensorType.CADENCE` and `SensorType.STRIDES` remain unrecorded (`GCData[5] = '-'`).

### 1.2 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit (`./gradlew testDebugUnitTest`)

### 1.3 Functional & Architectural Requirements
1. **Shared Pure-Kotlin IMU Cadence DSP Core (`REQ-FIL-029.1`)**: Extract `UniformGridResampler` (`50 Hz` / `20 ms`), `BiquadBandPass`, `Vector3BandPass`, `BiquadSection`, `DoubleRingBuffer`, `Vector3RingBuffer`, and `findFundamentalLag()` (`5 s` sliding normalized ACF + parabolic interpolation) from `PolarLiveRunningCadenceEstimator.kt` into `ImuCadenceDsp.kt`, preserving Polar H10 (`mG`) and Verity (`mG` + `deg/s`) outputs bit-for-bit while letting `SmartphoneImuCadenceEstimator` process Android SI units (`m/s^2`, `rad/s`) and `Sensor.TYPE_STEP_DETECTOR`.
2. **Carriage-Mode Cycling & Running Cadence Dispatch (`REQ-FIL-029.2`)**:
   - `BIKE_FRONT_POCKET`: `0.65–2.2 Hz` (`39–132 RPM`) 3D `GYRO + ACC` thigh-flexion ACF + `1.5 s` coasting energy gate (`0.0 RPM` within `2.0 s` when angular RMS `< 0.25 rad/s`), transitioning cleanly to suppressed `null` if classifier later settles into `BIKE_HANDLEBAR` (`4.0 s` quiet hysteresis).
   - `BIKE_JERSEY_BACK`: `0.65–2.2 Hz` 3D gyro ACF + `f`/`2f` harmonic disambiguation + strict prominence gate ($R(\tau_{\text{peak}})/R(0) \ge 0.45$).
   - `BIKE_HANDLEBAR` (+ `UNKNOWN`, + `WALKING_TRANSITION` under `BSportType.BIKE`): strictly disabled/suppressed (`null`, zero false RPM, zero `CADENCE`/`STRIDES` registration or `GCData[5]` `'C'`).
   - `RUN_WAIST_HIP` (+ `WALKING_TRANSITION` under `BSportType.RUN`): `1.2–4.0 Hz` impact band-pass + `TYPE_STEP_DETECTOR` fusion (`strides/min = steps/min / 2`, `+1` `STRIDES` per `2` steps), with `ACC + TYPE_STEP_DETECTOR` fallback when gyro is absent/disabled.
   - `RUN_BICEP_ARM` & `RUN_HANDHELD`: `0.6–2.0 Hz` 3D `GYRO + ACC` pendulum arm-swing ACF + `TYPE_STEP_DETECTOR` fusion with `isGlanceActive()` screen-glance blanking in `RUN_HANDHELD` and gyro-absent `ACC + TYPE_STEP_DETECTOR` fallback.
3. **Dual-Rate Smartphone IMU Lifecycle & Dynamic Registration (`REQ-FIL-029.3`)**: `SpeedAndLocationDevice_ATTFused` SHALL subscribe `TYPE_ACCELEROMETER` at `SENSOR_DELAY_GAME` (`~50 Hz`) in `registerImuSensors()` independent of `LocationAvailable()` / `isEffectiveGyroActive()` (guarding `SpeedAndLocationDevice.unregisterAccelerometer()` on `LocationUnavailable()`), dynamically register `CADENCE` only when non-handlebar cadence is active, register `STRIDES` (`MyIntegerAccumulatorSensor`, `accumulate(0)`) strictly under `BSportType.RUN` (`SPORT_TYPE_CHANGED_BY_USER_INTENT`), and reset cleanly on `onAccumulatorsReset()` / `shutDown()`.
4. **`MySensorManager` Priority, `DeviceType` Reachability & Zero Monolith Growth (`REQ-FIL-029.4`)**: Append `SPEED_AND_LOCATION_ATT_FUSED` behind `HRM` at tail of `MySensorManager.CADENCE_PRIORITY`; define explicit `STRIDES_PRIORITY` (`RUN_SPEED > HRM > SPEED_AND_LOCATION_ATT_FUSED`) via `setPriorityList`/`getPriorityArray` instead of ordinal fallback; expose `SPEED_AND_LOCATION_ATT_FUSED` in `DeviceType.getDeviceTypeList(CADENCE|STRIDES)`. Add `0` LoC to protected monoliths; zero DB schema changes; `N/A - no UI strings`.
5. **`SWE.2`/`SYS.2`/`SYS.3` Parity & Test Hygiene (`REQ-FIL-029.5`)**: Sync `docs/architecture.md` (`Rule 8.8`) and `docs/system/` (`SYSREQ-TRK-001`, `003`, `010`, `system_architecture.md`, `system_software_traceability.md`) in Stages 4–5 alongside newly created `.kt` files, with `0` new `src/main` reads (`REQ-PRO-049.1`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-FIL-029` extending `REQ-SEN-028`, `REQ-FIL-028`, `REQ-SET-112.4`, and `REQ-SET-114` across `PolarLiveRunningCadenceEstimator.kt`, `SpeedAndLocationDevice_ATTFused.kt`, `SpeedAndLocationDevice.java`, `MySensorManager.java`, and `DeviceType.java`.
* **Historical Origin & Commit Trace**: `f4bef44c` (`ATT-8016` — `CADENCE_PRIORITY` tail lock at `HRM` + `BSportType.RUN` gating), `438233eb` (`ATT-8017` — `PolarLiveRunningCadenceEstimator`), `f82a2960` (`ATT-7751` — `SpeedAndLocationDevice_ATTFused`), `fee3e805` (`ATT-8890` — `PhoneCarriageMode`/`PhoneCarriageClassifier`).
* **Root Reason for Existing Formulation**: `ATT-166` explicitly forbade handlebar cycling cadence (`5–50 Hz` chatter masks `1.2–1.6 Hz` pedaling); `ATT-8016` locked `HRM` at the end of `CADENCE_PRIORITY` so Polar IMU never preempted ANT+/BLE footpods or crank sensors.
* **Preservation of Core Invariants**: Preserves strict `BIKE_HANDLEBAR` suppression (zero RPM, zero `GCData[5]` `'C'`), external sensor precedence (`RUN_SPEED`/`BIKE_*`/`HRM` > `SPEED_AND_LOCATION_ATT_FUSED`), Polar H10/Verity equivalence, and monolith LoC caps (`TrackerService <= 3,556`, `DeviceManager <= 1,707`, `WorkoutSamplesDatabaseManager <= 1,533`, `WorkoutRepository <= 4,973` / `<= 5,117` cap on `e62a6ba4`).

---

## 3. Acceptance Criteria (Given-When-Then) & System Invariants
* **AC1 (Polar Equivalence)**: *Given* `PolarLiveRunningCadenceEstimator` backed by `ImuCadenceDsp`, *When* running H10/Verity tests, *Then* outputs remain equivalent within existing tolerances.
* **AC2 (`BIKE_FRONT_POCKET` & Coasting)**: *Given* `BIKE_FRONT_POCKET` `1.25 Hz` (`75 RPM`) followed by `5 s` coasting (`< 0.15 rad/s`), *When* evaluated, *Then* it emits `75 ± 2 RPM`, drops to `0.0 RPM` within `2.0 s`, and suppresses (`null`) if quiet coasting transitions to `BIKE_HANDLEBAR`.
* **AC3 (`BIKE_JERSEY_BACK` & `BIKE_HANDLEBAR` Suppression)**: *Given* `BIKE_JERSEY_BACK` (`f`/`2f` vs. noise) and `BIKE_HANDLEBAR` (`5–50 Hz` chatter) / `UNKNOWN` / `BIKE` walking dismount, *When* evaluated, *Then* jersey back emits only when prominence `>= 0.45` and handlebar/unknown/bike-walk emit `null` with zero `CADENCE`/`STRIDES` registration or `GCData[5]` `'C'`.
* **AC4 (`RUN_*` Cadence, Strides, Glance Blanking & Gyro-Absent Fallback)**: *Given* `RUN_WAIST_HIP`, `RUN_BICEP_ARM`, and `RUN_HANDHELD` at `170 steps/min` (`85 strides/min`) plus wrist glance and gyro-absent `ACC + TYPE_STEP_DETECTOR` indoor run, *When* evaluated, *Then* `CADENCE` emits `85 ± 2 strides/min` and `STRIDES` increments monotonically from `1` without glance phantom spikes.
* **AC5 (External Sensor Precedence)**: *Given* active ANT+/BLE `RUN_SPEED`/`BIKE_*`/`HRM` alongside `SPEED_AND_LOCATION_ATT_FUSED`, *When* `MySensorManager` arbitrates `CADENCE` & `STRIDES`, *Then* external sensors strictly preempt `SPEED_AND_LOCATION_ATT_FUSED`.
* **System Invariants**: `BIKE_HANDLEBAR` strictly suppressed (zero RPM, zero `GCData[5]` `'C'`); `RUN_SPEED`/`BIKE_*`/`HRM` > `SPEED_AND_LOCATION_ATT_FUSED`; Polar H10/Verity equivalence; `0` LoC growth (`TrackerService <= 3,556`, `DeviceManager <= 1,707`, `WorkoutSamplesDatabaseManager <= 1,533`, `WorkoutRepository <= 4,973` / `<= 5,117` cap on `e62a6ba4`); `N/A - no UI strings`; `0` new `src/main` reads (`REQ-PRO-049.1`).

---

## 4. Test Specification (`TST-FIL-029`)
* **`TST-FIL-029.1` (`PolarLiveRunningCadenceEstimatorTest`, `PolarOnlineStreamingBridgeTest`)**: Verify Polar H10 (`100/200 Hz`) and Verity (`52 Hz`) equivalence post-`ImuCadenceDsp` extraction.
* **`TST-FIL-029.2` (`SmartphoneImuCadenceEstimatorTest`, `SpeedAndLocationDevice_ATTFusedTest`)**: Verify `BIKE_FRONT_POCKET` (`75 RPM`, `1.5 s` coasting -> `0.0 RPM` within `2.0 s`, quiet `BIKE_HANDLEBAR` transition), `BIKE_JERSEY_BACK` (`f`/`2f`, `>= 0.45` gate), and strict `BIKE_HANDLEBAR`/`UNKNOWN`/`BIKE` walk suppression (no `CADENCE`/`STRIDES`/`GCData[5]` `'C'`).
* **`TST-FIL-029.3` (`SmartphoneImuCadenceEstimatorTest`, `SpeedAndLocationDevice_ATTFusedTest`)**: Verify `RUN_WAIST_HIP`, `RUN_BICEP_ARM`, `RUN_HANDHELD` (`85 ± 2 strides/min`, monotonic `STRIDES` from `1`, `isGlanceActive` blanking), gyro-absent `ACC + TYPE_STEP_DETECTOR` indoor fallback, `SENSOR_DELAY_GAME` (`50 Hz`) `ACC` lifecycle, and `onAccumulatorsReset`/`shutDown`.
* **`TST-FIL-029.4` (`MySensorManagerCadenceArbitrationTest`, `MySensorManagerPriorityTest`, `DeviceTypeSensorMappingTest`)**: Update `MySensorManagerCadenceArbitrationTest.kt:168-171` (`CADENCE_PRIORITY.last() == SPEED_AND_LOCATION_ATT_FUSED` behind `HRM`), verify explicit `STRIDES_PRIORITY` (`RUN_SPEED > HRM > SPEED_AND_LOCATION_ATT_FUSED`), and verify `DeviceType.getDeviceTypeList(CADENCE|STRIDES)`.
* **`TST-FIL-029.5` (Contracts & Governance)**: Verify monolith LoC ceilings (`WorkoutRepository <= 4,973` / `<= 5,117` cap, `0` LoC added by `ATT-8895`), `0` new `src/main` test reads (`SourceTextReadingAuditContractTest`), Stage 4–5 `SWE.2`/`SYS.2`/`SYS.3` contract suites, and `verify_requirement_governance.py` (`exit 0`).

---

## 5. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-FIL-029.1` (`SYSREQ-TRK-001`) | Shared `ImuCadenceDsp` extraction & Polar equivalence | `TST-FIL-029.1` | Unit (`PolarLiveRunningCadenceEstimatorTest`) |
| `REQ-FIL-029.2` (`SYSREQ-TRK-010`) | Pocket/jersey cycling, strict handlebar suppression, running modes & glance blanking | `TST-FIL-029.2`, `TST-FIL-029.3` | Unit (`SmartphoneImuCadenceEstimatorTest`) |
| `REQ-FIL-029.3` (`SYSREQ-TRK-003`/`010`) | 50-Hz `ACC` lifecycle, indoor fallback, dynamic `CADENCE`/`STRIDES` registration | `TST-FIL-029.2`, `TST-FIL-029.3` | Unit (`SpeedAndLocationDevice_ATTFusedTest`) |
| `REQ-FIL-029.4` (`SYSREQ-TRK-001`) | `CADENCE_PRIORITY`, `STRIDES_PRIORITY`, `DeviceType` mapping & monolith caps | `TST-FIL-029.4`, `TST-FIL-029.5` | Unit (`MySensorManagerCadenceArbitrationTest`) |
| `REQ-FIL-029.5` (`REQ-PRO-131.4`, `Rule 8.8`) | Stage 4–5 `SWE.2`/`SYS.2`/`SYS.3` parity & `0` `src/main` test reads | `TST-FIL-029.5` | Contract & Governance Check |
