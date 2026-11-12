# Stage 3: Implementation Plan - ATT-6125: Repair corrupted Tests fields in requirement shards

**Ticket**: [ATT-6125](https://rainerblind.atlassian.net/browse/ATT-6125)  
**Sub-task**: `[Impl-Plan]` (Created on submission)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-075` (*Requirement Shards Tests Field Format & Machine-Readable Traceability Integrity*)  
**Test Mapping**: `TST-PRO-075` (*Requirement Tests Field Format & Traceability Governance Verifier Test Suite*)  
**Branch**: `subagent-Lane-2-Agent---ATT-6125-self-09c91856`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
In `docs/requirements/*.md`, ~70 `Tests` fields contain truncated sentence fragments from unescaped pipe splits during initial sharding, and 452 requirements list production source files instead of test IDs due to 6-col vs 7-col table schema shifts. This plan establishes the automated repair pipeline, the governance validation rule in `tools/verify_requirement_governance.py`, and comprehensive unit tests in `tools/test_verify_requirement_governance.py`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-075` (`REQ-PRO-075.1`–`REQ-PRO-075.3`)
* **Test Mapping**: `TST-PRO-075` (`TST-PRO-075.1`–`TST-PRO-075.4`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Requirement Loss or Mutation**: 100% of requirement heading IDs, statuses, and descriptive contents are strictly preserved.
2. **Master Table Parity Invariant**: Master index tables in `docs/requirements.md` and `docs/tests.md` maintain 100% parity across all 9 domain shards.
3. **Format Purity**: Every `Tests:` field matches `^(`TST-[A-Z0-9_-]+`|\b(None|Planned|Review|Manual inspection)\b)(\s*,\s*(`TST-[A-Z0-9_-]+`|\b(None|Planned|Review|Manual inspection)\b))*$`.
4. **Zero Production Android Runtime Impact**: Zero modifications to `app/src/main/` classes or resources.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-075` and test `TST-PRO-075`. References `REQ-PRO-028` and `REQ-PRO-065`.
* **Historical Origin & Commit Trace**: Commit `06990c88` (Sprint 2026-44.1, `ATT-4594`) introduced domain-sharded requirements. Commit `c9c2d428` (`ATT-6124`) repaired the parser in `tools/shard_living_docs.py`.
* **Root Reason for Existing Formulation**: Shards were generated once by the initial script and never systematically retrofitted, leaving historical formatting artifacts in place.
* **Preservation of Core Invariants**: All 1072+ requirement identifiers, statuses, descriptions, and rationales are preserved. Bidirectional linkage to `docs/tests/` is elevated to 100% valid format. Zero production code changes in `app/src/main/`.

---

## 4. Proposed Architectural Changes

### Component 1: Shard Repair Utility (`tools/repair_requirement_shards.py`)
* Dedicated one-time repair script operating on all 9 shards in `docs/requirements/*.md`.
* Employs `split_markdown_table_row` from `tools/shard_living_docs.py` against `06990c88~1:docs/requirements.md` to extract historical ground-truth `tests` and `implementation` values for 438 source-file occurrences and all 68 truncated fragment occurrences.
* Resolves remaining 14 non-historical occurrences via reverse-lookup from `docs/tests/tst_*.md`.
* Hoists misplaced source files into `Implementation:` and assigns valid backticked `TST-*` IDs to `Tests:`.
* Strips trailing `| **Tickets**: ...` annotations from `Tests:` rows.

### Component 2: Governance Verifier Rule (`tools/verify_requirement_governance.py`)
* Function `verify_tests_field_format(repo_root=None)` scanning all `docs/requirements/req_*.md` files.
* Checks each `Tests:` line with strict regex syntax: `^(`TST-[A-Z0-9_-]+`|None|Planned|Review|Manual inspection)(\s*,\s*(`TST-[A-Z0-9_-]+`|None|Planned|Review|Manual inspection))*$`.
* Emits errors with file path, line number, and REQ ID.

### Component 3: Test Verification Suite (`tools/test_verify_requirement_governance.py`)
* Adds `test_verify_tests_field_format_valid_entries` testing valid single/multiple TST IDs and recognized placeholders.
* Adds `test_verify_tests_field_format_rejects_corrupted_and_source_files` testing detection of unclosed backticks, dangling sentence fragments, source code filenames, and trailing ticket annotations.

### UI Consistency
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Execute Shard Repair Script
* Files: `tools/repair_requirement_shards.py`, `docs/requirements/req_*.md`
* Actions:
  1. Author `tools/repair_requirement_shards.py` leveraging git history and test shards.
  2. Execute repair script to sanitize all 9 requirement shards.
  3. Verify clean git diff across `docs/requirements/`.

### Step 2: Implement Governance Verifier Rule
* File: `tools/verify_requirement_governance.py`
* Actions:
  1. Add `verify_tests_field_format(repo_root=None)`.
  2. Hook into `verify_governance()` orchestrator.
  3. Validate against live repaired shards.

### Step 3: Author Unit Tests for Verifier Rule
* File: `tools/test_verify_requirement_governance.py`
* Actions:
  1. Add test methods in `TestRequirementGovernance` validating both passing and failing synthetic shard samples.
  2. Run `python3 tools/test_verify_requirement_governance.py`.

### Step 4: Synchronize Master Indexes & Run Governance
* Files: `docs/requirements.md`, `docs/tests.md`
* Actions:
  1. Run `python3 tools/verify_requirement_governance.py --sync-indexes`.
  2. Run `python3 tools/verify_requirement_governance.py` and confirm 100% green exit code 0.

### Step 5: Execute Tier 1 Build Contract Regression Suite
* Action: Run `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
