# Stage 2: Requirement & Test Specification - ATT-6137: Archive JUnit results per CI run and release tag

**Ticket**: [ATT-6137](https://rainerblind.atlassian.net/browse/ATT-6137)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-067` (*JUnit Test Results Archival & Evidence Integrity*)  
**Test Spec ID**: `TST-PRO-067` (*JUnit Test Results Archival & Evidence Integrity Contract Test Suite*)  
**Branch**: `subagent-Lane-1-Agent---ATT-6137-self-df20f2da`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-067)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.4 BP, SWE.6 BP3, and SUP.8 (Remediation ID: A-10), verification evidence must be baseline-controlled, integrity-preserved, and retrievable per CI run and release tag. Local transient reports via `docs/engineering/test_results` enable effortless developer inspection, while CI artifact uploads and release tag attachments ensure persistent, auditable verification evidence.

### 1.2 Functional & Architectural Requirements
1. **CI Branch Run Archival (`REQ-PRO-067.1`)**: `.github/workflows/android-branch-build.yml` SHALL upload JUnit XML reports (`app/build/test-results/testDebugUnitTest/`) and HTML reports (`app/build/reports/tests/testDebugUnitTest/`) using `actions/upload-artifact@v4` with `if: always()` after unit test execution.
2. **Release Tag Packaging & Attachment (`REQ-PRO-067.2`)**: `.github/workflows/android-release.yml` SHALL package unit test results into a versioned archive `junit-test-results-<tag>.zip` (or `.tar.gz`) and attach it to the GitHub Release via `softprops/action-gh-release@v2`. It SHALL also upload reports as a workflow artifact.
3. **Local 1-Click Developer Symlink (`REQ-PRO-067.3`)**: `docs/engineering/test_results` SHALL remain a symbolic link pointing to `../../app/build/reports/tests/testDebugUnitTest` (Rule 67).
4. **Governance & Contract Verification (`REQ-PRO-067.4`)**: Automated contract tests in `ReleaseCiAndLintConfigContractTest.kt` and `tools/verify_requirement_governance.py` SHALL verify workflow YAML validity, `if: always()` archival, release attachment, and symlink integrity.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (CI Branch Archival)**:
  * *Given* a CI branch build executing unit tests,
  * *When* unit tests finish (pass or fail),
  * *Then* JUnit XML and HTML reports are uploaded as a workflow artifact (`if: always()`).
* **Criterion 2 (Release Test Evidence)**:
  * *Given* a release tag build,
  * *When* it completes,
  * *Then* the versioned `junit-test-results-<tag>.zip` (or `.tar.gz`) archive is attached to the GitHub Release.
* **Criterion 3 (Symlink Integrity)**:
  * *Given* the repository checkout,
  * *When* inspecting `docs/engineering/test_results`,
  * *Then* it is a valid symlink pointing to `../../app/build/reports/tests/testDebugUnitTest`.

### 1.4 System Invariants
* Zero modifications to Android production runtime code in `app/src/main/`.
* Rule 67 canonical directory structure strictly preserved.

---

## 2. Test Specification (TST-PRO-067)

### Test Case 1: `testBranchBuildWorkflow_archivesUnitTestResults` (`TST-PRO-067.1`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify `.github/workflows/android-branch-build.yml` contains an `actions/upload-artifact` step with `if: always()`, uploading `test-results/testDebugUnitTest` and `reports/tests/testDebugUnitTest`.

### Test Case 2: `testReleaseWorkflow_packagesAndAttachesUnitTestResults` (`TST-PRO-067.2`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify `.github/workflows/android-release.yml` packages `junit-test-results-*.zip` or `.tar.gz` and attaches the archive in `softprops/action-gh-release@v2`.

### Test Case 3: `testTestResultsSymlink_integrityAndCanonicalTarget` (`TST-PRO-067.3`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify `docs/engineering/test_results` is a symlink pointing to `../../app/build/reports/tests/testDebugUnitTest`.

### Test Case 4: Governance Tooling Verification (`TST-PRO-067.4`)
* **Scope**: Python Unit Test (`tools/test_verify_requirement_governance.py`)
* **Assertion**: Assert `verify_engineering_directory_structure()` validates symlink integrity and target.

### Test Case 5: Workflow YAML Syntax Validation (`TST-PRO-067.5`)
* **Scope**: Contract Test & Python Test
* **Assertion**: Assert YAML syntax for both workflows is valid and well-formed without parsing exceptions.

### Test Case 6: Tier 1 Targeted Regression Suite
* **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-067.1` | Contract Test | `ReleaseCiAndLintConfigContractTest.testBranchBuildWorkflow_archivesUnitTestResultsWithAlwaysCondition` | `REQ-PRO-067.1` | Specified |
| `TST-PRO-067.2` | Contract Test | `ReleaseCiAndLintConfigContractTest.testReleaseWorkflow_packagesAndAttachesUnitTestResults` | `REQ-PRO-067.2` | Specified |
| `TST-PRO-067.3` | Contract Test | `ReleaseCiAndLintConfigContractTest.testEngineeringTestResultsSymlink_integrityAndCanonicalTarget` | `REQ-PRO-067.3` | Specified |
| `TST-PRO-067.4` | Tooling Test | `tools/test_verify_requirement_governance.py` | `REQ-PRO-067.4` | Specified |
| `TST-PRO-067.5` | Syntax Test | `ReleaseCiAndLintConfigContractTest.testWorkflowYaml_syntaxAndStructure` | `REQ-PRO-067.4` | Specified |
