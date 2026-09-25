# Walkthrough - ATT-1382: Improve Lieblingsorte UI

**Issue**: [ATT-1382](https://rainerblind.atlassian.net/browse/ATT-1382)  
**Implementation Sub-Task**: [ATT-1387](https://rainerblind.atlassian.net/browse/ATT-1387)  
**Parent Issue**: ATT-1382 (*[Verbesserung] Improve Lieblingsorte UI*)  
**Active Sprint**: `2026-39.2` | **Target Release**: `V4.9.38`  
**Requirement**: `REQ-UI-166` (referencing `REQ-UI-165`) | **Test Spec**: `TST-UI-118`  
**Branch**: `feature/ATT-1382`  

---

## 1. Executive Summary
Under **ATT-1382** (Sub-task **ATT-1387**), the known start locations feature (*Lieblingsorte*) originally introduced in `ATT-919` was comprehensively modernized and refined to align directly with `aTrainingTracker` core screens (`RouteTabbedScreen`, `WorkoutTabsScreen`, `SegmentsTabsScreen`).

The implementation incorporates all design system requirements and successive user refinements:
1. **Standard Tabbed Layout & Header Surface**:
   - Replaced generic Scaffold and TopAppBar with the app-standard `primaryContainer` header `Surface` (`statusBarsPadding()`, `titleLarge` text, `onPrimaryContainer` tints).
   - Removed hamburger icon, location count badge, and search filter input per user guidance.
   - Text-only `PrimaryTabRow` (`containerColor = surfaceContainerHighest`, `divider = {}`) hosting **"Liste"** and **"Karte"** tabs.
   - Fluid `HorizontalPager` with swipe navigation from List to Map (`userScrollEnabled = true` on List; disabled on Map so pan gestures interact exclusively with Google Map).
2. **Multi-Dimension Sorting Menu**:
   - Header sort action with `DropdownMenu` offering 4 distinct sorting dimensions in exact sequence:
     1. Starts / Aufzeichnungen (`RECORDINGS`, `@string/filter_section_recordings`)
     2. Closest to Current Location (`DISTANCE_TO_USER`, `@string/sort_closest`) — dynamically disabled/dimmed when GPS is unavailable
     3. Altitude (`ALTITUDE`, `@string/sort_altitude`)
     4. Name (`NAME`, `@string/sort_name`)
3. **Map Marker Long-Click Deletion Context Menu**:
   - Long-pressing a marker pin or geofence circle on the map opens an anchored contextual `DropdownMenu` with a Delete action (`@string/delete`).
   - Tapping Delete prompts `DeleteConfirmationDialog` (`@string/really_delete_format`) before deleting the location from SQLite.
4. **List Item Card Standardization (Variant 2 Modern Icon Badge) & System Insets**:
   - Cards compose `MappableListItem` (`ElevatedCard`, 16dp rounded corners, 2dp elevation).
   - 44dp rounded container displaying `R.drawable.my_locations` (heart pin).
   - Location title with flowing inline metrics: ascent altitude chip • starts count badge.
   - Trailing edit button (`R.drawable.ic_table_edit`).
   - Bottom list content padding explicitly incorporates `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` to ensure the last card is never obscured behind the system navigation bar.
5. **Unified Edit Dialog & Title Consistency**:
   - Standardized edit title to "Lieblingsort bearbeiten" / "Edit Favorite Location" across all 9 languages.
   - Tapping list item opens edit dialog with embedded map preview; tapping marker peek card opens edit dialog without redundant map.
6. **Automatic Legacy Start Location Name Healing & Geocoder Auto-Naming**:
   - On initialization of `KnownLocationsViewModel` and `KnownLocationsRepository`, `healLegacyNames()` runs in the background.
   - `LocationNameResolver.isPlaceholderName()` recognizes coordinate fallback patterns (e.g. `Startort (48.137, 11.576)`) across all 9 languages.
   - `KnownLocationsDatabaseManager`: `upsertLocationByGeofence()` and `learnLocation()` resolve and persist geocoded names for placeholder entries, while strictly preserving manual user edits.
7. **Drawer Hierarchy & Distinct Iconography**:
   - Positioned `drawer_start_locations` directly below `drawer_my_locations` (*Lieblingsstrecken*).
   - `drawer_start_locations` uses `R.drawable.my_locations` (location pin with embedded heart).
   - `drawer_my_locations` uses `R.drawable.ic_favorite_route` (route trajectory with heart overlay).
8. **Intelligent Fallback Centering & Custom Theme Heart Markers**:
   - Dynamic map marker generation using theme primary color with crisp white heart glyph (`createHeartPinMarker`).
   - Fallback camera target centers on location with highest visit count ($\operatorname{argmax}(\text{hitCount})$), defaulting to Munich coordinates when empty.
9. **100% Localization Parity Across 9 Locales**:
   - German: "Lieblingsorte", "Lieblingsort bearbeiten".
   - English: "Favorite Locations", "Edit Favorite Location".
   - Complete coverage in DE, EN, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Changes Implemented

### A. Navigation Drawer Hierarchy & Assets
* **[ic_favorite_route.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/drawable/ic_favorite_route.xml)**: Created new vector drawable combining the classic route icon with a heart overlay.
* **[AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt)**:
  - Extracted `@VisibleForTesting fun createDrawerGroups(startTrackingTitleRes: Int): List<DrawerGroup>`.
  - Reordered the `drawer__maps` group so `drawer_my_locations` directly precedes `drawer_start_locations`.
  - Assigned `R.drawable.ic_favorite_route` to `drawer_my_locations` and `R.drawable.my_locations` to `drawer_start_locations`.

### B. Screen Header, Tab Navigation & Sorting
* **[KnownLocationsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt)**:
  - Eliminated `Scaffold` + `TopAppBar` overhead, hamburger menu button, location count badge, and search filter input.
  - Constructed the standard `Surface(color = MaterialTheme.colorScheme.primaryContainer)` header with `statusBarsPadding()`, `titleLarge` screen title, and sort action button.
  - Added 4-way sorting `DropdownMenu`: Starts (`RECORDINGS`), Distance to User (`DISTANCE_TO_USER`), Altitude (`ALTITUDE`), and Name (`NAME` last).
  - Implemented text-only `PrimaryTabRow` with `containerColor = MaterialTheme.colorScheme.surfaceContainerHighest` and `divider = {}`.
  - Integrated `HorizontalPager` with `pagerState` enabling smooth swipe transitions between "Liste" (page 0) and "Karte" (page 1), with user swiping disabled on Map tab to reserve touch gestures for map manipulation.
* **[KnownLocationSortOrder.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationSortOrder.kt)**:
  - Defined sorting enumeration: `RECORDINGS`, `DISTANCE_TO_USER`, `ALTITUDE`, `NAME`.

### C. List & Map Presentation Modernization
* **[KnownLocationsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt)**:
  - Upgraded `KnownLocationCard` to use `MappableListItem` (`ElevatedCard`, 16dp rounded corners, 2dp elevation) with modern icon badge (Variant 2):
    - 44dp leading container with `R.drawable.my_locations` pin icon.
    - Title with inline metrics row (`ic_ascent` altitude, `control_start` starts count).
    - Trailing edit button (`R.drawable.ic_table_edit`).
    - Tap opens `EditKnownLocationDialog` directly with map preview.
    - Long-press triggers anchored `DropdownMenu` with Edit, Show on Map, and Delete.
  - **Bottom System Insets**: Added `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` to `LazyColumn` content padding, ensuring the last card is never obscured.
  - **Map Markers & Centering**: Map centers on `viewModel.getFallbackMapLocation()` ($\operatorname{argmax}(\text{hitCount})$). Custom theme-colored heart markers generated via `createHeartPinMarker`.
  - **Map Marker Context Menu**: Long-pressing a marker or geofence circle opens anchored `DropdownMenu` offering Delete, protected by `DeleteConfirmationDialog`.
* **[EditKnownLocationDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt)**:
  - Header icon updated to `R.drawable.ic_table_edit`.
  - Dialog title updated to `@string/known_location_edit_title` ("Lieblingsort bearbeiten" / "Edit Favorite Location").

### D. Automatic Legacy Name Healing & Geocoder Auto-Naming
* **[KnownLocationsViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModel.kt)**:
  - Added `repository.healLegacyNames()` call to `init` block so legacy placeholder entries heal immediately upon viewing the screen.
  - Implemented 4-way reactive sorting flow combining `sortOrder`, `locationsFlow`, and `userLocation`.
* **[KnownLocationsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/repositories/KnownLocationsRepository.kt)**:
  - Added `healLegacyNames()` call to `init` block.
  - In `healLegacyNames()`: removed `!loc.isLocked` restriction for placeholder names so locations locked by earlier altitude calibration runs are not blocked from name enrichment.
  - In `refreshDem()`: if existing name is a placeholder, resolves real geocoded name rather than preserving placeholder text.
* **[LocationNameResolver.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/location/LocationNameResolver.kt)**:
  - Expanded `isPlaceholderName()` to recognize coordinate patterns (e.g. `Startort (48.137, 11.576)`, raw coordinate regexes) across all locales.
* **[KnownLocationsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java)**:
  - In `upsertLocationByGeofence()`: persists resolved name when updating an existing location with a placeholder name.
  - In `learnLocation()`: resolves human-readable address via Geocoder on new and repeat hits with placeholder names.
  - In `healLegacyLocations()`: enriches placeholder names during batch maintenance.

### E. Localization Parity (9 Locales)
* Updated string resources across all 9 locales:
  - German (`values-de`): `drawer_start_locations` -> **`Lieblingsorte`**, `known_locations_title` -> **`Lieblingsorte`**, `known_location_edit_title` -> **`Lieblingsort bearbeiten`**, `known_locations_empty_title` -> **`Keine Lieblingsorte`**.
  - English (`values`): **`Favorite Locations`**, **`Edit Favorite Location`**, **`No Favorite Locations`**.
  - Spanish (`values-es`): **`Lugares favoritos`**, **`Editar lugar favorito`**.
  - French (`values-fr`): **`Lieux favoris`**, **`Modifier le lieu favori`**.
  - Italian (`values-it`): **`Luoghi preferiti`**, **`Modifica luogo preferito`**.
  - Japanese (`values-ja`): **`お気に入りの場所`**, **`お気に入りの場所を編集`**.
  - Dutch (`values-nl`): **`Favoriete locaties`**, **`Favoriete locatie bewerken`**.
  - Polish (`values-pl`): **`Ulubione miejsca`**, **`Edytuj ulubione miejsce`**.
  - Portuguese (`values-pt`): **`Locais favoritos`**, **`Editar local favorito`**.

---

## 3. Verification & Evidence

### A. Automated Unit Tests
* **[AppNavigationDrawerTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt)**:
  - `testDrawerMapsGroupItemOrderingAndIcons()`: Validates that `drawer_my_locations` precedes `drawer_start_locations`, and icons are `ic_favorite_route` and `my_locations`.
* **[KnownLocationsViewModelTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModelTest.kt)**:
  - `testFallbackMapLocation_resolvesMaxHitCountOrNull()`: Validates `argmax(hitCount)` resolution and `null` handling for empty state.
* **[KnownLocationsScreenTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenTest.kt)**:
  - `testAll16KnownLocationStringKeysExistAcrossAll9Locales()`: 100% string presence across all 9 languages.
  - `testLieblingsorteNamingAcrossLocales()`: Verifies German ("Lieblingsorte") and English ("Favorite Locations") translations.

### B. Clean-Room Regression & Build
* **Full Unit Test Suite**: `./gradlew testDebugUnitTest` executed with 100% success (all 712+ tests passed, 0 failures, 0 regressions).
* **Kotlin Compilation**: `./gradlew compileDebugKotlin` completed cleanly with `BUILD SUCCESSFUL`.
* **Debug APK Compilation**: `./gradlew assembleDebug` completed cleanly with `BUILD SUCCESSFUL`.
