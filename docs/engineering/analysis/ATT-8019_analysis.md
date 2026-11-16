# Stage 1 Analysis: ATT-8019 — Persist Raw Polar IMU/RR Telemetry in Streaming GZIP Sidecars with Rolling Retention

**Ticket**: [ATT-8019](https://rainerblind.atlassian.net/browse/ATT-8019) (`3 SP`) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.2` | **Branch**: `feature/ATT-8019` | **Author**: `agent1` (`subagent_flash`, Role Rotation `Rule 7.1`)  

## 1. Problem Statement & Motivation
- Live Polar PMD streaming (`ATT-8016`, 50–200 Hz `ACC` / 26–52 Hz `GYRO` + `RR`/`HR`) and offline exercise sync (`ATT-7918` / `ATT-7924`) generate high-frequency telemetry (~720,000 frames/hour at 200 Hz).
- Inserting high-frequency samples into 1-Hz `WorkoutSamples.db` would bloat SQLite by tens of megabytes per workout and degrade query performance, while discarding raw streams prevents algorithm R&D (cadence, dynamics, stroke/turn detection) and user edge-case diagnosis.
- Streaming raw telemetry into an external GZIP-compressed sidecar file (`.polar_raw.csv.gz`, ~0.5–3.5 MB/hour) with a Kotlin replay reader (`PolarRawTelemetryReader`), cascade deletion on workout removal, and a user-configurable rolling retention policy (`30 days` [default], `Keep all`, `7 days`, `Off`) captures lossless raw data with zero SQLite overhead.

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Codebase Architecture Audit**:
  1. *Live Stream Ingestion (`PolarOnlineStreamingBridge.kt:482, 513`)*: Dispatches to `ImuFrameListener` (`L94-97`) and `RrBeatListener`. No persistent recorder listens to or archives raw frame channels.
  2. *Offline Telemetry Sync (`PolarH10OfflineController.kt:346`, `PolarVeritySenseOfflineController.kt`)*: Backfill logic extracts RR and optical HR into SQLite tables but does not write raw sidecar archives.
  3. *Workout Lifecycle (`TrackerService.java:1244, 2437`)*: Tracks `mBaseFileName` (`yyyy-MM-dd_HHmmss`). Starting/stopping sessions lacks sidecar file arming, flush, and shutdown hooks.
  4. *Cascade Deletion Gap (`WorkoutSummariesDatabaseManager.java:1832`, `WorkoutRepository.kt:1136`)*: Purges `WorkoutSummaries.db`, `WorkoutSamples.db`, `Laps.db`, and photos, but omits sidecar files (`raw_sensor/<fileBaseName>.polar_raw.csv.gz`).
  5. *Storage Retention & Settings Gap (`TuningPreferencesDataStore.kt`, `SensorsGpsFilterSection.kt`)*: Lacks raw sensor retention setting and 9-locale strings.
- **Independent Peer Review & Verification (ATT-3534)**:
  - Validated that BLE GATT callbacks must never perform blocking file I/O: writes must be buffered through an asynchronous channel/queue onto `Dispatchers.IO`.
  - PO Confirmation (2026-11-15): ATT-8009 (26 Hz swim IMU) remains deferred; sidecar persistence must target active streams (`ATT-8016` live PMD + `ATT-7918` offline RR) with extensible schema for future IMU sync.

## 3. User Scope Grounding (ATT-1250)
- **In-Scope**: (1) `PolarRawTelemetryRecorder` buffered GZIP CSV sidecar writer (`files/raw_sensor/<fileBaseName>.polar_raw.csv.gz`) with self-describing metadata header and timestamped channels (`ACC`, `GYRO`, `HR`, `RR`); (2) `PolarRawTelemetryReader` replay parser for tests and diagnostics; (3) Lifecycle wiring in `TrackerService`, `PolarOnlineStreamingBridge`, and `PolarH10OfflineController`; (4) Cascade deletion in `WorkoutSummariesDatabaseManager.deleteWorkout` and `WorkoutRepository.deleteWorkoutCascade`; (5) Rolling retention policy (`RawSensorRetentionPolicy`: `DAYS_30`, `KEEP_ALL`, `DAYS_7`, `OFF`) in `TuningPreferencesDataStore` and Compose UI in `SensorsGpsFilterSection` with 9-language localization; (6) `PolarRawTelemetryPruner` background pruning.
- **Out-of-Scope**: ATT-8009 Verity Sense offline swim IMU sync (deferred); modifying 1-Hz `WorkoutSamples.db` SQLite schema; cloud exports (Strava, Health Connect); modifying `ANT-Android-SDKs`.
- **Complexity & Risk (`REQ-PRO-029`, `Rule 7.1`)**: `3 SP` confirmed. Medium risk. Role Rotation applies (`agent1` Flash: S1, S3, S5; `agent2` Pro: S2, S4, Gate 5).

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Requirement Target**: Net-new `REQ-SET-117` (`TST-SET-119`).
- **Interaction with Existing Invariants**: Preserves `REQ-SET-112`/`REQ-SET-116` 50–200 Hz live streaming and cadence/dynamics DSP; preserves `REQ-DAT-042` cross-database cascade deletion contracts; preserves `REQ-UI-262` slider/toggle ergonomics in `SensorsGpsFilterSection`.

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Net-new `REQ-SET-117` (Polar Raw Telemetry GZIP Sidecar Persistence & Storage Retention).
- **Affected Architecture Elements**: New `PolarRawTelemetryRecorder.kt`, `PolarRawTelemetryReader.kt`, `PolarRawTelemetryPruner.kt`, `RawSensorRetentionPolicy.kt`; modified `PolarOnlineStreamingBridge.kt`, `PolarH10OfflineController.kt`, `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`, `TuningPreferencesDataStore.kt`, `SensorsGpsFilterSection.kt`, `res/values*/strings_devices.xml` (9 locales), `docs/architecture.md`, `docs/requirements/req_settings_profiles.md`.
- **Verification Impact & Test Surface**: Unit tests for recorder (buffered GZIP writes, header validation, empty session file cleanup), reader (replay parsing, corrupt gzip handling), pruner (30d/7d/all/off retention, cascade deletion), settings persistence, and `TranslationParityTest`.
- **Risk Assessment & Invariants**: Asynchronous queue on `Dispatchers.IO` prevents BLE callback stalling; automated pruning and empty-file discard prevent disk exhaustion.

## 6. Architectural Strategy & High-Level Solution
- **Sidecar File Path**: `context.filesDir/raw_sensor/<fileBaseName>.polar_raw.csv.gz`.
- **Header & Columns**: `# metadata: {"schemaVersion":1,"appVersion":"...","sensorModel":"...","streamMode":"LIVE|OFFLINE","accRateHz":200,"gyroRateHz":52,"sportId":...}` followed by `sensorTimestampNs,elapsedMs,channel,ax_mg,ay_mg,az_mg,gx_mdps,gy_mdps,gz_mdps,hr_bpm,rr_ms`.
- **Settings UI & Rule 8.5**: In `SensorsGpsFilterSection.kt`, `TuningSegmentedButtonRow` displays concise labels fitting 360dp widths across all 9 locales (`30 days`/`Keep all`/`7 days`/`Off` -> `30 Tage`/`Alle`/`7 Tage`/`Aus` -> `30 días`/`Todos`/`7 días`/`Desactivado`, etc.). Stage 4 warrants a `Rule 8.5` PNG screenshot and 4-point visual inspection.

## 7. System Invariants & Risk Assessment
- **Core Invariants**: (1) `WorkoutSamples.db` strictly retains 1-Hz sampling; (2) BLE GATT loops never block on disk I/O; (3) Workout deletion guarantees deletion of its `.polar_raw.csv.gz` file; (4) Empty recording sessions leave zero orphan files; (5) `ANT-Android-SDKs` submodule remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
- **Risk Rating**: **MEDIUM** (Multi-threaded I/O, file compression, cascade deletion hooks, and multi-locale UI, isolated from SQLite schemas).
