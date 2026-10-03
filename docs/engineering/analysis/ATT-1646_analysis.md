# Stage 1 Analysis: ATT-1646 - Advanced Tuning Drawer Positioning, Naming/Localization, and Default GPS Tolerance

**Ticket**: [ATT-1646](https://atrainingtracker.atlassian.net/browse/ATT-1646)  
**Sub-task**: [ATT-1669](https://atrainingtracker.atlassian.net/browse/ATT-1669) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1646`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Statement & Motivation

During user review of Sprint `2026-40.4` following the introduction of Advanced Tuning Preferences ([ATT-1304](https://atrainingtracker.atlassian.net/browse/ATT-1304)), the athlete requested three critical usability and naming refinements:
1. **Drawer Placement & Dedicated Section**:
   - The Advanced Tuning navigation item is currently mixed inside the general `drawer__settings` category.
   - Because tuning contains sensitive heuristics and algorithmic parameters that can affect tracking fidelity, it must be sequestered in its own distinct section/group at the very bottom of the navigation drawer, separated from standard day-to-day settings.
2. **Naming & Localization Alignment**:
   - The German terminology *"Erweiterte Abstimmung"* feels unnatural and awkward. The user requested renaming it to *"Experten-Einstellungen"* (or *"Detail-Einstellungen"*).
   - Correspondingly, translations across all 9 application locales require alignment to an "Expert Settings" or "Advanced Configuration" naming paradigm (e.g. *"Expert Settings"*, *"Ajustes de experto"*, *"Paramètres d'expert"*).
3. **Default GPS Tolerance**:
   - The factory default GPS accuracy tolerance is currently too permissive (inherited from legacy 200m).
   - Athletes require a tighter default GPS accuracy threshold of 50 m out-of-the-box.

---

## 2. Root Cause Analysis (Forensic Investigation)

### A. Navigation Drawer Structure
In `AppNavigationDrawer.kt` (lines 191–201):
```kotlin
DrawerGroup(
    titleRes = R.string.drawer__settings,
    items = listOf(
        DrawerItemConfig(R.id.drawer_units, R.drawable.ic_square_foot, R.string.prefsUnitsTitle),
        DrawerItemConfig(R.id.drawer_display_settings, R.drawable.ic_display_settings, R.string.Display),
        DrawerItemConfig(R.id.drawer_advanced_tuning, R.drawable.ic_tune, R.string.advanced_tuning_title),
        DrawerItemConfig(R.id.drawer_tracking_layouts, R.drawable.ic_table_edit, R.string.prefsConfigureDisplaysTitle),
        DrawerItemConfig(R.id.drawer_search_settings, R.drawable.ic_search, R.string.Search_Settings),
        DrawerItemConfig(R.id.drawer_backup_restore, R.drawable.ic_save_to_disc, R.string.import_backup),
        DrawerItemConfig(R.id.drawer_privacy_policy, R.drawable.ic_privacy, R.string.privacy_policy)
    )
)
```
- `drawer_advanced_tuning` sits at index 2 between `drawer_display_settings` and `drawer_tracking_layouts`.
- Mixing expert tuning with standard settings creates cognitive clutter and risks unintended adjustments by casual athletes.
- A dedicated 6th group at the bottom (`drawer__expert_settings`) provides clean visual isolation with an automatic divider separator.

### B. Localization Audit
In `strings.xml` across locales:
- `advanced_tuning_title` is currently translated as:
  - DE: `Erweiterte Abstimmung` -> Should be `Experten-Einstellungen`
  - EN: `Advanced Tuning` -> Should be `Expert Settings`
  - ES: `Ajuste avanzado` -> Should be `Ajustes de experto`
  - FR: `Réglage avancé` -> Should be `Paramètres d'expert`
  - IT: `Regolazione avanzata` -> Should be `Impostazioni per esperti`
  - JA: `詳細チューニング` -> Should be `エキスパート設定`
  - NL: `Geavanceerde afstemming` -> Should be `Expertinstellingen`
  - PL: `Zaawansowane dostrajanie` -> Should be `Ustawienia eksperckie`
  - PT: `Ajuste avançado` -> Should be `Configurações de especialista`
- Corresponding updates apply to `advanced_tuning_open` (the dialog trigger button in `DisplaySettingsDialog.kt`).
- The new drawer group header `drawer__expert_settings` must be added across all 9 locales.

### C. Factory Default GPS Accuracy
In `TuningPreferencesDataStore.kt`:
```kotlin
object TuningPreferencesDefaults {
    ...
    const val GPS_ACCURACY_THRESHOLD_M = 200.0f
```
- **Baseline Reconciliation**: The parent ticket review notes referenced reducing GPS tolerance *"from 100 m to 50 m"*, whereas forensic investigation of the codebase reveals that `TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M` was previously initialized to `200.0f` (inherited directly from legacy `SpeedAndLocationDevice.ACCURACY_THRESHOLD = 200`). Regardless of whether the conversational baseline assumed 100m or 200m, both the user's explicit directive and code requirements converge unambiguously on the target value of **50.0 m** (`50.0f`).
- Setting `GPS_ACCURACY_THRESHOLD_M` to `50.0f` tightens location filtering out-of-the-box while remaining safely above typical consumer GNSS multipath/drift (~10–25m).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Move `drawer_advanced_tuning` from `drawer__settings` into a new, dedicated `DrawerGroup(titleRes = R.string.drawer__expert_settings, items = listOf(...))` positioned at the very end of the drawer list in `AppNavigationDrawer.kt`.
  * Add string resource `drawer__expert_settings` in all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  * Update string resources `advanced_tuning_title` and `advanced_tuning_open` across all 9 supported locales.
  * Update `TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M` to `50.0f`.
  * Update unit tests in `TuningConfigTest.kt` and `AppNavigationDrawerTest.kt`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not change the internal preference keys (`tuning_gps_accuracy_threshold`) in DataStore to maintain full backward compatibility with athletes' saved settings.
  * Do not modify the slider range ($10.0\text{m} .. 500.0\text{m}$) in `AdvancedTuningDialog.kt`.
  * Do not alter the entry point from `DisplaySettingsDialog.kt` (it continues to launch `SettingsBottomSheetType.ADVANCED_TUNING`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-SET-073` (*Dedicated Advanced Tuning Preferences Screen with Parameter Clamping and Reset-to-Defaults*).
* **Historical Origin & Commit Trace**: Ticket `ATT-1304` (Sprint `2026-40.4`).
* **Root Reason for Existing Formulation**: `ATT-1304` placed the item in `drawer__settings` as a fast initial implementation and used 200m based on legacy constants in `SpeedAndLocationDevice`.
* **Refinement Reason**: Post-release athlete evaluation indicated that the wording was awkward and the item needed clear separation from general preferences, and that 50m default GPS tolerance provides superior location filtering.
* **Preservation of Core Invariants**:
  - Clamping boundaries and safety floor (5% brightness) preserved.
  - Reset to factory defaults operates identically (reverting to 50m GPS accuracy).
  - All existing tracking components (`SpeedAndLocationDevice`, `BatterySaverController`) adopt the parameter reactively.
  - 100% 9-language localization parity maintained.

---

## 5. Architectural Strategy & High-Level Solution

1. **Navigation Drawer Update (`AppNavigationDrawer.kt`)**:
   - In `createDrawerGroups`:
     - Remove `DrawerItemConfig(R.id.drawer_advanced_tuning, ...)` from `DrawerGroup(titleRes = R.string.drawer__settings)`.
     - Append a new `DrawerGroup(titleRes = R.string.drawer__expert_settings, items = listOf(DrawerItemConfig(R.id.drawer_advanced_tuning, R.drawable.ic_tune, R.string.advanced_tuning_title)))`.
2. **Localization (`strings.xml` in 9 locales)**:
   - Define `drawer__expert_settings` across all 9 languages.
   - Update `advanced_tuning_title` and `advanced_tuning_open` across all 9 languages.
3. **DataStore Defaults (`TuningPreferencesDataStore.kt`)**:
   - Change `GPS_ACCURACY_THRESHOLD_M` from `200.0f` to `50.0f`.
4. **Verification**:
   - Update `TuningConfigTest.kt` asserting `50.0f`.
   - Add test in `AppNavigationDrawerTest.kt` asserting that the final group in `createDrawerGroups` is the expert settings section containing `drawer_advanced_tuning`.
   - Run translation parity test.

---

## 6. System Invariants & Risk Assessment

| Invariant / Risk | Mitigation |
| :--- | :--- |
| **Existing User Preferences** | DataStore keys are untouched; athletes who explicitly tuned GPS accuracy keep their saved preferences. New installations and factory resets default to 50m. |
| **Drawer Navigation Stability** | Route resolution and bottom sheet dispatch (`NavRoutes.toBottomSheetType(R.id.drawer_advanced_tuning)`) use the stable ID `R.id.drawer_advanced_tuning`, which remains unchanged. |
| **9-Language Parity** | All string resources are verified across EN, DE, ES, FR, IT, JA, NL, PL, and PT via automated tests. |
