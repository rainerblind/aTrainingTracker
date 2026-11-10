# Stage 3: Implementation Plan - ATT-5119: 1-Tap Radar Audio Snooze, Peloton Sync & Urban Mute

**Ticket**: [ATT-5119](https://rainerblind.atlassian.net/browse/ATT-5119)  
**Sub-task**: [ATT-6645](https://rainerblind.atlassian.net/browse/ATT-6645) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-TRK-064` (`REQ-TRK-064.1..7`)  
**Test Mapping**: `TST-TRK-064` (`TST-TRK-064.1..7`)  
**Branch**: `feature/ATT-5119`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Continuous radar audio beeping in dense city queues, group rides, and parallel bike paths causes rider fatigue. Riders require 1-tap temporary audio snooze from the Cockpit overlay badge, automatic audio suppression when switching tail lights to Peloton mode, and smart urban convoy filtering, while keeping the visual vertical rail and high-speed threat safety alerts 100% active.

---

## 2. Traceability & Requirements Mapping
* **REQ-TRK-064.1 / TST-TRK-064.1**: 1-Tap Audio Snooze Gating (15m, 30m, Red Only, Rest of Ride, Resume) in `RadarAlertManager.kt`.
* **REQ-TRK-064.2 / TST-TRK-064.2**: Muted Badge Countdown Indicator & Touch Response in `RadarEdgeOverlay.kt`.
* **REQ-TRK-064.3 / TST-TRK-064.3**: Handlebar Remote Shifter Toggle `toggleRadarAudioSnooze()` in `RadarAlertManager.kt`.
* **REQ-TRK-064.4 / TST-TRK-064.4**: Peloton Light Mode Audio Sync in `RadarAlertManager.kt` & `RadarLightController.kt`.
* **REQ-TRK-064.5 / TST-TRK-064.5**: Smart Urban Congestion Filter ($\ge 3$ vehicles $< 5\text{ m/s}$ for $\ge 30\text{ s}$) in `RadarAlertManager.kt`.
* **REQ-TRK-064.6 / TST-TRK-064.6**: Preferences Persistence in `TuningPreferencesDataStore.kt`.
* **REQ-TRK-064.7 / TST-TRK-064.7**: 9-Language Localization Parity in `strings_radar.xml`.

---

## 3. System Invariants & Preserved Behavior
1. **Visual Threat Rail Continuity**: Vertical target rail remains 100% active, un-dimmed, and responsive during any audio snooze state.
2. **Red Alert Safety Preservation**: `FAST_APPROACHING` red threats are NEVER silenced by Peloton mode or Smart Urban filters.
3. **Zero Schema Migrations**: All state is in-memory and DataStore-persisted; zero SQLite database changes.
4. **Touch Pass-Through**: Outer overlay container preserves unhindered Map pan/zoom and horizontal tab swiping.

---

## 4. Proposed Architectural Changes
* **`RadarAudioSnoozeState.kt` & `RadarState.kt`**: Immutable `RadarAudioSnoozeState(isSnoozed, mode, remainingSeconds, expiresAtRealtimeMs)` added to `RadarState` and updated in `RadarStateRepository`.
* **`RadarAlertManager.kt`**: Adds 1s ticker coroutine for snooze expiration, companion `activeInstance` and `toggleRadarAudioSnooze()`, peloton mode check, and smart urban congestion velocity windowing.
* **`RadarEdgeOverlay.kt` & `RadarLightQuickSwitchSheet.kt`**: Unlocks top badge click for radar-only devices, renders muted bell + countdown on badge pill, and adds 1-tap snooze actions to quick-switch sheet.
* **`TuningPreferencesDataStore.kt` & `RadarSettingsSection.kt`**: Persists `radarPelotonAudioSync` and `radarSmartUrbanMute` and exposes toggles.
* **UI Consistency (Rule 23)**:
  * **Reference Screen**: `RadarEdgeOverlay.kt`, `RadarLightQuickSwitchSheet.kt`.
  * **Reused Components**: `AppModalBottomSheet`, `SingleChoiceSegmentedButtonRow`, `SubordinateTuningToggleItem`.
  * **Theme Tokens**: `MaterialTheme.colorScheme.onSurface`, `TTColor.RadarDisconnectedAmber`, `CircleShape`.
  * **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (State Models & DataStore)**: Create `RadarAudioSnoozeState.kt`, update `RadarState.kt`, and add preference keys in `TuningPreferencesDataStore.kt`.
2. **Step 2 (Alert Manager Snooze & Urban Filtering)**: Implement snooze timers, companion toggle, peloton sync gating, and urban congestion filter in `RadarAlertManager.kt`.
3. **Step 3 (Cockpit Overlay & Quick Sheet UI)**: Update `RadarEdgeOverlay.kt` badge icon/countdown and add snooze buttons in `RadarLightQuickSwitchSheet.kt`.
4. **Step 4 (Settings UI)**: Add sync and urban filter toggles to `RadarSettingsSection.kt`.
5. **Step 5 (Unit Tests)**: Author and execute tests in `RadarAlertManagerTest.kt`, `RadarEdgeOverlayTest.kt`, and `TuningPreferencesDataStoreTest.kt`.
   * Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.radar.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted radar unit test suites and `verify_requirement_governance.py`.
* **Rollback**: Clean branch isolation on `feature/ATT-5119` permits atomic reset if required.
