# Stage 1: Problem Domain & Root Cause Analysis - ATT-3454

**Ticket**: [ATT-3454](https://atrainingtracker.atlassian.net/browse/ATT-3454)  
**Summary**: Move missing ANT+ adapter warning toggle to sensor search and behavior tuning section  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-UI-353` (amended)  
**Test Mapping**: `TST-UI-313` (amended)  
**Author**: AI Agent 1 (Analyst)  
**Date**: 2026-10-13  

---

## 1. Problem Domain & Enhancement Analysis

### 1.1 Context & Observed Shortcoming
In the Advanced Tuning settings interface (`AdvancedTuningDialog` and `AdvancedTuningScreen`), the toggle *"Warnung bei fehlendem ANT+ Adapter"* (`tuning_show_ant_missing_adapter_warnings_title`, introduced in Sprint 2026-41.10 under ATT-3220) is currently rendered at the bottom of the *"Sensoren, GPS & Filter"* section (`SensorsGpsFilterSection.kt`).

From a domain cohesion and UX perspective, this toggle is misplaced:
1. **Semantic Divergence in Category Scope**:
   The *"Sensoren, GPS & Filter"* section is dedicated to numerical signal processing and physical calculation parameters:
   - GPS minimum accuracy threshold (`tuning_gps_accuracy_title`, 5 m – 250 m).
   - Barometric altitude moving average filter window (`tuning_altitude_window_title`, 5 s – 60 s).
   - Minimum speed threshold for slope / gradient calculation (`tuning_slope_min_speed_title`, 0.1 m/s – 2.0 m/s).
   These controls adjust mathematical algorithms for telemetry filtering.
2. **Trigger Lifecycle of Missing ANT+ Adapter Warnings**:
   The warning dialog (`AntMissingAdapterDialog`) is not triggered by telemetry filtering. It is dispatched by `BANALService` during sensor search cycles (`startSearch()`) whenever an athlete searches for ANT+ peripherals on a device lacking built-in or USB ANT+ adapter hardware.
3. **Natural Placement in Sensor Search & Behavior**:
   The *"Sensorsuche & Verhalten"* accordion section (`SensorSearchTuningSection.kt`) was established specifically to group discovery and hardware search behavior settings:
   - Number of search retry attempts (`prefsNumberOfSearchTriesTitle`, 1–5).
   - Automatic search trigger conditions (on app start, on resume from pause, on sport switch, on tracking start).
   - Discovery behaviors (sport-specific devices only, change sport when device gets lost).
   Placing the missing ANT+ adapter warning toggle inside the Sensor Search section creates semantic cohesion by grouping alert suppression directly alongside the search routines that produce it.

---

### 1.2 Architectural Gap & Target Domain Analysis

1. **State & Persistence Architecture**:
   - The warning suppression preference is persisted in DataStore via `TuningPreferencesDataStore` using `KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG` (`booleanPreferencesKey("tuning_suppress_ant_missing_adapter_dialog")`).
   - `TuningConfig` exposes `val suppressAntMissingAdapterDialog: Boolean` and helper `val showAntMissingAdapterWarnings: Boolean get() = !suppressAntMissingAdapterDialog`.
   - `AdvancedTuningDialog` and `AdvancedTuningScreen` manage `showAntMissingAdapterWarnings` state in memory, synchronizing it with `tuningConfigFlow` and writing back `suppressAntMissingAdapterDialog = !showAntMissingAdapterWarnings` upon save or reset.
   - Importantly, `SensorSearchPreferences` manages legacy search parameters via `SharedPreferences`. The missing adapter warning toggle MUST remain bound to DataStore (`TuningPreferencesDataStore` / `TuningConfig`), passed as explicit state and callback (`showAntMissingAdapterWarnings: Boolean`, `onShowAntMissingAdapterWarningsChange: (Boolean) -> Unit`) to avoid cross-store coupling or synchronization lag.

2. **Composables Restructuring**:
   - In `SensorsGpsFilterSection.kt`:
     - Remove `showAntMissingAdapterWarnings: Boolean = true` and `onShowAntMissingAdapterWarningsChange: (Boolean) -> Unit = {}` parameters.
     - Remove the `TuningToggleItem` rendering `tuning_show_ant_missing_adapter_warnings_title`.
     - File size drops from 93 lines to ~80 lines.
   - In `SensorSearchTuningSection.kt`:
     - Add `showAntMissingAdapterWarnings: Boolean = true` and `onShowAntMissingAdapterWarningsChange: (Boolean) -> Unit = {}` to the primary `@Composable fun SensorSearchTuningSection` signature.
     - Also add them with default values to the overload accepting `prefs: SensorSearchPreferences`.
     - Render `TuningToggleItem` under the *"Suchverhalten"* (`prefs_search_behavior_title`) subsection.
     - File size increases from 205 lines to ~225 lines, comfortably below the 400-line ceiling enforced by `AdvancedTuningModularityTest.kt`.
   - In `AdvancedTuningDialog.kt` and `AdvancedTuningScreen.kt`:
     - Disconnect `showAntMissingAdapterWarnings` from `SensorsGpsFilterSection`.
     - Pass `showAntMissingAdapterWarnings` and callback into `SensorSearchTuningSection`.
     - File line count in `AdvancedTuningDialog.kt` remains ~397 lines (< 400 lines threshold).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

### 2.1 Historical Context & Commit Trace
1. **Sprint 2026-41.6 (ATT-2939 / REQ-UI-323, commit `05cb0ba4`)**:
   Migrated `AntMissingAdapterDialog` and `AntMissingDependencyDialog` to Jetpack Compose Material 3 and integrated the ANT+ status sheet.
2. **Sprint 2026-41.10 (ATT-3220 / REQ-UI-353, commits `4b9c93fd`, `e7b966fa`)**:
   - Introduced persistent dialog dismissal suppression via `TuningPreferencesDataStore.KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG`.
   - Implemented re-enablement toggle in Advanced Tuning. At the time of implementation, `SensorsGpsFilterSection` was chosen as a quick landing spot because it was the only section with "Sensors" in its title, prior to the refinement and expansion of `SensorSearchTuningSection`.
3. **Sprint 2026-42.1 (ATT-3165 / REQ-UI-343)**:
   Added full-screen `AdvancedTuningScreen.kt`, mirroring the layout and controls of `AdvancedTuningDialog.kt`.

### 2.2 Chesterton's Fence Analysis (Mandatory 4 Fields)
1. **Original Requirement ID & Target**:
   `REQ-UI-353` (*Persistent Missing ANT+ Adapter Dialog Suppression and Advanced Settings Warning Reset*), targeting `SensorsGpsFilterSection.kt`, `AdvancedTuningDialog.kt`, and `AdvancedTuningScreen.kt`.
2. **Historical Origin & Commit Trace**:
   Sprint 2026-41.10 (`ATT-3220`, commit `e7b966fa`).
3. **Root Reason for Existing Formulation**:
   In ATT-3220, `SensorsGpsFilterSection` was used because it had "Sensors" in its heading. However, this conflated numerical telemetry filtering (GPS, barometric altitude, slope speed) with peripheral discovery alert behavior.
4. **Preservation of Core Invariants**:
   - Persistent suppression via DataStore `KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG` MUST remain 100% intact.
   - Clean install default (`suppress = false`, `warnings = true`) MUST NOT be changed.
   - Reset to defaults (`resetToDefaults()`) MUST revert the toggle to ON (`warnings = true`).
   - In-app dialog dismissal (`"Verstanden"`) MUST continue setting `suppress = true`.
   - Broadcast filtering guard in `MainActivityWithNavigation.kt` MUST remain unchanged.
   - 9-language localization parity (EN, DE, ES, FR, IT, JA, NL, PL, PT) for `tuning_show_ant_missing_adapter_warnings_title` and `_desc` MUST remain untouched.
   - `AdvancedTuningModularityTest` line limit (< 400 lines) MUST NOT be violated.

---

## 3. User Scope Grounding & Boundary Delineation (ATT-1250)

### 3.1 In-Scope Objectives
1. **Relocate Composable Toggle**:
   - Excising `showAntMissingAdapterWarnings` parameter and `TuningToggleItem` from `SensorsGpsFilterSection.kt`.
   - Adding `showAntMissingAdapterWarnings` and callback to `SensorSearchTuningSection.kt` under *"Suchverhalten"*.
2. **Update Dialog & Screen Callers**:
   - In `AdvancedTuningDialog.kt`: wire `showAntMissingAdapterWarnings` to `SensorSearchTuningSection`.
   - In `AdvancedTuningScreen.kt`: wire `showAntMissingAdapterWarnings` to `SensorSearchTuningSection`.
3. **Update Contract & Modularity Tests**:
   - Update `AntMissingAdapterDialogSuppressionTest.kt` to assert structural toggle wiring on `SensorSearchTuningSection.kt` and assert its absence from `SensorsGpsFilterSection.kt`.
   - Verify `AdvancedTuningModularityTest.kt` passes (< 400 lines across all files in `ui.settings.tuning`).
4. **Specification & Governance Alignment**:
   - Amend `REQ-UI-353` in `docs/requirements.md` and `TST-UI-313` in `docs/tests.md` to reflect the new canonical UI location.

### 3.2 Out-of-Scope (Boundaries to Prevent Scope Creep)
- Do NOT alter `TuningPreferencesDataStore` schema, keys, or flows.
- Do NOT alter `SensorSearchPreferences` SharedPreferences backing or migrate it to DataStore.
- Do NOT modify `MainActivityWithNavigation.kt` or `ATrainingTrackerApp.kt`.
- Do NOT alter strings or translations in `res/values*/strings.xml`.
- Do NOT re-order or modify any other tuning accordions or sections.

---

## 4. Traceability & Downstream Impact

| Artifact | Impact Description |
| :--- | :--- |
| `docs/requirements.md` | Amend `REQ-UI-353` section 4 to specify `SensorSearchTuningSection.kt` instead of `SensorsGpsFilterSection.kt`. |
| `docs/tests.md` | Amend `TST-UI-313` section 3 to reference `SensorSearchTuningSectionTest` / `AntMissingAdapterDialogSuppressionTest`. |
| `SensorsGpsFilterSection.kt` | Remove toggle parameter and UI item. |
| `SensorSearchTuningSection.kt` | Add toggle parameter and UI item under search behavior. |
| `AdvancedTuningDialog.kt` | Pass toggle state to `SensorSearchTuningSection`. |
| `AdvancedTuningScreen.kt` | Pass toggle state to `SensorSearchTuningSection`. |
| `AntMissingAdapterDialogSuppressionTest.kt` | Update structural assertions to target `SensorSearchTuningSection.kt`. |
| `AdvancedTuningModularityTest.kt` | Verify line count < 400 invariant. |

---

## 5. Conclusion & Stage Gate 1 Readiness
The problem domain, root cause, Chesterton's Fence archaeology, and boundary constraints for ATT-3454 are fully analyzed. The deliverable is ready for Stage Gate 1 audit.
