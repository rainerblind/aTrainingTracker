# Stage 1 Analysis: ATT-6708 - Decouple radar audio snooze from TrackerService lifecycle, preserve haptics during audio mute, and keep vehicle count visible on snoozed badge

**Ticket**: [ATT-6708](https://rainerblind.atlassian.net/browse/ATT-6708)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6708`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Sprint `2026-46.1` audit of `ATT-5119` revealed three critical flaws in cycling rearview radar audio snooze and threat alerting:
1. **Lifecycle Coupling in `RadarAlertManager`**: Snooze controls in `RadarLightQuickSwitchSheet` are inactive in `READY` (pre-ride / simulator preview) and `PAUSED` states because `RadarAlertManager.activeInstance` is bound strictly to `TrackerService` active recording. On workout stop, `activeInstance = null` leaves a frozen countdown badge in `RadarStateRepository`, while transient mid-ride BLE disconnects (`BTLERadarDevice.disconnectFromGatt()`) forcibly reset snooze to `INACTIVE`.
2. **Audio Snooze Suppresses Haptics & Un-Overridable Auto-Mute**: Early returns in `triggerApproachAlert()`, `triggerFastThreatAlert()`, and `triggerAllClearAlert()` execute before `vibrate()`, suppressing critical handlebar vibrations when only acoustic chimes were snoozed. Furthermore, when Peloton light mode or Smart Urban congestion suppresses audio, tapping "Resume Audio" only resets `currentSnoozeMode = NONE` without overriding active Peloton/Urban suppression, leaving the user locked into auto-mute.
3. **Badge Overwrites Approaching Vehicle Count, Lacks Vector Mute Icon & Localization**: When `ThreatLevel.APPROACHING` is active, `RadarStatusBadgePill` replaces the approaching vehicle count with ASCII tokens (`"${mins}m"`, `"P"`, `"U"`, `"M"`), omits a muted-bell vector icon, and uses hardcoded English accessibility descriptions. Additionally, if $\ge 3$ vehicles are present in urban congestion and a single vehicle passes at $\ge 5.0\text{ m/s}$, the 30s congestion accumulator resets to `null`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Service Lifecycle Coupling)**: `TrackerService.onCreate()` instantiates `RadarAlertManager`, setting `activeInstance`. In `READY` or `PAUSED`, `activeInstance` is null, causing `RadarAlertManager.snooze()` and `resumeAudio()` to silently drop user actions. On service teardown, `RadarAlertManager.release()` cancels `snoozeTickerJob` without clearing `RadarStateRepository.radarState.audioSnoozeState`. In `BTLERadarDevice.java` line 315, `disconnectFromGatt()` injects `RadarAudioSnoozeState.INACTIVE`, obliterating rider snooze preferences across transient BLE reconnects.
* **RCA 2 (Premature Alert Gating & Ineffective Resume CTA)**: Gating logic in `triggerApproachAlert()` returns before `if (config.haptics) vibrate(VibrationPattern.APPROACH)`. In `resumeAudio()`, only `currentSnoozeMode` is reset to `NONE`; `lastPelotonSuppressionActive` and `isUrbanSuppressionActive` remain true, causing `publishSnoozeStateLocked()` to immediately reassert `isSnoozed = true`.
* **RCA 3 (UI Rendering Branch & Convoy Accumulator Reset)**: `RadarEdgeOverlay.kt` line 305 routes `snoozeState.isSnoozed` into `Text(text = snoozeBadgeText ?: "M")`, bypassing `${radarState.targets.size}` during approaching threats. In `RadarAlertManager.kt` line 444, `slowTargetCount` counts targets with speed $< 5.0\text{ m/s}$. When 2 slow cars and 1 passing car ($\ge 5.0\text{ m/s}$) are detected, `slowTargetCount < 3` triggers `urbanCongestionStartTimeMs = null`, wiping the 30s accumulator despite dense traffic.
* **Independent Peer Review & Verification**:
  * Verified live files: `RadarAlertManager.kt`, `RadarEdgeOverlay.kt`, `RadarLightQuickSwitchSheet.kt`, `TrackingTabsScreen.kt`, and `BTLERadarDevice.java`. Confirmed lines and behaviors.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Decouple snooze state, timer, and expiration into a lifecycle-safe singleton (`RadarSnoozeController`) in `com.atrainingtracker.banalservice.devices.radar`, ensuring snooze/resume function in `READY`, `PAUSED`, and `TRACKING`, survive transient BLE drops, and reset on explicit workout completion.
  2. Gate only acoustic chimes on snooze / Peloton / Urban mute; ensure `vibrate()` fires whenever `config.haptics` is true.
  3. Support explicit "Resume Audio" override of Peloton and Urban auto-mute until the next state transition.
  4. Update `RadarEdgeOverlay` / `RadarStatusBadgePill` to keep approaching vehicle count visible, add a dedicated muted-bell vector icon + localized countdown indicator, and localize all accessibility descriptions across all 9 supported locales (en, de, es, fr, it, ja, nl, pl, pt).
  5. Retain `urbanCongestionStartTimeMs` when $\ge 3$ total vehicles are present even if one passes at $\ge 5.0\text{ m/s}$.
* **Out-of-Scope Non-Goals**:
  * Modifying BLE GATT packet parsing or ANT+ driver logic.
  * Adding new database tables or SQLite schema migrations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-064` (`REQ-TRK-064.1`, `REQ-TRK-064.2`, `REQ-TRK-064.3`, `REQ-TRK-064.5`).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-46.1 via `ATT-5119` (commit `24a520e5`).
* **Root Reason for Existing Formulation**: `RadarAlertManager` was initially coupled to `TrackerService` as a combined audio/haptic alert coordinator. Mute conditions were placed at method entry for simplicity, unintentionally disabling haptics and binding UI controls to service lifecycle.
* **Preservation of Core Invariants**: High-speed threat escalation alerts (`FAST_APPROACHING`) remain audible under `FAST_ONLY` snooze, Peloton sync, and Urban mute; visual rail tracking remains 100% active; zero native `AudioTrack` resource leaks.

---

## 5. Architectural Strategy & High-Level Solution
* **`RadarSnoozeController` Singleton**:
  * Implemented in `banalservice.devices.radar`. Coordinates `RadarAudioSnoozeState`, countdown ticker coroutine, and manual resume override flags (`manualResumeOverride`).
  * Survives BLE reconnects; resets cleanly upon explicit workout finish (`TrackerService` stop / accumulator reset).
* **Independent Audio vs. Haptic Dispatch**:
  * `vibrate(...)` executes directly if `config.haptics` is enabled.
  * Chimes are gated independently: `playChime()` checks `isAudioChimeSuppressed(threatLevel, config)` which respects manual resume overrides for Peloton and Urban states until state changes.
* **Resilient Urban Congestion Accumulator**:
  * `urbanCongestionStartTimeMs` is preserved when `state.targets.size >= 3` and at least 2 targets are slow, preventing premature timer resets from a single fast pass.
* **Enhanced `RadarStatusBadgePill` & Overlay**:
  * Approaching vehicle count `${radarState.targets.size}` is always rendered on threat.
  * When snoozed, a dedicated muted-bell vector icon and localized countdown indicator are rendered alongside the vehicle count.
  * Accessibility descriptions are localized across all 9 locales.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `RadarStateRepository.radarState` StateFlow API contract preserved.
  2. 100% 9-language translation parity in `strings_radar.xml` verified via `TranslationParityTest`.
  3. Single squashed commit on `sprint/2026-46.2` under `/tmp/att_build_merge.lock`.
  4. Final state assigned to Rainer Blind in `Final Review (Human)`.
* **Risk Rating**: **MEDIUM** (Multi-component coordination across banalservice singleton, audio alert manager, and Compose overlay UI; thoroughly protected by unit and Robolectric tests).
