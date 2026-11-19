#!/usr/bin/env python3
"""
tools/check_static_analysis_baselines.py

Single-source XML parser and monotonic ratchet for static analysis baselines
(REQ-PRO-123.2, REQ-PRO-133.4 / SWE.4 BP1/BP3):
- Counts <ID> entries in app/detekt-baseline.xml (ceiling 9,516)
- Counts <issue> entries in app/lint-baseline.xml (ceiling 216, zero Recycle entries)
- Fails (exit 1) if either baseline grows relative to --base-ref or ceiling,
  or if any <issue id="Recycle"> remains in app/lint-baseline.xml,
  with graceful fallback to ceiling when --base-ref is missing (shallow clone).
- Exposes collect_baseline_metrics() for tools/sprint_metrics.py and its mirror.
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

DETEKT_BASELINE_PATH = "app/detekt-baseline.xml"
LINT_BASELINE_PATH = "app/lint-baseline.xml"
DEFAULT_DETEKT_CEILING = 9516
DEFAULT_LINT_CEILING = 216
DEFAULT_DETEKT_BURN_DOWN_PER_SPRINT = 250
DEFAULT_LINT_BURN_DOWN_PER_SPRINT = 20

DETEKT_ID_RE = re.compile(r"<ID>")
LINT_ISSUE_RE = re.compile(r"<issue\b")
LINT_RECYCLE_ISSUE_RE = re.compile(r'<issue\b[^>]*\bid=["\']Recycle["\']')


def _default_repo_root():
    return Path(__file__).resolve().parent.parent


def count_detekt_baseline_entries(xml_text):
    """Counts recorded Detekt baseline smell <ID> entries."""
    return len(DETEKT_ID_RE.findall(xml_text or ""))


def count_lint_baseline_entries(xml_text):
    """Counts recorded Android Lint baseline <issue> entries."""
    return len(LINT_ISSUE_RE.findall(xml_text or ""))


def count_lint_recycle_entries(xml_text):
    """Counts recorded Android Lint baseline <issue id=\"Recycle\"> entries."""
    return len(LINT_RECYCLE_ISSUE_RE.findall(xml_text or ""))


def read_baseline_counts(repo_root=None):
    """Reads current Detekt and Android Lint baseline counts from disk."""
    root = Path(repo_root) if repo_root else _default_repo_root()
    detekt_file = root / DETEKT_BASELINE_PATH
    lint_file = root / LINT_BASELINE_PATH
    if not detekt_file.is_file():
        raise FileNotFoundError(f"Missing Detekt baseline file: {detekt_file}")
    if not lint_file.is_file():
        raise FileNotFoundError(f"Missing Android Lint baseline file: {lint_file}")
    lint_text = lint_file.read_text(encoding="utf-8")
    return {
        "detekt_baseline_count": count_detekt_baseline_entries(detekt_file.read_text(encoding="utf-8")),
        "lint_baseline_count": count_lint_baseline_entries(lint_text),
        "lint_recycle_count": count_lint_recycle_entries(lint_text),
    }


def resolve_git_base_ref(repo_root, base_ref):
    """Resolves a base Git ref (checking origin/<ref> fallback); returns None if unavailable."""
    if not base_ref:
        return None
    root = Path(repo_root) if repo_root else _default_repo_root()
    candidates = [base_ref]
    if not base_ref.startswith(("origin/", "HEAD")) and not re.fullmatch(r"[0-9a-fA-F]{7,40}", base_ref):
        candidates.append(f"origin/{base_ref}")
    for candidate in candidates:
        res = subprocess.run(
            ["git", "-C", str(root), "rev-parse", "--verify", "--quiet", f"{candidate}^{{commit}}"],
            capture_output=True,
            text=True,
            check=False,
        )
        if res.returncode == 0:
            return candidate
    return None


def _git_show_file(repo_root, ref, rel_path):
    res = subprocess.run(
        ["git", "-C", str(repo_root), "show", f"{ref}:{rel_path}"],
        capture_output=True,
        text=True,
        check=False,
    )
    if res.returncode != 0:
        return None
    return res.stdout


def read_base_ref_counts(repo_root, resolved_ref):
    """Reads Detekt and Lint baseline counts from a resolved Git ref, or None per missing file."""
    if not resolved_ref:
        return None
    root = Path(repo_root) if repo_root else _default_repo_root()
    detekt_xml = _git_show_file(root, resolved_ref, DETEKT_BASELINE_PATH)
    lint_xml = _git_show_file(root, resolved_ref, LINT_BASELINE_PATH)
    return {
        "detekt_baseline_count": count_detekt_baseline_entries(detekt_xml) if detekt_xml is not None else None,
        "lint_baseline_count": count_lint_baseline_entries(lint_xml) if lint_xml is not None else None,
    }


def check_baseline_ratchet(
    repo_root=None,
    base_ref=None,
    max_detekt=DEFAULT_DETEKT_CEILING,
    max_lint=DEFAULT_LINT_CEILING,
):
    """
    Verifies Detekt and Android Lint baselines have not grown vs base_ref and ceilings.
    Falls back cleanly to ceiling limits when base_ref is missing or unavailable (shallow clone).
    Returns (passed: bool, details: dict, messages: list[str]).
    """
    root = Path(repo_root) if repo_root else _default_repo_root()
    current = read_baseline_counts(root)
    curr_detekt = current["detekt_baseline_count"]
    curr_lint = current["lint_baseline_count"]
    curr_recycle = current["lint_recycle_count"]

    resolved_ref = resolve_git_base_ref(root, base_ref)
    base_counts = read_base_ref_counts(root, resolved_ref) if resolved_ref else None
    base_detekt = base_counts.get("detekt_baseline_count") if base_counts else None
    base_lint = base_counts.get("lint_baseline_count") if base_counts else None

    detekt_bounds = [b for b in (max_detekt, base_detekt) if b is not None]
    lint_bounds = [b for b in (max_lint, base_lint) if b is not None]
    detekt_limit = min(detekt_bounds) if detekt_bounds else None
    lint_limit = min(lint_bounds) if lint_bounds else None

    messages = []
    passed = True
    fallback_used = bool(base_ref) and resolved_ref is None
    if fallback_used:
        messages.append(
            f"INFO: Base ref '{base_ref}' unavailable (shallow clone or detached history); "
            f"falling back to ceiling limits (detekt<={max_detekt}, lint<={max_lint})."
        )

    if detekt_limit is not None and curr_detekt > detekt_limit:
        passed = False
        messages.append(
            f"FAIL: {DETEKT_BASELINE_PATH} grew to {curr_detekt} <ID> entries "
            f"(limit={detekt_limit}, base={base_detekt}, ceiling={max_detekt})."
        )
    else:
        messages.append(
            f"PASS: {DETEKT_BASELINE_PATH} = {curr_detekt} <ID> entries "
            f"(limit={detekt_limit}, base={base_detekt}, ceiling={max_detekt})."
        )

    if lint_limit is not None and curr_lint > lint_limit:
        passed = False
        messages.append(
            f"FAIL: {LINT_BASELINE_PATH} grew to {curr_lint} <issue> entries "
            f"(limit={lint_limit}, base={base_lint}, ceiling={max_lint})."
        )
    else:
        messages.append(
            f"PASS: {LINT_BASELINE_PATH} = {curr_lint} <issue> entries "
            f"(limit={lint_limit}, base={base_lint}, ceiling={max_lint})."
        )

    if curr_recycle > 0:
        passed = False
        messages.append(
            f"FAIL: {LINT_BASELINE_PATH} contains {curr_recycle} Recycle <issue> entries (must be 0; REQ-PRO-133.4)."
        )
    else:
        messages.append(
            f"PASS: {LINT_BASELINE_PATH} contains 0 Recycle <issue> entries (REQ-PRO-133.4)."
        )

    details = {
        "passed": passed,
        "detekt_baseline_count": curr_detekt,
        "lint_baseline_count": curr_lint,
        "lint_recycle_count": curr_recycle,
        "detekt_base_count": base_detekt,
        "lint_base_count": base_lint,
        "detekt_limit": detekt_limit,
        "lint_limit": lint_limit,
        "resolved_base_ref": resolved_ref,
        "fallback_used": fallback_used,
    }
    return passed, details, messages


def collect_baseline_metrics(repo_root=None, history=None):
    """
    Collects current Detekt/Lint baseline counts and deltas vs latest historical sprint record.
    Returns None if baseline XML files do not exist under repo_root.
    """
    root = Path(repo_root) if repo_root else _default_repo_root()
    if not (root / DETEKT_BASELINE_PATH).is_file() or not (root / LINT_BASELINE_PATH).is_file():
        return None

    counts = read_baseline_counts(root)
    curr_detekt = counts["detekt_baseline_count"]
    curr_lint = counts["lint_baseline_count"]

    prev_sa = None
    for rec in reversed(history or []):
        candidate = rec.get("static_analysis") if isinstance(rec, dict) else None
        if isinstance(candidate, dict) and (
            "detekt_baseline_count" in candidate or "lint_baseline_count" in candidate
        ):
            prev_sa = candidate
            break

    detekt_delta = (
        curr_detekt - prev_sa["detekt_baseline_count"]
        if prev_sa and isinstance(prev_sa.get("detekt_baseline_count"), int)
        else None
    )
    lint_delta = (
        curr_lint - prev_sa["lint_baseline_count"]
        if prev_sa and isinstance(prev_sa.get("lint_baseline_count"), int)
        else None
    )

    return {
        "detekt_baseline_count": curr_detekt,
        "lint_baseline_count": curr_lint,
        "detekt_ceiling": DEFAULT_DETEKT_CEILING,
        "lint_ceiling": DEFAULT_LINT_CEILING,
        "detekt_delta": detekt_delta,
        "lint_delta": lint_delta,
        "detekt_burn_down_target_per_sprint": DEFAULT_DETEKT_BURN_DOWN_PER_SPRINT,
        "lint_burn_down_target_per_sprint": DEFAULT_LINT_BURN_DOWN_PER_SPRINT,
    }


def _format_delta(delta):
    if delta is None:
        return "N/A"
    return f"+{delta}" if delta > 0 else str(delta)


def format_static_analysis_lines(sa_metrics, section_num):
    """Formats static analysis baseline counts and deltas as Confluence/Jira markdown lines."""
    if not sa_metrics:
        return []
    detekt_cnt = sa_metrics.get("detekt_baseline_count")
    lint_cnt = sa_metrics.get("lint_baseline_count")
    detekt_ceil = sa_metrics.get("detekt_ceiling", DEFAULT_DETEKT_CEILING)
    lint_ceil = sa_metrics.get("lint_ceiling", DEFAULT_LINT_CEILING)
    detekt_delta = _format_delta(sa_metrics.get("detekt_delta"))
    lint_delta = _format_delta(sa_metrics.get("lint_delta"))
    return [
        "",
        f"h3. {section_num}. Static Analysis Baseline Ratchet (SWE.4)",
        "|| Tool || Baseline File || Current Count || Ceiling || Delta vs Prior || Burn-Down Target/Sprint ||",
        f"| Detekt | `app/detekt-baseline.xml` | {detekt_cnt} | {detekt_ceil} | {detekt_delta} | -{sa_metrics.get('detekt_burn_down_target_per_sprint', DEFAULT_DETEKT_BURN_DOWN_PER_SPRINT)} |",
        f"| Android Lint | `app/lint-baseline.xml` | {lint_cnt} | {lint_ceil} | {lint_delta} | -{sa_metrics.get('lint_burn_down_target_per_sprint', DEFAULT_LINT_BURN_DOWN_PER_SPRINT)} |",
    ]


def main(argv=None):
    parser = argparse.ArgumentParser(description="Verify static analysis baseline ratchet.")
    parser.add_argument("--repo-root", default=None, help="Repository root path.")
    parser.add_argument("--base-ref", default=None, help="Optional Git base ref to ratchet against.")
    parser.add_argument("--max-detekt", type=int, default=DEFAULT_DETEKT_CEILING, help="Detekt <ID> ceiling.")
    parser.add_argument("--max-lint", type=int, default=DEFAULT_LINT_CEILING, help="Android Lint <issue> ceiling.")
    parser.add_argument("--json", action="store_true", help="Emit JSON output.")
    args = parser.parse_args(argv)

    try:
        passed, details, messages = check_baseline_ratchet(
            repo_root=args.repo_root,
            base_ref=args.base_ref,
            max_detekt=args.max_detekt,
            max_lint=args.max_lint,
        )
    except FileNotFoundError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    if args.json:
        print(json.dumps(details, sort_keys=True))
    else:
        for msg in messages:
            print(msg)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
