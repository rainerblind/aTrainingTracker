# Stage 5: Walkthrough & Verification - ATT-5131: Bike Bell Speaker Routing, Volume Boost & 3-Stage Escalation

**Ticket**: [[ATT-5131]](https://rainerblind.atlassian.net/browse/ATT-5131)  
**Parent Epic**: Standalone Ticket  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-421` (`REQ-UI-421.4`, `REQ-UI-421.5`)  
**Test Mapping**: `TST-UI-384` (`TST-UI-384.3`, `TST-UI-384.4`, `TST-UI-384.8`, `TST-UI-384.9`)  
**Branch**: `feature/ATT-5131`  
**Author**: AI Agent 1 (flash / Implementer)  
**Date**: 2026-11-03  

---

## 1. Executive Summary & Verification Overview
* **Issues Addressed**:
  1. Default audio routing played bell chimes into connected headphones rather than the external speaker.
  2. Synthesizer volume was attenuated when phone system volume was low.
  3. Binary volume toggle (`0.70f` -> `1.00f`) was only 3.1 dB, barely noticeable in outdoor wind noise.
* **Delivered Fix**:
  1. `AndroidManifest.xml`: Declared `android.permission.MODIFY_AUDIO_SETTINGS` (`REQ-UI-421.4c`).
  2. `BikeBellAudioSynthesizer.kt`: Configured `USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`; forced routing to `AudioDeviceInfo.TYPE_BUILTIN_SPEAKER` via `setPreferredDevice(speaker)` on creation and before playback; boosted hardware `STREAM_ALARM` to 100% (`flags = 0`) with delayed auto-restore (+650 ms for ring, +150 ms after stopRoll) and immediate restore on `close()` (`REQ-UI-421.4a/b`).
  3. `TrackingTabsViewModel.kt`: Implemented 3-stage logarithmic loudness escalation in `triggerBellRing()` (`0.35f` -> `0.65f` -> `1.00f`, resetting after $> 5\text{ s}$ silence) and passed `application` context (`REQ-UI-421.5`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-421.4a/b` | `[TST-UI-384.3]` | Unit Test (`BikeBellAudioSynthesizerTest`) | **PASSED** | `Verified` |
| `REQ-UI-421.5` | `[TST-UI-384.4]` | ViewModel Unit Test (`TrackingTabsViewModelTest`) | **PASSED** | `Verified` |
| `REQ-UI-421.4c` | `[TST-UI-384.9]` | Contract Test (`ManifestAndBuildConfigContractTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-UI-384.8]` | Tier 1 Targeted Suite (Audio, Tracking Tabs, Build, Theme) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Unit Test Suite Scope (Explicitly Executed)
```text
./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.audio.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.*" \
  --tests "com.atrainingtracker.trainingtracker.build.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"
BUILD SUCCESSFUL in 2s (32 actionable tasks: 1 from cache, 31 up-to-date) - 100% PASS
```

### Requirement Governance Audit (`verify_requirement_governance.py`)
```text
python3 tools/verify_requirement_governance.py
Modified/Altered existing requirement(s) detected: REQ-UI-421.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Invariant & Governance Verification
1. **Audio Isolation**: `STREAM_MUSIC` volume on rider's headphones is strictly untouched.
2. **Loudspeaker Projection**: Audio explicitly routed to `TYPE_BUILTIN_SPEAKER`.
3. **No UI Interruption**: Volume boost passes `flags = 0`, suppressing system volume dialogs over Cockpit.
4. **Living Documentation Synchronized**: `REQ-UI-421` and `TST-UI-384` marked `[Verified]`.
