# Stage 1 Analysis: ATT-2126 - Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes

**Ticket**: [ATT-2126](https://rainerblind.atlassian.net/browse/ATT-2126)  
**Sub-task**: [ATT-2205](https://rainerblind.atlassian.net/browse/ATT-2205) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-2126`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

In the current version of aTrainingTracker, athletes must configure sensor pairings on a per-equipment basis:
1. Navigate to the `EquipmentTabsScreen` ("Bikes" or "Shoes" tab).
2. Tap an individual equipment card to open its menu / configuration dialog (`EditEquipmentDialog`).
3. Scroll through a multi-select dropdown (`MultiSelectSensorSpinner`) to inspect or modify linked sensors (heart rate monitors, bike power meters, speed sensors, cadence sensors, etc.).
4. Save and repeat the process for every bike and pair of shoes.

### Core Deficiencies
* **Lack of Holistic Fleet Visibility**: Athletes cannot see at a glance which sensor is mounted to which bike or paired with which pair of shoes.
* **Double-Assignment & Omission Hazards**: It is difficult to spot if a sensor was accidentally assigned to two bikes, or if a newly paired sensor (e.g. new power meter or HRM strap) has not been linked to any bike or shoe.
* **Configuration Friction**: Swapping a sensor between bikes requires opening, scrolling, toggling, and saving two separate modal bottom sheets.

### Proposed Solution
Introduce a compact, high-efficiency **Equipment-to-Sensor Mapping Matrix with Checkboxes** as a 3rd tab in `EquipmentTabsScreen` ("Bikes" | "Shoes" | "Sensor Matrix"):
* **Columns (Header)**: All coupled external remote sensors/devices (HRM, Power Meters, Speed/Cadence sensors, Varia radars, Run footpods).
* **Rows**: Equipment items grouped into two clear sections:
  * 🚴 **Section 1: Bikes**
  * 👟 **Section 2: Shoes**
* **Cells (Intersections)**: Interactive 1-tap checkboxes directly linked to the database `LINKS` table.
* **Sticky First Column**: Sticky equipment identifier on the left for effortless horizontal scrolling across multiple sensor columns.
* **Instant Persistence**: Tapping any checkbox immediately toggles the link in `EquipmentDbHelper` and reactively updates state across all tabs.

---

## 2. Root Cause Analysis & Architectural Gap Analysis

### Current Architecture State
1. **EquipmentTabsScreen & Pager**:
   - `EquipmentTabsScreen.kt` defines a 2-page `HorizontalPager` with tabs `[R.string.equipment_type_bike, R.string.equipment_type_shoe]`.
   - Collapsing header (`CollapsingAppBarNestedScrollConnection`) coordinates app bar offsets.
   - Pager contents render `EquipmentList` for each page.
2. **Persistence Layer (`EquipmentDbHelper.java`)**:
   - Manages SQLite database `Equipment.db`.
   - Table `Equipment`: `_ID`, `Name`, `SportType`, `FrameType`, `StravaName`, `StravaId`, `Retired`.
   - Table `Links`: `EquipmentId`, `ANTDeviceId`.
   - Currently provides bulk update `updateEquipment(id, name, frameType, linkedDeviceIds, isRetired)` which clears all links for that equipment and re-inserts them. It lacks atomic single-link toggle methods (`addDeviceLink`, `removeDeviceLink`, `setDeviceLink`).
3. **Sensor Discovery (`DevicesDatabaseManager.java`)**:
   - Provides `getSensorsForSportType(BSportType.BIKE)` and `getSensorsForSportType(BSportType.RUN)`.
   - However, `getSensorsForSportType` filters strictly by `DEVICE_TYPE LIKE 'BIKE%'` or `RUN%`, excluding cross-sport sensors like Heart Rate Monitors (`HRM`) or generic environmental sensors from bike/shoe dropdowns.
   - There is no single method returning all user-paired remote sensors (`ANT_PLUS` and `BLUETOOTH_LE`) sorted alphabetically for a comprehensive matrix header.
4. **State Management (`EquipmentViewModel.kt`)**:
   - Exposes `bikes: StateFlow<List<EquipmentItem>>` and `shoes: StateFlow<List<EquipmentItem>>`.
   - Does not expose an aggregated `allRemoteSensors: StateFlow<List<SimpleSensorInfo>>` flow.
   - Lacks a 1-tap `setSensorLink(equipmentId, sensorId, isLinked)` method.

### Architectural Blueprint for Matrix
1. **Database Enhancements (`EquipmentDbHelper.java`)**:
   - Add atomic methods:
     - `addDeviceLink(long equipmentId, long deviceId)`
     - `removeDeviceLink(long equipmentId, long deviceId)`
     - `setDeviceLink(long equipmentId, long deviceId, boolean isLinked)`
   - Wrap operations in SQLite transactions to guarantee data consistency.
2. **Sensor Aggregation (`DevicesDatabaseManager.java`)**:
   - Add `getAllRemoteSensors(): List<SimpleSensorInfo>`:
     - Queries `Devices` table for all paired devices where `NAME IS NOT NULL AND NAME != ''` and device type is remote (or protocol is `ANT_PLUS` or `BLUETOOTH_LE`).
     - Returns items sorted alphabetically by sensor name.
3. **ViewModel Integration (`EquipmentViewModel.kt`)**:
   - Expose `allRemoteSensors: StateFlow<List<SimpleSensorInfo>>`.
   - Add `setSensorLink(equipmentId: Long, sensorId: Long, isLinked: Boolean)` running on `ioDispatcher`, calling `EquipmentDbHelper.setDeviceLink`, followed by `loadEquipment()`.
4. **Composable UI (`EquipmentSensorMatrixScreen.kt` & `EquipmentTabsScreen.kt`)**:
   - Extend `tabs` in `EquipmentTabsScreen.kt` to 3 tabs: `Bikes`, `Shoes`, `Sensor Matrix`.
   - Build `EquipmentSensorMatrixScreen`:
     - Outer scroll container with vertical scrolling and sticky sensor header.
     - Horizontal scroll container with sticky left column (Equipment name and icon).
     - Group headers: 🚴 Bikes and 👟 Shoes with item counts.
     - Checkbox cells with accessible tap targets (minimum 48dp).
     - Empty states when no sensors are paired or no equipment exists.
     - Floating Action Button hidden on the Matrix tab to prevent modal clutter.

---

## 3. Requirement Archaeology & Chesterton's Fence Audit

### Archaeology Matrix
1. **Original Requirement ID & Target**:
   - Net-new requirement (`REQ-UI-256`: *Equipment-to-Sensor Mapping Matrix with Checkboxes*), extending `REQ-UI-158` (*Equipment Management*) and `REQ-UI-160` (*Equipment-to-Sensor Mapping*) under Epic `ATT-355` (*Good and consistent UI*).
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.1 / Sprint 2026-40.2 (commits establishing `EquipmentTabsScreen`, `EditEquipmentDialog`, and `EquipmentDbHelper`).
3. **Root Reason for Existing Formulation**:
   - Sensor mapping was initially attached exclusively to the equipment editing modal bottom sheet (`EditEquipmentDialog`) because early designs focused on creating and updating individual bikes or shoes. As athletes accrued multiple bikes (e.g. Road, MTB, Gravel) and multiple sensors (Powermeter, HR monitor, Speed sensor), configuring equipment in silos became inefficient.
4. **Preservation of Core Invariants**:
   - **Database Schema Integrity**: Preserves existing `Equipment` and `Links` SQLite tables without schema migrations.
   - **Bidirectional Consistency**: Changes made in the matrix immediately reflect in the single-equipment dialog and vice versa.
   - **Touch Target Accessibility**: Checkbox cells adhere to Material 3 accessibility guidelines.
   - **Collapsing Header Synergy**: Integrates cleanly into `EquipmentTabsScreen` with `CollapsingAppBarNestedScrollConnection`.
   - **9-Language Localization Parity (`REQ-LOC-001`)**: All new UI strings fully translated in EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 4. User Scope Grounding (ATT-1250)

### In-Scope Objectives
1. **Database Layer**: Add atomic `addDeviceLink`, `removeDeviceLink`, and `setDeviceLink` to `EquipmentDbHelper.java`.
2. **Sensor Discovery**: Add `getAllRemoteSensors()` to `DevicesDatabaseManager.java`.
3. **ViewModel Layer**: Expose `allRemoteSensors` StateFlow and `setSensorLink(...)` in `EquipmentViewModel.kt`.
4. **UI Layer**:
   - Expand `EquipmentTabsScreen.kt` to 3 tabs ("Bikes", "Shoes", "Sensor Matrix").
   - Author `EquipmentSensorMatrixScreen.kt` with sticky equipment column, horizontal sensor scrolling, section grouping (Bikes, Shoes), and 1-tap checkbox toggling.
5. **Localization**: Add strings for `@string/equipment_tab_sensor_matrix`, `@string/equipment_matrix_no_sensors`, etc., in 9 languages.
6. **Testing**: Author unit and contract tests verifying link toggling, sensor retrieval, and matrix UI state rendering.

### Out-of-Scope (Non-Goals)
1. Modifying SQLite table schema (`Equipment.db` or `Devices.db`).
2. Auto-linking heuristics or automatic unlinking of sensors from other equipment.
3. Modifying Strava equipment synchronization mechanics.

---

## 5. Risk Assessment & Mitigation

| Risk | Impact | Likelihood | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| Many sensors causing horizontal layout overflow | Medium | High | Implement sticky left column with smooth horizontal scrolling container and fixed column widths. |
| Rapid checkbox toggling causing SQLite concurrency race conditions | Medium | Low | Execute DB link updates within synchronized SQLite transactions on `Dispatchers.IO` and refresh state sequentially. |
| Empty sensor list rendering broken matrix | Low | Medium | Provide an informative `EmptyStatePlaceholder` when no remote sensors are paired, guiding the athlete to pair sensors first. |
