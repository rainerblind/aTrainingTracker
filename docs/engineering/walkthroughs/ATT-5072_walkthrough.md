# Stage 5: Walkthrough & Verification - ATT-5072: Altitude Calibration Dialog Known Location Toggle

**Ticket**: [ATT-5072](https://rainerblind.atlassian.net/browse/ATT-5072)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-TRK-047` (`REQ-TRK-047.1`)  
**Test Mapping**: `TST-TRK-047` (`TST-TRK-047.6`, `TST-TRK-047.7`, `TST-TRK-047.1`, `TST-TRK-047.5`)  
**Branch**: `feature/ATT-5072`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Executive Summary & Verification Overview
ATT-5072 resolves landmark clutter in `KnownLocations.db` during manual barometric altimeter calibration from the tracking cockpit (`SensorSourceDialog.kt`). The "Save as Known Location" (*Als Lieblingsort speichern*) checkbox now defaults to unchecked (`false`), and the location name text input is conditionally rendered only when the athlete explicitly chooses to bookmark the point. When physically present at an existing saved known location (`matchedLocation != null`), the dialog recognizes the landmark, displays the matched location name badge (`altitude_calibration_matched_location`), hides the redundant save toggle, pre-fills the reference altitude from the stored location, and calibration updates the existing record with `ElevationSource.MANUAL_USER` and `isLocked = true` via `TrackingTabsViewModel.calibrateAltitude`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-047.1` | `TST-TRK-047.6` | Contract Test (`SensorSourceDialogAltitudeCalibrationTest.testContract_SensorSourceDialogKnownLocationDefaultsAndMatching`) | **PASSED** | `Verified` |
| `REQ-TRK-047.1` | `TST-TRK-047.7` | Unit Test (`TrackingTabsViewModelLocationTest.testCalibrateAltitude_withMatchedLocation_updatesExistingRecordAndCalibrates`) | **PASSED** | `Verified` |
| `REQ-TRK-047.1` | `TST-TRK-047.1` | Unit Test (`SensorSourceDialogAltitudeCalibrationTest.testContract_SensorSourceDialogHasAltitudeCalibrationSection`, `testUnitConversion_*`) | **PASSED** | `Verified` |
| `REQ-TRK-047.4` | `TST-TRK-047.5` | Localization Test (`TranslationParityTest`) for 9-language parity | **PASSED** | `Verified` |
| `REQ-PRO-001` | Tier 1 Targeted | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"` | **PASSED** (100% Tier 1) | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Suite (Rule 60)
* **Scope**: `Tier 1 targeted: com.atrainingtracker.trainingtracker.ui.tracking.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.build.*`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`
```text
BUILD SUCCESSFUL in 35s
32 actionable tasks: 1 executed, 31 up-to-date
```

### Requirement Governance
```text
Modified/Altered existing requirement(s) detected: REQ-TRK-047.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / Physical Verification (Pixel 10)
* **ADB Verification**: Deferred to Sprint Review (device offline: `66020DLCR002FL offline`).

---

## 5. Invariant & Governance Verification
1. **Calibration Dispatch**: Manual calibration dispatch via `BANALServiceRepository.calibrateAltimeter` remains immediate and unaltered.
2. **Database Integrity**: Unchecked calibration never writes to `KnownLocations.db`; matched location updates apply `ElevationSource.MANUAL_USER` and `isLocked = true`.
3. **UI Consistency**: Reuses `MaterialTheme.colorScheme.surfaceVariant`, `outlineVariant`, `primary`, and `typography.bodyMedium`.
4. **Living Documentation**: `REQ-TRK-047.1` and `TST-TRK-047` confirmed `[Verified]` in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
