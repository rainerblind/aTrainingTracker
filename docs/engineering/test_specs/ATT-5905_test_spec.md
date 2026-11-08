# Stage 2: Requirement & Test Specification - ATT-5905: Hardware Volume Buttons Cockpit Tab Cycling

**Ticket**: [ATT-5905](https://rainerblind.atlassian.net/browse/ATT-5905) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5905`  
**Requirement Mapping**: `REQ-TRK-054` (`REQ-TRK-054.1`..`REQ-TRK-054.5`) | **Test Spec ID**: `TST-TRK-054` (`TST-TRK-054.1`..`TST-TRK-054.5`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (`REQ-TRK-054`)

### 1.1 Functional Scope & Technical Invariants
* **Cyclic Tab Wrap-Around (`REQ-TRK-054.1`)**: `TrackingTabsPageResolver.resolveNextPage(currentPage, pageCount, forward)` computes `(currentPage + 1) % pageCount` for forward (Volume Up) and `(currentPage - 1 + pageCount) % pageCount` for backward (Volume Down). Edge case `pageCount <= 1` returns 0 without throwing.
* **Window Key Interception & HUD Suppression (`REQ-TRK-054.2`)**: `MainActivityWithNavigation.dispatchKeyEvent` intercepts `KEYCODE_VOLUME_UP` and `KEYCODE_VOLUME_DOWN` when registered; consuming both `ACTION_DOWN` and `ACTION_UP` suppresses Android's system volume slider HUD.
* **Lifecycle-Bound Registration (`REQ-TRK-054.3`)**: `TrackingTabsScreen` registers the interceptor via `DisposableEffect` only in `ScreenMode.TRACKING` when enabled. In `CONFIGURATION` or other screens, keys control system volume.
* **Settings & Persistence (`REQ-TRK-054.4`)**: Add `"volumeKeysSwitchCockpitTabs"` (enabled by default) to `DEFAULT_DISPLAY_OPTIONS` and helper `volumeKeysSwitchCockpitTabs()`. Add toggle in `DisplaySettingsDialog.kt`.

### 1.2 Given-When-Then Acceptance Criteria
* **AC-1 (Forward & Wrap-Around)**: *Given* 3 tabs (0, 1, 2) on tab 2 in `ScreenMode.TRACKING`, *When* `Volume Up` is pressed, *Then* pager animates to tab 0 and system volume HUD is suppressed.
* **AC-2 (Backward & Wrap-Around)**: *Given* on tab 0 in `ScreenMode.TRACKING`, *When* `Volume Down` is pressed, *Then* pager animates to tab 2 and system volume HUD is suppressed.
* **AC-3 (Disabled/Non-Cockpit Neutrality)**: *Given* option disabled or in `ScreenMode.CONFIGURATION`, *When* volume keys are pressed, *Then* normal system audio volume changes occur.

### 1.3 9-Language Localization Parity (`REQ-TRK-054.5`)
| Locale | Title (`prefs_volume_keys_switch_cockpit_tabs_title`) | Subtitle (`prefs_volume_keys_switch_cockpit_tabs_summary`) |
| :--- | :--- | :--- |
| `values` (EN) | Hardware volume keys cycle tabs | Cycle Cockpit tabs with volume +/- in live tracking |
| `values-de` | Lautstärketasten für Cockpit-Tabs | Im Cockpit mit Lautstärke +/- zyklisch durch die Tabs blättern |
| `values-es` | Teclas de volumen para pestañas | Cambiar cíclicamente de pestaña en el cockpit con volumen +/- |
| `values-fr` | Touches de volume pour onglets | Faire défiler les onglets du cockpit avec volume +/- |
| `values-it` | Tasti volume per schede cockpit | Sfoglia ciclicamente le schede nel cockpit con volume +/- |
| `values-ja` | 音量ボタンでタブ切り替え | コックピットで音量+/-ボタンを使ってタブを循環切り替え |
| `values-nl` | Volumeknoppen voor cockpit-tabbladen | In het cockpit met volume +/- cyclisch door tabbladen bladeren |
| `values-pl` | Klawisze głośności do zakładek | Przełączaj zakładki w kokpicie za pomocą głośności +/- |
| `values-pt` | Botões de volume para abas | Alternar ciclicamente as abas no cockpit com volume +/- |

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-054`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-054.1` | `TST-TRK-054.1` | `TrackingTabsPageResolverTest` | Cyclic forward/backward index arithmetic, wrap-around, and `pageCount <= 1` bounds |
| `REQ-TRK-054.2` | `TST-TRK-054.3` | `MainActivityWithNavigation` | Window `dispatchKeyEvent` interception, consuming both `ACTION_DOWN` & `ACTION_UP` |
| `REQ-TRK-054.3` | `TST-TRK-054.3` | `TrackingTabsScreen` | `DisposableEffect` registration in `TRACKING` mode; inactive in `CONFIGURATION` / exit |
| `REQ-TRK-054.4` | `TST-TRK-054.2` | `DisplaySettingsTest` | `DEFAULT_DISPLAY_OPTIONS` contains key; `volumeKeysSwitchCockpitTabs()` default & mutation |
| `REQ-TRK-054.5` | `TST-TRK-054.4` | `TranslationParityTest` | 9-locale parity validation for toggle title and summary string keys |
| `REQ-TRK-054` | `TST-TRK-054.5` | Targeted Test Suite | `./gradlew testDebugUnitTest --tests "...ui.tracking.trackingtabs.*" --tests "...ui.settings.display.*"` |
