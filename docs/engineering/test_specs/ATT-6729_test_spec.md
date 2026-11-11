# Stage 2: Requirement & Test Specification - ATT-6729: Add Jetpack Compose exemptions to detekt.yml, ratchet detekt-baseline ceiling, and gate in Tier 2

**Ticket**: [ATT-6729](https://rainerblind.atlassian.net/browse/ATT-6729)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-047` (*Detekt Static Analysis & Complexity Governance*)  
**Test Spec ID**: `TST-PRO-047` (*Detekt Static Analysis & Complexity Governance Contract Test Suite*)  
**Branch**: `feature/ATT-6729`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-047)

### 1.1 Problem Statement & Rationale
During Sprint 2026-46.1 review of ATT-6175, Detekt 1.23.8 was configured without official Jetpack Compose exemptions, flagging standard PascalCase `@Composable` functions as `FunctionNaming` violations (669 hits) and `@Preview` fixtures as `UnusedPrivateMember` (182 hits). Additionally, `app/detekt-baseline.xml` was unratcheted (lacking a ceiling bound in contract tests) and local Tier 2 gating lacked Detekt parity.

### 1.2 Functional & Architectural Requirements
* **REQ-PRO-047.1** (*Plugin Integration*): Root `build.gradle` and `app/build.gradle` SHALL configure `io.gitlab.arturbosch.detekt:1.23.8`.
* **REQ-PRO-047.2** (*Compose Exemptions & Complexity Rules*): `config/detekt/detekt.yml` SHALL configure Compose exemptions (`FunctionNaming` with `ignoreAnnotated: ['Composable']`, `LongParameterList` with `ignoreAnnotated: ['Composable']`, `UnusedPrivateMember` with `ignoreAnnotated: ['Preview']`, and `MagicNumber` exemptions) while preserving AST complexity thresholds (`LargeClass <= 800`, `LongMethod <= 60`, `CyclomaticComplexMethod <= 15`).
* **REQ-PRO-047.3** (*Ratcheted Baseline Ceiling*): `app/detekt-baseline.xml` SHALL isolate legacy debt with an upper bound ceiling of `<= 9550` `<ID>` entries.
* **REQ-PRO-047.4** (*CI Workflow Gating*): CI workflow SHALL execute `./gradlew :app:detekt` and upload reports.
* **REQ-PRO-047.5** (*Automated Contract & Tier 2 Parity*): `ReleaseCiAndLintConfigContractTest.kt` SHALL assert Compose exemptions and ratcheted baseline ceiling (`issueCount in 1..9550`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Compose Exemptions Active)**:
  * *Given* `config/detekt/detekt.yml`,
  * *When* Detekt executes on Compose screens and previews,
  * *Then* `@Composable` and `@Preview` functions SHALL trigger 0 `FunctionNaming` and 0 `UnusedPrivateMember` violations.
* **Criterion 2 (Baseline Ceiling Ratchet)**:
  * *Given* `app/detekt-baseline.xml`,
  * *When* parsed by `ReleaseCiAndLintConfigContractTest.kt`,
  * *Then* the total `<ID>` issue count SHALL be strictly `<= 9550`.
* **Criterion 3 (Execution Parity)**:
  * *Given* the codebase,
  * *When* executing `./gradlew :app:detekt`,
  * *Then* it SHALL pass with 0 unbaselined issues.

### 1.4 System Invariants
Zero runtime code changes; zero build failure on verified test suite; AST complexity limits remain active.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PRO-047` (`REQ-PRO-047.1`–`REQ-PRO-047.5`), refining `REQ-PRO-034`. Targets `build.gradle`, `app/build.gradle`, `config/detekt/detekt.yml`, `app/detekt-baseline.xml`, `.github/workflows/android-branch-build.yml`, and `ReleaseCiAndLintConfigContractTest.kt`.
2. *Historical Origin & Commit Trace*: Independent ASPICE pre-assessment finding C-03 (2026-11-08), ticket ATT-6175 (`V5.0.0`, Sprint 2026-46.1), and refined in ATT-6729.
3. *Root Reason for Existing Formulation*: ATT-6175 bootstrapped Detekt with default rules to establish initial CI pipeline execution, deferring domain-specific Jetpack Compose annotation suppressors and baseline ceiling limits.
4. *Preservation of Core Invariants*: AST complexity limits (`LargeClass <= 800`, `LongMethod <= 60`, `CyclomaticComplexMethod <= 15`) remain active and enforced; non-Compose production code remains strictly governed by standard naming and member usage rules.

---

## 2. Test Specification (TST-PRO-047)

### Test Case 1: `testDetektConfig_composeExemptionsAndRules` (`TST-PRO-047.1`)
* **Scope**: Contract Unit Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Preconditions**: `config/detekt/detekt.yml` configured with Compose exemptions.
* **Action**: Read `config/detekt/detekt.yml` and assert presence of `ignoreAnnotated: ['Composable']` and `ignoreAnnotated: ['Preview']`.
* **Expected Result**: All assertions pass.

### Test Case 2: `testDetektBaseline_existsAndIsRatcheted` (`TST-PRO-047.2`)
* **Scope**: Baseline Ceiling Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Action**: Parse `app/detekt-baseline.xml`, count `<ID>` elements, and assert `issueCount in 1..9550`.
* **Expected Result**: Total count <= 9550.

### Test Case 3: Detekt Execution Verification (`TST-PRO-047.3`)
* **Command**: `./gradlew :app:detekt`
* **Expected Result**: Build succeeds with 0 unbaselined issues.

### Test Case 4: Tier 1 Targeted Test Suite Execution (`TST-PRO-047.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-047.1` | Contract | `ReleaseCiAndLintConfigContractTest.testDetektConfiguration_complexityRulesAndExemptions` | `REQ-PRO-047.2` | Specified |
| `TST-PRO-047.2` | Contract | `ReleaseCiAndLintConfigContractTest.testDetektBaseline_existsAndIsRatcheted` | `REQ-PRO-047.3`, `REQ-PRO-047.5` | Specified |
| `TST-PRO-047.3` | Gradle Task | `./gradlew :app:detekt` | `REQ-PRO-047.3`, `REQ-PRO-047.4` | Specified |
| `TST-PRO-047.4` | Regression | `./gradlew testDebugUnitTest --tests "...ReleaseCiAndLintConfigContractTest"` | `REQ-PRO-047.5` | Specified |
