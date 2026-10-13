# Stage 3: Implementation Plan - ATT-2863: Fix segment unselection toggle in route map layers menu to hide segment polylines

**Ticket**: [ATT-2863](https://rainerblind.atlassian.net/browse/ATT-2863)  
**Sub-task**: [ATT-2907](https://rainerblind.atlassian.net/browse/ATT-2907) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-UI-318` (*Route Map Segment Polyline Suppression on Layer Unselection & Individual Item Hiding*)  
**Test Mapping**: `TST-UI-278` (*Route Map Segment Polyline Suppression & Background Path Layer Filtering Contract Verification*)  
**Branch**: `improvement/ATT-2863`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

During physical on-device testing of `ATT-2763` on Google Pixel 10 (Sprint 2026-41.4 Review), unselecting "Segments" in the `RouteOnMapScreen` top app bar layers menu had no visual effect: segment polylines remained visible on the map canvas. Stage 1 forensic investigation identified that while `segments(...)` (matched route segments) was conditionally executed based on `RouteOverlayLayer.SEGMENTS in enabledOverlayLayers`, background segment polylines supplied via `backgroundPaths` (from `RoutesScreen.kt`) were unconditionally rendered via `contextualPaths(backgroundPaths)` at the end of `mapContent`. Furthermore, individually hidden segments (`hiddenSegmentIds`) were not excluded from `backgroundPaths`, and the Layers dropdown menu Segments entry availability check only considered `matchedSegments.isNotEmpty()`, disabling the toggle on routes that only had background segments.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-318` (*Route Map Segment Polyline Suppression on Layer Unselection & Individual Item Hiding*)
* **Test Mapping**: `TST-UI-278` (*Route Map Segment Polyline Suppression & Background Path Layer Filtering Contract Verification*)
  * `TST-UI-278.1`: Contract test verifying `RouteOnMapScreen.kt` filters out all `MapSegment` paths from `backgroundPaths` when `RouteOverlayLayer.SEGMENTS` is not in `enabledOverlayLayers`.
  * `TST-UI-278.2`: Contract test verifying `RouteOnMapScreen.kt` filters out any `MapSegment` in `backgroundPaths` matching `hiddenSegmentIds`.
  * `TST-UI-278.3`: Contract test verifying `RouteOnMapScreen.kt` prevents duplicate rendering of matched segments between `segments(...)` and `contextualPaths(backgroundPaths)`.
  * `TST-UI-278.4`: Contract test verifying `hasSegments` includes `backgroundPaths.any { it is MapSegment }`.
  * `TST-UI-278.5`: Full clean-room regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Permanent Selected Route Anchoring (`REQ-UI-308.1`)**: The selected route polyline (`TTColor.RouteSelected`) remains permanently rendered in `mapContent` and cannot be hidden or unchecked.
2. **Permanent Start and End Markers (`REQ-UI-308.3`)**: Start (`control_start`) and End (`control_stop`) navigation markers remain visible on the map canvas at all times regardless of layer state.
3. **Climbs & Waypoints Toggle Isolation**: Climbs and Waypoints layer filtering and breakdown toggles remain 100% operational and independent.
4. **Non-Segment Background Paths Preservation**: Any non-segment path in `backgroundPaths` (such as adjacent tracks or routes) remains rendered via `contextualPaths`.
5. **Subtask Direct Completion**: Sub-task transitions directly to `Erledigt` via transition `freigabe` upon Gate 3 audit pass.
6. **Parent Human Decision Gate**: Parent ticket ATT-2863 terminal transition is strictly `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `RouteOnMapScreen.kt` (`com.atrainingtracker.trainingtracker.ui.routes`)

1. **Matched Segment ID Extraction**:
   Pre-compute the set of matched segment IDs for efficient de-duplication:
   ```kotlin
   val matchedSegmentIds = remember(matchedSegments) {
       matchedSegments.map { it.segment.summary.stravaId }.toSet()
   }
   ```

2. **Dynamic Background Path Filtering in `mapContent`**:
   Filter `backgroundPaths` before passing to `contextualPaths`:
   ```kotlin
   val isSegmentsLayerEnabled = RouteOverlayLayer.SEGMENTS in enabledOverlayLayers
   val visibleBackgroundPaths = backgroundPaths.filter { path ->
       if (path is MapSegment) {
           isSegmentsLayerEnabled && path.stravaId !in hiddenSegmentIds && path.stravaId !in matchedSegmentIds
       } else {
           true
       }
   }
   if (visibleBackgroundPaths.isNotEmpty()) {
       contextualPaths(visibleBackgroundPaths, sameSportAlpha = TTAlpha.Medium)
   }
   ```

3. **Layers Menu Segments Availability Refinement**:
   Update `hasSegments` to account for segments present in `backgroundPaths`:
   ```kotlin
   val hasSegments = matchedSegments.isNotEmpty() || backgroundPaths.any { it is MapSegment }
   ```

### UI Consistency (Rule 23)
* **Closest Reference Screen**: `TrackOnMapScreen.kt` (layers menu paradigm) and existing `RouteOnMapScreen.kt`.
* **Reused Components**: Existing `DropdownMenu`, `DropdownMenuItem`, `Checkbox`, and `Icons.Default.Layers`.
* **Theme Tokens**: Standard `MaterialTheme.colorScheme.primary`, `TTColor.StravaOrange`, `TTAlpha.High`, `TTAlpha.Disabled`.
* **One-Off Styles**: None. All changes operate strictly within existing Compose map layout and theme tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Gate 3 Audit Sign-Off
* Ensure Gate 3 passes and ATT-2907 is `Erledigt`.
* Run mandatory check: `python3 tools/jira_util.py check-gate ATT-2907`.

### Step 2: Implement Background Paths Filtering and Availability in `RouteOnMapScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt`
* Add `matchedSegmentIds` computation.
* Update `mapContent` to filter `backgroundPaths` based on `isSegmentsLayerEnabled`, `hiddenSegmentIds`, and `matchedSegmentIds`.
* Update `hasSegments` in `overlay` to include `backgroundPaths.any { it is MapSegment }`.

### Step 3: Expand Contract Tests in `RouteOverlayLayersContractTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOverlayLayersContractTest.kt`
* Add architectural assertions verifying:
  * `backgroundPaths` is filtered against `RouteOverlayLayer.SEGMENTS in enabledOverlayLayers`.
  * `backgroundPaths` is filtered against `hiddenSegmentIds`.
  * `backgroundPaths` is filtered against `matchedSegmentIds`.
  * `hasSegments` includes `backgroundPaths.any { it is MapSegment }`.

### Step 4: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteOverlayLayersContractTest"
  ```

---

## 6. Clean-Room Regression Verification (Stage 5)
* Execute full clean-room suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Author walkthrough: `docs/engineering/walkthroughs/ATT-2863_walkthrough.md`.
* Mark `REQ-UI-318` and `TST-UI-278` as `Verified`.
