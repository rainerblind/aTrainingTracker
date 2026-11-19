# Stage 3: Implementation Plan - ATT-9064: Fix TIME_TOTAL Clock-Beat Stretching, speedAverage_mps Desync & Stationary Indoor ERTS Drift

**Ticket**: `ATT-9064` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-9064` | **Author**: AI Agent 1 (`Pro` / Neon) | **Date**: 2026-11-19  
**Requirement Mapping**: `REQ-FIL-032` (`REQ-FIL-032.1`–`.3`) | **Test Mapping**: `TST-FIL-032` (`TST-FIL-032.1`–`.3`)

---

## 1. Architecture, Threading & LoC Budget (`SWE.2` / `SWE.3`)
* **Layer Boundaries**: Pure JVM odometry math (`UnicycleOdometryEkf.kt`, `ExtendedRtsOdometrySmoother.kt` under `banalservice/odometry/`) invoked on `Dispatchers.IO` by `ErtsPostWorkoutRecomputer.kt` (`trainingtracker/database/`). Zero UI/Compose changes (`Localization Parity: N/A - no UI strings`), zero SQLite schema migrations, zero `SWE.3` state-machine changes (`TrackerService`, `BANALService`, `MyBTLEDevice` untouched).
* **Touched Production Files (Strictly 3 Non-Monoliths `< 1,500` LoC)**:
  * `ErtsPostWorkoutRecomputer.kt`: `956 -> ~1,005 / 1,500` LoC (`~495` headroom)
  * `ExtendedRtsOdometrySmoother.kt`: `737 -> ~750 / 1,500` LoC (`~750` headroom)
  * `UnicycleOdometryEkf.kt`: `799 -> ~820 / 1,500` LoC (`~680` headroom)
* **Protected Monoliths (`0` LoC Edits)**: `TrackerService.java` (`3,538 / 3,559`), `SpeedAndLocationDevice_ATTFused.kt` (`1,441 / 1,500`), `WorkoutSummariesDatabaseManager.java` (`3,148 / 3,148` — reuses existing `getInt(workoutId, WorkoutSummaries.TIME_ACTIVE_s)`), `WorkoutSamplesDatabaseManager.java` (`1,533 / 1,533`), `WorkoutRepository.kt` (`<= 5,117`), `LapsDatabaseManager.java` (`612 / 1,500`), `strings*.xml`.

---

## 2. System Invariants & Preserved Contracts
1. **Raw Column & Default Flag Immutability**: Device-attributed columns (`" ("`) in `WorkoutSamples.db` stay 100% byte-for-byte untouched (`REQ-FIL-017.3`); `KEY_POST_WORKOUT_ERTS_SMOOTHING = false` default preserved.
2. **Live EKF Isolation**: `UnicycleOdometryEkf.predict()` keeps `@JvmOverloads isStationary: Boolean = false` default so `SpeedAndLocationDevice_ATTFused.kt:794`, `MIN_DOPPLER_SPEED_MPS = 3.0`, and `ZUPT_SPEED_VARIANCE = 2.0` remain unchanged.
3. **Pause/Flush-Stall & Canopy Compatibility**: Preserves `TST-FIL-027.1` (`6 s` batch-flush stall immunity + `30 s`/`25 s` double pauses), `TST-FIL-027.3` (pre-/post-stop speed `> 4.0 m/s` & GNSS re-acquisition), and canopy Doppler-dropout (`wheelSpeedMps = 10.0`).
4. **Governance & Hygiene**: `0` DB schema bumps, `0` new `src/main` test reads (`REQ-PRO-049.1`), Code Lock (`Rule 4`) before `app/src/` edits, Parent Human Gate (`Rule 3.1`).

---

## 3. Atomic Stage 4 Implementation Sequence

### Step 0: Gate 3 Code Lock Verification (`Rule 4`)
* Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and confirm `GATE_PASSED` (exit `0`) before modifying any file under `app/src/`.

### Step 1: Beat-Immune 1-Hz Timestamp Reconstruction (`REQ-FIL-032.1` -> `TST-FIL-032.1`)
* **Target**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputer.kt` (`readWorkoutSamples` + pure `internal fun reconstructTimestamps(...)` / helper).
* **Design**:
  * Row `0`: `ts = parsedTs ?: System.currentTimeMillis()`, anchor `baseEpochMs = ts`, `firstTimeTotalSec = timeTotalSec` (if non-null).
  * Row `i > 0`: baseline `oneHzTs = prevTs + 1000L`.
  * Detect genuine `> 5_000L ms` pause via:
    1. Wall-clock pause: `parsedTs != null && (parsedTs - oneHzTs) > PAUSE_GAP_THRESHOLD_MS (5_000L)` -> `ts = parsedTs`, re-anchor `baseEpochMs = ts` and `firstTimeTotalSec = timeTotalSec` (even when landing on `timeTotalSec == prevTimeTotalSec` or leading/all-`null` `TIME_TOTAL`!).
    2. Single-step `TIME_TOTAL` pause: `timeTotalSec != null && prevTimeTotalSec != null && (timeTotalSec - prevTimeTotalSec) > 5L` -> `ts = prevTs + (timeTotalSec - prevTimeTotalSec) * 1000L`, re-anchor `baseEpochMs = ts` and `firstTimeTotalSec = timeTotalSec`.
    3. Otherwise (normal 1-Hz active tick, `{0, 2}` clock-beat pairs in any order `0 -> 2` / `2 -> 0`, or `<= 5 s` / `6 s` SQLite batch-flush catch-up where `parsedTs - oneHzTs <= 5_000L`): `ts = oneHzTs` (**never** ratcheting `baseEpochMs` forward!). Initialize `baseEpochMs = ts; firstTimeTotalSec = timeTotalSec` on first non-null `timeTotalSec` after leading `null`s.

### Step 2: Authoritative `speedAverage_mps` & `ExtremaType.AVG(SPEED_mps)` Parity (`REQ-FIL-032.2` -> `TST-FIL-032.2`)
* **Target**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputer.kt` (`updateWorkoutSummaries`, `updateExtrema`, plus pure `internal fun computeAuthoritativeAverageSpeedMps(totalDistanceM, timeActiveSec, smoothedOdometry)`).
* **Design**:
  * Query `val timeActiveSec = summariesDbManager.getInt(workoutId, WorkoutSummaries.TIME_ACTIVE_s)` (no new DB manager methods).
  * Precedence: (a) if `timeActiveSec != null && timeActiveSec > 0` -> `totalDistance / timeActiveSec`; (b) else sum matched `activeDist` and `activeDt` strictly across consecutive steps `k = 1..last` with `dtSec in (0.0, MAX_ACTIVE_SAMPLE_GAP_SEC (5.0)]` and return `activeDist / activeDt` when `activeDt > 0.0` (avoiding pause-gap `deltaDist` inflation on `TST-FIL-027.1`!); (c) else finite non-negative mean of finite `speedMps` (`0.0` if empty/non-finite).
  * Write the single resolved `avgSpeed` to BOTH `WorkoutSummaries.SPEED_AVERAGE_mps` and `updateExtrema(..., SensorType.SPEED_mps, ExtremaType.AVG, avgSpeed, null)`.

### Step 3: Stationary `[E, N]` Covariance Freeze & Backward RTS Coordinate Pinning (`REQ-FIL-032.3` -> `TST-FIL-032.3`)
* **Target 1**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkf.kt` (`predict`).
  * Add `@JvmOverloads` parameter `isStationary: Boolean = false` to `predict(dtSec, gyroYawRateRadPerSec, timestampMs, gyroNoiseScale, isStationary)`.
  * When `isStationary == true`: skip `x[0]`/`x[1]` displacement integration, keep `transitionJacobian[0][j] = (j == 0 ? 1 : 0)` & `[1][j] = (j == 1 ? 1 : 0)` (zeroing `F[0..1, 2..4]` so `F * P * F^T` never injects `2.0 m^2/s` from `ZUPT_SPEED_VARIANCE`), and skip `Q_POS * dtSec` on `p[0][0]` & `p[1][1]` while preserving `x[3]`/`x[4]`, `F[3][4] = -dtSec`, `Q_ACC`/`Q_GYRO`/`Q_BIAS`, and SPD invertibility of `P_{k+1|k}`.
* **Target 2**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsOdometrySmoother.kt` (`smoothSamples`, `smoothSnapshots`).
  * Pass `isStationary = isStationary` to `ekf.predict(...)` at `smoothSamples():372`.
  * In `smoothSnapshots()` backward loop (`k = n - 2 downTo 0`), when `currStationary && nextStationary`, pin `smoothedStates[k][0] = smoothedStates[k + 1][0]` and `smoothedStates[k][1] = smoothedStates[k + 1][1]`, guaranteeing exact `0.00 m` Haversine coordinate movement across stationary stops while keeping `TST-FIL-027.3` post-outage GNSS re-acquisition intact.

### Step 4: Targeted Unit, Robolectric & Contract Verification (`TST-FIL-032.1`–`.3`)
* Add focused unit/Robolectric tests in `ErtsPostWorkoutRecomputerTest.kt` (`584`-row `78`-pair `0/2` beat non-stretching, `> 5 s` wall pause on `timeTotalSec == prevTimeTotalSec`, `deltaTimeTotal > 5L` pause, `speedAverage_mps` / `Extrema(AVG)` / `Laps.db` `<= 0.02 m/s` parity, `NULL` `TIME_ACTIVE_s` matched `activeDist / activeDt` fallback), `UnicycleOdometryEkfTest.kt` (`predict(isStationary = true)` zero `P_pos` growth over `118 s`, SPD invertibility, default `isStationary = false` unchanged), and `ExtendedRtsOdometrySmootherTest.kt` (`118 s` indoor stop `acc = 48.5 m` with `+35.66 m` departure jump -> `0.00 m` Haversine drift & `0.00 m` `deltaDistanceMeters`, direct `smoothSnapshots` stationary pinning).
* Execute `Rule 7.5` incremental `--rerun` verification (NO `--rerun-tasks`, NO `-PstampGitHash=true`):
```bash
python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun \
  --tests "*ErtsPostWorkoutRecomputerTest" \
  --tests "*ExtendedRtsOdometrySmootherTest" \
  --tests "*UnicycleOdometryEkfTest" \
  --tests "*TrackerServiceErtsFinalizationTest" \
  --tests "*LapsDatabaseManagerTest" \
  --tests "*ArchitectureConformanceContractTest" \
  --tests "*SystemLiveTrackingRequirementsContractTest" \
  --tests "*DeadCodeEliminationContractTest" \
  --tests "*SourceTextReadingAuditContractTest"
```

---

## 4. Verification & Rollback Plan
* **Verification**: `100%` pass across all 9 targeted/contract suites above + `python3 tools/verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-19_02_Spät --check-verified-evidence` (`exit 0`).
* **Rollback**: Isolated cleanly on `feature/ATT-9064`; `sprint/2026-11-19_02_Spät` remains untouched until Stage 6 `PASS` and `--merge-ticket`.
