# Stage 2: Requirement & Test Specification - ATT-5119: 1-Tap Radar Audio Snooze, Peloton Sync & Urban Suppression

**Ticket**: [ATT-5119](https://rainerblind.atlassian.net/browse/ATT-5119)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-TRK-064` (*1-Tap Radar Audio Snooze, Peloton-Mode Audio Sync & Smart Urban Alert Suppression*)  
**Test Spec ID**: `TST-TRK-064`  
**Branch**: `feature/ATT-5119`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-TRK-064)

### 1.1 Problem Statement & Rationale
Continuous radar alert chimes in group rides, city traffic queues, and parallel bike paths cause severe rider fatigue. Navigating nested menus mid-ride is hazardous. Cyclists require 1-tap temporary audio snooze from the Cockpit overlay badge, automatic audio suppression coupled to tail light Peloton mode, and smart urban convoy filtering while preserving full visual awareness and high-speed threat safety alerts.

### 1.2 Functional & Architectural Requirements
1. *1-Tap Cockpit Audio Snooze (`REQ-TRK-064.1`)*: The system SHALL provide 1-tap quick snooze actions (Snooze 15m, 30m, High-Threat Only, Mute Rest of Ride) in the quick sheet. Tapping the radar badge in `RadarEdgeOverlay` SHALL display the sheet regardless of whether tail light control is supported.
2. *Muted Badge Countdown Indicator & Visual Rail Continuity (`REQ-TRK-064.2`)*: While audio is snoozed, `RadarStatusBadgePill` SHALL render a muted indicator with countdown and localized semantics description (`radar_snooze_active_format`). The vertical visual target rail SHALL remain 100% active and un-dimmed.
3. *Automatic Snooze Expiration & Handlebar Shifter Integration (`REQ-TRK-064.3`)*: Standard audio alerts SHALL automatically restore upon timer expiry or manual resume. `RadarAlertManager` SHALL expose `toggleRadarAudioSnooze()` for remote handlebar/shifter button invocation (`ATT-1288`).
4. *Peloton Light Mode Audio Sync (`REQ-TRK-064.4`)*: When `radarPelotonAudioSync` is enabled (default `true`), switching tail light to `PELOTON` mode (`ATT-5102`) SHALL automatically suppress routine `APPROACHING` and `ALL_CLEAR` chimes while retaining `FAST_APPROACHING` threats. Reverting from `PELOTON` mode SHALL restore normal alerts.
5. *Smart Urban Congestion Suppression (`REQ-TRK-064.5`)*: When `radarSmartUrbanMute` is enabled (default `false`), the system SHALL suppress repetitive approach chimes when $\ge 3$ vehicles $< 5.0\text{ m/s}$ linger for $\ge 30\text{ s}$, auto-exiting after $\ge 20\text{ s}$ clear. `FAST_APPROACHING` threats SHALL sound immediately.
6. *Settings Persistence & DataStore Integration (`REQ-TRK-064.6`)*: `TuningPreferencesDataStore` SHALL persist `radarPelotonAudioSync` and `radarSmartUrbanMute`, exposed in `RadarSettingsSection.kt`.
7. *9-Language Localization Parity (`REQ-TRK-064.7`)*: All snooze, badge, and sync strings in `strings_radar.xml` SHALL maintain 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (1-Tap Snooze)**: *Given* active cycling tracking, *When* cyclist selects "Snooze 15 min", *Then* approach chimes are silenced for 15m, badge displays countdown, visual rail stays active, and alerts resume after 15m.
* **Criterion 2 (Peloton Mode Sync)**: *Given* `radarPelotonAudioSync` enabled, *When* tail light enters `PELOTON` mode, *Then* routine chimes are suppressed while high-speed red threats continue to sound.
* **Criterion 3 (Urban Congestion Filter)**: *Given* `radarSmartUrbanMute` enabled and $\ge 3$ slow vehicles queuing for $\ge 30\text{ s}$, *When* additional slow vehicles join, *Then* routine chimes are suppressed; fast threats trigger immediate alerts.

### 1.4 System Invariants
* Visual vertical target rail remains 100% active; `FAST_APPROACHING` safety alerts preserved; zero SQLite schema modifications; 100% 9-language translation parity.

---

## 2. Test Specification (TST-TRK-064)

### Test Case 1: 1-Tap Audio Snooze Gating & Expiration (`TST-TRK-064.1`)
* **Scope**: Unit Test (`RadarAlertManagerTest.kt`)
* **Action**: Trigger snooze (15m, 30m, Rest of Ride), verify chime suppression, countdown decrement, and automatic restoration.

### Test Case 2: Muted Badge Countdown Indicator & Touch Response (`TST-TRK-064.2`)
* **Scope**: Compose Unit Test (`RadarEdgeOverlayTest.kt`)
* **Action**: Render badge under snooze state, assert muted bell icon, countdown text, and tap handling on radar-only devices.

### Test Case 3: Handlebar Remote Shifter Toggle (`TST-TRK-064.3`)
* **Scope**: Unit Test (`RadarAlertManagerTest.kt`)
* **Action**: Invoke `RadarAlertManager.toggleRadarAudioSnooze()` and assert state toggling.

### Test Case 4: Peloton Mode Light-Audio Sync (`TST-TRK-064.4`)
* **Scope**: Unit Test (`RadarAlertManagerTest.kt`)
* **Action**: Set tail light to `PELOTON` mode; verify `APPROACHING` silenced and `FAST_APPROACHING` sounding.

### Test Case 5: Smart Urban Congestion Filter (`TST-TRK-064.5`)
* **Scope**: Unit Test (`RadarAlertManagerTest.kt`)
* **Action**: Feed 3 slow targets for 30s; verify chime suppression, 20s recovery, and urgent fast-threat chime.

### Test Case 6: Settings Persistence & DataStore Parity (`TST-TRK-064.6`)
* **Scope**: Unit Test (`TuningPreferencesDataStoreTest.kt`)
* **Action**: Verify preference persistence and defaults for `radarPelotonAudioSync` and `radarSmartUrbanMute`.

### Test Case 7: 9-Language Localization Parity (`TST-TRK-064.7`)
* **Scope**: Contract Test (`TranslationParityTest.kt`)
* **Action**: Verify 100% string and format specifier parity across all 9 locales in `strings_radar.xml`.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-064.1` | Unit | `RadarAlertManager.kt` | `REQ-TRK-064.1` | Specified |
| `TST-TRK-064.2` | UI | `RadarEdgeOverlay.kt` | `REQ-TRK-064.2` | Specified |
| `TST-TRK-064.3` | Unit | `RadarAlertManager.kt` | `REQ-TRK-064.3` | Specified |
| `TST-TRK-064.4` | Unit | `RadarAlertManager.kt`, `RadarLightController.kt` | `REQ-TRK-064.4` | Specified |
| `TST-TRK-064.5` | Unit | `RadarAlertManager.kt` | `REQ-TRK-064.5` | Specified |
| `TST-TRK-064.6` | Unit | `TuningPreferencesDataStore.kt` | `REQ-TRK-064.6` | Specified |
| `TST-TRK-064.7` | Localization | `TranslationParityTest.kt` | `REQ-TRK-064.7` | Specified |
| `TST-TRK-064.8` | Regression | Tier 1 Targeted Test Suite | `REQ-PRO-001` | Specified |
