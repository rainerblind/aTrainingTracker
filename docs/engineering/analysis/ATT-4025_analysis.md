# Stage 1 Analysis: ATT-4025 - Run unit tests and lint in release CI and reduce lint baseline

**Ticket**: [ATT-4025](https://rainerblind.atlassian.net/browse/ATT-4025)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (Process) | **Target Release**: `Backlog` | **Active Sprint**: None | **Branch**: `feature/ATT-4025`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Domain & Root Cause Analysis

Forensic code and CI audit confirmed reviewer findings with critical architectural nuances:
1. **Release CI Ungated by Tests**: `.github/workflows/android-release.yml` executes `./gradlew :app:assembleRelease :app:bundleRelease` directly without running tests. Failing unit tests do not block release artifact generation or GitHub Release publishing.
2. **Test Task Variant**: AGP configures unit tests exclusively under variant `debug` (`:app:testDebugUnitTest`). Task `:app:testReleaseUnitTest` does not exist in AGP for this project (fails with `Cannot locate tasks that match ':app:testReleaseUnitTest'`). Gating release assembly requires `:app:testDebugUnitTest`.
3. **Release Lint Execution**: `checkReleaseBuilds = true` in `app/build.gradle` triggers `lintVitalRelease` during `assembleRelease`, which passes cleanly (0 vital errors). However, full `lintRelease` is not executed in CI; running it locally surfaces 53 errors and 868 warnings (140 filtered by baseline) and requires ~10 minutes.
4. **Lint Suppressions Justification**:
   - `DuplicatePlatformClasses`: Justified due to legacy third-party library classpath overlaps (Apache HTTP/XML).
   - `MissingTranslation`: Safely suppressed because `TranslationParityTest.kt` (`TST-STR-018` under `REQ-LOC-001`) actively and strictly verifies full 9-language coverage, format specifiers, and plural consistency in `testDebugUnitTest`, avoiding false positives from English-only diagnostic/vendor strings.
   - `Range`: Suppressed due to hundreds of historical annotation warnings in legacy calculation routines; requires a dedicated multi-sprint burn-down.
5. **Baseline State & Hygiene**: `app/lint-baseline.xml` contains 608 entries generated under AGP 8.7.1. New Compose checks and an orphaned manifest entry (`StravaSegmentsIntentService`, deleted from codebase) fail full `lintRelease`.

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Nature of Change & Impact |
| :--- | :--- | :--- |
| **Release Workflow** | `.github/workflows/android-release.yml` | Insert `:app:testDebugUnitTest` prior to `assembleRelease :app:bundleRelease` to gate artifact generation. |
| **Lint Configuration** | `app/build.gradle` | Retain justified suppressions (`DuplicatePlatformClasses`, `MissingTranslation`, `Range`); preserve `checkReleaseBuilds = true`. |
| **Manifest Hygiene** | `app/src/main/AndroidManifest.xml` | Remove orphaned `StravaSegmentsIntentService` declaration resolving `MissingClass` lint error. |
| **Lint Baseline** | `app/lint-baseline.xml` | Refresh/update baseline to reconcile active errors and establish burn-down baseline. |
| **CI Contract Test** | `WorkflowContractTest.kt` | Add automated contract test verifying release CI includes `testDebugUnitTest` gating. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `./gradlew :app:testDebugUnitTest` to `.github/workflows/android-release.yml` before release assembly and artifact publishing.
  2. Document technical justifications in `app/build.gradle` for `MissingTranslation` (guarded by `TranslationParityTest`), `DuplicatePlatformClasses`, and `Range`.
  3. Excise orphaned `StravaSegmentsIntentService` from `AndroidManifest.xml`.
  4. Synchronize `lint-baseline.xml` with current AGP release variant and document incremental burn-down strategy.
* **Out-of-Scope Non-Goals**:
  1. No mass refactoring of 600+ historical lint warnings in `lint-baseline.xml`.
  2. No activation of `Range` lint rule before math/sensor domain refactoring.
  3. No alteration to release signing secrets or release packaging logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Establishes `REQ-BLD-002` (*Release CI Automated Test & Lint Gating with Baseline Hygiene*), extending `REQ-BLD-001` (*R8 Release Builds*) and aligning with `REQ-LOC-001` (*Translation Parity*).
* **Historical Origin & Commit Trace**: Commit `192635e1` (Guy Sheffer, Jul 2026) added release workflow without test step; commit `2bc9ae1d` (Gerhard Olsson, Jul 2024) suppressed `MissingTranslation` and `Range` during Android 14 SDK migration.
* **Root Reason for Existing Formulation**: CI release workflow focused strictly on packaging and signing artifacts; `MissingTranslation` and `Range` were disabled to avoid blocking builds on hundreds of legacy layout and string issues.
* **Preservation of Core Invariants**: Release APK/AAB signing and asset attachments remain fully operational; 100% clean-room test pass rate; translation integrity preserved via `TranslationParityTest`.

---

## 5. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Release workflow MUST fail immediately and halt artifact creation if any unit test fails in `:app:testDebugUnitTest`.
  2. Release APK/AAB artifacts MUST NOT be uploaded to GitHub Releases unless tests succeed.
  3. 9-language translation parity MUST remain strictly enforced by `TranslationParityTest`.
  4. Zero regressions in release build generation or application runtime behavior.
* **Risk Rating**: **LOW** — Modification is confined to CI workflow gating, build script documentation, manifest cleanup, and lint baseline synchronization.
