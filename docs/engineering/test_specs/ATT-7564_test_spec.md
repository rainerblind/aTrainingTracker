# Stage 2: Requirement & Test Specification - ATT-7564: Align release and PR CI gates with branch build, including governance script

**Ticket**: [ATT-7564](https://rainerblind.atlassian.net/browse/ATT-7564)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*ASPICE Governance & Process Stability*)  
**Target Release**: `V5.1.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-PRO-090` (*Unified CI & Release Quality Gates and Automated Governance Gating*)  
**Test Spec ID**: `TST-PRO-090` (*Unified CI & Release Quality Gates Contract Verification Suite*)  
**Branch**: `feat/ATT-7564`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-PRO-090)

### 1.1 Problem Statement & Rationale
ASPICE finding F7 revealed inconsistent quality gates across development and release branches. `.github/workflows/android-release.yml` omitted Android Lint, Detekt, Kover coverage verification, and requirement governance, while `.github/workflows/android-branch-build.yml` omitted requirement governance on branch pushes and PRs. `REQ-PRO-090` establishes quality gate parity across workflows, guaranteeing that release artifacts are built only from code satisfying all static analysis, coverage, and ASPICE SUP.8/SWE.4 governance criteria.

### 1.2 Attributes
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. *Release Workflow Quality Gate Parity (`REQ-PRO-090.1`)*: `.github/workflows/android-release.yml` SHALL execute `:app:lintDebug`, `:app:detekt`, `:app:koverVerifyDebug` (with report generation), and `python3 tools/verify_requirement_governance.py` prior to `:app:assembleRelease :app:bundleRelease`. Any gate failure SHALL immediately halt the workflow and block release artifact generation.
2. *Release Workflow Report Artifact Archiving (`REQ-PRO-090.2`)*: `.github/workflows/android-release.yml` SHALL upload report artifacts for lint (`app/build/reports/lint-results-debug.*`), detekt (`app/build/reports/detekt/`), and kover (`app/build/reports/kover/`) via `actions/upload-artifact@v4` guarded with `if: always()`.
3. *Branch & Pull Request Governance Script Gating (`REQ-PRO-090.3`)*: `.github/workflows/android-branch-build.yml` SHALL execute `python3 tools/verify_requirement_governance.py` in the `build-debug` job on all branch pushes and pull requests.
4. *Automated CI Contract Test Verification (`REQ-PRO-090.4`)*: `ReleaseCiAndLintConfigContractTest.kt` SHALL verify via automated unit tests that both workflow files contain all required gate commands in correct sequence and with proper artifact uploading.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Release Gate Execution)**: *Given* `.github/workflows/android-release.yml`, *When* evaluated by contract tests, *Then* `:app:lintDebug`, `:app:detekt`, `:app:koverVerifyDebug`, and `python3 tools/verify_requirement_governance.py` execute before `:app:assembleRelease :app:bundleRelease`.
* **Criterion 2 (Release Report Upload)**: *Given* `.github/workflows/android-release.yml`, *When* inspected, *Then* lint, detekt, and kover artifact uploads are configured with `if: always()`.
* **Criterion 3 (Branch Governance Gating)**: *Given* `.github/workflows/android-branch-build.yml`, *When* inspected, *Then* `python3 tools/verify_requirement_governance.py` executes in the `build-debug` job.
* **Criterion 4 (Contract Verification)**: *Given* `ReleaseCiAndLintConfigContractTest.kt`, *When* executed, *Then* all quality gate contract tests pass with 100% success.

### 1.5 System Invariants
- 100% clean-room test pass rate; zero production bytecode changes.
- Keystore and release signing secret provider chain remains intact.
- Parent ticket Human Decision Gate remains strictly enforced.

---

## 2. Test Specification (TST-PRO-090)

### Test Case 1: Release CI Gate Parity & Ordering Contract (`TST-PRO-090.1`)
* **Scope**: Unit Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* **Preconditions**: `.github/workflows/android-release.yml` exists.
* **Action**: Assert `android-release.yml` contains `:app:lintDebug`, `:app:detekt`, `:app:koverVerifyDebug`, and `python3 tools/verify_requirement_governance.py`, and that each step precedes `:app:assembleRelease :app:bundleRelease`.
* **Expected Result**: Assertions pass.

### Test Case 2: Release CI Report Artifact Archiving Contract (`TST-PRO-090.2`)
* **Scope**: Unit Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* **Preconditions**: `.github/workflows/android-release.yml` exists.
* **Action**: Assert `android-release.yml` uploads artifacts for lint report, detekt report, and kover report using `actions/upload-artifact` with `if: always()`.
* **Expected Result**: Assertions pass.

### Test Case 3: Branch Build Governance Step Contract (`TST-PRO-090.3`)
* **Scope**: Unit Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* **Preconditions**: `.github/workflows/android-branch-build.yml` exists.
* **Action**: Assert `android-branch-build.yml` contains `python3 tools/verify_requirement_governance.py` in `build-debug` job.
* **Expected Result**: Assertions pass.

### Test Case 4: Governance Verification & Living Doc Sync Suite (`TST-PRO-090.4`)
* **Scope**: Process Governance Suite
* **Target File**: `tools/verify_requirement_governance.py`
* **Action**: Execute `python3 tools/verify_requirement_governance.py` asserting exit code 0 and zero living documentation parity or traceability defects.
* **Expected Result**: Exit code 0, 100% parity verified.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-090.1` | Unit Contract | `testReleaseWorkflow_runsLintDetektKoverAndGovernanceBeforeAssembly` | `REQ-PRO-090.1` | Verified |
| `TST-PRO-090.2` | Unit Contract | `testReleaseWorkflow_archivesQualityGateReports` | `REQ-PRO-090.2` | Verified |
| `TST-PRO-090.3` | Unit Contract | `testBranchBuildWorkflow_runsGovernanceVerification` | `REQ-PRO-090.3` | Verified |
| `TST-PRO-090.4` | Governance | `tools/verify_requirement_governance.py` | `REQ-PRO-090.4` | Verified |
