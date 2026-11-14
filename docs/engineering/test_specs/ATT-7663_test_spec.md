# Stage 2: Requirement & Test Specification - ATT-7663: Add Sensor List Sorting by Alphabetical, First Seen, Last Usage, and Battery Level

**Ticket**: [ATT-7663](https://rainerblind.atlassian.net/browse/ATT-7663) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feature/ATT-7663`  
**Requirement Mapping**: `REQ-UI-528` (`REQ-UI-528.1`..`REQ-UI-528.5`) | **Test Spec ID**: `TST-UI-528` (`TST-UI-528.1`..`TST-UI-528.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-14  

---

## 1. Requirement Specification (`REQ-UI-528`)

### 1.1 Attributes & Problem Statement
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- Sensor list ordering in `DevicesTabbedScreen` / `DeviceListViewModel` is hardcoded to Alphabetical (`A–Z`). Athletes cannot sort sensors to inspect recently added sensors, recently active devices, or check low batteries before workouts. A Sort dropdown menu (`Icons.AutoMirrored.Filled.Sort`) matching Routes and Known Locations patterns is needed for sensor list tabs.

### 1.2 Functional & Architectural Requirements
1. `DeviceSortOrder` enum SHALL define `ALPHABETICAL` (default), `FIRST_SEEN`, `LAST_USAGE`, and `BATTERY_LEVEL`, each mapped to a localized string resource ID.
2. `DeviceListViewModel.getFilteredDevices` SHALL accept a `sortOrder: DeviceSortOrder` parameter.
3. *Primary Scan Recency Tier*: All `isNewlyDiscovered == true` sensors SHALL remain pinned at the very top of the list in reverse discovery order, strictly preserving `REQ-UI-334`.
4. *Secondary Sort Order Tier*:
   - `ALPHABETICAL`: Case-insensitive `deviceName` ascending (`A–Z`), tie-broken by `id` ascending.
   - `FIRST_SEEN`: Database row ID (`id`) descending (newest added/discovered first).
   - `LAST_USAGE`: Connected devices (`isConnected == true`) first, followed by disconnected devices ordered by parsed `lastSeen` timestamp descending (null/unparseable $\to$ 0L), tie-broken by `deviceName` (`A–Z`) and `id` ascending.
   - `BATTERY_LEVEL`: Known battery percentage (`batteryPercentage >= 0`) ascending (lowest battery first), followed by unknown battery (`batteryPercentage < 0`), tie-broken by `deviceName` (`A–Z`) and `id` ascending.
5. In `DevicesTabbedScreen.kt`, the top bar SHALL display a Sort `IconButton` with `DropdownMenu` and checkmark when `pagerState.currentPage in 0..2` (hidden on Equipment tabs 3 and 4).
6. `DevicesTabbedViewModel` SHALL persist `sortOrder` in `SavedStateHandle[KEY_SORT_ORDER]`.
7. 100% 9-language localization parity in `strings_devices.xml` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* an athlete on "My Sensors" sensor tabs (0, 1, or 2), *When* tapping the Sort icon button, *Then* a dropdown menu opens showing a checkmark on the active sort order.
- *Given* any selected sort order, *When* active scanning discovers new sensors, *Then* `isNewlyDiscovered` devices remain pinned at the top while remaining devices sort according to the selected criterion.
- *Given* Equipment tabs (Bikes/Shoes), *When* navigating to tab 3 or 4, *Then* the Sort icon button is hidden.

### Requirement Archaeology & Chesterton's Fence Audit
- *Original Requirement ID & Target*: Extends `REQ-UI-334` Clause 2 (`DeviceListViewModel.kt` sorting).
- *Historical Origin & Commit Trace*: `ATT-3099` (`7df8d5b0`) added scan prioritization; `ATT-5660` removed hardcoded connection jumping.
- *Root Reason for Existing Formulation*: Stable alphabetical sorting prevented cards jumping when pairing state changed.
- *Preservation of Core Invariants*: Alphabetical default with zero jumping on connection changes and newly discovered top-pinning remain 100% preserved.

---

## 2. Test Specification (`TST-UI-528`)

### Test Cases
- `[TST-UI-528.1]` (Unit - `DeviceSortOrderTest.kt`): Verify comparators for `ALPHABETICAL`, `FIRST_SEEN`, `LAST_USAGE`, and `BATTERY_LEVEL` with name/id tie-breakers.
- `[TST-UI-528.2]` (Unit - `DeviceListViewModelPriorityTest.kt`): Verify `isNewlyDiscovered == true` sensors remain pinned at top across all 4 sort orders.
- `[TST-UI-528.3]` (Unit - `DevicesTabbedViewModelTest.kt`): Verify `sortOrder` default is `ALPHABETICAL` and updates survive `SavedStateHandle`.
- `[TST-UI-528.4]` (Unit - `DevicesTabbedScreenContractTest.kt`): Verify Sort button is present on sensor tabs (0..2) and omitted on Equipment tabs (3..4).
- `[TST-UI-528.5]` (Localization - `TranslationParityTest.kt`): Verify 100% parity across all 9 locales for all 4 sort labels.
- `[TST-UI-528.6]` (Tier 1 Regression): Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.*" --tests "com.atrainingtracker.trainingtracker.build.*"` asserting 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component & Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-528.1]` | Unit | `DeviceSortOrder` (ordering logic & tie-breakers) | `REQ-UI-528.1`, `REQ-UI-528.2` | Specified |
| `[TST-UI-528.2]` | Unit | `DeviceListViewModel.getFilteredDevices` (top-pinning) | `REQ-UI-528.2` | Specified |
| `[TST-UI-528.3]` | Unit | `DevicesTabbedViewModel` (state & SavedStateHandle) | `REQ-UI-528.4` | Specified |
| `[TST-UI-528.4]` | Contract | `DevicesTabbedScreen` (top bar Sort button visibility) | `REQ-UI-528.3` | Specified |
| `[TST-UI-528.5]` | Localization | `strings_devices.xml` / `TranslationParityTest` | `REQ-UI-528.5` | Specified |
| `[TST-UI-528.6]` | Regression | `com.atrainingtracker.banalservice.ui.devices.*` | `REQ-PRO-001` | Specified |
