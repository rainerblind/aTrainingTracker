# Stage 1: Problem Domain & Root Cause Analysis - ATT-2774: Open Existing Segment Details Popup When Clicking a Segment in Route Segments Breakdown

**Ticket**: [ATT-2774](https://atrainingtracker.atlassian.net/browse/ATT-2774)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Requirement Mapping**: `REQ-UI-302` (Refined for `ATT-2774`)  
**Sprint**: `2026-41.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  
**Status**: Completed (Ready for Gate 1 Review)  

---

## 1. Problem Statement & User Impact

During the Sprint 2026-41.3 review on physical Pixel 10 hardware ([ATT-2583](https://atrainingtracker.atlassian.net/browse/ATT-2583)), the core geodesic spatial matching and breakdown presentation for starred segments along planned routes was verified and accepted.

However, a glaring interaction asymmetry was discovered between climbs and segments within `RouteOnMapScreen`:
1. **Asymmetric Tap Behavior**:
   - In `RouteClimbsBreakdownSection`, clicking on any climb card opens a dedicated `ClimbDetailSheet` (an `AppModalBottomSheet`), presenting the climb counter, key metrics HUD, focused map viewport with start/summit markers, and zoomed elevation profile.
   - In `RouteSegmentsBreakdownSection`, clicking on a segment card merely highlights the polyline and moves the elevation seeker line (`externalScrubDistance`). It **does not open any detail sheet or dialog**.
2. **User Expectation Violation**:
   - The athlete expects full functional and visual symmetry: tapping a segment card in the route breakdown should open a comprehensive segment details sheet displaying athlete PRs, distance along the route, full segment statistics, map geometry, and elevation profile.
   - Currently, an athlete viewing a route has no way to inspect detailed segment metrics (e.g. min/max elevation, detailed grade breakdown, Strava branding, start/finish geography) without exiting the route view entirely and searching for the segment in `StarredSegmentsScreen`.

This ticket ([ATT-2774](https://atrainingtracker.atlassian.net/browse/ATT-2774)) resolves this interaction gap by introducing `SegmentDetailSheet` and wiring it into `RouteOnMapScreen` in full parity with `ClimbDetailSheet`.

---

## 2. Forensic Investigation & Architectural Gap Analysis

### 2.1 Missing Detail Presentation in `RouteOnMapScreen.kt`
Inspection of `RouteOnMapScreen.kt` reveals how the two breakdown sections are wired:
```kotlin
val showClimbs = when {
    climbs.isNotEmpty() && matchedSegments.isNotEmpty() -> selectedBreakdownTab == RouteBreakdownTab.CLIMBS
    climbs.isNotEmpty() -> true
    else -> false
}

if (showClimbs) {
    RouteClimbsBreakdownSection(
        climbs = climbs,
        onClimbClick = { climb -> selectedClimbForDetail = climb }
    )
} else {
    RouteSegmentsBreakdownSection(
        segments = matchedSegments,
        onSegmentClick = { matched ->
            val segId = matched.segment.summary.stravaId
            highlightedSegmentId = if (highlightedSegmentId == segId) null else segId
            externalScrubDistance = matched.startDistanceMeters
        }
    )
}
```
And at the bottom of `RouteOnMapScreen.kt`:
```kotlin
selectedClimbForDetail?.let { climb ->
    val climbIndex = climbs.indexOf(climb).takeIf { it >= 0 }?.let { it + 1 }
    ClimbDetailSheet(
        climb = climb,
        routeIndex = climbIndex,
        totalRouteClimbs = climbs.size,
        bSportType = bSportType,
        onDismiss = { selectedClimbForDetail = null }
    )
}
```
There is **no** corresponding `selectedSegmentForDetail` state variable, and **no** segment detail sheet invocation.

### 2.2 Reusable Segment UI Building Blocks Already Present in Codebase
The application already possesses mature, high-quality segment presentation components:
1. `SegmentHeader.kt`:
   - Formats sport icon, segment name, climb category badge (`MetricBadge`), city, and athlete PR badge.
2. `SegmentDetails.kt`:
   - Renders canonical 3-row metric layout:
     - Row 1: Distance metric (`formatters.distance`) and `PoweredByStrava` branding badge.
     - Row 2: Average grade and maximum grade with vertical divider.
     - Row 3: Elevation gain, minimum altitude, and maximum altitude.
3. `ClimbDetailSheet.kt`:
   - Serves as the canonical architectural blueprint for route item detail bottom sheets: utilizes `AppModalBottomSheet`, header actions (counter badge + chips), key metrics card, focused `ATrainingTrackerMap`, and isolated elevation profile canvas.
4. `ElevationProfile.kt` & `ATrainingTrackerMap.kt`:
   - Existing map and profile components support embedding inside modal cards with tight LatLng bounding boxes and custom start/finish markers.

---

## 3. Chesterton's Fence & Requirement Archaeology

### 3.1 Historical Origin of `REQ-UI-302`
- **Origin**: Sprint 2026-41.3, Ticket `ATT-2583` (*Match and List Starred Segments along Routes in Route Details*).
- **Commit Trace**: Commit `6cb94285` (Feature implementation) and `1a64ebcf` (Living docs).
- **Root Reason for Existing Formulation**:
  - ATT-2583 was scoped primarily around the multi-tier geodesic spatial corridor analysis engine (`RouteSegmentMatcher.kt`), bearing alignment ($\Delta \theta \le 45^\circ$), route progression filtering, and tab coexistence with climbs.
  - In ATT-2583 Stage 3, card tap behavior was bounded to polyline highlighting and elevation profile scrubbing (`externalScrubDistance = matched.startDistanceMeters`) to prevent scope bloat.
  - In the Sprint 2026-41.3 review retro (`Sprint_Review_and_Retro_ATT-2660.md`, Action Item 3), the human reviewer specifically noted the missing popup and filed `ATT-2774` to complete interaction parity.

### 3.2 Preservation of Core Invariants
1. **Interactive Scrubbing Preserved**: Tapping a segment card must still highlight the segment polyline and scrub the parent route elevation profile to the segment's starting distance.
2. **Tab Coexistence Preserved**: The `RouteBreakdownTab` toggle between Climbs and Segments and its `rememberSaveable` state retention remain 100% untouched.
3. **ClimbDetailSheet Intact**: Zero modifications or regressions to `ClimbDetailSheet` or `RouteClimbsBreakdownSection`.
4. **Clean Modal Lifecycle**: `SegmentDetailSheet` must open cleanly as an `AppModalBottomSheet` above `RouteOnMapScreen` and dismiss cleanly via scrim tap, close button, or system Back press without disturbing parent route state.

---

## 4. Target Architectural Solution

### 4.1 Create `SegmentDetailSheet.kt` (`com.atrainingtracker.trainingtracker.ui.segments`)
A dedicated modal bottom sheet composable mirroring `ClimbDetailSheet`:
- **Container**: `AppModalBottomSheet`
  - `title`: `matchedSegment.segment.summary.name.ifBlank { stringResource(R.string.strava_segments_title) }`
  - `showCloseButton`: `true`
  - `onDismissRequest`: `onDismiss`
  - `headerActions`:
    - Counter badge: `Surface` displaying `stringResource(R.string.routes_segment_counter, routeIndex, totalRouteSegments)` (e.g., "Segment 1 von 3").
    - Category badge: `ClimbCategoryChip` if `climbCategory` is recognized.
    - PR badge: `MetricBadge(text = stringResource(R.string.routes_segment_pr, summary.prTime))` if `prTime` is present.
- **Card 1: Key Metrics HUD**:
  - ElevatedCard displaying start offset along the route (`R.string.routes_segment_start_at`) and city.
  - Reuses `SegmentDetails(summary = summary, showStravaLogo = true)`.
- **Card 2: Focused Map Viewport**:
  - ElevatedCard with `ATrainingTrackerMap` tightly zoomed to segment LatLngBounds, rendering start marker (`R.drawable.control_start`, `TTColor.StartPoint`), finish marker (`R.drawable.control_stop`, `TTColor.EndPoint`), and the segment polyline.
- **Card 3: Isolated Zoomed Elevation Profile**:
  - ElevatedCard displaying `ElevationProfile` for `matchedSegment.segment.path` with min and max altitude annotations.

### 4.2 Update `RouteOnMapScreen.kt`
- Introduce state: `var selectedSegmentForDetail by remember { mutableStateOf<MatchedRouteSegment?>(null) }`.
- In `RouteSegmentsBreakdownSection`:
  ```kotlin
  onSegmentClick = { matched ->
      selectedSegmentForDetail = matched
      highlightedSegmentId = matched.segment.summary.stravaId
      externalScrubDistance = matched.startDistanceMeters
  }
  ```
- Render `SegmentDetailSheet`:
  ```kotlin
  selectedSegmentForDetail?.let { matched ->
      val segIndex = matchedSegments.indexOf(matched).takeIf { it >= 0 }?.let { it + 1 }
      SegmentDetailSheet(
          matchedSegment = matched,
          routeIndex = segIndex,
          totalRouteSegments = matchedSegments.size,
          bSportType = bSportType,
          onDismiss = { selectedSegmentForDetail = null }
      )
  }
  ```

### 4.3 9-Language Localization Parity
Add string resource `routes_segment_counter` across all 9 supported locales:
- `values`: `Segment %1$d of %2$d`
- `values-de`: `Segment %1$d von %2$d`
- `values-es`: `Segmento %1$d de %2$d`
- `values-fr`: `Segment %1$d sur %2$d`
- `values-it`: `Segmento %1$d di %2$d`
- `values-ja`: `セグメント %1$d / %2$d`
- `values-nl`: `Segment %1$d van %2$d`
- `values-pl`: `Segment %1$d z %2$d`
- `values-pt`: `Segmento %1$d de %2$d`

---

## 5. Scope Bounding & Out-of-Scope Delineation

### In-Scope
- Authoring `SegmentDetailSheet.kt` in `ui/segments/`.
- Wiring `selectedSegmentForDetail` in `RouteOnMapScreen.kt`.
- Adding `routes_segment_counter` to strings.xml in 9 locales.
- Updating and expanding unit & contract tests (`RouteSegmentsBreakdownContractTest.kt`, `RouteSegmentsLocalizationTest.kt`).
- Verifying 100% clean-room test suite execution (`./gradlew testDebugUnitTest`).

### Out-of-Scope
- Modifying `RouteSegmentMatcher.kt` corridor tolerance or geodesic calculations.
- Modifying live segment tracking in `SensorGridScreen.kt` / `LiveSegmentSheet.kt`.
- Modifying Strava segment synchronization or database schema.

---

## 6. Traceability Matrix

| Lifecycle Stage | Artifact / Deliverable | Status |
| :--- | :--- | :--- |
| **Parent Ticket** | [ATT-2774](https://atrainingtracker.atlassian.net/browse/ATT-2774) | In Progress |
| **Stage 1 (Analysis)** | `docs/engineering/analysis/ATT-2774_analysis.md` | Completed (This document) |
| **Stage 1 Subtask** | [ATT-2792](https://atrainingtracker.atlassian.net/browse/ATT-2792) (`[Analysis]`) | In Review |
| **Requirement** | `REQ-UI-302` (Refined for `ATT-2774`) | Analysis Complete |
| **Test Spec** | `TST-UI-262` / `TST-UI-264` | Pending Stage 2 |
