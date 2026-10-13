# Stage 2: Requirement & Test Specification - ATT-3454: Move missing ANT+ adapter warning toggle to sensor search and behavior tuning section

**Ticket**: [ATT-3454](https://atrainingtracker.atlassian.net/browse/ATT-3454)  
**Sub-task**: [ATT-3480](https://atrainingtracker.atlassian.net/browse/ATT-3480) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-UI-353` (*Persistent Missing ANT+ Adapter Dialog Suppression and Advanced Settings Warning Reset*), amended  
**Test Spec ID**: `TST-UI-313`  
**Branch**: `feature/ATT-3454`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Requirement Specification (Amending REQ-UI-353)

### 1.1 Problem Statement & Rationale
In the Advanced Tuning settings interface (`AdvancedTuningDialog.kt` and `AdvancedTuningScreen.kt`), the toggle *"Warnung bei fehlendem ANT+ Adapter"* (`tuning_show_ant_missing_adapter_warnings_title`, introduced in Sprint 2026-41.10 under ATT-3220) is currently rendered at the bottom of the *"Sensoren, GPS & Filter"* section (`SensorsGpsFilterSection.kt`).

From a domain cohesion and UX perspective, this toggle is misplaced:
1. **Semantic Scope Separation**:
   *"Sensoren, GPS & Filter"* concentrates on continuous telemetry signal processing: GPS horizontal accuracy threshold (5–250 m), barometric altitude moving average window (5–60 s), and minimum speed for slope calculations (0.1–2.0 m/s). Placing an alert suppression toggle here pollutes the mathematical filtering category.
2. **Alert Trigger Context**:
   The missing ANT+ adapter warning dialog (`AntMissingAdapterDialog`) is dispatched by `BANALService` exclusively during peripheral discovery and sensor search sweeps (`startSearch()`) when an athlete searches for ANT+ sensors on hardware lacking an ANT+ adapter.
3. **Cohesion in Sensor Search & Behavior**:
   The *"Sensorsuche & Verhalten"* category (`SensorSearchTuningSection.kt`) was established specifically for search retries, automatic search triggers, and peripheral discovery behaviors. Grouping the missing ANT+ adapter warning toggle into this section establishes intuitive semantic cohesion and cleans up the GPS/filtering section.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-353` (*Persistent Missing ANT+ Adapter Dialog Suppression and Advanced Settings Warning Reset*), Clause 4 (*Advanced Settings Re-Enablement Toggle*), amending `REQ-UI-323` and complementing `REQ-SET-073`. Target: Relocate toggle from `SensorsGpsFilterSection.kt` to `SensorSearchTuningSection.kt`.
* **Historical Origin & Commit Trace**: `Sprint 2026-41.6` (`ATT-2939`, commit `05cb0ba4`): Migrated dialog to Compose. `Sprint 2026-41.10` (`ATT-3220`, commits `4b9c93fd`, `e7b966fa`): Implemented persistent dismissal suppression via DataStore and placed toggle in `SensorsGpsFilterSection.kt` as an initial landing spot. `Sprint 2026-42.1` (`ATT-3165`): Added full-screen `AdvancedTuningScreen.kt`. `Sprint 2026-42.2` (`ATT-3454`): Relocates toggle to `SensorSearchTuningSection.kt` for semantic cohesion.
* **Root Reason for Existing Formulation**: In ATT-3220, `SensorsGpsFilterSection` was chosen because it was the only section containing "Sensors" in its title at the time, before `SensorSearchTuningSection` was fully expanded. However, telemetry filtering and peripheral discovery alerts are distinct domains.
* **Preservation of Core Invariants**:
  1. Persistent suppression key `KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG` in `TuningPreferencesDataStore` MUST remain unaltered.
  2. Inverted helper property `showAntMissingAdapterWarnings: Boolean get() = !suppressAntMissingAdapterDialog` in `TuningConfig` MUST be preserved.
  3. Default factory value (`suppress = false`, `warnings = true`) and reset logic (`resetToDefaults()`) MUST be preserved.
  4. Broadcast reception guard in `MainActivityWithNavigation.kt` MUST remain untouched.
  5. In-app dialog dismissal via `"Verstanden"` setting `suppress = true` in `ATrainingTrackerApp.kt` MUST remain untouched.
  6. 9-language translation parity for `tuning_show_ant_missing_adapter_warnings_title` and `_desc` MUST remain 100% intact.
  7. Strict file size modularity (< 400 lines per file in `ui.settings.tuning`) enforced by `AdvancedTuningModularityTest.kt` MUST NOT be violated.

---

### 1.2 Functional Requirements (Behavioral Specification - "What, Not How")

1. **Sensor Search Section Integration**:
   - The system SHALL render the *"Warnung bei fehlendem ANT+ Adapter"* toggle item within `SensorSearchTuningSection.kt` under the *"Suchverhalten"* (`prefs_search_behavior_title`) subsection.
   - The composable SHALL bind `title = stringResource(R.string.tuning_show_ant_missing_adapter_warnings_title)`, `summary = stringResource(R.string.tuning_show_ant_missing_adapter_warnings_desc)`, and `isChecked = showAntMissingAdapterWarnings`.
   - Modifying the toggle state SHALL invoke `onShowAntMissingAdapterWarningsChange(newValue)`.

2. **Sensors & GPS Filter Section Purification**:
   - The system SHALL excise the `showAntMissingAdapterWarnings` parameter and its corresponding `TuningToggleItem` completely from `SensorsGpsFilterSection.kt`.
   - `SensorsGpsFilterSection.kt` SHALL exclusively contain numerical filtering controls (GPS accuracy, altitude window, slope speed).

3. **Screen & Dialog Integration**:
   - Both `AdvancedTuningDialog.kt` and `AdvancedTuningScreen.kt` SHALL route `showAntMissingAdapterWarnings` and its state mutator lambda into `SensorSearchTuningSection` instead of `SensorsGpsFilterSection`.
   - Both hosts SHALL preserve DataStore synchronization on initial load (`tuningConfigFlow`), in-memory mutation, and atomic persistence on save.

4. **Modularity & Architecture Preservation**:
   - All files in `ui.settings.tuning` and `ui.settings.tuning.categories` SHALL remain strictly below 400 lines of code.

5. **Localization Parity**:
   - All title and description strings SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT with zero new or missing strings.

---

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Toggle Rendered in Sensor Search Section**:
  * *Given* an athlete opens Advanced Settings (`AdvancedTuningScreen` or `AdvancedTuningDialog`) and expands "Sensorsuche & Verhalten",
  * *When* viewing the category items under "Suchverhalten",
  * *Then* the "Warnung bei fehlendem ANT+ Adapter" toggle is displayed with its title and explanatory summary.
* **Criterion 2: Removal from Sensors, GPS & Filter Section**:
  * *Given* an athlete expands "Sensoren, GPS & Filter" in Advanced Settings,
  * *When* inspecting the category items,
  * *Then* the "Warnung bei fehlendem ANT+ Adapter" toggle is absent, leaving only GPS accuracy, altitude filter window, and slope minimum speed sliders.
* **Criterion 3: State & Persistence Parity**:
  * *Given* `suppressAntMissingAdapterDialog = true` (toggle OFF),
  * *When* the athlete toggles it ON in "Sensorsuche & Verhalten" and saves,
  * *Then* `suppressAntMissingAdapterDialog = false` is saved to `TuningPreferencesDataStore`.
* **Criterion 4: Factory Reset Reversion**:
  * *Given* `suppressAntMissingAdapterDialog = true`,
  * *When* the athlete taps "Auf Werkseinstellungen zurücksetzen",
  * *Then* `suppressAntMissingAdapterDialog` reverts to `false` and the toggle in "Sensorsuche & Verhalten" returns to the checked state.
* **Criterion 5: File Modularity Ceiling**:
  * *Given* the modified Kotlin source files in `com.atrainingtracker.trainingtracker.ui.settings.tuning`,
  * *When* line counts are checked,
  * *Then* every file has fewer than 400 lines of code.

---

## 2. Verification Strategy & Test Cases

### 2.1 Concrete Test Cases

#### TC-1: SensorSearchTuningSection Composable Structural Wiring
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/ant/AntMissingAdapterDialogSuppressionTest.kt`
* **Procedure**:
  1. Inspect `SensorSearchTuningSection.kt`.
  2. Verify it declares parameter `showAntMissingAdapterWarnings: Boolean`.
  3. Verify it declares callback `onShowAntMissingAdapterWarningsChange: (Boolean) -> Unit`.
  4. Verify it references `R.string.tuning_show_ant_missing_adapter_warnings_title`.
  5. Verify it references `R.string.tuning_show_ant_missing_adapter_warnings_desc`.
* **Expected Outcome**: Assertions pass, verifying the toggle is structurally bound in `SensorSearchTuningSection`.

#### TC-2: SensorsGpsFilterSection Composable Cleanup
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/ant/AntMissingAdapterDialogSuppressionTest.kt`
* **Procedure**:
  1. Inspect `SensorsGpsFilterSection.kt`.
  2. Verify it does NOT declare parameter `showAntMissingAdapterWarnings`.
  3. Verify it does NOT reference `R.string.tuning_show_ant_missing_adapter_warnings_title`.
  4. Verify it does NOT reference `R.string.tuning_show_ant_missing_adapter_warnings_desc`.
* **Expected Outcome**: Assertions pass, confirming zero remaining toggle remnants in the GPS/Filter section.

#### TC-3: Host Callers Binding Verification
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/ant/AntMissingAdapterDialogSuppressionTest.kt`
* **Procedure**:
  1. Inspect `AdvancedTuningDialog.kt` and `AdvancedTuningScreen.kt`.
  2. Verify that `SensorSearchTuningSection` is passed `showAntMissingAdapterWarnings` and callback.
  3. Verify that `SensorsGpsFilterSection` is NOT passed `showAntMissingAdapterWarnings`.
* **Expected Outcome**: Assertions pass, verifying correct routing from host state.

#### TC-4: Modularity Invariant Verification
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningModularityTest.kt`
* **Procedure**:
  1. Run `testModularFilesExist` and `testAllTuningSourceFilesUnder400Lines`.
* **Expected Outcome**: All 14 tuning source files are strictly < 400 lines.

#### TC-5: 9-Language Localization Audit
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/translations/TranslationParityTest.kt`
* **Procedure**:
  1. Verify `tuning_show_ant_missing_adapter_warnings_title` and `_desc` exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Outcome**: 100% translation parity.

#### TC-6: Clean-Room Full Test Suite Regression Execution
* **Procedure**: Execute `./gradlew testDebugUnitTest`.
* **Expected Outcome**: 100% pass rate across all suites with zero failures.

---

## 3. Traceability Matrix

| Test Case | Requirement Clause | Target Component | Verification Mechanism |
| :--- | :--- | :--- | :--- |
| **TC-1** | `REQ-UI-353` §4 | `SensorSearchTuningSection.kt` | Unit / Contract Test |
| **TC-2** | `REQ-UI-353` §4 | `SensorsGpsFilterSection.kt` | Unit / Contract Test |
| **TC-3** | `REQ-UI-353` §4 | `AdvancedTuningDialog.kt`, `AdvancedTuningScreen.kt` | Unit / Contract Test |
| **TC-4** | `REQ-UI-353` §6 | `AdvancedTuningModularityTest.kt` | Structural Architecture Test |
| **TC-5** | `REQ-UI-353` §5 | `strings.xml` (all 9 locales) | `TranslationParityTest.kt` |
| **TC-6** | `REQ-UI-353` §6 | Entire Application Test Suite | Gradle clean-room execution |

---

## 4. Conclusion & Stage Gate 2 Readiness
The requirement specification, Chesterton's Fence archaeology, and verification test cases for ATT-3454 are fully detailed. The deliverable is ready for Stage Gate 2 audit.
