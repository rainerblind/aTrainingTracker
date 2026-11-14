# Stage 5: Walkthrough & Verification - ATT-7659: Implement Developer Menu in Navigation Drawer with Bike Radar V1-only protocol toggle

**Ticket**: [ATT-7659](https://issue-tracker/ATT-7659)  
**Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-SET-087.2`, `REQ-CON-030.8`  
**Test Mapping**: `TST-SET-087.3`, `TST-SET-087.4`, `TST-CON-030.9`  
**Branch**: `feat/ATT-7659`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the construction and verification of **ATT-7659**:
1. **Developer Menu Navigation & Gating (`REQ-SET-087.2`)**:
   - Added `drawer_developer_menu` in `res/values/ids.xml` and mapped route in `NavRoutes.kt` (`DEVELOPER_MENU`, `DEVELOPER_MENU_PATTERN`, `developerMenu()`).
   - Extended `AppNavigationDrawer.kt:createDrawerGroups(isDebug = BuildConfig.DEBUG)` to conditionally display `Developer Menu` (`R.drawable.ic_settings_24`) in the Info & Support group strictly when `isDebug == true`.
   - Mounted `NavRoutes.DEVELOPER_MENU_PATTERN` in `ATrainingTrackerApp.kt` route graph gated by `BuildConfig.DEBUG`.
2. **Dedicated Developer Menu Screen & Supporter Debug Extraction (`REQ-SET-087.2`)**:
   - Created `DeveloperMenuScreen.kt` featuring a top app bar, hosting `SupporterDebugSection` (tier overrides, jubilee celebration dialog preview, N+1 garage previews and flag reset) and a new Bike Radar debugging section.
   - Completely removed `SupporterDebugSection` from `AdvancedTuningScreen.kt`, eliminating debug pollution from production tuning while keeping the Radar Traffic Simulator accessible in `RadarSettingsSection.kt`.
3. **Bike Radar BLE V1-Only Protocol Override (`REQ-CON-030.8`)**:
   - Added `KEY_RADAR_V1_ONLY_OVERRIDE` preference in `SettingsDataStore.kt` with reactive Flow, suspend setter, and synchronous snapshot getter.
   - Exposed `@JvmStatic SettingsDataStoreJavaHelper.isRadarV1OnlyOverrideEnabled(context)` returning strictly `false` when `!BuildConfig.DEBUG`.
   - Wired override check in `BTLERadarDevice.java:configureServices()` to bypass V2 characteristic discovery and immediately invoke `fallbackToV1(gatt, btGattService)` when active.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Test Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-087.2` | `TST-SET-087.3` | Drawer item visibility in debug and omission in release (`AppNavigationDrawerTest.kt`) | **PASSED** | `Verified` |
| `REQ-SET-087.2` | `TST-SET-087.4` | Clean removal of debug controls from AdvancedTuningScreen (`AdvancedTuningScreenTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-030.8` | `TST-CON-030.9` | Force BLE V1 fallback and standard V2 when disabled (`BTLERadarDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-PRO-001` | Living requirement governance audit (`verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Regression Test Suite
* **Command**: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.*" --max-workers=2`
* **Result**: `BUILD SUCCESSFUL` (416 tests completed, 0 failures, 0 errors, 0 skipped).

### Living Requirement Governance Audit
* **Command**: `python3 tools/verify_requirement_governance.py --sync-indexes`
* **Result**: `PASS` (Master index tables successfully synchronized with 100% parity across living doc shards).

---

## 4. Invariant & Governance Verification

1. **Release Build Security Invariant**: In release builds (`BuildConfig.DEBUG == false`), the developer menu drawer destination, routing, and radar protocol override are absent or inert, strictly preventing debug leakage to production athletes.
2. **Radar Connection Safety**: Standard Garmin Varia V2 handshake choreography (Battery CCCD $\to$ MTU 247 $\to$ V2 CCCD $\to$ 1500 ms silence fallback) remains intact when the debug toggle is off.
