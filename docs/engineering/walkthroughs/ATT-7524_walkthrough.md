# Stage 5: Walkthrough & Verification - ATT-7524: Default ZoneDisplayOptions to Background + Left & Right Bars and Persist HRV Display Options

**Ticket**: [ATT-7524](https://rainerblind.atlassian.net/browse/ATT-7524)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-UI-172` (*Configurable Zone Visual Presentation*), `REQ-TRK-061.3` (*Dedicated HRV ZoneDisplayOptions*)  
**Test Mapping**: `TST-UI-124.1`–`TST-UI-124.4`, `TST-TRK-061.3`  
**Branch**: `feature/ATT-7524` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Executive Summary & Verification Overview
ATT-7524 updates `ZoneDisplayOptions` defaults to symmetrical card framing (tinted background, left bar, and right bar enabled, text color disabled) and establishes persistent DataStore preferences for the 4th `HRV (DFA a1)` tab in `ZonesSettingsActivity`. Furthermore, `TrackingViewModel` is decoupled so `SensorType.HRV_DFA_A1` resolves its display options directly from dedicated HRV settings across all sports (`RUN`, `BIKE`, `UNKNOWN`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-172` | `[TST-UI-124.1]` | Unit Test (`ZoneDisplayOptionsTest`, `SensorFieldZoneDisplayTest`) | **PASSED** | `Verified` |
| `REQ-UI-172` | `[TST-UI-124.2]` | Unit Test (`SettingsDataStoreZoneAtomicTest`) | **PASSED** | `Verified` |
| `REQ-TRK-061.3` | `[TST-TRK-061.3]` | Unit Test (`TrackingViewModelHrvTest`) | **PASSED** | `Verified` |
| `REQ-UI-172` | `[TST-UI-124.3]` | Localization Parity (`ZoneDisplayLocalizationTest`, `TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-UI-124.4]` | Tier 1 Targeted Regression Scope | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope
* `ZoneDisplayOptionsTest`: 4/4 passed (default values, custom values, copy immutability, equality/hashcode).
* `SensorFieldZoneDisplayTest`: 7/7 passed (`SensorFieldState` default right bar, threshold preservation).
* `SettingsDataStoreZoneAtomicTest`: 12/12 passed (dedicated HRV DataStore persistence, explicit `false` preservation, fallback to `true`).
* `TrackingViewModelHrvTest`: 4/4 passed (independent `getZoneType` nullity, dedicated `hrvDisplayOptions` resolution across `RUN`, `BIKE`, `UNKNOWN`).
* `ZoneDisplayLocalizationTest` & `TranslationParityTest`: 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* `ThemeColorCentralizationAuditTest` & `ui.components.core.*`: 100% passed (zero uncentralized color tokens).
* `processDebugResources`: Succeeded (AAPT2 resource check green).

```text
BUILD SUCCESSFUL in 23s
33 actionable tasks: 2 executed, 31 up-to-date
```

---

## 4. Hardware / Physical Verification (Pixel 10)
Pure settings model and telemetry rendering logic refactoring; verified via automated unit and contract test suites, AAPT2 compilation, and living doc governance checks. Visual previews in `ZonesSettingsActivity` conform to Material 3 tokens (`TTColor.Zone*`) and existing `ZoneDisplayToggleRow` layouts.

---

## 5. Invariant & Governance Verification
1. **Explicit Preference Preservation**: Users with explicitly saved `showRightBar = false` in DataStore are preserved without overwrite (`dataStore_preservesExplicitFalseRightBar` PASSED).
2. **Threshold & DB Invariance**: HRV threshold gap ($T_1 \ge T_2 + 0.05$) and SQLite schema (`DB_VERSION = 30`) remain unchanged.
3. **Living Documentation Synchronized**: Living requirements and tests validated via `python3 tools/verify_requirement_governance.py` (0 violations).
4. **Subtask Handover**: Ready for Gate 5 independent review via `--as agent2 gate-verdict`.
