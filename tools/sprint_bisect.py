#!/usr/bin/env python3
"""
tools/sprint_bisect.py - Sprint-level Regression Bisection & Forensic Archaeology Helper.

Assists in locating regression-inducing commits using coarse-grained sprint backward search
followed by fine-grained git bisect interval section.
"""

import sys
import os
import re
import subprocess
import argparse
import json
from typing import List, Dict, Optional, Tuple

APP_PACKAGE = "com.atrainingtracker"
MAIN_ACTIVITY = "de.rainerblind.trainingtracker.activities.MainActivityWithNavigation"
APK_PATH = "app/build/outputs/apk/debug/app-debug.apk"


def run_git(args: List[str], check: bool = True) -> str:
    """Executes a git command and returns stripped stdout."""
    res = subprocess.run(["git"] + args, capture_output=True, text=True)
    if check and res.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed (exit {res.returncode}):\n{res.stderr.strip()}")
    return res.stdout.strip()


def find_sprint_milestones(limit: int = 30) -> List[Dict[str, str]]:
    """
    Scans git log backwards for sprint review/retrospective commits and release tags.
    Returns list of dicts with keys: 'sha', 'sprint', 'date', 'subject', 'type'.
    """
    raw_log = run_git([
        "log",
        f"-n{limit * 3}",
        "--pretty=format:%h|%ad|%s",
        "--date=short",
        "--grep=Sprint Review & Retrospective",
        "--grep=docs(retro):",
        "--grep=docs: author Sprint",
        "--grep=Release V"
    ])

    milestones: List[Dict[str, str]] = []
    seen_sprints = set()

    if not raw_log:
        return milestones

    for line in raw_log.split("\n"):
        parts = line.split("|", 2)
        if len(parts) < 3:
            continue
        sha, date, subject = parts[0].strip(), parts[1].strip(), parts[2].strip()

        # Match sprint identifier, e.g., 2026-41.5 or 2026-40.16
        sprint_match = re.search(r'(?:Sprint\s+)?(202[0-9]-[0-9]+(?:\.[0-9]+)?)', subject, re.IGNORECASE)
        # Match release tag identifier, e.g., V4.9.38
        release_match = re.search(r'(V[0-9]+\.[0-9]+(?:\.[0-9]+)*(?:\.[0-9]+)?)', subject)

        if sprint_match:
            sprint_name = f"Sprint {sprint_match.group(1)}"
            if sprint_name not in seen_sprints:
                seen_sprints.add(sprint_name)
                milestones.append({
                    "sha": sha,
                    "sprint": sprint_name,
                    "date": date,
                    "subject": subject,
                    "type": "sprint"
                })
        elif release_match:
            rel_name = release_match.group(1)
            if rel_name not in seen_sprints:
                seen_sprints.add(rel_name)
                milestones.append({
                    "sha": sha,
                    "sprint": rel_name,
                    "date": date,
                    "subject": subject,
                    "type": "release"
                })

        if len(milestones) >= limit:
            break

    return milestones


def get_exponential_probes(milestones: List[Dict[str, str]]) -> List[Dict[str, any]]:
    """
    Computes the exponential backward probe sequence (offsets 1, 2, 4, 8, 16, 32...).
    Offset 1 is 1 sprint back (index 0).
    Offset 2 is 2 sprints back (index 1).
    Offset 4 is 4 sprints back (index 3).
    Offset 2^k is 2^k sprints back (index 2^k - 1).
    """
    probes = []
    k = 0
    while True:
        offset = 1 << k  # 1, 2, 4, 8, 16, 32...
        idx = offset - 1
        if idx >= len(milestones):
            break
        probes.append({
            "probe_num": k + 1,
            "power": k,
            "offset": offset,
            "idx": idx,
            "milestone": milestones[idx]
        })
        k += 1
    return probes


def cmd_list_exp_probes(limit: int = 64):
    """Prints the exponential backward sprint search probe sequence."""
    milestones = find_sprint_milestones(limit=limit)
    if not milestones:
        print("No sprint milestones found in git history.")
        return

    probes = get_exponential_probes(milestones)
    print("=" * 96)
    print("EXPONENTIAL BACKWARD SPRINT SEARCH SEQUENCE (Galloping Search: 1, 2, 4, 8, 16...)")
    print("=" * 96)
    print(f"{'Probe':<6} | {'Offset':<10} | {'Milestone / Sprint':<18} | {'SHA':<9} | {'Date':<10} | {'Deploy Command'}")
    print("-" * 96)
    for p in probes:
        m = p['milestone']
        offset_str = f"-{p['offset']} (2^{p['power']})"
        deploy_cmd = f"python3 tools/sprint_bisect.py deploy {m['sha']}"
        print(f"#{p['probe_num']:<5} | {offset_str:<10} | {m['sprint']:<18} | {m['sha']:<9} | {m['date']:<10} | {deploy_cmd}")
    print("=" * 96)
    print("DECISION PROTOCOL:")
    print("  1. Deploy Probe #1 (offset -1):")
    print("     • If WORKS  -> Interval bounded: [C_good = Probe #1, C_bad = HEAD]. Proceed to Phase 2 (git bisect).")
    print("     • If BROKEN -> Advance to Probe #2 (offset -2).")
    print("  2. For Probe #K (K > 1, offset -2^(K-1)):")
    print("     • If WORKS  -> Interval bounded: [C_good = Probe #K, C_bad = Probe #(K-1)]. Proceed to Phase 2.")
    print("     • If BROKEN -> Advance to Probe #(K+1) (offset -2^K).")
    print("=" * 96)


def cmd_deploy_probe(probe_num: int, limit: int = 64):
    """Directly deploys probe N (1, 2, 3...) from the exponential sequence."""
    if probe_num < 1:
        print("Error: Probe number must be >= 1.")
        sys.exit(1)
    milestones = find_sprint_milestones(limit=limit)
    probes = get_exponential_probes(milestones)
    if probe_num > len(probes):
        print(f"Error: Probe #{probe_num} exceeds available milestones ({len(probes)} probes available).")
        sys.exit(1)
    target_probe = probes[probe_num - 1]
    m = target_probe['milestone']
    print(f"Deploying Probe #{probe_num}: {m['sprint']} (offset -{target_probe['offset']}, SHA {m['sha']}, {m['date']})...")
    cmd_deploy(target_ref=m['sha'])


def cmd_list_sprints(limit: int = 30):
    """Prints a formatted table of historical sprint milestones backwards, highlighting exponential probe steps."""
    milestones = find_sprint_milestones(limit=limit)
    if not milestones:
        print("No sprint milestones found in git history.")
        return

    probe_map = {}
    for p in get_exponential_probes(milestones):
        probe_map[p['idx']] = f"Probe #{p['probe_num']} (2^{p['power']})"

    print("=" * 96)
    print(f"{'Idx':<4} | {'Offset':<7} | {'Exp Probe':<15} | {'Milestone / Sprint':<18} | {'SHA':<9} | {'Date':<10} | {'Commit Subject'}")
    print("-" * 96)
    for idx, m in enumerate(milestones):
        subj = m['subject'][:28] + ("..." if len(m['subject']) > 28 else "")
        probe_str = probe_map.get(idx, "--")
        offset_str = f"-{idx + 1}"
        print(f"{idx:<4} | {offset_str:<7} | {probe_str:<15} | {m['sprint']:<18} | {m['sha']:<9} | {m['date']:<10} | {subj}")
    print("=" * 96)
    print("Tip: Run 'python3 tools/sprint_bisect.py list-probes' to view only the exponential search probes.")



def cmd_deploy(target_ref: Optional[str] = None):
    """
    Optionally checks out target_ref, builds debug APK via Gradle,
    installs on connected device via ADB, and launches the main activity.
    """
    if target_ref:
        print(f"Checking out {target_ref}...")
        run_git(["checkout", target_ref])

    current_sha = run_git(["rev-parse", "--short", "HEAD"])
    print(f"Building debug APK at {current_sha}...")
    build_res = subprocess.run(["./gradlew", "assembleDebug"], capture_output=True, text=True)
    if build_res.returncode != 0:
        print("Gradle assembleDebug failed:\n", build_res.stderr[-1000:] if build_res.stderr else build_res.stdout[-1000:])
        sys.exit(1)

    if not os.path.exists(APK_PATH):
        print(f"Error: APK not found at {APK_PATH}")
        sys.exit(1)

    print(f"Installing {APK_PATH} on connected device...")
    install_res = subprocess.run(["adb", "install", "-r", APK_PATH], capture_output=True, text=True)
    if install_res.returncode != 0:
        print(f"adb install failed: {install_res.stderr.strip() or install_res.stdout.strip()}")
        sys.exit(1)
    print(install_res.stdout.strip())

    print(f"Launching {APP_PACKAGE}/{MAIN_ACTIVITY}...")
    subprocess.run(["adb", "shell", "am", "start", "-n", f"{APP_PACKAGE}/{MAIN_ACTIVITY}"], check=False)
    print(f"Successfully deployed {current_sha} to device!")


def cmd_bisect_start(bad_ref: str, good_ref: str):
    """Initializes git bisect with bad and good boundaries."""
    print(f"Starting git bisect: BAD={bad_ref}, GOOD={good_ref}")
    run_git(["bisect", "reset"], check=False)
    out = run_git(["bisect", "start", bad_ref, good_ref])
    print(out)


def cmd_bisect_good():
    """Marks current commit good in bisect session."""
    out = run_git(["bisect", "good"])
    print(out)


def cmd_bisect_bad():
    """Marks current commit bad in bisect session."""
    out = run_git(["bisect", "bad"])
    print(out)


def cmd_bisect_reset():
    """Resets bisect session."""
    out = run_git(["bisect", "reset"])
    print("Bisect session reset.")
    print(out)


def cmd_analyze(commit_sha: str):
    """
    Forensic analysis of a culprit commit.
    Extracts commit details, Jira ticket, changed files, and Chesterton's Fence archaeology.
    """
    commit_info = run_git(["show", "--stat", "--summary", commit_sha])
    raw_subject = run_git(["log", "-1", "--pretty=format:%s", commit_sha])
    author = run_git(["log", "-1", "--pretty=format:%an <%ae> on %ad", commit_sha])

    # Extract Jira ticket keys (e.g. ATT-1234)
    jira_keys = list(set(re.findall(r'\b(ATT-[0-9]+)\b', commit_info)))

    print("=" * 80)
    print(f"CULPRIT COMMIT FORENSIC REPORT: {commit_sha}")
    print("=" * 80)
    print(f"Subject: {raw_subject}")
    print(f"Author : {author}")
    print(f"Associated Jira Ticket(s): {', '.join(jira_keys) if jira_keys else 'None detected'}")
    print("\n--- Summary of Modified Files ---")
    stat_lines = run_git(["show", "--stat", "--oneline", commit_sha]).split("\n")
    for l in stat_lines[1:25]:
        print("  " + l)
    if len(stat_lines) > 25:
        print("  ... (truncated)")

    print("\n" + "=" * 80)
    print("CHESTERTON'S FENCE ARCHAEOLOGY CHECKLIST:")
    print("=" * 80)
    print("1. Original Rationale:")
    print("   Why was this commit introduced? What feature or bug did it intend to address?")
    print("2. Regression Mechanism:")
    print("   Which specific line or architectural shift inadvertently broke the feature?")
    print("3. Invariants to Preserve:")
    print("   What behavior must NOT be broken when repairing the regression?")
    print("4. Targeted Fix Strategy:")
    print("   How can we fix the regression without reverting the beneficial changes of this commit?")
    print("=" * 80)


def main():
    parser = argparse.ArgumentParser(
        description="Sprint-level Regression Bisection & Forensic Archaeology Helper"
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    # list-sprints
    sp_list = subparsers.add_parser("list-sprints", help="List recent sprint milestones backwards")
    sp_list.add_argument("-n", "--limit", type=int, default=30, help="Number of sprint milestones to display")

    # list-probes (exponential search)
    sp_probes = subparsers.add_parser("list-probes", aliases=["probes"], help="List exponential backward sprint probes (1, 2, 4, 8, 16...)")
    sp_probes.add_argument("-n", "--limit", type=int, default=64, help="Max history depth to scan")

    # deploy-probe
    sp_dep_probe = subparsers.add_parser("deploy-probe", help="Deploy probe N from exponential sequence directly")
    sp_dep_probe.add_argument("probe", type=int, help="Probe number (1 for offset 1, 2 for offset 2, 3 for offset 4...)")

    # deploy
    sp_deploy = subparsers.add_parser("deploy", help="Build and install APK on device")
    sp_deploy.add_argument("ref", nargs="?", default=None, help="Git commit, branch, or tag to checkout before build")

    # bisect-start
    sp_start = subparsers.add_parser("bisect-start", help="Start git bisect with bad and good commits")
    sp_start.add_argument("bad", help="Bad commit SHA/ref (where feature is broken)")
    sp_start.add_argument("good", help="Good commit SHA/ref (where feature worked)")

    # bisect-good / bad / reset
    subparsers.add_parser("bisect-good", help="Mark current bisect commit as GOOD")
    subparsers.add_parser("bisect-bad", help="Mark current bisect commit as BAD")
    subparsers.add_parser("bisect-reset", help="Reset/abort active git bisect session")

    # analyze
    sp_analyze = subparsers.add_parser("analyze", help="Perform forensic analysis on a culprit commit")
    sp_analyze.add_argument("sha", help="Commit SHA to analyze")

    args = parser.parse_args()

    if args.command in ("list-probes", "probes"):
        cmd_list_exp_probes(limit=args.limit)
    elif args.command == "deploy-probe":
        cmd_deploy_probe(probe_num=args.probe)
    elif args.command == "list-sprints":
        cmd_list_sprints(limit=args.limit)
    elif args.command == "deploy":
        cmd_deploy(target_ref=args.ref)
    elif args.command == "bisect-start":
        cmd_bisect_start(bad_ref=args.bad, good_ref=args.good)
    elif args.command == "bisect-good":
        cmd_bisect_good()
    elif args.command == "bisect-bad":
        cmd_bisect_bad()
    elif args.command == "bisect-reset":
        cmd_bisect_reset()
    elif args.command == "analyze":
        cmd_analyze(commit_sha=args.sha)


if __name__ == "__main__":
    main()
