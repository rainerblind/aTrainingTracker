# Stage 3 Implementation Plan: ATT-2510

## 1. Architectural Blueprint (SWE.2)
- **Ticket**: [ATT-2510](https://atrainingtracker.atlassian.net/browse/ATT-2510) — *Mark Climb Spans on Elevation Profile X-Axis Preserving Slope Gradient Coloring*
- **Requirement**: `REQ-UI-299`
- **Test Specification**: `TST-UI-259`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`

```mermaid
flowchart TD
    A[ElevationProfile Canvas Render] --> B{isTimeDomain == false && climbs.isNotEmpty()?}
    B -->|Yes| C[Draw Summit Badges at summitPt]
    B -->|No| D[Draw Profile Segments]
    C --> D
    D --> E[Draw Slope Gradient Curve seg.color 2dp]
    E --> F{Render X-Axis Climb Spans}
    F -->|For each categorized climb| G[Compute startX and endX via distanceToCanvasX]
    G --> H[Draw Horizontal Bar at height - 2.dp.toPx with category color 4dp]
    H --> I[Touch Scrubbing & Telemetry Badges]
```

## 2. UI Consistency & Aesthetics Checklist (Rule 23)
- [x] **Slope Gradient Fidelity**: The elevation curve segments strictly retain their physical slope gradient colors (`Zone1`..`Zone5`). Zero overlay or overdraw stroke on the curve.
- [x] **X-Axis Climb Span Bars**: Rendered along the X-axis baseline (`y = height - 2.dp.toPx()`) with `strokeWidth = 4.dp.toPx()` and `cap = StrokeCap.Round`.
- [x] **Color Harmony**: Bar color matches `getClimbCategoryColors(climb.category).first` (`HC`, `CAT_1`, `CAT_2`, `CAT_3`, `CAT_4`).
- [x] **Category Culling**: `ClimbCategory.UNCATEGORIZED` climbs are omitted from the X-axis highlight.
- [x] **Viewport Clipping**: Coordinates are clamped to `[0f, width]` and off-screen climbs (`rawX2 < 0f || rawX1 > width`) are skipped.
- [x] **Summit Badges**: Summit pins, category pills, and labels anchored at `summitPt` remain visible and unchanged.
- [x] **Tick Clearance**: X-axis distance tick labels at `height + 45f` maintain full clearance without collision.

## 3. Atomic Implementation Steps

### Step 1: Remove Accented Ridge Stroke Overdraw (`ElevationProfile.kt`)
- In `ElevationProfile.kt` lines 764–788, remove the accented ridge stroke loop that overdrew the profile curve with `strokeWidth = 3.5.dp.toPx()`.
- Ensure `cachedData.segments.forEach { seg -> drawLine(seg.color, ...) }` is the sole stroke defining the elevation ridge.

### Step 2: Render Horizontal X-Axis Climb Spans (`ElevationProfile.kt`)
- Along the bottom baseline of the profile, add:
```kotlin
// Render horizontal climb span indicators along the X-axis baseline (REQ-UI-299)
if (!isTimeDomain && climbs.isNotEmpty()) {
    val baselineY = height - 2.dp.toPx()
    climbs.filter { it.category != ClimbCategory.UNCATEGORIZED }.forEach { climb ->
        val cPoints = climb.pathPoints
        val startDist = cPoints.firstOrNull()?.distance ?: return@forEach
        val endDist = cPoints.lastOrNull()?.distance ?: return@forEach
        if (endDist < currentStartDist || startDist > currentStartDist + visibleSpan) return@forEach

        val rawX1 = ElevationProfileZoomMath.distanceToCanvasX(startDist, currentStartDist, visibleSpan, width)
        val rawX2 = ElevationProfileZoomMath.distanceToCanvasX(endDist, currentStartDist, visibleSpan, width)
        val x1 = rawX1.coerceIn(0f, width)
        val x2 = rawX2.coerceIn(0f, width)

        if (x2 > x1) {
            val (bgColor, _, _) = getClimbCategoryColors(climb.category)
            drawLine(
                color = bgColor,
                start = Offset(x1, baselineY),
                end = Offset(x2, baselineY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
```

### Step 3: Author Contract Tests (`ElevationProfileClimbSpanContractTest.kt`)
- Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileClimbSpanContractTest.kt`.
- Test:
  1. `testElevationProfile_doesNotContainAccentRidgeStrokeOverdraw`
  2. `testElevationProfile_rendersClimbSpansAlongXAxisBaseline`
  3. `testElevationProfile_usesCategoryColorsAndStrokeWidth`
  4. `testElevationProfile_omitsUncategorizedClimbsFromXAxis`
  5. `testElevationProfile_preservesSummitBadges`

### Step 4: Verification & Regression Gate
- Run targeted tests via Gradle (`BypassSandbox: true`).
- Run full clean-room regression test suite (`./gradlew testDebugUnitTest`).

## 4. Invariants & Risk Mitigation
- **Boundary Clamping**: `coerceIn(0f, width)` guarantees that partially visible climbs pan smoothly across viewport edges without drawing out of bounds.
- **Vertical Inset**: Insetting by `2.dp.toPx()` (`height - 2.dp.toPx()`) guarantees that the 4 dp stroke does not bleed past the canvas edge.
- **Time Domain Safety**: Guarded by `!isTimeDomain`, ensuring time-domain workouts with time on the X-axis are unaffected.
