# Stage 3 Implementation Plan: ATT-2231 - Limit LiveSegment Bottom Sheet Expansion to Elevation Profile Height

**Ticket**: [ATT-2231](https://rainerblind.atlassian.net/browse/ATT-2231)  
**Sub-task**: [ATT-2374](https://rainerblind.atlassian.net/browse/ATT-2374) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (per ASPICE Rule 19: Lösungsversion added upon final acceptance)  
**Active Sprint**: `2026-40.15`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Architectural Overview (SWE.2)

### 1.1 Component Boundaries
```
+-----------------------------------------------------------------------------------+
| SensorGridScreen.kt (BottomSheetScaffold)                                         |
|   sheetPeekHeight = PeekHeightLiveSegment + navBarHeight                          |
|   sheetContent = Box(wrapContentHeight()) -> LiveSegmentSheet                     |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| LiveSegmentSheet.kt                                                               |
|   MapDetailLayout(modifier = Modifier.fillMaxWidth().wrapContentHeight(),         |
|                    showMap = false, showZoomControls = false)                     |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| MapDetailLayout.kt                                                                |
|   BoxWithConstraints(modifier = wrapContentHeight())                              |
|     1. Header: SegmentHeader + live stats (measured: headerHeightPx)              |
|     2. Viewport Box: if (showMap || hasScrollable) fillMaxSize()                  |
|                      else fillMaxWidth().wrapContentHeight()                      |
|          padding(top = currentTopPaddingDp)                                       |
|          lowerColumn -> ElevationProfile (adaptive height)                        |
|   Total sheet height = headerHeight + elevationProfileHeight (~280-320dp)         |
|   Scaffold SheetValue.Expanded offset = scaffoldHeight - sheetHeight (NOT 0!)     |
+-----------------------------------------------------------------------------------+
```

### 1.2 Layout & Constraint Invariants
1. **Full-Screen Map Isolation**: Detail inspection layouts with `showMap == true` (`RouteOnMapScreen`, `SegmentOnMapScreen`, `TrackOnMapScreen`, `WorkoutClusterHeatmapScreen`) or scrollable telemetry (`hasScrollableContent == true`) continue to apply `Modifier.fillMaxSize()`.
2. **Ambient LiveSegment Clamping**: When `!showMap && !hasScrollableContent`, both `BoxWithConstraints` and the inner viewport `Box` wrap content height, ensuring the bottom sheet height equals only the natural height of the header, stats, and elevation profile.
3. **Gesture Safety**: `nestedScroll(connection)` is conditionally attached only when `showMap || hasScrollableContent`, eliminating touch delta interception during bottom sheet dragging.
4. **Cockpit Visibility**: The top section of `SensorGridScreen` remains visible and touch-accessible when the live segment sheet is expanded.

---

## 2. Step-by-Step Construction Plan

### Step 1: Update Viewport Box and NestedScroll in `MapDetailLayout.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* **Changes**:
  1. At line 503, conditionally attach `.nestedScroll(connection)`:
     ```kotlin
     .then(
         if (showMap || hasScrollableContent) Modifier.nestedScroll(connection) else Modifier
     )
     ```
  2. At line 580, branch the viewport modifier:
     ```kotlin
     val viewportModifier = if (showMap || hasScrollableContent) {
         Modifier.fillMaxSize()
     } else {
         Modifier.fillMaxWidth().wrapContentHeight()
     }
     Box(
         modifier = viewportModifier
             .padding(top = currentTopPaddingDp)
     )
     ```

### Step 2: Parameterize and Wrap Modifier in `LIveSegmentSheet.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt`
* **Changes**:
  1. Add `modifier: Modifier = Modifier` to `LiveSegmentSheet` function signature.
  2. Pass `modifier = modifier.fillMaxWidth().wrapContentHeight()` into `MapDetailLayout`.

### Step 3: Explicitly Declare `wrapContentHeight()` in `SensorGridScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  1. Ensure the `Box` wrapping `LiveSegmentSheet` in `sheetContent` has `Modifier.fillMaxWidth().wrapContentHeight()`.

### Step 4: Author Architectural Contract Tests
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheetContractTest.kt`
* **Changes**:
  1. Verify `LiveSegmentSheet` specifies `showMap = false` and `wrapContentHeight()`.
  2. Verify `MapDetailLayout` uses `wrapContentHeight()` on the resizable viewport Box when `!showMap && !hasScrollableContent`.
  3. Verify `MapDetailLayout` conditionally attaches `nestedScroll` only when `showMap || hasScrollableContent`.
  4. Verify `SensorGridScreen` wraps `LiveSegmentSheet` in `wrapContentHeight()`.

### Step 5: Verification & Targeted Test Execution
* **Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.segments.*"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"
  ```

---

## 3. Mandatory Programmatic Pre-Check Note (Rule 3)
* Prior to modifying any production code in `app/src/...` during Stage 4, the agent MUST run:
  `python3 tools/jira_util.py check-gate ATT-2374`
  and confirm it exits with code 0 (`GATE_PASSED: ATT-2374 is Erledigt`).
