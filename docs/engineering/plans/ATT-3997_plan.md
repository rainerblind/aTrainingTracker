# Stage 3: Implementation Plan - ATT-3997: [Bug] [High] BLE sensor search fails on Android 8-11 due to missing BLUETOOTH_ADMIN permission

**Ticket**: [ATT-3997](https://rainerblind.atlassian.net/browse/ATT-3997)  
**Sub-task**: [ATT-4362](https://rainerblind.atlassian.net/browse/ATT-4362) (`[Impl-Plan]`)  
**Parent Epic**: None (*Product Backlog / Bugfix*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-CON-021` (`REQ-CON-021.1` .. `REQ-CON-021.5`)  
**Test Mapping**: `TST-CON-021` (`TST-CON-021.1` .. `TST-CON-021.9`)  
**Branch**: `feature/ATT-3997`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

In **aTrainingTracker**, Bluetooth Low Energy (BLE) peripheral discovery enables athletes to search for, discover, and pair vital sensors including Heart Rate Monitors (`HRM`), Cycling Power Meters (`BIKE_POWER`), Speed & Cadence sensors (`BIKE_SPEED_AND_CADENCE`), and Footpods (`RUN_SPEED`) through `BTSearchForNewDevicesEngine.startAsyncSearch()`.

On devices running Android 8.0 through Android 11 (API level 26 through 30), attempting to search for new BLE sensors fails with an unhandled runtime `SecurityException` or fails to initiate discovery:
1. In `app/src/main/AndroidManifest.xml` (line 37), the permission declaration for `BLUETOOTH_ADMIN` is commented out (`<!-- uses-permission android:name="android.permission.BLUETOOTH_ADMIN" /-->`). On Android API $\le 30$, `BluetoothLeScanner.startScan(...)` enforces `android.permission.BLUETOOTH_ADMIN` in the system Bluetooth service Binder IPC layer. Because it was omitted from the manifest, the Android OS throws `java.lang.SecurityException: Need BLUETOOTH_ADMIN permission`.
2. In `BTSearchForNewDevicesEngine.java`:
   * On API $< 31$, `startAsyncSearch()` checks only `ACCESS_FINE_LOCATION`, omitting any check for `BLUETOOTH_ADMIN`.
   * At line 367, `scanner.startScan(...)` is invoked without defensive `try { ... } catch (SecurityException | IllegalStateException e)` error handling. When `SecurityException` is thrown, the exception is unhandled and terminates execution; because `scanning = true` was set at line 350 prior to the scan call, the search engine's internal lifecycle state is permanently poisoned, locking athletes out of all subsequent scan attempts with the message `"already searching"`.
   * At line 386 in `stopAsyncSearch()`, `scanner.stopScan(...)` is also executed without defensive exception handling. If Bluetooth is turned off mid-scan or permissions change, `stopScan()` can throw an unhandled `SecurityException` or `IllegalStateException`.

This plan details the SWE.2 architectural blueprint and atomic construction steps to resolve both defects safely without regressions.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-CON-021` (*BLUETOOTH_ADMIN Manifest Declaration for Android 8–11 Compatibility and Defensive BLE Scan Exception Handling*)
  * `REQ-CON-021.1`: Active `BLUETOOTH_ADMIN` declaration in `AndroidManifest.xml` with `android:maxSdkVersion="30"`.
  * `REQ-CON-021.2`: Explicit `BLUETOOTH_ADMIN` and `ACCESS_FINE_LOCATION` pre-scan permission verification in `BTSearchForNewDevicesEngine.startAsyncSearch()` for API $< 31$.
  * `REQ-CON-021.3`: Defensive `try { ... } catch (SecurityException | IllegalStateException e)` wrapping around `scanner.startScan(...)` in `startAsyncSearch()`, logging errors and immediately restoring `scanning = false`.
  * `REQ-CON-021.4`: Defensive `try { ... } catch (SecurityException | IllegalStateException e)` wrapping around `scanner.stopScan(...)` in `stopAsyncSearch()`, logging warnings and ensuring clean teardown.
  * `REQ-CON-021.5`: Multi-locale neutrality & zero string alterations; 100% translation parity across all 9 canonical application locales.
* **Test Mapping**: `TST-CON-021` (*BLE Scan Permission & Exception Handling Test Suite*)
  * `[TST-CON-021.1]`: Pre-API 31 missing `BLUETOOTH_ADMIN` abort test.
  * `[TST-CON-021.2]`: Pre-API 31 missing `ACCESS_FINE_LOCATION` abort test.
  * `[TST-CON-021.3]`: `startScan` `SecurityException` recovery & state restoration test.
  * `[TST-CON-021.4]`: `startScan` `IllegalStateException` recovery & state restoration test.
  * `[TST-CON-021.5]`: `stopScan` `SecurityException` recovery test.
  * `[TST-CON-021.6]`: `stopScan` `IllegalStateException` recovery test.
  * `[TST-CON-021.7]`: `AndroidManifest.xml` static validation for active `BLUETOOTH_ADMIN` with `maxSdkVersion="30"`.
  * `[TST-CON-021.8]`: 9-language localization parity and positional format specifier audit (`TranslationParityTest`).
  * `[TST-CON-021.9]`: Full clean-room unit regression suite pass rate (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Android 12+ Permission Boundaries (`REQ-CON-019`)**: Modern Android 12+ (API 31+) runtime permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`) remain completely intact. The `android:maxSdkVersion="30"` attribute strictly restricts `BLUETOOTH_ADMIN` to API $\le 30$, preventing Google Play Store permission policy warnings.
2. **BLE Peripheral Driver Integrity**: Existing BLE peripheral drivers (`BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLEBikeDevice`, `BTLERunSpeedDevice`) and GATT communication queues remain 100% untouched.
3. **Search Engine State Integrity**: The internal `scanning` boolean flag must strictly reflect real scanning state. A platform scanner exception must never leave `scanning = true`.
4. **Zero Database & UI Schema Changes**: No database tables, columns, migrations, or Compose UI layouts are modified.
5. **Zero Unintended Regressions**: Existing feature and test suites continue to pass cleanly with 100% pass rate.
6. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-3997` remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Android Application Manifest (`app/src/main/AndroidManifest.xml`)
* **Layer**: Android Framework / Manifest Package Declaration
* **Role**: Declare package-level permissions requested from the Android OS.
* **Changes**:
  Replace commented-out `BLUETOOTH_ADMIN` tag:
  ```xml
  <!-- uses-permission android:name="android.permission.BLUETOOTH_ADMIN" /-->
  ```
  with active bounded declaration:
  ```xml
  <uses-permission
      android:name="android.permission.BLUETOOTH_ADMIN"
      android:maxSdkVersion="30" />
  ```
* **Architectural Rationale**: Guarantees Android 8.0–11 (API 26–30) devices obtain install-time `BLUETOOTH_ADMIN` authorization needed by the system Bluetooth service to initiate scans, while modern Android 12+ (API 31+) devices ignore it.

### Component 2: Bluetooth LE Search Engine (`app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngine.java`)
* **Layer**: BANAL Service / Device Discovery (`com.atrainingtracker.banalservice.devices.bluetooth_le.search_new`)
* **Role**: Asynchronous BLE discovery engine scanning for nearby sensors and dispatching discovered peripheral callbacks.
* **Changes**:
  1. In `startAsyncSearch()`:
     * In the `Build.VERSION.SDK_INT < Build.VERSION_CODES.S` branch, add pre-check:
       ```java
       if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
           Log.w(TAG, "BLUETOOTH_ADMIN permission not granted");
           return;
       }
       ```
     * Wrap `scanner.startScan(filters, settings, mScanCallback);` in defensive `try/catch`:
       ```java
       try {
           scanner.startScan(filters, settings, mScanCallback);
       } catch (SecurityException | IllegalStateException e) {
           Log.e(TAG, "Failed to start BLE scan: " + e.getMessage(), e);
           scanning = false;
       }
       ```
  2. In `stopAsyncSearch()`:
     * Wrap `scanner.stopScan(mScanCallback);` in defensive `try/catch`:
       ```java
       try {
           scanner.stopScan(mScanCallback);
       } catch (SecurityException | IllegalStateException e) {
           Log.w(TAG, "Failed to stop BLE scan: " + e.getMessage(), e);
       }
       ```
  3. Introduce package-private accessor for testability:
     ```java
     int getBuildVersionSdkInt() {
         return Build.VERSION.SDK_INT;
     }
     ```
     and replace direct `Build.VERSION.SDK_INT` comparisons in `startAsyncSearch()` and `stopAsyncSearch()` with `getBuildVersionSdkInt() >= Build.VERSION_CODES.S`. This follows the identical, proven pattern established in `TrackerService` (`testSdkInt`) to enable comprehensive multi-API level JVM unit testing.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes. This remediation operates purely at the `AndroidManifest.xml` permission level and backend Bluetooth LE service discovery layer. Zero UI screens, composables, views, dialogs, or user-facing strings are modified.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Manifest Permission Restoration (`AndroidManifest.xml`)
* **File**: `app/src/main/AndroidManifest.xml`
* **Changes**:
  Replace commented-out `BLUETOOTH_ADMIN` line with:
  ```xml
      <uses-permission
          android:name="android.permission.BLUETOOTH_ADMIN"
          android:maxSdkVersion="30" />
  ```
* **Traceability**: `REQ-CON-021.1`

### Step 2: Testable SDK Version Accessor & Legacy Permission Guard in Search Engine
* **File**: `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngine.java`
* **Changes**:
  1. Add `int getBuildVersionSdkInt()` helper returning `Build.VERSION.SDK_INT`.
  2. In `startAsyncSearch()`, use `getBuildVersionSdkInt() >= Build.VERSION_CODES.S`. In the `else` branch, verify `ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED`. If not granted, log warning and return cleanly without setting `scanning = true`.
  3. In `stopAsyncSearch()`, use `getBuildVersionSdkInt() >= Build.VERSION_CODES.S` for consistency.
* **Traceability**: `REQ-CON-021.2`

### Step 3: Defensive Exception Handling in `startAsyncSearch()` and `stopAsyncSearch()`
* **File**: `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngine.java`
* **Changes**:
  1. Enclose `scanner.startScan(filters, settings, mScanCallback);` in `try { ... } catch (SecurityException | IllegalStateException e)`. In the `catch` block, log error with exception stack trace and set `scanning = false`.
  2. Enclose `scanner.stopScan(mScanCallback);` in `try { ... } catch (SecurityException | IllegalStateException e)`. In the `catch` block, log warning.
* **Traceability**: `REQ-CON-021.3`, `REQ-CON-021.4`

### Step 4: Unit Test Suite Expansion
* **File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Changes**:
  1. Expose `testSdkInt` in `TestSearchEngine` overriding `getBuildVersionSdkInt()`.
  2. Add unit tests for `TST-CON-021.1` (`testStartAsyncSearch_onApiBelow31_missingBluetoothAdminPermission_abortsGracefully`).
  3. Add unit tests for `TST-CON-021.2` (`testStartAsyncSearch_onApiBelow31_missingFineLocationPermission_abortsGracefully`).
  4. Add unit tests for `TST-CON-021.3` (`testStartAsyncSearch_startScanThrowsSecurityException_catchesAndResetsScanning`).
  5. Add unit tests for `TST-CON-021.4` (`testStartAsyncSearch_startScanThrowsIllegalStateException_catchesAndResetsScanning`).
  6. Add unit tests for `TST-CON-021.5` (`testStopAsyncSearch_stopScanThrowsSecurityException_catchesGracefully`).
  7. Add unit tests for `TST-CON-021.6` (`testStopAsyncSearch_stopScanThrowsIllegalStateException_catchesGracefully`).
  8. Add static manifest audit test for `TST-CON-021.7` (`testManifest_declaresBluetoothAdminPermissionWithMaxSdkVersion30`).
* **Traceability**: `TST-CON-021.1` .. `TST-CON-021.7`

### Step 5: Targeted Unit Test Execution
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.search_new.BTSearchForNewDevicesEngineTest"
  ```
* **Expected Result**: 100% pass rate across all new and existing test cases in ~5–15 seconds.

---

## 6. Verification & Rollback Plan

* **Verification Strategy**:
  1. **Stage 4 Construction**: Run targeted class unit tests via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.search_new.BTSearchForNewDevicesEngineTest"`.
  2. **Stage 5 Verification**:
     * Run localization parity audit: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.
     * Run full clean-room test suite: `./gradlew testDebugUnitTest` verifying 100% pass rate across all ~3,300+ unit tests.
     * Build debug APK: `./gradlew assembleDebug`.
* **Rollback Strategy**:
  All changes are strictly isolated on the dedicated feature branch `feature/ATT-3997`. If unexpected regressions occur or gate checks fail, the branch can be cleanly reset (`git reset --hard`) or abandoned without affecting `develop` or the active sprint integration branch.
