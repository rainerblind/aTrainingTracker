# Stage 1 Analysis: ATT-7670 - Track and Display Health Connect Export Status Under Community

**Ticket**: [ATT-7670](https://rainerblind.atlassian.net/browse/ATT-7670)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud Integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7670` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
* Currently, `ExportType.COMMUNITY` in `ExportType.java` only lists `FileFormat.STRAVA`.
* When workouts are synced to Health Connect (`HealthConnectManager.syncWorkout`), no row is initialized or updated in `ExportStatus.db` (`ExportStatusDatabaseManager`), and no status broadcast is sent via `ExportStatusChangedBroadcaster`.
* Athletes have zero visibility on workout cards or in `ExportDetailsDialog` regarding Health Connect sync state (`WAITING`, `PROCESSING`, `FINISHED_SUCCESS`, or `FINISHED_FAILED` with error answers).
* Manual sync from workout overflow menus also leaves export status unrecorded and UI unrefreshed.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
* **Root Cause Analysis (RCA)**:
  * `FileFormat.java`: Lacks `HEALTH_CONNECT` in enum and `ONLINE_COMMUNITIES` array.
  * `ExportType.java`: `COMMUNITY` only declares `new FileFormat[]{FileFormat.STRAVA}`.
  * `TrainingApplication.java`: `uploadToCommunity(FileFormat)` does not map `HEALTH_CONNECT` to `syncToHealthConnect()`.
  * `HealthConnectManager.kt`: `syncWorkout` performs SDK insertions but never updates `ExportStatusDatabaseManager` or emits `ExportStatusChangedBroadcaster.broadcastExportStatusChanged`.
  * `ExportStatusDataProvider.kt` & `ExportStatusRepository.kt`: Dynamic format synthesis only considers formats in `exportType.exportToFileFormats`.
* **Independent Peer Review & Verification**:
  * Audited `ExportStatusDatabaseManager.updateExportStatus`: supports upsert (inserts row if `update == 0`), ensuring legacy workouts without pre-existing `(COMMUNITY, HEALTH_CONNECT)` rows persist status cleanly.
  * Audited `ExportStatusDataProvider`: `getPluralIdsFor(ExportType.COMMUNITY)` already uses `R.plurals.export_notification__detail__Community_*` and `formatListAsString`, cleanly formatting "Strava and Health Connect" when both are active.
  * Audited `ExportManager.java:80-100`: `getExporter` switch must handle `HEALTH_CONNECT` cleanly to avoid runtime `IllegalStateException`.
  * Audited `ExportManager.java:213-220`: in `exportWorkout()`, `startFullExportProcess` must skip `HEALTH_CONNECT` since sync is handled directly via `HealthConnectManager.syncWorkoutAsync`.

---

## 3. User Scope Grounding & Complexity Split Guardrail (ATT-1250 / REQ-PRO-029)
* **In-Scope Goals**:
  1. Add `FileFormat.HEALTH_CONNECT` (`ONLINE_COMMUNITIES`) and include it in `ExportType.COMMUNITY`.
  2. Wire `TrainingApplication.uploadToCommunity(HEALTH_CONNECT)` to `syncToHealthConnect()`.
  3. Initialize `(COMMUNITY, HEALTH_CONNECT)` in `ExportManager.newWorkout()`.
  4. In `HealthConnectManager.kt`, record transitions (`PROCESSING`, `FINISHED_SUCCESS`, `FINISHED_FAILED` with error text in `ANSWER`) and broadcast via `ExportStatusChangedBroadcaster`.
  5. Synthesize `HEALTH_CONNECT` as `UNWANTED` for legacy workouts in `ExportStatusDataProvider` / `ExportStatusRepository`.
  6. Unit test coverage in `ExportStatusDataProviderTest.kt` and `HealthConnectSyncTest.kt`.
* **Out-of-Scope Non-Goals**: Per-workout checkbox in `EditWorkoutDialog` and `ExerciseRoute` timestamp fix (separate bug tickets).
* **Complexity Rating**: 2 SP (Standard / DB status integration + Broadcaster + UI synthesis). Splitting not required.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Net-new requirement `REQ-EXP-023` (*Health Connect Export Status Tracking & Community Display*), extending `REQ-EXP-018` and refining `REQ-SET-103`.
* **Historical Origin & Commit Trace**: `ATT-3052` added Health Connect background sync without tying into `ExportStatusDatabaseManager`; `ATT-2310` (`REQ-EXP-018`) established missing-format synthesis and safe DB upserts.
* **Root Reason for Existing Formulation**: Health Connect was originally created as a standalone cloud sync sidecar rather than a registered community destination.
* **Preservation of Core Invariants**: Strava upload behavior, WorkManager worker pipeline (`ExportWorker`), existing DB schema, and 100% 9-language localization parity preserved.

---

## 5. Side-Effect & Cross-Caller Impact Analysis (Rule 68)
* `ExportType.COMMUNITY.getExportToFileFormats()`: All consumers (`ExportStatusDataProvider`, `ExportStatusRepository`, `ExportManager`, `LegacyImportEngine`) now include `HEALTH_CONNECT`.
* `ExportManager.exportWorkout()`: Direct `HealthConnectManager.syncWorkoutAsync` call is preserved; WorkManager file queue is not spammed.
* `ExportDetailsDialog`: Dynamically renders Health Connect status row under Community with error message inspection.

---

## 6. Change Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-EXP-023` in `docs/requirements/req_data_migration_export.md`, refining `REQ-SET-103`.
* **Affected Architecture Elements**: `FileFormat.java`, `ExportType.java`, `ExportManager.java`, `HealthConnectManager.kt`, `ExportStatusDataProvider.kt`, `ExportStatusRepository.kt`.
* **Verification Impact & Test Surface**: `ExportStatusDataProviderTest`, `HealthConnectSyncTest`, `ExportManagerFitTest`, `ThemeColorCentralizationAuditTest`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Medium risk due to `FileFormat` enum exhaustiveness in Java switches. Verified compiler coverage for all switch expressions.

---

## 7. Living Documentation Plan
* Add `REQ-EXP-023` to `docs/requirements/req_data_migration_export.md` and `TST-EXP-020` to `docs/tests/tst_data_migration_export.md`.
