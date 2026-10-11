# Stage 3: Implementation Plan - ATT-3166: Filter sensors and devices by protocol, category, and battery level with notification deep-link

**Ticket**: [ATT-3166](https://atrainingtracker.atlassian.net/browse/ATT-3166)  
**Sub-task**: [ATT-3262](https://atrainingtracker.atlassian.net/browse/ATT-3262) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-754](https://atrainingtracker.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.9`  
**Requirement Mapping**: `REQ-UI-351`  
**Test Mapping**: `TST-UI-311`  
**Branch**: `feature/ATT-3166`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Description & Background

Athletes managing multiple wireless sensors have no filtering mechanism on `DevicesTabbedScreen` / `DeviceListScreen`. When a low sensor battery notification is tapped, a navigation race condition in `MainActivityWithNavigation.onCreate()` prevents the app from navigating to the sensors view on cold start, and even on warm start, the athlete must manually search through an unfiltered list to find the affected sensor.

This implementation introduces:
1. An immutable domain criteria model `DeviceFilterCriteria` with `Protocol?`, `Set<DeviceType>`, and `BatteryFilter`.
2. A Material 3 filter bottom sheet `DeviceFilterBottomSheet` using `FilterBottomSheetScaffold`.
3. A `FilterActionButton` in the header row of `DevicesTabbedScreen` and an `ActiveDeviceFilterChipsRow` below the tabs.
4. Reactive filtering in `DeviceListViewModel` and filter state management in `DevicesTabbedViewModel`.
5. Deep-link routing via `EXTRA_FILTER_CRITICAL_BATTERY` from `SensorBatteryNotificationManager` with cold-start navigation queuing in `MainActivityWithNavigation`.
6. Full 9-language localization parity across all supported locales.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-351` (*Multi-Dimensional Sensor Filtering by Protocol, Category, and Battery Level with Deep-Link Navigation*)
* **Test Mapping**: `TST-UI-311` (*Multi-Dimensional Sensor Filtering by Protocol, Category, and Battery Level with Deep-Link Navigation Verification*)
  * `TST-UI-311.1`: Domain model unit tests (`DeviceFilterCriteriaTest.kt`)
  * `TST-UI-311.2`: ViewModel reactive filtering unit tests (`DeviceListViewModelFilterTest.kt`)
  * `TST-UI-311.3`: Compose UI contract tests (`DevicesTabbedScreenFilterTest.kt`)
  * `TST-UI-311.4`: Notification deep-link unit tests (`NotificationDeepLinkTest.kt`)
  * `TST-UI-311.5`: 9-language localization parity audit (`TranslationParityTest.kt`)
  * `TST-UI-311.6`: Full clean-room test suite regression (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Scanner & Pairing Integrity**: Targeted scanning (`startPairingScan`) and pairing dialogs remain completely intact.
3. **Dispatcher & Thread Affinity**: Database operations and Flow transformations remain safe across background dispatchers and the Main Looper.
4. **Single Source of Truth**: `DeviceDataRepository` and `BANALServiceRepository` remain the underlying sources of truth via `GetMergedDevicesUseCase`.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `DeviceFilterCriteria` Domain Model
* Path: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceFilterCriteria.kt`
* Encapsulates:
  - `enum class BatteryFilter { ALL, CRITICAL_ONLY, NORMAL }`
  - `data class DeviceFilterCriteria(val protocol: Protocol? = null, val deviceTypes: Set<DeviceType> = emptySet(), val batteryFilter: BatteryFilter = BatteryFilter.ALL)`
  - Methods: `matches(device: DeviceUiData): Boolean`, `activeFilterCount: Int`, `isEmpty: Boolean`, `isNotEmpty: Boolean`, `toJson(): String`, `fromJson(jsonStr: String?): DeviceFilterCriteria`.

### Component 2: `DeviceFilterBottomSheet` & `ActiveDeviceFilterChipsRow`
* Paths:
  - `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceFilterBottomSheet.kt`
  - `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/ActiveDeviceFilterChipsRow.kt`
* `DeviceFilterBottomSheet`:
  - Implements `FilterBottomSheetScaffold` with 3 sections: Protocol (All, ANT+, Bluetooth LE), Sensor Category (HRM, Bike Power, Bike Speed, Bike Cadence, Speed & Cadence, Run Speed, Environment), Battery Level (All, Kritisch $\le 20\%$, Normal $> 20\%$).
* `ActiveDeviceFilterChipsRow`:
  - Renders horizontal row of `RemovableFilterChip` instances for active protocol, categories, and battery filter, plus "Clear all" button when count $> 1$.

### Component 3: `DevicesTabbedViewModel` & `DeviceListViewModel`
* In `DevicesTabbedViewModel`:
  - Add `_filterCriteria = MutableStateFlow(DeviceFilterCriteria())`, expose `filterCriteria: StateFlow<DeviceFilterCriteria>`.
  - Add `updateFilterCriteria(criteria: DeviceFilterCriteria)` and `clearFilterCriteria()`.
* In `DeviceListViewModel`:
  - Update `getFilteredDevices(spec: DeviceFilterSpec, criteria: DeviceFilterCriteria)`:
    - Applies primary tab filter (`CONNECTED`, `PAIRED`, `ALL_KNOWN`).
    - Applies secondary criteria predicate: `devices.filter { criteria.matches(it) }`.
    - Preserves discovery and connection sorting order.

### Component 4: `DevicesTabbedScreen` Integration
* In `DevicesTabbedScreen.kt`:
  - Collect `tabViewModel.filterCriteria`.
  - In header row, place `FilterActionButton(onClick = { showFilterSheet = true }, isFilterActive = filterCriteria.isNotEmpty, activeFilterCount = filterCriteria.activeFilterCount)`.
  - Below `PrimaryScrollableTabRow`, render `ActiveDeviceFilterChipsRow` when `filterCriteria.isNotEmpty`.
  - Pass `filterCriteria` into `DeviceListScreen` across the three device tabs.
  - Present `DeviceFilterBottomSheet` when `showFilterSheet == true`.

### Component 5: Deep-Link & Navigation Race Resolution
* In `MainActivityWithNavigation.kt`:
  - Define `const val EXTRA_FILTER_CRITICAL_BATTERY = "EXTRA_FILTER_CRITICAL_BATTERY"`.
  - Add `private var mPendingDrawerItemId: Int? = null`.
  - In `navigateToDrawerItem`: if `navController == null`, save `mPendingDrawerItemId = itemId`.
  - Add `fun onNavControllerReady(controller: NavHostController)`: if `mPendingDrawerItemId != null`, invoke `navigateToDrawerItem(mPendingDrawerItemId!!)` and reset `mPendingDrawerItemId = null`.
  - In `handleIntent(intent)`: if `intent.getBooleanExtra(EXTRA_FILTER_CRITICAL_BATTERY, false)`, set `tabViewModel.updateFilterCriteria(DeviceFilterCriteria(batteryFilter = BatteryFilter.CRITICAL_ONLY))`.
* In `ATrainingTrackerApp.kt`:
  - Inside `DisposableEffect(navController)`: invoke `activity.onNavControllerReady(navController)`.
* In `SensorBatteryNotificationManager.kt`:
  - Add `putExtra(MainActivityWithNavigation.EXTRA_FILTER_CRITICAL_BATTERY, true)` in the notification `PendingIntent`.

### UI Consistency (Rule 23)
* **Reference screen / component**: `RouteTabbedScreen.kt` & `RouteFilterBottomSheet.kt` / `WorkoutFilterBottomSheet.kt`.
* **Reused components**: `FilterActionButton` (`ui/common/filters/FilterActionButton.kt`), `FilterBottomSheetScaffold` (`ui/common/filters/FilterBottomSheetScaffold.kt`), `RemovableFilterChip` (`ui/common/filters/RemovableFilterChip.kt`), `FilterChip` (Material 3).
* **Theme tokens**: shapes `RoundedCornerShape(8.dp)` for filter chips, `RoundedCornerShape(12.dp)` for bottom sheet container; spacing `8.dp` and `16.dp`; colors `MaterialTheme.colorScheme.onPrimaryContainer`, `MaterialTheme.colorScheme.surface`, `MaterialTheme.colorScheme.primary`.
* **New one-off styles & justification**: None. Completely adheres to existing design guidelines.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localization Strings across all 9 Locales
* Update `values/strings.xml`, `values-de/strings.xml`, `values-es/strings.xml`, `values-fr/strings.xml`, `values-it/strings.xml`, `values-ja/strings.xml`, `values-nl/strings.xml`, `values-pl/strings.xml`, `values-pt/strings.xml`.
* Add:
  - `filter_sensors_title`
  - `filter_section_protocol`
  - `filter_section_category`
  - `filter_section_battery`
  - `filter_protocol_all`
  - `filter_battery_all`
  - `filter_battery_critical`
  - `filter_battery_normal`
  - `filter_battery_critical_chip`
  - `filter_battery_normal_chip`
  - `filter_no_matching_sensors`

### Step 2: Domain Model `DeviceFilterCriteria.kt`
* Create `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceFilterCriteria.kt`.
* Implement `BatteryFilter` enum and `DeviceFilterCriteria` data class with `matches`, `activeFilterCount`, `toJson`, `fromJson`.
* Add targeted test: `DeviceFilterCriteriaTest.kt`.
* Run test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceFilterCriteriaTest"`.

### Step 3: ViewModel Reactive Filtering
* In `DevicesTabbedViewModel.kt`: expose `filterCriteria`, `updateFilterCriteria`, `clearFilterCriteria`.
* In `DeviceListViewModel.kt`: update `getFilteredDevices(spec: DeviceFilterSpec, criteria: DeviceFilterCriteria = DeviceFilterCriteria())`.
* Add targeted test: `DeviceListViewModelFilterTest.kt`.
* Run test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceListViewModelFilterTest"`.

### Step 4: Filter Bottom Sheet & Active Chips Row
* Create `DeviceFilterBottomSheet.kt` with `FilterBottomSheetScaffold`, protocol chips, category chips, battery chips.
* Create `ActiveDeviceFilterChipsRow.kt` with `RemovableFilterChip`.
* Integrate into `DevicesTabbedScreen.kt` and `DeviceListScreen.kt`.
* Add targeted test: `DevicesTabbedScreenFilterTest.kt`.
* Run test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreenFilterTest"`.

### Step 5: Notification Deep-Link & Cold-Start Race Fix
* In `SensorBatteryNotificationManager.kt`: pass `EXTRA_FILTER_CRITICAL_BATTERY`.
* In `MainActivityWithNavigation.kt`: implement `mPendingDrawerItemId`, `onNavControllerReady`, and process `EXTRA_FILTER_CRITICAL_BATTERY`.
* In `ATrainingTrackerApp.kt`: trigger `activity.onNavControllerReady(navController)`.
* Add targeted test: `NotificationDeepLinkTest.kt`.
* Run test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.notifications.NotificationDeepLinkTest"`.

### Step 6: Full Clean-Room Suite & Verification
* Run `./gradlew testDebugUnitTest`.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests executed at every step; full clean-room suite in Stage 5.
* **Rollback**: Branch `feature/ATT-3166` is strictly isolated from `sprint/2026-41.9`.
