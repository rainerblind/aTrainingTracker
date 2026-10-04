# Stage 3 Implementation Plan: ATT-2194 - Fix Sensor Connection Status in SensorSourceDialog Showing Connected Devices as Disconnected

**Ticket**: [ATT-2194](https://rainerblind.atlassian.net/browse/ATT-2194)  
**Sub-task**: [ATT-2363](https://rainerblind.atlassian.net/browse/ATT-2363) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-268`  
**Test Mapping**: `TST-UI-227`  
**Branch**: `feature/ATT-2194`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Architectural Design & System Decomposition (SWE.2)

### 1.1 Architectural Boundary
* **Layer**: UI Presentation (Jetpack Compose).
* **Components Affected**:
  - `com.atrainingtracker.banalservice.ui.devices.DeviceStatusRow`: Reusable technical status composable displaying battery icon, battery percentage, and connection/last seen state.
  - `com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialog`: Bottom sheet composable displaying the multi-tiered sensor telemetry hierarchy (Source Device, Active Backups, Not Connected).
* **Threading & Concurrency**:
  - Pure declarative UI composition on Android UI thread / Compose recomposition.
  - No database mutations, background worker interactions, or asynchronous dispatchers required.

### 1.2 Data Flow Architecture
```text
SensorSourceDialog (receives allTelemetry & sensorType)
  │
  ├─► allActiveTelemetries (telemetry with values for sensorType)
  │     ├─► sourceDevice (active primary source) ──► DeviceIdentityBlock(isConnected = true)
  │     └─► activeBackups (active secondary sources) ──► DeviceIdentityBlock(isConnected = true)
  │
  ├─► notConnected (paired devices without active telemetry) ──► DeviceIdentityBlock(isConnected = false)
  │
  └─► DeviceIdentityBlock(device, isConnected)
        │
        ├─► Surface (Connection Status LED: green when isConnected=true, grey when false)
        │
        └─► DeviceStatusRow(device, isConnected = isConnected)
              │
              └─► stateText:
                    if (isConnected) -> R.string.devices_available ("verfügbar" / "available")
                    else if (lastSeen.isNotEmpty()) -> relativeTime (e.g. "vor 2 Std.")
                    else -> R.string.devices_not_connected ("nicht verbunden" / "not connected")
```

---

## 2. Atomic Step Sequencing

### Step 1: Parameterize `DeviceStatusRow.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/DeviceStatusRow.kt`
* **Changes**:
  1. Add `isConnected: Boolean = device.isConnected` as an optional parameter to `DeviceStatusRow(...)`.
  2. In the `stateText` evaluation:
     ```kotlin
     val stateText = if (isConnected) {
         stringResource(R.string.devices_available)
     } else if (relativeTime.isNotEmpty()) {
         relativeTime
     } else {
         stringResource(R.string.devices_not_connected)
     }
     ```
  3. Ensure that when `isConnected` is omitted, the function behaves identically to the current implementation (`device.isConnected`), preserving 100% backward compatibility for `DeviceItem.kt` and `EditDeviceDialog.kt`.

### Step 2: Propagate Connection State in `SensorSourceDialog.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt`
* **Changes**:
  1. In `DeviceIdentityBlock`:
     ```kotlin
     DeviceStatusRow(
         device = device,
         isConnected = isConnected,
         alpha = TTAlpha.Medium,
         textStyle = MaterialTheme.typography.bodySmall
     )
     ```
  2. For `sourceDevice` and `activeBackups`, `DeviceIdentityBlock` is invoked with `isConnected = true`, causing `DeviceStatusRow` to render `R.string.devices_available`.
  3. For `notConnected`, `DeviceIdentityBlock` is invoked with `isConnected = false`, causing `DeviceStatusRow` to render relative last seen or `R.string.devices_not_connected`.

### Step 3: Implement Unit & Contract Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/DeviceStatusRowTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialogContractTest.kt`
* **Tests Implemented**:
  1. `DeviceStatusRowTest`:
     - `testDeviceStatusRow_whenIsConnectedTrue_evaluatesToAvailable`: Verifies `isConnected = true` produces `R.string.devices_available` even if `device.isConnected == false`.
     - `testDeviceStatusRow_whenIsConnectedFalse_evaluatesToNotConnectedOrRelativeTime`: Verifies `isConnected = false` produces `R.string.devices_not_connected` when `lastSeen` is null.
     - `testDeviceStatusRow_defaultParameter_evaluatesToDeviceIsConnected`: Verifies default parameter fallback matches `device.isConnected`.
  2. `SensorSourceDialogContractTest`:
     - Verifies `SensorSourceDialog.kt` contains the parameter forwarding `isConnected = isConnected` within `DeviceIdentityBlock`.

### Step 4: Targeted Test Execution
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.DeviceStatusRowTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialogContractTest"
  ```

---

## 3. Invariant Protection & Governance

1. **Non-Breaking Call Sites**: Existing callers (`DeviceItem.kt`, `EditDeviceDialog.kt`) omit `isConnected` and continue to resolve `device.isConnected`.
2. **Disconnected Devices Integrity**: Devices in the "Nicht verbunden" list retain their existing behavior (`isConnected = false`), correctly displaying `(nicht verbunden)` or relative last seen time.
3. **High-Density 2-Row Alignment**: The 18dp horizontal center axis alignment between status LED and battery icon is completely preserved.
4. **Mandatory Pre-Check**: Before modifying production code in Stage 4, verify `python3 tools/jira_util.py check-gate ATT-2363` exits with code 0 (`GATE_PASSED: ATT-2363 is Erledigt`).
