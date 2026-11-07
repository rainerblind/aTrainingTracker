# Stage 2: Requirement & Test Specification - ATT-5670: Navigation Drawer & Expert Settings Restructuring

**Ticket**: [ATT-5670](https://rainerblind.atlassian.net/browse/ATT-5670)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5670`  
**Requirement Mapping**: `REQ-UI-463` (refining `REQ-UI-010`, `REQ-UI-222`) | **Test Spec ID**: `TST-UI-463`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-UI-463`)

### 1.1 Problem Statement & Rationale
Incremental feature growth has overloaded the Navigation Drawer (`AppNavigationDrawer.kt`) with 10 mixed settings items and unorganized Expert Settings (`AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt`) across 9 continuous accordion cards. The system SHALL restructure the drawer into logical domains (moving Import & Backup to Online Communities, streamlining Settings to 6 core tracking configurations, and introducing an "Info & Support" bottom group) and organize Expert Settings into 3 thematic blocks with visual subheaders.

### 1.2 Functional & Architectural Requirements
1. **Navigation Drawer Restructuring (`REQ-UI-463.1`)**:
   - `prefsOnlineCommunities` SHALL include `drawer_backup_restore` directly below `drawer_export` (5 items total).
   - `drawer__settings` SHALL be streamlined to 6 items in canonical sequence: `drawer_tracking_layouts`, `drawer_sport_types`, `drawer_auto_pause`, `drawer_display_settings`, `drawer_units`, `drawer_advanced_tuning`.
   - A new collapsible bottom group `drawer__info_support` SHALL be introduced containing: `drawer_supporter`, `drawer_tips_guide`, and `drawer_privacy_policy`.
2. **Expert Settings Thematic Grouping (`REQ-UI-463.2`)**:
   - The 9 accordions in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` SHALL be organized into 3 logical blocks separated by visual subheaders:
     * Block 1 (`tuning_group_cockpit_tracking`): Typography & Icons, Navigation & Tracking, Battery & Screen.
     * Block 2 (`tuning_group_sensors_gps`): Sensor Search, GPS & Filter.
     * Block 3 (`tuning_group_analysis_models`): Workout Masks & Cards, Aftermath & Analysis, Physiology & Performance, Route Clustering.
3. **Modularity & Reusable Subheader (`REQ-UI-463.3`)**:
   - `TuningGroupSubheader` composable SHALL be implemented in `AdvancedTuningAccordion.kt`.
   - All files in `ui/settings/tuning/` SHALL maintain strictly `< 400` lines per `AdvancedTuningModularityTest`.
4. **9-Language Localization Parity (`REQ-UI-463.4`)**:
   - 4 new string keys (`drawer__info_support`, `tuning_group_cockpit_tracking`, `tuning_group_sensors_gps`, `tuning_group_analysis_models`) SHALL be defined across all 9 locales with `&amp;` escaping and zero inline emojis (`ATT-5400`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Drawer Groups)**: *Given* the navigation drawer open, *When* inspecting groups, *Then* Import & Backup appears under Online Communities, Settings contains 6 configuration items ending with Advanced Tuning, and Info & Support forms the bottom group.
* **AC-2 (Expert Settings Blocks)**: *Given* Expert Settings open, *When* scrolling the 9 accordions, *Then* they appear in 3 blocks preceded by thematic subheaders.
* **AC-3 (Modularity)**: *Given* `AdvancedTuningModularityTest`, *When* checking file lengths, *Then* all tuning files are strictly `< 400` lines.
* **AC-4 (9-Locale Parity)**: *Given* `TranslationParityTest`, *When* validating all 9 locales, *Then* all 4 new keys are non-empty with zero format errors and zero emojis.
* **Invariants**: All drawer item IDs (`R.id.*`) and destination routes unchanged; DataStore keys, tuning parameters, and algorithms untouched.

---

## 2. Test Specification (`TST-UI-463`)

### Test Case 1: Navigation Drawer Hierarchy & Group Ordering Contract (`TST-UI-463.1`)
* **Scope**: Unit / Architecture Test (`AppNavigationDrawerTest.kt`)
* **Action**: Verify `prefsOnlineCommunities` has 5 items with `drawer_backup_restore` below `drawer_export`; verify `drawer__settings` has 6 items ending with `drawer_advanced_tuning`; verify `drawer__info_support` contains supporter, tips guide, and privacy policy.

### Test Case 2: Expert Settings Canonical Sequence & Subheader Audit (`TST-UI-463.2`)
* **Scope**: UI Structural Contract Test (`AdvancedTuningScreenTest.kt`)
* **Action**: Verify the 3-block accordion sequence and insertion of `TuningGroupSubheader` elements in Screen and Dialog.

### Test Case 3: Tuning Modularity & Line Limit Enforcement (`TST-UI-463.3`)
* **Scope**: Architecture Linter Test (`AdvancedTuningModularityTest.kt`)
* **Action**: Verify `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, and `AdvancedTuningAccordion.kt` are all `< 400` lines.

### Test Case 4: 9-Language Localization Audit (`TST-UI-463.4`)
* **Scope**: Localization Contract Test (`TranslationParityTest.kt`)
* **Action**: Verify 4 new keys across all 9 locales exist, are non-empty, use `&amp;`, and have zero inline emojis.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-UI-463.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Action**: Confirm 100% test pass rate across affected navigation, settings tuning, theme, and localization suites.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-463.1` | Contract | `AppNavigationDrawer.kt`, `AppNavigationDrawerTest.kt` | `REQ-UI-463.1` | Specified |
| `TST-UI-463.2` | Contract | `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `AdvancedTuningScreenTest.kt` | `REQ-UI-463.2` | Specified |
| `TST-UI-463.3` | Architecture | `AdvancedTuningAccordion.kt`, `AdvancedTuningModularityTest.kt` | `REQ-UI-463.3` | Specified |
| `TST-UI-463.4` | Localization | 9 `strings.xml` files, `TranslationParityTest.kt` | `REQ-UI-463.4` | Specified |
| `TST-UI-463.5` | Regression | Navigation, Tuning, Theme, Build Test Suites | `REQ-UI-463.1`..`REQ-UI-463.4` | Specified |
