# Stage 2: Requirement & Test Specification - ATT-5847: Equipment & Sensors UI: Deduplicate identical Strava gear names, localize ISO dates & frame types ('Road'), fix disconnected green dot & capitalize 'samsung'/'google'

**Ticket**: [ATT-5847](https://rainerblind.atlassian.net/browse/ATT-5847)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-492` (`REQ-UI-492.1`–`REQ-UI-492.4`)  
**Test Spec ID**: `TST-UI-492` (`TST-UI-492.1`–`TST-UI-492.6`)  
**Branch**: `feature/ATT-5847`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-492)

### 1.1 Problem Statement & Rationale
Live audits of equipment and sensor screens revealed redundant duplicate Strava names when identical to local names, raw ISO usage dates (`YYYY-MM-DD`), untranslated bike frame types ("Road", "Time Trial"), German spelling error `Rad Typ`, asymmetric empty placeholders, and sensor dialog issues (green LED when disconnected, lowercase smartphone manufacturers, unstyled equipment button).

### 1.2 Functional & Architectural Requirements
1. **Strava Deduplication (`REQ-UI-492.1`)**: In `EquipmentTabsScreen.kt`, when `stravaName` equals `name`, render inline `StravaOriginalLogo` in the title row and suppress the duplicate subtitle line.
2. **Date & Frame Type Localization (`REQ-UI-492.2`)**: Format `UsageItem` dates via `DateTimeUtils.formatIsoDate(date)` and resolve bike frame types via string resources (`equipment_bike_type_mtb`, `cross`, `road`, `tt`) across all 9 locales.
3. **Spelling & Placeholders (`REQ-UI-492.3`)**: Correct `Rad Typ` $\rightarrow$ `Radtyp` in DE and harmonize sensor empty placeholder to "Keine" / "None".
4. **Sensor Detail Sync & Styling (`REQ-UI-492.4`)**: Synchronize status LED with `DeviceStatusRow`, capitalize manufacturer in `DeviceData.kt`, and style `EquipmentSection` with `OutlinedTextField`.

### 1.3 9-Locale Localization Table (REQ-UI-492.2, REQ-LOC-001)

| Key | EN | DE | ES | FR | IT | JA | NL | PL | PT |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `equipment_bike_type_mtb` | MTB | MTB | MTB | VTT | MTB | MTB | MTB | MTB | MTB |
| `equipment_bike_type_cross` | Cross | Cross | Cross | Cyclo-cross | Cross | クロス | Cross | Przełaj | Cross |
| `equipment_bike_type_road` | Road | Rennrad | Carretera | Route | Corsa | ロード | Racefiets | Szosa | Estrada |
| `equipment_bike_type_tt` | Time Trial | Zeitfahrrad | Contrarreloj | Contre-la-montre | Cronometro | タイムトライアル | Tijdrit | Jazda na czas | Contra-relógio |

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* equipment with matching `stravaName` and `name`, *When* rendered, *Then* inline Strava logo displays in title and subtitle row is omitted.
* *Given* equipment usage dates in German locale, *When* rendered, *Then* formatted as `DD.MM.YYYY` via `DateTimeUtils.formatIsoDate`.
* *Given* a disconnected sensor in `EditDeviceDialog`, *When* displayed, *Then* LED dot is gray and manufacturer name is capitalized.

---

## 2. Test Specification (TST-UI-492) & Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-492.1` | Contract | `EquipmentSensorsUiPolishContractTest.kt` | `REQ-UI-492.1` | Specified |
| `TST-UI-492.2` | Contract | `EquipmentSensorsUiPolishContractTest.kt` | `REQ-UI-492.2` | Specified |
| `TST-UI-492.3` | Contract | `EquipmentSensorsUiPolishContractTest.kt` | `REQ-UI-492.3` | Specified |
| `TST-UI-492.4` | Contract | `EquipmentSensorsUiPolishContractTest.kt` | `REQ-UI-492.4` | Specified |
| `TST-UI-492.5` | Localization | `TranslationParityTest.kt` | `REQ-LOC-001` | Specified |
| `TST-UI-492.6` | Regression | `testDebugUnitTest` (`ui.equipment.*`, `devices.*`) | `REQ-PRO-001` | Specified |
