# Stage 2 Requirement & Test Specification — ATT-9613

- **Ticket**: `ATT-9613` (`2 SP`, `Bug`, `severity-major`, `tier-a`, Epic `ATT-281`) | **Target Release**: `V5.0.0` | **Risk Rating**: `HIGH`
- **Reserved IDs**: `REQ-EXP-048` (`[Planned]`), `TST-EXP-048` (`[Planned]`)
- **System Traceability (`SYS.2`/`SYS.3`)**: `Derived (SW-only)` — Internal `FileFormat` cache-classification and `ExportManager.invalidateWorkoutExport` file-cleanup guard refining `REQ-DAT-029.4` and protecting `REQ-EXP-044.1` / `REQ-EXP-045.3`–`.4` without altering external sensors, HALs, cloud REST APIs, or SQLite schemas.
- **Living-Doc Shards**: `docs/requirements/req_data_migration_export_part2.md` (`v1.0.41`), `docs/tests/tst_data_migration_export.md` (`v1.0.41`).
- **Localization Parity**: `N/A - 0 string changes` — pure exporter cache-invalidation bugfix with zero UI or `strings*.xml` edits.

## 1. Requirements Summary (`REQ-EXP-048` — Priority: `High`, Type: `Functional`, Verification: `Unit`)

| Sub-ID | Normative Contract (SHALL / MUST) |
|---|---|
| `REQ-EXP-048.1` | `FileFormat` SHALL declare an explicit per-constant boolean property `isDerivedExportFile()` that returns `true` strictly for SQLite-regenerable derived export formats (`CSV`, `GC`, `TCX`, `GPX`, `FIT`, `STRAVA`) and `false` for irreplaceable primary-capture sidecars (`RAW_SENSOR`) and non-file sync targets (`HEALTH_CONNECT`), while preserving all existing enum constants, `getDirName()`, `getFileEnding()`, `getUiNameId()`, `getMimeType()`, `STANDARD_FILE_FORMATS`, and `ONLINE_COMMUNITIES` unchanged. |
| `REQ-EXP-048.2` | `ExportManager.invalidateWorkoutExport(fileBaseName)` SHALL restrict local cache-file deletion strictly to formats where `format.isDerivedExportFile() && !format.getFileEnding().isEmpty()` (and only when `baseDir != null && !baseDir.getPath().isEmpty()`). It MUST NEVER delete any file inside `files/raw_sensor/` (including `<fileBaseName>.polar_raw.csv.gz`, `<fileBaseName>.phone_raw.csv.gz`, or `.tmp` recovery archives), while preserving deletion of all derived export caches (`CSV`, `GC`, `TCX`, `GPX`, `FIT`, `STRAVA`), WorkManager task cancellation, `ExportStatus.WAITING` database reset, and status broadcast (`REQ-DAT-029.4`). Authorized raw sidecar deletion SHALL remain restricted strictly to explicit workout deletion (`WorkoutRepository.deleteWorkoutCascade`, `WorkoutSummariesDatabaseManager.deleteWorkout`) and retention pruning (`PolarRawTelemetryPruner`). |

## 2. Traceability Matrix & Verification Plan (`TST-EXP-048`)

| Test ID | Target Test Class(es) | Requirement(s) | Key Verification Procedure |
|---|---|---|---|
| `TST-EXP-048.1` | `ExportManagerTest.kt` | `REQ-EXP-048.1`, `REQ-EXP-048.2` | Assert `FileFormat.isDerivedExportFile()` returns `true` for `CSV`, `GC`, `TCX`, `GPX`, `FIT`, `STRAVA` and `false` for `RAW_SENSOR` and `HEALTH_CONNECT`. Populate a real temp `filesDir` with derived files (`CSV/.csv`, `GC/.json`, `TCX/.tcx`, `GPX/.gpx`, `FIT/.fit`, `Strava/.tcx`) and raw sidecars (`raw_sensor/*.polar_raw.csv.gz`, `*.phone_raw.csv.gz`, `*.polar_raw.csv.gz.tmp`, `*.phone_raw.csv.gz.tmp`) plus a sentinel under `HealthConnect/<base>`; invoke `invalidateWorkoutExport(fileBaseName)` and verify all 6 derived files are deleted, all `raw_sensor/*` sidecars and `HealthConnect` sentinel remain intact with byte-identical contents, and WorkManager cancellation + `ExportStatus.WAITING` DB reset + broadcast still execute. |
| `TST-EXP-048.2` | `RawSensorFileExporterTest.kt` | `REQ-EXP-048.2` | Populate both `<fileBaseName>.polar_raw.csv.gz` and `<fileBaseName>.phone_raw.csv.gz` alongside derived export files in Robolectric `context.filesDir`, call `ExportManager(context).invalidateWorkoutExport(fileBaseName)` (simulating Polar deferred sync / crop / swim-recalc re-export), then invoke `RawSensorFileExporter(context).export(ExportInfo(fileBaseName, FileFormat.RAW_SENSOR, ExportType.FILE))` and assert both sidecars remain on disk and export succeeds (`"Successfully exported 2 raw sensor sidecars"` rather than graceful skip). |

## 3. Acceptance Criteria (Given-When-Then)

- **Given** a recorded workout with derived export files (`CSV`, `GC`, `TCX`, `GPX`, `FIT`, `STRAVA`) and raw sensor sidecars (`files/raw_sensor/<fileBaseName>.polar_raw.csv.gz`, `*.phone_raw.csv.gz`, and `.tmp` files), **When** `ExportManager.invalidateWorkoutExport(fileBaseName)` executes (during Polar H10/Verity Sense retry sync, workout cropping, or swim recalculation), **Then** all 6 derived export files are deleted, WorkManager jobs are cancelled, DB export statuses reset to `WAITING`, and **every** raw sensor sidecar file remains untouched with identical byte content.
- **Given** `ExportManager.invalidateWorkoutExport(fileBaseName)` has just executed on a workout with Polar and/or Phone raw sidecars, **When** `RawSensorFileExporter.export(...)` runs during subsequent re-export, **Then** the exporter finds and exports all preserved sidecars successfully instead of skipping due to deleted files.
- **Given** every constant in `FileFormat.values()`, **When** `isDerivedExportFile()` is queried, **Then** `CSV`, `GC`, `TCX`, `GPX`, `FIT`, and `STRAVA` return `true`, and `RAW_SENSOR` and `HEALTH_CONNECT` return `false`.

## 4. Chesterton's Fence & System Invariants

- **Original Requirement ID & Target**: `REQ-DAT-029.4` (`ExportManager.invalidateWorkoutExport`) in `docs/requirements/req_data_migration_export_part1.md`.
- **Historical Origin & Commit Trace**: `ATT-3274` (`6ac6e7aa`) & `ATT-3995`, authored before `FileFormat.RAW_SENSOR` (`ATT-8020` / `REQ-EXP-044`, `ATT-9348` / `REQ-EXP-045`).
- **Root Reason for Existing Formulation**: Pre-crop/pre-backfill cache invalidation needed to delete all regenerable local export caches so stale files are never uploaded or shared.
- **Preservation of Core Invariants**: Derived cache invalidation (`CSV`/`GC`/`TCX`/`GPX`/`FIT`/`STRAVA`), WorkManager cancellation, `ExportStatus.WAITING` reset, explicit workout cascade deletion (`WorkoutRepository.deleteWorkoutCascade`, `WorkoutSummariesDatabaseManager.deleteWorkout`), retention pruning (`PolarRawTelemetryPruner`), and `FileFormat.RAW_SENSOR` public constants (`dirName`, `fileEnding`, `mimeType`) remain 100% intact. Zero SQLite/UI/`strings*.xml` edits; `ANT-Android-SDKs` untouched at `160000`.
