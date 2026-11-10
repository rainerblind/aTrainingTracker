# Stage 3: Implementation Plan - ATT-6177: Remove Stale Travis CI Configuration

**Ticket**: [ATT-6177](https://rainerblind.atlassian.net/browse/ATT-6177)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6177`  
**Requirement Mapping**: `REQ-PRO-042` (`REQ-PRO-042.1`..`REQ-PRO-042.3`)  
**Test Mapping**: `TST-PRO-042` (`TST-PRO-042.1`..`TST-PRO-042.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
The repository retains an unmaintained `.travis.yml` file dating back to 2019 (JDK 8, API 29), and `app/build.gradle:35` retains the obsolete comment `// Travis update separately`. In modern development, CI/CD is managed exclusively by GitHub Actions in `.github/workflows/` (`android-branch-build.yml`, `android-release.yml`) using JDK 21 and Android API 37. Retaining stale CI configuration violates ASPICE SUP.8 (Configuration Management) and creates ambiguity in automated build execution.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-042` (*CI Configuration Integrity & Legacy Travis CI Deprecation*)
  * `REQ-PRO-042.1`: Remove `.travis.yml` from repository root and eradicate Travis references in `app/build.gradle`.
  * `REQ-PRO-042.2`: Maintain GitHub Actions workflows in `.github/workflows/` as the sole authoritative CI platform.
  * `REQ-PRO-042.3`: Automate CI configuration integrity enforcement via contract test in `ReleaseCiAndLintConfigContractTest.kt`.
* **Test Mapping**: `TST-PRO-042` (`TST-PRO-042.1`..`TST-PRO-042.4`)
  * `TST-PRO-042.1`: Absence test for `.travis.yml` in repository root.
  * `TST-PRO-042.2`: Absence test for `"travis"` in `app/build.gradle`.
  * `TST-PRO-042.3`: Unbroken continuity of existing release workflow verification tests.
  * `TST-PRO-042.4`: Targeted unit test execution (`com.atrainingtracker.trainingtracker.build.*`).

---

## 3. System Invariants & Preserved Behavior
1. **Authoritative CI Continuity**: GitHub Actions workflows in `.github/workflows/` remain completely untouched and authoritative.
2. **Build Configuration Invariance**: `app/build.gradle` compilation targets (`compileSdkVersion = 37`, `targetSdkVersion 37`, `minSdkVersion 26`) and dependencies remain 100% unchanged.
3. **Zero Runtime Impact**: Zero changes to production Kotlin/Java source code in `app/src/main/` or database schemas.

---

## 4. Proposed Architectural Changes
* **File System (`.travis.yml`)**: Delete `.travis.yml` using `git rm .travis.yml`.
* **Build Script (`app/build.gradle`)**: Remove comment line `// Travis update separately` above `compileSdkVersion = 37`.
* **Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)**: Add `@Test fun testCiConfiguration_noStaleTravisArtifacts()` asserting `.travis.yml` does not exist in the candidate root directories and `app/build.gradle` contains no case-insensitive `"travis"`.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Remove `.travis.yml`)**: Execute `git rm .travis.yml` to remove the dead configuration file (`REQ-PRO-042.1`, `TST-PRO-042.1`).
2. **Step 2 (Clean `app/build.gradle`)**: Remove line 35 `// Travis update separately` from `app/build.gradle` (`REQ-PRO-042.1`, `TST-PRO-042.2`).
3. **Step 3 (Add Contract Test in `ReleaseCiAndLintConfigContractTest.kt`)**: Implement `testCiConfiguration_noStaleTravisArtifacts()` asserting absence of `.travis.yml` and absence of `"travis"` in `app/build.gradle` (`REQ-PRO-042.3`, `TST-PRO-042.1`, `TST-PRO-042.2`, `TST-PRO-042.3`).
4. **Step 4 (Targeted Test Execution)**: Run targeted test suite:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"
   ```
   Confirm 100% PASS across all build/CI contract tests (`TST-PRO-042.4`).

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`.
* **Rollback Plan**: Changes are isolated to `feature/ATT-6177`; can be discarded cleanly via `git checkout -- .` and `git clean -fd`.
