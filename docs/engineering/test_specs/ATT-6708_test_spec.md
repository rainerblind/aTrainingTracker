# Stage 2: Requirement & Test Specification - ATT-6708: Decouple radar audio snooze from TrackerService lifecycle, preserve haptics during audio mute, and keep vehicle count visible on snoozed badge

**Ticket**: [ATT-6708](https://rainerblind.atlassian.net/browse/ATT-6708)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-064.1`, `REQ-TRK-064.2`, `REQ-TRK-064.3`, `REQ-TRK-064.5`, `REQ-TRK-064.7`  
**Test Spec Mapping**: `TST-TRK-064.1`, `TST-TRK-064.2`, `TST-TRK-064.3`, `TST-TRK-064.5`, `TST-TRK-064.7`  
**Branch**: `feature/ATT-6708` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-064 Refinement)

### 1.1 Problem Statement & Rationale
Sprint `2026-46.1` audit of `ATT-5119` identified three critical defects in cycling rearview radar alert coordination:
1. **Lifecycle Coupling in `RadarAlertManager`**: Snooze controls in `RadarLightQuickSwitchSheet` are inactive in `READY` (pre-ride / simulator preview) and `PAUSED` states because `RadarAlertManager.activeInstance` is bound strictly to `TrackerService` active recording. On workout stop, `activeInstance = null` leaves a frozen countdown badge in `RadarStateRepository`, while transient mid-ride BLE disconnects (`BTLERadarDevice.disconnectFromGatt()`) forcibly reset snooze to `INACTIVE`.
2. **Audio Snooze Suppresses Haptics & Un-Overridable Auto-Mute**: Early returns in `triggerApproachAlert()`, `triggerFastThreatAlert()`, and `triggerAllClearAlert()` execute before `vibrate()`, suppressing critical handlebar vibrations when only acoustic chimes were snoozed. Furthermore, when Peloton light mode or Smart Urban congestion suppresses audio, tapping "Resume Audio" only resets `currentSnoozeMode = NONE` without overriding active Peloton/Urban suppression, leaving the user locked into auto-mute.
3. **Badge Overwrites Approaching Vehicle Count, Lacks Vector Mute Icon & Localization**: When `ThreatLevel.APPROACHING` is active, `RadarStatusBadgePill` replaces the approaching vehicle count with ASCII tokens (`"${mins}m"`, `"P"`, `"U"`, `"M"`), omits a muted-bell vector icon, and uses hardcoded English accessibility descriptions. Additionally, if $\ge 3$ vehicles are present in urban congestion and a single vehicle passes at $\ge 5.0\text{ m/s}$, the 30s congestion accumulator resets to `null`.

### 1.2 Functional & Architectural Requirements
1. **Lifecycle-Decoupled 1-Tap Cockpit Audio Snooze (`REQ-TRK-064.1`)**: Snooze state, ticker countdown, and expiration SHALL be managed by a lifecycle-safe singleton (`RadarSnoozeController`) in `com.atrainingtracker.banalservice.devices.radar`. Snooze and resume SHALL operate consistently across `READY`, `PAUSED`, and `TRACKING` states, survive transient BLE disconnects/reconnects without reset to `INACTIVE`, and reset cleanly upon explicit workout finish (`TrackerService` stop / accumulator reset).
2. **Approaching Vehicle Count Retention, Muted-Bell Vector Icon & Accessibility Localization (`REQ-TRK-064.2`)**: While audio is snoozed, `RadarStatusBadgePill` SHALL render a dedicated muted-bell vector icon and localized countdown indicator alongside (never replacing) the active approaching vehicle count (`targets.size`). All threat badge accessibility descriptions SHALL be localized across all 9 supported locales via `radar_overlay_threats_format` and `radar_overlay_threats_fast_format`.
3. **Acoustic Chime Gating, Haptics Preservation & Manual Resume Override (`REQ-TRK-064.3`)**: Audio snooze, Peloton light mode sync, and Urban congestion mute SHALL gate only acoustic chime playback while strictly preserving haptic vibrations when `config.haptics` is enabled. Tapping "Resume Audio" SHALL cancel active snooze and provide an explicit manual override of active Peloton sync and Urban congestion auto-mute until the next state transition.
4. **Resilient Smart Urban Congestion Suppression (`REQ-TRK-064.5`)**: The 30s urban congestion accumulator SHALL remain intact when $\ge 3$ total vehicles are present and at least 2 are slow, even if a single faster vehicle ($\ge 5.0\text{ m/s}$) passes.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-TRK-064` (Radar Audio Snooze, Peloton Sync & Urban Suppression), targeting `RadarAlertManager.kt`, `RadarEdgeOverlay.kt`, `RadarLightQuickSwitchSheet.kt`, `TrackingTabsScreen.kt`, and `BTLERadarDevice.java`.
* `Historical Origin & Commit Trace`: `ATT-5119` (commit `24a520e5`). Refined under `ATT-6708` to decouple snooze from `TrackerService` lifecycle, decouple audio mute from haptic alerts, preserve approaching vehicle count on the overlay badge, and prevent urban timer resets from passing cars.
* `Root Reason for Existing Formulation`: Snooze logic was originally housed in `RadarAlertManager` for convenience alongside chime playback; gating checks were placed at method entry, inadvertently suppressing haptics; `disconnectFromGatt()` cleared snooze state assuming all state was session-bound; badge text replaced vehicle count because a combined layout had not yet been designed.
* `Preservation of Core Invariants`: 100% visual target rail continuity preserved; `FAST_APPROACHING` high-speed threat safety alerts and haptics always sound; zero native `AudioTrack` leaks; zero SQLite migrations; 100% 9-language translation parity.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* the app is in `READY` or `PAUSED` mode with radar connected, *When* the user selects "Snooze 15 min", *Then* `RadarSnoozeController` activates 15m snooze, the badge displays remaining time and a muted bell icon, and the state persists when tracking starts.
* *Given* active audio snooze or Peloton mode mute with haptics enabled, *When* a vehicle approaches at normal speed, *Then* the audio chime is silenced but haptic vibration triggers normally.
* *Given* active Peloton or Urban auto-mute, *When* the user taps "Resume Audio Alerts", *Then* audio chimes resume immediately and remain active until the next radar state transition.
* *Given* audio snooze is active and a vehicle approaches, *Then* `RadarStatusBadgePill` continues to display the approaching vehicle count alongside the muted-bell indicator.

---

## 2. Test Specification (TST-TRK-064 Refinement)

### Test Case 1: Lifecycle-Decoupled Audio Snooze & Resilient Reconnect (`TST-TRK-064.1a`)
* **Scope**: JVM Unit Test (`RadarSnoozeControllerTest.kt`, `RadarAlertManagerTest.kt`)
* **Assertions**: Verifies 15m/30m/Fast-Only/Rest-of-Ride snooze activations, 1-second countdown ticks, and manual resume across READY, PAUSED, and TRACKING. Verifies snooze state is preserved across `BTLERadarDevice.disconnectFromGatt()` and cleared upon explicit workout finish.

### Test Case 2: Muted-Bell Vector Icon, Approaching Vehicle Count & Localized Descriptions (`TST-TRK-064.2a`)
* **Scope**: JVM Unit Test (`RadarEdgeOverlayTest.kt`)
* **Assertions**: Verifies `RadarStatusBadgePill` renders vehicle count alongside the muted-bell indicator during `APPROACHING` threats when snoozed. Verifies `contentDescription` formatting matches localized resource keys across all threat levels.

### Test Case 3: Haptics Preservation During Audio Snooze & Manual Resume Override (`TST-TRK-064.3a`)
* **Scope**: JVM Unit Test (`RadarAlertManagerTest.kt`)
* **Assertions**: Verifies `vibrate(VibrationPattern.APPROACH)` executes when `config.haptics == true` even while `snoozeState.isSnoozed == true`. Verifies `resumeAudio()` overrides active Peloton and Urban auto-mute until the next state update.

### Test Case 4: Resilient Urban Congestion Accumulator With Mixed-Speed Traffic (`TST-TRK-064.5a`)
* **Scope**: JVM Unit Test (`RadarAlertManagerTest.kt`)
* **Assertions**: Verifies `urbanCongestionStartTimeMs` does not reset when 3 total vehicles are detected where 2 are slow and 1 is fast ($\ge 5.0\text{ m/s}$).

### Test Case 5: 9-Language Localization Parity for New Radar Overlay Strings (`TST-TRK-064.7a`)
* **Scope**: JVM Unit Test (`TranslationParityTest.kt`)
* **Assertions**: Verifies 100% key and format specifier parity for `radar_overlay_threats_format` and `radar_overlay_threats_fast_format` across all 9 supported locales.

### Test Case 6: Tier 1 Targeted Audio & Radar Unit Test Suites (`TST-TRK-064.8a`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.radar.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-064.1a` | Unit | `RadarSnoozeController.kt`, `BTLERadarDevice.java` | `REQ-TRK-064.1` | Specified |
| `TST-TRK-064.2a` | Unit | `RadarEdgeOverlay.kt` | `REQ-TRK-064.2` | Specified |
| `TST-TRK-064.3a` | Unit | `RadarAlertManager.kt` | `REQ-TRK-064.3` | Specified |
| `TST-TRK-064.5a` | Unit | `RadarAlertManager.kt` | `REQ-TRK-064.5` | Specified |
| `TST-TRK-064.7a` | Unit | `strings_radar.xml` (9 locales) | `REQ-TRK-064.7` | Specified |
| `TST-TRK-064.8a` | Suite | Full Radar Audio & Overlay Suites | `REQ-TRK-064.1..7` | Specified |
