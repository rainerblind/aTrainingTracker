# Stage 1 Analysis: ATT-6740 - Fix 11 stale filenames, CSV parser claim, and section numbering in sysreq_data_exchange.md

**Ticket**: [ATT-6740](https://rainerblind.atlassian.net/browse/ATT-6740)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6740`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During Sprint 2026-46.1 review of ATT-6161, an automated source-grounding audit identified:
1. 11 stale or non-existent class, XML, and test filenames across `docs/system/sysreq_data_exchange.md` and `docs/system_requirements.md`.
2. Overstated CSV workout import claim in `SYSREQ-DEX-002` (claims standalone 1-Hz CSV track ingestion; actual codebase only parses Strava `activities.csv` metadata via `StravaCsvParser.kt`, while workout tracks use FIT, TCX, GPX parsers).
3. Omission of existing cloud capabilities: Strava routes/segments sync (`SYSREQ-DEX-003`), Google Drive workout file uploads (`SYSREQ-DEX-004`), and Health Connect HRV/RHR import consent (`SYSREQ-DEX-006`).
4. Broken section numbering in `docs/system_requirements.md` (jumps from `## 1.` directly to `## 4.`, `## 5.`, `## 6.`).
5. Contract test gap: `SystemDataExchangeRequirementsContractTest.kt` lacks dynamic `File.exists()` verification for cited components.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Live Grounding Verification**:
  * *Kotlin Extensions*: `LegacyImportEngine.kt`, `WorkoutDataMapper.kt`, `StravaUploader.kt`, and `GoogleDriveUploader.kt` exist as Kotlin sources in `app/src/main/java`, but specifications reference `.java`.
  * *Helper & Database Classes*: `StravaUploadHelper.kt` does not exist; real classes are `StravaHelper.kt` and `StravaUploadDbHelper.java`. `DatabaseHelper.java` and `WorkoutDataHelper.java` do not exist; real classes are `DatabaseConnectionManager.kt`, `DatabaseSchemaRegistry.kt`, and `WorkoutSummariesDatabaseManager.java`.
  * *Unit Tests*: `FitExportTest` and `TCXExportTest` do not exist; real tests are `FitFileWriterTest.kt` and `TCXRoundTripTest.kt`.
  * *Auto Backup Manifest*: `backup_rules.xml` does not exist; authentic files are `app/src/main/res/xml/full_backup_content.xml` and `data_extraction_rules.xml`.
  * *CSV & Cloud Sync*: Verified `migration/parsers/` implements only `FitWorkoutParser`, `GpxWorkoutParser`, and `TcxWorkoutParser`; `StravaRoutesSyncWorker.kt`, `StravaSegmentsSyncWorker.kt`, and `HealthConnectHrvImporter.kt` exist on sprint branch.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Correct all 11 stale/non-existent class, XML, and test filenames in `docs/system/sysreq_data_exchange.md` and `docs/system_requirements.md`.
  * Clarify `SYSREQ-DEX-002` (FIT/TCX/GPX workout parsers + Strava bulk ZIP `activities.csv` metadata manifest) and document omitted routes/segments, Google Drive workout uploads, and Health Connect HRV/RHR consent.
  * Renumber sections in `docs/system_requirements.md` sequentially (`1..4`) and update contract test assertions.
  * Extend `SystemDataExchangeRequirementsContractTest.kt` and `SystemRequirementsSpecificationContractTest.kt` to dynamically verify `File.exists()` on disk without reading production source text.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Zero modifications to Android production runtime code in `app/src/main/java`.
  * No modifications to database schemas, cloud REST endpoints, or worker implementations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-055` / `TST-PRO-055` (*System Requirements for Data Exchange and Cloud Integration*).
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding S-03 (2026-11-08) and ticket ATT-6161 (commit `348ff4a7`).
* **Root Reason for Existing Formulation**: Initial SYS.2 draft included legacy naming assumptions and file extension typos from pre-Kotlin codebase layers.
* **Preservation of Core Invariants**: All 6 requirements `SYSREQ-DEX-001..006` remain fully traced to `SYS-EL-01..05`; exact bidirectional consistency and black-box behavioral boundaries are preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. Align specification text and tables in `sysreq_data_exchange.md` and `docs/system_requirements.md`.
2. Renumber sections in `docs/system_requirements.md` (1: Overview, 2: Live Tracking, 3: Data Exchange, 4: Traceability Matrix).
3. Enhance `SystemDataExchangeRequirementsContractTest.kt` and `SystemRequirementsSpecificationContractTest.kt` to dynamically assert `File.exists()` for all cited source, resource, and test files on disk.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero production runtime bytecode changes.
  2. 100% unit and contract test pass rate.
  3. Zero source-text reading audit violations (ATT-6186).
* **Risk Rating**: **LOW** (documentation precision and contract test hardening).
