# Stage 1 Analysis: ATT-2620 - Tracking tab configuration mode lower section not accessible or visible

**Ticket**: [ATT-2620](https://atrainingtracker.atlassian.net/browse/ATT-2620)  
**Sub-task**: [ATT-2715](https://atrainingtracker.atlassian.net/browse/ATT-2715) (`[Analysis]`)  
**Parent Epic**: [ATT-278](https://atrainingtracker.atlassian.net/browse/ATT-278) (*Cockpit & Live Telemetry Modernization*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2620`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-41.1 physical device review (Pixel 10) on ticket `ATT-2360` (attachment `ATT-2360_review_screenshot.png`), the athlete entered tracking tab configuration mode (`ScreenMode.CONFIGURATION`) to customize a tab's telemetry tiles and spatial overlays.
While the upper section of the configuration screen functioned as expected (tab name edit field, tab selector, and the Turn-by-Turn navigation hints toggle card), the **lower section was completely inaccessible or clipped off-screen**:
1. The spatial WYSIWYG toggle cards for Embedded Map (`showMap`) and Elevation Profile (`showElevationProfile`) directly below the sensor grid were pushed entirely below the screen viewport and could not be scrolled into view.
2. The bottom dock overlays (Strava Live Segments `showLiveSegments`, Live Climbs `showLiveClimbs`, and Lap Button `showLapButton`) were cut off or occluded behind the Android system navigation bar.
3. Swiping or dragging on the screen only scrolled the sensor field tiles within their own inner container; dragging failed to scroll the screen down to reveal the lower configuration toggles.

### Objective
Ensure that in `ScreenMode.CONFIGURATION`, all spatial configuration elements (Navigation Hints banner, Sensor Grid with Row/Col adders, Map and Elevation Profile toggles, and Bottom Dock toggles for Segments, Climbs, and Lap button) are laid out within a single unified scrollable container, properly clearing system window insets, without interference from live map renderers or bottom sheet scaffolds.

---

## 2. Forensic Investigation & Root Cause Analysis

### 2.1 Unscrollable Outer Column with Independent Scrollable Sensor Grid
In `SensorGridScreen.kt` (lines 302–403):
```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(top = paddingValues.calculateTopPadding()) // Only pad the top
) {
    // 1. Navigation Hints Toggle Card
    if (screenMode == ScreenMode.CONFIGURATION) { ... }

    // 2. The Sensor Grid (Scrollable)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        ...
    ) {
        // Rows, sensor tiles, and RowAdders / ColAdders
    }

    // 3. Map & Elevation Profile Toggles (Outside the inner scroll!)
    if (screenMode == ScreenMode.CONFIGURATION) {
        Row(...) {
            SpatialCockpitToggleCard(title = R.string.config_tracking__show_map, ...)
            SpatialCockpitToggleCard(title = R.string.config_tracking__showElevationProfile, ...)
        }
    }

    // 4. Map (Expanded)
    if (state.showMap) {
        ATrainingTrackerMap(modifier = Modifier.fillMaxWidth().weight(1f))
    }

    // 5. Bottom Dock (Outside the inner scroll!)
    if (screenMode == ScreenMode.CONFIGURATION) {
        Surface(...) { ... }
    }
}
```

#### The Layout Mechanics Failure
1. **Unscrollable Parent Container**: The root `Column` has `Modifier.fillMaxSize()` and does **NOT** have `verticalScroll()`.
2. **Inner Column Takes Full Available Height**: The sensor grid `Column` has its own `Modifier.verticalScroll(rememberScrollState())`. When placed in an unscrollable Column, it expands to take as much vertical height as its children need, up to the screen height. In edit mode, with 3 rows of sensor fields plus 4 `RowAdder` buttons (each ~32.dp) plus spacing, the sensor grid consumes virtually all remaining vertical height (~550dp) beneath the header (~140dp) and top navigation card (~60dp).
3. **Elements Pushed Beyond Viewport**: Because the outer Column is unscrollable, items placed *after* the sensor grid (the Map & Elevation toggles and the bottom dock) are positioned at vertical coordinates strictly below the physical screen boundary.
4. **Gesture Trapping**: When the user drags vertically on the display, only the inner sensor grid receives the touch gesture. Once the sensor grid reaches its bottom-most tile, dragging further has zero effect on the parent layout. The lower configuration cards remain permanently off-screen and untouchable.

### 2.2 Live Map with `weight(1f)` Rendered in Configuration Mode
In `SensorGridScreen.kt` (lines 517–526):
```kotlin
if (state.showMap) {
    ATrainingTrackerMap(
        zoomFocus = state.zoomFocus,
        ...
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    )
}
```
* Line 517 evaluated `if (state.showMap)` without checking `screenMode != ScreenMode.CONFIGURATION`.
* On any tab where `showMap` was enabled (the default for standard tabs), `ATrainingTrackerMap` was actively instantiated and assigned `weight(1f)`.
* This pushed the bottom dock (`SpatialCockpitToggleCard` for Segments, Climbs, and Lap button) down by the entire height of the map, forcing it off-screen and intercepting touch events.
* In `ScreenMode.CONFIGURATION`, the map is conceptually represented by the `SpatialCockpitToggleCard(R.string.config_tracking__show_map)` toggle; rendering the full interactive map during configuration is both visually conflicting and breaks layout constraints.

### 2.3 Live Sheet Scaffold Bleeding into Configuration Mode
In `SensorGridScreen.kt` (lines 266–292):
* `scaffoldState.bottomSheetState` is initialized to `SheetValue.PartiallyExpanded`.
* Although `sheetPeekHeight` was set to `0.dp` when `screenMode != ScreenMode.TRACKING`, `sheetContent` itself did not check `screenMode == ScreenMode.TRACKING`, causing `LiveClimbSheet` or `LiveSegmentSheet` to render and peak out from the bottom (as seen in `ATT-2360_review_screenshot.png`).
* `sheetSwipeEnabled` was also set to `showLiveSegments || showLiveClimbs` without gating on `screenMode == ScreenMode.TRACKING`.

### 2.4 Missing System Navigation Bar Insets Padding
When scrolling to the bottom of the screen, the bottom dock card was occluded by Android's 3-button or gesture navigation bar because bottom navigation bar padding (`navigationBarsPadding()`) was not applied to the configuration container.

---

## 3. Chesterton's Fence Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Refines and extends `REQ-UI-275` (*Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles*), specifically Clause 5 (*Spatial WYSIWYG Cockpit Editor*).
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.1 commit `1607a7ec` on `feature/ATT-2360` introduced spatial toggle cards directly into `SensorGridScreen.kt`.
3. **Root Reason for Existing Formulation**:
   - In `ScreenMode.TRACKING`, the outer column is intentionally unscrollable so `ATrainingTrackerMap` can fill `weight(1f)` while the sensor grid scrolls independently above it.
   - When spatial toggle cards were introduced for `ScreenMode.CONFIGURATION`, they were placed into the existing `SensorGridScreen.kt` structure without realizing that `CONFIGURATION` mode requires a completely different scrolling container strategy than `TRACKING` mode.
4. **Preservation of Core Invariants**:
   - `ScreenMode.TRACKING`: Outer column remains unscrollable; sensor grid scrolls independently; map expands with `weight(1f)`; elevation profile renders; bottom sheet scaffold handles active segments and climbs.
   - `ScreenMode.PREVIEW`: Full visual fidelity matching tracking mode without edit adders or toggles.
   - `ScreenMode.CONFIGURATION`: Unified, single-scroll container containing all configuration cards and sensor fields, with zero nested scroll conflicts and full accessibility down to the bottom dock.

---

## 4. User Scope Grounding (`ATT-1250`)

### In-Scope
1. **Mode-Specific Container Layout in `SensorGridScreen.kt`**:
   - When `screenMode == ScreenMode.CONFIGURATION`, wrap the entire configuration body in a single `verticalScroll(rememberScrollState())` container.
   - Remove the inner `verticalScroll` from the sensor grid `Column` when in `CONFIGURATION` mode to prevent nested scroll conflicts and allow natural vertical layout.
2. **Live Map & Elevation Profile Gating**:
   - Ensure `ATrainingTrackerMap` and `ElevationProfile` are ONLY rendered when `screenMode != ScreenMode.CONFIGURATION`.
3. **Bottom Sheet Scaffold Isolation**:
   - In `ScreenMode.CONFIGURATION`, ensure `sheetContent` is empty, `sheetPeekHeight` is `0.dp`, and `sheetSwipeEnabled` is `false`.
4. **Navigation Insets Padding**:
   - Apply `navigationBarsPadding()` / bottom padding to the configuration container so dock cards are fully visible above the system navigation bar when scrolled to the bottom.
5. **Architectural & Contract Verification**:
   - Update `TrackingTabWysiwygContractTest.kt` to verify that all spatial toggles remain accessible, live map is excluded from configuration mode, and the configuration container is scrollable.

### Out-of-Scope
- Changing the schema or persistence of `TrackingViewsDatabaseManager` (already verified under `REQ-UI-275`).
- Modifying `TrackingTabConfigHeader.kt` (working as intended).
- Modifying sensor field editing dialogs or drag-and-drop mechanics.

---

## 5. Technical Approach & Remediation

In `SensorGridScreen.kt`:
```kotlin
if (screenMode == ScreenMode.CONFIGURATION) {
    // UNIFIED SCROLLABLE CONFIGURATION CONTAINER
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(top = paddingValues.calculateTopPadding(), bottom = 16.dp)
    ) {
        // 1. Pick & Place Guidance Banner
        // 2. Navigation Prompts Banner Toggle
        // 3. Sensor Grid (Unscrollable Column with RowAdders & ColAdders)
        // 4. Map & Elevation Profile Spatial Toggle Row
        // 5. Live Segments, Live Climbs & Lap Button Spatial Dock Surface
    }
} else {
    // TRACKING & PREVIEW MODES (Preserved 1:1)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = paddingValues.calculateTopPadding())
    ) {
        // 1. HUD Banners (Navigation, Return, Fork, Auto-Route)
        // 2. Sensor Grid (Scrollable Column)
        // 3. Map (weight 1f)
        // 4. Elevation Profile
    }
}
```

This cleanly separates the UI structure:
- **Zero layout regressions** for active tracking and preview modes.
- **Flawless, continuous scrolling** for configuration mode: every single card from the top banner to the bottom dock is visible, scrollable, and interactive.

---

## 6. Verification Strategy

1. **Contract Tests (`TrackingTabWysiwygContractTest.kt`)**:
   - Assert `SensorGridScreen.kt` separates `ScreenMode.CONFIGURATION` into a unified scrollable column.
   - Assert `ATrainingTrackerMap` is excluded from `CONFIGURATION` mode.
   - Assert all 6 spatial toggle cards remain bound and accessible.
2. **Interactive Pixel 10 Verification**:
   - Deploy to device / run Compose rendering tests.
   - Verify smooth scrolling from top navigation banner down to bottom dock toggles.
3. **Clean-Room Full Suite Regression**:
   - Run `./gradlew testDebugUnitTest` ensuring 100% test pass rate with 0 failures.
