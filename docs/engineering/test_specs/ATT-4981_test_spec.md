# Stage 2: Requirement & Test Specification - ATT-4981: Health Connect HRV Import

**Ticket**: [ATT-4981](https://rainerblind.atlassian.net/browse/ATT-4981)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-4981`  
**Requirement Mapping**: `REQ-PER-031` | **Test Spec ID**: `TST-PER-039`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-PER-031)

### 1.1 Problem Statement & Rationale
Athletes wearing smart rings or watches during sleep record nocturnal resting HRV (`HeartRateVariabilityRmssdRecord`) and resting HR (`RestingHeartRateRecord`) into Android Health Connect. Ingesting these measurements into `HrvReadinessRepository` under an isolated `OVERNIGHT_WEARABLE` cohort enables Smallest Worthwhile Change (SWC) baselining without contaminating orthostatic waking cohorts (`SUPINE`, `SEATED`, `STANDING`). Multi-epoch nocturnal readings require log-normal aggregation ($\text{ln\_rmssd} = \frac{1}{M}\sum \ln(\text{rMSSD}_i)$), date attribution, permission gating, and idempotent deduplication.

### 1.2 Functional & Architectural Requirements
1. **Permission & Opt-In Gating (`REQ-PER-031.1`)**: Declare `READ_HEART_RATE_VARIABILITY` and `READ_RESTING_HEART_RATE` permissions in `AndroidManifest.xml` and register in `HealthConnectManager.kt`. Ingestion SHALL require explicit user opt-in (`isHealthConnectHrvImportEnabled`).
2. **Log-Normal Aggregation & Date Attribution (`REQ-PER-031.2`)**: Read nocturnal records in `HealthConnectHrvImporter`. Attribute records between 18:00 and 12:00 to the waking date `date_iso`. Compute log-normal mean $\text{ln\_rmssd} = \frac{1}{M}\sum \ln(\text{rMSSD}_i)$ and geometric mean $\text{rmssd\_ms} = \exp(\text{ln\_rmssd})$, paired with mean resting HR.
3. **Isolated Cohort Persistence (`REQ-PER-031.3`)**: Extend `HrvPosture` with `OVERNIGHT_WEARABLE`. Persist into `HrvReadinessRepository` (`HrvReadiness.db`) with `artifact_rate_pct = null` (inherently valid).
4. **Idempotent Ingestion & Source Tracking (`REQ-PER-031.4`)**: Deduplicate by `(date_iso, OVERNIGHT_WEARABLE)`. Record source package/record UID in `notes`. Re-imports SHALL NOT overwrite pinned entries or duplicate rows.
5. **System Invariants (`REQ-PER-031.5`)**: Waking cohorts strictly isolated; `.attbackup` export/restore preserved; zero unprompted background sync when disabled.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* nocturnal Health Connect records across sleep epochs (e.g., 30.0 ms, 40.0 ms, 50.0 ms),
  * *When* imported by `HealthConnectHrvImporter`,
  * *Then* $\text{ln\_rmssd}$ is the mean of logs ($\approx 3.655$), $\text{rmssd\_ms} \approx 38.67\text{ ms}$, and attributed to waking date `date_iso`.
* *Given* 14 `OVERNIGHT_WEARABLE` days and 10 `SUPINE` waking spot-checks,
  * *When* evaluating `SUPINE` readiness,
  * *Then* `OVERNIGHT_WEARABLE` records are strictly excluded and `SUPINE` remains `CALIBRATING` (`10 / 14 days`).
* *Given* user has not opted in or granted Health Connect permissions,
  * *When* import sync is invoked,
  * *Then* import aborts safely without exception and zero database records are modified.

---

## 2. Test Specification (TST-PER-039)

### Test Case 1: Permission & Opt-In Gating Contract Test (`TST-PER-039.1`)
* **Scope**: Unit Test (`HealthConnectHrvImporterTest.kt`).
* **Assertions**: Asserts import halts when permissions missing or opt-in setting is false; asserts permissions declared in manifest.

### Test Case 2: Nocturnal Log-Normal Aggregation & Date Attribution Test (`TST-PER-039.2`)
* **Scope**: Unit Test (`HealthConnectHrvImporterTest.kt`).
* **Assertions**: Multi-epoch rMSSD aggregates to log-normal mean; records at 23:00 attribute to next morning date `date_iso`; records at 06:00 attribute to current date.

### Test Case 3: Isolated Cohort Persistence & Deduplication Test (`TST-PER-039.3`)
* **Scope**: Unit Test (`HealthConnectHrvImporterTest.kt`, `HrvReadinessRepositoryTest.kt`).
* **Assertions**: Persists with `posture = OVERNIGHT_WEARABLE`, `artifactRatePct = null`; repeated import is idempotent; preserves pinned entries.

### Test Case 4: Per-Posture Isolation & Baseline Calculator Contract Test (`TST-PER-039.4`)
* **Scope**: Unit Test (`HrvReadinessCalculatorTest.kt`).
* **Assertions**: Zero crosstalk between `OVERNIGHT_WEARABLE` and waking postures (`SUPINE`, `SEATED`, `STANDING`) in SWC baseline math.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-PER-039.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% clean-room pass across HRV database, Health Connect, and build contract tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-039.1]` | Unit | Health Connect permission & opt-in gating | `REQ-PER-031.1` | Planned |
| `[TST-PER-039.2]` | Unit | Log-normal rMSSD aggregation & date attribution | `REQ-PER-031.2` | Planned |
| `[TST-PER-039.3]` | Unit | Idempotent persistence, source tracking, deduplication | `REQ-PER-031.3`, `REQ-PER-031.4` | Planned |
| `[TST-PER-039.4]` | Unit | Posture isolation in SWC baseline calculator | `REQ-PER-031.3`, `REQ-PER-031.5` | Planned |
| `[TST-PER-039.5]` | Regression | Tier 1 targeted regression suite | `REQ-PRO-001`, `REQ-PER-031.1`..`REQ-PER-031.5` | Planned |
