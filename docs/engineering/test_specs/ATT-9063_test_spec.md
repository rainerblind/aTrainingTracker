# Stage 2: Requirement & Test Specification - ATT-9063: ERTS Altitude/Slope DEM Grid-Step Immunity, Contractive Standstill Decay & Speed-Coupled Barometer Slew

**Ticket**: `ATT-9063` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9063` (base `c33bef19`)  
**Requirement Mapping**: `REQ-FIL-033` (`REQ-FIL-033.1`–`.3`, refining `REQ-FIL-017.2`/`.3`, `REQ-FIL-023.2`/`.3`, `REQ-FIL-027.5`, `REQ-FIL-028.4`, `REQ-TRK-057.1`)  
**Test Spec ID**: `TST-FIL-033` (`TST-FIL-033.1`–`.3`) | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-19

---

## 1. Requirement Specification (`REQ-FIL-033`)

### 1.1 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Implementation**: `ExtendedRtsAltitudeSlopeSmoother.kt`, `BarometricAltitudeFilter.kt`, `ErtsPostWorkoutRecomputer.kt`

### 1.2 Functional & Architectural Requirements
1. **DEM Grid-Step Slope Decoupling, Physical Bounds, Low-Speed Damping & Contractive OU Standstill Decay (`REQ-FIL-033.1`)**:
   - `ExtendedRtsAltitudeSlopeSmoother.smooth()` **SHALL** enforce `MAX_PHYSICAL_SLOPE = 0.35` (`±35%`) clamping on `x[1]` during every forward update AND on `smoothedStates[k][1]` during backward RTS smoothing, initialize flat-start slope variance `p[1][1] = DEFAULT_INITIAL_SLOPE_VAR (0.0025)` (`5%` SD), and cap moving slope variance at `MAX_SLOPE_VARIANCE = 0.015` (computing moving `p00New`/`p01New` from pre-update `p[1][1]` before applying the `p11New` cap so $P^- = F P^+ F^T + Q$ holds with $Q_{11} \ge 0$).
   - It **SHALL** damp slope process noise (`qgVal`) and barometer slope gain (`k1`) via `lowSpeedSlopeGainFactor(speed)` (`0.0` below `0.3 m/s`, quadratic ramp `((v - 0.3)/0.9)^2` over `0.3..1.2 m/s`, `1.0` at `>= 1.2 m/s`).
   - During sustained standstill (`standstillDurationSec >= STANDSTILL_SLOPE_GRACE_SEC (2.0 s)`), it **SHALL** apply an exact linear stationary **Ornstein-Uhlenbeck** transition `alpha = exp(-dtSec / 2.5)` (`x[1] *= alpha`, `fMat[1][1] = alpha`, `p[0][1] *= alpha`, `p[1][2] *= alpha`, `p[1][1] = alpha^2 * p[1][1] + (1 - alpha^2) * max(0.0025, p[1][1])`) with **no** hard zero-snap discontinuity so $x_{k|k-1} = F_k x_{k-1|k-1}$ matches `jacobians[k]` identically and backward RTS gain stays strictly contractive ($0 < C_k[1][1] \le \alpha < 1$).
   - On Step 3 reference (`DEM`/`GPS`) updates, when barometer data exists (`hasBaroData = samples.any { it.rawAltitudeM.isFinite() }`), it **MUST** decouple DEM grid steps from slope (`k1 = 0.0`) so DEM anchors `x[0]` and `x[2]` without injecting `±3–15 m` cliff slopes; all Step 2 (moving and standstill $H=[0,0,1]$) and Step 3 covariance updates **MUST** use exact 3×3 **Joseph form** $P^+ = (I - K H) P^- (I - K H)^T + K R K^T$ to guarantee symmetric SPD `P_{k|k}` for backward RTS `invert3x3()`.
2. **Speed-Coupled Barometric Slew Limiting for Alpine Descent Portability (`REQ-FIL-033.2`)**:
   - `BarometricAltitudeFilter` **SHALL** expose `computeSpeedCoupledSlewRateMps(baseMaxSlewRateMps, speedMps)` returning `max(safeBase, 0.25 * clampedSpeed)`, explicitly guarding `if (!speedMps.isFinite() || speedMps <= 0.0) return safeBase` (preventing Kotlin `Double.NaN.coerceIn(...) -> NaN`), sanitizing non-finite/`<= 0` base rates to `DEFAULT_MAX_SLEW_RATE_MPS (1.2)`, and capping finite speed at `35.0 m/s` (`126 km/h`).
   - `BarometricAltitudeFilter.filterSlewOnly(rawAltitude, timestampMs, speedMps = 0.0)` (`@JvmOverloads`) **SHALL** apply `computeSpeedCoupledSlewRateMps(maxSlewRateMps, speedMps)` while keeping `maxSlewRateMps` unchanged as the base profile rate (`0.7 m/s` in `BIKE_FRONT_POCKET`).
   - `ErtsPostWorkoutRecomputer.resolveCarriageModesAndFilterBaro()` **SHALL** pass sanitized `epochSpeedMps = row.wheelSpeedMps?.takeIf { it.isFinite() && it >= 0.0 } ?: row.speedMps?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0` into `baroFilter.filterSlewOnly(row.rawBaroAltitude, row.timestampMs, epochSpeedMps)`.
3. **Barometer-Free Fallback & NaN/Leading-Null Immunity (`REQ-FIL-033.3`)**:
   - When `!hasBaroData` (barometer-free DEM/GPS-only stream with `rawAltitudeM = Double.NaN`), Step 3 **SHALL** allow low-speed-damped, `±0.35`-clamped slope learning (`k1 = if (isStandstill) 0.0 else (p[1][0] / sRef) * gainScale`) via Joseph covariance update.
   - `ExtendedRtsAltitudeSlopeSmoother.smooth()` **SHALL** scan `for (s in samples)` sequentially taking `s.referenceAltitudeM?.takeIf { it.isFinite() } ?: s.rawAltitudeM.takeIf { it.isFinite() }` on the earliest sample having either finite value (defaulting to `0.0` only if none exist), set `hasInitialRef = (samples[0].referenceAltitudeM?.isFinite() == true)` strictly at `k = 0` (preserving delayed-anchor `t = 0` initialization), and guard single-sample `n == 1` identically so leading-null/NaN inputs never poison outputs.
   - `ErtsPostWorkoutRecomputer` **SHALL** pass `rawAltitudeM = row.rawAltitude ?: Double.NaN` (never `?: 0.0`) in `runAltitudeSmoothing()` and gate smoothing on `hasAltitudeData = rawRows.any { it.rawAltitude != null || it.referenceAltitude != null }`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (`REQ-FIL-033.1`)**: *Given* a `Zum Bäcker #58`-style closed-loop workout starting at `438 m` (`BARO ~ 437.5 m`, `DEM = 438 m`) with a `-3 m` (`438 -> 435 m`) or steep-mountain `-10 m` (`438 -> 428 m`) integer DEM step at `t = 4 s` during slow startup (`~5 m` travel), followed by `20 s` standstill and normal riding at `437–438 m`, *When* `ExtendedRtsAltitudeSlopeSmoother.smooth()` executes, *Then* `smoothed[0].altitudeM` stays within `±2.0 m` of `438.0 m` (never `458 m`), `|slopePercent| <= 35%` everywhere (`< 10%` during flat start), backward RTS does not blow up across the `20 s` stop, and `|ascentM - descentM| <= 3.0 m`.
* **AC-2 (`REQ-FIL-033.2`)**: *Given* (a) an `8–15%` mountain climb and (b) an `18 m/s` (`65 km/h`) alpine descent at `-10%` grade (`-1.8 m/s`) in `BIKE_FRONT_POCKET` (`baroMaxSlewRateMps = 0.7`), plus slow/flat pocket spikes (`±8 m`) and `NaN`/`-5.0`/`+Infinity` speeds, *When* `BarometricAltitudeFilter.filterSlewOnly(..., speedMps)` and `ErtsPostWorkoutRecomputer` + `ExtendedRtsAltitudeSlopeSmoother.smooth()` run, *Then* `-1.8 m/s` passes unclipped with accurate zero-phase altitude/slope/`2.0 m` hysteresis ascent/descent, slow pocket spikes stay rejected, `baroFilter.maxSlewRateMps` remains `0.7`, and non-finite/negative speeds safely fall back to `safeBase` without `NaN`.
* **AC-3 (`REQ-FIL-033.3`)**: *Given* a barometer-free phone workout (DEM/GPS-only with `rawAltitudeM = Double.NaN`, plus leading-null rows and `n = 1` non-finite edge case), *When* `ErtsPostWorkoutRecomputer` and `ExtendedRtsAltitudeSlopeSmoother.smooth()` run, *Then* all outputs are finite (`0` NaN), elevation tracks the climb/descent without overshoot, `|slopePercent| <= 35%`, and delayed-anchor convergence (`TST-FIL-017.2`) remains intact.

### 1.4 System Invariants & Localization
- Raw device-attributed columns remain 100% immutable (`REQ-FIL-017.3`); `ASCENT_DESCENT_HYSTERESIS_METERS = 2.0` (`TST-FIL-027.5`); `VERTICAL_SPEED` integer `[-3000, 3000] m/h` (`REQ-FIL-021`); live `AltitudeAndSlopeDevice_ATTFused` / `KinematicAltitudeSlopeEkf` untouched; `BarometricAltitudeFilter.maxSlewRateMps` & 2-arg `filterSlewOnly` unchanged; `0` DB schema changes; `0` protected monolith edits (`ExtendedRtsAltitudeSlopeSmoother` `< 1,500`, `BarometricAltitudeFilter` `< 1,500`, `ErtsPostWorkoutRecomputer` `< 1,500` LoC); `0` new `src/main` test reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

---

## 2. Test Specification (`TST-FIL-033`)

* **`TST-FIL-033.1` (DEM Grid-Step Immunity, Contractive OU Standstill Decay & Backward RTS Bounds — `ExtendedRtsAltitudeSlopeSmootherTest.kt`)**: Replay `Zum Bäcker #58` closed-loop profile with `-3 m` and `-10 m` DEM steps at `t = 4 s` during slow startup + `20 s` standstill + flat/hilly closed-loop return; assert `|smoothed[0].altitudeM - 438.0| <= 2.0`, `|slopePercent| <= 35.0` everywhere (`< 10.0%` on flat start), `|ascentM - descentM| <= 3.0`, SPD symmetry & no backward blowup across `20 s` stop, plus existing `TST-FIL-017.2` / `TST-FIL-027.5` / `TST-FIL-028.4` pass.
* **`TST-FIL-033.2` (Speed-Coupled Barometer Slew, Alpine Descent & NaN-Safe Speed Sanitization — `BarometricAltitudeFilterTest.kt`, `ErtsPostWorkoutRecomputerTest.kt`, `ExtendedRtsAltitudeSlopeSmootherTest.kt`)**: Verify `computeSpeedCoupledSlewRateMps()` on `0.0`, `2.0`, `18.0`, `50.0` (capped at `35.0`), `-5.0`, `Double.NaN`, `Double.POSITIVE_INFINITY` and non-finite base; verify `18 m/s` `-10%` (`-1.8 m/s`) descent & `10%` climb in `BIKE_FRONT_POCKET` pass unclipped via `filterSlewOnly(..., speedMps)` & `ErtsPostWorkoutRecomputer` end-to-end while slow-speed `±8 m` pocket spikes remain clamped, `baroFilter.maxSlewRateMps == 0.7`, and NaN wheel speed safely falls back to finite `speedMps`/`0.0`.
* **`TST-FIL-033.3` (Barometer-Free Fallback, Sequential Finite-Scan & `n == 1` Guard — `ExtendedRtsAltitudeSlopeSmootherTest.kt`, `ErtsPostWorkoutRecomputerTest.kt`, Contracts)**: Verify `rawAltitudeM = Double.NaN` + DEM/GPS climb/descent learns physically bounded slope (`|slopePercent| <= 35%`, finite ascent/descent, zero NaN); verify leading-null rows (`rawAltitude = null`, `referenceAltitude = null`) and `n = 1` non-finite sample emit finite non-NaN outputs without sea-level `0.0 m` spikes; verify `ArchitectureConformanceContractTest`, `SystemLiveTrackingRequirementsContractTest`, `DeadCodeEliminationContractTest`, and `SourceTextReadingAuditContractTest`.

---

## 3. Traceability Matrix

| Test Case | Scope | Test Suite(s) | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-033.1` | DEM step decoupling, OU standstill decay, `±35%` clamp, Joseph SPD | `ExtendedRtsAltitudeSlopeSmootherTest` | `REQ-FIL-033.1` | Planned |
| `TST-FIL-033.2` | Speed-coupled slew, alpine descent/climb, NaN speed guard | `BarometricAltitudeFilterTest`, `ErtsPostWorkoutRecomputerTest`, `ExtendedRtsAltitudeSlopeSmootherTest` | `REQ-FIL-033.2` | Planned |
| `TST-FIL-033.3` | Barometer-free fallback, leading-null & `n == 1` guard, contracts | `ExtendedRtsAltitudeSlopeSmootherTest`, `ErtsPostWorkoutRecomputerTest`, `ArchitectureConformanceContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest` | `REQ-FIL-033.3` | Planned |
