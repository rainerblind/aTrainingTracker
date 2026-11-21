# Stage 5 Verification & Walkthrough: ATT-6170 — Preserve `exportStatuses` in `WorkoutRepository.loadWorkout`

**Ticket**: `ATT-6170` (`1 SP`, `Bug`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469)  
**Living Docs**: `REQ-DAT-046` (`[Verified]`), `TST-DAT-042` (`[Verified]`) | **SWE.2 Architecture**: `docs/architecture.md` unchanged (`Rule 8.8`)  
**Branch**: `feature/ATT-6170` (preserved intact for Stage 6 Cold Review per `Rule 3.3`; verified code SHA `36609fff`) | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Delivered Changes (`36609fff`)
* **Lossless Cache Merge in `WorkoutRepository.loadWorkout` (`REQ-DAT-046.1`–`.3`)**:
  * Retained `radarTrafficSummary` enrichment (`:3721-3726`) and `_initialWorkoutLoaded.postValue(workout)` (`:3727`), and replaced the manual `_allWorkouts` overwrite (`newList[index] = workout`) with `addOrUpdateWorkout(workout)` (`:4363-4396`).
  * Reduced `WorkoutRepository.kt` from `4,997` to **`4,986` LoC** (`-11` LoC, `131` LoC under `5,117` ceiling in `ArchitectureConformanceContractTest`).
  * Preserves cached `exportStatuses`, `mapPolyline`, `encodedAltitudes`, `encodedDistances`, and `extremaRows` (`minLatLng`/`maxLatLng`) on cache hit while propagating refreshed DB fields (`clusterId`, `clusterName`, `workoutName`, `sportId`, `equipmentId`, `radarTrafficSummary`) and inserting uncached workouts descending by `headerData.startTimeS` under pure CAS `_allWorkouts.update`.
* **Unit Regression Coverage (`TST-DAT-042.1`–`.3`)**:
  * Added `testLoadWorkout_cachedWorkout_preservesExportStatusesAndTransientFieldsWhileUpdatingDbFields_TST_DAT_042_1` and `testLoadWorkout_uncachedWorkout_insertsSortedDescendingAndEmitsInitialWorkout_TST_DAT_042_2` in `WorkoutRepositoryExportStatusTest.kt`.
  * **Bug-Fix Verification Evidence (`REQ-PRO-121.2`)**: Pre-fix `loadWorkout` replaced `_allWorkouts[index]` with `fromCursor`'s `exportStatuses = emptyList()`, whereas post-fix `TST_DAT_042_1` asserts Strava/Dropbox/Google Drive `exportStatuses`, polyline/altitude/distance streams, and extrema `LatLng` remain intact alongside updated cluster/sport/gear fields and `_initialWorkoutLoaded`; `TST_DAT_042_2` confirms uncached descending insertion.
* **Localization & Submodule Hygiene**: `0` `values*/strings*.xml` edits (`Localization Parity: N/A`); `ANT-Android-SDKs` untouched at `160000 0e8cea71`.

## 2. Verification Evidence (`Rule 7.5` Doc-Only Fast Path — Zero `app/` Edits Since `36609fff`)
| Check / Suite | Scope | Result | Evidence / Reference |
|---|---|---|---|
| **Gate 4 Tier-1 Targeted (`ATT-9533`)** | `WorkoutRepositoryExportStatusTest` (`3`), `WorkoutRepositoryBatchLoadTest` (`1`), `WorkoutDataMappingSymmetryTest` (`10`), `ArchitectureConformanceContractTest` (`11`) | **25/25 PASS** (`0` fail, `0` error, `0` skip) | `@ 36609fff` (reused per `Rule 7.5`) |
| **Python Safety Linters** | `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `check_secrets_hygiene.py` | **4/4 PASS** (`exit 0`) | `@ 36609fff` |
| **Living Docs & Traceability** | `verify_requirement_governance.py --sync-indexes --check-verified-evidence --check-living-doc-layout` | **PASS** (`exit 0`, `0` unbacked) | `REQ-DAT-046` & `TST-DAT-042` `[Verified]` |
| **Live Emulator (`emulator-5554`)** | Deferred to independent Stage 6 Cold Review (`Rule 3.3`) | Pending Stage 6 | `feature/ATT-6170` preserved intact |

## 3. Branch & Handover State
* `feature/ATT-6170` remains intact and unmerged (`0` `app/` edits since `36609fff`), ready for Gate 5 audit and Stage 6 Cold Review (`Rule 3.3` / `Rule 8.2`).
