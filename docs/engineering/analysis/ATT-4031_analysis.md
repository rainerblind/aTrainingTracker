# Stage 1 Analysis: ATT-4031 - Split god classes and remove dead code

**Ticket**: [ATT-4031](https://rainerblind.atlassian.net/browse/ATT-4031)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4031`  
**Requirement Mapping**: `REQ-ARC-003` (*God Class Splitting, Dead Code Elimination, Thread-Safe Visibility & API Modernization*)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Statement & Motivation
An independent whole-app code review flagged structural debt, dead code, visibility hazards, and deprecated APIs across core components:
1. **Dead Methods & Unused State**: `todo()`, `setIsSegmentListUpdating()`, `isSegmentListUpdating()`, and `mSegmentListUpdating` in `TrainingApplication.java`; uncalled `checkBatteryOptimizations()` and `showInstallANTShitDialog()` in `MainActivityWithNavigation.kt`.
2. **Dead Legacy Classes**: `SelectSportTypeDialog.java`, `MultiSelectionSpinner.java`, `DeviceIdAndNameArrayAdapter.java`, and `InstallANTShitDialog.java` have 0 usages across the entire codebase (`BaseOAuthCallbackActivity` and `GenericFileProvider` were already purged in `ATT-4022`/`ATT-4023`).
3. **Visibility & Thread Safety**: `cTrackingMode` is confirmed `volatile`, but static mutable flag `cResumeFromCrash` in `TrainingApplication.java` lacks `volatile`, risking stale CPU cache reads during recovery.
4. **Deprecated APIs**: Deprecated `FLAG_SHOW_WHEN_LOCKED` in `MainActivityWithNavigation.kt` instead of modern `setShowWhenLocked(true)` (API 27+).
5. **Overexposed UI Previews**: Top-level `@Preview` composables in Compose UI files lack visibility modifiers, defaulting to `public` in production bytecode.

---

## 2. Root Cause Analysis & Call Site Audit

| Component / File | Findings & Root Cause | Remediation Strategy | Affected Call Sites |
|---|---|---|---|
| `TrainingApplication.java` | Uncalled `@Deprecated todo()`; uncalled segment updating map/methods (L231, L1571-1587); non-volatile `cResumeFromCrash` (L226). | Delete dead methods & field; mark `cResumeFromCrash` as `volatile`. (`cTrackingMode` is already `volatile`). | `TrainingApplication.java:226,231,1571-1587,1599` |
| `MainActivityWithNavigation.kt` | Dead `checkBatteryOptimizations()` (L498) and `showInstallANTShitDialog()` (L1103); deprecated `FLAG_SHOW_WHEN_LOCKED` (L642, 645). | Delete dead methods; modernize window lock flag via `setShowWhenLocked` for API 27+ with legacy fallback. | `MainActivityWithNavigation.kt:498,642,645,1103` |
| Dead Dialogs & Views | `SelectSportTypeDialog.java`, `MultiSelectionSpinner.java`, `DeviceIdAndNameArrayAdapter.java`, `InstallANTShitDialog.java` have 0 callers. | Delete dead Java source files; clean `lint-baseline.xml` for `MultiSelectionSpinner`. | `SelectSportTypeDialog.java`, `MultiSelectionSpinner.java`, `DeviceIdAndNameArrayAdapter.java`, `InstallANTShitDialog.java` |
| `AntStartupCheckContractTest.kt` | Contract test uses `indexOf("private fun checkBatteryOptimizations()")` as end-boundary for `onCreate()`. | Update test boundary to `indexOf("private fun observeNavigationEvents()")`. | `AntStartupCheckContractTest.kt:53` |
| Compose UI Screens | Top-level `@Preview` composables default to `public` in production bytecode. | Tighten top-level preview composables to `private`. | `ControlTrackingButton.kt`, `ControlTrackingScreen.kt`, `SensorGridScreen.kt`, etc. |

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Delete confirmed dead methods (`todo`, `setIsSegmentListUpdating`, `checkBatteryOptimizations`, `showInstallANTShitDialog`) and fields (`mSegmentListUpdating`).
  * Delete confirmed dead classes (`SelectSportTypeDialog`, `MultiSelectionSpinner`, `DeviceIdAndNameArrayAdapter`, `InstallANTShitDialog`) and purge baseline lint entry.
  * Modernize `FLAG_SHOW_WHEN_LOCKED` in `MainActivityWithNavigation` with API 27+ `setShowWhenLocked`.
  * Ensure memory visibility by making `cResumeFromCrash` `volatile`.
  * Tighten Compose `@Preview` composables to `private` across UI screens.
  * Add automated contract/unit test verifying dead code excision and API modernization.
* **Out-of-Scope Non-Goals**:
  * Do NOT perform full-scale architectural decomposition of `TrainingApplication`'s 100+ preferences into a new `SettingsRepository` (massive blast radius touching 500+ call sites, inappropriate for `[Low]` ticket).
  * Do NOT rewrite `TrackerService` or `WorkoutRepository` core tracking algorithms.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **REQ-ARC-003** (*God Class Splitting, Dead Code Elimination, Thread-Safe Visibility & API Modernization*): Net-new clean code requirement under Epic `ATT-431`.
* **Chesterton's Fence Invariants**:
  1. `AntStartupCheckContractTest.kt` relies on `MainActivityWithNavigation` method boundaries. Replacing the delimiter with `observeNavigationEvents` preserves 100% of the cold-start assertion contract without depending on dead code.
  2. Battery optimization checks are actively performed in modern Compose UI (`PermissionOnboardingSheet.kt`, `ControlTrackingScreen.kt`) and `ProcessExitReasonHelper.kt`; removing the dead activity method carries zero functional regression.
  3. `cResumeFromCrash` volatile modifier ensures immediate cross-thread visibility between background service intents and UI resume dialogs without changing lock semantics.

---

## 5. Architectural Strategy & High-Level Solution
1. **Dead Code & Class Removal**: Safely delete dead methods, unused fields, and unreferenced classes.
2. **Boundary Adjustment**: Update `AntStartupCheckContractTest` boundary string cleanly.
3. **Visibility & Concurrency Hardening**: Add `volatile` to `cResumeFromCrash`; restrict `@Preview` functions to `private`.
4. **API Modernization**: Implement version-branched `setShowWhenLocked` in `MainActivityWithNavigation`.
5. **Contract Verification**: Author `DeadCodeEliminationContractTest.kt` asserting zero presence of excised classes and methods.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Cold start, startup permissions, and tracking resume flows remain completely unbroken.
  2. Screen locking behavior (`NoUnlocking`) behaves identically across API 24 through 35+.
  3. 100% clean-room test pass rate across all existing unit test suites (`./gradlew testDebugUnitTest`).
* **Risk Rating**: **LOW**. Pure dead code excision, visibility tightening, and non-breaking API modernization with zero architectural or database schema regressions.
