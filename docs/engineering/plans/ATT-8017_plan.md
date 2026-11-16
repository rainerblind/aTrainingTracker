# Implementation Plan: ATT-8017 Polar Live Running Cadence & Stride Counter

**Jira**: ATT-8017 | **Author**: agent1 (`subagent_pro`) | **Risk**: `HIGH-RISK` (DSP/autocorrelation & live IMU stream, Rule 7.1 -> Flash reviews Gate 3 ONLY; Pro executes Stage 4) | **UI Changes**: None

## 1. Problem Description & Background
`PolarOnlineStreamingBridge` currently emits raw step peaks (steps/min) without bandpass filtering, autocorrelation harmonic disambiguation, Verity Sense arm-swing gyro fusion, `1 Hz` zero-order hold, standstill zeroing, or `MyAccumulatorSensor` baseline seeding. `ANTRunSpeedDevice`, `BTLERunSpeedDevice`, `TCXFileWriter`, and `FitFileWriter` standardize `SensorType.CADENCE` in **strides/min** (`60–100 strides/min` = `120–200 steps/min`) and monotonic `SensorType.STRIDES`.

## 2. Traceability & Requirements Mapping
- **REQ-SET-114.1 -> TST-SET-116.1**: Canonical strides/min unit parity across `SensorType.CADENCE` (`60–100 strides/min`) and `SensorType.STRIDES` (2 steps = 1 stride), seeded via `mStridesSensor.accumulate(0)` before stride `1`.
- **REQ-SET-114.2 -> TST-SET-116.2**: H10 chest `ACC` `1.2–4.0 Hz` band-pass + adaptive step peaks + `5s` autocorrelation (`0.6–2.0 Hz` stride fundamental vs `2f` step harmonic disambiguation).
- **REQ-SET-114.3 -> TST-SET-116.3**: Verity Sense armband `0.6–2.0 Hz` rotation-invariant `GYRO+ACC` arm-swing fusion -> strides/min.
- **REQ-SET-114.4 -> TST-SET-116.4 / TST-SET-116.5**: Steady `1 Hz` `SensorType.CADENCE` updates during motion and deterministic `0.0` zeroing after `>= 3s` standstill without orphan stride increments.

## 3. System Invariants & Preserved Behavior
- Preserve `MySensorManager.CADENCE_PRIORITY` (`RUN_SPEED` > `BIKE_*` > `HRM`) and `BSportType.RUN` (`RUN` + `WALK`) gating (`REQ-SET-112`).
- Preserve persistent single-GATT PMD session & `PolarOfflineGattCoordinator` `ACC` arbitration (`ATT-8016`).
- Zero UI changes (`EditDeviceDialog` untouched); zero `ANT-Android-SDKs` edits; `FitFileWriter`/`TCXFileWriter` unchanged (strides/min contract already matches).
- Keep DSP engine pure Kotlin in `PolarLiveRunningCadenceEstimator.kt` (no Android framework dependencies) for deterministic JVM Tier 1 unit testing.

## 4. Proposed Architectural Changes
- **New `PolarLiveRunningCadenceEstimator.kt`**: Pure-Kotlin DSP estimator parameterized by sensor model (`H10` vs `Verity Sense`) encapsulating biquad band-pass filters, `ACC`+`GYRO` norm extraction, `5s` sliding autocorrelation + adaptive peak-to-stride divider, `1 Hz` emission scheduler, and `>= 3s` motion-energy standstill detector.
- **Update `PolarOnlineStreamingBridge.kt`**: Replace provisional `detectStepPeak` with `PolarLiveRunningCadenceEstimator`; seed `mStridesSensor.accumulate(0)` in `syncCadenceSensorRegistration()` when activating; reset estimator on unregister/shutdown/sport change.
- **Update `docs/architecture.md`** (`Rule 8.8`): Register `PolarLiveRunningCadenceEstimator` in SWE.2 component inventory & Section 3.4 Polar live IMU pipeline.

## 5. Step-by-Step Implementation Sequence
1. **SWE.2 Architecture (`docs/architecture.md`)**: Document `PolarLiveRunningCadenceEstimator` and H10/Verity Sense DSP pipeline in Section 2 & 3.4.
2. **Pure-Kotlin DSP (`PolarLiveRunningCadenceEstimator.kt`)**: Implement H10 `1.2–4.0 Hz` `ACC` band-pass + `5s` autocorrelation + adaptive peak detection, Verity `0.6–2.0 Hz` `GYRO+ACC` arm-swing fusion, `36–120 strides/min` clamping, `1 Hz` emission, and `>= 3s` standstill zeroing (`0.0`).
3. **Bridge Wiring (`PolarOnlineStreamingBridge.kt`)**: Route `processAccData` & `processGyroData` through `PolarLiveRunningCadenceEstimator`, publish strides/min `CADENCE` (`1 Hz` + `0.0` standstill) and `STRIDES`, and call `mStridesSensor.accumulate(0)` on `mStridesSensor` activation.
4. **Tier 1 Tests (`PolarLiveRunningCadenceEstimatorTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `MySensorManagerCadenceArbitrationTest.kt`, `FitFileWriterTest.kt`)**: Verify existing contracts remain intact (`syntheticStepFrame`, `FitFileWriter` cadence parity) and add `// TST-SET-116.1`..`// TST-SET-116.5` unit tests (`ATT-8143` compliance).
5. **Docs Sync (`REQ-SET-114`, `TST-SET-116`)**: Update statuses to `[Implemented]` and run `verify_requirement_governance.py --sync-indexes`.

## 6. Verification & Rollback Plan
- **Tier 1 Gradle**: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarLiveRunningCadenceEstimatorTest" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOnlineStreamingBridgeTest" --tests "com.atrainingtracker.banalservice.sensor.MySensorManagerCadenceArbitrationTest" --tests "com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriterTest"`
- **Governance**: `python3 tools/verify_requirement_governance.py`
- **Rollback**: Revert `PolarLiveRunningCadenceEstimator.kt` and `PolarOnlineStreamingBridge.kt` edits on `feature/ATT-8017` cleanly.
