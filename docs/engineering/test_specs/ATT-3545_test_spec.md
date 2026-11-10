# Stage 2: Requirement & Test Specification - ATT-3545: Morning HRV Readiness DB & SWC Calculator

**Ticket**: [ATT-3545](https://rainerblind.atlassian.net/browse/ATT-3545)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3545`  
**Requirement Mapping**: `REQ-PER-030` | **Test Spec ID**: `TST-PER-038`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-PER-030)

### 1.1 Problem Statement & Rationale
Athletes tracking resting morning HRV ($\ln(\text{rMSSD})$) require dedicated local persistence, `.attbackup` export/restore protection, and Smallest Worthwhile Change (SWC) corridor baselining ($\mu_{7d}$ vs. $[\mu_{60d} \pm 0.5\sigma_{60d}]$). Orthostatic vagal withdrawal demands strict per-posture isolation (`SUPINE`, `SEATED`, `STANDING`), sample SD precision ($N-1$ denominator), and query-time primary daily reading deduplication.

### 1.2 Functional & Architectural Requirements
1. **SQLite Database & DTO Mapping (`REQ-PER-030.1`)**: Create `HrvReadiness.db` (`hrv_readiness_entries`) with symmetric `fromCursor` / `fromCursorBatch` mapping for `HrvReadinessEntry`, storing timestamps, local `date_iso`, `resting_hr_bpm`, `rmssd_ms`, `ln_rmssd`, `hrv_score_100`, `sdnn_ms`, `artifact_rate_pct`, `posture`, `is_primary_for_day`, and notes.
2. **Database Registry & Backup Parity (`REQ-PER-030.2`)**: Register `HrvReadiness.db` in `DatabaseSchemaRegistry.SUPPORTED_DB_VERSIONS` (referencing `DB_VERSION` directly per Rule 62) and `DatabaseConnectionManager.getActiveDatabases()` for `.attbackup` `VACUUM INTO` snapshots.
3. **Daily Deduplication & Transactional Primary Maintenance (`REQ-PER-030.3`)**: Aggregate daily readings by `(date_iso, posture)`, gating on `artifact_rate_pct <= 5.0%`. Resolve the primary reading deterministically (`ORDER BY is_primary_for_day DESC, timestamp_ms ASC LIMIT 1`) and maintain `is_primary_for_day` transactionally on insert, pin, or delete.
4. **Per-Posture SWC Baseline Calculator (`REQ-PER-030.4`)**: Implement pure Kotlin `HrvReadinessCalculator` computing $\mu_{7d}$ ($N \ge 3$ in `[D-6, D]`), $\mu_{60d}$, and unbiased sample SD $\sigma_{60d}$ ($N \ge 14$ in `[D-59, D]`). Classify `CALIBRATING` ($N_{60d} < 14$ or $N_{7d} < 3$), `OPTIMAL` ($|\mu_{7d} - \mu_{60d}| \le 0.5\sigma_{60d}$), `FATIGUED_LOW` ($\mu_{7d} < \mu_{60d} - 0.5\sigma_{60d}$), and `ABOVE_NORMAL` ($\mu_{7d} > \mu_{60d} + 0.5\sigma_{60d}$).

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* 10 `SUPINE` and 10 `SEATED` valid readings across distinct days,
  * *When* `HrvReadinessCalculator` evaluates `SUPINE` readiness,
  * *Then* `SEATED` readings are excluded and status is `CALIBRATING` (`10 / 14 days`).
* *Given* $\ge 14$ daily `SUPINE` $\ln(\text{rMSSD})$ values with $\mu_{60d} = 4.00, \sigma_{60d} = 0.30$ (corridor $[3.85, 4.15]$), and two readings on the target date,
  * *When* evaluated,
  * *Then* only the primary morning reading represents the day; $\mu_{7d} = 3.70$ classifies `FATIGUED_LOW` and $\mu_{7d} = 4.22$ classifies `ABOVE_NORMAL`.
* *Given* populated `hrv_readiness_entries` in `HrvReadiness.db`,
  * *When* `.attbackup` export and restore are performed,
  * *Then* all entries restore intact with identical column values.

### 1.4 System Invariants
Zero disruption to workout/sensor tables; pure calculation logic decoupled from Android UI; 100% clean-room test pass rate.

---

## 2. Test Specification (TST-PER-038)

### Test Case 1: Schema & DTO Cursor Mapping Contract Test (`TST-PER-038.1`)
* **Scope**: Unit Test (`HrvReadinessEntryTest.kt`, `HrvReadinessDatabaseManagerTest.kt`).
* **Assertions**: Symmetric single and batch `fromCursor` mapping; correct default values; table creation and CRUD operations.

### Test Case 2: Transactional Primary-Day Deduplication & Pin Test (`TST-PER-038.2`)
* **Scope**: Unit Test (`HrvReadinessDatabaseManagerTest.kt`).
* **Assertions**: Multiple readings on same `(date_iso, posture)` resolve earliest valid reading as primary; user pin overrides earliest; deleting pinned reading promotes earliest valid reading.

### Test Case 3: Backup & Database Schema Registry Parity Test (`TST-PER-038.3`)
* **Scope**: Unit Test (`DatabaseSchemaRegistryTest.kt`, `BackupManagerHrvReadinessTest.kt`).
* **Assertions**: `DatabaseSchemaRegistry` references `HrvReadinessDatabaseManager.DB_VERSION` directly; `DatabaseConnectionManager.getActiveDatabases()` includes `HrvReadiness.db`; `.attbackup` snapshot copies table.

### Test Case 4: Per-Posture Isolation & 14-Day Calibration Gate Test (`TST-PER-038.4`)
* **Scope**: Unit Test (`HrvReadinessCalculatorTest.kt`).
* **Assertions**: Zero crosstalk between `SUPINE`, `SEATED`, `STANDING`; $< 14$ baseline days or $< 3$ weekly days returns `CALIBRATING` with exact day count. Readings with `artifact_rate_pct > 5.0%` are ignored.

### Test Case 5: Exact Sample SD & SWC Corridor Classification Test (`TST-PER-038.5`)
* **Scope**: Unit Test (`HrvReadinessCalculatorTest.kt`).
* **Assertions**: Bessel's correction $N-1$ sample SD $\sigma_{60d}$; corridor boundaries $[\mu_{60d} \pm 0.5\sigma_{60d}]$; exact classification into `OPTIMAL`, `FATIGUED_LOW`, and `ABOVE_NORMAL`.

### Test Case 6: Tier 1 Targeted Regression Suite (`TST-PER-038.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% clean-room pass across database, migration, and build contract tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-038.1]` | Unit | Schema creation, symmetric `fromCursor` DTO mapping | `REQ-PER-030.1` | Planned |
| `[TST-PER-038.2]` | Unit | Transactional daily deduplication & pin promotion | `REQ-PER-030.3` | Planned |
| `[TST-PER-038.3]` | Unit | `DatabaseSchemaRegistry` & `BackupManager` parity | `REQ-PER-030.2` | Planned |
| `[TST-PER-038.4]` | Unit | Posture isolation, artifact filter, 14-day calibration gate | `REQ-PER-030.4` | Planned |
| `[TST-PER-038.5]` | Unit | Sample SD Bessel correction, SWC corridor classification | `REQ-PER-030.4` | Planned |
| `[TST-PER-038.6]` | Regression | Tier 1 targeted regression suite | `REQ-PRO-001`, `REQ-PER-030.1`..`REQ-PER-030.4` | Planned |
