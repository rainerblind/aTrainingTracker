# Lean AAA v2 Experiment Protocol & KW47 Baseline

**Document ID**: `DOC-MGT-LEAN-AAA`  
**Document Identifier**: `EXP-LEAN-AAA-V2`  
**Standard**: ASPICE 4.0 `MAN.3` / `SUP.1` / `PA 4.1–5.2` (Measured Method Experiment)  
**Target Release**: `V5.0.0` | **Version**: 1.0.0  
**Status**: Approved | **Approver**: Rainer Blind (PO) | **Last Review Date**: 2026-11-21  
**Governing Ticket**: [ATT-9538](https://rainerblind.atlassian.net/browse/ATT-9538) (Parent Epic [ATT-9537](https://rainerblind.atlassian.net/browse/ATT-9537))  

---

## 1. Purpose & Thesis

This protocol governs the **two-week Lean Agile Agentic ASPICE (`aaa-v2`) experiment** on `aTrainingTracker`.  
**Core Thesis**: *Lean, truthful ASPICE for AI agents — which practices actually catch defects, at what human cost?* The Android application is the test bed; the engineering process is measured empirically like a product.

---

## 2. Experiment Window, Tagging & Baseline Anchor

| Parameter | Value |
| :--- | :--- |
| **Baseline Anchor** | `develop @ 508e528d` (`2026-11-15` to `2026-11-21`, ISO week `2026-KW47`, pre-`2b7cc003` workflow changes) |
| **Baseline Artifact** | `docs/engineering/metrics/baseline_2026-KW47.json` |
| **Baseline `process_version`** | `"2.0"` (in `docs/engineering/metrics/process_metrics.jsonl`) |
| **Experiment `process_version`** | `"aaa-v2"` (used to partition `process_metrics.jsonl` and `method_scorecard.jsonl`) |
| **Duration** | **2 ISO weeks** (`KW48` + `KW49`) |
| **Target Start / Stop Dates** | **2026-11-22** $\rightarrow$ **2026-12-06** *(fixed upon PO approval with `Human-Approved` trailer)* |
| **Evaluation Ticket** | `ATT-9548` (report `docs/management/lean_aaa_v2_evaluation.md` + independent second-vendor CL re-assessment) |

---

## 3. Hypotheses & Quantitative Success Criteria

| ID | Hypothesis | KW47 Baseline (`508e528d`) | Two-Week `aaa-v2` Target |
| :--- | :--- | :--- | :--- |
| **H1 (Effort Shift)** | Risk-tiered documentation and automated gates shift engineering effort from prose/ceremony toward product code & tests. | Prod code lines added: **18.6%** (`58,726 / 315,473`); commits touching `app/src/main/`: **52.3%** (`161 / 308`); per-ticket docs: **13.2%** (`41,730` lines; `5,866` files in `docs/engineering/`). | Prod code share **$\ge 25\%$** (stretch $\ge 30\%$); commits touching `app/src/main/` **$\ge 65\%$**; per-ticket doc lines added reduced by **$\ge 50\%$**. |
| **H2 (Defect Detection Non-Inferiority)** | Replacing agent-prose Gates 1–5 with automated checks while preserving Stage 6 Cold Review (and second-vendor Tier A Stage 6) does not degrade defect detection. | Automated/Jira Gates 1–5 revision rate: **6.16%** (`54 / 877` across 15 Schichten; Claude sample: `1 / 64` = `1.56%`); Stage 6 bounce rate: **34.5%** (`50 / 145` tickets, `65` bounces; Claude trace: `6 / 10`); KW47 `process-escape` bugs: **5**. | Stage 6 continues catching real defects (no verification blindness); Tier-2 / CI / field `process-escape` bugs **$\le 5$/week**; **0** unaddressed Critical/Major field data-loss regressions. |
| **H3 (Bounded Human Cost)** | Replacing ad-hoc per-ticket/per-Schicht human checkpoints with one weekly PO decision session keeps human effort sustainable and recorded. | **93** docs carrying `Pending PO Sign-Off`; Layer-4 human review not performed since `2026-11-13`; PO minutes unmeasured (`null`). | **30–60 min/week** measured in the weekly PO session log, with **100%** of weekly baseline decisions carrying an authentic `Human-Approved` record. |
| **H4 (Truthful CL Profile)** | Focus on real evidence, independent QA/Tier-A review, and weekly PO sign-off improves assessed ASPICE capability honestly. | **CL1 on 9/15** VDA-scope processes; **CL2 on 0/15** (`docs/project_protocol.md` v1.0.3, stricter Claude rating). | **CL1 on 15/15** VDA-scope processes and **CL2 on core set** (`SWE.1`–`SWE.4`, `SUP.8`–`SUP.10`), backed by archived second-vendor audit + PO sign-off. |

---

## 4. Metric Definitions & Reproducible Data Sources

1. **Effort Split by Path Class (`git log --no-merges --numstat`)**:
   * `production_code`: `app/src/main/**`
   * `tests`: `app/src/test/**`, `app/src/androidTest/**`
   * `per_ticket_docs`: `docs/engineering/{analysis,test_specs,plans,walkthroughs,tickets}/**`
   * `living_specs`: `docs/requirements/**`, `docs/tests/**`, `docs/requirements.md`, `docs/tests.md`, `docs/system/**`, `docs/architecture*`
   * `process_tooling_and_other_docs`: all remaining paths (`tools/**`, `.agents/**`, `docs/engineering/{retros,gates,qa_audits,reviews,metrics,aspice_audits,code_reviews,test_evidence}/**`, `docs/management/**`, etc.)
2. **Product Commit Share (`git log --no-merges`)**: Ratio of non-merge commits modifying at least one file in `app/src/main/` vs. commits touching zero production code.
3. **Defect Detection per Practice**:
   * *Gates 1–5 Revision Rate*: `revisions / total_subtasks` aggregated across `docs/engineering/metrics/process_metrics.jsonl` (plus independent audit spot-checks).
   * *Stage 6 Bounce Rate*: `bounced_tickets / evaluated_tickets` and total `bounces` from `process_metrics.jsonl` / Jira changelogs.
   * *Tier-2 / CI / Field Escapes*: Jira `Bug` issues created in the ISO week carrying label `process-escape` or reported from physical PO device testing.
4. **Throughput & Cost per Ticket**: Completed tickets & completed Fibonacci Story Points per ISO week (`process_metrics.jsonl` / Jira), Schicht wall-clock hours per completed ticket, and agent quota utilization (when available).
5. **Human PO Minutes**: Explicit duration logged in the weekly `Wochen Review & Retro` PO decision session record (`ATT-9545`); never synthesized.
6. **Assessed Capability Profile**: 15 VDA-scope ASPICE 4.0 processes recorded per `docs/project_protocol.md` claim rule (only updated via archived independent assessment in `docs/engineering/aspice_audits/` + PO sign-off).

---

## 5. Abort / Pause Criteria

The experiment SHALL be paused or rolled back immediately if any of the following triggers occur:
1. **Field Reliability Regression**: Field crash/ANR or data-loss bugs double relative to KW47 baseline (or $\ge 2$ Critical field escapes occur within a single ISO week).
2. **Verification Blindness**: Tier-2 / CI `process-escape` bugs exceed `2×` KW47 baseline (`> 10`/week) while Stage 6 bounce yield drops below `10%`.
3. **Unsustainable PO Overhead**: Measured human PO decision/review effort exceeds **90 minutes/week** for two consecutive weeks.
4. **Explicit PO Directive**: The human Product Owner halts the experiment in a weekly review or interactive session.

---

## 6. Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-21 | Workflow Architect (`Interactive-PO`) | `ATT-9538` | Initial Lean AAA v2 experiment protocol and frozen KW47 baseline metrics (`docs/engineering/metrics/baseline_2026-KW47.json`, `develop @ 508e528d`). Created under explicit interactive PO `Rule 1.3` fast-track deviation (non-code management artifact). |
