# Stage 2: Requirement & Test Specification - ATT-3166: Filter sensors and devices by protocol, category, and battery level with notification deep-link

**Ticket**: [ATT-3166](https://atrainingtracker.atlassian.net/browse/ATT-3166)  
**Sub-task**: [ATT-3261](https://atrainingtracker.atlassian.net/browse/ATT-3261) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-754](https://atrainingtracker.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.9`  
**Requirement Mapping**: `REQ-UI-351` (*Multi-Dimensional Sensor Filtering by Protocol, Category, and Battery Level with Deep-Link Navigation*)  
**Test Spec ID**: `TST-UI-311`  
**Branch**: `feature/ATT-3166`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (REQ-UI-351)

### 1.1 Problem Statement & Rationale
Athletes managing extensive inventories of wireless sensors (ANT+ and Bluetooth LE across bikes, shoes, and heart rate monitors) currently face an unfiltered, monolithic list in `DevicesTabbedScreen` (`Verfügbar`, `Gekoppelt`, `Bekannt`). There is no mechanism to quickly filter sensors by communication protocol, device category, or battery health.
Additionally, when an athlete taps the low battery notification (`NOTIFICATION_ID_SENSOR_BATTERY = 4300`), a navigation race condition in `MainActivityWithNavigation.onCreate()` drops the intent navigation because `navController` is null at launch time, landing the user on the default Start Tracking screen instead of Sensors. Even when hot-started, the notification lands the user on an unfiltered sensor list, forcing them to manually search for which sensor is experiencing critical battery depletion ($\le 20\%$).

### 1.2 Functional & Architectural Requirements
The system SHALL provide multi-dimensional filtering across Sensors & Devices views (`DevicesTabbedScreen` / `DeviceListScreen`) by communication protocol, device category, and battery health, and SHALL guarantee reliable deep-link routing from low battery notifications directly into a pre-filtered critical battery list (refining `REQ-CON-007` and ASPICE Governance Rule 35):
1. **Filter Criteria Domain Model (`DeviceFilterCriteria`)**:
   - The system SHALL maintain an immutable domain model `DeviceFilterCriteria` supporting:
     - `protocol: Protocol?`: Filters by wireless protocol (`ANT_PLUS`, `BLUETOOTH_LE`), or `null`/`ALL` for all protocols.
     - `deviceTypes: Set<DeviceType>`: Filters by sensor categories (empty set = all categories; non-empty = must match any selected category).
     - `batteryFilter: BatteryFilter`: Enum with `ALL`, `CRITICAL_ONLY` ($\le 20\%$), `NORMAL` ($> 20\%$).
   - The model SHALL expose `activeFilterCount: Int`, `isEmpty: Boolean`, `isNotEmpty: Boolean`, and `matches(device: DeviceUiData): Boolean`.
   - For `CRITICAL_ONLY`, `matches` SHALL return true if and only if `device.batteryPercentage in 0..20`. Devices with unknown battery ($-1$) SHALL NOT match.
   - For `NORMAL`, `matches` SHALL return true if and only if `device.batteryPercentage > 20`.
   - The model SHALL provide JSON serialization (`toJson()`) and deserialization (`fromJson(String?)`) for preference persistence.
2. **Material 3 Filter Bottom Sheet (`DeviceFilterBottomSheet`)**:
   - Built on `FilterBottomSheetScaffold` (`com.atrainingtracker.trainingtracker.ui.common.filters`).
   - Section 1 (Protocol): FilterChips for Alle, ANT+, Bluetooth LE.
   - Section 2 (Sensor Category): FilterChips for remote sensor device types (HRM, Bike Power, Bike Speed, Bike Cadence, Speed & Cadence, Run Speed, Environment).
   - Section 3 (Battery Level): FilterChips for Alle, Kritisch ($\le 20\%$), Normal ($> 20\%$).
   - Standard action bar with "Filter zurücksetzen" and "Anwenden".
3. **Top Bar Filter Action & Active Filter Chips Strip**:
   - In `DevicesTabbedScreen`, the top header row SHALL render `FilterActionButton` with active filter count badge.
   - Below the tab row, `ActiveDeviceFilterChipsRow` SHALL render `RemovableFilterChip` for each active filter criterion (protocol, each category, battery level) and a "Clear all" button when $> 1$ criteria are active.
4. **Reactive ViewModel Integration**:
   - `DevicesTabbedViewModel` SHALL expose `filterCriteria: StateFlow<DeviceFilterCriteria>`, `updateFilterCriteria(criteria)`, and `clearFilterCriteria()`.
   - `DeviceListViewModel.getFilteredDevices(spec, criteria)` SHALL filter `mergedDevices` reactively across all tabs.
5. **Notification Deep-Link & Cold-Start Race Resolution**:
   - `SensorBatteryNotificationManager` SHALL pass `EXTRA_FILTER_CRITICAL_BATTERY = true` in the notification `PendingIntent`.
   - `MainActivityWithNavigation` SHALL queue pending navigation if `navController == null`, and dispatch navigation to `R.id.drawer_my_sensors` upon `onNavControllerReady`.
   - When launched with `EXTRA_FILTER_CRITICAL_BATTERY`, `MainActivityWithNavigation` SHALL pre-apply `BatteryFilter.CRITICAL_ONLY` on `DevicesTabbedViewModel`.
6. **9-Language Localization Parity**:
   - All new filter strings SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Filter Button & Sheet Appearance)**:
  * *Given* an athlete on `DevicesTabbedScreen`,
  * *When* tapping the filter action icon in the top app bar,
  * *Then* `DeviceFilterBottomSheet` opens using `FilterBottomSheetScaffold` styled consistently with workouts, routes, and segments.
* **Criterion 2 (Filtering by Protocol)**:
  * *Given* a mixed list of ANT+ and Bluetooth LE sensors,
  * *When* selecting the 'ANT+' or 'Bluetooth LE' filter chip and applying,
  * *Then* only sensors communicating via the selected protocol are displayed, and an active filter chip appears below the tab bar.
* **Criterion 3 (Filtering by Device Category)**:
  * *Given* multiple sensor types in the database,
  * *When* selecting specific categories (e.g. 'Herzfrequenz' or 'Leistungsmesser'),
  * *Then* the list is immediately filtered to matching devices across Available, Paired, and Known tabs.
* **Criterion 4 (Filtering by Battery Level)**:
  * *Given* sensors with various battery levels in the database,
  * *When* selecting 'Kritisch (<= 20%)',
  * *Then* only sensors with battery percentage in $0..20\%$ are displayed, and devices with unknown battery (-1) are excluded.
* **Criterion 5 (Notification Deep-Link to Pre-Filtered Sensor List)**:
  * *Given* a low battery notification posted in the Android notification shade,
  * *When* the athlete taps the notification (whether app is cold-started or backgrounded),
  * *Then* `MainActivityWithNavigation` navigates to the Sensors screen, and the sensor list is pre-filtered to critical low-battery devices with an active 'Batterie: <= 20%' filter chip visible.
* **Criterion 6 (Active Filter Removal & Reset)**:
  * *Given* one or more active filters applied,
  * *When* tapping the delete icon on an active filter chip or selecting 'Filter zurücksetzen' in the bottom sheet,
  * *Then* the filter criteria are cleared and the full list is restored.

### 1.4 System Invariants
1. Normal sensor pairing discovery via targeted scanning (`startPairingScan`) remains unaffected.
2. Direct hardware communication, GATT services, and SQLite database schemas remain unchanged.
3. 100% clean-room unit test pass rate with zero regressions across the codebase.

---

## 2. Test Specification (TST-UI-311)

### Test Case 1: `DeviceFilterCriteriaTest` (`TST-UI-311.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceFilterCriteriaTest.kt`
* **Preconditions**: Sample `DeviceUiData` instances with varied protocols, device types, and battery percentages (-1, 0, 15, 20, 21, 80, 100).
* **Actions & Expected Results**:
  1. `testMatches_protocolFilter`: Protocol match accepts matching protocol, rejects non-matching.
  2. `testMatches_deviceTypeFilter`: Category set match accepts device types contained in set, rejects others.
  3. `testMatches_batteryFilter_critical`: Matches $0 \dots 20\%$, rejects $> 20\%$, and rejects unknown ($-1$).
  4. `testMatches_batteryFilter_normal`: Matches $> 20\%$, rejects $\le 20\%$, and rejects unknown ($-1$).
  5. `testActiveFilterCount_and_isEmpty`: Counts dimensions independently (0 to 3).
  6. `testSerialization_toJson_fromJson`: Full round-trip parity including empty criteria.

### Test Case 2: `DeviceListViewModelFilterTest` (`TST-UI-311.2`)
* **Scope**: ViewModel Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListViewModelFilterTest.kt`
* **Preconditions**: Mocked repository returning mixed sensor list.
* **Actions & Expected Results**:
  1. Verify `getFilteredDevices(spec, criteria)` filters correctly across `CONNECTED`, `PAIRED`, and `ALL_KNOWN` tabs.
  2. Verify reactive emission when `filterCriteria` emits updated criteria.

### Test Case 3: `DevicesTabbedScreenFilterTest` (`TST-UI-311.3`)
* **Scope**: Architectural & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreenFilterTest.kt`
* **Preconditions**: Source file inspection.
* **Actions & Expected Results**:
  1. Verify `DevicesTabbedScreen.kt` integrates `FilterActionButton` in header row.
  2. Verify `DevicesTabbedScreen.kt` integrates `ActiveDeviceFilterChipsRow`.
  3. Verify `DeviceFilterBottomSheet.kt` exists and utilizes `FilterBottomSheetScaffold`.

### Test Case 4: `NotificationDeepLinkTest` (`TST-UI-311.4`)
* **Scope**: Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/notifications/NotificationDeepLinkTest.kt`
* **Preconditions**: Mocked intent and PendingIntent factory.
* **Actions & Expected Results**:
  1. Verify `SensorBatteryNotificationManager` sets `EXTRA_FILTER_CRITICAL_BATTERY = true` in PendingIntent.
  2. Verify `MainActivityWithNavigation` queues `mPendingDrawerItemId` when `navController == null` and executes pending navigation upon `onNavControllerReady`.
  3. Verify `BatteryFilter.CRITICAL_ONLY` is pre-applied when `EXTRA_FILTER_CRITICAL_BATTERY` is present.

### Test Case 5: 9-Language Localization & Specifier Audit (`TST-UI-311.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Goal**: Verify presence of new filter strings across all 9 locales:
  * `filter_sensors_title`, `filter_section_protocol`, `filter_section_category`, `filter_section_battery`
  * `filter_protocol_all`, `filter_battery_all`, `filter_battery_critical`, `filter_battery_normal`
  * `filter_battery_critical_chip`, `filter_battery_normal_chip`, `filter_no_matching_sensors`
* **Locales**: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 6: Clean-Room Regression Suite (`TST-UI-311.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-311.1` | Unit | `DeviceFilterCriteriaTest.kt` | `REQ-UI-351` (Clause 1) | Specified |
| `TST-UI-311.2` | Unit | `DeviceListViewModelFilterTest.kt` | `REQ-UI-351` (Clause 4) | Specified |
| `TST-UI-311.3` | Contract | `DevicesTabbedScreenFilterTest.kt` | `REQ-UI-351` (Clauses 2, 3) | Specified |
| `TST-UI-311.4` | Unit | `NotificationDeepLinkTest.kt` | `REQ-UI-351` (Clause 5) | Specified |
| `TST-UI-311.5` | Localization | `TranslationParityTest.kt` | `REQ-UI-351` (Clause 6) | Specified |
| `TST-UI-311.6` | Full Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
