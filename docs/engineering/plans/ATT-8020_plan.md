# Stage 3: Implementation Plan - ATT-8020: Export Raw Polar Sidecars & 1-Tap Aftermath Diagnostic Bundle Share

**Ticket**: [ATT-8020](https://rainerblind.atlassian.net/browse/ATT-8020) (`2 SP`) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.2` | **Branch**: `feature/ATT-8020`  
**Requirement Mapping**: `REQ-EXP-044` | **Test Mapping**: `TST-EXP-044`  
**Author**: `agent1` (`subagent_flash`, Role Rotation `Rule 7.1`) | **Stage 4 Implementer**: `agent2` (`subagent_pro`, `b5dd6b71`)

## 1. Problem Description & Traceability
- Enable exporting raw Polar sensor sidecars (`.polar_raw.csv.gz`, `ATT-8019`) via `ExportManager` to local public storage (MediaStore/Downloads), Dropbox, and Google Drive without failing standard exports when sidecars are absent.
- Provide a 1-tap Aftermath diagnostic bundle share sheet in Workout Details, packaging the raw sidecar, computed summary, laps, 1-Hz samples (with privacy-preserving GPS stripping), device/OS metadata, and user notes into a `.zip` dispatched via `FileProvider` (`Intent.ACTION_SEND`).
- Traceability: `REQ-EXP-044.1` -> `TST-EXP-044.1`, `TST-EXP-044.2`; `REQ-EXP-044.2` -> `TST-EXP-044.3`; `REQ-EXP-044.3` -> `TST-EXP-044.4`; `REQ-EXP-044.4` -> `TST-EXP-044.5`.

## 2. System Invariants & UI Consistency
1. **Graceful Skip Invariant**: Workouts lacking a `.polar_raw.csv.gz` sidecar file MUST export standard formats (`.fit`, `.tcx`, `.gpx`, `.csv`) cleanly; `RawSensorFileExporter`, `DropboxUploader`, and `GoogleDriveUploader` return success/skipped without throwing errors or halting export queues.
2. **Privacy GPS Stripping**: When "Include GPS Location" is unchecked (default), `SensorDiagnosticBundlePackager` strictly omits latitude and longitude coordinates from `samples_1hz.csv` and `summary.json`.
3. **Non-Blocking I/O**: Diagnostic ZIP creation runs exclusively on `Dispatchers.IO` and temporary files reside in `cache/diagnostics/`.
4. **FileProvider Security**: `file_paths.xml` declares `<cache-path name="diagnostics" path="diagnostics/" />`, granting scoped read URI permissions.
5. **Submodule Hygiene**: `ANT-Android-SDKs` submodule remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
6. **UI Consistency (`docs/design_guidelines.md` §1 & §5)**: `SensorDiagnosticShareSheet` uses standard `AppModalBottomSheet`, `Switch` toggle for GPS exclusion, `OutlinedTextField` for symptom notes, and primary `Button` for sharing; localized across all 9 locales in `strings_export.xml` without emojis (`Rule 38`); verified via Compose screenshot `docs/engineering/walkthroughs/img/ATT-8020_diagnostic_share_sheet.png` (`Rule 8.5`).

## 3. Proposed Architectural Changes
- **`FileFormat.java` & `ExportType.java`**: Add `RAW_SENSOR("raw_sensor", ".polar_raw.csv.gz", R.string.file_format_raw_sensor)` with MIME `"application/gzip"`. Add `RAW_SENSOR` to `STANDARD_FILE_FORMATS`, `ExportType.FILE`, `ExportType.DROPBOX`, and `ExportType.GOOGLE_DRIVE`.
- **`TrainingApplication.java` & `ExportSettingsDialog.kt`**: Add `SP_EXPORT_RAW_SENSOR` preference key, `exportToRawSensor()` / `setExportToRawSensor(boolean)` toggle, and switch control in `ExportSettingsDialog`.
- **`RawSensorFileExporter.kt`**: New exporter for `ExportType.FILE`. Checks `context.filesDir/raw_sensor/<fileBaseName>.polar_raw.csv.gz`, calls `copyFileToDownloads()`, or gracefully returns skipped if absent.
- **`DropboxUploader.java` & `GoogleDriveUploader.kt`**: Add graceful skip when `fileFormat == FileFormat.RAW_SENSOR` and local sidecar file does not exist.
- **`file_paths.xml`**: Add `<cache-path name="diagnostics" path="diagnostics/" />`.
- **`SensorDiagnosticBundlePackager.kt`**: Packages `cache/diagnostics/sensor_diagnostic_<fileBaseName>.zip` containing raw telemetry sidecar, `workout_summary.json`, `laps.json`, `samples_1hz.csv` (with GPS stripping), and `diagnostic_metadata.json` with user note.
- **`SensorDiagnosticShareSheet.kt` & Aftermath Wiring**: Expose "Report Raw Sensor Data" in `WorkoutHeader.kt` dropdown and `WorkoutDetailTabbedContent.kt` when `PolarRawTelemetryPruner.hasSidecar(context, fileBaseName)` is true; launches `Intent.ACTION_SEND` (support email to `rainer.blind@gmail.com`).

## 4. Atomic Implementation Sequence (Stage 4 Construction by `agent2` Pro)
1. **Step 1 (FileFormat, Preferences & 9-Locale Strings)**: Add `FileFormat.RAW_SENSOR`, update `ExportType`, wire `SP_EXPORT_RAW_SENSOR` in `TrainingApplication.java`, add toggle in `ExportSettingsDialog.kt`, add localized strings in `res/values*/strings_export.xml` across all 9 locales without emojis; test via `ExportSettingsDialogTest.kt` & `TranslationParityTest.kt` (`// TST-EXP-044.5`).
2. **Step 2 (RawSensorFileExporter & Cloud Uploaders)**: Implement `RawSensorFileExporter.kt` with `copyFileToDownloads()` and graceful skip; add graceful skip for absent sidecars in `DropboxUploader.java` and `GoogleDriveUploader.kt`; test via `RawSensorFileExporterTest.kt` (`// TST-EXP-044.1`), `DropboxUploaderRawSensorTest.kt`, and `GoogleDriveUploaderRawSensorTest.kt` (`// TST-EXP-044.2`).
3. **Step 3 (Diagnostic ZIP Packager & Privacy GPS Stripping)**: Implement `SensorDiagnosticBundlePackager.kt` on `Dispatchers.IO` with optional GPS stripping; add `<cache-path name="diagnostics" path="diagnostics/" />` in `file_paths.xml`; test via `SensorDiagnosticBundlePackagerTest.kt` (`// TST-EXP-044.3`) & `SensorDiagnosticBundlePrivacyTest.kt` (`// TST-EXP-044.4`).
4. **Step 4 (Aftermath UI, Screenshot & Architecture Docs)**: Implement `SensorDiagnosticShareSheet.kt`, wire menu entry in `WorkoutHeader.kt` and `WorkoutDetailTabbedContent.kt` gated by `hasSidecar()`; capture `docs/engineering/walkthroughs/img/ATT-8020_diagnostic_share_sheet.png` (Light & Dark) with 4-point visual inspection (`Rule 8.5`); update `docs/architecture.md` SWE.2 specification (`Rule 8.8`); test via `SensorDiagnosticShareSheetScreenshotTest.kt` (`// TST-EXP-044.5`).

## 5. Verification & Rollback Plan
- **Tier 1 Targeted Unit Test Command** *(all tests tagged `// TST-EXP-044.*`)*:
  `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.RawSensorFileExporterTest" --tests "com.atrainingtracker.trainingtracker.exporter.uploader.DropboxUploaderRawSensorTest" --tests "com.atrainingtracker.trainingtracker.exporter.uploader.GoogleDriveUploaderRawSensorTest" --tests "com.atrainingtracker.trainingtracker.ui.dialogs.SensorDiagnosticBundlePackagerTest" --tests "com.atrainingtracker.trainingtracker.ui.dialogs.SensorDiagnosticBundlePrivacyTest" --tests "com.atrainingtracker.trainingtracker.ui.dialogs.SensorDiagnosticShareSheetScreenshotTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
- **Governance & Rollback**: Validate via `python3 tools/verify_requirement_governance.py --check-code-orphans --check-req-description --verify-sys-trace --check-reverse-sys-trace`; fully isolated on `feature/ATT-8020` with zero database migrations.
