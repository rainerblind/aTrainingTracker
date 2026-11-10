# Stage 3: Implementation Plan - ATT-3545: Morning HRV Readiness DB & SWC Baseline Calculator

**Ticket**: [ATT-3545](https://rainerblind.atlassian.net/browse/ATT-3545)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3545`  
**Requirement Mapping**: `REQ-PER-030` (`REQ-PER-030.1`..`REQ-PER-030.5`)  
**Test Mapping**: `TST-PER-038` (`TST-PER-038.1`..`TST-PER-038.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Athletes tracking resting morning HRV ($\ln(\text{rMSSD})$) require dedicated local persistence, `.attbackup` export/restore protection, and Smallest Worthwhile Change (SWC) corridor baselining ($\mu_{7d}$ vs. $[\mu_{60d} \pm 0.5\sigma_{60d}]$). ATT-3545 establishes the foundational persistence layer (`HrvReadiness.db`), repository access, database schema registry parity, and pure Kotlin per-posture SWC calculation engine.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-PER-030.1` (SQLite schema & DTO symmetry), `REQ-PER-030.2` (Registry & backup parity), `REQ-PER-030.3` (Daily deduplication & pin transaction), `REQ-PER-030.4` (Per-posture SWC baseline calculator), `REQ-PER-030.5` (System invariants).
* **Test Mapping**: `TST-PER-038.1` (Schema & DTO cursor mapping), `TST-PER-038.2` (Deduplication & pin promotion), `TST-PER-038.3` (Registry & backup parity), `TST-PER-038.4` (Posture isolation & calibration gate), `TST-PER-038.5` (Sample SD & SWC classification), `TST-PER-038.6` (Tier 1 targeted regression).

---

## 3. System Invariants & Preserved Behavior
1. **Per-Posture Isolation**: Baselines and corridors for `SUPINE`, `SEATED`, and `STANDING` never mix.
2. **Calibration Gate**: Fewer than 14 baseline days in 60d or 3 days in 7d strictly returns `CALIBRATING`.
3. **Database Independence**: Standalone `HrvReadiness.db` creates zero locking dependencies on existing DBs.
4. **Backup Parity**: Atomic `VACUUM INTO` snapshot and full `.attbackup` restore integrity.
5. **Clean Code Lock**: Production code modifications remain locked until Gate 3 passes (`Rule 4`).

---

## 4. Proposed Architectural Changes
* **Models & Enums (`database.hrv`)**: `HrvPosture` (`SUPINE`, `SEATED`, `STANDING`), `ReadinessStatus` (`CALIBRATING`, `OPTIMAL`, `FATIGUED_LOW`, `ABOVE_NORMAL`), and `HrvReadinessEntry` with symmetric single/batch `fromCursor` mapping.
* **Database & Registry (`database.hrv`, `migration`)**: `HrvReadinessDatabaseManager` (`HrvReadiness.db`, `DB_VERSION = 1`, table `hrv_readiness_entries`) with transactional primary day deduplication. Registered in `DatabaseSchemaRegistry.SUPPORTED_DB_VERSIONS` and `DatabaseConnectionManager.getActiveDatabases()`.
* **Repository (`database.hrv`)**: `HrvReadinessRepository` encapsulating database access and querying.
* **Calculator Engine (`database.hrv`)**: Pure Kotlin `HrvReadinessCalculator` computing daily primary deduplication, 7-day $\mu_{7d}$ ($N \ge 3$), 60-day $\mu_{60d}$ & Bessel-corrected sample SD $\sigma_{60d}$ ($N \ge 14$), SWC corridor $[\mu_{60d} \pm 0.5\sigma_{60d}]$, and status classification.
* **SWE.2 Maintenance (`docs/architecture.md`)**: Add `HrvReadiness.db` and `HrvReadinessRepository` to SWE.2 component model and persistence specification.
* **UI Consistency**: No UI changes in this ticket (backend database, repository & calculation engine only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Domain Enums & Symmetric DTO
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/database/hrv/HrvPosture.kt`, `ReadinessStatus.kt`, `HrvReadinessEntry.kt`
* Define enums and immutable `HrvReadinessEntry` with symmetric single-row and batch `fromCursor` mappers.

### Step 2: Database Manager & Transactional Primary-Day Deduplication
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/database/hrv/HrvReadinessDatabaseManager.kt`
* Implement `HrvReadinessDbHelper`, table creation, indexing, CRUD, and transactional `is_primary_for_day` maintenance.

### Step 3: Database Schema Registry & Backup Integration
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/DatabaseSchemaRegistry.kt`, `DatabaseConnectionManager.kt`
* Register `HrvReadiness.db` in `SUPPORTED_DB_VERSIONS` and `getActiveDatabases()` for `.attbackup` parity.

### Step 4: Repository Layer & Per-Posture SWC Baseline Calculator
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/database/hrv/HrvReadinessRepository.kt`, `HrvReadinessCalculator.kt`
* Implement repository and pure calculation engine with Bessel sample SD and SWC status classification.

### Step 5: SWE.2 Architecture & Living Documentation Sync
* Files: `docs/architecture.md`, `docs/requirements/req_aftermath_periods.md`, `docs/tests/tst_aftermath_periods.md`
* Update architecture component models and sync requirement/test status.

### Step 6: Comprehensive Unit Test Suite Execution
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/database/hrv/HrvReadinessEntryTest.kt`, `HrvReadinessDatabaseManagerTest.kt`, `HrvReadinessCalculatorTest.kt`, `DatabaseSchemaRegistryHrvTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.*" && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit test execution, schema migration assertions, and requirement governance check.
* **Rollback**: Isolated branch `feature/ATT-3545` can be cleanly reverted or reset prior to Stage 5 squash merge.
