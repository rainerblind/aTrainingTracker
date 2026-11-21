# Stage 5 Verification & Walkthrough: ATT-6038 - BatchMetadataLoader & Export-Status N+1 Elimination

**Ticket**: [ATT-6038](https://issue-tracker/ATT-6038) (`3 SP`) | **Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033)  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Sprint**: `2026-11-21_01_Früh` (Sprint 468)  
**Living Docs**: `REQ-DAT-045` (`[Verified]`), `TST-DAT-041` (`[Verified]`) | **SWE.2 Architecture**: `docs/architecture.md` (`v1.0.19`)  
**Branch**: `feature/ATT-6038` (preserved intact for Stage 6 Bounce 1 Cold Re-Audit per `Rule 3.3`; verified code SHAs `df4b48ee`, `34ef13d5`) | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Delivered Changes (`df4b48ee` + Bounce 1 `34ef13d5`)
* **Chunked Batch Export & Strava Queries (`REQ-DAT-045.1`, `.2`)**:
  * Removed both dead `getExportStatusMap` overloads from `ExportStatusDatabaseManager.java`; added `getExportRowsForWorkouts(Collection<String>)` with `<= 500` bind-arg chunking, single upfront `healStaleProcessingRows()`, and `try (Cursor ...)` safety (`lint_cursor_safety.py` PASS).
  * Encapsulated via domain DTO `ExportStatusEntry` (`ExportStatusGroupData.kt`) and `ExportStatusRepository.getExportStatusEntriesForWorkouts` (zero `*DatabaseManager`/`Cursor` getters, `Rule 8.7`); added `ExportStatusDataProvider.createGroupDataFromEntries` / `createGroupDataFromRows`.
  * Chunked `StravaUploadDbHelper.getStravaActivityDataForWorkouts` bind args at `<= 500` (`<= 999` SQLite ceiling).
* **Shared `BatchMetadataLoader` (`REQ-DAT-045.3`, `Rule 8.8`)**:
  * Added `com.atrainingtracker.trainingtracker.repositories.BatchMetadataLoader` (`repositories/`, `0` net-new `ui/*` DB callers) loading all 9 `BatchMetadata` maps in $O(1)$ batch queries (including single `KnownLocations` geofence scan and `AthleteMetricsDatabaseManager.getEntriesByType` history + `SettingsDataStore` fallback matching `resolveEffectiveFtpAt`/`resolveEffectiveWeightKgAt`).
  * Added `hasEquipmentNames`, `hasLocationNames`, and `hasCardioPowerDynamics` flags on `WorkoutDataMapper.BatchMetadata` to prevent per-workout DB fallbacks on empty/unmatched rows.
  * Wired into `WorkoutRepository.loadAllWorkouts` (`WorkoutRepository.kt` `4,997 <= 5,117` LoC), `PeriodsRepository` (`getWorkoutsForRange`, `performHierarchicalMigration`), and `WorkoutClusterRepository`; documented in `docs/architecture.md` (`v1.0.19`).
* **Lossless `fromCursor` Delegation (`REQ-DAT-045.4`)**:
  * Replaced duplicated 140-line `WorkoutDataMapper.fromCursor` body with `readCursorSnapshot` + `loadForSingleWorkout` (`5` batch maps + `hasCardioPowerDynamics = true`, indexed single-item resolvers for the remaining `4`) + `fromSnapshot(..., reconcile = true)`, preserving `REQ-DAT-009`/`REQ-MAP-020` self-healing and `ATT-6170` `loadWorkout` boundaries.
* **Stage 6 Bounce 1 Remediation (`34ef13d5`, Gate 4 `ATT-9463` PASS)**:
  * Fixed Kotlin warning on `ExportStatusBatchQueryTest.kt:98` (`MockKMatcherScope.isNull(inverse = true)` -> `0` compiler warnings).
  * Added `BatchMetadataLoaderTest` coverage for `loadForSnapshots(emptyList())` and `loadForSingleWorkout(snapshot)` (`3/3 PASS`).
  * Grounded `req_data_migration_export_part2.md` (`v1.0.12`), `tst_data_migration_export.md` (`v1.0.13`), and `docs/requirements.md` Part 2 Prefixes (`DAT, EXP`) against live code (`Rule 8.7`).

## 2. Verification Evidence (`Rule 7.5` Doc-Only Fast Path — Zero `app/` Edits Since `34ef13d5`)
| Check / Suite | Scope | Result | SHA / Evidence |
|---|---|---|---|
| **Gate 4 Bounce 1 Tier-1 (`ATT-9463`)** | 6 suites (`ExportStatusBatchQueryTest` 2, `ExportStatusDataProviderTest` 6, `BatchMetadataLoaderTest` 3, `WorkoutRepositoryBatchLoadTest` 1, `WorkoutDataMappingSymmetryTest` 10, `ArchitectureConformanceContractTest` 11) | **33/33 PASS**, `0` warnings (`0` fail, `0` skip) | `@ 34ef13d5` (reused per `Rule 7.5`) |
| **Gate 4 Initial Tier-1 (`ATT-9452`)** | Full 18 targeted suites (`ExportStatus*`, `BatchMetadataLoaderTest`, `WorkoutRepository*`, `WorkoutDataMap*`, `WorkoutCluster*`, `PeriodsRepository*`, `ArchitectureConformanceContractTest`, `TryWithResourcesCursorAuditTest`) + `3/3` linters (`lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`) | **99/99 PASS**, `3/3` linters (`exit 0`) | `@ df4b48ee` (reused per `Rule 7.5`) |
| **Live Emulator Smoke (`emulator-5554`)** | Stage 6 Cold Review on-device verification | `28` real workouts via `BatchMetadataLoader` in `375 ms` | Stage 6 `ATT-6038` |
| **Living Docs & Traceability** | `verify_requirement_governance.py --base-ref sprint/2026-11-21_01_Früh --check-living-doc-layout --check-verified-evidence` | **PASS** (`exit 0`) | `REQ-DAT-045` & `TST-DAT-041` `[Verified]` |

## 3. Branch & Handover State
* `feature/ATT-6038` remains intact and unmerged (`0` files touched under `app/` since `34ef13d5`); `ANT-Android-SDKs` untouched at `160000 0e8cea71`. Ready for Gate 5 Bounce 1 audit and Stage 6 Bounce 1 Cold Re-Audit.
