# Stage 2: Requirement & Test Specification - ATT-6138: Generate release verification summary per tag

**Ticket**: [ATT-6138](https://rainerblind.atlassian.net/browse/ATT-6138)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-074` (*Release Verification Summary Generation per Tag*)  
**Test Spec ID**: `TST-PRO-074` (*Release Verification Summary Generator Test Suite*)  
**Branch**: `subagent-Lane-2-Agent---ATT-6138-self-aabf75cf`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-074)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.6 BP5 (Finding A-11), release decisions must be based on recorded, baseline-controlled verification evidence. Prior to this improvement, no single release-level summary existed defining what was verified, with which result, on which baseline. A standalone generator script SHALL consolidate unit test metrics, bidirectional requirement-to-test traceability coverage, system integration verification results (SYS.4), and system verification protocol sign-off records (SYS.5) into an authoritative Markdown summary per release tag (`docs/releases/<tag>/verification_summary.md`), explicitly highlighting all deviations requiring human approval.

### 1.2 Functional & Architectural Requirements
1. **Automated Release Verification Summary CLI (`REQ-PRO-074.1`)**: `tools/generate_release_verification_summary.py` SHALL provide a command-line interface accepting `--tag <TAG>` (mandatory), optional `--commit <SHA>` (defaulting to current HEAD commit), optional `--out <PATH>` (defaulting to `docs/releases/<tag>/verification_summary.md`), and optional threshold parameters (`--min-req-tst-coverage`, `--min-tst-code-coverage`, `--min-bidirectional-coverage`, `--max-code-without-spec`).
2. **Unit & Regression Test Evidence Aggregation (`REQ-PRO-074.2`)**: The generator SHALL aggregate total unit test count, passed, failed, and skipped tests from `app/build/test-results/testDebugUnitTest/` XML files or test archive files, and reference the versioned JUnit test archive artifact (`build-evidence/junit-test-results-<tag>.zip` per ATT-6137).
3. **Bidirectional REQ-TST Traceability Matrix Aggregation (`REQ-PRO-074.3`)**: The generator SHALL query and embed bidirectional requirement-to-test coverage data via `tools/verify_requirement_governance.py --coverage-json` (per ATT-6122), presenting REQ-to-TST coverage %, TST-to-code coverage %, and bidirectional coverage metrics.
4. **System Integration & Verification Protocol Status Aggregation (`REQ-PRO-074.4`)**: The generator SHALL parse and summarize SYS.4 System Integration Verification results from `docs/releases/<tag>/system_integration_results.json` (per ATT-6166) and SYS.5 System Verification Protocol sign-off records from `docs/releases/<tag>/sys_verification_record.md` (per ATT-6167).
5. **Open Deviations & Human Approval Gating (`REQ-PRO-074.5`)**: The generator SHALL evaluate whether any test failure, missing evidence file, or coverage below configured thresholds exists, and enumerate them under an explicit `Deviations Requiring Human Approval` section, assigning an overall status of `PASS (READY FOR RELEASE)` or `BLOCKED / REQUIRES HUMAN APPROVAL`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Summary Generation)**:
  * *Given* release evidence artifacts for a release tag,
  * *When* `tools/generate_release_verification_summary.py --tag <TAG>` executes,
  * *Then* `docs/releases/<tag>/verification_summary.md` is generated containing Unit Tests, Traceability Coverage, SYS.4, and SYS.5 sections.
* **Criterion 2 (Deviations Requiring Human Approval)**:
  * *Given* any failing test, missing evidence, or coverage below threshold,
  * *When* the summary is generated,
  * *Then* it is explicitly listed under `Deviations Requiring Human Approval` with overall status `BLOCKED / REQUIRES HUMAN APPROVAL`.
* **Criterion 3 (Automated Unit Testing & Governance Parity)**:
  * *Given* `tools/test_generate_release_verification_summary.py`,
  * *When* executed via unittest/pytest,
  * *Then* 100% of unit tests pass and `tools/verify_requirement_governance.py` confirms 100% living doc parity.

### 1.4 System Invariants
* Pure Python 3 standard library CLI without external package dependencies.
* Zero modifications to Android production runtime code in `app/src/main/`.

---

## 2. Test Specification (TST-PRO-074)

### Test Case 1: CLI Argument Parsing & Default Output Path (`TST-PRO-074.1`)
* **Scope**: Tooling Unit Test (`tools/test_generate_release_verification_summary.py`)
* **Target**: `parse_args()`
* **Assertion**: Verify CLI accepts `--tag`, `--commit`, `--out`, and threshold flags, correctly resolving `--out` to `docs/releases/<tag>/verification_summary.md` by default.

### Test Case 2: Unit Test Report Parsing & Archival Link (`TST-PRO-074.2`)
* **Scope**: Tooling Unit Test (`tools/test_generate_release_verification_summary.py`)
* **Target**: `collect_unit_test_summary()`
* **Assertion**: Verify parsing of JUnit XML reports and versioned archive reference `build-evidence/junit-test-results-<tag>.zip`. If reports directory is missing or empty, verify missing evidence deviation is recorded.

### Test Case 3: Coverage JSON Integration & Threshold Verification (`TST-PRO-074.3`)
* **Scope**: Tooling Unit Test (`tools/test_generate_release_verification_summary.py`)
* **Target**: `collect_coverage_summary()`
* **Assertion**: Verify extraction of bidirectional REQ-TST, TST-to-code, and code-to-spec metrics from `--coverage-json` structure, flagging threshold breaches as deviations.

### Test Case 4: SYS.4 and SYS.5 Evidence Parsing & Deviation Detection (`TST-PRO-074.4`)
* **Scope**: Tooling Unit Test (`tools/test_generate_release_verification_summary.py`)
* **Target**: `collect_sys4_summary()` and `collect_sys5_summary()`
* **Assertion**: Verify parsing of `system_integration_results.json` and `sys_verification_record.md`, flagging missing files, failed tests, unedited template placeholders, and unsigned protocols under deviations requiring human approval.

### Test Case 5: End-to-End Markdown Summary Generation & Governance Parity (`TST-PRO-074.5`)
* **Scope**: End-to-End Tooling & Governance Test
* **Target**: `generate_release_verification_summary()` & `tools/verify_requirement_governance.py`
* **Assertion**: Verify end-to-end generation of `verification_summary.md` across both PASS and BLOCKED scenarios, and confirm 100% living documentation parity.

---

## 3. Traceability Matrix

| Test Case | Scope | Function Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-074.1` | Unit | `parse_args` | `REQ-PRO-074.1` | Specified |
| `TST-PRO-074.2` | Unit | `collect_unit_test_summary` | `REQ-PRO-074.2` | Specified |
| `TST-PRO-074.3` | Unit | `collect_coverage_summary` | `REQ-PRO-074.3` | Specified |
| `TST-PRO-074.4` | Unit | `collect_sys4_summary`, `collect_sys5_summary` | `REQ-PRO-074.4`, `REQ-PRO-074.5` | Specified |
| `TST-PRO-074.5` | E2E | `generate_release_verification_summary`, `verify_requirement_governance.py` | `REQ-PRO-074.1`–`REQ-PRO-074.5` | Specified |
