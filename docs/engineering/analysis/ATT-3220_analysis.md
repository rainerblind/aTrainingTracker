# Stage 1 Analysis: ATT-3220 - Persistently suppress missing ANT adapter dialog after dismissal with setting to reset

**Ticket**: [ATT-3220](https://atrainingtracker.atlassian.net/browse/ATT-3220)  
**Sub-task**: [ATT-3285](https://atrainingtracker.atlassian.net/browse/ATT-3285) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*[Epic] Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Branch**: `feature/ATT-3220`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation

On Android devices lacking native ANT+ hardware (such as Google Pixel, modern Samsung Galaxy S/Z series, and Motorola devices), sensor scanning cycles repeatedly emit the `com.atrainingtracker.ADAPTER_NOT_DETECTED` broadcast whenever the sensor subsystem attempts to scan for or connect to ANT+ peripherals.

In sprint `2026-41.6` (`ATT-2939`, `REQ-UI-323`), the missing adapter alert was modernized into a Material 3 Compose dialog (`AntMissingAdapterDialog.kt`) managed by `activity.antDialogState = AntDialogState.MissingAdapter` in `MainActivityWithNavigation.kt` and `ATrainingTrackerApp.kt`. However:
1. **Transient In-Memory State**:
   Tapping "Verstanden" ("Got it") on `AntMissingAdapterDialog` only resets `activity.antDialogState = null` in memory.
2. **Repetitive Alert Spams**:
   Because dismissal is not persisted to disk, any subsequent sensor search trigger (such as auto-search on app start, resuming from pause, sport change, pull-to-refresh on sensors tab, or background BLE/ANT scan loops) receives another `ADAPTER_NOT_DETECTED` broadcast. Since `antDialogState` is null and `showAntStatusSheet` is false, `MainActivityWithNavigation.showANTAdapterMissingDialog()` pops up the dialog again.
3. **Athlete Disruption**:
   Athletes who consciously use only Bluetooth LE sensors or know their phone lacks built-in ANT+ hardware are repeatedly harassed by the same warning dialog across sessions and tab switches.

To deliver a polished user experience, the athlete's dismissal must be persisted into `TuningPreferencesDataStore`. Once dismissed, future `ADAPTER_NOT_DETECTED` broadcasts must be discarded. Furthermore, an explicit preference switch must be provided in Advanced Tuning ("Show missing ANT+ adapter warnings") allowing athletes who later attach a physical USB-OTG ANT+ stick to re-enable diagnostic warnings.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Broadcast Generation (`MyANTDevice.java`)
In `com.atrainingtracker.banalservice.devices.ant_plus.MyANTDevice.java:409-410`:
```java
case ADAPTER_NOT_DETECTED:
    sendBroadcast("com.atrainingtracker.ADAPTER_NOT_DETECTED");
```
Whenever an ANT+ channel search is initiated without an ANT+ chip or USB-OTG service adapter detected, `MyANTDevice` broadcasts `com.atrainingtracker.ADAPTER_NOT_DETECTED`.

### 2.2 Reception & Presentation in `MainActivityWithNavigation.kt`
In `MainActivityWithNavigation.kt:246-272`:
```kotlin
private val mAntAdapterMissingReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        showANTAdapterMissingDialog()
    }
}

fun showANTAdapterMissingDialog() {
    if (antDialogState != null || showAntStatusSheet) {
        return
    }
    antDialogState = AntDialogState.MissingAdapter
}
```
The guard checks only whether the dialog is *currently visible* on screen (`antDialogState != null`) or the bottom sheet is open (`showAntStatusSheet`). It has no awareness of prior user dismissals.

### 2.3 Dismissal Callback in `ATrainingTrackerApp.kt`
In `ATrainingTrackerApp.kt:501-512`:
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
    ...
```
Tapping "Verstanden" (`onDismiss`) simply sets `activity.antDialogState = null`. No preference is stored. On the very next broadcast, `showANTAdapterMissingDialog()` re-triggers the dialog.

---

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

### 3.1 Existing Requirement: `REQ-UI-323`
- **Requirement Title**: *Material 3 Compose Migration for ANT Missing Adapter and Missing Dependency Alerts with Direct ANT+ Status Sheet Integration.*
- **Historical Commit Trace**: Commit `05cb0ba4` (`ATT-2939`, Sprint 2026-41.6).
- **Existing Clause 5**:
  *"In MainActivityWithNavigation.kt, incoming failure broadcasts when an alert dialog or AntServicesStatusSheet is already visible SHALL NOT duplicate or stack alerts."*
- **Root Reason for Existing Formulation**:
  The focus of `ATT-2939` was eliminating raw Android `AlertDialog.Builder` popups and fixing a German typo ("intalliert" $\rightarrow$ "installiert"). It prevented simultaneous duplicate dialogs while one was open, but did not address cross-session or post-dismissal suppression.

### 3.2 Invariants to Preserve
1. **First-Time Discovery**: Athletes without ANT+ hardware must still see the dialog on their initial encounter so they understand why ANT+ sensors cannot be found.
2. **Actionable Status Inspection**: The primary button "ANT+ Status anzeigen" must continue to open `AntServicesStatusSheet.kt` to educate athletes on USB-OTG requirements.
3. **Missing Dependency Alerts Intact**: `AntMissingDependencyDialog` (`com.atrainingtracker.ANT_DEPENDENCY_MISSING`) is NOT suppressed by this flag, as missing software services (ANT+ Radio Service, ANT+ Plugins) are installable from Google Play.
4. **Re-Enablement Guarantee**: Athletes must always be able to re-enable warnings via Advanced Tuning if they purchase an ANT+ USB dongle or reset settings to defaults.

---

## 4. Scope Bounding (`ATT-1250`)

### 4.1 In-Scope Objectives
1. **Persistent DataStore Preference**:
   - Add `KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG` in `TuningPreferencesDataStore.kt` (boolean, defaults to `false` on clean install).
   - Expose `suppressAntMissingAdapterDialogFlow: Flow<Boolean>` and helper mutator `setSuppressAntMissingAdapterDialog(suppress: Boolean)`.
   - Include in `TuningConfig` and `resetToDefaults()`.
2. **Broadcast Guard in `MainActivityWithNavigation.kt`**:
   - Maintain cached suppression state from `TuningPreferencesDataStore`.
   - Discard `ADAPTER_NOT_DETECTED` broadcasts when suppressed.
3. **Dismissal Persistence in `ATrainingTrackerApp.kt`**:
   - On `onDismiss` of `AntMissingAdapterDialog`, asynchronously persist `suppressAntMissingAdapterDialog = true`.
4. **Advanced Tuning Toggle**:
   - Add "Show missing ANT+ adapter warnings" toggle in `SensorsGpsFilterSection.kt` (checked when `!suppressAntMissingAdapterDialog`).
   - Wire into `AdvancedTuningDialog.kt` state and saving pipeline.
5. **Localization Parity**:
   - Add title and description strings across all 9 supported locales: `values/` (EN), `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
6. **Unit Tests**:
   - DataStore preference serialization and default reset tests.
   - Activity suppression guard unit test.
   - Translation parity test asserting 100% coverage.

### 4.2 Out-of-Scope Items
- Altering `MyANTDevice.java` low-level broadcast logic.
- Suppressing `AntMissingDependencyDialog` (dependencies can be installed from Play Store).
- Redesigning `AntServicesStatusSheet.kt`.

---

## 5. Proposed Architectural Solution

```
[ MyANTDevice ] --(Broadcast: ADAPTER_NOT_DETECTED)--> [ MainActivityWithNavigation ]
                                                                 |
                                              Check: isSuppressed || dialogVisible?
                                                                 |
                                            +--------------------+--------------------+
                                            | (True: Discard)                         | (False)
                                            v                                         v
                                         [ Drop ]                     [ Set AntDialogState.MissingAdapter ]
                                                                                      |
                                                                                      v
                                                                          [ AntMissingAdapterDialog ]
                                                                                      |
                                                             +------------------------+------------------------+
                                                             | "Verstanden" (Dismiss)                          | "ANT+ Status anzeigen"
                                                             v                                                 v
                                             [ Set suppressAnt = true ]                           [ Open AntServicesStatusSheet ]
                                             [ Save to DataStore      ]
```

### 5.1 DataStore & Config Updates
- **`TuningPreferencesDefaults.kt`**:
  `const val SUPPRESS_ANT_MISSING_ADAPTER_DIALOG = false`
- **`TuningConfig.kt`**:
  `val suppressAntMissingAdapterDialog: Boolean = TuningPreferencesDefaults.SUPPRESS_ANT_MISSING_ADAPTER_DIALOG`
  `val showAntMissingAdapterWarnings: Boolean get() = !suppressAntMissingAdapterDialog`
- **`TuningPreferencesDataStore.kt`**:
  `KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG = booleanPreferencesKey("tuning_suppress_ant_missing_adapter_dialog")`
  `suspend fun setSuppressAntMissingAdapterDialog(suppress: Boolean)`

### 5.2 Settings UI (`SensorsGpsFilterSection.kt`)
```kotlin
TuningToggleItem(
    title = stringResource(R.string.tuning_show_ant_missing_adapter_warnings_title),
    summary = stringResource(R.string.tuning_show_ant_missing_adapter_warnings_desc),
    isChecked = showAntMissingAdapterWarnings,
    onCheckedChange = onShowAntMissingAdapterWarningsChange
)
```

---

## 6. Verification & Test Strategy

1. **Unit Tests**:
   - `TuningPreferencesDataStoreTest`: Verify preference reading, writing, and atomic reset.
   - `AntMissingAdapterDialogSuppressionTest`: Verify suppression guard behavior on `ADAPTER_NOT_DETECTED` broadcast reception.
   - `TranslationParityTest`: Verify 9-language parity for new string resources.
2. **Clean-Room Regression**:
   - Run full `./gradlew testDebugUnitTest` verifying 100% pass rate.
