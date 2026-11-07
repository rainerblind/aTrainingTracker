# Stage 1 Analysis: ATT-5765 - Höchstmaß (ASPICE Level 4/5 — Part 1/2): Automated Quantitative Sprint Metrics (PA 4.1) & SPC (PA 4.2)

**Ticket**: [ATT-5765](https://rainerblind.atlassian.net/browse/ATT-5765)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5765`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
Our **Höchstmaß — Agile Agentic ASPICE (AAA)** engineering workflow fulfills **ASPICE Level 3 (*Established*)** across `SWE.1–SWE.6` and exercises continuous process improvement (**Level 5 *Innovating***) via mandatory retrospectives. To formally achieve **ASPICE Level 4 (*Predictable*)**, the Process Assessment Model requires:
* **`PA 4.1` (Quantitative Analysis)**: Automated quantitative process metrics collection across sprints and explicit quality objectives.
* **`PA 4.2` (Quantitative Control)**: Statistical Process Control (SPC) with defined control limits and automated outlier / special cause variation detection.
Because gate decisions (`PASS` / `REVISION`) and role provenance are already recorded in Jira, metrics can be gathered 100% automatically without manual overhead.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Current State**: Metrics are evaluated manually in retrospective markdown files without automated aggregation, longitudinal time-series persistence, or statistical control limits.
* **Independent Peer Review & Verification**:
  * Audited `tools/jira_util.py`: Subtask gate comments strictly follow `[ROLE: agent2]` attribution with explicit `PASS` or `REVISION` verdicts, allowing deterministic extraction of First-Time-Pass-Rates (`FTPR`) and Swiss Cheese layer escape probabilities ($p_1 \dots p_5$).
  * Audited `tools/verify_requirement_governance.py`: `ALLOWED_ENGINEERING_ENTRIES` restricts `docs/engineering/` to canonical subdirectories and will reject `docs/engineering/metrics/` unless explicitly whitelisted.
  * Verified configuration in `tools/hoechstmass/hoechstmass_config.json`: Also specifies `allowed_engineering_entries`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Implement `sprint-metrics` command in `tools/jira_util.py` (and mirror in `tools/hoechstmass/tools/jira_util.py`) with `--sprint`, `--record`, `--post-to-retro`, and `--backfill=N`.
  2. Calculate Gate 1–5 FTPR, Swiss Cheese escape probabilities, Human Acceptance Rate, throughput, and risk mix.
  3. Persist time-series to `docs/engineering/metrics/process_metrics.jsonl` with idempotent upsert.
  4. Implement SPC control limits ($\mu \pm 2\sigma$) and `PA 4.2` Special Cause Variation detection.
  5. Update `tools/verify_requirement_governance.py`, `hoechstmass_config.json`, and Rule 8 in `aspice_governance.md` to whitelist `metrics/`.
  6. Author automated Python unit tests covering metric calculations, SPC math, and backfill.
* **Out-of-Scope Non-Goals**:
  * Android application runtime code changes in `app/src/main/`.
  * Advanced visualization charts (reserved for Part 2/2, `ATT-5764`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Requirement Mapping**: Net-new requirement `REQ-PRO-038` (*Automated Quantitative Sprint Metrics & Statistical Process Control*), extending `REQ-PRO-014`, `REQ-PRO-027`, and `REQ-PRO-035`.
* **Preservation of Core Invariants**: Existing `jira_util.py` commands and Jira authentication remain intact; zero regression in verification scripts.

---

## 5. Architectural Strategy & High-Level Solution
* **CLI Engine**: `sprint-metrics` subcommand parsing Agile board sprint issues and comments via REST API.
* **Time-Series Persistence**: `docs/engineering/metrics/process_metrics.jsonl` storing one JSON record per sprint.
* **SPC Statistics**: Compute rolling mean $\mu$ and standard deviation $\sigma$ from `process_metrics.jsonl`, asserting control limits against current sprint.
* **Governance Whitelist**: Add `"metrics"` to `ALLOWED_ENGINEERING_ENTRIES` in governance tooling.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero runtime app modifications; backwards-compatible Jira CLI; 100% unit and contract test pass rate.
* **Risk Rating**: **LOW** — Pure tooling, metrics aggregation, and governance whitelist update.
