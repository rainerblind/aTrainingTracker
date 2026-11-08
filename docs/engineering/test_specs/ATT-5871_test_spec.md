# Stage 2: Requirement & Test Specification - ATT-5871: Units Disambiguation & Power Iconography

**Ticket**: [ATT-5871](https://atrainingtracker.atlassian.net/browse/ATT-5871)  
**Parent Epic**: [ATT-5909](https://atrainingtracker.atlassian.net/browse/ATT-5909) (*Localization & Formatting Parity (i18n)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5871`  
**Requirement Mapping**: `REQ-UI-487` (*Navigation Drawer Units Disambiguation & Distinct Power Cockpit Iconography*)  
**Test Spec ID**: `TST-UI-487` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-487)

### 1.1 Problem Statement & Rationale
During UI walkthrough of the Navigation Drawer, Units dialog, and Activity Type selector, three defects were identified:
1. German Navigation Drawer ambiguity: Both `tab_workouts` (*Training -> Einheiten*) and `prefsUnitsTitle` (*Einstellungen -> Einheiten*) rendered `"Einheiten"`.
2. Untranslated unit string: In `UnitsSettingsDialog.kt`, German and Japanese retained English literal `"mile"` in `units_type_imperial` (`"Imperial (mile)"`).
3. Indistinguishable sport iconography: In `ActivityTypeSelectionDialog.kt` and `CopyTrackingTabDialog.kt`, `ActivityType.BIKE_SPEED_AND_CADENCE` and `ActivityType.BIKE_POWER` both bound `R.drawable.bsport_bike`.

### 1.2 Functional & Architectural Requirements
1. **Drawer & Dialog Label Disambiguation (`REQ-UI-487.1`)**: In `res/values-de/strings.xml`, `prefsUnitsTitle` SHALL resolve to `"Maßeinheiten"`, eliminating duplicate drawer label collision with `tab_workouts` (`"Einheiten"`). In `AppNavigationDrawer.kt` (under Settings -> `drawer_units`) and `UnitsSettingsDialog.kt` header, the displayed title SHALL bind `R.string.prefsUnitsTitle` (`"Maßeinheiten"` in DE, `"Units"` in EN).
2. **Units Dialog Imperial Localization Parity (`REQ-UI-487.2`)**: In `res/values-de/strings.xml`, `units_type_imperial` SHALL resolve to `"Imperial (Meilen)"`. In `res/values-ja/strings.xml`, `units_type_imperial` SHALL resolve to `"ヤードポンド法 (マイル)"`. In `UnitsSettingsDialog.kt`, selecting between `MyUnits.METRIC` and `MyUnits.IMPERIAL` SHALL render fully localized labels across all 9 supported locales without untranslated English unit fragments.
3. **Distinct Bike Power Iconography (`REQ-UI-487.3`)**: Vector asset `res/drawable/bsport_bike_power.xml` SHALL be authored matching the `192x192` viewport / `48dp` geometry of `bsport_bike.xml`, incorporating a distinct power lightning bolt badge in the top-left quadrant using canonical `ic_power` vector geometry. `ActivityType.BIKE_POWER` enum in `com.atrainingtracker.banalservice.ActivityType` SHALL bind `logoId = R.drawable.bsport_bike_power`. `ActivityTypeSelectionDialog.kt` and `CopyTrackingTabDialog.kt` SHALL render `bsport_bike_power` for `ActivityType.BIKE_POWER` and `bsport_bike` for `ActivityType.BIKE_SPEED_AND_CADENCE`, ensuring visual distinction.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (German Drawer Label Disambiguation)**:
  * *Given* the Navigation Drawer open in German locale (`de`),
  * *When* viewing drawer items under "Einstellungen",
  * *Then* the units item displays `"Maßeinheiten"` instead of `"Einheiten"`.
* **Criterion 2 (Units Dialog Imperial Localization)**:
  * *Given* `UnitsSettingsDialog` open in German locale (`de`),
  * *When* inspecting unit selection options,
  * *Then* the dialog title is `"Maßeinheiten"`, metric is `"Metrisch (m)"`, and imperial is `"Imperial (Meilen)"`.
* **Criterion 3 (Distinct Bike Power Iconography)**:
  * *Given* `ActivityTypeSelectionDialog` or `CopyTrackingTabDialog` open,
  * *When* inspecting the sport icon for `BIKE_POWER` ("Rad Leistung"),
  * *Then* it renders `R.drawable.bsport_bike_power` with a prominent power lightning bolt badge, visually distinct from `BIKE_SPEED_AND_CADENCE` ("Radeln").

### 1.4 System Invariants & Chesterton's Fence
* SQLite database schema (`TrackingViewsDbHelper`), preference keys (`SP_UNITS`), and drawer routes (`NavRoutes.fromDrawerItemId(R.id.drawer_units)`) remain 100% byte-for-byte identical.
* Enum contract `ActivityType.BIKE_POWER.name()` used in database queries remains strictly unaltered.
* 100% 9-language translation parity maintained; 100% clean-room test pass rate.

---

## 2. Test Specification (TST-UI-487)

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-487.1]` | Contract | `AppNavigationDrawerContractTest`: `values-de` `prefsUnitsTitle` resolves to `"Maßeinheiten"` and differs from `tab_workouts` | `REQ-UI-487.1` | Specified |
| `[TST-UI-487.2]` | Contract | `TranslationParityTest`: `values-de` `units_type_imperial` equals `"Imperial (Meilen)"` and `values-ja` equals `"ヤードポンド法 (マイル)"` | `REQ-UI-487.2` | Specified |
| `[TST-UI-487.3]` | Contract | `VectorAssetResolutionContractTest`: `ActivityType.BIKE_POWER.logoId` equals `R.drawable.bsport_bike_power` and parses valid vector with power badge | `REQ-UI-487.3` | Specified |
| `[TST-UI-487.4]` | Regression | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"` | `REQ-UI-487.1`..`3` | Specified |
