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

### 1.2 Functional & Architectural Requirements

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

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Map-less Tracking Tab with Bottom Sheet Active)**:
  * *Given* a tracking tab configured with `showMap == false` and `showElevationProfile == true` on a device with active live climbs/segments (`sheetPeekHeight > 0.dp`),
  * *When* viewing the tracking screen during active tracking or route preview,
  * *Then* the elevation profile renders at its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) strictly above the bottom sheet peek bar, with zero occlusion of its baseline, elevation path, or axis labels.

* **Criterion 2 (Map Tab with Elevation Profile & Bottom Sheet Active)**:
  * *Given* a tracking tab configured with `showMap == true` and `showElevationProfile == true` on a device with active live climbs/segments,
  * *When* viewing the tracking screen,
  * *Then* the elevation profile sits directly above the bottom sheet peek bar, and the map expands flexibly to fill 100% of all vertical space between the sensor grid and the elevation profile.

* **Criterion 3 (Bottom Sheet Inactive)**:
  * *Given* a tracking tab with `showElevationProfile == true` and `sheetPeekHeight == 0.dp`,
  * *When* viewing the tracking screen,
  * *Then* the elevation profile anchors directly at the bottom screen boundary with zero redundant whitespace.

* **Criterion 4 (Regression Prevention)**:
  * *Given* all existing unit tests in `SensorGridScreenLayoutTest` and `SensorGridScreenElevationProfileTest`,
  * *When* running `./gradlew testDebugUnitTest`,
  * *Then* 100% of tests pass without regressions.

### 1.4 System Invariants
* Zero regression in existing unit test pass rates.
* Parent ticket Human Decision Gate remains strictly enforced.

---

## 2. Test Specification (TST-UI-332)

### Test Case 1: `testLayoutStructure_nonMapCockpitAppliesBottomPadding` (`TST-UI-332.1`)
* **Scope**: Unit / Layout Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Preconditions**: `SensorGridScreen.kt` source loaded.
* **Action**: Verify that when `!state.mapState.showMap`, the stationary cockpit Column or container modifier applies `bottom = paddingValues.calculateBottomPadding()`.
* **Expected Result**: Assert true that non-map cockpit container respects scaffold bottom padding.

### Test Case 2: `testLayoutStructure_elevationProfileSurfaceAppliesBottomPadding` (`TST-UI-332.2`)
* **Scope**: Unit / Layout Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Preconditions**: `SensorGridScreen.kt` source loaded.
* **Action**: Verify that the elevation profile `Surface` applies bottom padding derived from `paddingValues.calculateBottomPadding()`.
* **Expected Result**: Assert true that `Surface` modifier declares `bottom = paddingValues.calculateBottomPadding()`.

### Test Case 3: `testLayoutGeometry_elevationProfileClearsBottomSheet` (`TST-UI-332.3`)
* **Scope**: Unit / Geometry Simulation Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Preconditions**: Mathematical simulation with `availableHeight = 800dp`, `sheetPeekHeight = 104dp`, `elevationProfileHeight = 70dp`.
* **Action**: Compute top and bottom bounds of elevation profile and sheet peek bar under map-less and map-enabled configurations.
* **Expected Result**: Verify that $y_{\text{profile\_bottom}} \le y_{\text{sheet\_top}}$ in both cases with zero occlusion.

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-UI-332.4`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence and matching `%s`/`%d` tokens across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, 0 new strings, zero format specifier mismatches.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-332.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-332.1` | Unit | `SensorGridScreenLayoutTest.testLayoutStructure_nonMapCockpitAppliesBottomPadding` | `REQ-UI-369.1` | Specified |
| `TST-UI-332.2` | Unit | `SensorGridScreenLayoutTest.testLayoutStructure_elevationProfileSurfaceAppliesBottomPadding` | `REQ-UI-369.2` | Specified |
| `TST-UI-332.3` | Unit | `SensorGridScreenLayoutTest.testLayoutGeometry_elevationProfileClearsBottomSheet` | `REQ-UI-369.1`, `REQ-UI-369.2`, `REQ-UI-369.3` | Specified |
| `TST-UI-332.4` | Localization | `TranslationParityTest` | `REQ-UI-369`, `REQ-UI-106` | Specified |
| `TST-UI-332.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
