# Stage 3 Implementation Plan: ATT-5086 - Radar Audio Chime & Haptic Alert Engine

**Ticket**: [ATT-5086](https://rainerblind.atlassian.net/browse/ATT-5086) | **Sub-task**: [ATT-6579](https://rainerblind.atlassian.net/browse/ATT-6579) (`[Impl-Plan]`) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5086`  
**Requirement Mapping**: `REQ-TRK-063` (`063.1`–`063.7`) | **Test Spec**: `TST-TRK-063` (`063.1`–`063.8`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Technical Architecture & SWE.2 Design
* **RadarAudioSynthesizer (`com.atrainingtracker.trainingtracker.audio.radar`)**:
  - In-memory 16-bit PCM multi-tone synthesis (2.2–3.2 kHz harmonics) preloaded into `MODE_STATIC` `AudioTrack`s (`REQ-TRK-063.2`).
  - Warm replay invariant (§3.4): `pause()` + `playbackHeadPosition = 0` + `play()` (zero `track.stop()`, preventing 200–385 ms AudioFlinger latency).
  - Configured with `AudioAttributes.USAGE_MEDIA`.
  - Routing via `setPreferredDevice(AudioDeviceInfo)`: `TYPE_BUILTIN_SPEAKER` when `FORCE_SPEAKER`, `null` for system default (`REQ-TRK-063.5`).
* **RadarAlertManager (`com.atrainingtracker.trainingtracker.audio.radar`)**:
  - Observes `RadarStateRepository.radarState` flow in `TrackerService` for foreground and screen-off reliability.
  - State machine with debouncing: triggers Approach (`NONE -> APPROACHING`), Urgent Threat (`-> FAST_APPROACHING`), All-Clear (`-> NONE`), and Disconnect (`-> DISCONNECTED_ERROR`) (`REQ-TRK-063.1`).
  - Transient ducking: Requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` before chime; abandons immediately on completion (`REQ-TRK-063.4`).
  - Speaker boost: Temporarily boosts stream volume (`flags = 0`) during speaker chime; restores in `finally` and `release()` (`REQ-TRK-063.3`).
  - Haptics: Triggers distinct `VibrationEffect` patterns (medium pulse, triple pulse, long buzz) (`REQ-TRK-063.1`).
* **DataStore & Settings UI (`TuningPreferencesDataStore.kt` & `RadarSettingsSection.kt`)**:
  - Enums: `RadarAudioAlertMode` (`ALL_ALERTS`, `APPROACH_AND_FAST`, `FAST_ONLY`, `MUTED`), `RadarAudioRoute` (`SYSTEM_DEFAULT`, `FORCE_SPEAKER`).
  - Preferences: Alert mode, alert volume (10–100%), speaker boost toggle, audio route, and haptic toggle (`REQ-TRK-063.6`).
  - Material 3 settings card in `RadarSettingsSection.kt` with chips, slider, and switches adhering to `docs/design_guidelines.md`.
* **TrackerService Integration**:
  - Instantiated and started in `TrackerService.java`, cleanly released in `onDestroy()`.

---

## 2. Atomic Implementation Steps

| Step | Component | Target Files | Action & Core Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Preferences Model | `TuningPreferencesDataStore.kt`<br>`TuningPreferencesDefaults.kt` | Define `RadarAudioAlertMode` and `RadarAudioRoute` enums; add DataStore keys, flow properties, and update methods with defaults. | `REQ-TRK-063.6`<br>`TST-TRK-063.6` |
| **2** | Audio Synthesizer | `RadarAudioSynthesizer.kt` | Implement PCM synthesis for 4 chimes, `USAGE_MEDIA`, warm `MODE_STATIC` replay (`pause() + head = 0 + play()`), speaker routing, and `close()`. | `REQ-TRK-063.2,5`<br>`TST-TRK-063.2,5` |
| **3** | Alert Manager | `RadarAlertManager.kt` | Implement state transition debouncer, transient audio ducking, speaker volume boost & guaranteed restore, and `VibrationEffect` haptics. | `REQ-TRK-063.1,3,4`<br>`TST-TRK-063.1,3,4` |
| **4** | Service Wiring | `TrackerService.java` | Initialize `RadarAlertManager` in `TrackerService`, observe `RadarStateRepository.radarState`, and release cleanly in `onDestroy()`. | `REQ-TRK-063.1`<br>`TST-TRK-063.1` |
| **5** | Settings UI | `RadarSettingsSection.kt` | Add Audio & Haptic Alerts card with mode chips, volume slider, speaker boost toggle, route selector, and haptic toggle. | `REQ-TRK-063.6`<br>`TST-TRK-063.6` |
| **6** | Unit Tests | `RadarAudioSynthesizerTest.kt`<br>`RadarAlertManagerTest.kt`<br>`TuningPreferencesDataStoreTest.kt` | Author unit tests verifying PCM synthesis, warm replay, state transitions, debouncing, volume boost & restore, and DataStore persistence. | `REQ-TRK-063.1-6`<br>`TST-TRK-063.1-6` |
| **7** | Parity & Regression | `TranslationParityTest.kt`<br>Targeted Suite | Verify 9-language translation parity in `strings_radar.xml` and execute targeted test suite (`audio.radar.*`, `settings.*`, `build.*`). | `REQ-TRK-063.7`<br>`TST-TRK-063.7,8` |

---

## 3. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: No production edits until Gate 3 audit passes (`tools/jira_util.py check-gate`).
* **Volume Restore**: Hardware volume must be defensively restored to the user's prior level in `finally` and service `onDestroy()`.
* **Coding Standards §3.4**: `USAGE_MEDIA`, warm `MODE_STATIC` replay via `pause()` + seek 0 + `play()` (zero `track.stop()`).
* **Database & Schema**: Zero SQLite schema modifications; purely in-memory synthesis and DataStore preference persistence.
* **Risk Rating**: **MEDIUM** (Audio subsystem and foreground service lifecycle interactions; fully mitigable via isolated test suites).
