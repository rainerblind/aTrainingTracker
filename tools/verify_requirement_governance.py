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

import json
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

# All legacy duplicates fully resolved in ATT-6123 (SWE.1 BP5, SWE.6 BP4, REQ-PRO-066.6)
LEGACY_DUPLICATE_IDS = frozenset()

# Known legacy dangling references pending backfill in A-08.x (ATT-6127..6135, SWE.1 BP5, SWE.6 BP4, REQ-PRO-070)
LEGACY_DANGLING_TST_REFS = frozenset({
    "TST-FUSION-001",
    "TST-FUSION-002",
    "TST-FUSION-003",
    "TST-FUSION-004",
    "TST-INT-007",
    "TST-STR-010",
    "TST-UI-041",
    "TST-UI-044",
})

LEGACY_DANGLING_REQ_REFS = frozenset({
    "REQ-MAP-037",
    "REQ-STB-013",
    "REQ-TRK-035",
    "REQ-UI-002",
    "REQ-UI-008",
    "REQ-UI-009",
    "REQ-UI-010",
    "REQ-UI-062",
    "REQ-UI-135",
    "REQ-UI-166",
    "REQ-UI-289",
})


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


IMPACT_HEADER_PATTERN = re.compile(
    r"(?:^|\n)\s*(?:#{1,3}\s+|h[1-3]\.\s+)(?:\d+\.\s*)?(?:Change(?:[- ]?Request)?\s+)?Impact\s+Analysis.*",
    re.IGNORECASE,
)

IMPACT_DIMENSIONS = [
    {
        "name": "Affected Requirements",
        "patterns": [
            r"Affected\s+Requirements?",
            r"Requirements?\s+Impact",
            r"Affected\s+REQs?",
        ],
    },
    {
        "name": "Affected Architecture Elements",
        "patterns": [
            r"Affected\s+Architecture(?:\s+Elements)?",
            r"Architecture\s+Impact",
            r"Affected\s+Components?",
        ],
    },
    {
        "name": "Verification Impact & Test Surface",
        "patterns": [
            r"Verification\s+Impact(?:\s*&\s*Test\s+Surface)?",
            r"Affected\s+Tests?(?:\s*&\s*Verification\s+Impact)?",
            r"Affected\s+Test\s+Cases?",
            r"Test\s+Surface",
            r"Test\s+Impact",
        ],
    },
    {
        "name": "Risk Assessment & Invariants",
        "patterns": [
            r"Risk\s+Assessment(?:\s*&\s*(?:Invariants|Mitigations))?",
            r"Risks?(?:\s*&\s*(?:Invariants|Mitigations))?",
        ],
    },
]


def validate_impact_analysis_section(text):
    """
    REQ-PRO-064.2 / TST-PRO-064.1:
    Validates that deliverable text contains a valid Change-Request Impact Analysis section.
    Checks that the section header exists and that all 4 mandatory dimensions:
      1. Affected Requirements
      2. Affected Architecture Elements
      3. Verification Impact & Test Surface
      4. Risk Assessment & Invariants
    are present with non-empty, non-placeholder content.
    Returns (is_valid: bool, errors: list[str]).
    """
    errors = []
    if not text or not text.strip():
        errors.append("Deliverable text is empty or missing.")
        return False, errors

    header_match = IMPACT_HEADER_PATTERN.search(text)
    if not header_match:
        errors.append(
            "Missing mandatory section header: '## Change-Request Impact Analysis' "
            "(or '## Impact Analysis' / 'h2. Impact Analysis')."
        )
        return False, errors

    section_start = header_match.end()
    remaining = text[section_start:]
    next_header_match = re.search(r"(?:\n#{1,3}\s+|\nh[1-3]\.\s+)", remaining)
    section_body = remaining[:next_header_match.start()] if next_header_match else remaining

    for dim in IMPACT_DIMENSIONS:
        dim_name = dim["name"]
        patterns = dim["patterns"]
        found = False
        matched_val = None
        for p in patterns:
            dim_regex = re.compile(
                rf"(?:[\*\-\•\#]+\s*)?(?:\*|`)*(?:{p})(?:\*|`)*\s*[:\-\—]\s*([^\n\r]+)",
                re.IGNORECASE,
            )
            m = dim_regex.search(section_body)
            if m:
                found = True
                matched_val = m.group(1).strip()
                break

        if not found:
            label_found = False
            for p in patterns:
                if re.search(rf"(?:{p})", section_body, re.IGNORECASE):
                    label_found = True
                    break
            if label_found:
                errors.append(f"Dimension '{dim_name}' is present but has empty or invalid content.")
            else:
                errors.append(f"Missing mandatory impact dimension: '{dim_name}'.")
        else:
            clean_val = re.sub(r"^[`\*_]+|[`\*_]+$", "", matched_val).strip()
            placeholder_tokens = {"todo", "tbd", "none", "n/a", "null", "undefined", ""}
            if not clean_val or clean_val.lower() in placeholder_tokens or clean_val.lower() in {"[req-xxx]", "[todo]", "[tbd]", "[none]", "[n/a]"}:
                errors.append(f"Dimension '{dim_name}' has empty or placeholder content: '{matched_val}'.")

    return len(errors) == 0, errors


def verify_stage1_impact_analysis(analysis_file=None, repo_root=None):
    """
    REQ-PRO-064.2 / TST-PRO-064.2:
    Verifies that a Stage 1 analysis deliverable satisfies Change-Request Impact Analysis governance.
    If analysis_file is specified, validates that file.
    If not specified, discovers the latest analysis file from docs/engineering/analysis/.
    """
    if repo_root is None:
        repo_root = os.getcwd()

    if analysis_file:
        target_path = analysis_file if os.path.isabs(analysis_file) else os.path.join(repo_root, analysis_file)
        if not os.path.exists(target_path):
            return False, [f"ERROR: Analysis file not found: {target_path}"]
        try:
            with open(target_path, "r", encoding="utf-8") as f:
                content = f.read()
        except Exception as e:
            return False, [f"ERROR: Failed to read analysis file {target_path}: {e}"]

        valid, errors = validate_impact_analysis_section(content)
        if not valid:
            return False, [f"FAIL: Change-Request Impact Analysis in {os.path.basename(target_path)} failed validation:"] + [f"  - {e}" for e in errors]
        return True, [f"PASS: Change-Request Impact Analysis verified in {os.path.basename(target_path)}."]
    else:
        analysis_dir = os.path.join(repo_root, "docs", "engineering", "analysis")
        if not os.path.isdir(analysis_dir):
            return False, [f"ERROR: Analysis directory not found: {analysis_dir}"]
        md_files = [f for f in os.listdir(analysis_dir) if f.endswith(".md")]
        if not md_files:
            return False, [f"ERROR: No analysis files found in {analysis_dir}"]
        newest_file = max(md_files, key=lambda f: os.path.getmtime(os.path.join(analysis_dir, f)))
        return verify_stage1_impact_analysis(os.path.join(analysis_dir, newest_file), repo_root=repo_root)


ALLOWED_ENGINEERING_ENTRIES = {
    "analysis",
    "gates",
    "metrics",
    "plans",
    "qa_audits",
    "retros",
    "reviews",
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


def verify_sprint_metrics(sprint_name=None, repo_root=None):
    """
    REQ-PRO-060.3 / TST-PRO-060.2:
    Verifies that sprint metrics have been recorded in docs/engineering/metrics/process_metrics.jsonl.
    Validates:
    - File exists and is non-empty.
    - Every line is valid JSON conforming to the schema (sprint_id, sprint_name, throughput, quality_gates).
    - If sprint_name is specified, verifies that an entry for that sprint exists and has:
      total_story_points > 0 and completed_story_points > 0.
    - If sprint_name is not specified, verifies that at least one valid entry exists, and that the latest
      entry has total_story_points > 0 and completed_story_points > 0.
    Fails with exit code 1 if the verification fails.
    """
    if repo_root is None:
        repo_root = os.getcwd()

    metrics_file = os.path.join(repo_root, "docs", "engineering", "metrics", "process_metrics.jsonl")
    if not os.path.isfile(metrics_file):
        return False, [f"ERROR: Sprint process metrics file does not exist: {metrics_file}"]

    try:
        with open(metrics_file, "r", encoding="utf-8") as f:
            lines = [line.strip() for line in f if line.strip()]
    except Exception as e:
        return False, [f"ERROR: Failed to read sprint process metrics file {metrics_file}: {e}"]

    if not lines:
        return False, [f"ERROR: Sprint process metrics file is empty: {metrics_file}"]

    entries = []
    required_keys = {"sprint_id", "sprint_name", "throughput", "quality_gates"}
    for idx, line in enumerate(lines, start=1):
        try:
            entry = json.loads(line)
        except Exception as e:
            return False, [f"ERROR: Malformed JSON at line {idx} in {metrics_file}: {e}"]
        if not isinstance(entry, dict) or not required_keys.issubset(entry.keys()):
            return False, [f"ERROR: Line {idx} in {metrics_file} missing required schema fields {required_keys - entry.keys()}"]

        # Validate optional problem_trends schema when present (REQ-PRO-063.4)
        pt = entry.get("problem_trends")
        if pt is not None:
            pt_keys = {"total_bugs", "open_bugs", "closed_bugs", "by_severity"}
            if not isinstance(pt, dict) or not pt_keys.issubset(pt.keys()):
                missing = pt_keys - pt.keys() if isinstance(pt, dict) else pt_keys
                return False, [f"ERROR: Line {idx} in {metrics_file} missing required problem_trends fields {missing}"]
            if not isinstance(pt.get("by_severity"), dict):
                return False, [f"ERROR: Line {idx} in {metrics_file} has invalid by_severity mapping in problem_trends"]

        entries.append(entry)

    if sprint_name:
        clean_target = sprint_name.strip()
        clean_target = re.sub(r"^sprint[/_]", "", clean_target)
        found_entry = None
        for entry in entries:
            s_name = str(entry.get("sprint_name", "")).strip()
            s_id = str(entry.get("sprint_id", "")).strip()
            if clean_target in (s_name, s_id, f"sprint_{s_name}", f"sprint/{s_name}") or s_name == clean_target:
                found_entry = entry
                break
        if not found_entry:
            return False, [f"ERROR: Missing sprint metrics record for sprint '{sprint_name}' in {metrics_file}"]

        throughput = found_entry.get("throughput", {})
        total_sp = float(throughput.get("total_story_points", 0.0) or 0.0)
        completed_sp = float(throughput.get("completed_story_points", 0.0) or 0.0)
        if total_sp <= 0.0 or completed_sp <= 0.0:
            return False, [
                f"ERROR: Sprint metrics for '{sprint_name}' have invalid story points: "
                f"total_story_points={total_sp}, completed_story_points={completed_sp} (expected > 0)."
            ]
        return True, [
            f"PASS: Sprint metrics verified for '{sprint_name}': "
            f"total_story_points={total_sp}, completed_story_points={completed_sp}."
        ]
    else:
        latest = entries[-1]
        throughput = latest.get("throughput", {})
        total_sp = float(throughput.get("total_story_points", 0.0) or 0.0)
        completed_sp = float(throughput.get("completed_story_points", 0.0) or 0.0)
        if total_sp <= 0.0 or completed_sp <= 0.0:
            return False, [
                f"ERROR: Latest sprint metrics entry ('{latest.get('sprint_name')}') has invalid story points: "
                f"total_story_points={total_sp}, completed_story_points={completed_sp} (expected > 0)."
            ]
        return True, [
            f"PASS: Verified {len(entries)} sprint metrics record(s) in {metrics_file}. "
            f"Latest sprint '{latest.get('sprint_name')}' has total_story_points={total_sp}, completed_story_points={completed_sp}."
        ]


def verify_qa_audits(sprint_name=None, repo_root=None):
    """
    REQ-PRO-061.4 / TST-PRO-061.2:
    Verifies that sprint QA process-compliance audit records exist in docs/engineering/qa_audits/
    per REQ-PRO-061. Fails with exit code 1 if:
    - qa_audits directory does not exist.
    - Expected sprint audit file is missing or empty.
    - Audit record does not contain recognized compliance verdicts ('COMPLIANT' or 'NON-COMPLIANT').
    - Audit record does not contain at least 2 sampled ticket evaluations (REQ-PRO-061.1).
    """
    if repo_root is None:
        repo_root = os.getcwd()

    qa_dir = os.path.join(repo_root, "docs", "engineering", "qa_audits")
    if not os.path.isdir(qa_dir):
        return False, [f"ERROR: QA audit reports directory does not exist: {qa_dir}"]

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
            cand_path = os.path.join(qa_dir, cand)
            if os.path.isfile(cand_path):
                found_file = cand_path
                break

        if not found_file:
            return False, [
                f"ERROR: Missing sprint QA process-compliance audit file for sprint '{sprint_name}'. "
                f"Expected one of {[os.path.join('docs/engineering/qa_audits', c) for c in candidates]}."
            ]

        try:
            with open(found_file, "r", encoding="utf-8") as f:
                content = f.read().strip()
            if not content:
                return False, [f"ERROR: Sprint QA audit report is empty: {found_file}"]
            if not re.search(r"\b(?:COMPLIANT|NON-COMPLIANT)\b", content, re.IGNORECASE):
                return False, [f"ERROR: Sprint QA audit report contains no recognized compliance verdicts: {found_file}"]

            ticket_sections = re.findall(
                r"(?:###\s*(?:Sampled\s+)?Ticket:?\s*([A-Z0-9_-]+)|\|\s*([A-Z0-9_-]+)\s*\|\s*(?:PASS|FAIL|COMPLIANT|NON-COMPLIANT))",
                content,
                re.IGNORECASE,
            )
            sampled_tickets = set(t[0] or t[1] for t in ticket_sections if (t[0] or t[1]) and not (t[0] or t[1]).startswith("ATT-232"))
            if len(sampled_tickets) == 0:
                all_keys = set(re.findall(r"\b(?:ATT|PROJ)-\d+\b", content))
                sampled_tickets = {k for k in all_keys if not k.startswith("ATT-232")}

            total_sampled = len(sampled_tickets)
            if total_sampled < 2:
                return False, [
                    f"ERROR: Sprint QA audit report in {found_file} sampled only {total_sampled} ticket(s) (minimum >= 2 required by REQ-PRO-061.1)."
                ]

            return True, [f"PASS: Sprint QA audit report verified for '{sprint_name}' ({total_sampled} sampled tickets): {found_file}"]
        except Exception as e:
            return False, [f"ERROR: Failed to read QA audit file {found_file}: {e}"]
    else:
        md_files = [f for f in os.listdir(qa_dir) if f.endswith(".md") and not f.startswith(".")]
        if not md_files:
            return False, [f"ERROR: No sprint QA process-compliance audit files (*.md) found in {qa_dir}"]

        valid_files = 0
        for fname in md_files:
            fpath = os.path.join(qa_dir, fname)
            try:
                with open(fpath, "r", encoding="utf-8") as f:
                    content = f.read().strip()
                if content and re.search(r"\b(?:COMPLIANT|NON-COMPLIANT)\b", content, re.IGNORECASE):
                    valid_files += 1
            except Exception:
                pass

        if valid_files == 0:
            return False, [f"ERROR: No valid, non-empty sprint QA audit records found in {qa_dir}"]
        return True, [f"PASS: Verified {valid_files} sprint QA process-compliance audit record(s) in {qa_dir}."]


def verify_independent_reviews(sprint_name=None, repo_root=None):
    """
    REQ-PRO-062.4 / TST-PRO-062.2:
    Verifies that independent QA review records (Human Ceremony 2 or Cross-Vendor AI)
    exist in docs/engineering/reviews/ and satisfy schema requirements:
    - Traceable reviewer metadata (Reviewer, Date, Scope, Findings/Verdict).
    """
    if repo_root is None:
        repo_root = os.getcwd()

    rev_dir = os.path.join(repo_root, "docs", "engineering", "reviews")
    if not os.path.isdir(rev_dir):
        return False, [f"ERROR: Independent reviews directory does not exist: {rev_dir}"]

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
            cand_path = os.path.join(rev_dir, cand)
            if os.path.isfile(cand_path):
                found_file = cand_path
                break

        # Also search for files containing the sprint name
        if not found_file:
            for fname in os.listdir(rev_dir):
                if fname.endswith(".md") and not fname.startswith("."):
                    fpath = os.path.join(rev_dir, fname)
                    try:
                        with open(fpath, "r", encoding="utf-8") as f:
                            if clean_name.lower() in f.read().lower():
                                found_file = fpath
                                break
                    except Exception:
                        pass

        if not found_file:
            return False, [
                f"ERROR: Missing independent review record for sprint '{sprint_name}'. "
                f"Expected one of {[os.path.join('docs/engineering/reviews', c) for c in candidates]}."
            ]

        try:
            with open(found_file, "r", encoding="utf-8") as f:
                content = f.read().strip()
            if not content:
                return False, [f"ERROR: Independent review record is empty: {found_file}"]

            required_patterns = [
                (r"(?:\*\*Reviewer\*\*|Reviewer:?)\s*([^\n\r]+)", "Reviewer"),
                (r"(?:\*\*Date\*\*|Date:?)\s*([^\n\r]+)", "Date"),
                (r"(?:\*\*Scope\*\*|Scope:?)\s*([^\n\r]+)", "Scope"),
                (r"(?:\*\*Verdict\*\*|Verdict|Process Verdict)[\s:\*]+(PASS|FAIL|ACCEPTED|COMPLIANT)", "Verdict"),
            ]
            for pat, field_name in required_patterns:
                if not re.search(pat, content, re.IGNORECASE):
                    return False, [f"ERROR: Independent review record in {found_file} is missing required metadata field: {field_name}"]

            return True, [f"PASS: Independent review record verified for '{sprint_name}': {found_file}"]
        except Exception as e:
            return False, [f"ERROR: Failed to read review record file {found_file}: {e}"]
    else:
        md_files = [f for f in os.listdir(rev_dir) if f.endswith(".md") and not f.startswith(".")]
        if not md_files:
            return False, [f"ERROR: No independent review records (*.md) found in {rev_dir}"]

        valid_files = 0
        for fname in md_files:
            fpath = os.path.join(rev_dir, fname)
            try:
                with open(fpath, "r", encoding="utf-8") as f:
                    content = f.read().strip()
                if (
                    content
                    and re.search(r"(?:\*\*Reviewer\*\*|Reviewer:?)", content, re.IGNORECASE)
                    and re.search(r"(?:\*\*Verdict\*\*|Verdict:?)", content, re.IGNORECASE)
                ):
                    valid_files += 1
            except Exception:
                pass

        if valid_files == 0:
            return False, [f"ERROR: No valid, non-empty independent review records found in {rev_dir}"]
        return True, [f"PASS: Verified {valid_files} independent review record(s) in {rev_dir}."]




def verify_engineering_directory_structure(eng_dir="docs/engineering"):
    """
    Verifies that docs/engineering/ contains only canonical ASPICE directories and approved files
    (no stray folders like summaries/, impl_plans/, test_reports/, implementation/, or stray symlinks),
    and enforces symlink integrity for test_results (Rule 67, REQ-PRO-067.3).
    """
    if not os.path.isdir(eng_dir):
        return True, []
    unexpected = sorted(set(os.listdir(eng_dir)) - ALLOWED_ENGINEERING_ENTRIES)
    if unexpected:
        return False, [
            f"ERROR: Non-canonical entries found in {eng_dir}/: {', '.join(unexpected)}. "
            f"Allowed entries: {', '.join(sorted(ALLOWED_ENGINEERING_ENTRIES))}."
        ]
    test_results_path = os.path.join(eng_dir, "test_results")
    if os.path.lexists(test_results_path):
        if not os.path.islink(test_results_path):
            return False, [
                f"ERROR: {test_results_path} must be a symbolic link (Rule 67, REQ-PRO-067.3)."
            ]
        target = os.readlink(test_results_path)
        expected_target = "../../app/build/reports/tests/testDebugUnitTest"
        if target != expected_target:
            return False, [
                f"ERROR: {test_results_path} symlink target '{target}' does not match expected canonical target '{expected_target}' (Rule 67, REQ-PRO-067.3)."
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


def find_duplicate_heading_ids(repo_root=None):
    """
    Scans docs/requirements/*.md and docs/tests/*.md for requirement and test headings.
    Returns a dict mapping duplicate ID -> list of (relative_file_path, line_number) tuples.
    Only entries with len(occurrences) > 1 are included in the returned dict (REQ-PRO-066.1).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    specs = [
        ("docs/requirements", "REQ"),
        ("docs/tests", "TST"),
    ]
    seen = {}
    for shard_dir_rel, prefix in specs:
        abs_shard_dir = os.path.join(repo_root, shard_dir_rel)
        if not os.path.isdir(abs_shard_dir):
            continue
        for fn in sorted(os.listdir(abs_shard_dir)):
            if not fn.endswith(".md"):
                continue
            rel_path = os.path.join(shard_dir_rel, fn)
            abs_fp = os.path.join(abs_shard_dir, fn)
            with open(abs_fp, "r", encoding="utf-8") as f:
                for lno, line in enumerate(f, 1):
                    m = re.match(r"^(#{2,4})\s*(" + prefix + r"-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
                    if m:
                        item_id = m.group(2)
                        seen.setdefault(item_id, []).append((rel_path, lno))

    return {item_id: locs for item_id, locs in sorted(seen.items()) if len(locs) > 1}


def verify_unique_ids(repo_root=None, fail_on_duplicates=False, legacy_allowlist=None):
    """
    Verifies that all ### REQ-* and ### TST-* heading IDs are unique (REQ-PRO-066 / TST-PRO-066).
    Reports every duplicate with file and line.

    If fail_on_duplicates is True (or --enforce-unique / --fail-on-duplicates CLI flag):
      Fails if ANY duplicate is detected, reporting file and line for each occurrence.
    If fail_on_duplicates is False (default):
      Known legacy duplicates present in legacy_allowlist (or LEGACY_DUPLICATE_IDS) are reported
      as WARNINGs without failing validation, preserving CI green status until ATT-6123.
      Any NEW duplicate ID (not in the allowlist) causes validation to fail.

    Returns (success: bool, diagnostics: list[str]).
    """
    if legacy_allowlist is None:
        legacy_allowlist = LEGACY_DUPLICATE_IDS

    duplicates = find_duplicate_heading_ids(repo_root=repo_root)
    diagnostics = []

    if not duplicates:
        diagnostics.append("PASS: All REQ and TST heading IDs are unique across living documentation shards.")
        return True, diagnostics

    legacy_dups = {k: v for k, v in duplicates.items() if k in legacy_allowlist}
    new_dups = {k: v for k, v in duplicates.items() if k not in legacy_allowlist}

    for item_id, locs in sorted(duplicates.items()):
        loc_str = ", ".join(f"{fp}:{lno}" for fp, lno in locs)
        is_legacy = item_id in legacy_allowlist
        tag = "WARNING (Legacy duplicate)" if (is_legacy and not fail_on_duplicates) else "ERROR (Duplicate ID)"
        diagnostics.append(f"{tag}: {item_id} appears {len(locs)} times: {loc_str}")

    if fail_on_duplicates:
        diagnostics.insert(0, f"ERROR: Duplicate REQ/TST IDs detected ({len(duplicates)} duplicates).")
        return False, diagnostics

    if new_dups:
        diagnostics.insert(
            0,
            f"ERROR: {len(new_dups)} new duplicate REQ/TST ID(s) detected outside legacy allowlist: "
            f"{', '.join(sorted(new_dups.keys()))}."
        )
        return False, diagnostics

    diagnostics.insert(
        0,
        f"PASS: Zero new duplicate REQ/TST IDs detected ({len(legacy_dups)} legacy duplicates tolerated pending ATT-6123)."
    )
    return True, diagnostics


class DanglingReference:
    """Represents a single dangling reference from a living doc entry to a missing heading."""
    def __init__(self, source_file, line_no, source_id, target_id, target_type):
        self.source_file = source_file
        self.line_no = line_no
        self.source_id = source_id
        self.target_id = target_id
        self.target_type = target_type

    def __iter__(self):
        return iter((self.source_file, self.line_no, self.source_id, self.target_id))

    def __getitem__(self, item):
        if isinstance(item, int):
            return (self.source_file, self.line_no, self.source_id, self.target_id)[item]
        return getattr(self, item)

    def __repr__(self):
        return f"DanglingReference({self.source_file}:{self.line_no} [{self.source_id}] -> {self.target_id})"

    def __eq__(self, other):
        if isinstance(other, (tuple, list)):
            return (self.source_file, self.line_no, self.source_id, self.target_id) == tuple(other)
        if isinstance(other, DanglingReference):
            return (self.source_file, self.line_no, self.source_id, self.target_id) == (
                other.source_file,
                other.line_no,
                other.source_id,
                other.target_id,
            )
        return False


class DanglingReferencesList(list):
    """List containing all dangling references, with dictionary and attribute access for backward compatibility."""
    def __init__(self, dangling_tsts=None, dangling_reqs=None):
        tsts = list(dangling_tsts or [])
        reqs = list(dangling_reqs or [])
        super().__init__(tsts + reqs)
        self.dangling_tsts = tsts
        self.dangling_reqs = reqs

    def __getitem__(self, item):
        if item == "dangling_tsts":
            return self.dangling_tsts
        elif item == "dangling_reqs":
            return self.dangling_reqs
        return super().__getitem__(item)

    def get(self, key, default=None):
        if key == "dangling_tsts":
            return self.dangling_tsts
        elif key == "dangling_reqs":
            return self.dangling_reqs
        return default

    def __contains__(self, item):
        if item in ("dangling_tsts", "dangling_reqs"):
            return True
        return super().__contains__(item)


def _load_shard_sources(shards_arg, default_dir_rel, repo_root):
    """
    Normalizes shard inputs into a list of (rel_path, lines) tuples.
    Supports:
      - None (scans default_dir_rel under repo_root)
      - directory path (str)
      - dict: {rel_path: str_content_or_lines}
      - list/tuple of file paths
    """
    if shards_arg is None:
        abs_dir = os.path.join(repo_root, default_dir_rel) if repo_root else default_dir_rel
        if not os.path.isdir(abs_dir):
            return []
        result = []
        for fn in sorted(os.listdir(abs_dir)):
            if fn.endswith(".md"):
                rel_path = os.path.join(default_dir_rel, fn)
                abs_fp = os.path.join(abs_dir, fn)
                with open(abs_fp, "r", encoding="utf-8") as f:
                    result.append((rel_path, f.readlines()))
        return result
    elif isinstance(shards_arg, str) and os.path.isdir(shards_arg):
        result = []
        for fn in sorted(os.listdir(shards_arg)):
            if fn.endswith(".md"):
                rel_path = os.path.join(os.path.basename(shards_arg), fn)
                abs_fp = os.path.join(shards_arg, fn)
                with open(abs_fp, "r", encoding="utf-8") as f:
                    result.append((rel_path, f.readlines()))
        return result
    elif isinstance(shards_arg, dict):
        result = []
        for k, v in sorted(shards_arg.items()):
            if isinstance(v, str):
                lines = v.splitlines(keepends=True)
            else:
                lines = list(v)
            result.append((k, lines))
        return result
    elif isinstance(shards_arg, (list, tuple)):
        result = []
        for item in shards_arg:
            if isinstance(item, str) and os.path.isfile(item):
                with open(item, "r", encoding="utf-8") as f:
                    result.append((item, f.readlines()))
        return result
    return []


def find_dangling_references(req_shards=None, tst_shards=None, repo_root=None):
    """
    Extracts all cited TST IDs from the 'Tests' field of every REQ and verifies they match an existing ### TST-* heading.
    Extracts all cited REQ IDs from the 'Requirements' field of every TST and verifies they match an existing ### REQ-* heading.
    Reports all dangling references with source file, line, and missing target ID (REQ-PRO-070.1, REQ-PRO-070.2).

    Returns a DanglingReferencesList (supports list operations, attribute access (.dangling_tsts, .dangling_reqs),
    and dict key indexing ['dangling_tsts'], ['dangling_reqs']).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    loaded_reqs = _load_shard_sources(req_shards, "docs/requirements", repo_root)
    loaded_tsts = _load_shard_sources(tst_shards, "docs/tests", repo_root)

    # 1. Discover all declared headings
    known_req_ids = set()
    known_tst_ids = set()

    for rel_path, lines in loaded_reqs:
        for line in lines:
            m = re.match(r"^(#{2,4})\s*(REQ-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                known_req_ids.add(m.group(2))

    for rel_path, lines in loaded_tsts:
        for line in lines:
            m = re.match(r"^(#{2,4})\s*(TST-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                known_tst_ids.add(m.group(2))

    dangling_tsts = []
    dangling_reqs = []

    # 2. Extract cited TST IDs in REQ entries
    for rel_path, lines in loaded_reqs:
        cur_req = None
        for lno, line in enumerate(lines, 1):
            m_h = re.match(r"^(#{2,4})\s*(REQ-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m_h:
                cur_req = m_h.group(2)
            m_f = re.match(r"^\s*[-*]\s*\*{0,2}Tests?\*{0,2}\s*:\s*(.*)", line, re.IGNORECASE)
            if m_f:
                val = m_f.group(1)
                cites = re.findall(r"(?<![A-Za-z0-9_-])TST-[A-Z0-9_-]+(?![A-Za-z0-9_-])", val)
                for c in cites:
                    if c not in known_tst_ids:
                        dangling_tsts.append(
                            DanglingReference(
                                source_file=rel_path,
                                line_no=lno,
                                source_id=cur_req or "UNKNOWN",
                                target_id=c,
                                target_type="TST",
                            )
                        )

    # 3. Extract cited REQ IDs in TST entries
    for rel_path, lines in loaded_tsts:
        cur_tst = None
        for lno, line in enumerate(lines, 1):
            m_h = re.match(r"^(#{2,4})\s*(TST-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m_h:
                cur_tst = m_h.group(2)
            m_f = re.match(
                r"^\s*[-*]\s*\*{0,2}(?:Requirements?|Requirement\(s\)|Target Requirement)\*{0,2}\s*:\s*(.*)",
                line,
                re.IGNORECASE,
            )
            if m_f:
                val = m_f.group(1)
                cites = re.findall(r"(?<![A-Za-z0-9_-])REQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])", val)
                for c in cites:
                    if c not in known_req_ids:
                        dangling_reqs.append(
                            DanglingReference(
                                source_file=rel_path,
                                line_no=lno,
                                source_id=cur_tst or "UNKNOWN",
                                target_id=c,
                                target_type="REQ",
                            )
                        )

    return DanglingReferencesList(dangling_tsts=dangling_tsts, dangling_reqs=dangling_reqs)


def verify_dangling_references(
    repo_root=None,
    fail_on_dangling=False,
    legacy_tst_allowlist=None,
    legacy_req_allowlist=None,
    req_shards=None,
    tst_shards=None,
):
    """
    Verifies that all cited REQ and TST references resolve to authentic headings (REQ-PRO-070 / TST-PRO-070).
    Reports all dangling references with source file, line number, source ID, and missing target ID.

    If fail_on_dangling is True (or --enforce-links / --fail-on-dangling CLI flag):
      Fails if ANY dangling reference is detected (including legacy items).
    If fail_on_dangling is False (default):
      Known legacy dangling references present in allowlists are reported as WARNINGs without failing validation.
      Any NEW dangling reference (not in the allowlist) causes validation to fail.

    Returns (success: bool, diagnostics: list[str]).
    """
    if legacy_tst_allowlist is None:
        legacy_tst_allowlist = LEGACY_DANGLING_TST_REFS
    if legacy_req_allowlist is None:
        legacy_req_allowlist = LEGACY_DANGLING_REQ_REFS

    dangling = find_dangling_references(req_shards=req_shards, tst_shards=tst_shards, repo_root=repo_root)
    diagnostics = []

    if not dangling:
        diagnostics.append("PASS: All cited REQ and TST references resolve to authentic headings.")
        return True, diagnostics

    new_dangling = []
    legacy_dangling = []

    for ref in dangling:
        is_legacy = (
            (ref.target_type == "TST" and ref.target_id in legacy_tst_allowlist)
            or (ref.target_type == "REQ" and ref.target_id in legacy_req_allowlist)
        )
        if is_legacy and not fail_on_dangling:
            legacy_dangling.append(ref)
            tag = "WARNING (Legacy dangling reference)"
        else:
            new_dangling.append(ref)
            tag = "ERROR (Dangling reference)"

        diagnostics.append(
            f"{tag}: {ref.source_file}:{ref.line_no} [{ref.source_id}] cites non-existent {ref.target_id}."
        )

    if fail_on_dangling:
        diagnostics.insert(
            0,
            f"ERROR: Dangling REQ/TST references detected ({len(dangling)} references, strict enforcement active)."
        )
        return False, diagnostics

    if new_dangling:
        diagnostics.insert(
            0,
            f"ERROR: {len(new_dangling)} new dangling REQ/TST reference(s) detected outside legacy allowlist: "
            f"{', '.join(sorted(set(r.target_id for r in new_dangling)))}."
        )
        return False, diagnostics

    diagnostics.insert(
        0,
        f"PASS: Zero new dangling REQ/TST references detected ({len(legacy_dangling)} legacy references tolerated pending A-08.x)."
    )
    return True, diagnostics


class CoverageCategoryResult:
    """Represents a single coverage gap category with counts, totals, and percentages (REQ-PRO-071)."""
    def __init__(self, name, description, covered_count, total_count, gap_count, percentage, gap_items=None, details=None):
        self.name = name
        self.description = description
        self.covered_count = covered_count
        self.total_count = total_count
        self.gap_count = gap_count
        self.percentage = float(percentage)
        self.gap_items = list(gap_items or [])
        self.details = dict(details or {})

    def to_dict(self):
        return {
            "name": self.name,
            "description": self.description,
            "covered_count": self.covered_count,
            "total_count": self.total_count,
            "gap_count": self.gap_count,
            "percentage": round(self.percentage, 2),
            "gap_items": self.gap_items,
            "details": self.details,
        }

    def __getitem__(self, item):
        if hasattr(self, item):
            return getattr(self, item)
        d = self.to_dict()
        if item in d:
            return d[item]
        raise KeyError(item)

    def get(self, key, default=None):
        try:
            return self[key]
        except KeyError:
            return default

    def __repr__(self):
        return (
            f"CoverageCategoryResult({self.name}: {self.covered_count}/{self.total_count} "
            f"({self.percentage:.2f}%), gap={self.gap_count})"
        )


class TraceabilityCoverageReport:
    """Comprehensive traceability coverage report across all 4 categories (REQ-PRO-071)."""
    def __init__(self, cat1, cat2, cat3, cat4, metadata=None):
        self.cat1 = cat1
        self.cat2 = cat2
        self.cat3 = cat3
        self.cat4 = cat4
        self.metadata = dict(metadata or {})

    def to_dict(self):
        return {
            "category_1_reqs_without_tst": self.cat1.to_dict(),
            "category_2_tsts_without_code": self.cat2.to_dict(),
            "category_3_code_tags_without_spec": self.cat3.to_dict(),
            "category_4_tsts_not_cited_back": self.cat4.to_dict(),
            "summary": {
                "total_reqs": self.cat1.total_count,
                "reqs_with_tst": self.cat1.covered_count,
                "req_tst_coverage_pct": round(self.cat1.percentage, 2),
                "total_tsts": self.cat2.total_count,
                "tsts_with_code": self.cat2.covered_count,
                "tst_code_coverage_pct": round(self.cat2.percentage, 2),
                "total_code_tsts": self.cat3.total_count,
                "code_tags_with_spec": self.cat3.covered_count,
                "code_tags_without_spec": self.cat3.gap_count,
                "code_spec_match_pct": round(self.cat3.percentage, 2),
                "tsts_cited_back": self.cat4.covered_count,
                "bidirectional_coverage_pct": round(self.cat4.percentage, 2),
            },
            "metadata": self.metadata,
        }

    def __getitem__(self, item):
        d = self.to_dict()
        if item in d:
            return d[item]
        aliases = {
            "cat1": self.cat1,
            "cat2": self.cat2,
            "cat3": self.cat3,
            "cat4": self.cat4,
            "category_1": self.cat1,
            "category_2": self.cat2,
            "category_3": self.cat3,
            "category_4": self.cat4,
        }
        if item in aliases:
            return aliases[item]
        raise KeyError(item)

    def get(self, key, default=None):
        try:
            return self[key]
        except KeyError:
            return default


def extract_test_code_tags(test_sources=None, repo_root=None):
    """
    Extracts all test code tags (TST-*) across test source files.
    Supports:
      - None (scans app/src/test under repo_root)
      - directory path (str)
      - dict: {rel_path: str_content_or_lines}
      - list/tuple: file paths or (rel_path, content_or_lines) tuples
    Normalizes method sub-tags (e.g. TST-XXX-001_1, TST-XXX-001.1, TST-XXX-001-A) to their base TST-XXX-001 ID.
    Returns dict mapping base TST ID to list of occurrences: {base_id: [{"file": fp, "line": lno, "raw_tag": raw}]}.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    files_to_scan = []
    if test_sources is None:
        test_dir = os.path.join(repo_root, "app/src/test")
        if os.path.isdir(test_dir):
            for root, dirs, files in os.walk(test_dir):
                for f in sorted(files):
                    if f.endswith(".kt") or f.endswith(".java"):
                        abs_fp = os.path.join(root, f)
                        rel_fp = os.path.relpath(abs_fp, repo_root)
                        with open(abs_fp, "r", encoding="utf-8", errors="ignore") as fh:
                            files_to_scan.append((rel_fp, fh.readlines()))
    elif isinstance(test_sources, str) and os.path.isdir(test_sources):
        for root, dirs, files in os.walk(test_sources):
            for f in sorted(files):
                if f.endswith(".kt") or f.endswith(".java"):
                    abs_fp = os.path.join(root, f)
                    rel_fp = os.path.relpath(abs_fp, repo_root) if repo_root else abs_fp
                    with open(abs_fp, "r", encoding="utf-8", errors="ignore") as fh:
                        files_to_scan.append((rel_fp, fh.readlines()))
    elif isinstance(test_sources, dict):
        for k, v in sorted(test_sources.items()):
            lines = v.splitlines(keepends=True) if isinstance(v, str) else list(v)
            files_to_scan.append((k, lines))
    elif isinstance(test_sources, (list, tuple)):
        for item in test_sources:
            if isinstance(item, tuple) and len(item) == 2:
                k, v = item
                lines = v.splitlines(keepends=True) if isinstance(v, str) else list(v)
                files_to_scan.append((k, lines))
            elif isinstance(item, str) and os.path.isfile(item):
                with open(item, "r", encoding="utf-8", errors="ignore") as fh:
                    files_to_scan.append((item, fh.readlines()))

    tag_pattern = re.compile(r"(?<![A-Za-z0-9_-])(TST-[A-Z]+-[0-9]+(?:[\._-][A-Za-z0-9_-]+)*)")
    base_pattern = re.compile(r"^(TST-[A-Z]+-[0-9]+)")

    code_tags = {}
    for rel_fp, lines in files_to_scan:
        for lno, line in enumerate(lines, 1):
            for m in tag_pattern.finditer(line):
                raw = m.group(1).rstrip(".")
                m_base = base_pattern.match(raw)
                base_id = m_base.group(1) if m_base else raw
                code_tags.setdefault(base_id, []).append({
                    "file": rel_fp,
                    "line": lno,
                    "raw_tag": raw,
                })

    return code_tags


def calculate_traceability_coverage(
    req_shards=None,
    tst_shards=None,
    test_sources=None,
    repo_root=None,
):
    """
    Computes bidirectional REQ-TST traceability and test code existence coverage across 4 categories (REQ-PRO-071):
      - Category 1: REQs without TST citation (REQ-PRO-071.1)
      - Category 2: TSTs without test code (REQ-PRO-071.2)
      - Category 3: Code tags without spec (REQ-PRO-071.3)
      - Category 4: TSTs not cited back by their REQ (REQ-PRO-071.4)
    Returns TraceabilityCoverageReport.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    loaded_reqs = _load_shard_sources(req_shards, "docs/requirements", repo_root)
    loaded_tsts = _load_shard_sources(tst_shards, "docs/tests", repo_root)

    # 1. Discover all declared headings
    known_req_ids = set()
    known_tst_ids = set()

    for rel_path, lines in loaded_reqs:
        for line in lines:
            m = re.match(r"^(#{2,4})\s*(REQ-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                known_req_ids.add(m.group(2))

    for rel_path, lines in loaded_tsts:
        for line in lines:
            m = re.match(r"^(#{2,4})\s*(TST-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                known_tst_ids.add(m.group(2))

    # 2. Extract cited TSTs in REQs' Tests field
    req_to_tsts = {r: set() for r in known_req_ids}
    for rel_path, lines in loaded_reqs:
        cur_req = None
        for line in lines:
            m = re.match(r"^(#{2,4})\s*(REQ-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                cur_req = m.group(2)
            m_f = re.match(r"^\s*[-*]\s*\*{0,2}Tests?\*{0,2}\s*:\s*(.*)", line, re.IGNORECASE)
            if m_f and cur_req:
                cites = re.findall(r"(?<![A-Za-z0-9_-])TST-[A-Z0-9_-]+(?![A-Za-z0-9_-])", m_f.group(1))
                req_to_tsts[cur_req].update(cites)

    # 3. Extract cited REQs in TSTs' Requirements field
    tst_to_reqs = {t: set() for t in known_tst_ids}
    for rel_path, lines in loaded_tsts:
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
                cites = re.findall(r"(?<![A-Za-z0-9_-])REQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])", m_f.group(1))
                tst_to_reqs[cur_tst].update(cites)

    # 4. Scan test code tags
    code_tsts = extract_test_code_tags(test_sources=test_sources, repo_root=repo_root)

    # Category 1: REQs without TST
    reqs_without_tst = sorted([r for r in known_req_ids if not req_to_tsts.get(r)])
    total_reqs = len(known_req_ids)
    covered_reqs = total_reqs - len(reqs_without_tst)
    pct_cat1 = (covered_reqs / total_reqs * 100.0) if total_reqs > 0 else 100.0
    cat1 = CoverageCategoryResult(
        name="Category 1",
        description="REQs citing at least one TST in 'Tests' field",
        covered_count=covered_reqs,
        total_count=total_reqs,
        gap_count=len(reqs_without_tst),
        percentage=pct_cat1,
        gap_items=reqs_without_tst,
    )

    # Category 2: TSTs without test code
    tsts_without_code = sorted([t for t in known_tst_ids if t not in code_tsts])
    total_tsts = len(known_tst_ids)
    covered_tsts = total_tsts - len(tsts_without_code)
    pct_cat2 = (covered_tsts / total_tsts * 100.0) if total_tsts > 0 else 100.0
    cat2 = CoverageCategoryResult(
        name="Category 2",
        description="TST specifications with matching test code tag in test directory",
        covered_count=covered_tsts,
        total_count=total_tsts,
        gap_count=len(tsts_without_code),
        percentage=pct_cat2,
        gap_items=tsts_without_code,
    )

    # Category 3: Code tags without spec
    code_tags_without_spec = sorted([b for b in code_tsts.keys() if b not in known_tst_ids])
    total_code_tsts = len(code_tsts)
    covered_code_tags = total_code_tsts - len(code_tags_without_spec)
    pct_cat3 = (covered_code_tags / total_code_tsts * 100.0) if total_code_tsts > 0 else 100.0
    cat3 = CoverageCategoryResult(
        name="Category 3",
        description="Code tags with matching TST heading in living documentation",
        covered_count=covered_code_tags,
        total_count=total_code_tsts,
        gap_count=len(code_tags_without_spec),
        percentage=pct_cat3,
        gap_items=code_tags_without_spec,
        details={b: code_tsts[b] for b in code_tags_without_spec},
    )

    # Category 4: TSTs not cited back by their REQ
    tsts_not_cited_back = []
    tst_back_details = {}
    for t in sorted(known_tst_ids):
        cited = tst_to_reqs.get(t, set())
        if not cited:
            tsts_not_cited_back.append(t)
            tst_back_details[t] = ["<NO_CITED_REQS>"]
        else:
            unciting = [r for r in sorted(cited) if t not in req_to_tsts.get(r, set())]
            if unciting:
                tsts_not_cited_back.append(t)
                tst_back_details[t] = unciting

    covered_bidirectional = total_tsts - len(tsts_not_cited_back)
    pct_cat4 = (covered_bidirectional / total_tsts * 100.0) if total_tsts > 0 else 100.0
    cat4 = CoverageCategoryResult(
        name="Category 4",
        description="TST specifications cited back by all of their referenced REQs",
        covered_count=covered_bidirectional,
        total_count=total_tsts,
        gap_count=len(tsts_not_cited_back),
        percentage=pct_cat4,
        gap_items=tsts_not_cited_back,
        details=tst_back_details,
    )

    return TraceabilityCoverageReport(
        cat1=cat1,
        cat2=cat2,
        cat3=cat3,
        cat4=cat4,
        metadata={"repo_root": repo_root},
    )


def format_coverage_markdown(report):
    """Formats a TraceabilityCoverageReport into a GitHub-style Markdown matrix."""
    lines = [
        "# ASPICE Traceability & Verification Coverage Report (REQ-PRO-071)",
        "",
        "| Category | Metric Description | Covered / Compliant | Total | Gap Count | Coverage % |",
        "| :--- | :--- | :--- | :--- | :--- | :--- |",
        f"| **Category 1** | REQs with TST Citation | {report.cat1.covered_count} | {report.cat1.total_count} | {report.cat1.gap_count} | {report.cat1.percentage:.2f}% |",
        f"| **Category 2** | TSTs with Test Code | {report.cat2.covered_count} | {report.cat2.total_count} | {report.cat2.gap_count} | {report.cat2.percentage:.2f}% |",
        f"| **Category 3** | Code Tags with Valid Spec | {report.cat3.covered_count} | {report.cat3.total_count} | {report.cat3.gap_count} | {report.cat3.percentage:.2f}% |",
        f"| **Category 4** | TSTs Cited Back by REQs | {report.cat4.covered_count} | {report.cat4.total_count} | {report.cat4.gap_count} | {report.cat4.percentage:.2f}% |",
        "",
        "### Category Summaries:",
        f"* **Category 1 (REQs without TST)**: {report.cat1.gap_count} requirements cite no test specification in their 'Tests' field.",
        f"* **Category 2 (TSTs without test code)**: {report.cat2.gap_count} test specifications have no matching test method in the test directory.",
    ]

    if report.cat3.gap_count > 0:
        sample = ", ".join(report.cat3.gap_items[:15])
        suffix = f" ... (+{report.cat3.gap_count - 15} more)" if report.cat3.gap_count > 15 else ""
        lines.append(f"* **Category 3 (Code tags without spec)**: {report.cat3.gap_count} unique test tags in test code have no living document heading: {sample}{suffix}.")
    else:
        lines.append("* **Category 3 (Code tags without spec)**: 0 code tags without spec (100% matched).")

    lines.append(
        f"* **Category 4 (TSTs not cited back)**: {report.cat4.gap_count} test specifications are not cited back by their referenced requirements."
    )
    return "\n".join(lines)


def format_coverage_json(report):
    """Formats a TraceabilityCoverageReport as a formatted JSON string."""
    return json.dumps(report.to_dict(), indent=2)


def verify_coverage_thresholds(
    report,
    min_req_tst_coverage=None,
    min_tst_code_coverage=None,
    max_code_without_spec=None,
    min_bidirectional_coverage=None,
):
    """
    Evaluates coverage metrics against optional ratcheting thresholds (REQ-PRO-071.5).
    Returns (success: bool, diagnostics: list[str]).
    """
    diagnostics = []
    failures = []

    if min_req_tst_coverage is not None:
        if report.cat1.percentage < min_req_tst_coverage:
            msg = (
                f"ERROR: Gate violated: REQ-TST coverage {report.cat1.percentage:.2f}% is below threshold "
                f"{min_req_tst_coverage:.2f}% (Category 1: {report.cat1.covered_count}/{report.cat1.total_count})."
            )
            failures.append(msg)
            diagnostics.append(msg)
        else:
            diagnostics.append(
                f"PASS: REQ-TST coverage {report.cat1.percentage:.2f}% meets threshold {min_req_tst_coverage:.2f}%."
            )

    if min_tst_code_coverage is not None:
        if report.cat2.percentage < min_tst_code_coverage:
            msg = (
                f"ERROR: Gate violated: TST-to-code coverage {report.cat2.percentage:.2f}% is below threshold "
                f"{min_tst_code_coverage:.2f}% (Category 2: {report.cat2.covered_count}/{report.cat2.total_count})."
            )
            failures.append(msg)
            diagnostics.append(msg)
        else:
            diagnostics.append(
                f"PASS: TST-to-code coverage {report.cat2.percentage:.2f}% meets threshold {min_tst_code_coverage:.2f}%."
            )

    if max_code_without_spec is not None:
        if report.cat3.gap_count > max_code_without_spec:
            msg = (
                f"ERROR: Gate violated: Code tags without spec count {report.cat3.gap_count} exceeds maximum allowed "
                f"{max_code_without_spec} (Category 3)."
            )
            failures.append(msg)
            diagnostics.append(msg)
        else:
            diagnostics.append(
                f"PASS: Code tags without spec count {report.cat3.gap_count} within maximum allowed {max_code_without_spec}."
            )

    if min_bidirectional_coverage is not None:
        if report.cat4.percentage < min_bidirectional_coverage:
            msg = (
                f"ERROR: Gate violated: Bidirectional REQ-TST coverage {report.cat4.percentage:.2f}% is below threshold "
                f"{min_bidirectional_coverage:.2f}% (Category 4: {report.cat4.covered_count}/{report.cat4.total_count})."
            )
            failures.append(msg)
            diagnostics.append(msg)
        else:
            diagnostics.append(
                f"PASS: Bidirectional REQ-TST coverage {report.cat4.percentage:.2f}% meets threshold {min_bidirectional_coverage:.2f}%."
            )

    if failures:
        return False, diagnostics

    if not diagnostics:
        diagnostics.append("PASS: Coverage calculated successfully (no thresholds configured).")
    return True, diagnostics



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


def verify_governance(
    deliverable_text=None,
    diff_text=None,
    base_ref="develop",
    skip_tripwires=False,
    fail_on_duplicates=False,
    fail_on_dangling=False,
    min_req_tst_coverage=None,
    min_tst_code_coverage=None,
    max_code_without_spec=None,
    min_bidirectional_coverage=None,
):
    """
    Main governance validation entrypoint.
    Enforces engineering directory structure, master table parity, unique requirement and test IDs,
    bidirectional reference integrity (REQ-PRO-070), optional coverage threshold ratchets (REQ-PRO-071),
    Rule 81 deterministic tripwires (composable islands, repository encapsulation, architecture/system
    spec grounding), and Chesterton's Fence archaeology on modified requirements.
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

    unique_ok, unique_diag = verify_unique_ids(fail_on_duplicates=fail_on_duplicates)
    if not unique_ok:
        diagnostics.extend(unique_diag)
        return False, diagnostics
    diagnostics.extend(unique_diag)

    links_ok, links_diag = verify_dangling_references(fail_on_dangling=fail_on_dangling)
    if not links_ok:
        diagnostics.extend(links_diag)
        return False, diagnostics
    diagnostics.extend(links_diag)

    if (
        min_req_tst_coverage is not None
        or min_tst_code_coverage is not None
        or max_code_without_spec is not None
        or min_bidirectional_coverage is not None
    ):
        cov_report = calculate_traceability_coverage()
        cov_ok, cov_diag = verify_coverage_thresholds(
            cov_report,
            min_req_tst_coverage=min_req_tst_coverage,
            min_tst_code_coverage=min_tst_code_coverage,
            max_code_without_spec=max_code_without_spec,
            min_bidirectional_coverage=min_bidirectional_coverage,
        )
        diagnostics.extend(cov_diag)
        if not cov_ok:
            return False, diagnostics

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
    parser.add_argument(
        "--check-metrics",
        nargs="?",
        const="",
        default=None,
        help="Verify sprint process metrics in docs/engineering/metrics/process_metrics.jsonl (optionally specify sprint name).",
    )
    parser.add_argument(
        "--check-qa-audits",
        nargs="?",
        const="",
        default=None,
        help="Verify sprint QA process-compliance audit reports in docs/engineering/qa_audits/ (optionally specify sprint name).",
    )
    parser.add_argument(
        "--check-reviews",
        nargs="?",
        const="",
        default=None,
        help="Verify independent QA review records in docs/engineering/reviews/ (optionally specify sprint name).",
    )
    parser.add_argument(
        "--check-impact",
        nargs="?",
        const="",
        default=None,
        help="Verify Change-Request Impact Analysis in Stage 1 deliverable (optionally specify analysis file path).",
    )
    parser.add_argument(
        "--enforce-unique",
        "--fail-on-duplicates",
        dest="enforce_unique",
        action="store_true",
        help="Strictly fail if any duplicate REQ/TST heading IDs exist, including legacy duplicates (REQ-PRO-066.3).",
    )
    parser.add_argument(
        "--check-unique-ids",
        action="store_true",
        help="Check living documentation shards for duplicate REQ and TST heading IDs (REQ-PRO-066.1).",
    )
    parser.add_argument(
        "--enforce-links",
        "--fail-on-dangling",
        dest="enforce_links",
        action="store_true",
        help="Strictly fail if any dangling REQ or TST references exist, including legacy references (REQ-PRO-070.4).",
    )
    parser.add_argument(
        "--check-links",
        "--check-dangling-references",
        action="store_true",
        help="Check living documentation shards for dangling REQ and TST citations (REQ-PRO-070.1, REQ-PRO-070.2).",
    )
    parser.add_argument(
        "--coverage",
        action="store_true",
        help="Report bidirectional REQ-TST coverage and TST-to-test-code existence matrix (REQ-PRO-071).",
    )
    parser.add_argument(
        "--coverage-json",
        action="store_true",
        help="Output coverage report in structured JSON format (REQ-PRO-071.5).",
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
        "--max-code-without-spec",
        type=int,
        default=None,
        help="Maximum allowed count of code tags without specification in living docs.",
    )
    parser.add_argument(
        "--min-bidirectional-coverage",
        type=float,
        default=None,
        help="Minimum required percentage of TSTs cited back by their REQ (0-100).",
    )

    args = parser.parse_args()

    if (
        args.coverage
        or args.coverage_json
        or args.min_req_tst_coverage is not None
        or args.min_tst_code_coverage is not None
        or args.max_code_without_spec is not None
        or args.min_bidirectional_coverage is not None
    ):
        report = calculate_traceability_coverage()
        ok, diag = verify_coverage_thresholds(
            report,
            min_req_tst_coverage=args.min_req_tst_coverage,
            min_tst_code_coverage=args.min_tst_code_coverage,
            max_code_without_spec=args.max_code_without_spec,
            min_bidirectional_coverage=args.min_bidirectional_coverage,
        )
        if args.coverage_json:
            out_dict = report.to_dict()
            out_dict["gate_result"] = {"passed": ok, "diagnostics": diag}
            print(json.dumps(out_dict, indent=2))
        else:
            print(format_coverage_markdown(report))
            print("")
            for msg in diag:
                print(msg, file=sys.stderr if not ok else sys.stdout)
        sys.exit(0 if ok else 1)

    if args.check_links:
        ok, diag = verify_dangling_references(fail_on_dangling=args.enforce_links)
        for msg in diag:
            print(msg, file=sys.stderr if not ok else sys.stdout)
        sys.exit(0 if ok else 1)

    if args.check_unique_ids:
        ok, diag = verify_unique_ids(fail_on_duplicates=args.enforce_unique)
        for msg in diag:
            print(msg, file=sys.stderr if not ok else sys.stdout)
        sys.exit(0 if ok else 1)

    if args.check_impact is not None:
        target_file = args.check_impact.strip() if args.check_impact.strip() else None
        ok, diag = verify_stage1_impact_analysis(analysis_file=target_file)
        for msg in diag:
            print(msg, file=sys.stderr if not ok else sys.stdout)
        sys.exit(0 if ok else 1)

    if args.check_gates is not None:
        target_sprint = args.check_gates if args.check_gates != "" else None
        ok, diag = verify_gate_records(sprint_name=target_sprint)
        for msg in diag:
            print(msg, file=sys.stderr if not ok else sys.stdout)
        sys.exit(0 if ok else 1)

    if args.check_metrics is not None:
        target_sprint = args.check_metrics if args.check_metrics != "" else None
        ok, diag = verify_sprint_metrics(sprint_name=target_sprint)
        for msg in diag:
            print(msg, file=sys.stderr if not ok else sys.stdout)
        sys.exit(0 if ok else 1)

    if args.check_qa_audits is not None:
        target_sprint = args.check_qa_audits if args.check_qa_audits != "" else None
        ok, diag = verify_qa_audits(sprint_name=target_sprint)
        for msg in diag:
            print(msg, file=sys.stderr if not ok else sys.stdout)
        sys.exit(0 if ok else 1)

    if args.check_reviews is not None:
        target_sprint = args.check_reviews if args.check_reviews != "" else None
        ok, diag = verify_independent_reviews(sprint_name=target_sprint)
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
        unique_ok, unique_diag = verify_unique_ids(fail_on_duplicates=args.enforce_unique)
        if not unique_ok:
            for d in unique_diag:
                print(d, file=sys.stderr)
            sys.exit(1)
        links_ok, links_diag = verify_dangling_references(fail_on_dangling=args.enforce_links)
        if not links_ok:
            for d in links_diag:
                print(d, file=sys.stderr)
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
            ticket_m = re.search(r"([A-Z]+-\d+)", branch)
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
        fail_on_duplicates=args.enforce_unique,
        fail_on_dangling=args.enforce_links,
        min_req_tst_coverage=args.min_req_tst_coverage,
        min_tst_code_coverage=args.min_tst_code_coverage,
        max_code_without_spec=args.max_code_without_spec,
        min_bidirectional_coverage=args.min_bidirectional_coverage,
    )

    for msg in diagnostics:
        if msg.startswith("ERROR") or msg.startswith("FAIL"):
            print(msg, file=sys.stderr)
        else:
            print(msg)

    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
