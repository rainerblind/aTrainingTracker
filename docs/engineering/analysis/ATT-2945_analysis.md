# Stage 1 Analysis: ATT-2945 - Climb cockpit bottom sheet appears on tracking tabs where live climbs are disabled

**Ticket**: [ATT-2945](https://atrainingtracker.atlassian.net/browse/ATT-2945)  
**Sub-task**: [ATT-3004](https://atrainingtracker.atlassian.net/browse/ATT-3004) (`[Analysis]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2945`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Problem Summary

In the workout tracking cockpit (`TrackingTabsScreen`), athletes can configure multiple custom tracking screens (tabs), each possessing independent display settings such as `showLiveClimbs` (*Live-Anstiege anzeigen*, `REQ-UI-275`).

During on-device desk testing with mock GPS replay along a route with recognized climbs, it was observed that the Live Climb popup / bottom sheet (ClimbPro cockpit, `LiveClimbSheet.kt`) appears, peeks, or casts an elevated shadow even when the athlete is viewing a tracking tab where Live Climbs is explicitly toggled **OFF** in that screen's configuration (`state.showLiveClimbs == false`).

Furthermore, swiping between tabs with mismatched Live Climbs configurations (`showLiveClimbs == true` vs `showLiveClimbs == false`) exhibits state bleeding, where the bottom sheet from adjacent tabs or previously expanded bottom sheet state fails to dismiss cleanly.

---

## 2. Forensic Investigation & Root Cause Analysis

### 2.1 The Horizontal Pager Pre-composition Architecture
In `TrackingTabsScreen.kt:617-624`:
```kotlin
HorizontalPager(
    state = pagerState,
    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    userScrollEnabled = true,
    beyondViewportPageCount = if (screenMode == ScreenMode.TRACKING) trackingViews.size + 1 else trackingViews.size
) { page ->
    ...
    TrackingTabGridContent(viewInfo.tabViewId, screenMode)
}
```
In `ScreenMode.TRACKING`, `beyondViewportPageCount` is configured to `trackingViews.size + 1`. This deliberately pre-composes and retains all tracking tab composables (`SensorGridScreen`) concurrently in the composition tree to guarantee instantaneous, latency-free swiping without recomposition lag or GPS dropped frames.

### 2.2 Global Live Climb Broadcast
`LiveClimbsRepository` is a process-wide singleton (`LiveClimbsRepository.getInstance(context)`). When an active ascent is approached or traversed (`LiveClimbStatus.APPROACHING` or `ON_CLIMB`), `activeLiveClimb` emissions are globally collected by all active `SensorGridScreen` instances across all tabs simultaneously.

### 2.3 The Local Scaffold State Disconnect in `SensorGridScreen.kt`
Inside `SensorGridScreen.kt:226-274`, the bottom sheet layout is governed by:
```kotlin
val scaffoldState = rememberBottomSheetScaffoldState(
    bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = false // Allow it to hide if no segment or climb
    )
)
...
BottomSheetScaffold(
    scaffoldState = scaffoldState,
    sheetShape = BottomSheetDesign.SheetShape,
    sheetContainerColor = MaterialTheme.colorScheme.surface,
    sheetShadowElevation = BottomSheetDesign.SheetShadowElevation,
    sheetTonalElevation = BottomSheetDesign.SheetTonalElevation,
    sheetDragHandle = null,
    sheetPeekHeight = if ((showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING) BottomSheetDesign.PeekHeightLiveSegment + navBarHeight else 0.dp,
    sheetSwipeEnabled = (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING,
    sheetContent = {
        if (screenMode == ScreenMode.TRACKING && showLiveSegments && activeSegment != null) {
            Box(...) { LiveSegmentSheet(...) }
        } else if (screenMode == ScreenMode.TRACKING && showLiveClimbs && activeLiveClimb != null) {
            Box(...) { LiveClimbSheet(...) }
        } else {
            Box(Modifier.fillMaxWidth().height(1.dp)) // Empty placeholder
        }
    }
)
```

Forensic investigation reveals five compounding defects in this structure:

1. **Hardcoded Initial PartiallyExpanded State**:
   `rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = false)` unconditionally initializes the bottom sheet state to `SheetValue.PartiallyExpanded`, even when `showLiveClimbs == false` and `showLiveSegments == false`.
2. **Missing Reactive State Transition to `Hidden`**:
   While `sheetPeekHeight` is set to `0.dp` and `sheetSwipeEnabled` to `false` when `!(showLiveSegments || showLiveClimbs)`, **nothing in the codebase ever calls `scaffoldState.bottomSheetState.hide()`**!
   Consequently, `scaffoldState.bottomSheetState` is physically unable to ever enter `SheetValue.Hidden`. It remains in `PartiallyExpanded` or `Expanded` indefinitely.
3. **Elevated 1.dp Surface Shadow ("Peek Banner")**:
   When `!(showLiveSegments || showLiveClimbs)`, `sheetContent` renders a 1.dp placeholder box:
   `Box(Modifier.fillMaxWidth().height(1.dp))`
   Because `BottomSheetScaffold` wraps `sheetContent` in `Surface(shape = BottomSheetDesign.SheetShape, shadowElevation = 8.dp, tonalElevation = 8.dp, color = surface)`, a 1.dp rounded surface with an 8.dp drop shadow is anchored at the bottom edge of the viewport. This produces a visible elevated line and shadow artifact ("peek banner") across the bottom navigation insets.
4. **Expanded State Bleed**:
   If an athlete expands the ClimbPro sheet on Tab 1 (or previously on any tab), `bottomSheetState.currentValue` becomes `SheetValue.Expanded`. In Material 3 Compose, `sheetPeekHeight = 0.dp` only influences the `PartiallyExpanded` anchor; the `Expanded` anchor is determined by sheet content height. Thus, when swiped to or initialized, the sheet remains expanded rather than being forced to hidden.
5. **Initial State Race in `TrackingScreenState`**:
   In `TrackingViewModel.kt:72`:
   `data class TrackingScreenState(..., val showLiveClimbs: Boolean = true, ...)`
   `showLiveClimbs` defaults to `true`. On tabs where Live Climbs is disabled in SQLite, `state.showLiveClimbs` evaluates to `true` on the very first composition pass until the SQLite flow `getTrackingViewInfoFlow(viewId)` emits the tab's persisted `viewInfo`. During an active climb, this causes `showLiveClimbs` to briefly evaluate to `true` during tab initialization, triggering sheet layout before collapsing.

---

## 3. Formal Architectural Trade-Off Analysis (SYS.2 / SYS.3)

In accordance with ASPICE guidelines and the parent ticket's technical directive, two competing architectural strategies were evaluated to resolve the multi-scaffold state bleeding issue:

### 3.1 Option Evaluation Matrix

| Architectural Criterion | Option A: Per-Tab Scaffold Hardening + Page Visibility Guard (`SensorGridScreen.kt`) | Option B: Top-Level Scaffold Elevation (`TrackingTabsScreen.kt`) |
| :--- | :--- | :--- |
| **Scaffold Cardinality** | $N$ instances (1 per tab), isolated within `SensorGridScreen`. | 1 single instance wrapping `HorizontalPager`. |
| **Horizontal Swipe Ergonomics** | Sheet slides horizontally with the tab during page transitions. If Tab 1 has climb enabled and Tab 2 does not, the sheet slides off naturally with Tab 1. | Sheet remains stationary while pager scrolls underneath. Swiping to a tab with climbs disabled triggers a vertical dismiss animation. |
| **Page Visibility Awareness** | Explicitly parameterized by `isTabActive: Boolean` derived from `pagerState.currentPage == pageIndex`. Inactive tabs force `SheetValue.Hidden`. | Naturally governed by `currentViewInfo?.showLiveClimbs` for the active page. |
| **Regression Risk on Existing Contracts** | **Zero**. Preserves `TrackingTabWysiwygContractTest.kt` (`testSensorGridScreen_isolatesBottomSheetScaffoldToTrackingMode`) and `REQ-UI-295`. | **High**. Breaks established contract tests expecting `BottomSheetScaffold` inside `SensorGridScreen.kt`, requiring test specification waivers. |
| **Control Tab (Page 0) Isolation** | Guaranteed by `ControlTrackingScreen` not using `SensorGridScreen`. | Requires conditional suppression inside the global scaffold when `page == 0`. |
| **Visual Peek Elimination** | Achieved by dynamically zeroing `sheetShadowElevation`, setting `sheetContainerColor = Color.Transparent`, and omitting placeholder `Box`. | Achieved identically. |
| **Implementation Complexity** | Low-to-Medium (additive, highly localized to `SensorGridScreen` and `TrackingScreenState`). | High (invasive refactoring across `TrackingTabsScreen`, `SensorGridScreen`, `TrackingTabGridContent`, and test contracts). |

### 3.2 Architectural Decision & Justification
**Decision**: Adopt **Enhanced Option A** (Per-Tab Scaffold Hardening combined with Active-Tab Visibility Gating).

**Justification**:
1. *Living Contract Preservation (`Chesterton's Fence`)*:
   `TrackingTabWysiwygContractTest.kt` explicitly verifies:
   - `sheetPeekHeight` in `SensorGridScreen.kt` is gated to `ScreenMode.TRACKING`
   - `sheetSwipeEnabled` in `SensorGridScreen.kt` is gated to `(showLiveSegments || showLiveClimbs)`
   - `sheetContent` in `SensorGridScreen.kt` gates `LiveSegmentSheet` and `LiveClimbSheet`.
   Elevating the scaffold to `TrackingTabsScreen` violates existing SWE.1 / SWE.2 architectural contracts established in `ATT-2360` (`REQ-UI-275`) and `ATT-2620` (`REQ-UI-295`).
2. *Elimination of Swipe Drag Contention via `isTabActive`*:
   The primary criticism of multi-scaffold architectures is touch event contention across pre-composed pages in `HorizontalPager`. By passing `isTabActive = (pagerState.currentPage == targetPage)` from `TrackingTabsScreen` to `SensorGridScreen`:
   - All off-screen (inactive) tabs have `isTabActive == false` -> `sheetSwipeEnabled = false` and `bottomSheetState = Hidden`.
   - Only the single currently visible tab enables swipe gestures. Touch contention between competing scaffolds is mathematically zero.
3. *Complete Symptom Elimination*:
   Combining `isTabActive`, conditional initial state (`if (shouldShow) PartiallyExpanded else Hidden`), reactive `LaunchedEffect` driving `hide()` / `partialExpand()`, zeroed elevation/transparent container when inactive, and a safe default `showLiveClimbs = false` in `TrackingScreenState` eliminates 100% of peek artifacts, cold-start flashes, and tab state bleeding.

---

## 4. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   Amends `REQ-UI-275` (*Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles*, Clause 6b, ATT-2360) and `REQ-UI-295` (*Unified Scrollable Container Architecture*, Clause 4, ATT-2620).
2. **Historical Origin & Commit Trace**:
   - `ATT-1281` (Sprint 2026-40.14, commit `d5c90d81`): Introduced Live ClimbPro cockpit sheet in `SensorGridScreen.kt` using `BottomSheetScaffold`.
   - `ATT-2360` (Sprint 2026-41.1, commit `1607a7ec`): Added per-tab `showLiveClimbs` toggle column in `TrackingViewsDatabaseManager` and wired `state.showLiveClimbs`.
   - `ATT-2620` (Sprint 2026-41.3, commit `e5033b5c`): Established `sheetPeekHeight = 0.dp` in `ScreenMode.CONFIGURATION`.
3. **Root Reason for Existing Formulation**:
   The author set `skipHiddenState = false` with the intent to allow the sheet to hide ("`// Allow it to hide if no segment or climb`"), and set `sheetPeekHeight = 0.dp` believing that zero peek height would prevent the sheet from appearing. However, in Compose Material 3 `BottomSheetScaffold`, setting `sheetPeekHeight = 0.dp` does not transition the underlying `SheetValue` to `Hidden`; it merely places the `PartiallyExpanded` anchor at `y = height`, leaving the elevated surface and shadow in place.
4. **Preservation of Core Invariants**:
   - Tracking tabs where `showLiveClimbs == true` MUST continue to display the ClimbPro sheet during active climbs (`APPROACHING` / `ON_CLIMB`).
   - Strava Live Segments (`LiveSegmentSheet`) priority and sheet display MUST remain 100% intact.
   - Expand, collapse, and swipe gestures on tabs with Live Climbs/Segments enabled MUST remain fully responsive.
   - 100% clean-room test suite pass rate across all tracking, map, climb, and segment test suites.

---

## 5. Scope Bounding & Out-of-Scope Constraints (`ATT-1250`)

### 5.1 In-Scope Objectives
* **Active-Tab Visibility Parameterization**: Thread `isTabActive: Boolean = true` from `TrackingTabsScreen` via `TrackingTabGridContent` to `SensorGridScreen`.
* **Initial State Gating**: Initialize `bottomSheetState` to `SheetValue.Hidden` whenever `!isTabActive || !(showLiveSegments || showLiveClimbs)` or `screenMode != ScreenMode.TRACKING`.
* **Reactive Lifecycle Synchronization**: Implement a `LaunchedEffect(shouldShowBottomSheet)` that explicitly invokes `scaffoldState.bottomSheetState.hide()` when disabled/suppressed, and `scaffoldState.bottomSheetState.partialExpand()` when active.
* **Surface Artifact Elimination**: Ensure that when `!shouldShowBottomSheet`, `sheetContent` renders no empty box, `sheetContainerColor` resolves to `Color.Transparent`, and elevations resolve to `0.dp`.
* **Safe State Defaults**: Default `showLiveClimbs: Boolean = false` in `TrackingScreenState` so tabs do not flash active climb sheets during cold composition passes before database configuration loading.
* **Contract & Unit Tests**: Create unit/contract tests asserting that on tabs with `showLiveClimbs == false` or `isTabActive == false`, the sheet is forced to `SheetValue.Hidden` and no sheet content is composed.

### 5.2 Out-of-Scope Constraints
* Modifying `LiveClimbsRepository` climb detection algorithms, elevation thresholds, or state transitions (governed by `REQ-MAP-027`).
* Redesigning `LiveClimbSheet` internal visual components or elevation graphs (deferred to `ATT-2947`).

---

## 6. Proposed Solution Architecture

### 6.1 Recomposition & Sheet Governance in `SensorGridScreen.kt`

```kotlin
// Determine whether any bottom sheet content should be displayed on this tab
val shouldShowBottomSheet = isTabActive && (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING

// Initialize state to Hidden if not showing
val initialSheetValue = if (shouldShowBottomSheet) SheetValue.PartiallyExpanded else SheetValue.Hidden
val scaffoldState = rememberBottomSheetScaffoldState(
    bottomSheetState = rememberStandardBottomSheetState(
        initialValue = initialSheetValue,
        skipHiddenState = false
    )
)

// Reactively drive SheetValue transitions
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

### 6.2 Elimination of Elevated Surface & Peek Artifacts
When `!shouldShowBottomSheet`:
* `sheetPeekHeight = 0.dp`
* `sheetSwipeEnabled = false`
* `sheetShadowElevation = if (shouldShowBottomSheet) BottomSheetDesign.SheetShadowElevation else 0.dp`
* `sheetTonalElevation = if (shouldShowBottomSheet) BottomSheetDesign.SheetTonalElevation else 0.dp`
* `sheetContainerColor = if (shouldShowBottomSheet) MaterialTheme.colorScheme.surface else Color.Transparent`
* `sheetContent`: When `!shouldShowBottomSheet`, omit any placeholder `Box`, rendering an empty composable block.

### 6.3 Initial State Hygiene in `TrackingViewModel.kt`
Update `TrackingScreenState`:
```kotlin
data class TrackingScreenState(
    ...
    val showLiveClimbs: Boolean = false, // Safe default; populated immediately from viewInfo
    ...
)
```

---

## 7. Verification Strategy & Test Matrix

1. **Unit & Contract Verification**:
   - Contract test in `SensorGridScreenClimbSuppressionTest.kt`:
     - Verify that when `state.showLiveClimbs == false` or `isTabActive == false`, `scaffoldState.bottomSheetState` initializes or transitions to `SheetValue.Hidden`.
     - Verify that when `state.showLiveClimbs == false`, `LiveClimbSheet` is NOT composed in `sheetContent`.
     - Verify that when `state.showLiveClimbs == true && isTabActive == true` and an active climb is present, `scaffoldState.bottomSheetState` initializes to `PartiallyExpanded` and `LiveClimbSheet` is composed.
2. **Clean-Room Regression Suite**:
   - Run `./gradlew testDebugUnitTest` verifying 100% pass rate across all 2,253+ unit tests.
