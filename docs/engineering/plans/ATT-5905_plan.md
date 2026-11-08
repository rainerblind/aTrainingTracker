# Stage 3: Implementation Plan - ATT-5905: Hardware Volume Buttons Cockpit Tab Cycling

**Ticket**: [ATT-5905](https://rainerblind.atlassian.net/browse/ATT-5905) | **Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5905`  
**Requirement Mapping**: `REQ-TRK-054` (`REQ-TRK-054.1`..`REQ-TRK-054.5`) | **Test Mapping**: `TST-TRK-054` (`TST-TRK-054.1`..`TST-TRK-054.5`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Description & Background
During outdoor activities in rain, cold weather, or with thick gloves, capacitive touch swipes to switch Cockpit tabs (`TrackingTabsScreen`) are unreliable or hazardous. Using hardware volume buttons (`KEYCODE_VOLUME_UP`, `KEYCODE_VOLUME_DOWN`) to cycle tabs with bidirectional cyclic wrap-around (`(page ± 1 + count) % count`) allows full navigation even when bike handlebar mounts block one physical button. The feature must be enabled by default, discoverable via a toggle in `DisplaySettingsDialog.kt`, localized in 9 languages, and completely suppress the Android system volume slider HUD by consuming both `ACTION_DOWN` and `ACTION_UP`.

---

## 2. Traceability & Requirements Mapping
* `REQ-TRK-054.1` -> `TST-TRK-054.1`: Cyclic wrap-around mathematics in `TrackingTabsPageResolver.resolveNextPage`.
* `REQ-TRK-054.2` -> `TST-TRK-054.3`: Window-level key interception in `MainActivityWithNavigation.dispatchKeyEvent` suppressing audio HUD.
* `REQ-TRK-054.3` -> `TST-TRK-054.3`: Lifecycle-bound registration in `TrackingTabsScreen` via `DisposableEffect` (active in `TRACKING` only).
* `REQ-TRK-054.4` -> `TST-TRK-054.2`: Preference persistence in `TrainingApplication.DEFAULT_DISPLAY_OPTIONS` and `DisplaySettingsDialog.kt`.
* `REQ-TRK-054.5` -> `TST-TRK-054.4`: 9-language localization parity (`prefs_volume_keys_switch_cockpit_tabs_title`, `..._summary`).

---

## 3. System Invariants & Preserved Behavior
1. **Non-Cockpit Neutrality**: When on non-Cockpit screens, in `ScreenMode.CONFIGURATION`, or when the setting is disabled, volume keys adjust system volume normally.
2. **Audio HUD Suppression**: Consuming both `ACTION_DOWN` and `ACTION_UP` returning `true` guarantees the Android volume slider HUD never flashes.
3. **Wrap-Around & Animation Invariants**: Boundary case `pageCount <= 1` safely resolves to `0`; rapid presses operate on `pagerState.targetPage` to prevent animation fighting.
4. **Existing Preferences Integrity**: Existing display options (`forcePortrait`, `keepScreenOn`, `noUnlocking`) and SharedPreferences schemas remain untouched.

---

## 4. Proposed Architectural Changes (SWE.2) & UI Consistency
* `TrackingTabsPageResolver.kt`: Add pure function `resolveNextPage(currentPage: Int, pageCount: Int, forward: Boolean): Int`.
* `TrainingApplication.java`: Add `"volumeKeysSwitchCockpitTabs"` to `DEFAULT_DISPLAY_OPTIONS` and getter `volumeKeysSwitchCockpitTabs()`.
* `MainActivityWithNavigation.kt`: Add `hardwareKeyInterceptor: ((KeyEvent) -> Boolean)?` and override `dispatchKeyEvent(event: KeyEvent): Boolean`.
* `TrackingTabsScreen.kt`: Register `hardwareKeyInterceptor` in `DisposableEffect` when in `ScreenMode.TRACKING` and setting is enabled; invoke `pagerState.animateScrollToPage()`.
* `DisplaySettingsDialog.kt`: Add subtitle support to `DisplayOptionToggle` and add toggle for `volumeKeysSwitchCockpitTabs`.
* **UI Consistency**: Reuses existing `DisplayOptionToggle` and `AppBottomSheetContent` in `DisplaySettingsDialog.kt` matching Material 3 typography and switch styling.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pure Domain Resolver & Unit Tests
* Files: `TrackingTabsPageResolver.kt`, `TrackingTabsPageResolverTest.kt`
* Action: Implement `resolveNextPage(...)` with cyclic wrap-around arithmetic and add test assertions (`REQ-TRK-054.1`, `TST-TRK-054.1`).

### Step 2: Settings Data Layer & Unit Tests
* Files: `TrainingApplication.java`, `DisplaySettingsTest.kt`
* Action: Add `"volumeKeysSwitchCockpitTabs"` to `DEFAULT_DISPLAY_OPTIONS`, implement `volumeKeysSwitchCockpitTabs()`, and verify in unit tests (`REQ-TRK-054.4`, `TST-TRK-054.2`).

### Step 3: Localization Parity
* Files: `app/src/main/res/values*/strings.xml` (all 9 locales)
* Action: Add `prefs_volume_keys_switch_cockpit_tabs_title` and `prefs_volume_keys_switch_cockpit_tabs_summary` with 100% parity (`REQ-TRK-054.5`, `TST-TRK-054.4`).

### Step 4: Display Settings UI Integration
* Files: `DisplaySettingsDialog.kt`
* Action: Add subtitle support to `DisplayOptionToggle` and compose the new toggle switch under general display options (`REQ-TRK-054.4`).

### Step 5: Activity Key Interception & TrackingTabs Integration
* Files: `MainActivityWithNavigation.kt`, `TrackingTabsScreen.kt`
* Action: Implement `dispatchKeyEvent` interception hook in `MainActivityWithNavigation` and lifecycle-bound `DisposableEffect` in `TrackingTabsScreen` (`REQ-TRK-054.2`, `REQ-TRK-054.3`, `TST-TRK-054.3`).

### Step 6: Regression Verification
* Action: Execute targeted package tests and verify zero regressions (`TST-TRK-054.5`).
