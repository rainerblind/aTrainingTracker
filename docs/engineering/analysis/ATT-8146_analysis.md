# Stage 1 Analysis: ATT-8146 - Fix SYSREQ coverage regex and report true SYS-to-SWE coverage

**Ticket**: [ATT-8146](https://rainerblind.atlassian.net/browse/ATT-8146)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Branch**: `feature/ATT-8146` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Finding `M2` (SWE.1 BP5, SYS.2 BP5), `tools/verify_requirement_governance.py --report-sys-coverage` currently prints bogus software requirement IDs (`REQ-DEX-001`..`006`, `REQ-TRK-001`..`006` extracted from inside `SYSREQ-*`) and includes process requirements (`REQ-PRO-054..056`, `REQ-PRO-069`, `REQ-PRO-076`) as system refinements, while omitting reverse (`SWE -> SYSREQ`) coverage. This creates false confidence (`100.0%`) when authentic `System-Refinement` coverage is `49 / 355` (`13.8%`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Defect 1 — Unbounded Substring Regex (`tools/verify_requirement_governance.py:3710-3716`)**:
  `if "|" in line and "SYSREQ-" in line and "REQ-" in line:` evaluates `True` for any line containing `SYSREQ-` alone, and `re.findall(r"REQ-[A-Z0-9_-]+", line)` matches the `REQ-DEX-*` / `REQ-TRK-*` suffix inside `SYSREQ-DEX-*` / `SYSREQ-TRK-*`.
* **Defect 2 — Unfiltered Process/Derived Prefixes (`:3738-3746` & `:3712-3716`)**:
  `verify_system_software_traceability()` records `SYSREQ-*` mentions in `docs/requirements/*.md` for any `REQ-*` heading regardless of `SYSTEM_REFINED_PREFIXES` vs `SWE_DERIVED_PREFIXES`, misclassifying `5` `REQ-PRO-*` governance specs (`REQ-PRO-054`, `055`, `056`, `069`, `076`) as system refinements.
* **Defect 3 — One-Directional Reporting (`:3776-3807`, `:4245-4255`)**:
  Only forward `SYSREQ -> SWE` coverage (`12/12 = 100.0%`) is computed and printed; reverse `System-Refinement` `SWE -> SYSREQ` coverage is omitted.
* **Independent Peer Review & Verification (Live `d5a3d1bb`)**:
  * Verified `--report-sys-coverage` emits non-existent `REQ-DEX-001`..`006` and `REQ-PRO-*` under `SYSREQ-DEX-001`..`006` / `SYSREQ-TRK-001`..`006`.
  * Measured true live counts: `1164` total REQs, `355` in `SYSTEM_REFINED_PREFIXES` (`CON`, `FIL`, `FUSION`, `SEN`, `TRK`, `WEA`, `DAT`, `EXP`, `EXT`, `MIG`, `GEO`, `MAP`, `NAV`, `ROU`). `48` unique `SYSTEM_REFINED_PREFIXES` REQs map via `docs/system/*.md` matrices + `1` inline-only (`REQ-TRK-071` -> `SYSREQ-TRK-004`) = **`49 / 355` (`13.80%`)** (`306` unlinked). All `12/12` declared `SYSREQ-*` remain genuinely covered by `>= 2` product REQs each.
  * Checked `tools/hoechstmass/tools/verify_requirement_governance.py` and `app/src/test/`: `verify_system_software_traceability()` does not exist in Hoechstmass; `test_scenario_40..43` (`tools/test_verify_requirement_governance.py:1630-1718`) and `System*ContractTest.kt` remain compatible if forward `report` keys are preserved. Secondary `REQ-[A-Z]+-[0-9]+` regexes at `:2965` and `:3077` also lack `(?<![A-Z0-9_-])` guards.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**: (1) Enforce `(?<![A-Z0-9_-])REQ-[A-Z0-9_-]+` boundary matching in `verify_system_software_traceability()` (and harden `:2965`/`:3077` if non-intrusive); (2) Exclude `SWE_DERIVED_PREFIXES` (`REQ-PRO-*`, etc.) from `SYSREQ` refinement sets (reporting them separately or excluding cleanly); (3) Compute and display bidirectional coverage in `--report-sys-coverage` (`SYSREQ -> Product REQ` and `Product REQ (System-Refinement) -> SYSREQ`, ~`13.8%`); (4) Add unit tests in `tools/test_verify_requirement_governance.py` for `SYSREQ-DEX-001` false-positive rejection, `REQ-PRO-*` exclusion, and bidirectional counts.
* **Out-of-Scope Non-Goals**: Manually backfilling the `306` unlinked `SYSTEM_REFINED_PREFIXES` REQs (`ATT-8146` is `1 SP` tooling fix); modifying `app/src/main/` or `docs/system/*.md` matrices.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-076` (`REQ-PRO-076.1`..`REQ-PRO-076.4`, `docs/requirements/req_process_stability.md:4410`) and `TST-PRO-076` (`docs/tests/tst_process_stability.md`).
* **Historical Origin & Commit Trace**: Introduced in `ATT-6163` (Sprint `2026-46.4`, building on `88525c79` / `b5e7d58f` / `95eb871b`).
* **Root Reason for Existing Formulation**: Enforced that all `12` declared `SYSREQ-*` have refining SWE REQs and zero dangling `SYSREQ-*` references exist.
* **Preservation of Core Invariants**: Preserve `12/12` `SYSREQ` forward gate (`--verify-sys-trace` exit `0`), `test_scenario_40..43` `report` schema (`sysreq_count`, `covered_count`, `coverage_pct`), pure Python stdlib, and zero `app/src/main/` changes.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-PRO-076.4` in `docs/requirements/req_process_stability.md` (with 4-field archaeology) to mandate boundary-safe regex, `SWE_DERIVED_PREFIXES` exclusion, and bidirectional `SWE -> SYSREQ` reporting.
* **Affected Architecture Elements**: `tools/verify_requirement_governance.py` (`verify_system_software_traceability`, `--report-sys-coverage` CLI output).
* **Verification Impact & Test Surface**: `tools/test_verify_requirement_governance.py` (`test_scenario_40..43` + new `TST-PRO-076.5`/bug regression tests), `python3 tools/verify_requirement_governance.py --verify-sys-trace --report-sys-coverage`.
* **Risk Assessment & Invariants**: **LOW** (`1 SP`, standard tooling bugfix; zero `app/src/main/` or `docs/system/*.md` changes; `ANT-Android-SDKs` stays `160000`).

---

## 6. Architectural Strategy & High-Level Solution
* Replace `"REQ-" in line` and `re.findall(r"REQ-[A-Z0-9_-]+", ...)` with `(?<![A-Z0-9_-])REQ-[A-Z0-9_-]+(?![A-Z0-9_-])` so `SYSREQ-DEX-001` never yields `REQ-DEX-001`.
* Filter `sys_to_swe` refinement sets strictly to `SYSTEM_REFINED_PREFIXES` (excluding `REQ-PRO-*` and other `SWE_DERIVED_PREFIXES`).
* Populate `report` with reverse coverage fields (`swe_refinement_total_count`, `swe_refinement_covered_count`, `swe_refinement_coverage_pct`, `swe_to_sys_map`) and print both directions in `--report-sys-coverage`.
