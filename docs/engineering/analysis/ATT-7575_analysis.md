# Stage 1 Analysis: ATT-7575 - Arbitrate concurrent BLE and ANT+ radar sources in RadarStateRepository

**Ticket**: [ATT-7575](https://rainerblind.atlassian.net/browse/ATT-7575)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7575`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Radar technical review finding M2 (Major):
1. **Unarbitrated State Clashing (M2)**: Garmin Varia radars (RTL515, RearVue 820) broadcast simultaneously over BLE and ANT+. `RadarStateRepository` maintains a single global `_radarState` without source tagging or transport arbitration. When both drivers are paired, each overwrites the other's targets at 4–8 Hz, causing visual jitter in `RadarEdgeOverlay` and spurious acoustic alerts in `RadarAlertManager`.
2. **Watchdog Collision & Premature Disconnect (M2)**: If either driver suffers packet loss or starvation timeout while the other link is streaming normally, its watchdog publishes `DISCONNECTED_ERROR` with empty targets to `RadarStateRepository`, destroying the active session and flashing false error states.
3. **Global State Coupling in Driver Status (M2)**: `ANTBikeRadarDevice.isReceivingData()` reads the shared global state (`RadarStateRepository.getState().connectionState == CONNECTED`), falsely reporting data reception when only BLE is connected.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Codebase Verification**:
  * Inspected `RadarStateRepository.kt:30-46`: `_radarState` is updated unconditionally via `updateState(newState)`. No metadata identifies whether an update originated from `BTLERadarDevice`, `ANTBikeRadarDevice`, or `RadarTrafficSimulator`.
  * Inspected `BTLERadarDevice.java:472` and `ANTBikeRadarDevice.kt:457`: Both drivers emit `RadarState` directly to the same singleton repository without arbitration.
  * Inspected `BTLERadarDevice.java:553` and `ANTBikeRadarDevice.kt:514`: Watchdogs in both drivers emit `RadarConnectionState.DISCONNECTED_ERROR` to `RadarStateRepository`, blindly clobbering an active counterpart link.
  * Inspected `ANTBikeRadarDevice.kt:224-226`: `isReceivingData()` queries global repository connection state rather than driver-specific packet telemetry.
* **Independent Peer Review & Verification**:
  * Confirmed that tagging `RadarState` with `RadarSource` (`NONE`, `SIMULATOR`, `ANT_PLUS`, `BLE`) and enforcing deterministic priority (`BLE` > `ANT_PLUS` > `SIMULATOR`) with fallback hysteresis cleanly prevents target clobbering while ensuring seamless failover if the primary link disconnects.
  * Scoping `isReceivingData()` to driver-specific packet reception isolates peripheral connectivity checks.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Introduce `enum class RadarSource { NONE, SIMULATOR, ANT_PLUS, BLE }`.
  * Add `source: RadarSource` property to `RadarState`.
  * Implement source arbitration in `RadarStateRepository` prioritizing `BLE` over `ANT_PLUS` and `ANT_PLUS` over `SIMULATOR`, ignoring secondary target updates and secondary `DISCONNECTED_ERROR` while the primary link streams.
  * Provide per-source state inspection in `RadarStateRepository.getState(source: RadarSource)`.
  * Scope `ANTBikeRadarDevice.isReceivingData()` to ANT+ specific reception.
* **Out-of-Scope Non-Goals**:
  * Merging simultaneous 2D vectors from multiple disparate radar physical devices.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-028.4` (Reactive Repository) and `REQ-CON-032.3` (Target Demuxing) in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: Sprints 2026-46.3 (`ATT-5083`, `ATT-5084`).
* **Root Reason for Existing Formulation**: Dual-protocol concurrent pairing was not considered when `RadarStateRepository` was introduced as a single `MutableStateFlow`.
* **Preservation of Core Invariants**: High-rate (4–8 Hz) reactive stream to downstream UI and audio engines; fail-safe timeout transitions; 100% test pass rate.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-041` (Concurrent Radar Source Arbitration & Multi-Transport Decoupling).
* **Affected Architecture Elements**: `RadarState.kt`, `RadarStateRepository.kt`, `ANTBikeRadarDevice.kt`, `BTLERadarDevice.java`, `RadarTrafficSimulator.kt`.
* **Verification Impact & Test Surface**: `RadarStateRepositoryTest.kt`, `ANTBikeRadarDeviceTest.kt`, `BTLERadarDeviceTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Thread-safe internal map in `RadarStateRepository` preserves existing public API contracts and downstream flows.

---

## 6. Architectural Strategy & High-Level Solution
1. **RadarSource Classification**: Define `RadarSource` enum with explicit priority ordering (`BLE` = 3, `ANT_PLUS` = 2, `SIMULATOR` = 1, `NONE` = 0).
2. **State Repository Arbitration**:
   * Maintain `sourceStates: ConcurrentHashMap<RadarSource, RadarState>`.
   * When `updateState(newState)` is invoked:
     - Record in `sourceStates[newState.source]`.
     - Allow update if `newState.source == activeSource` or `newState.source.priority > activeSource.priority` or `activeSource` is disconnected / timed out.
     - Prevent secondary links from publishing `DISCONNECTED_ERROR` or target lists while a higher-priority link is `CONNECTED`.
3. **Driver Scoping**:
   * Set `source = RadarSource.BLE` in `BTLERadarDevice.java`.
   * Set `source = RadarSource.ANT_PLUS` in `ANTBikeRadarDevice.kt`.
   * Set `source = RadarSource.SIMULATOR` in `RadarTrafficSimulator.kt`.
   * In `ANTBikeRadarDevice.isReceivingData()`, query `RadarStateRepository.getState(RadarSource.ANT_PLUS)`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Priority fidelity: Higher-fidelity BLE stream takes precedence over ANT+.
  2. Failover continuity: If the primary stream drops, secondary stream assumes control without intermediate disconnect flash.
  3. Isolated driver telemetry: A driver's status reflects its own physical link.
* **Risk Rating**: **LOW** — Pure domain repository arbitration with zero database migrations or UI changes.
