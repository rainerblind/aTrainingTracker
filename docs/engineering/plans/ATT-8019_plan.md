# Stage 3: Implementation Plan - ATT-8019: Persist Raw Polar IMU/RR Telemetry in Streaming GZIP Sidecars with Rolling Retention

**Ticket**: [ATT-8019](https://rainerblind.atlassian.net/browse/ATT-8019) (`3 SP`) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.2` | **Branch**: `feature/ATT-8019`  
**Requirement Mapping**: `REQ-SET-117` | **Test Mapping**: `TST-SET-119`  
**Author**: `agent1` (`subagent_flash`, Role Rotation `Rule 7.1`) | **Stage 4 Implementer**: `agent2` (`subagent_pro`, `b5dd6b71`)

## 1. Problem Description & Traceability
- Persist high-frequency Polar PMD live telemetry (50–200 Hz `ACC`, 26–52 Hz `GYRO`, `HR`, `RR`, `ATT-8016`) and offline sync (`ATT-7918`) into streaming GZIP sidecar files (`files/raw_sensor/<fileBaseName>.polar_raw.csv.gz`, ~0.5–3.5 MB/h) outside `WorkoutSamples.db`.
- Provide self-describing metadata header, deterministic Kotlin parser (`PolarRawTelemetryReader`), workout cascade deletion (`WorkoutSummariesDatabaseManager.deleteWorkout`, `WorkoutRepository.deleteWorkoutCascade`), rolling retention pruning (`PolarRawTelemetryPruner`), and M3 Settings selector in `SensorsGpsFilterSection` across all 9 locales.
- Traceability: `REQ-SET-117.1` -> `TST-SET-119.1`; `REQ-SET-117.2` -> `TST-SET-119.2`; `REQ-SET-117.3` -> `TST-SET-119.3`; `REQ-SET-117.4` -> `TST-SET-119.4`, `TST-SET-119.5`.

## 2. System Invariants & UI Consistency
1. **1-Hz Database Invariance**: `WorkoutSamples.db` strictly retains 1-Hz sampling with zero raw IMU insertion and zero SQLite schema bumps.
2. **Non-Blocking BLE Invariance**: BLE GATT and PMD callbacks stream frames into a non-blocking bounded `Channel<RawTelemetryFrame>(capacity = 8192)` consumed on `Dispatchers.IO`; disk I/O never blocks GATT event loops or drops packets.
3. **Cascade Deletion**: Deleting a workout in `WorkoutSummariesDatabaseManager.deleteWorkout` or `WorkoutRepository.deleteWorkoutCascade` purges its associated `.polar_raw.csv.gz` sidecar file (`REQ-DAT-042`).
4. **Orphan Safety**: Aborted or empty recording sessions discard partial sidecar files, leaving zero 0-byte orphan files.
5. **Submodule Hygiene**: `ANT-Android-SDKs` submodule remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
6. **UI Consistency (`docs/design_guidelines.md` §1 & `TuningFormControls.kt`)**: In `SensorsGpsFilterSection.kt`, use `TuningSegmentedButtonRow` for `RawSensorRetentionPolicy` (`30 days` [default], `Keep all`, `7 days`, `Off`); localized across 9 locales in `strings_devices.xml` (`Rule 9.4`) without emojis (`Rule 38`); verified via Compose screenshot `docs/engineering/walkthroughs/img/ATT-8019_raw_telemetry_settings.png` (`Rule 8.5`).

## 3. Proposed Architectural Changes
- **`RawSensorRetentionPolicy.kt` & DataStore**: Enum (`DAYS_30`, `KEEP_ALL`, `DAYS_7`, `OFF`) persisted in `TuningPreferencesDataStore` with synchronous access in `SettingsDataStoreJavaHelper`.
- **`PolarRawTelemetryRecorder.kt`**: Coroutine-based buffered sidecar writer (`files/raw_sensor/<fileBaseName>.polar_raw.csv.gz`). Writes JSON header (`# metadata: {...}`) and monotonic CSV rows (`sensorTimestampNs,elapsedMs,channel,ax_mg,ay_mg,az_mg,gx_mdps,gy_mdps,gz_mdps,hr_bpm,rr_ms`). Bounded `Channel(capacity = 8192)` drained to `GZIPOutputStream` on `Dispatchers.IO`. Discards empty sessions.
- **`PolarRawTelemetryReader.kt`**: Deterministic parser reconstructing metadata and channels (`ACC`, `GYRO`, `HR`, `RR`) with resilient recovery for truncated/corrupted files.
- **`PolarRawTelemetryPruner.kt`**: Scans `raw_sensor/`, deletes sidecars older than configured retention threshold, and provides `deleteSidecar(context, fileBaseName)` for cascade deletion.
- **Service & Controller Wiring**: Wire `PolarOnlineStreamingBridge` and `PolarH10OfflineController` to feed `PolarRawTelemetryRecorder`; manage recorder lifecycle and trigger background pruning in `TrackerService.java`.
- **Settings UI**: Add `TuningSegmentedButtonRow` in `SensorsGpsFilterSection.kt`.

## 4. Atomic Implementation Sequence (Stage 4 Construction by `agent2` Pro)
1. **Step 1 (Retention Enum, DataStore & 9-Locale Strings)**: Add `RawSensorRetentionPolicy.kt`; wire `rawSensorRetentionPolicy` in `TuningPreferencesDataStore.kt` & `SettingsDataStoreJavaHelper.kt`; add localized strings in `res/values*/strings_devices.xml` across all 9 locales without emojis; test via `TuningPreferencesDataStoreTest.kt` & `TranslationParityTest.kt` (`// TST-SET-119.4`).
2. **Step 2 (Recorder & Reader `PolarRawTelemetryRecorder.kt` / `PolarRawTelemetryReader.kt`)**: Implement bounded channel GZIP streaming recorder and deterministic reader parser; test via `PolarRawTelemetryRecorderTest.kt` (`// TST-SET-119.1`) & `PolarRawTelemetryReaderTest.kt` (`// TST-SET-119.2`).
3. **Step 3 (Pruning & Deletion Cascade `PolarRawTelemetryPruner.kt`)**: Implement retention pruner and `deleteSidecar` helper; hook into `WorkoutSummariesDatabaseManager.deleteWorkout` and `WorkoutRepository.deleteWorkoutCascade`; test via `PolarRawTelemetryPrunerTest.kt` & `WorkoutSummariesDatabaseManagerTest.kt` (`// TST-SET-119.3`).
4. **Step 4 (Live Bridge, Offline Controller & TrackerService Wiring)**: Connect `PolarOnlineStreamingBridge` and `PolarH10OfflineController` to recorder; wire `TrackerService.startWorkout()` / `endWorkout()` for recorder lifecycle and background pruning; test via `PolarOnlineStreamingBridgeTest.kt` & `TrackerServiceResilienceTest.kt` (`// TST-SET-119.1`, `// TST-SET-119.3`).
5. **Step 5 (M3 Settings UI, Screenshot & Architecture Docs)**: Add `TuningSegmentedButtonRow` in `SensorsGpsFilterSection.kt`; capture `docs/engineering/walkthroughs/img/ATT-8019_raw_telemetry_settings.png` (Light & Dark) with 4-point visual inspection (`Rule 8.5`); update `docs/architecture.md` SWE.2 specification (`Rule 8.8`); test via `SensorsGpsFilterScreenshotTest.kt` (`// TST-SET-119.5`).

## 5. Verification & Rollback Plan
- **Tier 1 Targeted Unit Test Command** *(all tests tagged `// TST-SET-119.*`)*:
  `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryRecorderTest" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryReaderTest" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryPrunerTest" --tests "com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStoreTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
- **Governance & Rollback**: Validate via `python3 tools/verify_requirement_governance.py --check-code-orphans --check-req-description`; fully isolated on `feature/ATT-8019` with zero SQLite migrations.
