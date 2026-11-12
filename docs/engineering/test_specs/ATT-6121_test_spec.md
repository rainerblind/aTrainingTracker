# Stage 2: Requirement & Test Specification - ATT-6121: Detect Dangling REQ and TST References in Governance Verifier

**Ticket**: [ATT-6121](https://rainerblind.atlassian.net/browse/ATT-6121)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-070` | **Test Spec ID**: `TST-PRO-070`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-070)

### 1.1 Problem Statement & Rationale
Citations between requirements and test cases across domain shards under `docs/requirements/` and `docs/tests/` must point to authentic, existing target headings (`### REQ-*`, `### TST-*`) to guarantee evidence integrity, bidirectional traceability, and ASPICE SWE.1 BP5 / SWE.6 BP4 compliance. Currently, 8 unique TST IDs cited by REQs and 11 unique REQ IDs cited by TSTs do not exist in heading anchors, breaking automated traceability resolution.

### 1.2 Functional & Architectural Requirements
1. **Dangling TST Detection in Requirements (`REQ-PRO-070.1`)**: `tools/verify_requirement_governance.py` SHALL extract all cited test identifiers (`TST-*`) from the `Tests` field of every requirement in `docs/requirements/*.md` and verify each cited test matches an existing `### TST-*` heading in `docs/tests/*.md`. Missing IDs SHALL be reported with source file, line number, requirement ID, and missing test ID.
2. **Dangling REQ Detection in Tests (`REQ-PRO-070.2`)**: `tools/verify_requirement_governance.py` SHALL extract all cited requirement identifiers (`REQ-*`) from the `Requirements` (or `Requirement(s)`) field of every test in `docs/tests/*.md` and verify each cited requirement matches an existing `### REQ-*` heading in `docs/requirements/*.md`. Missing IDs SHALL be reported with source file, line number, test ID, and missing requirement ID.
3. **Legacy Dangling Reference Allowlist & CI Non-Regression (`REQ-PRO-070.3`)**: `tools/verify_requirement_governance.py` SHALL maintain controlled baseline allowlists (`LEGACY_DANGLING_TST_REFS`, `LEGACY_DANGLING_REQ_REFS`) for pre-existing legacy dangling references slated for backfill in A-08.x (ATT-6127..6135). In default mode, known legacy items emit warnings without failing. Any net-new dangling reference outside the allowlist SHALL fail verification with exit code 1.
4. **Strict Reference Enforcement Flag (`REQ-PRO-070.4`)**: `tools/verify_requirement_governance.py` SHALL support CLI flags `--enforce-links` / `--fail-on-dangling` to activate strict zero-tolerance mode, failing with exit code 1 if ANY dangling reference is detected.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* a requirement cites a non-existent `TST-*` ID or a test cites a non-existent `REQ-*` ID outside the legacy allowlist,
  * *When* `verify_requirement_governance.py` runs,
  * *Then* it outputs an error identifying the dangling reference with source file, line number, and missing ID, and exits with code 1.
* *Given* dangling references match only the known legacy baseline allowlist,
  * *When* `verify_requirement_governance.py` runs in standard mode,
  * *Then* it outputs warnings and exits with code 0 (passing).
* *Given* known legacy dangling references exist and `--enforce-links` is supplied,
  * *When* `verify_requirement_governance.py --enforce-links` runs,
  * *Then* it lists all dangling references with file, line, and missing IDs, and exits with code 1.

### 1.4 System Invariants
Pure Python 3 standard library; zero modifications to Android runtime code in `app/src/main/`; existing governance checks (master table parity, archaeology, uniqueness, directory structure) remain 100% intact.

---

## 2. Test Specification (TST-PRO-070)

### Test Case 1: Dangling TST Detection in Requirements (`TST-PRO-070.1`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`).
* **Assertions**: `find_dangling_references()` on synthetic shard files with unresolvable `TST-*` IDs in `- **Tests**:` fields reports exact source file, line number, requirement ID, and missing test ID.

### Test Case 2: Dangling REQ Detection in Tests (`TST-PRO-070.2`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`).
* **Assertions**: `find_dangling_references()` on synthetic shard files with unresolvable `REQ-*` IDs in `- **Requirement(s)**:` fields reports exact source file, line number, test ID, and missing requirement ID.

### Test Case 3: Legacy Baseline Tolerance & New Dangling Link Rejection (`TST-PRO-070.3`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`).
* **Assertions**: Shards containing only IDs from the legacy baseline allowlists return `success = True` in standard mode; injecting a novel dangling link immediately returns `success = False`.

### Test Case 4: Strict Enforcement Mode (`TST-PRO-070.4`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`).
* **Assertions**: Supplying `fail_on_dangling=True` or CLI `--enforce-links` causes `verify_dangling_references()` to return `success = False` when allowlisted legacy dangling links exist.

### Test Case 5: Clean Repository Resolution (`TST-PRO-070.5`)
* **Scope**: Regression / Tooling Contract (`tools/test_verify_requirement_governance.py`).
* **Assertions**: Running `verify_dangling_references()` against the repository living docs passes cleanly with zero novel dangling references and exits with code 0.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PRO-070.1]` | Unit | `test_dangling_tst_detection_in_requirements` | `REQ-PRO-070.1` | Planned |
| `[TST-PRO-070.2]` | Unit | `test_dangling_req_detection_in_tests` | `REQ-PRO-070.2` | Planned |
| `[TST-PRO-070.3]` | Unit | `test_legacy_dangling_tolerance_and_new_reference_rejection` | `REQ-PRO-070.3` | Planned |
| `[TST-PRO-070.4]` | Unit | `test_strict_enforce_links_flag` | `REQ-PRO-070.4` | Planned |
| `[TST-PRO-070.5]` | Regression | `test_repository_dangling_references_baseline_pass` | `REQ-PRO-070.1`, `REQ-PRO-070.3` | Planned |
