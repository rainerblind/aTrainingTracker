# Stage 1 Analysis: ATT-1450 - [Feature] Abbiegehinweise während der Streckennavigation (Turn-by-Turn Navigation Cues)

**Ticket**: [ATT-1450](https://rainerblind.atlassian.net/browse/ATT-1450)  
**Sub-task**: [ATT-2297](https://rainerblind.atlassian.net/browse/ATT-2297) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1450`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

Athletes using **aTrainingTracker** to navigate loaded GPS routes (from TCX courses, GPX tracks, or Strava sync) currently lack proactive turn-by-turn navigation prompts. While the map visually highlights the active route with high-contrast emerald polylines and directional chevrons (`REQ-MAP-023`), athletes must continuously stare at the screen to anticipate approaching intersections, forks, or trail branchings.

This creates three critical operational limitations:
1. **Safety & Distraction**: Continuous manual map inspection while cycling at high speeds or trail running on technical single-tracks diverts attention from traffic and terrain, increasing crash risk.
2. **Battery Consumption**: With AMOLED Battery Saver mode enabled (`REQ-SET-073` / `ATT-1268`), the screen dims to a low-power baseline. Without proactive wake-up triggers for turns, athletes miss navigational decisions unless they manually touch the screen or trigger sensor changes.
3. **Missed Turns & Off-Route Detours**: Deviating from the planned course often goes unnoticed for hundreds of meters or kilometers until the athlete checks the map, requiring tedious backtracking.

Implementing proactive visual countdown prompts, auditory alert tones, off-route warnings, and seamless AMOLED battery saver wake-up transforms route following into an effortless, battery-efficient, hands-free experience matching dedicated bike computers (Garmin Edge, Wahoo ELEMNT).

---

## 2. Root Cause Analysis & Architectural Gap Analysis

### 2.1 Current Architectural Baseline
The existing routing and tracking subsystems provide strong foundational components:
- **`RoutesRepository`**: Exposes `activeNavigatedRouteId: StateFlow<Long?>` and `allRoutes: StateFlow<List<RouteWithPath>>`.
- **`RouteWaypoint` / `RoutesDatabaseManager` (`REQ-MAP-026` / `ATT-58`)**: Table `route_waypoints` already stores classified waypoints and course points with `distanceFromStart` and semantic types, including `WaypointType.TURN_LEFT`, `TURN_RIGHT`, and `TURN_STRAIGHT`.
- **`TcxCourseParser`**: Parses `<CoursePoint>` elements from Garmin TCX courses with point types `Left`, `Right`, `Straight`.
- **`GpxRouteImporter`**: Extracts waypoints and projects them along route polylines using `WaypointDistanceCalculator`.
- **`BatterySaverController` (`ATT-1268`)**: Implements `onWakeupEvent()`, which immediately restores display brightness to 100% (1.0f) for `tuningConfig.wakeupDurationMs` (default 15s).
- **`SensorGridScreen` / `TrackingTabsScreen`**: Host the cockpit layout, live sensors, map, and bottom sheets (`LiveClimbSheet`, `LiveSegmentSheet`).

### 2.2 Architectural Gaps
1. **Missing Turn Cue Tracking State Machine**:
   - There is no reactive navigation engine that computes the athlete's real-time position along the active route, identifies the next upcoming turn cue, tracks the approaching countdown distance, and signals cue passing.
2. **Geometric Fallback for Plain GPX Routes**:
   - Many GPX files contain raw trackpoints without explicit `<CoursePoint>` or `<wpt>` cues. The system lacks a geometric curvature/angle analysis engine to detect sharp direction changes ($\ge 30^\circ$) and generate synthetic turn cues automatically.
3. **Missing Visual Cockpit Overlay**:
   - No compact, high-contrast banner exists in the Cockpit/Map to present upcoming directional arrows, remaining distance countdown (e.g. "↰ 150 m", "50 m", "JETZT"), and street/trail names.
4. **Missing Auditory Prompt Subsystem**:
   - No audio alert manager emits distinctive audio chimes or tones upon approaching a turn, executing a turn, or straying off-route.
5. **No Integration with Battery Saver Wake-Up**:
   - `BatterySaverController.onWakeupEvent()` is currently triggered by segment state transitions (`LiveSegmentStatus`), tracking mode changes, or user interaction, but NOT by approaching navigational turns or off-route alerts.
6. **No Off-Route Cross-Track Corridor Monitor**:
   - No background evaluator measures cross-track distance against the active route polyline to detect corridor deviation ($D_{\text{cross}} > 50\text{m}$) and alert the athlete.

---

## 3. User Scope Grounding (ATT-1250)

In strict accordance with `ATT-1250`, the scope of `ATT-1450` is rigorously bounded to the turn-by-turn prompt feature during active route navigation:

### In-Scope Goals:
1. **Turn Cue Extraction & Synthesis**:
   - Extract turn cues from persisted route waypoints (`WaypointCategory.TURN_CUE`).
   - For routes lacking embedded turn course points, provide a lightweight geometric turn detector that detects sharp angular direction changes ($\ge 30^\circ$) with smoothing.
2. **Turn-by-Turn Navigation Engine (`TurnByTurnNavigationEngine`)**:
   - Track athlete's live GPS coordinate and bearing against the active route.
   - Maintain navigation state: approaching cue, countdown distance in meters, active turn execution, and passed cue progression.
3. **Visual Cockpit Prompt Banner (`TurnPromptBanner`)**:
   - Render a high-visibility, glanceable HUD banner showing directional arrow icon, countdown distance (e.g., "150 m", "50 m", "NOW"), and street/way name.
   - Visual off-route indicator when straying from the route corridor.
4. **Battery Saver Mode Wake-Up (`ATT-1268`)**:
   - Trigger `BatterySaverController.onWakeupEvent()` when entering the countdown threshold of an upcoming turn ($\approx 150\text{ m}$) and when deviating off-route.
5. **Auditory Alert Manager (`NavigationAudioAlertManager`)**:
   - Emits distinct tone cues for approach, immediate turn, off-route warning, and back-on-route confirmation via Android `ToneGenerator` (zero external dependencies, no permissions required).
6. **Off-Route Detection & Recovery**:
   - Detect cross-track deviation beyond configurable corridor threshold ($50\text{ m}$) for $\ge 3$ consecutive GPS fixes.
   - Alert athlete visually and audibly; detect when rejoining route ($D_{\text{cross}} \le 30\text{m}$).
7. **Configurable Tuning & 9-Language Localization Parity**:
   - Preferences in `TuningPreferencesDataStore`: audio prompt toggle (default true), countdown distance threshold (default 150m), and off-route corridor threshold (default 50m).
   - 100% localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Out-of-Scope Non-Goals (Scope Bounding):
1. **Dynamic Road Network Re-Routing**:
   - On-device street network graph routing (e.g. GraphHopper, OSRM routing tiles) is strictly out of scope. The feature guides athletes along their planned track/course.
2. **Text-to-Speech (TTS) Voice Engine**:
   - Voice synthesis engines require language pack downloads, audio focus interruptions of third-party music players, and high battery overhead. Dedicated sports computer tone chimes provide clear, battery-efficient alerts.
3. **GPX / TCX Export Format Modifications**:
   - Export logic is unchanged; imported waypoints and tracks remain unaltered.
4. **Third-Party Turn-by-Turn API Services**:
   - Zero dependency on external paid turn APIs (e.g., Google Directions API); all cue detection and tracking occurs locally and offline.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement only (**`REQ-MAP-028`**). No existing requirements in `docs/requirements.md` are modified or weakened.
* **Architectural Synergy**:
  - Complements `REQ-MAP-023` (*Prominent High-Contrast Rendering for Actively Navigated Routes*).
  - Complements `REQ-MAP-024` (*Quick Route Selector & Auto Detection*).
  - Leverages `REQ-MAP-026` (*Support Waypoints, POIs and TCX Course Points*).
  - Integrates with `REQ-SET-073` (*AMOLED Battery Saver Wake-Up*).

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Component Architecture
```
                               +----------------------------+
                               |     GPS Location Stream    |
                               | (BANALServiceRepository)   |
                               +--------------+-------------+
                                              |
                                              v
+------------------------+      +-------------------------------+
|    RoutesRepository    |      |  TurnByTurnNavigationEngine   |
| (activeNavigatedRoute, |----->|  - Cross-Track Error Calc     |
|  route_waypoints)      |      |  - Upcoming Cue Countdown     |
+------------------------+      |  - Turn Passing Transition    |
                                |  - Off-Route Corridor Monitor |
                                +---------------+---------------+
                                                |
                 +------------------------------+-------------------------------+
                 |                              |                               |
                 v                              v                               v
    +------------------------+    +---------------------------+   +---------------------------+
    |    TurnPromptBanner    |    |   NavigationAudioAlert    |   |  BatterySaverController   |
    | (Jetpack Compose HUD)  |    |  (ToneGenerator Chimes)   |   |    (onWakeupEvent())      |
    +------------------------+    +---------------------------+   +---------------------------+
```

### 5.2 Key Data Models & State
- **`TurnCue`**:
  - `id: Long`
  - `latLng: LatLng`
  - `distanceFromStart: Double` (meters along route)
  - `turnType: TurnDirection` (`LEFT`, `RIGHT`, `SLIGHT_LEFT`, `SLIGHT_RIGHT`, `SHARP_LEFT`, `SHARP_RIGHT`, `STRAIGHT`, `U_TURN`)
  - `instruction: String` (e.g., "Links abbiegen")
  - `wayName: String` (street or trail name if available)
- **`TurnNavigationStatus`**:
  - `distanceToNextCueMeters: Double`
  - `upcomingCue: TurnCue?`
  - `isOffRoute: Boolean`
  - `crossTrackDistanceMeters: Double`
  - `isApproaching: Boolean` (within countdown threshold $\le 150\text{ m}$)
  - `isTurnNow: Boolean` (within execution threshold $\le 25\text{ m}$)

### 5.3 Geometric Cue Detector (`TurnCueDetector`)
- For routes with no course points, inspect polyline segments.
- Calculate bearing differences $\Delta \theta$ across windowed segments ($15-25$m chord length to filter GPS jitter).
- Where $|\Delta \theta| \ge 30^\circ$, synthesize a `TurnCue`:
  - $30^\circ \le \Delta \theta < 60^\circ$: `SLIGHT_RIGHT` / `SLIGHT_LEFT`
  - $60^\circ \le \Delta \theta < 120^\circ$: `TURN_RIGHT` / `TURN_LEFT`
  - $120^\circ \le \Delta \theta < 150^\circ$: `SHARP_RIGHT` / `SHARP_LEFT`
  - $\Delta \theta \ge 150^\circ$: `U_TURN`

### 5.4 Priority Arbiter in Cockpit
- In `SensorGridScreen.kt`, the HUD priority order:
  1. Strava Live Segments (active effort)
  2. Live Climbs (active ascent)
  3. Turn-by-Turn Prompt Banner: Displays as an unobtrusive top overlay or within the navigation sheet peek, ensuring the athlete never misses a critical turn even when climbing or segment tracking is active.

---

## 6. System Invariants & Risk Assessment

### Core Invariants:
1. **Zero Regression**: All existing unit tests and clean-room full build (`./gradlew testDebugUnitTest`) must pass with 100% success.
2. **Offline Sovereignty**: Zero network requests required for turn detection, countdown, or audio alerts.
3. **Safety & Zero Crash Policy**: Faulty, self-intersecting, or single-point routes must be handled gracefully without IndexOutOfBoundsException or division-by-zero.
4. **Subtask Governance**: Subtasks must never have fixVersions set. Fix Version `V4.9.39` is applied exclusively to parent `ATT-1450` at final gate review.

### Risk Assessment:
* **Risk Rating**: **LOW**
* **Technical Justification**:
  - Reuses established `RouteWaypoint`, `RoutesDatabaseManager`, and `PathPoint` geometry math.
  - Tone alerts require standard Android SDK `ToneGenerator` with no external permissions.
  - Display wake-up directly invokes existing, battle-tested `BatterySaverController.onWakeupEvent()`.
  - Non-invasive UI overlay seamlessly integrates into `SensorGridScreen.kt`.
