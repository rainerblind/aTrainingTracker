# Stage 2: Requirement & Test Specification - ATT-6184: Set up androidTest Infrastructure with Emulator CI Job

**Ticket**: [ATT-6184](https://rainerblind.atlassian.net/browse/ATT-6184)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6184`  
**Requirement Mapping**: `REQ-PRO-051` (*Android Instrumented Test Infrastructure & Emulator CI Execution*)  
**Test Spec ID**: `TST-PRO-051`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-PRO-051)

### 1.1 Problem Statement & Rationale
ASPICE pre-assessment finding C-10 (SWE.5, SWE.6) identified that `app/src/androidTest` does not exist and no on-device or emulator verification pipeline executes in GitHub Actions CI. Establishing AndroidJUnitRunner configuration, standard AndroidX test dependencies, an instrumented smoke test, and an emulator CI workflow fulfills Level 2 integration verification requirements while adhering strictly to Rule 79 (zero local host software emulator boots).

### 1.2 Functional & Technical Requirements
* **REQ-PRO-051.1** (*Runner & Dependency Configuration*): `app/build.gradle` `defaultConfig` SHALL configure `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`, and declare standard `androidTestImplementation` dependencies (`androidx.test.ext:junit`, `androidx.test.espresso:espresso-core`, `androidx.test:runner`, `androidx.test:rules`).
* **REQ-PRO-051.2** (*Instrumented Smoke Test Suite*): Establish `app/src/androidTest/java/com/atrainingtracker/trainingtracker/SmokeInstrumentationTest.kt` verifying target context, package identifier, and instrumentation bootstrap under `AndroidJUnit4`.
* **REQ-PRO-051.3** (*GitHub Actions Emulator CI Execution Job*): `.github/workflows/android-branch-build.yml` SHALL define an emulator test job with KVM hardware acceleration running `./gradlew :app:connectedDebugAndroidTest` and archiving test results via `actions/upload-artifact@v4`.
* **REQ-PRO-051.4** (*Build & CI Conformance Contract Testing*): `ReleaseCiAndLintConfigContractTest.kt` SHALL assert that `app/build.gradle` configures `testInstrumentationRunner` and `androidTestImplementation`, and that the CI workflow defines the emulator job and artifact upload.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Runner & Dependencies)**: *Given* `app/build.gradle`, *When* inspected, *Then* it SHALL configure `testInstrumentationRunner` and declare `androidTestImplementation` dependencies.
* **Criterion 2 (AndroidTest Compilation)**: *Given* `SmokeInstrumentationTest.kt`, *When* `./gradlew compileDebugAndroidTestKotlin` executes, *Then* compilation SHALL succeed without errors.
* **Criterion 3 (CI Emulator Workflow)**: *Given* `.github/workflows/android-branch-build.yml`, *When* inspected, *Then* it SHALL contain an emulator test job running `connectedDebugAndroidTest` and uploading test results.
* **Criterion 4 (Contract Test Execution)**: *Given* `ReleaseCiAndLintConfigContractTest.kt`, *When* executed in the unit test suite, *Then* all contract assertions SHALL pass with 100% success.

---

## 2. Test Specification (TST-PRO-051)

### Test Case 1: Build Script Instrumentation Runner & Dependency Test (`TST-PRO-051.1`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify `app/build.gradle` contains `testInstrumentationRunner` and `androidTestImplementation` dependencies.

### Test Case 2: CI Workflow Emulator Job & Artifact Upload Test (`TST-PRO-051.2`)
* **Scope**: Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)
* **Assertion**: Verify `.github/workflows/android-branch-build.yml` contains emulator runner action/step and test artifact upload.

### Test Case 3: AndroidTest Compilation & Smoke Test Verification (`TST-PRO-051.3`)
* **Scope**: Build Compilation (`SmokeInstrumentationTest.kt`)
* **Command**: `./gradlew compileDebugAndroidTestKotlin`
* **Assertion**: Verify zero compilation errors and valid AndroidJUnit4 smoke test structure.

### Test Case 4: Clean-Room Targeted Suite Execution (`TST-PRO-051.4`)
* **Scope**: Contract Test Suite (`ReleaseCiAndLintConfigContractTest.kt`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-051.1` | Contract | `app/build.gradle`, `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-051.1` | Planned |
| `TST-PRO-051.2` | Contract | `.github/workflows/android-branch-build.yml`, `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-051.3` | Planned |
| `TST-PRO-051.3` | Build | `SmokeInstrumentationTest.kt` | `REQ-PRO-051.2` | Planned |
| `TST-PRO-051.4` | Suite | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-051.4` | Planned |
