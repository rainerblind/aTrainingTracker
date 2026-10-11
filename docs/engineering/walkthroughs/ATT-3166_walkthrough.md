# Walkthrough: ATT-3166

## Multi-Dimensional Sensor Filtering by Protocol, Category, and Battery Level with Deep-Link Navigation

### Executive Summary
Prior to this implementation, the Sensors & Devices screens (`DevicesTabbedScreen` / `DeviceListScreen`) presented discovered, paired, and known hardware sensors in an unfiltered list. Athletes managing multiple ANT+ and Bluetooth LE sensors across cycling, running, and environmental peripherals had no mechanism to filter by wireless protocol, device type, or battery health. Furthermore, when tapping the post-workout low sensor battery notification (ATT-2192), an asynchronous Compose navigation race condition in `MainActivityWithNavigation.onCreate()` prevented navigation on cold launch, and even when navigated, the athlete had to manually search through the unfiltered list to find low-battery sensors.

This ticket resolves these gaps with a standardized, Material 3 compliant filtering architecture:
- **Domain Criteria Model (`DeviceFilterCriteria`)**: Immutable criteria data model encapsulating `Protocol?`, `Set<DeviceType>`, and `BatteryFilter` (`ALL`, `CRITICAL_ONLY` [0..20%], `NORMAL` [>20%]), with JSON state persistence.
- **Material 3 Filter UI**:
  - `FilterActionButton`: Added to the `DevicesTabbedScreen` top app bar with active badge count indicator.
  - `DeviceFilterBottomSheet`: Built on the project's canonical `FilterBottomSheetScaffold` with Protocol, Category, and Battery filter chip groups.
  - `ActiveDeviceFilterChipsRow`: Horizontal row of `RemovableFilterChip` components below the tab row for one-tap dismissal of active filters.
- **Reactive ViewModels**:
  - `DevicesTabbedViewModel`: Holds active `filterCriteria` with `SavedStateHandle` serialization and `EXTRA_FILTER_CRITICAL_BATTERY` initialization.
  - `DeviceListViewModel`: Applies criteria predicate in `getFilteredDevices(spec, criteria)` across Available, Paired, and Known tabs.
- **Notification Deep-Link & Cold-Start Race Resolution**:
  - `SensorBatteryNotificationManager`: Passes `EXTRA_FILTER_CRITICAL_BATTERY = true` in the low battery notification `PendingIntent`.
  - `MainActivityWithNavigation`: Implements `mPendingDrawerItemId` queue and `onNavControllerReady()` callback, ensuring notification intents queue cleanly until `navController` is bound, and automatically pre-selects the Critical (<= 20%) battery filter.
  - `ATrainingTrackerApp`: Triggers `activity.onNavControllerReady(navController)` inside `DisposableEffect(navController)`.
- **Localization Parity**: 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## Changes Made

### 1. Domain & UI Layer
* [DeviceFilterCriteria.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceFilterCriteria.kt):
  Implemented `BatteryFilter` enum and `DeviceFilterCriteria` data class with multi-dimensional matching logic, active count calculation, and JSON serialization.
* [DeviceFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceFilterBottomSheet.kt):
  Created modal bottom sheet utilizing `FilterBottomSheetScaffold` with Protocol, Sensor Category, and Battery Level sections.
* [ActiveDeviceFilterChipsRow.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/ActiveDeviceFilterChipsRow.kt):
  Created active filter chip strip displaying removable chips for protocol, categories, and battery levels with 'Clear all' support.
* [DevicesTabbedScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt):
  Integrated `FilterActionButton` in the header action bar, adaptive `appBarMaxHeightPx` with chips row height, active filter chips below tabs, passed criteria to `DeviceListScreen`, and presented `DeviceFilterBottomSheet`.
* [DeviceListScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListScreen.kt):
  Accepted `filterCriteria` parameter and rendered specific empty state when filters yield no matches (`R.string.filter_no_matching_sensors`).

### 2. ViewModels & Navigation
* [DevicesTabbedViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedViewModel.kt):
  Added `filterCriteria` StateFlow, `updateFilterCriteria`, `clearFilterCriteria`, `setCriticalBatteryFilter`, and SavedStateHandle persistence.
* [DeviceListViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListViewModel.kt):
  Updated `getFilteredDevices` to apply `criteria.matches(device)` while preserving tab partitioning and discovery priority.
* [SensorBatteryNotificationManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/notifications/SensorBatteryNotificationManager.kt):
  Added `EXTRA_FILTER_CRITICAL_BATTERY` extra to the notification `PendingIntent`.
* [MainActivityWithNavigation.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt):
  Implemented `onNavControllerReady()` and `mPendingDrawerItemId` to eliminate cold-start race conditions; pre-applied critical battery filter on `DevicesTabbedViewModel` when launched from low battery notification.
* [ATrainingTrackerApp.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt):
  Connected `activity.onNavControllerReady(navController)` in `DisposableEffect(navController)`.

### 3. Localization
* Added 11 new sensor filter string resources across all 9 supported language directories:
  - `values/strings.xml`, `values-de/strings.xml`, `values-es/strings.xml`, `values-fr/strings.xml`, `values-it/strings.xml`, `values-ja/strings.xml`, `values-nl/strings.xml`, `values-pl/strings.xml`, `values-pt/strings.xml`.

### 4. Living Documentation
* [requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md):
  `REQ-UI-351` updated to `Verified`.
* [tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md):
  `TST-UI-311` updated to `Verified`.

---

## Verification Results

### Targeted Unit Tests
| Test Class | Scope / Target | Result |
| :--- | :--- | :--- |
| `DeviceFilterCriteriaTest` | Domain model matching, protocol/category/battery filtering, JSON round-trip | **PASSED** (100%) |
| `DeviceListViewModelFilterTest` | ViewModel reactive filtering across Connected, Paired, and Known tabs | **PASSED** (100%) |
| `NotificationDeepLinkTest` | Low battery notification extras, ViewModel critical battery pre-selection | **PASSED** (100%) |
| `TranslationParityTest` | 100% localization parity across all 9 supported application locales | **PASSED** (100%) |
| `com.atrainingtracker.banalservice.ui.devices.*` | Full peripheral and devices UI suite regression | **PASSED** (100%) |

### Clean-Room Full Suite Regression
- Executed `./gradlew testDebugUnitTest` across the complete codebase:
  - **Result**: BUILD SUCCESSFUL in 2m 53s
  - **Failures**: 0
  - **Regressions**: 0
