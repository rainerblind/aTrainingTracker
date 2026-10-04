# Stage 1 Analysis: ATT-2231 - Limit LiveSegment Bottom Sheet Expansion to Elevation Profile Height

**Ticket**: [ATT-2231](https://rainerblind.atlassian.net/browse/ATT-2231)  
**Sub-task**: [ATT-2372](https://rainerblind.atlassian.net/browse/ATT-2372) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (per ASPICE Rule 19: Lösungsversion added upon final acceptance)  
**Active Sprint**: `2026-40.15`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Domain & Root Cause Analysis (RCA)

### 1.1 Observed Defect
During active workout tracking, when a Strava or custom Live Segment is encountered, the segment is presented via a bottom sheet (`LiveSegmentSheet` embedded inside `BottomSheetScaffold` in `SensorGridScreen.kt`).
Currently, the sheet can be dragged/expanded all the way to the very top of the screen (`SheetValue.Expanded`, offset = 0).
This behavior is counter-productive:
1. `LiveSegmentSheet` (`showMap = false`) contains only the segment header (`SegmentHeader`), live segment stats (`SegmentLiveDetails`), and the elevation profile canvas (`ElevationProfile`). It does not have a map or full-screen scrollable telemetry lists.
2. Expanding the sheet to the top of the viewport completely obscures the primary cockpit telemetry (heart rate, cadence, power, speed, etc.).
3. The popup should only be draggable/expandable upward as far as necessary to display the complete elevation profile without covering the rest of the cockpit screen.

### 1.2 Forensic Root Cause
Forensic code investigation reveals a constraint leak in `MapDetailLayout.kt`:
1. In `MapDetailLayout.kt` (lines 495–499), `BoxWithConstraints` conditionally declares:
   ```kotlin
   modifier = modifier
       .then(
           if (showMap || hasScrollableContent) Modifier.fillMaxSize() else Modifier.wrapContentHeight()
       )
   ```
   The root container was correctly intended to wrap its content height when `!showMap && !hasScrollableContent`.
2. Furthermore, in the lower section branch (lines 693–722), `Column(modifier = Modifier.fillMaxWidth().wrapContentHeight())` correctly wraps the elevation profile column.
3. **The Defect**: However, at line 580, the intermediate viewport container `Box` was unconditionally hardcoded to `Modifier.fillMaxSize()`:
   ```kotlin
   // 2. RESIZABLE VIEWPORT (Map + SplitPaneDivider + Scrollable Lower Section)
   Box(
       modifier = Modifier
           .fillMaxSize() // <-- DEFECT: Forces child to expand to constraints.maxHeight
           .padding(top = currentTopPaddingDp)
   )
   ```
4. Because this child `Box` declares `fillMaxSize()`, it demands `constraints.maxHeight` (the full screen height) from `BoxWithConstraints`. Consequently, `BoxWithConstraints` measures as full screen height, causing `LiveSegmentSheet` to measure as full screen height.
5. In Material 3 `BottomSheetScaffold` (`SensorGridScreen.kt`), the `Expanded` state offset is computed as `max(0f, scaffoldHeight - sheetHeight)`. Since `sheetHeight == scaffoldHeight`, the sheet expands to offset 0 (the top of the viewport), completely covering the cockpit.
6. Additionally, attaching `Modifier.nestedScroll(connection)` unconditionally on `BoxWithConstraints` (line 503) binds `CollapsingAppBarNestedScrollConnection` even when there is no collapsing app bar or scrollable lower content, capturing drag gestures that should directly drive the bottom sheet.

---

## 2. Chesterton's Fence Requirement Archaeology

1. **Original Requirement ID & Target**:
   - Refines `REQ-UI-047` (*Live Segment Sheet Height Constraint*) and `REQ-UI-213` (*Resilient Map Preview Visibility & Scrollable Telemetry Layout Architecture*), targeting `MapDetailLayout.kt`, `LiveSegmentSheet.kt`, and `SensorGridScreen.kt`.
2. **Historical Origin & Commit Trace**:
   - `REQ-UI-047` was authored early in the project to prevent the live segment popup from covering cockpit telemetry.
   - Refactorings in `REQ-UI-223` (Interactive split pane, commit `69c84e1b`), `REQ-UI-245` (Upper metadata, commit `ATT-2112`), and `REQ-UI-246` (Persistent sticky lower viewport container, commit `ATT-2113`) altered `MapDetailLayout.kt`.
   - During the viewport architecture overhaul, line 580 was written with `Modifier.fillMaxSize()`, inadvertently overriding the wrap-content behavior intended by line 498 and breaking `REQ-UI-047`.
3. **Root Reason for Existing Formulation**:
   - Full-screen detail inspection screens (`RouteOnMapScreen`, `TrackOnMapScreen`, `SegmentOnMapScreen`, `WorkoutClusterHeatmapScreen`) require the map and scrollable telemetry to fill the remaining screen space (`fillMaxSize()`).
   - The developer generalized `Modifier.fillMaxSize()` at line 580 without branching on `showMap || hasScrollableContent`.
4. **Preservation of Core Invariants**:
   - Full-screen map screens with `showMap == true` continue using `Modifier.fillMaxSize()` for full-screen map and split-pane interactions (`REQ-UI-223`).
   - Scrollable telemetry screens (`hasScrollableContent == true`) continue using `Modifier.fillMaxSize()` for smooth vertical scrolling (`REQ-UI-213`).
   - Only when `!showMap && !hasScrollableContent` (i.e. `LiveSegmentSheet`), the viewport Box switches to `Modifier.fillMaxWidth().wrapContentHeight()`, restricting maximum sheet expansion strictly to the combined height of the segment header, live stats, and elevation profile.
   - Peek height baseline (`PeekHeightLiveSegment = 126.dp`) and two-state dragging between peek and elevation profile height are strictly preserved.
   - Cockpit telemetry in `SensorGridScreen` remains unobstructed.

---

## 3. Scope Bounding (In-Scope vs. Out-of-Scope)

### In-Scope:
* Branching viewport modifier in `MapDetailLayout.kt` (line 580) so that when `!showMap && !hasScrollableContent`, `Modifier.fillMaxWidth().wrapContentHeight()` is applied instead of `fillMaxSize()`.
* Conditionally attaching `nestedScroll(connection)` in `MapDetailLayout.kt` only when `showMap || hasScrollableContent`.
* Ensuring `LiveSegmentSheet.kt` exposes `modifier: Modifier = Modifier` and forwards `modifier.fillMaxWidth().wrapContentHeight()` to `MapDetailLayout`.
* Authoring unit / contract tests verifying that `LiveSegmentSheet` and `MapDetailLayout` (with `showMap = false`, `hasScrollableContent = false`) declare wrapped content height constraints and do not enforce `fillMaxSize`.

### Out-of-Scope:
* Altering full-screen map screens (`RouteOnMapScreen`, `TrackOnMapScreen`, `SegmentOnMapScreen`, `WorkoutClusterHeatmapScreen`).
* Modifying `LiveClimbSheet` (which already wraps content height).
* Redesigning `SensorGridScreen` sensor fields or grid layout.

---

## 4. Verification Strategy

1. **Architectural Contract Test (`LiveSegmentSheetContractTest.kt`)**:
   - Verify `LiveSegmentSheet` passes `showMap = false`, `showZoomControls = false`, and wrapped content height.
   - Verify `MapDetailLayout` applies `wrapContentHeight()` on the resizable viewport Box when `!showMap && !hasScrollableContent`.
2. **Targeted Unit Regression**:
   - Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.segments.*"`
   - Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"`
3. **Full Clean-Room Regression**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.
   - Execute `./gradlew assembleDebug` ensuring clean binary compilation.
