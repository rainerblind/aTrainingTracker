# Stage 2: Requirement & Test Specification - ATT-2461: Improve waypoint marker visuals using Maki POI icon package

**Ticket**: [ATT-2461](https://atrainingtracker.atlassian.net/browse/ATT-2461)  
**Sub-task**: [ATT-2578](https://atrainingtracker.atlassian.net/browse/ATT-2578) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-MAP-033` (*High-Contrast Maki POI Waypoint Badges, Shelter Classification & Substring Disambiguation*)  
**Test Spec ID**: `TST-MAP-035`  
**Branch**: `feature/ATT-2461`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (REQ-MAP-033)

### 1.1 Problem Statement & Rationale
During real-world on-device reviews (Sprint 2026-40.16 Joint Review), waypoint markers rendered along route paths were found to suffer from low contrast when displayed directly from bare 32 px vector drawables over complex vector or satellite map tiles. Furthermore, waypoints encapsulated in `MapRoute` were not rendered in `PathPreviewMap.kt`, leaving route card thumbnails without landmark indicators. Semantically, substring matching for mountain summits mistakenly classified German trail intersections ("Kreuzung") as summits ("kreuz"), and trail shelters / refuges ("Unterstand", "Schutzhütte") lacked a dedicated POI type, falling back to benches or food.

The system SHALL provide high-contrast circular badge markers with category-specific vibrant tinting, crisp white halo rings, outer contrast borders, and centered Mapbox Maki glyphs across both interactive map views and route card thumbnails, introduce `WaypointType.POI_SHELTER`, disambiguate substring classification false positives, and ensure 100% 9-language localization parity.

### 1.2 Functional & Architectural Requirements

1. *`WaypointType.POI_SHELTER` & Maki Vector Asset*:
   • `RouteWaypoint.kt` SHALL define `POI_SHELTER(WaypointCategory.LANDMARK, R.drawable.ic_poi_shelter, R.string.waypoint_type_shelter)`.
   • The system SHALL provide `ic_poi_shelter.xml` as a clean Android Vector Drawable (24x24dp viewport, white fill) modeled after the Mapbox Maki CC0 shelter icon.
   • `TurnCue.fromWaypointType(type)` SHALL continue returning `null` for landmark POIs including `POI_SHELTER`.
   • Persistence in `RoutesDatabaseManager.kt` via `type.name` string serialization SHALL retain full forward and backward compatibility without database migration.

2. *GPX / TCX Heuristic Parsing & Disambiguation*:
   • In `WaypointType.fromGpx(sym, type, name, desc)`:
     - Substring "Kreuzung" (trail intersection / junction) SHALL NOT be classified as a summit (`POI_SUMMIT`). Mountain summit cross detection SHALL require: `val hasKreuz = text.contains("gipfelkreuz") || (text.contains("kreuz") && !text.contains("kreuzung"))`.
     - Shelter keywords ("unterstand", "schutzhütte", "schutzhuette", "shelter", "refuge", "biwak", "bivouac") SHALL be classified as `POI_SHELTER`.
     - "unterstand" SHALL be removed from bench matching (`POI_BENCH`); "schutzhütte" SHALL be removed from food matching (`POI_FOOD`).
   • In `WaypointType.fromTcx(pointType)`:
     - TCX point type `"shelter"` SHALL map to `POI_SHELTER`.

3. *High-Contrast Circular Badge Marker Rendering (`MapUtils.kt`)*:
   • The system SHALL provide `createWaypointBadgeMarker(context: Context, type: WaypointType, sizeDp: Int = 32): BitmapDescriptor?`:
     - Badge Diameter: `sizeDp = 32` (converted to pixels via display density).
     - Geometry:
       1. Outer subtle dark contrast hairline / shadow ring (radius $R$, stroke 1 dp, `#33000000`) for high contrast over light vector map tiles.
       2. Inner crisp white halo ring (radius $R - 0.5\text{ dp}$, stroke 2 dp, `#FFFFFFFF`) ensuring separation from dark and satellite map tiles.
       3. Category-specific vibrant filled disc (radius $R - 2.5\text{ dp}$):
          - Summit (`POI_SUMMIT`): Deep Amber `#E65100`
          - Water (`POI_WATER`): Azure Blue `#0288D1`
          - Bench (`POI_BENCH`): Forest Green `#2E7D32`
          - Shelter (`POI_SHELTER`): Pine Teal `#00695C`
          - Food (`POI_FOOD`): Coral Orange `#EF6C00`
          - Viewpoint (`POI_VIEWPOINT`): Royal Purple `#6A1B9A`
          - Hazard (`POI_DANGER`): Warning Red `#D32F2F`
          - First Aid (`POI_FIRST_AID`): Crimson `#C62828`
          - Turn Cues (`TURN_LEFT`, `TURN_RIGHT`, `TURN_STRAIGHT`): Cobalt Blue `#1565C0`
          - Generic (`GENERIC`): Slate `#546E7A`
       4. Centered white vector glyph (~16–18 dp) drawn with `PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)`.

4. *Interactive Map Layer Integration (`MapLayers.kt`)*:
   • `RouteWaypointsLayer` SHALL resolve icon descriptors via `createWaypointBadgeMarker(ctx, waypoint.type, 32)` instead of bare vector bitmaps.
   • Markers SHALL continue rendering above polylines ($Z \ge 50\text{f}$) and retain clickable InfoWindows displaying waypoint name, description, and altitude.

5. *Route Thumbnail & Preview Map Integration (`PathPreviewMap.kt`)*:
   • In `PathPreviewMap.kt`, when `path is MapRoute && path.waypoints.isNotEmpty()`, the composable SHALL render `RouteWaypointsLayer(waypoints = path.waypoints, context = context)`.
   • Waypoint markers in preview maps SHALL render at the identical badge visual quality without crashing Lite Mode.

6. *100% 9-Language Localization Parity*:
   • String resource `waypoint_type_shelter` SHALL be defined across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

---

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Disambiguation of "Kreuzung" and "Gipfelkreuz")**:
  * *Given* a GPX waypoint with name "Kreuzung Waldweg" or description "Kreuzung am Waldrand",
  * *When* evaluated by `WaypointType.fromGpx`,
  * *Then* it SHALL NOT be classified as `POI_SUMMIT` and SHALL fall back to `GENERIC`.
  * *Given* a GPX waypoint with name "Gipfelkreuz auf dem Jusi" or "Gipfelkreuz Hochwang",
  * *When* evaluated by `WaypointType.fromGpx`,
  * *Then* it SHALL be classified as `POI_SUMMIT`.

* **Criterion 2 (Shelter Classification for "Unterstand" and "Schutzhütte")**:
  * *Given* a GPX waypoint with name or description containing "Unterstand", "Schutzhütte", "Schutzhuette", or "Refuge",
  * *When* evaluated by `WaypointType.fromGpx`,
  * *Then* it SHALL be classified as `POI_SHELTER`.
  * *Given* a TCX course point with point type "shelter",
  * *When* evaluated by `WaypointType.fromTcx`,
  * *Then* it SHALL be classified as `POI_SHELTER`.

* **Criterion 3 (High-Contrast Circular Badge Marker Rendering)**:
  * *Given* a waypoint of any `WaypointType`,
  * *When* `createWaypointBadgeMarker` generates a marker bitmap,
  * *Then* the bitmap SHALL contain a category-tinted disc, an outer dark hairline stroke, a crisp white halo ring, and a centered white glyph.

* **Criterion 4 (Thumbnail Previews Render Waypoints)**:
  * *Given* a route containing waypoints displayed in `RouteItem` / `PathPreviewMap`,
  * *When* the preview map renders,
  * *Then* `RouteWaypointsLayer` SHALL be composed, rendering waypoint badges along the route polyline.

* **Criterion 5 (9-Language Parity)**:
  * *Given* all 9 supported locales,
  * *When* `TranslationParityTest` executes,
  * *Then* `waypoint_type_shelter` SHALL exist in all 9 `strings.xml` files with zero AAPT2 formatting errors.

---

### 1.4 System Invariants
* **Invariant 1**: Database schema v10 in `RoutesDatabaseManager` MUST NOT be bumped or altered.
* **Invariant 2**: Google Maps Lite Mode in `PathPreviewMap` (`REQ-STB-010`) MUST NOT encounter thread deadlocks or crashes.
* **Invariant 3**: 100% full-suite unit test pass rate (`./gradlew testDebugUnitTest`) MUST be preserved.

---

### 1.5 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  - Refines Clause 1 & Clause 5 of `REQ-MAP-026` (*Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points*).
  - Formulates `REQ-MAP-033` (*High-Contrast Maki POI Waypoint Badges, Shelter Classification & Substring Disambiguation*).
* **Historical Origin & Commit Trace**:
  - `ATT-58` (Sprint `2026-40.14`, commit `7dfd4649`): Introduced initial waypoint parsing and bare vector rendering.
* **Root Reason for Existing Formulation**:
  - ATT-58 established basic parsing and persistence pipelines, rendering raw vector drawables without background discs. Substring matching did not account for compound words such as "Kreuzung".
* **Preservation of Core Invariants**:
  - Schema v10 cascade deletion, TCX course point handling, turn cues, and 100% test pass rate remain strictly preserved.

---

## 2. Test Specification (TST-MAP-035)

### Test Case 1: GPX & TCX Heuristic Disambiguation Unit Tests (`TST-MAP-035.1`)
* **Scope**: Pure Kotlin Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/WaypointTypeClassificationTest.kt`
* **Preconditions**: Test instances of `WaypointType.fromGpx` and `WaypointType.fromTcx`.
* **Action**:
  1. Test "Kreuzung": Pass name "Kreuzung Waldweg", assert result is `GENERIC` (NOT `POI_SUMMIT`).
  2. Test "Gipfelkreuz": Pass name "Gipfelkreuz Jusi", assert result is `POI_SUMMIT`.
  3. Test "Kreuz" without "ung": Pass name "Kreuz am Berg", assert result is `POI_SUMMIT`.
  4. Test "Unterstand": Pass desc "Unterstand", assert result is `POI_SHELTER` (NOT `POI_BENCH`).
  5. Test "Schutzhütte": Pass name "Schutzhütte am Waldrand", assert result is `POI_SHELTER` (NOT `POI_FOOD`).
  6. Test TCX "shelter": Pass `fromTcx("shelter")`, assert result is `POI_SHELTER`.
* **Expected Result**: All keywords disambiguate cleanly with 100% accuracy.

### Test Case 2: Real-World Multi-File GPX Integration Test (`TST-MAP-035.2`)
* **Scope**: File Parsing & Integration Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/SchwaebischeAlbWaypointIntegrationTest.kt`
* **Preconditions**: Sample GPX file `Nebelhoehle.gpx`.
* **Action**:
  1. Parse `Nebelhoehle.gpx`.
  2. Verify waypoint with description "Unterstand" is extracted and asserted as `WaypointType.POI_SHELTER`.
* **Expected Result**: Nebelhöhle shelter waypoint correctly resolves to `POI_SHELTER`.

### Test Case 3: High-Contrast Waypoint Badge Marker Unit & Bitmap Rendering Tests (`TST-MAP-035.3`)
* **Scope**: Robolectric / Android Graphic Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/WaypointBadgeMarkerTest.kt`
* **Preconditions**: Android context with display metrics.
* **Action**:
  1. Invoke `createWaypointBadgeMarker(context, WaypointType.POI_SHELTER, 32)`.
  2. Assert non-null `BitmapDescriptor`.
  3. Iterate all `WaypointType.values()`, assert non-null marker generated for every type.
* **Expected Result**: Every waypoint type generates a crisp, high-contrast badge bitmap.

### Test Case 4: Route Card Thumbnail Map Integration & Contract Tests (`TST-MAP-035.4`)
* **Scope**: Composable / Contract Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItemWaypointContractTest.kt`
* **Preconditions**: `MapRoute` with waypoints list.
* **Action**:
  1. Verify `MapRoute` with `POI_SHELTER` waypoint retains the waypoint.
  2. Verify `PathPreviewMap.kt` contains `RouteWaypointsLayer` invocation when `path is MapRoute && path.waypoints.isNotEmpty()`.
* **Expected Result**: Route previews and thumbnails reliably display waypoints.

### Test Case 5: 9-Language Localization Audit (`TST-MAP-035.5`)
* **Scope**: Static XML Audit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Preconditions**: `strings.xml` in `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Action**:
  1. Assert string key `waypoint_type_shelter` exists in all 9 locale directories.
  2. Assert no placeholder formatting discrepancies or raw `&#10;` line break defects.
* **Expected Result**: 100% translation parity across all 9 languages.

### Test Case 6: Clean-Room Full Suite Regression Execution (`TST-MAP-035.6`)
* **Scope**: Full Gradle Unit Test Execution
* **Action**:
  1. Execute `./gradlew testDebugUnitTest`.
* **Expected Result**: 100% pass rate across the entire repository.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case ID | Test Class / Verification Method | Status |
|:---|:---|:---|:---|
| **REQ-MAP-033.1** (`POI_SHELTER` & Maki Asset) | `TST-MAP-035.1`, `TST-MAP-035.3` | `WaypointTypeClassificationTest.kt`, `WaypointBadgeMarkerTest.kt` | Specified |
| **REQ-MAP-033.2** (GPX/TCX Heuristics Disambiguation) | `TST-MAP-035.1`, `TST-MAP-035.2` | `WaypointTypeClassificationTest.kt`, `SchwaebischeAlbWaypointIntegrationTest.kt` | Specified |
| **REQ-MAP-033.3** (High-Contrast Badge Rendering) | `TST-MAP-035.3` | `WaypointBadgeMarkerTest.kt` | Specified |
| **REQ-MAP-033.4** (Interactive Map Layer Integration) | `TST-MAP-035.3` | `WaypointBadgeMarkerTest.kt` | Specified |
| **REQ-MAP-033.5** (Route Card Thumbnail Integration) | `TST-MAP-035.4` | `RouteItemWaypointContractTest.kt` | Specified |
| **REQ-MAP-033.6** (9-Language Localization Parity) | `TST-MAP-035.5` | `TranslationParityTest.kt` | Specified |
| **System Invariants** (Zero Regressions) | `TST-MAP-035.6` | `./gradlew testDebugUnitTest` | Specified |
