# Test Specification - ATT-1449: Show Lieblingsorte on General Map & Streamline Management View

**Ticket**: [ATT-1449](https://rainerblind.atlassian.net/browse/ATT-1449)  
**Sub-task**: [ATT-1499](https://rainerblind.atlassian.net/browse/ATT-1499) (Stage 2 Test-Spec)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.3`  
**Requirement Mapping**: `REQ-UI-180` (*Display Favorite Locations on Central Navigation Map and Streamline Management View*, extending `REQ-UI-165` and `REQ-UI-166`)  
**Test Spec ID**: `TST-UI-132`  
**Branch**: `feature/ATT-1449`  

---

## 1. Overview & Verification Strategy

This test specification defines the verification strategy for consolidating favorite start locations (*Lieblingsorte*) onto the central navigation map (`NavRoutes.MAP` / `MapScreenWithTrack.kt`) and streamlining `KnownLocationsScreen.kt` into a clean, single-perspective list, covering:
1. **Central Map DSL & Layer Rendering**: Ensuring `MapContentScope.knownLocations` renders theme-colored heart-pin markers (`createHeartPinMarker`) and circular geofence overlays with radius matching `location.radius.toDouble()`.
2. **Central Map State Flow**: Ensuring `MapFragmentWithTrackViewModel` collects `KnownLocationsRepository.locationsFlow`, exposes `knownLocations` in `MapFragmentUIState`, and provides delegation for in-place location updates.
3. **Interactive Bottom Peek Sheet**: Ensuring tapping a location pin on the central map sets `selectedLocationId`, expands `BottomSheetScaffold` to peek height ($140\text{dp} + \text{navBarHeight}$), displays location name, altitude, start count, and an edit action button, while map taps or BackHandler collapses the sheet.
4. **Targeted Deep Link Navigation**: Ensuring `NavRoutes.map(locationId)` constructs `"map?locationId={locationId}"`, `NavRoutes.toDrawerItemId` cleanly resolves to `R.id.drawer_map`, and `MapScreenWithTrack` animates camera focus to the target coordinates and opens its peek card.
5. **Streamlined Management Screen**: Ensuring `KnownLocationsScreen.kt` removes the redundant "Karte" tab and `HorizontalPager`, and that selecting "Show on Map" in the item overflow menu invokes `onShowOnMap(location.id)`.
6. **Streamlined ViewModel**: Ensuring `KnownLocationsViewModel` removes dead map culling, viewport listeners, and tab state while maintaining 100% of list filtering, sorting, and editing capabilities.
7. **Clean-Room Regression**: Ensuring `./gradlew testDebugUnitTest` runs with 100% pass rate across all project modules.

---

## 2. Test Cases & Verification Matrix

### Test Case 1: `testUiState_combinesKnownLocationsFlow` (ViewModel Unit Test - `TST-UI-132.1`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrackViewModelTest.kt`
* **Goal**: Verify that `MapFragmentWithTrackViewModel` collects `KnownLocationsRepository.locationsFlow` and maps it into `MapFragmentUIState.knownLocations`.
* **Preconditions**:
  * Mock `KnownLocationsRepository` providing a `MutableStateFlow<List<KnownLocationItem>>`.
  * Initial list contains 2 sample locations: "Home" (`id = 1L`) and "Park" (`id = 2L`).
* **Action**:
  * Initialize `MapFragmentWithTrackViewModel`.
* **Expected Result**:
  * `viewModel.uiState.value.knownLocations.size == 2`.
  * Items in `uiState.knownLocations` match the repository flow items.

### Test Case 2: `testUpdateKnownLocation_delegatesToRepository` (ViewModel Unit Test - `TST-UI-132.2`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrackViewModelTest.kt`
* **Goal**: Verify that `viewModel.updateKnownLocation` delegates cleanly to `KnownLocationsRepository.updateLocation`.
* **Preconditions**:
  * Mock `KnownLocationsRepository`.
* **Action**:
  * Call `viewModel.updateKnownLocation(id = 1L, name = "New Home", altitude = 520.0, radius = 150, source = ElevationSource.MANUAL_USER)`.
* **Expected Result**:
  * `coVerify(exactly = 1) { mockKnownLocationsRepository.updateLocation(1L, "New Home", 520.0, 150, ElevationSource.MANUAL_USER) }`.

### Test Case 3: `testMapRoute_withAndWithoutLocationId_andDrawerMapping` (Navigation Unit Test - `TST-UI-132.3`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutesTest.kt`
* **Goal**: Verify route construction and drawer item mapping for central map with and without `locationId`.
* **Action & Expected Result**:
  * `NavRoutes.map()` returns `"map"`.
  * `NavRoutes.map(null)` returns `"map"`.
  * `NavRoutes.map(-1L)` returns `"map"`.
  * `NavRoutes.map(42L)` returns `"map?locationId=42"`.
  * `NavRoutes.toDrawerItemId("map")` returns `R.id.drawer_map`.
  * `NavRoutes.toDrawerItemId("map?locationId=42")` returns `R.id.drawer_map`.

### Test Case 4: `testStreamlinedViewModel_retainsListSortingAndFilteringWithoutMapState` (ViewModel Unit Test - `TST-UI-132.4`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModelTest.kt`
* **Goal**: Verify that removing map tab state from `KnownLocationsViewModel` does not compromise list sorting, searching, or editing.
* **Preconditions**:
  * `KnownLocationsViewModel` initialized with mocked repository.
* **Action**:
  * Apply sort order (e.g. by Name, Starts, Altitude).
  * Apply search query filter.
* **Expected Result**:
  * `filteredLocations` updates accurately.
  * No map viewport culling logic is executed.
  * Edit dialog state opens and closes normally.

### Test Case 5: `testMapScreen_locationPinClick_opensPeekCard` (Compose Interaction Test - `TST-UI-132.5`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrackTest.kt`
* **Goal**: Verify that selecting a favorite location pin on the central map expands the bottom peek sheet with location details.
* **Preconditions**:
  * `MapScreenWithTrack` rendered with sample location "Trailhead" (`altitude = 450m`, `startsCount = 12`).
* **Action**:
  * Trigger location click for "Trailhead".
* **Expected Result**:
  * Bottom peek sheet expands to peek height ($140\text{dp} + \text{navBarHeight}$).
  * Location name "Trailhead", altitude, and starts count are displayed.
  * Edit button is visible.

### Test Case 6: `testMapScreen_targetLocationId_centersCameraAndOpensPeekCard` (Navigation Integration Test - `TST-UI-132.6`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrackTest.kt`
* **Goal**: Verify that providing `targetLocationId` upon entering `MapScreenWithTrack` automatically selects the location and animates the camera.
* **Preconditions**:
  * `MapScreenWithTrack` rendered with `targetLocationId = 1L`.
* **Action**:
  * Screen composition executes.
* **Expected Result**:
  * `selectedLocationId == 1L`.
  * Camera moves/animates to target location coordinates.
  * Bottom peek card for location 1 is displayed.

### Test Case 7: `testKnownLocationsScreen_showOnMap_triggersCallback` (Compose Unit Test - `TST-UI-132.7`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenTest.kt`
* **Goal**: Verify that tapping "Show on Map" in the card overflow menu invokes `onShowOnMap(location.id)`.
* **Preconditions**:
  * `KnownLocationsScreen` rendered with sample location (`id = 99L`).
* **Action**:
  * Open card overflow menu and tap "Show on Map" (`@string/known_location_show_map`).
* **Expected Result**:
  * Callback `onShowOnMap` is invoked with `99L`.
  * No "Karte" tab is present in the screen.

### Test Case 8: On-Device Pixel 10 End-to-End Verification (`TST-UI-132.8`)
* **Device**: Physical Google Pixel 10 (`66020DLCR002FL`), Light Mode.
* **Procedure**:
  1. Open app and navigate to "Lieblingsorte" via drawer (`R.id.drawer_start_locations`).
  2. Verify screen renders as a single-perspective list without tabs or embedded map.
  3. Open 3-dot overflow menu on a location card, tap "Show on Map".
  4. Verify app transitions to the central map, centers camera on the location pin, and displays the peek sheet.
  5. Tap "Bearbeiten" on the peek sheet; verify `EditKnownLocationDialog` opens.
  6. Tap map background; verify peek sheet collapses.
  7. Navigate to central map directly via drawer; verify all favorite location pins and circular geofence overlays appear alongside routes/segments.

### Test Case 9: Clean-Room Full Suite Regression (`TST-UI-132.9`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% test pass rate with 0 regressions across all modules.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
|:---|:---|:---|:---|:---|
| `TST-UI-132.1` | Central Map ViewModel | `MapFragmentWithTrackViewModel.uiState` | `REQ-UI-180` | Specified |
| `TST-UI-132.2` | Central Map ViewModel | `MapFragmentWithTrackViewModel.updateKnownLocation` | `REQ-UI-180` | Specified |
| `TST-UI-132.3` | Navigation | `NavRoutes.map` & `toDrawerItemId` | `REQ-UI-180` | Specified |
| `TST-UI-132.4` | Management ViewModel | `KnownLocationsViewModel` list operations | `REQ-UI-180` | Specified |
| `TST-UI-132.5` | Central Map UI | `MapScreenWithTrack` location pin click & peek | `REQ-UI-180` | Specified |
| `TST-UI-132.6` | Central Map UI | `MapScreenWithTrack` `targetLocationId` handling | `REQ-UI-180` | Specified |
| `TST-UI-132.7` | Management Screen | `KnownLocationsScreen` overflow "Show on Map" | `REQ-UI-180` | Specified |
| `TST-UI-132.8` | E2E System | On-device Pixel 10 workflow verification | `REQ-UI-180` | Specified |
| `TST-UI-132.9` | Regression | Full-suite test execution | `REQ-UI-180` | Specified |
