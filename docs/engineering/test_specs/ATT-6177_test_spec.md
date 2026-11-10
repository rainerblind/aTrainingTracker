# Stage 2: Requirement & Test Specification - ATT-6177: Remove Stale Travis CI Configuration

**Ticket**: [ATT-6177](https://rainerblind.atlassian.net/browse/ATT-6177)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6177`  
**Requirement Mapping**: `REQ-PRO-042` (*CI Configuration Integrity & Legacy Travis CI Deprecation*)  
**Test Spec ID**: `TST-PRO-042`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-PRO-042)

### 1.1 Problem Statement & Rationale
Retaining unmaintained legacy CI configuration (`.travis.yml` referencing JDK 8 and API 29) alongside obsolete build script comments (`app/build.gradle:35`) violates ASPICE SUP.8 (Configuration Management) and creates confusion regarding authoritative build/verification pipelines. GitHub Actions in `.github/workflows/` is the sole authoritative CI platform.

### 1.2 Functional & Architectural Requirements
* **REQ-PRO-042.1** (*Removal of Stale Travis Artifacts*): The obsolete `.travis.yml` configuration file SHALL be deleted from repository root, and legacy comments referencing Travis in `app/build.gradle` SHALL be removed.
* **REQ-PRO-042.2** (*Authoritative CI Invariant*): GitHub Actions workflows in `.github/workflows/` SHALL remain the sole authoritative CI/CD pipelines. No active build configuration or documentation SHALL reference Travis CI.
* **REQ-PRO-042.3** (*CI Configuration Integrity Contract*): Automated contract testing in `ReleaseCiAndLintConfigContractTest.kt` SHALL assert that `.travis.yml` does not exist and `app/build.gradle` contains no references to Travis.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (File Absence)**: *Given* repository root, *When* checked for `.travis.yml`, *Then* the file SHALL NOT exist.
* **Criterion 2 (Comment Hygiene)**: *Given* `app/build.gradle`, *When* scanned for `"travis"`, *Then* zero case-insensitive occurrences SHALL be found.
* **Criterion 3 (Contract Test)**: *Given* `ReleaseCiAndLintConfigContractTest.kt`, *When* executed, *Then* CI integrity assertions SHALL pass with 100% success.

---

## 2. Test Specification (TST-PRO-042)

### Test Case 1: Travis Configuration File Absence (`TST-PRO-042.1`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify `findRepoRootFile(".travis.yml", "../.travis.yml")` or equivalent file check confirms `.travis.yml` does not exist.

### Test Case 2: Build Gradle Comment Hygiene (`TST-PRO-042.2`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify `findBuildGradleFile().readText()` contains no occurrence of `"travis"` (case-insensitive).

### Test Case 3: GitHub Actions Authoritative Pipeline Continuity (`TST-PRO-042.3`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify existing tests validating `.github/workflows/android-release.yml` continue to pass without regression.

### Test Case 4: Targeted Unit Test Suite Execution (`TST-PRO-042.4`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-042.1` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-042.1` | Planned |
| `TST-PRO-042.2` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-042.1` | Planned |
| `TST-PRO-042.3` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-042.2` | Planned |
| `TST-PRO-042.4` | Regression | `trainingtracker.build.*` | `REQ-PRO-042.3` | Planned |
