# Stage 3: Implementation Plan - ATT-4031: Split god classes and remove dead code

**Ticket**: [ATT-4031](https://rainerblind.atlassian.net/browse/ATT-4031)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4031`  
**Requirement Mapping**: `REQ-ARC-003`  
**Test Mapping**: `TST-ARC-003`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background
Independent code review identified structural debt across core classes: uncalled dead methods (`todo`, `setIsSegmentListUpdating`, `checkBatteryOptimizations`, `showInstallANTShitDialog`), unreferenced legacy classes (`SelectSportTypeDialog`, `MultiSelectionSpinner`, `DeviceIdAndNameArrayAdapter`, `InstallANTShitDialog`), missing `volatile` on static recovery state `cResumeFromCrash`, deprecated window lock flags (`FLAG_SHOW_WHEN_LOCKED`), and overexposed public `@Preview` composables.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-ARC-003` (`REQ-ARC-003.1` – `REQ-ARC-003.6`)
  * `REQ-ARC-003.1`: Excision of dead methods and state in `TrainingApplication.java` and `MainActivityWithNavigation.kt`.
  * `REQ-ARC-003.2`: Physical removal of dead dialog and view classes; purge `lint-baseline.xml` entry.
  * `REQ-ARC-003.3`: `volatile` visibility on `TrainingApplication.cResumeFromCrash`.
  * `REQ-ARC-003.4`: API 27+ `setShowWhenLocked` modernization with legacy fallback in `MainActivityWithNavigation.kt`.
  * `REQ-ARC-003.5`: Restrict top-level Compose `@Preview` composables to `private`.
  * `REQ-ARC-003.6`: Automated contract verification in `DeadCodeEliminationContractTest.kt`.
* **Test Mapping**: `TST-ARC-003` (`TST-ARC-003.1` – `TST-ARC-003.6`).

---

## 3. System Invariants & Preserved Behavior
1. **Startup & Lifecycle Integrity**: App startup, permissions, and background tracking resume flows remain 100% operational.
2. **Lock Screen Parity**: `NoUnlocking` behavior functions identically across API 24 through 35+.
3. **Contract Test Invariant**: `AntStartupCheckContractTest` maintains 100% cold-start assertion coverage with updated boundary.
4. **Zero Regressions**: 100% clean-room unit test pass rate across the full project test suite.

---

## 4. Proposed Architectural Changes
* **Component 1 (`TrainingApplication.java`)**: Delete `todo()`, `setIsSegmentListUpdating()`, `isSegmentListUpdating()`, and `mSegmentListUpdating`; declare `cResumeFromCrash` as `volatile`.
* **Component 2 (Dead Classes & Lint Baseline)**: Delete `SelectSportTypeDialog.java`, `MultiSelectionSpinner.java`, `DeviceIdAndNameArrayAdapter.java`, and `InstallANTShitDialog.java`; purge `MultiSelectionSpinner` issue from `app/lint-baseline.xml`.
* **Component 3 (`MainActivityWithNavigation.kt` & `AntStartupCheckContractTest.kt`)**:
  * Delete uncalled `checkBatteryOptimizations()` and `showInstallANTShitDialog()` along with dead dialog imports.
  * In `applyDisplaySettings()`, use `setShowWhenLocked(true/false)` for API 27+ with legacy `FLAG_SHOW_WHEN_LOCKED` fallback.
  * In `AntStartupCheckContractTest.kt`, retarget end-boundary to `observeNavigationEvents`.
* **Component 4 (Compose Previews)**: Tighten top-level `@Preview` composables in UI files to `private`.
* **UI Consistency**: No visual UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: TrainingApplication Cleanup & Volatility
* Edit `TrainingApplication.java`: Remove `todo()`, `setIsSegmentListUpdating`, `isSegmentListUpdating`, and `mSegmentListUpdating`. Mark `cResumeFromCrash` as `protected static volatile boolean`.

### Step 2: Dead Class Excision & Baseline Purge
* Delete `SelectSportTypeDialog.java`, `MultiSelectionSpinner.java`, `DeviceIdAndNameArrayAdapter.java`, `InstallANTShitDialog.java`.
* Purge `MultiSelectionSpinner#performClick` entry in `app/lint-baseline.xml`.

### Step 3: MainActivityWithNavigation Excision & API Modernization
* Edit `MainActivityWithNavigation.kt`: Delete `checkBatteryOptimizations` and `showInstallANTShitDialog`. Branch on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1` calling `setShowWhenLocked(...)` in `applyDisplaySettings`.
* Edit `AntStartupCheckContractTest.kt`: Update inspection boundary delimiter to `observeNavigationEvents`.

### Step 4: Compose Preview Visibility Scoping
* Tighten public top-level `@Preview` composables across UI screens (`ControlTrackingButton.kt`, `ControlTrackingScreen.kt`, etc.) to `private`.

### Step 5: Contract Verification Suite
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted contract tests, adjacent suites (`AntStartupCheckContractTest`), full test suite, and requirement governance script.
* **Rollback**: Atomic git revert on branch `feature/ATT-4031`.
