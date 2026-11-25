# Stage 2: Requirement & Test Specification - ATT-8082: Migrate Strava Upload Pipeline from TCX to Native FIT

**Ticket**: [ATT-8082](https://rainerblind.atlassian.net/browse/ATT-8082) | **Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-25_01_Früh` (Sprint 480) | **Risk Tier**: `tier-a`
**Requirement Mapping**: `REQ-EXP-051` | **Test Spec ID**: `TST-EXP-051` | **System Req**: `SYSREQ-DEX-001`, `SYSREQ-DEX-003`
**Branch**: `feature/ATT-8082` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-25

---

## 1. Requirement Specification (`REQ-EXP-051`)
* **Attributes**: **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration, On-Device Manual (live Strava barometric elevation check by Human PO) | **Localization Parity**: `N/A - 0 string changes`
* **Functional & Architectural Requirements**:
  1. *`FileFormat.STRAVA` Binary FIT Configuration (`REQ-EXP-051.1`)*: `FileFormat.STRAVA` SHALL use dir `"Strava"`, file ending `".fit"`, `isDerivedExportFile() == true`, and `getMimeType() == "application/vnd.ant.fit"` while keeping `FileFormat.TCX` (`".tcx"`, `"application/vnd.garmin.tcx+xml"`) unchanged.
  2. *`ExportManager` Routing & Stale Legacy Cleanup (`REQ-EXP-051.2`)*: `ExportManager.getExporter()` SHALL route `(ExportType.FILE, FileFormat.STRAVA)` to `FitFileWriter(context)` (keeping `(ExportType.COMMUNITY, FileFormat.STRAVA)` on `StravaUploader(context)`). `ExportManager.invalidateWorkoutExport()` SHALL delete both `Strava/<base>.fit` and any residual pre-upgrade `Strava/<base>.tcx` while strictly preserving `files/raw_sensor/*` (`REQ-EXP-048.2`).
  3. *`FitFileWriter` Creator Metadata & Strava Selective-Upload Privacy (`REQ-EXP-051.3`)*: `FitFileWriter` SHALL set `FileIdMesg.setProductName("aTrainingTracker")` and emit a creator `DeviceInfoMesg` (`timestamp = startDate`, `deviceIndex = DeviceIndex.CREATOR`, `manufacturer = Manufacturer.DEVELOPMENT`, `product = 1`, `serialNumber = 1L`, `productName = "aTrainingTracker"`, `softwareVersion` parsed from `BuildConfig.VERSION_NAME` clamped to `[0.0f, 655.34f]`). When `exportInfo.fileFormat == FileFormat.STRAVA`, selective-upload opt-outs (`haveHR`/`havePower`/`haveCadence`/`haveAltitude`) SHALL also gate `HrvMesg`, cycling pedal dynamics, and corresponding `SessionMesg` summary fields without altering `FileFormat.FIT` exports.
  4. *`StravaUploader` FIT Multipart Upload & Legacy `.tcx` Fallback (`REQ-EXP-051.4`)*: `StravaUploader.doExport()` SHALL resolve primary `Strava/<base>.fit` when present and non-empty, and fall back to existing non-empty `Strava/<base>.tcx` for pre-upgrade offline queues. `StravaUploader.uploadToStrava()` SHALL send multipart `data_type = "fit"` for `.fit` files and `data_type = "tcx"` when `file.name.endsWith(".tcx", ignoreCase = true)`, preserving all post-upload `doUpdate` sync (`REQ-EXP-008`, `REQ-EXP-009`, `REQ-EXP-010`, `REQ-EXP-013`, `REQ-EXP-021`, `REQ-RAD-007`, `REQ-EXP-050`).
* **Acceptance Criteria (Given-When-Then)**:
  * **C1 (Native FIT Export & Upload)**: *Given* a finished workout scheduled for Strava, *When* `ExportManager` exports and `StravaUploader` uploads to `/api/v3/uploads`, *Then* `FitFileWriter` writes `Strava/<base>.fit` and the multipart request sends `data_type = "fit"` with filename `<base>.fit`.
  * **C2 (Legacy `.tcx` Queue Fallback)**: *Given* a pre-upgrade queued `ExportInfo(base, STRAVA, COMMUNITY)` with only `Strava/<base>.tcx` on disk, *When* `StravaUploader.doExport()` executes, *Then* it resolves `Strava/<base>.tcx` and uploads with `data_type = "tcx"` without failing.
  * **C3 (FIT Creator Metadata & Selective Privacy)**: *Given* a workout exported via `FitFileWriter`, *When* decoded by Garmin FIT SDK `Decode`, *Then* `FileIdMesg.productName == "aTrainingTracker"`, a `DeviceInfoMesg` with `DeviceIndex.CREATOR`, `Manufacturer.DEVELOPMENT`, `productName == "aTrainingTracker"`, and valid `softwareVersion` is present, `RecordMesg.altitude` is preserved, `FitWorkoutParser` round-trips cleanly, and disabled Strava toggles omit corresponding HR/HRV/power/cadence/altitude metrics.
* **System Invariants**: `FileFormat.TCX` unchanged; `BaseFileWriter.onFinished()` skips public `Downloads/` for `STRAVA`; `raw_sensor/*` preserved on invalidation; `0` SQLite/UI/`strings*.xml` edits; `doUpdate` metadata/radar/PR/`ATT-8100` photo sync unchanged.

## 2. Test Specification (`TST-EXP-051`)
* **`TST-EXP-051.1` (`BaseExporterDownloadsTest`, `ExportManagerFitTest`, `ExportManagerTest`)**: Verify `FileFormat.STRAVA` ending `".fit"`, MIME `"application/vnd.ant.fit"`, `ExportManager.getExporter(FILE, STRAVA) is FitFileWriter`, `BaseFileWriter.onFinished()` skips `Downloads/` for `STRAVA`, and `invalidateWorkoutExport()` deletes both `Strava/<base>.fit` and residual `Strava/<base>.tcx` while preserving `raw_sensor/*`.
* **`TST-EXP-051.2` (`FitFileWriterTest`)**: Decode generated `.fit` via Garmin FIT SDK `Decode` + `MesgBroadcaster`; assert `FileIdMesg.productName == "aTrainingTracker"`, `DeviceInfoMesg` (`CREATOR`, `DEVELOPMENT`, `productName`, `softwareVersion` from `parseSoftwareVersion` incl. `"5.0.0-dev"`, `"5.12.3"`, `"999.99"` clamp, malformed fallback), `RecordMesg.altitude`, and `FileFormat.STRAVA` selective-upload opt-out omission (`HR`/`HRV`, `Power`/pedal dynamics, `Cadence`, `Altitude`).
* **`TST-EXP-051.3` (`StravaUploaderTest`)**: Capture OkHttp `MultipartBody` on `POST /api/v3/uploads`; assert `data_type="fit"` for `.fit` upload, `data_type="tcx"` when uploading `.tcx`, `doExport()` primary `.fit` preference over stale `.tcx`, and `doExport()` fallback to existing `Strava/<base>.tcx` when `.fit` is absent.
* **`TST-EXP-051.4` (`CloudHealthConnectIntegrationTest`, `FitLosslessRoundTripTest`, `FitWorkoutParserTest`)**: Verify end-to-end Robolectric `StravaUploader` multipart `.fit` upload + polling + `PUT /api/v3/activities/{id}` + SQLite ID persistence, and lossless FIT export-to-import round-trip compatibility with `DeviceInfoMesg` present.
* **`TST-EXP-051.5` (Tier-1 Regression)**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.migration.Fit*" --tests "com.atrainingtracker.trainingtracker.integration.CloudHealthConnectIntegrationTest"`.
* **`TST-EXP-051.6` (Human PO On-Device Live Strava Verification)**: Human PO uploads a real barometric ride from `aTrainingTracker` to live Strava and verifies activity attribution and whether Strava preserves recorded barometric altitude vs DEM override under `Manufacturer.DEVELOPMENT (255)`.

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-EXP-051.1` | Unit | `FileFormat`, `ExportManager`, `BaseFileWriter` | `REQ-EXP-051.1`, `.2`, `REQ-EXP-022`, `REQ-EXP-048` | Planned |
| `TST-EXP-051.2` | Unit (FIT Decode) | `FitFileWriter.doExport`, `parseSoftwareVersion` | `REQ-EXP-051.3`, `REQ-EXP-003` | Planned |
| `TST-EXP-051.3` | Unit (OkHttp) | `StravaUploader.doExport`, `uploadToStrava` | `REQ-EXP-051.4`, `REQ-EXP-021` | Planned |
| `TST-EXP-051.4` | Integration / Round-Trip | `StravaUploader`, `FitWorkoutParser` | `REQ-EXP-051.3`, `.4`, `REQ-MIG-053` | Planned |
| `TST-EXP-051.5` | Regression (Tier 1) | `exporter.*`, `migration.Fit*` | `REQ-PRO-001`, `REQ-EXP-050` | Planned |
| `TST-EXP-051.6` | On-Device Manual (PO) | Live Strava barometric elevation check | `REQ-EXP-051.3`, `.4` | Planned (PO) |
