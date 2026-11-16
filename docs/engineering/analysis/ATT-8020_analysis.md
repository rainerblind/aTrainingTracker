# Stage 1 Analysis: ATT-8020 — Export Raw Polar Sensor Sidecars via ExportManager & 1-Tap Aftermath Diagnostic Bundle Share

**Ticket**: [ATT-8020](https://rainerblind.atlassian.net/browse/ATT-8020) (`2 SP`) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.2` | **Branch**: `feature/ATT-8020` | **Author**: `agent1` (`subagent_flash`, Role Rotation `Rule 7.1`)  

## 1. Problem Statement & Motivation
- `ATT-8019` introduced raw high-frequency Polar IMU/RR streaming sidecars (`files/raw_sensor/<fileBaseName>.polar_raw.csv.gz`) outside SQLite.
- Currently, sidecars are trapped in internal storage. Power users cannot export them alongside standard workout files (to local storage, Dropbox, or Google Drive via `ExportManager`), and athletes reporting metric discrepancies (e.g., missed swim turns, stroke misclassification, running cadence glitches) have no direct mechanism to extract or report raw sensor logs for diagnostics without developer tooling or adb.
- Integrating raw sidecar export into `ExportManager` and providing a 1-tap Aftermath "Share / Report Sensor Diagnostic Bundle" (`.zip` packaging raw telemetry, computed laps/summary, optional GPS stripping, and user comments via `FileProvider`) bridges R&D analysis and customer support reproduction.

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Codebase Architecture Audit**:
  1. *`FileFormat.java` & `ExportType.java`*: Lacks `RAW_SENSOR` (`raw_sensor`, `.polar_raw.csv.gz`). `ExportManager` has no exporter mapping to dispatch existing sidecars to MediaStore / Public Downloads.
  2. *Cloud Uploaders (`DropboxUploader.java:53`, `GoogleDriveUploader.kt:57`)*: Both uploaders resolve `new File(getBaseDirFile(context), filename)` where `filename = exportInfo.shortPath`. For `FileFormat.RAW_SENSOR`, `shortPath` maps to `"raw_sensor/<fileBaseName>.polar_raw.csv.gz"`, matching `context.filesDir`! Uploaders must gracefully skip workouts where no sidecar was recorded without throwing fatal errors.
  3. *Export Settings (`ExportSettingsDialog.kt`, `TrainingApplication.java`)*: Lacks raw sensor export toggle (`SP_EXPORT_RAW_SENSOR`).
  4. *`file_paths.xml`*: Only exposes `cache/images/` and `files/backups/`. Sharing diagnostic zip bundles from `cache/diagnostics/` requires adding `<cache-path name="diagnostics" path="diagnostics/" />`.
  5. *Aftermath Diagnostic Sharing (`WorkoutDetailTabbedContent.kt`, `WorkoutHeader.kt`)*: `WorkoutDiagnosticBottomSheet` only handles caught runtime recording exceptions (`recordingErrors`). No dialog or sheet exists to package raw sensor telemetry, 1-Hz samples, laps, and user notes for sensor bug reporting.
- **Independent Peer Review & Verification (ATT-3534)**:
  - Verified that raw sidecars are already compressed (`.csv.gz`), so copying or uploading introduces zero CPU compression penalty.
  - User privacy: GPS coordinates must be optionally stripped from 1-Hz sample logs within the diagnostic zip bundle before sharing.

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. `FileFormat.RAW_SENSOR` in `FileFormat.java` (`raw_sensor`, `.polar_raw.csv.gz`, `application/gzip`).
  2. `ExportManager` wiring: `RawSensorFileExporter` copying existing sidecars to public downloads; graceful no-op when sidecar is absent.
  3. `DropboxUploader` & `GoogleDriveUploader` support for `FileFormat.RAW_SENSOR`.
  4. Preference toggle in `TrainingApplication` & `ExportSettingsDialog`.
  5. `SensorDiagnosticBundlePackager`: Pure Kotlin packager creating `cache/diagnostics/sensor_diagnostic_<fileBaseName>.zip` containing raw sidecar, computed summary, laps, 1-Hz samples (with GPS strip toggle), device/OS metadata, and user notes.
  6. Compose 1-tap Aftermath UI sheet/dialog (`SensorDiagnosticShareSheet.kt`) accessible from Workout Details menu/header when a sidecar exists, launching `FileProvider` + `Intent.ACTION_SEND` (support email to `rainer.blind@gmail.com`).
  7. 9-language localization parity across `values*/strings_export.xml` and `values*/strings_devices.xml`.
- **Out-of-Scope Non-Goals**: Modifying `.fit`/`.tcx` export pipelines; automatic upload of raw sensor files to Strava (Strava API rejects `.polar_raw.csv.gz`); modifying `ANT-Android-SDKs`.
- **Complexity & Risk (`REQ-PRO-029`, `Rule 7.1`)**: `2 SP` confirmed (Standard). Medium risk.

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Requirement Target**: Net-new `REQ-EXP-044` (`TST-EXP-044`) in `docs/requirements/req_data_migration_export.md` under `SYSREQ-DEX-001` & `SYSREQ-DEX-004`.
- **Interactions**: Extends `ExportManager` (`REQ-EXP-001`, `REQ-EXP-022`) and `FileProvider` (`REQ-EXP-018`) without modifying existing FIT/TCX/GPX/CSV writers. Preserves `ATT-8019` sidecar path and retention invariants.

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Net-new `REQ-EXP-044` (Raw Sensor Sidecar Export & Diagnostic Bundle Share).
- **Affected Architecture Elements**:
  - New classes: `RawSensorFileExporter.kt`, `SensorDiagnosticBundlePackager.kt`, `SensorDiagnosticShareSheet.kt`.
  - Modified classes: `FileFormat.java`, `ExportType.java`, `ExportManager.java`, `TrainingApplication.java`, `DropboxUploader.java`, `GoogleDriveUploader.kt`, `ExportSettingsDialog.kt`, `WorkoutHeader.kt`, `WorkoutDetailTabbedContent.kt`.
  - Resources: `file_paths.xml`, `strings_export.xml` / `strings_devices.xml` (9 locales).
  - Documentation: `docs/architecture.md`, `docs/requirements/req_data_migration_export.md`, `docs/tests/tst_data_migration_export.md`.
- **Verification Impact & Test Surface**: Unit tests for `RawSensorFileExporterTest`, `SensorDiagnosticBundlePackagerTest` (ZIP layout, GPS exclusion, metadata JSON), `ExportManagerRawSensorTest`, `DropboxUploaderRawSensorTest`, `GoogleDriveUploaderRawSensorTest`, and `TranslationParityTest`.
- **Risk Assessment & Invariants**: Missing sidecars on non-Polar workouts must be gracefully ignored (exit code success / skipped). Temporary zip bundles in `cache/diagnostics/` must be cleaned up to prevent cache bloat.

## 6. Architectural Strategy & High-Level Solution
- **Sidecar Source**: `context.filesDir.resolve("raw_sensor/${fileBaseName}.polar_raw.csv.gz")`.
- **File Export**: `RawSensorFileExporter` uses `copyFileToDownloads` to copy the `.polar_raw.csv.gz` file to public downloads.
- **Diagnostic Bundle**: `ZipOutputStream` streams `raw_telemetry.polar_raw.csv.gz`, `summary.json`, `laps.json`, `samples_1hz.csv`, and `diagnostic_metadata.json` into a single archive.
- **Share Intent**: Intent with `ACTION_SEND`, `type = "application/zip"`, `EXTRA_STREAM` granting read URI permission via `FileProvider.getUriForFile`.

## 7. System Invariants & Risk Assessment
- **Core Invariants**: (1) Workouts lacking raw sidecars export standard formats with zero errors; (2) Diagnostic ZIP creation never blocks the UI thread; (3) GPS stripping toggle strictly purges latitude/longitude from shared diagnostic samples; (4) `ANT-Android-SDKs` remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
- **Risk Rating**: **LOW-MEDIUM** (Standard file export extension, zip packaging, and Compose sheet; no database schema or Bluetooth protocol changes).
