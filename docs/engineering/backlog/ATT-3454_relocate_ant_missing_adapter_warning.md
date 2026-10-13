# [Verbesserung] [Settings] Move missing ANT+ adapter warning toggle to sensor search and behavior tuning section

**Ticket**: [ATT-3454](https://rainerblind.atlassian.net/browse/ATT-3454)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Status**: Backlog  

---

## Problem Statement & Value Proposition
In Advanced Settings (Erweiterte Einstellungen / `AdvancedTuningDialog`), the toggle "Warnung bei fehlendem ANT+ Adapter" (`tuning_show_ant_missing_adapter_warnings_title`, introduced in ATT-3220) is currently located inside the "Sensoren, GPS & Filter" (`SensorsGpsFilterSection`) accordion section.

From a domain and UX perspective, this toggle is misplaced:
1. "Sensoren, GPS & Filter" concentrates on physical sensor data calculation and signal filtering parameters (e.g. GPS minimum accuracy, barometric altitude moving average window, slope minimum speed).
2. The missing ANT+ adapter warning is triggered specifically during sensor search rounds (`startSearch()`) when looking for ANT+ peripherals on hardware without an ANT+ adapter.
3. Placing this toggle inside "Sensorsuche & Verhalten" (`SensorSearchTuningSection`) creates semantic cohesion, grouping it directly with other discovery and search behavior settings (such as auto-search on launch, resume, sport switch, and retry counts).

## User Story
* **As an**: athlete configuring advanced tuning options
* **I want**: the "Warnung bei fehlendem ANT+ Adapter" toggle located in the "Sensorsuche & Verhalten" section
* **So that**: sensor discovery and search alert configurations are grouped coherently in one intuitive place.

## Scope & Technical Architecture
1. **Composables & Layout Relocation**:
   * In `SensorsGpsFilterSection.kt`: Remove `showAntMissingAdapterWarnings` parameter and the corresponding `TuningToggleItem`.
   * In `SensorSearchTuningSection.kt`: Add `showAntMissingAdapterWarnings` and `onShowAntMissingAdapterWarningsChange` parameters and render the `TuningToggleItem` within the section (or integrate into `SensorSearchPreferences` if desired, while preserving `TuningPreferencesDataStore` binding).
   * In `AdvancedTuningDialog.kt`: Route `showAntMissingAdapterWarnings` state and callback to `SensorSearchTuningSection` instead of `SensorsGpsFilterSection`.
2. **Contract & Regression Tests**:
   * Update `SensorsGpsFilterSectionTest.kt` to verify the toggle is no longer rendered in the GPS/Filter section.
   * Update `SensorSearchTuningSectionTest.kt` and `AntMissingAdapterDialogSuppressionTest.kt` to verify that `SensorSearchTuningSection` binds and controls the toggle.
   * Verify persistence and default resets via `TuningPreferencesDataStore` remain 100% intact.

## Acceptance Criteria (Given-When-Then)
* **Criterion 1: Toggle Rendered in Sensor Search Section**:
  * *Given* an athlete opens Advanced Settings and expands "Sensorsuche & Verhalten"
  * *When* viewing the category items
  * *Then* the "Warnung bei fehlendem ANT+ Adapter" toggle is displayed with its title and explanatory summary.
* **Criterion 2: Removal from Sensors & GPS Filter Section**:
  * *Given* an athlete opens Advanced Settings and expands "Sensoren, GPS & Filter"
  * *When* inspecting the category items
  * *Then* the "Warnung bei fehlendem ANT+ Adapter" toggle is absent, leaving only data filtering controls (GPS accuracy, altitude window, slope speed).
* **Criterion 3: State & Persistence Parity**:
  * *Given* the athlete changes the toggle state in "Sensorsuche & Verhalten" and saves
  * *When* the preference is persisted
  * *Then* `TuningPreferencesDataStore` updates `suppressAntMissingAdapterDialog` accordingly without functional regressions.

## Parent Epic & Sprint Placement
* **Target Epic**: `ATT-355` (Good and consistent UI)
* **Backlog Placement**: Product Backlog (do NOT assign to active sprint)
