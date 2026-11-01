# Stage 2: Requirement & Test Specification - ATT-4841: [Feature] Flag within sensor field config for showing hr zone, power zone bar, balance, ...

**Ticket**: [ATT-4841](https://rainerblind.atlassian.net/browse/ATT-4841)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-44.1`  
**Requirement Mapping**: `REQ-TRK-046`  
**Test Mapping**: `TST-TRK-046`  
**Author**: AI Agent 2 (Test Architect)  
**Date**: 2026-11-01  

---

## 1. Traceability Matrix

| Requirement Clause | Test Spec ID | Verification Method | Target Component |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-046.1` (Schema v16 & Model) | `TST-TRK-046.1` | Unit Test | `TrackingViewsDatabaseManagerTest` |
| `REQ-TRK-046.2` (Edit Dialog UI Constraint) | `TST-TRK-046.2` | Unit Test | `EditSensorFieldViewModelTest` |
| `REQ-TRK-046.3` (Cockpit Conditional Rendering)| `TST-TRK-046.3` | Unit Test & On-Device | `SensorFieldView.kt`, `TrackingViewModel` |
| `REQ-TRK-046.4` (Global Gauge Removal) | `TST-TRK-046.4` | Audit & Compilation | `ZonesSettingsActivity.kt` |
| `REQ-TRK-046.5` (9-Language Parity) | `TST-TRK-046.5` | Unit Test | `TranslationParityTest` |

---

## 2. Test Specifications (`TST-TRK-046`)

### TST-TRK-046.1: Database Schema v16 Migration & Backfill
- **Setup**: Create `TrackingViewsDbHelper` at version 15 with pre-populated rows (`HR`, `POWER`, `SPEED_mps`, `CADENCE`).
- **Execution**: Upgrade database to version 16 (`onUpgrade(15, 16)`).
- **Expected Result**:
  - `PRAGMA table_info(LayoutRowsTable)` confirms `ShowValue` and `ShowBar` columns exist.
  - `ShowValue` equals `1` across all rows.
  - `ShowBar` equals `1` for `HR` and `POWER`, and `0` for `SPEED_mps` and `CADENCE`.

### TST-TRK-046.2: Edit Dialog Selection Constraint
- **Setup**: `EditSensorFieldViewModel` initialized with a Power field (`isBarSupported == true`).
- **Execution**:
  - `onShowValueChanged(false)` when `showBar == true` -> succeeds, `showValue == false`.
  - `onShowBarChanged(false)` when `showValue == false` -> rejected, `showBar` remains `true`.
  - `onShowValueChanged(true)` -> succeeds, both active.
  - Switch to Speed (`onSensorTypeChanged(SPEED_mps)`) -> `isBarSupported == false`, `showBar == false`, `showValue == true`.

### TST-TRK-046.3: Repository Persistence
- **Execution**: `updateSensorFieldConfig` and `insertSensorFieldConfig` with `showValue` and `showBar`.
- **Expected Result**: `getSensorFieldConfig` flow emits config matching persisted booleans.

### TST-TRK-046.4: 9-Language Localization Parity
- **Execution**: `TranslationParityTest` verifies `sensor_field_display_elements_title`, `sensor_field_show_value`, and `sensor_field_show_bar` across all 9 supported locales.

---

## 3. Acceptance Verification Criteria

- 100% test pass rate across `TrackingViewsDatabaseManagerTest`, `EditSensorFieldViewModelTest`, and `TranslationParityTest`.
- Clean compilation without AAPT2 errors (`./gradlew processDebugResources`).
