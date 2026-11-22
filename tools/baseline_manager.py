#!/usr/bin/env python3
"""
tools/baseline_manager.py
ASPICE SUP.8 (BP5/BP6/BP7) / REQ-PRO-119 / TST-PRO-119

Manages immutable annotated baseline tags, deterministic baseline content manifests
(docs/releases/<baseline>/manifest.md), and dry-run-by-default branch/tag hygiene audits.
"""

import argparse
import datetime
import json
import os
import re
import subprocess
import sys
from typing import Any, Dict, List, Optional, Tuple


PROTECTED_BRANCHES = frozenset({"master", "develop", "HEAD"})
_SWEEP_REPORT_FILENAME_RE = re.compile(r"^\d{4}-\d{2}-\d{2}_sweep\.md$")
_SWEEP_VERDICT_RE = re.compile(r"\*\*Verdict\*\*:\s*`?([A-Za-z0-9_-]+)`?")
_SWEEP_ROW_VERDICT_RE = re.compile(r"\|\s*`(PASS|FAIL)`\s*\|")


def _run_git(args: List[str], repo_root: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git"] + args,
        cwd=repo_root,
        capture_output=True,
        text=True,
        check=check,
    )


def _ref_exists(ref_name: str, repo_root: str) -> bool:
    res = _run_git(["rev-parse", "-q", "--verify", ref_name], repo_root, check=False)
    return res.returncode == 0


def sanitize_baseline_dirname(baseline: str) -> str:
    """Converts a baseline tag (e.g. 'sprint/2026-11-17_02_Spät' or 'V5.0.0') into a single directory name."""
    cleaned = baseline.strip().strip("/")
    return cleaned.replace("/", "_")


def create_annotated_tag(
    baseline: str,
    target_ref: str = "HEAD",
    message: Optional[str] = None,
    repo_root: str = ".",
) -> Tuple[bool, str]:
    """
    REQ-PRO-119.1: Creates an immutable annotated Git tag, refusing to overwrite an
    existing tag or collide with a local/remote branch of the same name.
    """
    tag_name = baseline.strip()
    if not tag_name:
        return False, "ERROR: Baseline tag name cannot be empty."

    commit_res = _run_git(["rev-parse", "--verify", f"{target_ref}^{{commit}}"], repo_root, check=False)
    if commit_res.returncode != 0:
        return False, f"ERROR: Target ref '{target_ref}' is not a valid commit: {commit_res.stderr.strip()}"
    commit_sha = commit_res.stdout.strip()

    if _ref_exists(f"refs/tags/{tag_name}", repo_root):
        return (
            False,
            f"ERROR: Tag 'refs/tags/{tag_name}' already exists. Force overwrite is prohibited (REQ-PRO-119.1).",
        )

    for branch_ref in (f"refs/heads/{tag_name}", f"refs/remotes/origin/{tag_name}"):
        if _ref_exists(branch_ref, repo_root):
            return (
                False,
                f"ERROR: Ref collision detected: '{branch_ref}' exists with the same name as tag '{tag_name}' (REQ-PRO-119.1).",
            )

    tag_msg = message.strip() if message and message.strip() else f"Immutable baseline {tag_name} at {commit_sha[:8]}"
    tag_res = _run_git(["tag", "-a", tag_name, commit_sha, "-m", tag_msg], repo_root, check=False)
    if tag_res.returncode != 0:
        return False, f"ERROR: Failed to create annotated tag '{tag_name}': {tag_res.stderr.strip()}"

    obj_type_res = _run_git(["cat-file", "-t", f"refs/tags/{tag_name}"], repo_root, check=False)
    obj_type = obj_type_res.stdout.strip()
    if obj_type != "tag":
        return False, f"ERROR: Created tag '{tag_name}' has unexpected object type '{obj_type}' (expected 'tag')."

    return True, f"CREATED annotated tag '{tag_name}' -> {commit_sha} (type={obj_type})"


def _parse_master_index_counts(index_path: str, kind: str) -> Dict[str, Any]:
    """Parses Total / Verified / Implemented / Planned counts from docs/requirements.md or docs/tests.md."""
    result: Dict[str, Any] = {
        "path": index_path,
        "exists": False,
        "total": 0,
        "verified": 0,
        "implemented": 0,
        "planned": 0,
    }
    if not os.path.isfile(index_path):
        return result

    with open(index_path, "r", encoding="utf-8") as f:
        content = f.read()

    result["exists"] = True
    total_pat = r"\*\*Total Living Requirements\*\*:\s*(\d+)" if kind == "req" else r"\*\*Total Living Tests\*\*:\s*(\d+)"
    m_total = re.search(total_pat, content)
    if m_total:
        result["total"] = int(m_total.group(1))

    row_pattern = re.compile(r"^\|\s*\[[^\]]+\]\([^)]+\)\s*\|[^|]*\|[^|]*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|$", re.MULTILINE)
    rows = row_pattern.findall(content)
    if rows:
        result["total"] = result["total"] or sum(int(r[0]) for r in rows)
        result["verified"] = sum(int(r[1]) for r in rows)
        result["implemented"] = sum(int(r[2]) for r in rows)
        result["planned"] = sum(int(r[3]) for r in rows)
    return result


def _collect_test_evidence(repo_root: str, baseline: str, test_evidence_path: Optional[str] = None) -> Dict[str, Any]:
    """Resolves sprint/release test evidence JSON or Markdown in docs/engineering/test_evidence/."""
    norm = sanitize_baseline_dirname(baseline)
    evidence_dir = os.path.join(repo_root, "docs", "engineering", "test_evidence")
    candidates: List[str] = []
    if test_evidence_path:
        candidates.append(test_evidence_path if os.path.isabs(test_evidence_path) else os.path.join(repo_root, test_evidence_path))
    else:
        candidates.extend([
            os.path.join(evidence_dir, f"{norm}.json"),
            os.path.join(evidence_dir, f"{norm}.md"),
            os.path.join(evidence_dir, f"{baseline.replace('/', '_')}.json"),
            os.path.join(evidence_dir, f"{baseline.replace('/', '_')}.md"),
        ])

    chosen = next((c for c in candidates if os.path.isfile(c)), None)
    if not chosen:
        return {"found": False, "rel_path": "NONE", "summary": "No matching test evidence file found"}

    rel_path = os.path.relpath(chosen, repo_root).replace("\\", "/")
    if chosen.endswith(".json"):
        try:
            with open(chosen, "r", encoding="utf-8") as f:
                data = json.load(f)
            ut = data.get("unit_tests", {})
            passed = int(ut.get("passed", 0))
            total = int(ut.get("total", 0))
            failures = int(ut.get("failures", 0))
            errors = int(ut.get("errors", 0))
            verdict = "PASS" if (total > 0 and failures == 0 and errors == 0) else "CHECK"
            md_companion = chosen[:-5] + ".md"
            display_path = os.path.relpath(md_companion, repo_root).replace("\\", "/") if os.path.isfile(md_companion) else rel_path
            return {
                "found": True,
                "rel_path": display_path,
                "summary": f"{verdict} ({passed}/{total} passed, {failures} failures, {errors} errors)",
            }
        except Exception as exc:
            return {"found": True, "rel_path": rel_path, "summary": f"Present (JSON parse warning: {exc})"}

    with open(chosen, "r", encoding="utf-8") as f:
        text = f.read()
    m_res = re.search(r"\*\*Overall Test Result\*\*:\s*\*\*([^*]+)\*\*", text)
    summary = m_res.group(1).strip() if m_res else "Present"
    return {"found": True, "rel_path": rel_path, "summary": summary}


def _collect_latest_emulator_sweep_deviation(repo_root: str) -> Optional[str]:
    """
    REQ-PRO-138.1 / REQ-PRO-138.2: Inspects the lexicographically latest YYYY-MM-DD_sweep.md
    under docs/engineering/emulator_sweeps/. Returns an Open Release Deviation string if the
    verdict is FAIL or missing/unparseable (fail-closed); returns None on PASS or no reports.
    """
    sweep_dir = os.path.join(repo_root, "docs", "engineering", "emulator_sweeps")
    if not os.path.isdir(sweep_dir):
        return None
    sweep_files = sorted(
        name
        for name in os.listdir(sweep_dir)
        if _SWEEP_REPORT_FILENAME_RE.match(name) and os.path.isfile(os.path.join(sweep_dir, name))
    )
    if not sweep_files:
        return None

    latest_name = sweep_files[-1]
    latest_path = os.path.join(sweep_dir, latest_name)
    rel_path = f"docs/engineering/emulator_sweeps/{latest_name}"
    try:
        with open(latest_path, "r", encoding="utf-8") as f:
            content = f.read()
    except OSError as exc:
        return f"**DEVIATION**: Latest emulator sweep `{rel_path}` could not be read ({exc}) (REQ-PRO-135 / SUP.9)"

    m_verdict = _SWEEP_VERDICT_RE.search(content)
    verdict = m_verdict.group(1).strip().upper() if m_verdict else "UNKNOWN"
    if verdict == "PASS":
        return None

    row_verdicts = _SWEEP_ROW_VERDICT_RE.findall(content)
    total_runs = len(row_verdicts)
    failed_runs = sum(1 for v in row_verdicts if v == "FAIL")
    run_detail = f" ({failed_runs}/{total_runs} runs failed)" if total_runs > 0 else ""
    return (
        f"**DEVIATION**: Latest emulator sweep `{rel_path}` verdict is `{verdict}`{run_detail} "
        f"— see sweep report for diagnostics (REQ-PRO-135 / SUP.9)"
    )


def _collect_open_risks_and_deviations(repo_root: str, norm_dir: str) -> Dict[str, List[str]]:
    """Collects High/Medium risks from docs/management/risk_register.md and open deviations from verification_summary.md and emulator sweeps."""
    high_medium_risks: List[str] = []
    risk_path = os.path.join(repo_root, "docs", "management", "risk_register.md")
    if os.path.isfile(risk_path):
        with open(risk_path, "r", encoding="utf-8") as f:
            for line in f:
                m = re.match(r"^\|\s*\*\*(RISK-\d+)\*\*\s*\|[^|]*\|\s*\*\*([^*]+)\*\*[^|]*\|\s*\d+\s*\|\s*\d+\s*\|\s*\*\*(\d+)\*\*\s*\|\s*\*\*(High|Medium)\*\*", line)
                if m:
                    high_medium_risks.append(f"{m.group(1)} ({m.group(4)}, E={m.group(3)}): {m.group(2).strip()}")

    open_deviations: List[str] = []
    summary_path = os.path.join(repo_root, "docs", "releases", norm_dir, "verification_summary.md")
    if os.path.isfile(summary_path):
        with open(summary_path, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip().startswith("- [ ] **DEVIATION**:"):
                    open_deviations.append(line.strip()[len("- [ ] "):])

    sweep_deviation = _collect_latest_emulator_sweep_deviation(repo_root)
    if sweep_deviation:
        open_deviations.append(sweep_deviation)

    return {"risks": high_medium_risks, "deviations": open_deviations}


def _resolve_governance_status(repo_root: str, override: Optional[str] = None) -> str:
    if override and override.strip():
        return override.strip()
    gov_script = os.path.join(repo_root, "tools", "verify_requirement_governance.py")
    if not os.path.isfile(gov_script):
        return "UNKNOWN (verify_requirement_governance.py not found)"
    res = subprocess.run(
        [sys.executable, gov_script],
        cwd=repo_root,
        capture_output=True,
        text=True,
        check=False,
    )
    return "PASS (exit 0)" if res.returncode == 0 else f"FAIL (exit {res.returncode})"


def generate_baseline_manifest(
    baseline: str,
    target_ref: str = "HEAD",
    test_evidence_path: Optional[str] = None,
    governance_status: Optional[str] = None,
    out_path: Optional[str] = None,
    repo_root: str = ".",
) -> Tuple[bool, str, str]:
    """
    REQ-PRO-119.2: Generates deterministic docs/releases/<baseline>/manifest.md aggregating
    commit SHA, REQ/TST counts, test evidence, open risks/deviations, and governance status.
    """
    baseline_name = baseline.strip()
    if not baseline_name:
        return False, "", "ERROR: Baseline name cannot be empty."

    commit_res = _run_git(["rev-parse", "--verify", f"{target_ref}^{{commit}}"], repo_root, check=False)
    if commit_res.returncode != 0:
        return False, "", f"ERROR: Target ref '{target_ref}' is not a valid commit: {commit_res.stderr.strip()}"
    commit_sha = commit_res.stdout.strip()

    subject_res = _run_git(["show", "-s", "--format=%s (%cI)", commit_sha], repo_root, check=False)
    commit_info = subject_res.stdout.strip() if subject_res.returncode == 0 else commit_sha[:8]

    tag_exists = _ref_exists(f"refs/tags/{baseline_name}", repo_root)
    tag_type = "MISSING"
    if tag_exists:
        t_res = _run_git(["cat-file", "-t", f"refs/tags/{baseline_name}"], repo_root, check=False)
        tag_type = t_res.stdout.strip() or "UNKNOWN"

    norm_dir = sanitize_baseline_dirname(baseline_name)
    baseline_type = "sprint" if (baseline_name.startswith("sprint/") or baseline_name.startswith("sprint_")) else "release"

    req_counts = _parse_master_index_counts(os.path.join(repo_root, "docs", "requirements.md"), "req")
    tst_counts = _parse_master_index_counts(os.path.join(repo_root, "docs", "tests.md"), "tst")
    evidence = _collect_test_evidence(repo_root, baseline_name, test_evidence_path=test_evidence_path)
    risks_devs = _collect_open_risks_and_deviations(repo_root, norm_dir)
    gov_status = _resolve_governance_status(repo_root, override=governance_status)
    generated_utc = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    target_file = out_path or os.path.join(repo_root, "docs", "releases", norm_dir, "manifest.md")
    os.makedirs(os.path.dirname(target_file), exist_ok=True)

    lines = [
        f"# Baseline Content Manifest: {baseline_name}",
        "",
        f"- **Baseline Tag**: `{baseline_name}` (Type: `{baseline_type}`, Git Tag Type: `{tag_type}`)",
        f"- **Baseline Commit**: `{commit_sha}` — {commit_info}",
        f"- **Generated (UTC)**: `{generated_utc}`",
        f"- **Governance Status (`verify_requirement_governance.py`)**: `{gov_status}`",
        "",
        "## 1. Living Documentation & Traceability Inventory",
        "",
        "| Artifact | Total | Verified | Implemented | Planned |",
        "| :--- | :---: | :---: | :---: | :---: |",
        f"| `docs/requirements.md` | {req_counts['total']} | {req_counts['verified']} | {req_counts['implemented']} | {req_counts['planned']} |",
        f"| `docs/tests.md` | {tst_counts['total']} | {tst_counts['verified']} | {tst_counts['implemented']} | {tst_counts['planned']} |",
        "",
        "## 2. Verification & Test Evidence",
        f"- **Test Evidence Artifact**: `{evidence['rel_path']}`",
        f"- **Test Evidence Summary**: {evidence['summary']}",
    ]

    summary_rel = f"docs/releases/{norm_dir}/verification_summary.md"
    sys5_rel = f"docs/releases/{norm_dir}/sys_verification_record.md"
    if os.path.isfile(os.path.join(repo_root, summary_rel)):
        lines.append(f"- **Release Verification Summary**: `{summary_rel}`")
    if os.path.isfile(os.path.join(repo_root, sys5_rel)):
        lines.append(f"- **SYS.5 Verification Record**: `{sys5_rel}`")

    lines.extend([
        "",
        "## 3. Open Risks & Deviations",
        f"- **Active High/Medium Risks (`docs/management/risk_register.md`)**: {len(risks_devs['risks'])}",
    ])
    for r in risks_devs["risks"]:
        lines.append(f"  - {r}")
    lines.append(f"- **Open Release Deviations**: {len(risks_devs['deviations'])}")
    for d in risks_devs["deviations"]:
        lines.append(f"  - {d}")
    lines.append("")

    with open(target_file, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    return True, target_file, f"GENERATED manifest at {target_file}"


def audit_and_cleanup_branches(
    base_ref: str = "develop",
    execute: bool = False,
    confirm_po: bool = False,
    target_branches: Optional[List[str]] = None,
    repo_root: str = ".",
) -> Tuple[bool, Dict[str, Any], str]:
    """
    REQ-PRO-119.5: Dry-run-by-default branch and tag hygiene audit. Refuses destructive
    execution without --confirm-po and only deletes merged local branches via git branch -d.
    """
    if execute and not confirm_po:
        return (
            False,
            {},
            "ERROR: Destructive cleanup requires explicit --confirm-po authorization (REQ-PRO-119.5).",
        )

    cur_res = _run_git(["rev-parse", "--abbrev-ref", "HEAD"], repo_root, check=False)
    current_branch = cur_res.stdout.strip() if cur_res.returncode == 0 else ""

    # Identify branches checked out in any worktree ('+ branch')
    wt_branches = set()
    wt_res = _run_git(["branch"], repo_root, check=False)
    if wt_res.returncode == 0:
        for line in wt_res.stdout.splitlines():
            stripped = line.strip()
            if stripped.startswith("+ ") or stripped.startswith("* "):
                wt_branches.add(stripped[2:].strip())

    merged_local: List[str] = []
    if _ref_exists(f"refs/heads/{base_ref}", repo_root):
        m_res = _run_git(["branch", "--merged", base_ref], repo_root, check=False)
        if m_res.returncode == 0:
            for line in m_res.stdout.splitlines():
                name = line.strip().lstrip("*+ ").strip()
                if name and name not in PROTECTED_BRANCHES and name != current_branch and name not in wt_branches:
                    merged_local.append(name)

    stale_remote: List[str] = []
    r_res = _run_git(["branch", "-r", "--merged", base_ref], repo_root, check=False)
    if r_res.returncode == 0:
        for line in r_res.stdout.splitlines():
            name = line.strip()
            if not name or "->" in name:
                continue
            short = name.replace("origin/", "", 1)
            if short not in PROTECTED_BRANCHES:
                stale_remote.append(name)

    lightweight_tags: List[str] = []
    tag_collisions: List[str] = []
    t_res = _run_git(["for-each-ref", "--format=%(refname:short) %(objecttype)", "refs/tags"], repo_root, check=False)
    if t_res.returncode == 0:
        for line in t_res.stdout.splitlines():
            parts = line.rsplit(" ", 1)
            if len(parts) != 2:
                continue
            t_name, t_type = parts
            if t_type == "commit":
                lightweight_tags.append(t_name)
            if _ref_exists(f"refs/heads/{t_name}", repo_root) or _ref_exists(f"refs/remotes/origin/{t_name}", repo_root):
                tag_collisions.append(t_name)

    deleted_branches: List[str] = []
    if execute and confirm_po:
        candidates = target_branches if target_branches else merged_local
        for br in candidates:
            if br in PROTECTED_BRANCHES or br == current_branch or br in wt_branches:
                continue
            if br not in merged_local:
                return (
                    False,
                    {"deleted_branches": deleted_branches},
                    f"ERROR: Refusing to delete unmerged or protected branch '{br}' (history preservation invariant).",
                )
            del_res = _run_git(["branch", "-d", br], repo_root, check=False)
            if del_res.returncode != 0:
                return False, {"deleted_branches": deleted_branches}, f"ERROR: git branch -d {br} failed: {del_res.stderr.strip()}"
            deleted_branches.append(br)

    report = {
        "mode": "execute" if (execute and confirm_po) else "dry-run",
        "base_ref": base_ref,
        "merged_local_branches": merged_local,
        "stale_remote_branches": stale_remote,
        "lightweight_tags_count": len(lightweight_tags),
        "lightweight_tags": lightweight_tags,
        "tag_branch_collisions": tag_collisions,
        "deleted_branches": deleted_branches,
    }
    msg = (
        f"CLEANUP ({report['mode']}): merged_local={len(merged_local)}, "
        f"stale_remote={len(stale_remote)}, lightweight_tags={len(lightweight_tags)}, "
        f"collisions={len(tag_collisions)}, deleted={len(deleted_branches)}"
    )
    return True, report, msg


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="ASPICE SUP.8 Baseline Manager (REQ-PRO-119).")
    parser.add_argument("--repo-root", default=".", help="Repository root path (default: .)")
    sub = parser.add_subparsers(dest="command", required=True)

    p_tag = sub.add_parser("tag", help="Create an immutable annotated baseline tag (refuses overwrites/collisions).")
    p_tag.add_argument("baseline", help="Tag name (e.g. sprint/2026-11-17_02_Spät or V5.0.0__273)")
    p_tag.add_argument("--target", default="HEAD", help="Target commit/ref (default: HEAD)")
    p_tag.add_argument("-m", "--message", default=None, help="Annotation message")

    p_man = sub.add_parser("manifest", help="Generate docs/releases/<baseline>/manifest.md.")
    p_man.add_argument("baseline", help="Baseline name/tag")
    p_man.add_argument("--target", default="HEAD", help="Target commit/ref (default: HEAD)")
    p_man.add_argument("--test-evidence", default=None, help="Explicit test evidence file path")
    p_man.add_argument("--governance-status", default=None, help="Override governance status string")
    p_man.add_argument("--out", default=None, help="Explicit output path")

    p_clean = sub.add_parser("cleanup-audit", help="Dry-run-by-default branch and tag hygiene audit.")
    p_clean.add_argument("--base", default="develop", help="Base integration branch (default: develop)")
    p_clean.add_argument("--execute", action="store_true", help="Delete merged local branches (requires --confirm-po)")
    p_clean.add_argument("--confirm-po", action="store_true", help="Explicit PO confirmation for branch deletion")
    p_clean.add_argument("--branches", nargs="*", default=None, help="Specific merged local branches to delete")
    p_clean.add_argument("--json", action="store_true", help="Print JSON report")

    args = parser.parse_args(argv)
    if args.command == "tag":
        ok, msg = create_annotated_tag(args.baseline, target_ref=args.target, message=args.message, repo_root=args.repo_root)
        print(msg, file=sys.stdout if ok else sys.stderr)
        return 0 if ok else 1

    if args.command == "manifest":
        ok, _, msg = generate_baseline_manifest(
            args.baseline,
            target_ref=args.target,
            test_evidence_path=args.test_evidence,
            governance_status=args.governance_status,
            out_path=args.out,
            repo_root=args.repo_root,
        )
        print(msg, file=sys.stdout if ok else sys.stderr)
        return 0 if ok else 1

    if args.command == "cleanup-audit":
        ok, report, msg = audit_and_cleanup_branches(
            base_ref=args.base,
            execute=args.execute,
            confirm_po=args.confirm_po,
            target_branches=args.branches,
            repo_root=args.repo_root,
        )
        if args.json and ok:
            print(json.dumps(report, indent=2, ensure_ascii=False))
        else:
            print(msg, file=sys.stdout if ok else sys.stderr)
        return 0 if ok else 1

    return 1


if __name__ == "__main__":
    sys.exit(main())
