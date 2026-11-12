# Stage 2: Requirement & Test Specification - ATT-6135: Backfill test evidence for Store Presence (project_governance shard) requirements

**Ticket**: [ATT-6135](https://rainerblind.atlassian.net/browse/ATT-6135)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-084` (*Store Presence Test Evidence Backfill & Bidirectional Traceability Governance*)  
**Test Spec ID**: `TST-PRO-084`  
**Branch**: `subagent-Lane-2-Agent---ATT-6135-self-487a5904`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-084)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.9 (ATT-6135), all requirements in `docs/requirements/req_store_presence.md` (historically referenced as the `project_governance` shard prior to ATT-6140) must link to valid, existing test specifications in `docs/tests/tst_store_presence.md` backed by authentic automated test code in `app/src/test/` or be downgraded to `[Implemented]`. Furthermore, all requirements must satisfy mandatory attribute governance (`Priority`, `Type`, `Verification Method` per `REQ-PRO-077`, SWE.1 BP2) with zero dangling references and 100% master index mathematical parity.

### 1.2 Attributes
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. *Test Code Tag Grounding & Category 2 Completeness (`REQ-PRO-084.1`)*:
   • In `PlayStoreListingContractTest.kt`, methods `fullDescriptionsContainMandatoryV5Features()` and `storeListingsDoNotContainUnimplementedBacklogFeatures()` SHALL declare test code tag `TST-STP-002`.
   • In `PlayStoreListingContractTest.kt`, method `storeScreenshotsAreUniqueAndValid()` SHALL declare test code tag `TST-STP-004`.
   • The class documentation header of `PlayStoreListingContractTest.kt` SHALL explicitly cite `REQ-STP-004`, `TST-STP-002`, and `TST-STP-004`, resolving Category 2 gaps for the `store_presence` domain to 0 (10/10, 100%).
2. *Mandatory Attribute Compliance (`REQ-PRO-084.2`)*:
   • All 10 requirements in `docs/requirements/req_store_presence.md` (`REQ-STP-001` through `REQ-STP-010`) SHALL declare valid `Priority` (`Critical`, `High`, `Medium`, `Low`), `Type` (`Functional`, `Non-Functional`), and `Verification Method` (`Unit`, `Integration`, `On-Device Manual`, `Review`, `Formal Analysis`) attributes per `REQ-PRO-077`.
3. *Test Specification Alignment (`REQ-PRO-084.3`)*:
   • Test specifications `TST-STP-001` through `TST-STP-010` in `docs/tests/tst_store_presence.md` SHALL declare `Priority`, `Type`, and `Verification Method` attributes.
   • Bidirectional citation between `docs/requirements/req_store_presence.md` and `docs/tests/tst_store_presence.md` SHALL be 100% reciprocal with 0 Category 4 gaps.
4. *Governance Registration & Index Synchronization (`REQ-PRO-084.4`)*:
   • Governance requirement `REQ-PRO-084` SHALL be registered in `docs/requirements/req_process_stability.md` and `TST-PRO-084` in `docs/tests/tst_process_stability.md`.
   • Master index tables in `docs/requirements.md` and `docs/tests.md` SHALL be recalculated via `tools/verify_requirement_governance.py --sync-indexes`, achieving 100% mathematical parity.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**:
  * *Given* `docs/requirements/req_store_presence.md`,
  * *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  * *Then* 100% of requirements cite valid TSTs (10/10), 100% of TSTs have test code tags (10/10), and Category 4 bidirectional gaps for STP are 0.
* **Criterion 2**:
  * *Given* `docs/requirements/req_store_presence.md` and `docs/tests/tst_store_presence.md`,
  * *When* evaluated via `python3 tools/verify_requirement_governance.py --check-attributes --check-links --check-evidence`,
  * *Then* zero missing attributes, zero dangling references, and zero ungrounded Verified requirements are detected.
* **Criterion 3**:
  * *Given* `python3 tools/verify_requirement_governance.py --sync-indexes`,
  * *When* executed,
  * *Then* master index tables achieve 100% mathematical parity across all 9 domain shards.

### 1.5 System Invariants
1. Pure Python 3 standard library governance execution; zero Android production runtime code changes.
2. 100% pass rate in `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`.
3. Master table mathematical parity across all 9 domain shards remains 100% green.

---

## 2. Test Specification (TST-PRO-084)

### Test Case 1: Coverage & Bidirectional Traceability Audit (`TST-PRO-084.1`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: `req_store_presence.md`, `tst_store_presence.md`, and `PlayStoreListingContractTest.kt` updated.
* **Action**: `python3 tools/verify_requirement_governance.py --coverage`
* **Expected Result**: 0 Category 2 gaps for STP (10/10 covered), 0 Category 4 gaps for STP, 100% REQ-TST coverage.

### Test Case 2: Attribute & Link Integrity Audit (`TST-PRO-084.2`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: Attributes added to `REQ-STP-001`..`010` and `TST-STP-001`..`010`.
* **Action**: `python3 tools/verify_requirement_governance.py --check-attributes --check-links --check-evidence --check-unique-ids`
* **Expected Result**: Zero missing attributes, zero dangling references in `store_presence`, zero ungrounded `[Verified]` requirements.

### Test Case 3: Master Index Table Synchronization (`TST-PRO-084.3`)
* **Scope**: Master Index Parity
* **Target File**: `docs/requirements.md`, `docs/tests.md`
* **Preconditions**: Shard headers updated and process stability requirements registered.
* **Action**: `python3 tools/verify_requirement_governance.py --sync-indexes`
* **Expected Result**: 100% mathematical parity across all 9 domain shards with 4-column summary format (`Total | Verified | Implemented | Planned / Open`).

### Test Case 4: Build Contract Test Suite Execution (`TST-PRO-084.4`)
* **Scope**: Unit Tests
* **Target Files**: `PlayStoreListingContractTest.kt`, `ReadmePlayStoreBadgeContractTest.kt`, `PromoVideoAssetsContractTest.kt`, `ReleaseCiAndLintConfigContractTest.kt`
* **Action**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% PASS rate across all build and release contract tests.
