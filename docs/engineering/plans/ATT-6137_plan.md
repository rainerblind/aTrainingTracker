# Stage 3: Implementation Plan - ATT-6137: Archive JUnit results per CI run and release tag

**Ticket**: [ATT-6137](https://rainerblind.atlassian.net/browse/ATT-6137)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-067` (*JUnit Test Results Archival & Evidence Integrity*)  
**Test Mapping**: `TST-PRO-067` (*JUnit Test Results Archival & Evidence Integrity Contract Test Suite*)  
**Branch**: `subagent-Lane-1-Agent---ATT-6137-self-df20f2da`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
ASPICE SWE.4 BP, SWE.6 BP3, and SUP.8 mandate controlled, retrievable verification evidence per CI run and tagged release (Remediation A-10). Currently, local developer inspection is served via the `docs/engineering/test_results` symlink into transient build directories (`app/build/reports/tests/testDebugUnitTest`), but CI branch and release workflows do not archive or attach JUnit reports. This plan details the declarative CI workflow enhancements, symlink governance enforcement, and automated contract tests.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-067` (`REQ-PRO-067.1` CI Branch Archival, `REQ-PRO-067.2` Release Tag Archival & Release Asset Attachment, `REQ-PRO-067.3` 1-Click Developer Symlink Preservation, `REQ-PRO-067.4` Governance & Contract Verification).
* **Test Mapping**: `TST-PRO-067` (`TST-PRO-067.1` CI workflow upload contract, `TST-PRO-067.2` Release archive packaging contract, `TST-PRO-067.3` Symlink integrity contract, `TST-PRO-067.4` Governance tooling test, `TST-PRO-067.5` YAML syntax verification).

---

## 3. System Invariants & Preserved Behavior
1. **Rule 67 Canonical Layout**: `docs/engineering/test_results` symlink remains intact pointing to `../../app/build/reports/tests/testDebugUnitTest`.
2. **Zero Production Runtime Impact**: No modifications to Android application runtime code in `app/src/main/`.
3. **Non-breaking Release Publishing**: Release APK and AAB artifact assembly and release signing remain undisturbed.
4. **Parent Human Gate Invariance**: Terminal acceptance of ATT-6137 remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **Component 1: GitHub Actions CI Workflows** (`.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`):
  * Add `Upload Unit Test Results` step in `android-branch-build.yml` with `if: always()`, archiving `app/build/test-results/testDebugUnitTest/` and `app/build/reports/tests/testDebugUnitTest/`.
  * Add `Package JUnit test results archive` and release asset attachment in `android-release.yml` for `junit-test-results-<tag>.zip` (and `.tar.gz`).
* **Component 2: Governance Tooling** (`tools/verify_requirement_governance.py`):
  * Enhance `verify_engineering_directory_structure()` to assert that `docs/engineering/test_results` is a symlink pointing to the canonical relative target `../../app/build/reports/tests/testDebugUnitTest`.
* **Component 3: Contract Test Suite** (`ReleaseCiAndLintConfigContractTest.kt`, `tools/test_verify_requirement_governance.py`):
  * Add JUnit assertions for workflow upload steps, `if: always()`, release attachment list, symlink target resolution, and YAML syntax validity.
* **UI Consistency**: No UI changes (CI workflow and process governance ticket).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: CI Branch Build Workflow Update
* File: `.github/workflows/android-branch-build.yml`
* Changes: Insert `Upload Unit Test Results` artifact step with `if: always()` immediately following `:app:testDebugUnitTest`.

### Step 2: Release Workflow Update
* File: `.github/workflows/android-release.yml`
* Changes: Add packaging step for `junit-test-results-${TAG_NAME}.zip`, add `Upload release unit test results` artifact step with `if: always()`, and update `Attach artifacts to GitHub Release` to include `build-evidence/junit-test-results-*`.

### Step 3: Governance Tooling Verification
* File: `tools/verify_requirement_governance.py` (and `tools/hoechstmass/tools/verify_requirement_governance.py`)
* Changes: Add explicit check asserting `docs/engineering/test_results` is a symbolic link pointing to `../../app/build/reports/tests/testDebugUnitTest`.

### Step 4: Contract Tests & Python Unit Tests
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`, `tools/test_verify_requirement_governance.py`
* Changes: Add tests asserting workflow declarations, symlink integrity, and YAML syntax validation.

### Step 5: Test Execution & Verification
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
* Command: `python3 -m unittest tools/test_verify_requirement_governance.py`
* Command: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit test contract and governance scripts under POSIX sprint lock.
* **Rollback**: Clean git working tree allows trivial reversal before merge onto `sprint/2026-46.4`.
