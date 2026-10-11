# Stage 2: Requirement & Test Specification: ATT-3107

## Metadata
* **Ticket ID**: ATT-3107
* **Title**: Display elevation profile with adequate vertical height when map is disabled on tracking screen
* **Parent Epic**: ATT-355 (Good and consistent UI)
* **Author**: Antigravity (Pair Programming Assistant)
* **Date**: 2026-10-10
* **Requirement ID**: `REQ-UI-339`
* **Test Specification ID**: `TST-UI-299`
* **Status**: Specified

---

## 1. Requirement Specification (REQ-UI-339)

### 1.1 Summary
The system SHALL dynamically allocate vertical layout weight and support flexible vertical canvas expansion for the elevation profile on the live tracking screen (`SensorGridScreen.kt`) when the embedded map is disabled (`showMap == false` and `showElevationProfile == true`) (ATT-3107, complementing `REQ-UI-197`, `REQ-UI-225`, and `REQ-UI-326`):

1. **Flexible Sensor Grid Weight Constraint (`SensorGridScreen.kt`)**:
   - The scrollable sensor grid `Column` SHALL declare `Modifier.weight(1f, fill = false)`.
   - When sensor tiles consume minimal vertical space (1–2 rows), the column SHALL wrap its content height naturally without forced stretching.
   - When sensor tiles consume extensive vertical space (4–6 rows), the column SHALL be constrained to available proportional height and scroll smoothly without crowding out bottom cockpit views.
2. **Dynamic Elevation Profile Weight Allocation (`SensorGridScreen.kt`)**:
   - When `showMap == false` and `showElevationProfile == true` with `state.pathPoints.size >= 2`, the elevation profile wrapping `Surface` SHALL declare `Modifier.weight(1f)`, expanding to utilize the generous vertical space vacated by the map.
   - When `showMap == true`, the elevation profile SHALL remain unweighted with its standard top padding, allowing the map to retain primary flexible weight (`weight(1f)`).
   - When `state.pathPoints.size < 2`, the elevation profile SHALL NOT be rendered, preventing empty stretched canvas artifacts.
3. **Decoupled Canvas Height Expansion (`ElevationProfile.kt`)**:
   - `ElevationProfile` SHALL accept an explicit parameter `fillParentHeight: Boolean = false`.
   - When `fillParentHeight == false` (default across all list previews, summaries, and map sheets), the canvas SHALL maintain its fixed `totalCanvasHeight` (`height(totalCanvasHeight)`).
   - When `fillParentHeight == true`, the canvas SHALL declare `defaultMinSize(minHeight = totalCanvasHeight).fillMaxHeight()`, dynamically expanding to fill the full available height of the weighted container ($\ge 120\text{dp}$).
4. **Preservation of System Invariants**:
   - Ambient `NavigationHintsOverlay` positioning at `Alignment.TopCenter` when `showMap == false` (REQ-UI-335 / ATT-3100), map recomposition barrier isolation (REQ-UI-326), and 100% test pass rate MUST be strictly preserved.

### 1.2 Chesterton's Fence Audit
* **Historical Precedent**: Sprint 2026-41.6 (`ATT-2944`). `TrackingMapContainer` was isolated with `weight(1f)`, but when `showMap == false`, no weight was assigned to `ElevationProfile`, causing the unconstrained sensor grid to push it into a 15–20dp clipped slit.
* **Root Reason for Existing Formulation**: Historical tracking layout assumed the map was always visible when tracking. When map toggling was added, the elevation profile was not given flexible weight.
* **Preservation of Intent**: Sensor grid scrollability, unconstrained list preview height, and map + elevation profile coexistence are fully preserved.

---

## 2. Test Specification (TST-UI-299)

### 2.1 Test Categories & Verification Scope

| Test ID | Category | Target File | Verification Scope |
| :--- | :--- | :--- | :--- |
| **TST-UI-299.1** | Sensor Grid Weight Constraint | `SensorGridScreenLayoutTest.kt` | Verify that the sensor grid `Column` specifies `Modifier.weight(1f, fill = false)`. |
| **TST-UI-299.2** | Dynamic Elevation Profile Weight | `SensorGridScreenLayoutTest.kt` | Verify that the elevation profile container applies `Modifier.weight(1f)` when `!state.mapState.showMap` and `state.showElevationProfile == true`. |
| **TST-UI-299.3** | ElevationProfile Wiring Parameters | `SensorGridScreenLayoutTest.kt` | Verify that `ElevationProfile` receives `fillParentHeight = !state.mapState.showMap` and `modifier = if (!state.mapState.showMap) Modifier.fillMaxSize() else Modifier.fillMaxWidth()`. |
| **TST-UI-299.4** | Sparse Path Guard Safety | `SensorGridScreenLayoutTest.kt` | Verify that rendering is guarded by `state.pathPoints.size >= 2`. |
| **TST-UI-299.5** | Decoupled Canvas Height Modifier | `ElevationProfileLayoutTest.kt` | Verify that `ElevationProfile` defines `fillParentHeight: Boolean = false`, and conditionally attaches `.height(totalCanvasHeight)` vs `.defaultMinSize(minHeight = totalCanvasHeight).fillMaxHeight()`. |
| **TST-UI-299.6** | List Previews Non-Regression | `ElevationProfileLayoutTest.kt` | Verify that list preview callers do not pass `fillParentHeight = true` and retain their natural wrapped height. |
| **TST-UI-299.7** | Clean-Room Full Suite Regression | Full Unit Test Suite | Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with zero regressions. |

---

## 3. Acceptance Criteria (Given-When-Then)

### Scenario 1: Elevation Profile Height without Map
* **Given** the tracking screen in `ScreenMode.TRACKING`,
* **When** the map is disabled (`showMap == false`) and elevation profile is enabled (`showElevationProfile == true`) with at least 2 track points,
* **Then** the elevation profile expands to fill the space vacated by the map ($\ge 120\text{dp}$), rendering the full curve and gradient shading without clipping.

### Scenario 2: Sensor Grid Scrollability with Many Rows
* **Given** an athlete with 6 rows of sensor fields with map disabled and elevation profile enabled,
* **When** the screen renders,
* **Then** the sensor grid scrolls smoothly and does NOT squeeze the elevation profile off the bottom of the screen.

### Scenario 3: Map + Elevation Profile Coexistence
* **Given** both map and elevation profile enabled,
* **When** the screen renders,
* **Then** the map takes the primary weighted space and the elevation profile renders below it at its standard adaptive height.

---

## 4. Invariant Protection

1. **Height Integrity**: The elevation profile is guaranteed $\ge 120\text{dp}$ vertical height when the map is disabled.
2. **List Preview Safety**: All list previews continue using wrapped height without unbounded expansion.
3. **Clean-Room Test Pass Rate**: Full test suite must maintain 100% pass rate.
