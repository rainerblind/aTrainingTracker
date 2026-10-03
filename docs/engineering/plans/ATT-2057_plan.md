# Stage 3: Implementation Plan - ATT-2057: Hide Research Button When No Real Paired Remote Devices Exist in Database

**Ticket**: [ATT-2057](https://rainerblind.atlassian.net/browse/ATT-2057)  
**Sub-task**: [ATT-2234](https://rainerblind.atlassian.net/browse/ATT-2234) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Requirement Mapping**: `REQ-UI-259` (*Conditional Research Button Visibility Based on Real Paired Remote Devices*)  
**Test Mapping**: `TST-UI-218` (*Conditional Research Button Visibility Based on Real Paired Remote Devices Verification*)  
**Branch**: `feature/ATT-2057`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Description & Background

On `ControlTrackingScreen`, the "Suchen" button (`ResearchButton`) triggers a background search cycle for previously paired external remote devices via `BANALServiceRepository.startSearchingForPairedDevices()`.

When an athlete has no paired remote sensors in the database (e.g. fresh installation, or all external sensors have been removed or unpaired), only internal smartphone pseudo-devices exist (`Protocol.SMARTPHONE` entries representing GPS, Network Location, Google Fused Location, Phone Battery, and Barometric Altimeter). In this state, displaying the "Suchen" button creates confusion, visual clutter, and misleading affordances.

The system will conditionally render `ResearchButton` if and only if at least one real, paired remote sensor (`protocol` is `ANT_PLUS` or `BLUETOOTH_LE` and `isPaired == true`) exists in SQLite.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-259` (*Conditional Research Button Visibility Based on Real Paired Remote Devices*)
* **Test Mapping**: `TST-UI-218`
  * `TST-UI-218.1`: ViewModel emits `false` when only smartphone/internal devices exist.
  * `TST-UI-218.2`: ViewModel emits `true` when a paired BLE or ANT+ device exists.
  * `TST-UI-218.3`: ViewModel emits `false` when all remote devices are unpaired (`isPaired == false`).
  * `TST-UI-218.4`: Database query `hasPairedRemoteDevices()` executes optimized `LIMIT 1` query.
  * `TST-UI-218.5`: Brand compliance (authentic ANT+ and Bluetooth logos untouched) and 9-language localization audit.
  * `TST-UI-218.6`: Clean-room test suite regression pass (1529+ tests).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly (1529+ tests).
2. **Layout Stability & Centering**: The central information column (`SearchArea` and `RemoteDevices`) is anchored to `Alignment.TopCenter` within a `fillMaxWidth()` container and will not shift horizontally when `ResearchButton` is hidden.
3. **Trademark & Brand Protection (AC-3)**: Official registered trademarks for ANT+ (`R.drawable.ant_logo`) and Bluetooth (`R.drawable.logo_protocol_bluetooth`) SHALL NOT be altered or modified in any way.
4. **Instantaneous Cold-Start Seeding**: `hasPairedRemoteDevices` is initialized synchronously from SQLite via `devicesDatabaseManager.hasPairedRemoteDevices()`, preventing UI pop-in or visual flickering on cold start.
5. **Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `DevicesDatabaseManager.java` (Database Layer)
* Add public method `hasPairedRemoteDevices(): boolean` executing:
  ```sql
  SELECT _id FROM Devices WHERE paired > 0 AND (protocol = 'ANT_PLUS' OR protocol = 'BLUETOOTH_LE') LIMIT 1
  ```
* Ensures safe cursor lifecycle (`try ... finally { cursor.close(); }`) and returns `true` if $\ge 1$ matching rows exist.

### Component 2: `ControlTrackingViewModel.kt` (ViewModel Layer)
* Introduce `val hasPairedRemoteDevices: StateFlow<Boolean>`.
* Map `devicesRepository.allDevices` to check `devices.any { it.isPaired && (it.protocol == Protocol.ANT_PLUS || it.protocol == Protocol.BLUETOOTH_LE) }`.
* Seed `initialValue` synchronously with `devicesDatabaseManager.hasPairedRemoteDevices()`.

### Component 3: `ControlTrackingScreen.kt` & `TrackingTabsScreen.kt` (UI Layer)
* In `ControlTrackingScreen.kt`, add parameter `showResearchButton: Boolean = true`.
* Wrap the `Box(modifier = Modifier.align(Alignment.TopStart)) { ResearchButton(...) }` in `if (showResearchButton)`.
* In `TrackingTabsScreen.kt`, collect `val hasPairedRemoteDevices by controlViewModel.hasPairedRemoteDevices.collectAsState()` and pass `showResearchButton = hasPairedRemoteDevices`.
* Add Compose `@Preview` composables verifying both visibility states.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add `hasPairedRemoteDevices()` to `DevicesDatabaseManager.java`
* Target: `app/src/main/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManager.java`
* Implement `hasPairedRemoteDevices(): boolean` with parameterized `LIMIT 1` query.

### Step 2: Expose `hasPairedRemoteDevices` in `ControlTrackingViewModel.kt`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModel.kt`
* Map `allDevicesFromDb` to `StateFlow<Boolean>` seeded with `devicesDatabaseManager.hasPairedRemoteDevices()`.

### Step 3: Parameterize `ControlTrackingScreen.kt` & Wire in `TrackingTabsScreen.kt`
* Targets:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* Add `showResearchButton: Boolean = true` to `ControlTrackingScreen`.
* Conditionally render `ResearchButton` when `showResearchButton == true`.
* Bind in `TrackingTabsScreen.kt`.
* Add Preview variants for visible and hidden research button states.

### Step 4: Author Unit Tests for ViewModel and Database Manager
* Targets:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModelTest.kt`
  * `app/src/test/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManagerTest.kt`
* Implement `TST-UI-218.1`, `TST-UI-218.2`, `TST-UI-218.3`, and `TST-UI-218.4`.

### Step 5: Execute Targeted Tests
* Run:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModelTest"`
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.database.DevicesDatabaseManagerTest"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run targeted unit tests during construction.
  - Run full test suite regression (`./gradlew testDebugUnitTest`, 1529+ tests).
  - Verify trademark assets integrity (`ant_logo`, `logo_protocol_bluetooth`).
  - Author Stage 5 walkthrough (`docs/engineering/walkthroughs/ATT-2057_walkthrough.md`).
* **Rollback Plan**:
  - All changes reside on isolated feature branch `feature/ATT-2057`. Reverting or checking out `sprint/2026-40.14` restores previous baseline immediately with zero schema impact.
