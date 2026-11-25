# Stage 3: Implementation Plan - ATT-8082: Migrate Strava Upload Pipeline from TCX to Native FIT

**Ticket**: [ATT-8082](https://rainerblind.atlassian.net/browse/ATT-8082) | **Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-25_01_Früh` (Sprint 480) | **Risk Tier**: `tier-a` (`2 SP`)
**Requirement Mapping**: `REQ-EXP-051` (`SYSREQ-DEX-001`, `SYSREQ-DEX-003`) | **Test Mapping**: `TST-EXP-051`
**Branch**: `feature/ATT-8082` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-25

---

## 1. Problem Description & Background
Migrate the Strava workout export and upload pipeline from legacy XML `.tcx` to native binary FIT 2.0 (`FitFileWriter.kt`), preserving full sensor telemetry (temperature, L/R pedal dynamics, `HrvMesg`, Bike Radar developer fields, Sport/SubSport), adding clean `"aTrainingTracker"` creator metadata, and guaranteeing zero failure for pre-upgrade queued `.tcx` uploads.

## 2. System Invariants, Upgrade Path & SWE.2 Boundaries
* **Upgrade-Path Behavior (`docs/coding_standards.md §5.6`)**: Existing users with offline Strava jobs queued before upgrading retain their `Strava/<base>.tcx` file; `StravaUploader.doExport()` resolves primary `.fit` first and falls back to non-empty `Strava/<base>.tcx` sending `data_type="tcx"`. Newly finished or re-exported workouts write `Strava/<base>.fit` and upload with `data_type="fit"`. Cropping/editing invalidates both `.fit` and any legacy `.tcx`.
* **Preserved Invariants**: `FileFormat.TCX` untouched; `BaseFileWriter.onFinished()` skips public `Downloads/` for `STRAVA`; `ExportManager.invalidateWorkoutExport()` preserves `files/raw_sensor/*`; all `StravaUploader.doUpdate()` syncs (name, gear, sport, commute/trainer/race, radar summary, segment PRs, `ATT-8100` photo download) untouched; `FitWorkoutParser` round-trip intact; `0` SQLite/UI/`strings*.xml` edits; `FitFileWriter.kt` & `StravaUploader.kt` stay `< 1,500` LoC; `docs/architecture.md` & `docs/system/system_architecture.md` (`SYS-IF-05` already models `Multipart FIT`) require zero structural updates.
* **UI Consistency**: No UI changes.

## 3. Proposed Architectural Changes (SWE.2)
1. **`FileFormat.java`** (`exporter/`): Change `STRAVA("Strava", ".fit", R.string.Strava, true)` and move `case STRAVA:` under `case FIT:` in `getMimeType()` -> `"application/vnd.ant.fit"`.
2. **`ExportManager.java`** (`exporter/`): Route `case STRAVA -> new FitFileWriter(context);` under `ExportType.FILE` in `getExporter()`. In `invalidateWorkoutExport()`, when `format == FileFormat.STRAVA`, also delete residual legacy `Strava/<fileBaseName>.tcx` if present (still strictly derived-only, preserving `raw_sensor/*`).
3. **`FitFileWriter.kt`** (`exporter/writer/`):
   * Add `CREATOR_PRODUCT_NAME = "aTrainingTracker"`, `MAX_FIT_SOFTWARE_VERSION = 655.34f`, and `internal fun parseSoftwareVersion(versionName: String? = BuildConfig.VERSION_NAME): Float` parsing `major[.minor]` clamped to `[0.0f, 655.34f]`.
   * Set `fileIdMesg.setProductName(CREATOR_PRODUCT_NAME)` and immediately write creator `DeviceInfoMesg` (`timestamp = DateTime(startDate)`, `deviceIndex = DeviceIndex.CREATOR`, `manufacturer = Manufacturer.DEVELOPMENT`, `product = 1`, `serialNumber = 1L`, `productName = CREATOR_PRODUCT_NAME`, `softwareVersion` if `> 0f`).
   * For `isStravaExport = (exportInfo.fileFormat == FileFormat.STRAVA)`: enforce selective-upload opt-out parity (`REQ-EXP-003`) by gating `HrvMesg` & session HR on `haveHR`, pedal dynamics & session power/NP/IF/TSS/work on `havePower`, session cadence on `haveCadence`, and session ascent/descent on `haveAltitude`. Standard `FileFormat.FIT` remains 100% unchanged.
4. **`StravaUploader.kt`** (`exporter/uploader/`):
   * Add `private const val FIT = "fit"`, `internal fun resolveUploadFile(exportInfo, baseDir): File` (primary `exportInfo.shortPath` `.fit`, falling back to non-empty `Strava/<base>.tcx` when `fileFormat == FileFormat.STRAVA`), and `internal fun resolveDataType(file): String` (`"tcx"` if `.tcx` case-insensitive, else `"fit"`).
   * Use `resolveUploadFile(exportInfo)` in `doExport()` and `addFormDataPart(DATA_TYPE, resolveDataType(file))` in `uploadToStrava()`. Update duplicate-bypass log text from `"Bypassing TCX upload."` to `"Bypassing file upload."`.

## 4. Step-by-Step Implementation Sequence (Post-Gate-3 Construction)
* **Step 1 (`FileFormat.java` & `ExportManager.java`)**: Update `FileFormat.STRAVA` ending/MIME, route `ExportManager.getExporter(FILE, STRAVA)` to `FitFileWriter`, and add legacy `Strava/<base>.tcx` cleanup inside `invalidateWorkoutExport()`.
* **Step 2 (`FitFileWriter.kt`)**: Add `parseSoftwareVersion`, `FileIdMesg.setProductName("aTrainingTracker")`, creator `DeviceInfoMesg` emission, and `isStravaExport` selective-upload gating for HR/HRV, power/pedal dynamics, cadence, and altitude summaries.
* **Step 3 (`StravaUploader.kt`)**: Add `resolveUploadFile()` legacy `.tcx` fallback and `resolveDataType()` dynamic `data_type` (`"fit"` / `"tcx"`) in `doExport()` / `uploadToStrava()`.
* **Step 4 (Unit & Integration Tests — `TST-EXP-051.1`..`.4`)**:
  * Update `BaseExporterDownloadsTest.kt:102` (`FileFormat.STRAVA.mimeType` -> `"application/vnd.ant.fit"`).
  * Extend `ExportManagerFitTest.kt` (`getExporter(FILE, STRAVA) is FitFileWriter`, `FileFormat.STRAVA.fileEnding == ".fit"`) and `ExportManagerTest.kt` (`invalidateWorkoutExport` purges both `.fit` and legacy `.tcx` while preserving `raw_sensor/*`).
  * Extend `FitFileWriterTest.kt` with Garmin FIT SDK `Decode` assertions for `FileIdMesg.productName`, `DeviceInfoMesg` creator fields + `parseSoftwareVersion` boundary cases, `RecordMesg.altitude`, and Strava selective-upload opt-out privacy.
  * Extend `StravaUploaderTest.kt` capturing OkHttp `MultipartBody` to verify `data_type="fit"`, `data_type="tcx"` legacy fallback, and primary `.fit` precedence; verify `CloudHealthConnectIntegrationTest.kt` multipart `.fit` flow and `FitLosslessRoundTripTest` compatibility.
* **Step 5 (Tier-1 Verification & Governance)**:
  * Run `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.migration.Fit*" --tests "com.atrainingtracker.trainingtracker.integration.CloudHealthConnectIntegrationTest" --tests "com.atrainingtracker.trainingtracker.build.SystemDataExchangeRequirementsContractTest" --tests "com.atrainingtracker.trainingtracker.build.ExceptionHandlingHygieneTest"`
  * Run `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-11-25_01_Früh` and `python3 tools/auto_gate.py --ticket ATT-8082 --tier tier-a --write-bundle`.

## 5. Verification & Rollback Plan
* **Verification**: Automated JVM/Robolectric FIT decode + OkHttp multipart contract tests verify exact binary messages and fallback logic (`TST-EXP-051.1`..`.5`); live Strava cloud barometric elevation behavior under `Manufacturer.DEVELOPMENT (255)` is explicitly deferred to Human PO verification (`TST-EXP-051.6`).
* **Rollback**: Isolated on `feature/ATT-8082`; full revert leaves `sprint/2026-11-25_01_Früh` untouched with zero DB schema impact.
