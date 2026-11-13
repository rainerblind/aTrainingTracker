# Stage 2: Requirement & Test Specification - ATT-7535: Reorder Settings section in Navigation Drawer

**Ticket**: [ATT-7535](https://rainerblind.atlassian.net/browse/ATT-7535)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-UI-513.2` (*Settings Drawer Items Ordering*)  
**Test Spec ID**: `TST-UI-513.1` | **Branch**: `feature/ATT-7535`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-UI-513.2)

### 1.1 Problem Statement & Rationale
In `AppNavigationDrawer.kt`, the *Einstellungen* (*Settings*) group currently lists items out of logical hierarchy (Tracking Tabs, Sportarten, Trainings Zonen, Auto-Pause, Display, Maßeinheiten, Experten-Einstellungen). Reordering to athlete physiology -> sport taxonomy -> cockpit -> display & pause behavior -> units -> expert tuning establishes an intuitive cognitive progression.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. `AppNavigationDrawer.kt` `createDrawerGroups()` SHALL order the 7 `drawer__settings` items in exact sequence:
   - 1: Trainings Zonen (`R.id.drawer_training_zones`, `R.drawable.ic_speed`, `R.string.prefs_training_zones`)
   - 2: Sportarten (`R.id.drawer_sport_types`, `R.drawable.ic_sports_combined`, `R.string.sport_types`)
   - 3: Tracking Tabs (`R.id.drawer_tracking_layouts`, `R.drawable.ic_table_edit`, `R.string.prefsConfigureDisplaysTitle`)
   - 4: Display (`R.id.drawer_display_settings`, `R.drawable.ic_display_settings`, `R.string.Display`)
   - 5: Auto-Pause (`R.id.drawer_auto_pause`, `R.drawable.ic_pause_circle`, `R.string.auto_pause_title`)
   - 6: Maßeinheiten (`R.id.drawer_units`, `R.drawable.ic_square_foot`, `R.string.prefsUnitsTitle`)
   - 7: Experten-Einstellungen (`R.id.drawer_advanced_tuning`, `R.drawable.ic_tune`, `R.string.advanced_tuning_title`)
2. All item IDs, drawables, strings, tinting rules, and `NavRoutes` mappings MUST remain preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC1 (Settings Drawer Item Ordering)**:
  * *Given* the user expands the *Einstellungen* group in `AppNavigationDrawer`,
  * *When* items are rendered,
  * *Then* they appear in the exact top-to-bottom sequence 1–7 specified above.
* **AC2 (Unit Test & Navigation Invariant Parity)**:
  * *Given* unit tests `NavigationDrawerStateTest` and `AutoPauseNavigationTest`,
  * *When* executed,
  * *Then* they assert the new sequence and pass cleanly.

### 1.5 System Invariants
- Total drawer item count remains 27 (7 in Settings group).
- Pure declarative list reordering; zero database, route, or destination changes.

---

## 2. Test Specification (TST-UI-513.1)

### Test Case 1: Settings Drawer Group Sequence Verification (`TST-UI-513.1a`)
* **Scope**: Unit Test (`NavigationDrawerStateTest.kt#testDrawerGroupsSettingsOrdering`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt`
* **Precondition**: `createDrawerGroups()` called with active tracking tab.
* **Action**: Map `settingsGroup.items.map { it.id }`.
* **Expected Result**: Matches `listOf(drawer_training_zones, drawer_sport_types, drawer_tracking_layouts, drawer_display_settings, drawer_auto_pause, drawer_units, drawer_advanced_tuning)`.

### Test Case 2: AutoPause Settings Adjacency Verification (`TST-UI-513.1b`)
* **Scope**: Unit Test (`AutoPauseNavigationTest.kt#testAutoPauseDrawerItemConfiguration`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AutoPauseNavigationTest.kt`
* **Precondition**: Settings drawer group queried.
* **Action**: Assert indices of `drawer_sport_types`, `drawer_training_zones`, and `drawer_auto_pause`.
* **Expected Result**: `sportTypesIndex == trainingZonesIndex + 1` (Sportarten directly below Training Zones).

### Test Case 3: Tier 1 Targeted Navigation Suite (`TST-UI-513.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*"`
* **Expected Result**: 100% PASS across all navigation unit tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-513.1a` | Unit | `NavigationDrawerStateTest.kt` | `REQ-UI-513.2` | Specified |
| `TST-UI-513.1b` | Unit | `AutoPauseNavigationTest.kt` | `REQ-UI-513.2` | Specified |
| `TST-UI-513.5` | Tier 1 Regr | `ui.navigation.*` | `REQ-PRO-001` | Specified |
