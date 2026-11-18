# Stage 1 Analysis: ATT-8200 - Fix silent ERROR gate-bypass, broken coverage JSON keys, and tag-path lookup in generate_release_verification_summary.py

**Ticket**: [ATT-8200](https://rainerblind.atlassian.net/browse/ATT-8200) | **Sub-task**: Pending creation (`[Analysis]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` (Jira ID `10372`, active unreleased) | **Active Sprint**: `2026-11-18_02_Spät` (ID `460`)  
**Branch**: `feature/ATT-8200` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

## 1. Problem Statement & Motivation
Three defects in `tools/generate_release_verification_summary.py` undermine `SWE.6` BP5 and `SUP.8` BP5 release gating: coverage threshold violations are silently dropped, Category 3/4 coverage dictionaries read nonexistent JSON keys, and canonical CI release tags (`V<versionName>__<versionCode>`) fail to resolve `docs/releases/V<versionName>/` dossiers.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Bug 1 — Silent Coverage Gate Bypass (`L243-246`, `L231`)**: `verify_coverage_thresholds()` (`tools/verify_requirement_governance.py:3276-3317`) emits `"ERROR: Gate violated: ..."` on all 4 threshold failures (never `"FAIL:"`). `collect_coverage_summary()` filters `if d.startswith("FAIL:"):` (`L245`), yielding `deviations = []`, `cov_gate = "PASS"` (`L504`), and `PASS (READY FOR RELEASE)` (`L480`). Additionally, subprocess fallback (`L231`) uses `check=True`, raising `CalledProcessError` when `verify_requirement_governance.py --coverage-json` exits `1` on failed thresholds (`L6295, L6381`) instead of parsing `stdout` JSON.
* **Bug 2 — Broken Coverage JSON Keys (`L255-256`)**: `collect_coverage_summary()` reads `category_3_code_without_spec` and `category_4_tst_without_req_citation`, whereas `TraceabilityCoverageReport.to_dict()` (`tools/verify_requirement_governance.py:2933-2934`) emits `category_3_code_tags_without_spec` and `category_4_tsts_not_cited_back`, returning `{}` for `cat3`/`cat4`.
* **Bug 3 — Release Tag Dossier Lookup Mismatch (`L78, L269, L328, L687`)**: `.github/workflows/android-release.yml:162-163` and `.agents/skills/release-manager/SKILL.md:215` pass `V<versionName>__<versionCode>` (e.g. `V5.0.0__273` per `docs/management/cm_plan.md:70`), whereas dossiers live in `docs/releases/V5.0.0/`. Meanwhile CI (`android-release.yml:108-109`) names the JUnit zip `build-evidence/junit-test-results-${TAG_NAME}.zip` using the raw tag (`V5.0.0__273`).
* **Independent Peer Review (`ATT-8157` Overlap)**: `ATT-8200` cites `--output-json` and `SYS-INT-003..006` drift in `docs/releases/README.md` and `TEMPLATE.md`. Live inspection confirms `ATT-8157` (`1750ab64`) already removed both and `ReleaseManagerSystemEvidenceContractTest.kt:108,119-120` enforces their absence; only `README.md` tag-vs-dossier folder clarification remains relevant.

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**: Fix `ERROR:`/`FAIL:` threshold deviation capture (and subprocess fallback JSON parsing) in `collect_coverage_summary()`; fix `cat3`/`cat4` JSON keys; normalize `V<ver>__<code>` -> `V<ver>` for dossier lookup/output while preserving raw tag + `versionCode` in the Markdown header and checking raw-tag JUnit archive first; clarify `docs/releases/README.md` if needed; add regression tests in `tools/test_generate_release_verification_summary.py`; update `REQ-PRO-074` / `TST-PRO-074`.
* **Out-of-Scope Non-Goals**: No Android `app/src/main/` changes; no edits to `.github/workflows/android-release.yml`, `tools/verify_requirement_governance.py`, or `ReleaseManagerSystemEvidenceContractTest.kt` forbidden strings (`--output-json`, `system_integration_results.json`); complexity confirmed `2 SP` (no split).

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-074` (`REQ-PRO-074.1`, `.3`, `.4`, `.5`) and `TST-PRO-074` in `docs/requirements/req_process_stability_part2.md:461-493` & `docs/tests/tst_process_stability_part2.md:465-482`.
* **Historical Origin & Commit Trace**: Introduced in `dc7b3d61` (`ATT-6138`, Sprint `2026-46.4`); `REQ-PRO-074.4` updated in `1750ab64` (`ATT-8157`); sharded in `f55c9fee` (`ATT-8206`).
* **Root Reason for Existing Formulation**: Enforces `SWE.6` BP5 / `SUP.8` release evidence aggregation using pure Python 3 stdlib while keeping legacy simulator JSON non-gating (`REQ-PRO-112`).
* **Preservation of Core Invariants**: Refining diagnostic prefixes, JSON keys, and tag-to-dossier normalization strengthens the intended gating invariants without altering `app/src/main/` or `REQ-PRO-112` non-gating simulator rules.

## Change Impact Analysis
* **Affected Requirements**: `REQ-PRO-074` (`REQ-PRO-074.1`, `.3`, `.4`, `.5`), `TST-PRO-074`; `SYS.2`/`SYS.3` (`docs/system/sysreq_*.md`, `system_context.md`, `system_architecture.md`) unaffected (zero sensor, RF, FGS, or external API changes per `REQ-PRO-131.4`).
* **Affected Architecture Elements**: `tools/generate_release_verification_summary.py` (`collect_unit_test_summary`, `collect_coverage_summary`, `collect_sys4_summary`, `collect_sys5_summary`, `render_markdown_summary`, `generate_release_verification_summary`), `docs/releases/README.md` (tag/dossier note only), `docs/requirements/req_process_stability_part2.md`, `docs/tests/tst_process_stability_part2.md`.
* **Verification Impact & Test Surface**: `tools/test_generate_release_verification_summary.py` (`TST-PRO-074`, add regression tests for `ERROR: Gate violated:`, `cat3`/`cat4` non-zero gap dict extraction, `V5.0.0__273` normalization + JUnit archive lookup), `python3 tools/verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **LOW** risk (pure Python 3 stdlib tooling, zero `app/src/main/` changes). Invariants: preserve `ReleaseManagerSystemEvidenceContractTest.kt` contract rules, raw-tag `junit-test-results-<tag>.zip` lookup compatibility, non-gating `collect_sys4_summary()`, and `Human Decision Gate`.

## 5. Architectural Strategy & High-Level Solution
* Add a helper `normalize_release_tag(tag: str) -> Tuple[str, str, Optional[str]]` returning `(raw_tag, dossier_tag, version_code)` where `V<ver>__<code>` strips `__\d+$` for `docs/releases/<dossier_tag>/` (unless `docs/releases/<raw_tag>` already exists).
* Check raw-tag JUnit archive first (`junit-test-results-{raw_tag}.zip`) with fallback to `dossier_tag`, and render `Release Tag`, `Version Code`, and `Release Dossier` in `render_markdown_summary()`.
* Match both `FAIL:` and `ERROR:` in `collect_coverage_summary()`, append fallback deviation whenever `not gate_ok`, parse `stdout` JSON even on non-zero subprocess exit, and read `category_3_code_tags_without_spec` / `category_4_tsts_not_cited_back`.
