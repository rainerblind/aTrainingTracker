# Stage 3: Implementation Plan - ATT-8895: Carriage-Classified Smartphone IMU Cycling & Running Cadence

**Ticket**: `ATT-8895` (`3 SP`, `Feature`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-18_02_Spät` (`ID 460`) | **Requirement Mapping**: `REQ-FIL-029` (`REQ-FIL-029.1`–`.5`) | **Test Mapping**: `TST-FIL-029`  
**Branch**: `feature/ATT-8895` | **Sub-task**: `[Impl-Plan]` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Problem Description & SWE.2 Architectural Boundaries
* No `Protocol.SMARTPHONE` cadence source exists today (`GCData[5] = '-'` without external footpods/crank sensors or Polar IMU), while `ATT-166` forbids handlebar cycling cadence (`5–50 Hz` road chatter masks `1.2–1.6 Hz` pedaling).
* **SWE.2 Threading & Layering**: Pure-JVM `ImuCadenceDsp` & `SmartphoneImuCadenceEstimator` (`banalservice.odometry`) run synchronously inside `SpeedAndLocationDevice_ATTFused` sensor callbacks (`IF-SWE-04`) under an estimator lock, never touching SQLite, UI, or `TrackerService` (`IF-SWE-06`). Dynamic `MySensor` registration routes through `MySensorManager.CADENCE_PRIORITY` / `STRIDES_PRIORITY` (`ProxySensor`) and persists automatically at `1 Hz` via existing `TrackerService.sampleAndWriteToDb()`.
* **UI Consistency (`docs/design_guidelines.md`)**: `N/A` — zero Compose screens/dialogs/widgets modified and zero new UI strings (`N/A - no UI strings`).
* **SWE.3 Living Detailed Design (`REQ-PRO-114`)**: `TrackerService.java`, `BANALService.java`, `DeviceManager.java`, `MyBTLEDevice.java`, and `WorkoutRepository.kt` state machines remain untouched; update `banal_service.md` §4.1 (`IF-SWE-04`) for `STRIDES_PRIORITY` and verify `DetailedDesignParityContractTest`.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-FIL-029` (`.1` shared `ImuCadenceDsp` + Polar equivalence; `.2` carriage-mode cycling/running cadence & strict handlebar suppression; `.3` ~50-Hz `ACC` lifecycle + dynamic `CADENCE`/`STRIDES`; `.4` `CADENCE_PRIORITY` tail + explicit `STRIDES_PRIORITY` + `DeviceType` reachability + `0` monolith LoC growth; `.5` `SWE.2`/`SYS.2`/`SYS.3` parity + `0` `src/main` test reads).
* **Tests**: `TST-FIL-029` (`.1`–`.5`) linked to `SYSREQ-TRK-001`, `SYSREQ-TRK-003`, `SYSREQ-TRK-010`.

---

## 3. System Invariants & Preserved Behavior
1. **Handlebar Suppression & `GCData[5]` Invariance**: `BIKE_HANDLEBAR`, `UNKNOWN`, and `WALKING_TRANSITION` while `BSportType.BIKE` strictly return `null` and never register `SensorType.CADENCE`/`STRIDES` or flip `GCData[5]` to `'C'`.
2. **External Sensor Precedence & Polar Equivalence**: `RUN_SPEED > BIKE_CADENCE > BIKE_SPEED_AND_CADENCE > BIKE_POWER > HRM > SPEED_AND_LOCATION_ATT_FUSED` (`CADENCE_PRIORITY`) and `RUN_SPEED > HRM > SPEED_AND_LOCATION_ATT_FUSED` (`STRIDES_PRIORITY`); Polar H10/Verity cadence behavior stays bit-for-bit equivalent.
3. **Monolith Ceilings & Hygiene**: `0` LoC added to `TrackerService.java` (`<= 3,559`), `DeviceManager.java` (`<= 1,708`), `WorkoutSamplesDatabaseManager.java` (`<= 1,533`), `WorkoutRepository.kt` (`<= 4,973` / `<= 5,117` cap); `0` DB migrations; `N/A - no UI strings`; `0` new `src/main` test reads (`REQ-PRO-049.1`); `0` edits to pre-existing dirty `tools/` files.

---

## 4. Proposed Architectural Changes
* **Shared Pure-Kotlin DSP (`banalservice.odometry.ImuCadenceDsp.kt` + `PolarLiveRunningCadenceEstimator.kt`)**: Extract `UniformGridResampler`, `BiquadBandPass`, `Vector3BandPass`, `BiquadSection`, `DoubleRingBuffer`, `Vector3RingBuffer`, and `findFundamentalLag()` (`5 s` ACF + parabolic interpolation) with internal package-accessible visibility, keeping Polar (`mG`/`deg/s`) unchanged.
* **Smartphone Cadence Estimator (`banalservice.odometry.SmartphoneImuCadenceEstimator.kt`)**: Pure-JVM SI estimator (`m/s^2`, `rad/s`, `TYPE_STEP_DETECTOR`, `PhoneCarriageMode`, `isGlanceActive`, `SportContextHint`) implementing: `BIKE_FRONT_POCKET` (`0.65–2.2 Hz` `GYRO+ACC`, `1.5 s` coasting gate `< 0.25 rad/s` -> `0.0 RPM` within `2.0 s`, clean `null` on `BIKE_HANDLEBAR` transition), `BIKE_JERSEY_BACK` (`0.65–2.2 Hz` `GYRO`, `f`/`2f` harmonic disambiguation, `R(tau)/R(0) >= 0.45`), `BIKE_HANDLEBAR`/`UNKNOWN`/`BIKE` walk (`null`), `RUN_WAIST_HIP`/`RUN` walk (`1.2–4.0 Hz` impact + `TYPE_STEP_DETECTOR`), `RUN_BICEP_ARM`/`RUN_HANDHELD` (`0.6–2.0 Hz` `GYRO+ACC` + `TYPE_STEP_DETECTOR`, `isGlanceActive` blanking, gyro-absent `ACC + TYPE_STEP_DETECTOR` fallback).
* **Driver & Priority Wiring (`SpeedAndLocationDevice_ATTFused.kt`, `SpeedAndLocationDevice.java`, `MySensorManager.java`, `DeviceType.java`)**: Subscribe `TYPE_ACCELEROMETER` at `SENSOR_DELAY_GAME` (`~50 Hz`) in `registerImuSensors()` independent of `LocationAvailable()`/`isEffectiveGyroActive()` (guarding `unregisterAccelerometer()` on `LocationUnavailable()`); dynamically register/unregister `CADENCE` & `BSportType.RUN`-gated `STRIDES` (`accumulate(0)`) with `SPORT_TYPE_CHANGED_BY_USER_INTENT` & clean reset; update `CADENCE_PRIORITY`, `STRIDES_PRIORITY`, `DeviceType.getDeviceTypeList()`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
| Step | Action & Target Files | Mapping | Verification (`./gradlew :app:testDebugUnitTest --tests ...`) |
| :---: | :--- | :--- | :--- |
| **1** | Extract shared pure-Kotlin DSP core (`ImuCadenceDsp.kt`) from `PolarLiveRunningCadenceEstimator.kt` preserving Polar `mG`/`deg/s` behavior intact. | `REQ-FIL-029.1` / `TST-FIL-029.1` | `*PolarLiveRunningCadenceEstimatorTest`, `*PolarOnlineStreamingBridgeTest` |
| **2** | Create pure-Kotlin `SmartphoneImuCadenceEstimator.kt` (SI `m/s^2`/`rad/s` + `TYPE_STEP_DETECTOR`) covering `BIKE_FRONT_POCKET`, `BIKE_JERSEY_BACK`, strict `BIKE_HANDLEBAR`/`UNKNOWN`/`BIKE` walk suppression, and `RUN_*` modes (`isGlanceActive` blanking + gyro-absent fallback); add `SmartphoneImuCadenceEstimatorTest.kt`. | `REQ-FIL-029.2` / `TST-FIL-029.2`, `.3` | `*SmartphoneImuCadenceEstimatorTest` |
| **3** | Wire ~50-Hz `TYPE_ACCELEROMETER` in `SpeedAndLocationDevice_ATTFused.kt` (w/ `SpeedAndLocationDevice.java` `unregisterAccelerometer()` guard), dynamic `CADENCE`/`RUN`-gated `STRIDES` (`GCData[5]` `'C'` & `SPORT_TYPE_CHANGED_BY_USER_INTENT` safe), `DeviceType.java` (`Rule 81`), `MySensorManager.java` (`CADENCE_PRIORITY` tail + `STRIDES_PRIORITY`), and update `MySensorManagerCadenceArbitrationTest.kt:168-171` + `SpeedAndLocationDevice_ATTFusedTest.kt` / `MySensorManagerPriorityTest.kt` / `DeviceTypeSensorMappingTest.kt`. | `REQ-FIL-029.3`, `.4` / `TST-FIL-029.2`–`.4` | `*SpeedAndLocationDevice_ATTFusedTest`, `*MySensorManagerCadenceArbitrationTest`, `*MySensorManagerPriorityTest`, `*DeviceTypeSensorMappingTest` |
| **4** | Verify `0` monolith LoC growth (`WorkoutRepository <= 4,973` / `<= 5,117` cap, `TrackerService <= 3,559`, `DeviceManager <= 1,708`, `WorkoutSamplesDatabaseManager <= 1,533`); sync `docs/architecture.md` (`Rule 8.8`), `docs/architecture/detailed_design/banal_service.md`, `docs/system/` (`SYSREQ-TRK-001`, `003`, `010`, `system_architecture.md`, `system_software_traceability.md`), and living docs; run all targeted unit/contract suites + `verify_requirement_governance.py`. | `REQ-FIL-029.4`, `.5` / `TST-FIL-029.5` | `*ArchitectureConformanceContractTest`, `*DetailedDesignParityContractTest`, `*SystemLiveTrackingRequirementsContractTest`, `*SystemArchitectureDesignContractTest`, `*SourceTextReadingAuditContractTest`, `verify_requirement_governance.py` |

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted Tier-1 `./gradlew :app:testDebugUnitTest --tests ...` across all 12 listed test suites (`--rerun-tasks` before Stage 5) + `python3 tools/verify_requirement_governance.py --repo-root . --check-specs` (noting pre-existing `ee4fac1e`).
* **Rollback**: Feature branch `feature/ATT-8895` isolated off `sprint/2026-11-18_02_Spät` (`fee3e805`); zero DB schema changes; pure working-tree revert if needed.
