# Stage 3 Implementation Plan: ATT-2945 - Climb cockpit bottom sheet appears on tracking tabs where live climbs are disabled

**Ticket**: [ATT-2945](https://atrainingtracker.atlassian.net/browse/ATT-2945)  
**Sub-task**: [ATT-3006](https://atrainingtracker.atlassian.net/browse/ATT-3006) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2945`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Architectural Overview (SWE.2)

Ticket `ATT-2945` implements `REQ-UI-327` to deterministically suppress the live climb and segment bottom sheet (`BottomSheetScaffold`) on any tracking tab where Live Climbs or Live Segments are disabled in SQLite, or when the tab is not the currently visible page in `HorizontalPager`.

```
[TrackingTabsScreen]
   │  HorizontalPager(state = pagerState) { page ->
   │     isTabActive = (pagerState.currentPage == page)
   ▼
[TrackingTabGridContent(tabViewId, screenMode, isTabActive)]
   │
   ▼
[SensorGridScreen(state, screenMode, isTabActive)]
   ├── shouldShowBottomSheet = isTabActive && (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING
   ├── scaffoldState: initialValue = if (shouldShowBottomSheet) PartiallyExpanded else Hidden
   ├── LaunchedEffect(shouldShowBottomSheet): drives hide() / partialExpand()
   └── BottomSheetScaffold:
         ├── sheetPeekHeight = if (shouldShowBottomSheet) PeekHeight + navBarHeight else 0.dp
         ├── sheetSwipeEnabled = shouldShowBottomSheet
         ├── sheetShadowElevation = if (shouldShowBottomSheet) SheetShadowElevation else 0.dp
         ├── sheetTonalElevation = if (shouldShowBottomSheet) SheetTonalElevation else 0.dp
         ├── sheetContainerColor = if (shouldShowBottomSheet) surface else Color.Transparent
         └── sheetContent = { if (shouldShowBottomSheet) LiveSegmentSheet / LiveClimbSheet else {} }
```

---

## 2. UI Consistency & Design Guidelines (§ 5)

* **Reference Screen**: Cockpit tracking tabs in `TrackingTabsScreen.kt` and `SensorGridScreen.kt`.
* **Tokens & Dimensions**:
  - `BottomSheetDesign.PeekHeightLiveSegment` (`64.dp`) + `navBarHeight`.
  - `BottomSheetDesign.SheetShape` (`RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)`).
  - Inactive State: `sheetShadowElevation = 0.dp`, `sheetTonalElevation = 0.dp`, `sheetContainerColor = Color.Transparent`, eliminating all bottom-edge drop shadow lines ("peek banners").
* **Material Theme Color Tokens**:
  - Active: `MaterialTheme.colorScheme.surface`.
  - Inactive: `Color.Transparent`.

---

## 3. Atomic Implementation Steps

### Step 1: State Model Hygiene in `TrackingViewModel.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`
* **Action**:
  - Update `TrackingScreenState`:
    ```kotlin
    val showLiveClimbs: Boolean = false, // Safe default; populated immediately from viewInfo
    ```
  - Prevents transient active climb sheet flashes during initial cold composition prior to SQLite view configuration emission.

### Step 2: Active Tab Gating & Deterministic Lifecycle in `SensorGridScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Action**:
  - Add parameter `isTabActive: Boolean = true` to `SensorGridScreen`.
  - Define:
    ```kotlin
    val shouldShowBottomSheet = isTabActive && (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING
    ```
  - Configure `scaffoldState`:
    ```kotlin
    val initialSheetValue = if (shouldShowBottomSheet) SheetValue.PartiallyExpanded else SheetValue.Hidden
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = initialSheetValue,
            skipHiddenState = false
        )
    )
    ```
  - Implement reactive lifecycle driver:
    ```kotlin
    LaunchedEffect(shouldShowBottomSheet) {
        if (!shouldShowBottomSheet) {
            if (scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
                scaffoldState.bottomSheetState.hide()
            }
        } else {
            if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) {
                scaffoldState.bottomSheetState.partialExpand()
            }
        }
    }
    ```
  - Update `BottomSheetScaffold` attributes:
    ```kotlin
    sheetPeekHeight = if (shouldShowBottomSheet) BottomSheetDesign.PeekHeightLiveSegment + navBarHeight else 0.dp,
    sheetSwipeEnabled = shouldShowBottomSheet,
    sheetShadowElevation = if (shouldShowBottomSheet) BottomSheetDesign.SheetShadowElevation else 0.dp,
    sheetTonalElevation = if (shouldShowBottomSheet) BottomSheetDesign.SheetTonalElevation else 0.dp,
    sheetContainerColor = if (shouldShowBottomSheet) MaterialTheme.colorScheme.surface else Color.Transparent,
    sheetContent = {
        if (shouldShowBottomSheet) {
            if (screenMode == ScreenMode.TRACKING && showLiveSegments && activeSegment != null) {
                Box(...) { LiveSegmentSheet(liveSegment = activeSegment) }
            } else if (screenMode == ScreenMode.TRACKING && showLiveClimbs && activeLiveClimb != null) {
                Box(...) { LiveClimbSheet(liveClimb = activeLiveClimb!!) }
            }
        }
    }
    ```

### Step 3: Parameter Propagation in `TrackingTabGridContent.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingTabGridContent.kt`
* **Action**:
  - Add `isTabActive: Boolean = true` parameter to `TrackingTabGridContent`.
  - Forward `isTabActive = isTabActive` to `SensorGridScreen`.

### Step 4: Active Tab Status Calculation in `TrackingTabsScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* **Action**:
  - Inside `HorizontalPager`:
    ```kotlin
    TrackingTabGridContent(
        tabViewId = viewInfo.tabViewId,
        screenMode = screenMode,
        isTabActive = pagerState.currentPage == page
    )
    ```

### Step 5: Contract & Unit Verification in `SensorGridScreenClimbSuppressionTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenClimbSuppressionTest.kt`
* **Action**:
  - Test `isTabActive` parameter presence and default value.
  - Test `shouldShowBottomSheet` gating expression.
  - Test `scaffoldState` conditional initial value (`SheetValue.Hidden` vs `SheetValue.PartiallyExpanded`).
  - Test `LaunchedEffect` driving `hide()` and `partialExpand()`.
  - Test visual artifact elimination (`0.dp` elevation, `Color.Transparent`, empty content).
  - Verify existing contracts in `TrackingTabWysiwygContractTest.kt` remain 100% passing.

---

## 4. Invariants to Enforce

1. **ClimbPro Functionality**: Live Climb cockpit sheet must display and operate normally on tabs where `showLiveClimbs == true`.
2. **Strava Live Segments**: Live Segments popup must display normally and retain higher priority than live climbs.
3. **Contract Stability**: Existing contract tests in `TrackingTabWysiwygContractTest.kt` must remain fully green.
4. **Full-Suite Green**: 100% pass rate across the full 2,253+ test suite.

---

## 5. Verification Commands

```bash
# Targeted contract tests
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenClimbSuppressionTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingTabWysiwygContractTest"

# Full clean-room regression
./gradlew testDebugUnitTest
```
