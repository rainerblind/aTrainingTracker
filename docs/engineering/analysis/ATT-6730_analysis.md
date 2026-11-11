# Stage 1 Analysis: ATT-6730 - Replace Kover placeholder minBound(1) with measured LINE and BRANCH coverage baseline thresholds

**Ticket**: [ATT-6730](https://rainerblind.atlassian.net/browse/ATT-6730)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6730`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-46.1 review of ATT-6171 (*Add Kover coverage measurement with CI threshold*), three gaps were identified:
1. **Placeholder 1% Threshold**: `app/build.gradle` hardcodes `minBound(1)` (1% coverage), permitting code coverage to degrade by up to ~38 percentage points without failing CI, undermining ASPICE SWE.4 verification gating.
2. **Missing Explicit Branch Coverage**: Kover's default `minBound(N)` only checks `CoverageUnit.LINE`. No `CoverageUnit.BRANCH` threshold was configured despite requirements mandating line and branch gating.
3. **Incomplete Android Generated Class Exclusions**: Filter exclusions only omitted `*BuildConfig*` and `*ComposableSingletons*`, omitting standard Android generated classes (`*.R`, `*.R$*`, `*Manifest*`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * Independently verified `app/build.gradle` lines 154–170: current rule only enforces `minBound(1)` without branch threshold or complete Android generated exclusions.
  * Measured authentic project-wide code coverage on `sprint/2026-46.2` across 5,395 unit tests via Kover binary instrumentation (`testDebugUnitTest.ic` & `reportDebug.xml`):
    * **LINE Coverage**: 39,023 covered / 99,217 total lines = **39.33%**
    * **BRANCH Coverage**: 17,795 covered / 54,765 total branches = **32.49%**
    * **INSTRUCTION Coverage**: 240,839 covered / 865,648 total = **27.82%**
  * Evaluated generated class exclusions (`*BuildConfig*`, `*ComposableSingletons*`, `*.R`, `*.R$*`, `*Manifest*`): eliminates artificial denominator inflation.
  * Established ratcheting bounds:
    * LINE threshold: `minBound(35)` (or `bound { coverageUnits = CoverageUnit.LINE; minValue = 35 }`)
    * BRANCH threshold: `minBound(30)` (or `bound { coverageUnits = CoverageUnit.BRANCH; minValue = 30 }`)
    * Both thresholds strictly exceed 1%, protect against regression, and pass cleanly on the active sprint baseline.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Update `app/build.gradle` Kover report filter exclusions to include `*.R`, `*.R$*`, and `*Manifest*`.
  * Configure explicit `CoverageUnit.LINE` (`minBound(35)`) and `CoverageUnit.BRANCH` (`minBound(30)`) thresholds in `app/build.gradle`.
  * Update `ReleaseCiAndLintConfigContractTest.kt` to verify line and branch bounds and expanded exclusions.
  * Verify `./gradlew :app:koverVerifyDebug` passes cleanly.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification of production business logic or UI code to artificially alter coverage.
  * No changes to Detekt or Android Lint baselines.
  * No modification of CI workflow step definitions or artifact destinations.

---

### 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-048` (`REQ-PRO-048.2`), refining `REQ-PRO-034`. Targets `app/build.gradle` and `ReleaseCiAndLintConfigContractTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in ATT-6171 (`V5.0.0`, Sprint 2026-46.1) to resolve ASPICE pre-assessment finding C-01.
* **Root Reason for Existing Formulation**: ATT-6171 validated the Kover Gradle task graph via `--dry-run` to avoid full test execution overhead during initial plugin bootstrap, leaving `minBound(1)` as an uncalibrated placeholder.
* **Preservation of Core Invariants**: Calibrating bounds to authentic baseline metrics (35% LINE, 30% BRANCH) activates real SWE.4 gating while guaranteeing zero build disruption on the verified sprint codebase.

---

## 5. Architectural Strategy & High-Level Solution

1. **`app/build.gradle`**:
   * Expand `kover.reports.filters.excludes.classes` with `"*.R"`, `"*.R$*"`, and `"*Manifest*"`.
   * Configure verification rule with explicit `CoverageUnit.LINE` (`minValue = 35`) and `CoverageUnit.BRANCH` (`minValue = 30`).
2. **`ReleaseCiAndLintConfigContractTest.kt`**:
   * Extend `testBuildGradle_koverPluginAndConfiguration()` to assert presence of `*.R`, `*Manifest*`, `CoverageUnit.LINE`, and `CoverageUnit.BRANCH` in the configuration.
3. **Verification**:
   * Run targeted test `ReleaseCiAndLintConfigContractTest`.
   * Run `./gradlew :app:koverVerifyDebug` confirming threshold compliance.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing unit test suite pass rate.
  2. `:app:koverVerifyDebug` task passes cleanly on verified sprint codebase.
  3. Continuous downward ratchet: code coverage cannot degrade below baseline.
* **Risk Rating**: **LOW** (Build script configuration and contract test verification only; zero production code modified).
