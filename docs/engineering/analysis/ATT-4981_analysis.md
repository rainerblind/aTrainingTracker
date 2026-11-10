# Stage 1 Analysis: ATT-4981 - Import morning and overnight resting HRV (rMSSD) from Android Health Connect into HrvReadinessRepository

**Ticket**: [ATT-4981](https://rainerblind.atlassian.net/browse/ATT-4981)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-4981`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  
**Requirement Mapping**: `REQ-PER-031` (`docs/requirements/req_aftermath_periods.md`)  
**Test Mapping**: `TST-PER-039` (`docs/tests/tst_aftermath_periods.md`)  

---

## 1. Problem Statement & Motivation
* **Context**: Athletes wearing smart rings (Oura), watches (Fitbit, Pixel Watch, Galaxy Watch), or bands during sleep record nocturnal resting HRV ($\text{rMSSD}$) and resting HR into Android Health Connect.
* **Gap**: `HrvReadinessRepository` (`ATT-3545`) currently persists only waking spot-check readings. Athletes using passive overnight wearable monitoring cannot leverage their data for Smallest Worthwhile Change (SWC) readiness guidance.
* **Objective**: Ingest `HeartRateVariabilityRmssdRecord` and `RestingHeartRateRecord` from Health Connect into `HrvReadinessRepository`, performing log-normal multi-sample nocturnal aggregation under an isolated `OVERNIGHT_WEARABLE` cohort with runtime permission gating.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `HealthConnectManager.kt` and `AndroidManifest.xml` lack `READ_HEART_RATE_VARIABILITY` and `READ_RESTING_HEART_RATE` permissions and reading routines.
  * `HrvPosture` was restricted to waking postures (`SUPINE`, `SEATED`, `STANDING`).
* **Independent Peer Review & Verification (ATT-3534)**:
  * *SDK Inspection*: `androidx.health.connect:connect-client:1.1.0-alpha11` provides `HeartRateVariabilityRmssdRecord` (`heartRateVariabilityMillis: Double` in `[1.0, 200.0] ms`) at instantaneous timestamps, without raw RR intervals. `artifact_rate_pct = null` is already handled as valid in `ATT-3545`.
  * *Strict Cohort Isolation*: Nocturnal sleep HRV reflects baseline parasympathetic tone distinct from waking orthostatic postures. Adding `OVERNIGHT_WEARABLE` to `HrvPosture` isolates baselines naturally via existing `HrvReadinessCalculator` logic without requiring database schema changes.
  * *Log-Normal Multi-Sample Aggregation*: Wearables record multiple epochs (e.g., 5-min bins). Because rMSSD is right-skewed, the daily value must be aggregated as the arithmetic mean of logs: $\text{ln\_rmssd} = \frac{1}{M}\sum \ln(\text{rMSSD}_i)$, and $\text{rmssd\_ms} = \exp(\text{ln\_rmssd})$.
  * *Ecosystem Grounding*: Fitbit/Pixel Watch and Oura natively write nocturnal HRV; Garmin Connect does not yet support Health Connect HRV writing. The importer must operate idempotently and tolerate missing records gracefully.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Health Connect read permissions (`READ_HEART_RATE_VARIABILITY`, `READ_RESTING_HEART_RATE`) in `AndroidManifest.xml` and `HealthConnectManager.kt`.
  * Extension of `HrvPosture` with `OVERNIGHT_WEARABLE`.
  * `HealthConnectHrvImporter` performing nocturnal log-normal multi-sample aggregation and date attribution (`hour >= 18` $\rightarrow$ next day, `hour < 12` $\rightarrow$ current day).
  * Idempotent import via `HrvReadinessRepository`, deduplicating by `(date_iso, OVERNIGHT_WEARABLE)` and tracking source package/UID in `notes`.
  * Explicit opt-in setting in DataStore (`isHealthConnectHrvImportEnabled`).
  * Unit tests with mock `HealthConnectClient`.
* **Out-of-Scope**: Writing HRV back to Health Connect; UI readiness display or trend charts (`ATT-3556`, `ATT-3557`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Status**: Net-new requirement (`REQ-PER-031`) and test specification (`TST-PER-039`).
* **Chesterton's Fence & Side-Effects (Rule 68)**: `OVERNIGHT_WEARABLE` extends `HrvPosture` additively without altering `HrvReadiness.db` schema. Waking baselines remain strictly partitioned. Zero interference with live workout sensor telemetry.

---

## 5. Architectural Strategy & High-Level Solution
* **Adapter / Importer**: `HealthConnectHrvImporter` in `cloud.healthconnect` or `database.hrv`.
* **Aggregation**: Filter nocturnal records in query window, group by waking `date_iso`, compute geometric mean of rMSSD and mean of $\ln(\text{rMSSD})$, pair with `RestingHeartRateRecord`.
* **Repository Storage**: Persist via `HrvReadinessRepository.insertEntry` with `posture = HrvPosture.OVERNIGHT_WEARABLE`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Strict cohort isolation: `OVERNIGHT_WEARABLE` never mixes with `SUPINE`, `SEATED`, or `STANDING`.
  2. Opt-in gating: Zero background queries unless user explicitly enables sync and grants permissions.
  3. Idempotent deduplication: Repeated imports never overwrite pinned entries or duplicate rows.
* **Risk Rating**: **LOW** — Additive permissions and isolated cohort ingestion leveraging existing `HrvReadinessRepository` infrastructure.
