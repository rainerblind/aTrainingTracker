# Stage 3: Implementation Plan - ATT-6121: Detect Dangling REQ and TST References in Governance Verifier

**Ticket**: [ATT-6121](https://rainerblind.atlassian.net/browse/ATT-6121)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-070` (`REQ-PRO-070.1`, `REQ-PRO-070.2`, `REQ-PRO-070.3`, `REQ-PRO-070.4`)  
**Test Mapping**: `TST-PRO-070` (`TST-PRO-070.1`, `TST-PRO-070.2`, `TST-PRO-070.3`, `TST-PRO-070.4`, `TST-PRO-070.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
ASPICE 4.0 assessment identified 8 unique TST IDs cited by REQs (11 occurrences) and 11 unique REQ IDs cited by TSTs (16 occurrences) pointing to non-existent headings across domain shards (`docs/requirements/*.md` and `docs/tests/*.md`). `verify_requirement_governance.py` validates heading counts and ID uniqueness, but does not cross-check field citations against known headings. This plan defines the implementation of link integrity verification, precise file/line diagnostics, baseline allowlisting to protect CI until A-08.x backfill tickets merge, and a strict `--enforce-links` / `--fail-on-dangling` CLI flag.

---

## 2. Traceability & Requirements Mapping
* **Requirements**:
  * `REQ-PRO-070.1`: Dangling TST citation detection in requirement `Tests` fields.
  * `REQ-PRO-070.2`: Dangling REQ citation detection in test `Requirements` fields.
  * `REQ-PRO-070.3`: Controlled baseline allowlist (`LEGACY_DANGLING_TST_REFS`, `LEGACY_DANGLING_REQ_REFS`) for CI stability.
  * `REQ-PRO-070.4`: Strict reference enforcement flag (`--enforce-links` / `--fail-on-dangling`).
* **Test Mapping**:
  * `TST-PRO-070.1`: Unit test verifying dangling TST detection with file, line, and missing ID.
  * `TST-PRO-070.2`: Unit test verifying dangling REQ detection with file, line, and missing ID.
  * `TST-PRO-070.3`: Unit test verifying legacy allowlist tolerance and novel reference rejection.
  * `TST-PRO-070.4`: Unit test verifying strict `--enforce-links` failure mode.
  * `TST-PRO-070.5`: Live repository living documentation clean verification.

---

## 3. System Invariants & Preserved Behavior
1. **Pure Python 3 Standard Library**: Zero third-party dependencies (`re`, `os`, `sys`, `argparse`).
2. **Deterministic Exit Codes**: 0 = PASS, 1 = FAIL.
3. **CI Non-Regression**: Pre-existing legacy dangling references emit warnings in default mode so concurrent sprint lanes remain unblocked until A-08.x backfill.
4. **Tooling Contract Parity**: Existing verifier functions (`verify_unique_ids`, `verify_master_table_parity`, `verify_composable_islands`, `verify_repository_encapsulation`) remain 100% intact.
5. **Code Lock**: Code modifications locked until Gate 3 audit passes (`Rule 4`).

---

## 4. Proposed Architectural Changes
* **Constants**:
  * `LEGACY_DANGLING_TST_REFS = frozenset({"TST-FUSION-001", "TST-FUSION-002", "TST-FUSION-003", "TST-FUSION-004", "TST-INT-007", "TST-STR-010", "TST-UI-041", "TST-UI-044"})`
  * `LEGACY_DANGLING_REQ_REFS = frozenset({"REQ-MAP-037", "REQ-STB-013", "REQ-TRK-035", "REQ-UI-002", "REQ-UI-008", "REQ-UI-009", "REQ-UI-010", "REQ-UI-062", "REQ-UI-135", "REQ-UI-166", "REQ-UI-289"})`
* **Functions**:
  * `find_dangling_references(req_shards=None, tst_shards=None, repo_root=None)`:
    * Parses heading IDs across requirements and test shards.
    * Inspects `- **Tests**:` in REQs and `- **Requirement(s)**:` in TSTs.
    * Returns dict `{"dangling_tsts": [...], "dangling_reqs": [...]}` with `(source_file, line_no, source_id, missing_target_id)`.
  * `verify_dangling_references(repo_root=None, fail_on_dangling=False, legacy_tst_allowlist=None, legacy_req_allowlist=None)`:
    * Evaluates findings against baseline allowlists.
    * Returns `(success: bool, diagnostics: list[str])`.
* **Verifier Integration**:
  * Call `verify_dangling_references()` from `verify_governance()`.
  * Add `--enforce-links` / `--fail-on-dangling` CLI arguments.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `find_dangling_references` & `verify_dangling_references` in `tools/verify_requirement_governance.py`
* Define baseline allowlist constants.
* Implement `find_dangling_references()` supporting both repository scanning and synthetic in-memory/file shard inputs.
* Implement `verify_dangling_references()` with legacy vs. novel diagnostic categorizations.
* Wire into `verify_governance()` and CLI parser (`--enforce-links`, `--fail-on-dangling`).

### Step 2: Implement Unit Tests in `tools/test_verify_requirement_governance.py`
* Add comprehensive test cases in `TestDanglingReferencesGovernance`:
  * Dangling TST citation detection in REQs.
  * Dangling REQ citation detection in TSTs.
  * Legacy allowlist tolerance in standard mode.
  * Net-new dangling reference rejection.
  * Strict mode failure under `--enforce-links`.
  * Repository baseline verification.

### Step 3: Verification & Execution
* Run unit test suite: `python3 -m unittest tools/test_verify_requirement_governance.py`.
* Run root verifier: `python3 tools/verify_requirement_governance.py`.
* Run strict verifier to assert detection: `python3 tools/verify_requirement_governance.py --enforce-links` (asserting exit code 1 and all dangling refs reported).

---

## 6. Verification & Rollback Plan
* **Verification**: `python3 -m unittest tools/test_verify_requirement_governance.py` and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Clean worktree isolation allows immediate checkout/reset without impacting main repository or sibling worktrees.
