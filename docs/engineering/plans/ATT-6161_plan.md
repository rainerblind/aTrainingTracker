# Stage 3: Implementation Plan - ATT-6161: Specify system requirements for data exchange and cloud integration

**Ticket**: [ATT-6161](https://rainerblind.atlassian.net/browse/ATT-6161)  
**Sub-task**: [ATT-6654](https://rainerblind.atlassian.net/browse/ATT-6654) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-PRO-055` (*System Requirements for Data Exchange and Cloud Integration*)  
**Test Mapping**: `TST-PRO-055`  
**Branch**: `feature/ATT-6161`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Description & Background
ASPICE 4.0 pre-assessment finding S-03 (SYS.2 BP1-BP3) identified that external data interfaces (multi-format workout export/import in FIT, TCX, GPX, CSV, cloud synchronization with Strava, Dropbox, Google Drive, Health Connect biometrics, and full application state backup/restore via `.attbackup`) lack formal system-level specifications. While software classes exist, black-box system requirements specifying data schemas, API rate governance, atomic backup transactions, Zip Slip path traversal defenses, credential isolation, and decommissioned platform boundaries are missing. Specifying these capabilities in `docs/system/sysreq_data_exchange.md` and verifying code grounding via contract tests closes this ASPICE CL2 gap.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-055` (*System Requirements for Data Exchange and Cloud Integration*)
* **Test Mapping**: `TST-PRO-055` (*System Requirements for Data Exchange Contract Test Suite*)
* **Traceability Matrix**:
  - `REQ-PRO-055.1` -> `TST-PRO-055.1`, `TST-PRO-055.2` (`docs/system/sysreq_data_exchange.md` specification & code grounding)
  - `REQ-PRO-055.2` -> `TST-PRO-055.2`, `TST-PRO-055.3` (`SystemDataExchangeRequirementsContractTest.kt` targeted unit suite execution)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Code Modification**: No changes to production classes in `app/src/main/` or database schemas.
2. **Credential & Privacy Isolation**: Strict exclusion of `auth_credentials_prefs.xml` from `.attbackup` and Android Auto Backup.
3. **Backup Atomicity & Integrity**: Pre-verification of 1.5x storage space, WAL snapshotting, and Zip Slip defenses preserved.
4. **Clean-Room Verification**: 100% test pass rate across targeted contract test suites and requirement index parity.

---

## 4. Proposed Architectural Changes
* **System Requirements Specification (`docs/system/sysreq_data_exchange.md`)**:
  - Author authoritative system-level requirements document under `docs/system/`.
  - Specify 6 formal requirements:
    1. `SYSREQ-DEX-001`: Multi-Format Telemetry Export Interoperability (FIT, TCX, GPX, CSV).
    2. `SYSREQ-DEX-002`: Multi-Format Workout Ingestion & Collision Deduplication.
    3. `SYSREQ-DEX-003`: Cloud Activity Synchronization & Rate Governance (Strava OAuth2 multipart upload & gear sync).
    4. `SYSREQ-DEX-004`: Cloud Storage Synchronization & Automatic Backup (Dropbox API v2 & Google Drive REST API v3).
    5. `SYSREQ-DEX-005`: Full Application State Backup & Atomic Recovery Pipeline (`.attbackup`, 1.5x space check, Zip Slip protection).
    6. `SYSREQ-DEX-006`: Credential Protection, Security Isolation & Decommissioned Boundaries (`auth_credentials_prefs` exclusion, Health Connect user consent, decommissioned Runkeeper/TrainingPeaks).
  - Include attributes for each: ID, Title, Formal Statement (`SHALL`/`MUST`), Rationale, Priority, Verification Method, Traced Software Components.
* **System Requirements Contract Test (`SystemDataExchangeRequirementsContractTest.kt`)**:
  - Create `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemDataExchangeRequirementsContractTest.kt`.
  - Assert existence and completeness of `docs/system/sysreq_data_exchange.md`.
  - Assert 1:1 code grounding against constants in `FileFormat.java`, `BackupManager.kt`, and security rules without reading production source files.
* **UI Consistency**: No UI changes (system-level documentation & contract test only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Create `docs/system/sysreq_data_exchange.md` (REQ-PRO-055.1)
* Author `docs/system/sysreq_data_exchange.md` defining all 6 `SYSREQ-DEX-*` requirements with required sections and code grounding.

### Step 2: Implement `SystemDataExchangeRequirementsContractTest.kt` (REQ-PRO-055.2)
* Author `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemDataExchangeRequirementsContractTest.kt`.
* Implement `testSysreqDataExchange_fileExistsAndContainsAllRequirements()`, `testSysreqDataExchange_allRequirementsHaveMandatorySections()`, and `testSysreqDataExchange_codeGroundingParity()`.
* Run: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemDataExchangeRequirementsContractTest"`

### Step 3: Living Doc Verification & Parity Check (TST-PRO-055.3)
* Run `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-46.1` to assert 100% living documentation parity.

---

## 6. Verification & Rollback Plan
* **Local Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemDataExchangeRequirementsContractTest"`
* **Rollback Plan**: Branch isolation on `feature/ATT-6161` ensures clean revert with zero impact on `sprint/2026-46.1`.
