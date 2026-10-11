# Stage 1 Analysis: ATT-3166 - Filter sensors and devices by protocol, category, and battery level with notification deep-link

**Ticket**: [ATT-3166](https://atrainingtracker.atlassian.net/browse/ATT-3166)  
**Sub-task**: [ATT-3260](https://atrainingtracker.atlassian.net/browse/ATT-3260) (`[Analysis]`)  
**Parent Epic**: [ATT-754](https://atrainingtracker.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.9`  
**Branch**: `feature/ATT-3166`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation

Currently, the Sensors & Devices view (`DevicesTabbedScreen` / `DeviceListScreen`) presents all discovered, paired, and known hardware sensors in an unfiltered, monolithic list across its respective tabs (Verfügbar, Gekoppelt, Bekannt). Athletes managing multiple sensors (e.g., dual bike power meters, multiple heart rate straps, speed/cadence pods, smart trainer, running footpods, and environmental sensors) have no mechanism to narrow down the list by protocol, device category, or battery health.

Furthermore, when an athlete taps the post-workout low sensor battery notification (`NOTIFICATION_ID_SENSOR_BATTERY = 4300`, introduced in ATT-2192 / `REQ-CON-007`), two critical UX gaps emerge:
1. **Navigation Race Condition on Cold-Start**: In `MainActivityWithNavigation.kt`, `setContent` initializes the Compose UI asynchronously. When `handleIntent(intent)` executes immediately in `onCreate()`, `activity.navController` is still `null`. As a consequence, `navigateToDrawerItem(R.id.drawer_my_sensors)` silently fails to dispatch navigation on cold app launches, leaving the athlete stranded on the default Start Tracking screen (`NavRoutes.START_TRACKING`).
2. **Missing Deep-Link Pre-Filtering**: Even when hot-started, the notification lands the user on the unfiltered sensors list. The athlete must manually scan through their entire sensor inventory to find which sensor is suffering from critical battery depletion ($\le 20\%$).

Introducing a standardized filtering experience matching workouts, routes, segments, and clusters—with protocol, category, and battery level filters, plus deep-link routing from battery alerts—improves visual clarity, reduces cognitive load, and enables instant action on low-battery sensors.

---

## 2. Root Cause Analysis (Forensic Investigation & Architectural Gap Analysis)

### 2.1 Lack of Multi-Dimensional Filter Architecture in Device Tabs
In `DevicesTabbedScreen.kt` and `DevicesTabbedViewModel.kt`, device filtering is hardcoded to fixed `DeviceFilterSpec` tabs:
* `tabs = listOf(DeviceFilterSpec(CONNECTED), DeviceFilterSpec(PAIRED), DeviceFilterSpec(ALL_KNOWN))`
* The `protocol` and `deviceType` fields inside `DeviceFilterSpec` are immutable session parameters loaded once from `SavedStateHandle` (defaulting to `Protocol.ALL` and `DeviceType.ALL`).
* `DeviceListViewModel.getFilteredDevices(spec)` filters only by tab type (`isPaired`, `isConnected`), protocol equality (`spec.protocol`), and single device type equality (`spec.deviceType`). There is zero support for multi-category sets (`Set<DeviceType>`) or battery percentage thresholds (`batteryPercentage in 0..20`).
* No filter bottom sheet (`DeviceFilterBottomSheet`), filter action button (`FilterActionButton`), or active chip strip (`ActiveDeviceFilterChipsRow`) exists on `DevicesTabbedScreen`.

### 2.2 Cold-Start Navigation Race Condition in `MainActivityWithNavigation`
In `MainActivityWithNavigation.onCreate`:
```kotlin
setContent {
    ATrainingTrackerTheme {
        ATrainingTrackerApp(
            activity = this,
            drawerController = mDrawerController
        )
    }
}
handleIntent(intent)
```
Inside `ATrainingTrackerApp.kt`:
```kotlin
val navController: NavHostController = rememberNavController()
DisposableEffect(navController) {
    activity.navController = navController
    onDispose { activity.navController = null }
}
```
Because Compose composition executes asynchronously after `setContent`, `activity.navController` is guaranteed to be `null` when `handleIntent(intent)` invokes `navigateToDrawerItem(mSelectedFragmentId)`.
Inside `navigateToDrawerItem`:
```kotlin
navController?.let { controller ->
    controller.navigate(route) { ... }
}
```
The navigation block is silently dropped. `mSelectedFragmentId` and `mDrawerController.selectedItemId` are set, but `NavHost` remains initialized to its `startDestination` (`NavRoutes.START_TRACKING`).

### 2.3 Absence of Critical Battery Intent Extra & Pre-Filter Propagation
`SensorBatteryNotificationManager.kt` builds the notification intent with:
```kotlin
putExtra(MainActivityWithNavigation.SELECTED_FRAGMENT, SelectedFragment.SENSORS.name)
putExtra(MainActivityWithNavigation.EXTRA_DRAWER_ITEM_ID, R.id.drawer_my_sensors)
```
It does not pass any flag indicating that the destination should pre-filter to critical battery sensors (`EXTRA_FILTER_CRITICAL_BATTERY`). Moreover, neither `MainActivityWithNavigation` nor `DevicesTabbedViewModel` have a contract to ingest such an extra and pre-populate the filter criteria.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **DeviceFilterCriteria Domain Model**:
     - Create `DeviceFilterCriteria` (immutable data class) supporting `protocol: Protocol?`, `deviceTypes: Set<DeviceType>`, and `batteryFilter: BatteryFilter` (`ALL`, `CRITICAL_ONLY` [$\le 20\%$], `NORMAL` [$> 20\%$]).
     - Provide `matches(device: DeviceUiData): Boolean`, `activeFilterCount: Int`, `isEmpty`, `isNotEmpty`, and JSON serialization/deserialization for state persistence.
  2. **DeviceFilterBottomSheet Composable**:
     - Built using `FilterBottomSheetScaffold` (`com.atrainingtracker.trainingtracker.ui.common.filters`).
     - Protocol section (FilterChips for Alle, ANT+, Bluetooth LE).
     - Category section (FilterChips for HRM, Bike Power, Bike Speed, Bike Cadence, Speed & Cadence, Run Speed, Environment).
     - Battery Level section (FilterChips for Alle, Kritisch [$\le 20\%$], Normal [$> 20\%$]).
     - Standard action bar with "Filter zurücksetzen" and "Anwenden".
  3. **Header Filter Action & Active Chips Row**:
     - Integrate `FilterActionButton` in `DevicesTabbedScreen` header row next to the ANT+ options menu, displaying badge count when active.
     - Implement `ActiveDeviceFilterChipsRow` below the tabs with `RemovableFilterChip` for one-tap removal of individual criteria, plus "Alle zurücksetzen" button.
  4. **ViewModel Reactive Filtering Integration**:
     - In `DevicesTabbedViewModel`, expose `filterCriteria: StateFlow<DeviceFilterCriteria>` and methods `updateFilterCriteria()` and `clearFilterCriteria()`.
     - In `DeviceListViewModel`, update `getFilteredDevices(spec, criteria)` to reactively combine and filter `mergedDevices` against `DeviceFilterCriteria`.
  5. **Notification Deep-Link & Cold-Start Race Resolution**:
     - Add `EXTRA_FILTER_CRITICAL_BATTERY = "EXTRA_FILTER_CRITICAL_BATTERY"` to `MainActivityWithNavigation`.
     - In `SensorBatteryNotificationManager.kt`, pass `putExtra(EXTRA_FILTER_CRITICAL_BATTERY, true)` in the notification `PendingIntent`.
     - In `MainActivityWithNavigation.kt`, queue `mPendingDrawerItemId` if `navController == null`, and dispatch it when `onNavControllerReady()` is called from `ATrainingTrackerApp.kt`.
     - Pre-apply `BatteryFilter.CRITICAL_ONLY` in `DevicesTabbedViewModel` when `EXTRA_FILTER_CRITICAL_BATTERY` is present.
  6. **100% 9-Language Localization Parity**:
     - Add all new filter string keys across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Do NOT modify BLE/ANT+ communication stacks, GATT characteristics, or driver scanning internals.
  - Do NOT alter SQLite database schemas or `DeviceDataRepository` storage formats.
  - Do NOT alter `EquipmentSportSensorMatrix` tab implementations (Bikes / Shoes tabs).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-351` (*Multi-Dimensional Sensor Filtering by Protocol, Category, and Battery Level with Deep-Link Navigation*), while refining and integrating with `REQ-CON-007` (*Modernized Post-Workout Low Sensor Battery Alerting*).
* **Historical Origin & Commit Trace**:
  - `REQ-CON-007` was introduced in Sprint 2026-40.16 (ATT-2192) to replace blocking modal dialogs with dual-channel alerts.
  - Filter bottom sheet architecture was established in Sprint 2026-40.15 for Workouts (ATT-736), Routes (ATT-737), Segments (ATT-738), and Clusters (ATT-739).
* **Root Reason for Existing Formulation**:
  - Device tabs originally relied on a single `Protocol` and single `DeviceType` parameter passed during pairing setup from `ControlTrackingScreen`.
  - Notification intent was created to target `MainActivityWithNavigation` with `EXTRA_DRAWER_ITEM_ID = R.id.drawer_my_sensors`, but without pre-filtering or race-condition guarding.
* **Preservation of Core Invariants**:
  - Targeted pairing scan (`startPairingScan`, `UiState.DisplayingTabs`) remains strictly intact.
  - 100% clean-room test pass rate, background thread safety, and zero regression on existing tab and sensor flows.

---

## 5. Architectural Strategy & High-Level Solution

```
┌─────────────────────────────────────────────────────────────┐
│             SensorBatteryNotificationManager                │
│  (PendingIntent with EXTRA_DRAWER_ITEM_ID & CRITICAL_BAT)   │
└──────────────────────────────┬──────────────────────────────┘
                               │ Tap Notification
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 MainActivityWithNavigation                  │
│  - handleIntent(): queues pending nav if navController null │
│  - onNavControllerReady(): executes pending nav to SENSORS  │
│  - Applies BatteryFilter.CRITICAL_ONLY to tabViewModel      │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   DevicesTabbedScreen                       │
│  - Header: Title | ANT Menu | FilterActionButton (Badge)    │
│  - Tabs: Verfügbar | Gekoppelt | Bekannt | Bikes | Schuhe   │
│  - ActiveDeviceFilterChipsRow: [ANT+] [HRM] [≤20%] [X]     │
│  - DeviceFilterBottomSheet: Protocol / Category / Battery   │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│              DeviceListViewModel.getFilteredDevices         │
│  - Evaluates DeviceFilterCriteria.matches(deviceUiData)     │
│  - Dynamically updates LazyColumn in real-time             │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `DeviceUiData` fields (`batteryPercentage`, `protocol`, `deviceType`) are evaluated without modifying raw database schemas.
  2. Targeted pairing scan from the FAB (`+`) continues to function seamlessly without interference from active filters.
  3. `MainActivityWithNavigation` backstack and drawer controller states remain stable.
  4. 100% unit test pass rate across all suites with zero regressions.
* **Risk Rating**: **LOW**. Compose UI additions and ViewModel Flow transformations are non-invasive, purely additive, and adhere to proven architectural patterns established in Workouts and Routes filtering.
