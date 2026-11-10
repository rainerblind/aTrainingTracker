# Stage 1 Analysis: ATT-5086 - Radar Audio Chime & Haptic Alert Engine

**Ticket**: [ATT-5086](https://rainerblind.atlassian.net/browse/ATT-5086)  
**Sub-task**: [ATT-6573](https://rainerblind.atlassian.net/browse/ATT-6573) (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5086`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Under Epic ATT-1286, cyclists in traffic require instant, eyes-free auditory and haptic awareness of rearward vehicle threats cutting through wind and road noise:
1. **Eyes-Free Situational Alerts**: Emitting distinct acoustic multi-tone chimes and vibration patterns on threat transitions (`APPROACHING`, `FAST_APPROACHING`, `ALL_CLEAR`, `DISCONNECTED_ERROR`).
2. **Foreground & Screen-Off Execution**: Alerts must trigger reliably during tracking in `TrackerService` even when the screen is off or dimmed.
3. **Independent Volume & Speaker Boost**: Riders often have everyday phone volume low. The alert engine must support independent alert volume (10–100%, default 90%) and temporarily boost phone speaker stream volume for ~300 ms without system UI popups (`flags = 0`), auto-restoring everyday volume immediately.
4. **Transient Audio Ducking & Headphone Routing**: Duck background music/podcasts (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) and support both connected Bluetooth headphones and forced phone speaker output (`AudioTrack.setPreferredDevice()`).

---

## 2. Root Cause Analysis, Gap Analysis & Independent Peer Review
* **Architectural Gap**: `RadarStateRepository` provides a 4–8 Hz `StateFlow<RadarState>`, but no alert coordinator or synthesizer exists to process threat transitions into audio/haptic signals.
* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  - *AudioTrack Invariant (§3.4)*: `AudioAttributes.USAGE_MEDIA` must be used with warm `MODE_STATIC` replay (`track.pause()` + `playbackHeadPosition = 0` + `track.play()`). Calling `stop()` is strictly prohibited to avoid 200–385 ms AudioFlinger renegotiation latency and attack transient truncation (ATT-5649).
  - *Transient Ducking*: Requesting `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` ducking for ~300 ms and abandoning immediately restores Spotify/podcasts without stealing persistent playback focus.
  - *Speaker vs. Headphone Routing*: `AudioTrack.setPreferredDevice(AudioDeviceInfo)` (using `TYPE_BUILTIN_SPEAKER`) routes to phone speaker when forced, or `null` for default Bluetooth/wired headphone routing without switching `AudioManager` into telephony `MODE_IN_COMMUNICATION`.
  - *Transition Debouncing*: Guarding state transitions with hysteresis and timestamp cooldown prevents chime spamming on every 8 Hz frame update.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit
* **In-Scope Goals (ATT-5086 - 3 SP)**:
  - Implement `RadarAlertManager.kt` and `RadarAudioSynthesizer.kt` (`USAGE_MEDIA`, `MODE_STATIC` PCM multi-tone synthesis: 2.2–3.2 kHz harmonics).
  - Implement `VibrationEffect` haptic alerts, transient audio ducking, and temporary speaker volume boost & restore.
  - Bind `RadarAlertManager` to `TrackerService` lifecycle observing `RadarStateRepository.radarState`.
  - Add preferences to `TuningPreferencesDataStore.kt` & controls to `RadarSettingsSection.kt`.
  - 9-language localization parity in `strings_radar.xml` (zero inline emojis).
  - Comprehensive unit test coverage for synthesizer, state transitions, ducking, and preferences.
* **Out-of-Scope Non-Goals**:
  - `ATT-5088`: Post-ride radar telemetry persistence and close-pass safety analytics.
  - `ATT-5095` / `ATT-5096`: BLE V2 lateral lane offset and vehicle sizing decoders.
  - `ATT-5102`: ANT+/BLE tail light hardware control profiles.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Shard**: `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
* **Refined Requirements**: Net-new `REQ-TRK-063` (*Radar Audio Chime and Haptic Alert Engine*) and `TST-TRK-063`.
* **Chesterton's Fence**: Legacy alerts (`NavigationAudioAlertManager`) used `ToneGenerator(STREAM_NOTIFICATION)`, which mutes under Silent/Vibrate profiles. Coding standard §3.4 mandates `USAGE_MEDIA` or `USAGE_ALARM`. Volume boost must restore original volume defensively in `finally` and service `onDestroy()` to prevent leaving phone volume unexpectedly high.

---

## 5. Architectural Strategy & System Invariants
* **Components**: `RadarAlertManager.kt` & `RadarAudioSynthesizer.kt` in `com.atrainingtracker.trainingtracker.audio.radar`.
* **Core Invariants**:
  1. Guaranteed restoration of everyday audio volume after chime completion or service termination.
  2. Adherence to coding standards §3.4 (warm `MODE_STATIC` replay, zero `track.stop()`).
  3. Zero main-thread blocking or audio dropout during 4–8 Hz telemetry reception.
  4. 100% 9-language translation parity in `strings_radar.xml`.
* **Risk Rating**: **MEDIUM** (Audio subsystem, audio focus, hardware volume, and foreground service lifecycle interactions; fully mitigable via unit tests and established `BikeBellAudioSynthesizer` patterns).
