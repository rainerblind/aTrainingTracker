#!/usr/bin/env python3
"""
tools/hoechstmass/tools/verify_requirement_governance.py

Enforces Chesterton's Fence requirement archaeology governance and living documentation
master table counter parity across domain shards dynamically using hoechstmass_config.json.
Pure Python 3 standard library.
"""

import json
import os
import re
import subprocess
import sys

DEFAULT_CONFIG_LOCATIONS = [
    os.path.join(os.path.dirname(__file__), "hoechstmass_config.json"),
    os.path.join(os.path.dirname(__file__), "..", "hoechstmass_config.json"),
    os.path.join(os.path.dirname(__file__), "..", "..", "hoechstmass_config.json"),
]

HEADER_PATTERN = re.compile(
    r"(?:#{2,4}|h[2-4]\.)\s*(?:\d+(?:\.\d+)*[.\)]\s*)?Requirement Archaeology & Chesterton's Fence Audit",
    re.IGNORECASE,
)


def load_config(custom_path=None):
    """Loads configuration from hoechstmass_config.json or returns defaults."""
    paths = [custom_path] if custom_path else DEFAULT_CONFIG_LOCATIONS
    for p in paths:
        if p and os.path.exists(p):
            with open(p, "r", encoding="utf-8") as f:
                return json.load(f)
    return {
        "requirements": {
            "prefixes": ["REQ", "TST", "PRO", "ARC", "UI", "TOOL"],
            "mandatory_archaeology_fields": [
                "Original Requirement ID & Target",
                "Historical Origin & Commit Trace",
                "Root Reason for Existing Formulation",
                "Preservation of Core Invariants"
            ]
        },
        "paths": {
            "engineering_dir": "docs/engineering",
            "requirements_master": "docs/requirements.md",
            "requirements_shard_dir": "docs/requirements",
            "tests_master": "docs/tests.md",
            "tests_shard_dir": "docs/tests",
            "allowed_engineering_entries": [
                "analysis",
                "metrics",
                "plans",
                "retros",
                "test_specs",
                "walkthroughs",
                "test_results",
                "rule_history_archive.md",
                "workflow_overview.html"
            ]
        }
    }


def validate_archaeology_section(text, mandatory_fields=None):
    """Validates that text contains all 4 mandatory Chesterton's Fence archaeology fields."""
    if mandatory_fields is None:
        cfg = load_config()
        mandatory_fields = cfg.get("requirements", {}).get("mandatory_archaeology_fields", [])

    errors = []
    if not text:
        errors.append("Deliverable text is empty or missing.")
        return False, errors

    header_match = HEADER_PATTERN.search(text)
    if not header_match:
        errors.append("Missing mandatory section header: '### Requirement Archaeology & Chesterton's Fence Audit'.")
        return False, errors

    section_start = header_match.end()
    remaining = text[section_start:]
    next_header = re.search(r"(?:\n#{1,3}\s+|\nh[1-3]\.\s+)", remaining)
    section_body = remaining[:next_header.start()] if next_header else remaining

    for field in mandatory_fields:
        pattern = re.compile(
            rf"(?:[\*\-\•\#]+\s*)?(?:\*|`)*{re.escape(field)}(?:\*|`)*\s*[:\-\—]\s*([^\n\r]+)",
            re.IGNORECASE,
        )
        match = pattern.search(section_body)
        if not match:
            if re.search(re.escape(field), section_body, re.IGNORECASE):
                errors.append(f"Field '{field}' is present but has empty or invalid content.")
            else:
                errors.append(f"Missing mandatory field: '{field}'.")
        else:
            val = match.group(1).strip()
            clean_val = re.sub(r"^[`\*_]+|[`\*_]+$", "", val).strip()
            if not clean_val or clean_val.lower() in ["todo", "none", "n/a", "tbd", ""]:
                errors.append(f"Field '{field}' has empty or placeholder content: '{val}'.")

    return len(errors) == 0, errors


def verify_engineering_directory_structure(eng_dir=None, allowed=None):
    """Verifies that docs/engineering/ contains only allowed canonical subdirectories."""
    cfg = load_config()
    if eng_dir is None:
        eng_dir = cfg.get("paths", {}).get("engineering_dir", "docs/engineering")
    if allowed is None:
        allowed = set(cfg.get("paths", {}).get("allowed_engineering_entries", []))

    if not os.path.isdir(eng_dir):
        return True, []

    unexpected = sorted(set(os.listdir(eng_dir)) - set(allowed))
    if unexpected:
        return False, [
            f"ERROR: Non-canonical entries found in {eng_dir}/: {', '.join(unexpected)}. "
            f"Allowed entries: {', '.join(sorted(allowed))}."
        ]
    return True, []


def verify_master_table_parity(repo_root=None, config=None):
    """Verifies 100% mathematical parity between master tables and domain shards."""
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
    if config is None:
        config = load_config()

    errors = []
    p = config.get("paths", {})
    specs = [
        (p.get("requirements_master", "docs/requirements.md"), p.get("requirements_shard_dir", "docs/requirements"), "REQ"),
        (p.get("tests_master", "docs/tests.md"), p.get("tests_shard_dir", "docs/tests"), "TST"),
    ]

    for master_rel, shard_dir_rel, prefix in specs:
        abs_shard_dir = os.path.join(repo_root, shard_dir_rel)
        if not os.path.isdir(abs_shard_dir):
            continue
        shard_counts = {}
        for fn in sorted(os.listdir(abs_shard_dir)):
            if not fn.endswith(".md"):
                continue
            fp = os.path.join(abs_shard_dir, fn)
            with open(fp, "r", encoding="utf-8") as f:
                lines = f.readlines()
            tot = ver = plan = 0
            for l in lines:
                m = re.match(r"^(#{2,4})\s*(" + prefix + r"-[A-Z0-9_-]+):?\s*(.*?)$", l.strip())
                if not m:
                    continue
                tot += 1
                if "[Verified]" in l:
                    ver += 1
                else:
                    plan += 1
            shard_counts[fn] = (tot, ver, plan)

        master_fp = os.path.join(repo_root, master_rel)
        if not os.path.exists(master_fp):
            continue
        with open(master_fp, "r", encoding="utf-8") as f:
            master_lines = f.readlines()

        grand_total = sum(c[0] for c in shard_counts.values())
        shards_checked = 0
        for lno, line in enumerate(master_lines, 1):
            m_link = re.search(r"\((?:requirements|tests)/((?:req|tst)_[^)]+\.md)\)", line)
            if m_link:
                s_name = m_link.group(1)
                m_cells = re.search(r"\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*$", line)
                if not m_cells:
                    errors.append(f"{master_rel}:{lno}: Table row for {s_name} missing numeric counts.")
                    continue
                t, v, p_count = int(m_cells.group(1)), int(m_cells.group(2)), int(m_cells.group(3))
                shards_checked += 1
                if s_name in shard_counts:
                    exp_t, exp_v, exp_p = shard_counts[s_name]
                    if (t, v, p_count) != (exp_t, exp_v, exp_p):
                        errors.append(
                            f"{master_rel}:{lno}: Count mismatch for {s_name}: "
                            f"Table has Total={t}, Verified={v}, Planned={p_count}; "
                            f"Shard has Total={exp_t}, Verified={exp_v}, Planned={exp_p}."
                        )
                else:
                    errors.append(f"{master_rel}:{lno}: Shard {s_name} referenced in table does not exist.")
            elif "Total Living " in line:
                m_tot = re.search(r"\*\*Total Living (?:Requirements|Tests)\*\*:\s*(\d+)", line)
                if m_tot:
                    reported_total = int(m_tot.group(1))
                    if reported_total != grand_total:
                        errors.append(
                            f"{master_rel}:{lno}: Grand total mismatch: "
                            f"Reported={reported_total}, Actual parsed sum={grand_total}."
                        )

        if shards_checked != len(shard_counts):
            errors.append(
                f"{master_rel}: Shard count mismatch: Table checked {shards_checked} shards, "
                f"filesystem has {len(shard_counts)} shards."
            )

    return len(errors) == 0, errors


def main():
    dir_ok, dir_errs = verify_engineering_directory_structure()
    if not dir_ok:
        for e in dir_errs:
            print(e, file=sys.stderr)
        sys.exit(1)

    parity_ok, parity_errs = verify_master_table_parity()
    if not parity_ok:
        for e in parity_errs:
            print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)

    print("PASS: Living documentation master tables match all domain shards with 100% parity.")
    sys.exit(0)


if __name__ == "__main__":
    main()
