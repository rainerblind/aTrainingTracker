# Stage 2: Requirement & Test Specification - ATT-3052: Android Health Connect Sync

**Ticket**: [ATT-3052](https://rainerblind.atlassian.net/browse/ATT-3052)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3052`  
**Requirement Mapping**: `REQ-SET-100`..`103` | **Test Spec ID**: `TST-SET-100`..`103`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-SET-100..103 Health Connect Integration)

### 1.1 Scope & Functional Requirements
* **REQ-SET-100 (SDK Status & Permissions)**: Integrate `androidx.health.connect:connect-client:1.1.0`. Verify `HealthConnectClient.getSdkStatus()` (`SDK_AVAILABLE`, `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`, `SDK_UNAVAILABLE`). Declare and request write permissions (`WRITE_EXERCISE`, `WRITE_EXERCISE_ROUTE`, `WRITE_HEART_RATE`, `WRITE_POWER`, `WRITE_SPEED`, `WRITE_DISTANCE`, `WRITE_TOTAL_CALORIES_BURNED`) via `createRequestPermissionResultContract()`.
* **REQ-SET-101 (Record Mapping)**: Map `BSportType` to Health Connect `ExerciseSessionRecord` types (`BIKE` $\to$ `EXERCISE_TYPE_BIKING`, `RUN` $\to$ `EXERCISE_TYPE_RUNNING`, trainer $\to$ `EXERCISE_TYPE_BIKING_STATIONARY`, treadmill $\to$ `EXERCISE_TYPE_RUNNING_TREADMILL`, others $\to$ `EXERCISE_TYPE_OTHER_WORKOUT`). Map telemetry to `ExerciseRoute`, `HeartRateRecord`, `PowerRecord`, `SpeedRecord`, `DistanceRecord`, and `TotalCaloriesBurnedRecord`.
* **REQ-SET-102 (Trackless Indoor Parity)**: Decouple route dependency so indoor/trainer activities export `ExerciseSessionRecord` (`exerciseRoute = null`) and non-spatial series records without exception.
* **REQ-SET-103 (Settings UI & Idempotency)**: Cloud Integrations settings toggle in `HealthConnectSettingsDialog`, automatic background sync upon workout save, manual re-sync action, and idempotent writes using `Metadata(clientRecordId = "workout_${fileBaseName}")`.

### 1.2 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Permissions & Availability)**: Given a supported device, when toggling sync, then the Health Connect permission contract opens and requests write permissions.
* **Criterion 2 (Outdoor Session with GPS)**: Given a workout with GPS and sensor telemetry, when saved, then `ExerciseSessionRecord` and associated series (`ExerciseRoute`, `HeartRateRecord`, `PowerRecord`, `SpeedRecord`) are written.
* **Criterion 3 (Trackless Indoor Workout)**: Given an indoor session without GPS, when saved, then session and non-spatial series export without route errors.
* **Criterion 4 (Manual Re-Sync & Idempotency)**: Given an already-synced workout, when manual re-sync is triggered, then existing records update in-place without duplicate sessions.

---

## 2. Test Specification (TST-SET-100..103) & Localization Plan

### 2.1 Test Suites
* **TST-SET-100**: SDK status resolution, permission declarations in manifest, and permission contract request flow.
* **TST-SET-101**: Sport type mapping, per-second telemetry cursor conversion, and timestamp boundary invariant verification.
* **TST-SET-102**: Trackless workout construction, null route safety, and non-spatial series persistence parity.
* **TST-SET-103**: Settings toggle persistence, clientRecordId idempotency, manual sync action, and 9-locale parity.

### 2.2 Localization Plan (strings_health_connect.xml)
Keys: `health_connect_title`, `health_connect_sync_title`, `health_connect_sync_summary`, `health_connect_not_available`, `health_connect_update_required`, `health_connect_sync_manual`, `health_connect_sync_success` across 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 3. Traceability Matrix

| Requirement | Test Suite | Target Component | Status |
| :--- | :--- | :--- | :--- |
| `REQ-SET-100` | `TST-SET-100` | `HealthConnectManager.kt`, `AndroidManifest.xml` | Draft / Planned |
| `REQ-SET-101` | `TST-SET-101` | `HealthConnectRecordMapper.kt` | Draft / Planned |
| `REQ-SET-102` | `TST-SET-102` | `HealthConnectRecordMapper.kt`, `HealthConnectManager.kt` | Draft / Planned |
| `REQ-SET-103` | `TST-SET-103` | `HealthConnectSettingsDialog.kt`, `strings_health_connect.xml` | Draft / Planned |
