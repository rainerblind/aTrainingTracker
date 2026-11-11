# Stage 2: Requirement & Test Specification - ATT-6728: Ratchet lint-baseline contract bound to <=239 and restore Compose resource Lint rules to error

**Ticket**: [ATT-6728](https://rainerblind.atlassian.net/browse/ATT-6728)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-046` (*Android Lint CI Quality Gate & Baseline Burn-down*)  
**Test Spec ID**: `TST-PRO-046` (*Android Lint CI Quality Gate & Baseline Burn-down Contract Test Suite*)  
**Branch**: `feature/ATT-6728`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-046)

### 1.1 Problem Statement & Rationale
During Sprint 2026-46.1 review of ATT-6173, `app/lint-baseline.xml` was pruned from 607 down to 239 issues. However, `ReleaseCiAndLintConfigContractTest.testLintBaseline_issueCountWithinBound()` remained bounded to `<= 546`, allowing silent accumulation of up to +307 issues without failing the contract test, and failing if baseline issues ever drop to 0 (`1..546`). Furthermore, `app/build.gradle` downgraded `NonObservableLocale` and `LocalContextGetResourceValueCall` to `warning` as an interim accommodation during legacy Compose migration. Restoring these rules to `error` and ratcheting the baseline bound to `0..239` hardens CI quality gating under ASPICE SWE.3 / SWE.4.

### 1.2 Functional & Architectural Requirements
* **REQ-PRO-046.4** (*Lint Baseline Ratchet & Zero-Safe Bound*): `app/lint-baseline.xml` SHALL isolate legacy lint debt with an upper bound ceiling of `<= 239` issues (`issueCount in 0..239`), ensuring regressions are rejected while allowing burn-down to 0 issues without contract failure.
* **REQ-PRO-046.5** (*Automated CI & Lint Governance Contract*): `ReleaseCiAndLintConfigContractTest.kt` SHALL assert that CI workflows execute `lintDebug`, upload reports, maintain the ratcheted baseline ceiling (`issueCount in 0..239`), and do not downgrade Compose resource/locale lint rules.
* **REQ-PRO-046.6** (*Compose Resource & Locale Lint Enforcement*): `app/build.gradle` SHALL NOT downgrade `NonObservableLocale` and `LocalContextGetResourceValueCall` to warning. Jetpack Compose screens SHALL resolve strings via `stringResource(...)`, plurals via `pluralStringResource(...)`, and locale via `LocalConfiguration.current.locales[0]`. `SafePainterResource.kt` SHALL defensively guard `id <= 0` returning null without exception on negative sentinel IDs.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Ratcheted Baseline Ceiling)**:
  * *Given* `app/lint-baseline.xml` and `ReleaseCiAndLintConfigContractTest.testLintBaseline_issueCountWithinBound()`,
  * *When* the contract test evaluates `<issue` count,
  * *Then* it SHALL enforce `issueCount in 0..239`.
* **Criterion 2 (Compose Resource Lint Enforcement Restored)**:
  * *Given* `app/build.gradle`,
  * *When* inspected by static analysis and contract tests,
  * *Then* `NonObservableLocale` and `LocalContextGetResourceValueCall` SHALL NOT be downgraded to warning, and `./gradlew :app:lintDebug` SHALL pass with 0 errors.
* **Criterion 3 (SafePainterResource Sentinel Guard)**:
  * *Given* `SafePainterResource.kt`,
  * *When* evaluated with sentinel resource IDs (`id <= 0`),
  * *Then* it SHALL return null/fallback without triggering exceptions or `ResourceType` lint errors.

### 1.4 System Invariants
Zero modifications to public APIs; 100% test pass rate across existing unit and contract tests; no new user-facing strings required (all refactored call sites reference existing resources).

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PRO-046` (`REQ-PRO-046.4`, `REQ-PRO-046.5`, `REQ-PRO-046.6`), refining `REQ-BLD-002` and `REQ-PRO-034`. Targets `app/build.gradle`, `app/lint-baseline.xml`, and `ReleaseCiAndLintConfigContractTest.kt`.
2. *Historical Origin & Commit Trace*: Independent ASPICE pre-assessment finding C-02 (2026-11-08) and ticket ATT-6173 (commit `08a744c1`).
3. *Root Reason for Existing Formulation*: In ATT-6173, the ceiling was set to `546` (a 10% reduction from 607) as an interim milestone. The Compose rules were temporarily downgraded to `warning` to avoid blocking CI while legacy screens were refactored.
4. *Preservation of Core Invariants*: Existing suppressions (`DuplicatePlatformClasses`, `MissingTranslation` guarded by `TranslationParityTest`, and `Range`) remain intact. Ratcheting to `<= 239` and restoring Compose resource rules to `error` strengthens rather than weakens static analysis gating.

---

## 2. Test Specification (TST-PRO-046)

### Test Case 1: `testLintBaseline_issueCountWithinBound` (`TST-PRO-046.3`)
* **Scope**: Baseline Ceiling Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Action**: Parse `app/lint-baseline.xml`, count `<issue` elements, and assert `issueCount in 0..239`.
* **Expected Result**: Assert passes cleanly for current 239 issues and any lower count down to 0.

### Test Case 2: `testBuildGradle_composeLintRulesNotDowngraded` (`TST-PRO-046.5`)
* **Scope**: Build Configuration Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Action**: Read `app/build.gradle` and assert it does not downgrade `NonObservableLocale` or `LocalContextGetResourceValueCall` to warning.
* **Expected Result**: Assert passes when warnings are removed.

### Test Case 3: `testSafePainterResource_handlesSentinelIdsDefensively` (`TST-PRO-046.6`)
* **Scope**: Unit Test (`SafePainterResourceContractTest.kt`)
* **Action**: Invoke `safePainterResource` with `id = 0` and `id = -1`.
* **Expected Result**: Gracefully returns fallback painter without throwing `Resources.NotFoundException`.

### Test Case 4: Android Lint Clean Verification (`TST-PRO-046.7`)
* **Command**: `./gradlew :app:lintDebug`
* **Expected Result**: Execution succeeds with 0 lint errors (`abortOnError = true`).

### Test Case 5: Tier 1 Contract Suite Execution (`TST-PRO-046.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-046.3` | Contract | `ReleaseCiAndLintConfigContractTest.testLintBaseline_issueCountWithinBound` | `REQ-PRO-046.4`, `REQ-PRO-046.5` | Specified |
| `TST-PRO-046.5` | Contract | `ReleaseCiAndLintConfigContractTest.testBuildGradle_composeLintRulesNotDowngraded` | `REQ-PRO-046.5`, `REQ-PRO-046.6` | Specified |
| `TST-PRO-046.6` | Unit | `SafePainterResourceContractTest` | `REQ-PRO-046.6` | Specified |
| `TST-PRO-046.7` | Gradle Task | `./gradlew :app:lintDebug` | `REQ-PRO-046.4`, `REQ-PRO-046.6` | Specified |
| `TST-PRO-046.4` | Regression | `./gradlew testDebugUnitTest --tests "...ReleaseCiAndLintConfigContractTest"` | `REQ-PRO-046.5` | Specified |
