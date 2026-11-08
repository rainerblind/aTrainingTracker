# Stage 3: Implementation Plan - ATT-6209: Card Context Menu Home-Base Excision

**Ticket**: [ATT-6209](https://atrainingtracker.atlassian.net/browse/ATT-6209)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6209`  
**Requirement Mapping**: `REQ-MAP-036` (`REQ-MAP-036.6`) | **Test Mapping**: `TST-MAP-036` (`TST-MAP-036.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Description & Objective
In `KnownLocationsScreen.kt`, long-pressing a `KnownLocationCard` opens a context menu (`DropdownMenu`) that contains redundant "Als Heim-Basis festlegen" / "Heim-Basis aufheben" actions (`location_set_home_action_${item.id}` / `location_clear_home_action_${item.id}`). Home-Base designation is an infrequent administrative setting already fully configurable with descriptive explanation (`known_locations_home_base_desc`) inside `EditKnownLocationDialog`. This plan excises the redundant menu items from `KnownLocationCard` and cleans up unused lambda parameters across the screen hierarchy while preserving the title Home icon, dialog toggle, merge, and delete operations.

---

## 2. Requirements & Living Documentation Mapping
* **Requirement**: `REQ-MAP-036.6` (*Card Context Menu Home-Base Action Excision*).
  * In `KnownLocationCard` (`KnownLocationsScreen.kt`), remove the `if (item.isHome) ... else ...` `DropdownMenuItem` block for Home-Base designation/removal.
  * Retain contextual card operations: "Zusammenführen..." (`REQ-SET-095`) and "Löschen" (`REQ-UI-061`).
  * Preserve `Icons.Default.Home` badge indicator in card title row and dedicated Home-Base toggle switch in `EditKnownLocationDialog`.
* **Test Mapping**: `TST-MAP-036.4` (`TST-MAP-036.4a` Contract tests in `KnownLocationHomeContractTest.kt`, `TST-MAP-036.4b` Context menu purity tests in `KnownLocationsScreenTest.kt`, `TST-MAP-036.4c` Tier 1 regression suite).

---

## 3. System Invariants & UI Consistency
1. **UI Consistency**: Follows `docs/design_guidelines.md` §3.2 (elimination of redundant competing mutation surfaces; quick-action purity).
2. **Title Home Indicator Invariant**: `Icons.Default.Home` indicator in card title row next to the location name (`location_home_icon_${item.id}`) remains strictly intact.
3. **Dialog Configuration Invariant**: `EditKnownLocationDialog` Home-Base switch row (`known_locations_home_base`) and descriptive subtitle (`known_locations_home_base_desc`) remain untouched and fully operational.
4. **Database & ViewModel Invariants**: Zero changes to SQLite schema (`StartLocation2Altitude.db`), `KnownLocationsDatabaseManager`, `KnownLocationsRepository`, or `KnownLocationsViewModel` methods (`setHomeLocation`, `clearHomeLocation`).

---

## 4. Architectural Strategy & Component Design
* **Component 1: `KnownLocationsScreen.kt`**:
  * In `KnownLocationCard`, remove lines 995-1025 (the `item.isHome` conditional `DropdownMenuItem` block).
  * Remove unused parameters `onSetHome: () -> Unit = {}` and `onClearHome: () -> Unit = {}` from `KnownLocationCard`.
  * Remove `onSetHome` and `onClearHome` parameter propagation from `KnownLocationsContent` and `KnownLocationsScreen` top-level composable.
* **Component 2: `KnownLocationHomeContractTest.kt`**:
  * Update `testKnownLocationsScreen_containsHomeTitleIconAndNoRedundantBadge()`: assert that `KnownLocationsScreen.kt` does NOT contain `known_locations_set_home` and `known_locations_remove_home`. Assert that `location_home_icon_${item.id}` and `EditKnownLocationDialog` Home-Base switch are present.
* **Component 3: `KnownLocationsScreenTest.kt`**:
  * Update `testKnownLocationCardContextMenuStructure()`: assert that `location_set_home_action_` and `location_clear_home_action_` are NOT present in `KnownLocationsScreen.kt`. Assert `location_merge_action_` and `location_delete_action_` are present.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Check
* Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` to verify Gate 3 PASS before code edits.

### Step 2: Excise Home-Base Dropdown Menu Items and Clean Signatures in `KnownLocationsScreen.kt`
* Remove the `item.isHome` conditional dropdown menu items from `KnownLocationCard`.
* Clean up `onSetHome` and `onClearHome` callbacks from `KnownLocationCard` and `KnownLocationsContent`.

### Step 3: Update `KnownLocationHomeContractTest.kt`
* Update contract assertions to verify absence of `known_locations_set_home` / `known_locations_remove_home` and presence of title Home icon.

### Step 4: Update `KnownLocationsScreenTest.kt`
* Update `testKnownLocationCardContextMenuStructure()` to verify absence of `location_set_home_action_` / `location_clear_home_action_` and presence of merge/delete actions.

### Step 5: Verification & Governance
* Targeted tests: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Governance audit: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-45.13`
