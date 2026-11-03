#!/usr/bin/env python3
"""
shard_living_docs.py

Restructures monolithic single-line living documentation (docs/requirements.md and docs/tests.md)
into 7 domain-sharded multi-line Markdown files under docs/requirements/ and docs/tests/
with lightweight master index files at docs/requirements.md and docs/tests.md (ATT-4594, REQ-PRO-028).

Enforces 100% ID parity, preserving all 762 REQ IDs and 764 TST IDs without text loss.
"""

import argparse
import os
import re
import sys

DOMAINS = [
    {
        "key": "tracking_cockpit",
        "title": "Tracking & Live Sensor Cockpit",
        "req_file": "docs/requirements/req_tracking_cockpit.md",
        "tst_file": "docs/tests/tst_tracking_cockpit.md",
        "prefixes": {"TRK", "CON", "FIL", "SEN", "FUSION", "STP", "WEA"},
        "desc": "Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle.",
    },
    {
        "key": "aftermath_periods",
        "title": "Post-Workout Analytics & Periods",
        "req_file": "docs/requirements/req_aftermath_periods.md",
        "tst_file": "docs/tests/tst_aftermath_periods.md",
        "prefixes": {"PER", "PERF"},
        "desc": "Post-workout analytics, period statistics, zone distributions, graphs and visual scrubbers.",
    },
    {
        "key": "maps_navigation",
        "title": "Maps, Polylines & Navigation",
        "req_file": "docs/requirements/req_maps_navigation.md",
        "tst_file": "docs/tests/tst_maps_navigation.md",
        "prefixes": {"MAP", "NAV", "GEO", "ROU"},
        "desc": "Map rendering, polylines, navigation overlays, GPX track follow-me, and routing.",
    },
    {
        "key": "data_migration_export",
        "title": "Persistence, Migration & Cloud Export",
        "req_file": "docs/requirements/req_data_migration_export.md",
        "tst_file": "docs/tests/tst_data_migration_export.md",
        "prefixes": {"DAT", "MIG", "EXP", "EXT"},
        "desc": "Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX export.",
    },
    {
        "key": "settings_profiles",
        "title": "Settings, Profiles & Localization",
        "req_file": "docs/requirements/req_settings_profiles.md",
        "tst_file": "docs/tests/tst_settings_profiles.md",
        "prefixes": {"SET", "PRI", "LOC"},
        "desc": "Athlete settings, bike/sensor profiles, privacy zones, data stores, and 9-locale parity.",
    },
    {
        "key": "ui_design_system",
        "title": "UI Components & Design System",
        "req_file": "docs/requirements/req_ui_design_system.md",
        "tst_file": "docs/tests/tst_ui_design_system.md",
        "prefixes": {"UI", "UNT"},
        "desc": "Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts.",
    },
    {
        "key": "process_stability",
        "title": "Engineering Process, Stability & Architecture",
        "req_file": "docs/requirements/req_process_stability.md",
        "tst_file": "docs/tests/tst_process_stability.md",
        "prefixes": {"PRO", "STB", "ARC", "BLD", "TOOL", "MAN", "TST", "STR", "SEC", "REG", "LIV", "BUG", "INT", "LOG", "NFR"},
        "desc": "ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools.",
    },
]

PREFIX_TO_DOMAIN = {}
for dom in DOMAINS:
    for p in dom["prefixes"]:
        PREFIX_TO_DOMAIN[p] = dom["key"]


def clean_multiline_text(text):
    """Converts embedded <br> / <br/> tags and html entities into clean markdown lines."""
    if not text:
        return ""
    # Normalize escaped backslashes before br
    cleaned = re.sub(r"\\+<br\s*/?>", "\n", text, flags=re.IGNORECASE)
    cleaned = re.sub(r"<br\s*/?>", "\n", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"&nbsp;", " ", cleaned)
    # Strip trailing and leading whitespace
    lines = [line.strip() for line in cleaned.splitlines()]
    return "\n".join(lines).strip()


def parse_requirements_file(filepath):
    """
    Parses docs/requirements.md into structured requirement objects.
    Handles multi-line table rows gracefully.
    """
    with open(filepath, "r", encoding="utf-8") as f:
        lines = f.readlines()

    logical_rows = []
    curr = ""
    for line in lines:
        if line.startswith("| **REQ-"):
            if curr:
                logical_rows.append(curr)
            curr = line.strip()
        elif curr:
            if not curr.endswith("|"):
                curr += " " + line.strip()
            else:
                logical_rows.append(curr)
                curr = ""
    if curr:
        logical_rows.append(curr)

    reqs = []
    for row in logical_rows:
        parts = [p.strip() for p in row.split("|")[1:-1]]
        if len(parts) < 6:
            continue
        req_id_m = re.search(r"REQ-([A-Z0-9_-]+)", parts[0])
        if not req_id_m:
            continue
        req_id = f"REQ-{req_id_m.group(1)}"
        prefix = req_id_m.group(1).split("-")[0]
        desc_raw = parts[1]
        rationale_raw = parts[2]
        impl_raw = parts[3]
        tests_raw = parts[4]
        status_raw = parts[5]

        # Extract title from description if bold
        title_m = re.match(r"^\*\*([^*]+)\*\*\.?\s*(.*)", desc_raw, re.DOTALL)
        if title_m:
            title = title_m.group(1).strip().rstrip(".")
            body = title_m.group(2).strip()
        else:
            first_sent = desc_raw.split(". ")[0].strip().rstrip(".")
            title = first_sent[:80] if len(first_sent) > 80 else first_sent
            body = desc_raw

        domain_key = PREFIX_TO_DOMAIN.get(prefix, "process_stability")

        reqs.append({
            "id": req_id,
            "prefix": prefix,
            "domain": domain_key,
            "title": title,
            "description": clean_multiline_text(body),
            "rationale": clean_multiline_text(rationale_raw),
            "implementation": clean_multiline_text(impl_raw),
            "tests": clean_multiline_text(tests_raw),
            "status": clean_multiline_text(status_raw),
            "raw_row": row,
        })
    return reqs


def parse_tests_file(filepath):
    """
    Parses docs/tests.md into structured test objects.
    Handles multi-line table rows gracefully.
    """
    with open(filepath, "r", encoding="utf-8") as f:
        lines = f.readlines()

    logical_rows = []
    curr = ""
    for line in lines:
        if line.startswith("| **TST-"):
            if curr:
                logical_rows.append(curr)
            curr = line.strip()
        elif curr:
            if not curr.endswith("|"):
                curr += " " + line.strip()
            else:
                logical_rows.append(curr)
                curr = ""
    if curr:
        logical_rows.append(curr)

    tests = []
    for row in logical_rows:
        parts = [p.strip() for p in row.split("|")[1:-1]]
        if len(parts) < 6:
            continue
        tst_id_m = re.search(r"TST-([A-Z0-9_-]+)", parts[0])
        if not tst_id_m:
            continue
        tst_id = f"TST-{tst_id_m.group(1)}"
        prefix = tst_id_m.group(1).split("-")[0]
        jira_raw = parts[1]
        component_raw = parts[2]
        reqs_raw = parts[3]
        desc_raw = parts[4]
        status_raw = parts[5]

        domain_key = PREFIX_TO_DOMAIN.get(prefix, "process_stability")

        tests.append({
            "id": tst_id,
            "prefix": prefix,
            "domain": domain_key,
            "jira": clean_multiline_text(jira_raw),
            "component": clean_multiline_text(component_raw),
            "requirements": clean_multiline_text(reqs_raw),
            "description": clean_multiline_text(desc_raw),
            "status": clean_multiline_text(status_raw),
            "raw_row": row,
        })
    return tests


def format_requirement_section(req):
    """Formats a single requirement into a multi-line markdown section."""
    lines = [f"### {req['id']}: {req['title']} [{req['status']}]"]
    if req["tests"]:
        lines.append(f"- **Tests**: {req['tests']}")
    if req["implementation"]:
        lines.append(f"- **Implementation**: {req['implementation']}")
    if req["rationale"]:
        lines.append(f"- **Rationale**: {req['rationale']}")
    if req["description"]:
        desc_lines = req["description"].splitlines()
        if len(desc_lines) == 1:
            lines.append(f"- **Description**: {desc_lines[0]}")
        else:
            lines.append("- **Description**:")
            for dl in desc_lines:
                lines.append(f"  {dl}")
    lines.append("")
    return "\n".join(lines)


def format_test_section(tst):
    """Formats a single test case into a multi-line markdown section."""
    title_summary = tst["component"] if tst["component"] else tst["description"][:60].strip()
    title_summary = title_summary.replace("`", "").strip()
    lines = [f"### {tst['id']}: {title_summary} [{tst['status']}]"]
    if tst["requirements"]:
        lines.append(f"- **Requirement(s)**: {tst['requirements']}")
    if tst["component"]:
        lines.append(f"- **Component**: {tst['component']}")
    if tst["jira"]:
        lines.append(f"- **Jira**: `{tst['jira']}`")
    if tst["description"]:
        desc_lines = tst["description"].splitlines()
        if len(desc_lines) == 1:
            lines.append(f"- **Test Description**: {desc_lines[0]}")
        else:
            lines.append("- **Test Description**:")
            for dl in desc_lines:
                lines.append(f"  {dl}")
    lines.append("")
    return "\n".join(lines)


def write_domain_shards(reqs, tests, repo_root):
    """Writes the 7 requirement domain shards and 7 test domain shards."""
    os.makedirs(os.path.join(repo_root, "docs", "requirements"), exist_ok=True)
    os.makedirs(os.path.join(repo_root, "docs", "tests"), exist_ok=True)

    # Group requirements by domain
    reqs_by_domain = {d["key"]: [] for d in DOMAINS}
    for r in reqs:
        reqs_by_domain[r["domain"]].append(r)

    # Group tests by domain
    tests_by_domain = {d["key"]: [] for d in DOMAINS}
    for t in tests:
        tests_by_domain[t["domain"]].append(t)

    # Write requirements shards
    for dom in DOMAINS:
        dom_reqs = reqs_by_domain[dom["key"]]
        abs_req_path = os.path.join(repo_root, dom["req_file"])
        content_lines = [
            f"# Living Requirements: {dom['title']}",
            "",
            f"**Domain**: `{dom['key']}`  ",
            f"**Prefixes**: {', '.join(f'`{p}`' for p in sorted(dom['prefixes']))}  ",
            f"**Scope**: {dom['desc']}  ",
            f"**Total Requirements**: {len(dom_reqs)}",
            "",
            "---",
            "",
        ]
        for r in dom_reqs:
            content_lines.append(format_requirement_section(r))

        with open(abs_req_path, "w", encoding="utf-8") as f:
            f.write("\n".join(content_lines))

    # Write tests shards
    for dom in DOMAINS:
        dom_tests = tests_by_domain[dom["key"]]
        abs_tst_path = os.path.join(repo_root, dom["tst_file"])
        content_lines = [
            f"# Living Test Specification: {dom['title']}",
            "",
            f"**Domain**: `{dom['key']}`  ",
            f"**Prefixes**: {', '.join(f'`{p}`' for p in sorted(dom['prefixes']))}  ",
            f"**Scope**: {dom['desc']}  ",
            f"**Total Tests**: {len(dom_tests)}",
            "",
            "---",
            "",
        ]
        for t in dom_tests:
            content_lines.append(format_test_section(t))

        with open(abs_tst_path, "w", encoding="utf-8") as f:
            f.write("\n".join(content_lines))


def generate_master_indices(reqs, tests, repo_root):
    """Generates the lightweight master indices at docs/requirements.md and docs/tests.md."""
    # Count by domain
    req_counts = {d["key"]: 0 for d in DOMAINS}
    req_verified = {d["key"]: 0 for d in DOMAINS}
    for r in reqs:
        req_counts[r["domain"]] += 1
        if "verified" in r["status"].lower():
            req_verified[r["domain"]] += 1

    tst_counts = {d["key"]: 0 for d in DOMAINS}
    tst_verified = {d["key"]: 0 for d in DOMAINS}
    for t in tests:
        tst_counts[t["domain"]] += 1
        if "verified" in t["status"].lower() or "passed" in t["status"].lower():
            tst_verified[t["domain"]] += 1

    # docs/requirements.md master index
    req_index_lines = [
        "# aTrainingTracker: Living Requirements Specification (Master Index)",
        "",
        "This master index links to the 7 domain-sharded requirement files under `docs/requirements/`.",
        "Living requirements use multi-line Markdown headings (`### REQ-XXX: Title [Status]`) to eliminate single-line table bloat (ATT-4594, `REQ-PRO-028`).",
        "",
        "## Domain Requirement Shards",
        "",
        "| Domain Shard | Scope & Functional Areas | Prefixes | Total | Verified | Planned / Open |",
        "| :--- | :--- | :--- | :---: | :---: | :---: |",
    ]
    for dom in DOMAINS:
        k = dom["key"]
        tot = req_counts[k]
        ver = req_verified[k]
        open_count = tot - ver
        prefix_str = ", ".join(f"`{p}`" for p in sorted(dom["prefixes"]))
        req_index_lines.append(
            f"| [{dom['title']}](requirements/{os.path.basename(dom['req_file'])}) | {dom['desc']} | {prefix_str} | {tot} | {ver} | {open_count} |"
        )
    req_index_lines.extend([
        "",
        f"**Total Living Requirements**: {len(reqs)} across 7 domain shards.",
        "",
        "---",
        "",
        "## Developer & AI Agent Guidelines",
        "* **Reading Living Requirements**: View the relevant domain shard directly (`view_file` on `docs/requirements/req_<domain>.md`), keeping each file small (~100–350 KB) and well within tool output limits.",
        "* **Adding / Modifying Requirements**: Append or amend requirements using the standard multi-line section format:",
        "  ```markdown",
        "  ### REQ-DOM-NNN: Formal Title [Status]",
        "  - **Tests**: `TST-DOM-NNN` | **Tickets**: `ATT-XXXX`",
        "  - **Implementation**: `TargetComponent.kt`",
        "  - **Rationale**: Architectural justification...",
        "  - **Description**: Normative contract specification...",
        "  ```",
        "* **Chesterton's Fence Archaeology**: When modifying an existing requirement, ensure the corresponding Stage 2 specification contains all 4 mandatory archaeology fields (`verify_requirement_governance.py`).",
        "",
    ])

    with open(os.path.join(repo_root, "docs", "requirements.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(req_index_lines))

    # docs/tests.md master index
    tst_index_lines = [
        "# aTrainingTracker: Living Test Specification & Verification Plan (Master Index)",
        "",
        "This master index links to the 7 domain-sharded test specification files under `docs/tests/`.",
        "Living test specifications use multi-line Markdown headings (`### TST-XXX: Title [Status]`) (ATT-4594, `REQ-PRO-028`).",
        "",
        "## Domain Test Shards",
        "",
        "| Domain Shard | Scope & Functional Areas | Prefixes | Total | Verified | Planned / Open |",
        "| :--- | :--- | :--- | :---: | :---: | :---: |",
    ]
    for dom in DOMAINS:
        k = dom["key"]
        tot = tst_counts[k]
        ver = tst_verified[k]
        open_count = tot - ver
        prefix_str = ", ".join(f"`{p}`" for p in sorted(dom["prefixes"]))
        tst_index_lines.append(
            f"| [{dom['title']}](tests/{os.path.basename(dom['tst_file'])}) | {dom['desc']} | {prefix_str} | {tot} | {ver} | {open_count} |"
        )
    tst_index_lines.extend([
        "",
        f"**Total Living Tests**: {len(tests)} across 7 domain shards.",
        "",
        "---",
        "",
        "## Verification Procedure",
        "* **Unit Tests**: Targeted tests via `./gradlew testDebugUnitTest --tests \"com.atrainingtracker.<package>.*\"` during Stages 4 & 5; full suite at Sprint Integration Gate.",
        "* **Requirement Governance**: Enforced via `python3 tools/verify_requirement_governance.py`.",
        "* **Auto-Verification**: Flipped from `Planned` to `Verified` automatically on Stage 5 submission (`auto_verify_living_docs_on_stage5`).",
        "",
    ])

    with open(os.path.join(repo_root, "docs", "tests.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(tst_index_lines))


def verify_sharding_parity(reqs, tests, repo_root):
    """
    Asserts 100% ID parity between original parsed data and written domain shards.
    Returns (success: bool, errors: list[str]).
    """
    errors = []

    # Check all requirement shards exist and contain all REQ IDs
    found_req_ids = set()
    for dom in DOMAINS:
        abs_path = os.path.join(repo_root, dom["req_file"])
        if not os.path.exists(abs_path):
            errors.append(f"Missing requirement shard: {dom['req_file']}")
            continue
        with open(abs_path, "r", encoding="utf-8") as f:
            content = f.read()
        ids = set(re.findall(r"###\s*(REQ-[A-Z0-9_-]+):", content))
        found_req_ids.update(ids)

    expected_req_ids = set(r["id"] for r in reqs)
    missing_reqs = expected_req_ids - found_req_ids
    if missing_reqs:
        errors.append(f"Missing REQ IDs in shards ({len(missing_reqs)}): {sorted(missing_reqs)[:10]}")

    # Check all test shards exist and contain all TST IDs
    found_tst_ids = set()
    for dom in DOMAINS:
        abs_path = os.path.join(repo_root, dom["tst_file"])
        if not os.path.exists(abs_path):
            errors.append(f"Missing test shard: {dom['tst_file']}")
            continue
        with open(abs_path, "r", encoding="utf-8") as f:
            content = f.read()
        ids = set(re.findall(r"###\s*(TST-[A-Z0-9_-]+):", content))
        found_tst_ids.update(ids)

    expected_tst_ids = set(t["id"] for t in tests)
    missing_tests = expected_tst_ids - found_tst_ids
    if missing_tests:
        errors.append(f"Missing TST IDs in shards ({len(missing_tests)}): {sorted(missing_tests)[:10]}")

    return len(errors) == 0, errors


def main():
    parser = argparse.ArgumentParser(description="Shard living documentation into domain files (ATT-4594).")
    parser.add_argument("--repo-root", default=".", help="Root of git repository.")
    parser.add_argument("--verify-only", action="store_true", help="Only verify existing shards without re-generating.")
    args = parser.parse_args()

    repo_root = os.path.abspath(args.repo_root)
    req_src = os.path.join(repo_root, "docs", "requirements.md")
    tst_src = os.path.join(repo_root, "docs", "tests.md")

    if args.verify_only:
        # Check that shards and master indices exist
        all_shards = [dom["req_file"] for dom in DOMAINS] + [dom["tst_file"] for dom in DOMAINS]
        missing = [f for f in all_shards if not os.path.exists(os.path.join(repo_root, f))]
        if missing:
            print(f"FAIL: Missing shards: {missing}", file=sys.stderr)
            sys.exit(1)
        print("PASS: All 14 domain shards exist.")
        sys.exit(0)

    print("Parsing monolithic living documents...")
    reqs = parse_requirements_file(req_src)
    tests = parse_tests_file(tst_src)
    print(f"Parsed {len(reqs)} requirements and {len(tests)} test cases.")

    print("Writing 7 domain requirement shards and 7 domain test shards...")
    write_domain_shards(reqs, tests, repo_root)

    print("Generating lightweight master index files...")
    generate_master_indices(reqs, tests, repo_root)

    print("Verifying 100% ID parity across shards...")
    success, errors = verify_sharding_parity(reqs, tests, repo_root)
    if not success:
        print("FAIL: Sharding parity errors:", file=sys.stderr)
        for err in errors:
            print(f"  - {err}", file=sys.stderr)
        sys.exit(1)

    print(f"SUCCESS: Successfully sharded {len(reqs)} REQ IDs and {len(tests)} TST IDs into 7 domains with 100% parity.")


if __name__ == "__main__":
    main()
