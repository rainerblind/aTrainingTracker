# Stage 1 Analysis: ATT-8427 - Extract authentic WorkoutSummaries columns and eliminate synthetic metrics in extract_pixel_field_evidence.py

**Ticket**: [ATT-8427](https://rainerblind.atlassian.net/browse/ATT-8427)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht`  
**Branch**: `feature/ATT-8427` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-17  

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Critical Finding `K1` (SYS.4/5, SWE.6, SUP.1, SUP.8), evidence extraction must reflect genuine physical hardware recordings:
1. **Synthetic & Hardcoded Fake Metrics**: In `tools/extract_pixel_field_evidence.py`, `parse_sqlite_database()` (L200–220) and `parse_tcx_file()` (L149–153) inject unmeasured, hardcoded metrics: `sample_loss_pct=0.15/0.2`, `battery_drain_pct_hr=6.2/5.8`, `fit_valid=True`, `cloud_synced=True`, and synthetic radar pairing correlation `has_radar = has_pow and has_cad`. Under Finding K1, unmeasured metrics MUST remain `None` / `NOT MEASURED`.
2. **Schema Drift & Fantasy Columns**: `parse_sqlite_database()` queries non-existent columns (`startTime`, `duration`, `distance`, `avgHr`, `avgPower`, `avgCadence`, `elevationGain`, `sportType`). Authentic `WorkoutSummariesDatabaseManager.java` schema uses `WorkoutSummaries` (`timeStart`, `timeActive_s`, `distanceTotal_m`, `ascending`, `Sport`, `uploadToStrava`) and secondary table `ExtremumValues` (`sensorType`, `extremumType`, `value`).
3. **Fragile ADB Database Pull**: `pull_device_evidence_adb()` attempts to dump databases to `/sdcard/Download/` via `run-as`, which fails under Android Scoped Storage. Direct extraction via `exec-out run-as ... cat databases/WorkoutSummaries.db` is required.

---

## 2. Root Cause Analysis & Independent Peer Review
* **RCA 1 (Rushed Mocking in ATT-8141)**: Initial implementation in `ATT-8141` populated default/plausible floats to fill table templates without connecting to the actual multi-table schema in `WorkoutSummariesDatabaseManager.java`.
* **RCA 2 (Imaginary Column Querying)**: `parse_sqlite_database()` used generic camelCase column names that never existed in the production SQLite database helper.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Audited `WorkoutSummariesDatabaseManager.java` L2344–2420: Confirmed table `WorkoutSummaries` with columns `timeStart`, `timeActive_s`, `distanceTotal_m`, `ascending`, `Sport`, and table `ExtremumValues` with columns `workoutID`, `sensorType`, `extremumType`, `value`.
  * Audited `tools/extract_pixel_field_evidence.py` L200–220: Verified synthetic hardcoded assignments `sample_loss_pct=0.15`, `battery_drain_pct_hr=6.2`, `fit_valid=True`, and `has_radar=has_pow and has_cad`.
  * Audited `tools/test_extract_pixel_field_evidence.py` L149–165: Verified test fixture mimics the imaginary schema instead of testing authentic production tables.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Align `parse_sqlite_database()` in `tools/extract_pixel_field_evidence.py` to authentic `WorkoutSummariesDatabaseManager` schema (`WorkoutSummaries` + `ExtremumValues`).
  2. Eliminate all synthetic metric injection in `parse_sqlite_database()` and `parse_tcx_file()`: set unmeasured attributes to `None`.
  3. Streamline `pull_device_evidence_adb()` to pull `WorkoutSummaries.db` directly via `exec-out run-as <pkg> cat databases/WorkoutSummaries.db` with SQLite header validation.
  4. Update `evaluate_verification_coverage()` to handle `None` values gracefully without assuming fake measurements.
  5. Update `tools/test_extract_pixel_field_evidence.py` with authentic schema fixtures and `None` assertions.
* **Out-of-Scope Non-Goals**:
  - Modifying `WorkoutSummariesDatabaseManager.java` or Android app runtime code (read-only consumer).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PRO-098.1` / `TST-PRO-098.3` (`docs/requirements/req_process_stability.md`). Targets `tools/extract_pixel_field_evidence.py` and `tools/test_extract_pixel_field_evidence.py`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-8141` (`92f94d30`, Sprint 2026-46.8) to resolve critical Finding K1.
* **Root Reason for Existing Formulation**: Early draft prioritized pipeline demonstration, inserting plausible dummy numbers that inadvertently violated the zero-fabrication invariant.
* **Preservation of Core Invariants**: Finding K1 zero-synthetic-evidence invariant; Rule 7.6 read-only PO dogfooding pipeline; pure Python stdlib; `ANT-Android-SDKs` at `0e8cea71`.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-PRO-098.1` (`req_process_stability.md`); test spec `TST-PRO-098.3` (`tst_process_stability.md`).
* **Affected Architecture Elements**: `tools/extract_pixel_field_evidence.py`, `tools/test_extract_pixel_field_evidence.py`.
* **Verification Impact & Test Surface**: Unit tests in `test_extract_pixel_field_evidence.py`, `python3 tools/verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **LOW** (1 SP developer tooling bugfix; zero Android production binary impact).

---

## 6. Architectural Strategy & High-Level Solution
* **Multi-Table Query**: Query `WorkoutSummaries` for basic telemetry (`_id`, `timeStart`, `timeActive_s`, `distanceTotal_m`, `ascending`, `Sport`, `uploadToStrava`), then join or query `ExtremumValues` for active sensor types (`HR`, `POWER`, `CADENCE`, `ALTITUDE`, `RADAR_*`).
* **Truthful Nullability**: If `ExtremumValues` or stream logs lack RF/battery metrics, keep `sample_loss_pct`, `battery_drain_pct_hr`, `max_rf_dropout_sec`, `fit_valid` as `None`.
* **Direct ADB Stream**: Use `adb exec-out run-as <pkg> cat databases/WorkoutSummaries.db` to pull cleanly across scoped-storage boundaries.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) `app/src/main/` strictly untouched; (2) Pure Python standard library; (3) Finding K1 evidence authenticity preserved; (4) `ANT-Android-SDKs` intact at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* **Risk Rating**: **LOW** (1 SP tooling defect fix).
