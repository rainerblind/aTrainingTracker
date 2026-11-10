# Stage 3: Implementation Plan - ATT-5120: 3D Stereo Audio Panning & Speed Pitch Modulation

**Ticket**: [ATT-5120](https://rainerblind.atlassian.net/browse/ATT-5120)  
**Sub-task**: [ATT-6672](https://rainerblind.atlassian.net/browse/ATT-6672) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-TRK-065` (`REQ-TRK-065.1..6`)  
**Test Mapping**: `TST-TRK-065` (`TST-TRK-065.1..7`)  
**Branch**: `feature/ATT-5120`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Monophonic radar audio alert tones provide no directional lane differentiation or intuitive speed perception over stereo or bone-conduction headphones. BLE V2 radar frames expose signed lateral lane offset ($x$) and relative approach velocity ($v_{\text{rel}}$). Cyclists require stereo audio panning toward the overtaking ear, continuous approach-speed pitch modulation (`0.95x .. 1.35x`), an eyes-free lane-change confirmation tick, and interactive preview controls in settings.

---

## 2. Traceability & Requirements Mapping
* **REQ-TRK-065.1 / TST-TRK-065.1,4**: Stereo lateral-offset panning `(leftGain, rightGain)` with RHT/LHT handedness and speaker fallback in `RadarSpatialAudioMath.kt` & `RadarAudioSynthesizer.kt`.
* **REQ-TRK-065.2 / TST-TRK-065.2**: Approach-speed pitch/tempo modulation (`0.95x .. 1.35x` for `3 .. 30 m/s`) via `AudioTrack.setPlaybackParams`.
* **REQ-TRK-065.3 / TST-TRK-065.3**: Lane-change spatial confirmation tick cue in `RadarAudioSynthesizer.kt` & `RadarAlertManager.kt`.
* **REQ-TRK-065.4 / TST-TRK-065.5**: Settings persistence in `TuningPreferencesDataStore.kt`.
* **REQ-TRK-065.5 / TST-TRK-065.7**: Interactive preview controls in `RadarSettingsSection.kt`.
* **REQ-TRK-065.6 / TST-TRK-065.6**: 9-language localization parity in `strings_radar.xml`.

---

## 3. System Invariants & Preserved Behavior
1. **Warm Playback Latency**: Zero AudioFlinger port renegotiation; `track.pause()` + `playbackHeadPosition = 0` + `play()` preserved.
2. **Phone Speaker Centered Invariant**: `FORCE_SPEAKER` routing collapses stereo gains to `(1.0, 1.0)` to maintain maximum output.
3. **Zero SQLite Changes**: All settings stored in `TuningPreferencesDataStore`; zero database migrations.
4. **Visual Threat Rail Continuity**: Vertical target rail and animations remain 100% active and unaffected.

---

## 4. Proposed Architectural Changes
* **`RadarSpatialAudioMath.kt`**: Helper object computing `(leftGain, rightGain)` based on lateral offset $x$, traffic orientation (RHT vs. LHT), and `route`; maps approach speed to pitch `0.95f .. 1.35f`.
* **`RadarAudioSynthesizer.kt`**: Enhances `playApproach` and `playFastThreat` with `leftGain`, `rightGain`, and `pitch` using `setStereoVolume` and `setPlaybackParams`; synthesizes and manages warm static `laneChangeTickTrack`.
* **`RadarAlertManager.kt`**: Extracts primary target kinematics in `onRadarStateUpdated()`, applies spatial gains and pitch, and detects lane-change transitions (`IN_RIDER_LANE` $\to$ `CHANGING_LANE_SAFE`) to trigger the spatial tick.
* **`TuningPreferencesDataStore.kt` & `RadarSettingsSection.kt`**: Persists 4 new spatial preferences; renders subordinate toggles, handedness `SingleChoiceSegmentedButtonRow` (Rule 1.1), and preview buttons.
* **UI Consistency (Rule 23)**:
  * Reference screen: `RadarSettingsSection.kt` (Audio & Haptic Alerts Card).
  * Reused components: `SingleChoiceSegmentedButtonRow`, `SubordinateTuningToggleItem`, `FilterChip`.
  * Theme tokens: `MaterialTheme.colorScheme.primary`, `TTColor.RadarThreatAmber`.
  * New one-off styles: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Math & DataStore)**: Implement `RadarSpatialAudioMath.kt` and add preference keys in `TuningPreferencesDataStore.kt`.
2. **Step 2 (Synthesizer Stereo Panning & Tick)**: Wire `setStereoVolume`, `PlaybackParams`, and `playLaneChangeTick` in `RadarAudioSynthesizer.kt`.
3. **Step 3 (Alert Manager Kinematics & Lane Tracking)**: Wire primary target speed/offset extraction and lane transition detection in `RadarAlertManager.kt`.
4. **Step 4 (Settings UI & Localization)**: Embed handedness segmented button, toggles, and preview actions in `RadarSettingsSection.kt`; update 9-locale `strings_radar.xml`.
5. **Step 5 (Unit Tests)**: Author and execute tests in `RadarSpatialAudioMathTest.kt`, `RadarAlertManagerTest.kt`, `TuningConfigTest.kt`, and `TranslationParityTest.kt`.
   * Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.radar.*" --tests "com.atrainingtracker.trainingtracker.settings.TuningConfigTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and `verify_requirement_governance.py`.
* **Rollback**: Atomic revert on `feature/ATT-5120` without touching `sprint/2026-46.1`.
