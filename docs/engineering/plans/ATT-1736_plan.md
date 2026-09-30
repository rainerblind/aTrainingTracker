# Stage 3 Implementation Plan: ATT-1736

**Ticket**: [ATT-1736](https://atrainingtracker.atlassian.net/browse/ATT-1736)  
**Sub-task**: [ATT-1762](https://atrainingtracker.atlassian.net/browse/ATT-1762) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1736`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Traceability & Scope Matrix

* **Requirements Traced**: `REQ-UI-197` (*Elevation Profile Contextual Zoom Controls Visibility & Non-Overlapping Scrubber Text Architecture*)
* **Tests Traced**: `TST-UI-151` (*Elevation Profile Contextual Zoom Controls Visibility and Non-Overlapping Scrubber Layout Verification*)
* **Scope Definition**:
  1. Add `showZoomControls: Boolean = true` parameter to `MapDetailLayout` in [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt).
  2. In `MapDetailLayout.kt`, pass `showZoomControls = showZoomControls` when invoking `ElevationProfile`.
  3. In `LiveSegmentSheet` ([LIveSegmentSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt)), pass `showZoomControls = false` to `MapDetailLayout` to suppress the zoom controls row and legend info button in the compact LiveSegment popup during tracking.
  4. Ensure all full-screen inspection screens ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt), [RouteOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt), [SegmentOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentOnMapScreen.kt), [WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt)) continue to display zoom controls without regression via the default parameter (`showZoomControls: Boolean = true`).
  5. Update [ElevationProfileLayoutTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileLayoutTest.kt) and [LiveSegmentSheetLayoutTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheetLayoutTest.kt) to verify parameter contracts and suppression behavior.

---

## 2. Architectural Design & Call-Site Audit (SWE.2)

### 2.1 Component Interaction & Hierarchy
```mermaid
graph TD
    subgraph FullScreenViews["Full-Screen Detail Views"]
        TOMS["TrackOnMapScreen"]
        ROMS["RouteOnMapScreen"]
        SOMS["SegmentOnMapScreen"]
        WCHS["WorkoutClusterHeatmapScreen"]
    end

    subgraph CockpitPopup["Cockpit Ambient Popup"]
        SGS["SensorGridScreen"] --> LSS["LiveSegmentSheet"]
    end

    FullScreenViews -->|showZoomControls = true (default)| MDL["MapDetailLayout"]
    LSS -->|showZoomControls = false| MDL

    MDL -->|forward showZoomControls| EP["ElevationProfile"]
    EP -->|if showZoomControls == true| ZC["Zoom Controls (+, -, Pan, Reset) & Legend"]
    EP -->|if showZoomControls == false| NZ["Clean Profile Canvas (Zero Controls Overlay)"]
```

### 2.2 Call Sites Analysis
1. `MapDetailLayout.kt`:
   - Line 73: Add `showZoomControls: Boolean = true` to parameter list.
   - Line 216: Change hardcoded `showZoomControls = true` to `showZoomControls = showZoomControls`.
2. `LIveSegmentSheet.kt`:
   - Line 44: Add `showZoomControls = false` to `MapDetailLayout` invocation.
3. Existing Callers of `MapDetailLayout`:
   - `TrackOnMapScreen.kt`: Relies on default `showZoomControls = true`.
   - `RouteOnMapScreen.kt`: Relies on default `showZoomControls = true`.
   - `SegmentOnMapScreen.kt`: Relies on default `showZoomControls = true`.
   - `WorkoutClusterHeatmapScreen.kt`: Relies on default `showZoomControls = true`.

---

## 3. Step-by-Step Atomic Implementation Tasks

### Task 1: Update Parameterization in `MapDetailLayout.kt`
- File: [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)
- In the function signature for `MapDetailLayout`, declare:
  ```kotlin
  showZoomControls: Boolean = true,
  ```
- In the `ElevationProfile` call site inside `MapDetailLayout`, pass:
  ```kotlin
  ElevationProfile(
      pathPoints = path,
      currentDistance = selectedDistance,
      minAltitudeOverride = minAltitudeOverride,
      maxAltitudeOverride = maxAltitudeOverride,
      onDistanceSelected = { selectedDistance = it },
      showZoomControls = showZoomControls,
      xAxisDomain = tuningConfig.profileXAxisDomain,
      bSportType = bSportType,
      modifier = Modifier.fillMaxWidth()
  )
  ```

### Task 2: Suppress Zoom Controls in `LIveSegmentSheet.kt`
- File: [LIveSegmentSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt)
- In `LiveSegmentSheet`, pass `showZoomControls = false` to `MapDetailLayout`:
  ```kotlin
  @Composable
  fun LiveSegmentSheet(
      liveSegment: LiveSegment
  ) {
      MapDetailLayout(
          bSportType = liveSegment.staticData.summary.bSportType,
          zoomFocus = MapZoomFocus.FIT_PRIMARY,
          activeScrubPath = liveSegment.staticData.path,
          useStatusBarsPadding = false,
          showMap = false,
          showZoomControls = false,
          header = {
              ...
          }
      )
  }
  ```

### Task 3: Update Unit Tests
- File: [ElevationProfileLayoutTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileLayoutTest.kt)
  - Update `testMapDetailLayout_enablesZoomControls`: verify that `MapDetailLayout` declares `showZoomControls: Boolean = true` as default parameter and passes `showZoomControls = showZoomControls` to `ElevationProfile`.
- File: [LiveSegmentSheetLayoutTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheetLayoutTest.kt)
  - Add test `testLiveSegmentSheet_suppressesZoomControls`: verify that `LIveSegmentSheet.kt` source explicitly passes `showZoomControls = false` to `MapDetailLayout`.

### Task 4: Execute Clean-Room Test Suite
- Run targeted unit tests:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileLayoutTest"`
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.segments.LiveSegmentSheetLayoutTest"`
- Run full regression suite:
  `./gradlew testDebugUnitTest`

---

## 4. System Invariants & Verification Checklist

* [x] **Zero Full-Screen Regression**: `MapDetailLayout` defaults to `showZoomControls = true`, ensuring `TrackOnMapScreen`, `RouteOnMapScreen`, `SegmentOnMapScreen`, and `WorkoutClusterHeatmapScreen` remain 100% interactive.
* [x] **Clean Cockpit Experience**: `LiveSegmentSheet` suppresses zoom buttons and info button, eliminating visual clutter and accidental touch capture during active tracking.
* [x] **Architecture Preservation**: Separation of layout geometry (controls row, canvas top padding, and scrubber text) maintained.
* [x] **Threading & Database Invariants**: Zero impact on database queries, background threads, or state flows.
* [x] **Parent Human Gate Invariance**: AI agents do not transition parent tickets to `Erledigt`.
