#!/usr/bin/env python3
"""
repair_requirement_shards.py

Deterministic repair tool for docs/requirements/*.md shards (ATT-6125, REQ-PRO-075).
1. Restores truncated sentence fragments in Tests fields using pre-sharding baseline (06990c88~1) and test shards.
2. Moves misplaced Kotlin/Java/XML source files from Tests fields to Implementation fields,
   and assigns the corresponding valid backticked TST-* ID(s) to Tests.
3. Cleans trailing inline metadata (e.g. '| **Tickets**: ...') from Tests fields.
"""

import glob
import os
import re
import subprocess
import sys

# Ensure tools/ is on python path to import split_markdown_table_row
tools_dir = os.path.dirname(os.path.abspath(__file__))
if tools_dir not in sys.path:
    sys.path.insert(0, tools_dir)
from shard_living_docs import split_markdown_table_row


def get_history_baseline(repo_root):
    """
    Parses 06990c88~1:docs/requirements.md to extract pristine requirements.
    """
    cmd = ["git", "show", "06990c88~1:docs/requirements.md"]
    res = subprocess.run(cmd, cwd=repo_root, capture_output=True, text=True, check=True)
    lines = res.stdout.splitlines()

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

    baseline = {}
    for row in logical_rows:
        parts = split_markdown_table_row(row)
        if len(parts) < 6:
            continue
        req_id_m = re.search(r"REQ-[A-Z0-9_-]+", parts[0])
        if not req_id_m:
            continue
        req_id = req_id_m.group(0)

        if len(parts) >= 7:
            # 7-column table: [0] ID, [1] Title, [2] Description, [3] Rationale, [4] Implementation, [5] Tests, [6] Status
            impl_raw = parts[4]
            tests_raw = parts[5]
            status_raw = parts[6]
        else:
            # 6-column table: [0] ID, [1] Description, [2] Rationale, [3] Implementation, [4] Tests, [5] Status
            impl_raw = parts[3]
            tests_raw = parts[4]
            status_raw = parts[5]

        # Extract TST IDs from tests_raw
        tsts = re.findall(r"TST-[A-Z0-9_-]+", tests_raw)
        formatted_tests = ", ".join(f"`{t}`" for t in tsts) if tsts else None

        # Clean impl
        impl_cleaned = impl_raw.strip().replace("<br>", " ")

        baseline[req_id] = {
            "tests_raw": tests_raw.strip(),
            "tst_ids": tsts,
            "tests_formatted": formatted_tests,
            "impl": impl_cleaned,
        }

    return baseline


def get_test_shard_cites(repo_root):
    """
    Scans docs/tests/tst_*.md to build REQ -> list of citing TST IDs.
    """
    tst_files = sorted(glob.glob(os.path.join(repo_root, "docs/tests/tst_*.md")))
    req_to_tsts = {}

    for f in tst_files:
        with open(f, "r", encoding="utf-8") as fp:
            lines = fp.readlines()
        cur_tst = None
        for line in lines:
            m = re.match(r"^(#{2,4})\s*(TST-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                cur_tst = m.group(2)
            m_f = re.match(
                r"^\s*[-*]\s*\*{0,2}(?:Requirements?|Requirement\(s\)|Target Requirement)\*{0,2}\s*:\s*(.*)",
                line,
                re.IGNORECASE,
            )
            if m_f and cur_tst:
                reqs = re.findall(r"(?<![A-Za-z0-9_-])REQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])", m_f.group(1))
                for r in reqs:
                    req_to_tsts.setdefault(r, []).append(cur_tst)

    # Deduplicate preserving order
    for r in req_to_tsts:
        seen = set()
        deduped = []
        for t in req_to_tsts[r]:
            if t not in seen:
                seen.add(t)
                deduped.append(t)
        req_to_tsts[r] = deduped

    return req_to_tsts


def repair_shards(repo_root=None, dry_run=False):
    if repo_root is None:
        repo_root = os.getcwd()

    baseline = get_history_baseline(repo_root)
    req_to_tsts = get_test_shard_cites(repo_root)

    req_files = sorted(glob.glob(os.path.join(repo_root, "docs/requirements/req_*.md")))
    valid_tst_exact = re.compile(
        r"^(`TST-[A-Z0-9_-]+`|\b(None|Planned|Review|Manual inspection)\b)(,\s*(`TST-[A-Z0-9_-]+`|\b(None|Planned|Review|Manual inspection)\b))*$",
        re.IGNORECASE,
    )

    stats = {
        "files_modified": 0,
        "source_files_moved": 0,
        "truncated_restored": 0,
        "tickets_stripped": 0,
        "total_reqs": 0,
    }

    # Special manual overrides for REQs that have specific historical/design contexts
    manual_overrides = {
        "REQ-DAT-010": "`Planned`",
        "REQ-MAP-036": "`TST-MAP-036`",
        "REQ-UI-011": "`TST-UI-001`, `TST-UI-011`, `TST-UI-012`, `TST-UI-013`, `TST-UI-014`, `TST-UI-015`, `TST-UI-016`, `TST-UI-017`, `TST-UI-018`, `TST-UI-019`, `TST-UI-020`, `TST-LOG-001`, `TST-LOG-002`",
    }

    for fpath in req_files:
        with open(fpath, "r", encoding="utf-8") as fp:
            content = fp.read()

        # Parse sections
        # Match lines
        lines = content.splitlines(keepends=True)
        new_lines = []
        file_modified = False

        i = 0
        while i < len(lines):
            line = lines[i]
            m_req = re.match(r"^(#{2,4})\s*(\[)?(REQ-[A-Z0-9_-]+)(\])?:?\s*(.*?)$", line.strip())
            if not m_req:
                new_lines.append(line)
                i += 1
                continue

            req_id = m_req.group(3)
            stats["total_reqs"] += 1

            # We are at a requirement heading. Collect its lines until the next heading or EOF
            block_lines = [line]
            i += 1
            while i < len(lines):
                next_line = lines[i]
                if re.match(r"^(#{2,4})\s*(\[)?REQ-[A-Z0-9_-]+", next_line.strip()):
                    break
                block_lines.append(next_line)
                i += 1

            # Process block_lines
            # Find Tests line and Implementation line
            tests_idx = None
            impl_idx = None
            for idx, bl in enumerate(block_lines):
                if re.match(r"^\s*[-*]\s*\*{0,2}Tests?\*{0,2}\s*:", bl, re.IGNORECASE):
                    tests_idx = idx
                elif re.match(r"^\s*[-*]\s*\*{0,2}Implementation\*{0,2}\s*:", bl, re.IGNORECASE):
                    impl_idx = idx

            if tests_idx is None:
                new_lines.extend(block_lines)
                continue

            tests_line = block_lines[tests_idx]
            m_tval = re.match(r"^(\s*[-*]\s*\*{0,2}Tests?\*{0,2}\s*:\s*)(.*)", tests_line, re.IGNORECASE)
            if not m_tval:
                new_lines.extend(block_lines)
                continue

            prefix_t = m_tval.group(1)
            tests_val = m_tval.group(2).strip()

            if req_id in manual_overrides and tests_val != manual_overrides[req_id]:
                block_lines[tests_idx] = f"- **Tests**: {manual_overrides[req_id]}\n"
                file_modified = True
                new_lines.extend(block_lines)
                continue

            # Check if tests_val is already perfectly conforming
            # But wait: if it contains source files or trailing tickets, it is not conforming
            is_valid = bool(valid_tst_exact.match(tests_val))

            if is_valid and not re.search(r"\.(kt|java|xml)\b", tests_val):
                new_lines.extend(block_lines)
                continue

            # Need repair for this REQ!
            file_modified = True

            # Case A: Does tests_val have trailing '| **Tickets**: ...' or similar?
            clean_val = tests_val
            if "| **Ticket" in clean_val or "| **Tickets" in clean_val:
                clean_val = re.split(r"\|\s*\*{0,2}Tickets?", clean_val)[0].strip()
                stats["tickets_stripped"] += 1

            # Check if clean_val is valid now
            if valid_tst_exact.match(clean_val) and not re.search(r"\.(kt|java|xml)\b", clean_val):
                block_lines[tests_idx] = f"- **Tests**: {clean_val}\n"
                new_lines.extend(block_lines)
                continue

            # Case B: Source files in Tests field
            has_source_files = bool(re.search(r"\.(kt|java|xml)\b", tests_val))
            if has_source_files:
                stats["source_files_moved"] += 1
                source_files_text = tests_val

                # If it had '| **Implementation**: ...' in tests_val (e.g. REQ-DAT-043)
                if "| **Implementation**:" in source_files_text:
                    parts = source_files_text.split("| **Implementation**:")
                    tst_part = parts[0].strip()
                    impl_part = parts[1].strip()
                    resolved_tests = tst_part
                    source_files_text = impl_part
                else:
                    # Resolve tests from baseline or reverse cites
                    if req_id in manual_overrides:
                        resolved_tests = manual_overrides[req_id]
                    elif req_id in baseline and baseline[req_id]["tests_formatted"]:
                        resolved_tests = baseline[req_id]["tests_formatted"]
                    elif req_id in req_to_tsts and req_to_tsts[req_id]:
                        resolved_tests = ", ".join(f"`{t}`" for t in req_to_tsts[req_id])
                    else:
                        resolved_tests = "`Planned`"

                # Update Tests line
                block_lines[tests_idx] = f"- **Tests**: {resolved_tests}\n"

                # Hoist source files to Implementation field
                # If Implementation field exists:
                if impl_idx is not None:
                    curr_impl_line = block_lines[impl_idx]
                    m_ival = re.match(r"^(\s*[-*]\s*\*{0,2}Implementation\*{0,2}\s*:\s*)(.*)", curr_impl_line)
                    curr_impl_val = m_ival.group(2).strip() if m_ival else ""

                    # If curr_impl_val already lists source files, don't duplicate
                    # Or if curr_impl_val was the rationale text shifted into impl (e.g. 7-col shift)
                    # From baseline: if req_id in baseline, baseline[req_id]["impl"] is the exact source files!
                    if req_id in baseline and baseline[req_id]["impl"]:
                        actual_impl = baseline[req_id]["impl"]
                    else:
                        # Extract backticked files from source_files_text
                        files = re.findall(r"`[^`]+\.(?:kt|java|xml)`", source_files_text)
                        if files:
                            actual_impl = ", ".join(files)
                        else:
                            actual_impl = source_files_text

                    block_lines[impl_idx] = f"- **Implementation**: {actual_impl}\n"
                else:
                    # Insert Implementation line right after Tests
                    if req_id in baseline and baseline[req_id]["impl"]:
                        actual_impl = baseline[req_id]["impl"]
                    else:
                        files = re.findall(r"`[^`]+\.(?:kt|java|xml)`", source_files_text)
                        actual_impl = ", ".join(files) if files else source_files_text
                    block_lines.insert(tests_idx + 1, f"- **Implementation**: {actual_impl}\n")

                new_lines.extend(block_lines)
                continue

            # Case C: Truncated sentence fragment / dangling code check in Tests
            stats["truncated_restored"] += 1
            if req_id in manual_overrides:
                resolved_tests = manual_overrides[req_id]
            elif req_id in baseline and baseline[req_id]["tests_formatted"]:
                resolved_tests = baseline[req_id]["tests_formatted"]
            elif req_id in req_to_tsts and req_to_tsts[req_id]:
                resolved_tests = ", ".join(f"`{t}`" for t in req_to_tsts[req_id])
            else:
                # Check if clean_val has any TST-* inside
                tsts = re.findall(r"TST-[A-Z0-9_-]+", clean_val)
                if tsts:
                    resolved_tests = ", ".join(f"`{t}`" for t in tsts)
                else:
                    resolved_tests = "`Planned`"

            block_lines[tests_idx] = f"- **Tests**: {resolved_tests}\n"
            new_lines.extend(block_lines)

        if file_modified:
            stats["files_modified"] += 1
            if not dry_run:
                with open(fpath, "w", encoding="utf-8") as fp:
                    fp.writelines(new_lines)

    return stats


if __name__ == "__main__":
    dry = "--dry-run" in sys.argv
    st = repair_shards(dry_run=dry)
    print(f"Repair complete: {st}")
