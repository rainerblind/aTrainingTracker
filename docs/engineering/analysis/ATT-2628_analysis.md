# Stage 1 Analysis: ATT-2628 - Reduce route waypoint marker badge size for improved map glanceability

**Ticket**: [ATT-2628](https://atrainingtracker.atlassian.net/browse/ATT-2628)  
**Sub-task**: [ATT-2675](https://atrainingtracker.atlassian.net/browse/ATT-2675) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2628`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.1 physical device review (Pixel 10), high-contrast circular Maki waypoint badge markers (`ATT-2461` / `REQ-MAP-033`) were verified and approved for category-specific color fills, white halo contrast rings, and Maki glyph integration.

However, physical testing revealed an ergonomic and visual density defect:
* The current default badge diameter of **32 dp** is disproportionately large and visually dominant.
* On high-density smartphone displays (e.g., 440+ dpi on Pixel 10), 32 dp covers an $88 \times 88\text{ px}$ circular footprint centered directly over the waypoint coordinate.
* This excessive diameter severely occludes underlying route polylines, tight switchbacks, street names, and topographic map features, especially along routes with frequent waypoints or turn cues.
* In compact route card preview thumbnails (`PathPreviewMap.kt`), 32 dp markers dominate the small preview card and obscure the overall route shape.

The objective of **ATT-2628** is to reduce the default waypoint marker diameter to an unobtrusive, highly glanceable size (**22 dp**, within the requested 20 dp – 24 dp range), ensure all visual elements (outer dark hairline, inner crisp white halo ring, category filled disc, and centered white Maki glyph) scale proportionally and sharply, and verify visual balance across full-screen interactive maps and route card thumbnails.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Marker Generation Mechanics (`MapUtils.kt`)
In `MapUtils.kt`, `createWaypointBadgeMarker` currently defines:
```kotlin
fun createWaypointBadgeMarker(
    context: Context,
    type: WaypointType,
    sizeDp: Int = 32
): BitmapDescriptor?
```
The drawing pipeline uses fixed stroke widths regardless of `sizeDp`:
1. `val outerRadius = center - (0.5f * density)` with stroke `1f * density` (fixed 1 dp).
2. `val haloRadius = outerRadius - (0.5f * density)` with stroke `2f * density` (fixed 2 dp).
3. `val fillRadius = haloRadius - (1.0f * density)`.
4. `val iconSizePx = (sizePx * 0.55f).toInt()`.

### Why 32 dp Was Too Large
* Surface area comparison:
  * At 32 dp diameter: Area = $\pi \times 16^2 \approx 804.2\text{ dp}^2$.
  * At 22 dp diameter: Area = $\pi \times 11^2 \approx 380.1\text{ dp}^2$.
  * Reducing the diameter to 22 dp achieves a **52.7% reduction in visual occlusion area**, eliminating the sensation of map crowding while retaining ample surface for instant category identification.
* Stroke scaling considerations:
  * If `sizeDp` is reduced to 22 dp without proportional stroke scaling, the 2 dp white halo stroke consumes $4\text{ dp}$ of total diameter ($18.2\%$ of the badge), compressing the inner category disc and Maki glyph.
  * Proportional scaling using a scale factor `scale = sizeDp / 32f` with minimal crisp hairline clamping (outer stroke: $\max(0.75\text{ dp}, 1\text{ dp} \times scale)$; halo stroke: $\max(1.25\text{ dp}, 2\text{ dp} \times scale)$) guarantees sharp antialiased rendering across all display densities (mdpi to xxxhdpi) without pinching the central icon.

### Map Layer Integration (`MapLayers.kt`)
In `MapLayers.kt` (`RouteWaypointsLayer`):
```kotlin
val iconDescriptor = remember(waypoint.type, context) {
    context?.let { ctx ->
        createWaypointBadgeMarker(ctx, waypoint.type, 32)
    }
}
```
`RouteWaypointsLayer` currently hardcodes `32` when calling `createWaypointBadgeMarker`, bypassing any default constant or configurability.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Define a standardized constant `DEFAULT_WAYPOINT_MARKER_SIZE_DP = 22` in `MapUtils.kt`.
  2. Update `createWaypointBadgeMarker` to default to `sizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP`.
  3. Implement proportional stroke scaling for the outer contrast stroke and white halo ring, clamped to crisp visibility thresholds.
  4. Update `RouteWaypointsLayer` in `MapLayers.kt` to expose `markerSizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP` and pass `markerSizeDp` into `createWaypointBadgeMarker`.
  5. Verify rendering and visual balance in both full-screen map views (`RouteOverlayLayer` / `RouteWaypointsLayer`) and route preview cards (`PathPreviewMap.kt`).
  6. Add automated unit and contract tests in `WaypointBadgeMarkerTest.kt` verifying the 22 dp default diameter, proportional stroke geometry, and non-empty bitmap generation.
  7. Guarantee 100% pass rate across the full clean-room unit test suite.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying waypoint category color mapping (`resolveWaypointCategoryColor` remains strictly unchanged).
  2. Modifying waypoint SQLite schema or database tables in `Routes.db`.
  3. Changing InfoWindow tap interactions or title/snippet formatting in `RouteWaypointsLayer`.
  4. Altering GPS tracking polylines or Strava Live Segment rendering.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines Clause 3 & Clause 4 of `REQ-MAP-033` (*High-Contrast Maki POI Waypoint Badges, Shelter Classification & Substring Disambiguation*), targeting `MapUtils.kt` and `MapLayers.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-2461`, Sprint `2026-41.1`, commit `9b8fc6a9`.
* **Root Reason for Existing Formulation**: In ATT-2461, circular badge markers were introduced to replace bare unbacked vector icons, which suffered from poor contrast against varied satellite/terrain maps. A 32 dp diameter was initially chosen as a conservative reference to ensure glyph legibility. However, physical on-device evaluation during the Sprint 2026-41.1 review revealed that 32 dp markers are excessively large, creating polyline track occlusion and visual clutter on high-density screens.
* **Preservation of Core Invariants**: 
  - All 10 distinct waypoint category colors (`#E65100`, `#0288D1`, `#2E7D32`, `#00695C`, `#EF6C00`, `#6A1B9A`, `#D32F2F`, `#C62828`, `#1565C0`, `#546E7A`) remain intact.
  - White Maki vector glyphs centered at 55% of diameter with white tinting remain intact.
  - Double-ring contrast geometry (outer dark hairline + inner white halo) remains intact.
  - Zero database schema modifications; 100% full-suite unit test pass rate preserved.

---

## 5. Architectural Strategy & High-Level Solution

### Architectural Changes
1. **`MapUtils.kt`**:
   - Introduce `const val DEFAULT_WAYPOINT_MARKER_SIZE_DP = 22`.
   - Update `createWaypointBadgeMarker`:
     ```kotlin
     fun createWaypointBadgeMarker(
         context: Context,
         type: WaypointType,
         sizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP
     ): BitmapDescriptor?
     ```
   - Scale strokes proportionally:
     ```kotlin
     val density = context.resources.displayMetrics.density
     val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
     val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
     val canvas = Canvas(bitmap)
     val center = sizePx / 2f

     val scale = sizeDp / 32f
     val darkStrokeWidth = (1f * density * scale).coerceAtLeast(0.75f * density)
     val haloStrokeWidth = (2f * density * scale).coerceAtLeast(1.25f * density)
     ...
     ```
2. **`MapLayers.kt`**:
   - In `RouteWaypointsLayer`, add `markerSizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP` parameter.
   - Update descriptor creation: `createWaypointBadgeMarker(ctx, waypoint.type, markerSizeDp)`.
   - In `remember` key, include `markerSizeDp` so changing marker size properly invalidates cached descriptors.
3. **`WaypointBadgeMarkerTest.kt`**:
   - Assert `DEFAULT_WAYPOINT_MARKER_SIZE_DP == 22`.
   - Verify `createWaypointBadgeMarker` contract and proportional rendering at 22 dp and across boundary sizes (e.g., 20 dp, 24 dp, 32 dp).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Visual Contrast Hierarchy**: Waypoint markers remain clearly visible over terrain, road labels, and active route lines.
  2. **Proportional Legibility**: Maki glyphs never shrink below recognizable dimensions; stroke rings never blur or drop out.
  3. **Zero Runtime Regressions**: 100% clean-room test pass rate across all project unit test suites.
  4. **Human Decision Gate**: Parent ticket `ATT-2628` completion is reserved strictly for human review.

* **Risk Rating**: **LOW**
  - Self-contained UI presentation and bitmap generation change.
  - Zero database schema, background service, sensor processing, or threading dependencies.
