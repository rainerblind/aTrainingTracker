# Stage 3: Implementation Plan - ATT-5871: Units Disambiguation & Power Iconography

**Ticket**: [ATT-5871](https://atrainingtracker.atlassian.net/browse/ATT-5871) | **Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-5909](https://atrainingtracker.atlassian.net/browse/ATT-5909) (*Localization & Formatting Parity (i18n)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5871`  
**Requirement Mapping**: `REQ-UI-487` (`REQ-UI-487.1`..`3`) | **Test Mapping**: `TST-UI-487` (`TST-UI-487.1`..`4`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Description & Background
Navigation Drawer and modal settings screens present three terminology and iconography collisions: (1) both `tab_workouts` and `prefsUnitsTitle` render `"Einheiten"` in German; (2) `units_type_imperial` in `UnitsSettingsDialog.kt` retained English `"mile"` in `values-de` and `values-ja`; and (3) `ActivityType.BIKE_POWER` and `ActivityType.BIKE_SPEED_AND_CADENCE` both bind `R.drawable.bsport_bike`, rendering them visually indistinguishable in `ActivityTypeSelectionDialog.kt` and `CopyTrackingTabDialog.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-487` (*Navigation Drawer Units Disambiguation & Distinct Power Cockpit Iconography*)
  - `REQ-UI-487.1`: Disambiguate German drawer label and dialog title to `"Maßeinheiten"`.
  - `REQ-UI-487.2`: Localize imperial units option to `"Imperial (Meilen)"` (`de`) and `"ヤードポンド法 (マイル)"` (`ja`).
  - `REQ-UI-487.3`: Author `bsport_bike_power.xml` vector drawable and map `ActivityType.BIKE_POWER.logoId`.
* **Test Mapping**: `TST-UI-487` (`TST-UI-487.1`..`4` in `docs/tests/tst_ui_design_system.md`).

---

## 3. System Invariants & Preserved Behavior
1. **Database Schema & Key Invariants**: `TrackingViewsDbHelper` table schemas and SQLite queries using `ActivityType.BIKE_POWER.name()` remain 100% byte-for-byte identical.
2. **Preference Contract Stability**: `TrainingApplication.SP_UNITS` preference key and `MyUnits.name` (`METRIC` / `IMPERIAL`) persistence remain untouched.
3. **Drawer Navigation Integrity**: `NavRoutes.fromDrawerItemId(R.id.drawer_units)` routing and dialog invocation contracts are fully preserved.
4. **Sport Hierarchy Preservation**: `ActivityType.BIKE_POWER.sportType` remains `BSportType.BIKE` for sensor grouping, zones, and calibration.

---

## 4. Proposed Architectural Changes
* `res/drawable/bsport_bike_power.xml`: Analytical vector asset (`192x192` viewport, `48dp`) combining the multi-color cyclist from `bsport_bike.xml` with canonical `ic_power` lightning bolt in the top-left quadrant (`#FFA000` / `#FFC107` bolt fill with `#000000` stroke).
* `com.atrainingtracker.banalservice.ActivityType`: Point `BIKE_POWER` constructor parameter `logoId` to `R.drawable.bsport_bike_power`.
* **UI Consistency (Rule 23 / Section 5)**: `ActivityTypeSelectionDialog.kt` and `CopyTrackingTabDialog.kt` render `type.logoId` via `safePainterResource` with `tint = Color.Unspecified`, preserving multi-color vector paths on light/dark surfaces.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `res/drawable/bsport_bike_power.xml`
* File: `app/src/main/res/drawable/bsport_bike_power.xml`
* Action: Author vector drawable containing `bsport_bike` cyclist geometry and top-left power lightning bolt badge (`REQ-UI-487.3`).

### Step 2: Wire `ActivityType.BIKE_POWER` to `bsport_bike_power`
* File: `app/src/main/java/com/atrainingtracker/banalservice/ActivityType.java`
* Action: Update `BIKE_POWER` enum constant to pass `R.drawable.bsport_bike_power` as `logoId` (`REQ-UI-487.3`).

### Step 3: Implement `AppNavigationDrawerContractTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerContractTest.kt`
* Action: Assert `prefsUnitsTitle` resolves to `"Maßeinheiten"` in `values-de`, differs from `tab_workouts` (`"Einheiten"`), and tests Japanese imperial localization parity (`TST-UI-487.1`, `TST-UI-487.2`).

### Step 4: Update `VectorAssetResolutionContractTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
* Action: Add `"bsport_bike_power"` to vector asset tests, verify path parsing, and assert `ActivityType.BIKE_POWER.logoId == R.drawable.bsport_bike_power` (`TST-UI-487.3`).

### Step 5: Execute Test Verification Suite
* Commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.AppNavigationDrawerContractTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.resources.VectorAssetResolutionContractTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
  - `python3 tools/verify_requirement_governance.py`
