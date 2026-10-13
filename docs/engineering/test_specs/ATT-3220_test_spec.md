# Stage 2: Requirement & Test Specification - ATT-3220: Persistently suppress missing ANT adapter dialog after dismissal with setting to reset

**Ticket**: [ATT-3220](https://atrainingtracker.atlassian.net/browse/ATT-3220)  
**Sub-task**: [ATT-3286](https://atrainingtracker.atlassian.net/browse/ATT-3286) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*[Epic] Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-UI-353` (*Persistent Missing ANT+ Adapter Dialog Suppression and Advanced Settings Warning Reset*)  
**Test Spec ID**: `TST-UI-313`  
**Branch**: `feature/ATT-3220`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (REQ-UI-353)

### 1.1 Problem Statement & Rationale
On Android devices lacking native ANT+ hardware, background sensor scanning and connection attempts frequently fire `com.atrainingtracker.ADAPTER_NOT_DETECTED` broadcasts. In `ATT-2939` (`REQ-UI-323`), the missing adapter alert was modernized into a Material 3 Compose dialog (`AntMissingAdapterDialog`). However, tapping "Verstanden" ("Got it") only clears the transient `activity.antDialogState` in-memory. Because dismissal is not persisted to disk, subsequent sensor searches, sport changes, or scan cycles repeatedly trigger the "Fehlender ANT Adapter" popup.

This requirement establishes persistent dismissal in `TuningPreferencesDataStore`, guards `MainActivityWithNavigation` against repeated broadcasts once dismissed, and exposes an Advanced Tuning toggle ("Show missing ANT+ adapter warnings") allowing athletes to restore diagnostic warnings if they later attach a physical USB-OTG ANT+ stick.

### 1.2 Functional & Architectural Requirements
The system SHALL persistently suppress the missing ANT+ adapter dialog upon user dismissal and provide an Advanced Tuning toggle to inspect and re-enable warnings (ATT-3220, amending `REQ-UI-323` and complementing `REQ-SET-073`):
1. *Persistent Suppression Preference (`TuningPreferencesDataStore.kt`)*:
   - In `TuningPreferencesDataStore.kt`, the system SHALL define preference key `KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG = booleanPreferencesKey("tuning_suppress_ant_missing_adapter_dialog")`.
   - On clean installation, the preference SHALL default to `false` (`TuningPreferencesDefaults.SUPPRESS_ANT_MISSING_ADAPTER_DIALOG = false`).
   - `TuningConfig` SHALL declare `val suppressAntMissingAdapterDialog: Boolean = TuningPreferencesDefaults.SUPPRESS_ANT_MISSING_ADAPTER_DIALOG` and helper property `val showAntMissingAdapterWarnings: Boolean get() = !suppressAntMissingAdapterDialog`.
   - `TuningPreferencesDataStore` SHALL expose `val suppressAntMissingAdapterDialogFlow: Flow<Boolean>` and mutator `suspend fun setSuppressAntMissingAdapterDialog(suppress: Boolean)`.
   - Invoking `resetToDefaults()` in `TuningPreferencesDataStore` SHALL restore `suppressAntMissingAdapterDialog = false`.
2. *Broadcast Reception Guard (`MainActivityWithNavigation.kt`)*:
   - In `MainActivityWithNavigation.kt`, the activity SHALL observe `suppressAntMissingAdapterDialogFlow` from `TuningPreferencesDataStore`.
   - When `mAntAdapterMissingReceiver` receives `com.atrainingtracker.ADAPTER_NOT_DETECTED`:
     - If `suppressAntMissingAdapterDialog == true`, the broadcast SHALL be discarded immediately without mutating `antDialogState`.
     - If `suppressAntMissingAdapterDialog == false`, the activity SHALL evaluate `showANTAdapterMissingDialog()` and display `AntMissingAdapterDialog` if not already visible.
3. *Dismissal Persistence (`ATrainingTrackerApp.kt`)*:
   - When the athlete taps "Verstanden" (`onDismiss`) on `AntMissingAdapterDialog`, the app SHALL asynchronously invoke `tuningPreferencesDataStore.setSuppressAntMissingAdapterDialog(true)`.
   - Tapping the primary action button "ANT+ Status anzeigen" (`onViewStatus`) SHALL dismiss the dialog and display `AntServicesStatusSheet` without mutating the suppression preference.
4. *Advanced Settings Re-Enablement Toggle (`SensorsGpsFilterSection.kt` & `AdvancedTuningDialog.kt`)*:
   - In `SensorsGpsFilterSection.kt`, the system SHALL render a `TuningToggleItem`:
     - Title: `tuning_show_ant_missing_adapter_warnings_title` ("Show missing ANT+ adapter warnings" / "Warnung bei fehlendem ANT+ Adapter")
     - Summary: `tuning_show_ant_missing_adapter_warnings_desc` ("Show alert dialog when an ANT+ sensor is searched but no adapter is detected" / "Hinweisdialog anzeigen, wenn nach ANT+ Sensoren gesucht wird, aber kein Adapter erkannt wurde")
     - Checked state: `showAntMissingAdapterWarnings` (`!suppressAntMissingAdapterDialog`).
     - Toggling off SHALL set `suppressAntMissingAdapterDialog = true`.
     - Toggling on SHALL set `suppressAntMissingAdapterDialog = false`.
   - `AdvancedTuningDialog.kt` SHALL integrate this preference into its state management and persistence flows.
5. *9-Language Localization Parity*:
   - Title and summary strings SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries and matching typography.
6. *Preservation of System Invariants*:
   - First-time alert presentation on clean install MUST be preserved.
   - `AntMissingDependencyDialog` (`com.atrainingtracker.ANT_DEPENDENCY_MISSING`) MUST NOT be suppressed by this preference.
   - 100% full clean-room unit test pass rate across all suites.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-UI-323` (*Material 3 Compose Migration for ANT Missing Adapter and Missing Dependency Alerts with Direct ANT+ Status Sheet Integration*, Sprint 2026-41.6, ATT-2939) and complements `REQ-SET-073` (*DataStore Expert Tuning Repository*).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.6 (`ATT-2939`, commit `05cb0ba4`).
3. *Root Reason for Existing Formulation*: In `ATT-2939`, the alert was migrated to Compose and prevented stacking of open dialogs in memory, but did not persist dismissal to persistent storage, leading to recurring alert popups across background scans.
4. *Preservation of Core Invariants*: Single-instance presentation, direct status sheet shortcut, Play Store dependency resolution, and 100% test pass rate remain preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (First-Time Alert & Dismissal Persistence)**:
  * *Given* a device without ANT+ hardware on clean installation (`suppressAntMissingAdapterDialog = false`)
  * *When* `com.atrainingtracker.ADAPTER_NOT_DETECTED` is emitted
  * *Then* `AntMissingAdapterDialog` is presented
  * *When* the athlete taps "Verstanden"
  * *Then* the dialog is dismissed and `suppressAntMissingAdapterDialog = true` is saved to DataStore.
* **Criterion 2 (Subsequent Broadcast Suppression)**:
  * *Given* `suppressAntMissingAdapterDialog = true`
  * *When* subsequent `com.atrainingtracker.ADAPTER_NOT_DETECTED` broadcasts are received
  * *Then* `MainActivityWithNavigation` discards the broadcast and `antDialogState` remains `null`.
* **Criterion 3 (Advanced Settings Re-Enablement)**:
  * *Given* `suppressAntMissingAdapterDialog = true`
  * *When* the athlete navigates to Advanced Settings -> Sensors & GPS
  * *Then* the "Show missing ANT+ adapter warnings" toggle is displayed in the OFF position
  * *When* the athlete toggles it to ON and saves
  * *Then* `suppressAntMissingAdapterDialog = false` is saved and future broadcasts will trigger the dialog.
* **Criterion 4 (Default Reset Safety)**:
  * *Given* `suppressAntMissingAdapterDialog = true`
  * *When* the athlete taps "Auf Werkseinstellungen zurücksetzen"
  * *Then* `suppressAntMissingAdapterDialog` reverts to `false`.

---

## 2. Test Specification (TST-UI-313)

### 2.1 Test Cases & Verification Procedures

#### Case 1: DataStore Preference Serialization & Default Reset Unit Tests (`TuningPreferencesDataStoreTest.kt`)
- Verify `TuningConfig` initializes with `suppressAntMissingAdapterDialog = false` and `showAntMissingAdapterWarnings = true`.
- Verify `setSuppressAntMissingAdapterDialog(true)` persists and emits `true` via `suppressAntMissingAdapterDialogFlow`.
- Verify `setSuppressAntMissingAdapterDialog(false)` persists and emits `false`.
- Verify `resetToDefaults()` restores `suppressAntMissingAdapterDialog = false`.

#### Case 2: Broadcast Guard Unit Tests (`AntMissingAdapterDialogSuppressionTest.kt`)
- When `suppressAntMissingAdapterDialog == false` and `antDialogState == null`: verify `showANTAdapterMissingDialog()` transitions `antDialogState` to `AntDialogState.MissingAdapter`.
- When `suppressAntMissingAdapterDialog == true`: verify `showANTAdapterMissingDialog()` discards the broadcast and `antDialogState` remains `null`.
- When `antDialogState != null`: verify subsequent calls do not duplicate state.

#### Case 3: Advanced Settings Toggle & State Binding Tests (`SensorsGpsFilterSectionTest.kt`)
- Verify `SensorsGpsFilterSection` renders `TuningToggleItem` with `checked = showAntMissingAdapterWarnings`.
- Verify toggling invokes `onShowAntMissingAdapterWarningsChange`.
- Verify `AdvancedTuningDialog` loads `showAntMissingAdapterWarnings` from `tuningConfigFlow` and writes back on save.

#### Case 4: Localization Parity Audit (`TranslationParityTest.kt`)
- Verify `tuning_show_ant_missing_adapter_warnings_title` and `tuning_show_ant_missing_adapter_warnings_desc` exist and are non-empty across all 9 supported locales:
  - `values/strings.xml` (EN)
  - `values-de/strings.xml` (DE)
  - `values-es/strings.xml` (ES)
  - `values-fr/strings.xml` (FR)
  - `values-it/strings.xml` (IT)
  - `values-ja/strings.xml` (JA)
  - `values-nl/strings.xml` (NL)
  - `values-pl/strings.xml` (PL)
  - `values-pt/strings.xml` (PT)

#### Case 5: Full Clean-Room Regression Execution
- Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case | Method / Component | Verification Criteria |
| :--- | :--- | :--- | :--- |
| `REQ-UI-353.1` (DataStore Preference) | Case 1 | `TuningPreferencesDataStoreTest.kt` | Key default `false`, serialization, atomic reset. |
| `REQ-UI-353.2` (Broadcast Guard) | Case 2 | `AntMissingAdapterDialogSuppressionTest.kt` | Discard broadcast when suppressed; allow when unsuppressed. |
| `REQ-UI-353.3` (Dismissal Persistence) | Case 1 & 2 | `ATrainingTrackerApp.kt` | "Verstanden" triggers `setSuppressAntMissingAdapterDialog(true)`. |
| `REQ-UI-353.4` (Advanced Settings) | Case 3 | `SensorsGpsFilterSectionTest.kt` | Toggle reflects and updates preference. |
| `REQ-UI-353.5` (9-Language Parity) | Case 4 | `TranslationParityTest.kt` | 100% translation coverage across 9 locales. |
| System Invariants | Case 5 | Clean-Room Suite | 100% unit test pass rate across entire project. |
