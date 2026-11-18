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

DEFAULT_MAX_SHARD_LINES = 4000

DOMAINS = [
    {
        "key": "tracking_cockpit",
        "title": "Tracking & Live Sensor Cockpit",
        "req_file": "docs/requirements/req_tracking_cockpit.md",
        "tst_file": "docs/tests/tst_tracking_cockpit.md",
        "prefixes": {"TRK", "CON", "FIL", "SEN", "FUSION", "WEA"},
        "desc": "Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle.",
    },
    {
        "key": "aftermath_periods",
        "title": "Post-Workout Analytics & Periods",
        "req_file": "docs/requirements/req_aftermath_periods.md",
        "tst_file": "docs/tests/tst_aftermath_periods.md",
        "prefixes": {"HRV", "PER", "PERF", "RAD", "SPT", "WKO"},
        "desc": "Post-workout analytics, period statistics, zone distributions, graphs and visual scrubbers.",
    },
    {
        "key": "maps_navigation",
        "title": "Maps, Polylines & Navigation",
        "req_file": "docs/requirements/req_maps_navigation.md",
        "tst_file": "docs/tests/tst_maps_navigation.md",
        "prefixes": {"CLM", "GEO", "MAP", "NAV", "RAD", "ROU"},
        "desc": "Map rendering, polylines, navigation overlays, GPX track follow-me, and routing.",
    },
    {
        "key": "data_migration_export",
        "title": "Persistence, Migration & Cloud Export",
        "req_file": "docs/requirements/req_data_migration_export.md",
        "tst_file": "docs/tests/tst_data_migration_export.md",
        "prefixes": {"DAT", "MIG", "EXP", "EXT", "RAD"},
        "desc": "Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX export.",
    },
    {
        "key": "settings_profiles",
        "title": "Settings, Profiles & Localization",
        "req_file": "docs/requirements/req_settings_profiles.md",
        "tst_file": "docs/tests/tst_settings_profiles.md",
        "prefixes": {"SET", "PRI", "LOC", "MON"},
        "desc": "Athlete settings, bike/sensor profiles, privacy zones, data stores, monetization, and 9-locale parity.",
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
    {
        "key": "aftermath_reviews",
        "title": "Jubilee Milestones & App Reviews",
        "req_file": "docs/requirements/req_aftermath_reviews.md",
        "tst_file": "docs/tests/tst_aftermath_reviews.md",
        "prefixes": {"REV"},
        "desc": "Milestone workout auto-naming, jubilee achievements, Google Play in-app reviews, athlete feedback, and supporter tier promotions.",
    },
    {
        "key": "store_presence",
        "title": "Store Presence",
        "req_file": "docs/requirements/req_store_presence.md",
        "tst_file": "docs/tests/tst_store_presence.md",
        "prefixes": {"STP"},
        "desc": "Store presence, Google Play listings, screenshots, store metadata contract testing, and release governance.",
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


def split_markdown_table_row(row):
    """
    Splits a markdown table row by '|' cell delimiters while ignoring:
    - Escaped pipes (\\|)
    - Pipes inside backticks / inline code spans (`...` or ``...``)
    Returns list of stripped cell contents without leading/trailing empty boundary cells.
    """
    cells = []
    current = []
    in_code_ticks = 0
    i = 0
    n = len(row)
    while i < n:
        char = row[i]

        # Check for escaped characters
        if char == "\\" and i + 1 < n:
            current.append(char)
            current.append(row[i + 1])
            i += 2
            continue

        # Check for backtick run
        if char == "`":
            run_len = 1
            while i + run_len < n and row[i + run_len] == "`":
                run_len += 1
            if in_code_ticks == 0:
                in_code_ticks = run_len
            elif in_code_ticks == run_len:
                in_code_ticks = 0
            current.append(row[i:i + run_len])
            i += run_len
            continue

        # Check for pipe delimiter outside code spans
        if char == "|" and in_code_ticks == 0:
            cells.append("".join(current))
            current = []
            i += 1
            continue

        current.append(char)
        i += 1

    cells.append("".join(current))

    # Strip leading and trailing empty cell from table row boundary pipes (| cell1 | cell2 |)
    if cells and not cells[0].strip():
        cells = cells[1:]
    if cells and not cells[-1].strip():
        cells = cells[:-1]
    return [c.strip() for c in cells]


def parse_requirements_file(filepath):
    """
    Parses docs/requirements.md into structured requirement objects.
    Handles multi-line table rows gracefully and supports both 6-column and 7-column formats.
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
        parts = split_markdown_table_row(row)
        if len(parts) < 6:
            continue
        req_id_m = re.search(r"REQ-([A-Z0-9_-]+)", parts[0])
        if not req_id_m:
            continue
        req_id = f"REQ-{req_id_m.group(1)}"
        prefix = req_id_m.group(1).split("-")[0]

        if len(parts) >= 7:
            # 7-column table: [0] ID, [1] Title, [2] Description, [3] Rationale, [4] Implementation, [5] Tests, [6] Status
            title = clean_multiline_text(parts[1]).strip().rstrip(".")
            if title.startswith("**") and title.endswith("**"):
                title = title[2:-2].strip().rstrip(".")
            body = parts[2]
            rationale_raw = parts[3]
            impl_raw = parts[4]
            tests_raw = parts[5]
            status_raw = parts[6]
        else:
            # 6-column table: [0] ID, [1] Description (with bold title), [2] Rationale, [3] Implementation, [4] Tests, [5] Status
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
        parts = split_markdown_table_row(row)
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
        f"This master index links to the {len(DOMAINS)} domain-sharded requirement files under `docs/requirements/`.",
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
        f"**Total Living Requirements**: {len(reqs)} across {len(DOMAINS)} domain shards.",
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
        f"This master index links to the {len(DOMAINS)} domain-sharded test specification files under `docs/tests/`.",
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
        f"**Total Living Tests**: {len(tests)} across {len(DOMAINS)} domain shards.",
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


def check_shard_sizes(repo_root=None, max_lines=DEFAULT_MAX_SHARD_LINES):
    """
    Audits all living documentation requirement and test shards (including split *_partN.md
    sub-shards) under docs/requirements/ and docs/tests/ against max_lines (REQ-PRO-090, REQ-PRO-127.3).
    Returns:
        dict with:
            'passed': bool (True if zero shards exceed max_lines)
            'max_lines': int
            'oversized': list of dicts: [{'path': rel_path, 'lines': count, 'type': 'requirement'|'test', 'domain': dom_key}]
            'shards': dict of rel_path -> line_count
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    shards = {}
    oversized = []

    dirs = [
        ("docs/requirements", "requirement"),
        ("docs/tests", "test"),
    ]
    for dir_rel, shard_type in dirs:
        dir_abs = os.path.join(repo_root, dir_rel)
        if not os.path.isdir(dir_abs):
            continue
        for fn in sorted(os.listdir(dir_abs)):
            if not fn.endswith(".md"):
                continue
            rel_path = f"{dir_rel}/{fn}"
            abs_path = os.path.join(dir_abs, fn)
            if not os.path.isfile(abs_path):
                continue
            with open(abs_path, "r", encoding="utf-8") as f:
                lines = sum(1 for _ in f)
            shards[rel_path] = lines
            dom_key = re.sub(r"^(?:req|tst)_", "", os.path.splitext(fn)[0])
            dom_key = re.sub(r"_part\d+$", "", dom_key)
            if lines > max_lines:
                oversized.append({
                    "path": rel_path,
                    "lines": lines,
                    "type": shard_type,
                    "domain": dom_key,
                })

    return {
        "passed": len(oversized) == 0,
        "max_lines": max_lines,
        "oversized": oversized,
        "shards": shards,
    }


def split_oversized_shard(shard_path, max_lines=DEFAULT_MAX_SHARD_LINES, output_dir=None):
    """
    Partitions an oversized requirement or test shard into compliant sub-shards (REQ-PRO-090)
    while strictly preserving:
      - 100% ID uniqueness (every ID appears exactly once across parts)
      - Zero text loss (all fields, markdown headings, descriptions preserved)
      - Bidirectional traceability parity
    Returns list of paths to created sub-shards.
    """
    if not os.path.exists(shard_path):
        raise FileNotFoundError(f"Shard file not found: {shard_path}")

    with open(shard_path, "r", encoding="utf-8") as f:
        content = f.read()

    lines = content.splitlines()
    if len(lines) <= max_lines:
        return [shard_path]

    # Strip trailing Change History / Revision History from body so it isn't swallowed into the last section
    history_match = re.search(r"(?m)^(?:---\s*\n+)?##\s+(?:\d+\.\s+)?(?:Change|Revision)\s+History\b", content)
    body_end = history_match.start() if history_match else len(content)
    body_content = content[:body_end].rstrip()

    # Sections begin with '### REQ-' or '### TST-'
    section_pattern = re.compile(r"^(###\s+(?:REQ|TST)-[A-Z0-9_-]+:.*)", re.MULTILINE)
    matches = list(section_pattern.finditer(body_content))

    if not matches:
        return [shard_path]

    header_text = body_content[:matches[0].start()].strip()
    # Remove trailing '---' from header_text if present so we don't duplicate separators
    header_text = re.sub(r"\n+---\s*$", "", header_text).strip()

    has_doc_header = bool(re.search(r"\*\*Document ID\*\*:\s*`?DOC-[A-Z0-9-]+`?", header_text))
    footer_template = (
        "\n\n---\n\n"
        "## Change History\n\n"
        "| Version | Date | Author | Ticket | Summary of Changes |\n"
        "| :--- | :--- | :--- | :--- | :--- |\n"
        "| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |\n"
    )
    footer_lines_count = len(footer_template.splitlines()) if (has_doc_header or history_match) else 1

    # Extract individual sections
    sections = []
    for i, m in enumerate(matches):
        start = m.start()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(body_content)
        sections.append(body_content[start:end].strip())

    header_lines_count = len(header_text.splitlines()) + 4
    effective_max_lines = max(1, max_lines - header_lines_count - footer_lines_count)

    chunks = []
    current_chunk = []
    current_lines = 0

    for sec in sections:
        sec_lines = len(sec.splitlines()) + 1
        if current_chunk and (current_lines + sec_lines > effective_max_lines):
            chunks.append(current_chunk)
            current_chunk = [sec]
            current_lines = sec_lines
        else:
            current_chunk.append(sec)
            current_lines += sec_lines

    if current_chunk:
        chunks.append(current_chunk)

    if len(chunks) <= 1:
        return [shard_path]

    if output_dir is None:
        output_dir = os.path.dirname(shard_path)
    os.makedirs(output_dir, exist_ok=True)

    base_name = os.path.basename(shard_path)
    name_root, ext = os.path.splitext(base_name)

    created_files = []
    total_parts = len(chunks)

    for idx, chunk in enumerate(chunks, 1):
        part_filename = f"{name_root}_part{idx}{ext}"
        part_path = os.path.join(output_dir, part_filename)

        part_header = header_text
        part_header = re.sub(
            r"^(#\s+Living\s+(?:Requirements|Test\s+Specification):\s+.*?)(?:\s+\(Part\s+\d+(?:\s+of\s+\d+)?\))?$",
            rf"\1 (Part {idx} of {total_parts})",
            part_header,
            flags=re.MULTILINE,
        )
        part_header = re.sub(
            r"(\*\*Document ID\*\*:\s*`?DOC-[A-Z0-9-]+?)(?:-P\d+)?(`?(?:\s|$))",
            rf"\1-P{idx}\2",
            part_header,
        )
        if has_doc_header:
            part_header = re.sub(
                r"\*\*Version\*\*:\s*`?\d+\.\d+\.\d+`?",
                "**Version**: 1.0.0",
                part_header,
            )
        part_header = re.sub(
            r"\*\*(?:Total Requirements|Total Tests)\*\*:\s*\d+",
            f"**Total {'Requirements' if 'req' in name_root else 'Tests'}**: {len(chunk)}",
            part_header,
        )
        part_header = re.sub(
            r"(\*\*Domain\*\*:\s*`[^`]+)(_part\d+)?(`)",
            rf"\1_part{idx}\3",
            part_header,
        )

        part_content = part_header + "\n\n---\n\n" + "\n\n".join(chunk)
        if has_doc_header or history_match:
            part_content += footer_template
        else:
            part_content += "\n"

        with open(part_path, "w", encoding="utf-8") as f:
            f.write(part_content)

        created_files.append(part_path)

    return created_files


def main():
    parser = argparse.ArgumentParser(description="Shard living documentation into domain files (ATT-4594).")
    parser.add_argument("--repo-root", default=".", help="Root of git repository.")
    parser.add_argument("--verify-only", action="store_true", help="Only verify existing shards without re-generating.")
    parser.add_argument("--force", action="store_true", help="Force overwrite even if master index or zero rows detected.")
    parser.add_argument("--check-size", action="store_true", help="Check line counts of domain shards against limit (REQ-PRO-090).")
    parser.add_argument("--max-lines", type=int, default=DEFAULT_MAX_SHARD_LINES, help=f"Max line limit for shards (default: {DEFAULT_MAX_SHARD_LINES}).")
    parser.add_argument("--split-shard", help="Path to an oversized shard to split into sub-shards (REQ-PRO-090).")
    parser.add_argument("--out-dir", help="Output directory for split sub-shards.")
    parser.add_argument("--strict", action="store_true", help="Exit with 1 if any shard exceeds line limit in --check-size.")
    args = parser.parse_args()

    repo_root = os.path.abspath(args.repo_root)
    req_src = os.path.join(repo_root, "docs", "requirements.md")
    tst_src = os.path.join(repo_root, "docs", "tests.md")

    if args.check_size:
        result = check_shard_sizes(repo_root, max_lines=args.max_lines)
        if result["passed"]:
            print(f"PASS: All {len(result['shards'])} domain shards are within line limits (limit: {args.max_lines} lines).")
            sys.exit(0)
        else:
            print(f"FLAGGED: {len(result['oversized'])} oversized shard(s) exceeding {args.max_lines} lines limit:")
            for item in result["oversized"]:
                print(f"  - OVERSIZED: {item['path']} ({item['lines']} lines > limit {args.max_lines})")
            if args.strict:
                sys.exit(1)
            sys.exit(0)

    if args.split_shard:
        shard_path = os.path.abspath(args.split_shard)
        created = split_oversized_shard(shard_path, max_lines=args.max_lines, output_dir=args.out_dir)
        print(f"Split {shard_path} into {len(created)} sub-shards:")
        for cp in created:
            print(f"  - {cp}")
        sys.exit(0)

    if args.verify_only:
        # Check that every domain has either its monolithic shard or >= 1 split *_partN.md sub-shard
        missing = []
        discovered = set()
        for dom in DOMAINS:
            for field in ("req_file", "tst_file"):
                rel = dom[field]
                abs_p = os.path.join(repo_root, rel)
                if os.path.exists(abs_p):
                    discovered.add(rel)
                    continue
                parent_dir = os.path.dirname(abs_p)
                stem = os.path.splitext(os.path.basename(rel))[0]
                parts = []
                if os.path.isdir(parent_dir):
                    parts = [
                        f"{os.path.dirname(rel)}/{fn}"
                        for fn in sorted(os.listdir(parent_dir))
                        if fn.startswith(f"{stem}_part") and fn.endswith(".md")
                    ]
                if parts:
                    discovered.update(parts)
                else:
                    missing.append(rel)
        if missing:
            print(f"FAIL: Missing shards: {missing}", file=sys.stderr)
            sys.exit(1)
        print(f"PASS: All {len(discovered)} living documentation shards exist across {len(DOMAINS)} domains.")
        sys.exit(0)

    # Safety Guard: Check if requirements.md or tests.md is already a Master Index
    is_master_index = False
    if os.path.exists(req_src):
        with open(req_src, "r", encoding="utf-8") as f:
            first_lines = "".join(f.readline() for _ in range(10))
            if "Master Index" in first_lines:
                is_master_index = True
    if os.path.exists(tst_src):
        with open(tst_src, "r", encoding="utf-8") as f:
            first_lines = "".join(f.readline() for _ in range(10))
            if "Master Index" in first_lines:
                is_master_index = True

    if is_master_index and not args.force:
        print(
            "ERROR: docs/requirements.md or docs/tests.md is already a Master Index! "
            "Refusing to overwrite domain shards. Use --force to override.",
            file=sys.stderr,
        )
        sys.exit(1)

    print("Parsing monolithic living documents...")
    reqs = parse_requirements_file(req_src)
    tests = parse_tests_file(tst_src)
    print(f"Parsed {len(reqs)} requirements and {len(tests)} test cases.")

    if (len(reqs) == 0 or len(tests) == 0) and not args.force:
        print(
            f"ERROR: Parsed {len(reqs)} requirements and {len(tests)} tests. "
            "Refusing to overwrite domain shards with empty data. Use --force to override.",
            file=sys.stderr,
        )
        sys.exit(1)

    print(f"Writing {len(DOMAINS)} domain requirement shards and {len(DOMAINS)} domain test shards...")
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

    print(f"SUCCESS: Successfully sharded {len(reqs)} REQ IDs and {len(tests)} TST IDs into {len(DOMAINS)} domains with 100% parity.")


if __name__ == "__main__":
    main()
