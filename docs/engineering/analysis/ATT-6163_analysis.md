# Stage 1 Analysis: ATT-6163 - Trace software requirements to system requirements

**Ticket**: [ATT-6163](https://rainerblind.atlassian.net/browse/ATT-6163)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `feature/ATT-6163`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the ASPICE 4.0 assessment (baseline develop @ sprint/2026-45.11, Remediation S-04, SYS.2 BP5 & SWE.1 BP5), the assessment identified an absence of traceability between system requirements and software requirements:
* System requirements defined in `docs/system/sysreq_live_tracking.md` (`SYSREQ-TRK-001`..`006`) and `docs/system/sysreq_data_exchange.md` (`SYSREQ-DEX-001`..`006`) allocate to software architecture elements, but lack explicit derivation links to concrete software requirements (`REQ-*`).
* Software requirements stand alone without machine-checked traceability to parent `SYSREQ-*` or formal rationale classifying them as software-derived.
* To achieve ASPICE CL2 compliance across SYS.2 and SWE.1, bidirectional traceability between `SYSREQ` and `REQ` must be formally established and verified by automated governance tooling.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause**:
  1. System engineering work products (`SYS.2` in `docs/system/sysreq_*.md` and `SYS.3` in `docs/system/system_architecture.md`) were established recently under Epic ATT-6142 without retrofitting derivation links into the software requirements catalogue (`docs/requirements/`).
  2. `tools/verify_requirement_governance.py` previously verified only declared vs. allocated SYSREQ IDs within `docs/system/`, lacking checks for software requirement derivation, SYSREQ coverage, or detection of invalid/dangling cited `SYSREQ-*` references.
* **Independent Hypothesis Verification (4-Augen-Prinzip)**:
  - Codebase inspection of `docs/system/sysreq_*.md` confirms 12 declared system requirements (`SYSREQ-TRK-001`..`006` and `SYSREQ-DEX-001`..`006`).
  - Analysis of the 1075 software requirements reveals that sensor telemetry, GNSS, tracking lifecycle, and data export/import requirements refine the 12 system requirements, whereas UI components, presentation logic, mathematical formulas, and internal process tooling are purely software-derived.
  - Verification tooling must therefore support: (a) explicit `System Requirement` links, (b) explicit `Derived` markers with rationale, and (c) domain/prefix tailoring rules classifying internal software requirements as derived with documented rationale.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Author System-to-Software Requirement Traceability Matrix in `docs/system/sysreq_live_tracking.md` and `docs/system/sysreq_data_exchange.md` (and dedicated mapping in `docs/system/system_software_traceability.md`).
  2. Define domain and prefix tailoring rules in `docs/system/system_software_traceability.md` and `docs/project_governance.md` documenting rationale for software-derived requirements (e.g. `UI`, `UNT`, `PRO`, `PER`, `SET`, etc.).
  3. Extend `tools/verify_requirement_governance.py` with:
     - SYSREQ citation verification (all cited SYSREQs must exist in `docs/system/sysreq_*.md`).
     - SYSREQ coverage verification (every declared SYSREQ must be refined by $\ge 1$ software REQ).
     - Software requirement derivation verification (every SWE REQ has a valid parent SYSREQ, derived marker, or is covered by prefix tailoring).
  4. Implement automated test scenarios in `tools/test_verify_requirement_governance.py`.
  5. Register `REQ-PRO-076` and `TST-PRO-076` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md` with 100% master index parity.
* **Out-of-Scope Non-Goals**:
  - Modifying Android runtime APK code or UI components.
  - Renaming existing REQ or SYSREQ IDs.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirements `REQ-PRO-076` and `TST-PRO-076`.
* **Historical Origin & Commit Trace**: Net-new under Epic ATT-6142 (SYS.2 BP5 / SWE.1 BP5). Builds upon ATT-6121 (`88525c79`, dangling reference detection) and ATT-6160/ATT-6161 (SYS.2 baseline specifications).
* **Root Reason for Existing Formulation**: Previously, system and software levels operated as separate documentation silos. Establishing automated derivation bridges system-level constraints down to code-level verification.
* **Preservation of Core Invariants**: Existing link checks and master index parsers remain untouched; zero external Python dependencies introduced.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: Net-new `REQ-PRO-076`, `TST-PRO-076`.
* **Affected Architecture Elements**:
  - `docs/system/sysreq_live_tracking.md`
  - `docs/system/sysreq_data_exchange.md`
  - `docs/system/system_software_traceability.md`
  - `docs/project_governance.md`
  - `tools/verify_requirement_governance.py`
  - `tools/test_verify_requirement_governance.py`
  - `docs/requirements/req_process_stability.md` & `docs/tests/tst_process_stability.md`
  - `docs/requirements.md` & `docs/tests.md`
* **Verification Impact & Test Surface**:
  - `python3 tools/test_verify_requirement_governance.py`
  - `python3 tools/verify_requirement_governance.py`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`
* **Risk Assessment & Invariants**: Low risk. Isolated to documentation, governance verification tooling, and unit tests.

---

## 6. Architectural Strategy & High-Level Solution

1. **System Traceability Matrix**:
   - Map `SYSREQ-TRK-001`..`006` to concrete `REQ-TRK-*`, `REQ-CON-*`, `REQ-FIL-*`, `REQ-FUSION-*`, `REQ-SEN-*` requirements.
   - Map `SYSREQ-DEX-001`..`006` to concrete `REQ-DAT-*`, `REQ-EXP-*`, `REQ-EXT-*`, `REQ-MIG-*` requirements.
2. **Tailoring Rules**:
   - Establish normative domain tailoring in `docs/system/system_software_traceability.md` and `docs/project_governance.md`.
   - Prefix families (`UI`, `UNT`, `PER`, `PERF`, `SPT`, `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST`, `REV`, `STP`, `LOC`, `MON`, `PRI`, `SET`) are formally documented as SWE-derived.
3. **Governance Tooling**:
   - Implement `verify_system_software_traceability()` in `tools/verify_requirement_governance.py`.
   - Add CLI options `--verify-sys-trace` and integrate into standard CI verification run.
   - Add comprehensive unit test scenarios in `tools/test_verify_requirement_governance.py`.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Bidirectional mathematical parity across living documentation indexes must remain 100% green.
  2. Python 3 standard library only; zero runtime bytecode alterations.
  3. Every declared `SYSREQ-*` must have verified coverage by at least one software requirement.
