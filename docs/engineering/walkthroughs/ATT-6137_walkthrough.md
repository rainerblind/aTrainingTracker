# Stage 5: Walkthrough & Verification - ATT-6137: Archive JUnit results per CI run and release tag

**Ticket**: [ATT-6137](https://rainerblind.atlassian.net/browse/ATT-6137)  
**Sub-task**: Pending (`[Test]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-067`  
**Test Mapping**: `TST-PRO-067`  
**Branch**: `subagent-Lane-1-Agent---ATT-6137-self-df20f2da`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview
Under ASPICE SWE.4 BP, SWE.6 BP3, and SUP.8 (Remediation A-10), this improvement establishes automated archival of JUnit test results across CI workflows and release tags while strictly preserving local developer inspection convenience:
1. Updated `.github/workflows/android-branch-build.yml` with an `actions/upload-artifact@v4` step (`if: always()`) to archive JUnit XML reports (`app/build/test-results/testDebugUnitTest/`) and HTML reports (`app/build/reports/tests/testDebugUnitTest/`) on every CI branch run.
2. Updated `.github/workflows/android-release.yml` to package versioned JUnit test report archives (`junit-test-results-<tag>.zip` and `.tar.gz`), upload them as workflow artifacts, and attach them to GitHub Releases via `softprops/action-gh-release@v2`.
3. Preserved `docs/engineering/test_results -> ../../app/build/reports/tests/testDebugUnitTest` symlink (`Rule 67`) as the single 1-click local pointer to latest test runs.
4. Enhanced `tools/verify_requirement_governance.py` and `tools/hoechstmass/tools/verify_requirement_governance.py` to assert symlink integrity.
5. Added automated contract tests in `ReleaseCiAndLintConfigContractTest.kt` and Python test suite in `tools/test_verify_requirement_governance.py`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-067.1` | `TST-PRO-067.1` | `ReleaseCiAndLintConfigContractTest.testBranchBuildWorkflow_archivesUnitTestResultsWithAlwaysCondition` | **PASSED** | `Verified` |
| `REQ-PRO-067.2` | `TST-PRO-067.2` | `ReleaseCiAndLintConfigContractTest.testReleaseWorkflow_packagesAndAttachesUnitTestResults` | **PASSED** | `Verified` |
| `REQ-PRO-067.3` | `TST-PRO-067.3` | `ReleaseCiAndLintConfigContractTest.testEngineeringTestResultsSymlink_integrityAndCanonicalTarget` | **PASSED** | `Verified` |
| `REQ-PRO-067.4` | `TST-PRO-067.4` | `tools/test_verify_requirement_governance.py::test_scenario_24_verify_engineering_directory_structure_test_results_symlink` | **PASSED** | `Verified` |
| `REQ-PRO-067.4` | `TST-PRO-067.5` | `ReleaseCiAndLintConfigContractTest.testWorkflowYaml_syntaxAndStructure` & `test_scenario_25_workflow_yaml_syntax_validity` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope (`ReleaseCiAndLintConfigContractTest`)
```text
python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"
BUILD SUCCESSFUL in 3m 59s
33 actionable tasks: 20 executed, 13 from cache
```

### Python Governance Test Suite (`tools/test_verify_requirement_governance.py`)
```text
python3 -m unittest tools/test_verify_requirement_governance.py
Ran 25 tests in 0.750s
OK
```

### Living Docs Parity & Governance Verification (`tools/verify_requirement_governance.py`)
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero Composable islands detected (no modified UI files).
PASS: Zero repository encapsulation violations detected.
PASS: Zero architecture or system specification modifications detected.
```

---

## 4. Hardware / Physical Verification (Pixel 10)
Pure CI workflow, tooling, and contract test enhancement; zero APK runtime bytecode changes; verified via automated contract and unit test suite. No UI changes.

---

## 5. Invariant & Governance Verification
1. **Rule 67 Canonical Layout**: `docs/engineering/test_results` symlink verified intact and pointing to `../../app/build/reports/tests/testDebugUnitTest`.
2. **Living Documentation Synchronized**: Status in `docs/requirements.md` and `docs/tests.md` updated with 100% mathematical parity.
3. **Subtask Completion**: Stage 5 subtask transitioned to `Erledigt` via `gate-verdict PASS`.
4. **Parent Ticket Final Review**: Handed over to Stage 6 Cold Carbon Review before `Final Review (Human)`.
