# Stage 3: Implementation Plan - ATT-6707: Fix RadarLightController pause/stop lifecycle & MockK guard, detect StVZO hardware in BTLERadarDevice, and gate optimistic state updates

**Ticket**: [ATT-6707](https://rainerblind.atlassian.net/browse/ATT-6707)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-CON-031.1`, `REQ-CON-031.2`, `REQ-CON-031.3`, `REQ-CON-031.4`, `REQ-CON-031.5`, `REQ-CON-031.6`, `REQ-CON-031.7`  
**Test Mapping**: `TST-CON-031.1`, `TST-CON-031.2`, `TST-CON-031.3`, `TST-CON-031.4`, `TST-CON-031.5`, `TST-CON-031.6`, `TST-CON-031.7`, `TST-CON-031.8`  
**Branch**: `feature/ATT-6707`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Garmin Varia radar tail light control suffers from three architectural and lifecycle defects:
1. `BTLERadarDevice.java` hardcodes `isStvzo = false` without querying Device Information `0x2A24` Model Number string (`010-03180-10`, `RTL516`, etc.), leaving StVZO flashing-mode pruning disabled, failing to read initial tail light state on connect, and failing to restore active light mode after mid-ride BLE reconnects.
2. `TrackerService.onDestroy()` unconditionally stops the tail light on workout pause (extinguishing rear safety light at red lights), while `RESUME_AUTOMATICALLY` does not restore it. On stop, `onWorkoutStopped` fires twice and launches an unmanaged `CoroutineScope(Dispatchers.IO)` reading DataStore, racing with `BANALService` unbinding. `context.filesDir` checks cause `MockKException` crashes in unit tests (`Coding Standards §5.5`).
3. `RadarLightController.setLightMode()` mutates `_currentMode` and `RadarStateRepository` optimistically before confirming dispatch. `RadarSettingsSection` uses an anti-pattern 5-chip `FlowRow` (> 4 options) and dispatches live GATT writes when editing idle preferences. `RadarLightQuickSwitchSheet` uses raw `Color.Gray` and lacks `@Preview` composables.

---

## 2. Traceability & Requirements Mapping
* **REQ-CON-031.1 (StVZO Model Detection & Initial/Reconnect Sync)**: Discovers `0x180A`/`0x2A24`, reads StVZO model string, reads initial `6a4e3202` mode, and re-applies active mode on reconnect -> `TST-CON-031.1`
* **REQ-CON-031.2 (Mode Pruning & Gated State Mutation)**: Prunes flashing modes under StVZO; mutates `_currentMode` and `RadarStateRepository` strictly when `dispatched == true` -> `TST-CON-031.2`
* **REQ-CON-031.3 (Quick-Switch Sheet Token & Previews)**: Uses `MaterialTheme.colorScheme.outline`; adds Light, Dark, and StVZO `@Preview`s -> `TST-CON-031.3`
* **REQ-CON-031.4 (Workout Lifecycle Auto-Switch & Safety Pause Preservation)**: Synchronous preference cache without `context.filesDir` or unmanaged coroutines; workout pause never turns off light; supports `RESUME_AUTOMATICALLY`; dispatches `OFF` synchronously in `handleActionStop` before service unbind -> `TST-CON-031.4`
* **REQ-CON-031.5 (Compliant Settings Selector & Idle Isolation)**: Replaces 5-chip `FlowRow` with single-selection dialog/menu; modifies DataStore without live peripheral writes when idle -> `TST-CON-031.5`
* **REQ-CON-031.6 (Traffic Simulator Light State Emulation)**: Emulates tail light command reception for UI verification -> `TST-CON-031.6`
* **REQ-CON-031.7 (9-Language Parity)**: 100% key/format parity across all 9 locales in `strings_radar.xml` -> `TST-CON-031.7`

---

## 3. System Invariants & Preserved Behavior
1. **Safety First**: Active tail light illumination is strictly preserved during workout pause (zero light cutoff at traffic stops).
2. **Deterministic Teardown**: Tail light `OFF` command dispatched synchronously before `BANALService` unbinds in `handleActionStop`.
3. **Strict MockK & Zero Unmanaged Coroutines**: Synchronous preference caching; zero `context.filesDir` inspections; zero `CoroutineScope(Dispatchers.IO)` spawns (`Coding Standards §5.5`).
4. **StVZO Compliance**: German legal restrictions enforced (solid & off only; flashing modes pruned).
5. **No Schema Changes**: Pure in-memory / DataStore configuration; zero SQLite migrations.

---

## 4. UI Consistency (Design Guidelines Section 5)
* **Reference Screens**: `RadarSettingsSection.kt` and `RadarLightQuickSwitchSheet.kt`.
* **Component Reuse**: Replace 5-chip `FlowRow` in Settings with an accessible single-choice selector (e.g. dropdown / segmented dialog) complying with Design Guidelines §1.3/§1.4. In `RadarLightQuickSwitchSheet`, replace `Color.Gray` with `MaterialTheme.colorScheme.outline`.
* **Theme Tokens**: `MaterialTheme.colorScheme.outline`, `TTColor.RadarThreatRed`, `TTColor.RadarThreatAmber`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Model Number `0x2A24` Detection & Reconnect Sync (`BTLERadarDevice.java` & `BluetoothConstants.java`)
* Define `UUID_CHARACTERISTIC_MODEL_NUMBER` (`00002a24-0000-1000-8000-00805f9b34fb`) in `BluetoothConstants.java`.
* In `BTLERadarDevice.java`, read Device Information characteristic `0x2A24` upon service discovery.
* Parse model number string; set `mIsStvzo = true` if matching `010-03180-10`, `RTL516`, or `StVZO`.
* Register with `RadarLightController.registerRadarDevice(this, mIsLightControlSupported, mIsStvzo)`.
* On connect/reconnect, read/subscribe `6a4e3202` and re-enqueue active light mode if workout is in progress.

### Step 2: Synchronous Preference Caching & Gated Mutation (`RadarLightController.kt`)
* Remove `context.filesDir` checks and `CoroutineScope(Dispatchers.IO).launch` calls.
* Cache `radarAutoLightControl` and `radarLightDefaultMode` synchronously via `updateCachedPreferences(...)`.
* In `setLightMode(mode)`, only update `_currentMode` and `RadarStateRepository` if `dispatched == true`.
* In `onWorkoutStarted`, trigger cached default mode synchronously if auto-light control is enabled.
* In `onWorkoutStopped`, trigger `RadarLightMode.OFF` synchronously if auto-light control is enabled.

### Step 3: Lifecycle Hardening & Stop Synchronization (`TrackerService.java`)
* In `TrackerService.onStartCommand()`, include `StartType.RESUME_AUTOMATICALLY` alongside `START_NORMAL` and `RESUME_BY_USER` for `onWorkoutStarted()`.
* In `handleActionStop()`, dispatch `RadarLightController.onWorkoutStopped(this)` synchronously before unbinding `BANALService`.
* In `onDestroy()`, remove `RadarLightController.onWorkoutStopped(this)` to prevent double-firing and pause-extinguish races.

### Step 4: UI Polish & Guidelines Compliance (`RadarSettingsSection.kt` & `RadarLightQuickSwitchSheet.kt`)
* In `RadarSettingsSection.kt`, replace the 5-chip `FlowRow` with a compliant selector; persist to DataStore only without calling `setLightMode()` when idle.
* In `RadarLightQuickSwitchSheet.kt`, replace raw `Color.Gray` with `MaterialTheme.colorScheme.outline`.
* Add `@Preview` composables for Light, Dark, and StVZO variants in `RadarLightQuickSwitchSheet.kt`.

### Step 5: Test Suite Expansion & Verification
* Expand `RadarLightControllerTest.kt` with tests for gated state mutation, synchronous lifecycle hooks, and StVZO pruning.
* Expand `BTLERadarDeviceTest.kt` with tests for `0x2A24` Model Number detection and reconnect mode sync.
* Expand `TrackerServiceTest.kt` verifying pause preservation and synchronous stop dispatch.
* Verify 100% translation parity via `TranslationParityTest.kt`.

---

## 6. Verification & Rollback Plan
* **Verification Command**:
  `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.radar.*"`
* **Rollback Plan**: `git checkout feature/ATT-6707` to clean state.
