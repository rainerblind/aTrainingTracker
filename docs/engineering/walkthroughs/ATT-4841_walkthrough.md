# Stage 5: Walkthrough & Verification - ATT-4841: [Feature] Flag within sensor field config for showing hr zone, power zone bar, balance, ...

**Ticket**: [ATT-4841](https://rainerblind.atlassian.net/browse/ATT-4841)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.1`  
**Requirement Mapping**: `REQ-TRK-046`  
**Test Mapping**: `TST-TRK-046`  
**Branch**: `feature/ATT-4841`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Executive Summary & Verification Overview

Implemented per-sensor-field display flags (`showValue` and `showBar`) allowing athletes to customize metric rendering:
1. **Schema Upgrade (v16)**: Added `ShowValue INT DEFAULT 1` and `ShowBar INT DEFAULT 0` to `LayoutRowsTable` with non-destructive migration and backfills (`ShowBar = 1` for `HR`, `POWER`, `PEDAL_POWER_BALANCE`, `W_PRIME_BAL`). Updated `DatabaseSchemaRegistry` to 16.
2. **Model & State**: Extended `SensorFieldConfig` and `SensorFieldState` with `showValue` and `showBar` booleans, correctly wired through `TrackingViewsRepository` and `TrackingViewModel`.
3. **Mutual Exclusion Constraint**: `EditSensorFieldViewModel` enforces that an athlete cannot simultaneously deselect both Value and Bar.
4. **Editor UI**: `EditSensorFieldDialog` surfaces *Display Elements* FilterChips for Value and Bar on bar-supported metrics.
5. **Tile Rendering**: `SensorFieldView` conditionally renders Value and horizontal bars (`ZoneGaugeBar`, `PedalPowerBalanceBar`, `WPrimeBatteryGaugeBar`).
6. **Cleaned Settings**: Deprecated redundant global `showGauge` toggle row from `ZonesSettingsActivity`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-046.1` (Per-Field Schema & Migration) | `TST-TRK-046.1` | Real SQLite Migration Test | **PASSED** | `Verified` |
| `REQ-TRK-046.2` (Display Elements Editor Chips) | `TST-TRK-046.2` | On-Device & ViewModel Test | **PASSED** | `Verified` |
| `REQ-TRK-046.3` (Mutual Exclusion Invariant) | `TST-TRK-046.3` | Unit Test | **PASSED** | `Verified` |
| `REQ-TRK-046.4` (Cockpit Element Gating) | `TST-TRK-046.4` | Unit & Compose Tests | **PASSED** | `Verified` |
| `REQ-TRK-046.5` (9-Language Parity) | `TST-TRK-046.5` | TranslationParityTest | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

* `TrackingViewsDatabaseManagerTest` & `TrackingViewsRealSqliteMigrationTest`: 100% PASS (verified v15 -> v16 migration, idempotency, backfills, and isolation).
* `EditSensorFieldViewModelTest`: 100% PASS (verified defaults, mutual exclusion, and persistence).
* `SensorFieldZoneGaugeTest`: 100% PASS (verified state defaults and copy).
* `TranslationParityTest`: 100% PASS across all 9 localized resources (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* Governance: `python3 tools/verify_requirement_governance.py` passed cleanly.

---

## 4. Hardware / Physical Verification (Pixel 10)

Deployed debug APK on Pixel 10 (`66020DLCR002FL`):
* **On-Device Inspection**: Navigated to *Tracking Tabs -> Radeln*, clicked on the Heart Rate (*HF*) tile to open `EditSensorFieldDialog`.
* **Verified Elements**: Verified that the new *Anzeigeelemente* section displays *Wert* and *Balken* FilterChips under View Size.
* **Captured Artifact**: `docs/engineering/walkthroughs/img/ATT-4841_edit_dialog.png`.
* **Logcat**: Zero crashes detected in `adb logcat -b crash -d`.

---

## 5. Invariant & Governance Verification

1. **Non-Destructive Migration**: Existing cockpit layouts retain all sensor fields without data loss.
2. **Living Documentation Synchronized**: `req_tracking_cockpit.md` and `tst_tracking_cockpit.md` updated to `Verified`.
