# Stage 1 Analysis: ATT-7748 - Add Auto-Import tab and automatic Dropbox and Google Drive workout sync for secondary devices

**Ticket**: [ATT-7748](https://rainerblind.atlassian.net/browse/ATT-7748)  
**Sub-task**: [ATT-7809](https://rainerblind.atlassian.net/browse/ATT-7809) (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7748`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Athletes using secondary recording devices (e.g., dedicated handlebar bike-phones) currently face friction getting activities onto their primary phone (where sleep, recovery, and Health Connect aggregate data) or tablet. Currently, importing from Dropbox or Google Drive requires navigating to the Import tab, manually launching a bulk recovery scan, and interacting with cluster route dialogs. There is no automated background sync on app start or workout list refresh, no dedicated configuration tab for multi-device auto-import, and no automatic relay to local Health Connect or Strava on the receiving device.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Gap Analysis**:
  1. `ImportBackupTabsScreen.kt` provides 3 tabs (`Import`, `Backup`, `Restore`). No dedicated multi-device auto-import settings tab exists.
  2. `LegacyImportEngine.kt` bulk recovery methods (`bulkRecoverFromDropbox`, `bulkRecoverFromGoogleDrive`) support recovery, but `scheduleCommunityUploads` explicitly skips `FileFormat.HEALTH_CONNECT` (`if (format == FileFormat.HEALTH_CONNECT) continue`) and lacks an automated background trigger.
  3. No background `HealthConnectWorkoutSyncWorker` exists to relay imported workouts asynchronously to local Android Health Connect via `WorkManager`.
  4. `WorkoutSummariesTabbedScreen.kt` loads workouts on launch (`loadWorkoutsIfNeeded()`) but does not check for cloud auto-import deltas.
- **Independent Peer Review & Verification**:
  - Verified `ImportBackupTabsScreen.kt:117` (`pageCount = { 3 }`) and `tabs` list: inserting Auto-Import at index 1 shifts Backup to index 2 and Restore to index 3, maintaining exact tab order `1. Import | 2. Auto-Import | 3. Backup | 4. Restore`.
  - Audited `LegacyImportEngine.kt:221-226` and `440-445`: `ExistingWorkoutsSnapshot` pre-checks `fileBaseName` prior to remote downloading, preventing wasted network bandwidth for existing workouts.
  - Confirmed `HealthConnectManager.syncWorkout(...)` handles `ExportStatusDatabaseManager` state (`PROCESSING`, `FINISHED_SUCCESS`, `FINISHED_FAILED`) and broadcast triggers; encapsulating this in `HealthConnectWorkoutSyncWorker` allows reliable background execution.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add dedicated **Auto-Import** tab at index 1 (second position) in `ImportBackupTabsScreen.kt`.
  2. Configure master switch, independent Dropbox and Google Drive toggles, file format selector (default `.fit`, with `.tcx`, `.gpx`, `all`), Health Connect relay toggle, Strava relay toggle, and "Sync Now" button.
  3. Incremental non-interactive cloud sync engine in `AutoImportManager` that queries configured cloud folders, filters against `ExistingWorkoutsSnapshot` before download, and imports without blocking dialogs.
  4. Enqueue `HealthConnectWorkoutSyncWorker` and schedule Strava upload upon auto-importing new activities.
  5. Cooldown-gated auto-import check on app launch / workout list screen open.
* **Out-of-Scope Non-Goals**:
  - Lossless FIT developer field enhancements (radar/dynamics) covered under companion ticket.
  - Two-way database mirroring or cloud deletion syncing.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-MIG-052` extending `REQ-MIG-031` (*Deduplication*), `REQ-MIG-034` (*Google Drive Recovery*), `REQ-MIG-035` (*Dropbox Recovery*), `REQ-MIG-050` (*In-Memory Snapshot*), and `REQ-SET-103` (*Health Connect Sync*).
* **Historical Origin & Commit Trace**: `ATT-314` (deduplication baseline), `ATT-4664` (cloud restore parity), `ATT-5902` (`ExistingWorkoutsSnapshot`).
* **Root Reason for Existing Formulation**: Recovery was built for manual disaster recovery, requiring explicit user interaction. Multi-device daily riding workflows require automated, non-interactive incremental synchronization.
* **Preservation of Core Invariants**: Existing manual bulk recovery and backup restoration remain intact. Zero duplicate workouts created; zero unauthenticated background polling.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-MIG-052` (`REQ-MIG-052.1`–`052.5`).
* **Affected Architecture Elements**: `ImportBackupTabsScreen.kt`, `BackupRestoreViewModel.kt`, `AutoImportManager.kt`, `HealthConnectWorkoutSyncWorker.kt`, `TrainingApplication.java`, `strings_auto_import.xml` across all 9 locales.
* **Verification Impact & Test Surface**: Net-new `TST-MIG-050` (`AutoImportContractTest`, `AutoImportManagerTest`, `HealthConnectWorkoutSyncWorkerTest`, `TranslationParityTest`).
* **Risk Assessment & Invariants**: Low risk. Isolated to migration and cloud sync. In-memory snapshot pre-filtering guarantees zero unnecessary file downloads.

---

## 6. Architectural Strategy & High-Level Solution
- Create `AutoImportManager.kt` as an encapsulated singleton managing preference persistence (`TrainingApplication` / `SharedPreferences`), cooldown timestamps, and non-interactive incremental sync routines.
- Implement `HealthConnectWorkoutSyncWorker.kt` (`CoroutineWorker`) providing `enqueue(context, workoutId)` to relay newly imported activities to Health Connect asynchronously.
- Update `ImportBackupTabsScreen.kt` with a 4-tab pager (`Import`, `Auto-Import`, `Backup`, `Restore`), rendering Material 3 card controls with real-time cloud account status.
- Add `strings_auto_import.xml` across all 9 locales for 100% localization parity.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Deduplication invariant: No duplicate workouts inserted into `WorkoutSummariesDatabaseManager`.
  2. Tab order invariant: Exact 4-tab sequence `Import | Auto-Import | Backup | Restore`.
  3. Security & battery invariant: Zero background polling when Auto-Import is disabled; cooldown prevents rapid successive cloud calls.
* **Risk Rating**: **LOW** (additive feature with clean component boundaries and zero impact on live GPS recording).
