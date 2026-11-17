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
SEVERITY_LEVELS = ("Critical", "Major", "Minor", "Cosmetic")
NON_FIX_RESOLUTION_LABELS = {
    "resolution-rejected": "Rejected",
    "resolution-duplicate": "Duplicate",
    "resolution-cannot-reproduce": "Cannot Reproduce",
}


def _parse_iso_datetime(ts_str):
    """Parses Jira ISO-8601 timestamps (e.g. 2026-11-10T14:22:00.000+0100 or Z) into UTC datetime."""
    if not ts_str or not isinstance(ts_str, str):
        return None
    s = ts_str.strip()
    if not s:
        return None
    if s.endswith("Z"):
        s = s[:-1] + "+00:00"
    m_tz = re.search(r"([+-]\d{2})(\d{2})$", s)
    if m_tz:
        s = s[:m_tz.start()] + f"{m_tz.group(1)}:{m_tz.group(2)}"
    try:
        dt = datetime.datetime.fromisoformat(s)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=datetime.timezone.utc)
        return dt.astimezone(datetime.timezone.utc)
    except ValueError:
        return None


def _summarize_bug_ages(ages_by_sev):
    """Computes open bug age summary (avg, max, >7d) overall and per severity."""
    all_ages = []
    per_sev = {}
    for sev in SEVERITY_LEVELS:
        vals = ages_by_sev.get(sev, [])
        all_ages.extend(vals)
        per_sev[sev] = {
            "count_with_created": len(vals),
            "avg": round(sum(vals) / len(vals), 1) if vals else 0.0,
            "max": round(max(vals), 1) if vals else 0.0,
            "gt_7d": sum(1 for v in vals if v > 7.0),
        }
    return {
        "count_with_created": len(all_ages),
        "avg": round(sum(all_ages) / len(all_ages), 1) if all_ages else 0.0,
        "max": round(max(all_ages), 1) if all_ages else 0.0,
        "gt_7d": sum(1 for v in all_ages if v > 7.0),
        "by_severity": per_sev,
    }


def _extract_bug_trend_point(record):
    """Extracts a compact per-sprint bug trend point from a process_metrics record."""
    pt = record.get("problem_trends")
    if not isinstance(pt, dict):
        return None
    by_sev = pt.get("by_severity") or {}
    age_obj = pt.get("open_bug_age_days") or {}
    return {
        "sprint_id": record.get("sprint_id"),
        "sprint_name": str(record.get("sprint_name") or record.get("sprint_id") or "Unknown"),
        "total_bugs": int(pt.get("total_bugs", 0) or 0),
        "open_bugs": int(pt.get("open_bugs", 0) or 0),
        "closed_bugs": int(pt.get("closed_bugs", 0) or 0),
        "fixed_awaiting_human": int(pt.get("fixed_awaiting_human", 0) or 0),
        "critical": int(by_sev.get("Critical", 0) or 0),
        "major": int(by_sev.get("Major", 0) or 0),
        "minor": int(by_sev.get("Minor", 0) or 0),
        "cosmetic": int(by_sev.get("Cosmetic", 0) or 0),
        "resolution_rate_percent": float(pt.get("resolution_rate_percent", 100.0) or 0.0),
        "avg_open_age_days": float(age_obj.get("avg", 0.0) or 0.0) if isinstance(age_obj, dict) else 0.0,
    }


def _build_bug_trend_history(current_record, history=None, max_entries=6):
    """Builds chronological multi-sprint bug trend history ending with current_record."""
    points = []
    seen = set()
    cur_sid = current_record.get("sprint_id")
    cur_sname = str(current_record.get("sprint_name") or "")
    for rec in (history or []):
        pt = _extract_bug_trend_point(rec)
        if not pt:
            continue
        if (cur_sid and pt.get("sprint_id") == cur_sid) or (cur_sname and pt.get("sprint_name") == cur_sname):
            continue
        key = (pt.get("sprint_id"), pt.get("sprint_name"))
        if key in seen:
            continue
        seen.add(key)
        points.append(pt)
    if max_entries > 1 and len(points) > (max_entries - 1):
        points = points[-(max_entries - 1):]
    cur_pt = _extract_bug_trend_point(current_record)
    if cur_pt:
        points.append(cur_pt)
    return points


def _render_ascii_bar(val, max_val, width=10):
    if max_val <= 0 or val <= 0:
        return "." * width
    filled = max(1, min(width, int(round((val / max_val) * width))))
    return ("#" * filled) + ("." * (width - filled))


def identify_gate_number(summary):
    """Determines Gate number (1..5) from subtask summary prefix."""
    s_lower = summary.lower()
    for g in range(1, 6):
        data = STAGE_MAP[g]
        if data["prefix"] in s_lower:
            return g
    return None


def extract_text_from_comment_body(body):
    """
    REQ-PRO-092.2:
    Recursively extracts plain text from Atlassian Document Format (ADF) dicts
    or returns string bodies unmodified.
    """
    if isinstance(body, str):
        return body
    if isinstance(body, dict):
        texts = []

        def _recurse(node):
            if isinstance(node, dict):
                if node.get("type") == "text" and "text" in node:
                    texts.append(node["text"])
                for v in node.values():
                    _recurse(v)
            elif isinstance(node, list):
                for item in node:
                    _recurse(item)

        _recurse(body)
        return " ".join(texts)
    return str(body) if body is not None else ""


def parse_final_review_agent_verdicts_from_comments(comments):
    """
    REQ-PRO-058.4 / TST-PRO-058.4:
    Parses Stage 6 ('Final Review (Agent)') verdicts from parent issue comments.
    Returns list of verdict strings: ['PASS'], ['BOUNCE', 'PASS'], ['BOUNCE', 'BOUNCE', 'BOUNCE', 'ESCALATED'], etc.
    """
    verdicts = []
    for c in (comments or []):
        body = extract_text_from_comment_body(c.get("body", ""))
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
        body = extract_text_from_comment_body(c.get("body", ""))
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


def parse_sprint_gate_metrics(parent_issues, sprint_info, history=None, now=None):
    """
    Calculates PA 4.1 Quantitative Process Metrics from a list of parent issues and their subtasks.
    Each parent issue dict must contain 'fields' with 'summary', 'status', 'priority', 'subtasks',
    and optional customfield story points.
    """
    sprint_id = sprint_info.get("id", 0)
    sprint_name = sprint_info.get("name", "Unknown Sprint")
    if now is None:
        ref_now = datetime.datetime.now(datetime.timezone.utc)
    elif now.tzinfo is None:
        ref_now = now.replace(tzinfo=datetime.timezone.utc)
    else:
        ref_now = now.astimezone(datetime.timezone.utc)

    # 1. Throughput & Story Points
    total_tickets = len(parent_issues)
    completed_tickets = 0
    total_sp = 0.0
    completed_sp = 0.0
    risk_counts = {"Low": 0, "Medium": 0, "High": 0, "Highest": 0}

    # Problem trends containers (REQ-PRO-063 / REQ-PRO-121.1)
    total_bugs = 0
    open_bugs = 0
    closed_bugs = 0
    fixed_awaiting_human = 0
    by_severity = {s: 0 for s in SEVERITY_LEVELS}
    open_by_severity = {s: 0 for s in SEVERITY_LEVELS}
    closed_by_severity = {s: 0 for s in SEVERITY_LEVELS}
    awaiting_human_by_severity = {s: 0 for s in SEVERITY_LEVELS}
    open_ages_by_sev = {s: [] for s in SEVERITY_LEVELS}
    by_resolution = {"Fixed": 0, "Rejected": 0, "Duplicate": 0, "Cannot Reproduce": 0, "Fertig": 0, "Unresolved": 0}

    # Gate statistics containers
    gate_data = {
        g: {"total": 0, "passed_first_time": 0, "revisions": 0, "bounces": 0, "unverified": 0}
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

        # Problem resolution tracking (REQ-PRO-063 / REQ-PRO-121.1)
        itype = fields.get("issuetype", {})
        itype_id = str(itype.get("id", ""))
        itype_name = itype.get("name", "").lower()
        is_bug = itype_id == "10006" or itype_name in ("bug", "fehler")
        if is_bug:
            total_bugs += 1
            labels = fields.get("labels", []) or []
            norm_labels = [str(l).strip().lower() for l in labels]

            res_field = fields.get("resolution")
            jira_res_name = ""
            if isinstance(res_field, dict):
                jira_res_name = str(res_field.get("name", "") or "").strip()
            elif isinstance(res_field, str):
                jira_res_name = res_field.strip()

            label_res_name = None
            for nl in norm_labels:
                if nl in NON_FIX_RESOLUTION_LABELS:
                    label_res_name = NON_FIX_RESOLUTION_LABELS[nl]
                    break
                if nl == "resolution-fixed":
                    label_res_name = "Fixed"
                    break

            is_terminal_status = status_name in ("erledigt", "closed", "done")
            is_awaiting_human_status = status_name in ("final review (human)", "final review")
            is_bug_closed = is_terminal_status or (label_res_name in ("Rejected", "Duplicate", "Cannot Reproduce")) or (
                bool(jira_res_name) and not is_awaiting_human_status and status_name not in ("zu erledigen", "analysis", "test spec", "implementation plan", "implementation", "test", "in bearbeitung")
            )
            is_bug_awaiting_human = (not is_bug_closed) and is_awaiting_human_status

            if is_bug_closed:
                closed_bugs += 1
            elif is_bug_awaiting_human:
                fixed_awaiting_human += 1
            else:
                open_bugs += 1

            effective_res = label_res_name or jira_res_name or ("Fixed" if is_bug_awaiting_human else ("Fertig" if is_terminal_status else "Unresolved"))
            if effective_res in by_resolution:
                by_resolution[effective_res] += 1
            else:
                by_resolution["Fixed" if (is_bug_closed or is_bug_awaiting_human) else "Unresolved"] += 1

            detected_sev = None
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
                raw_desc = fields.get("description", "")
                desc = extract_text_from_comment_body(raw_desc) if raw_desc else ""
                m_sev = re.search(r"(?:\*\*Severity\*\*|Severity)\s*:?\s*([A-Za-z]+)", desc, re.I)
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
            elif is_bug_awaiting_human:
                awaiting_human_by_severity[detected_sev] += 1
            else:
                open_by_severity[detected_sev] += 1
                created_dt = _parse_iso_datetime(fields.get("created"))
                if created_dt is not None:
                    age_days = max(0.0, (ref_now - created_dt).total_seconds() / 86400.0)
                    open_ages_by_sev[detected_sev].append(age_days)

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
                # If subtask is marked Erledigt with no explicit revision comments:
                # REQ-PRO-092.3: do NOT count as passed_first_time. Track as unverified!
                st_status = st.get("fields", {}).get("status", {}).get("name", "").lower()
                if st_status in ("erledigt", "done", "closed"):
                    gate_data[g_num]["unverified"] += 1
                continue

            gate_data[g_num]["total"] += 1
            if verdicts[0] == "PASS":
                gate_data[g_num]["passed_first_time"] += 1
            else:
                gate_data[g_num]["revisions"] += 1

    # 2. Compute FTPR and Swiss Cheese Layer Escape Rates (p1..p6)
    total_evaluated_subtasks = sum(gate_data[g]["total"] for g in range(1, 6))
    total_unverified_subtasks = sum(gate_data[g]["unverified"] for g in range(1, 6))
    total_passed_first_time = sum(gate_data[g]["passed_first_time"] for g in range(1, 6))

    overall_ftpr = (
        round((total_passed_first_time / total_evaluated_subtasks) * 100.0, 1)
        if total_evaluated_subtasks > 0 else 100.0
    )

    # Gate 6: Stage 6 ('Final Review (Agent)') Cold Carbon Review
    tot6 = gate_data[6]["total"]
    pft6 = gate_data[6]["passed_first_time"]
    rev6 = gate_data[6]["revisions"]
    bounces6 = gate_data[6]["bounces"]
    ftpr6 = round((pft6 / tot6) * 100.0, 1) if tot6 > 0 else 100.0
    escape_rate6 = round(1.0 - (ftpr6 / 100.0), 4)

    # Downstream defect escape attribution across upstream gates (REQ-PRO-092.4)
    # Stage 6 bounces represent defects that escaped upstream verification barriers (Gates 3, 4, 5).
    # Attribute downstream escapes to Gates 3, 4, 5 to ensure p_g reflects downstream defect leakage.
    downstream_escape_ratio = 0.0
    if bounces6 > 0:
        base_pop = max(1.0, float(tot6 if tot6 > 0 else total_evaluated_subtasks))
        downstream_escape_ratio = min(1.0, round(float(bounces6) / (base_pop * 3.0), 4))
        if downstream_escape_ratio <= 0.0:
            downstream_escape_ratio = 0.05

    gates_summary = {}
    p_layers = []
    for g in range(1, 6):
        tot = gate_data[g]["total"]
        pft = gate_data[g]["passed_first_time"]
        rev = gate_data[g]["revisions"]
        unver = gate_data[g]["unverified"]
        ftpr = round((pft / tot) * 100.0, 1) if tot > 0 else 100.0
        # Escape rate p_g is the defect escape probability from authoring into rework
        escape_rate = round(1.0 - (ftpr / 100.0), 4)

        # REQ-PRO-092.4: If Stage 6 bounces occurred, attribute downstream escape to Gates 3, 4, 5
        if g in (3, 4, 5) and bounces6 > 0:
            escape_rate = max(escape_rate, downstream_escape_ratio)

        p_layers.append(escape_rate)

        gates_summary[f"gate_{g}"] = {
            "name": STAGE_MAP[g]["name"],
            "total_subtasks": tot,
            "passed_first_time": pft,
            "revisions": rev,
            "unverified": unver,
            "ftpr_percent": ftpr,
            "escape_rate_p": escape_rate,
        }

    gates_summary["gate_6"] = {
        "name": STAGE_MAP[6]["name"],
        "total_subtasks": tot6,
        "total_tickets": tot6,
        "passed_first_time": pft6,
        "revisions": rev6,
        "bounces": bounces6,
        "unverified": gate_data[6]["unverified"],
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

    resolved_bug_count = closed_bugs + fixed_awaiting_human
    resolution_rate = (
        round((resolved_bug_count / total_bugs) * 100.0, 1)
        if total_bugs > 0 else 100.0
    )
    open_bug_age_days = _summarize_bug_ages(open_ages_by_sev)

    # REQ-PRO-092.5: Metric Plausibility Warning Thresholds
    plausibility_warnings = []
    total_revisions_all = sum(gate_data[g]["revisions"] for g in range(1, 6)) + rev6 + bounces6
    if total_evaluated_subtasks >= 20 and overall_ftpr >= 100.0 and total_revisions_all == 0:
        plausibility_warnings.append(
            f"PA 4.1 Plausibility Warning: Gate first-time pass rate is 100.0% across {total_evaluated_subtasks} subtasks without revisions, indicating potential gate discrimination blind spots."
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
            "fixed_awaiting_human": fixed_awaiting_human,
            "resolution_rate_percent": resolution_rate,
            "by_severity": by_severity,
            "open_by_severity": open_by_severity,
            "closed_by_severity": closed_by_severity,
            "awaiting_human_by_severity": awaiting_human_by_severity,
            "by_resolution": by_resolution,
            "open_bug_age_days": open_bug_age_days,
        },
        "quality_gates": {
            "overall_ftpr_percent": overall_ftpr,
            "total_subtasks_evaluated": total_evaluated_subtasks,
            "total_unverified_subtasks": total_unverified_subtasks,
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
        "plausibility_warnings": plausibility_warnings,
    }
    if history is not None:
        metrics["problem_trends"]["bug_trend_history"] = _build_bug_trend_history(metrics, history=history)

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

    # ASPICE 4.0 Finding M3 / REQ-PRO-107.3: Multi-Sprint 100% FTPR Rubber-Stamping Alert
    if current_metrics and history:
        curr_ftpr = current_metrics.get("quality_gates", {}).get("overall_ftpr_percent", 0.0)
        curr_subtasks = current_metrics.get("quality_gates", {}).get("total_subtasks_evaluated", 0)
        if curr_ftpr >= 100.0 and curr_subtasks > 0:
            consecutive_100_count = 1
            for hist in reversed(history):
                h_ftpr = hist.get("quality_gates", {}).get("overall_ftpr_percent", 0.0)
                h_subtasks = hist.get("quality_gates", {}).get("total_subtasks_evaluated", 0)
                if h_ftpr >= 100.0 and h_subtasks > 0:
                    consecutive_100_count += 1
                else:
                    break
            if consecutive_100_count >= 2:
                warnings.append(
                    f"⚠️ ASPICE Finding M3 Rubber-Stamping Alert: Consecutive sprints ({consecutive_100_count} >= 2) exhibit 100.0% Gate 1-5 First-Time Pass Rate with zero revisions. Gate discrimination failure detected (GP 2.1.4, GP 2.2.4). Mandatory review calibration required."
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


def format_metrics_markdown(metrics, spc=None, history=None):
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
        awaiting_cnt = pt.get("fixed_awaiting_human", 0)
        age_info = pt.get("open_bug_age_days") or {}
        age_by_sev = age_info.get("by_severity") or {}
        lines.extend([
            "",
            "h3. 3. Problem Trends & Defect Severity (ASPICE SUP.9)",
            f"* *Total Bugs*: {pt.get('total_bugs', 0)} (Open: {pt.get('open_bugs', 0)}, Awaiting Human: {awaiting_cnt}, Closed: {pt.get('closed_bugs', 0)}) | *Resolution Rate*: *{pt.get('resolution_rate_percent', 100.0)}%*",
            f"* *Open Bug Age (days)*: avg={age_info.get('avg', 0.0)}d, max={age_info.get('max', 0.0)}d, >7d={age_info.get('gt_7d', 0)}",
            "",
            "|| Severity || Total || Open || Awaiting Human || Closed || Avg Age (d) || Max Age (d) || >7d ||",
        ])
        by_sev = pt.get("by_severity", {})
        open_by_sev = pt.get("open_by_severity", {})
        awaiting_by_sev = pt.get("awaiting_human_by_severity", {})
        closed_by_sev = pt.get("closed_by_severity", {})
        for sev in SEVERITY_LEVELS:
            tot = by_sev.get(sev, 0)
            op = open_by_sev.get(sev, 0)
            aw = awaiting_by_sev.get(sev, 0)
            cl = closed_by_sev.get(sev, 0)
            s_age = age_by_sev.get(sev) or {}
            lines.append(
                f"| {sev} | {tot} | {op} | {aw} | {cl} | "
                f"{s_age.get('avg', 0.0)} | {s_age.get('max', 0.0)} | {s_age.get('gt_7d', 0)} |"
            )

        trend_points = pt.get("bug_trend_history")
        if not trend_points and history is not None:
            trend_points = _build_bug_trend_history(metrics, history=history)
        if trend_points:
            max_open = max((p.get("open_bugs", 0) for p in trend_points), default=0)
            lines.extend([
                "",
                "h4. Bug Trend vs Previous Sprints",
                "|| Sprint || Total || Open || Resolved || Crit/Maj/Min/Cos || Avg Age (d) || Res % || Open Trend ||",
            ])
            for p in trend_points:
                resolved_p = p.get("closed_bugs", 0) + p.get("fixed_awaiting_human", 0)
                sev_vec = f"{p.get('critical', 0)}/{p.get('major', 0)}/{p.get('minor', 0)}/{p.get('cosmetic', 0)}"
                bar = _render_ascii_bar(p.get("open_bugs", 0), max_open)
                lines.append(
                    f"| {p.get('sprint_name')} | {p.get('total_bugs', 0)} | {p.get('open_bugs', 0)} | "
                    f"{resolved_p} | {sev_vec} | {p.get('avg_open_age_days', 0.0)} | "
                    f"{p.get('resolution_rate_percent', 100.0)}% | `{bar}` |"
                )

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

    plausibility_warnings = metrics.get("plausibility_warnings", [])
    if plausibility_warnings:
        lines.append("")
        for w in plausibility_warnings:
            lines.append(f"*⚠️ {w}*")

    return "\n".join(lines)
