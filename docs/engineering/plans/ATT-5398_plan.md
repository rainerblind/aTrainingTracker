# Stage 3: Implementation Plan - ATT-5398: Subscription Downgrade Sensor Quota Reconciliation

**Ticket**: [ATT-5398](https://rainerblind.atlassian.net/browse/ATT-5398)  
**Sub-task**: [ATT-5421](https://rainerblind.atlassian.net/browse/ATT-5421) (`[Impl-Plan]`)  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5398`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.5`, `REQ-SET-084.6`)  
**Test Mapping**: `TST-SET-084` (`TST-SET-084.5`..`TST-SET-084.8`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
When a Supporter subscription expires or drops to Free (`SupporterTier.FREE`, `!isProActive()`), athletes with $> 2$ previously paired sensors retain `PAIRED = 1` in `Devices.db`. Furthermore, `SwapOrUpgradeDialog` performs 1:1 swaps, leaving $> 2$ devices paired if multi-sensor state existed. This plan implements a non-destructive Keep-2-Sensors reconciliation bottom sheet (`AppModalBottomSheet`) and hardens `executeSwapAndPair()` to guarantee total paired sensors strictly $\le 2$ on Free tier.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-084.5` (*Downgrade Sensor Quota Reconciliation*) & `REQ-SET-084.6` (*Multi-Sensor Swap Hardening*).
* **Test Mapping**: `TST-SET-084.5` (Trigger & Default Selection), `TST-SET-084.6` (Batch Pruning & Data Preservation), `TST-SET-084.7` (Swap Hardening $\le 2$), `TST-SET-084.8` (9-Locale Parity).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Data Deletion**: Unpaired sensors MUST NEVER be deleted from `Devices.db`; custom names, calibration, and equipment links remain 100% intact.
2. **Zero In-Ride Disruption**: Reconciliation prompts and pruning are strictly gated on `!TrainingApplication.isTracking()`.
3. **No Quota Bypass on Dismissal**: Dismissing the sheet automatically applies the 2 pre-selected default sensors.
4. **M3 Bottom Sheet Standard**: Built with `AppModalBottomSheet` per `ATT-5399` (`docs/design_guidelines.md` 5.3 & 5.8).

---

## 4. Proposed Architectural Changes

### Component 1: Repository & Database (`DeviceDataRepository.kt`)
* Add `reconcilePairedDevices(keepDeviceIds: Set<Long>)`: fetches all paired devices and unpairs any device not in `keepDeviceIds` via `updateDevice(copy(isPaired = false))` (sending `PAIRING_CHANGED` broadcasts).

### Component 2: ViewModel (`DeviceListViewModel.kt`)
* Expose `keepTwoSensorsDialogEvent: StateFlow<KeepTwoSensorsDialogEvent?>`.
* Add `checkDowngradeReconciliation()`: evaluates `!proFeatureManager.isProActive.value && pairedDevices.size > 2 && !TrainingApplication.isTracking()`, preselecting GPS + Barometer (or 2 most recent) as defaults.
* Add `confirmKeepTwoSensors(selectedIds: Set<Long>)` and `dismissKeepTwoSensors()` (applying defaults).
* Harden `executeSwapAndPair(unpairDeviceId, candidateDeviceId)`: in Free mode, unpairs all paired devices except candidate and the 1 retained device, enforcing count strictly $\le 2$.

### Component 3: UI Presentation (`KeepTwoSensorsBottomSheet.kt` & `DevicesTabbedScreen.kt`)
* Author `KeepTwoSensorsBottomSheet` using `AppModalBottomSheet` with `Icons.Default.Devices`, checkboxes (max 2 selectable), "Confirm", and "Unlock Unlimited" (`NavRoutes.SUPPORTER`) actions.
* Host alongside `DevicesTabbedScreen` (and trigger check on resume).

### UI Consistency (Rule 23)
* **Reference component**: `SwapOrUpgradeDialog.kt` (`AppModalBottomSheet`).
* **Reused components**: `AppModalBottomSheet`, `Checkbox`, `HorizontalDivider`, `FilledTonalButton`, `Button`.
* **Theme tokens**: `MaterialTheme.colorScheme.primaryContainer`, `outlineVariant`, `surface`. Spacing: `16.dp`, `8.dp`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Repository Pruning & Swap Hardening (`REQ-SET-084.5`, `REQ-SET-084.6`)
* Files: `DeviceDataRepository.kt`, `DeviceListViewModel.kt`.
* Changes: Implement `reconcilePairedDevices()`; add `KeepTwoSensorsDialogEvent`, reconciliation checks, and multi-sensor pruning in `executeSwapAndPair()`.

### Step 2: Reconciliation Bottom Sheet & UI Wiring (`REQ-SET-084.5`, `REQ-UI-149`)
* Files: `KeepTwoSensorsBottomSheet.kt`, `DevicesTabbedScreen.kt`.
* Changes: Author composable sheet with max-2 checkbox selection; wire into `DevicesTabbedScreen`.

### Step 3: 9-Locale Localization Resources (`REQ-SET-084.5`)
* Files: `app/src/main/res/values*/strings.xml` (all 9 supported locales).
* Changes: Add `keep_two_sensors_title`, `keep_two_sensors_message`, `keep_two_sensors_action_confirm`, `keep_two_sensors_action_unlock`.

### Step 4: Unit Testing & Governance Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`
* Script: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`TST-SET-084.5`..`8`), compose previews, and `verify_requirement_governance.py`.
* **Rollback**: Atomic feature branch `feature/ATT-5398` allows immediate revert without affecting `sprint/2026-45.6`.
