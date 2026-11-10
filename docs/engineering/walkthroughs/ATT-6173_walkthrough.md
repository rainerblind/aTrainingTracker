# Stage 5: Walkthrough & Verification - ATT-6173: Run Android Lint in CI and Reduce Lint Baseline

**Ticket**: [ATT-6173](https://rainerblind.atlassian.net/browse/ATT-6173)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6173`  
**Requirement Mapping**: `REQ-PRO-046` (`REQ-PRO-046.1`–`REQ-PRO-046.5`) | **Test Mapping**: `TST-PRO-046` (`TST-PRO-046.1`–`TST-PRO-046.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary & Verification Overview
Resolved ASPICE pre-assessment finding C-02 by gating static analysis in continuous integration, configuring build scripts for strict gating and reporting, burning down legacy baseline debt, and establishing automated contract enforcement:
* **CI Static Analysis Gating**: Updated `.github/workflows/android-branch-build.yml` with `./gradlew :app:lintDebug` and `actions/upload-artifact@v4` for HTML/XML reports (`REQ-PRO-046.1`, `REQ-PRO-046.2`).
* **Build Script Configuration**: Configured `app/build.gradle` `lint { ... }` with `abortOnError = true`, `htmlReport = true`, `xmlReport = true`, preserving justified suppressions (`REQ-PRO-046.3`).
* **Baseline Burn-down & Error Fixes**: Pruned 368 stale missing-file issues from `app/lint-baseline.xml`, burning down issues from 607 to 239 (60.6% reduction, exceeding the >= 10% bound <= 546). Fixed authentic code errors (9x `strings.xml` formatting, API 30 gating, intent hygiene, Compose state) (`REQ-PRO-046.4`).
* **Automated Contract Tests**: Expanded `ReleaseCiAndLintConfigContractTest.kt` covering CI workflow lint steps, report uploads, build settings, and baseline size bounds (`REQ-PRO-046.5`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-046.1`, `REQ-PRO-046.2` | `TST-PRO-046.1` | `ReleaseCiAndLintConfigContractTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-046.3` | `TST-PRO-046.2` | `ReleaseCiAndLintConfigContractTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-046.4` | `TST-PRO-046.3` | `ReleaseCiAndLintConfigContractTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-046.5` | `TST-PRO-046.4` | Targeted Suite & `:app:lintDebug` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: ~12m under parallel sprint lock)
* **Executed Scope**: `Tier 1 targeted: ReleaseCiAndLintConfigContractTest, TranslationParityTest, build.*, and :app:lintDebug`
* **Commands**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --max-workers=2 \
    --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest" \
    --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" \
    --tests "com.atrainingtracker.trainingtracker.build.*"
  python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:lintDebug
  ```
* **Results**:
  - `testDebugUnitTest`: 100% PASS across `ReleaseCiAndLintConfigContractTest` (6/6 tests), `TranslationParityTest` (10/10 tests), and all `build.*` contracts (0 failures, 0 regressions).
  - `:app:lintDebug`: `BUILD SUCCESSFUL` (0 errors, 946 warnings, 20 hints, 139 filtered by baseline).
* **Scope Note**: CI workflow, build script, baseline XML, and targeted bugfix verification; physical APK deployment skipped per non-UI / CI process ticket guidelines.

---

## 4. Key Implementation Highlights

```kotlin
// ReleaseCiAndLintConfigContractTest.kt
@Test
fun testBranchBuildWorkflow_runsLintAndUploadsReport() {
    val content = findCiWorkflowFile().readText()
    assertTrue(content.contains("./gradlew :app:lintDebug"))
    assertTrue(content.contains("actions/upload-artifact"))
    assertTrue(content.contains("app/build/reports/lint-results-debug.*"))
}

@Test
fun testLintBaseline_issueCountWithinBound() {
    val content = findLintBaselineFile().readText()
    val issueCount = Regex("<issue\\b").findAll(content).count()
    assertTrue(issueCount in 1..546) // Current: 239 issues (60.6% burn-down)
}
```

---

## 5. Invariants & Preserved Behavior
1. **Preserved Justifications**: Retained `checkReleaseBuilds = true`, `DuplicatePlatformClasses`, `MissingTranslation` (guarded by `TranslationParityTest`), and `Range` suppressions.
2. **Quality Gate Enforcement**: Unbaselined static errors deterministically break CI builds via `abortOnError = true`.
