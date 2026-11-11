# Stage 2: Requirement & Test Specification - ATT-6705: Fix RadarAlertManager <800ms FAST_APPROACHING escalation suppression, SYSTEM_DEFAULT speaker boost without headphones, and reactive config caching

**Ticket**: [ATT-6705](https://rainerblind.atlassian.net/browse/ATT-6705)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-063.1`, `REQ-TRK-063.3`, `REQ-TRK-063.6`, `REQ-TRK-063.7`  
**Test Spec Mapping**: `TST-TRK-063.1`, `TST-TRK-063.3`, `TST-TRK-063.6`, `TST-TRK-063.7`, `TST-TRK-063.8`  
**Branch**: `feature/ATT-6705` | **Author**: AI Agent 1 (Flash) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-063 Refinement)

### 1.1 Problem Statement & Rationale
Sprint 2026-46.1 review identified three defects: (1) `RadarAlertManager.onRadarStateUpdated()` debouncer suppresses high-speed threat escalations when transitioning `NONE -> APPROACHING` and escalating to `FAST_APPROACHING` within `< 800 ms`, prematurely overwriting `lastThreatLevel = currentThreat` and permanently silencing fast-threat chimes; (2) `playChime()` checks `route == FORCE_SPEAKER` rather than checking whether audio routes to the built-in speaker (`FORCE_SPEAKER` or `SYSTEM_DEFAULT` without headphones), depriving phone-mounted cyclists of speaker volume boost; (3) `onRadarStateUpdated()` synchronously polls `store.getTuningConfig()` on every 8 Hz frame, `stop()` omits `handler?.removeCallbacks(restoreRunnable)`, and `RadarSettingsSection.kt` allows ugly multi-line text wrapping on German `SegmentedButton`s.

### 1.2 Functional & Architectural Requirements
1. **High-Speed Threat Escalation Priority (`REQ-TRK-063.1`)**: On threat escalation (`currentThreat == FAST_APPROACHING && lastThreatLevel != FAST_APPROACHING`), `RadarAlertManager` SHALL emit the urgent fast-threat alert regardless of the 800 ms debounce cooldown. Routine debouncing SHALL NOT mutate `lastThreatLevel` on suppressed frames without emitting the alert.
2. **Built-in Speaker Volume Boost on SYSTEM_DEFAULT (`REQ-TRK-063.3`)**: When `config.speakerBoost` is enabled, `RadarAlertManager` SHALL activate hardware speaker volume boost whenever playback routes to the built-in speaker (`config.route == FORCE_SPEAKER || (config.route == SYSTEM_DEFAULT && !synthesizer.isHeadphonesOrBluetoothConnected())`).
3. **Reactive Config Caching & Lifecycle Cleanup (`REQ-TRK-063.6`)**: `RadarAlertManager` SHALL collect `actualDataStore?.tuningConfigFlow` into a cached configuration snapshot, eliminating synchronous per-frame DataStore queries during 8 Hz telemetry. `RadarAlertManager.stop()` SHALL invoke `handler?.removeCallbacks(restoreRunnable)` before restoring speaker volume.
4. **Compact Phrasing & Single-Line Enforcement (`REQ-TRK-063.7`)**: Half-width audio route `SegmentedButton` labels in `RadarSettingsSection.kt` SHALL use compact phrasing and enforce `maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis` across all 9 locales (Rule 38).

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-TRK-063` (Radar Audio Chime and Haptic Alert Engine), targeting `RadarAlertManager.kt`, `RadarAudioSynthesizer.kt`, `RadarSettingsSection.kt`, and `strings_radar.xml`.
* `Historical Origin & Commit Trace`: Introduced in `ATT-5086` (commit `85790a1b`) and refined in `ATT-6709` (commit `0d7d1106`); refined in `ATT-6705` for escalation debouncing, `SYSTEM_DEFAULT` speaker boost, reactive config caching, and single-line button rendering.
* `Root Reason for Existing Formulation`: Routine debouncing was introduced in `ATT-5086` to avoid chime flutter during rapid signal oscillations, but prematurely setting `lastThreatLevel = currentThreat` permanently silenced `< 800 ms` high-speed escalations. Speaker boost was guarded strictly by `FORCE_SPEAKER`, missing cyclists on default routing without headphones. Synchronous `getTuningConfig()` on 8 Hz frames was inherited from prototype implementation.
* `Preservation of Core Invariants`: High-speed threat escalation alert priority is guaranteed; volume boost auto-restoration in `finally` and `stop()` is strictly preserved; 8 Hz telemetry processing executes non-blocking without DataStore I/O; 100% 9-language translation parity.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* an initial `APPROACHING` alert triggered at `t = 0 ms`, *When* a target escalates to `FAST_APPROACHING` at `t = 250 ms` (`< 800 ms`), *Then* `RadarAlertManager` immediately emits the urgent Fast-Threat chime and haptic pattern without debounce suppression.
* *Given* route `SYSTEM_DEFAULT`, Speaker Boost enabled, and no external headphones connected, *When* an alert chime triggers, *Then* hardware stream volume boosts to the configured alert volume.
* *Given* a running `RadarAlertManager` with a scheduled volume restore runnable, *When* `stop()` is called, *Then* pending runnables are cancelled and stream volume is immediately restored.

---

## 2. Test Specification (TST-TRK-063 Refinement)

### Test Case 1: Fast Threat Escalation Within Debounce Cooldown (`TST-TRK-063.1a`)
* **Scope**: JVM Unit Test (`RadarAlertManagerTest.kt`)
* **Procedure**: Trigger `NONE -> APPROACHING` at `t = 10_000 ms`, advance time to `t = 10_300 ms` (`< 800 ms`), update state to `FAST_APPROACHING`.
* **Expected Result**: Verifies `mockSynthesizer.playApproach(...)` is called at `t = 10_000 ms` and `mockSynthesizer.playFastThreat(...)` is called at `t = 10_300 ms`.

### Test Case 2: Speaker Volume Boost on SYSTEM_DEFAULT Without Headphones (`TST-TRK-063.3a`)
* **Scope**: JVM Unit Test (`RadarAlertManagerTest.kt`)
* **Procedure**: Set `radarAudioRoute = SYSTEM_DEFAULT`, `radarSpeakerBoost = true`, and mock `isHeadphonesOrBluetoothConnected() = false`.
* **Expected Result**: Verifies `mockAudioManager.setStreamVolume(...)` boosts volume to target level on alert trigger, and scheduled restore runnable restores initial volume.

### Test Case 3: Stop Cleans Up Pending Volume Restoration Runnable (`TST-TRK-063.3b`)
* **Scope**: JVM Unit Test (`RadarAlertManagerTest.kt`)
* **Procedure**: Trigger an alert that schedules `restoreRunnable`, invoke `manager.stop()`.
* **Expected Result**: Verifies `mockHandler.removeCallbacks(any())` is called and volume is restored.

### Test Case 4: Reactive Configuration Caching (`TST-TRK-063.6a`)
* **Scope**: JVM Unit Test (`RadarAlertManagerTest.kt`)
* **Procedure**: Verify `RadarAlertManager` caches configuration from `tuningConfigFlow` and processes telemetry frames without invoking `mockDataStore.getTuningConfig()` repeatedly.

### Test Case 5: Compact Strings & Localization Parity (`TST-TRK-063.7a`)
* **Scope**: Static Resource Audit & Unit Test (`TranslationParityTest.kt`)
* **Expected Result**: Verifies `radar_alert_route_speaker` is compacted across all 9 locales and matches format parity.

### Test Case 6: Tier 1 Targeted Audio Radar Test Suite (`TST-TRK-063.8a`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.radar.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-063.1a` | Unit | `RadarAlertManagerTest.kt` | `REQ-TRK-063.1` | Specified |
| `TST-TRK-063.3a` | Unit | `RadarAlertManagerTest.kt` | `REQ-TRK-063.3` | Specified |
| `TST-TRK-063.3b` | Unit | `RadarAlertManagerTest.kt` | `REQ-TRK-063.3`, `REQ-TRK-063.6` | Specified |
| `TST-TRK-063.6a` | Unit | `RadarAlertManagerTest.kt` | `REQ-TRK-063.6` | Specified |
| `TST-TRK-063.7a` | Resource | `strings_radar.xml` (9 locales) | `REQ-TRK-063.7` | Specified |
| `TST-TRK-063.8a` | Suite | Full Audio Radar Suite | `REQ-TRK-063.1..7` | Specified |
