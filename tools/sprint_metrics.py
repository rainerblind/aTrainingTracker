#!/usr/bin/env python3
"""
tools/sprint_metrics.py

Höchstmaß (ASPICE Level 4/5): Automated Quantitative Sprint Metrics (PA 4.1)
& Statistical Process Control (PA 4.2).

Extracts Gate 1–5 First-Time-Pass-Rates (FTPR), empirical Swiss Cheese layer
defect escape probabilities (p1..p5), Human Acceptance Rate, and velocity directly
from Jira [ROLE: agent2] subtask review comments. Persists time-series to
docs/engineering/metrics/process_metrics.jsonl and calculates SPC control limits (mu +/- 2sigma).
"""

import datetime
import json
import math
import os
import re
import sys
from pathlib import Path

# Subtask stage markers mapping to Gate numbers
STAGE_MAP = {
    1: {"prefix": "[analysis]", "name": "Analysis (Gate 1)"},
    2: {"prefix": "[req & test spec]", "name": "Req & Test Spec (Gate 2)"},
    3: {"prefix": "[impl-plan]", "name": "Impl-Plan (Gate 3)"},
    4: {"prefix": "[implementation]", "name": "Implementation (Gate 4)"},
    5: {"prefix": "[test]", "name": "Test (Gate 5)"},
    6: {"prefix": "[final review (agent)]", "name": "Final Review (Agent) (Gate 6)"},
}

FINAL_REVIEW_BOUNCE_RE = re.compile(
    r"(?:\[FINAL REVIEW \(AGENT\)\s*[—\-]+\s*BOUNCE\s+(\d+)/3\]|Final Review \(Agent\)\s*BOUNCE\s*\(?(\d+)/3\)?)",
    re.IGNORECASE,
)
FINAL_REVIEW_PASS_RE = re.compile(
    r"(?:\[FINAL REVIEW \(AGENT\)\s*[—\-]+\s*PASS\]|Final Review \(Agent\)\s*PASS)",
    re.IGNORECASE,
)
FINAL_REVIEW_ESCALATED_RE = re.compile(
    r"(?:\[FINAL REVIEW \(AGENT\)\s*[—\-]+\s*ESCALATED|Final Review \(Agent\)\s*ESCALATED)",
    re.IGNORECASE,
)

DEFAULT_METRICS_PATH = "docs/engineering/metrics/process_metrics.jsonl"


def identify_gate_number(summary):
    """Determines Gate number (1..5) from subtask summary prefix."""
    s_lower = summary.lower()
    for g in range(1, 6):
        data = STAGE_MAP[g]
        if data["prefix"] in s_lower:
            return g
    return None


def parse_final_review_agent_verdicts_from_comments(comments):
    """
    REQ-PRO-058.4 / TST-PRO-058.4:
    Parses Stage 6 ('Final Review (Agent)') verdicts from parent issue comments.
    Returns list of verdict strings: ['PASS'], ['BOUNCE', 'PASS'], ['BOUNCE', 'BOUNCE', 'BOUNCE', 'ESCALATED'], etc.
    """
    verdicts = []
    for c in (comments or []):
        body = c.get("body", "")
        if FINAL_REVIEW_BOUNCE_RE.search(body):
            verdicts.append("BOUNCE")
        elif FINAL_REVIEW_ESCALATED_RE.search(body):
            verdicts.append("ESCALATED")
        elif FINAL_REVIEW_PASS_RE.search(body):
            verdicts.append("PASS")
    return verdicts


def parse_verdicts_from_comments(comments):
    """
    Parses Gate review verdicts from subtask comments.
    Identifies agent2 review comments and determines if they are PASS or REVISION.
    Returns list of verdict strings: ['PASS'], ['REVISION', 'PASS'], etc.
    """
    verdicts = []
    for c in comments:
        body = c.get("body", "")
        author_name = c.get("author", {}).get("displayName", "")
        # Reviewer comment identification: role tag or display name
        is_agent2 = (
            "[ROLE: agent2]" in body
            or "[Automated comment by AI Agent 2" in body
            or "agent2" in author_name.lower()
            or "reviewer" in author_name.lower()
            or "auditor" in author_name.lower()
        )
        if not is_agent2:
            continue

        body_upper = body.upper()
        # Check verdict
        if "REVISION" in body_upper or "REVISION REQUIRED" in body_upper:
            verdicts.append("REVISION")
        elif "PASS" in body_upper:
            verdicts.append("PASS")

    return verdicts


def parse_sprint_gate_metrics(parent_issues, sprint_info):
    """
    Calculates PA 4.1 Quantitative Process Metrics from a list of parent issues and their subtasks.
    Each parent issue dict must contain 'fields' with 'summary', 'status', 'priority', 'subtasks',
    and optional customfield story points.
    """
    sprint_id = sprint_info.get("id", 0)
    sprint_name = sprint_info.get("name", "Unknown Sprint")

    # 1. Throughput & Story Points
    total_tickets = len(parent_issues)
    completed_tickets = 0
    total_sp = 0.0
    completed_sp = 0.0
    risk_counts = {"Low": 0, "Medium": 0, "High": 0, "Highest": 0}

    # Problem trends containers (REQ-PRO-063)
    total_bugs = 0
    open_bugs = 0
    closed_bugs = 0
    by_severity = {"Critical": 0, "Major": 0, "Minor": 0, "Cosmetic": 0}
    open_by_severity = {"Critical": 0, "Major": 0, "Minor": 0, "Cosmetic": 0}
    closed_by_severity = {"Critical": 0, "Major": 0, "Minor": 0, "Cosmetic": 0}

    # Gate statistics containers
    gate_data = {
        g: {"total": 0, "passed_first_time": 0, "revisions": 0, "bounces": 0}
        for g in range(1, 7)
    }

    for issue in parent_issues:
        fields = issue.get("fields", {})
        status_name = fields.get("status", {}).get("name", "").lower()
        is_completed = status_name in ("erledigt", "closed", "done", "final review (human)", "final review (agent)", "final review")

        if is_completed:
            completed_tickets += 1

        # Story points
        sp_val = fields.get("customfield_10016") or fields.get("customfield_10026") or fields.get("story_points")
        try:
            sp_num = float(sp_val) if sp_val is not None else 0.0
            total_sp += sp_num
            if is_completed:
                completed_sp += sp_num
        except (ValueError, TypeError):
            pass

        # Risk distribution from Priority
        priority_name = fields.get("priority", {}).get("name", "Medium")
        if priority_name in risk_counts:
            risk_counts[priority_name] += 1
        else:
            risk_counts["Medium"] += 1

        # Problem resolution tracking (REQ-PRO-063)
        itype = fields.get("issuetype", {})
        itype_id = str(itype.get("id", ""))
        itype_name = itype.get("name", "").lower()
        is_bug = itype_id == "10006" or itype_name in ("bug", "fehler")
        if is_bug:
            total_bugs += 1
            is_bug_closed = status_name in ("erledigt", "closed", "done")
            if is_bug_closed:
                closed_bugs += 1
            else:
                open_bugs += 1

            detected_sev = None
            labels = fields.get("labels", [])
            for l in labels:
                ll = str(l).strip().lower()
                if ll.startswith("severity-"):
                    cand = ll.replace("severity-", "").capitalize()
                    if cand in by_severity:
                        detected_sev = cand
                        break
                elif ll.capitalize() in by_severity:
                    detected_sev = ll.capitalize()
                    break

            if not detected_sev:
                desc = fields.get("description", "")
                m_sev = re.search(r"(?:\*\*Severity\*\*|Severity:?)\s*([A-Za-z]+)", desc, re.I)
                if m_sev:
                    cand = m_sev.group(1).capitalize()
                    if cand in by_severity:
                        detected_sev = cand

            if not detected_sev:
                p_map = {"Highest": "Critical", "High": "Major", "Medium": "Minor", "Low": "Cosmetic", "Lowest": "Cosmetic"}
                detected_sev = p_map.get(priority_name, "Major")

            by_severity[detected_sev] += 1
            if is_bug_closed:
                closed_by_severity[detected_sev] += 1
            else:
                open_by_severity[detected_sev] += 1

        # Inspect parent comments for Stage 6 ('Final Review (Agent)') verdicts
        parent_comments = issue.get("comments", []) or fields.get("comment", {}).get("comments", [])
        fr_verdicts = parse_final_review_agent_verdicts_from_comments(parent_comments)
        if fr_verdicts:
            gate_data[6]["total"] += 1
            bounce_cnt = sum(1 for v in fr_verdicts if v in ("BOUNCE", "ESCALATED"))
            gate_data[6]["bounces"] += bounce_cnt
            if fr_verdicts[0] == "PASS":
                gate_data[6]["passed_first_time"] += 1
            else:
                gate_data[6]["revisions"] += 1

        # Inspect subtasks
        subtasks = fields.get("subtasks", [])
        for st in subtasks:
            st_summary = st.get("fields", {}).get("summary", "")
            g_num = identify_gate_number(st_summary)
            if not g_num:
                continue

            comments = st.get("comments", []) or st.get("fields", {}).get("comment", {}).get("comments", [])
            verdicts = parse_verdicts_from_comments(comments)

            if not verdicts:
                # If subtask is marked Erledigt with no explicit revision comments, count as passed
                st_status = st.get("fields", {}).get("status", {}).get("name", "").lower()
                if st_status in ("erledigt", "done", "closed"):
                    gate_data[g_num]["total"] += 1
                    gate_data[g_num]["passed_first_time"] += 1
                continue

            gate_data[g_num]["total"] += 1
            if verdicts[0] == "PASS":
                gate_data[g_num]["passed_first_time"] += 1
            else:
                gate_data[g_num]["revisions"] += 1

    # 2. Compute FTPR and Swiss Cheese Layer Escape Rates (p1..p6)
    total_evaluated_subtasks = sum(gate_data[g]["total"] for g in range(1, 6))
    total_passed_first_time = sum(gate_data[g]["passed_first_time"] for g in range(1, 6))

    overall_ftpr = (
        round((total_passed_first_time / total_evaluated_subtasks) * 100.0, 1)
        if total_evaluated_subtasks > 0 else 100.0
    )

    gates_summary = {}
    p_layers = []
    for g in range(1, 6):
        tot = gate_data[g]["total"]
        pft = gate_data[g]["passed_first_time"]
        rev = gate_data[g]["revisions"]
        ftpr = round((pft / tot) * 100.0, 1) if tot > 0 else 100.0
        # Escape rate p_g is the defect escape probability from authoring into rework
        escape_rate = round(1.0 - (ftpr / 100.0), 4)
        p_layers.append(escape_rate)

        gates_summary[f"gate_{g}"] = {
            "name": STAGE_MAP[g]["name"],
            "total_subtasks": tot,
            "passed_first_time": pft,
            "revisions": rev,
            "ftpr_percent": ftpr,
            "escape_rate_p": escape_rate,
        }

    # Gate 6: Stage 6 ('Final Review (Agent)') Cold Carbon Review
    tot6 = gate_data[6]["total"]
    pft6 = gate_data[6]["passed_first_time"]
    rev6 = gate_data[6]["revisions"]
    bounces6 = gate_data[6]["bounces"]
    ftpr6 = round((pft6 / tot6) * 100.0, 1) if tot6 > 0 else 100.0
    escape_rate6 = round(1.0 - (ftpr6 / 100.0), 4)
    gates_summary["gate_6"] = {
        "name": STAGE_MAP[6]["name"],
        "total_subtasks": tot6,
        "total_tickets": tot6,
        "passed_first_time": pft6,
        "revisions": rev6,
        "bounces": bounces6,
        "ftpr_percent": ftpr6,
        "escape_rate_p": escape_rate6,
    }

    # Cumulative Swiss Cheese escape probability: product of (p1 * p2 * p3 * p4 * p5 [* p6])
    # If a layer has 0 revisions in the sample, substitute a nominal baseline (e.g. 0.02)
    cumulative_escape_prob = 1.0
    active_p_layers = list(p_layers) + ([escape_rate6] if tot6 > 0 else [])
    for p in active_p_layers:
        effective_p = p if p > 0 else 0.02  # nominal minimum barrier leakage
        cumulative_escape_prob *= effective_p
    cumulative_escape_prob = float(f"{cumulative_escape_prob:.2e}")

    # Human Acceptance Rate (parent tickets reaching completion without rework)
    human_acceptance_rate = (
        round((completed_tickets / total_tickets) * 100.0, 1)
        if total_tickets > 0 else 100.0
    )

    resolution_rate = (
        round((closed_bugs / total_bugs) * 100.0, 1)
        if total_bugs > 0 else 100.0
    )

    metrics = {
        "sprint_id": sprint_id,
        "sprint_name": sprint_name,
        "recorded_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "throughput": {
            "total_tickets": total_tickets,
            "completed_tickets": completed_tickets,
            "completion_rate_percent": round((completed_tickets / total_tickets) * 100.0, 1) if total_tickets > 0 else 0.0,
            "total_story_points": total_sp,
            "completed_story_points": completed_sp,
        },
        "risk_distribution": risk_counts,
        "problem_trends": {
            "total_bugs": total_bugs,
            "open_bugs": open_bugs,
            "closed_bugs": closed_bugs,
            "resolution_rate_percent": resolution_rate,
            "by_severity": by_severity,
            "open_by_severity": open_by_severity,
            "closed_by_severity": closed_by_severity,
        },
        "quality_gates": {
            "overall_ftpr_percent": overall_ftpr,
            "total_subtasks_evaluated": total_evaluated_subtasks,
            "gates": gates_summary,
            "swiss_cheese": {
                "p1_analysis_escape": p_layers[0],
                "p2_req_test_spec_escape": p_layers[1],
                "p3_impl_plan_escape": p_layers[2],
                "p4_implementation_escape": p_layers[3],
                "p5_test_verification_escape": p_layers[4],
                "p6_final_review_agent_escape": escape_rate6,
                "theoretical_cumulative_escape_prob": cumulative_escape_prob,
            },
            "human_acceptance_rate_percent": human_acceptance_rate,
        },
    }

    return metrics


def calculate_spc_limits(history, current_metrics=None):
    """
    PA 4.2 Statistical Process Control:
    Calculates rolling mean (mu) and standard deviation (sigma) across historical sprints.
    Computes upper and lower control limits (mu +/- 2sigma).
    If current_metrics is provided, checks for special cause variation outliers.
    """
    if not history:
        return {"has_history": False, "warnings": [], "limits": {}}

    keys_to_track = [
        ("overall_ftpr", lambda m: m.get("quality_gates", {}).get("overall_ftpr_percent", 100.0)),
        ("human_acceptance", lambda m: m.get("quality_gates", {}).get("human_acceptance_rate_percent", 100.0)),
        ("completed_tickets", lambda m: m.get("throughput", {}).get("completed_tickets", 0)),
        ("completed_sp", lambda m: m.get("throughput", {}).get("completed_story_points", 0.0)),
    ]

    spc_results = {}
    warnings = []

    for name, extractor in keys_to_track:
        values = [extractor(h) for h in history if extractor(h) is not None]
        n = len(values)
        if n < 1:
            continue

        mu = sum(values) / n
        if n >= 2:
            variance = sum((x - mu) ** 2 for x in values) / (n - 1)
            sigma = math.sqrt(variance)
        else:
            sigma = 0.0

        ucl = round(mu + 2 * sigma, 2)
        lcl = round(max(0.0, mu - 2 * sigma), 2)

        spc_results[name] = {
            "n": n,
            "mu": round(mu, 2),
            "sigma": round(sigma, 2),
            "ucl": ucl,
            "lcl": lcl,
        }

        if current_metrics:
            curr_val = extractor(current_metrics)
            if curr_val is not None:
                # For rates (FTPR, acceptance), dropping below LCL is an alert
                if "ftpr" in name or "acceptance" in name:
                    if curr_val < lcl:
                        warnings.append(
                            f"⚠️ PA 4.2 Special Cause Variation on {name.upper()}: "
                            f"{curr_val}% is below Lower Control Limit (LCL: {lcl}%, mu: {round(mu, 1)}%, sigma: {round(sigma, 1)}%). "
                            "Root-Cause Analysis (RCA) in Retro required."
                        )
                else:
                    if curr_val < lcl or (ucl > 0 and curr_val > ucl * 1.5):
                        warnings.append(
                            f"⚠️ PA 4.2 Variation on {name.upper()}: {curr_val} outside [{lcl}, {ucl}] "
                            f"(mu: {round(mu, 1)}, sigma: {round(sigma, 1)})."
                        )

    return {
        "has_history": True,
        "sample_size": len(history),
        "limits": spc_results,
        "warnings": warnings,
    }


def record_sprint_metrics(metrics_dict, filepath=DEFAULT_METRICS_PATH, repo_root=None):
    """
    Idempotently appends or updates the sprint metrics record in JSON Lines format.
    If a record with the same sprint_id or sprint_name exists, it is updated.
    """
    target = Path(repo_root) / filepath if repo_root else Path(filepath)
    target.parent.mkdir(parents=True, exist_ok=True)

    records = []
    if target.exists():
        with open(target, "r", encoding="utf-8") as f:
            for line in f:
                line_str = line.strip()
                if not line_str:
                    continue
                try:
                    records.append(json.loads(line_str))
                except json.JSONDecodeError:
                    continue

    s_id = metrics_dict.get("sprint_id")
    s_name = metrics_dict.get("sprint_name")

    updated = False
    for idx, r in enumerate(records):
        if (s_id and r.get("sprint_id") == s_id) or (s_name and r.get("sprint_name") == s_name):
            records[idx] = metrics_dict
            updated = True
            break

    if not updated:
        records.append(metrics_dict)

    with open(target, "w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r, sort_keys=True) + "\n")

    action = "Updated" if updated else "Recorded"
    print(f"{action} metrics for Sprint '{s_name}' (ID: {s_id}) in {target}.")
    return True


def load_historical_metrics(filepath=DEFAULT_METRICS_PATH, repo_root=None):
    """Loads all historical metrics from the JSONL file."""
    target = Path(repo_root) / filepath if repo_root else Path(filepath)
    if not target.exists():
        return []

    records = []
    with open(target, "r", encoding="utf-8") as f:
        for line in f:
            line_str = line.strip()
            if not line_str:
                continue
            try:
                records.append(json.loads(line_str))
            except json.JSONDecodeError:
                continue
    return records


def format_metrics_markdown(metrics, spc=None):
    """Formats quantitative metrics and SPC analysis into a clean markdown table."""
    s_name = metrics.get("sprint_name", "")
    tp = metrics.get("throughput", {})
    qg = metrics.get("quality_gates", {})
    gates = qg.get("gates", {})
    sc = qg.get("swiss_cheese", {})

    lines = [
        f"h2. Quantitative Sprint Process Metrics (PA 4.1 & PA 4.2) — Sprint {s_name}",
        "",
        "h3. 1. Throughput & Quality Gate First-Time-Pass-Rates (FTPR)",
        f"* *Ticket Throughput*: {tp.get('completed_tickets')}/{tp.get('total_tickets')} tickets ({tp.get('completion_rate_percent')}%) | *Velocity*: {tp.get('completed_story_points')} SP",
        f"* *Overall Gate FTPR*: *{qg.get('overall_ftpr_percent')}%* (evaluated across {qg.get('total_subtasks_evaluated')} sub-tasks)",
        f"* *Human Acceptance Rate*: *{qg.get('human_acceptance_rate_percent')}%*",
        "",
        "|| Gate / Stage || Sub-tasks || 1st-Time Pass || Revisions || FTPR % || Defect Escape Rate (p) ||",
    ]

    for g in range(1, 7):
        info = gates.get(f"gate_{g}")
        if not info and g == 6:
            continue
        info = info or {}
        lines.append(
            f"| {info.get('name', f'Gate {g}')} | {info.get('total_subtasks', 0)} | "
            f"{info.get('passed_first_time', 0)} | {info.get('revisions', 0)} | "
            f"*{info.get('ftpr_percent', 100.0)}%* | {info.get('escape_rate_p', 0.0)} |"
        )

    p6_str = f", p6={sc.get('p6_final_review_agent_escape')}" if "p6_final_review_agent_escape" in sc else ""
    lines.extend([
        "",
        "h3. 2. Empirical Swiss Cheese Multiplicative Defect Reduction (Reason Model)",
        f"* *Empirical Escape Rates*: p1={sc.get('p1_analysis_escape')}, p2={sc.get('p2_req_test_spec_escape')}, p3={sc.get('p3_impl_plan_escape')}, p4={sc.get('p4_implementation_escape')}, p5={sc.get('p5_test_verification_escape')}{p6_str}",
        f"* *Theoretical Cumulative Defect Escape Probability*: P(escaped defect) = {sc.get('theoretical_cumulative_escape_prob')}",
    ])

    pt = metrics.get("problem_trends", {})
    if pt:
        lines.extend([
            "",
            "h3. 3. Problem Trends & Defect Severity (ASPICE SUP.9)",
            f"* *Total Bugs*: {pt.get('total_bugs', 0)} (Open: {pt.get('open_bugs', 0)}, Closed: {pt.get('closed_bugs', 0)}) | *Resolution Rate*: *{pt.get('resolution_rate_percent', 100.0)}%*",
            "",
            "|| Severity || Total || Open || Closed ||",
        ])
        by_sev = pt.get("by_severity", {})
        open_by_sev = pt.get("open_by_severity", {})
        closed_by_sev = pt.get("closed_by_severity", {})
        for sev in ("Critical", "Major", "Minor", "Cosmetic"):
            tot = by_sev.get(sev, 0)
            op = open_by_sev.get(sev, 0)
            cl = closed_by_sev.get(sev, 0)
            lines.append(f"| {sev} | {tot} | {op} | {cl} |")

    if spc and spc.get("has_history"):
        spc_num = "4" if pt else "3"
        lines.extend([
            "",
            f"h3. {spc_num}. Statistical Process Control (SPC — PA 4.2, Sample Size: {spc.get('sample_size')} Sprints)",
        ])
        for k, v in spc.get("limits", {}).items():
            lines.append(f"* *{k.upper()}*: mu={v['mu']}, sigma={v['sigma']} -> Control Band [LCL: {v['lcl']}, UCL: {v['ucl']}]")

        if spc.get("warnings"):
            lines.append("")
            lines.append("*Special Cause Variations Detected*:")
            for w in spc.get("warnings"):
                lines.append(f"* {w}")
        else:
            lines.append("*Process Stability*: All metrics within +/- 2sigma control limits (In Statistical Control).")

    return "\n".join(lines)
