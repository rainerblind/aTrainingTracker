# Stage 5: Walkthrough & Verification - ATT-9018: Fix Bulk Strava Import OOM from Climbs.db Double-Deserialization & Per-Workout Reloads

**Ticket**: [ATT-9018](https://rainerblind.atlassian.net/browse/ATT-9018) (`3 SP`, `Bug`, `severity-critical`) | **Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-19_01_Früh` | **Branch**: `feature/ATT-9018` | **Status**: `Verified`  
**Requirement Mapping**: `REQ-STB-021` (`REQ-STB-021.1`–`.5`) | **Test Mapping**: `TST-STB-021` (`TST-STB-021.1`–`.4`)  
**Author**: AI Agent 1 (`agent1`, `Pro`) | **Date**: 2026-11-19

---

## 1. Executive Summary & Implemented Architecture
* **Zero-Split Lazy `Climbs.db` Deduplication (`REQ-STB-021.1`)**: Updated [`ClimbsDatabaseManager.kt`](../../../app/src/main/java/com/atrainingtracker/trainingtracker/database/ClimbsDatabaseManager.kt) (`deserializePathPoints` zero-split index scanning; 8-column `METADATA_COLUMNS` queries + cached lazy `POLYLINE_ONLY_COLUMN` reads for both climbs only on `NEEDS_CORRIDOR_CHECK`), [`ClimbDeduplicator.kt`](../../../app/src/main/java/com/atrainingtracker/trainingtracker/climbs/ClimbDeduplicator.kt) (`MetadataPreFilterResult`, triangle-inequality ellipse bounding-box slack `buildConservativeBoundingBox`, `preFilterMetadata`), and [`ClimbDetector.kt`](../../../app/src/main/java/com/atrainingtracker/trainingtracker/climbs/ClimbDetector.kt) (`.toList()` sublist detachment).
* **`LiveClimbsRepository` List Sharing (`REQ-STB-021.2`)**: Added `Mutex` serialization, no-op category copy skip (`withResolvedCategory`), and shared single `deduplicatedStored` reference between `_allStoredClimbs` and `_allClimbs` on free-riding (and `applyFormula`) in [`LiveClimbsRepository.kt`](../../../app/src/main/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepository.kt).
* **Climb Coordinator Extraction & Bulk Batch Deferral (`REQ-STB-021.3`, `.5`)**: Extracted [`WorkoutClimbIngestionCoordinator.kt`](../../../app/src/main/java/com/atrainingtracker/trainingtracker/climbs/WorkoutClimbIngestionCoordinator.kt) into `climbs/` (shrinking [`WorkoutRepository.kt`](../../../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt) to `4,931 <= 4,973` LoC), updated `backfillHistoricalClimbs` to single post-loop refresh, dropped duplicate `WORKOUT_UPDATED_INTENT` after direct `reloadWorkoutData` (`app != null`) in [`LegacyImportEngine.kt`](../../../app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt), and introduced coroutine-scoped [`BulkImportBatchContext.kt`](../../../app/src/main/java/com/atrainingtracker/trainingtracker/migration/BulkImportBatchContext.kt) across `StravaBulkExportImporter`, `BackupRestoreViewModel` (`importStravaArchive`, multi-file `importWorkoutFilesInternal`, Dropbox/Drive recovery), `LegacyImportEngine` bulk recovery, and `AutoImportManager` with single post-batch reconciliation (`TST-MIG-023` single-file immediacy intact).
* **Cached `DateFormat` Helper (`REQ-STB-021.4`)**: Added `ThreadLocal` `(Locale, TimeZone)`-keyed `DateFormat.getDateTimeInstance()` cache in [`LastActiveDateFormatter.kt`](../../../app/src/main/java/com/atrainingtracker/banalservice/database/LastActiveDateFormatter.kt) (`0` `.ui.*` imports) and delegated `getLastActiveString()` in [`DevicesDatabaseManager.java`](../../../app/src/main/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManager.java) (`1,669 <= 1,671` LoC).

---

## 2. Requirement & Test Verification Matrix (`Rule 7.5` Reused Stage 4 + Gate 4 Evidence)

| Requirement | Test Spec | Verified Scope (Unchanged Stage 4 Code/Tests Baseline) | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-STB-021.1` | `TST-STB-021.1` | `ClimbsDatabaseManagerTest` (`10/10`), `ClimbDetectorTest` (`9/9`), `ClimbDeduplicatorTest` (`8/8`) | **PASSED** | `Verified` |
| `REQ-STB-021.2` | `TST-STB-021.2` | `LiveClimbsRepositoryTest` (`20/20`) | **PASSED** | `Verified` |
| `REQ-STB-021.3` | `TST-STB-021.3` | `WorkoutHistoricalClimbBackfillTest` (`6/6`), `WorkoutClimbIngestionTest` (`4/4`), `TcxImportPeriodAndClusterIntegrationTest` (`5/5`), `StravaBulkExportImporterTest` (`6/6`), `BackupRestoreViewModelFitTest` (`6/6`), `AutoImportManagerTest` (`4/4`), `WorkoutRepository*` (`19` suites) | **PASSED** | `Verified` |
| `REQ-STB-021.4` | `TST-STB-021.4` | `DevicesDatabaseManagerLastActiveTest` (`4/4`), `DevicesDatabaseManagerTest` (`14/14`), `DeviceStatusRowContractTest` (`3/3`), `DeviceSortOrderTest` (`6/6`) | **PASSED** | `Verified` |
| `REQ-STB-021.5` | `TST-STB-021.4` | `ArchitectureConformanceContractTest` (`11/11`), `DetailedDesignParityContractTest` (`3/3`), `InterfaceSignatureDriftContractTest` (`3/3`), `SourceTextReadingAuditContractTest` (`4/4`), `AspiceSweAuditContractTest` (`13/13`), `UiReflectionOnlyTestAuditContractTest` (`4/4`), `TryWithResourcesCursorAuditTest` (`5/5`), `SafeEnumDeserializationAuditTest` (`1/1`), `LifecycleServiceReceiverSafetyAuditTest` (`1/1`), `TranslationParityTest` (`10/10`) | **PASSED** | `Verified` |

---

## 3. Automated Evidence, Governance & Verification Boundaries
* **Stage 4 Targeted + Contract Verification (`--rerun-tasks --console=plain`)**: **`105` suites, `517` tests, `0` failures, `0` errors, `0` skipped** (`0` new compiler warnings). Reused directly in Stage 5 per **`Rule 7.5`** (zero production/test code changes since Gate 4).
* **Gate 4 Independent Re-Verification (`ATT-9072`)**: **`14` suites, `110` tests, `0` failures, `0` errors, `0` skipped**, `0` warnings.
* **LoC, Baselines & Governance**: `WorkoutRepository.kt = 4,931` (`<= 4,973`), `DevicesDatabaseManager.java = 1,669` (`<= 1,671`); `check_static_analysis_baselines.py` PASS (`detekt=9509`, `lint=238`); `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` PASS; `verify_requirement_governance.py --repo-root . --base-ref sprint/2026-11-19_01_Früh` PASS (`exit 0`).
* **UI, Localization & Physical/Emulator Boundary**: `UI Consistency: N/A`; `Localization Parity: N/A - no UI strings`. Per `Rule 7.6`, live `emulator-5554` runtime verification executes in Stage 6, and end-to-end 3,287-row Strava bulk archive stress acceptance on physical Pixel 10 remains PO acceptance at Joint Review.
