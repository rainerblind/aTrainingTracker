# Stage 1 Analysis: ATT-3545 - Create Morning HRV Readiness Database (with backup/restore) and per-posture 7-Day vs. 60-Day SWC Baseline Calculator

**Ticket**: [ATT-3545](https://rainerblind.atlassian.net/browse/ATT-3545)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3545`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  
**Requirement Mapping**: `REQ-PER-030` (`docs/requirements/req_aftermath_periods.md`)  
**Test Mapping**: `TST-PER-038` (`docs/tests/tst_aftermath_periods.md`)  

---

## 1. Problem Statement & Motivation
* **Context**: Athletes require resting morning Heart Rate Variability (HRV / $\ln(\text{rMSSD})$) monitoring to track autonomic recovery, systemic stress, and training readiness (Plews et al. 2012, 2013; Buchheit 2014).
* **Gap**: *aTrainingTracker* lacks dedicated longitudinal persistence for resting morning HRV checks, automated Smallest Worthwhile Change (SWC) corridor baselining ($\mu_{7d}$ vs. $[\mu_{60d} \pm 0.5\sigma_{60d}]$), and backup/restore coverage in `.attbackup`.
* **Objective**: Introduce `HrvReadiness.db` (`hrv_readiness_entries`), `.attbackup` integration, and a pure per-posture SWC baseline calculator (`HrvReadinessCalculator`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * Real-time HRV (`RrArtifactFilter.kt`, `DfaCalculator.kt`) is transient and scoped to live workouts. No database exists for daily waking readiness entries.
  * `DatabaseSchemaRegistry.kt` and `DatabaseConnectionManager.kt` lack definitions for `HrvReadiness.db`, leaving it vulnerable during backup/restore and schema downgrade checks.
* **Independent Peer Review & Verification (ATT-3534)**:
  * *Live Code Audit*: Inspected `DatabaseSchemaRegistry.kt:29-50` and `DatabaseConnectionManager.kt:109-127`. Adding `HrvReadiness.db` enables atomic `VACUUM INTO` snapshots and WAL truncation without modifying backup engine logic.
  * *Posture Isolation*: Postural shifts (supine $\rightarrow$ seated $\rightarrow$ standing) induce massive orthostatic vagal withdrawal. Merging postures inflates $\sigma_{60d}$, destroying SWC sensitivity. Independent baselines per `HrvPosture` (`SUPINE`, `SEATED`, `STANDING`) are strictly required.
  * *Deduplication & Primary Day Rule*: Aggregating by local `date_iso` (`YYYY-MM-DD`) with query-time `ORDER BY is_primary_for_day DESC, timestamp_ms ASC LIMIT 1` (and transactional maintenance in manager) guarantees deterministic single-reading representation per day.
  * *Sample SD Precision*: Verified $\sigma_{60d}$ must use Bessel's correction ($N-1$ denominator) over daily $\ln(\text{rMSSD})$ with $N \ge 14$.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * `HrvPosture` (`SUPINE`, `SEATED`, `STANDING`) and `ReadinessStatus` (`CALIBRATING`, `OPTIMAL`, `FATIGUED_LOW`, `ABOVE_NORMAL`) enums.
  * `HrvReadinessEntry` DTO with symmetric `fromCursor` / `fromCursorBatch` mapping.
  * `HrvReadinessDatabaseManager` (`HrvReadiness.db`, table `hrv_readiness_entries`, `DB_VERSION = 1`) with transactional primary-day resolution and CRUD.
  * Registration in `DatabaseSchemaRegistry.kt` and `DatabaseConnectionManager.kt` for `.attbackup` export/restore parity.
  * `HrvReadinessCalculator`: per-posture SWC baseline math ($N_{7d} \ge 3$, $N_{60d} \ge 14$, $\sigma_{60d}$ sample SD, $[\mu_{60d} \pm 0.5\sigma_{60d}]$ corridor, and `CALIBRATING` day counter).
* **Out-of-Scope**: Morning measurement UI / sensor wizard (`ATT-3556`), Periods HRV trend chart (`ATT-3557`), Health Connect sync (`ATT-4981`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Status**: Net-new requirement only (`REQ-PER-030`) and test suite (`TST-PER-038`). No existing requirements modified.
* **Chesterton's Fence & Side-Effect Audit (Rule 68)**: Dedicated standalone database avoids touching existing workout or sensor schemas. Backup registry additions preserve backward compatibility for existing backup archives.

---

## 5. Architectural Strategy & High-Level Solution
* **Module Placement**: `com.atrainingtracker.trainingtracker.database.hrv` (or `physiology.readiness`).
* **Database Schema**: `hrv_readiness_entries` (`_id`, `timestamp_ms`, `date_iso`, `timezone_id`, `resting_hr_bpm`, `rmssd_ms`, `ln_rmssd`, `hrv_score_100`, `sdnn_ms`, `artifact_rate_pct`, `duration_sec`, `posture`, `is_primary_for_day`, `notes`).
* **Calculator API**: `HrvReadinessCalculator.calculateReadiness(entries, posture, targetDate)` returning baseline metrics, SWC bounds, calibration progress, and `ReadinessStatus`.
* **Backup Parity**: Register `HrvReadiness.db` in `DatabaseSchemaRegistry.SUPPORTED_DB_VERSIONS` and `DatabaseConnectionManager.getActiveDatabases()`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Per-posture isolation: readings of differing postures MUST NEVER be mixed into baseline math.
  2. 14-day calibration gate: $< 14$ daily readings in 60d (or $< 3$ in 7d) MUST return `CALIBRATING`.
  3. Artifact gate: readings with `artifact_rate_pct > 5.0%` excluded from baseline/rolling averages.
  4. `.attbackup` round-trip parity: backup and restore restore all rows intact without data loss.
* **Risk Rating**: **LOW** — Additive schema, isolated database, pure algorithmic calculation, zero breaking changes to existing tracking or persistence flows.
