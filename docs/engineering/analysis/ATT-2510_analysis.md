# Stage 1 Analysis: ATT-2510

## 1. Problem Domain & Background
In `ElevationProfile.kt`, the elevation profile curve visualizes course topography. Course slope gradient is dynamically evaluated via `ElevationSmoothingMath.calculateGrade` and color-coded along the curve into standard training/climbing zones (`TTColor.Zone1`..`Zone5`).

In sprint 2026-41.2 (commit `ATT-2358` / `REQ-UI-274`), an "accented ridge stroke" was added to highlight recognized cycling climbs (`climbs.isNotEmpty()` and `!isTimeDomain`). However, this ridge stroke was rendered directly on top of the elevation profile curve segments:
```kotlin
drawLine(
    color = bgColor,
    start = Offset(px1, py1),
    end = Offset(px2, py2),
    strokeWidth = 3.5.dp.toPx(),
    cap = StrokeCap.Round
)
```
Because `strokeWidth` (3.5 dp) was thicker than the underlying slope gradient stroke (2.0 dp) and used a solid climb category color (`bgColor`), it completely painted over and obliterated the physical slope grade colors. Athletes could no longer distinguish between steep pitches (e.g. 15%+ Zone 5) and moderate sections (e.g. 4-6% Zone 2) within a climb.

## 2. Chesterton's Fence & Requirement Archaeology
- **Why was the accented ridge stroke added?**
  In `ATT-2358`, athletes wanted recognized cycling climbs to be immediately noticeable on the elevation profile, rather than only seeing summit badge pins.
- **Why was it placed on the ridge curve?**
  It was a straightforward, immediate way to show which parts of the profile were climbs. However, it created a severe visual trade-off: the primary function of the elevation curve—to communicate local gradient steepness via color—was sacrificed.
- **How to preserve the intent without sacrificing gradient colors?**
  Keep the slope gradient colors on the elevation ridge curve 100% intact. Delineate the spatial extent of each climb along the horizontal X-axis baseline (`y = height`) with a distinct category-colored bar/bracket. This clearly demarcates climb start and end distances without conflicting with the slope color palette on the ridge.

## 3. Invariants & Scope Bounds
1. **Preserve Ridge Slope Colors**:
   The elevation curve segments rendered in `cachedData.segments.forEach { seg -> drawLine(seg.color, ... 2.dp.toPx()) }` must remain completely unobstructed. The accented ridge stroke `drawLine` over `(px1, py1)` to `(px2, py2)` must be eliminated.
2. **Horizontal X-Axis Span Bar**:
   Along the bottom horizontal X-axis baseline (`y = height`), render a category-colored horizontal bar for each recognized climb:
   - Distance range: `climb.pathPoints.first().distance` to `climb.pathPoints.last().distance`.
   - Canvas coordinates: `x1 = distanceToCanvasX(startDist, ...)`, `x2 = distanceToCanvasX(endDist, ...)`.
   - Color: `getClimbCategoryColors(climb.category).first`.
   - Thickness: `4.dp.toPx()`, `cap = StrokeCap.Round`.
   - Exclusion: `ClimbCategory.UNCATEGORIZED` climbs are omitted.
3. **Summit Badges**:
   The summit pins and category badges (`drawRoundRect`, `drawText`, stem line) above the peaks remain completely intact.
4. **Telemetry & Interaction**:
   Profile zoom, pan, scrubbing, time-domain rendering, and tick marks remain 100% functional.

## 4. Verification Strategy
- Architectural contract test `ElevationProfileClimbSpanContractTest.kt` verifying:
  1. Elimination of the accented ridge stroke overdraw on the elevation curve.
  2. Rendering of horizontal category-colored bars along the X-axis baseline for classified climbs.
  3. Proper bounding using `pathPoints.first().distance` and `pathPoints.last().distance`.
  4. Omission of `ClimbCategory.UNCATEGORIZED`.
  5. Preservation of summit badges.
- Full clean-room regression test suite (`./gradlew testDebugUnitTest`).
