# Stage 1 Analysis: ATT-5397 - Automatic Tier-Matched Launcher Icon and Removal of SupporterQuickIconPickerDialog

**Ticket**: [ATT-5397](https://rainerblind.atlassian.net/browse/ATT-5397)  
**Parent Epic**: None (Standalone)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5397`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
Post-Sprint 2026-45.5 review user feedback (*"Wenn man auf das Supporter icon am oberen rechten Rand klickt, kommt ein dialog zur Auswahl des start icons. Das möchte ich so nicht. Jeder Supporter erhält immer das entsprechende Icon. Der Dialog ist damit eigentlich überflüssig."*) directed that the homescreen launcher icon must automatically match the athlete's effective supporter tier (`effectiveTier`), rendering manual icon selection via `SupporterQuickIconPickerDialog` obsolete.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Architectural Evolution RCA**: In `ATT-1406`, `SupporterQuickIconPickerDialog` was created to allow tiered supporters to pick between all unlocked icons, requiring `KEY_SELECTED_LAUNCHER_ICON` in `SettingsDataStore`. This added unnecessary interaction friction and UI complexity.
* **Independent Peer Review & Verification**:
  * Audited `LauncherIconManager.kt`: Resolves target variant from `settingsDataStore.selectedLauncherIcon.first()`. Can directly synchronize with `proFeatureManager.effectiveTier.value` via `LauncherIconVariant.fromTier(effectiveTier)`, preserving deferred `DONT_KILL_APP` on `ProcessLifecycleOwner.onStop` when `!TrainingApplication.isTracking()`.
  * Audited `SensorStatus.kt`: Top-right supporter emblem opens `SupporterQuickIconPickerDialog`. Removing the dialog eliminates `showSupporterDialog` and adapts the emblem to an optional direct navigation trigger (`onNavigateToSupporter`) or non-modal status indicator.
  * Audited `AppNavigationDrawer.kt`: Header currently checks `selectedVariant` vs `LauncherIconVariant.fromTier(effectiveTier)`. Simplifies directly to `LauncherIconVariant.fromTier(effectiveTier)` and removes `.clickable { showQuickPicker = true }`.
  * Audited `SupporterScreen.kt` & `SupporterDebugSection.kt`: Both contain legacy buttons launching `SupporterQuickIconPickerDialog`. Must be cleaned up alongside string resources.
  * Audited `SettingsDataStore.kt`: `KEY_SELECTED_LAUNCHER_ICON`, `selectedLauncherIcon`, and `setSelectedLauncherIcon` can be safely retired.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Automatically synchronize `LauncherIconManager` with `ProFeatureManager.effectiveTier` via `LauncherIconVariant.fromTier(effectiveTier)`.
  * Retire `SupporterQuickIconPickerDialog.kt`, string resources `supporter_quick_picker_*` (9 locales), and `selectedLauncherIcon` from `SettingsDataStore.kt`.
  * Remove dialog invocation from `SensorStatus.kt` (emblem becomes non-modal or navigates to supporter screen), `AppNavigationDrawer.kt`, `SupporterScreen.kt`, and `SupporterDebugSection.kt`.
  * Update `SupporterBadgeContractTest.kt` and `LauncherIconManagerTest.kt` to validate automatic tier binding.
* **Out-of-Scope Non-Goals**:
  * Changes to the 6 `<activity-alias>` declarations in `AndroidManifest.xml` (all 6 variants remain active targets).
  * Modifications to adaptive icon artwork, drawables, mipmaps, or safe-zone geometries.
* **Complexity Rating**: **2 SP (Standard)** (Clean component retirement and reactive tier wiring).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-445.2`, `REQ-UI-445.3`, `REQ-UI-445.4` (Launcher icon switching, cockpit emblem, drawer header).
* **Historical Origin & Commit Trace**: Commit `46247b3d` (`ATT-1406`, Sprint `2026-45.4`).
* **Root Reason for Existing Formulation**: Manual picker was introduced under the assumption that users might prefer lower-tier icons over higher ones. User feedback confirmed each supporter strictly wants their matching tier icon automatically.
* **Preservation of Core Invariants**:
  1. Non-disruptive deferred activation (`PackageManager.DONT_KILL_APP` on `onStop` while `!isTracking`).
  2. 100% authentic Cervélo P3C and stopwatch colors and safe-zone containment ($33.0 \le r \le 34.5\text{dp}$).
  3. 24dp Cockpit emblem symmetry and 46dp Drawer header visual alignment.

---

## 5. Architectural Strategy & High-Level Solution
* Wire `LauncherIconManager` to observe or query `ProFeatureManager.effectiveTier` during deferred background activation.
* Remove `SupporterQuickIconPickerDialog.kt` and associated localized strings across all 9 languages.
* Streamline `AppNavigationDrawer.DrawerHeader` to unconditionally use `LauncherIconVariant.fromTier(effectiveTier)` without click-to-pick.
* Streamline `SensorStatus.kt` emblem to status-only representation (or optional navigation to Supporter screen).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. In-ride non-disruption: No component alias updates execute while `TrainingApplication.isTracking() == true`.
  2. Free tier preservation: Free athletes remain on `LauncherIconVariant.STANDARD` (`MainActivityStandard`).
  3. 9-language localization parity maintained upon string retirement.
* **Risk Rating**: **LOW** (Dead code removal, simplification of state derivation, zero database schema or sensor recording impact).
