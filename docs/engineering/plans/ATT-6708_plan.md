# Stage 3: Implementation Plan - ATT-6708: Decouple radar audio snooze from TrackerService lifecycle, preserve haptics during audio mute, and keep vehicle count visible on snoozed badge

**Ticket**: [ATT-6708](https://rainerblind.atlassian.net/browse/ATT-6708)  
**Sub-task**: [ATT-6944](https://rainerblind.atlassian.net/browse/ATT-6944) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-064.1`, `REQ-TRK-064.2`, `REQ-TRK-064.3`, `REQ-TRK-064.5`, `REQ-TRK-064.7`  
**Test Mapping**: `TST-TRK-064.1a`, `TST-TRK-064.2a`, `TST-TRK-064.3a`, `TST-TRK-064.5a`, `TST-TRK-064.7a`, `TST-TRK-064.8a`  
**Branch**: `feature/ATT-6708`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Radar audio snooze and threat alerting currently suffer from three architectural defects:
1. `RadarAlertManager` snooze actions are tied to `TrackerService` active recording via `activeInstance`, rendering snooze controls inoperative in `READY` and `PAUSED` states, leaving frozen badges on workout stop, and wiping snooze state across transient BLE drops via `BTLERadarDevice.disconnectFromGatt()`.
2. Audio mute conditions return early before triggering haptic vibrations (`vibrate()`), suppressing critical tactical feedback. In addition, tapping "Resume Audio" does not override active Peloton light sync or Smart Urban congestion suppression, trapping the user in auto-mute.
3. `RadarStatusBadgePill` overwrites approaching vehicle count (`targets.size`) with ASCII tokens, omits the muted-bell vector icon, and contains unlocalized threat accessibility strings. In addition, `RadarAlertManager` resets `urbanCongestionStartTimeMs` to null whenever a single vehicle in a queue passes at $\ge 5.0\text{ m/s}$.

---

## 2. Traceability & Requirements Mapping
* **REQ-TRK-064.1 (Lifecycle-Decoupled Audio Snooze)**: Singleton `RadarSnoozeController` manages snooze state, ticker countdown, and expiration across `READY`, `PAUSED`, and `TRACKING` -> `TST-TRK-064.1a`
* **REQ-TRK-064.2 (Vehicle Count Retention & Muted-Bell Vector Icon)**: `RadarStatusBadgePill` retains `targets.size`, displays muted-bell vector icon + countdown, and localizes descriptions -> `TST-TRK-064.2a`
* **REQ-TRK-064.3 (Acoustic Chime Gating & Manual Resume Override)**: Gates only acoustic chimes while preserving haptics; manual resume overrides Peloton/Urban auto-mute until next state transition -> `TST-TRK-064.3a`
* **REQ-TRK-064.5 (Resilient Urban Congestion Accumulator)**: Keeps `urbanCongestionStartTimeMs` intact when $\ge 3$ total vehicles are present even if one passes at $\ge 5.0\text{ m/s}$ -> `TST-TRK-064.5a`
* **REQ-TRK-064.7 (9-Language Parity)**: 100% key and format parity across all 9 locales in `strings_radar.xml` -> `TST-TRK-064.7a`

---

## 3. System Invariants & Preserved Behavior
1. **Visual Continuity**: 100% active, un-dimmed vertical target rail tracking and threat animations strictly preserved.
2. **Safety Alerts**: `FAST_APPROACHING` red threats and haptic vibrations always trigger regardless of snooze mode.
3. **No Schema Changes**: Zero SQLite migrations; uses existing in-memory reactive `RadarStateRepository`.
4. **Thread Safety**: All snooze mutations and countdown ticks synchronized on `RadarSnoozeController` internal lock.
5. **Localization Parity**: Zero hardcoded strings or inline emojis; verified via `TranslationParityTest`.

---

## 4. UI Consistency (Design Guidelines Section 5)
* **Reference Screen**: `RadarEdgeOverlay.kt` status pill and vertical rail.
* **Component Reuse**: Top status badge retains standard dimensions (`size(24.dp)`). During snooze with active approaching vehicles, the pill displays `${radarState.targets.size}` in high-contrast text, while a secondary muted indicator badge (or pill extension) displays the muted-bell vector icon (`ic_radar_bell_muted.xml`) and remaining countdown.
* **Color Tokens**: `TTColor.RadarClearGreen`, `TTColor.RadarThreatAmber`, `TTColor.RadarThreatRed`, `TTColor.RadarDisconnectedAmber`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Lifecycle-Safe Singleton `RadarSnoozeController.kt` (`com.atrainingtracker.banalservice.devices.radar`)
* Create `RadarSnoozeController` singleton object.
* Manage `RadarAudioSnoozeState`, countdown ticker coroutine job, and `isManualResumeOverridingAutoMute` flag.
* Expose `snooze(mode)`, `resumeAudio()`, `toggleSnooze()`, `setPelotonMute(active)`, `setUrbanMute(active)`, `onStateTransition()`, and `reset()`.
* Publish state updates directly to `RadarStateRepository`.

### Step 2: Decoupled Chime & Haptic Alert Engine (`RadarAlertManager.kt`)
* Delegate snooze, resume, and auto-mute state changes to `RadarSnoozeController`.
* Separate acoustic chime playback from haptic alerts: call `if (config.haptics) vibrate(...)` unconditionally before checking audio suppression.
* In `isAudioChimeSuppressed(threatLevel, config)`, check if `RadarSnoozeController` has audio muted (respecting `isManualResumeOverridingAutoMute`).
* In urban congestion filter, keep `urbanCongestionStartTimeMs` intact when `state.targets.size >= 3` and at least 2 targets are slow, even if a single target passes at $\ge 5.0\text{ m/s}$.

### Step 3: Lifecycle Hooks in BLE Peripheral, TrackerService, and TrackingTabsScreen
* In `BTLERadarDevice.java` line 315, preserve `currentState.getAudioSnoozeState()` during `disconnectFromGatt()`.
* In `TrackerService.java`, call `RadarSnoozeController.reset()` on workout finish / service teardown (`onDestroy()`).
* In `TrackingTabsScreen.kt`, route snooze actions directly to `RadarSnoozeController`.

### Step 4: UI Refactoring, Muted-Bell Vector Drawable & 9-Language Localization
* Add vector drawable `app/src/main/res/drawable/ic_radar_bell_muted.xml`.
* Update `RadarEdgeOverlay.kt`:
  - When threats exist (`targets.isNotEmpty()`), always render `${radarState.targets.size}` in `RadarStatusBadgePill`.
  - When snoozed, render dedicated muted-bell vector icon + countdown indicator alongside vehicle count.
  - Localize threat content descriptions using string resources: `radar_overlay_threats_format` and `radar_overlay_threats_fast_format`.
* Add missing strings to `strings_radar.xml` across all 9 locales (en, de, es, fr, it, ja, nl, pl, pt).

### Step 5: Unit & Robolectric Test Suite Expansion
* Author `RadarSnoozeControllerTest.kt` covering all snooze modes, countdown ticks, and auto-mute overrides.
* Update `RadarAlertManagerTest.kt` verifying haptic preservation during audio snooze and urban warmup resilience.
* Update `RadarEdgeOverlayTest.kt` verifying vehicle count visibility and localized content descriptions.
* Execute `TranslationParityTest.kt` asserting 100% translation parity.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted radar and audio suites under `/tmp/att_build_merge.lock`:
  `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.trainingtracker.audio.radar.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Rollback**: `git checkout feature/ATT-6708` to clean state.
