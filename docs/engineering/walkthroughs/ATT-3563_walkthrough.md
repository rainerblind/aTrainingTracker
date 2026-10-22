# Stage 5: Walkthrough & Verification - ATT-3563: Harden Service Launches and BroadcastReceiver Unregistration Against Android Lifecycle Exceptions

**Ticket**: [ATT-3563](https://rainerblind.atlassian.net/browse/ATT-3563)  
**Sub-task**: [ATT-3664](https://rainerblind.atlassian.net/browse/ATT-3664) (`[Test] Verify LifecycleUtils, execute clean-room regression, and document walkthrough`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-STB-016` (*Guarded Service Launches and Idempotent BroadcastReceiver Teardown Across Lifecycle Boundaries*)  
**Test Mapping**: `TST-STB-016` (*Lifecycle Safety, Guarded Service Launches & Idempotent Receiver Teardown Verification Suite*)  
**Branch**: `feature/ATT-3563`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary & Verification Overview

Prior to ATT-3563, background execution restrictions (Android 8.0+ API 26+) and lifecycle race conditions could crash the application with:
1. `IllegalStateException` / `BackgroundServiceStartNotAllowedException` / `ForegroundServiceStartNotAllowedException` / `SecurityException` when attempting unshielded `Context.startService` invocations from activities or background state (e.g. `MainActivityWithNavigation`, `BANALServiceRepository`, `TrainingApplication`).
2. `IllegalArgumentException: Receiver not registered` or `IllegalStateException` when tearing down `BroadcastReceiver`s across activities, fragments, services, viewmodels, and sensor devices during out-of-order lifecycle transitions (e.g. `onPause`, `onStop`, `onDestroy`, `onCleared`, or reactive flow cancellation).

ATT-3563 resolves this across the entire codebase through a robust, four-tier architecture:
1. **Centralized Lifecycle Safety Engine (`LifecycleUtils.kt`)**:
   - `safeStartService(context, intent, tag)`: Safely calls `context.startService(intent)`, catching `IllegalStateException`, `SecurityException`, and generic `Throwable`. Emits a structured diagnostic warning (`Log.w`) and returns `ComponentName?` (or `null` on failure).
   - `safeUnregisterReceiver(context, receiver, tag)`: Safely calls `context.unregisterReceiver(receiver)`, catching `IllegalArgumentException`, `IllegalStateException`, and generic `Throwable`. Emits a structured diagnostic warning (`Log.w`) detailing the receiver's runtime class and returns `Boolean` (`true` on success, `false` on failure).
   - Defensive logging wrapper `logWarning` resilient against unmocked `android.util.Log` in local JVM test runners.
   - Null-safe Kotlin extension functions `Context?.safeStartService(...)` and `Context?.safeUnregisterReceiver(...)`.
2. **Comprehensive Call Site Migration (Zero Unguarded Sites Remaining)**:
   - 4 service launch sites across `MainActivityWithNavigation.kt`, `BANALServiceRepository.kt`, and `TrainingApplication.java`.
   - 35+ receiver unregistration sites across activities (`MainActivityWithNavigation`), fragments (`ConfigViewsFragment`, `EquipmentFragment`), repositories (`ExportStatusRepository`), UI composables (`SensorGridScreen`), view models (`DevicesTabbedViewModel`), background services (`BANALService`, `TrackerService`, `PebbleService`, `PebbleServiceBuildIn`), and hardware sensor devices (`DeviceManager`, `MyDevice`, `BatteryDevice`, `MyRemoteDevice`, `AltitudeFromPressureDevice`, `FilterManager`).
3. **Automated Architecture Guard & CLI Linter**:
   - Standalone CLI linter `tools/lint_lifecycle_safety.py` scanning `app/src/main` to guarantee zero unshielded platform calls.
   - Automated JUnit architecture test `LifecycleServiceReceiverSafetyAuditTest.kt` executed as part of `./gradlew testDebugUnitTest`.
4. **Clean-Room Regression**:
   - 100% clean-room test pass rate across all 2932 unit tests in the codebase with zero regressions.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-STB-016.1` (Canonical Safety Engine) | `TST-STB-016.1` | `LifecycleUtilsTest.kt` | **PASSED** | `Verified` |
| `REQ-STB-016.2` (Guarded Service Launches) | `TST-STB-016.2` | `LifecycleServiceReceiverSafetyAuditTest.kt` & targeted tests | **PASSED** | `Verified` |
| `REQ-STB-016.3` (Idempotent Receiver Teardown) | `TST-STB-016.3` | `LifecycleServiceReceiverSafetyAuditTest.kt` & targeted tests | **PASSED** | `Verified` |
| `REQ-STB-016.4` (Architecture Guard & Linter) | `TST-STB-016.4` | `LifecycleServiceReceiverSafetyAuditTest.kt` & `lint_lifecycle_safety.py` | **PASSED** | `Verified` |
| Clean-Room Full Suite Regression | `TST-STB-016.5` | `./gradlew testDebugUnitTest` | **PASSED** (2932/2932) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Architecture Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.util.LifecycleUtilsTest" \
                            --tests "com.atrainingtracker.architecture.LifecycleServiceReceiverSafetyAuditTest"
```
**Output**:
```
BUILD SUCCESSFUL in 52s
32 actionable tasks: 6 executed, 26 up-to-date
```

### Static Architecture Guard CLI Linter
```bash
python3 tools/lint_lifecycle_safety.py
```
**Output**:
```
PASS: Lifecycle safety audit passed. Zero unguarded startService or unregisterReceiver call sites found.
```

### Full Clean-Room Unit Test Suite Execution
```bash
./gradlew testDebugUnitTest
```
**Output**:
```
Total tests: 2932, Failures: 0, Skipped: 0
BUILD SUCCESSFUL in 3m
```

---

## 4. Code Changes Summary

- **Engine Added**:
  - `app/src/main/java/com/atrainingtracker/util/LifecycleUtils.kt`
- **Tests & Linters Added**:
  - `app/src/test/java/com/atrainingtracker/util/LifecycleUtilsTest.kt`
  - `app/src/test/java/com/atrainingtracker/architecture/LifecycleServiceReceiverSafetyAuditTest.kt`
  - `tools/lint_lifecycle_safety.py`
- **Call Sites Migrated (18 files, 39+ call sites)**:
  - Service launches: `MainActivityWithNavigation.kt`, `BANALServiceRepository.kt`, `TrainingApplication.java`
  - BroadcastReceiver unregistrations: `MainActivityWithNavigation.kt`, `EquipmentFragment.kt`, `ExportStatusRepository.kt`, `SensorGridScreen.kt`, `ConfigViewsFragment.java`, `TrainingApplication.java`, `TrackerService.java`, `BANALService.java`, `MyDevice.java`, `BatteryDevice.java`, `MyRemoteDevice.java`, `AltitudeFromPressureDevice.java`, `DeviceManager.java`, `DevicesTabbedViewModel.kt`, `FilterManager.java`, `PebbleService.java`, `PebbleServiceBuildIn.java`
- **Documentation Updated**:
  - `docs/requirements.md` (`REQ-STB-016` marked `Verified`)
  - `docs/tests.md` (`TST-STB-016` marked `Verified`)
