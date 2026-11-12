# Stage 2: Requirement & Test Specification - ATT-6120: Enforce Unique REQ and TST IDs in Governance Verifier

**Ticket**: [ATT-6120](https://rainerblind.atlassian.net/browse/ATT-6120)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-066` | **Test Spec ID**: `TST-PRO-066`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-066)

### 1.1 Problem Statement & Rationale
Requirement (`### REQ-*`) and test (`### TST-*`) heading identifiers across domain shards under `docs/requirements/` and `docs/tests/` must be globally unique to guarantee unambiguous bidirectional traceability and reliable ASPICE evidence tracking. Currently, `verify_master_table_parity()` aggregates counts without asserting ID uniqueness, allowing 11 duplicate IDs to exist undetected.

### 1.2 Functional & Architectural Requirements
1. **Duplicate Heading ID Detection (`REQ-PRO-066.1`)**: `tools/verify_requirement_governance.py` SHALL scan all `.md` shard files under `docs/requirements/` and `docs/tests/` for requirement (`### REQ-*`) and test (`### TST-*`) headings. For each duplicate ID detected, it SHALL record the identifier, filename, and line number.
2. **Legacy Duplicate Baseline Allowlist & CI Non-Regression (`REQ-PRO-066.2`)**: `tools/verify_requirement_governance.py` SHALL maintain a controlled baseline allowlist (`LEGACY_DUPLICATE_IDS`) of the 11 known pre-existing duplicates (to be remediated in ATT-6123). In standard mode, known legacy duplicates SHALL emit warning diagnostics without failing. Any net-new duplicate (not in the legacy baseline) SHALL fail verification with exit code 1.
3. **Strict Uniqueness Enforcement Flag (`REQ-PRO-066.3`)**: `tools/verify_requirement_governance.py` SHALL support `--enforce-unique` / `--fail-on-duplicates` to activate strict zero-tolerance mode, failing with exit code 1 if any duplicate identifier exists.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* two heading definitions share an ID not in `LEGACY_DUPLICATE_IDS`,
  * *When* `verify_requirement_governance.py` runs,
  * *Then* it outputs an error listing the duplicate ID with all offending filenames and line numbers, and exits with code 1.
* *Given* duplicate IDs match only the known legacy baseline,
  * *When* `verify_requirement_governance.py` runs in standard mode,
  * *Then* it outputs warnings and exits with code 0 (passing).
* *Given* known legacy duplicates exist and `--enforce-unique` is supplied,
  * *When* `verify_requirement_governance.py --enforce-unique` runs,
  * *Then* it lists all duplicates with file and line numbers and exits with code 1.

### 1.4 System Invariants
Pure Python 3 standard library; zero modifications to Android runtime code in `app/src/main/`; existing governance checks (master table parity, archaeology, directory structure) remain 100% intact.

---

## 2. Test Specification (TST-PRO-066)

### Test Case 1: Duplicate Detection & File/Line Diagnostics (`TST-PRO-066.1`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`).
* **Assertions**: `verify_unique_ids()` on synthetic shard files with duplicated IDs returns `success = False` and diagnostics containing exact identifier, filename, and line numbers for each occurrence.

### Test Case 2: Legacy Baseline Tolerance & New Duplicate Rejection (`TST-PRO-066.2`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`).
* **Assertions**: Shards containing only IDs from `LEGACY_DUPLICATE_IDS` return `success = True` in standard mode; injecting a novel duplicate outside the baseline immediately returns `success = False`.

### Test Case 3: Strict Enforcement Option (`TST-PRO-066.3`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`).
* **Assertions**: Passing `fail_on_duplicates=True` or CLI `--enforce-unique` / `--fail-on-duplicates` causes `verify_unique_ids()` to return `success = False` even when duplicates match the legacy baseline.

### Test Case 4: Repository Governance Verification (`TST-PRO-066.4`)
* **Scope**: Regression / Tooling Contract (`tools/test_verify_requirement_governance.py`).
* **Assertions**: Running `verify_unique_ids()` against the authentic repository passes cleanly with 0 new duplicates and exits with code 0.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PRO-066.1]` | Unit | `test_duplicate_heading_detection_reports_file_and_line` | `REQ-PRO-066.1` | Planned |
| `[TST-PRO-066.2]` | Unit | `test_legacy_baseline_tolerance_and_new_duplicate_rejection` | `REQ-PRO-066.2` | Planned |
| `[TST-PRO-066.3]` | Unit | `test_strict_enforce_unique_flag` | `REQ-PRO-066.3` | Planned |
| `[TST-PRO-066.4]` | Regression | `test_repository_unique_ids_clean_pass` | `REQ-PRO-066.1`, `REQ-PRO-066.2` | Planned |
