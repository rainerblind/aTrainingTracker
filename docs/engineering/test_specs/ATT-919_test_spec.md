# Test Specification & Requirement Synchronization - ATT-919: Altitude Correction: Create UI for this DB such that the user can set the correct altitude that is then never ever updated

## 1. Feature Overview & Test Scope

* **Feature Issue Key**: `ATT-919`
* **Sub-Task (Stage 2)**: `ATT-1379` (`[Test-Spec]`)
* **Parent Epic**: `ATT-235` (Sensor Calibration & Data Integrity)
* **Target Release (Fix Version)**: `V4.9.38` (Sprint `2026-39.2`)
* **Related Requirements**: `REQ-UI-165` (Net-New), `REQ-DAT-007` (Synchronized), `REQ-DAT-014`, `REQ-CON-013`, `REQ-UI-159`
* **Related Tests**: `TST-UI-117` (New Verification Specification)

### Objective
Provide athlete sovereignty over elevation calibration reference anchors through a dedicated Jetpack Compose management screen (`KnownLocationsScreen.kt`) and modal editing dialog (`EditKnownLocationDialog.kt`). The interface offers dual perspectives (List and Map with 200m circular geofence overlays), instant altitude locking (`is_locked = 1`, `source = MANUAL_USER`), on-demand online DEM elevation retrieval via `ElevationService`, and unit-aware formatting (m/ft), backed by a single-threaded coroutine SQLite dispatcher and 100% 9-language localization parity.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-165` (*Known Start Locations Management, Interactive Map Geofences & Altitude Lock UI*) targeting `KnownLocationsScreen.kt`, `KnownLocationsViewModel.kt`, `KnownLocationsRepository.kt`, `EditKnownLocationDialog.kt`, `AppNavigationDrawer.kt`, and `NavRoutes.kt`.
   - `REQ-DAT-007` (*Automated Altitude Reference Discovery & Stable Reference Elevation Preservation*) targeting `KnownLocationsDatabaseManager.java`.
2. **Historical Origin & Commit Trace**:
   - `REQ-DAT-007` originated in `ATT-39` (commit `9feb03b3`), refined in `ATT-448` (commit `308f9392`), and synchronized in `ATT-1366` (commit `5c647699`).
   - In commit `e1dcdc9e` on `feature/ATT-919`, the early-return guard in `KnownLocationsDatabaseManager.learnLocation()` that previously bypassed all updates for locked locations was refined to allow `hitCount` increments while strictly guarding altitude and coordinates against mutation.
3. **Root Reason for Existing Formulation**:
   - The initial formulation of `REQ-DAT-014` / `REQ-DAT-007` treated `is_locked = 1` as a complete write barrier across the entire SQLite row to guarantee that automated background learning and DEM batch sweeps would never modify a locked location.
   - However, `hitCount` represents athlete workout frequency (the number of workouts initiated within that 200m spatial geofence), which is purely informational usage telemetry. Completely halting `hitCount` updates for locked locations caused heavily utilized home, club, and track locations to remain stuck at "1 Start" once locked, creating misleading UI badges.
   - Decoupling `hitCount` incrementing from altitude modification allows athletes to see authentic workout frequency while permanently guaranteeing that `altitude`, `latitude`, and `longitude` are never altered or drifted by background processes.
4. **Preservation of Core Invariants**:
   - *Altitude Immutability*: Verified by automated unit test `testLearnLocation_lockedRecord_strictlyImmutable` in `KnownLocationsDatabaseManagerTest.kt`, proving zero SQLite writes to `ALTITUDE` or `LATITUDE`/`LONGITUDE` occur when `is_locked == 1`.
   - *Spatial Geofence*: 200m clustering radius and 5-decimal coordinate quantization remain unaltered.
   - *Concurrency*: Single-threaded coroutine SQLite dispatcher (`newSingleThreadExecutor("KnownLocationsDB-Thread")`) serializes all repository interactions, preventing coroutine deadlocks.
   - *Sensor Dispatch*: Null-safe barometric sensor initialization and calibration shift dispatch (`REQ-CON-013`) are preserved.

---

## 3. Synchronized Requirement Specification (`REQ-UI-165` & `REQ-DAT-007`)

### REQ-UI-165: Known Start Locations Management, Interactive Map Geofences & Altitude Lock UI
The system SHALL provide a dedicated Jetpack Compose management screen and interactive dual-perspective interface for known start locations, enabling athletes to audit, calibrate, lock, and manage spatial reference points and their reference altitudes (ATT-919):
1. **Navigation Drawer Entry Point**: The system SHALL integrate an entry item under the map destinations group (`drawer__maps`) in `AppNavigationDrawer`: `R.id.drawer_start_locations` -> `NavRoutes.START_LOCATIONS` with localized title ("Start Locations" / "Startorte") and pin icon (`Icons.Default.Place` or `R.drawable.ic_menu_locations`), navigating to `KnownLocationsScreen`.
2. **Dual-Perspective Presentation (`KnownLocationsScreen.kt`)**:
   - The screen SHALL feature a top `TabRow` toggling between "List" (`@string/known_locations_tab_list`) and "Map" (`@string/known_locations_tab_map`) perspectives.
   - *List Perspective*: Renders a scrollable `LazyColumn` of known location cards. Each card SHALL display:
     - Location Name (`name` or localized fallback `@string/known_location_unnamed_format` with formatted geodetic coordinate).
     - Reference Altitude formatted according to active unit preferences (Metric: meters $h_{\text{m}}$ with "m", Imperial: feet $h_{\text{ft}}$ with "ft" rounded to nearest integer).
     - Source Badge reflecting provenance (`INTERNET_DEM`, `MANUAL_USER`, `AUTO_LEARNED`, `GPS_FALLBACK`, `LEGACY_RAW`) with localized label and distinct semantic tint. Manual user edits are clearly identified by the `MANUAL_USER` badge.
     - Hit Count Badge displaying total workout starts originating from this location.
     - Geodetic Coordinates (Latitude, Longitude formatted to 5 decimal places).
     - Action overflow menu providing options: Edit (`@string/edit`), Center on Map (`@string/show_on_map`), and Delete (`@string/delete`).
   - *Map Perspective*: Renders an interactive Google Map (`ATrainingTrackerMap` / `GoogleMap`) displaying:
     - Marker pins for each known location placed at `(latitude, longitude)`.
     - Geofence circular overlays (`CircleOptions`) centered at each location with radius 200.0 meters, semi-transparent fill (`TTAlpha.HeatmapLow`), and subtle stroke (`primary` theme color).
     - Dynamic viewport bounds culling: the renderer SHALL evaluate camera bounds (`visibleRegion.latLngBounds`) to prioritize active rendering of markers and circles within the visible viewport.
     - Marker click listener: tapping any marker or geofence circle selects the location and displays a bottom peek sheet with location summary and an action to launch the edit dialog.
3. **Modal Edit Dialog (`EditKnownLocationDialog.kt`)**:
   - The dialog SHALL compose `AppModalBottomSheet` with localized title `@string/known_location_edit_title` and close dismiss icon.
   - *Name Field*: Outlined text field allowing custom naming of the location.
   - *Altitude Field*: Localized numeric text field supporting decimal comma/period input. In Metric mode, value is in meters ($0.1\text{m}$ precision); in Imperial mode, value is in feet.
   - *"Fetch from Internet (DEM)" Action*: Prominent button allowing on-demand elevation retrieval via `ElevationService.fetchElevation(lat, lon)`. While fetching, an inline progress indicator is displayed. Upon success, the altitude field updates to the DEM elevation and `source` is set to `INTERNET_DEM`.
   - *Action Bar*: Integrates `AppDialogActions.SaveCancel` with borderless "Abbrechen" (`@string/Cancel`) and filled "Speichern" (`@string/save`). Saving commits modifications to SQLite via `KnownLocationsRepository`. To eliminate user friction and protect manual calibrations without requiring manual lock management, whenever the user manually saves an altitude, the system SHALL automatically set `source = MANUAL_USER` and `is_locked = 1`. If the user fetches from DEM and saves, `source` is set to `INTERNET_DEM` and `is_locked = 0`.
4. **Single-Threaded SQLite Dispatcher & Coroutine Concurrency**: `KnownLocationsRepository` SHALL serialize all SQLite read and write transactions through a dedicated single-threaded dispatcher (`newSingleThreadExecutor("KnownLocationsDB-Thread").asCoroutineDispatcher()`), eliminating coroutine thread hopping and preventing deadlocks between Java `synchronized` monitors and Kotlin Coroutines.
5. **Unit Conversion Precision & Invariants**:
   - The authoritative database storage for altitude SHALL strictly remain SI meters (`Double`).
   - Manual editing in Imperial units SHALL convert feet to meters with explicit 0.1m rounding: $h_{\text{m}} = \text{round}(h_{\text{ft}} \times 0.3048 \times 10.0) / 10.0$.
   - Imperial display SHALL round to the nearest integer foot: $h_{\text{ft}} = \text{round}(h_{\text{m}} / 0.3048)$.
   - Location deletion: Deleting a location SHALL purge the record from `KnownLocations.TABLE` and refresh the UI reactively.
   - Workout start counting invariant: Repeat workout starts within a locked location's 200m geofence SHALL increment `hitCount` (`existing.hitCount + 1`) while strictly preserving locked altitude and coordinates (`REQ-DAT-007`).
6. **100% Localization Parity**: All user-facing strings across the screen, tabs, badges, actions, dialogs, and units SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

#### Acceptance Criteria (Given-When-Then):
* **AC-1 (Drawer Navigation)**:
  - *Given* the athlete opens the navigation drawer,
  - *When* "Start Locations" / "Startorte" is tapped,
  - *Then* `NavController` SHALL navigate to `NavRoutes.START_LOCATIONS` and render `KnownLocationsScreen`.
* **AC-2 (List View Presentation & Badges)**:
  - *Given* the List tab on `KnownLocationsScreen`,
  - *When* viewing stored locations,
  - *Then* each card SHALL display Name, formatted Altitude with units, Source badge, Hit Count badge, 5-decimal coordinates, and Action overflow menu without redundant lock toggles.
* **AC-3 (Manual Altitude Calibration & Automatic Lock)**:
  - *Given* an athlete editing a location in `EditKnownLocationDialog`,
  - *When* the athlete types a new altitude (e.g. 525.5m) and taps "Speichern",
  - *Then* SQLite SHALL automatically update `altitude = 525.5`, `is_locked = 1`, and `source = MANUAL_USER`.
* **AC-4 (Online DEM Elevation Retrieval)**:
  - *Given* an athlete tapping "Fetch from Internet (DEM)" in the edit dialog,
  - *When* the API returns elevation 523.0m and user taps "Speichern",
  - *Then* the altitude field updates to 523.0m, and SQLite persists `source = INTERNET_DEM` and `is_locked = 0`.
* **AC-5 (Interactive Map & 200m Geofences)**:
  - *Given* the Map tab on `KnownLocationsScreen`,
  - *When* displayed,
  - *Then* Google Map SHALL render marker pins and 200m circular geofence overlays for each known location within the camera viewport.
* **AC-6 (Workout Start Counting on Locked Locations)**:
  - *Given* an active workout start at a locked location,
  - *When* the session starts,
  - *Then* `hitCount` SHALL increment by 1 while the locked altitude remains immutable.

---

## 4. Test Verification Procedures (`TST-UI-117`)

### TST-UI-117: Known Start Locations Management, Interactive Map Geofences & Altitude Lock Verification

| Test Step | Target Component | Action / Inputs | Expected Result | Pass Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **TST-UI-117.1** | `AppNavigationDrawerTest.kt` | Navigate via drawer item `R.id.drawer_start_locations`. | `NavController` routes to `NavRoutes.START_LOCATIONS` and drawer closes. | Navigation target == `NavRoutes.START_LOCATIONS`. |
| **TST-UI-117.2** | `KnownLocationsViewModelTest.kt` | Execute concurrent read/write operations via `KnownLocationsRepository`. | All SQLite queries execute serially on `KnownLocationsDB-Thread` without deadlocks or thread-hopping exceptions. | 0 deadlocks, clean completion. |
| **TST-UI-117.3** | `KnownLocationsViewModelTest.kt` | Call `updateLocation(locationId, name, altitude, source = MANUAL_USER)`. | Database row updates with `altitude`, `source = MANUAL_USER`, and `is_locked = 1` atomically. | `is_locked == 1`, `source == MANUAL_USER`. |
| **TST-UI-117.4** | `KnownLocationsUnitConversionTest.kt` | Test Metric/Imperial round-trip conversions (e.g. 1000 ft -> 304.8 m -> 1000 ft; 525.5 m parsing). | Parsing and formatting preserve 0.1m precision in Metric and nearest integer in Imperial without drift. | Precision verified, drift == 0. |
| **TST-UI-117.5** | `KnownLocationsScreenTest.kt` | Render `KnownLocationsScreen` in List mode. | Cards display Location Name, Altitude (m/ft), Source badge, Hit Count badge, coordinates (5 decimals), and action menu (no lock toggle). | All card elements rendered and verified. |
| **TST-UI-117.6** | `KnownLocationsViewModelTest.kt` / `KnownLocationsScreenTest.kt` | Viewport culling test: supply 50 locations across multiple regions; execute `filterByViewport(visibleBounds)`. | Returned list contains strictly the subset of locations whose coordinates intersect `visibleBounds`. | Filtered count matches expected viewport subset. |
| **TST-UI-117.7** | `EditKnownLocationDialogTest.kt` | Edit altitude to 530.0m in `EditKnownLocationDialog` and tap "Speichern". | Callback receives `altitude = 530.0`, `isLocked = true`, `source = MANUAL_USER`; committed to SQLite. | `is_locked == 1`, `source == MANUAL_USER`. |
| **TST-UI-117.8** | `EditKnownLocationDialogTest.kt` | Tap "Fetch from Internet (DEM)" with mocked `ElevationService` returning 520.0m. | Progress indicator shows during fetch; altitude updates to "520.0"; source updates to `INTERNET_DEM`. | `source == INTERNET_DEM`, `altitude == 520.0`. |
| **TST-UI-117.9** | `KnownLocationsDatabaseManagerTest.kt` | Invoke `learnLocation(pos, 480.0, ExtremaType.START)` on locked location (`is_locked = 1, altitude = 520.0, hitCount = 5`). | `altitude` remains strictly 520.0m, coordinates remain unchanged, and `hitCount` increments to 6. | `altitude == 520.0`, `hitCount == 6`. |
| **TST-UI-117.10** | `TranslationParityTest.kt` | Audit all 16 new string resources across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) for keys: `drawer_start_locations`, `known_locations_title`, `known_locations_tab_list`, `known_locations_tab_map`, `known_locations_search_hint`, `known_locations_empty_title`, `known_locations_empty_desc`, `known_location_unnamed_format`, `known_locations_starts_count`, `source_internet_dem`, `source_manual_user`, `source_auto_learned`, `source_gps_fallback`, `source_legacy_raw`, `known_location_edit_title`, `known_location_fetch_dem`. | 0 missing translation keys, 100% localization parity across 9 locales. | Full parity across 9 locales. |
| **TST-UI-117.11** | Full Repository | Execute clean-room unit regression: `./gradlew testDebugUnitTest`. | All test suites pass with 0 failures and 0 errors. | 100% clean-room test pass. |

---

## 5. ASPICE Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-165` (New) | `TST-UI-117` | Automated Compose UI Tests, ViewModel Unit Tests, and Reflection Audits | Specified |
| `REQ-DAT-007` (Synchronized) | `TST-DAT-009`, `TST-UI-117.9` | Automated JUnit / MockK (`KnownLocationsDatabaseManagerTest.kt`) | Verified |
| `REQ-DAT-014` | `TST-DAT-008` | Automated JUnit (`ElevationServiceTest.kt`, `KnownLocationsDatabaseManagerTest.kt`) | Verified |
| `REQ-CON-013` | `TST-CON-004` | Automated Unit Test (`AltitudeFromPressureDeviceTest.kt`) | Verified |
| `REQ-UI-159` | `TST-NAV-009` | Automated Navigation Test (`SingleActivityNavigationTest.kt`) | Verified |
