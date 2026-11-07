# Stage 1 Analysis: ATT-5649 - BikeBellAudioSynthesizer: Fix clipped/muted bell audio across all Android devices

**Ticket**: [ATT-5649](https://rainerblind.atlassian.net/browse/ATT-5649)  
**Sub-task**: [ATT-5735](https://rainerblind.atlassian.net/browse/ATT-5735) (`[Analysis]`)  
**Parent Epic**: Standalone Ticket  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.9`  
**Branch**: `feature/ATT-5649`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
On modern Android devices (Pixel, Samsung, Xiaomi, OnePlus), the electronic bike bell (`BikeBellAudioSynthesizer`) produces clipped, muted, or distorted audio upon interaction. Analysis via `dumpsys audio` and `dumpsys media.audio_policy` reveals that cold mixer port renegotiation, routing port-thrashing (`SPEAKER` vs `SPEAKER_SAFE`), premature alarm stream volume reset, amplifier cold-start clipping, and hard buffer cut-offs degrade acoustic reproduction.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  1. **AudioFlinger Mixer Port Teardown (`track.stop()`)**: Calling `track.stop()` in `MODE_STATIC` moves the track to `STATE_STOPPED`, tearing down the AudioFlinger mixer port and putting the hardware mixer in standby. Subsequent `play()` incurs 200–385 ms setup latency while playhead advances past initial transients. *Remedy*: Warm replay using `track.pause()` + `track.playbackHeadPosition = 0` + `track.play()`. (Note: Stage 5 will update `docs/coding_standards.md` §3.4 to document this static replay pattern).
  2. **Speaker Routing Port Thrashing**: Unconditionally calling `setPreferredDevice(builtInSpeaker)` forces switching between `SPEAKER_SAFE` (port 4, default for `USAGE_ALARM`) and `SPEAKER` (port 3) on every ring, causing repeated routing renegotiation and up to 385 ms silence. *Remedy*: Only set `setPreferredDevice(builtInSpeaker)` when an external output device (Bluetooth A2DP/SCO/BLE, wired/USB headset, hearing aid) is connected; otherwise clear with `setPreferredDevice(null)`.
  3. **Premature Volume Restore (`650 ms`)**: `RING_RESTORE_DELAY_MS = 650L` restores volume while the chime is still ringing and causes rapid `3 -> 7 -> 3 -> 7` volume thrashing during the 5 s escalation window (`TrackingTabsViewModel`), triggering Android's hardware gain mute (`event:muted`). *Remedy*: Increase `RING_RESTORE_DELAY_MS` to `5500L` (matching 5 s escalation window) and guard `setStreamVolume` with `currentVolume < maxVolume`.
  4. **SmartPA Amplifier Standby & Sample Rate**: Hardware Class-D amplifiers (SmartPA) require ~30–40 ms to wake from standby; strike 1 at $t=0$ has its transient swallowed. Modern AudioFlinger mixers also operate natively at 48 kHz (44.1 kHz forces resampling). *Remedy*: Change `SAMPLE_RATE` to `48000 Hz` and add a 40 ms SmartPA wakeup pilot lead-in (`LEAD_IN_SEC = 0.040`) before strike 1 ($t=0.040\text{ s}$) and strike 2 ($t=0.150\text{ s}$).
  5. **Buffer Truncation & DC Click**: `DOUBLE_STRIKE_DURATION_SEC = 0.6` truncates the second strike at ~35–40% residual amplitude. *Remedy*: Extend duration to `0.95 s` with a 60 ms cosine fade-out (`FADE_OUT_SEC = 0.060`) tapering smoothly to zero.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Update `BikeBellAudioSynthesizer.kt` constants and synthesis: 48 kHz sample rate, 40 ms SmartPA lead-in, 0.95 s duration with 60 ms cosine fade-out, 5500 ms restore delay.
  - Implement conditional speaker routing in `applySpeakerRouting()` detecting external output devices.
  - Implement warm replay via `track.pause()` + `playbackHeadPosition = 0` in `ring()`, `startRoll()`, and `stopRoll()`.
  - Guard alarm volume boost against redundant calls when already at max volume.
  - Update unit tests in `BikeBellAudioSynthesizerTest.kt` to verify all 5 improvements.
* **Out-of-Scope Non-Goals**:
  - Changes to UI components (`BikeBellButton.kt`, `SensorGridScreen.kt`, `TrackingTabsScreen.kt`).
  - Changes to escalation stage logic or thresholds in `TrackingTabsViewModel.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-421.4` (Acoustic Brass Synthesis, Concurrency Safety & Audio Focus) & `REQ-UI-421.7` (Preservation of System Invariants).
* **Historical Origin & Commit Trace**: `ATT-4371` introduced `track.stop()` before `playbackHeadPosition = 0` because static tracks threw `ERROR_INVALID_OPERATION` if rewound after finishing without stop. `ATT-5131` introduced `USAGE_ALARM` with forced `setPreferredDevice(builtInSpeaker)` and 650 ms volume boost. `ATT-5282` added 5-mode brass synthesis.
* **Root Reason for Existing Formulation**: `stop()` ensured reset on older OS versions, but modern AudioFlinger destroys the active port on `stop()`. `setPreferredDevice` was applied unconditionally to guarantee speaker playback with headphones, but causes port thrashing when no headphones are connected.
* **Preservation of Core Invariants**: `pause()` + `playbackHeadPosition = 0` reliably rewinds static tracks without tearing down the mixer port. Conditional routing preserves external speaker projection when headphones are connected while letting Android route `USAGE_ALARM` naturally without thrashing.

---

## 5. Architectural Strategy & High-Level Solution
* Modify `BikeBellAudioSynthesizer.kt`:
  - `SAMPLE_RATE = 48000`, `DOUBLE_STRIKE_DURATION_SEC = 0.95`, `LEAD_IN_SEC = 0.040`, `FADE_OUT_SEC = 0.060`, `RING_RESTORE_DELAY_MS = 5500L`.
  - `applySpeakerRouting(track)`: inspect `AudioManager.GET_DEVICES_OUTPUTS` for external audio devices; set `builtInSpeaker` if present, otherwise set `null`.
  - `ring()`, `startRoll()`, `stopRoll()`: replace `track.stop()` with `track.pause()`.
  - `boostAlarmVolumeLocked()`: guard `currentVolume < maxVolume`.
* Update `BikeBellAudioSynthesizerTest.kt` for 48 kHz buffer dimensions, 5500 ms delay, conditional routing, and `pause()` invocations.
* In Stage 5, update `docs/coding_standards.md` §3.4 to document warm `pause()` replay for static tracks.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero latency `AudioTrack.MODE_STATIC` guarantee; thread safety under `synchronized(lock)`; audible speaker projection during headphone use; clean release on `close()`.
* **Risk Rating**: **LOW** — Self-contained purely within `BikeBellAudioSynthesizer`, covered by Robolectric/MockK unit tests with zero UI or database coupling.
