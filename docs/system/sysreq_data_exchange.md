# System Requirements Specification: Data Exchange and Cloud Integration

**Document Version**: 1.0.0  
**ASPICE Process Areas**: SYS.2 (System Requirements Analysis, BP1–BP3)  
**Requirement Reference**: `REQ-PRO-055` (`REQ-PRO-055.1`, `REQ-PRO-055.2`)  
**Test Reference**: `TST-PRO-055` (`TST-PRO-055.1`–`TST-PRO-055.3`)  
**Author**: Antigravity Autonomous Engineering Pair  
**Status**: Approved & Grounded in Live Source Code  

---

## 1. Scope & Operational Context

This document establishes the formal, black-box system requirements (`SYSREQ-DEX-*`) for data exchange, telemetry export/import, cloud services integration, and disaster recovery in **aTrainingTracker**.

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
* **Verification Method**: Test & Demonstration (`SystemDataExchangeRequirementsContractTest`, `FitExportTest`, `TCXExportTest`)
* **Traced Software Components**: `ExportManager.java`, `FileFormat.java`, `FitFileWriter.java`, `TCXFileWriter.java`, `GPXFileWriter.java`, `CSVFileWriter.java`

### SYSREQ-DEX-002: Multi-Format Workout Ingestion & Collision Deduplication
* **Title**: Multi-Format Workout Ingestion & Collision Deduplication
* **Statement**: The system SHALL ingest historical workout recordings in standard formats (FIT, TCX, GPX, CSV):
  1. Parse trackpoints, lap markers, sensor telemetry, and workout metadata into the application SQLite database (`WorkoutSummaries.db`, `WorkoutSamples.db`).
  2. Perform collision detection and deduplication against existing sessions based on start timestamp, duration, and trajectory spatial bounding box to prevent duplicate workout entries.
  3. Support batch import workflows (e.g. Strava bulk export archive ingestion) with non-blocking background execution.
* **Rationale**: Athletes switching from other devices or platforms must be able to restore historical training logs without creating duplicated entries.
* **Priority**: High
* **Verification Method**: Test (`SystemDataExchangeRequirementsContractTest`, `LegacyImportEngine`, `StravaBulkExportImporter`)
* **Traced Software Components**: `LegacyImportEngine.java`, `StravaBulkExportImporter.kt`, `WorkoutDataMapper.java`, `WorkoutSamplesDatabaseManager.java`

### SYSREQ-DEX-003: Cloud Activity Synchronization & Rate Governance
* **Title**: Cloud Activity Synchronization & Rate Governance (Strava)
* **Statement**: The system SHALL support cloud workout synchronization via the Strava v3 REST API:
  1. Authorize athlete access via OAuth2 authorization code flow with secure refresh token exchange and variant-scoped callback redirection (`strava.${applicationId}`).
  2. Perform asynchronous multipart workout file uploads (`/api/v3/uploads`) with background status polling and upload success confirmation.
  3. Enforce Strava API rate limit compliance (15-minute and 24-hour request windows) to prevent API throttling or quota exhaustion.
  4. Synchronize athlete equipment (bikes, shoes) and auto-assign gear to matched workout activity types (`StravaEquipmentSyncWorker.kt`).
* **Rationale**: Social sharing and cloud analytics on platforms like Strava are primary use cases for endurance athletes.
* **Priority**: High
* **Verification Method**: Test & Analysis (`SystemDataExchangeRequirementsContractTest`, `StravaUploader`, `StravaEquipmentSyncWorker`)
* **Traced Software Components**: `StravaUploader.java`, `StravaUploadHelper.kt`, `StravaEquipmentSyncWorker.kt`, `AuthCredentialsStore.kt`

### SYSREQ-DEX-004: Cloud Storage Synchronization & Automatic Backup
* **Title**: Cloud Storage Synchronization & Automatic Backup (Dropbox & Google Drive)
* **Statement**: The system SHALL support automated and manual cloud archiving of completed workout files and full database backups:
  1. *Dropbox Integration*: Integrate Dropbox API v2 (`com.dropbox.core:dropbox-core-sdk:8.0.2`, `DropboxUploader.java`) with OAuth2 PKCE authorization, uploading workout files to user-configured subfolders and periodic `.attbackup` archives to `/Backups/`.
  2. *Google Drive Integration*: Integrate Google Drive REST API v3 (`GoogleDriveUploader.java`, `GoogleDriveClient.kt`) with Google Sign-In authorization, synchronizing application backups to `aTrainingTracker/Backups/`.
  3. Cloud synchronization tasks SHALL execute within background `WorkManager` workers, automatically retrying upon transient network dropouts without impeding the live tracking engine.
* **Rationale**: Seamless off-device cloud replication protects athletes against hardware loss, phone damage, or accidental database corruption.
* **Priority**: High
* **Verification Method**: Test (`SystemDataExchangeRequirementsContractTest`, `DropboxBackupManager`, `GoogleDriveBackupManager`)
* **Traced Software Components**: `DropboxUploader.java`, `DropboxBackupManager.kt`, `GoogleDriveUploader.java`, `GoogleDriveBackupManager.kt`, `BackupWorker.kt`

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
* **Traced Software Components**: `BackupManager.kt`, `SecureZipExtractor.kt`, `BackupRestoreViewModel.kt`, `DatabaseHelper.java`

### SYSREQ-DEX-006: Credential Protection, Security Isolation & Decommissioned Boundaries
* **Title**: Credential Protection, Security Isolation & Decommissioned Platform Boundaries
* **Statement**: The system SHALL enforce strict data privacy, credential protection, and interface lifecycle boundaries:
  1. *Credential Protection*: Sensitive user authentication tokens, OAuth refresh secrets, and cloud credentials stored in `auth_credentials_prefs.xml` SHALL be strictly excluded from `.attbackup` archives and Android Auto Backup (`backup_rules.xml`).
  2. *Legacy Credential Purging*: Any legacy credential keys found in default shared preferences SHALL be automatically migrated and purged (`AuthCredentialsStore.migrateLegacyCredentials()`).
  3. *Health Connect Gating*: Integration with Android Health Connect API (`ExerciseSessionRecord`, `HeartRateRecord`) SHALL enforce dynamic runtime user permission consent before transmitting biometric samples.
  4. *Decommissioned Platform Boundaries*: Permanently retired third-party cloud interfaces (Runkeeper HealthGraph API and TrainingPeaks API) SHALL be maintained as inert schema placeholders strictly to preserve historical SQLite database schema integrity and prevent migration regressions.
* **Rationale**: Exported archives and cloud services must never leak athlete credentials, and deprecated cloud platforms must not trigger runtime communication errors.
* **Priority**: High
* **Verification Method**: Static Audit & Test (`SystemDataExchangeRequirementsContractTest`, `BackupManagerCredentialExclusionTest`, `BackupRulesXmlValidationTest`)
* **Traced Software Components**: `AuthCredentialsStore.kt`, `backup_rules.xml`, `HealthConnectManager.kt`, `WorkoutDataHelper.java`

---

## 3. System Requirements & Software Code Grounding Matrix

| System Requirement | Primary Capabilities | Traced Software Classes | Grounded Constants / Parameters |
| :--- | :--- | :--- | :--- |
| `SYSREQ-DEX-001` | Multi-format telemetry export (FIT, TCX, GPX, CSV) | `ExportManager`, `FileFormat`, `FitFileWriter` | `FileFormat.FIT` (`.fit`), `FileFormat.TCX` (`.tcx`), `FileFormat.GPX` (`.gpx`), `FileFormat.CSV` (`.csv`) |
| `SYSREQ-DEX-002` | Multi-format workout ingestion & deduplication | `LegacyImportEngine`, `StravaBulkExportImporter` | Timestamp and bounding box deduplication, FIT/TCX/GPX/CSV parsers |
| `SYSREQ-DEX-003` | Strava OAuth2 activity sync & rate governance | `StravaUploader`, `StravaEquipmentSyncWorker` | `/api/v3/uploads`, `strava.${applicationId}`, rate limit backoff |
| `SYSREQ-DEX-004` | Dropbox & Google Drive cloud backup sync | `DropboxBackupManager`, `GoogleDriveBackupManager` | `/Backups/`, `aTrainingTracker/Backups/`, `dropbox-core-sdk:8.0.2` |
| `SYSREQ-DEX-005` | Full state `.attbackup` atomic archiving & Zip Slip defense | `BackupManager`, `SecureZipExtractor` | `BACKUP_FILENAME = "aTrainingTracker_backup.attbackup"`, 1.5x space check, WAL flush |
| `SYSREQ-DEX-006` | Credential exclusion, Health Connect, decommissioned services | `AuthCredentialsStore`, `backup_rules.xml` | Exclusion of `auth_credentials_prefs.xml`, Runkeeper & TrainingPeaks inert demarcation |
