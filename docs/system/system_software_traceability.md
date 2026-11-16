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
| `SYSREQ-TRK-001` | Multi-Sensor Profile Interoperability | `REQ-CON-001`, `REQ-CON-002`, `REQ-CON-003`, `REQ-CON-004`, `REQ-CON-005`, `REQ-CON-006`, `REQ-CON-007`, `REQ-CON-009`, `REQ-CON-010`, `REQ-CON-011`, `REQ-CON-017`, `REQ-CON-028`, `REQ-SEN-020`, `REQ-SEN-021`, `REQ-SEN-025`, `REQ-SEN-026`, `REQ-SEN-027`, `REQ-SEN-028`, `REQ-SEN-029`, `REQ-SEN-030` | `BANALService.java`, `BluetoothConstants.java`, `BTLERadarDevice.java`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-002` | Reconnection Resilience & Watchdog | `REQ-CON-005`, `REQ-CON-012`, `REQ-CON-018`, `REQ-CON-019`, `REQ-CON-020`, `REQ-CON-023`, `REQ-CON-024`, `REQ-CON-025`, `REQ-CON-026`, `REQ-CON-027`, `REQ-CON-028`, `REQ-CON-029`, `REQ-CON-030`, `REQ-CON-031`, `REQ-CON-032`, `REQ-CON-033`, `REQ-CON-035`, `REQ-CON-036`, `REQ-CON-037`, `REQ-CON-038`, `REQ-CON-039`, `REQ-CON-040`, `REQ-CON-041`, `REQ-CON-042`, `REQ-CON-043`, `REQ-TRK-013` | `MyBTLEDevice.java`, `BTLERadarDevice.java`, `FilterManager.kt`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-003` | Deterministic 1-Hz Sampling Loop | `REQ-FIL-001`, `REQ-FIL-002`, `REQ-FIL-003`, `REQ-FIL-004`, `REQ-FIL-005`, `REQ-FIL-006`, `REQ-FIL-007`, `REQ-FIL-008`, `REQ-FIL-009`, `REQ-FIL-010`, `REQ-FIL-011`, `REQ-FIL-012`, `REQ-FIL-013`, `REQ-FIL-014`, `REQ-FIL-015`, `REQ-FIL-016`, `REQ-FIL-017`, `REQ-FIL-018`, `REQ-FIL-019`, `REQ-SEN-020`, `REQ-SEN-021`, `REQ-SEN-025`, `REQ-SEN-028`, `REQ-SEN-029`, `REQ-TRK-001`, `REQ-TRK-002`, `REQ-TRK-004`, `REQ-TRK-007`, `REQ-TRK-009`, `REQ-TRK-010`, `REQ-TRK-069` | `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-004` | Multi-Source GNSS Positioning | `REQ-CLM-008`, `REQ-CLM-009`, `REQ-CLM-010`, `REQ-CLM-011`, `REQ-FIL-003`, `REQ-FIL-004`, `REQ-FIL-006`, `REQ-FIL-007`, `REQ-FIL-009`, `REQ-FIL-010`, `REQ-FIL-012`, `REQ-FIL-013`, `REQ-FIL-016`, `REQ-FIL-017`, `REQ-FIL-018`, `REQ-FIL-019`, `REQ-GEO-001`, `REQ-GEO-002`, `REQ-MAP-003`, `REQ-MAP-004`, `REQ-MAP-005`, `REQ-MAP-008`, `REQ-MAP-009`, `REQ-MAP-011`, `REQ-MAP-012`, `REQ-MAP-016`, `REQ-MAP-018`, `REQ-MAP-019`, `REQ-MAP-020`, `REQ-MAP-021`, `REQ-MAP-022`, `REQ-MAP-023`, `REQ-MAP-024`, `REQ-MAP-025`, `REQ-MAP-026`, `REQ-MAP-027`, `REQ-MAP-028`, `REQ-MAP-029`, `REQ-MAP-030`, `REQ-MAP-031`, `REQ-MAP-032`, `REQ-MAP-033`, `REQ-MAP-034`, `REQ-MAP-035`, `REQ-MAP-036`, `REQ-MAP-037`, `REQ-MAP-038`, `REQ-MAP-039`, `REQ-MAP-040`, `REQ-MAP-041`, `REQ-MAP-042`, `REQ-MAP-044`, `REQ-MAP-045`, `REQ-MAP-046`, `REQ-MAP-047`, `REQ-MAP-048`, `REQ-MAP-049`, `REQ-MAP-050`, `REQ-MAP-051`, `REQ-MAP-052`, `REQ-MAP-053`, `REQ-MAP-054`, `REQ-MAP-055`, `REQ-MAP-056`, `REQ-MAP-057`, `REQ-MAP-058`, `REQ-MAP-059`, `REQ-MAP-060`, `REQ-MAP-061`, `REQ-MAP-062`, `REQ-MAP-063`, `REQ-MAP-064`, `REQ-MAP-065`, `REQ-MAP-066`, `REQ-MAP-067`, `REQ-NAV-043`, `REQ-NAV-056`, `REQ-TRK-003`, `REQ-TRK-011`, `REQ-TRK-012`, `REQ-TRK-013`, `REQ-TRK-014`, `REQ-TRK-017`, `REQ-TRK-022`, `REQ-TRK-024`, `REQ-TRK-025`, `REQ-TRK-026`, `REQ-TRK-029`, `REQ-TRK-030`, `REQ-TRK-031`, `REQ-TRK-032`, `REQ-TRK-034`, `REQ-TRK-036`, `REQ-TRK-037`, `REQ-TRK-039`, `REQ-TRK-040`, `REQ-TRK-041`, `REQ-TRK-042`, `REQ-TRK-043`, `REQ-TRK-044`, `REQ-TRK-047`, `REQ-TRK-048`, `REQ-TRK-049`, `REQ-TRK-050`, `REQ-TRK-052`, `REQ-TRK-055`, `REQ-TRK-056`, `REQ-TRK-057`, `REQ-TRK-062`, `REQ-TRK-066`, `REQ-TRK-070`, `REQ-TRK-071`, `REQ-TRK-072`, `REQ-TRK-074`, `REQ-TRK-075`, `REQ-TRK-076`, `REQ-TRK-077`, `REQ-TRK-078`, `REQ-TRK-079`, `REQ-TRK-080`, `REQ-TRK-081` | `SpeedAndLocationDevice_GPS.java`, `SpeedAndLocationDevice_GoogleFused.java`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-005` | Foreground Service & Power Management | `REQ-TRK-003`, `REQ-TRK-005`, `REQ-TRK-008`, `REQ-TRK-015`, `REQ-TRK-016`, `REQ-TRK-023`, `REQ-TRK-028`, `REQ-TRK-033`, `REQ-TRK-045`, `REQ-TRK-046`, `REQ-TRK-051`, `REQ-TRK-053`, `REQ-TRK-058`, `REQ-TRK-059`, `REQ-TRK-063`, `REQ-TRK-064` | `TrackerService.java`, `TrainingApplication.java`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-006` | Real-Time Alerts & Cockpit Feedback | `REQ-CON-013`, `REQ-CON-014`, `REQ-CON-015`, `REQ-CON-016`, `REQ-CON-021`, `REQ-CON-022`, `REQ-CON-028`, `REQ-CON-034`, `REQ-GEO-001`, `REQ-GEO-002`, `REQ-NAV-043`, `REQ-NAV-056`, `REQ-RAD-005`, `REQ-TRK-018`, `REQ-TRK-019`, `REQ-TRK-020`, `REQ-TRK-021`, `REQ-TRK-027`, `REQ-TRK-035`, `REQ-TRK-038`, `REQ-TRK-054`, `REQ-TRK-060`, `REQ-TRK-061`, `REQ-TRK-065`, `REQ-TRK-067`, `REQ-TRK-068`, `REQ-TRK-073`, `REQ-TRK-082` | `SensorGridScreen.kt`, `RadarEdgeOverlay.kt`, `RadarAlertManager.kt`, `SystemLiveTrackingRequirementsContractTest.kt` |

### 2.2 Domain 2: Data Exchange, Cloud Integration & Disaster Recovery (`SYSREQ-DEX-*`)

| System Requirement | System Title | Refining Software Requirements (`REQ-*`) | Grounded Classes & Verification Test |
| :--- | :--- | :--- | :--- |
| `SYSREQ-DEX-001` | Multi-Format Telemetry Export | `REQ-DAT-012`, `REQ-DAT-013`, `REQ-DAT-017`, `REQ-DAT-018`, `REQ-DAT-023`, `REQ-DAT-027`, `REQ-DAT-028`, `REQ-DAT-030`, `REQ-DAT-031`, `REQ-DAT-032`, `REQ-DAT-037`, `REQ-DAT-038`, `REQ-DAT-039`, `REQ-DAT-040`, `REQ-DAT-041`, `REQ-DAT-042`, `REQ-DAT-043`, `REQ-EXP-001`, `REQ-EXP-022`, `REQ-EXP-042` | `ExportManager.java`, `FitFileWriter.kt`, `TCXFileWriter.kt`, `GPXFileWriter.java`, `CSVFileWriter.java`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-002` | Multi-Format Workout Ingestion & Deduplication | `REQ-DAT-001`, `REQ-DAT-014`, `REQ-DAT-019`, `REQ-DAT-021`, `REQ-DAT-025`, `REQ-DAT-034`, `REQ-MIG-018`, `REQ-MIG-022`, `REQ-MIG-024`, `REQ-MIG-033`, `REQ-MIG-037`, `REQ-MIG-039`, `REQ-MIG-046`, `REQ-MIG-051`, `REQ-MIG-053` | `LegacyImportEngine.kt`, `StravaBulkExportImporter.kt`, `StravaCsvParser.kt`, `WorkoutSamplesDatabaseManager.java`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-003` | Cloud Activity Sync (Strava) | `REQ-EXP-002`, `REQ-EXP-003`, `REQ-EXP-005`, `REQ-EXP-006`, `REQ-EXP-007`, `REQ-EXP-008`, `REQ-EXP-009`, `REQ-EXP-010`, `REQ-EXP-011`, `REQ-EXP-012`, `REQ-EXP-013`, `REQ-EXP-014`, `REQ-EXP-015`, `REQ-EXP-016`, `REQ-EXP-017`, `REQ-EXP-019`, `REQ-EXP-021`, `REQ-EXP-023`, `REQ-EXP-043`, `REQ-EXT-001`, `REQ-EXT-002`, `REQ-EXT-003`, `REQ-EXT-004`, `REQ-EXT-005`, `REQ-EXT-006`, `REQ-EXT-007`, `REQ-EXT-008`, `REQ-EXT-010`, `REQ-RAD-007` | `StravaUploader.kt`, `StravaEquipmentSyncWorker.kt`, `StravaRoutesSyncWorker.kt`, `StravaSegmentsSyncWorker.kt`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-004` | Cloud Storage Sync (Dropbox & GDrive) | `REQ-DAT-020`, `REQ-DAT-022`, `REQ-DAT-024`, `REQ-DAT-029`, `REQ-EXP-018`, `REQ-MIG-002`, `REQ-MIG-003`, `REQ-MIG-007`, `REQ-MIG-008`, `REQ-MIG-009`, `REQ-MIG-010`, `REQ-MIG-019`, `REQ-MIG-028`, `REQ-MIG-030`, `REQ-MIG-031`, `REQ-MIG-034`, `REQ-MIG-035`, `REQ-MIG-036`, `REQ-MIG-038`, `REQ-MIG-040`, `REQ-MIG-049`, `REQ-MIG-050`, `REQ-MIG-052` | `DropboxBackupManager.kt`, `GoogleDriveBackupManager.kt`, `GoogleDriveUploader.kt`, `BackupWorker.kt`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-005` | Full State Backup & Atomic Recovery | `REQ-DAT-004`, `REQ-DAT-005`, `REQ-DAT-006`, `REQ-DAT-007`, `REQ-DAT-008`, `REQ-DAT-009`, `REQ-DAT-011`, `REQ-DAT-015`, `REQ-DAT-016`, `REQ-DAT-020`, `REQ-DAT-026`, `REQ-DAT-033`, `REQ-DAT-035`, `REQ-DAT-036`, `REQ-DAT-044`, `REQ-MIG-001`, `REQ-MIG-004`, `REQ-MIG-005`, `REQ-MIG-006`, `REQ-MIG-011`, `REQ-MIG-012`, `REQ-MIG-013`, `REQ-MIG-014`, `REQ-MIG-015`, `REQ-MIG-016`, `REQ-MIG-017`, `REQ-MIG-020`, `REQ-MIG-021`, `REQ-MIG-023`, `REQ-MIG-025`, `REQ-MIG-026`, `REQ-MIG-027`, `REQ-MIG-029`, `REQ-MIG-032`, `REQ-MIG-041`, `REQ-MIG-042`, `REQ-MIG-043`, `REQ-MIG-044`, `REQ-MIG-045`, `REQ-MIG-047`, `REQ-MIG-048` | `BackupManager.kt`, `SecureZipExtractor.kt`, `BackupRestoreViewModel.kt`, `DatabaseConnectionManager.kt`, `SystemDataExchangeRequirementsContractTest.kt` |
| `SYSREQ-DEX-006` | Security Isolation & Credentials Shield | `REQ-DAT-024`, `REQ-EXP-020`, `REQ-EXT-009`, `REQ-EXT-011`, `REQ-EXT-012` | `AuthCredentialsStore.kt`, `full_backup_content.xml`, `data_extraction_rules.xml`, `HealthConnectHrvImporter.kt`, `SystemDataExchangeRequirementsContractTest.kt` |

---

## 3. Normative Prefix Tailoring Rules for Software-Derived Requirements

In accordance with ASPICE SWE.1 BP5, requirements that originate directly from software architecture, user interface design decisions, mathematical computations, or engineering process governance are classified as **Software-Derived** (`[SWE-Derived]`). These requirements refine the software system without a standalone physical system-level parent requirement.

### 3.1 Domain & Prefix Tailoring Matrix

| Domain Shard | Prefixes | Tailoring Classification | Documented Architectural Rationale |
| :--- | :--- | :--- | :--- |
| **Tracking Cockpit** (`req_tracking_cockpit.md`) | `CON`, `FIL`, `FUSION`, `RAD`, `SEN`, `TRK`, `WEA` | **System Refinement** | Refines `SYSREQ-TRK-001` through `SYSREQ-TRK-006` (BLE/ANT+ sensors, 1-Hz sampling loop, GPS provider, foreground service, and radar alerts). |
| **Persistence & Cloud** (`req_data_migration_export.md`) | `DAT`, `EXP`, `EXT`, `MIG`, `RAD` | **System Refinement** | Refines `SYSREQ-DEX-001` through `SYSREQ-DEX-006` (FIT/TCX/GPX/CSV export, workout ingestion, Strava sync, cloud storage, `.attbackup`, and security isolation). |
| **Maps & Navigation** (`req_maps_navigation.md`) | `CLM`, `GEO`, `MAP`, `NAV`, `RAD`, `ROU` | **System Refinement & SWE-Derived** | Polyline rendering, GPX follow-me, and routing trace to `SYSREQ-TRK-004` (GNSS acquisition) with software-derived map UI visualization contracts. |
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
