# Stage 3: Implementation Plan - ATT-2628: Reduce route waypoint marker badge size for improved map glanceability

**Ticket**: [ATT-2628](https://atrainingtracker.atlassian.net/browse/ATT-2628)  
**Sub-task**: [ATT-2677](https://atrainingtracker.atlassian.net/browse/ATT-2677) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Requirement Mapping**: `REQ-MAP-037` (*Route Waypoint Marker Badge Diameter Optimization & Glanceability Proportional Scaling*)  
**Test Mapping**: `TST-MAP-039` (*Route Waypoint Marker Badge Diameter Optimization & Glanceability Proportional Scaling Verification*)  
**Branch**: `feature/ATT-2628`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Description & Background

During physical device testing on Pixel 10 (Sprint 2026-41.1 review), the circular Maki waypoint badge markers (`ATT-2461` / `REQ-MAP-033`) were observed to be too large and visually dominant at their initial 32 dp diameter ($88 \times 88\text{ px}$ on high-density displays). The oversized badges occlude route polyline tracks, street names, and map topography, and dominate small route preview thumbnails (`PathPreviewMap.kt`).

This implementation plan deconstructs the tasks required to optimize the badge diameter to **22 dp** ($52.7\%$ surface area reduction), scale stroke geometry and glyphs proportionally with hairline clamping, make marker size configurable in `RouteWaypointsLayer`, and verify 100% test coverage.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-037` (*Route Waypoint Marker Badge Diameter Optimization & Glanceability Proportional Scaling*)
* **Test Mapping**: `TST-MAP-039` (*Route Waypoint Marker Badge Diameter Optimization & Glanceability Proportional Scaling Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing route management, navigation, and map rendering suites pass with 100% success rate.
2. **Category Color Invariance**: All 10 semantic category colors (`resolveWaypointCategoryColor`) remain strictly preserved.
3. **Maki Glyph & Asset Linkage**: All 10 Maki vector drawables (`type.iconResId`) and white tinting remain intact.
4. **Database & Schema Invariance**: SQLite schema version 10 in `Routes.db` and table `route_waypoints` remain untouched.
5. **Interactive InfoWindows**: Marker click listeners, title, description, and altitude snippet formatting in `RouteWaypointsLayer` remain preserved.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-2628` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `MapUtils.kt` (Bitmap Generation & Geometry Scaling)
* Define `const val DEFAULT_WAYPOINT_MARKER_SIZE_DP = 22`.
* In `createWaypointBadgeMarker(context: Context, type: WaypointType, sizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP)`:
  * Compute proportional scale ratio: `val scale = sizeDp / 32f`.
  * Compute dark contrast stroke width: `val darkStrokeWidth = (1f * density * scale).coerceAtLeast(0.75f * density)`.
  * Compute white halo ring stroke width: `val haloStrokeWidth = (2f * density * scale).coerceAtLeast(1.25f * density)`.
  * Derive outer radius, halo radius, and fill radius from center and stroke widths.
  * Draw centered white Maki vector glyph with `iconSizePx = (sizePx * 0.55f).toInt().coerceAtLeast(1)`.

### Component 2: `MapLayers.kt` (Composable Map Layer)
* In `RouteWaypointsLayer`:
  * Add parameter `markerSizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP`.
  * Pass `markerSizeDp` to `createWaypointBadgeMarker(ctx, waypoint.type, markerSizeDp)`.
  * In `remember` cache key, include `markerSizeDp`: `remember(waypoint.type, context, markerSizeDp)`.

### UI Consistency (Rule 23)
* **Reference screen / component**: `RouteWaypointsLayer` in `MapLayers.kt` and `createSensorMarker` in `MapUtils.kt`.
* **Reused components**: Standard Google Maps Compose `Marker` composable, `saveBitmapDescriptorFactoryFromBitmap`.
* **Theme tokens**: 
  * Category fills: Defined semantic color tokens (`TTColor` / `resolveWaypointCategoryColor`).
  * Contrast rings: Outer hairline (`0x33000000`), white halo (`Color.WHITE`).
  * Size: Optimized from 32 dp to 22 dp, matching standard compact touch/marker proportions.
* **New one-off styles & justification**: None. Reuses established double-ring circular badge geometry with proportional scaling.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Verification (`REQ-PRO-016`)
* Verify Stage 3 Plan subtask `ATT-2677` is in status `Erledigt` via:
  ```bash
  python3 tools/jira_util.py check-gate ATT-2677
  ```
* Halt immediately if exit code is non-zero.

### Step 2: Implement Marker Size Constant & Proportional Stroke Scaling in `MapUtils.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapUtils.kt`
* **Changes**:
  1. Add `const val DEFAULT_WAYPOINT_MARKER_SIZE_DP = 22`.
  2. Update `createWaypointBadgeMarker` signature to use `sizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP`.
  3. Replace fixed 1 dp and 2 dp strokes with dynamically scaled stroke widths clamped to $\ge 0.75\text{ dp}$ (outer dark hairline) and $\ge 1.25\text{ dp}$ (white halo ring).
  4. Scale glyph size to $55\%$ of `sizePx` coerced to at least $1\text{ px}$.

### Step 3: Integrate Configurable Marker Size in `MapLayers.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt`
* **Changes**:
  1. In `RouteWaypointsLayer`, add parameter `markerSizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP`.
  2. Call `createWaypointBadgeMarker(ctx, waypoint.type, markerSizeDp)`.
  3. Update `remember` cache key: `remember(waypoint.type, context, markerSizeDp)`.

### Step 4: Add Unit & Contract Tests in Test Suites
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/WaypointBadgeMarkerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItemWaypointContractTest.kt`
* **Changes**:
  1. In `WaypointBadgeMarkerTest.kt`:
     - Add `testMapUtils_defaultMarkerSizeConstant_is22Dp`.
     - Add `testMapUtils_createWaypointBadgeMarker_defaultSizeAndProportionalScaling` verifying default 22 dp and proportional stroke scaling across sizes (20, 22, 24, 32 dp) for all 10 `WaypointType`s.
  2. In `RouteItemWaypointContractTest.kt`:
     - Add `testRouteWaypointsLayer_exposesMarkerSizeDpParameter` asserting signature and descriptor creation.

### Step 5: Execute Targeted Unit Tests
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.ui.map.WaypointBadgeMarkerTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteItemWaypointContractTest"
  ```
* **Success Criteria**: 100% tests pass cleanly in under 15 seconds.

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted test suites executed during Stage 4.
  2. Full clean-room regression suite (`./gradlew testDebugUnitTest`) executed during Stage 5.
  3. Stage 5 Walkthrough authored documenting verification evidence.
* **Rollback Plan**:
  - Git branch isolation (`feature/ATT-2628`) allows clean rollback or reset via `git checkout sprint/2026-41.3`.
  - Zero database schema migrations ensure complete backward compatibility.
