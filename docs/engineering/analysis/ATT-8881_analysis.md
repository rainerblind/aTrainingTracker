# Stage 1 Analysis: ATT-8881 - Prevent 5-Hz Jeans-Pocket Pedaling Pressure Oscillations & Ascent Inflation

**Ticket**: `ATT-8881` (`2 SP`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-18_01_Früh` | **Branch**: `feature/ATT-8881` (base `504a3d61`) | **Date**: 2026-11-18

## 1. Problem Statement & Motivation
* Live `AltitudeAndSlopeDevice_ATTFused` (`att_fused_altitude_slope`) reported `+26.89 m` ascent / `-26.30 m` descent on a `2 km` ride (`Zum Bäcker #57`, `workoutID=2191`, `11 m` DEM envelope `435..446 m`) with live `SLOPE` swinging between `+12%`, `-10%`, `+7%`, and `-11%` on flat terrain (`438 -> 436 m`).
* Expected behavior: live barometric ingestion is rate-matched to `1 Hz` (preserving a true `~7 s` median window and lockstep with `1-Hz` `ekf.predict()`), adaptive `rBaro` damps cyclic pocket-compression jitter, flat-road `SLOPE` stays within `±2%`, and false `2.0 m` ascent/descent hysteresis steps are prevented.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Cause 1 — 5-Hz `SENSOR_DELAY_NORMAL` Collapses `BarometricAltitudeFilter` Window to `1.4 s`**:
  * `AltitudeAndSlopeDevice_ATTFused.kt:209` registers `Sensor.TYPE_PRESSURE` with `SensorManager.SENSOR_DELAY_NORMAL` (`~5 Hz`, `~200 ms`), dispatching `onSensorChanged()` -> `handlePressureMeasurement(event.values[0], System.currentTimeMillis())` (`:252-269`).
  * `BarometricAltitudeFilter.filterSlewOnly()` (`BarometricAltitudeFilter.kt:42, 114-156`) sizes `window` by callback count (`medianWindowSize = 7`), not elapsed seconds, and omits Stage 3 EMA (`alpha = 0.18`) per `REQ-FIL-016.2`. At `5 Hz`, 7 samples span `~1.4 s` (`1.2 s` intervals), letting `1.0–1.5 Hz` pedaling compression ripples and `1–2 s` pocket pulses pass at `±1.2 m/s` slew.
* **Cause 2 — 5-to-1 Update-to-Predict Asymmetry & Static `DEFAULT_VAR_BARO = 0.25`**:
  * `handlePressureMeasurement()` calls `ekf.updateBarometer(slewAlt, ...)` (`:267`) and `publishAltitudeAndMetrics()` (`:268`) `5x/s`, while `mScheduler` runs `calculateMetrics()` -> `ekf.predict(dtSec, speed)` (`:150, 308`) only `1x/s`.
  * With static `DEFAULT_VAR_BARO = 0.25` (`(0.5 m)^2`, `KinematicAltitudeSlopeEkf.kt:56`), 5 updates per `predict()` yield effective 1-s variance `0.25 / 5 = 0.05 m^2` (`80x` tighter than `DEFAULT_VAR_DEM = 4.0`). At cycling speeds (`5–6 m/s`, `lowSpeedSlopeGainFactor = 1.0`), `±0.8..1.2 m` pocket oscillations couple via `k1Eff` (`:289, 305`) into `±10–12%` slope swings and push `ekf.altitude` across `ASCENT_DESCENT_HYSTERESIS_METERS = 2.0` (`:68, 383-388`).
* **Independent Peer Review & Verification (Live `504a3d61`)**:
  * **Telemetry File Check**: `2026-11-18_055512.csv` is external field telemetry not committed in git; synthetic 5-Hz pedaling + pocket-pulse replay tests must model its exact profile (`±1.2 m` cyclic oscillation, `±8 m` pulses, flat `DEM = 438 m`, `speed = 6.0 m/s`).
  * **Unit-Test Timing & First-Sample Blind Spot (`AltitudeAndSlopeDevice_ATTFusedTest.kt`)**: Tests pass synthetic `timestampMs` directly to `handlePressureMeasurement()` without sleeps (`:149, 186, 420, 504, 634`) and assert immediate initialization on the very first sample (`:149-153` `ekf.isInitialized`, `:503-506` `568.4 ± 1.0 m` after 5x 200-ms ticks). Naive buffering that drops/defers the first sample or reads wall-clock time would break existing tests. `shutDown()` (`:177-199`) must also clear any sub-second aggregator state, and `calculateMetrics()` (`:291-310`) must coordinate cleanly with `1-Hz` baro flushing.
  * **2-State `p00` & Climb Convergence Blind Spot (`KinematicAltitudeSlopeEkfTest.kt:264`, `AltitudeAndSlopeDevice_ATTFusedTest.kt:452-465`)**: `testThreeStateRefBeforeBaro_andTwoStateGuard` asserts 2-state `p00 == DEFAULT_VAR_BARO (0.25)`, and `tstFil020_3` / `tstFil022_3` / `tstFil023_3` require full `22..27 m` ascent on steady `+0.83..0.84 m/s` climbs. Thus `DEFAULT_VAR_BARO = 0.25` MUST stay intact; damping cyclic pocket ripples requires **adaptive residual/jitter variance scaling** (inflating effective `varBaro` during oscillatory/sign-reversing jitter, not raising base `rBaro` indiscriminately).
  * **Sibling Audit**: `AltitudeFromPressureDevice.java:79, 101, 397` also uses `SENSOR_DELAY_NORMAL` + `BarometricAltitudeFilter.filter()`, and `BarometricAltitudeFilterTest.kt` tests 1-Hz `filter()`. Rate-matching inside `AltitudeAndSlopeDevice_ATTFused` (or additive time-window API without breaking `filter()`/`filterSlewOnly()`) avoids side effects on `AltitudeFromPressureDevice` and `ExtendedRtsAltitudeSlopeSmoother.kt` (offline 1-Hz post-workout path).

## 3. Scope Grounding & Non-Goals (`ATT-1250`)
* **In-Scope Goals**:
  * Rate-match 5-Hz `TYPE_PRESSURE` ingestion in `AltitudeAndSlopeDevice_ATTFused.kt` (and/or time-window support in `BarometricAltitudeFilter.kt`) to 1-Hz median/mean aggregation synced with `ekf.predict()`, preserving immediate first-sample init and deterministic `timestampMs` unit-test calls.
  * Add adaptive barometer jitter/oscillation variance (`varBaro`) scaling in `KinematicAltitudeSlopeEkf.kt` so cyclic `±0.8..1.2 m` pocket ripples do not swing `slopePercent` by `±10%` or trip `2.0 m` hysteresis on flat roads.
  * Add deterministic 5-Hz jeans-pocket oscillation & `Zum Bäcker #57` synthetic replay unit tests (`REQ-PRO-049.1`: 0 `src/main` reads).
* **Out-of-Scope Non-Goals**:
  * Modifying `AltitudeFromPressureDevice.java`, `ExtendedRtsAltitudeSlopeSmoother.kt`, `SpeedAndLocationDevice_ATTFused.kt`, `WorkoutRepository.kt`, UI/strings (`N/A - no UI strings`), DB schemas, or `ANT-Android-SDKs`.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-FIL-016.1`/`.2` (`ATT-6279`), `REQ-FIL-020.1`/`.2` (`ATT-8081`), `REQ-FIL-022` (`ATT-8575`), `REQ-FIL-023.2`/`.3` (`ATT-8576`), and `REQ-TRK-057.1` (`ATT-6265`). Targets `AltitudeAndSlopeDevice_ATTFused.kt`, `BarometricAltitudeFilter.kt`, `KinematicAltitudeSlopeEkf.kt`.
* **Historical Origin & Commit Trace**: `e079f7a9` (`ATT-6265`, 7-sample median + `1.2 m/s` slew), `75c1497e` / `33e60295` (`ATT-6279`, `ATTFused` + `filterSlewOnly` to avoid double-EMA lag), `8b0eba41` (`ATT-8081`, DEM precedence & `2.0 m` hysteresis), `23317f58` / `e9766625` (`ATT-8575`, 3-state `baroBias` & `p00 = DEFAULT_VAR_BARO` 2-state guard), `eba66320` / `f6715226` (`ATT-8576`, standstill clearing & low-speed `< 1.2 m/s` damping).
* **Root Reason for Existing Formulation**: `ATT-6265` designed `medianWindowSize = 7` assuming 1-Hz sampling; `ATT-6279` added `filterSlewOnly()` without rate-matching `SENSOR_DELAY_NORMAL`; `ATT-8576` damped low-speed (`< 1.2 m/s`) slope coupling but left cruising speeds (`5–6 m/s`, `gainScale = 1.0`) unprotected against 5-Hz cyclic pocket compression.
* **Preservation of Core Invariants**: Preserves `filterSlewOnly()` zero-double-EMA lag (`REQ-FIL-016.2`), `2.0 m` hysteresis & DEM-over-GPS precedence (`REQ-FIL-020`), 3-state `baroBias` & 2-state `p00 = DEFAULT_VAR_BARO` (`REQ-FIL-022`), standstill slope clearing & low-speed damping (`REQ-FIL-023`), and `@Synchronized` thread safety.

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-FIL-*` / `TST-FIL-*` refining `REQ-FIL-016.2`, `REQ-FIL-020.2`, `REQ-FIL-023.3`, and `REQ-TRK-057.1` (linking `SYSREQ-TRK-003`, `SYSREQ-TRK-004`).
* **Affected Architecture Elements**: `AltitudeAndSlopeDevice_ATTFused.kt`, `BarometricAltitudeFilter.kt` (optional time-window/aggregator support), `KinematicAltitudeSlopeEkf.kt`, plus `*Test.kt` suites (`WorkoutRepository.kt` LoC `<= 5,117` untouched).
* **Verification Impact & Test Surface**: `AltitudeAndSlopeDevice_ATTFusedTest.kt`, `BarometricAltitudeFilterTest.kt`, `KinematicAltitudeSlopeEkfTest.kt`, `SourceTextReadingAuditContractTest` (`REQ-PRO-049.1`), `ArchitectureConformanceContractTest` (`N/A - no UI strings`).
* **Risk Assessment & Invariants**: **MEDIUM** (`2 SP`). Rate-matching and adaptive `varBaro` must suppress flat-road `±1.2 m` pedaling oscillations without lagging true `0.4–0.84 m/s` climbs or breaking deterministic 1-Hz / single-sample test calls.

## 6. System Invariants & Risk Assessment
* **Core Invariants**: `DEFAULT_VAR_BARO = 0.25` & 2-state `p00 = varBaro`; immediate first-sample EKF initialization; `2.0 m` ascent/descent hysteresis (`REQ-FIL-020.2`); `baroBias` thermal/synoptic offset absorption (`REQ-FIL-022`); `@Synchronized` thread safety; `WorkoutRepository.kt` untouched (`<= 5,117` LoC); `REQ-PRO-049.1` (`0` new `src/main` test reads); `ExtendedRtsAltitudeSlopeSmoother.kt` & `AltitudeFromPressureDevice.java` untouched.
* **Risk Rating**: **MEDIUM** — touches live EKF variance adaptation and sensor ingestion cadence where over-damping could attenuate real climbs or break single-call unit test assumptions.
