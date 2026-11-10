# Stage 5: Walkthrough & Verification - ATT-6184: Set up androidTest Infrastructure with Emulator CI Job

**Ticket**: [ATT-6184](https://rainerblind.atlassian.net/browse/ATT-6184)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*) | **Story Points**: 3 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6184`  
**Requirement Mapping**: `REQ-PRO-051` (`REQ-PRO-051.1`–`REQ-PRO-051.4`) | **Test Mapping**: `TST-PRO-051` (`TST-PRO-051.1`–`TST-PRO-051.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary & Verification Overview
Formally resolved ASPICE 4.0 pre-assessment finding C-10 (SWE.5, SWE.6) by establishing the `app/src/androidTest` source set, test instrumentation runner, instrumented smoke test, and an automated emulator CI job in GitHub Actions while strictly observing Rule 79:
* **Test Instrumentation Runner & Dependencies**: Configured `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in `app/build.gradle` `defaultConfig` and declared `androidTestImplementation` dependencies for JUnit ext, Espresso Core, test runner, and rules (`REQ-PRO-051.1`).
* **Instrumented Smoke Test Suite**: Created `app/src/androidTest/java/com/atrainingtracker/trainingtracker/AppSmokeAndroidTest.kt` verifying Android application context and package identity (`com.atrainingtracker.debug` / `com.atrainingtracker`) under `AndroidJUnit4` (`REQ-PRO-051.2`).
* **GitHub Actions Emulator CI Execution Job**: Configured `emulator-test` job in `.github/workflows/android-branch-build.yml` with KVM hardware acceleration running `reactivecircus/android-emulator-runner@v2` on API 30 emulator executing `./gradlew :app:connectedDebugAndroidTest` and archiving test results via `actions/upload-artifact@v4` (`REQ-PRO-051.3`).
* **Contract Testing & Governance**: Extended `ReleaseCiAndLintConfigContractTest.kt` with contract assertions asserting runner config, test dependencies, CI emulator workflow steps, and smoke test existence (`REQ-PRO-051.4`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-051.1` | `TST-PRO-051.1` | `ReleaseCiAndLintConfigContractTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-051.2` | `TST-PRO-051.3` | `compileDebugAndroidTestKotlin` + Contract | **PASSED** | `Verified` |
| `REQ-PRO-051.3` | `TST-PRO-051.2` | `ReleaseCiAndLintConfigContractTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-051.4` | `TST-PRO-051.4` | Targeted Suite `build.*` (Tier 1) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: 46s under parallel sprint lock)
* **Executed Scope**: `Tier 1 targeted: compileDebugAndroidTestKotlin + com.atrainingtracker.trainingtracker.build.*`
* **Command**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 \
    ./gradlew compileDebugAndroidTestKotlin testDebugUnitTest --max-workers=2 \
    --tests "com.atrainingtracker.trainingtracker.build.*"
  ```
* **Results**:
  - `compileDebugAndroidTestKotlin`: SUCCESS (compiles `AppSmokeAndroidTest.kt` against Android SDK 37 without errors).
  - `testDebugUnitTest`: 100% PASS across all contract tests in `com.atrainingtracker.trainingtracker.build.*` (0 failures, 0 regressions).
  - `ReleaseCiAndLintConfigContractTest`: All 3 new tests passed (`testBuildGradle_androidTestInstrumentationRunnerAndDependencies`, `testBranchBuildWorkflow_runsEmulatorTestsAndUploadsReport`, `testAndroidTestDirectory_containsSmokeTest`).
* **Rule 79 Guardrail Compliance**: Local verification strictly confined to compilation and JVM contract tests; zero software emulators booted locally.

---

## 4. Key Implementation Highlights
* **`app/build.gradle`**: Configured `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` and added `androidTestImplementation` for JUnit ext, Espresso Core, test runner, and rules.
* **`app/src/androidTest/`**: Authored `AppSmokeAndroidTest.kt` asserting target context and package identity (`com.atrainingtracker.debug` / `com.atrainingtracker`).
* **CI Workflow**: Added `emulator-test` job running `reactivecircus/android-emulator-runner@v2` (API 30, KVM) running `./gradlew :app:connectedDebugAndroidTest` and archiving test results.

---

## 5. Invariants & Preserved Behavior
1. **Rule 79 Compliance**: No software emulator booted on host machine during local verification.
2. **Zero Runtime Regression**: Zero modifications to production source code in `app/src/main/`.
3. **Living Doc Parity**: `REQ-PRO-051` and `TST-PRO-051` synchronized to `[Verified]` with 100% master index parity.
