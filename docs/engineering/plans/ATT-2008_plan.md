# Stage 3: Implementation Plan - ATT-2008: [Bug] [UI/Sheets] Segment and Route Popups within Map Cannot Be Moved Upward Enough (Elevation Profile Occluded by Navigation Bar)

**Ticket**: [ATT-2008](https://rainerblind.atlassian.net/browse/ATT-2008)  
**Sub-task**: [ATT-2047](https://rainerblind.atlassian.net/browse/ATT-2047) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Requirement Mapping**: `REQ-UI-237` (*UI/Sheets: System Navigation Bar Inset Clearance & Upward Travel Calibration for Segment, Route, and Live Segment Popups*)  
**Test Mapping**: `TST-UI-196`  
**Branch**: `feature/ATT-2008`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

When inspecting segments or routes in `MapScreenWithTrack.kt`, or active live segments in `SensorGridScreen.kt` (`LiveSegmentSheet.kt`), the expanded bottom sheet does not provide sufficient upward travel and lacks bottom navigation bar window inset padding. Because `analyticsContent` is null in these screens, the elevation profile sits at the physical bottom edge of the display, causing the Android 3-button navigation bar (or gesture pill) to occlude the X-axis distance labels, zoom/pan controls, and scrubbing baseline. Furthermore, in `LiveSegmentSheet.kt`, because the wrapped content height lacks navigation bar insets, `BottomSheetScaffold` caps sheet expansion at the unpadded height, preventing the athlete from moving the popup upward to clear the navigation bar.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-237` (*UI/Sheets: System Navigation Bar Inset Clearance & Upward Travel Calibration for Segment, Route, and Live Segment Popups*)
* **Test Mapping**: `TST-UI-196` (*Segment, Route, and Live Segment Popup Navigation Bar Inset & Upward Travel Calibration Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing full-screen maps (`StarredSegmentsScreen.kt`, `RoutesScreen.kt`), aftermath screens (`TrackOnMapScreen.kt`, `PeriodMapScreen.kt`), and unit tests must continue to pass 100%.
2. **Double-Padding Immunity**: When `analyticsContent != null` (e.g. `TrackOnMapScreen.kt`), `analyticsContent` applies `Modifier.navigationBarsPadding()`; the elevation profile `Surface` must NOT apply navigation bar padding, strictly preventing double-padding.
3. **Dynamic Self-Measuring Peek Baseline**: Collapsed/peeked sheet heights established in `ATT-1645` (`measuredSegmentHeaderHeight + navBarHeight`) remain 100% intact.
4. **Edge-to-Edge Surface Uniformity**: The surface background of the bottom sheet extends seamlessly under the navigation bar using `MaterialTheme.colorScheme.surface`, eliminating visual seams (`REQ-UI-196`).
5. **Human Gate Governance (Rule 1)**: Subtasks advance directly to `Erledigt` via automated Gate audits (`freigabe`); parent ticket `ATT-2008` stops at `Final Review (Human)` assigned to the human user.

---

## 4. Proposed Architectural Changes

### Component 1: `MapDetailLayout.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`)
In `lowerColumn`:
* Update the `Surface` hosting `ElevationProfile` (and telemetry graphs) to conditionally apply `Modifier.navigationBarsPadding()` when `analyticsContent == null`:
  ```kotlin
  if (showElevationProfile) {
      activeScrubPath?.let { path ->
          Surface(
              color = MaterialTheme.colorScheme.surface,
              modifier = Modifier
                  .fillMaxWidth()
                  .then(
                      if (analyticsContent == null) Modifier.navigationBarsPadding() else Modifier
                  )
          ) {
              Box(modifier = Modifier.drawWithContent {
                  elevationLayer.record {
                      this@drawWithContent.drawContent()
                  }
                  drawLayer(elevationLayer)
              }) {
                  Column(modifier = Modifier.fillMaxWidth()) {
                      ...
                  }
              }
          }
      }
  } else if (analyticsContent == null && !useStatusBarsPadding) {
      Spacer(modifier = Modifier.navigationBarsPadding())
  }
  ```
* Impact on `LiveSegmentSheet.kt` (`SensorGridScreen.kt`):
  Because `LiveSegmentSheet` uses `MapDetailLayout(showMap = false, useStatusBarsPadding = false)`, its measured content height naturally expands by `navBarHeight`. `BottomSheetScaffold` automatically allows the sheet to drag and expand upward by the additional navigation bar height, placing the entire elevation profile above the 3-button navigation bar.
* Impact on `SegmentOnMapScreen.kt` / `RouteOnMapScreen.kt` (`MapScreenWithTrack.kt`):
  Inside `Box(modifier = Modifier.fillMaxWidth().height(maxSheetHeight))`, the elevation profile is lifted above the navigation bar by `navBarHeight`, preventing occlusion by the 3 buttons.

### Component 2: Structural Contract Test (`MapDetailLayoutNavigationBarsPaddingTest.kt`)
* Author `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutNavigationBarsPaddingTest.kt`.
* Verify that `MapDetailLayout.kt` applies `navigationBarsPadding()` to the elevation profile `Surface` when `analyticsContent == null`.
* Verify that `analyticsContent` retains its `navigationBarsPadding()`.
* Verify defensive fallback `Spacer(modifier = Modifier.navigationBarsPadding())` when both are absent.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Check Gate 3 Approval
* Command: `python3 tools/jira_util.py check-gate ATT-2047`
* Verify exit code 0 before modifying production code.

### Step 2: Implement Navigation Bar Padding in `MapDetailLayout.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* Changes: Update `lowerColumn` around lines 205-245 to apply `navigationBarsPadding()` when `analyticsContent == null`, and add defensive fallback spacer when both are absent in bottom sheet mode.

### Step 3: Implement Structural Contract Test
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutNavigationBarsPaddingTest.kt`
* Verify conditional navigation bar padding contracts.

### Step 4: Run Targeted Unit & Contract Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutNavigationBarsPaddingTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetVisualContractTest"
  ```
* Expected Result: 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted contract tests in Stage 4, followed by complete clean-room full suite regression (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback Plan**: All changes are isolated on branch `feature/ATT-2008`. If unexpected layout side-effects occur, revert commit or reset branch to `sprint/2026-40.11` without affecting other sprint tickets.
