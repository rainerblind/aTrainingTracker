# Stage 3: Implementation Plan - ATT-6126: Introduce evidence-based requirement status model

**Ticket**: [ATT-6126](https://rainerblind.atlassian.net/browse/ATT-6126)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-073`  
**Test Mapping**: `TST-PRO-073`  
**Branch**: `feature/ATT-6126`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

Living requirements previously suffered from status inflation, with 98% claiming `[Verified]` despite lacking test citations or passing test code. This improvement introduces an evidence-based requirement status model (`[Planned]`, `[Implemented]`, `[Verified]`), expands living doc master index tables to 4 columns, updates the governance verifier with automatic synchronization and verification rules, and aligns the SWE audit contract test.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-073` (*Evidence-Based Living Requirement Status Model and Governance Verification*)
* **Test Mapping**: `TST-PRO-073` (*Evidence-Based Requirement Status Model and Master Index Parity Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **100% Master Table Parity**: Master tables in `docs/requirements.md` and `docs/tests.md` must mathematically match parsed domain shard headings across all 9 shards.
2. **Pure Python Standard Library**: `tools/verify_requirement_governance.py` and `tools/test_verify_requirement_governance.py` must use standard library only (no pip dependencies).
3. **Subtask & Gate Flow**: Subtasks transition to `Erledigt` upon passing gate verdict; parent ticket advances through lifecycle.
4. **Code Lock**: Production code in `app/src/...` is locked until Gate 3 is `Erledigt` via `python3 tools/jira_util.py check-gate <SUBTASK_KEY>`.

---

## 4. Proposed Architectural Changes

### Component 1: Process Documentation & Standards
- `docs/project_governance.md`: Defines evidence-based requirement lifecycle and verification rules.
- `.agents/rules/aspice_governance.md`: Embeds requirement status governance references.

### Component 2: Governance Verifier (`tools/verify_requirement_governance.py`)
- Extends heading parser and shard counting tuple to `(tot, ver, impl, plan)`.
- Updates `verify_master_table_parity()` and `recalculate_master_index_tables()` for 4-column format (`Total | Verified | Implemented | Planned / Open`).
- Implements `verify_requirement_evidence_status()` enforcing that `[Verified]` REQs cite $\ge 1$ existing TST ID.
- Adds CLI flags `--check-evidence` and `--enforce-evidence`, and integrates diff checks into `verify_governance()`.

### Component 3: Build & Contract Test Suite
- `AspiceSweAuditContractTest.kt`: Updates heading standardization assertion to accept `[Implemented]` and `[Open]`; updates `testMasterTableParity_matchesDomainShards()` to parse and validate 4 numeric columns.
- `tools/test_verify_requirement_governance.py`: Adds unit tests validating 4-column parity and evidence status enforcement.

### UI Consistency
No UI changes (pure governance tooling, build audit, and documentation).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Process Documentation
* **Files**: `docs/project_governance.md`, `.agents/rules/aspice_governance.md`
* **Changes**: Formalize `[Planned]`, `[Implemented]`, and `[Verified]` criteria and evidence rules.

### Step 2: Living Requirements & Test Shards
* **Files**: `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`, `docs/requirements.md`, `docs/tests.md`
* **Changes**: Add `REQ-PRO-073` and `TST-PRO-073`; update master index headers and tables to 4 columns.

### Step 3: Governance Verifier Update
* **Files**: `tools/verify_requirement_governance.py`
* **Changes**: Support `[Implemented]` status, 4-column table verification/sync, and `verify_requirement_evidence_status()`.

### Step 4: Contract Test & Tooling Unit Test Update
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `tools/test_verify_requirement_governance.py`
* **Changes**: Update regex and assertions for 4 columns; add unit tests for evidence rules and 4-column parity.

### Step 5: Master Index Synchronization & Verification
* **Command**: `python3 tools/verify_requirement_governance.py --sync-indexes && python3 tools/verify_requirement_governance.py`
* **Verification**: Verify 100% parity across all 9 domain shards.

### Step 6: Targeted Build and Unit Test Execution
* **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`
* **Verification**: Assert clean green pass.

---

## 6. Verification & Rollback Plan

* **Verification**: `python3 tools/test_verify_requirement_governance.py`, `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`, and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Clean revert of `feature/ATT-6126` branch without repository residue.
