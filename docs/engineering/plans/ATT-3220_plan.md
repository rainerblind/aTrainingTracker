# Stage 3 Implementation Plan: ATT-3220 - Persistently suppress missing ANT adapter dialog after dismissal with setting to reset

**Ticket**: [ATT-3220](https://atrainingtracker.atlassian.net/browse/ATT-3220)  
**Sub-task**: [ATT-3287](https://atrainingtracker.atlassian.net/browse/ATT-3287) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*[Epic] Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-UI-353` (*Persistent Missing ANT+ Adapter Dialog Suppression and Advanced Settings Warning Reset*)  
**Test Spec ID**: `TST-UI-313`  
**Branch**: `feature/ATT-3220`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Executive Summary & Architectural Overview

`ATT-3220` eliminates repetitive alert dialog spam caused by `com.atrainingtracker.ADAPTER_NOT_DETECTED` broadcasts on devices lacking native ANT+ hardware. When an athlete acknowledges the warning by tapping "Verstanden", the suppression state is saved to `TuningPreferencesDataStore`. Subsequent broadcasts are dropped, keeping the tracking cockpit and sensor discovery views interruption-free. An explicit switch in Advanced Settings ("Show missing ANT+ adapter warnings") enables athletes who later acquire a USB-OTG ANT+ adapter to restore diagnostic warnings.

### Architectural Boundaries (SWE.2)
- **Settings & Persistence Layer**: `TuningPreferencesDataStore.kt`, `TuningConfig.kt`, `TuningPreferencesDefaults.kt`.
- **Application Shell / Navigation**: `MainActivityWithNavigation.kt` (broadcast receiver guard), `ATrainingTrackerApp.kt` (dismissal persistence).
- **Settings UI**: `SensorsGpsFilterSection.kt`, `AdvancedTuningDialog.kt` (Material 3 toggle switch).
- **Localization**: `res/values-*/strings.xml` across all 9 application locales.

---

## 2. UI Consistency (Rule 23)

In accordance with Rule 23 and `docs/design_guidelines.md` (Section 5):
- **Closest Existing Reference Screen**: Section 3 ("Sensors, GPS & Filtering" / `SensorsGpsFilterSection.kt`) and Section 4 ("Sensor Search & Behavior" / `SensorSearchTuningSection.kt`) within `AdvancedTuningDialog.kt`.
- **Reused Components**:
  - `TuningToggleItem` (`com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControls.kt`).
  - Standard Material 3 `Switch` integrated within the standardized tuning item row.
- **Theme Tokens & Design Specs**:
  - Spacing: `14.dp` vertical arrangement matching existing slider and toggle items in `SensorsGpsFilterSection`.
  - Typography: `MaterialTheme.typography.bodyMedium` for the setting title, `MaterialTheme.typography.bodySmall` for the helper description.
  - Colors: `MaterialTheme.colorScheme.onSurface` for title text, `MaterialTheme.colorScheme.onSurfaceVariant` for description text.
  - Switch colors: Default Material 3 `SwitchDefaults.colors()`.
- **Justification for New Styles**: Zero new or one-off styles. Reuses 100% established tuning components.

---

## 3. Atomic Step Sequencing

### Step 1: DataStore Keys, Defaults & Configuration
- **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDefaults.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
- **Actions**:
  1. In `TuningPreferencesDefaults`: add `const val SUPPRESS_ANT_MISSING_ADAPTER_DIALOG = false`.
  2. In `TuningConfig`: add `val suppressAntMissingAdapterDialog: Boolean = TuningPreferencesDefaults.SUPPRESS_ANT_MISSING_ADAPTER_DIALOG` and helper `val showAntMissingAdapterWarnings: Boolean get() = !suppressAntMissingAdapterDialog`.
  3. In `TuningPreferencesDataStore`:
     - Add `KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG: Preferences.Key<Boolean> = booleanPreferencesKey("tuning_suppress_ant_missing_adapter_dialog")`.
     - Add to `ALL_KEYS`.
     - In `tuningConfigFlow`: map `KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG` with fallback `false`.
     - Add `val suppressAntMissingAdapterDialogFlow: Flow<Boolean>`.
     - Add `suspend fun setSuppressAntMissingAdapterDialog(suppress: Boolean)`.
     - Update `saveTuningConfig()` and `resetToDefaults()`.
- **Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStoreTest"`

### Step 2: Activity Broadcast Reception Guard
- **Target File**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
- **Actions**:
  1. Inject / declare `tuningPreferencesDataStore: TuningPreferencesDataStore by lazy { TuningPreferencesDataStore(applicationContext) }`.
  2. Declare `private var isAntMissingAdapterDialogSuppressed: Boolean = false`.
  3. In `onCreate()`, launch lifecycle-aware coroutine collecting `suppressAntMissingAdapterDialogFlow`:
     ```kotlin
     lifecycleScope.launch {
         repeatOnLifecycle(Lifecycle.State.CREATED) {
             tuningPreferencesDataStore.suppressAntMissingAdapterDialogFlow.collect { suppressed ->
                 isAntMissingAdapterDialogSuppressed = suppressed
             }
         }
     }
     ```
  4. In `showANTAdapterMissingDialog()`:
     ```kotlin
     if (isAntMissingAdapterDialogSuppressed || antDialogState != null || showAntStatusSheet) {
         return
     }
     antDialogState = AntDialogState.MissingAdapter
     ```
- **Verification**: Verify compilation and suppression guard logic.

### Step 3: Dismissal Persistence in Application Shell
- **Target File**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`
- **Actions**:
  1. In `when (val dialogState = activity.antDialogState)` -> `is AntDialogState.MissingAdapter`:
     ```kotlin
     val coroutineScope = rememberCoroutineScope()
     val tuningDataStore = remember { TuningPreferencesDataStore(context) }
     AntMissingAdapterDialog(
         onViewStatus = {
             activity.antDialogState = null
             activity.showAntStatusSheet = true
         },
         onDismiss = {
             activity.antDialogState = null
             coroutineScope.launch {
                 tuningDataStore.setSuppressAntMissingAdapterDialog(true)
             }
         }
     )
     ```
- **Verification**: Verify clean dismissal callback and non-blocking asynchronous persistence.

### Step 4: Advanced Tuning UI Toggle & State Integration
- **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorsGpsFilterSection.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
- **Actions**:
  1. In `SensorsGpsFilterSection.kt`:
     - Add parameters `showAntMissingAdapterWarnings: Boolean` and `onShowAntMissingAdapterWarningsChange: (Boolean) -> Unit`.
     - Render `TuningToggleItem(title = stringResource(R.string.tuning_show_ant_missing_adapter_warnings_title), summary = stringResource(R.string.tuning_show_ant_missing_adapter_warnings_desc), isChecked = showAntMissingAdapterWarnings, onCheckedChange = onShowAntMissingAdapterWarningsChange)`.
  2. In `AdvancedTuningDialog.kt`:
     - Add state variable `var showAntMissingAdapterWarnings by remember { mutableStateOf(!TuningPreferencesDefaults.SUPPRESS_ANT_MISSING_ADAPTER_DIALOG) }`.
     - In `applyConfig()`: `showAntMissingAdapterWarnings = config.showAntMissingAdapterWarnings`.
     - In `onSave`: pass `suppressAntMissingAdapterDialog = !showAntMissingAdapterWarnings` to `TuningConfig`.
     - In `SensorsGpsFilterSection` call: pass `showAntMissingAdapterWarnings` and lambda.
- **Verification**: Verify preview rendering and state round-trip.

### Step 5: 9-Language Localization Parity
- **Target Files**:
  - `app/src/main/res/values/strings.xml` (EN)
  - `app/src/main/res/values-de/strings.xml` (DE)
  - `app/src/main/res/values-es/strings.xml` (ES)
  - `app/src/main/res/values-fr/strings.xml` (FR)
  - `app/src/main/res/values-it/strings.xml` (IT)
  - `app/src/main/res/values-ja/strings.xml` (JA)
  - `app/src/main/res/values-nl/strings.xml` (NL)
  - `app/src/main/res/values-pl/strings.xml` (PL)
  - `app/src/main/res/values-pt/strings.xml` (PT)
- **Keys to Add**:
  - `tuning_show_ant_missing_adapter_warnings_title`:
    - EN: "Show missing ANT+ adapter warnings"
    - DE: "Warnung bei fehlendem ANT+ Adapter"
    - ES: "Mostrar advertencias de falta de adaptador ANT+"
    - FR: "Afficher les avertissements d'adaptateur ANT+ manquant"
    - IT: "Mostra avvisi per adattatore ANT+ mancante"
    - JA: "ANT+アダプター未検出の警告を表示"
    - NL: "Waarschuwingen voor ontbrekende ANT+-adapter tonen"
    - PL: "Pokaż ostrzeżenia o braku adaptera ANT+"
    - PT: "Mostrar avisos de adaptador ANT+ em falta"
  - `tuning_show_ant_missing_adapter_warnings_desc`:
    - EN: "Show alert dialog when an ANT+ sensor is searched but no adapter is detected"
    - DE: "Hinweisdialog anzeigen, wenn nach ANT+ Sensoren gesucht wird, aber kein Adapter erkannt wurde"
    - ES: "Mostrar diálogo de alerta cuando se busca un sensor ANT+ pero no se detecta ningún adaptador"
    - FR: "Afficher une boîte de dialogue d'alerte lorsqu'un capteur ANT+ est recherché mais qu'aucun adaptateur n'est détecté"
    - IT: "Mostra finestra di avviso quando si cerca un sensore ANT+ ma non viene rilevato alcun adattatore"
    - JA: "ANT+センサー検索時にアダプターが検出されない場合、警告ダイアログを表示します"
    - NL: "Waarschuwingsdialoog tonen wanneer naar een ANT+-sensor wordt gezocht maar er geen adapter is gedetecteerd"
    - PL: "Pokaż okno dialogowe ostrzeżenia, gdy wyszukiwany jest czujnik ANT+, ale nie wykryto adaptera"
    - PT: "Mostrar caixa de diálogo de alerta quando um sensor ANT+ é procurado mas nenhum adaptador é detetado"
- **Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

### Step 6: Targeted Unit & Integration Tests
- **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/ant/AntMissingAdapterDialogSuppressionTest.kt`
- **Actions**:
  - Test DataStore persistence, flow emissions, atomic reset.
  - Test dialog suppression guard logic.
  - Execute full suite clean-room regression.
- **Verification**: `./gradlew testDebugUnitTest`

---

## 4. Invariants & Rollback Safety

1. **Clean Installation Experience**: On a fresh install, `suppressAntMissingAdapterDialog` is `false`, ensuring first-time athletes receive the warning.
2. **Missing Dependency Alerts Unaffected**: `AntMissingDependencyDialog` (`com.atrainingtracker.ANT_DEPENDENCY_MISSING`) remains strictly unsuppressed.
3. **No Database Migration Required**: All state is managed within Jetpack DataStore preferences, requiring zero SQLite schema alterations.
4. **Localization Parity**: 100% parity across all 9 application locales.
