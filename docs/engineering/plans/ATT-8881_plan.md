# Stage 3: Implementation Plan - ATT-8881: Prevent 5-Hz Jeans-Pocket Pedaling Pressure Oscillations & Ascent Inflation

**Ticket**: [ATT-8881](https://rainerblind.atlassian.net/browse/ATT-8881) (`2 SP`) | **Sub-task**: `[Impl-Plan]` | **Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_01_Früh` | **Branch**: `feature/ATT-8881`  
**Requirement Mapping**: `REQ-FIL-026` (`REQ-FIL-026.1`..`.3`) | **Test Mapping**: `TST-FIL-026` (`TST-FIL-026.1`..`.4`) | **Date**: 2026-11-18

---

## 1. Problem Description & Background
* Hardware `Sensor.TYPE_PRESSURE` (`SENSOR_DELAY_NORMAL`, `~5 Hz` / `200 ms`) drives `AltitudeAndSlopeDevice_ATTFused.handlePressureMeasurement()` `5x` per `1-Hz` `ekf.predict()`, shrinking `BarometricAltitudeFilter.medianWindowSize = 7` from `7 s` to `~1.4 s` and collapsing effective 1-s `varBaro` from `0.25` to `0.05 m^2`.
* Jeans-pocket pedaling oscillations (`±0.8..1.2 m` at `1.0–1.5 Hz`) and compression pulses (`±8 m`) couple into `±10–12%` flat-road `SLOPE` swings and `+26.89 m` ascent inflation (`Zum Bäcker #57`).

## 2. Traceability & Requirements Mapping

| Requirement | Plan Component | Verification (`TST-FIL-026`) |
| :--- | :--- | :--- |
| `REQ-FIL-026.1` | `AltitudeAndSlopeDevice_ATTFused.kt`: 1-Hz median bucket aggregation, immediate 1st-sample init, `calculateMetrics()` sync, `shutDown()` clear | `TST-FIL-026.1`, `TST-FIL-026.3` |
| `REQ-FIL-026.2` | `KinematicAltitudeSlopeEkf.kt`: adaptive cyclic-jitter `varBaro` inflation while preserving `DEFAULT_VAR_BARO = 0.25` & 2-state `p00` | `TST-FIL-026.1`, `TST-FIL-026.2` |
| `REQ-FIL-026.3` | Scope isolation (`AltitudeFromPressureDevice`, `ExtendedRtsAltitudeSlopeSmoother`, `WorkoutRepository` untouched; `0` `src/main` test reads; `N/A - no UI strings`) | `TST-FIL-026.3`, `TST-FIL-026.4` |

## 3. System Invariants & Preserved Behavior
1. **Barometric Filter & Double-EMA Lag (`REQ-FIL-016.2`)**: `BarometricAltitudeFilter.filterSlewOnly()` (`medianWindowSize = 7`, `maxSlewRateMps = 1.2`) and `BarometricAltitudeFilter.kt` public behavior remain intact; `AltitudeFromPressureDevice.java` stays untouched.
2. **EKF Contracts (`REQ-FIL-020`, `REQ-FIL-022`, `REQ-FIL-023`)**: `DEFAULT_VAR_BARO = 0.25` and 2-state `p00 = varBaro` (`KinematicAltitudeSlopeEkfTest:264`), `2.0 m` ascent/descent hysteresis, 3-state `baroBias` anchoring, standstill slope clearing, and `< 1.2 m/s` damping remain preserved; steady climbs (`+0.83..0.84 m/s`) retain full `22..27 m` ascent.
3. **Deterministic Unit Tests & Thread Safety**: Immediate initialization on sample 1 (`!ekf.isInitialized`), pure `timestampMs`-driven aggregation (no `System.currentTimeMillis()` / sleeps in tests), `@Synchronized` locks, and clean `shutDown()` reset.
4. **Architecture & Process Hygiene**: `WorkoutRepository.kt` (`<= 5,117` LoC), `ExtendedRtsAltitudeSlopeSmoother.kt`, `SpeedAndLocationDevice_ATTFused.kt`, and UI strings remain untouched (`N/A - no UI strings`). No new subsystem/DB migration/`banal_service.md` state transitions (`REQ-PRO-114` N/A). `SourceTextReadingAuditContractTest` (`REQ-PRO-049.1`: `0` new `src/main` reads) and human parent gate preserved.

## 4. Proposed Architectural Changes
### Component 1: `AltitudeAndSlopeDevice_ATTFused.kt` (1-Hz Rate-Matched Baro Ingestion)
* Add `@Synchronized`-guarded sub-second bucket state (`mBaroBucketAltitudes`, `mLastBaroEmitTimestampMs`, `mLastBaroBucketTimestampMs`) with `BARO_AGGREGATION_WINDOW_MS = 950L` (tolerating `~200 ms` Android sensor jitter for 5-sample 1-Hz buckets while flushing deterministic `1000 ms` test steps every tick).
* In `handlePressureMeasurement(pressureHpa, timestampMs)`: convert `rawAlt` (guard `isFinite()`), emit immediately if `mLastBaroEmitTimestampMs < 0L` (1st sample init) or on clock regression guard, else buffer `rawAlt` and flush the bucket **median** to `ingestFilteredBaroAltitude(medianAlt, timestampMs)` (`baroFilter.filterSlewOnly` -> `ekf.updateBarometer` -> `publishAltitudeAndMetrics`) once `timestampMs - mLastBaroEmitTimestampMs >= BARO_AGGREGATION_WINDOW_MS`.
* In `calculateMetrics(nowMs)`: if `mBaroBucketAltitudes.isNotEmpty()` and `nowMs - mLastBaroEmitTimestampMs >= BARO_AGGREGATION_WINDOW_MS`, flush pending bucket median at `mLastBaroBucketTimestampMs` prior to `ekf.predict(dtSec, speed)`.
* In `shutDown()`: clear `mBaroBucketAltitudes` and reset `mLastBaroEmitTimestampMs = -1L`, `mLastBaroBucketTimestampMs = -1L`.

### Component 2: `KinematicAltitudeSlopeEkf.kt` (Adaptive Cyclic Pocket-Jitter `varBaro` Scaling)
* Add `@Synchronized`-guarded jitter tracking state (`lastMovingBaroAltM`, `lastBaroDeltaM`, `lastBaroResidualM`, `adaptiveBaroJitterVar`, reset in `reset()`).
* In `updateBarometer(...)` when `isInitialized && hasBarometerSample && !isStandstill`: inspect consecutive baro step `deltaZ` and second difference `d2 = deltaZ - lastBaroDeltaM` plus innovation sign reversals (`y * lastBaroResidualM < 0`).
* On oscillatory/sign-reversing steps (`deltaZ * lastBaroDeltaM < 0` or `y * lastBaroResidualM < 0`), increase `adaptiveBaroJitterVar` (fast EMA attack `0.35`, slow decay `0.10` on consistent-sign climbs, capped at `MAX_ADAPTIVE_VAR_BARO = 25.0`), and use `effectiveVarBaro = varBaro + adaptiveBaroJitterVar` in Kalman gain/innovation variance `s` and covariance updates (`2-State` & `3-State`). Monotonic climbs yield `d2 ~ 0` -> `adaptiveBaroJitterVar -> 0` -> zero climb lag; cyclic `±1.2 m` ripples inflate `effectiveVarBaro` so flat `slopePercent` stays within `±2%` and `ekf.altitude` stays inside the `2.0 m` hysteresis band.

### Component 3: Offline Behavioral & Replay Test Suites (`REQ-PRO-049.1`)
* `AltitudeAndSlopeDevice_ATTFusedTest.kt`: add `TST-FIL-026.1` (5-Hz flat `±1.2 m` pedaling + `±8 m` pulse rejection over `60 s`; synthetic `2 km` `Zum Bäcker #57` `435..446 m` replay with totals in `10–16 m`) & `TST-FIL-026.3` (`shutDown()` aggregator clearing + restart).
* `KinematicAltitudeSlopeEkfTest.kt`: add `TST-FIL-026.2` (cyclic sign-reversing `±1.2 m` ripples at `5–6 m/s` keeping `|slopePercent| <= 2%`, plus unattenuated `+0.83 m/s` climb verification).

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Gate 3 Verification (`Rule 4.1`)**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` (`GATE_PASSED`).
2. **Step 1 (`AltitudeAndSlopeDevice_ATTFused.kt`)**: Implement `@Synchronized` 1-Hz median bucket aggregation, immediate 1st-sample emit, `calculateMetrics()` pending-bucket flush, and `shutDown()` reset.
3. **Step 2 (`KinematicAltitudeSlopeEkf.kt`)**: Implement second-difference / sign-reversal jitter detector + adaptive `effectiveVarBaro` scaling and `reset()` cleanup, preserving `DEFAULT_VAR_BARO = 0.25` & 2-state `p00`.
4. **Step 3 (`AltitudeAndSlopeDevice_ATTFusedTest.kt`, `KinematicAltitudeSlopeEkfTest.kt`)**: Add `TST-FIL-026.1`..`.3` deterministic offline unit/replay tests (`0` `src/main` reads).
5. **Step 4 (Verification)**: Execute batched Tier-1 Gradle tests & governance check below.

## 6. Verification & Rollback Plan
* **Verification (Single Batched `Rule 7.5` Gradle Run under POSIX `flock`)**:
  ```bash
  python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest \
    --tests "com.atrainingtracker.banalservice.devices.AltitudeAndSlopeDevice_ATTFusedTest" \
    --tests "com.atrainingtracker.banalservice.devices.BarometricAltitudeFilterTest" \
    --tests "com.atrainingtracker.banalservice.odometry.KinematicAltitudeSlopeEkfTest" \
    --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" \
    --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
    --max-workers=1 --rerun-tasks
  ```
  Plus `python3 tools/verify_requirement_governance.py --repo-root .` (exit `0`).
* **Rollback**: All changes isolated on `feature/ATT-8881`; revert or drop branch without affecting `sprint/2026-11-18_01_Früh`.
