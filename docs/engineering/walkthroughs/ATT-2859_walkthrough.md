# Stage 5: Walkthrough & Verification - ATT-2859: Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets

**Ticket**: [ATT-2859](https://rainerblind.atlassian.net/browse/ATT-2859)  
**Sub-task**: [ATT-3863](https://rainerblind.atlassian.net/browse/ATT-3863) (`[Test]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Requirement Mapping**: `REQ-UI-375`  
**Test Mapping**: `TST-UI-338`  
**Branch**: `feature/ATT-2859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Executive Summary & Verification Overview

Migrated the 16 legacy multi-density sport type (`bsport_bike`, `bsport_run`, `bsport_other`) and ANT+/BLE sensor badge (`bike_cad`, `bike_pwr`, `bike_spd`, `bike_speed_and_cadence`, `hr`, `run_spd`, `temp`, `bt_bike_cad`, `bt_bike_pwr`, `bt_bike_spd`, `bt_bike_speed_and_cadence`, `bt_hr`, `bt_run`) raster drawables to density-independent Android `VectorDrawable` XML assets in `app/src/main/res/drawable/` in strict compliance with Rule 37 (`docs/design_guidelines.md` Section 5.5):
- **Xfig 3.2 (`.fig`) & GIMP (`.xcf`) Mathematical Translation**: Compiled `bsport_bike.xml`, `bsport_run.xml`, `bsport_other.xml`, `bt_bike_cad.xml`, `bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_speed_and_cadence.xml`, `bt_hr.xml`, and `bt_run.xml` directly from `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/logo.fig`, `Bluetooth/bike.fig`, `Bluetooth/runner.fig`, `Bluetooth/BT_HR_just_the_heart.fig`, and `Bluetooth/BT_*.xcf`, sorting primitives in back-to-front Z-depth order (`depth 70` down to `depth 50`) and translating circles (`1`), 3-point circular arcs (`5`), stroked polylines (`2`), and B-splines (`3`) to exact SVG `A`/`Q`/`C` curves with `android:fillType="evenOdd"` for text counter-holes.
- **Analytical ANT+ Badge Reconstruction**: Constructed the 7 ANT+ sensor badges on their native `96×96` viewport with `#000000` rounded-square badges ($r=10$), `#FFFFFF` analytical geometric emblems, and `evenOdd` bold uppercase typography.
- **Legacy Raster Purge**: Removed all 106 superseded `.png` files across `drawable`, `drawable-ldpi`, `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, and `drawable-xxxhdpi` (including `bsport_*_gray.png` and `btle_hr.png`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-375` | `TST-UI-338.1` | DOM & Rule 37 Geometric Contract (`VectorAssetResolutionContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-375` | `TST-UI-338.2` | Multi-Color & Protocol Badge Fidelity (`VectorAssetResolutionContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-375` | `TST-UI-338.3` | 106 Raster PNG Purge Audit (`VectorAssetResolutionContractTest`, `DrawableDensityFallbackAuditTest`) | **PASSED** | `Verified` |
| `REQ-UI-375` | `TST-UI-338.4` | Resource ID & SportTypeSelector Contract (`SportTypeSelectorContractTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-338.3` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
```

### Targeted Unit & Integration Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.resources.VectorAssetResolutionContractTest" --tests "com.atrainingtracker.trainingtracker.ui.resources.DrawableDensityFallbackAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SportTypeSelectorContractTest"
BUILD SUCCESSFUL in 46s
```

---

## 4. Hardware / Physical Verification (Pixel 10)

### Visual Consistency (Rule 23 & Rule 37 — 1:1 Side-by-Side Raster vs. Vector Comparison)

| 16-Icon Side-by-Side Original PNG vs. Compiled VectorDrawable XML (`ATT-2859_vector_comparison.png`) |
| :---: |
| ![16-Icon Vector Comparison](img/ATT-2859_vector_comparison.png) |

* **Checked against `docs/design_guidelines.md` §5 & §5.5 (Rule 37)**: shapes ☑ spacing ☑ colors/themes ☑ typography/icons ☑ placement ☑
* **Deviations & justification**: None — 1:1 mathematical fidelity with original `.fig`/`.xcf` master geometry and reference PNGs.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-UI-375`) and `docs/tests.md` (`TST-UI-338`) updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask (`ATT-3863`) transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket (`ATT-2859`) transitioned to `Final Review (Human)` and assigned to `human` for final release sign-off.
