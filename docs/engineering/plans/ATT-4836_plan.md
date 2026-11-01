# Stage 3: Implementation Plan - ATT-4836: Own section for auto pause in settings

**Ticket**: [ATT-4836](https://rainerblind.atlassian.net/browse/ATT-4836)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Active Sprint**: `2026-44.1`  
**Target Release**: `V4.9.41`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Architectural Design & Layer Boundaries (SWE.2)

```
[Navigation Drawer (AppNavigationDrawer.kt / NavRoutes.kt)]
           │ (NavRoutes.AUTO_PAUSE)
           ▼
[AutoPauseTabsScreen (Material 3 Tabbed UI)]
           │ (events & state)
           ▼
[AutoPauseViewModel (StateFlow<TuningConfig>)]
           │ (suspend update/reset)
           ▼
[TuningPreferencesDataStore (Jetpack DataStore)]
           │ (cache invalidation)
           ▼
[TrackerService & AutoPauseManager (1-Hz evaluation)]
```

### Layer Responsibilities
- **UI (`AutoPauseTabsScreen.kt`)**: Renders collapsing TopAppBar with drawer hamburger and reset action; manages 3 tabs (*Cycling*, *Running*, *Other Sports*) with `HorizontalPager`; uses `TuningToggleItem` and `TuningSliderItem` for design system consistency.
- **ViewModel (`AutoPauseViewModel.kt`)**: Injects `TuningPreferencesDataStore`; exposes reactive `tuningConfig: StateFlow<TuningConfig>`; dispatches debounced updates on `viewModelScope`.
- **Repository (`TuningPreferencesDataStore.kt`)**: Persists settings immediately; invalidates `TrackerService` auto-pause cache on mutation; adds `resetAutoPausePreferences(sportType)`.
- **Legacy Retirement**: Cleans `SensorsGpsFilterSection.kt`, `AdvancedTuningScreen.kt`, and `AdvancedTuningDialog.kt` to enforce single canonical surface (Design Guidelines 3.2).

---

## 2. UI Consistency (Design Guidelines Section 5)

- **Reference Screen**: `SportTypesTabsScreen.kt` and `EquipmentTabsScreen.kt` (collapsing header, tabs, standard padding `16.dp`).
- **Reused Components**: `TuningToggleItem`, `TuningSliderItem` from `ui.settings.tuning.TuningFormControls.kt`.
- **Theme Tokens**: `MaterialTheme.colorScheme.primary`, `surface`, `onSurface`, and `TTColor` constants.

---

## 3. Atomic Implementation Steps

1. **Step 1: XML Resources & Localization**:
   - Add `drawer_auto_pause` ID in `res/values/ids.xml`.
   - Add `ic_pause_circle.xml` in `res/drawable/` (24dp vector).
   - Add `auto_pause_title` in all 9 `strings.xml` files.
2. **Step 2: Navigation Integration**:
   - Add `NavRoutes.AUTO_PAUSE` and bidirectional mappings in `NavRoutes.kt`.
   - Add `DrawerItemConfig` in `AppNavigationDrawer.kt` below `drawer_sport_types`.
   - Register route in `ATrainingTrackerApp.kt`.
3. **Step 3: DataStore Helper Extension**:
   - Add `resetAutoPausePreferences(sportType: BSportType?)` in `TuningPreferencesDataStore.kt`.
4. **Step 4: Screen & ViewModel Construction**:
   - Implement `AutoPauseViewModel.kt` in `ui.settings.autopause`.
   - Implement `AutoPauseTabsScreen.kt` in `ui.settings.autopause`.
5. **Step 5: Legacy Auto-Pause Retirement**:
   - Excise `autoPause*` parameters and UI from `SensorsGpsFilterSection.kt`, `AdvancedTuningScreen.kt`, and `AdvancedTuningDialog.kt`.
6. **Step 6: Unit & Contract Tests**:
   - Author `AutoPauseNavigationTest.kt` (`TST-SET-080.1`).
   - Author `AutoPauseViewModelTest.kt` (`TST-SET-080.2`).
   - Author `AutoPauseRetirementContractTest.kt` (`TST-SET-080.3`).

---

## 4. Invariants & Verification Checklist

- **Backward Compatibility**: DataStore keys (`KEY_AUTO_PAUSE_*`) remain untouched.
- **Rule 4 Code Lock**: `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>` must exit 0 before Step 1.
- **Translation Parity**: `TranslationParityTest` and `./gradlew processDebugResources` pass.
