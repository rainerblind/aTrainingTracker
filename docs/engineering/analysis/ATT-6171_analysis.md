# Stage 1 Analysis: ATT-6171 - Add Kover Coverage Measurement with CI Threshold

**Ticket**: [ATT-6171](https://rainerblind.atlassian.net/browse/ATT-6171)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6171`  
**Requirement Mapping**: `REQ-PRO-048` | **Test Mapping**: `TST-PRO-048`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
ASPICE pre-assessment finding C-01 flagged that aTrainingTracker lacks code coverage measurement and threshold enforcement (SWE.4 BP verification criteria). Currently, unit tests run in CI via `./gradlew :app:testDebugUnitTest`, but neither line nor branch coverage is measured, reported, or gated. Introducing JetBrains Kotlinx Kover into Gradle and CI with an objective baseline threshold and artifact archiving establishes automated coverage verification.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * Build files lack coverage plugin dependencies and configuration (`build.gradle`, `app/build.gradle`).
  * Continuous integration (`.github/workflows/android-branch-build.yml`) executes unit tests, Android Lint, and Detekt, but lacks coverage reporting and ratcheting verification steps.
  * No contract test exists asserting coverage tool integration or threshold gating.
* **Independent Peer Review & Verification (ATT-3534)**:
  * *Build Script Audit*: Disproved assumption of Kotlin DSL (`.kts`) and version catalog (`libs.versions.toml`). Root `build.gradle` and `app/build.gradle` use Groovy DSL.
  * *CI File Audit*: Branch CI workflow is named `.github/workflows/android-branch-build.yml` (not `android-ci.yml`).
  * *Plugin Compatibility*: JetBrains Kover plugin (`org.jetbrains.kotlinx.kover:0.9.1`) is fully compatible with Gradle 9.6.0, AGP 9.4.0, and Kotlin 2.4.10.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  * Add `org.jetbrains.kotlinx:kover-gradle-plugin:0.9.1` to root `build.gradle` and apply `org.jetbrains.kotlinx.kover` in `app/build.gradle`.
  * Configure Kover report generation (HTML and XML) and verification rule (`koverVerify`) with baseline threshold for debug unit tests.
  * Update `.github/workflows/android-branch-build.yml` with coverage verification task and report artifact upload.
  * Author contract tests in `ReleaseCiAndLintConfigContractTest.kt` verifying Kover plugin, configuration, and CI workflow steps.
  * Define `REQ-PRO-048` in `req_process_stability.md` and `TST-PRO-048` in `tst_process_stability.md`.
* **Out-of-Scope Non-Goals**: Rewriting build scripts to Kotlin DSL (`.kts`), writing business logic unit tests to artificially inflate coverage, or altering `android-release.yml`.
* **Complexity Rating**: 2 SP (Standard). Strictly scoped to build configuration, CI workflow steps, and contract verification.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-048` (*Kover Code Coverage Measurement & CI Threshold Gating*), refining `REQ-PRO-034` under Epic `ATT-6142`. Targets `build.gradle`, `app/build.gradle`, `.github/workflows/android-branch-build.yml`, and `ReleaseCiAndLintConfigContractTest.kt`.
* **Historical Origin & Commit Trace**: Pre-assessment finding C-01 (2026-11-08) and ticket ATT-6171.
* **Root Reason for Existing Formulation**: Project historically relied on qualitative unit test pass/fail; coverage metrics were never automated.
* **Preservation of Core Invariants**: Existing test suites, Android Lint, Detekt, and release packaging remain intact.
* **Side-Effect Audit (Rule 68)**: Zero runtime code impact; zero APK footprint; coverage instrumentation applies strictly during debug unit test execution.

---

## 5. Architectural Strategy & High-Level Solution
* **Plugin Configuration**: Add Kover classpath to `build.gradle` and apply plugin in `app/build.gradle`.
* **Report & Verification DSL**: Configure `kover` extension in `app/build.gradle` with exclusions for generated classes (BuildConfig, Compose singletons) and baseline verification boundary.
* **CI Integration**: Add `./gradlew :app:koverXmlReportDebug :app:koverHtmlReportDebug :app:koverVerifyDebug` and `actions/upload-artifact@v4` in `android-branch-build.yml`.
* **Contract Verification**: Extend `ReleaseCiAndLintConfigContractTest.kt` with tests for Kover plugin presence, report tasks, and CI steps.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Target Release `V5.0.0`; Active Sprint `2026-46.1`; 100% pass on all existing unit tests and contract tests; zero APK runtime modification.
* **Risk Rating**: **LOW**. Standard build-time test verification and CI tooling integration.
