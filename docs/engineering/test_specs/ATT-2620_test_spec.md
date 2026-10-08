# Stage 2 Requirement & Test Specification: ATT-2620 - Tracking tab configuration mode lower section not accessible or visible

**Ticket**: [ATT-2620](https://atrainingtracker.atlassian.net/browse/ATT-2620)  
**Sub-task**: [ATT-2716](https://atrainingtracker.atlassian.net/browse/ATT-2716) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-278](https://atrainingtracker.atlassian.net/browse/ATT-278) (*Cockpit & Live Telemetry Modernization*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2620`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (REQ-UI-295)

### 1.1 Requirement Definition
The system SHALL isolate and configure `ScreenMode.CONFIGURATION` in `SensorGridScreen.kt` using a single unified vertically scrollable container that seamlessly slots all spatial configuration controls without viewport clipping or live tracking overlay collision (ATT-2620):

1. **Unified Scrollable Configuration Container**:
   - In `ScreenMode.CONFIGURATION`, the entire configuration body SHALL be enclosed in a single `Column` with `Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()`.
   - The sensor grid `Column` in `CONFIGURATION` mode SHALL NOT apply an inner nested `verticalScroll`, allowing all sensor field tiles, `RowAdder` buttons, and `ColAdder` buttons to layout naturally within the parent scrollable container.

2. **Spatial Toggle Viewport Accessibility**:
   - The Turn-by-Turn Navigation Hints toggle card (`showNavigationHints`) SHALL render at the top of the scrollable container.
   - The Map (`showMap`) and Elevation Profile (`showElevationProfile`) spatial toggle cards SHALL render directly below the sensor grid.
   - The bottom dock cards for Strava Live Segments (`showLiveSegments`), Live Climbs (`showLiveClimbs`), and Lap Button (`showLapButton`) SHALL render below the Map/Elevation toggles.
   - All spatial toggle cards SHALL remain accessible and interactive by scrolling, regardless of the number of sensor rows on the tab.

3. **Exclusion of Live Map and Telemetry Overlays in Configuration Mode**:
   - Interactive `ATrainingTrackerMap` and `ElevationProfile` SHALL ONLY render when `screenMode != ScreenMode.CONFIGURATION`.
   - In `ScreenMode.CONFIGURATION`, embedded Map and Elevation Profile SHALL be represented exclusively by their respective `SpatialCockpitToggleCard`s.

4. **Bottom Sheet Scaffold Isolation**:
   - In `ScreenMode.CONFIGURATION`, `BottomSheetScaffold` SHALL set `sheetPeekHeight = 0.dp`, `sheetSwipeEnabled = false`, and render empty `sheetContent`, preventing `LiveClimbSheet` or `LiveSegmentSheet` from occluding the configuration canvas.

5. **Preserved Invariants**:
   - `ScreenMode.TRACKING` and `ScreenMode.PREVIEW` layouts SHALL remain 100% preserved with an unscrollable outer column, independently scrollable sensor grid, and expanded weighted map (`Modifier.weight(1f)`).

### 1.2 Acceptance Criteria (Given-When-Then)
- **AC-1 (Scrollability with Multiple Sensor Rows)**:
  - *Given* an athlete in `ScreenMode.CONFIGURATION` for a tab with 3 or more rows of sensor fields,
  - *When* the editor renders,
  - *Then* the athlete SHALL be able to scroll vertically across the entire canvas to reveal and interact with the Map toggle, Elevation Profile toggle, and the bottom dock toggles.
- **AC-2 (Live Map & Elevation Profile Exclusion)**:
  - *Given* a tab with `showMap == true` or `showElevationProfile == true`,
  - *When* entering `ScreenMode.CONFIGURATION`,
  - *Then* `ATrainingTrackerMap` and `ElevationProfile` SHALL NOT be instantiated or rendered, and no component SHALL consume `weight(1f)`.
- **AC-3 (Bottom Insets & Navigation Bar Clearance)**:
  - *Given* an athlete scrolled to the very bottom of the configuration canvas,
  - *When* observing the bottom dock cards,
  - *Then* the cards SHALL be completely visible above the system navigation bar without being cut off or occluded.
- **AC-4 (Tracking & Preview Invariant Preservation)**:
  - *Given* an athlete in `ScreenMode.TRACKING` or `ScreenMode.PREVIEW`,
  - *When* tracking or previewing,
  - *Then* the sensor grid SHALL scroll independently, the map SHALL expand with `weight(1f)`, and live segment/climb sheets SHALL function normally with zero regression.

---

## 2. Test Specification (TST-UI-255)

### 2.1 Test Cases & Traceability Matrix

| Test ID | Requirement Clause | Target Class / Composable | Verification Method |
|:---|:---|:---|:---|
| **TST-UI-255.1** | `REQ-UI-295.1` | `SensorGridScreen.kt` | Verify `SensorGridScreen` applies unified `verticalScroll(rememberScrollState())` to the configuration container in `ScreenMode.CONFIGURATION`. |
| **TST-UI-255.2** | `REQ-UI-295.1` | `SensorGridScreen.kt` | Verify the sensor grid `Column` does not declare nested `verticalScroll()` in `CONFIGURATION` mode, eliminating nested scroll traps. |
| **TST-UI-255.3** | `REQ-UI-295.3` | `SensorGridScreen.kt` | Verify `ATrainingTrackerMap` and `ElevationProfile` are conditionally guarded by `screenMode != ScreenMode.CONFIGURATION`. |
| **TST-UI-255.4** | `REQ-UI-295.4` | `SensorGridScreen.kt` | Verify `BottomSheetScaffold` isolates `sheetContent`, `sheetPeekHeight`, and `sheetSwipeEnabled` to `screenMode == ScreenMode.TRACKING`. |
| **TST-UI-255.5** | `REQ-UI-295.2` | `TrackingTabWysiwygContractTest.kt` | Verify all 6 spatial cockpit toggle cards remain declared and bound to their respective actions. |
| **TST-UI-255.6** | System Invariant | `./gradlew testDebugUnitTest` | Execute full clean-room test suite to ensure 0 regressions. |

---

## 3. Localization Parity
`REQ-UI-295` utilizes existing string resources (`config_tracking__show_map`, `config_tracking__showElevationProfile`, `config_tracking__showLiveSegments`, `config_tracking__show_live_climbs`, `config_tracking__showLapButton`, `config_tracking__show_navigation_hints`). All existing strings are already verified across all 9 application locales under `TST-UI-235.4`. Zero new string keys are required.
