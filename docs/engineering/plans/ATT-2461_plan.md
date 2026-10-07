# Stage 3: Implementation Plan - ATT-2461: Improve waypoint marker visuals using Maki POI icon package

**Ticket**: [ATT-2461](https://atrainingtracker.atlassian.net/browse/ATT-2461)  
**Sub-task**: [ATT-2579](https://atrainingtracker.atlassian.net/browse/ATT-2579) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-MAP-033`  
**Test Mapping**: `TST-MAP-035`  
**Branch**: `feature/ATT-2461`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Background

In ATT-58, route waypoints and POIs (Points of Interest) from GPX and TCX courses were extracted, persisted in SQLite `route_waypoints`, and rendered on the map.

However, during real-world on-device testing (Sprint 2026-40.16 Joint Review), several visual and semantic limitations were identified:
1. **Low Marker Contrast**: Waypoints are currently rendered as bare 32 px vector drawables (`bitmapDescriptorFromVectorInternal`) without backgrounds, white halo rings, or contrast borders, making them difficult to distinguish over complex vector map tiles or satellite imagery.
2. **Missing Waypoint Rendering in Route Thumbnails**: In `PathPreviewMap.kt`, only polylines and start/end/apex markers are rendered; embedded route waypoints in `MapRoute.waypoints` are omitted.
3. **Keyword Classification False Positives ("Kreuzung" vs. "Gipfelkreuz")**: In `RouteWaypoint.kt`, `WaypointType.fromGpx` checks `text.contains("kreuz")` to detect mountain summits (`POI_SUMMIT`), causing trail intersections ("Kreuzung") to be misclassified as summits.
4. **Missing Dedicated Shelter Classification ("Unterstand")**: Trail shelters and huts ("Unterstand", "Schutzhütte", "Refuge", "Biwak") are currently either misclassified as rest benches (`POI_BENCH`) or restaurants/food (`POI_FOOD`).
5. **Icon Standardization**: Adopting Mapbox Maki (CC0 1.0 Universal / Public Domain, royalty-free) provides clean, outdoor-focused vector glyphs with consistent styling.

The goal of ATT-2461 is to:
1. Introduce `WaypointType.POI_SHELTER` with clean Maki shelter vector drawable (`ic_poi_shelter.xml`) and 9-language localization parity (`waypoint_type_shelter`).
2. Disambiguate GPX keyword heuristics: "Kreuzung" must not map to `POI_SUMMIT`, and shelter keywords must map to `POI_SHELTER`.
3. Implement high-contrast circular badge marker rendering (`createWaypointBadgeMarker` in `MapUtils.kt`) featuring category-specific vibrant tinting, crisp white halo ring, outer contrast border, and centered white Maki glyph.
4. Ensure consistent waypoint rendering across the interactive live map (`MapLayers.kt`), route inspection views, and route card thumbnails (`PathPreviewMap.kt`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-033` (*High-Contrast Maki POI Waypoint Badges, Shelter Classification & Substring Disambiguation*)
* **Test Mapping**: `TST-MAP-035` (*High-Contrast Maki POI Waypoint Badges, Shelter Classification & Substring Disambiguation Verification*)
* **Traceability Matrix**:
  - `REQ-MAP-033.1` (`POI_SHELTER` & Maki Asset) -> `TST-MAP-035.1`, `TST-MAP-035.3`
  - `REQ-MAP-033.2` (GPX/TCX Heuristics Disambiguation) -> `TST-MAP-035.1`, `TST-MAP-035.2`
  - `REQ-MAP-033.3` (High-Contrast Badge Rendering) -> `TST-MAP-035.3`
  - `REQ-MAP-033.4` (Interactive Map Layer Integration) -> `TST-MAP-035.3`
  - `REQ-MAP-033.5` (Route Card Thumbnail Integration) -> `TST-MAP-035.4`
  - `REQ-MAP-033.6` (9-Language Localization Parity) -> `TST-MAP-035.5`
  - System Invariants -> `TST-MAP-035.6` (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Full clean-room test suite (`./gradlew testDebugUnitTest`) must pass with 100% success rate.
2. **Database Schema Backward Compatibility**: `RoutesDbHelper.DB_VERSION` remains at 10. `route_waypoints` stores `type` as string (`type.name`), which falls back safely to `GENERIC` if an unrecognized string is encountered.
3. **Google Maps Lite Mode Deadlock Immunity (`REQ-STB-010`)**: Adding waypoint badges to `PathPreviewMap.kt` must operate within Lite Mode constraints without triggering RenderThread deadlocks.
4. **Interactive Map Marker Behavior**: Clicking a marker in `RouteWaypointsLayer` must continue to invoke `onWaypointClick` and open the native `InfoWindow` showing name, description, and altitude.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `RouteWaypoint.kt` (Domain & Model Layer)
* Add `POI_SHELTER(WaypointCategory.LANDMARK, R.drawable.ic_poi_shelter, R.string.waypoint_type_shelter)` to `WaypointType`.
* Update `fromGpx`:
  - Summit cross disambiguation:
    `val hasKreuz = text.contains("gipfelkreuz") || (text.contains("kreuz") && !text.contains("kreuzung"))`
  - Shelter matching:
    `text.contains("unterstand") || text.contains("schutzhütte") || text.contains("schutzhuette") || text.contains("shelter") || text.contains("refuge") || text.contains("biwak") || text.contains("bivouac") -> POI_SHELTER`
  - Remove "unterstand" from `POI_BENCH` matching.
  - Remove "schutzhütte" from `POI_FOOD` matching.
* Update `fromTcx`:
  - `"shelter" -> POI_SHELTER`.

### Component 2: Vector Drawable Asset (`ic_poi_shelter.xml`)
* Create `app/src/main/res/drawable/ic_poi_shelter.xml`:
  - 24x24dp viewport, Mapbox Maki shelter CC0 vector path (A-frame shelter roof and floor line), white fill (`#FFFFFFFF`).

### Component 3: Badge Marker Rendering Engine (`MapUtils.kt`)
* Implement:
  ```kotlin
  fun createWaypointBadgeMarker(
      context: Context,
      type: WaypointType,
      sizeDp: Int = 32
  ): BitmapDescriptor?
  ```
* Resolve category background color:
  - `POI_SUMMIT` -> Deep Amber `#E65100`
  - `POI_WATER` -> Azure Blue `#0288D1`
  - `POI_BENCH` -> Forest Green `#2E7D32`
  - `POI_SHELTER` -> Pine Teal `#00695C`
  - `POI_FOOD` -> Coral Orange `#EF6C00`
  - `POI_VIEWPOINT` -> Royal Purple `#6A1B9A`
  - `POI_DANGER` -> Warning Red `#D32F2F`
  - `POI_FIRST_AID` -> Crimson `#C62828`
  - `TURN_LEFT`, `TURN_RIGHT`, `TURN_STRAIGHT` -> Cobalt Blue `#1565C0`
  - `GENERIC` -> Slate `#546E7A`
* Draw sequence:
  1. Outer dark hairline stroke: radius $R$, stroke width 1 dp, color `#33000000`.
  2. Inner white halo ring: radius $R - 0.5\text{ dp}$, stroke width 2 dp, color `#FFFFFFFF`.
  3. Category-filled disc: radius $R - 2.5\text{ dp}$, fill style, category color.
  4. Centered white vector glyph: size ~16–18 dp, tinted white via `PorterDuff.Mode.SRC_IN`.
* Return `saveBitmapDescriptorFactoryFromBitmap(bitmap)`.

### Component 4: Interactive Map Layer (`MapLayers.kt`)
* In `RouteWaypointsLayer(waypoints, context, alpha, onWaypointClick)`:
  - Replace `bitmapDescriptorFromVectorInternal(ctx, waypoint.type.iconResId, 32, null)` with:
    `createWaypointBadgeMarker(ctx, waypoint.type, 32)`.
  - Cache bitmap descriptors per `waypoint.type` in a `remember` map or key to avoid recreating bitmaps per waypoint instance.

### Component 5: Route Card Thumbnail Integration (`PathPreviewMap.kt`)
* In `PathPreviewMap.kt`, within the `GoogleMap` composable block:
  ```kotlin
  if (path is MapRoute && path.waypoints.isNotEmpty()) {
      RouteWaypointsLayer(
          waypoints = path.waypoints,
          context = context
      )
  }
  ```

### Component 6: Localization Parity (9 Languages)
* Add `waypoint_type_shelter` to `strings.xml`:
  - `values/` (EN): `Shelter`
  - `values-de/` (DE): `Unterstand`
  - `values-es/` (ES): `Refugio`
  - `values-fr/` (FR): `Abri`
  - `values-it/` (IT): `Rifugio`
  - `values-ja/` (JA): `避難小屋`
  - `values-nl/` (NL): `Schuilplaats`
  - `values-pl/` (PL): `Schronienie`
  - `values-pt/` (PT): `Abrigo`

---

## 5. UI Consistency (Rule 23)

* **Reference Screen**: `ATrainingTrackerMap.kt`, `RouteOnMapScreen.kt`, and `RouteItem.kt`.
* **Reused Components**: `RouteWaypointsLayer` from `MapLayers.kt`, `PathPreviewMap` from `PathPreviewMap.kt`.
* **Theme Tokens**: Standard Android density pixel scaling, `Color.White`, `PorterDuff.Mode.SRC_IN`, standard 32 dp touch/glanceable marker dimensions.
* **Justification for Styles**: High-contrast circular badges ensure waypoints are distinguishable across all map tile providers, light/dark themes, and satellite imagery without obscuring route polylines.

---

## 6. Atomic Implementation Steps & Verification Plan

### Step 1: 9-Language Localization Parity
* Add `waypoint_type_shelter` in all 9 `strings.xml` files.
* **Verification**: `python3 -c "import xml.etree.ElementTree as ET; ..."` or `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`.

### Step 2: Vector Drawable Asset
* Create `app/src/main/res/drawable/ic_poi_shelter.xml` with clean Maki shelter vector path.
* **Verification**: Inspect XML syntax and ensure valid VectorDrawable tags.

### Step 3: Domain Model & Classification Refinements
* Update `RouteWaypoint.kt`:
  - Add `POI_SHELTER` to `WaypointType`.
  - Fix "Kreuzung" false positives and map shelter keywords in `fromGpx`.
  - Map "shelter" in `fromTcx`.
* **Verification**: Update `WaypointTypeClassificationTest.kt` and `SchwaebischeAlbWaypointIntegrationTest.kt`. Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.*"`.

### Step 4: High-Contrast Badge Marker Rendering
* Implement `createWaypointBadgeMarker` in `MapUtils.kt`.
* Update `RouteWaypointsLayer` in `MapLayers.kt` to use `createWaypointBadgeMarker`.
* **Verification**: Create `WaypointBadgeMarkerTest.kt` testing bitmap generation across all waypoint types.

### Step 5: Route Card Thumbnail Map Integration
* Update `PathPreviewMap.kt` to render `RouteWaypointsLayer` when `path is MapRoute && path.waypoints.isNotEmpty()`.
* Update `RouteItemWaypointContractTest.kt` to assert `PathPreviewMap.kt` contains `RouteWaypointsLayer`.
* **Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteItemWaypointContractTest"`.

### Step 6: Full Clean-Room Regression Verification
* Execute full test suite: `./gradlew testDebugUnitTest`.
* **Verification**: Zero failures, 100% pass rate.
