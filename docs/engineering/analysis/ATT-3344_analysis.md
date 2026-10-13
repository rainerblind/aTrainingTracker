# Stage 1 Analysis: ATT-3344 - Migrate Advanced Settings to full-screen screen and optimize category structure

**Ticket**: [ATT-3344](https://rainerblind.atlassian.net/browse/ATT-3344)  
**Sub-task**: [ATT-3427](https://rainerblind.atlassian.net/browse/ATT-3427) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Branch**: `feature/ATT-3344`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-SET-073` (*Dedicated Advanced Tuning Preferences Screen with Parameter Clamping and Reset-to-Defaults*), `REQ-UI-222` (*Settings/UI: Modular, Collapsible Subsection Accordion Architecture for Advanced Settings*), and `REQ-SET-074` (*Expert Settings Drawer Positioning*), targeting `ui/settings/tuning/AdvancedTuningDialog.kt`, `ui/settings/tuning/AdvancedTuningAccordion.kt`, `ui/navigation/NavRoutes.kt`, and `ui/navigation/ATrainingTrackerApp.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.4 (`ATT-1304`, commit `fca240c1`), Sprint 2026-40.8 (`ATT-1821`), and Sprint 2026-40.9 (`ATT-1957`). Modernized in Sprint 2026-40.10 (`ATT-2033`) into modular composables under `ui.settings.tuning`.
3. **Root Reason for Existing Formulation**:
   - Advanced Tuning was originally embedded inside a Material 3 `ModalBottomSheet` via `SettingsBottomSheetType.ADVANCED_TUNING`. At the time, bottom sheets were being adopted uniformly across settings dialogs (Display, Units, Strava, etc.). However, Advanced Tuning has grown to 25+ parameters across 7 large accordion sections with nested `verticalScroll`. In Material 3 `ModalBottomSheet`, upward flicking or reaching the top of the scroll container dispatches unconsumed drag velocity to the parent bottom sheet, causing the entire settings screen to accidentally collapse and dismiss, discarding unapplied adjustments. Furthermore, Navigation settings were placed at the very bottom (Section 7), separating them from in-ride cockpit settings, while hardware sensors and sensor search were fragmented.
4. **Preservation of Core Invariants**:
   - Invariant 1: All 25+ parameter keys, DataStore persistence schemas (`TuningConfig`, `TuningPreferencesDataStore`), and factory reset mechanics remain 100% intact.
   - Invariant 2: Section composable implementations (`CockpitTypographySection`, `AmoledBatterySaverSection`, `SensorsGpsFilterSection`, `SensorSearchTuningSection`, `WorkoutMasksAndCardsSection`, `AftermathAnalysisSection`, `NavigationSection`) remain intact.
   - Invariant 3: Advisory warning notice (`TuningWarningNotice`) remains visible at the top.
   - Invariant 4: Accordion card styling (`RoundedCornerShape(12.dp)`, `surfaceVariant`, `outlineVariant`, animated chevron rotation, initially collapsed state) remains strictly preserved.
   - Invariant 5: 400-line modularity threshold across all files in `ui.settings.tuning` remains strictly enforced (`AdvancedTuningModularityTest`).
   - Invariant 6: 100% 9-language translation parity maintained across all supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 1. Problem Statement & Motivation

The "Erweiterte Einstellungen" (Advanced Settings / Advanced Tuning) interface provides power users and athletes with granular control over rendering parameters, hardware sensors, battery management, and navigation algorithms. Over several sprints, two severe usability and architectural defects have emerged:

### 1.1 Accidental Screen Dismissal on Scroll (ModalBottomSheet Defect)
1. Currently, Advanced Settings is hosted inside a Material 3 `ModalBottomSheet` via `SettingsBottomSheetType.ADVANCED_TUNING` in `ATrainingTrackerApp.kt`.
2. Inside the sheet, large collapsible accordions contain extensive forms, sliders, and parameter lists with internal `verticalScroll`.
3. In Material 3 nested scrolling, as soon as the user scrolls to the top of the container (or flicks quickly while browsing), unconsumed downward drag motion is dispatched directly to the parent `ModalBottomSheet`. The sheet interprets this as a swipe-to-dismiss gesture and instantly collapses and closes the entire settings screen, discarding unapplied adjustments and causing severe user frustration.

### 1.2 Suboptimal and Counter-Intuitive Categorical Layout
1. Navigation and turn-by-turn routing is buried at the very bottom (Section 7), beneath post-workout history and aftermath graphs, even though navigation heuristics are frequently tweaked alongside in-ride cockpit settings.
2. Hardware and sensor options ("Sensoren, GPS & Filter" vs. "Sensorsuche & Verhalten") are split into disjoint sections.
3. Incoherent functional sequence mixing in-ride styling, telemetry, and post-ride analytics.

---

## 2. Technical Architecture & Root Cause Analysis

### 2.1 Nested Scrolling Defect in ModalBottomSheet
In Material 3 `ModalBottomSheet`, drag gestures propagate via `NestedScrollConnection`:
- When child composables with `Modifier.verticalScroll(rememberScrollState())` receive a downward drag gesture, if `scrollState.value == 0` (or after reaching 0 during momentum fling), the child does not consume `available.y`.
- The unconsumed delta is passed to the parent `ModalBottomSheetState`, which interprets downward drag as a sheet drag-down gesture.
- When velocity or translation exceeds the threshold, the sheet transitions to `SheetValue.Hidden`, calling `onDismiss()` and dismissing the UI.
- Standard settings screens in Android (and in aTrainingTracker, such as `ZoneSettingsScreen` and `ImportBackupTabsScreen`) use dedicated full-screen `Scaffold` navigation destinations, which do not intercept downward scroll gestures for dismissal.

### 2.2 Full-Screen Scaffold Solution (`AdvancedTuningScreen.kt`)
Migrating Advanced Settings to a dedicated full-screen screen:
1. Creates `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt`.
2. Uses Material 3 `Scaffold`:
   - `topBar`: Standard `TopAppBar` with:
     - Navigation back arrow (`Icons.AutoMirrored.Filled.ArrowBack`) calling `onNavigateBack()`.
     - Localized screen title (`R.string.advanced_tuning_title`).
     - Header action icon: Reset to Defaults (`Icons.Default.RestartAlt`, `R.string.reset_to_defaults`).
   - `bottomBar`: Prominent sticky footer action bar with Save button (`AppDialogActions.SaveCancel` or full-width `Button(onClick = onSave)` with `R.string.save`) and Cancel button.
   - `content`: Full-screen scrollable `Column` with `Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())`.
3. Decouples scrolling completely from sheet dismiss gestures, guaranteeing zero accidental dismissals.

### 2.3 Navigation Integration
1. **Route Identifier (`NavRoutes.kt`)**:
   - Add `const val ADVANCED_SETTINGS = "advanced_settings"`.
   - In `NavRoutes.fromDrawerItemId(itemId: Int)`: Map `R.id.drawer_advanced_tuning -> ADVANCED_SETTINGS`.
   - In `NavRoutes.toDrawerItemId(route: String?)`: Map `ADVANCED_SETTINGS -> R.id.drawer_advanced_tuning`.
   - In `NavRoutes.toBottomSheetType(itemId: Int)`: Return `null` for `R.id.drawer_advanced_tuning`.
2. **NavHost Registration (`ATrainingTrackerApp.kt`)**:
   - Register `composable(NavRoutes.ADVANCED_SETTINGS)` in `NavHost`.
   - Provide `AdvancedTuningScreen(onNavigateBack = { navController.popBackStack() })`.
3. **Display Settings Link**:
   - In `DisplaySettingsDialog.kt`, `onNavigateToTuning` callback triggers navigation to `NavRoutes.ADVANCED_SETTINGS` via `navController.navigate(NavRoutes.ADVANCED_SETTINGS)` after closing the display settings sheet.
4. **Backward Compatibility**:
   - Keep `AdvancedTuningDialog.kt` and `AdvancedTuningDialogFragment.kt` marked as `@Deprecated` (or retaining structural contracts for legacy tests) while having all primary user pathways navigate to `NavRoutes.ADVANCED_SETTINGS`.

### 2.4 Lifecycle-Driven Category Reorganization
Restructure categories into a coherent 3-phase athletic lifecycle hierarchy:

#### Phase A: Active In-Ride Experience (Cockpit & Navigation)
* **1. Navigation & Kartenführung (`TuningSection.NAVIGATION`)**:
  - Route selection radius, navigation cue countdown & dismiss duration, Follow-Me dynamic camera, autonomous route auto-switch.
  - Promoted from bottom (Section 7) to top (Section 1).
* **2. Cockpit-Typografie & Sensorfelder (`TuningSection.COCKPIT_TYPOGRAPHY`)**:
  - Font family, font weight, sensor field style, corner radius, border thickness/contrast, and live 3-metric preview.
* **3. AMOLED & Akkuschoner (`TuningSection.BATTERY_SAVER`)**:
  - Full/medium dim factors, flat/steep slope thresholds, wakeup duration, downward delay.

#### Phase B: Hardware & Telemetry (Data Acquisition & Sensors)
* **4. Sensoren & GPS-Filterung (`TuningSection.SENSORS_GPS`)**:
  - GPS accuracy threshold, barometric altitude smoothing window, slope min speed calculation, ANT+ missing adapter warnings.
* **5. Sensorsuche & Verbindungen (`TuningSection.SENSOR_SEARCH`)**:
  - Auto-scan triggers, scan retry count, sport-specific device connection behavior.

#### Phase C: History & Post-Workout (Data Visualization & Summary)
* **6. Workout-Masken & Detailkarten (`TuningSection.WORKOUT_MASKS_CARDS`)**:
  - Summary card visibility, section ordering, workout history detail mask selection.
* **7. Aftermath & Analyse (`TuningSection.AFTERMATH_ANALYSIS`)**:
  - Elevation profile X-axis domain, telemetry chart domains, pace ceiling truncation, elevation smoothing filter.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Create `AdvancedTuningScreen.kt` as a full-screen Compose destination with `Scaffold`, `TopAppBar`, back navigation, reset defaults action, and save action.
  2. Register `NavRoutes.ADVANCED_SETTINGS` and route in `ATrainingTrackerApp.kt`.
  3. Route `R.id.drawer_advanced_tuning` directly to `NavRoutes.ADVANCED_SETTINGS` via `fromDrawerItemId`.
  4. Route `DisplaySettingsDialog` tuning trigger to `NavRoutes.ADVANCED_SETTINGS`.
  5. Reorganize `TuningSection` enum and accordion rendering order to match the lifecycle sequence: Navigation -> Cockpit -> Battery Saver -> Sensors & GPS -> Sensor Search -> Workout Masks -> Aftermath.
  6. Maintain strict modularity: ensure all files in `ui.settings.tuning` remain < 400 lines.
  7. Update automated tests (`AdvancedTuningVisualContractTest`, `AppNavigationDrawerTest`, navigation tests) to verify the new route and order.
* **Out-of-Scope Non-Goals**:
  1. Modifying tuning parameter definitions, DataStore keys, default values, or clamping bounds.
  2. Modifying individual category composable internals (`NavigationSection`, `CockpitTypographySection`, etc.) beyond structural imports or parameter passing.
  3. Modifying other settings sheets (`UnitsSettingsDialog`, `StravaSettingsDialog`).

---

## 4. Architectural Blueprint & Interface Specifications

```
                       ┌─────────────────────────────────────┐
                       │       AppNavigationDrawer.kt        │
                       └──────────────────┬──────────────────┘
                                          │ R.id.drawer_advanced_tuning
                                          ▼
                       ┌─────────────────────────────────────┐
                       │            NavRoutes.kt             │
                       │ fromDrawerItemId -> ADVANCED_SETTINGS│
                       └──────────────────┬──────────────────┘
                                          │ navController.navigate()
                                          ▼
                       ┌─────────────────────────────────────┐
                       │        ATrainingTrackerApp.kt       │
                       │ composable(ADVANCED_SETTINGS)       │
                       └──────────────────┬──────────────────┘
                                          ▼
                       ┌─────────────────────────────────────┐
                       │       AdvancedTuningScreen.kt       │
                       │ - Scaffold                          │
                       │ - TopAppBar (Back, Title, Reset)    │
                       │ - Sticky BottomBar (Save, Cancel)   │
                       │ - Full-height Scrollable Column     │
                       └──────────────────┬──────────────────┘
                                          │
        ┌─────────────────────────────────┴─────────────────────────────────┐
        ▼                                 ▼                                 ▼
┌──────────────────┐             ┌──────────────────┐             ┌──────────────────┐
│Active In-Ride    │             │Hardware & Sensors│             │History & Analysis│
│1. Navigation     │             │4. Sensors & GPS  │             │6. Workout Masks  │
│2. Cockpit Typo   │             │5. Sensor Search  │             │7. Aftermath      │
│3. Battery Saver  │             └──────────────────┘             └──────────────────┘
└──────────────────┘
```

---

## 5. Verification & Safety Strategy

1. **Zero Accidental Dismissals**: Manual and automated tests asserting `AdvancedTuningScreen` is decoupled from `ModalBottomSheetState`.
2. **Back Navigation & State Handling**:
   - Top app bar back button and system back gesture cleanly pop the navigation backstack to the previous screen.
   - Save persists all modified preferences atomically to DataStore and SharedPreferences before popping backstack.
3. **Modularity Invariant**:
   - `AdvancedTuningModularityTest` asserts all files in `ui.settings.tuning` remain strictly < 400 lines.
4. **Contract Invariants**:
   - `AdvancedTuningVisualContractTest` updated to reflect the new category sequence while preserving all parameter checks.
   - Clean-room test suite pass rate: 100% across all unit and contract tests (`./gradlew testDebugUnitTest`).
