# Stage 3 Implementation Plan: ATT-6039 - StravaBulkExportImporter & LegacyImportEngine: Unify bulk import processing order to chronological ascending (oldest first)

**Ticket**: [ATT-6039](https://rainerblind.atlassian.net/browse/ATT-6039)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-MIG-049` (`049.1`–`049.5`)  
**Test Spec Mapping**: `TST-MIG-047` (`047.1`–`047.5`)  
**Branch**: `feature/ATT-6039`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Technical Architecture & SWE.2 Design
* **Decomposition & Boundaries**:
  - `StravaBulkExportImporter`: In-memory sorting of parsed `StravaActivityRow` list prior to the sequential import loop.
  - `SafDirectoryScanner`: Recursive SAF document tree discovery with case-insensitive lexicographical/chronological URI sorting.
  - `BackupRestoreViewModel`: UI-facing multi-URI batch pre-sorting using canonical `ImportFileValidator.resolveDisplayName` (Rule 36 DRY).
  - `LegacyImportEngine`: Sorting deduplicated cloud entries ascending prior to concurrent worker channel dispatch.
* **Primitive Sorting Invariant (Rule 1.3)**: Strava activities evaluate raw 64-bit numeric IDs (`activityId.toLongOrNull() ?: Long.MAX_VALUE`), preserving deterministic monotonic order regardless of display formatting or export language.
* **Sequential Cluster & DB Invariant (`REQ-MIG-049.5`)**: Chronological ascending processing ensures earliest activities receive cluster `#1` in `WorkoutClusterEngine` and lower SQLite autoincrement `_id` in `WorkoutSummaries.db`.
* **Synergy with ATT-5902**: Clean ascending sorting enables subsequent in-memory snapshot fast-forwarding without stream rewinds.

---

## 2. Atomic Implementation Steps

| Step | Component | Target File | Action & Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Strava Importer | `StravaBulkExportImporter.kt` | Sort `activities` ascending by `activityId.toLongOrNull() ?: Long.MAX_VALUE` (fallback `reversed()`). | `REQ-MIG-049.1`<br>`TST-MIG-047.1` |
| **2** | SAF Scanner | `SafDirectoryScanner.kt` | Collect matching files and return URIs sorted by `String.CASE_INSENSITIVE_ORDER` on filename with URI tie-breaker. | `REQ-MIG-049.2`<br>`TST-MIG-047.2` |
| **3** | ViewModel Batch | `BackupRestoreViewModel.kt` | Pre-sort incoming `uris` ascending by resolved filename via `ImportFileValidator.resolveDisplayName` before import loop. | `REQ-MIG-049.3`<br>`TST-MIG-047.3` |
| **4** | Cloud Recovery | `LegacyImportEngine.kt` | Sort deduplicated `entries` in `bulkRecoverFromDropbox` and `bulkRecoverFromGoogleDrive` ascending by filename. | `REQ-MIG-049.4`<br>`TST-MIG-047.3` |
| **5** | Unit Tests | `migration/*Test.kt` | Add ascending order tests in `StravaBulkExportImporterTest`, `SafDirectoryScannerTest`, and `LegacyImportEngineDeduplicationTest`. | `REQ-MIG-049.1-4`<br>`TST-MIG-047.1-3` |
| **6** | Verification | Test Suite | Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*"` and governance scripts. | `REQ-MIG-049.5`<br>`TST-MIG-047.4-5` |

---

## 3. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: No production source files in `app/src/main/` modified until Gate 3 is marked `Erledigt`.
* **Security & Deduplication**: Zip-Slip verification (`REQ-MIG-040`), `isWorkoutExisting` deduplication, and equipment synchronization remain 100% invariant.
* **Risk Rating**: **LOW** (Pre-dispatch in-memory sorting of file/activity lists; zero database schema migrations or network protocol changes).
