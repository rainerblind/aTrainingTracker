# Stage 2 Requirement & Test Specification: ATT-1647

**Ticket**: [ATT-1647](https://atrainingtracker.atlassian.net/browse/ATT-1647)  
**Sub-task**: [ATT-1675](https://atrainingtracker.atlassian.net/browse/ATT-1675) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1647`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Requirement Specification (REQ-UI-197)

### REQ-UI-197: Elevation Profile Contextual Zoom Controls Visibility & Non-Overlapping Scrubber Text Architecture
The system SHALL restrict interactive zoom and pan controls on the elevation profile chart strictly to detailed inspection views, suppress them in compact list item previews, and enforce a non-overlapping vertical layout between the overlay control buttons and the scrubber text label (ATT-1647):

1. **Contextual Zoom Controls Parameterization (`ElevationProfile.kt`)**:
   - `ElevationProfile` composable overloads SHALL accept `showZoomControls: Boolean = false`.
   - When `showZoomControls == false`:
     - The zoom controls row (`+`, `-`, Pan/Scrub mode toggle, and zoom scale reset badge) and the legend info icon button SHALL NOT be rendered.
     - The `Canvas` gesture handler `Modifier.pointerInput(...)` SHALL NOT be attached, enabling smooth list scrolling and instantaneous parent card click handling (`clickable { onMapClick() }`).
     - Top padding of the chart canvas SHALL be reduced to compact `16.dp` without visual overhead.
   - When `showZoomControls == true`:
     - Full interactive zooming, panning, reset badge, and legend info button are rendered.
     - Detailed inspection layouts (`MapDetailLayout.kt`) SHALL explicitly pass `showZoomControls = true`.
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

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

1. **Original Requirement ID & Target**: `REQ-UI-192` (*Interactive Elevation Profile Zoom, Pan & Centered Scaling Navigation*), targeting `ElevationProfile.kt`.
2. **Historical Origin & Commit Trace**: Commit `69286d4e` (Ticket `ATT-527`, Sprint `2026-40.4`).
3. **Root Reason for Existing Formulation**: In `ATT-527`, zoom controls were added directly to `ElevationProfile` without parameterization or checking if the call site was a list preview card. Additionally, both the controls and the scrubber label were placed in the same 24dp top padding zone.
4. **Refinement Reason**: Post-sprint review determined that list cards should remain passive, compact previews, and scrubbing near the left edge of the chart in detail views caused severe collision with the overlay control buttons.
5. **Preservation of Core Invariants**: Viewport mathematics (`ElevationProfileZoomMath`), synchronized coordinate scrubbing, adaptive height, and unit formatting are fully preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **AC-1 (List Preview Cards)**:
  - *Given* an athlete viewing workout history, route list, or segment list,
  - *When* inspecting the elevation profile preview,
  - *Then* zero zoom controls (`+`, `-`, Pan mode, reset badge) or legend buttons SHALL be displayed, and tapping the profile opens the detailed map view.
* **AC-2 (Detailed Inspection Views)**:
  - *Given* an athlete viewing a workout, route, or segment detail sheet in `MapDetailLayout`,
  - *When* inspecting the elevation profile,
  - *Then* zoom controls and the legend button SHALL be visible and fully interactive.
* **AC-3 (Scrubber Non-Overlap)**:
  - *Given* an athlete scrubbing on the left edge of the elevation profile in `MapDetailLayout`,
  - *When* the scrubber line is at distance 0 or near the left edge,
  - *Then* the scrubber text label SHALL render completely below the control buttons with clear vertical separation and zero text collision.

---

## 4. Test Case Specification (TST-UI-151)

### TST-UI-151: Elevation Profile Contextual Zoom Controls Visibility and Non-Overlapping Scrubber Layout Verification
- **Target Components**: [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt), [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)
- **Test File**: `ElevationProfileLayoutTest.kt`
- **Scenarios**:
  1. `testElevationProfile_defaults_suppressZoomControls`: Verify `showZoomControls` defaults to `false` in both overloads.
  2. `testElevationProfile_sourceCodeInspection_layoutBounds`: Verify `ElevationProfile.kt` source code sets `top = 44.dp` for `showZoomControls == true` and anchors scrubber text below `26.dp`.
  3. `testMapDetailLayout_passesShowZoomControlsTrue`: Verify `MapDetailLayout.kt` explicitly sets `showZoomControls = true`.
  4. Full test suite regression execution (`./gradlew testDebugUnitTest`).
