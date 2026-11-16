#!/usr/bin/env python3
"""
tools/generate_test_evidence.py
ASPICE SWE.4 BP5 / SWE.5 BP7 / SWE.6 BP5 / SUP.8 / REQ-PRO-100 / TST-PRO-100

Generates persistent, baseline-controlled test and coverage evidence in git
(docs/engineering/test_evidence/<target>.md and .json) per sprint completion
and release candidate tag.

Ingests:
1. Machine-readable JUnit XML test results (app/build/test-results/**/TEST-*.xml
   or junit-test-results-*.zip).
2. TST-XXX correlation mapping via verify_requirement_governance.
3. Kover XML code coverage report (app/build/reports/kover/reportDebug.xml),
   extracting project-wide and critical package line/branch percentages.
"""

import argparse
import datetime
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from typing import Any, Dict, List, Optional, Tuple

try:
    from tools.verify_requirement_governance import (
        correlate_test_results_to_tsts,
        extract_test_code_tags,
        parse_junit_xml_results,
    )
except ImportError:
    try:
        from verify_requirement_governance import (  # type: ignore
            correlate_test_results_to_tsts,
            extract_test_code_tags,
            parse_junit_xml_results,
        )
    except ImportError:
        correlate_test_results_to_tsts = None  # type: ignore
        extract_test_code_tags = None  # type: ignore
        parse_junit_xml_results = None  # type: ignore


CRITICAL_SUBSYSTEMS = {
    "tracking": {
        "name": "Critical Tracking",
        "packages": ["com.atrainingtracker.trainingtracker.tracker"],
        "line_floor": 55.0,
        "branch_floor": 45.0,
    },
    "filters": {
        "name": "Critical Filters",
        "packages": ["com.atrainingtracker.banalservice.filters"],
        "line_floor": 70.0,
        "branch_floor": 50.0,
    },
    "sensors": {
        "name": "Critical Sensors & Protocols",
        "packages": [
            "com.atrainingtracker.banalservice.devices",
            "com.atrainingtracker.banalservice.sensor",
        ],
        "line_floor": 60.0,
        "branch_floor": 50.0,
    },
}


def get_git_commit(repo_root: str) -> str:
    """Retrieves current Git commit SHA from repo_root."""
    try:
        res = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=repo_root,
            capture_output=True,
            text=True,
            check=True,
        )
        return res.stdout.strip()
    except Exception:
        return "UNKNOWN_COMMIT"


def parse_kover_xml_report(kover_xml_path: str) -> Dict[str, Any]:
    """
    Parses JaCoCo-compatible Kover XML report (reportDebug.xml) for global
    and package-level line/branch coverage metrics (REQ-PRO-100.3).
    """
    if not os.path.isfile(kover_xml_path):
        return {
            "measured": False,
            "reason": f"Kover XML report not found at {kover_xml_path} (e.g. only unit tests executed without koverXmlReport)",
            "global": {"line_percent": None, "branch_percent": None},
            "critical_packages": {},
        }

    try:
        tree = ET.parse(kover_xml_path)
        root = tree.getroot()

        # 1. Global counters (direct children of <report>)
        global_counters = {}
        for c in root.findall("counter"):
            c_type = c.attrib.get("type", "")
            missed = int(c.attrib.get("missed", 0))
            covered = int(c.attrib.get("covered", 0))
            global_counters[c_type] = {"missed": missed, "covered": covered}

        def calc_pct(c_dict: Dict[str, Any], c_type: str) -> Optional[float]:
            if c_type in c_dict:
                tot = c_dict[c_type]["covered"] + c_dict[c_type]["missed"]
                if tot > 0:
                    return round((c_dict[c_type]["covered"] / tot) * 100.0, 2)
            return None

        global_line_pct = calc_pct(global_counters, "LINE")
        global_branch_pct = calc_pct(global_counters, "BRANCH")

        # 2. Package-level counters
        # Map package name (slashes or dots) to its counters
        package_counters: Dict[str, Dict[str, Dict[str, int]]] = {}
        for pkg in root.findall("package"):
            pkg_name = pkg.attrib.get("name", "").replace("/", ".")
            counters: Dict[str, Dict[str, int]] = {}
            for c in pkg.findall("counter"):
                c_type = c.attrib.get("type", "")
                missed = int(c.attrib.get("missed", 0))
                covered = int(c.attrib.get("covered", 0))
                counters[c_type] = {"missed": missed, "covered": covered}
            package_counters[pkg_name] = counters

        # 3. Aggregate critical subsystems
        critical_results: Dict[str, Any] = {}
        for sub_id, sub_info in CRITICAL_SUBSYSTEMS.items():
            tot_line_cov = 0
            tot_line_mis = 0
            tot_branch_cov = 0
            tot_branch_mis = 0
            matched_any = False

            for pkg_pattern in sub_info["packages"]:
                for pkg_name, counters in package_counters.items():
                    if pkg_name == pkg_pattern or pkg_name.startswith(pkg_pattern + "."):
                        matched_any = True
                        if "LINE" in counters:
                            tot_line_cov += counters["LINE"]["covered"]
                            tot_line_mis += counters["LINE"]["missed"]
                        if "BRANCH" in counters:
                            tot_branch_cov += counters["BRANCH"]["covered"]
                            tot_branch_mis += counters["BRANCH"]["missed"]

            sub_line_tot = tot_line_cov + tot_line_mis
            sub_branch_tot = tot_branch_cov + tot_branch_mis
            line_pct = (
                round((tot_line_cov / sub_line_tot) * 100.0, 2)
                if sub_line_tot > 0
                else None
            )
            branch_pct = (
                round((tot_branch_cov / sub_branch_tot) * 100.0, 2)
                if sub_branch_tot > 0
                else None
            )

            line_status = (
                "PASS"
                if line_pct is not None and line_pct >= sub_info["line_floor"]
                else ("FAIL" if line_pct is not None else "N/A")
            )
            branch_status = (
                "PASS"
                if branch_pct is not None and branch_pct >= sub_info["branch_floor"]
                else ("FAIL" if branch_pct is not None else "N/A")
            )

            critical_results[sub_id] = {
                "name": sub_info["name"],
                "packages": sub_info["packages"],
                "line_percent": line_pct,
                "line_floor": sub_info["line_floor"],
                "line_status": line_status,
                "branch_percent": branch_pct,
                "branch_floor": sub_info["branch_floor"],
                "branch_status": branch_status,
                "matched": matched_any,
            }

        return {
            "measured": True,
            "global": {
                "line_percent": global_line_pct,
                "branch_percent": global_branch_pct,
            },
            "critical_packages": critical_results,
        }

    except Exception as e:
        return {
            "measured": False,
            "reason": f"Failed to parse Kover XML report ({kover_xml_path}): {e}",
            "global": {"line_percent": None, "branch_percent": None},
            "critical_packages": {},
        }


def render_markdown_evidence(
    target_id: str,
    target_type: str,
    commit_sha: str,
    timestamp: str,
    test_summary: Dict[str, Any],
    tst_results: Dict[str, Any],
    coverage_summary: Dict[str, Any],
) -> str:
    """Renders structured Markdown test evidence deliverable (REQ-PRO-100.1)."""
    failures = test_summary.get("failures", 0)
    errors = test_summary.get("errors", 0)
    total = test_summary.get("total", 0)
    passed = test_summary.get("passed", 0)
    skipped = test_summary.get("skipped", 0)
    time_sec = test_summary.get("time", 0.0)

    overall_result = "PASS" if failures == 0 and errors == 0 and total > 0 else ("FAIL" if (failures > 0 or errors > 0) else "UNKNOWN")

    md = [
        f"# Test & Coverage Evidence: {target_id}",
        "",
        f"- **Target**: `{target_id}` (Type: `{target_type}`)",
        f"- **Git Commit**: `{commit_sha}`",
        f"- **Timestamp**: `{timestamp}`",
        f"- **Overall Test Result**: **{overall_result}** ({failures} failure(s), {errors} error(s))",
        "",
        "---",
        "",
        "## 1. Unit Test Metrics",
        f"- **Total Tests Executed**: {total:,}",
        f"- **Passed**: {passed:,}",
        f"- **Failures**: {failures:,}",
        f"- **Errors**: {errors:,}",
        f"- **Skipped**: {skipped:,}",
        f"- **Execution Duration**: {time_sec:.2f}s",
        "",
        "---",
        "",
        "## 2. Code Coverage Metrics (Kover)",
    ]

    if coverage_summary.get("measured", False):
        glob = coverage_summary.get("global", {})
        line_p = glob.get("line_percent")
        branch_p = glob.get("branch_percent")
        md.append(f"- **Status**: Measured")
        md.append(f"- **Global Line Coverage**: `{line_p}%`" if line_p is not None else "- **Global Line Coverage**: `N/A`")
        md.append(f"- **Global Branch Coverage**: `{branch_p}%`" if branch_p is not None else "- **Global Branch Coverage**: `N/A`")
        md.append("")
        md.append("### Critical Subsystem Coverage")
        md.append("| Subsystem | Line Coverage | Floor | Line Verdict | Branch Coverage | Floor | Branch Verdict |")
        md.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: |")
        for sub_id, sub in coverage_summary.get("critical_packages", {}).items():
            l_val = f"{sub['line_percent']}%" if sub['line_percent'] is not None else "N/A"
            b_val = f"{sub['branch_percent']}%" if sub['branch_percent'] is not None else "N/A"
            md.append(
                f"| **{sub['name']}** | {l_val} | {sub['line_floor']}% | {sub['line_status']} | "
                f"{b_val} | {sub['branch_floor']}% | {sub['branch_status']} |"
            )
    else:
        md.append(f"- **Status**: Not Measured ({coverage_summary.get('reason', 'N/A')})")

    md.extend([
        "",
        "---",
        "",
        "## 3. Evaluated TST Specifications",
        f"- **Total Correlated TSTs**: {len(tst_results):,}",
        "",
        "| TST ID | Status | Passing Cases | Failing Cases |",
        "| :--- | :---: | :---: | :---: |",
    ])

    sorted_tsts = sorted(tst_results.items())
    for tid, tdata in sorted_tsts[:50]:  # Show top 50 in MD, full list in JSON
        p_count = len(tdata.get("passing_cases", []))
        f_count = len(tdata.get("failing_cases", []))
        md.append(f"| `{tid}` | **{tdata.get('status', 'UNKNOWN')}** | {p_count} | {f_count} |")

    if len(sorted_tsts) > 50:
        md.append(f"\n*(Remaining {len(sorted_tsts) - 50} TST specifications recorded in companion JSON)*")

    md.append("")
    return "\n".join(md)


def should_skip_kover(
    target_id: str,
    target_type: str = "sprint",
    kover_xml_path: Optional[str] = None,
    skip_kover: bool = False,
) -> bool:
    """Skip Kover on 01_Früh (15:00) and 03_Nacht (05:00) unless explicitly forced via kover_xml_path."""
    if skip_kover:
        return True
    if kover_xml_path:
        return False
    if target_type == "sprint" and target_id:
        lowered = target_id.lower()
        if any(tag in lowered for tag in ("01_früh", "01_frueh", "03_nacht")):
            return True
    return False


def generate_test_evidence(
    target_id: str,
    target_type: str = "sprint",
    repo_root: Optional[str] = None,
    results_dir: Optional[str] = None,
    archive_path: Optional[str] = None,
    kover_xml_path: Optional[str] = None,
    out_dir: Optional[str] = None,
    commit_sha: Optional[str] = None,
    timestamp: Optional[str] = None,
    skip_kover: bool = False,
) -> Tuple[str, str, Dict[str, Any]]:
    """
    Main test evidence generator function (REQ-PRO-100).
    Produces:
      docs/engineering/test_evidence/<target_id>.md
      docs/engineering/test_evidence/<target_id>.json
    Returns (md_path, json_path, payload_dict).
    """
    root = repo_root or os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    commit = commit_sha or get_git_commit(root)
    ts = timestamp or datetime.datetime.now(datetime.timezone.utc).isoformat()
    output_directory = out_dir or os.path.join(root, "docs", "engineering", "test_evidence")
    os.makedirs(output_directory, exist_ok=True)

    # 1. Parse JUnit XML Results
    parsed_junit: Dict[str, Any] = {"summary": {}, "by_tst": {}}
    if parse_junit_xml_results:
        parsed_junit = parse_junit_xml_results(
            results_dir=results_dir,
            archive_path=archive_path,
            repo_root=root,
        )

    # Correlate with TST code tags if available
    code_tags = extract_test_code_tags(repo_root=root) if extract_test_code_tags else None
    if correlate_test_results_to_tsts:
        correlate_test_results_to_tsts(parsed_junit, code_tags=code_tags)

    test_summary = parsed_junit.get("summary", {
        "total": 0, "passed": 0, "failures": 0, "errors": 0, "skipped": 0, "time": 0.0
    })
    tst_results = parsed_junit.get("by_tst", {})

    # 2. Parse Kover XML Report (skipped on 01_Früh / 03_Nacht to avoid stale XML leaks)
    if should_skip_kover(target_id=target_id, target_type=target_type, kover_xml_path=kover_xml_path, skip_kover=skip_kover):
        coverage_summary = {
            "measured": False,
            "reason": "Kover skipped per Tier-2 policy (executed only at 02_Spät 22:00 cutoff and releases)",
            "global": {"line_percent": None, "branch_percent": None},
            "critical_packages": {},
        }
    else:
        effective_kover_xml = kover_xml_path or os.path.join(root, "app", "build", "reports", "kover", "reportDebug.xml")
        coverage_summary = parse_kover_xml_report(effective_kover_xml)

    # 3. Assemble JSON Payload
    tst_clean_results: Dict[str, Any] = {}
    for tid, tdata in tst_results.items():
        tst_clean_results[tid] = {
            "status": tdata.get("status", "UNKNOWN"),
            "passing_cases_count": len(tdata.get("passing_cases", [])),
            "failing_cases_count": len(tdata.get("failing_cases", [])),
            "failing_cases": [
                {"name": c.get("name"), "classname": c.get("classname"), "failure_message": c.get("failure_message")}
                for c in tdata.get("failing_cases", [])
            ],
        }

    payload = {
        "target": target_id,
        "type": target_type,
        "commit": commit,
        "timestamp": ts,
        "unit_tests": test_summary,
        "coverage": coverage_summary,
        "tst_results": tst_clean_results,
    }

    # 4. Write Files
    # Normalize filename: e.g. sprint/2026-46.8 -> sprint_2026-46.8
    file_stem = target_id.replace("/", "_")
    json_path = os.path.join(output_directory, f"{file_stem}.json")
    md_path = os.path.join(output_directory, f"{file_stem}.md")

    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)

    md_content = render_markdown_evidence(
        target_id=target_id,
        target_type=target_type,
        commit_sha=commit,
        timestamp=ts,
        test_summary=test_summary,
        tst_results=tst_results,
        coverage_summary=coverage_summary,
    )
    with open(md_path, "w", encoding="utf-8") as f:
        f.write(md_content)

    print(f"Generated test evidence files:")
    print(f"  Markdown: {md_path}")
    print(f"  JSON:     {json_path}")
    print(f"Summary: {test_summary.get('total', 0)} tests, {test_summary.get('failures', 0)} failures, Kover measured: {coverage_summary.get('measured')}")

    return md_path, json_path, payload


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate versioned test and coverage evidence per sprint or release (ASPICE SWE.4/SWE.5/SWE.6/SUP.8 / REQ-PRO-100)."
    )
    parser.add_argument(
        "--sprint",
        type=str,
        default=None,
        help="Sprint identifier or name (e.g. sprint_2026-46.8 or 2026-46.8).",
    )
    parser.add_argument(
        "--release",
        type=str,
        default=None,
        help="Release tag identifier (e.g. V5.0.0 or V5.0.0__2026111201).",
    )
    parser.add_argument(
        "--target",
        type=str,
        default=None,
        help="Generic target name if neither --sprint nor --release is provided.",
    )
    parser.add_argument(
        "--repo-root",
        type=str,
        default=None,
        help="Repository root directory.",
    )
    parser.add_argument(
        "--test-results-dir",
        type=str,
        default=None,
        help="Path to JUnit XML directory (default: app/build/test-results/**).",
    )
    parser.add_argument(
        "--test-archive-path",
        type=str,
        default=None,
        help="Path to JUnit zip archive.",
    )
    parser.add_argument(
        "--kover-xml",
        type=str,
        default=None,
        help="Path to Kover XML report (default: app/build/reports/kover/reportDebug.xml).",
    )
    parser.add_argument(
        "--no-kover",
        action="store_true",
        help="Skip reading Kover XML coverage (auto-skipped for 01_Früh and 03_Nacht sprints).",
    )
    parser.add_argument(
        "--out-dir",
        type=str,
        default=None,
        help="Output directory (default: docs/engineering/test_evidence).",
    )
    parser.add_argument(
        "--commit",
        type=str,
        default=None,
        help="Commit SHA (defaults to git rev-parse HEAD).",
    )
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)

    target_id = args.sprint or args.release or args.target
    if not target_id:
        print("Error: Must specify --sprint, --release, or --target.", file=sys.stderr)
        return 1

    target_type = "sprint" if args.sprint else ("release" if args.release else "generic")
    if target_type == "sprint" and not target_id.startswith("sprint_"):
        target_id = f"sprint_{target_id}"

    generate_test_evidence(
        target_id=target_id,
        target_type=target_type,
        repo_root=args.repo_root,
        results_dir=args.test_results_dir,
        archive_path=args.test_archive_path,
        kover_xml_path=args.kover_xml,
        out_dir=args.out_dir,
        commit_sha=args.commit,
        skip_kover=args.no_kover,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
