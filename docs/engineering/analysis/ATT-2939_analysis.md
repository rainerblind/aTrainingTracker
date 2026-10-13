# Stage 1 Analysis: ATT-2939 - Modernize legacy ANT missing adapter and dependency alert dialogs to Material 3 Compose

**Ticket**: [ATT-2939](https://atrainingtracker.atlassian.net/browse/ATT-2939)  
**Sub-task**: [ATT-2974](https://atrainingtracker.atlassian.net/browse/ATT-2974) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2939`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During sensor discovery and ANT+ device initialization on devices lacking built-in ANT hardware (such as modern Google Pixel, Samsung Galaxy S series, or emulators), the ANT radio stack broadcasts failure intents (`ADAPTER_NOT_DETECTED` and `ANT_DEPENDENCY_MISSING`). When received by `MainActivityWithNavigation.kt`, the application presents legacy Android View-based `android.app.AlertDialog` popups:
* `showANTAdapterMissingDialog()` (lines 259–277)
* `showSpecificInstallANTDialog()` (lines 234–257)

These dialogs exhibit four significant defects:
1. **Antiquated Legacy UI & Visual Disharmony**: They instantiate raw `AlertDialog.Builder(this)` widgets using default platform styles from Android 5/6 eras, clashing visually with the app's modern Material 3 design system, typography, and container tokens.
2. **Dead-End User Experience (Lack of Actionable Guidance)**: `showANTAdapterMissingDialog` only offers an "OK" dismiss button. Athletes on modern phones are left stranded without actionable direction, despite the presence of the newly modernized ANT+ Status Sheet (`AntServicesStatusSheet.kt`, introduced in ATT-2513) which provides comprehensive guidance on USB-OTG and ANT+ USB dongle prerequisites.
3. **Localization Spelling Defect**: In German (`values-de/strings.xml:953`), `ant_missing_adapter_message` contains a spelling error ("intalliert" instead of "installiert").
4. **Repetitive Popups During Background Scanning**: Repeated sensor scan cycles can trigger duplicate broadcasts, re-popping dialogs if the user dismissed them, creating intrusive modal interruptions during workout tracking and sensor pairing.

### Current State vs. Expected Behavior
* **Current State**:
  * Broadcasts trigger raw `android.app.AlertDialog` popups.
  * Missing adapter dialog only offers an "OK" button without linking to `AntServicesStatusSheet`.
  * German string has typo "intalliert".
  * Dialog presentation state tracking is rudimentary (`Boolean` flags that never reset upon dismissal or cleanly manage session lifecycle).
* **Expected Behavior**:
  * Broadcasts trigger clean Material 3 Compose dialogs (`AntMissingAdapterDialog` and `AntMissingDependencyDialog`) adhering to MaterialTheme tokens, rounded corners, and official untinted ANT+ branding (`R.drawable.ant_logo`).
  * Missing adapter dialog offers a primary action button ("ANT+ Status anzeigen" / "View ANT+ Status") that immediately launches `AntServicesStatusSheet`, alongside a clean dismiss action ("Verstanden" / "Got it").
  * Missing dependency dialog offers "Go to Store" (`R.string.go_to_store`) linking to Google Play and "Cancel" (`R.string.cancel`).
  * German typo "intalliert" is corrected to "installiert" and 100% translation parity is enforced across all 9 supported application locales.
  * State management ensures a single non-repetitive dialog presentation per scanning session.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Broadcast Emission in Sensor Stack (`MyANTDevice.java:400, 410`)
In `MyANTDevice.java`:
```java
case DEPENDENCY_NOT_INSTALLED:
    sendBroadcast("com.atrainingtracker.ANT_DEPENDENCY_MISSING");
    break;
case ADAPTER_NOT_DETECTED:
    sendBroadcast("com.atrainingtracker.ADAPTER_NOT_DETECTED");
    break;
```
When `BANALService` attempts to bind to ANT+ devices or when the athlete searches for ANT+ sensors, `MyANTDevice` receives callbacks from the ANT plugin library. If the phone has no native ANT+ radio, `ADAPTER_NOT_DETECTED` is emitted. If the ANT Radio Service or ANT Plugins Service is missing, `DEPENDENCY_NOT_INSTALLED` is emitted.

### 2.2 Legacy Alert Construction (`MainActivityWithNavigation.kt:234-277`)
In `MainActivityWithNavigation.kt`:
```kotlin
    private var showingSpecificInstallANTDialog: Boolean = false
    fun showSpecificInstallANTDialog() {
        if (showingSpecificInstallANTDialog) {
            return
        } else {
            showingSpecificInstallANTDialog = true
        }

        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle(R.string.ant_missing_dependency_title)
        alertDialogBuilder.setMessage(getString(R.string.ant_missing_dependency_message, AntPluginPcc.getMissingDependencyName()))
        alertDialogBuilder.setCancelable(true)
        alertDialogBuilder.setPositiveButton(R.string.go_to_store) { _, _ ->
            val startStore = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + AntPluginPcc.getMissingDependencyPackageName()))
            startStore.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(startStore)
        }
        alertDialogBuilder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.dismiss()
        }

        val waitDialog = alertDialogBuilder.create()
        waitDialog.show()
    }

    private var isShowingANTAdapterMissingDialog: Boolean = false
    fun showANTAdapterMissingDialog() {
        if (isShowingANTAdapterMissingDialog) {
            return
        } else {
            isShowingANTAdapterMissingDialog = true
        }

        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle(R.string.ant_missing_adapter_title)
        alertDialogBuilder.setMessage(R.string.ant_missing_adapter_message)
        alertDialogBuilder.setCancelable(true)
        alertDialogBuilder.setNeutralButton(R.string.OK) { dialog, _ ->
            dialog.dismiss()
        }

        val waitDialog = alertDialogBuilder.create()
        waitDialog.show()
    }
```
* **Legacy View System**: Uses Android Framework `AlertDialog.Builder(this)` rather than Material 3 Jetpack Compose.
* **Dead End**: Line 271 sets `alertDialogBuilder.setNeutralButton(R.string.OK)`. It simply dismisses without giving the athlete any pathway to inspect hardware or service requirements.
* **Primitive Flag State**: The booleans `showingSpecificInstallANTDialog` and `isShowingANTAdapterMissingDialog` prevent concurrent dialogs, but because they are never reset when the user dismisses via back button or tap outside, subsequent deliberate user actions cannot re-trigger status checks.

### 2.3 German Typographical Error (`values-de/strings.xml:953`)
Line 953 in `values-de/strings.xml`:
```xml
<string name="ant_missing_adapter_message">Das ANT Interface meldet uns einen fehlenden ANT Adapter zurück.  Vermutlich hat dein Smartphone keine direkte ANT+ Unterstützung.  Du kannst dennoch ANT+ Sensoren koppeln, wenn Du dir einen USB ANT Dongle besorgst, den Du über einen OTG Adapter mit dem Smartphone verbindest (und dann auch noch den ANT USB Service intalliert hast).</string>
```
The token "intalliert" is missing the 's'.

---

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**: Amends `REQ-UI-296` (*Modernized Contextual ANT+ System Service Guidance & Cold-Start Dialog Elimination*, ATT-2513) Clause 1 & 4, and complements `REQ-UI-309` (*Official Untinted ANT+ Logo Rendering and Bluetooth LE Advisory Note Elimination in ANT+ System Services Sheet*, ATT-2749).
2. **Historical Origin & Commit Trace**: Legacy View dialogs authored in 2017 (`05.01.17`, commit `MainActivityWithNavigation.java`), ported to Kotlin in ATT-657 (Sprint 2026-40.1).
3. **Root Reason for Existing Formulation**: In 2017, Android applications relied on standard framework `AlertDialog.Builder`. In ATT-2513, the cold-start check (`checkANTInstallation()`) was eliminated, and `AntServicesStatusSheet.kt` was created for contextual guidance in sensor tabs. However, the runtime broadcast receivers in `MainActivityWithNavigation.kt` for scan failure events were not refactored into Compose.
4. **Preservation of Core Invariants**:
   * Sensor discovery and connection management in `BANALService` and `MyANTDevice.java` remain 100% untouched.
   * Google Play store redirection for missing packages via `market://details?id=...` remains intact.
   * Single-instance dialog guard per scan/dismissal cycle must be preserved to prevent notification spam.
   * 100% test pass rate across the full test suite must be preserved.

---

## 4. Proposed Technical Solution & Architecture

### 4.1 Dialog State Model (`AntDialogState.kt`)
Introduce a clean state model representing active ANT alert dialogs:
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

### 4.2 Material 3 Compose Dialogs (`AntAlertDialogs.kt`)
Create dedicated, reusable Material 3 composables:
1. **`AntMissingAdapterDialog`**:
   * Uses `androidx.compose.material3.AlertDialog`.
   * `icon`: `Image` rendering `safePainterResource(id = R.drawable.ant_logo)` with size 36.dp.
   * `title`: `Text(stringResource(R.string.ant_missing_adapter_title))`.
   * `text`: `Text(stringResource(R.string.ant_missing_adapter_message))`.
   * `confirmButton`: `Button` or `TextButton` labeled `stringResource(R.string.ant_dialog_view_status)` ("ANT+ Status anzeigen" / "View ANT+ Status"). Invoking this triggers `onViewStatus()`.
   * `dismissButton`: `TextButton` labeled `stringResource(R.string.ant_dialog_dismiss)` ("Verstanden" / "Got it"). Invoking this triggers `onDismiss()`.
2. **`AntMissingDependencyDialog`**:
   * Uses `androidx.compose.material3.AlertDialog`.
   * `icon`: `Image` rendering `safePainterResource(id = R.drawable.ant_logo)`.
   * `title`: `Text(stringResource(R.string.ant_missing_dependency_title))`.
   * `text`: `Text(stringResource(R.string.ant_missing_dependency_message, dependencyName))`.
   * `confirmButton`: `TextButton` labeled `stringResource(R.string.go_to_store)`.
   * `dismissButton`: `TextButton` labeled `stringResource(R.string.cancel)`.

### 4.3 Activity & App Composition Integration
In `MainActivityWithNavigation.kt`:
* Expose:
  ```kotlin
  var antDialogState: AntDialogState? by mutableStateOf(null)
  var showAntStatusSheet: Boolean by mutableStateOf(false)
  ```
* In `showANTAdapterMissingDialog()`:
  * If `antDialogState != null` or `showAntStatusSheet`, return.
  * Set `antDialogState = AntDialogState.MissingAdapter`.
* In `showSpecificInstallANTDialog()`:
  * If `antDialogState != null` or `showAntStatusSheet`, return.
  * Set `antDialogState = AntDialogState.MissingDependency(name = AntPluginPcc.getMissingDependencyName() ?: "", packageName = AntPluginPcc.getMissingDependencyPackageName() ?: "")`.

In `ATrainingTrackerApp.kt`:
* Compose the dialogs based on `activity.antDialogState`:
  ```kotlin
  when (val dialogState = activity.antDialogState) {
      is AntDialogState.MissingAdapter -> {
          AntMissingAdapterDialog(
              onViewStatus = {
                  activity.antDialogState = null
                  activity.showAntStatusSheet = true
              },
              onDismiss = {
                  activity.antDialogState = null
              }
          )
      }
      is AntDialogState.MissingDependency -> {
          AntMissingDependencyDialog(
              dependencyName = dialogState.dependencyName,
              onGoToStore = {
                  activity.antDialogState = null
                  val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${dialogState.packageName}")).apply {
                      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  }
                  activity.startActivity(intent)
              },
              onDismiss = {
                  activity.antDialogState = null
              }
          )
      }
      null -> Unit
  }

  if (activity.showAntStatusSheet) {
      AntServicesStatusSheet(
          onDismiss = { activity.showAntStatusSheet = false }
      )
  }
  ```

### 4.4 Localization & Typo Correction
1. Correct `values-de/strings.xml:953`:
   * Change "intalliert" to "installiert".
2. Add new string keys across all 9 locales:
   * `ant_dialog_view_status`:
     * EN: "View ANT+ Status"
     * DE: "ANT+ Status anzeigen"
     * ES: "Ver estado de ANT+"
     * FR: "Afficher l'état ANT+"
     * IT: "Mostra stato ANT+"
     * JA: "ANT+ ステータスを表示"
     * NL: "ANT+ status weergeven"
     * PL: "Pokaż status ANT+"
     * PT: "Ver estado do ANT+"
   * `ant_dialog_dismiss`:
     * EN: "Got it"
     * DE: "Verstanden"
     * ES: "Entendido"
     * FR: "Compris"
     * IT: "Ho capito"
     * JA: "了解"
     * NL: "Begrepen"
     * PL: "Rozumiem"
     * PT: "Entendido"

---

## 5. User Scope Grounding (`ATT-1250`)

### In-Scope
* Creating `AntDialogState.kt` and `AntAlertDialogs.kt` with Material 3 Compose `AlertDialog` components.
* Migrating `showANTAdapterMissingDialog()` and `showSpecificInstallANTDialog()` in `MainActivityWithNavigation.kt` to drive Compose dialog state.
* Connecting the missing adapter primary action to display `AntServicesStatusSheet`.
* Correcting the German spelling error ("intalliert" -> "installiert") in `values-de/strings.xml`.
* Establishing 100% 9-language parity for new button labels.
* Writing unit and contract tests verifying dialog composition, state transitions, and 9-language localization parity.

### Out-of-Scope
* Modifying ANT protocol connection or device driver logic in `MyANTDevice.java` or `BANALService.java`.
* Changing `AntServicesStatusSheet.kt` internals or its service detection mechanism.
* Altering Bluetooth LE pairing or scanning logic.

---

## 6. Verification & Test Strategy

1. **`AntAlertDialogContractTest.kt`**:
   * Unit tests verifying `AntMissingAdapterDialog` and `AntMissingDependencyDialog` render expected Material 3 dialog components, titles, messages, and action buttons.
   * Verify callback invocations (`onViewStatus`, `onGoToStore`, `onDismiss`).
   * Verify `MainActivityWithNavigation` broadcast receiver handlers update Compose state and launch `AntServicesStatusSheet` without instantiating `android.app.AlertDialog`.
2. **`AntDialogLocalizationTest.kt`**:
   * Audit all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) verifying:
     * `ant_missing_adapter_title`, `ant_missing_adapter_message`, `ant_missing_dependency_title`, `ant_missing_dependency_message` are non-empty.
     * `ant_dialog_view_status` and `ant_dialog_dismiss` exist and are translated in all 9 locales.
     * `values-de/strings.xml` contains "installiert" and zero occurrences of "intalliert".
3. **Full Clean-Room Regression**:
   * Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

---

## 7. Invariants Enforced

* **Zero Raw Android View Dialogs for ANT alerts**: No calls to `AlertDialog.Builder` remain for ANT adapter/dependency alerts.
* **Non-Blocking User Experience**: Athlete can dismiss easily with "Verstanden" / "Got it", or inspect status via `AntServicesStatusSheet`.
* **State Machine Invariant**: Tapping "View ANT+ Status" immediately clears the dialog state and activates `showAntStatusSheet = true`, avoiding overlapping modals.
* **Full Localization Parity**: 100% translation coverage across EN, DE, ES, FR, IT, JA, NL, PL, PT.
