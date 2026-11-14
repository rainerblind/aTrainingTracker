# Stage 5: Walkthrough & Verification - ATT-7736: Rotate ic_radar.xml icon 90 degrees counter-clockwise (left)

**Ticket**: [ATT-7736](https://rainerblind.atlassian.net/browse/ATT-7736)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6` | **Branch**: `feat/ATT-7736`  
**Requirement Mapping**: `REQ-UI-451` / `REQ-UI-451.4`  
**Test Mapping**: `TST-UI-451` / `TST-UI-451.4`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

Following review feedback on ATT-7544, ATT-7736 reorients the Bike Radar status icon (`res/drawable/ic_radar.xml`) 90 degrees counter-clockwise to radiate radar waves horizontally to the left, matching rearview radar visual convention:
1. **Vector Asset Reorientation (`ic_radar.xml`)**: Wrapped the `<path>` inside `<group android:pivotX="480" android:pivotY="480" android:rotation="270">`, transforming origin dot to center-right `(720, 480)` and wave arcs radiating horizontally to the left (`REQ-UI-451.4`).
2. **Contract & Geometry Verification**: Extended `SensorStatusTopBarSpacingTest.testIcRadarDrawableFile_existsAndIsValidVector` asserting `android:rotation="270"` (or `-90`) and pivot coordinates `(480, 480)` (`TST-UI-451.4a`).
3. **SensorType Invariant**: Verified all 6 radar sensor types (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`) continue mapping to `R.drawable.ic_radar` (`TST-UI-451.4b`).
4. **Layout Budget & Invariants Preserved**: Zero changes to 24dp bounds, 960x960 viewport, `SensorStatus.kt` 9-icon layout budget (270 dp <= 272 dp), or `SensorSourceDialog.kt`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-451.4` | `[TST-UI-451.4a]` | Unit Test (`SensorStatusTopBarSpacingTest.testIcRadarDrawableFile_existsAndIsValidVector`) | **PASSED** | `Verified` |
| `REQ-UI-451.4` | `[TST-UI-451.4b]` | Unit Test (`SensorStatusTopBarSpacingTest.testRadarSensorTypes_mapToDedicatedIcRadarDrawable`) | **PASSED** | `Verified` |
| `REQ-UI-451` | `[TST-UI-451.4c]` | Tier 1 Targeted (`SensorStatusTopBarSpacingTest` suite: 7/7 tests) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
- Command: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorStatusTopBarSpacingTest" --max-workers=2`
- Result: **7/7 tests passed** (100% green under sprint lock).
- Governance Verification: `python3 tools/verify_requirement_governance.py` $\to$ **PASSED** (100% requirement & test spec parity, 0 violations).

---

## 4. Invariant & Governance Verification

1. **Geometry Invariant**: 24dp x 24dp bounds and 960x960 viewport strictly preserved; origin dot centered vertically at `y = 480` and radar waves expand leftwards towards `x = 333`.
2. **Display Budget Invariant**: 9 icons consume 270 dp in portrait cockpit header, fitting within 272 dp budget with >= 2 dp margin.
3. **Target Release Governance**: Aligned to active unreleased release `V5.0.0` (Core Rule 8).
