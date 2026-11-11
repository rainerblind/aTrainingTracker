# Stage 2: Requirement & Test Specification - ATT-6737: Expand AppSmokeAndroidTest to launch MainActivity + tracking start/stop and pre-build APKs on API 34 CI emulator

**Ticket**: [ATT-6737](https://rainerblind.atlassian.net/browse/ATT-6737)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-051` (*Android Instrumented Test Infrastructure & Emulator CI Execution*)  
**Test Spec ID**: `TST-PRO-051`  
**Branch**: `feature/ATT-6737`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-051)

### 1.1 Problem Statement & Rationale
Prior to ATT-6737, `AppSmokeAndroidTest.kt` only verified target application package name and basic instrumentation bootstrap. In CI, `.github/workflows/android-branch-build.yml` compiled APKs during active emulator execution on API 30, causing runner idle time and missing Android 14 (API 34) Foreground Service (`FOREGROUND_SERVICE_TYPE_LOCATION | HEALTH`) validation. Expanding `AppSmokeAndroidTest.kt` to launch `MainActivityWithNavigation` and cycle through `READY -> TRACKING -> STOP/DISCARD` while pre-building APKs in CI establishes verified Level 2 integration testing per ASPICE SWE.5 / SWE.6.

### 1.2 Functional & Architectural Requirements
* **`REQ-PRO-051.1`**: `app/build.gradle` SHALL configure `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` and declare `androidTestImplementation` dependencies for JUnit, Espresso Core, runner, and rules.
* **`REQ-PRO-051.2`**: `app/src/androidTest/java/com/atrainingtracker/trainingtracker/AppSmokeAndroidTest.kt` SHALL declare `GrantPermissionRule` for runtime location and notification permissions, launch `MainActivityWithNavigation` via `ActivityScenario` without crashing, exercise a synthetic `READY -> TRACKING -> STOP/DISCARD` lifecycle, and purge synthetic test session data via `WorkoutDeletionHelper` without wiping existing database tables (Core Rule 61).
* **`REQ-PRO-051.3`**: `.github/workflows/android-branch-build.yml` SHALL execute a pre-build step `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` prior to booting `reactivecircus/android-emulator-runner@v2`, targeting Android 14 (`api-level: 34`, `target: google_apis`, `arch: x86_64`) with hardware acceleration (KVM).
* **`REQ-PRO-051.4`**: `ReleaseCiAndLintConfigContractTest.kt` SHALL assert that `app/build.gradle` configures the runner and test dependencies, `.github/workflows/android-branch-build.yml` configures the pre-build step and API 34 target, and `AppSmokeAndroidTest.kt` references `MainActivityWithNavigation` and `GrantPermissionRule`.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PRO-051` (*Android Instrumented Test Infrastructure & Emulator CI Execution*), refining `REQ-PRO-045` under Epic `ATT-6142`. Targets `AppSmokeAndroidTest.kt`, `.github/workflows/android-branch-build.yml`, and `ReleaseCiAndLintConfigContractTest.kt`.
2. *Historical Origin & Commit Trace*: Independent ASPICE pre-assessment finding C-10 (2026-11-08) and tickets ATT-6184, ATT-6737.
3. *Root Reason for Existing Formulation*: ATT-6184 initially established a minimal package-name smoke assertion and API 30 runner to lay the pipeline foundation without blocking on full activity orchestration.
4. *Preservation of Core Invariants*: JVM unit test suite (`testDebugUnitTest`), Lint, Detekt, and Kover gates remain untouched. Rule 79 (zero local host software emulator boots during development) and Rule 61 (non-destructive test data handling) remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Behavioral App Launch & Non-Destructive Tracking Start/Stop Smoke Test)**:
  * *Given* `AppSmokeAndroidTest.kt` with `GrantPermissionRule` for location/notification permissions and `ActivityScenario`,
  * *When* `connectedDebugAndroidTest` runs in CI,
  * *Then* it launches `MainActivityWithNavigation`, verifies UI startup without crash, exercises a synthetic `READY -> TRACKING -> STOP/DISCARD` session lifecycle, and strictly removes only its own synthetic test session via `WorkoutDeletionHelper`.
* **Criterion 2 (Pre-Compiled APK Step & API 34 Emulator Target in CI)**:
  * *Given* `emulator-test` job in `.github/workflows/android-branch-build.yml`,
  * *When* CI executes,
  * *Then* `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` runs before booting `android-emulator-runner@v2`, and the emulator targets `api-level: 34` (`google_apis`, `x86_64`).

### 1.5 System Invariants
1. Rule 79 strictly preserved: Zero local host software emulator boots during development; local verification uses compilation and assembly tasks.
2. Rule 61 strictly preserved: No destructive table wipes or dropping of existing databases; only synthetic session rows deleted.
3. 100% JVM unit test pass rate and requirement governance pass.

---

## 2. Test Specification (TST-PRO-051)

### Test Case 1: `testBranchBuildWorkflow_runsEmulatorTestsAndUploadsReport` (`TST-PRO-051.2`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* **Preconditions**: `.github/workflows/android-branch-build.yml` file exists in repository.
* **Action**: Parse workflow YAML content and assert:
  - Presence of pre-build step executing `:app:assembleDebug :app:assembleDebugAndroidTest`.
  - Presence of `api-level: 34` and `target: google_apis`.
* **Expected Result**: Assertions pass with 100% success.

### Test Case 2: `testAndroidTestDirectory_containsSmokeTest` (`TST-PRO-051.3`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* **Preconditions**: `AppSmokeAndroidTest.kt` exists in `app/src/androidTest/`.
* **Action**: Read test file and assert references to `MainActivityWithNavigation`, `GrantPermissionRule`, and synthetic session lifecycle.
* **Expected Result**: Assertions pass with 100% success.

### Test Case 3: Compilation and Assembly of Debug and AndroidTest APKs (`TST-PRO-051.3`)
* **Scope**: Build Verification (SWE.5 / Level 2 entry criteria)
* **Command**: `./gradlew :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest`
* **Expected Result**: Clean compilation and assembly of both APKs with exit code 0.

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-PRO-051.5`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence and matching `%s`/`%d` tokens across all 9 locales: EN, DE, ES, FR, IT, JA, NL, PL, PT. (Zero new user-facing strings introduced in ATT-6737; existing strings remain 100% intact).
* **Expected Result**: 100% parity maintained.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Command | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-051.1` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-051.1` | Specified |
| `TST-PRO-051.2` | Contract | `ReleaseCiAndLintConfigContractTest.kt` | `REQ-PRO-051.3` | Specified |
| `TST-PRO-051.3` | Contract & Build | `AppSmokeAndroidTest.kt` / `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` | `REQ-PRO-051.2` | Specified |
| `TST-PRO-051.4` | Clean-Room Suite | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` | `REQ-PRO-051.4` | Specified |
| `TST-PRO-051.5` | Localization | `TranslationParityTest` | `REQ-UI-106` | Specified |
