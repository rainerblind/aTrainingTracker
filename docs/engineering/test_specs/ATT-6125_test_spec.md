# Stage 2: Requirement & Test Specification - ATT-6125: Repair corrupted Tests fields in requirement shards

**Ticket**: [ATT-6125](https://rainerblind.atlassian.net/browse/ATT-6125)  
**Sub-task**: `[Req & Test Spec]` (Created on submission)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-075` (*Requirement Shards Tests Field Format & Machine-Readable Traceability Integrity*)  
**Test Spec ID**: `TST-PRO-075`  
**Branch**: `subagent-Lane-2-Agent---ATT-6125-self-09c91856`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-075)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.1 BP1, bidirectional traceability between requirements and test cases must be robustly machine-checkable. In `docs/requirements/*.md`, ~70 `Tests` fields contain truncated fragments from historical table pipe splits, and 452 requirements list production Kotlin/Java source files instead of `TST-*` IDs. The system SHALL enforce that all `Tests` fields contain only valid backticked test IDs or standard placeholders, moving production source files to `Implementation`.

### 1.2 Functional & Architectural Requirements
* `REQ-PRO-075.1`: The `Tests:` field in every requirement section across `docs/requirements/*.md` SHALL strictly conform to comma-separated backticked test IDs (format `` `TST-[A-Z0-9_-]+` ``) or recognized placeholders (`None`, `Planned`, `Review`, `Manual inspection`).
* `REQ-PRO-075.2`: `tools/verify_requirement_governance.py` SHALL implement `verify_tests_field_format(repo_root=None)` executing as part of the standard governance verification pipeline, reporting file paths, line numbers, and requirement IDs for any violations.
* `REQ-PRO-075.3`: All 9 domain shards and the master index `docs/requirements.md` SHALL maintain 100% parity with zero format violations.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Format Verifier Enforcement)**:
  * *Given* living requirement shards in `docs/requirements/*.md`,
  * *When* `tools/verify_requirement_governance.py` validates `Tests:` fields,
  * *Then* it asserts zero format violations across all requirements.
* **Criterion 2 (Violation Rejection)**:
  * *Given* a requirement containing unclosed backticks, dangling sentence fragments, non-TST text, or source code files (`.kt`, `.java`, `.xml`) in `Tests:`,
  * *When* `verify_tests_field_format()` runs,
  * *Then* it returns a non-zero error count and logs detailed diagnostic failure information.
* **Criterion 3 (Shard Repair Integrity)**:
  * *Given* the 452 REQs with source files and ~70 truncated fragment entries,
  * *When* repair completes,
  * *Then* all misplaced source files are hoisted to `Implementation:` and `Tests:` contains valid `TST-*` IDs.

### 1.4 System Invariants
* Zero requirement deletions or heading ID alterations.
* Master table parity (`verify_master_table_parity`) remains 100% green.
* Zero changes to Android runtime binaries or application resources.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-075` and test `TST-PRO-075`. References `REQ-PRO-028` and `REQ-PRO-065`.
* **Historical Origin & Commit Trace**: Commit `06990c88` (Sprint 2026-44.1, `ATT-4594`) introduced domain-sharded requirements. Commit `c9c2d428` (`ATT-6124`) repaired the parser in `tools/shard_living_docs.py`.
* **Root Reason for Existing Formulation**: Shards were generated once by the initial script and never systematically retrofitted, leaving historical formatting artifacts in place.
* **Preservation of Core Invariants**: All 1072+ requirement identifiers, statuses, descriptions, and rationales are preserved. Bidirectional linkage to `docs/tests/` is elevated to 100% valid format. Zero production code changes in `app/src/main/`.

---

## 2. Test Specification (TST-PRO-075)

### Test Case 1: `test_verify_tests_field_format_valid_entries` (`TST-PRO-075.1`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Preconditions**: Synthetic shards with valid single and multiple backticked TST IDs, and placeholders (`None`, `Planned`, `Review`, `Manual inspection`).
* **Action**: Invoke `verify_tests_field_format(temp_dir)`.
* **Expected Result**: Returns 0 errors, output reports PASS.

### Test Case 2: `test_verify_tests_field_format_rejects_corrupted_and_source_files` (`TST-PRO-075.2`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Preconditions**: Synthetic shards containing unclosed backticks, dangling boolean expressions (`code == 201`), source files (`MyClass.kt`), and trailing `| **Tickets**: ATT-...`.
* **Action**: Invoke `verify_tests_field_format(temp_dir)`.
* **Expected Result**: Detects each error, reports line numbers and REQ IDs, returns non-zero error count.

### Test Case 3: Live Shard Audit & Master Parity (`TST-PRO-075.3`)
* **Scope**: Integration Test (`python3 tools/verify_requirement_governance.py`)
* **Preconditions**: Repaired live repository shards.
* **Action**: Execute `python3 tools/verify_requirement_governance.py`.
* **Expected Result**: Exit code 0, 100% parity across all 9 shards, zero dangling references, zero format violations.

### Test Case 4: Tier 1 Targeted Build Contract Suite (`TST-PRO-075.4`)
* **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-075.1` | Unit | `verify_tests_field_format` (valid cases) | `REQ-PRO-075.1` | Specified |
| `TST-PRO-075.2` | Unit | `verify_tests_field_format` (violations) | `REQ-PRO-075.1`, `REQ-PRO-075.2` | Specified |
| `TST-PRO-075.3` | Integration | `python3 tools/verify_requirement_governance.py` | `REQ-PRO-075.2`, `REQ-PRO-075.3` | Specified |
| `TST-PRO-075.4` | Build Contract | `ReleaseCiAndLintConfigContractTest` | `REQ-PRO-067` | Specified |
