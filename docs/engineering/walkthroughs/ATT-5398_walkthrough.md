# Stage 5: Walkthrough & Verification - ATT-5398: Subscription Downgrade Sensor Quota Reconciliation

**Ticket**: [ATT-5398](https://rainerblind.atlassian.net/browse/ATT-5398)  
**Sub-task**: [Test]  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5398`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.5`, `REQ-SET-084.6`)  
**Test Mapping**: `TST-SET-084` (`TST-SET-084.5`..`TST-SET-084.8`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-04  

---

## 1. Executive Summary & Verification Overview
Implemented Subscription Downgrade Sensor Quota Reconciliation (`REQ-SET-084.5`) and Multi-Sensor Swap Hardening (`REQ-SET-084.6`). When returning to Free tier with $> 2$ paired sensors outside active tracking, `DeviceListViewModel` emits `keepTwoSensorsDialogEvent` to display `KeepTwoSensorsBottomSheet` (`AppModalBottomSheet`), preselecting 2 defaults (`GPS` + `ALTITUDE_FROM_PRESSURE`). Confirming or dismissing the sheet non-destructively sets `PAIRED = 0` on unselected devices via `DeviceDataRepository.reconcilePairedDevices()`. All calibration and equipment links remain 100% intact. `executeSwapAndPair()` is hardened to prune excess sensors so total paired count strictly resolves to $\le 2$.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-084.5` | `TST-SET-084.5` | Unit (`testDowngradeDetection_triggersReconciliationWithDefaults`) | **PASSED** | `Verified` |
| `REQ-SET-084.5` | `TST-SET-084.6` | Unit (`testConfirmKeepTwoSensors`, `testDismissKeepTwoSensors`) | **PASSED** | `Verified` |
| `REQ-SET-084.6` | `TST-SET-084.7` | Unit (`testMultiSensorSwap_hardensExcessSensorsToStrictlyTwo`) | **PASSED** | `Verified` |
| `REQ-SET-084.5` | `TST-SET-084.8` | `TranslationParityTest` (9 locales: EN, DE, ES, FR, IT, JA, NL, PL, PT) | **PASSED** | `Verified` |
| `REQ-UI-149` | Theme Audit | `ThemeColorCentralizationAuditTest` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence (Tier 1 Targeted Scope)
* **Scope**: Targeted unit and contract tests across modified packages:
  - `com.atrainingtracker.banalservice.ui.devices.devicelist.*` (14 unit tests passed, including 5 new quota/downgrade tests)
  - `com.atrainingtracker.trainingtracker.TranslationParityTest` (100% parity across all 9 locales)
  - `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest` (Passed)
  - `com.atrainingtracker.trainingtracker.build.*` (Passed)
```text
BUILD SUCCESSFUL in 1s
32 actionable tasks: 1 from cache, 31 up-to-date
```

---

## 4. UI & Visual Consistency (Rule 23)
* **Reference Component**: `SwapOrUpgradeDialog.kt` (`AppModalBottomSheet`).
* **Implementation**: `KeepTwoSensorsBottomSheet.kt` leverages canonical `AppModalBottomSheet` with `Icons.Default.Devices`, reactive checkbox selection (max 2), "Confirm" button (`keep_two_sensors_action_confirm`), and direct upsell shortcut (`keep_two_sensors_action_unlock`).
* **Theme Tokens**: `MaterialTheme.colorScheme.primaryContainer`, `surface`, `outlineVariant`, `onSurfaceVariant`.

---

## 5. Invariant & Governance Verification
1. **Data Preservation**: Unpaired devices are never deleted from SQLite; names, calibration, and equipment links remain intact.
2. **In-Ride Protection**: Gated on `!isTrackingProvider.invoke()` so active workouts are never disrupted.
3. **Quota Integrity**: Dismissal automatically prunes to the 2 sensible defaults; Free quota cannot be bypassed.
4. **Governance**: `python3 tools/verify_requirement_governance.py` confirmed exit code 0 (`PASS: Requirement Archaeology verified`).
