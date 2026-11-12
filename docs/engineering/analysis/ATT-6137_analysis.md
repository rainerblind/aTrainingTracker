# Stage 1 Analysis: ATT-6137 - Archive JUnit results per CI run and release tag

**Ticket**: [ATT-6137](https://rainerblind.atlassian.net/browse/ATT-6137)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-1-Agent---ATT-6137-self-df20f2da`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Under ASPICE SWE.4 BP, SWE.6 BP3, and SUP.8 (Remediation ID: A-10), verification evidence must be baseline-controlled, integrity-preserved, and retrievable per CI run and release tag. Currently:
1. `docs/engineering/test_results` is a convenient local symlink into transient build outputs (`../../app/build/reports/tests/testDebugUnitTest`), but CI workflows (`.github/workflows/android-branch-build.yml` and `.github/workflows/android-release.yml`) do not archive or upload JUnit XML/HTML reports as workflow artifacts.
2. Tagged releases publish APK/AAB assets without versioned JUnit test report archives (`junit-test-results-<tag>.zip` or `.tar.gz`), leaving release tags without attached verification evidence.
3. No automated contract test or governance check enforces symlink integrity or workflow YAML validity.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: CI pipelines were initially optimized strictly for artifact compilation, signing, and static lint/kover reporting (`lint-report`, `kover-report`), omitting JUnit XML (`app/build/test-results/testDebugUnitTest/`) and HTML (`app/build/reports/tests/testDebugUnitTest/`) artifact uploads. Release releases attached only binaries via `softprops/action-gh-release@v2`.
* **Independent Peer Review & Verification**:
  * Inspected `.github/workflows/android-branch-build.yml`: Unit tests run at line 102 (`./gradlew :app:testDebugUnitTest`), but lines 105-134 only upload lint, detekt, and kover artifacts.
  * Inspected `.github/workflows/android-release.yml`: Unit tests run at line 101, but lines 110-125 package and attach only `.apk` and `.aab`.
  * Verified `docs/engineering/test_results`: Exists as a valid symlink to `../../app/build/reports/tests/testDebugUnitTest`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Preserve `docs/engineering/test_results -> ../../app/build/reports/tests/testDebugUnitTest` symlink (Rule 67) for 1-click local reports.
  2. Update `.github/workflows/android-branch-build.yml` with an `always()` step to upload JUnit XML and HTML reports as workflow artifacts on every CI run.
  3. Update `.github/workflows/android-release.yml` to package `junit-test-results-<tag>.zip` (or `.tar.gz`) and attach it to GitHub Releases.
  4. Implement automated verification via `ReleaseCiAndLintConfigContractTest.kt` and `tools/verify_requirement_governance.py` (symlink integrity and YAML syntax validation).
* **Out-of-Scope Non-Goals**:
  * Modifying test suites or test runner configurations in `app/build.gradle`.
  * Replacing or committing HTML files directly into git repository branches.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-new requirement**: `REQ-PRO-067` (*JUnit Test Results Archival & Evidence Integrity*) and `TST-PRO-067`. No existing requirements modified or relaxed.
* **Preservation of Core Invariants**: `Rule 67` canonical layout strictly preserved; 100% clean-room test pass rate; release signing and publishing workflows remain non-breaking.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-067` in `docs/requirements/req_process_stability.md` and `TST-PRO-067` in `docs/tests/tst_process_stability.md`.
* **Affected Architecture Elements**: `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, `tools/verify_requirement_governance.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`.
* **Verification Impact & Test Surface**: Contract test execution (`./gradlew testDebugUnitTest --tests "...ReleaseCiAndLintConfigContractTest"`), governance verification (`python3 tools/verify_requirement_governance.py`), and YAML syntax validation.
* **Risk Assessment & Invariants**: Low risk. Workflow additions use standard GitHub Actions steps (`actions/upload-artifact@v4` with `if: always()`, `softprops/action-gh-release@v2`). Zero impact on Android runtime application code.

---

## 6. Architectural Strategy & High-Level Solution
1. **Branch CI Archival**: Add `Upload Unit Test Results` step in `android-branch-build.yml` (`if: always()`) targeting `app/build/test-results/testDebugUnitTest/` and `app/build/reports/tests/testDebugUnitTest/`.
2. **Release CI Versioned Archive**: Add a packaging step in `android-release.yml` using `${GITHUB_REF_NAME:-release}` to create `junit-test-results-<tag>.zip` (with fallback to `.tar.gz`), add artifact upload (`release-unit-test-results`), and attach to release via `softprops/action-gh-release@v2`.
3. **Symlink & Governance Validation**: Enhance `verify_requirement_governance.py` to assert `docs/engineering/test_results` is a symlink pointing to `../../app/build/reports/tests/testDebugUnitTest`.
4. **Contract Testing**: Add `@Test` cases in `ReleaseCiAndLintConfigContractTest.kt` verifying YAML structure, `always()` condition, artifact upload paths, release attachment paths, and symlink integrity.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `docs/engineering/test_results` symlink must remain valid and operational for local developer 1-click test inspection.
  2. Release build and tag execution must not fail if optional transient reports are empty.
  3. Zero modifications to Android production runtime code in `app/src/main/`.
* **Risk Rating**: **LOW** (Declarative CI workflow enhancements and automated contract validation).
