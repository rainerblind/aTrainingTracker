# Stage 3: Implementation Plan - ATT-6730: Replace Kover placeholder minBound(1) with measured LINE and BRANCH coverage baseline thresholds

**Ticket**: [ATT-6730](https://rainerblind.atlassian.net/browse/ATT-6730)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-048` (`REQ-PRO-048.1`–`REQ-PRO-048.4`)  
**Test Mapping**: `TST-PRO-048` (`TST-PRO-048.1`–`TST-PRO-048.4`)  
**Branch**: `feature/ATT-6730`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

Sprint 2026-46.1 review of ATT-6171 identified three coverage governance gaps in `app/build.gradle`:
1. Hardcoded placeholder `minBound(1)` (1% line coverage) instead of objective measured baseline.
2. Missing explicit `CoverageUnit.BRANCH` gating bound.
3. Incomplete Android generated class exclusions (omitting `*.R`, `*.R$*`, and `*Manifest*`).

Authentic project coverage was measured across the test suite at **39.33% LINE** and **32.49% BRANCH**.

---

## 2. Traceability & Requirements Mapping

* **REQ-PRO-048.1**: Kover 0.9.11 plugin applied in `app/build.gradle` and root `build.gradle`.
* **REQ-PRO-048.2**: Report exclusions expanded (`*.R`, `*.R$*`, `*Manifest*`) and explicit verification bounds configured for `CoverageUnit.LINE` (`minBound(35)`) and `CoverageUnit.BRANCH` (`minBound(30)`).
* **REQ-PRO-048.3**: CI workflow gating `:app:koverXmlReportDebug` and `:app:koverVerifyDebug`.
* **REQ-PRO-048.4**: `ReleaseCiAndLintConfigContractTest.kt` asserting plugin, exclusions, and line/branch bounds.
* **Test Mapping**: `TST-PRO-048.1`..`TST-PRO-048.4`.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing 3,660+ unit tests continue to pass with 100% success rate.
2. **Deterministic CI Quality Gate**: Pull requests that drop line coverage below 35% or branch coverage below 30% deterministically fail CI.
3. **No Code Lock Breach**: Zero production code edits; build script and test contract modifications occur strictly in Stage 4 following Gate 3 approval.

---

## 4. Proposed Architectural Changes

### Component 1: `app/build.gradle`
* In `kover.reports.filters.excludes.classes`, append `"*.R"`, `"*.R$*"`, and `"*Manifest*"`.
* In `kover.reports.verify.rule`, configure:
  * `minBound(35)` for `CoverageUnit.LINE`
  * `bound { coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH; minValue = 30 }` for `CoverageUnit.BRANCH`.

### Component 2: `ReleaseCiAndLintConfigContractTest.kt`
* Extend `testBuildGradle_koverPluginAndConfiguration()` to assert:
  * Presence of `"*.R"` and `"*Manifest*"` in exclusion filters.
  * Presence of `"CoverageUnit.BRANCH"` and explicit `minBound` verification thresholds.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Kover Configuration in `app/build.gradle`
* Files: `app/build.gradle`
* Changes: Add `*.R`, `*.R$*`, `*Manifest*` exclusions; configure `minBound(35)` and `CoverageUnit.BRANCH` `minValue = 30`.

### Step 2: Update Governance Contract Test
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* Changes: Add assertions verifying `*.R`, `*Manifest*`, `CoverageUnit.BRANCH`, and `minBound`.

### Step 3: Targeted Unit Test Execution
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
* Expected Result: 100% pass rate.

### Step 4: Kover Verification Execution
* Command: `./gradlew :app:koverCachedVerifyDebug :app:koverVerifyDebug -x testDebugUnitTest`
* Expected Result: `BUILD SUCCESSFUL` against calibrated thresholds.

### Step 5: Living Documentation Parity Check
* Command: `python3 tools/verify_requirement_governance.py`
* Expected Result: PASS across all 9 domain shards with 100% parity.

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted suite `ReleaseCiAndLintConfigContractTest` and `:app:koverVerifyDebug`.
* **Rollback**: Branch `feature/ATT-6730` is cleanly isolated; git reset reverts changes cleanly without affecting `sprint/2026-46.2`.
