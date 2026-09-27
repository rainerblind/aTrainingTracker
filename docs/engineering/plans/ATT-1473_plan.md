# Implementation Plan - ATT-1473: Customizable Geofence Radius with Live Map Preview in Edit Dialog

**Ticket**: [ATT-1473](https://rainerblind.atlassian.net/browse/ATT-1473)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Sub-task**: [ATT-1495](https://rainerblind.atlassian.net/browse/ATT-1495) (Stage 3 Impl-Plan)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-179` (*Customizable Geofence Radius & Live Map Preview in Lieblingsorte Edit Dialog*)  
**Test ID**: `TST-UI-131`  
**Analysis Reference**: `docs/engineering/analysis/ATT-1473_analysis.md`  
**Test Spec Reference**: `docs/engineering/test_specs/ATT-1473_test_spec.md`  
**Branch**: `feature/ATT-1473`  

---

## 1. Executive Summary

Ticket **ATT-1473** introduces the ability for athletes to customize the geofence radius of their "Lieblingsorte" (Known Start Locations) within the editing dialog (`EditKnownLocationDialog`), with real-time visual feedback on an embedded mini-map (`LocationMiniMap`).

While the underlying SQLite schema (`KnownLocations.RADIUS`), distance calculations, and map circle rendering already natively support variable radii, the UI currently lacks a slider control and the update piping drops the radius during saves. This implementation plan specifies the exact changes across all layers to deliver this capability with 100% architectural integrity, zero database migration risk, and complete 9-language localization parity.

---

## 2. Step-by-Step Implementation Strategy

### Phase 1: Database Persistence Layer (`KnownLocationsDatabaseManager.java`)
**File**: [KnownLocationsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java)

1. **Add Extended `updateLocation` Method**:
   ```java
   public void updateLocation(long id, @NonNull String name, double altitude, int radius, @NonNull ElevationSource source, boolean isLocked) {
       ContentValues contentValues = new ContentValues();
       contentValues.put(KnownLocationsDbHelper.NAME, name);
       contentValues.put(KnownLocationsDbHelper.ALTITUDE, altitude);
       contentValues.put(KnownLocationsDbHelper.RADIUS, radius);
       contentValues.put(KnownLocationsDbHelper.SOURCE, source.name());
       contentValues.put(KnownLocationsDbHelper.IS_LOCKED, isLocked ? 1 : 0);

       updateId(id, contentValues);
   }
   ```
2. **Retain Existing Overload**:
   Keep `updateLocation(long id, @NonNull String name, double altitude, @NonNull ElevationSource source, boolean isLocked)` without modifying `RADIUS`, ensuring legacy/background callers (e.g. `healLegacyNames`) never inadvertently overwrite user-configured radii.

---

### Phase 2: Domain Formatting Layer (`KnownLocationsUnitConversions.kt`)
**File**: [KnownLocationsUnitConversions.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsUnitConversions.kt)

1. **Add `formatRadius` Helper**:
   ```kotlin
   /**
    * Formats geofence radius for UI presentation.
    * In Metric mode: "200 m"
    * In Imperial mode: "200 m (656 ft)"
    */
   fun formatRadius(meters: Int, isMetric: Boolean): String {
       return if (isMetric) {
           "$meters m"
       } else {
           val feet = metersToFeet(meters.toDouble())
           "$meters m ($feet ft)"
       }
   }
   ```

---

### Phase 3: Repository Layer (`KnownLocationsRepository.kt`)
**File**: [KnownLocationsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/repositories/KnownLocationsRepository.kt)

1. **Add Parameterized `updateLocation`**:
   ```kotlin
   open suspend fun updateLocation(
       id: Long,
       name: String,
       altitude: Double,
       radius: Int,
       source: ElevationSource
   ) = withContext(dbDispatcher) {
       val isLocked = (source == ElevationSource.MANUAL_USER)
       databaseManager.updateLocation(id, name, altitude, radius, source, isLocked)
       loadLocations()
   }
   ```
2. **Retain 4-Argument Overload**:
   ```kotlin
   open suspend fun updateLocation(
       id: Long,
       name: String,
       altitude: Double,
       source: ElevationSource
   ) = withContext(dbDispatcher) {
       val isLocked = (source == ElevationSource.MANUAL_USER)
       databaseManager.updateLocation(id, name, altitude, source, isLocked)
       loadLocations()
   }
   ```
3. **Preserve Concurrency Invariant**:
   All database writes remain strictly serialized on `dbDispatcher` (`KnownLocationsDB-Thread`).

---

### Phase 4: ViewModel Layer (`KnownLocationsViewModel.kt`)
**File**: [KnownLocationsViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModel.kt)

1. **Add Parameterized `updateLocation`**:
   ```kotlin
   fun updateLocation(id: Long, name: String, altitude: Double, radius: Int, source: ElevationSource) {
       viewModelScope.launch {
           repository.updateLocation(id, name, altitude, radius, source)
           dismissEditDialog()
       }
   }
   ```
2. **Retain 4-Argument Overload**:
   ```kotlin
   fun updateLocation(id: Long, name: String, altitude: Double, source: ElevationSource) {
       viewModelScope.launch {
           repository.updateLocation(id, name, altitude, source)
           dismissEditDialog()
       }
   }
   ```

---

### Phase 5: Localization Resources (`strings.xml` in 9 Locales)
**Files**: `app/src/main/res/values*/strings.xml`

Add `known_location_radius_label` and `known_location_radius_format` across all 9 supported locales:
* **Default (EN)**:
  ```xml
  <string name="known_location_radius_label">Radius</string>
  <string name="known_location_radius_format">%1$s: %2$s</string>
  ```
* **German (`values-de`)**:
  ```xml
  <string name="known_location_radius_label">Erfassungsradius</string>
  <string name="known_location_radius_format">%1$s: %2$s</string>
  ```
* **Spanish (`values-es`)**:
  ```xml
  <string name="known_location_radius_label">Radio de cobertura</string>
  <string name="known_location_radius_format">%1$s: %2$s</string>
  ```
* **French (`values-fr`)**:
  ```xml
  <string name="known_location_radius_label">Rayon de couverture</string>
  <string name="known_location_radius_format">%1$s: %2$s</string>
  ```
* **Italian (`values-it`)**:
  ```xml
  <string name="known_location_radius_label">Raggio di copertura</string>
  <string name="known_location_radius_format">%1$s: %2$s</string>
  ```
* **Japanese (`values-ja`)**:
  ```xml
  <string name="known_location_radius_label">検知半径</string>
  <string name="known_location_radius_format">%1$s: %2$s</string>
  ```
* **Dutch (`values-nl`)**:
  ```xml
  <string name="known_location_radius_label">Detectiestraal</string>
  <string name="known_location_radius_format">%1$s: %2$s</string>
  ```
* **Polish (`values-pl`)**:
  ```xml
  <string name="known_location_radius_label">Promień wykrywania</string>
  <string name="known_location_radius_format">%1$s: %2$s</string>
  ```
* **Portuguese (`values-pt`)**:
  ```xml
  <string name="known_location_radius_label">Raio de cobertura</string>
  <string name="known_location_radius_format">%1$s: %2$s</string>
  ```

---

### Phase 6: Presentation Layer (`EditKnownLocationDialog.kt` & `KnownLocationsScreen.kt`)
**Files**:
* [EditKnownLocationDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt)
* [KnownLocationsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt)

1. **Update `EditKnownLocationDialog` & `EditKnownLocationSheetContent`**:
   * Add radius state:
     ```kotlin
     var radiusMeters by remember {
         mutableFloatStateOf(location.radius.coerceIn(50, 1000).toFloat())
     }
     ```
   * Update `onConfirm` callback parameter:
     ```kotlin
     onConfirm: (id: Long, name: String, altitudeMeters: Double, radiusMeters: Int, source: ElevationSource) -> Unit
     ```
   * Insert Slider UI below the altitude field:
     * Label with formatted readout:
       `Text(stringResource(R.string.known_location_radius_format, stringResource(R.string.known_location_radius_label), KnownLocationsUnitConversions.formatRadius(radiusMeters.roundToInt(), isMetric)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("edit_location_radius_label"))`
     * `Slider(value = radiusMeters, onValueChange = { radiusMeters = (round(it / 25f) * 25f).coerceIn(50f, 1000f) }, valueRange = 50f..1000f, steps = 37, modifier = Modifier.fillMaxWidth().testTag("edit_location_radius_slider"))`
   * In `onSave`:
     `onConfirm(location.id, finalName, parsed, radiusMeters.roundToInt(), currentSource)`
   * In `LocationMiniMap`:
     Pass dynamic `radius = radiusMeters.toDouble()`.
2. **Wire Call Site in `KnownLocationsScreen.kt`**:
   * In `uiState.selectedLocationForEdit?.let { itemToEdit -> ... }`:
     ```kotlin
     EditKnownLocationDialog(
         location = itemToEdit,
         isMetric = uiState.isMetric,
         showMap = uiState.showMapInEditDialog,
         onConfirm = { id, name, altitude, radius, source ->
             viewModel.updateLocation(id, name, altitude, radius, source)
         },
         onDismiss = { viewModel.dismissEditDialog() }
     )
     ```
3. **Update Previews in `EditKnownLocationDialog.kt`**:
   * Update mock preview callbacks to include `radius` parameter.

---

### Phase 7: Verification & Testing
1. **Unit Tests**:
   * `KnownLocationsDatabaseManagerTest.kt`: verify `updateLocation` with radius puts radius in ContentValues.
   * `KnownLocationsRepositoryTest.kt`: verify `updateLocation` persists radius and updates StateFlow.
   * `KnownLocationsViewModelTest.kt`: verify `updateLocation` passes radius and dismisses edit dialog.
   * `EditKnownLocationDialogTest.kt`: verify slider logic, metric/imperial formatting, and save callback.
   * `TranslationParityTest.kt`: verify string existence and formatting across all 9 locales.
2. **Clean-Room Regression Suite**:
   * Execute `./gradlew testDebugUnitTest` with `BypassSandbox: true`.

---

### Phase 8: System Documentation (`docs/requirements.md` & `docs/tests.md`)
1. Add `REQ-UI-179` to `docs/requirements.md`.
2. Add `TST-UI-131` to `docs/tests.md`.
