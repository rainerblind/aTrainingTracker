# Stage 3: Implementation Plan - ATT-5399: Replace Centered AlertDialogs with AppModalBottomSheet Bottom Popups

**Ticket**: [ATT-5399](https://rainerblind.atlassian.net/browse/ATT-5399)  
**Sub-task**: [ATT-5415](https://rainerblind.atlassian.net/browse/ATT-5415) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5399`  
**Requirement Mapping**: `REQ-UI-149`, `REQ-SET-084`, `REQ-SET-088.5`, `REQ-REV-003`, `REQ-SET-082.5`  
**Test Mapping**: `TST-UI-102`, `TST-SET-084`, `TST-SET-088`, `TST-REV-003`, `TST-SET-082`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
User feedback from the Post-Sprint 2026-45.5 review highlighted that 4 recently introduced dialogs (`SwapOrUpgradeDialog.kt`, `GarageUpgradeNudgeDialog.kt`, `FirstJubileeCelebrationDialog.kt`, `ZoneHistoryDialog.kt`) were implemented as centered `AlertDialog`s instead of bottom popups (`AppModalBottomSheet`). This plan executes their atomic migration to `AppModalBottomSheet`, updates `docs/design_guidelines.md` (Sections 5.3 & 5.8), and authors contract unit tests enforcing zero centered `AlertDialog`s in interactive prompts.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-149` (Bottom popup standard), `REQ-SET-084` (Swap dialog), `REQ-SET-088.5` (Garage nudge), `REQ-REV-003` (Jubilee celebration), `REQ-SET-082.5` (Zone history)
* **Test Mapping**: `TST-UI-102` (`ModalBottomSheetDialogsIntegrityTest.kt`), `TST-SET-084`, `TST-SET-088`, `TST-REV-003`, `TST-SET-082`

---

## 3. System Invariants & Preserved Behavior
1. **Composition Retention (Rule 47)**: Dialogs remain rendered as overlays alongside underlying host screens without unmounting.
2. **Scroll Safety (Section 5.8)**: `ZoneHistoryDialog` passes `scrollable = false` to `AppModalBottomSheet` to host its internal `LazyColumn` safely without triggering infinite-height measurement exceptions.
3. **API & Callback Contracts**: Function signatures, parameters, and callback contracts (`onDismiss`, `onUpgrade`, `onSwapConfirmed`, `onRequestInAppReview`, `onSaveSnapshot`, etc.) are 100% preserved.
4. **Code Lock (Rule 4)**: No production code modified until Gate 3 is approved.

---

## 4. Proposed Architectural Changes
### Component 1: `SwapOrUpgradeDialog.kt`
* Replace `AlertDialog` with `AppModalBottomSheet`. Pass `icon = Icons.Default.SwapHoriz`, `title = stringResource(R.string.swap_dialog_title)`, and place `Button` (Swap), `FilledTonalButton` (Unlock), and `TextButton` (Cancel) in `actions`.

### Component 2: `GarageUpgradeNudgeDialog.kt`
* Replace `AlertDialog` with `AppModalBottomSheet`. Pass `icon = if (isDiamond) Icons.Default.Diamond else Icons.AutoMirrored.Filled.DirectionsBike`, `title = title`, body text in `content`, and upgrade / dismiss buttons in `actions`.

### Component 3: `FirstJubileeCelebrationDialog.kt`
* Replace `AlertDialog` with `AppModalBottomSheet`. Pass `icon = Icons.Filled.EmojiEvents`, `title = stringResource(R.string.jubilee_dialog_title, count, sportName)`, body text + supporter appeal card in `content`, and review/feedback/dismiss buttons in `actions`.

### Component 4: `ZoneHistoryDialog.kt`
* Replace `AlertDialog` with `AppModalBottomSheet(scrollable = false)` hosting internal `LazyColumn`. Sub-dialog `ZoneSnapshotEditDialog` converts to `AppModalBottomSheet(scrollable = true)`.

### Component 5: Style Guide & Contract Tests
* Update `docs/design_guidelines.md` (Sections 5.3 & 5.8) codifying bottom sheet requirement for interactive dialogs.
* Author `app/src/test/.../ModalBottomSheetDialogsIntegrityTest.kt` verifying `AppModalBottomSheet` adoption.

### UI Consistency (Rule 23)
* **Reference component**: `AppModalBottomSheet.kt` / `EditDeviceDialog.kt`
* **Reused components**: `AppModalBottomSheet`, Material 3 buttons and typography
* **Theme tokens**: `MaterialTheme.colorScheme`, `MaterialTheme.typography`
* **New one-off styles**: None

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Codify Style Guide in `docs/design_guidelines.md`
* Sections 5.3 and 5.8: Formally mandate `AppModalBottomSheet` for interactive dialogs.

### Step 2: Migrate `SwapOrUpgradeDialog.kt` & `GarageUpgradeNudgeDialog.kt`
* Replace `AlertDialog` with `AppModalBottomSheet` preserving icons, titles, and actions.

### Step 3: Migrate `FirstJubileeCelebrationDialog.kt` & `ZoneHistoryDialog.kt`
* Replace `AlertDialog` with `AppModalBottomSheet` (using `scrollable = false` for `ZoneHistoryDialog`).

### Step 4: Author Contract Tests & Execute Verification
* Author `ModalBottomSheetDialogsIntegrityTest.kt`. Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.*" --tests "com.atrainingtracker.trainingtracker.ui.dialogs.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.*"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit and contract tests, `TranslationParityTest`, and `verify_requirement_governance.py`.
* **Rollback**: `git checkout feature/ATT-5399 -- .` restores pristine state.
