# Stage 1 Analysis: ATT-2016 - Persistent Floating Scrubbing Telemetry Badge Pinned Above Scrollable Graphs in MapDetailLayout

**Ticket**: [ATT-2016](https://rainerblind.atlassian.net/browse/ATT-2016)  
**Sub-task**: [ATT-2092](https://rainerblind.atlassian.net/browse/ATT-2092) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.12`  
**Branch**: `feature/ATT-2016`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

In the Aftermath workout inspection screens ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt), [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)), multiple synchronized telemetry graphs are stacked vertically in a scrollable column below the interactive map and sticky zoom toolbar:
1. Elevation Profile (*Höhenprofil*)
2. Speed / Pace Graph (*Geschwindigkeit / Tempo*)
3. Heart Rate Graph (*Herzfrequenz*)
4. Cycling Power Graph (*Leistung*)

When touching or horizontally scrubbing along any of these graphs, the system synchronizes a vertical cursor line and highlights instantaneous sample metrics across all visible charts. A multi-metric floating card (`ScrubbingTelemetryBadge`) displays instantaneous telemetry: Distance, Time, Altitude, Slope, Heart Rate (with zone), Power (with zone), and Pace/Speed.

### Current Defect & Limitation:
Currently, `ScrubbingTelemetryBadge` is embedded directly within [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt) (`Alignment.TopCenter`, lines 722–747). Because `ElevationProfile` is placed inside the vertically scrollable `lowerColumn`:
* When the user scrolls down to inspect or scrub the Speed/Pace, Heart Rate, or Cycling Power graphs, the `ElevationProfile` scrolls upward and moves off the top of the viewport.
* The `ScrubbingTelemetryBadge` scrolls off-screen with it and becomes completely invisible.
* As a result, while an athlete is scrubbing the Heart Rate or Power graph down below, they are unable to see the exact numeric telemetry values, timestamps, and zone labels.
* Furthermore, if an athlete disables the Elevation Profile in Settings (`showElevationProfile = false`, per `REQ-UI-240`), `ElevationProfile` is not rendered at all, meaning `ScrubbingTelemetryBadge` is completely absent when scrubbing the remaining telemetry graphs.

### Expected Behavior:
* `ScrubbingTelemetryBadge` must be lifted to [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt) as a persistent floating overlay pinned at the top of the lower graph viewport (immediately below the sticky `GlobalTelemetryZoomToolbar` / `SplitPaneDivider`).
* Whenever scrubbing is active (`selectedDistance != null`), the badge must remain pinned, elevated, and fully visible at the top of the graph pane, independent of the vertical scroll position.
* Scrubbing on any graph (Elevation, Speed/Pace, Heart Rate, Power) must update the pinned badge in real time with instantaneous values.
* When scrubbing concludes (`selectedDistance == null`), the badge must dismiss smoothly without layout shift.
* Even if `showElevationProfile` is `false`, scrubbing any telemetry graph must display the pinned badge.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Historical Architectural Evolution:
1. **Sprint 2026-40.5 (`ATT-1391` / `REQ-UI-201`)**:
   `ScrubbingTelemetryBadge` was originally implemented as an internal overlay inside `ElevationProfile.kt`. At that time, `ElevationProfile` was the sole graph in Aftermath, so embedding the badge at `Alignment.TopCenter` was clean and local.
2. **Sprint 2026-40.6 (`ATT-1740` / `REQ-UI-206`)**:
   Continuous stacked telemetry curves (Speed/Pace, Heart Rate, Power) were introduced inside a vertically scrollable column (`lowerColumn`) in `MapDetailLayout.kt`. All charts were wired to share `selectedDistance: Double?` for synchronized cursor position. However, the visual readout badge remained embedded inside `ElevationProfile.kt`.
3. **Sprint 2026-40.8 (`ATT-1876` / `REQ-UI-225`)**:
   Horizontal zoom controls were decoupled from `ElevationProfile` into a sticky `GlobalTelemetryZoomToolbar` in `MapDetailLayout.kt`, placed outside `Modifier.verticalScroll(...)`. This established the precedent of persistent controls pinned between the map and scrollable graphs, but `ScrubbingTelemetryBadge` remained trapped inside `ElevationProfile`.
4. **Sprint 2026-40.12 (`ATT-2030` / `REQ-UI-240`)**:
   Aftermath section customization was decoupled between list cards and details view. When users disable the Elevation Profile on details view, the badge is not rendered at all because its host composable is suppressed.

### Gap Analysis:
* `MapDetailLayout.kt` manages all the necessary state:
  - `selectedDistance: Double?`
  - `activeScrubPath: List<PathPoint>?`
  - `bSportType: BSportType`
  - `unit: MyUnits`
  - `tuningConfig.elevationXAxisDomain` / `tuningConfig.telemetryXAxisDomain`
  - `isTrackless: Boolean`
  - Zone thresholds (`hrThresholds`, `powerThresholds`)
* However, the lower viewport in `MapDetailLayout.kt` directly invoked `lowerColumn(...)` with `Modifier.verticalScroll(...)` without an outer viewport `Box` hosting a pinned overlay badge.
* To resolve this, the lower pane must be wrapped in a viewport `Box` where `lowerColumn` handles vertical scrolling, and `ScrubbingTelemetryBadge` is placed as a persistent overlay pinned at `Alignment.TopCenter` with `padding(top = 4.dp)` whenever `selectedDistance != null`.
* To prevent duplicate overlapping badges when `ElevationProfile` is at the top of the scroll container, `ElevationProfile` must accept `showScrubbingBadge: Boolean = true` (default `true` for standalone backward compatibility, but passed `false` by `MapDetailLayout`).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Lift Badge to Container Viewport**: Hoist `ScrubbingTelemetryBadge` to [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt) as a persistent floating overlay pinned at `Alignment.TopCenter` of the lower graph viewport, immediately below `GlobalTelemetryZoomToolbar` / `SplitPaneDivider`.
  2. **Persistent Scrubbing Visibility**: Keep the badge visible whenever scrubbing is active (`selectedDistance != null`), independent of the vertical scroll position in `lowerColumn`.
  3. **Universal Telemetry Scrubbing**: Ensure touching/scrubbing on any graph (Elevation, Speed/Pace, Heart Rate, Power) updates the pinned badge with instantaneous interpolated values (Distance, Time, Altitude, Slope, HR with zone, Power with zone, Pace/Speed).
  4. **Elevation-Independent Operation**: Ensure the badge renders and updates during telemetry graph scrubbing even when `showElevationProfile == false`.
  5. **Trackless Support**: Ensure the badge functions seamlessly for trackless workouts (`distance == 0.0`), displaying elapsed time on the primary axis.
  6. **Non-Occluding Layering & Touch Pass-Through**: Ensure the badge overlay does not intercept or swallow horizontal chart drag gestures or scroll events.
  7. **Backward Compatibility**: Provide `showScrubbingBadge: Boolean = true` in `ElevationProfile.kt` so standalone callers remain fully functional while `MapDetailLayout` suppresses internal rendering.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * **Zone Badge Color Suffixing**: Ticket [ATT-2015](https://rainerblind.atlassian.net/browse/ATT-2015) explicitly addresses rendering the zone badge in active zone color (`• Z1`..`• Z5`). Modifying the inner styling or color mapping of `ScrubbingTelemetryBadge` is strictly deferred to ATT-2015.
  * **Zoom Mathematics Changes**: No modifications to [ElevationProfileZoomMath.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMath.kt).
  * **Database Schema Modifications**: No SQLite schema migrations or DAO alterations.
  * **List Card or Live Tracking Changes**: List card previews ([WorkoutSummary.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt)) and live recording sheets ([SensorGridScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt)) have `showZoomControls = false` and remain completely unaffected.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Existing Requirements Inspected:
* `REQ-UI-201` (*Synchronized Multi-Metric Scrubbing on Elevation Profile with Configurable X-Axis Domain Architecture*):
  - Sprint 2026-40.5 (`ATT-1391`). Created `ScrubbingTelemetryBadge` and embedded it inside `ElevationProfile.kt`.
* `REQ-UI-206` (*Continuous Telemetry Metric Graphs with Section Headings & Synchronized Multi-Chart Scrubbing Architecture*):
  - Sprint 2026-40.6 (`ATT-1740`). Added continuous curves below `ElevationProfile` sharing `selectedDistance`.
* `REQ-UI-225` (*Persistent Sticky Global Zoom Toolbar Between Map and Telemetry Graphs*):
  - Sprint 2026-40.8 (`ATT-1876`). Decoupled horizontal zoom buttons from `ElevationProfile` to `GlobalTelemetryZoomToolbar` in `MapDetailLayout.kt`.
* `REQ-UI-226` (*Directional Gesture Disambiguation and Smooth Vertical Scrolling in MapDetailLayout*):
  - Sprint 2026-40.8 (`ATT-1872`). Established touch slop disambiguation allowing smooth vertical scrolling.

### Chesterton's Fence Evaluation:
1. **Original Requirement ID & Target**:
   Net-new requirement **`REQ-UI-241`**, extending and refining `REQ-UI-201`, `REQ-UI-206`, and `REQ-UI-225` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. **Historical Origin & Commit Trace**:
   Sprint 2026-40.5 (Commit `e7a6a273`, `ATT-1391`), Sprint 2026-40.6 (Commit `061a4b35`, `ATT-1740`), Sprint 2026-40.8 (Commit `d71b4028`, `ATT-1876`).
3. **Root Reason for Existing Formulation**:
   `ScrubbingTelemetryBadge` was originally embedded in `ElevationProfile` because it was the only chart component. Decoupling zoom buttons in `REQ-UI-225` proved the value of hoisting sticky controls to `MapDetailLayout.kt`. Hoisting the scrubbing badge to `MapDetailLayout.kt` is the logical continuation of this decoupled viewport architecture.
4. **Preservation of Core Invariants**:
   - `ElevationProfile` curve drawing geometry and top padding (44.dp / 16.dp) are preserved.
   - `ElevationProfileLayoutTest.kt` passes without breakage by retaining `showScrubbingBadge: Boolean = true` default.
   - Multi-chart lockstep zooming, panning, and synchronized cursor tracking are preserved.
   - Directional vertical scroll disambiguation (`REQ-UI-226`) is preserved.
   - 9-language localization parity is preserved.

---

## 5. Architectural Strategy & High-Level Solution

### Component Changes:

1. **[ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt)**:
   - Add parameter `showScrubbingBadge: Boolean = true` to both `ElevationProfile` composable overloads.
   - Guard internal invocation of `ScrubbingTelemetryBadge` with `if (showZoomControls && showScrubbingBadge && currentDistance != null)`.
   - Ensure `ScrubbingTelemetryBadge` composable is public and accessible to `MapDetailLayout.kt`.

2. **[MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)**:
   - In `lowerColumn`, pass `showScrubbingBadge = false` to `ElevationProfile`.
   - Hoist resolution of `activeScrubPoint` and interpolated altitude (`activeScrubAltitude`) using `remember(selectedDistance, activeScrubPath, isTrackless)`:
     ```kotlin
     val activeScrubPoint = remember(selectedDistance, activeScrubPath, isTrackless) {
         if (selectedDistance != null && !activeScrubPath.isNullOrEmpty()) {
             if (isTrackless) {
                 activeScrubPath.minByOrNull { abs(it.timeSec - selectedDistance!!) }
             } else {
                 val idx = activeScrubPath.indexOfLast { it.distance <= selectedDistance!! }.coerceAtLeast(0)
                 activeScrubPath.getOrNull(idx) ?: activeScrubPath.firstOrNull()
             }
         } else null
     }
     ```
   - In both viewport layout branches (`showMap && hasScrollableContent` and `!showMap && hasScrollableContent`), encapsulate `lowerColumn` within a viewport `Box`:
     ```kotlin
     Box(modifier = Modifier.weight(1f - splitFraction).fillMaxWidth()) {
         lowerColumn(
             Modifier
                 .fillMaxSize()
                 .verticalScroll(rememberScrollState())
         )
         if (showZoomControls && selectedDistance != null && activeScrubPoint != null) {
             ScrubbingTelemetryBadge(
                 point = activeScrubPoint,
                 bSportType = bSportType,
                 altitude = activeScrubAltitude,
                 unit = unit,
                 xAxisDomain = if (isTrackless) ProfileXAxisDomain.TIME 
                               else if (showElevationProfile) tuningConfig.elevationXAxisDomain 
                               else tuningConfig.telemetryXAxisDomain,
                 modifier = Modifier
                     .align(Alignment.TopCenter)
                     .padding(top = 4.dp),
                 hrZoneThresholds = hrThresholds,
                 powerZoneThresholds = powerThresholds
             )
         }
     }
     ```
   - Re-use the memoized `hrThresholds` and `powerThresholds` across graph section titles and `ScrubbingTelemetryBadge`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Test Regressions**: All 1,374+ unit tests, including [ElevationProfileLayoutTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileLayoutTest.kt) and [MapDetailLayoutTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt), must continue to pass cleanly.
  2. **Gesture Freedom**: Single-finger vertical swipes must propagate unhindered to `verticalScroll` per `REQ-UI-226`. The overlay badge must not intercept pointer input or cause scroll lockup.
  3. **Human Gate Governance**: Parent ticket `ATT-2016` terminal transition is strictly `Final Review (Human)`. AI agents must never move parent tickets to `Erledigt`.
* **Risk Rating**: **LOW**.
  - All modifications are pure Jetpack Compose layout containment and parameter hoisting in `MapDetailLayout.kt` and `ElevationProfile.kt`.
  - Zero database schema impact, zero threading/coroutine modifications, zero network impact.
