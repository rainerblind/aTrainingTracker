# Stage 2 Test Specification: ATT-7580

**Ticket:** ATT-7580 (`[Bug] Use monotonic elapsedRealtime clock in RadarAlertManager and RadarSnoozeController`)  
**Sub-task:** [ATT-7912](https://rainerblind.atlassian.net/browse/ATT-7912) (`[Req & Test Spec]`)  
**Parent Epic:** ATT-1286 | **Sprint:** 2026-46.6 | **Target Release:** V5.0.0 | **Story Points:** 1 SP  
**Role:** `agent1` (Subagent Flash) / Review: `agent2` (Subagent Pro) | **Status:** In Progress (Stage 2)  

---

## 1. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Target Component | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-063.1` | `TST-TRK-063.1` | Unit | `RadarAlertManager.kt` | Proposed |
| `REQ-TRK-064.1` | `TST-TRK-064.1` | Unit | `RadarSnoozeController.kt` | Proposed |

---

## 2. Requirement Specification

### REQ-TRK-063: Radar Audio Chime and Haptic Alert Engine [Verified]
- **Tests**: `TST-TRK-063`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarAlertManager.kt`, `RadarAudioSynthesizer.kt`, `TrackerService.kt`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Rationale**: Cyclists operating in traffic require eyes-free situational awareness through audible and tactile cues that cut through outdoor wind and road noise. `RadarAlertManager` observes `RadarStateRepository.radarState` during active tracking in `TrackerService` (including foreground and screen-off execution), synthesizing distinct PCM audio chimes via `AudioTrack` (`USAGE_MEDIA`, `MODE_STATIC` warm replay) and triggering `VibrationEffect` patterns on threat level transitions (`APPROACHING`, `FAST_APPROACHING`, `ALL_CLEAR`, `DISCONNECTED_ERROR`). The alert engine must support independent radar alert volume, transient background media audio ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), temporary hardware phone-speaker volume boost with guaranteed auto-restore, and configurable audio routing to connected headphones or forced phone speaker output (`AudioTrack.setPreferredDevice()`) without switching `AudioManager` into telephony communication mode (ATT-5086, Parent Epic ATT-1286, extending `REQ-CON-028` and `REQ-TRK-062`):
- **Refined Clause**:
  1. *Threat Transition Alerts, Target Selection & Monotonic Debouncing (`REQ-TRK-063.1`)*:
     - The system SHALL emit distinct acoustic chimes and vibration patterns on threat transitions: double ascending chime for initial approach (`NONE -> APPROACHING`), urgent triple high-pitch chime for high-speed threat (`-> FAST_APPROACHING`), soft single chime for all-clear (`APPROACHING/FAST -> NONE`), and descending warning chime with long vibration for disconnect (`-> DISCONNECTED_ERROR`). When multiple approaching targets exist during high-speed escalation, the system SHALL select the `FAST_APPROACHING` target with highest approach speed to drive alert pitch and spatial panning.
     - State transitions SHALL be debounced to prevent alert spamming during continuous 4–8 Hz telemetry updates; however, high-speed escalations (`-> FAST_APPROACHING`) SHALL NOT be suppressed by routine debounce cooldown windows, and debounced frames SHALL NOT advance or mutate the tracked threat level unless the transition alert is actually emitted.
     - Alert debounce cooldowns (`DEBOUNCE_COOLDOWN_MS = 800L`), chime playback intervals (`CHIME_DURATION_MS = 350L`), and lane change cooldowns (`3000L`) SHALL be tracked using a strictly monotonic time clock (`SystemClock.elapsedRealtime()`), ensuring wall-clock NTP adjustments or timezone jumps do not freeze or prematurely expire alert timers.

### REQ-TRK-064: 1-Tap Radar Audio Snooze, Peloton-Mode Audio Sync & Smart Urban Alert Suppression [Verified]
- **Tests**: `TST-TRK-064`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarSnoozeController.kt`, `RadarAlertManager.kt`, `RadarEdgeOverlay.kt`, `RadarLightQuickSwitchSheet.kt`, `RadarState.kt`, `RadarStateRepository.kt`, `BTLERadarDevice.java`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Rationale**: Cyclists riding through city traffic, along busy parallel roads, or in group rides experience severe alert fatigue from continuous radar beeping. Navigating deeply buried menus mid-ride is hazardous, and following riders in peloton formations trigger constant spurious alerts. The system must provide 1-tap temporary audio snooze from the Cockpit overlay badge, automatic audio suppression coupled to tail light Peloton mode, and an automated smart urban convoy filter while preserving 100% active visual rail tracking, haptic feedback, and high-speed threat alerts (ATT-5119, ATT-6708, Parent Epic ATT-1286, extending `REQ-TRK-062`, `REQ-TRK-063`, and `REQ-CON-031`):
- **Refined Clause**:
  1. *Lifecycle-Decoupled 1-Tap Cockpit Audio Snooze with Monotonic Expiry (`REQ-TRK-064.1`)*:
     - The system SHALL provide 1-tap quick snooze actions accessible from `RadarEdgeOverlay` and `RadarLightQuickSwitchSheet`: Snooze 15 minutes, Snooze 30 minutes, High-Threat Only, and Mute Rest of Ride.
     - Snooze state, ticker countdown, and expiration SHALL be managed by a lifecycle-safe singleton (`RadarSnoozeController`) in `com.atrainingtracker.banalservice.devices.radar` using a strictly monotonic time clock (`SystemClock.elapsedRealtime()`), ensuring snooze and resume operate consistently across `READY`, `PAUSED`, and `TRACKING` states, survive transient BLE disconnects/reconnects without reset to `INACTIVE`, remain unaffected by wall-clock NTP adjustments or timezone jumps, and reset cleanly upon explicit workout finish or test reset.

---

## 3. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-TRK-063.1` (Radar Alert Debouncing & Timing) in `RadarAlertManager.kt`.
   - `REQ-TRK-064.1` (1-Tap Radar Audio Snooze Lifecycle & Expiry) in `RadarSnoozeController.kt`.
2. **Historical Origin & Commit Trace**:
   - `ATT-5086` (commit `85790a1b`): Initial introduction of `RadarAlertManager` with `timeProvider: () -> Long = { System.currentTimeMillis() }`.
   - `ATT-5119`: Introduction of `RadarSnoozeController` with `private var timeProvider: () -> Long = { System.currentTimeMillis() }`.
3. **Root Reason for Existing Formulation**:
   - `System.currentTimeMillis()` was originally utilized as a convenient time source for calculating elapsed milliseconds (`now - lastChimeTimeMs < DEBOUNCE_COOLDOWN_MS`) and calculating snooze expiration (`snoozeEndTimeMs = now + durationMs`).
   - However, `System.currentTimeMillis()` tracks wall-clock Unix epoch time, which is subject to NTP automatic synchronization, user clock adjustments, daylight saving time shifts, and mobile network timezone updates.
   - If the wall clock jumps backward while an alert cooldown or snooze window is active:
     - In `RadarAlertManager`, `now - lastChimeTimeMs` becomes negative, permanently suppressing subsequent alert chimes for up to the full jump duration (e.g. 1 hour).
     - In `RadarSnoozeController`, `now >= snoozeEndTimeMs` fails to expire, extending a 15-minute snooze by an additional hour or indefinitely.
   - If the wall clock jumps forward:
     - Debounce cooldowns collapse instantly, permitting chime stuttering.
     - Snooze windows expire prematurely.
4. **Preservation of Core Invariants**:
   - 800 ms debounce window for routine threats preserved.
   - Escalation priority (`-> FAST_APPROACHING`) unblocked preserved.
   - 15-minute and 30-minute snooze durations preserved.
   - Testability invariant preserved: both components continue to allow dependency injection of a custom `timeProvider` for unit testing.
   - JVM test execution resilience: Android `SystemClock.elapsedRealtime()` can throw `RuntimeException: Method elapsedRealtime in android.os.SystemClock not mocked` when executed in standard JVM unit tests without Robolectric. Canonical `DEFAULT_TIME_PROVIDER` includes a fallback to `System.nanoTime() / 1_000_000L` to guarantee zero JVM test crashes while providing identical strictly monotonic behavior.
   - Test isolation invariant: calling `RadarSnoozeController.reset()` SHALL restore `timeProvider = DEFAULT_TIME_PROVIDER` to prevent test-injected lambda leaks across test classes.

---

## 4. Acceptance Criteria (Given-When-Then)

### REQ-TRK-063.1 (RadarAlertManager Monotonic Cooldowns)
- **AC-TRK-063.1-1: Backward Wall-Clock Jump Resiliency**
  - *Given* `RadarAlertManager` initialized with the default monotonic time provider,
  - *When* an `APPROACHING` alert triggers at monotonic time $t = 1000\text{ ms}$, and a wall-clock jump occurs (e.g., NTP shifts wall time backwards by 1 hour while monotonic time increments normally to $t = 1850\text{ ms} > 800\text{ ms}$),
  - *Then* `RadarAlertManager` correctly allows the next threat transition alert without sticking in debounced state.
- **AC-TRK-063.1-2: Default Time Provider Fallback Invariant**
  - *Given* `RadarAlertManager` instantiated with no explicit `timeProvider` parameter,
  - *When* `timeProvider()` is queried on Android, it uses `SystemClock.elapsedRealtime()`; when queried in standard JVM unit test harness where `SystemClock` is unmocked, it falls back cleanly to `System.nanoTime() / 1_000_000L` without throwing `RuntimeException`.

### REQ-TRK-064.1 (RadarSnoozeController Monotonic Expiry & Reset)
- **AC-TRK-064.1-1: Backward Wall-Clock Jump Snooze Expiry**
  - *Given* `RadarSnoozeController` with active 15-minute audio snooze initiated at monotonic time $t = 10,000\text{ ms}$ (target expiry $t = 910,000\text{ ms}$),
  - *When* wall-clock time shifts backwards by 3600 seconds while monotonic time advances to $t = 910,001\text{ ms}$,
  - *Then* `checkSnoozeExpiry()` detects expiry and transitions `snoozeMode` to `NONE`.
- **AC-TRK-064.1-2: Reset Restores Default Time Provider**
  - *Given* `RadarSnoozeController` configured with a custom test `timeProvider` via `setTimeProvider { ... }`,
  - *When* `RadarSnoozeController.reset()` is invoked,
  - *Then* `timeProvider` is restored to `DEFAULT_TIME_PROVIDER`, ensuring zero test fixture leakage.

---

## 5. Detailed Test Specification

### TST-TRK-063.1: RadarAlertManager Monotonic Time Resilience
- **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/audio/radar/RadarAlertManagerTest.kt`
- **Test Cases**:
  1. `testDefaultTimeProvider_usesMonotonicClock()`:
     - Instantiate `RadarAlertManager` with default arguments.
     - Verify default `timeProvider()` returns a non-zero strictly positive value without throwing `RuntimeException`.
     - Verify two successive readings satisfy $t_2 \ge t_1$.
  2. `testDebounceCooldown_unaffectedByWallClockTimeShift()`:
     - Simulate an approach alert at time $t_0 = 100_000\text{ ms}$.
     - Advance simulated monotonic clock to $t_0 + 850\text{ ms}$ while simulating a backward wall clock shift (e.g. wall clock decreased by 3,600,000 ms).
     - Emit a subsequent approach alert.
     - Verify the alert chime is triggered and not debounced.

### TST-TRK-064.1: RadarSnoozeController Monotonic Time Resilience
- **File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/radar/RadarSnoozeControllerTest.kt`
- **Test Cases**:
  1. `testDefaultTimeProvider_usesMonotonicClock()`:
     - Call `RadarSnoozeController.reset()`.
     - Verify default time evaluation is monotonic and does not throw on JVM test environment.
  2. `testSnoozeExpiry_unaffectedByBackwardWallClockShift()`:
     - Configure simulated monotonic time at $t = 100_000\text{ ms}$.
     - Snooze 15 minutes (`snooze15Minutes()`), establishing `snoozeEndTimeMs = 100_000 + 900_000 = 1_000_000\text{ ms}`.
     - Advance simulated monotonic clock to $t = 1_000_001\text{ ms}$ while wall clock decreases by 1 hour.
     - Verify `checkSnoozeExpiry()` transitions `currentSnoozeMode` to `NONE`.
  3. `testReset_restoresDefaultTimeProvider()`:
     - Set a custom `timeProvider` using `setTimeProvider { 42L }`.
     - Call `reset()`.
     - Verify `timeProvider` no longer returns `42L` but resumes default monotonic time evaluation.

---

## 6. Living Documentation Parity Checklist
- [x] Living doc `docs/requirements/req_tracking_cockpit.md` updated with `REQ-TRK-063.1` and `REQ-TRK-064.1` monotonic clock specifications.
- [x] Living doc `docs/tests/tst_tracking_cockpit.md` updated with `TST-TRK-063.1` and `TST-TRK-064.1` monotonic test definitions.
- [x] `verify_requirement_governance.py --sync-indexes` passed with 100% parity.
- [x] `verify_requirement_governance.py` passed with 0 errors and valid Chesterton's Fence audit.
