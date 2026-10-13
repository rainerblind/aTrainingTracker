# Stage 3: Implementation Plan - ATT-3099: Highlight and prioritize newly discovered sensors at top of scan list with notification

**Ticket**: [ATT-3099](https://atrainingtracker.atlassian.net/browse/ATT-3099)  
**Sub-task**: [ATT-3120](https://atrainingtracker.atlassian.net/browse/ATT-3120) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-UI-334`  
**Test Mapping**: `TST-UI-294`  
**Branch**: `feature/ATT-3099`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Description & Background

During wireless BLE and ANT+ sensor scanning (as observed by the athlete during the Sprint 2026-41.7 Joint Review of ATT-2969), newly discovered sensors silently append at the very bottom of the device catalog.
When an athlete has multiple paired, known, or historical sensors, newly discovered hardware appears off-screen "below the fold":
1. The athlete receives zero immediate tactile, audible, or visual confirmation that a sensor has been detected.
2. The athlete must manually scroll all the way to the bottom to verify whether discovery succeeded, creating unnecessary uncertainty.

To solve this, the application must:
1. Prioritize newly discovered sensors at the very top of the scan list (`isNewlyDiscovered == true` at index 0).
2. Highlight the card with an eye-catching "NEW" badge chip and primary highlight border.
3. Provide immediate sensory feedback: in-app Snackbar with the sensor name, haptic vibration, and auto-scroll to the top item.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-334` (*Newly Discovered Sensor Prioritization, Visual Highlight Badge & Discovery Feedback Notification*)
* **Test Mapping**: `TST-UI-294` (*Newly Discovered Sensor Prioritization, Visual Highlight Badge & Discovery Feedback Notification Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Pairing toggle switches, sensor configuration dialogs (`EditDeviceDialog`), and equipment matrix associations remain 100% operational.
2. **GATT & ANT+ Service Continuity**: Background BLE and ANT+ discovery in `BANALService` and `DeviceManager.java` remains untouched.
3. **Multi-Protocol & Sport Type Preservation**: Unrestrictive tab filter preservation (`REQ-UI-291`) and decoupled pairing scan dispatch remain preserved.
4. **9-Language Parity**: All newly introduced string tokens maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket ATT-3099 remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Domain Model (`DeviceData.kt` & `GetMergedDevicesUseCase.kt`)
* In `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceData.kt`:
  * Extend `DeviceUiData` with `val isNewlyDiscovered: Boolean = false`.
* In `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/GetMergedDevicesUseCase.kt`:
  * Evaluate `val isFound = foundIds.contains(knownDevice.id)`.
  * Pass `isNewlyDiscovered = isFound` when constructing `DeviceUiData`.

### Component 2: Sorting Logic (`DeviceListViewModel.kt`)
* In `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListViewModel.kt`:
  * In `getFilteredDevices(spec: DeviceFilterSpec)`, sort the filtered list using:
    ```kotlin
    val newlyFoundIds = banalServiceRepository.newlyFoundDevicesIds.value
    filtered.sortedWith(
        compareByDescending<DeviceUiData> { it.isNewlyDiscovered }
            .thenByDescending { if (it.isNewlyDiscovered) newlyFoundIds.indexOf(it.id) else -1 }
            .thenByDescending { it.isConnected }
            .thenBy { it.deviceName }
    )
    ```

### Component 3: Card Presentation & Visual Highlighting (`MappableListItem.kt` & `DeviceItem.kt`)
* In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/MappableListItem.kt`:
  * Accept optional `border: BorderStroke? = null`.
  * When `border != null`, apply it to `ElevatedCard`, otherwise default to `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`.
* In `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceItem.kt`:
  * When `device.isNewlyDiscovered`:
    * Pass `border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))` to `MappableListItem`.
    * Render a Material 3 "NEW" badge chip (`Surface` with `RoundedCornerShape(6.dp)`, `primaryContainer` background, `onPrimaryContainer` content color, labelSmall bold text `@string/devices_newly_discovered_badge`) next to `device.deviceName`.

### Component 4: Feedback Event, Snackbar, Haptics & Auto-Scroll (`DevicesTabbedViewModel.kt` & `DevicesTabbedScreen.kt`)
* In `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedViewModel.kt`:
  * Declare `private val _newDeviceDiscoveredEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)`.
  * Expose `val newDeviceDiscoveredEvent: SharedFlow<String> = _newDeviceDiscoveredEvent.asSharedFlow()`.
  * In `deviceDiscoveryReceiver.onReceive()`:
    * When `BANALService.NEW_DEVICE_FOUND_INTENT` arrives, extract `deviceId`.
    * Resolve the device name from `deviceDBRepository.getDeviceSnapshotById(deviceId)` or database query, and emit to `_newDeviceDiscoveredEvent`.
* In `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt`:
  * Remember `SnackbarHostState`.
  * Host `SnackbarHost(hostState = snackbarHostState)` anchored at bottom center above navigation bar.
  * In `LaunchedEffect(tabViewModel)`:
    * Collect `tabViewModel.newDeviceDiscoveredEvent`.
    * Perform haptic vibration: `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`.
    * Smoothly auto-scroll: `availableListState.animateScrollToItem(0)`.
    * Show snackbar: `snackbarHostState.showSnackbar(context.getString(R.string.devices_new_sensor_discovered, deviceName), duration = SnackbarDuration.Short)`.

### Component 5: 9-Language Localization
* Add `devices_newly_discovered_badge` and `devices_new_sensor_discovered` across:
  * `values/strings.xml`
  * `values-de/strings.xml`
  * `values-es/strings.xml`
  * `values-fr/strings.xml`
  * `values-it/strings.xml`
  * `values-ja/strings.xml`
  * `values-nl/strings.xml`
  * `values-pl/strings.xml`
  * `values-pt/strings.xml`

### UI Consistency (Rule 23 — Mandatory UI Audit)
* **Reference screen / component**: `DeviceItem.kt`, `DevicesTabbedScreen.kt`, `SensorBatteryNotificationManager.kt` (Snackbar pattern).
* **Reused components**: `MappableListItem`, `SnackbarHost`, `Surface`, `MaterialTheme.typography.labelSmall`.
* **Theme tokens**: `MaterialTheme.colorScheme.primary`, `primaryContainer`, `onPrimaryContainer`, `outlineVariant`.
* **Shapes**: `RoundedCornerShape(6.dp)` for badge chip, `RoundedCornerShape(16.dp)` for card container.
* **New one-off styles & justification**: None. Reuses existing design system tokens and spacing.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Strings Across All 9 Locales
* Files: `app/src/main/res/values*/strings.xml`
* Add `devices_newly_discovered_badge` and `devices_new_sensor_discovered`.

### Step 2: Domain Model & UseCase Pipeline Integration
* Files:
  * `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceData.kt`
  * `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/GetMergedDevicesUseCase.kt`
* Add `val isNewlyDiscovered: Boolean = false` to `DeviceUiData` and map in `GetMergedDevicesUseCase`.

### Step 3: Priority Sorting in `DeviceListViewModel.kt`
* Files:
  * `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListViewModel.kt`
* Sort newly discovered devices at index 0 in recency order ahead of connected and idle devices.

### Step 4: Card Border & "NEW" Badge in `MappableListItem.kt` & `DeviceItem.kt`
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/MappableListItem.kt`
  * `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceItem.kt`
* Apply highlight border and compose "NEW" badge chip when `device.isNewlyDiscovered == true`.

### Step 5: Discovery Event, Snackbar, Haptics & Auto-Scroll in ViewModels & Screen
* Files:
  * `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedViewModel.kt`
  * `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt`
* Emit `newDeviceDiscoveredEvent`, trigger haptic vibration, show snackbar, and auto-scroll `availableListState.animateScrollToItem(0)`.

### Step 6: Unit & Contract Tests
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/domain/usecase/GetMergedDevicesUseCaseTest.kt`
  * `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListViewModelPriorityTest.kt`
  * `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceItemVisualHighlightTest.kt`
  * `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedViewModelDiscoveryTest.kt`
  * `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/DeviceDiscoveryLocalizationTest.kt`
* Execute targeted unit tests via `./gradlew testDebugUnitTest --tests "*Device*"`.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests, localization parity assertions, followed by full clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-3099`) allows clean revert if needed without polluting sprint branch.
