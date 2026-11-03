# Stage 2: Requirement & Test Specification - ATT-5131: Bike Bell Speaker Routing, Volume Boost & 3-Stage Escalation

**Ticket**: [[ATT-5131]](https://rainerblind.atlassian.net/browse/ATT-5131)  
**Parent Epic**: Standalone Ticket  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-421` (`REQ-UI-421.4`, `REQ-UI-421.5`)  
**Test Mapping**: `TST-UI-384` (`TST-UI-384.3`, `TST-UI-384.4`, `TST-UI-384.9`)  
**Branch**: `feature/ATT-5131`  
**Author**: AI Agent 1 (flash / Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-421)

### 1.1 Problem Statement & Rationale
The Cockpit electronic bike bell plays into connected headphones instead of the loudspeaker, is attenuated when system volume is low, and has an imperceptible binary volume jump (3.1 dB). The system must force output to `TYPE_BUILTIN_SPEAKER`, temporarily boost `STREAM_ALARM` to 100% with auto-restore, and provide a 3-stage logarithmic escalation (`0.35f -> 0.65f -> 1.00f`).

### 1.2 Functional & Architectural Requirements
1. *Speaker Routing & Dedicated Alarm Stream (`REQ-UI-421.4a`)*: `BikeBellAudioSynthesizer` SHALL configure `AudioAttributes` with `USAGE_ALARM` and `CONTENT_TYPE_SONIFICATION`, and resolve `AudioDeviceInfo.TYPE_BUILTIN_SPEAKER` to invoke `track.setPreferredDevice(speaker)` on both `singleStrikeTrack` and `rollTrack` upon creation and prior to playback.
2. *Temporary Hardware Volume Boost & Auto-Restore (`REQ-UI-421.4b`)*: Before playback, `BikeBellAudioSynthesizer` SHALL capture `savedAlarmVolume = audioManager.getStreamVolume(STREAM_ALARM)` (if unboosted), set `STREAM_ALARM` to `getStreamMaxVolume` with `flags = 0`, and schedule auto-restore (+650 ms after `ring()`, +150 ms after `stopRoll()`).
3. *Manifest Permission (`REQ-UI-421.4c`)*: `AndroidManifest.xml` SHALL declare `<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />`.
4. *3-Stage Perceptual Loudness Escalation (`REQ-UI-421.5`)*: `TrackingTabsViewModel.triggerBellRing()` SHALL progress through 3 volume stages within a 5-second window: Stage 1 = `0.35f` (-9.1 dB), Stage 2 = `0.65f` (-3.7 dB), Stage 3+ / Long-Press = `1.00f` (0 dB), resetting to Stage 1 after $> 5\text{ s}$ silence.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-UI-421.4` and `REQ-UI-421.5`, targeting `BikeBellAudioSynthesizer.kt`, `TrackingTabsViewModel.kt`, and `AndroidManifest.xml`.
2. *Historical Origin & Commit Trace*: Sprint 2026-43.1 commit `40af18141387` (ATT-4281) and Sprint 2026-43.2 commit `bac64ff9` (ATT-4371) introduced `USAGE_MEDIA` to bypass Silent mode.
3. *Root Reason for Existing Formulation*: `USAGE_MEDIA` bypassed Silent mode but routed sound to connected headphones and lacked hardware gain boosting.
4. *Preservation of Core Invariants*: `USAGE_ALARM` combined with `TYPE_BUILTIN_SPEAKER` preserves Silent mode bypass, forces phone speaker output, and boosts bell volume without altering `STREAM_MUSIC` on headphones.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Speaker Routing)**: *Given* headphones connected, *When* bell rings or rolls, *Then* audio routes to `TYPE_BUILTIN_SPEAKER`.
* **Criterion 2 (Hardware Boost & Restore)**: *Given* `STREAM_ALARM` volume at 2/15, *When* bell rings, *Then* `STREAM_ALARM` boosts to 15 with `flags = 0` and auto-restores to 2.
* **Criterion 3 (3-Stage Escalation)**: *Given* silence $> 5\text{ s}$, *When* tapped 3 times within 5s, *Then* volumes are `0.35f`, `0.65f`, `1.00f`, resetting to `0.35f` after 5s.

---

## 2. Test Specification (TST-UI-384)

### Test Case 1: Speaker Routing & Hardware Volume Boost (`TST-UI-384.3`)
* **Scope**: Unit Test (`BikeBellAudioSynthesizerTest.kt`)
* **Procedure**: Verify `USAGE_ALARM`, `setPreferredDevice(builtInSpeaker)`, `setStreamVolume(STREAM_ALARM, max, 0)`, and delayed restore to original volume.

### Test Case 2: 3-Stage Loudness Escalation Logic (`TST-UI-384.4`)
* **Scope**: ViewModel Unit Test (`TrackingTabsViewModelTest.kt`)
* **Procedure**: Tap 1 (`0.35f`), Tap 2 within 5s (`0.65f`), Tap 3 within 5s (`1.00f`), Tap 4 (`1.00f`), wait 6s (`0.35f`), long-press (`1.00f`).

### Test Case 3: Manifest Permission Contract (`TST-UI-384.9`)
* **Scope**: Contract Test (`ManifestAndBuildConfigContractTest.kt`)
* **Procedure**: Assert `AndroidManifest.xml` contains `android.permission.MODIFY_AUDIO_SETTINGS`.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-384.8`)
* **Scope**: Tier 1 Unit Tests (`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.*" --tests "com.atrainingtracker.trainingtracker.build.*"`)
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-384.3` | Unit | `BikeBellAudioSynthesizer.kt` | `REQ-UI-421.4a`, `REQ-UI-421.4b` | Planned |
| `TST-UI-384.4` | ViewModel | `TrackingTabsViewModel.kt` | `REQ-UI-421.5` | Planned |
| `TST-UI-384.9` | Contract | `AndroidManifest.xml` | `REQ-UI-421.4c` | Planned |
| `TST-UI-384.8` | Regression | Tier 1 Audio & Tracking Unit Tests | `REQ-PRO-001` | Planned |
