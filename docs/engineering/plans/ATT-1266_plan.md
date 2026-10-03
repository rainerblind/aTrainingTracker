# Implementation Plan - ATT-1266: [Map] Dark mode map styling for live route tracking and navigation

**Parent Ticket**: [ATT-1266](https://rainerblind.atlassian.net/browse/ATT-1266)  
**Parent Epic**: [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*)  
**Sub-task**: [ATT-1432](https://rainerblind.atlassian.net/browse/ATT-1432) (`[Impl-Plan]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-MAP-021`  
**Test ID**: `TST-MAP-023`  

---

## 1. Executive Summary & Technical Rationale

During outdoor night training, dusk rides, or dark-mode telemetry sessions, the existing Google Maps rendering in `ATrainingTrackerMap.kt` hardcodes `MapProperties(mapType = MapType.TERRAIN)`. This renders bright, white/light-tan terrain tiles that cause blinding screen glare, ruin athlete night vision adaptation, and drain significant battery power on AMOLED/OLED displays.

Google Maps SDK v2 rules state that custom styling via `MapStyleOptions` is **only** applied when the map type is `MapType.NORMAL`. When set to `MapType.TERRAIN` or `MapType.SATELLITE`, custom style JSON is ignored by the tile rendering engine.

To solve this cleanly, this implementation introduces an AMOLED-optimized dark vector tile styling engine:
1. `res/raw/map_style_dark.json`: Custom vector styling defining a deep AMOLED black base (`#121212`), navy-black water bodies (`#0A1118`), clear road hierarchies (`#212121` to `#383838`), muted grey labels (`#9E9E9E`), and hidden POI clutter.
2. `DarkMapStyle.kt`: A thread-safe, singleton-cached style loader with graceful fallback if JSON parsing fails at runtime.
3. `ATrainingTrackerMap.kt`: Dynamic resolution of `isDark` based on an optional `darkTheme: Boolean?` parameter or `MaterialTheme.colorScheme.surface.luminance() < 0.5f`. Automatically selects `MapType.NORMAL` + `DarkMapStyle` when dark, and preserves `MapType.TERRAIN` + null styling when light.
4. `MapLayers.kt` & `MapModels.kt`: Adaptive polyline contrast for the live tracking layer (`Color(0xFF00E5FF)` electric cyan on dark vs `Color.Blue` on light), while strictly preserving custom route colors, Strava orange, and spatial markers.

---

## 2. Traceability & Architectural Mapping

| Requirement / Standard | Addressed By | Architectural Mechanism |
|---|---|---|
| **`REQ-MAP-021`** (Dark Map Styling) | `res/raw/map_style_dark.json`, `DarkMapStyle.kt`, `ATrainingTrackerMap.kt` | Vector tile JSON definition applied to `GoogleMap` `MapProperties` when `isDark == true`. |
| **`TST-MAP-023`** (Verification Suite) | `DarkMapStyleTest.kt`, `MapThemeResolutionTest.kt`, `MapLayersStyleTest.kt` | Unit testing of JSON palette, singleton caching, luminance resolution, and polyline color hierarchy. |
| **`REQ-UI-101`** (Theme Compliance) | `ATrainingTrackerMap.kt` | Evaluates `darkTheme ?: (MaterialTheme.colorScheme.surface.luminance() < 0.5f)`. |
| **`REQ-UI-168`..`171`** (Cockpit Theming) | `SensorGridScreen.kt`, `TrackingTabsScreen.kt` | Recomposes `ATrainingTrackerMap` whenever cockpit theme changes (`ALWAYS_DARK`, `SYSTEM`, `ALWAYS_LIGHT`). |
| **`REQ-MAP-006`** (Map DSL Modularity) | `MapContentScope.kt`, `MapLayers.kt` | Layer composition retains existing scopes, z-index layering, and click handlers. |
| **`REQ-MAP-009`** (Live Telemetry Tracking) | `MapLayers.kt` (`LiveTrackLayer`) | Active tracking polyline adapts to `#00E5FF` electric cyan on dark backgrounds without modifying raw GPS coordinates. |

---

## 3. Phase-by-Phase Implementation Steps

### Phase 1: Vector Tile JSON Asset Creation (`res/raw/map_style_dark.json`)
- Create raw resource file `app/src/main/res/raw/map_style_dark.json`.
- Configure rule-sets:
  - `all`: elements `geometry` -> `color: #121212`.
  - `all`: elements `labels.text.stroke` -> `color: #121212`.
  - `all`: elements `labels.text.fill` -> `color: #9e9e9e`.
  - `administrative.locality`: elements `labels.text.fill` -> `color: #bdbdbd`.
  - `poi`: `visibility: "off"`.
  - `road`: elements `geometry` -> `color: #212121`.
  - `road`: elements `geometry.stroke` -> `color: #1b1b1b`.
  - `road.highway`: elements `geometry` -> `color: #383838`.
  - `transit`: `visibility: "off"`.
  - `water`: elements `geometry` -> `color: #0a1118`.
  - `water`: elements `labels.text.fill` -> `color: #4e5d6c`.

### Phase 2: Singleton Style Manager (`DarkMapStyle.kt`)
- Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/DarkMapStyle.kt`.
- Implement singleton caching for `MapStyleOptions`:
  - `getMapStyleOptions(context: Context): MapStyleOptions?`: Checks volatile cache first. Synchronizes, parses `R.raw.map_style_dark` via `MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)`, and caches reference.
  - Fallback: Catches any `Resources.NotFoundException` or parsing error, logs a warning, and returns `null` so the map defaults safely to unstyled `MapType.NORMAL` without crashing.
  - `clearCache()`: Utility to reset cache in tests.
  - `parseStyleJson(json: String): MapStyleOptions?`: Helper for parsing from string.

### Phase 3: Theme Resolution & Dynamic `MapProperties` (`ATrainingTrackerMap.kt`)
- In `ATrainingTrackerMap.kt`:
  - Add optional parameter: `darkTheme: Boolean? = null`.
  - Resolve dark state:
    ```kotlin
    val isDark = darkTheme ?: (MaterialTheme.colorScheme.surface.luminance() < 0.5f)
    ```
  - Resolve dynamic map properties:
    ```kotlin
    val mapProperties = remember(isDark, context) {
        if (isDark) {
            MapProperties(
                mapType = MapType.NORMAL,
                mapStyleOptions = DarkMapStyle.getMapStyleOptions(context)
            )
        } else {
            MapProperties(
                mapType = MapType.TERRAIN,
                mapStyleOptions = null
            )
        }
    }
    ```
  - Pass `mapProperties` into `GoogleMap(properties = mapProperties, ...)`.
  - Provide `isDark` down the Compose tree via `LocalMapStyle provides style.copy(isDark = isDark)`.

### Phase 4: High-Contrast Polyline Hierarchy in DSL (`MapModels.kt`, `MapLayers.kt`)
- In `MapModels.kt`:
  - Add `val isDark: Boolean = false` to data class `MapStyle`.
- In `MapLayers.kt`:
  - Update `LiveTrackLayer(path: List<LatLng>)`:
    ```kotlin
    val style = LocalMapStyle.current
    val liveTrackColor = if (style.isDark) Color(0xFF00E5FF) else Color.Blue
    XRayPolyline(
        points = path,
        color = liveTrackColor,
        width = style.trackWidth,
        ...
    )
    ```
  - Invariant preservation: Confirm `MappablePathLayer` continues to use `path.color` for custom routes and `TTColor.StravaOrange` for segments without overriding user selections.

### Phase 5: Verification & Unit Tests
- Create `DarkMapStyleTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/`:
  - Validate JSON syntax, background `#121212`, water `#0A1118`, POI hidden, text labels `#9E9E9E`.
  - Validate singleton caching idempotency.
  - Validate graceful error handling on corrupt input.
- Create `MapThemeResolutionTest.kt`:
  - Validate `isDark == true` -> `MapType.NORMAL` with `DarkMapStyle`.
  - Validate `isDark == false` -> `MapType.TERRAIN` with `null`.
  - Validate luminance thresholding across black, dark surface, and light surface.
- Create `MapLayersStyleTest.kt`:
  - Validate `LiveTrackLayer` color selection (`#00E5FF` vs `Color.Blue`).
  - Validate custom route color preservation.
- Execute full test suite: `./gradlew testDebugUnitTest`.

---

## 4. Preserved Invariants & Boundary Verification

1. **Light Mode Parity**: When `isDark == false`, `mapType` remains `MapType.TERRAIN` and `mapStyleOptions` is `null`. Zero regression to ambient daylight map rendering.
2. **User Route Color Integrity**: User-customized track/route colors set on `MappablePath.color` are preserved identically.
3. **Telemetry & Pin Invariants**: Coordinate precision (`LatLng`), start/end pins (`TTColor.StartPoint`, `TTColor.EndPoint`), apex points, and segment overlays are completely preserved.
4. **Performance & Memory Bounds**: Style parsing occurs exactly once; RAM usage is bounded under 10 KB; parse time under 3 ms; zero disk I/O on recomposition.
5. **Fail-Safe Crash Immunity**: Any style parsing failure degrades gracefully to unstyled `MapType.NORMAL` with logged warnings, avoiding application crash during tracking.

---

## 5. Verification Strategy & Gate Criteria

| Phase | Gate / Artifact | Verification Criteria |
|---|---|---|
| **Stage 3** | `ATT-1432` Plan Review | Agent 2 Gate 3 audit recommendation (`RECOMMEND PASS`), Human approval. |
| **Stage 4** | Implementation & Tests | `./gradlew testDebugUnitTest` 100% pass, walkthrough deliverable. |
| **Stage 5** | Test & Release | Verification of `REQ-MAP-021` and `TST-MAP-023` in `docs/requirements.md` and `docs/tests.md`. |
