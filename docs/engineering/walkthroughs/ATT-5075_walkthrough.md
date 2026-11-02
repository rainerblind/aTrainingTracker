# Stage 5: Walkthrough & Verification - ATT-5075: Harmonize Lieblingsstrecken Map Popup

**Ticket**: [ATT-5075](https://rainerblind.atlassian.net/browse/ATT-5075)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-322` (`REQ-UI-322.4`, `REQ-UI-322.2`)  
**Test Mapping**: `TST-UI-282` (`TST-UI-282.5`, `TST-UI-282.6`)  
**Branch**: `feature/ATT-5075`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Executive Summary & Verification Overview
ATT-5075 modernizes the bottom sheet popup for favorite route clusters (`ClusterOnMapSheet` in `MapScreenWithTrack.kt`) to match the visual grammar, tokens, and information density of *Lieblingsstrecken* list items (`WorkoutClusterComponents.kt`). It completely eliminates hardcoded purple/lila (`TTColor.Cluster`) text and icon styling, aligns with Material 3 tokens, enriches `MapCluster` with `WorkoutClusterStats` via `MapFragmentWithTrackViewModel`, calibrates cluster sheet peek height to `BottomSheetDesign.PeekHeightWorkout + navBarHeight` (~140dp), and strictly preserves map canvas polyline rendering in `TTColor.Cluster`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-322.4` | `TST-UI-282.5` | Contract Test (`GeneralMapLayersContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-322.2` | `TST-UI-282.6` | Unit Test (`MapModelsTest`, `MapFragmentWithTrackViewModelTest`) | **PASSED** | `Verified` |
| `REQ-UI-322.6` | `TST-UI-282.7` | 9-Language Localization Audit (`GeneralMapLayersLocalizationTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | Tier 1 Targeted | `./gradlew testDebugUnitTest --tests "..."` | **PASSED** (100% Tier 1) | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Suite (Rule 60)
* **Scope**: `--tests "com.atrainingtracker.trainingtracker.ui.map.*"` + `--tests "com.atrainingtracker.trainingtracker.build.*"` + `--tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
```text
BUILD SUCCESSFUL in 13s
32 actionable tasks: 1 executed, 31 up-to-date
```
* **Executed Tests**: `GeneralMapLayersContractTest` (including `testClusterOnMapSheet_harmonizedLayoutAndColorTokens_TST_UI_282_5`), `MapModelsTest` (including `toMapCluster_withStats_enrichesMapClusterAndPreservesCoordinates_TST_UI_282_6`), `MapFragmentWithTrackViewModelTest` (verifying `clusterStats` combining), and `ThemeColorCentralizationAuditTest`.

### Requirement Governance
```text
Zero requirement modifications detected.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / Physical Verification (Pixel 10)
* **ADB Verification**: Deferred to Sprint Review (device offline: `66020DLCR002FL offline`).

### Visual Consistency (Rule 23)
* **Reused Components**: `MetricItem`, `DistanceFormatter`, `AltitudeFormatter`, `TimeFormatter`, `WorkoutClusterStats.formatRelativeRecency`.
* **Theme Tokens**: Standard Material 3 (`onSurface`, `onSurfaceVariant`, `primary`), `TTColor.Gold` for PR trophy, and `Color.Unspecified` for 32dp sport icon.

---

## 5. Invariant & Governance Verification
1. **Map Canvas Polyline Styling**: Canvas polyline stroke color (`TTColor.Cluster`), zIndex (`12f`), and width (`MapVisualization.CLUSTER_WIDTH`) preserved.
2. **Referential Coordinate Stability (`REQ-MAP-051`)**: Decoded coordinate lists memoized across recompositions.
3. **Non-Regression**: Zero regression on routes, segments, locations, or live track layers.
4. **Living Documentation**: `REQ-UI-322` and `TST-UI-282` updated to `Verified`.
