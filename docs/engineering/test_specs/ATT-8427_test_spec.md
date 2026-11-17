# Stage 2: Requirement & Test Specification - ATT-8427: Extract authentic WorkoutSummaries columns and eliminate synthetic metrics in extract_pixel_field_evidence.py

**Ticket**: [ATT-8427](https://rainerblind.atlassian.net/browse/ATT-8427)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht`  
**Requirement Mapping**: `REQ-PRO-098` (`REQ-PRO-098.1`)  
**Test Spec ID**: `TST-PRO-098` (`TST-PRO-098.3`)  
**Branch**: `feature/ATT-8427` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-17  

---

## 1. Requirement Specification (REQ-PRO-098)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Status: `[Verified]`
* **REQ-PRO-098.1 (Authentic Pixel Telemetry Extraction & Zero Fabrication — [Amended] ATT-8427)**: `tools/extract_pixel_field_evidence.py` SHALL extract authentic workout telemetry from Google Pixel 10 devices and exports. When querying SQLite databases (`parse_sqlite_database`), it SHALL query authentic tables and columns matching `WorkoutSummariesDatabaseManager.java` (`WorkoutSummaries` columns `_id`, `timeStart`, `timeActive_s`, `distanceTotal_m`, `ascending`, `Sport`, `uploadToStrava`, and `ExtremumValues` columns `workoutID`, `sensorType`, `extremumType`, `value`). It SHALL adhere strictly to Finding K1 zero-fabrication standards: all unmeasured or unrecorded metrics (`sample_loss_pct`, `battery_drain_pct_hr`, `max_rf_dropout_sec`, `fit_valid`, radar presence without sensor stream proof) MUST evaluate to `None` / `"NOT MEASURED"`, across both SQLite and TCX extraction pipelines. Furthermore, `pull_device_evidence_adb` SHALL reliably pull app-private databases directly via `adb exec-out run-as <package> cat databases/WorkoutSummaries.db` with SQLite magic header validation.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PRO-098.1` / `TST-PRO-098.3`. Targets `tools/extract_pixel_field_evidence.py` and `tools/test_extract_pixel_field_evidence.py`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-8141` (`92f94d30`, Sprint 2026-46.8) to resolve critical Finding K1.
* **Root Reason for Existing Formulation**: The initial implementation used dummy camelCase columns and hardcoded floats to demonstrate report rendering, which inadvertently violated the zero-fabrication mandate.
* **Preservation of Core Invariants**: Strictly upholds Finding K1 zero-synthetic-evidence invariant; preserves Rule 7.6 read-only PO dogfooding pipeline; keeps pure Python stdlib; `ANT-Android-SDKs` at `0e8cea71`.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Authentic SQLite Database Parsing)**: *Given* an authentic `WorkoutSummaries.db` containing `WorkoutSummaries` and `ExtremumValues` tables, *When* `parse_sqlite_database()` executes, *Then* it queries authentic column names (`timeStart`, `timeActive_s`, `distanceTotal_m`, `ascending`, `Sport`, `uploadToStrava`), extracts real sensor presence from `ExtremumValues` (`HR`, `POWER`, `CADENCE`, `RADAR_*`), and reports `None` for unmeasured metrics (`sample_loss_pct`, `battery_drain_pct_hr`, `fit_valid`).
* **Criterion 2 (Authentic TCX Export Parsing)**: *Given* a TCX export file without radar or battery telemetry, *When* `parse_tcx_file()` executes, *Then* it extracts true duration, distance, and trackpoint sensors, while reporting `None` for `sample_loss_pct`, `battery_drain_pct_hr`, `fit_valid`, and `cloud_synced`, with `has_radar == False`.
* **Criterion 3 (Direct ADB Scoped Storage Extraction)**: *Given* a connected reference device via ADB, *When* `pull_device_evidence_adb()` executes, *Then* it pulls `WorkoutSummaries.db` directly via `adb exec-out run-as <pkg> cat databases/WorkoutSummaries.db` and validates the SQLite format 3 magic header.
* **Criterion 4 (Unit Test Parity on Authentic Schema)**: *Given* `tools/test_extract_pixel_field_evidence.py`, *When* executed via `python3 -m unittest`, *Then* test fixtures assert against authentic `WorkoutSummaries` and `ExtremumValues` schemas and pass with 100% success rate.

---

## 4. Test Specification (TST-PRO-098.3)
* **TST-PRO-098.3 (Pixel Telemetry Extractor & Zero-Fabrication Test)**: In `tools/test_extract_pixel_field_evidence.py`, verify that `tools/extract_pixel_field_evidence.py`:
  1. Queries authentic `WorkoutSummaries` and `ExtremumValues` columns without imaginary camelCase fallbacks.
  2. Reports `None` for unmeasured metrics (`sample_loss_pct`, `battery_drain_pct_hr`, `fit_valid`, `cloud_synced`) across SQLite and TCX parsers.
  3. Verifies `has_radar` evaluates to True only when authentic `RADAR_*` sensor entries exist in `ExtremumValues`, never based on power/cadence correlation.
  4. Successfully evaluates verification coverage matrix with `None` metric handling without runtime TypeError or formatting crashes.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-098.3` | Unit/Live | `tools/test_extract_pixel_field_evidence.py`, `tools/extract_pixel_field_evidence.py` | `REQ-PRO-098.1` | Specified |
| `TST-PRO-098.1` | Unit | `tools/test_generate_release_verification_summary.py` | `REQ-PRO-098.2` | Specified |
