# Stage 2 Requirement & Test Specification: ATT-1736

**Ticket**: [ATT-1736](https://atrainingtracker.atlassian.net/browse/ATT-1736)  
**Sub-task**: [ATT-1761](https://atrainingtracker.atlassian.net/browse/ATT-1761) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1736`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-197)

### REQ-UI-197: Elevation Profile Contextual Zoom Controls Visibility & Non-Overlapping Scrubber Text Architecture
The system SHALL restrict interactive zoom and pan controls on the elevation profile chart strictly to detailed inspection views, suppress them in compact list item previews, and enforce a non-overlapping vertical layout between the overlay control buttons and the scrubber text label (ATT-1647, ATT-1736):

1. **Contextual Zoom Controls Parameterization (`ElevationProfile.kt`, `MapDetailLayout.kt`, `LIveSegmentSheet.kt`)**:
   - `ElevationProfile` composable overloads SHALL accept `showZoomControls: Boolean = false`.
   - When `showZoomControls == false`:
     - The zoom controls row (`+`, `-`, Pan/Scrub mode toggle, and zoom scale reset badge) and the legend info icon button SHALL NOT be rendered.
     - The `Canvas` gesture handler `Modifier.pointerInput(...)` SHALL NOT be attached, enabling smooth list scrolling and instantaneous parent card click handling (`clickable { onMapClick() }`).
     - Top padding of the chart canvas SHALL be reduced to compact `16.dp` without visual overhead.
   - When `showZoomControls == true`:
     - Full interactive zooming, panning, reset badge, and legend info button are rendered.
   - Detailed inspection layouts (`MapDetailLayout.kt`) SHALL accept `showZoomControls: Boolean = true` defaulting to `true`, forwarding the parameter directly to `ElevationProfile`.
   - Full-screen detail inspection screens ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt), [RouteOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt), [SegmentOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentOnMapScreen.kt), [WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt)) SHALL rely on the default `true`, while ambient/compact bottom sheet popups ([LIveSegmentSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt)) SHALL explicitly pass `showZoomControls = false`, completely suppressing zoom controls and legend button in the LiveSegment popup during tracking cockpit view (ATT-1736).
2. **Non-Overlapping Vertical Layout Architecture**:
   - In detailed inspection views where `showZoomControls == true`:
     - The controls row height SHALL be constrained to `24.dp` at `top = 2.dp` (occupying vertical interval `2.dp .. 26.dp`).
     - The `Canvas` top padding SHALL be set to `44.dp` (with total canvas height = `cachedData.adaptiveHeight + 20.dp` to preserve exact drawable plotting height).
     - The scrubber marker label (`distanceFormatter | altitudeFormatter`) baseline SHALL be anchored at `y = -4.dp.toPx()`, placing the text block in vertical interval `28.dp .. 40.dp`.
     - The elevation curve is plotted from `y = 0` (which begins at `44.dp` in Box space).
     - This guarantees a minimum 2dp clear separation between the bottom edge of the control buttons and the top edge of the scrubber text across all horizontal coordinates, eliminating overlap unconditionally.
3. **Preservation of Core Invariants**:
   - Viewport zooming calculations, centroid scaling, pan clamping, adaptive distance ticks, and unit formatting remain 100% intact per `REQ-UI-192`.
   - Existing call sites in list previews (`WorkoutSummary.kt`, `RouteItem.kt`, `SegmentItem.kt`) and ambient live tracking (`SensorGridScreen.kt`) automatically inherit `showZoomControls = false` cleanly.
   - All full-screen detail views retain complete zoom and pan capabilities via `showZoomControls = true` default.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  - `REQ-UI-192` (*Interactive Elevation Profile Zoom, Pan & Centered Scaling Navigation*), targeting `ElevationProfile.kt` and `MapDetailLayout.kt`.
* **Historical Origin & Commit Trace**:
  - Commit `69286d4e` (Ticket `ATT-527`, Sprint `2026-40.4`) and ticket `ATT-1647` (Sprint `2026-40.5`).
* **Root Reason for Existing Formulation**:
  - In `ATT-527`, zoom controls were added directly to `ElevationProfile` without parameterization or checking if the call site was a list preview card. In `ATT-1647`, `ElevationProfile` was parameterized with `showZoomControls: Boolean = false`, but `MapDetailLayout` unconditionally passed `showZoomControls = true` without accommodating compact popups.
* **Refinement Reason (ATT-1736)**:
  - During on-device review of Sprint `2026-40.5`, the user observed that within the LiveSegment popup during tracking cockpit view, the elevation zoom controls (+, -, pan mode) are still displayed over the elevation profile. Parameterizing `MapDetailLayout` with `showZoomControls: Boolean = true` and setting `showZoomControls = false` in `LIveSegmentSheet.kt` cleanly suppresses controls in the popup while preserving them in all detail inspection screens.
* **Preservation of Core Invariants**:
  - Viewport mathematics (`ElevationProfileZoomMath`), synchronized coordinate scrubbing, adaptive height, unit formatting, and full-screen detail view interactivity are fully preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **AC-1 (List Preview Cards)**:
  - *Given* an athlete viewing workout history, route list, or segment list,
  - *When* inspecting the elevation profile preview,
  - *Then* zero zoom controls (`+`, `-`, Pan mode, reset badge) or legend buttons SHALL be displayed, and tapping the profile opens the detailed map view.
* **AC-2 (Detailed Inspection Views)**:
  - *Given* an athlete viewing a workout, route, or segment detail sheet in `MapDetailLayout`,
  - *When* inspecting the elevation profile,
  - *Then* zoom controls and the legend button SHALL be visible and fully interactive (`showZoomControls == true`).
* **AC-3 (LiveSegment Popup Zoom Controls Suppression)**:
  - *Given* an athlete viewing an active live segment popup during tracking in `SensorGridScreen` (`LIveSegmentSheet.kt`),
  - *When* the popup renders the elevation profile,
  - *Then* zoom controls (`+`, `-`, Pan mode, reset badge) and legend info button SHALL be completely suppressed (`showZoomControls = false`), preserving an uncluttered cockpit view.
* **AC-4 (Scrubber Non-Overlap)**:
  - *Given* an athlete scrubbing on the left edge of the elevation profile in `MapDetailLayout`,
  - *When* the scrubber line is at distance 0 or near the left edge,
  - *Then* the scrubber text label SHALL render completely below the control buttons with clear vertical separation and zero text collision.

---

## 4. Test Case Specification (TST-UI-151)

### TST-UI-151: Elevation Profile Contextual Zoom Controls Visibility and Non-Overlapping Scrubber Layout Verification
- **Target Components**: [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt), [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt), [LIveSegmentSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt)
- **Test Files**: `ElevationProfileLayoutTest.kt`, `LiveSegmentSheetLayoutTest.kt`
- **Scenarios**:
  1. `testElevationProfile_defaults_suppressZoomControls`: Verify `showZoomControls` defaults to `false` in both `ElevationProfile` overloads.
  2. `testMapDetailLayout_parameterDefaults_showZoomControlsTrue`: Verify `MapDetailLayout.kt` defines `showZoomControls: Boolean = true` as default and forwards `showZoomControls = showZoomControls` to `ElevationProfile`.
  3. `testLiveSegmentSheet_suppressesZoomControls`: Verify `LIveSegmentSheet.kt` passes `showZoomControls = false` to `MapDetailLayout`.
  4. `testElevationProfile_sourceCodeInspection_layoutBounds`: Verify `ElevationProfile.kt` source code sets `top = 44.dp` for `showZoomControls == true` and anchors scrubber text below `26.dp`.
  5. `testListPreviewCallers_doNotEnableZoomControls`: Verify preview callers (`WorkoutSummary`, `RouteItem`, `SegmentItem`, `SensorGridScreen`) do not pass `showZoomControls = true`.
  6. Clean-room full regression execution: `./gradlew testDebugUnitTest`.
