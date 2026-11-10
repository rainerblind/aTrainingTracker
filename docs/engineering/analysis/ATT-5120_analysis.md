# Stage 1 Analysis: ATT-5120 - 3D Stereo Audio Panning & Approach-Speed Pitch Modulation

**Ticket**: [ATT-5120](https://rainerblind.atlassian.net/browse/ATT-5120)  
**Sub-task**: [ATT-6668](https://rainerblind.atlassian.net/browse/ATT-6668) (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-5120`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Cyclists using open-ear bone-conduction or stereo Bluetooth headphones receive flat mono radar beeps that only convey rear vehicle presence. Riders must divert their eyes to the screen to discern whether an approaching vehicle is directly behind them or has pulled into the passing lane, and how rapidly it is closing. BLE V2 telemetry (`6a4e3204`, Garmin Varia RearVue 820) exposes lateral offset $x$ and approach speed $v_{\text{rel}}$, enabling intuitive eyes-free spatial audio and speed pitch perception.

---

## 2. Root Cause & Architectural Gap Analysis
* **`RadarAudioSynthesizer.kt` (`ATT-5086`)**: Configured with `CHANNEL_OUT_MONO` and fixed playback pitch (`PlaybackParams` unused); lacks stereo gain panning `(leftGain, rightGain)` and lane-change confirmation click synthesis.
* **`RadarAlertManager.kt` (`ATT-5086` / `ATT-5119`)**: Evaluates aggregate threat transitions without extracting the primary target's `lateralOffsetMeters` or `approachSpeedMps`, and lacks target lane-change tracking (`IN_RIDER_LANE` $\to$ `CHANGING_LANE_SAFE`).
* **Independent Peer Review & Verification**:
  * Verified `PlaybackParams.setPitch(pitch)` and `setSpeed(speed)` on `AudioTrack` operate natively without rebuilding static PCM buffers on API 26+ (`minSdkVersion = 26`).
  * Verified `AudioTrack.setStereoVolume(leftGain, rightGain)` (or stereo PCM synthesis) pans dynamically during warm `MODE_STATIC` replay (`pause() -> playbackHeadPosition = 0 -> play()`), adhering strictly to Rules 53 and 76 (`USAGE_MEDIA`).
  * Force Speaker Invariant: When `route == FORCE_SPEAKER`, stereo panning collapses to centered `(1.0, 1.0)` to preserve maximum phone speaker output.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Stereo lateral-offset panning `(leftGain, rightGain)` based on BLE V2 `lateralOffsetMeters`: Centered `(1.0, 1.0)` for in-lane ($|x| < 1.0\text{ m}$), panned `(1.0, 0.20)` / `(0.20, 1.0)` for safe passing ($|x| \ge 1.5\text{ m}$).
  * Traffic orientation selection: Right-Hand Traffic (overtake on left) vs. Left-Hand Traffic (overtake on right) in `TuningPreferencesDataStore`.
  * Approach-speed pitch/cadence modulation: continuous linear scaling `0.95x .. 1.35x` mapped across `3.0 .. 30.0 m/s` (`11 .. 108 km/h`).
  * Subtle spatial lane-change tick cue when vehicle moves from in-lane to safe passing lane.
  * Interactive preview buttons in `RadarSettingsSection.kt` (Centered, Passing, Slow, Fast, Lane-Change Tick).
  * 9-language localization parity in `strings_radar.xml`.
* **Out-of-Scope Non-Goals**:
  * Core BLE V2 GATT parser modification (decoupled in `BTLERadarDevice.java` / `RadarTarget.kt`).
  * SQLite schema alterations (all settings persisted in DataStore).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Allocation**: Net-new `REQ-TRK-065` and `TST-TRK-065`, extending `REQ-TRK-063` (Alert Engine) and `REQ-TRK-064` (Audio Snooze).
* **Chesterton's Fence**: `REQ-TRK-063` established reliable multi-tone chimes with guaranteed speaker boost restore. 3D spatial panning and pitch modulation MUST NOT compromise the warm replay latency invariant (§3.4), reduce overall threat chime audibility, or alter haptic vibration patterns.

---

## 5. Architectural Strategy & High-Level Solution
* **Audio Synthesizer**: Enhance `RadarAudioSynthesizer` with `playApproach(volume, route, leftGain, rightGain, pitch)` and `playFastThreat(...)` via `AudioTrack.setPlaybackParams` and stereo channel gains. Synthesize lightweight spatial tick PCM for lane-change events.
* **Alert Manager**: Extract primary target kinematics (`approachSpeedMps`, `lateralOffsetMeters`) in `onRadarStateUpdated()`. Map speed to pitch `0.95x .. 1.35x` and offset to stereo gains based on configured traffic orientation. Track lane state transitions to fire lane-change cues.
* **Settings & UI**: Add `radarSpatialAudioEnabled`, `radarSpeedPitchModulation`, `radarTrafficOrientation`, and `radarLaneChangeTickEnabled` to `TuningPreferencesDataStore`. Embed audio preview controls in `RadarSettingsSection.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: (1) Zero regression in standard mono/speaker alerts. (2) Warm `MODE_STATIC` replay preserved without AudioFlinger port teardown. (3) 100% 9-language localization parity. (4) Parent ticket Human Decision Gate strictly enforced.
* **Risk Rating**: **LOW** — Additive audio modulation parameters and DataStore configuration with zero SQLite or concurrency impacts.
