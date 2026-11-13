# Stage 1 Analysis: ATT-7535 - Reorder Settings section in Navigation Drawer

**Ticket**: [ATT-7535](https://rainerblind.atlassian.net/browse/ATT-7535)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7535` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
In the *Einstellungen* (*Settings*) group of `AppNavigationDrawer.kt`, the 7 items are currently ordered: Tracking Tabs, Sportarten, Trainings Zonen, Auto-Pause, Display, Maßeinheiten, Experten-Einstellungen. This mixes cockpit layout with taxonomy and fragments general tracking preferences. Reordering to (1) Trainings Zonen, (2) Sportarten, (3) Tracking Tabs, (4) Display, (5) Auto-Pause, (6) Maßeinheiten, (7) Experten-Einstellungen establishes an intuitive cognitive hierarchy: athlete physiology -> sport taxonomy -> live cockpit screens -> tracking behavior -> units -> advanced diagnostics.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Investigation**:
  1. *Declarative List Declaration*: `AppNavigationDrawer.kt:229-241` declares `items` in `createDrawerGroups` directly in sequence. UI rendering iterates over this list sequentially.
  2. *Historical Evolution*: `drawer_training_zones` was introduced in `ATT-6720` (`REQ-UI-513.2`) at position 3 behind Sportarten. Tracking Layouts remained at position 1 from legacy grouping (`ATT-4289`).
* **Independent Peer Review & Verification**:
  * Verified in `AppNavigationDrawer.kt#L229-L241`: Reordering `items` cleanly changes drawer display order with zero impact on ID-based click dispatching (`onItemSelected`) or auto-expansion logic (`isDrawerGroupInitiallyExpanded`).
  * *Blind Spot Uncovered*: In addition to `NavigationDrawerStateTest.kt#L142-L158` (which asserts exact item list sequence), `AutoPauseNavigationTest.kt#L70-L79` explicitly asserts relative position invariants (`sportTypesIndex + 1 == trainingZonesIndex` and `trainingZonesIndex + 1 == autoPauseIndex`). Both test suites must be updated to reflect the new sequence.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Reorder the 7 items inside `DrawerGroup(titleRes = R.string.drawer__settings)` in `AppNavigationDrawer.kt`.
  2. Update unit test assertions in `NavigationDrawerStateTest.kt` and `AutoPauseNavigationTest.kt`.
  3. Maintain 100% contract compliance and backward compatibility for all drawer destinations.
* **Out-of-Scope Non-Goals**:
  * Modifying routes, IDs, icons, string labels, or bottom sheet resolution in `NavRoutes.kt`.
  * Modifying non-Settings drawer groups (*Training*, *Maps*, *My Stuff*, *Online*, *Info & Support*).
  * Changing destination screens or settings dialog implementations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-UI-513.2` (*Settings Drawer Training Zones Integration*) in `docs/requirements/req_ui_design_system.md`.
* **Historical Origin & Commit Trace**: Commit `83e5f529` (`ATT-6720`, Sprint 2026-46.3).
* **Root Reason for Existing Formulation**: `ATT-6720` integrated `drawer_training_zones` after `drawer_sport_types` to preserve legacy `drawer_tracking_layouts` at index 0.
* **Preservation of Core Invariants**: All 7 settings items, item IDs, icons, string titles, and collapsible group behavior remain identical; only presentation order is optimized.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: Refinement of `REQ-UI-513.2` in `docs/requirements/req_ui_design_system.md`.
* **Affected Architecture Elements**: `AppNavigationDrawer.kt` (`createDrawerGroups`).
* **Verification Impact & Test Surface**: `NavigationDrawerStateTest.kt`, `AutoPauseNavigationTest.kt`, `AppNavigationDrawerTest.kt` (`TST-UI-513.1`).
* **Risk Assessment & Invariants**: LOW risk. Pure declarative list reordering without logic or schema changes. Drawer selection, routing, and auto-expansion invariants preserved.

---

## 6. Architectural Strategy & High-Level Solution
1. **Drawer Definition (`AppNavigationDrawer.kt`)**: Update `createDrawerGroups()` to order the `drawer__settings` list: `drawer_training_zones`, `drawer_sport_types`, `drawer_tracking_layouts`, `drawer_display_settings`, `drawer_auto_pause`, `drawer_units`, `drawer_advanced_tuning`.
2. **Test Alignment**: Update expected order in `NavigationDrawerStateTest.kt` and relative adjacency checks in `AutoPauseNavigationTest.kt`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Drawer item count = 27 total (7 in Settings); all IDs map to correct routes; selection and initial expansion remain intact.
* **Risk Rating**: **LOW** (pure declarative ordering change verified by targeted unit tests).
