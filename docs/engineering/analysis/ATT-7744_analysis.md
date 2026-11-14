# Stage 1 Analysis: ATT-7744 - Hide Cockpit radar overlay bar while searching for unconnected BLE radar sensor

**Ticket**: [ATT-7744](https://issue-tracker/ATT-7744)  
**Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Branch**: `feat/ATT-7744`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
When an athlete enters the Cockpit (`ScreenMode.TRACKING`), the `RadarEdgeOverlay` bar is displayed if `radarState.connectionState != RadarConnectionState.DISCONNECTED`. In `BTLERadarDevice.java:startSearching()`, initiating BLE search immediately updates `RadarStateRepository` to `RadarConnectionState.CONNECTING`. Consequently, the radar rail appears in the Cockpit even when the radar is turned off, charging at home, or unpowered. Furthermore, if a connected radar drops mid-ride (`DISCONNECTED_ERROR`), reconnect search attempts overwrite `DISCONNECTED_ERROR` with `CONNECTING`, erroneously clearing the fail-safe amber warning bar.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Broad Visibility Condition in Cockpit UI)**: `TrackingTabsScreen.kt:1100-1103` checks `radarState.connectionState != RadarConnectionState.DISCONNECTED`. Because `CONNECTING != DISCONNECTED`, the overlay renders immediately upon search initiation.
* **RCA 2 (State Overwrite on Reconnect in BTLERadarDevice)**: `BTLERadarDevice.java:startSearching()` unconditionally overwrites the active `RadarState` with `RadarConnectionState.CONNECTING`, even when the device was already in `DISCONNECTED_ERROR` (watchdog timeout), clobbering the amber warning badge.
* **Independent Peer Review & Verification**:
  * Inspected `TrackingTabsScreen.kt:1100`: Verified that gating overlay rendering on `radarState.connectionState == RadarConnectionState.CONNECTED || radarState.connectionState == RadarConnectionState.DISCONNECTED_ERROR || RadarTrafficSimulator.isSimulating` cleanly hides the rail during search while keeping mid-ride disconnect warnings and simulator previews active.
  * Inspected `BTLERadarDevice.java:177`: Verified that checking `currentState.getConnectionState() != RadarConnectionState.DISCONNECTED_ERROR` before setting `CONNECTING` preserves fail-safe state across reconnect loops.
  * Inspected `BTLERadarDevice.java:574` and `notifyStopSearching()`: Verified that on initial search timeout, state reverts to `DISCONNECTED` if not in `DISCONNECTED_ERROR`.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * Update `TrackingTabsScreen.kt` visibility logic so `RadarEdgeOverlay` displays only when `CONNECTED`, `DISCONNECTED_ERROR`, or `isSimulating`.
  * Update `BTLERadarDevice.java` to prevent overwriting `DISCONNECTED_ERROR` during reconnect `startSearching()`.
  * Ensure initial search failure cleanly transitions to `DISCONNECTED` in `BTLERadarDevice.java`.
* **Out-of-Scope Non-Goals**:
  * Header sensor status bar icon modifications (`ATT-7544`).
  * Changing ANT+ radar behavior (which already aligns with this lifecycle).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-062.4` (*Fail-Safe Disconnect, Error State & 1-Tap Reconnect*), targeting `TrackingTabsScreen.kt` and `BTLERadarDevice.java`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-5085` (commit `671c4c1a`); refined in `ATT-6703` and `ATT-7537`.
* **Root Reason for Existing Formulation**: `!= DISCONNECTED` was an initial heuristic assuming that any non-idle state warranted cockpit representation, overlooking the user experience during initial search when no radar is attached.
* **Preservation of Core Invariants**: Mid-ride disconnect fail-safe amber indication (`DISCONNECTED_ERROR`) is strictly maintained and protected against reconnect clobbering; 1-tap reconnect action remains functional.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-TRK-062.4` in `docs/requirements/req_tracking_cockpit.md`.
* **Affected Architecture Elements**: `TrackingTabsScreen.kt`, `BTLERadarDevice.java`.
* **Verification Impact & Test Surface**: Unit tests in `TrackingTabsScreenTest.kt` (or UI tests) and `BTLERadarDeviceTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Refines UI visibility gating and state preservation without altering BLE communication or packet decoding.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero false green indication; fail-safe amber warning preserved on connection loss; radar simulator functionality unchanged.
* **Risk Rating**: `LOW` — Localized visibility logic and lifecycle state guard.
