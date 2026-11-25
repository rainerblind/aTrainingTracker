# Stage 1 Analysis: ATT-8082 - Migrate Strava Workout Upload Pipeline from TCX to Native Binary FIT

**Ticket**: [ATT-8082](https://rainerblind.atlassian.net/browse/ATT-8082) | **Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-25_01_Früh` (Sprint 480) | **Risk Tier**: `tier-a` (`2 SP`)
**Branch**: `feature/ATT-8082` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-25

---

## 1. Problem Statement & Motivation
`FileFormat.STRAVA`, `ExportManager.getExporter()`, and `StravaUploader` currently generate and upload Strava workouts as XML `.tcx` (`data_type = "tcx"`), even though `FitFileWriter.kt` already encodes compact binary FIT 2.0 files via Garmin FIT SDK (`21.141.0`). Migrating Strava uploads to native `.fit` preserves ambient temperature, L/R cycling pedal balance, beat-to-beat `HrvMesg` RR intervals, Bike Radar developer fields, and exact `Sport`/`SubSport` enums; cuts payload size ~5–10×; and replaces raw phone-model TCX `<Creator><Name>` attribution with clean `"aTrainingTracker"` FIT creator metadata.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Live Codebase Call-Site Audit**:
  * `FileFormat.java:33,88-92`: `STRAVA("Strava", ".tcx", R.string.Strava, true)` and `getMimeType()` returns `"application/vnd.garmin.tcx+xml"`.
  * `ExportManager.java:90,329-342`: `ExportType.FILE` maps `case STRAVA -> new TCXFileWriter(context)`; `invalidateWorkoutExport()` deletes `Strava/<base><fileEnding>`.
  * `StravaUploader.kt:80-81,187,233-250`: `doExport()` resolves `File(baseDir, exportInfo.shortPath)` and `uploadToStrava()` hardcodes `addFormDataPart("data_type", "tcx")`.
  * `FitFileWriter.kt:112,328-334`: Emits `FileIdMesg` (`ACTIVITY`, `Manufacturer.DEVELOPMENT`, `product=1`, `serialNumber=1L`, `timeCreated`) without `productName` and emits zero `DeviceInfoMesg`.
* **Independent Peer Review & Verification (ATT-3534)**:
  1. **Kotlin File Path**: Ticket mentions `FitFileWriter.java`; live code migrated it to `FitFileWriter.kt` (`ATT-7906`, `1,194/1,500` LoC).
  2. **Pre-Upgrade Offline Queue Hazard**: `ExportInfo.toJson()` persists `fileFormat="STRAVA"` without extension. After upgrading `fileEnding` to `".fit"`, a pre-upgrade WorkManager upload job resolves `shortPath="Strava/<base>.fit"`, which does not exist when only `Strava/<base>.tcx` was generated before upgrade. `StravaUploader.doExport()` MUST resolve primary `.fit` first and fall back to non-empty `Strava/<base>.tcx`, while `uploadToStrava()` derives `data_type` (`"tcx"` vs `"fit"`) from the resolved filename.
  3. **Stale Pre-Crop `.tcx` Hazard**: `ExportManager.invalidateWorkoutExport()` must delete legacy `Strava/<base>.tcx` alongside `Strava/<base>.fit` so editing/cropping a pre-upgrade workout cannot leave a stale `.tcx` that the fallback could upload.
  4. **Garmin FIT SDK `software_version` Bounds**: SDK `DeviceInfoMesg` encodes `software_version` as `UINT16` (`scale = 100`, max `655.34f`; `0xFFFF` is invalid). Parsing `BuildConfig.VERSION_NAME` (`"5.0.0-dev"` -> `5.0f`) must clamp to `[0.0f, 655.34f]`.
  5. **Strava Selective Upload Privacy (`REQ-EXP-003`)**: `BaseFileWriter.getHeaderData()` applies `uploadStravaGPS/Altitude/HR/Power/Cadence` to `have*` flags. `FitFileWriter` gates `RecordMesg` fields, but for `FileFormat.STRAVA` must also gate `HrvMesg`/session HR (`haveHR`), pedal dynamics/session power/NP/IF/TSS/work (`havePower`), session cadence (`haveCadence`), and session ascent/descent (`haveAltitude`).
  6. **Existing Test Regression Caught**: `BaseExporterDownloadsTest.kt:102` asserts `FileFormat.STRAVA.mimeType == "application/vnd.garmin.tcx+xml"` and must be updated to `"application/vnd.ant.fit"`.

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**: `FileFormat.STRAVA` (`.fit` / `application/vnd.ant.fit`), `ExportManager` Strava exporter routing + legacy `.tcx` invalidation cleanup, `FitFileWriter.kt` creator metadata (`FileIdMesg.productName` + `DeviceInfoMesg(CREATOR)`) + Strava selective-upload gating, `StravaUploader.kt` `data_type="fit"` + legacy `.tcx` fallback, unit/integration tests, and living docs (`REQ-EXP-051`/`TST-EXP-051`).
* **Out-of-Scope**: Standalone `FileFormat.TCX` export (`TCXFileWriter.kt` untouched), UI/Compose/localization/SQLite schema changes, spoofing third-party Garmin/Wahoo FIT manufacturer IDs. Live Strava cloud barometric elevation behavior with `Manufacturer.DEVELOPMENT (255)` is a Human PO Verification Focus (no unauthenticated external upload in autonomous tests).

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-EXP-022` (`FileFormat.STRAVA` MIME type), `REQ-EXP-008` / `REQ-EXP-021` (Strava upload payload format), and `REQ-EXP-003` (Strava selective upload) via net-new `REQ-EXP-051` in `docs/requirements/req_data_migration_export_part2.md`.
* **Historical Origin & Commit Trace**: `REQ-EXP-022` (`ATT-4013`, `06990c88`), `REQ-EXP-008` (`ATT-902`/`ATT-1105`), `REQ-EXP-021` (`ATT-4012`), `FitFileWriter.kt` (`ATT-1827`/`ATT-7906`, `e40d6a85`).
* **Root Reason for Existing Formulation**: `STRAVA` predated `FitFileWriter` and reused `TCXFileWriter` (sharing `application/vnd.garmin.tcx+xml` and `data_type="tcx"`).
* **Preservation of Core Invariants**: `FileFormat.TCX` stays `.tcx` / `application/vnd.garmin.tcx+xml`; `BaseFileWriter.onFinished()` still excludes `STRAVA` from public `Downloads/` (`REQ-EXP-022`); pre-upload duplicate discovery (`REQ-EXP-008`), retry/backoff (`REQ-EXP-021`), `doUpdate` metadata/radar (`REQ-RAD-007`), segment PRs (`REQ-EXP-010`), and `ATT-8100` photo sync (`REQ-EXP-050`) remain 100% intact.

## Change Impact Analysis
* **Affected Requirements**: Net-new `REQ-EXP-051` (refining `REQ-EXP-003`, `REQ-EXP-008`, `REQ-EXP-021`, `REQ-EXP-022`); `SYS.2`/`SYS.3` check: `SYSREQ-DEX-001`, `SYSREQ-DEX-003`, and `SYS-IF-05` (`docs/system/system_architecture.md:107,175` already specifies `Multipart FIT` for `/api/v3/uploads` — zero `SYS.2`/`SYS.3` doc changes required).
* **Affected Architecture Elements**: `FileFormat.java`, `ExportManager.java`, `FitFileWriter.kt`, `StravaUploader.kt` (zero new subsystems, services, DB schemas, UI screens, or `strings*.xml` keys; `docs/architecture.md` unchanged).
* **Verification Impact & Test Surface**: Net-new `TST-EXP-051`; new/updated JVM & Robolectric tests in `BaseExporterDownloadsTest`, `ExportManagerFitTest`, `ExportManagerTest`, `FitFileWriterTest`, `StravaUploaderTest`, `CloudHealthConnectIntegrationTest`, plus `FitLosslessRoundTripTest` and `FitWorkoutParserTest`.
* **Risk Assessment & Invariants**: **HIGH (`tier-a`)** — touches cloud workout upload and binary FIT serialization. Mitigated by legacy `.tcx` queue fallback, Garmin FIT SDK round-trip decode assertions, selective-upload privacy assertions, and preserving all post-upload `doUpdate` flows and LoC ceilings (`<= 1,500`).
