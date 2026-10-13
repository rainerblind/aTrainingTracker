# Stage 1 Analysis: ATT-2585 - Show Saved Routes Containing the Segment in Segment Details

**Ticket**: [ATT-2585](https://atrainingtracker.atlassian.net/browse/ATT-2585)  
**Sub-task**: [ATT-2802](https://atrainingtracker.atlassian.net/browse/ATT-2802) (`[Analysis]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Branch**: `improvement/ATT-2585`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

When an athlete inspects a specific segment in `SegmentOnMapScreen` or `SegmentDetails` (e.g. to prepare for a target effort or personal record attempt), there is currently no indication of which saved routes in their personal library traverse that segment. 
Athletes must manually open multiple routes one by one in the Routes section to check whether a given course includes the segment.
Displaying the list of matching saved routes directly in the Segment Details screen establishes bidirectional route-segment navigation, enabling 1-tap route selection, course planning, and seamless exploration centered around key athletic milestones.

---

## 2. Root Cause Analysis (Forensic Investigation & Architectural Gap)

1. **Unidirectional Route-Segment Correlation**:
   - In `ATT-2583` (`REQ-UI-302`), `RouteSegmentMatcher` was introduced to calculate forward-direction spatial corridor intersections from a single route polyline against all candidate segments (`matchSegmentsPure(routePath, candidateSegments, routeSportType)`).
   - While `RouteOnMapScreen` leverages this to list segments along a route, the inverse association—evaluating candidate routes against a segment—was not exposed or modeled.
2. **Segment Details Layout Missing Telemetry / Analytics Content**:
   - `SegmentOnMapScreen.kt` hosts `MapDetailLayout`, but passes `analyticsContent = null`. The bottom pane of `MapDetailLayout` only renders the segment's elevation profile.
   - When no `analyticsContent` is supplied, `hasScrollableContent` remains `false`, leaving the lower area unused for contextual associations.
3. **Missing Cross-Navigation Affordance**:
   - Currently, selecting a segment in `StarredSegmentsScreen` or `MapScreenWithTrack` operates in isolation. There is no callback or intent flow to navigate from segment inspection directly to the associated route details in `RouteOnMapScreen`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Implement an inverted matching function `RouteSegmentMatcher.findRoutesContainingSegment(segment, candidateRoutes)` that cleanly reuses `RouteSegmentMatcher.matchSegmentsPure` to evaluate which saved routes traverse the segment within the established 25m spatial corridor and bearing tolerances.
  * Create a modular, theme-consistent UI section `SegmentRoutesSection.kt` (`ElevatedCard`, 12 dp rounded corners, Material 3 `surfaceVariant`) rendering:
    - Section header: `Routes with this segment (N)` (`@string/segment_routes_containing_title`).
    - Route cards: sport icon, route name, distance (formatted km/mi), elevation gain (+m), and start distance offset along route.
  * Integrate `SegmentRoutesSection` into `SegmentOnMapScreen`'s lower pane via `analyticsContent` in `MapDetailLayout`.
  * Support interactive navigation: tapping a route card navigates directly to `RouteOnMapScreen` for that route, pre-highlighting the active segment.
  * Ensure graceful hiding/empty state handling when no saved routes match.
  * 100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Editing or modifying saved route polylines or segment geometries.
  * Modifying cloud/Strava segment synchronization or route import formats.
  * Altering unrelated workout cluster or track analysis screens.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Net-new requirement only**: `REQ-UI-305` (*Inverted Route-Segment Spatial Association, Segment-Containing Routes Breakdown & Bidirectional Navigation*).
* **Test mapping**: `TST-UI-265` (*Inverted Route-Segment Association Engine, Segment Details Routes Breakdown & Cross-Navigation Verification*).
* **Reference Requirements**: Complements `REQ-UI-302` (*Route Starred Segments Spatial Overlap Matching, Chronological Route Breakdown & Map Synergy*) and `REQ-UI-303` (*Dedicated Route Matched Segment Detail View*).
* No existing requirements in `docs/requirements.md` are modified or weakened.

---

## 5. Architectural Strategy & High-Level Solution

### Component 1: `RouteSegmentMatcher.kt`
* Expose:
  ```kotlin
  @Immutable
  data class SegmentMatchedRoute(
      val route: RouteWithPath,
      val startDistanceMeters: Double,
      val endDistanceMeters: Double
  )
  
  suspend fun findRoutesContainingSegment(
      segment: SegmentWithPath,
      candidateRoutes: List<RouteWithPath>,
      dispatcher: CoroutineDispatcher = Dispatchers.Default
  ): List<SegmentMatchedRoute>
  
  fun findRoutesContainingSegmentPure(
      segment: SegmentWithPath,
      candidateRoutes: List<RouteWithPath>
  ): List<SegmentMatchedRoute>
  ```
* Pure synchronous matching reuses `matchSegmentsPure(route.path, listOf(segment), route.summary.bSportType)` across all candidate routes, ensuring 100% mathematical consistency with existing corridor, bearing, progression, and length consistency rules.

### Component 2: `SegmentRoutesSection.kt`
* Composable function:
  ```kotlin
  @Composable
  fun SegmentRoutesSection(
      matchingRoutes: List<SegmentMatchedRoute>,
      modifier: Modifier = Modifier,
      onRouteClick: ((Long) -> Unit)? = null
  )
  ```
* Adheres to `docs/design_guidelines.md` §5 (visual consistency with `RouteSegmentsBreakdownSection`):
  - Card shape: `RoundedCornerShape(12.dp)`.
  - Spacing: 8 dp grid, 12 dp padding.
  - Colors: `surfaceVariant`, `onSurface`, `onSurfaceVariant`.
  - Content: Sport icon (`bSportType.iconResId`), route name (`FontWeight.SemiBold`), total distance, elevation gain, and start offset.

### Component 3: `SegmentOnMapScreen.kt` & `StarredSegmentsScreen.kt`
* `SegmentOnMapScreen`:
  - Accepts `candidateRoutes: List<RouteWithPath> = emptyList()` and `onRouteClick: ((Long) -> Unit)? = null`.
  - Calculates `matchingRoutes` asynchronously via `produceState` on `Dispatchers.Default`.
  - Renders `SegmentRoutesSection` in `analyticsContent` of `MapDetailLayout` when `matchingRoutes.isNotEmpty()`.
* `StarredSegmentsScreen`:
  - Observes `allRoutes: StateFlow<List<RouteWithPath>>` from `SegmentListViewModel`.
  - Passes `candidateRoutes = allRoutes` into `SegmentOnMapScreen`.
  - Manages `inspectedRouteId` state: tapping a route displays `RouteOnMapScreen` with `BackHandler` returning to the segment.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regressions across existing route, climb, and segment test suites.
  2. Computation offloaded to background dispatcher (`Dispatchers.Default`) without UI frame drops.
  3. Parent ticket Human Decision Gate remains strictly preserved.
* **Risk Rating**: **LOW**. The spatial engine already exists and has comprehensive unit tests (`RouteSegmentMatcherTest`). Inverted matching simply iterates candidate routes through the pure matcher. UI integration is additive via existing `analyticsContent` slots.
