# Stage 1 Analysis: ATT-6709 - Disable phone-speaker stereo attenuation on SYSTEM_DEFAULT, select FAST_APPROACHING target kinematics, add per-target lane-change hysteresis, and release preview AudioTracks

**Ticket**: [ATT-6709](https://rainerblind.atlassian.net/browse/ATT-6709)  
**Sub-task**: [ATT-6930](https://rainerblind.atlassian.net/browse/ATT-6930) (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6709`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Sprint 2026-46.1 review audit of `ATT-5120` identified three distinct functional and architectural defects across spatial radar audio and settings:
1. `RadarSpatialAudioMath.computeStereoGains()` only bypasses panning when `route == FORCE_SPEAKER`. On `SYSTEM_DEFAULT` without connected headphones/Bluetooth, built-in phone speaker playback attenuates one stereo channel down to `0.20f`, compromising outdoor alert audibility. Additionally, parameter `trafficOrientation` is declared and documented in KDoc but unused in the body.
2. In `RadarAlertManager.kt`, `primaryTarget` selects solely by minimum range (`minByOrNull { it.rangeMeters }`). When a slow car trails closely (`25 m`, `5 m/s`) while a fast threat appears further back (`90 m`, `28 m/s`), `triggerFastThreatAlert` plays with the slow car's low pitch and centered pan. Moreover, lane-change detection tracks only a single scalar target without spatial/temporal hysteresis, fluttering on 8 Hz lateral jitter.
3. `RadarAlertManager.previewSynthesizer` allocates 5 native `AudioTrack`s that are never released on Settings disposal; `RadarAudioSynthesizer` calls deprecated `setStereoVolume()` on a `CHANNEL_OUT_MONO` track; and `RadarSettingsSection` uses stateless `FilterChip(selected = false)` buttons and full-size `Switch` controls for subordinate settings.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Speaker Attenuation on SYSTEM_DEFAULT)**: `computeStereoGains()` checked only `route == FORCE_SPEAKER`. In `SYSTEM_DEFAULT`, Android routes audio to the built-in speaker when no headset is present, but `computeStereoGains` still attenuated channels. `trafficOrientation` was left unreferenced because Cartesian panning directly maps negative lateral offset to the left channel.
* **RCA 2 (Kinematics Misattribution & Lane Flutter)**: `processRadarStateUpdate()` bound `primaryTarget` strictly by distance before dispatching `triggerFastThreatAlert()`. Furthermore, lane-change tracking used a single scalar pair (`lastTrackedTargetId`/`lastTrackedTargetOffset`) without multi-car convoy support, spatial transition boundary hysteresis, or per-target cooldown.
* **RCA 3 (AudioTrack Leaks & UI Inconsistencies)**: `previewSynthesizer` lived in static companion state without a lifecycle cleanup hook. Calling `setStereoVolume()` on `CHANNEL_OUT_MONO` was an API misuse deprecated since API 21. `FilterChip` was misapplied to one-shot chime actions.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `hasHeadphones: Boolean = true` to `computeStereoGains()`, collapsing gains to `(1.0f, 1.0f)` on `SYSTEM_DEFAULT` when false; clean up and document `trafficOrientation` usage.
  2. For `FAST_APPROACHING` alerts, select the fast-approaching target (`filter { it.threatLevel == FAST_APPROACHING }.maxByOrNull { it.approachSpeedMps } ?: primaryTarget`).
  3. Implement per-target `LaneTrackState` map with spatial hysteresis (in-lane $|x| < 1.0$ m to passing $|x| \ge 1.5$ m) and per-target cooldown (`3,000 ms`), pruning departed targets.
  4. Provide `releasePreviewSynthesizer()` in `RadarAlertManager` and hook it to `DisposableEffect` in `RadarSettingsSection.kt`.
  5. Replace deprecated `setStereoVolume()` on mono track in `RadarAudioSynthesizer.kt` with standard `setVolume()`.
  6. In `RadarSettingsSection.kt`: Convert stateless preview `FilterChip`s to `AssistChip`/`OutlinedButton` and subordinate toggles to `SubordinateTuningToggleItem`.
* **Out-of-Scope Non-Goals**:
  * Rewriting acoustic frequency synthesis or haptic patterns.
  * Modifying BLE GATT radar packet decoding.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Reference**: `REQ-TRK-063` (Radar Alert Engine), `REQ-TRK-065` (Spatial Audio & Speed Pitch Modulation), `REQ-UI-512` (Radar Settings UI).
* **Historical Origin**: Commit `28fb9956` (spatial audio math) and `85790a1b` (alert manager).
* **Preservation of Invariants**: Peak volume on built-in speakers preserved; clean-room 60 fps UI rendering preserved; 100% 9-language translation parity.

---

## 5. Architectural Strategy & High-Level Solution
* **Spatial Audio Math**: Support `hasHeadphones: Boolean = true` parameter in `computeStereoGains()`.
* **Alert Target Selection**: Select `FAST_APPROACHING` target kinematics for fast threat chime and per-target `LaneTrackState` for safe passing cues.
* **Resource Lifecycle & UI Cleanup**: Expose `releasePreviewSynthesizer()`, clean up `RadarSettingsSection.kt` chips, and adopt `SubordinateTuningToggleItem`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Loudness preserved on built-in speaker across all routes.
  2. Zero native `AudioTrack` resource leaks on Settings exit.
  3. Strict adherence to Design Guidelines §1.2 & §3.3.
* **Risk Rating**: **LOW** (Targeted corrections in math, state routing, and Compose UI).
