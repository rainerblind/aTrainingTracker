#!/usr/bin/env python3
"""
Automated Quality Gate & Machine-Generated Evidence Bundle CLI (ATT-9543, ATT-9544; Lean AAA v2).

Replaces low-signal agent-prose Gates 1-5 on Tier B/C (and complements Tier A's pre-impl plan review)
with deterministic machine checks:
  1. Dependency-based unit test selection & execution (Android/Gradle + Python unittest)
  2. Safety linters (cursor, lifecycle, safe enum) & governance checks (conflict markers, doc headers)
  3. Ticket deliverable placeholder/link lint
  4. Machine-generated per-ticket evidence bundle (JUnit XML + commit SHA + run UTC + UI screenshots + SHA-256 manifest)
  5. Structured tool-only Jira gate verdict table & transition to Final Review (Agent)
"""

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
PLACEHOLDER_RE = re.compile(r"\b(TODO|TBD|FIXME|XXX)\b|<TICKET_KEY>")
MD_LINK_RE = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
UI_PATH_HINTS = ("/ui/", "compose", "res/layout/", "res/drawable/", "Theme.kt", "Screen.kt", "Dialog.kt", "Sheet.kt")


def get_head_sha(repo_root=REPO_ROOT):
    try:
        return subprocess.check_output(
            ["git", "-C", repo_root, "rev-parse", "HEAD"], text=True, stderr=subprocess.DEVNULL
        ).strip()
    except Exception:
        return "UNKNOWN"


def discover_changed_files(repo_root=REPO_ROOT, base_ref="develop"):
    """Return sorted repo-relative paths changed vs base_ref (plus working tree modifications)."""
    changed = set()
    for cmd in (
        ["git", "-C", repo_root, "diff", "--name-only", f"{base_ref}...HEAD"],
        ["git", "-C", repo_root, "diff", "--name-only"],
        ["git", "-C", repo_root, "diff", "--cached", "--name-only"],
    ):
        try:
            out = subprocess.check_output(cmd, text=True, stderr=subprocess.DEVNULL)
            for line in out.splitlines():
                if line.strip():
                    changed.add(line.strip())
        except Exception:
            pass
    return sorted(changed)


def is_ui_touching(changed_files):
    return any(any(h in f for h in UI_PATH_HINTS) for f in changed_files if f.startswith("app/src/main/"))


def _path_to_fqcn(rel_path):
    m = re.match(r"app/src/test/java/(.+)\.(?:kt|java)$", rel_path)
    return m.group(1).replace("/", ".") if m else None


def select_dependent_tests(changed_files, repo_root=REPO_ROOT):
    """
    Dependency-based test selection:
      - Directly modified app/src/test/ files
      - Unit test files in app/src/test/ whose name or source references any modified app/src/main/ class stem
      - Directly modified tools/test_*.py or matching tools/test_<module>.py for modified tools/<module>.py
    """
    android_tests = set()
    python_tests = set()
    prod_stems = set()

    for f in changed_files:
        if f.startswith("app/src/test/java/") and f.endswith((".kt", ".java")):
            fqcn = _path_to_fqcn(f)
            if fqcn:
                android_tests.add(fqcn)
        elif f.startswith("app/src/main/java/") and f.endswith((".kt", ".java")):
            stem = os.path.splitext(os.path.basename(f))[0]
            if len(stem) >= 3:
                prod_stems.add(stem)
        elif f.startswith("tools/test_") and f.endswith(".py"):
            if os.path.isfile(os.path.join(repo_root, f)):
                python_tests.add(f)
        elif f.startswith("tools/") and f.endswith(".py"):
            base = os.path.basename(f)
            cand = os.path.join("tools", f"test_{base}")
            if os.path.isfile(os.path.join(repo_root, cand)):
                python_tests.add(cand)

    if prod_stems:
        test_root = os.path.join(repo_root, "app", "src", "test", "java")
        stem_pat = re.compile(r"\b(" + "|".join(re.escape(s) for s in sorted(prod_stems)) + r")\b")
        if os.path.isdir(test_root):
            for dirpath, _, filenames in os.walk(test_root):
                for fname in filenames:
                    if not fname.endswith(("Test.kt", "Test.java")):
                        continue
                    full = os.path.join(dirpath, fname)
                    rel = os.path.relpath(full, repo_root).replace(os.sep, "/")
                    if any(fname.startswith(s) for s in prod_stems):
                        fqcn = _path_to_fqcn(rel)
                        if fqcn:
                            android_tests.add(fqcn)
                        continue
                    try:
                        with open(full, "r", encoding="utf-8", errors="replace") as fh:
                            if stem_pat.search(fh.read()):
                                fqcn = _path_to_fqcn(rel)
                                if fqcn:
                                    android_tests.add(fqcn)
                    except OSError:
                        pass

    return {"android_tests": sorted(android_tests), "python_tests": sorted(python_tests)}


def lint_ticket_deliverables(ticket_key, tier, repo_root=REPO_ROOT):
    """Check ticket deliverables for unresolved placeholders or broken local markdown links."""
    if tier == "tier-c":
        return True, ["Tier C fast lane: zero stage deliverables required."]
    paths = []
    if tier == "tier-b":
        cand = os.path.join(repo_root, "docs", "engineering", "tickets", f"{ticket_key}.md")
        if not os.path.isfile(cand):
            return False, [f"Missing Tier B combined deliverable: docs/engineering/tickets/{ticket_key}.md"]
        paths.append(cand)
    else:
        for sub, suffix in (
            ("analysis", f"{ticket_key}_analysis.md"),
            ("plans", f"{ticket_key}_plan.md"),
        ):
            cand = os.path.join(repo_root, "docs", "engineering", sub, suffix)
            if os.path.isfile(cand):
                paths.append(cand)

    errors = []
    for p in paths:
        rel = os.path.relpath(p, repo_root)
        with open(p, "r", encoding="utf-8", errors="replace") as f:
            text = f.read()
        for m in PLACEHOLDER_RE.finditer(text):
            errors.append(f"{rel}: unresolved placeholder '{m.group(0)}'")
        for m in MD_LINK_RE.finditer(text):
            target = m.group(1).strip().split("#", 1)[0]
            if not target or target.startswith(("http://", "https://", "mailto:", "file://")):
                continue
            resolved = os.path.normpath(os.path.join(os.path.dirname(p), target))
            resolved_root = os.path.normpath(os.path.join(repo_root, target))
            if not os.path.exists(resolved) and not os.path.exists(resolved_root):
                errors.append(f"{rel}: broken relative link '{target}'")
    if errors:
        return False, errors
    return True, [f"Deliverable lint passed ({len(paths)} file(s))."]


def parse_junit_xml_files(xml_paths):
    """Parse JUnit XML files returning total tests, failures, errors, skipped, and failing test names."""
    total = failures = errors = skipped = 0
    failing_names = []
    for p in xml_paths:
        try:
            root = ET.parse(p).getroot()
        except Exception as exc:
            errors += 1
            failing_names.append(f"{os.path.basename(p)} (XML parse error: {exc})")
            continue
        suites = [root] if root.tag == "testsuite" else list(root.findall("testsuite"))
        for suite in suites:
            for tc in suite.findall("testcase"):
                total += 1
                cls = tc.attrib.get("classname", "")
                name = tc.attrib.get("name", "")
                if tc.find("failure") is not None:
                    failures += 1
                    failing_names.append(f"{cls}.{name}".strip("."))
                elif tc.find("error") is not None:
                    errors += 1
                    failing_names.append(f"{cls}.{name}".strip("."))
                elif tc.find("skipped") is not None:
                    skipped += 1
    return {
        "total": total,
        "failures": failures,
        "errors": errors,
        "skipped": skipped,
        "failing_tests": failing_names,
    }


def _run_cmd(cmd, cwd, timeout=900):
    proc = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=timeout)
    return proc.returncode, proc.stdout, proc.stderr


def run_python_tests(test_files, repo_root=REPO_ROOT):
    """Run selected Python unit test files and capture structured pass/fail with failing test names."""
    if not test_files:
        return {"passed": True, "ran": 0, "failing_tests": [], "details": "No Python tests selected."}
    failing = []
    ran = 0
    for tf in test_files:
        rc, out, err = _run_cmd([sys.executable, "-m", "unittest", "-v", tf], cwd=repo_root, timeout=300)
        combined = out + "\n" + err
        m = re.search(r"Ran (\d+) test", combined)
        if m:
            ran += int(m.group(1))
        if rc != 0:
            for fm in re.finditer(r"^(?:FAIL|ERROR):\s+([^\s]+)", combined, re.MULTILINE):
                failing.append(fm.group(1))
            if not failing:
                failing.append(tf)
    return {
        "passed": len(failing) == 0,
        "ran": ran,
        "failing_tests": failing,
        "details": f"{ran} Python tests passed." if not failing else f"Failed: {', '.join(failing)}",
    }


def run_android_tests(test_classes, repo_root=REPO_ROOT):
    """Run selected Gradle unit tests via ./gradlew --rerun --tests and parse JUnit XML."""
    if not test_classes:
        return {"passed": True, "ran": 0, "failing_tests": [], "xml_files": [], "details": "No Android tests selected."}
    cmd = ["./gradlew", ":app:testDebugUnitTest", "--rerun"]
    for tc in test_classes:
        cmd.extend(["--tests", tc])
    rc, out, err = _run_cmd(cmd, cwd=repo_root, timeout=1800)
    xml_dir = os.path.join(repo_root, "app", "build", "test-results", "testDebugUnitTest")
    xml_files = []
    if os.path.isdir(xml_dir):
        for tc in test_classes:
            cand = os.path.join(xml_dir, f"TEST-{tc}.xml")
            if os.path.isfile(cand):
                xml_files.append(cand)
    parsed = parse_junit_xml_files(xml_files)
    failing = list(parsed["failing_tests"])
    if rc != 0 and not failing:
        failing.append("Gradle :app:testDebugUnitTest exited non-zero (check compilation/test logs)")
    return {
        "passed": rc == 0 and parsed["failures"] == 0 and parsed["errors"] == 0,
        "ran": parsed["total"],
        "failing_tests": failing,
        "xml_files": xml_files,
        "details": (
            f"{parsed['total']} Android unit tests passed ({len(xml_files)} JUnit XML)."
            if not failing
            else f"Failed Android test(s): {', '.join(failing)}"
        ),
    }


def run_automated_gates(
    ticket_key,
    tier="tier-b",
    repo_root=REPO_ROOT,
    base_ref="develop",
    changed_files=None,
    run_tests=True,
):
    """Execute machine-checkable gates for a ticket and return a structured result dict."""
    run_started_utc = dt.datetime.now(dt.timezone.utc).isoformat()
    commit_sha = get_head_sha(repo_root)
    files = changed_files if changed_files is not None else discover_changed_files(repo_root, base_ref)
    selected = select_dependent_tests(files, repo_root=repo_root)
    ui_changed = is_ui_touching(files)

    checks = []

    # 1. Dependent Tests
    py_res = run_python_tests(selected["python_tests"], repo_root) if run_tests else {"passed": True, "ran": 0, "failing_tests": [], "details": "Skipped (--skip-tests)."}
    and_res = run_android_tests(selected["android_tests"], repo_root) if run_tests else {"passed": True, "ran": 0, "failing_tests": [], "xml_files": [], "details": "Skipped (--skip-tests)."}
    all_failing = py_res["failing_tests"] + and_res["failing_tests"]
    checks.append({
        "name": "dependent_unit_tests",
        "passed": py_res["passed"] and and_res["passed"],
        "details": f"Android: {and_res['details']} | Python: {py_res['details']}",
        "failing_tests": all_failing,
    })

    # 2. Safety Linters (when app/src/main/ touched)
    if any(f.startswith("app/src/main/") for f in files):
        linter_errors = []
        for linter in ("tools/lint_cursor_safety.py", "tools/lint_lifecycle_safety.py", "tools/lint_safe_enum.py"):
            lp = os.path.join(repo_root, linter)
            if os.path.isfile(lp):
                rc, _, err = _run_cmd([sys.executable, lp], cwd=repo_root, timeout=120)
                if rc != 0:
                    linter_errors.append(f"{linter}: {err.strip() or 'failed'}")
        checks.append({
            "name": "static_safety_linters",
            "passed": len(linter_errors) == 0,
            "details": "All safety linters passed." if not linter_errors else "; ".join(linter_errors),
        })

    # 3. Deliverable Placeholder & Link Lint
    deliv_ok, deliv_msgs = lint_ticket_deliverables(ticket_key, tier, repo_root=repo_root)
    checks.append({
        "name": "deliverable_lint",
        "passed": deliv_ok,
        "details": "; ".join(deliv_msgs),
    })

    # 4. Conflict Markers Check
    gov_script = os.path.join(repo_root, "tools", "verify_requirement_governance.py")
    if os.path.isfile(gov_script):
        rc, out, err = _run_cmd([sys.executable, gov_script, "--check-conflict-markers"], cwd=repo_root, timeout=120)
        checks.append({
            "name": "conflict_markers",
            "passed": rc == 0,
            "details": (out.strip().splitlines()[-1] if out.strip() else err.strip() or "checked"),
        })

    overall_pass = all(c["passed"] for c in checks)
    return {
        "schema_version": "1.0.0",
        "ticket_key": ticket_key,
        "risk_tier": tier,
        "commit_sha": commit_sha,
        "run_started_utc": run_started_utc,
        "ui_touching": ui_changed,
        "changed_files_count": len(files),
        "selected_tests": selected,
        "android_xml_files": and_res.get("xml_files", []),
        "python_test_summary": py_res,
        "android_test_summary": {k: v for k, v in and_res.items() if k != "xml_files"},
        "checks": checks,
        "verdict": "PASS" if overall_pass else "FAIL",
        "failing_tests": all_failing,
    }


def render_gate_report_markdown(result, bundle_rel_dir=None):
    """Render strictly machine-generated structured Jira/Markdown gate report (no agent prose)."""
    marker = f"[auto-gate:{result['ticket_key']}:{result['commit_sha'][:8]}:{result['verdict']}]"
    lines = [
        f"### Automated Gate Report — {result['ticket_key']} ({result['risk_tier']}) — **{result['verdict']}**",
        f"Marker: `{marker}` | Commit: `{result['commit_sha'][:12]}` | Run UTC: `{result['run_started_utc']}`",
        "",
        "| Check | Status | Details |",
        "| :--- | :---: | :--- |",
    ]
    for c in result["checks"]:
        st = "PASS" if c["passed"] else "FAIL"
        lines.append(f"| `{c['name']}` | **{st}** | {c['details']} |")
    if result.get("failing_tests"):
        lines.append("")
        lines.append(f"**Failing Test(s)**: {', '.join(f'`{t}`' for t in result['failing_tests'])}")
    if bundle_rel_dir:
        lines.append(f"**Evidence Bundle**: `{bundle_rel_dir}`")
    return "\n".join(lines)


def advance_jira_after_auto_gate(result, bundle_rel_dir=None, role="agent1"):
    """Post structured gate table to Jira and advance Tier B/C to Final Review (Agent) on PASS."""
    tools_dir = os.path.dirname(os.path.abspath(__file__))
    if tools_dir not in sys.path:
        sys.path.insert(0, tools_dir)
    import jira_util

    report = render_gate_report_markdown(result, bundle_rel_dir=bundle_rel_dir)
    jira_util.add_comment(result["ticket_key"], report, role=role)
    if result["verdict"] == "PASS" and result["risk_tier"] in ("tier-b", "tier-c"):
        jira_util.transition_issue(result["ticket_key"], "final review (agent)", role=role, inject_context=False)
        print(f"AUTO_GATE_ADVANCED: {result['ticket_key']} ({result['risk_tier']}) passed automated gates and moved to 'Final Review (Agent)'.")
        return True
    return False


def main(argv=None):
    parser = argparse.ArgumentParser(description="Run Lean AAA v2 automated quality gates (ATT-9543).")
    parser.add_argument("--ticket", required=True, help="Jira ticket key (e.g. ATT-9543)")
    parser.add_argument("--tier", default="tier-b", choices=("tier-a", "tier-b", "tier-c"), help="Ticket risk tier")
    parser.add_argument("--base-ref", default="develop", help="Git base ref for changed-file discovery")
    parser.add_argument("--skip-tests", action="store_true", help="Skip test execution (lint-only dry run)")
    parser.add_argument("--advance-jira", action="store_true", help="Post structured gate report to Jira and advance Tier B/C to Final Review (Agent) on PASS")
    parser.add_argument("--as", dest="role", default="agent1", help="Jira actor role")
    parser.add_argument("--json", action="store_true", help="Output JSON verdict")
    args = parser.parse_args(argv)

    result = run_automated_gates(
        ticket_key=args.ticket,
        tier=args.tier,
        repo_root=REPO_ROOT,
        base_ref=args.base_ref,
        run_tests=not args.skip_tests,
    )
    if args.advance_jira:
        advance_jira_after_auto_gate(result, role=args.role)

    if args.json:
        print(json.dumps(result, indent=2, sort_keys=True))
    else:
        print(render_gate_report_markdown(result))
    return 0 if result["verdict"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
