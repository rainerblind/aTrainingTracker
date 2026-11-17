# Stage 1 Analysis: ATT-8455 - Preserve `RadarState.isAutoMode` Across Telemetry Frames & Simulator

**Ticket**: [ATT-8455](https://rainerblind.atlassian.net/browse/ATT-8455) | **Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) | **Branch**: `feature/ATT-8455` | **SP**: 2 (Standard, confirmed)

---

## 1. Problem Statement & Independent Peer Review & Verification
PO review (`ATT-7972`, Pixel 10) observed manual tail-light mode selection (`RadarLightQuickSwitchSheet` / short-tap `RadarLightButton`) reverting immediately to `Automatik`.
* **Verified Root Cause (`c422e1b6` / `ATT-7991`)**: `RadarState` (`RadarState.kt:114-124`) added `val isAutoMode: Boolean = true` as the 9th `@JvmOverloads` parameter. `RadarLightController` (`RadarLightController.kt:315-331, 391-392`) sets `isAutoMode = false` in `RadarStateRepository`, while `TrackingTabsScreen.kt:1146, 1207` observes `radarState.isAutoMode` (`EditDeviceDialog.kt:1123` observes `RadarLightController.isAutoMode`). However, drivers/simulator construct fresh `RadarState` snapshots without `isAutoMode`, resetting it to `true` at 4–8 Hz:
  - `BTLERadarDevice.java` (6 sites, 8-arg Java ctor): `L154` (watchdog), `L183` (`startSearching`), `L202` (`notifyStopSearching`), `L248` (`configureServices`), `L490` (`processTargets` 4–8 Hz), `L583` (`disconnectFromGatt` after `unregisterRadarDevice`).
  - `RadarTrafficSimulator.kt`: `generateFrame()` (`L151..367`), `tick()` (`L388-393` `.copy()`), `start()` (`L464-469` `.copy()` 8 Hz), `stop()` (`L503-509` ctor).
  - `ANTBikeRadarDevice.kt` (3 sites missed in ticket hypothesis): `L458` (`publishTargets` 8 Hz), `L517` (`onWatchdogTimeout`), `L633` (`shutDown`).
  - Non-telemetry sites verified clean: `RadarStateRepository.kt` (`L40, L86, L99`), `ANTBikeLightDevice.kt:350` (`.copy()`), `RadarSnoozeController.kt:306` (`.copy()`), `RadarEdgeOverlay.kt` (`@Preview` only).

---

## 2. Cross-State Field Audit & Option Tradeoff (A vs. B vs. Combined)
* **All `RadarState` Fields (`RadarState.kt:114-124`)**: Telemetry (`connectionState`, `overallThreatLevel`, `targets`, `lastUpdateRealtimeMs`, `source`) vs. app/control (`lightMode`, `isLightControlSupported`, `audioSnoozeState`, `isAutoMode`). (`brakeLightActive` / `isLightControlled` do not exist on `RadarState`; `isBrakeActive` / `isLightControlAvailable` live on `RadarLightController`.) `lightMode` and `audioSnoozeState` are already preserved via `currentState` (`REQ-CON-042.3`); only `isAutoMode` was omitted.
* **Option A (`RadarStateRepository` preserves `isAutoMode` in `updateState`)**:
  - *Pro*: Single enforcement funnel. *Con*: Blocking `newState.isAutoMode` blindly breaks `RadarLightController` (`setAutoMode`, `setManualLightMode`, `cycleNextMode`, `onWorkoutStarted`, `reset`) which also call `updateState(current.copy(isAutoMode = ...))`; coupling repository directly to `RadarLightController` risks test-isolation leakage when tests call `RadarStateRepository.reset()` alone.
* **Option B (Callers pass `currentState.isAutoMode`)**:
  - *Pro*: Symmetric with `lightMode`/`audioSnoozeState` (`REQ-CON-042`); zero coupling. *Con*: In multi-source arbitration (`RadarStateRepository.kt:44-76`), `RadarLightController` updates only `sourceStates[activeSource]`, so failover (e.g., BLE -> ANT+) could expose stale `isAutoMode = true` from an inactive source's map entry.
* **Recommendation — Combined (Option B + Targeted Option A)**:
  1. Pass `currentState.isAutoMode` at all 6 `BTLERadarDevice.java`, 3 `ANTBikeRadarDevice.kt`, and 3 `RadarTrafficSimulator.kt` (`tick`, `start`, `stop`) emission sites.
  2. Add targeted cross-source app-state sync / `updateControlState` in `RadarStateRepository.kt` so manual `isAutoMode` (and `audioSnoozeState`) cannot revert during BLE/ANT+/Simulator source transitions or failovers, while keeping `RadarStateRepository.reset()` test-isolated.

---

## 3. Chesterton's Fence Audit (`REQ-PRO-022`) & Side-Effect Analysis (`Rule 68`)
* **Original Requirement ID & Target**: `REQ-CON-031.8` (`ATT-7991`, `c422e1b6`) & `REQ-CON-042.3` (`ATT-7574`, `a3783bd7`) in `docs/requirements/req_tracking_cockpit.md`. (`REQ-RAD-005..007` cover post-workout analytics/Strava and are unaffected.)
* **Historical Origin & Root Reason**: `ATT-7574` established preserving `lightMode` & `audioSnoozeState` via `currentState` before simulator preemption; `ATT-7991` later appended `isAutoMode = true` to `RadarState` without updating Java/Kotlin driver constructors.
* **Preservation of Core Invariants**: `REQ-CON-041` source priority (`BLE > ANT_PLUS > SIMULATOR`), `REQ-CON-042.2` pre-preemption state sampling in `BTLERadarDevice.processTargets`, `SmartRadarLightEngine` suspension on manual override, and `EditDeviceDialog`/`TrackingTabsScreen` UI parity remain intact.
* **Side-Effect & Cross-Caller Impact**: Audited `RadarAlertManager.kt`, `SmartRadarLightEngine.kt`, `RadarSnoozeController.kt`, `ANTBikeLightDevice.kt`, `RadarSettingsSection.kt`, `TrackingTabsScreen.kt`. Preserving `isAutoMode` fixes `RadarLightButton` (`AUTO` badge) and `RadarLightQuickSwitchSheet` without affecting `RadarAlertManager` or `SmartRadarLightEngine` (which checks `isManualOverridden`).

---

## 4. Change-Request Impact Analysis (`REQ-PRO-064` / ASPICE SUP.10)
* **Affected Requirements**: `REQ-CON-031.8` (live `isAutoMode` reactive fidelity), `REQ-CON-042.3` (application state preservation across BLE/ANT+/Simulator frames) in `docs/requirements/req_tracking_cockpit.md`.
* **Affected Architecture Elements**: `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `RadarTrafficSimulator.kt`, `RadarStateRepository.kt`, `RadarLightController.kt` (if using dedicated control-state helper).
* **Verification Impact & Test Surface**: `BTLERadarDeviceTest.kt`, `ANTBikeRadarDeviceTest.kt`, `RadarTrafficSimulatorTest.kt`, `RadarStateRepositoryTest.kt`, `RadarLightControllerTest.kt`.
* **Risk Assessment & Invariants**: **LOW** risk. Zero SQLite schema changes; zero UI layout changes; thread-safe under `RadarStateRepository.lock`; `ANT-Android-SDKs` untouched.
