# Stage 2: Requirement & Test Specification - ATT-7315: Differentiate map marker label for A->B routes

**Ticket**: [ATT-7315](https://rainerblind.atlassian.net/browse/ATT-7315)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-SET-097` (*Point-to-Point (A->B) Route Corridor Discrimination & Bipolar Product Apex*)  
**Test Spec ID**: `TST-SET-097`  
**Branch**: `subagent-Lane-3-Agent---ATT-7315-self-2da9c00a`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-SET-097 / REQ-SET-097.5)

### 1.1 Problem Statement & Rationale
On point-to-point (A->B) routes such as commutes, ATT-6686 introduced bipolar product optimization $d(\text{start}, P) \times d(P, \text{end})$, placing the apex marker at the geometric midpoint / corridor peak. Labeling this marker `R.string.max_line_distance` ("weiteste Entfernung" / "furthest away") is confusing and incorrect because the destination (Ende) is the furthest point from start. The system SHALL differentiate the marker label based on route topology.

### 1.2 Functional & Architectural Requirements
1. **Route Classification (`isPointToPoint`)**:
   - `WorkoutCluster.isPointToPoint` and `WorkoutData.isPointToPoint` SHALL return `true` if and only if $d(\text{start}, \text{end}) \ge 150\text{ m}$.
2. **Differentiated Map Marker Labeling (`REQ-SET-097.5`)**:
   - For all point-to-point routes ($d(\text{start}, \text{end}) \ge 150\text{ m}$), the map marker title for the apex coordinate (`ClusterMarkerType.DISTANCE` / `ic_distance`) SHALL be labeled `R.string.route_apex` ("Scheitelpunkt" in German, "Apex" across all 9 localized languages).
   - For closed-loop routes ($d(\text{start}, \text{end}) < 150\text{ m}$), the marker title SHALL retain `R.string.max_line_distance` ("weiteste Entfernung" in German, "furthest away" in English) representing the turnaround point.
   - This dynamic differentiation SHALL apply across `WorkoutRepository.getMapMarkerList`, `WorkoutClustersViewModel`, `WorkoutClusterHeatmapScreen`, and `MapScreenWithTrack`.
3. **9-Language Localization Parity**:
   - The string resource `route_apex` SHALL be defined across all 9 supported locales:
     - `values`: "Apex"
     - `values-de`: "Scheitelpunkt"
     - `values-es`: "Ápice"
     - `values-fr`: "Apex"
     - `values-it`: "Apice"
     - `values-ja`: "頂点"
     - `values-nl`: "Apex"
     - `values-pl`: "Wierzchołek"
     - `values-pt`: "Ápice"

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Point-to-Point Differentiated Label)**:
  * *Given* an A -> B point-to-point workout or cluster with $d(\text{start}, \text{end}) \ge 150\text{ m}$,
  * *When* map markers are constructed via `WorkoutRepository.getMapMarkerList` or rendered in cluster views,
  * *Then* the apex marker title SHALL be `route_apex` ("Scheitelpunkt" / "Apex").
* **Criterion 2 (Closed-Loop Preservation)**:
  * *Given* a closed-loop workout or cluster with $d(\text{start}, \text{end}) < 150\text{ m}$,
  * *When* map markers are constructed or rendered,
  * *Then* the apex marker title SHALL be `max_line_distance` ("weiteste Entfernung" / "furthest away").
* **Criterion 3 (Localization Parity)**:
  * *Given* all 9 locale `strings.xml` resource files,
  * *When* `TranslationParityTest` executes,
  * *Then* `route_apex` is present in all 9 files without missing keys or unescaped characters.

### 1.4 System Invariants
1. Closed loops ($d(\text{start}, \text{end}) < 150\text{ m}$) remain labeled "weiteste Entfernung" 100% preserving historical behavior.
2. Iconography (`R.drawable.ic_distance`), marker colors (`TTColor.ApexPoint`), and mathematical coordinates are strictly unchanged.
3. Zero database schema migrations; zero mutations to existing tables or columns.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-097` targeting `WorkoutClusterEngine.kt`, `WorkoutRepository.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, and `MapScreenWithTrack.kt`.
* **Historical Origin & Commit Trace**: Commit `23f048e4` (ATT-6686: Use bipolar product in `findApexFromPoints`) and ATT-5899.
* **Root Reason for Existing Formulation**: The apex marker title was historically hardcoded to `R.string.max_line_distance` ("weiteste Entfernung" / "furthest away") assuming loop routes. In ATT-6686, the bipolar product optimization shifted the apex coordinate on point-to-point routes to the midpoint / detour peak, making "weiteste Entfernung" factually incorrect since destination is furthest away.
* **Preservation of Core Invariants**: Closed loops ($d(\text{start}, \text{end}) < 150\text{ m}$) remain labeled "weiteste Entfernung" / "furthest away" 100% preserving historical behavior. Underlying telemetry database tables and sensor types are strictly unchanged.

---

## 2. Test Specification (TST-SET-097)

### Test Case 1: `testMapMarkerApexLabelDifferentiated_pointToPoint_vs_closedLoop` (`TST-SET-097.5a`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryMapMarkerApexTest.kt`
* **Preconditions**:
  - Point-to-point workout with start at `(48.7000, 9.0000)` and end at `(48.7200, 9.0200)` ($d \approx 2.7\text{ km} \ge 150\text{ m}$).
  - Closed-loop workout with start at `(48.7000, 9.0000)` and end at `(48.7005, 9.0005)` ($d \approx 67\text{ m} < 150\text{ m}$).
* **Action**: Invoke `getMapMarkerList(workoutData)` for each.
* **Expected Result**:
  - Point-to-point apex marker title equals `application.getString(R.string.route_apex)`.
  - Closed-loop apex marker title equals `application.getString(R.string.max_line_distance)`.

### Test Case 2: 9-Language Localization & Specifier Audit (`TST-SET-097.5b`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify `route_apex` exists in `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, and `values-pt`.
* **Expected Result**: 100% parity across all 9 locales.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-SET-097.5c`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.database.WorkoutCluster*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository*"`
* **Goal**: Verify 100% pass rate across all modified cluster, repository, and map marker test suites.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-097.5a` | Unit | `WorkoutRepository.getMapMarkerList`, `WorkoutData.isPointToPoint` | `REQ-SET-097.5` | Specified |
| `TST-SET-097.5b` | Localization | `TranslationParityTest` | `REQ-SET-097.5`, `REQ-UI-106` | Specified |
| `TST-SET-097.5c` | Regression (Tier 1) | Cluster & Aftermath test suites | `REQ-SET-097`, `REQ-PRO-001` | Specified |
