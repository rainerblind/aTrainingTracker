# Stage 1 Analysis: ATT-5131 - Bike Bell Speaker Routing, Volume Boost & 3-Stage Escalation

**Ticket**: [ATT-5131](https://rainerblind.atlassian.net/browse/ATT-5131)  
**Sub-task**: [[ATT-5269]](https://rainerblind.atlassian.net/browse/ATT-5269) (`[Analysis]`)  
**Parent Epic**: Standalone Ticket  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Branch**: `feature/ATT-5131`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
The Cockpit Electronic Bike Bell (`BikeBellAudioSynthesizer` / `TrackingTabsViewModel`) exhibits 3 real-world acoustic defects:
1. **Headphone Audio Bleed**: Default routing plays bell audio into rider's connected Bluetooth/wired headphones instead of the external speaker, failing to alert pedestrians.
2. **Low System Volume Inaudibility**: `AudioTrack.setVolume(1.0f)` scales relative to system stream volume; low system volume renders the bell inaudible outdoors.
3. **Imperceptible Escalation**: Binary toggle (`0.70f` vs `1.00f`) yields only a 3.1 dB difference ($20 \log_{10}(1.0 / 0.70)$), barely 1 JND in outdoor wind noise.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Routing)**: `BikeBellAudioSynthesizer` uses `USAGE_MEDIA` without calling `AudioTrack.setPreferredDevice(AudioDeviceInfo)` for `TYPE_BUILTIN_SPEAKER`. Android routes `USAGE_MEDIA` to connected A2DP/headset sinks.
* **RCA 2 (Hardware Gain)**: The synthesizer lacks `AudioManager` hardware stream control and fails to boost hardware output during playback.
* **RCA 3 (Escalation Curve)**: `TrackingTabsViewModel.triggerBellRing()` implements a binary toggle instead of a logarithmic multi-stage progression.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Audited `BikeBellAudioSynthesizer.kt:167-172`: Confirmed `AudioAttributes.USAGE_MEDIA` without routing overrides.
  * Verified `ManifestAndBuildConfigContractTest.kt` and `MergedManifestFgsAuditTest.kt`: Neither enforces a closed permission whitelist. Declaring normal permission `android.permission.MODIFY_AUDIO_SETTINGS` in `AndroidManifest.xml` passes all contract checks.
  * Coding Standards §3.4 explicitly sanctions `AudioAttributes.USAGE_ALARM`. Combining `STREAM_ALARM` with `TYPE_BUILTIN_SPEAKER` ensures `STREAM_MUSIC` on headphones is completely unaffected when boosting hardware volume.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Force output routing to `AudioDeviceInfo.TYPE_BUILTIN_SPEAKER` via `setPreferredDevice()` on `singleStrikeTrack` and `rollTrack`.
  * Declare `android.permission.MODIFY_AUDIO_SETTINGS` in `AndroidManifest.xml`.
  * Temporarily boost `AudioManager.STREAM_ALARM` to `getStreamMaxVolume()` with `flags = 0` during ring/roll and auto-restore original volume.
  * Implement 3-stage logarithmic escalation in `TrackingTabsViewModel`: Tap 1 = `0.35f` (-9.1 dB), Tap 2 ($\le 5\text{ s}$) = `0.65f` (-3.7 dB), Tap 3+ / Long-Press = `1.00f` (0 dB), resetting after $> 5\text{ s}$ silence.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to Cockpit UI gesture handling (`BikeBellButton.kt` press monitoring remains intact).
  * No modification to database schema or `TrackingViewsRepository`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-421` (`REQ-UI-421.4`, `REQ-UI-421.5`).
* **Historical Origin & Commit Trace**: `ATT-4281` (Sprint 2026-43.1, commit `40af18141387`) introduced bike bell; `ATT-4371` (Sprint 2026-43.2, commit `bac64ff9`) established `USAGE_MEDIA` to bypass Silent/Vibrate ringer suppression.
* **Root Reason for Existing Formulation**: `USAGE_MEDIA` was adopted because `USAGE_ASSISTANCE_SONIFICATION` was muted in Silent mode. However, `USAGE_MEDIA` directs sound to headphones when connected.
* **Preservation of Core Invariants**: `USAGE_ALARM` bypasses Silent/Vibrate mode while providing an independent hardware stream (`STREAM_ALARM`) separate from `STREAM_MUSIC`, ensuring headphone music is not disrupted when volume is boosted.

---

## 5. Architectural Strategy & High-Level Solution
* **Synthesizer Routing & Volume Boost (`BikeBellAudioSynthesizer.kt`)**:
  * Accept `context: Context? = null` and `audioManager: AudioManager? = null` in constructor.
  * Configure `AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(CONTENT_TYPE_SONIFICATION).build()`.
  * Resolve `builtInSpeaker = audioManager?.getDevices(GET_DEVICES_OUTPUTS)?.firstOrNull { it.type == TYPE_BUILTIN_SPEAKER }` and apply `track.setPreferredDevice(builtInSpeaker)`.
  * In `ring()` and `startRoll()`, capture original `STREAM_ALARM` volume, set to max (`flags = 0`), and schedule auto-restore (+650 ms for ring, +150 ms after `stopRoll()`).
* **Escalation Progression (`TrackingTabsViewModel.kt`)**:
  * Pass `application` context to `BikeBellAudioSynthesizer`.
  * Track `bellEscalationStage: Int` (1..3) resetting after $> 5\text{ s}$ elapsed since `lastRingTimestampMs`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. No headphone volume spikes: `STREAM_MUSIC` volume and Bluetooth media playback remain strictly untouched.
  2. UI responsiveness: Volume boost uses `flags = 0` to prevent intrusive system volume dialog overlays.
  3. Clean lifecycle: Pending restore tasks and audio tracks released safely in `close()`.
* **Risk Rating**: **LOW** — Changes are strictly isolated to `BikeBellAudioSynthesizer`, `TrackingTabsViewModel`, and `AndroidManifest.xml` with zero database or cross-module ripple effects.
