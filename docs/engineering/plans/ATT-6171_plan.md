# Stage 3: Implementation Plan - ATT-6171: Add Kover Coverage Measurement with CI Threshold

**Ticket**: [ATT-6171](https://rainerblind.atlassian.net/browse/ATT-6171)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6171`  
**Requirement Mapping**: `REQ-PRO-048` (`REQ-PRO-048.1`..`REQ-PRO-048.4`)  
**Test Mapping**: `TST-PRO-048` (`TST-PRO-048.1`..`TST-PRO-048.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
ASPICE pre-assessment finding C-01 highlighted that aTrainingTracker lacks code coverage measurement and threshold enforcement (SWE.4 BP verification criteria). Currently, unit tests run without automated line/branch coverage reporting or gating. This plan establishes JetBrains Kotlinx Kover 0.9.1 integration in `build.gradle` and `app/build.gradle`, configures HTML/XML reports and baseline threshold gating (`koverVerifyDebug`), integrates verification and artifact archiving in `.github/workflows/android-branch-build.yml`, and verifies governance through `ReleaseCiAndLintConfigContractTest.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-048` (*Kover Code Coverage Measurement & CI Threshold Gating*)
  * `REQ-PRO-048.1`: Add Kover 0.9.1 classpath to `build.gradle` and apply plugin in `app/build.gradle`.
  * `REQ-PRO-048.2`: Configure Kover DSL in `app/build.gradle` with exclusions, XML/HTML reports, and baseline threshold rule (`koverVerifyDebug`).
  * `REQ-PRO-048.3`: CI workflow executes Kover report/verify tasks and uploads coverage report artifacts.
  * `REQ-PRO-048.4`: Automated contract verification in `ReleaseCiAndLintConfigContractTest.kt`.
* **Test Mapping**: `TST-PRO-048` (`TST-PRO-048.1`..`TST-PRO-048.4`)
  * `TST-PRO-048.1`: Kover plugin and build script configuration contract assertion.
  * `TST-PRO-048.2`: CI workflow Kover step and report artifact upload contract assertion.
  * `TST-PRO-048.3`: Kover task execution generating reports and passing threshold verification.
  * `TST-PRO-048.4`: Clean-room execution of targeted contract tests and Kover tasks.

---

## 3. System Invariants & Preserved Behavior
1. **Zero Impact on Production APK**: Coverage instrumentation applies only during debug test execution; zero runtime bytecode or APK footprint.
2. **Quality Gate Tripwire**: Decreasing line/branch coverage below the ratcheting baseline fails `./gradlew :app:koverVerifyDebug`.
3. **Continuous ASPICE Conformance**: Existing unit tests, Android Lint, Detekt, and release packaging workflows remain intact.

---

## 4. Proposed Architectural Changes
* **Build Files (`build.gradle`, `app/build.gradle`)**:
  * Root `build.gradle`: Add `'org.jetbrains.kotlinx:kover-gradle-plugin:0.9.1'` to `buildscript.dependencies`.
  * `app/build.gradle`: Apply `'org.jetbrains.kotlinx.kover'`, configure `kover { ... }` block (reports for debug variant, XML/HTML outputs, filters excluding generated classes, and `verify.rule.minBound` threshold).
* **CI Workflow (`.github/workflows/android-branch-build.yml`)**:
  * Add `Run Kover Coverage` (`./gradlew :app:koverXmlReportDebug :app:koverHtmlReportDebug :app:koverVerifyDebug`) and `Upload Coverage Report` (`actions/upload-artifact@v4`, `if: always()`, path `app/build/reports/kover/`).
* **Contract Test Expansion (`ReleaseCiAndLintConfigContractTest.kt`)**:
  * Add test cases asserting Kover plugin application, configuration path, and CI workflow steps.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Gradle Kover Plugin Integration)**: Add Kover plugin to root `build.gradle` and apply in `app/build.gradle` (`REQ-PRO-048.1`, `TST-PRO-048.1`).
2. **Step 2 (Configure Kover Reports & Verification Threshold)**: Add `kover { ... }` block in `app/build.gradle` with exclusions and `minBound` rule (`REQ-PRO-048.2`, `TST-PRO-048.1`, `TST-PRO-048.3`).
3. **Step 3 (Update CI Workflow)**: Add Kover execution and artifact upload in `.github/workflows/android-branch-build.yml` (`REQ-PRO-048.3`, `TST-PRO-048.2`).
4. **Step 4 (Expand Contract Test Suite)**: Add contract test methods in `ReleaseCiAndLintConfigContractTest.kt` (`REQ-PRO-048.4`, `TST-PRO-048.1`, `TST-PRO-048.2`).
5. **Step 5 (Targeted Verification)**: Run `ReleaseCiAndLintConfigContractTest` and `./gradlew :app:koverXmlReportDebug :app:koverHtmlReportDebug :app:koverVerifyDebug` asserting 100% PASS (`TST-PRO-048.3`, `TST-PRO-048.4`).

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` and `./gradlew :app:koverXmlReportDebug :app:koverHtmlReportDebug :app:koverVerifyDebug`.
* **Rollback Plan**: Changes are isolated to `feature/ATT-6171`; revert via `git checkout -- .` and `git clean -fd`.
