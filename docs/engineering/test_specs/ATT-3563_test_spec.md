# Stage 2 Test Specification: ATT-3563 - Harden Service Launches and BroadcastReceiver Unregistration Against Android Lifecycle Exceptions

**Ticket**: [ATT-3563](https://rainerblind.atlassian.net/browse/ATT-3563)  
**Sub-task**: [ATT-3661](https://rainerblind.atlassian.net/browse/ATT-3661) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-3563`  
**Requirement Mapping**: `REQ-STB-016` (*Guarded Service Launches and Idempotent BroadcastReceiver Teardown Across Lifecycle Boundaries*)  
**Test Mapping**: `TST-STB-016`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Formal Requirement Specification (`REQ-STB-016`)

### REQ-STB-016: Guarded Service Launches and Idempotent BroadcastReceiver Teardown Across Lifecycle Boundaries

The system SHALL guarantee complete crash immunity against Android OS lifecycle exceptions (`IllegalStateException`, `BackgroundServiceStartNotAllowedException`, `ForegroundServiceStartNotAllowedException`, `SecurityException`, and `IllegalArgumentException: Receiver not registered`) during service launches and `BroadcastReceiver` teardown across activities, fragments, services, repositories, viewmodels, and hardware sensor devices (ATT-3563, parent epic ATT-235):

1. **Canonical Lifecycle Safety Utility (`LifecycleUtils.kt`) (`REQ-STB-016.1`)**:
   - The system SHALL provide a centralized utility `com.atrainingtracker.util.LifecycleUtils` offering:
     - `@JvmStatic @JvmOverloads fun safeStartService(context: Context?, intent: Intent?, tag: String = "LifecycleUtils"): ComponentName?`
     - `@JvmStatic @JvmOverloads fun safeUnregisterReceiver(context: Context?, receiver: BroadcastReceiver?, tag: String = "LifecycleUtils"): Boolean`
   - Kotlin callers SHALL be provided with extension functions:
     - `fun Context?.safeStartService(intent: Intent?, tag: String = "LifecycleUtils"): ComponentName?`
     - `fun Context?.safeUnregisterReceiver(receiver: BroadcastReceiver?, tag: String = "LifecycleUtils"): Boolean`
   - `safeStartService` SHALL accept null context or null intent returning `null`, invoke `context.startService(intent)`, and catch `IllegalStateException`, `SecurityException`, and general `Throwable`, logging a structured warning (`Log.w`) without re-throwing.
   - `safeUnregisterReceiver` SHALL accept null context or null receiver returning `false`, invoke `context.unregisterReceiver(receiver)`, catch `IllegalArgumentException` and `IllegalStateException`, and log a structured warning (`Log.w`) containing the caller tag and receiver runtime class name, returning `false` without crashing.
   - Logging in `LifecycleUtils` SHALL be resilient against unmocked `android.util.Log` in local JVM test runners via defensive try-catch.

2. **Service Launch Guarding (`REQ-STB-016.2`)**:
   - All direct unshielded `Context.startService(...)` calls across `MainActivityWithNavigation.kt` (`BANALService`), `BANALServiceRepository.kt`, and `TrainingApplication.java` (Pebble services) SHALL be migrated to `LifecycleUtils.safeStartService`.
   - Callers SHALL safely tolerate `null` return values without throwing downstream `NullPointerException`.

3. **Comprehensive Receiver Teardown Guarding (`REQ-STB-016.3`)**:
   - All unshielded `unregisterReceiver(...)` calls across `MainActivityWithNavigation.kt`, `TrackerService.java`, `TrainingApplication.java`, `BANALService.java`, `DeviceManager.java`, `MyDevice.java`, `BatteryDevice.java`, `MyRemoteDevice.java`, `AltitudeFromPressureDevice.java`, `FilterManager.java`, `EquipmentFragment.kt`, `ConfigViewsFragment.java`, `DevicesTabbedViewModel.kt`, `ExportStatusRepository.kt`, `SensorGridScreen.kt`, and Pebble services SHALL be migrated to `LifecycleUtils.safeUnregisterReceiver`.
   - Teardown operations SHALL be completely idempotent, tolerating unregister calls when receivers were never registered or were already unregistered.

4. **Static Architectural Guard & CI/CD Contract (`REQ-STB-016.4`)**:
   - The codebase SHALL enforce lifecycle safety via an automated JUnit architecture audit test (`LifecycleServiceReceiverSafetyAuditTest.kt`) executed as part of `./gradlew testDebugUnitTest`, verifying zero unshielded `unregisterReceiver(` or `startService(` calls exist in `app/src/main` outside `LifecycleUtils`.
   - Standalone CLI linter `tools/lint_lifecycle_safety.py` SHALL verify zero violations.

---

## 2. Test Specification (`TST-STB-016`)

### TST-STB-016: Lifecycle Safety, Guarded Service Launches & Idempotent Receiver Teardown Verification Suite

1. **Central Utility Unit Tests (`LifecycleUtilsTest.kt`) (`TST-STB-016.1`)**:
   - `testSafeStartService_success_returnsComponentName`:
     - Given a valid mocked `Context` and `Intent`, when `context.startService(intent)` succeeds, `safeStartService` returns the expected `ComponentName`.
   - `testSafeStartService_nullInputs_returnsNull`:
     - Given `null` context or `null` intent, `safeStartService` returns `null` without calling `startService`.
   - `testSafeStartService_illegalStateException_returnsNullAndLogsWarning`:
     - Given a mocked `Context` throwing `IllegalStateException` ("Not allowed to start service Intent..."), `safeStartService` returns `null`, catches the exception, and logs a warning with `Log.w`.
   - `testSafeStartService_securityException_returnsNullAndLogsWarning`:
     - Given a mocked `Context` throwing `SecurityException` ("Permission denied"), `safeStartService` returns `null` and logs a warning.
   - `testSafeUnregisterReceiver_success_returnsTrue`:
     - Given a valid mocked `Context` and `BroadcastReceiver`, when `context.unregisterReceiver(receiver)` succeeds, `safeUnregisterReceiver` returns `true`.
   - `testSafeUnregisterReceiver_nullInputs_returnsFalse`:
     - Given `null` context or `null` receiver, `safeUnregisterReceiver` returns `false` without calling `unregisterReceiver`.
   - `testSafeUnregisterReceiver_notRegistered_returnsFalseAndLogsWarning`:
     - Given a mocked `Context` throwing `IllegalArgumentException` ("Receiver not registered"), `safeUnregisterReceiver` returns `false`, catches the exception, and logs a warning containing the receiver class name.
   - `testKotlinExtensions_delegateToLifecycleUtils`:
     - Verify `context.safeStartService(intent)` and `context.safeUnregisterReceiver(receiver)` invoke the corresponding utility methods.

2. **Static Architecture Contract Test (`LifecycleServiceReceiverSafetyAuditTest.kt`) (`TST-STB-016.2`)**:
   - Recursively inspects all `.java` and `.kt` source files in `app/src/main` (excluding `LifecycleUtils.kt`).
   - Asserts 0 occurrences matching regex:
     - `(?<!safe)unregisterReceiver\s*\(`
     - `(?<!safe)startService\s*\(`
   - Fails the build with clear file and line pointers if any unshielded call is found.

3. **CLI Linter Verification (`tools/lint_lifecycle_safety.py`) (`TST-STB-016.3`)**:
   - Standalone Python linter executing regex AST patterns over `app/src/main`.
   - Exits with return code 0 when zero violations are found, non-zero otherwise.

4. **Clean-Room Full Suite Regression Execution (`TST-STB-016.4`)**:
   - Executes `./gradlew testDebugUnitTest` verifying 100% clean-room test pass rate across all 2920+ unit tests with zero regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Test Specification Item | Verification Target |
| :--- | :--- | :--- |
| `REQ-STB-016.1` (Canonical Utility & Resilient Logging) | `TST-STB-016.1` | `LifecycleUtilsTest.kt` |
| `REQ-STB-016.2` (Guarded Service Launches) | `TST-STB-016.1`, `TST-STB-016.2` | `LifecycleUtilsTest.kt`, `LifecycleServiceReceiverSafetyAuditTest.kt` |
| `REQ-STB-016.3` (Idempotent Receiver Teardown) | `TST-STB-016.1`, `TST-STB-016.2` | `LifecycleUtilsTest.kt`, `LifecycleServiceReceiverSafetyAuditTest.kt` |
| `REQ-STB-016.4` (Static Guard & CI/CD Contract) | `TST-STB-016.2`, `TST-STB-016.3` | `LifecycleServiceReceiverSafetyAuditTest.kt`, `lint_lifecycle_safety.py` |
| Full System Non-Regression | `TST-STB-016.4` | `./gradlew testDebugUnitTest` |
