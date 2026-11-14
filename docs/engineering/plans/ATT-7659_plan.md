# Stage 3: Implementation Plan - ATT-7659: Implement Developer Menu in Navigation Drawer with Bike Radar V1-only protocol toggle

**Ticket**: [ATT-7659](https://issue-tracker/ATT-7659)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-SET-087.2`, `REQ-CON-030.8`  
**Test Mapping**: `TST-SET-087.3`, `TST-SET-087.4`, `TST-CON-030.9`  
**Branch**: `feat/ATT-7659`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Architecture Overview
Debug and QA controls (Supporter Tier Override, Jubilee dialog preview, Garage prompts preview) currently pollute `AdvancedTuningScreen.kt`. Furthermore, testing legacy Garmin Varia BLE V1 protocol fallback requires an explicit developer override. This plan introduces a dedicated `DeveloperMenuScreen` mounted in `AppNavigationDrawer` strictly when `BuildConfig.DEBUG == true`, extracts supporter debug tools out of `AdvancedTuningScreen`, and wires a persistent `radar_v1_only_override` toggle directly governing `BTLERadarDevice.configureServices()`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-087.2` (*Debug Developer Menu & Supporter Tier Override Isolation*)
  - Gated navigation drawer item (`R.id.drawer_developer_menu`) and screen route exclusively in debug builds (`BuildConfig.DEBUG`).
  - Total removal of debug controls from `AdvancedTuningScreen`.
* **Requirement**: `REQ-CON-030.8` (*Developer BLE V1-Only Protocol Override*)
  - Toggle forcing immediate `fallbackToV1()` in `BTLERadarDevice.java` when enabled in debug builds.
* **Test Mapping**: `TST-SET-087.3`, `TST-SET-087.4` in `AppNavigationDrawerTest.kt` / `AdvancedTuningScreenTest.kt`; `TST-CON-030.9` in `BTLERadarDeviceTest.kt`.

---

## 3. System Invariants & Preserved Behavior
1. **Release Build Security Invariant**: `BuildConfig.DEBUG` strictly gates drawer item generation, routing, and radar override execution; in release builds, overrides are completely absent/ignored.
2. **Radar Connection Safety**: Normal V2 handshake choreography (Battery CCCD $\to$ MTU 247 $\to$ V2 CCCD $\to$ 1500 ms fallback) remains intact when toggle is off.
3. **Database & Schema Invariance**: Zero SQLite migrations; preferences stored via `SettingsDataStore` / SharedPreferences.
4. **Code Lock**: Production code modifications strictly locked until Gate 3 passes.

---

## Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-087.2`, `REQ-CON-030.8` (`AdvancedTuningScreen.kt`, `DeveloperMenuScreen.kt`, `BTLERadarDevice.java`).
* **Historical Origin & Commit Trace**: `REQ-SET-087` introduced in `ATT-5299`/`ATT-5400`; `REQ-CON-030` introduced in `ATT-5095` and refined in `ATT-7542`.
* **Root Reason for Existing Formulation**: Debug overrides were placed in `AdvancedTuningScreen` as a developer shortcut without a separate navigation hierarchy.
* **Preservation of Core Invariants**: Relocating controls to `DeveloperMenuScreen` maintains developer workflow efficiency while ensuring zero debug UI leakage to end users in production.

---

## 4. UI Consistency & Style Guide Compliance (`docs/design_guidelines.md`)
* **Component Selection**: `DeveloperMenuScreen` utilizes standard `TuningToggleItem` and `SingleChoiceSegmentedButtonRow` with `icon = {}` for tier selection.
* **No Broken Windows**: Zero raw unscaled `Switch` or `FilterChip` for mutually exclusive options; action buttons strictly enforce `maxLines = 1, softWrap = false`.
* **Localization Parity**: All new user-facing strings defined across all 9 locales with 100% key and parameter parity.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Identifiers & Navigation Routing)**: Add `drawer_developer_menu` to `res/values/ids.xml`; add `NavRoutes.DEVELOPER_MENU` in `NavRoutes.kt`; add drawer item in `AppNavigationDrawer.kt:createDrawerGroups(isDebug = BuildConfig.DEBUG)` under `Info & Support` or `Developer` section.
2. **Step 2 (Screen Construction & Extraction)**: Create `DeveloperMenuScreen.kt` hosting `SupporterDebugSection` and `BikeRadarDebugSection` (V1-only override). Remove `SupporterDebugSection` from `AdvancedTuningScreen.kt`.
3. **Step 3 (App Host Wiring - Rule 81)**: Mount `NavRoutes.DEVELOPER_MENU` in `ATrainingTrackerApp.kt` route graph.
4. **Step 4 (Radar V1 Fallback Wire)**: In `BTLERadarDevice.java:configureServices()`, check `BuildConfig.DEBUG` and `SettingsDataStore.isRadarV1OnlyOverrideEnabled()`; if true, log warning and invoke `fallbackToV1()` directly.
5. **Step 5 (Unit Tests & Fast Targeted Regression)**: Add tests in `AppNavigationDrawerTest.kt`, `DeveloperMenuScreenTest.kt`, and `BTLERadarDeviceTest.kt`.
   Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.ui.navigation.*"`.
