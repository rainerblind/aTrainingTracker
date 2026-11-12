# Stage 3: Implementation Plan - ATT-7315: Differentiate map marker label for A->B routes

**Ticket**: [ATT-7315](https://rainerblind.atlassian.net/browse/ATT-7315)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-SET-097` (`REQ-SET-097.5`)  
**Test Mapping**: `TST-SET-097` (`TST-SET-097.5`)  
**Branch**: `subagent-Lane-3-Agent---ATT-7315-self-2da9c00a`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

In ATT-6686, route clustering on point-to-point (A->B) routes was updated to evaluate the spatial apex via the bipolar product $d(\text{start}, P) \times d(P, \text{end})$, positioning the apex pin near the geometric midpoint or lateral corridor peak. However, the UI marker label for this point remained hardcoded to `R.string.max_line_distance` ("weiteste Entfernung" / "furthest away"). On point-to-point commutes, this is confusing because the destination is the furthest point. The label must be dynamically differentiated to "Scheitelpunkt" (Apex) for point-to-point routes ($d(\text{start}, \text{end}) \ge 150\text{ m}$) while preserving "weiteste Entfernung" for closed-loop routes ($d(\text{start}, \text{end}) < 150\text{ m}$).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-SET-097.5` (*Differentiated Map Marker Labeling for Point-to-Point Routes*)
* **Test Mapping**: `TST-SET-097.5` (*Differentiated Map Marker Labeling Test Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Closed-Loop Invariance**: Closed loops ($d(\text{start}, \text{end}) < 150\text{ m}$) remain labeled `max_line_distance` ("weiteste Entfernung" / "furthest away") 100% preserving historical behavior.
2. **Icon & Visual Styling**: Marker drawable `R.drawable.ic_distance` and color token `TTColor.ApexPoint` remain identical.
3. **Database Schema & Telemetry**: Zero database schema mutations; `ExtremaType.MAX_LINE_DISTANCE` and raw telemetry persistence remain untouched.
4. **9-Language Parity**: All 9 localized resource files define `route_apex` without missing keys.

---

## 4. Proposed Architectural Changes

### Component 1: Localization Resources (`res/values*/strings.xml`)
Introduce `route_apex` string in all 9 language variants:
- English (`values`): `Apex`
- German (`values-de`): `Scheitelpunkt`
- Spanish (`values-es`): `Ápice`
- French (`values-fr`): `Apex`
- Italian (`values-it`): `Apice`
- Japanese (`values-ja`): `頂点`
- Dutch (`values-nl`): `Apex`
- Polish (`values-pl`): `Wierzchołek`
- Portuguese (`values-pt`): `Ápice`

### Component 2: Domain Model Helpers (`WorkoutData.kt`, `WorkoutClusterDatabaseManager.kt`)
Add `val isPointToPoint: Boolean` property to both `WorkoutData` and `WorkoutCluster` based on $d(\text{start}, \text{end}) \ge 150\text{ m}$ using `WorkoutClusterEngine.distanceBetween`.

### Component 3: UI Marker Title Resolution
In `WorkoutRepository.getMapMarkerList`, `WorkoutClustersViewModel`, `WorkoutClusterHeatmapScreen`, `MapScreenWithTrack`, and `ManualClusterScreen`, conditionally emit `route_apex` when `isPointToPoint == true`, else `max_line_distance`.

### UI Consistency (Rule 23)
* **Reference screen / component**: Existing map markers in `MapScreenWithTrack.kt` and `WorkoutClusterHeatmapScreen.kt`.
* **Reused components**: Existing `LocationMarker`, `ClusterPeakMarker`, `createSensorMarker`, `PointToggleButton`.
* **Theme tokens**: Color `TTColor.ApexPoint`, icon `R.drawable.ic_distance`.
* **New one-off styles & justification**: None. Reuses existing tokens and design system.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localization Resource Definition
* Files: `app/src/main/res/values*/strings.xml` (all 9 locale files).
* Changes: Define `<string name="route_apex">...</string>`.

### Step 2: Domain Model Property Addition
* Files: `WorkoutData.kt`, `WorkoutClusterDatabaseManager.kt`.
* Changes: Add `val isPointToPoint: Boolean` getter computing geodetic distance between start and end coordinates with $150\text{ m}$ threshold.

### Step 3: UI & Repository Marker Title Wiring
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ManualClusterScreen.kt`
* Changes: Bind apex marker title and toggle labels to `route_apex` for point-to-point and `max_line_distance` for closed loop.

### Step 4: Unit Test Authoring & Verification
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryMapMarkerApexTest.kt`
* Changes: Test `isPointToPoint` domain logic and marker title assignment for both topologies.
* Command: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryMapMarkerApexTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests, translation parity test, and clean-room full test suite in Stage 5.
* **Rollback**: Working branch isolation allows trivial git revert.
