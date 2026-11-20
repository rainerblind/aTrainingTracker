# Stage 3: Implementation Plan - ATT-9063: Fix ExtendedRtsAltitudeSlopeSmoother -91% DEM Grid-Step Slope Blowup and +20.5 m Initial Backward-RTS Altitude Overshoot

**Ticket**: `ATT-9063` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9063` | **Author**: AI Agent 1 (`Pro` / Neon) | **Date**: 2026-11-19  
**Requirement Mapping**: `REQ-FIL-033` (`REQ-FIL-033.1`–`.3`) | **Test Mapping**: `TST-FIL-033` (`TST-FIL-033.1`–`.3`)

---

## 1. Architecture, Threading & LoC Budget (`SWE.2` / `SWE.3`)
* **Layer Boundaries**: Pure JVM altitude/slope RTS smoother (`ExtendedRtsAltitudeSlopeSmoother.kt`, `banalservice/odometry/`) and causal barometric filter (`BarometricAltitudeFilter.kt`, `banalservice/devices/`) invoked on `Dispatchers.IO` by `ErtsPostWorkoutRecomputer.kt` (`trainingtracker/database/`). Zero UI/Compose changes (`Localization Parity: N/A - no UI strings`), zero SQLite schema migrations, zero `SWE.3` state-machine changes (`TrackerService`, `BANALService`, `MyBTLEDevice` untouched).
* **Touched Production Files (Strictly 3 Non-Monoliths `< 1,500` LoC)**:
  * `ExtendedRtsAltitudeSlopeSmoother.kt`: `453 -> ~540 / 1,500` LoC (`~960` headroom)
  * `BarometricAltitudeFilter.kt`: `205 -> ~230 / 1,500` LoC (`~1,270` headroom)
  * `ErtsPostWorkoutRecomputer.kt`: `1,051 -> ~1,065 / 1,500` LoC (`~435` headroom; preserves existing `NumericalEncodingUtils` import in `BASELINE_ALLOWED_DATABASE_UPWARD_UI_IMPORTS`)
* **Protected Monoliths (`0` LoC Edits)**: `TrackerService.java` (`3,559`), `WorkoutSummariesDatabaseManager.java` (`3,148`), `WorkoutSamplesDatabaseManager.java` (`1,533`), `WorkoutRepository.kt` (`5,117`), `AltitudeAndSlopeDevice_ATTFused.kt` (`513`, untouched), `KinematicAltitudeSlopeEkf.kt` (`503`, untouched), `strings*.xml`.

---

## 2. System Invariants & Preserved Contracts
1. **Raw Column & Persistence Immutability**: Device-attributed columns (`" ("`) remain 100% byte-for-byte immutable (`REQ-FIL-017.3`); `ASCENT_DESCENT_HYSTERESIS_METERS = 2.0` (`TST-FIL-027.5`); `VERTICAL_SPEED` integer `[-3000, +3000] m/h` (`REQ-FIL-021`).
2. **Live Filter & Contract Preservation**: `BarometricAltitudeFilter.maxSlewRateMps` remains the unmutated base profile slew rate (`0.7 m/s` in `BIKE_FRONT_POCKET`, preserving `AltitudeAndSlopeDevice_ATTFusedTest:850` & `BarometricAltitudeFilterTest:163`), and 2-arg `filterSlewOnly(alt, ts)` defaults `speedMps = 0.0` via `@JvmOverloads`.
3. **Existing ERTS Regression Parity**: Delayed DEM anchor bias convergence (`TST-FIL-017.2`), standstill coffee-stop clamping (`0.0 m` ascent/descent/slope), `2.0 m` ripple hysteresis (`TST-FIL-027.5`), carriage baro variance scaling (`TST-FIL-028.4`), and `ErtsPostWorkoutRecomputerTest.testVerticalSpeedRecomputation` (`ALTITUDE` + `DEM` without baro column) remain 100% intact.
4. **Governance & Hygiene**: `0` DB schema bumps, `0` new `src/main` test reads (`REQ-PRO-049.1`), strict Code Lock (`Rule 4`, zero `app/` edits in Stage 3), Parent Human Gate (`Rule 3.1`).

---

## 3. Atomic Stage 4 Implementation Sequence

### Step 0: Gate 3 Code Lock Verification (`Rule 4`)
* Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and confirm `GATE_PASSED` (exit `0`) before modifying any file under `app/src/`.

### Step 1: Hardened `ExtendedRtsAltitudeSlopeSmoother` (`REQ-FIL-033.1`, `REQ-FIL-033.3` -> `TST-FIL-033.1`, `TST-FIL-033.3`)
* **Target**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsAltitudeSlopeSmoother.kt`
* **Design**:
  1. **Constants & Helpers**: Add `MAX_PHYSICAL_SLOPE = 0.35`, `LOW_SPEED_SLOPE_THRESHOLD_MPS = 1.2`, `DEFAULT_INITIAL_SLOPE_VAR = 0.0025`, `MAX_SLOPE_VARIANCE = 0.015`, `STANDSTILL_SLOPE_GRACE_SEC = 2.0`, `STANDSTILL_SLOPE_DECAY_TAU_SEC = 2.5`; pure helpers `lowSpeedSlopeGainFactor(speedMps)` (`0.0` `< 0.3`, quadratic `((v - 0.3)/0.9)^2` over `0.3..1.2`, `1.0` `>= 1.2`), `clampPhysicalSlope(rawSlope)` (`coerceIn(-0.35, 0.35)`), and `josephCovarianceUpdate3x3(p, kVec, hVec, rVar)` computing exact symmetric SPD $P^+ = (I - K H) P (I - K H)^T + K R K^T$ (with diagonal floors `p[0][0] >= 1e-6`, `p[1][1] >= 1e-8`, `p[2][2] >= 1e-6` + `symmetrize3x3`).
  2. **Single-Sample & Sequential Finite-Scan Initialization**:
     * For `n == 1`: `val alt = s0.referenceAltitudeM?.takeIf { it.isFinite() } ?: s0.rawAltitudeM.takeIf { it.isFinite() } ?: 0.0`.
     * For `n >= 2`: compute `hasBaroData = samples.any { it.rawAltitudeM.isFinite() }`, `hasReference = samples.any { it.referenceAltitudeM?.isFinite() == true }`, and `hasInitialRef = (samples[0].referenceAltitudeM?.isFinite() == true)` strictly at `k = 0`. Scan sequentially `for (s in samples)` taking `s.referenceAltitudeM?.takeIf { it.isFinite() } ?: s.rawAltitudeM.takeIf { it.isFinite() }` on the earliest sample having either finite value (fallback `0.0`), preserving delayed-anchor `TST-FIL-017.2` `t = 0` initialization (`p[0][0] = 400.0`). Set `p[1][1] = DEFAULT_INITIAL_SLOPE_VAR (0.0025)`.
  3. **Exact Linear Forward Predict (Moving & Contractive OU Standstill)**:
     * Track `standstillDurationSec = 0.0`. Compute `gainScale = lowSpeedSlopeGainFactor(sample.smoothedSpeedMps)`.
     * **Standstill (`k > 0 && isStandstill`)**: `standstillDurationSec += dtSec`; default `slopeDecay = 1.0`. When `standstillDurationSec >= STANDSTILL_SLOPE_GRACE_SEC`, compute `slopeDecay = exp(-dtSec / STANDSTILL_SLOPE_DECAY_TAU_SEC)`, update `x[1] *= slopeDecay` (**no** hard zero-snap so $x_{k|k-1} = F_k x_{k-1|k-1}$ matches `jacobians[k]` identically), `p[0][1] *= slopeDecay; p[1][0] = p[0][1]`, `p[1][2] *= slopeDecay; p[2][1] = p[1][2]`, and `p[1][1] = slopeDecay * slopeDecay * p[1][1] + (1.0 - slopeDecay * slopeDecay) * max(DEFAULT_INITIAL_SLOPE_VAR, p[1][1])` (strictly contractive $0 < C_k[1][1] \le \alpha < 1$). Increment `p[2][2] += DEFAULT_Q_BIAS * dtSec`. Set `fMat[1][1] = slopeDecay`.
     * **Moving (`k > 0 && !isStandstill`)**: `standstillDurationSec = 0.0`; `x[0] += x[1] * deltaDist`; compute `p00New`, `p01New`, `p02New` from **pre-update** `p[1][1]` BEFORE capping `p11New = min(MAX_SLOPE_VARIANCE, p[1][1] + DEFAULT_QG * deltaDist * gainScale)`; `p[2][2] += DEFAULT_Q_BIAS * dtSec`. Set `fMat[0][1] = deltaDist`.
  4. **Step 2 (Barometer) & Step 3 (Reference Anchor) with Joseph SPD Form**:
     * **Step 2 (`sample.rawAltitudeM.isFinite()`)**: If `isStandstill`, update `x[2]` with `kVec = [0, 0, kBias]`, `hVec = [0, 0, 1]` via `josephCovarianceUpdate3x3`; if moving, scale `k1 = ((p[1][0] + p[1][2]) / s) * gainScale`, clamp `x[1] = clampPhysicalSlope(x[1] + k1 * yH)`, and update `p` with `hVec = [1, 0, 1]` via `josephCovarianceUpdate3x3`.
     * **Step 3 (`refAlt != null && refAlt.isFinite()`)**: Set `k1 = if (hasBaroData || isStandstill) 0.0 else (p[1][0] / sRef) * gainScale`, clamp `x[1] = clampPhysicalSlope(x[1] + k1 * yRefH)`, and update `p` with `hVec = [1, 0, 0]` via `josephCovarianceUpdate3x3`.
  5. **Backward RTS Clamping**: After `smoothedStates[k][i] = posteriors[k][i] + cDeltaX[i]`, clamp `smoothedStates[k][1] = clampPhysicalSlope(smoothedStates[k][1])` (and clamp `smoothedStates[lastIdx][1]`).

### Step 2: NaN-Safe Speed-Coupled Slew Limit (`REQ-FIL-033.2` -> `TST-FIL-033.2`)
* **Target**: `app/src/main/java/com/atrainingtracker/banalservice/devices/BarometricAltitudeFilter.kt`
* **Design**:
  * Add `SPEED_COUPLED_SLOPE_FACTOR = 0.25`, `MAX_SPEED_COUPLED_REF_MPS = 35.0`, and `@JvmStatic fun computeSpeedCoupledSlewRateMps(baseMaxSlewRateMps: Double, speedMps: Double): Double`: sanitize `safeBase = if (baseMaxSlewRateMps.isFinite() && baseMaxSlewRateMps > 0.0) baseMaxSlewRateMps else DEFAULT_MAX_SLEW_RATE_MPS`; explicitly `if (!speedMps.isFinite() || speedMps <= 0.0) return safeBase`; return `max(safeBase, SPEED_COUPLED_SLOPE_FACTOR * min(speedMps, MAX_SPEED_COUPLED_REF_MPS))`.
  * Update `filterSlewOnly` with `@JvmOverloads fun filterSlewOnly(rawAltitude: Double, timestampMs: Long, speedMps: Double = 0.0): Double`, computing `effectiveMaxSlew = computeSpeedCoupledSlewRateMps(maxSlewRateMps, speedMps)` for `maxDelta = effectiveMaxSlew * dtSec` without mutating `maxSlewRateMps`.

### Step 3: Recomputer Speed Wiring & Leading-Null / Barometer-Free Safety (`REQ-FIL-033.2`, `REQ-FIL-033.3` -> `TST-FIL-033.2`, `TST-FIL-033.3`)
* **Target**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputer.kt`
* **Design**:
  * In `recomputeWorkout()` (`:183`): widen `val hasAltitudeData = rawRows.any { it.rawAltitude != null || it.referenceAltitude != null }`.
  * In `resolveCarriageModesAndFilterBaro()` (`:400-405`): compute `val epochSpeedMps = row.wheelSpeedMps?.takeIf { it.isFinite() && it >= 0.0 } ?: row.speedMps?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0` and call `baroFilter.filterSlewOnly(row.rawBaroAltitude, row.timestampMs, epochSpeedMps)`.
  * In `runAltitudeSmoothing()` (`:504`): pass `rawAltitudeM = row.rawAltitude ?: Double.NaN` (eliminating fake `0.0 m` sea-level spikes on null rows).

### Step 4: Targeted Unit, Robolectric & Contract Verification (`TST-FIL-033.1`–`.3`)
* Add focused unit/Robolectric tests in:
  * `ExtendedRtsAltitudeSlopeSmootherTest.kt`: (a) `Zum Bäcker #58` closed-loop `-3 m` DEM step at `t = 4 s` during slow startup (`~5 m`) + `20 s` standstill + flat/hilly closed loop (`|smoothed[0].altitudeM - 438.0| <= 2.0`, `|slopePercent| < 10.0%` on flat start & `<= 35.0%` everywhere, `|ascentM - descentM| <= 3.0 m`); (b) steep-mountain transient `-10 m` DEM grid-boundary step (`438 -> 428 -> 438 m` around startup + `20 s` stop) asserting bounded start altitude (no `+20 m` backward-RTS overshoot) & `|slopePercent| <= 35.0%`; (c) barometer-free `rawAltitudeM = Double.NaN` + DEM/GPS climb/descent learning bounded slope (`<= 35%`, zero NaN), plus leading-null rows & `n == 1` non-finite guard.
  * `BarometricAltitudeFilterTest.kt`: `computeSpeedCoupledSlewRateMps` matrix (`0.0`, `2.0`, `18.0`, `50.0 -> 35.0` cap, `-5.0`, `NaN`, `+Inf`, non-finite base) + `BIKE_FRONT_POCKET` (`0.7 m/s`, window `9`) unclipped `-1.8 m/s` alpine descent at `18 m/s` vs. rejected slow-speed `±8 m` pocket spike + unchanged `maxSlewRateMps == 0.7`.
  * `ErtsPostWorkoutRecomputerTest.kt`: end-to-end `BIKE_FRONT_POCKET` `18 m/s` `-10%` alpine descent + `10%` climb, NaN wheel-speed sanitization fallback, and barometer-free DEM-only / leading-null `ALTITUDE` workout.
* Execute `Rule 7.5` incremental `--rerun` verification (NO `--rerun-tasks`, NO `-PstampGitHash=true`):
```bash
python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun \
  --tests "*ExtendedRtsAltitudeSlopeSmootherTest" \
  --tests "*BarometricAltitudeFilterTest" \
  --tests "*ErtsPostWorkoutRecomputerTest" \
  --tests "*AltitudeAndSlopeDevice_ATTFusedTest" \
  --tests "*KinematicAltitudeSlopeEkfTest" \
  --tests "*TrackerServiceErtsFinalizationTest" \
  --tests "*ArchitectureConformanceContractTest" \
  --tests "*SystemLiveTrackingRequirementsContractTest" \
  --tests "*DeadCodeEliminationContractTest" \
  --tests "*SourceTextReadingAuditContractTest"
```

---

## 4. Verification & Rollback Plan
* **Verification**: `100%` pass across all 10 targeted/contract suites above + `python3 tools/verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-19_03_Nacht --check-verified-evidence` (`exit 0`).
* **Rollback**: Isolated cleanly on `feature/ATT-9063`; `sprint/2026-11-19_03_Nacht` remains untouched until Stage 6 `PASS` and `--merge-ticket`.
