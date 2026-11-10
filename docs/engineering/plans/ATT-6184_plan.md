# Stage 3: Implementation Plan - ATT-6184: Set up androidTest infrastructure with emulator CI job

**Ticket**: [ATT-6184](https://rainerblind.atlassian.net/browse/ATT-6184)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-PRO-051` (`REQ-PRO-051.1` - `REQ-PRO-051.4`)  
**Test Mapping**: `TST-PRO-051` (`TST-PRO-051.1` - `TST-PRO-051.4`)  
**Branch**: `feature/ATT-6184`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Description & Background
ASPICE pre-assessment finding C-10 (SWE.5, SWE.6) identified that `app/src/androidTest` is missing and no automated on-device / emulator CI verification pipeline exists. This plan operationalizes instrumented test infrastructure in Gradle, establishes the initial smoke test suite, configures a GitHub Actions emulator CI job, and enforces build contracts while strictly observing Rule 79 (zero local host software emulator boots).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-051` (*Android Instrumented Test Infrastructure & Emulator CI Execution*)
* **Test Mapping**: `TST-PRO-051` (*Android Instrumented Test Infrastructure & Emulator CI Contract Test Suite*)
* **Traceability Matrix**:
  - `REQ-PRO-051.1` -> `TST-PRO-051.1` (Gradle test runner & `androidTestImplementation` dependencies)
  - `REQ-PRO-051.2` -> `TST-PRO-051.3` (`AppSmokeAndroidTest.kt` in `app/src/androidTest/`)
  - `REQ-PRO-051.3` -> `TST-PRO-051.2` (CI workflow emulator job & test artifact archiving)
  - `REQ-PRO-051.4` -> `TST-PRO-051.4` (Targeted `ReleaseCiAndLintConfigContractTest` suite execution)

---

## 3. System Invariants & Preserved Behavior
1. **Rule 79 Guardrail**: Local subagents NEVER boot `emulator` / `qemu-system-*` software emulators on the host machine.
2. **Local Verification Bounding**: Local verification is strictly confined to `compileDebugAndroidTestKotlin`, `assembleDebugAndroidTest`, and JVM contract tests.
3. **Zero Production Code Regression**: No runtime files in `app/src/main/` or database schemas are altered.
4. **CI Gate Stability**: JVM unit test (`testDebugUnitTest`), Lint, Detekt, and Kover gates continue running uninterrupted.

---

## 4. Proposed Architectural Changes
* **Build Architecture (`app/build.gradle`)**: Configure `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in `defaultConfig`. Declare `androidTestImplementation` dependencies (`androidx.test.ext:junit:1.2.1`, `androidx.test.espresso:espresso-core:3.6.1`, `androidx.test:runner:1.6.2`, `androidx.test:rules:1.6.1`).
* **Source Tree (`app/src/androidTest/`)**: Create `app/src/androidTest/java/com/atrainingtracker/trainingtracker/AppSmokeAndroidTest.kt` verifying Android context bootstrap and package id under `AndroidJUnit4`.
* **CI Workflow (`.github/workflows/android-branch-build.yml`)**: Add `emulator-test` job with KVM hardware acceleration running `reactivecircus/android-emulator-runner@v2` (API 30, x86_64) running `./gradlew :app:connectedDebugAndroidTest` and archiving test results to `connected-test-report`.
* **Contract Tests (`ReleaseCiAndLintConfigContractTest.kt`)**: Add contract assertions for runner configuration, test dependencies, CI emulator workflow steps, and `AppSmokeAndroidTest.kt` existence.
* **UI Consistency**: No UI changes (test infrastructure & CI pipeline only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Configure `app/build.gradle` (REQ-PRO-051.1)
* Add `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` to `defaultConfig`.
* Add `androidTestImplementation` dependencies for JUnit ext, Espresso Core, runner, and rules.
* Verify via `./gradlew compileDebugAndroidTestKotlin`.

### Step 2: Establish `app/src/androidTest/` Smoke Test (REQ-PRO-051.2)
* Create `app/src/androidTest/java/com/atrainingtracker/trainingtracker/AppSmokeAndroidTest.kt`.
* Implement smoke test asserting target package name (`com.atrainingtracker.debug` / `com.atrainingtracker`) and context validity.
* Verify compilation via `./gradlew compileDebugAndroidTestKotlin assembleDebugAndroidTest`.

### Step 3: Configure CI Emulator Job (REQ-PRO-051.3)
* Update `.github/workflows/android-branch-build.yml` with `emulator-test` job running `reactivecircus/android-emulator-runner@v2` and uploading `app/build/reports/androidTests/connected/`.

### Step 4: Add Contract Tests & Verify Targeted Suite (REQ-PRO-051.4)
* Add `testBuildGradle_androidTestInstrumentationRunnerAndDependencies()`, `testBranchBuildWorkflow_runsEmulatorTestsAndUploadsReport()`, and `testAndroidTestDirectory_containsSmokeTest()` in `ReleaseCiAndLintConfigContractTest.kt`.
* Run: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
* Run: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Local Verification**: `./gradlew compileDebugAndroidTestKotlin assembleDebugAndroidTest` + JVM contract tests in `ReleaseCiAndLintConfigContractTest`.
* **Remote Verification**: GitHub Actions CI executes `connectedDebugAndroidTest` on API 30 emulator.
* **Rollback Plan**: Git branch isolation (`feature/ATT-6184`) allows clean rollback with zero impact on `sprint/2026-46.1`.
