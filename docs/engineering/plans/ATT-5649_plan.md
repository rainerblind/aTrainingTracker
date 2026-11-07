# Stage 3: Implementation Plan - ATT-5649: Fix clipped/muted bell audio across all Android devices

**Ticket**: [ATT-5649](https://rainerblind.atlassian.net/browse/ATT-5649)  
**Sub-task**: [ATT-5737](https://rainerblind.atlassian.net/browse/ATT-5737) (`[Impl-Plan]`)  
**Parent Epic**: Standalone Ticket  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.9`  
**Requirement Mapping**: `REQ-UI-421.4`, `REQ-UI-421.7`  
**Test Mapping**: `TST-UI-384.3a`, `TST-UI-384.3b`, `TST-UI-384.3c`  
**Branch**: `feature/ATT-5649`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Problem Description & Background
On modern Android devices, `BikeBellAudioSynthesizer` chimes suffer from clipping, muting, or distortion due to:
1. AudioFlinger port teardown on static `track.stop()`, causing 200–385 ms playback latency.
2. Unconditional `setPreferredDevice(builtInSpeaker)` triggering routing thrashing between `SPEAKER` and `SPEAKER_SAFE`.
3. Premature 650 ms volume restoration cutting off bell decay and triggering OS hardware gain mutes during the 5 s escalation window.
4. SmartPA Class-D amplifier standby latency (~30–40 ms) and 44.1 kHz resampling overhead.
5. Abrupt 0.6 s buffer truncation with ~35–40% residual amplitude.

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-421.4` (Acoustic Brass Synthesis & Audio Routing)** $\leftrightarrow$ `TST-UI-384.3a`, `TST-UI-384.3b`, `TST-UI-384.3c`: 48 kHz native rate, 40 ms SmartPA lead-in, 0.95 s duration with 60 ms cosine fade-out, conditional speaker routing, 5.5 s volume restore, max volume guard, and warm `pause()` static replay.
* **`REQ-UI-421.7` (System Invariants & Non-Regression)** $\leftrightarrow$ `TST-UI-384.3`: Thread safety under `synchronized(lock)`, zero disk I/O in `MODE_STATIC`, and clean lifecycle teardown in `close()`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero-Latency In-Memory Playback**: Audio remains pre-synthesized in RAM via `AudioTrack.MODE_STATIC`.
2. **Loud Speaker Output Under Headphones**: When headphones are plugged in, audio routes cleanly to `TYPE_BUILTIN_SPEAKER`.
3. **Escalation & UI Stability**: 3-stage escalation in `TrackingTabsViewModel` (`0.35f -> 0.65f -> 1.00f`) and Cockpit gestures remain 100% unchanged.
4. **Code Lock**: Zero production code edits in `app/src/...` prior to Gate 3 PASS.

---

## 4. Proposed Architectural Changes
* **Component: `BikeBellAudioSynthesizer.kt`**:
  - Update constants: `SAMPLE_RATE = 48000`, `LEAD_IN_SEC = 0.040`, `DOUBLE_STRIKE_DURATION_SEC = 0.95`, `FADE_OUT_SEC = 0.060`, `RING_RESTORE_DELAY_MS = 5500L`.
  - Double strike PCM synthesis: offset strikes by `LEAD_IN_SEC` (strike 1 at $t=0.040\text{ s}$, strike 2 at $t=0.150\text{ s}$) and apply raised cosine fade-out over final 60 ms.
  - Conditional speaker routing: inspect `AudioManager.GET_DEVICES_OUTPUTS` for external audio devices; set `builtInSpeaker` if connected, else `track.setPreferredDevice(null)`.
  - Max volume guard in `boostAlarmVolumeLocked()`: only boost and capture if `currentVolume < maxVolume`.
  - Warm static replay: replace `track.stop()` with `track.pause()` in `ring()`, `startRoll()`, and `stopRoll()`.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Synthesis Constants, SmartPA Lead-In & Cosine Fade-Out (`BikeBellAudioSynthesizer.kt`, `TST-UI-384.3a`)
* Update `SAMPLE_RATE = 48000`, `LEAD_IN_SEC = 0.040`, `DOUBLE_STRIKE_DURATION_SEC = 0.95`, `FADE_OUT_SEC = 0.060`.
* In `synthesizeDoubleStrikePcm`, shift strikes past lead-in and apply cosine window over last 60 ms down to 0.

### Step 2: Conditional Speaker Routing & Volume Guard (`BikeBellAudioSynthesizer.kt`, `TST-UI-384.3b/c`)
* In `applySpeakerRouting(track)`, detect external audio devices (`TYPE_BLUETOOTH_A2DP`, `TYPE_BLUETOOTH_SCO`, `TYPE_BLE_*`, `TYPE_WIRED_*`, `TYPE_USB_*`, `TYPE_HEARING_AID`). If present, set `builtInSpeaker`; otherwise set `null`.
* In `boostAlarmVolumeLocked()`, guard with `currentVolume < maxVolume`. Set `RING_RESTORE_DELAY_MS = 5500L`.

### Step 3: Warm Static Replay (`BikeBellAudioSynthesizer.kt`, `TST-UI-384.3c`)
* In `ring()`, `startRoll()`, and `stopRoll()`, replace `track.stop()` with `track.pause()` before `playbackHeadPosition = 0`.

### Step 4: Unit Test Suite Updates (`BikeBellAudioSynthesizerTest.kt`, `TST-UI-384.3a/b/c`)
* Update buffer size expectations (91,200 bytes for double-strike, 240,000 bytes for roll at 48 kHz).
* Add assertions for SmartPA lead-in, cosine fade-out, conditional routing, `pause()` replay, and 5.5 s volume restore.

### Step 5: Verification & Documentation Updates (`TST-UI-384.3`, Rule 53)
* Run targeted unit test suite: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.BikeBellAudioSynthesizerTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModelTest"`
* Run requirement governance verification: `python3 tools/verify_requirement_governance.py`
* In Stage 5, update `docs/coding_standards.md` §3.4 noting warm `pause()` replay preference for static tracks.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted audio unit tests covering buffer properties, routing, and lifecycle under MockK/Robolectric.
* **Rollback**: Clean git branch isolation on `feature/ATT-5649`; zero schema or database changes.
