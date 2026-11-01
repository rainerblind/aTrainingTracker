# Stage 2: Requirement & Test Specification - ATT-4836: Own section for auto pause in settings

**Ticket**: [ATT-4836](https://rainerblind.atlassian.net/browse/ATT-4836)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Active Sprint**: `2026-44.1`  
**Target Release**: `V4.9.44`  
**Author**: AI Agent 2 (Specification Engineer)  
**Date**: 2026-11-01  

---

## 1. Traceability Matrix

| Requirement | Test Spec | Target Component | Description |
| :--- | :--- | :--- | :--- |
| `REQ-SET-080.1` | `TST-SET-080.1` | `NavRoutes.kt`, `AppNavigationDrawer.kt`, `ids.xml` | Drawer entry below Sport Types and route bidirectional mapping |
| `REQ-SET-080.2` | `TST-SET-080.2` | `AutoPauseTabsScreen.kt`, `AutoPauseViewModel.kt` | Full-screen 3-tab cockpit (Bike, Run, Other) with collapsing top bar |
| `REQ-SET-080.3` | `TST-SET-080.2` | `TuningPreferencesDataStore.kt` | Sport toggles, sliders, and reset-to-defaults with instant persistence |
| `REQ-SET-080.4` | `TST-SET-080.3` | `SensorsGpsFilterSection.kt`, `AdvancedTuningScreen.kt` | Retirement of legacy auto-pause controls from Advanced Tuning |
| `REQ-SET-080.5` | `TST-SET-080.4` | `values*/strings.xml` | 9-language translation parity for `auto_pause_title` |

---

## 2. Requirement Specification (`REQ-SET-080`)

1. **`REQ-SET-080.1` (Drawer Entry & Route)**: The system SHALL provide `drawer_auto_pause` in `AppNavigationDrawer.kt` directly below `drawer_sport_types` with icon `ic_pause_circle`, mapped bidirectionally to `NavRoutes.AUTO_PAUSE`.
2. **`REQ-SET-080.2` (Tabbed Cockpit)**: The system SHALL render `AutoPauseTabsScreen` featuring tabs for Cycling (`BSportType.BIKE`), Running (`BSportType.RUN`), and Other Sports (`BSportType.UNKNOWN`), a top app bar with hamburger drawer button, and a reset-to-defaults action (`R.string.reset_to_defaults`).
3. **`REQ-SET-080.3` (Controls & Persistence)**: Each tab SHALL provide an enable toggle, speed threshold slider, and debounce slider backed by `TuningPreferencesDataStore` with instant persistence. Resetting on a tab SHALL restore that sport's defaults.
4. **`REQ-SET-080.4` (Retirement from Advanced Tuning)**: The system SHALL excise auto-pause controls from `SensorsGpsFilterSection.kt`, `AdvancedTuningScreen.kt`, and `AdvancedTuningDialog.kt` to ensure a single canonical configuration surface.
5. **`REQ-SET-080.5` (9-Language Parity)**: All new string resources SHALL be localized with 100% parity across all 9 supported locales.

---

## 3. Test Specification (`TST-SET-080`)

1. **`TST-SET-080.1` (Drawer & Route Resolution Contract)**:
   - Verify `NavRoutes.toRoute(R.id.drawer_auto_pause) == NavRoutes.AUTO_PAUSE`.
   - Verify `NavRoutes.toDrawerItemId(NavRoutes.AUTO_PAUSE) == R.id.drawer_auto_pause`.
   - Verify `AppNavigationDrawer` places `drawer_auto_pause` directly below `drawer_sport_types`.
2. **`TST-SET-080.2` (ViewModel State & DataStore Persistence)**:
   - Verify `AutoPauseViewModel` exposes reactive `tuningConfig: StateFlow<TuningConfig>`.
   - Verify mutating enable, speed threshold, and debounce persists to `TuningPreferencesDataStore`.
   - Verify calling reset restores default values for the active sport.
3. **`TST-SET-080.3` (Retirement Contract)**:
   - Verify `SensorsGpsFilterSection.kt` no longer declares auto-pause parameters.
   - Verify `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` no longer bind auto-pause sliders.
4. **`TST-SET-080.4` (Localization Parity)**:
   - Verify `auto_pause_title` exists in all 9 `strings.xml` files.

---

## 4. Invariants & Acceptance Criteria

- **Given** an athlete in the navigation drawer, **When** tapping *Auto-Pause*, **Then** `AutoPauseTabsScreen` opens to the tabbed cockpit.
- **Given** a sport tab, **When** adjusting threshold or debounce, **Then** settings persist immediately to `TuningPreferencesDataStore`.
- **Invariants**: DataStore preference keys, default thresholds, and `TrackerService` auto-pause logic remain unchanged.
