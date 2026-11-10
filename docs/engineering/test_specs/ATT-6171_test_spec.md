# Stage 2: Requirement & Test Specification - ATT-6171: Add Kover Coverage Measurement with CI Threshold

**Ticket**: [ATT-6171](https://rainerblind.atlassian.net/browse/ATT-6171)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6171`  
**Requirement Mapping**: `REQ-PRO-048` (*Kover Code Coverage Measurement & CI Threshold Gating*)  
**Test Spec ID**: `TST-PRO-048`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-PRO-048)

### 1.1 Problem Statement & Rationale
ASPICE pre-assessment finding C-01 flagged that aTrainingTracker lacks code coverage measurement and threshold enforcement (SWE.4 BP verification criteria). Currently, unit tests execute in CI via `./gradlew :app:testDebugUnitTest`, but neither line nor branch coverage is measured, reported, or gated. Introducing JetBrains Kotlinx Kover 0.9.1 into Gradle and CI with an objective baseline threshold and artifact archiving establishes automated coverage verification.

### 1.2 Functional & Technical Requirements
* **REQ-PRO-048.1** (*Plugin Integration*): Root `build.gradle` and `app/build.gradle` SHALL configure and apply `org.jetbrains.kotlinx.kover:0.9.1` with Kotlin 2.4.10 and Gradle 9.6.0 compatibility.
* **REQ-PRO-048.2** (*Report & Verification Configuration*): `app/build.gradle` SHALL configure the Kover DSL for debug unit tests with HTML and XML report generation, class exclusions for generated code (BuildConfig, Compose, test mocks), and a verification threshold rule (`koverVerifyDebug`) enforcing baseline coverage boundaries.
* **REQ-PRO-048.3** (*CI Workflow Gating*): Continuous integration workflow (`.github/workflows/android-branch-build.yml`) SHALL execute Kover report generation and verification tasks (`./gradlew :app:koverXmlReportDebug :app:koverHtmlReportDebug :app:koverVerifyDebug`), and upload coverage reports via `actions/upload-artifact@v4`.
* **REQ-PRO-048.4** (*Automated Governance Contract*): `ReleaseCiAndLintConfigContractTest.kt` SHALL assert Kover plugin application in build scripts, report/verify configuration, and CI workflow execution steps.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Build Configuration)**: *Given* `build.gradle` and `app/build.gradle`, *When* inspected, *Then* Kover plugin (0.9.1) SHALL be configured and applied with XML/HTML reporting and verification rules.
* **Criterion 2 (Coverage Reporting & Threshold)**: *Given* debug unit test execution, *When* running Kover tasks, *Then* line/branch coverage reports SHALL be produced and verified against the baseline boundary without build failure.
* **Criterion 3 (CI Workflow)**: *Given* `.github/workflows/android-branch-build.yml`, *When* inspected, *Then* it SHALL contain Kover report/verify steps and artifact upload for coverage reports.
* **Criterion 4 (Contract Verification)**: *Given* `ReleaseCiAndLintConfigContractTest.kt`, *When* executed, *Then* all Kover contract tests SHALL pass with 100% success.

---

## 2. Test Specification (TST-PRO-048)

### Test Case 1: Build Script Kover Plugin & Configuration Conformance (`TST-PRO-048.1`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify root `build.gradle` and `app/build.gradle` apply `org.jetbrains.kotlinx.kover` and configure report and verification blocks.

### Test Case 2: CI Workflow Kover Step & Artifact Upload Conformance (`TST-PRO-048.2`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify `.github/workflows/android-branch-build.yml` contains Kover reporting/verify task execution and artifact upload for coverage reports.

### Test Case 3: Kover Report Generation & Verification Execution (`TST-PRO-048.3`)
* **Scope**: Gradle Task Execution (`koverHtmlReportDebug`, `koverXmlReportDebug`, `koverVerifyDebug`)
* **Command**: `./gradlew :app:koverXmlReportDebug :app:koverHtmlReportDebug :app:koverVerifyDebug`
* **Expected Result**: XML and HTML reports generated under `app/build/reports/kover/` and verification succeeds.

### Test Case 4: Targeted Unit Test Suite & Contract Clean-Room Execution (`TST-PRO-048.4`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
* **Expected Result**: 100% pass rate across all contract assertions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-048.1` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-048.1`, `REQ-PRO-048.2` | Planned |
| `TST-PRO-048.2` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-048.3` | Planned |
| `TST-PRO-048.3` | Gradle Task | `app/build.gradle` / Kover tasks | `REQ-PRO-048.2`, `REQ-PRO-048.3` | Planned |
| `TST-PRO-048.4` | Regression | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-048.4` | Planned |
