# Stage 3 Implementation Plan: ATT-2620 - Tracking tab configuration mode lower section not accessible or visible

**Ticket**: [ATT-2620](https://atrainingtracker.atlassian.net/browse/ATT-2620)  
**Sub-task**: [ATT-2717](https://atrainingtracker.atlassian.net/browse/ATT-2717) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-278](https://atrainingtracker.atlassian.net/browse/ATT-278) (*Cockpit & Live Telemetry Modernization*)  
**Requirement Mapping**: `REQ-UI-295` (*Unified Scrollable Container Architecture and Viewport Insets Slotting for Tracking Tab Configuration Mode*)  
**Test Spec Mapping**: `TST-UI-255`  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2620`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. UI Consistency & Reflection (Rule 23)

In accordance with **Rule 23** and **Design Guidelines Section 5**:
1. **Existing Patterns**:
   - `SensorGridScreen.kt` defines `SpatialCockpitToggleCard` using standard Material 3 elevated card semantics (`CardDefaults.elevatedCardElevation`, `RoundedCornerShape(12.dp)`).
   - `SpatialCockpitToggleCard` provides immediate visual state feedback via `FilterChipDefaults` / tonal container styling.
   - Bottom dock cards are grouped in a `Surface` with `RoundedCornerShape(12.dp)` and `tonalElevation = 2.dp`.
2. **Design Harmonization**:
   - We preserve the exact visual styling of `SpatialCockpitToggleCard` and the bottom dock surface.
   - We do not introduce ad-hoc wrappers, custom scroll physics, or hardcoded pixel heights.
   - We apply standard Compose insets handling via `navigationBarsPadding()` to guarantee clearance of Android system navigation bars across gesture and 3-button navigation devices.

---

## 2. Proposed Architecture & Code Changes

### Step 1: Isolate `BottomSheetScaffold` in `SensorGridScreen.kt`
- Gate `sheetPeekHeight`, `sheetSwipeEnabled`, and `sheetContent` to `screenMode == ScreenMode.TRACKING`.
- When in `ScreenMode.CONFIGURATION` or `ScreenMode.PREVIEW`, `sheetPeekHeight` is `0.dp`, `sheetSwipeEnabled` is `false`, and `sheetContent` renders an empty placeholder `Box(Modifier.fillMaxWidth().height(1.dp))`. This completely eliminates bottom sheet overlapping.

### Step 2: Bifurcate Layout Containers for Configuration vs. Tracking/Preview
- When `screenMode == ScreenMode.CONFIGURATION`:
  - Enclose the entire configuration body in a single `Column` with:
    ```kotlin
    Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(top = paddingValues.calculateTopPadding(), bottom = 16.dp)
    ```
  - Slot elements in order:
    1. **Pick & Place Guidance Banner** (if `selectedFieldForMove != null`)
    2. **Turn-by-Turn Navigation Prompts Toggle Card** (`SpatialCockpitToggleCard`)
    3. **Sensor Grid**: Unscrollable `Column` (without `.verticalScroll()`), allowing all rows, `RowAdder` buttons, `ColAdder` buttons, and `SensorFieldView` cards to lay out naturally.
    4. **Map & Elevation Profile Toggle Cards**: Row of 2 `SpatialCockpitToggleCard`s.
    5. **Bottom Dock Cards**: Surface with Live Segments, Live Climbs, and Lap Button `SpatialCockpitToggleCard`s.
  - Omit `ATrainingTrackerMap` and `ElevationProfile` entirely from `CONFIGURATION` mode, eliminating `weight(1f)` consumption and touch event hijacking.

- When `screenMode != ScreenMode.CONFIGURATION` (`TRACKING` and `PREVIEW` modes):
  - Retain the exact existing layout:
    1. Root `Column` without `verticalScroll`.
    2. HUD banners.
    3. Sensor grid `Column` with `.verticalScroll(rememberScrollState())`.
    4. `ATrainingTrackerMap` with `Modifier.weight(1f)` (if `state.showMap`).
    5. `ElevationProfile` (if `state.showElevationProfile && state.pathPoints.isNotEmpty()`).

### Step 3: Contract Tests in `TrackingTabWysiwygContractTest.kt`
- Add tests verifying:
  - Configuration container declares `verticalScroll(rememberScrollState())` and `navigationBarsPadding()`.
  - Sensor grid does not declare nested `verticalScroll()` in configuration mode.
  - `ATrainingTrackerMap` and `ElevationProfile` are conditionally excluded from `ScreenMode.CONFIGURATION`.
  - `BottomSheetScaffold` isolates live sheets to `ScreenMode.TRACKING`.
  - All 6 spatial toggle cards remain bound.

---

## 3. Atomic Implementation Steps

| Step | Action | Target File | Verification Criteria |
|:---|:---|:---|:---|
| **Step 1** | Gate `BottomSheetScaffold` properties to `ScreenMode.TRACKING` | `SensorGridScreen.kt` | Peek height, swipe, and sheet content inactive in configuration mode. |
| **Step 2** | Implement unified scrollable configuration column | `SensorGridScreen.kt` | Single `verticalScroll()` container wrapping all 5 configuration sections; nested scroll removed from grid. |
| **Step 3** | Exclude live map and elevation profile from `CONFIGURATION` mode | `SensorGridScreen.kt` | Zero map rendering or `weight(1f)` in configuration mode. |
| **Step 4** | Update contract unit tests | `TrackingTabWysiwygContractTest.kt` | Targeted unit test suite passes 100%. |
| **Step 5** | Execute clean-room full regression suite | Gradle CLI | `./gradlew testDebugUnitTest` passes 100% (0 failures). |

---

## 4. Invariants & Risk Assessment
- **Zero Regression on Active Tracking**: In `ScreenMode.TRACKING`, the weighted map and independent sensor grid scrolling remain completely untouched.
- **Zero Nested Scroll Traps**: By removing `.verticalScroll()` from the sensor grid in configuration mode, Compose will not encounter illegal infinite height measurement constraints.
- **Insets Safety**: `navigationBarsPadding()` ensures complete visibility on all Android gesture / 3-button navigation heights.
