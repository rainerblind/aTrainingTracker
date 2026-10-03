# Test Specification - ATT-1473: Customizable Geofence Radius with Live Map Preview in Edit Dialog

**Ticket**: [ATT-1473](https://rainerblind.atlassian.net/browse/ATT-1473)  
**Sub-task**: [ATT-1494](https://rainerblind.atlassian.net/browse/ATT-1494) (Stage 2 Test-Spec)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.3`  
**Requirement Mapping**: `REQ-UI-179` (*Customizable Geofence Radius & Live Map Preview in Lieblingsorte Edit Dialog*, extending `REQ-UI-165` and `REQ-UI-166`)  
**Test Spec ID**: `TST-UI-131`  
**Branch**: `feature/ATT-1473`  

---

## 1. Overview & Verification Strategy

This test specification defines the verification strategy for customizable geofence radius adjustment and real-time live map preview in the "Lieblingsorte" edit dialog (`EditKnownLocationDialog`), covering:
1. **Presentation & Slider Interactivity**: Ensuring the slider spans 50m to 1,000m with 25m step increments, formats readouts dynamically in metric/imperial modes, and passes the updated radius to `onConfirm`.
2. **Real-Time Map Preview Dynamics**: Ensuring the circular overlay on `LocationMiniMap` reactively consumes `radius = currentRadius.toDouble()`, updating in real-time as the slider moves without requiring dialog save/dismiss.
3. **ViewModel & State Delegation**: Ensuring `KnownLocationsViewModel.updateLocation` accepts the new radius, delegates cleanly to `KnownLocationsRepository`, and closes the dialog.
4. **Repository & Concurrency Serialization**: Ensuring `KnownLocationsRepository.updateLocation` accepts `radius: Int`, dispatches on the single-thread `dbDispatcher` (`KnownLocationsDB-Thread`), and emits the updated `KnownLocationItem` through `locationsFlow`.
5. **SQLite Persistence**: Ensuring `KnownLocationsDatabaseManager.updateLocation` persists the `RADIUS` column to SQLite.
6. **9-Language Localization & Positional Specifier Integrity**: Ensuring all radius strings (`known_location_radius_label`, `known_location_radius_format`) exist across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) and pass `TranslationParityTest`.
7. **Clean-Room Regression**: Ensuring `./gradlew testDebugUnitTest` runs with 100% pass rate.

---

## 2. Test Cases & Verification Matrix

### Test Case 1: `testUpdateLocation_withCustomRadius_persistsRadiusAndRefreshesFlow` (Repository Unit Test - `TST-UI-131.1`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/KnownLocationsRepositoryTest.kt`
* **Goal**: Verify that updating a location with a custom radius persists the radius to `mockDbManager` and reflects in `locationsFlow`.
* **Preconditions**:
  * `mockDbManager` configured with initial location: `id = 10L, radius = 200`.
* **Action**:
  * Invoke `repository.updateLocation(id = 10L, name = "Trailhead North", altitude = 450.0, radius = 500, source = ElevationSource.MANUAL_USER)`.
* **Expected Result**:
  * `verify(exactly = 1) { mockDbManager.updateLocation(10L, "Trailhead North", 450.0, 500, ElevationSource.MANUAL_USER, true) }`.
  * `repository.locationsFlow.value[0].radius == 500`.
  * Invariant: `isLocked == true` because `source == MANUAL_USER`.

### Test Case 2: `testUpdateLocation_backwardCompatibleOverload_delegatesCleanly` (Repository Unit Test - `TST-UI-131.2`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/KnownLocationsRepositoryTest.kt`
* **Goal**: Verify that calling the 4-argument overload `updateLocation(id, name, altitude, source)` preserves existing behavior without errors.
* **Preconditions**:
  * Repository initialized with `mockDbManager`.
* **Action**:
  * Invoke `repository.updateLocation(id = 5L, name = "Downtown", altitude = 510.0, source = ElevationSource.MANUAL_USER)`.
* **Expected Result**:
  * Call delegates smoothly to `mockDbManager.updateLocation`.

### Test Case 3: `testViewModel_updateLocation_propagatesRadiusAndDismissesDialog` (ViewModel Unit Test - `TST-UI-131.3`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModelTest.kt`
* **Goal**: Verify that `KnownLocationsViewModel.updateLocation` accepts `radius` and passes all parameters to `repository.updateLocation`, closing the edit dialog.
* **Preconditions**:
  * `KnownLocationsViewModel` initialized with mocked `KnownLocationsRepository`.
  * Open edit dialog for sample location (`id = 1L`).
* **Action**:
  * Invoke `viewModel.updateLocation(id = 1L, name = "Home Spot", altitude = 520.0, radius = 75, source = ElevationSource.MANUAL_USER)`.
* **Expected Result**:
  * `coVerify(exactly = 1) { mockRepository.updateLocation(1L, "Home Spot", 520.0, 75, ElevationSource.MANUAL_USER) }`.
  * `viewModel.uiState.value.selectedLocationForEdit == null` (dialog dismissed).

### Test Case 4: `testRadiusFormatting_metricAndImperial` (Unit Conversion Test - `TST-UI-131.4`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialogTest.kt`
* **Goal**: Verify `formatRadius` generates user-friendly string readouts in both metric and imperial modes.
* **Preconditions**:
  * Input radius: `200` meters.
* **Action & Expected Result**:
  * Metric mode (`isMetric = true`): `"200 m"`.
  * Imperial mode (`isMetric = false`): `"200 m (656 ft)"`.
  * Edge cases:
    * `50` meters: Metric `"50 m"`, Imperial `"50 m (164 ft)"`.
    * `1000` meters: Metric `"1000 m"`, Imperial `"1000 m (3281 ft)"`.

### Test Case 5: `testEditDialog_radiusSlider_initializesAndEmitsUpdatedRadius` (Dialog Logic Test - `TST-UI-131.5`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialogTest.kt`
* **Goal**: Verify that changing the radius slider updates state and propagates to `onConfirm`.
* **Preconditions**:
  * Sample location with initial radius `200`.
* **Action**:
  * User adjusts slider to `350` meters.
  * User taps "Speichern" (`onSave`).
* **Expected Result**:
  * `onConfirm` callback receives `radiusMeters = 350`.
  * Slider value is clamped within `[50, 1000]`.

### Test Case 6: `testDatabaseManager_updateLocation_persistsRadiusColumn` (Database Unit Test - `TST-UI-131.6`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManagerTest.kt`
* **Goal**: Verify `KnownLocationsDatabaseManager.updateLocation(id, name, altitude, radius, source, isLocked)` writes `KnownLocationsDbHelper.RADIUS` into `ContentValues`.
* **Preconditions**:
  * Mock SQLite database and `KnownLocationsDatabaseManager`.
* **Action**:
  * Invoke `dbManager.updateLocation(42L, "Trail Center", 300.0, 450, ElevationSource.MANUAL_USER, true)`.
* **Expected Result**:
  * Verify `ContentValues` passed to `db.update` contains `radius = 450`.

### Test Case 7: `testLocalizationParity_radiusStrings` (Localization Parity Test - `TST-UI-131.7`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify 100% presence and formatting compliance of new string resources across all 9 supported locales:
  * `known_location_radius_label`
  * `known_location_radius_format`
* **Expected Result**:
  * Present and non-empty in `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  * Zero missing translations or illegal format specifiers.

### Test Case 8: Clean-Room Regression Suite (`TST-UI-131.8`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% test pass rate across the entire project with zero broken invariants.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
|:---|:---|:---|:---|:---|
| `TST-UI-131.1` | Repository | `KnownLocationsRepository.updateLocation` | `REQ-UI-179` | Specified |
| `TST-UI-131.2` | Repository | Backward-compatible overload | `REQ-UI-179` | Specified |
| `TST-UI-131.3` | ViewModel | `KnownLocationsViewModel.updateLocation` | `REQ-UI-179` | Specified |
| `TST-UI-131.4` | Formatting | `KnownLocationsUnitConversions.formatRadius` | `REQ-UI-179` | Specified |
| `TST-UI-131.5` | Compose Dialog | `EditKnownLocationDialog.kt` slider & confirm | `REQ-UI-179` | Specified |
| `TST-UI-131.6` | Database | `KnownLocationsDatabaseManager.updateLocation` | `REQ-UI-179` | Specified |
| `TST-UI-131.7` | Localization | 9-Language string resource audit | `REQ-UI-179`, `REQ-UI-106` | Specified |
| `TST-UI-131.8` | Regression | Full testDebugUnitTest suite | `REQ-PRO-001` | Specified |
