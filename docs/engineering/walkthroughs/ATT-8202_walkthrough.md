# Stage 5: Walkthrough & Verification - ATT-8202: SWE.2 Heap/Cache/Map Budgets & IF-SWE-05 Upward-Import Guard

**Ticket**: [ATT-8202](https://rainerblind.atlassian.net/browse/ATT-8202) (`2 SP`) | **Sub-task**: `[Test]` (assigned on `submit-stage`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8202`  
**Requirement Mapping**: `REQ-PRO-130` (`[Verified]`, refining `REQ-PRO-050`, `REQ-PRO-052`, `REQ-PRO-115`) | **Test Mapping**: `TST-PRO-130` (`[Verified]`)  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-18

---

## 1. Executive Summary & Verification Overview
Completed Stage 5 clean-room verification for `ATT-8202` on `feature/ATT-8202`:
* **Bounded Caches & Decoupled Persistence**: `WorkoutRepository.kt` (`5,048 <= 5,117` LoC) enforces `LruCache<Long, HrvSessionThreshold>(64)` (`HRV_THRESHOLD_CACHE_SIZE = 64`) with eviction in `deleteWorkoutSummary()`, `deleteWorkoutCascade()`, `refreshWorkout()`, and `resetForTesting()`, and implements `ClusterWorkoutDataPort`. `WorkoutClusterEngine.kt` (`1,634 <= 1,635` LoC) has `0` `.ui.*` imports via `ClusterWorkoutDataPort.kt` (`319` LoC) and `ClusterWorkoutDataAdapter.kt` (`45` LoC).
* **SWE.2 Architecture & Interface Grounding**: `docs/architecture.md` (`v1.0.3`, §2/§4.9/§5.1) and `docs/architecture/interfaces.md` (`v1.0.1`) document singleton repository/cache/vertex/`GoogleMap` budgets (`MAX_OVERLAY_POINTS = 800`, `MAX_SUMMARY_THUMBNAIL_TRACKS = 8`, `recentlyFinishedWorkoutIds <= 50`, `LruCache(64)`, `2_000`/`8_000` vertices, `RadarStateRepository <= 4`, `extraBufferCapacity = 16`, `liteMode(true)` vs `beyondViewportPageCount = 0`), Swim/RadarLight/TPMS/Polar PFTP, and truthful `IF-SWE-05` boundaries.
* **Upward-Import Ratchet**: `ArchitectureConformanceContractTest.kt` enforces `\bimport\s+(?:static\s+)?com\.atrainingtracker\..*\.ui\b` across `trainingtracker/database/` (`10` legacy files) and `banalservice/database/` (`0`), asserting `WorkoutClusterEngine.kt` has `0` `.ui.*` imports plus negative `ui.aftermath` tripwire.

---

## 2. Requirement & Test Verification Matrix
| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-130.1` | `TST-PRO-130.1` | `WorkoutRepository*Test` (`19` suites, `73/73` PASS) + `ArchitectureConformanceContractTest` (`11/11`) + `NonFunctionalRequirementsContractTest` (`3/3`) + `SystemArchitectureDesignContractTest` (`9/9`) | **PASSED** | `Verified` |
| `REQ-PRO-130.2` | `TST-PRO-130.2` | `InterfaceSignatureDriftContractTest` (`3/3`) + `AspiceSweAuditContractTest` (`13/13`) + `WorkoutCluster*Test`/`EditWorkoutClusterTest`/`WorkoutClustersViewModelTest`/`EditWorkoutClusteringTest` (`21` suites, `123/123`) | **PASSED** | `Verified` |
| `REQ-PRO-130.3` | `TST-PRO-130.3` | `ArchitectureConformanceContractTest` (`testDatabaseLayer_noUpwardUiOrComposeImports`, `testRatchetingTripwire_detectsForbiddenDatabaseUpwardImport`) | **PASSED** | `Verified` |
| `REQ-PRO-130.4` | `TST-PRO-130.4` | `SourceTextReadingAuditContractTest` (`4/4`), `DetailedDesignParityContractTest` (`3/3`), `:app:lintDebug` (`0` errors), `verify_requirement_governance.py` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence
### Clean-Room Batched Regression (`--max-workers=1 --rerun-tasks`)
* **Command**: `./gradlew :app:testDebugUnitTest --tests "...ArchitectureConformanceContractTest" --tests "...InterfaceSignatureDriftContractTest" --tests "...AspiceSweAuditContractTest" --tests "...SystemArchitectureDesignContractTest" --tests "...NonFunctionalRequirementsContractTest" --tests "...DetailedDesignParityContractTest" --tests "...SourceTextReadingAuditContractTest" --tests "...DeadCodeEliminationContractTest" --tests "...WorkoutRepository*Test" --tests "...WorkoutCluster*Test" --tests "...EditWorkoutClusterTest" --tests "...WorkoutClustersViewModelTest" --tests "...EditWorkoutClusteringTest" --max-workers=1 --rerun-tasks`
* **Aggregate XML Results (`$WT/app/build/test-results/testDebugUnitTest/`, refreshed `2026-11-18 11:39:19`)**: `48` XML suites, `247` tests: **`246` PASS, `1` FAIL, `0` ERR, `0` SKIP**.
  * **`47/47` `ATT-8202`-scoped contract & component suites**: **`242/242` PASS (`0` FAIL, `0` ERR, `0` SKIP)**.
  * **Sole failure (pre-existing & outside `ATT-8202` scope)**: `DeadCodeEliminationContractTest.testComposePreviewsVisibilityAudit` (`4/5` PASS, `1` FAIL) due to `internal @Preview` functions in untouched `EditDeviceDialog.kt:1598..1649` (introduced in `3376bb3f`, `ATT-8450`).
* **Compile & Lint**: `./gradlew :app:compileDebugKotlin --max-workers=1 --rerun-tasks` -> `BUILD SUCCESSFUL` (`0` new compiler warnings across `WorkoutRepository.kt:4031`, `ClusterWorkoutDataPort.kt`, `ClusterWorkoutDataAdapter.kt`, `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, `WorkoutClustersViewModel.kt`); `./gradlew :app:lintDebug --max-workers=1` -> `BUILD SUCCESSFUL` (`0 errors, 989 warnings, 22 hints`, `139` baseline-filtered).

### Governance & Living Docs Verification
* `python3 tools/verify_requirement_governance.py --repo-root . --sync-indexes`: Synchronized `docs/requirements.md` (`1245` total, `1001` Verified, `243` Implemented, `1` Planned) and `docs/tests.md` (`1252` total, `1030` Verified, `214` Implemented, `8` Planned).
* `python3 tools/verify_requirement_governance.py --repo-root . --check-size --check-doc-headers --check-conflict-markers --check-living-doc-layout`: **PASS (`exit 0`)** (`28/28` shards `<= 4,000` lines, `61` governed docs, `0` conflict markers).
* `python3 tools/verify_requirement_governance.py --repo-root .`: **PASS (`exit 0`)**.
* `python3 tools/verify_requirement_governance.py --repo-root . --check-verified-evidence`: Zero errors on `TST-PRO-130` / `REQ-PRO-130`; exits `1` solely on two pre-existing sprint baseline items outside `ATT-8202` (`[TST-ARC-003]` via `EditDeviceDialog.kt` `DeadCodeEliminationContractTest` and `[TST-TRK-087]` in `tst_tracking_cockpit.md` from `ATT-8717`/`ATT-8712`).

---

## 4. Hardware / Physical Verification
Pure architecture, repository cache-bounding, and contract-test verification (`Localization Parity: N/A - no UI strings`; zero APK UI changes). Physical device (`Pixel 10`) deployment is reserved exclusively for Sprint-End Ceremony 2 (`Rule 7.6`).

### Visual Consistency (Rule 23)
No UI changes.

---

## 5. Invariant & Governance Verification
1. **Monolith LoC Ceilings (`REQ-PRO-129.2`)**: `WorkoutRepository.kt` = `5,048 <= 5,117` LoC; `WorkoutClusterEngine.kt` = `1,634 <= 1,635` LoC; `ClusterWorkoutDataPort.kt` = `319` LoC; `ClusterWorkoutDataAdapter.kt` = `45` LoC.
2. **Governance & Branch Isolation**: `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `app/source-reading-baseline.txt == 105`, `ANT-Android-SDKs` untouched, `feature/ATT-8202` kept intact (not merged onto `sprint/2026-11-18_01_Früh`).
3. **Parent Human Gate**: Parent ticket transitions strictly via Stage 6 Cold Review to `Final Review (Human)` (`human` owns `Erledigt`).
