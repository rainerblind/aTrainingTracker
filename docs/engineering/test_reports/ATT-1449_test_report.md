# Test Report - ATT-1449: Show Lieblingsorte on General Map

**Ticket**: [ATT-1449](https://rainerblind.atlassian.net/browse/ATT-1449)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Sub-task**: [ATT-1504](https://rainerblind.atlassian.net/browse/ATT-1504) (Stage 5 Test)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement Mapping**: `REQ-UI-180` (*Display Known Locations (Lieblingsorte) on Central Map with Bottom Peek Card & List Navigation*)  
**Test Spec Mapping**: `TST-UI-132`  
**Target Device**: Google Pixel 10 (`66020DLCR002FL`, Android 16 / API 36, Light Mode)  
**Branch**: `feature/ATT-1449`  
**Date**: 2026-09-28  

---

## 1. Executive Summary

This test report documents the verification and validation of ticket **ATT-1449**, which promotes athlete favorite start locations (*Lieblingsorte*) to first-class visual citizens on the application's central map (`MapScreenWithTrack`), backed by an interactive bottom peek card, deep-link navigation from the favorite locations list, and live geofence radius preview during editing. Furthermore, the redundant secondary GoogleMap tab and view pager within `KnownLocationsScreen` were successfully decommissioned into a streamlined, high-performance single-perspective list.

All 8 specified test cases in `TST-UI-132` passed with 100% success. The clean-room unit test suite (`./gradlew testDebugUnitTest`) passed with zero regressions across 825+ unit tests. On-device verification on the physical Google Pixel 10 confirmed fluid heart-pin rendering, circular geofence overlays, bottom sheet peek card interaction, live radius preview manipulation on the main map, and seamless navigation from the Lieblingsorte screen.

---

## 2. Test Execution Matrix (`TST-UI-132`)

| Test ID | Test Scope | Verification Method | Associated Requirement | Result |
|:---|:---|:---|:---|:---:|
| **TST-UI-132.1** | State Flow Combination | `MapFragmentWithTrackViewModelTest.testUiState_combinesKnownLocationsFlow` | `REQ-UI-180` | **PASSED** |
| **TST-UI-132.2** | Repository Delegation | `MapFragmentWithTrackViewModelTest.testUpdateKnownLocation_delegatesToRepository` | `REQ-UI-180` | **PASSED** |
| **TST-UI-132.3** | Parameterized Route & Drawer | `NavRoutesClusterTest.testMapRoute_parameterizedAndDrawerMapping` | `REQ-UI-180` | **PASSED** |
| **TST-UI-132.4** | Streamlined ViewModel | `KnownLocationsViewModelTest` (single-perspective, no dead map state) | `REQ-UI-180` | **PASSED** |
| **TST-UI-132.5** | Map Marker & Circle Overlay | `MapContentScope.knownLocations` rendering verification | `REQ-UI-180` | **PASSED** |
| **TST-UI-132.6** | Peek Card List-Identical Styling | `MapScreenWithTrack.KnownLocationOnMapSheet` verification | `REQ-UI-180` | **PASSED** |
| **TST-UI-132.7** | Live Geofence Radius Preview | `EditKnownLocationDialogTest.testEditDialog_radiusSlider_invokesOnRadiusChangeLive` | `REQ-UI-180`, `REQ-UI-179` | **PASSED** |
| **TST-UI-132.8** | Full Clean-Room Regression | Clean-room execution of `./gradlew testDebugUnitTest` | `REQ-PRO-001` | **PASSED** |

---

## 3. Test Details & Results

### 3.1 ViewModel & State Aggregation (`TST-UI-132.1`, `TST-UI-132.2`, `TST-UI-132.4`)
- `MapFragmentWithTrackViewModel` aggregates `knownLocationsRepository.locationsFlow` into `uiState: StateFlow<MapFragmentUIState>` with zero race conditions.
- `updateKnownLocation` cleanly delegates parameter updates (id, name, altitude, radius, source) to `KnownLocationsRepository.updateLocation(...)`.
- `KnownLocationsViewModel` decommissioned dead camera, bounds calculation, and tab selection state, retaining purely list-oriented search, sorting, and dialog management.

### 3.2 Navigation & Route Parameterization (`TST-UI-132.3`)
- `NavRoutes.map(locationId: Long?)` constructs valid URI paths:
  - `NavRoutes.map(null)` -> `"map"`
  - `NavRoutes.map(42L)` -> `"map?locationId=42"`
- Parameterized route matching in `NavRoutes.isDrawerDestination("map?locationId=42")` correctly maps to `R.id.drawer_map`.
- `ATrainingTrackerApp.kt` routes `MAP_PATTERN` extracting `ARG_LOCATION_ID`, centering camera window and pre-selecting target location.

### 3.3 Visual Presentation & Bottom Peek Card (`TST-UI-132.5`, `TST-UI-132.6`)
- `MapContentScope.knownLocations`:
  - Heart-pin markers rendered with `createHeartPinMarker(context, primaryColor, Color.White)`.
  - Geofence circular overlays rendered with `radius = loc.radius.toDouble()`, fill alpha 0.15, stroke alpha 0.5, stroke width 2f.
- `KnownLocationOnMapSheet`:
  - Formatted identically to `KnownLocationItemCard` in `KnownLocationsScreen`.
  - Row 1: Location name (`titleMedium`, Bold) with `Icons.Default.Edit` `IconButton` (24dp) replacing the overflow button.
  - Row 2: Prominent reference altitude metric (`R.drawable.ic_ascent`, primary color) and start counts (`R.plurals.known_locations_starts`).
  - Standard `MinimumDragHandle()` at top with `sheetPeekHeight = 100.dp + navBarHeight`.
  - Tapping empty map area or pressing system Back cleanly dismisses the peek card.

### 3.4 Live Geofence Radius Preview on Main Map (`TST-UI-132.7`)
- `EditKnownLocationDialog` exposes `onRadiusChange: ((Int) -> Unit)?`.
- As the user moves the radius slider (50m to 1,000m, step=25m), `previewRadius` reactively updates `displayLocations` on the central map behind the bottom sheet.
- Canceling or dismissing the dialog reverts `previewRadius` to null (restoring original circle). Confirming persists the new radius and dismisses the dialog.

### 3.5 Clean-Room Regression Suite (`TST-UI-132.8`)
- **Command**: `./gradlew testDebugUnitTest`
- **Result**: `BUILD SUCCESSFUL in 3m 45s`
- **Pass Rate**: 100% across all 825+ tests (0 failures, 0 errors).

---

## 4. Physical Device Verification (Google Pixel 10)

- **Device**: Google Pixel 10 (`66020DLCR002FL`, Android 16 / API 36)
- **Display Theme**: Light Mode (`Night mode: no`)
- **Verification Steps & Observations**:
  1. Built and installed debug APK on device via `./gradlew installDebug`.
  2. Opened Central Map (`drawer_map`):
     - Favorite locations (*Lieblingsorte*) displayed clearly with blue heart-pin markers and circular geofence overlays.
  3. Tapped "Work" heart-pin marker:
     - Bottom peek card smoothly expanded with `MinimumDragHandle()`.
     - Readout displayed "Work", Edit pen icon, "↑ 441 m", and "15 Starts" in blue primary color, identical to list entry.
  4. Tapped Edit pen icon:
     - `EditKnownLocationDialog` opened as a modal bottom sheet without redundant mini-map (`showMap = false`).
  5. Dragged Radius Slider:
     - The circular geofence overlay on the central map behind the dialog dynamically expanded and contracted in real time matching the slider value.
  6. Tapped "Abbrechen":
     - Circle immediately snapped back to original 500m geofence radius.
  7. Re-opened dialog, changed radius to 400m, and tapped "Speichern":
     - Dialog closed, and the updated 400m circle was persisted and displayed on the main map.
  8. Opened Drawer -> **Lieblingsorte** (`drawer_start_locations`):
     - Single-perspective list loaded instantly with no lag or tab flickering.
     - Tapped overflow menu -> "Auf Karte anzeigen":
     - Navigated directly to central map, centered camera on the location, and opened its bottom peek card.
  9. Verified device remained strictly in Light Mode (`Night mode: no`).

---

## 5. ASPICE Traceability & Sign-Off

- **Requirements**: `REQ-UI-180` verified.
- **Test Specifications**: `TST-UI-132` verified (all 8 cases passed).
- **Sub-tasks Completed**:
  - `ATT-1498`: Stage 1 (Analysis) - `Erledigt`
  - `ATT-1499`: Stage 2 (Test-Spec) - `Erledigt`
  - `ATT-1500`: Stage 3 (Impl-Plan) - `Erledigt`
  - `ATT-1501`: Stage 4 (Implementation) - `Freigabe (Human)`
  - `ATT-1504`: Stage 5 (Test & Verification) - `Freigabe (Human)`
