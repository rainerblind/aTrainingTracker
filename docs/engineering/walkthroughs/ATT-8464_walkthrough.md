# Stage 5 Walkthrough: ATT-8464 - Remove 'Split Favorite Route…' from List Context Menu

**Ticket**: [ATT-8464](https://rainerblind.atlassian.net/browse/ATT-8464) | **Sub-task**: `[Test]` (Pending `submit-stage`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8464`  
**Requirements**: `REQ-UI-505` (`REQ-UI-505.4`, `REQ-UI-505.5`), `REQ-SET-096.1` (preserved)  
**Test Spec**: `TST-UI-505` (`TST-UI-505.3`, `TST-UI-505.4`) | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Executive Summary & Verification Walkthrough
1. **List Context Menu Split Removal (`REQ-UI-505.4`)**:
   - `WorkoutClusterComponents.kt`: Removed `onSplitRequest` parameter on `ClusterItem`, removed the `R.string.cluster_split_action` `DropdownMenuItem`, and removed the unused `Icons.Default.CallSplit` import. Long-click context menu now exclusively offers *"Merge with other favorite route…"* (`cluster_menu_merge`) and *"Delete"* (`delete`).
   - `WorkoutClustersList.kt` & `WorkoutClustersTabsScreen.kt`: Removed `onSplitRequest` parameter and forwarding.
   - `WorkoutClustersScreen.kt`: Removed `clusterToSplit` state, `onSplitRequest` lambda, and `WorkoutClusterSplitSheet` modal host.
2. **Detail-View Split & Localization Invariants Preserved (`REQ-UI-505.5`, `REQ-SET-096.1`)**:
   - `WorkoutClusterHeatmapScreen.kt:808-815` retains the `CallSplit` header `IconButton` (for `cluster.hitCount >= 2`) and `WorkoutClusterSplitSheet` (`L270-279`).
   - `WorkoutClusterSplitSheet.kt` and `WorkoutClustersViewModel.openSplitPreview()` remain intact and functional.
   - `R.string.cluster_split_action` remains intact across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
3. **Contract Test Update (`TST-UI-505.3`)**:
   - Updated `WorkoutClusterSplitMapPreviewTest.testClusterItem_and_WorkoutClustersScreen_excludeSplitAction_whileHeatmapRetainsIt` (`// Traces to: REQ-UI-505, REQ-SET-096`) to verify exclusion from list screens/components while asserting retention of Merge/Delete in `ClusterItem` and `CallSplit` / `WorkoutClusterSplitSheet` in `WorkoutClusterHeatmapScreen`.

---

## 2. Requirement & Test Verification Matrix
| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-505.4` | `TST-UI-505.3` | Contract: `WorkoutClusterSplitMapPreviewTest` | **PASSED** (4/4) | `Verified` |
| `REQ-UI-505.5` / `REQ-SET-096.1` | `TST-UI-505.3` | Contract + i18n: `WorkoutClusterSplitDialogContractTest` | **PASSED** (5/5) | `Verified` |
| `REQ-UI-505` | `TST-UI-505.4` | Tier 1 Targeted Suite (`*Cluster*` + Audits + Build) | **PASSED** (353/353) | `Verified` |

---

## 3. Automated Test & Build Evidence (Tier 1 Targeted Scope)
* **Command**:
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "*Cluster*" --tests "*GlobalDeleteContextMenuAuditTest*" --tests "*ArchitectureConformanceContractTest*" --tests "*RepositoryUiCleanlinessContractTest*" :app:compileDebugAndroidTestKotlin assembleDebug --max-workers=2
  ```
* **Observed Results**:
  - **Tier 1 Targeted Unit Tests**: **81 suites, 353 tests, 0 failures, 0 errors, 0 skipped** (`WorkoutClusterSplitMapPreviewTest`: 4/4, `WorkoutClusterSplitDialogContractTest`: 5/5, `ClusterItemAndFilterPolishTest`: 7/7, `GlobalDeleteContextMenuAuditTest`: 3/3, `ArchitectureConformanceContractTest`: 9/9, `RepositoryUiCleanlinessContractTest`: 9/9).
  - **Build & AndroidTest Compile**: `:app:compileDebugAndroidTestKotlin` and `:app:assembleDebug` -> **BUILD SUCCESSFUL**.
  - **Governance**: `python3 tools/verify_requirement_governance.py --sync-indexes` & `python3 tools/verify_requirement_governance.py` -> **PASS** (100% shard/master parity & Chesterton's Fence audit verified).
  - **Note**: Tier 2 static gates (`lintDebug`, `detekt`, full unit suite) are deferred to the Sprint-End Integration Gate per `Rule 3.5` / `Rule 85`.

---

## 4. System Invariants & Hygiene
* `ANT-Android-SDKs` submodule verified untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* Zero PNG assets modified; zero database/repository/engine changes.
* `REQ-UI-505` and `TST-UI-505` promoted to `[Verified]` in living documentation.
