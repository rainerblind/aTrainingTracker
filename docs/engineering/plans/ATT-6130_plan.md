# Stage 3: Implementation Plan - ATT-6130: Backfill test evidence for Process Stability requirements

**Ticket**: [ATT-6130](https://rainerblind.atlassian.net/browse/ATT-6130)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-086`  
**Test Mapping**: `TST-PRO-086`  
**Branch**: `subagent-Lane-2-Agent---ATT-6130-self-84d603cd`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

Remediation ID: A-08.4 under Epic ATT-6119 (ASPICE SWE.6 BP4, SWE.1 BP5). Living requirements in `docs/requirements/req_process_stability.md` and test specifications in `docs/tests/tst_process_stability.md` must achieve 100% evidence grounding, eliminate dangling references, populate mandatory attributes (`Priority`, `Type`, `Verification Method` per `REQ-PRO-077`, SWE.1 BP2), restore missing `REQ-STB-013`, prune legacy allowlists in `tools/verify_requirement_governance.py`, and register `REQ-PRO-086` / `TST-PRO-086`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-086` (*Process Stability Test Evidence Backfill & Bidirectional Traceability Governance*)
* **Test Mapping**: `TST-PRO-086` (*Process Stability Test Evidence Backfill & Traceability Verification Suite*)
* **Restored Requirement**: `REQ-STB-013` (*Resilient Single-Pass Workout History Streaming & CursorWindow Crash Protection*)
* **Domain REQs**: 144 total in `req_process_stability.md` (all evidence-grounded)
* **Domain TSTs**: 173 total in `tst_process_stability.md`
* **Target Tests**: `tools/test_verify_requirement_governance.py`, `tools/test_jira_util.py`, `WorkoutRepositoryStreamTest.kt`

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All unit tests in `tools/test_verify_requirement_governance.py` and `tools/test_jira_util.py` maintain 100% pass rate.
2. **Master Index Mathematical Parity**: 100% parity across all 9 domain shards with 4-column summary format (`Total | Verified | Implemented | Planned / Open`).
3. **Traceability Standards**: 100% of `process_stability` REQs cite valid TSTs, zero dangling references exist originating from this domain, and Category 4 bidirectional parity is >= 95%.
4. **Zero Production Runtime Risk**: Changes are strictly confined to living documentation, governance verifier allowlists, and verifier test assertions.

---

## 4. Proposed Architectural Changes

### Component 1: Living Requirement Shard (`docs/requirements/req_process_stability.md`)
* Restore `REQ-STB-013` with full attributes, rationale, Chesterton's Fence archaeology, and acceptance criteria.
* Remove dangling test citations `TST-STR-010` from `REQ-NFR-001` and `TST-FUSION-004` from `REQ-NFR-002`.
* Populate `Priority`, `Type`, and `Verification Method` across all requirements per `REQ-PRO-077`.
* Register `REQ-PRO-086`.

### Component 2: Living Test Specification Shard (`docs/tests/tst_process_stability.md`)
* Fix dangling REQ citations in `TST-INT-004` (`REQ-UI-110`), `TST-INT-005` (`REQ-UI-111`), `TST-INT-006` (`REQ-UI-111`), `TST-STR-001` (`REQ-UI-109`), `TST-STR-003` (`REQ-UI-110`).
* Enumerate all 23 citing requirements in `TST-MAN-001`.
* Expand `TST-PRO-052` requirement range to explicit list `REQ-NFR-003`..`REQ-NFR-014`.
* Populate `Priority`, `Type`, and `Verification Method` across all 173 test specifications.
* Register `TST-PRO-086`.

### Component 3: Governance Verifier & Allowlist Pruning (`tools/verify_requirement_governance.py`)
* Prune `TST-STR-010` and `TST-FUSION-004` from `LEGACY_DANGLING_TST_REFS`.
* Prune `REQ-STB-013` from `LEGACY_DANGLING_REQ_REFS`.
* Prune all 58 verified `process_stability` requirements from `LEGACY_UNGROUNDED_VERIFIED_REQS`.

### Component 4: Verifier Unit Tests (`tools/test_verify_requirement_governance.py`)
* Update allowlist count assertions to reflect pruned items.

### Component 5: Master Index Tables (`docs/requirements.md`, `docs/tests.md`)
* Recalculate and synchronize master tables via `verify_requirement_governance.py --sync-indexes`.

### UI Consistency
* No UI changes.

---

## 5. Step-by-Step Implementation Plan

### Step 1: Restore `REQ-STB-013` & Clean Dangling Citations in `req_process_stability.md`
- Insert `REQ-STB-013` before `REQ-STB-014`.
- Prune `TST-STR-010` from `REQ-NFR-001` and `TST-FUSION-004` from `REQ-NFR-002`.

### Step 2: Fix Dangling Citations & Category 4 Gaps in `tst_process_stability.md`
- Update `TST-INT-004`, `TST-INT-005`, `TST-INT-006`, `TST-STR-001`, `TST-STR-003`.
- Update `TST-MAN-001` with explicit 23 citing REQs.
- Expand `TST-PRO-052` cited requirements.

### Step 3: Backfill Mandatory Attributes on All REQs in `req_process_stability.md`
- Insert `Priority`, `Type`, and `Verification Method` on all requirements lacking them.

### Step 4: Backfill Mandatory Attributes on All TSTs in `tst_process_stability.md`
- Insert `Priority`, `Type`, and `Verification Method` on all test specifications lacking them.

### Step 5: Register `REQ-PRO-086` and `TST-PRO-086`
- Add `REQ-PRO-086` to `docs/requirements/req_process_stability.md`.
- Add `TST-PRO-086` to `docs/tests/tst_process_stability.md`.

### Step 6: Prune Allowlists in `verify_requirement_governance.py` & Update Tests
- Remove resolved IDs from `LEGACY_DANGLING_TST_REFS`, `LEGACY_DANGLING_REQ_REFS`, and `LEGACY_UNGROUNDED_VERIFIED_REQS`.
- Update `tools/test_verify_requirement_governance.py`.

### Step 7: Synchronize Master Index Tables
- Run `python3 tools/verify_requirement_governance.py --sync-indexes`.
- Validate 100% parity across `docs/requirements.md` and `docs/tests.md`.

### Step 8: Verification & Governance Audit
- Run `python3 tools/verify_requirement_governance.py --coverage`.
- Run `python3 tools/verify_requirement_governance.py --enforce-unique --enforce-links`.
- Run `python3 -m unittest tools/test_verify_requirement_governance.py`.
