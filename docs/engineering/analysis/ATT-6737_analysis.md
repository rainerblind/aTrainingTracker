# Stage 1 Analysis: ATT-6737 - Expand AppSmokeAndroidTest to launch MainActivity + tracking start/stop and pre-build APKs on API 34 CI emulator

**Ticket**: [ATT-6737](https://rainerblind.atlassian.net/browse/ATT-6737)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6737`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During Sprint 2026-46.1 review of ATT-6184 (*Set up androidTest infrastructure with emulator CI job*), two enhancements were identified:
1. **Template Context Check in Smoke Test**: `AppSmokeAndroidTest.kt` currently only asserts package name and instrumentation non-nullness. It does not launch `MainActivityWithNavigation` nor exercise an end-to-end `READY -> TRACKING -> STOP/DISCARD` lifecycle on the Android OS runtime.
2. **CI Emulator Idle Compilation & API 30 Limitation**: In `.github/workflows/android-branch-build.yml`, `android-emulator-runner@v2` boots first and compiles the entire project inside `script: ./gradlew :app:connectedDebugAndroidTest` while the emulator sits idle (risking ADB boot timeouts), and targets `api-level: 30` (`target: default`) rather than Android 14 (`api-level: 34`, `target: google_apis`) where Android 14 `FOREGROUND_SERVICE_TYPE_LOCATION | HEALTH` enforcement applies.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * Live inspection of `app/src/androidTest/java/com/atrainingtracker/trainingtracker/AppSmokeAndroidTest.kt` confirms only basic package name and bootstrap assertions are present.
  * Live inspection of `.github/workflows/android-branch-build.yml` (lines 237–245) confirms `api-level: 30`, `target: default`, and zero pre-compilation step prior to emulator startup.
  * Android 14 (API 34) Foreground Service Constraints: Android 14 requires `FOREGROUND_SERVICE_TYPE_LOCATION` and `FOREGROUND_SERVICE_TYPE_HEALTH` with associated runtime permissions. Upgrading the emulator target to `api-level: 34`, `target: google_apis`, `arch: x86_64` aligns CI with `docs/architecture/integration_strategy.md` Level 2 specifications.
  * Adding a dedicated pre-build step (`./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`) prior to emulator boot eliminates compilation latency during emulator execution and avoids emulator boot timeout flakiness.
  * In `AppSmokeAndroidTest.kt`, granting runtime permissions (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `POST_NOTIFICATIONS`) via `GrantPermissionRule` allows `MainActivityWithNavigation` to launch smoothly via `ActivityScenario` and undergo a synthetic start/stop cycle.
  * In accordance with Core Rule 61, synthetic test sessions must be strictly removed via `WorkoutDeletionHelper(context).deleteWorkout(id)` without wiping existing databases.
  * In accordance with Core Rule 79 (emulator retirement policy), zero local emulator is booted; local validation is strictly performed via compile and assemble Gradle tasks.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Expand `AppSmokeAndroidTest.kt` to launch `MainActivityWithNavigation` with `GrantPermissionRule` and `ActivityScenario`.
  * Exercise a synthetic `READY -> TRACKING -> STOP/DISCARD` lifecycle and purge synthetic test session data via `WorkoutDeletionHelper`.
  * Update `.github/workflows/android-branch-build.yml` to pre-build debug and test APKs before booting `android-emulator-runner@v2`.
  * Update CI emulator configuration to `api-level: 34`, `target: google_apis`, `arch: x86_64`.
  * Update contract test `ReleaseCiAndLintConfigContractTest.kt` to enforce pre-build step, API 34 target, and expanded smoke test assertions.
  * Update living documentation in `docs/requirements/req_process_stability.md` (`REQ-PRO-051`) and `docs/tests/tst_process_stability.md` (`TST-PRO-051`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No booting of local emulator on developer/agent workstation (Rule 79).
  * No wiping of database tables or destructive schema resets (Rule 61).
  * No modification to production tracking business logic or service state machines.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-051` (*Android Instrumented Test Infrastructure & Emulator CI Execution*), refining `REQ-PRO-045` under Epic `ATT-6142`. Targets `AppSmokeAndroidTest.kt`, `.github/workflows/android-branch-build.yml`, and `ReleaseCiAndLintConfigContractTest.kt`.
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding C-10 (2026-11-08) and ticket ATT-6184.
* **Root Reason for Existing Formulation**: ATT-6184 initially established a minimal smoke test and API 30 CI runner to establish the baseline pipeline.
* **Preservation of Core Invariants**: Expands coverage to real activity launch, tracking lifecycle, APK pre-assembly, and API 34 runtime verification while strictly preserving Rule 79 (zero local emulator boot), Rule 61 (non-destructive database testing), and 100% JVM unit test pass rate.

---

## 5. Architectural Strategy & High-Level Solution

1. **`AppSmokeAndroidTest.kt`**:
   - Declare `@get:Rule val permissionRule: GrantPermissionRule` for location and notification permissions.
   - Use `ActivityScenario.launch(MainActivityWithNavigation::class.java)` to verify UI startup without crash.
   - Trigger `REQUEST_START_TRACKING` broadcast, assert tracking state transitions, trigger `REQUEST_STOP_TRACKING` broadcast.
   - Obtain synthetic `mWorkoutID` and cleanly remove it via `WorkoutDeletionHelper`.
2. **`.github/workflows/android-branch-build.yml`**:
   - Add pre-build step:
     ```yaml
     - name: Pre-build debug and test APKs
       run: ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
     ```
   - Update emulator runner configuration:
     ```yaml
     api-level: 34
     target: google_apis
     arch: x86_64
     ```
3. **`ReleaseCiAndLintConfigContractTest.kt`**:
   - Verify CI workflow contains pre-build step and API 34 / google_apis configuration.
   - Verify `AppSmokeAndroidTest.kt` references `MainActivityWithNavigation` and `GrantPermissionRule`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero local host emulator booted during development (Rule 79).
  2. Non-destructive test data handling: purge only synthetic test session (Rule 61).
  3. 100% test pass rate across JVM unit tests and CI contract tests.
* **Risk Rating**: **LOW** (Enhancements isolated to androidTest, CI workflow configuration, and contract assertions; zero production code modification).
