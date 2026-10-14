# Stage 2: Requirement & Test Specification - ATT-3311: [Bug] Restore visibility of sole elevation profile on tracking screen without map

**Ticket**: [ATT-3311](https://atrainingtracker.atlassian.net/browse/ATT-3311)  
**Sub-task**: [ATT-3591](https://atrainingtracker.atlassian.net/browse/ATT-3591) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `v1.0.0-rc1`  
**Active Sprint**: `2026-42.4`  
**Requirement Mapping**: `REQ-UI-369` (*Cockpit Layout Inset Padding & Elevation Profile Bottom Sheet Clearance*)  
**Test Spec ID**: `TST-UI-332`  
**Branch**: `bugfix/ATT-3311`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-UI-369)

### 1.1 Problem Statement & Rationale
During Sprint Review `2026-41.10` on Google Pixel 10 (`ATT-3173`), the reviewer reported that the sole elevation profile on tracking tabs without a map was no longer shown at all. Forensic investigation revealed that the tracking cockpit layout (`BoxWithConstraints` in `SensorGridScreen.kt`) intentionally discarded `paddingValues.calculateBottomPadding()` to allow Google Maps to render underneath the bottom sheet. Consequently, when `ATT-3173` removed `Modifier.weight(1f)` and `fillParentHeight` from the elevation profile to enforce canonical adaptive height ($70\text{dp} \dots 200\text{dp}$), the unweighted elevation profile dropped into the bottom $\approx 70\text{dp} \dots 106\text{dp}$ of the viewport, where the $\approx 104\text{dp}$ Bottom Sheet Peek Bar directly overlay and fully occluded the elevation path, baseline, and labels.

`REQ-UI-369` amends `REQ-UI-346` and aligns with `REQ-UI-358` by enforcing dynamic bottom inset padding so the elevation profile is anchored strictly above the bottom sheet peek bar in all cockpit modes.

### 1.2 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-346` (*Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen*, Sprint 2026-41.9, ATT-3173) and aligns with `REQ-UI-358` (*Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment*, Sprint 2026-41.11, ATT-3343). Targets `SensorGridScreen.kt` and `SensorGridScreenLayoutTest.kt`.
* **Historical Origin & Commit Trace**: 
  - Sprint 2026-40.8 (`3079c4b9`): Initial introduction of `ElevationProfile` on tracking screen.
  - Sprint 2026-41.8 (`ATT-3107`, commit `71ea8399` / `REQ-UI-339`): Introduced `Modifier.weight(1f)` on `ElevationProfile` when map is disabled.
  - Sprint 2026-41.9 (`ATT-3173`, commit `935a2328` / `REQ-UI-346`): Removed artificial `Modifier.weight(1f)` from `ElevationProfile` to enforce canonical adaptive height ($70\text{dp} \dots 200\text{dp}$).
  - Sprint 2026-41.11 (`ATT-3343`, commit `fca58372` / `REQ-UI-358`): Introduced `BoxWithConstraints` and asymmetric map weight allocation, but retained `.padding(top = paddingValues.calculateTopPadding()) // Only pad the top`.
* **Root Reason for Existing Formulation (The "Fence")**: 
  The top-level `BoxWithConstraints` deliberately omitted scaffold bottom padding (`paddingValues.calculateBottomPadding()`) so that a full-screen Google Map could extend edge-to-edge underneath a translucent or peeked bottom sheet. However, when the elevation profile sits at the bottom of the stationary `Column` (either on tabs without map, or beneath the map on map tabs), it is a bounded chart ($70\text{dp} \dots 200\text{dp}$), not a continuous background map. Omitting bottom padding causes the $104\text{dp}$ sheet peek bar to occlude the entire chart baseline, path, and X-axis labels.
* **Preservation of Core Invariants**: 
  By applying `padding(bottom = paddingValues.calculateBottomPadding())` selectively to the non-map cockpit `Column` and to the elevation profile `Surface` when below the map, the map still expands flexibly per `REQ-UI-358`, canonical adaptive height is preserved per `REQ-UI-346`, and zero occlusion occurs.

### 1.3 Functional & Architectural Requirements

1. **Non-Map Tracking Cockpit Bottom Inset Application (`SensorGridScreen.kt`)**:
   * The system SHALL apply `bottom = paddingValues.calculateBottomPadding()` to the stationary cockpit container `Column` when `!state.mapState.showMap`.
   * Because no embedded map exists on these tabs, applying the scaffold's bottom padding ensures that multi-row sensor grids and the elevation profile are pushed up and anchored cleanly above the bottom sheet peek bar.

2. **Map-Enabled Elevation Profile Inset Application (`SensorGridScreen.kt`)**:
   * When `state.mapState.showMap == true` and `state.showElevationProfile == true` (with $\ge 2$ path points), the system SHALL apply `bottom = paddingValues.calculateBottomPadding()` to the elevation profile `Surface`.
   * This anchors the elevation profile strictly above the bottom sheet peek bar while preserving the ability of the map `Box` (with `.weight(1f, fill = true)`) to expand flexibly and consume all vertical space remaining between the sensor grid and the elevation profile per `REQ-UI-358`.

3. **Zero Dead Space on Peek Height Inactivity**:
   * When `sheetPeekHeight == 0.dp` (e.g. preview mode or tabs without active live segments/climbs), `paddingValues.calculateBottomPadding()` evaluates to `0.dp`, introducing zero unnecessary bottom whitespace.

4. **Preservation of System Invariants**:
   * Canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) via `calculateElevationProfileHeight(range)` without artificial `Modifier.weight(1f)` or `fillParentHeight = true` (`REQ-UI-346`).
   * Sensor grid internal vertical scrolling with `.weight(1f, fill = false)` when map is hidden and `.heightIn(max = availableHeight * 0.5f)` when map is visible (`REQ-UI-358`).
   * Recomposition barrier isolation in `TrackingMapContainer` (`REQ-UI-326`).
   * 100% unit test pass rate across the test suite with zero regressions.

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Map-less Tracking Tab with Bottom Sheet Active)**:
  * *Given* a tracking tab configured with `showMap == false` and `showElevationProfile == true` on a device with active live climbs/segments (`sheetPeekHeight > 0.dp`),
  * *When* viewing the tracking screen during active tracking or route preview,
  * *Then* the elevation profile renders at its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) strictly above the bottom sheet peek bar, with zero occlusion of its baseline, elevation path, or axis labels.

* **Criterion 2 (Map Tab with Elevation Profile & Bottom Sheet Active)**:
  * *Given* a tracking tab configured with `showMap == true` and `showElevationProfile == true` on a device with active live climbs/segments,
  * *When* viewing the tracking screen,
  * *Then* the elevation profile sits directly above the bottom sheet peek bar, and the map expands flexibly to fill 100% of all vertical space between the sensor grid and the elevation profile.

* **Criterion 3 (Bottom Sheet Inactive / Zero Peek Height)**:
  * *Given* a tracking tab with `showElevationProfile == true` and `sheetPeekHeight == 0.dp`,
  * *When* viewing the tracking screen,
  * *Then* the elevation profile anchors directly at the bottom screen boundary with zero redundant whitespace and zero layout shift.

* **Criterion 4 (Regression Prevention)**:
  * *Given* all existing unit tests in `SensorGridScreenLayoutTest` and `SensorGridScreenElevationProfileTest`,
  * *When* running `./gradlew testDebugUnitTest`,
  * *Then* 100% of tests pass without regressions.

### 1.5 System Invariants
* Zero regression in existing unit test pass rates.
* Parent ticket Human Decision Gate remains strictly enforced.

---

## 2. Test Specification (TST-UI-332)

### Test Case 1: `testLayoutStructure_nonMapCockpitAppliesBottomPadding` (`TST-UI-332.1`)
* **Scope**: Unit / Layout Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Preconditions**: `SensorGridScreen.kt` source loaded.
* **Action**: Inspect the stationary cockpit `Column` modifier chain when `!state.mapState.showMap`.
* **Expected Result**: Assert that the modifier applied to the cockpit Column contains `.padding(bottom = paddingValues.calculateBottomPadding())` (or equivalent conditional modifier branch verifying `if (!state.mapState.showMap) Modifier.padding(bottom = paddingValues.calculateBottomPadding()) else Modifier`).

### Test Case 2: `testLayoutStructure_elevationProfileSurfaceAppliesBottomPadding` (`TST-UI-332.2`)
* **Scope**: Unit / Layout Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Preconditions**: `SensorGridScreen.kt` source loaded.
* **Action**: Inspect the elevation profile `Surface` modifier declaration in `SensorGridScreen.kt`.
* **Expected Result**: Assert that the `Surface` modifier includes `bottom = paddingValues.calculateBottomPadding()`, ensuring bottom sheet clearance when rendered below the map.

### Test Case 3: `testLayoutGeometry_elevationProfileClearsBottomSheet` (`TST-UI-332.3`)
* **Scope**: Unit / Geometry Simulation Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Preconditions**: Cockpit layout geometry simulation: `availableHeight = 800f`, `sheetPeekHeight = 104f`, `elevationProfileHeight = 70f`, `topPadding = 8f`.
* **Action**: 
  1. For map-less cockpit (`showMap == false`): Compute `effectiveColumnHeight = availableHeight - sheetPeekHeight = 696f`. With sensor grid intrinsic height `300f`, assert profile is positioned at `y = 308f` and ends at `y = 378f`, well clear of sheet top at `y = 696f`.
  2. For map-enabled cockpit (`showMap == true`): Compute remaining space for map `availableHeight - sensorGridHeight - (profileHeight + topPadding + sheetPeekHeight) = 800 - 300 - (70 + 8 + 104) = 318f`. Assert profile starts at `y = 800 - 104 - 70 = 626f` and ends at `y = 696f`, exactly touching sheet top `696f` with $0\text{dp}$ overlap and zero dead space.
* **Expected Result**: Assert $y_{\text{profile\_bottom}} \le y_{\text{sheet\_top}}$ in both cases ($0\text{px}$ overlap).

### Test Case 4: `testLayoutGeometry_zeroSheetPeekHeightBoundaryVerification` (`TST-UI-332.4`)
* **Scope**: Unit / Boundary Invariant Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Preconditions**: Cockpit layout geometry simulation: `availableHeight = 800f`, `sheetPeekHeight = 0f`, `elevationProfileHeight = 70f`.
* **Action**: 
  1. Evaluate `paddingValues.calculateBottomPadding() == 0f`.
  2. Compute total consumed vertical space with map: `sensorGridHeight (300f) + mapHeight (422f) + profileHeight (70f) + topPadding (8f) = 800f`.
  3. Assert `deadSpace == 0f`, and verify that profile bottom anchors at exactly `y = 800f` without negative insets or viewport clipping.
* **Expected Result**: Assert `deadSpace == 0.0f` and `profileBottom == availableHeight`.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-332.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-332.1` | Unit | `SensorGridScreenLayoutTest.testLayoutStructure_nonMapCockpitAppliesBottomPadding` | `REQ-UI-369.1` | Specified |
| `TST-UI-332.2` | Unit | `SensorGridScreenLayoutTest.testLayoutStructure_elevationProfileSurfaceAppliesBottomPadding` | `REQ-UI-369.2` | Specified |
| `TST-UI-332.3` | Unit | `SensorGridScreenLayoutTest.testLayoutGeometry_elevationProfileClearsBottomSheet` | `REQ-UI-369.1`, `REQ-UI-369.2` | Specified |
| `TST-UI-332.4` | Unit | `SensorGridScreenLayoutTest.testLayoutGeometry_zeroSheetPeekHeightBoundaryVerification` | `REQ-UI-369.3` | Specified |
| `TST-UI-332.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
