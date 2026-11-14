# Stage 1 Analysis: ATT-7659 - Add debug-only Developer Menu with Bike Radar V1-only protocol toggle

**Ticket**: [ATT-7659](https://issue-tracker/ATT-7659)  
**Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Branch**: `feat/ATT-7659`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Currently, diagnostic debug controls (`SupporterDebugSection`: Supporter Tier Override, Jubilee Celebration Dialog preview, and N+1 Garage upgrade previews/flag reset) are embedded at the bottom of user-facing Expert Settings (`AdvancedTuningScreen.kt`). This clutters the settings screen for end users. Additionally, testing a dual V1/V2 BLE radar (such as Garmin Varia RearVue 820) against the legacy V1 protocol (`6a4e3203`) requires physically unbonding or waiting for timeouts, lacking an explicit developer override to force V1-only operation.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Debug Tool Sprawl)**: Diagnostic tools were historically added directly into `AdvancedTuningScreen` gated by `if (BuildConfig.DEBUG)` because a dedicated developer diagnostics destination did not exist in the Navigation Drawer.
* **RCA 2 (BLE Protocol Testability Gap)**: `BTLERadarDevice.configureServices()` unconditionally initiates the V2 GATT handshake whenever `UUID_CHARACTERISTIC_RADAR_V2` (`6a4e3204`) is present on a bonded device, preventing isolated testing of V1 acquisition and legacy fallback behavior.
* **Independent Peer Review & Verification**:
  * Inspected `AdvancedTuningScreen.kt:461` and `SupporterDebugSection.kt`: confirmed supporter/garage/jubilee debug triggers can be cleanly hosted in a new `DeveloperMenuScreen.kt`.
  * Inspected `AppNavigationDrawer.kt:242-250`: confirmed `drawer_developer_menu` can be cleanly appended to the drawer strictly when `BuildConfig.DEBUG == true`.
  * Inspected `BTLERadarDevice.java:265-275`: confirmed that checking a debug preference before V2 handshake allows clean immediate invocation of `fallbackToV1(gatt, btGattService)` without affecting light control services.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * Add `drawer_developer_menu` in `ids.xml`, `NavRoutes.kt`, and `AppNavigationDrawer.kt` strictly visible when `BuildConfig.DEBUG == true`.
  * Create `DeveloperMenuScreen.kt` hosting `SupporterDebugSection`, jubilee/garage test actions, and a new Bike Radar debug section with a "Disable V2 Bike Radar Protocol (Use V1 Only)" toggle.
  * Remove `SupporterDebugSection` from `AdvancedTuningScreen.kt` (while keeping the Radar Traffic Simulator accessible in `RadarSettingsSection.kt`).
  * In `BTLERadarDevice.java`, check `BuildConfig.DEBUG` and the V1-only preference to bypass V2 and invoke `fallbackToV1()` on connection.
* **Out-of-Scope Non-Goals**:
  * Mid-session dynamic GATT renegotiation (setting applies cleanly on next radar connection).
  * Exposing developer options in release builds (`BuildConfig.DEBUG == false`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-087.2` (Debug Tier Override Selector) and `REQ-CON-030.2` (Automatic Legacy Fallback Invariant).
* **Historical Origin & Commit Trace**: `REQ-SET-087` introduced in Sprint `2026-44.1` (`ATT-51`); `REQ-CON-030` introduced in Sprint `2026-46.3` (`ATT-5095`) and refined in `ATT-7542`.
* **Root Reason for Existing Formulation**: `REQ-SET-087.2` placed debug overrides in `AdvancedTuningScreen` before a developer drawer menu existed. `REQ-CON-030.2` specified fallback on failure, but had no manual developer bypass.
* **Preservation of Core Invariants**: Release builds remain 100% free of debug tools; default production radar behavior remains V2-preferring; ProFeatureManager resolution and radar telemetry invariants are strictly preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-SET-087.2` and `REQ-CON-030.2`.
* **Affected Architecture Elements**: `AppNavigationDrawer.kt`, `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `DeveloperMenuScreen.kt`, `AdvancedTuningScreen.kt`, `BTLERadarDevice.java`, `ids.xml`, `strings_settings.xml`.
* **Verification Impact & Test Surface**: Unit tests in `DeveloperMenuNavigationTest.kt`, `AppNavigationDrawerTest.kt`, and `BTLERadarDeviceTest.kt`.
* **Risk Assessment & Invariants**: Low risk. All changes are strictly gated by `BuildConfig.DEBUG`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero debug controls or menu entries visible in release builds (`BuildConfig.DEBUG == false`); radar data pipeline, GATT serialization, and light control remain invariant.
* **Risk Rating**: `LOW` — Additive navigation route and debug-only protocol override with complete unit test coverage.
