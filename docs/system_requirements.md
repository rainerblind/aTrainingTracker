# System Requirements Specification Index (ASPICE SYS.2)

**Document Version**: 1.1.1  
**ASPICE Process Area**: SYS.2 (System Requirements Analysis, BP1–BP3)  
**Parent Epic**: ATT-6142 / ATT-8138 (ASPICE CL2 – System Level & Remediation)  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

---

## 1. System Requirements Overview & Architecture

aTrainingTracker specifies formal system requirements (`SYSREQ-*`) separated cleanly from internal software design across the following authoritative system requirement specifications:

* **[System Context and External Interfaces](system/system_context.md)** (`REQ-PRO-053`, `TST-PRO-053`, ATT-6158): Defines system boundaries, external element taxonomy across 5 domains, and operational assumptions.
* **[Live Tracking with Sensors System Requirements](system/sysreq_live_tracking.md)** (`REQ-PRO-054`, `TST-PRO-054`, `REQ-PRO-131`, ATT-6160, ATT-8201): Defines end-to-end multi-sensor acquisition, 1-Hz sampling, GNSS positioning, foreground power management, real-time alerts, bidirectional radar/light actuation, barometer/EKF climbs, GPX audio navigation, smartphone IMU/iTPMS, swimming, HRV/DFA alpha-1, and process-death recovery (`SYSREQ-TRK-001` through `SYSREQ-TRK-013`).
* **[Data Exchange and Cloud Integration System Requirements](system/sysreq_data_exchange.md)** (`REQ-PRO-055`, `TST-PRO-055`, `REQ-PRO-131`, ATT-6161, ATT-8201): Defines multi-format export/import, cloud synchronization (Strava, Dropbox, Google Drive), atomic backup archiving (`.attbackup`), security/privacy boundaries, Polar PFTP/PMD offline backfill, and Open-Meteo DEM enrichment (`SYSREQ-DEX-001` through `SYSREQ-DEX-008`).

---

## 2. Live Tracking with Sensors System Requirements (Summary)

See [`docs/system/sysreq_live_tracking.md`](system/sysreq_live_tracking.md) for full formal specifications:
* **`SYSREQ-TRK-001`**: Multi-Sensor Telemetry Acquisition & Standard Profile Interoperability (BLE GATT + ANT+).
* **`SYSREQ-TRK-002`**: Automatic Reconnection & Exponential Backoff Resilience (1s initial, 16s max backoff, 3.5s radar watchdog).
* **`SYSREQ-TRK-003`**: Deterministic 1-Hz Technical Sampling Loop & Time-Series Recording (`TrackerService`).
* **`SYSREQ-TRK-004`**: Multi-Source GNSS Acquisition & Position Telemetry (GPS provider + Google Fused Location).
* **`SYSREQ-TRK-005`**: Background Foreground Service Execution & Power Management (Partial WakeLock, battery optimization).
* **`SYSREQ-TRK-006`**: Real-Time Sensory Alerts & Cockpit Feedback (Compose tiles, radar threat audio/visual warnings).
* **`SYSREQ-TRK-007`**: Bidirectional BLE & ANT+ Radar / Tail-Light Actuation & Smart Braking (`6a4e3202`, `6a4e2f00`, ANT+ `0x28`/`0x23`).
* **`SYSREQ-TRK-008`**: Barometric Pressure, EKF Altitude/Slope Fusion, Live Climbs & Segments.
* **`SYSREQ-TRK-009`**: Google Maps GPX Follow-Me & Turn-by-Turn Audio Navigation.
* **`SYSREQ-TRK-010`**: Smartphone IMU Kinematics, Fused Odometry & Indirect TPMS (`ATTFused`, `SmartBrakeLightDetector`, `IndirectTpmsEstimator`).
* **`SYSREQ-TRK-011`**: Pool & Open-Water Swimming Telemetry & Lap Adjustment (`SwimWorkoutAdjustCard`, `SwimmingSection`).
* **`SYSREQ-TRK-012`**: Live HRV / RR-Interval Streaming & DFA Alpha-1 Threshold Estimation (`DfaCalculator`, `HrvRampTestScreen`).
* **`SYSREQ-TRK-013`**: Three-Tier Process-Death Session Recovery & State Restoration (`MANUAL_PAUSED` / `AUTO_PAUSED` / `TRACKING`, `StartOrResumeDialog`).

---

## 3. Data Exchange and Cloud Integration System Requirements (SYSREQ-DEX-001..008)

See [`docs/system/sysreq_data_exchange.md`](system/sysreq_data_exchange.md) for full formal specifications:
* **`SYSREQ-DEX-001`**: Multi-Format Telemetry Export Interoperability (FIT, TCX, GPX, CSV).
* **`SYSREQ-DEX-002`**: Multi-Format Workout Ingestion & Collision Deduplication (FIT, TCX, GPX tracks and Strava bulk export `activities.csv` metadata).
* **`SYSREQ-DEX-003`**: Cloud Activity Synchronization & Rate Governance (Strava OAuth2 multipart upload, gear, routes & segments sync).
* **`SYSREQ-DEX-004`**: Cloud Storage Synchronization & Automatic Backup (Dropbox API v2 & Google Drive REST API v3 workout & backup sync).
* **`SYSREQ-DEX-005`**: Full Application State Backup & Atomic Recovery Pipeline (`.attbackup`, 1.5x space check, Zip Slip protection).
* **`SYSREQ-DEX-006`**: Credential Protection, Security Isolation & Decommissioned Boundaries (`auth_credentials_prefs` exclusion, `full_backup_content.xml`, `data_extraction_rules.xml`, Health Connect HRV consent, decommissioned Runkeeper/TrainingPeaks).
* **`SYSREQ-DEX-007`**: Polar BLE PFTP/PMD Offline Recording, Clock Sync & 30s/45s Post-Workout Backfill (`0000feee`, `fb005c80`, `mDbExecutor`).
* **`SYSREQ-DEX-008`**: External Elevation Model (Open-Meteo DEM) & Cloud Telemetry Enrichment (`ElevationService`, `ElevationCacheDatabaseManager`).

---

## 4. Complete System Requirements Traceability Matrix

| Requirement ID | Domain | Title | Verification Method | Traced Software Classes |
| :--- | :--- | :--- | :--- | :--- |
| `SYSREQ-TRK-001` | Live Tracking | Multi-Sensor Acquisition & Profile Interoperability | Test / `SystemLiveTrackingRequirementsContractTest` | `BANALService`, `BluetoothConstants` |
| `SYSREQ-TRK-002` | Live Tracking | Reconnection & Exponential Backoff Resilience | Test / `SystemLiveTrackingRequirementsContractTest` | `MyBTLEDevice`, `BTLERadarDevice` |
| `SYSREQ-TRK-003` | Live Tracking | Deterministic 1-Hz Sampling & SQLite Recording | Test / `SystemLiveTrackingRequirementsContractTest` | `TrackerService`, `WorkoutSamplesDatabaseManager` |
| `SYSREQ-TRK-004` | Live Tracking | Multi-Source GNSS Acquisition & Position Telemetry | Test / `SystemLiveTrackingRequirementsContractTest` | `SpeedAndLocationDevice_GPS`, `SpeedAndLocationDevice_GoogleFused` |
| `SYSREQ-TRK-005` | Live Tracking | Foreground Service Execution & Power Management | Test / `SystemLiveTrackingRequirementsContractTest` | `TrackerService`, `TrainingApplication` |
| `SYSREQ-TRK-006` | Live Tracking | Real-Time Sensory Alerts & Cockpit Feedback | Test / `SystemLiveTrackingRequirementsContractTest` | `SensorGridScreen`, `RadarEdgeOverlay` |
| `SYSREQ-TRK-007` | Live Tracking | Bidirectional BLE/ANT+ Radar & Light Actuation | Test / `SystemLiveTrackingRequirementsContractTest` | `RadarLightController`, `SmartBrakeLightDetector`, `ANTBikeLightDevice`, `ANTBikeRadarDevice` |
| `SYSREQ-TRK-008` | Live Tracking | Barometric EKF Altitude/Slope, Climbs & Segments | Test / `SystemLiveTrackingRequirementsContractTest` | `KinematicAltitudeSlopeEkf`, `ClimbDetector`, `LiveClimbsRepository`, `LiveSegmentsRepository` |
| `SYSREQ-TRK-009` | Live Tracking | Google Maps GPX Follow-Me & Turn Audio Nav | Test / `SystemLiveTrackingRequirementsContractTest` | `GpxRouteImporter`, `TurnByTurnNavigationEngine`, `RouteDivergenceDetector`, `NavigationAudioAlertManager` |
| `SYSREQ-TRK-010` | Live Tracking | Smartphone IMU Kinematics, Odometry & iTPMS | Test / `SystemLiveTrackingRequirementsContractTest` | `SpeedAndLocationDevice_ATTFused`, `QuaternionGyroProjector`, `IndirectTpmsEstimator`, `DualWheelTpmsEstimator` |
| `SYSREQ-TRK-011` | Live Tracking | Pool & Open-Water Swimming Telemetry & Lap Adjust | Test / `SystemLiveTrackingRequirementsContractTest` | `SwimWorkoutAdjustCard`, `SwimmingSection`, `PolarVeritySenseOfflineController` |
| `SYSREQ-TRK-012` | Live Tracking | Live HRV / RR Streaming & DFA Alpha-1 Thresholds | Test / `SystemLiveTrackingRequirementsContractTest` | `DfaCalculator`, `HRVMetricsDevice`, `HrvSignalQualityAnalyzer`, `HrvThresholdEstimator` |
| `SYSREQ-TRK-013` | Live Tracking | Three-Tier Process-Death Session Recovery | Test / `SystemLiveTrackingRequirementsContractTest` | `TrackerService`, `StartOrResumeDialog`, `StartOrResumeInterface` |
| `SYSREQ-DEX-001` | Data Exchange | Multi-Format Telemetry Export Interoperability | Test / `SystemDataExchangeRequirementsContractTest` | `ExportManager`, `FileFormat`, `FitFileWriter` |
| `SYSREQ-DEX-002` | Data Exchange | Multi-Format Workout Ingestion & Deduplication | Test / `SystemDataExchangeRequirementsContractTest` | `LegacyImportEngine`, `StravaBulkExportImporter`, `StravaCsvParser` |
| `SYSREQ-DEX-003` | Data Exchange | Cloud Activity Synchronization & Rate Governance | Test / `SystemDataExchangeRequirementsContractTest` | `StravaUploader`, `StravaEquipmentSyncWorker`, `StravaRoutesSyncWorker`, `StravaSegmentsSyncWorker` |
| `SYSREQ-DEX-004` | Data Exchange | Cloud Storage Synchronization & Automatic Backup | Test / `SystemDataExchangeRequirementsContractTest` | `DropboxBackupManager`, `GoogleDriveBackupManager`, `GoogleDriveUploader` |
| `SYSREQ-DEX-005` | Data Exchange | Full State Backup & Atomic Recovery Pipeline | Test / `SystemDataExchangeRequirementsContractTest` | `BackupManager`, `SecureZipExtractor` |
| `SYSREQ-DEX-006` | Data Exchange | Credential Protection & Decommissioned Boundaries | Test / `SystemDataExchangeRequirementsContractTest` | `AuthCredentialsStore`, `full_backup_content.xml`, `data_extraction_rules.xml` |
| `SYSREQ-DEX-007` | Data Exchange | Polar BLE PFTP/PMD Offline Recording & 30s/45s Backfill | Test / `SystemDataExchangeRequirementsContractTest` | `PolarH10OfflineController`, `PolarVeritySenseOfflineController`, `PolarOfflineGattCoordinator`, `TrackerService` |
| `SYSREQ-DEX-008` | Data Exchange | External Elevation Model (Open-Meteo DEM) & Cache | Test / `SystemDataExchangeRequirementsContractTest` | `ElevationService`, `ElevationCacheDatabaseManager`, `ElevationResult` |
