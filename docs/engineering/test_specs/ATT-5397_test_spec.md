# Stage 2: Requirement & Test Specification - ATT-5397: Automatic Tier-Matched Launcher Icon and Removal of SupporterQuickIconPickerDialog

**Ticket**: [ATT-5397](https://rainerblind.atlassian.net/browse/ATT-5397)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: None (Standalone)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5397`  
**Requirement Mapping**: `REQ-UI-445` (*Supporter Badges: Custom Launcher Icons, Cockpit Status Emblem, Navigation Drawer & Share Watermark*)  
**Test Spec ID**: `TST-UI-445`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-UI-445)

### 1.1 Problem Statement & Rationale
User feedback established that supporters expect the homescreen launcher icon and drawer header icon to automatically reflect their effective tier (`effectiveTier`) without manual selection via `SupporterQuickIconPickerDialog`, which is superfluous and removed.

### 1.2 Functional & Architectural Requirements
1. `LauncherIconManager` SHALL automatically resolve and apply the launcher icon alias matching `ProFeatureManager.effectiveTier` via `LauncherIconVariant.fromTier(effectiveTier)` (`FREE -> STANDARD`, `PIONEER -> PIONEER`, `BRONZE -> BRONZE`, `SILVER -> SILVER`, `GOLD -> GOLD`, `DIAMOND -> DIAMOND`).
2. Component changes via `PackageManager.setComponentEnabledSetting` with `DONT_KILL_APP` SHALL remain deferred to `ProcessLifecycleOwner.onStop` while `!TrainingApplication.isTracking()`.
3. `SupporterQuickIconPickerDialog.kt`, its localized strings (`supporter_quick_picker_*`), and manual icon selection in `SettingsDataStore.kt` SHALL be completely retired.
4. Tapping the Cockpit emblem in `SensorStatus.kt` and the Drawer header in `AppNavigationDrawer.kt` SHALL NOT trigger an icon picker dialog.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Automatic Tier Binding)**:
  * *Given* an athlete whose `effectiveTier` is `GOLD` (or changes from `FREE` to `BRONZE`),
  * *When* the app transitions to background (`onStop`) while not tracking,
  * *Then* `LauncherIconManager` activates the matching `<activity-alias>` (`MainActivityGold` / `MainActivityBronze`) via `DONT_KILL_APP`.
* **Criterion 2 (Dialog Retirement & Direct Interaction)**:
  * *Given* an athlete in the Cockpit or Navigation Drawer,
  * *When* tapping the Supporter emblem or Drawer header,
  * *Then* no icon selection dialog is displayed.

### 1.4 System Invariants
* Zero disruption to active tracking: component alias switching NEVER executes while `isTracking == true`.
* Free athletes retain `MainActivityStandard`. 100% authentic Cervélo P3C / stopwatch colors and safe-zone compliance ($33.0 \le r \le 34.5\text{dp}$) preserved.

---

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-445.2`, `REQ-UI-445.3`, `REQ-UI-445.4` (`LauncherIconManager.kt`, `SensorStatus.kt`, `AppNavigationDrawer.kt`).
* `Historical Origin & Commit Trace`: Commit `46247b3d` (`ATT-1406`, Sprint `2026-45.4`).
* `Root Reason for Existing Formulation`: Manual picker was created assuming tiered supporters would customize among unlocked tiers. User feedback clarified that each tier automatically maps 1:1 to its launcher icon.
* `Preservation of Core Invariants`: Non-disruptive deferred activation on `onStop` when `!isTracking`, 100% brand artwork fidelity, and Free tier stability are strictly preserved.

---

## 2. Test Specification (TST-UI-445)

### Test Case 1: Automatic Tier Synchronization (`TST-UI-445.2`)
* **Scope**: Unit Test (`LauncherIconManagerTest.kt`)
* **Preconditions**: `effectiveTier` emits `SupporterTier.SILVER`; `isTracking` is false.
* **Action**: Trigger `applyDesiredLauncherIcon()` or `onStop`.
* **Expected Result**: `MainActivitySilver` is enabled (`COMPONENT_ENABLED_STATE_ENABLED`) with `DONT_KILL_APP`; all other aliases disabled.

### Test Case 2: Cockpit Emblem & Dialog Removal Contract (`TST-UI-445.3`)
* **Scope**: Contract Test (`SupporterBadgeContractTest.kt`, `SensorStatusTest.kt`)
* **Preconditions**: Verified source tree.
* **Action**: Audit `SensorStatus.kt`, `AppNavigationDrawer.kt`, `SupporterScreen.kt`, and `SupporterDebugSection.kt`.
* **Expected Result**: Zero references to `SupporterQuickIconPickerDialog`; `supporter_quick_picker_*` absent from all 9 `strings.xml`.

### Test Case 3: Localization Parity & Governance Audit (`TST-UI-445.5`)
* **Scope**: Localization & Governance Test (`TranslationParityTest.kt`, `verify_requirement_governance.py`)
* **Expected Result**: 100% parity across 9 locales, zero missing strings, governance check exits 0.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-445.2` | Unit | `LauncherIconManager.applyDesiredLauncherIcon` | `REQ-UI-445.2` | Specified |
| `TST-UI-445.3` | Contract | `SupporterBadgeContractTest` / UI callers | `REQ-UI-445.3`, `REQ-UI-445.4` | Specified |
| `TST-UI-445.5` | Localization | `TranslationParityTest` / `verify_requirement_governance.py` | `REQ-UI-445.5`, `REQ-PRO-022` | Specified |
