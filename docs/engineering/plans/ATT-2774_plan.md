# Stage 3: Implementation Plan - ATT-2774: Open Existing Segment Details Popup When Clicking a Segment in Route Segments Breakdown

**Ticket**: [ATT-2774](https://atrainingtracker.atlassian.net/browse/ATT-2774)  
**Sub-task**: [ATT-2794](https://atrainingtracker.atlassian.net/browse/ATT-2794) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-303` (*Dedicated Route Matched Segment Detail View with Focused Map and Zoomed Isolated Elevation Profile*)  
**Test Mapping**: `TST-UI-263` (*Route Matched Segment Detail Bottom Sheet, Focused Map, Isolated Elevation Profile & Interaction Parity Verification*)  
**Branch**: `improvement/ATT-2774`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  
**Status**: Ready for Gate 3 Review  

---

## 1. Problem Description & Background

In the route visualization workflow (`RouteOnMapScreen.kt`), athletes can explore route elevation, climbs, and matched segments.

While tapping on a climb item in `RouteClimbsBreakdownSection` opens a rich modal detail bottom sheet (`ClimbDetailSheet.kt`) with isolated map geometry, elevation profile, and telemetry metrics, clicking a matched segment in `RouteSegmentsBreakdownSection` currently only toggles the polyline highlight and moves the elevation seeker line (`externalScrubDistance`). It does not present any detail sheet or dialog.

This ticket ([ATT-2774](https://atrainingtracker.atlassian.net/browse/ATT-2774)) eliminates this interaction gap by creating `SegmentDetailSheet.kt` and wiring it into `RouteOnMapScreen.kt` to achieve complete visual, functional, and architectural parity with `ClimbDetailSheet.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-303` (*Dedicated Route Matched Segment Detail View with Focused Map and Zoomed Isolated Elevation Profile*)
* **Test Mapping**: `TST-UI-263` (*Route Matched Segment Detail Bottom Sheet, Focused Map, Isolated Elevation Profile & Interaction Parity Verification*):
  - `TST-UI-263.1`: Segment Tap Interaction & Bottom Sheet State Contract Test (`RouteSegmentsBreakdownContractTest.kt`)
  - `TST-UI-263.2`: SegmentDetailSheet Structural & Viewport Contract Tests (`SegmentDetailSheetContractTest.kt`)
  - `TST-UI-263.3`: 9-Language Localization Parity Audit (`RouteSegmentsLocalizationTest.kt`)
  - `TST-UI-263.4`: Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Visual & Interaction Symmetry (Rule 23)**: `SegmentDetailSheet` mirrors `ClimbDetailSheet` structure, design tokens, and interaction patterns (modal bottom sheet, header with counter and badges, key metrics HUD, focused map viewport, and isolated elevation profile).
2. **Scrubbing & Highlight Preservation**: Tapping a segment card continues to set `highlightedSegmentId` and scrub `externalScrubDistance = matched.startDistanceMeters` on the parent route map.
3. **Coexistence Integrity**: Climbs breakdown, `ClimbDetailSheet`, tab toggle (`RouteBreakdownTab`), and `rememberSaveable` state retention remain 100% intact.
4. **Clean Dismissal**: Dismissing `SegmentDetailSheet` via close button, drag handle gesture, or system Back press clears `selectedSegmentForDetail = null` without losing route state, map bounds, or breakdown scroll position.
5. **Human Gate Invariance (Rule 2)**: Subtask `ATT-2794` transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`. Parent ticket `ATT-2774` terminal state is `Final Review (Human)`.
6. **Zero Regression**: 100% clean-room test suite pass rate across all 438+ unit test classes.

---

## 4. UI Consistency (Rule 23)

In compliance with `docs/design_guidelines.md` (Section 5 *Visual Consistency Baseline*):
- **Closest Reference Component**: `ClimbDetailSheet.kt` (`com.atrainingtracker.trainingtracker.ui.climbs`).
- **Reused Components**:
  - `AppModalBottomSheet` (`com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet`)
  - `SegmentDetails` (`com.atrainingtracker.trainingtracker.ui.segments.SegmentDetails`)
  - `ClimbCategoryChip` (`com.atrainingtracker.trainingtracker.ui.climbs.ClimbCategoryChip`)
  - `MetricBadge` (`com.atrainingtracker.trainingtracker.ui.components.MetricBadge`)
  - `ATrainingTrackerMap` (`com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap`)
  - `ElevationProfile` (`com.atrainingtracker.trainingtracker.ui.map.ElevationProfile`)
- **Theme Tokens & Styling**:
  - Container cards: `ElevatedCard` / `Card` with `shape = RoundedCornerShape(12.dp)` and `CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)`.
  - Content padding: `16.dp` horizontal on root sheet column, `12.dp` inside cards.
  - Spacing scale: `4.dp`, `8.dp`, `12.dp`.
  - Markers: `R.drawable.control_start` (`TTColor.StartPoint`), `R.drawable.control_stop` (`TTColor.EndPoint`).
- **One-Off Styles**: Zero one-off styles or ad-hoc colors. All colors and typography strictly adhere to `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and `TTColor`.

---

## 5. Architectural Decomposition (SWE.2)

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        RouteOnMapScreen.kt                             │
│                                                                        │
│  var selectedSegmentForDetail by remember { mutableStateOf(...) }      │
│                                                                        │
│  RouteSegmentsBreakdownSection                                         │
│    └── onSegmentClick = { matched ->                                   │
│            selectedSegmentForDetail = matched                          │
│            highlightedSegmentId = matched.segment.summary.stravaId     │
│            externalScrubDistance = matched.startDistanceMeters        │
│        }                                                               │
│                                                                        │
│  selectedSegmentForDetail?.let { matched ->                            │
│      SegmentDetailSheet(                                               │
│          matchedSegment = matched,                                     │
│          routeIndex = matchedSegments.indexOf(matched) + 1,             │
│          totalRouteSegments = matchedSegments.size,                    │
│          bSportType = bSportType,                                      │
│          onDismiss = { selectedSegmentForDetail = null }               │
│      )                                                                 │
│  }                                                                     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        SegmentDetailSheet.kt                           │
│                                                                        │
│  AppModalBottomSheet                                                   │
│    ├── Header: Title, CloseButton, Counter, CategoryChip, PR Badge     │
│    └── Body (Column):                                                  │
│        ├── Card 1: Key Metrics HUD                                     │
│        │     - Start at: routes_segment_start_at + City                │
│        │     - HorizontalDivider                                       │
│        │     - SegmentDetails(summary = summary, showStravaLogo = true)│
│        ├── Card 2: Focused Map Viewport                                │
│        │     - ATrainingTrackerMap (MapZoomFocus.EXPLICIT_BOUNDS)      │
│        │     - Start Marker (StartPoint) & Stop Marker (EndPoint)      │
│        └── Card 3: Isolated Zoomed Elevation Profile                   │
│              - ElevationProfile(pathPoints = segment.path)             │
│              - Altitude range indicators (minAlt → maxAlt)             │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Step-by-Step Implementation Sequence

### Step 1: 9-Language Localization Strings
* **Files**: `app/src/main/res/values*/strings.xml` (all 9 locales).
* **Action**: Add `routes_segment_counter` with exact format specifiers `%1$d` and `%2$d`:
  - `values/strings.xml`: `Segment %1$d of %2$d`
  - `values-de/strings.xml`: `Segment %1$d von %2$d`
  - `values-es/strings.xml`: `Segmento %1$d de %2$d`
  - `values-fr/strings.xml`: `Segment %1$d sur %2$d`
  - `values-it/strings.xml`: `Segmento %1$d di %2$d`
  - `values-ja/strings.xml`: `セグメント %1$d / %2$d`
  - `values-nl/strings.xml`: `Segment %1$d van %2$d`
  - `values-pl/strings.xml`: `Segment %1$d z %2$d`
  - `values-pt/strings.xml`: `Segmento %1$d de %2$d`

### Step 2: Implement `SegmentDetailSheet.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentDetailSheet.kt`
* **Action**:
  - Implement `calculateSegmentBounds(matchedSegment: MatchedRouteSegment): LatLngBounds?`.
  - Implement `SegmentDetailSheet(...)` using `AppModalBottomSheet`.
  - Implement `SegmentDetailMetricsCard` embedding `routes_segment_start_at`, `summary.city`, and `SegmentDetails`.
  - Implement `SegmentDetailMapCard` embedding `ATrainingTrackerMap` with `MapTrack`, start marker, finish marker, and `MapZoomFocus.EXPLICIT_BOUNDS`.
  - Implement `SegmentDetailElevationProfileCard` embedding `ElevationProfile` with altitude range headers.

### Step 3: Wire `selectedSegmentForDetail` in `RouteOnMapScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt`
* **Action**:
  - Add state `var selectedSegmentForDetail by remember { mutableStateOf<MatchedRouteSegment?>(null) }`.
  - Wire `RouteSegmentsBreakdownSection` `onSegmentClick` to assign `selectedSegmentForDetail = matched`.
  - Add invocation of `SegmentDetailSheet` when `selectedSegmentForDetail != null`.

### Step 4: Author Contract Tests
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentDetailSheetContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSegmentsBreakdownContractTest.kt`
* **Action**:
  - Assert that `SegmentDetailSheet` composable is public and accepts required parameters.
  - Assert that `RouteOnMapScreen.kt` manages `selectedSegmentForDetail` and invokes `SegmentDetailSheet`.

### Step 5: Author Localization Parity Test
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSegmentsLocalizationTest.kt`
* **Action**:
  - Update test to assert that `routes_segment_counter` exists and has `%1$d` and `%2$d` specifiers across all 9 locales using `LocalizationTestCache`.

### Step 6: Full Clean-Room Regression Verification
* **Action**: Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 7. Verification & Quality Gates

| Gate | Verification Target | Command / Artifact | Criteria |
| :--- | :--- | :--- | :--- |
| **Gate 3** | Implementation Plan Review | `tools/review_agent.py audit ATT-2794` | RECOMMEND PASS |
| **Pre-Stage 4** | Programmatic Gate Check | `python3 tools/jira_util.py check-gate ATT-2794` | Exit Code 0 |
| **Stage 4** | Unit & Contract Tests | `./gradlew testDebugUnitTest --tests "...SegmentDetailSheetContractTest"` | 100% Pass |
| **Stage 4** | Localization Parity | `./gradlew testDebugUnitTest --tests "...RouteSegmentsLocalizationTest"` | 100% Pass |
| **Stage 5** | Clean-Room Full Suite | `./gradlew testDebugUnitTest` | 100% Pass (0 failures) |
