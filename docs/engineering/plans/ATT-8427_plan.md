# Stage 3: Implementation Plan - ATT-8427: Extract authentic WorkoutSummaries columns and eliminate synthetic metrics in extract_pixel_field_evidence.py

**Ticket**: [ATT-8427](https://rainerblind.atlassian.net/browse/ATT-8427)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht`  
**Requirement Mapping**: `REQ-PRO-098` (`REQ-PRO-098.1`)  
**Test Mapping**: `TST-PRO-098` (`TST-PRO-098.3`)  
**Branch**: `feature/ATT-8427` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-17  

---

## 1. Problem Description & Background
Under ASPICE 4.0 Finding `K1`, `tools/extract_pixel_field_evidence.py` violated zero-fabrication standards by injecting hardcoded synthetic metrics (`sample_loss_pct=0.15/0.2`, `battery_drain_pct_hr=6.2/5.8`, `fit_valid=True`, and `has_radar = has_pow and has_cad`) and querying non-existent camelCase columns on a dummy table. Furthermore, `pull_device_evidence_adb()` used `/sdcard/Download/` staging which breaks under Android Scoped Storage.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-098.1` $\rightarrow$ `TST-PRO-098.3`**:
  - `parse_sqlite_database()` queries authentic `WorkoutSummaries` (`timeStart`, `timeActive_s`, `distanceTotal_m`, `ascending`, `Sport`, `uploadToStrava`) and `ExtremumValues` (`workoutID`, `sensorType`, `extremumType`, `value`) tables/columns.
  - Unmeasured metrics evaluate strictly to `None` / `"NOT MEASURED"` across SQLite and TCX parsers.
  - `pull_device_evidence_adb()` executes direct stream pull via `adb exec-out run-as <pkg> cat databases/WorkoutSummaries.db` with SQLite magic header check (`SQLite format 3`).
  - Unit test fixtures in `tools/test_extract_pixel_field_evidence.py` test authentic dual-table schema and assert `None` for unmeasured metrics.

---

## 3. System Invariants & Preserved Behavior
1. **Finding K1 Zero-Fabrication Mandate**: Unmeasured metrics MUST remain `None`; never synthesize plausible numbers or infer hardware pairing without direct log/table proof.
2. **Zero Production Code Edits**: Zero edits to `app/src/main/` or `WorkoutSummariesDatabaseManager.java`.
3. **Graceful None Handling**: `evaluate_verification_coverage()` handles `None` metric values without formatting errors or false gate promotions.
4. **Submodule Purity**: `ANT-Android-SDKs` preserved at commit `0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (`REQ-PRO-098.1` / `tools/extract_pixel_field_evidence.py`)**:
  - Update `WorkoutSession` class fields (`sample_loss_pct`, `battery_drain_pct_hr`, `max_rf_dropout_sec`, `fit_valid`, `cloud_synced`) to default to `None`.
  - In `parse_tcx_file()`, set unmeasured metrics (`sample_loss_pct`, `battery_drain_pct_hr`, `fit_valid`, `cloud_synced`) to `None`, and set `has_radar = False`.
* **Step 2 (`REQ-PRO-098.1` / `tools/extract_pixel_field_evidence.py`)**:
  - Rewrite `parse_sqlite_database()` to query `WorkoutSummaries` table using authentic column constants (`timeStart`, `timeActive_s`, `distanceTotal_m`, `ascending`, `Sport`, `uploadToStrava`).
  - Query `ExtremumValues` table for `workoutID` to detect genuine sensor signals (`HR`, `POWER`, `CADENCE`, `RADAR_*`, `ALTITUDE`, `LATITUDE`/`LONGITUDE`).
  - Ensure unmeasured metrics evaluate to `None` (`sample_loss_pct=None`, `battery_drain_pct_hr=None`, `fit_valid=None`), and `has_radar` is True only if actual `RADAR_*` sensor rows exist.
* **Step 3 (`REQ-PRO-098.1` / `tools/extract_pixel_field_evidence.py`)**:
  - Update `pull_device_evidence_adb()` to stream `WorkoutSummaries.db` directly via `adb exec-out run-as <package> cat databases/WorkoutSummaries.db` and validate the `b"SQLite format 3"` magic header.
  - Update `evaluate_verification_coverage()` evidence strings to format `None` metrics as `"NOT MEASURED"`.
* **Step 4 (`TST-PRO-098.3` / `tools/test_extract_pixel_field_evidence.py`)**:
  - Rebuild `test_parse_sqlite_database` fixture using authentic `WorkoutSummaries` and `ExtremumValues` DDL and insert realistic sensor rows.
  - Add assertions verifying `None` for unmeasured fields, authentic `has_radar` evaluation, and TCX nullability.
  - Add test for `exec-out run-as` ADB database pulling with SQLite header verification.
* **Step 5 (Verification)**:
  - Run `python3 -m unittest tools/test_extract_pixel_field_evidence.py`.
  - Run `python3 tools/verify_requirement_governance.py`.

---

## 5. Verification & Rollback Plan
* **Verification**: Verify 100% test pass on `tools/test_extract_pixel_field_evidence.py` and clean exit code 0 on `verify_requirement_governance.py`.
* **Rollback**: `git checkout feature/ATT-8427 -- tools/` cleanly reverts modifications.
