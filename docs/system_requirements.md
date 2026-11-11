# System Requirements Specification Index (ASPICE SYS.2)

**Document Version**: 1.0.0  
**ASPICE Process Area**: SYS.2 (System Requirements Analysis, BP1–BP3)  
**Parent Epic**: ATT-6142 (ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates)  
**Status**: Approved & Grounded in Live Source Code  

---

## 1. System Requirements Overview & Architecture

aTrainingTracker specifies formal system requirements (`SYSREQ-*`) separated cleanly from internal software design across the following authoritative system requirement specifications:

* **[System Context and External Interfaces](system/system_context.md)** (`REQ-PRO-053`, `TST-PRO-053`, ATT-6158): Defines system boundaries, external element taxonomy across 5 domains, and operational assumptions.
* **[Live Tracking with Sensors System Requirements](system/sysreq_live_tracking.md)** (`REQ-PRO-054`, `TST-PRO-054`, ATT-6160): Defines end-to-end multi-sensor acquisition, 1-Hz sampling, GNSS positioning, foreground power management, and real-time alerts (`SYSREQ-TRK-001` through `SYSREQ-TRK-006`).
* **[Data Exchange and Cloud Integration System Requirements](system/sysreq_data_exchange.md)** (`REQ-PRO-055`, `TST-PRO-055`, ATT-6161): Defines multi-format export/import, cloud synchronization (Strava, Dropbox, Google Drive), atomic backup archiving (`.attbackup`), and security/privacy boundaries (`SYSREQ-DEX-001` through `SYSREQ-DEX-006`).

---

## 4. Live Tracking with Sensors System Requirements (Summary)

See [`docs/system/sysreq_live_tracking.md`](system/sysreq_live_tracking.md) for full formal specifications:
* **`SYSREQ-TRK-001`**: Multi-Sensor Telemetry Acquisition & Standard Profile Interoperability (BLE GATT + ANT+).
* **`SYSREQ-TRK-002`**: Automatic Reconnection & Exponential Backoff Resilience (1s initial, 16s max backoff, 3.5s radar watchdog).
* **`SYSREQ-TRK-003`**: Deterministic 1-Hz Technical Sampling Loop & Time-Series Recording (`TrackerService`).
* **`SYSREQ-TRK-004`**: Multi-Source GNSS Acquisition & Position Telemetry (GPS provider + Google Fused Location).
* **`SYSREQ-TRK-005`**: Background Foreground Service Execution & Power Management (Partial WakeLock, battery optimization).
* **`SYSREQ-TRK-006`**: Real-Time Sensory Alerts & Cockpit Feedback (Compose tiles, radar threat audio/visual warnings).

---

## 5. Data Exchange and Cloud Integration System Requirements (SYSREQ-DEX-001..006)

See [`docs/system/sysreq_data_exchange.md`](system/sysreq_data_exchange.md) for full formal specifications:
* **`SYSREQ-DEX-001`**: Multi-Format Telemetry Export Interoperability (FIT, TCX, GPX, CSV).
* **`SYSREQ-DEX-002`**: Multi-Format Workout Ingestion & Collision Deduplication.
* **`SYSREQ-DEX-003`**: Cloud Activity Synchronization & Rate Governance (Strava OAuth2 multipart upload & gear sync).
* **`SYSREQ-DEX-004`**: Cloud Storage Synchronization & Automatic Backup (Dropbox API v2 & Google Drive REST API v3).
* **`SYSREQ-DEX-005`**: Full Application State Backup & Atomic Recovery Pipeline (`.attbackup`, 1.5x space check, Zip Slip protection).
* **`SYSREQ-DEX-006`**: Credential Protection, Security Isolation & Decommissioned Boundaries (`auth_credentials_prefs` exclusion, Health Connect user consent, decommissioned Runkeeper/TrainingPeaks).

---

## 6. Complete System Requirements Traceability Matrix

| Requirement ID | Domain | Title | Verification Method | Traced Software Classes |
| :--- | :--- | :--- | :--- | :--- |
| `SYSREQ-TRK-001` | Live Tracking | Multi-Sensor Acquisition & Profile Interoperability | Test / `SystemLiveTrackingRequirementsContractTest` | `BANALService`, `BluetoothConstants` |
| `SYSREQ-TRK-002` | Live Tracking | Reconnection & Exponential Backoff Resilience | Test / `SystemLiveTrackingRequirementsContractTest` | `MyBTLEDevice`, `BTLERadarDevice` |
| `SYSREQ-TRK-003` | Live Tracking | Deterministic 1-Hz Sampling & SQLite Recording | Test / `SystemLiveTrackingRequirementsContractTest` | `TrackerService`, `WorkoutSamplesDatabaseManager` |
| `SYSREQ-TRK-004` | Live Tracking | Multi-Source GNSS Acquisition & Position Telemetry | Test / `SystemLiveTrackingRequirementsContractTest` | `SpeedAndLocationDevice_GPS`, `SpeedAndLocationDevice_GoogleFused` |
| `SYSREQ-TRK-005` | Live Tracking | Foreground Service Execution & Power Management | Test / `SystemLiveTrackingRequirementsContractTest` | `TrackerService`, `TrainingApplication` |
| `SYSREQ-TRK-006` | Live Tracking | Real-Time Sensory Alerts & Cockpit Feedback | Test / `SystemLiveTrackingRequirementsContractTest` | `SensorGridScreen`, `RadarEdgeOverlay` |
| `SYSREQ-DEX-001` | Data Exchange | Multi-Format Telemetry Export Interoperability | Test / `SystemDataExchangeRequirementsContractTest` | `ExportManager`, `FileFormat`, `FitFileWriter` |
| `SYSREQ-DEX-002` | Data Exchange | Multi-Format Workout Ingestion & Deduplication | Test / `SystemDataExchangeRequirementsContractTest` | `LegacyImportEngine`, `StravaBulkExportImporter` |
| `SYSREQ-DEX-003` | Data Exchange | Cloud Activity Synchronization & Rate Governance | Test / `SystemDataExchangeRequirementsContractTest` | `StravaUploader`, `StravaEquipmentSyncWorker` |
| `SYSREQ-DEX-004` | Data Exchange | Cloud Storage Synchronization & Automatic Backup | Test / `SystemDataExchangeRequirementsContractTest` | `DropboxBackupManager`, `GoogleDriveBackupManager` |
| `SYSREQ-DEX-005` | Data Exchange | Full State Backup & Atomic Recovery Pipeline | Test / `SystemDataExchangeRequirementsContractTest` | `BackupManager`, `SecureZipExtractor` |
| `SYSREQ-DEX-006` | Data Exchange | Credential Protection & Decommissioned Boundaries | Test / `SystemDataExchangeRequirementsContractTest` | `AuthCredentialsStore`, `full_backup_content.xml` |
