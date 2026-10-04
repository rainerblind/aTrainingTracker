# Stage 2: Requirement & Test Specification - ATT-2194: Fix Sensor Connection Status in SensorSourceDialog Showing Connected Devices as Disconnected

**Ticket**: [ATT-2194](https://rainerblind.atlassian.net/browse/ATT-2194)  
**Sub-task**: [ATT-2362](https://rainerblind.atlassian.net/browse/ATT-2362) (`[Test-Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-268`  
**Test Mapping**: `TST-UI-227`  
**Branch**: `feature/ATT-2194`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (`REQ-UI-268`)

### 1.1 Formal Specification
| Requirement ID | Module | Title | Target Description |
| :--- | :--- | :--- | :--- |
| **REQ-UI-268** | **UI / Tracking** | **Truthful Live Sensor Connection Status in SensorSourceDialog and Parameterized Override in DeviceStatusRow.** | The system SHALL truthfully reflect active sensor connection status in the tracking cockpit's `SensorSourceDialog` by parameterizing `DeviceStatusRow` and propagating live telemetry connection state from `DeviceIdentityBlock` (ATT-2194):<br>1. *Parameterized Connection Override in DeviceStatusRow (`DeviceStatusRow.kt`)*: `DeviceStatusRow` SHALL declare `isConnected: Boolean = device.isConnected` as an optional parameter. When computing `stateText`, the composable SHALL evaluate `isConnected`: (a) When `isConnected == true`, `stateText` SHALL evaluate to `stringResource(R.string.devices_available)` ("verfügbar" / "available"); (b) When `isConnected == false`, `stateText` SHALL evaluate to relative time if `device.lastSeen` is present, or `stringResource(R.string.devices_not_connected)` ("nicht verbunden" / "not connected").<br>2. *Live Telemetry Propagation in SensorSourceDialog (`SensorSourceDialog.kt`)*: In `DeviceIdentityBlock`, `DeviceStatusRow` SHALL be invoked with `isConnected = isConnected`. For primary active sources (`sourceDevice`) and active backups (`activeBackups`), `isConnected` SHALL evaluate to `true`, rendering `R.string.devices_available` next to the battery percentage. For disconnected devices (`notConnected`), `isConnected` SHALL evaluate to `false`, rendering `R.string.devices_not_connected` or relative last seen.<br>3. *Strict Preservation of Backward Compatibility*: All existing call sites of `DeviceStatusRow` omitting `isConnected` (`DeviceItem.kt`, `EditDeviceDialog.kt`) SHALL continue to resolve `device.isConnected` without behavioral alteration.<br>4. *Layout and Alignment Invariants*: The 2-row high-density layout (Icon, Name/LED, Battery/Status), 18dp horizontal center axis alignment between status LED and battery icon, and 100% clean-room test suite pass rate MUST NOT be broken. |

---

### 1.2 Chesterton's Fence Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Refines and supersedes the status row coupling in `REQ-UI-050` (*Optimized Sensor Identity Layout*) and aligns with `REQ-UI-049` (*Sensor Source Dialog*).
2. **Historical Origin & Commit Trace**:
   - `REQ-UI-050` introduced the 2-row identification block in `SensorSourceDialog` without manufacturer name to maximize clarity.
   - `REQ-UI-149` modernized `SensorSourceDialog` into `AppModalBottomSheet`.
3. **Root Reason for Existing Formulation**:
   - `DeviceStatusRow` was originally constructed for paired devices lists (`DeviceItem.kt`), where persistent `DeviceUiData` was assumed to hold all display state.
   - When reused inside `SensorSourceDialog`, runtime telemetry connection state was computed dynamically by the dialog but only forwarded to the circular LED `Surface`, leaving `DeviceStatusRow` to read stale `device.isConnected` (`false`).
4. **Preservation of Core Invariants**:
   - Truly disconnected devices in `notConnected` list continue to show `(nicht verbunden)` or relative last seen time.
   - Battery icon and percentage remain intact (`$batteryText ($stateText)`).
   - LED 18dp horizontal axis alignment with the battery icon is strictly preserved.

---

### 1.3 Given-When-Then Acceptance Criteria

#### Scenario 1: Viewing Active Primary Source Device in SensorSourceDialog
* **Given** an athlete in the tracking cockpit with an actively connected primary sensor (e.g. smartphone barometer streaming altitude, or HR chest strap streaming heart rate),
* **When** tapping the corresponding sensor metric in `SensorStatus` to open `SensorSourceDialog`,
* **Then** the primary device under "Quelle" SHALL display a green connection LED and its status row SHALL display the battery percentage followed by `(verfügbar)` (or localized equivalent, e.g. `(available)`) and SHALL NOT display `(nicht verbunden)`.

#### Scenario 2: Viewing Active Backup Sensors in SensorSourceDialog
* **Given** secondary sensors actively streaming telemetry for the inspected metric,
* **When** viewing the "Aktive Backups" section in `SensorSourceDialog`,
* **Then** every active backup sensor SHALL display a green connection LED and its status row SHALL display `(verfügbar)`.

#### Scenario 3: Viewing Disconnected Paired Sensors in SensorSourceDialog
* **Given** paired sensors that are not currently connected or delivering telemetry,
* **When** viewing the "Nicht verbunden" section in `SensorSourceDialog`,
* **Then** their status row SHALL display `(nicht verbunden)` or the relative last-seen timestamp (e.g. `vor 2 Std.`).

#### Scenario 4: Preserving Standalone DeviceStatusRow in Device Lists
* **Given** paired device lists in `DeviceItem.kt` or `EditDeviceDialog.kt` where `isConnected` is not explicitly passed,
* **When** `DeviceStatusRow` is rendered,
* **Then** it SHALL default cleanly to `device.isConnected`.

---

## 2. Test Specification (`TST-UI-227`)

### 2.1 Test Cases Breakdown

| Test Case ID | Class / Unit Under Test | Description & Validation Target | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TST-UI-227.1` | `DeviceStatusRowTest` | Verify `DeviceStatusRow` renders `R.string.devices_available` when `isConnected = true`, even if `device.isConnected == false`. | Evaluates to `devices_available`. |
| `TST-UI-227.2` | `DeviceStatusRowTest` | Verify `DeviceStatusRow` renders `R.string.devices_not_connected` when `isConnected = false` and `lastSeen` is null. | Evaluates to `devices_not_connected`. |
| `TST-UI-227.3` | `DeviceStatusRowTest` | Verify `DeviceStatusRow` defaults `isConnected` to `device.isConnected` when parameter is omitted. | Matches `device.isConnected` behavior. |
| `TST-UI-227.4` | `SensorSourceDialogContractTest` | Verify `SensorSourceDialog.kt` passes `isConnected = isConnected` from `DeviceIdentityBlock` to `DeviceStatusRow`. | Contract check passes. |
| `TST-UI-227.5` | Full Suite Clean-Room Regression | Run `./gradlew testDebugUnitTest`. | 100% pass rate with zero failures. |

---

## 3. Traceability Matrix

| Requirement Clause | Test Specification | Verification Method |
| :--- | :--- | :--- |
| `REQ-UI-268` Clause 1 (DeviceStatusRow isConnected parameter) | `TST-UI-227.1`, `TST-UI-227.2`, `TST-UI-227.3` | Automated Unit Test (`DeviceStatusRowTest`) |
| `REQ-UI-268` Clause 2 (SensorSourceDialog live propagation) | `TST-UI-227.4` | Architectural Contract Test (`SensorSourceDialogContractTest`) |
| `REQ-UI-268` Clause 3 (Backward compatibility preservation) | `TST-UI-227.3` | Automated Unit Test (`DeviceStatusRowTest`) |
| `REQ-UI-268` Clause 4 (Full system invariants) | `TST-UI-227.5` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) |
