# Stage 1 Analysis: ATT-7315 - Differentiate map marker label for A->B routes: replace 'Weiteste Entfernung' with 'Scheitelpunkt'

**Ticket**: [ATT-7315](https://rainerblind.atlassian.net/browse/ATT-7315)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Branch**: `subagent-Lane-3-Agent---ATT-7315-self-2da9c00a`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During Sprint 2026-46.3 Joint Review of ATT-6686, the Product Owner observed a user-facing labeling inconsistency on point-to-point (A->B) commute routes:
- **Current Behavior**: On all routes (both closed loops and point-to-point commutes), the route apex marker (blue distance pin `R.drawable.ic_distance`) is uniformly labeled `R.string.max_line_distance` ("weiteste Entfernung" in German, "furthest away" in English). In ATT-6686, `findApexFromPoints` was updated to maximize the bipolar product $d(\text{start}, P) \times d(P, \text{end})$, placing the apex marker near the geometric midpoint / lateral detour peak of A->B routes. Labeling this intermediate point "weiteste Entfernung" is confusing and factually inaccurate to athletes, as the destination (Ende / Stop) is the furthest point from start.
- **Expected Behavior**:
  - Closed-loop routes ($d(\text{start}, \text{end}) < 150\text{ m}$): Retain `R.string.max_line_distance` ("weiteste Entfernung" / "furthest away"), as it correctly represents the turnaround point furthest from the origin.
  - Point-to-point / commute routes ($d(\text{start}, \text{end}) \ge 150\text{ m}$): Differentiate marker label to `R.string.route_apex` ("Scheitelpunkt" in German, "Apex" across all 9 localized languages).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

- **Root Cause**:
  In `WorkoutRepository.getMapMarkerList(workoutData)`, `WorkoutClustersViewModel`, `WorkoutClusterHeatmapScreen`, and `MapScreenWithTrack`, the marker title for `ClusterMarkerType.DISTANCE` is hardcoded to `R.string.max_line_distance`. While this was appropriate when routes were assumed to be circular or closed loops, ATT-6686 established a clear geometric distinction for point-to-point routes ($d(\text{start}, \text{end}) \ge 150\text{ m}$), where the apex is positioned by bipolar product optimization as the route apex/midpoint rather than maximal geodesic displacement from start.
- **Independent Peer Review & Verification**:
  - Audited live sprint codebase:
    1. `WorkoutRepository.kt` (lines 2380–2382): Adds `LocationMarker(it, R.drawable.ic_distance, application.getString(R.string.max_line_distance))`.
    2. `WorkoutClustersViewModel.kt` (line 387): Pre-calculates member markers using `application.getString(R.string.max_line_distance)`.
    3. `WorkoutClusterHeatmapScreen.kt` (lines 114, 165, 496, 573, 734): Hardcodes `apexLabel = stringResource(R.string.max_line_distance)`.
    4. `MapScreenWithTrack.kt` (lines 689, 733): Hardcodes `apexLabel = stringResource(R.string.max_line_distance)`.
    5. `ManualClusterScreen.kt` (lines 206, 274, 288): References `R.string.max_line_distance`.
  - Verified geometric threshold: `WorkoutClusterEngine.kt` (line 328) defines point-to-point as `end != null && distanceBetween(start, end) >= 150f`.
  - Confirmed evidence: `commute_apex_screenshot.png` shows an A->B track where the midpoint pin was labeled "weiteste Entfernung".

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Introduce string resource `route_apex` with 9-language localization parity (DE: "Scheitelpunkt", EN: "Apex", ES: "Ápice", FR: "Apex", IT: "Apice", JA: "頂点", NL: "Apex", PL: "Wierzchołek", PT: "Ápice").
  * Expose an authoritative route classification helper `isPointToPoint` on `WorkoutCluster` and `WorkoutData` based on $d(\text{start}, \text{end}) \ge 150\text{ m}$.
  * Differentiate the map marker label across `WorkoutRepository`, `WorkoutClustersViewModel`, `WorkoutClusterHeatmapScreen`, and `MapScreenWithTrack`: conditionally emit `route_apex` for point-to-point routes and `max_line_distance` for closed loops.
  * Update targeted unit tests and localization parity tests.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to database schemas or sensor enum definitions (`ExtremaType.MAX_LINE_DISTANCE` remains untouched).
  * No modification to bipolar apex mathematical coordinates or clustering algorithms (established in ATT-6686).
  * No modification to elevation extrema pins (min/max altitude) or start/stop pins.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-SET-097` (Point-to-Point (A->B) Route Corridor Discrimination & Bipolar Product Apex) in `docs/requirements/req_settings_profiles.md`.
* **Historical Origin & Commit Trace**: Commit `23f048e4` (ATT-6686: Use bipolar product $d(\text{start}, P) \times d(P, \text{end})$ in `findApexFromPoints`).
* **Root Reason for Existing Formulation**: Previously, the UI marker string was coupled to the historical telemetry concept of maximum line distance (`max_line_distance`). With the bipolar product apex placing the pin at the route midpoint/corridor peak on point-to-point routes, the label became semantically decoupled from physical max displacement.
* **Preservation of Core Invariants**: Closed loops ($d(\text{start}, \text{end}) < 150\text{ m}$) remain labeled "weiteste Entfernung" / "furthest away" 100% preserving historical behavior. Underlying telemetry database tables and sensor types are strictly unchanged.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: Amends `REQ-SET-097` (adding `REQ-SET-097.5`: Differentiated Map Marker Labeling for Point-to-Point Routes).
* **Affected Architecture Elements**:
  - `app/src/main/res/values*/strings.xml` (all 9 language resource files)
  - `WorkoutClusterDatabaseManager.kt` (`WorkoutCluster.isPointToPoint`)
  - `WorkoutData.kt` (`WorkoutData.isPointToPoint`)
  - `WorkoutRepository.kt` (`getMapMarkerList`)
  - `WorkoutClustersViewModel.kt` (`ClusterPeakMarker` generation)
  - `WorkoutClusterHeatmapScreen.kt` (`apexLabel` and legend/toggle binding)
  - `MapScreenWithTrack.kt` (`apexLabel` binding)
  - `ManualClusterScreen.kt` (dynamic `apexLabel` based on start/end selection)
* **Verification Impact & Test Surface**:
  - `TranslationParityTest.kt`: Validates all 9 locale files define `route_apex`.
  - `WorkoutRepositoryTest.kt` / `WorkoutDataMapperLocationTest.kt`: Unit tests verifying marker title equals "Scheitelpunkt" (or localized "Apex") when $d \ge 150\text{ m}$ and "weiteste Entfernung" when $d < 150\text{ m}$.
  - `WorkoutClustersViewModelTest.kt` / `WorkoutClusterPointToPointApexTest.kt`: Verifies member and cluster markers reflect the differentiated label.
* **Risk Assessment & Invariants**:
  - Invariants: Marker icons (`R.drawable.ic_distance`), marker colors (`TTColor.ApexPoint`), and coordinate calculations are unchanged.
  - Risk: None. Purely semantic label differentiation conditioned on existing, proven $150\text{ m}$ geometric threshold.

---

## 6. Architectural Strategy & High-Level Solution

1. **Localization Definition**:
   Add `<string name="route_apex">Apex</string>` in base `values/strings.xml`, `<string name="route_apex">Scheitelpunkt</string>` in `values-de`, and corresponding professional translations in ES, FR, IT, JA, NL, PL, PT.
2. **Domain Model Property**:
   Add helper property:
   ```kotlin
   val isPointToPoint: Boolean
       get() = if (startLatLng != null && endLatLng != null) {
           WorkoutClusterEngine.distanceBetween(startLatLng, endLatLng) >= 150f
       } else false
   ```
   to `WorkoutData` and `WorkoutCluster` (using `startLat`, `startLng`, `endLat`, `endLng`).
3. **Marker Title Dynamic Resolution**:
   In `WorkoutRepository.getMapMarkerList`, resolve:
   `val apexTitle = if (workoutData.isPointToPoint) application.getString(R.string.route_apex) else application.getString(R.string.max_line_distance)`.
   Mirror this logic in `WorkoutClustersViewModel`, `WorkoutClusterHeatmapScreen`, `MapScreenWithTrack`, and `ManualClusterScreen`.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing closed-loop route marker labeling ("weiteste Entfernung" / "furthest away" preserved).
  2. Zero changes to database schema, extremum calculation, or GPS track polyline persistence.
  3. Strict 9-language localization parity with zero missing keys.
* **Risk Rating**: **LOW**
  - The change is strictly a UI presentation refinement with a well-defined pure function condition ($d \ge 150\text{ m}$) backed by existing unit test suites.
