# Stage 5 Walkthrough: ATT-2231 - Limit LiveSegment Bottom Sheet Expansion to Elevation Profile Height

**Ticket**: [ATT-2231](https://rainerblind.atlassian.net/browse/ATT-2231)  
**Sub-task**: [ATT-2376](https://rainerblind.atlassian.net/browse/ATT-2376) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (per ASPICE Rule 19: Lösungsversion added upon final acceptance)  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-270`  
**Test Mapping**: `TST-UI-229`  
**Branch**: `feature/ATT-2231`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Executive Summary & Verification Objective

The goal of ATT-2231 was to eliminate an undesirable layout defect during active workout tracking in `SensorGridScreen.kt`: when a Strava or custom Live Segment was active, the bottom sheet (`LiveSegmentSheet`) could be dragged all the way to the top of the viewport (`SheetValue.Expanded`, offset = 0). This completely covered the primary cockpit telemetry (heart rate, cadence, power, speed, elapsed time) despite `LiveSegmentSheet` having only a compact header, live stats, and an elevation profile canvas (~280–320dp total height).

Forensic investigation revealed that in `MapDetailLayout.kt`, the intermediate resizable viewport container `Box` was unconditionally hardcoded to `Modifier.fillMaxSize()`. This demanded `constraints.maxHeight` from the parent `BoxWithConstraints`, inflating the bottom sheet's measured height to the full screen and allowing the Material 3 `BottomSheetScaffold` to anchor `Expanded` at offset 0.

By branching the viewport modifier so that it applies `Modifier.fillMaxWidth().wrapContentHeight()` when `!showMap && !hasScrollableContent`, and conditionally attaching `nestedScroll(connection)` only when `showMap || hasScrollableContent`, the sheet's measured height is clamped strictly to the combined natural height of its content. As a result, the bottom sheet expands only as far as necessary to reveal the elevation profile without covering the cockpit telemetry above.

---

## 2. Changes Implemented

### 2.1 Viewport Container & NestedScroll Clamping (`MapDetailLayout.kt`)
* Modified `BoxWithConstraints` to conditionally attach `Modifier.nestedScroll(connection)` only when `showMap || hasScrollableContent`, preventing drag interception on ambient bottom sheets.
* Branched the resizable viewport container modifier at line 580:
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

### 2.2 Modifier Parameterization (`LIveSegmentSheet.kt`)
* Parameterized `LiveSegmentSheet` with `modifier: Modifier = Modifier`.
* Forwarded `modifier = modifier.fillMaxWidth().wrapContentHeight()` into `MapDetailLayout`.

### 2.3 Bottom Sheet Container Clamping (`SensorGridScreen.kt`)
* Added explicit `.wrapContentHeight()` to the `LiveSegmentSheet` container `Box` inside `sheetContent`.

### 2.4 Automated Architectural Contract Tests (`LiveSegmentSheetContractTest.kt`)
Authored `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheetContractTest.kt` verifying:
* `testLiveSegmentSheet_forwardsWrapContentHeightAndSuppressesMap`: Confirms `LiveSegmentSheet` accepts `modifier`, applies `wrapContentHeight()`, and suppresses map and zoom controls.
* `testMapDetailLayout_branchesViewportModifierOnShowMapOrHasScrollableContent`: Asserts that `MapDetailLayout` branches its viewport modifier and uses `wrapContentHeight` when `!showMap && !hasScrollableContent`.
* `testMapDetailLayout_attachesNestedScrollConditionally`: Verifies `nestedScroll` is only attached when `showMap || hasScrollableContent`.
* `testSensorGridScreen_liveSegmentContainerWrapsContentHeight`: Asserts `SensorGridScreen` applies `wrapContentHeight()` on the LiveSegment container `Box`.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Unit & Contract Tests
* **Segments Package**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.segments.*"
  ```
  Result: `BUILD SUCCESSFUL in 1m` (All segments and contract tests passed).
* **Map Package**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"
  ```
  Result: `BUILD SUCCESSFUL in 12s` (All map and layout tests passed).

### 3.2 Full Clean-Room Regression Test Suite
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
  Result: `BUILD SUCCESSFUL` (100% pass rate across the full test suite).

### 3.3 Debug APK Build Verification
* **Command**:
  ```bash
  ./gradlew assembleDebug
  ```
  Result: `BUILD SUCCESSFUL` (Clean compilation and packaging).

---

## 4. Requirement Traceability Matrix

| Requirement | Test ID | Description | Result |
| :--- | :--- | :--- | :--- |
| **REQ-UI-270** | `TST-UI-229` | Clamped Maximum LiveSegment Bottom Sheet Expansion to Elevation Profile Content Height | **Verified** |

---

## 5. Invariant & Governance Compliance

* **Invariant 1 (Full-Screen Detail Map Views)**: All analytical inspection screens (`RouteOnMapScreen`, `SegmentOnMapScreen`, `TrackOnMapScreen`, `WorkoutClusterHeatmapScreen`) where `showMap == true` continue using `Modifier.fillMaxSize()`, preserving interactive map display, split-pane dragging, and full-screen telemetry layouts.
* **Invariant 2 (Cockpit Visibility)**: When the LiveSegment sheet is dragged to its maximum expansion, the upper cockpit area in `SensorGridScreen` remains completely visible and touch-accessible.
* **Invariant 3 (Two-State Controlled Dragging)**: The sheet transitions cleanly between `PartiallyExpanded` (`PeekHeightLiveSegment + navBarHeight`) and `Expanded` (elevation profile revealed).
* **Invariant 4 (Clean-Room Test Pass Rate)**: 100% pass rate maintained across all unit tests.
