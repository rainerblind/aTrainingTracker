# System-to-Software Requirement Traceability & Tailoring Specification

**Document Version**: 1.0.0  
**ASPICE Process Areas**: SYS.2 (System Requirements Analysis, BP5), SWE.1 (Software Requirements Analysis, BP5)  
**Requirement Reference**: `REQ-PRO-076` (`REQ-PRO-076.1`–`REQ-PRO-076.4`)  
**Test Reference**: `TST-PRO-076` (`TST-PRO-076.1`–`TST-PRO-076.4`)  
**Author**: Antigravity Autonomous Engineering Pair  
**Status**: Approved & Grounded in Live Source Code  

---

## 1. Scope & ASPICE Compliance Context

This specification establishes normative, bidirectional traceability between black-box system requirements (`SYSREQ-*`) and concrete software requirements (`REQ-*`) across **aTrainingTracker**.

### ASPICE 4.0 Governance Alignment:
* **SYS.2 BP5**: *Ensure consistency and bilateral traceability of system requirements to customer requirements and other work products* – All system capabilities map to refining software architecture elements and concrete software specifications.
* **SWE.1 BP5**: *Ensure consistency and bilateral traceability of software requirements to system requirements* – Every software requirement either directly traces to a parent system requirement or is classified as software-derived with documented architectural rationale.
* **GP 2.2**: *Establish and maintain bidirectional traceability across work products*.

---

## 2. Bidirectional System-to-Software Requirement Traceability

### 2.1 Domain 1: Live Tracking & Sensor Telemetry (`SYSREQ-TRK-*`)

| System Requirement | System Title | Refining Software Requirements (`REQ-*`) | Grounded Classes & Verification Test |
| :--- | :--- | :--- | :--- |
| `SYSREQ-TRK-001` | Multi-Sensor Profile Interoperability | `REQ-CON-001`, `REQ-CON-002`, `REQ-CON-003`, `REQ-CON-004`, `REQ-CON-005`, `REQ-CON-007`, `REQ-CON-028` | `BANALService.java`, `BluetoothConstants.java`, `BTLERadarDevice.java`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-002` | Reconnection Resilience & Watchdog | `REQ-CON-005`, `REQ-CON-028`, `REQ-TRK-013` | `MyBTLEDevice.java`, `BTLERadarDevice.java`, `FilterManager.java`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-003` | Deterministic 1-Hz Sampling Loop | `REQ-TRK-001`, `REQ-TRK-002`, `REQ-TRK-007`, `REQ-FIL-001`, `REQ-FIL-005` | `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-004` | Multi-Source GNSS Positioning | `REQ-TRK-003`, `REQ-TRK-013`, `REQ-TRK-014`, `REQ-TRK-017` | `SpeedAndLocationDevice_GPS.java`, `SpeedAndLocationDevice_GoogleFused.java`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-005` | Foreground Service & Power Management | `REQ-TRK-003`, `REQ-TRK-005`, `REQ-TRK-015`, `REQ-TRK-016` | `TrackerService.java`, `TrainingApplication.java`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-006` | Real-Time Alerts & Cockpit Feedback | `REQ-TRK-018`, `REQ-TRK-019`, `REQ-TRK-073`, `REQ-CON-028` | `SensorGridScreen.kt`, `RadarEdgeOverlay.kt`, `RadarAlertManager.kt`, `SystemLiveTrackingRequirementsContractTest.kt` |

### 2.2 Domain 2: Data Exchange, Cloud Integration & Disaster Recovery (`SYSREQ-DEX-*`)

| System Requirement | System Title | Refining Software Requirements (`REQ-*`) | Grounded Classes & Verification Test |
| :--- | :--- | :--- | :--- |
| `SYSREQ-DEX-001` | Multi-Format Telemetry Export | `REQ-EXP-001`, `REQ-EXP-022`, `REQ-DAT-018`, `REQ-DAT-027`, `REQ-DAT-028` | `ExportManager.java`, `FitFileWriter.java`, `TCXFileWriter.java`, `GPXFileWriter.java`, `CSVFileWriter.java`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-002` | Multi-Format Workout Ingestion & Deduplication | `REQ-DAT-001`, `REQ-DAT-019`, `REQ-DAT-021`, `REQ-DAT-025` | `LegacyImportEngine.kt`, `StravaBulkExportImporter.kt`, `StravaCsvParser.kt`, `WorkoutSamplesDatabaseManager.java`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-003` | Cloud Activity Sync (Strava) | `REQ-EXP-002`, `REQ-EXP-005`, `REQ-EXP-008`, `REQ-EXP-009`, `REQ-EXP-011`, `REQ-EXP-012`, `REQ-EXP-019`, `REQ-EXP-021`, `REQ-EXT-004` | `StravaUploader.kt`, `StravaEquipmentSyncWorker.kt`, `StravaRoutesSyncWorker.kt`, `StravaSegmentsSyncWorker.kt`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-004` | Cloud Storage Sync (Dropbox & GDrive) | `REQ-DAT-020`, `REQ-DAT-022`, `REQ-DAT-024`, `REQ-DAT-029` | `DropboxBackupManager.kt`, `GoogleDriveBackupManager.kt`, `GoogleDriveUploader.kt`, `BackupWorker.kt`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-005` | Full State Backup & Atomic Recovery | `REQ-DAT-005`, `REQ-DAT-020`, `REQ-DAT-026`, `REQ-MIG-023` | `BackupManager.kt`, `SecureZipExtractor.kt`, `BackupRestoreViewModel.kt`, `DatabaseConnectionManager.kt`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-006` | Security Isolation & Credentials Shield | `REQ-EXP-020`, `REQ-DAT-024` | `AuthCredentialsStore.kt`, `full_backup_content.xml`, `data_extraction_rules.xml`, `HealthConnectHrvImporter.kt`, `SystemDataExchangeRequirementsContractTest.kt` |

---

## 3. Normative Prefix Tailoring Rules for Software-Derived Requirements

In accordance with ASPICE SWE.1 BP5, requirements that originate directly from software architecture, user interface design decisions, mathematical computations, or engineering process governance are classified as **Software-Derived** (`[SWE-Derived]`). These requirements refine the software system without a standalone physical system-level parent requirement.

### 3.1 Domain & Prefix Tailoring Matrix

| Domain Shard | Prefixes | Tailoring Classification | Documented Architectural Rationale |
| :--- | :--- | :--- | :--- |
| **Tracking Cockpit** (`req_tracking_cockpit.md`) | `CON`, `FIL`, `FUSION`, `SEN`, `TRK`, `WEA` | **System Refinement** | Refines `SYSREQ-TRK-001` through `SYSREQ-TRK-006` (BLE/ANT+ sensors, 1-Hz sampling loop, GPS provider, foreground service, and radar alerts). |
| **Persistence & Cloud** (`req_data_migration_export.md`) | `DAT`, `EXP`, `EXT`, `MIG` | **System Refinement** | Refines `SYSREQ-DEX-001` through `SYSREQ-DEX-006` (FIT/TCX/GPX/CSV export, workout ingestion, Strava sync, cloud storage, `.attbackup`, and security isolation). |
| **Maps & Navigation** (`req_maps_navigation.md`) | `GEO`, `MAP`, `NAV`, `ROU` | **System Refinement & SWE-Derived** | Polyline rendering, GPX follow-me, and routing trace to `SYSREQ-TRK-004` (GNSS acquisition) with software-derived map UI visualization contracts. |
| **UI Design System** (`req_ui_design_system.md`) | `UI`, `UNT` | **SWE-Derived** | Jetpack Compose design tokens, layout hierarchy, theme centralization, typography, gestures, and unit formatters represent pure software user interface presentation layers. |
| **Post-Workout Analytics** (`req_aftermath_periods.md`) | `PER`, `PERF`, `SPT` | **SWE-Derived** | Statistical aggregation algorithms, heart rate / power zone distributions, and interactive chart scrubbers are software-internal mathematical models computed over persisted samples. |
| **Settings & Profiles** (`req_settings_profiles.md`) | `LOC`, `MON`, `PRI`, `SET` | **SWE-Derived** | Athlete configuration, bike and sensor profile entities, privacy zone radii, monetization feature flags, and 9-locale translation tables govern software runtime behavior and presentation. |
| **Milestones & Reviews** (`req_aftermath_reviews.md`) | `REV` | **SWE-Derived** | Milestone celebration algorithms, jubilee auto-naming, and Google Play In-App Review triggering are software engagement workflows. |
| **Store Presence** (`req_store_presence.md`) | `STP` | **SWE-Derived** | Google Play Store metadata, promotional graphics contracts, and release packaging are software distribution artifacts. |
| **Engineering Process** (`req_process_stability.md`) | `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST` | **SWE-Derived** | ASPICE governance verification tools, CI static analysis gates, compiler flags, and unit/contract test suites govern the software development lifecycle. |

---

## 4. Verification and Tooling Enforcement

The integrity of this traceability specification is automatically verified by `tools/verify_requirement_governance.py`:
1. **SYSREQ Existence Guard**: All `SYSREQ-*` identifiers cited in requirements or traceability tables must exist in `docs/system/sysreq_*.md`.
2. **SYSREQ Coverage Guard**: 100% of declared system requirements (`SYSREQ-TRK-001`..`006`, `SYSREQ-DEX-001`..`006`) must be refined by at least one software requirement.
3. **Tailoring Consistency Guard**: All software requirements belong either to refined system requirement domains or tailored software-derived prefix families.
