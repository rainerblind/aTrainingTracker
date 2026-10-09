# Stage 2: Requirement & Test Specification - ATT-2939

**Ticket**: [ATT-2939](https://atrainingtracker.atlassian.net/browse/ATT-2939)  
**Sub-task**: [ATT-2975](https://atrainingtracker.atlassian.net/browse/ATT-2975) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Requirement Mapping**: `REQ-UI-323` (*Material 3 Compose Migration for ANT Missing Adapter and Missing Dependency Alerts with Direct ANT+ Status Sheet Integration*)  
**Test Spec ID**: `TST-UI-283`  
**Branch**: `feature/ATT-2939`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Formal Requirement Specification (`REQ-UI-323`)

### Requirement Text
The system SHALL modernize legacy ANT missing adapter and missing dependency alert dialogs into Material 3 Jetpack Compose components in `AntAlertDialogs.kt`, eliminate raw Android View `android.app.AlertDialog.Builder` popups in `MainActivityWithNavigation.kt`, provide direct actionable shortcuts to `AntServicesStatusSheet.kt`, correct the German localization typographical error in `values-de/strings.xml`, and enforce 100% translation parity across all 9 supported application locales (ATT-2939, amending `REQ-UI-296` and complementing `REQ-UI-309`):

1. **Explicit Dialog State Model (`AntDialogState.kt`)**:
   * The system SHALL declare a sealed hierarchy `AntDialogState` in `com.atrainingtracker.trainingtracker.ui.ant`:
     * `data object MissingAdapter : AntDialogState()`
     * `data class MissingDependency(val dependencyName: String, val packageName: String) : AntDialogState()`
2. **Material 3 Compose Alert Dialogs (`AntAlertDialogs.kt`)**:
   * `AntMissingAdapterDialog`:
     * SHALL compose an `androidx.compose.material3.AlertDialog`.
     * SHALL render the official untinted ANT+ logo (`safePainterResource(id = R.drawable.ant_logo)`) within the dialog icon slot with size 36.dp.
     * SHALL display `stringResource(R.string.ant_missing_adapter_title)` as title.
     * SHALL display `stringResource(R.string.ant_missing_adapter_message)` as body text.
     * SHALL provide a primary action button labeled `stringResource(R.string.ant_dialog_view_status)` ("ANT+ Status anzeigen" / "View ANT+ Status") invoking `onViewStatus()`.
     * SHALL provide a dismiss button labeled `stringResource(R.string.ant_dialog_dismiss)` ("Verstanden" / "Got it") invoking `onDismiss()`.
   * `AntMissingDependencyDialog`:
     * SHALL compose an `androidx.compose.material3.AlertDialog`.
     * SHALL render the official untinted ANT+ logo (`safePainterResource(id = R.drawable.ant_logo)`) within the dialog icon slot with size 36.dp.
     * SHALL display `stringResource(R.string.ant_missing_dependency_title)` as title.
     * SHALL display `stringResource(R.string.ant_missing_dependency_message, dependencyName)` as body text.
     * SHALL provide a primary action button labeled `stringResource(R.string.go_to_store)` invoking `onGoToStore()`.
     * SHALL provide a dismiss button labeled `stringResource(R.string.cancel)` invoking `onDismiss()`.
3. **Actionable Shortcut & Sheet Integration (`MainActivityWithNavigation.kt` & `ATrainingTrackerApp.kt`)**:
   * Tapping "View ANT+ Status" on `AntMissingAdapterDialog` SHALL dismiss the dialog and immediately present `AntServicesStatusSheet`, providing clear visibility into USB-OTG adapter and ANT+ USB stick requirements without requiring manual navigation through settings.
   * Tapping "Go to Store" on `AntMissingDependencyDialog` SHALL launch the Google Play Store with `market://details?id=<package>` with flags `FLAG_ACTIVITY_NEW_TASK`.
4. **Elimination of Raw Platform Dialogs**:
   * `MainActivityWithNavigation.kt` SHALL NOT instantiate `AlertDialog.Builder` or raw `android.app.AlertDialog` for `com.atrainingtracker.ADAPTER_NOT_DETECTED` or `com.atrainingtracker.ANT_DEPENDENCY_MISSING` broadcast receivers.
5. **Non-Repetitive Alert Presentation**:
   * In `MainActivityWithNavigation.kt`, incoming failure broadcasts when an ANT alert dialog or `AntServicesStatusSheet` is already visible SHALL NOT duplicate or stack alerts.
6. **German Typographical Error Correction & 9-Language Parity**:
   * In `values-de/strings.xml:953`, the spelling error "intalliert" SHALL be replaced with "installiert".
   * String resources `ant_dialog_view_status` and `ant_dialog_dismiss` SHALL be defined and localized with 100% parity across all 9 supported locales: English (default), German (`values-de`), Spanish (`values-es`), French (`values-fr`), Italian (`values-it`), Japanese (`values-ja`), Dutch (`values-nl`), Polish (`values-pl`), and Portuguese (`values-pt`).
7. **Preservation of System Invariants**:
   * `BANALService` and `MyANTDevice` sensor discovery and pairing pipelines MUST remain 100% operational.
   * Cold-start check elimination (`REQ-UI-296`) MUST remain strictly preserved.
   * 100% test pass rate across the full test suite MUST be strictly preserved.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)

1. **Original Requirement ID & Target**: Amends `REQ-UI-296` (*Modernized Contextual ANT+ System Service Guidance & Cold-Start Dialog Elimination*, ATT-2513) Clause 1 & 4, and complements `REQ-UI-309` (*Official Untinted ANT+ Logo Rendering and Bluetooth LE Advisory Note Elimination in ANT+ System Services Sheet*, ATT-2749).
2. **Historical Origin & Commit Trace**: Ticket `ATT-2513` (Sprint 2026-41.3) and Ticket `ATT-2749` (Sprint 2026-41.4). Legacy code in `MainActivityWithNavigation.kt` authored in 2017 (`05.01.17`, commit `MainActivityWithNavigation.java`), ported to Kotlin under ATT-657 (Sprint 2026-40.1).
3. **Root Reason for Existing Formulation**: In ATT-2513, cold-start checking was eliminated and `AntServicesStatusSheet` was introduced for sensor tabs. However, the runtime broadcast receivers in `MainActivityWithNavigation.kt` for dynamic scan failure events (`ADAPTER_NOT_DETECTED` and `ANT_DEPENDENCY_MISSING`) were overlooked and retained legacy `android.app.AlertDialog.Builder` popups with raw view styles and dead-end "OK" buttons.
4. **Preservation of Core Invariants**: Hardware detection in `MyANTDevice.java`, Google Play store launches for missing packages, single-instance dialog presentation per scan/session, and 100% test pass rate remain strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Material 3 Missing Adapter Dialog)**:
  * *Given* a smartphone lacking built-in ANT+ hardware,
  * *When* the sensor stack emits `ADAPTER_NOT_DETECTED`,
  * *Then* `MainActivityWithNavigation` presents `AntMissingAdapterDialog` composed in Material 3 with official untinted ANT+ logo, title, and explanatory text,
  * *And* no legacy `android.app.AlertDialog` instance is created.
* **Criterion 2 (Actionable Shortcut to ANT+ Status Sheet)**:
  * *Given* the `AntMissingAdapterDialog`,
  * *When* the athlete taps the primary action button ("View ANT+ Status" / "ANT+ Status anzeigen"),
  * *Then* the dialog is dismissed and `AntServicesStatusSheet` is immediately displayed on screen.
* **Criterion 3 (Material 3 Missing Dependency Dialog)**:
  * *Given* a missing ANT+ system dependency,
  * *When* the sensor stack emits `ANT_DEPENDENCY_MISSING`,
  * *Then* `AntMissingDependencyDialog` is displayed with dependency name, Google Play action button, and cancel button.
* **Criterion 4 (German Typo Correction)**:
  * *Given* `values-de/strings.xml`,
  * *When* inspecting string resource `ant_missing_adapter_message`,
  * *Then* the word "installiert" is spelled correctly, with zero occurrences of "intalliert".
* **Criterion 5 (100% 9-Language Localization Parity)**:
  * *Given* all 9 supported application locales,
  * *When* running translation parity tests,
  * *Then* `ant_dialog_view_status` and `ant_dialog_dismiss` exist, are non-empty, and resolve with zero errors.

---

## 4. Test Specification (`TST-UI-283`)

### 4.1 Unit & Contract Tests (`AntAlertDialogContractTest.kt`)
* **TST-UI-283.1**: Verify `AntMissingAdapterDialog` structural composition:
  * Instantiates `androidx.compose.material3.AlertDialog`.
  * Verifies title matches `R.string.ant_missing_adapter_title`.
  * Verifies text matches `R.string.ant_missing_adapter_message`.
  * Verifies confirm button matches `R.string.ant_dialog_view_status` and invokes `onViewStatus()`.
  * Verifies dismiss button matches `R.string.ant_dialog_dismiss` and invokes `onDismiss()`.
* **TST-UI-283.2**: Verify `AntMissingDependencyDialog` structural composition:
  * Instantiates `androidx.compose.material3.AlertDialog`.
  * Verifies title matches `R.string.ant_missing_dependency_title`.
  * Verifies text contains dependency name.
  * Verifies confirm button matches `R.string.go_to_store` and invokes `onGoToStore()`.
  * Verifies dismiss button matches `R.string.cancel` and invokes `onDismiss()`.
* **TST-UI-283.3**: Verify `MainActivityWithNavigation.kt` elimination of legacy View builders:
  * Inspect source file asserting zero occurrences of `AlertDialog.Builder` within `showANTAdapterMissingDialog` and `showSpecificInstallANTDialog`.
  * Verify state dispatching to `antDialogState` and `showAntStatusSheet`.

### 4.2 Localization Parity Audit (`AntDialogLocalizationTest.kt`)
* **TST-UI-283.4**: Verify 9-language translation parity across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`:
  * `ant_dialog_view_status`: Present and non-empty in all 9 files.
  * `ant_dialog_dismiss`: Present and non-empty in all 9 files.
  * `ant_missing_adapter_title` and `ant_missing_adapter_message`: Present and non-empty in all 9 files.
  * `ant_missing_dependency_title` and `ant_missing_dependency_message`: Present and non-empty in all 9 files.
  * Assert `values-de/strings.xml` contains `installiert` and does NOT contain `intalliert`.

### 4.3 Clean-Room Regression Test Suite
* **TST-UI-283.5**: Full clean-room test execution (`./gradlew testDebugUnitTest`) asserting 100% pass rate with zero regressions.

---

## 5. Traceability Matrix

| Requirement Clause | Test ID | Target Component | Status |
| :--- | :--- | :--- | :---: |
| `REQ-UI-323.1`, `REQ-UI-323.2` | `TST-UI-283.1` | `AntAlertDialogs.kt` (`AntMissingAdapterDialog`) | `Specified` |
| `REQ-UI-323.1`, `REQ-UI-323.2` | `TST-UI-283.2` | `AntAlertDialogs.kt` (`AntMissingDependencyDialog`) | `Specified` |
| `REQ-UI-323.3`, `REQ-UI-323.4`, `REQ-UI-323.5` | `TST-UI-283.3` | `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt` | `Specified` |
| `REQ-UI-323.6` | `TST-UI-283.4` | `values*/strings.xml` (All 9 Locales) | `Specified` |
| `REQ-PRO-001` | `TST-UI-283.5` | Full Clean-Room Test Suite (`testDebugUnitTest`) | `Specified` |
