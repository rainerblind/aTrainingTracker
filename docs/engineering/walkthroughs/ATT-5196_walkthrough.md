# Stage 5: Walkthrough & Verification - ATT-5196: [Verbesserung] Lieblingsstrecken map popup: drag handle, expandable details, component reuse & stats hydration

**Ticket**: [ATT-5196](https://rainerblind.atlassian.net/browse/ATT-5196)  
**Sub-task**: [ATT-5233](https://rainerblind.atlassian.net/browse/ATT-5233) (`[Test]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `Sprint 2026-45.2`  
**Requirement Mapping**: `REQ-UI-322` (`REQ-UI-322.10`–`REQ-UI-322.12`)  
**Test Mapping**: `TST-UI-282` (`TST-UI-282.13`–`TST-UI-282.14`, `TST-UI-282.2`–`TST-UI-282.3`)  
**Branch**: `feature/ATT-5196`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Executive Summary & Verification Overview

Harmonized the favorite route cluster (*Lieblingsstrecken*) map popup and repository hydration pathway:
1. **Companion Stats Hydration (`WorkoutClusterRepository.kt`)**: In `loadAllClustersLightweight()`, populated `_clusterStats.value = summariesManager.getWorkoutClusterStatsForAllClusters()` when empty (`REQ-UI-322.10`). Cold-start map views immediately render metrics without requiring prior cluster list visits.
2. **Bottom Sheet Parity & Drag Handle (`MapScreenWithTrack.kt`)**: Refactored `ClusterOnMapSheet` to host `MapDetailLayout(useStatusBarsPadding = false)`, rendering canonical `MinimumDragHandle()` at the top (`REQ-UI-322.11`).
3. **Canonical Component Reuse (`MapScreenWithTrack.kt`)**: Replaced custom rows with `WorkoutClusterIdentityRow` and `WorkoutClusterMetadataBlock` in header slot, ensuring 1:1 visual parity with list items (Design Guidelines Section 5.15).
4. **Expandable Analytics Dashboard (`MapScreenWithTrack.kt`)**: Hosted `WorkoutClusterDetailDashboard` in `metadataContent` slot, presenting full metrics and heatmap when dragging the sheet upward.
5. **Dynamic Peek Measurement (`MapScreenWithTrack.kt`)**: Measured `measuredClusterHeaderHeight` via `onHeaderHeightMeasured`, aligning peek resting offset flush above navigation bars (`REQ-UI-322.12`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-322.10` | `TST-UI-282.13` | Unit Test (`WorkoutClusterRepositoryTest`) | **PASSED** | `Verified` |
| `REQ-UI-322.11`, `REQ-UI-322.12` | `TST-UI-282.14` | Contract Test (`GeneralMapLayersContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-322` | `TST-UI-282.2` | 9-Language Localization Parity | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-282.3` | Tier 1 Targeted Test Suite (Rule 60) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Rule 60)
* **Executed Scope**: `database.WorkoutClusterRepositoryTest`, `ui.map.*`, `ui.clusters.*`, `build.*`, `ThemeColorCentralizationAuditTest`.
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterRepositoryTest" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* **Result**: `BUILD SUCCESSFUL in 20s (100% PASS, 0 failures)`
* **Governance**: `python3 tools/verify_requirement_governance.py` passed with 0 errors.

---

## 4. Visual & Manual Device Verification
* Device status: Headless verification (`adb devices` empty).
* UI Structural Verification: `GeneralMapLayersContractTest` verifies `MapDetailLayout`, `MinimumDragHandle()`, `WorkoutClusterIdentityRow`, `WorkoutClusterMetadataBlock`, and dynamic peek height measurement via AST contracts.

---

## 5. Invariant & Governance Verification

1. **Zero Thread-Blocking Allocations**: DB hydration remains on `Dispatchers.IO`; polyline decoding on `Dispatchers.Default`.
2. **Living Documentation Synchronized**: `REQ-UI-322` and `TST-UI-282` in `docs/requirements/req_ui_design_system.md` and `docs/tests/tst_ui_design_system.md` marked `Verified`.
3. **Requirement Governance**: Verified via `verify_requirement_governance.py` (0 errors).
4. **Git Hygiene**: Retaining atomic commit on `feature/ATT-5196`.
