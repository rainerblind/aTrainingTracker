# Stage 3: Implementation Plan - ATT-1645: Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets (Flush Header-to-NavBar Baseline)

**Ticket**: [ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)  
**Sub-task**: [ATT-2054](https://atrainingtracker.atlassian.net/browse/ATT-2054) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-UI-221` (*UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing*)  
**Test Mapping**: `TST-UI-175` (*Bottom Sheet Peek Baselines, Design Token Centralization & System Navigation Inset Verification*)  
**Branch**: `feature/ATT-1645`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Architectural Motivation

Bottom sheet peek baselines across the application were standardized into centralized tokens in `BottomSheetDesign.kt` in Sprint `2026-40.8`. However, physical review on Google Pixel 10 hardware in Sprint `2026-40.11` confirmed that the sheet resting peek behavior was "still not ideal".

During Ceremony 1 for Sprint `2026-40.12`, the user clarified the exact visual and geometric invariant:
> *"There is the line between the white heading and the green map. This line must be exactly on top of the navigation bar."*

### Forensic Root Cause in `MapScreenWithTrack.kt` & `MapDetailLayout.kt`
1. **Initial Fallback Disparity**: In `BottomSheetDesign.kt`, `PeekHeightSegment = 192.dp` was significantly larger than the actual rendered height of the segment header ($\approx 140\text{dp}$ to $148\text{dp}$).
2. **Sheet Anchor Offset Desynchronization**: In `MapScreenWithTrack.kt`, when a segment or route is selected, `LaunchedEffect(selectedSegmentId)` executes `scaffoldState.bottomSheetState.partialExpand()`. At that initial instant, `measuredSegmentHeaderHeight` is still `null`, so the sheet expands to $192\text{dp} + \text{navBarHeight}$.
3. When `onHeaderHeightMeasured` fires with the real height (e.g. $144\text{dp}$), `sheetPeekHeight` updates, but in Material 3 Compose `BottomSheetScaffold`, the bottom sheet does not automatically animate or re-snap its resting offset to the new peek height if it was already partially expanded! It remains stuck at the oversized $192\text{dp} + \text{navBarHeight}$ position, exposing $\approx 48\text{dp}$ of the green map directly above the navigation bar.

To solve this permanently, this plan introduces **Dynamic Sheet Resting Offset Synchronization** and ensures that the separation line between the white header surface and the green map aligns flush with the top edge of the navigation bar.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-221` (*UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing*)
* **Test Mapping**: `TST-UI-175` (*Bottom Sheet Peek Baselines, Design Token Centralization & System Navigation Inset Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Flush Geometric Invariant**: In collapsed peek state, the dividing line between the bottom of the white heading card (`MinimumDragHandle() + Surface(header)`) and the top of the underlying map canvas MUST sit flush with the top edge of the system navigation bar (`WindowInsets.navigationBars`), showing 100% of the header and 0% of the map.
2. **System Insets Invariant**: Every persistent sheet peek height MUST strictly add `navBarHeight` (`WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`).
3. **Defensive Fallback Invariant**: When dynamic measurement is pending during the initial frame or in preview mode, `sheetPeekHeight` safely falls back to `BottomSheetDesign` baseline tokens.
4. **Maximum Height Boundary Constraint**: The maximum expanded height constraint (`maxSheetHeight = maxHeight - statusBarHeight`) established under `REQ-SET-069` remains strictly unaltered.
5. **Drag Handle Dimensions**: `MinimumDragHandle` layout dimensions (32dp x 3dp, 15dp total vertical footprint) remain strictly unaltered.
6. **Subtask Direct Completion**: Sub-task `ATT-2054` transitions directly to `Erledigt` upon passing Gate 3 review via `freigabe`.
7. **Parent Human Gate Invariance**: Moving parent ticket `ATT-1645` to `Erledigt` remains an inviolable human decision gate reserved exclusively for the user.

---

## 4. Proposed Architectural Changes

### Component 1: `MapScreenWithTrack.kt` (Dynamic Resting Offset Synchronization)
Ensure that when `measuredSegmentHeaderHeight` or `measuredRouteHeaderHeight` updates while the sheet is in `PartiallyExpanded` state, the bottom sheet re-snaps/partially expands to the exact measured height so the dividing line sits flush on the navigation bar:
```kotlin
// In MapScreenWithTrack.kt
LaunchedEffect(selectedSegmentId, selectedRouteId, selectedLocationId) {
    if (selectedSegmentId != null || selectedRouteId != null || selectedLocationId != null) {
        scaffoldState.bottomSheetState.partialExpand()
    } else {
        scaffoldState.bottomSheetState.hide()
    }
}

// When measured header height updates, ensure the sheet aligns flush to the new peek height
LaunchedEffect(measuredSegmentHeaderHeight, measuredRouteHeaderHeight) {
    if (scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded) {
        scaffoldState.bottomSheetState.partialExpand()
    }
}
```

### Component 2: `MapDetailLayout.kt` (Header Container Boundary Integrity)
Verify that `Column(onGloballyPositioned)` cleanly bounds `MinimumDragHandle()` and `Surface(header)` with zero trailing padding or extra margins before the map viewport starts:
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .onGloballyPositioned { coordinates ->
            if (!useStatusBarsPadding && onHeaderHeightMeasured != null) {
                val heightDp = with(density) { coordinates.size.height.toDp() }
                onHeaderHeightMeasured(heightDp)
            }
        }
) {
    if (!useStatusBarsPadding) {
        MinimumDragHandle()
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = if (useStatusBarsPadding) RectangleShape else BottomSheetDesign.SheetShape,
        modifier = if (useStatusBarsPadding) Modifier.statusBarsPadding() else Modifier
    ) {
        Box(modifier = Modifier.drawWithContent {
            headerLayer.record { this@drawWithContent.drawContent() }
            drawLayer(headerLayer)
        }) {
            header()
        }
    }
}
```

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Construction Gate Check
* Verify Gate 3 prerequisite: `python3 tools/jira_util.py check-gate ATT-2054`.
* Confirm command returns exit code 0 (`GATE_PASSED: ATT-2054 is Erledigt`).

### Step 2: Implement Offset Synchronization in `MapScreenWithTrack.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt`
* Add `LaunchedEffect(measuredSegmentHeaderHeight, measuredRouteHeaderHeight)` to re-trigger `partialExpand()` when the measured header height resolves.
* Verify `sheetPeekHeight` arithmetic guarantees the bottom of the white header sits flush on `Y_navBarTop`.

### Step 3: Verify Visual & Structural Contract Tests
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesignTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* Execute targeted test runner:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetDesignTest --tests com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetVisualContractTest
  ```

### Step 4: Clean-Room Full Suite Regression Execution
* Execute:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Verify 0 failures, 0 regressions across all modules.

---

## 6. Pre-Check Command for Stage 4

Before modifying any source code in Stage 4, execute:
```bash
python3 tools/jira_util.py check-gate ATT-2054
```
Code editing is strictly prohibited until this command exits with code 0.
