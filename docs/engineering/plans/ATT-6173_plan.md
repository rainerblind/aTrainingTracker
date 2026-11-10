# Stage 3: Implementation Plan - ATT-6173: Run Android Lint in CI and Reduce Lint Baseline

**Ticket**: [ATT-6173](https://rainerblind.atlassian.net/browse/ATT-6173)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6173`  
**Requirement Mapping**: `REQ-PRO-046` (`REQ-PRO-046.1`..`REQ-PRO-046.5`)  
**Test Mapping**: `TST-PRO-046` (`TST-PRO-046.1`..`TST-PRO-046.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Android Lint has been omitted from CI branch builds (`.github/workflows/`), permitting static regressions to merge unchecked. Furthermore, `app/lint-baseline.xml` accepted 607 issues, of which 368 (>60%) point to deleted legacy files. This plan details the integration of `./gradlew :app:lintDebug` and artifact archiving in CI, lint build configuration, pruning stale baseline entries to <= 546 issues (>= 10% reduction), fixing authentic errors so `lintDebug` passes, and automated contract testing in `ReleaseCiAndLintConfigContractTest.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-046` (*Android Lint CI Quality Gate & Baseline Burn-down*)
  * `REQ-PRO-046.1`: CI workflow executes `./gradlew :app:lintDebug` on push and PR.
  * `REQ-PRO-046.2`: CI workflow uploads HTML/XML lint report artifacts via `actions/upload-artifact@v4`.
  * `REQ-PRO-046.3`: `app/build.gradle` configures `abortOnError = true`, `htmlReport = true`, `xmlReport = true`.
  * `REQ-PRO-046.4`: `app/lint-baseline.xml` pruned to <= 546 issues (>= 10% reduction from 607).
  * `REQ-PRO-046.5`: Contract verification in `ReleaseCiAndLintConfigContractTest.kt`.
* **Test Mapping**: `TST-PRO-046` (`TST-PRO-046.1`..`TST-PRO-046.4`)
  * `TST-PRO-046.1`: CI workflow lint step & artifact upload contract assertion.
  * `TST-PRO-046.2`: Build script lint block options assertion.
  * `TST-PRO-046.3`: Lint baseline count upper bound parsing assertion (<= 546).
  * `TST-PRO-046.4`: Targeted unit test execution (`ReleaseCiAndLintConfigContractTest`).

---

## 3. System Invariants & Preserved Behavior
1. **Preserve Justified Suppressions**: Maintain `DuplicatePlatformClasses`, `MissingTranslation` (guarded by `TranslationParityTest`), and `Range` suppressions in `app/build.gradle`.
2. **Deterministic CI Gating**: CI must fail if new unbaselined lint errors are introduced (`abortOnError = true`).
3. **No Production Code Regressions**: 100% unit test pass rate; zero runtime behavior modifications.

---

## 4. Proposed Architectural Changes
* **CI Workflow (`.github/workflows/android-branch-build.yml`)**:
  * Add step `Run Android Lint` (`./gradlew :app:lintDebug`) before debug assembly.
  * Add step `Upload Lint Report` (`actions/upload-artifact@v4`, `if: always()`) uploading `app/build/reports/lint-results-debug.*`.
* **Build Script (`app/build.gradle`)**:
  * Explicitly set `abortOnError = true`, `htmlReport = true`, `xmlReport = true` inside `lint { ... }`.
* **Baseline Hygiene & Error Elimination**:
  * Fix authentic errors in code (`zone_calc_method_max_hr` `formatted="false"` across all 9 `strings.xml`, `@RequiresApi(30)` in `ProcessExitReasonHelper.kt`, `@get:VisibleForTesting`).
  * Prune stale issues from `app/lint-baseline.xml` pointing to deleted files, reducing issue count to <= 546.
* **Contract Test Expansion (`ReleaseCiAndLintConfigContractTest.kt`)**:
  * Add `@Test fun testBranchBuildWorkflow_runsLintAndUploadsReport()` (`TST-PRO-046.1`).
  * Add `@Test fun testBuildGradle_lintReportingAndAbortOnError()` (`TST-PRO-046.2`).
  * Add `@Test fun testLintBaseline_issueCountWithinBound()` (`TST-PRO-046.3`).
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Fix Authentic Code Errors)**: Add `formatted="false"` to `zone_calc_method_max_hr` in `strings.xml` (all 9 locales); annotate API 30 calls in `ProcessExitReasonHelper.kt`; fix annotation targets (`REQ-PRO-046.4`).
2. **Step 2 (Prune Stale Lint Baseline)**: Prune entries referencing deleted classes/fixed lines in `app/lint-baseline.xml`, ensuring issue count <= 546 (`REQ-PRO-046.4`, `TST-PRO-046.3`).
3. **Step 3 (Configure `app/build.gradle`)**: Set `abortOnError = true`, `htmlReport = true`, `xmlReport = true` (`REQ-PRO-046.3`, `TST-PRO-046.2`).
4. **Step 4 (Update CI Workflow)**: Add `Run Android Lint` and report artifact upload in `android-branch-build.yml` (`REQ-PRO-046.1`, `REQ-PRO-046.2`, `TST-PRO-046.1`).
5. **Step 5 (Expand Contract Test Suite)**: Add test methods in `ReleaseCiAndLintConfigContractTest.kt` covering `TST-PRO-046.1`..`TST-PRO-046.3` (`REQ-PRO-046.5`).
6. **Step 6 (Targeted Verification)**: Run `ReleaseCiAndLintConfigContractTest` and `./gradlew :app:lintDebug` to verify 100% pass (`TST-PRO-046.4`).

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` and `./gradlew :app:lintDebug`.
* **Rollback Plan**: Changes isolated to `feature/ATT-6173`; revert via `git checkout -- .` and `git clean -fd`.
