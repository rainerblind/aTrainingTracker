# Stage 2 Requirement & Test Specification: ATT-2510

## 1. Traceability & Scope
- **Ticket**: [ATT-2510](https://atrainingtracker.atlassian.net/browse/ATT-2510) — *Mark Climb Spans on Elevation Profile X-Axis Preserving Slope Gradient Coloring*
- **Requirement**: `REQ-UI-299` (*Preservation of Slope Gradient Coloring on Elevation Profile Curve and Horizontal X-Axis Climb Span Highlighting*)
- **Test ID**: `TST-UI-259` (*Elevation Profile Slope Gradient Preservation and X-Axis Climb Span Highlight Verification*)
- **Primary Source File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
- **Target Contract Test**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileClimbSpanContractTest.kt`

## 2. Requirement Details (REQ-UI-299)
1. **Slope Gradient Ridge Integrity**:
   - The elevation profile curve SHALL NOT be overwritten by a solid climb category color stroke.
   - All curve segments SHALL strictly retain their physical slope gradient coloration (`TTColor.Zone1`..`Zone5`) derived from `ElevationSmoothingMath.calculateGrade`.
2. **Horizontal X-Axis Climb Span Highlighting**:
   - For recognized cycling climbs (`climbs.isNotEmpty()` and `!isTimeDomain`), the system SHALL render a dedicated category-colored horizontal span bar along the X-axis baseline (`y = height - 2.dp.toPx()`) spanning from the climb's start distance to end distance.
   - The start and end canvas X coordinates SHALL be evaluated via `ElevationProfileZoomMath.distanceToCanvasX(startDist, currentStartDist, visibleSpan, width)` and `ElevationProfileZoomMath.distanceToCanvasX(endDist, currentStartDist, visibleSpan, width)`.
   - The span bar SHALL be drawn with `color = getClimbCategoryColors(climb.category).first`, `strokeWidth = 4.dp.toPx()`, and `cap = StrokeCap.Round`.
   - Climbs with `category == ClimbCategory.UNCATEGORIZED` SHALL be omitted from the X-axis span highlights.
3. **Summit Badges Preservation**:
   - Summit category badges, pins, and background pills positioned above the elevation profile peaks SHALL remain visible and anchored to `summitPt`.
4. **Preservation of Invariants**:
   - Touch scrubbing, telemetry badges, zoom/pan bounds, time-domain rendering, and tick spacing MUST NOT be modified.

## 3. Test Cases (TST-UI-259)

### Test Case 1: Ridge Overdraw Elimination
- **Target**: `testElevationProfile_doesNotContainAccentRidgeStrokeOverdraw`
- **Given**: The source code of `ElevationProfile.kt`.
- **When**: Inspecting curve rendering routines.
- **Then**: `ElevationProfile.kt` SHALL NOT contain any loop drawing lines between `px1, py1` and `px2, py2` with climb category `bgColor`. The slope curve `drawLine(seg.color, Offset(x1, y1), Offset(x2, y2), 2.dp.toPx())` remains unobstructed.

### Test Case 2: Horizontal X-Axis Span Rendering
- **Target**: `testElevationProfile_rendersClimbSpansAlongXAxisBaseline`
- **Given**: An elevation profile rendering in distance domain with recognized cycling climbs.
- **When**: Rendering the canvas content.
- **Then**: For each classified climb, a horizontal bar is rendered along the baseline with:
  - Y position: `height - 2.dp.toPx()` (to prevent clipping).
  - Start coordinate: mapped from `climb.pathPoints.first().distance`.
  - End coordinate: mapped from `climb.pathPoints.last().distance`.
  - Canvas X clamping: `coerceIn(0f, width)`.

### Test Case 3: Styling & Category Classification
- **Target**: `testElevationProfile_usesCategoryColorsAndStrokeWidth`
- **Given**: A climb with category `CAT_4`, `CAT_3`, `CAT_2`, `CAT_1`, or `HC`.
- **When**: Drawing the span bar.
- **Then**: The stroke width is `4.dp.toPx()`, the cap is `StrokeCap.Round`, and the color matches `getClimbCategoryColors(climb.category).first`.

### Test Case 4: Uncategorized Exclusion
- **Target**: `testElevationProfile_omitsUncategorizedClimbsFromXAxis`
- **Given**: A climb with `climb.category == ClimbCategory.UNCATEGORIZED`.
- **When**: Evaluating climbs for X-axis highlighting.
- **Then**: The climb is filtered out and no span bar is drawn.

### Test Case 5: Summit Badges Retention
- **Target**: `testElevationProfile_preservesSummitBadges`
- **Given**: The source code of `ElevationProfile.kt`.
- **When**: Inspecting summit badge routines.
- **Then**: The summit badge rendering logic (stem paint, category pill, badge text) remains intact and anchored to `summitPt`.

## 4. Acceptance Criteria (Given-When-Then)
- *Given* an elevation profile with recognized climbs,
- *When* viewed in distance domain,
- *Then* the elevation profile curve retains its slope gradient colors without being overridden by a solid category stroke, and climb spans are clearly demarcated along the horizontal X-axis baseline.
- *Given* full test suite execution (`./gradlew testDebugUnitTest`),
- *Then* 100% of unit and contract tests pass with zero regressions.
