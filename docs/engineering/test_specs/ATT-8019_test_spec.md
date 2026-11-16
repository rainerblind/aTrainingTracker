# Stage 2: Requirement & Test Specification - ATT-8019: Persist Raw Polar IMU/RR Telemetry in Streaming GZIP Sidecars with Rolling Retention

**Ticket**: [ATT-8019](https://issue-tracker/ATT-8019) (`3 SP`) | **Parent Epic**: `ATT-5910` | **Target Release**: `V5.0.0` | **Sprint**: `2026-47.2`  
**Requirement Mapping**: `REQ-SET-117` | **Test Spec ID**: `TST-SET-119` | **Author**: `agent2` (`subagent_pro`, Role Rotation)

---

## 1. Requirement Specification (`REQ-SET-117`)
- **Attributes**: Priority `Medium` | Type `Functional` | Method `Unit / Integration` | Status `Planned`
- **REQ-SET-117.1 (Buffered Streaming GZIP Sidecar Writer)**: `PolarRawTelemetryRecorder` SHALL persist live PMD (`ATT-8016`) and offline sync (`ATT-7918`) sensor frames directly into `files/raw_sensor/<fileBaseName>.polar_raw.csv.gz` outside `WorkoutSamples.db`. Recording SHALL stream via non-blocking bounded coroutine buffer `Channel<RawTelemetryFrame>(capacity = 8192)` on `Dispatchers.IO` so BLE GATT and PMD callbacks never stall or drop frames due to disk I/O. Empty or aborted sessions SHALL delete the partial file, leaving zero orphan files.
- **REQ-SET-117.2 (Self-Describing Header & Monotonic Schema)**: Sidecar file SHALL start with metadata header `# metadata: {"schemaVersion":1,"appVersion":"...","sensorModel":"...","firmwareVersion":"...","streamMode":"LIVE|OFFLINE","accRateHz":...,"gyroRateHz":...,"sportId":...,"poolLength_m":...}` followed by column header `sensorTimestampNs,elapsedMs,channel,ax_mg,ay_mg,az_mg,gx_mdps,gy_mdps,gz_mdps,hr_bpm,rr_ms`. Preserves monotonic hardware timestamps (`sensorTimestampNs`) and elapsed time (`elapsedMs`). Deterministic parser `PolarRawTelemetryReader` SHALL parse metadata and channels with graceful handling of corrupt/truncated archives.
- **REQ-SET-117.3 (Workout Cascade Deletion & Rolling Retention Pruning)**: Deleting a workout via `WorkoutSummariesDatabaseManager.deleteWorkout` and `WorkoutRepository.deleteWorkoutCascade` SHALL hook into sidecar storage and delete the corresponding `.polar_raw.csv.gz` file (`REQ-DAT-042`). `PolarRawTelemetryPruner` SHALL enforce user-configurable rolling retention (`RawSensorRetentionPolicy`: `DAYS_30` [default], `KEEP_ALL`, `DAYS_7`, `OFF`) off the main thread, pruning unpinned expired sidecars while preserving workout database summaries.
- **REQ-SET-117.4 (M3 Settings Selector & 9-Locale Parity)**: `SensorsGpsFilterSection` SHALL provide an M3 segmented selector for `RawSensorRetentionPolicy` (`30 days` [default], `Keep all`, `7 days`, `Off`). All strings across all 9 locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) SHALL be defined in `strings_devices.xml` without emojis (`Rule 38`, `ATT-5400`). Stage 4 SHALL generate and inspect verification screenshot `docs/engineering/walkthroughs/img/ATT-8019_raw_telemetry_settings.png` (`Rule 8.5`).

### Acceptance Criteria & System Invariants
- *Given* active Polar PMD streaming (ACC/GYRO/HR/RR) or offline sync, *When* processed by `PolarRawTelemetryRecorder`, *Then* frames are buffered via `Channel(capacity = 8192)` and written to `files/raw_sensor/<fileBaseName>.polar_raw.csv.gz` without blocking GATT callbacks, while `WorkoutSamples.db` strictly retains 1-Hz sampling.
- *Given* an existing `.polar_raw.csv.gz` sidecar file, *When* read via `PolarRawTelemetryReader`, *Then* metadata JSON header and all frame channels parse deterministically with exact timestamp fidelity.
- *Given* an athlete deletes a workout from History or repository, *When* `WorkoutSummariesDatabaseManager.deleteWorkout` executes, *Then* the sidecar `.polar_raw.csv.gz` file is deleted concurrently.
- *Given* sidecars exceeding the retention threshold (`30 days`, `7 days`, or `Off`), *When* `PolarRawTelemetryPruner` executes, *Then* unpinned expired sidecars are deleted off the main thread while workout database summaries are preserved.
- *Given* the athlete opens Sensors & GPS Settings across any of the 9 locales, *When* `SensorsGpsFilterSection` renders, *Then* the retention selector displays localized labels (`30 days`, `Keep all`, `7 days`, `Off`) without UI clipping (`ATT-8019_raw_telemetry_settings.png`).
- **Invariants**: `WorkoutSamples.db` strictly retains 1-Hz sampling with zero schema modifications; BLE GATT loops never block on file I/O; empty sessions leave zero orphan files; `ANT-Android-SDKs` submodule remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`; Role Rotation (`agent2` Pro: S2, S4, Gate 5).

---

## 2. Test Specification (`TST-SET-119`)
1. `TST-SET-119.1` (`PolarRawTelemetryRecorderTest.kt`): Validates GZIP-compressed CSV writes via non-blocking bounded `Channel(capacity = 8192)` on `Dispatchers.IO` under simulated 200 Hz burst load without callback stalling. Confirms empty/aborted workout sessions discard the file leaving zero orphan files.
2. `TST-SET-119.2` (`PolarRawTelemetryReaderTest.kt`): Validates `# metadata: {...}` JSON header deserialization, column schema parsing, monotonic timestamp fidelity (`sensorTimestampNs`, `elapsedMs`), multi-channel reconstruction (`ACC`, `GYRO`, `HR`, `RR`), and graceful handling of corrupt/truncated archives.
3. `TST-SET-119.3` (`PolarRawTelemetryPrunerTest.kt`, `WorkoutSummariesDatabaseManagerTest.kt`, `WorkoutRepositoryTest.kt`): Validates cascade deletion of sidecars on workout deletion (`REQ-DAT-042`), and off-thread retention pruning across `DAYS_30`, `DAYS_7`, `KEEP_ALL`, and `OFF` preserving workout database records.
4. `TST-SET-119.4` (`TuningPreferencesDataStoreTest.kt`, `TranslationParityTest.kt`): Validates `RawSensorRetentionPolicy` persistence and defaults in `TuningPreferencesDataStore`, plus full 9-locale parity across `strings_devices.xml` (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) with zero emojis.
5. `TST-SET-119.5` (`SensorsGpsFilterScreenshotTest.kt`): Validates M3 segmented button row rendering in `SensorsGpsFilterSection` on 360dp width across all 9 locales, generating `docs/engineering/walkthroughs/img/ATT-8019_raw_telemetry_settings.png` for 4-point visual inspection (`Rule 8.5`).

---

## 3. Traceability Matrix
| Req ID | Description | Test Case ID | Verification |
| :--- | :--- | :--- | :--- |
| `REQ-SET-117.1` | Buffered GZIP sidecar writer, bounded Channel(8192), orphan cleanup | `TST-SET-119.1` | Unit / Integration |
| `REQ-SET-117.2` | Self-describing JSON metadata header & deterministic reader parser | `TST-SET-119.2` | Unit |
| `REQ-SET-117.3` | Workout cascade deletion & off-thread retention pruning (30d/7d/all/off) | `TST-SET-119.3` | Integration |
| `REQ-SET-117.4` | M3 Settings selector, DataStore persistence & 9-locale strings | `TST-SET-119.4`, `TST-SET-119.5` | Unit / Screenshot |
