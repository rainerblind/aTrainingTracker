# Stage 3: Implementation Plan - ATT-7535: Reorder Settings section in Navigation Drawer

**Ticket**: [ATT-7535](https://rainerblind.atlassian.net/browse/ATT-7535)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-UI-513.2` (*Settings Drawer Items Ordering*)  
**Test Mapping**: `TST-UI-513.1` (`TST-UI-513.1a`, `TST-UI-513.1b`, `TST-UI-513.5`)  
**Branch**: `feature/ATT-7535` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
In `AppNavigationDrawer.kt`, the 7 items in the *Einstellungen* (*Settings*) group currently follow an arbitrary order that disrupts the user's mental model. This plan reorders the items into an intuitive progression: (1) Trainings Zonen, (2) Sportarten, (3) Tracking Tabs, (4) Display, (5) Auto-Pause, (6) Maßeinheiten, and (7) Experten-Einstellungen.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-513.2` (*Settings Drawer Items Ordering*)
* **Test Mapping**: `TST-UI-513.1` (Navigation Drawer Structure & Adjacency Suite)
  - `TST-UI-513.1a`: `NavigationDrawerStateTest.kt` (Exact 7-item Settings sequence)
  - `TST-UI-513.1b`: `AutoPauseNavigationTest.kt` (Relative item adjacency checks)
  - `TST-UI-513.5`: `ui.navigation.*` (Full Tier 1 navigation regression suite)

---

## 3. System Invariants & Preserved Behavior
1. **Drawer Totals & Routing**: 27 total drawer items preserved; route resolution (`NavRoutes.kt`) and item IDs remain identical.
2. **Expansion & Selection**: Collapsible behavior and initial group expansion (`isDrawerGroupInitiallyExpanded`) unaffected.
3. **Parent Human Gate**: Terminal completion remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **UI Layer (`AppNavigationDrawer.kt`)**: Reorder `items` inside `createDrawerGroups()` under `R.string.drawer__settings`.
* **UI Consistency (`docs/design_guidelines.md`)**:
  - *Reference Component*: `AppNavigationDrawer.kt` (`DrawerGroup`, `DrawerItemConfig`).
  - *Reused Components*: Existing standard `DrawerItemView` and `DrawerGroupView`.
  - *Theme Tokens*: Material 3 standard drawer surface & typography; zero new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Reorder Settings Items in `AppNavigationDrawer.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`
* **Changes**: Reorder `items` in `DrawerGroup(titleRes = R.string.drawer__settings)` to: `drawer_training_zones`, `drawer_sport_types`, `drawer_tracking_layouts`, `drawer_display_settings`, `drawer_auto_pause`, `drawer_units`, `drawer_advanced_tuning`.

### Step 2: Update Sequence Assertions in `NavigationDrawerStateTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt`
* **Changes**: Update `settingsIds` expected list to match the new 7-item order and update positional assertions.

### Step 3: Update Relative Adjacency Checks in `AutoPauseNavigationTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AutoPauseNavigationTest.kt`
* **Changes**: Update `testDrawerStructure_autoPauseDirectlyBelowSportTypes` to verify `sportTypesIndex == trainingZonesIndex + 1` and `autoPauseIndex == displaySettingsIndex + 1`.

### Step 4: Tier 1 Unit Test Verification & Governance Audit
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit test execution (`TST-UI-513.1a`, `TST-UI-513.1b`, `TST-UI-513.5`) and Stage 5 clean-room test run.
* **Rollback**: Branch isolation (`feature/ATT-7535`) allows complete rollback via `git checkout sprint/2026-46.5`.
