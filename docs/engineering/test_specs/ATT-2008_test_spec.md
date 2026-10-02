# Stage 2: Requirement & Test Specification - ATT-2008: [Bug] [UI/Sheets] Segment and Route Popups within Map Cannot Be Moved Upward Enough (Elevation Profile Occluded by Navigation Bar)

**Ticket**: [ATT-2008](https://rainerblind.atlassian.net/browse/ATT-2008)  
**Sub-task**: [ATT-2046](https://rainerblind.atlassian.net/browse/ATT-2046) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Requirement Mapping**: `REQ-UI-237` (*UI/Sheets: System Navigation Bar Inset Clearance & Upward Travel Calibration for Segment, Route, and Live Segment Popups*)  
**Test Spec ID**: `TST-UI-196`  
**Branch**: `feature/ATT-2008`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-237)

### 1.1 Problem Statement & Rationale
During map inspection of segments and routes (`MapScreenWithTrack.kt`), as well as live segment popups during tracking (`SensorGridScreen.kt` / `LiveSegmentSheet.kt`), the bottom sheet / popup cannot be dragged or moved upward sufficiently. Because the upward travel or expanded height constraint does not properly account for Android system navigation bar window insets (`WindowInsets.navigationBars`), the elevation profile at the bottom of the sheet remains positioned behind the Android 3-button navigation bar (or gesture bar), occluding the chart, axis labels, and zoom/pan controls.

### 1.2 Functional & Architectural Requirements
1. **Navigation Bar Inset Clearance in `MapDetailLayout.kt`**:
   * In `MapDetailLayout.kt`, when `showElevationProfile` is true and `analyticsContent == null`, the `Surface` wrapping `ElevationProfile` (and telemetry graphs) SHALL apply `Modifier.navigationBarsPadding()`.
   * When `analyticsContent != null`, `analyticsContent` SHALL retain its `Modifier.navigationBarsPadding()`, and the elevation profile `Surface` SHALL NOT apply navigation bar padding, preventing double-padding.
   * When both `showElevationProfile` is false and `analyticsContent == null` in a bottom sheet context (`!useStatusBarsPadding`), `lowerColumn` SHALL defensively provide `Spacer(modifier = Modifier.navigationBarsPadding())`.
2. **Upward Travel & Expansion Height Clearance in `LiveSegmentSheet` (`SensorGridScreen.kt`)**:
   * Because `ElevationProfile`'s `Surface` applies `navigationBarsPadding()`, the total measured content height of `LiveSegmentSheet` naturally increases by `WindowInsets.navigationBars.calculateBottomPadding()`.
   * Material 3 `BottomSheetScaffold` SHALL allow the sheet to drag and expand upward by the additional navigation bar height, placing the entire elevation profile and all its controls 100% above the 3-button navigation bar.
3. **Map Detail Popup Alignment in `MapScreenWithTrack.kt`**:
   * When expanded, `SegmentOnMapScreen` and `RouteOnMapScreen` within `MapScreenWithTrack.kt` SHALL position the elevation profile above the system navigation bar, ensuring X-axis labels and touch controls remain fully visible and interactive.
4. **Preservation of Core Invariants**:
   * Peak baselines (`BottomSheetDesign.PeekHeightSegment`, `PeekHeightRoute`, `PeekHeightLiveSegment`, `PeekHeightKnownLocation`) established in `ATT-1645` remain 100% preserved.
   * Edge-to-edge drawing under navigation bars using `MaterialTheme.colorScheme.surface` remains intact.
   * Full-screen map views where `useStatusBarsPadding == true` (e.g. `StarredSegmentsScreen.kt`, `RoutesScreen.kt`) continue to render cleanly.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Upward Movement / Expanded Height Clearance)**:
  * *Given* a segment, route, or live segment popup displayed on the map or tracking cockpit,
  * *When* the user drags or expands the popup upward to its fully expanded state,
  * *Then* the sheet SHALL expand upward sufficiently to reveal the entire elevation profile completely above the Android system navigation bar.
* **Criterion 2 (Navigation Bar Button Non-Occlusion)**:
  * *Given* an expanded segment, route, or live segment popup on a device configured with 3-button navigation,
  * *When* inspecting the bottom of the sheet,
  * *Then* zero content, chart canvas, or axis labels SHALL be occluded behind the navigation bar buttons.
* **Criterion 3 (Dynamic Self-Measuring Peek Preservation)**:
  * *Given* a segment or route popup in its collapsed/peeked state,
  * *When* inspecting the sheet header,
  * *Then* the header SHALL remain fully visible above the system navigation bar with `peekHeight = base + navBarHeight`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-237`), extending and refining `REQ-UI-047` (*Live Segment Sheet Height Constraint*), `REQ-UI-148` (*Edge-to-Edge System Insets*), `REQ-UI-189` (*Standardized Boundaries & Contours*), `REQ-UI-196` (*Harmonized Spacing & Unified Surface Background*), and `REQ-UI-205` (*Slotted Analytics Content in MapDetailLayout*).
* **Historical Origin & Commit Trace**: Commit `69286d4e` (`ATT-1393`) introduced `analyticsContent` with `.navigationBarsPadding()`. Commit `f17dc40d` (`ATT-1645`) calibrated dynamic peek baselines.
* **Root Reason for Existing Formulation**: When `analyticsContent` was added to `MapDetailLayout`, `.navigationBarsPadding()` was placed directly on `analyticsContent`. However, for Segments, Routes, and Live Segments, `analyticsContent` is null, leaving `ElevationProfile` as the terminal component with zero navigation bar padding.
* **Preservation of Core Invariants**: 100% preservation of edge-to-edge drawing under the navigation bar, existing peek baselines, and full test suite pass rate.

---

## 2. Test Specification (TST-UI-196)

### Test Case 1: `MapDetailLayoutNavigationBarsPaddingTest` (`TST-UI-196.1`)
* **Scope**: Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutNavigationBarsPaddingTest.kt`
* **Preconditions**: `MapDetailLayout.kt` source code parsed.
* **Action**:
  - Verify that `MapDetailLayout.kt` applies `Modifier.navigationBarsPadding()` to the elevation profile `Surface` when `analyticsContent == null`.
  - Verify that `analyticsContent` retains its `navigationBarsPadding()`.
  - Verify that `MapDetailLayout.kt` provides fallback `Spacer(modifier = Modifier.navigationBarsPadding())` when both `showElevationProfile` is false and `analyticsContent == null` for bottom sheets.
* **Expected Result**: Structural contract assertions pass 100%.

### Test Case 2: Bottom Sheet Visual Contract & Inset Tests (`TST-UI-196.2`)
* **Scope**: UI Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* **Preconditions**: Verify `MapScreenWithTrack.kt`, `SensorGridScreen.kt`, and `LiveSegmentSheet.kt`.
* **Action**: Run existing and updated bottom sheet contract tests.
* **Expected Result**: All assertions pass.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-196.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-196.1` | Contract | `MapDetailLayoutNavigationBarsPaddingTest` | `REQ-UI-237` | Specified |
| `TST-UI-196.2` | Contract | `BottomSheetVisualContractTest` | `REQ-UI-237`, `REQ-UI-189` | Specified |
| `TST-UI-196.3` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
