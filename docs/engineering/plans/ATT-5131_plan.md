# Stage 3: Implementation Plan - ATT-5131: Bike Bell Speaker Routing, Volume Boost & 3-Stage Escalation

**Ticket**: [[ATT-5131]](https://rainerblind.atlassian.net/browse/ATT-5131)  
**Sub-task**: [[ATT-5271]](https://rainerblind.atlassian.net/browse/ATT-5271) (`[Impl-Plan]`)  
**Parent Epic**: Standalone Ticket  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-421` (`REQ-UI-421.4a/b/c`, `REQ-UI-421.5`)  
**Test Mapping**: `TST-UI-384` (`TST-UI-384.3`, `TST-UI-384.4`, `TST-UI-384.8`, `TST-UI-384.9`)  
**Branch**: `feature/ATT-5131`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
The Cockpit electronic bike bell routes audio to connected headphones instead of the phone's built-in speaker, is attenuated when system volume is low, and uses an imperceptible binary volume toggle (3.1 dB). This plan forces output routing to `TYPE_BUILTIN_SPEAKER`, temporarily boosts `STREAM_ALARM` to 100% with auto-restore, and implements a 3-stage logarithmic escalation (`0.35f -> 0.65f -> 1.00f`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-421.4` (Speaker routing, `USAGE_ALARM`, 100% volume boost), `REQ-UI-421.5` (3-stage escalation).
* **Test Mapping**: `TST-UI-384.3` (Synthesizer unit test), `TST-UI-384.4` (ViewModel escalation unit test), `TST-UI-384.9` (Manifest contract test), `TST-UI-384.8` (Tier 1 regression suite).

---

## 3. System Invariants & Preserved Behavior
1. **Audio Isolation**: `STREAM_MUSIC` volume on connected headphones is strictly unaffected when boosting `STREAM_ALARM`.
2. **Headphone Bypass**: Bell audio always projects out of the phone's physical loudspeaker (`TYPE_BUILTIN_SPEAKER`).
3. **No UI Overlays**: `setStreamVolume()` passes `flags = 0`, preventing Android system volume dialogs from blocking Cockpit telemetry.
4. **Lifecycle & Clean Teardown**: Hardware volume is reliably restored to original level on completion, timeout (+650 ms / +150 ms), or `close()`.

---

## 4. Proposed Architectural Changes
* **`AndroidManifest.xml`**: Declare `<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />`.
* **`BikeBellAudioSynthesizer.kt`**:
  * Set `USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION` in `createAudioAttributes()`.
  * Accept `context: Context? = null`, `audioManager: AudioManager?`, `mainHandler: Handler?`.
  * Query `GET_DEVICES_OUTPUTS` for `TYPE_BUILTIN_SPEAKER` and apply `track.setPreferredDevice(speaker)`.
  * Capture `savedAlarmVolume`, boost `STREAM_ALARM` to `getStreamMaxVolume` with `flags = 0`, and schedule auto-restore.
* **`TrackingTabsViewModel.kt`**: Pass `application` context to synthesizer; maintain `bellEscalationStage: Int` (1..3) resetting after $> 5\text{ s}$ silence (`0.35f` -> `0.65f` -> `1.00f`).
* **UI Consistency**: No UI changes (`BikeBellButton.kt` composable and gestures remain unchanged).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Manifest Permission & Contract Test (`REQ-UI-421.4c`, `TST-UI-384.9`)
* Files: `app/src/main/AndroidManifest.xml`, `app/src/test/.../build/ManifestAndBuildConfigContractTest.kt`
* Add `MODIFY_AUDIO_SETTINGS` permission and assert declaration in contract test.

### Step 2: Speaker Routing & Volume Boost in Synthesizer (`REQ-UI-421.4a/b`, `TST-UI-384.3`)
* Files: `app/src/main/.../audio/BikeBellAudioSynthesizer.kt`, `app/src/test/.../audio/BikeBellAudioSynthesizerTest.kt`
* Implement `USAGE_ALARM`, `setPreferredDevice()`, volume boost with delayed auto-restore, and update unit tests.

### Step 3: 3-Stage Escalation in TrackingTabsViewModel (`REQ-UI-421.5`, `TST-UI-384.4`)
* Files: `app/src/main/.../TrackingTabsViewModel.kt`, `app/src/test/.../TrackingTabsViewModelTest.kt`
* Implement 3-stage escalation logic (`0.35f`, `0.65f`, `1.00f`), wire `application` context, and update unit tests.

### Step 4: Tier 1 Targeted Verification Suite (`TST-UI-384.8`)
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute Tier 1 unit and contract tests (~15s) ensuring 100% pass rate.
* **Rollback**: `git checkout -- .` on `feature/ATT-5131` leaves sprint branch completely untouched.
