#!/usr/bin/env python3
"""
Weekly Method Scorecard for Lean AAA v2 (ATT-9539, Parent Epic ATT-9537).

Computes ISO-week product-vs-process effort split, defects per practice,
risk-tier ticket/SP breakdown, cost per ticket, and measured human PO minutes,
compares every metric against docs/engineering/metrics/baseline_2026-KW47.json,
persists idempotently to docs/engineering/metrics/method_scorecard.jsonl,
and optionally posts a compact scorecard to the rolling Wochen Review & Retro ticket.
"""

import argparse
import datetime as dt
import json
import os
import re
import subprocess
import sys
from collections import Counter

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DEFAULT_BASELINE_PATH = os.path.join(REPO_ROOT, "docs", "engineering", "metrics", "baseline_2026-KW47.json")
DEFAULT_PROCESS_METRICS_PATH = os.path.join(REPO_ROOT, "docs", "engineering", "metrics", "process_metrics.jsonl")
DEFAULT_SCORECARD_JSONL = os.path.join(REPO_ROOT, "docs", "engineering", "metrics", "method_scorecard.jsonl")
DEFAULT_PO_SESSIONS_JSONL = os.path.join(REPO_ROOT, "docs", "engineering", "reviews", "po_sessions.jsonl")

ISO_WEEK_RE = re.compile(r"^(\d{4})-(?:KW|W)(\d{2})$", re.IGNORECASE)
SPRINT_DATE_RE = re.compile(r"^(\d{4}-\d{2}-\d{2})")

PATH_CLASSES = (
    "production_code",
    "tests",
    "per_ticket_docs",
    "living_specs",
    "process_tooling_and_other_docs",
)


def parse_iso_week(week_str):
    """Parse 'YYYY-KWnn' or 'YYYY-Wnn' into (iso_week_label, start_date, end_date)."""
    m = ISO_WEEK_RE.match(week_str.strip())
    if not m:
        raise ValueError(f"Invalid ISO week '{week_str}'. Expected format YYYY-KWnn (e.g. 2026-KW47).")
    year, week = int(m.group(1)), int(m.group(2))
    start_date = dt.date.fromisocalendar(year, week, 1)
    end_date = dt.date.fromisocalendar(year, week, 7)
    return f"{year}-KW{week:02d}", start_date, end_date


def current_iso_week(today=None):
    today = today or dt.date.today()
    iso = today.isocalendar()
    return f"{iso.year}-KW{iso.week:02d}"


def classify_path(path):
    """Classify a repo-relative path into one of the 5 Lean AAA v2 protocol path classes."""
    p = path.lstrip("./")
    if p.startswith("app/src/main/"):
        return "production_code"
    if p.startswith("app/src/test/") or p.startswith("app/src/androidTest/"):
        return "tests"
    if p.startswith((
        "docs/engineering/analysis/",
        "docs/engineering/test_specs/",
        "docs/engineering/plans/",
        "docs/engineering/walkthroughs/",
        "docs/engineering/tickets/",
    )):
        return "per_ticket_docs"
    if (
        p.startswith(("docs/requirements/", "docs/tests/", "docs/system/", "docs/architecture"))
        or p in ("docs/requirements.md", "docs/tests.md")
    ):
        return "living_specs"
    return "process_tooling_and_other_docs"


def parse_git_numstat_output(output):
    """Parse `git log --no-merges --numstat --format=COMMIT:%H` output into effort & commit stats."""
    lines_added = Counter({cls: 0 for cls in PATH_CLASSES})
    lines_deleted = Counter({cls: 0 for cls in PATH_CLASSES})
    total_commits = 0
    commits_touching_prod = 0
    cur_has_prod = False
    in_commit = False

    for line in output.splitlines():
        if line.startswith("COMMIT:"):
            if in_commit:
                total_commits += 1
                if cur_has_prod:
                    commits_touching_prod += 1
            in_commit = True
            cur_has_prod = False
        elif "\t" in line:
            parts = line.split("\t", 2)
            if len(parts) == 3:
                added_s, deleted_s, rel_path = parts
                if added_s.isdigit() and deleted_s.isdigit():
                    cls = classify_path(rel_path)
                    lines_added[cls] += int(added_s)
                    lines_deleted[cls] += int(deleted_s)
                    if cls == "production_code":
                        cur_has_prod = True
    if in_commit:
        total_commits += 1
        if cur_has_prod:
            commits_touching_prod += 1

    total_added = sum(lines_added.values())
    classes = {}
    for cls in PATH_CLASSES:
        added = lines_added[cls]
        pct = round(100.0 * added / total_added, 1) if total_added > 0 else 0.0
        classes[cls] = {"lines_added": added, "lines_deleted": lines_deleted[cls], "share_percent": pct}

    no_prod = total_commits - commits_touching_prod
    prod_commit_pct = round(100.0 * commits_touching_prod / total_commits, 1) if total_commits > 0 else 0.0
    return {
        "effort_split": {"total_lines_added": total_added, "classes": classes},
        "commits": {
            "total_non_merge_commits": total_commits,
            "commits_touching_production_code": commits_touching_prod,
            "commits_touching_production_code_percent": prod_commit_pct,
            "commits_without_production_code": no_prod,
            "commits_without_production_code_percent": round(100.0 - prod_commit_pct, 1) if total_commits > 0 else 0.0,
        },
    }


def collect_git_metrics(repo_root, ref, start_date, end_date):
    since_arg = f"{start_date.isoformat()}T00:00:00+01:00"
    until_arg = f"{end_date.isoformat()}T23:59:59+01:00"
    cmd = [
        "git", "-C", repo_root, "log", ref,
        f"--since={since_arg}", f"--until={until_arg}",
        "--no-merges", "--numstat", "--format=COMMIT:%H",
    ]
    out = subprocess.check_output(cmd, text=True, errors="replace")
    return parse_git_numstat_output(out)


def aggregate_process_metrics(jsonl_path, start_date, end_date, process_version=None):
    """Aggregate Schicht records from process_metrics.jsonl falling inside [start_date, end_date]."""
    records = []
    if os.path.isfile(jsonl_path):
        with open(jsonl_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                rec = json.loads(line)
                if process_version and rec.get("process_version") != process_version:
                    continue
                m = SPRINT_DATE_RE.match(str(rec.get("sprint_name", "")))
                if not m:
                    continue
                rec_date = dt.date.fromisoformat(m.group(1))
                if start_date <= rec_date <= end_date:
                    records.append(rec)

    g15_total = 0
    g15_rev = 0
    per_gate = {}
    g6_eval = 0
    g6_bounced_tickets = 0
    g6_bounces = 0
    completed_tickets = 0
    completed_sp = 0.0
    wall_hours = 0.0

    for rec in records:
        sname = str(rec.get("sprint_name", ""))
        if "01_Früh" in sname or "01_Frueh" in sname:
            wall_hours += 10.0
        elif "02_Spät" in sname or "02_Spaet" in sname or "03_Nacht" in sname:
            wall_hours += 7.0

        tp = rec.get("throughput") or {}
        completed_tickets += int(tp.get("completed_tickets", 0) or 0)
        completed_sp += float(tp.get("completed_story_points", 0.0) or 0.0)

        gates = (rec.get("quality_gates") or {}).get("gates") or {}
        for gk in ("gate_1", "gate_2", "gate_3", "gate_4", "gate_5"):
            g = gates.get(gk) or {}
            tot = int(g.get("total_subtasks", 0) or 0)
            rev = int(g.get("revisions", 0) or 0)
            g15_total += tot
            g15_rev += rev
            entry = per_gate.setdefault(gk, {"total_subtasks": 0, "revisions": 0})
            entry["total_subtasks"] += tot
            entry["revisions"] += rev

        g6 = gates.get("gate_6") or {}
        g6_eval += int(g6.get("total_tickets", g6.get("total_subtasks", 0)) or 0)
        g6_bounced_tickets += int(g6.get("revisions", 0) or 0)
        g6_bounces += int(g6.get("bounces", 0) or 0)

    return {
        "schichten_completed": len(records),
        "gates_1_to_5": {
            "total_subtasks_evaluated": g15_total,
            "revisions": g15_rev,
            "revision_rate_percent": round(100.0 * g15_rev / g15_total, 2) if g15_total > 0 else 0.0,
            "per_gate": per_gate,
        },
        "stage_6_cold_review": {
            "tickets_evaluated": g6_eval,
            "tickets_bounced": g6_bounced_tickets,
            "ticket_bounce_rate_percent": round(100.0 * g6_bounced_tickets / g6_eval, 1) if g6_eval > 0 else 0.0,
            "total_bounces": g6_bounces,
        },
        "throughput": {
            "completed_tickets": completed_tickets,
            "completed_story_points": round(completed_sp, 1),
            "mean_sp_per_ticket": round(completed_sp / completed_tickets, 2) if completed_tickets > 0 else 0.0,
            "estimated_schicht_wall_hours_total": round(wall_hours, 1),
            "mean_wall_minutes_per_ticket": round(60.0 * wall_hours / completed_tickets, 1) if completed_tickets > 0 else 0.0,
        },
    }


def read_po_minutes(po_sessions_path, iso_week):
    """Read measured PO session minutes for iso_week from po_sessions.jsonl; returns None if unrecorded."""
    if not os.path.isfile(po_sessions_path):
        return None
    total = 0.0
    found = False
    with open(po_sessions_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            rec = json.loads(line)
            if rec.get("iso_week") == iso_week and rec.get("minutes") is not None:
                total += float(rec["minutes"])
                found = True
    return round(total, 1) if found else None


def classify_po_acceptance_buckets(accepted_issues, followup_issue_types_by_key=None):
    """
    Delegate to sprint_metrics.classify_po_acceptance_buckets so weekly scorecard and per-Schicht
    metrics use identical 3-way follow-up bucketing (clean / refinement / bug_escape) AND
    persisted '[PO Review Comment]' coverage tracking.
    """
    tools_dir = os.path.dirname(os.path.abspath(__file__))
    if tools_dir not in sys.path:
        sys.path.insert(0, tools_dir)
    import sprint_metrics

    return sprint_metrics.classify_po_acceptance_buckets(
        accepted_issues,
        followup_issue_types_by_key=followup_issue_types_by_key,
    )


def collect_jira_metrics(start_date, end_date, role="coordinator"):
    """Query Jira for process-escape bugs, field bugs, tier breakdown, and 3-way PO acceptance split."""
    tools_dir = os.path.dirname(os.path.abspath(__file__))
    if tools_dir not in sys.path:
        sys.path.insert(0, tools_dir)
    import jira_util

    config = jira_util.get_config()
    end_plus_one = (end_date + dt.timedelta(days=1)).isoformat()
    start_s = start_date.isoformat()

    escape_jql = f"project = ATT AND labels = process-escape AND created >= '{start_s}' AND created < '{end_plus_one}'"
    escapes = jira_util.paged_jql_search(escape_jql, ["summary", "status", "labels"], role=role, config=config)

    field_jql = f"project = ATT AND issuetype = Bug AND labels = field-escape AND created >= '{start_s}' AND created < '{end_plus_one}'"
    field_bugs = jira_util.paged_jql_search(field_jql, ["summary", "status", "labels"], role=role, config=config)

    tier_jql = (
        f"project = ATT AND issuetype != Sub-task AND status in ('Final Review (Human)', 'Erledigt') "
        f"AND updated >= '{start_s}' AND updated < '{end_plus_one}'"
    )
    tier_issues = jira_util.paged_jql_search(
        tier_jql, ["labels", "status", "issuelinks", "comment", jira_util.STORY_POINTS_FIELD], role=role, config=config
    )
    tiers = {t: {"tickets": 0, "story_points": 0.0} for t in ("tier-a", "tier-b", "tier-c", "unclassified")}
    accepted_issues = []
    for issue in tier_issues:
        fields = issue.get("fields") or {}
        labels = {str(l).strip().lower() for l in (fields.get("labels") or [])}
        tier_key = next((t for t in ("tier-a", "tier-b", "tier-c") if t in labels), "unclassified")
        sp_raw = fields.get(jira_util.STORY_POINTS_FIELD)
        sp_val = float(sp_raw) if sp_raw is not None else 0.0
        tiers[tier_key]["tickets"] += 1
        tiers[tier_key]["story_points"] = round(tiers[tier_key]["story_points"] + sp_val, 1)
        if ((fields.get("status") or {}).get("name") or "").strip().lower() == "erledigt":
            accepted_issues.append(issue)

    followup_jql = "project = ATT AND labels = po-review-followup"
    followups = jira_util.paged_jql_search(followup_jql, ["issuetype"], role=role, config=config)
    followup_types = {
        i["key"]: ((i.get("fields") or {}).get("issuetype") or {}).get("name", "")
        for i in followups
    }
    po_acceptance_split = classify_po_acceptance_buckets(accepted_issues, followup_types)

    return {
        "process_escape_bugs_count": len(escapes),
        "process_escape_bug_keys": [i["key"] for i in escapes],
        "field_escape_bugs_count": len(field_bugs),
        "field_escape_bug_keys": [i["key"] for i in field_bugs],
        "tiers": tiers,
        "po_acceptance_split": po_acceptance_split,
    }


def _delta(current, baseline):
    if current is None or baseline is None:
        return None
    return round(float(current) - float(baseline), 2)


def compute_scorecard(
    iso_week,
    repo_root=REPO_ROOT,
    ref="develop",
    process_version="aaa-v2",
    baseline_path=DEFAULT_BASELINE_PATH,
    process_metrics_path=DEFAULT_PROCESS_METRICS_PATH,
    po_sessions_path=DEFAULT_PO_SESSIONS_JSONL,
    po_minutes_override=None,
    query_jira=True,
    role="coordinator",
    filter_process_version=False,
):
    week_label, start_date, end_date = parse_iso_week(iso_week)
    git_stats = collect_git_metrics(repo_root, ref, start_date, end_date)
    pm_stats = aggregate_process_metrics(
        process_metrics_path,
        start_date,
        end_date,
        process_version=process_version if filter_process_version else None,
    )
    po_minutes = po_minutes_override if po_minutes_override is not None else read_po_minutes(po_sessions_path, week_label)

    if query_jira:
        jira_stats = collect_jira_metrics(start_date, end_date, role=role)
    else:
        jira_stats = {
            "process_escape_bugs_count": None,
            "process_escape_bug_keys": [],
            "field_escape_bugs_count": None,
            "field_escape_bug_keys": [],
            "tiers": {t: {"tickets": 0, "story_points": 0.0} for t in ("tier-a", "tier-b", "tier-c", "unclassified")},
        }

    baseline = {}
    if baseline_path and os.path.isfile(baseline_path):
        with open(baseline_path, "r", encoding="utf-8") as f:
            baseline = json.load(f)

    b_classes = (baseline.get("effort_split_by_path_class") or {}).get("classes") or {}
    c_classes = git_stats["effort_split"]["classes"]
    b_commits = baseline.get("commits") or {}
    c_commits = git_stats["commits"]
    b_gates = baseline.get("gate_and_review_effectiveness") or {}
    b_g15 = b_gates.get("gates_1_to_5") or {}
    b_g6 = b_gates.get("stage_6_cold_review") or {}
    b_esc = baseline.get("escapes") or {}
    b_tp = baseline.get("throughput_and_cost") or {}

    comparisons = {
        "production_code_share_percent": {
            "baseline": (b_classes.get("production_code") or {}).get("share_percent"),
            "current": c_classes["production_code"]["share_percent"],
        },
        "tests_share_percent": {
            "baseline": (b_classes.get("tests") or {}).get("share_percent"),
            "current": c_classes["tests"]["share_percent"],
        },
        "per_ticket_docs_share_percent": {
            "baseline": (b_classes.get("per_ticket_docs") or {}).get("share_percent"),
            "current": c_classes["per_ticket_docs"]["share_percent"],
        },
        "living_specs_share_percent": {
            "baseline": (b_classes.get("living_specs") or {}).get("share_percent"),
            "current": c_classes["living_specs"]["share_percent"],
        },
        "process_tooling_share_percent": {
            "baseline": (b_classes.get("process_tooling_and_other_docs") or {}).get("share_percent"),
            "current": c_classes["process_tooling_and_other_docs"]["share_percent"],
        },
        "commits_touching_prod_percent": {
            "baseline": b_commits.get("commits_touching_production_code_percent"),
            "current": c_commits["commits_touching_production_code_percent"],
        },
        "gates_1_to_5_revision_rate_percent": {
            "baseline": b_g15.get("revision_rate_percent"),
            "current": pm_stats["gates_1_to_5"]["revision_rate_percent"],
        },
        "stage_6_ticket_bounce_rate_percent": {
            "baseline": b_g6.get("ticket_bounce_rate_percent"),
            "current": pm_stats["stage_6_cold_review"]["ticket_bounce_rate_percent"],
        },
        "process_escape_bugs_count": {
            "baseline": b_esc.get("process_escape_bugs_count"),
            "current": jira_stats["process_escape_bugs_count"],
        },
        "completed_tickets": {
            "baseline": b_tp.get("completed_tickets"),
            "current": pm_stats["throughput"]["completed_tickets"],
        },
        "completed_story_points": {
            "baseline": b_tp.get("completed_story_points"),
            "current": pm_stats["throughput"]["completed_story_points"],
        },
        "mean_wall_minutes_per_ticket": {
            "baseline": b_tp.get("mean_wall_minutes_per_ticket_across_3_lanes"),
            "current": pm_stats["throughput"]["mean_wall_minutes_per_ticket"],
        },
        "po_minutes_weekly": {
            "baseline": (baseline.get("human_po_effort") or {}).get("weekly_po_minutes_measured"),
            "current": po_minutes,
        },
    }
    for item in comparisons.values():
        item["delta"] = _delta(item["current"], item["baseline"])

    return {
        "schema_version": "1.0.0",
        "iso_week": week_label,
        "window_start": start_date.isoformat(),
        "window_end": end_date.isoformat(),
        "git_ref": ref,
        "process_version": process_version,
        "baseline_iso_week": baseline.get("iso_week", "2026-KW47"),
        "recorded_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "effort_split": git_stats["effort_split"],
        "commits": git_stats["commits"],
        "gates_1_to_5": pm_stats["gates_1_to_5"],
        "stage_6_cold_review": pm_stats["stage_6_cold_review"],
        "throughput": pm_stats["throughput"],
        "escapes": {
            "process_escape_bugs_count": jira_stats["process_escape_bugs_count"],
            "process_escape_bug_keys": jira_stats["process_escape_bug_keys"],
            "field_escape_bugs_count": jira_stats["field_escape_bugs_count"],
            "field_escape_bug_keys": jira_stats["field_escape_bug_keys"],
        },
        "tiers": jira_stats["tiers"],
        "po_acceptance_split": jira_stats.get("po_acceptance_split"),
        "human_po_minutes": po_minutes,
        "comparisons": comparisons,
    }


def _fmt(val, suffix=""):
    if val is None:
        return "n/a"
    return f"{val}{suffix}"


def _fmt_delta(val, suffix=""):
    if val is None:
        return "n/a"
    sign = "+" if val > 0 else ""
    return f"{sign}{val}{suffix}"


def render_scorecard_markdown(card):
    """Render scorecard with Baseline vs. Current vs. Delta table for Wochen Review & Retro."""
    c = card["comparisons"]
    marker = f"[method-scorecard:{card['iso_week']}:{card['process_version']}]"
    lines = [
        f"### Weekly Method Scorecard — {card['iso_week']} (`process_version: {card['process_version']}`)",
        f"Marker: `{marker}` | Window: `{card['window_start']}` .. `{card['window_end']}` | Baseline: `{card['baseline_iso_week']}`",
        "",
        "| Metric | Baseline (`KW47`) | Current | Delta |",
        "| :--- | ---: | ---: | ---: |",
        f"| Prod code lines added (`app/src/main/`) | {_fmt(c['production_code_share_percent']['baseline'], '%')} | {_fmt(c['production_code_share_percent']['current'], '%')} | {_fmt_delta(c['production_code_share_percent']['delta'], ' pp')} |",
        f"| Tests lines added | {_fmt(c['tests_share_percent']['baseline'], '%')} | {_fmt(c['tests_share_percent']['current'], '%')} | {_fmt_delta(c['tests_share_percent']['delta'], ' pp')} |",
        f"| Per-ticket docs lines added | {_fmt(c['per_ticket_docs_share_percent']['baseline'], '%')} | {_fmt(c['per_ticket_docs_share_percent']['current'], '%')} | {_fmt_delta(c['per_ticket_docs_share_percent']['delta'], ' pp')} |",
        f"| Living specs lines added | {_fmt(c['living_specs_share_percent']['baseline'], '%')} | {_fmt(c['living_specs_share_percent']['current'], '%')} | {_fmt_delta(c['living_specs_share_percent']['delta'], ' pp')} |",
        f"| Process tooling / other docs | {_fmt(c['process_tooling_share_percent']['baseline'], '%')} | {_fmt(c['process_tooling_share_percent']['current'], '%')} | {_fmt_delta(c['process_tooling_share_percent']['delta'], ' pp')} |",
        f"| Commits touching `app/src/main/` | {_fmt(c['commits_touching_prod_percent']['baseline'], '%')} | {_fmt(c['commits_touching_prod_percent']['current'], '%')} | {_fmt_delta(c['commits_touching_prod_percent']['delta'], ' pp')} |",
        f"| Gates 1–5 revision rate | {_fmt(c['gates_1_to_5_revision_rate_percent']['baseline'], '%')} | {_fmt(c['gates_1_to_5_revision_rate_percent']['current'], '%')} | {_fmt_delta(c['gates_1_to_5_revision_rate_percent']['delta'], ' pp')} |",
        f"| Stage 6 ticket bounce rate | {_fmt(c['stage_6_ticket_bounce_rate_percent']['baseline'], '%')} | {_fmt(c['stage_6_ticket_bounce_rate_percent']['current'], '%')} | {_fmt_delta(c['stage_6_ticket_bounce_rate_percent']['delta'], ' pp')} |",
        f"| `process-escape` bugs | {_fmt(c['process_escape_bugs_count']['baseline'])} | {_fmt(c['process_escape_bugs_count']['current'])} | {_fmt_delta(c['process_escape_bugs_count']['delta'])} |",
        f"| Completed tickets / SP | {_fmt(c['completed_tickets']['baseline'])} / {_fmt(c['completed_story_points']['baseline'])} | {_fmt(c['completed_tickets']['current'])} / {_fmt(c['completed_story_points']['current'])} | {_fmt_delta(c['completed_tickets']['delta'])} / {_fmt_delta(c['completed_story_points']['delta'])} |",
        f"| Mean wall min / ticket | {_fmt(c['mean_wall_minutes_per_ticket']['baseline'], ' min')} | {_fmt(c['mean_wall_minutes_per_ticket']['current'], ' min')} | {_fmt_delta(c['mean_wall_minutes_per_ticket']['delta'], ' min')} |",
        f"| Measured human PO minutes | {_fmt(c['po_minutes_weekly']['baseline'], ' min')} | {_fmt(c['po_minutes_weekly']['current'], ' min')} | {_fmt_delta(c['po_minutes_weekly']['delta'], ' min')} |",
        "",
        "**Risk-Tier Mix**: " + ", ".join(
            f"`{t}`: {v['tickets']} tickets ({v['story_points']} SP)"
            for t, v in card["tiers"].items()
        ),
    ]
    po_split = card.get("po_acceptance_split")
    if po_split:
        cov_str = ""
        if "with_po_comment" in po_split:
            cov_pct = _fmt(po_split.get("po_comment_coverage_percent"), "%")
            cov_str = f" | `po_comment_coverage`: {po_split['with_po_comment']}/{po_split['total_accepted']} ({cov_pct})"
        lines.append(
            f"**PO Acceptance Split**: `clean`: {po_split['clean_accepted']} | "
            f"`with_refinement`: {po_split['accepted_with_refinement']} | "
            f"`with_bug_escape`: {po_split['accepted_with_bug_escape']} "
            f"(total accepted: {po_split['total_accepted']}{cov_str})"
        )
    return "\n".join(lines)


def persist_scorecard_jsonl(card, jsonl_path=DEFAULT_SCORECARD_JSONL):
    """Idempotently upsert scorecard record by (iso_week, process_version) in JSONL."""
    os.makedirs(os.path.dirname(jsonl_path), exist_ok=True)
    existing = []
    if os.path.isfile(jsonl_path):
        with open(jsonl_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    existing.append(json.loads(line))
    key = (card["iso_week"], card["process_version"])
    updated = [r for r in existing if (r.get("iso_week"), r.get("process_version")) != key]
    updated.append(card)
    updated.sort(key=lambda r: (r.get("iso_week", ""), r.get("process_version", "")))
    with open(jsonl_path, "w", encoding="utf-8") as f:
        for rec in updated:
            f.write(json.dumps(rec, sort_keys=True) + "\n")
    return jsonl_path


def post_scorecard_to_weekly_retro(card, role="coordinator"):
    """Post rendered scorecard idempotently to the rolling Wochen Review & Retro ticket."""
    tools_dir = os.path.dirname(os.path.abspath(__file__))
    if tools_dir not in sys.path:
        sys.path.insert(0, tools_dir)
    import jira_util

    weekly_key = jira_util.ensure_weekly_retro_ticket(role=role)
    marker = f"[method-scorecard:{card['iso_week']}:{card['process_version']}]"
    if jira_util._weekly_rollup_already_posted(weekly_key, marker, role=role):
        print(f"Method scorecard {marker} already posted to {weekly_key}; skipping duplicate.")
        return weekly_key, False
    md = render_scorecard_markdown(card)
    jira_util.add_comment(weekly_key, md, role=role)
    print(f"Posted method scorecard {marker} to {weekly_key}.")
    return weekly_key, True


def main(argv=None):
    parser = argparse.ArgumentParser(description="Compute and post the Lean AAA v2 weekly method scorecard.")
    parser.add_argument("--week", default=current_iso_week(), help="ISO week (e.g. 2026-KW47)")
    parser.add_argument("--ref", default="develop", help="Git ref to inspect (default: develop)")
    parser.add_argument("--process-version", default="aaa-v2", help="Process version label (default: aaa-v2)")
    parser.add_argument("--filter-process-version", action="store_true", help="Filter process_metrics.jsonl strictly to --process-version")
    parser.add_argument("--baseline", default=DEFAULT_BASELINE_PATH, help="Baseline JSON path")
    parser.add_argument("--po-minutes", type=float, default=None, help="Explicit measured PO minutes for this week")
    parser.add_argument("--no-jira", action="store_true", help="Skip live Jira queries (offline/test mode)")
    parser.add_argument("--record", action="store_true", help="Upsert record into docs/engineering/metrics/method_scorecard.jsonl")
    parser.add_argument("--post-to-weekly-retro", action="store_true", help="Post idempotent scorecard comment to Wochen Review & Retro")
    parser.add_argument("--json", action="store_true", help="Print full JSON instead of markdown")
    parser.add_argument("--as", dest="role", default="coordinator", help="Jira role account (default: coordinator)")
    args = parser.parse_args(argv)

    card = compute_scorecard(
        iso_week=args.week,
        repo_root=REPO_ROOT,
        ref=args.ref,
        process_version=args.process_version,
        baseline_path=args.baseline,
        po_minutes_override=args.po_minutes,
        query_jira=not args.no_jira,
        role=args.role,
        filter_process_version=args.filter_process_version,
    )
    if args.record:
        persist_scorecard_jsonl(card)
    if args.post_to_weekly_retro:
        post_scorecard_to_weekly_retro(card, role=args.role)

    if args.json:
        print(json.dumps(card, indent=2, sort_keys=True))
    else:
        print(render_scorecard_markdown(card))
    return 0


if __name__ == "__main__":
    sys.exit(main())
