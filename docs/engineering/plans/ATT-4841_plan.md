# Stage 3: Implementation Plan - ATT-4841: [Feature] Flag within sensor field config for showing hr zone, power zone bar, balance, ...

**Ticket**: [ATT-4841](https://rainerblind.atlassian.net/browse/ATT-4841)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-44.1`  
**Requirement Mapping**: `REQ-TRK-046`  
**Test Mapping**: `TST-TRK-046`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Architectural Decomposition (SWE.2)

- **Database Layer (`TrackingViewsDatabaseManager.java`)**:
  - Bump `DB_VERSION` to 16.
  - Define `SHOW_VALUE` and `SHOW_BAR` column constants on `TrackingViewsDbHelper`.
  - Add columns to `CREATE_LAYOUTS_TABLE_V16`.
  - In `onUpgrade(15 -> 16)`: PRAGMA table check, `ALTER TABLE ... ADD COLUMN`, backfill `ShowValue = 1`, and `ShowBar = 1` for `HR`, `POWER`, `PEDAL_POWER_BALANCE`, `W_PRIME_BAL` (`0` for others).
  - Update `insertSensorFieldAt` and `updateSensorView` to persist both booleans.
- **Repository & State Model**:
  - `SensorFieldConfig` & `SensorFieldState`: Add `showValue: Boolean = true, showBar: Boolean = false`.
  - `TrackingViewsRepository.kt`: Populate `showValue`/`showBar` from cursor; update `insertSensorFieldConfig` and `updateSensorFieldConfig`.
  - `TrackingViewModel.kt`: Project `config.showValue` and `config.showBar` into `SensorFieldState`.
- **UI / Edit Dialog Layer**:
  - `EditDialogUiState` & `EditSensorFieldViewModel.kt`: Implement `isBarSupported`, `onShowValueChanged`, `onShowBarChanged` with constraint that prevents deselecting both.
  - `EditSensorFieldDialog.kt`: Render FilterChips for *Value* and *Bar* when `isBarSupported`.
  - `SensorFieldView.kt`: Condition value row on `fieldState.showValue`, and bars on `fieldState.showBar`.
  - `ZonesSettingsActivity.kt`: Remove redundant global `showGauge` toggle row.
- **Localization**: Add strings across all 9 `strings.xml` files.

---

## 2. Atomic Implementation Steps

| Step | Action | Target File(s) | Verification / Test |
| :--- | :--- | :--- | :--- |
| **Step 1** | Upgrade `DB_VERSION = 16`, add migration & queries | `TrackingViewsDatabaseManager.java` | `TrackingViewsDatabaseManagerTest` |
| **Step 2** | Extend `SensorFieldConfig`, `SensorFieldState`, & Repo | `TrackingViewsRepository.kt`, `SensorFieldState.kt` | Compilation check |
| **Step 3** | Implement ViewModel constraints and UI chips | `EditSensorFieldViewModel.kt`, `EditSensorFieldDialog.kt` | `EditSensorFieldViewModelTest` |
| **Step 4** | Condition Value and Bar rendering; remove global toggle | `SensorFieldView.kt`, `ZonesSettingsActivity.kt` | `SensorFieldZoneGaugeTest` |
| **Step 5** | Add 9-language strings and run parity tests | `res/values*/strings.xml` | `TranslationParityTest` |

---

## 3. UI Consistency & Invariants

- **UI Consistency**: Reuses standard Material 3 `FilterChip` and semantic typography (`MaterialTheme.typography.labelLarge`).
- **Invariants**: 100% non-destructive SQLite migration, zero loss of existing tab configs, and backward compatibility for all existing tests.
