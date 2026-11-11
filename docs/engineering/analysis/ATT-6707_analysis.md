# Stage 1 Analysis: ATT-6707 - Fix RadarLightController pause/stop lifecycle & MockK guard, detect StVZO hardware in BTLERadarDevice, and gate optimistic state updates

**Ticket**: [ATT-6707](https://rainerblind.atlassian.net/browse/ATT-6707)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6707`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Sprint `2026-46.1` audit of `ATT-5102` identified three critical defects in BLE radar tail light mode control:
1. **StVZO Hardware Detection Hardcoded & Missing Sync**: `BTLERadarDevice.configureServices()` passes literal `isStvzo = false` to `RadarLightController.registerRadarDevice()`, never querying Device Information `0x2A24` (Model Number). Consequently, StVZO flashing-mode pruning and legal disclaimers never activate. In addition, `BTLERadarDevice` neither reads `6a4e3202` on connect to reflect physical light state nor restores light mode after transient mid-ride BLE drops.
2. **Workout Pause Extinguishes Safety Light & Stop Teardown Race**: `TrackerService.onDestroy()` unconditionally calls `onWorkoutStopped()`, extinguishing the rear safety light when paused at intersections, while `RESUME_AUTOMATICALLY` does not restore it. On workout stop, `onWorkoutStopped` fires twice and launches unstructured `CoroutineScope(Dispatchers.IO)` reading DataStore, racing with `TrackerService` unbinding `BANALService` (dropping the `OFF` write). `context.filesDir` checks violate Coding Standards §5.5 and crash MockK tests.
3. **Optimistic State Mutation & UI Anti-Patterns**: `RadarLightController.setLightMode()` mutates `_currentMode` and `RadarStateRepository` before verifying command dispatch. `RadarSettingsSection` uses an anti-pattern 5-chip `FlowRow` for default mode (violating Design Guidelines §1.3/§1.4 for $> 4$ options) and dispatches live GATT writes when changing settings outside workouts. `RadarLightQuickSwitchSheet` uses raw `Color.Gray` and lacks `@Preview`s.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (StVZO Hardcoding & Telemetry Sync Gap)**: `BTLERadarDevice.java` lines 170 & 174 pass literal `false` for `isStvzo`. The device never queues a read for `0x2A24` on service `0x180A`. `6a4e3202` is never read or notified, so initial hardware state is unknown, and reconnects do not re-apply the active workout mode.
* **RCA 2 (Service Lifecycle Misattribution & Unstructured Coroutine Race)**: `TrackerService.onDestroy()` executes on pause and service restart, mistakenly triggering `onWorkoutStopped()`. `onStartCommand()` only checks `START_NORMAL || RESUME_BY_USER`, omitting `RESUME_AUTOMATICALLY`. In `RadarLightController.kt`, `onWorkoutStopped()` spawns unmanaged `CoroutineScope(Dispatchers.IO)` to read DataStore while `TrackerService` synchronously tears down the peripheral. `resolveSafeDataStoreContext()` queries `context.filesDir`, throwing `MockKException` in unit tests.
* **RCA 3 (Ungated State Mutation & UI Guideline Non-Conformance)**: In `RadarLightController.kt` lines 105–107, `_currentMode` and `RadarStateRepository` update unconditionally before checking `dispatched`. In `RadarSettingsSection.kt` line 697, 5 `FilterChip`s in a `FlowRow` violate Design Guidelines §1.3/§1.4, and the click handler invokes `RadarLightController.setLightMode(mode)` even when idle. In `RadarLightQuickSwitchSheet.kt` line 125, raw `Color.Gray` is used instead of `MaterialTheme.colorScheme.outline`.
* **Independent Peer Review & Verification**: Live sprint codebase audit confirms all file citations, line numbers, and architectural flaws in `BTLERadarDevice.java`, `RadarLightController.kt`, `TrackerService.java`, `RadarSettingsSection.kt`, and `RadarLightQuickSwitchSheet.kt`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Inspect `0x2A24` Model Number string in `BTLERadarDevice` to detect StVZO units (`010-03180-10`, `RTL516`, etc.); read `6a4e3202` on connect and re-apply active workout light mode upon mid-ride reconnect.
  2. Refactor `RadarLightController` to use synchronously cached preferences (zero `context.filesDir`, zero unstructured `CoroutineScope(Dispatchers.IO)` per Coding Standards §5.5); ensure Pause never extinguishes tail light, `RESUME_AUTOMATICALLY` restores light, and `OFF` is dispatched strictly on explicit workout stop (`handleActionStop`) before `BANALService` unbinds.
  3. Update `_currentMode` and `RadarStateRepository` only when `dispatched == true` (or confirmed by GATT).
  4. Replace 5-chip `FlowRow` in `RadarSettingsSection` with a compliant selector that only updates default preferences when idle.
  5. Replace `Color.Gray` with `MaterialTheme.colorScheme.outline` and add Light/Dark/StVZO `@Preview`s in `RadarLightQuickSwitchSheet`.
* **Out-of-Scope Non-Goals**:
  * Changing radar threat telemetry packet parsing.
  * Adding database migrations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-031` (`REQ-CON-031.1`, `REQ-CON-031.2`, `REQ-CON-031.4`, `REQ-CON-031.5`).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-46.1 via `ATT-5102` (commit `3532c253`).
* **Root Reason for Existing Formulation**: Rapid initial implementation hardcoded `isStvzo = false` pending hardware arrival; lifecycle hooks relied on `onDestroy` without differentiating Pause from Finish; settings selector reused live switch callback.
* **Preservation of Core Invariants**: StVZO legal safety preserved; non-blocking GATT FIFO queueing maintained; 100% 9-language translation parity intact.

---

## 5. Architectural Strategy & High-Level Solution
* **Hardware Detection & Reconnect Sync (`BTLERadarDevice.java`)**:
  * Discover Device Information service `0x180A` and enqueue read of `0x2A24` (Model Number). If string matches StVZO patterns, set `mIsStvzo = true` and update `RadarLightController`.
  * Read `6a4e3202` on connect to sync physical light state; re-dispatch active workout light mode upon mid-ride reconnect.
* **Synchronously Cached Preferences (`RadarLightController.kt`)**:
  * Cache `radarAutoLightControl` and `radarLightDefaultMode` in `RadarLightController` via DataStore flow observation or synchronous init. Lifecycle hooks execute synchronously without async coroutines or `context.filesDir`.
* **Lifecycle Boundary Hardening (`TrackerService.java`)**:
  * Dispatch `OFF` strictly in `handleActionStop()` before unbinding `BANALService`. Remove `onWorkoutStopped` from `onDestroy()`.
  * Include `RESUME_AUTOMATICALLY` in `onStartCommand()` light check.
* **Gated State Mutation & UI Compliance**:
  * `setLightMode()` updates `_currentMode` and `RadarStateRepository` only if `dispatched == true`.
  * Replace `FlowRow` in `RadarSettingsSection` with a compliant selector that updates only DataStore preferences when idle.
  * In `RadarLightQuickSwitchSheet`, use `MaterialTheme.colorScheme.outline` and add `@Preview`s.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Tail light remains lit during workout pause.
  2. Flashing modes strictly disabled on StVZO hardware.
  3. Single squashed commit on `sprint/2026-46.2` under `/tmp/att_build_merge.lock`.
* **Risk Rating**: **MEDIUM** (Touches BLE GATT service configuration, service lifecycle hooks, and Compose UI; thoroughly verifiable via JVM unit and Robolectric tests).
