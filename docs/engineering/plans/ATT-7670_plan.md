# Stage 3 Implementation Plan - ATT-7670: Track and Display Health Connect Export Status Under Community

**Ticket**: [ATT-7670](https://rainerblind.atlassian.net/browse/ATT-7670)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud Integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-EXP-023` | **Test Spec ID**: `TST-EXP-020`  
**Branch**: `feature/ATT-7670` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Architecture & Component Decomposition (SWE.2)
- **Layer & Component Boundaries**:
  - `exporter/FileFormat.java`: Register `HEALTH_CONNECT("HealthConnect", "", R.string.health_connect_title)` in enum and include in `ONLINE_COMMUNITIES`. Handle MIME type in `getMimeType()`.
  - `exporter/ExportType.java`: Include `FileFormat.HEALTH_CONNECT` in `COMMUNITY.exportToFileFormats` (`{FileFormat.STRAVA, FileFormat.HEALTH_CONNECT}`).
  - `TrainingApplication.java`: In `uploadToCommunity()`, route `FileFormat.HEALTH_CONNECT` to `syncToHealthConnect()`; in `exportToFile()`, return `false` for `HEALTH_CONNECT`.
  - `exporter/ExportManager.java`: In `newWorkout()`, initialize `(COMMUNITY, HEALTH_CONNECT)` to `TRACKING` (if enabled) or `UNWANTED`. In `exportWorkout()`, exclude `HEALTH_CONNECT` from file queue loop. In `getExporter()`, return `NotYetImplementedExporter` fallback.
  - `cloud/healthconnect/HealthConnectManager.kt`: In `syncWorkout()`, record `PROCESSING` at sync start, `FINISHED_SUCCESS` on success, and `FINISHED_FAILED` with error text in `ANSWER` on failure via `ExportStatusDatabaseManager.updateExportStatus()`, and trigger `ExportStatusChangedBroadcaster.broadcastExportStatusChanged()`.
  - `ui/components/export/ExportStatusDataProvider.kt` & `ExportStatusRepository.kt`: Rely on `COMMUNITY.exportToFileFormats` for automatic synthesis of missing `HEALTH_CONNECT` rows as `UNWANTED` on legacy workouts.
  - `migration/LegacyImportEngine.kt`: Guard community format loop to avoid enqueueing WorkManager file jobs for `HEALTH_CONNECT`.

---

## 2. Atomic Implementation Steps
1. **Step 1 - FileFormat & ExportType Community Registration (`REQ-EXP-023.1,2`)**:
   - Add `HEALTH_CONNECT` to `FileFormat.java` and `ONLINE_COMMUNITIES`.
   - Update `ExportType.COMMUNITY` in `ExportType.java`.
   - Update `TrainingApplication.java` (`uploadToCommunity`, `exportToFile`).
2. **Step 2 - ExportManager Initialization & Exclusion Guarding (`REQ-EXP-023.3,6`)**:
   - In `ExportManager.java`, guard `exportWorkout()` so `startFullExportProcess` is not called for `HEALTH_CONNECT`.
   - Add exhaustive cases in `ExportManager.getExporter()` returning `NotYetImplementedExporter`.
   - In `LegacyImportEngine.kt`, skip file-based `exportWorkoutTo` for `HEALTH_CONNECT`.
3. **Step 3 - HealthConnectManager Export Status Tracking & Broadcast (`REQ-EXP-023.4`)**:
   - In `HealthConnectManager.kt` (`syncWorkout`), update `ExportStatusDatabaseManager` on start (`PROCESSING`), success (`FINISHED_SUCCESS`), and failure (`FINISHED_FAILED` with exception message in `ANSWER`).
   - Emit `ExportStatusChangedBroadcaster.broadcastExportStatusChanged` on all status transitions.
4. **Step 4 - Dynamic Format Synthesis & UI Tests (`TST-EXP-020.1..3`)**:
   - In `ExportStatusDataProviderTest.kt`, verify synthesis of `HEALTH_CONNECT` as `UNWANTED` on legacy rows and plural aggregation ("Strava and Health Connect").
   - In `HealthConnectSyncTest.kt`, verify `syncWorkout` updates status DB to `FINISHED_SUCCESS` / `FINISHED_FAILED` and broadcasts.
   - In `ExportManagerFitTest.kt`, verify `newWorkout()` initializes `HEALTH_CONNECT` as `TRACKING` / `UNWANTED`.
5. **Step 5 - Automated Verification & Governance**:
   - Run targeted unit tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*" --tests "com.atrainingtracker.trainingtracker.ui.components.export.*"`.
   - Run contract and governance audits: `ThemeColorCentralizationAuditTest`, `TranslationParityTest`, and `verify_requirement_governance.py`.

---

## 3. Invariant Protection & System Properties
- **Database Schema Invariant**: Zero DDL changes or migrations; uses existing `ExportStatus.db` `(fileBaseName, TYPE, FORMAT)` upsert semantics.
- **Strava Pipeline Invariant**: Strava TCX export, background WorkManager upload, and retry pipelines remain 100% unaltered.
- **Clean Fallback**: Unsupported operation or unhandled formats gracefully fall back without runtime `IllegalStateException`.
- **Localization Parity**: Uses existing `R.string.health_connect_title` and `R.plurals.export_notification__detail__Community_*` across all 9 locales.

---

## 4. UI Consistency & Design Guidelines Compliance
- Conforms to `docs/design_guidelines.md`: Export status display leverages existing `ExportDetailsDialog` and workout card components without introducing new unstyled controls.

---

## 5. Verification Strategy & Living Documentation
- Test Scope: `ExportStatusDataProviderTest`, `HealthConnectSyncTest`, `ExportManagerFitTest`, `ThemeColorCentralizationAuditTest`.
- Status Lifecycle: Advance `REQ-EXP-023` to `[Implemented]` in Stage 4 and `[Verified]` in Stage 5.
