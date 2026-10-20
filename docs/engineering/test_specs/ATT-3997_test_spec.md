# Stage 2: Requirement & Test Specification - ATT-3997: [Bug] [High] BLE sensor search fails on Android 8-11 due to missing BLUETOOTH_ADMIN permission

**Ticket**: [ATT-3997](https://rainerblind.atlassian.net/browse/ATT-3997)  
**Sub-task**: [ATT-4361](https://rainerblind.atlassian.net/browse/ATT-4361) (`[Req & Test Spec]`)  
**Parent Epic**: None (*Product Backlog / Bugfix*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-CON-021` (*BLUETOOTH_ADMIN Manifest Declaration for Android 8–11 Compatibility and Defensive BLE Scan Exception Handling*)  
**Test Spec ID**: `TST-CON-021`  
**Branch**: `feature/ATT-3997`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-CON-021)

### 1.1 Problem Statement & Rationale
In **aTrainingTracker**, Bluetooth Low Energy (BLE) sensor discovery (`BTSearchForNewDevicesEngine.startAsyncSearch()`) allows athletes to discover, pair, and connect vital training sensors such as Heart Rate Monitors (`HRM`), Cycling Power Meters (`BIKE_POWER`), Speed & Cadence sensors (`BIKE_SPEED_AND_CADENCE`), and Footpods (`RUN_SPEED`).

On devices running Android 8.0 through Android 11 (API level 26 through 30), attempting to search for new BLE sensors fails with an unhandled runtime `SecurityException` or fails to initiate discovery:
1. In `app/src/main/AndroidManifest.xml` (line 37), the permission declaration for `BLUETOOTH_ADMIN` is commented out:
   ```xml
   <!-- uses-permission android:name="android.permission.BLUETOOTH_ADMIN" /-->
   ```
   While `minSdkVersion` is set to 26 (`app/build.gradle`), the Android framework security enforcement on API levels $\le 30$ mandates `android.permission.BLUETOOTH_ADMIN` to initiate Bluetooth LE scanning via `BluetoothLeScanner.startScan(...)`. Because the permission is absent from the manifest, the Android OS rejects the Binder IPC transaction to the Bluetooth service and throws:
   `java.lang.SecurityException: Need BLUETOOTH_ADMIN permission`.
2. In `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngine.java`:
   * In `startAsyncSearch()`, pre-flight permission checking on API $< 31$ verifies only `ACCESS_FINE_LOCATION`, omitting any check for `BLUETOOTH_ADMIN`.
   * At line 367, `scanner.startScan(...)` is invoked without defensive `try { ... } catch (SecurityException | IllegalStateException e)` error handling. When `SecurityException` is thrown, the exception is unhandled and terminates execution; because `scanning = true` was set at line 350 prior to the scan invocation, the search engine's internal state is permanently poisoned, locking athletes out of all subsequent scan attempts with the message `"already searching"`.
   * At line 386 in `stopAsyncSearch()`, `scanner.stopScan(...)` is likewise executed without defensive exception handling. If Bluetooth is turned off mid-scan or permissions change, `stopScan()` can throw an unhandled `SecurityException` or `IllegalStateException`.

**Rationale**: Athletes using devices running Android 8.0 through 11 must be able to search for and pair BLE sensors reliably. Restoring `BLUETOOTH_ADMIN` with `android:maxSdkVersion="30"` preserves modern Android 12+ runtime permission boundaries while guaranteeing full backward compatibility. Defensive exception handling ensures the search engine never enters an irrecoverable state lockup when platform IPC calls fail.

### 1.2 Functional & Architectural Requirements
The system SHALL satisfy the following functional and technical requirements:

1. **Manifest Permission Declaration (`REQ-CON-021.1`)**:
   * In `app/src/main/AndroidManifest.xml`, the system SHALL declare:
     ```xml
     <uses-permission
         android:name="android.permission.BLUETOOTH_ADMIN"
         android:maxSdkVersion="30" />
     ```
   * The declaration SHALL be active (uncommented).
   * Devices running Android 8.0 through Android 11 (API 26–30) SHALL obtain install-time Bluetooth admin authorization required by the framework Bluetooth service for BLE discovery.
   * Modern devices running Android 12+ (API 31+) SHALL cleanly ignore the permission in favor of runtime `BLUETOOTH_SCAN` without triggering Google Play Store permission policy warnings.

2. **Legacy Permission Guard in Search Engine (`REQ-CON-021.2`)**:
   * In `BTSearchForNewDevicesEngine.startAsyncSearch()`, when executing on Android versions below API 31 (`Build.VERSION.SDK_INT < Build.VERSION_CODES.S`), the engine SHALL explicitly verify both `BLUETOOTH_ADMIN` and `ACCESS_FINE_LOCATION` via `ContextCompat.checkSelfPermission`.
   * If either `BLUETOOTH_ADMIN` or `ACCESS_FINE_LOCATION` is not granted (`!= PackageManager.PERMISSION_GRANTED`), the engine SHALL log a descriptive warning (`Log.w`), abort execution before calling `scanner.startScan(...)`, and ensure `scanning` remains `false`.

3. **Defensive Scan Execution & Lifecycle State Restoration (`REQ-CON-021.3`)**:
   * In `BTSearchForNewDevicesEngine.startAsyncSearch()`, the invocation `scanner.startScan(filters, settings, mScanCallback)` SHALL be enclosed in a `try { ... } catch (SecurityException | IllegalStateException e)` block.
   * If a `SecurityException` or `IllegalStateException` is thrown by the platform scanner, the engine SHALL log an error with exception details, immediately restore `scanning = false`, and return cleanly without crashing the caller thread or permanently locking the engine out of subsequent scan attempts.

4. **Defensive Scan Teardown (`REQ-CON-021.4`)**:
   * In `BTSearchForNewDevicesEngine.stopAsyncSearch()`, the invocation `scanner.stopScan(mScanCallback)` SHALL be enclosed in a `try { ... } catch (SecurityException | IllegalStateException e)` block.
   * If the platform throws an exception during teardown (e.g., Bluetooth adapter disabled concurrently or permissions revoked mid-scan), the engine SHALL log a warning, ensure `scanning = false` is maintained, and proceed to execute `resetTrackingState()` cleanly.

5. **Multi-Locale Neutrality & Zero String Alterations (`REQ-CON-021.5`)**:
   * The defect remediation operates purely at the Android manifest and service engine level.
   * Zero new string resources are required across the 9 canonical application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
   * 100% translation parity across existing resources SHALL be strictly maintained.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Install-Time Permission Grant on Android 8–11)**:
  * *Given* an Android 8.0–11 device (API 26–30) with `BLUETOOTH_ADMIN` declared with `maxSdkVersion="30"` in `AndroidManifest.xml`,
  * *When* the application is installed and executed,
  * *Then* `BLUETOOTH_ADMIN` is granted at install time without runtime prompt and without triggering Google Play Store policy warnings on API 31+.
* **Criterion 2 (Legacy BLUETOOTH_ADMIN Pre-Scan Check)**:
  * *Given* an Android 8.0–11 device (API 26–30) where `BLUETOOTH_ADMIN` is not granted,
  * *When* `BTSearchForNewDevicesEngine.startAsyncSearch()` executes,
  * *Then* the method logs a warning, aborts cleanly before calling `scanner.startScan()`, and leaves `scanning = false`.
* **Criterion 3 (Legacy ACCESS_FINE_LOCATION Pre-Scan Check)**:
  * *Given* an Android 8.0–11 device (API 26–30) where `ACCESS_FINE_LOCATION` is not granted,
  * *When* `BTSearchForNewDevicesEngine.startAsyncSearch()` executes,
  * *Then* the method logs a warning, aborts cleanly before calling `scanner.startScan()`, and leaves `scanning = false`.
* **Criterion 4 (Defensive startScan SecurityException Recovery)**:
  * *Given* a running `BTSearchForNewDevicesEngine` where `scanner.startScan(...)` throws `SecurityException` or `IllegalStateException`,
  * *When* `startAsyncSearch()` executes,
  * *Then* the exception is caught and logged, `scanning` is restored to `false`, and subsequent calls to `startAsyncSearch()` are not blocked by the `already searching` guard.
* **Criterion 5 (Defensive stopScan Exception Recovery)**:
  * *Given* an active scan where `scanner.stopScan(...)` throws `SecurityException` or `IllegalStateException`,
  * *When* `stopAsyncSearch()` executes,
  * *Then* the exception is caught and logged, `scanning` is set to `false`, and `resetTrackingState()` executes cleanly.
* **Criterion 6 (Android 12+ Permission Independence)**:
  * *Given* an Android 12+ device (API 31+) with `BLUETOOTH_SCAN` granted,
  * *When* `startAsyncSearch()` executes,
  * *Then* scanning proceeds using low-latency aggressive scan settings without requiring `BLUETOOTH_ADMIN`.

### 1.4 System Invariants
* **Android 12+ Permission Boundaries (`REQ-CON-019`)**: Modern Android 12+ (API 31+) runtime permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`) remain completely intact. The `maxSdkVersion="30"` attribute strictly restricts `BLUETOOTH_ADMIN` to API $\le 30$.
* **BLE Peripheral Driver Integrity**: Existing BLE peripheral drivers (`BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLEBikeDevice`, `BTLERunSpeedDevice`) and GATT communication queues remain 100% untouched.
* **Search Engine State Integrity**: The engine's internal `scanning` boolean flag must strictly reflect real scanning state. A failure at the platform scanner layer must never leave `scanning = true`.
* **Zero Database & UI Schema Changes**: No database tables, columns, migrations, or Compose UI layouts are modified.
* **Clean-Room Test Suite Pass Rate**: 100% full clean-room unit regression test suite pass rate under `./gradlew testDebugUnitTest`.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-CON-021`, extending and refining `REQ-CON-019` (`ATT-2773`). Targets `app/src/main/AndroidManifest.xml` and `BTSearchForNewDevicesEngine.java`.
* `Historical Origin & Commit Trace`: Ticket `ATT-3997`, Sprint `2026-43.1`. Discovered during independent whole-app code review of `feature/ATT-3921` (`0234bfc0`). `BLUETOOTH_ADMIN` was present in Initial Commit (`845818c2cfe0`, 2019) and was commented out in commit `f2f55e11cf48` ("feat: Android permissions", July 26, 2024). Ticket `ATT-2773` (`REQ-CON-019`, Sprint 2026-41.4, commit `53370e78`) introduced Android 12 permissions (`BLUETOOTH_SCAN`) and direct `TRANSPORT_LE` connection, but omitted legacy `BLUETOOTH_ADMIN` verification and scan exception guards.
* `Root Reason for Existing Formulation`: Commit `f2f55e11cf48` commented out `BLUETOOTH_ADMIN` under the erroneous assumption that modern `BLUETOOTH_SCAN` superseded it globally across all Android versions. On API $\le 30$, `BluetoothLeScanner.startScan` strictly enforces `BLUETOOTH_ADMIN` at the framework Binder boundary. Furthermore, `startScan()` and `stopScan()` omitted defensive exception handling under the assumption that pre-flight permission checks would prevent all platform runtime exceptions.
* `Preservation of Core Invariants`: Android 12+ runtime permission model (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`) remains strictly intact (`REQ-CON-019`); existing BLE peripheral drivers (`BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLEBikeDevice`, `BTLERunSpeedDevice`) remain 100% untouched; search engine lifecycle state consistency (`scanning` flag) is preserved; and clean-room full test suite pass rate remains 100%.

---

## 2. Test Specification (TST-CON-021)

### 2.1 Unit Tests

#### Test Case 1: Pre-API 31 Missing BLUETOOTH_ADMIN Abort Test (`[TST-CON-021.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Preconditions**:
  * Execute under API level $< 31$ (e.g. `Build.VERSION_CODES.R` / API 30).
  * Mock `ContextCompat.checkSelfPermission(mockContext, Manifest.permission.BLUETOOTH_ADMIN)` returning `PackageManager.PERMISSION_DENIED`.
  * Mock `ContextCompat.checkSelfPermission(mockContext, Manifest.permission.ACCESS_FINE_LOCATION)` returning `PackageManager.PERMISSION_GRANTED`.
* **Action**:
  * Instantiate `TestSearchEngine(mockContext, DeviceType.HRM, mockCallback)`.
  * Invoke `engine.startAsyncSearch()`.
* **Expected Result**:
  * `engine.isScanning()` evaluates to `false`.
  * `mockScanner.startScan(...)` is invoked exactly 0 times.
  * Search aborts cleanly without throwing any exception.

#### Test Case 2: Pre-API 31 Missing ACCESS_FINE_LOCATION Abort Test (`[TST-CON-021.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Preconditions**:
  * Execute under API level $< 31$ (e.g. `Build.VERSION_CODES.R` / API 30).
  * Mock `ContextCompat.checkSelfPermission(mockContext, Manifest.permission.BLUETOOTH_ADMIN)` returning `PackageManager.PERMISSION_GRANTED`.
  * Mock `ContextCompat.checkSelfPermission(mockContext, Manifest.permission.ACCESS_FINE_LOCATION)` returning `PackageManager.PERMISSION_DENIED`.
* **Action**:
  * Instantiate `TestSearchEngine(mockContext, DeviceType.HRM, mockCallback)`.
  * Invoke `engine.startAsyncSearch()`.
* **Expected Result**:
  * `engine.isScanning()` evaluates to `false`.
  * `mockScanner.startScan(...)` is invoked exactly 0 times.
  * Search aborts cleanly without throwing any exception.

#### Test Case 3: StartScan SecurityException Graceful Recovery Test (`[TST-CON-021.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Preconditions**:
  * Mock permissions granted (`BLUETOOTH_SCAN` or `BLUETOOTH_ADMIN` + `ACCESS_FINE_LOCATION`).
  * Mock `mockScanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>())` throwing `SecurityException("Need BLUETOOTH_ADMIN permission")`.
* **Action**:
  * Instantiate `TestSearchEngine(mockContext, DeviceType.HRM, mockCallback)`.
  * Invoke `engine.startAsyncSearch()`.
* **Expected Result**:
  * Method catches `SecurityException` and logs the error without rethrowing.
  * `engine.isScanning()` is reset to `false`.
  * State lockup is prevented: subsequent invocations to `startAsyncSearch()` are not rejected by the `already searching` branch.

#### Test Case 4: StartScan IllegalStateException Graceful Recovery Test (`[TST-CON-021.4]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Preconditions**:
  * Mock permissions granted.
  * Mock `mockScanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>())` throwing `IllegalStateException("BT adapter not turned ON")`.
* **Action**:
  * Instantiate `TestSearchEngine(mockContext, DeviceType.HRM, mockCallback)`.
  * Invoke `engine.startAsyncSearch()`.
* **Expected Result**:
  * Method catches `IllegalStateException` and logs the error without rethrowing.
  * `engine.isScanning()` is reset to `false`.

#### Test Case 5: StopScan SecurityException Graceful Recovery Test (`[TST-CON-021.5]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Preconditions**:
  * Engine is in active scanning state (`scanning = true`).
  * Mock `mockScanner.stopScan(any<ScanCallback>())` throwing `SecurityException("Permission revoked")`.
* **Action**:
  * Instantiate `TestSearchEngine(mockContext, DeviceType.HRM, mockCallback)`.
  * Invoke `engine.stopAsyncSearch()`.
* **Expected Result**:
  * Method catches `SecurityException` and logs the warning without rethrowing.
  * `engine.isScanning()` is set to `false`.
  * `resetTrackingState()` is guaranteed to execute.

#### Test Case 6: StopScan IllegalStateException Graceful Recovery Test (`[TST-CON-021.6]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Preconditions**:
  * Engine is in active scanning state (`scanning = true`).
  * Mock `mockScanner.stopScan(any<ScanCallback>())` throwing `IllegalStateException("BT adapter off")`.
* **Action**:
  * Instantiate `TestSearchEngine(mockContext, DeviceType.HRM, mockCallback)`.
  * Invoke `engine.stopAsyncSearch()`.
* **Expected Result**:
  * Method catches `IllegalStateException` and logs the warning without rethrowing.
  * `engine.isScanning()` is set to `false`.
  * `resetTrackingState()` is guaranteed to execute.

#### Test Case 7: Manifest Permission Declaration & MaxSdkVersion Audit (`[TST-CON-021.7]`)
* **Scope**: Manifest Validation / Static Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Preconditions**:
  * `app/src/main/AndroidManifest.xml` parsed or inspected.
* **Action**:
  * Inspect `<uses-permission>` tags in `AndroidManifest.xml`.
* **Expected Result**:
  * Tag `<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />` is declared and active (not commented out).
  * `android:maxSdkVersion` attribute is strictly set to `"30"`.

### 2.2 Localization Parity Audit

#### Test Case 8: 9-Language Localization & Specifier Audit (`[TST-CON-021.8]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**:
  * Verify that zero new user-facing string resources are required for this platform permission and service exception handling bugfix.
  * Confirm 100% translation parity and format specifier consistency across all 9 canonical locales:
    * `values` (English)
    * `values-de` (German)
    * `values-es` (Spanish)
    * `values-fr` (French)
    * `values-it` (Italian)
    * `values-ja` (Japanese)
    * `values-nl` (Dutch)
    * `values-pl` (Polish)
    * `values-pt` (Portuguese)
* **Expected Result**:
  * 100% parity across all 9 locales with zero missing string entries and zero format specifier mismatches.

### 2.3 Clean-Room Regression Suite

#### Test Case 9: Clean-Room Regression Suite (`[TST-CON-021.9]`)
* **Scope**: Full Clean-Room JVM Test Suite Execution
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**:
  * Execute the entire project test suite in a clean JVM environment.
  * Verify that all ~3,300+ unit tests pass with zero errors, zero failures, and zero regressions.
* **Expected Result**:
  * `BUILD SUCCESSFUL` with 100% test pass rate across all project modules.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test / Verification Procedure | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-CON-021.1]` | Unit | `BTSearchForNewDevicesEngine.startAsyncSearch` (pre-API 31 missing BLUETOOTH_ADMIN) | `REQ-CON-021.2` | Specified |
| `[TST-CON-021.2]` | Unit | `BTSearchForNewDevicesEngine.startAsyncSearch` (pre-API 31 missing ACCESS_FINE_LOCATION) | `REQ-CON-021.2` | Specified |
| `[TST-CON-021.3]` | Unit | `BTSearchForNewDevicesEngine.startAsyncSearch` (`startScan` SecurityException catch & state reset) | `REQ-CON-021.3` | Specified |
| `[TST-CON-021.4]` | Unit | `BTSearchForNewDevicesEngine.startAsyncSearch` (`startScan` IllegalStateException catch & state reset) | `REQ-CON-021.3` | Specified |
| `[TST-CON-021.5]` | Unit | `BTSearchForNewDevicesEngine.stopAsyncSearch` (`stopScan` SecurityException catch & teardown) | `REQ-CON-021.4` | Specified |
| `[TST-CON-021.6]` | Unit | `BTSearchForNewDevicesEngine.stopAsyncSearch` (`stopScan` IllegalStateException catch & teardown) | `REQ-CON-021.4` | Specified |
| `[TST-CON-021.7]` | Static / Manifest | `AndroidManifest.xml` (`BLUETOOTH_ADMIN` with `maxSdkVersion="30"`) | `REQ-CON-021.1` | Specified |
| `[TST-CON-021.8]` | Localization | `TranslationParityTest` (9 canonical app locales) | `REQ-CON-021.5`, `REQ-UI-106` | Specified |
| `[TST-CON-021.9]` | Full Suite | `./gradlew testDebugUnitTest` (Clean-room full test suite) | `REQ-PRO-001` | Specified |

---

## 4. 9-Locale Verification Summary

| Locale Directory | Language | New Strings Required | Parity Status | Specifier Audit |
| :--- | :--- | :---: | :---: | :---: |
| `values` | English | 0 | 100% (Unchanged) | N/A (No new strings) |
| `values-de` | German | 0 | 100% (Unchanged) | N/A (No new strings) |
| `values-es` | Spanish | 0 | 100% (Unchanged) | N/A (No new strings) |
| `values-fr` | French | 0 | 100% (Unchanged) | N/A (No new strings) |
| `values-it` | Italian | 0 | 100% (Unchanged) | N/A (No new strings) |
| `values-ja` | Japanese | 0 | 100% (Unchanged) | N/A (No new strings) |
| `values-nl` | Dutch | 0 | 100% (Unchanged) | N/A (No new strings) |
| `values-pl` | Polish | 0 | 100% (Unchanged) | N/A (No new strings) |
| `values-pt` | Portuguese | 0 | 100% (Unchanged) | N/A (No new strings) |

**Conclusion**: This remediation is strictly a manifest permission declaration and defensive exception handling fix in the backend BLE service. Zero UI components or localized string resources are touched. 100% localization parity across all 9 canonical locales is fully maintained.
