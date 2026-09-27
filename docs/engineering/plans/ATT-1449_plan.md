# Implementation Plan - ATT-1449: Show Lieblingsorte on General Map & Streamline Management View

**Ticket**: [ATT-1449](https://rainerblind.atlassian.net/browse/ATT-1449)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Sub-task**: [ATT-1500](https://rainerblind.atlassian.net/browse/ATT-1500) (Stage 3 Impl-Plan)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-180` (*Display Favorite Locations on Central Navigation Map and Streamline Management View*)  
**Test ID**: `TST-UI-132`  
**Analysis Reference**: `docs/engineering/analysis/ATT-1449_analysis.md`  
**Test Spec Reference**: `docs/engineering/test_specs/ATT-1449_test_spec.md`  
**Branch**: `feature/ATT-1449`  

---

## 1. Executive Summary

Ticket **ATT-1449** consolidates spatial visualization into the central navigation map (`NavRoutes.MAP` / `MapScreenWithTrack.kt`) by displaying athlete's favorite start locations (*Lieblingsorte*) alongside routes, segments, and live tracks, and streamlines `KnownLocationsScreen.kt` by removing its redundant "Karte" tab.

This architectural enhancement eliminates duplicate GoogleMap allocations, removes ~300 lines of redundant viewport bounds culling and camera orchestration logic, aligns `KnownLocationsScreen` with `RoutesScreen` and `StarredSegmentsScreen` as a clean single-perspective list, and adds an interactive bottom peek card on the central map with in-place editing.

---

## 2. Architecture & Data Flow Diagram

```
+-----------------------------------------------------------------------------------+
|                            KnownLocationsScreen.kt                                |
|  - Clean, high-performance single-perspective list (no TabRow / no Pager)         |
|  - Overflow action "Show on Map" -> onShowOnMap(location.id)                      |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          | onShowOnMap(id) -> navController.navigate(NavRoutes.map(id))
                                          v
+-----------------------------------------------------------------------------------+
|                          ATrainingTrackerApp.kt (NavHost)                         |
|  composable(NavRoutes.MAP_PATTERN)                                                |
|  - extracts locationIdArg and passes targetLocationId to MapScreenWithTrack       |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                             MapScreenWithTrack.kt                                 |
|  - ATrainingTrackerMap DSL: knownLocations(uiState.knownLocations)                |
|  - Camera animates to targetLocationId if provided                                |
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
|  - fun updateKnownLocation(...) -> repository.updateLocation                      |
+-----------------------------------------------------------------------------------+
```

---

## 3. Step-by-Step Implementation Phases

### Phase 1: Navigation Contracts (`NavRoutes.kt`)
**File**: [NavRoutes.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt)

1. **Add Route Pattern & Argument Constants**:
   ```kotlin
   const val MAP_PATTERN = "map?locationId={locationId}"
   const val ARG_LOCATION_ID = "locationId"
   ```
2. **Add Helper Method**:
   ```kotlin
   /**
    * Builds a navigation route to the central map, optionally parameterized with a target location ID.
    */
   fun map(locationId: Long? = null): String =
       if (locationId != null && locationId > 0) "map?$ARG_LOCATION_ID=$locationId" else MAP
   ```
3. **Verify Drawer Mapping Invariant**:
   `toDrawerItemId(route: String?)` already evaluates `route?.substringBefore("?")?.substringBefore("/")`, which resolves `"map?locationId=123"` to `MAP` (`R.id.drawer_map`).

---

### Phase 2: Central Map DSL & Scope (`MapContentScope.kt`)
**File**: [MapContentScope.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt)

1. **Extend `MapContentScope` Interface**:
   ```kotlin
   /**
    * Renders athlete's favorite start locations (Lieblingsorte) with heart-pin markers and geofence overlays.
    */
   fun knownLocations(
       locations: List<KnownLocationItem>,
       onLocationClick: (Long) -> Unit = {}
   )
   ```
2. **Extend `MapContentScopeImpl`**:
   * Add internal container:
     ```kotlin
     private data class LocationData(val location: KnownLocationItem, val onClick: (Long) -> Unit)
     private val locationData = mutableStateListOf<LocationData>()
     ```
   * Clear in `collect`:
     ```kotlin
     locationData.clear()
     ```
   * Implement interface method:
     ```kotlin
     override fun knownLocations(locations: List<KnownLocationItem>, onLocationClick: (Long) -> Unit) {
         locations.forEach { location ->
             this.locationData.add(LocationData(location, onLocationClick))
         }
     }
     ```
   * In `Render(currentZoom: Float)`:
     Render favorite locations:
     ```kotlin
     // 8. Known Locations (Lieblingsorte)
     locationData.forEach { data ->
         val loc = data.location
         val markerBitmap = remember(loc.id, primaryColor) {
             createHeartPinMarker(context, primaryColor, androidx.compose.ui.graphics.Color.White)
         }
         
         // Circular geofence overlay
         com.google.maps.android.compose.Circle(
             center = loc.latLng,
             radius = loc.radius.toDouble(),
             fillColor = primaryColor.copy(alpha = 0.15f),
             strokeColor = primaryColor.copy(alpha = 0.5f),
             strokeWidth = 2f
         )

         // Marker with heart glyph
         com.google.maps.android.compose.Marker(
             state = com.google.maps.android.compose.rememberUpdatedMarkerState(position = loc.latLng),
             title = loc.name,
             icon = markerBitmap,
             onClick = {
                 data.onClick(loc.id)
                 true
             }
         )
     }
     ```

---

### Phase 3: Central Map ViewModel (`MapFragmentWithTrackViewModel.kt`)
**File**: [MapFragmentWithTrackViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrackViewModel.kt)

1. **Enrich `MapFragmentUIState`**:
   ```kotlin
   data class MapFragmentUIState(
       val segments: List<MapSegment> = emptyList(),
       val routes: List<MapRoute> = emptyList(),
       val knownLocations: List<KnownLocationItem> = emptyList(),
       val markers: List<LocationMarker> = emptyList(),
       val currentTrack: List<LatLng> = emptyList(),
       val bSportType: BSportType = BSportType.UNKNOWN
   )
   ```
2. **Inject `KnownLocationsRepository` & Combine State**:
   ```kotlin
   class MapFragmentWithTrackViewModel(
       application: Application,
       private val knownLocationsRepository: KnownLocationsRepository = KnownLocationsRepository.getInstance(application)
   ) : AndroidViewModel(application) {
       ...
       val uiState: StateFlow<MapFragmentUIState> = combine(
           banalRepository.bSportType,
           banalRepository.currentTrack,
           segmentsRepository.allSegmentsWithPath,
           routesRepository.allRoutes,
           knownLocationsRepository.locationsFlow
       ) { bSportType, currentTrack, liveSegments, allRoutes, knownLocations ->
           ...
           MapFragmentUIState(
               segments = ...,
               routes = ...,
               knownLocations = knownLocations,
               bSportType = bSportType,
               currentTrack = currentTrack,
               markers = markers
           )
       }.stateIn(...)
   ```
3. **Add Location Update Operation**:
   ```kotlin
   fun updateKnownLocation(id: Long, name: String, altitude: Double, radius: Int, source: ElevationSource) {
       viewModelScope.launch {
           knownLocationsRepository.updateLocation(id, name, altitude, radius, source)
       }
   }
   ```

---

### Phase 4: Central Map UI & Bottom Peek Sheet (`MapScreenWithTrack.kt`)
**File**: [MapScreenWithTrack.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt)

1. **Add `targetLocationId` Parameter**:
   ```kotlin
   @OptIn(ExperimentalMaterial3Api::class)
   @Composable
   fun MapScreenWithTrack(
       viewModel: MapFragmentWithTrackViewModel = viewModel(),
       targetLocationId: Long? = null,
       modifier: Modifier = Modifier
   )
   ```
2. **State Management for Selected Location & Dialog**:
   ```kotlin
   var selectedSegmentId by rememberSaveable { mutableStateOf<Long?>(null) }
   var selectedRouteId by rememberSaveable { mutableStateOf<Long?>(null) }
   var selectedLocationId by rememberSaveable { mutableStateOf<Long?>(targetLocationId) }
   var editingLocation by remember { mutableStateOf<KnownLocationItem?>(null) }
   ```
3. **Adjust Peek Height**:
   ```kotlin
   sheetPeekHeight = when {
       selectedSegmentId != null -> 185.dp + navBarHeight
       selectedRouteId != null -> 100.dp + navBarHeight
       selectedLocationId != null -> 140.dp + navBarHeight
       else -> 0.dp
   }
   ```
4. **Peek Sheet Content for Location**:
   When `selectedLocationId != null`:
   * Display `Card` with:
     * Row 1: Location title with heart icon and starts count badge (`stringResource(R.string.known_location_starts_count_format, location.hitCount)`).
     * Row 2: Reference altitude formatted with metric/imperial units and `ElevationSourceBadge`.
     * Row 3: Action button "Bearbeiten" (`Icons.Default.Edit`, `@string/edit`) opening `editingLocation = selectedLocation`.
5. **Host `EditKnownLocationDialog`**:
   ```kotlin
   editingLocation?.let { loc ->
       EditKnownLocationDialog(
           location = loc,
           isMetric = TrainingApplication.getUnit() == MyUnits.METRIC,
           showMap = false,
           onConfirm = { id, name, altitude, radius, source ->
               viewModel.updateKnownLocation(id, name, altitude, radius, source)
               editingLocation = null
           },
           onDismiss = { editingLocation = null }
       )
   }
   ```
6. **Pass `knownLocations` to Map DSL**:
   ```kotlin
   ATrainingTrackerMap(...) {
       knownLocations(uiState.knownLocations, onLocationClick = { id ->
           selectedSegmentId = null
           selectedRouteId = null
           selectedLocationId = id
       })
       ...
   }
   ```
7. **Camera Auto-Focus on `targetLocationId`**:
   When `targetLocationId != null` and matching location is in `uiState.knownLocations`, select location and center camera.
8. **Dismiss Handlers**:
   Map tap or BackHandler resets `selectedLocationId = null`.

---

### Phase 5: Streamlining Management Screen & ViewModel (`KnownLocationsScreen.kt`, `KnownLocationsViewModel.kt`)
**Files**:
* [KnownLocationsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt)
* [KnownLocationsViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModel.kt)

1. **Streamline `KnownLocationsViewModel.kt`**:
   * Remove `KnownLocationsTab`, `selectedTab`, `visibleMapLocations`, `selectedLocationForMapPeek`, `currentViewportBounds`.
   * Remove `onViewportBoundsChanged(bounds: LatLngBounds?)`, `selectTab(tab: KnownLocationsTab)`, `getFallbackMapLocation()`, `dismissMapPeek()`.
   * In `KnownLocationsUiState`, remove `visibleMapLocations`, `selectedTab`, and `selectedLocationForMapPeek`.
2. **Streamline `KnownLocationsScreen.kt`**:
   * Add callback `onShowOnMap: (Long) -> Unit = {}`.
   * Remove `PrimaryTabRow` (List and Map tabs).
   * Remove `HorizontalPager` and `KnownLocationsMapContent`.
   * Render `KnownLocationsListContent` directly in body.
   * In `KnownLocationsListContent`, when "Show on Map" (`@string/known_location_show_map`) is selected from the card overflow menu:
     Invoke `onShowOnMap(item.id)`.

---

### Phase 6: Top-Level Navigation Wiring (`ATrainingTrackerApp.kt`)
**File**: [ATrainingTrackerApp.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt)

1. **Wire Parameterized `MAP_PATTERN` Destination**:
   ```kotlin
   composable(
       route = NavRoutes.MAP_PATTERN,
       arguments = listOf(
           navArgument(NavRoutes.ARG_LOCATION_ID) {
               type = NavType.LongType
               defaultValue = -1L
           }
       )
   ) { backStackEntry ->
       val locationIdArg = backStackEntry.arguments?.getLong(NavRoutes.ARG_LOCATION_ID)?.takeIf { it > 0 }
       val mapViewModel: MapFragmentWithTrackViewModel = viewModel(activity)
       MapScreenWithTrack(
           viewModel = mapViewModel,
           targetLocationId = locationIdArg
       )
   }
   ```
2. **Wire `START_LOCATIONS` Callback**:
   ```kotlin
   composable(NavRoutes.START_LOCATIONS) {
       val knownLocationsViewModel: KnownLocationsViewModel = viewModel(activity)
       KnownLocationsScreen(
           viewModel = knownLocationsViewModel,
           onMenuClick = { drawerController.openDrawer() },
           onShowOnMap = { locationId ->
               navController.navigate(NavRoutes.map(locationId))
           }
       )
   }
   ```

---

### Phase 7: Verification & Test Implementation

1. **ViewModel Tests**:
   * `MapFragmentWithTrackViewModelTest.kt`: test uiState flow combination and repository update delegation.
   * `KnownLocationsViewModelTest.kt`: test streamlined list sorting, search filtering, and editing.
2. **Navigation Tests**:
   * `NavRoutesTest.kt`: test `NavRoutes.map(locationId)` and `toDrawerItemId`.
3. **Screen Tests**:
   * `KnownLocationsScreenTest.kt`: test that "Show on Map" invokes `onShowOnMap`.
   * `MapScreenWithTrackTest.kt`: test pin click and peek sheet presentation.
4. **Full-Suite Regression**:
   * Run `./gradlew testDebugUnitTest`.
5. **Physical Device Verification**:
   * Deploy to Google Pixel 10 (`66020DLCR002FL`) and execute test procedure `TST-UI-132.8`.

---

## 4. Preserved Invariants & Safety Measures

1. **Existing Central Map Layers**:
   Tracks, Strava segments, planned GPX routes, GPS live track, and heatmaps remain completely unaltered.
2. **Camera Focus Invariants**:
   Standard tracking zoom behaviors (`LOCAL_SEGMENTS`, `FOLLOW_ME`, `FIT_ALL`) remain untouched when opening the map without `locationId`.
3. **Database Thread Concurrency**:
   `KnownLocationsRepository.dbDispatcher` (`KnownLocationsDB-Thread`) continues to serialize all database operations, preventing race conditions.
4. **Localization Parity**:
   All strings reuse existing verified translations across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT). No missing translations or placeholders.
5. **Device State**:
   Physical Pixel 10 remains in Light Mode (`Night mode: no`).
