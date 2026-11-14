# Stage 1 Analysis: ATT-7663 - Add sensor list sorting by Alphabetical, First Seen, Last Usage, and Battery Level

**Ticket**: [ATT-7663](https://rainerblind.atlassian.net/browse/ATT-7663)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feature/ATT-7663`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
The "My Sensors" screen (`DevicesTabbedScreen` / `DeviceListViewModel`) provides multi-dimensional filtering, but ordering is hardcoded to Alphabetical (`A–Z`). Users with multi-sensor ecosystems cannot easily sort sensors to find recently added peripherals, inspect recently active sensors, or check low batteries before a workout. A top app bar Sort dropdown offering Alphabetical (default), First Seen, Last Usage, and Battery Level is needed, matching Routes and Known Locations UX.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA Gap**: In `DeviceListViewModel.kt` (lines 149–156), `filtered.sortedWith(...)` statically applies `isNewlyDiscovered` prioritization followed strictly by `String.CASE_INSENSITIVE_ORDER` on `deviceName` and `id`. No sorting parameter is supported.
* **Top App Bar Gap**: `DevicesTabbedScreen.kt` top bar hosts `FilterActionButton` and ANT+ overflow menu, but lacks a Sort button (`Icons.AutoMirrored.Filled.Sort`).
* **Independent Hypothesis Verification**:
  * *Tab Scope*: Sensor sorting applies to sensor list tabs (Available, Paired, All Known; `pagerState.currentPage in 0..2`). On Equipment Matrix tabs (Bikes/Shoes tabs 3 and 4), the Sort button must be hidden.
  * *Timestamp Parsing*: `lastSeen` values vary between localized `DateFormat.getDateTimeInstance()`, ISO strings, and `"Now"`. For `LAST_USAGE`, connected sensors (`isConnected == true`) lead, followed by parsed timestamp descending, with unparseable/null dates placed at bottom.
  * *Scan Recency Pinning*: `isNewlyDiscovered == true` sensors must remain pinned at the very top across all sort modes in recency order (`newlyFoundDevicesIds.indexOf(it.id)` descending per `REQ-UI-334`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Introduce `DeviceSortOrder` enum (`ALPHABETICAL`, `FIRST_SEEN`, `LAST_USAGE`, `BATTERY_LEVEL`) with string resources.
  * Extend `DeviceListViewModel.getFilteredDevices` with `sortOrder` parameter implementing multi-tier sorting and deterministic tie-breakers.
  * Add Sort `IconButton` with `DropdownMenu` and checkmark in `DevicesTabbedScreen.kt`, backed by `DevicesTabbedViewModel` / `SavedStateHandle`.
  * Ensure 100% 9-language localization parity in `strings_devices.xml` (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
* **Out-of-Scope Non-Goals**:
  * No sorting changes to Equipment Matrix tabs (Bikes / Shoes tabs 3 and 4).
  * No SQLite database schema changes (sorting remains purely in-memory Flow transformation).
  * No modifications to `DeviceFilterCriteria` filtering mechanics.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-334` Clause 2 (*Top-Of-List Scan Priority Sorting & Stable List Ordering*).
* **Historical Origin & Commit Trace**: `ATT-3099` (commit `7df8d5b0`) introduced `isNewlyDiscovered` recency priority; `ATT-5660` (Sprint `2026-45.9`) removed hardcoded `isConnected` sorting to prevent sensor jumping during pairing toggles.
* **Root Reason for Existing Formulation**: `ATT-5660` enforced stable alphabetical order without `isConnected` jumping.
* **Preservation of Core Invariants**: `ALPHABETICAL` remains the default sort order with zero jumping on connection changes. Pinning of `isNewlyDiscovered` sensors at the top is strictly preserved across all sort orders.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-UI-334` (extended to support user-selected `DeviceSortOrder`).
* **Affected Architecture Elements**: `DeviceSortOrder.kt`, `DeviceListViewModel.kt`, `DevicesTabbedViewModel.kt`, `DevicesTabbedScreen.kt`, `DeviceListScreen.kt`, `strings_devices.xml` (9 locales).
* **Verification Impact & Test Surface**: `DeviceListViewModelPriorityTest.kt`, new `DeviceSortOrderTest.kt`, `DeviceDiscoveryLocalizationTest.kt`, `DevicesTabbedScreenContractTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Isolated to UI and ViewModel presentation layers.

---

## 6. Architectural Strategy & High-Level Solution
* Define `DeviceSortOrder(@StringRes val labelResId: Int)` in `banalservice.ui.devices.devicelist`.
* Manage `_sortOrder: MutableStateFlow<DeviceSortOrder>` in `DevicesTabbedViewModel` with `SavedStateHandle[KEY_SORT_ORDER]`.
* In `DevicesTabbedScreen.kt`, display `IconButton(Icons.AutoMirrored.Filled.Sort)` next to `FilterActionButton` when `pagerState.currentPage in 0..2`.
* In `DeviceListViewModel.getFilteredDevices(spec, criteria, sortOrder)`:
  * Primary tier: `compareByDescending { it.isNewlyDiscovered }.thenByDescending { newlyFoundIds.indexOf(it.id) }`.
  * Secondary tier:
    * `ALPHABETICAL`: `deviceName` (case-insensitive) ascending, then `id` ascending.
    * `FIRST_SEEN`: `id` descending.
    * `LAST_USAGE`: `isConnected` descending, then parsed `lastSeen` timestamp descending (null/unparseable $\to$ 0L), then `deviceName` ascending, then `id` ascending.
    * `BATTERY_LEVEL`: Known battery (`batteryPercentage >= 0`) ascending, then unknown (`< 0`), then `deviceName` ascending, then `id` ascending.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `isNewlyDiscovered` sensors remain pinned at top in recency order across all sort orders (`REQ-UI-334`).
  2. `ALPHABETICAL` default provides zero jumping on connection changes.
  3. 100% 9-language localization parity with non-empty strings.
* **Risk Rating**: **LOW** — Presentation layer only; no persistence or GATT state machine risks.
