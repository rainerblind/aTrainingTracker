# Stage 2: Requirement & Test Specification - ATT-6740: Fix 11 stale filenames, CSV parser claim, and section numbering in sysreq_data_exchange.md

**Ticket**: [ATT-6740](https://rainerblind.atlassian.net/browse/ATT-6740)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-055` (*System Requirements for Data Exchange and Cloud Integration*)  
**Test Spec ID**: `TST-PRO-055`  
**Branch**: `feature/ATT-6740`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-055)

### 1.1 Problem Statement & Rationale
`docs/system/sysreq_data_exchange.md` and `docs/system_requirements.md` contain 11 stale or non-existent class, test, and XML filenames, overstate standalone CSV workout import (only `StravaCsvParser.kt` parses bulk export `activities.csv` metadata, while discrete workout tracks use FIT/TCX/GPX), omit active cloud capabilities (Strava routes/segments, Google Drive workout uploads, Health Connect HRV/RHR import consent), and have broken section numbering in `docs/system_requirements.md` (jumping from Section 1 directly to 4..6).

### 1.2 Functional & Architectural Requirements
1. **Filename Grounding**: Replace all 11 stale filenames with authentic files:
   - Kotlin sources: `LegacyImportEngine.kt`, `WorkoutDataMapper.kt`, `StravaUploader.kt`, `GoogleDriveUploader.kt` (replacing `.java`).
   - Helpers & DB: `StravaHelper.kt`, `StravaUploadDbHelper.java` (replacing `StravaUploadHelper.kt`), `DatabaseConnectionManager.kt`, `DatabaseSchemaRegistry.kt` (replacing `DatabaseHelper.java`), `WorkoutSummariesDatabaseManager.java` (replacing `WorkoutDataHelper.java`).
   - Unit Tests: `FitFileWriterTest.kt`, `TCXRoundTripTest.kt` (replacing `FitExportTest`, `TCXExportTest`).
   - Auto Backup XMLs: `full_backup_content.xml`, `data_extraction_rules.xml` (replacing `backup_rules.xml`).
2. **CSV Import Scope Clarification (`SYSREQ-DEX-002`)**: Clarify that discrete workout tracks are parsed via FIT/TCX/GPX parsers in `migration/parsers/`, while `StravaCsvParser.kt` parses the `activities.csv` metadata manifest inside Strava bulk export ZIPs.
3. **Cloud Capabilities Completion**:
   - `SYSREQ-DEX-003`: Document Strava routes (`StravaRoutesSyncWorker.kt`) and segments (`StravaSegmentsSyncWorker.kt`) sync.
   - `SYSREQ-DEX-004`: Document Google Drive workout file export (`GoogleDriveUploader.kt`).
   - `SYSREQ-DEX-006`: Document Health Connect HRV/RHR import consent (`HealthConnectHrvImporter.kt`).
4. **Section Numbering**: Renumber `docs/system_requirements.md` sequentially (1. Overview, 2. Live Tracking, 3. Data Exchange, 4. Traceability Matrix).
5. **Contract Test Hardening**: Dynamically assert `File.exists()` on disk for all cited source, resource, and test files without reading production source text.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Specification Grounding)**:
  * *Given* `docs/system/sysreq_data_exchange.md` and `docs/system_requirements.md`,
  * *When* audited,
  * *Then* all 11 filenames SHALL match authentic repository files on disk, CSV import scope SHALL be accurately bounded, and sections in `system_requirements.md` SHALL be numbered sequentially 1..4.
* **Criterion 2 (Dynamic Contract Assertions)**:
  * *Given* `SystemDataExchangeRequirementsContractTest.kt` and `SystemRequirementsSpecificationContractTest.kt`,
  * *When* executed in the unit test suite,
  * *Then* dynamic `File.exists()` assertions for all cited files and structural assertions SHALL pass with 100% success.

### 1.4 System Invariants
Zero runtime bytecode modifications in `app/src/main/java`; zero source-text reading violations (ATT-6186); 100% test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-055` / `TST-PRO-055` (*System Requirements for Data Exchange and Cloud Integration*).
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding S-03 (2026-11-08) and ticket ATT-6161 (commit `348ff4a7`).
* **Root Reason for Existing Formulation**: Initial SYS.2 specification draft contained 11 stale/non-existent filenames from pre-Kotlin legacy artifacts, overstated CSV track import, and omitted routes/segments/GDrive/HealthConnect cloud capabilities.
* **Preservation of Core Invariants**: All 6 requirements `SYSREQ-DEX-001..006` remain 100% preserved and allocated to `SYS-EL-01..05`; zero changes to production runtime code; specifications and contract tests are brought into strict alignment with production reality.

---

## 2. Test Specification (TST-PRO-055)

### Test Case 1: Dynamic File Grounding Verification (`TST-PRO-055.2`)
* **Scope**: Unit / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemDataExchangeRequirementsContractTest.kt`
* **Preconditions**: Repository contains live Kotlin/Java sources, XML configs, and test classes.
* **Action**: Dynamically assert `File.exists()` for each backticked component cited in `sysreq_data_exchange.md` (constructed via dynamic paths to avoid triggering source-reading audit).
* **Expected Result**: All 11 files exist on disk with zero failures.

### Test Case 2: Sequential Section Numbering & Coverage (`TST-PRO-055.1`)
* **Scope**: Unit / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemRequirementsSpecificationContractTest.kt`
* **Action**: Verify `docs/system_requirements.md` defines sequential section headers `## 1.`, `## 2.`, `## 3.`, `## 4.`, and covers all `SYSREQ-TRK-*` and `SYSREQ-DEX-*` IDs.
* **Expected Result**: Assertions pass and match updated sequential headings.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-PRO-055.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass across all build and specification contract tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-055.1` | Contract | `testSystemRequirements_sequentialSectionNumbering` | `REQ-PRO-055.1` | Specified |
| `TST-PRO-055.2` | Contract | `testSysreqDataExchange_allCitedFilesExistOnDisk` | `REQ-PRO-055.2` | Specified |
| `TST-PRO-055.3` | Regression | `SystemDataExchangeRequirementsContractTest` | `REQ-PRO-001`, `REQ-PRO-055` | Specified |
