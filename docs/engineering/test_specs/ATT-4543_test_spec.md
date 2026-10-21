# Stage 2: Requirement & Test Specification - ATT-4543: [Verbesserung] Update default Cycling FTP to 270 W and align default PWR_BIKE Coggan 7-zone cutoffs (149, 203, 243, 284, 324, 405 W)

**Ticket**: [ATT-4543](https://rainerblind.atlassian.net/browse/ATT-4543) | **Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4543`  
**Requirement Mapping**: `REQ-UI-426` (amending `REQ-UI-370`, `REQ-UI-373`) | **Test Spec ID**: `TST-UI-391`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Traceability Matrix

| Requirement | Description | Test Case | Target Artifact | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-426.1` | Cycling FTP baseline 270 W & Coggan cutoffs [149, 203, 243, 284, 324, 405] | `TST-UI-391.1`, `TST-UI-391.2` | `SettingsDataStore.kt`, `SettingsSnapshot` | Planned |
| `REQ-UI-426.2` | Zone Calculator Dialog default FTP input "270" | `TST-UI-391.3` | `ZoneCalculatorDialog.kt` | Planned |
| `REQ-UI-426.3` | TrackingViewModel HUD fallback cutoffs alignment | `TST-UI-391.4` | `TrackingViewModel.kt` | Planned |
| `REQ-UI-426.4` | System invariants (custom profiles & DB immutability) | `TST-UI-391.5`, `TST-UI-391.6` | `SettingsDataStoreBaselinesTest.kt` | Planned |

---

## 2. Formal Requirement Specifications

* **Baseline Constants & DataStore Defaults (`REQ-UI-426.1`)**:
  * In `SettingsDataStore.kt`, `DEFAULT_CYCLING_FTP` SHALL be `270`.
  * `SettingsSnapshot.DEFAULT.pwrBikeZoneMax`, `getDefaultValue(ZoneType.PWR_BIKE, zone)`, and `resetToDefaults(ZoneType.PWR_BIKE)` SHALL yield `[149, 203, 243, 284, 324, 405]`.
  * `calculateProportionalPowerDefaults()` unconfigured Z5/Z6 fallback when Z4 is missing/zero SHALL evaluate to `Pair(324, 405)`.
* **UI Calculator Default State (`REQ-UI-426.2`)**:
  * In `ZoneCalculatorDialog.kt`, `ZoneCalculatorUiState.ftpInput` fallback default SHALL be `"270"`.
* **HUD Telemetry Fallbacks (`REQ-UI-426.3`)**:
  * In `TrackingViewModel.kt`, `zoneCutoffsMap[PWR_BIKE]` and fallback lists in `calculateZoneColor()` / `calculateZoneGaugeState()` SHALL be `[149, 203, 243, 284, 324, 405]`.
* **System Invariants (`REQ-UI-426.4`)**:
  * Customized user profiles (`isCustomized == true`) MUST NOT be altered or reset.
  * Historical workout summaries and samples in SQLite MUST remain strictly immutable.
  * Strict integer monotonicity ($0 < 149 < 203 < 243 < 284 < 324 < 405 < 2500\text{ W}$) MUST be preserved.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-426` (and `REQ-UI-370`, `REQ-UI-373`). Targets `SettingsDataStore.kt`, `ZoneCalculatorDialog.kt`, `TrackingViewModel.kt`.
* **Historical Origin & Commit Trace**: `ATT-4521` (`7051a307ddf7`), `ATT-3184` (`25828a66`), and `ATT-3188` (`889fcebf`).
* **Root Reason for Existing Formulation**: ATT-4521 set 240 W as an initial recreational baseline; athletic calibration indicates 270 W is the optimal target baseline for competitive power meter cyclists.
* **Preservation of Core Invariants**: Customized user profiles retain stored values; strict integer monotonicity ($Z_n \ge Z_{n-1} + 1$) and 2500 W ceiling remain guaranteed; formulas in `ZoneCalculationMath` remain pure and unchanged.

---

## 3. Formal Test Case Specifications (Given / When / Then)

* **`TST-UI-391.1` — DataStore Constants & Snapshot Baselines**:
  * *Given* default constants in `SettingsDataStore`, *When* evaluated, *Then* `DEFAULT_CYCLING_FTP == 270`, and `SettingsSnapshot.DEFAULT.pwrBikeZoneMax == listOf(149, 203, 243, 284, 324, 405)`.
* **`TST-UI-391.2` — DataStore `getDefaultValue()` & `resetToDefaults()`**:
  * *Given* a test DataStore instance, *When* `getDefaultValue()` or `resetToDefaults()` is called for `PWR_BIKE`, *Then* cutoffs resolve to `[149, 203, 243, 284, 324, 405]` in monotonic order.
* **`TST-UI-391.3` — ZoneCalculatorDialog State Holder Fallback**:
  * *Given* `ZoneCalculatorStateHolder` instantiated with null stored FTP, *When* initialized, *Then* `ftpInput` is `"270"` and `calculatedZones` resolves to `[149, 203, 243, 284, 324, 405]`.
* **`TST-UI-391.4` — TrackingViewModel Fallback Cutoffs**:
  * *Given* `TrackingViewModel` initialized, *When* power telemetry is mapped to zone colors or gauge states, *Then* default `zoneCutoffsMap[PWR_BIKE]` evaluates to `[149, 203, 243, 284, 324, 405]`.

---

## 4. Edge Case & Regression Matrix

| Edge Case / Invariant | Precondition | Trigger / Input | Expected Result |
| :--- | :--- | :--- | :--- |
| **Custom Profile Protection** | User configured custom Z1–Z6 power cutoffs (`isCustomized = true`) | App starts or DataStore preferences read | Custom cutoffs strictly preserved; new 270 W defaults do NOT overwrite stored values. |
| **Calculator Reset** | Dialog opened with custom values | User taps "Standard wiederherstellen" | Inputs reset to 270 W (and 185/55 bpm) and zones recalculate to [149, 203, 243, 284, 324, 405]. |
| **Strict Monotonicity & Ceiling** | Default power cutoffs loaded | `ZoneValidator.validatePowerZoneStack()` executed | Validates $Z_1 < Z_2 < Z_3 < Z_4 < Z_5 < Z_6 < 2500\text{ W}$ without throwing validation errors. |

---

## 5. Localization & Resource Parity Matrix

* **Resource Impact**: Zero new string keys required. Standard Material 3 UI labels (`default_label`, `cancel`, `apply`, etc.) already exist with 100% parity across all 9 canonical locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* **Regression Test**: Run `TranslationParityTest` to confirm 0 missing entries and 100% format specifier parity.
