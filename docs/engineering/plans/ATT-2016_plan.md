# Stage 3: Implementation Plan - ATT-2016: Persistent Floating Scrubbing Telemetry Badge Pinned Above Scrollable Graphs in MapDetailLayout

**Ticket**: [ATT-2016](https://rainerblind.atlassian.net/browse/ATT-2016)  
**Sub-task**: [ATT-2094](https://rainerblind.atlassian.net/browse/ATT-2094) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.12`  
**Requirement Mapping**: `REQ-UI-241`  
**Test Mapping**: `TST-UI-200`  
**Branch**: `feature/ATT-2016`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

In the workout Aftermath detailed inspection screen ([MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)), multiple telemetry charts (Elevation Profile, Speed/Pace, Heart Rate, and Cycling Power) are stacked inside a vertically scrollable container below the map and zoom toolbar.

Currently, the multi-metric telemetry readout card (`ScrubbingTelemetryBadge`) is embedded inside [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt). Because `ElevationProfile` is inside the vertically scrollable container:
* Scrolling down to inspect or scrub the Speed/Pace, Heart Rate, or Power graphs causes `ElevationProfile` to scroll offscreen, hiding `ScrubbingTelemetryBadge`.
* When `showElevationProfile == false` (e.g. disabled via `WorkoutDetailPreferences`), `ElevationProfile` is not rendered at all, meaning the badge is completely absent when scrubbing the remaining telemetry charts.

The objective of ATT-2016 is to hoist `ScrubbingTelemetryBadge` to [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt) as a persistent floating overlay pinned at `Alignment.TopCenter` of the lower graph viewport, immediately below `GlobalTelemetryZoomToolbar` / `SplitPaneDivider`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-241` (*Aftermath/Scrubbing: Persistent Floating Scrubbing Telemetry Badge Pinned Above Scrollable Graphs in MapDetailLayout*)
* **Test Mapping**: `TST-UI-200` (*Persistent Floating Scrubbing Telemetry Badge Pinned Above Scrollable Graphs in MapDetailLayout Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly. In particular, `ElevationProfileLayoutTest.kt` must remain 100% green by preserving `showScrubbingBadge: Boolean = true` as default in `ElevationProfile.kt`.
2. **Directional Gesture Disambiguation (`REQ-UI-226`)**: Single-finger vertical swipes ($|\Delta y| > |\Delta x|$) must pass through unconsumed to `lowerColumn`. The pinned overlay badge must be non-interactive and transparent to horizontal drag and vertical scroll pointer events.
3. **Cross-Domain Coordinate Parity (`REQ-UI-233`, `REQ-UI-235`)**: Synchronized scrubbing across spatial (meters) and trackless temporal (seconds) domains must resolve the active point accurately without coordinate drift.
4. **Subtask Direct Completion**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`. AI agents must never transition parent tickets to `Erledigt`.

---

## 4. Proposed Architectural Changes

### Component 1: `ElevationProfile.kt` (Decoupled Badge Gating)
* Add `showScrubbingBadge: Boolean = true` to both composable overloads of `ElevationProfile`.
* In `ElevationProfileContent`, gate the internal invocation of `ScrubbingTelemetryBadge`:
  ```kotlin
  if (showZoomControls && showScrubbingBadge && currentDistance != null) { ... }
  ```
* Ensure `ScrubbingTelemetryBadge` composable is public so `MapDetailLayout` can consume it directly.

### Component 2: `MapDetailLayout.kt` (Container Viewport Overlay & State Hoisting)
* Pass `showScrubbingBadge = false` when calling `ElevationProfile` within `lowerColumn` to prevent redundant duplicate badges.
* Memoize `hrThresholds` and `powerThresholds` at the top level of `MapDetailLayout`:
  ```kotlin
  val hrThresholds = remember(bSportType, context) { ... }
  val powerThresholds = remember(context) { ... }
  ```
* Hoist resolution of `activeScrubPoint` and interpolated altitude (`activeScrubAltitude`) using `remember(selectedDistance, activeScrubPath, isTrackless)`:
  - If `isTrackless`: lookup nearest point by `abs(it.timeSec - selectedDistance)`.
  - If standard GPS path: lookup point by `distance <= selectedDistance` and interpolate altitude between adjacent samples.
* In both layout branches (`showMap && hasScrollableContent` and `!showMap && hasScrollableContent` / `wrapContentHeight`):
  Wrap `lowerColumn` in a container `Box`:
  ```kotlin
  Box(modifier = Modifier.weight(1f - splitFraction).fillMaxWidth()) {
      lowerColumn(Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
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

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Check
* Verify Gate 3 sign-off via `python3 tools/jira_util.py check-gate ATT-2094`.

### Step 2: Update `ElevationProfile.kt`
* Add parameter `showScrubbingBadge: Boolean = true` to both `ElevationProfile` overloads.
* Gate internal `ScrubbingTelemetryBadge` call with `showScrubbingBadge`.
* Verify `ElevationProfileLayoutTest.kt` passes.

### Step 3: Update `MapDetailLayout.kt`
* Pass `showScrubbingBadge = false` to `ElevationProfile`.
* Hoist `hrThresholds`, `powerThresholds`, `activeScrubPoint`, and `activeScrubAltitude`.
* Wrap the lower scroll section in a viewport `Box` hosting the pinned `ScrubbingTelemetryBadge`.

### Step 4: Write Contract & Regression Unit Tests
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutScrubbingBadgeContractTest.kt`.
* Verify structural invariants and parameter wiring.
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutScrubbingBadgeContractTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileLayoutTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphZoneTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted unit tests during Stage 4 construction, followed by full clean-room test execution (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-2016`) off `sprint/2026-40.12` ensures full revert capability without risk to the sprint baseline.
