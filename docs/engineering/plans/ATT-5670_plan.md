# Stage 3: Implementation Plan - ATT-5670: Navigation Drawer & Expert Settings Logical Restructuring

**Ticket**: [ATT-5670](https://rainerblind.atlassian.net/browse/ATT-5670)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5670`  
**Requirement Mapping**: `REQ-UI-463` (`REQ-UI-463.1`–`REQ-UI-463.4`) | **Test Mapping**: `TST-UI-463` (`TST-UI-463.1`–`TST-UI-463.5`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Architectural Strategy
To resolve UI clutter caused by organic feature growth, aTrainingTracker restructures the Navigation Drawer (`AppNavigationDrawer.kt`) and Expert Settings (`AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt`):
- **Navigation Drawer**: Relocates *Import & Backup* to *Online Communities*, streamlines *Settings* to 6 core tracking configurations ending with *Advanced Tuning*, and introduces a new collapsible *Info & Support* bottom group for non-config/legal items.
- **Expert Settings**: Partitions 9 accordions into 3 logical blocks headed by visual subheaders, pairing *Aftermath & Analysis* directly adjacent to *Workout Masks & Cards*.
- **UI Consistency**: Reusable `TuningGroupSubheader` in `AdvancedTuningAccordion.kt` adhering to `MaterialTheme.typography.labelLarge` and `colorScheme.primary`. Preserves 0 dp tonal elevation and strict `< 400` line threshold across all files in `ui/settings/tuning/`.

---

## 2. Invariants & Preserved Behavior
1. **Routing & Actions**: All 25 drawer destination IDs (`R.id.*`) and route mappings remain strictly unchanged (`REQ-UI-463.1`).
2. **Persistence Integrity**: All 25+ DataStore preference keys, defaults, and reactive state flows remain 100% untouched (`REQ-UI-463.2`).
3. **Modularity Limit**: All files in `ui/settings/tuning/` strictly maintain `< 400` lines per `AdvancedTuningModularityTest` (`REQ-UI-463.3`).
4. **9-Language Parity**: 4 new keys defined across all 9 locales with `&amp;` XML entity escaping and zero inline emojis (`ATT-5400`, `REQ-UI-463.4`).

---

## 3. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Locale String Resources (`REQ-UI-463.4`)
* **Files**: `app/src/main/res/values/strings.xml` and `values-{de,es,fr,it,ja,nl,pl,pt}/strings.xml`
* **Changes**: Add `drawer__info_support`, `tuning_group_cockpit_tracking`, `tuning_group_sensors_gps`, and `tuning_group_analysis_models` across all 9 files with `&amp;` escaping and no emojis.

### Step 2: Navigation Drawer Group Restructuring (`REQ-UI-463.1`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`
* **Changes**:
  - `prefsOnlineCommunities`: Append `drawer_backup_restore` below `drawer_export` (5 items total).
  - `drawer__settings`: Streamline to 6 items: `drawer_tracking_layouts`, `drawer_sport_types`, `drawer_auto_pause`, `drawer_display_settings`, `drawer_units`, `drawer_advanced_tuning`.
  - Append new collapsible group `drawer__info_support` with: `drawer_supporter`, `drawer_tips_guide`, `drawer_privacy_policy`.

### Step 3: Reusable Subheader & Modularity (`REQ-UI-463.2`, `REQ-UI-463.3`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`
* **Changes**: Add `@Composable fun TuningGroupSubheader(@StringRes titleRes: Int, modifier: Modifier = Modifier)`. Ensure file stays `< 400` lines.

### Step 4: Expert Settings Thematic Grouping & Pairing (`REQ-UI-463.2`, `REQ-UI-463.3`)
* **Files**: `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`
* **Changes**: Add 3 `TuningGroupSubheader` headers and reorder 9 accordions into 3 blocks:
  - Block 1 (`tuning_group_cockpit_tracking`): `COCKPIT_TYPOGRAPHY`, `NAVIGATION`, `BATTERY_SAVER`.
  - Block 2 (`tuning_group_sensors_gps`): `SENSOR_SEARCH`, `SENSORS_GPS`.
  - Block 3 (`tuning_group_analysis_models`): `WORKOUT_MASKS_CARDS`, `AFTERMATH_ANALYSIS`, `PHYSIOLOGY_MODELS`, `ROUTE_CLUSTERING`.
  - Keep both files strictly `< 400` lines.

### Step 5: Test Suite Updates & Regression Verification (`TST-UI-463.1`–`TST-UI-463.5`)
* **Files**: `AppNavigationDrawerTest.kt`, `NavigationDrawerStateTest.kt`, `AdvancedTuningScreenTest.kt`
* **Changes**: Update assertions for 6-group drawer structure, item counts, ordering, and the new 3-block accordion sequence.

---

## 4. Verification Commands (`TST-UI-463.5`)
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* `python3 tools/verify_requirement_governance.py`
