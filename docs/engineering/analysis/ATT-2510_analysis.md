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

## 2. Requirement Traceability & Mapping
- **Direct Requirement**: `REQ-UI-299` (*Preservation of Slope Gradient Coloring on Elevation Profile Curve and Horizontal X-Axis Climb Span Highlighting*).
- **Target Test Specification**: `TST-UI-259` (*Elevation Profile Slope Gradient Preservation and X-Axis Climb Span Highlight Verification*).
- **Refined Requirements**:
  - `REQ-UI-274`: *Climb Categorization & Accent Highlights* — refined to retain summit category badges while relocating the climb extent indication off the ridge curve to the X-axis baseline.
  - `REQ-UI-192`: *ElevationProfile Mathematical Precision & Zoom Engine* — coordinate mapping invariance (`ElevationProfileZoomMath.distanceToCanvasX`).
  - `REQ-UI-297`: *Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Evaluation* — ensures grade calculation and zone colors (`Zone1`..`Zone5`) remain pristine on the ridge curve.

## 3. Call-Site Inventory (`ElevationProfile.kt`)
The changes are contained within `ElevationProfile.kt`:
1. **Composables and Canvas Draw Scope**:
   - `ElevationProfile(pathPoints, ... climbs, ...)` (lines 223–873): The root composable receiving `climbs: List<Climb>`.
   - `Canvas(modifier = canvasModifier.padding(bottom = 24.dp, start = 50.dp, end = 25.dp, top = topPadding))` (lines 562–858):
     - `drawIntoCanvas { canvas -> ... }` (lines 569–738): Renders gridlines, axis labels, and summit category badges (`lines 679–737`).
     - `clipRect(left = 0f, top = 0f, right = width, bottom = height)` (lines 740–789):
       - Slope gradient curve rendering (lines 741–762): **PRESERVED 100%**.
       - Accented ridge stroke rendering loop (lines 764–788): **REMOVED**.
2. **Horizontal X-Axis Span Rendering Call-Site**:
   - Within the Canvas `drawIntoCanvas` block (or directly on `DrawScope` after `clipRect`), render the horizontal climb span bars along the X-axis baseline.

## 4. Layout Boundary, Clipping & Coordinate Calculations
To avoid visual clipping at canvas boundaries or collision with X-axis tick labels:
1. **Vertical Offset & Baseline Invariant**:
   - The Canvas has a bottom padding of `24.dp` (`modifier.padding(bottom = 24.dp, ...)`).
   - Tick labels are drawn at `height + 45f` in native canvas coordinates.
   - The profile baseline is at `y = height`.
   - To prevent bottom edge clipping when using `strokeWidth = 4.dp.toPx()`, the baseline stroke center is positioned at `val spanY = height - 2.dp.toPx()` (exactly inset by half the stroke width). This guarantees that the 4 dp thick bar sits flush against the bottom of the profile fill without extending past the canvas bottom clipping plane.
2. **Horizontal Clamping**:
   - For each classified climb (`climb.category != ClimbCategory.UNCATEGORIZED`):
     - `val rawX1 = ElevationProfileZoomMath.distanceToCanvasX(startDist, currentStartDist, visibleSpan, width)`
     - `val rawX2 = ElevationProfileZoomMath.distanceToCanvasX(endDist, currentStartDist, visibleSpan, width)`
     - If `rawX2 < 0f || rawX1 > width`, skip (culled outside visible viewport).
     - Clamped bounds: `val x1 = rawX1.coerceIn(0f, width)` and `val x2 = rawX2.coerceIn(0f, width)`.
     - Render line with `StrokeCap.Round`, `color = getClimbCategoryColors(climb.category).first`, and `strokeWidth = 4.dp.toPx()`.
3. **Category Exclusion Invariant**:
   - Climbs classified as `ClimbCategory.UNCATEGORIZED` are omitted, preventing arbitrary flat/mild sections from cluttering the X-axis baseline.

## 5. Chesterton's Fence & Invariant Preservation
- **Summit Badges**: Summit pins and category pills anchored at `summitPt` (lines 679–737) remain 100% intact.
- **Slope Colors**: The curve `drawLine(seg.color, Offset(x1, y1), Offset(x2, y2), 2.dp.toPx())` is no longer occluded by any overlay.
- **Interactions**: Touch scrubbing, multi-metric telemetry badge, pinch-to-zoom, and time domain remain 100% operational.

## 6. Verification Strategy
- Author `ElevationProfileClimbSpanContractTest.kt` verifying:
  1. Complete removal of the accented ridge stroke overdraw on the elevation curve.
  2. Presence of the horizontal X-axis climb span rendering logic with proper baseline inset (`height - 2.dp.toPx()`).
  3. Omission of `ClimbCategory.UNCATEGORIZED`.
  4. Preservation of summit pins and badges.
- Execute full regression test suite (`./gradlew testDebugUnitTest`).
