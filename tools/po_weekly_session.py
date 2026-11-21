#!/usr/bin/env python3
"""
Weekly PO Decision Session Digest & Signed Baseline CLI (ATT-9545; Lean AAA v2).

Replaces unsustainable per-Schicht Layer-4 human checkpoints with one prepared
30-60 minute weekly PO decision session:
  - generate-digest: builds docs/engineering/reviews/po_digest_<WEEK>.md
  - record-session: records authentic Human-Approved baseline (with optional annotated git tag
    and Wochen Review & Retro comment) OR truthful 'PO session not performed' deviation
    (never synthesizing approval).
"""

import argparse
import datetime as dt
import json
import os
import random
import re
import subprocess
import sys

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(TOOLS_DIR, ".."))
if TOOLS_DIR not in sys.path:
    sys.path.insert(0, TOOLS_DIR)

import method_scorecard

DEFAULT_PO_SESSIONS_PATH = os.path.join(REPO_ROOT, "docs", "engineering", "reviews", "po_sessions.jsonl")
DEFAULT_REVIEWS_DIR = os.path.join(REPO_ROOT, "docs", "engineering", "reviews")
REQ_HEADING_RE = re.compile(r"^\+\s*#{2,4}\s+((?:SYS)?REQ-[A-Z0-9._-]+:.*)$", re.MULTILINE)
TICKET_KEY_RE = re.compile(r"\b(ATT-\d+)\b")


def load_po_sessions(jsonl_path=DEFAULT_PO_SESSIONS_PATH):
    if not os.path.isfile(jsonl_path):
        return []
    rows = []
    with open(jsonl_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def save_po_session_record(record, jsonl_path=DEFAULT_PO_SESSIONS_PATH):
    os.makedirs(os.path.dirname(jsonl_path), exist_ok=True)
    rows = load_po_sessions(jsonl_path)
    week = record["iso_week"]
    updated = [r for r in rows if r.get("iso_week") != week]
    updated.append(record)
    updated.sort(key=lambda r: r.get("iso_week", ""))
    with open(jsonl_path, "w", encoding="utf-8") as f:
        for r in updated:
            f.write(json.dumps(r, sort_keys=True) + "\n")
    return jsonl_path


def get_last_approved_baseline_sha(jsonl_path=DEFAULT_PO_SESSIONS_PATH, fallback_sha="508e528d"):
    approved = [r for r in load_po_sessions(jsonl_path) if r.get("status") == "APPROVED" and r.get("baseline_sha")]
    if not approved:
        return fallback_sha
    approved.sort(key=lambda r: r.get("iso_week", ""))
    return approved[-1]["baseline_sha"]


def _git_output(args, repo_root=REPO_ROOT):
    try:
        return subprocess.check_output(["git", "-C", repo_root] + args, text=True, stderr=subprocess.DEVNULL)
    except Exception:
        return ""


def collect_req_diff_headings(base_sha, target_ref="develop", repo_root=REPO_ROOT):
    diff = _git_output(
        ["diff", "--unified=0", f"{base_sha}..{target_ref}", "--", "docs/requirements/", "docs/system/"],
        repo_root=repo_root,
    )
    headings = sorted(set(m.group(1).strip() for m in REQ_HEADING_RE.finditer(diff)))
    return headings


def collect_release_plan_diff(base_sha, target_ref="develop", repo_root=REPO_ROOT):
    return _git_output(
        ["diff", "--stat", f"{base_sha}..{target_ref}", "--", "docs/management/release_plan.md"],
        repo_root=repo_root,
    ).strip()


def sample_accepted_tickets(base_sha, target_ref="develop", repo_root=REPO_ROOT, sample_count=2, seed=None):
    log_out = _git_output(
        ["log", "--no-merges", "--format=%h %s", f"{base_sha}..{target_ref}"],
        repo_root=repo_root,
    )
    tickets = []
    seen = set()
    for line in log_out.splitlines():
        m = TICKET_KEY_RE.search(line)
        if not m:
            continue
        key = m.group(1)
        if key in seen:
            continue
        seen.add(key)
        sha = line.split(" ", 1)[0]
        summary = line.split(" ", 1)[1] if " " in line else ""
        bundle_rel = os.path.join("docs", "engineering", "test_evidence", "tickets", key)
        doc_rel = os.path.join("docs", "engineering", "tickets", f"{key}.md")
        tickets.append({
            "key": key,
            "commit_sha": sha,
            "summary": summary,
            "evidence_bundle": bundle_rel if os.path.isdir(os.path.join(repo_root, bundle_rel)) else None,
            "ticket_doc": doc_rel if os.path.isfile(os.path.join(repo_root, doc_rel)) else None,
        })
    rng = random.Random(seed)
    if len(tickets) <= sample_count:
        return tickets
    return rng.sample(tickets, sample_count)


def generate_weekly_digest(
    iso_week,
    repo_root=REPO_ROOT,
    target_ref="develop",
    base_sha=None,
    po_sessions_path=DEFAULT_PO_SESSIONS_PATH,
    out_dir=DEFAULT_REVIEWS_DIR,
    query_jira=False,
    seed=None,
):
    week_label, start_date, end_date = method_scorecard.parse_iso_week(iso_week)
    resolved_base = base_sha or get_last_approved_baseline_sha(po_sessions_path)
    candidate_sha = _git_output(["rev-parse", "--short", target_ref], repo_root=repo_root).strip() or target_ref

    req_changes = collect_req_diff_headings(resolved_base, target_ref, repo_root)
    rel_diff = collect_release_plan_diff(resolved_base, target_ref, repo_root)
    sampled = sample_accepted_tickets(resolved_base, target_ref, repo_root, sample_count=2, seed=seed)
    open_deviations = [
        r for r in load_po_sessions(po_sessions_path) if r.get("status") == "NOT_PERFORMED"
    ]
    card = method_scorecard.compute_scorecard(
        iso_week=week_label,
        repo_root=repo_root,
        ref=target_ref,
        po_sessions_path=po_sessions_path,
        query_jira=query_jira,
    )
    scorecard_md = method_scorecard.render_scorecard_markdown(card)

    lines = [
        f"# Weekly PO Decision Session Digest — {week_label}",
        "",
        f"* **Window**: `{start_date.isoformat()}` .. `{end_date.isoformat()}`",
        f"* **Last Approved Baseline**: `{resolved_base}`",
        f"* **Candidate Baseline**: `{target_ref} @ {candidate_sha}`",
        "* **Target PO Session Duration**: 30–60 minutes (`ATT-9545`)",
        "",
        "## 1. New / Changed Requirements (`REQ-*` & `SYSREQ-*`)",
    ]
    if req_changes:
        for h in req_changes[:30]:
            lines.append(f"* `{h}`")
        if len(req_changes) > 30:
            lines.append(f"* *(+{len(req_changes) - 30} more — inspect `git diff {resolved_base}..{target_ref} -- docs/requirements/ docs/system/`)*")
    else:
        lines.append("* None in this baseline window.")

    lines.extend([
        "",
        "## 2. Release Scope Changes (`docs/management/release_plan.md`)",
        f"```text\n{rel_diff or 'No changes to release_plan.md.'}\n```",
        "",
        "## 3. Random Audit Sample (2 Accepted Tickets & Evidence)",
    ])
    if sampled:
        for t in sampled:
            ev = f"`{t['evidence_bundle']}`" if t["evidence_bundle"] else "*No per-ticket bundle yet*"
            doc = f"`{t['ticket_doc']}`" if t["ticket_doc"] else "*Legacy/Tier-C*"
            lines.append(f"* **{t['key']}** (`{t['commit_sha']}`): {t['summary']} — Evidence: {ev} | Doc: {doc}")
    else:
        lines.append("* No commits in window.")

    lines.extend([
        "",
        "## 4. Open Deviations",
    ])
    if open_deviations:
        for d in open_deviations:
            lines.append(f"* **{d['iso_week']}**: `{d.get('deviation', 'PO session not performed')}` — {d.get('notes', '')}")
    else:
        lines.append("* None.")

    lines.extend([
        "",
        "## 5. Weekly Method Scorecard",
        scorecard_md,
        "",
        "## 6. PO Decision Record Command",
        f"```bash\npython3 tools/po_weekly_session.py record-session --week {week_label} --baseline-sha {candidate_sha} --minutes <MINUTES> --tag --post-jira --notes \"<Decisions>\"\n```",
        "",
    ])

    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, f"po_digest_{week_label}.md")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    return out_path, candidate_sha


def record_po_session(
    iso_week,
    performed=True,
    baseline_sha=None,
    minutes=None,
    approver="Human PO",
    notes="",
    po_sessions_path=DEFAULT_PO_SESSIONS_PATH,
    reviews_dir=DEFAULT_REVIEWS_DIR,
    repo_root=REPO_ROOT,
    tag_git=False,
    post_jira=False,
    role="coordinator",
):
    """
    Record an authentic PO weekly decision session (Human-Approved) OR an explicit
    'PO session not performed' deviation. Never fabricates a synthetic approval.
    """
    week_label, _, _ = method_scorecard.parse_iso_week(iso_week)
    today = dt.date.today().isoformat()
    recorded_at = dt.datetime.now(dt.timezone.utc).isoformat()

    if performed:
        if not baseline_sha:
            raise ValueError("baseline_sha is required when recording an approved PO session.")
        if minutes is None or float(minutes) <= 0:
            raise ValueError("Positive measured --minutes is required when recording an approved PO session.")
        trailer = f"Human-Approved: {approver} {today} baseline={baseline_sha}"
        record = {
            "schema_version": "1.0.0",
            "iso_week": week_label,
            "status": "APPROVED",
            "approver": approver,
            "baseline_sha": baseline_sha,
            "minutes": round(float(minutes), 1),
            "human_approved_trailer": trailer,
            "deviation": None,
            "notes": notes,
            "recorded_at": recorded_at,
        }
        save_po_session_record(record, po_sessions_path)
        if tag_git:
            tag_name = f"po-approved/{week_label}"
            msg = f"PO Weekly Baseline Approval {week_label}\n\n{trailer}\nMinutes: {record['minutes']}\nNotes: {notes}"
            subprocess.check_call(["git", "-C", repo_root, "tag", "-a", tag_name, baseline_sha, "-m", msg])
        if post_jira:
            import jira_util
            weekly_key = jira_util.ensure_weekly_retro_ticket(role=role)
            jira_util.add_comment(
                weekly_key,
                f"*Weekly PO Decision Session Approved ({week_label})*\n* `{trailer}`\n* Measured PO duration: `{record['minutes']} min`\n* Notes: {notes or 'Baseline approved.'}",
                role=role,
            )
        return record
    else:
        record = {
            "schema_version": "1.0.0",
            "iso_week": week_label,
            "status": "NOT_PERFORMED",
            "approver": None,
            "baseline_sha": baseline_sha,
            "minutes": None,
            "human_approved_trailer": None,
            "deviation": "PO session not performed",
            "notes": notes or "Weekly PO decision session did not take place; recorded truthfully with zero synthetic approval.",
            "recorded_at": recorded_at,
        }
        save_po_session_record(record, po_sessions_path)
        os.makedirs(reviews_dir, exist_ok=True)
        dev_path = os.path.join(reviews_dir, f"po_deviation_{week_label}.md")
        with open(dev_path, "w", encoding="utf-8") as f:
            f.write(
                f"# Open Deviation: PO session not performed ({week_label})\n\n"
                f"* **Status**: `NOT_PERFORMED` (Zero synthetic approval generated — `ATT-9545`)\n"
                f"* **Recorded At**: `{recorded_at}`\n"
                f"* **Notes**: {record['notes']}\n"
            )
        return record


def ensure_weekly_session_truthfulness(iso_week, po_sessions_path=DEFAULT_PO_SESSIONS_PATH, reviews_dir=DEFAULT_REVIEWS_DIR):
    """Ceremony 2 check: if no APPROVED session exists for iso_week, record truthful NOT_PERFORMED deviation."""
    week_label, _, _ = method_scorecard.parse_iso_week(iso_week)
    for r in load_po_sessions(po_sessions_path):
        if r.get("iso_week") == week_label and r.get("status") == "APPROVED":
            return r
    return record_po_session(
        week_label,
        performed=False,
        po_sessions_path=po_sessions_path,
        reviews_dir=reviews_dir,
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description="Weekly PO Decision Session CLI (ATT-9545).")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_dig = sub.add_parser("generate-digest", help="Generate weekly PO digest markdown")
    p_dig.add_argument("--week", default=method_scorecard.current_iso_week())
    p_dig.add_argument("--ref", default="develop")
    p_dig.add_argument("--base-sha", default=None)
    p_dig.add_argument("--seed", type=int, default=None)
    p_dig.add_argument("--with-jira", action="store_true")

    p_rec = sub.add_parser("record-session", help="Record PO session approval or truthful NOT_PERFORMED deviation")
    p_rec.add_argument("--week", default=method_scorecard.current_iso_week())
    p_rec.add_argument("--baseline-sha", default=None)
    p_rec.add_argument("--minutes", type=float, default=None)
    p_rec.add_argument("--approver", default="Human PO")
    p_rec.add_argument("--notes", default="")
    p_rec.add_argument("--not-performed", action="store_true")
    p_rec.add_argument("--tag", action="store_true")
    p_rec.add_argument("--post-jira", action="store_true")
    p_rec.add_argument("--as", dest="role", default="coordinator")

    args = parser.parse_args(argv)
    if args.cmd == "generate-digest":
        path, sha = generate_weekly_digest(
            iso_week=args.week, target_ref=args.ref, base_sha=args.base_sha,
            query_jira=args.with_jira, seed=args.seed,
        )
        print(f"Generated weekly PO digest: {path} (candidate baseline: {sha})")
    elif args.cmd == "record-session":
        rec = record_po_session(
            iso_week=args.week,
            performed=not args.not_performed,
            baseline_sha=args.baseline_sha,
            minutes=args.minutes,
            approver=args.approver,
            notes=args.notes,
            tag_git=args.tag,
            post_jira=args.post_jira,
            role=args.role,
        )
        print(json.dumps(rec, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
