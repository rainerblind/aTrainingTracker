# Stage 5 Walkthrough: ATT-9157 - Calibrate PhoneCarriageClassifier for Front-Hip Running Belt, Easy-Pace Running, and Sport-Context Guardrails

**Ticket**: `ATT-9157` (`3 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9157` (base `e11a8123`, code SHA `9b89af95`) | **Author**: AI Agent 1 (`Pro` / Neon) | **Date**: 2026-11-20  
**Verified Requirements**: `REQ-FIL-035` (`REQ-FIL-035.1`–`.3`, `[Verified]`) | **Verified Tests**: `TST-FIL-035` (`TST-FIL-035.1`–`.3`, `[Verified]`)

---

## 1. Summary of Changes (Strictly 1 Non-Monolith File `<= 1,500` LoC)
1. **Sport-Context Cycling Guardrail, Non-Sticky Eviction & Brick Override (`REQ-FIL-035.1`, `PhoneCarriageClassifier.kt` `1,124 / 1,500` LoC)**:
   - Defined `suppressCyclingForRun = sportHint == SportContextHint.RUNNING && !wheelActiveMoving && (wheelSpeed == null || wheelSpeed <= 0.5) && (effectiveSpeed == null || effectiveSpeed < 5.5)`.
   - When `suppressCyclingForRun` holds, `PhoneCarriageClassifier.evaluateRawCandidate()` masks `BIKE_HANDLEBAR`, `BIKE_JERSEY_BACK`, and `BIKE_FRONT_POCKET` (`Double.NEGATIVE_INFINITY`, including zero-IMU fallback and `bestMode` initialization to `RUN_WAIST_HIP`), guaranteeing `0%` cycling modes during normal running and mapping any stale `BIKE_*` `committedMode` to `Double.NEGATIVE_INFINITY` so `SCORE_MARGIN` stickiness never holds a masked cycling mode.
   - Once moving wheel speed (`> 0.5 m/s`) or cycling velocity (`>= 5.5 m/s`) arrives, `suppressCyclingForRun` automatically lifts, preserving triathlon/duathlon/brick `RUN -> BIKE` detection (`REQ-FIL-031`).
2. **Easy-Pace Running Walking-Gate Calibration, Bonus Low-Impact Brisk Walk Guard & Front-Hip Belt Tiers (`REQ-FIL-035.2`, `PhoneCarriageClassifier.kt`)**:
   - Extended `AccelFootstrikeEval` to propagate `impactRmsMps2: Double?` (`2.1–4.2 Hz` bandpass RMS) across all post-span paths (including `< 1.2 m/s²` so low-impact walking remains distinguishable from accel-absent).
   - Added `easyRunSuppressWalk` scoped strictly to `sportHint == SportContextHint.RUNNING && effectiveSpeed != null && effectiveSpeed >= 1.65` when `impactRmsMps2 >= 1.8`, OR `effectiveRunStepFreqHz >= 2.15` (`stepFreqHz ?: accelEval.footstrikeFreqHz`), OR (`effectiveSpeed >= 1.75` and `impactRmsMps2 == null || impactRmsMps2 >= 1.2`), gating **BOTH** `walkingStepBand` and `walkingSpeedBand`.
   - Bonus real edge-case fix verified during Stage 4: gated `suppressWalkForNoFootstrikeClimb` with `!suppressCyclingForRun` so a low-impact brisk walk (`1.85 m/s`, `impactRms < 1.2 m/s²`) during explicit `Sport = RUNNING` is never mistaken for an uphill MTB climb and cleanly enters `WALKING_TRANSITION` (while `UNKNOWN`/`CYCLING` `2.0 m/s` uphill MTB climbers in `ATT-9062` remain `BIKE_*`).
   - Calibrated smooth front-hip running belt tiers across `scoreRunWaistHip` (`swingSd < 10.0 -> +2.6`, `< 15.5 -> +1.8`, `< 22.0 -> -0.5`, else `-2.0`; `yaw < 0.06 -> +0.8`, `<= 0.48 -> +2.2`, `<= 0.68 -> +1.4`, `<= 0.95 -> -0.4`, else `-1.5`; `baro < 0.45 -> +1.2`; `step >= 2.15 -> +0.8`; `glance == 0 -> +1.2`, else `-1.5`), `scoreRunBicepArm` (`swingSd >= 15.5 -> +2.6`, `>= 11.5 -> +0.8`, else `-0.8`), and `scoreRunHandheld` (`swingSd >= 15.0 -> +1.8`, `>= 11.0 -> +0.4`, else `-1.5`; `yaw >= 0.55 -> +2.6`, `>= 0.42 -> +0.8`, else `-1.5`; `baro >= 0.45 -> +1.8`; `glance >= 1 -> +2.8`, else `-1.4`).
3. **Chatter-Resistant Margin & Asymmetric Running Dwell Hysteresis (`REQ-FIL-035.3`, `PhoneCarriageClassifier.kt`)**:
   - Raised `SCORE_MARGIN` from `0.25` to `0.85`.
   - Added asymmetric `requiredDwellMs(from, to)`: `0 ms` verified wrist-glance `RUN_* -> RUN_HANDHELD`, `mountHysteresisMs (4.0 s)` for `RUN_* -> WALKING_TRANSITION`, `maxOf(mountHysteresisMs, 6_000L)` for non-glance intra-run `RUN_* -> RUN_*`, and fast `dismountHysteresisMs (2.0 s)` for `WALKING_TRANSITION -> RUN_*`, `RUN_* -> STATIONARY`, `STATIONARY <-> *`, and `BIKE_* <-> WALKING_TRANSITION`.
4. **Zero Caller / Monolith / Localization Touch**: `PhoneCarriageClassifier.kt` = **`1,124 / 1,500` LoC** (`+62` net LoC, `376` headroom). `SpeedAndLocationDevice_ATTFused.kt` (`1,459 / 1,500` LoC), `ImuCadenceDsp.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`, `SmartphoneImuCadenceEstimator.kt`, `MultisportTransitionDetector.kt`, all protected monoliths, and `values*/strings_*.xml` (`Localization Parity: N/A - no UI strings`) remain **`0` LoC touched**.

---

## 2. Verification Evidence (Gate 4 Reuse per `Rule 7.5` — `0` `app/` Diff Since `9b89af95`)
- **Tier 1 Targeted Incremental `--rerun` Suite Results (`ATT-9224` Gate 4 Independently Verified — `138/138 PASS`, `0` Failures, `0` Errors, `0` Skipped, `0` New Compiler Warnings)**:

| Test Suite | Result | Verified Scope / Verbatim Test Methods |
| :--- | :---: | :--- |
| `PhoneCarriageClassifierTest` | `12/12 PASS` | `TST-FIL-035.1`/`.2`/`.3`: `testRunningSportContextGuardrail_masksCyclingEvictsStaleBikeAndAllowsBrickOverride_tstFil035_1`, `testFrontHipRunningBeltAndEasyPace_bypassesWalkingBandsPreservesObstacleWalkAndCarrySeparation_tstFil035_2`, `testChatterResistantMarginAndAsymmetricDwell_suppressesTransientDipsWhilePreservingFastGlanceAndWalkToRun_tstFil035_3` + all 9 existing regressions (incl. `TST-FIL-034.1`–`.4` & `TST-FIL-028`) |
| `SpeedAndLocationDevice_ATTFusedTest` | `31/31 PASS` | `testHandheldGlanceGatingAndRunningChordHeading_tstFil028_3` + `TST-FIL-034.1`/`.3` & all 30 existing regressions |
| `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest` | `3/3 PASS` | Live BANALService sport-context & Phase 2 `5.0 s` walk→run transition parity |
| `AltitudeAndSlopeDevice_ATTFusedTest` | `21/21 PASS` | Carriage-adaptive barometric filtering parity |
| `KinematicSportTypeDiscriminatorTest` | `6/6 PASS` | Sport discrimination & footstrike parity |
| `MultisportTransitionDetectorTest` | `10/10 PASS` | Multisport transition & brick carriage-mode parity |
| `SmartphoneImuCadenceEstimatorTest` | `12/12 PASS` | Shared `ImuCadenceDsp` & carriage-mode cadence parity |
| `UnicycleOdometryEkfTest` | `17/17 PASS` | Odometry EKF & stationary pinning parity |
| `ArchitectureConformanceContractTest` | `11/11 PASS` | Monolith LoC ceilings (`PhoneCarriageClassifier.kt = 1,124 / 1,500`, `SpeedAndLocationDevice_ATTFused.kt = 1,459 / 1,500` [`0` edits], `0` protected monolith edits) |
| `DeadCodeEliminationContractTest` | `5/5 PASS` | Zero dead code / unused helpers |
| `SourceTextReadingAuditContractTest` | `6/6 PASS` | `0` `src/main` reads in tests (`REQ-PRO-049.1`) |
| `SystemLiveTrackingRequirementsContractTest` | `4/4 PASS` | `SYSREQ-TRK-003`/`004`/`010` <-> `REQ-FIL-035` traceability |

- **Governance & Code-Freeze Check**:
  - `verify_requirement_governance.py --repo-root . --sync-indexes`, `--base-ref refs/heads/sprint/2026-11-19_03_Nacht`, AND clean-checkout `--check-verified-evidence --test-results-dir /tmp/empty_check_9157_s5`: **`exit 0`** (`100%` `[Verified]` parity, `4` static `TST-FIL-035` tags indexed).
  - `git diff 9b89af95 -- app/`: **empty (`0` bytes changed since Gate 4 `PASS`)**.

---

## 3. Handoff Status
- `feature/ATT-9157` preserved intact atop `sprint/2026-11-19_03_Nacht` (`e11a8123`, NO premature merge), ready for Gate 5 review and Stage 6 Cold Final Review (`Final Review (Agent)`).
