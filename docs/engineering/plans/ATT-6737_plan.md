# Stage 3: Implementation Plan - ATT-6737: Expand AppSmokeAndroidTest to launch MainActivity + tracking start/stop and pre-build APKs on API 34 CI emulator

**Ticket**: [ATT-6737](https://rainerblind.atlassian.net/browse/ATT-6737)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-051`  
**Test Mapping**: `TST-PRO-051`  
**Branch**: `feature/ATT-6737`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

Sprint 2026-46.1 review of ATT-6184 identified two critical enhancements for Level 2 verification:
1. `AppSmokeAndroidTest.kt` currently verifies package name and instrumentation context without exercising real activity lifecycle or tracking start/stop behavior.
2. In GitHub Actions CI, `.github/workflows/android-branch-build.yml` runs Gradle compilation while the emulator is already running on API 30, risking emulator idle timeouts and failing to verify Android 14 (API 34) Foreground Service (`FOREGROUND_SERVICE_TYPE_LOCATION | HEALTH`) requirements.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-051` (*Android Instrumented Test Infrastructure & Emulator CI Execution*)
* **Test Mapping**: `TST-PRO-051` (*Android Instrumented Test Infrastructure & Emulator CI Contract Test Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Rule 79 Invariance**: Zero local host emulator booted on development or agent machines; local verification uses compilation (`compileDebugAndroidTestKotlin`) and APK assembly (`assembleDebug`, `assembleDebugAndroidTest`).
2. **Rule 61 Invariance**: Non-destructive test execution: Never wipe SQLite databases or reset schema; only purge the synthetic test workout session via `WorkoutDeletionHelper`.
3. **Subtask Self-Sufficiency & Human Decision Gate**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`; parent ticket remains at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `AppSmokeAndroidTest.kt` (`app/src/androidTest/...`)
* Add `GrantPermissionRule` granting `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, and `POST_NOTIFICATIONS` (API 33+).
* Use `ActivityScenario.launch(MainActivityWithNavigation::class.java)` to verify UI startup without crash.
* Exercise `READY -> TRACKING -> STOP/DISCARD` lifecycle, verify state transitions, and clean up synthetic test workout data via `WorkoutDeletionHelper`.

### Component 2: `.github/workflows/android-branch-build.yml`
* Insert pre-build step:
  ```yaml
  - name: Pre-build debug and test APKs
    run: ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
  ```
* Update emulator runner configuration to:
  ```yaml
  api-level: 34
  target: google_apis
  arch: x86_64
  ```

### Component 3: `ReleaseCiAndLintConfigContractTest.kt` (`app/src/test/...`)
* Update `testBranchBuildWorkflow_runsEmulatorTestsAndUploadsReport()` to assert pre-build step and API 34 google_apis target.
* Update `testAndroidTestDirectory_containsSmokeTest()` to assert `MainActivityWithNavigation` and `GrantPermissionRule` references in `AppSmokeAndroidTest.kt`.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update CI Workflow Configuration
* Files: `.github/workflows/android-branch-build.yml`
* Changes: Add pre-build step before `android-emulator-runner@v2`; update `api-level: 34` and `target: google_apis`.

### Step 2: Update Build & CI Contract Assertions
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* Changes: Add contract assertions for CI pre-build step, API 34 target, and `AppSmokeAndroidTest.kt` structure.

### Step 3: Expand `AppSmokeAndroidTest.kt`
* Files: `app/src/androidTest/java/com/atrainingtracker/trainingtracker/AppSmokeAndroidTest.kt`
* Changes: Add `GrantPermissionRule`, launch `MainActivityWithNavigation`, cycle through tracking start/stop, and purge synthetic test session via `WorkoutDeletionHelper`.

### Step 4: Verification via Contract Unit Tests & APK Assembly
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest`
* Command: `python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-6737_test_spec.md`

---

## 6. Verification & Rollback Plan

* **Verification**: Unit contract tests and APK compilation/assembly verify correctness without booting local emulator. Full connected tests run on API 34 emulator in GitHub Actions CI.
* **Rollback**: Branch isolation (`feature/ATT-6737`) allows clean revert to `sprint/2026-46.2` without side effects.
