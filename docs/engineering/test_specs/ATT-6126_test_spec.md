# Stage 2: Requirement & Test Specification - ATT-6126: Introduce evidence-based requirement status model

**Ticket**: [ATT-6126](https://rainerblind.atlassian.net/browse/ATT-6126)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-073` (*Evidence-Based Living Requirement Status Model and Governance Verification*)  
**Test Spec ID**: `TST-PRO-073`  
**Branch**: `feature/ATT-6126`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-073)

### 1.1 Problem Statement & Rationale
ASPICE SWE.1 (Software Requirements Analysis), SWE.6 BP3 (Verify Software Units), and GP 2.2 require work product claims to be grounded in controlled, versioned evidence. Prior to this improvement, requirement headings arbitrarily claimed `[Verified]` without citing existing test specifications or passing automated test code.

### 1.2 Functional & Architectural Requirements
1. **Status Lifecycle Taxonomy (`REQ-PRO-073.1`)**: Living requirements and test specifications SHALL use multi-line headings ending with an explicit evidence-based status:
   - `[Planned]` / `[Open]`: Requirement is formally specified, but no verified test evidence or passing automated test exists yet.
   - `[Implemented]`: Code construction exists, but automated test evidence is pending, incomplete, or failing.
   - `[Verified]`: Requires $\ge 1$ existing, authentic TST specification with passing automated test code or formal verification record.
2. **Master Index Table 4-Column Format (`REQ-PRO-073.2`)**: Master index tables in `docs/requirements.md` and `docs/tests.md` SHALL format domain summaries with 4 numeric count columns: `Total | Verified | Implemented | Planned / Open`.
3. **Automated Index Synchronization (`REQ-PRO-073.3`)**: `tools/verify_requirement_governance.py --sync-indexes` and `verify_master_table_parity()` SHALL parse, validate, and synchronize `[Implemented]` status alongside `[Verified]` and `[Planned]` / `[Open]` with 100% mathematical parity across all 9 domain shards.
4. **Evidence-Backed Verification Rule (`REQ-PRO-073.4`)**: `tools/verify_requirement_governance.py` SHALL verify that any requirement claiming `[Verified]` cites $\ge 1$ existing TST ID. In default CI checks (`verify_governance()`), any new or modified requirement in the git diff claiming `[Verified]` without an existing TST ID SHALL fail validation. Under full-repository gating (`--enforce-evidence`), any requirement claiming `[Verified]` without valid test evidence SHALL fail.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1**:
  * *Given* a requirement is marked `[Verified]` without citing an existing TST ID in `Tests:`,
  * *When* `verify_requirement_governance.py` executes validation,
  * *Then* it fails with a descriptive error.
* **Criterion 2**:
  * *Given* a requirement is marked `[Verified]` and its cited TST specification exists and has verified automated test code,
  * *When* `verify_requirement_governance.py` executes validation,
  * *Then* it passes.
* **Criterion 3**:
  * *Given* living documentation domain shards containing `[Verified]`, `[Implemented]`, and `[Planned]` headings,
  * *When* `python3 tools/verify_requirement_governance.py --sync-indexes` runs,
  * *Then* master index tables in `docs/requirements.md` and `docs/tests.md` are updated to 4 columns matching domain counts with 100% parity.

### 1.4 System Invariants
- 100% mathematical parity between master summary tables and domain shards.
- Zero external runtime or build dependencies (pure Python 3 standard library).
- Backward compatibility: existing valid headings (`[Verified]`, `[Planned]`) remain supported.

---

## 2. Test Specification (TST-PRO-073)

### Test Case 1: Headings Standardization Contract Test (`TST-PRO-073.1`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt` (`testLivingDocShards_allHeadingsStandardized`)
* **Preconditions**: Living doc shards in `docs/requirements/` and `docs/tests/`.
* **Action**: Asserts all `### REQ-*` and `### TST-*` headings end with `[Verified]`, `[Implemented]`, `[Planned]`, or `[Open]`.
* **Expected Result**: 100% pass across all 9 domain shards.

### Test Case 2: 4-Column Master Table Parity Contract Test (`TST-PRO-073.2`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt` (`testMasterTableParity_matchesDomainShards`)
* **Preconditions**: `docs/requirements.md` and `docs/tests.md`.
* **Action**: Parses 4 columns (`Total`, `Verified`, `Implemented`, `Planned`) and asserts exact equality with shard counts.
* **Expected Result**: 100% parity across all 9 domain shards.

### Test Case 3: Verifier 4-Column Sync & Parity Unit Tests (`TST-PRO-073.3`)
* **Scope**: Python Tooling Unit Test
* **Target File**: `tools/test_verify_requirement_governance.py`
* **Preconditions**: Mock shard files and master index tables.
* **Action**: Tests `verify_master_table_parity()` and `recalculate_master_index_tables()` with 4 columns.
* **Expected Result**: Accurate count calculation, proper table formatting, and deterministic return codes.

### Test Case 4: Evidence Status Verification Rule Unit Tests (`TST-PRO-073.4`)
* **Scope**: Python Tooling Unit Test
* **Target File**: `tools/test_verify_requirement_governance.py`
* **Preconditions**: Requirements claiming `[Verified]` with and without valid TST citations.
* **Action**: Calls `verify_requirement_evidence_status()`.
* **Expected Result**: Fails when `[Verified]` REQ lacks TST; passes when valid TST is cited.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-073.1` | Contract | `AspiceSweAuditContractTest.testLivingDocShards_allHeadingsStandardized` | `REQ-PRO-073.1` | Specified |
| `TST-PRO-073.2` | Contract | `AspiceSweAuditContractTest.testMasterTableParity_matchesDomainShards` | `REQ-PRO-073.2` | Specified |
| `TST-PRO-073.3` | Tooling Unit | `test_verify_requirement_governance.py:test_master_table_parity_4_columns` | `REQ-PRO-073.3` | Specified |
| `TST-PRO-073.4` | Tooling Unit | `test_verify_requirement_governance.py:test_evidence_status_rule` | `REQ-PRO-073.4` | Specified |
