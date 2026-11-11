# Stage 3: Implementation Plan - ATT-6709: Disable phone-speaker stereo attenuation on SYSTEM_DEFAULT, select FAST_APPROACHING target kinematics, add per-target lane-change hysteresis, and release preview AudioTracks

**Ticket**: [ATT-6709](https://rainerblind.atlassian.net/browse/ATT-6709)  
**Sub-task**: [ATT-6934](https://rainerblind.atlassian.net/browse/ATT-6934) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-063.1`, `REQ-TRK-065.1`, `REQ-TRK-065.3`, `REQ-TRK-065.5`  
**Test Mapping**: `TST-TRK-063.1a`, `TST-TRK-065.1a`, `TST-TRK-065.3a`, `TST-TRK-065.4a`, `TST-TRK-065.7a`  
**Branch**: `feature/ATT-6709` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Sprint 2026-46.1 review identified three defects: (1) `computeStereoGains()` attenuates outdoor phone-speaker volume on `SYSTEM_DEFAULT` when headphones are absent; (2) `triggerFastThreatAlert()` binds to the closest car instead of the `FAST_APPROACHING` threat, while lane-change tracking lacks multi-vehicle state and flutter hysteresis; (3) `previewSynthesizer` leaks native `AudioTrack`s on Settings exit, and UI contains `FilterChip` action buttons and non-subordinate switches.

---

## 2. Traceability & Requirements Mapping
* **REQ-TRK-065.1 (Speaker Panning Collapse)**: `computeStereoGains()` returns `(1.0f, 1.0f)` when `hasHeadphones == false` on `SYSTEM_DEFAULT` -> `TST-TRK-065.1a`
* **REQ-TRK-063.1 (Fast Threat Kinematics)**: `triggerFastThreatAlert()` selects fast-approaching threat kinematics -> `TST-TRK-063.1a`
* **REQ-TRK-065.3 (Multi-Target Lane Hysteresis)**: Per-target `LaneTrackState` map with 3,000 ms cooldown and departed ID pruning -> `TST-TRK-065.3a`
* **REQ-TRK-065.5 (Resource Lifecycle & UI Consistency)**: `releasePreviewSynthesizer()`, `AssistChip` actions, `SubordinateTuningToggleItem`, and `setVolume()` on mono tracks -> `TST-TRK-065.4a`

---

## 3. System Invariants & Preserved Behavior
1. **Speaker Loudness**: Peak built-in speaker volume preserved across all alert modes.
2. **Headphone Fidelity**: Stereo spatial panning and approach-speed pitch modulation unchanged when headphones/Bluetooth audio devices are connected.
3. **No Native Leaks**: All static `AudioTrack` handles released on Settings disposal.
4. **Design Consistency**: Strict adherence to Design Guidelines §1.2 (`AssistChip`) and §3.3 (`SubordinateTuningToggleItem`).

---

## 4. Proposed Architectural Changes
* **Component 1 (`RadarSpatialAudioMath.kt`)**: Add parameter `hasHeadphones: Boolean = true` to `computeStereoGains()`. Collapse gains to `(1.0f, 1.0f)` if `!isSpatialAudioEnabled || route == FORCE_SPEAKER || (!hasHeadphones && route == SYSTEM_DEFAULT) || lateralOffsetMeters == null`.
* **Component 2 (`RadarAudioSynthesizer.kt`)**: Add `isHeadphonesOrBluetoothConnected(): Boolean` inspecting `AudioManager` outputs against `EXTERNAL_DEVICE_TYPES`. In `playTrack()`, replace deprecated `setStereoVolume()` with `setVolume()` on mono tracks.
* **Component 3 (`RadarAlertManager.kt`)**: Select `FAST_APPROACHING` target kinematics for fast threats. Implement `private val laneTrackStates = mutableMapOf<Int, LaneTrackState>()` with spatial hysteresis and 3,000 ms cooldown. Provide `releasePreviewSynthesizer()`.
* **Component 4 (`RadarSettingsSection.kt`)**: Add `DisposableEffect` calling `releasePreviewSynthesizer()`. Convert preview `FilterChip`s to `AssistChip`s, and `radarSpeedPitchModulation` to `SubordinateTuningToggleItem`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Spatial Audio Panning & Headphone Fallback (`RadarSpatialAudioMath.kt`)
* Add `hasHeadphones: Boolean = true` parameter to `computeStereoGains()` and collapse gains on `SYSTEM_DEFAULT` without headphones.

### Step 2: Headphone Detection & Volume Cleanup (`RadarAudioSynthesizer.kt`)
* Implement `isHeadphonesOrBluetoothConnected()`; replace deprecated `setStereoVolume()` on mono track with `setVolume()`.

### Step 3: Fast Threat Kinematics & Lane-Change Hysteresis (`RadarAlertManager.kt`)
* Prioritize `FAST_APPROACHING` target kinematics; implement per-target `LaneTrackState` map; add `releasePreviewSynthesizer()`.

### Step 4: Settings Lifecycle & UI Consistency (`RadarSettingsSection.kt`)
* Hook `DisposableEffect` for preview synthesizer release; replace stateless `FilterChip` preview actions with `AssistChip`; embed subordinate toggles.

### Step 5: Unit Tests & Regression Verification
* Files: `RadarSpatialAudioMathTest.kt`, `RadarAlertManagerTest.kt`, `RadarAudioSynthesizerTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.radar.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted radar audio unit suite passing 100% + `verify_requirement_governance.py`.
* **Rollback**: Branch revert via `git checkout feature/ATT-6709`.
