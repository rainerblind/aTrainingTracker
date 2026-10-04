# Stage 3: Implementation Plan - ATT-1450: [Feature] Abbiegehinweise während der Streckennavigation (Turn-by-Turn Navigation Cues)

**Ticket**: [ATT-1450](https://rainerblind.atlassian.net/browse/ATT-1450)  
**Sub-task**: [ATT-2299](https://rainerblind.atlassian.net/browse/ATT-2299) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Requirement Mapping**: `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts, Battery Saver Wake-Up & Off-Route Alerts*)  
**Test Mapping**: `TST-MAP-030` (*Visual & Auditory Turn-by-Turn Navigation Prompts, Battery Saver Wake-Up & Off-Route Alerts Verification*)  
**Branch**: `feature/ATT-1450`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Background

Athletes navigating active routes in **aTrainingTracker** need proactive visual countdown prompts and auditory tone alerts when approaching turns or deviating from their route corridor. Furthermore, when AMOLED Battery Saver mode is engaged (`REQ-SET-073` / `ATT-1268`), the display must automatically wake to full brightness upon entering the approach threshold ($\le 150\text{ m}$) or going off-route.

This plan establishes the atomic software construction steps for:
1. Turn cue extraction (from course points/waypoints) and geometric synthesis (for plain GPX paths).
2. The real-time turn navigation state machine (`TurnByTurnNavigationEngine`).
3. Tone-based auditory alerts via `NavigationAudioAlertManager` (`ToneGenerator`).
4. Display wake-up integration with `BatterySaverController.onWakeupEvent()`.
5. Visual HUD prompt banner (`TurnPromptBanner`) in the Cockpit (`SensorGridScreen.kt`).
6. Configurable tuning preferences in `TuningPreferencesDataStore`.
7. 100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts, Battery Saver Wake-Up & Off-Route Alerts*)
* **Test Mapping**: `TST-MAP-030` (*Turn-by-Turn Verification Suite*)
  - `TST-MAP-030.1`: Geometric Turn Detection (`TurnCueDetectorTest.kt`)
  - `TST-MAP-030.2`: Navigation Engine State Lifecycle (`TurnByTurnNavigationEngineTest.kt`)
  - `TST-MAP-030.3`: Battery Saver Wake-Up & Audio Alerts (`TurnAlertTriggersTest.kt`)
  - `TST-MAP-030.4`: 9-Language Localization & Format Audit (`TranslationParityTest.kt`)
  - `TST-MAP-030.5`: Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing route database schema v10 (`RoutesDatabaseManager.kt`), DEM enrichment (`REQ-MAP-025`), climbs tracking (`REQ-MAP-027`), and full unit test suite must continue to pass cleanly.
2. **Offline Calculation Sovereignty**: Zero network requests required for turn detection, countdown, or audio alerts.
3. **Display Safety Floor & Battery Saver Modularity**: `BatterySaverController` contracts and dimming safety floors ($\ge 5\%$) remain strictly intact.
4. **Thread Safety & Immutability**: Navigation state calculations run off the UI thread; UI state is exposed via immutable Kotlin `StateFlow`.
5. **Subtask Governance**: Subtasks must never have a fixVersion set. `V4.9.39` is set exclusively on parent ticket `ATT-1450` during final review.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2 Decomposition)

```
                                  +------------------------------------+
                                  |        GPS Location Stream         |
                                  |     (BANALServiceRepository)       |
                                  +-----------------+------------------+
                                                    |
                                                    v
+--------------------------+          +------------------------------------+
|     RoutesRepository     |--------->|     TurnByTurnNavigationEngine     |
| (activeNavigatedRouteId) |          |  - Cross-Track Error Calculation   |
+--------------------------+          |  - Cue Countdown & State Machine   |
                                      |  - Off-Route Corridor Detection    |
                                      +-----------------+------------------+
                                                        |
                     +----------------------------------+----------------------------------+
                     |                                  |                                  |
                     v                                  v                                  v
       +----------------------------+     +----------------------------+     +----------------------------+
       |      TurnPromptBanner      |     | NavigationAudioAlertManager|     |   BatterySaverController   |
       | (Jetpack Compose Overlay)  |     |  (ToneGenerator Chimes)    |     |     (onWakeupEvent())      |
       +----------------------------+     +----------------------------+     +----------------------------+
```

### Component 1: Domain Models (`TurnCue.kt`)
* Package: `com.atrainingtracker.trainingtracker.routes`
* Entities:
  - `enum class TurnDirection`: `LEFT`, `RIGHT`, `SLIGHT_LEFT`, `SLIGHT_RIGHT`, `SHARP_LEFT`, `SHARP_RIGHT`, `STRAIGHT`, `U_TURN` with vector icon resource and string resource.
  - `data class TurnCue`: Unique ID, LatLng, `distanceFromStart` (meters along route), direction, instruction text, and way name.
  - `data class TurnNavigationState`: Active route ID, upcoming cue, `distanceToNextCueMeters`, `isApproaching` ($\le 150\text{m}$), `isTurnNow` ($\le 25\text{m}$), `isOffRoute` ($> 50\text{m}$ for $\ge 3$ fixes), `crossTrackDistanceMeters`, passed cues count, total cues count.

### Component 2: Geometric & Waypoint Turn Detector (`TurnCueDetector.kt`)
* Package: `com.atrainingtracker.trainingtracker.routes`
* Logic:
  - Extracts stored waypoints matching `WaypointCategory.TURN_CUE` from the route.
  - If no course points exist, evaluates polyline angles using 3-5 point sliding window ($\ge 15\text{m}$ chord).
  - Detects heading deltas $|\Delta \theta| \ge 30^\circ$ and synthesizes `TurnCue` instances with monotonic `distanceFromStart`.

### Component 3: Real-Time Navigation State Machine (`TurnByTurnNavigationEngine.kt`)
* Package: `com.atrainingtracker.trainingtracker.routes`
* Logic:
  - Maintains active route polyline and ordered cues.
  - Given location fix:
    - Calculates orthogonal projection onto polyline: distance along route $D_{\text{current}}$ and cross-track error $D_{\text{cross}}$.
    - Identifies next upcoming cue ($D_{\text{cue}} > D_{\text{current}} - 20\text{m}$).
    - Manages state transitions (`APPROACHING` at $150\text{m}$, `TURN_NOW` at $25\text{m}$, `PASSED` when $D_{\text{current}} > D_{\text{cue}} + 20\text{m}$).
    - Evaluates off-route counter: triggers `OFF_ROUTE` if $D_{\text{cross}} > 50\text{m}$ for 3 fixes; resets to `ON_ROUTE` when $D_{\text{cross}} \le 30\text{m}$.

### Component 4: Auditory Alert Manager (`NavigationAudioAlertManager.kt`)
* Package: `com.atrainingtracker.trainingtracker.routes`
* Logic:
  - Uses Android `ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)` (zero permissions required).
  - Emits:
    - Double short tone on approach.
    - Ascending chime on turn now.
    - Descending warning chime on off-route.
    - Single chime on back-on-route.
  - Safe disposal and release on teardown.

### Component 5: Cockpit Visual HUD Banner (`TurnPromptBanner.kt`)
* Package: `com.atrainingtracker.trainingtracker.ui.routes`
* UI:
  - High-visibility card overlay placed in `SensorGridScreen.kt`.
  - Direction icon, large countdown distance ("150 m", "50 m", "JETZT"), and street name.
  - Warning styling for off-route alerts ("Route verlassen").

### Component 6: Tuning Preferences (`TuningPreferencesDataStore.kt` & `AdvancedTuningDialog.kt`)
* Keys:
  - `turnPromptsEnabled`: Boolean (default `true`)
  - `turnAudioAlertsEnabled`: Boolean (default `true`)
  - `turnCueCountdownDistanceMeters`: Int (default `150`)
  - `offRouteCorridorThresholdMeters`: Int (default `50`)

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Domain Models & Icons
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCue.kt`
  - Vector drawables: `ic_turn_slight_left.xml`, `ic_turn_slight_right.xml`, `ic_turn_sharp_left.xml`, `ic_turn_sharp_right.xml`, `ic_turn_u_turn.xml` (reusing existing `ic_turn_left`, `ic_turn_right`, `ic_turn_straight`).
* **Test**: Unit test model instantiation and properties.

### Step 2: Geometric & Course Point Turn Detector
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetector.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnCueDetectorTest.kt`
* **Test Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.TurnCueDetectorTest"`

### Step 3: Turn-by-Turn Navigation Engine
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/TurnByTurnNavigationEngine.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnByTurnNavigationEngineTest.kt`
* **Test Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.TurnByTurnNavigationEngineTest"`

### Step 4: Auditory Alerts & Battery Saver Wake-Up Integration
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/NavigationAudioAlertManager.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnAlertTriggersTest.kt`
* **Test Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.TurnAlertTriggersTest"`

### Step 5: Visual Cockpit Prompt Banner & SensorGridScreen Integration
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBanner.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`

### Step 6: Tuning Preferences & 9-Language Localization
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/tuning/TuningPreferencesDataStore.kt`
  - `app/src/main/res/values*/strings.xml` (EN, DE, ES, FR, IT, JA, NL, PL, PT)
* **Test Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`

### Step 7: Clean-Room Full Suite Regression Execution
* **Command**: `./gradlew testDebugUnitTest` (100% pass rate requirement across all modules).

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests executed at each atomic step; full clean-room `./gradlew testDebugUnitTest` and `TranslationParityTest` in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-1450` guarantees zero impact on `sprint/2026-40.14` until all verification gates pass and the ticket is reviewed.
