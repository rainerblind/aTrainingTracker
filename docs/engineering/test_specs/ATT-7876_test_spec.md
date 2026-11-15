# Stage 2: Requirement & Test Specification - ATT-7876: Epoch-Bounded Barometric Calibration

**Ticket**: [ATT-7876](https://rainerblind.atlassian.net/browse/ATT-7876)  
**Sub-task**: Stage 2 (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.7`  
**Requirement Mapping**: `REQ-FIL-018` (*Epoch-Bounded Barometric Calibration & Non-Barometric Column Isolation*)  
**Test Spec ID**: `TST-FIL-018`  
**Branch**: `feature/ATT-7876`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-15

---

## 1. Requirement Specification (`REQ-FIL-018`)

### 1.1 Problem Statement & Rationale
Barometric retroactive altitude corrections (`ALTITUDE_CORRECTION_INTENT`) currently overwrite non-barometric columns (`DEM`, `GPS`, `Google Fused`, `ATT Fused`), apply unbounded Case A shifts across pre-crash calibrated rows on `RESUME_SERVICE_RECREATION`, and divide Case B `AVG ALTITUDE` by ~20x due to mixing 20-second `sampledAltitudes` sums with 1-Hz `altStats.count`.

### 1.2 Attributes
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **`REQ-FIL-018.1` (Strict Barometric Column Scope)**: `WorkoutSamplesDatabaseManager.applyAltitudeDriftCorrection()` MUST shift exclusively `DeviceType.ALTITUDE_FROM_PRESSURE` columns (`DevicesDatabaseManager` names + current/9-locale `devices_altitude_from_pressure` + legacy `ATT-5073` names) and canonical `ALTITUDE` strictly on rows where `baroCol IS NOT NULL AND ABS(ALTITUDE - baroCol) < 0.01`, evaluated before mutating `baroCol`. It SHALL NEVER mutate `ALTITUDE (GPS)`, `ALTITUDE (Google Fused)`, `ALTITUDE (Topografische Höhe (DEM))`, or `ALTITUDE (ATT Fused ...)`.
2. **`REQ-FIL-018.2` (Process-Epoch & Upper-Bound Case A Guard)**: `TrackerService` SHALL track `mSessionEpochStartTimeMs` on `START_NORMAL`, `RESUME_SERVICE_RECREATION`, and process-recreation resumes (`mLiveSession == null`). Case A (`t0Ms <= 0 || t1Ms <= t0Ms`) SHALL restrict SQL updates to `TIME >= floor(sessionEpochStartTimeMs / 1000)` (if `> 0`) and `TIME <= t1Ms / 1000.0` (if `> 0`). `LiveWorkoutSession.rehydrateStreams()` SHALL pad `0L` timestamps for pre-resume samples and preserve pre-epoch `sampledAltitudes` and `RunningStats`; `TrackerService.mAltitudeCorrectionReceiver` SHALL only update `LiveWorkoutSession` and `WorkoutSummariesDatabaseManager` when `ALTITUDE_FROM_PRESSURE` is the active canonical `ALTITUDE` source.
3. **`REQ-FIL-018.3` (Dimensional Consistency in Case B)**: `LiveWorkoutSession.applyRetroactiveAltitudeCorrection()` Case B (`t0Ms > 0 && t1Ms > t0Ms`) MUST keep `altStats.sum` dimensionally matched to 1-Hz `altStats.count` by adding `meanCorrection * altStats.count`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**: *Given* `WorkoutSamples` with GPS, Google Fused, DEM, and Barometric altitude columns *When* `ALTITUDE_CORRECTION_INTENT` fires *Then* non-barometric columns remain unchanged and canonical `ALTITUDE` shifts only on barometric rows.
* **Criterion 2**: *Given* calibrated pre-crash samples resumed via `RESUME_SERVICE_RECREATION` *When* post-restart barometric calibration runs (`prevCalTimeMs == -1L`) *Then* only `[restartTime, calibrationTime]` barometric rows shift.
* **Criterion 3**: *Given* 1-Hz `altStats` (~445 m, count=100) and 20-s `sampledAltitudes` (size=5) *When* Case B runs *Then* `altStats.getAverage()` equals `445 m + meanCorrection` (never ~22 m).

### 1.5 System Invariants
Passive DB `onOpen()`; atomic SQLite transaction; 500 m sanity guard (`REQ-CON-017`); 0 regressions across existing Case B and crash-recovery suites.

---

## 2. Test Specification (`TST-FIL-018`)

### Test Case 1: Column Isolation & Epoch Bounds (`TST-FIL-018.1`, `TST-FIL-018.2`)
* **Target File**: `WorkoutSamplesDatabaseManagerTest.kt`
* **Procedure**: Populate GPS, Google Fused, DEM, ATT Fused, Barometric, and canonical `ALTITUDE` columns across pre-crash, post-restart `[epochStart, t1]`, and post-`t1` rows; assert non-barometric columns stay immutable, canonical `ALTITUDE` skips DEM-sourced rows, and pre-crash/post-`t1` rows stay untouched on Case A.

### Test Case 2: 1-Hz vs 20-s Case B Average & Pre-Crash Epoch Protection (`TST-FIL-018.2`, `TST-FIL-018.3`)
* **Target File**: `LiveWorkoutSessionAltitudeDriftTest.kt`
* **Procedure**: Add 100 1-Hz samples (~445 m) + 5 20-s `sampledAltitudes`, apply Case B, assert `count == 100` and `getAverage() ~= 445.0 + meanCorrection`; also rehydrate pre-crash stream + stats, set epoch timestamp, apply Case A, assert pre-crash entries remain untouched.

### Test Case 3: 9-Language Localization Reuse & Tier-1 Suite (`TST-FIL-018.4`, `TST-FIL-018.5`)
* **Procedure**: Verify zero new string keys added and existing `devices_altitude_from_pressure` localized names across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) resolve cleanly; execute targeted Tier-1 Gradle tests, `:app:lintDebug`, and scoped `:app:detekt`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-018.1` | Unit (Robolectric) | `WorkoutSamplesDatabaseManager.applyAltitudeDriftCorrection` | `REQ-FIL-018.1` | Planned |
| `TST-FIL-018.2` | Unit (Robolectric/JVM) | `applyAltitudeDriftCorrection`, `LiveWorkoutSession` | `REQ-FIL-018.2` | Planned |
| `TST-FIL-018.3` | Unit (JVM) | `LiveWorkoutSession.applyRetroactiveAltitudeCorrection` | `REQ-FIL-018.3` | Planned |
| `TST-FIL-018.4`–`.5` | Localization & Tier-1 | 9-locale pressure names + targeted Gradle suite | `REQ-FIL-018`, `REQ-PRO-001` | Planned |
