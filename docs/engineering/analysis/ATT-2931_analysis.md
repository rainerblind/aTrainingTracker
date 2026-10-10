# Stage 1 Analysis: ATT-2931 - Add layers control menu to toggle routes, segments, markers, and track on general map

**Ticket**: [ATT-2931](https://atrainingtracker.atlassian.net/browse/ATT-2931)  
**Sub-task**: [ATT-2959](https://atrainingtracker.atlassian.net/browse/ATT-2959) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.6`  
**Branch**: `feature/ATT-2931`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

On the general map (`MapScreenWithTrack.kt`, accessible via the "Karte" drawer menu and during active workout tracking), multiple spatial entities are rendered simultaneously:
- Stored routes (`uiState.routes`)
- Strava / local segments (`uiState.segments`)
- Favorite locations / Lieblingsorte (`displayLocations` from `uiState.knownLocations`)
- Waypoint and workout start markers (`uiState.markers`)
- Live GPS recording track (`uiState.currentTrack`)

As athletes accumulate saved routes, star Strava segments, and configure favorite locations, the general map canvas becomes visually cluttered and overcrowded. Overlapping route lines, segment overlays, and POI markers compete for visual hierarchy and obscure the athlete's current location and live breadcrumbs during tracking.

### Current State vs. Expected Behavior
* **Current State**:
  * In `MapScreenWithTrack.kt` (lines 266–285), all spatial entity layers (`knownLocations`, `segments`, `routes`, `markers`, `liveTrack`) are rendered unconditionally inside `ATrainingTrackerMap`.
  * There is no UI affordance or setting to toggle layer visibility on or off.
* **Expected Behavior**:
  * Provide a dedicated floating Layers button (`Icons.Default.Layers`) on `MapScreenWithTrack.kt` aligned with the established visual design language of `TrackOnMapScreen.kt` and `RouteOnMapScreen.kt`.
  * Tapping the button opens a Material 3 `DropdownMenu` featuring checkboxes, color legend swatches, and localized labels for:
    - **Routes** (`TTColor.RouteSelected`)
    - **Segments** (`TTColor.StravaOrange`)
    - **Favorite Locations** (`MaterialTheme.colorScheme.tertiary`)
    - **Recorded Track** (`TrackType.BEST.color` / Red)
  * Selecting/deselecting a layer immediately updates the map canvas, hiding or showing the corresponding layer elements.
  * Layer preferences are persisted across app sessions in `SharedPreferences` via `MapFragmentWithTrackViewModel`.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Unconditional Rendering in `MapScreenWithTrack.kt`
In `MapScreenWithTrack.kt`, the content lambda of `ATrainingTrackerMap` unconditionally registers all layers:
```kotlin
ATrainingTrackerMap(...) {
    knownLocations(displayLocations, onLocationClick = { id -> ... })
    segments(uiState.segments, onSegmentClick = { id -> ... })
    routes(uiState.routes, onRouteClick = { id -> ... })
    markers(uiState.markers)
    liveTrack(uiState.currentTrack)
}
```
Because no filtering condition wraps these calls, every entity in the database matching the current sport type or bounding box is rendered at all times.

### 2.2 Established Pattern in Sister Screens
The application has already solved this exact ergonomics challenge in two other map screens:
1. `TrackOnMapScreen.kt` (Sprint 2026-40.4 / ATT-850):
   - Circular floating surface (`44.dp`, `color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay)`, `shadowElevation = 6.dp`).
   - `Icons.Default.Layers` with dynamic tint (`primary` when customized, `onSurface` otherwise).
   - Material 3 `DropdownMenu` with checkbox, small colored chip (`12.dp`, `RoundedCornerShape(2.dp)`), and localized title.
2. `RouteOnMapScreen.kt` (Sprint 2026-41.4 / ATT-2763 & Sprint 2026-41.6 / ATT-2863):
   - Reused the exact same circular floating button and dropdown menu pattern for toggling Climbs, Segments, and Waypoints.

Adopting this exact pattern in `MapScreenWithTrack.kt` creates seamless UX consistency across the application.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Layer Model (`GeneralMapLayer`)**:
     - Define `enum class GeneralMapLayer { ROUTES, SEGMENTS, KNOWN_LOCATIONS, TRACK }` in `com.atrainingtracker.trainingtracker.ui.map`.
  2. **ViewModel State & Persistence**:
     - Expose `enabledLayers: StateFlow<Set<GeneralMapLayer>>` and `fun toggleLayer(layer: GeneralMapLayer)` in `MapFragmentWithTrackViewModel.kt`.
     - Persist layer selection in `SharedPreferences` (key `KEY_GENERAL_MAP_ENABLED_LAYERS`).
     - Default state: all layers enabled (`GeneralMapLayer.entries.toSet()`).
  3. **Floating Layers Action Button & Dropdown Menu**:
     - Wrap `ATrainingTrackerMap` in a `Box(modifier = Modifier.fillMaxSize())` inside `BottomSheetScaffold` body.
     - Add floating circular button at `Alignment.TopEnd` with `.statusBarsPadding().padding(top = 16.dp, end = 16.dp)`.
     - Display Material 3 `DropdownMenu` with checkbox, color swatch, and localized label for each layer.
  4. **Conditional Layer Rendering**:
     - In `MapScreenWithTrack.kt`, conditionally invoke `knownLocations()`, `segments()`, `routes()`, and `markers()` + `liveTrack()` based on `layer in enabledLayers`.
  5. **Localization Parity**:
     - Add string resources `map_layers`, `map_layer_routes`, `map_layer_segments`, `map_layer_locations`, `map_layer_track` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  6. **Automated Testing**:
     - Author comprehensive contract tests in `GeneralMapLayersContractTest.kt` verifying default state, toggle transitions, persistence, and filtering logic.
     - Execute the full clean-room test suite (`./gradlew testDebugUnitTest`).
* **Out-of-Scope Non-Goals**:
  * No modifications to `RouteOnMapScreen` or `TrackOnMapScreen` layer controls.
  * No changes to database entities or repositories (`KnownLocationsRepository`, `RoutesRepository`, `SegmentsRepository`, `BANALServiceRepository`).
  * No changes to workout tracking background services or GPS telemetry sampling.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*), amending and complementing `REQ-UI-180` (*Display Favorite Locations on Central Navigation Map*) and `REQ-UI-308` (*Route Map Overlay Layer Visibility Controls*).
* **Historical Origin & Commit Trace**:
  - `REQ-UI-180` was introduced in Sprint 2026-40.4 (`ATT-1449`, commit `191ba05d`), consolidating favorite location pins onto the central navigation map.
  - `REQ-UI-308` was introduced in Sprint 2026-41.4 (`ATT-2763`), establishing the map layers dropdown menu pattern.
* **Root Reason for Existing Formulation**: Previously, spatial entities were added incrementally to `MapScreenWithTrack.kt` to maximize situational awareness. Unconditional rendering was sufficient when the number of routes and locations was small. However, as the app matured, having all layers permanently visible causes visual collisions and obscuration.
* **Preservation of Core Invariants**:
  - Bottom sheet inspection for clicked entities (routes, segments, locations) remains fully operational when the layer is enabled.
  - Live tracking recording, start pin calculation, and GPS telemetry are completely unaffected by layer visibility toggles.
  - Default state retains all layers visible, ensuring zero regression for athletes who prefer the current full display.
  - 100% unit test pass rate is strictly maintained.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Layer Model
```kotlin
package com.atrainingtracker.trainingtracker.ui.map

enum class GeneralMapLayer {
    ROUTES,
    SEGMENTS,
    KNOWN_LOCATIONS,
    TRACK
}
```

### 5.2 ViewModel State & Persistence
In `MapFragmentWithTrackViewModel.kt`:
```kotlin
private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

private val _enabledLayers = MutableStateFlow(loadEnabledLayers())
val enabledLayers: StateFlow<Set<GeneralMapLayer>> = _enabledLayers.asStateFlow()

private fun loadEnabledLayers(): Set<GeneralMapLayer> {
    val saved = prefs.getStringSet(PREF_GENERAL_MAP_ENABLED_LAYERS, null)
        ?: return GeneralMapLayer.entries.toSet()
    val restored = saved.mapNotNull { name ->
        runCatching { GeneralMapLayer.valueOf(name) }.getOrNull()
    }.toSet()
    return restored
}

fun toggleLayer(layer: GeneralMapLayer) {
    val current = _enabledLayers.value
    val updated = if (layer in current) current - layer else current + layer
    _enabledLayers.value = updated
    prefs.edit().putStringSet(PREF_GENERAL_MAP_ENABLED_LAYERS, updated.map { it.name }.toSet()).apply()
}

companion object {
    const val PREF_GENERAL_MAP_ENABLED_LAYERS = "pref_general_map_enabled_layers"
}
```

### 5.3 UI Layer in `MapScreenWithTrack.kt`
- Wrap `ATrainingTrackerMap` in `Box(modifier = Modifier.fillMaxSize())`.
- Place floating `Surface` with `Icons.Default.Layers` at `Alignment.TopEnd` with `statusBarsPadding().padding(top = 16.dp, end = 16.dp)`.
- Render `DropdownMenu` showing:
  - Checkbox
  - Color swatch (`12.dp`, `RoundedCornerShape(2.dp)`):
    - `ROUTES`: `TTColor.RouteSelected` (`Color(0xFF1565C0)`)
    - `SEGMENTS`: `TTColor.StravaOrange` (`Color(0xFFFC4C02)`)
    - `KNOWN_LOCATIONS`: `MaterialTheme.colorScheme.tertiary`
    - `TRACK`: `TrackType.BEST.color` (`Color(0xFFD32F2F)`)
  - Localized label text.
- In `ATrainingTrackerMap` content:
  - `if (GeneralMapLayer.KNOWN_LOCATIONS in enabledLayers) knownLocations(...)`
  - `if (GeneralMapLayer.SEGMENTS in enabledLayers) segments(...)`
  - `if (GeneralMapLayer.ROUTES in enabledLayers) routes(...)`
  - `if (GeneralMapLayer.TRACK in enabledLayers) { markers(uiState.markers); liveTrack(uiState.currentTrack) }`

---

## 6. System Invariants & Risk Assessment

* **System Invariants**:
  1. **Persistence Invariant**: User layer selections MUST survive app restarts.
  2. **Recording Invariant**: Toggling the Live Track layer off MUST NOT stop, pause, or interfere with active GPS tracking or track point persistence.
  3. **Visual Invariant**: Icon button, surface elevation, and dropdown menu styling MUST match `RouteOnMapScreen` and `TrackOnMapScreen` exactly.
  4. **Localization Invariant**: All 5 string resources MUST exist across all 9 supported languages without missing translations or malformed tokens.
* **Risk Mitigation**:
  - Null-safe preference deserialization ensures graceful recovery if preferences are corrupted.
  - Decoupling UI layer filtering from ViewModel data streams guarantees that data queries and background observers remain uninterrupted.
