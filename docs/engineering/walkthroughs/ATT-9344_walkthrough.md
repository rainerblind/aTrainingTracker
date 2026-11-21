# Stage 5: Walkthrough & Verification - ATT-9344: Fix Mid-Workout Crash Recovery Dropping Lap 1, Abandoning Polar Raw Sidecar .tmp, and Barometer Reset Slope Spike

**Ticket**: `ATT-9344` (`2 SP`, `Bug`, `medium-risk`, `severity-major`) | **Parent Epic**: `ATT-235` (*No Crashes & App Stability*) | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467) | **Requirement Mapping**: `REQ-STB-025` (`REQ-STB-025.1`–`REQ-STB-025.4`) | **Test Mapping**: `TST-STB-025` (`TST-STB-025.1`–`TST-STB-025.4`)  
**Branch**: `feature/ATT-9344` (Verified Code SHA: `a98d0157`; Gate 4 `ATT-9429` PASS) | **Author**: AI Agent 1 (`--as agent1`, Neon) | **Date**: 2026-11-21

---

## 1. Executive Summary & Verification Overview
Resolved all three mid-workout crash-recovery data-integrity defects uncovered during Pixel 10 run `2026-11-20_173833` across 12 production files (11 modified + new `PolarRawSidecarRecovery.kt`), `docs/architecture.md` (`v1.0.18`), and `docs/architecture/detailed_design/tracker_service.md` (`v1.0.6`), with zero UI or localization changes (`Localization Parity: N/A - no UI strings`):
- **Seamless `LAP_NR` Continuity (`REQ-STB-025.1`)**: `MyAccumulatorSensor.java` normalizes `mInitialValue` by `mZeroValue` in both its constructor (`sub(initialVal, zeroValue)`) and `setInitialValue()` (`sub(value, mZeroValue)`), eliminating `SensorType.LAP_NR` (`mZeroValue = 1`) off-by-one `1 -> 2` increments across `RESUME_SERVICE_RECREATION` and `RESUME_BY_USER` while preserving `mZeroValue == 0` accumulators, `newLap()`, `reset()`, and `resetTo()`; `WorkoutRepository.kt` clamps `laps >= BANALService.INIT_LAP_NR` and queries last persisted finite altitude (`queryLastFiniteAltitude`).
- **Truncated `.polar_raw.csv.gz.tmp` Salvage, Resume & Export Fallback (`REQ-STB-025.2`)**: `PolarRawSidecarRecovery.kt` stages `.tmp`/prior `.gz` atomically to `.salvage` and stream-salvages via O(1)-memory 256-byte chunk reads from `GZIPInputStream(fis, 512)` (avoiding `BufferedReader`/`StreamDecoder` 8 KB pre-fill `EOFException` loss), dropping incomplete trailing lines via `\n`-framing + `RawTelemetryRow.fromCsvLine(trimmed) != null`; `PolarRawTelemetryRecorder.resumeOrStartActiveSession()` resumes seamlessly from `TrackerService.applyResumedState()`; `RawSensorFileExporter.doExport()` salvages abandoned `.tmp` archives before graceful skip.
- **Silent Live Barometer Re-Anchor & Pre-Slew ERTS Step Absorption (`REQ-STB-025.3`, `REQ-STB-025.4`)**: `AltitudeFromPressureDevice.java` applies `reanchorSilently(double)` via dedicated `mPendingSilentAnchorAltitude` without calling `calibrate()` or broadcasting `ALTITUDE_CORRECTION_INTENT`; `KinematicAltitudeSlopeEkf.kt` & `AltitudeAndSlopeDevice_ATTFused.kt` re-anchor both 3-State and 2-State barometer-only modes via `BANALService.reanchorBarometricAltitudeOnResume()` without ascent/descent jumps; `ErtsPostWorkoutRecomputer.kt` (`detectAndCompensateBaroResetSteps()`) runs on raw un-slewed barometer values prior to `BarometricAltitudeFilter.filterSlewOnly()` (5-sample stable baseline + `>= 3/4` sustained post-step) paired with `ExtendedRtsAltitudeSlopeSmoother.kt` `BARO_RESET_Q_BIAS_IMPULSE`, eliminating `+35%` slope spikes while preserving transient pocket-spike rejection (`REQ-TRK-057.1`) and `10%` climbs (`REQ-FIL-033.2`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verified Test Methods & Scope (Gate 4 `ATT-9429` @ `a98d0157`) | Result | Living Docs |
| :--- | :--- | :--- | :---: | :---: |
| `REQ-STB-025.1` | `TST-STB-025.1` | `MyAccumulatorSensorTest.testLapNrAccumulator_withNonZeroZeroValue_restoresExactLapWithoutDoubleCount_TST_STB_025_1` (`6/6`), `TrackerServiceCrashRecoveryCharacterisationTest.testApplyResumedState_reanchorsBarometerAndResumesPolarSidecarForCrashAndUserResume_TST_STB_025_1` (`7/7`), `AccumulatorTest` (`5/5`), `MyAccumulatorSensorResetToTest` (`2/2`) | **PASS** | `[Verified]` |
| `REQ-STB-025.2` | `TST-STB-025.2` | `PolarRawTelemetryRecorderTest.testResumeOrStartActiveSession_salvagesTruncatedTmpDropsPartialLineAndAppendsPostCrashRows_TST_STB_025_2` (`9/9`), `RawSensorFileExporterTest.testDoExport_whenAbandonedTmpExists_salvagesToFinalSidecarAndExports_TST_STB_025_2` (`4/4`), `PolarRawTelemetryReaderTest` (`3/3`), `PolarRawTelemetryPrunerTest` (`4/4`) | **PASS** | `[Verified]` |
| `REQ-STB-025.3` | `TST-STB-025.3` | `AltitudeFromPressureDeviceTest.testReanchorSilently_beforeAndAfterFirstPressure_alignsWithoutRetroactiveBroadcast_TST_STB_025_3` (`13/13`), `AltitudeAndSlopeDevice_ATTFusedTest.testReanchorOnResume_bothThreeStateAndTwoState_preventsStepAndAscentJump_TST_STB_025_3` (`23/23`) | **PASS** | `[Verified]` |
| `REQ-STB-025.4` | `TST-STB-025.4` | `ErtsPostWorkoutRecomputerTest.testBarometerResetStep_compensatedBeforeSlewFilterWhilePreservingPocketSpikeAndAlpineClimb_TST_STB_025_4` (`21/21`), `ExtendedRtsAltitudeSlopeSmootherTest.testSustainedBaroResetStep_absorbedIntoBiasWithoutSlopeSpike_TST_STB_025_4` (`9/9`), `ArchitectureConformanceContractTest` (`11/11`), `InterfaceSignatureDriftContractTest` (`3/3`), `DetailedDesignParityContractTest` (`3/3`) | **PASS** | `[Verified]` |

---

## 3. Automated Test Evidence (Rule 7.5 Doc-Only Fast Path — Reusing Gate 4 `ATT-9429` @ `a98d0157`)
Stage 5 modifies **zero** production or test source files (`a98d0157` unchanged); per `Rule 7.5`, Gate 4's verified Tier 1 targeted Gradle execution (`--rerun --tests ...`, never `--rerun-tasks`) is reused directly:
```text
Tier 1 Targeted Scope (18 suites, Gate 4 ATT-9429 @ a98d0157):
  AltitudeAndSlopeDevice_ATTFusedTest (23/23), AltitudeFromPressureDeviceTest (13/13),
  ErtsPostWorkoutRecomputerTest (21/21), ArchitectureConformanceContractTest (11/11),
  PolarRawTelemetryRecorderTest (9/9), ExtendedRtsAltitudeSlopeSmootherTest (9/9),
  TrackerServiceCrashRecoveryCharacterisationTest (7/7), MyAccumulatorSensorTest (6/6),
  AccumulatorTest (5/5), SlidingWindowPowerAccumulatorTest (5/5), PolarRawTelemetryPrunerTest (4/4),
  RawSensorFileExporterTest (4/4), PolarRawTelemetryReaderTest (3/3),
  DetailedDesignParityContractTest (3/3), InterfaceSignatureDriftContractTest (3/3),
  MyAccumulatorSensorResetToTest (2/2), SensorAndDeviceConcurrencyStressTest (1/1),
  MySensorDeactivationAndProxyTest (1/1)
Result : BUILD SUCCESSFUL — 18 suites, 130/130 tests PASS (0 failures, 0 errors, 0 skipped, 0 new compiler warnings)
```
- **4/4 Safety Linters (`@ a98d0157`)**: `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, and `check_secrets_hygiene.py` (`9,134` tracked files) all **PASS**.

---

## 4. Device / Visual Verification Scope (`Rule 7.6`, `Rule 8.5`)
- **Non-UI Backend Service & DSP Pipeline Ticket**: Zero UI, Compose, layout, drawable, or `strings*.xml` changes (`No UI changes.`). Per `stage5-verification/SKILL.md` §2, Stage 5 skips emulator screenshot capture; independent Stage 6 Cold Final Review (`Rule 3.3`) will perform live `emulator-5554` deployment/logcat verification prior to atomic `--merge-ticket`.

---

## 5. Invariant & Governance Verification
1. **LoC & Signature Contracts**: `TrackerService.java` = **`3,537/3,559` LoC** (`+0` net lines; 7-arg & 11-arg `ResumedWorkoutState` constructors preserved alongside compact 12-arg overload); `WorkoutRepository.kt` = **`4,985/5,117` LoC**; `BANALService.java` = **`1,097/1,500` LoC**; `PolarRawSidecarRecovery.kt` = `260` LoC; `ArchitectureConformanceContractTest` (`11/11`), `InterfaceSignatureDriftContractTest` (`3/3`), and `DetailedDesignParityContractTest` (`3/3`) **PASS**.
2. **SWE.2 / SWE.3 & Living Docs Parity (`Rule 8.8`, `Rule 8.9`)**: `docs/architecture.md` (`v1.0.18`), `docs/architecture/detailed_design/tracker_service.md` (`v1.0.6`), `req_process_stability_part2.md` (`v1.0.57`), `tst_process_stability_part2.md` (`v1.0.50`), `docs/requirements.md` (`v1.0.101`), and `docs/tests.md` (`v1.0.101`) synchronized to `[Verified]`.
3. **Clean-Checkout Evidence Governance**: `verify_requirement_governance.py --repo-root . --base-ref f8c03d6f --check-verified-evidence --test-results-dir /tmp/empty_check_9344` **PASS (`exit 0`)**.
4. **Branch Hygiene (`Rule 3.3`, `Rule 8.2`)**: `feature/ATT-9344` preserved intact (not prematurely merged onto `sprint/2026-11-20_03_Nacht`) for Stage 6 Cold Final Review.
