#!/usr/bin/env python3
"""
tools/generate_release_verification_summary.py
ASPICE SWE.6 BP5 / SUP.8 / REQ-PRO-074 / TST-PRO-074 (Remediation ID: A-11)

Generates an authoritative, baseline-controlled release verification summary
Markdown file per release candidate tag (docs/releases/<tag>/verification_summary.md).

Aggregates:
1. Unit & regression test count, pass/fail status, execution time, and link to
   versioned JUnit test archive (per ATT-6137).
2. Bidirectional requirement-to-test coverage matrix via
   tools/verify_requirement_governance.py --coverage-json (per ATT-6122).
3. SYS.4 System Integration Verification results from
   docs/releases/<tag>/system_integration_results.json (per ATT-6166).
4. SYS.5 System Verification Protocol sign-off record status from
   docs/releases/<tag>/sys_verification_record.md (per ATT-6167).
5. Open deviations section explicitly enumerating any failing test, sub-threshold
   coverage, or missing system evidence under "Deviations Requiring Human Approval".
"""

import argparse
import datetime
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from typing import Any, Dict, List, Optional, Tuple

try:
    from verify_requirement_governance import (
        calculate_traceability_coverage,
        verify_coverage_thresholds,
    )
except ImportError:
    try:
        from tools.verify_requirement_governance import (
            calculate_traceability_coverage,
            verify_coverage_thresholds,
        )
    except ImportError:
        calculate_traceability_coverage = None  # type: ignore
        verify_coverage_thresholds = None  # type: ignore



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


def collect_unit_test_summary(
    repo_root: str,
    tag: str,
    results_dir: Optional[str] = None,
    archive_path: Optional[str] = None,
) -> Dict[str, Any]:
    """
    Parses JUnit XML files or versioned zip archive to extract unit test metrics.
    """
    effective_results_dir = results_dir or os.path.join(
        repo_root, "app", "build", "test-results", "testDebugUnitTest"
    )
    effective_archive = archive_path or os.path.join(
        repo_root, "build-evidence", f"junit-test-results-{tag}.zip"
    )

    total_tests = 0
    failures = 0
    errors = 0
    skipped = 0
    total_time = 0.0
    failed_cases: List[Dict[str, str]] = []
    xml_sources_found = False

    # 1. Try parsing directly from test-results directory
    if os.path.isdir(effective_results_dir):
        xml_files = [
            os.path.join(effective_results_dir, f)
            for f in os.listdir(effective_results_dir)
            if f.endswith(".xml")
        ]
        if xml_files:
            xml_sources_found = True
            for xf in xml_files:
                try:
                    tree = ET.parse(xf)
                    root = tree.getroot()
                    suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
                    for suite in suites:
                        total_tests += int(suite.attrib.get("tests", 0))
                        failures += int(suite.attrib.get("failures", 0))
                        errors += int(suite.attrib.get("errors", 0))
                        skipped += int(suite.attrib.get("skipped", 0))
                        total_time += float(suite.attrib.get("time", 0.0))

                        for tc in suite.findall("testcase"):
                            failure_elem = tc.find("failure")
                            error_elem = tc.find("error")
                            if failure_elem is not None or error_elem is not None:
                                elem = failure_elem if failure_elem is not None else error_elem
                                failed_cases.append(
                                    {
                                        "classname": tc.attrib.get("classname", "UnknownClass"),
                                        "name": tc.attrib.get("name", "UnknownTest"),
                                        "message": elem.attrib.get("message", "Test assertion failed") if elem is not None else "",
                                    }
                                )
                except Exception as e:
                    print(f"Warning: Failed to parse XML file {xf}: {e}", file=sys.stderr)

    # 2. If no directory XMLs found, check versioned zip archive
    if not xml_sources_found and os.path.isfile(effective_archive):
        try:
            with zipfile.ZipFile(effective_archive, "r") as z:
                xml_names = [n for n in z.namelist() if n.endswith(".xml")]
                if xml_names:
                    xml_sources_found = True
                    for xn in xml_names:
                        with z.open(xn) as zf:
                            tree = ET.parse(zf)
                            root = tree.getroot()
                            suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
                            for suite in suites:
                                total_tests += int(suite.attrib.get("tests", 0))
                                failures += int(suite.attrib.get("failures", 0))
                                errors += int(suite.attrib.get("errors", 0))
                                skipped += int(suite.attrib.get("skipped", 0))
                                total_time += float(suite.attrib.get("time", 0.0))

                                for tc in suite.findall("testcase"):
                                    failure_elem = tc.find("failure")
                                    error_elem = tc.find("error")
                                    if failure_elem is not None or error_elem is not None:
                                        elem = failure_elem if failure_elem is not None else error_elem
                                        failed_cases.append(
                                            {
                                                "classname": tc.attrib.get("classname", "UnknownClass"),
                                                "name": tc.attrib.get("name", "UnknownTest"),
                                                "message": elem.attrib.get("message", "Test assertion failed") if elem is not None else "",
                                            }
                                        )
        except Exception as e:
            print(f"Warning: Failed to read archive {effective_archive}: {e}", file=sys.stderr)

    archive_exists = os.path.isfile(effective_archive)
    passed_tests = max(0, total_tests - (failures + errors + skipped))

    deviations: List[str] = []
    if not xml_sources_found:
        deviations.append(
            f"Unit test evidence missing: No JUnit test reports found in {os.path.relpath(effective_results_dir, repo_root)} or {os.path.relpath(effective_archive, repo_root)}."
        )
    elif (failures + errors) > 0:
        deviations.append(
            f"Unit test failures: {failures + errors} test(s) failed out of {total_tests} executed."
        )

    return {
        "available": xml_sources_found,
        "total_tests": total_tests,
        "passed_tests": passed_tests,
        "failed_tests": failures + errors,
        "skipped_tests": skipped,
        "execution_time_sec": round(total_time, 2),
        "failed_cases": failed_cases,
        "archive_path": os.path.relpath(effective_archive, repo_root),
        "archive_exists": archive_exists,
        "deviations": deviations,
    }


def collect_coverage_summary(
    repo_root: str,
    min_req_tst_coverage: Optional[float] = None,
    min_tst_code_coverage: Optional[float] = None,
    min_bidirectional_coverage: Optional[float] = None,
    max_code_without_spec: Optional[int] = None,
) -> Dict[str, Any]:
    """
    Invokes or imports verify_requirement_governance.py to extract coverage JSON.
    """
    report: Dict[str, Any] = {}
    gate_ok = True
    diag: List[str] = []

    invoked_directly = False
    if calculate_traceability_coverage is not None and verify_coverage_thresholds is not None:
        try:
            report = calculate_traceability_coverage(repo_root=repo_root)
            gate_ok, diag = verify_coverage_thresholds(
                report,
                min_req_tst_coverage=min_req_tst_coverage,
                min_tst_code_coverage=min_tst_code_coverage,
                max_code_without_spec=max_code_without_spec,
                min_bidirectional_coverage=min_bidirectional_coverage,
            )
            invoked_directly = True
        except Exception:
            invoked_directly = False

    if not invoked_directly:
        try:
            cmd = [
                sys.executable,
                os.path.join(repo_root, "tools", "verify_requirement_governance.py"),
                "--coverage-json",
            ]
            if min_req_tst_coverage is not None:
                cmd.extend(["--min-req-tst-coverage", str(min_req_tst_coverage)])
            if min_tst_code_coverage is not None:
                cmd.extend(["--min-tst-code-coverage", str(min_tst_code_coverage)])
            if min_bidirectional_coverage is not None:
                cmd.extend(["--min-bidirectional-coverage", str(min_bidirectional_coverage)])
            if max_code_without_spec is not None:
                cmd.extend(["--max-code-without-spec", str(max_code_without_spec)])

            proc = subprocess.run(cmd, cwd=repo_root, capture_output=True, text=True, check=True)
            report = json.loads(proc.stdout)
            gate_ok = report.get("gate_result", {}).get("passed", True)
            diag = report.get("gate_result", {}).get("diagnostics", [])
        except Exception as sub_err:
            return {
                "available": False,
                "error": f"Failed to compute coverage report: {sub_err}",
                "deviations": [f"Traceability coverage calculation error: {sub_err}"],
            }

    deviations: List[str] = []
    if not gate_ok:
        for d in diag:
            if d.startswith("FAIL:"):
                deviations.append(f"Traceability threshold violation: {d}")

    summary = report.get("summary", {})
    return {
        "available": True,
        "summary": summary,
        "categories": {
            "cat1": report.get("category_1_reqs_without_tst", {}),
            "cat2": report.get("category_2_tsts_without_code", {}),
            "cat3": report.get("category_3_code_without_spec", {}),
            "cat4": report.get("category_4_tst_without_req_citation", {}),
        },
        "gate_passed": gate_ok,
        "diagnostics": diag,
        "deviations": deviations,
    }


def collect_sys4_summary(repo_root: str, tag: str) -> Dict[str, Any]:
    """
    Parses docs/releases/<tag>/system_integration_results.json (ASPICE SYS.4).
    """
    sys4_rel_path = os.path.join("docs", "releases", tag, "system_integration_results.json")
    sys4_path = os.path.join(repo_root, sys4_rel_path)

    if not os.path.isfile(sys4_path):
        return {
            "available": False,
            "path": sys4_rel_path,
            "deviations": [
                f"SYS.4 evidence missing: '{sys4_rel_path}' does not exist (mandatory gate per REQ-PRO-072)."
            ],
        }

    try:
        with open(sys4_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        return {
            "available": False,
            "path": sys4_rel_path,
            "deviations": [f"SYS.4 evidence corrupted: Could not parse '{sys4_rel_path}': {e}"],
        }

    overall_status = data.get("overall_status", "UNKNOWN")
    total_cases = data.get("total_test_cases", 0)
    passed_cases = data.get("passed_test_cases", 0)
    failed_cases = data.get("failed_test_cases", 0)
    test_results = data.get("test_results", {})
    timestamp = data.get("timestamp", "UNKNOWN")
    git_commit = data.get("git_commit", "UNKNOWN")

    deviations: List[str] = []
    if overall_status != "PASS":
        deviations.append(
            f"SYS.4 verification non-pass: Overall status is '{overall_status}' with {failed_cases} failed test cases."
        )
    if total_cases < 6:
        deviations.append(
            f"SYS.4 suite incomplete: Expected at least 6 test cases (SYS-INT-001..006), found {total_cases}."
        )

    for case_id, res in test_results.items():
        if not res.get("passed", False):
            deviations.append(
                f"SYS.4 test failure: {case_id} ('{res.get('name', '')}') failed: {res.get('details', '')}"
            )

    return {
        "available": True,
        "path": sys4_rel_path,
        "overall_status": overall_status,
        "total_cases": total_cases,
        "passed_cases": passed_cases,
        "failed_cases": failed_cases,
        "timestamp": timestamp,
        "git_commit": git_commit,
        "test_results": test_results,
        "deviations": deviations,
    }


def collect_sys5_summary(repo_root: str, tag: str) -> Dict[str, Any]:
    """
    Parses docs/releases/<tag>/sys_verification_record.md (ASPICE SYS.5).
    """
    sys5_rel_path = os.path.join("docs", "releases", tag, "sys_verification_record.md")
    sys5_path = os.path.join(repo_root, sys5_rel_path)

    if not os.path.isfile(sys5_path):
        return {
            "available": False,
            "path": sys5_rel_path,
            "deviations": [
                f"SYS.5 evidence missing: '{sys5_rel_path}' does not exist (mandatory gate per REQ-PRO-072)."
            ],
        }

    try:
        with open(sys5_path, "r", encoding="utf-8") as f:
            content = f.read()
    except Exception as e:
        return {
            "available": False,
            "path": sys5_rel_path,
            "deviations": [f"SYS.5 evidence read error: Could not read '{sys5_rel_path}': {e}"],
        }

    deviations: List[str] = []

    # 1. Parse Overall Verdict
    verdict_match = re.search(r"\*\*Overall Verdict\*\*:\s*(\*\*\[.*?\]\*\*|\*\*.*?\*\*|\[.*?\]|[A-Z]+)", content)
    overall_verdict = "UNKNOWN"
    if verdict_match:
        raw_verdict = verdict_match.group(1).replace("*", "").strip()
        overall_verdict = raw_verdict

    if overall_verdict != "PASS":
        if "[ PASS / REVISION / FAIL ]" in content or "REVISION" in overall_verdict:
            deviations.append(
                f"SYS.5 overall verdict unapproved: Found '{overall_verdict}' in {sys5_rel_path} (must be 'PASS')."
            )
        else:
            deviations.append(
                f"SYS.5 overall verdict not PASS: Found '{overall_verdict}' in {sys5_rel_path}."
            )

    # 2. Parse Verification Test Results Matrix (SYS-VRF-001..008)
    cases_found: Dict[str, str] = {}
    for line in content.splitlines():
        line = line.strip()
        if line.startswith("|") and "`SYS-VRF-" in line:
            parts = [p.strip() for p in line.split("|")]
            # Table format: | Test ID | Title | Traced Requirements | Environment | Result | Notes |
            if len(parts) >= 6:
                id_match = re.search(r"`(SYS-VRF-\d+)`", parts[1])
                if id_match:
                    case_id = id_match.group(1)
                    raw_res = parts[5].replace("*", "").strip()
                    cases_found[case_id] = raw_res

    expected_cases = [f"SYS-VRF-00{i}" for i in range(1, 9)]
    for ec in expected_cases:
        if ec not in cases_found:
            deviations.append(f"SYS.5 test case missing: '{ec}' not found in verification matrix.")
        else:
            res = cases_found[ec]
            if res != "PASS":
                deviations.append(f"SYS.5 test case non-pass: '{ec}' has result '{res}' (expected PASS).")

    # 3. Check Formal Sign-Off
    has_signoff = False
    signoff_lines = [
        l for l in content.splitlines()
        if "System Verification Engineer" in l or "Quality Gatekeeper" in l
    ]
    if len(signoff_lines) >= 2:
        # Check if placeholders exist
        has_blank_placeholder = any("______" in l for l in signoff_lines)
        if has_blank_placeholder:
            deviations.append("SYS.5 formal sign-off incomplete: Protocol contains blank signature/date placeholders.")
        else:
            has_signoff = True
    else:
        deviations.append("SYS.5 formal sign-off section missing or incomplete.")

    return {
        "available": True,
        "path": sys5_rel_path,
        "overall_verdict": overall_verdict,
        "cases_found": cases_found,
        "has_signoff": has_signoff,
        "deviations": deviations,
    }


def compile_deviations(
    unit_summary: Dict[str, Any],
    coverage_summary: Dict[str, Any],
    sys4_summary: Dict[str, Any],
    sys5_summary: Dict[str, Any],
) -> Tuple[List[str], str]:
    """
    Compiles all open deviations requiring human approval across the 4 verification domains.
    Returns (deviations_list, overall_release_status).
    """
    all_deviations: List[str] = []
    all_deviations.extend(unit_summary.get("deviations", []))
    all_deviations.extend(coverage_summary.get("deviations", []))
    all_deviations.extend(sys4_summary.get("deviations", []))
    all_deviations.extend(sys5_summary.get("deviations", []))

    if not all_deviations:
        status = "PASS (READY FOR RELEASE)"
    else:
        status = "BLOCKED / REQUIRES HUMAN APPROVAL"

    return all_deviations, status


def render_markdown_summary(
    tag: str,
    commit_sha: str,
    unit_summary: Dict[str, Any],
    coverage_summary: Dict[str, Any],
    sys4_summary: Dict[str, Any],
    sys5_summary: Dict[str, Any],
    deviations: List[str],
    overall_status: str,
) -> str:
    """
    Renders the consolidated Markdown verification summary document.
    """
    now_str = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    # Determine gate badges
    unit_gate = "PASS" if not unit_summary.get("deviations") and unit_summary.get("available") else "FAIL"
    cov_gate = "PASS" if not coverage_summary.get("deviations") and coverage_summary.get("available") else "FAIL"
    sys4_gate = "PASS" if not sys4_summary.get("deviations") and sys4_summary.get("available") else "FAIL"
    sys5_gate = "PASS" if not sys5_summary.get("deviations") and sys5_summary.get("available") else "FAIL"

    # Coverage stats
    cov_stats = coverage_summary.get("summary", {})
    req_tst_pct = cov_stats.get("req_tst_coverage_pct", 0.0)
    tst_code_pct = cov_stats.get("tst_code_coverage_pct", 0.0)
    bidi_pct = cov_stats.get("bidirectional_coverage_pct", 0.0)

    lines = [
        f"# Release Verification Summary: Release {tag} (ASPICE SWE.6 BP5, SUP.8)",
        "",
        f"**Release Tag**: `{tag}`  ",
        f"**Baseline Commit**: `{commit_sha}`  ",
        f"**Generation Timestamp**: `{now_str}`  ",
        f"**Overall Verification Status**: **{overall_status}**  ",
        "",
        "---",
        "",
        "## 1. Executive Verification Dashboard & Gating Matrix",
        "",
        "In accordance with **ASPICE SWE.6 BP5** (Work Product Verification), **SUP.8** (Configuration & Release Management), and **REQ-PRO-074**, release qualification requires recorded, verifiable evidence across unit, traceability, and system verification domains prior to candidate tagging and Google Play Store distribution.",
        "",
        "| Verification Dimension | Governed Standard | Evidence Artifact / Source | Status / Metric | Gating Verdict |",
        "| :--- | :--- | :--- | :--- | :--- |",
        f"| **Unit & Regression Tests** | ASPICE SWE.6 BP3, BP4 | `{unit_summary.get('archive_path', 'build-evidence/junit-test-results-' + tag + '.zip')}` | {unit_summary.get('passed_tests', 0)}/{unit_summary.get('total_tests', 0)} passed ({unit_summary.get('failed_tests', 0)} failed) | [ {unit_gate} ] |",
        f"| **Traceability Coverage** | ASPICE SWE.1, SWE.6 | `tools/verify_requirement_governance.py` | REQ-TST: {req_tst_pct:.1f}%, TST-Code: {tst_code_pct:.1f}% | [ {cov_gate} ] |",
        f"| **System Integration (SYS.4)** | ASPICE SYS.4 BP5 | `{sys4_summary.get('path', 'docs/releases/' + tag + '/system_integration_results.json')}` | {sys4_summary.get('passed_cases', 0)}/{sys4_summary.get('total_cases', 0)} tests PASS | [ {sys4_gate} ] |",
        f"| **System Protocol (SYS.5)** | ASPICE SYS.5 BP5, BP6 | `{sys5_summary.get('path', 'docs/releases/' + tag + '/sys_verification_record.md')}` | Verdict: {sys5_summary.get('overall_verdict', 'UNKNOWN')}, Sign-off: {'Signed' if sys5_summary.get('has_signoff') else 'Incomplete'} | [ {sys5_gate} ] |",
        "",
        "---",
        "",
        "## 2. Unit & Regression Testing Evidence (SWE.6 BP3, BP4 / ATT-6137)",
        "",
    ]

    if unit_summary.get("available"):
        lines.extend([
            f"* **Total Tests Executed**: `{unit_summary.get('total_tests', 0)}`",
            f"* **Passed**: `{unit_summary.get('passed_tests', 0)}`",
            f"* **Failed / Errors**: `{unit_summary.get('failed_tests', 0)}`",
            f"* **Skipped**: `{unit_summary.get('skipped_tests', 0)}`",
            f"* **Execution Duration**: `{unit_summary.get('execution_time_sec', 0.0)} s`",
            f"* **Versioned JUnit Archive**: [`{unit_summary.get('archive_path')}`](../../../{unit_summary.get('archive_path')}) (Archived per ATT-6137)",
        ])
        if unit_summary.get("failed_cases"):
            lines.extend(["", "### Failed Test Cases:"])
            for fc in unit_summary["failed_cases"]:
                lines.append(f"* `{fc['classname']}.{fc['name']}`: {fc['message']}")
    else:
        lines.append(f"> [!WARNING]\n> **Missing Evidence**: Unit test execution reports were not found in expected directory or archive.")

    lines.extend([
        "",
        "---",
        "",
        "## 3. Bidirectional Requirement & Test Traceability Matrix (SWE.1, SWE.6 / ATT-6122)",
        "",
    ])

    if coverage_summary.get("available"):
        lines.extend([
            "| Category | Traceability Dimension | Covered / Total | Coverage % | Status |",
            "| :--- | :--- | :--- | :--- | :--- |",
            f"| **Category 1** | REQs citing at least one TST in living docs | {cov_stats.get('reqs_with_tst', 0)} / {cov_stats.get('total_reqs', 0)} | {req_tst_pct:.2f}% | {'PASS' if req_tst_pct >= 40.0 else 'DEFICIT'} |",
            f"| **Category 2** | TSTs backed by executable test code | {cov_stats.get('tsts_with_code', 0)} / {cov_stats.get('total_tsts', 0)} | {tst_code_pct:.2f}% | {'PASS' if tst_code_pct >= 50.0 else 'DEFICIT'} |",
            f"| **Category 3** | Code test tags matching living doc specification | {cov_stats.get('code_tags_with_spec', 0)} / {cov_stats.get('total_code_tsts', 0)} | {cov_stats.get('code_spec_match_pct', 0.0):.2f}% | {'PASS' if cov_stats.get('code_tags_without_spec', 0) == 0 else 'DEFICIT'} |",
            f"| **Category 4** | Bidirectional citations (TST cited back by REQ) | {cov_stats.get('tsts_cited_back', 0)} / {cov_stats.get('total_tsts', 0)} | {bidi_pct:.2f}% | {'PASS' if bidi_pct >= 30.0 else 'DEFICIT'} |",
            "",
            f"* **Gate Diagnostics**: {'; '.join(coverage_summary.get('diagnostics', ['OK']))}",
        ])
    else:
        lines.append(f"> [!WARNING]\n> Traceability coverage could not be evaluated: {coverage_summary.get('error')}")

    lines.extend([
        "",
        "---",
        "",
        "## 4. System Integration Verification Summary (SYS.4 / ATT-6166)",
        "",
    ])

    if sys4_summary.get("available"):
        lines.extend([
            f"* **Artifact**: [`{sys4_summary['path']}`](../../../{sys4_summary['path']})",
            f"* **Execution Timestamp**: `{sys4_summary.get('timestamp')}`",
            f"* **Baseline Commit**: `{sys4_summary.get('git_commit')}`",
            f"* **Overall Status**: **{sys4_summary.get('overall_status')}** ({sys4_summary.get('passed_cases')}/{sys4_summary.get('total_cases')} cases passed)",
            "",
            "| Test Case ID | Description / Interface | Result |",
            "| :--- | :--- | :--- |",
        ])
        for cid, cres in sorted(sys4_summary.get("test_results", {}).items()):
            verdict = "PASS" if cres.get("passed") else "FAIL"
            lines.append(f"| `{cid}` | {cres.get('name', '')} | [ {verdict} ] |")
    else:
        lines.append(f"> [!CAUTION]\n> **Missing Evidence**: `{sys4_summary.get('path')}` does not exist. SYS.4 verification is required before release candidate approval.")

    lines.extend([
        "",
        "---",
        "",
        "## 5. System Verification Protocol Sign-Off Summary (SYS.5 / ATT-6167)",
        "",
    ])

    if sys5_summary.get("available"):
        lines.extend([
            f"* **Artifact**: [`{sys5_summary['path']}`](../../../{sys5_summary['path']})",
            f"* **Recorded Verdict**: **{sys5_summary.get('overall_verdict')}**",
            f"* **Formal QA Sign-Off**: **{'Verified / Complete' if sys5_summary.get('has_signoff') else 'INCOMPLETE / MISSING'}**",
            "",
            "| Protocol Test ID | Target Domain / Interface | Protocol Verdict |",
            "| :--- | :--- | :--- |",
        ])
        for cid, cres in sorted(sys5_summary.get("cases_found", {}).items()):
            lines.append(f"| `{cid}` | Formal On-Device Scenario | [ {cres} ] |")
    else:
        lines.append(f"> [!CAUTION]\n> **Missing Evidence**: `{sys5_summary.get('path')}` does not exist. SYS.5 on-device protocol sign-off is required before release candidate approval.")

    lines.extend([
        "",
        "---",
        "",
        "## 6. Deviations Requiring Human Approval",
        "",
    ])

    if not deviations:
        lines.extend([
            "> [!NOTE]",
            "> **Zero Open Deviations**: All unit tests, traceability coverage thresholds, system integration simulations (SYS.4), and on-device protocol sign-offs (SYS.5) passed without deviations. Release candidate is approved for distribution.",
        ])
    else:
        lines.extend([
            "> [!CAUTION]",
            "> **RELEASE GATING ALERT**: The following open deviations were detected. Under **REQ-PRO-074** and **REQ-PRO-072**, release candidate tag creation and Google Play Store distribution are **BLOCKED** until all deviations are remediated or formally approved by the human Quality Gatekeeper.",
            "",
        ])
        for dev in deviations:
            lines.append(f"- [ ] **DEVIATION**: {dev}")

    lines.extend([
        "",
        "---",
        "",
        "## 7. Release Authorization & Audit Sign-Off",
        "",
        "* **Release Manager**: ___________________________ Date: ____________",
        "* **Quality Assurance Gatekeeper**: _______________ Date: ____________",
        "* **Process Owner (ASPICE Lead)**: ________________ Date: ____________",
        "",
    ])

    return "\n".join(lines)


def generate_release_verification_summary(
    tag: str,
    commit_sha: Optional[str] = None,
    out_path: Optional[str] = None,
    repo_root: Optional[str] = None,
    test_results_dir: Optional[str] = None,
    junit_archive: Optional[str] = None,
    min_req_tst_coverage: Optional[float] = None,
    min_tst_code_coverage: Optional[float] = None,
    min_bidirectional_coverage: Optional[float] = None,
    max_code_without_spec: Optional[int] = None,
) -> Tuple[str, bool]:
    """
    Main orchestration routine. Aggregates verification evidence, compiles
    deviations, renders markdown, and writes to disk.
    Returns (output_path, all_passed).
    """
    root = repo_root or os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    commit = commit_sha or get_git_commit(root)
    target_out = out_path or os.path.join(root, "docs", "releases", tag, "verification_summary.md")

    unit_summary = collect_unit_test_summary(
        root, tag, results_dir=test_results_dir, archive_path=junit_archive
    )
    cov_summary = collect_coverage_summary(
        root,
        min_req_tst_coverage=min_req_tst_coverage,
        min_tst_code_coverage=min_tst_code_coverage,
        min_bidirectional_coverage=min_bidirectional_coverage,
        max_code_without_spec=max_code_without_spec,
    )
    sys4_summary = collect_sys4_summary(root, tag)
    sys5_summary = collect_sys5_summary(root, tag)

    deviations, overall_status = compile_deviations(
        unit_summary, cov_summary, sys4_summary, sys5_summary
    )

    md_content = render_markdown_summary(
        tag,
        commit,
        unit_summary,
        cov_summary,
        sys4_summary,
        sys5_summary,
        deviations,
        overall_status,
    )

    os.makedirs(os.path.dirname(os.path.abspath(target_out)), exist_ok=True)
    with open(target_out, "w", encoding="utf-8") as f:
        f.write(md_content)

    print(f"Generated release verification summary: {target_out}")
    print(f"Overall Status: {overall_status} ({len(deviations)} open deviation(s))")

    return target_out, len(deviations) == 0


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate release verification summary per release tag (ASPICE SWE.6 BP5 / REQ-PRO-074)."
    )
    parser.add_argument(
        "--tag",
        type=str,
        required=True,
        help="Release tag name (e.g. V5.0.0 or V5.0.0__2026111201).",
    )
    parser.add_argument(
        "--commit",
        type=str,
        default=None,
        help="Git commit SHA baseline (defaults to HEAD).",
    )
    parser.add_argument(
        "--out",
        type=str,
        default=None,
        help="Output Markdown file path (defaults to docs/releases/<tag>/verification_summary.md).",
    )
    parser.add_argument(
        "--repo-root",
        type=str,
        default=None,
        help="Repository root path (defaults to repository root containing tools/).",
    )
    parser.add_argument(
        "--test-results-dir",
        type=str,
        default=None,
        help="Path to JUnit XML test results directory (defaults to app/build/test-results/testDebugUnitTest).",
    )
    parser.add_argument(
        "--junit-archive",
        type=str,
        default=None,
        help="Path to versioned JUnit test results zip archive.",
    )
    parser.add_argument(
        "--min-req-tst-coverage",
        type=float,
        default=None,
        help="Minimum required percentage of REQs citing TSTs (0-100).",
    )
    parser.add_argument(
        "--min-tst-code-coverage",
        type=float,
        default=None,
        help="Minimum required percentage of TSTs with test code (0-100).",
    )
    parser.add_argument(
        "--min-bidirectional-coverage",
        type=float,
        default=None,
        help="Minimum required percentage of bidirectional citations (0-100).",
    )
    parser.add_argument(
        "--max-code-without-spec",
        type=int,
        default=None,
        help="Maximum allowed count of code tags without specification in living docs.",
    )
    parser.add_argument(
        "--fail-on-deviations",
        action="store_true",
        help="Exit with non-zero status code if any deviations are detected.",
    )
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    _, passed = generate_release_verification_summary(
        tag=args.tag,
        commit_sha=args.commit,
        out_path=args.out,
        repo_root=args.repo_root,
        test_results_dir=args.test_results_dir,
        junit_archive=args.junit_archive,
        min_req_tst_coverage=args.min_req_tst_coverage,
        min_tst_code_coverage=args.min_tst_code_coverage,
        min_bidirectional_coverage=args.min_bidirectional_coverage,
        max_code_without_spec=args.max_code_without_spec,
    )
    if args.fail_on_deviations and not passed:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
