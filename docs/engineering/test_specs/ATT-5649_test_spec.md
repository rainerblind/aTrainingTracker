# Stage 2: Requirement & Test Specification - ATT-5649: Fix clipped/muted bell audio across all Android devices

**Ticket**: [ATT-5649](https://rainerblind.atlassian.net/browse/ATT-5649)  
**Parent Epic**: Standalone Ticket | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5649`  
**Requirement Mapping**: `REQ-UI-421.4`, `REQ-UI-421.7` | **Test Spec ID**: `TST-UI-384.3`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-UI-421.4`, `REQ-UI-421.7`)

### 1.1 Problem Statement & Rationale
On modern Android devices, `BikeBellAudioSynthesizer` exhibits clipped, muted, or distorted chimes caused by 5 interdependent audio lifecycle factors: AudioFlinger port teardown via `stop()`, port thrashing between `SPEAKER` and `SPEAKER_SAFE`, premature 650 ms volume restore with hardware gain mute events, SmartPA amplifier cold-start latency, and buffer truncation at 0.6 s.

### 1.2 Functional & Architectural Requirements
1. **Native 48 kHz & SmartPA Lead-In**: Synthesizer SHALL operate at $48\text{ kHz}$ native sample rate (`SAMPLE_RATE = 48000`), include a $40\text{ ms}$ SmartPA amplifier warmup lead-in (`LEAD_IN_SEC = 0.040`) before strike 1 ($t = 0.040\text{ s}$) and strike 2 ($t = 0.150\text{ s}$), and extend double-strike duration to $0.95\text{ s}$ (`DOUBLE_STRIKE_DURATION_SEC = 0.95`) with a $60\text{ ms}$ raised cosine fade-out (`FADE_OUT_SEC = 0.060`) tapering cleanly to zero.
2. **Conditional Speaker Routing**: Synthesizer SHALL only invoke `setPreferredDevice(builtInSpeaker)` when an external audio output device (Bluetooth A2DP/SCO/BLE, wired/USB headset, hearing aid) is detected in `AudioManager.GET_DEVICES_OUTPUTS`; otherwise it SHALL set `setPreferredDevice(null)` to let `AudioPolicyManager` route `USAGE_ALARM` via `SPEAKER_SAFE` without port thrashing.
3. **5.5 s Volume Restore & Max Volume Guard**: `RING_RESTORE_DELAY_MS` SHALL be extended to $5500\text{ ms}$ matching the $5\text{-second}$ escalation window in `TrackingTabsViewModel`, and `setStreamVolume` SHALL only be called if `currentVolume < maxVolume`.
4. **Warm Static Replay**: `ring()`, `startRoll()`, and `stopRoll()` SHALL invoke `track.pause()` (never `track.stop()`) before `track.playbackHeadPosition = 0` to maintain active AudioFlinger mixer sessions.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Warm Replay & No Thrashing)**: *Given* no external headphones connected, *When* `ring()` is invoked repeatedly within 5 seconds, *Then* `track.pause()` is called, no speaker port renegotiation occurs, and both strikes sound fully without truncation.
* **AC-2 (Conditional Speaker Routing)**: *Given* Bluetooth or wired headphones connected, *When* `ring()` or `startRoll()` is invoked, *Then* `track.setPreferredDevice(builtInSpeaker)` routes audio to the phone loudspeaker.
* **AC-3 (Volume Restore Window)**: *Given* `STREAM_ALARM` < max, *When* multiple rings occur during 5 s escalation, *Then* volume is boosted to max without intermediate hardware mute events and auto-restores 5500 ms after the last ring.
* **Invariants**: Thread safety via `synchronized(lock)`; `MODE_STATIC` zero disk I/O; clean release in `close()`.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-421.4` (Acoustic Brass Synthesis) & `REQ-UI-421.7` in `docs/requirements/req_ui_design_system.md`.
* `Historical Origin & Commit Trace`: `ATT-4371` added `stop()` to reset static tracks. `ATT-5131` introduced `USAGE_ALARM` with unconditional `setPreferredDevice` and 650 ms restore. `ATT-5282` introduced 5-mode brass synthesis at 44.1 kHz.
* `Root Reason for Existing Formulation`: `stop()` avoided older Android `ERROR_INVALID_OPERATION`, but modern AudioFlinger destroys the active port on `stop()`. Unconditional `setPreferredDevice` ensured speaker output with headphones, but causes port-thrashing (port 3 vs 4) without headphones.
* `Preservation of Core Invariants`: `pause()` + `playbackHeadPosition = 0` reliably rewinds static tracks. Conditional routing preserves loud speaker projection with headphones while preventing port thrashing without headphones.

---

## 2. Test Specification (`TST-UI-384.3`)

### Test Case 1: 48 kHz Buffer Dimensions, Lead-In & Cosine Fade-Out (`TST-UI-384.3a`)
* **Scope**: Unit Test (`BikeBellAudioSynthesizerTest.kt`)
* **Preconditions**: Synthesizer instantiated at 48000 Hz.
* **Action**: Synthesize double-strike and roll PCM buffers; evaluate sample amplitudes at lead-in, attack, and fade-out.
* **Expected Result**: Double-strike buffer is $0.95\text{ s} \times 48000 \times 2 = 91200\text{ bytes}$; lead-in $t < 0.040\text{ s}$ contains silent/pilot audio; final 60 ms smoothly fades to 0.

### Test Case 2: Conditional Speaker Routing & Device Type Gating (`TST-UI-384.3b`)
* **Scope**: Unit Test (`BikeBellAudioSynthesizerTest.kt`)
* **Preconditions**: Mock `AudioManager` with varying output devices (headphones present vs absent).
* **Action**: Invoke `ring()` and `startRoll()`.
* **Expected Result**: When external device present, `setPreferredDevice(builtInSpeaker)` is invoked; when only built-in speaker present, `setPreferredDevice(null)` is called.

### Test Case 3: Warm Replay (`pause()`) & 5.5 s Volume Restore (`TST-UI-384.3c`)
* **Scope**: Unit Test (`BikeBellAudioSynthesizerTest.kt`)
* **Preconditions**: Mock `AudioTrack`, `AudioManager`, and `Handler`.
* **Action**: Invoke `ring()`, rapid repeat `ring()`, and `close()`.
* **Expected Result**: `track.pause()` is verified (zero `track.stop()` calls); restore delayed by 5500 ms; `setStreamVolume` only called if initial volume was below max.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-384.3a` | Unit | `BikeBellAudioSynthesizer.synthesizeDoubleStrikePcm` | `REQ-UI-421.4` | Specified |
| `TST-UI-384.3b` | Unit | `BikeBellAudioSynthesizer.applySpeakerRouting` | `REQ-UI-421.4` | Specified |
| `TST-UI-384.3c` | Unit | `BikeBellAudioSynthesizer.ring`, `startRoll` | `REQ-UI-421.4`, `REQ-UI-421.7` | Specified |
