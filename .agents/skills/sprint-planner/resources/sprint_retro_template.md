# Sprint Review & Retrospective: Sprint [SPRINT_NAME] (`[RETRO_TICKET_KEY]`)

**Ticket**: [[RETRO_TICKET_KEY]](https://issue-tracker/[RETRO_TICKET_KEY]) (`Review & Retro`)  
**Sprint**: `Sprint [SPRINT_NAME]` (Branch `sprint/[SPRINT_NAME]`)  
**Target Release**: `[TARGET_RELEASE]`  
**Date**: [YYYY-MM-DD]  

---

## 1. Sprint Velocity & Delivery Summary

| Metric | Planned (Ceremony 1) | Completed & Accepted (Ceremony 2) | Deferred (Documented Deviation) | Completion Rate |
| :--- | :---: | :---: | :---: | :---: |
| **Work Tickets** | [N] | **[M]** | [K] | **[%]** |
| **Story Points (SP)** | [X] SP | **[Y] SP** | [Z] SP | **[%]** |
| **Accepted (`i.O.`)** | — | **[M] / [M]** (`100%`) | — | **100%** |
| **Follow-Up Tickets Logged** | — | [Count] | — | — |

---

## 2. Quantitative Sprint Process Metrics (PA 4.1 & PA 4.2)
<!-- Automatically populated via: python3 tools/jira_util.py --as coordinator sprint-metrics --record --post-to-retro -->

### 2.1 Quality Gate First-Time-Pass-Rates (FTPR) & Empirical Defect Escape Rates
| Gate / Stage | Sub-tasks | 1st-Time Pass | Revisions | FTPR % | Defect Escape Rate (p) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| Analysis (Gate 1) | [N1] | [P1] | [R1] | [%] | [p1] |
| Req & Test Spec (Gate 2) | [N2] | [P2] | [R2] | [%] | [p2] |
| Impl-Plan (Gate 3) | [N3] | [P3] | [R3] | [%] | [p3] |
| Implementation (Gate 4) | [N4] | [P4] | [R4] | [%] | [p4] |
| Test (Gate 5) | [N5] | [P5] | [R5] | [%] | [p5] |

* **Overall Gate FTPR**: `[%]` (evaluated across [Total] sub-tasks)
* **Human Acceptance Rate**: `[%]`
* **Reason Swiss Cheese Defect Reduction Model**:
  - Empirical Escape Rates: $p_1 = \dots, p_2 = \dots, p_3 = \dots, p_4 = \dots, p_5 = \dots$
  - Theoretical Cumulative Defect Escape Probability: $P(\text{escaped defect}) = \prod_{i=1}^5 p_i = \dots$

### 2.2 Statistical Process Control (SPC — PA 4.2) & Closed-Loop Improvement (PA 5.2)
* **Historical Baseline**: [N] recorded sprints in `docs/engineering/metrics/process_metrics.jsonl`
* **Control Limits ($\mu \pm 2\sigma$)**:
  - Overall FTPR: $\mu = \dots, \sigma = \dots \rightarrow [\text{LCL}: \dots, \text{UCL}: \dots]$
  - Human Acceptance: $\mu = \dots, \sigma = \dots \rightarrow [\text{LCL}: \dots, \text{UCL}: \dots]$
  - Completed Tickets: $\mu = \dots, \sigma = \dots \rightarrow [\text{LCL}: \dots, \text{UCL}: \dots]$
  - Completed SP: $\mu = \dots, \sigma = \dots \rightarrow [\text{LCL}: \dots, \text{UCL}: \dots]$
* **Statistical Control Status**: [In Statistical Control / Special Cause Variation Detected]
* **PA 4.2 $\rightarrow$ PA 5.2 Corrective Action**:
  - *Trigger*: In case of any `⚠️ PA 4.2 Special Cause Variation` outside $\mu \pm 2\sigma$, define explicit Root-Cause Analysis (RCA) and concrete process/skill countermeasure prior to sprint merge.

---

## 3. Ticket-by-Ticket Review Outcomes (Backlog Rank Order)

| # | Key | SP | Summary | Verdict | Follow-Up Ticket(s) |
| :-: | :--- | :-: | :--- | :---: | :--- |
| 1 | [[KEY-1]](https://issue-tracker/[KEY-1]) | [SP] | [Summary 1] | **Accepted** | — |
| 2 | [[KEY-2]](https://issue-tracker/[KEY-2]) | [SP] | [Summary 2] | **Accepted** | — |

---

## 4. Retrospective Analysis & Continuous Improvements
1. **What Went Well**:
   - [Highlights and effective practices]
2. **What Can Be Improved**:
   - [Pain points and identified bottlenecks]
3. **Action Items for Next Sprint**:
   - [Concrete process adjustments]
