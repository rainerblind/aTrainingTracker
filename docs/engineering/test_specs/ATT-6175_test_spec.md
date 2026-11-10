# Stage 2: Requirement & Test Specification - ATT-6175: Introduce Detekt with Baseline and Complexity Rules

**Ticket**: [ATT-6175](https://rainerblind.atlassian.net/browse/ATT-6175)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6175`  
**Requirement Mapping**: `REQ-PRO-047` (*Detekt Static Analysis & Complexity Governance*)  
**Test Spec ID**: `TST-PRO-047`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-PRO-047)

### 1.1 Problem Statement & Rationale
ASPICE pre-assessment finding C-03 flagged that the repository lacks Kotlin AST static analysis and complexity guardrails. Over 15 files exceed 1,200 LOC without regression tripwires. Introducing Detekt 1.23.8 with complexity bounds (`LargeClass` <= 800, `LongMethod` <= 60, `CyclomaticComplexMethod` <= 15), a legacy baseline (`app/detekt-baseline.xml`), and CI branch workflow gating enforces ASPICE SWE.3 static verification.

### 1.2 Functional & Technical Requirements
* **REQ-PRO-047.1** (*Plugin Integration*): Root `build.gradle` and `app/build.gradle` SHALL configure and apply `io.gitlab.arturbosch.detekt` (1.23.8) with Kotlin 2.4.10 / Gradle 9.6.0 compatibility.
* **REQ-PRO-047.2** (*Complexity Governance*): `config/detekt/detekt.yml` SHALL configure complexity rules (`LargeClass` threshold <= 800 LOC, `LongMethod` threshold <= 60 LOC, `CyclomaticComplexMethod` threshold <= 15).
* **REQ-PRO-047.3** (*Baseline Debt Isolation*): `app/detekt-baseline.xml` SHALL isolate legacy complexity debt, ensuring `./gradlew detekt` passes with 0 unbaselined errors.
* **REQ-PRO-047.4** (*CI Workflow Gating*): `.github/workflows/android-branch-build.yml` SHALL execute `./gradlew detekt` and upload HTML/XML reports via `actions/upload-artifact@v4`.
* **REQ-PRO-047.5** (*Contract Enforcement*): `ReleaseCiAndLintConfigContractTest.kt` SHALL assert Detekt plugin configuration, rule thresholds, baseline presence, and CI workflow steps.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Build Configuration)**: *Given* `app/build.gradle`, *When* inspected, *Then* it SHALL apply the Detekt plugin and configure `config/detekt/detekt.yml` and `app/detekt-baseline.xml`.
* **Criterion 2 (Complexity Rules)**: *Given* `config/detekt/detekt.yml`, *When* inspected, *Then* complexity rule thresholds SHALL be active.
* **Criterion 3 (CI Workflow)**: *Given* `.github/workflows/android-branch-build.yml`, *When* inspected, *Then* it SHALL contain `detekt` execution and report artifact upload.
* **Criterion 4 (Contract Verification)**: *Given* `ReleaseCiAndLintConfigContractTest.kt`, *When* executed, *Then* all Detekt contract tests pass with 100% success.

---

## 2. Test Specification (TST-PRO-047)

### Test Case 1: Build Script Detekt Plugin & Configuration Conformance (`TST-PRO-047.1`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify `app/build.gradle` applies detekt plugin and references `config/detekt/detekt.yml` and `app/detekt-baseline.xml`.

### Test Case 2: Detekt Rules Configuration Conformance (`TST-PRO-047.2`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify `config/detekt/detekt.yml` exists and defines `LargeClass`, `LongMethod`, and `CyclomaticComplexMethod`.

### Test Case 3: CI Workflow Detekt Step & Artifact Upload Conformance (`TST-PRO-047.3`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify `.github/workflows/android-branch-build.yml` contains `./gradlew detekt` and artifact upload for detekt reports.

### Test Case 4: Targeted Unit Test Suite & Detekt Clean-Room Execution (`TST-PRO-047.4`)
* **Scope**: Targeted Unit Test Suite & Detekt Task
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest" && ./gradlew detekt`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-047.1` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-047.1`, `REQ-PRO-047.3` | Planned |
| `TST-PRO-047.2` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-047.2` | Planned |
| `TST-PRO-047.3` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-047.4` | Planned |
| `TST-PRO-047.4` | Regression | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-047.5` | Planned |
