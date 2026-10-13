# Stage 3: Implementation Plan - ATT-2585: Show Saved Routes Containing the Segment in Segment Details

**Ticket**: [ATT-2585](https://atrainingtracker.atlassian.net/browse/ATT-2585)  
**Sub-task**: [ATT-2804](https://atrainingtracker.atlassian.net/browse/ATT-2804) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-305` (*Inverted Route-Segment Spatial Association, Segment-Containing Routes Breakdown & Bidirectional Navigation*)  
**Test Mapping**: `TST-UI-265` (*Inverted Route-Segment Association Engine, Segment Details Routes Breakdown & Cross-Navigation Verification*)  
**Branch**: `improvement/ATT-2585`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Description & Background

When athletes inspect a starred or local segment in `SegmentOnMapScreen` or `SegmentDetails`, there is currently no indication of which saved routes in their local library pass through that segment. Athletes must manually inspect multiple routes to determine course feasibility. 
By calculating an inverted spatial corridor match (`RouteSegmentMatcher.findRoutesContainingSegment`) and rendering a structured routes breakdown below the elevation profile in `SegmentOnMapScreen`, athletes gain immediate 1-tap route selection and bidirectional route-segment navigation centered around target athletic milestones.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-305` (*Inverted Route-Segment Spatial Association, Segment-Containing Routes Breakdown & Bidirectional Navigation*)
* **Test Mapping**: `TST-UI-265` (*Inverted Route-Segment Association Engine, Segment Details Routes Breakdown & Cross-Navigation Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing route matching (`RouteSegmentMatcher.matchSegmentsPure`), climb breakdowns (`RouteClimbsBreakdownSection`), and segment detail views (`SegmentDetailSheet`) continue to pass all tests cleanly.
2. **Thread Safety & Dispatcher Affinity**: Route matching operations execute on `Dispatchers.Default` via `produceState` / `withContext` to guarantee zero main-thread jank or frame drops during Compose recomposition.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Inverted Matching Engine (`RouteSegmentMatcher.kt`)
* Model:
  ```kotlin
  @Immutable
  data class SegmentMatchedRoute(
      val route: RouteWithPath,
      val startDistanceMeters: Double,
      val endDistanceMeters: Double
  )
  ```
* Functions:
  - `findRoutesContainingSegment(segment: SegmentWithPath, candidateRoutes: List<RouteWithPath>, dispatcher: CoroutineDispatcher = Dispatchers.Default): List<SegmentMatchedRoute>`
  - `findRoutesContainingSegmentPure(segment: SegmentWithPath, candidateRoutes: List<RouteWithPath>): List<SegmentMatchedRoute>`
* Implementation iterates over `candidateRoutes` and calls `matchSegmentsPure(route.path, listOf(segment), route.summary.bSportType)` ensuring 100% mathematical consistency with existing corridor (25m), heading ($\le 45^\circ$), progression, and span consistency checks.

### Component 2: Routes Section UI Component (`SegmentRoutesSection.kt`)
* Package: `com.atrainingtracker.trainingtracker.ui.segments`
* Signature:
  ```kotlin
  @Composable
  fun SegmentRoutesSection(
      matchingRoutes: List<SegmentMatchedRoute>,
      modifier: Modifier = Modifier,
      onRouteClick: ((Long) -> Unit)? = null
  )
  ```
* Structure:
  - Header: `Text(text = stringResource(R.string.segment_routes_containing_title, matchingRoutes.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)`
  - Cards: `ElevatedCard` (`RoundedCornerShape(12.dp)`, `surfaceVariant`) rendering:
    - Top row: Sport icon (`bSportType.iconResId`), route name (`titleMedium`, `FontWeight.SemiBold`, `TextOverflow.Ellipsis`).
    - Metrics row: Total distance (`formatters.formatDistance`), total elevation gain (`formatters.formatElevationGain`), start offset along route (`routes_segment_start_at`).

### Component 3: Integration into `SegmentOnMapScreen.kt` & `StarredSegmentsScreen.kt`
* `SegmentOnMapScreen`:
  - Receives `candidateRoutes: List<RouteWithPath> = emptyList()` and `onRouteClick: ((Long) -> Unit)? = null`.
  - Computes `val matchingRoutes by produceState(...) { value = RouteSegmentMatcher.findRoutesContainingSegment(...) }`.
  - Injects `SegmentRoutesSection` into `MapDetailLayout` via `analyticsContent` when `matchingRoutes.isNotEmpty()`.
* `SegmentListViewModel`:
  - Exposes `val allRoutes: StateFlow<List<RouteWithPath>> = routesRepository.allRoutes`.
* `StarredSegmentsScreen`:
  - Collects `val allRoutes by viewModel.allRoutes.collectAsStateWithLifecycle()`.
  - Maintains `var inspectedRouteId by rememberSaveable { mutableStateOf<Long?>(null) }`.
  - Passes `candidateRoutes = allRoutes` and `onRouteClick = { inspectedRouteId = it }` to `SegmentOnMapScreen`.
  - When `inspectedRouteId != null`, renders `RouteOnMapScreen` inspecting the route with `BackHandler` returning to `SegmentOnMapScreen`.

### UI Consistency (Rule 23)
* **Reference screen / component**: `RouteSegmentsBreakdownSection.kt` and `RouteClimbsBreakdownSection.kt`.
* **Reused components**: `ElevatedCard`, `LocalMetricFormatter`, `bSportType.iconResId`.
* **Theme tokens**: 
  - Shapes: `RoundedCornerShape(12.dp)` for cards, matching existing breakdown sections.
  - Spacing: 8 dp grid, 12 dp padding inside cards, 16 dp horizontal screen padding.
  - Colors: `MaterialTheme.colorScheme.surfaceVariant`, `onSurface`, `onSurfaceVariant`.
  - Typography: `MaterialTheme.typography.titleMedium` for section header and route names, `bodyMedium` for metrics.
* **New one-off styles & justification**: None. Reuses established breakdown card styling.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: String Resources (9-Language Parity)
* Add `segment_routes_containing_title` across all 9 locale directories:
  - `values/strings.xml`: `Routes with this segment (%d)`
  - `values-de/strings.xml`: `Routen mit diesem Segment (%d)`
  - `values-es/strings.xml`: `Rutas con este segmento (%d)`
  - `values-fr/strings.xml`: `Itinéraires avec ce segment (%d)`
  - `values-it/strings.xml`: `Percorsi con questo segmento (%d)`
  - `values-ja/strings.xml`: `このセグメントを含むルート (%d)`
  - `values-nl/strings.xml`: `Routes met dit segment (%d)`
  - `values-pl/strings.xml`: `Trasy z tym segmentem (%d)`
  - `values-pt/strings.xml`: `Rotas com este segmento (%d)`

### Step 2: Implement Inverted Matcher in `RouteSegmentMatcher.kt`
* Define `SegmentMatchedRoute`.
* Implement `findRoutesContainingSegment` and `findRoutesContainingSegmentPure`.

### Step 3: Implement `SegmentRoutesSection.kt`
* Create composable in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentRoutesSection.kt`.

### Step 4: Expose `allRoutes` in `SegmentListViewModel.kt`
* Add `val allRoutes: StateFlow<List<RouteWithPath>> = routesRepository.allRoutes`.

### Step 5: Wire into `SegmentOnMapScreen.kt` and `StarredSegmentsScreen.kt`
* Integrate `matchingRoutes` into `analyticsContent` in `SegmentOnMapScreen.kt`.
* Wire `allRoutes` and `inspectedRouteId` route inspection into `StarredSegmentsScreen.kt`.

### Step 6: Unit and Contract Tests
* Update `RouteSegmentMatcherTest.kt` with inverted matching tests.
* Create `SegmentRoutesSectionContractTest.kt` asserting UI structure, metrics, and callbacks.
* Create `SegmentRoutesLocalizationTest.kt` verifying 9-language parity for `segment_routes_containing_title`.

### Step 7: Targeted Test Verification
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.RouteSegmentMatcherTest" --tests "com.atrainingtracker.trainingtracker.ui.segments.SegmentRoutes*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests in Stage 4, followed by full clean-room suite (`./gradlew clean testDebugUnitTest`) and walkthrough in Stage 5.
* **Rollback**: Clean branch isolation on `improvement/ATT-2585` allows immediate discard via `git checkout sprint/2026-41.4 && git branch -D improvement/ATT-2585` if issues arise.
