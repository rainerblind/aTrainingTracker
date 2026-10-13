# Stage 2: Requirement & Test Specification (ATT-2583)

## 1. Traceability & Scope
- **Ticket**: [ATT-2583](https://atrainingtracker.atlassian.net/browse/ATT-2583) — *[Verbesserung] Match and List Starred Segments along Routes in Route Details*
- **Subtask**: [ATT-2762](https://atrainingtracker.atlassian.net/browse/ATT-2762) (`[Req & Test Spec]`)
- **Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)
- **Target Release**: `V4.9.39`
- **Active Sprint**: `2026-41.3`
- **Branch**: `feature/ATT-2583`
- **Author**: AI Agent 1 (Implementer)
- **Requirement**: `REQ-UI-302` (*Route Starred Segments Spatial Overlap Matching, Chronological Route Breakdown & Map Synergy*)
- **Test Specification**: `TST-UI-262` (*Route Starred Segments Spatial Overlap Matching, Chronological Route Breakdown & Map Synergy Verification*)

---

## 2. Formal Requirement Specification (`REQ-UI-302`)

1. **Spatial Overlap Matching Engine (`RouteSegmentMatcher.kt`)**:
   - The system SHALL evaluate candidate segments from `SegmentsRepository.allSegmentsWithPath` against the active route polyline (`List<PathPoint>`) and sport type (`BSportType`).
   - Computation SHALL be offloaded to `Dispatchers.Default` (via `withContext(Dispatchers.Default)` or `produceState`) to guarantee zero UI thread blocking during screen composition and map loading.
   - Candidate matching criteria:
     a. *Sport Compatibility*: Segment sport type must match route sport type (`segment.summary.bSportType == route.bSportType` or either is `BSportType.UNKNOWN`).
     b. *Corridor Proximity*: Segment start coordinate and end coordinate must lie within corridor distance tolerance ($d \le 25\text{ m}$) of the route polyline.
     c. *Directional Ambiguity Elimination & Vector Alignment*: Travel bearing at start and end points must align with route heading within $\Delta \theta \le 45^\circ$ with positive vector dot product ($\cos(\Delta \theta) > 0$).
     d. *Forward Progression*: Route distance at segment finish ($D_{\text{end}}$) must strictly exceed route distance at segment start ($D_{\text{start}}$).
     e. *Path Length Consistency*: Route span distance $(D_{\text{end}} - D_{\text{start}})$ must approximate intrinsic segment distance within tolerance ($|(D_{\text{end}} - D_{\text{start}}) - \text{segment.distance}| \le \max(100.0, \text{segment.distance} \times 0.25)$).
     f. *Intermediate Point Sampling*: For multi-point segments ($\ge 3$ points), sample midpoint coordinates are validated against route points between start and end indices.
   - Output: `List<MatchedRouteSegment>` sorted in ascending order of `startDistanceMeters` along the route.

2. **Route Segments Breakdown UI (`RouteSegmentsBreakdownSection.kt`)**:
   - Below the route elevation profile, `RouteSegmentsBreakdownSection` SHALL render an `ElevatedCard` (12.dp rounded corners, `surfaceVariant`) for each matched segment displaying:
     - Header: Sport icon, segment name, category badge (if present), and athlete PR badge/text (e.g. "PR 02:45" or localized string).
     - Metrics row: Start kilometer along route (e.g. "Start bei km 14.2"), segment length (km/mi), and average gradient (%).

3. **Climbs vs. Segments Coexistence Synergy (`RouteOnMapScreen.kt`)**:
   - If a route contains both recognized climbs (`climbs.isNotEmpty()`) and matched segments (`matchedSegments.isNotEmpty()`):
     - `RouteOnMapScreen` SHALL render a Material 3 `FilterChip` toggle row directly above the breakdown content (`[ Anstiege (N) ]  [ Segmente (M) ]`).
     - Active tab selection state SHALL be persisted via `rememberSaveable` to survive device rotations.
   - If only climbs are present: the system SHALL directly render `RouteClimbsBreakdownSection`.
   - If only segments are present: the system SHALL directly render `RouteSegmentsBreakdownSection`.
   - If neither are present: `analyticsContent` SHALL cleanly evaluate to `null` without rendering empty cards or layout gaps.

4. **Map & Elevation Profile Interactive Synergy**:
   - Tapping a matched segment card SHALL highlight its polyline prominently on the map in `RouteOnMapScreen` and focus/scrub the elevation profile to the segment's starting distance along the route.

5. **100% 9-Language Localization Parity**:
   - String resources `routes_segments_section_title`, `routes_segments_tab_title`, `routes_segment_start_at`, `routes_segment_pr` SHALL be externalized and translated across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

6. **Preservation of System Invariants**:
   - `MapDetailLayout` dragging, split-pane math, and collapsing header measurement remain 100% intact.
   - `RouteClimbsBreakdownSection` and `ClimbDetailSheet` functionality remain 100% intact.
   - Single-tap route selection toggle and GPX export invariants remain intact.
   - Zero regression across clean-room test suite (`./gradlew testDebugUnitTest`).

---

## 3. Test Specification (`TST-UI-262`)

### Test Case 1: Geodesic Spatial Corridor & Directional Traversal Matching Unit Tests
- **Test Class**: `RouteSegmentMatcherTest.kt`
- **Given**: A route with known path points and a set of candidate segments:
  - Segment A: Perfect corridor alignment along km 5 to km 8, forward direction, matching sport type.
  - Segment B: Reverse direction traversal along the same path corridor.
  - Segment C: Perpendicular crossing corridor.
  - Segment D: Opposing sport type (e.g. RUN vs. BIKE).
  - Segment E: Start point within corridor but finish point diverging $> 500\text{ m}$.
  - Segment F: Horseshoe segment with close start/finish but diverging middle path.
- **When**: Invoking `RouteSegmentMatcher.matchSegments(routePath, candidateSegments, sportType)`.
- **Then**:
  - Segment A MUST be matched with accurate `startDistanceMeters` and `endDistanceMeters`.
  - Segments B, C, D, E, and F MUST be excluded.
  - Matched list MUST be sorted in ascending order of `startDistanceMeters`.

### Test Case 2: UI Breakdown & Material 3 Tab Coexistence Contract Tests
- **Test Class**: `RouteSegmentsBreakdownContractTest.kt`
- **Given**: Composable signatures of `RouteSegmentsBreakdownSection` and `RouteOnMapScreen`.
- **When**: Inspecting composable parameters, cards, and state flow.
- **Then**:
  - `RouteSegmentsBreakdownSection` accepts `segments: List<MatchedRouteSegment>`, `onSegmentClick: ((MatchedRouteSegment) -> Unit)?`.
  - FilterChip toggle row renders when both climbs and segments are non-empty.
  - Active tab selection persists using `rememberSaveable`.
  - Elevation profile scrub distance and map segment highlight are triggered on segment card click.

### Test Case 3: 9-Language Localization Parity
- **Test Class**: `RouteSegmentsLocalizationTest.kt`
- **Given**: All 9 supported `strings.xml` resource files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
- **When**: Checking keys `routes_segments_section_title`, `routes_segments_tab_title`, `routes_segment_start_at`, `routes_segment_pr`.
- **Then**:
  - All keys MUST exist in all 9 locales.
  - No empty or untranslated placeholder values.
  - Format tokens (`%1$s`, etc.) MUST match exactly across locales.
  - Zero literal XML entity breakages (e.g. no `&#10;`).

### Test Case 4: Full Clean-Room Regression Suite
- **Command**: `./gradlew testDebugUnitTest`
- **Expected Outcome**: 100% test pass rate with zero regressions.

---

## 4. Traceability Matrix

| Requirement Clause | Verification Procedure / Test Case | Target Class / Module |
|:---|:---|:---|
| `REQ-UI-302.1` Spatial Corridor & Directional Matching | `RouteSegmentMatcherTest.kt` | `RouteSegmentMatcher.kt` |
| `REQ-UI-302.2` Chronological Breakdown UI | `RouteSegmentsBreakdownContractTest.kt` | `RouteSegmentsBreakdownSection.kt` |
| `REQ-UI-302.3` Climbs vs. Segments Coexistence & Tabs | `RouteSegmentsBreakdownContractTest.kt` | `RouteOnMapScreen.kt` |
| `REQ-UI-302.4` Map & Elevation Profile Highlighting | `RouteSegmentsBreakdownContractTest.kt` | `RouteOnMapScreen.kt` |
| `REQ-UI-302.5` 9-Language Localization Parity | `RouteSegmentsLocalizationTest.kt` | `res/values*/strings.xml` |
| `REQ-UI-302.6` System Invariants & Regression | Full Clean-Room Suite (`testDebugUnitTest`) | Entire application |

---

## 5. Acceptance Criteria (Given-When-Then)

- **Scenario 1: Overlapping forward segments along route**:
  - *Given* a planned route passing through two starred Strava segments in forward direction and one in reverse direction,
  - *When* viewing the route in `RouteOnMapScreen`,
  - *Then* exactly the two forward segments SHALL be matched and listed in ascending order of their start distance along the route.

- **Scenario 2: Segment Breakdown Card presentation**:
  - *Given* a matched segment card in `RouteSegmentsBreakdownSection`,
  - *When* rendered on screen,
  - *Then* the card SHALL display the segment name, sport icon, category badge (if any), PR badge/time, start kilometer ("Start bei km X.X"), length, and average grade.

- **Scenario 3: Climbs and Segments Coexistence**:
  - *Given* a route with 3 recognized climbs and 2 matched segments,
  - *When* inspecting the route breakdown below the elevation profile,
  - *Then* a Material 3 FilterChip row (`[ Anstiege (3) ]  [ Segmente (2) ]`) SHALL be displayed, allowing toggling between Climbs and Segments with state preserved across device rotations.

- **Scenario 4: Route with no matching segments**:
  - *Given* a route with no overlapping starred segments,
  - *When* viewed in `RouteOnMapScreen`,
  - *Then* the segments section SHALL remain hidden or display an informative empty state without breaking the layout or elevation profile.
