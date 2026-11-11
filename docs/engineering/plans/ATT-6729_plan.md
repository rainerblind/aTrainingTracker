# Stage 3: Implementation Plan - ATT-6729: Add Jetpack Compose exemptions to detekt.yml, ratchet detekt-baseline ceiling, and gate in Tier 2

**Ticket**: [ATT-6729](https://rainerblind.atlassian.net/browse/ATT-6729)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-047` (`REQ-PRO-047.1`–`REQ-PRO-047.5`)  
**Test Mapping**: `TST-PRO-047` (`TST-PRO-047.1`–`TST-PRO-047.4`)  
**Branch**: `feature/ATT-6729`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

Sprint 2026-46.1 review of ATT-6175 identified three static analysis governance gaps:
1. Missing Jetpack Compose exemptions in `config/detekt/detekt.yml`, generating 861 false positives across `@Composable` PascalCase naming, `@Preview` private functions, and Compose parameter lists.
2. Unratcheted `app/detekt-baseline.xml` (only checked for file length > 1000).
3. Missing local Tier 2 Detekt parity.

---

## 2. Traceability & Requirements Mapping

* **REQ-PRO-047.1**: Detekt 1.23.8 plugin applied in `build.gradle` and `app/build.gradle`.
* **REQ-PRO-047.2**: `config/detekt/detekt.yml` configured with official Compose exemptions (`FunctionNaming` with `ignoreAnnotated: ['Composable']`, `LongParameterList` with `ignoreAnnotated: ['Composable']`, `UnusedPrivateMember` with `ignoreAnnotated: ['Preview']`, and `MagicNumber` property/annotation exemptions) while preserving AST complexity thresholds (`LargeClass <= 800`, `LongMethod <= 60`, `CyclomaticComplexMethod <= 15`).
* **REQ-PRO-047.3**: `app/detekt-baseline.xml` regenerated with Compose exemptions (reducing `<ID>` entries from 10,379 to 9,518) and ratcheted with an upper-bound ceiling.
* **REQ-PRO-047.4**: CI and local Tier 2 workflows executing `./gradlew :app:detekt` and passing with 0 unbaselined issues.
* **REQ-PRO-047.5**: `ReleaseCiAndLintConfigContractTest.kt` asserting Compose exemptions and ratcheted baseline ceiling (`issueCount in 1..9550`).
* **Test Mapping**: `TST-PRO-047.1`..`TST-PRO-047.4`.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Runtime Regressions**: Build/static analysis configuration changes only; zero production Kotlin code changes.
2. **Preserved AST Complexity Gating**: `LargeClass`, `LongMethod`, and `CyclomaticComplexMethod` rules remain 100% active and unrelaxed.
3. **Monotonic Debt Reduction**: Stale or false-positive baseline entries are eliminated; ceiling assertion prevents baseline regression.

---

## 4. Proposed Architectural Changes

### Component 1: `config/detekt/detekt.yml`
* Add `naming` block with `FunctionNaming: active: true, ignoreAnnotated: ['Composable']`.
* Add `ignoreAnnotated: ['Composable']` to `complexity > LongParameterList`.
* Add `style` block with `UnusedPrivateMember: active: true, ignoreAnnotated: ['Preview']` and `MagicNumber: active: true, ignorePropertyDeclaration: true, ignoreCompanionObjectPropertyDeclaration: true, ignoreAnnotation: true`.

### Component 2: `app/detekt-baseline.xml`
* Regenerate baseline via `./gradlew :app:detektBaseline` to purge 861 false positives (yielding 9,518 entries).

### Component 3: `ReleaseCiAndLintConfigContractTest.kt`
* Extend `testDetektConfig_complexityRulesActive()` to verify Compose exemptions.
* Update `testDetektBaseline_existsAndIsPopulated()` to count `<ID>` entries and assert `issueCount in 1..9550`.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `config/detekt/detekt.yml`
* Files: `config/detekt/detekt.yml`
* Action: Add `naming` and `style` blocks with Compose annotation suppressors; update `LongParameterList`.

### Step 2: Regenerate Detekt Baseline
* Command: `./gradlew :app:detektBaseline`
* Action: Produce clean baseline without Compose false positives.

### Step 3: Update Governance Contract Test
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* Action: Add assertions verifying Compose exemptions and ratchet ceiling (`issueCount in 1..9550`).

### Step 4: Targeted Unit Test Execution
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
* Expected Result: 100% pass rate.

### Step 5: Detekt Execution Verification
* Command: `./gradlew :app:detekt`
* Expected Result: `BUILD SUCCESSFUL` with 0 unbaselined issues.

### Step 6: Requirement Governance Check
* Command: `python3 tools/verify_requirement_governance.py`
* Expected Result: PASS across all 9 domain shards with 100% parity.

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted suite `ReleaseCiAndLintConfigContractTest` and `./gradlew :app:detekt`.
* **Rollback**: Isolated git branch `feature/ATT-6729`; revert cleanly via `git checkout -- .`.
