# Stage 1 Analysis: ATT-2625 - Remove Alle button from device type selection dialog in sensor pairing flow

**Ticket**: [ATT-2625](https://atrainingtracker.atlassian.net/browse/ATT-2625)  
**Sub-task**: [ATT-2685](https://atrainingtracker.atlassian.net/browse/ATT-2685) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2625`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.1 review on physical device (Google Pixel 10), sensor pairing via the '+' Floating Action Button in the Sensors screen ("Meine Sensoren", `DevicesTabbedScreen.kt`) was tested.
When clicking the FAB:
1. The athlete selects the protocol (`Protocol.ANT_PLUS` or `Protocol.BLUETOOTH_LE`) via `PairingProtocolBottomSheet`.
2. A bottom sheet dialog (`DeviceTypeSelectionDialog`) opens listing specific hardware sensor profiles (Heart Rate, Power, Speed/Cadence, etc.).
3. At the very bottom of this dialog, an "Alle" ("All") button is displayed in the bottom action bar.

### Problem Statement
The "Alle" button in the pairing dialog makes no sense in this flow: sensor pairing scans must be targeted to a specific wireless protocol and hardware profile (e.g. Heart Rate Strap, Power Meter, Bike Radar). Selecting "Alle" causes ambiguous scanning behavior, violates profile-specific GATT/ANT+ discovery constraints, and confuses athletes during sensor pairing setup.

### Expected Behavior
Remove the "Alle" button from the device type selection dialog in the sensor pairing flow, presenting strictly the valid hardware device type options for the chosen protocol.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Historical Emergence of the "Alle" Action
* `DeviceTypeSelectionDialog` was originally introduced in commit `d4b2e864b7` (SCRUM-37) as an overlay on `ControlTrackingScreen` to filter the sensor list. In that legacy context, selecting "Alle" served as an unfiltered view of all known sensors.
* In commit `4cb34fba`, `DeviceTypeSelectionDialog` was modernized into an `AppModalBottomSheet`. The "Alle" button was placed into the `actions` parameter of `AppModalBottomSheet`:
  ```kotlin
  AppModalBottomSheet(
      title = stringResource(R.string.select_device_type),
      onDismissRequest = onDismiss,
      actions = {
          TextButton(
              onClick = { onSelected(DeviceType.ALL) },
              modifier = Modifier.fillMaxWidth()
          ) {
              Text(stringResource(R.string.devices_all))
          }
      }
  ) {
      // List of specific DeviceType items
  }
  ```
* In Sprint 2026-41.1 (`ATT-2189`), the dedicated pairing Floating Action Button was integrated into `DevicesTabbedScreen.kt`. It re-used `DeviceTypeSelectionDialog` as the second step of the pairing sequence.
* Because `DeviceTypeSelectionDialog` unconditionally defined the `actions` block with `TextButton` calling `onSelected(DeviceType.ALL)`, the pairing flow presented "Alle" at the bottom of the device type list.

### 2.2 Caller Investigation
Across the entire repository, `DeviceTypeSelectionDialog` is referenced in exactly two places:
1. `DevicesTabbedScreen.kt` (line 328): Sensor pairing FAB flow.
2. `ControlTrackingScreen.kt` (line 420): Quick pairing button from the tracking control screen, which emits `ControlNavigation.ToPairing(protocol, deviceType)`.

In both invocation points, the dialog is used strictly as a **pairing flow step** to initiate discovery for a new physical sensor. Neither caller requires or benefits from a `DeviceType.ALL` option.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Objectives**:
  1. Remove the "Alle" button from `DeviceTypeSelectionDialog` in the sensor pairing flow.
  2. Support configuration via parameter `showAllOption: Boolean = false` (defaulting to `false`) or completely remove the `actions` block so that `AppModalBottomSheet` renders no bottom action bar or divider.
  3. Ensure both `DevicesTabbedScreen` and `ControlTrackingScreen` present only explicit hardware device types (`DeviceType.getRemoteDeviceTypes(protocol)`).
  4. Add unit and contract tests verifying that `DeviceTypeSelectionDialog` does not expose or trigger `DeviceType.ALL` during pairing.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Sensor filter persistence or Sensors screen tab visibility (which is addressed in sibling ticket `ATT-2626`).
  * Modifying `EquipmentSportSensorMatrixScreen.kt` or bike/shoe tabs.
  * Altering BLE or ANT+ hardware scanning adapters in `banalservice`.

---

## 4. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

* **Original Requirement ID & Target**: `REQ-UI-042` (*Sensor Selection Flow and Navigation Overlay*) and `REQ-UI-278` (*Sensors Screen Pairing FAB & Protocol Selection*).
* **Historical Origin & Commit Trace**: Commit `d4b2e864b7` (SCRUM-37) and `43b35eb6` (ATT-2189).
* **Root Reason for Existing Formulation**: The "Alle" button was originally placed as a confirm/reset button to navigate to an unfiltered sensor list. In a dedicated pairing initiation flow, however, "Alle" is semantically invalid because discovery scans must target a specific hardware profile.
* **Preservation of Core Invariants**:
  - All valid hardware device types for the selected protocol (`DeviceType.getRemoteDeviceTypes(protocol)`) remain visible with crisp icons and localized names.
  - Dialog dismissal via tap-outside, swipe-down, or top-right close button remains intact.
  - Protocol-to-device-type pairing contract remains intact.

---

## 5. Architectural Strategy & High-Level Solution

In `DeviceTypeSelectionDialog.kt`:
* Add optional parameter `showAllOption: Boolean = false`.
* Bind `AppModalBottomSheet`'s `actions` slot conditionally:
  ```kotlin
  actions = if (showAllOption) {
      {
          TextButton(
              onClick = { onSelected(DeviceType.ALL) },
              modifier = Modifier.fillMaxWidth()
          ) {
              Text(stringResource(R.string.devices_all))
          }
      }
  } else null
  ```
* When `showAllOption` is `false` (the default for pairing flows), `actions` is `null`. `AppModalBottomSheet` omits the bottom action bar and divider entirely, leaving a clean, scrollable list of specific hardware sensor profiles.
