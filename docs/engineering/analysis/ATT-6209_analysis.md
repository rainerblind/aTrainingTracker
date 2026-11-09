# Stage 1 Analysis: ATT-6209 - KnownLocationCard (Lieblingsorte): Remove 'Als Heim-Basis festlegen' entry from card context menu

**Ticket**: [ATT-6209](https://atrainingtracker.atlassian.net/browse/ATT-6209)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6209`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
* **Context**: In `KnownLocationsScreen.kt`, long-pressing a `KnownLocationCard` opens a context menu (`DropdownMenu`) containing "Als Heim-Basis festlegen" (`known_locations_set_home`) / "Heim-Basis aufheben" (`known_locations_remove_home`), followed by "Zusammenführen..." (`known_locations_menu_merge`) and "Löschen" (`delete`).
* **Defect**: Home-Base designation is an infrequent administrative property already fully configurable via the dedicated `EditKnownLocationDialog` (Switch with `known_locations_home_base_desc`). Exposing it in the card quick-action context menu creates UI clutter, visual distraction, and redundant competing mutation surfaces violating Design Guidelines §3.2.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `ATT-2472` originally added set/clear home actions to the card context menu before `EditKnownLocationDialog` became the primary 1-tap configuration surface.
  * `KnownLocationsScreen.kt:995-1025` conditionally renders `location_set_home_action_${item.id}` and `location_clear_home_action_${item.id}` inside `KnownLocationCard`.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected `EditKnownLocationDialog.kt:80-90`: Fully encapsulates Home Base toggle switch and description.
  * Inspected `KnownLocationsViewModel.kt:390-420`: Home designation persists reliably via `KnownLocationsRepository` and `KnownLocationsDatabaseManager`.
  * Verified context menu retains its core contextual operations: "Zusammenführen..." (`REQ-SET-095`) and "Löschen" (`REQ-UI-061`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Remove `DropdownMenuItem` for `known_locations_remove_home` and `known_locations_set_home` from `KnownLocationCard`.
  * Clean up unused `onSetHome` and `onClearHome` lambdas from `KnownLocationCard` and parent screen hierarchy.
  * Retain `EditKnownLocationDialog` Home-Base toggle without modifications.
  * Update `KnownLocationHomeContractTest.kt` to assert context menu does not contain home toggle items.
  * Update `KnownLocationsScreenTest.kt` to assert `location_set_home_action_` and `location_clear_home_action_` are absent.
* **Out-of-Scope**: Modifying Home Base DB schema, changing `EditKnownLocationDialog`, or altering `Icons.Default.Home` badge indicator in card title.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Original Requirement ID & Target**: `REQ-MAP-034` / `TST-MAP-036.4` (*Designated Home-Base Selection*) and `REQ-UI-061` / `REQ-SET-095` (*Card Context Menu Purity*).
* **Historical Origin & Commit Trace**: `ATT-2472` introduced home actions into the context menu. `ATT-2629` (`REQ-MAP-036`) later removed redundant card badge. `ATT-5889` added merge action.
* **Root Reason for Existing Formulation**: Rapid prototyping convenience during initial Home-Base feature rollout.
* **Preservation of Core Invariants**: Home icon indicator (`Icons.Default.Home`) next to card title, Home-Base toggle in dialog, and single home transactional exclusivity in DB remain strictly intact.
* **Side-Effect Audit (Rule 68)**: Zero database migration; zero ViewModel logic break; zero change to workout auto-naming or return navigation resolution.

---

## 5. Architectural Strategy & High-Level Solution
* **Leaf UI Excision**: In `KnownLocationsScreen.kt`, delete lines 995-1025 (`DropdownMenuItem` for home base).
* **Clean Parameter Signatures**: Remove `onSetHome` and `onClearHome` parameters from `KnownLocationCard` and screen callers where unused.
* **Contract Test Synchronization**: Update `KnownLocationHomeContractTest.kt` and `KnownLocationsScreenTest.kt` to enforce absence of home actions from card context menu.
* **Living Docs Traceability**: Update `docs/tests/tst_maps_navigation.md` (`TST-MAP-036.4`) to document the refined context menu contract.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Target Release `V5.0.0`; Active Sprint `2026-45.13`; 100% test pass in `KnownLocationHomeContractTest`, `KnownLocationsScreenTest`, and full unit test suite.
* **Risk Rating**: **LOW** — Declarative Jetpack Compose UI item removal with zero backend, database, or business logic dependencies.
