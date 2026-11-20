# Stage 5 Walkthrough: ATT-9062 - Fix PhoneCarriageClassifier Indoor GNSS-Outage RUN_WAIST_HIP Freeze & Front-Pocket Pedaling RUN_HANDHELD Misclassification

**Ticket**: `ATT-9062` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9062` (base `b6a1eaea`, code SHA `4b47831a`) | **Author**: AI Agent 1 (`Pro` / Neon) | **Date**: 2026-11-20  
**Verified Requirements**: `REQ-FIL-034` (`REQ-FIL-034.1`–`.4`, `[Verified]`) | **Verified Tests**: `TST-FIL-034` (`TST-FIL-034.1`–`.4`, `[Verified]`)

---

## 1. Summary of Changes (Strictly 2 Non-Monolith Files `<= 1,500` LoC)
1. **Indoor GNSS-Outage, Stagnant-Fix & Decay-to-Zero `STATIONARY` Transition (`REQ-FIL-034.1`, `PhoneCarriageClassifier.kt` `1,062 / 1,500` LoC & `SpeedAndLocationDevice_ATTFused.kt` `1,459 / 1,500` LoC)**:
   - Transitioned stale-GNSS (`effectiveSpeed == null` after prior speed/mode), low-speed (`<= 1.8 m/s`), and stagnant-cached-fix (`>= 4.0 s` time window, `>= 3` fixes, `< 2.0 m` spread, Null-Island guarded) quiet-IMU windows (`swingSdDeg < 2.5`, `rawGyroRms < 0.14`, no steps/footstrikes) to `PhoneCarriageMode.STATIONARY (0)` instead of returning `UNKNOWN` and freezing `RUN_WAIST_HIP (5)` — while keeping pure IMU-only startup checks (zero prior speed) at neutral `UNKNOWN`.
   - Overrode `SpeedAndLocationDevice_ATTFused.triggerDecayTick()` using injectable `currentTimeMillis()` to propagate `0.0 m/s` to `carriageClassifier.ingestGnssSpeed(0.0, null, null, nowMs)` and force `updateCarriageClassification(nowMs, forceClassify = true)`.
2. **Cyclic-Immune `detectGlanceShift()` & Front-Pocket Cycling (`REQ-FIL-034.2`, `PhoneCarriageClassifier.kt`)**:
   - Required `prevSpanMs >= 1_200L` (`prevN >= 4`) to eliminate startup-sliver triggers and rejected periodic thigh oscillation (`0.8–1.8 Hz ±25°`) whenever `(recSdDeg > 8.0 && deltaDeg < 2.6 * recSdDeg)` OR `(prevSdDeg >= 7.0 && deltaDeg <= 1.25 * prevPeakDeg)`.
   - Preserved genuine sustained wrist-glance holds (`70° -> 18–20°` and gentle `70° -> 35°`) and accelerated `RUN_* -> RUN_HANDHELD` on verified active wrist glances so first-glance gyro spike gating activates immediately.
3. **Degraded Multipath GNSS Speed & `isMovingByGnss` Accuracy Gating (`REQ-FIL-034.3`, `SpeedAndLocationDevice_ATTFused.kt`)**:
   - Defined `hasTrustedGpsSpeed = hasGpsSpeed && acc > 0.0 && acc <= UnicycleOdometryEkf.MAX_GNSS_ACCURACY_METERS` gating BOTH `speedForClassifier` and `isMovingByGnss` (and gated `lat`/`lon` stagnation forwarding on `accValid`), blocking `2.89 m/s @ acc = 43.9 m` indoor multipath spikes from overriding walking speed or defeating `forceStationaryPinning`.
4. **Cross-Device & Cross-Athlete Portability (`REQ-FIL-034.4`, `PhoneCarriageClassifier.kt` & `SpeedAndLocationDevice_ATTFused.kt`)**:
   - Added `setStepDetectorRegistered(Boolean)` (distinguishing `stepDetectorUnavailable` from `stepDetectorActiveAndZero`) and signed 3-axis `ingestAccelerometer()` reusing `ImuCadenceDsp` (`50 Hz` time-based `UniformGridResampler`, `2.1–4.2 Hz` `BiquadBandPass` RMS `>= 1.2 m/s²` + ACF `>= 0.42` over `2.3..3.8 Hz`, pause reset `> STALENESS_MS`, `>= 2.0 s` active span for `hasConfirmedNoFootstrikes`).
   - Prioritized `effectiveRunStepFreqHz = stepFreqHz ?: accelFootstrikeFreqHz` ahead of `>= 5.5 m/s` and fed it into `scoreRunWaistHip` & `scoreRunBicepArm`; gated `WALKING_TRANSITION` via `!(confirmedNoFootstrikes && effectiveSpeed >= 1.6)` and awarded `cyclingPrior += 1.5` when `confirmedNoFootstrikes && effectiveSpeed >= 1.8`.
5. **Protected Monoliths & Localization (`0` LoC Edits)**: `SpeedAndLocationDevice_ATTFused.kt` grew strictly `+18` LoC (`1,441 -> 1,459 / 1,500`, `41` headroom); `ImuCadenceDsp.kt`, `ErtsPostWorkoutRecomputer.kt`, `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutRepository.kt`, and `values*/strings_*.xml` (`Localization Parity: N/A - no UI strings`) remain 100% untouched.

---

## 2. Verification Evidence (Gate 4 Reuse per `Rule 7.5` — `0` `app/` Diff Since `4b47831a`)
- **Tier 1 Targeted Incremental `--rerun` Suite Results (`ATT-9208` Gate 4 Independently Verified — `135/135 PASS`, `0` Failures, `0` Errors, `0` Skipped, `0` New Warnings)**:

| Test Suite | Result | Verified Scope / Verbatim Test Methods |
| :--- | :---: | :--- |
| `PhoneCarriageClassifierTest` | `9/9 PASS` | `TST-FIL-034.1`/`.2`/`.4`: `testIndoorGnssOutageAndStagnantCachedFixes_transitionToStationaryInsteadOfRunWaistHipFreeze_tstFil034_1`, `testCyclicFrontPocketPedaling_zeroFalseGlancesAndClassifiesBikeFrontPocket_tstFil034_2`, `testCrossDeviceAndAthletePortability_deniedPermissionBaroFreeFastRunnerAndUphillMtb_tstFil034_4` + all 6 existing regressions |
| `SpeedAndLocationDevice_ATTFusedTest` | `31/31 PASS` | `TST-FIL-034.1`/`.3`: `testDegradedMultipathIndoorSpeed_gatedFromClassifierAndStationaryPinning_tstFil034_3`, `testTriggerDecayTick_propagatesZeroSpeedToCarriageClassifierWithInjectableClock_tstFil034_1` + `testHandheldGlanceGatingAndRunningChordHeading_tstFil028_3` & all 29 regressions |
| `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest` | `3/3 PASS` | Live BANALService sport-context & carriage integration |
| `AltitudeAndSlopeDevice_ATTFusedTest` | `21/21 PASS` | Carriage-adaptive barometric filtering parity |
| `KinematicSportTypeDiscriminatorTest` | `6/6 PASS` | Sport discrimination & footstrike parity |
| `MultisportTransitionDetectorTest` | `10/10 PASS` | Multisport transition carriage-mode parity |
| `SmartphoneImuCadenceEstimatorTest` | `12/12 PASS` | Shared `ImuCadenceDsp` & carriage-mode cadence parity |
| `UnicycleOdometryEkfTest` | `17/17 PASS` | `MIN_DOPPLER_SPEED_MPS = 3.0` & odometry EKF parity |
| `ArchitectureConformanceContractTest` | `11/11 PASS` | Monolith LoC ceilings (`SpeedAndLocationDevice_ATTFused.kt = 1,459 / 1,500`, `0` protected edits) |
| `DeadCodeEliminationContractTest` | `5/5 PASS` | Zero dead code / unused helpers |
| `SourceTextReadingAuditContractTest` | `6/6 PASS` | `0` `src/main` reads in tests (`REQ-PRO-049.1`) |
| `SystemLiveTrackingRequirementsContractTest` | `4/4 PASS` | `SYSREQ-TRK-003`/`004`/`010` <-> `REQ-FIL-034` traceability |

- **Governance & Code-Freeze Check**:
  - `verify_requirement_governance.py --repo-root . --sync-indexes` & clean-checkout `--base-ref refs/heads/sprint/2026-11-19_03_Nacht --check-verified-evidence --test-results-dir /tmp/empty_check_9062_s5`: **`exit 0`** (`100%` `[Verified]` parity, `6` static `TST-FIL-034` tags indexed).
  - `git diff 4b47831a -- app/`: **empty (`0` bytes changed since Gate 4 `PASS`)**.

---

## 3. Handoff Status
- `feature/ATT-9062` preserved intact atop `sprint/2026-11-19_03_Nacht` (`b6a1eaea`, NO premature merge), ready for Gate 5 review and Stage 6 Cold Final Review (`Final Review (Agent)`).
