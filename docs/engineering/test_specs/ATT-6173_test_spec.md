# Stage 2: Requirement & Test Specification - ATT-6173: Run Android Lint in CI and Reduce Lint Baseline

**Ticket**: [ATT-6173](https://rainerblind.atlassian.net/browse/ATT-6173)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6173`  
**Requirement Mapping**: `REQ-PRO-046` (*Android Lint CI Quality Gate & Baseline Burn-down*)  
**Test Spec ID**: `TST-PRO-046`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-PRO-046)

### 1.1 Problem Statement & Rationale
ASPICE pre-assessment finding C-02 flagged that Android Lint is not executed in continuous integration (`.github/workflows/`), allowing static analysis regressions to enter branches unchecked. Furthermore, `app/lint-baseline.xml` records 607 issues, of which >60% (368 entries) reference deleted legacy files, obscuring authentic defects. Running `./gradlew :app:lintDebug` in CI, uploading report artifacts, and burning down the baseline by >= 10% (<= 546 issues) enforces ASPICE SWE.3 static verification compliance.

### 1.2 Functional & Architectural Requirements
* **REQ-PRO-046.1** (*CI Lint Gating*): CI workflow (`.github/workflows/android-branch-build.yml`) SHALL execute `./gradlew :app:lintDebug` on every push and PR to gate static analysis regressions.
* **REQ-PRO-046.2** (*Artifact Archiving*): CI workflow SHALL upload HTML and XML lint report artifacts (`app/build/reports/lint-results-debug.*`) via `actions/upload-artifact`.
* **REQ-PRO-046.3** (*Build Configuration*): `app/build.gradle` `lint { ... }` SHALL specify `abortOnError = true`, `htmlReport = true`, `xmlReport = true`, while maintaining baseline file linkage and justified suppressions.
* **REQ-PRO-046.4** (*Baseline Burn-down*): `app/lint-baseline.xml` SHALL be pruned of stale references and authentic fixes, bounding total baseline issues to <= 546 (>= 10% reduction from 607).
* **REQ-PRO-046.5** (*Contract Verification*): `ReleaseCiAndLintConfigContractTest.kt` SHALL assert CI lint execution, report archiving, and baseline boundary enforcement.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (CI Workflow)**: *Given* `.github/workflows/android-branch-build.yml`, *When* inspected, *Then* it SHALL contain a step executing `lintDebug` and an artifact upload step for lint reports.
* **Criterion 2 (Build Script)**: *Given* `app/build.gradle`, *When* inspected, *Then* the `lint` block SHALL specify `abortOnError = true`, `htmlReport = true`, and `xmlReport = true`.
* **Criterion 3 (Baseline Bound)**: *Given* `app/lint-baseline.xml`, *When* counting `<issue` elements, *Then* the total count SHALL be <= 546.
* **Criterion 4 (Contract Test)**: *Given* `ReleaseCiAndLintConfigContractTest.kt`, *When* executed, *Then* all assertions pass with 100% success.

---

## 2. Test Specification (TST-PRO-046)

### Test Case 1: CI Workflow Lint Step & Artifact Upload Conformance (`TST-PRO-046.1`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify `.github/workflows/android-branch-build.yml` contains `lintDebug` and `actions/upload-artifact` uploading `app/build/reports/lint-results-debug.*`.

### Test Case 2: Build Configuration Lint Settings Conformance (`TST-PRO-046.2`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify `app/build.gradle` contains `abortOnError = true`, `htmlReport = true`, and `xmlReport = true`.

### Test Case 3: Lint Baseline Count Upper Bound Audit (`TST-PRO-046.3`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Parse `app/lint-baseline.xml` and assert issue count <= 546.

### Test Case 4: Targeted Unit Test Suite Execution (`TST-PRO-046.4`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-046.1` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-046.1`, `REQ-PRO-046.2` | Planned |
| `TST-PRO-046.2` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-046.3` | Planned |
| `TST-PRO-046.3` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-046.4` | Planned |
| `TST-PRO-046.4` | Regression | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-046.5` | Planned |
