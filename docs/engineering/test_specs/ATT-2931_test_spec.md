# Stage 2: Requirement & Test Specification - ATT-2931: Add layers control menu to toggle routes, segments, markers, and track on general map

**Ticket**: [ATT-2931](https://atrainingtracker.atlassian.net/browse/ATT-2931)  
**Sub-task**: [ATT-2960](https://atrainingtracker.atlassian.net/browse/ATT-2960) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Requirement Mapping**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*)  
**Test Spec ID**: `TST-UI-282`  
**Branch**: `feature/ATT-2931`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-322)

### 1.1 Problem Statement & Rationale
On the general map (`MapScreenWithTrack.kt`, accessible via the "Karte" drawer menu and during active workout tracking), multiple spatial entities are rendered simultaneously: saved routes (`uiState.routes`), Strava and local segments (`uiState.segments`), favorite locations / Lieblingsorte (`displayLocations` from `uiState.knownLocations`), markers (`uiState.markers`), and live GPS tracks (`uiState.currentTrack`).

As athletes build rich route libraries, star multiple Strava segments, and define favorite locations, the general map canvas becomes visually overcrowded. Overlapping polyline ribbons and POI pins compete for visual hierarchy and obscure the athlete's current location and live track breadcrumbs. Currently, `MapScreenWithTrack.kt` renders all spatial layers unconditionally with zero visibility controls.

### 1.2 Functional & Architectural Requirements
The system SHALL provide dynamic visibility controls for general map spatial layers via a floating Layers button and Material 3 dropdown menu in `MapScreenWithTrack.kt`, conditional layer composition in `ATrainingTrackerMap`, and preference persistence in `MapFragmentWithTrackViewModel` (ATT-2931, amending and complementing `REQ-UI-180` and `REQ-UI-308`):
1. **Layer Model (`GeneralMapLayer`)**:
   * The system SHALL define `enum class GeneralMapLayer` in `com.atrainingtracker.trainingtracker.ui.map` declaring exactly four layer types:
     - `ROUTES`: Stored application routes.
     - `SEGMENTS`: Strava and local starred segments.
     - `KNOWN_LOCATIONS`: Favorite locations (Lieblingsorte).
     - `TRACK`: Live GPS workout track breadcrumbs and workout start markers.
2. **ViewModel State & Persistence (`MapFragmentWithTrackViewModel.kt`)**:
   * `MapFragmentWithTrackViewModel` SHALL expose `val enabledLayers: StateFlow<Set<GeneralMapLayer>>`.
   * On initial instantiation, `MapFragmentWithTrackViewModel` SHALL restore enabled layers from `SharedPreferences` (`PREF_GENERAL_MAP_ENABLED_LAYERS`).
   * If no preferences are stored, the system SHALL default to enabling all layers (`GeneralMapLayer.entries.toSet()`).
   * `MapFragmentWithTrackViewModel` SHALL provide `fun toggleLayer(layer: GeneralMapLayer)`.
   * Toggling a layer SHALL immediately emit the updated set in `enabledLayers` and synchronously or asynchronously commit the updated string set to `SharedPreferences`.
3. **Ergonomic Floating Layers Button & Dropdown Menu (`MapScreenWithTrack.kt`)**:
   * `MapScreenWithTrack.kt` SHALL wrap `ATrainingTrackerMap` in a `Box(modifier = Modifier.fillMaxSize())` inside the `BottomSheetScaffold` body.
   * `MapScreenWithTrack.kt` SHALL render a floating circular button at `Alignment.TopEnd` with `.statusBarsPadding().padding(top = 16.dp, end = 16.dp)`.
   * The button SHALL be a `Surface` with `size = 44.dp`, `shape = CircleShape`, `color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay)`, `shadowElevation = 6.dp`, and `tonalElevation = 2.dp`.
   * The button SHALL contain an `Icon` with `Icons.Default.Layers` (`size = 22.dp`), whose tint dynamically resolves to `MaterialTheme.colorScheme.primary` when any layer is disabled (`enabledLayers.size < GeneralMapLayer.entries.size`) or `MaterialTheme.colorScheme.onSurface` when all layers are enabled.
   * Clicking the button SHALL open a Material 3 `DropdownMenu` styled consistently with `TrackOnMapScreen.kt` and `RouteOnMapScreen.kt`.
   * The dropdown menu SHALL render four `DropdownMenuItem` entries with:
     - Checkbox reflecting whether the layer is present in `enabledLayers`.
     - Color legend swatch (`12.dp`, `RoundedCornerShape(2.dp)`):
       * `ROUTES`: `TTColor.RouteSelected` (`Color(0xFF1565C0)`).
       * `SEGMENTS`: `TTColor.StravaOrange` (`Color(0xFFFC4C02)`).
       * `KNOWN_LOCATIONS`: `MaterialTheme.colorScheme.tertiary`.
       * `TRACK`: `TrackType.BEST.color` (`Color(0xFFD32F2F)`).
     - Localized text label.
4. **Conditional Map Canvas Layer Composition**:
   * In `ATrainingTrackerMap`'s content scope:
     - `if (GeneralMapLayer.KNOWN_LOCATIONS in enabledLayers) knownLocations(displayLocations, ...)`
     - `if (GeneralMapLayer.SEGMENTS in enabledLayers) segments(uiState.segments, ...)`
     - `if (GeneralMapLayer.ROUTES in enabledLayers) routes(uiState.routes, ...)`
     - `if (GeneralMapLayer.TRACK in enabledLayers) { markers(uiState.markers); liveTrack(uiState.currentTrack) }`
5. **100% 9-Language Localization Parity**:
   * String resources `map_layers`, `map_layer_routes`, `map_layer_segments`, `map_layer_locations`, `map_layer_track` SHALL be externalized and translated across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
6. **Preservation of System Invariants**:
   * Entity selection bottom sheet inspection (routes, segments, favorite locations) MUST remain fully operational when the layer is visible.
   * Background GPS workout recording, start location detection, and telemetry capture MUST NOT be interrupted when the Live Track layer is unselected.
   * Default state displays all layers, preserving existing user behavior out of the box.
   * 100% unit test pass rate across the test suite.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-322`), amending and complementing `REQ-UI-180` (*Display Favorite Locations on Central Navigation Map*) and `REQ-UI-308` (*Route Map Overlay Layer Visibility Controls*).
2. *Historical Origin & Commit Trace*:
   - `REQ-UI-180` was introduced in Sprint 2026-40.4 (`ATT-1449`, commit `191ba05d`), consolidating favorite location pins onto the central navigation map.
   - `REQ-UI-308` was introduced in Sprint 2026-41.4 (`ATT-2763`), establishing the map layers dropdown menu pattern on the route map.
3. *Root Reason for Existing Formulation*: Previously, spatial entities were added incrementally to `MapScreenWithTrack.kt` to maximize situational awareness. Unconditional rendering was sufficient when the number of routes and locations was small. As user libraries grew, having all layers permanently visible caused visual collisions and obscuration.
4. *Preservation of Core Invariants*: Full touch target compliance (>= 48dp), entity detail sheet inspection, live track recording, and 100% test pass rate remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Layers Action Button)**:
  * *Given* the athlete is on the general map (`MapScreenWithTrack`),
  * *When* viewing the screen,
  * *Then* a floating layers button (`Icons.Default.Layers`) is visible at `Alignment.TopEnd` with `statusBarsPadding().padding(top = 16.dp, end = 16.dp)`.
* **Criterion 2 (Layer Menu Display & Legend)**:
  * *Given* the athlete taps the layers button,
  * *When* the dropdown menu opens,
  * *Then* checkboxes for Routes, Segments, Favorite Locations, and Recorded Track are shown with their corresponding color swatches and localized labels.
* **Criterion 3 (Dynamic Decluttering)**:
  * *Given* the athlete unchecks "Routes",
  * *When* returning to the map,
  * *Then* all route polylines are immediately hidden from the map canvas while segments, favorite locations, and live track remain visible.
* **Criterion 4 (Live Track Toggle)**:
  * *Given* an active workout recording,
  * *When* the athlete toggles off the "Recorded Track" layer,
  * *Then* the live track polyline and start marker are hidden from the map canvas without interrupting recording.
* **Criterion 5 (State Persistence)**:
  * *Given* the athlete deselects a layer (e.g. Segments),
  * *When* navigating away and reopening the general map or restarting the app,
  * *Then* the layer selection state is restored from persistence.
* **Criterion 6 (Design & Localization Parity)**:
  * *Given* the layer selection menu,
  * *When* rendered in any of the 9 supported languages,
  * *Then* styling, typography, and iconography match `TrackOnMapScreen.kt` and `RouteOnMapScreen.kt` with zero text truncation.

---

## 2. Test Specification (TST-UI-282)

### Test Case 1: GeneralMapLayer Enum Contract (`TST-UI-282.1`)
* **Scope**: Unit & Contract Test (`GeneralMapLayersContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/GeneralMapLayersContractTest.kt`
* **Checks**:
  * Assert `GeneralMapLayer` declares exactly four entries: `ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, `TRACK`.
  * Assert `GeneralMapLayer.entries.toSet()` contains all four values.

### Test Case 2: ViewModel Layer Toggling & Persistence Contract (`TST-UI-282.2`)
* **Scope**: Unit & Contract Test (`GeneralMapLayersContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/GeneralMapLayersContractTest.kt`
* **Checks**:
  * Verify `MapFragmentWithTrackViewModel` initializes `enabledLayers` to all layers when `SharedPreferences` is empty.
  * Verify `toggleLayer(GeneralMapLayer.ROUTES)` removes `ROUTES` from `enabledLayers` and updates `SharedPreferences`.
  * Verify toggling `ROUTES` again restores it to `enabledLayers`.
  * Verify restoring from existing `SharedPreferences` containing a subset (e.g. `setOf("SEGMENTS", "TRACK")`) correctly deserializes into `Set<GeneralMapLayer>`.

### Test Case 3: MapScreenWithTrack UI Structure & Conditional Layer Delegation (`TST-UI-282.3`)
* **Scope**: Unit & Contract Test (`GeneralMapLayersContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/GeneralMapLayersContractTest.kt`
* **Checks**:
  * Assert `MapScreenWithTrack.kt` contains `Icons.Default.Layers`.
  * Assert `MapScreenWithTrack.kt` contains `GeneralMapLayer` references gating `knownLocations()`, `segments()`, `routes()`, and `liveTrack()`.
  * Assert `MapScreenWithTrack.kt` contains `DropdownMenu` with all 4 layer entries and color chips.

### Test Case 4: 9-Language Localization Parity Audit (`TST-UI-282.4`)
* **Scope**: Unit & Localization Test (`GeneralMapLayersLocalizationTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/GeneralMapLayersLocalizationTest.kt`
* **Checks**:
  * Verify string resources `map_layers`, `map_layer_routes`, `map_layer_segments`, `map_layer_locations`, `map_layer_track` exist and are non-empty across all 9 supported locale directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  * Verify 0 missing keys, 0 untranslated placeholders, and valid XML formatting.

### Test Case 5: Clean-Room Full Suite Regression Execution (`TST-UI-282.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% test pass rate across the entire test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-282.1` | Contract | `GeneralMapLayer` enum declaration | `REQ-UI-322.1` | Specified |
| `TST-UI-282.2` | Contract | `MapFragmentWithTrackViewModel` persistence & toggling | `REQ-UI-322.2` | Specified |
| `TST-UI-282.3` | Contract | `MapScreenWithTrack` UI & conditional delegation | `REQ-UI-322.3`, `REQ-UI-322.4` | Specified |
| `TST-UI-282.4` | Localization | `GeneralMapLayersLocalizationTest` (9 locales) | `REQ-UI-322.5` | Specified |
| `TST-UI-282.5` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |

