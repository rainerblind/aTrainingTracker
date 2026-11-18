# Stage 2: Requirement & Test Specification - ATT-8890: Activity & Phone Carriage Mode Classification, 1-Hz Persistence & Adaptive EKF/ERTS Filtering

**Ticket**: `ATT-8890` (`3 SP`, `Feature`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-18_02_Spät` (`ID 460`) | **Requirement Mapping**: `REQ-FIL-028` (`SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-010`) | **Test Spec ID**: `TST-FIL-028`  
**Branch**: `feature/ATT-8890` | **Sub-task**: `[Req & Test Spec]` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-FIL-028`)

### 1.1 Problem Statement & Rationale (Chesterton's Fence Summary)
* Static filter constants from `ATT-8880`/`ATT-8881` (`1.0 s` gyro mean, `7 s` baro median, `rBaro = 0.25`) cannot optimize across rigid handlebars (`Q_GYRO = 0.0004`, `2–3 s` baro), jersey pockets (`4x`, `5 s`, `0.6 m^2`), jeans front pockets (`25x–40x`, `7–9 s`, `0.7 m/s` slew, `2.0 m^2`), running waist/arm/handheld (`1.0–1.5 s` / `2/f_step` / glance-gating, `0.8 m/s` chord heading), walking dismounts, and indoor stops.
* Refines `REQ-SEN-028.3`, `REQ-FIL-016.2`, `REQ-FIL-017.1`–`.2`, `REQ-FIL-023.1`, `REQ-FIL-025.1`–`.2`, `REQ-FIL-026`, `REQ-FIL-027`, `REQ-TRK-070`, `REQ-TRK-083` (`14935efb`, `b73c6375`, `1ef85e6a`) while preserving `SensorType` ordinals `0..66`, `MIN_DOPPLER_SPEED_MPS = 3.0`, `ZUPT_SPEED_VARIANCE = 2.0`, `DEFAULT_VAR_BARO = 0.25`, `4`-step onboarding (`PermissionOnboardingSheetContractTest`), and raw column immutability.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit (`./gradlew testDebugUnitTest`)

### 1.3 Functional & Architectural Requirements
1. **`PhoneCarriageMode` & `PhoneCarriageClassifier` (`REQ-FIL-028.1`)**: Define `UNKNOWN(-1)`, `STATIONARY(0)`, `WALKING_TRANSITION(1)`, `BIKE_HANDLEBAR(2)`, `BIKE_JERSEY_BACK(3)`, `BIKE_FRONT_POCKET(4)`, `RUN_WAIST_HIP(5)`, `RUN_BICEP_ARM(6)`, `RUN_HANDHELD(7)`. Fuse 50-Hz orientation stability/cyclic variance, gyro-to-GNSS yaw innovation, barometer micro-slew turbulence, speed/wheel cadence, optional step frequency, and `ActivityType` context with dwell hysteresis (`>= 4–8 s` mount stability, fast dismount/stop switch). Gate `Sensor.TYPE_STEP_DETECTOR` defensively (`sdkInt < 29 || ACTIVITY_RECOGNITION granted`, non-null sensor, `SecurityException` catch, `SDK_INT == 0` plain-JVM guard; zero onboarding/startup permission prompts; `OnlineUserGuideContractTest` bans `"ACTIVITY_RECOGNITION"` in `docs/guide/`) with deterministic fallback if `TYPE_STEP_DETECTOR`/`TYPE_ROTATION_VECTOR`/`TYPE_GYROSCOPE`/`TYPE_PRESSURE` is missing.
2. **1-Hz `SensorType.ACTIVITY_AND_CARRIAGE_MODE`, DB/CSV & Reachability (`REQ-FIL-028.2`)**: Append strictly at `SensorType` end (ordinal `67`), `SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`, `SensorValueType.INTEGER`, `filteringPossible = false`, `internalUnit = "dimensionless"`, `'Internal unit:'` Javadoc, `PhoneCarriageModeFormatter`, `ActivityType.getSensorTypeArray()` & `DeviceType.getDeviceTypeList()` exposure (`Rule 81`). Dynamic `INTEGER` DB/CSV writes add `0` LoC to `TrackerService.java`, `DeviceManager.java`, `WorkoutSamplesDatabaseManager.java`, `CSVFileWriter.java`, `WorkoutRepository.kt`.
3. **Smooth Live EKF/Filter Adaptation (`REQ-FIL-028.3`)**: Smoothly blend `Q_GYRO`, yaw window + glance gating, baro median window + slew clamp, `rBaro`, running-pace chord heading, and dismount ZUPT across all 8 active modes + `UNKNOWN` without state jumps or breaking sample-1 `0.1 rad/s` `WORLD_YAW_RATE_radps` emission or existing EKF constants.
4. **1-Hz ERTS Ingestion & Legacy Kinematic Fallback (`REQ-FIL-028.4`)**: Add `ACTIVITY_AND_CARRIAGE_MODE` to `ErtsPostWorkoutRecomputer.ALLOWED_CANONICAL_COLUMNS` + `updateSamplesDatabase()`, feed 1-Hz modes into `ExtendedRtsOdometrySmoother`/`ExtendedRtsAltitudeSlopeSmoother` for dismount pinning and per-mode scaling, and infer 1-Hz kinematic/variance fallback modes on legacy tables (`ExtendedRtsOdometrySmootherTest:140` preserved).
5. **9-Locale & `SYS.2`/`SYS.3`/`SWE.2` Parity Plan (`REQ-FIL-028.5`)**: Add `9/9` `strings_devices.xml` translations (`EN, DE, ES, FR, IT, JA, NL, PL, PT`, zero `&#10;`, `\n` parity) for `SensorType` + 9 mode labels. Synchronize `SensorCategoryAndSelectorContractTest` (`67->68`), `SensorTypeUnitsContractTest`, `SensorFilterDefaultsTest`, `docs/architecture/sensor_units.md`, `docs/architecture.md` (`Rule 8.8`), and `docs/system/` (`SYSREQ-TRK-010`, `system_context.md`, `system_architecture.md`, `system_software_traceability.md` per `REQ-PRO-131.4`) in Stages 3–5 with `0` `src/main` test reads (`REQ-PRO-049.1`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Bakery Tour Timeline, 1-Hz Persistence & ERTS Ingestion)**: *Given* a workout transitioning `BIKE_FRONT_POCKET -> WALKING_TRANSITION -> STATIONARY -> WALKING_TRANSITION -> BIKE_FRONT_POCKET`, *When* `PhoneCarriageClassifier` runs live and `WorkoutSamplesDatabaseManager` / `CSVFileWriter` / `ErtsPostWorkoutRecomputer` execute, *Then* `SensorType.ACTIVITY_AND_CARRIAGE_MODE` persists integer codes (`4,1,0,1,4`) at `1 Hz` in DB & CSV, legacy DBs fall back deterministically, and ERTS pins stops accurately.
* **Criterion 2 (All 6 Cycling/Running Modes, Smooth Filtering & Missing-Sensor Fallback)**: *Given* moving `BIKE_HANDLEBAR`, `BIKE_JERSEY_BACK`, `BIKE_FRONT_POCKET`, `RUN_WAIST_HIP`, `RUN_BICEP_ARM`, `RUN_HANDHELD` streams plus denied `ACTIVITY_RECOGNITION` / missing sensors / `SDK_INT == 0`, *When* classified and filtered live, *Then* all modes converge cleanly without state jumps or crashes, localization passes `9/9`, and governance exits `0`.

### 1.5 System Invariants
Ordinals `0..66` untouched; `filteringPossible = false`; `0` onboarding/monolith LoC growth; `MIN_DOPPLER_SPEED_MPS = 3.0`, `ZUPT_SPEED_VARIANCE = 2.0`, `DEFAULT_VAR_BARO = 0.25`, sample-1 yaw & `ExtendedRtsOdometrySmootherTest:140` invariant; `0` `src/main` reads (`REQ-PRO-049.1`); `9/9` locale parity.

---

## 2. Test Specification (`TST-FIL-028`)

* **`TST-FIL-028.1` (`PhoneCarriageClassifierTest`, `SpeedAndLocationDevice_ATTFusedTest`)**: Replay all 9 modes + `Zum Bäcker` timeline + missing `TYPE_STEP_DETECTOR`/denied `ACTIVITY_RECOGNITION`/missing rotation/baro/`SDK_INT == 0`; assert mode accuracy, dwell hysteresis, fast dismounts, zero `SecurityException`/NPEs.
* **`TST-FIL-028.2` (`SensorCategoryAndSelectorContractTest`, `SensorTypeUnitsContractTest`, `SensorFilterDefaultsTest`, `ErtsPostWorkoutRecomputerTest`)**: Verify ordinal `67`, `INTEGER`, `filteringPossible = false`, `"dimensionless"`, `ActivityType`/`DeviceType` reachability, `PhoneCarriageModeFormatter`, 1-Hz DB/CSV persistence, `0` monolith LoC growth.
* **`TST-FIL-028.3` (`SpeedAndLocationDevice_ATTFusedTest`, `AltitudeAndSlopeDevice_ATTFusedTest`, `BarometricAltitudeFilterTest`, `UnicycleOdometryEkfTest`, `KinematicAltitudeSlopeEkfTest`)**: Verify mid-session mode switches smoothly adapt `Q_GYRO`, yaw window/glance gate, baro window/slew, and `rBaro` without state discontinuities or regression to existing baselines.
* **`TST-FIL-028.4` (`ErtsPostWorkoutRecomputerTest`, `ExtendedRtsOdometrySmootherTest`, `ExtendedRtsAltitudeSlopeSmootherTest`)**: Replay recorded-mode DBs (`Ride -> Walk -> Stop -> Ride`) & legacy DBs (kinematic fallback), verifying dismount pinning, pocket dampening, handlebar sensitivity, and `:140` canopy test.
* **`TST-FIL-028.5` (Contracts, 9-Locale & Governance)**: Run `TranslationParityTest` (`9/9` `strings_devices.xml`), `MergedManifestFgsAuditTest`, `PermissionOnboardingSheetContractTest`, `SourceTextReadingAuditContractTest` (`0` new `src/main` reads), Stage 3–5 `SWE.2`/`SYS.2`/`SYS.3` doc contract tests, and `verify_requirement_governance.py` (`exit 0`).

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-FIL-028.1` (`SYSREQ-TRK-004`/`010`) | `PhoneCarriageMode`, `PhoneCarriageClassifier`, hysteresis & gated sensor fallback | `TST-FIL-028.1` | Unit (`PhoneCarriageClassifierTest`) |
| `REQ-FIL-028.2` (`SYSREQ-TRK-003`) | `SensorType.ACTIVITY_AND_CARRIAGE_MODE` ordinal `67`, DB/CSV, reachability, formatter | `TST-FIL-028.2` | Unit & Schema Contracts |
| `REQ-FIL-028.3` (`SYSREQ-TRK-004`/`010`) | Smooth live adaptation of `Q_GYRO`, yaw/glance, baro median/slew & `rBaro` | `TST-FIL-028.3` | Unit & Simulation Replay |
| `REQ-FIL-028.4` (`SYSREQ-TRK-010`) | `ErtsPostWorkoutRecomputer` 1-Hz mode ingestion & legacy kinematic fallback | `TST-FIL-028.4` | Unit & SQLite ERTS Replay |
| `REQ-FIL-028.5` (`REQ-PRO-131.4`, `Rule 8.8`) | 9-locale `strings_devices.xml` parity, `SWE.2`/`SYS.2`/`SYS.3` sync, `0` `src/main` reads | `TST-FIL-028.5` | Contract & Governance Check |
