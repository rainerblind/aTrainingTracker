# Stage 1 Analysis: ATT-2461 - Improve waypoint marker visuals using Maki POI icon package

**Ticket**: [ATT-2461](https://atrainingtracker.atlassian.net/browse/ATT-2461)  
**Sub-task**: [ATT-2577](https://atrainingtracker.atlassian.net/browse/ATT-2577) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2461`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & Motivation

In ATT-58, route waypoints and POIs (Points of Interest) from GPX and TCX courses were extracted, persisted in `Routes.db` (`route_waypoints`), and rendered on the map (`MapLayers.kt -> RouteWaypointsLayer`).

However, during real-world and on-device testing (Sprint 2026-40.16 Joint Review), several visual and semantic limitations were identified:
1. **Low Marker Contrast**: Waypoint markers are currently rendered as bare 32 px vector drawables (`bitmapDescriptorFromVectorInternal(ctx, waypoint.type.iconResId, 32, null)`) without background, halo ring, or contrast border. Against satellite imagery or complex vector map tiles (forests, contours, urban grids), bare colored or dark icons blend into the background and become difficult to distinguish.
2. **Missing Waypoint Rendering in Route Thumbnails**: While `RouteItem.kt` constructs `MapRoute` with `waypoints = waypoints`, `PathPreviewMap.kt` only draws the route polyline and Start/End/Apex markers, omitting waypoints from route list cards and preview thumbnails.
3. **Keyword Classification False Positives ("Kreuzung" vs. "Gipfelkreuz")**: In `RouteWaypoint.kt`, `WaypointType.fromGpx` checks `text.contains("kreuz")` to detect mountain summits (`POI_SUMMIT`). This creates a false positive whenever a waypoint contains "Kreuzung" (crossroads / junction), misclassifying trail intersections as mountain summits.
4. **Missing Dedicated Shelter Classification ("Unterstand")**: Trail shelters, huts, and rain refuges ("Unterstand", "Schutzhütte", "Refuge", "Bivouac") are currently either misclassified as rest benches (`POI_BENCH`) or restaurants/food (`POI_FOOD`). A dedicated `POI_SHELTER` type is required for outdoor navigation.
5. **Icon Modernization**: Standardizing POI icons on Mapbox Maki (CC0 1.0 Universal / Public Domain, royalty-free for commercial use) ensures crisp, clean, outdoor-focused vector glyphs with consistent styling and line weights.

The goal of ATT-2461 is to:
- Introduce `WaypointType.POI_SHELTER` with clean Maki shelter vector drawable (`ic_poi_shelter.xml`) and 9-language localization parity (`waypoint_type_shelter`).
- Refine GPX keyword heuristics to disambiguate "Kreuzung" from summit crosses and map shelter keywords ("unterstand", "schutzhütte", "shelter", "refuge", "biwak") to `POI_SHELTER`.
- Implement high-contrast circular badge marker rendering (`createWaypointBadgeMarker` in `MapUtils.kt`) featuring category-specific vibrant tinting, crisp white halo ring, outer contrast border, and centered white Maki glyph.
- Ensure consistent waypoint rendering across the interactive live map (`MapLayers.kt`), route inspection views, and route card thumbnails (`PathPreviewMap.kt`).

---

## 2. Forensic Investigation & Gap Analysis

1. **`RouteWaypoint.kt` (`WaypointType` & Classification)**:
   - `WaypointType` enum contains: `POI_BENCH`, `POI_WATER`, `POI_SUMMIT`, `POI_FOOD`, `POI_VIEWPOINT`, `POI_FIRST_AID`, `POI_DANGER`, `TURN_LEFT`, `TURN_RIGHT`, `TURN_STRAIGHT`, `GENERIC`.
   - In `WaypointType.fromGpx(sym, type, name, desc)`:
     - Line 67: `text.contains("unterstand")` is currently mapped to `POI_BENCH`.
     - Line 68: `text.contains("kreuz")` matches "kreuzung", so "Kreuzung Waldweg" maps to `POI_SUMMIT`.
     - Line 69: `text.contains("schutzhütte")` is mapped to `POI_FOOD`.
   - Adding `POI_SHELTER(WaypointCategory.LANDMARK, R.drawable.ic_poi_shelter, R.string.waypoint_type_shelter)`:
     - High priority shelter matching: `text.contains("unterstand") || text.contains("schutzhütte") || text.contains("schutzhuette") || text.contains("shelter") || text.contains("refuge") || text.contains("biwak") || text.contains("bivouac") -> POI_SHELTER`.
     - Summit cross disambiguation: `val hasKreuz = text.contains("gipfelkreuz") || (text.contains("kreuz") && !text.contains("kreuzung"))`.
   - In `WaypointType.fromTcx(pointType)`:
     - Add `"shelter" -> POI_SHELTER`.
   - In `RoutesDatabaseManager.kt`:
     - Stored as string `type.name`. Reading uses `try { WaypointType.valueOf(typeStr) } catch { WaypointType.GENERIC }`. Adding `POI_SHELTER` is 100% backward and forward compatible without database migration.
   - In `TurnCue.kt`:
     - `TurnCue.fromWaypointType(type)` has `else -> null`, perfectly safe.

2. **Badge Rendering & Visual Hierarchy (`MapUtils.kt` & `MapLayers.kt`)**:
   - Currently, `RouteWaypointsLayer` invokes:
     `bitmapDescriptorFromVectorInternal(ctx, waypoint.type.iconResId, 32, null)`.
     This produces a transparent bitmap containing only the vector lines of the icon at its native fill color.
   - We introduce `createWaypointBadgeMarker(context: Context, type: WaypointType, sizeDp: Int = 32): BitmapDescriptor?`:
     - Base size: 32 dp (standard touch and glanceable size).
     - Geometry:
       1. Outer subtle dark contrast hairline / shadow ring (radius $R$, stroke 1 dp, `#33000000`) for high contrast against white and light map tiles.
       2. Inner crisp white halo ring (radius $R - 0.5\text{ dp}$, stroke 2 dp, `#FFFFFFFF`) ensuring separation from the background.
       3. Category-specific vibrant filled disc (radius $R - 2.5\text{ dp}$):
          - Summit: Deep Amber / Orange `#E65100`
          - Water: Azure Blue `#0288D1`
          - Bench: Forest Green `#2E7D32`
          - Shelter: Pine Teal `#00695C`
          - Food: Coral Orange `#EF6C00`
          - Viewpoint: Royal Purple `#6A1B9A`
          - Danger / Hazard: Warning Red `#D32F2F`
          - First Aid: Crimson `#C62828`
          - Turn Cues: Cobalt Blue `#1565C0`
          - Generic: Slate `#546E7A`
       4. Centered white vector glyph (~16–18 dp) drawn with `PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)`.
   - Caching: In `MapLayers.kt`, cache the generated `BitmapDescriptor` by `(waypoint.type, isDark)` to avoid repeated bitmap allocations during recomposition.

3. **Route Card Thumbnail Integration (`PathPreviewMap.kt`)**:
   - `RouteItem.kt` already packages waypoints into `MapRoute`:
     `MapRoute(..., waypoints = waypoints)`.
   - In `PathPreviewMap.kt`, rendering only draws `Polyline`, `start`, `end`, and `apex` markers.
   - By adding:
     ```kotlin
     if (path is MapRoute && path.waypoints.isNotEmpty()) {
         RouteWaypointsLayer(
             waypoints = path.waypoints,
             context = context
         )
     }
     ```
     all route card thumbnails in `RouteList` and route previews instantly render the high-contrast waypoint badges.

4. **Integration Test Archaeology**:
   - `SchwaebischeAlbWaypointIntegrationTest.kt` (line 147):
     `assertEquals("Unterstand", shelter.description)`
     `assertEquals(WaypointType.POI_BENCH, shelter.type)`
     Must be updated to `assertEquals(WaypointType.POI_SHELTER, shelter.type)`.
   - `WaypointTypeClassificationTest.kt` (line 16):
     `WaypointType.fromGpx(sym = "Circle, Red", type = "user", name = "Start Tour 13", desc = "Unterstand")`
     Must be updated to assert `WaypointType.POI_SHELTER`.
     New test methods must be added for "Kreuzung" vs. "Gipfelkreuz" and Maki shelter classification.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `POI_SHELTER` to `WaypointType` in `RouteWaypoint.kt`.
  2. Create clean Mapbox Maki-inspired Android vector drawable `ic_poi_shelter.xml`.
  3. Disambiguate GPX classification: "Kreuzung" does not map to `POI_SUMMIT`; "Unterstand" and shelter keywords map to `POI_SHELTER`.
  4. Implement `createWaypointBadgeMarker` in `MapUtils.kt` with white ring, contrast border, category color, and centered white glyph.
  5. Integrate badge marker rendering into `RouteWaypointsLayer` in `MapLayers.kt`.
  6. Integrate `RouteWaypointsLayer` into `PathPreviewMap.kt` when `path is MapRoute && path.waypoints.isNotEmpty()`.
  7. 9-language localization parity for `waypoint_type_shelter`.
  8. Update and expand unit/integration tests in `WaypointTypeClassificationTest.kt`, `SchwaebischeAlbWaypointIntegrationTest.kt`, and `RouteWaypointLayerTest.kt`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying SQLite database table structure (v10 schema remains untouched).
  - Altering polyline rendering or elevation profile calculation.
  - Modifying turn-by-turn prompt audio or countdown logic.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  - Refines Clause 1 & Clause 5 of `REQ-MAP-026` (*Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points*).
  - Formulates `REQ-MAP-033` (*High-Contrast Maki POI Waypoint Badges, Shelter Classification & Substring Disambiguation*).
* **Historical Origin & Commit Trace**:
  - `ATT-58` (Sprint `2026-40.14`, commit `7dfd4649`): Introduced initial waypoint parsing and bare vector rendering.
* **Root Reason for Existing Formulation**:
  - ATT-58 focused on end-to-end data pipeline (GPX/TCX parsing, SQLite persistence, and marker click callbacks). Markers were rendered directly from vector drawables without background discs or contrast borders. "Unterstand" was grouped under benches as a temporary fallback, and "kreuz" matching did not anticipate German compound words like "Kreuzung".
* **Preservation of Core Invariants**:
  - Database schema v10 (`route_waypoints`), foreign key cascade deletion, existing waypoint types, and 100% full-suite unit test pass rate remain preserved.

---

## 4. Architectural Strategy & High-Level Solution

1. **Model & Keyword Engine (`RouteWaypoint.kt`)**:
   - Add `POI_SHELTER(WaypointCategory.LANDMARK, R.drawable.ic_poi_shelter, R.string.waypoint_type_shelter)`.
   - Update `fromGpx`:
     - Disambiguate "kreuz": `val hasKreuz = text.contains("gipfelkreuz") || (text.contains("kreuz") && !text.contains("kreuzung"))`.
     - Shelter matching: check "unterstand", "schutzhütte", "schutzhuette", "shelter", "refuge", "biwak", "bivouac".
     - Remove "unterstand" from bench matching; remove "schutzhütte" from food matching.
   - Update `fromTcx`: `"shelter" -> POI_SHELTER`.

2. **Vector Asset (`ic_poi_shelter.xml`)**:
   - Mapbox Maki CC0 shelter icon converted into clean Android Vector Drawable (24x24dp viewport, white fill).

3. **Map Marker Graphics (`MapUtils.kt` & `MapLayers.kt`)**:
   - Implement `createWaypointBadgeMarker(context: Context, type: WaypointType, sizeDp: Int = 32): BitmapDescriptor?`.
   - Palette mapping per category/type.
   - In `RouteWaypointsLayer`, use `createWaypointBadgeMarker` with `remember(waypoint.type, context)`.

4. **Thumbnail Map Rendering (`PathPreviewMap.kt`)**:
   - When `path is MapRoute && path.waypoints.isNotEmpty()`, compose `RouteWaypointsLayer(path.waypoints, context)`.

5. **Localization Parity (9 Languages)**:
   - Add `waypoint_type_shelter` across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

---

## 5. System Invariants & Risk Assessment

* **System Invariants**:
  1. Zero regressions in `./gradlew testDebugUnitTest`.
  2. Backward compatibility of SQLite `route_waypoints` table without schema version bump.
  3. Interactive marker click opens native `InfoWindow` with name, description, and altitude.
  4. Route list thumbnails maintain Google Maps Lite Mode performance without memory leaks.
* **Risk Rating**: **LOW**
  - Self-contained UI presentation and classification refinement with zero risk to tracking or database storage.
