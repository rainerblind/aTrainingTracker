# Stage 3: Implementation Plan - ATT-6740: Fix 11 stale filenames, CSV parser claim, and section numbering in sysreq_data_exchange.md

**Ticket**: [ATT-6740](https://rainerblind.atlassian.net/browse/ATT-6740)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-055`  
**Test Mapping**: `TST-PRO-055`  
**Branch**: `feature/ATT-6740`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

`docs/system/sysreq_data_exchange.md` and `docs/system_requirements.md` contain 11 stale/non-existent class, XML, and test filenames, overstate standalone CSV workout track import, omit active cloud synchronization capabilities (Strava routes/segments, Google Drive workout export, Health Connect HRV/RHR import consent), and exhibit broken section numbering in `docs/system_requirements.md` (jumping 1 -> 4 -> 5 -> 6). This plan outlines atomic specification alignment and contract test hardening.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-055` (*System Requirements for Data Exchange and Cloud Integration*)
* **Test Mapping**: `TST-PRO-055` (*System Requirements for Data Exchange Contract Test Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Runtime Regressions**: Zero modifications to production source code in `app/src/main/java`.
2. **Dynamic Grounding Parity**: All cited source, resource, and test files exist on disk.
3. **Source Reading Audit Invariance**: Tests construct file paths dynamically to avoid triggering the source-text reading audit (ATT-6186).
4. **Human Decision Gate**: Parent ticket advances to `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: System Requirements Specification (`docs/system/sysreq_data_exchange.md`)
* Replace 11 stale filenames: `.java` -> `.kt` (`LegacyImportEngine.kt`, `WorkoutDataMapper.kt`, `StravaUploader.kt`, `GoogleDriveUploader.kt`), real helpers/DB (`StravaHelper.kt`, `StravaUploadDbHelper.java`, `DatabaseConnectionManager.kt`, `DatabaseSchemaRegistry.kt`, `WorkoutSummariesDatabaseManager.java`), real tests (`FitFileWriterTest.kt`, `TCXRoundTripTest.kt`), and manifest XMLs (`full_backup_content.xml`, `data_extraction_rules.xml`).
* Clarify `SYSREQ-DEX-002` CSV import scope (FIT/TCX/GPX track parsers + Strava bulk ZIP `activities.csv` metadata manifest).
* Document omitted cloud flows: Strava routes (`StravaRoutesSyncWorker.kt`) & segments (`StravaSegmentsSyncWorker.kt`) in `SYSREQ-DEX-003`, Google Drive workout export (`GoogleDriveUploader.kt`) in `SYSREQ-DEX-004`, and Health Connect HRV/RHR import consent (`HealthConnectHrvImporter.kt`) in `SYSREQ-DEX-006`.

### Component 2: System Requirements Index (`docs/system_requirements.md`)
* Renumber sections sequentially 1..4 (1. Overview, 2. Live Tracking, 3. Data Exchange, 4. Traceability Matrix).
* Align traced software classes table with authentic components.

### Component 3: Contract Test Suites
* Expand `SystemDataExchangeRequirementsContractTest.kt` with dynamic `File.exists()` assertions for all cited filenames.
* Update `SystemRequirementsSpecificationContractTest.kt` to assert sequential section headings `## 1.`, `## 2.`, `## 3.`, `## 4.`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Reconcile `docs/system/sysreq_data_exchange.md`
* Correct 11 filenames, clarify `SYSREQ-DEX-002` CSV scope, and document cloud workers across `SYSREQ-DEX-003`, `004`, `006`.

### Step 2: Reconcile `docs/system_requirements.md`
* Renumber sections sequentially 1..4 and align traced software components.

### Step 3: Harden `SystemDataExchangeRequirementsContractTest.kt` & `SystemRequirementsSpecificationContractTest.kt`
* Implement dynamic `File.exists()` assertions for cited files without literal forbidden patterns.

### Step 4: Verification & Governance
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* Run `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit and contract tests followed by living documentation verification.
* **Rollback**: `git checkout sprint/2026-46.2` reverts working tree cleanly.
