# Stage 1 Analysis: ATT-5847 - Equipment & Sensors UI: Deduplicate identical Strava gear names, localize ISO dates & frame types ('Road'), fix disconnected green dot & capitalize 'samsung'/'google'

**Ticket**: [ATT-5847](https://rainerblind.atlassian.net/browse/ATT-5847)  
**Sub-task**: [ATT-6028](https://rainerblind.atlassian.net/browse/ATT-6028) (`[Analysis]`)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile (Gear, ANT+/BLE & PMC)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5847`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
During live UI testing of equipment (`Räder & Schuhe`) and sensor management screens (`Sensoren`), several UX regressions and visual inconsistencies were detected:
1. **Strava Name Redundancy**: When local equipment name and linked Strava gear name match (e.g. "Brooks Green Silence"), the card repeats the identical string on two consecutive lines.
2. **Unlocalized Dates & Frame Types**: Equipment cards render raw ISO date strings (`YYYY-MM-DD`) and hardcoded English bike frame types ("Road", "Time Trial").
3. **Spelling & Placeholder Asymmetry**: German label `Rad Typ` has an invalid space (`Radtyp`), while empty-state labels differ ("Keine" vs. "Keine verknüpften Sensoren").
4. **Sensor Detail Discrepancies**: In `EditDeviceDialog`, disconnected sensors display a green LED dot next to "100% (nicht verbunden)", internal manufacturers display in lowercase ("samsung", "google"), and the equipment selector renders as an unstyled bare `TextButton`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Strava Redundancy**: In `EquipmentTabsScreen.kt:493-504`, subtitle row unconditionally prints `StravaOriginalLogo()` + `item.stravaName` whenever `!item.stravaName.isNullOrBlank()`, lacking equality check against `item.name`.
2. **Unlocalized Strings**: `EquipmentTabsScreen.kt:421-427, 549-560` and `BikeTypeSelector.kt:48-55` hardcode frame type strings and pass unformatted ISO strings to `UsageItem`.
3. **Spelling & Placeholders**: `values-de/strings.xml:898` defines `Rad Typ`; `EditEquipmentDialog.kt:230` uses verbose string `equipment_no_sensors_linked`.
4. **Sensor Status & Styling**: In `EditDeviceDialog.kt:109-126`, LED dot checks `liveData?.isConnected ?: data.isConnected` while `DeviceStatusRow` evaluates `data.isConnected`, causing dot/text desynchronization. `DeviceData.kt:126` passes raw Android `Build.BRAND` without capitalization. `EquipmentSection` uses bare `TextButton` instead of `OutlinedTextField`.
* **Independent Peer Review & Verification**:
  * Visually inspected all 6 attached screenshots (`Rule 71`): `screen_06_sensoren.png`, `screen_07_raeder_schuhe.png`, `round5_06_bike_edit.png`, `round5_07_shoes_tab.png`, `round5_08_shoe_edit.png`, `round5_09_sensor_edit.png`.
  * Verified date formatting synergy: Reuses `DateTimeUtils.formatIsoDate(...)` (`REQ-UI-475.3`, `Rule 36` DRY) across `UsageItem`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Inline Strava logo badge when gear name matches local equipment name; suppress duplicate subtitle line.
  * Localize ISO usage dates via `DateTimeUtils.formatIsoDate` and bike frame types across all 9 locales.
  * Correct `Radtyp` in DE and harmonize empty placeholders to "Keine" / "None" across 9 locales.
  * Synchronize sensor status LED with `DeviceStatusRow`, capitalize manufacturer names, and apply `OutlinedTextField` styling to `EquipmentSection`.
* **Out-of-Scope Non-Goals**:
  * Altering database schemas (`Equipment.db`, `Devices.db`) or Strava synchronization logic.
  * Modifying sensor discovery or Bluetooth/ANT+ background connection pipelines.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-492`, refining `REQ-UI-061` (*Universal List Card Contracts*), `REQ-UI-149` (*Modal Bottom Sheet Dialogs*), `REQ-UI-256` (*Equipment Management UI*), and `REQ-UI-283` (*Sensor Management*).
* **Historical Origin & Commit Trace**: `ATT-2126` (`commit fc10ad81`) and `ATT-2382` established equipment cards and sensor dialogs.
* **Root Reason for Existing Formulation**: Strava subtitle was introduced to display external provenance, but omitted equality deduplication. Frame types and status LEDs were implemented with prototype defaults before unified styling standards.
* **Preservation of Core Invariants**: Card deletion gestures (`REQ-UI-061`), sensor-equipment link persistence (`REQ-UI-256`, `REQ-UI-257`), and 100% test suite pass rate remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
* `EquipmentTabsScreen.kt`: Conditionally render inline `StravaOriginalLogo` when `item.stravaName == item.name`; localize frame types and apply `DateTimeUtils.formatIsoDate`.
* `BikeTypeSelector.kt`: Replace hardcoded strings with localized string resources (`equipment_bike_type_mtb`, `cross`, `road`, `tt`).
* `EditEquipmentDialog.kt`: Harmonize `equipment_no_sensors_linked` with `equipment_no_sports_linked`.
* `EditDeviceDialog.kt`: Unify `currentDevice = liveData ?: data`, pass `isConnected` to `DeviceStatusRow`, style `EquipmentSection` with `OutlinedTextField`.
* `DeviceData.kt`: Capitalize `manufacturer` via `.replaceFirstChar` in `raw2UiDeviceData`.
* `values*/strings.xml`: Correct `Radtyp` in DE; add 9-locale frame types and unified "Keine" / "None".

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Equipment and sensor database schemas and foreign key relations remain 100% unchanged.
  2. 9-language localization parity (`REQ-LOC-001`) strictly maintained across all new/modified strings.
  3. Zero regression in sensor connection telemetry or background scanning pipelines.
* **Risk Rating**: **LOW** (Presentation-layer refinements, string resource additions, and null-safe utility calls).
