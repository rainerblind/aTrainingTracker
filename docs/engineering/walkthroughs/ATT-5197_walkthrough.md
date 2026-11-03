# Stage 5: Walkthrough & Verification - ATT-5197: [Bug] Tracking cockpit map (SensorGridScreen & TrackingViewModel): never show or compute Lieblingsstrecken (clusters)

**Ticket**: [ATT-5197](https://rainerblind.atlassian.net/browse/ATT-5197)  
**Sub-task**: [ATT-5228](https://rainerblind.atlassian.net/browse/ATT-5228) (`[Test]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `Sprint 2026-45.2`  
**Requirement Mapping**: `REQ-UI-322` (`REQ-UI-322.6`–`REQ-UI-322.9`)  
**Test Mapping**: `TST-UI-282` (`TST-UI-282.11`–`TST-UI-282.12`, `TST-UI-282.2`–`TST-UI-282.3`)  
**Branch**: `feature/ATT-5197`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Executive Summary & Verification Overview

Decoupled and strictly isolated the live tracking cockpit map (`SensorGridScreen.kt` / `TrackingViewModel.kt`) from route clusters (*Lieblingsstrecken* / `GeneralMapLayer.CLUSTERS`):
1. **Cockpit UI Map Excision (`SensorGridScreen.kt`)**: Removed `clusters(mapState.mapClusters)` composition and excised `mapState.mapClusters` from `remember(...)` dependencies in `TrackingMapContainer`.
2. **ViewModel Decoupling (`TrackingViewModel.kt`)**: Removed `workoutClusterRepository` constructor parameter, deleted `isClusterLayerEnabled()` and `mapClustersFlow`, simplified `staticMapLayerFlow` to 3 streams, and excised `mapClusters` from `TrackingScreenState` and `StaticMapLayerData`.
3. **Model Simplification (`TrackingMapState.kt`)**: Removed `val mapClusters` property and unused `MapCluster` import.
4. **Preserved Invariants**: General map (`MapFragmentWithTrackViewModel` / `MapScreenWithTrack.kt`) retains 100% full `CLUSTERS` visibility controls, polyline rendering, and `ClusterOnMapSheet` inspection. Zero 1-Hz telemetry allocations or Compose churn (`REQ-MAP-051`, `REQ-UI-326`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-322.6`, `REQ-UI-322.8` | `TST-UI-282.11` | Contract Test (`TrackingMapIsolationContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-322.7` | `TST-UI-282.12` | Unit Test (`TrackingViewModelMapStateTest`) | **PASSED** | `Verified` |
| `REQ-UI-322` | `TST-UI-282.2` | 9-Language Localization Parity | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-282.3` | Tier 1 Targeted Test Suite (Rule 60) | **PASSED** (427/427) | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Rule 60)
* **Executed Scope**: `ui.tracking.*`, `ui.map.*`, `build.*`, `ThemeColorCentralizationAuditTest`.
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* **Result**: `BUILD SUCCESSFUL in 52s (427 tests completed, 0 failed, 100% PASS)`
* **Build Verification**: `./gradlew assembleDebug` passed in 21s.
* **Governance**: `python3 tools/verify_requirement_governance.py` passed with 0 errors (Chesterton's Fence verified with all 4 mandatory fields).

---

## 4. Visual & Manual Device Verification
* Device status: No active ADB device connected during headless run (`adb devices` empty).
* Compose Static Contract: `TrackingMapIsolationContractTest` asserts AST level guarantee that `SensorGridScreen.kt` body never composes `clusters()` on `ATrainingTrackerMap`, and `TrackingMapState` contains zero cluster fields.

---

## 5. Invariant & Governance Verification

1. **Zero Cockpit Regressions**: 427 targeted unit tests passed with 0 failures.
2. **Living Documentation Synchronized**: `REQ-UI-322` and `TST-UI-282` in `docs/requirements/req_ui_design_system.md` and `docs/tests/tst_ui_design_system.md` updated to `Verified`.
3. **Requirement Governance**: Passed via `verify_requirement_governance.py`.
4. **Git Hygiene**: Retaining single atomic commit ready for squash-merge onto `sprint/2026-45.2`.
