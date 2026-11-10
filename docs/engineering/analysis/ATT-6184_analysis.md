# Stage 1 Analysis: ATT-6184 - Set up androidTest infrastructure with emulator CI job

**Ticket**: [ATT-6184](https://rainerblind.atlassian.net/browse/ATT-6184)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6184`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Independent ASPICE 4.0 pre-assessment finding C-10 (SWE.5, SWE.6) identified that `app/src/androidTest` does not exist in the repository and no on-device or emulator verification pipeline runs in GitHub Actions CI (`.github/workflows/android-branch-build.yml`). While unit tests execute in JVM (Robolectric / MockK / Kover), instrumented verification of Android OS contracts, permissions, lifecycle transitions, and ART runtime behavior cannot run in CI. Establishing `androidTest` infrastructure with standard runner, dependencies, a smoke test, and an emulator CI job resolves finding C-10.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Missing Build & Source Infrastructure**:
   - `app/build.gradle` lacks `testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"` in `defaultConfig`.
   - `app/build.gradle` has zero `androidTestImplementation` dependencies.
   - `app/src/androidTest` directory is absent (`compileDebugAndroidTestKotlin` yields `NO-SOURCE`).
2. **Missing CI Emulator Pipeline**:
   - `.github/workflows/android-branch-build.yml` defines only `build-debug` without Android emulator execution or test result archiving.
3. **Independent Peer Review & Verification**:
   - Audited live sprint codebase on `sprint/2026-46.1`: `app/src/androidTest` does not exist; `app/build.gradle` has no `testInstrumentationRunner` or `androidTestImplementation`.
   - Verified compilation pipeline: `./gradlew compileDebugAndroidTestKotlin` executes successfully as `NO-SOURCE` without errors once local environment is configured.
   - Verified Rule 79 compliance: Local verification is strictly bounded to `compileDebugAndroidTestKotlin`, `assembleDebugAndroidTest`, and contract tests (`ReleaseCiAndLintConfigContractTest.kt`), completely prohibiting local software emulator boots on the host machine.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Configure `testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"` in `app/build.gradle` (`defaultConfig`).
  - Add standard `androidTestImplementation` dependencies (`androidx.test.ext:junit`, `androidx.test.espresso:espresso-core`, `androidx.test:runner`, `androidx.test:rules`) compatible with pinned AndroidX libraries.
  - Create `app/src/androidTest/java/com/atrainingtracker/trainingtracker/SmokeInstrumentationTest.kt` verifying package context and basic application launch under AndroidJUnit4.
  - Add an emulator verification job in `.github/workflows/android-branch-build.yml` using `reactivecircus/android-emulator-runner@v2` (API 30, x86_64) running `./gradlew :app:connectedDebugAndroidTest` and archiving test results via `actions/upload-artifact@v4`.
  - Extend `ReleaseCiAndLintConfigContractTest.kt` with contract tests verifying `testInstrumentationRunner`, `androidTestImplementation`, and CI emulator workflow configuration.
  - Map to living docs: `req_process_stability.md` (`REQ-PRO-051`) and `tst_process_stability.md` (`TST-PRO-051`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Exhaustive UI E2E test flows for all screens (future tickets under SWE.5/SWE.6).
  - Booting local software emulators on the developer host machine (strictly prohibited by Rule 79).
  - Runtime production code changes in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-051` (*Android Instrumented Test Infrastructure & Emulator CI Execution*) and test suite `TST-PRO-051`. Refines ASPICE SWE.5/SWE.6 integration strategy (`REQ-PRO-045`).
* **Historical Origin & Commit Trace**: ASPICE 4.0 pre-assessment finding C-10 (2026-11-08) and Parent Epic ATT-6142.
* **Root Reason for Existing Formulation**: Project historically relied on JVM Robolectric tests for CI velocity, leaving instrumented ART verification unautomated.
* **Preservation of Core Invariants**: Existing JVM unit tests (`testDebugUnitTest`), Lint, Detekt, and Kover gates continue untouched. Zero production runtime or release signing impact.

---

## 5. Architectural Strategy & High-Level Solution
* `app/build.gradle`: Add `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` and `androidTestImplementation` test libraries compatible with core-1.15.0.
* `app/src/androidTest/`: Author `SmokeInstrumentationTest.kt` verifying target context and app instantiation.
* `.github/workflows/android-branch-build.yml`: Add `emulator-test` job with KVM hardware acceleration running `./gradlew :app:connectedDebugAndroidTest` and uploading test results.
* `ReleaseCiAndLintConfigContractTest.kt`: Add contract verification for `testInstrumentationRunner`, `androidTestImplementation`, and CI emulator job.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero production code changes; Rule 79 compliance (no local host emulator boot); 100% unit test pass rate; living doc 100% parity.
* **Risk Rating**: **MEDIUM** (3 SP, build and CI infrastructure expansion without runtime regressions).
