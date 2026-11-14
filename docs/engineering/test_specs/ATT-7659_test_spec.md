# Stage 2: Requirement & Test Specification - ATT-7659: Implement Developer Menu in Navigation Drawer with Bike Radar V1-only protocol toggle

**Ticket**: [ATT-7659](https://issue-tracker/ATT-7659)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-SET-087.2`, `REQ-CON-030.8`  
**Test Spec ID**: `TST-SET-087.3`, `TST-SET-087.4`, `TST-CON-030.9`  
**Branch**: `feat/ATT-7659`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-SET-087.2, REQ-CON-030.8)

### 1.1 Problem Statement & Rationale
QA and developers need dedicated debugging entry points to force fallback to legacy Garmin Varia V1 BLE protocol (`6a4e3203`) without modifying peripheral firmware or physical hardware. In addition, developer controls (Supporter Tier Overrides, Jubilee preview, Garage dialog previews) currently pollute `AdvancedTuningScreen.kt` and must be extracted into a dedicated `DeveloperMenuScreen.kt` accessible from the Navigation Drawer exclusively in debug builds (`BuildConfig.DEBUG == true`).

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Functional & Non-Functional
- **Verification Method**: Unit / Integration / Review

### 1.3 Functional & Architectural Requirements
1. `AppNavigationDrawer` SHALL conditionally render `Developer Menu` (`R.id.drawer_developer_menu`) strictly when `BuildConfig.DEBUG == true`.
2. In release builds (`BuildConfig.DEBUG == false`), the `Developer Menu` drawer item and its navigation route SHALL be absent or unreachable.
3. `DeveloperMenuScreen` SHALL host:
   - Supporter tier override selector (`[NONE, PIONEER, BRONZE, SILVER, GOLD, DIAMOND]`) and dialog previews, cleanly extracted from `AdvancedTuningScreen.kt`.
   - Bike Radar BLE V1-Only toggle (`isRadarV1OnlyOverrideEnabled`), persisted in DataStore/SharedPreferences.
4. `AdvancedTuningScreen` SHALL NOT display developer or supporter tier overrides.
5. In `BTLERadarDevice.configureServices()`, when `BuildConfig.DEBUG == true` and `isRadarV1OnlyOverrideEnabled` is enabled, the device SHALL immediately invoke `fallbackToV1()` and skip V2 characteristic discovery/handshake. In release builds, this toggle SHALL be ignored and default to `false`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**: *Given* a debug build (`BuildConfig.DEBUG == true`), *When* opening the drawer, *Then* `Developer Menu` is visible and navigates to `DeveloperMenuScreen`.
* **Criterion 2**: *Given* a release build (`BuildConfig.DEBUG == false`), *When* drawer items are generated, *Then* `Developer Menu` is excluded.
* **Criterion 3**: *Given* `AdvancedTuningScreen`, *When* rendered in debug or release, *Then* supporter tier overrides are not present.
* **Criterion 4**: *Given* `isRadarV1OnlyOverrideEnabled` is true in a debug build, *When* `BTLERadarDevice.configureServices()` executes, *Then* `fallbackToV1()` is called immediately and V2 is bypassed.

### 1.5 System Invariants
Zero security bypass in release builds; zero schema migrations; 100% backward compatibility for radar connection lifecycle.

---

## Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-087.2`, `REQ-CON-030.8` (`AdvancedTuningScreen.kt`, `DeveloperMenuScreen.kt`, `BTLERadarDevice.java`).
* **Historical Origin & Commit Trace**: `REQ-SET-087` introduced in `ATT-5299`/`ATT-5400`; `REQ-CON-030` introduced in `ATT-5095` and refined in `ATT-7542`.
* **Root Reason for Existing Formulation**: Debug overrides were initially embedded directly in `AdvancedTuningScreen` as an expedient developer shortcut, mixing production user tuning with debug-only toggles.
* **Preservation of Core Invariants**: Extracting developer tools to `DeveloperMenuScreen` preserves tier override and dialog preview capabilities for development while guaranteeing strict release-build omission (`BuildConfig.DEBUG`), ensuring zero leak of developer privileges to end users.

---

## 2. Test Specification (TST-SET-087, TST-CON-030)

### Test Case 1: Developer Menu Drawer Visibility & Navigation (`TST-SET-087.3`)
* **Scope**: Unit Test in `AppNavigationDrawerTest.kt` / `NavRoutesTest.kt`
* **Preconditions**: Navigation drawer initialized.
* **Action**: Assert `drawer_developer_menu` is present when `isDebug = true`, absent when `isDebug = false`.

### Test Case 2: Clean Advanced Tuning Isolation (`TST-SET-087.4`)
* **Scope**: Unit Test in `AdvancedTuningScreenTest.kt`
* **Preconditions**: `AdvancedTuningScreen` instantiated.
* **Action**: Assert `AdvancedTuningScreen` does not reference or render `SupporterDebugSection`.

### Test Case 3: BLE Radar V1-Only Protocol Override (`TST-CON-030.9`)
* **Scope**: Unit Test in `BTLERadarDeviceTest.kt`
* **Preconditions**: `BTLERadarDevice` connected to simulated Varia radar.
* **Action**: Mock `isRadarV1OnlyOverrideEnabled = true` in debug $\rightarrow$ assert `fallbackToV1()` invoked directly; when false $\rightarrow$ standard V2 discovery.

### Test Case 4: Fast Targeted Regression
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.*"`

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-SET-087.2` | Dedicated Developer Menu drawer entry gated by `BuildConfig.DEBUG` | `TST-SET-087.3` | Unit Test |
| `REQ-SET-087.2` | Clean removal of debug controls from `AdvancedTuningScreen` | `TST-SET-087.4` | Unit Test |
| `REQ-CON-030.8` | Debug toggle forcing legacy BLE V1 protocol fallback | `TST-CON-030.9` | Unit Test |
| `REQ-SET-087.1` | Preserved Supporter Tier override resolution logic | `TST-SET-087.1` | Unit Test |
