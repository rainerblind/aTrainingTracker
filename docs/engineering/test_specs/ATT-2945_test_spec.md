# Stage 2 Requirement & Test Specification: ATT-2945 - Climb cockpit bottom sheet appears on tracking tabs where live climbs are disabled

**Ticket**: [ATT-2945](https://atrainingtracker.atlassian.net/browse/ATT-2945)  
**Sub-task**: [ATT-3005](https://atrainingtracker.atlassian.net/browse/ATT-3005) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2945`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-327)

### 1.1 Requirement Definition
* **Requirement ID**: `REQ-UI-327`
* **Title**: Tracking Tab Live Climb and Segment Cockpit BottomSheet Active-Tab Gating & State Synchronization
* **Type**: Functional & Architectural UI Specification
* **Target Release**: `V4.9.40`
* **Status**: Specified
* **Amends/Complements**: Amends `REQ-UI-275` (*Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles*, Clause 6b, ATT-2360) and `REQ-UI-295` (*Unified Scrollable Container Architecture*, Clause 4, ATT-2620)
* **Parent Ticket**: ATT-2945

### 1.2 Description
The system shall strictly suppress the live climb and segment bottom sheet (`BottomSheetScaffold`) on any tracking tab where Live Climbs or Live Segments are disabled or when the tab is not the currently active page in `HorizontalPager`, eliminating bottom sheet peek artifacts, cold-start flashes, and cross-tab state bleeding:
1. *Active Tab Parameterization & Pre-composition Isolation*:
   - `TrackingTabsScreen.kt` shall determine active tab status for each page in `HorizontalPager`: `isTabActive = (pagerState.currentPage == targetPage)` (where `targetPage = index + 1` in `TRACKING` mode, accounting for the Control Tab on page 0).
   - `TrackingTabGridContent` and `SensorGridScreen` shall accept parameter `isTabActive: Boolean = true` (defaulting to `true` for standalone testing and preview).
   - In `SensorGridScreen.kt`, bottom sheet activation shall be gated strictly behind:
     `val shouldShowBottomSheet = isTabActive && (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING`
2. *Deterministic Sheet State Lifecycle & Hidden State Synchronization*:
   - `scaffoldState` in `SensorGridScreen.kt` shall initialize `bottomSheetState` with:
     `initialValue = if (shouldShowBottomSheet) SheetValue.PartiallyExpanded else SheetValue.Hidden`
     and `skipHiddenState = false`.
   - `SensorGridScreen.kt` shall execute a `LaunchedEffect(shouldShowBottomSheet)` that:
     (a) when `!shouldShowBottomSheet`: if `scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden`, calls `scaffoldState.bottomSheetState.hide()`.
     (b) when `shouldShowBottomSheet`: if `scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden`, calls `scaffoldState.bottomSheetState.partialExpand()`.
3. *Complete Surface & Peek Artifact Suppression*:
   - When `!shouldShowBottomSheet`:
     - `sheetPeekHeight = 0.dp`
     - `sheetSwipeEnabled = false`
     - `sheetShadowElevation = 0.dp`
     - `sheetTonalElevation = 0.dp`
     - `sheetContainerColor = Color.Transparent`
     - `sheetContent` shall NOT render any placeholder `Box` (render an empty composable block).
   - When `shouldShowBottomSheet`:
     - `sheetPeekHeight = BottomSheetDesign.PeekHeightLiveSegment + navBarHeight`
     - `sheetSwipeEnabled = true`
     - `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation`
     - `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation`
     - `sheetContainerColor = MaterialTheme.colorScheme.surface`.
4. *State Hygiene & Backward-Compatible Defaults (`TrackingViewModel.kt`)*:
   - `TrackingScreenState.showLiveClimbs` shall retain its default value of `true` (`val showLiveClimbs: Boolean = true`) for complete backward compatibility with `REQ-UI-275`, while active climb sheet display is strictly gated by `isTabActive && shouldShowBottomSheet` runtime evaluation.
5. *Preservation of System Invariants*:
   - Full ClimbPro cockpit functionality (`LiveClimbSheet`) on tabs where `showLiveClimbs == true` during active climbs (`APPROACHING` / `ON_CLIMB`).
   - Strava Live Segments (`LiveSegmentSheet`) priority and rendering.
   - Dynamic expand/collapse drag gestures and swipe interactions on active tabs.
   - Full compliance with `TrackingTabWysiwygContractTest.kt` (`REQ-UI-275` / `REQ-UI-295`).
   - 100% full clean-room unit test pass rate across all 2,253+ tests.

### 1.3 Acceptance Criteria (Given-When-Then)

#### Scenario 1: Tab with Live Climbs Disabled
* **Given** an active workout tracking session with an active climb in progress (`LiveClimbStatus.ON_CLIMB`),
* **When** viewing a tracking tab where `showLiveClimbs == false` in its configuration,
* **Then** `scaffoldState.bottomSheetState` is in `SheetValue.Hidden`,
* **And** `sheetPeekHeight` is `0.dp`,
* **And** `sheetShadowElevation` is `0.dp` with `Color.Transparent` container,
* **And** no climb bottom sheet, surface, or peek banner is visible.

#### Scenario 2: Dynamic Tab Swiping
* **Given** an active workout tracking session with an active climb,
* **When** swiping from a tab with Live Climbs enabled to a tab with Live Climbs disabled,
* **Then** the climb cockpit sheet is cleanly dismissed and hidden on the new tab,
* **And** when swiping back to the tab with Live Climbs enabled, the climb cockpit sheet restores its partially expanded state smoothly.

#### Scenario 3: Cold Start Composition Hygiene
* **Given** a tab configured with `showLiveClimbs == false`,
* **When** the tracking screen initializes,
* **Then** `TrackingScreenState.showLiveClimbs` defaults to `false`,
* **And** no transient flash or partial expansion of `LiveClimbSheet` occurs prior to database view info loading.

---

## 2. Test Specification (TST-UI-287)

### 2.1 Test Scope & Objectives
Validate the complete suppression of the bottom sheet on tabs with Live Climbs/Segments disabled, active-tab visibility parameterization, and reactive state synchronization.

### 2.2 Test Cases & Methodology

#### Test 1: Sub-State & Active-Tab Gating Contract Tests (`SensorGridScreenClimbSuppressionTest.kt`)
* Verify `SensorGridScreen` accepts parameter `isTabActive: Boolean = true`.
* Verify `shouldShowBottomSheet` incorporates `isTabActive`, `(showLiveSegments || showLiveClimbs)`, and `screenMode == ScreenMode.TRACKING`.
* Verify `scaffoldState.bottomSheetState` initializes with `if (shouldShowBottomSheet) SheetValue.PartiallyExpanded else SheetValue.Hidden`.
* Verify `LaunchedEffect` explicitly invokes `scaffoldState.bottomSheetState.hide()` when `!shouldShowBottomSheet` and `partialExpand()` when `shouldShowBottomSheet`.

#### Test 2: Visual Artifact & Surface Suppression Tests (`SensorGridScreenClimbSuppressionTest.kt`)
* Verify that when `!shouldShowBottomSheet`:
  - `sheetShadowElevation` resolves to `0.dp`.
  - `sheetTonalElevation` resolves to `0.dp`.
  - `sheetContainerColor` resolves to `Color.Transparent`.
  - `sheetContent` does not compose a 1.dp placeholder `Box`.

#### Test 3: TrackingScreenState Safe Default Tests (`TrackingViewModelGridTest.kt`)
* Verify `TrackingScreenState` defaults `showLiveClimbs` to `false`.

#### Test 4: Full Clean-Room Regression Suite
* Run `./gradlew testDebugUnitTest` verifying 100% pass rate across the entire test suite.

---

## 3. Traceability Matrix

| Requirement ID | Test Case ID | Test Class | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-327` | `TST-UI-287` | `SensorGridScreenClimbSuppressionTest` | Active tab gating, reactive sheet hiding, peek suppression | Specified |
| `REQ-UI-275` | `TST-UI-235` | `TrackingTabWysiwygContractTest` | Tab configuration spatial toggles & runtime gating | Specified |
| `REQ-UI-295` | `TST-UI-255` | `TrackingTabWysiwygContractTest` | Scrollable configuration container & config mode isolation | Specified |
