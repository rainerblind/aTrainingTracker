# Stage 2: Requirement & Test Specification - ATT-4031: Split god classes and remove dead code

**Ticket**: [ATT-4031](https://rainerblind.atlassian.net/browse/ATT-4031)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4031`  
**Requirement Mapping**: `REQ-ARC-003` (*God Class Splitting, Dead Code Elimination, Thread-Safe Visibility & API Modernization*)  
**Test Spec ID**: `TST-ARC-003`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-ARC-003`)

### 1.1 Problem Statement & Rationale
Whole-app code review identified unreferenced legacy classes (`SelectSportTypeDialog`, `MultiSelectionSpinner`, `DeviceIdAndNameArrayAdapter`, `InstallANTShitDialog`), uncalled dead methods (`todo`, `setIsSegmentListUpdating`, `checkBatteryOptimizations`, `showInstallANTShitDialog`), missing `volatile` on static recovery state `cResumeFromCrash`, deprecated window lock flags (`FLAG_SHOW_WHEN_LOCKED`), and overexposed public `@Preview` composables.

### 1.2 Functional & Architectural Requirements
1. **Dead Method & State Excision (`REQ-ARC-003.1`)**: Dead methods `todo()`, `setIsSegmentListUpdating()`, `isSegmentListUpdating()`, and field `mSegmentListUpdating` in `TrainingApplication.java` SHALL be excised. Dead methods `checkBatteryOptimizations()` and `showInstallANTShitDialog()` in `MainActivityWithNavigation.kt` SHALL be excised.
2. **Dead Legacy Class Removal (`REQ-ARC-003.2`)**: Unreferenced classes `SelectSportTypeDialog.java`, `MultiSelectionSpinner.java`, `DeviceIdAndNameArrayAdapter.java`, and `InstallANTShitDialog.java` SHALL be deleted from disk; lint baseline entries SHALL be purged.
3. **Thread-Safe Visibility (`REQ-ARC-003.3`)**: In `TrainingApplication.java`, static field `cResumeFromCrash` SHALL be declared `volatile` (`public static volatile boolean cResumeFromCrash`), guaranteeing cross-thread memory visibility between service intent dispatchers and UI dialogs.
4. **Lock Screen API Modernization (`REQ-ARC-003.4`)**: In `MainActivityWithNavigation.kt`, deprecated `FLAG_SHOW_WHEN_LOCKED` SHALL be replaced with `setShowWhenLocked(true/false)` for API 27+ with legacy fallback for API 24-26.
5. **Compose Preview Visibility Scoping (`REQ-ARC-003.5`)**: Top-level `@Preview` composables across UI screens SHALL be restricted to `private` visibility.
6. **Programmatic Contract Verification (`REQ-ARC-003.6`)**: Per `docs/coding_standards.md` §5.4 (`REQ-TST-002`), excision and volatility SHALL be verified via programmatic reflection (`Class.forName`, `Modifier.isVolatile`, `declaredFields`, `declaredMethods`) and file existence checks in `DeadCodeEliminationContractTest.kt`.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-ARC-003` under Epic `ATT-431` (*Clean Code*), extending `REQ-ARC-001` and `REQ-ARC-002`. Targets `TrainingApplication.java`, `MainActivityWithNavigation.kt`, and excised dialog classes.
* **Historical Origin & Commit Trace**: Whole-app independent code review (`feature/ATT-3921` @ `0234bfc0`, Oct 2026). Dead dialogs originated from 2014-2017 ANT+ radio and Eclipse migrations; `AntStartupCheckContractTest` was anchored to `checkBatteryOptimizations`.
* **Root Reason for Existing Formulation**: Code was retained during Jetpack Compose migrations to avoid unintended side effects or breaking test boundaries.
* **Preservation of Core Invariants**: Battery optimizations active in Compose onboarding (`PermissionOnboardingSheet.kt`) remain intact; `AntStartupCheckContractTest` boundary is redirected to `observeNavigationEvents`; lock-screen functionality preserved; 100% clean-room test pass rate.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Dead Classes Excision)**: *Given* excised classes, *When* probed, *Then* files do not exist and `Class.forName` throws `ClassNotFoundException`.
* **Criterion 2 (Dead Methods Excision)**: *Given* `TrainingApplication` and `MainActivityWithNavigation`, *When* inspected via reflection, *Then* dead methods are absent.
* **Criterion 3 (Volatile Visibility)**: *Given* `TrainingApplication.cResumeFromCrash`, *When* inspected via reflection, *Then* `Modifier.isVolatile(field.modifiers)` is `true`.
* **Criterion 4 (Lock Screen API)**: *Given* `MainActivityWithNavigation`, *When* configured on API 27+, *Then* `setShowWhenLocked` is used without deprecated flags.
* **Criterion 5 (Private Previews)**: *Given* Compose UI screens, *When* inspected, *Then* top-level `@Preview` composables are `private`.

---

## 2. Test Specification (`TST-ARC-003`)

### Test Cases
* `TST-ARC-003.1` (**Dead Legacy Class Elimination Verification**): `DeadCodeEliminationContractTest.kt` verifies via reflection (`Class.forName` throwing `ClassNotFoundException`) and file existence that dead classes are purged.
* `TST-ARC-003.2` (**TrainingApplication Excision & Volatile State**): Verifies via reflection absence of `todo()`, segment updating methods, and `mSegmentListUpdating`, and asserts `Modifier.isVolatile(cResumeFromCrash.modifiers) == true`.
* `TST-ARC-003.3` (**MainActivityWithNavigation Dead Methods & Lock Screen API**): Verifies via reflection absence of `checkBatteryOptimizations()` and `showInstallANTShitDialog()`, and verifies `setShowWhenLocked` API branch.
* `TST-ARC-003.4` (**AntStartupCheckContractTest Invariant Audit**): Verifies `AntStartupCheckContractTest` passes with boundary retargeted to `observeNavigationEvents`.
* `TST-ARC-003.5` (**Compose Preview Visibility Audit**): Verifies that top-level `@Preview` composables across screens have `private` visibility.
* `TST-ARC-003.6` (**Clean-Room Regression**): `./gradlew testDebugUnitTest` and `TranslationParityTest` achieve 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-ARC-003.1` | Contract | `SelectSportTypeDialog`, `MultiSelectionSpinner`, `InstallANTShitDialog` | `REQ-ARC-003.2` | In Progress (ATT-4031) |
| `TST-ARC-003.2` | Contract/Unit | `TrainingApplication.java` | `REQ-ARC-003.1`, `REQ-ARC-003.3` | In Progress (ATT-4031) |
| `TST-ARC-003.3` | Contract/Unit | `MainActivityWithNavigation.kt` | `REQ-ARC-003.1`, `REQ-ARC-003.4` | In Progress (ATT-4031) |
| `TST-ARC-003.4` | Contract | `AntStartupCheckContractTest.kt` | `REQ-ARC-003.1` | In Progress (ATT-4031) |
| `TST-ARC-003.5` | Policy | Compose UI screens (`ControlTrackingButton.kt`, etc.) | `REQ-ARC-003.5` | In Progress (ATT-4031) |
| `TST-ARC-003.6` | Regression | `./gradlew testDebugUnitTest`, `TranslationParityTest.kt` | `REQ-ARC-003.6` | In Progress (ATT-4031) |
