#!/usr/bin/env python3
"""
Static Architecture Guard: lint_lifecycle_safety.py
Enforces REQ-STB-016 (Lifecycle Exception Hardening).

Scans app/src/main for unguarded Context.startService and Context.unregisterReceiver
invocations that could throw IllegalStateException, SecurityException, or IllegalArgumentException.
"""

import os
import re
import sys

UNSAFE_PATTERNS = [
    (re.compile(r'(?<!safe)startService\s*\('),
     "Unguarded startService invocation without LifecycleUtils / safeStartService wrapper"),
    (re.compile(r'(?<!safe)unregisterReceiver\s*\('),
     "Unguarded unregisterReceiver invocation without LifecycleUtils / safeUnregisterReceiver wrapper"),
]

def scan_file(filepath):
    violations = []
    with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
        lines = f.readlines()

    # Skip LifecycleUtils itself where platform startService / unregisterReceiver are safely wrapped
    if filepath.endswith("LifecycleUtils.kt") or filepath.endswith("LifecycleUtils.java"):
        return violations

    for line_no, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        # Skip single-line comments or doc comment lines
        if line.startswith("//") or line.startswith("*") or line.startswith("/*"):
            continue

        for pattern, message in UNSAFE_PATTERNS:
            match = pattern.search(line)
            if match:
                violations.append((filepath, line_no, message, line))
    return violations

def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "app", "src", "main"))
    if not os.path.exists(root_dir):
        print(f"Error: Directory not found: {root_dir}")
        sys.exit(1)

    all_violations = []
    for dirpath, _, filenames in os.walk(root_dir):
        for fname in filenames:
            if fname.endswith(('.java', '.kt')):
                fpath = os.path.join(dirpath, fname)
                violations = scan_file(fpath)
                all_violations.extend(violations)

    if all_violations:
        print(f"FAILED: Found {len(all_violations)} lifecycle safety violation(s):")
        for fpath, lno, msg, snippet in all_violations:
            rel_path = os.path.relpath(fpath, os.path.dirname(__file__) + "/..")
            print(f"  {rel_path}:{lno}: {msg} -> `{snippet}`")
        sys.exit(1)
    else:
        print("PASS: Lifecycle safety audit passed. Zero unguarded startService or unregisterReceiver call sites found.")
        sys.exit(0)

if __name__ == "__main__":
    main()
