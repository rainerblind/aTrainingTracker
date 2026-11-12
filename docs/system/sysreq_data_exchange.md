# System Requirements Specification: Data Exchange and Cloud Integration

**Document Version**: 1.0.0  
**ASPICE Process Areas**: SYS.2 (System Requirements Analysis, BP1–BP3)  
**Requirement Reference**: `REQ-PRO-055` (`REQ-PRO-055.1`, `REQ-PRO-055.2`)  
**Test Reference**: `TST-PRO-055` (`TST-PRO-055.1`–`TST-PRO-055.3`)  
**Author**: Antigravity Autonomous Engineering Pair  
**Status**: Approved & Grounded in Live Source Code  

---

## 1. Scope & Operational Context

This document establishes the formal, black-box system requirements (`SYSREQ-DEX-001` through `SYSREQ-DEX-006`) for data exchange, telemetry export/import, cloud services integration, and disaster recovery in **aTrainingTracker**.

These requirements satisfy ASPICE 4.0 **SYS.2 (System Requirements Analysis)** Base Practices:
* **SYS.2 BP1**: *Specify system requirements* – Define functional, non-functional, security, and interface data requirements.
* **SYS.2 BP2**: *Structure system requirements* – Organize requirements by domain (Export, Import, Cloud Sync, Backup, Security).
* **SYS.2 BP3**: *Analyze system requirements* – Ensure verifiability, privacy safeguards, and complete traceability.

---

## 2. System Requirements Catalog

### SYSREQ-DEX-001: Multi-Format Telemetry Export Interoperability
* **Title**: Multi-Format Telemetry Export Interoperability
* **Statement**: The system SHALL export completed endurance workouts into standard open formats without loss of captured metric fidelity:
  1. *Flexible & Interoperable Data Transfer (FIT)*: Garmin FIT 2.0 binary container (`.fit`, `FileFormat.FIT`, MIME `application/vnd.ant.fit`) via `FitFileWriter.java` with file header, device info, record mesgs (timestamp, lat/lng, distance, speed, HR, cadence, power, temperature, elevation), lap mesgs, and session summaries.
  2. *Garmin Training Center XML (TCX)*: Standard XML schema (`.tcx`, `FileFormat.TCX`, MIME `application/vnd.garmin.tcx+xml`) via `TCXFileWriter.java` including trackpoint extensions (`TPX`).
  3. *GPS Exchange Format (GPX)*: Open geospatial standard (`.gpx`, `FileFormat.GPX`, MIME `application/gpx+xml`) via `GPXFileWriter.java` containing trackpoints, elevation, and Garmin TrackPointExtension elements.
  4. *Tabular Plaintext (CSV)*: Comma-separated values (`.csv`, `FileFormat.CSV`, MIME `text/csv`) via `CSVFileWriter.java` exporting raw 1-Hz discrete telemetry rows for external spreadsheet or scientific analysis.
  5. The export pipeline SHALL enforce canonical distance (`DistanceFormatter`), elevation (`AltitudeFormatter`), and speed/pace formatting across all export serialization paths.
* **Rationale**: Athletes require vendor-neutral mobility of their training data across desktop software (GoldenCheetah), third-party ecosystems, and long-term offline archives.
* **Priority**: High
* **Verification Method**: Test & Demonstration (`SystemDataExchangeRequirementsContractTest`, `FitFileWriterTest.kt`, `TCXRoundTripTest.kt`)
* **Traced Software Components**: `ExportManager.java`, `FileFormat.java`, `FitFileWriter.java`, `TCXFileWriter.java`, `GPXFileWriter.java`, `CSVFileWriter.java`

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

---

## 3. System Requirements & Software Code Grounding Matrix

| System Requirement | Primary Capabilities | Traced Software Classes | Grounded Constants / Parameters |
| :--- | :--- | :--- | :--- |
| `SYSREQ-DEX-001` | Multi-format telemetry export (FIT, TCX, GPX, CSV) | `ExportManager`, `FileFormat`, `FitFileWriter` | `FileFormat.FIT` (`.fit`), `FileFormat.TCX` (`.tcx`), `FileFormat.GPX` (`.gpx`), `FileFormat.CSV` (`.csv`) |
| `SYSREQ-DEX-002` | Multi-format workout ingestion & deduplication | `LegacyImportEngine`, `StravaBulkExportImporter`, `StravaCsvParser` | Timestamp and bounding box deduplication, FIT/TCX/GPX track parsers, Strava `activities.csv` manifest parser |
| `SYSREQ-DEX-003` | Strava OAuth2 activity, route & segment sync | `StravaUploader`, `StravaEquipmentSyncWorker`, `StravaRoutesSyncWorker`, `StravaSegmentsSyncWorker` | `/api/v3/uploads`, `strava.${applicationId}`, rate limit backoff |
| `SYSREQ-DEX-004` | Dropbox & Google Drive cloud backup & workout sync | `DropboxBackupManager`, `GoogleDriveBackupManager`, `GoogleDriveUploader` | `/Backups/`, `aTrainingTracker/Backups/`, `dropbox-core-sdk:8.0.2` |
| `SYSREQ-DEX-005` | Full state `.attbackup` atomic archiving & Zip Slip defense | `BackupManager`, `SecureZipExtractor` | `BACKUP_FILENAME = "aTrainingTracker_backup.attbackup"`, 1.5x space check, WAL flush |
| `SYSREQ-DEX-006` | Credential exclusion, Health Connect, decommissioned services | `AuthCredentialsStore`, `full_backup_content.xml`, `data_extraction_rules.xml`, `HealthConnectHrvImporter` | Exclusion of `auth_credentials_prefs.xml`, Health Connect HRV consent, Runkeeper & TrainingPeaks inert demarcation |

---

## 4. System-to-Software Requirement Traceability Matrix (SYS.2 BP5 -> SWE.1 BP5)

The following matrix establishes bidirectional traceability between system data exchange requirements and concrete refining software requirements in `docs/requirements/req_data_migration_export.md`:

| System Requirement | System Title | Refining Software Requirements (`REQ-*`) | Grounded Classes & Verification Test |
| :--- | :--- | :--- | :--- |
| `SYSREQ-DEX-001` | Multi-Format Telemetry Export | `REQ-EXP-001`, `REQ-EXP-022`, `REQ-DAT-018`, `REQ-DAT-027`, `REQ-DAT-028` | `ExportManager.java`, `FitFileWriter.java`, `TCXFileWriter.java`, `GPXFileWriter.java`, `CSVFileWriter.java`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-002` | Multi-Format Workout Ingestion & Deduplication | `REQ-DAT-001`, `REQ-DAT-019`, `REQ-DAT-021`, `REQ-DAT-025` | `LegacyImportEngine.kt`, `StravaBulkExportImporter.kt`, `StravaCsvParser.kt`, `WorkoutSamplesDatabaseManager.java`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-003` | Cloud Activity Sync (Strava) | `REQ-EXP-002`, `REQ-EXP-005`, `REQ-EXP-008`, `REQ-EXP-009`, `REQ-EXP-011`, `REQ-EXP-012`, `REQ-EXP-019`, `REQ-EXP-021`, `REQ-EXT-004` | `StravaUploader.kt`, `StravaEquipmentSyncWorker.kt`, `StravaRoutesSyncWorker.kt`, `StravaSegmentsSyncWorker.kt`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-004` | Cloud Storage Sync (Dropbox & GDrive) | `REQ-DAT-020`, `REQ-DAT-022`, `REQ-DAT-024`, `REQ-DAT-029` | `DropboxBackupManager.kt`, `GoogleDriveBackupManager.kt`, `GoogleDriveUploader.kt`, `BackupWorker.kt`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-005` | Full State Backup & Atomic Recovery | `REQ-DAT-005`, `REQ-DAT-020`, `REQ-DAT-026`, `REQ-MIG-023` | `BackupManager.kt`, `SecureZipExtractor.kt`, `BackupRestoreViewModel.kt`, `DatabaseConnectionManager.kt`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-006` | Security Isolation & Credentials Shield | `REQ-EXP-020`, `REQ-DAT-024` | `AuthCredentialsStore.kt`, `full_backup_content.xml`, `data_extraction_rules.xml`, `HealthConnectHrvImporter.kt`, `SystemDataExchangeRequirementsContractTest.kt` |

