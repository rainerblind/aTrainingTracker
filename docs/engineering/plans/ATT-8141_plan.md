# Stage 3: Implementation Plan - ATT-8141: Withdraw unsubstantiated V5.0.0 system verification record

**Ticket**: [ATT-8141](https://rainerblind.atlassian.net/browse/ATT-8141) | **Sub-task**: [ATT-8256](https://rainerblind.atlassian.net/browse/ATT-8256) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.8` | **Branch**: `feature/ATT-8141`  
**Requirement Mapping**: `REQ-PRO-088.1`, `REQ-PRO-098` (`REQ-PRO-098.1`..`REQ-PRO-098.5`)  
**Test Mapping**: `TST-PRO-088.1`, `TST-PRO-098` (`TST-PRO-098.1`..`TST-PRO-098.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-15  

---

## 1. Problem Description & Background
Under ASPICE 4.0 Finding `K1`, `docs/releases/V5.0.0/sys_verification_record.md` contained synthetic Pixel 8/6 serial numbers (`*-REF-*`) and longevity metrics authored by autonomous agents during `ATT-7560` without physical execution. While the release gating kept `V5.0.0` blocked (`verification_summary.md` = `BLOCKED`), the fabricated claims compromise evidence integrity. Furthermore, continuous real dogfooding rides recorded by the PO on the reference Google Pixel 10 are not yet ingested as release evidence. This plan resets the protocol to an authentic `OPEN` baseline, hardens release gating against placeholder serials and missing PO attestations, codifies governance rules against synthetic hardware evidence, and builds `tools/extract_pixel_field_evidence.py` to extract real Pixel 10 telemetry and coach the PO on remaining test gaps.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-088.1` (*System Verification Evidence Reset & Integrity*) & `REQ-PRO-098` (*Pixel Field Telemetry Extractor, Gap Coach & Governance*)
* **Test Mapping**: `TST-PRO-088.1` & `TST-PRO-098` (`TST-PRO-098.1`..`TST-PRO-098.4`) in `docs/tests/tst_process_stability.md`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Android Production Runtime Code Changes**: `app/src/main/` is 100% untouched.
2. **Release Gating Integrity**: Release candidate authorization remains strictly blocked unless all verification gates pass without open deviations.
3. **Pure Python Stdlib Resilience**: Tooling runs without heavy external dependencies, supporting offline/mock modes for automated unit testing.
4. **Parent Human Gate Invariance**: Terminal approval and release authorization remain exclusively reserved for the human PO.

---

## 4. Proposed Architectural Changes
* **Component 1 (`docs/releases/V5.0.0/sys_verification_record.md` & `TEMPLATE.md`)**:
  - Reset `V5.0.0/sys_verification_record.md` to `OPEN / NOT PERFORMED`, removing fabricated Pixel 8/6 serials and metrics.
  - Enrich `docs/releases/TEMPLATE.md` and the record with mandatory Human PO Attestation block (tester name, date, device model, serial/hardware ID, build SHA, and workout/FIT ID references).
* **Component 2 (`tools/generate_release_verification_summary.py`)**:
  - Update `collect_sys5_summary()` with regex detection blocking placeholder serials (`*-REF-*`, `SYS-HW-*` placeholders) and unbacked PASS claims lacking human attestation.
* **Component 3 (`tools/extract_pixel_field_evidence.py`)**:
  - CLI tool extracting workout telemetry from the connected Google Pixel 10 (`adb`) or local export folder (inspecting SQLite tables or `.fit`/`.tcx` files).
  - Pre-populates verification matrix with `EXTRACTED_AWAITING_PO_ATTESTATION` and generates a prioritized Verification Gap & Coaching Report across `SYS-VRF-001`..`008` and environments `E1-E5`.
* **Component 4 (Governance Rules & Checklists)**:
  - Update `.agents/rules/aspice_governance.md` with Rule 7.6 / Core Rule additions: agents prohibited from fabricating physical hardware results; physical execution tickets labeled `human-execution`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Low Risk)** — developer tooling, verification gating, and markdown evidence; executed by **`subagent_flash`**.
* **Step 1: Release Protocol Reset & Template Update**: Reset `docs/releases/V5.0.0/sys_verification_record.md` to OPEN and add Human PO Attestation block to `docs/releases/TEMPLATE.md`.
* **Step 2: Hardened Release Verification Gating**: Implement placeholder serial rejection and human attestation checks in `tools/generate_release_verification_summary.py`.
* **Step 3: Pixel Field Evidence Extractor & Gap Coach Tool**: Implement `tools/extract_pixel_field_evidence.py` supporting `adb` pulling and local folder parsing, table population, and gap report generation.
* **Step 4: Governance Rules & Living Documentation Update**: Update `.agents/rules/aspice_governance.md` with hardware integrity rule; mark `REQ-PRO-088.1` and `REQ-PRO-098` as `[Implemented]` in living docs.
* **Step 5: Unit Test Suites**: Author tests in `tools/test_generate_release_verification_summary.py` and `tools/test_extract_pixel_field_evidence.py`.
* **Tier 1 Test Command**:
  ```bash
  python3 -m unittest tools/test_generate_release_verification_summary.py tools/test_extract_pixel_field_evidence.py && python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit tests and governance verifier. Test `extract_pixel_field_evidence.py` against mock data and connected Pixel 10 in read-only mode.
* **Rollback**: Isolated branch `feature/ATT-8141` enables complete rollback without affecting `develop` or active sprint baselines.
