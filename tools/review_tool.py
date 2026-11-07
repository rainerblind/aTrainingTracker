#!/usr/bin/env python3
"""
tools/review_tool.py

Isolated In-Place Code & Document Review Workflow Helper for Höchstmaß.
Manages file staging into the isolated .reviews/ mini-git repository,
opening in emacsclient, diff harvesting, extracting >> review directives,
and live buffer reload without interfering with parallel background builds.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

DIRECTIVE_PATTERN = re.compile(
    r"(?://|#|<!--|/\*|;)?\s*>>\s*(.+?)(?:\*/|-->)?$",
    re.MULTILINE,
)


def find_repo_root(start_dir="."):
    """Finds the root directory containing .git."""
    curr = Path(start_dir).resolve()
    while curr != curr.parent:
        if (curr / ".git").exists():
            return curr
        curr = curr.parent
    return Path(start_dir).resolve()


def ensure_reviews_env(repo_root=None):
    """
    Ensures .reviews/ exists, has its own initialized git repo,
    and is excluded from the main repository index via .git/info/exclude.
    """
    root = find_repo_root(repo_root) if repo_root else find_repo_root()
    reviews_dir = root / ".reviews"
    reviews_dir.mkdir(parents=True, exist_ok=True)

    # 1. Ensure .reviews/ is in .git/info/exclude
    exclude_file = root / ".git" / "info" / "exclude"
    if exclude_file.exists():
        content = exclude_file.read_text(encoding="utf-8")
        if ".reviews/" not in content and ".reviews" not in content:
            with open(exclude_file, "a", encoding="utf-8") as f:
                f.write("\n.reviews/\n")

    # 2. Ensure .reviews has its own git repo
    git_dir = reviews_dir / ".git"
    if not git_dir.exists():
        subprocess.run(
            ["git", "-C", str(reviews_dir), "init"],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        subprocess.run(
            ["git", "-C", str(reviews_dir), "config", "user.name", "Reviewer"],
            check=True,
        )
        subprocess.run(
            ["git", "-C", str(reviews_dir), "config", "user.email", "reviewer@hoechstmass.local"],
            check=True,
        )

    return reviews_dir


def stage_files(file_paths, open_editor=True, repo_root=None):
    """
    Copies specified files from repo_root to .reviews/, commits baseline,
    and optionally opens them in emacsclient.
    """
    root = find_repo_root(repo_root) if repo_root else find_repo_root()
    reviews_dir = ensure_reviews_env(root)

    staged_relative = []
    for fp in file_paths:
        src = Path(fp)
        if not src.is_absolute():
            src = root / src
        if not src.exists():
            print(f"Warning: Source file {src} not found, skipping.", file=sys.stderr)
            continue

        rel_path = src.relative_to(root)
        dest = reviews_dir / rel_path
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dest)
        staged_relative.append(str(rel_path))

    if not staged_relative:
        print("No valid files to stage.")
        return []

    # Git add and commit in .reviews/
    subprocess.run(
        ["git", "-C", str(reviews_dir), "add", "."],
        check=True,
    )
    commit_res = subprocess.run(
        ["git", "-C", str(reviews_dir), "commit", "-m", f"Baseline: {', '.join(staged_relative)}", "--allow-empty"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )

    print(f"Staged {len(staged_relative)} file(s) into .reviews/:")
    for s in staged_relative:
        print(f"  • .reviews/{s}")

    if open_editor:
        open_in_emacs([str(reviews_dir / s) for s in staged_relative])

    return staged_relative


def stage_ticket(ticket_key, base_ref="develop", open_editor=True, repo_root=None):
    """
    Detects modified/changed files for the given ticket or current branch
    and stages them into .reviews/.
    """
    root = find_repo_root(repo_root) if repo_root else find_repo_root()
    cmd = ["git", "diff", "--name-only", f"{base_ref}...HEAD"]
    try:
        res = subprocess.run(
            cmd,
            cwd=str(root),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=True,
        )
        files = [line.strip() for line in res.stdout.splitlines() if line.strip()]
    except Exception:
        files = []

    if not files:
        # Fallback to status porcelain
        status_res = subprocess.run(
            ["git", "status", "--porcelain"],
            cwd=str(root),
            stdout=subprocess.PIPE,
            text=True,
        )
        for line in status_res.stdout.splitlines():
            if len(line) > 3:
                files.append(line[3:].strip())

    if not files:
        print(f"No changed files detected for ticket {ticket_key} against {base_ref}.")
        return []

    return stage_files(files, open_editor=open_editor, repo_root=root)


def get_diff(repo_root=None):
    """Returns the git diff from .reviews/ repo."""
    root = find_repo_root(repo_root) if repo_root else find_repo_root()
    reviews_dir = ensure_reviews_env(root)
    res = subprocess.run(
        ["git", "-C", str(reviews_dir), "diff"],
        stdout=subprocess.PIPE,
        text=True,
    )
    return res.stdout


def extract_directives(diff_text=None, repo_root=None):
    """
    Extracts all inline >> review directives from .reviews/ diff or modified files.
    Returns list of dicts: [{'file': ..., 'line': ..., 'directive': ...}]
    """
    root = find_repo_root(repo_root) if repo_root else find_repo_root()
    reviews_dir = ensure_reviews_env(root)

    directives = []
    # If diff text is provided, extract from added diff lines
    if diff_text:
        current_file = None
        current_line = 0
        for line in diff_text.splitlines():
            if line.startswith("+++ b/"):
                current_file = line[6:]
            elif line.startswith("@@ "):
                m = re.search(r"\+(\d+)", line)
                if m:
                    current_line = int(m.group(1))
            elif line.startswith("+") and not line.startswith("+++"):
                added_content = line[1:]
                m = DIRECTIVE_PATTERN.search(added_content)
                if m:
                    directives.append({
                        "file": current_file or "unknown",
                        "line": current_line,
                        "directive": m.group(1).strip(),
                    })
                current_line += 1
            elif not line.startswith("-"):
                current_line += 1
    else:
        # Scan all staged files in .reviews/
        for p in reviews_dir.rglob("*"):
            if p.is_file() and not any(part == ".git" for part in p.parts):
                rel = p.relative_to(reviews_dir)
                try:
                    text = p.read_text(encoding="utf-8", errors="replace")
                    for idx, line in enumerate(text.splitlines(), start=1):
                        m = DIRECTIVE_PATTERN.search(line)
                        if m:
                            directives.append({
                                "file": str(rel),
                                "line": idx,
                                "directive": m.group(1).strip(),
                            })
                except Exception:
                    continue

    return directives


def apply_reviews(file_filter=None, repo_root=None, reload_editor=True):
    """
    Applies modified files from .reviews/ back to the project root,
    commits the state in .reviews/, and signals emacsclient to reload buffers.
    """
    root = find_repo_root(repo_root) if repo_root else find_repo_root()
    reviews_dir = ensure_reviews_env(root)

    # Check for uncommitted diff in .reviews/
    status_res = subprocess.run(
        ["git", "-C", str(reviews_dir), "status", "--porcelain"],
        stdout=subprocess.PIPE,
        text=True,
    )
    changed_files = []
    for line in status_res.stdout.splitlines():
        if len(line) > 3:
            changed_files.append(line[3:].strip())

    if not changed_files:
        print("No modified files in .reviews/ to apply.")
        return []

    applied = []
    for rel_path in changed_files:
        if file_filter and rel_path not in file_filter:
            continue

        src = reviews_dir / rel_path
        dest = root / rel_path
        if src.exists():
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dest)
            applied.append(rel_path)

    # Commit synchronized state in .reviews/
    subprocess.run(["git", "-C", str(reviews_dir), "add", "."], check=True)
    subprocess.run(
        ["git", "-C", str(reviews_dir), "commit", "-m", f"Applied back: {', '.join(applied)}", "--allow-empty"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    print(f"Successfully applied {len(applied)} file(s) from .reviews/ to workspace:")
    for a in applied:
        print(f"  • {a}")

    if reload_editor:
        reload_emacs_buffers()

    return applied


def open_in_emacs(file_paths):
    """Invokes emacsclient -n to open target files in the human's running Emacs."""
    emacsclient = shutil.which("emacsclient")
    if not emacsclient:
        return False

    abs_paths = [str(Path(fp).resolve()) for fp in file_paths]
    try:
        subprocess.run([emacsclient, "-n"] + abs_paths, check=False)
        return True
    except Exception:
        return False


def reload_emacs_buffers():
    """Tells running Emacs instance to revert all unmodified buffers."""
    emacsclient = shutil.which("emacsclient")
    if not emacsclient:
        return False
    elisp = '(progn (mapc (lambda (b) (with-current-buffer b (when (and buffer-file-name (file-exists-p buffer-file-name)) (revert-buffer t t t)))) (buffer-list)) t)'
    try:
        subprocess.run([emacsclient, "--eval", elisp], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
        return True
    except Exception:
        return False


def main():
    parser = argparse.ArgumentParser(
        description="Isolated In-Place Code & Document Review Workflow (.reviews/) CLI Helper"
    )
    subparsers = parser.add_subparsers(dest="command", help="Available subcommands")

    # init
    subparsers.add_parser("init", help="Initialize .reviews/ mini-git repository")

    # stage
    p_stage = subparsers.add_parser("stage", help="Stage files into .reviews/ and open in Emacs")
    p_stage.add_argument("files", nargs="+", help="Files to stage into .reviews/")
    p_stage.add_argument("--no-open", action="store_true", help="Do not launch emacsclient")

    # stage-ticket
    p_ticket = subparsers.add_parser("stage-ticket", help="Stage all changed files for a ticket")
    p_ticket.add_argument("ticket_key", help="Jira ticket key (e.g. ATT-5817)")
    p_ticket.add_argument("--base", default="develop", help="Base ref to diff against")
    p_ticket.add_argument("--no-open", action="store_true", help="Do not launch emacsclient")

    # diff
    p_diff = subparsers.add_parser("diff", help="Show git diff from .reviews/ repo")
    p_diff.add_argument("--directives", action="store_true", help="Extract and list >> review directives")

    # apply
    p_apply = subparsers.add_parser("apply", help="Apply edits from .reviews/ back to project root")
    p_apply.add_argument("files", nargs="*", help="Optional file paths to apply (default all)")
    p_apply.add_argument("--no-reload", action="store_true", help="Do not signal emacsclient reload")

    # status
    subparsers.add_parser("status", help="Show git status of .reviews/ repo")

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(1)

    if args.command == "init":
        r = ensure_reviews_env()
        print(f"Initialized isolated review sandbox at {r}")

    elif args.command == "stage":
        stage_files(args.files, open_editor=not args.no_open)

    elif args.command == "stage-ticket":
        stage_ticket(args.ticket_key, base_ref=args.base, open_editor=not args.no_open)

    elif args.command == "diff":
        diff_text = get_diff()
        if args.directives:
            directives = extract_directives(diff_text=diff_text)
            print(f"Found {len(directives)} review directive(s):")
            for d in directives:
                print(f"  • {d['file']}:{d['line']} >> {d['directive']}")
        else:
            if diff_text:
                print(diff_text)
            else:
                print("No changes in .reviews/")

    elif args.command == "apply":
        apply_reviews(file_filter=args.files if args.files else None, reload_editor=not args.no_reload)

    elif args.command == "status":
        r = ensure_reviews_env()
        subprocess.run(["git", "-C", str(r), "status"])


if __name__ == "__main__":
    main()
