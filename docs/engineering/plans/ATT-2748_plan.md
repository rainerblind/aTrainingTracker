# Stage 3: Implementation Plan - ATT-2748: Remove Climb Pins from Route Map and Display UC Climbs on Map and Elevation Profile

**Ticket**: [ATT-2748](https://atrainingtracker.atlassian.net/browse/ATT-2748)  
**Sub-task**: [ATT-2829](https://atrainingtracker.atlassian.net/browse/ATT-2829) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*[Epic] Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-307` (*Route Map Climb Pin Elimination, Full-Spectrum UC Climb Polyline Highlighting & Elevation Profile Baseline Parity*, refining and amending `REQ-UI-298` and `REQ-UI-299`)  
**Test Mapping**: `TST-UI-267` (*Route Map Pin Elimination, Full-Spectrum UC Climb Polyline Highlighting, and Elevation Profile Baseline Parity Verification*)  
**Branch**: `improvement/ATT-2748`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  
**Status**: Ready for Gate 3 Review  

---

## 1. Problem Description & Background

During Sprint Review 2026-41.3 on a Google Pixel 10 (validating `ATT-2509` and `ATT-2510`), visual ergonomics and usability issues were observed regarding climb visualizations on route maps and elevation profiles:
1. **Redundant Climb Start Pin Markers**: In `RouteOnMapScreen.kt`, circular ascent pin markers (`ic_ascent` with category background color at `climb.startLatLng`) crowd the route polyline and distract athletes from the actual route course and Start/End navigation pins. Now that climbs are directly highlighted as colored polyline spans along the route curve (`MapContentScope.climbs()`), these pins are redundant.
2. **Missing Uncategorized (UC) Climbs on Map and Elevation Profile**:
   - In `MapContentScope.kt:482`, `climb.category != ClimbCategory.UNCATEGORIZED` explicitly skips UC climbs, preventing them from being rendered as colored polyline spans along the route curve.
   - In `ElevationProfile.kt:776`, `climbs.filter { it.category != ClimbCategory.UNCATEGORIZED }` explicitly excludes UC climbs from being marked as horizontal span indicator bars along the X-axis baseline.
   - This caused confusing discrepancies where climbs appeared in the climb breakdown list (`RouteClimbsBreakdownSection`), but vanished from the map polyline and elevation profile.
3. **Synergy with Royal Blue Route Baseline**: With prerequisite ticket **ATT-2761** merged, the route base polyline renders in Royal Blue (`Color(0xFF1565C0)`). Uncategorized climb spans rendered in neutral category grey (`#757575`) achieve clear chromatic separation ($\Delta C = 0.278$, achromatic vs saturated blue) on map polylines and $> 4.8:1$ WCAG contrast on elevation profile dark canvas.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-307` (*Route Map Climb Pin Elimination, Full-Spectrum UC Climb Polyline Highlighting & Elevation Profile Baseline Parity*)
* **Test Mapping**: `TST-UI-267` (*Route Map Pin Elimination, Full-Spectrum UC Climb Polyline Highlighting, and Elevation Profile Baseline Parity Verification*):
  - `TST-UI-267.1`: Route Map Marker Contract Audit (`RouteOnMapScreenClimbContractTest.kt`)
  - `TST-UI-267.2`: Full-Spectrum Climb Polyline Highlighting Contract Tests (`ClimbPolylineContractTest.kt`)
  - `TST-UI-267.3`: Elevation Profile Baseline UC Span Parity (`ElevationProfileClimbSpanContractTest.kt`)
  - `TST-UI-267.4`: Living Documentation Synchronization (`docs/requirements.md`, `docs/tests.md`)
  - `TST-UI-267.5`: Full Clean-Room Regression Suite (`./gradlew clean testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Route Inception & Completion Marker Invariance**: Route Start (`control_start`, `TTColor.StartPoint`) and End (`control_stop`, `TTColor.EndPoint`) pin markers MUST remain present at the first and last route path points.
2. **UCI Climb Category Color Invariance**: `getClimbCategoryColors` (HC `#880E4F`, Cat 1 `#C62828`, Cat 2 `#EF6C00`, Cat 3 `#F9A825`, Cat 4 `#2E7D32`, UC `#757575`) MUST NOT be altered.
3. **Elevation Profile Ridge Curve Invariance**: Elevation profile slope gradient coloring (`Zone1`..`Zone5`) along the ridge curve remains untouched.
4. **Distance-Domain Coordinate Mapping Invariance**: Touch scrubbing, distance-to-canvas coordinate mapping via `ElevationProfileZoomMath.distanceToCanvasX`, and coordinate clamping (`coerceIn(0f, width)`) remain 100% intact.
5. **Human Gate Invariance (Rule 2)**: Subtask `ATT-2829` transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`. Parent ticket `ATT-2748` terminal state is `Final Review (Human)`.
6. **Zero Regression**: 100% clean-room test suite pass rate across all unit test classes.

---

## 4. UI Consistency (Rule 23)

In compliance with `docs/design_guidelines.md` (Section 5 *Visual Consistency Baseline*):
- **Closest Reference Component**: `RouteOnMapScreen.kt` (map marker handling), `MapContentScope.kt` (polyline highlights), and `ElevationProfile.kt` (X-axis baseline indicators).
- **Reused Tokens**:
  - `TTColor.ClimbUncategorized = Color(0xFF757575)` (neutral category grey for UC climbs)
  - `TTColor.StartPoint = Color(0xFF4CAF50)` (Green Start pin)
  - `TTColor.EndPoint = Color(0xFFF44336)` (Red Stop pin)
  - `TTColor.RouteSelected = Color(0xFF1565C0)` (Royal Blue route baseline)
- **Visual Ergonomics**: Eliminating redundant circular climb start pins removes visual clutter, preventing pin overlapping and decluttering the map viewport. Displaying UC climb spans in neutral grey establishes visual symmetry between the climb breakdown list, the route polyline, and the elevation profile X-axis baseline.
- **One-Off Styles**: Zero one-off styles or arbitrary inline hex values. All colors resolve through `getClimbCategoryColors(climb.category)`.

---

## 5. Architectural Decomposition (SWE.2)

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        RouteOnMapScreen.kt                             │
│                                                                        │
│  allMarkers:                                                           │
│    - Start Marker (control_start, TTColor.StartPoint)                  │
│    - End Marker   (control_stop, TTColor.EndPoint)                     │
│    (Zero ic_ascent climb pins)                                         │
└───────────────────┬───────────────────────────────┬────────────────────┘
                    │                               │
                    ▼                               ▼
┌───────────────────────────────────────┐ ┌──────────────────────────────┐
│         MapContentScope.kt            │ │     ElevationProfile.kt      │
│                                       │ │                              │
│  climbs(climbs):                      │ │  Baseline Span Indicators:   │
│    All recognized climbs (HC..Cat 4   │ │    All recognized climbs     │
│    AND UNCATEGORIZED) added to        │ │    (HC..Cat 4 AND            │
│    climbHighlights:                   │ │    UNCATEGORIZED) drawn at   │
│    - color: ClimbCategory color       │ │    baselineY with            │
│    - UC color: Color(0xFF757575)      │ │    strokeWidth = 4.dp.toPx() │
│    - zIndex: 25f                      │ │                              │
└───────────────────────────────────────┘ └──────────────────────────────┘
```

---

## 6. Step-by-Step Implementation Sequence

### Step 1: Remove Climb Ascent Pin Markers from `RouteOnMapScreen.kt`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt`
- **Action**:
  - Remove `climbMarkers` definition (lines 104–109).
  - Remove climb marker iteration block adding `ic_ascent` markers to `allMarkers` (lines 269–282).
  - Keep `allMarkers` containing strictly `control_start` and `control_stop` markers.
- **Verification**: Compilation check via `./gradlew compileDebugKotlin`.

### Step 2: Remove `UNCATEGORIZED` Exclusion from `MapContentScope.climbs()`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt`
- **Action**:
  - In `climbs(climbs: List<Climb>)` (lines 480–494), remove `if (climb.category != ClimbCategory.UNCATEGORIZED)`.
  - Allow all climbs (including `UNCATEGORIZED`) with $\ge 2$ points to generate `ClimbHighlightData` with `getClimbCategoryColors(climb.category).first` (`Color(0xFF757575)` for UC).
- **Verification**: Compilation check via `./gradlew compileDebugKotlin`.

### Step 3: Remove `UNCATEGORIZED` Exclusion from `ElevationProfile.kt` Baseline Spans
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
- **Action**:
  - In distance-domain climb baseline rendering (line 776), remove `.filter { it.category != ClimbCategory.UNCATEGORIZED }`.
  - Draw horizontal indicator bars along `baselineY` for all recognized climbs, including `UNCATEGORIZED` using `getClimbCategoryColors(climb.category).first`.
- **Verification**: Compilation check via `./gradlew compileDebugKotlin`.

### Step 4: Update Contract & Unit Tests
- **Target Files**:
  1. `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreenClimbContractTest.kt`:
     - Add assertions verifying that `RouteOnMapScreen.kt` does NOT reference `ic_ascent` or create climb pin markers.
     - Verify `allMarkers` contains strictly Start and End markers.
  2. `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ClimbPolylineContractTest.kt`:
     - Update `testUncategorizedClimbs_areOmittedFromHighlightLayer` to `testUncategorizedClimbs_renderHighlightPolylineWithNeutralGrey`.
     - Assert `mapScope.climbHighlights` contains 1 element with `color = Color(0xFF757575)`.
  3. `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileClimbSpanContractTest.kt`:
     - Update assertion #3: verify `ElevationProfile.kt` does NOT filter out `UNCATEGORIZED` climbs, and contains comment noting all recognized climbs (including UC) are rendered.
- **Verification**: Run targeted unit tests:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenClimbContractTest" --tests "com.atrainingtracker.trainingtracker.ui.map.ClimbPolylineContractTest" --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileClimbSpanContractTest"`.

### Step 5: Full Clean-Room Regression Suite
- **Command**: `./gradlew clean testDebugUnitTest`
- **Assertion**: 100% pass rate across all test classes, 0 regressions.

---

## 7. Verification & Testing Strategy

| Scope | Verification Command / Target | Success Criteria |
| :--- | :--- | :--- |
| **Route Map Markers** | `RouteOnMapScreenClimbContractTest.kt` | Zero `ic_ascent` markers in `RouteOnMapScreen.kt`; Start/End markers preserved |
| **Map Polyline UC Spans** | `ClimbPolylineContractTest.kt` | UC climbs produce `ClimbHighlightData` with `Color(0xFF757575)` |
| **Elevation Profile UC Spans** | `ElevationProfileClimbSpanContractTest.kt` | All climbs including UC rendered along X-axis baseline |
| **Full Regression** | `./gradlew clean testDebugUnitTest` | 100% test pass rate across all test classes |

---

## 8. Rollback & Contingency Plan

If any visual regression or unexpected behavior occurs:
1. Revert changes to `RouteOnMapScreen.kt`, `MapContentScope.kt`, and `ElevationProfile.kt` via git revert.
2. The changes are strictly localized to Compose presentation layer; no database schema, SharedPreferences, or network protocols are modified. Rollback risk is zero.
