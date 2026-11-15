# Stage 5: Walkthrough & Verification - ATT-7991: Unify Radar Tail Light Mode & Auto Persistence

**Ticket**: [ATT-7991](https://rainerblind.atlassian.net/browse/ATT-7991) | **Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Radar*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7991`  
**Requirement Mapping**: `REQ-CON-031` (`REQ-CON-031.3`, `REQ-CON-031.4`, `REQ-CON-031.5`, `REQ-CON-031.8`)  
**Test Mapping**: `TST-CON-031` (`TST-CON-031.3`, `TST-CON-031.4`, `TST-CON-031.5`, `TST-CON-031.9`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-15  

---

## 1. Executive Summary & Verification Overview

ATT-7991 unifies Garmin Varia radar tail light mode persistence and user interfaces across the app:
1. **Unified Persistence & Automatic Migration (`TuningPreferencesDataStore.kt`)**: Persists `radarLightAutoMode: Boolean` (default `true`) and `radarLightDefaultMode: RadarLightMode` (`DAY_FLASH`). Migrates legacy `RadarLightControlMode`: `SMART_AUTOMATION -> true`, `FIXED_WORKOUT_MODE -> legacy default mode`, `MANUAL -> false + OFF`.
2. **Controller Decoupling & Debounced Mode Cycling (`RadarLightController.kt`)**: Exposes dual `isAutoMode` and `currentMode` flows. Implements ~400 ms hardware command debounce for `cycleNextMode()`, updating UI immediately while coalescing radio writes. Preserves StVZO mode pruning (`AUTO -> SOLID -> OFF -> AUTO`).
3. **Workout Lifecycle Preservation (`RadarLightController.kt`)**: Automatically activates persisted choice on cycling workout start; turns light `OFF` and disconnects on stop without wiping user configuration.
4. **UI Consolidation & Parity (`RadarSettingsSection.kt`, `RadarLightQuickSwitchSheet.kt`, `EditDeviceDialog.kt`)**: Retired redundant 3-way mode controls and dropdown from Expert Settings while retaining deceleration brake light toggle (`Design Guidelines §3.2`). Added `Automatik` as first selectable choice with green status dot and active mode subtitle in both live sheets.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-031.5` | `TST-CON-031.5a` | Unit Test (`TuningPreferencesDataStoreRadarLightControlModeTest`) | **PASSED** | `Verified` |
| `REQ-CON-031.8` | `TST-CON-031.9a` | Unit Test (`RadarLightControllerTest.testDebouncedCycleNextMode`) | **PASSED** | `Verified` |
| `REQ-CON-031.4` | `TST-CON-031.4a` | Unit Test (`RadarLightControllerTest.testWorkoutStartStopLifecycle`) | **PASSED** | `Verified` |
| `REQ-CON-031.5` | `TST-CON-031.5b` | Compose UI Test (`RadarSettingsSectionTest`) | **PASSED** | `Verified` |
| `REQ-CON-031.3` | `TST-CON-031.3a` | Visual Inspection & Compose Test (`RadarLightQuickSwitchSheet`) | **PASSED** | `Verified` |
| `REQ-CON-031.7` | `TST-CON-031.7` | Localization Parity (`TranslationParityTest`, 9 locales) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-CON-031.8` | Tier 1 Targeted Regression Suite (248 tests) | **PASSED** | `Verified` |

---

## 3. Visual Verification & On-Device Certification

All UI screens verified on `emulator-5554` and certified via `read_file`:
* **Settings**: ![Radar Settings](img/ATT-7991_radar_settings_section.png) (3-way selector retired, brake toggle preserved).
* **Quick Switch**: ![Quick Switch Sheet](img/ATT-7991_quick_switch_sheet.png) (Automatik first option with active mode subtitle).
* **Device Dialog**: ![Edit Device Dialog](img/ATT-7991_edit_device_dialog.png) (Parity with sheet, Automatik + physical modes).
* **Crash Logcat**: `adb -s emulator-5554 logcat -b crash -d` verified clean (0 crashes).

---

## 4. Invariant & Governance Verification

1. **GATT Queue Safety**: ~400 ms command debouncing prevents BLE queue flooding during rapid tap sequences.
2. **StVZO Compliance**: Non-StVZO flashing modes pruned automatically when `isStvzo == true`.
3. **Clean-Room Test Pass Rate**: 248/248 unit tests passed; `:app:lintDebug` passed with 0 errors.
4. **Living Documentation Synchronized**: `docs/requirements.md` and `docs/tests.md` updated to `Verified`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-031.3` (Quick-Switch Sheet), `REQ-CON-031.4` (Workout Lifecycle), `REQ-CON-031.5` (Settings & Device Controls)
* **Historical Origin & Commit Trace**: Introduced in `ATT-5102` (`c1d27049`), refined in `ATT-6707` (`fe09f672`), `ATT-7545` (`6bfac996`), `ATT-7540`, and `ATT-7538` (`b645651f`).
* **Root Reason for Existing Formulation**: `ATT-7538` added a 3-way Expert Settings mode (`MANUAL`, `FIXED_WORKOUT_MODE`, `SMART_AUTOMATION`) to eliminate boolean race conditions, but isolated `Automatik` in settings, leaving live sheets as one-way manual traps with no return path and coupling workout stop to setting erasure.
* **Preservation of Core Invariants**: StVZO regulatory compliance (`isStvzo` skips flashing modes); non-blocking GATT FIFO queueing with ~400 ms debounce; safety illumination during pause; independent deceleration brake light (`radarBrakeLightEnabled`); backward-compatible preference migration; 100% 9-language translation parity.