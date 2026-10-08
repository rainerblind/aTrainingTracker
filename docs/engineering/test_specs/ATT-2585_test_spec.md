# Stage 2: Requirement & Test Specification - ATT-2585: Show Saved Routes Containing the Segment in Segment Details

**Ticket**: [ATT-2585](https://atrainingtracker.atlassian.net/browse/ATT-2585)  
**Sub-task**: [ATT-2803](https://atrainingtracker.atlassian.net/browse/ATT-2803) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-305` (*Inverted Route-Segment Spatial Association, Segment-Containing Routes Breakdown & Bidirectional Navigation*)  
**Test Spec ID**: `TST-UI-265`  
**Branch**: `improvement/ATT-2585`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (`REQ-UI-305`)

### 1.1 Problem Statement & Rationale
When an athlete inspects a specific segment in `SegmentOnMapScreen` or `SegmentDetails` (e.g. to prepare for a target effort or personal record attempt), there is currently no indication of which saved routes in their personal library traverse that segment. 
Athletes must manually open multiple routes one by one in the Routes section to check whether a given course includes the segment.
Displaying the list of matching saved routes directly in the Segment Details screen establishes bidirectional route-segment navigation, enabling 1-tap route selection, course planning, and seamless exploration centered around key athletic milestones.

### 1.2 Functional & Architectural Requirements
1. **Inverted Route-Segment Matching Engine (`RouteSegmentMatcher.kt`)**:
   - The system SHALL expose `findRoutesContainingSegment(segment: SegmentWithPath, candidateRoutes: List<RouteWithPath>)` evaluating candidate routes on `Dispatchers.Default`.
   - The inverted matching algorithm SHALL leverage `matchSegmentsPure(route.path, listOf(segment), route.summary.bSportType)` to maintain 100% mathematical consistency with existing corridor proximity ($d \le 25\text{ m}$), directional heading delta ($\Delta \theta \le 45^\circ$, dot product $> 0$), forward progression ($D_{end} > D_{start}$), span consistency, and midpoint verification rules.
   - Output: `List<SegmentMatchedRoute>` sorted alphabetically by route name.
2. **Segment Routes Breakdown UI (`SegmentRoutesSection.kt`)**:
   - Below the segment elevation profile in `SegmentOnMapScreen`, `SegmentRoutesSection` SHALL render inside `analyticsContent` of `MapDetailLayout` when one or more matching routes exist.
   - Section title SHALL display `segment_routes_containing_title` with the matching count (e.g. "Routes with this segment (2)").
   - Each matching route SHALL be presented in an `ElevatedCard` (12 dp rounded corners, `surfaceVariant`) displaying:
     - Sport profile icon (`route.summary.bSportType.iconResId`).
     - Route name (`route.summary.name`).
     - Key route telemetry: total distance (`formatters.formatDistance`), total elevation gain (`formatters.formatElevationGain`), and segment start offset along route (`routes_segment_start_at`).
3. **Interactive Navigation & Segment Pre-Highlighting**:
   - Tapping a route card SHALL invoke `onRouteClick(route.summary.id)`.
   - In `StarredSegmentsScreen`, tapping a route SHALL navigate to `RouteOnMapScreen` inspecting that route, with the active segment pre-selected in `highlightedSegmentId` and back navigation returning seamlessly to `SegmentOnMapScreen`.
   - In `MapScreenWithTrack`, tapping a route card SHALL switch active inspection to `selectedRouteId`.
4. **Graceful Empty State Handling**:
   - When zero saved routes traverse the segment, `analyticsContent` SHALL evaluate to `null` or remain cleanly hidden without layout gaps or elevation profile distortion.
5. **100% 9-Language Localization Parity**:
   - String resource `segment_routes_containing_title` SHALL be externalized and translated across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
6. **Preservation of System Invariants**:
   - `MapDetailLayout` split-pane math, route selection toggle, and 100% unit test pass rate MUST NOT be broken.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1**:
  * *Given* an athlete with multiple saved routes, two of which pass through a starred segment in the forward direction,
  * *When* inspecting the segment in `SegmentOnMapScreen`,
  * *Then* `SegmentRoutesSection` SHALL list exactly those two routes with their names, lengths, elevation gains, and start offsets.
* **Criterion 2**:
  * *Given* a displayed route card in `SegmentRoutesSection`,
  * *When* the user taps the card,
  * *Then* the app SHALL navigate to `RouteOnMapScreen` inspecting that route with the segment pre-highlighted.
* **Criterion 3**:
  * *Given* a segment not traversed by any saved routes in the athlete's library,
  * *When* viewed in `SegmentOnMapScreen`,
  * *Then* the section SHALL remain hidden without disrupting the elevation profile or map.
* **Criterion 4**:
  * *Given* all 9 supported application locales,
  * *When* evaluating `segment_routes_containing_title`,
  * *Then* the string SHALL exist and declare valid format specifiers across all 9 resources.

### 1.4 System Invariants
1. Zero regressions across existing route, climb, and segment test suites.
2. Background computation on `Dispatchers.Default` ensuring zero frame drops or ANRs.
3. Subtasks transition directly to `Erledigt` via `freigabe` upon passing Gate audit.
4. Parent ticket completion remains reserved for the human user in `Final Review (Human)`.

---

## 2. Test Specification (`TST-UI-265`)

### Test Case 1: `testFindRoutesContainingSegmentPure_matchingAndRejection` (`TST-UI-265.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteSegmentMatcherTest.kt`
* **Preconditions**: Synthetic segment path and candidate routes (matching forward route, reverse direction route, distant non-overlapping route).
* **Action**: Execute `RouteSegmentMatcher.findRoutesContainingSegmentPure(segment, candidateRoutes)`.
* **Expected Result**: Only the forward overlapping route is returned with valid `startDistanceMeters` and `endDistanceMeters`.

### Test Case 2: `testSegmentRoutesSectionContract` (`TST-UI-265.2`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentRoutesSectionContractTest.kt`
* **Preconditions**: Inspect `SegmentRoutesSection.kt` and `SegmentOnMapScreen.kt` source files.
* **Action**: Verify `SegmentRoutesSection` renders `ElevatedCard` with sport icon, route name, distance, and elevation gain; verify `onRouteClick` callback wiring; verify `SegmentOnMapScreen.kt` integrates `SegmentRoutesSection` in `MapDetailLayout` `analyticsContent`.

### Test Case 3: `testSegmentRoutesLocalizationParity` (`TST-UI-265.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentRoutesLocalizationTest.kt`
* **Goal**: Verify string presence and matching `%d` format token for `segment_routes_containing_title` across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-265.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-265.1` | Unit | `RouteSegmentMatcher.findRoutesContainingSegmentPure` | `REQ-UI-305` | Specified |
| `TST-UI-265.2` | Contract | `SegmentRoutesSection`, `SegmentOnMapScreen` | `REQ-UI-305` | Specified |
| `TST-UI-265.3` | Localization | `SegmentRoutesLocalizationTest` | `REQ-UI-305`, `REQ-UI-106` | Specified |
| `TST-UI-265.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
