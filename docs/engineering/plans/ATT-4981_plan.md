# Stage 3: Implementation Plan - ATT-4981: Health Connect Resting HRV Ingestion

**Ticket**: [ATT-4981](https://rainerblind.atlassian.net/browse/ATT-4981)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-4981`  
**Requirement Mapping**: `REQ-PER-031` (`REQ-PER-031.1`..`REQ-PER-031.5`)  
**Test Mapping**: `TST-PER-039` (`TST-PER-039.1`..`TST-PER-039.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Athletes wearing smart rings or watches during sleep record nocturnal resting HRV (`HeartRateVariabilityRmssdRecord`) and resting HR (`RestingHeartRateRecord`) in Android Health Connect. Ingesting these measurements into `HrvReadinessRepository` under an isolated `OVERNIGHT_WEARABLE` cohort enables Smallest Worthwhile Change (SWC) baselining without contaminating orthostatic waking cohorts (`SUPINE`, `SEATED`, `STANDING`). Multi-epoch nocturnal readings require log-normal aggregation ($\text{ln\_rmssd} = \frac{1}{M}\sum \ln(\text{rMSSD}_i)$), date attribution, permission gating, and idempotent deduplication.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-PER-031.1` (Permission & opt-in gating), `REQ-PER-031.2` (Log-normal aggregation & date attribution), `REQ-PER-031.3` (Isolated cohort persistence), `REQ-PER-031.4` (Idempotent ingestion & source tracking), `REQ-PER-031.5` (System invariants).
* **Test Mapping**: `TST-PER-039.1` (Permission & opt-in gating), `TST-PER-039.2` (Log-normal aggregation & date attribution), `TST-PER-039.3` (Idempotent persistence & deduplication), `TST-PER-039.4` (Posture isolation in SWC calculator), `TST-PER-039.5` (Tier 1 targeted regression).

---

## 3. System Invariants & Preserved Behavior
1. **Strict Cohort Isolation**: `OVERNIGHT_WEARABLE` records never mix into waking baselines (`SUPINE`, `SEATED`, `STANDING`).
2. **Permission Gating**: Zero Health Connect queries unless user explicitly enables sync and grants permissions.
3. **Idempotency**: Repeated imports never duplicate daily records or overwrite user-pinned entries.
4. **Database Parity**: `HrvReadiness.db` schema v1 preserved without destructive migrations; `.attbackup` parity intact.
5. **Clean Code Lock**: Production code modifications remain locked until Gate 3 passes (`Rule 4`).

---

## 4. Proposed Architectural Changes
* **Permissions & Availability (`AndroidManifest.xml`, `HealthConnectManager.kt`)**: Declare `READ_HEART_RATE_VARIABILITY` and `READ_RESTING_HEART_RATE`. Add `HRV_READ_PERMISSIONS` and `hasHrvReadPermissions(context)` check to `HealthConnectManager`.
* **Cohort Modeling (`HrvPosture.kt`)**: Add `OVERNIGHT_WEARABLE` to `HrvPosture` enum.
* **Importer Component (`HealthConnectHrvImporter.kt`)**: Implements `importRecentHrv(context, daysBack)` querying `HeartRateVariabilityRmssdRecord` and `RestingHeartRateRecord`, attributing nocturnal samples to waking `date_iso`, computing log-normal mean of logs ($\text{ln\_rmssd} = \frac{1}{M}\sum \ln(\text{rMSSD}_i)$, $\text{rmssd} = \exp(\text{ln\_rmssd})$), and inserting via `HrvReadinessRepository`.
* **Preferences (`HealthConnectManager.kt` / `TrainingApplication`)**: Opt-in toggle `isHealthConnectHrvImportEnabled`.
* **SWE.2 Maintenance (`docs/architecture.md`)**: Document `HealthConnectHrvImporter` in Persistence & Cloud Layer.
* **UI Consistency**: No UI changes in this ticket (importer engine & permission declarations only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Manifest Permissions & HealthConnectManager Read Routines
* Files: `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/healthconnect/HealthConnectManager.kt`
* Add permissions, `HRV_READ_PERMISSIONS`, and `hasHrvReadPermissions(context)` helper.

### Step 2: Extend HrvPosture with OVERNIGHT_WEARABLE
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/database/hrv/HrvPosture.kt`
* Add `OVERNIGHT_WEARABLE` to enum and verify `fromNameOrNull` coverage.

### Step 3: Implement HealthConnectHrvImporter
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/cloud/healthconnect/HealthConnectHrvImporter.kt`
* Implement nocturnal epoch date attribution, log-normal aggregation, resting HR pairing, and idempotent repository insertion.

### Step 4: SWE.2 Architecture & Living Documentation Sync
* Files: `docs/architecture.md`, `docs/requirements/req_aftermath_periods.md`, `docs/tests/tst_aftermath_periods.md`
* Update component allocations in `docs/architecture.md` and sync living doc paths.

### Step 5: Unit & Contract Tests Execution
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/cloud/healthconnect/HealthConnectHrvImporterTest.kt`, `HrvReadinessCalculatorTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*" --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit test execution with mock `HealthConnectClient`, testing multi-epoch aggregation, date attribution, permission rejection, and posture isolation.
* **Rollback**: Isolated branch `feature/ATT-4981` can be cleanly reverted without affecting `develop` or `sprint/2026-46.1`.
