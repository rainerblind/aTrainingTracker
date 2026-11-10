# Stage 1 Analysis: ATT-5119 - 1-Tap Radar Audio Snooze, Peloton Sync & Urban Suppression

**Ticket**: [ATT-5119](https://rainerblind.atlassian.net/browse/ATT-5119)  
**Sub-task**: [ATT-6637](https://rainerblind.atlassian.net/browse/ATT-6637) (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-5119`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Continuous radar audio chimes during group rides (peloton drafting), city traffic queues, and parallel bike paths cause severe rider fatigue. Standard bike computers require navigating 4 menus deep to mute alerts and lack auto-unmute timers. Constant beeping in bumper-to-bumper city traffic degrades rider responsiveness and masks genuine high-speed threats.

---

## 2. Root Cause & Architectural Gap Analysis
* **`RadarAlertManager.kt` (`ATT-5086`)**: Alerts fire unconditionally on threat transitions in `onRadarStateUpdated` without support for temporary snooze intervals, light-mode coupling, or urban convoy filtering.
* **`RadarEdgeOverlay.kt` (`ATT-5085`)**: Top status badge tap is artificially locked behind `radarState.isLightControlSupported`, preventing riders without smart lights (e.g. Garmin RVR315) from accessing quick controls, and lacks a muted-bell countdown visual indicator.
* **`RadarLightQuickSwitchSheet.kt` (`ATT-5102`)**: Handles tail light modes but lacks audio snooze actions.
* **Independent Peer Review & Verification**: Verified that `RadarStateRepository.radarState` (`RadarState.kt`) can hold an immutable `audioSnoozeState`, allowing instantaneous 1-tap UI updates and decoupling audio gating from visual rail tracking.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * 1-Tap Cockpit Audio Snooze (15m, 30m, High-Threat Only, Rest of Ride, Resume) in `RadarEdgeOverlay` quick sheet.
  * Muted-bell indicator + remaining countdown on `RadarStatusBadgePill` with visual rail remaining 100% active.
  * Companion `toggleRadarAudioSnooze()` on `RadarAlertManager` for handlebar/Di2 hood button triggers (`ATT-1288`).
  * Automatic Peloton-mode audio coupling (`radarPelotonAudioSuppression`, default true) when light is in `PELOTON`.
  * Smart Urban Auto-Mute policy (`radarSmartUrbanMute`, default false): mutes slow queues ($\ge 3$ vehicles $< 5\text{ m/s}$ for $\ge 30\text{ s}$), auto-resumes after $\ge 20\text{ s}$ clear; `FAST_APPROACHING` red alerts always sound.
  * `TuningPreferencesDataStore` persistence, `RadarSettingsSection.kt` UI, and 9-language localization parity.
* **Out-of-Scope Non-Goals**:
  * Shifter hardware BLE/ANT+ protocol parsing (reserved for `ATT-1288`).
  * Core radar GATT characteristic changes or SQLite schema migrations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Targets**: Net-new `REQ-TRK-064` & `TST-TRK-064`, extending `REQ-TRK-062` and `REQ-TRK-063`.
* **Chesterton's Fence**: `REQ-TRK-063` established audio alerts for hands-free outdoor safety. Muting audio MUST NEVER suppress the visual vertical target rail, disable disconnect warnings, or silence `FAST_APPROACHING` red threats during urban/peloton suppression.

---

## 5. Architectural Strategy & Solution Design
* **`RadarAudioSnoozeState`**: Immutable model (`isSnoozed`, `mode`, `remainingSeconds`, `expiresAtRealtimeMs`) published via `RadarStateRepository.radarState`.
* **`RadarAlertManager` Extensions**:
  * 1-second coroutine ticker updating remaining countdown; auto-restores on expiration.
  * Gated chime triggers: `triggerApproachAlert()` and `triggerAllClearAlert()` check snooze, peloton mode, and urban filter. `triggerFastThreatAlert()` always sounds unless `MUTED` or `REST_OF_RIDE` / timed snooze is active.
  * Static companion `toggleRadarAudioSnooze()` for handlebar button integration.
* **`RadarEdgeOverlay.kt` & Quick Sheet**: Enable badge clicks unconditionally; render muted bell icon and countdown text; add 1-tap snooze buttons to bottom sheet.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: (1) Visual target rail remains 100% active and un-dimmed during audio snooze. (2) Zero SQLite migrations. (3) 100% 9-language localization parity. (4) Parent ticket Human Decision Gate strictly enforced.
* **Risk Rating**: **LOW** — Purely additive in-memory audio gating, UI extensions, and DataStore preferences with zero database impact.
