# Stage 1 Analysis: ATT-2194 - Fix Sensor Connection Status in SensorSourceDialog Showing Connected Devices as Disconnected

**Ticket**: [ATT-2194](https://rainerblind.atlassian.net/browse/ATT-2194)  
**Sub-task**: [ATT-2361](https://rainerblind.atlassian.net/browse/ATT-2361) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Branch**: `feature/ATT-2194`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Executive Summary & Problem Statement

In the tracking cockpit's sensor source dialog (`SensorSourceDialog.kt`), tapping any metric icon in `SensorStatus` opens a bottom sheet showing the sensor redundancy chain:
1. **Quelle** (`source_device`): Primary active telemetry provider.
2. **Aktive Backups** (`source_active_backups`): Redundant secondary sensors delivering live telemetry.
3. **Nicht verbunden** (`source_not_connected`): Paired sensors not currently connected or delivering data.

### The Defect
When viewing active devices under "Quelle" or "Aktive Backups", each device displays a green technical status LED (`TTColor.ConnectionStatusGreen`) indicating active connection and live data streaming (e.g. smartphone barometer showing live `437 m`). However, the secondary status row immediately below the device name renders:
`100% (nicht verbunden)` / `100% (not connected)`

This contradictory visual state (green "connected" LED + red/grey "not connected" text) causes immediate confusion for athletes during tracking sessions, falsely implying that the sensor connection has dropped or that telemetry is simulated/stale.

---

## 2. Root Cause Analysis (Forensic Investigation)

A forensic investigation into `SensorSourceDialog.kt`, `DeviceStatusRow.kt`, and `DeviceDataRepository.kt` revealed the exact cause of this parameter disconnection:

### 2.1 Live Telemetry Evaluation in `SensorSourceDialog.kt`
In `SensorSourceDialog.kt` (lines 67–74, 110–145):
```kotlin
val allActiveTelemetries = remember(allTelemetry, sensorType) {
    allTelemetry.filter { telemetry -> telemetry.allValues.any { it.sensor == sensorType } }
}
val activeBackups = remember(allActiveTelemetries, sourceDevice) {
    allActiveTelemetries.filter { it.deviceId != sourceDevice?.id }
}
```
When rendering `sourceDevice` and `activeBackups`, `SensorSourceDialog` explicitly computes:
```kotlin
DeviceIdentityBlock(
    device = sourceDevice, 
    isConnected = true, 
    valueWithUnit = if (value != "--") "$value $unit" else value,
    ...
)
```
and for each backup:
```kotlin
DeviceIdentityBlock(
    device = device, 
    isConnected = true, 
    valueWithUnit = if (value != "--") "$value $unit" else value,
    ...
)
```

### 2.2 Broken Parameter Forwarding in `DeviceIdentityBlock`
In `SensorSourceDialog.kt` (lines 201–266):
```kotlin
@Composable
private fun DeviceIdentityBlock(
    device: DeviceUiData, 
    isConnected: Boolean,
    valueWithUnit: String? = null,
    modifier: Modifier = Modifier
) {
    ...
    // The LED correctly receives the live isConnected parameter:
    Surface(
        modifier = Modifier.size(10.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (isConnected) TTColor.ConnectionStatusGreen else Color.LightGray,
        tonalElevation = 2.dp
    ) {}
    ...
    // BUT DeviceStatusRow is called without isConnected!
    DeviceStatusRow(
        device = device,
        alpha = TTAlpha.Medium,
        textStyle = MaterialTheme.typography.bodySmall
    )
}
```

### 2.3 Hardcoded Evaluation on Stale `device.isConnected` in `DeviceStatusRow.kt`
In `DeviceStatusRow.kt` (lines 40–77):
```kotlin
@Composable
fun DeviceStatusRow(
    device: DeviceUiData,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    alpha: Float = TTAlpha.Medium
) {
    ...
    val relativeTime = getRelativeLastSeen(device.lastSeen)
    val stateText = if (device.isConnected) {
        stringResource(R.string.devices_available)
    } else if (relativeTime.isNotEmpty()) {
        relativeTime
    } else {
        stringResource(R.string.devices_not_connected)
    }

    Text(
        text = "$batteryText ($stateText)",
        ...
    )
}
```
* `DeviceUiData` originates from `DeviceDataRepository.kt` / `RawDeviceDataProvider.kt`, which loads persistent device definitions from SQLite.
* In SQLite, paired devices do not hold a permanent connection flag; connection is an ephemeral session state managed by `BANALService` and broadcast via `DeviceTelemetry`.
* Consequently, `device.isConnected` is `false` by default on cached `DeviceUiData` instances.
* Because `DeviceStatusRow` evaluated `device.isConnected` instead of allowing a caller-driven connection state override, it always fell through to `stringResource(R.string.devices_not_connected)`, displaying `(nicht verbunden)`.

---

## 3. Proposed Resolution & Architecture

### 3.1 Parameterized Connection Override in `DeviceStatusRow.kt`
Extend `DeviceStatusRow` with an optional connection state parameter:
```kotlin
@Composable
fun DeviceStatusRow(
    device: DeviceUiData,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    alpha: Float = TTAlpha.Medium,
    isConnected: Boolean = device.isConnected
) {
    ...
    val stateText = if (isConnected) {
        stringResource(R.string.devices_available)
    } else if (relativeTime.isNotEmpty()) {
        relativeTime
    } else {
        stringResource(R.string.devices_not_connected)
    }
    ...
}
```
* **Default Argument Invariant**: Defaulting to `device.isConnected` ensures 100% backward compatibility for other callers (`DeviceItem.kt`, `EditDeviceDialog.kt`).

### 3.2 Propagation in `SensorSourceDialog.kt`
In `DeviceIdentityBlock`:
```kotlin
DeviceStatusRow(
    device = device,
    isConnected = isConnected,
    alpha = TTAlpha.Medium,
    textStyle = MaterialTheme.typography.bodySmall
)
```
* When `isConnected == true` (`sourceDevice` or `activeBackups`), `DeviceStatusRow` evaluates `isConnected == true` and renders `R.string.devices_available` ("verfügbar" in German, "available" in English).
* When `isConnected == false` (`notConnected` list), `DeviceStatusRow` evaluates `isConnected == false`, rendering the relative timestamp or `R.string.devices_not_connected` ("nicht verbunden").

---

## 4. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Refines `REQ-UI-050` (*Optimized Sensor Identity Layout*) and `REQ-UI-049` (*Sensor Source Dialog*).
2. **Historical Origin & Commit Trace**:
   - `REQ-UI-050` established the 2-row identity block (Icon, Name/LED, Battery/Status) in `SensorSourceDialog`.
   - `REQ-UI-149` modernized `SensorSourceDialog` into `AppModalBottomSheet`.
3. **Root Reason for Existing Formulation**:
   - `DeviceStatusRow` was originally constructed for the paired devices list (`DeviceItem.kt`), where `DeviceUiData` was assumed to be the sole data carrier.
   - When `SensorSourceDialog` was modernized into Compose, `DeviceIdentityBlock` passed `isConnected` to the circular LED `Surface` but omitted it when calling `DeviceStatusRow`.
4. **Preservation of Core Invariants**:
   - Truly disconnected devices in `notConnected` list continue to show `(nicht verbunden)` or relative last seen time.
   - Battery icon and percentage remain intact (`$batteryText ($stateText)`).
   - LED 18dp horizontal axis alignment with the battery icon is strictly preserved.
   - Zero changes to `DeviceItem.kt` or `EditDeviceDialog.kt`.

---

## 5. User Scope Grounding (`ATT-1250`)

* **In-Scope**:
  - `DeviceStatusRow.kt`: Add `isConnected: Boolean = device.isConnected` parameter and use it in state determination.
  - `SensorSourceDialog.kt`: Forward `isConnected` from `DeviceIdentityBlock` to `DeviceStatusRow`.
  - Automated Unit Tests: Contract and behavior tests verifying `DeviceStatusRow` displays "verfügbar" / "available" when `isConnected = true`, and "nicht verbunden" when `isConnected = false`.
* **Out-of-Scope**:
  - Modifying `BANALService` BLE or ANT+ connection management.
  - Modifying SQLite database schemas in `DevicesDatabaseManager`.
  - Redesigning `EditDeviceDialog.kt` or `DeviceItem.kt`.

---

## 6. Verification & Test Strategy

1. **Unit Test (`DeviceStatusRowTest.kt` / `SensorSourceDialogTest.kt`)**:
   - Test 1: Verify `DeviceStatusRow` with `isConnected = true` renders `R.string.devices_available` even if `device.isConnected == false`.
   - Test 2: Verify `DeviceStatusRow` with `isConnected = false` renders `R.string.devices_not_connected` or relative time.
   - Test 3: Verify `DeviceStatusRow` defaults to `device.isConnected` when `isConnected` parameter is omitted.
2. **Visual & Contract Tests**:
   - Verify `SensorSourceDialog.kt` propagates `isConnected` from `DeviceIdentityBlock` to `DeviceStatusRow`.
3. **Clean-Room Regression**:
   - Execute `./gradlew testDebugUnitTest` ensuring 100% pass rate.
