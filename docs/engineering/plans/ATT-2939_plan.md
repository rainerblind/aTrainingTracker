# Stage 3: Implementation Plan - ATT-2939: Modernize legacy ANT missing adapter and dependency alert dialogs to Material 3 Compose

**Ticket**: [ATT-2939](https://atrainingtracker.atlassian.net/browse/ATT-2939)  
**Sub-task**: [ATT-2976](https://atrainingtracker.atlassian.net/browse/ATT-2976) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Requirement Mapping**: `REQ-UI-323` (*Material 3 Compose Migration for ANT Missing Adapter and Missing Dependency Alerts with Direct ANT+ Status Sheet Integration*)  
**Test Mapping**: `TST-UI-283` (*Material 3 Compose ANT Missing Adapter & Dependency Alerts Verification*)  
**Branch**: `feature/ATT-2939`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

When the ANT radio stack reports missing hardware (`ADAPTER_NOT_DETECTED`) or missing system services (`DEPENDENCY_NOT_INSTALLED`), `MainActivityWithNavigation.kt` catches the broadcasts and displays legacy Android View-based `android.app.AlertDialog.Builder` popups (`showANTAdapterMissingDialog()` and `showSpecificInstallANTDialog()`).

These popups:
1. Clash with the app's modern Material 3 design system.
2. Provide a dead-end "OK" button without linking to the diagnostic `AntServicesStatusSheet.kt` (ATT-2513).
3. Contain a typographical error in German (`values-de/strings.xml`: "intalliert" instead of "installiert").
4. Have primitive boolean tracking that fails to cleanly manage dismissal and prevents subsequent valid re-triggering.

This plan specifies the atomic implementation steps to modernize these dialogs into Material 3 Compose components, wire the primary action to open `AntServicesStatusSheet`, correct the German typo, achieve 100% 9-language translation parity, and eliminate all raw View dialog builders.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-323` (*Material 3 Compose Migration for ANT Missing Adapter and Missing Dependency Alerts with Direct ANT+ Status Sheet Integration*)
* **Test Mapping**: `TST-UI-283` (*Material 3 Compose ANT Missing Adapter & Dependency Alerts Verification*)
  * `TST-UI-283.1`: Contract test verifying `AntMissingAdapterDialog` renders Material 3 AlertDialog with untinted ANT+ logo, title, message, and action callbacks (`onViewStatus`, `onDismiss`).
  * `TST-UI-283.2`: Contract test verifying `AntMissingDependencyDialog` renders Material 3 AlertDialog with untinted ANT+ logo, title, formatted dependency message, and action callbacks (`onGoToStore`, `onDismiss`).
  * `TST-UI-283.3`: Contract test verifying `MainActivityWithNavigation.kt` eliminates raw `AlertDialog.Builder` instances and dispatches state to Compose `antDialogState` and `showAntStatusSheet`.
  * `TST-UI-283.4`: Localization parity audit verifying string keys across all 9 locales and verifying the German typo fix.
  * `TST-UI-283.5`: Full clean-room regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Cold-Start Popups (`REQ-UI-296`)**: App startup in `MainActivityWithNavigation.onCreate()` MUST NOT check ANT installation or pop up dialogs.
2. **Sensor Discovery & Pairing Pipeline**: `BANALService` and `MyANTDevice` sensor discovery and ANT+ dongle communication pipelines MUST remain 100% operational.
3. **Official Untinted Branding (`REQ-UI-309`)**: ANT+ logo (`R.drawable.ant_logo`) MUST render in its original colors without monochrome primary color filter distortion.
4. **Google Play Redirection**: Missing dependency dialog MUST cleanly launch the Play Store market intent (`market://details?id=...`).
5. **No Alert Stacking / Duplication**: An active alert or visible status sheet MUST prevent duplicate concurrent dialogs from appearing.
6. **100% 9-Language Parity**: All newly introduced and updated strings MUST maintain complete parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
7. **Human Decision Gate**: The parent ticket `ATT-2939` terminal state for the AI agent is strictly `Final Review (Human)`.

---

## 4. UI Consistency & Design System Alignment (Rule 23)

* **Reference Screen**: `AntServicesStatusSheet.kt` (ATT-2513 / ATT-2749) and `DeleteConfirmationDialog.kt`.
* **Reused Components**:
  * `androidx.compose.material3.AlertDialog`
  * `safePainterResource(id = R.drawable.ant_logo)`
  * `AntServicesStatusSheet(onDismiss = { ... })`
* **Theme Tokens**:
  * `containerColor = MaterialTheme.colorScheme.surface`
  * `titleContentColor = MaterialTheme.colorScheme.onSurface`
  * `textContentColor = MaterialTheme.colorScheme.onSurfaceVariant`
  * `shape = AlertDialogDefaults.shape` (Material 3 28.dp rounded corners)
  * Action buttons: Primary `Button` / `TextButton` styled with `MaterialTheme.colorScheme.primary`
* **Typography**:
  * Title: `MaterialTheme.typography.headlineSmall`
  * Body: `MaterialTheme.typography.bodyMedium`

---

## 5. Architectural Decomposition (SWE.2)

### 5.1 UI Layer
* **`com.atrainingtracker.trainingtracker.ui.ant.AntDialogState.kt`**:
  ```kotlin
  package com.atrainingtracker.trainingtracker.ui.ant

  sealed class AntDialogState {
      data object MissingAdapter : AntDialogState()
      data class MissingDependency(
          val dependencyName: String,
          val packageName: String
      ) : AntDialogState()
  }
  ```
* **`com.atrainingtracker.trainingtracker.ui.ant.AntAlertDialogs.kt`**:
  * `AntMissingAdapterDialog(onViewStatus: () -> Unit, onDismiss: () -> Unit)`
  * `AntMissingDependencyDialog(dependencyName: String, onGoToStore: () -> Unit, onDismiss: () -> Unit)`
* **`com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerApp.kt`**:
  * Composes `AntMissingAdapterDialog` or `AntMissingDependencyDialog` when `activity.antDialogState != null`.
  * Composes `AntServicesStatusSheet` when `activity.showAntStatusSheet == true`.

### 5.2 Activity & Broadcast Receiver Layer
* **`MainActivityWithNavigation.kt`**:
  * Exposes mutable state:
    ```kotlin
    var antDialogState: AntDialogState? by mutableStateOf(null)
    var showAntStatusSheet: Boolean by mutableStateOf(false)
    ```
  * `mAntAdapterMissingReceiver`: invokes `showANTAdapterMissingDialog()`.
  * `showANTAdapterMissingDialog()`: checks if `antDialogState != null || showAntStatusSheet`; if not, sets `antDialogState = AntDialogState.MissingAdapter`.
  * `mAntDependencyReceiver`: invokes `showSpecificInstallANTDialog()`.
  * `showSpecificInstallANTDialog()`: checks if `antDialogState != null || showAntStatusSheet`; if not, sets `antDialogState = AntDialogState.MissingDependency(...)`.
  * Removes `AlertDialog.Builder` instantiation from `showANTAdapterMissingDialog()` and `showSpecificInstallANTDialog()`.

### 5.3 Resources Layer
* `values-de/strings.xml`: replace `intalliert` with `installiert`.
* `values*/strings.xml`: add `ant_dialog_view_status` and `ant_dialog_dismiss` in all 9 supported locales.

---

## 6. Atomic Implementation Steps

### Step 1: Localization & German Typo Correction
* Correct "intalliert" -> "installiert" in `values-de/strings.xml:953`.
* Add `ant_dialog_view_status` and `ant_dialog_dismiss` across all 9 localized string resource files:
  * EN: "View ANT+ Status", "Got it"
  * DE: "ANT+ Status anzeigen", "Verstanden"
  * ES: "Ver estado de ANT+", "Entendido"
  * FR: "Afficher l'état ANT+", "Compris"
  * IT: "Mostra stato ANT+", "Ho capito"
  * JA: "ANT+ ステータスを表示", "了解"
  * NL: "ANT+ status weergeven", "Begrepen"
  * PL: "Pokaż status ANT+", "Rozumiem"
  * PT: "Ver estado do ANT+", "Entendido"

### Step 2: Implement `AntDialogState.kt` and `AntAlertDialogs.kt`
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/ant/AntDialogState.kt`.
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/ant/AntAlertDialogs.kt` with `AntMissingAdapterDialog` and `AntMissingDependencyDialog` using Material 3 `AlertDialog` and `safePainterResource(id = R.drawable.ant_logo)`.

### Step 3: Modernize `MainActivityWithNavigation.kt`
* Add `antDialogState` and `showAntStatusSheet` state properties.
* Update `showANTAdapterMissingDialog()` to assign `AntDialogState.MissingAdapter` without invoking `AlertDialog.Builder`.
* Update `showSpecificInstallANTDialog()` to assign `AntDialogState.MissingDependency` without invoking `AlertDialog.Builder`.

### Step 4: Wire Dialogs & Sheet into `ATrainingTrackerApp.kt`
* Render `AntMissingAdapterDialog` or `AntMissingDependencyDialog` based on `activity.antDialogState`.
* Wire `onViewStatus` to clear `antDialogState` and set `activity.showAntStatusSheet = true`.
* Render `AntServicesStatusSheet` when `activity.showAntStatusSheet == true`.

### Step 5: Author Unit, Contract & Localization Tests
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/ant/AntAlertDialogContractTest.kt` verifying composables, callback invocations, and absence of legacy `AlertDialog.Builder`.
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/ant/AntDialogLocalizationTest.kt` verifying all 9 locales and the German typo correction.
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.ant.*"
  ```

### Step 6: Full Clean-Room Regression Test Suite
* Run:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Assert 100% pass rate with zero regressions.

---

## 7. Rollback & Migration Strategy

All changes are additive to the Compose UI layer and purely replace legacy `AlertDialog.Builder` invocations in `MainActivityWithNavigation.kt`. If any unexpected behavior occurs, reverting `MainActivityWithNavigation.kt` restores the prior View-based popup immediately. No database schema or sensor protocol changes are involved.
