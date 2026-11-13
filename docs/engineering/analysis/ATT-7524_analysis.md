# Stage 1 Analysis: ATT-7524 - Default ZoneDisplayOptions to Background + Left & Right Bars and Persist HRV Zone Display Options

**Ticket**: [ATT-7524](https://rainerblind.atlassian.net/browse/ATT-7524)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7524` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
* **Asymmetric Default `Zonendarstellung`**: `ZoneDisplayOptions` currently defaults to `showBackground = true`, `showLeftBar = true`, `showRightBar = false`, `showTextColor = false`. Symmetrical framing with both left and right bars (`showRightBar = true`) alongside tinted background improves glanceability across narrow and wide cockpit tiles while keeping `showTextColor = false` for high-contrast numeric legibility.
* **Unpersisted HRV Display Options**: On the 4th `HRV (DFA a1)` tab in `ZonesSettingsActivity.kt:1086-1096`, `displayOptions` is stored only in ephemeral `remember { mutableStateOf(...) }`, discarding user toggles upon leaving the screen.
* **HR-Coupled Mapping in Live Cockpit**: `TrackingViewModel.kt:978-983` maps `SensorType.HRV_DFA_A1` to `ZoneType.HR_RUN` / `ZoneType.HR_BIKE` (and `null` for other sports), preventing dedicated HRV display preferences across all sport profiles.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (`ZoneDisplayOptions.kt:35`)**: Constructor default `showRightBar = false` and `SettingsDataStore.kt:704,713,722,1074` fallback to `false` for unset preferences.
* **RCA 2 (`ZonesSettingsActivity.kt:1086-1096`)**: Ephemeral state lacks DataStore read/write wiring.
* **RCA 3 (`TrackingViewModel.kt:572-574, 783-784, 978-983`)**: Resolves `HRV_DFA_A1` display options by piggybacking on HR zone profiles.
* **Independent Peer Review & Verification**:
  * Verified `ZoneDisplayOptions.kt#L32-38`: `showRightBar` defaults to `false`.
  * Verified `SettingsDataStore.kt#L701-725, L1070-1078`: Missing right bar keys fall back to `false`.
  * Verified `ZonesSettingsActivity.kt#L1086-1096`: Ephemeral `remember` used; no DataStore persistence.
  * Verified `TrackingViewModel.kt#L978-983`: Maps `HRV_DFA_A1` to `HR_RUN`/`HR_BIKE`, returning `null` for `BSportType.UNKNOWN`.
  * Verified backward compatibility: Explicit user preferences stored as `false` in DataStore are preserved.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Update `ZoneDisplayOptions` defaults to `showBackground = true`, `showLeftBar = true`, `showRightBar = true`, `showTextColor = false`, `showGauge = false`.
  2. Update `SettingsDataStore` fallbacks for unset right bar keys to `true`.
  3. Introduce dedicated DataStore preference keys and flow for HRV `ZoneDisplayOptions` (`hrvZoneDisplayOptionsFlow`, `saveHrvZoneDisplayOptions`, cached in `SettingsSnapshot`).
  4. Wire reactive DataStore persistence to HRV display options in `HrvDfaA1SettingsContent` (`ZonesSettingsActivity.kt`).
  5. Decouple `SensorType.HRV_DFA_A1` in `TrackingViewModel.kt` to read dedicated HRV display options across all sport types.
  6. Synchronize unit tests (`ZoneDisplayOptionsTest`, `SensorFieldZoneDisplayTest`, `TrackingViewModelHrvTest`, `SettingsDataStoreZoneAtomicTest`).
* **Out-of-Scope Non-Goals**:
  * No alterations to physiological HRV threshold calculations ($T_1 \ge T_2 + 0.05$) or `TuningConfig`.
  * No SQLite database schema migrations (`DB_VERSION = 30` preserved).
  * No changes to Coggan 7-zone power or Karvonen heart rate boundary algorithms.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refines `REQ-UI-172` (*Configurable Zone Visual Presentation*) and amends `REQ-TRK-061.3` (*HRV Telemetry Zone Indicators*).
* **Historical Origin & Commit Trace**:
  * `REQ-UI-172`: `ATT-1390` (commit `e3966ebf`, Sprint 2026-40.5).
  * `REQ-TRK-061.3`: `ATT-3542` (commit `14292b35`), `ATT-6715` (commit `a36a9c43`, interim HR parity), and `ATT-7317` (commit `fce6777b`, dedicated 4th tab).
* **Root Reason for Existing Formulation**: `showRightBar` was off to prevent tile crowding; `HRV_DFA_A1` piggybacked on HR zones because HRV lacked dedicated zone settings prior to `ATT-7317`.
* **Preservation of Core Invariants**: Symmetrical bars maintain visual harmony; dedicated HRV preferences give athletes full control across all sports; explicit user settings are preserved.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-UI-172`, amends `REQ-TRK-061.3`.
* **Affected Architecture Elements**: `ZoneDisplayOptions.kt`, `SettingsDataStore.kt`, `ZonesSettingsActivity.kt`, `TrackingViewModel.kt`.
* **Verification Impact & Test Surface**: `ZoneDisplayOptionsTest.kt`, `SensorFieldZoneDisplayTest.kt`, `TrackingViewModelHrvTest.kt`, `SettingsDataStoreZoneAtomicTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Isolated UI defaults, DataStore key additions, and sensor display resolution.

---

## 6. Architectural Strategy & High-Level Solution
1. **Defaults**: Update `ZoneDisplayOptions(showRightBar = true)` and DataStore fallback mapping `prefs[keys.rightBarKey] ?: true`.
2. **HRV Persistence**: Add `hrv_dfa_a1_zone_show_*` keys to `SettingsDataStore`, expose `hrvZoneDisplayOptionsFlow` and `saveHrvZoneDisplayOptions`, include in `SettingsSnapshot`.
3. **UI Integration**: In `HrvDfaA1SettingsContent`, collect `hrvZoneDisplayOptionsFlow` and persist toggles.
4. **Cockpit Resolution**: In `TrackingViewModel`, resolve `SensorType.HRV_DFA_A1` from `hrvDisplayOptions` for all sports.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Explicit user preferences preserved; 100% 9-language translation parity maintained; zero regressions.
* **Risk Rating**: **LOW** (Presentation defaults and DataStore preference wiring).
