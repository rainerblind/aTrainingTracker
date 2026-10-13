# Stage 1 Analysis: ATT-2583 - Match and List Starred Segments along Routes in Route Details

**Ticket**: [ATT-2583](https://atrainingtracker.atlassian.net/browse/ATT-2583)  
**Sub-task**: [ATT-2760](https://atrainingtracker.atlassian.net/browse/ATT-2760) (`[Analysis]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2583`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

When preparing or inspecting a route in the Route Details screen (`RouteOnMapScreen.kt` invoked from `RoutesScreen.kt` and `MapScreenWithTrack.kt`), the application provides comprehensive visual and analytical breakdown for route geometry, summary metrics, waypoints, and recognized climbs (`RouteClimbsBreakdownSection` under `REQ-UI-274`, `REQ-UI-298`, and `REQ-UI-300`).

However, for Strava and local segments, the user experience is disjointed and incomplete:
1. **Unfiltered Background Polylines**: In `RoutesScreen.kt`, all saved/starred segments matching the route's sport type are forwarded as `backgroundPaths` and rendered indiscriminately as faint background lines across the entire map, regardless of whether they have any spatial relation to the route.
2. **Absence of Route-Segment Association**: Athletes cannot see which specific segments actually lie along the planned route, where they begin or end along the itinerary, or what athletic pacing challenge they present.
3. **No Athlete Capability Context**: While `SegmentSummary` tracks the athlete's Personal Record (PR) time (`prTime` / `prTime_raw`) under `REQ-EXP-010`, this critical pacing context is inaccessible while inspecting routes.
4. **Lack of Chronological Itinerary**: Unlike climbs, which are surfaced with "Start bei km X.X", length, and average grade in `RouteClimbsBreakdownSection`, segments lack any structured representation in the route breakdown below the elevation profile.

### Expected Behavior
1. **Spatial Overlap Matching Engine**: Intelligently evaluate starred segments in `SegmentsRepository` against the route polyline (`List<PathPoint>`), identifying segments that lie along the route corridor within a 25-meter tolerance, travel in the route's forward direction ($\Delta \theta \le 45^\circ$, positive vector dot product), and compute the exact kilometer marker where each segment starts and ends.
2. **Route Segments Breakdown Section**: Introduce a dedicated `RouteSegmentsBreakdownSection` below the elevation profile displaying matched segments ordered chronologically by starting distance along the route, with segment name, category badge, start kilometer (e.g. "km 14.2"), length, average grade, and athlete's PR time.
3. **Climbs vs. Segments Synergy (Garmin & Strava Benchmark)**: Coexistence below the elevation profile:
   - When both climbs and segments exist on a route: provide clean Material 3 sub-tabs / filter chips (`[ Anstiege (3) ]  [ Segmente (4) ]`) with state persisted via `rememberSaveable`, allowing the athlete to toggle between the Climbs breakdown and the Segments breakdown.
   - When only climbs exist: render `RouteClimbsBreakdownSection`.
   - When only segments exist: render `RouteSegmentsBreakdownSection`.
   - When neither exists: cleanly collapse `analyticsContent` without leaving empty gaps or breaking layout.
4. **Map & Elevation Profile Interactive Synergy**: Tapping a segment card highlights the segment polyline on the upper map and sets the elevation profile scrub focus to the segment's starting distance along the route.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Architectural Gap in Route Details Data Flow
Forensic examination of `RoutesScreen.kt` (lines 126–143) and `RouteOnMapScreen.kt` (lines 132–139) reveals the root cause:
```kotlin
// RoutesScreen.kt:
val backgroundPaths = remember(selectedRoute, allSegments) {
    allSegments
        .filter { it.summary.bSportType == selectedRoute.summary.bSportType }
        .map { it.toMapSegment(showStartAndFinishText = false) }
}
RouteOnMapScreen(
    route = selectedRoute.toMapRoute(),
    routeSummary = selectedRoute.summary,
    backgroundPaths = backgroundPaths,
    ...
)
```
- `RoutesScreen` passes all sport-matching segments as passive `backgroundPaths` without checking geometric overlap against `selectedRoute.path`.
- In `RouteOnMapScreen.kt`:
```kotlin
analyticsContent = if (climbs.isNotEmpty()) {
    {
        RouteClimbsBreakdownSection(
            climbs = climbs,
            onClimbClick = { climb -> selectedClimbForDetail = climb }
        )
    }
} else null,
```
- `RouteOnMapScreen` checks only `climbs.isNotEmpty()`. It is unaware of matched segments and lacks any segment breakdown section.

### 2.2 Segment Data & Repository Readiness
Investigation confirms that all necessary domain data already exists in `SegmentsRepository` and `RoutesViewModel`:
- `SegmentsRepository.allSegmentsWithPath: StateFlow<List<SegmentWithPath>>` is already exposed and continuously collected in `RoutesViewModel.segments`.
- `SegmentWithPath` contains `summary: SegmentSummary` (with `name`, `stravaId`, `bSportType`, `distance_raw`, `averageGrade_raw`, `climbCategory`, `prTime`, `prTime_raw`) and `path: List<PathPoint>` (with geodesic coordinates and elevations).
- What is missing is a high-performance geodesic matching engine that computes spatial overlap, travel direction alignment, and anchors segments to route kilometer distances without blocking the UI thread.

---

## 3. User Scope Grounding (ATT-1250)

### 3.1 In-Scope Goals
1. **Spatial Route-Segment Matching Engine (`RouteSegmentMatcher.kt`)**:
   - Evaluate candidate segments against a route path (`List<PathPoint>`) and sport type (`BSportType`).
   - Offload computation to `Dispatchers.Default` (via `withContext(Dispatchers.Default)` or `produceState`) to guarantee zero UI thread blocking during map loading.
   - Comprehensive multi-tier geometric match criteria:
     - Sport type equality (or either is `UNKNOWN`).
     - Non-empty path points ($\ge 2$ points).
     - **Corridor Proximity**: Segment start and end coordinates lie within corridor tolerance ($d \le 25\text{ m}$) of the route polyline.
     - **Directional Ambiguity Elimination & Vector Alignment**: Travel bearing alignment at start and finish must satisfy $\Delta \theta \le 45^\circ$ AND vector dot product $> 0$ ($\cos(\Delta \theta) > 0$), preventing false positives on opposing traffic lanes or reverse-direction segments.
     - **Forward Traversal Progression**: The route distance at segment end ($D_{\text{end}}$) must strictly exceed route distance at segment start ($D_{\text{start}}$), ensuring forward travel along the route.
     - **Path Length Consistency**: The route span $(D_{\text{end}} - D_{\text{start}})$ must approximate the segment's intrinsic distance within tolerance: $|(D_{\text{end}} - D_{\text{start}}) - \text{segment.distance}| \le \max(100.0, \text{segment.distance} \times 0.25)$, eliminating horseshoe false positives.
     - **Intermediate Point Verification**: For segments with $\ge 3$ points, sample midpoints are validated against the route path between start and end indices.
   - Output: `MatchedRouteSegment` containing the segment reference, `startDistanceMeters`, and `endDistanceMeters`, sorted by `startDistanceMeters` ascending.
2. **Dedicated Segment Breakdown Composable (`RouteSegmentsBreakdownSection.kt`)**:
   - Render matched segment cards with:
     - Header: Sport icon, segment name, category badge (if present), and athlete PR badge (e.g. "PR 02:45").
     - Metrics row: Start kilometer along route (e.g. "Start bei km 14.2"), segment length (km/mi), and average gradient (%).
3. **Climbs vs. Segments Synergy in `RouteOnMapScreen.kt`**:
   - If both climbs and matched segments are present: render Material 3 `FilterChip` toggle row (`[ Anstiege (N) ]  [ Segmente (M) ]`) with tab state managed via `rememberSaveable` to ensure persistence across device rotations.
   - If only climbs: render `RouteClimbsBreakdownSection`.
   - If only segments: render `RouteSegmentsBreakdownSection`.
   - If neither: `analyticsContent` collapses cleanly to `null`.
4. **Map & Elevation Profile Interactive Synergy**:
   - Tapping a matched segment card highlights its polyline prominently on the upper map and sets the elevation profile scrub focus to the segment's starting distance along the route.
5. **100% 9-Language Localization Parity**:
   - All newly introduced string resources (`routes_segments_section_title`, `routes_segments_tab_title`, `routes_segment_start_at`, `routes_segment_pr`) localized across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 3.2 Out-of-Scope Non-Goals (Scope Bounding)
- In-ride live segment tracking (managed separately by `LiveSegmentsRepository.kt` and `LiveSegmentSheet.kt`).
- Modifying SQLite database schemas (`Routes.db` or `Segments.db`).
- Modifying Strava API sync workers or upload pipelines.
- Editing or creating new segments.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-302`), extending `REQ-UI-274` (*Route Climbs Visual Prominence & Dedicated Breakdown*) and `REQ-EXP-010` (*Strava Segment Personal Best Update & Presentation*).
* **Historical Origin & Commit Trace**: Ticket `ATT-2583`, Sprint `2026-41.3`, target release `V4.9.39`, Epic `ATT-2582` (*Segments: Live Tracking, Exploration & Route Integration*).
* **Root Reason for Existing Formulation**: Previously, routes and segments were separate data silos in the UI. When Climbs were added (`ATT-1281` / `ATT-2388`), routes gained an ascent breakdown. Segments remained relegated to full-database background lines without correlation to route progression.
* **Preservation of Core Invariants**:
  - `MapDetailLayout` dragging, split-pane math, and collapsing header measurement remain 100% untouched.
  - `RouteClimbsBreakdownSection` and `ClimbDetailSheet` functionality remain 100% intact.
  - Single-tap route selection toggle and GPX export invariants remain intact.
  - Zero regression across clean-room test suite (`./gradlew testDebugUnitTest`).

---

## 5. Architectural Strategy & High-Level Solution

```text
┌────────────────────────────────────────────────────────────────────────┐
│                          RoutesScreen.kt                               │
│  • Obtains selectedRoute: RouteWithPath                                │
│  • Obtains allSegments: List<SegmentWithPath>                          │
│  • Forwards both to RouteOnMapScreen                                   │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        RouteOnMapScreen.kt                             │
│  • produceState(initialValue = emptyList(), route, segments):          │
│      withContext(Dispatchers.Default) {                                │
│          RouteSegmentMatcher.matchSegments(route, segments)            │
│      }                                                                 │
│                                                                        │
│  • var selectedTab by rememberSaveable { mutableStateOf(CLIMBS) }      │
│                                                                        │
│  • analyticsContent:                                                   │
│      if (climbs.isNotEmpty() && matchedSegments.isNotEmpty())          │
│          FilterChip row: [ Climbs (N) ] [ Segments (M) ]               │
│          -> toggles between ClimbsBreakdown and SegmentsBreakdown      │
│      else if (climbs.isNotEmpty())                                     │
│          RouteClimbsBreakdownSection(...)                              │
│      else if (matchedSegments.isNotEmpty())                            │
│          RouteSegmentsBreakdownSection(...)                            │
│      else null                                                         │
│                                                                        │
│  • mapContent:                                                         │
│      Highlights selected matched segment polyline on map               │
└────────────────────────────────────────────────────────────────────────┘
```

### Data Model
```kotlin
data class MatchedRouteSegment(
    val segment: SegmentWithPath,
    val startDistanceMeters: Double,
    val endDistanceMeters: Double
)
```

---

## 6. User Interface Consistency (Rule 23)

- **Card Styling**: `ElevatedCard` using `MaterialTheme.colorScheme.surfaceVariant`, 12.dp rounded corners, and 12.dp internal padding, exactly matching `RouteClimbsBreakdownSection`.
- **Typography & Icons**: Uses `LocalMetricFormatter.current` for localized distance formatting. PR badge uses `Icons.Default.EmojiEvents` / `ic_trophy` or distinct pill styling.
- **Tab Chips**: Material 3 `FilterChip` components with localized labels, item counts, and `rememberSaveable` state.
