# Stage 2: Requirement & Test Specification - ATT-6209: Card Context Menu Home-Base Excision

**Ticket**: [ATT-6209](https://atrainingtracker.atlassian.net/browse/ATT-6209)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6209`  
**Requirement Mapping**: `REQ-MAP-036` (`REQ-MAP-036.6`) | **Test Spec ID**: `TST-MAP-036` (`TST-MAP-036.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-MAP-036.6 Context Menu Purity)

### 1.1 Problem Statement & Rationale
In `KnownLocationsScreen.kt`, long-pressing a `KnownLocationCard` opens a context menu (`DropdownMenu`) that contains redundant "Als Heim-Basis festlegen" / "Heim-Basis aufheben" actions. Home-Base designation is an infrequent administrative setting that is already fully configurable with descriptive explanation (`known_locations_home_base_desc`) inside `EditKnownLocationDialog`. Exposing competing mutation surfaces creates unnecessary visual clutter in the card quick-action menu (Design Guidelines §3.2).

### 1.2 Functional & Architectural Requirements
1. **Context Menu Leaf Excision (`REQ-MAP-036.6`)**:
   - In `KnownLocationsScreen.kt` (`KnownLocationCard`), the `DropdownMenuItem` entries for "Als Heim-Basis festlegen" (`known_locations_set_home`) and "Heim-Basis aufheben" (`known_locations_remove_home`) SHALL be removed.
   - The card context menu SHALL retain only card-scoped contextual operations: "Zusammenführen..." (`REQ-SET-095`) and "Löschen" (`REQ-UI-061`).
2. **Signature Hygiene**:
   - Remove unused `onSetHome` and `onClearHome` lambdas from `KnownLocationCard` and its screen callers.
3. **Preservation of Invariants**:
   - Retain `Icons.Default.Home` badge indicator in card title row next to the location name.
   - Retain dedicated Home-Base toggle switch in `EditKnownLocationDialog`.
   - Retain `KnownLocationsViewModel` methods (`setHomeLocation`, `clearHomeLocation`) and transactional exclusivity in SQLite (`StartLocation2Altitude.db`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Excision of Context Menu Home Actions)**:
  * *Given* a known location card (whether home or non-home),
  * *When* long-pressed to reveal the context menu,
  * *Then* neither "Als Heim-Basis festlegen" nor "Heim-Basis aufheben" is displayed; test tags `location_set_home_action_${item.id}` and `location_clear_home_action_${item.id}` are absent.
* **Criterion 2 (Context Menu Retention)**:
  * *Given* the card context menu,
  * *When* opened,
  * *Then* it contains "Zusammenführen..." (`location_merge_action_${item.id}`) and "Löschen" (`location_delete_action_${item.id}`).
* **Criterion 3 (Edit Dialog Configuration Integrity)**:
  * *Given* `EditKnownLocationDialog`,
  * *When* opened,
  * *Then* the Home-Base switch row (`known_locations_home_base`) and descriptive subtitle (`known_locations_home_base_desc`) remain fully functional.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Clause 3 of `REQ-MAP-034` (*Designated Home-Base Selection*) and `REQ-MAP-036` (*Known Locations: Heim-Basis Redundant Badge Excision*); refined by `ATT-6209` (`REQ-MAP-036.6`).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2472` (Sprint `2026-41.1`), `ATT-2629`, `ATT-5884` (`REQ-UI-470.5`), `ATT-6209`.
3. *Root Reason for Existing Formulation*: In ATT-2472, home actions were added directly to the card context menu before `EditKnownLocationDialog` was enhanced with a dedicated, informative toggle switch (`known_locations_home_base_desc`). Exposing home actions in both places created redundant, competing mutation surfaces violating Design Guidelines §3.2.
4. *Preservation of Core Invariants*: Single-home transactional exclusivity in SQLite (`StartLocation2Altitude.db`), Home icon indicator in card title (`Icons.Default.Home`), home configuration in `EditKnownLocationDialog`, merge action (`location_merge_action_`), and delete action (`location_delete_action_`) remain strictly intact.

---

## 2. Test Specification (TST-MAP-036.4)

### Test Case 1: Contract Test Synchronization (`TST-MAP-036.4a`)
* **Scope**: `KnownLocationHomeContractTest.kt`.
* **Assertions**: Assert `assertFalse` for `known_locations_set_home` and `known_locations_remove_home` in `KnownLocationsScreen.kt`; assert `assertTrue` for `location_home_icon_${item.id}` in card title and Home-Base toggle in `EditKnownLocationDialog`.

### Test Case 2: Context Menu Purity Contract Test (`TST-MAP-036.4b`)
* **Scope**: `KnownLocationHomeContractTest.kt`, `KnownLocationsScreenTest.kt`.
* **Assertions**: Assert `location_set_home_action_` and `location_clear_home_action_` test tags are absent from screen source; assert `location_merge_action_` and `location_delete_action_` are present.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-MAP-036.4c`)
* **Scope**: Targeted unit tests (`ui.knownlocations.*`, `ui.theme.*`, `ui.components.core.*`, `build.*`).
* **Assertions**: 100% pass across touched packages and architectural rules.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-MAP-036.4a]` | Contract | `KnownLocationHomeContractTest.kt`: Absence of set/remove home in context menu | `REQ-MAP-036.6` | Specified |
| `[TST-MAP-036.4b]` | Contract | `KnownLocationHomeContractTest.kt`: Retention of title icon, merge, and delete | `REQ-MAP-036.6` | Specified |
| `[TST-MAP-036.4c]` | Regression | Tier 1 targeted suite (`ui.knownlocations.*`, `build.*`) | `REQ-PRO-001` | Specified |
