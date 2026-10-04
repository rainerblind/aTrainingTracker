# Stage 1 Analysis: ATT-2057 - Hide Research Button When No Real Paired Remote Devices Exist in Database

**Ticket**: [ATT-2057](https://rainerblind.atlassian.net/browse/ATT-2057)  
**Sub-task**: [ATT-2232](https://rainerblind.atlassian.net/browse/ATT-2232) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Branch**: `feature/ATT-2057`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

On the workout setup and control screen (`ControlTrackingScreen`, Page 0 of `TrackingTabsScreen`), the "Suchen" (`ResearchButton`) action button is currently unconditionally displayed at the top-left of the screen, anchored next to the central telemetry status area.

When an athlete has previously paired external sports sensors (such as ANT+ or Bluetooth LE heart rate monitors, bike power meters, cadence sensors, or speed sensors), this button serves an essential purpose: triggering a background search and reconnection cycle for those paired devices (`startSearchingForPairedDevices()`).

However, on a fresh application installation or whenever the athlete has not paired any real remote devices (or has unpaired all of them), the database contains zero external hardware sensors. In this state, only internal device records exist (e.g. `Protocol.SMARTPHONE` entries representing GPS, Network Location, Google Fused Location, Phone Battery, and Barometric Altimeter). For such athletes, rendering the "Suchen" button is confusing, misleading, and non-functional—tapping it triggers a search queue for 0 devices.

**Objective**: The "Suchen" button (`ResearchButton`) must only be rendered when at least one real, paired remote device (protocol `ANT_PLUS` or `BLUETOOTH_LE` with `isPaired == true`) exists in the database.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 UI Composition Analysis
In `ControlTrackingScreen.kt` (lines 166–193), the information area and research button are laid out within a `Box`:
```kotlin
Box(modifier = Modifier
    .fillMaxWidth()
    .padding(bottom = 8.dp)
) {
    // The Information Area - Anchored to the MATHEMATICAL CENTER of the screen
    Column(
        modifier = Modifier.align(Alignment.TopCenter),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SearchArea(searchingFor = searchingFor)
        RemoteDevices(devices = devices, onDeviceClick = onDeviceClick)
    }

    // Research Button - Anchored to the far left of the screen
    Box(modifier = Modifier.align(Alignment.TopStart)) {
        ResearchButton(
            isEnabled = searchingFor == null,
            onClick = onSearch
        )
    }
}
```
Currently, `ResearchButton` is composed unconditionally. Its only input parameter is `isEnabled = searchingFor == null`. There is no conditional check on whether any paired remote devices exist.

Because the central `Column` is anchored with `Alignment.TopCenter` within a `fillMaxWidth()` container, hiding `ResearchButton` (`Alignment.TopStart`) does not cause any layout shift or misalignment for the central search area or connected device chips.

### 2.2 Device Representation & Database Schema
In `DevicesDatabaseManager.java` and `DevicesDbHelper`:
- Internal devices are inserted with `PROTOCOL = Protocol.SMARTPHONE.name()` (`SPEED_AND_LOCATION_GPS`, `SPEED_AND_LOCATION_NETWORK`, `SPEED_AND_LOCATION_GOOGLE_FUSED`, `ALTITUDE_FROM_PRESSURE`, `BATTERY`).
- External remote sensors are inserted with `PROTOCOL = Protocol.ANT_PLUS.name()` or `PROTOCOL = Protocol.BLUETOOTH_LE.name()` (`HRM`, `BIKE_POWER`, `BIKE_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`).
- The `PAIRED` column indicates whether the device is currently paired (`PAIRED > 0`).

In `DeviceManager.java` (lines 537–538, 881–897), `startSearchForPairedDevices()` exclusively searches for devices matching:
```sql
SELECT ... FROM Devices WHERE protocol = ? AND paired > 0
```
where `protocol` is `Protocol.ANT_PLUS` or `Protocol.BLUETOOTH_LE`.

### 2.3 Reactive State Pipeline
In `ControlTrackingViewModel.kt`:
- `private val allDevicesFromDb = devicesRepository.allDevices` is a `StateFlow<List<DeviceUiData>>` backed by `DeviceDataRepository`.
- Each `DeviceUiData` exposes:
  - `protocol: Protocol`
  - `deviceType: DeviceType`
  - `isPaired: Boolean`
- Whenever devices are added, paired, unpaired, or deleted, `DeviceDataRepository` updates `_allDevices`.

### 2.4 Gap Analysis
Currently:
1. `DevicesDatabaseManager` lacks a direct synchronous helper `hasPairedRemoteDevices(): Boolean` to quickly check SQLite during cold start.
2. `ControlTrackingViewModel` does not expose a `hasPairedRemoteDevices: StateFlow<Boolean>` derived from `allDevicesFromDb`.
3. `ControlTrackingScreen` does not accept a `showResearchButton: Boolean` parameter and renders `ResearchButton` unconditionally.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Implement `hasPairedRemoteDevices(): Boolean` in `DevicesDatabaseManager` with a fast `LIMIT 1` query.
  * Expose `val hasPairedRemoteDevices: StateFlow<Boolean>` in `ControlTrackingViewModel`.
  * Parameterize `ControlTrackingScreen` with `showResearchButton: Boolean = true` (or passed from ViewModel).
  * Wrap `ResearchButton` in `if (showResearchButton)` in `ControlTrackingScreen.kt`.
  * Forward `hasPairedRemoteDevices` from `ControlTrackingViewModel` in `TrackingTabsScreen.kt`.
  * Provide Compose Previews illustrating both states (with and without `ResearchButton`).
  * Verify 100% brand compliance: Authentic ANT+ and Bluetooth logos remain untouched (`AC-3`).
  * Add unit tests verifying state emissions and contract behavior (`TST-UI-218`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Altering the BLE / ANT+ search state machine or connection logic in `DeviceManager` or `BANALService`.
  * Modifying the internal layout or styling of `ResearchButton.kt`.
  * Changing `PairingButtons` or the pairing flow.
  * Modifying internal smartphone location or battery sensor handling.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Requirement Status**: Net-new requirement only (`REQ-UI-259` and `TST-UI-218`). No existing requirements in `docs/requirements.md` are modified.
* **Chesterton's Fence Audit**:
  * Existing requirements referencing `ControlTrackingScreen.kt` and `ControlTrackingViewModel.kt` include:
    - `REQ-PRI-003`: Contextual JIT permission flow and permission warning badges.
    - `REQ-UI-183`: Lieblingsorte cockpit location badge and barometer calibration feedback.
    - `REQ-UI-199`: Altimeter calibration dispatch synchronization.
  * None of these requirements specify or constrain the rendering behavior of `ResearchButton`. All existing invariants (permission flows, badges, calibration, layout) are preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 SQLite Precondition Check (`DevicesDatabaseManager.java`)
Add:
```java
public boolean hasPairedRemoteDevices() {
    Cursor cursor = getDatabase().query(
            DevicesDbHelper.DEVICES,
            new String[]{DevicesDbHelper.C_ID},
            DevicesDbHelper.PAIRED + " > 0 AND (" + DevicesDbHelper.PROTOCOL + "=? OR " + DevicesDbHelper.PROTOCOL + "=?)",
            new String[]{Protocol.ANT_PLUS.name(), Protocol.BLUETOOTH_LE.name()},
            null,
            null,
            null,
            "1"
    );
    if (cursor != null) {
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }
    return false;
}
```

### 5.2 Reactive State Pipeline (`ControlTrackingViewModel.kt`)
In `ControlTrackingViewModel`:
```kotlin
val hasPairedRemoteDevices: StateFlow<Boolean> = allDevicesFromDb
    .map { devices ->
        devices.any { it.isPaired && (it.protocol == Protocol.ANT_PLUS || it.protocol == Protocol.BLUETOOTH_LE) }
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = devicesDatabaseManager.hasPairedRemoteDevices()
    )
```

### 5.3 UI Conditional Rendering (`ControlTrackingScreen.kt`)
In `ControlTrackingScreen`:
```kotlin
@Composable
fun ControlTrackingScreen(
    ...
    showResearchButton: Boolean = true,
    ...
) {
    ...
    if (showResearchButton) {
        Box(modifier = Modifier.align(Alignment.TopStart)) {
            ResearchButton(
                isEnabled = searchingFor == null,
                onClick = onSearch
            )
        }
    }
    ...
}
```

In `TrackingTabsScreen.kt`:
```kotlin
val hasPairedRemoteDevices by controlViewModel.hasPairedRemoteDevices.collectAsState()
...
ControlTrackingScreen(
    ...
    showResearchButton = hasPairedRemoteDevices,
    ...
)
```

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression across the full test suite (1529+ passing unit tests).
  2. Authentic ANT+ and Bluetooth logos remain completely unmodified (AC-3).
  3. Mathematical center alignment of the search status and connected devices column is preserved.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - Justification: The change is purely a UI visibility condition based on an existing reactive flow (`DeviceDataRepository.allDevices`) with zero database schema migrations and zero alterations to background service threads.
