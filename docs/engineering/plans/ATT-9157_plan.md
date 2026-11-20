# Stage 3 Implementation Plan: ATT-9157 — Calibrate PhoneCarriageClassifier for Front-Hip Running Belt, Easy-Pace Running, and Sport-Context Guardrails

**Ticket**: `ATT-9157` (`3 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9157` (base `e11a8123`, includes `ATT-9062`)  
**Requirements**: `REQ-FIL-035` (`REQ-FIL-035.1`–`.3`) | **Test Spec**: `TST-FIL-035` (`TST-FIL-035.1`–`.3`)  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-20

---

## 1. Architecture & SWE.2 Design Overview
- **Single-File Production Scope (Zero Monolith Touch)**: All production changes live strictly inside `app/src/main/java/com/atrainingtracker/banalservice/odometry/PhoneCarriageClassifier.kt` (`1,062 -> ~1,135 / 1,500` LoC, `~365` LoC headroom).
- **Zero Edits to Callers / Monoliths**: `SpeedAndLocationDevice_ATTFused.kt` (`1,459 / 1,500` LoC), `ImuCadenceDsp.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`, `SmartphoneImuCadenceEstimator.kt`, `MultisportTransitionDetector.kt`, `PhoneCarriageMode.kt`, `ErtsPostWorkoutRecomputer.kt`, and all protected monoliths remain **`0` LoC touched**.
- **Invariants**: `PhoneCarriageMode` enum codes (`-1..7`) and `CarriageFilterProfile` untouched; `ATT-9062` (`REQ-FIL-034`) and `ATT-8890` (`REQ-FIL-028`) regression parity preserved; time-based windows only; `0` DB schema bumps; `0` new `src/main` reads in tests (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

---

## 2. Atomic Stage 4 Implementation Steps

### Step 0: Gate 3 Verification & Code Lock
- Before editing ANY file under `app/`, run:
  `python3 tools/jira_util.py --as agent1 check-gate ATT-9157` (or with the Gate 3 subtask key) and confirm `GATE_PASSED`.
- Verify `git status --short` leaves all user-owned dirty files untouched.

### Step 1: Production Calibration in `PhoneCarriageClassifier.kt` (`REQ-FIL-035.1`–`.3`)
Target: `app/src/main/java/com/atrainingtracker/banalservice/odometry/PhoneCarriageClassifier.kt` (`1,062 -> ~1,135 / 1,500` LoC).

1. **Constants & Data Model (`companion object` `:100-112`)**:
   - Raise `SCORE_MARGIN` from `0.25` to `0.85` (`REQ-FIL-035.3`).
   - Add named calibration constants:
     - `RUN_CYCLING_MASK_WHEEL_MAX_MPS = 0.5`
     - `RUN_CYCLING_MASK_SPEED_MPS = 5.5`
     - `EASY_RUN_MIN_SPEED_MPS = 1.65`
     - `EASY_RUN_FALLBACK_SPEED_MPS = 1.75`
     - `EASY_RUN_IMPACT_RMS_MPS2 = 1.8`
     - `EASY_RUN_MIN_STEP_HZ = 2.15`
     - `INTRA_RUN_HYSTERESIS_MIN_MS = 6_000L`
   - Extend `private data class AccelFootstrikeEval` (`:111`) to:
     `private data class AccelFootstrikeEval(val footstrikeFreqHz: Double?, val confirmedNoFootstrikes: Boolean, val impactRmsMps2: Double? = null)`
   - Update `evaluateAccelFootstrike(nowMs)` (`:915-939`) to compute `val finiteImpactRms = impactRms.takeIf { it.isFinite() }` and propagate `finiteImpactRms` in ALL post-span return paths (including `< ACCEL_IMPACT_MIN_RMS_MPS2`, so low-impact walking RMS like `0.6 m/s²` is distinguishable from `null` accel-absent!).

2. **Asymmetric Running Dwell Hysteresis (`classify()` `:390-417`, `REQ-FIL-035.3`)**:
   - Preserve immediate `0 ms` verified wrist-glance transition (`committedMode.isRunningMode() && rawCandidate == PhoneCarriageMode.RUN_HANDHELD && isGlanceActive(nowMs)`).
   - Replace `isDismountOrRemountTransition()` dwell selection with explicit helper `requiredDwellMs(from: PhoneCarriageMode, to: PhoneCarriageMode): Long`:
     - `from.isRunningMode() && to == PhoneCarriageMode.WALKING_TRANSITION -> mountHysteresisMs` (`4_000L` default — rejects 2–3 s canopy GPS speed dips).
     - `from.isRunningMode() && to.isRunningMode() -> maxOf(mountHysteresisMs, INTRA_RUN_HYSTERESIS_MIN_MS)` (`6_000L` default — eliminates mid-run waist/bicep/handheld chatter when `!isGlanceActive`).
     - `from.isWalkingOrStationary() || to.isWalkingOrStationary() -> dismountHysteresisMs` (`2_000L` default — keeps `WALKING_TRANSITION -> RUN_*`, `RUN_* -> STATIONARY`, `STATIONARY <-> *`, and `BIKE_* <-> WALKING_TRANSITION` fast, preserving `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest` Phase 2 `5.0 s` walk→run and `ATT-9062` bakery responsiveness).
     - `else -> mountHysteresisMs` (`4_000L` default).

3. **Easy-Pace Running Walking-Gate Bypass (`evaluateRawCandidate()` `:486-509`, `REQ-FIL-035.2`)**:
   - Compute `easyRunSuppressWalk` strictly scoped to `sportHint == SportContextHint.RUNNING`:
     ```kotlin
     val easyRunSuppressWalk = sportHint == SportContextHint.RUNNING &&
         effectiveSpeed != null &&
         effectiveSpeed >= EASY_RUN_MIN_SPEED_MPS &&
         (
             (accelEval.impactRmsMps2 != null && accelEval.impactRmsMps2 >= EASY_RUN_IMPACT_RMS_MPS2) ||
             (stepFreqHz != null && stepFreqHz >= EASY_RUN_MIN_STEP_HZ) ||
             (effectiveSpeed >= EASY_RUN_FALLBACK_SPEED_MPS &&
                 (accelEval.impactRmsMps2 == null || accelEval.impactRmsMps2 >= ACCEL_IMPACT_MIN_RMS_MPS2))
         )
     ```
   - Gate **BOTH** `walkingStepBand` (`if (!easyRunSuppressWalk && walkingStepBand && ...)`) **AND** `walkingSpeedBand` (`if (!easyRunSuppressWalk && walkingSpeedBand)`) so easy runs (`1.65–2.5 m/s`, `2.15–2.30 Hz` or batched/dropped step detector) never get trapped in `WALKING_TRANSITION`, while `effectiveSpeed == null`, `< 1.65 m/s` obstacle climbs, low-impact walking (`impactRmsMps2 < 1.2`), and all non-`RUNNING` (`UNKNOWN`/`CYCLING`) walking/dismount tests remain 100% preserved.

4. **Sport-Context Cycling Guardrail & Non-Sticky `BIKE_*` Eviction (`evaluateRawCandidate()` `:511-583`, `REQ-FIL-035.1`)**:
   - Compute:
     ```kotlin
     val suppressCyclingForRun = sportHint == SportContextHint.RUNNING &&
         !wheelActiveMoving &&
         (wheelSpeed == null || wheelSpeed <= RUN_CYCLING_MASK_WHEEL_MAX_MPS) &&
         (effectiveSpeed == null || effectiveSpeed < RUN_CYCLING_MASK_SPEED_MPS)
     ```
   - In zero-IMU fallback (`:535-541`), return `PhoneCarriageMode.RUN_WAIST_HIP` when `suppressCyclingForRun || runningPrior > cyclingPrior`.
   - When `suppressCyclingForRun` is `true`, set `sBikeHandlebar = Double.NEGATIVE_INFINITY`, `sBikeJersey = Double.NEGATIVE_INFINITY`, `sBikeFrontPocket = Double.NEGATIVE_INFINITY` (otherwise compute normal `cyclingPrior + scoreBike*`).
   - Because `sBike*` are `Double.NEGATIVE_INFINITY`, candidate selection excludes all `BIKE_*` modes AND `committedScore` (`:570-578`) automatically maps any stale `BIKE_*` `committedMode` to `Double.NEGATIVE_INFINITY` (non-finite -> `committedScore.isFinite()` is `false`, immediately breaking stickiness!). Initialize `bestMode`/`bestScore` cleanly across all 6 modes (starting with `sBikeHandlebar` and comparing `>` for the remaining 5 guarantees `sRunWaist` beats `Double.NEGATIVE_INFINITY`).

5. **Front-Hip Belt & Running Carry Scoring Tiers (`:659-738`, `REQ-FIL-035.2`)**:
   - `scoreRunWaistHip()`:
     - `o.swingSdDeg`: `< 10.0 -> +2.6`, `< 15.5 -> +1.8`, `< 22.0 -> -0.5`, else `-2.0`.
     - `yawResidualRms`: `< 0.06 -> +0.8`, `<= 0.48 -> +2.2`, `<= 0.68 -> +1.4`, `<= 0.95 -> -0.4`, else `-1.5`.
     - `baroStepRms`: `< 0.45 -> +1.2`, else `-0.5`.
     - `stepFreqHz`: `>= EASY_RUN_MIN_STEP_HZ (2.15) -> +0.8`.
     - `glanceCount`: `== 0 -> +1.2`, else `-1.5`.
   - `scoreRunBicepArm()`:
     - `o.swingSdDeg`: `>= 15.5 -> +2.6`, `>= 11.5 -> +0.8`, else `-0.8` (preserving `17.0°` bicep armband win and preventing front-hip belt hijack).
     - Keep yaw (`0.16..0.48 -> +2.0`, `> 0.48 -> +0.3`, else `-1.0`), baro (`< 0.36 -> +1.6`, else `-0.8`), dominant-freq harmonic (`+1.4` / `+0.8`), and glance (`== 0 -> +0.8`, else `-1.5`) unchanged.
   - `scoreRunHandheld()`:
     - `o.swingSdDeg`: `>= 15.0 -> +1.8`, `>= 11.0 -> +0.4`, else `-1.5`.
     - `yawResidualRms`: `>= 0.55 -> +2.6`, `>= 0.42 -> +0.8`, else `-1.5`.
     - `baroStepRms`: `>= 0.45 -> +1.8`, else `0.0`.
     - `glanceCount`: `>= 1 -> +2.8`, else `-1.4`.

### Step 2: Targeted Behavioral Unit Tests (`PhoneCarriageClassifierTest.kt`)
- Add `TST-FIL-035` to `// Linked test specs:` header in `PhoneCarriageClassifierTest.kt` AND annotate every new test with explicit hyphenated `// REQ-FIL-035.x / TST-FIL-035.x` comments for clean-checkout `REQ-PRO-099.3` parity (`0` `src/main` file reads per `REQ-PRO-049.1`):
  1. `testRunningSportContextGuardrail_masksCyclingEvictsStaleBikeAndAllowsBrickOverride_tstFil035_1` (`REQ-FIL-035.1 / TST-FIL-035.1`): verifies `0%` `BIKE_*` under `Sport = RUN` with `wheelSpeed <= 0.5` & `speed < 5.5` (including zero-IMU fallback and cycling-like front-pocket IMU), verifies immediate non-sticky eviction of a stale committed `BIKE_FRONT_POCKET`, and verifies triathlon/brick override lifts mask on `wheelSpeed = 6.0` or `gnssSpeed = 6.5`.
  2. `testFrontHipRunningBeltAndEasyPace_bypassesWalkingBandsPreservesObstacleWalkAndCarrySeparation_tstFil035_2` (`REQ-FIL-035.2 / TST-FIL-035.2`): replays easy-pace front-hip running belt (`1.8–2.5 m/s`, both `2.20 Hz` in `walkingStepBand` and dropped steps with `impactRms >= 1.8 m/s²`, mild/lively/bouncy/low-yaw belt IMU profiles) asserting `RUN_WAIST_HIP (5)`; replays slow obstacle climb (`1.2 m/s`, `1.5 Hz`, `impactRms < 1.2 m/s²`) asserting `WALKING_TRANSITION (1)`; verifies rear clip (`RUN_WAIST_HIP`), bicep armband (`17.0° -> RUN_BICEP_ARM`), handheld with glance (`RUN_HANDHELD`), and handheld without glance (`swing >= 16°`, `yaw >= 0.65`, `baro >= 0.50 -> RUN_HANDHELD`).
  3. `testChatterResistantMarginAndAsymmetricDwell_suppressesTransientDipsWhilePreservingFastGlanceAndWalkToRun_tstFil035_3` (`REQ-FIL-035.3 / TST-FIL-035.3`): replays steady front-hip belt run perturbed by 2.5 s canopy GPS speed dips (`1.2 m/s`) and 4.5 s non-glance bicep/handheld IMU bursts asserting `0` spurious switches (`RUN_WAIST_HIP` held continuously), while verifying `0 ms` verified wrist-glance `RUN_WAIST_HIP -> RUN_HANDHELD`, `2.0 s` `WALKING_TRANSITION -> RUN_WAIST_HIP`, and `2.0 s` `RUN_WAIST_HIP -> STATIONARY`.

### Step 3: Verification, Governance & Stage 4 Submission
- Execute locked incremental Gradle verification (NEVER `--rerun-tasks`, NEVER `-PstampGitHash=true`):
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun \
    --tests "*PhoneCarriageClassifierTest" \
    --tests "*SpeedAndLocationDevice_ATTFusedTest" \
    --tests "*SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest" \
    --tests "*AltitudeAndSlopeDevice_ATTFusedTest" \
    --tests "*SmartphoneImuCadenceEstimatorTest" \
    --tests "*KinematicSportTypeDiscriminatorTest" \
    --tests "*MultisportTransitionDetectorTest" \
    --tests "*ArchitectureConformanceContractTest" \
    --tests "*DeadCodeEliminationContractTest" \
    --tests "*SystemLiveTrackingRequirementsContractTest" \
    --tests "*SourceTextReadingAuditContractTest"
  ```
- Confirm `0` failures, `0` errors, `0` skipped, `0` new compiler warnings, `PhoneCarriageClassifier.kt <= 1,500` LoC, `SpeedAndLocationDevice_ATTFused.kt = 1,459` LoC (`0` edits), and `extract_test_code_tags(repo_root=".")` indexes `TST-FIL-035`.
- Promote `REQ-FIL-035` and `TST-FIL-035` to `[Implemented]`, run `--sync-indexes` and clean-checkout `verify_requirement_governance.py --check-verified-evidence` (`exit 0`), commit strictly Stage 4 files, and submit Stage 4.
