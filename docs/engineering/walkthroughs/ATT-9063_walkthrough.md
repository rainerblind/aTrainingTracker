# Stage 5 Walkthrough: ATT-9063 - Fix ExtendedRtsAltitudeSlopeSmoother -91% DEM Grid-Step Slope Blowup & +20.5 m Initial Backward-RTS Altitude Overshoot

**Ticket**: `ATT-9063` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9063` (base `c33bef19`, code SHA `0f4dd34b`) | **Author**: AI Agent 1 (`Pro` / Neon) | **Date**: 2026-11-19  
**Verified Requirements**: `REQ-FIL-033` (`REQ-FIL-033.1`–`.3`, `[Verified]`) | **Verified Tests**: `TST-FIL-033` (`TST-FIL-033.1`–`.3`, `[Verified]`)

---

## 1. Summary of Changes (Strictly 3 Non-Monolith Files `< 1,500` LoC)
1. **DEM Grid-Step Slope Decoupling, Contractive OU Standstill Decay & 3×3 Joseph SPD Updates (`REQ-FIL-033.1`, `ExtendedRtsAltitudeSlopeSmoother.kt` `547 / 1,500` LoC)**:
   - Added `MAX_PHYSICAL_SLOPE = 0.35` (`±35%`) forward + backward RTS clamping (`clampPhysicalSlope`), NaN-guarded `lowSpeedSlopeGainFactor(0.3..1.2 m/s)`, realistic flat-start `DEFAULT_INITIAL_SLOPE_VAR = 0.0025` (`5%` SD), and pre-update `p[1][1]` moving covariance propagation before capping `p11New` at `MAX_SLOPE_VARIANCE = 0.015`.
   - Added exact continuous linear stationary **Ornstein-Uhlenbeck** standstill slope decay (`grace = 2.0 s`, `tau = 2.5 s`, `fMat[1][1] = alpha`, `Q11 = (1 - alpha^2) * max(0.0025, p[1][1])`, no hard zero-snap) so `priors[k]` matches `jacobians[k]` identically and backward RTS gain stays strictly contractive ($0 < C_k[1][1] \le \alpha < 1$).
   - Decoupled integer DEM reference updates from slope (`k1 = 0.0` when `hasBaroData`), and replaced simplified `P - K H P` updates with exact 3×3 **Joseph form** $P^+ = (I - K H) P^- (I - K H)^T + K R K^T$ across Step 2 (moving & standstill) and Step 3, guaranteeing symmetric SPD `P_{k|k}` for `invert3x3()`.
2. **Speed-Coupled NaN-Safe Barometric Slew Limiting (`REQ-FIL-033.2`, `BarometricAltitudeFilter.kt` `234 / 1,500` LoC)**:
   - Added `computeSpeedCoupledSlewRateMps(base, speed)` (`max(safeBase, 0.25 * min(speed, 35.0))`, explicitly guarding `!speedMps.isFinite() || speedMps <= 0.0` against Kotlin `Double.NaN.coerceIn`) and `@JvmOverloads filterSlewOnly(..., speedMps = 0.0)` preserving base `maxSlewRateMps == 0.7`.
   - Wired finite non-negative `epochSpeedMps` (`wheelSpeedMps ?: speedMps ?: 0.0`) in `ErtsPostWorkoutRecomputer.resolveCarriageModesAndFilterBaro()` so `18 m/s` `-10%` (`-1.8 m/s`) alpine descents in `BIKE_FRONT_POCKET` pass unclipped while slow pocket spikes stay rejected.
3. **Barometer-Free Fallback & Leading-Null / `n == 1` Safety (`REQ-FIL-033.3`, `ExtendedRtsAltitudeSlopeSmoother.kt` & `ErtsPostWorkoutRecomputer.kt` `1,054 / 1,500` LoC)**:
   - Enabled damped `±35%`-clamped Step 3 slope learning when `!hasBaroData` (`rawAltitudeM = Double.NaN`), sequential finite-scan `initialAlt` with `k = 0` `hasInitialRef` (preserving delayed-anchor `TST-FIL-017.2`), `n == 1` finite fallback (`?: 0.0`), `row.rawAltitude ?: Double.NaN` in `runAltitudeSmoothing()`, and `referenceAltitude`-inclusive `hasAltitudeData`.
4. **Protected Monoliths & Localization (`0` LoC Edits)**: `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutRepository.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`, `KinematicAltitudeSlopeEkf.kt`, and `values*/strings_*.xml` (`Localization Parity: N/A - no UI strings`) remain 100% untouched.

---

## 2. Verification Evidence (Gate 4 Reuse per `Rule 7.5` — `0` `app/` Diff Since `0f4dd34b`)
- **Tier 1 Targeted Incremental `--rerun` Suite Results (`ATT-9196` Gate 4 Independently Verified — `109/109 PASS`, `0` Failures, `0` Errors, `0` Skipped, `0` Warnings in Touched Files)**:

| Test Suite | Result | Verified Scope / Key Test Methods |
| :--- | :---: | :--- |
| `ExtendedRtsAltitudeSlopeSmootherTest` | `8/8 PASS` | `TST-FIL-033.1`/`.3`: `testZumBaeckerStartupDemGridStepAndStandstill_noSlopeBlowupOrInitialOvershoot_tstFil033_1`, `testSteepMountainTransientDemStepAndStandstill_staysBoundedWithoutOvershoot_tstFil033_1`, `testHelperGuards_lowSpeedGainClampAndJosephSpd_tstFil033_1`, `testBarometerFreeDemOnlyLeadingNullAndSingleSample_finiteAndPhysicallyBounded_tstFil033_3` + `TST-FIL-017.2`/`.027.5`/`.028.4` |
| `BarometricAltitudeFilterTest` | `7/7 PASS` | `TST-FIL-033.2`: `testComputeSpeedCoupledSlewRateMps_scalesWithSpeedAndGuardsNonFinite_tstFil033_2`, `testSpeedCoupledFilterSlewOnly_passesAlpineDescentUnclippedWhileRejectingSlowPocketSpikes_tstFil033_2` + `TST-TRK-057`/`TST-FIL-028.3` |
| `ErtsPostWorkoutRecomputerTest` | `19/19 PASS` | `TST-FIL-033.2`/`.3`: `testAlpineClimbAndFastDescentInFrontPocket_speedCoupledSlewUnclippedAndNaNWheelSpeedSafe_tstFil033_2`, `testBarometerFreeDemOnlyAndLeadingNullAltitudeWorkout_smoothAndFinite_tstFil033_3` + all ERTS regressions |
| `AltitudeAndSlopeDevice_ATTFusedTest` | `21/21 PASS` | Live `ATTFused` & `baroFilter.maxSlewRateMps == 0.7` contract parity |
| `KinematicAltitudeSlopeEkfTest` | `14/14 PASS` | Live EKF regression parity |
| `ExtendedRtsOdometrySmootherTest` | `12/12 PASS` | Adjacent ERTS odometry & altitude smoother integration parity |
| `TrackerServiceErtsFinalizationTest` | `2/2 PASS` | Post-workout ERTS hook integration |
| `ArchitectureConformanceContractTest` | `11/11 PASS` | Monolith LoC ceilings (`0` protected edits; `547`/`234`/`1,054 < 1,500` LoC) & layer isolation |
| `DeadCodeEliminationContractTest` | `5/5 PASS` | Zero dead code / unused helpers |
| `SourceTextReadingAuditContractTest` | `6/6 PASS` | `0` `src/main` reads in tests (`REQ-PRO-049.1`) |
| `SystemLiveTrackingRequirementsContractTest` | `4/4 PASS` | `SYSREQ-TRK-003` / `SYSREQ-TRK-004` <-> `REQ-FIL-033` bidirectional traceability |

- **Governance & Code-Freeze Check**:
  - `verify_requirement_governance.py --repo-root . --sync-indexes` & `--base-ref refs/heads/sprint/2026-11-19_03_Nacht --check-verified-evidence`: **`exit 0`** (`100%` `[Verified]` parity).
  - `git diff 0f4dd34b -- app/`: **empty (`0` bytes changed since Gate 4 `PASS`)**.

---

## 3. Handoff Status
- `feature/ATT-9063` preserved intact atop `sprint/2026-11-19_03_Nacht` (`c33bef19`, NO premature merge), ready for Gate 5 review and Stage 6 Cold Final Review (`Final Review (Agent)`).
