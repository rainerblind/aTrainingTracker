#!/usr/bin/env python3
"""
verify_requirement_governance.py

Enforces Chesterton's Fence requirement archaeology governance (REQ-PRO-022 / TST-PRO-015).
Ensures that whenever an existing requirement in docs/requirements.md is modified, relaxed,
or replaced, an auditable archaeological investigation section is present with all 4 mandatory fields:
  1. Original Requirement ID & Target
  2. Historical Origin & Commit Trace
  3. Root Reason for Existing Formulation
  4. Preservation of Core Invariants

Net-new requirements (novel requirement IDs absent from the base comparison) bypass this check cleanly.

Invariants:
  - Strictly Python 3 standard library (no pip packages).
  - Deterministic exit codes: 0 = valid / compliant, 1 = schema failure.
"""

import os
import re
import subprocess
import sys

MANDATORY_FIELDS = [
    r"Original Requirement ID & Target",
    r"Historical Origin & Commit Trace",
    r"Root Reason for Existing Formulation",
    r"Preservation of Core Invariants",
]

HEADER_PATTERN = re.compile(
    r"(?:#{2,4}|h[2-4]\.)\s*(?:\d+(?:\.\d+)*[.\)]\s*)?Requirement Archaeology & Chesterton's Fence Audit",
    re.IGNORECASE,
)

REQ_ROW_PATTERN = re.compile(
    r"^[+-](?:\|\s*\*\*REQ-([A-Z0-9_-]+)\*\*|#{2,4}\s*REQ-([A-Z0-9_-]+))",
    re.MULTILINE,
)


def get_git_diff(base_ref="develop", target_paths=None):
    """
    Retrieves the git diff for target_paths against base_ref (or merge-base with base_ref).
    If base_ref is not resolvable or in detached HEAD, falls back to diff against HEAD or staged/working tree.
    """
    if target_paths is None:
        target_paths = ["docs/requirements.md", "docs/requirements/"]
    elif isinstance(target_paths, str):
        target_paths = [target_paths]

    if base_ref == "develop":
        try:
            sprint_branches = subprocess.check_output(
                ["git", "for-each-ref", "--format=%(refname:short)", "refs/heads/sprint/*"],
                stderr=subprocess.DEVNULL,
            ).decode("utf-8").strip().splitlines()
            best_sb = None
            best_commit_time = -1
            for sb in sprint_branches:
                ret = subprocess.call(
                    ["git", "merge-base", "--is-ancestor", sb, "HEAD"],
                    stderr=subprocess.DEVNULL,
                )
                if ret == 0:
                    base_ref = sb
                    best_sb = None
                    break
                try:
                    mb = subprocess.check_output(
                        ["git", "merge-base", sb, "HEAD"],
                        stderr=subprocess.DEVNULL,
                    ).decode("utf-8").strip()
                    if mb:
                        ts = int(subprocess.check_output(
                            ["git", "log", "-1", "--format=%ct", mb],
                            stderr=subprocess.DEVNULL,
                        ).decode("utf-8").strip())
                        if ts > best_commit_time:
                            best_commit_time = ts
                            best_sb = sb
                except Exception:
                    pass
            if best_sb:
                base_ref = best_sb
        except Exception:
            pass

    # Try merge-base first
    try:
        mb = subprocess.check_output(
            ["git", "merge-base", base_ref, "HEAD"],
            stderr=subprocess.DEVNULL,
        ).decode("utf-8").strip()
        if mb:
            cmd = ["git", "diff", f"{mb}", "--"] + target_paths
            return subprocess.check_output(cmd, stderr=subprocess.DEVNULL).decode("utf-8")
    except Exception:
        pass

    # Fallback to direct diff with base_ref
    try:
        cmd = ["git", "diff", f"{base_ref}", "--"] + target_paths
        diff = subprocess.check_output(cmd, stderr=subprocess.DEVNULL).decode("utf-8")
        if diff.strip():
            return diff
    except Exception:
        pass

    # Fallback to working tree / uncommitted changes
    try:
        cmd = ["git", "diff", "HEAD", "--"] + target_paths
        diff = subprocess.check_output(cmd, stderr=subprocess.DEVNULL).decode("utf-8")
        return diff
    except Exception:
        return ""


def parse_diff_requirements(diff_text):
    """
    Parses git diff output for docs/requirements.md and docs/requirements/*.md.
    Supports both legacy table rows (| **REQ-XXX**) and multi-line markdown headings (### REQ-XXX:).
    Returns:
      modified_reqs: set of REQ IDs that appear in both deleted (-) and added (+) lines.
      added_reqs: set of REQ IDs that appear ONLY in added (+) lines.
      deleted_reqs: set of REQ IDs that appear ONLY in deleted (-) lines.
    """
    added_ids = set()
    deleted_ids = set()
    current_added_req = None
    current_deleted_req = None

    for line in diff_text.splitlines():
        if line.startswith("+++") or line.startswith("---"):
            continue
        if line.startswith("@@"):
            m_hunk = re.search(r"REQ-([A-Z0-9_-]+)", line)
            current_added_req = f"REQ-{m_hunk.group(1)}" if m_hunk else None
            current_deleted_req = current_added_req
            if current_added_req == "REQ-XXX":
                current_added_req = None
                current_deleted_req = None
            continue
        if line.startswith(" "):
            m_ctx = re.search(r"#{2,4}\s*REQ-([A-Z0-9_-]+)", line)
            if m_ctx:
                current_added_req = f"REQ-{m_ctx.group(1)}"
                current_deleted_req = current_added_req
                if current_added_req == "REQ-XXX":
                    current_added_req = None
                    current_deleted_req = None
            continue

        if line.startswith("+"):
            m = re.search(r"\|\s*\*\*REQ-([A-Z0-9_-]+)\*\*", line)
            if not m:
                m = re.search(r"#{2,4}\s*REQ-([A-Z0-9_-]+)(?:[:\s]|$)", line)
            if not m:
                m = re.search(r"\*\*REQ-([A-Z0-9_-]+)\.\d+\*\*", line)
            if m:
                req_id = f"REQ-{m.group(1)}"
                if req_id != "REQ-XXX":
                    added_ids.add(req_id)
                    current_added_req = req_id
                else:
                    current_added_req = None
            elif current_added_req and current_added_req != "REQ-XXX":
                added_ids.add(current_added_req)
        elif line.startswith("-"):
            m = re.search(r"\|\s*\*\*REQ-([A-Z0-9_-]+)\*\*", line)
            if not m:
                m = re.search(r"#{2,4}\s*REQ-([A-Z0-9_-]+)(?:[:\s]|$)", line)
            if not m:
                m = re.search(r"\*\*REQ-([A-Z0-9_-]+)\.\d+\*\*", line)
            if m:
                req_id = f"REQ-{m.group(1)}"
                if req_id != "REQ-XXX":
                    deleted_ids.add(req_id)
                    current_deleted_req = req_id
                else:
                    current_deleted_req = None
            elif current_deleted_req and current_deleted_req != "REQ-XXX":
                deleted_ids.add(current_deleted_req)

    # Modified requirements appear in both added and deleted sets
    modified_reqs = added_ids.intersection(deleted_ids)
    purely_added_reqs = added_ids - modified_reqs
    purely_deleted_reqs = deleted_ids - modified_reqs

    return modified_reqs, purely_added_reqs, purely_deleted_reqs


def validate_archaeology_section(text):
    """
    Validates that text contains a valid Requirement Archaeology & Chesterton's Fence Audit section.
    Returns (is_valid, list_of_errors).
    """
    errors = []

    if not text:
        errors.append("Deliverable text is empty or missing.")
        return False, errors

    header_match = HEADER_PATTERN.search(text)
    if not header_match:
        errors.append(
            "Missing mandatory section header: "
            "'### Requirement Archaeology & Chesterton's Fence Audit' "
            "(or Jira markup 'h3. Requirement Archaeology & Chesterton's Fence Audit')."
        )
        return False, errors

    # Extract text from header match to next header (### or h1./h2./h3.) or end of text
    section_start = header_match.end()
    remaining = text[section_start:]
    next_header_match = re.search(r"(?:\n#{1,3}\s+|\nh[1-3]\.\s+)", remaining)
    section_body = remaining[:next_header_match.start()] if next_header_match else remaining

    for field in MANDATORY_FIELDS:
        # Regex to match bullet or label: e.g.
        # * `Original Requirement ID & Target`: <content>
        # • Original Requirement ID & Target: <content>
        # - Original Requirement ID & Target: <content>
        # *** *Original Requirement ID & Target*: <content>
        pattern = re.compile(
            rf"(?:[\*\-\•\#]+\s*)?(?:\*|`)*{re.escape(field)}(?:\*|`)*\s*[:\-\—]\s*([^\n\r]+)",
            re.IGNORECASE,
        )
        match = pattern.search(section_body)
        if not match:
            # Check if field name exists at all
            if re.search(re.escape(field), section_body, re.IGNORECASE):
                errors.append(f"Field '{field}' is present but has empty or invalid content.")
            else:
                errors.append(f"Missing mandatory field: '{field}'.")
        else:
            val = match.group(1).strip()
            # Clean off surrounding markdown or formatting
            clean_val = re.sub(r"^[`\*_]+|[`\*_]+$", "", val).strip()
            if not clean_val or clean_val.lower() in ["todo", "none", "n/a", "tbd", ""]:
                errors.append(f"Field '{field}' has empty or placeholder content: '{val}'.")

    return len(errors) == 0, errors


ALLOWED_ENGINEERING_ENTRIES = {
    "analysis",
    "gates",
    "metrics",
    "plans",
    "retros",
    "test_specs",
    "walkthroughs",
    "test_results",
    "rule_history_archive.md",
    "workflow_overview.html",
}


def verify_gate_records(sprint_name=None, repo_root=None):
    """
    REQ-PRO-059.4 / TST-PRO-059.2:
    Verifies that sprint quality gate records have been exported to docs/engineering/gates/
    per REQ-PRO-059. Fails with exit code 1 if the expected sprint gate record is missing or empty.
    """
    if repo_root is None:
        repo_root = os.getcwd()

    gates_dir = os.path.join(repo_root, "docs", "engineering", "gates")
    if not os.path.isdir(gates_dir):
        return False, [f"ERROR: Quality gate export directory does not exist: {gates_dir}"]

    if sprint_name:
        clean_name = sprint_name.strip()
        slug = re.sub(r"[/\\ ]+", "_", clean_name).lower()
        if not slug.endswith(".md"):
            slug += ".md"

        candidates = [
            slug,
            f"sprint_{slug}" if not slug.startswith("sprint_") else slug,
            slug.replace("sprint_", "", 1) if slug.startswith("sprint_") else slug,
        ]

        found_file = None
        for cand in candidates:
            cand_path = os.path.join(gates_dir, cand)
            if os.path.isfile(cand_path):
                found_file = cand_path
                break

        if not found_file:
            return False, [
                f"ERROR: Missing sprint quality gate export file for sprint '{sprint_name}'. "
                f"Expected one of {[os.path.join('docs/engineering/gates', c) for c in candidates]}."
            ]

        try:
            with open(found_file, "r", encoding="utf-8") as f:
                content = f.read().strip()
            if not content:
                return False, [f"ERROR: Sprint quality gate export file is empty: {found_file}"]
            if not re.search(r"\b(?:gate|verdict|pass|bounce|revision)\b", content, re.IGNORECASE):
                return False, [f"ERROR: Sprint quality gate export file contains no recognized gate records: {found_file}"]
            return True, [f"PASS: Sprint quality gate records verified for '{sprint_name}': {found_file}"]
        except Exception as e:
            return False, [f"ERROR: Failed to read gate export file {found_file}: {e}"]
    else:
        md_files = [f for f in os.listdir(gates_dir) if f.endswith(".md") and not f.startswith(".")]
        if not md_files:
            return False, [f"ERROR: No sprint quality gate export files (*.md) found in {gates_dir}"]

        valid_files = 0
        for fname in md_files:
            fpath = os.path.join(gates_dir, fname)
            try:
                with open(fpath, "r", encoding="utf-8") as f:
                    content = f.read().strip()
                if content and re.search(r"\b(?:gate|verdict|pass|bounce|revision)\b", content, re.IGNORECASE):
                    valid_files += 1
            except Exception:
                pass

        if valid_files == 0:
            return False, [f"ERROR: No valid, non-empty sprint quality gate export records found in {gates_dir}"]
        return True, [f"PASS: Verified {valid_files} sprint quality gate export record(s) in {gates_dir}."]


def verify_engineering_directory_structure(eng_dir="docs/engineering"):
    """
    Verifies that docs/engineering/ contains only canonical ASPICE directories and approved files
    (no stray folders like summaries/, impl_plans/, test_reports/, implementation/, or stray symlinks).
    """
    if not os.path.isdir(eng_dir):
        return True, []
    unexpected = sorted(set(os.listdir(eng_dir)) - ALLOWED_ENGINEERING_ENTRIES)
    if unexpected:
        return False, [
            f"ERROR: Non-canonical entries found in {eng_dir}/: {', '.join(unexpected)}. "
            f"Allowed entries: {', '.join(sorted(ALLOWED_ENGINEERING_ENTRIES))}."
        ]
    return True, []


def verify_master_table_parity(repo_root=None):
    """
    Verifies that the summary tables in docs/requirements.md and docs/tests.md have 100% mathematical
    parity with actual parsed ### REQ-* and ### TST-* headings across the 9 domain shards (ATT-5431).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    errors = []
    specs = [
        ("docs/requirements.md", "docs/requirements", "REQ"),
        ("docs/tests.md", "docs/tests", "TST"),
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
            m_preamble = re.search(r"links to the (\d+) domain-sharded", line)
            if m_preamble:
                stated_preamble = int(m_preamble.group(1))
                if stated_preamble != len(shard_counts):
                    errors.append(
                        f"{master_rel}:{lno}: Preamble shard count mismatch: "
                        f"Stated={stated_preamble}, Actual filesystem shard count={len(shard_counts)}."
                    )
            m_link = re.search(r"\((?:requirements|tests)/((?:req|tst)_[^)]+\.md)\)", line)
            if m_link:
                s_name = m_link.group(1)
                m_cells = re.search(r"\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*$", line)
                if not m_cells:
                    errors.append(f"{master_rel}:{lno}: Table row for {s_name} missing numeric counts.")
                    continue
                t, v, p = int(m_cells.group(1)), int(m_cells.group(2)), int(m_cells.group(3))
                shards_checked += 1
                if s_name in shard_counts:
                    exp_t, exp_v, exp_p = shard_counts[s_name]
                    if (t, v, p) != (exp_t, exp_v, exp_p):
                        errors.append(
                            f"{master_rel}:{lno}: Count mismatch for {s_name}: "
                            f"Table has Total={t}, Verified={v}, Planned={p}; "
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
                m_shards = re.search(r"across (\d+) domain shards", line)
                if m_shards:
                    reported_shards = int(m_shards.group(1))
                    if reported_shards != len(shard_counts):
                        errors.append(
                            f"{master_rel}:{lno}: Summary shard count mismatch: "
                            f"Reported={reported_shards}, Actual filesystem shard count={len(shard_counts)}."
                        )

        if shards_checked != len(shard_counts):
            errors.append(
                f"{master_rel}: Shard count mismatch: Table checked {shards_checked} shards, "
                f"filesystem has {len(shard_counts)} shards."
            )

    return len(errors) == 0, errors


def recalculate_master_index_tables(repo_root=None):
    """
    Recalculates summary tables ('Total', 'Verified', 'Planned / Open') and total requirement/test
    counts in docs/requirements.md and docs/tests.md based on actual parsed section headings
    in the 9 domain shards under docs/requirements/ and docs/tests/ (ATT-5431, REQ-PRO-041.4).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    specs = [
        ("docs/requirements.md", "docs/requirements", "REQ"),
        ("docs/tests.md", "docs/tests", "TST"),
    ]
    recalculated = []
    for master_rel, shard_dir_rel, prefix in specs:
        abs_shard_dir = os.path.join(repo_root, shard_dir_rel)
        if not os.path.isdir(abs_shard_dir):
            continue
        shard_counts = {}
        for fn in sorted(os.listdir(abs_shard_dir)):
            if not fn.endswith(".md"):
                continue
            fp = os.path.join(abs_shard_dir, fn)
            tot = ver = plan = 0
            with open(fp, "r", encoding="utf-8") as f:
                for line in f:
                    m = re.match(r"^(#{2,4})\s*(" + prefix + r"-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
                    if not m:
                        continue
                    tot += 1
                    if "[Verified]" in line:
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
        grand_verified = sum(c[1] for c in shard_counts.values())
        grand_planned = sum(c[2] for c in shard_counts.values())

        new_lines = []
        changed = False
        for line in master_lines:
            new_line = line
            m_link = re.search(r"\((?:requirements|tests)/((?:req|tst)_[^)]+\.md)\)", line)
            if m_link:
                s_name = m_link.group(1)
                if s_name in shard_counts:
                    t, v, p = shard_counts[s_name]
                    new_line = re.sub(
                        r"\|\s*\d+\s*\|\s*\d+\s*\|\s*\d+\s*\|(\s*)$",
                        rf"| {t} | {v} | {p} |\1",
                        line,
                    )
            elif "links to the " in line:
                new_line = re.sub(
                    r"(links to the )\d+( domain-sharded)",
                    rf"\g<1>{len(shard_counts)}\g<2>",
                    line,
                )
            elif "Total Living " in line:
                new_line = re.sub(
                    r"(\*\*Total Living (?:Requirements|Tests)\*\*:\s*)\d+(\s*across )\d+(\s*domain shards\.)",
                    rf"\g<1>{grand_total}\g<2>{len(shard_counts)}\g<3>",
                    line,
                )
            if new_line != line:
                changed = True
            new_lines.append(new_line)

        if changed:
            with open(master_fp, "w", encoding="utf-8") as f:
                f.writelines(new_lines)
            msg = f"Recalculated master index table in {master_rel}: Total={grand_total}, Verified={grand_verified}, Planned={grand_planned}."
            print(msg)
            recalculated.append(msg)
        else:
            recalculated.append(f"Master index table in {master_rel} is already up to date.")
    return recalculated


ALLOWLIST_COMPOSABLES = {
    "Theme",
    "ATrainingTrackerTheme",
    "TrainingTrackerTheme",
}

GRANDFATHERED_UNGROUNDED_SYMBOLS = {
    "backup_rules.xml",
    "BikeBellAudioSynthesizer.java",
    "MapViewportHelper.kt",
    "SoundEngine.java",
    "RadarThreatView.kt",
    "StravaUploader.java",
    "GoogleDriveUploader.java",
    "auth_credentials_prefs.xml",
    "WorkoutDataHelper.java",
    "WorkoutDataMapper.java",
    "LegacyImportEngine.java",
    "StravaUploadHelper.kt",
    "DatabaseHelper.java",
}

GRANDFATHERED_SYSREQS = {
    "SYSREQ-PER-001",
    "SYSREQ-PER-002",
    "SYSREQ-PER-003",
    "SYSREQ-PER-004",
    "SYSREQ-PER-005",
    "SYSREQ-ACQ-001",
    "SYSREQ-ACQ-002",
    "SYSREQ-ACQ-003",
    "SYSREQ-ACQ-004",
    "SYSREQ-ACQ-005",
    "SYSREQ-ENV-001",
    "SYSREQ-ENV-002",
    "SYSREQ-ENV-003",
}


def find_composable_callers(func_name, search_dir, exclude_file=None):
    """
    Searches search_dir (e.g. app/src/main) for production callers of func_name.
    Excludes the declaration itself and callers located inside @Preview functions.
    """
    callers = []
    if not os.path.isdir(search_dir):
        return callers

    call_pattern = re.compile(rf"\b{re.escape(func_name)}\s*\(")
    decl_pattern = re.compile(rf"\bfun\s+(?:<[^>]+>\s+)?{re.escape(func_name)}\b")

    for root, _, files in os.walk(search_dir):
        for f in files:
            if not (f.endswith(".kt") or f.endswith(".java")):
                continue
            fp = os.path.join(root, f)
            is_same_file = bool(exclude_file and os.path.normpath(fp) == os.path.normpath(exclude_file))

            try:
                with open(fp, "r", encoding="utf-8", errors="ignore") as fh:
                    content = fh.read()
            except Exception:
                continue

            if not call_pattern.search(content):
                continue

            lines = content.splitlines()
            in_preview = False
            brace_depth = 0
            saw_open_brace = False
            pending_preview = False

            for idx, line in enumerate(lines, 1):
                stripped = line.strip()

                if re.search(r"@(Preview|PreviewLightDark|DevicePreview|PreviewScreenSizes|PreviewParameter)\b", stripped):
                    pending_preview = True

                if re.search(r"\bfun\s+[A-Za-z0-9_]+\s*\(", stripped):
                    if pending_preview or "preview" in stripped.lower():
                        in_preview = True
                        brace_depth = 0
                        saw_open_brace = False
                    pending_preview = False

                if in_preview:
                    open_count = line.count("{")
                    close_count = line.count("}")
                    if open_count > 0:
                        saw_open_brace = True
                    brace_depth += open_count - close_count
                    if saw_open_brace and brace_depth <= 0:
                        in_preview = False
                    continue

                if call_pattern.search(line):
                    if is_same_file and decl_pattern.search(line):
                        continue
                    if stripped.startswith("//") or stripped.startswith("*"):
                        continue
                    callers.append(f"{fp}:{idx}")
                    break
        if callers:
            break
    return callers


def extract_added_composables(diff_text):
    """
    Extracts newly added non-preview, non-private @Composable function names and file paths from a git diff.
    Returns list of (func_name, file_path).
    """
    added_composables = []
    current_file = None
    file_lines = {}

    for line in diff_text.splitlines():
        if line.startswith("+++ b/"):
            current_file = line[6:].strip()
            if current_file.startswith("app/src/main/") and current_file.endswith(".kt"):
                file_lines.setdefault(current_file, [])
        elif current_file and current_file.startswith("app/src/main/") and current_file.endswith(".kt"):
            file_lines[current_file].append(line)

    for fp, lines in file_lines.items():
        pending_preview = False
        pending_composable = False
        for line in lines:
            if not line.startswith("+") or line.startswith("+++"):
                if line.startswith(" ") and not line.strip().startswith("@"):
                    pending_preview = False
                    pending_composable = False
                continue

            stripped = line[1:].strip()
            if not stripped:
                continue

            if re.search(r"@(Preview|PreviewLightDark|DevicePreview|PreviewScreenSizes|PreviewParameter)\b", stripped):
                pending_preview = True

            if "@Composable" in stripped:
                pending_composable = True

            m_fun = re.search(r"\bfun\s+(?:<[^>]+>\s+)?([A-Za-z0-9_]+)\s*\(", stripped)
            if m_fun:
                func_name = m_fun.group(1)
                is_private = bool(re.search(r"\bprivate\s+fun\b", stripped))
                is_preview = pending_preview or bool(re.search(r"@(Preview|PreviewLightDark|DevicePreview)\b", stripped)) or func_name.endswith("Preview")
                is_composable = pending_composable or ("@Composable" in stripped)

                if is_composable and not is_preview and not is_private:
                    if func_name not in ALLOWLIST_COMPOSABLES:
                        added_composables.append((func_name, fp))

                pending_preview = False
                pending_composable = False

    return added_composables


def verify_composable_islands(diff_text=None, repo_root=None, base_ref="develop"):
    """
    Zero-Caller @Composable Island Check (Rule 81 / REQ-PRO-041.5).
    Detects newly added non-@Preview, non-private @Composable declarations in modified
    app/src/main/**/*.kt files and verifies at least one production call site exists in app/src/main/.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    if diff_text is None:
        diff_text = get_git_diff(base_ref=base_ref, target_paths=["app/src/main/"])

    if not diff_text or not diff_text.strip():
        return True, ["PASS: Zero Composable islands detected (no modified UI files)."]

    added_composables = extract_added_composables(diff_text)
    if not added_composables:
        return True, ["PASS: Zero unmounted Composable islands detected in git diff."]

    main_dir = os.path.join(repo_root, "app/src/main")
    errors = []
    checked_count = 0

    for func_name, decl_fp in added_composables:
        checked_count += 1
        full_decl_path = os.path.join(repo_root, decl_fp) if not os.path.isabs(decl_fp) else decl_fp
        callers = find_composable_callers(func_name, main_dir, exclude_file=full_decl_path)
        if not callers:
            errors.append(
                f"Zero-caller @Composable island detected: '{func_name}' in {decl_fp} "
                f"has zero production call sites in app/src/main/ (only preview or uncalled)."
            )

    if errors:
        return False, errors
    return True, [f"PASS: Verified {checked_count} newly added @Composable component(s) have valid production callers."]


def verify_repository_encapsulation(diff_text=None, repo_root=None, base_ref="develop"):
    """
    Repository Encapsulation Guard (Rule 81 / coding_standards.md Sec 2.5 / REQ-PRO-041.5).
    Rejects any non-private method or property exposing *DatabaseManager, *DbHelper,
    SQLiteDatabase, or Cursor in modified/added *Repository.kt and *Repository.java files.
    """
    if diff_text is None:
        diff_text = get_git_diff(base_ref=base_ref, target_paths=["app/src/main/"])

    if not diff_text or not diff_text.strip():
        return True, ["PASS: Zero repository encapsulation violations detected."]

    current_file = None
    errors = []
    prohibited_types_re = r"\b(?:[A-Za-z0-9_]*DatabaseManager|[A-Za-z0-9_]*DbHelper|SQLiteDatabase|Cursor)\b"

    for line in diff_text.splitlines():
        if line.startswith("+++ b/"):
            current_file = line[6:].strip()
            continue
        if not current_file or not (current_file.endswith("Repository.kt") or current_file.endswith("Repository.java")):
            continue
        if not line.startswith("+") or line.startswith("+++"):
            continue

        stripped = line[1:].strip()
        if not stripped or stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*") or stripped.startswith("import "):
            continue

        if stripped.startswith("private ") or " private val " in stripped or " private var " in stripped or " private fun " in stripped:
            continue

        is_fun_returning_prohibited = re.search(
            rf"\bfun\s+[A-Za-z0-9_]+\s*\([^)]*\)\s*:\s*.*?" + prohibited_types_re, stripped
        )
        is_prop_with_prohibited = re.search(
            rf"\b(?:val|var)\s+[A-Za-z0-9_]+\s*:\s*.*?" + prohibited_types_re, stripped
        )
        is_getter_name = re.search(
            r"\bfun\s+get[A-Za-z0-9_]*(?:DatabaseManager|DbHelper|Database|Cursor)\s*\(", stripped
        )
        is_java_prohibited = re.search(
            rf"(?:public|protected)?\s+" + prohibited_types_re + r"\s+(?:get[A-Za-z0-9_]*|[A-Za-z0-9_]+)\s*\(", stripped
        )

        if is_fun_returning_prohibited or is_prop_with_prohibited or is_getter_name or is_java_prohibited:
            errors.append(
                f"Repository encapsulation violation in {current_file}: "
                f"Non-private member '{stripped}' exposes SQLite/DatabaseManager internals."
            )

    if errors:
        return False, errors
    return True, ["PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members)."]


def verify_architecture_and_system_specs(diff_text=None, repo_root=None, base_ref="develop"):
    """
    Architecture & System Spec Grounding & SYSREQ Bidirectional Equality Guard (Rule 81 / REQ-PRO-041.5).
    Verifies that every backticked source file in modified architecture/system markdown files exists
    in the repository and that declared SYSREQ-* IDs match allocated IDs.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    if diff_text is None:
        diff_text = get_git_diff(
            base_ref=base_ref,
            target_paths=["docs/architecture.md", "docs/architecture/", "docs/system/"],
        )

    if not diff_text or not diff_text.strip():
        return True, ["PASS: Zero architecture or system specification modifications detected."]

    errors = []
    current_file = None
    sysreq_files_modified = False

    # Index existing filenames in repository for fast basename lookup
    all_repo_basenames = set()
    for root, _, files in os.walk(repo_root):
        if ".git" in root:
            continue
        for f in files:
            all_repo_basenames.add(f)

    for line in diff_text.splitlines():
        if line.startswith("+++ b/"):
            current_file = line[6:].strip()
            if current_file.startswith("docs/system"):
                sysreq_files_modified = True
            continue

        if not current_file or not (current_file.startswith("docs/architecture") or current_file.startswith("docs/system")):
            continue

        if not line.startswith("+") or line.startswith("+++"):
            continue

        # Extract backticked filenames
        ticks = re.findall(r"`([^`\n]+\.(?:kt|java|xml))`", line[1:])
        for t in ticks:
            if t in GRANDFATHERED_UNGROUNDED_SYMBOLS:
                continue
            direct_path = os.path.join(repo_root, t)
            base_name = os.path.basename(t)
            if not os.path.isfile(direct_path) and base_name not in all_repo_basenames:
                errors.append(
                    f"Ungrounded architecture/system symbol in {current_file}: "
                    f"'{t}' does not resolve to an authentic file in the repository."
                )

    if sysreq_files_modified:
        declared_ids = set()
        sysreq_dir = os.path.join(repo_root, "docs/system")
        if os.path.isdir(sysreq_dir):
            for fn in os.listdir(sysreq_dir):
                if fn.startswith("sysreq_") and fn.endswith(".md"):
                    with open(os.path.join(sysreq_dir, fn), "r", encoding="utf-8") as fh:
                        for m in re.finditer(r"SYSREQ-[A-Z0-9_-]+", fh.read()):
                            clean_id = m.group(0).rstrip("-_")
                            if clean_id != "SYSREQ-XXX":
                                declared_ids.add(clean_id)

        allocated_ids = set()
        sys_arch_fp = os.path.join(repo_root, "docs/system/system_architecture.md")
        if os.path.isfile(sys_arch_fp):
            with open(sys_arch_fp, "r", encoding="utf-8") as fh:
                for m in re.finditer(r"SYSREQ-[A-Z0-9_-]+", fh.read()):
                    clean_id = m.group(0).rstrip("-_")
                    if clean_id != "SYSREQ-XXX":
                        allocated_ids.add(clean_id)

        missing_allocation = declared_ids - allocated_ids
        missing_declaration = (allocated_ids - declared_ids) - GRANDFATHERED_SYSREQS

        if missing_allocation:
            errors.append(
                f"SYSREQ allocation mismatch: {', '.join(sorted(missing_allocation))} "
                f"declared in docs/system/sysreq_*.md but missing from allocation in docs/system/system_architecture.md."
            )
        if missing_declaration:
            errors.append(
                f"SYSREQ declaration mismatch: {', '.join(sorted(missing_declaration))} "
                f"allocated in docs/system/system_architecture.md but missing declaration in docs/system/sysreq_*.md."
            )

    if errors:
        return False, errors
    return True, ["PASS: Architecture & system specification code-grounding and SYSREQ parity verified."]


def verify_governance(deliverable_text=None, diff_text=None, base_ref="develop", skip_tripwires=False):
    """
    Main governance validation entrypoint.
    Enforces engineering directory structure, master table parity, Rule 81 deterministic tripwires
    (composable islands, repository encapsulation, architecture/system spec grounding), and
    Chesterton's Fence archaeology on modified requirements.
    Returns (success: bool, diagnostics: list[str]).
    """
    diagnostics = []

    dir_ok, dir_errors = verify_engineering_directory_structure()
    if not dir_ok:
        diagnostics.extend(dir_errors)
        return False, diagnostics

    parity_ok, parity_errors = verify_master_table_parity()
    if not parity_ok:
        diagnostics.extend([f"ERROR: {e}" for e in parity_errors])
        return False, diagnostics
    diagnostics.append("PASS: Living documentation master tables match all 9 domain shards with 100% parity.")

    if not skip_tripwires:
        islands_ok, islands_diag = verify_composable_islands(diff_text=diff_text, base_ref=base_ref)
        if not islands_ok:
            diagnostics.extend([f"ERROR: {e}" for e in islands_diag])
            return False, diagnostics
        diagnostics.extend(islands_diag)

        repo_ok, repo_diag = verify_repository_encapsulation(diff_text=diff_text, base_ref=base_ref)
        if not repo_ok:
            diagnostics.extend([f"ERROR: {e}" for e in repo_diag])
            return False, diagnostics
        diagnostics.extend(repo_diag)

        specs_ok, specs_diag = verify_architecture_and_system_specs(diff_text=diff_text, base_ref=base_ref)
        if not specs_ok:
            diagnostics.extend([f"ERROR: {e}" for e in specs_diag])
            return False, diagnostics
        diagnostics.extend(specs_diag)

    if diff_text is None:
        diff_text = get_git_diff(base_ref=base_ref)

    modified_reqs, added_reqs, deleted_reqs = parse_diff_requirements(diff_text)

    if not modified_reqs and not deleted_reqs:
        # No existing requirements modified or deleted
        if added_reqs:
            diagnostics.append(
                f"Net-new requirement(s) detected: {', '.join(sorted(added_reqs))}. "
                f"Bypassing archaeology check cleanly."
            )
        else:
            diagnostics.append("Zero requirement modifications detected.")
        return True, diagnostics

    # Existing requirements were modified or removed
    flagged_reqs = modified_reqs.union(deleted_reqs)
    diagnostics.append(
        f"Modified/Altered existing requirement(s) detected: {', '.join(sorted(flagged_reqs))}."
    )

    if not deliverable_text:
        diagnostics.append(
            "ERROR: Existing requirements were modified, but no deliverable text was provided "
            "for archaeology audit validation."
        )
        return False, diagnostics

    is_valid, errors = validate_archaeology_section(deliverable_text)
    if not is_valid:
        diagnostics.append("ERROR: Requirement Archaeology & Chesterton's Fence Audit failed validation:")
        diagnostics.extend([f"  - {e}" for e in errors])
        return False, diagnostics

    diagnostics.append(
        "PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields."
    )
    return True, diagnostics


def main():
    """CLI interface for verify_requirement_governance.py."""
    import argparse

    parser = argparse.ArgumentParser(
        description="Verify Requirement Archaeology & Chesterton's Fence Governance (REQ-PRO-022)."
    )
    parser.add_argument(
        "--text-file",
        help="Path to file containing deliverable / sub-task description text.",
    )
    parser.add_argument(
        "--diff-file",
        help="Path to file containing git diff of docs/requirements.md (defaults to live git diff).",
    )
    parser.add_argument(
        "--base-ref",
        default="develop",
        help="Git base ref to diff against (default: develop).",
    )
    parser.add_argument(
        "--sync-indexes",
        action="store_true",
        help="Recalculate and synchronize master index tables in docs/requirements.md and docs/tests.md.",
    )
    parser.add_argument(
        "--check-text-only",
        action="store_true",
        help="Validate the archaeology section template directly without diff checks.",
    )
    parser.add_argument(
        "--check-composable-islands",
        action="store_true",
        help="Run zero-caller Composable island check only.",
    )
    parser.add_argument(
        "--check-repo-encapsulation",
        action="store_true",
        help="Run repository encapsulation guard check only.",
    )
    parser.add_argument(
        "--check-specs",
        action="store_true",
        help="Run architecture and system spec grounding & SYSREQ parity check only.",
    )
    parser.add_argument(
        "--skip-tripwires",
        action="store_true",
        help="Skip the 3 deterministic Python tripwires.",
    )
    parser.add_argument(
        "--check-gates",
        nargs="?",
        const="",
        default=None,
        help="Verify sprint quality gate records export in docs/engineering/gates/ (optionally specify sprint name).",
    )

    args = parser.parse_args()

    if args.check_gates is not None:
        target_sprint = args.check_gates if args.check_gates != "" else None
        ok, diag = verify_gate_records(sprint_name=target_sprint)
        for msg in diag:
            print(msg, file=sys.stderr if not ok else sys.stdout)
        sys.exit(0 if ok else 1)

    if args.sync_indexes:
        recalculate_master_index_tables()
        parity_ok, parity_errors = verify_master_table_parity()
        if not parity_ok:
            for e in parity_errors:
                print(f"ERROR: {e}", file=sys.stderr)
            sys.exit(1)
        print("PASS: Master index tables successfully synchronized with 100% parity.")
        sys.exit(0)

    diff_content = None
    if args.diff_file:
        if not os.path.exists(args.diff_file):
            print(f"Error: File {args.diff_file} not found.", file=sys.stderr)
            sys.exit(1)
        with open(args.diff_file, "r", encoding="utf-8") as f:
            diff_content = f.read()

    if args.check_composable_islands:
        ok, diag = verify_composable_islands(diff_text=diff_content, base_ref=args.base_ref)
        for msg in diag:
            print(msg, file=sys.stderr if not ok else sys.stdout)
        sys.exit(0 if ok else 1)

    if args.check_repo_encapsulation:
        ok, diag = verify_repository_encapsulation(diff_text=diff_content, base_ref=args.base_ref)
        for msg in diag:
            print(msg, file=sys.stderr if not ok else sys.stdout)
        sys.exit(0 if ok else 1)

    if args.check_specs:
        ok, diag = verify_architecture_and_system_specs(diff_text=diff_content, base_ref=args.base_ref)
        for msg in diag:
            print(msg, file=sys.stderr if not ok else sys.stdout)
        sys.exit(0 if ok else 1)

    text_content = ""
    if args.text_file:
        if not os.path.exists(args.text_file):
            print(f"Error: File {args.text_file} not found.", file=sys.stderr)
            sys.exit(1)
        with open(args.text_file, "r", encoding="utf-8") as f:
            text_content = f.read()
    elif not sys.stdin.isatty():
        text_content = sys.stdin.read()

    if not text_content:
        # Attempt auto-discovery based on current branch or engineering docs
        try:
            branch = subprocess.check_output(
                ["git", "rev-parse", "--abbrev-ref", "HEAD"],
                stderr=subprocess.DEVNULL,
            ).decode("utf-8").strip()
            ticket_m = re.search(r"(?:feature|bugfix|hotfix)/([A-Z]+-\d+)", branch)
            ticket_key = ticket_m.group(1) if ticket_m else None
            if ticket_key:
                candidates = [
                    f"docs/engineering/test_specs/{ticket_key}_test_spec.md",
                    f"docs/engineering/plans/{ticket_key}_plan.md",
                    f"docs/engineering/walkthroughs/{ticket_key}_walkthrough.md",
                    f"docs/engineering/analysis/{ticket_key}_analysis.md",
                ]
                for cand in candidates:
                    if os.path.exists(cand):
                        with open(cand, "r", encoding="utf-8") as f:
                            text_content = f.read()
                        break
        except Exception:
            pass

    if args.check_text_only:
        valid, errors = validate_archaeology_section(text_content)
        if valid:
            print("PASS: Archaeology section is structurally valid.")
            sys.exit(0)
        else:
            print("FAIL: Archaeology section validation errors:", file=sys.stderr)
            for err in errors:
                print(f"  - {err}", file=sys.stderr)
            sys.exit(1)

    success, diagnostics = verify_governance(
        deliverable_text=text_content,
        diff_text=diff_content,
        base_ref=args.base_ref,
        skip_tripwires=args.skip_tripwires,
    )

    for msg in diagnostics:
        if msg.startswith("ERROR") or msg.startswith("FAIL"):
            print(msg, file=sys.stderr)
        else:
            print(msg)

    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
