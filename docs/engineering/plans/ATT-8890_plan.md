# Stage 3: Implementation Plan - ATT-8890: Activity & Phone Carriage Mode Classification, 1-Hz Persistence & Adaptive EKF/ERTS Filtering

**Ticket**: `ATT-8890` (`3 SP`, `Feature`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-18_02_Spät` (`ID 460`) | **Requirement Mapping**: `REQ-FIL-028` (`REQ-FIL-028.1`–`.5`) | **Test Mapping**: `TST-FIL-028`  
**Branch**: `feature/ATT-8890` | **Sub-task**: `[Impl-Plan]` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Problem Description & SWE.2 Architectural Boundaries
* Static gyro/barometer tuning (`ATT-8880`/`ATT-8881`) degrades across diverse mounts/dismounts (`BIKE_HANDLEBAR`, `BIKE_JERSEY_BACK`, `BIKE_FRONT_POCKET`, `RUN_WAIST_HIP`, `RUN_BICEP_ARM`, `RUN_HANDHELD`, `WALKING_TRANSITION`, `STATIONARY`).
* **SWE.2 Threading & Layering**: Pure-JVM `PhoneCarriageMode` & `PhoneCarriageClassifier` (`banalservice.odometry`) run inside `SpeedAndLocationDevice_ATTFused` sensor thread (`IF-SWE-04`); `AltitudeAndSlopeDevice_ATTFused` reads `ACTIVITY_AND_CARRIAGE_MODE` from `MySensorManager` with `UNKNOWN` fallback (no cyclic device dependency); `TrackerService.sampleAndWriteToDb()` persists `SensorValueType.INTEGER` at `1 Hz` on `mDbExecutor` (`IF-SWE-06`, `0` monolith LoC); `ErtsPostWorkoutRecomputer` executes offline on `Dispatchers.IO` (`IF-SWE-05`/`06`).
* **UI Consistency (`docs/design_guidelines.md`)**: `N/A` — zero Compose layouts/widgets modified (`4`-step onboarding in `PermissionOnboardingSheet.kt` untouched; strings-only `strings_devices.xml` telemetry labels).
* **SWE.3 Living Detailed Design (`REQ-PRO-114`)**: `TrackerService`, `BANALService`/`DeviceManager`, `MyBTLEDevice`, and `WorkoutRepository` state machines/flags are **untouched**; verify parity via `DetailedDesignParityContractTest`.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-FIL-028` (`.1` classifier + defensive `TYPE_STEP_DETECTOR` fallback; `.2` `SensorType.ACTIVITY_AND_CARRIAGE_MODE` + DB/CSV + reachability; `.3` smooth live EKF/filter tuning; `.4` 1-Hz ERTS ingestion + legacy fallback; `.5` `9/9` localization + `SWE.2`/`SYS.2`/`SYS.3` parity).
* **Tests**: `TST-FIL-028` (`.1`–`.5`) linked to `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-010`.

---

## 3. System Invariants & Preserved Behavior
1. **Enum & Persistence Invariance**: `SensorType` ordinals `0..66` unchanged (`ACTIVITY_AND_CARRIAGE_MODE` appended at `67`, `filteringPossible = false`, `internalUnit = "dimensionless"`, `'Internal unit:'` Javadoc); `0` LoC added to `TrackerService.java`, `DeviceManager.java`, `WorkoutSamplesDatabaseManager.java`, `CSVFileWriter.java`, `WorkoutRepository.kt`.
2. **Filter & Platform Invariants**: Preserve `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` & `ZUPT_SPEED_VARIANCE = 2.0`, `KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO = 0.25`, sample-1 `0.1 rad/s` `WORLD_YAW_RATE_radps` emission, `ExtendedRtsOdometrySmootherTest:140`, `KEY_POST_WORKOUT_ERTS_SMOOTHING = false`, raw device column immutability, `4`-step onboarding (no eager `ACTIVITY_RECOGNITION` prompt; banned in `docs/guide/`), plain-JVM `SDK_INT == 0` safety, `0` new `src/main` reads (`REQ-PRO-049.1`), and `9/9` locale parity (`\n`, zero `&#10;`).

---

## 4. Proposed Architectural Changes
* **Domain / Classification (`banalservice.odometry` & `res/values*`)**: New `PhoneCarriageMode.kt` (`UNKNOWN(-1)` + `0..7` + helper predicates/mapping), 50-Hz pure-JVM `PhoneCarriageClassifier.kt` (orientation cyclic variance, gyro-to-GNSS yaw innovation, barometer micro-slew turbulence, step/speed cadence, `>= 4–8 s` mount hysteresis, fast dismount transition, glance-shift detection), `PhoneCarriageModeFormatter.kt`, `SensorCategory.java` comment + `SensorType.java` (`ordinal 67`), `ActivityType.java` & `DeviceType.java` reachability, `9/9` `strings_devices.xml`.
* **Live Adaptive Fusion (`banalservice.devices`)**: `SpeedAndLocationDevice_ATTFused.kt` (opportunistic/defensive `TYPE_STEP_DETECTOR` gating + sensor-absent fallback, `PhoneCarriageClassifier` feed, publishing `ACTIVITY_AND_CARRIAGE_MODE`, smooth buffer-preserving yaw window + glance blanking + `Q_GYRO` scale + running chord heading `>= 0.8 m/s` + dismount ZUPT); `BarometricAltitudeFilter.kt` (buffer-preserving dynamic median window & slew update preserving `lastSlewAltitude`); `AltitudeAndSlopeDevice_ATTFused.kt` (observing mode via `MySensorManager`, adapting baro window/slew, `rBaro`, standstill clamping); minor tuning hooks in `UnicycleOdometryEkf.kt` / `KinematicAltitudeSlopeEkf.kt` if needed without altering core constants.
* **Offline ERTS (`banalservice.odometry` & `database`)**: `ErtsPostWorkoutRecomputer.kt` (`ALLOWED_CANONICAL_COLUMNS` propagation + 1-Hz recorded mode ingestion + legacy 1-Hz kinematic/variance fallback), `ExtendedRtsOdometrySmoother.kt` & `ExtendedRtsAltitudeSlopeSmoother.kt` (dismount pinning + per-mode `Q_GYRO`/`rBaro`/window scaling).
* **Documentation & Contracts (`docs/`, `app/src/test/`)**: `docs/architecture.md` (`Rule 8.8`, `67->68`), `docs/architecture/sensor_units.md`, `docs/system/` (`sysreq_live_tracking.md` `SYSREQ-TRK-010`, `system_context.md`, `system_architecture.md`, `system_software_traceability.md`), plus unit/contract suites.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
| Step | Action & Target Files | Mapping | Verification (`./gradlew testDebugUnitTest --tests ...`) |
| :---: | :--- | :--- | :--- |
| **1** | Create `PhoneCarriageMode.kt` (`-1, 0..7`) and 50-Hz `PhoneCarriageClassifier.kt` (`banalservice.odometry`) with dwell hysteresis, orientation glance gating, sensor-absent fallback; add `PhoneCarriageClassifierTest.kt`. | `REQ-FIL-028.1` / `TST-FIL-028.1` | `*PhoneCarriageClassifierTest` |
| **2** | Add `PhoneCarriageMode` & `ACTIVITY_AND_CARRIAGE_MODE` translations (`values`, `de,es,fr,it,ja,nl,pl,pt` `strings_devices.xml`), `PhoneCarriageModeFormatter.kt`, `SensorType.ACTIVITY_AND_CARRIAGE_MODE` (ordinal `67`, `INTEGER`, `filteringPossible=false`, `'Internal unit:' dimensionless`), and wire `ActivityType.getSensorTypeArray()` + `DeviceType.getDeviceTypeList()` (`Rule 81`). | `REQ-FIL-028.2,.5` / `TST-FIL-028.2,.5` | `*PhoneCarriageModeFormatterTest`, `*SensorCategoryAndSelectorContractTest`, `*SensorTypeUnitsContractTest`, `*SensorFilterDefaultsTest`, `*TranslationParityTest` |
| **3** | Update `BarometricAltitudeFilter.kt` (buffer-preserving dynamic median window & slew), `SpeedAndLocationDevice_ATTFused.kt` (defensive `TYPE_STEP_DETECTOR` w/ `SDK_INT == 0` guard, classifier integration, smooth yaw/glance/`Q_GYRO`/chord/ZUPT adaptation), and `AltitudeAndSlopeDevice_ATTFused.kt` (mode-adaptive baro window/slew & `rBaro` via `MySensorManager`). | `REQ-FIL-028.1,.3` / `TST-FIL-028.1,.3` | `*BarometricAltitudeFilterTest`, `*SpeedAndLocationDevice_ATTFusedTest`, `*AltitudeAndSlopeDevice_ATTFusedTest`, `*UnicycleOdometryEkfTest`, `*KinematicAltitudeSlopeEkfTest` |
| **4** | Update `ErtsPostWorkoutRecomputer.kt` (`ALLOWED_CANONICAL_COLUMNS` + 1-Hz mode read + legacy kinematic fallback), `ExtendedRtsOdometrySmoother.kt`, and `ExtendedRtsAltitudeSlopeSmoother.kt` for dismount pinning & carriage scaling while preserving `:140`. | `REQ-FIL-028.4` / `TST-FIL-028.4` | `*ErtsPostWorkoutRecomputerTest`, `*ExtendedRtsOdometrySmootherTest`, `*ExtendedRtsAltitudeSlopeSmootherTest` |
| **5** | Sync `docs/architecture.md` (`Rule 8.8`), `docs/architecture/sensor_units.md`, `docs/system/` (`SYSREQ-TRK-010`, `system_context.md`, `system_architecture.md`, `system_software_traceability.md`), and contract test `67->68` expectations (`SensorCategoryAndSelectorContractTest`, `SensorTypeUnitsContractTest`); run full contract & governance suite (`REQ-PRO-049.1` `0` `src/main` reads). | `REQ-FIL-028.5` / `TST-FIL-028.5` | `*DetailedDesignParityContractTest`, `*SystemArchitectureDesignContractTest`, `*SystemLiveTrackingRequirementsContractTest`, `*ArchitectureConformanceContractTest`, `*MergedManifestFgsAuditTest`, `*PermissionOnboardingSheetContractTest`, `*SourceTextReadingAuditContractTest`, `verify_requirement_governance.py` |

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted JVM unit + contract regression across Steps 1–5 (`--rerun-tasks` before completion) + `:app:lintDebug` + `python3 tools/verify_requirement_governance.py --repo-root .` (`exit 0`).
* **Rollback**: Isolated on `feature/ATT-8890` (pre-commit working tree / clean revert against `sprint/2026-11-18_02_Spät` `14935efb`); DB schema untouched (`0` destructive migrations).
