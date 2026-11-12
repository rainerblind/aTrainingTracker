# Stage 1 Analysis: ATT-6130 - Backfill test evidence for Process Stability requirements

**Ticket**: [ATT-6130](https://rainerblind.atlassian.net/browse/ATT-6130)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-2-Agent---ATT-6130-self-84d603cd`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the independent ASPICE 4.0 pre-assessment (baseline develop @ sprint/2026-45.11, Remediation ID: A-08.4, ASPICE SWE.6 BP4, SWE.1 BP5), the `process_stability` living documentation domain (`docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`) was audited for evidence integrity, bidirectional traceability, dangling reference elimination, and mandatory attribute compliance:
* **Dangling TST References in `req_process_stability.md`**: `REQ-NFR-001` cites non-existent `TST-STR-010`, and `REQ-NFR-002` cites non-existent `TST-FUSION-004`. Both requirements already cite authentic tests (`TST-NFR-001` and `TST-NFR-002`), making the dangling references historical residue.
* **Dangling REQ References in `tst_process_stability.md`**: `TST-INT-004`, `TST-INT-005`, `TST-INT-006`, `TST-STR-001`, `TST-STR-003`, `TST-STB-013`, and `TST-STB-018` cite non-existent REQ IDs (`REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-STB-013`).
* **Missing Living Requirement `REQ-STB-013`**: `REQ-STB-013` (*Resilient Single-Pass Workout History Streaming & CursorWindow Crash Protection*) was inadvertently dropped during living documentation sharding (`06990c88`, ATT-4594), leaving `TST-STB-013` and `TST-STB-018` referencing a dangling ID and leaving SQLite streaming invariants undocumented in `req_process_stability.md`.
* **Mandatory Attribute Gaps (REQ-PRO-077, SWE.1 BP2)**: 134 requirements in `req_process_stability.md` and 167 test specifications in `tst_process_stability.md` lack mandatory `Priority`, `Type`, and `Verification Method` attributes.
* **Legacy Ungrounded Allowlist Cleanup (REQ-PRO-073)**: 58 `process_stability` requirements are grandfathered in `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py`. Forensic audit confirms that 100% of these requirements cite authentic, existing `TST-*` specifications with automated test implementations in `tools/` and `app/src/test/`.
* **Process Stability Registration**: Registration of `REQ-PRO-086` and `TST-PRO-086` is required under `req_process_stability.md` and `tst_process_stability.md`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause**:
  1. Sharding of living documentation (`ATT-4594`, commit `06990c88`) split the monolithic table into shards. `REQ-STB-013` was omitted from the resulting `req_process_stability.md` shard.
  2. Legacy tests in `tst_process_stability.md` (`TST-INT-004`, `TST-INT-005`, `TST-INT-006`, `TST-STR-001`, `TST-STR-003`) retained early prototype references (`REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`) that never existed as formal headings in modern shards.
  3. `TST-MAN-001` declared `Requirement(s): All other REQ` instead of explicitly enumerating citing requirements, creating Category 4 bidirectional traceability gaps.
  4. Structured attributes (`Priority`, `Type`, `Verification Method`) introduced in `REQ-PRO-077` (ATT-6126) were not yet backfilled into `req_process_stability.md` or `tst_process_stability.md`.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Audited `tools/verify_requirement_governance.py`:
    * Confirmed `LEGACY_DANGLING_TST_REFS` contains `TST-STR-010` and `TST-FUSION-004`. Removing these dangling references from `req_process_stability.md` allows full removal from the allowlist.
    * Confirmed `LEGACY_DANGLING_REQ_REFS` contains `REQ-STB-013`. Restoring `REQ-STB-013` resolves this dangling reference across `TST-STB-013` and `TST-STB-018`.
  - Audited `WorkoutRepository.kt` & `WorkoutRepositoryStreamTest.kt`:
    * Confirmed `REQ-STB-013` implementation and automated tests (`loadAllWorkouts`, `RawCursorSnapshot`, single-pass forward iteration) are 100% active and passing in `WorkoutRepositoryStreamTest.kt` and `WorkoutRepositoryStressTest.kt`.
  - Audited automated test coverage for `process_stability`:
    * `tools/test_verify_requirement_governance.py` (52 unit tests, 100% pass).
    * `tools/test_jira_util.py` (38 unit tests, 100% pass).
    * `tools/test_flock_serialization.py`, `tools/test_sprint_metrics.py`, `ArchitectureConformanceContractTest.kt`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Restore `REQ-STB-013` with full Chesterton's Fence archaeology and acceptance criteria into `docs/requirements/req_process_stability.md`.
  2. Eliminate dangling TST references (`TST-STR-010`, `TST-FUSION-004`) from `req_process_stability.md` and prune `LEGACY_DANGLING_TST_REFS` in `tools/verify_requirement_governance.py`.
  3. Fix dangling REQ references in `docs/tests/tst_process_stability.md` (`REQ-UI-008` -> `REQ-UI-109`, `REQ-UI-009` -> `REQ-UI-110`, `REQ-UI-010` -> `REQ-UI-111`) and prune `REQ-STB-013` from `LEGACY_DANGLING_REQ_REFS`.
  4. Fix Category 4 bidirectional parity gaps in `tst_process_stability.md` (`TST-MAN-001`, `TST-PRO-052` range expansion).
  5. Backfill mandatory attributes (`Priority`, `Type`, `Verification Method`) across all requirements in `req_process_stability.md` and test specifications in `tst_process_stability.md`.
  6. Prune all 58 verified `process_stability` requirements from `LEGACY_UNGROUNDED_VERIFIED_REQS`.
  7. Register net-new governance requirement `REQ-PRO-086` and test specification `TST-PRO-086`.
  8. Synchronize master index tables via `python3 tools/verify_requirement_governance.py --sync-indexes`.
* **Out-of-Scope Non-Goals**:
  - Modifying non-governance production code in `app/src/main/`.
  - Modifying unrelated domain shards (`req_ui_design_system.md`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-086` / `TST-PRO-086`, restoration of `REQ-STB-013`, and attribute/traceability backfill across `req_process_stability.md` and `tst_process_stability.md`.
* **Historical Origin & Commit Trace**:
  - `REQ-STB-013`: Sprint 2026-40.16 (`ATT-2309`, commit `3e710f3c`), amended in `ATT-4002`. Dropped during sharding in `06990c88`.
  - `REQ-NFR-001` & `REQ-NFR-002`: Sprint 2026-46.4 (`ATT-6139`). Dangling citations `TST-STR-010` and `TST-FUSION-004` were legacy placeholders.
* **Root Reason for Existing Formulation**: Sharding automation and manual markdown editing left incomplete metadata and dangling references that violate ASPICE CL2 SWE.1 BP5 and SWE.6 BP4.
* **Preservation of Core Invariants**: 100% automated test pass rate; master table mathematical parity; zero regression in governance verifier checks.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: Net-new `REQ-PRO-086`, restored `REQ-STB-013`, `REQ-NFR-001`, `REQ-NFR-002`, and all 142 existing REQs in `req_process_stability.md`.
* **Affected Architecture Elements**:
  - `docs/requirements/req_process_stability.md`
  - `docs/tests/tst_process_stability.md`
  - `tools/verify_requirement_governance.py`
  - `tools/test_verify_requirement_governance.py`
  - `docs/requirements.md` & `docs/tests.md`
* **Verification Impact & Test Surface**:
  - `python3 tools/verify_requirement_governance.py --coverage`
  - `python3 tools/verify_requirement_governance.py --enforce-unique --enforce-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
  - `python3 -m unittest tools/test_verify_requirement_governance.py`
* **Risk Assessment & Invariants**: Low risk. Confined strictly to living documentation metadata, governance verifier allowlists, and unit test assertions.

---

## 6. Architectural Strategy & High-Level Solution

1. **Restore `REQ-STB-013`**: Insert `REQ-STB-013` into `docs/requirements/req_process_stability.md` with complete attributes and invariants.
2. **Resolve Dangling References**:
   - Clean `REQ-NFR-001` and `REQ-NFR-002` in `req_process_stability.md`.
   - Update `TST-INT-004`, `TST-INT-005`, `TST-INT-006`, `TST-STR-001`, `TST-STR-003` in `tst_process_stability.md`.
   - Prune `TST-STR-010`, `TST-FUSION-004`, and `REQ-STB-013` from `tools/verify_requirement_governance.py`.
3. **Traceability Parity (Category 4)**:
   - Expand `TST-PRO-052` requirements range to explicit comma-separated list (`REQ-NFR-003` through `REQ-NFR-014`).
   - Enumerate all 23 citing REQs in `TST-MAN-001`.
4. **Attribute Enrichment**: Add `Priority`, `Type`, `Verification Method` to all REQs in `req_process_stability.md` and all TSTs in `tst_process_stability.md`.
5. **Prune Grandfathered Verified REQs**: Remove all 58 verified `process_stability` requirements from `LEGACY_UNGROUNDED_VERIFIED_REQS`.
6. **Register `REQ-PRO-086` & `TST-PRO-086`**: Document remediation A-08.4 governance requirements.
7. **Sync Master Tables**: Execute `verify_requirement_governance.py --sync-indexes`.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. 100% test pass rate in `python3 -m unittest tools/test_verify_requirement_governance.py`.
  2. 100% mathematical parity across `docs/requirements.md` and `docs/tests.md`.
  3. Zero ungrounded `[Verified]` requirements in `process_stability`.
  4. Zero dangling references originating from `process_stability` files.
* **Risk Rating**: **LOW** (documentation metadata, governance tooling allowlists, zero runtime code edits).
