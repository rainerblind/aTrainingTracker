# Stage 5: Walkthrough & Verification - ATT-6209: Card Context Menu Home-Base Excision

**Ticket**: [ATT-6209](https://atrainingtracker.atlassian.net/browse/ATT-6209)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6209`  
**Requirement Mapping**: `REQ-MAP-036` (`REQ-MAP-036.6`) | **Test Spec ID**: `TST-MAP-036` (`TST-MAP-036.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Scope
Long-pressing a `KnownLocationCard` previously displayed a context menu (`DropdownMenu`) containing redundant "Als Heim-Basis festlegen" / "Heim-Basis aufheben" actions. Home-Base designation is an infrequent administrative setting already configured via the dedicated switch row in `EditKnownLocationDialog` (`known_locations_home_base_desc`). In this ticket, the redundant dropdown menu entries and unused callback parameters (`onSetHome`, `onClearHome`) were excised from `KnownLocationCard` and its screen callers, leaving only card-scoped contextual actions ("Zusammenführen..." and "Löschen") per Design Guidelines §3.2.

---

## 2. On-Device Visual Inspection (Rule 71 Certified)
* Verified on physical **Pixel 10** (`66020DLCR002FL`, Android 17):
  * Navigated to *Karten -> Lieblingsorte* (`KnownLocationsScreen`).
  * Long-pressed a `KnownLocationCard` to reveal the context menu (`DropdownMenu`).
  * **Result**: The context menu displays strictly "Mit anderem Ort zusammenführen…" (`location_merge_action_`) and "löschen" (`location_delete_action_`). "Als Heim-Basis festlegen" and "Heim-Basis aufheben" are absent.
  * The title Home icon indicator (`Icons.Default.Home`), card sport breakdown, and `EditKnownLocationDialog` toggle remain fully intact.
  * Verified clean logcat crash buffer.

![Known Location Context Menu Purity](file:///home/rainer/.gemini/antigravity/worktrees/b86ffd1a-5a6a-4e52-9976-b53aca20c1c8/subagent-Epic-ATT-1396-Pro-Agent-self-5415be16/docs/engineering/walkthroughs/img/ATT-6209_known_location_context_menu.png)

---

## 3. Automated Test Execution & Scope
* **Tier 1 Targeted Test Scope**:
  * Command: `./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * **Result**: **100% PASS** executed under `parallel_sprint_lock.py` (2s from cache / 1m 25s execution).
* **Living Documentation Governance**:
  * Command: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-45.13`
  * **Result**: **100% PASS** (all 9 domain shards synchronized, `REQ-MAP-036` and `TST-MAP-036` updated from `[Planned]` to `[Verified]`).

---

## 4. Acceptance Criteria Verification Table

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1 (Excision of Context Menu Home Actions)**: Neither "Als Heim-Basis festlegen" nor "Heim-Basis aufheben" in dropdown | Device Inspection + Contract Test | **MET** | `KnownLocationHomeContractTest.kt:68-80`, `ATT-6209_known_location_context_menu.png` |
| **AC-2 (Context Menu Retention)**: Context menu retains merge and delete actions | Device Inspection + Contract Test | **MET** | `KnownLocationHomeContractTest.kt:82-90`, `ATT-6209_known_location_context_menu.png` |
| **AC-3 (Edit Dialog Configuration Integrity)**: Home-Base switch row and description intact | Unit Test | **MET** | `EditKnownLocationDialogTest.kt:240-265` |
| **AC-4 (Title Icon Indicator Invariant)**: `Icons.Default.Home` next to card title preserved | Contract Test + Device Inspection | **MET** | `KnownLocationHomeContractTest.kt:52-59`, `ATT-6209_known_location_context_menu.png` |
| **AC-5 (Governance & Parity)**: Master tables synced, living docs verified | Governance Tool | **MET** | `verify_requirement_governance.py` PASS |
