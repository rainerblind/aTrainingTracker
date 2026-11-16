# Stage 2: Requirement & Test Specification - ATT-8017: Polar Live Running Cadence & Stride Counter

**Ticket**: [ATT-8017](https://rainerblind.atlassian.net/browse/ATT-8017) (`3 SP`) | **Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-SET-114` (`REQ-SET-114.1`..`REQ-SET-114.4`) | **Test Spec ID**: `TST-SET-116` (`TST-SET-116.1`..`TST-SET-116.5`)  
**Author**: AI Agent 2 (`Lane 3 Flash`, fused Stage 2) | **Stage 4 Mandate**: **HIGH-RISK** (`subagent_pro` Doing / `subagent_flash` Gate 4 per `Rule 7.1`, non-UI DSP engine)

---

## 1. Requirement Specification (`REQ-SET-114`)

- **Priority**: High | **Type**: Functional | **Verification Method**: Unit / Integration
- **Implementation**: `PolarLiveRunningCadenceEstimator.kt`, `PolarOnlineStreamingBridge.kt`, `docs/architecture.md`

### 1.1 Normative Clauses
1. **Canonical Strides/Min & Stride-Cycle Parity (`REQ-SET-114.1`)**: `PolarLiveRunningCadenceEstimator` and `PolarOnlineStreamingBridge` SHALL publish `SensorType.CADENCE` strictly in **strides/min** (`60–100` strides/min = `120–200` steps/min), matching `ANTRunSpeedDevice`, `BTLERunSpeedDevice` (`0x2A53`), and `FitFileWriter` (`RecordMesg.cadence`/`SessionMesg.avgCadence` `UINT8` where `85–90` strides/min renders as `170–180` spm on Strava). `syncCadenceSensorRegistration()` SHALL seed `mStridesSensor.accumulate(0)` before stride 1 (`MyAccumulatorSensor` baseline parity) and increment `SensorType.STRIDES` once per 2-step stride cycle.
2. **Polar H10 Sternum `ACC` Estimator (`REQ-SET-114.2`)**: For H10 `ACC` (`50–100/200` Hz), apply `1.2–4.0 Hz` band-pass filtering (`72–240` steps/min) on acceleration magnitude + adaptive foot-strike peak detection + sliding `5s` autocorrelation without harmonic doubling/halving.
3. **Polar Verity Sense `GYRO+ACC` Fusion (`REQ-SET-114.3`)**: For Verity Sense `ACC+GYRO` (`52` Hz), fuse orientation-invariant `GYRO` angular-rate magnitude and `ACC` arm-swing envelope across `0.6–2.0 Hz` (`36–120` strides/min = `72–240` steps/min), immune to armband rotation.
4. **1-Hz Emission & `>= 3s` Standstill Zeroing (`REQ-SET-114.4`)**: Emit `1 Hz` `SensorType.CADENCE` updates within `±2` strides/min across `60–100` strides/min (`BSportType.RUN`, incl. `TTSportType.WALK`), and drop cadence cleanly to `0.0` via motion-energy gating after `>= 3s` of standstill.

### 1.2 Acceptance Criteria & Invariants
- *Given* steady running/walking (`60–100` strides/min) with H10 (`ACC`) or Verity Sense (`ACC+GYRO`, any orientation), *When* processed by `PolarLiveRunningCadenceEstimator` + `PolarOnlineStreamingBridge`, *Then* `SensorType.CADENCE` updates at `1 Hz` within `±2` strides/min, `SensorType.STRIDES` counts from stride `1`, and `FitFileWriter` exports half-step strides/min.
- *Given* `>= 3s` of standstill during a run, *When* evaluated by motion-energy gating, *Then* `SensorType.CADENCE` drops cleanly to `0.0`.
- **Invariants**: `BSportType.RUN`-only gating & `MySensorManager.CADENCE_PRIORITY` (`RUN_SPEED`/`BIKE_*` > `HRM`) preserved; zero UI/DB/exporter/`ANT-Android-SDKs` edits; non-UI DSP engine (zero UI screenshot needed); **HIGH-RISK** Stage 4 (`subagent_pro` Doing / `subagent_flash` Gate 4, `Rule 7.1`).

---

## 2. Test Specification (`TST-SET-116`)

- **TST-SET-116.1 (`PolarLiveRunningCadenceEstimatorTest`)**: Verify H10 `ACC` (50/100 Hz) `1.2–4.0 Hz` band-pass + `5s` autocorrelation across `60–100` strides/min (`±2` strides/min, zero harmonic lock).
- **TST-SET-116.2 (`PolarLiveRunningCadenceEstimatorTest`)**: Verify Verity Sense `ACC+GYRO` (52 Hz) `0.6–2.0 Hz` fusion within `±2` strides/min across `60–100` strides/min and rotated sensor axes.
- **TST-SET-116.3 (`PolarLiveRunningCadenceEstimatorTest`, `PolarOnlineStreamingBridgeTest`)**: Verify `1 Hz` cadence emission and clean `0.0` standstill zeroing at `>= 3s`.
- **TST-SET-116.4 (`PolarOnlineStreamingBridgeTest`)**: Verify `mStridesSensor.accumulate(0)` baseline seeding on `BSportType.RUN` registration so stride `1` increments `SensorType.STRIDES` to `1`.
- **TST-SET-116.5 (`PolarOnlineStreamingBridgeTest` + `FitFileWriter` parity)**: Verify half-step strides/min parity with `ANTRunSpeedDevice`/`BTLERunSpeedDevice`/`FitFileWriter` (`RecordMesg.cadence`/`SessionMesg.avgCadence`).

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-116.1` | DSP / H10 ACC | `PolarLiveRunningCadenceEstimator` | `REQ-SET-114.2` | Planned |
| `TST-SET-116.2` | DSP / Verity Fusion | `PolarLiveRunningCadenceEstimator` | `REQ-SET-114.3` | Planned |
| `TST-SET-116.3` | DSP / Standstill | `PolarLiveRunningCadenceEstimator`, `PolarOnlineStreamingBridge` | `REQ-SET-114.4` | Planned |
| `TST-SET-116.4` | Accumulator Seed | `PolarOnlineStreamingBridge`, `MyIntegerAccumulatorSensor` | `REQ-SET-114.1` | Planned |
| `TST-SET-116.5` | Unit / FIT Parity | `PolarOnlineStreamingBridge`, `FitFileWriter` | `REQ-SET-114.1` | Planned |
