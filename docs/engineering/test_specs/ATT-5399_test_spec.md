# Stage 2: Requirement & Test Specification - ATT-5399: Replace Centered AlertDialogs with AppModalBottomSheet Bottom Popups

**Ticket**: [ATT-5399](https://rainerblind.atlassian.net/browse/ATT-5399)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5399`  
**Requirement Mapping**: `REQ-UI-149`, `REQ-SET-084`, `REQ-SET-088.5`, `REQ-REV-003`, `REQ-SET-082.5`  
**Test Spec ID**: `TST-UI-102`, `TST-SET-084`, `TST-SET-088`, `TST-REV-003`, `TST-SET-082`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-UI-149 & Domain Extensions)

### 1.1 Problem Statement & Rationale
Post-Sprint 2026-45.5 review user feedback highlighted that four recently added interactive dialogs (`SwapOrUpgradeDialog`, `GarageUpgradeNudgeDialog`, `FirstJubileeCelebrationDialog`, `ZoneHistoryDialog`) were implemented as centered `AlertDialog`s. Centered dialogs break one-handed thumb reachability and violate `REQ-UI-149`, which mandates `AppModalBottomSheet` bottom popups for interactive prompts, editors, and nudges.

### 1.2 Functional & Architectural Requirements
1. **Container Migration to `AppModalBottomSheet` (`REQ-UI-149`)**:
   - `SwapOrUpgradeDialog.kt`: SHALL compose `AppModalBottomSheet` with `icon = Icons.Default.SwapHoriz` and bottom actions slot (`REQ-SET-084`).
   - `GarageUpgradeNudgeDialog.kt`: SHALL compose `AppModalBottomSheet` with `icon = if (isDiamond) Diamond else DirectionsBike` (`REQ-SET-088.5`).
   - `FirstJubileeCelebrationDialog.kt`: SHALL compose `AppModalBottomSheet` with `icon = Icons.Filled.EmojiEvents` (`REQ-REV-003`).
   - `ZoneHistoryDialog.kt`: SHALL compose `AppModalBottomSheet` with `scrollable = false` for the outer sheet, enclosing its internal `LazyColumn` safely (`REQ-SET-082.5`). `ZoneSnapshotEditDialog` SHALL compose `AppModalBottomSheet(scrollable = true)`.
2. **Design Guidelines Codification**: `docs/design_guidelines.md` (Sections 5.3 & 5.8) SHALL codify that interactive prompts, nudges, celebrations, and editors must be bottom popups (`AppModalBottomSheet`), reserving centered `AlertDialog` strictly for short 1–2 sentence destructive confirmations.
3. **Invariants**: All existing callback signatures, dismiss handlers, parameter contracts, and navigation routes SHALL remain 100% backward compatible.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-149`, `REQ-SET-084`, `REQ-SET-088.5`, `REQ-REV-003`, `REQ-SET-082.5`, targeting `AppModalBottomSheet` container migration for 4 dialogs.
* **Historical Origin & Commit Trace**: `REQ-UI-149` established in Sprint `2026-41.1` (`ATT-900`).
* **Root Reason for Existing Formulation**: Expedient initial implementation velocity during MVP construction used standard `AlertDialog` before design system bottom sheet consolidation.
* **Preservation of Core Invariants**: Retains 100% of callback signatures, dismiss logic, navigation targets, and input validation while elevating mobile ergonomics and edge-to-edge Material 3 styling.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Bottom Popup Presentation)**: *Given* any of the 4 dialogs is triggered, *When* displayed on screen, *Then* it renders as an `AppModalBottomSheet` anchored to the screen bottom with standard handle and edge-to-edge navigation padding.
* **Criterion 2 (LazyColumn Measurement Safety)**: *Given* `ZoneHistoryDialog` with multiple zone snapshots, *When* presented, *Then* `AppModalBottomSheet` receives `scrollable = false`, preventing infinite height measurement exceptions and supporting internal scroll.

---

## 2. Test Specification (TST-UI-102 & Domain Extensions)

### Test Case 1: Composable Structure & Contract Test (`ModalBottomSheetDialogsIntegrityTest.kt`)
* **Scope**: UI / Component Contract Unit Test
* **Target Files**: `app/src/test/.../ModalBottomSheetDialogsIntegrityTest.kt`
* **Action**: Assert that `SwapOrUpgradeDialog`, `GarageUpgradeNudgeDialog`, `FirstJubileeCelebrationDialog`, and `ZoneHistoryDialog` invoke `AppModalBottomSheet` and pass correct icon, title, and actions parameters.
* **Expected Result**: 100% PASS with zero centered `AlertDialog` calls in those 4 components.

### Test Case 2: Regression Suite (`TST-UI-102.REG`)
* **Scope**: Targeted unit tests (`ui.devices.*`, `ui.settings.*`, `ui.dialogs.*`, `ThemeColorCentralizationAuditTest`)
* **Expected Result**: 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-102` | Contract | `ModalBottomSheetDialogsIntegrityTest.kt` | `REQ-UI-149` | Specified |
| `TST-SET-084` | Unit / UI | `SwapOrUpgradeDialog.kt` | `REQ-SET-084` | Specified |
| `TST-SET-088` | Unit / UI | `GarageUpgradeNudgeDialog.kt` | `REQ-SET-088.5` | Specified |
| `TST-REV-003` | Unit / UI | `FirstJubileeCelebrationDialog.kt` | `REQ-REV-003` | Specified |
| `TST-SET-082` | Unit / UI | `ZoneHistoryDialog.kt` | `REQ-SET-082.5` | Specified |
