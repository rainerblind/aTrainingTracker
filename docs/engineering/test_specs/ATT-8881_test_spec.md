# Stage 2: Requirement & Test Specification - ATT-8881

**DOC-ID**: `DOC-TST-ATT-8881` | **Ticket**: [ATT-8881](https://rainerblind.atlassian.net/browse/ATT-8881) (`2 SP`) | **Parent**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-18_01_Früh` | **Branch**: `feature/ATT-8881`  
**Requirement Mapping**: `REQ-FIL-026` | **Test Spec ID**: `TST-FIL-026` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-FIL-026` `[Planned]`)
* **Attributes**: Priority: `High` | Type: `Functional` | Verification Method: `Unit` | System Requirement(s): `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
* **Implementation**: `AltitudeAndSlopeDevice_ATTFused.kt`, `BarometricAltitudeFilter.kt`, `KinematicAltitudeSlopeEkf.kt`
* **`REQ-FIL-026.1` (1-Hz Barometric Rate-Matching, First-Sample Init & Deterministic Flushing)**: `AltitudeAndSlopeDevice_ATTFused` SHALL aggregate `handlePressureMeasurement(pressureHpa, timestampMs)` samples into `1-Hz` (`1,000 ms` window) representative barometric observations before invoking `BarometricAltitudeFilter.filterSlewOnly()` (`medianWindowSize = 7` spanning `~7 s`, zero double-EMA lag per `REQ-FIL-016.2`), `ekf.updateBarometer()`, and `publishAltitudeAndMetrics()` in lockstep with `1-Hz` `calculateMetrics()` / `ekf.predict()`. On the very first `handlePressureMeasurement()` call (`!ekf.isInitialized`), `ekf` MUST initialize and publish `mAltitudeSensor` immediately using deterministic `timestampMs` (no wall-clock reads). `calculateMetrics(nowMs)` SHALL coordinate cleanly with pending sub-second buffers, and `shutDown()` MUST clear all sub-second aggregator state alongside `baroFilter.reset()` and `ekf.reset()`.
* **`REQ-FIL-026.2` (Adaptive Cyclic Pocket-Jitter Variance Scaling & `DEFAULT_VAR_BARO` Guard)**: `KinematicAltitudeSlopeEkf` MUST preserve `DEFAULT_VAR_BARO = 0.25` and 2-state `p00 = varBaro` (`KinematicAltitudeSlopeEkfTest:264`). During active barometric updates, it SHALL detect oscillatory/sign-reversing residual jitter and adaptively inflate effective `varBaro` so `±0.8..1.2 m` pedaling ripples at `5–6 m/s` keep flat-road `slopePercent` within `±2%` and prevent false `2.0 m` hysteresis steps (`REQ-FIL-020.2`), without attenuating genuine monotonic climbs (`0.4–0.84 m/s`).
* **`REQ-FIL-026.3` (Scope Isolation, Localization & Test Hygiene)**: `AltitudeFromPressureDevice.java`, `ExtendedRtsAltitudeSlopeSmoother.kt`, `SpeedAndLocationDevice_ATTFused.kt`, and `WorkoutRepository.kt` (`<= 5,117` LoC) MUST stay untouched (`Localization Parity: N/A - no UI strings`), with `0` new `src/main` source-text reads in tests (`REQ-PRO-049.1`).

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-FIL-026` refining `REQ-FIL-016.2` (`75c1497e`/`33e60295`, `filterSlewOnly()`), `REQ-FIL-020.2` (`8b0eba41`, `2.0 m` hysteresis), `REQ-FIL-023.3` (`eba66320`/`f6715226`, `< 1.2 m/s` slope damping), and `REQ-TRK-057.1` (`e079f7a9`, 7-sample median + `1.2 m/s` slew). Targets `AltitudeAndSlopeDevice_ATTFused.kt`, `BarometricAltitudeFilter.kt`, `KinematicAltitudeSlopeEkf.kt`.
* **Historical Origin & Root Reason**: `ATT-6265` calibrated `medianWindowSize = 7` assuming `1-Hz` sampling; `ATT-6279` added `filterSlewOnly()` without rate-matching `SENSOR_DELAY_NORMAL` (`5 Hz`); `ATT-8576` damped low-speed (`< 1.2 m/s`) slope coupling while leaving `5–6 m/s` cruising speeds exposed to 5-Hz pocket compression.
* **Preservation of Core Invariants**: Preserves `REQ-FIL-016.2` `filterSlewOnly()` zero double-EMA lag, `BarometricAltitudeFilter.medianWindowSize = 7`, `DEFAULT_VAR_BARO = 0.25` & 2-state `p00 = varBaro`, immediate first-sample `ekf.isInitialized`, clean `shutDown()` reset, `REQ-FIL-020.2` `2.0 m` hysteresis, `REQ-FIL-022` `baroBias`, `AltitudeFromPressureDevice.java` untouched, `WorkoutRepository.kt <= 5,117` LoC untouched, `@Synchronized` safety, and `SourceTextReadingAuditContractTest` (`0` new `src/main` reads).

## 3. Acceptance Criteria (Given-When-Then)
* *Given* `AltitudeAndSlopeDevice_ATTFused` receiving `5-Hz` (`200 ms` `timestampMs`) pressure events with `±1.2 m` pedaling oscillations (`1.0–1.5 Hz`) and `±8 m` compression pulses over `60 s` on flat `DEM = 438.0 m` at `6.0 m/s`, *When* processed alongside `1-Hz` `calculateMetrics()`, *Then* baro EKF updates are rate-matched to `1 Hz`, `slopePercent` stays within `±2%`, and `ASCENT`/`DESCENT` remain `0.0 m`.
* *Given* a synthetic `2 km` `Zum Bäcker #57` profile (`435..446 m` envelope with `±1.2 m` pocket ripples and `±8 m` pulses), *When* replayed through `AltitudeAndSlopeDevice_ATTFused`, *Then* total ascent and descent remain within `10–16 m` without `±10%` flat-road slope swings or `+26.89 m` inflation.
* *Given* a single `handlePressureMeasurement(1013.25f, 1000L)` call, a `shutDown()` restart, or steady `+0.83..0.84 m/s` climbs (`tstFil020_3`, `tstFil022_3`, `tstFil023_3`), *When* executed, *Then* `ekf.isInitialized` and `mAltitudeSensor` populate on sample 1, `shutDown()` clears all aggregator buffers, 2-state `p00 == 0.25` stays intact, and real climbs accumulate full `22..27 m` ascent without lag.

## 4. Test Specification (`TST-FIL-026`, Offline Unit — `REQ-PRO-049.1`)
* **`TST-FIL-026.1` (`AltitudeAndSlopeDevice_ATTFusedTest`)**: Simulate `5-Hz` (`200 ms`) flat-road `±1.2 m` pedaling oscillations + `±8 m` pulses (`60 s`, `6.0 m/s`, `DEM = 438.0 m`) and full `2 km` `Zum Bäcker #57` synthetic replay; assert `1-Hz` EKF sync, flat `|slopePercent| <= 2%`, `0.0 m` flat ascent/descent, and `2 km` totals in `10–16 m`.
* **`TST-FIL-026.2` (`KinematicAltitudeSlopeEkfTest`)**: Verify 2-state `p00 == DEFAULT_VAR_BARO` (`0.25`), adaptive `varBaro` inflation on cyclic sign-reversing `±1.2 m` oscillations (`|slopePercent| <= 2%` at `5–6 m/s`), and unattenuated convergence on steady `+0.83..0.84 m/s` climbs.
* **`TST-FIL-026.3` (`AltitudeAndSlopeDevice_ATTFusedTest`, `BarometricAltitudeFilterTest`)**: Verify immediate 1st-sample `ekf.isInitialized` & `mAltitudeSensor` population, `shutDown()` aggregator clearing across restarts, and unchanged pass of `tstFil020_3`, `tstFil022_3`, `tstFil023_2`, `tstFil023_3`, and `BarometricAltitudeFilterTest` (`medianWindowSize = 7`, `filterSlewOnly()`).
* **`TST-FIL-026.4` (`SourceTextReadingAuditContractTest`)**: Run `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.AltitudeAndSlopeDevice_ATTFusedTest" --tests "com.atrainingtracker.banalservice.devices.BarometricAltitudeFilterTest" --tests "com.atrainingtracker.banalservice.odometry.KinematicAltitudeSlopeEkfTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"` (`100%` pass, `0` new `src/main` reads, `N/A - no UI strings`).

## 5. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-026.1` | Unit | `AltitudeAndSlopeDevice_ATTFused`, `BarometricAltitudeFilter` | `REQ-FIL-026.1`, `.2`, `.3` | Proposed |
| `TST-FIL-026.2` | Unit | `KinematicAltitudeSlopeEkf` | `REQ-FIL-026.2` | Proposed |
| `TST-FIL-026.3` | Unit | `AltitudeAndSlopeDevice_ATTFused`, `BarometricAltitudeFilter` | `REQ-FIL-026.1`, `.2`, `.3` | Proposed |
| `TST-FIL-026.4` | Contract | `SourceTextReadingAuditContractTest` | `REQ-FIL-026.3` | Proposed |
