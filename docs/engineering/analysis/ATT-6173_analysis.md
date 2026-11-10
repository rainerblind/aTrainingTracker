# Stage 1 Analysis: ATT-6173 - Run Android Lint in CI and Reduce Lint Baseline

**Ticket**: [ATT-6173](https://rainerblind.atlassian.net/browse/ATT-6173)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6173`  
**Requirement Mapping**: `REQ-PRO-046` | **Test Mapping**: `TST-PRO-046`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
ASPICE pre-assessment finding C-02 flagged that Android Lint is not executed in continuous integration (`.github/workflows/`), allowing static analysis regressions to enter branches unchecked. Furthermore, `app/lint-baseline.xml` accepts 607 issues, of which >60% (368 entries) reference deleted legacy files, obscuring real defects. Running `./gradlew :app:lintDebug` in CI and reducing baseline issues by >= 10% (<= 546) is required for ASPICE SWE.3 static verification compliance.

---

## 2. Root Cause Analysis & Independent Verification (4-Augen-Prinzip)
* **CI Workflow Audit**:
  * `.github/workflows/android-branch-build.yml` executes only `:app:testDebugUnitTest` and `:app:assembleDebug`, completely omitting `:app:lintDebug` and report archiving.
  * Adding `lintDebug` and `actions/upload-artifact@v4` for HTML/XML reports provides continuous static gating.
* **Baseline & Codebase Forensic Analysis**:
  * `app/lint-baseline.xml` contains 607 recorded issues; forensic inspection reveals 368 issues point to non-existent Java files from legacy refactorings.
  * Running `lintDebug` currently fails with 65 unbaselined errors, notably `StringFormatInvalid` on `% HRmax` in `strings.xml`, unhandled `@RequiresApi(30)` in `ProcessExitReasonHelper.kt`, and invalid `@get:VisibleForTesting` annotations.
  * Fixing authentic code errors and pruning stale baseline entries reduces the baseline to < 240 issues (> 60% reduction, exceeding the 10% requirement) and enables `lintDebug` to pass cleanly.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  * Update `.github/workflows/android-branch-build.yml` to run `./gradlew :app:lintDebug` and upload HTML/XML artifacts.
  * Configure `app/build.gradle` `lint { abortOnError = true, htmlReport = true, xmlReport = true }`.
  * Fix authentic code errors (e.g. `formatted="false"` for `% HRmax`, `@RequiresApi(30)`, annotation scopes).
  * Prune stale/fixed issues from `app/lint-baseline.xml` reducing issues by >= 10% (<= 546 issues).
  * Expand `ReleaseCiAndLintConfigContractTest.kt` to enforce CI lint execution, report uploads, and baseline size.
  * Define `REQ-PRO-046` and `TST-PRO-046` in living documentation.
* **Out-of-Scope Non-Goals**: Mass-refactoring valid legacy warnings (e.g. 479 `UnusedResources` across untouched layouts).
* **Complexity Rating**: 2 SP (Standard). Well bounded to CI configuration, baseline hygiene, and targeted error fixes.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-046` (*Android Lint CI Quality Gate & Baseline Burn-down*), refining `REQ-BLD-002` and `REQ-PRO-034` under Epic `ATT-6142`. Targets `.github/workflows/android-branch-build.yml`, `app/build.gradle`, `app/lint-baseline.xml`, and `ReleaseCiAndLintConfigContractTest.kt`.
* **Historical Origin & Commit Trace**: Pre-assessment finding C-02 (2026-11-08) and ticket ATT-6173.
* **Root Reason for Existing Formulation**: Baseline was captured during AGP 8 upgrade and never maintained.
* **Preservation of Core Invariants**: Existing lint suppressions (`DuplicatePlatformClasses`, `MissingTranslation`, `Range`) and `TranslationParityTest` in `app/build.gradle` remain strictly intact.

---

## 5. Architectural Strategy & High-Level Solution
* Add CI lint step and artifact upload in `android-branch-build.yml`.
* Fix authentic code errors in `strings.xml` and Kotlin/Java sources so `lintDebug` passes with 0 errors.
* Prune stale entries from `app/lint-baseline.xml` ensuring baseline issue count <= 546.
* Enforce rules via contract tests in `ReleaseCiAndLintConfigContractTest.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% unit test pass rate; zero regression in APK build time or runtime functionality.
* **Risk Rating**: **LOW**. CI pipeline hardening, baseline maintenance, and targeted code fixes.
