# Stage 1 Analysis: ATT-8168 - Correct sprint metrics statistics and suspend unsupported CL3 and CL4 capability claims

**Ticket**: [ATT-8168](https://rainerblind.atlassian.net/browse/ATT-8168)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_03_Nacht`  
**Branch**: `feature/ATT-8168` | **Author**: AI Agent 1 (Implementer, `flash`) | **Date**: 2026-11-17  

---

## 1. Problem Statement & Motivation
ASPICE 4.0 Audit Finding `Observation (PA 4.x)` identified statistical defects in `tools/sprint_metrics.py` and unsupported Capability Level 3/4/5 claims in documentation:
1. **Unbounded SPC Limits**: Percentage metrics (`overall_ftpr`, `human_acceptance`) use unbounded $\mu \pm 2\sigma$, producing `UCL > 100%` (`116.58%` and `118.01%` on current `process_metrics.jsonl`, `n=18`), and mix incompatible process eras without baseline resets.
2. **Misnamed Acceptance Rate**: `human_acceptance_rate_percent` equals `completion_rate_percent` (`completed_tickets / total_tickets`) and counts tickets still in `Final Review (Human)` / `Final Review (Agent)` as human-accepted.
3. **Fabricated Escape Probability**: Zero-revision gates substitute nominal `p = 0.02`, yielding `2.37e-08` in Sprint `2026-46.6` alongside `17` open bugs.
4. **Suppressed Plausibility Warning**: `100%` Gate 1–5 FTPR blind-spot warnings are suppressed whenever Gate 6 records bounces (`rev6 + bounces6`).
5. **Unsupported CL3/CL4/CL5 Claims**: Governance docs present PA 3.x–5.x as current operational implementation despite CL2 not yet being achieved.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Unclamped Normal SPC & Unpartitioned History)**: `calculate_spc_limits()` (`tools/sprint_metrics.py` L568–653) applies normal $\mu \pm 2\sigma$ (`ucl = round(mu + 2 * sigma, 2)` at L601) to proportions instead of binomial/p-chart limits clamped to `[0, 100]`, over all 18 mixed-era JSONL rows (`process_metrics.jsonl` L1–19).
* **RCA 2 (Status & Formula Conflation)**: `is_completed` (L280) includes `final review*` states and `human_acceptance_rate` (L499–502) duplicates `completion_rate_percent` (L526) instead of measuring terminal human acceptance (`Erledigt`/`Done`/`Closed`).
* **RCA 3 (Synthetic Floor & Blind-Spot Masking)**: L494 substitutes `0.02` when `p == 0`, while L513 adds `rev6 + bounces6` into `total_revisions_all`, silencing L514–517 even when Gate 6 catches escapes missed by Gates 1–5.
* **RCA 4 (Premature Maturity Framing)**: `REQ-PRO-038` docs (`docs/project_protocol.md`, `tools/hoechstmass/`) frame PA 3.1–5.2 as active operational status instead of future targets pending CL2 attainment.
* **Independent Peer Review & Verification**:
  * Verified `tools/sprint_metrics.py` line shifts post-`ATT-8165`: nominal `p=0.02` is L490–496 (ticket cited L338–343), `human_acceptance_rate` is L498–502 (cited L346–350), plausibility check is L511–517 (cited L359–363), and `tools/hoechstmass/tools/sprint_metrics.py` mirrors the pre-`ATT-8165` bugs (L337–363, L408–475).
  * Executed `calculate_spc_limits()` against `docs/engineering/metrics/process_metrics.jsonl` (`n=18`): confirmed `overall_ftpr` `UCL=116.58`, `human_acceptance` `UCL=118.01`, and `2026-46.6` (L11) `theoretical_cumulative_escape_prob=2.37e-08` with `open_bugs=17` and `plausibility_warnings=[]`.
  * Audited `docs/project_protocol.md` (L41–55), `tools/hoechstmass/docs/project_protocol.md` (L96–110), `tools/hoechstmass/README.md` (L77–92), `tools/sprint_metrics.py` (L5), and `docs/presentations/agile_agentic_aspice_beamer.tex` (L1–1047): presentation contains zero CL3/CL4 achievement claims; `HoechstmassCoreFrameworkContractTest.kt` (L223–254) pins exact strings `"Level 1: Performed"`..`"Level 5: Innovating"` and `"PA 1.1"`..`"PA 5.2"`, which must remain as explicitly marked **Target / Suspended** roadmap entries.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals (`2 SP`)**:
  1. Fix `tools/sprint_metrics.py` (and `tools/hoechstmass/tools/sprint_metrics.py`): binomial/p-chart limits clamped to `[0, 100]`, `process_version` baseline reset, true human acceptance vs completion separation, removal of fabricated `p=0.02` escape product in favor of empirical escape rate, and Gate 1–5 plausibility check independent of Gate 6 bounces.
  2. Update `tools/test_sprint_metrics.py` (and `tools/hoechstmass/tools/test_hoechstmass_tools.py` if mirrored) with deterministic unit tests.
  3. Mark CL3/CL4/CL5 claims in `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, `tools/hoechstmass/README.md`, and `sprint_metrics.py` docstrings as target/informative until CL2 is verified (bumping `Version` per `REQ-PRO-120`).
* **Out-of-Scope Non-Goals**: `app/src/main/` changes; deleting `PA 1.1–5.2` matrix keys asserted by `HoechstmassCoreFrameworkContractTest.kt`; altering historical `process_metrics.jsonl` outcomes beyond schema-safe `process_version` annotation if needed.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-PRO-038` (*PA 4.1/4.2 Metrics*) and `REQ-PRO-092` (*Process Metrics Credibility*) in `docs/requirements/req_process_stability.md`; preserves `REQ-PRO-120` work-product headers and `REQ-PRO-035` contract markers.
* **Historical Origin & Commit Trace**: `REQ-PRO-038` (`ATT-5765`/`ATT-5764`), `REQ-PRO-092` (`ATT-7559`), `REQ-PRO-120` (`2de2a41c`).
* **Root Reason for Existing Formulation**: Early SPC used rapid normal approximation, `0.02` floor avoided zero-product collapse, and PA 1.1–5.2 matrix documented full ASPICE taxonomy.
* **Preservation of Core Invariants**: Preserve existing `process_metrics.jsonl` schema fields (`verify_sprint_metrics` L650–669), `TST-PRO-107.3` rubber-stamping alert (`test_jira_accounts.py` L1923–1946), and `HoechstmassCoreFrameworkContractTest` / `AspiceSweAuditContractTest` contract markers.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-PRO-038`, `REQ-PRO-092` (`docs/requirements/req_process_stability.md`) and `TST-PRO-038`, `TST-PRO-092` (`docs/tests/tst_process_stability.md`).
* **Affected Architecture Elements**: `tools/sprint_metrics.py`, `tools/hoechstmass/tools/sprint_metrics.py`, `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, `tools/hoechstmass/README.md`.
* **Verification Impact & Test Surface**: `tools/test_sprint_metrics.py`, `tools/test_jira_accounts.py`, `tools/verify_requirement_governance.py --check-doc-headers`, `HoechstmassCoreFrameworkContractTest`, `AspiceSweAuditContractTest`.
* **Risk Assessment & Invariants**: **LOW** — offline Python tooling & governance doc fixes; zero `app/src/main/` changes; backward-compatible JSONL schema and contract test markers preserved.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: `app/src/main/` untouched; `ANT-Android-SDKs` untouched; `REQ-PRO-120` monotonic `Version` bump + `Reviewed` status on edited governed docs; zero live Jira API calls in unit tests (`Rule 5`).
* **Risk Rating**: **LOW** (`2 SP` bounded tooling & documentation remediation).
