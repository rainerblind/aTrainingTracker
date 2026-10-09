# Stage 3: Implementation Plan - ATT-2931: Add layers control menu to toggle routes, segments, markers, and track on general map

**Ticket**: [ATT-2931](https://atrainingtracker.atlassian.net/browse/ATT-2931)  
**Sub-task**: [ATT-2961](https://atrainingtracker.atlassian.net/browse/ATT-2961) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Requirement Mapping**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*)  
**Test Mapping**: `TST-UI-282` (*General Map Layer Visibility Controls & Preference Persistence Verification*)  
**Branch**: `feature/ATT-2931`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

On the general map (`MapScreenWithTrack.kt`, accessible via the "Karte" drawer menu and during active workout tracking), multiple spatial entities are rendered simultaneously:
- Stored routes (`uiState.routes`)
- Strava and local segments (`uiState.segments`)
- Favorite locations / Lieblingsorte (`displayLocations` from `uiState.knownLocations`)
- Waypoint and workout start markers (`uiState.markers`)
- Live GPS recording track (`uiState.currentTrack`)

As athletes accumulate saved routes, star Strava segments, and configure favorite locations, the general map canvas becomes visually overcrowded. Overlapping polyline ribbons and POI pins compete for visual hierarchy and obscure the athlete's current location and live track breadcrumbs. Currently, `MapScreenWithTrack.kt` renders all spatial layers unconditionally with zero visibility controls.

To resolve this, we will introduce a map layers control menu on the general map, adopting the established UI pattern and ergonomic design from `TrackOnMapScreen.kt` and `RouteOnMapScreen.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*)
* **Test Mapping**: `TST-UI-282` (*General Map Layer Visibility Controls & Preference Persistence Verification*)
  * `TST-UI-282.1`: Unit & contract test verifying `GeneralMapLayer` enum declaration (`ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, `TRACK`) and default state enabling all layers.
  * `TST-UI-282.2`: Unit & contract test verifying `MapFragmentWithTrackViewModel` layer toggling and `SharedPreferences` persistence.
  * `TST-UI-282.3`: Unit & contract test verifying `MapScreenWithTrack.kt` UI structure, floating action button, dropdown menu, and conditional map canvas delegation.
  * `TST-UI-282.4`: 9-language localization audit verifying string resources (`map_layers`, `map_layer_routes`, `map_layer_segments`, `map_layer_locations`, `map_layer_track`) across all 9 supported application locales.
  * `TST-UI-282.5`: Full clean-room regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Persistent User Customization**: User layer toggle selections MUST survive application restarts via `SharedPreferences`.
2. **Background Recording Invariant**: Toggling the Live Track layer off MUST NOT stop, pause, or interfere with active GPS workout tracking, telemetry recording, or start marker computation in `MapFragmentWithTrackViewModel`.
3. **Detail Sheet Inspection Preservation**: When a layer is enabled, tapping any route, segment, or location pin MUST continue to open its respective peek sheet or bottom sheet dialog as specified in `REQ-UI-180` and `REQ-UI-315`.
4. **Ergonomic & Visual Identity Parity**: Floating button dimensions (`44.dp`), shape (`CircleShape`), elevation (`shadowElevation = 6.dp`), alpha (`TTAlpha.Overlay`), and dropdown menu item layout (checkbox + `12.dp` color swatch + text) MUST strictly match `TrackOnMapScreen.kt` and `RouteOnMapScreen.kt`.
5. **Localization Parity Invariant**: All newly introduced string resources MUST exist across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing keys.
6. **Subtask Direct Completion**: Subtask `ATT-2961` transitions directly to `Erledigt` via transition `freigabe` upon review agent audit pass.
7. **Parent Human Decision Gate**: Parent ticket `ATT-2931` terminal state is strictly `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `GeneralMapLayer.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
Create a new file defining the layer enumeration:
```kotlin
package com.atrainingtracker.trainingtracker.ui.map

enum class GeneralMapLayer {
    ROUTES,
    SEGMENTS,
    KNOWN_LOCATIONS,
    TRACK
}
```

### Component 2: `MapFragmentWithTrackViewModel.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
1. Inject / obtain `SharedPreferences` via `PreferenceManager.getDefaultSharedPreferences(application)`.
2. Define preference key: `PREF_GENERAL_MAP_ENABLED_LAYERS = "pref_general_map_enabled_layers"`.
3. Expose state flow `enabledLayers: StateFlow<Set<GeneralMapLayer>>`.
4. Implement initialization:
   ```kotlin
   private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
   private val _enabledLayers = MutableStateFlow(loadEnabledLayers())
   val enabledLayers: StateFlow<Set<GeneralMapLayer>> = _enabledLayers.asStateFlow()

   private fun loadEnabledLayers(): Set<GeneralMapLayer> {
       val saved = prefs.getStringSet(PREF_GENERAL_MAP_ENABLED_LAYERS, null)
           ?: return GeneralMapLayer.entries.toSet()
       return saved.mapNotNull { name ->
           runCatching { GeneralMapLayer.valueOf(name) }.getOrNull()
       }.toSet()
   }

   fun toggleLayer(layer: GeneralMapLayer) {
       val current = _enabledLayers.value
       val updated = if (layer in current) current - layer else current + layer
       _enabledLayers.value = updated
       prefs.edit()
           .putStringSet(PREF_GENERAL_MAP_ENABLED_LAYERS, updated.map { it.name }.toSet())
           .apply()
   }
   ```

### Component 3: `MapScreenWithTrack.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
1. Collect `enabledLayers by viewModel.enabledLayers.collectAsStateWithLifecycle()`.
2. Wrap `ATrainingTrackerMap` in a `Box(modifier = Modifier.fillMaxSize())` inside the `BottomSheetScaffold` body.
3. In `ATrainingTrackerMap` content lambda, gate layers:
   ```kotlin
   if (GeneralMapLayer.KNOWN_LOCATIONS in enabledLayers) {
       knownLocations(displayLocations, onLocationClick = { id ->
           selectedSegmentId = null
           selectedRouteId = null
           selectedLocationId = id
       })
   }
   if (GeneralMapLayer.SEGMENTS in enabledLayers) {
       segments(uiState.segments, onSegmentClick = { id ->
           selectedRouteId = null
           selectedLocationId = null
           selectedSegmentId = id
       })
   }
   if (GeneralMapLayer.ROUTES in enabledLayers) {
       routes(uiState.routes, onRouteClick = { id ->
           selectedSegmentId = null
           selectedLocationId = null
           selectedRouteId = id
       })
   }
   if (GeneralMapLayer.TRACK in enabledLayers) {
       markers(uiState.markers)
       liveTrack(uiState.currentTrack)
   }
   ```
4. Render the floating layers button and dropdown menu:
   - Position: `Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 16.dp, end = 16.dp)`.
   - Surface: `size = 44.dp`, `shape = CircleShape`, `color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay)`, `shadowElevation = 6.dp`, `tonalElevation = 2.dp`.
   - Icon: `Icons.Default.Layers`, `size = 22.dp`, tint dynamically resolving to `MaterialTheme.colorScheme.primary` when any layer is disabled, else `MaterialTheme.colorScheme.onSurface`.
   - DropdownMenu: Checkbox + `12.dp` color swatch (`RoundedCornerShape(2.dp)`) + localized label for each of the 4 layers.

### Component 4: String Resources (9-Language Parity)
Add the following keys to all 9 `strings.xml` files:
* `map_layers`:
  - EN: "Map Layers"
  - DE: "Kartenebenen"
  - ES: "Capas del mapa"
  - FR: "Calques de la carte"
  - IT: "Livelli mappa"
  - JA: "マップレイヤー"
  - NL: "Kaartlagen"
  - PL: "Warstwy mapy"
  - PT: "Camadas do mapa"
* `map_layer_routes`:
  - EN: "Routes"
  - DE: "Routen"
  - ES: "Rutas"
  - FR: "Itinéraires"
  - IT: "Percorsi"
  - JA: "ルート"
  - NL: "Routes"
  - PL: "Trasy"
  - PT: "Percursos"
* `map_layer_segments`:
  - EN: "Segments"
  - DE: "Segmente"
  - ES: "Segmentos"
  - FR: "Segments"
  - IT: "Segmenti"
  - JA: "セグメント"
  - NL: "Segmenten"
  - PL: "Segmenty"
  - PT: "Segmentos"
* `map_layer_locations`:
  - EN: "Favorite Locations"
  - DE: "Lieblingsorte"
  - ES: "Ubicaciones favoritas"
  - FR: "Lieux favoris"
  - IT: "Luoghi preferiti"
  - JA: "お気に入りの場所"
  - NL: "Favoriete locaties"
  - PL: "Ulubione lokalizacje"
  - PT: "Locais favoritos"
* `map_layer_track`:
  - EN: "Recorded Track"
  - DE: "Aufzeichnung"
  - ES: "Track grabado"
  - FR: "Trace enregistrée"
  - IT: "Traccia registrata"
  - JA: "記録トラック"
  - NL: "Opgenomen track"
  - PL: "Zarejestrowany ślad"
  - PT: "Trilha gravada"

### Component 5: Test Classes
1. `GeneralMapLayersContractTest.kt`:
   - Verify `GeneralMapLayer` enum declaration and values.
   - Verify `MapFragmentWithTrackViewModel` toggling and preference persistence.
   - Verify `MapScreenWithTrack.kt` AST / structural contract.
2. `GeneralMapLayersLocalizationTest.kt`:
   - Verify all 5 strings are present and non-empty in all 9 `strings.xml` resource directories.

### UI Consistency (Rule 23)
* **Closest Existing Reference Screen**: `RouteOnMapScreen.kt` and `TrackOnMapScreen.kt`.
* **Reused Components**: Floating circular `Surface` with `TTAlpha.Overlay`, `Icons.Default.Layers`, `DropdownMenu`, `DropdownMenuItem`, `Checkbox`, `RoundedCornerShape(2.dp)` color legend swatch.
* **Theme Tokens**: `MaterialTheme.colorScheme.surface`, `MaterialTheme.colorScheme.primary`, `MaterialTheme.colorScheme.onSurface`, `TTColor.RouteSelected`, `TTColor.StravaOrange`, `MaterialTheme.colorScheme.tertiary`, `TrackType.BEST.color`, `TTAlpha.Overlay`, `TTAlpha.High`, `TTAlpha.Disabled`.
* **One-Off Styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Gate 3 Audit Sign-Off
* Audit Gate 3 with `review_agent.py`.
* Transition subtask `ATT-2961` to `Erledigt` via `freigabe`.
* Manually advance parent `ATT-2931` to `Implementation` (`python3 tools/jira_util.py move ATT-2931 implementation`).

### Step 2: Create `GeneralMapLayer.kt`
* Define `enum class GeneralMapLayer { ROUTES, SEGMENTS, KNOWN_LOCATIONS, TRACK }`.

### Step 3: Update `MapFragmentWithTrackViewModel.kt`
* Add `enabledLayers: StateFlow<Set<GeneralMapLayer>>` backed by `SharedPreferences`.
* Add `toggleLayer(layer: GeneralMapLayer)`.

### Step 4: Add String Resources Across All 9 Locales
* Populate `map_layers`, `map_layer_routes`, `map_layer_segments`, `map_layer_locations`, `map_layer_track` across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### Step 5: Update `MapScreenWithTrack.kt`
* Wrap map in `Box`.
* Add floating layers button and dropdown menu.
* Apply conditional visibility gates to `knownLocations()`, `segments()`, `routes()`, and `markers()` + `liveTrack()`.

### Step 6: Create Contract & Localization Tests
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/GeneralMapLayersContractTest.kt`.
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/GeneralMapLayersLocalizationTest.kt`.

### Step 7: Execute Targeted Tests
* Run:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.GeneralMapLayers*"
  ```

---

## 6. Clean-Room Regression Verification (Stage 5)
* Run full regression test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Author walkthrough document: `docs/engineering/walkthroughs/ATT-2931_walkthrough.md`.
* Synchronize living documentation: update `REQ-UI-322` and `TST-UI-282` to `Verified`.
* Merge feature branch into `sprint/2026-41.6` via `--no-ff`.
* Manually transition parent `ATT-2931` to `Final Review (Human)`.
