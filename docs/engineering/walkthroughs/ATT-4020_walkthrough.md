# Stage 5 Walkthrough & Verification: ATT-4020

**Ticket**: [ATT-4020](https://rainerblind.atlassian.net/browse/ATT-4020)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*Stability & Robustness*)  
**Active Sprint**: `sprint/2026-43.5`  
**Requirement Mapping**: `REQ-STB-019` (`REQ-STB-019.1` – `REQ-STB-019.6`)  
**Test Mapping**: `TST-STB-019` (`TST-STB-019.1` – `TST-STB-019.7`)  
**Branch**: `feature/ATT-4020` | **Author**: AI Agent 1 | **Date**: 2026-10-21  

---

## 1. Executive Summary & Verification Overview

Whole-app architecture review identified 5 defect patterns in `MainActivityWithNavigation.kt` and `TrainingApplication.java`:
1. **Service Binding & Runnable Leaks**: `onDestroy` skipped `disconnectFromBANALService()` on rotation and leaked 5-min delayed runnables.
2. **Unscoped Coroutine Launches**: `CoroutineScope(Dispatchers.IO)` captured the Activity instance.
3. **In-Ride Battery State Loss**: Deduplication reset on rotation, and monitoring cancelled on `onPause` during tracking.
4. **Synchronous Main-Thread SQLite**: `getAllFilterData`, `hasUnfinishedWorkout`, and `trackLocation` blocked UI.
5. **Raw Dialog Window Leak**: Google Play Services error dialog leaked windows on recreation.

All 5 defects were remediated and verified under `REQ-STB-019` with zero regressions.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Test Class | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-STB-019.1` | `TST-STB-019.1` | Unconditional unbind & runnable cancel (`MainActivityLifecycleTest`) | **PASS** | `Verified` |
| `REQ-STB-019.2` | `TST-STB-019.2` | Lifecycle-bound coroutine cancellation (`MainActivityLifecycleTest`) | **PASS** | `Verified` |
| `REQ-STB-019.3` | `TST-STB-019.3` | Companion alert retention & onPause monitoring (`MainActivityLifecycleTest`) | **PASS** | `Verified` |
| `REQ-STB-019.4` | `TST-STB-019.4` | Asynchronous SQLite execution on IO dispatcher (`MainActivityLifecycleTest`) | **PASS** | `Verified` |
| `REQ-STB-019.5` | `TST-STB-019.5` | Safe Play Services dialog dismissal in onDestroy (`MainActivityLifecycleTest`) | **PASS** | `Verified` |
| `REQ-STB-019.6` | `TST-STB-019.7` | Clean-room activity regression suite (`com.atrainingtracker...activities.*`) | **PASS** (36/36) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Activity Test Suite (`testDebugUnitTest`)
```text
com.atrainingtracker.trainingtracker.activities.MainActivityLifecycleTest (12 tests) - ALL PASSED
com.atrainingtracker.trainingtracker.activities.MainActivityIntentLifecycleTest (10 tests) - ALL PASSED
com.atrainingtracker.trainingtracker.activities.LocationPermissionSafetyTest (7 tests) - ALL PASSED
com.atrainingtracker.trainingtracker.activities.MainActivityStartupPermissionContractTest (3 tests) - ALL PASSED
com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest (4 tests) - ALL PASSED

BUILD SUCCESSFUL (36/36 tests passed)
```

---

## 4. Invariant & Governance Verification

- **Requirement Governance**: `python3 tools/verify_requirement_governance.py` passed with 0 errors.
- **Living Documentation**: `REQ-STB-019` and `TST-STB-019` updated to `Verified`.
- **System Invariants**: Zero SQLite schema changes; battery alert thresholds (20%/10%) strictly preserved; `BANALService.GetBanalServiceInterface` preserved.
- **Core Rule 8**: Zero git commits created on `feature/ATT-4020`. All work retained in uncommitted working tree.
