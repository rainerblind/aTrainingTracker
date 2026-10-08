# Stage 2: Requirement & Test Specification - ATT-2628: Reduce route waypoint marker badge size for improved map glanceability

**Ticket**: [ATT-2628](https://atrainingtracker.atlassian.net/browse/ATT-2628)  
**Sub-task**: [ATT-2676](https://atrainingtracker.atlassian.net/browse/ATT-2676) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Requirement Mapping**: `REQ-MAP-037` (*Route Waypoint Marker Badge Diameter Optimization & Glanceability Proportional Scaling*)  
**Test Spec ID**: `TST-MAP-039`  
**Branch**: `feature/ATT-2628`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (`REQ-MAP-037`)

### 1.1 Problem Statement & Rationale
During Sprint 2026-41.1 review on physical device (Pixel 10), high-contrast circular Maki waypoint badge markers were approved for category-specific color fills, white halo contrast rings, and Maki glyph integration (`ATT-2461` / `REQ-MAP-033`).
However, the 32 dp marker diameter was observed to be disproportionately large on high-density screens ($88 \times 88\text{ px}$), occluding route polyline tracks, street names, and surrounding terrain. In route card preview thumbnails (`PathPreviewMap.kt`), 32 dp markers dominate small cards.

`REQ-MAP-037` optimizes the default diameter to **22 dp** ($52.7\%$ surface area reduction from $804.2\text{ dp}^2$ to $380.1\text{ dp}^2$), introduces proportional double-ring stroke scaling with minimum hairline clamping, and makes the marker size configurable in `RouteWaypointsLayer`.

### 1.2 Functional & Architectural Requirements

1. **Default Marker Diameter Constant & Configuration (`MapUtils.kt`)**:
   - `MapUtils.kt` SHALL define `const val DEFAULT_WAYPOINT_MARKER_SIZE_DP = 22`.
   - `createWaypointBadgeMarker(context: Context, type: WaypointType, sizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP): BitmapDescriptor?` SHALL adopt `DEFAULT_WAYPOINT_MARKER_SIZE_DP` as its default parameter value.

2. **Proportional Double-Ring Stroke & Central Glyph Geometry Scaling**:
   - The circular badge rendering pipeline SHALL scale stroke widths proportionally using scaling ratio `scale = sizeDp / 32f`.
   - Outer dark contrast hairline / shadow stroke width SHALL evaluate to `(1f * density * scale).coerceAtLeast(0.75f * density)` with color `0x33000000`.
   - Inner crisp white halo ring stroke width SHALL evaluate to `(2f * density * scale).coerceAtLeast(1.25f * density)` with color `Color.WHITE`.
   - The category-filled disc SHALL occupy `fillRadius = haloRadius - (haloStrokeWidth / 2f)`.
   - The centered white Maki glyph SHALL be rendered with `iconSizePx = (sizePx * 0.55f).toInt().coerceAtLeast(1)` and tinted white (`Color.WHITE`).

3. **Composable Map Layer Integration (`MapLayers.kt`)**:
   - `RouteWaypointsLayer` SHALL accept parameter `markerSizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP`.
   - It SHALL invoke `createWaypointBadgeMarker(ctx, waypoint.type, markerSizeDp)` and key cached bitmap descriptors by `remember(waypoint.type, context, markerSizeDp)`.

4. **Visual Balance in Route Previews & Interactive Maps (`PathPreviewMap.kt`, `RouteOverlayLayer`)**:
   - Waypoint markers rendered across both full-screen map views and route card preview thumbnails SHALL inherit the compact 22 dp badge size, eliminating polyline track occlusion and visual clutter while preserving sharp Maki glyph legibility.

5. **Category Color & Resource Invariants Preservation**:
   - All 10 distinct waypoint category colors (`#E65100`, `#0288D1`, `#2E7D32`, `#00695C`, `#EF6C00`, `#6A1B9A`, `#D32F2F`, `#C62828`, `#1565C0`, `#546E7A`), Mapbox Maki CC0 glyph linkage, and 9-language localization parity SHALL remain strictly preserved.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines Clause 3 & Clause 4 of `REQ-MAP-033` (*High-Contrast Maki POI Waypoint Badges, Shelter Classification & Substring Disambiguation*), targeting `MapUtils.kt` and `MapLayers.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-2461`, Sprint `2026-41.1`, commit `9b8fc6a9`.
* **Root Reason for Existing Formulation**: In ATT-2461, circular badge markers were introduced to replace bare unbacked vector icons, which suffered from poor contrast against varied satellite/terrain maps. A 32 dp diameter was initially chosen as a conservative reference to ensure glyph legibility. However, physical on-device evaluation during the Sprint 2026-41.1 review revealed that 32 dp markers are excessively large, creating polyline track occlusion and visual clutter on high-density screens.
* **Preservation of Core Invariants**: All 10 distinct waypoint category colors, white Maki vector glyphs centered at 55% of diameter, double-ring contrast geometry, InfoWindow tap interactions, route card thumbnail rendering, and 100% full-suite unit test pass rate remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Default Marker Diameter)**:
  * *Given* `createWaypointBadgeMarker` called without an explicit `sizeDp` argument,
  * *When* the marker bitmap is generated,
  * *Then* the bitmap dimensions SHALL match `22 dp * density` (`DEFAULT_WAYPOINT_MARKER_SIZE_DP == 22`).
* **Criterion 2 (Unobtrusive Map Rendering)**:
  * *Given* `RouteWaypointsLayer` rendering on a full-screen interactive map or inside `PathPreviewMap`,
  * *When* composing waypoint markers,
  * *Then* markers SHALL render with 22 dp diameter, leaving underlying route tracks and map details unobscured.
* **Criterion 3 (Crisp Anti-Aliased Stroke Geometry)**:
  * *Given* any supported screen density (mdpi to xxxhdpi),
  * *When* generating 22 dp badge markers,
  * *Then* outer dark hairline stroke SHALL be $\ge 0.75\text{ dp}$ and white halo ring SHALL be $\ge 1.25\text{ dp}$, preventing stroke drop-out or blurring.
* **Criterion 4 (Category Color & Glyph Integrity)**:
  * *Given* all 10 `WaypointType`s,
  * *When* rendered at 22 dp,
  * *Then* each marker SHALL present its distinct category color, crisp halo, and centered white Maki glyph.

### 1.5 System Invariants
* SQLite schema version 10 in `Routes.db` and table `route_waypoints` MUST NOT be altered.
* InfoWindow click listeners and snippet formatting in `RouteWaypointsLayer` MUST NOT be altered.
* 100% full-suite clean-room unit test pass rate MUST NOT be broken.

---

## 2. Test Specification (`TST-MAP-039`)

### Test Case 1: `testMapUtils_defaultMarkerSizeConstant_is22Dp` (`TST-MAP-039.1`)
* **Scope**: Automated Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/WaypointBadgeMarkerTest.kt`
* **Preconditions**: `MapUtils.kt` is compiled.
* **Action**: Read `DEFAULT_WAYPOINT_MARKER_SIZE_DP` via reflection or direct reference.
* **Expected Result**: Equals `22`.

### Test Case 2: `testMapUtils_createWaypointBadgeMarker_defaultSizeAndProportionalScaling` (`TST-MAP-039.2`)
* **Scope**: Automated Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/WaypointBadgeMarkerTest.kt`
* **Preconditions**: Robolectric / Android test context available.
* **Action**:
  1. Call `createWaypointBadgeMarker(context, WaypointType.POI_SUMMIT)` without `sizeDp` parameter.
  2. Verify non-null BitmapDescriptor generated.
  3. Verify proportional stroke calculation logic in `MapUtils.kt` contains `DEFAULT_WAYPOINT_MARKER_SIZE_DP`, scale clamping `0.75f * density` and `1.25f * density`.
* **Expected Result**: Non-null descriptors returned for all 10 waypoint types; contract assertions pass.

### Test Case 3: `testRouteWaypointsLayer_exposesMarkerSizeDpParameter` (`TST-MAP-039.3`)
* **Scope**: Automated Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItemWaypointContractTest.kt`
* **Preconditions**: `MapLayers.kt` exists.
* **Action**: Inspect source content of `MapLayers.kt`.
* **Expected Result**:
  1. `RouteWaypointsLayer` signature contains `markerSizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP`.
  2. Call site invokes `createWaypointBadgeMarker(ctx, waypoint.type, markerSizeDp)`.
  3. `remember` key includes `markerSizeDp`.

### Test Case 4: 9-Language Localization Audit (`TST-MAP-039.4`)
* **Scope**: Localization Parity Test
* **Target File**: `com.atrainingtracker.translations.TranslationParityTest`
* **Goal**: Verify string presence and matching specifiers for all waypoint types across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity across all 9 supported locales.

### Test Case 5: Clean-Room Regression Suite (`TST-MAP-039.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-039.1` | Unit | `DEFAULT_WAYPOINT_MARKER_SIZE_DP` | `REQ-MAP-037.1` | Specified |
| `TST-MAP-039.2` | Unit | `createWaypointBadgeMarker` | `REQ-MAP-037.1`, `REQ-MAP-037.2` | Specified |
| `TST-MAP-039.3` | Contract | `RouteWaypointsLayer` | `REQ-MAP-037.3`, `REQ-MAP-037.4` | Specified |
| `TST-MAP-039.4` | Localization | `TranslationParityTest` | `REQ-MAP-037.5`, `REQ-UI-106` | Specified |
| `TST-MAP-039.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
