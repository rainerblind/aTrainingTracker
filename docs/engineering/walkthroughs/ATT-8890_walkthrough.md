# Stage 5: Walkthrough & Verification (Bounce 1/3) - ATT-8890: Activity & Phone Carriage Mode IMU/Barometer Classification, 1-Hz Persistence & Adaptive EKF/ERTS Filtering

**Ticket**: [ATT-8890](https://rainerblind.atlassian.net/browse/ATT-8890) (`3 SP`, `Feature`) | **Sub-task**: `[Test - Bounce 1]` | **Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_02_Spät` (`ID 460`) | **Branch**: `feature/ATT-8890` | **Status**: `Verified`  
**Requirement Mapping**: `REQ-FIL-028` (`REQ-FIL-028.1`..`.5`; `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-010`) | **Test Mapping**: `TST-FIL-028` (`TST-FIL-028.1`..`.5`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Executive Summary & Bounce 1/3 Remediation Overview
* **Core Implementation (`REQ-FIL-028.1`–`.5`)**: Added `PhoneCarriageMode.kt` (`UNKNOWN(-1)`, `STATIONARY(0)`, `WALKING_TRANSITION(1)`, `BIKE_HANDLEBAR(2)`, `BIKE_JERSEY_BACK(3)`, `BIKE_FRONT_POCKET(4)`, `RUN_WAIST_HIP(5)`, `RUN_BICEP_ARM(6)`, `RUN_HANDHELD(7)`), pure-JVM 50-Hz `PhoneCarriageClassifier.kt` (orientation cyclic stability, gyro-to-GNSS yaw innovation, 1-Hz baro roughness, optional `TYPE_STEP_DETECTOR`, glance gating, hysteresis, single-limb pendulum walking `0.60..1.12 Hz`), `SensorType.ACTIVITY_AND_CARRIAGE_MODE` (ordinal `67`, `INTEGER`, `filteringPossible = false`), `PhoneCarriageModeFormatter.kt`, `9/9` `strings_devices.xml`, live adaptation (`SpeedAndLocationDevice_ATTFused.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`, `BarometricAltitudeFilter.kt`), ERTS 1-Hz ingestion + legacy fallback (`ErtsPostWorkoutRecomputer.kt`, `ExtendedRtsOdometrySmoother.kt`, `ExtendedRtsAltitudeSlopeSmoother.kt`), and synchronized `SWE.2`/`SYS.2`/`SYS.3` docs.
* **Cold Stage 6 Bounce 1/3 Fixes (`ATT-9006` Gate 4 Pass)**:
  1. **Two-Pass Standstill Cluster & Stop Pinning**: `computeRawGnssDisplacementSpeedMps()` now evaluates recent `3.0..4.5 s` standstill clusters (`CLUSTER_STOP_MIN_SPAN_SEC = 3.0`, `CLUSTER_STOP_RADIUS_METERS = 1.5`, `recentSampleCount >= 2`) *before* `4.5..15.0 s` lookback fixes, eliminating 15-s post-stop ghost speed tails when `Location.hasSpeed() == false`, feeding `0.0 m/s` standstill speed to EKF, and syncing unpinned initial classifier stop pinning onto `mStationaryAnchor`.
  2. **250ms/1Hz Gyro Throttle, Zero-Alloc Classifier & `dt`-Exponential Smoothing**: Throttled 50-Hz `classify()` evaluations to `CARRIAGE_CLASSIFY_INTERVAL_MS = 250L` (`forceClassify = true` on GNSS / wheel motion-state change), emitted `mActivityAndCarriageModeSensor?.newValue()` strictly on transitions or `>= 1_000L ms`, converted profile adaptation to `dt`-exponential `alpha = 1.0 - exp(-dtSec / 0.75)`, and removed intermediate `HashMap` / `sortedByDescending` / `toList` / `mutableListOf` allocations in `PhoneCarriageClassifier.kt`.
  3. **GNSS Bearing-Gap Yaw Reset**: Reset `estimatedGnssYawRateRadPerSec = 0.0` (and cleared bearing state on invalid/slow fixes) across expired `> 3_000 ms`, out-of-order, low-speed `< 1.2 m/s`, or degraded `> 25 m` GNSS bearing fixes.
* **Verification Scope & Evidence Reuse (`Rule 7.5`)**: Zero source/test edits post-Gate 4 (`ATT-9006`) Pass; reused `--rerun-tasks` `92/92` (9 post-heal suites) + unchanged `195/195` baseline (24 suites), `0` failed, `0` skipped, `0` new Kotlin warnings. Tier 1 JVM/Robolectric/Contract non-UI algorithm scope (`N/A` screenshots; APK deployment & physical field runs skipped per `stage5-verification` §2; full-repo `lintDebug`/`detekt` defer to Tier 2 gate).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope (`Rule 7.5` Tier 1 JVM / Robolectric / Contract) | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-028.1` | `TST-FIL-028.1` | `PhoneCarriageClassifierTest` (`6/6` `--rerun-tasks`), `SpeedAndLocationDevice_ATTFusedTest` (`20/20` `--rerun-tasks`), `MergedManifestFgsAuditTest` (`5/5`), `PermissionOnboardingSheetContractTest` (`4/4`), `MainActivityStartupPermissionContractTest` (`3/3`) | **PASSED** | `Verified` |
| `REQ-FIL-028.2` | `TST-FIL-028.2` | `PhoneCarriageModeFormatterTest` (`3/3` `--rerun-tasks`), `SensorCategoryAndSelectorContractTest` (`4/4`), `SensorTypeUnitsContractTest` (`2/2`), `SensorFilterDefaultsTest` (`15/15`), `ActivityTypeTest` (`7/7`), `ErtsPostWorkoutRecomputerTest` (`14/14` `--rerun-tasks`) | **PASSED** | `Verified` |
| `REQ-FIL-028.3` | `TST-FIL-028.3` | `SpeedAndLocationDevice_ATTFusedTest` (`20/20` `--rerun-tasks`), `AltitudeAndSlopeDevice_ATTFusedTest` (`21/21` `--rerun-tasks`), `BarometricAltitudeFilterTest` (`5/5`), `UnicycleOdometryEkfTest` (`16/16`), `KinematicAltitudeSlopeEkfTest` (`14/14`) | **PASSED** | `Verified` |
| `REQ-FIL-028.4` | `TST-FIL-028.4` | `ErtsPostWorkoutRecomputerTest` (`14/14` `--rerun-tasks`), `ExtendedRtsOdometrySmootherTest` (`10/10` `--rerun-tasks`), `ExtendedRtsAltitudeSlopeSmootherTest` (`4/4`) | **PASSED** | `Verified` |
| `REQ-FIL-028.5` | `TST-FIL-028.5` | `TranslationParityTest` (`10/10`), `ArchitectureConformanceContractTest` (`11/11` `--rerun-tasks`), `DetailedDesignParityContractTest` (`3/3` `--rerun-tasks`), `SystemContextContractTest` (`6/6`), `SystemArchitectureDesignContractTest` (`9/9`), `SystemLiveTrackingRequirementsContractTest` (`4/4`), `SourceTextReadingAuditContractTest` (`4/4` `--rerun-tasks`) | **PASSED** | `Verified` |

---

## 3. Automated Test & Governance Evidence (Explicit Tier 1 Scope)

### Post-Bounce 1 Heal (`--rerun-tasks`, 9 Suites: `92/92` PASSED)
```text
com.atrainingtracker.banalservice.devices.AltitudeAndSlopeDevice_ATTFusedTest: 21 tests, 0 failures, 0 errors, 0 skipped
com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_ATTFusedTest: 20 tests, 0 failures, 0 errors, 0 skipped
com.atrainingtracker.banalservice.odometry.ExtendedRtsOdometrySmootherTest: 10 tests, 0 failures, 0 errors, 0 skipped
com.atrainingtracker.banalservice.odometry.PhoneCarriageClassifierTest: 6 tests, 0 failures, 0 errors, 0 skipped
com.atrainingtracker.banalservice.sensor.formater.PhoneCarriageModeFormatterTest: 3 tests, 0 failures, 0 errors, 0 skipped
com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest: 11 tests, 0 failures, 0 errors, 0 skipped
com.atrainingtracker.trainingtracker.build.DetailedDesignParityContractTest: 3 tests, 0 failures, 0 errors, 0 skipped
com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest: 4 tests, 0 failures, 0 errors, 0 skipped
com.atrainingtracker.trainingtracker.database.ErtsPostWorkoutRecomputerTest: 14 tests, 0 failures, 0 errors, 0 skipped
TOTAL across 9 XMLs: 92 tests, 0 failures, 0 errors, 0 skipped (BUILD SUCCESSFUL)
```

### Baseline & Clean-Room Audit
* **Unchanged Baseline 24-Suite Run (`195/195` PASSED)**: All 15 non-modified baseline suites (`BarometricAltitudeFilterTest` `5/5`, `UnicycleOdometryEkfTest` `16/16`, `KinematicAltitudeSlopeEkfTest` `14/14`, `ExtendedRtsAltitudeSlopeSmootherTest` `4/4`, `ActivityTypeTest` `7/7`, `SensorCategoryAndSelectorContractTest` `4/4`, `SensorTypeUnitsContractTest` `2/2`, `SensorFilterDefaultsTest` `15/15`, `TranslationParityTest` `10/10`, `MergedManifestFgsAuditTest` `5/5`, `PermissionOnboardingSheetContractTest` `4/4`, `MainActivityStartupPermissionContractTest` `3/3`, `SystemContextContractTest` `6/6`, `SystemArchitectureDesignContractTest` `9/9`, `SystemLiveTrackingRequirementsContractTest` `4/4`) passed `0` failures / `0` skipped.
* `python3 tools/verify_requirement_governance.py --repo-root .`: **Exit `0`** (`REQ-FIL-028` & `TST-FIL-028` `[Verified]`, `68 SensorType constant(s)`, `21/21 SYSREQs covered`, `62 governed document(s)`). Monolith LoC unchanged (`TrackerService.java: 3,559`, `DeviceManager.java: 1,708`, `WorkoutSamplesDatabaseManager.java: 1,533`, `WorkoutRepository.kt: 5,048`).
