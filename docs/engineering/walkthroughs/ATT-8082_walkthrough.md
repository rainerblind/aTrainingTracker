# Stage 5 Walkthrough: ATT-8082 - Migrate Strava Workout Upload Pipeline from TCX to Native Binary FIT

**Ticket**: [ATT-8082](https://rainerblind.atlassian.net/browse/ATT-8082) | **Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-25_01_Früh` (Sprint 480) | **Risk Tier**: `tier-a` (`2 SP`)
**Requirement**: `REQ-EXP-051` (`[Verified]`) | **Test Spec**: `TST-EXP-051` (`[Verified]`) | **Gate 3**: `ATT-9703` (`PASS`)
**Branch**: `feature/ATT-8082` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-25

---

## 1. Summary of Implemented Changes
1. **[FileFormat.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/FileFormat.java)**: Updated `FileFormat.STRAVA` file ending from `".tcx"` to `".fit"` and `getMimeType()` to `"application/vnd.ant.fit"` while keeping `FileFormat.TCX` (`".tcx"`, `"application/vnd.garmin.tcx+xml"`) and `isDerivedExportFile()` unchanged.
2. **[ExportManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/ExportManager.java)**: Routed `(ExportType.FILE, FileFormat.STRAVA)` to `new FitFileWriter(context)` and extended `invalidateWorkoutExport()` to purge residual pre-upgrade `Strava/<fileBaseName>.tcx` alongside `Strava/<fileBaseName>.fit` while preserving `files/raw_sensor/*` (`REQ-EXP-048.2`).
3. **[FitFileWriter.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriter.kt)** (`1,252/1,500` LoC):
   * Set `FileIdMesg.setProductName("aTrainingTracker")` and emitted creator `DeviceInfoMesg` (`DeviceIndex.CREATOR`, `Manufacturer.DEVELOPMENT`, `product = 1`, `serialNumber = 1L`, `productName = "aTrainingTracker"`, bounded `softwareVersion` via `parseSoftwareVersion(BuildConfig.VERSION_NAME)` clamped to `[0.0f, 655.34f]`).
   * Applied Gate 3 Watchpoints 1 & 2 for `FileFormat.STRAVA` selective-upload opt-outs (`REQ-EXP-003`): skips `WorkoutRrRepository` (`HrvMesg` interleaved + trailing) & session HR when `!haveHR`, gates `RecordMesg`/accumulator pedal dynamics (`LapMesg`/`SessionMesg`) & session power/NP/IF/TSS/work when `!havePower`, gates session cadence when `!haveCadence`, and gates session ascent/descent when `!haveAltitude`. Standard `FileFormat.FIT` remains unchanged.
4. **[StravaUploader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt)** (`1,028/1,500` LoC):
   * Added `resolveUploadFile()` preferring non-empty primary `Strava/<base>.fit` and falling back to non-empty legacy `Strava/<base>.tcx` for pre-upgrade offline queues (rejecting 0-byte files).
   * Added `resolveDataType()` submitting multipart `data_type = "fit"` for `.fit` and `"tcx"` for legacy `.tcx`, preserving all post-upload `doUpdate()` metadata, radar summary, segment PRs, and `ATT-8100` photo download hooks intact.

## 2. Verification Evidence & Test Results
* **Tier-1 Targeted Unit, Integration & Contract Suite**: **`251/251` PASS across `39` suites** (`0` fail, `0` err, `0` skip, `0` new compiler warnings):
  * `BaseExporterDownloadsTest` (`5/5`), `ExportManagerFitTest` (`9/9`), `ExportManagerTest` (`4/4`), `FitFileWriterTest` (`19/19`), `FitFileWriterHrvTest` (`2/2`), `FitFileWriterRadarTest` (`2/2`), `StravaUploaderTest` (`11/11`), `StravaUploaderNamingTest` (`32/32`), `StravaPhotoDownloadTest` (`12/12`), `StravaUploaderRadarSummaryTest` (`5/5`), `StravaUploaderRaceWorkoutTypeTest` (`3/3`), `CloudHealthConnectIntegrationTest` (`3/3`), `FitLosslessRoundTripTest` (`7/7`), `FitSportMappingTest` (`6/6`), `SystemDataExchangeRequirementsContractTest` (`4/4`), `ExceptionHandlingHygieneTest` (`8/8`), `RepositoryEngineCleanlinessContractTest` (`9/9`), plus all remaining `exporter.*` suites.
* **Machine Evidence Bundle**: `docs/engineering/test_evidence/tickets/ATT-8082/` (`evidence_summary.json` + SHA-256 `manifest.json`).
* **Governance Verification**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-11-25_01_Früh` EXIT `0`; `ANT-Android-SDKs` submodule intact at `160000`.
* **Non-UI Classification**: Zero Compose/Activity/Fragment/Manifest/resource/Service changes (no UI screenshots required).

## 3. Human PO Field Verification Focus (`Rule 7.6` / `TST-EXP-051.6`)
* Autonomous JVM tests verify exact binary Garmin FIT SDK decode (`FileIdMesg`, creator `DeviceInfoMesg`, `RecordMesg.altitude`, `HrvMesg`, pedal dynamics, radar developer fields) and multipart `data_type = "fit"` / legacy `.tcx` fallback without calling external `www.strava.com`.
* **PO Acceptance Focus**: On a live Strava upload of a barometric ride from `aTrainingTracker`, confirm clean app attribution and observe whether Strava preserves recorded barometric/EKF altitude vs DEM override under `Manufacturer.DEVELOPMENT (255)` (and whether a follow-up for Strava barometric device-ID registration is desired).
