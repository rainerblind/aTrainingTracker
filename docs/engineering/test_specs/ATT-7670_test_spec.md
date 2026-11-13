# Stage 2: Requirement & Test Specification - ATT-7670: Track and Display Health Connect Export Status Under Community

**Ticket**: [ATT-7670](https://rainerblind.atlassian.net/browse/ATT-7670) | **Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7670`  
**Requirement Mapping**: `REQ-EXP-023` | **Test Spec ID**: `TST-EXP-020`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (`REQ-EXP-023`)

### 1.1 Attributes & Problem Statement
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- Health Connect sync (`HealthConnectManager.syncWorkout()`) operates as an unrecorded sidecar. Athletes have zero visibility on workout cards or in `ExportDetailsDialog` regarding whether Health Connect sync is waiting, in-progress, succeeded, or failed.

### 1.2 Functional & Architectural Requirements
1. `FileFormat` SHALL register `HEALTH_CONNECT("HealthConnect", "", R.string.health_connect_title)` in `ONLINE_COMMUNITIES`, and `ExportType.COMMUNITY.getExportToFileFormats()` SHALL return `{FileFormat.STRAVA, FileFormat.HEALTH_CONNECT}`.
2. `TrainingApplication.uploadToCommunity(FileFormat.HEALTH_CONNECT)` SHALL evaluate `TrainingApplication.syncToHealthConnect()`.
3. `ExportManager.newWorkout()` SHALL initialize an `ExportStatus` row for `(ExportType.COMMUNITY, FileFormat.HEALTH_CONNECT)` set to `TRACKING` if Health Connect sync is enabled, or `UNWANTED` if disabled.
4. `HealthConnectManager.syncWorkout()` SHALL record `PROCESSING` (or `WAITING`), then `FINISHED_SUCCESS` on success or `FINISHED_FAILED` with exception message in `ANSWER` on error in `ExportStatusDatabaseManager`, and SHALL broadcast `ExportStatusChangedBroadcaster.broadcastExportStatusChanged(context, fileBaseName)`.
5. `ExportStatusDataProvider` and `ExportStatusRepository` SHALL synthesize missing `HEALTH_CONNECT` rows as `UNWANTED` on legacy workouts and reactively format plural status lines and details rows for `COMMUNITY`.
6. `ExportManager.getExporter()` SHALL return a no-op exporter or throw clean `UnsupportedOperationException`, and `ExportManager.exportWorkout()` SHALL omit `FileFormat.HEALTH_CONNECT` from `startFullExportProcess()`.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* a workout and Health Connect enabled, *When* `syncWorkout()` finishes successfully, *Then* `(COMMUNITY, HEALTH_CONNECT)` is set to `FINISHED_SUCCESS` and the Community export line shows `Health Connect`.
- *Given* a Health Connect sync failure, *When* caught in `syncWorkout()`, *Then* status transitions to `FINISHED_FAILED` with error text in `ANSWER`, viewable in `ExportDetailsDialog`.
- *Given* a historical workout without Health Connect rows, *When* loaded in `ExportStatusDataProvider`, *Then* `HEALTH_CONNECT` is synthesized as `UNWANTED` without error.

### Requirement Archaeology & Chesterton's Fence Audit
- *Original Requirement ID & Target*: Net-new `REQ-EXP-023` (extending `REQ-EXP-018` and refining `REQ-SET-103`), targeting `FileFormat.java`, `ExportType.java`, `HealthConnectManager.kt`, and `ExportStatusDataProvider.kt`.
- *Historical Origin & Commit Trace*: `ATT-3052` added standalone Health Connect sync; `ATT-2310` (`REQ-EXP-018`) introduced missing-format synthesis and safe DB upserts.
- *Root Reason for Existing Formulation*: Health Connect sync was originally treated as a one-shot cloud utility rather than a first-class community upload destination.
- *Preservation of Core Invariants*: Strava TCX WorkManager upload pipeline, `ExportStatus.db` schema (no DDL migrations), and 100% 9-language translation parity preserved.

---

## 2. Test Specification (`TST-EXP-020`)

### Test Cases
- `[TST-EXP-020.1]` (Unit - `ExportStatusDataProviderTest.kt`): Verify missing `HEALTH_CONNECT` format synthesis as `UNWANTED` on legacy rows, and verify plural aggregation ("Strava and Health Connect") when both succeed.
- `[TST-EXP-020.2]` (Unit - `HealthConnectSyncTest.kt`): Verify `syncWorkout()` updates DB status to `FINISHED_SUCCESS` on completion, records `FINISHED_FAILED` + `ANSWER` on failure, and emits status change broadcast.
- `[TST-EXP-020.3]` (Unit - `ExportManagerFitTest.kt`): Verify `ExportManager.newWorkout()` initializes `HEALTH_CONNECT` as `TRACKING` when enabled and `UNWANTED` when disabled, and verify `exportWorkout()` does not enqueue WorkManager file jobs for Health Connect.
- `[TST-EXP-020.4]` (Contract - `ThemeColorCentralizationAuditTest` & Core): Verify UI cleanliness and 9-language localization parity for `health_connect_title` and `Community` plurals.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component & Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-EXP-020.1]` | Unit | `ExportStatusDataProvider.createGroupData` | `REQ-EXP-023.5` | Specified |
| `[TST-EXP-020.2]` | Unit | `HealthConnectManager.syncWorkout` | `REQ-EXP-023.4` | Specified |
| `[TST-EXP-020.3]` | Unit | `ExportManager.newWorkout` / `exportWorkout` | `REQ-EXP-023.1..3,6` | Specified |
| `[TST-EXP-020.4]` | Contract | Localization & Design Conformance | `REQ-EXP-023.6` | Specified |
