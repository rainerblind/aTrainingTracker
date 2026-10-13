# Stage 1 Analysis: ATT-2465 - Move bike and shoe sensor matrices into the Sensors view as two additional tabs

**Ticket**: [ATT-2465](https://rainerblind.atlassian.net/browse/ATT-2465)  
**Sub-task**: [ATT-2606](https://rainerblind.atlassian.net/browse/ATT-2606) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2465`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & Motivation

In Sprint 2026-40.16, tickets `ATT-2126` and `ATT-2382` introduced centralized equipment-to-sensor mapping matrices with 1-tap checkboxes, partitioned by sport compatibility (Bikes vs. Shoes). Currently, this functionality is hosted as a third tab ("Sensor-Matrix", `@string/equipment_tab_sensor_matrix`) in `EquipmentTabsScreen.kt` under the Equipment ("Ausrüstung") area. In that single screen, both the Bikes and Shoes tables are vertically stacked within a single scrollable container.

During the Sprint 2026-40.16 Joint Review, human testing identified an ergonomic and architectural misalignment:
> *"It might be better to place these matrices in the Sensors. I.e. within the sensors view, we add two more tabs on the very right. One for the bikes and one for the shoes."*

From an athlete's mental model, associating physical sensors with equipment is fundamentally a sensor management activity. The Sensors screen ("Alle Sensoren" / `DevicesTabbedScreen.kt`) currently hosts 3 tabs:
1. `Verbunden` (`devices_tab_available` / Available / Connected)
2. `Gekoppelt` (`devices_tab_paired` / Paired)
3. `Bekannt` (`devices_tab_known` / All Known)

Moving the matrices into the Sensors view as two dedicated tabs at the very right:
- Tab 3: `Räder` (`devices_tab_bikes` / Bikes)
- Tab 4: `Schuhe` (`devices_tab_shoes` / Shoes)

creates a unified, coherent sensor hub (`Verbunden` | `Gekoppelt` | `Bekannt` | `Räder` | `Schuhe`). Furthermore, separating Bikes and Shoes into dedicated tabs eliminates the awkward vertical stacking of both tables in one screen, allowing each sport matrix to maximize viewport space with dedicated vertical and horizontal scrolling.

---

## 2. Root Cause Analysis (Forensic Investigation & Architectural Gap Analysis)

### 2.1 Current Sensors UI Architecture (`DevicesTabbedScreen.kt`)
* `DevicesTabbedScreen` uses Jetpack Compose Material 3 `PrimaryScrollableTabRow` to display tabs.
* Currently, the tabs list is hardcoded to 3 `DeviceFilterSpec` items:
  ```kotlin
  val tabs = remember(protocol, deviceType) {
      listOf(
          DeviceFilterSpec(DeviceFilterType.CONNECTED, protocol, deviceType),
          DeviceFilterSpec(DeviceFilterType.PAIRED, protocol, deviceType),
          DeviceFilterSpec(DeviceFilterType.ALL_KNOWN, protocol, deviceType)
      )
  }
  ```
* The `HorizontalPager` maps each index `0..2` directly to `DeviceListScreen`.
* `DevicesTabbedScreen` does not yet observe `EquipmentViewModel` or render sport-specific equipment matrices.
* However, `PrimaryScrollableTabRow` is already in place (line 209), meaning horizontal scrolling of tab chips is natively supported without text truncation or wrapping when expanding to 5 tabs.

### 2.2 Current Equipment UI Architecture (`EquipmentTabsScreen.kt`)
* `EquipmentTabsScreen` currently defines 3 tabs:
  ```kotlin
  val tabs = listOf(
      stringResource(R.string.equipment_type_bike),
      stringResource(R.string.equipment_type_shoe),
      stringResource(R.string.equipment_tab_sensor_matrix)
  )
  ```
* On page 2, it invokes `EquipmentSensorMatrixScreen`, which renders Bikes and Shoes tables vertically stacked with `MatrixSectionHeader` banners.
* The floating action button (FAB) for adding new equipment is conditionally suppressed on page 2 (`if (pagerState.currentPage != 2)`).

### 2.3 Evaluation & Decision on Equipment Area Entry Point (Ticket Clause 4)
Ticket `ATT-2465` explicitly requires:
> *"Decide in Analysis whether the matrix entry point in the equipment area is removed or kept as a shortcut."*

**Analysis & Decision**:
1. **Removal of 3rd tab from `EquipmentTabsScreen`**:
   - Having the matrix present in both `EquipmentTabsScreen` (as a stacked combo table) and `DevicesTabbedScreen` (as two dedicated tabs) creates duplicate UI surfaces, confusing mental models ("Should I configure my matrix in Equipment or in Sensors?"), and maintenance overhead.
   - Individual equipment item configuration in `EquipmentTabsScreen` already provides direct sensor selection via `EditEquipmentDialog` (multi-select sensor spinner).
   - In the Navigation Drawer, "Meine Sensoren" is located immediately adjacent to "Fahrräder" and "Laufschuhe".
   - Removing the 3rd tab restores `EquipmentTabsScreen` to a clean, focused 2-tab screen (`Räder` and `Schuhe`), while centralizing all fleet-wide sensor mapping under `Sensors`.
   - **Conclusion**: Remove the "Sensor-Matrix" tab from `EquipmentTabsScreen`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Extend `DevicesTabbedScreen.kt` tabs to 5 items: `Verbunden` | `Gekoppelt` | `Bekannt` | `Räder` | `Schuhe`.
  * Support `PrimaryScrollableTabRow` tab bar navigation across all 5 pages.
  * Integrate `EquipmentViewModel` into `DevicesTabbedScreen` to provide reactive `bikes`, `shoes`, `bikeSensors`, `shoeSensors`, and `setSensorLink` mutator.
  * In `DevicesTabbedScreen`:
    - Page 3 renders the Bikes Equipment-to-Sensor Matrix table.
    - Page 4 renders the Shoes Equipment-to-Sensor Matrix table.
  * Ensure independent horizontal scroll states, sticky equipment column (`STICKY_COLUMN_WIDTH = 184.dp`), sport-specific column prioritization (`ATT-2464`), and 1-tap Material 3 Checkbox persistence.
  * Seamlessly support collapsing top app bar via `CollapsingAppBarNestedScrollConnection` when scrolling matrix tables vertically.
  * Remove the redundant 3rd tab (`equipment_tab_sensor_matrix`) from `EquipmentTabsScreen.kt`, reverting it to 2 clean tabs (`Bikes` and `Shoes`).
  * 100% 9-language localization parity for new tab labels (`devices_tab_bikes` / `devices_tab_shoes` across EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No SQLite schema alterations in `Equipment.db`, `Devices.db`, or `LINKS` table.
  * No modifications to Bluetooth LE / ANT+ scanner lifecycle, device connection state machines, or background services.
  * No changes to workout telemetry recording, fit export, or Strava sync.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

This ticket refines and amends existing requirement `REQ-UI-256` (*Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes*):

* **Original Requirement ID & Target**: `REQ-UI-256`, targeting `EquipmentTabsScreen.kt`, `EquipmentSensorMatrixScreen.kt`, `EquipmentViewModel.kt`, and `DevicesDatabaseManager.java`.
* **Historical Origin & Commit Trace**: Introduced in Sprint `2026-40.16`, ticket `ATT-2126` (commit `fc10ad81`); refined in `ATT-2382` (partitioned into bike and shoe tables); sorted by sport domain priority in `ATT-2464`.
* **Root Reason for Existing Formulation**: `REQ-UI-256` originally placed the matrix as a 3rd tab in `EquipmentTabsScreen` because it was initially conceived as an equipment property view. However, physical usage showed that athletes expect to configure sensor connections in the Sensors screen. Placing both tables in one tab also caused vertical crowding.
* **Preservation of Core Invariants**:
  - 1-tap Checkbox persistence in SQLite `LINKS` table via `EquipmentDbHelper.setDeviceLink` is strictly preserved.
  - Bidirectional reactive synchronization with `EditEquipmentDialog` and `EditDeviceDialog` (`REQ-UI-257`) is strictly preserved.
  - Sticky equipment name and icon column (`184.dp`) with 2-line wrapping (`REQ-UI-263`) is strictly preserved.
  - Independent horizontal scrolling for bike and shoe tables is strictly preserved.
  - Sport-compatible sensor filtering and domain priority ordering (`REQ-UI-283` / `ATT-2464`) are strictly preserved.
  - 100% full-suite test pass rate with 0 regressions is strictly maintained.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Tab Model Extension in `DevicesTabbedScreen.kt`
Define a clean sealed interface or enum for tabs in `DevicesTabbedScreen`:
```kotlin
sealed class SensorScreenTab {
    data class DeviceFilter(val spec: DeviceFilterSpec) : SensorScreenTab()
    object BikesMatrix : SensorScreenTab()
    object ShoesMatrix : SensorScreenTab()
}
```
Or an indexed list:
- Index 0: Available (`DeviceFilterType.CONNECTED`)
- Index 1: Paired (`DeviceFilterType.PAIRED`)
- Index 2: Known (`DeviceFilterType.ALL_KNOWN`)
- Index 3: Bikes Matrix
- Index 4: Shoes Matrix

### 5.2 Single-Sport Matrix Composable Component
Refactor `EquipmentSensorMatrixScreen.kt` to expose a clean, reusable `EquipmentSportSensorMatrix` composable:
- Renders a single sport's matrix table (either Bikes or Shoes).
- Eliminates redundant in-tab section headers ("Räder" within the "Räder" tab).
- Manages independent horizontal and vertical scroll states.
- Connects to nested scroll for collapsing app bar.
- Shows sport-tailored empty states when equipment or sensors are absent.

### 5.3 EquipmentTabsScreen Streamlining
- Revert `EquipmentTabsScreen` to 2 tabs: `R.string.equipment_type_bike` and `R.string.equipment_type_shoe`.
- Render FAB unconditionally for both tabs.
- Remove references to `EquipmentSensorMatrixScreen`.

### 5.4 9-Language Localization
Add `devices_tab_bikes` and `devices_tab_shoes` to `strings_devices.xml` across all 9 supported locales:
- `values/`: "Bikes", "Shoes"
- `values-de/`: "Räder", "Schuhe"
- `values-es/`: "Bicicletas", "Zapatillas"
- `values-fr/`: "Vélos", "Chaussures"
- `values-it/`: "Biciclette", "Scarpe"
- `values-ja/`: "バイク", "シューズ"
- `values-nl/`: "Fietsen", "Schoenen"
- `values-pl/`: "Rowery", "Buty"
- `values-pt/`: "Bicicletas", "Sapatilhas"

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression across all 2002+ clean-room unit tests.
  2. 1-tap checkbox toggling immediately updates `LINKS` in SQLite and synchronizes reactively with all screens.
  3. Independent horizontal scrolling between Bikes and Shoes tabs.
  4. Material 3 design tokens, typography, and collapsing app bar compatibility preserved.
  5. 100% 9-language translation parity maintained.
  6. Human Decision Gate strictly guarded on parent ticket `ATT-2465`.
* **Risk Rating**: **LOW**. The underlying data access layer, database managers, and ViewModel state flows are mature and unchanged. The modification is purely a presentation layout and tab navigation enhancement in Jetpack Compose.
