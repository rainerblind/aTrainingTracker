# Stage 2: Requirement & Test Specification - ATT-6730: Replace Kover placeholder minBound(1) with measured LINE and BRANCH coverage baseline thresholds

**Ticket**: [ATT-6730](https://rainerblind.atlassian.net/browse/ATT-6730)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-048` (*Kover Code Coverage Measurement & CI Threshold Gating*)  
**Test Spec ID**: `TST-PRO-048` (*Kover Code Coverage Measurement & CI Threshold Gating Contract Test Suite*)  
**Branch**: `feature/ATT-6730`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-048)

### 1.1 Problem Statement & Rationale
During Sprint 2026-46.1 review of ATT-6171, `app/build.gradle` retained an uncalibrated placeholder `minBound(1)` (1% coverage), lacked explicit `CoverageUnit.BRANCH` gating, and omitted generated Android class exclusions (`*.R`, `*.R$*`, `*Manifest*`), permitting severe coverage degradation without failing CI.

### 1.2 Functional & Architectural Requirements
* **REQ-PRO-048.1** (*Plugin Integration*): Root `build.gradle` and `app/build.gradle` SHALL configure `org.jetbrains.kotlinx.kover:0.9.11`.
* **REQ-PRO-048.2** (*Report & Verification Configuration*): `app/build.gradle` SHALL configure report exclusions for `*BuildConfig*`, `*ComposableSingletons*`, `*.R`, `*.R$*`, and `*Manifest*`, and enforce dual verification bounds for `CoverageUnit.LINE` (`minBound(35)`) and `CoverageUnit.BRANCH` (`minBound(30)`).
* **REQ-PRO-048.3** (*CI Workflow Gating*): CI workflow SHALL execute `:app:koverXmlReportDebug`, `:app:koverHtmlReportDebug`, and `:app:koverVerifyDebug`, archiving reports.
* **REQ-PRO-048.4** (*Automated Governance Contract*): `ReleaseCiAndLintConfigContractTest.kt` SHALL assert Kover plugin application, generated class exclusions, and explicit line/branch verification thresholds.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Generated Class Exclusions & Baseline Measurement)**:
  * *Given* `app/build.gradle` with exclusions for `*BuildConfig*`, `*ComposableSingletons*`, `*.R`, `*.R$*`, and `*Manifest*`,
  * *When* Kover reports execute on the verified test suite,
  * *Then* measured baseline coverage (LINE 39.33%, BRANCH 32.49%) SHALL be recorded and documented.
* **Criterion 2 (Enforce Line & Branch Ratcheting Bounds)**:
  * *Given* measured baseline metrics,
  * *When* `kover.reports.verify` executes via `./gradlew :app:koverVerifyDebug`,
  * *Then* thresholds for `CoverageUnit.LINE` ($\ge 35$) and `CoverageUnit.BRANCH` ($\ge 30$) SHALL pass without failure.
* **Criterion 3 (Contract Test Validation)**:
  * *Given* `ReleaseCiAndLintConfigContractTest.kt`,
  * *When* executed,
  * *Then* assertions verifying Kover plugin, generated exclusions, and line/branch bounds SHALL pass.

### 1.4 System Invariants
Zero runtime code changes; zero build failure on verified test suite; coverage bounds strictly exceed 1%.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PRO-048` (`REQ-PRO-048.1`–`REQ-PRO-048.4`), refining `REQ-PRO-034`. Targets `build.gradle`, `app/build.gradle`, `.github/workflows/android-branch-build.yml`, and `ReleaseCiAndLintConfigContractTest.kt`.
2. *Historical Origin & Commit Trace*: Independent ASPICE pre-assessment finding C-01 (2026-11-08), ticket ATT-6171 (`V5.0.0`, Sprint 2026-46.1), and refined in ATT-6730.
3. *Root Reason for Existing Formulation*: ATT-6171 validated the Kover Gradle task graph via `--dry-run` to avoid full test execution overhead during initial plugin bootstrap, leaving `minBound(1)` as an uncalibrated placeholder without branch threshold or complete Android generated exclusions.
4. *Preservation of Core Invariants*: Calibrating bounds to authentic baseline metrics (35% LINE, 30% BRANCH) activates real SWE.4 gating while guaranteeing zero build disruption on the verified sprint codebase.

---

## 2. Test Specification (TST-PRO-048)

### Test Case 1: `testBuildGradle_koverPluginAndConfiguration` (`TST-PRO-048.1`)
* **Scope**: Contract Unit Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Preconditions**: `app/build.gradle` configured with Kover 0.9.11, class exclusions, and line/branch bounds.
* **Action**: Read `app/build.gradle` and assert presence of `org.jetbrains.kotlinx.kover`, `*.R`, `*Manifest*`, `CoverageUnit.LINE`, and `CoverageUnit.BRANCH`.
* **Expected Result**: All assertions pass.

### Test Case 2: `testBranchBuildWorkflow_runsKoverAndUploadsReport` (`TST-PRO-048.2`)
* **Scope**: CI Workflow Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Action**: Assert CI workflow contains `:app:koverXmlReportDebug`, `:app:koverVerifyDebug`, and artifact upload.
* **Expected Result**: All assertions pass.

### Test Case 3: Kover Verification Execution (`TST-PRO-048.3`)
* **Command**: `./gradlew :app:koverVerifyDebug`
* **Expected Result**: Task executes and succeeds against LINE >= 35% and BRANCH >= 30% bounds.

### Test Case 4: Tier 1 Targeted Test Suite Execution (`TST-PRO-048.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-048.1` | Contract | `ReleaseCiAndLintConfigContractTest.testBuildGradle_koverPluginAndConfiguration` | `REQ-PRO-048.1`, `REQ-PRO-048.2` | Specified |
| `TST-PRO-048.2` | CI Contract | `ReleaseCiAndLintConfigContractTest.testBranchBuildWorkflow_runsKoverAndUploadsReport` | `REQ-PRO-048.3`, `REQ-PRO-048.4` | Specified |
| `TST-PRO-048.3` | Gradle Task | `./gradlew :app:koverVerifyDebug` | `REQ-PRO-048.2`, `REQ-PRO-048.3` | Specified |
| `TST-PRO-048.4` | Regression | `./gradlew testDebugUnitTest --tests "...ReleaseCiAndLintConfigContractTest"` | `REQ-PRO-048.4` | Specified |
