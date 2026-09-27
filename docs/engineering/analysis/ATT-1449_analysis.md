# Engineering Analysis - ATT-1449: Show Lieblingsorte on General Map & Streamline Management View

## 1. Executive Summary & Problem Overview

* **Issue Key**: `ATT-1449`
* **Sub-task Key**: `ATT-1498` (Stage 1 Analysis)
* **Parent Epic**: `ATT-1396` (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)
* **Sprint / FixVersion**: Sprint `2026-39.3` / `V4.9.38`
* **Associated Requirements**: `REQ-UI-180` (*Display Favorite Locations on Central Navigation Map and Streamline Management View*, extending `REQ-UI-165` and `REQ-UI-166`) in `docs/requirements.md`
* **Associated Verification**: `TST-UI-132` in `docs/tests.md`
* **Target Components**:
  * Central Map Presentation Layer:
    * `MapContentScope.kt`
    * `MapScreenWithTrack.kt`
    * `MapFragmentWithTrackViewModel.kt`
  * Management Screen Presentation Layer:
    * `KnownLocationsScreen.kt`
    * `KnownLocationsViewModel.kt`
  * Navigation Architecture:
    * `NavRoutes.kt`
    * `ATrainingTrackerApp.kt`
  * Localization:
    * Reuses existing verified strings across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT)

---

## 2. Problem Statement & Athletic Motivation

### 2.1 Athletic Context
Athletes train across regional outdoor environments where routes, segments, and start points intersect:
1. **Holistic Spatial Awareness**:
   * Athletes frequently inspect the central map (`NavRoutes.MAP` / `drawer_map`) to plan upcoming workouts, examine Strava segments, review planned GPX routes, and see past tracks.
   * Having favorite start locations (*Lieblingsorte*) isolated on a secondary screen forces unnecessary context switches and deprives athletes of spatial context—such as knowing which planned routes or starred segments originate near a particular favorite start point.
2. **Elimination of Fragmented Perspectives**:
   * The current `KnownLocationsScreen` embeds a secondary Google Maps instance in a "Karte" tab alongside the "Liste" tab.
   * Maintaining two separate full-screen interactive maps creates redundant resource usage (dual GoogleMap render allocations, duplicate camera state managers, complex viewport culling), whereas `RoutesScreen` and `StarredSegmentsScreen` successfully adopt a clean, focused single-perspective list design that delegates spatial inspection to the central map.
3. **Seamless Cross-Navigation**:
   * Athletes managing their favorite locations in the list need a direct one-tap action to jump from a location item to its visual placement on the central map, automatically focusing the camera and inspecting its details.

### 2.2 Current Technical State & Gap Analysis
1. **Central Map Scope (`MapContentScope.kt`)**:
   * Renders `tracks`, `segments`, `routes`, `markers`, `liveTrack`, and `heatmap`.
   * Lacks a DSL operation for `knownLocations` with custom heart-pin markers and circular geofence overlays.
2. **Central Map ViewModel (`MapFragmentWithTrackViewModel.kt`)**:
   * Combines `banalRepository`, `segmentsRepository`, and `routesRepository`.
   * Does not observe `KnownLocationsRepository.locationsFlow`, so favorite locations are absent from `MapFragmentUIState`.
3. **Central Map Bottom Peek Sheet (`MapScreenWithTrack.kt`)**:
   * Supports peek cards for Strava segments (`selectedSegmentId`) and routes (`selectedRouteId`).
   * Lacks peek state for favorite locations (`selectedLocationId`) displaying location name, altitude, start count, and an edit action.
4. **Known Locations Screen (`KnownLocationsScreen.kt`)**:
   * Implements a `PrimaryTabRow` and `HorizontalPager` with `KnownLocationsListContent` and `KnownLocationsMapContent`.
   * `KnownLocationsMapContent` contains ~300 lines of map scaffolding, bounds culling, and camera controllers that duplicate central map functionality.
   * The "Show on Map" item menu action (`@string/known_location_show_map`) currently toggles to the internal pager tab instead of navigating to the central map.
5. **Navigation Contracts (`NavRoutes.kt`)**:
   * `NavRoutes.MAP` does not accept parameters for target location centering and peek expansion.

---

## 3. Technical Architecture & Scope Specification

```
+-----------------------------------------------------------------------------------+
|                            KnownLocationsScreen.kt                                |
|  - Clean, high-performance single-perspective list (no TabRow / no Pager)         |
|  - Overflow action "Show on Map" -> onShowOnMap(location.id)                      |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          | navController.navigate(NavRoutes.map(id))
                                          v
+-----------------------------------------------------------------------------------+
|                          ATrainingTrackerApp.kt (NavHost)                         |
|  composable(NavRoutes.MAP_PATTERN) -> extracts locationId                         |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                             MapScreenWithTrack.kt                                 |
|  - ATrainingTrackerMap DSL: knownLocations(uiState.knownLocations)               |
|  - Camera centers on targetLocationId if provided                                 |
|  - BottomSheetScaffold peek card for selectedLocationId:                          |
|      * Location Name & Starts count badge                                         |
|      * Reference Altitude & Elevation Source badge                                |
|      * Edit button -> launches EditKnownLocationDialog                            |
+-----------------------------------------+-----------------------------------------+
                                          ^
                                          | observes uiState.knownLocations
+-----------------------------------------+-----------------------------------------+
|                       MapFragmentWithTrackViewModel.kt                            |
|  - Injects KnownLocationsRepository.getInstance(application)                      |
|  - Combines locationsFlow into MapFragmentUIState.knownLocations                  |
|  - fun updateKnownLocation(...)                                                   |
+-----------------------------------------------------------------------------------+
```

### 3.1 Central Map Integration (`MapContentScope.kt`, `MapScreenWithTrack.kt`, `MapFragmentWithTrackViewModel.kt`)

1. **Map DSL Extension (`MapContentScope.kt`)**:
   * Add interface operation:
     ```kotlin
     fun knownLocations(
         locations: List<KnownLocationItem>,
         onLocationClick: (Long) -> Unit = {}
     )
     ```
   * In `MapContentScopeImpl`:
     * Add `private data class LocationData(val location: KnownLocationItem, val onClick: (Long) -> Unit)`.
     * In `Render(currentZoom: Float)`:
       * Draw `Circle` overlay for each location with center `location.latLng`, radius `location.radius.toDouble()`, fill color `primaryColor.copy(alpha = 0.15f)`, and stroke color `primaryColor.copy(alpha = 0.5f)`.
       * Draw `Marker` at `location.latLng` using `createHeartPinMarker(context, primaryColor, Color.White)`.
       * Attach click handler invoking `onClick(location.id)`.

2. **ViewModel State Enrichment (`MapFragmentWithTrackViewModel.kt`)**:
   * Inject `KnownLocationsRepository.getInstance(application)`.
   * Add `knownLocations: List<KnownLocationItem> = emptyList()` to `MapFragmentUIState`.
   * Combine `knownLocationsRepository.locationsFlow` with existing streams in `stateIn`.
   * Provide `updateKnownLocation(id: Long, name: String, altitude: Double, radius: Int, source: ElevationSource)` for in-place editing from the peek sheet.

3. **Bottom Peek Sheet & Camera Handling (`MapScreenWithTrack.kt`)**:
   * Introduce `selectedLocationId: Long?` alongside `selectedSegmentId` and `selectedRouteId`.
   * When `selectedLocationId != null`:
     * Set `sheetPeekHeight = 140.dp + navBarHeight`.
     * Render peek card displaying:
       * Location title with heart icon.
       * Starts count badge (`@string/known_location_starts_count_format`).
       * Reference altitude formatted according to metric/imperial unit preference (`@string/altitude_label`, source badge).
       * Action button "Bearbeiten" (`@string/edit`) which sets `editingLocation = selectedLocation`.
   * Host `EditKnownLocationDialog` when `editingLocation != null`, delegating save to `viewModel.updateKnownLocation`.
   * On map background tap or Back button press: clear `selectedLocationId = null` to dismiss the peek sheet.
   * Support optional `targetLocationId: Long?`: when non-null and matching location exists in `uiState.knownLocations`, initialize `selectedLocationId = targetLocationId` and animate camera to `targetLocation.latLng`.

### 3.2 Streamlining `KnownLocationsScreen.kt` & `KnownLocationsViewModel.kt`

1. **Remove Redundant Embedded Map from `KnownLocationsScreen.kt`**:
   * Remove `PrimaryTabRow` (List vs. Map tabs).
   * Remove `HorizontalPager` and `KnownLocationsMapContent`.
   * Render `KnownLocationsListContent` directly inside the scaffold, matching `RoutesScreen` and `StarredSegmentsScreen`.
   * Update top app bar title and action controls (remove map tab tests/tags, keep sort menu and search).
2. **Streamline `KnownLocationsViewModel.kt`**:
   * Remove `KnownLocationsTab`, `selectedTab`, `visibleMapLocations`, and `selectedLocationForMapPeek`.
   * Remove `onViewportBoundsChanged(bounds: LatLngBounds?)`, `selectTab(tab: KnownLocationsTab)`, `getFallbackMapLocation()`, and `dismissMapPeek()`.
   * Remove unused map culling computation loops, saving CPU cycles and memory.
3. **Wire "Show on Map" Overflow Action**:
   * `KnownLocationsScreen` accepts `onShowOnMap: (Long) -> Unit`.
   * Inside `KnownLocationsListContent`, when the user selects "Show on Map" (`@string/known_location_show_map`) from the card's 3-dot overflow menu, call `onShowOnMap(item.id)`.

### 3.3 Deep Link Navigation (`NavRoutes.kt` & `ATrainingTrackerApp.kt`)

1. **`NavRoutes.kt` Route Definitions**:
   * Define:
     ```kotlin
     const val MAP_PATTERN = "map?locationId={locationId}"
     const val ARG_LOCATION_ID = "locationId"
     fun map(locationId: Long? = null): String =
         if (locationId != null && locationId > 0) "map?$ARG_LOCATION_ID=$locationId" else MAP
     ```
   * `NavRoutes.toDrawerItemId(route)` already strips query parameters via `substringBefore("?")`, maintaining drawer highlighting for `R.id.drawer_map`.
2. **`ATrainingTrackerApp.kt` Wiring**:
   * Define composable destination for `NavRoutes.MAP_PATTERN` with optional `locationId` argument (`defaultValue = -1L`).
   * Pass `targetLocationId = locationIdArg` to `MapScreenWithTrack`.
   * In `NavRoutes.START_LOCATIONS` composable: pass `onShowOnMap = { locationId -> navController.navigate(NavRoutes.map(locationId)) }`.

---

## 4. Call Site Audit & Impact Analysis

| Affected Component | Current Callers | Impact & Required Adaptation |
| :--- | :--- | :--- |
| `MapContentScope.kt` | `ATrainingTrackerMap.kt`, `MapScreenWithTrack.kt`, `PeriodsScreen.kt`, `WorkoutClustersScreen.kt` | Non-breaking additive change: add `knownLocations(...)` to DSL interface and default empty implementation. |
| `MapFragmentWithTrackViewModel.kt` | `MapScreenWithTrack.kt`, `MapFragmentWithTrackViewModelTest.kt` | Add `knownLocationsRepository.locationsFlow` to combine; enrich `MapFragmentUIState`. |
| `MapScreenWithTrack.kt` | `ATrainingTrackerApp.kt`, `MapScreenWithTrackTest.kt` | Add parameter `targetLocationId: Long? = null`; add peek sheet and pin marker click handling. |
| `KnownLocationsScreen.kt` | `ATrainingTrackerApp.kt`, `KnownLocationsScreenTest.kt` | Add `onShowOnMap: (Long) -> Unit = {}`; remove `PrimaryTabRow`, `HorizontalPager`, and `KnownLocationsMapContent`. |
| `KnownLocationsViewModel.kt` | `KnownLocationsScreen.kt`, `KnownLocationsViewModelTest.kt` | Remove dead map tab and viewport bounds culling code. Clean up `KnownLocationsUiState`. |
| `NavRoutes.kt` | `AppNavigationDrawer.kt`, `ATrainingTrackerApp.kt`, `MainActivityWithNavigation.kt` | Add `MAP_PATTERN`, `ARG_LOCATION_ID`, and `map(locationId)`. Purely additive. |
| `ATrainingTrackerApp.kt` | `MainActivityWithNavigation.kt` | Wire parameterized `MAP_PATTERN` route and pass `onShowOnMap` callback in `START_LOCATIONS`. |

---

## 5. System Invariants & Non-Target Metrics (Chesterton's Fence)

1. **Preservation of Existing Map Content & Layering**:
   * Live GPS track (`liveTrack`), Strava segments (`segments`), GPX routes (`routes`), workout tracks (`tracks`), and sensor markers (`markers`) MUST remain fully functional with zero visual degradation.
2. **Camera Focus Invariants**:
   * Existing zoom focus behaviors (`MapZoomFocus.LOCAL_SEGMENTS`, `FOLLOW_ME`, `FIT_ALL`) MUST NOT be altered when navigating to the map without a `locationId` parameter.
3. **Database Concurrency & Persistence**:
   * All mutations to known locations (e.g. editing altitude or radius from the map peek card) MUST be dispatched on `KnownLocationsRepository.dbDispatcher` (`KnownLocationsDB-Thread`), ensuring zero SQLite deadlocks.
4. **Chesterton's Fence on `KnownLocationsScreen` Map Tab**:
   * *Origin*: The embedded map in `KnownLocationsScreen` was introduced in `ATT-919` / `ATT-1382` before `ATrainingTrackerMap` and `MapContentScope` were established as the unified central map engine.
   * *Rationale for Removal*: Having a second GoogleMap inside a tab consumes redundant GPU/CPU memory and creates a fragmented experience. The central map now fully supports multiple layer types and peek cards. Removing the tab aligns `KnownLocationsScreen` with `RoutesScreen` and `StarredSegmentsScreen`.
5. **Localization Integrity**:
   * All user-facing text strings reuse existing, 100% verified translations across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT). No missing translations or placeholders.

---

## 6. Given-When-Then Acceptance Criteria

* **Scenario 1: Favorite Locations Rendered on Central Map**
  * *Given* an athlete navigates to the central map (`NavRoutes.MAP` / `drawer_map`),
  * *When* the map is displayed,
  * *Then* all saved favorite locations SHALL be rendered with custom theme-colored heart-pin markers (`createHeartPinMarker`) and semi-transparent circular geofence overlays reflecting each location's radius.

* **Scenario 2: Interactive Location Peek Card**
  * *Given* favorite location pins rendered on the central map,
  * *When* the athlete taps a location pin,
  * *Then* the bottom sheet SHALL expand to peek height (140dp + navBarHeight) displaying the location name, reference altitude, elevation source badge, starts count badge, and a "Bearbeiten" button.

* **Scenario 3: Dismissing Location Peek Card**
  * *Given* the location peek card expanded on the central map,
  * *When* the athlete taps the map background or presses the system Back button,
  * *Then* the peek card SHALL collapse and the map selection SHALL be cleared.

* **Scenario 4: Editing Location from Central Map Peek Card**
  * *Given* the location peek card expanded on the central map,
  * *When* the athlete taps the "Bearbeiten" button,
  * *Then* `EditKnownLocationDialog` SHALL open populated with the selected location's data (name, altitude, radius); upon confirming, the changes SHALL persist immediately to SQLite and update the pin/circle overlay.

* **Scenario 5: Streamlined Known Locations Screen**
  * *Given* an athlete navigates to "Lieblingsorte" (`NavRoutes.START_LOCATIONS`),
  * *When* the screen renders,
  * *Then* it SHALL present a clean single-perspective list without tabs or embedded GoogleMap.

* **Scenario 6: Cross-Navigation from List to Central Map**
  * *Given* a location item displayed in `KnownLocationsScreen`,
  * *When* the athlete selects "Show on Map" (`@string/known_location_show_map`) from the item overflow menu,
  * *Then* the app SHALL navigate to `NavRoutes.map(location.id)`, center the central map camera on that location, and open its bottom peek card.

---

## 7. Risk Rating & Mitigation Strategy

* **Risk Rating**: **LOW to MEDIUM**
* **Technical Justification**:
  * The central map DSL (`MapContentScope`) and `BottomSheetScaffold` architecture are already proven and highly modular.
  * `KnownLocationsRepository.locationsFlow` already provides reactive, thread-safe access to favorite locations.
  * Removing the embedded map from `KnownLocationsScreen` simplifies the codebase and eliminates ~300 lines of complex viewport culling logic.
* **Mitigation Strategy**:
  * Comprehensive unit tests for `MapFragmentWithTrackViewModel` and `KnownLocationsViewModel`.
  * Compose UI interaction tests for marker taps, peek card presentation, and dismiss handling.
  * Real device verification on Google Pixel 10 ensuring smooth 60 FPS map rendering and camera transitions.
