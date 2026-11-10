# Stage 2: Requirement & Test Specification - ATT-5086: Radar Audio Chime & Haptic Alert Engine

**Ticket**: [ATT-5086](https://rainerblind.atlassian.net/browse/ATT-5086) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5086`  
**Requirement Mapping**: `REQ-TRK-063` | **Test Spec ID**: `TST-TRK-063`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-TRK-063)

### 1.1 Problem Statement & Rationale
Cyclists operating in traffic require eyes-free situational awareness through audible and tactile cues cutting through outdoor wind and road noise. `RadarAlertManager` observes `RadarStateRepository.radarState` during active tracking in `TrackerService` (foreground and screen-off execution), synthesizing distinct PCM audio chimes via `AudioTrack` (`USAGE_MEDIA`, `MODE_STATIC` warm replay) and triggering `VibrationEffect` patterns on threat level transitions (`APPROACHING`, `FAST_APPROACHING`, `ALL_CLEAR`, `DISCONNECTED_ERROR`). The alert engine supports independent radar alert volume, transient background media ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), temporary speaker volume boost with guaranteed auto-restore, and configurable routing to connected headphones or forced phone speaker (`AudioTrack.setPreferredDevice()`) without switching `AudioManager` into telephony communication mode.

### 1.2 Functional & Architectural Requirements
* **REQ-TRK-063.1** (*Threat Transition Alerts & Debouncing*): `RadarAlertManager` SHALL trigger distinct acoustic chimes and vibration patterns on threat transitions: double ascending chime for initial approach (`NONE -> APPROACHING`), urgent triple high-pitch chime for high-speed threat (`-> FAST_APPROACHING`), soft single chime for all-clear (`APPROACHING/FAST -> NONE`), and descending warning chime with long vibration for disconnect (`-> DISCONNECTED_ERROR`). Transitions SHALL be debounced to prevent alert spamming.
* **REQ-TRK-063.2** (*AudioTrack USAGE_MEDIA Synthesis & Warm MODE_STATIC Replay*): `RadarAudioSynthesizer` SHALL synthesize low-latency multi-tone PCM audio (2.2–3.2 kHz harmonics) via `AudioTrack` with `AudioAttributes.USAGE_MEDIA`. Persistent static tracks SHALL use warm replay (`pause()` + `playbackHeadPosition = 0` + `play()`), prohibiting `track.stop()`.
* **REQ-TRK-063.3** (*Independent Volume & Speaker Override Hardware Boost*): Dedicated alert volume (10–100%, default 90%). When routed to phone speaker with "Boost Speaker Volume" enabled, the system SHALL save everyday stream volume, boost hardware volume without UI popups (`flags = 0`) for the chime (~300 ms), and auto-restore immediately in `finally` and service `onDestroy()`.
* **REQ-TRK-063.4** (*Transient Audio Ducking*): Request transient audio focus (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) prior to chime playback and abandon focus immediately upon completion.
* **REQ-TRK-063.5** (*Headphone vs. Forced Phone Speaker Routing*): Configure `AudioTrack.setPreferredDevice(AudioDeviceInfo)`: `TYPE_BUILTIN_SPEAKER` when "Force Phone Speaker" is enabled, or `null` for system default / connected Bluetooth headphones.
* **REQ-TRK-063.6** (*User Customization & DataStore Integration*): `TuningPreferencesDataStore` SHALL persist `radarAudioAlertMode`, `radarAlertVolume`, `radarSpeakerBoost`, `radarAudioRoute`, and `radarHapticAlert`, exposed in `RadarSettingsSection.kt`.
* **REQ-TRK-063.7** (*9-Language Localization Parity*): All alert settings in `strings_radar.xml` SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* Audio Alert Mode set to `ALL_ALERTS`, *When* `RadarState` transitions from `NONE` to `APPROACHING`, escalates to `FAST_APPROACHING`, and returns to `NONE`, *Then* `RadarAlertManager` plays Approach chime once, Fast chime on escalation, and All-Clear chime on last vehicle clearance without repeating on 8 Hz updates.
* *Given* phone media volume set low (e.g. 2/15) and Speaker Boost enabled, *When* an alert chime plays through the built-in speaker, *Then* hardware stream volume boosts to configured Alert Volume during the chime and restores to 2/15 immediately afterwards.
* *Given* background media playback over Bluetooth headphones and "Force Phone Speaker" enabled, *When* an alert chime plays, *Then* background media ducks transiently and the chime routes to `TYPE_BUILTIN_SPEAKER`.
* *Invariants*: Guaranteed volume restore in `finally`/`onDestroy()`; §3.4 warm static replay (zero `track.stop()`); zero main-thread blocking; zero SQLite schema modifications; 100% 9-language translation parity.

---

## 2. Test Specification (TST-TRK-063)

### Test Case 1: Threat Transition & Debounce Unit Tests (`TST-TRK-063.1`)
* **Scope**: Unit Test (`RadarAlertManagerTest.kt`)
* **Assertion**: Verify chime and vibration triggers on transitions (`NONE -> APPROACHING`, `FAST_APPROACHING`, `-> NONE`, `-> DISCONNECTED_ERROR`). Verify debouncing prevents repeated chimes on 8 Hz range telemetry.

### Test Case 2: PCM Audio Synthesis & MODE_STATIC Replay Tests (`TST-TRK-063.2`)
* **Scope**: Unit Test (`RadarAudioSynthesizerTest.kt`)
* **Assertion**: Verify multi-tone PCM synthesis within [-1.0, 1.0], `USAGE_MEDIA` AudioAttributes, and warm `MODE_STATIC` replay (`pause()`, `playbackHeadPosition = 0`, `play()`).

### Test Case 3: Independent Volume Boost & Safe Restoration Tests (`TST-TRK-063.3`)
* **Scope**: Unit Test (`RadarAlertManagerTest.kt`)
* **Assertion**: Verify saving everyday volume, boosting hardware volume with `flags = 0`, and guaranteed auto-restoration in `finally` and service `onDestroy()`.

### Test Case 4: Transient Audio Ducking Tests (`TST-TRK-063.4`)
* **Scope**: Unit Test (`RadarAlertManagerTest.kt`)
* **Assertion**: Verify `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` request before playback and immediate abandonment on chime completion.

### Test Case 5: Audio Device Routing Tests (`TST-TRK-063.5`)
* **Scope**: Unit Test (`RadarAudioSynthesizerTest.kt`)
* **Assertion**: Verify `AudioTrack.setPreferredDevice()` sets `TYPE_BUILTIN_SPEAKER` when forced, and `null` for system default.

### Test Case 6: TuningPreferencesDataStore Alert Preferences Tests (`TST-TRK-063.6`)
* **Scope**: Unit Test (`TuningPreferencesDataStoreTest.kt`)
* **Assertion**: Verify defaults and DataStore persistence for alert mode, volume, speaker boost, audio route, and haptic vibration.

### Test Case 7: 9-Language Localization Parity Test (`TST-TRK-063.7`)
* **Scope**: Contract Test (`TranslationParityTest.kt`)
* **Assertion**: Verify 100% key and format parity across `strings_radar.xml` in all 9 supported locales.

### Test Case 8: Clean-Room Targeted Test Suite Execution (`TST-TRK-063.8`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.radar.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-063.1` | Unit | `RadarAlertManager.kt`, `RadarAlertManagerTest.kt` | `REQ-TRK-063.1` | Specified |
| `TST-TRK-063.2` | Unit | `RadarAudioSynthesizer.kt`, `RadarAudioSynthesizerTest.kt` | `REQ-TRK-063.2` | Specified |
| `TST-TRK-063.3` | Unit | `RadarAlertManager.kt`, `RadarAlertManagerTest.kt` | `REQ-TRK-063.3` | Specified |
| `TST-TRK-063.4` | Unit | `RadarAlertManager.kt`, `RadarAlertManagerTest.kt` | `REQ-TRK-063.4` | Specified |
| `TST-TRK-063.5` | Unit | `RadarAudioSynthesizer.kt`, `RadarAudioSynthesizerTest.kt` | `REQ-TRK-063.5` | Specified |
| `TST-TRK-063.6` | Unit | `TuningPreferencesDataStore.kt`, `TuningPreferencesDataStoreTest.kt` | `REQ-TRK-063.6` | Specified |
| `TST-TRK-063.7` | Parity | `strings_radar.xml`, `TranslationParityTest.kt` | `REQ-TRK-063.7` | Specified |
| `TST-TRK-063.8` | Regression | `audio.radar.*`, `settings.*`, `build.*` | `REQ-PRO-001` | Specified |
