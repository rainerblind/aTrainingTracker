# Stage 2: Requirement & Test Specification - ATT-6130: Backfill test evidence for Process Stability requirements

**Ticket**: [ATT-6130](https://rainerblind.atlassian.net/browse/ATT-6130)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-086` (*Process Stability Test Evidence Backfill & Bidirectional Traceability Governance*)  
**Test Spec ID**: `TST-PRO-086`  
**Branch**: `subagent-Lane-2-Agent---ATT-6130-self-84d603cd`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-086)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.4 (ATT-6130), requirements in `docs/requirements/req_process_stability.md` must link to valid, existing test specifications in `docs/tests/tst_process_stability.md` backed by authentic automated test code in `tools/` and `app/src/test/` or be downgraded to `[Implemented]`. All dangling references in this domain must be eliminated, mandatory attributes (`Priority`, `Type`, `Verification Method` per `REQ-PRO-077`, SWE.1 BP2) populated across all REQs and TSTs, and master index tables synchronized with 100% mathematical parity.

### 1.2 Attributes
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. *Restoration of REQ-STB-013 & SQLite Streaming Governance (`REQ-PRO-086.1`)*:
   • `REQ-STB-013` (*Resilient Single-Pass Workout History Streaming & CursorWindow Crash Protection*) SHALL be restored in `docs/requirements/req_process_stability.md` with complete attributes, rationale, Chesterton's Fence archaeology, and invariants.
   • `REQ-STB-013` SHALL be linked bidirectionally with `TST-STB-013` and `TST-STB-018`, backed by `WorkoutRepositoryStreamTest.kt` and `WorkoutRepositoryStressTest.kt`.
2. *Dangling Reference Elimination (`REQ-PRO-086.2`)*:
   • Non-existent test citations `TST-STR-010` and `TST-FUSION-004` SHALL be removed from `REQ-NFR-001` and `REQ-NFR-002` in `req_process_stability.md`.
   • Dangling REQ citations in `docs/tests/tst_process_stability.md` (`TST-INT-004`, `TST-INT-005`, `TST-INT-006`, `TST-STR-001`, `TST-STR-003`) SHALL be resolved to authentic headings (`REQ-UI-110`, `REQ-UI-111`, `REQ-UI-109`).
   • `TST-STR-010`, `TST-FUSION-004`, and `REQ-STB-013` SHALL be pruned from `LEGACY_DANGLING_TST_REFS` and `LEGACY_DANGLING_REQ_REFS` in `tools/verify_requirement_governance.py`.
3. *Mandatory Attribute Backfill (`REQ-PRO-086.3`)*:
   • All 144 requirements in `docs/requirements/req_process_stability.md` SHALL declare valid `Priority` (`Critical`, `High`, `Medium`, `Low`), `Type` (`Functional`, `Non-Functional`), and `Verification Method` (`Unit`, `Integration`, `On-Device Manual`, `Review`, `Formal Analysis`) attributes per `REQ-PRO-077`.
   • All 173 test specifications in `docs/tests/tst_process_stability.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes.
4. *Bidirectional Parity & Ungrounded Allowlist Pruning (`REQ-PRO-086.4`)*:
   • `TST-MAN-001` SHALL explicitly enumerate all 23 citing requirements across living shards.
   • `TST-PRO-052` SHALL explicitly list `REQ-NFR-003` through `REQ-NFR-014`, eliminating range expansion gaps.
   • All 58 verified `process_stability` requirements SHALL be pruned from `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py`.
5. *Governance Registration & Index Synchronization (`REQ-PRO-086.5`)*:
   • `REQ-PRO-086` SHALL be registered in `docs/requirements/req_process_stability.md` and `TST-PRO-086` in `docs/tests/tst_process_stability.md`.
   • Master index tables in `docs/requirements.md` and `docs/tests.md` SHALL be recalculated via `tools/verify_requirement_governance.py --sync-indexes`, achieving 100% mathematical parity.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**:
  * *Given* `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`,
  * *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  * *Then* >= 95% of REQs cite valid TSTs (target: 100%), Category 4 bidirectional parity is >= 95%, and zero ungrounded Verified statuses exist.
* **Criterion 2**:
  * *Given* `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`,
  * *When* evaluated via `python3 tools/verify_requirement_governance.py --check-attributes --check-links --check-evidence`,
  * *Then* zero missing attributes and zero dangling references exist originating from this domain.
* **Criterion 3**:
  * *Given* `python3 tools/verify_requirement_governance.py --sync-indexes`,
  * *When* executed,
  * *Then* master index tables achieve 100% mathematical parity across all 9 domain shards.

### 1.5 System Invariants
1. Pure Python 3 standard library governance execution; zero Android production runtime code changes.
2. 100% pass rate in `python3 -m unittest tools/test_verify_requirement_governance.py`.
3. Master table mathematical parity across all 9 domain shards remains 100% green.

---

## 2. Test Specification (TST-PRO-086)

### Test Case 1: Coverage & Bidirectional Traceability Audit (`TST-PRO-086.1`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: `req_process_stability.md` and `tst_process_stability.md` updated.
* **Action**: `python3 tools/verify_requirement_governance.py --coverage`
* **Expected Result**: >= 95% REQ-TST coverage, 0 dangling references in `process_stability`, Category 4 parity >= 95%.

### Test Case 2: Attribute & Link Integrity Audit (`TST-PRO-086.2`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: Attributes added to all REQs and TSTs in process stability shards.
* **Action**: `python3 tools/verify_requirement_governance.py --check-attributes --check-links --check-evidence --check-unique-ids`
* **Expected Result**: Zero missing attributes, zero dangling references in `process_stability`, zero ungrounded `[Verified]` requirements.

### Test Case 3: Master Index Table Synchronization (`TST-PRO-086.3`)
* **Scope**: Master Index Parity
* **Target File**: `docs/requirements.md`, `docs/tests.md`
* **Preconditions**: Shard headers updated and process stability requirements registered.
* **Action**: `python3 tools/verify_requirement_governance.py --sync-indexes`
* **Expected Result**: 100% mathematical parity across all 9 domain shards with 4-column summary format (`Total | Verified | Implemented | Planned / Open`).

### Test Case 4: Governance Tooling Unit Test Regression Suite (`TST-PRO-086.4`)
* **Scope**: Unit Tests
* **Target File**: `tools/test_verify_requirement_governance.py`
* **Action**: `python3 -m unittest tools/test_verify_requirement_governance.py`
* **Expected Result**: 100% PASS rate across all 52 unit tests.
