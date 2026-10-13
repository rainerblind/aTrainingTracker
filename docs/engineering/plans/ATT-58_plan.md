# Stage 3: Implementation Plan - ATT-58: Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points (Rework Cycle 2)

**Ticket**: [ATT-58](https://rainerblind.atlassian.net/browse/ATT-58)  
**Sub-task**: [ATT-2418](https://rainerblind.atlassian.net/browse/ATT-2418) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-026` (*Support Waypoints, POIs and TCX Course Points*)  
**Test Mapping**: `TST-MAP-028` (*Support Waypoints, POIs and TCX Course Points Verification*)  
**Branch**: `feature/ATT-58`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Background

During acceptance testing of Cycle 1 on a physical Google Pixel 10 device, the Product Owner (PO) imported 5 real-world GPX routes from `/home/rainer/Downloads/Schwaebische_Alb` (*Gipfelkreuz auf dem Jusi*, *Wentaler Felsenmeer*, *Aussichtspunkt Floriansberg*, *Nebelhoehle*, and *Uracher Wasserfall*). While the routes imported successfully, **no waypoints appeared on the map**.

Forensic investigation revealed that:
1. `GpxImportActivity.kt` omitted `state.waypoints` in `viewModel.saveRoute(updatedSummary, state.points)`, silently discarding all extracted waypoints and writing 0 rows into `route_waypoints` in `Routes.db`.
2. `RouteItem.kt` thumbnails omitted waypoints from `MapRoute` in `PathPreviewMap`.
3. `MapLayers.kt` returned `true` from `Marker.onClick`, which suppressed the Google Maps native InfoWindow popup (`title` and `snippet`), causing markers to appear non-interactive.
4. `WaypointType.fromGpx` ignored descriptions (`<desc>` / `<cmt>`), misclassifying shelters (`"Unterstand"`) as `GENERIC`.
5. `GpxImportViewModel.kt` only parsed GPX, failing on `.tcx` course files.

Rework Cycle 2 provides complete end-to-end integration and persistence for waypoints and course points.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-026` (*Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points*)
* **Test Mapping**: `TST-MAP-028` (*Support Waypoints, POIs and TCX Course Points Verification*)
  * `TST-MAP-028.1`: `GpxImportViewModelTest.kt` (ViewModel waypoint persistence delegation)
  * `TST-MAP-028.2`: `RouteItemWaypointContractTest.kt` (Thumbnail waypoint propagation)
  * `TST-MAP-028.3`: `WaypointTypeClassificationTest.kt` (Description and German outdoor heuristic classification)
  * `TST-MAP-028.4`: `SchwaebischeAlbWaypointIntegrationTest.kt` (Real-world 5-file dataset validation)
  * `TST-MAP-028.5`: `TcxCourseParserTest.kt` (TCX course point extraction)
  * `TST-MAP-028.6`: `TranslationParityTest.kt` (9-language localization audit)
  * `TST-MAP-028.7`: Full suite clean-room regression (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing 1,761 unit tests across all modules continue to pass cleanly.
2. **Schema v10 Stability**: `route_waypoints` SQLite schema, table indices, and foreign key cascade deletion (`ON DELETE CASCADE`) are preserved without database breakage.
3. **DEM Enrichment Uncompromised**: `GpxRouteImporter` DEM elevation querying (`REQ-MAP-025`) continues to enrich coordinates when altitude is absent.
4. **Thread Safety & Dispatcher Affinity**: File parsing and database transactions remain strictly confined to `Dispatchers.IO`.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket [ATT-58](https://rainerblind.atlassian.net/browse/ATT-58) is strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `GpxImportActivity.kt` & `GpxImportViewModel.kt`
* **File**: [GpxImportActivity.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/GpxImportActivity.kt)
  - In `ImportState.Editing`, pass `state.waypoints` to `viewModel.saveRoute(updatedSummary, state.points, state.waypoints)`.
* **File**: [GpxImportViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModel.kt)
  - Support fallback to `TcxCourseParser` when URI represents a `.tcx` file or when GPX parsing fails.

### Component 2: `RouteItem.kt` & `RouteList.kt`
* **File**: [RouteItem.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItem.kt)
  - Add parameter `waypoints: List<RouteWaypoint> = emptyList()`.
  - Pass `waypoints` into `MapRoute` for `PathPreviewMap`.
* **File**: [RouteList.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteList.kt)
  - Extract `route.waypoints` from `RouteWithPath` and pass to `RouteItem`.

### Component 3: `MapLayers.kt`
* **File**: [MapLayers.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt)
  - In `RouteWaypointsLayer`, update `Marker.onClick`:
    ```kotlin
    onClick = {
        onWaypointClick(waypoint)
        false // Return false so Google Maps displays the native InfoWindow (title & snippet)
    }
    ```

### Component 4: `RouteWaypoint.kt` & `GpxRouteImporter.kt`
* **File**: [RouteWaypoint.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteWaypoint.kt)
  - Update `WaypointType.fromGpx` to accept `(sym: String?, type: String?, name: String?, desc: String? = null)`.
  - Expand keywords for outdoor and German terms (`unterstand`, `sitzgelegenheit`, `blick`, `grillplatz`, `parkplatz`).
* **File**: [GpxRouteImporter.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/GpxRouteImporter.kt)
  - Pass `wpt.desc ?: wpt.cmt` to `WaypointType.fromGpx`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Check Gate 3 Approval
* Verify that Stage 3 subtask `ATT-2418` is in status `Erledigt` via:
  ```bash
  python3 tools/jira_util.py check-gate ATT-2418
  ```

### Step 2: Fix UI Import Persistence in `GpxImportActivity.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/GpxImportActivity.kt`
* Pass `state.waypoints` in `EditRouteScreen.onSave` callback to `viewModel.saveRoute(...)`.

### Step 3: Forward Waypoints to Route Card Thumbnails in `RouteItem.kt` & `RouteList.kt`
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItem.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteList.kt`
* Propagate `waypoints` to `RouteItem` and populate `MapRoute(..., waypoints = waypoints)` in `PathPreviewMap`.

### Step 4: Fix Marker InfoWindow Interaction in `MapLayers.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt`
* In `RouteWaypointsLayer`, return `false` from `Marker.onClick` so the standard InfoWindow popup renders with `title` and `snippet`.

### Step 5: Enrich Semantic POI Classification in `RouteWaypoint.kt` & `GpxRouteImporter.kt`
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteWaypoint.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/GpxRouteImporter.kt`
* Add `desc` parameter to `WaypointType.fromGpx`, expand keyword matching (`unterstand`, `sitzgelegenheit`, `blick`, `grillplatz`, `parkplatz`), and pass `wpt.desc ?: wpt.cmt`.

### Step 6: Enable TCX Course Import in `GpxImportViewModel.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModel.kt`
* Integrate fallback to `TcxCourseParser` when TCX files are selected.

### Step 7: Author Real-World Schwäbische Alb Integration Test
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/SchwaebischeAlbWaypointIntegrationTest.kt`
* Test parsing and waypoint extraction across all 5 real-world GPX files in `/home/rainer/Downloads/Schwaebische_Alb`.

### Step 8: Update and Expand Unit Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModelTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/WaypointTypeClassificationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItemWaypointContractTest.kt`
* Run targeted verification:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.*" \
                              --tests "com.atrainingtracker.trainingtracker.ui.routes.*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run targeted unit tests:
    ```bash
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.SchwaebischeAlbWaypointIntegrationTest" \
                                --tests "com.atrainingtracker.trainingtracker.routes.WaypointTypeClassificationTest" \
                                --tests "com.atrainingtracker.trainingtracker.ui.routes.GpxImportViewModelTest" \
                                --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteItemWaypointContractTest"
    ```
  - Full clean-room test suite:
    ```bash
    ./gradlew testDebugUnitTest
    ```
* **Rollback Plan**:
  - Branch isolation on `feature/ATT-58` allows reverting individual commits or checking out `sprint/2026-40.16` cleanly if regressions occur.
