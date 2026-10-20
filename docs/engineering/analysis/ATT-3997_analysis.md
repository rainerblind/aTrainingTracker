# Stage 1 Analysis: ATT-3997 - BLE sensor search fails on Android 8-11 due to missing BLUETOOTH_ADMIN permission

**Ticket**: [ATT-3997](https://rainerblind.atlassian.net/browse/ATT-3997)  
**Sub-task**: [ATT-4360](https://rainerblind.atlassian.net/browse/ATT-4360) (`[Analysis]`)  
**Parent Epic**: None (*Product Backlog / Bugfix*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Branch**: `feature/ATT-3997`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

In **aTrainingTracker**, Bluetooth Low Energy (BLE) sensor search enables athletes to discover and pair vital peripherals such as Heart Rate Monitors (`HRM`), Cycling Power Meters (`BIKE_POWER`), Speed & Cadence sensors (`BIKE_SPEED_AND_CADENCE`), and Footpods (`RUN_SPEED`).

On devices running Android 8.0 through Android 11 (API level 26 through 30), attempting to search for new BLE sensors (`BTSearchForNewDevicesEngine.startAsyncSearch()`) fails with an unhandled runtime `SecurityException` or fails to initiate discovery.

The application configuration exhibits two distinct defects:
1. In `app/src/main/AndroidManifest.xml` (line 37), the permission declaration for `BLUETOOTH_ADMIN` is commented out:
   ```xml
   <!-- uses-permission android:name="android.permission.BLUETOOTH_ADMIN" /-->
   ```
   While `minSdkVersion` is set to 26 (`app/build.gradle`), Android framework security checks on API levels $\le 30$ require `android.permission.BLUETOOTH_ADMIN` to initiate Bluetooth LE scanning via `BluetoothLeScanner.startScan(...)`. Because the permission is not declared in the manifest, the Android OS rejects the Binder IPC transaction to the Bluetooth service and throws `java.lang.SecurityException: Need BLUETOOTH_ADMIN permission`.
2. In `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngine.java` (line 367 and line 386), the calls to `scanner.startScan(...)` and `scanner.stopScan(...)` are executed without any defensive `try { ... } catch (SecurityException e)` error handling. When `SecurityException` is thrown, the exception is unhandled and the engine's internal `scanning` flag is left stuck in `true` (set at line 350 prior to `startScan()`), corrupting engine lifecycle state and preventing any subsequent scan attempts.

### Expected Behavior
- On Android 8–11 (API 26–30), `BLUETOOTH_ADMIN` is declared in `AndroidManifest.xml` with `android:maxSdkVersion="30"` so the OS grants it at install time.
- `BTSearchForNewDevicesEngine.startAsyncSearch()` checks both `BLUETOOTH_ADMIN` and `ACCESS_FINE_LOCATION` on API $< 31$ before attempting to scan.
- `scanner.startScan(...)` and `scanner.stopScan(...)` are defensively guarded against `SecurityException` and `IllegalStateException`, gracefully logging failures and restoring `scanning = false` without crashing.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Manifest Permission Regression Archaeology
A git forensic inspection of `app/src/main/AndroidManifest.xml` reveals the history of `BLUETOOTH_ADMIN`:
- In the original codebase (commit `845818c2cfe01890e20faa8249358f8546c00887`, "Initial Commit", 2019), `BLUETOOTH` and `BLUETOOTH_ADMIN` were both declared unconditionally:
  ```xml
  <uses-permission android:name="android.permission.BLUETOOTH" />
  <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
  ```
- In commit `f2f55e11cf48e9b4b29896478cb7d41f22566580` ("feat: Android permissions", July 26, 2024), Android 12 (API 31) runtime permissions (`BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`) were introduced. During that update, `BLUETOOTH` was capped with `android:maxSdkVersion="30"`, but `BLUETOOTH_ADMIN` was commented out entirely:
  ```xml
  <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30"/>
  <!-- uses-permission android:name="android.permission.BLUETOOTH_ADMIN" /-->
  <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
  <uses-permission android:name="android.permission.BLUETOOTH_SCAN" ... />
  ```
- The author presumed that `BLUETOOTH_ADMIN` was fully replaced by `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` across all Android versions. However, according to the Android platform specification:
  - On **Android 12+ (API 31+)**: `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` are runtime permissions that replace `BLUETOOTH` and `BLUETOOTH_ADMIN`.
  - On **Android 8.0 through Android 11 (API 26–30)**: `BluetoothLeScanner.startScan(...)` enforces `android.permission.BLUETOOTH_ADMIN` in the system Bluetooth service.
  - Commenting out `BLUETOOTH_ADMIN` meant the permission was absent from the package manifest for all devices, breaking BLE scanning on every device running Android 8 through Android 11.
  - The correct pattern for backward compatibility is:
    ```xml
    <uses-permission
        android:name="android.permission.BLUETOOTH_ADMIN"
        android:maxSdkVersion="30" />
    ```

### 2.2 Pre-Scan Runtime Permission Gap
In `BTSearchForNewDevicesEngine.java` lines 332–342:
```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "BLUETOOTH_SCAN permission not granted");
        return;
    }
} else {
    if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "ACCESS_FINE_LOCATION permission not granted");
        return;
    }
}
```
On API $< 31$, the method only checked `ACCESS_FINE_LOCATION`. It omitted checking `BLUETOOTH_ADMIN`.

### 2.3 Unguarded Call Sites and State Poisoning
In `BTSearchForNewDevicesEngine.java`:
- At line 350: `scanning = true;` is set before invoking the scanner.
- At line 367: `scanner.startScan(filters, settings, mScanCallback);` is invoked directly without `try/catch`.
  - When the platform throws `SecurityException` (or `IllegalStateException` if the adapter state changes concurrently), the exception propagates unhandled, terminating the thread or crashing the app.
  - Because `scanning = true` was already set, subsequent calls to `startAsyncSearch()` hit line 368 (`else if (DEBUG) Log.i(TAG, "already searching");`) and do nothing. The search engine is permanently locked.
- At line 386 in `stopAsyncSearch()`:
  `scanner.stopScan(mScanCallback);` is also invoked without `try/catch`. If permissions were revoked while scanning or if the Bluetooth adapter was disabled mid-scan, `stopScan()` can throw `SecurityException` or `IllegalStateException`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Restore Manifest Permission**: Re-enable `<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />` in `app/src/main/AndroidManifest.xml`.
  2. **Check `BLUETOOTH_ADMIN` on Legacy Platforms**: In `BTSearchForNewDevicesEngine.startAsyncSearch()`, verify `ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_ADMIN) == PERMISSION_GRANTED` on API $< 31$ alongside `ACCESS_FINE_LOCATION`.
  3. **Defensive Exception Handling**: Wrap `scanner.startScan(...)` in `try { ... } catch (SecurityException | IllegalStateException e)` in `startAsyncSearch()`. On exception, log an error, reset `scanning = false`, and return cleanly.
  4. **Defensive Teardown**: Wrap `scanner.stopScan(...)` in `try { ... } catch (SecurityException | IllegalStateException e)` in `stopAsyncSearch()`.
  5. **Unit Test Coverage**: Add unit tests in `BTSearchForNewDevicesEngineTest.kt` verifying:
     - Pre-API 31 permission abortion when `BLUETOOTH_ADMIN` is denied.
     - Graceful recovery and `scanning` state restoration when `startScan()` throws `SecurityException` or `IllegalStateException`.
     - Graceful handling when `stopScan()` throws `SecurityException` or `IllegalStateException`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying Android 12+ runtime permission dialogs or location permission prompting workflows in UI activities (tracked separately in ATT-3998).
  2. Altering ANT+ discovery or reconnection logic (`DeviceManager.java`, `MyANTDevice.java`).
  3. Refactoring peripheral telemetry parsers (`BTLEHeartRateDevice`, `BTLEBikePowerDevice`, etc.) or GATT notification queues (`REQ-CON-020`).
  4. Changing foreground service type declarations (`FOREGROUND_SERVICE_CONNECTED_DEVICE`) or FGS start lifecycles.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-CON-021` ("`BLUETOOTH_ADMIN` Manifest Declaration for Android 8–11 Compatibility and Defensive BLE Scan Exception Handling"), extending and refining `REQ-CON-019` (`ATT-2773`). Targets `app/src/main/AndroidManifest.xml` and `BTSearchForNewDevicesEngine.java`.
* **Historical Origin & Commit Trace**:
  - `Initial Commit` (`845818c2`, 2019): `BLUETOOTH_ADMIN` was active in `AndroidManifest.xml`.
  - Commit `f2f55e11cf48e9b4b29896478cb7d41f22566580` ("feat: Android permissions", July 26, 2024): Commented out `BLUETOOTH_ADMIN` when adding Android 12 permissions.
  - Sprint 2026-41.4 (`ATT-2773`, commit `53370e78`): Defined `REQ-CON-019` for direct `TRANSPORT_LE` and API 31+ `BLUETOOTH_SCAN` permissions, but omitted legacy `BLUETOOTH_ADMIN` verification and scan exception guards.
  - Discovery: Independent whole-app code review of `feature/ATT-3921` (`0234bfc0`), filed under ticket `ATT-3997`.
* **Root Reason for Existing Formulation**:
  The developer commenting out `BLUETOOTH_ADMIN` in commit `f2f55e1` mistakenly believed that Google Play Store and Android 12+ prohibited `BLUETOOTH_ADMIN` once `BLUETOOTH_SCAN` was added. In reality, the platform requires `maxSdkVersion="30"` so the permission is retained on older OS versions while ignored on modern ones. Furthermore, `startScan()` and `stopScan()` lacked defensive `try/catch` wrappers under the assumption that pre-checks were 100% sufficient to prevent platform runtime exceptions.
* **Preservation of Core Invariants**:
  Restoring `BLUETOOTH_ADMIN` with `android:maxSdkVersion="30"` strictly preserves all Android 12+ permission boundaries (`REQ-CON-019`), leaves existing peripheral drivers (`BTLEHeartRateDevice`, etc.) completely unchanged, and maintains the 100% clean-room test pass rate.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Manifest Correction (`app/src/main/AndroidManifest.xml`)
Re-enable `BLUETOOTH_ADMIN` bounded to API $\le 30$:
```xml
    <uses-permission
        android:name="android.permission.BLUETOOTH"
        android:maxSdkVersion="30" />
    <uses-permission
        android:name="android.permission.BLUETOOTH_ADMIN"
        android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
```

### 5.2 Legacy Permission Check in `BTSearchForNewDevicesEngine.java`
Update `startAsyncSearch()` to verify `BLUETOOTH_ADMIN` on API $< 31$:
```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "BLUETOOTH_SCAN permission not granted");
        return;
    }
} else {
    if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "BLUETOOTH_ADMIN permission not granted");
        return;
    }
    if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "ACCESS_FINE_LOCATION permission not granted");
        return;
    }
}
```

### 5.3 Defensive Exception Handling and State Protection
Wrap `scanner.startScan(...)` in `try/catch`:
```java
try {
    scanning = true;
    scanner.startScan(filters, settings, mScanCallback);
} catch (SecurityException | IllegalStateException e) {
    Log.e(TAG, "Failed to start BLE scan: " + e.getMessage(), e);
    scanning = false;
}
```
Wrap `scanner.stopScan(...)` in `try/catch`:
```java
if (scanning) {
    scanning = false;
    BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
    if (scanner != null) {
        try {
            scanner.stopScan(mScanCallback);
        } catch (SecurityException | IllegalStateException e) {
            Log.w(TAG, "Failed to stop BLE scan: " + e.getMessage(), e);
        }
    }
}
```

### 5.4 Test Blueprint (`BTSearchForNewDevicesEngineTest.kt`)
- `testStartAsyncSearch_onApiBelow31_missingBluetoothAdminPermission_abortsGracefully()`: Deny `BLUETOOTH_ADMIN`, verify scan does not start and `scanning` is false.
- `testStartAsyncSearch_startScanThrowsSecurityException_catchesAndResetsScanning()`: Mock `scanner.startScan` throwing `SecurityException`, verify method catches it and `scanning` is reset to false.
- `testStopAsyncSearch_stopScanThrowsSecurityException_catchesGracefully()`: Mock `scanner.stopScan` throwing `SecurityException`, verify method catches it and does not crash.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing BLE scanning, pairing, and reconnection on Android 12+.
  2. Full backward compatibility on Android 8–11 without triggering Google Play Store permission policy warnings (`maxSdkVersion="30"`).
  3. Search engine state integrity (`scanning` accurately reflects actual scan status; zero state lockup on platform exceptions).
  4. 100% clean-room test suite pass rate (`./gradlew testDebugUnitTest`).
  5. Parent ticket Human Decision Gate remains strictly guarded.
* **Risk Rating**: **LOW**
  - Justification: Restoring an install-time permission capped at API 30 is a standard, low-risk platform fix. Adding defensive `try/catch` around external framework Binder calls prevents crashes without altering any existing business logic, database structures, or UI components.
