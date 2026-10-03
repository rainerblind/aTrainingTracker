# Implementation Plan - ATT-919: Known Start Locations Management, Interactive Map Geofences & Altitude Lock UI

**Ticket**: [ATT-919](https://rainerblind.atlassian.net/browse/ATT-919)  
**Sub-task**: [ATT-1380](https://rainerblind.atlassian.net/browse/ATT-1380) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Requirements**: `REQ-UI-165` (Net-New), `REQ-DAT-007` (Synchronized), `REQ-DAT-014`  
**Test Spec ID**: `TST-UI-117`  
**Branch**: `feature/ATT-919`  

---

## 1. Technical Architecture & Components

The feature delivers an end-to-end management experience for geographical workout start locations stored in `StartLocation2Altitude.db` (Schema V5).

### 1.1 Navigation & Routing Architecture
1. **Drawer Navigation**:
   - Integrated into [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt) under `drawer__maps` destinations:
     - Item ID: `R.id.drawer_start_locations`
     - Destination Route: `NavRoutes.START_LOCATIONS`
     - Icon: `Icons.Default.Place`
     - Title: `R.string.drawer_start_locations` (*"Start Locations"* / *"Startorte"*)
2. **NavHost Registration**:
   - Register route in [NavRoutes.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt) (`const val START_LOCATIONS = "start_locations"`).
   - Wire route in [ATrainingTrackerApp.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt) `composable(NavRoutes.START_LOCATIONS)` instantiating `KnownLocationsScreen`.

---

### 1.2 Reverse Geocoding & Human-Readable Auto-Naming (`LocationNameResolver.kt`)
1. **Android Geocoder Integration**:
   - Encapsulate address resolution in `com.atrainingtracker.trainingtracker.location.LocationNameResolver`:
     ```kotlin
     object LocationNameResolver {
         suspend fun resolveLocationName(context: Context, latitude: Double, longitude: Double): String
     }
     ```
   - Queries `android.location.Geocoder` on `Dispatchers.IO`:
     - Combines address elements hierarchically:
       - Priority 1: `featureName` + `locality` (e.g. *"Englischer Garten, München"*)
       - Priority 2: `thoroughfare` + `subLocality` / `locality` (e.g. *"Marienplatz, München"*)
       - Priority 3: `locality` (e.g. *"München"*) or `adminArea`
     - Fallback on offline/error/empty: Localized coordinates string `context.getString(R.string.known_location_unnamed_format, latitude, longitude)` (e.g. *"Startort (48.137, 11.576)"*).
2. **Discovery & Healing Integration**:
   - When `AltitudeFromPressureDevice` discovers a new start location or legacy healing detects a placeholder (`"Internet DEM start"`, `"Auto-learned start"`, or blank), it resolves a human-readable name asynchronously.
   - User-customized names (`source == MANUAL_USER` or user edits) are **strictly preserved** and immune to automatic overwriting.

---

### 1.3 Concurrency & Single-Threaded SQLite Repository (`KnownLocationsRepository.kt`)
1. **Thread Confinement Invariant**:
   - To eliminate coroutine thread hopping and avoid deadlocks with Java `synchronized` blocks in `KnownLocationsDatabaseManager`, all SQLite operations are confined to a dedicated single-threaded dispatcher:
     ```kotlin
     private val dbDispatcher = Executors.newSingleThreadExecutor { runnable ->
         Thread(runnable, "KnownLocationsDB-Thread").apply { isDaemon = true }
     }.asCoroutineDispatcher()
     ```
2. **Repository API**:
   - `getLocationsFlow(): Flow<List<KnownLocationItem>>`: Emits the reactive list of stored start locations sorted by hit count descending or name.
   - `suspend fun updateLocation(id: Long, name: String, altitude: Double, source: ElevationSource): Unit`: Updates name, altitude, source, and automatically sets `is_locked = (source == MANUAL_USER)`.
   - `suspend fun deleteLocation(id: Long): Unit`: Deletes location and refreshes flow.
   - `suspend fun refreshDem(id: Long, latLng: LatLng): ElevationResult`: Invokes `ElevationService.fetchElevation()`.

---

### 1.4 State Management & Viewport Bounds Culling (`KnownLocationsViewModel.kt`)
1. **UI State Model**:
   ```kotlin
   data class KnownLocationsUiState(
       val locations: List<KnownLocationItem> = emptyList(),
       val filteredLocations: List<KnownLocationItem> = emptyList(),
       val visibleMapLocations: List<KnownLocationItem> = emptyList(),
       val selectedTab: KnownLocationsTab = KnownLocationsTab.LIST,
       val searchQuery: String = "",
       val isMetric: Boolean = true,
       val isLoading: Boolean = false,
       val selectedLocationForEdit: KnownLocationItem? = null,
       val selectedLocationForMapPeek: KnownLocationItem? = null
   )
   ```
2. **Viewport Culling Guardrails**:
   - Function `onViewportBoundsChanged(bounds: LatLngBounds)` filters markers to those strictly inside visible bounds, capping rendered markers at 100 to maintain 60 FPS scrolling and zooming.

---

### 1.5 Dual-Perspective Jetpack Compose Screen (`KnownLocationsScreen.kt`)
1. **List Perspective**:
   - Search/filter header.
   - High-density Material 3 cards:
     - **Location Name**: Primary title (editable).
     - **Reference Altitude**: Formatted in meters (`525.5 m`) or nearest foot (`1724 ft`).
     - **Source Badge**: Color-coded semantic tag:
       - `INTERNET_DEM`: Blue / primary (`@string/source_internet_dem`)
       - `MANUAL_USER`: Green / success (`@string/source_manual_user`)
       - `AUTO_LEARNED`: Amber / secondary (`@string/source_auto_learned`)
       - `GPS_FALLBACK`: Orange / warning (`@string/source_gps_fallback`)
       - `LEGACY_RAW`: Gray / muted (`@string/source_legacy_raw`)
     - **Hit Count Badge**: e.g. `42 Starts` (`@string/known_locations_starts_count`).
     - **Coordinates**: Lat/Lon formatted to 5 decimal places.
     - **Overflow Menu**: Edit (`@string/edit`), Show on Map (`@string/show_on_map`), Delete (`@string/delete`).
2. **Map Perspective**:
   - Google Map composable displaying:
     - Marker pins placed at `(latitude, longitude)`.
     - 200m circular geofence overlays (`CircleOptions`, radius 200.0m, `TTAlpha.HeatmapLow` fill).
     - Tap marker: displays bottom peek sheet with location summary and "Bearbeiten" action.

---

### 1.6 Modal Edit Dialog (`EditKnownLocationDialog.kt`)
- Bottom sheet composable:
  - **Name Field**: `OutlinedTextField` for custom naming.
  - **Altitude Field**: `OutlinedTextField` with decimal comma/period parsing.
  - **"Fetch from Internet (DEM)" Button**: Queries `ElevationService` asynchronously with inline progress indicator. Upon success, updates altitude and sets source to `INTERNET_DEM`.
  - **Save & Cancel**: Commits to SQLite via `KnownLocationsRepository`. Saving a manual altitude edit automatically persists `source = MANUAL_USER` and `is_locked = 1`.

---

### 1.7 Unit Conversion Precision & Invariants
- **Database Storage**: SI meters (`Double`) strictly maintained.
- **Metric Editing**: $0.1\text{m}$ precision preserved.
- **Imperial Conversion**:
  - Foot to meter manual save: $h_{\text{m}} = \text{round}(h_{\text{ft}} \times 0.3048 \times 10.0) / 10.0$.
  - Meter to foot display: $h_{\text{ft}} = \text{round}(h_{\text{m}} / 0.3048)$.

---

### 1.8 100% 9-Language Localization
16 new localized string resources across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`:
- `drawer_start_locations`
- `known_locations_title`
- `known_locations_tab_list`
- `known_locations_tab_map`
- `known_locations_search_hint`
- `known_locations_empty_title`
- `known_locations_empty_desc`
- `known_location_unnamed_format`
- `known_locations_starts_count`
- `source_internet_dem`
- `source_manual_user`
- `source_auto_learned`
- `source_gps_fallback`
- `source_legacy_raw`
- `known_location_edit_title`
- `known_location_fetch_dem`

---

## 2. Impact Analysis & Invariants Cross-Check

### 2.1 Mapped Requirements Verification
* `REQ-UI-165` (Known Start Locations Management & Calibration UI): **Fully Implemented**.
* `REQ-DAT-007` (Automated Altitude Reference Discovery & Hit Count Tracking): **Preserved**. Verified that `learnLocation()` increments `hitCount` on locked/manual locations while keeping reference altitude immutable (`testLearnLocation_lockedRecord_strictlyImmutable`).
* `REQ-DAT-014` (DEM Reference Altitude Retrieval & Schema V5): **Preserved**. Schema V5 compatibility and batch healing invariants intact.
* `REQ-CON-013` (Sensor Initialization & Null-Safe Correction Dispatch): **Preserved**. Barometric baseline calibration unaffected.
* `REQ-UI-159` / `REQ-UI-161` / `REQ-UI-162` (Drawer State & Navigation Invariants): **Preserved**. Single source of truth and back handling unaffected.

---

## 3. Step-by-Step Implementation Sequence

1. **Gate Check**: Verify sub-task `ATT-1380` reaches `Erledigt` in Jira.
2. **String Resources**: Add all 16 string keys across all 9 `strings.xml` files.
3. **LocationNameResolver**: Implement `LocationNameResolver.kt` for reverse geocoding with unit tests in `LocationNameResolverTest.kt`.
4. **Data Layer Concurrency & Repository**:
   - Implement `KnownLocationsRepository.kt` on `KnownLocationsDB-Thread`.
   - Unit test concurrency in `KnownLocationsRepositoryTest.kt`.
5. **ViewModel & Unit Conversions**:
   - Implement `KnownLocationsViewModel.kt` with viewport culling and search filtering.
   - Unit test ViewModel and unit conversions in `KnownLocationsViewModelTest.kt` and `KnownLocationsUnitConversionTest.kt`.
6. **Navigation Integration**:
   - Update `NavRoutes.kt`, `AppNavigationDrawer.kt`, and `ATrainingTrackerApp.kt`.
   - Verify drawer navigation in `AppNavigationDrawerTest.kt`.
7. **Compose UI Screens & Dialog**:
   - Implement `EditKnownLocationDialog.kt` with DEM fetch and auto-locking on manual save.
   - Implement `KnownLocationsScreen.kt` with List and Map tabs.
   - Write Compose UI tests in `KnownLocationsScreenTest.kt` and `EditKnownLocationDialogTest.kt`.
8. **Automated Verification & Regression**:
   - Execute translation parity test: `./gradlew testDebugUnitTest --tests TranslationParityTest`.
   - Execute full test suite: `./gradlew testDebugUnitTest`.

---

## 4. Test Verification Procedures (`TST-UI-117`)

| Test Procedure | Component | Verification Target |
| :--- | :--- | :--- |
| `TST-UI-117.1` | `AppNavigationDrawerTest.kt` | Drawer item routes to `NavRoutes.START_LOCATIONS` and closes drawer. |
| `TST-UI-117.2` | `KnownLocationsRepositoryTest.kt` | Concurrency serialization on `KnownLocationsDB-Thread` with 0 deadlocks. |
| `TST-UI-117.3` | `KnownLocationsViewModelTest.kt` | `updateLocation` atomically persists `name`, `altitude`, `source = MANUAL_USER`, `is_locked = 1`. |
| `TST-UI-117.4` | `LocationNameResolverTest.kt` | Geocoder returns place names, handles network errors gracefully, protects custom names. |
| `TST-UI-117.5` | `KnownLocationsUnitConversionTest.kt` | Metric (0.1m) and Imperial (integer feet) round-trip precision without drift. |
| `TST-UI-117.6` | `KnownLocationsScreenTest.kt` | List tab renders cards with Name, Altitude, Source badge, Hit Count, Coordinates. |
| `TST-UI-117.7` | `KnownLocationsViewModelTest.kt` | Viewport culling filters locations intersecting active `visibleRegion.latLngBounds`. |
| `TST-UI-117.8` | `EditKnownLocationDialogTest.kt` | Editing altitude to 530.0m auto-locks and updates source to `MANUAL_USER`. |
| `TST-UI-117.9` | `EditKnownLocationDialogTest.kt` | "Fetch DEM" updates altitude text to DEM elevation and sets source to `INTERNET_DEM`. |
| `TST-UI-117.10` | `KnownLocationsDatabaseManagerTest.kt` | `learnLocation` on locked/manual location increments `hitCount` while altitude is unchanged. |
| `TST-UI-117.11` | `TranslationParityTest.kt` | 100% localization parity across all 16 keys in all 9 locales. |
| `TST-UI-117.12` | Clean-Room Suite | `./gradlew testDebugUnitTest` passes with 0 failures and 0 errors. |
