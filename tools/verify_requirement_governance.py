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
    r"(?:###|h3\.)\s*Requirement Archaeology & Chesterton's Fence Audit",
    re.IGNORECASE,
)

REQ_ROW_PATTERN = re.compile(
    r"^[+-]\|\s*\*\*REQ-([A-Z0-9_-]+)\*\*",
    re.MULTILINE,
)


def get_git_diff(base_ref="develop", target_file="docs/requirements.md"):
    """
    Retrieves the git diff for target_file against base_ref (or merge-base with base_ref).
    If base_ref is not resolvable or in detached HEAD, falls back to diff against HEAD or staged/working tree.
    """
    # Try merge-base first
    try:
        mb = subprocess.check_output(
            ["git", "merge-base", base_ref, "HEAD"],
            stderr=subprocess.DEVNULL,
        ).decode("utf-8").strip()
        diff = subprocess.check_output(
            ["git", "diff", f"{mb}..HEAD", "--", target_file],
            stderr=subprocess.DEVNULL,
        ).decode("utf-8")
        if diff.strip():
            return diff
    except Exception:
        pass

    # Fallback to direct diff with base_ref
    try:
        diff = subprocess.check_output(
            ["git", "diff", f"{base_ref}..HEAD", "--", target_file],
            stderr=subprocess.DEVNULL,
        ).decode("utf-8")
        if diff.strip():
            return diff
    except Exception:
        pass

    # Fallback to working tree / uncommitted changes
    try:
        diff = subprocess.check_output(
            ["git", "diff", "HEAD", "--", target_file],
            stderr=subprocess.DEVNULL,
        ).decode("utf-8")
        return diff
    except Exception:
        return ""


def parse_diff_requirements(diff_text):
    """
    Parses git diff output for docs/requirements.md.
    Returns:
      modified_reqs: set of REQ IDs that appear in both deleted (-) and added (+) lines.
      added_reqs: set of REQ IDs that appear ONLY in added (+) lines.
      deleted_reqs: set of REQ IDs that appear ONLY in deleted (-) lines.
    """
    added_ids = set()
    deleted_ids = set()

    for line in diff_text.splitlines():
        if line.startswith("+++") or line.startswith("---"):
            continue
        if line.startswith("+"):
            m = re.search(r"\|\s*\*\*REQ-([A-Z0-9_-]+)\*\*", line)
            if m:
                added_ids.add(f"REQ-{m.group(1)}")
        elif line.startswith("-"):
            m = re.search(r"\|\s*\*\*REQ-([A-Z0-9_-]+)\*\*", line)
            if m:
                deleted_ids.add(f"REQ-{m.group(1)}")

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


def verify_governance(deliverable_text=None, diff_text=None, base_ref="develop"):
    """
    Main governance validation entrypoint.
    If diff_text is not provided, extracts diff of docs/requirements.md against base_ref.
    Checks whether existing requirements were modified.
    If yes, enforces archaeology validation on deliverable_text.
    Returns (success: bool, diagnostics: list[str]).
    """
    diagnostics = []

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
        "--check-text-only",
        action="store_true",
        help="Validate the archaeology section template directly without diff checks.",
    )

    args = parser.parse_args()

    text_content = ""
    if args.text_file:
        if not os.path.exists(args.text_file):
            print(f"Error: File {args.text_file} not found.", file=sys.stderr)
            sys.exit(1)
        with open(args.text_file, "r", encoding="utf-8") as f:
            text_content = f.read()
    elif not sys.stdin.isatty():
        text_content = sys.stdin.read()

    diff_content = None
    if args.diff_file:
        if not os.path.exists(args.diff_file):
            print(f"Error: File {args.diff_file} not found.", file=sys.stderr)
            sys.exit(1)
        with open(args.diff_file, "r", encoding="utf-8") as f:
            diff_content = f.read()

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
    )

    for msg in diagnostics:
        if msg.startswith("ERROR") or msg.startswith("FAIL"):
            print(msg, file=sys.stderr)
        else:
            print(msg)

    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
