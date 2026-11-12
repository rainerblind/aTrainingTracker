# Stage 2: Requirement & Test Specification - ATT-6127: Backfill test evidence for Tracking Cockpit requirements

**Ticket**: [ATT-6127](https://rainerblind.atlassian.net/browse/ATT-6127)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-078` (*Tracking Cockpit Test Evidence & Bidirectional Traceability Governance*), `REQ-TRK-035` (*Deterministic BANAL State Snapshotting*)  
**Test Spec ID**: `TST-PRO-078`, `TST-TRK-027`  
**Branch**: `feature/ATT-6127`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-PRO-078`)

### 1.1 Problem Statement & Rationale
Living documentation shard `docs/requirements/req_tracking_cockpit.md` previously contained ungrounded `[Verified]` claims, dangling references (`TST-FUSION-001`..`003`), missing requirement `REQ-TRK-035` (causing dangling reference `TST-TRK-027`), and broken bidirectional back-links. To meet ASPICE SWE.1 BP5 and SWE.6 BP4, all requirements in the Tracking Cockpit domain must cite authentic test specifications, maintain bidirectional linkage, and ground `[Verified]` claims in automated test code.

### 1.2 Functional & Architectural Requirements
1. **Tracking Cockpit Test Citation Completeness (`REQ-PRO-078.1`)**:
   - Every requirement in `docs/requirements/req_tracking_cockpit.md` SHALL cite at least one valid, existing `TST-*` ID in its `Tests:` field matching an existing heading in `docs/tests/tst_tracking_cockpit.md` (or cross-domain test specification), achieving $\ge 95\%$ domain test coverage.
2. **Bidirectional Linkage Parity (`REQ-PRO-078.2`)**:
   - Every test specification in `docs/tests/tst_tracking_cockpit.md` SHALL be cited back by all of its referenced requirements in `docs/requirements/req_tracking_cockpit.md`, achieving $\ge 95\%$ bidirectional parity (Category 4).
3. **Zero Dangling References (`REQ-PRO-078.3`)**:
   - All legacy dangling references in the Tracking Cockpit domain (`TST-FUSION-001`, `TST-FUSION-002`, `TST-FUSION-003`, `REQ-TRK-035`) SHALL be eliminated by restoring `REQ-TRK-035` and registering planned fusion specifications `TST-FUSION-001`..`003`.
4. **Evidence-Grounded Status Transition (`REQ-PRO-078.4`)**:
   - Requirements lacking automated unit test code in `app/src/test/` SHALL be downgraded from `[Verified]` to `[Implemented]` per `REQ-PRO-073`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Traceability Coverage)**:
  * *Given* living documentation shards `req_tracking_cockpit.md` and `tst_tracking_cockpit.md`,
  * *When* `calculate_traceability_coverage()` executes for this domain,
  * *Then* Category 1 coverage is $\ge 95\%$, Category 4 coverage is $\ge 95\%$, and zero dangling references exist in this domain.
* **Criterion 2 (Status Integrity)**:
  * *Given* a requirement claiming `[Verified]` in `req_tracking_cockpit.md`,
  * *When* audited by `verify_requirement_evidence_status()`,
  * *Then* it cites $\ge 1$ existing test specification with passing automated test code or formal verification record.
* **Criterion 3 (Master Index Parity)**:
  * *Given* living documentation shards and master tables in `docs/requirements.md` and `docs/tests.md`,
  * *When* `verify_master_table_parity()` executes,
  * *Then* 100% mathematical parity across all columns (`Total | Verified | Implemented | Planned / Open`) is verified.

---

## 2. Test Specification (`TST-PRO-078`)

### Test Case 1: `testTrackingCockpitTraceabilityCoverage` (`TST-PRO-078.1`)
* **Scope**: Automated Python Governance Verification
* **Target File**: `tools/test_verify_requirement_governance.py` & `tools/verify_requirement_governance.py`
* **Preconditions**: Tracking Cockpit shards updated with restored `REQ-TRK-035`, planned fusion TSTs, and updated links.
* **Action**: Execute `python3 tools/verify_requirement_governance.py --coverage`.
* **Expected Result**: 0 dangling references for tracking cockpit, Category 1 coverage $\ge 95\%$, Category 4 coverage $\ge 95\%$.

### Test Case 2: `testMasterTableParity_matchesDomainShards` (`TST-PRO-078.2`)
* **Scope**: JVM Architecture Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`
* **Action**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`.
* **Expected Result**: 100% parity verified between master index tables and domain shards across 4 columns.

### Test Case 3: Tier 1 Unit Test & Governance Regression (`TST-PRO-078.3`)
* **Command**: `python3 tools/verify_requirement_governance.py && python3 tools/test_verify_requirement_governance.py`
* **Expected Result**: Exit code 0, all assertions pass.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-078.1` | Governance | `calculate_traceability_coverage` | `REQ-PRO-078.1`, `REQ-PRO-078.2`, `REQ-PRO-078.3` | Specified |
| `TST-PRO-078.2` | Contract Test | `AspiceSweAuditContractTest.kt` | `REQ-PRO-078.4`, `REQ-PRO-073` | Specified |
| `TST-PRO-078.3` | Tool Unit Tests | `test_verify_requirement_governance.py` | `REQ-PRO-078.1`..`4` | Specified |
| `TST-TRK-027` | Tracker Service | `TrackerServiceFinalizationTest.kt` | `REQ-TRK-035` | Specified |
