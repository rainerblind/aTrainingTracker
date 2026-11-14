# Stage 3: Implementation Plan - ATT-7663: Add Sensor List Sorting

**Ticket**: [ATT-7663](https://rainerblind.atlassian.net/browse/ATT-7663)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feature/ATT-7663`  
**Requirement Mapping**: `REQ-UI-528` (`REQ-UI-528.1`..`REQ-UI-528.5`) | **Test Mapping**: `TST-UI-528` (`TST-UI-528.1`..`TST-UI-528.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-14  

---

## 1. Problem Description & Background
"My Sensors" (`DevicesTabbedScreen` / `DeviceListViewModel`) provides multi-dimensional filtering, but list ordering is statically hardcoded to Alphabetical (`A–Z`). Athletes need flexible sorting to find newly paired sensors (`FIRST_SEEN`), check recently active devices (`LAST_USAGE`), or inspect low battery sensors (`BATTERY_LEVEL`) prior to workouts, accessible via a top app bar Sort dropdown matching `RouteTabbedScreen` and `KnownLocationsScreen`.

---

## 2. Traceability & Requirements Mapping
* **REQ-UI-528.1**: `DeviceSortOrder` enum in `devicelist` $\to$ `TST-UI-528.1` (`DeviceSortOrderTest.kt`)
* **REQ-UI-528.2**: Multi-tier sort pipeline & scan recency pinning in `DeviceListViewModel` $\to$ `TST-UI-528.1`, `TST-UI-528.2`
* **REQ-UI-528.3**: Top bar Sort `IconButton` & `DropdownMenu` on tabs 0..2 in `DevicesTabbedScreen` $\to$ `TST-UI-528.4`
* **REQ-UI-528.4**: `sortOrder` StateFlow & `SavedStateHandle` in `DevicesTabbedViewModel` $\to$ `TST-UI-528.3`
* **REQ-UI-528.5**: 9-language localization parity in `strings_devices.xml` $\to$ `TST-UI-528.5`
* **REQ-PRO-001**: Tier 1 test regression suite $\to$ `TST-UI-528.6`

---

## 3. System Invariants & Preserved Behavior
1. **Scan Recency Pinning (`REQ-UI-334`)**: All `isNewlyDiscovered == true` sensors stay pinned at index 0..N in reverse discovery order across all 4 sort modes.
2. **Stable Non-Jumping Default**: In `ALPHABETICAL`, `FIRST_SEEN`, and `BATTERY_LEVEL`, connection state changes never cause cards to jump (`ATT-5660`).
3. **Tab Scoping**: Sort button renders on sensor tabs (0, 1, 2) and is omitted on Equipment Matrix tabs (3, 4).
4. **Pure In-Memory Reactive Pipeline**: Zero SQLite schema or `devices.db` modifications.
5. **Localization Parity**: 100% parity across all 9 application locales.

---

## 4. Proposed Architectural Changes
* **`DeviceSortOrder.kt`**: Domain enum in `com.atrainingtracker.banalservice.ui.devices.devicelist` with `ALPHABETICAL`, `FIRST_SEEN`, `LAST_USAGE`, and `BATTERY_LEVEL` with `@StringRes val labelResId: Int`.
* **`DeviceListViewModel.kt`**: Add `sortOrder: DeviceSortOrder = DeviceSortOrder.ALPHABETICAL` parameter to `getFilteredDevices(spec, criteria, sortOrder)` with comparator logic and robust `lastSeen` timestamp parsing.
* **`DevicesTabbedViewModel.kt`**: Manage `_sortOrder = MutableStateFlow<DeviceSortOrder>` with `KEY_SORT_ORDER` persistence in `SavedStateHandle`.
* **`DevicesTabbedScreen.kt` & `DeviceListScreen.kt`**: Render `Icons.AutoMirrored.Filled.Sort` button next to `FilterActionButton` when `pagerState.currentPage in 0..2`. Pass `sortOrder` to `DeviceListScreen`.
* **UI Consistency (`docs/design_guidelines.md`)**:
  * *Reference*: `RouteTabbedScreen.kt` lines 332–382 / `KnownLocationsScreen.kt` lines 198–252.
  * *Components*: `IconButton`, `DropdownMenu`, `DropdownMenuItem`, `Icons.AutoMirrored.Filled.Sort`, `Icons.Default.Check`.
  * *Tokens*: `MaterialTheme.colorScheme.onPrimaryContainer`, `surface`, `onSurface`.
  * *Visual Screenshot Verification (`Rule 84`)*: `Rule84DeviceSortScreenshotTest` verifying Sort button, dropdown checkmark, and card order in light/dark themes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: 9-Language Localization Shards (`REQ-UI-528.5`)
* Files: `app/src/main/res/values*/strings_devices.xml` (all 9 locales: `values/`, `-de/`, `-es/`, `-fr/`, `-it/`, `-ja/`, `-nl/`, `-pl/`, `-pt/`).
* Add `devices_sort_alphabetical`, `devices_sort_first_seen`, `devices_sort_last_usage`, `devices_sort_battery_level`.
### Step 2: `DeviceSortOrder` Domain Model (`REQ-UI-528.1`)
* File: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceSortOrder.kt`
* Implement enum, label mappings, and comparator helper.
### Step 3: ViewModel Sorting Pipeline (`REQ-UI-528.2`, `REQ-UI-528.4`)
* Files: `DeviceListViewModel.kt`, `DevicesTabbedViewModel.kt`
* Add `sortOrder` parameter to `getFilteredDevices()`; wire `SavedStateHandle[KEY_SORT_ORDER]`.
### Step 4: Top App Bar UI & Screen Integration (`REQ-UI-528.3`)
* Files: `DevicesTabbedScreen.kt`, `DeviceListScreen.kt`
* Add Sort `IconButton` with `DropdownMenu` on tabs 0..2; pass `sortOrder` to `DeviceListScreen`.
### Step 5: Unit Test Suite & Rule 84 Screenshot Verification (`TST-UI-528.1`..`TST-UI-528.6`)
* Files: `DeviceSortOrderTest.kt`, `DeviceListViewModelPriorityTest.kt`, `DevicesTabbedViewModelTest.kt`, `DevicesTabbedScreenContractTest.kt`, `DeviceDiscoveryLocalizationTest.kt`, `Rule84DeviceSortScreenshotTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests, AAPT2 build verification, requirement governance check (`python3 tools/verify_requirement_governance.py`).
* **Rollback**: Branch isolation (`git checkout sprint/2026-46.6`) enables zero-risk revert.
