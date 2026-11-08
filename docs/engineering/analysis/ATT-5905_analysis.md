# Stage 1 Analysis: ATT-5905 - TrackingTabsScreen & DisplaySettingsDialog: Use hardware volume buttons to cycle Cockpit tabs with wrap-around

**Ticket**: [ATT-5905](https://rainerblind.atlassian.net/browse/ATT-5905)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5905`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
During outdoor workouts (cycling, running) in rain, cold weather, or with thick gloves, capacitive touch swipes to navigate between Cockpit tabs (`Standard`, `Karte`, `Runden`, `Live Segments`) in `TrackingTabsScreen` are unreliable or hazardous while riding. Athletes require physical hardware button switching akin to dedicated bike computers. Smartphones provide physical side keys (`KEYCODE_VOLUME_UP`, `KEYCODE_VOLUME_DOWN`) that can cycle Cockpit tabs. Additionally, if an out-front handlebar mount blocks one volume button, bidirectional cyclic wrap-around (`(page ± 1 + count) % count`) guarantees full tab reachability using a single physical button.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
* **Architectural Gap**: Key handling in Jetpack Compose (`Modifier.onKeyEvent`) requires an active focus target; touching or scrolling non-focusable components drops focus, silently dropping hardware key events. Furthermore, Compose-level handling runs after Window key dispatch, failing to reliably suppress Android's system volume slider HUD.
* **Independent Peer Review & Verification**:
  - **Window-Level Key Interception**: Overriding `dispatchKeyEvent(event: KeyEvent): Boolean` in `MainActivityWithNavigation` intercepts `KEYCODE_VOLUME_UP` and `KEYCODE_VOLUME_DOWN` before focus delegation or system volume processing. Consuming both `ACTION_DOWN` and `ACTION_UP` completely prevents the system audio HUD from appearing.
  - **Lifecycle-Bound Delegation via `DisposableEffect`**: `TrackingTabsScreen` installs an interceptor in `MainActivityWithNavigation` only while active in `ScreenMode.TRACKING` and when the setting is enabled. Navigating away, switching to `ScreenMode.CONFIGURATION`, or disabling the setting unregisters the interceptor, cleanly restoring default volume control.
  - **Pure Cyclic Calculation in `TrackingTabsPageResolver`**: Exposing `resolveNextPage(currentPage: Int, pageCount: Int, forward: Boolean): Int` provides deterministic `(curr + 1) % count` and `(curr - 1 + count) % count` wrap-around calculations isolated from UI state.
  - **Settings Integration**: Persisting `"volumeKeysSwitchCockpitTabs"` in `TrainingApplication.DEFAULT_DISPLAY_OPTIONS` (enabled by default) and adding a toggle in `DisplaySettingsDialog.kt` provides discoverability with 9-language localization parity.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  - Implement `resolveNextPage()` in `TrackingTabsPageResolver.kt` with pure unit tests in `TrackingTabsPageResolverTest.kt`.
  - Add `volumeKeysSwitchCockpitTabs` to `TrainingApplication.DEFAULT_DISPLAY_OPTIONS` and getter `volumeKeysSwitchCockpitTabs()`, verified in `DisplaySettingsTest.kt`.
  - Add a localized toggle with title and subtitle to `DisplaySettingsDialog.kt` across all 9 languages (`res/values*/strings.xml`).
  - Wire key interception in `MainActivityWithNavigation.kt` (`dispatchKeyEvent`) and `TrackingTabsScreen.kt` (`DisposableEffect`) animating `pagerState` smoothly.
* **Out-of-Scope Non-Goals**: Modifying volume key behavior outside Cockpit `ScreenMode.TRACKING`, intercepting non-volume hardware keys (e.g., power or Bixby buttons), or altering audio service playback streams.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Refined Requirements**: Extends `REQ-SET-052` (*Display Settings Modal Dialog & Preferences*) and `REQ-UI-435` (*TrackingTabs Pager Resolution*). Formulates net-new `REQ-TRK-048` (*Hardware Volume Keys Cockpit Tab Cycling with Cyclic Wrap-Around*).
* **Historical Trace**: `ATT-261` / `ATT-1016` (DisplaySettings modal), `ATT-4838` (`TrackingTabsPageResolver`), and `ATT-5879` (Cockpit layout refinements).
* **Root Reason for Existing Formulation**: Volume keys were left unintercepted because Compose views do not request key events by default, leaving volume handling to Android's default audio stream manager.
* **Preservation of Core Invariants**: Existing display preferences (`forcePortrait`, `keepScreenOn`, `noUnlocking`) remain unchanged; volume buttons function normally on all other screens; 9-language localization parity is maintained.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Hardware volume buttons MUST function normally on all non-Cockpit screens and in `ScreenMode.CONFIGURATION`; both `ACTION_DOWN` and `ACTION_UP` MUST be consumed in `TRACKING` mode to avoid audio HUD flicker; cyclic wrap-around MUST handle `pageCount <= 1` gracefully without IndexOutOfBoundsException.
* **Risk Rating**: **LOW** (Scoped to Cockpit `ScreenMode.TRACKING` with explicit lifecycle unregistration; fallback to default key dispatch).
