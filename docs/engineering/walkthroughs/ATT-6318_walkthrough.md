# Stage 5: Walkthrough & Verification - ATT-6318: WorkoutClusterSplitSheet Embedded Map Preview

**Ticket**: [ATT-6318](https://atrainingtracker.atlassian.net/browse/ATT-6318)  
**Parent Epic**: [ATT-176](https://atrainingtracker.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6318`  
**Requirement Mapping**: `REQ-UI-505` (`REQ-UI-505.1`–`REQ-UI-505.5`) | **Test Mapping**: `TST-UI-505` (`TST-UI-505.1`–`TST-UI-505.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary & Verification Overview
Resolved the issue where `WorkoutClusterSplitSheet` modal bottom sheet and scrim obscured background corridor variant paths (`splitVariantPaths`), and where list views lacked a map preview and split action:
* **Embedded Color-Coded Map Preview (`REQ-UI-505.1`)**: Embedded a 200dp rounded `Surface` with `PathPreviewMap` directly above the sensitivity slider inside `WorkoutClusterSplitSheet.kt`, rendering all detected corridor variants in their corresponding badge colors (`getVariantColor(index)`) and auto-fitting cluster bounds.
* **Live Dynamic Modulation (`REQ-UI-505.2`)**: Modulating corridor sensitivity slider updates `splitVariantPaths` and refreshes the embedded map preview in real time without UI stutter.
* **Multi-Path Preview Map (`REQ-UI-505.3`)**: Enhanced `PathPreviewMap.kt` to accept `paths: List<MappablePath> = emptyList()` with composite `LatLngBounds`, while preserving single-path caller compatibility and `liteMode(true)` touch isolation.
* **List-Level Split Action Entry Point (`REQ-UI-505.4`)**: Exposed "Split Favorite Route…" (`cluster_split_action`, `Icons.Default.CallSplit`) in `ClusterItem` 3-dot context menu for `hitCount >= 2`, bubbled to `WorkoutClustersScreen.kt` to open `WorkoutClusterSplitSheet` directly from list view.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-505.1` | `TST-UI-505.1` | `WorkoutClusterSplitMapPreviewTest.testWorkoutClusterSplitSheet_embedsColorCodedMapPreviewAboveSlider` | **PASSED** | `Verified` |
| `REQ-UI-505.2` | `TST-UI-505.1` | `WorkoutClusterSplitMapPreviewTest.testWorkoutClusterSplitSheet_embedsColorCodedMapPreviewAboveSlider` | **PASSED** | `Verified` |
| `REQ-UI-505.3` | `TST-UI-505.2` | `WorkoutClusterSplitMapPreviewTest.testPathPreviewMap_supportsMultiPathAndCompositeBounds` | **PASSED** | `Verified` |
| `REQ-UI-505.4` | `TST-UI-505.3` | `WorkoutClusterSplitMapPreviewTest.testClusterItem_and_WorkoutClustersScreen_wireSplitAction` | **PASSED** | `Verified` |
| `REQ-UI-505.5` | `TST-UI-505.4` | Tier 1 targeted regression suite & governance audit | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: 30s)
* **Executed Scope**:
  * `--tests "com.atrainingtracker.trainingtracker.ui.clusters.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.map.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.components.core.*"`
  * `--tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: `BUILD SUCCESSFUL in 30s` (100% PASS across 699 unit tests).
* **Governance Check**: `python3 tools/verify_requirement_governance.py` -> `PASS: Living documentation master tables match all 9 domain shards with 100% parity.`
* **Device Verification**: Physical device check via `adb devices`: no physical device connected; software emulator strictly avoided per Rule 79.

---

## 4. Invariants & Preserved Behavior
1. **Core Clustering Invariant (`REQ-UI-505.5`)**: `WorkoutClusterEngine.kt` clustering heuristics and medoid algorithms remain completely unmodified.
2. **PathPreviewMap Compatibility**: Single-path callers (`CreateLapOnMapSheet`, `MoveLapBoundaryOnMapSheet`, `CropWorkoutScreen`, `WorkoutSummary`) and `liteMode(true)` touch isolation remain 100% intact.
3. **9-Language Parity & Zero Emojis (`ATT-5400`)**: Complete parity across all 9 locales with zero inline emojis.
