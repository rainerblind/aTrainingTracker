# System Requirements Specification: Data Exchange and Cloud Integration

**Document ID**: `DOC-SYS-DEX`  
**Version**: 1.2.3  
**ASPICE Process Areas**: SYS.2 (System Requirements Analysis, BP1–BP3)  
**Requirement Reference**: `REQ-PRO-055` (`REQ-PRO-055.1`, `REQ-PRO-055.2`)  
**Test Reference**: `TST-PRO-055` (`TST-PRO-055.1`–`TST-PRO-055.3`)  
**Author**: Antigravity Autonomous Engineering Pair  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

---

## 1. Scope & Operational Context

This document establishes the formal, black-box system requirements (`SYSREQ-DEX-001` through `SYSREQ-DEX-008`) for data exchange, telemetry export/import, Polar BLE PFTP/PMD offline backfill, external DEM elevation queries, cloud services integration, and disaster recovery in **aTrainingTracker**.

These requirements satisfy ASPICE 4.0 **SYS.2 (System Requirements Analysis)** Base Practices:
* **SYS.2 BP1**: *Specify system requirements* – Define functional, non-functional, security, and interface data requirements.
* **SYS.2 BP2**: *Structure system requirements* – Organize requirements by domain (Export, Import, Cloud Sync, Backup, Security).
* **SYS.2 BP3**: *Analyze system requirements* – Ensure verifiability, privacy safeguards, and complete traceability.

---

## 2. System Requirements Catalog

### SYSREQ-DEX-001: Multi-Format Telemetry Export Interoperability
* **Title**: Multi-Format Telemetry Export Interoperability
* **Statement**: The system SHALL export completed endurance workouts into standard open formats without loss of captured metric fidelity:
  1. *Flexible & Interoperable Data Transfer (FIT)*: Garmin FIT 2.0 binary container (`.fit`, `FileFormat.FIT`, MIME `application/vnd.ant.fit`) via `FitFileWriter.kt` with file header, device info, record mesgs (timestamp, lat/lng, distance, speed, HR, cadence, power, temperature, elevation), lap mesgs, and session summaries.
  2. *Garmin Training Center XML (TCX)*: Standard XML schema (`.tcx`, `FileFormat.TCX`, MIME `application/vnd.garmin.tcx+xml`) via `TCXFileWriter.kt` including trackpoint extensions (`TPX`).
  3. *GPS Exchange Format (GPX)*: Open geospatial standard (`.gpx`, `FileFormat.GPX`, MIME `application/gpx+xml`) via `GPXFileWriter.java` containing trackpoints, elevation, and Garmin TrackPointExtension elements.
  4. *Tabular Plaintext (CSV)*: Comma-separated values (`.csv`, `FileFormat.CSV`, MIME `text/csv`) via `CSVFileWriter.java` exporting raw 1-Hz discrete telemetry rows for external spreadsheet or scientific analysis.
  5. The export pipeline SHALL enforce canonical distance (`DistanceFormatter`), elevation (`AltitudeFormatter`), and speed/pace formatting across all export serialization paths.
* **Rationale**: Athletes require vendor-neutral mobility of their training data across desktop software (GoldenCheetah), third-party ecosystems, and long-term offline archives.
* **Priority**: High
* **Verification Method**: Test & Demonstration (`SystemDataExchangeRequirementsContractTest`, `FitFileWriterTest.kt`, `TCXRoundTripTest.kt`)
* **Traced Software Components**: `ExportManager.java`, `FileFormat.java`, `FitFileWriter.kt`, `TCXFileWriter.kt`, `GPXFileWriter.java`, `CSVFileWriter.java`

### SYSREQ-DEX-002: Multi-Format Workout Ingestion & Collision Deduplication
* **Title**: Multi-Format Workout Ingestion & Collision Deduplication
* **Statement**: The system SHALL ingest historical workout recordings in standard formats (FIT, TCX, GPX) and bulk export metadata (CSV):
  1. Parse discrete workout trackpoints, lap markers, sensor telemetry, and workout metadata from FIT, TCX, and GPX files into the application SQLite database (`WorkoutSummaries.db`, `WorkoutSamples.db`) via dedicated parsers (`FitWorkoutParser.kt`, `GpxWorkoutParser.kt`, `TcxWorkoutParser.kt`).
  2. Parse athlete activity metadata from bulk export `activities.csv` manifests via `StravaCsvParser.kt`.
  3. Perform collision detection and deduplication against existing sessions based on start timestamp, duration, and trajectory spatial bounding box to prevent duplicate workout entries.
  4. Support batch import workflows (e.g. Strava bulk export archive ingestion) with non-blocking background execution (`LegacyImportEngine.kt`, `StravaBulkExportImporter.kt`).
* **Rationale**: Athletes switching from other devices or platforms must be able to restore historical training logs without creating duplicated entries.
* **Priority**: High
* **Verification Method**: Test (`SystemDataExchangeRequirementsContractTest`, `LegacyImportEngine.kt`, `StravaBulkExportImporter.kt`)
* **Traced Software Components**: `LegacyImportEngine.kt`, `StravaBulkExportImporter.kt`, `WorkoutDataMapper.kt`, `WorkoutSamplesDatabaseManager.java`, `StravaCsvParser.kt`

### SYSREQ-DEX-003: Cloud Activity Synchronization & Rate Governance
* **Title**: Cloud Activity Synchronization & Rate Governance (Strava)
* **Statement**: The system SHALL support cloud workout synchronization via the Strava v3 REST API:
  1. Authorize athlete access via OAuth2 authorization code flow with secure refresh token exchange and variant-scoped callback redirection (`strava.${applicationId}`).
  2. Perform asynchronous multipart workout file uploads (`/api/v3/uploads`) with background status polling and upload success confirmation.
  3. Enforce Strava API rate limit compliance (15-minute and 24-hour request windows) to prevent API throttling or quota exhaustion.
  4. Synchronize athlete equipment (bikes, shoes) and auto-assign gear to matched workout activity types (`StravaEquipmentSyncWorker.kt`).
  5. Synchronize athlete routes (`StravaRoutesSyncWorker.kt`) and starred segments (`StravaSegmentsSyncWorker.kt`) via background workers.
* **Rationale**: Social sharing, route exploration, and cloud analytics on platforms like Strava are primary use cases for endurance athletes.
* **Priority**: High
* **Verification Method**: Test & Analysis (`SystemDataExchangeRequirementsContractTest`, `StravaUploader.kt`, `StravaEquipmentSyncWorker.kt`, `StravaRoutesSyncWorker.kt`, `StravaSegmentsSyncWorker.kt`)
* **Traced Software Components**: `StravaUploader.kt`, `StravaHelper.kt`, `StravaUploadDbHelper.java`, `StravaEquipmentSyncWorker.kt`, `StravaRoutesSyncWorker.kt`, `StravaSegmentsSyncWorker.kt`, `AuthCredentialsStore.kt`

### SYSREQ-DEX-004: Cloud Storage Synchronization & Automatic Backup
* **Title**: Cloud Storage Synchronization & Automatic Backup (Dropbox & Google Drive)
* **Statement**: The system SHALL support automated and manual cloud archiving of completed workout files and full database backups:
  1. *Dropbox Integration*: Integrate Dropbox API v2 (`com.dropbox.core:dropbox-core-sdk:8.0.2`, `DropboxUploader.java`) with OAuth2 PKCE authorization, uploading workout files to user-configured subfolders and periodic `.attbackup` archives to `/Backups/`.
  2. *Google Drive Integration*: Integrate Google Drive REST API v3 (`GoogleDriveUploader.kt`, `GoogleDriveClient.kt`) with Google Sign-In authorization, uploading workout files and synchronizing application backups to `aTrainingTracker/Backups/` (`GoogleDriveBackupManager.kt`).
  3. Cloud synchronization tasks SHALL execute within background `WorkManager` workers, automatically retrying upon transient network dropouts without impeding the live tracking engine.
* **Rationale**: Seamless off-device cloud replication protects athletes against hardware loss, phone damage, or accidental database corruption.
* **Priority**: High
* **Verification Method**: Test (`SystemDataExchangeRequirementsContractTest`, `DropboxBackupManager.kt`, `GoogleDriveBackupManager.kt`)
* **Traced Software Components**: `DropboxUploader.java`, `DropboxBackupManager.kt`, `GoogleDriveUploader.kt`, `GoogleDriveBackupManager.kt`, `GoogleDriveClient.kt`, `BackupWorker.kt`

### SYSREQ-DEX-005: Full Application State Backup & Atomic Recovery Pipeline
* **Title**: Full Application State Backup & Atomic Recovery Pipeline (`.attbackup`)
* **Statement**: The system SHALL provide an atomic, self-contained application backup and restoration pipeline:
  1. Bundle all application databases (`WorkoutSummaries.db`, `WorkoutSamples.db`, `WorkoutClusters.db`, etc.), XML shared preferences, and associated photo attachments into a single compressed `.attbackup` archive (`BackupManager.BACKUP_FILENAME = "aTrainingTracker_backup.attbackup"`).
  2. Verify that destination storage has at least 1.5x the required uncompressed free disk space prior to initiating backup generation.
  3. Execute SQLite WAL flush and checkpointing (`PRAGMA wal_checkpoint(FULL)`) prior to database copying to guarantee ACID snapshot consistency.
  4. Write the backup to a temporary staging file (`aTrainingTracker_backup.attbackup.tmp`) and atomically rename upon completion to prevent corrupted partial archives.
  5. Validate ZIP entry paths upon restoration via `SecureZipExtractor` to protect against Zip Slip path traversal vulnerabilities (`REQ-SEC-004`).
* **Rationale**: Total disaster recovery and phone-to-phone migration require an airtight, atomic snapshot of all local athlete history.
* **Priority**: High
* **Verification Method**: Test (`SystemDataExchangeRequirementsContractTest`, `BackupManagerTest`, `BackupImportSecurityTest`)
* **Traced Software Components**: `BackupManager.kt`, `SecureZipExtractor.kt`, `BackupRestoreViewModel.kt`, `DatabaseConnectionManager.kt`, `DatabaseSchemaRegistry.kt`

### SYSREQ-DEX-006: Credential Protection, Security Isolation & Decommissioned Boundaries
* **Title**: Credential Protection, Security Isolation & Decommissioned Platform Boundaries
* **Statement**: The system SHALL enforce strict data privacy, credential protection, and interface lifecycle boundaries:
  1. *Credential Protection*: Sensitive user authentication tokens, OAuth refresh secrets, and cloud credentials stored in `auth_credentials_prefs.xml` SHALL be strictly excluded from `.attbackup` archives and Android Auto Backup (`@xml/full_backup_content`, `full_backup_content.xml`, and `data_extraction_rules.xml`).
  2. *Legacy Credential Purging*: Any legacy credential keys found in default shared preferences SHALL be automatically migrated and purged (`AuthCredentialsStore.migrateLegacyCredentials()`).
  3. *Health Connect Gating*: Integration with Android Health Connect API (`ExerciseSessionRecord`, `HeartRateRecord`) and HRV/RHR biometric ingestion (`HealthConnectHrvImporter.kt`) SHALL enforce dynamic runtime user permission consent before transmitting or reading samples.
  4. *Decommissioned Platform Boundaries*: Permanently retired third-party cloud interfaces (Runkeeper HealthGraph API and TrainingPeaks API) SHALL be maintained as inert schema placeholders strictly to preserve historical SQLite database schema integrity and prevent migration regressions.
* **Rationale**: Exported archives and cloud services must never leak athlete credentials, and deprecated cloud platforms must not trigger runtime communication errors.
* **Priority**: High
* **Verification Method**: Static Audit & Test (`SystemDataExchangeRequirementsContractTest`, `BackupManagerCredentialExclusionTest`, `BackupRulesXmlValidationTest`)
* **Traced Software Components**: `AuthCredentialsStore.kt`, `full_backup_content.xml`, `data_extraction_rules.xml`, `HealthConnectManager.kt`, `HealthConnectHrvImporter.kt`, `WorkoutSummariesDatabaseManager.java`

### SYSREQ-DEX-007: Polar BLE PFTP/PMD Offline Recording, Clock Sync & Post-Workout Backfill
* **Title**: Polar BLE PFTP/PMD Offline Recording, Clock Sync & Post-Workout Backfill
* **Statement**: The system SHALL manage Polar BLE SDK `8.6.0` (`com.github.polarofficial:polar-ble-sdk:8.6.0`) offline flash recording and post-workout telemetry backfill over Polar File Transfer Protocol (`0000feee-0000-1000-8000-00805f9b34fb`, `SERVICE_POLAR_PFTP`) and Polar Measurement Data (`fb005c80-02e7-f387-1cad-8acd2d8df0c8`, `SERVICE_POLAR_PMD`):
  1. Synchronize sensor device clocks, arbitrate GATT connections (`PolarOfflineGattCoordinator.kt`), and arm/stop internal flash recording on Polar H10 and Verity Sense sensors (`PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `PolarOnlineStreamingBridge.kt`).
  2. Execute asynchronous foreground-protected post-workout stop and PFTP backfill bounded by `BACKFILL_TIMEOUT_MS = 30_000L` (`30s`) for Polar H10 and `BACKFILL_TIMEOUT_MS = 45_000L` (`45s`, `FETCH_TIMEOUT_MS = 30_000L`) for Polar Verity Sense prior to `ExportManager` export generation (`REQ-TRK-084`, `REQ-TRK-091`), persisting raw telemetry diagnostic sidecars (`PolarRawTelemetryRecorder.kt`, `REQ-EXP-044`).
* **Rationale**: Athletes lose RF connectivity during swimming or gym sessions; offline sensor flash recording and deterministic `30s` (H10) / `45s` (Verity Sense) post-workout backfill guarantee gap-free FIT/TCX/Strava exports.
* **Priority**: High
* **Verification Method**: Test & Analysis (`SystemDataExchangeRequirementsContractTest`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `TrackerService.java`)
* **Traced Software Components**: `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `PolarOfflineGattCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `PolarOfflineCapabilityStore.kt`, `PolarRawTelemetryRecorder.kt`, `WorkoutPostProcessingCoordinator.kt`, `WorkoutPostProcessingService.kt`, `TrackerService.java`, `BluetoothConstants.java`

### SYSREQ-DEX-008: External Elevation Model (Open-Meteo DEM) & Cloud Telemetry Enrichment
* **Title**: External Elevation Model (Open-Meteo DEM) & Cloud Telemetry Enrichment
* **Statement**: The system SHALL query external Digital Elevation Model (DEM) reference altitudes and enrich local terrain caches with graceful offline fallback:
  1. Retrieve reference terrain elevation profiles via HTTPS GET from Open-Meteo (`https://api.open-meteo.com/v1/elevation`, `ElevationService.kt`) and persist spatial lookup caches in SQLite (`ElevationCacheDatabaseManager.kt`).
  2. Calibrate barometric/GNSS altitude profiles and heal corrupted historical elevation tracks (`REQ-DAT-014`, `REQ-CLM-009`) without blocking live 1-Hz tracking when network connectivity is unavailable.
* **Rationale**: Smartphone barometers require sea-level reference calibration and historical GPS tracks benefit from authoritative DEM elevation healing.
* **Priority**: Medium
* **Verification Method**: Test (`SystemDataExchangeRequirementsContractTest`, `ElevationService.kt`, `ElevationCacheDatabaseManager.kt`)
* **Traced Software Components**: `ElevationService.kt`, `ElevationCacheDatabaseManager.kt`, `ElevationResult.kt`

---

## 3. System Requirements & Software Code Grounding Matrix

| System Requirement | Primary Capabilities | Traced Software Classes | Grounded Constants / Parameters |
| :--- | :--- | :--- | :--- |
| `SYSREQ-DEX-001` | Multi-format telemetry export (FIT, TCX, GPX, CSV) | `REQ-DAT-012`, `REQ-DAT-013`, `REQ-DAT-017`, `REQ-DAT-018`, `REQ-DAT-023`, `REQ-DAT-027`, `REQ-DAT-028`, `REQ-DAT-030`, `REQ-DAT-031`, `REQ-DAT-032`, `REQ-DAT-037`, `REQ-DAT-038`, `REQ-DAT-039`, `REQ-DAT-040`, `REQ-DAT-041`, `REQ-DAT-042`, `REQ-DAT-043`, `REQ-EXP-001`, `REQ-EXP-022`, `REQ-EXP-042`, `REQ-EXP-044` | `FileFormat.FIT` (`.fit`), `FileFormat.TCX` (`.tcx`), `FileFormat.GPX` (`.gpx`), `FileFormat.CSV` (`.csv`) |
| `SYSREQ-DEX-002` | Multi-format workout ingestion & deduplication | `REQ-DAT-001`, `REQ-DAT-014`, `REQ-DAT-019`, `REQ-DAT-021`, `REQ-DAT-025`, `REQ-DAT-034`, `REQ-MIG-018`, `REQ-MIG-022`, `REQ-MIG-024`, `REQ-MIG-033`, `REQ-MIG-037`, `REQ-MIG-039`, `REQ-MIG-046`, `REQ-MIG-051`, `REQ-MIG-053` | Timestamp and bounding box deduplication, FIT/TCX/GPX track parsers, Strava `activities.csv` manifest parser |
| `SYSREQ-DEX-003` | Strava OAuth2 activity, route & segment sync | `REQ-EXP-002`, `REQ-EXP-003`, `REQ-EXP-005`, `REQ-EXP-006`, `REQ-EXP-007`, `REQ-EXP-008`, `REQ-EXP-009`, `REQ-EXP-010`, `REQ-EXP-011`, `REQ-EXP-012`, `REQ-EXP-013`, `REQ-EXP-014`, `REQ-EXP-015`, `REQ-EXP-016`, `REQ-EXP-017`, `REQ-EXP-019`, `REQ-EXP-021`, `REQ-EXP-023`, `REQ-EXP-043`, `REQ-EXT-001`, `REQ-EXT-002`, `REQ-EXT-003`, `REQ-EXT-004`, `REQ-EXT-005`, `REQ-EXT-006`, `REQ-EXT-007`, `REQ-EXT-008`, `REQ-EXT-010`, `REQ-RAD-007` | `/api/v3/uploads`, `strava.${applicationId}`, rate limit backoff |
| `SYSREQ-DEX-004` | Dropbox & Google Drive cloud backup & workout sync | `REQ-DAT-020`, `REQ-DAT-022`, `REQ-DAT-024`, `REQ-DAT-029`, `REQ-EXP-018`, `REQ-EXP-044`, `REQ-MIG-002`, `REQ-MIG-003`, `REQ-MIG-007`, `REQ-MIG-008`, `REQ-MIG-009`, `REQ-MIG-010`, `REQ-MIG-019`, `REQ-MIG-028`, `REQ-MIG-030`, `REQ-MIG-031`, `REQ-MIG-034`, `REQ-MIG-035`, `REQ-MIG-036`, `REQ-MIG-038`, `REQ-MIG-040`, `REQ-MIG-049`, `REQ-MIG-050`, `REQ-MIG-052` | `/Backups/`, `aTrainingTracker/Backups/`, `dropbox-core-sdk:8.0.2` |
| `SYSREQ-DEX-005` | Full state `.attbackup` atomic archiving & Zip Slip defense | `REQ-DAT-004`, `REQ-DAT-005`, `REQ-DAT-006`, `REQ-DAT-007`, `REQ-DAT-008`, `REQ-DAT-009`, `REQ-DAT-011`, `REQ-DAT-015`, `REQ-DAT-016`, `REQ-DAT-020`, `REQ-DAT-026`, `REQ-DAT-033`, `REQ-DAT-035`, `REQ-DAT-036`, `REQ-DAT-044`, `REQ-MIG-001`, `REQ-MIG-004`, `REQ-MIG-005`, `REQ-MIG-006`, `REQ-MIG-011`, `REQ-MIG-012`, `REQ-MIG-013`, `REQ-MIG-014`, `REQ-MIG-015`, `REQ-MIG-016`, `REQ-MIG-017`, `REQ-MIG-020`, `REQ-MIG-021`, `REQ-MIG-023`, `REQ-MIG-025`, `REQ-MIG-026`, `REQ-MIG-027`, `REQ-MIG-029`, `REQ-MIG-032`, `REQ-MIG-041`, `REQ-MIG-042`, `REQ-MIG-043`, `REQ-MIG-044`, `REQ-MIG-045`, `REQ-MIG-047`, `REQ-MIG-048` | `BACKUP_FILENAME = "aTrainingTracker_backup.attbackup"`, 1.5x space check, WAL flush |
| `SYSREQ-DEX-006` | Credential exclusion, Health Connect, decommissioned services | `REQ-DAT-024`, `REQ-EXP-020`, `REQ-EXT-009`, `REQ-EXT-011`, `REQ-EXT-012` | Exclusion of `auth_credentials_prefs.xml`, Health Connect HRV consent, Runkeeper & TrainingPeaks inert demarcation |
| `SYSREQ-DEX-007` | Polar BLE PFTP/PMD offline recording & 30s/45s backfill | `REQ-CON-045`, `REQ-CON-049`, `REQ-CON-050`, `REQ-SET-120`, `REQ-TRK-084`, `REQ-TRK-091`, `REQ-EXP-044` | `0000feee-...` (`SERVICE_POLAR_PFTP`), `fb005c80-...` (`SERVICE_POLAR_PMD`), H10 `30_000L` / Verity Sense `45_000L` |
| `SYSREQ-DEX-008` | Open-Meteo DEM elevation & spatial cache healing | `REQ-DAT-014`, `REQ-TRK-024`, `REQ-SEN-025`, `REQ-CLM-009` | `https://api.open-meteo.com/v1/elevation`, `ElevationCacheDatabaseManager.kt` |

---

## 4. System-to-Software Requirement Traceability Matrix (SYS.2 BP5 -> SWE.1 BP5)

The following matrix establishes bidirectional traceability between system data exchange requirements and concrete refining software requirements in `docs/requirements/req_data_migration_export_part1.md` and `docs/requirements/req_data_migration_export_part2.md`:

| System Requirement | System Title | Refining Software Requirements (`REQ-*`) | Grounded Classes & Verification Test |
| :--- | :--- | :--- | :--- |
| `SYSREQ-DEX-001` | Multi-Format Telemetry Export | `REQ-DAT-012`, `REQ-DAT-013`, `REQ-DAT-017`, `REQ-DAT-018`, `REQ-DAT-023`, `REQ-DAT-027`, `REQ-DAT-028`, `REQ-DAT-030`, `REQ-DAT-031`, `REQ-DAT-032`, `REQ-DAT-037`, `REQ-DAT-038`, `REQ-DAT-039`, `REQ-DAT-040`, `REQ-DAT-041`, `REQ-DAT-042`, `REQ-DAT-043`, `REQ-EXP-001`, `REQ-EXP-022`, `REQ-EXP-042`, `REQ-EXP-044` | `ExportManager.java`, `FitFileWriter.kt`, `TCXFileWriter.kt`, `GPXFileWriter.java`, `CSVFileWriter.java`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-002` | Multi-Format Workout Ingestion & Deduplication | `REQ-DAT-001`, `REQ-DAT-014`, `REQ-DAT-019`, `REQ-DAT-021`, `REQ-DAT-025`, `REQ-DAT-034`, `REQ-MIG-018`, `REQ-MIG-022`, `REQ-MIG-024`, `REQ-MIG-033`, `REQ-MIG-037`, `REQ-MIG-039`, `REQ-MIG-046`, `REQ-MIG-051`, `REQ-MIG-053` | `LegacyImportEngine.kt`, `StravaBulkExportImporter.kt`, `StravaCsvParser.kt`, `WorkoutSamplesDatabaseManager.java`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-003` | Cloud Activity Sync (Strava) | `REQ-EXP-002`, `REQ-EXP-003`, `REQ-EXP-005`, `REQ-EXP-006`, `REQ-EXP-007`, `REQ-EXP-008`, `REQ-EXP-009`, `REQ-EXP-010`, `REQ-EXP-011`, `REQ-EXP-012`, `REQ-EXP-013`, `REQ-EXP-014`, `REQ-EXP-015`, `REQ-EXP-016`, `REQ-EXP-017`, `REQ-EXP-019`, `REQ-EXP-021`, `REQ-EXP-023`, `REQ-EXP-043`, `REQ-EXT-001`, `REQ-EXT-002`, `REQ-EXT-003`, `REQ-EXT-004`, `REQ-EXT-005`, `REQ-EXT-006`, `REQ-EXT-007`, `REQ-EXT-008`, `REQ-EXT-010`, `REQ-RAD-007` | `StravaUploader.kt`, `StravaEquipmentSyncWorker.kt`, `StravaRoutesSyncWorker.kt`, `StravaSegmentsSyncWorker.kt`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-004` | Cloud Storage Sync (Dropbox & GDrive) | `REQ-DAT-020`, `REQ-DAT-022`, `REQ-DAT-024`, `REQ-DAT-029`, `REQ-EXP-018`, `REQ-EXP-044`, `REQ-MIG-002`, `REQ-MIG-003`, `REQ-MIG-007`, `REQ-MIG-008`, `REQ-MIG-009`, `REQ-MIG-010`, `REQ-MIG-019`, `REQ-MIG-028`, `REQ-MIG-030`, `REQ-MIG-031`, `REQ-MIG-034`, `REQ-MIG-035`, `REQ-MIG-036`, `REQ-MIG-038`, `REQ-MIG-040`, `REQ-MIG-049`, `REQ-MIG-050`, `REQ-MIG-052` | `DropboxBackupManager.kt`, `GoogleDriveBackupManager.kt`, `GoogleDriveUploader.kt`, `BackupWorker.kt`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-005` | Full State Backup & Atomic Recovery | `REQ-DAT-004`, `REQ-DAT-005`, `REQ-DAT-006`, `REQ-DAT-007`, `REQ-DAT-008`, `REQ-DAT-009`, `REQ-DAT-011`, `REQ-DAT-015`, `REQ-DAT-016`, `REQ-DAT-020`, `REQ-DAT-026`, `REQ-DAT-033`, `REQ-DAT-035`, `REQ-DAT-036`, `REQ-DAT-044`, `REQ-MIG-001`, `REQ-MIG-004`, `REQ-MIG-005`, `REQ-MIG-006`, `REQ-MIG-011`, `REQ-MIG-012`, `REQ-MIG-013`, `REQ-MIG-014`, `REQ-MIG-015`, `REQ-MIG-016`, `REQ-MIG-017`, `REQ-MIG-020`, `REQ-MIG-021`, `REQ-MIG-023`, `REQ-MIG-025`, `REQ-MIG-026`, `REQ-MIG-027`, `REQ-MIG-029`, `REQ-MIG-032`, `REQ-MIG-041`, `REQ-MIG-042`, `REQ-MIG-043`, `REQ-MIG-044`, `REQ-MIG-045`, `REQ-MIG-047`, `REQ-MIG-048` | `BackupManager.kt`, `SecureZipExtractor.kt`, `BackupRestoreViewModel.kt`, `DatabaseConnectionManager.kt`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-006` | Security Isolation & Credentials Shield | `REQ-DAT-024`, `REQ-EXP-020`, `REQ-EXT-009`, `REQ-EXT-011`, `REQ-EXT-012` | `AuthCredentialsStore.kt`, `full_backup_content.xml`, `data_extraction_rules.xml`, `HealthConnectHrvImporter.kt`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-007` | Polar BLE PFTP/PMD Offline Backfill (30s/45s) | `REQ-CON-045`, `REQ-CON-049`, `REQ-CON-050`, `REQ-SET-120`, `REQ-TRK-084`, `REQ-TRK-091`, `REQ-EXP-044` | `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `PolarOfflineGattCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `WorkoutPostProcessingCoordinator.kt`, `TrackerService.java`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-008` | External Elevation Model (Open-Meteo DEM) | `REQ-DAT-014`, `REQ-TRK-024`, `REQ-SEN-025`, `REQ-CLM-009` | `ElevationService.kt`, `ElevationCacheDatabaseManager.kt`, `ElevationResult.kt`, `SystemDataExchangeRequirementsContractTest.kt` |

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-16 | AI Agent | `ATT-6142` | Prior system specification baseline. |
| `1.1.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Standardized `DOC-SYS-DEX` header, bumped version, reset status to `Reviewed` (`Pending PO Sign-Off`). |
| `1.2.0` | 2026-11-18 | AI Agent (`ATT-8201`) | `ATT-8201` | Added `SYSREQ-DEX-007` (Polar BLE PFTP/PMD offline recording & 30s backfill) and `SYSREQ-DEX-008` (Open-Meteo DEM elevation & spatial cache healing) and updated matrices. |
| `1.2.1` | 2026-11-18 | AI Agent (`ATT-8201`) | `ATT-8201` | Cold Stage 6 heal: distinguished Polar H10 `30_000L` (`30s`) vs. Verity Sense `45_000L` (`45s`) `BACKFILL_TIMEOUT_MS` in `SYSREQ-DEX-007` and updated pre-split shard references to `req_data_migration_export_part1/2.md`. |
| `1.2.2` | 2026-11-20 | Lane A Author (`ATT-9154`, `--as agent1`) | `ATT-9154` | Refined `SYSREQ-DEX-007` for asynchronous foreground-protected post-workout Polar backfill prior to `ExportManager` export generation and linked `REQ-TRK-091` in §3/§4 (`Rule 81`: new `.kt` filenames deferred until Stage 4). |
| `1.2.3` | 2026-11-20 | Lane B Author (`ATT-9175`, `--as agent1`) | `ATT-9175` | Linked `REQ-SET-120` and grounded `PolarOfflineGattCoordinator.kt` / `PolarOnlineStreamingBridge.kt` under `SYSREQ-DEX-007` in §3/§4 parity (`Rule 81`). |
