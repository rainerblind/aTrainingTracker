# Stage 1 Analysis: ATT-6161 - Specify system requirements for data exchange and cloud integration

**Ticket**: [ATT-6161](https://rainerblind.atlassian.net/browse/ATT-6161)  
**Sub-task**: [ATT-6650](https://rainerblind.atlassian.net/browse/ATT-6650) (`[Analysis]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6161`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
ASPICE 4.0 pre-assessment finding S-03 (SYS.2 BP1-BP3) identified that external data interfaces (Strava OAuth2 upload & gear sync, Dropbox API v2 export & cloud backup, Google Drive REST API v3 database synchronization, Health Connect biometrics, and multi-format file export/import in FIT, TCX, GPX, CSV) and full application state backup (`.attbackup`) lack formal system-level specifications. While software components exist (`ExportManager`, `StravaUploader`, `DropboxUploader`, `GoogleDriveBackupManager`, `LegacyImportEngine`), external data contracts, privacy/security boundaries, and failure modes are not specified at the system level. Establishing `docs/system/sysreq_data_exchange.md` satisfies ASPICE SYS.2 BP1–BP3.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Missing System-Level Data Exchange Specification (SYS.2 BP1-BP3 Gap)**:
   - Data exchange contracts and cloud synchronizations were developed iteratively as software classes without high-level system requirements specifying format schemas, rate-limiting, retry policies, privacy boundaries, or backup atomic guarantees.
2. **Independent Peer Review & Code-Grounding Verification (ATT-5431)**:
   - Audited Exporters: `FitFileWriter.java`, `TCXFileWriter.java`, `GPXFileWriter.java`, `CSVFileWriter.java` (in `FileFormat.java`: standard formats `.fit`, `.tcx`, `.gpx`, `.csv`).
   - Audited Cloud Uploaders: `StravaUploader.kt` (OAuth2 multipart upload to `/api/v3/uploads`, rate limiting), `DropboxUploader.java` (`DbxClientV2`), `GoogleDriveUploader.kt` (`GoogleDriveClient`).
   - Audited Backup & Recovery: `BackupManager.kt` (`BACKUP_FILENAME = "aTrainingTracker_backup.attbackup"`, 1.5x storage space pre-verification, WAL snapshotting, atomic move), `DropboxBackupManager.kt` (`/Backups/aTrainingTracker_backup.attbackup`), `GoogleDriveBackupManager.kt` (`aTrainingTracker/Backups/`).
   - Audited Security & Privacy: `BackupRulesXmlValidationTest.kt` & `BackupManagerCredentialExclusionTest.kt` (strict exclusion of `auth_credentials_prefs.xml` and credential purge from `.attbackup`), `SecureZipExtractor.kt` (Zip Slip path traversal prevention).
   - Audited Health Connect & Decommissioned Platforms: `HealthConnectManager.kt` (on-device exercise/HR biometrics sync with explicit permission checks); Runkeeper and TrainingPeaks APIs marked permanently decommissioned.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Author authoritative system requirements document `docs/system/sysreq_data_exchange.md` defining formal `SYSREQ-DEX-*` requirements:
    (1) Multi-format workout export (FIT, TCX, GPX, CSV standard schemas),
    (2) Multi-format workout file import & deduplication (FIT, TCX, GPX, CSV, duplicate collision detection),
    (3) Cloud activity upload (Strava OAuth2 multipart upload, rate-limiting, gear sync),
    (4) Cloud storage & backup sync (Dropbox API v2 and Google Drive REST API v3 `.attbackup` synchronization),
    (5) Application state backup & recovery (`.attbackup` atomic snapshots, 1.5x space check, Zip Slip protection),
    (6) Security, privacy & decommissioned boundaries (credential exclusion, Health Connect user consent, decommissioned Runkeeper/TrainingPeaks).
  - Structure each requirement with: ID, Title, Statement (`SHALL`/`MUST`), Rationale, Priority, Verification Method, and Traced Software Components.
  - Define living requirement `REQ-PRO-055` in `docs/requirements/req_process_stability.md` and test spec `TST-PRO-055` in `docs/tests/tst_process_stability.md`.
  - Author automated contract test `SystemDataExchangeRequirementsContractTest.kt` under `app/src/test/.../build/` enforcing document structure and code grounding.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying live runtime exporter logic or network API implementations in `app/src/main/`.
  - Altering SQLite schema or database tables.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-055` (*System Requirements for Data Exchange and Cloud Integration*), establishing ASPICE SYS.2 BP1-BP3 baseline under Parent Epic ATT-6142. Targets `docs/system/sysreq_data_exchange.md` and `SystemDataExchangeRequirementsContractTest.kt`.
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding S-03 (2026-11-08) and ticket ATT-6161.
* **Root Reason for Existing Formulation**: Project previously managed data exchange solely at software implementation level without black-box system requirements specifying external data contracts, failure modes, and privacy constraints.
* **Preservation of Core Invariants**: Zero runtime code changes; existing FIT/TCX/GPX/CSV serialization, Strava/Dropbox/Google Drive API contracts, and backup atomicity remain 100% unaltered.

---

## 5. Architectural Strategy & High-Level Solution
* `docs/system/sysreq_data_exchange.md`: Specify complete system requirements catalog:
  - `SYSREQ-DEX-001`: Multi-Format Telemetry Export Interoperability (FIT, TCX, GPX, CSV).
  - `SYSREQ-DEX-002`: Multi-Format Workout Ingestion & Collision Deduplication.
  - `SYSREQ-DEX-003`: Cloud Activity Synchronization & Rate Governance (Strava OAuth2).
  - `SYSREQ-DEX-004`: Cloud Storage Synchronization & Automatic Backup (Dropbox & Google Drive).
  - `SYSREQ-DEX-005`: Full State Backup & Atomic Recovery Pipeline (`.attbackup`).
  - `SYSREQ-DEX-006`: Credential Protection, Security Isolation & Decommissioned Boundaries.
* `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemDataExchangeRequirementsContractTest.kt`: JUnit 4 contract test verifying document existence, mandatory attributes, and 100% code grounding against production constants without source reading.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero modifications to production code in `app/src/main/`; 100% unit test pass rate; living documentation index parity; Parent ticket Human Decision Gate preserved.
* **Risk Rating**: **LOW** (2 SP, documentation and contract testing baseline without runtime code impacts).
