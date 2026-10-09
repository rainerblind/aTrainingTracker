# Stage 2: Requirement & Test Specification - ATT-2863: Fix segment unselection toggle in route map layers menu to hide segment polylines

**Ticket**: [ATT-2863](https://rainerblind.atlassian.net/browse/ATT-2863)  
**Sub-task**: [ATT-2906](https://rainerblind.atlassian.net/browse/ATT-2906) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-UI-318` (*Route Map Segment Polyline Suppression on Layer Unselection & Individual Item Hiding*)  
**Test Spec ID**: `TST-UI-278`  
**Branch**: `improvement/ATT-2863`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-318)

### 1.1 Problem Statement & Rationale
During physical on-device testing of `ATT-2763` on Google Pixel 10 (Sprint 2026-41.4 Review), unselecting "Segments" in the `RouteOnMapScreen` top app bar layers menu had no visual effect: segment polylines remained visible on the map canvas. Investigation revealed that while `segments(...)` (matched route segments) was conditionally rendered based on `RouteOverlayLayer.SEGMENTS in enabledOverlayLayers`, background segments supplied via `backgroundPaths` (from `RoutesScreen.kt`) were rendered unconditionally via `contextualPaths(backgroundPaths)` at the end of `mapContent`. Furthermore, individually hidden segments (`hiddenSegmentIds`) were not excluded from `backgroundPaths`, and the Layers dropdown menu Segments entry availability check only considered `matchedSegments.isNotEmpty()`, disabling the toggle on routes that only had background segments.

### 1.2 Functional & Architectural Requirements
The system SHALL suppress ALL segment polylines (including both matched route segments and background contextual segment paths) from the map canvas when the Segments layer is unselected in the route map layers menu, filter out individual hidden segments from background paths, and accurately reflect Segments layer availability (ATT-2863, amending `REQ-UI-308`):
1. **Full Map Canvas Segment Polyline Suppression (`RouteOnMapScreen.kt`)**:
   * When the Segments overlay layer is unchecked (`RouteOverlayLayer.SEGMENTS !in enabledOverlayLayers`), the system SHALL suppress ALL segment polylines from `mapContent`.
   * Both `segments(...)` (matched route segments) and any `MapSegment` instances contained in `backgroundPaths` passed to `contextualPaths(...)` SHALL be suppressed from the map canvas.
2. **Individual Breakdown Card Hidden Segment Filtering in Background Paths**:
   * When an individual segment is toggled off (`hiddenSegmentIds: Set<Long>`), any `MapSegment` in `backgroundPaths` whose `stravaId` matches an ID in `hiddenSegmentIds` SHALL be excluded from `contextualPaths(...)`, preventing hidden segments from leaking back onto the map canvas as background polylines.
3. **De-duplication of Matched and Background Segments**:
   * When the Segments layer is enabled (`RouteOverlayLayer.SEGMENTS in enabledOverlayLayers`), any segment that is already rendered as a matched segment via `segments(...)` SHALL be excluded from `contextualPaths(backgroundPaths)` so that matched segments are rendered exclusively with interactive click handlers and live segment styling.
4. **Segments Layer Menu Availability Refinement**:
   * In the route map layers dropdown menu, the Segments layer entry SHALL be enabled if `matchedSegments.isNotEmpty() || backgroundPaths.any { it is MapSegment }`, allowing athletes to toggle segment visibility even on routes with only background segments.
5. **Preservation of System Invariants**:
   * Selected route polyline (`TTColor.RouteSelected`), Start (`control_start`) and End (`control_stop`) markers, and Climbs/Waypoints layer controls MUST NOT be affected.
   * Non-segment paths in `backgroundPaths` (e.g. tracks or routes, if present) MUST remain visible.
   * 100% test pass rate across the unit test suite.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Full Segment Suppression on Layer Uncheck)**:
  * *Given* an athlete viewing a route on `RouteOnMapScreen` with segment polylines visible on the map,
  * *When* the athlete taps the Layers button and unchecks "Segments",
  * *Then* ALL segment polylines (matched segments and background segments) SHALL immediately disappear from the map canvas.
* **Criterion 2 (Individual Hidden Segment Filtering)**:
  * *Given* a route with segment polylines and an individual segment hidden in the segments breakdown list (`hiddenSegmentIds`),
  * *When* rendered on the map,
  * *Then* that specific segment's polyline SHALL NOT be rendered in either the matched segments layer or the background paths layer.
* **Criterion 3 (Segments Toggle Availability)**:
  * *Given* a route with background segments but no matched segments,
  * *When* the athlete opens the Layers dropdown menu,
  * *Then* the "Segments" checkbox SHALL be enabled and interactive.
* **Criterion 4 (Permanent Route Anchoring & Invariant Preservation)**:
  * *Given* `RouteOnMapScreen`,
  * *When* toggling layers or hiding segments,
  * *Then* the primary selected route polyline (`TTColor.RouteSelected`) and Start/End markers remain permanently rendered.

---

## 2. Test Specification (TST-UI-278)

### Test Case 1: Architectural Contract Test for Segment Layer Suppression in `backgroundPaths` (`TST-UI-278.1`)
* **Scope**: Unit & Contract Test (`RouteOverlayLayersContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOverlayLayersContractTest.kt`
* **Checks**:
  * Verify `RouteOnMapScreen.kt` filters `backgroundPaths` based on `RouteOverlayLayer.SEGMENTS in enabledOverlayLayers`.
  * Verify all `MapSegment` instances are removed from `backgroundPaths` when `RouteOverlayLayer.SEGMENTS` is not in `enabledOverlayLayers`.
  * Verify non-segment `backgroundPaths` remain intact when `RouteOverlayLayer.SEGMENTS` is disabled.

### Test Case 2: Individual Hidden Segment Filtering in `backgroundPaths` (`TST-UI-278.2`)
* **Scope**: Unit & Contract Test (`RouteOverlayLayersContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOverlayLayersContractTest.kt`
* **Checks**:
  * Verify `RouteOnMapScreen.kt` filters `backgroundPaths` using `hiddenSegmentIds`.
  * Verify any `MapSegment` whose `stravaId in hiddenSegmentIds` is omitted from `contextualPaths(...)`.

### Test Case 3: Matched Segment De-duplication (`TST-UI-278.3`)
* **Scope**: Unit & Contract Test (`RouteOverlayLayersContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOverlayLayersContractTest.kt`
* **Checks**:
  * Verify `RouteOnMapScreen.kt` excludes `matchedSegmentIds` from `backgroundPaths` to prevent duplicate rendering of matched segments.

### Test Case 4: Layers Dropdown Availability for Background Segments (`TST-UI-278.4`)
* **Scope**: Unit & Contract Test (`RouteOverlayLayersContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOverlayLayersContractTest.kt`
* **Checks**:
  * Verify `hasSegments` includes `backgroundPaths.any { it is MapSegment }`.
  * Verify dropdown menu Segments item is enabled when `hasSegments` is true.

### Test Case 5: Clean-Room Full Suite Regression Execution (`TST-UI-278.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% test pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-278.1` | Contract | `RouteOnMapScreen` background paths layer filtering | `REQ-UI-318.1` | Specified |
| `TST-UI-278.2` | Contract | `RouteOnMapScreen` background paths hiddenSegmentIds filtering | `REQ-UI-318.2` | Specified |
| `TST-UI-278.3` | Contract | `RouteOnMapScreen` matched segment de-duplication | `REQ-UI-318.3` | Specified |
| `TST-UI-278.4` | Contract | `RouteOnMapScreen` layers menu `hasSegments` availability | `REQ-UI-318.4` | Specified |
| `TST-UI-278.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-UI-318.5` | Specified |
