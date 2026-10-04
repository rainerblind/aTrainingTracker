# Stage 2 Requirement & Test Specification: ATT-2231 - Limit LiveSegment Bottom Sheet Expansion to Elevation Profile Height

**Ticket**: [ATT-2231](https://rainerblind.atlassian.net/browse/ATT-2231)  
**Sub-task**: [ATT-2373](https://rainerblind.atlassian.net/browse/ATT-2373) (`[Test-Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (per ASPICE Rule 19: Lösungsversion added upon final acceptance)  
**Active Sprint**: `2026-40.15`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Formal Requirement: REQ-UI-270

### REQ-UI-270: Clamped Maximum LiveSegment Bottom Sheet Expansion to Elevation Profile Content Height

The system SHALL restrict the maximum expansion of the `LiveSegmentSheet` bottom sheet during active tracking strictly to the combined height of the segment header, live stats, and elevation profile, ensuring primary cockpit telemetry remains unobstructed across all sheet states (ATT-2231):
1. **Adaptive Viewport Height Constraints (`MapDetailLayout.kt`)**:
   - In `MapDetailLayout.kt`, when `showMap == false` and `hasScrollableContent == false` (ambient bottom sheet mode for `LiveSegmentSheet`), the resizable viewport container `Box` SHALL apply `Modifier.fillMaxWidth().wrapContentHeight()` instead of `Modifier.fillMaxSize()`.
   - When `showMap == true` or `hasScrollableContent == true` (full-screen detail inspection views: `RouteOnMapScreen`, `TrackOnMapScreen`, `SegmentOnMapScreen`, `WorkoutClusterHeatmapScreen`), the resizable viewport container `Box` SHALL continue to apply `Modifier.fillMaxSize()`.
2. **Conditional NestedScroll Attachment (`MapDetailLayout.kt`)**:
   - The root `BoxWithConstraints` SHALL attach `Modifier.nestedScroll(connection)` if and only if `showMap || hasScrollableContent`. When `!showMap && !hasScrollableContent`, `nestedScroll` SHALL NOT be attached, ensuring touch gestures pass cleanly to the parent bottom sheet scaffold without app bar drag interception.
3. **Modifier Parameterization (`LiveSegmentSheet.kt`)**:
   - `LiveSegmentSheet` composable SHALL accept an optional `modifier: Modifier = Modifier`, forwarding `modifier.fillMaxWidth().wrapContentHeight()` to `MapDetailLayout`.
4. **Cockpit Visibility & Clamped Bottom Sheet Expansion (`SensorGridScreen.kt`)**:
   - In `SensorGridScreen.kt`, when `LiveSegmentSheet` is rendered inside `BottomSheetScaffold`, the bottom sheet's measured height SHALL equal the natural content height of the segment header, live stats, and elevation profile canvas (~280–320dp).
   - At `SheetValue.Expanded`, the bottom sheet offset SHALL equal `scaffoldHeight - sheetHeight`. The bottom sheet SHALL NOT expand to the top of the viewport (`offset = 0`), preserving complete visibility and touch interaction for primary cockpit telemetry (heart rate, cadence, power, speed).
   - Dragging between `SheetValue.PartiallyExpanded` (`PeekHeightLiveSegment + navBarHeight`) and `SheetValue.Expanded` (elevation profile revealed) SHALL remain fully supported.
5. **Chesterton's Fence Archaeology & Invariant Preservation**:
   - Refines `REQ-UI-047` (*Live Segment Sheet Height Constraint*) and `REQ-UI-213` (*Resilient Map Preview Visibility & Scrollable Telemetry Layout Architecture*).
   - Preserves full-screen map layouts (`REQ-UI-223`, `REQ-UI-245`, `REQ-UI-246`), interactive elevation zoom controls on detail maps (`REQ-UI-197`), unified surface background (`REQ-UI-196`, `REQ-UI-218`), and 100% clean-room test suite pass rate.

#### Acceptance Criteria (Given-When-Then)
* **AC-1 (Clamped Expansion & Cockpit Visibility)**:
  - *Given* an athlete in active tracking with a live segment triggered (`SensorGridScreen.kt`),
  - *When* the bottom sheet expands to `SheetValue.Expanded`,
  - *Then* the sheet SHALL stop expanding once the elevation profile is fully visible, and the upper section of the sensor grid / cockpit telemetry SHALL remain visible and unobstructed.
* **AC-2 (Two-State Controlled Dragging)**:
  - *Given* an active live segment bottom sheet,
  - *When* dragged downward,
  - *Then* the sheet SHALL transition to peek height (`PeekHeightLiveSegment + navBarHeight`), displaying only the header and live delta metrics.
* **AC-3 (Non-Full-Screen Viewport in MapDetailLayout)**:
  - *Given* `MapDetailLayout.kt` invoked with `showMap = false` and `hasScrollableContent = false`,
  - *When* inspected,
  - *Then* the viewport container `Box` SHALL apply `Modifier.fillMaxWidth().wrapContentHeight()` and SHALL NOT enforce `fillMaxSize()`.
* **AC-4 (Full-Screen Invariant Preservation for Maps)**:
  - *Given* full-screen map screens (`RouteOnMapScreen`, `SegmentOnMapScreen`, `TrackOnMapScreen`, `WorkoutClusterHeatmapScreen`),
  - *When* rendered with `showMap = true`,
  - *Then* they SHALL continue to occupy the full viewport using `Modifier.fillMaxSize()`.

---

## 2. Test Specification: TST-UI-229

### TST-UI-229: LiveSegmentSheet Clamped Bottom Sheet Height & MapDetailLayout Viewport Constraints Verification

1. **Architectural Contract Verification (`LiveSegmentSheetContractTest.kt`)**:
   - *Test 1.1*: Verify `LiveSegmentSheet.kt` passes `showMap = false`, `showZoomControls = false`, and applies `Modifier.fillMaxWidth().wrapContentHeight()`.
   - *Test 1.2*: Verify `MapDetailLayout.kt` evaluates `showMap || hasScrollableContent` when applying `fillMaxSize()` vs `wrapContentHeight()` on the resizable viewport Box.
   - *Test 1.3*: Verify `MapDetailLayout.kt` conditionally attaches `Modifier.nestedScroll(connection)` only when `showMap || hasScrollableContent`.
   - *Test 1.4*: Verify `SensorGridScreen.kt` wraps the LiveSegment bottom sheet content in `wrapContentHeight()`.
2. **Targeted Unit Regression**:
   - Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.segments.*"`
   - Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"`
3. **Clean-Room Full Suite Regression**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.
   - Execute `./gradlew assembleDebug` ensuring clean binary assembly.

---

## 3. Traceability Matrix

| Test Case ID | Requirement ID | Target Component | Verification Type | Status |
| :--- | :--- | :--- | :--- | :--- |
| **TST-UI-229.1** | `REQ-UI-270` | `LiveSegmentSheet.kt` | Unit / Architectural Contract Test | Specified |
| **TST-UI-229.2** | `REQ-UI-270` | `MapDetailLayout.kt` | Unit / Architectural Contract Test | Specified |
| **TST-UI-229.3** | `REQ-UI-270` | `SensorGridScreen.kt` | Layout / Contract Test | Specified |
| **TST-UI-229.4** | `REQ-UI-270` | Full App Build | Clean-Room Regression | Specified |
