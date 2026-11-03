# Stage 1 Analysis: ATT-5189 - [Verbesserung] CopyTrackingTabDialog: do not preselect current activity type (all unselected by default)

**Ticket**: [ATT-5189](https://rainerblind.atlassian.net/browse/ATT-5189)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Branch**: `feature/ATT-5189`  
**Author**: AI Agent 1 (pro / Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
- In Sprint 2026-45.1, ATT-5078 introduced `CopyTrackingTabDialog` allowing athletes to duplicate a custom tracking tab into target sport profiles (`ActivityType`).
- However, when opening the bottom sheet, the source activity profile is preselected by default (`selectedTypes = setOf(sourceActivityType)`).
- Preselecting the current sport type causes UX confusion: athletes typically copy tabs across *different* sport types (e.g., sharing a polished Cycling dashboard with Mountain Biking or Running), not duplicate tabs within the current sport. Accidental confirmation inadvertently creates unwanted duplicate tabs in the active sport type.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Code Audit & Root Cause**:
  - In `CopyTrackingTabDialog.kt:100`: `var selectedTypes by remember { mutableStateOf(setOf(sourceActivityType)) }` pre-populates the selection with the source sport type.
  - Because `saveEnabled = selectedTypes.isNotEmpty()` (line 111), the Copy action button is immediately enabled upon open, allowing accidental immediate duplication without explicit user intent.
- **Independent Peer Review & Verification**:
  - Audited `CopyTrackingTabDialog.kt:95-113`: Changing initial state to `emptySet<ActivityType>()` guarantees all checkboxes start unchecked and `saveEnabled` evaluates to `false` initially.
  - Audited `TrackingTabsScreen.kt:1034-1045`: The dialog call site passes `sourceActivityType = currentActivityType`. Preserving `sourceActivityType` in the composable signature ensures full binary and source compatibility across caller sites.

---

## 3. Chesterton's Fence Archaeology & Side-Effect Impact Audit (Rule 68)
- **Original Requirement ID & Target**: Amends `REQ-UI-439.2` (*Target ActivityType Selection Dialog*), Parent Epic `ATT-355`. Targets `CopyTrackingTabDialog.kt`.
- **Historical Origin & Commit Trace**: Sprint 2026-45.1 commit `82bf905d` (ATT-5078) added the initial multi-select bottom sheet implementation with default `setOf(sourceActivityType)`.
- **Root Reason for Existing Formulation**: Preselection was introduced as a placeholder default during initial bottom sheet authoring, without considering cross-sport copying ergonomics.
- **Preservation of Core Invariants**: Multi-selection of any `ActivityType` (including the source type) remains fully supported; deep copy and append semantics (`REQ-UI-439.3`) and 9-language localization (`REQ-UI-439.4`) remain 100% intact.
- **Cross-Caller & Side-Effect Impact Audit**: Only caller is `TrackingTabsScreen.kt:1037`. Zero database queries, background services, or sensor components are affected.

---

## 4. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Initialize `selectedTypes` to `emptySet()` in `CopyTrackingTabDialog.kt`.
  2. Keep the Confirm / Copy button disabled when `selectedTypes.isEmpty()`.
  3. Add automated contract/unit test in `TrackingTabConfigHeaderContractTest.kt` verifying unselected default and disabled button contract.
- **Out-of-Scope Non-Goals**:
  - Modifying SQLite transactional deep-copy logic in `TrackingViewsDatabaseManager.java`.
  - Reordering or filtering the list of `ActivityType` values.

---

## 5. Architectural Strategy & High-Level Solution
- **UI State Layer (`CopyTrackingTabDialog.kt`)**:
  - Update `var selectedTypes by remember { mutableStateOf(emptySet<ActivityType>()) }`.
  - Existing `saveEnabled = selectedTypes.isNotEmpty()` automatically enforces that the user must select at least one activity type before the action button activates.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**: Zero preselected items upon dialog open; Copy button disabled while `selectedTypes.isEmpty()`; transactional integrity of tab duplication preserved.
- **Risk Rating**: **LOW**. 1-line Compose state default change and contract test update; zero schema or concurrency risk.
