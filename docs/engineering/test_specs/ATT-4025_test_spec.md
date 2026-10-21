# Stage 2: Requirement & Test Specification - ATT-4025: [Medium] Run unit tests and lint in release CI and reduce lint baseline

**Ticket**: [ATT-4025](https://rainerblind.atlassian.net/browse/ATT-4025) | **Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*) | **Target Release**: `Backlog` | **Active Sprint**: None | **Branch**: `feature/ATT-4025`  
**Requirement Mapping**: `REQ-BLD-002` (*Release CI Automated Test & Lint Gating with Baseline Hygiene*)  
**Test Spec ID**: `TST-BLD-002`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-BLD-002 Clauses 1–5)

### 1.1 Problem Statement & Rationale
Release build CI (`.github/workflows/android-release.yml`) directly executes `:app:assembleRelease` and `:app:bundleRelease` without test gating. Failing unit tests do not block artifact generation or GitHub Release publishing. Furthermore, `app/lint-baseline.xml` and `AndroidManifest.xml` contain obsolete entries (e.g. deleted `StravaSegmentsIntentService` causing `MissingClass`), and `app/build.gradle` lacks explicit justifications for active lint suppressions.

### 1.2 Functional & Architectural Requirements
* **Release CI Automated Unit Test Gating (`REQ-BLD-002.1`)**: `.github/workflows/android-release.yml` SHALL execute `./gradlew :app:testDebugUnitTest` prior to `:app:assembleRelease` and `:app:bundleRelease`. Any unit test failure MUST halt the workflow immediately, preventing release APK/AAB assembly and GitHub Release publishing.
* **Release Lint Execution & Suppressions Justification (`REQ-BLD-002.2`)**: `app/build.gradle` SHALL maintain `checkReleaseBuilds = true` to run `lintVitalRelease` during release assembly, and SHALL document technical justifications for suppressions: `DuplicatePlatformClasses` (legacy library classpath overlaps), `MissingTranslation` (guarded by `TranslationParityTest`), and `Range` (legacy calculation annotations).
* **Manifest Component Hygiene (`REQ-BLD-002.3`)**: `app/src/main/AndroidManifest.xml` SHALL NOT declare non-existent component classes; orphaned `StravaSegmentsIntentService` SHALL be excised.
* **Lint Baseline Synchronization (`REQ-BLD-002.4`)**: `app/lint-baseline.xml` SHALL be synchronized with active AGP release checks to eliminate orphaned entries.
* **CI Workflow Contract Verification (`REQ-BLD-002.5`)**: `ReleaseWorkflowContractTest.kt` SHALL verify via automated contract test that `.github/workflows/android-release.yml` includes `:app:testDebugUnitTest` prior to `assembleRelease` / `bundleRelease`.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* `.github/workflows/android-release.yml`, *When* inspected by `ReleaseWorkflowContractTest`, *Then* `:app:testDebugUnitTest` is configured to run before `:app:assembleRelease :app:bundleRelease`.
* *Given* a unit test failure during release CI execution, *When* the workflow runs, *Then* the build halts immediately and release assets are not published.
* *Given* `app/build.gradle`, *When* inspected, *Then* `checkReleaseBuilds = true` is active and technical rationales for `DuplicatePlatformClasses`, `MissingTranslation`, and `Range` are documented.
* *Given* `app/src/main/AndroidManifest.xml`, *When* audited, *Then* orphaned `StravaSegmentsIntentService` is absent.
* *Given* the complete unit test suite, *When* `./gradlew testDebugUnitTest` runs, *Then* 100% of unit tests and `TranslationParityTest` pass without regression.

### 1.4 System Invariants
1. Release APK/AAB signing and release artifact distribution MUST remain fully operational.
2. 9-language translation parity MUST remain strictly enforced by `TranslationParityTest`.
3. 100% clean-room test suite pass rate with zero regressions.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

* `Original Requirement ID & Target`: Net-new requirement `REQ-BLD-002`, extending `REQ-BLD-001` (*R8 Release Builds*) and aligning with `REQ-LOC-001` (*Translation Parity*). Targets `.github/workflows/android-release.yml`, `app/build.gradle`, `app/src/main/AndroidManifest.xml`, `app/lint-baseline.xml`, and `ReleaseWorkflowContractTest.kt`.
* `Historical Origin & Commit Trace`: Commit `192635e1` (Guy Sheffer, Jul 2026) added release workflow without test step; commit `2bc9ae1d` (Gerhard Olsson, Jul 2024) suppressed `MissingTranslation` and `Range` during Android 14 SDK migration.
* `Root Reason for Existing Formulation`: CI release workflow focused strictly on packaging and signing artifacts; `MissingTranslation` and `Range` were disabled to avoid blocking builds on hundreds of legacy layout and string issues.
* `Preservation of Core Invariants`: Release APK/AAB signing and asset attachments remain fully operational; 100% clean-room test pass rate; translation integrity preserved via `TranslationParityTest`.

---

## 3. Test Specification (TST-BLD-002)

* **`TST-BLD-002.1`**: CI workflow release test gating contract test (`ReleaseWorkflowContractTest.kt`).
* **`TST-BLD-002.2`**: Manifest `MissingClass` hygiene verification (absence of `StravaSegmentsIntentService`).
* **`TST-BLD-002.3`**: Build configuration lint suppressions documentation audit (`app/build.gradle`).
* **`TST-BLD-002.4`**: Lint baseline re-generation and burn-down verification (`app/lint-baseline.xml`).
* **`TST-BLD-002.5`**: Clean-room regression suite and translation parity execution (`./gradlew testDebugUnitTest`).

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test / Subject | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-BLD-002.1]` | Contract | Release workflow CI gating step | `REQ-BLD-002.1`, `REQ-BLD-002.5` | Specified (ATT-4025) |
| `[TST-BLD-002.2]` | Hygiene | Manifest service declarations | `REQ-BLD-002.3` | Specified (ATT-4025) |
| `[TST-BLD-002.3]` | Config | `build.gradle` suppressions documentation | `REQ-BLD-002.2` | Specified (ATT-4025) |
| `[TST-BLD-002.4]` | Baseline | `lint-baseline.xml` synchronization | `REQ-BLD-002.4` | Specified (ATT-4025) |
| `[TST-BLD-002.5]` | Regression | Full test suite (`testDebugUnitTest`) | `REQ-PRO-001`, `REQ-LOC-001` | Specified (ATT-4025) |
