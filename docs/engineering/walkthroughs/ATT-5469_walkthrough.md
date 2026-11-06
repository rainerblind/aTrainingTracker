# Stage 5: Walkthrough & Verification - ATT-5469: Subscription Downgrade Sensor Quota Global Reconciliation

**Ticket**: [ATT-5469](https://rainerblind.atlassian.net/browse/ATT-5469)  
**Sub-task**: [Test]  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5469`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.5`, `REQ-SET-084.7`)  
**Test Mapping**: `TST-SET-084` (`TST-SET-084.9`..`TST-SET-084.12`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary & Verification Overview
Expanded Free-tier sensor quota downgrade reconciliation (`REQ-SET-084.5`) beyond `DeviceListViewModel` to trigger globally across the app. Created `SensorQuotaReconciliationManager` as a thread-safe singleton domain service. Hoisted `KeepTwoSensorsBottomSheet` to the root Compose tree (`ATrainingTrackerApp.kt`), triggered by `LaunchedEffect(isProActive)`, `MainActivityWithNavigation.onResume()`, `TrackingTabsScreen`, and `ControlTrackingViewModel` (pre-tracking start/search). Implemented defense-in-depth connection clamping in `DeviceManager.java` (`REQ-SET-084.7`) ensuring hardware search queue and remote device creation enforce `MAX_FREE_PAIRED_DEVICES = 2` on Free tier.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-084.5` | `TST-SET-084.9` | Unit (`SensorQuotaReconciliationManagerTest` - 6 tests) | **PASSED** | `Verified` |
| `REQ-SET-084.5` | `TST-SET-084.10` | Unit (`DeviceListViewModelQuotaTest` - 9 tests) | **PASSED** | `Verified` |
| `REQ-SET-084.5` | `TST-SET-084.11` | Unit (`ControlTrackingViewModelQuotaTest` - 4 tests) | **PASSED** | `Verified` |
| `REQ-SET-084.7` | `TST-SET-084.12` | Unit (`DeviceManagerQuotaClampingTest` - 2 tests) | **PASSED** | `Verified` |
| `REQ-UI-149` | Theme Audit | `ThemeColorCentralizationAuditTest` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence (Tier 1 Targeted Scope)
* **Scope**: Targeted unit and contract tests across modified packages:
  - `com.atrainingtracker.trainingtracker.monetization.*` (Passed)
  - `com.atrainingtracker.trainingtracker.ui.tracking.*` (Passed)
  - `com.atrainingtracker.banalservice.*` (Passed)
  - `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest` (Passed)
  - `com.atrainingtracker.trainingtracker.build.*` (Passed)
  - `verify_requirement_governance.py` (100% parity across all 9 domain shards)
```text
BUILD SUCCESSFUL in 1m 39s
32 actionable tasks: 1 executed, 31 up-to-date
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified.
```

---

## 4. UI & Visual Consistency (Rule 23)
* **Hoisted Component**: `KeepTwoSensorsBottomSheet` hosted in root `ATrainingTrackerApp.kt` over all screens.
* **Excised Duplicate**: Local bottom sheet invocation removed from `DevicesTabbedScreen.kt` preventing double-rendering.
* **On-Device Physical Verification**: Skipped per Rule 60 (no physical device or emulator connected on ADB; headless CI).

---

## 5. Invariant & Governance Verification
1. **In-Ride Protection**: Reconciliation is strictly suppressed during active recording (`!TrainingApplication.isTracking()`).
2. **Non-Destructive Batch Pruning**: Unselected sensors have `PAIRED = 0` set in `Devices.db` while preserving names, calibrations, and equipment links.
3. **Defense-in-Depth Hardware Clamping**: `DeviceManager` clamps search queue and paired device instantiation to $\le 2$ on Free tier.
4. **Governance Parity**: `docs/architecture.md` Section 2 & 4.7 updated; `verify_requirement_governance.py` verified 100% parity.
