# Stage 2: Requirement & Test Specification - ATT-8141: Withdraw unsubstantiated V5.0.0 system verification record

**Ticket**: [ATT-8141](https://rainerblind.atlassian.net/browse/ATT-8141)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-PRO-088` (Reset), `REQ-PRO-098` (Net-New)  
**Test Mapping**: `TST-PRO-088` (Reset), `TST-PRO-098` (Net-New)  
**Branch**: `feature/ATT-8141` | **Author**: AI Agent 2 (Auditor / Stage 2 Specifier) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (`REQ-PRO-088` & `REQ-PRO-098`)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **REQ-PRO-088.1 (System Verification Evidence Reset)**: `docs/releases/V5.0.0/sys_verification_record.md` SHALL be filed with authentic telemetry or open status, requiring explicit human PO attestation and prohibiting synthetic placeholder serials (`*-REF-*`) or automated unbacked PASS verdicts.
* **REQ-PRO-098.1 (Telemetry Extraction & Gap Coaching)**: `tools/extract_pixel_field_evidence.py` SHALL extract authentic workout telemetry from the PO's Google Pixel 10 (`66020DLCR002FL`), pre-populate `SYS-VRF-*` rows with `EXTRACTED_AWAITING_PO_ATTESTATION`, and output a prioritized coverage-gap report for unverified sensor combinations.
* **REQ-PRO-098.2 (Release Guarding)**: `tools/generate_release_verification_summary.py` SHALL block release authorization (`BLOCKED`) when placeholder serials (`*-REF-*`), unbacked passes, or missing human PO attestations are detected.
* **REQ-PRO-098.3 (Protocol Reset)**: `docs/releases/TEMPLATE.md` and `docs/releases/V5.0.0/sys_verification_record.md` SHALL incorporate mandatory Human PO Attestation metadata and reset to `OPEN / NOT PERFORMED`.
* **REQ-PRO-098.4 (Human-Execution Governance)**: `.agents/rules/aspice_governance.md` SHALL prohibit autonomous agents from authoring physical test results; physical verification tickets mandate label `human-execution`.
* **REQ-PRO-098.5 (Tooling Test Parity)**: Extractor and release gating tests SHALL verify placeholder blocking and gap report emission with 100% test pass rate.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-088.1` & `TST-PRO-088.1` (Release Verification Evidence Execution).
* **Historical Origin & Commit Trace**: Added in `386dc268` (`ATT-7560`, Sprint `2026-46.5`) to satisfy evaluation finding F3.
* **Root Reason for Existing Formulation**: Enforced system-level verification evidence before tagging V5.0.0, but lacked human-in-the-loop gating, causing agents to synthesize passing runs.
* **Preservation of Core Invariants**: Retains SYS.5 verification gating while restoring evidence integrity—gating now mandates authentic Pixel 10 workout telemetry and human PO sign-off rather than permitting fabricated passes.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Clean Protocol)**: *Given* `docs/releases/V5.0.0/sys_verification_record.md`, *When* inspected, *Then* fabricated Pixel 8/6 serials are removed and rows report authentic telemetry or `OPEN / NOT PERFORMED`.
* **Criterion 2 (Gating Enforcement)**: *Given* a record with placeholder serials (`*-REF-*`) or missing PO attestation, *When* `generate_release_verification_summary.py` runs, *Then* status is `BLOCKED`.
* **Criterion 3 (Extraction & Gap Report)**: *Given* Pixel 10 workout data, *When* `extract_pixel_field_evidence.py` runs, *Then* rows are pre-populated with `EXTRACTED_AWAITING_PO_ATTESTATION` and an actionable sensor gap report is generated.

---

## 4. Test Specification (`TST-PRO-088` & `TST-PRO-098`)
* **TST-PRO-088.1 (Protocol Completeness & PO Attestation Contract)**: Verify `sys_verification_record.md` exists, rejects synthetic serials, and enforces human PO attestation.
* **TST-PRO-098.1 (Placeholder Detection Test)**: In `tools/test_generate_release_verification_summary.py`, assert `collect_sys5_summary()` blocks `*-REF-*` placeholder serials.
* **TST-PRO-098.2 (Human PO Attestation Gating Test)**: In `tools/test_generate_release_verification_summary.py`, assert missing PO signature blocks release gating.
* **TST-PRO-098.3 (Pixel Extractor & Gap Coach Test)**: Unit test `extract_pixel_field_evidence.py` parsing session telemetry and outputting prioritized gap recommendations.
* **TST-PRO-098.4 (Governance Parity Test)**: Verify `verify_requirement_governance.py` reports 100% parity across `docs/requirements.md` and `docs/tests.md`.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-088.1` | Review / Schema | `docs/releases/V5.0.0/sys_verification_record.md` | `REQ-PRO-088.1` | Specified |
| `TST-PRO-098.1` | Unit Test | `tools/test_generate_release_verification_summary.py` | `REQ-PRO-098.2` | Specified |
| `TST-PRO-098.2` | Unit Test | `tools/test_generate_release_verification_summary.py` | `REQ-PRO-098.2` | Specified |
| `TST-PRO-098.3` | Unit Test | `tools/test_extract_pixel_field_evidence.py` | `REQ-PRO-098.1` | Specified |
| `TST-PRO-098.4` | Governance | `tools/verify_requirement_governance.py` | `REQ-PRO-098.4` | Specified |
