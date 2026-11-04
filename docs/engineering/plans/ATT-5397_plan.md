# Stage 3: Implementation Plan - ATT-5397: Automatic Tier-Matched Launcher Icon and Removal of SupporterQuickIconPickerDialog

**Ticket**: [ATT-5397](https://rainerblind.atlassian.net/browse/ATT-5397)  
**Parent Epic**: None (Standalone)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5397`  
**Requirement Mapping**: `REQ-UI-445` (`REQ-UI-445.2`, `REQ-UI-445.3`, `REQ-UI-445.4`)  
**Test Mapping**: `TST-UI-445` (`TST-UI-445.2`, `TST-UI-445.3`, `TST-UI-445.4`, `TST-UI-445.5`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
User feedback from Post-Sprint 2026-45.5 review directed that homescreen launcher icons and the navigation drawer header icon must automatically match the athlete's effective supporter tier (`effectiveTier`), retiring manual selection and `SupporterQuickIconPickerDialog`. This plan decomposes the atomic migration into four verifiable steps.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-445.2` (Automatic launcher icon tier synchronization), `REQ-UI-445.3` (Cockpit emblem non-modal interaction), `REQ-UI-445.4` (Drawer header tier icon resolution).
* **Test Mapping**: `TST-UI-445.2` (`LauncherIconManagerTest.kt`), `TST-UI-445.3` (`SupporterBadgeContractTest.kt`, `SensorStatusTest.kt`), `TST-UI-445.4` (`AppNavigationDrawerTest.kt`), `TST-UI-445.5` (`TranslationParityTest.kt`, `verify_requirement_governance.py`).

---

## 3. System Invariants & Preserved Behavior
1. **In-Ride Deferred Activation Invariant**: Component switching via `PackageManager.DONT_KILL_APP` executes strictly during background `onStop` when `!TrainingApplication.isTracking()`.
2. **Free Tier Invariant**: Non-supporters unconditionally retain `MainActivityStandard`.
3. **Artwork & Brand Color Fidelity**: 100% preservation of Cervélo P3C and stopwatch vector paths and safe-zone containment ($33.0 \le r \le 34.5\text{dp}$).
4. **Code Lock (Rule 4)**: Zero production code edits until Gate 3 is approved.

---

## 4. Proposed Architectural Changes
### Component 1: `LauncherIconManager.kt` & `SettingsDataStore.kt`
* Inject `ProFeatureManager` into `LauncherIconManager`. In `applyDesiredLauncherIcon()`, resolve `targetVariant = LauncherIconVariant.fromTier(proFeatureManager.effectiveTier.value)`.
* Retire `KEY_SELECTED_LAUNCHER_ICON`, `selectedLauncherIcon`, and `setSelectedLauncherIcon` from `SettingsDataStore.kt`.

### Component 2: Retire `SupporterQuickIconPickerDialog.kt` & Strings
* Delete `SupporterQuickIconPickerDialog.kt`.
* Remove `supporter_quick_picker_title` and `supporter_quick_picker_thank_you` from all 9 locale `strings.xml` files.

### Component 3: Call Site Streamlining (`SensorStatus`, `AppNavigationDrawer`, `SupporterScreen`, `SupporterDebugSection`)
* In `SensorStatus.kt`: Remove `showSupporterDialog` and dialog invocation; emblem renders as non-modal status indicator or optional direct navigation trigger (`onNavigateToSupporter`).
* In `AppNavigationDrawer.kt`: Compute `activeVariant = LauncherIconVariant.fromTier(effectiveTier)`; remove `showQuickPicker` and `.clickable { showQuickPicker = true }`.
* In `SupporterScreen.kt` & `SupporterDebugSection.kt`: Remove icon picker trigger buttons and dialog state.

### Component 4: Test Suite Updates
* Update `LauncherIconManagerTest.kt` to verify automatic tier mapping from `ProFeatureManager.effectiveTier`.
* Update `SupporterBadgeContractTest.kt` to assert dialog and string retirement and tier-matched drawer/cockpit binding.

### UI Consistency (Rule 23)
* **Reused components**: Existing 24dp Cockpit emblem, standard 46dp `DrawerHeader` icon.
* **Theme tokens**: `MaterialTheme.colorScheme`, `TTColor`. New one-off styles: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Wire Automatic Tier Mapping in `LauncherIconManager` & Clean `SettingsDataStore`
* Update `LauncherIconManager.kt` to bind to `ProFeatureManager.effectiveTier`. Remove manual icon preference keys in `SettingsDataStore.kt`.

### Step 2: Delete `SupporterQuickIconPickerDialog.kt` & Retire Strings Across 9 Locales
* Remove `SupporterQuickIconPickerDialog.kt` and delete `supporter_quick_picker_*` entries from `values/` and all 8 `values-<locale>/` files.

### Step 3: Streamline Call Sites in Cockpit, Navigation Drawer, and Settings Screens
* Remove picker references from `SensorStatus.kt`, `AppNavigationDrawer.kt`, `SupporterScreen.kt`, and `SupporterDebugSection.kt`.

### Step 4: Update Automated Tests & Run Verification
* Update `LauncherIconManagerTest.kt` and `SupporterBadgeContractTest.kt`.
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.branding.*" --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.i18n.TranslationParityTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: `LauncherIconManagerTest`, `SupporterBadgeContractTest`, `TranslationParityTest`, and `verify_requirement_governance.py`.
* **Rollback**: `git checkout feature/ATT-5397 -- .` cleanly reverts working branch.
