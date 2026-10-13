# Stage 3 Implementation Plan: ATT-2583

## 1. Ticket & Architectural Context
- **Ticket**: [ATT-2583](https://atrainingtracker.atlassian.net/browse/ATT-2583) — *[Verbesserung] Match and List Starred Segments along Routes in Route Details*
- **Subtask**: [ATT-2765](https://atrainingtracker.atlassian.net/browse/ATT-2765) (`[Impl-Plan]`)
- **Requirement**: `REQ-UI-302`
- **Test Specification**: `TST-UI-262`
- **Target Branch**: `feature/ATT-2583`

---

## 2. SWE.2 Architecture & Component Design
This feature enriches route exploration by detecting which starred Strava segments overlap with the selected route, providing athletes with immediate awareness of upcoming segment PR attempts directly in the route breakdown.

### Component Decomposition
1. **`RouteSegmentMatcher.kt` (`com.atrainingtracker.trainingtracker.routes`)**:
   - `MatchedRouteSegment` data model:
     - `segment: SegmentWithPath`
     - `startDistanceMeters: Double` (distance along route polyline where segment starts)
     - `endDistanceMeters: Double` (distance along route polyline where segment ends)
     - `startPathIndex: Int`
     - `endPathIndex: Int`
   - Geodesic Matching Algorithm:
     - Offloaded to background thread (`Dispatchers.Default`).
     - Rejects mismatched sport types (`segment.summary.bSportType != routeSportType` when neither is `UNKNOWN`).
     - Corridor proximity tolerance: 25.0 meters (`GeoUtils.haversineDistanceMeters`).
     - Bearing alignment tolerance: $\le 45.0^\circ$ difference between segment heading and route heading.
     - Strict progression check: $D_{\text{end}} > D_{\text{start}}$.
     - Distance consistency check: route span distance matches segment intrinsic length within $\max(100.0, \text{segment.distance} \times 0.25)$.
     - Midpoint corridor validation for segments with $\ge 3$ coordinates.
     - Results sorted strictly ascending by `startDistanceMeters`.

2. **`RouteSegmentsBreakdownSection.kt` (`com.atrainingtracker.trainingtracker.ui.routes`)**:
   - Composable `RouteSegmentsBreakdownSection(segments: List<MatchedRouteSegment>, modifier: Modifier = Modifier, onSegmentClick: ((MatchedRouteSegment) -> Unit)? = null)`.
   - Displays section title (`stringResource(R.string.routes_segments_section_title, segments.size)`).
   - Renders each matched segment as an `ElevatedCard` (12.dp corner radius, `surfaceVariant`):
     - Sport icon (`Image(painterResource(segment.summary.bSportType.iconResId))`).
     - Segment title (`segment.summary.name`).
     - Climb category chip (`ClimbCategoryChip`) if category is classified.
     - Athlete PR badge (`routes_segment_pr`, e.g. "PR 02:45") if `segment.summary.prTime` is present.
     - Metrics row: Start distance along route (`routes_segment_start_at`), segment length (`distance`), and average gradient (`averageGrade`).

3. **`RouteOnMapScreen.kt` Updates**:
   - Parameter additions: `allSegments: List<SegmentWithPath> = emptyList()`.
   - Asynchronous matching state:
     - `val matchedSegments by produceState<List<MatchedRouteSegment>>(initialValue = emptyList(), route?.path, allSegments, bSportType)` on `Dispatchers.Default`.
   - Dynamic Tab Coexistence Toggle:
     - When both `climbs.isNotEmpty()` AND `matchedSegments.isNotEmpty()`: render Material 3 `FilterChip` row (`[ Anstiege (N) ]  [ Segmente (M) ]`) with state persisted via `rememberSaveable`.
     - When only climbs exist: render `RouteClimbsBreakdownSection`.
     - When only segments exist: render `RouteSegmentsBreakdownSection`.
     - When neither exists: render nothing (`analyticsContent = null`).
   - Interactive synergy:
     - Clicking a segment card scrubs the elevation profile to `startDistanceMeters` and highlights the segment polyline.

4. **`RoutesScreen.kt` Updates**:
   - Pass existing `allSegments` flow collection into `RouteOnMapScreen(..., allSegments = allSegments)`.

---

## 3. Atomic Implementation Steps

### Step 1: 9-Language Localization Parity
Add 4 string resources to all 9 `strings.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`):
- `routes_segments_section_title`: `%d Segmente` / `Segments (%d)`
- `routes_segments_tab_title`: `Segmente (%d)` / `Segments (%d)`
- `routes_segment_start_at`: `Start bei %s` / `Start at %s`
- `routes_segment_pr`: `PR %s`

### Step 2: Implement Geodesic Overlap Engine
Create `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteSegmentMatcher.kt`:
- Define `MatchedRouteSegment`.
- Implement `matchSegments(routePath: List<PathPoint>, candidateSegments: List<SegmentWithPath>, routeSportType: BSportType): List<MatchedRouteSegment>`.
- Use `GeoUtils.haversineDistanceMeters` and `GeoUtils.calculateInitialBearing`.

### Step 3: Implement `RouteSegmentsBreakdownSection.kt`
Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSegmentsBreakdownSection.kt`:
- Composable displaying list of `MatchedRouteSegment` cards.
- Clean M3 styling, sport icon, category chip, PR badge, and metrics row.

### Step 4: Integrate into `RouteOnMapScreen.kt` and `RoutesScreen.kt`
- Add `allSegments: List<SegmentWithPath> = emptyList()` to `RouteOnMapScreen`.
- Add `produceState` for matched segments.
- Add Material 3 `FilterChip` row for toggling between Climbs and Segments when both exist.
- Wire `allSegments` from `RoutesScreen.kt`.

### Step 5: Unit & Contract Tests
- `RouteSegmentMatcherTest.kt`: Exhaustive verification of spatial proximity, reverse direction filtering, length consistency, sport matching, and sorting.
- `RouteSegmentsBreakdownContractTest.kt`: Composable structure, parameter contracts, and tab toggle logic.
- `RouteSegmentsLocalizationTest.kt`: 100% 9-language localization audit.

### Step 6: Full Clean-Room Regression Suite
- Execute `./gradlew testDebugUnitTest` ensuring 100% pass rate.

---

## 4. UI Consistency & Theming (Rule 23)
- **Reference Component**: Follows the layout, elevation, and card geometry of `RouteClimbsBreakdownSection` in `RouteOnMapScreen.kt`.
- **Containers & Surfaces**: `ElevatedCard` with `RoundedCornerShape(12.dp)` and `MaterialTheme.colorScheme.surfaceVariant`.
- **Text & Badges**:
  - Title: `MaterialTheme.typography.titleMedium` (`onSurface`).
  - Segment Name: `MaterialTheme.typography.bodyLarge` (`onSurface`).
  - Metrics: `MaterialTheme.typography.bodySmall` (`onSurfaceVariant`).
  - Category Badge: Reuses `ClimbCategoryChip` from `LiveClimbSheet.kt`.
- **Toggles**: Standard Material 3 `FilterChip` with `FilterChipDefaults.filterChipColors()`.
- **Touch Targets**: Minimum 48.dp click targets.

---

## 5. Invariants & Risk Mitigation
- **Thread Safety**: Matching computation strictly occurs on `Dispatchers.Default` to prevent UI frame drops.
- **Null Safety**: Graceful fallback when segments or route coordinates are empty.
- **Preservation of Existing Behaviors**:
  - `MapDetailLayout` sliding and snapping logic untouched.
  - Climb cards and `ClimbDetailSheet` flow untouched.
  - Single-tap route selection untouched.
