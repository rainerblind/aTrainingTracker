# Stage 1 Analysis: ATT-5399 - Replace Centered AlertDialogs with AppModalBottomSheet Bottom Popups

**Ticket**: [ATT-5399](https://rainerblind.atlassian.net/browse/ATT-5399)  
**Sub-task**: [ATT-5413](https://rainerblind.atlassian.net/browse/ATT-5413) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5399`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
Post-Sprint 2026-45.5 review user feedback (*"Die neu eingeführten Dialoge sind klassische Dialoge, die in der Mitte des Bildschirms aufpoppen... durch bottom popups zu ersetzen"*) identified 4 recently added dialogs (`SwapOrUpgradeDialog.kt`, `GarageUpgradeNudgeDialog.kt`, `FirstJubileeCelebrationDialog.kt`, `ZoneHistoryDialog.kt`) implemented as centered Material 3 `AlertDialog`s. Centered dialogs violate one-handed thumb ergonomics and contradict the app's standard (`REQ-UI-149`), which mandates `AppModalBottomSheet` bottom popups for interactive prompts and editors.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Scaffolding Inconsistency RCA**: During initial feature construction across Sprints 2026-41..45, centered `AlertDialog`s were used as expedient initial wrappers rather than integrating with `AppModalBottomSheet`.
* **Design Guidelines Gap**: `docs/design_guidelines.md` (Sections 5.3 & 5.8) documented bottom sheet usage for maps and live climbs, but lacked an explicit prohibition against centered `AlertDialog`s for selection prompts, nudges, and multi-field editors.
* **Independent Peer Review & Verification**:
  * Audited `SwapOrUpgradeDialog.kt`: Currently uses `AlertDialog`; cleanly adapts to `AppModalBottomSheet` with `icon = Icons.Default.SwapHoriz` and bottom actions slot.
  * Audited `GarageUpgradeNudgeDialog.kt`: Currently uses `AlertDialog`; cleanly adapts to `AppModalBottomSheet` with `icon = if (isDiamond) Diamond else DirectionsBike`.
  * Audited `FirstJubileeCelebrationDialog.kt`: Currently uses `AlertDialog`; cleanly adapts to `AppModalBottomSheet` with `icon = Icons.Filled.EmojiEvents`.
  * Audited `ZoneHistoryDialog.kt`: Contains a `LazyColumn` for historical snapshots. **Critical Invariant**: MUST pass `scrollable = false` to `AppModalBottomSheet` to prevent infinite-height constraint measurement crashes (Section 5.8). Sub-dialog `ZoneSnapshotEditDialog` must also convert to `AppModalBottomSheet(scrollable = true)`.
  * Audited callers (`DevicesTabbedScreen`, `EquipmentTabsScreen`, `ATrainingTrackerApp`, `ZonesSettingsActivity`, `AthleteProfileScreen`): All dialogs are already hoisted as overlays alongside the host screens, preserving Rule 47 composition retention.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Convert `SwapOrUpgradeDialog.kt` to `AppModalBottomSheet`.
  * Convert `GarageUpgradeNudgeDialog.kt` to `AppModalBottomSheet`.
  * Convert `FirstJubileeCelebrationDialog.kt` to `AppModalBottomSheet`.
  * Convert `ZoneHistoryDialog.kt` and `ZoneSnapshotEditDialog` to `AppModalBottomSheet` (with `scrollable = false` on the history list).
  * Codify in `docs/design_guidelines.md` (Sections 5.3 & 5.8) that interactive popups, nudges, celebration alerts, and editors MUST use `AppModalBottomSheet`, reserving `AlertDialog` strictly for short 1–2 sentence destructive confirmations (`DeleteConfirmationDialog`).
* **Out-of-Scope Non-Goals**:
  * `SupporterQuickIconPickerDialog.kt` (retired in `ATT-5397`).
  * Downgrade Keep-2-Sensors dialog (authored directly as `AppModalBottomSheet` in `ATT-5398`).
  * Changes to view models, navigation routes, DataStore keys, or database schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-149`, `REQ-SET-084`, `REQ-SET-088.5`, `REQ-REV-003`, `REQ-SET-082.5`, targeting `AppModalBottomSheet` migration across 4 dialog components.
* **Historical Origin & Commit Trace**: `REQ-UI-149` established in Sprint `2026-41.1` (`ATT-900`).
* **Root Reason for Existing Formulation**: `AlertDialog` was used for initial MVP velocity before standardizing on `AppModalBottomSheet`.
* **Preservation of Core Invariants**: Retains 100% of callback contracts, dismiss logic, navigation targets, and input validation while elevating visual consistency to Material 3 bottom sheets.

---

## 5. Architectural Strategy & High-Level Solution
* Replace `AlertDialog` containers with `AppModalBottomSheet` across all 4 dialog components.
* Map header icon vectors to `icon`, localized title strings to `title`, confirm/dismiss actions to `actions`, and form bodies to `content`.
* Enforce `scrollable = false` on `ZoneHistoryDialog` to support its internal `LazyColumn` safely.
* Update `docs/design_guidelines.md` and verify with Compose / contract unit tests.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Modal composition retention: Sheets render as overlays alongside the host screen without unmounting (Rule 47).
  2. Scroll safety: Zero nested `verticalScroll` inside `scrollable = true` containers (Section 5.8).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (Declarative Compose container refactoring; zero database, sensor, or business logic risk).
