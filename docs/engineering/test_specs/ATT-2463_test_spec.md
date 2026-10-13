# Stage 2: Requirement & Test Specification - ATT-2463: Remove corridor-based route grouping and differentiating thumbnail zoom (ATT-1954)

**Ticket**: [ATT-2463](https://rainerblind.atlassian.net/browse/ATT-2463)  
**Sub-task**: [ATT-2597](https://rainerblind.atlassian.net/browse/ATT-2597) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-MAP-035` (Supersedes and removes `REQ-MAP-030`)  
**Test Spec ID**: `TST-MAP-037`  
**Branch**: `feature/ATT-2463`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (REQ-MAP-035)

### 1.1 Problem Statement & Rationale
During Sprint 2026-40.16 physical testing on a Pixel 10 device, the corridor-based route grouping and differentiating thumbnail zoom features (`ATT-1954` / `REQ-MAP-030`) proved counterproductive:
1. Departure gateway direction filtering (North, East, South, West filter chips) cluttered `RouteTabbedScreen` and disrupted simple scanning of routes.
2. Differentiating thumbnail zoom cropped common entry/exit corridors to focus on unique middle loops, producing tight, disorienting map previews lacking geographical context.
3. The feature introduced dead preferences and string resources across 9 locales.

The system SHALL cleanly excise corridor-based route grouping and differentiating thumbnail zoom, restore flat list presentation and full-route bounding box map previews, extract shared geodesic calculation utilities (`GeoUtils.kt`) required by Fork Navigation (`REQ-MAP-031`), and clean up obsolete configuration keys and strings.

### 1.2 Functional & Architectural Requirements
1. **Extraction of Shared Geodesic Utilities (`GeoUtils.kt`)**:
   - The system SHALL extract pure mathematical calculations from `RouteCorridorClassifier.kt` into `app/src/main/java/com/atrainingtracker/trainingtracker/routes/GeoUtils.kt`:
     - `haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double`
     - `calculateInitialBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double`
   - `ForkRouteMatcher.kt` and `RouteDivergenceDetector.kt` SHALL consume `GeoUtils` with 100% functional equivalence.
2. **Complete Excision of Corridor Classification & Grouping**:
   - The system SHALL delete `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteCorridorClassifier.kt` (including `GatewayDirection` enum and corridor clustering).
   - The system SHALL delete `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GatewayFilterChipsRow.kt`.
   - `RouteTabbedScreen.kt` SHALL eliminate `GatewayFilterChipsRow`, `selectedGateway`, `availableGateways`, `hasGatewayChips`, and gateway filtering, rendering a clean flat route list for each sport tab.
3. **Complete Excision of Differentiating Thumbnail Zoom**:
   - The system SHALL delete `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteBoundingBoxCalculator.kt`.
   - In `RouteItem.kt`, the system SHALL eliminate `RouteBoundingBoxCalculator` calls and the `focusedThumbnailZoomEnabled` parameter.
   - `PathPreviewMap` in `RouteItem.kt` SHALL render the full global route bounding box (`targetBounds = null`).
   - In `RouteList.kt`, the parameter `focusedThumbnailZoomEnabled` SHALL be removed.
4. **Configuration & DataStore Cleanup**:
   - `TuningConfig` and `TuningPreferencesDataStore` SHALL deprecate / remove active `corridorGroupingEnabled` and `focusedThumbnailZoomEnabled` properties and mutators.
   - Persisted values in existing user DataStore instances SHALL be ignored gracefully without deserialization errors or runtime exceptions.
5. **Localization Cleanup**:
   - The system SHALL remove obsolete string resources across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`):
     - `gateway_all`, `gateway_north`, `gateway_northeast`, `gateway_east`, `gateway_southeast`, `gateway_south`, `gateway_southwest`, `gateway_west`, `gateway_northwest`, `gateway_unknown`.
     - `pref_corridor_grouping_title`, `pref_corridor_grouping_desc`, `pref_focused_thumbnail_title`, `pref_focused_thumbnail_desc`.
6. **Living Documentation & Invariant Preservation**:
   - `REQ-MAP-030` and `TST-MAP-032` SHALL be marked as `Superseded / Removed by ATT-2463`.
   - All other route management and navigation features (`REQ-MAP-024`, `REQ-MAP-028`, `REQ-MAP-029`, `REQ-MAP-031`, `REQ-MAP-033`, `REQ-MAP-034`) and 100% clean-room test pass rate MUST NOT be broken.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-030` (*Corridor-Based Route Grouping & Differentiating Thumbnail Zoom*).
* **Historical Origin & Commit Trace**: Ticket `ATT-1954`, Sprint `2026-40.16` (commit `69e9e1c4`), Epic `ATT-66` (*[Epic] Improve Routes*).
* **Root Reason for Existing Formulation**: Introduced to cluster routes by departure direction and avoid squashing unique route loops in previews.
* **Preservation of Core Invariants**: On-device physical validation revealed that corridor clustering complicated route browsing and cropped previews lacked essential geographic context. Reverting to full-extent thumbnails and a clean flat list restores optimal usability without affecting underlying route geometry, elevation profiles, or navigation engines. Shared geodesic math is preserved in `GeoUtils.kt`.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* an athlete viewing `RouteTabbedScreen`,
* *When* routes exist in any tab,
* *Then* no compass gateway filter chips (North, East, South, West, etc.) SHALL appear, and routes SHALL render in standard flat order.
* *Given* route cards in `RouteList`,
* *When* displaying map thumbnails,
* *Then* each map preview SHALL display the entire route bounding box without cropping common departure or arrival corridors.
* *Given* `ForkRouteMatcher` and `RouteDivergenceDetector`,
* *When* computing distances and approach/branch bearings,
* *Then* `GeoUtils.haversineDistanceMeters` and `GeoUtils.calculateInitialBearing` SHALL yield mathematically identical results to previous implementations.
* *Given* all 9 supported locales,
* *When* running translation parity tests,
* *Then* all removed strings SHALL be cleanly excised with zero dangling references.

---

## 2. Test Specification (TST-MAP-037)

### Test Case 1: Geodesic Utilities Correctness (`TST-MAP-037.1`)
* **Scope**: Mathematical Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/GeoUtilsTest.kt`
* **Actions**:
  1. Test `haversineDistanceMeters` for cardinal points, known distances, and zero displacement.
  2. Test `calculateInitialBearing` for North ($0^\circ$), East ($90^\circ$), South ($180^\circ$), West ($270^\circ$).
* **Expected Result**: Assertions pass within floating-point tolerance ($1\times 10^{-4}$).

### Test Case 2: Clean Route UI Contract Test (`TST-MAP-037.2`)
* **Scope**: Structural AST / Composable Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteTabbedCleanLayoutTest.kt`
* **Actions**:
  1. Verify `RouteTabbedScreen.kt` contains no references to `GatewayFilterChipsRow`, `GatewayDirection`, or `RouteCorridorClassifier`.
  2. Verify `RouteItem.kt` contains no references to `RouteBoundingBoxCalculator` or `calculateDifferentiatingBounds`.
  3. Verify `RouteList.kt` exposes clean parameters without `focusedThumbnailZoomEnabled`.
* **Expected Result**: All structural assertions pass.

### Test Case 3: 9-Language Localization Parity Audit (`TST-MAP-037.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Actions**:
  1. Verify zero missing translation tags across all 9 locales.
  2. Verify obsolete gateway and preference strings are absent from all `strings.xml`.
* **Expected Result**: Audit passes cleanly with 0 errors.

### Test Case 4: Full Clean-Room Regression Suite (`TST-MAP-037.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate across the full suite with zero failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-037.1` | Unit | `GeoUtils` distance & bearing | `REQ-MAP-035` Clause 1 | Specified |
| `TST-MAP-037.2` | UI Contract | `RouteTabbedScreen`, `RouteItem` | `REQ-MAP-035` Clauses 2, 3 | Specified |
| `TST-MAP-037.3` | Localization | `TranslationParityTest` | `REQ-MAP-035` Clause 5 | Specified |
| `TST-MAP-037.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
