# Stage 3: Implementation Plan - ATT-4025: Run unit tests and lint in release CI and reduce lint baseline

**Ticket**: [ATT-4025](https://rainerblind.atlassian.net/browse/ATT-4025)  
**Sub-task**: `[Impl-Plan]` | **Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `Backlog` | **Active Sprint**: None | **Branch**: `feature/ATT-4025`  
**Requirement Mapping**: `REQ-BLD-002` (*Release CI Automated Test & Lint Gating with Baseline Hygiene*)  
**Test Mapping**: `TST-BLD-002` (`TST-BLD-002.1`–`TST-BLD-002.5`)  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Description & Background

`.github/workflows/android-release.yml` executes `:app:assembleRelease :app:bundleRelease` without running unit tests. Release artifacts can be built and published even if tests fail. Task `:app:testReleaseUnitTest` does not exist in AGP for this project; unit tests reside under variant `debug` (`:app:testDebugUnitTest`). Furthermore, `AndroidManifest.xml` and `lint-baseline.xml` contain obsolete references to deleted `StravaSegmentsIntentService` (`MissingClass`), and `app/build.gradle` lacks explicit justifications for active lint suppressions (`DuplicatePlatformClasses`, `MissingTranslation`, `Range`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-BLD-002` (*Release CI Automated Test & Lint Gating with Baseline Hygiene*), extending `REQ-BLD-001` and aligning with `REQ-LOC-001`.
* **Test Mapping**: `TST-BLD-002` (`TST-BLD-002.1`–`TST-BLD-002.5` in `ReleaseCiAndLintConfigContractTest.kt` and full test suite).

---

## 3. System Invariants & Preserved Behavior

1. **Test Gating Invariant**: Release workflow MUST halt immediately on any unit test failure; release APK/AAB artifacts MUST NOT be generated or published if tests fail.
2. **Translation Parity Invariant**: 9-language translation parity (`de, es, fr, it, ja, nl, pl, pt`) MUST remain strictly enforced via `TranslationParityTest.kt` (`TST-STR-018`).
3. **Build & Release Signing Invariant**: Release APK/AAB signing and asset attachment logic in CI MUST remain fully functional without regressions.
4. **Clean-Room Test Suite Pass Rate**: 100% test pass rate across existing unit and contract tests under `./gradlew testDebugUnitTest`.

---

## 4. Proposed Architectural Changes

### Component 1: Release CI Workflow (`.github/workflows/android-release.yml`)
* Insert step "Run unit tests" invoking `./gradlew :app:testDebugUnitTest` immediately before "Build release APK and AAB".

### Component 2: AndroidManifest Hygiene (`app/src/main/AndroidManifest.xml`)
* Delete orphaned `<service android:name=".trainingtracker.onlinecommunities.strava.StravaSegmentsIntentService" />` declaration, eliminating `MissingClass` lint error.

### Component 3: Lint Configuration & Suppressions Documentation (`app/build.gradle`)
* Preserve `checkReleaseBuilds = true` to run `lintVitalRelease`.
* Document technical rationales for `DuplicatePlatformClasses` (legacy classpath overlaps), `MissingTranslation` (superseded by `TranslationParityTest`), and `Range` (historical annotations in legacy math routines slated for future burn-down).

### Component 4: Lint Baseline Hygiene (`app/lint-baseline.xml`)
* Remove stale issue entry for deleted `StravaSegmentsIntentService.java`.

### UI Consistency (Rule 23)
* No UI changes (CI workflow, build script, manifest hygiene, and lint configuration only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

* **Step 1: Gated Release CI Workflow (`REQ-BLD-002.1`)**: Edit `.github/workflows/android-release.yml` to insert `Run unit tests` step (`./gradlew :app:testDebugUnitTest`) before `Build release APK and AAB`.
* **Step 2: Manifest Component Cleanup (`REQ-BLD-002.3`)**: In `app/src/main/AndroidManifest.xml`, remove orphaned `StravaSegmentsIntentService` service declaration.
* **Step 3: Document Build Lint Configuration (`REQ-BLD-002.2`)**: In `app/build.gradle` `lint { ... }` block, add technical rationale comments explaining `DuplicatePlatformClasses`, `MissingTranslation`, and `Range`.
* **Step 4: Excise Obsolete Baseline Entry (`REQ-BLD-002.4`)**: In `app/lint-baseline.xml`, remove stale `StravaSegmentsIntentService.java` issue entry.
* **Step 5: Automated Contract Test (`TST-BLD-002.1`–`TST-BLD-002.4`)**: Author `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt` verifying:
  1. `.github/workflows/android-release.yml` runs `:app:testDebugUnitTest` prior to `assembleRelease` / `bundleRelease`.
  2. `AndroidManifest.xml` does not declare `StravaSegmentsIntentService`.
  3. `app/build.gradle` configures `checkReleaseBuilds = true` and documents suppressions.
  4. `lint-baseline.xml` does not contain `StravaSegmentsIntentService`.
* **Step 6: Clean-Room Regression Suite (`TST-BLD-002.5`)**: Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate including `ReleaseCiAndLintConfigContractTest` and `TranslationParityTest`.

---

## 6. Verification & Rollback Plan

* **Verification**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` and `./gradlew testDebugUnitTest`.
* **Rollback**: Revert working branch commits via `git checkout feature/ATT-4025` if regressions emerge.
