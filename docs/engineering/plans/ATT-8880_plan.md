# Stage 3: Implementation Plan - ATT-8880: Fix Post-Workout ERTS Batch Timestamp Quantization, Indoor Stop Drift, Jeans-Pocket Gyro/Baro Artifacts, Lap Distance Sync, and Stream Subsampling

**Ticket**: [ATT-8880](https://rainerblind.atlassian.net/browse/ATT-8880) (`3 SP`, `severity-major`) | **Sub-task**: `[Impl-Plan]` | **Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_01_Früh` | **Branch**: `feature/ATT-8880`  
**Requirement Mapping**: `REQ-FIL-027` (`REQ-FIL-027.1`..`.5`) | **Test Mapping**: `TST-FIL-027` (`TST-FIL-027.1`..`.5`), `TST-FIL-017.3`, `TST-FIL-019.3` | **Date**: 2026-11-18

---

## 1. Problem Description & Background
* Field cycling telemetry (`Zum Bäcker #57`, `workoutID = 2191`, `607 s`, smartphone in front jeans pocket) revealed 5 compounding defects across `SpeedAndLocationDevice_ATTFused`, `ErtsPostWorkoutRecomputer`, `ExtendedRtsOdometrySmoother`, `UnicycleOdometryEkf`, and `ExtendedRtsAltitudeSlopeSmoother`:
  1. SQLite `time` (`DEFAULT CURRENT_TIMESTAMP`) batch-flush `dt = 0 s / 2 s` quantization stalls EKF prediction (`-88.65 m` distance loss, `-5.3 m` turn-braking), drops `VERTICAL_SPEED` to `0 m/h`, and bypasses `useAntiAliasedYaw`.
  2. `SpeedAndLocationDevice_ATTFused` overwrites `mWorldYawRateSensor` with the last 20-ms gyro sample, logging `1.2–1.5 Hz` thigh-pedaling aliases into `WORLD_YAW_RATE_radps` and wheel calibration.
  3. Indoor stops (`140 s`, `speed <= 0.05`, `acc = 50 m`) omit ZUPT and stationary RTS cross-covariance decoupling, letting backward RTS pull post-stop departure motion backwards (`+31.66 m` phantom drift) and collapse pre-stop speeds.
  4. `ALLOWED_CANONICAL_COLUMNS` omits `DISTANCE_m_LAP`, and `updateWorkoutSummaries()` encodes unsimplified `1-Hz` `mapPolyline` / `distanceStream` / `altitudeStream` instead of matching `TrackerService.finalizeLiveSession()`.
  5. `ExtendedRtsAltitudeSlopeSmoother` sums unthresholded 1-Hz micro-ripples without `2.0 m` hysteresis (`REQ-FIL-020.2`), and `ErtsPostWorkoutRecomputer` ingests filtered canonical `ALTITUDE` instead of pre-filtering 9-locale raw barometer columns via `BarometricAltitudeFilter.filterSlewOnly()`.

## 2. Traceability & Requirements Mapping

| Requirement | Plan Component | Verification |
| :--- | :--- | :--- |
| `REQ-FIL-027.1` | `ErtsPostWorkoutRecomputer.readWorkoutSamples()`: monotonic `TIME_TOTAL` timestamp reconstruction + `max(parsedTs, prevTs + 1000L)` fallback before baro/ERTS | `TST-FIL-027.1` |
| `REQ-FIL-027.2` | `SpeedAndLocationDevice_ATTFused.kt`: rolling `1.0 s` time-weighted mean `mWorldYawRateSensor`, immediate sample-1 publish, clean reset/shutdown | `TST-FIL-027.2` |
| `REQ-FIL-027.3` | `ExtendedRtsOdometrySmoother.kt` (+ `UnicycleOdometryEkf.kt` guard): stationary epoch ZUPT + RTS `[0..1][2]` decoupling + `0.0 m/s` clamp | `TST-FIL-027.3` |
| `REQ-FIL-027.4` | `ErtsPostWorkoutRecomputer.kt`: `DISTANCE_m_LAP` in `ALLOWED_CANONICAL_COLUMNS` + per-lap reset; `PolyUtil.simplify(..., 10.0)` & `ENCODING_STEP_SIZE` (`20`) parity | `TST-FIL-027.4`, `TST-FIL-017.3`, `TST-FIL-019.3` |
| `REQ-FIL-027.5` | `ErtsPostWorkoutRecomputer.kt` (9-locale barometer `filterSlewOnly()`) & `ExtendedRtsAltitudeSlopeSmoother.kt` (`2.0 m` anchor hysteresis) | `TST-FIL-027.5` |

## 3. System Invariants & Preserved Behavior
1. **Raw DB Column & Default Gate Invariants (`REQ-FIL-017.3`, `REQ-FIL-017.5`)**: Device-attributed `WorkoutSamples.db` columns remain 100% byte-for-byte untouched; `KEY_POST_WORKOUT_ERTS_SMOOTHING = false` default preserved; Null-Island `NULL` handling (`REQ-FIL-019`) intact.
2. **Live EKF & Canopy Dropout Contracts (`REQ-FIL-023.1`, `REQ-FIL-025.2`)**: `UnicycleOdometryEkf.ZUPT_SPEED_VARIANCE = 2.0`, `MIN_DOPPLER_SPEED_MPS = 3.0`, and `ExtendedRtsOdometrySmootherTest:140` (`wheelSpeedMps = 10.0`, `gnssSpeed = 0.0`) remain unbroken.
3. **Gyro Sample-1 & Lifecycle Safety (`REQ-SEN-028.3`)**: `SpeedAndLocationDevice_ATTFusedTest:126-139` immediate `0.1 rad/s` sample-1 emission preserved; `mLastGyroTimestampNs = 0L` and yaw window cleared in `resetOriginAndEkf()` and `shutDown()`.
4. **Architecture & Process Hygiene**: `WorkoutRepository.kt` (`5,048 <= 5,117` LoC), `TrackerService.java`, `AltitudeAndSlopeDevice_ATTFused.kt`, `BarometricAltitudeFilter.kt`, and UI/strings remain untouched (`N/A - no UI strings`; `REQ-PRO-114` detailed design N/A — no `TrackerService`/`BANALService`/`MyBTLEDevice` state-machine changes; `REQ-PRO-049.1` `0` new `src/main` reads).

## 4. Proposed Architectural Changes
### Component 1: `SpeedAndLocationDevice_ATTFused.kt` (Rolling 1.0-s Time-Weighted Yaw-Rate Window)
* Add a `@Synchronized` / lock-guarded rolling `1.0 s` window (`ArrayDeque<YawRateSample>` tracking cumulative `dtSec` and `omega * dtSec` sums over a `1.0 s` span, always retaining at least the latest sample).
* Route both `onSensorChanged()` (`TYPE_GYROSCOPE`) and `@VisibleForTesting ingestImuMeasurement()` through a shared helper `recordWorldYawRate(worldYawRate, dtSec)` so sample 1 immediately publishes `worldYawRate` (`0.1 rad/s`), while 50-Hz callbacks publish the rolling `1.0 s` time-weighted mean $\sum \omega_i \Delta t_i / \sum \Delta t_i$ (eliminating thigh-pedaling aliases in both `WORLD_YAW_RATE_radps` and `onLocationChanged()`'s wheel calibration).
* Clear the yaw-rate window and reset `mLastGyroTimestampNs = 0L` and `mHasRotationVector = false` inside `resetOriginAndEkf()` and `shutDown()`.

### Component 2: `ErtsPostWorkoutRecomputer.kt` (Timing, 9-Locale Baro, `DISTANCE_m_LAP`, Stream Parity)
* **Monotonic `timestampMs` (`readWorkoutSamples()`)**: Read `SensorType.TIME_TOTAL.name` alongside `time`. Anchor `baseEpochMs` to the first valid `parseTimestampMs(timeStr)`; compute `baseEpochMs + (timeTotal - firstTimeTotal) * 1000L` while `TIME_TOTAL` strictly advances (preserving genuine wall-clock gaps if `time` jumps beyond `TIME_TOTAL`), and fall back to `parseTimestampMs(timeStr)` with strictly monotonic `max(candidate, prevTs + 1000L)` when `TIME_TOTAL` is absent/null/non-increasing. Compute `ts` **before** filtering barometer samples.
* **9-Locale Raw Barometer `filterSlewOnly()`**: Match barometer device columns across all 9 locales + ATT-5073 legacy names (`WorkoutSamplesDatabaseManager.java:927-936` names inside `"ALTITUDE (<name>)"`, case-insensitive, excluding `DEM`, `Topografische`, `GPS`, `att_fused`). Pre-filter non-null barometer values via a per-pass `BarometricAltitudeFilter().filterSlewOnly(baroAlt, ts)`, falling back to canonical `ALTITUDE` only when absent or row-null.
* **`DISTANCE_m_LAP` Sync (`ALLOWED_CANONICAL_COLUMNS`, `updateSamplesDatabase()`, `bindColumnValue()`)**: Add `SensorType.DISTANCE_m_LAP.name` to `ALLOWED_CANONICAL_COLUMNS` and `colsToUpdate` when present; track `lapCumulativeDist` and reset to `0.0` on `LAP_NR` transitions **before** adding `odom.deltaDistanceMeters`, binding `DISTANCE_m_LAP` in parity with `LapsDatabaseManager.syncLapDistancesAndSpeeds()`.
* **Summary Stream Parity (`updateWorkoutSummaries()`)**: Simplify `validLatLngs` with `PolyUtil.simplify(validLatLngs, 10.0)` before `PolyUtil.encode(...)`; subsample `distances` and `altitudes` via a shared `subsampleStream(values, WorkoutSummaries.ENCODING_STEP_SIZE)` helper (`step = 20`: indices `19, 39, 59, ...`, retaining `[first, last]` when `values.size in 2 until 20` or `values` when `size <= 1`).

### Component 3: `ExtendedRtsOdometrySmoother.kt` (+ `UnicycleOdometryEkf.kt` Read-Only / Additive Guard)
* Classify stationary epochs strictly in `smoothSamples()` as `val isStationary = sample.speedMps != null && sample.speedMps <= 0.05 && (sample.wheelSpeedMps == null || sample.wheelSpeedMps <= 0.05)`.
* On stationary epochs: call `ekf.applyZeroVelocityUpdate(sample.timestampMs)`, pass `isStationary = true` to `ekf.updateGnssPosition(...)`, and skip `ekf.updateSpeed(0.0, sVar)` (`156.25` inflation).
* Keep `UnicycleOdometryEkf.ZUPT_SPEED_VARIANCE = 2.0` intact for live tests; pass a `stationaryMask: BooleanArray? = null` (default `null` via `@JvmOverloads`) into `smoothSnapshots(snapshots, refLat, refLon, stationaryMask)` and decouple velocity-position coupling (`transJac[0..1][2] = 0.0`, `cov[0..1][2] = 0.0`) across stationary transitions while clamping `smoothedStates[k][2] = 0.0` for `stationaryMask[k] == true` (leaving moving acceleration/deceleration and `wheelSpeedMps = 10.0` untouched).

### Component 4: `ExtendedRtsAltitudeSlopeSmoother.kt` (`2.0 m` Elevation Hysteresis)
* Add `const val ASCENT_DESCENT_HYSTERESIS_METERS = 2.0` (`REQ-FIL-020.2` parity).
* In `smooth()`, track `anchorAlt` (initialized on first non-standstill sample) and increment `cumulativeAscent` / `cumulativeDescent` only when `!isStandstill && abs(alt - anchorAlt) >= ASCENT_DESCENT_HYSTERESIS_METERS`, updating `anchorAlt = alt` on threshold crossing while preserving `previousAlt` for `vertSpeedMps` and `0.0 m` standstill output.

### Component 5: Unit & Replay Tests (`REQ-PRO-049.1` Zero `src/main` Reads)
* `ErtsPostWorkoutRecomputerTest.kt`: update collinear `PolyUtil.simplify(..., 10.0)` assertions (`2` endpoints instead of `30`/`25` in `TST-FIL-017.3`, `TST-FIL-019.3`); add `TST-FIL-027.1` (`TIME_TOTAL` vs `dt=0/2s` + fallback + pause), `TST-FIL-027.4` (`DISTANCE_m_LAP` + `Laps.db` + `< 20` & `>= 60` streams), and `TST-FIL-027.5` (9-locale barometer `filterSlewOnly()`).
* `SpeedAndLocationDevice_ATTFusedTest.kt`: add `TST-FIL-027.2` (1st-sample `0.1 rad/s`, 50-Hz `1.2–1.5 Hz` cyclic pedaling cancellation + steady turn, `resetOriginAndEkf()`/`shutDown()` reset).
* `ExtendedRtsOdometrySmootherTest.kt`: add `TST-FIL-027.3` (140-s indoor stop `acc=50m` pinned at `0.0 m/s` / `+0.0 m` with moving pre-stop speeds intact, preserving `wheelSpeedMps = 10.0` canopy test).
* `ExtendedRtsAltitudeSlopeSmootherTest.kt`: add `TST-FIL-027.5` (`2.0 m` hysteresis ripple rejection vs genuine climb).

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Gate 3 Verification (`Rule 4.1`)**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` (`GATE_PASSED`).
2. **Step 1 (`SpeedAndLocationDevice_ATTFused.kt`)**: Implement rolling `1.0 s` time-weighted yaw-rate window, sample-1 immediate emit, and `resetOriginAndEkf()` / `shutDown()` reset.
3. **Step 2 (`ExtendedRtsOdometrySmoother.kt`, `ExtendedRtsAltitudeSlopeSmoother.kt`)**: Implement stationary epoch ZUPT + RTS velocity/position decoupling + `0.0 m/s` clamp, and `2.0 m` anchor hysteresis.
4. **Step 3 (`ErtsPostWorkoutRecomputer.kt`)**: Implement `TIME_TOTAL` + monotonic fallback, 9-locale barometer `filterSlewOnly()`, `DISTANCE_m_LAP` sync, `PolyUtil.simplify(..., 10.0)`, and `ENCODING_STEP_SIZE` stream subsampling.
5. **Step 4 (Test Suites)**: Update `TST-FIL-017.3` / `TST-FIL-019.3` collinear polyline assertions and add `TST-FIL-027.1`..`.5` unit/replay tests (`0` new `src/main` reads).
6. **Step 5 (Single Batched Verification)**: Execute the batched Gradle & governance checks below.

## 6. Verification & Rollback Plan
* **Verification (Single Batched `Rule 7.5` Gradle Run under POSIX `flock`)**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.database.ErtsPostWorkoutRecomputerTest" \
    --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_ATTFusedTest" \
    --tests "com.atrainingtracker.banalservice.odometry.ExtendedRtsOdometrySmootherTest" \
    --tests "com.atrainingtracker.banalservice.odometry.ExtendedRtsAltitudeSlopeSmootherTest" \
    --tests "com.atrainingtracker.banalservice.odometry.UnicycleOdometryEkfTest" \
    --tests "com.atrainingtracker.banalservice.devices.BarometricAltitudeFilterTest" \
    --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" \
    --max-workers=1 --rerun-tasks
  ```
  Plus `python3 tools/verify_requirement_governance.py --repo-root .` (exit `0`).
* **Rollback**: All changes are isolated on `feature/ATT-8880`; drop/revert branch without affecting `sprint/2026-11-18_01_Früh`.
