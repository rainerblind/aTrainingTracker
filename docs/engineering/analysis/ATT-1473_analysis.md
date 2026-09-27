# Engineering Analysis - ATT-1473: Customizable Geofence Radius with Live Map Preview in Edit Dialog

## 1. Executive Summary & Problem Overview

* **Issue Key**: `ATT-1473`
* **Sub-task Key**: `ATT-1493` (Stage 1 Analysis)
* **Parent Epic**: `ATT-1396` (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)
* **Sprint / FixVersion**: Sprint `2026-39.3` / `V4.9.38`
* **Associated Requirements**: `REQ-UI-179` (*Customizable Geofence Radius & Live Map Preview in Lieblingsorte Edit Dialog*, extending `REQ-UI-165` and `REQ-UI-166`) in `docs/requirements.md`
* **Associated Verification**: `TST-UI-131` in `docs/tests.md`
* **Target Components**:
  * Presentation Layer: `EditKnownLocationDialog.kt`, `KnownLocationsScreen.kt`
  * Domain / Formatting: `KnownLocationsUnitConversions.kt`
  * ViewModel Layer: `KnownLocationsViewModel.kt`
  * Repository Layer: `KnownLocationsRepository.kt`
  * Database Layer: `KnownLocationsDatabaseManager.java`
  * Localization: `strings.xml` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT)

---

## 2. Problem Statement & Athletic Motivation

### 2.1 Athletic Context
Athletes start workouts in diverse spatial and environmental contexts:
1. **Dense Urban Settings**:
   * In urban centers or apartment blocks, a fixed 200m radius is too coarse. Two distinct start locations (e.g. front doorstep vs. an adjacent park entrance 150m away) inadvertently collide into a single known location.
   * Athletes require a tighter geofence radius (e.g. 50m – 100m) to accurately distinguish nearby start points.
2. **Sprawling Trailheads & Sports Facilities**:
   * At large athletic facilities, forest trailheads, rowing centers, or stadium parking lots, athletes may park or begin workouts at different corners that exceed 200m from a single fixed center coordinate.
   * Athletes require an expanded geofence radius (e.g. 300m – 500m, up to 1,000m) to guarantee reliable start detection and altimeter calibration regardless of where they park.

### 2.2 Current Technical State & Gap Analysis
Under the hood, the database and spatial lookup logic are already architected to support variable per-location radii:
* **Database**: `KnownLocationsDatabaseManager.RADIUS` (`radius int`) column exists in SQLite table `KnownLocations` (default 200m), and `MyLocation.radius` correctly maps to this field.
* **Spatial Matching**: `KnownLocationsDatabaseManager.getMyLocation(pos)` already evaluates distance against the location's specific radius:
  ```java
  if (distance < radius) { ... }
  ```
* **Domain Model**: `KnownLocationItem.radius` is exposed in `KnownLocationsRepository.kt`.
* **Map Rendering**: `LocationMiniMap` and `KnownLocationsScreen` render `Circle(center = location.latLng, radius = location.radius.toDouble())`.

**The Critical Missing Link**:
The user interface currently lacks any mechanism to view or adjust this radius:
1. `EditKnownLocationDialog.kt` contains text fields only for `Name` and `Altitude`. It lacks a radius control.
2. `KnownLocationsDatabaseManager.updateLocation(long id, String name, double altitude, ElevationSource source, boolean isLocked)` does not update the `RADIUS` column.
3. `KnownLocationsRepository.updateLocation(...)` does not accept or pass a radius parameter.
4. `KnownLocationsViewModel.updateLocation(...)` does not accept or pass a radius parameter.
5. In `EditKnownLocationDialog.kt`, `LocationMiniMap` receives `location.radius` statically, so it cannot preview radius adjustments in real-time before saving.

---

## 3. Technical Architecture & Scope Specification

```
+-----------------------------------------------------------------------------------+
|                            EditKnownLocationDialog.kt                             |
|  - Name OutlinedTextField                                                         |
|  - Altitude OutlinedTextField                                                     |
|  - NEW: Radius Slider (50m - 1000m, step=25m) with Readout Label                  |
|  - Embedded LocationMiniMap (Circle dynamically updates radius=sliderValue in real-time) |
|  - Save Action -> onConfirm(id, name, altitude, radius, source)                   |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                            KnownLocationsViewModel.kt                             |
|  fun updateLocation(id: Long, name: String, altitude: Double, radius: Int, ...)   |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                            KnownLocationsRepository.kt                            |
|  suspend fun updateLocation(id: Long, name: String, altitude: Double, radius: Int, ..)|
|  - dispatches on single-threaded dbDispatcher (KnownLocationsDB-Thread)          |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                        KnownLocationsDatabaseManager.java                         |
|  public void updateLocation(long id, String name, double altitude, int radius, ..)|
|  - writes values.put(KnownLocationsDbHelper.RADIUS, radius) to SQLite TABLE      |
+-----------------------------------------------------------------------------------+
```

### 3.1 Presentation Layer (`EditKnownLocationDialog.kt`)
1. **Slider Specification**:
   * Positioned immediately below the Reference Altitude field.
   * Value range: `50f` to `1000f` (meters).
   * Step increment: `25m` (`steps = 37`, or dynamic snapping: `round(value / 25f) * 25f`), providing smooth, granular control (e.g. 50m, 75m, 100m, 125m, 150m ... 1000m).
   * Initial state: `remember { mutableFloatStateOf(location.radius.coerceIn(50, 1000).toFloat()) }`.
2. **Label & Metric/Imperial Unit Readout**:
   * Clear label with dynamic readout formatted according to user unit preference:
     * Metric: `Radius: 200 m` (or localized: `Erfassungsradius: 200 m`).
     * Imperial: `Radius: 200 m (656 ft)` (or localized format).
   * Test tags: `testTag("edit_location_radius_slider")`, `testTag("edit_location_radius_label")`.
3. **Live Map Preview**:
   * The embedded `LocationMiniMap` circle consumes `radius = currentRadius.toDouble()`.
   * As the user drags the slider, the circular blue geofence overlay expands or contracts in 60 FPS real-time, providing immediate visual feedback of geographical coverage against roads, buildings, and trailheads.
   * Works in both real Google Maps mode and in `LocalInspectionMode` Compose preview mode.
4. **Dialog Callback Contract**:
   * Update `onConfirm` signature to:
     ```kotlin
     onConfirm: (id: Long, name: String, altitudeMeters: Double, radiusMeters: Int, source: ElevationSource) -> Unit
     ```
   * Updated in both `EditKnownLocationDialog` and `EditKnownLocationSheetContent`.

### 3.2 ViewModel Layer (`KnownLocationsViewModel.kt`)
1. Update `updateLocation`:
   ```kotlin
   fun updateLocation(id: Long, name: String, altitude: Double, radius: Int, source: ElevationSource) {
       viewModelScope.launch {
           repository.updateLocation(id, name, altitude, radius, source)
           dismissEditDialog()
       }
   }
   ```
2. Maintain overloaded `fun updateLocation(id: Long, name: String, altitude: Double, source: ElevationSource)` for backward compatibility (defaulting to existing location radius or 200m).
3. Ensure `KnownLocationsScreen.kt` passes `radius` from dialog `onConfirm` to `viewModel.updateLocation`.

### 3.3 Repository Layer (`KnownLocationsRepository.kt`)
1. Update `updateLocation`:
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
2. Retain 4-argument overload for backwards compatibility.
3. Thread confinement invariant: Remains strictly executed on `dbDispatcher` (`KnownLocationsDB-Thread`).

### 3.4 Persistence Layer (`KnownLocationsDatabaseManager.java`)
1. Extend `updateLocation`:
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
2. Retain existing 5-argument `updateLocation(long id, @NonNull String name, double altitude, @NonNull ElevationSource source, boolean isLocked)` overload (delegating or updating without radius column) so automatic callers (e.g. `healLegacyNames`) do not reset custom radii.

### 3.5 Unit Conversions & Formatting (`KnownLocationsUnitConversions.kt`)
* Provide formatting helper:
  ```kotlin
  fun formatRadius(meters: Int, isMetric: Boolean): String
  ```
  Returns `"$meters m"` in metric mode and `"$meters m (${metersToFeet(meters.toDouble())} ft)"` in imperial mode.

### 3.6 Localization & 9-Language Parity
Define localized string resources:
* `known_location_radius_label`:
  * EN: `"Radius"`
  * DE: `"Erfassungsradius"`
  * ES: `"Radio de cobertura"`
  * FR: `"Rayon de couverture"`
  * IT: `"Raggio di copertura"`
  * JA: `"検知半径"`
  * NL: `"Detectiestraal"`
  * PL: `"Promień wykrywania"`
  * PT: `"Raio de cobertura"`
* `known_location_radius_format`:
  * `"%1$s: %2$s"` (e.g. `Radius: 200 m` or `Erfassungsradius: 200 m`)
* Ensure 100% presence and format specifier compliance (`TranslationParityTest.kt`).

---

## 4. Invariants & Chesterton's Fence Analysis

1. **Schema V5 & Table Structure**:
   * The `KnownLocations` SQLite table already defines `radius int`. No database migration (`onUpgrade`) is needed; existing columns are preserved.
2. **Single-Thread SQLite Confinement (`INV-CON-01`)**:
   * All database read/write operations must remain strictly dispatched to `KnownLocationsDB-Thread` via `KnownLocationsRepository.dbDispatcher`.
3. **Automatic Lock Invariant (`REQ-UI-165`)**:
   * When manual edits to name, altitude, or radius are saved, `source` is set to `MANUAL_USER` and `is_locked` is set to `1` (true).
4. **Workout Start Counting Invariant (`REQ-DAT-015`)**:
   * Repeat workout starts within the custom radius continue to increment `hitCount` atomically without modifying custom radius or altitude.
5. **Map Performance & Viewport Bounds**:
   * Updating the radius in the dialog must only mutate the local composable state and the mini-map preview, keeping the main map reactive and 60 FPS smooth.

---

## 5. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-179` (*Customizable Geofence Radius & Live Map Preview in Lieblingsorte Edit Dialog*)
* **Verification**: `TST-UI-131` (*Geofence Radius Adjustment, Dynamic Preview & Persistence Verification*)
* **Epics**: `ATT-1396` (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)
* **Jira Tickets**: `ATT-1473` (Parent), `ATT-1493` (Stage 1 Analysis)

---

## 6. Verification Criteria & Test Strategy

1. **Unit Tests (`KnownLocationsRepositoryTest.kt`)**:
   * Verify `updateLocation` with explicit `radius` persists the radius to `mockDbManager`.
   * Verify updated radius is reflected in `loadLocations()` and `locationsFlow`.
2. **ViewModel Tests (`KnownLocationsViewModelTest.kt`)**:
   * Verify `viewModel.updateLocation(id, name, altitude, radius, source)` propagates all parameters to repository and dismisses the edit dialog.
3. **Dialog Tests (`EditKnownLocationDialogTest.kt`)**:
   * Verify radius slider interaction adjusts local radius state.
   * Verify confirming dialog passes the updated radius to `onConfirm`.
   * Verify formatting of radius in metric and imperial modes.
4. **Localization Parity (`TranslationParityTest.kt`)**:
   * Verify newly defined string resources exist across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing keys and valid format specifiers.
5. **Clean-Room Regression Suite**:
   * `./gradlew testDebugUnitTest` must pass with 100% success rate and zero regressions.
