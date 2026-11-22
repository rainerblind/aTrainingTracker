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
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from typing import Any, Dict, List, Optional, Tuple

DEFAULT_MIN_TIER2_TESTS = 100

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

GROUP_B_MANUAL_NFRS = [
    {"req_id": "REQ-NFR-004", "tst_id": "TST-NFR-004", "name": "CPU Consumption Ceiling", "budget": "< 5.0% single-core"},
    {"req_id": "REQ-NFR-006", "tst_id": "TST-NFR-006", "name": "Tracking Battery Drain Ceiling", "budget": "<= 5.0%/h bg / <= 12.0%/h fg"},
    {"req_id": "REQ-NFR-012", "tst_id": "TST-NFR-012", "name": "Cockpit Frame Render Budget", "budget": "< 16.6 ms p95 (60 FPS)"},
    {"req_id": "REQ-NFR-015", "tst_id": "TST-NFR-015", "name": "Cold Start Startup Latency Budget", "budget": "TTID <= 1200 ms / TTFD <= 1500 ms"},
    {"req_id": "REQ-NFR-016", "tst_id": "TST-NFR-016", "name": "GNSS Horizontal Trajectory Accuracy", "budget": "p95 <= 5.0 m / RMSE <= 3.0 m"},
    {"req_id": "REQ-NFR-017", "tst_id": "TST-NFR-017", "name": "Production Crash-Free Session Stability", "budget": ">= 99.5% prod / >= 99.0% staging"},
    {"req_id": "REQ-NFR-018", "tst_id": "TST-NFR-018", "name": "Application Not Responding (ANR) Rate Ceiling", "budget": "<= 0.05% ANR / < 100 ms main thread"},
]


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


def _strip_md_cell(text: str) -> str:
    """Strips Markdown backticks and bold/italic markers from a table cell."""
    return text.strip().strip("*").strip("`").strip()


def _parse_manual_protocol_ledger(manual_protocol_path: Optional[str]) -> Tuple[bool, List[Dict[str, Any]]]:
    """
    Parses attested Group B manual protocol rows from nfr_manual_protocol_template.md.
    Never fabricates PASS/Verified status: reports MISSING PROTOCOL if file absent,
    or the actual table verdict (e.g. PENDING MANUAL, PASS, FAIL) per REQ-NFR row.
    """
    manual_exists = bool(manual_protocol_path and os.path.isfile(manual_protocol_path))
    parsed_rows: Dict[str, Dict[str, str]] = {}

    if manual_exists and manual_protocol_path:
        try:
            with open(manual_protocol_path, "r", encoding="utf-8") as f:
                for line in f:
                    stripped = line.strip()
                    if not stripped.startswith("|"):
                        continue
                    cells = [c.strip() for c in stripped.strip("|").split("|")]
                    if len(cells) < 8:
                        continue
                    req_match = re.search(r"REQ-NFR-\d{3}", cells[0])
                    if not req_match:
                        continue
                    req_id = req_match.group(0)
                    parsed_rows[req_id] = {
                        "device_build": _strip_md_cell(cells[3]),
                        "date": _strip_md_cell(cells[4]),
                        "operator": _strip_md_cell(cells[5]),
                        "measured_value": _strip_md_cell(cells[6]),
                        "status": _strip_md_cell(cells[7]) or "PENDING MANUAL",
                    }
        except OSError:
            manual_exists = False

    entries: List[Dict[str, Any]] = []
    for item in GROUP_B_MANUAL_NFRS:
        row = parsed_rows.get(item["req_id"])
        if not manual_exists:
            status = "MISSING PROTOCOL"
            device_build = "<MISSING>"
            date = "<MISSING>"
            operator = "<MISSING>"
            measured_val = "<NOT MEASURED>"
        elif row is None:
            status = "PENDING MANUAL"
            device_build = "<TBD>"
            date = "<TBD>"
            operator = "<TBD>"
            measured_val = "<NOT MEASURED>"
        else:
            status = row["status"]
            device_build = row["device_build"]
            date = row["date"]
            operator = row["operator"]
            measured_val = row["measured_value"]

        entries.append(
            {
                **item,
                "verification_method": "On-Device Manual",
                "device_build": device_build,
                "date": date,
                "operator": operator,
                "measured_value": measured_val,
                "status": status,
                "note": "MANUAL PROTOCOL — NOT EXECUTED IN JVM"
                if status in ("PENDING MANUAL", "MISSING PROTOCOL")
                else f"ATTESTED ON-DEVICE ({device_build}, {date})",
            }
        )
    return manual_exists, entries


def parse_nfr_measurements(
    nfr_json_path: str,
    manual_protocol_path: Optional[str] = None,
) -> Dict[str, Any]:
    """
    Parses measured JVM/Robolectric NFR benchmark results (nfr_measurements.json and/or
    atomic per-NFR REQ-NFR-*.json files) and parses Group B On-Device Manual protocol
    status from nfr_manual_protocol_template.md (REQ-PRO-113.3 / ATT-8158).
    """
    manual_exists, manual_entries = _parse_manual_protocol_ledger(manual_protocol_path)

    nfr_dir = os.path.dirname(nfr_json_path)
    per_nfr_files = (
        sorted(
            os.path.join(nfr_dir, fn)
            for fn in os.listdir(nfr_dir)
            if fn.startswith("REQ-NFR-") and fn.endswith(".json") and os.path.isfile(os.path.join(nfr_dir, fn))
        )
        if nfr_dir and os.path.isdir(nfr_dir)
        else []
    )

    if not os.path.isfile(nfr_json_path) and not per_nfr_files:
        return {
            "measured": False,
            "reason": f"NFR measurement report not found at {nfr_json_path}",
            "measured_count": 0,
            "passed_count": 0,
            "failed_count": 0,
            "measurements": [],
            "manual_protocol_present": manual_exists,
            "manual_protocols": manual_entries,
        }

    try:
        items_by_req: Dict[str, Dict[str, Any]] = {}
        suite_name = "MeasuredNfrBenchmarkTest"

        if os.path.isfile(nfr_json_path):
            with open(nfr_json_path, "r", encoding="utf-8") as f:
                raw = json.load(f)
            suite_name = raw.get("suite", suite_name)
            items = raw.get("measurements", [])
            if not isinstance(items, list):
                raise ValueError("'measurements' field must be a list")
            for entry in items:
                if isinstance(entry, dict) and entry.get("req_id"):
                    items_by_req[str(entry["req_id"])] = entry

        for per_path in per_nfr_files:
            with open(per_path, "r", encoding="utf-8") as pf:
                per_entry = json.load(pf)
            if isinstance(per_entry, dict) and per_entry.get("req_id"):
                items_by_req[str(per_entry["req_id"])] = per_entry

        cleaned: List[Dict[str, Any]] = []
        passed_count = 0
        failed_count = 0
        for req_key in sorted(items_by_req.keys()):
            entry = items_by_req[req_key]
            passed = bool(entry.get("passed", False))
            if passed:
                passed_count += 1
            else:
                failed_count += 1
            cleaned.append({
                "req_id": entry.get("req_id", ""),
                "tst_id": entry.get("tst_id", ""),
                "name": entry.get("name", ""),
                "metric": entry.get("metric", ""),
                "measured_value": entry.get("measured_value"),
                "unit": entry.get("unit", ""),
                "budget": entry.get("budget", ""),
                "passed": passed,
                "warmup_iterations": entry.get("warmup_iterations", 0),
                "measured_iterations": entry.get("measured_iterations", 0),
                "details": entry.get("details", ""),
            })

        return {
            "measured": len(cleaned) > 0,
            "suite": suite_name,
            "measured_count": len(cleaned),
            "passed_count": passed_count,
            "failed_count": failed_count,
            "measurements": cleaned,
            "manual_protocol_present": manual_exists,
            "manual_protocols": manual_entries,
        }
    except Exception as e:
        return {
            "measured": False,
            "reason": f"Failed to parse NFR measurement report ({nfr_json_path}): {e}",
            "measured_count": 0,
            "passed_count": 0,
            "failed_count": 0,
            "measurements": [],
            "manual_protocol_present": manual_exists,
            "manual_protocols": manual_entries,
        }


def render_markdown_evidence(
    target_id: str,
    target_type: str,
    commit_sha: str,
    timestamp: str,
    test_summary: Dict[str, Any],
    tst_results: Dict[str, Any],
    coverage_summary: Dict[str, Any],
    nfr_summary: Optional[Dict[str, Any]] = None,
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

    if nfr_summary is not None:
        md.extend([
            "",
            "---",
            "",
            "## 4. Non-Functional Requirements (NFR) Verification Summary",
        ])
        if nfr_summary.get("measured", False):
            md.append(
                f"- **Group A (JVM/Robolectric Measured)**: {nfr_summary.get('passed_count', 0)}/"
                f"{nfr_summary.get('measured_count', 0)} passed "
                f"({nfr_summary.get('failed_count', 0)} failed)"
            )
            md.append("")
            md.append("| REQ ID | TST ID | Metric | Measured Value | Budget | Verdict |")
            md.append("| :--- | :--- | :--- | :---: | :---: | :---: |")
            for item in nfr_summary.get("measurements", []):
                verdict = "PASS" if item.get("passed") else "FAIL"
                val = item.get("measured_value")
                unit = item.get("unit", "")
                val_str = f"`{val:.4f} {unit}`" if isinstance(val, (int, float)) else f"`{val} {unit}`"
                md.append(
                    f"| `{item.get('req_id')}` | `{item.get('tst_id')}` | `{item.get('metric')}` | "
                    f"{val_str} | `{item.get('budget')}` | **{verdict}** |"
                )
        else:
            md.append(
                f"- **Group A (JVM/Robolectric Measured)**: Not Measured ({nfr_summary.get('reason', 'N/A')})"
            )

        manual_items = nfr_summary.get("manual_protocols", [])
        if manual_items:
            md.extend([
                "",
                "### Group B — On-Device Manual Protocols (`[MANUAL PROTOCOL — NOT EXECUTED IN JVM]`)",
                "| REQ ID | TST ID | Budget | Method | Device / Build | Measured | Verdict |",
                "| :--- | :--- | :---: | :--- | :--- | :---: | :---: |",
            ])
            for m in manual_items:
                md.append(
                    f"| `{m.get('req_id')}` | `{m.get('tst_id')}` | `{m.get('budget')}` | "
                    f"{m.get('verification_method')} | `{m.get('device_build', '<TBD>')}` | "
                    f"`{m.get('measured_value', '<NOT MEASURED>')}` | **{m.get('status')}** |"
                )

    md.append("")
    return "\n".join(md)


def should_skip_kover(
    target_id: str,
    target_type: str = "sprint",
    kover_xml_path: Optional[str] = None,
    skip_kover: bool = False,
) -> bool:
    """Skip Kover for regular Schicht sprints; Kover runs in weekly Code Review / ASPICE Audit and releases."""
    if skip_kover:
        return True
    if kover_xml_path:
        return False
    if target_type == "sprint" and target_id:
        lowered = target_id.lower()
        if any(tag in lowered for tag in ("01_früh", "01_frueh", "02_spät", "02_spaet", "03_nacht")):
            return True
    return False


def snapshot_junit_results(source_dir: str, snapshot_dir: str) -> int:
    """Copies all JUnit TEST-*.xml files from source_dir into snapshot_dir before targeted re-runs (ATT-9497)."""
    if not source_dir or not os.path.isdir(source_dir):
        raise ValueError(f"JUnit source directory does not exist: {source_dir}")
    os.makedirs(snapshot_dir, exist_ok=True)
    copied = 0
    for dirpath, _, filenames in os.walk(source_dir):
        for fname in filenames:
            if fname.startswith("TEST-") and fname.endswith(".xml"):
                src = os.path.join(dirpath, fname)
                rel = os.path.relpath(src, source_dir)
                dst = os.path.join(snapshot_dir, rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy2(src, dst)
                copied += 1
    return copied


def merge_junit_xml_directories(source_dirs: List[str], staging_dir: str) -> int:
    """
    Merges JUnit TEST-*.xml files across multiple directories in chronological order into staging_dir (ATT-9497).
    Later directories (e.g. targeted re-runs) update only matching suite filenames while preserving all other
    suites from earlier full Tier-2 snapshots.
    """
    os.makedirs(staging_dir, exist_ok=True)
    merged_by_name: Dict[str, str] = {}
    for sdir in source_dirs:
        if not sdir or not os.path.isdir(sdir):
            continue
        for dirpath, _, filenames in os.walk(sdir):
            for fname in sorted(filenames):
                if fname.startswith("TEST-") and fname.endswith(".xml"):
                    merged_by_name[fname] = os.path.join(dirpath, fname)
    for fname, src_path in sorted(merged_by_name.items()):
        shutil.copy2(src_path, os.path.join(staging_dir, fname))
    return len(merged_by_name)


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
    nfr_json_path: Optional[str] = None,
    merge_results_dirs: Optional[List[str]] = None,
    min_tests: Optional[int] = None,
    allow_failure: bool = False,
) -> Tuple[str, str, Dict[str, Any]]:
    """
    Main test evidence generator function (REQ-PRO-100 / ATT-9497).
    Produces:
      docs/engineering/test_evidence/<target_id>.md
      docs/engineering/test_evidence/<target_id>.json
    Returns (md_path, json_path, payload_dict).
    Raises ValueError if total tests < min_tests or failures/errors > 0 without allow_failure=True.
    """
    root = repo_root or os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    commit = commit_sha or get_git_commit(root)
    ts = timestamp or datetime.datetime.now(datetime.timezone.utc).isoformat()
    output_directory = out_dir or os.path.join(root, "docs", "engineering", "test_evidence")
    os.makedirs(output_directory, exist_ok=True)

    # 1. Parse JUnit XML Results (optionally merging preserved full Tier-2 snapshots + targeted re-runs)
    parsed_junit: Dict[str, Any] = {"summary": {}, "by_tst": {}}
    temp_merge_dir: Optional[str] = None
    effective_results_dir = results_dir
    try:
        if merge_results_dirs:
            ordered_dirs: List[str] = list(merge_results_dirs)
            default_dir = results_dir or os.path.join(root, "app", "build", "test-results")
            if default_dir and os.path.isdir(default_dir) and default_dir not in ordered_dirs:
                ordered_dirs.append(default_dir)
            temp_merge_dir = tempfile.mkdtemp(prefix="att_junit_merge_")
            merge_junit_xml_directories(ordered_dirs, temp_merge_dir)
            effective_results_dir = temp_merge_dir

        if parse_junit_xml_results:
            parsed_junit = parse_junit_xml_results(
                results_dir=effective_results_dir,
                archive_path=archive_path,
                repo_root=root,
            )
    finally:
        if temp_merge_dir and os.path.isdir(temp_merge_dir):
            shutil.rmtree(temp_merge_dir, ignore_errors=True)

    # Correlate with TST code tags if available
    code_tags = extract_test_code_tags(repo_root=root) if extract_test_code_tags else None
    if correlate_test_results_to_tsts:
        correlate_test_results_to_tsts(parsed_junit, code_tags=code_tags)

    test_summary = parsed_junit.get("summary", {
        "total": 0, "passed": 0, "failures": 0, "errors": 0, "skipped": 0, "time": 0.0
    })
    tst_results = parsed_junit.get("by_tst", {})

    # ATT-9497 / Finding K3 Guardrails: Refuse truncated/overwritten or failing evidence unless overridden
    total_tests = int(test_summary.get("total", 0) or 0)
    total_failures = int(test_summary.get("failures", 0) or 0)
    total_errors = int(test_summary.get("errors", 0) or 0)

    if min_tests is not None and min_tests > 0 and total_tests < min_tests:
        raise ValueError(
            f"Refusing to archive test evidence for '{target_id}': executed only {total_tests} test(s), "
            f"below minimum Tier-2 threshold ({min_tests}) (ATT-9497 / Finding K3). "
            "Preserve full Tier-2 XMLs with --snapshot-to / --merge-results-dir before targeted re-runs, "
            "or pass --min-tests 0 only for intentional synthetic runs."
        )
    if (total_failures > 0 or total_errors > 0) and not allow_failure:
        raise ValueError(
            f"Refusing to archive failing test evidence for '{target_id}': "
            f"{total_failures} failure(s), {total_errors} error(s) (ATT-9497 / Finding K3). "
            "Fix failing tests first or pass --allow-failure to record an explicit deviation."
        )

    # 2. Parse Kover XML Report (skipped on regular Schichten; run in weekly reviews/audits & releases)
    if should_skip_kover(target_id=target_id, target_type=target_type, kover_xml_path=kover_xml_path, skip_kover=skip_kover):
        coverage_summary = {
            "measured": False,
            "reason": "Kover skipped for regular Schicht (executed in weekly Code Review / ASPICE Audit and releases)",
            "global": {"line_percent": None, "branch_percent": None},
            "critical_packages": {},
        }
    else:
        effective_kover_xml = kover_xml_path or os.path.join(root, "app", "build", "reports", "kover", "reportDebug.xml")
        coverage_summary = parse_kover_xml_report(effective_kover_xml)

    # 2b. Parse NFR Benchmark Measurements & Manual Protocol Status (REQ-PRO-113.3)
    effective_nfr_json = nfr_json_path or os.path.join(root, "app", "build", "reports", "nfr", "nfr_measurements.json")
    manual_protocol_path = os.path.join(root, "docs", "engineering", "test_evidence", "nfr_manual_protocol_template.md")
    nfr_summary = parse_nfr_measurements(effective_nfr_json, manual_protocol_path=manual_protocol_path)

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
        "nfr_verification": nfr_summary,
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
        nfr_summary=nfr_summary,
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
        help="Skip reading Kover XML coverage (auto-skipped for regular Schicht sprints).",
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
    parser.add_argument(
        "--nfr-json",
        type=str,
        default=None,
        help="Path to measured NFR benchmark JSON (default: app/build/reports/nfr/nfr_measurements.json).",
    )
    parser.add_argument(
        "--snapshot-to",
        type=str,
        default=None,
        help="Snapshot current JUnit TEST-*.xml files into this directory before targeted re-runs and exit (ATT-9497).",
    )
    parser.add_argument(
        "--merge-results-dir",
        action="append",
        default=None,
        help="Additional JUnit XML snapshot directory to merge chronologically before current results (repeatable, ATT-9497).",
    )
    parser.add_argument(
        "--min-tests",
        type=int,
        default=None,
        help=f"Minimum executed test count required to archive sprint/release evidence (default: {DEFAULT_MIN_TIER2_TESTS} for sprint/release, 0 disables).",
    )
    parser.add_argument(
        "--allow-failure",
        action="store_true",
        help="Explicit deviation flag allowing archival when test failures or errors > 0 (ATT-9497).",
    )
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)

    if args.snapshot_to:
        root = args.repo_root or os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
        src_dir = args.test_results_dir or os.path.join(root, "app", "build", "test-results")
        try:
            count = snapshot_junit_results(src_dir, args.snapshot_to)
            print(f"Snapshotted {count} JUnit XML suite file(s) from {src_dir} to {args.snapshot_to}.")
            return 0
        except ValueError as err:
            print(f"Error: {err}", file=sys.stderr)
            return 1

    target_id = args.sprint or args.release or args.target
    if not target_id:
        print("Error: Must specify --sprint, --release, or --target.", file=sys.stderr)
        return 1

    target_type = "sprint" if args.sprint else ("release" if args.release else "generic")
    if target_type == "sprint" and not target_id.startswith("sprint_"):
        target_id = f"sprint_{target_id}"

    effective_min_tests = args.min_tests
    if effective_min_tests is None and target_type in ("sprint", "release"):
        effective_min_tests = DEFAULT_MIN_TIER2_TESTS

    try:
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
            nfr_json_path=args.nfr_json,
            merge_results_dirs=args.merge_results_dir,
            min_tests=effective_min_tests,
            allow_failure=args.allow_failure,
        )
    except ValueError as err:
        print(f"Error: {err}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
