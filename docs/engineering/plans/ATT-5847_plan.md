# Stage 3: Implementation Plan - ATT-5847: Equipment & Sensors UI: Deduplicate identical Strava gear names, localize ISO dates & frame types ('Road'), fix disconnected green dot & capitalize 'samsung'/'google'

**Ticket**: [ATT-5847](https://rainerblind.atlassian.net/browse/ATT-5847)  
**Sub-task**: [ATT-6030](https://rainerblind.atlassian.net/browse/ATT-6030) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-492` (`REQ-UI-492.1`–`REQ-UI-492.4`)  
**Test Mapping**: `TST-UI-492` (`TST-UI-492.1`–`TST-UI-492.6`)  
**Branch**: `feature/ATT-5847`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
Live testing of `Räder & Schuhe` and `Sensoren` surfaced UI redundancy and localization defects: identical Strava gear names repeating in subtitle rows, unlocalized raw ISO usage dates (`YYYY-MM-DD`), hardcoded English bike frame types ("Road", "Time Trial"), German typo `Rad Typ` (`Radtyp`), and sensor dialog defects (green LED dot when disconnected, lowercase manufacturers "samsung"/"google", and unstyled bare `TextButton` for equipment linking).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-492` (*Equipment & Sensors UI Polish*)
  * `REQ-UI-492.1`: Inline Strava logo badge when gear name equals equipment name; suppress duplicate subtitle.
  * `REQ-UI-492.2`: Format usage dates via `DateTimeUtils.formatIsoDate` and localize bike frame types across 9 locales.
  * `REQ-UI-492.3`: Correct `Radtyp` in DE and harmonize empty placeholder to "Keine" / "None".
  * `REQ-UI-492.4`: Synchronize sensor status LED with `DeviceStatusRow`, capitalize manufacturers, style equipment selector.
* **Test Mapping**: `TST-UI-492` (*Equipment & Sensors UI Polish Verification Suite*)
  * `TST-UI-492.1`–`TST-UI-492.4`: Unit & AST contract tests in `EquipmentSensorsUiPolishContractTest.kt`.
  * `TST-UI-492.5`: 9-locale parity verification in `TranslationParityTest.kt`.
  * `TST-UI-492.6`: Tier 1 targeted regression suite (`ui.equipment.*`, `devices.*`, `TranslationParityTest`, `ThemeColorCentralizationAuditTest`).

---

## 3. System Invariants & Preserved Behavior
1. **Schema & Connectivity Invariants**: SQLite schemas (`Equipment.db`, `Devices.db`) and sensor connection state machines remain 100% unchanged.
2. **Localization Parity (`REQ-LOC-001`)**: 100% parity across all 9 canonical languages with zero missing keys.
3. **Card Gestures (`REQ-UI-061`)**: Swipe and long-press card actions remain intact.

---

## 4. Proposed Architectural Changes & UI Consistency
* **`EquipmentTabsScreen.kt` & `BikeTypeSelector.kt`**:
  * Conditionally render inline `StravaOriginalLogo(14.dp)` in title row when `stravaName.equals(name, ignoreCase = false)`.
  * Resolve frame types via `stringResource(R.string.equipment_bike_type_*)` and format dates via `DateTimeUtils.formatIsoDate`.
* **`EditDeviceDialog.kt` & `DeviceData.kt`**:
  * Bind LED dot to `isConnected` and pass `currentDevice = liveData ?: data` with `isConnected` to `DeviceStatusRow`.
  * Capitalize `manufacturer` via `replaceFirstChar { it.titlecase(Locale.getDefault()) }` in `raw2UiDeviceData`.
  * Style `EquipmentSection` selector with `OutlinedTextField(readOnly = true, trailingIcon = dropdownChevron)`.
* **`values*/strings.xml`**:
  * Add `equipment_bike_type_mtb/cross/road/tt` across 9 locales; correct `Radtyp` in DE; harmonize empty placeholder.
* **UI Consistency**:
  * Reference screens: `EditEquipmentDialog.kt` (`MultiSelectSportTypeSpinner`), `DeviceItem.kt`.
  * Theme tokens: `TTColor.ConnectionStatusGreen`, `Color.LightGray`, `MaterialTheme.typography.bodySmall`, standard `OutlinedTextField` styling. No one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Localization Strings across 9 Locales (`values*/strings.xml`)**:
   - Add frame types (`mtb`, `cross`, `road`, `tt`); correct `Radtyp` in DE; harmonize empty placeholder.
2. **Step 2: Equipment Card & Selector Polish (`EquipmentTabsScreen.kt`, `BikeTypeSelector.kt`, `EditEquipmentDialog.kt`)**:
   - Implement Strava inline badge deduplication, localized frame types, and `DateTimeUtils.formatIsoDate`.
3. **Step 3: Sensor Detail Synchronization, Capitalization & Styling (`EditDeviceDialog.kt`, `DeviceData.kt`)**:
   - Synchronize LED dot with `DeviceStatusRow`, titlecase manufacturer, and style `EquipmentSection` as `OutlinedTextField`.
4. **Step 4: Contract & Unit Verification Suite (`EquipmentSensorsUiPolishContractTest.kt`)**:
   - Author tests for `TST-UI-492.1`–`TST-UI-492.4`.
5. **Step 5: Test Execution & Verification**:
   - Execute targeted tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.equipment.*" --tests "com.atrainingtracker.banalservice.ui.devices.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.
   - Run `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and localization parity suite in Stage 4; clean-room regression in Stage 5.
* **Rollback Plan**: Atomic commit on `feature/ATT-5847`; clean revert via `git reset --hard` if tests fail.
