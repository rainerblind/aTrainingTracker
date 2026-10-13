# Stage 2: Requirement & Test Specification - ATT-1450: [Feature] Abbiegehinweise während der Streckennavigation (Turn-by-Turn Navigation Cues)

**Ticket**: [ATT-1450](https://rainerblind.atlassian.net/browse/ATT-1450)  
**Sub-task**: [ATT-2298](https://rainerblind.atlassian.net/browse/ATT-2298) (`[Test-Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Requirement Mapping**: `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts, Battery Saver Wake-Up & Off-Route Alerts*)  
**Test Spec ID**: `TST-MAP-030`  
**Branch**: `feature/ATT-1450`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (REQ-MAP-028)

### 1.1 Problem Statement & Rationale
During outdoor cycling and running workouts along loaded GPS routes, athletes must not be required to constantly stare at the map display to anticipate upcoming direction changes. Lack of proactive cues causes missed turns, off-route detours, and dangerous distraction. Furthermore, under AMOLED Battery Saver mode (`REQ-SET-073` / `ATT-1268`), the display is dimmed to preserve battery; without automated wake-up triggers for turns, navigation prompts remain invisible. Proactive visual countdown prompts, auditory alert chimes, automatic display wake-up, and off-route warnings provide safe, effortless, and battery-friendly navigation.

### 1.2 Functional & Architectural Requirements
The system SHALL provide visual countdown prompts, auditory alert chimes, automated AMOLED battery saver display wake-up, and off-route corridor deviation monitoring during active route navigation:

1. **Turn Cue Source & Geometric Synthesis (`TurnCueDetector.kt`)**:
   - *Course Points & Waypoints*: The navigation engine SHALL extract stored course points and waypoints with category `WaypointCategory.TURN_CUE` (`TURN_LEFT`, `TURN_RIGHT`, `TURN_STRAIGHT`) from `RoutesDatabaseManager.getWaypointsForRoute(routeId)` (`REQ-MAP-026`).
   - *Geometric Curvature Synthesis*: For routes lacking embedded turn course points, `TurnCueDetector` SHALL evaluate polyline heading deltas $|\Delta \theta| \ge 30^\circ$ across windowed chord lengths ($15-25$m) to synthesize directional turn cues (`SLIGHT_LEFT`, `LEFT`, `SHARP_LEFT`, `SLIGHT_RIGHT`, `RIGHT`, `SHARP_RIGHT`, `U_TURN`).
2. **Real-Time Navigation Engine (`TurnByTurnNavigationEngine.kt`)**:
   - When `RoutesRepository.activeNavigatedRouteId` is non-null, the engine SHALL project the athlete's current location onto the active route polyline, computing distance along the route ($D_{\text{current}}$) and cross-track orthogonal error ($D_{\text{cross}}$).
   - The engine SHALL identify the next upcoming turn cue ahead ($D_{\text{cue}} > D_{\text{current}}$) and calculate remaining countdown distance $D_{\text{remain}} = D_{\text{cue}} - D_{\text{current}}$.
   - State Lifecycle:
     - `APPROACHING`: Triggered when $D_{\text{remain}} \le \text{countdownThreshold}$ (default $150\text{ m}$).
     - `TURN_NOW`: Triggered when $D_{\text{remain}} \le 25\text{ m}$.
     - `PASSED`: Triggered when $D_{\text{current}} > D_{\text{cue}} + 20\text{ m}$, cleanly advancing to the next cue.
3. **Visual Cockpit HUD Banner (`TurnPromptBanner.kt`)**:
   - Rendered as a prominent, glanceable overlay in `SensorGridScreen.kt` when a turn cue is approaching ($\le 150\text{ m}$) or when the athlete is off-route.
   - Displays turn direction arrow icon, countdown distance in meters (e.g., "150 m", "50 m", or "JETZT" / "NOW" when $\le 25\text{ m}$), and way/street name if available.
   - For off-route state, displays a high-visibility warning banner with "Off Route / Route verlassen" and distance back to the route corridor.
4. **Automated AMOLED Battery Saver Display Wake-Up**:
   - Upon entering the `APPROACHING` threshold ($\le 150\text{m}$), the system SHALL invoke `BatterySaverController.onWakeupEvent()`, restoring 100% display brightness for the configured wake-up duration.
   - Upon entering `OFF_ROUTE`, the system SHALL also invoke `BatterySaverController.onWakeupEvent()`.
5. **Auditory Chime Alerts (`NavigationAudioAlertManager.kt`)**:
   - When audio cues are enabled, the system SHALL emit distinct audio chimes via Android `ToneGenerator` (`AudioManager.STREAM_NOTIFICATION` or `STREAM_MUSIC`):
     - Approach prompt ($\le 150\text{ m}$): Double tone alert.
     - Immediate turn ($\le 25\text{ m}$): Ascending confirmation chime.
     - Off-route alert: Descending warning chime.
     - Rejoining route: Single confirmation tone.
6. **Off-Route Corridor Monitoring & Recovery**:
   - If cross-track distance $D_{\text{cross}} > \text{corridorThreshold}$ (default $50\text{ m}$) for $\ge 3$ consecutive GPS fixes, the system transitions to `OFF_ROUTE`.
   - When returning to $D_{\text{cross}} \le 30\text{ m}$, the system transitions back to `ON_ROUTE`.
7. **Tuning Preferences & 100% 9-Language Localization Parity**:
   - `TuningPreferencesDataStore` provides `turnPromptsEnabled: Boolean` (default true), `turnAudioAlertsEnabled: Boolean` (default true), `turnCueCountdownDistanceMeters: Int` (default 150), and `offRouteCorridorThresholdMeters: Int` (default 50).
   - All user-facing strings SHALL maintain 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Embedded Turn Cue Tracking & Countdown)**:
  * *Given* an active route with a stored `TURN_LEFT` waypoint at kilometer 5.0 (5000m),
  * *When* the athlete's projected position reaches 4850m ($D_{\text{remain}} = 150\text{ m}$),
  * *Then* the navigation engine SHALL transition to `APPROACHING`, display `TurnPromptBanner` with a left arrow and "150 m", trigger an approach tone chime, and call `BatterySaverController.onWakeupEvent()`.
* **AC-2 (Execution "Turn Now" Prompt)**:
  * *Given* an approaching turn cue,
  * *When* the athlete reaches 4980m ($D_{\text{remain}} = 20\text{ m} \le 25\text{ m}$),
  * *Then* the banner SHALL display "JETZT" / "NOW" with the direction icon, and emit the immediate turn chime.
* **AC-3 (Turn Passing & Progression)**:
  * *Given* an active turn cue at 5000m,
  * *When* the athlete passes 5025m ($> 5000\text{m} + 20\text{m}$),
  * *Then* the cue SHALL be marked passed, the banner dismissed, and the engine advanced to the next upcoming cue.
* **AC-4 (Geometric Curvature Synthesis)**:
  * *Given* a GPX route lacking explicit course points but featuring a $90^\circ$ right turn,
  * *When* evaluated by `TurnCueDetector`,
  * *Then* a synthetic `TURN_RIGHT` cue SHALL be generated at the vertex coordinate with accurate `distanceFromStart`.
* **AC-5 (Off-Route Deviation & Rejoining)**:
  * *Given* an active route navigation session,
  * *When* the athlete deviates $\ge 55\text{ m}$ ($> 50\text{ m}$) from the route path for 3 consecutive GPS fixes,
  * *Then* the system SHALL transition to `OFF_ROUTE`, display the warning banner, emit a warning chime, and wake the display.
  * *When* the athlete returns to within 25m ($\le 30\text{ m}$),
  * *Then* the system SHALL emit a recovery chime and resume normal navigation.

### 1.4 System Invariants
1. Zero regression in existing route database schema v10 (`RoutesDatabaseManager.kt`), climbs tracking (`REQ-MAP-027`), and all unit tests.
2. 100% offline sovereignty: Zero network calls required for turn cue detection, countdown math, or audio alerts.
3. Thread safety: Mathematical calculations run off the main UI thread; UI state is exposed via immutable Kotlin `StateFlow`.
4. Subtask governance: Subtasks MUST NOT have a fixVersion assigned.

---

## 2. Test Specification (TST-MAP-030)

### Test Case 1: `TurnCueDetectorTest` (`TST-MAP-030.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Preconditions**: Polyline coordinates with known turns:
  - 90° right turn, 90° left turn, 45° slight right, 135° sharp left, 180° U-turn.
  - Straight trackpoints with minor GPS noise ($\le 10^\circ$).
* **Action**: Invoke `TurnCueDetector.detectTurnCues(pathPoints)`.
* **Expected Result**:
  - Sharp and standard turns are detected with correct `TurnDirection`.
  - Minor noise ($\le 10^\circ$) generates zero false turn cues.
  - `distanceFromStart` is accurately calculated and monotonic.

### Test Case 2: `TurnByTurnNavigationEngineTest` (`TST-MAP-030.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnByTurnNavigationEngineTest.kt`
* **Preconditions**: Active route with 3 turn cues at 500m, 1200m, 3000m.
* **Action**:
  - Simulate athlete progression at 300m, 350m (countdown entry), 480m (turn now), 525m (passed), 1500m (off route at 70m cross-track), 1600m (back on route).
* **Expected Result**:
  - Transition to `APPROACHING` at 350m ($D_{\text{remain}} = 150\text{ m}$).
  - Transition to `TURN_NOW` at 480m ($D_{\text{remain}} = 20\text{ m}$).
  - Transition to next cue (1200m) at 525m.
  - Transition to `OFF_ROUTE` when cross-track $> 50\text{ m}$ for 3 fixes.
  - Transition back to `ON_ROUTE` when cross-track $\le 30\text{ m}$.

### Test Case 3: Battery Saver Wake-Up & Audio Alert Triggers (`TST-MAP-030.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnAlertTriggersTest.kt`
* **Preconditions**: Mocked `BatterySaverController` and `NavigationAudioAlertManager`.
* **Action**: Fire navigation transitions (`APPROACHING`, `OFF_ROUTE`, `TURN_NOW`).
* **Expected Result**:
  - `onWakeupEvent()` is called on `APPROACHING` and `OFF_ROUTE`.
  - Distinct chimes are dispatched for approach, turn now, off-route, and back-on-route.

### Test Case 4: 9-Language Localization & Format Specifier Audit (`TST-MAP-030.4`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Goal**: Verify all turn-by-turn strings (`turn_cue_left`, `turn_cue_right`, `turn_cue_straight`, `turn_cue_slight_left`, `turn_cue_slight_right`, `turn_cue_sharp_left`, `turn_cue_sharp_right`, `turn_cue_u_turn`, `turn_cue_now`, `turn_cue_off_route`, `turn_cue_back_on_route`, tuning preferences) exist across all 9 locales:
  - EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% parity, zero missing keys, zero mismatched format tokens.

### Test Case 5: Clean-Room Regression Suite (`TST-MAP-030.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Test Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-030.1` | Unit | `TurnCueDetectorTest` | `REQ-MAP-028` (Section 1) | Specified |
| `TST-MAP-030.2` | Unit | `TurnByTurnNavigationEngineTest` | `REQ-MAP-028` (Sections 2, 6) | Specified |
| `TST-MAP-030.3` | Unit | `TurnAlertTriggersTest` | `REQ-MAP-028` (Sections 4, 5) | Specified |
| `TST-MAP-030.4` | Localization | `TranslationParityTest` | `REQ-MAP-028` (Section 7), `REQ-UI-106` | Specified |
| `TST-MAP-030.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-PRO-014` | Specified |
