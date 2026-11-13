# Stage 5: Walkthrough & Verification - ATT-7532: Configurable Morning HRV Check Button on Start Tracking Screen

**Ticket**: [ATT-7532](https://rainerblind.atlassian.net/browse/ATT-7532)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-UI-525.1`, `REQ-UI-525.2`, `REQ-UI-525.3`, `REQ-UI-525.4`, `REQ-UI-525.5`  
**Test Mapping**: `TST-UI-525.1`, `TST-UI-525.2`, `TST-UI-525.3`, `TST-UI-525.4`, `TST-UI-525.5`, `TST-UI-525.6`  
**Branch**: `feature/ATT-7532` | **Author**: AI Agent 2 (Flash) | **Date**: 2026-11-13  

---

## 1. Executive Summary & Verification Overview
Implemented a user-configurable, contextual "Morgendlicher Check" CTA button on the Start tracking screen (`ControlTrackingScreen` / `TrackingTabsScreen` `bottomContent`). The launcher enables 1-tap navigation directly into `NavRoutes.MORNING_HRV`. Visibility is reactive (`ControlTrackingViewModel.isMorningHrvCheckVisible`) and strictly guarded: active only in `TrackingMode.READY`, when tuning preference `showMorningCheckOnStartScreen` is enabled, at least one paired HRM sensor exists (`DevicesDatabaseManager.hasPairedHeartRateDevices`), current local time is before `morningCheckCutoffHour` (default 11:00 AM, configurable 6..15), and no check has been completed today (`HrvReadinessDatabaseManager.hasCompletedMorningCheck`). Added tuning controls to `PhysiologyModelsSection` across `AdvancedTuningScreen` and `AdvancedTuningDialog`, and verified 100% 9-language localization parity.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-525.1` | `TST-UI-525.1` | `TuningConfigTest` | **PASSED** | `Verified` |
| `REQ-UI-525.2` | `TST-UI-525.2` | `DevicesDatabaseManagerTest` | **PASSED** | `Verified` |
| `REQ-UI-525.3` | `TST-UI-525.3` | `HrvReadinessDatabaseManagerTest` | **PASSED** | `Verified` |
| `REQ-UI-525.4` | `TST-UI-525.4` | `ControlTrackingViewModelTest` & Compose audit | **PASSED** | `Verified` |
| `REQ-UI-525.5` | `TST-UI-525.5` | `TranslationParityTest` (9 locales) | **PASSED** | `Verified` |
| `REQ-PRO-001`, `REQ-PRO-022` | `TST-UI-525.6` | `verify_requirement_governance.py` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
Command: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningConfigTest" --tests "com.atrainingtracker.banalservice.database.DevicesDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModelTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
```text
BUILD SUCCESSFUL in 1m 28s
33 actionable tasks: 7 executed, 5 from cache, 21 up-to-date
100% PASS: All unit tests in targeted test classes passed cleanly.
```

### Resource Compilation Verification Scope
Command: `python3 tools/parallel_sprint_lock.py -- ./gradlew processDebugResources`
```text
BUILD SUCCESSFUL in 1s
18 actionable tasks: 18 up-to-date
100% PASS: All 9-language XML resources compiled cleanly via AAPT2.
```

### Governance Verification Evidence
Command: `python3 tools/verify_requirement_governance.py`
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero attribute violations detected. Zero unmounted Composable islands. Repository encapsulation verified.
```

---

## 4. UI Consistency & Verification Checklist
* **`docs/design_guidelines.md` §1 & §5 Compliance**:
  * Shapes: `RoundedCornerShape(8.dp)` matching cockpit control cards and buttons.
  * Spacing & Sizing: `minHeight = 48.dp`, padding `16.dp` horizontal, `8.dp` vertical; text styled with `MaterialTheme.typography.labelLarge` and `maxLines = 1` with ellipsis.
  * Iconography: `Icons.Default.Favorite` (HR heart) alongside localized label `R.string.hrv_morning_check_start_button`.
  * Typography & Parity: 100% 9-language translation parity verified across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

---

## 5. Invariant & Governance Verification
1. **Zero Interference with Active Cockpit**: CTA button is strictly visible during `TrackingMode.READY` and automatically hidden once tracking begins (`RECORDING`/`PAUSED`).
2. **Zero Overhead for Non-HRM Athletes**: Button is absent for athletes without paired HRM sensors.
3. **Daily One-Shot Invariant**: Immediate auto-hide upon completing today's morning check and on resume.
